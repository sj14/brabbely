package io.github.sj14.brabbely.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class BackupFormatTest {
    private val backup = Backup(
        settings = mapOf("sleep_minutes" to 45, "skip_silence" to true, "speed" to 1.25f, "theme_mode" to "DARK"),
        podcasts = listOf(
            PodcastBackup(
                feedUrl = "https://example.com/feed.xml",
                title = "Example",
                playedGuids = listOf("guid-1", "guid-2"),
                positions = mapOf("guid-3" to 123_456L),
            ),
        ),
        queue = listOf(QueueEntry("https://example.com/feed.xml", "guid-3")),
    )

    @Test
    fun writtenBackupIsCompressedAndReadsBack() {
        val output = ByteArrayOutputStream()
        BackupFormat.write(backup, output)
        val bytes = output.toByteArray()

        assertEquals(0x1f.toByte(), bytes[0])
        assertEquals(0x8b.toByte(), bytes[1])
        assertEquals(backup, BackupFormat.read(bytes.inputStream()))
    }

    @Test
    fun readsBackupFromWhenTheAppWasCalledLauscher() {
        val output = ByteArrayOutputStream()
        BackupFormat.write(backup, output)
        val json = GZIPInputStream(output.toByteArray().inputStream()).use { it.readBytes().decodeToString() }
        val lauscherJson = json.replace("\"brabbely-backup\"", "\"lauscher-backup\"")
        check(lauscherJson != json)

        val lauscherBackup = ByteArrayOutputStream()
        GZIPOutputStream(lauscherBackup).use { it.write(lauscherJson.toByteArray()) }
        assertEquals(backup, BackupFormat.read(lauscherBackup.toByteArray().inputStream()))
    }
}
