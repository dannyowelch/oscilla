package com.dannyowelch.oscilla.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class VisualizerFramesTest {
    @Test
    fun waveformCenterIsSilenceAndTheEndsAreFullScale() {
        val pcm = visualizerWaveformToPcm(byteArrayOf(128.toByte(), 0, 255.toByte()))
        assertEquals(0f, pcm[0], 1e-6f)
        assertEquals(-1f, pcm[1], 1e-6f)
        assertEquals(127f / 128f, pcm[2], 1e-6f)
    }

    @Test
    fun fftBytesUnpackDcNyquistAndOneComplexBin() {
        val fft = byteArrayOf(10, -20, 3, 4)
        val magnitudes = visualizerFftToMagnitudes(fft)
        assertEquals(3, magnitudes.size)
        assertEquals(10f / 128f, magnitudes[0], 1e-6f)
        assertEquals(hypot(3f, 4f) / 128f, magnitudes[1], 1e-6f)
        assertEquals(20f / 128f, magnitudes[2], 1e-6f)
    }

    @Test
    fun oddOrEmptyFftCapturesAreIgnored() {
        assertEquals(0, visualizerFftToMagnitudes(byteArrayOf(1, 2, 3)).size)
        assertEquals(0, visualizerFftToMagnitudes(byteArrayOf()).size)
    }

    @Test
    fun aFlatVisualizerWaveformIsAZeroFrame() {
        val pcm = visualizerWaveformToPcm(ByteArray(8) { 128.toByte() })
        assertTrue(isZeroFrame(pcm, floatArrayOf()))
    }
}
