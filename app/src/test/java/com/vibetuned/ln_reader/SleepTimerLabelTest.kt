package com.vibetuned.ln_reader

import com.vibetuned.ln_reader.ui.player.sleepTimerLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerLabelTest {

    @Test
    fun `time mode shows the countdown`() {
        assertEquals("29:41 left", sleepTimerLabel(msUntilStop = 1_781_000, chaptersLeft = null))
    }

    @Test
    fun `hours appear only once there is one`() {
        assertEquals("59:59 left", sleepTimerLabel(3_599_000, null))
        assertEquals("1:30:00 left", sleepTimerLabel(5_400_000, null))
    }

    @Test
    fun `seconds round up so the last moment never reads zero`() {
        assertEquals("0:01 left", sleepTimerLabel(400, null))
        assertEquals("0:00 left", sleepTimerLabel(0, null))
        assertEquals("0:00 left", sleepTimerLabel(-50, null))
    }

    @Test
    fun `last chapter says end of chapter`() {
        assertEquals("3:12 left · end of chapter", sleepTimerLabel(192_000, chaptersLeft = 1))
    }

    @Test
    fun `several chapters say how many`() {
        assertEquals("12:30 left · 2 chapters", sleepTimerLabel(750_000, chaptersLeft = 2))
    }
}
