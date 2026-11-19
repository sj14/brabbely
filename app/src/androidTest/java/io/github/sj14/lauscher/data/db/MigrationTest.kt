package io.github.sj14.lauscher.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrates databases of older versions, built from the schemas in app/schemas, and checks that the
 * result matches the current schema. Each new version gets a test here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun version1To2KeepsOnlyTheFileNameOfDownloads() {
        helper.createDatabase(DATABASE, 1).use { db ->
            insertPodcast(db)
            insertVersion1Episode(db, id = 7, localPath = "/storage/emulated/0/Android/data/app/files/Podcasts/7.mp3")
            insertVersion1Episode(db, id = 8, localPath = null)
        }

        helper.runMigrationsAndValidate(DATABASE, 2, true).use { db ->
            db.query("SELECT id, downloadFile FROM episodes ORDER BY id").use { cursor ->
                cursor.moveToNext()
                assertEquals("7.mp3", cursor.getString(1))
                cursor.moveToNext()
                assertNull(cursor.getString(1))
            }
        }
    }

    @Test
    fun version2To3MovesShowNotesIntoTheirOwnTable() {
        helper.createDatabase(DATABASE, 2).use { db ->
            insertPodcast(db)
            db.execSQL(
                """
                INSERT INTO episodes (id, podcastId, guid, title, description, pubDate, mediaUrl, mimeType,
                    fileSize, duration, position, played, lastPlayedAt, downloadState, downloadedBytes)
                VALUES (?, 1, ?, 'Episode', ?, 0, 'https://example.com/a.mp3', 'audio/mpeg', 0, 0, 0, 0, 0, 'NONE', 0)
                """,
                arrayOf<Any?>(7, "with-notes", "<p>Notes</p>"),
            )
            db.execSQL(
                """
                INSERT INTO episodes (id, podcastId, guid, title, description, pubDate, mediaUrl, mimeType,
                    fileSize, duration, position, played, lastPlayedAt, downloadState, downloadedBytes)
                VALUES (?, 1, ?, 'Episode', ?, 0, 'https://example.com/b.mp3', 'audio/mpeg', 0, 0, 0, 1, 0, 'NONE', 0)
                """,
                arrayOf<Any?>(8, "without-notes", ""),
            )
            db.execSQL("INSERT INTO chapters (episodeId, startMs, title) VALUES (7, 0, 'Intro')")
        }

        helper.runMigrationsAndValidate(DATABASE, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT episodeId, notes FROM show_notes").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToNext()
                assertEquals(7L, cursor.getLong(0))
                assertEquals("<p>Notes</p>", cursor.getString(1))
            }
            db.query("SELECT id, played FROM episodes ORDER BY id").use { cursor ->
                assertEquals(2, cursor.count)
                cursor.moveToNext()
                cursor.moveToNext()
                assertEquals(1, cursor.getInt(1))
            }
            // Rebuilding the episodes table must not delete the chapters that refer to it.
            db.query("SELECT COUNT(*) FROM chapters").use { cursor ->
                cursor.moveToNext()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun version3To4AddsChapterImages() {
        helper.createDatabase(DATABASE, 3).use { db ->
            insertPodcast(db)
            db.execSQL(
                """
                INSERT INTO episodes (id, podcastId, guid, title, pubDate, mediaUrl, mimeType, fileSize, duration,
                    position, played, lastPlayedAt, downloadState, downloadedBytes)
                VALUES (7, 1, 'guid-7', 'Episode', 0, 'https://example.com/7.mp3', 'audio/mpeg', 0, 0, 0, 0, 0, 'NONE', 0)
                """,
            )
            db.execSQL("INSERT INTO chapters (episodeId, startMs, title) VALUES (7, 0, 'Intro')")
        }

        helper.runMigrationsAndValidate(DATABASE, 4, true).use { db ->
            db.query("SELECT title, imageUrl FROM chapters").use { cursor ->
                cursor.moveToNext()
                assertEquals("Intro", cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        }
    }

    private fun insertPodcast(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO podcasts (id, feedUrl, title, author, description, subscribed, lastRefreshed)
            VALUES (1, 'https://example.com/feed.xml', 'Example', '', '', 1, 0)
            """,
        )
    }

    private fun insertVersion1Episode(db: SupportSQLiteDatabase, id: Long, localPath: String?) {
        val downloadState = if (localPath == null) "NONE" else "DONE"
        db.execSQL(
            """
            INSERT INTO episodes (id, podcastId, guid, title, description, pubDate, mediaUrl, mimeType,
                fileSize, duration, position, played, lastPlayedAt, downloadState, downloadedBytes, localPath)
            VALUES (?, 1, ?, 'Episode', '', 0, ?, 'audio/mpeg', 0, 0, 0, 0, 0, ?, 0, ?)
            """,
            arrayOf<Any?>(id, "guid-$id", "https://example.com/$id.mp3", downloadState, localPath),
        )
    }

    private companion object {
        const val DATABASE = "migration-test.db"
    }
}
