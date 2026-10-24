package com.vibetuned.ln_reader.data.repo

/**
 * One book found in a zip, as paths inside it: the audiobook, its EPUB and its sync manifest.
 * An EPUB-only book has no [audio] and no [sync].
 */
internal data class ZipBook(val audio: String?, val epub: String?, val sync: String?) {
    /** The file the book is created from: the audio, or the EPUB when there is no audio. */
    val primary: String get() = audio ?: epub ?: error("A zip book needs an audio file or an EPUB")
}

internal data class ZipPlan(
    /** In natural order of their paths, so "Volume 2" comes before "Volume 10". */
    val books: List<ZipBook>,
    /** The one folder every file sits under, when there is one: the name of what was zipped. */
    val commonFolder: String?
)

/**
 * Decides which files in a zip make which books. Pure: works on the paths alone, so it is tested
 * without a zip in sight (`ZipBookPlannerTest`). Mirrors `ZipBookPlanner` in the iOS app.
 *
 * Files are grouped by the folder they sit in, and each folder is paired up on its own:
 * - every .m4b is a book;
 * - an .epub or sync manifest with the same name as an .m4b goes with it;
 * - failing that, a folder's only unpaired .m4b takes its only unpaired .epub, and a lone sync
 *   manifest (or the one called `sync_manifest.json`) — the ln-vox layout, where the three names
 *   all differ;
 * - an .epub nothing claimed becomes an EPUB-only book.
 *
 * Callers pass only sync manifests among the .json files; telling a manifest from any other JSON
 * needs the file's contents, which the planner never sees.
 */
internal object ZipBookPlanner {

    private val wantedExtensions = setOf("m4b", "epub", "json")

    /**
     * Whether a zip entry could be part of a book. Skips folders, macOS's `__MACOSX` resource-fork
     * copies — which carry the very same names and extensions as the real files — and anything
     * hidden, such as `.DS_Store` and `._` AppleDouble files.
     */
    fun isCandidate(path: String): Boolean {
        val clean = normalize(path)
        if (clean.isEmpty() || clean.endsWith("/")) return false
        val segments = clean.split('/')
        if (segments.any { it == "__MACOSX" || it.startsWith(".") }) return false
        return extensionOf(clean) in wantedExtensions
    }

    /** Zip paths written on Windows can use backslashes; everything here works on forward ones. */
    fun normalize(path: String): String = path.replace('\\', '/').trimStart('/')

    fun plan(paths: List<String>): ZipPlan {
        val files = paths.map(::normalize).filter(::isCandidate).distinct()
        val books = files.groupBy(::folderOf).values.flatMap(::planFolder)
            .sortedWith(compareBy(NaturalOrder) { it.primary })
        return ZipPlan(books, commonFolder(files))
    }

    /**
     * What to call the collection for a zip that holds several books: the folder that was zipped
     * when there is one, otherwise the zip's own name. Google Drive names its downloads like
     * `Series-20261024T204511Z-1-001.zip`; the export stamp and part number are dropped, so every
     * part of a split download lands in the same collection.
     */
    fun collectionName(plan: ZipPlan, zipFileName: String?): String {
        plan.commonFolder?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val fromZip = zipFileName
            ?.substringAfterLast('/')
            ?.replace(Regex("\\.zip$", RegexOption.IGNORE_CASE), "")
            ?.replace(DRIVE_EXPORT_SUFFIX, "")
            ?.trim()
        return fromZip?.takeIf { it.isNotEmpty() } ?: "Imported books"
    }

    private fun planFolder(files: List<String>): List<ZipBook> {
        val audios = files.filter { extensionOf(it) == "m4b" }.sortedWith(NaturalOrder)
        val epubs = files.filter { extensionOf(it) == "epub" }.sortedWith(NaturalOrder).toMutableList()
        val syncs = files.filter { extensionOf(it) == "json" }.sortedWith(NaturalOrder).toMutableList()

        class Draft(val audio: String, var epub: String? = null, var sync: String? = null)

        val drafts = audios.map { audio ->
            val stem = stemOf(audio)
            Draft(
                audio = audio,
                epub = epubs.firstOrNull { stemOf(it).equals(stem, ignoreCase = true) }
                    ?.also { epubs.remove(it) },
                sync = syncs.firstOrNull { stemOf(it).equals(stem, ignoreCase = true) }
                    ?.also { syncs.remove(it) }
            )
        }
        drafts.filter { it.epub == null }.singleOrNull()?.let { draft ->
            epubs.singleOrNull()?.let { draft.epub = it; epubs.remove(it) }
        }
        drafts.filter { it.sync == null }.singleOrNull()?.let { draft ->
            val pick = syncs.singleOrNull()
                ?: syncs.firstOrNull { fileNameOf(it).equals("sync_manifest.json", ignoreCase = true) }
            pick?.let { draft.sync = it; syncs.remove(it) }
        }
        return drafts.map { ZipBook(it.audio, it.epub, it.sync) } +
            epubs.map { ZipBook(audio = null, epub = it, sync = null) }
    }

    private fun commonFolder(files: List<String>): String? {
        if (files.isEmpty() || files.any { '/' !in it }) return null
        return files.map { it.substringBefore('/') }.distinct().singleOrNull()
    }

    private fun folderOf(path: String) = path.substringBeforeLast('/', missingDelimiterValue = "")
    private fun fileNameOf(path: String) = path.substringAfterLast('/')
    private fun stemOf(path: String) = fileNameOf(path).substringBeforeLast('.')
    private fun extensionOf(path: String) =
        fileNameOf(path).substringAfterLast('.', missingDelimiterValue = "").lowercase()

    private val DRIVE_EXPORT_SUFFIX = Regex("-\\d{8}T\\d{6}Z(?:-\\d+-\\d+)?$")
}

/**
 * Orders names the way people number things: digit runs compare as numbers ("Vol 2" < "Vol 10"),
 * everything else case-insensitively.
 */
internal object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a).map { it.value }.toList()
        val cb = chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val result = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0')
                val ny = y.trimStart('0')
                if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
            } else {
                x.compareTo(y, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return ca.size - cb.size
    }
}
