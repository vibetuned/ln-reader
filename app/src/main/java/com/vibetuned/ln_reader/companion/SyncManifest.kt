package com.vibetuned.ln_reader.companion

import org.json.JSONObject
import java.io.File

/**
 * Parsed `sync_manifest.json` that ties audio timestamps to EPUB beats and images.
 *
 * - [beats] drive in-reader highlighting: each carries the `data-beat-id` to mark and the audio
 *   window it covers.
 * - [images] drive scrubber markers: each carries a `trigger_seconds` position and the ordinal
 *   used to match it to an embedded m4b image.
 */
data class SyncManifest(
    val spanClass: String,
    val dataAttr: String,
    val beats: List<SyncBeat>,
    val images: List<SyncImage>
) {
    /** Active beat for a playback position: the last beat whose window has started. */
    fun beatAt(positionMs: Long): SyncBeat? {
        if (beats.isEmpty()) return null
        val seconds = positionMs / 1000.0
        // beats are sorted by startSeconds at parse time; binary search the last start <= seconds.
        var lo = 0
        var hi = beats.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (beats[mid].startSeconds <= seconds) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found.takeIf { it >= 0 }?.let { beats[it] }
    }
}

data class SyncBeat(
    val dataBeatId: String,
    val chapterId: String?,
    /** EPUB-relative path of the XHTML this beat lives in, e.g. "OEBPS/Text/prologue.xhtml". */
    val xhtml: String,
    val startSeconds: Double,
    val endSeconds: Double
)

data class SyncImage(
    /** EPUB-relative src as written in the manifest, e.g. "../Images/Cover.jpg". */
    val src: String,
    val xhtml: String?,
    val triggerSeconds: Double,
    /** Position of this entry within the manifest's images array (used to index m4b images). */
    val ordinal: Int
)

object SyncManifestParser {

    fun parse(file: File): SyncManifest? =
        runCatching { parse(file.readText()) }.getOrNull()

    fun parse(json: String): SyncManifest {
        val root = JSONObject(json)
        val spanClass = root.optString("span_class", "lnvox-beat")
        val dataAttr = root.optString("data_attr", "data-beat-id")

        val beatsArr = root.optJSONArray("beats")
        val beats = ArrayList<SyncBeat>(beatsArr?.length() ?: 0)
        if (beatsArr != null) {
            for (i in 0 until beatsArr.length()) {
                val o = beatsArr.optJSONObject(i) ?: continue
                val id = o.optString("data_beat_id").ifEmpty { o.optString("beat_id") }
                if (id.isEmpty()) continue
                beats += SyncBeat(
                    dataBeatId = id,
                    chapterId = o.optString("chapter_id").takeIf { it.isNotEmpty() },
                    xhtml = o.optString("xhtml"),
                    startSeconds = o.optDouble("start_seconds", 0.0),
                    endSeconds = o.optDouble("end_seconds", 0.0)
                )
            }
        }
        beats.sortBy { it.startSeconds }

        val imagesArr = root.optJSONArray("images")
        val images = ArrayList<SyncImage>(imagesArr?.length() ?: 0)
        if (imagesArr != null) {
            for (i in 0 until imagesArr.length()) {
                val o = imagesArr.optJSONObject(i) ?: continue
                val src = o.optString("src")
                if (src.isEmpty()) continue
                images += SyncImage(
                    src = src,
                    xhtml = o.optString("xhtml").takeIf { it.isNotEmpty() },
                    triggerSeconds = o.optDouble("trigger_seconds", 0.0),
                    ordinal = i
                )
            }
        }

        return SyncManifest(spanClass, dataAttr, beats, images)
    }
}
