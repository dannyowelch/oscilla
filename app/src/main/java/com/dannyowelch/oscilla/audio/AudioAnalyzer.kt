package com.dannyowelch.oscilla.audio

import kotlin.math.pow
import kotlin.math.roundToInt

data class AudioFeatures(
    val level: Float,
    val peak: Float,
    val bands: FloatArray,
    val waveform: FloatArray,
    val bass: Float,
    val mid: Float,
    val treble: Float,
    val flux: Float,
    val onset: Boolean,
    val beat: Boolean,
)

/**
 * Turns raw PCM and an FFT magnitude spectrum into levels, smoothed log-spaced
 * bands, bass/mid/treble energy, and a simple onset/beat flag.
 *
 * Onset is a full-spectrum flux spike. Beat is the same idea on the bass bands,
 * which is what a kick tends to do. Both stay quiet until a short history exists,
 * and both honor a refractory gap so one hit does not retrigger every frame.
 */
class AudioAnalyzer(
    val bandCount: Int = 24,
    private val attack: Float = 0.62f,
    private val release: Float = 0.16f,
    private val onsetRatio: Float = 1.7f,
    private val beatRatio: Float = 1.55f,
    private val refractoryMs: Long = 180L,
    private val historySize: Int = 24,
) {
    private val smoothed = FloatArray(bandCount)
    private val previous = FloatArray(bandCount)
    private val fluxHistory = FloatArray(historySize)
    private val bassFluxHistory = FloatArray(historySize)
    private var historyCount = 0
    private var historyIndex = 0
    private var lastOnsetMs = Long.MIN_VALUE / 4
    private var lastBeatMs = Long.MIN_VALUE / 4

    fun reset() {
        smoothed.fill(0f)
        previous.fill(0f)
        fluxHistory.fill(0f)
        bassFluxHistory.fill(0f)
        historyCount = 0
        historyIndex = 0
        lastOnsetMs = Long.MIN_VALUE / 4
        lastBeatMs = Long.MIN_VALUE / 4
    }

    fun process(magnitudes: FloatArray, pcm: FloatArray, nowMs: Long): AudioFeatures {
        val target = groupLogBands(magnitudes, bandCount)
        val bassEnd = (bandCount * BASS_FRACTION).toInt().coerceIn(1, bandCount - 2)
        val midEnd = (bandCount * MID_FRACTION).toInt().coerceIn(bassEnd + 1, bandCount - 1)
        var flux = 0f
        var bassFlux = 0f
        for (index in target.indices) {
            val delta = target[index] - previous[index]
            if (delta > 0f) {
                flux += delta
                if (index < bassEnd) bassFlux += delta
            }
            val coeff = if (target[index] > smoothed[index]) attack else release
            smoothed[index] += (target[index] - smoothed[index]) * coeff
            previous[index] = target[index]
        }
        val onset = isSpike(flux, fluxHistory, nowMs, lastOnsetMs, onsetRatio)
        val beat = isSpike(bassFlux, bassFluxHistory, nowMs, lastBeatMs, beatRatio)
        if (onset) lastOnsetMs = nowMs
        if (beat) lastBeatMs = nowMs
        push(fluxHistory, flux)
        push(bassFluxHistory, bassFlux)
        val level = rms(pcm).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        return AudioFeatures(
            level = level,
            peak = peakAbs(pcm),
            bands = smoothed.copyOf(),
            waveform = downsamplePeaks(pcm, WAVEFORM_POINTS.coerceAtMost(96)),
            bass = averageRange(smoothed, 0, bassEnd),
            mid = averageRange(smoothed, bassEnd, midEnd),
            treble = averageRange(smoothed, midEnd, bandCount),
            flux = flux,
            onset = onset,
            beat = beat,
        )
    }

    private fun isSpike(
        value: Float,
        history: FloatArray,
        nowMs: Long,
        lastMs: Long,
        ratio: Float,
    ): Boolean {
        if (historyCount < MIN_HISTORY) return false
        if (nowMs - lastMs < refractoryMs) return false
        var sum = 0.0
        for (sample in history) sum += sample
        val mean = (sum / history.size).toFloat()
        if (value < MIN_SPIKE) return false
        return value > mean * ratio
    }

    private fun push(history: FloatArray, value: Float) {
        history[historyIndex] = value
        historyIndex = (historyIndex + 1) % history.size
        if (historyCount < history.size) historyCount += 1
    }

    companion object {
        const val BASS_FRACTION = 0.2f
        const val MID_FRACTION = 0.55f
        private const val MIN_HISTORY = 8
        private const val MIN_SPIKE = 0.02f
    }
}

fun logBandEdges(binCount: Int, bandCount: Int): IntArray {
    require(bandCount > 0)
    val edges = IntArray(bandCount + 1)
    if (binCount <= 0) return edges
    edges[0] = 0
    for (band in 1 until bandCount) {
        val mapped = binCount.toDouble().pow(band.toDouble() / bandCount).roundToInt() - 1
        edges[band] = mapped.coerceIn(0, binCount)
    }
    edges[bandCount] = binCount
    for (band in 1 until bandCount) {
        if (edges[band] <= edges[band - 1] && edges[band - 1] < binCount) {
            edges[band] = edges[band - 1] + 1
        }
    }
    edges[bandCount] = binCount
    return edges
}

fun groupLogBands(magnitudes: FloatArray, bandCount: Int): FloatArray {
    val out = FloatArray(bandCount)
    if (magnitudes.isEmpty() || bandCount <= 0) return out
    val edges = logBandEdges(magnitudes.size, bandCount)
    for (band in 0 until bandCount) {
        val start = edges[band].coerceIn(0, magnitudes.size)
        val end = edges[band + 1].coerceIn(start, magnitudes.size)
        if (end <= start) continue
        var sum = 0.0
        var count = 0
        for (index in start until end) {
            val value = magnitudes[index]
            if (!value.isFinite() || value < 0f) continue
            sum += value
            count += 1
        }
        out[band] = if (count == 0) 0f else (sum / count).toFloat()
    }
    return out
}

private fun averageRange(values: FloatArray, start: Int, end: Int): Float {
    if (end <= start) return 0f
    var sum = 0.0
    var count = 0
    for (index in start until end) {
        val value = values[index]
        if (!value.isFinite()) continue
        sum += value
        count += 1
    }
    return if (count == 0) 0f else (sum / count).toFloat()
}
