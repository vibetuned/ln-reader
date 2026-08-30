package com.vibetuned.ln_reader

import com.vibetuned.ln_reader.companion.EpubTextSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpubTextSearchTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun page(name: String, body: String): String {
        val file = File(tmp.root, name)
        file.writeText("<html><head><title>ignore me</title></head><body>$body</body></html>")
        return name
    }

    @Test
    fun findsMatchAcrossInlineTags() {
        // "word" split by an inline tag must still match, with no phantom separator.
        val paths = listOf(page("a.xhtml", "<p>a wo<i>rd</i> here</p>"))
        val results = EpubTextSearch.search(tmp.root, paths, "word")
        assertEquals(1, results.size)
        assertEquals(0, results[0].spineIndex)
        assertEquals(0, results[0].occurrence)
    }

    @Test
    fun countsOccurrencesPerPageInDocumentOrder() {
        val paths = listOf(
            page("a.xhtml", "<p>Fox one.</p><p>fox two.</p>"),
            page("b.xhtml", "<p>FOX three.</p>")
        )
        val results = EpubTextSearch.search(tmp.root, paths, "fox")
        assertEquals(3, results.size)
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0), results.map { it.spineIndex to it.occurrence })
    }

    @Test
    fun matchesFlexibleWhitespaceAndNbsp() {
        val paths = listOf(page("a.xhtml", "<p>hello\n   there</p><p>hello&nbsp;there</p>"))
        val results = EpubTextSearch.search(tmp.root, paths, "hello there")
        assertEquals(2, results.size)
    }

    @Test
    fun decodesEntitiesInMatchesAndSnippets() {
        val paths = listOf(page("a.xhtml", "<p>Tom &amp; Jerry &#65;gain</p>"))
        assertEquals(1, EpubTextSearch.search(tmp.root, paths, "Tom & Jerry").size)
        assertEquals(1, EpubTextSearch.search(tmp.root, paths, "Again").size)
    }

    @Test
    fun ignoresHeadScriptStyleAndComments() {
        val file = File(tmp.root, "a.xhtml")
        file.writeText(
            "<html><head><title>needle</title></head><body>" +
                "<script>var needle=1;</script><style>.needle{}</style>" +
                "<!-- needle --><p>hay</p></body></html>"
        )
        assertTrue(EpubTextSearch.search(tmp.root, listOf("a.xhtml"), "needle").isEmpty())
    }

    @Test
    fun snippetMarksTheMatchedRange() {
        val paths = listOf(page("a.xhtml", "<p>The quick brown fox jumps over the lazy dog.</p>"))
        val r = EpubTextSearch.search(tmp.root, paths, "brown FOX").single()
        val marked = r.snippet.substring(r.matchStart, r.matchStart + r.matchLength)
        assertEquals("brown fox", marked)
    }

    @Test
    fun jsPatternEscapesRegexSpecialsAndJoinsWordsFlexibly() {
        assertEquals("""a\.b\*c""", EpubTextSearch.jsPattern("a.b*c"))
        assertEquals("""one[\s\u00A0]+two""", EpubTextSearch.jsPattern("  one   two "))
        assertNull(EpubTextSearch.jsPattern("   "))
    }

    @Test
    fun capsResultCount() {
        val body = (1..EpubTextSearch.MAX_RESULTS + 50).joinToString(" ") { "again" }
        val paths = listOf(page("a.xhtml", "<p>$body</p>"))
        assertEquals(EpubTextSearch.MAX_RESULTS, EpubTextSearch.search(tmp.root, paths, "again").size)
    }
}
