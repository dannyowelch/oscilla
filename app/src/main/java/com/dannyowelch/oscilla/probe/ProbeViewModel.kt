package com.dannyowelch.oscilla.probe

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dannyowelch.oscilla.audio.AudioFrame
import com.dannyowelch.oscilla.audio.AudioSource
import com.dannyowelch.oscilla.audio.AudioSourceCallback
import com.dannyowelch.oscilla.audio.AudioSourceKind
import com.dannyowelch.oscilla.audio.CaptureEvent
import com.dannyowelch.oscilla.audio.FFT_BARS
import com.dannyowelch.oscilla.audio.MicrophoneAudioSource
import com.dannyowelch.oscilla.audio.ProbeBus
import com.dannyowelch.oscilla.audio.SignalHealth
import com.dannyowelch.oscilla.audio.SilenceDetector
import com.dannyowelch.oscilla.audio.VisualizerAudioSource
import com.dannyowelch.oscilla.audio.WAVEFORM_POINTS
import com.dannyowelch.oscilla.audio.averageBins
import com.dannyowelch.oscilla.audio.downsamplePeaks
import com.dannyowelch.oscilla.audio.peakAbs
import com.dannyowelch.oscilla.audio.rms
import com.dannyowelch.oscilla.audio.signalHealth
import com.dannyowelch.oscilla.capture.PlaybackCaptureService
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PermissionStep {
    Idle,
    RecordAudio,
    Notifications,
    Projection,
}

data class ProbeUiState(
    val kind: AudioSourceKind = AudioSourceKind.PLAYBACK_CAPTURE,
    val running: Boolean = false,
    val level: Float = 0f,
    val rms: Float = 0f,
    val peak: Float = 0f,
    val peakMagnitude: Float = 0f,
    val sampleRateHz: Int = 0,
    val framesReceived: Long = 0,
    val waveform: List<Float> = emptyList(),
    val bars: List<Float> = emptyList(),
    val health: SignalHealth = SignalHealth.Waiting,
    val status: String = "Pick a source and start. Play music, then compare the three sources.",
    val statusIsError: Boolean = false,
    val permissionStep: PermissionStep = PermissionStep.Idle,
    val permissionNonce: Int = 0,
    val showSettings: Boolean = false,
)

class ProbeViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ProbeUiState())
    val state: StateFlow<ProbeUiState> = _state.asStateFlow()

    private val frames = Channel<AudioFrame>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var session = 0
    private var inAppSource: AudioSource? = null
    private var playbackActive = false
    private var requestedRecordAudio = false
    private val silence = SilenceDetector()
    private var runningSinceMs = 0L
    private var framesReceived = 0L
    private var zeroStreakMs: Long? = null
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            for (frame in frames) ingest(frame)
        }
        viewModelScope.launch {
            ProbeBus.frameFlow.collect { frame ->
                if (playbackActive && _state.value.running &&
                    _state.value.kind == AudioSourceKind.PLAYBACK_CAPTURE
                ) {
                    frames.trySend(frame)
                }
            }
        }
        viewModelScope.launch {
            ProbeBus.eventFlow.collect { event -> onCaptureEvent(event) }
        }
    }

    fun select(kind: AudioSourceKind) {
        if (_state.value.running) return
        _state.update { it.copy(kind = kind, showSettings = false, statusIsError = false) }
    }

    fun onStartClicked(
        recordAudioGranted: Boolean,
        notificationsGranted: Boolean,
        notificationsRequired: Boolean,
    ) {
        if (_state.value.running) return
        _state.update { it.copy(showSettings = false) }
        when (_state.value.kind) {
            AudioSourceKind.MICROPHONE, AudioSourceKind.VISUALIZER -> {
                if (recordAudioGranted) startInApp() else request(PermissionStep.RecordAudio)
            }
            AudioSourceKind.PLAYBACK_CAPTURE -> when {
                !recordAudioGranted -> request(PermissionStep.RecordAudio)
                notificationsRequired && !notificationsGranted -> request(PermissionStep.Notifications)
                else -> request(PermissionStep.Projection)
            }
        }
    }

    fun consumePermissionStep(): PermissionStep {
        val step = _state.value.permissionStep
        _state.update { it.copy(permissionStep = PermissionStep.Idle) }
        return step
    }

    fun onRecordAudioResult(
        granted: Boolean,
        shouldShowRationale: Boolean,
        notificationsGranted: Boolean,
        notificationsRequired: Boolean,
    ) {
        if (!granted) {
            val permanent = !shouldShowRationale && requestedRecordAudio
            _state.update {
                it.copy(
                    status = if (permanent) {
                        "Microphone permission is off. Enable RECORD_AUDIO in system settings."
                    } else {
                        "Microphone permission is required for this source."
                    },
                    statusIsError = true,
                    showSettings = permanent,
                )
            }
            return
        }
        continueAfterAudio(notificationsGranted, notificationsRequired)
    }

    fun onNotificationsResult(granted: Boolean) {
        if (!granted) {
            _state.update {
                it.copy(
                    status = "Notifications are off. Capture can still run, but the status notification may be hidden.",
                    statusIsError = false,
                )
            }
        }
        request(PermissionStep.Projection)
    }

    fun onProjectionGranted(context: Context, resultCode: Int, data: Intent) {
        inAppSource?.stop()
        inAppSource = null
        ProbeBus.wantsPlayback = true
        playbackActive = true
        PlaybackCaptureService.start(context, resultCode, data)
        beginSessionUi("Starting playback capture…")
    }

    fun onProjectionDenied() {
        ProbeBus.wantsPlayback = false
        playbackActive = false
        _state.update {
            it.copy(
                running = false,
                status = "Playback capture was not allowed.",
                statusIsError = true,
            )
        }
    }

    fun stop() {
        session += 1
        val stopService = playbackActive || ProbeBus.wantsPlayback
        ProbeBus.wantsPlayback = false
        playbackActive = false
        ticker?.cancel()
        ticker = null
        inAppSource?.stop()
        inAppSource = null
        if (stopService) PlaybackCaptureService.stop(getApplication())
        _state.update {
            it.copy(
                running = false,
                health = SignalHealth.Waiting,
                status = "Stopped.",
                statusIsError = false,
                showSettings = false,
            )
        }
    }

    fun onHostStopped() {
        val source = inAppSource ?: return
        session += 1
        source.stop()
        inAppSource = null
        ticker?.cancel()
        ticker = null
        _state.update {
            it.copy(
                running = false,
                health = SignalHealth.Waiting,
                status = "Stopped because the app left the foreground.",
                statusIsError = false,
            )
        }
    }

    override fun onCleared() {
        session += 1
        ticker?.cancel()
        inAppSource?.stop()
        inAppSource = null
        ProbeBus.wantsPlayback = false
        playbackActive = false
        PlaybackCaptureService.stop(getApplication())
    }

    private fun continueAfterAudio(notificationsGranted: Boolean, notificationsRequired: Boolean) {
        when (_state.value.kind) {
            AudioSourceKind.MICROPHONE, AudioSourceKind.VISUALIZER -> startInApp()
            AudioSourceKind.PLAYBACK_CAPTURE -> {
                if (notificationsRequired && !notificationsGranted) {
                    request(PermissionStep.Notifications)
                } else {
                    request(PermissionStep.Projection)
                }
            }
        }
    }

    private fun request(step: PermissionStep) {
        if (step == PermissionStep.RecordAudio) requestedRecordAudio = true
        _state.update { it.copy(permissionStep = step, permissionNonce = it.permissionNonce + 1) }
    }

    private fun startInApp() {
        ProbeBus.wantsPlayback = false
        if (playbackActive) {
            playbackActive = false
            PlaybackCaptureService.stop(getApplication())
        }
        inAppSource?.stop()
        val context = getApplication<Application>()
        val source: AudioSource = when (_state.value.kind) {
            AudioSourceKind.MICROPHONE -> MicrophoneAudioSource(context)
            AudioSourceKind.VISUALIZER -> VisualizerAudioSource(context)
            AudioSourceKind.PLAYBACK_CAPTURE -> return
        }
        beginSessionUi(runningStatus(source.kind))
        val activeSession = session
        inAppSource = source
        source.start(object : AudioSourceCallback {
            override fun onFrame(frame: AudioFrame) {
                if (session != activeSession) return
                frames.trySend(frame)
            }

            override fun onError(message: String) {
                viewModelScope.launch {
                    if (session != activeSession) return@launch
                    fail(message)
                }
            }
        })
    }

    private fun onCaptureEvent(event: CaptureEvent) {
        when (event) {
            CaptureEvent.Started -> {
                if (!ProbeBus.wantsPlayback || !_state.value.running) return
                _state.update {
                    it.copy(
                        status = "Capturing playback. Play audio in another app.",
                        statusIsError = false,
                    )
                }
            }
            is CaptureEvent.Failed -> {
                if (!playbackActive && !ProbeBus.wantsPlayback && !_state.value.running) return
                playbackActive = false
                ProbeBus.wantsPlayback = false
                fail(event.message)
            }
            is CaptureEvent.Ended -> {
                if (!_state.value.running) {
                    playbackActive = false
                    return
                }
                playbackActive = false
                ProbeBus.wantsPlayback = false
                finish(event.message)
            }
        }
    }

    private fun beginSessionUi(status: String) {
        session += 1
        silence.reset()
        framesReceived = 0
        zeroStreakMs = null
        runningSinceMs = SystemClock.elapsedRealtime()
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(250)
                if (_state.value.running) publishHealth()
            }
        }
        _state.update {
            it.copy(
                running = true,
                level = 0f,
                rms = 0f,
                peak = 0f,
                peakMagnitude = 0f,
                sampleRateHz = 0,
                framesReceived = 0,
                waveform = emptyList(),
                bars = emptyList(),
                health = SignalHealth.Waiting,
                status = status,
                statusIsError = false,
                showSettings = false,
            )
        }
    }

    private fun ingest(frame: AudioFrame) {
        if (!_state.value.running) return
        framesReceived += 1
        zeroStreakMs = silence.onFrame(frame.pcm, frame.magnitudes, frame.capturedAtElapsedMs)
        val energy = rms(frame.pcm)
        val safeRms = if (energy.isFinite()) energy else 0f
        val elapsed = (frame.capturedAtElapsedMs - runningSinceMs).coerceAtLeast(0L)
        _state.update {
            it.copy(
                level = safeRms.coerceIn(0f, 1f),
                rms = safeRms,
                peak = peakAbs(frame.pcm),
                peakMagnitude = frame.magnitudes.maxOrNull() ?: 0f,
                sampleRateHz = frame.sampleRateHz,
                framesReceived = framesReceived,
                waveform = downsamplePeaks(frame.pcm, WAVEFORM_POINTS).toList(),
                bars = averageBins(frame.magnitudes, FFT_BARS).toList(),
                health = signalHealth(elapsed, framesReceived, zeroStreakMs),
            )
        }
    }

    private fun publishHealth() {
        val elapsed = (SystemClock.elapsedRealtime() - runningSinceMs).coerceAtLeast(0L)
        val health = signalHealth(elapsed, framesReceived, zeroStreakMs)
        _state.update { it.copy(health = health) }
    }

    private fun fail(message: String) {
        session += 1
        ticker?.cancel()
        ticker = null
        inAppSource?.stop()
        inAppSource = null
        playbackActive = false
        _state.update {
            it.copy(
                running = false,
                health = SignalHealth.Waiting,
                status = message,
                statusIsError = true,
            )
        }
    }

    private fun finish(message: String) {
        session += 1
        ticker?.cancel()
        ticker = null
        _state.update {
            it.copy(
                running = false,
                health = SignalHealth.Waiting,
                status = message,
                statusIsError = false,
            )
        }
    }

    private fun runningStatus(kind: AudioSourceKind): String {
        return when (kind) {
            AudioSourceKind.MICROPHONE -> "Listening to the microphone."
            AudioSourceKind.VISUALIZER -> "Reading Visualizer session 0."
            AudioSourceKind.PLAYBACK_CAPTURE -> "Capturing playback."
        }
    }
}
