package com.boomersolitaire.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppTheme { AUTO, FELT, LINEN, DARK, HIGH_CONTRAST }
enum class CardSize { NORMAL, LARGE, EXTRA_LARGE }
enum class CardBack { STRIPES, LATTICE, SUNBURST }

data class Settings(
    val drawThree: Boolean = false,
    val winnableDeals: Boolean = true,
    val theme: AppTheme = AppTheme.AUTO,
    val fourColorDeck: Boolean = false,
    val cardSize: CardSize = CardSize.NORMAL,
    val leftHanded: Boolean = false,
    val showTimer: Boolean = false,
    val soundEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val cardBack: CardBack = CardBack.STRIPES,
)

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val drawThree = booleanPreferencesKey("draw_three")
        val winnableDeals = booleanPreferencesKey("winnable_deals")
        val theme = stringPreferencesKey("theme")
        val fourColorDeck = booleanPreferencesKey("four_color_deck")
        val cardSize = stringPreferencesKey("card_size")
        val leftHanded = booleanPreferencesKey("left_handed")
        val showTimer = booleanPreferencesKey("show_timer")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val cardBack = stringPreferencesKey("card_back")
        val lastPlayedDay = longPreferencesKey("last_played_day")
        val dayStreak = longPreferencesKey("day_streak")
    }

    val settings: Flow<Settings> = context.settingsStore.data.map { p ->
        Settings(
            drawThree = p[Keys.drawThree] ?: false,
            winnableDeals = p[Keys.winnableDeals] ?: true,
            theme = enumOr(p[Keys.theme], AppTheme.AUTO),
            fourColorDeck = p[Keys.fourColorDeck] ?: false,
            cardSize = enumOr(p[Keys.cardSize], CardSize.NORMAL),
            leftHanded = p[Keys.leftHanded] ?: false,
            showTimer = p[Keys.showTimer] ?: false,
            soundEnabled = p[Keys.soundEnabled] ?: true,
            reduceMotion = p[Keys.reduceMotion] ?: false,
            cardBack = enumOr(p[Keys.cardBack], CardBack.STRIPES),
        )
    }

    suspend fun setDrawThree(value: Boolean) = context.settingsStore.edit { it[Keys.drawThree] = value }
    suspend fun setWinnableDeals(value: Boolean) = context.settingsStore.edit { it[Keys.winnableDeals] = value }
    suspend fun setTheme(value: AppTheme) = context.settingsStore.edit { it[Keys.theme] = value.name }
    suspend fun setFourColorDeck(value: Boolean) = context.settingsStore.edit { it[Keys.fourColorDeck] = value }
    suspend fun setCardSize(value: CardSize) = context.settingsStore.edit { it[Keys.cardSize] = value.name }
    suspend fun setLeftHanded(value: Boolean) = context.settingsStore.edit { it[Keys.leftHanded] = value }
    suspend fun setShowTimer(value: Boolean) = context.settingsStore.edit { it[Keys.showTimer] = value }
    suspend fun setSoundEnabled(value: Boolean) = context.settingsStore.edit { it[Keys.soundEnabled] = value }
    suspend fun setReduceMotion(value: Boolean) = context.settingsStore.edit { it[Keys.reduceMotion] = value }
    suspend fun setCardBack(value: CardBack) = context.settingsStore.edit { it[Keys.cardBack] = value.name }

    /** Day streak, framed only as encouragement. Days are local epoch days. */
    val dayStreak: Flow<Long> = context.settingsStore.data.map { it[Keys.dayStreak] ?: 0L }

    suspend fun recordPlayedToday(todayEpochDay: Long) {
        context.settingsStore.edit { p ->
            val last = p[Keys.lastPlayedDay] ?: 0L
            val streak = p[Keys.dayStreak] ?: 0L
            when (todayEpochDay - last) {
                0L -> Unit // already counted today
                1L -> {
                    p[Keys.dayStreak] = streak + 1
                    p[Keys.lastPlayedDay] = todayEpochDay
                }
                else -> {
                    p[Keys.dayStreak] = 1L
                    p[Keys.lastPlayedDay] = todayEpochDay
                }
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
