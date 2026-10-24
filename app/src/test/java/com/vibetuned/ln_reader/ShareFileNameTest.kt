package com.vibetuned.ln_reader

import com.vibetuned.ln_reader.data.repo.shareFileName
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareFileNameTest {
    @Test
    fun `plain titles are kept`() {
        assertEquals("Sweet Anticipation", shareFileName("Sweet Anticipation"))
    }

    @Test
    fun `characters file systems reject become spaces`() {
        assertEquals("Flatland A Romance of Many Dimensions", shareFileName("Flatland: A Romance of Many Dimensions"))
        assertEquals("AC DC Who What", shareFileName("AC/DC\\\\Who?\"What\""))
    }

    @Test
    fun `leading and trailing dots and spaces go`() {
        assertEquals("Hidden", shareFileName("  ..Hidden.. "))
    }

    @Test
    fun `very long titles are cut`() {
        assertEquals(120, shareFileName("x".repeat(400)).length)
    }

    @Test
    fun `nothing usable falls back to Book`() {
        assertEquals("Book", shareFileName("///"))
        assertEquals("Book", shareFileName(""))
    }
}
