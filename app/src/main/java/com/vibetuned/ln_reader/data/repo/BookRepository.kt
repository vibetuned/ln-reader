package com.vibetuned.ln_reader.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.vibetuned.ln_reader.data.db.BookDao
import com.vibetuned.ln_reader.data.db.BookEntity
import com.vibetuned.ln_reader.data.db.ChapterDao
import com.vibetuned.ln_reader.data.db.ChapterEntity
import com.vibetuned.ln_reader.data.db.CollectionEntity
import com.vibetuned.ln_reader.companion.EpubReader
import com.vibetuned.ln_reader.data.db.EmbeddedImageDao
import com.vibetuned.ln_reader.data.db.EmbeddedImageEntity
import com.vibetuned.ln_reader.data.db.LnReaderDatabase
import com.vibetuned.ln_reader.data.db.MEDIA_KIND_EPUB
import com.vibetuned.ln_reader.data.db.ReadingPositionDao
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.BookDetail
import com.vibetuned.ln_reader.data.prefs.DownloadPreferences
import com.vibetuned.ln_reader.m4b.M4bParser
import com.vibetuned.ln_reader.m4b.M4bSource
import com.vibetuned.ln_reader.m4b.ParsedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.UUID

class BookRepository(
    private val context: Context,
    private val database: LnReaderDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val imageDao: EmbeddedImageDao,
    private val readingPositionDao: ReadingPositionDao,
    private val parser: M4bParser,
    private val downloadPreferences: DownloadPreferences
) {

    data class ImportProgress(
        val phase: Phase,
        val bytesRead: Long = 0,
        val totalBytes: Long = -1,
        /** 1-based position of the book being imported, when a zip brings several; else 0. */
        val bookIndex: Int = 0,
        val bookCount: Int = 0
    ) {
        enum class Phase { Extracting, Parsing, Downloading, Finalizing }
    }

    /**
     * What an import brought in. One book for a single file; a zip can bring several, and some of
     * them can fail while the rest succeed, which [failures] lists by file name.
     */
    data class ImportOutcome(
        val books: List<Book>,
        val failures: List<Pair<String, String>> = emptyList(),
        /** The collection a multi-book zip filled, when the import created or reused one. */
        val collectionName: String? = null
    ) {
        /** A message for the user when part of the import didn't make it; null when all did. */
        val problem: String?
            get() {
                if (failures.isEmpty()) return null
                val total = books.size + failures.size
                val names = failures.joinToString("; ") { (name, why) -> "$name: $why" }
                return "Imported ${books.size} of $total books. Couldn't import $names"
            }
    }

    /** Imports a picked file of any supported kind: an .m4b, an .epub, or a .zip of them. */
    suspend fun importAny(
        uri: Uri,
        collectionId: String? = null,
        onProgress: (ImportProgress) -> Unit = {}
    ): Result<ImportOutcome> {
        val meta = withContext(Dispatchers.IO) { queryUriMeta(uri) }
        return if (isZip(uri, meta.name)) importZip(uri, meta, collectionId, onProgress)
        else import(uri, collectionId, onProgress).map { ImportOutcome(listOf(it)) }
    }

    fun books(): Flow<List<Book>> =
        bookDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Books at the top of the library — not inside any collection. */
    fun topLevelBooks(): Flow<List<Book>> =
        bookDao.observeTopLevel().map { list -> list.map { it.toDomain() } }

    /** Books inside a given collection. */
    fun booksInCollection(collectionId: String): Flow<List<Book>> =
        bookDao.observeByCollection(collectionId).map { list -> list.map { it.toDomain() } }

    suspend fun getDetail(bookId: String): BookDetail? = withContext(Dispatchers.IO) {
        val book = bookDao.byId(bookId)?.toDomain() ?: return@withContext null
        val chapters = chapterDao.forBook(bookId).map { it.toDomain() }
        val images = imageDao.forBook(bookId).map { it.toDomain() }
        BookDetail(book, chapters, images)
    }

    fun bookDetail(bookId: String): Flow<BookDetail?> =
        combine(
            bookDao.observeAll(),
            chapterDao.observeForBook(bookId),
            imageDao.observeForBook(bookId)
        ) { books, chapters, images ->
            val book = books.firstOrNull { it.id == bookId }?.toDomain() ?: return@combine null
            BookDetail(
                book = book,
                chapters = chapters.map { it.toDomain() },
                images = images.map { it.toDomain() }
            )
        }

    /**
     * Import a book from a picked file. An .epub becomes an EPUB-only book (see [importEpub]);
     * anything else is treated as an .m4b audiobook. If a cloud provider (Drive, OneDrive, etc.)
     * is the source, the audio is copied locally so playback streams from disk instead of the
     * network — the destination is whatever [DownloadPreferences] points at, defaulting to the
     * app's internal storage. Local SAF URIs are referenced in place.
     */
    suspend fun import(
        uri: Uri,
        collectionId: String? = null,
        onProgress: (ImportProgress) -> Unit = {}
    ): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        // One metadata query for both name and size: for cloud document providers each lookup
        // is a network round-trip, so fetching both columns at once (instead of two separate
        // DocumentFile reads) halves the latency before the download can begin.
        val sourceMeta = queryUriMeta(uri)
        if (isEpub(uri, sourceMeta.name)) importEpub(uri, sourceMeta, collectionId, onProgress)
        else importAudio(uri, sourceMeta, collectionId, onProgress)
    }

    private fun isEpub(uri: Uri, fileName: String?): Boolean =
        fileName?.endsWith(".epub", ignoreCase = true) == true ||
            runCatching { context.contentResolver.getType(uri) }.getOrNull() == "application/epub+zip"

    /**
     * A book that is only an EPUB — no audio, so nothing for the player; it lives entirely in the
     * reader, which keeps its place through `reading_positions`. The file is copied to the same
     * spot an attached companion would use (`companions/<id>/book.epub`) and extracted where the
     * reader expects it, so everything downstream of import treats it like an audiobook whose
     * EPUB companion happens to be the whole book. Title, author and cover come from the OPF.
     */
    private suspend fun importEpub(
        uri: Uri,
        sourceMeta: UriMeta,
        collectionId: String?,
        onProgress: (ImportProgress) -> Unit
    ): Result<Book> = runCatching {
        val bookId = UUID.randomUUID().toString()
        val cleanup = mutableListOf<() -> Unit>()
        try {
            onProgress(ImportProgress(ImportProgress.Phase.Downloading, 0, sourceMeta.size.takeIf { it > 0 } ?: -1L))
            val epubFile = File(companionDir(bookId).also { it.mkdirs() }, "book.epub")
            cleanup += { companionDir(bookId).deleteRecursively() }
            copyUriToFile(uri, epubFile)

            onProgress(ImportProgress(ImportProgress.Phase.Parsing))
            val rootDir = epubExtractionDir(bookId)
            cleanup += { rootDir.deleteRecursively() }
            EpubReader.ensureExtracted(epubFile, rootDir)
            val epub = EpubReader.parse(rootDir)
            require(epub.spine.isNotEmpty()) { "This EPUB has no readable pages." }

            // Copy the cover out to the usual images dir so coverPath means the same thing it does
            // for an audiobook (and survives the extraction dir being rebuilt).
            val coverPath = epub.coverPath
                ?.let { File(rootDir, it) }
                ?.takeIf { it.isFile }
                ?.let { src ->
                    val dir = bookImageDir(bookId).also { it.mkdirs() }
                    cleanup += { dir.deleteRecursively() }
                    val ext = src.extension.lowercase().ifEmpty { "img" }
                    File(dir, "0.$ext").also { src.copyTo(it, overwrite = true) }.absolutePath
                }

            val displayTitle = epub.title
                ?: sourceMeta.name?.removeSuffix(".epub")?.removeSuffix(".EPUB")
                ?: "Untitled"

            onProgress(ImportProgress(ImportProgress.Phase.Finalizing))
            val book = BookEntity(
                id = bookId,
                uri = "",
                title = displayTitle,
                author = epub.author,
                album = null,
                durationMs = 0L,
                coverPath = coverPath,
                importedAt = System.currentTimeMillis(),
                fileSize = epubFile.length(),
                isDownloaded = false,
                epubPath = epubFile.absolutePath,
                collectionId = collectionId,
                mediaKind = MEDIA_KIND_EPUB,
                spineCount = epub.spine.size
            )
            database.withTransaction { bookDao.upsert(book) }
            cleanup.clear()
            book.toDomain()
        } finally {
            cleanup.forEach { runCatching { it() } }
        }
    }

    /**
     * [adoptFile] is an audio file already on this device that the import may take over — one just
     * extracted from a zip. It is moved into place rather than copied, so a book from a zip never
     * exists twice on disk; [uri] is then only that file's address.
     */
    private suspend fun importAudio(
        uri: Uri,
        sourceMeta: UriMeta,
        collectionId: String?,
        onProgress: (ImportProgress) -> Unit,
        adoptFile: File? = null
    ): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val bookId = UUID.randomUUID().toString()
            val fileName = sourceMeta.name
            val cleanup = mutableListOf<() -> Unit>()
            try {
                // For cloud sources, download the whole file first and parse the local copy.
                // Parsing has to read most of the file anyway, so a single sequential download
                // beats a scattered network parse followed by a separate download.
                val downloadedUri: Uri? = if (adoptFile != null) {
                    val targetFolder =
                        downloadPreferences.downloadFolderUri.firstOrNull()?.let { Uri.parse(it) }
                    val (destUri, undoMove) = moveToLocation(adoptFile, bookId, fileName, targetFolder) { read ->
                        onProgress(ImportProgress(ImportProgress.Phase.Downloading, read, adoptFile.length()))
                    }
                    cleanup += undoMove
                    destUri
                } else if (isRemoteUri(uri)) {
                    val total = sourceMeta.size.takeIf { it > 0 } ?: -1L
                    onProgress(ImportProgress(ImportProgress.Phase.Downloading, 0, total))
                    val targetFolder =
                        downloadPreferences.downloadFolderUri.firstOrNull()?.let { Uri.parse(it) }
                    val (destUri, undoDownload) = downloadToLocation(
                        sourceUri = uri,
                        bookId = bookId,
                        fileName = fileName,
                        targetFolder = targetFolder
                    ) { read -> onProgress(ImportProgress(ImportProgress.Phase.Downloading, read, total)) }
                    cleanup += undoDownload
                    destUri
                } else null

                // Parse the local copy when we downloaded one, otherwise the original SAF URI.
                val parseSource = downloadedUri ?: uri
                onProgress(ImportProgress(ImportProgress.Phase.Parsing))
                val parsed = M4bSource.open(context, parseSource).use { parser.parse(it) }

                val (coverPath, imageEntities) = writeImagesToCache(bookId, parsed.images)
                if (coverPath != null) cleanup += { bookImageDir(bookId).deleteRecursively() }

                val displayTitle = parsed.title?.takeIf { it.isNotBlank() }
                    ?: fileName?.removeSuffix(".m4b")?.removeSuffix(".M4B")
                    ?: "Untitled"

                val finalUri = downloadedUri?.toString() ?: uri.toString()
                val finalSize = downloadedUri?.let { sizeOf(it) } ?: sourceMeta.size

                onProgress(ImportProgress(ImportProgress.Phase.Finalizing))
                val book = BookEntity(
                    id = bookId,
                    uri = finalUri,
                    title = displayTitle,
                    author = parsed.author?.takeIf { it.isNotBlank() },
                    album = parsed.album?.takeIf { it.isNotBlank() },
                    durationMs = parsed.durationMs,
                    coverPath = coverPath,
                    importedAt = System.currentTimeMillis(),
                    fileSize = finalSize,
                    isDownloaded = downloadedUri != null,
                    collectionId = collectionId
                )
                val chapters = parsed.chapters.mapIndexed { index, ch ->
                    ChapterEntity(
                        bookId = bookId,
                        orderIndex = index,
                        title = ch.title,
                        startMs = ch.startMs
                    )
                }
                database.withTransaction {
                    bookDao.upsert(book)
                    chapterDao.insertAll(chapters)
                    imageDao.insertAll(imageEntities)
                }
                cleanup.clear()

                if (downloadedUri != null) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }

                book.toDomain()
            } finally {
                cleanup.forEach { runCatching { it() } }
            }
        }
    }

    suspend fun delete(bookId: String) = withContext(Dispatchers.IO) {
        val entity = bookDao.byId(bookId)
        if (entity != null && entity.isDownloaded) {
            deleteDownloadedFile(Uri.parse(entity.uri))
        }
        val imageDir = bookImageDir(bookId)
        if (imageDir.exists()) imageDir.deleteRecursively()
        val downloadDir = bookDownloadDir(bookId)
        if (downloadDir.exists()) downloadDir.deleteRecursively()
        val companionDir = companionDir(bookId)
        if (companionDir.exists()) companionDir.deleteRecursively()
        val extractionDir = epubExtractionDir(bookId)
        if (extractionDir.exists()) extractionDir.deleteRecursively()
        database.withTransaction {
            chapterDao.deleteForBook(bookId)
            imageDao.deleteForBook(bookId)
            readingPositionDao.delete(bookId)
            bookDao.delete(bookId)
        }
    }

    // ── companions (EPUB + sync manifest) ────────────────────────────────────────

    suspend fun attachEpub(bookId: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(companionDir(bookId).also { it.mkdirs() }, "book.epub")
            copyUriToFile(uri, target)
            bookDao.updateEpubPath(bookId, target.absolutePath)
        }
    }

    suspend fun attachSync(bookId: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(companionDir(bookId).also { it.mkdirs() }, "sync.json")
            copyUriToFile(uri, target)
            bookDao.updateSyncPath(bookId, target.absolutePath)
        }
    }

    suspend fun detachEpub(bookId: String) = withContext(Dispatchers.IO) {
        bookDao.byId(bookId)?.epubPath?.let { runCatching { File(it).delete() } }
        // Drop any extracted EPUB working dir too (created by the reader).
        epubExtractionDir(bookId).let { if (it.exists()) it.deleteRecursively() }
        bookDao.updateEpubPath(bookId, null)
    }

    suspend fun detachSync(bookId: String) = withContext(Dispatchers.IO) {
        bookDao.byId(bookId)?.syncPath?.let { runCatching { File(it).delete() } }
        bookDao.updateSyncPath(bookId, null)
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        (context.contentResolver.openInputStream(uri)
            ?: error("Couldn't open $uri")).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
    }

    private fun companionDir(bookId: String): File =
        File(context.filesDir, "companions/$bookId")

    /** Where the reader unzips a book's EPUB; must match `ReaderViewModel` / `EpubWebView`. */
    private fun epubExtractionDir(bookId: String): File =
        File(context.filesDir, "epubs/$bookId")

    // ── zips ───────────────────────────────────────────────────────────────────

    private fun isZip(uri: Uri, fileName: String?): Boolean {
        if (fileName != null) return fileName.endsWith(".zip", ignoreCase = true)
        // No name to go by: trust the provider's type. Not "application/epub+zip" — an EPUB is a
        // zip too, but it is a book on its own.
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return type == "application/zip" || type == "application/x-zip-compressed"
    }

    /**
     * Imports every book in a zip ([ZipBookPlanner] decides what is a book). Entries are unpacked
     * into a staging folder beside the app's own files, so each audiobook is then moved, not
     * copied, into place; the staging folder is removed whatever happens.
     *
     * Several books go into a collection, named after the zipped folder or the zip itself, and an
     * existing collection of that name is reused, so the next volumes of a series join the earlier
     * ones. A single book never gets a collection of its own — a Drive download wraps even one book
     * in a folder. When the import starts inside a collection, everything goes into that one.
     *
     * One book failing doesn't stop the others; the outcome lists what failed.
     */
    private suspend fun importZip(
        uri: Uri,
        meta: UriMeta,
        collectionId: String?,
        onProgress: (ImportProgress) -> Unit
    ): Result<ImportOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.filesDir, "import-staging/${UUID.randomUUID()}")
            try {
                val total = meta.size.takeIf { it > 0 } ?: -1L
                onProgress(ImportProgress(ImportProgress.Phase.Extracting, 0, total))
                val extracted = ZipExtractor.extract(
                    open = { context.contentResolver.openInputStream(uri) ?: error("Couldn't open the zip.") },
                    dir = staging
                ) { read -> onProgress(ImportProgress(ImportProgress.Phase.Extracting, read, total)) }

                // A .json only counts if it is a sync manifest; the planner can't look inside.
                val usable = extracted.filter { path ->
                    !path.endsWith(".json", ignoreCase = true) || isSyncManifest(File(staging, path))
                }
                val plan = ZipBookPlanner.plan(usable)
                require(plan.books.isNotEmpty()) { "There is no .m4b or .epub in this zip." }

                var createdCollection: String? = null
                var collectionName: String? = null
                val targetCollection = collectionId ?: if (plan.books.size > 1) {
                    val name = ZipBookPlanner.collectionName(plan, meta.name)
                    collectionName = name
                    database.collectionDao().byName(name)?.id ?: UUID.randomUUID().toString().also { id ->
                        database.collectionDao().upsert(
                            CollectionEntity(id = id, name = name, createdAt = System.currentTimeMillis())
                        )
                        createdCollection = id
                    }
                } else null

                val books = mutableListOf<Book>()
                val failures = mutableListOf<Pair<String, String>>()
                plan.books.forEachIndexed { index, zipBook ->
                    val primary = File(staging, zipBook.primary)
                    val forBook: (ImportProgress) -> Unit = {
                        onProgress(it.copy(bookIndex = index + 1, bookCount = plan.books.size))
                    }
                    val result = if (zipBook.audio != null) {
                        importAudio(
                            uri = primary.toUri(),
                            sourceMeta = UriMeta(primary.name, primary.length()),
                            collectionId = targetCollection,
                            onProgress = forBook,
                            adoptFile = primary
                        ).onSuccess { book ->
                            zipBook.epub?.let { attachEpub(book.id, File(staging, it).toUri()) }
                            zipBook.sync?.let { attachSync(book.id, File(staging, it).toUri()) }
                        }
                    } else {
                        importEpub(
                            uri = primary.toUri(),
                            sourceMeta = UriMeta(primary.name, primary.length()),
                            collectionId = targetCollection,
                            onProgress = forBook
                        )
                    }
                    result
                        .onSuccess { books += it }
                        .onFailure { failures += primary.name to (it.message ?: it.javaClass.simpleName) }
                }

                if (books.isEmpty()) {
                    createdCollection?.let { database.collectionDao().delete(it) }
                    val (name, why) = failures.first()
                    error("Couldn't import $name: $why")
                }
                // Re-read: attaching an EPUB or a sync manifest updated the rows after import.
                val fresh = books.map { book -> bookDao.byId(book.id)?.toDomain() ?: book }
                ImportOutcome(fresh, failures, collectionName)
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    /** A sync manifest is the JSON ln-vox writes: a `beats` list mapping audio to EPUB spans. */
    private fun isSyncManifest(file: File): Boolean = runCatching {
        file.length() < MAX_MANIFEST_BYTES && file.readText().contains("\"beats\"")
    }.getOrDefault(false)

    /**
     * Moves an audio file that's already on the device into the book's place. The default download
     * location is on the same storage as the staging folder, so this is a rename: no copy, no
     * second gigabyte. A download folder the user picked is somewhere else entirely and gets a
     * copy instead, with the original removed afterwards.
     */
    private fun moveToLocation(
        file: File,
        bookId: String,
        fileName: String?,
        targetFolder: Uri?,
        onBytes: (Long) -> Unit
    ): Pair<Uri, () -> Unit> {
        if (targetFolder != null) {
            return downloadToLocation(file.toUri(), bookId, fileName, targetFolder, onBytes)
                .also { file.delete() }
        }
        val dir = bookDownloadDir(bookId).also { it.mkdirs() }
        val target = File(dir, (fileName ?: "$bookId.m4b").replace(File.separatorChar, '_'))
        if (!file.renameTo(target)) {
            file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            file.delete()
        }
        return target.toUri() to { dir.deleteRecursively() }
    }

    // ── downloads ──────────────────────────────────────────────────────────────

    /** Returns the destination Uri and a callback that removes the file if the import is aborted. */
    private fun downloadToLocation(
        sourceUri: Uri,
        bookId: String,
        fileName: String?,
        targetFolder: Uri?,
        onBytes: (Long) -> Unit
    ): Pair<Uri, () -> Unit> {
        val safeName = (fileName ?: "$bookId.m4b").replace(File.separatorChar, '_')
        return if (targetFolder == null) {
            val dir = bookDownloadDir(bookId).also { it.mkdirs() }
            val target = File(dir, safeName)
            try {
                target.outputStream().buffered().use { copyStream(sourceUri, it, onBytes) }
            } catch (t: Throwable) {
                runCatching { target.delete() }
                runCatching { dir.delete() }
                throw t
            }
            target.toUri() to { dir.deleteRecursively() }
        } else {
            val tree = DocumentFile.fromTreeUri(context, targetFolder)
                ?: error("Configured download folder is no longer accessible.")
            // Prefix with the bookId so duplicate file names don't collide.
            val displayName = "${bookId.take(8)}_$safeName"
            val newFile = tree.createFile("audio/mp4", displayName)
                ?: error("Couldn't create $displayName in the download folder.")
            try {
                val out = context.contentResolver.openOutputStream(newFile.uri)
                    ?: error("Couldn't open ${newFile.uri} for writing.")
                out.use { copyStream(sourceUri, it, onBytes) }
            } catch (t: Throwable) {
                runCatching { newFile.delete() }
                throw t
            }
            newFile.uri to { runCatching { newFile.delete() } }
        }
    }

    private fun copyStream(sourceUri: Uri, output: OutputStream, onBytes: (Long) -> Unit) {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: error("Couldn't open $sourceUri for reading.")
        input.use {
            val buf = ByteArray(64 * 1024)
            var copied = 0L
            while (true) {
                val n = it.read(buf)
                if (n <= 0) break
                output.write(buf, 0, n)
                copied += n
                onBytes(copied)
            }
            output.flush()
        }
    }

    private fun deleteDownloadedFile(uri: Uri) {
        when (uri.scheme) {
            "file" -> runCatching {
                val file = uri.toFile()
                if (file.exists()) file.delete()
                val parent = file.parentFile
                if (parent?.isDirectory == true && parent.list()?.isEmpty() == true) parent.delete()
            }
            "content" -> runCatching {
                DocumentFile.fromSingleUri(context, uri)?.delete()
            }
        }
    }

    private fun sizeOf(uri: Uri): Long = when (uri.scheme) {
        "file" -> runCatching { uri.toFile().length() }.getOrDefault(0L)
        "content" -> fileSizeFromUri(uri)
        else -> 0L
    }

    private fun isRemoteUri(uri: Uri): Boolean {
        // SAF authorities for local providers all use com.android.*. Everything else
        // (Drive, OneDrive, Dropbox, etc.) is treated as remote and downloaded so the
        // player doesn't have to stream over the network.
        val authority = uri.authority ?: return false
        return !authority.startsWith("com.android.")
    }

    // ── images cache ───────────────────────────────────────────────────────────

    private fun writeImagesToCache(
        bookId: String,
        images: List<ParsedImage>
    ): Pair<String?, List<EmbeddedImageEntity>> {
        if (images.isEmpty()) return null to emptyList()
        val dir = bookImageDir(bookId).also { it.mkdirs() }
        val entities = images.mapIndexed { index, img ->
            val ext = when (img.mimeType) {
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                else -> "img"
            }
            val file = File(dir, "$index.$ext")
            file.writeBytes(img.bytes)
            EmbeddedImageEntity(
                bookId = bookId,
                orderIndex = index,
                mimeType = img.mimeType,
                cachePath = file.absolutePath
            )
        }
        return entities.first().cachePath to entities
    }

    private fun bookImageDir(bookId: String): File =
        File(context.filesDir, "books/$bookId/images")

    private fun bookDownloadDir(bookId: String): File =
        File(context.filesDir, "downloads/$bookId")

    private data class UriMeta(val name: String?, val size: Long)

    /**
     * Fetches display name and size in a single [android.content.ContentResolver] query. For cloud
     * document providers each metadata lookup is a network round-trip, so reading both columns at
     * once is markedly faster than two separate [DocumentFile] calls. Returns nulls/0 if the
     * provider doesn't supply a column or the query fails.
     */
    private fun queryUriMeta(uri: Uri): UriMeta = runCatching {
        if (uri.scheme == "file") {
            val file = uri.toFile()
            return UriMeta(name = file.name, size = file.length())
        }
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use UriMeta(null, 0L)
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            UriMeta(
                name = nameIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString),
                size = sizeIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong) ?: 0L
            )
        }
    }.getOrNull() ?: UriMeta(null, 0L)

    private fun fileSizeFromUri(uri: Uri): Long =
        DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L

    private companion object {
        /** No real sync manifest comes close; anything bigger isn't worth reading to find out. */
        const val MAX_MANIFEST_BYTES = 64L * 1024 * 1024
    }
}
