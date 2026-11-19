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
            db.execSQL(
                """
                INSERT INTO podcasts (id, feedUrl, title, author, description, subscribed, lastRefreshed)
                VALUES (1, 'https://example.com/feed.xml', 'Example', '', '', 1, 0)
                """,
            )
            insertEpisode(db, id = 7, localPath = "/storage/emulated/0/Android/data/app/files/Podcasts/7.mp3")
            insertEpisode(db, id = 8, localPath = null)
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

    private fun insertEpisode(db: SupportSQLiteDatabase, id: Long, localPath: String?) {
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
