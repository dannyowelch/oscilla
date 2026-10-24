package com.dannyowelch.oscilla.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class AudioLevelsTest {
    @Test
    fun rmsAndPeakOfKnownSamples() {
        assertEquals(0f, rms(floatArrayOf()), 0f)
        assertEquals(0f, rms(floatArrayOf(0f, 0f, 0f)), 0f)
        assertEquals(0.5f, rms(floatArrayOf(0.5f, -0.5f)), 1e-6f)
        assertEquals(sqrt(12.5).toFloat(), rms(floatArrayOf(3f, -4f)), 1e-5f)
        assertEquals(0.5f, peakAbs(floatArrayOf(-0.2f, 0.5f, 0.1f)), 1e-6f)
    }

    @Test
    fun pcm16Extremes() {
        assertEquals(0f, pcm16ToFloat(shortArrayOf(0))[0], 0f)
        assertEquals(32767f / 32768f, pcm16ToFloat(shortArrayOf(32767))[0], 1e-6f)
        assertEquals(-1f, pcm16ToFloat(shortArrayOf(Short.MIN_VALUE))[0], 0f)
        assertEquals(1, pcm16ToFloat(shortArrayOf(10, 20, 30), length = 1).size)
    }

    @Test
    fun downsampleKeepsTheStrongestSampleInEachBucket() {
        val out = downsamplePeaks(floatArrayOf(0.1f, -0.8f, 0.2f, 0.7f), buckets = 2)
        assertEquals(-0.8f, out[0], 0f)
        assertEquals(0.7f, out[1], 0f)
        assertEquals(2, downsamplePeaks(floatArrayOf(0.2f, -0.3f), buckets = 4).size)
    }

    @Test
    fun averageBinsGroupsEqualWidths() {
        val out = averageBins(floatArrayOf(1f, 3f, 2f, 4f), bars = 2)
        assertEquals(2f, out[0], 1e-6f)
        assertEquals(3f, out[1], 1e-6f)
        assertEquals(2, averageBins(floatArrayOf(0.5f, 0.25f), bars = 8).size)
    }
}
