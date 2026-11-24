package com.nebula.avatarcall

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack

/**
 * Avatar priority:
 *  1. a remote video track (future MuseTalk / avatar worker) -> rendered as-is
 *  2. idle + talk loop videos (URLs) -> crossfade on agent speaking
 *  3. bundled photo -> breathing + speaking pulse (placeholder until real clips exist)
 */
@Composable
fun AvatarView(
    room: Room?,
    remoteVideo: VideoTrack?,
    idleUrl: String,
    talkUrl: String,
    speaking: Boolean,
    level: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when {
            room != null && remoteVideo != null ->
                LkVideo(room, remoteVideo, mirror = false, modifier = Modifier.fillMaxSize())
            idleUrl.isNotBlank() ->
                LoopAvatar(idleUrl, talkUrl.ifBlank { idleUrl }, speaking, Modifier.fillMaxSize())
            else -> PhotoAvatar(speaking, level, Modifier.fillMaxSize())
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LoopAvatar(idleUrl: String, talkUrl: String, speaking: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val idle = remember(idleUrl) { loopPlayer(ctx, idleUrl) }
    val talk = remember(talkUrl) { loopPlayer(ctx, talkUrl) }
    DisposableEffect(idle, talk) { onDispose { idle.release(); talk.release() } }

    // restart the talk clip from a slightly random offset each turn so it never looks repeated
    LaunchedEffect(speaking) {
        if (speaking && talk.duration > 0) talk.seekTo((0..talk.duration.toInt()).random().toLong())
    }
    val talkAlpha by animateFloatAsState(if (speaking) 1f else 0f, tween(160), label = "talk")

    Box(modifier) {
        PlayerSurface(idle, Modifier.fillMaxSize())
        PlayerSurface(talk, Modifier.fillMaxSize().alpha(talkAlpha))
    }
}

@OptIn(UnstableApi::class)
private fun loopPlayer(ctx: android.content.Context, url: String): ExoPlayer =
    ExoPlayer.Builder(ctx).build().apply {
        setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        repeatMode = Player.REPEAT_MODE_ONE
        volume = 0f
        playWhenReady = true
        prepare()
    }

@OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(player: ExoPlayer, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
    )
}

@Composable
private fun PhotoAvatar(speaking: Boolean, level: Float, modifier: Modifier) {
    val inf = rememberInfiniteTransition(label = "breath")
    val breath by inf.animateFloat(
        1f, 1.012f,
        infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b",
    )
    val sway by inf.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "s",
    )
    val talk by animateFloatAsState(
        if (speaking) 0.006f + (level.coerceIn(0f, 1f) * 0.012f) else 0f,
        tween(90), label = "t",
    )
    Image(
        painter = painterResource(R.drawable.avatar_fallback),
        contentDescription = "avatar",
        contentScale = ContentScale.Crop,
        modifier = modifier.graphicsLayer {
            val s = breath + talk
            scaleX = s; scaleY = s
            translationX = sway * 4f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.3f)
        },
    )
}
