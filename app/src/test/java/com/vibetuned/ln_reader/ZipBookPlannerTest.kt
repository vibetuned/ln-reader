package com.vibetuned.ln_reader

import com.vibetuned.ln_reader.data.repo.NaturalOrder
import com.vibetuned.ln_reader.data.repo.ZipBook
import com.vibetuned.ln_reader.data.repo.ZipBookPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipBookPlannerTest {

    @Test
    fun `google drive download of one book folder`() {
        // The exact layout of a Drive "Download" of an ln-vox book folder.
        val plan = ZipBookPlanner.plan(
            listOf(
                "sweet anticipation/Sweet Anticipation.m4b",
                "sweet anticipation/sync_manifest.json",
                "sweet anticipation/sweet-anticipation_volume-01.epub"
            )
        )
        assertEquals(
            listOf(
                ZipBook(
                    audio = "sweet anticipation/Sweet Anticipation.m4b",
                    epub = "sweet anticipation/sweet-anticipation_volume-01.epub",
                    sync = "sweet anticipation/sync_manifest.json"
                )
            ),
            plan.books
        )
        assertEquals("sweet anticipation", plan.commonFolder)
    }

    @Test
    fun `flat zip with one book has no common folder`() {
        val plan = ZipBookPlanner.plan(listOf("Book.m4b", "Book.epub", "sync_manifest.json"))
        assertEquals(listOf(ZipBook("Book.m4b", "Book.epub", "sync_manifest.json")), plan.books)
        assertNull(plan.commonFolder)
    }

    @Test
    fun `one folder per book makes one book each, in natural order`() {
        val plan = ZipBookPlanner.plan(
            listOf(
                "Series/Volume 10/v10.m4b", "Series/Volume 10/v10.epub",
                "Series/Volume 2/v2.m4b", "Series/Volume 2/sync_manifest.json",
                "Series/Volume 1/v1.m4b"
            )
        )
        assertEquals(
            listOf("Series/Volume 1/v1.m4b", "Series/Volume 2/v2.m4b", "Series/Volume 10/v10.m4b"),
            plan.books.map { it.audio }
        )
        assertEquals("Series/Volume 10/v10.epub", plan.books[2].epub)
        assertEquals("Series/Volume 2/sync_manifest.json", plan.books[1].sync)
        assertEquals("Series", plan.commonFolder)
    }

    @Test
    fun `several books in one folder pair by name`() {
        val plan = ZipBookPlanner.plan(
            listOf("A.m4b", "B.m4b", "B.epub", "A.epub", "A.json", "B.json")
        )
        assertEquals(
            listOf(ZipBook("A.m4b", "A.epub", "A.json"), ZipBook("B.m4b", "B.epub", "B.json")),
            plan.books
        )
    }

    @Test
    fun `ambiguous companions are not guessed`() {
        // Two audiobooks, two differently named EPUBs: no way to know which goes with which, so
        // neither audiobook takes one and both EPUBs stand as books of their own.
        val plan = ZipBookPlanner.plan(listOf("A.m4b", "B.m4b", "x.epub", "y.epub"))
        assertEquals(
            listOf(
                ZipBook("A.m4b", null, null),
                ZipBook("B.m4b", null, null),
                ZipBook(null, "x.epub", null),
                ZipBook(null, "y.epub", null)
            ),
            plan.books
        )
    }

    @Test
    fun `an epub without audio is a book of its own`() {
        val plan = ZipBookPlanner.plan(listOf("Novels/Frankenstein.epub", "Novels/Dracula.epub"))
        assertEquals(
            listOf(ZipBook(null, "Novels/Dracula.epub", null), ZipBook(null, "Novels/Frankenstein.epub", null)),
            plan.books
        )
        assertEquals("Novels", plan.commonFolder)
    }

    @Test
    fun `a sync manifest alone is not a book`() {
        assertEquals(emptyList<ZipBook>(), ZipBookPlanner.plan(listOf("sync_manifest.json")).books)
    }

    @Test
    fun `sync_manifest json wins over other manifests for a lone audiobook`() {
        val plan = ZipBookPlanner.plan(listOf("x.m4b", "alt.json", "sync_manifest.json"))
        assertEquals("sync_manifest.json", plan.books.single().sync)
    }

    @Test
    fun `mac resource forks and hidden files are skipped`() {
        assertFalse(ZipBookPlanner.isCandidate("__MACOSX/book/._Book.m4b"))
        assertFalse(ZipBookPlanner.isCandidate("book/._Book.m4b"))
        assertFalse(ZipBookPlanner.isCandidate("book/.DS_Store"))
        assertFalse(ZipBookPlanner.isCandidate("book/"))
        assertFalse(ZipBookPlanner.isCandidate("book/cover.jpg"))
        assertTrue(ZipBookPlanner.isCandidate("book/Book.M4B"))
        val plan = ZipBookPlanner.plan(listOf("__MACOSX/b/._b.m4b", "b/b.m4b", "b/._b.m4b"))
        assertEquals(listOf("b/b.m4b"), plan.books.map { it.audio })
        // The resource-fork tree doesn't count as a second top-level folder.
        assertEquals("b", plan.commonFolder)
    }

    @Test
    fun `windows separators are understood`() {
        val plan = ZipBookPlanner.plan(listOf("Series\\Vol 1\\a.m4b", "Series\\Vol 2\\b.m4b"))
        assertEquals(listOf("Series/Vol 1/a.m4b", "Series/Vol 2/b.m4b"), plan.books.map { it.audio })
        assertEquals("Series", plan.commonFolder)
    }

    @Test
    fun `collection is named after the zipped folder`() {
        val plan = ZipBookPlanner.plan(listOf("Series/1/a.m4b", "Series/2/b.m4b"))
        assertEquals("Series", ZipBookPlanner.collectionName(plan, "whatever.zip"))
    }

    @Test
    fun `without a folder the zip name is used, minus drive's export stamp`() {
        val plan = ZipBookPlanner.plan(listOf("1/a.m4b", "2/b.m4b"))
        assertEquals("Light novels", ZipBookPlanner.collectionName(plan, "Light novels-20261024T204511Z-1-001.zip"))
        assertEquals("Light novels", ZipBookPlanner.collectionName(plan, "Light novels-20261024T204511Z.ZIP"))
        assertEquals("My books", ZipBookPlanner.collectionName(plan, "My books.zip"))
        assertEquals("Imported books", ZipBookPlanner.collectionName(plan, null))
    }

    @Test
    fun `natural order compares numbers as numbers`() {
        val sorted = listOf("Vol 10", "vol 2", "Vol 1", "Vol 02b").sortedWith(NaturalOrder)
        assertEquals(listOf("Vol 1", "vol 2", "Vol 02b", "Vol 10"), sorted)
    }
}
