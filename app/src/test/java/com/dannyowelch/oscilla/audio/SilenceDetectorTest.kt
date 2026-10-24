package com.dannyowelch.oscilla.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {
    @Test
    fun zerosDoNotCountAsSilentUntilTheWindowElapses() {
        val detector = SilenceDetector()
        assertEquals(0L, detector.onFrame(floatArrayOf(0f, 0f), floatArrayOf(0f), nowMs = 1_000))
        assertEquals(2_999L, detector.onFrame(floatArrayOf(0f), floatArrayOf(), nowMs = 3_999))
        assertTrue(signalHealth(elapsedMs = 2_999, framesReceived = 2, zeroStreakMs = 2_999) == SignalHealth.Live)
        assertEquals(SignalHealth.Silent, signalHealth(elapsedMs = 3_000, framesReceived = 2, zeroStreakMs = 3_000))
    }

    @Test
    fun energyResetsTheZeroStreak() {
        val detector = SilenceDetector()
        detector.onFrame(floatArrayOf(0f), floatArrayOf(0f), nowMs = 0)
        assertNull(detector.onFrame(floatArrayOf(0.01f), floatArrayOf(0f), nowMs = 2_000))
        assertEquals(0L, detector.onFrame(floatArrayOf(0f), floatArrayOf(0f), nowMs = 5_000))
    }

    @Test
    fun onePcmStepIsZeroAndTheNextStepIsNot() {
        val step = 1f / 32768f
        assertTrue(isZeroFrame(floatArrayOf(step, -step), floatArrayOf()))
        assertFalse(isZeroFrame(floatArrayOf(step * 2f), floatArrayOf()))
    }

    @Test
    fun magnitudeKeepsAZeroWaveformFromCountingAsSilent() {
        assertFalse(isZeroFrame(floatArrayOf(0f), floatArrayOf(0.2f)))
        assertTrue(isZeroFrame(floatArrayOf(), floatArrayOf()))
        assertTrue(isZeroFrame(floatArrayOf(), floatArrayOf(0f)))
    }

    @Test
    fun resetStartsTheStreakOver() {
        val detector = SilenceDetector()
        detector.onFrame(floatArrayOf(0f), floatArrayOf(), nowMs = 0)
        detector.reset()
        assertEquals(0L, detector.onFrame(floatArrayOf(0f), floatArrayOf(), nowMs = 5_000))
    }
}
