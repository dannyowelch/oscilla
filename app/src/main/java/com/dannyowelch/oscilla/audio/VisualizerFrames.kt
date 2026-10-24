package com.dannyowelch.oscilla.audio

import kotlin.math.abs
import kotlin.math.hypot

/** Unsigned 8-bit Visualizer waveform. 128 is silence. */
fun visualizerWaveformToPcm(waveform: ByteArray): FloatArray {
    return FloatArray(waveform.size) { index ->
        ((waveform[index].toInt() and 0xFF) - 128) / 128f
    }
}

/**
 * Visualizer FFT layout: byte 0 is the real DC bin, byte 1 is the real Nyquist bin,
 * then real/imag pairs for bins 1..n/2-1. Values are signed 8-bit.
 */
fun visualizerFftToMagnitudes(fft: ByteArray): FloatArray {
    val n = fft.size
    if (n < 2 || n and (n - 1) != 0) return FloatArray(0)
    val bins = n / 2 + 1
    val out = FloatArray(bins)
    out[0] = abs(fft[0].toFloat()) / 128f
    out[bins - 1] = abs(fft[1].toFloat()) / 128f
    for (bin in 1 until bins - 1) {
        val real = fft[2 * bin].toFloat()
        val imaginary = fft[2 * bin + 1].toFloat()
        out[bin] = hypot(real, imaginary) / 128f
    }
    return out
}
