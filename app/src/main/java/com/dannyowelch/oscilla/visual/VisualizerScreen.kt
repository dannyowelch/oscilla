package com.dannyowelch.oscilla.visual

import android.Manifest
import androidx.compose.material3.ExperimentalMaterial3Api
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dannyowelch.oscilla.audio.AudioSourceKind
import com.dannyowelch.oscilla.probe.PermissionStep
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizerScreen(
    onOpenProbe: () -> Unit,
    viewModel: ShowViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val projectionManager = remember {
        activity.getSystemService(android.media.projection.MediaProjectionManager::class.java)
    }
    val runtimes = remember { HashMap<String, VisualRuntime>() }
    var nowMs by remember { mutableLongStateOf(0L) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onRecordAudioResult(
            granted = granted,
            shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO,
            ),
            notificationsGranted = notificationsGranted(activity),
            notificationsRequired = notificationsRequired(),
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onNotificationsResult(granted) }
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.onProjectionGranted(activity, result.resultCode, data)
        } else {
            viewModel.onProjectionDenied()
        }
    }

    LaunchedEffect(state.permissionNonce) {
        when (viewModel.consumePermissionStep()) {
            PermissionStep.RecordAudio -> audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            PermissionStep.Notifications -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PermissionStep.Projection -> projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            PermissionStep.Idle -> Unit
        }
    }

    LaunchedEffect(state.source) {
        viewModel.ensureStarted(
            recordGranted = hasRecordAudio(activity),
            notificationsGranted = notificationsGranted(activity),
            notificationsRequired = notificationsRequired(),
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frame -> nowMs = frame }
            viewModel.onDrawFrame(SystemClock.elapsedRealtime())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onHostStopped(activity.isChangingConfigurations)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity) {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val fade = if (state.previousIndex == null) {
        1f
    } else {
        crossfadeAlpha(SystemClock.elapsedRealtime() - state.fadeStartedMs)
    }
    val current = VisualCatalog.spec(state.visualIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = viewModel::toggleControls,
            ),
    ) {
        val previousIndex = state.previousIndex
        val previousParams = state.previousParams
        if (previousIndex != null && previousParams != null && fade < 1f) {
            VisualCanvas(
                spec = VisualCatalog.spec(previousIndex),
                params = previousParams,
                features = state.features,
                runtime = runtimeFor(runtimes, previousIndex),
                timeMs = nowMs,
                alpha = 1f - fade,
            )
        }
        VisualCanvas(
            spec = current,
            params = state.params,
            features = state.features,
            runtime = runtimeFor(runtimes, state.visualIndex),
            timeMs = nowMs,
            alpha = fade,
        )
        if (state.statusIsError) {
            Text(
                text = state.status,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        }
        if (state.controlsVisible) {
            Controls(
                state = state,
                onPrevious = { viewModel.stepVisual(-1) },
                onNext = { viewModel.stepVisual(1) },
                onShuffle = viewModel::randomizeCurrent,
                onAuto = { viewModel.setAuto(state.auto.copy(enabled = !state.auto.enabled)) },
                onSettings = { viewModel.setSettingsOpen(true) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (state.settingsOpen) {
        ModalBottomSheet(onDismissRequest = { viewModel.setSettingsOpen(false) }) {
            SettingsSheet(
                state = state,
                onSource = { kind ->
                    viewModel.selectSource(kind)
                    viewModel.ensureStarted(
                        recordGranted = hasRecordAudio(activity),
                        notificationsGranted = notificationsGranted(activity),
                        notificationsRequired = notificationsRequired(),
                    )
                },
                onAuto = viewModel::setAuto,
                onOpenSettings = {
                    activity.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", activity.packageName, null)
                        },
                    )
                },
                onOpenProbe = {
                    viewModel.releaseCapture()
                    viewModel.setSettingsOpen(false)
                    onOpenProbe()
                },
            )
        }
    }
}

@Composable
private fun VisualCanvas(
    spec: VisualSpec,
    params: VisualParams,
    features: com.dannyowelch.oscilla.audio.AudioFeatures,
    runtime: VisualRuntime,
    timeMs: Long,
    alpha: Float,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        with(spec.draw) { draw(features, params, runtime, timeMs) }
    }
}

@Composable
private fun Controls(
    state: ShowUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onAuto: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = VisualCatalog.spec(state.visualIndex)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
    ) {
        Text(
            text = "${visual.name} · ${state.source.label}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = visual.blurb,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onPrevious) { Text("Prev") }
            TextButton(onClick = onNext) { Text("Next") }
            TextButton(onClick = onShuffle) { Text("Shuffle") }
            TextButton(onClick = onAuto) { Text(if (state.auto.enabled) "Auto on" else "Auto") }
            TextButton(onClick = onSettings) { Text("Settings") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    state: ShowUiState,
    onSource: (AudioSourceKind) -> Unit,
    onAuto: (AutoConfig) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProbe: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Audio source", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AudioSourceKind.entries.forEach { kind ->
                FilterChip(
                    selected = state.source == kind,
                    onClick = { onSource(kind) },
                    label = { Text(kind.label) },
                )
            }
        }
        Text(
            state.source.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.showAppSettings) {
            TextButton(onClick = onOpenSettings) { Text("Open app settings") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Auto", modifier = Modifier.weight(1f))
            Switch(checked = state.auto.enabled, onCheckedChange = { onAuto(state.auto.copy(enabled = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Switch visual", modifier = Modifier.weight(1f))
            Switch(
                checked = state.auto.switchVisual,
                onCheckedChange = { onAuto(state.auto.copy(switchVisual = it)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.auto.trigger == AutoTrigger.SECONDS,
                onClick = { onAuto(state.auto.copy(trigger = AutoTrigger.SECONDS, interval = 24)) },
                label = { Text("Seconds") },
            )
            FilterChip(
                selected = state.auto.trigger == AutoTrigger.BEATS,
                onClick = { onAuto(state.auto.copy(trigger = AutoTrigger.BEATS, interval = 16)) },
                label = { Text("Beats") },
            )
        }
        val range = if (state.auto.trigger == AutoTrigger.SECONDS) 8f..60f else 4f..48f
        Text(
            if (state.auto.trigger == AutoTrigger.SECONDS) {
                "Every ${state.auto.interval} seconds"
            } else {
                "Every ${state.auto.interval} beats"
            },
        )
        Slider(
            value = state.auto.interval.toFloat().coerceIn(range.start, range.endInclusive),
            onValueChange = { onAuto(state.auto.copy(interval = it.roundToInt().coerceAtLeast(1))) },
            valueRange = range,
        )
        TextButton(onClick = onOpenProbe) { Text("Open audio probe") }
    }
}

private fun runtimeFor(runtimes: MutableMap<String, VisualRuntime>, index: Int): VisualRuntime {
    val spec = VisualCatalog.spec(index)
    return runtimes.getOrPut(spec.id, spec.createRuntime)
}

private fun hasRecordAudio(activity: Activity): Boolean {
    return ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun notificationsRequired(): Boolean = Build.VERSION.SDK_INT >= 33

private fun notificationsGranted(activity: Activity): Boolean {
    if (!notificationsRequired()) return true
    return ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
