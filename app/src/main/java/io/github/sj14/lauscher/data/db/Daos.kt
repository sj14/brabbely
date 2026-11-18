package io.github.sj14.lauscher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query(
        """
        SELECT p.*, (
            SELECT COUNT(*) FROM episodes e WHERE e.podcastId = p.id AND e.played = 0 AND e.pubDate > :newSince
        ) AS unplayedCount
        FROM podcasts p WHERE p.subscribed = 1 ORDER BY p.title COLLATE NOCASE
        """,
    )
    fun observeSubscribed(newSince: Long): Flow<List<PodcastWithCounts>>

    @Query("SELECT * FROM podcasts WHERE subscribed = 1")
    suspend fun getSubscribed(): List<Podcast>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    fun observe(id: Long): Flow<Podcast?>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun get(id: Long): Podcast?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getByFeedUrl(feedUrl: String): Podcast?

    @Insert
    suspend fun insert(podcast: Podcast): Long

    @Update
    suspend fun update(podcast: Podcast)

    @Query("UPDATE podcasts SET subscribed = :subscribed WHERE id = :id")
    suspend fun setSubscribed(id: Long, subscribed: Boolean)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM podcasts WHERE subscribed = 0")
    suspend fun deleteUnsubscribed()
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY pubDate DESC")
    fun observeForPodcast(podcastId: Long): Flow<List<Episode>>

    @Transaction
    @Query(
        """
        SELECT e.* FROM episodes e JOIN podcasts p ON p.id = e.podcastId
        WHERE p.subscribed = 1 AND e.played = 0 AND e.pubDate > :newSince ORDER BY e.pubDate DESC
        """,
    )
    fun observeNew(newSince: Long): Flow<List<EpisodeWithPodcast>>

    @Transaction
    @Query("SELECT * FROM episodes WHERE queuePosition IS NOT NULL ORDER BY queuePosition")
    fun observeQueue(): Flow<List<EpisodeWithPodcast>>

    @Transaction
    @Query("SELECT * FROM episodes WHERE downloadState != 'NONE' ORDER BY pubDate DESC")
    fun observeDownloads(): Flow<List<EpisodeWithPodcast>>

    @Query("SELECT * FROM episodes WHERE queuePosition IS NOT NULL ORDER BY queuePosition")
    suspend fun getQueue(): List<Episode>

    @Transaction
    @Query("SELECT * FROM episodes WHERE id = :id")
    fun observeWithPodcast(id: Long): Flow<EpisodeWithPodcast?>

    @Transaction
    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getWithPodcast(id: Long): EpisodeWithPodcast?

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun get(id: Long): Episode?

    @Transaction
    @Query("SELECT * FROM episodes WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun getLastPlayed(): EpisodeWithPodcast?

    @Query("SELECT id, guid FROM episodes WHERE podcastId = :podcastId")
    suspend fun getKeys(podcastId: Long): List<EpisodeKey>

    @Query("SELECT guid, played, position FROM episodes WHERE podcastId = :podcastId AND (played = 1 OR position > 0)")
    suspend fun getProgress(podcastId: Long): List<EpisodeProgress>

    @Query("UPDATE episodes SET queuePosition = NULL")
    suspend fun clearQueue()

    @Query("SELECT guid FROM episodes WHERE podcastId = :podcastId")
    suspend fun getGuids(podcastId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<Episode>): List<Long>

    @Query("SELECT MAX(queuePosition) FROM episodes")
    suspend fun maxQueuePosition(): Int?

    @Query("SELECT MIN(queuePosition) FROM episodes")
    suspend fun minQueuePosition(): Int?

    @Query("UPDATE episodes SET queuePosition = :position WHERE id = :id")
    suspend fun setQueuePosition(id: Long, position: Int?)

    @Query("UPDATE episodes SET position = :position, lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun setPosition(id: Long, position: Long, playedAt: Long)

    @Query("UPDATE episodes SET duration = :duration WHERE id = :id AND duration != :duration")
    suspend fun setDuration(id: Long, duration: Long)

    @Query("UPDATE episodes SET played = :played, position = 0 WHERE id = :id")
    suspend fun setPlayed(id: Long, played: Boolean)

    @Query("UPDATE episodes SET played = 1, position = 0 WHERE podcastId = :podcastId")
    suspend fun markAllPlayed(podcastId: Long)

    @Query("UPDATE episodes SET downloadState = :state, downloadedBytes = :bytes, localPath = :path WHERE id = :id")
    suspend fun setDownload(id: Long, state: DownloadState, bytes: Long, path: String?)

    @Query("UPDATE episodes SET downloadedBytes = :bytes, fileSize = MAX(fileSize, :total) WHERE id = :id")
    suspend fun setDownloadProgress(id: Long, bytes: Long, total: Long)

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId AND localPath IS NOT NULL")
    suspend fun getDownloadedForPodcast(podcastId: Long): List<Episode>
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE episodeId = :episodeId ORDER BY startMs")
    fun observe(episodeId: Long): Flow<List<Chapter>>

    @Query("SELECT COUNT(*) FROM chapters WHERE episodeId = :episodeId")
    suspend fun count(episodeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<Chapter>)
}
