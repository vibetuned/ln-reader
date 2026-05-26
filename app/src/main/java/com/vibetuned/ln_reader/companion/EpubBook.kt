package com.vibetuned.ln_reader.companion

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipInputStream

/** One reading-order document, with its EPUB-root-relative path and the URL the WebView loads. */
data class EpubSpineItem(
    val rootRelativePath: String,
    val url: String
)

data class EpubBook(
    val rootDir: File,
    val spine: List<EpubSpineItem>
)

/**
 * Minimal EPUB handling: unzip the container, read `META-INF/container.xml` to find the OPF, then
 * parse the OPF manifest + spine into an ordered list of XHTML documents. Documents are served to
 * the WebView through a [androidx.webkit.WebViewAssetLoader] mounted at [BASE_URL].
 */
object EpubReader {

    const val BASE_URL = "https://appassets.androidplatform.net/epub/"

    fun ensureExtracted(epubFile: File, rootDir: File) {
        if (rootDir.exists() && (rootDir.listFiles()?.isNotEmpty() == true)) return
        rootDir.mkdirs()
        extract(epubFile, rootDir)
    }

    fun parse(rootDir: File): EpubBook {
        val opfPath = findOpfPath(rootDir)
        val opfFile = File(rootDir, opfPath)
        val opfDir = opfPath.substringBeforeLast('/', "")
        val (manifest, spineIdrefs) = parseOpf(opfFile)
        val items = spineIdrefs.mapNotNull { idref ->
            val href = manifest[idref] ?: return@mapNotNull null
            val cleanHref = href.substringBefore('#')
            val rootRel = if (opfDir.isEmpty()) cleanHref else "$opfDir/$cleanHref"
            EpubSpineItem(rootRelativePath = rootRel, url = BASE_URL + rootRel)
        }
        return EpubBook(rootDir, items)
    }

    private fun extract(epubFile: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalPath
        ZipInputStream(epubFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                // Guard against Zip path traversal.
                if (outFile.canonicalPath.startsWith(canonicalTarget)) {
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun findOpfPath(rootDir: File): String {
        val container = File(rootDir, "META-INF/container.xml")
        require(container.exists()) { "EPUB missing META-INF/container.xml" }
        val parser = Xml.newPullParser()
        container.inputStream().use { input ->
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    parser.getAttributeValue(null, "full-path")?.let { return it }
                }
                event = parser.next()
            }
        }
        error("No rootfile entry in container.xml")
    }

    private fun parseOpf(opf: File): Pair<Map<String, String>, List<String>> {
        val manifest = HashMap<String, String>()
        val spine = ArrayList<String>()
        val parser = Xml.newPullParser()
        opf.inputStream().use { input ->
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) manifest[id] = href
                        }
                        "itemref" -> {
                            parser.getAttributeValue(null, "idref")?.let { spine.add(it) }
                        }
                    }
                }
                event = parser.next()
            }
        }
        return manifest to spine
    }
}
