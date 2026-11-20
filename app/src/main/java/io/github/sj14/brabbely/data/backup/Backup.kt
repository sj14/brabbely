package io.github.sj14.brabbely.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** What is listened to in one podcast. Episodes are identified by the GUID from the feed. */
data class PodcastBackup(
    val feedUrl: String,
    val title: String,
    val playedGuids: List<String>,
    /** Playback positions in milliseconds of episodes that were started but not finished. */
    val positions: Map<String, Long>,
)

data class QueueEntry(val feedUrl: String, val guid: String)

/**
 * Everything needed to restore the app on another device. Episode details are left out,
 * they are loaded from the feeds again when restoring.
 */
data class Backup(
    val settings: Map<String, Any>,
    val podcasts: List<PodcastBackup>,
    val queue: List<QueueEntry>,
)

/** Reads and writes backups as gzip-compressed JSON. */
object BackupFormat {
    private const val FORMAT = "brabbely-backup"

    /** The format of backups made while the app was called Lauscher; they are read the same way. */
    private const val FORMAT_LAUSCHER = "lauscher-backup"
    private const val VERSION = 1

    /** File name and type for a backup created on the given day. */
    fun fileName(date: LocalDate) = "brabbely-backup-$date.json.gz"
    const val MIME_TYPE = "application/gzip"

    fun write(backup: Backup, output: OutputStream) {
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("settings", settingsToJson(backup.settings))
            .put("podcasts", JSONArray(backup.podcasts.map(::podcastToJson)))
            .put("queue", JSONArray(backup.queue.map { JSONObject().put("feedUrl", it.feedUrl).put("guid", it.guid) }))
        GZIPOutputStream(output).use { it.write(json.toString().toByteArray()) }
    }

    fun read(input: InputStream): Backup {
        val text = GZIPInputStream(input).use { it.readBytes().decodeToString() }
        val json = JSONObject(text)
        val format = json.optString("format")
        require(format == FORMAT || format == FORMAT_LAUSCHER) { "Not a Brabbely backup" }

        val podcasts = mutableListOf<PodcastBackup>()
        val podcastArray = json.getJSONArray("podcasts")
        for (index in 0 until podcastArray.length()) {
            podcasts += podcastFromJson(podcastArray.getJSONObject(index))
        }

        val queue = mutableListOf<QueueEntry>()
        val queueArray = json.optJSONArray("queue") ?: JSONArray()
        for (index in 0 until queueArray.length()) {
            val entry = queueArray.getJSONObject(index)
            queue += QueueEntry(entry.getString("feedUrl"), entry.getString("guid"))
        }

        return Backup(settingsFromJson(json.optJSONObject("settings") ?: JSONObject()), podcasts, queue)
    }

    private fun podcastToJson(podcast: PodcastBackup): JSONObject {
        val positions = JSONObject()
        podcast.positions.forEach { (guid, position) -> positions.put(guid, position) }
        return JSONObject()
            .put("feedUrl", podcast.feedUrl)
            .put("title", podcast.title)
            .put("played", JSONArray(podcast.playedGuids))
            .put("positions", positions)
    }

    private fun podcastFromJson(json: JSONObject): PodcastBackup {
        val played = json.optJSONArray("played") ?: JSONArray()
        val positions = json.optJSONObject("positions") ?: JSONObject()
        return PodcastBackup(
            feedUrl = json.getString("feedUrl"),
            title = json.optString("title"),
            playedGuids = List(played.length()) { played.getString(it) },
            positions = positions.keys().asSequence().associateWith { positions.getLong(it) },
        )
    }

    // Settings keep their type, so they can be written back to the matching DataStore key.
    private fun settingsToJson(settings: Map<String, Any>): JSONObject {
        val json = JSONObject()
        for ((key, value) in settings) {
            val type = when (value) {
                is Boolean -> "boolean"
                is Int -> "int"
                is Float -> "float"
                is String -> "string"
                else -> continue
            }
            json.put(key, JSONObject().put("type", type).put("value", value))
        }
        return json
    }

    private fun settingsFromJson(json: JSONObject): Map<String, Any> {
        val settings = mutableMapOf<String, Any>()
        for (key in json.keys()) {
            val entry = json.getJSONObject(key)
            settings[key] = when (entry.getString("type")) {
                "boolean" -> entry.getBoolean("value")
                "int" -> entry.getInt("value")
                "float" -> entry.getDouble("value").toFloat()
                "string" -> entry.getString("value")
                else -> continue
            }
        }
        return settings
    }
}
