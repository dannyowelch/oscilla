package com.dannyowelch.oscilla.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class FftTest {
    @Test
    fun dcIsASingleBin() {
        val real = floatArrayOf(1f, 1f, 1f, 1f)
        val imaginary = FloatArray(4)
        fft(real, imaginary)
        assertEquals(4f, real[0], 1e-3f)
        assertEquals(0f, imaginary[0], 1e-3f)
        for (index in 1 until 4) {
            assertEquals(0f, real[index], 1e-3f)
            assertEquals(0f, imaginary[index], 1e-3f)
        }
    }

    @Test
    fun impulseSpreadsAcrossEveryBin() {
        val real = floatArrayOf(1f, 0f, 0f, 0f)
        val imaginary = FloatArray(4)
        fft(real, imaginary)
        for (index in 0 until 4) {
            assertEquals(1f, real[index], 1e-3f)
            assertEquals(0f, imaginary[index], 1e-3f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPowerOfTwoIsRejected() {
        fft(floatArrayOf(1f, 0f, 0f), FloatArray(3))
    }

    @Test
    fun hannWindowIsZeroAtTheEndsAndOneInTheMiddle() {
        assertEquals(0f, hann(0, 5), 1e-6f)
        assertEquals(0f, hann(4, 5), 1e-6f)
        assertEquals(1f, hann(2, 5), 1e-5f)
    }

    @Test
    fun sinePeaksAtItsBin() {
        val n = 64
        val bin = 5
        val samples = FloatArray(n) { index ->
            cos(2.0 * PI * bin * index / n).toFloat()
        }
        val magnitudes = magnitudeSpectrum(samples)
        val peak = magnitudes.indices.maxBy { magnitudes[it] }
        assertEquals(bin, peak)
        assertTrue(magnitudes[bin] > magnitudes[0] * 4f)
    }

    @Test
    fun silenceHasNoSpectrumEnergy() {
        val magnitudes = magnitudeSpectrum(FloatArray(16))
        assertEquals(9, magnitudes.size)
        assertTrue(magnitudes.all { it < 1e-6f })
    }

    @Test
    fun shortInputIsZeroPaddedToAPowerOfTwo() {
        assertEquals(3, magnitudeSpectrum(floatArrayOf(0f, 0f, 0f)).size)
    }
}
