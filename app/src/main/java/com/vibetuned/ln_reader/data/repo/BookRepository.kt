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
import com.vibetuned.ln_reader.data.db.EmbeddedImageDao
import com.vibetuned.ln_reader.data.db.EmbeddedImageEntity
import com.vibetuned.ln_reader.data.db.LnReaderDatabase
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
    private val parser: M4bParser,
    private val downloadPreferences: DownloadPreferences
) {

    data class ImportProgress(
        val phase: Phase,
        val bytesRead: Long = 0,
        val totalBytes: Long = -1
    ) {
        enum class Phase { Parsing, Downloading, Finalizing }
    }

    fun books(): Flow<List<Book>> =
        bookDao.observeAll().map { list -> list.map { it.toDomain() } }

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
     * Import an .m4b. If [uri] points at a cloud provider (Drive, OneDrive, etc.), the file is
     * copied locally so playback streams from disk instead of the network. The destination is
     * whatever [DownloadPreferences] points at, defaulting to the app's internal storage.
     * Local SAF URIs are referenced in place.
     */
    suspend fun import(
        uri: Uri,
        onProgress: (ImportProgress) -> Unit = {}
    ): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val bookId = UUID.randomUUID().toString()
            // One metadata query for both name and size: for cloud document providers each lookup
            // is a network round-trip, so fetching both columns at once (instead of two separate
            // DocumentFile reads) halves the latency before the download can begin.
            val sourceMeta = queryUriMeta(uri)
            val fileName = sourceMeta.name
            val cleanup = mutableListOf<() -> Unit>()
            try {
                // For cloud sources, download the whole file first and parse the local copy.
                // Parsing has to read most of the file anyway, so a single sequential download
                // beats a scattered network parse followed by a separate download.
                val downloadedUri: Uri? = if (isRemoteUri(uri)) {
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
                    isDownloaded = downloadedUri != null
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
        database.withTransaction {
            chapterDao.deleteForBook(bookId)
            imageDao.deleteForBook(bookId)
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
        File(context.filesDir, "epubs/$bookId").let { if (it.exists()) it.deleteRecursively() }
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
}
