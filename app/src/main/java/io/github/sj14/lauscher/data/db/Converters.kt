package io.github.sj14.lauscher.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDownloadState(state: DownloadState): String = state.name

    @TypeConverter
    fun toDownloadState(name: String): DownloadState = DownloadState.valueOf(name)
}
