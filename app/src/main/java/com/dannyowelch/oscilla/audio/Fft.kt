package com.dannyowelch.oscilla.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal fun hann(index: Int, length: Int): Float {
    if (length <= 1) return 1f
    return (0.5 * (1.0 - cos(2.0 * PI * index / (length - 1)))).toFloat()
}

internal fun fft(real: FloatArray, imaginary: FloatArray) {
    val n = real.size
    require(n == imaginary.size && n >= 2 && n and (n - 1) == 0) {
        "FFT size must be a power of two"
    }

    var j = 0
    for (i in 1 until n) {
        var bit = n ushr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit ushr 1
        }
        j = j xor bit
        if (i < j) {
            var swap = real[i]
            real[i] = real[j]
            real[j] = swap
            swap = imaginary[i]
            imaginary[i] = imaginary[j]
            imaginary[j] = swap
        }
    }

    var length = 2
    while (length <= n) {
        val angle = -2.0 * PI / length
        val wlenReal = cos(angle)
        val wlenImaginary = sin(angle)
        val half = length / 2
        var start = 0
        while (start < n) {
            var wReal = 1.0
            var wImaginary = 0.0
            for (k in 0 until half) {
                val even = start + k
                val odd = even + half
                val oddReal = real[odd] * wReal - imaginary[odd] * wImaginary
                val oddImaginary = real[odd] * wImaginary + imaginary[odd] * wReal
                val evenReal = real[even].toDouble()
                val evenImaginary = imaginary[even].toDouble()
                real[even] = (evenReal + oddReal).toFloat()
                imaginary[even] = (evenImaginary + oddImaginary).toFloat()
                real[odd] = (evenReal - oddReal).toFloat()
                imaginary[odd] = (evenImaginary - oddImaginary).toFloat()
                val nextReal = wReal * wlenReal - wImaginary * wlenImaginary
                wImaginary = wReal * wlenImaginary + wImaginary * wlenReal
                wReal = nextReal
            }
            start += length
        }
        length = length shl 1
    }
}

fun magnitudeSpectrum(samples: FloatArray): FloatArray {
    val n = nextPowerOfTwo(samples.size.coerceAtLeast(2))
    val real = FloatArray(n)
    val imaginary = FloatArray(n)
    val windowLength = samples.size.coerceAtLeast(1)
    for (index in samples.indices) {
        real[index] = samples[index] * hann(index, windowLength)
    }
    fft(real, imaginary)
    val bins = n / 2 + 1
    return FloatArray(bins) { bin ->
        (hypot(real[bin].toDouble(), imaginary[bin].toDouble()) / n).toFloat()
    }
}

private fun nextPowerOfTwo(value: Int): Int {
    var n = 1
    while (n < value) {
        val shifted = n shl 1
        require(shifted > 0) { "FFT size overflow" }
        n = shifted
    }
    return n
}
