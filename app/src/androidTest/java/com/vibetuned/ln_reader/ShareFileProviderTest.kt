package com.vibetuned.ln_reader

import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibetuned.ln_reader.data.repo.ShareFileProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** What a receiving app sees when it asks the share provider about a book shared in place. */
@RunWith(AndroidJUnit4::class)
class ShareFileProviderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bytes = ByteArray(4096) { (it % 251).toByte() }
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File(context.filesDir, "downloads/share-test/book.m4b").apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
    }

    @After
    fun tearDown() {
        file.parentFile?.deleteRecursively()
    }

    private fun queryName(uri: android.net.Uri): Pair<String, Long> =
        context.contentResolver.query(uri, null, null, null, null)!!.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) to
                cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
        }

    @Test
    fun presentsTheBookTitleInsteadOfTheNameOnDisk() {
        val uri = ShareFileProvider.uriFor(context, file, "Flatland A Romance of Many Dimensions.m4b")
        assertEquals("Flatland A Romance of Many Dimensions.m4b" to bytes.size.toLong(), queryName(uri))
    }

    @Test
    fun withoutANameTheFileKeepsItsOwn() {
        assertEquals("book.m4b" to bytes.size.toLong(), queryName(ShareFileProvider.uriFor(context, file)))
    }

    @Test
    fun theRenamedUriStillReadsTheFile() {
        val uri = ShareFileProvider.uriFor(context, file, "Any Name.m4b")
        val read = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertArrayEquals(bytes, read)
    }
}
