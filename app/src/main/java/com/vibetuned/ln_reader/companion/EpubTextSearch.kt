package com.vibetuned.ln_reader.companion

import java.io.File

/** One search hit: the k-th occurrence of the query on a spine page, with a display snippet. */
data class EpubSearchMatch(
    val spineIndex: Int,
    /** 0-based index of this match among the matches on its page, in document order. */
    val occurrence: Int,
    val snippet: String,
    /** Range of the matched text inside [snippet], for emphasis in the results list. */
    val matchStart: Int,
    val matchLength: Int
)

/**
 * Whole-book plain-text search over an extracted EPUB.
 *
 * The reader highlights hits by walking the WebView's DOM text nodes in JS and wrapping the k-th
 * regex match. For [occurrence][EpubSearchMatch.occurrence] to point at the right highlight, both
 * sides must see the same text in the same order, so [extractText] mirrors what the DOM walk
 * concatenates: markup contributes nothing (no implied separator), entities are decoded, and
 * head/script/style content is dropped. Queries match with flexible whitespace (any run of
 * whitespace or NBSP in the document matches a single space in the query) on both sides.
 */
object EpubTextSearch {

    /** Hard cap so a one-letter query on a long book can't build an unbounded result list. */
    const val MAX_RESULTS = 500

    private const val SNIPPET_CONTEXT_CHARS = 60

    fun search(rootDir: File, spinePaths: List<String>, query: String): List<EpubSearchMatch> {
        val pattern = kotlinPattern(query) ?: return emptyList()
        val results = ArrayList<EpubSearchMatch>()
        for ((spineIndex, path) in spinePaths.withIndex()) {
            val html = runCatching { File(rootDir, path).readText() }.getOrNull() ?: continue
            val text = extractText(html)
            var occurrence = 0
            for (match in pattern.findAll(text)) {
                results += snippetFor(text, match, spineIndex, occurrence)
                occurrence++
                if (results.size >= MAX_RESULTS) return results
            }
        }
        return results
    }

    private fun kotlinPattern(query: String): Regex? {
        val words = splitQuery(query) ?: return null
        return Regex(
            words.joinToString(WHITESPACE_RUN) { Regex.escape(it) },
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * The same pattern as [kotlinPattern] but escaped for JS `new RegExp(...)` — Kotlin's
     * [Regex.escape] uses `\Q...\E`, which JS doesn't understand. Null for a blank query.
     */
    fun jsPattern(query: String): String? {
        val words = splitQuery(query) ?: return null
        return words.joinToString(WHITESPACE_RUN) { word ->
            word.replace(JS_REGEX_SPECIALS) { "\\" + it.value }
        }
    }

    private fun splitQuery(query: String): List<String>? =
        query.trim().split(Regex(WHITESPACE_RUN)).filter { it.isNotEmpty() }.ifEmpty { null }

    // Java's \s does not match NBSP while JS's does, so both patterns name it explicitly.
    private const val WHITESPACE_RUN = """[\s\u00A0]+"""
    private val JS_REGEX_SPECIALS = Regex("""[.*+?^${'$'}{}()|\[\]\\]""")

    private val HEAD_BLOCK = Regex("(?is)<head[^>]*>.*?</head>")
    private val SCRIPT_STYLE_BLOCK = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val COMMENT = Regex("(?s)<!--.*?-->")
    private val TAG = Regex("<[^>]+>")

    internal fun extractText(html: String): String =
        decodeEntities(
            html.replace(HEAD_BLOCK, "")
                .replace(SCRIPT_STYLE_BLOCK, "")
                .replace(COMMENT, "")
                .replace(TAG, "")
        )

    private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")

    // The named entities that actually show up in EPUB prose. An unknown entity is left verbatim,
    // which can only make a match harder to find, never mis-count an occurrence the JS side sees
    // differently at an earlier position — both sides simply won't match inside it.
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to "\u00A0", "shy" to "\u00AD",
        "mdash" to "—", "ndash" to "–", "hellip" to "…",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”"
    )

    private fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).toIntOrNull(16)?.toCharsOrNull() ?: m.value
            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.toCharsOrNull() ?: m.value
            else -> NAMED_ENTITIES[body] ?: m.value
        }
    }

    private fun Int.toCharsOrNull(): String? =
        if (Character.isValidCodePoint(this)) String(Character.toChars(this)) else null

    private val WHITESPACE = Regex(WHITESPACE_RUN)

    private fun snippetFor(
        text: String,
        match: MatchResult,
        spineIndex: Int,
        occurrence: Int
    ): EpubSearchMatch {
        val start = match.range.first
        val end = match.range.last + 1
        val preRaw = text.substring((start - SNIPPET_CONTEXT_CHARS).coerceAtLeast(0), start)
        val postRaw = text.substring(end, (end + SNIPPET_CONTEXT_CHARS).coerceAtMost(text.length))
        val prefix = if (start > SNIPPET_CONTEXT_CHARS) "…" else ""
        val suffix = if (end + SNIPPET_CONTEXT_CHARS < text.length) "…" else ""
        val pre = prefix + preRaw.replace(WHITESPACE, " ").trimStart()
        val matched = match.value.replace(WHITESPACE, " ")
        val post = postRaw.replace(WHITESPACE, " ").trimEnd() + suffix
        return EpubSearchMatch(
            spineIndex = spineIndex,
            occurrence = occurrence,
            snippet = pre + matched + post,
            matchStart = pre.length,
            matchLength = matched.length
        )
    }
}
