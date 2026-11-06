package com.callcatcher.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    /** All recordings, newest first. The UI filters by source in-memory or via [observeBySource]. */
    @Query("SELECT * FROM recordings ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE source = :source ORDER BY startedAtMillis DESC")
    fun observeBySource(source: String): Flow<List<RecordingEntity>>

    /** Every recording with a given party, for the "tap a contact → all their calls" view. */
    @Query("SELECT * FROM recordings WHERE address = :address ORDER BY startedAtMillis DESC")
    fun observeByAddress(address: String): Flow<List<RecordingEntity>>

    @Query(
        "SELECT * FROM recordings WHERE " +
            "contactName LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' " +
            "ORDER BY startedAtMillis DESC"
    )
    fun search(query: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT path FROM recordings")
    suspend fun allPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recording: RecordingEntity): Long

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recordings WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)
}
