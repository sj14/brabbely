package io.github.sj14.lauscher.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * Every change of the schema needs a new version and a migration, otherwise the app crashes on
 * devices with the old schema. Room checks auto migrations against the schemas in app/schemas;
 * MigrationTest checks all of them.
 */
@Database(
    entities = [Podcast::class, Episode::class, ShowNotes::class, Chapter::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = AppDatabase.DownloadFileName::class)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun showNotesDao(): ShowNotesDao
    abstract fun chapterDao(): ChapterDao

    /**
     * Version 2 stores only the name of a downloaded file instead of its absolute path, which is
     * only valid on one device and would be wrong after restoring the database on another one.
     */
    @RenameColumn(tableName = "episodes", fromColumnName = "localPath", toColumnName = "downloadFile")
    class DownloadFileName : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.query("SELECT id, downloadFile FROM episodes WHERE downloadFile IS NOT NULL").use { cursor ->
                while (cursor.moveToNext()) {
                    val fileName = File(cursor.getString(1)).name
                    db.execSQL("UPDATE episodes SET downloadFile = ? WHERE id = ?", arrayOf(fileName, cursor.getLong(0)))
                }
            }
        }
    }

    companion object {
        const val NAME = "lauscher.db"

        /**
         * Version 3 moves the show notes into a table of their own, see [ShowNotes]. Written by hand,
         * because an auto migration cannot copy them before it drops the column. SQLite before
         * Android 14 cannot drop a column, so the table is rebuilt the way Room's auto migrations do
         * it; the SQL is that of app/schemas/…/3.json. Room turns foreign keys on only after the
         * migrations, so dropping the old table does not delete the chapters.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `show_notes` (`episodeId` INTEGER NOT NULL, `notes` TEXT NOT NULL,
                    PRIMARY KEY(`episodeId`),
                    FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
                    """,
                )
                db.execSQL("INSERT INTO show_notes (episodeId, notes) SELECT id, description FROM episodes WHERE description != ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `_new_episodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `podcastId` INTEGER NOT NULL, `guid` TEXT NOT NULL, `title` TEXT NOT NULL, `pubDate` INTEGER NOT NULL,
                    `mediaUrl` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `fileSize` INTEGER NOT NULL,
                    `duration` INTEGER NOT NULL, `imageUrl` TEXT, `chaptersUrl` TEXT, `position` INTEGER NOT NULL,
                    `played` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `queuePosition` INTEGER,
                    `downloadState` TEXT NOT NULL, `downloadedBytes` INTEGER NOT NULL, `downloadFile` TEXT,
                    FOREIGN KEY(`podcastId`) REFERENCES `podcasts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
                    """,
                )
                val columns = "id, podcastId, guid, title, pubDate, mediaUrl, mimeType, fileSize, duration, imageUrl, " +
                    "chaptersUrl, position, played, lastPlayedAt, queuePosition, downloadState, downloadedBytes, downloadFile"
                db.execSQL("INSERT INTO _new_episodes ($columns) SELECT $columns FROM episodes")
                db.execSQL("DROP TABLE episodes")
                db.execSQL("ALTER TABLE _new_episodes RENAME TO episodes")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_podcastId_guid` ON `episodes` (`podcastId`, `guid`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_queuePosition` ON `episodes` (`queuePosition`)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_2_3)
                .build()
    }
}
