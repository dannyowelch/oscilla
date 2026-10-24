package com.dannyowelch.oscilla.visual

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dannyowelch.oscilla.audio.AudioAnalyzer
import com.dannyowelch.oscilla.audio.AudioFeatures
import com.dannyowelch.oscilla.audio.AudioFrame
import com.dannyowelch.oscilla.audio.AudioSource
import com.dannyowelch.oscilla.audio.AudioSourceCallback
import com.dannyowelch.oscilla.audio.AudioSourceKind
import com.dannyowelch.oscilla.audio.CaptureEvent
import com.dannyowelch.oscilla.audio.MicrophoneAudioSource
import com.dannyowelch.oscilla.audio.ProbeBus
import com.dannyowelch.oscilla.audio.VisualizerAudioSource
import com.dannyowelch.oscilla.capture.PlaybackCaptureService
import com.dannyowelch.oscilla.probe.PermissionStep
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ShowUiState(
    val source: AudioSourceKind,
    val visualIndex: Int,
    val params: VisualParams,
    val previousIndex: Int?,
    val previousParams: VisualParams?,
    val fadeStartedMs: Long,
    val features: AudioFeatures,
    val controlsVisible: Boolean,
    val settingsOpen: Boolean,
    val auto: AutoConfig,
    val running: Boolean,
    val status: String,
    val statusIsError: Boolean,
    val permissionStep: PermissionStep,
    val permissionNonce: Int,
    val showAppSettings: Boolean,
)

class ShowViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = OscillaPrefs(application)
    private val analyzer = AudioAnalyzer()
    private val scheduler = AutoScheduler()
    private val random = Random(System.currentTimeMillis())
    private val frames = Channel<AudioFrame>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ShowUiState> = _state.asStateFlow()

    private var session = 0
    private var inAppSource: AudioSource? = null
    private var playbackActive = false
    private var requestedRecordAudio = false

    init {
        viewModelScope.launch {
            for (frame in frames) ingest(frame)
        }
        viewModelScope.launch {
            ProbeBus.frameFlow.collect { frame ->
                if (playbackActive && ProbeBus.ownerId == ProbeBus.OWNER_SHOW) {
                    frames.trySend(frame)
                }
            }
        }
        viewModelScope.launch {
            ProbeBus.eventFlow.collect { event ->
                if (ProbeBus.ownerId != ProbeBus.OWNER_SHOW) return@collect
                when (event) {
                    CaptureEvent.Started -> _state.update {
                        it.copy(running = true, status = "Playback", statusIsError = false)
                    }
                    is CaptureEvent.Failed -> fail(event.message)
                    is CaptureEvent.Ended -> {
                        playbackActive = false
                        if (_state.value.running) {
                            _state.update { it.copy(running = false, status = event.message, statusIsError = false) }
                        }
                    }
                }
            }
        }
    }

    fun toggleControls() {
        _state.update { it.copy(controlsVisible = !it.controlsVisible) }
    }

    fun setSettingsOpen(open: Boolean) {
        _state.update { it.copy(settingsOpen = open, controlsVisible = true) }
    }

    fun stepVisual(delta: Int) {
        val count = VisualCatalog.all.size
        val next = Math.floorMod(_state.value.visualIndex + delta, count)
        fadeTo(next, _state.value.params)
        prefs.setVisualIndex(next)
    }

    fun randomizeCurrent() {
        fadeTo(_state.value.visualIndex, randomParams(random))
    }

    fun selectSource(kind: AudioSourceKind) {
        prefs.setSource(kind)
        _state.update { it.copy(source = kind, status = "", statusIsError = false, showAppSettings = false) }
        releaseCapture()
    }

    fun setAuto(config: AutoConfig) {
        val safe = config.copy(interval = config.interval.coerceAtLeast(1))
        prefs.setAuto(safe)
        scheduler.reset()
        _state.update { it.copy(auto = safe) }
    }

    fun onDrawFrame(nowMs: Long) {
        val config = _state.value.auto
        scheduler.observe(_state.value.features.beat)
        if (!scheduler.due(nowMs, config)) return
        scheduler.mark(nowMs)
        val nextIndex = if (config.switchVisual) {
            nextVisualIndex(_state.value.visualIndex, VisualCatalog.all.size, random)
        } else {
            _state.value.visualIndex
        }
        fadeTo(nextIndex, randomParams(random))
        prefs.setVisualIndex(nextIndex)
    }

    fun ensureStarted(
        recordGranted: Boolean,
        notificationsGranted: Boolean,
        notificationsRequired: Boolean,
    ) {
        if (_state.value.running || _state.value.permissionStep != PermissionStep.Idle) return
        requestStart(recordGranted, notificationsGranted, notificationsRequired)
    }

    fun requestStart(
        recordGranted: Boolean,
        notificationsGranted: Boolean,
        notificationsRequired: Boolean,
    ) {
        when (_state.value.source) {
            AudioSourceKind.MICROPHONE, AudioSourceKind.VISUALIZER -> {
                if (recordGranted) startInApp() else ask(PermissionStep.RecordAudio)
            }
            AudioSourceKind.PLAYBACK_CAPTURE -> when {
                !recordGranted -> ask(PermissionStep.RecordAudio)
                notificationsRequired && !notificationsGranted -> ask(PermissionStep.Notifications)
                else -> ask(PermissionStep.Projection)
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
                        "Microphone permission is off. Enable it in system settings."
                    } else {
                        "Microphone permission is required."
                    },
                    statusIsError = true,
                    showAppSettings = permanent,
                    running = false,
                )
            }
            return
        }
        when (_state.value.source) {
            AudioSourceKind.MICROPHONE, AudioSourceKind.VISUALIZER -> startInApp()
            AudioSourceKind.PLAYBACK_CAPTURE -> {
                if (notificationsRequired && !notificationsGranted) {
                    ask(PermissionStep.Notifications)
                } else {
                    ask(PermissionStep.Projection)
                }
            }
        }
    }

    fun onNotificationsResult(granted: Boolean) {
        if (!granted) {
            _state.update {
                it.copy(status = "Notifications are off. Playback capture can still run.", statusIsError = false)
            }
        }
        ask(PermissionStep.Projection)
    }

    fun onProjectionGranted(context: Context, resultCode: Int, data: Intent) {
        inAppSource?.stop()
        inAppSource = null
        ProbeBus.ownerId = ProbeBus.OWNER_SHOW
        ProbeBus.wantsPlayback = true
        playbackActive = true
        PlaybackCaptureService.start(context, resultCode, data)
        _state.update { it.copy(running = true, status = "Starting playback…", statusIsError = false) }
    }

    fun onProjectionDenied() {
        if (ProbeBus.ownerId == ProbeBus.OWNER_SHOW) ProbeBus.wantsPlayback = false
        playbackActive = false
        _state.update {
            it.copy(running = false, status = "Playback capture was not allowed.", statusIsError = true)
        }
    }

    fun onHostStopped(changingConfigurations: Boolean) {
        if (changingConfigurations) return
        releaseCapture()
    }

    fun releaseCapture() {
        session += 1
        inAppSource?.stop()
        inAppSource = null
        val ownedPlayback = playbackActive || ProbeBus.ownerId == ProbeBus.OWNER_SHOW
        playbackActive = false
        if (ownedPlayback) {
            ProbeBus.wantsPlayback = false
            if (ProbeBus.ownerId == ProbeBus.OWNER_SHOW) ProbeBus.ownerId = ""
            PlaybackCaptureService.stop(getApplication())
        }
        analyzer.reset()
        _state.update { it.copy(running = false, features = emptyFeatures()) }
    }

    override fun onCleared() {
        releaseCapture()
    }

    private fun ask(step: PermissionStep) {
        if (step == PermissionStep.RecordAudio) requestedRecordAudio = true
        _state.update { it.copy(permissionStep = step, permissionNonce = it.permissionNonce + 1) }
    }

    private fun startInApp() {
        if (playbackActive || ProbeBus.ownerId == ProbeBus.OWNER_SHOW) {
            ProbeBus.wantsPlayback = false
            playbackActive = false
            PlaybackCaptureService.stop(getApplication())
        }
        inAppSource?.stop()
        val context = getApplication<Application>()
        val source: AudioSource = when (_state.value.source) {
            AudioSourceKind.MICROPHONE -> MicrophoneAudioSource(context)
            AudioSourceKind.VISUALIZER -> VisualizerAudioSource(context)
            AudioSourceKind.PLAYBACK_CAPTURE -> return
        }
        session += 1
        val active = session
        inAppSource = source
        _state.update {
            it.copy(running = true, status = source.kind.label, statusIsError = false, showAppSettings = false)
        }
        source.start(object : AudioSourceCallback {
            override fun onFrame(frame: AudioFrame) {
                if (session != active) return
                frames.trySend(frame)
            }

            override fun onError(message: String) {
                viewModelScope.launch {
                    if (session != active) return@launch
                    fail(message)
                }
            }
        })
    }

    private fun ingest(frame: AudioFrame) {
        if (!_state.value.running) return
        val features = analyzer.process(frame.magnitudes, frame.pcm, frame.capturedAtElapsedMs)
        _state.update { it.copy(features = features) }
    }

    private fun fadeTo(index: Int, params: VisualParams) {
        prefs.setParams(params)
        _state.update {
            it.copy(
                previousIndex = it.visualIndex,
                previousParams = it.params,
                visualIndex = index,
                params = params,
                fadeStartedMs = SystemClock.elapsedRealtime(),
            )
        }
    }

    private fun fail(message: String) {
        session += 1
        inAppSource?.stop()
        inAppSource = null
        playbackActive = false
        _state.update { it.copy(running = false, status = message, statusIsError = true, controlsVisible = true) }
    }

    private fun initialState(): ShowUiState {
        return ShowUiState(
            source = prefs.source(),
            visualIndex = prefs.visualIndex(),
            params = prefs.params(),
            previousIndex = null,
            previousParams = null,
            fadeStartedMs = 0L,
            features = emptyFeatures(),
            controlsVisible = true,
            settingsOpen = false,
            auto = prefs.auto(),
            running = false,
            status = "",
            statusIsError = false,
            permissionStep = PermissionStep.Idle,
            permissionNonce = 0,
            showAppSettings = false,
        )
    }
}

fun emptyFeatures(): AudioFeatures {
    return AudioFeatures(
        level = 0f,
        peak = 0f,
        bands = FloatArray(24),
        waveform = FloatArray(0),
        bass = 0f,
        mid = 0f,
        treble = 0f,
        flux = 0f,
        onset = false,
        beat = false,
    )
}
