package com.vibetuned.ln_reader.player

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.repo.BookRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/**
 * Tiny HTTP server that makes the current book reachable by a Cast receiver.
 *
 * A Chromecast pulls media itself over HTTP; it can't read this phone's file/content URIs. While
 * a cast session is active, [PlaybackService] runs this server and the cast media items point at
 * it: `/t/<token>/book/<bookId>` streams the m4b (with Range support, required for seeking) and
 * `/t/<token>/cover/<bookId>` serves the cover for the TV screen. Anything not carrying the
 * per-process random token gets a 404, so other devices on the network can't browse the library.
 */
class CastMediaServer(
    context: Context,
    private val bookRepository: BookRepository
) : NanoHTTPD(AUTO_PORT) {

    private val appContext = context.applicationContext
    private val token = UUID.randomUUID().toString().replace("-", "")

    fun startIfNeeded() {
        if (!isAlive) start(SOCKET_READ_TIMEOUT, /* daemon = */ true)
    }

    fun stopIfRunning() {
        if (isAlive) stop()
    }

    /** URL the receiver should stream the book from, or null when the server isn't reachable. */
    fun bookUrl(bookId: String): Uri? = baseUrl()?.buildUpon()
        ?.appendPath("book")?.appendPath(bookId)?.build()

    fun coverUrl(bookId: String): Uri? = baseUrl()?.buildUpon()
        ?.appendPath("cover")?.appendPath(bookId)?.build()

    private fun baseUrl(): Uri? {
        if (!isAlive) return null
        val ip = deviceIp() ?: return null
        return "http://$ip:$listeningPort/t/$token".toUri()
    }

    /** The phone's site-local IPv4 — the address a receiver on the same Wi-Fi can reach. */
    private fun deviceIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    override fun serve(session: IHTTPSession): Response {
        val parts = session.uri.trim('/').split('/')
        if (parts.size != 4 || parts[0] != "t" || parts[1] != token) return notFound()
        val book = runBlocking { bookRepository.getDetail(parts[3]) }?.book ?: return notFound()
        return when (parts[2]) {
            "book" -> runCatching { serveBook(book, session.headers["range"]) }
                .getOrElse { notFound() }
            "cover" -> runCatching { serveCover(book) }.getOrElse { notFound() }
            else -> notFound()
        }
    }

    private fun serveBook(book: Book, rangeHeader: String?): Response {
        val uri = book.uri.toUri()
        val total = contentLength(uri)
        val range = parseRange(rangeHeader, total)
        return if (range == null || total <= 0) {
            newFixedLengthResponse(Response.Status.OK, AUDIO_MIME, openStream(uri, 0L), total)
                .apply { addHeader("Accept-Ranges", "bytes") }
        } else {
            val (start, end) = range
            newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                AUDIO_MIME,
                openStream(uri, start),
                end - start + 1
            ).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Range", "bytes $start-$end/$total")
            }
        }
    }

    private fun serveCover(book: Book): Response {
        val file = book.coverPath?.let(::File)?.takeIf { it.isFile } ?: return notFound()
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return newFixedLengthResponse(Response.Status.OK, mime, file.inputStream(), file.length())
    }

    /** Handles the `bytes=start-` and `bytes=start-end` forms Cast receivers send. */
    private fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
        if (header == null || total <= 0) return null
        val m = RANGE_PATTERN.matchEntire(header.trim()) ?: return null
        val start = m.groupValues[1].toLongOrNull() ?: return null
        if (start >= total) return null
        val end = m.groupValues[2].toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
        if (end < start) return null
        return start to end
    }

    private fun contentLength(uri: Uri): Long = when (uri.scheme) {
        "content" -> appContext.contentResolver.openAssetFileDescriptor(uri, "r")
            ?.use { it.length } ?: -1L
        "file" -> File(uri.path!!).length()
        else -> File(uri.toString()).length()
    }

    private fun openStream(uri: Uri, offset: Long): InputStream {
        val stream = when (uri.scheme) {
            "content" -> appContext.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Can't open $uri")
            "file" -> File(uri.path!!).inputStream()
            else -> File(uri.toString()).inputStream()
        }
        var remaining = offset
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
        return stream
    }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")

    companion object {
        /** 0 lets the OS pick a free port; [getListeningPort] reports it once started. */
        private const val AUTO_PORT = 0
        private const val AUDIO_MIME = "audio/mp4"
        private val RANGE_PATTERN = Regex("""bytes=(\d+)-(\d*)""")
    }
}
