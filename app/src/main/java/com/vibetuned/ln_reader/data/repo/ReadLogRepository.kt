package com.vibetuned.ln_reader.data.repo

import com.vibetuned.ln_reader.data.db.READ_LOG_KIND_LISTEN
import com.vibetuned.ln_reader.data.db.READ_LOG_KIND_READ
import com.vibetuned.ln_reader.data.db.ReadLogDao
import com.vibetuned.ln_reader.data.db.ReadLogEntity
import com.vibetuned.ln_reader.data.model.ReadLogEntry
import com.vibetuned.ln_reader.data.model.ReadLogKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.UUID

/**
 * The usage log: one row per listening or reading session, for the time-series view to come.
 *
 * Lifecycle of a row is start → heartbeat… → end. [start] opens it with `endedAt == startedAt`;
 * [heartbeat] advances `endedAt` while the session runs (so a process killed mid-session still
 * leaves an honest duration, to within the heartbeat interval); [end] closes it. Callers hold the
 * returned id for the session's life and never reuse it.
 *
 * Nothing here deletes rows when a book is removed: usage history outlives the book, which is
 * why each row snapshots the title.
 */
class ReadLogRepository(
    private val dao: ReadLogDao
) {
    fun observeAll(): Flow<List<ReadLogEntry>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun between(fromMs: Long, toMs: Long): List<ReadLogEntry> =
        dao.between(fromMs, toMs).map { it.toDomain() }

    suspend fun forBook(bookId: String): List<ReadLogEntry> =
        dao.forBook(bookId).map { it.toDomain() }

    /**
     * Sessions bucketed for the usage chart. Only rows that reach into the charted window are
     * read, so the query stays small however long the history gets.
     */
    suspend fun stats(
        granularity: StatsGranularity,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ReadingStats {
        val from = ReadingStatsBuilder.windowStartMs(granularity, nowMs, zone)
        val entries = dao.endingAfter(from).map { it.toDomain() }
        return ReadingStatsBuilder.build(entries, granularity, nowMs, zone)
    }

    /** One page of history, newest first — the details list loads these as it scrolls. */
    suspend fun page(limit: Int, offset: Int): List<ReadLogEntry> =
        dao.page(limit, offset).map { it.toDomain() }

    suspend fun count(): Int = dao.count()

    /** Replaces all history. Debug seeding only — nothing in the app deletes the log. */
    suspend fun replaceAll(entries: List<ReadLogEntry>) {
        dao.deleteAll()
        entries.forEach { e ->
            dao.insert(
                ReadLogEntity(
                    id = e.id,
                    bookId = e.bookId,
                    bookTitle = e.bookTitle,
                    kind = when (e.kind) {
                        ReadLogKind.Listen -> READ_LOG_KIND_LISTEN
                        ReadLogKind.Read -> READ_LOG_KIND_READ
                    },
                    startedAt = e.startedAt,
                    endedAt = e.endedAt,
                    startPosition = e.startPosition,
                    endPosition = e.endPosition
                )
            )
        }
    }

    /** Opens a session and returns its id. [position] is playback ms (listen) or spine index (read). */
    suspend fun start(bookId: String, bookTitle: String, kind: ReadLogKind, position: Long): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insert(
            ReadLogEntity(
                id = id,
                bookId = bookId,
                bookTitle = bookTitle,
                kind = when (kind) {
                    ReadLogKind.Listen -> READ_LOG_KIND_LISTEN
                    ReadLogKind.Read -> READ_LOG_KIND_READ
                },
                startedAt = now,
                endedAt = now,
                startPosition = position,
                endPosition = position
            )
        )
        return id
    }

    /** Marks the session as still running, as of now, at [position]. */
    suspend fun heartbeat(sessionId: String, position: Long) {
        dao.updateEnd(sessionId, System.currentTimeMillis(), position)
    }

    /** Closes the session as of now, at [position]. Idempotent. */
    suspend fun end(sessionId: String, position: Long) {
        dao.updateEnd(sessionId, System.currentTimeMillis(), position)
    }
}
