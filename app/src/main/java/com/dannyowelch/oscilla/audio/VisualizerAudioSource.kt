package com.dannyowelch.oscilla.audio

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** Output-mix Visualizer. Session 0 is the global mix, not an app player. */
class VisualizerAudioSource(
    private val context: Context,
) : AudioSource {
    override val kind: AudioSourceKind = AudioSourceKind.VISUALIZER

    private val started = AtomicBoolean(false)
    private var visualizer: Visualizer? = null
    private var pcm = FloatArray(0)
    private var magnitudes = FloatArray(0)

    override fun start(callback: AudioSourceCallback) {
        if (!started.compareAndSet(false, true)) return
        if (!hasRecordAudio(context)) {
            started.set(false)
            callback.onError("RECORD_AUDIO is not granted.")
            return
        }
        try {
            val effect = Visualizer(OUTPUT_MIX_SESSION)
            val range = Visualizer.getCaptureSizeRange()
            effect.captureSize = range[1]
            effect.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int,
                    ) {
                        if (!started.get()) return
                        pcm = visualizerWaveformToPcm(waveform)
                        emit(callback, samplingRate)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int,
                    ) {
                        if (!started.get()) return
                        magnitudes = visualizerFftToMagnitudes(fft)
                        emit(callback, samplingRate)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                true,
                true,
            )
            effect.enabled = true
            visualizer = effect
        } catch (error: Throwable) {
            started.set(false)
            callback.onError(error.message ?: "Visualizer failed to start.")
        }
    }

    private fun emit(callback: AudioSourceCallback, samplingRateMilliHz: Int) {
        callback.onFrame(
            AudioFrame(
                pcm = pcm,
                magnitudes = magnitudes,
                sampleRateHz = samplingRateMilliHz / 1000,
                capturedAtElapsedMs = SystemClock.elapsedRealtime(),
            ),
        )
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        val effect = visualizer
        visualizer = null
        try {
            effect?.enabled = false
        } catch (_: Throwable) {
        }
        try {
            effect?.release()
        } catch (_: Throwable) {
        }
    }

    private companion object {
        const val OUTPUT_MIX_SESSION: Int = 0
    }
}
