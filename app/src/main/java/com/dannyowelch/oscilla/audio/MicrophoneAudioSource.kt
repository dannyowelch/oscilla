package com.dannyowelch.oscilla.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

class MicrophoneAudioSource(
    private val context: Context,
) : AudioSource {
    override val kind: AudioSourceKind = AudioSourceKind.MICROPHONE

    private var capture: PcmCapture? = null

    override fun start(callback: AudioSourceCallback) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback.onError("RECORD_AUDIO is not granted.")
            return
        }
        try {
            val sampleRate = chooseSampleRate()
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(monoPcm16(sampleRate))
                .setBufferSizeInBytes(captureBufferBytes(sampleRate))
                .build()
            val reader = PcmCapture(record, sampleRate)
            capture = reader
            reader.start(callback)
        } catch (error: Throwable) {
            callback.onError(error.message ?: "Microphone capture failed to start.")
        }
    }

    override fun stop() {
        val reader = capture
        capture = null
        reader?.stop()
    }
}

internal fun hasRecordAudio(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
}
