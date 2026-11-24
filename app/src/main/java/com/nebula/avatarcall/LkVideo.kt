package com.nebula.avatarcall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

/** Renders a LiveKit VideoTrack, aspect-fill (no letterbox in 9:16). */
@Composable
fun LkVideo(room: Room, track: VideoTrack, mirror: Boolean, modifier: Modifier = Modifier) {
    val holder = remember { arrayOfNulls<TextureViewRenderer>(1) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureViewRenderer(ctx).also { v ->
                room.initVideoRenderer(v)
                v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                v.setEnableHardwareScaler(true)
                holder[0] = v
            }
        },
        update = { v -> v.setMirror(mirror) },
    )
    DisposableEffect(track) {
        val v = holder[0]
        v?.let { track.addRenderer(it) }
        onDispose { v?.let { track.removeRenderer(it) } }
    }
}
