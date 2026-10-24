package com.dannyowelch.oscilla.audio

import kotlin.math.sqrt

/** One 16-bit PCM step. Samples at or below this are treated as digital zero. */
const val PCM_ZERO_EPSILON: Float = 1f / 32768f

const val WAVEFORM_POINTS: Int = 128
const val FFT_BARS: Int = 32

fun pcm16ToFloat(samples: ShortArray, length: Int = samples.size): FloatArray {
    val count = length.coerceIn(0, samples.size)
    return FloatArray(count) { index -> samples[index] / 32768f }
}

fun rms(samples: FloatArray): Float {
    if (samples.isEmpty()) return 0f
    var sum = 0.0
    var count = 0
    for (sample in samples) {
        if (!sample.isFinite()) continue
        sum += sample.toDouble() * sample.toDouble()
        count += 1
    }
    if (count == 0) return 0f
    return sqrt(sum / count).toFloat()
}

fun peakAbs(samples: FloatArray): Float {
    var peak = 0f
    for (sample in samples) {
        if (!sample.isFinite()) continue
        val magnitude = kotlin.math.abs(sample)
        if (magnitude > peak) peak = magnitude
    }
    return peak
}

fun isZeroFrame(
    pcm: FloatArray,
    magnitudes: FloatArray,
    epsilon: Float = PCM_ZERO_EPSILON,
): Boolean {
    val pcmZero = pcm.all { sample -> sample.isFinite() && kotlin.math.abs(sample) <= epsilon }
    val magnitudeZero = magnitudes.all { bin -> bin.isFinite() && bin <= epsilon }
    return when {
        pcm.isEmpty() && magnitudes.isEmpty() -> true
        pcm.isEmpty() -> magnitudeZero
        magnitudes.isEmpty() -> pcmZero
        else -> pcmZero && magnitudeZero
    }
}

fun downsamplePeaks(samples: FloatArray, buckets: Int): FloatArray {
    require(buckets > 0)
    if (samples.isEmpty()) return FloatArray(0)
    if (samples.size <= buckets) return samples.copyOf()
    val out = FloatArray(buckets)
    for (bucket in 0 until buckets) {
        val start = bucket * samples.size / buckets
        val end = (bucket + 1) * samples.size / buckets
        var peak = 0f
        for (index in start until end) {
            val sample = samples[index]
            if (kotlin.math.abs(sample) >= kotlin.math.abs(peak)) peak = sample
        }
        out[bucket] = peak
    }
    return out
}

fun averageBins(magnitudes: FloatArray, bars: Int): FloatArray {
    require(bars > 0)
    if (magnitudes.isEmpty()) return FloatArray(0)
    if (magnitudes.size <= bars) return magnitudes.copyOf()
    val out = FloatArray(bars)
    for (bar in 0 until bars) {
        val start = bar * magnitudes.size / bars
        val end = ((bar + 1) * magnitudes.size / bars).coerceAtLeast(start + 1)
        var sum = 0.0
        var count = 0
        for (index in start until end) {
            val value = magnitudes[index]
            if (!value.isFinite()) continue
            sum += value.toDouble()
            count += 1
        }
        out[bar] = if (count == 0) 0f else (sum / count).toFloat()
    }
    return out
}
