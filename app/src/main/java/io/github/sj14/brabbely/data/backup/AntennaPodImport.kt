package io.github.sj14.brabbely.data.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Reads the database export of AntennaPod (Settings → Database export) into a [Backup],
 * so it can be restored the same way as a Brabbely backup.
 */
object AntennaPodImport {
    /** AntennaPod's Feeds.state for podcasts that are subscribed, not archived. */
    private const val STATE_SUBSCRIBED = 0

    /** AntennaPod's FeedItems.read for played episodes. */
    private const val READ_PLAYED = 1

    fun read(databaseFile: File): Backup {
        val db = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
        db.use {
            val podcasts = readFeeds(db).map { (feedId, feedUrl, title) ->
                PodcastBackup(
                    feedUrl = feedUrl,
                    title = title,
                    playedGuids = readPlayedGuids(db, feedId),
                    positions = readPositions(db, feedId),
                )
            }
            return Backup(settings = emptyMap(), podcasts = podcasts, queue = readQueue(db))
        }
    }

    private data class Feed(val id: Long, val url: String, val title: String)

    private fun readFeeds(db: SQLiteDatabase): List<Feed> {
        val feeds = mutableListOf<Feed>()
        val query = "SELECT id, download_url, title FROM Feeds WHERE state = ? AND download_url LIKE 'http%'"
        db.rawQuery(query, arrayOf(STATE_SUBSCRIBED.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                feeds += Feed(cursor.getLong(0), cursor.getString(1), cursor.getString(2).orEmpty())
            }
        }
        return feeds
    }

    private fun readPlayedGuids(db: SQLiteDatabase, feedId: Long): List<String> {
        val guids = mutableListOf<String>()
        val query = "SELECT item_identifier FROM FeedItems WHERE feed = ? AND read = ?"
        db.rawQuery(query, arrayOf(feedId.toString(), READ_PLAYED.toString())).use { cursor ->
            while (cursor.moveToNext()) guids += cursor.getString(0)
        }
        return guids
    }

    private fun readPositions(db: SQLiteDatabase, feedId: Long): Map<String, Long> {
        val positions = mutableMapOf<String, Long>()
        val query = """
            SELECT i.item_identifier, m.position FROM FeedItems i JOIN FeedMedia m ON m.feeditem = i.id
            WHERE i.feed = ? AND i.read != ? AND m.position > 0
        """
        db.rawQuery(query, arrayOf(feedId.toString(), READ_PLAYED.toString())).use { cursor ->
            while (cursor.moveToNext()) positions[cursor.getString(0)] = cursor.getLong(1)
        }
        return positions
    }

    private fun readQueue(db: SQLiteDatabase): List<QueueEntry> {
        val queue = mutableListOf<QueueEntry>()
        val query = """
            SELECT f.download_url, i.item_identifier FROM Queue q
            JOIN FeedItems i ON i.id = q.feeditem JOIN Feeds f ON f.id = i.feed
            ORDER BY q.id
        """
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) queue += QueueEntry(cursor.getString(0), cursor.getString(1))
        }
        return queue
    }
}
