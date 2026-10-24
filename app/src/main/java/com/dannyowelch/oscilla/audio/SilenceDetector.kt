package com.dannyowelch.oscilla.audio

/**
 * Tracks how long a source has delivered only zeros.
 * [onFrame] returns the streak length in milliseconds, or null when the frame has energy.
 */
class SilenceDetector(
    private val epsilon: Float = PCM_ZERO_EPSILON,
) {
    private var zeroSinceMs: Long? = null

    fun onFrame(pcm: FloatArray, magnitudes: FloatArray, nowMs: Long): Long? {
        if (!isZeroFrame(pcm, magnitudes, epsilon)) {
            zeroSinceMs = null
            return null
        }
        val start = zeroSinceMs ?: nowMs.also { zeroSinceMs = it }
        return nowMs - start
    }

    fun reset() {
        zeroSinceMs = null
    }
}

object ProbeTiming {
    const val SILENCE_WINDOW_MS: Long = 3_000L
}

enum class SignalHealth {
    Waiting,
    Live,
    Silent,
    NoFrames,
}

fun signalHealth(
    elapsedMs: Long,
    framesReceived: Long,
    zeroStreakMs: Long?,
    windowMs: Long = ProbeTiming.SILENCE_WINDOW_MS,
): SignalHealth {
    if (framesReceived <= 0L) {
        return if (elapsedMs >= windowMs) SignalHealth.NoFrames else SignalHealth.Waiting
    }
    if (zeroStreakMs != null && zeroStreakMs >= windowMs) {
        return SignalHealth.Silent
    }
    return SignalHealth.Live
}
