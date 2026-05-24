package com.vibetuned.ln_reader.m4b

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Walks an MP4 atom tree over an [M4bSource]. Atom layout:
 *   4 bytes big-endian size, 4 bytes type, [8 bytes extended size if size == 1], payload.
 * Size == 0 means "to end of parent".
 *
 * The `meta` atom is a full box: 4 byte version+flags prefix before its children.
 * Children of `ilst` are keyed metadata items whose payload contains one or more
 * `data` atoms (8-byte type+locale prefix, then raw value bytes).
 */
class AtomReader(val source: M4bSource) {

    data class Atom(
        val type: String,
        val offset: Long,
        val headerSize: Int,
        val totalSize: Long
    ) {
        val payloadOffset: Long get() = offset + headerSize
        val payloadSize: Long get() = totalSize - headerSize
        val payloadEnd: Long get() = offset + totalSize
    }

    fun topLevelAtoms(): List<Atom> = atomsIn(0L, source.size)

    fun children(parent: Atom, skipPayloadBytes: Int = 0): List<Atom> =
        atomsIn(parent.payloadOffset + skipPayloadBytes, parent.payloadEnd)

    /** Children of `meta`, accounting for its 4-byte version/flags prefix. */
    fun metaChildren(meta: Atom): List<Atom> = children(meta, skipPayloadBytes = 4)

    fun findChild(parent: Atom?, type: String): Atom? {
        val atoms = if (parent == null) topLevelAtoms() else children(parent)
        return atoms.firstOrNull { it.type == type }
    }

    fun findChildren(parent: Atom?, type: String): List<Atom> {
        val atoms = if (parent == null) topLevelAtoms() else children(parent)
        return atoms.filter { it.type == type }
    }

    fun readPayload(atom: Atom): ByteArray {
        val len = atom.payloadSize
        require(len <= Int.MAX_VALUE) { "atom payload too large: $len" }
        return source.readAt(atom.payloadOffset, len.toInt())
    }

    private fun atomsIn(start: Long, end: Long): List<Atom> {
        val out = ArrayList<Atom>()
        var pos = start
        while (pos + 8 <= end) {
            val header = source.readAt(pos, 8)
            val size32 = readU32(header, 0)
            val type = String(header, 4, 4, StandardCharsets.ISO_8859_1)
            val total: Long
            val headerSize: Int
            when (size32) {
                1L -> {
                    val ext = source.readAt(pos + 8, 8)
                    total = readU64(ext, 0)
                    headerSize = 16
                }
                0L -> {
                    total = end - pos
                    headerSize = 8
                }
                else -> {
                    total = size32
                    headerSize = 8
                }
            }
            if (total < headerSize || pos + total > end) break
            out.add(Atom(type, pos, headerSize, total))
            pos += total
        }
        return out
    }

    companion object {
        fun readU32(buf: ByteArray, off: Int): Long =
            ByteBuffer.wrap(buf, off, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL

        fun readU64(buf: ByteArray, off: Int): Long =
            ByteBuffer.wrap(buf, off, 8).order(ByteOrder.BIG_ENDIAN).long
    }
}
