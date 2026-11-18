package io.github.sj14.lauscher.data.opml

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream

data class OpmlEntry(val title: String, val feedUrl: String)

object Opml {
    fun read(input: InputStream): List<OpmlEntry> {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val entries = mutableListOf<OpmlEntry>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "outline") continue
            val feedUrl = parser.getAttributeValue(null, "xmlUrl") ?: continue
            val title = parser.getAttributeValue(null, "text") ?: parser.getAttributeValue(null, "title") ?: feedUrl
            entries += OpmlEntry(title, feedUrl)
        }
        return entries
    }

    fun write(entries: List<OpmlEntry>, output: OutputStream) {
        val serializer = Xml.newSerializer()
        serializer.setOutput(output, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "opml").attribute(null, "version", "2.0")
        serializer.startTag(null, "head")
        serializer.startTag(null, "title").text("Lauscher subscriptions").endTag(null, "title")
        serializer.endTag(null, "head")
        serializer.startTag(null, "body")
        for (entry in entries) {
            serializer.startTag(null, "outline")
                .attribute(null, "type", "rss")
                .attribute(null, "text", entry.title)
                .attribute(null, "xmlUrl", entry.feedUrl)
                .endTag(null, "outline")
        }
        serializer.endTag(null, "body")
        serializer.endTag(null, "opml")
        serializer.endDocument()
    }
}
