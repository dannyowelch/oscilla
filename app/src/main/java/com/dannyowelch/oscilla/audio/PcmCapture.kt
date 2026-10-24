package com.dannyowelch.oscilla.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

internal const val FRAME_SAMPLES: Int = 2048

internal fun chooseSampleRate(): Int {
    val rates = intArrayOf(48_000, 44_100, 16_000)
    for (rate in rates) {
        val min = AudioRecord.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min > 0) return rate
    }
    return 48_000
}

internal fun monoPcm16(sampleRateHz: Int): AudioFormat {
    return AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(sampleRateHz)
        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
        .build()
}

internal fun captureBufferBytes(sampleRateHz: Int): Int {
    val min = AudioRecord.getMinBufferSize(
        sampleRateHz,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )
    val frameBytes = FRAME_SAMPLES * 2
    val floor = if (min > 0) maxOf(min, frameBytes) else frameBytes
    return floor * 2
}

internal class PcmCapture(
    private val record: AudioRecord,
    private val sampleRateHz: Int,
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start(callback: AudioSourceCallback) {
        if (!running.compareAndSet(false, true)) return
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            running.set(false)
            record.release()
            callback.onError("AudioRecord was not initialized.")
            return
        }
        val worker = Thread({
            try {
                record.startRecording()
                val buffer = ShortArray(FRAME_SAMPLES)
                while (running.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read == 0) continue
                    if (read < 0) {
                        callback.onError("AudioRecord.read failed ($read).")
                        break
                    }
                    val pcm = pcm16ToFloat(buffer, read)
                    callback.onFrame(
                        AudioFrame(
                            pcm = pcm,
                            magnitudes = magnitudeSpectrum(pcm),
                            sampleRateHz = sampleRateHz,
                            capturedAtElapsedMs = SystemClock.elapsedRealtime(),
                        ),
                    )
                }
            } catch (error: Throwable) {
                callback.onError(error.message ?: error.javaClass.simpleName)
            } finally {
                try {
                    record.release()
                } catch (_: Throwable) {
                }
            }
        }, "oscilla-pcm")
        thread = worker
        worker.start()
    }

    fun stop() {
        running.set(false)
        try {
            if (record.state == AudioRecord.STATE_INITIALIZED &&
                record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            ) {
                record.stop()
            }
        } catch (_: IllegalStateException) {
        }
        thread?.join(2_000)
    }
}
