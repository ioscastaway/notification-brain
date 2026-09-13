package com.ioscastaway.notificationbrain.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainDao {
    @Insert
    suspend fun insert(record: NotificationRecord): Long

    @Update
    suspend fun update(record: NotificationRecord)

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun byId(id: Long): NotificationRecord?

    /** The newest record for a key that is still in the shade. */
    @Query("SELECT * FROM notifications WHERE `key` = :key AND outcome = 'PENDING' ORDER BY postedAt DESC LIMIT 1")
    suspend fun pendingByKey(key: String): NotificationRecord?

    @Query("SELECT * FROM notifications WHERE verdict = 'DISMISS' ORDER BY postedAt DESC LIMIT 500")
    fun dismissed(): Flow<List<NotificationRecord>>

    /** Kept notifications the user then swiped away one by one: the learning candidates. */
    @Query("SELECT * FROM notifications WHERE verdict = 'KEEP' AND outcome = 'USER_SWIPED' AND feedbackChip IS NULL ORDER BY postedAt DESC LIMIT 300")
    fun swipedCandidates(): Flow<List<NotificationRecord>>

    /** Rows still in the shade for the given keys (newest per key wins in the caller). */
    @Query("SELECT * FROM notifications WHERE `key` IN (:keys) AND outcome = 'PENDING' ORDER BY postedAt DESC")
    suspend fun pendingByKeys(keys: List<String>): List<NotificationRecord>

    @Query("SELECT * FROM notifications ORDER BY postedAt DESC LIMIT 2000")
    suspend fun history(): List<NotificationRecord>

    @Query("SELECT COUNT(*) FROM notifications WHERE verdict = 'DISMISS' AND postedAt >= :since")
    fun dismissedSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE verdict = 'DISMISS' AND postedAt >= :since")
    suspend fun dismissedCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE postedAt >= :since")
    fun seenSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE verdict = 'DISMISS' AND feedbackChip IN ('WAS_IMPORTANT', 'NEVER_TOUCH_APP')")
    fun falseDismissals(): Flow<Int>

    /** Every (package, channel) the listener has seen, with the app label: the compiler's vocabulary. */
    @Query("SELECT packageName, appLabel, channelId FROM notifications GROUP BY packageName, channelId ORDER BY packageName")
    suspend fun knownChannels(): List<KnownChannel>

    @Query("DELETE FROM notifications WHERE postedAt < :before")
    suspend fun pruneBefore(before: Long): Int

    @Insert
    suspend fun insertCrash(crash: CrashRecord)

    @Query("SELECT * FROM crashes ORDER BY timestamp DESC LIMIT 100")
    fun crashes(): Flow<List<CrashRecord>>

    @Query("SELECT MAX(timestamp) FROM crashes WHERE source = 'exit-info'")
    suspend fun newestExitInfo(): Long?
}

data class KnownChannel(val packageName: String, val appLabel: String, val channelId: String?)
