package com.openstream.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpenStreamApp { exo -> player = exo } }
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
fun OpenStreamApp(onPlayerCreated: (ExoPlayer?) -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    val exo = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onPlayerCreated(exo)
        onDispose { exo.release(); onPlayerCreated(null) }
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
