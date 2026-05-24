package com.vibetuned.ln_reader.m4b

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Random-access read over a SAF Uri. Open once, read many times via [readAt].
 * Caller owns lifecycle and must [close].
 */
class M4bSource private constructor(
    private val pfd: ParcelFileDescriptor,
    private val channel: FileChannel,
    val size: Long
) : Closeable {

    fun readAt(offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length >= 0) { "offset and length must be non-negative" }
        require(offset + length <= size) { "read past end: $offset + $length > $size" }
        val buf = ByteBuffer.allocate(length)
        var pos = offset
        while (buf.hasRemaining()) {
            val read = channel.read(buf, pos)
            if (read < 0) break
            pos += read
        }
        return buf.array()
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { pfd.close() }
    }

    companion object {
        fun open(context: Context, uri: Uri): M4bSource {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Could not open file descriptor for $uri")
            val stream = FileInputStream(pfd.fileDescriptor)
            val channel = stream.channel
            return M4bSource(pfd, channel, channel.size())
        }
    }
}
