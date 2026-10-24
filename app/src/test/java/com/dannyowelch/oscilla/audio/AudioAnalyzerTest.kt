package com.dannyowelch.oscilla.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAnalyzerTest {
    @Test
    fun silenceStaysQuiet() {
        val analyzer = AudioAnalyzer(bandCount = 8)
        val features = analyzer.process(FloatArray(32), FloatArray(128), nowMs = 1_000)
        assertEquals(0f, features.level, 1e-6f)
        assertFalse(features.onset)
        assertFalse(features.beat)
        assertTrue(features.bands.all { it < 1e-4f })
        assertEquals(0f, features.bass, 1e-5f)
    }

    @Test
    fun smoothingRisesThenDecays() {
        val analyzer = AudioAnalyzer(bandCount = 4, attack = 0.5f, release = 0.5f)
        analyzer.process(FloatArray(16) { 1f }, floatArrayOf(0.2f), nowMs = 0)
        val risen = analyzer.process(FloatArray(16) { 1f }, floatArrayOf(0.2f), nowMs = 40)
        assertTrue(risen.bands.all { it > 0.6f })
        val decayed = analyzer.process(FloatArray(16), floatArrayOf(), nowMs = 80)
        assertTrue(decayed.bands[0] < risen.bands[0])
        assertTrue(decayed.bands[0] > 0.2f)
    }

    @Test
    fun bassSpikeIsABeatAndDoesNotRetriggerImmediately() {
        val analyzer = AudioAnalyzer(bandCount = 8)
        repeat(12) { frame ->
            analyzer.process(flat(0.01f), FloatArray(32), nowMs = frame * 40L)
        }
        val hit = analyzer.process(lowEnergy(1f), FloatArray(32), nowMs = 600)
        assertTrue(hit.beat)
        assertTrue(hit.bass > hit.treble)
        val held = analyzer.process(lowEnergy(1f), FloatArray(32), nowMs = 640)
        assertFalse(held.beat)
    }

    @Test
    fun trebleSpikeIsAnOnsetWithoutABassBeat() {
        val analyzer = AudioAnalyzer(bandCount = 8)
        repeat(12) { frame ->
            analyzer.process(flat(0.01f), FloatArray(32), nowMs = frame * 40L)
        }
        val hit = analyzer.process(highEnergy(1f), FloatArray(32), nowMs = 600)
        assertTrue(hit.onset)
        assertFalse(hit.beat)
        assertTrue(hit.treble > hit.bass)
    }

    @Test
    fun logBandsKeepTheLowestAndHighestBinsApart() {
        val low = FloatArray(64)
        low[0] = 1f
        val lowBands = groupLogBands(low, 8)
        assertTrue(lowBands[0] > 0f)
        assertEquals(0f, lowBands.last(), 0f)

        val high = FloatArray(64)
        high[63] = 1f
        val highBands = groupLogBands(high, 8)
        assertEquals(0f, highBands[0], 0f)
        assertTrue(highBands.last() > 0f)
    }

    private fun flat(value: Float): FloatArray = FloatArray(64) { value }

    private fun lowEnergy(value: Float): FloatArray = FloatArray(64) { index -> if (index < 3) value else 0.01f }

    private fun highEnergy(value: Float): FloatArray = FloatArray(64) { index -> if (index > 58) value else 0.01f }
}
