package io.github.sj14.lauscher.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * Every change of the schema needs a new version and a migration, otherwise the app crashes on
 * devices with the old schema. Room checks auto migrations against the schemas in app/schemas.
 */
@Database(
    entities = [Podcast::class, Episode::class, Chapter::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = AppDatabase.DownloadFileName::class)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
