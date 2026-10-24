package com.vibetuned.ln_reader.data.repo

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Pulls the files that could make a book ([ZipBookPlanner.isCandidate]) out of a zip, keeping their
 * paths. Everything else in the archive is never written.
 *
 * It streams first: one pass over the source, so a zip still on Drive is read straight off the
 * network rather than downloaded whole and then unpacked, and nothing but the wanted files ever
 * touches the disk. A few archives can't be streamed — an entry stored uncompressed whose sizes
 * come after its data, as some web and scripting zip writers produce, or names in an old non-UTF-8
 * encoding. For those it copies the zip next to the output and reads it through its central
 * directory instead, which handles every case, at the cost of a second copy of the archive while
 * it works.
 */
internal object ZipExtractor {

    /**
     * Extracts into [dir], which should be empty, and returns the extracted files' paths relative
     * to it, with forward slashes. [onBytes] is told how many bytes of the zip have been read so
     * far, against the zip's own size.
     */
    fun extract(open: () -> InputStream, dir: File, onBytes: (Long) -> Unit): List<String> {
        dir.mkdirs()
        return try {
            open().use { source -> CountingInputStream(source, onBytes).use { stream(it, dir) } }
        } catch (e: ZipException) {
            fromLocalCopy(open, dir, onBytes)
        } catch (e: IllegalArgumentException) {
            // ZipInputStream's way of reporting entry names it can't decode as UTF-8.
            fromLocalCopy(open, dir, onBytes)
        }
    }

    private fun stream(input: InputStream, dir: File): List<String> {
        val written = mutableListOf<String>()
        ZipInputStream(input.buffered(BUFFER)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                // An uncompressed entry gives no way to find its end except the size in its header.
                // When the writer put the sizes after the data instead, OpenJDK's reader throws,
                // but Android's reads the entry as empty and carries on misaligned — silently
                // corrupting this file and everything after it. Detect it and take the slow path.
                // (A genuinely empty stored file looks the same; the slow path handles that too.)
                if (entry.method == ZipEntry.STORED && entry.compressedSize <= 0L) {
                    throw ZipException("Stored entry ${entry.name} has no size ahead of its data")
                }
                write(entry.name, dir, written) { out -> zip.copyTo(out, BUFFER) }
            }
        }
        return written
    }

    private fun fromLocalCopy(open: () -> InputStream, dir: File, onBytes: (Long) -> Unit): List<String> {
        // Start over: whatever the streaming pass got through before giving up may be partial.
        dir.deleteRecursively()
        dir.mkdirs()
        val archive = File(dir.parentFile, "${dir.name}.zip")
        try {
            open().use { source ->
                CountingInputStream(source, onBytes).use { input ->
                    archive.outputStream().use { input.copyTo(it, BUFFER) }
                }
            }
            return try {
                fromZipFile(archive, dir, Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                // Not UTF-8: zips from older Windows tools name entries in code page 437.
                dir.deleteRecursively()
                dir.mkdirs()
                fromZipFile(archive, dir, Charset.forName("IBM437"))
            }
        } finally {
            archive.delete()
        }
    }

    private fun fromZipFile(archive: File, dir: File, charset: Charset): List<String> {
        val written = mutableListOf<String>()
        ZipFile(archive, charset).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                write(entry.name, dir, written) { out ->
                    zip.getInputStream(entry).use { it.copyTo(out, BUFFER) }
                }
            }
        }
        return written
    }

    private inline fun write(
        name: String,
        dir: File,
        written: MutableList<String>,
        copy: (java.io.OutputStream) -> Unit
    ) {
        if (!ZipBookPlanner.isCandidate(name)) return
        val path = ZipBookPlanner.normalize(name)
        // Zip slip: an entry named "../../something" must not land outside the output directory.
        val target = File(dir, path)
        val root = dir.canonicalPath + File.separator
        if (!target.canonicalPath.startsWith(root)) return
        target.parentFile?.mkdirs()
        target.outputStream().use(copy)
        written += path
    }

    /** Reports the running count of bytes read, so extraction can show progress against the zip size. */
    private class CountingInputStream(input: InputStream, private val onBytes: (Long) -> Unit) :
        FilterInputStream(input) {
        private var count = 0L
        private var lastReported = 0L

        override fun read(): Int = super.read().also { if (it >= 0) advance(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

        override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) advance(it) }

        private fun advance(n: Long) {
            count += n
            // Every quarter megabyte: often enough to look smooth, rarely enough to stay cheap.
            if (count - lastReported >= 256 * 1024) {
                lastReported = count
                onBytes(count)
            }
        }
    }

    private const val BUFFER = 64 * 1024
}
