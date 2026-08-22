package com.openstream.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File

/**
 * Records the device screen (the player, or anything else) to
 * Movies/OpenStream using MediaProjection + MediaRecorder.
 * The projection result intent is handed over via [start] before the service starts.
 */
class RecordService : Service() {

    companion object {
        @Volatile var projectionData: Intent? = null
        @Volatile var isRecording = false

        fun start(context: Context, data: Intent) {
            projectionData = data
            context.startService(Intent(context, RecordService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordService::class.java).putExtra("stop", true))
        }
    }

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("stop", false) == true) {
            stopRecording()
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        val data = projectionData ?: run { stopSelf(); return START_NOT_STICKY }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(android.app.Activity.RESULT_OK, data).also { p ->
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = stopRecording()
            }, null)
        }

        startRecording()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("record", "Screen recording", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = Notification.Builder(this, "record")
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Recording screen…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startRecording() {
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels.let { if (it % 2 == 1) it - 1 else it }
        val height = metrics.heightPixels.let { if (it % 2 == 1) it - 1 else it }

        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "OpenStream")
        dir.mkdirs()
        val out = File(dir, "rec_${System.currentTimeMillis()}.mp4")

        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
        recorder = r
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        r.setVideoSize(width, height)
        r.setVideoFrameRate(30)
        r.setVideoEncodingBitRate(8_000_000)
        r.setOutputFile(out.absolutePath)
        r.prepare()

        virtualDisplay = projection?.createVirtualDisplay(
            "OpenStreamRecord", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null
        )
        r.start()
        isRecording = true
    }

    private fun stopRecording() {
        isRecording = false
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
