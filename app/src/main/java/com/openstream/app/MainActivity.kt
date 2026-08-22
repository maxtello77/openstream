package com.openstream.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {

    private var player: ExoPlayer? = null

    fun attachPlayer(p: ExoPlayer?) { player = p }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        setContent { Root() }
    }

    @Deprecated("Deprecated in Java")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }
}

@Composable
fun Root() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashReporter.lastCrash(context)) }

    if (crash != null) {
        CrashScreen(crash!!) {
            CrashReporter.clear(context)
            crash = null
        }
    } else {
        OpenStreamApp()
    }
}

/** Plain-View crash screen: works even if the Compose UI itself is what crashed. */
@Composable
fun CrashScreen(text: String, onClear: () -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.BLACK).verticalScroll(rememberScrollState()),
        factory = { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 96, 48, 48)
                addView(TextView(ctx).apply {
                    setText("The app crashed. Please screenshot this screen and send it to your developer.\n\n$text")
                    setTextColor(Color.rgb(255, 110, 110))
                    textSize = 12f
                    setTextIsSelectable(true)
                })
                addView(Button(ctx).apply {
                    setText("Clear and continue")
                    setOnClickListener { onClear() }
                })
            }
        }
    )
}

@Composable
fun OpenStreamApp() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val activityContext = LocalContext.current

    val exo = remember { ExoPlayer.Builder(activityContext).build() }
    DisposableEffect(Unit) {
        (context as? MainActivity)?.let { it.attachPlayer(exo) }
        onDispose { exo.release() }
    }

    var currentSource by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(StreamHistory.load(context)) }
    var recording by remember { mutableStateOf(RecordService.isRecording) }

    fun play(source: String) {
        currentSource = source
        StreamHistory.add(context, source)
        history = StreamHistory.load(context)
        exo.setMediaItem(MediaItem.fromUri(Uri.parse(source)))
        exo.prepare()
        exo.playWhenReady = true
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        play(uri.toString())
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            RecordService.start(context, data)
            recording = true
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (currentSource != null) {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { useController = true } },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    update = { it.player = exo }
                )
            } else {
                Column(Modifier.padding(16.dp)) {
                    Text("OpenStream", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Play a local video file, or paste a stream URL below.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        OutlinedButton(onClick = {
                            play("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                        }) { Text("Test MP4") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            play("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                        }) { Text("Test HLS") }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("rtmp://, rtsp://, https://…") },
                    singleLine = true
                )
                IconButton(onClick = { if (url.isNotBlank()) play(url.trim()) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                IconButton(onClick = { filePicker.launch(arrayOf("video/*")) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Pick file")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    IconButton(onClick = {
                        activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                    }) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture in picture")
                    }
                }
                IconButton(onClick = {
                    if (recording) {
                        RecordService.stop(context); recording = false
                    } else {
                        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                as MediaProjectionManager
                        projectionLauncher.launch(mgr.createScreenCaptureIntent())
                    }
                }) {
                    Icon(
                        if (recording) Icons.Default.Stop else Icons.Default.Videocam,
                        contentDescription = if (recording) "Stop recording" else "Record screen",
                        tint = if (recording) MaterialTheme.colorScheme.error else LocalContentColor.current
                    )
                }
            }

            if (history.isNotEmpty()) {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyColumn {
                    items(history) { source ->
                        ListItem(
                            headlineContent = { Text(source, maxLines = 1) },
                            modifier = Modifier.clickable { play(source) }
                        )
                    }
                }
            }
        }
    }
}
