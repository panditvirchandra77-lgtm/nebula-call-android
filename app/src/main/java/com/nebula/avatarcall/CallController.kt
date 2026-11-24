package com.nebula.avatarcall

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoCaptureParameter
import io.livekit.android.room.track.VideoEncoding
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CallConfig(
    val serverUrl: String,
    val tokenUrl: String,
    val manualToken: String,
    val roomName: String,
    val identity: String,
    val idleVideoUrl: String,
    val talkVideoUrl: String,
)

enum class CallStatus { IDLE, FETCHING_TOKEN, CONNECTING, WAITING_AGENT, LIVE, ENDED, ERROR }

/**
 * Owns the LiveKit Room. Phone joins as a normal participant: publishes mic + camera (9:16, 480x854),
 * subscribes to the agent's audio (and its video, if an avatar worker ever publishes one).
 */
class CallController(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null
    private var pollJob: Job? = null

    val status = MutableStateFlow(CallStatus.IDLE)
    val error = MutableStateFlow<String?>(null)
    val agentSpeaking = MutableStateFlow(false)
    val agentLevel = MutableStateFlow(0f)
    val agentName = MutableStateFlow<String?>(null)
    val remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo = MutableStateFlow<VideoTrack?>(null)
    val micOn = MutableStateFlow(true)
    val camOn = MutableStateFlow(true)

    val currentRoom: Room? get() = room

    fun start(cfg: CallConfig) {
        if (room != null) return
        error.value = null
        scope.launch {
            try {
                status.value = CallStatus.FETCHING_TOKEN
                val token = if (cfg.manualToken.isNotBlank()) cfg.manualToken.trim()
                else fetchToken(cfg.tokenUrl, cfg.roomName, cfg.identity)

                status.value = CallStatus.CONNECTING
                val r = LiveKit.create(
                    appContext,
                    RoomOptions(
                        adaptiveStream = true,
                        dynacast = true,
                        videoTrackCaptureDefaults = LocalVideoTrackOptions(
                            position = CameraPosition.FRONT,
                            // portrait 9:16, small: the agent only samples ~1 fps
                            captureParams = VideoCaptureParameter(480, 854, 24),
                        ),
                        videoTrackPublishDefaults = VideoTrackPublishDefaults(
                            videoEncoding = VideoEncoding(maxBitrate = 600_000, maxFps = 24),
                            simulcast = false,
                        ),
                    ),
                )
                room = r
                launch { r.events.collect { onEvent(it) } }

                r.connect(cfg.serverUrl.trim(), token)
                r.localParticipant.setMicrophoneEnabled(true)
                r.localParticipant.setCameraEnabled(true)
                refreshLocalVideo()
                refreshRemote()
                status.value = if (agentName.value == null) CallStatus.WAITING_AGENT else CallStatus.LIVE
                startPolling()
            } catch (t: Throwable) {
                error.value = t.message ?: t.toString()
                status.value = CallStatus.ERROR
                cleanup()
            }
        }
    }

    private fun onEvent(e: RoomEvent) {
        when (e) {
            is RoomEvent.ParticipantConnected,
            is RoomEvent.ParticipantDisconnected,
            is RoomEvent.TrackSubscribed,
            is RoomEvent.TrackUnsubscribed -> refreshRemote()
            is RoomEvent.TrackPublished,
            is RoomEvent.TrackUnpublished -> refreshLocalVideo()
            is RoomEvent.Disconnected -> {
                if (status.value != CallStatus.ENDED) {
                    status.value = CallStatus.ENDED
                    e.error?.let { error.value = it.message }
                }
            }
            is RoomEvent.FailedToConnect -> {
                error.value = e.error.message
                status.value = CallStatus.ERROR
            }
            else -> Unit
        }
    }

    private fun pickAgent(): Participant? {
        val r = room ?: return null
        val all = r.remoteParticipants.values
        return all.firstOrNull { it.kind == Participant.Kind.AGENT } ?: all.firstOrNull()
    }

    private fun refreshRemote() {
        val agent = pickAgent()
        agentName.value = agent?.identity?.value
        if (agent != null && (status.value == CallStatus.WAITING_AGENT)) status.value = CallStatus.LIVE
        if (agent == null && status.value == CallStatus.LIVE) status.value = CallStatus.WAITING_AGENT
        // An avatar worker (MuseTalk etc.) may publish video as any remote participant.
        val r = room ?: return
        val vt = r.remoteParticipants.values.asSequence()
            .flatMap { p -> p.videoTrackPublications.asSequence().map { it.second } }
            .filterIsInstance<VideoTrack>()
            .firstOrNull()
        remoteVideo.value = vt
    }

    private fun refreshLocalVideo() {
        val r = room ?: return
        localVideo.value = r.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
    }

    /** Speaking detection with a short hang-over so the talk loop doesn't flicker between words. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var lastLoud = 0L
            while (isActive) {
                val agent = pickAgent()
                val lvl = agent?.audioLevel ?: 0f
                val now = System.currentTimeMillis()
                if (agent?.isSpeaking == true || lvl > 0.02f) lastLoud = now
                agentLevel.value = lvl
                agentSpeaking.value = now - lastLoud < 450
                delay(60)
            }
        }
    }

    fun toggleMic() {
        val r = room ?: return
        scope.launch {
            val next = !micOn.value
            r.localParticipant.setMicrophoneEnabled(next)
            micOn.value = next
        }
    }

    fun toggleCam() {
        val r = room ?: return
        scope.launch {
            val next = !camOn.value
            r.localParticipant.setCameraEnabled(next)
            camOn.value = next
            refreshLocalVideo()
        }
    }

    fun flipCamera() {
        (localVideo.value as? LocalVideoTrack)?.let { t ->
            val pos = t.options.position
            t.switchCamera(position = if (pos == CameraPosition.BACK) CameraPosition.FRONT else CameraPosition.BACK)
        }
    }

    fun hangUp() {
        status.value = CallStatus.ENDED
        cleanup()
    }

    private fun cleanup() {
        pollJob?.cancel()
        pollJob = null
        room?.disconnect()
        room?.release()
        room = null
        remoteVideo.value = null
        localVideo.value = null
        agentSpeaking.value = false
        agentName.value = null
        micOn.value = true
        camOn.value = true
    }

    fun dispose() {
        cleanup()
        scope.cancel()
    }

    private suspend fun fetchToken(tokenUrl: String, roomName: String, identity: String): String =
        withContext(Dispatchers.IO) {
            require(tokenUrl.isNotBlank()) { "Token URL ya manual token, ek dena zaroori hai" }
            val url = tokenUrl.trim().toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("room", roomName)
                ?.addQueryParameter("identity", identity)
                ?.build() ?: error("Token URL galat hai: $tokenUrl")
            val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Token server ${resp.code}: ${body.take(200)}")
                val t = body.trim()
                if (t.startsWith("{")) {
                    val j = JSONObject(t)
                    listOf("token", "accessToken", "participantToken", "participant_token")
                        .firstNotNullOfOrNull { k -> j.optString(k).takeIf { it.isNotBlank() } }
                        ?: error("Token JSON mein 'token' field nahi mila")
                } else t.trim('"')
            }
        }
}
