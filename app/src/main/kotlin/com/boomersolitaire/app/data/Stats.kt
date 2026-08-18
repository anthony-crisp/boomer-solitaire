package com.boomersolitaire.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_records")
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endedAtEpochMs: Long,
    val drawThree: Boolean,
    val won: Boolean,
    val durationMs: Long,
    val moves: Int,
)

@Dao
interface GameRecordDao {
    @Insert
    suspend fun insert(record: GameRecord)

    @Query("SELECT * FROM game_records ORDER BY endedAtEpochMs ASC")
    fun all(): Flow<List<GameRecord>>

    @Query("SELECT * FROM game_records WHERE won = 1 ORDER BY durationMs ASC LIMIT :limit")
    fun bestByTime(limit: Int): Flow<List<GameRecord>>
}

@Database(entities = [GameRecord::class], version = 1, exportSchema = false)
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
                ).build().also { instance = it }
            }
    }
}

/** Aggregate statistics for one draw mode. */
data class ModeStats(
    val played: Int = 0,
    val won: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val fastestWinMs: Long? = null,
    val fewestMoves: Int? = null,
) {
    val winRate: Int get() = if (played == 0) 0 else (won * 100) / played
}

fun computeModeStats(records: List<GameRecord>, drawThree: Boolean): ModeStats {
    val mode = records.filter { it.drawThree == drawThree }
    if (mode.isEmpty()) return ModeStats()
    var current = 0
    var best = 0
    for (r in mode) { // records are ordered oldest → newest
        if (r.won) {
            current++
            if (current > best) best = current
        } else {
            current = 0
        }
    }
    val wins = mode.filter { it.won }
    return ModeStats(
        played = mode.size,
        won = wins.size,
        currentStreak = current,
        bestStreak = best,
        fastestWinMs = wins.minOfOrNull { it.durationMs },
        fewestMoves = wins.minOfOrNull { it.moves },
    )
}
