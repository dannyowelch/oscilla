package com.dannyowelch.oscilla.probe

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dannyowelch.oscilla.BuildConfig
import com.dannyowelch.oscilla.audio.AudioSourceKind
import com.dannyowelch.oscilla.audio.ProbeTiming
import com.dannyowelch.oscilla.audio.SignalHealth
import kotlin.math.abs

@Composable
fun ProbeScreen(viewModel: ProbeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val projectionManager = remember {
        activity.getSystemService(android.media.projection.MediaProjectionManager::class.java)
    }

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
    ) { granted ->
        viewModel.onNotificationsResult(granted)
    }
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
            PermissionStep.RecordAudio -> {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            PermissionStep.Notifications -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PermissionStep.Projection -> {
                projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
            PermissionStep.Idle -> Unit
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onHostStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Oscilla", style = MaterialTheme.typography.headlineMedium)
            Text("Audio source probe", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AudioSourceKind.entries.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = state.kind == kind,
                        onClick = { viewModel.select(kind) },
                        enabled = !state.running,
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AudioSourceKind.entries.size,
                        ),
                    ) {
                        Text(kind.label)
                    }
                }
            }
            Text(state.kind.blurb, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    if (state.running) {
                        viewModel.stop()
                    } else {
                        viewModel.onStartClicked(
                            recordAudioGranted = hasRecordAudio(activity),
                            notificationsGranted = notificationsGranted(activity),
                            notificationsRequired = notificationsRequired(),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.running) "Stop" else "Start")
            }
            if (state.showSettings) {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            this.data = Uri.fromParts("package", activity.packageName, null)
                        }
                        activity.startActivity(intent)
                    },
                ) {
                    Text("Open settings")
                }
            }
            Text(
                state.status,
                color = if (state.statusIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (state.running &&
                (state.health == SignalHealth.Silent || state.health == SignalHealth.NoFrames)
            ) {
                val label = if (state.health == SignalHealth.Silent) "SILENT" else "NO FRAMES"
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                "SILENT means only zeros for ${ProbeTiming.SILENCE_WINDOW_MS / 1000} seconds. " +
                    "NO FRAMES means the source sent nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Level (RMS)", style = MaterialTheme.typography.labelLarge)
            LevelMeter(level = state.level)
            Text(
                "RMS ${format4(state.rms)} · peak ${format4(state.peak)} · " +
                    "FFT peak ${format4(state.peakMagnitude)} · ${state.sampleRateHz} Hz · " +
                    "${state.framesReceived} frames",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Waveform (scaled to this frame)", style = MaterialTheme.typography.labelLarge)
            Waveform(samples = state.waveform)
            Text("FFT (scaled to this frame)", style = MaterialTheme.typography.labelLarge)
            SpectrumBars(bars = state.bars)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LevelMeter(level: Float) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(maxWidth * level.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun Waveform(samples: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val mid = size.height / 2f
        drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1f)
        if (samples.size < 2) return@Canvas
        val peak = samples.maxOf { sample ->
            val value = if (sample.isFinite()) abs(sample) else 0f
            value
        }
        val scale = if (peak < 1e-4f) 1f else peak
        val step = size.width / (samples.size - 1)
        for (index in 1 until samples.size) {
            val previous = samples[index - 1].finiteOrZero() / scale
            val current = samples[index].finiteOrZero() / scale
            drawLine(
                color = color,
                start = Offset((index - 1) * step, mid - previous.coerceIn(-1f, 1f) * mid),
                end = Offset(index * step, mid - current.coerceIn(-1f, 1f) * mid),
                strokeWidth = 2f,
            )
        }
    }
}

@Composable
private fun SpectrumBars(bars: List<Float>) {
    val color = MaterialTheme.colorScheme.secondary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (bars.isEmpty()) return@Canvas
        val peak = bars.maxOf { if (it.isFinite()) it else 0f }
        val audible = peak > 1e-4f
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
        bars.forEachIndexed { index, value ->
            val fraction = if (audible) (value.finiteOrZero() / peak).coerceIn(0f, 1f) else 0f
            val height = fraction * size.height
            val left = index * (barWidth + gap)
            drawLine(
                color = color,
                start = Offset(left + barWidth / 2f, size.height),
                end = Offset(left + barWidth / 2f, size.height - height),
                strokeWidth = barWidth,
            )
        }
    }
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

private fun format4(value: Float): String = "%.4f".format(java.util.Locale.US, value)

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
