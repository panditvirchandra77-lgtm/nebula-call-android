package com.nebula.avatarcall

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var controller: CallController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller = CallController(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF4FD1C5))) {
                App(controller)
            }
        }
    }

    override fun onDestroy() {
        controller.dispose()
        super.onDestroy()
    }
}

private object Prefs {
    private const val NAME = "nebula_call"
    fun load(c: Context): CallConfig {
        val p = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return CallConfig(
            serverUrl = p.getString("server", "wss://nebula-claw.tailed4b62.ts.net")!!,
            tokenUrl = p.getString("tokenUrl", "")!!,
            manualToken = p.getString("manualToken", "")!!,
            roomName = p.getString("room", "nebula")!!,
            identity = p.getString("identity", "jeetu-phone")!!,
            idleVideoUrl = p.getString("idle", "")!!,
            talkVideoUrl = p.getString("talk", "")!!,
        )
    }
    fun save(c: Context, cfg: CallConfig) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("server", cfg.serverUrl).putString("tokenUrl", cfg.tokenUrl)
            .putString("manualToken", cfg.manualToken).putString("room", cfg.roomName)
            .putString("identity", cfg.identity).putString("idle", cfg.idleVideoUrl)
            .putString("talk", cfg.talkVideoUrl).apply()
    }
}

@Composable
private fun App(controller: CallController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cfg by remember { mutableStateOf(Prefs.load(ctx)) }
    val status by controller.status.collectAsState()
    var permError by rememberSaveable { mutableStateOf<String?>(null) }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) {
            permError = null
            controller.start(cfg)
        } else permError = "Mic permission chahiye"
    }

    val inCall = status in setOf(CallStatus.FETCHING_TOKEN, CallStatus.CONNECTING, CallStatus.WAITING_AGENT, CallStatus.LIVE)
    if (inCall) {
        CallScreen(controller, cfg)
    } else {
        SetupScreen(
            cfg = cfg,
            status = status,
            error = permError ?: controller.error.collectAsState().value,
            onChange = { cfg = it },
            onCall = {
                Prefs.save(ctx, cfg)
                perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
            },
        )
    }
}

@Composable
private fun SetupScreen(
    cfg: CallConfig,
    status: CallStatus,
    error: String?,
    onChange: (CallConfig) -> Unit,
    onCall: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B1020)).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Nebula Call", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("LiveKit video call with Sonia", color = Color(0xFF9AA4BF))
        Spacer(Modifier.height(6.dp))
        Field("LiveKit URL (wss:// ya ws://)", cfg.serverUrl) { onChange(cfg.copy(serverUrl = it)) }
        Field("Token URL (GET ?room=&identity=)", cfg.tokenUrl) { onChange(cfg.copy(tokenUrl = it)) }
        Field("Ya manual token (optional)", cfg.manualToken) { onChange(cfg.copy(manualToken = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("Room", cfg.roomName) { onChange(cfg.copy(roomName = it)) } }
            Box(Modifier.weight(1f)) { Field("Identity", cfg.identity) { onChange(cfg.copy(identity = it)) } }
        }
        Field("Avatar idle loop .mp4 URL (optional)", cfg.idleVideoUrl) { onChange(cfg.copy(idleVideoUrl = it)) }
        Field("Avatar talk loop .mp4 URL (optional)", cfg.talkVideoUrl) { onChange(cfg.copy(talkVideoUrl = it)) }
        if (status == CallStatus.ENDED) Text("Call khatam.", color = Color(0xFF9AA4BF))
        error?.let { Text("Error: $it", color = Color(0xFFFF7A7A)) }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onCall,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
        ) { Text("Video call shuru karo", fontSize = 17.sp) }
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CallScreen(c: CallController, cfg: CallConfig) {
    val status by c.status.collectAsState()
    val speaking by c.agentSpeaking.collectAsState()
    val level by c.agentLevel.collectAsState()
    val remoteVideo by c.remoteVideo.collectAsState()
    val localVideo by c.localVideo.collectAsState()
    val micOn by c.micOn.collectAsState()
    val camOn by c.camOn.collectAsState()
    val room = c.currentRoom

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AvatarView(
            room = room, remoteVideo = remoteVideo,
            idleUrl = cfg.idleVideoUrl, talkUrl = cfg.talkVideoUrl,
            speaking = speaking, level = level,
            modifier = Modifier.fillMaxSize(),
        )
        // top gradient + status
        Box(
            Modifier.fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
        )
        Column(Modifier.statusBarsPadding().padding(18.dp)) {
            Text("Sonia", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when (status) {
                    CallStatus.FETCHING_TOKEN -> "Token le raha hoon…"
                    CallStatus.CONNECTING -> "Connect ho raha hai…"
                    CallStatus.WAITING_AGENT -> "Sonia ka intezaar…"
                    CallStatus.LIVE -> if (speaking) "bol rahi hai" else "sun rahi hai"
                    else -> ""
                },
                color = Color(0xFFCFE9E6), fontSize = 14.sp,
            )
        }
        // local camera PiP
        if (room != null && localVideo != null && camOn) {
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp)
                    .size(width = 104.dp, height = 185.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(14.dp))
            ) { LkVideo(room, localVideo!!, mirror = true, modifier = Modifier.fillMaxSize()) }
        }
        // controls
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundBtn(if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff, Color(0x33FFFFFF)) { c.toggleMic() }
            RoundBtn(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, Color(0x33FFFFFF)) { c.toggleCam() }
            RoundBtn(Icons.Filled.Cameraswitch, Color(0x33FFFFFF)) { c.flipCamera() }
            RoundBtn(Icons.Filled.CallEnd, Color(0xFFE53935), big = true) { c.hangUp() }
        }
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, bg: Color, big: Boolean = false, onClick: () -> Unit) {
    val s = if (big) 68.dp else 56.dp
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(s).clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(containerColor = bg, contentColor = Color.White),
    ) { Icon(icon, contentDescription = null, modifier = Modifier.width(if (big) 30.dp else 24.dp)) }
}
