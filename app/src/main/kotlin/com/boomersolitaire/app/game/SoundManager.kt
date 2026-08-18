package com.boomersolitaire.app.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Soft card sounds, synthesised in code (no assets, no licensing questions)
 * and cached as tiny WAV files on first run.
 */
class SoundManager(private val context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = mutableMapOf<GameSound, Int>()
    @Volatile private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
        for (sound in GameSound.entries) {
            val file = File(dir, "${sound.name.lowercase()}_v1.wav")
            if (!file.exists()) file.writeBytes(wav(synthesise(sound)))
            ids[sound] = soundPool.load(file.path, 1)
        }
        loaded = true
    }

    fun play(sound: GameSound) {
        if (!loaded) return
        val id = ids[sound] ?: return
        val volume = when (sound) {
            GameSound.WIN -> 0.5f
            GameSound.SHUFFLE -> 0.35f
            else -> 0.4f
        }
        soundPool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }

    // ---- Synthesis ----

    private val sampleRate = 22050

    private fun synthesise(sound: GameSound): FloatArray = when (sound) {
        GameSound.PLACE -> render(0.08f) { t ->
            sin(2f * PI.toFloat() * 165f * t) * exp(-t * 42f) * 0.9f +
                noise(t) * exp(-t * 70f) * 0.22f
        }
        GameSound.FLIP -> render(0.05f) { t ->
            noise(t) * exp(-t * 85f) * 0.55f +
                sin(2f * PI.toFloat() * 880f * t) * exp(-t * 95f) * 0.18f
        }
        GameSound.SLIDE -> lowpass(
            render(0.14f) { t ->
                noise(t) * sin(PI.toFloat() * t / 0.14f) * 0.32f
            },
        )
        GameSound.SHUFFLE -> lowpass(
            render(0.3f) { t ->
                noise(t) * (0.55f + 0.45f * sin(2f * PI.toFloat() * 22f * t)) *
                    sin(PI.toFloat() * t / 0.3f) * 0.34f
            },
        )
        GameSound.WIN -> {
            val notes = listOf(523.25f, 659.25f, 783.99f, 1046.5f)
            val dur = 0.85f
            val out = FloatArray((sampleRate * dur).toInt())
            notes.forEachIndexed { i, freq ->
                val startS = (i * 0.13f * sampleRate).toInt()
                for (s in startS until out.size) {
                    val t = (s - startS).toFloat() / sampleRate
                    out[s] += sin(2f * PI.toFloat() * freq * t) * exp(-t * 3.4f) * 0.16f
                }
            }
            out
        }
    }

    private val rng = Random(7)
    private fun noise(@Suppress("UNUSED_PARAMETER") t: Float): Float = rng.nextFloat() * 2f - 1f

    private inline fun render(seconds: Float, sample: (Float) -> Float): FloatArray {
        val n = (sampleRate * seconds).toInt()
        return FloatArray(n) { i -> sample(i.toFloat() / sampleRate) }
    }

    /** Gentle 5-sample moving average to soften the noise. */
    private fun lowpass(input: FloatArray): FloatArray {
        val out = FloatArray(input.size)
        var acc = 0f
        for (i in input.indices) {
            acc += input[i]
            if (i >= 5) acc -= input[i - 5]
            out[i] = acc / 5f
        }
        return out
    }

    private fun wav(samples: FloatArray): ByteArray {
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            pcm[i * 2] = (v and 0xFF).toByte()
            pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        val dataLen = pcm.size
        val byteRate = sampleRate * 2
        val header = ByteArray(44)
        fun putStr(offset: Int, s: String) = s.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        fun putIntLe(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
            header[offset + 2] = ((value shr 16) and 0xFF).toByte()
            header[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun putShortLe(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        putStr(0, "RIFF"); putIntLe(4, 36 + dataLen); putStr(8, "WAVE")
        putStr(12, "fmt "); putIntLe(16, 16); putShortLe(20, 1); putShortLe(22, 1)
        putIntLe(24, sampleRate); putIntLe(28, byteRate); putShortLe(32, 2); putShortLe(34, 16)
        putStr(36, "data"); putIntLe(40, dataLen)
        return header + pcm
    }
}
