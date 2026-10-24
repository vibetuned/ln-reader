package com.vibetuned.ln_reader

import com.vibetuned.ln_reader.ui.player.sleepTimerRibbonLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerRibbonLabelTest {

    @Test
    fun `time mode shows the countdown`() {
        assertEquals("29:41 left", sleepTimerRibbonLabel(msUntilStop = 1_781_000, chaptersLeft = null))
    }

    @Test
    fun `hours appear only once there is one`() {
        assertEquals("59:59 left", sleepTimerRibbonLabel(3_599_000, null))
        assertEquals("1:30:00 left", sleepTimerRibbonLabel(5_400_000, null))
    }

    @Test
    fun `seconds round up so the last moment never reads zero`() {
        assertEquals("0:01 left", sleepTimerRibbonLabel(400, null))
        assertEquals("0:00 left", sleepTimerRibbonLabel(0, null))
        assertEquals("0:00 left", sleepTimerRibbonLabel(-50, null))
    }

    @Test
    fun `last chapter says end of chapter`() {
        assertEquals("3:12 left · end of chapter", sleepTimerRibbonLabel(192_000, chaptersLeft = 1))
    }

    @Test
    fun `several chapters say how many`() {
        assertEquals("12:30 left · 2 chapters", sleepTimerRibbonLabel(750_000, chaptersLeft = 2))
    }
}
