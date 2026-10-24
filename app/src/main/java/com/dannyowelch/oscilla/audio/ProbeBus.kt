package com.dannyowelch.oscilla.audio

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface CaptureEvent {
    data object Started : CaptureEvent
    data class Failed(val message: String) : CaptureEvent
    data class Ended(val message: String) : CaptureEvent
}

/**
 * Process-wide bridge from [com.dannyowelch.oscilla.capture.PlaybackCaptureService]
 * to the probe screen. Playback capture has to run inside that service.
 */
object ProbeBus {
    @Volatile
    var wantsPlayback: Boolean = false

    private val frames = MutableSharedFlow<AudioFrame>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val events = MutableSharedFlow<CaptureEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val frameFlow: SharedFlow<AudioFrame> = frames.asSharedFlow()
    val eventFlow: SharedFlow<CaptureEvent> = events.asSharedFlow()

    fun publishFrame(frame: AudioFrame) {
        frames.tryEmit(frame)
    }

    fun publish(event: CaptureEvent) {
        events.tryEmit(event)
    }
}
