package io.github.sj14.lauscher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Every change of the schema needs a new version and a migration, otherwise the app crashes on
 * devices with the old schema. The schema of each version is kept in app/schemas.
 */
@Database(entities = [Podcast::class, Episode::class, ShowNotes::class, Chapter::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun showNotesDao(): ShowNotesDao
    abstract fun chapterDao(): ChapterDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lauscher.db").build()
    }
}
