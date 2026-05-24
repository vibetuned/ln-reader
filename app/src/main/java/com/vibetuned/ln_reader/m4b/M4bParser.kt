package com.vibetuned.ln_reader.m4b

import com.vibetuned.ln_reader.m4b.AtomReader.Companion.readU32
import com.vibetuned.ln_reader.m4b.AtomReader.Companion.readU64
import java.nio.charset.StandardCharsets

data class ParsedChapter(val startMs: Long, val title: String)

data class ParsedImage(val mimeType: String, val bytes: ByteArray)

data class ParsedM4b(
    val title: String?,
    val author: String?,
    val album: String?,
    val durationMs: Long,
    val chapters: List<ParsedChapter>,
    val images: List<ParsedImage>
)

class M4bParser {

    fun parse(source: M4bSource): ParsedM4b {
        val reader = AtomReader(source)
        val moov = reader.findChild(null, "moov")
            ?: error("Not an MP4: no moov atom")

        val durationMs = readMovieDurationMs(reader, moov)
        val udta = reader.findChild(moov, "udta")
        val (title, author, album, images) = if (udta != null) readUdta(reader, udta) else Quad(null, null, null, emptyList())
        val chapters = udta?.let { readChapters(reader, it) }.orEmpty()

        return ParsedM4b(
            title = title,
            author = author,
            album = album,
            durationMs = durationMs,
            chapters = chapters,
            images = images
        )
    }

    private fun readMovieDurationMs(reader: AtomReader, moov: AtomReader.Atom): Long {
        val mvhd = reader.findChild(moov, "mvhd") ?: return 0L
        val payload = reader.readPayload(mvhd)
        if (payload.isEmpty()) return 0L
        val version = payload[0].toInt() and 0xFF
        return if (version == 1) {
            if (payload.size < 4 + 8 + 8 + 4 + 8) 0L
            else {
                val timescale = readU32(payload, 4 + 8 + 8)
                val duration = readU64(payload, 4 + 8 + 8 + 4)
                if (timescale <= 0) 0L else (duration * 1000L / timescale)
            }
        } else {
            if (payload.size < 4 + 4 + 4 + 4 + 4) 0L
            else {
                val timescale = readU32(payload, 4 + 4 + 4)
                val duration = readU32(payload, 4 + 4 + 4 + 4)
                if (timescale <= 0) 0L else (duration * 1000L / timescale)
            }
        }
    }

    private data class Quad(
        val title: String?,
        val author: String?,
        val album: String?,
        val images: List<ParsedImage>
    )

    private fun readUdta(reader: AtomReader, udta: AtomReader.Atom): Quad {
        val meta = reader.findChild(udta, "meta") ?: return Quad(null, null, null, emptyList())
        val ilst = reader.metaChildren(meta).firstOrNull { it.type == "ilst" }
            ?: return Quad(null, null, null, emptyList())

        var title: String? = null
        var author: String? = null
        var album: String? = null
        val images = ArrayList<ParsedImage>()

        for (item in reader.children(ilst)) {
            // Each ilst item contains one or more `data` atoms.
            val dataAtoms = reader.children(item).filter { it.type == "data" }
            when (item.type) {
                "©nam" -> title = readFirstStringData(reader, dataAtoms) ?: title
                "©ART" -> author = readFirstStringData(reader, dataAtoms) ?: author
                "©alb" -> album = readFirstStringData(reader, dataAtoms) ?: album
                "covr" -> dataAtoms.forEach { d -> readImageData(reader, d)?.let(images::add) }
            }
        }
        return Quad(title, author, album, images)
    }

    private fun readFirstStringData(reader: AtomReader, dataAtoms: List<AtomReader.Atom>): String? {
        for (atom in dataAtoms) {
            val payload = reader.readPayload(atom)
            // data atom payload: 4 bytes type indicator, 4 bytes locale, then value
            if (payload.size <= 8) continue
            return String(payload, 8, payload.size - 8, StandardCharsets.UTF_8)
        }
        return null
    }

    private fun readImageData(reader: AtomReader, dataAtom: AtomReader.Atom): ParsedImage? {
        val payload = reader.readPayload(dataAtom)
        if (payload.size <= 8) return null
        val typeIndicator = readU32(payload, 0).toInt()
        val mime = when (typeIndicator) {
            13 -> "image/jpeg"
            14 -> "image/png"
            else -> sniffImageMime(payload, 8)
        } ?: return null
        val bytes = payload.copyOfRange(8, payload.size)
        return ParsedImage(mime, bytes)
    }

    private fun sniffImageMime(buf: ByteArray, off: Int): String? {
        if (buf.size - off < 4) return null
        // JPEG: FF D8 FF
        if ((buf[off].toInt() and 0xFF) == 0xFF &&
            (buf[off + 1].toInt() and 0xFF) == 0xD8 &&
            (buf[off + 2].toInt() and 0xFF) == 0xFF
        ) return "image/jpeg"
        // PNG: 89 50 4E 47
        if ((buf[off].toInt() and 0xFF) == 0x89 &&
            buf[off + 1] == 'P'.code.toByte() &&
            buf[off + 2] == 'N'.code.toByte() &&
            buf[off + 3] == 'G'.code.toByte()
        ) return "image/png"
        return null
    }

    private fun readChapters(reader: AtomReader, udta: AtomReader.Atom): List<ParsedChapter> {
        val chpl = reader.findChild(udta, "chpl") ?: return emptyList()
        val payload = reader.readPayload(chpl)
        // Standard Nero chpl layout used by mp4v2 / mp4chaps:
        //   1 byte version, 3 bytes flags, 4 bytes reserved, 1 byte count, then chapters.
        // Per chapter: 8 bytes start time (100-ns units), 1 byte titleLen, titleLen bytes UTF-8.
        if (payload.size < 9) return emptyList()
        val countAtNine = payload[8].toInt() and 0xFF
        var headerLen = 9
        var count = countAtNine
        // Sanity: if reading chapters at headerLen=9 overflows, fall back to short header
        // (1 byte version, 3 bytes flags, 1 byte count).
        if (!chaptersFit(payload, headerLen, count)) {
            val countAtFour = payload[4].toInt() and 0xFF
            if (chaptersFit(payload, 5, countAtFour)) {
                headerLen = 5
                count = countAtFour
            } else {
                return emptyList()
            }
        }
        val chapters = ArrayList<ParsedChapter>(count)
        var pos = headerLen
        repeat(count) {
            if (pos + 9 > payload.size) return@repeat
            val ticks = readU64(payload, pos)
            pos += 8
            val titleLen = payload[pos].toInt() and 0xFF
            pos += 1
            if (pos + titleLen > payload.size) return@repeat
            val title = String(payload, pos, titleLen, StandardCharsets.UTF_8)
            pos += titleLen
            chapters.add(ParsedChapter(startMs = ticks / 10_000L, title = title))
        }
        return chapters
    }

    private fun chaptersFit(payload: ByteArray, headerLen: Int, count: Int): Boolean {
        if (count <= 0 || count > 10_000) return false
        var pos = headerLen
        repeat(count) {
            if (pos + 9 > payload.size) return false
            val titleLen = payload[pos + 8].toInt() and 0xFF
            pos += 9 + titleLen
            if (pos > payload.size) return false
        }
        return true
    }
}
