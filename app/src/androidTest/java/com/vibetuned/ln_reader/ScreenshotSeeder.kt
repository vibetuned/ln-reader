package com.vibetuned.ln_reader

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.prefs.SortDirection
import com.vibetuned.ln_reader.data.prefs.SortField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Fills the library with the ln-vox demo books so the store screenshots have real content.
 *
 * Not a test of anything — it's a fixture loader that lives in androidTest so it never ships in
 * the app. Assets are pushed to the app's external files dir first:
 *
 *   adb push <book>.m4b /sdcard/Android/data/com.vibetuned.ln_reader/files/demo/<slug>/book.m4b
 *
 * The repository takes a `file://` Uri here rather than the SAF `content://` a real import would
 * hand it. That path holds up: `takePersistableUriPermission` is already wrapped in `runCatching`,
 * a file Uri has no authority so `isRemoteUri` reports false and the book is referenced in place,
 * and titles come off the m4b metadata instead of the display name.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotSeeder {

    private data class Demo(val slug: String, val progress: Double)

    /**
     * Import order matters: the library defaults to DateAdded/Desc, so the last one imported
     * lands top-left in the grid. Positions are saved in this same order afterwards, which makes
     * the final entry the book the app auto-restores on launch.
     */
    private val demos = listOf(
        Demo("fairy-dreams", 0.04),
        Demo("flatland", 0.12),
        Demo("oz", 0.61),
        Demo("alice", 0.34)
    )

    @Test
    fun seedLibrary() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as LnReaderApplication
        val container = app.container
        val books = container.bookRepository
        val positions = container.positionRepository

        val root = File(instrumentation.targetContext.getExternalFilesDir(null), "demo")
        assertTrue("demo assets missing at $root", root.isDirectory)

        // Start from an empty library so re-runs don't stack duplicates.
        books.books().first().forEach { books.delete(it.id) }
        container.libraryPreferences.setSort(SortField.DateAdded, SortDirection.Desc)

        val imported = mutableListOf<Pair<Demo, Book>>()
        for (demo in demos) {
            val dir = File(root, demo.slug)
            val m4b = File(dir, "book.m4b")
            assertTrue("missing ${m4b.absolutePath}", m4b.isFile)

            val book = books.import(m4b.toUri()).getOrThrow()
            File(dir, "book.epub").takeIf { it.isFile }?.let {
                books.attachEpub(book.id, it.toUri()).getOrThrow()
            }
            File(dir, "sync.json").takeIf { it.isFile }?.let {
                books.attachSync(book.id, it.toUri()).getOrThrow()
            }
            imported += demo to book
            println("SEEDED ${book.title} | ${book.durationMs}ms | epub+sync attached")
        }

        for ((demo, book) in imported) {
            positions.save(book.id, (book.durationMs * demo.progress).toLong())
        }

        val finalLibrary = books.books().first()
        assertTrue("expected ${demos.size} books, got ${finalLibrary.size}", finalLibrary.size == demos.size)
        finalLibrary.forEach { println("LIBRARY ${it.title} epub=${it.hasEpub} sync=${it.hasSync}") }
    }
}
