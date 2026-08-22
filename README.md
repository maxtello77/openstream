# OpenStream

A clean-room alternative to camera-spoofing apps: an Android video player and
streaming client built on Jetpack Compose + Media3 (ExoPlayer).

## Features

- **Play anything ExoPlayer supports**: local files, progressive HTTP/HTTPS MP4,
  HLS (`.m3u8`), DASH, **RTSP** and **RTMP** live streams
  (rtmp/rtsp support via `media3-exoplayer-rtmp` / `-rtsp`).
- **File picker** with persisted URI permission for local videos.
- **Playback history** (last 20 sources, stored in SharedPreferences).
- **Picture-in-picture**: manual button + automatic PiP when you leave the app
  while something is playing.
- **Screen recording**: records the display to `Movies/OpenStream/` via
  MediaProjection + MediaRecorder (H.264, 8 Mbps, 30 fps), run as a
  `mediaProjection`-type foreground service.

## Build

1. Open the `OpenStream` folder in Android Studio (Hedgehog or newer).
2. Let it sync; press Run. Requires JDK 17 (bundled with Android Studio).

Or from the CLI: `gradlew installDebug` (generate a wrapper first with
`gradle wrapper` if you don't have one).

## Project layout

```
app/src/main/java/com/openstream/app/
  MainActivity.kt     Compose UI: player, URL entry, file picker, PiP, record toggle
  RecordService.kt    MediaProjection screen recorder (foreground service)
  StreamHistory.kt    Recent-sources persistence
```

## Notes

- Recorded files land in `Movies/OpenStream/` (public storage, no permission
  needed on API 29+ for the app's own MediaRecorder output via
  `getExternalStoragePublicDirectory`; on API 24–28 this also works for
  the primary external volume).
- To re-stream (push) video to an RTMP server instead of just playing, the next
  step would be integrating a muxer such as Media3's `Transformer` or a librtmp
  wrapper — playback and recording here are the foundation.
