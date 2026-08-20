package com.boomersolitaire.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {

    private var clock = 0L
    private fun record(won: Boolean, abandoned: Boolean = false, durationMs: Long = 60_000, moves: Int = 100) =
        GameRecord(
            endedAtEpochMs = clock++,
            drawThree = false,
            won = won,
            durationMs = durationMs,
            moves = moves,
            abandoned = abandoned,
        )

    @Test
    fun `wins, current run and best run are three different numbers`() {
        // won, won, unfinished, won  →  3 wins, best run of 2, current run of 1
        val records = listOf(
            record(won = true),
            record(won = true),
            record(won = false, abandoned = true),
            record(won = true),
        )
        val stats = computeModeStats(records, drawThree = false)
        assertEquals(3, stats.won)
        assertEquals(1, stats.currentStreak)
        assertEquals(2, stats.bestStreak)
        assertEquals(4, stats.started)
        assertEquals(1, stats.unfinished)
    }

    @Test
    fun `an unfinished game ends the run but is never counted as a defeat`() {
        val records = listOf(record(won = true), record(won = false, abandoned = true))
        val stats = computeModeStats(records, drawThree = false)
        assertEquals(1, stats.won)
        assertEquals(0, stats.currentStreak)
        assertEquals(1, stats.bestStreak)
        // It is reported as unfinished, never folded into a "games lost" count.
        assertEquals(1, stats.unfinished)
        assertEquals(2, stats.started)
    }

    @Test
    fun `records for the other draw mode are ignored`() {
        val mine = record(won = true)
        val theirs = record(won = true).copy(drawThree = true)
        assertEquals(1, computeModeStats(listOf(mine, theirs), drawThree = false).won)
        assertEquals(1, computeModeStats(listOf(mine, theirs), drawThree = true).won)
    }

    @Test
    fun `best and fastest come only from won games`() {
        val records = listOf(
            record(won = true, durationMs = 300_000, moves = 150),
            record(won = false, abandoned = true, durationMs = 1_000, moves = 3),
            record(won = true, durationMs = 200_000, moves = 120),
        )
        val stats = computeModeStats(records, drawThree = false)
        assertEquals(200_000L, stats.fastestWinMs)
        assertEquals(120, stats.fewestMoves)
    }

    @Test
    fun `an empty history reports nothing rather than zeroes with records`() {
        val stats = computeModeStats(emptyList(), drawThree = false)
        assertEquals(0, stats.started)
        assertEquals(0, stats.won)
        assertEquals(null, stats.fastestWinMs)
    }
}
