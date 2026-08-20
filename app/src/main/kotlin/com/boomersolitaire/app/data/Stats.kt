package com.boomersolitaire.app.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One finished-with game.
 *
 * Klondike has no losing condition — undo is unlimited and the stock recycles
 * forever — so a game is either **won** or **set aside**. [abandoned] marks
 * the latter, and the statistics deliberately never treat it as a defeat.
 */
@Entity(tableName = "game_records")
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endedAtEpochMs: Long,
    val drawThree: Boolean,
    val won: Boolean,
    val durationMs: Long,
    val moves: Int,
    @ColumnInfo(defaultValue = "0") val abandoned: Boolean = false,
)

@Dao
interface GameRecordDao {
    @Insert
    suspend fun insert(record: GameRecord)

    @Query("SELECT * FROM game_records ORDER BY endedAtEpochMs ASC")
    fun all(): Flow<List<GameRecord>>

    @Query("SELECT * FROM game_records WHERE won = 1 ORDER BY durationMs ASC LIMIT :limit")
    fun bestByTime(limit: Int): Flow<List<GameRecord>>

    /**
     * A v1.2 bug recorded games begun via "Play again" with a ~0:00 duration
     * (the clock never restarted), which would otherwise stand as unbeatable
     * records. Gated to games that ended before the v1.3 fix shipped so it
     * can never delete a genuine future win.
     */
    @Query(
        "DELETE FROM game_records WHERE won = 1 AND durationMs < moves * 250 " +
            "AND endedAtEpochMs < $V1_3_RELEASED_EPOCH_MS",
    )
    suspend fun purgeImpossibleDurations()
}

/** 2026-08-20T00:00:00Z — the morning the clock fix shipped. */
private const val V1_3_RELEASED_EPOCH_MS = 1787184000000L

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game_records ADD COLUMN abandoned INTEGER NOT NULL DEFAULT 0")
        // Every pre-v1.4 non-win was written by recordAbandonIfNeeded — there
        // was no other way to record one — so they are all set-aside games.
        db.execSQL("UPDATE game_records SET abandoned = 1 WHERE won = 0")
    }
}

@Database(entities = [GameRecord::class], version = 2, exportSchema = false)
abstract class StatsDatabase : RoomDatabase() {
    abstract fun dao(): GameRecordDao

    companion object {
        @Volatile private var instance: StatsDatabase? = null

        fun get(context: Context): StatsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    "stats.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

/** Aggregate statistics for one draw mode. */
data class ModeStats(
    val won: Int = 0,
    val started: Int = 0,
    val setAside: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val fastestWinMs: Long? = null,
    val fewestMoves: Int? = null,
)

/**
 * Streaks count consecutive **wins**. Setting a game aside is not a defeat and
 * never breaks a streak — the app should never make putting the phone down
 * feel like a failure.
 */
fun computeModeStats(records: List<GameRecord>, drawThree: Boolean): ModeStats {
    val mode = records.filter { it.drawThree == drawThree }
    if (mode.isEmpty()) return ModeStats()
    var current = 0
    var best = 0
    for (r in mode) { // records are ordered oldest → newest
        when {
            r.won -> {
                current++
                if (current > best) best = current
            }
            r.abandoned -> Unit // set aside, not lost — streak survives
            else -> current = 0
        }
    }
    val wins = mode.filter { it.won }
    return ModeStats(
        won = wins.size,
        started = mode.size,
        setAside = mode.count { it.abandoned },
        currentStreak = current,
        bestStreak = best,
        fastestWinMs = wins.minOfOrNull { it.durationMs },
        fewestMoves = wins.minOfOrNull { it.moves },
    )
}
