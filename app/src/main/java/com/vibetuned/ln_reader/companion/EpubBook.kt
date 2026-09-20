package com.vibetuned.ln_reader.companion

import android.net.Uri
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
    val spine: List<EpubSpineItem>,
    /** `dc:title` from the OPF, if present. */
    val title: String? = null,
    /** First `dc:creator` from the OPF, if present. */
    val author: String? = null,
    /** EPUB-root-relative path of the cover image, resolved from the OPF, if declared. */
    val coverPath: String? = null
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
        val opfData = parseOpf(opfFile)
        fun rootRelative(href: String): String {
            val clean = Uri.decode(href.substringBefore('#'))
            return if (opfDir.isEmpty()) clean else "$opfDir/$clean"
        }
        val items = opfData.spineIdrefs.mapNotNull { idref ->
            val href = opfData.manifest[idref] ?: return@mapNotNull null
            val rootRel = rootRelative(href)
            EpubSpineItem(rootRelativePath = rootRel, url = BASE_URL + rootRel)
        }
        // EPUB 3 marks the cover in the manifest (`properties="cover-image"`); EPUB 2 points at it
        // from `<meta name="cover" content="<id>"/>`. Either way it resolves through the manifest.
        val coverHref = (opfData.coverImageId ?: opfData.coverMetaId)?.let { opfData.manifest[it] }
        return EpubBook(
            rootDir = rootDir,
            spine = items,
            title = opfData.title?.trim()?.takeIf { it.isNotEmpty() },
            author = opfData.creator?.trim()?.takeIf { it.isNotEmpty() },
            coverPath = coverHref?.let(::rootRelative)
        )
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

    /** What we pull out of the OPF: the manifest + spine, plus the Dublin Core bits a library tile needs. */
    private class OpfData {
        val manifest = HashMap<String, String>()
        val spineIdrefs = ArrayList<String>()
        var title: String? = null
        var creator: String? = null
        var coverImageId: String? = null
        var coverMetaId: String? = null
    }

    private fun parseOpf(opf: File): OpfData {
        val data = OpfData()
        val parser = Xml.newPullParser()
        opf.inputStream().use { input ->
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    // Namespaces aren't processed, so Dublin Core elements arrive prefixed
                    // ("dc:title"); match on the local part so either spelling works.
                    when (parser.name.substringAfter(':')) {
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) data.manifest[id] = href
                            val properties = parser.getAttributeValue(null, "properties") ?: ""
                            if (id != null && properties.split(' ').contains("cover-image")) {
                                data.coverImageId = data.coverImageId ?: id
                            }
                        }
                        "itemref" -> parser.getAttributeValue(null, "idref")?.let { data.spineIdrefs.add(it) }
                        "meta" -> if (parser.getAttributeValue(null, "name") == "cover") {
                            data.coverMetaId = data.coverMetaId ?: parser.getAttributeValue(null, "content")
                        }
                        "title" -> if (data.title == null) {
                            data.title = parser.nextText()
                            event = parser.eventType
                            continue
                        }
                        "creator" -> if (data.creator == null) {
                            data.creator = parser.nextText()
                            event = parser.eventType
                            continue
                        }
                    }
                }
                event = parser.next()
            }
        }
        return data
    }
}
