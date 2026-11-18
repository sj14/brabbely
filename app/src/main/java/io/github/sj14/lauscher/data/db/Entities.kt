package io.github.sj14.lauscher.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "podcasts", indices = [Index(value = ["feedUrl"], unique = true)])
data class Podcast(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val link: String? = null,
    /** False while the podcast is only previewed from the search. */
    val subscribed: Boolean = false,
    val lastRefreshed: Long = 0,
)

enum class DownloadState { NONE, QUEUED, DOWNLOADING, DONE, FAILED }

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Podcast::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["podcastId", "guid"], unique = true), Index("queuePosition")],
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String = "",
    val pubDate: Long = 0,
    val mediaUrl: String,
    val mimeType: String = "",
    val fileSize: Long = 0,
    /** Duration in milliseconds, 0 if unknown. */
    val duration: Long = 0,
    val imageUrl: String? = null,
    val chaptersUrl: String? = null,
    /** Last playback position in milliseconds. */
    val position: Long = 0,
    val played: Boolean = false,
    val lastPlayedAt: Long = 0,
    /** Position in the play queue, null if the episode is not queued. */
    val queuePosition: Int? = null,
    val downloadState: DownloadState = DownloadState.NONE,
    val downloadedBytes: Long = 0,
    val localPath: String? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

@Entity(
    tableName = "chapters",
    primaryKeys = ["episodeId", "startMs"],
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Chapter(
    val episodeId: Long,
    val startMs: Long,
    val title: String,
)

/** An episode together with the podcast it belongs to, for lists that mix podcasts. */
data class EpisodeWithPodcast(
    @Embedded val episode: Episode,
    @Relation(parentColumn = "podcastId", entityColumn = "id")
    val podcast: Podcast,
) {
    val imageUrl: String? get() = episode.imageUrl ?: podcast.imageUrl
}

/** The columns that identify an episode within its podcast. */
data class EpisodeKey(val id: Long, val guid: String)

/** The listening progress of an episode, for the backup. */
data class EpisodeProgress(val guid: String, val played: Boolean, val position: Long)

/** A podcast with the number of episodes not yet played, for the library grid. */
data class PodcastWithCounts(
    @Embedded val podcast: Podcast,
    val unplayedCount: Int,
)
