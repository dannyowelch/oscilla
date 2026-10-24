package com.dannyowelch.oscilla.audio

enum class AudioSourceKind(
    val label: String,
    val blurb: String,
) {
    PLAYBACK_CAPTURE(
        label = "Playback",
        blurb = "AudioPlaybackCapture for USAGE_MEDIA, USAGE_GAME, and USAGE_UNKNOWN. " +
            "Accept the system capture dialog. Oscilla then runs a mediaProjection foreground service. " +
            "Play audio in another app. Apps that set allowAudioPlaybackCapture=\"false\" stay silent.",
    ),
    VISUALIZER(
        label = "Visualizer",
        blurb = "android.media.audiofx.Visualizer on session 0, the output mix. " +
            "Needs the microphone permission and MODIFY_AUDIO_SETTINGS. Some devices return only zeros.",
    ),
    MICROPHONE(
        label = "Microphone",
        blurb = "AudioRecord from the microphone. Hum or play a speaker near the phone to confirm the meter moves.",
    ),
}

class AudioFrame(
    val pcm: FloatArray,
    val magnitudes: FloatArray,
    val sampleRateHz: Int,
    val capturedAtElapsedMs: Long,
)

interface AudioSourceCallback {
    fun onFrame(frame: AudioFrame)
    fun onError(message: String)
}

interface AudioSource {
    val kind: AudioSourceKind
    fun start(callback: AudioSourceCallback)
    fun stop()
}
