package com.vibetuned.ln_reader

import com.vibetuned.ln_reader.ui.player.groupMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerGroupsTest {

    private fun group(positions: List<Float>, minGap: Float = 28f) =
        groupMarkers(positions, { it }, minGap)

    @Test
    fun `nothing to group`() {
        assertEquals(emptyList<Any>(), group(emptyList()))
    }

    @Test
    fun `markers far apart stay single`() {
        val groups = group(listOf(10f, 100f, 200f))
        assertEquals(listOf(listOf(10f), listOf(100f), listOf(200f)), groups.map { it.items })
        assertEquals(listOf(10f, 100f, 200f), groups.map { it.center })
    }

    @Test
    fun `markers too close share a badge in the middle`() {
        // The Sweet Anticipation chapter 6 case: two pairs, each nearly on top of each other.
        val groups = group(listOf(300f, 460f, 485f, 650f, 662f))
        assertEquals(listOf(listOf(300f), listOf(460f, 485f), listOf(650f, 662f)), groups.map { it.items })
        assertEquals(listOf(300f, 472.5f, 656f), groups.map { it.center })
    }

    @Test
    fun `order of the input doesn't matter`() {
        assertEquals(group(listOf(10f, 20f, 100f)), group(listOf(100f, 20f, 10f)))
    }

    @Test
    fun `a chain of near neighbours becomes one group`() {
        val groups = group(listOf(0f, 20f, 40f, 60f))
        assertEquals(1, groups.size)
        assertEquals(30f, groups.single().center)
    }

    @Test
    fun `exactly the minimum gap stays apart`() {
        assertEquals(2, group(listOf(0f, 28f)).size)
    }

    @Test
    fun `badges never end up closer than the minimum gap`() {
        // Every spread of markers, at every spacing: the guarantee the grouping exists for.
        val random = java.util.Random(7)
        repeat(500) {
            val positions = List(random.nextInt(12) + 1) { random.nextFloat() * 400f }
            val centers = group(positions).map { it.center }
            centers.zipWithNext().forEach { (a, b) ->
                assertTrue("badges at $a and $b from $positions", b - a >= 28f - 1e-3f)
            }
        }
    }
}
