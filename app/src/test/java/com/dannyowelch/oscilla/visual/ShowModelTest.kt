package com.dannyowelch.oscilla.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShowModelTest {
    @Test
    fun randomParamsStayInsideTheRanges() {
        val random = Random(42)
        repeat(40) {
            val params = randomParams(random)
            assertTrue(params.paletteIndex in 0 until Palettes.COUNT)
            assertTrue(params.sensitivity in VisualParams.MIN_SENSITIVITY..VisualParams.MAX_SENSITIVITY)
            assertTrue(params.symmetry in VisualParams.MIN_SYMMETRY..VisualParams.MAX_SYMMETRY)
            assertTrue(params.speed in VisualParams.MIN_SPEED..VisualParams.MAX_SPEED)
            assertEquals(4, Palettes.argb(params.paletteIndex).size)
        }
    }

    @Test
    fun nextVisualIsADifferentIndex() {
        val random = Random(7)
        repeat(20) { current ->
            val next = nextVisualIndex(current % 5, 5, random)
            assertNotEquals(current % 5, next)
            assertTrue(next in 0..4)
        }
        assertEquals(0, nextVisualIndex(3, 1, random))
    }

    @Test
    fun crossfadeReachesFullAfterTheDuration() {
        assertEquals(0f, crossfadeAlpha(0, durationMs = 640), 0f)
        assertEquals(0.5f, crossfadeAlpha(320, durationMs = 640), 1e-5f)
        assertEquals(1f, crossfadeAlpha(900, durationMs = 640), 0f)
    }

    @Test
    fun autoModeWaitsForSecondsOrBeats() {
        val seconds = AutoScheduler()
        val byTime = AutoConfig(enabled = true, trigger = AutoTrigger.SECONDS, interval = 10, switchVisual = true)
        assertFalse(seconds.due(1_000, byTime))
        assertFalse(seconds.due(10_999, byTime))
        assertTrue(seconds.due(11_000, byTime))
        seconds.mark(11_000)
        assertFalse(seconds.due(15_000, byTime))

        val beats = AutoScheduler()
        val byBeat = AutoConfig(enabled = true, trigger = AutoTrigger.BEATS, interval = 4, switchVisual = false)
        assertFalse(beats.due(0, byBeat))
        repeat(3) {
            beats.observe(true)
            beats.observe(false)
        }
        assertFalse(beats.due(100, byBeat))
        beats.observe(true)
        assertTrue(beats.due(200, byBeat))
        assertFalse(AutoScheduler().due(5_000, byTime.copy(enabled = false)))
    }
}
