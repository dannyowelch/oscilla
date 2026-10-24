package com.dannyowelch.oscilla.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import androidx.core.content.ContextCompat

class PlaybackCaptureAudioSource(
    private val context: Context,
    private val projection: MediaProjection,
) : AudioSource {
    override val kind: AudioSourceKind = AudioSourceKind.PLAYBACK_CAPTURE

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
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val record = AudioRecord.Builder()
                .setAudioFormat(monoPcm16(sampleRate))
                .setBufferSizeInBytes(captureBufferBytes(sampleRate))
                .setAudioPlaybackCaptureConfig(config)
                .build()
            val reader = PcmCapture(record, sampleRate)
            capture = reader
            reader.start(callback)
        } catch (error: Throwable) {
            callback.onError(error.message ?: "Playback capture failed to start.")
        }
    }

    override fun stop() {
        val reader = capture
        capture = null
        reader?.stop()
    }
}
