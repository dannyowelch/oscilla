package com.dannyowelch.oscilla.capture

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.dannyowelch.oscilla.MainActivity
import com.dannyowelch.oscilla.R
import com.dannyowelch.oscilla.audio.AudioSourceCallback
import com.dannyowelch.oscilla.audio.CaptureEvent
import com.dannyowelch.oscilla.audio.PlaybackCaptureAudioSource
import com.dannyowelch.oscilla.audio.ProbeBus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Media projection consent has to be granted before this service starts.
 * Inside [onStartCommand] the order is fixed: promote to a mediaProjection
 * foreground service, then [MediaProjectionManager.getMediaProjection],
 * then [MediaProjection.registerCallback], then the [android.media.AudioRecord].
 */
class PlaybackCaptureService : android.app.Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private var projection: MediaProjection? = null
    private var audioSource: PlaybackCaptureAudioSource? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            shutdown(
                message = "Playback capture stopped. The token was revoked or the user ended the projection.",
                stopProjection = false,
                failed = false,
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!ProbeBus.wantsPlayback) {
            shutdown(message = null, stopProjection = true, failed = false)
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java)
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            shutdown(
                message = "Playback capture consent was missing.",
                stopProjection = false,
                failed = true,
            )
            return START_NOT_STICKY
        }
        beginCapture(resultCode, resultData)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ProbeBus.wantsPlayback = false
        shutdown(
            message = "Oscilla was closed.",
            stopProjection = true,
            failed = false,
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        shutdown(message = null, stopProjection = true, failed = false)
        super.onDestroy()
    }

    private fun beginCapture(resultCode: Int, data: Intent) {
        releaseAudio()
        val manager = getSystemService(MediaProjectionManager::class.java)
        val created = try {
            manager.getMediaProjection(resultCode, data)
        } catch (error: RuntimeException) {
            shutdown(
                message = "Could not start playback capture: ${error.message ?: error.javaClass.simpleName}",
                stopProjection = false,
                failed = true,
            )
            return
        }
        if (created == null) {
            shutdown(
                message = "MediaProjection was not granted.",
                stopProjection = false,
                failed = true,
            )
            return
        }
        if (!ProbeBus.wantsPlayback) {
            try {
                created.stop()
            } catch (_: RuntimeException) {
            }
            shutdown(message = null, stopProjection = false, failed = false)
            return
        }
        projection = created
        created.registerCallback(projectionCallback, mainHandler)
        val source = PlaybackCaptureAudioSource(this, created)
        audioSource = source
        source.start(object : AudioSourceCallback {
            override fun onFrame(frame: com.dannyowelch.oscilla.audio.AudioFrame) {
                if (ProbeBus.wantsPlayback) ProbeBus.publishFrame(frame)
            }

            override fun onError(message: String) {
                mainHandler.post {
                    shutdown(message = message, stopProjection = true, failed = true)
                }
            }
        })
        if (!closed.get() && ProbeBus.wantsPlayback) {
            ProbeBus.publish(CaptureEvent.Started)
        }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback capture",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while Oscilla captures device playback audio."
        }
        manager.createNotificationChannel(channel)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Oscilla")
            .setContentText("Capturing playback audio")
            .setSmallIcon(R.drawable.ic_stat_oscilla)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
    }

    private fun shutdown(message: String?, stopProjection: Boolean, failed: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        releaseAudio()
        val active = projection
        projection = null
        if (stopProjection && active != null) {
            try {
                active.stop()
            } catch (_: RuntimeException) {
            }
        }
        if (message != null) {
            ProbeBus.publish(if (failed) CaptureEvent.Failed(message) else CaptureEvent.Ended(message))
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseAudio() {
        val source = audioSource
        audioSource = null
        source?.stop()
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "oscilla_playback_capture"
        private const val NOTIFICATION_ID = 10

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, PlaybackCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackCaptureService::class.java))
        }
    }
}
