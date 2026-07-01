package cn.gekeai.mychatgpt.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/** A few mock files for the "最近" grid; two real entries then loading skeletons. */
private val MockRecentFiles = listOf(
    RecentFile("f1", "0017_爱肯拿_acana_高蛋白室内猫粮配方说明.md", badge = "MD"),
    RecentFile("f2", "0017_爱肯拿_acana_高蛋白室内猫粮配方说明.md", badge = "MD"),
    RecentFile("f3", "", badge = "", loading = true),
    RecentFile("f4", "", badge = "", loading = true),
)

private const val FirstReply = "I'm ready whenever you are! What's on your mind today?"

@Composable
fun VoiceChatScreen(modifier: Modifier = Modifier) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val attachments = remember { mutableStateListOf<Attachment>() }
    var inputText by remember { mutableStateOf("") }
    var inputMode by remember { mutableStateOf(InputMode.VOICE) }
    var orbState by remember { mutableStateOf(OrbState.IDLE) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    var filePickerOpen by remember { mutableStateOf(false) }
    var idCounter by remember { mutableIntStateOf(0) }
    fun nextId(): String = "id${idCounter++}"

    // Live microphone level — drives the orb diameter while the user is speaking.
    val context = LocalContext.current
    val audio = rememberAudioLevelController()

    // Reply trigger: when set, simulate the assistant "thinking" then speaking.
    var pendingReply by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingReply) {
        val reply = pendingReply ?: return@LaunchedEffect
        orbState = OrbState.SPEAKING
        delay(900)
        messages.add(ChatMessage(nextId(), reply, fromUser = false))
        delay(2500)
        orbState = OrbState.IDLE
        pendingReply = null
    }

    fun send() {
        if (inputText.isBlank() && attachments.isEmpty()) return
        messages.add(
            ChatMessage(
                id = nextId(),
                text = inputText.trim(),
                fromUser = true,
                attachments = attachments.toList(),
            ),
        )
        inputText = ""
        attachments.clear()
        inputMode = InputMode.VOICE
        pendingReply = FirstReply
    }

    fun startListening() {
        orbState = OrbState.LISTENING
        audio.start(context)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening()
    }

    fun onMicTap() {
        if (orbState == OrbState.LISTENING) {
            // Stop capturing and let the assistant "respond".
            audio.stop()
            pendingReply = FirstReply
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoiceColors.Screen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Title bar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "ChatGPT ",
                    color = VoiceColors.TitleStrong,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "声音",
                    color = VoiceColors.TitleMuted,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Normal,
                )
            }

            // Transcript (assistant text rendered plainly, user turns as light bubbles).
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.size(4.dp))
                messages.forEach { msg -> MessageItem(msg) }
            }

            // Orb sits just above the composer.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                GradientOrb(
                    state = orbState,
                    amplitude = audio.amplitude,
                    size = 96.dp,
                )
            }
            Spacer(Modifier.size(20.dp))

            // Composer.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    // Sit above whichever is taller: the nav bar (resting) or the keyboard.
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(bottom = 32.dp),
            ) {
                VoiceInputBar(
                    mode = inputMode,
                    text = inputText,
                    attachments = attachments,
                    onTextChange = { inputText = it },
                    onActivateText = { inputMode = InputMode.TEXT },
                    onPlusClick = { plusMenuOpen = true },
                    onMicClick = { onMicTap() },
                    onCloseClick = {
                        audio.stop()
                        messages.clear()
                        attachments.clear()
                        inputText = ""
                        inputMode = InputMode.VOICE
                        orbState = OrbState.IDLE
                    },
                    onSend = { send() },
                    onRemoveAttachment = { attachments.remove(it) },
                )
            }
        }

        // `+` menu overlay (above content, below nothing — composer stays usable).
        if (plusMenuOpen) {
            PlusMenuOverlay(
                onDismiss = { plusMenuOpen = false },
                onCamera = { plusMenuOpen = false },
                onPhotos = { plusMenuOpen = false },
                onFiles = {
                    plusMenuOpen = false
                    filePickerOpen = true
                },
                onLiveVideo = { plusMenuOpen = false },
                onShareScreen = { plusMenuOpen = false },
                bottomInset = 96.dp,
            )
        }
    }

    // File picker modal.
    if (filePickerOpen) {
        FilePickerSheet(
            files = MockRecentFiles,
            onDismiss = { filePickerOpen = false },
            onUpload = { /* no-op in the replica */ },
            onAttach = { picked ->
                picked.forEach { f ->
                    attachments.add(Attachment(nextId(), f.name, f.badge))
                }
                filePickerOpen = false
                inputMode = InputMode.TEXT
            },
        )
    }
}

@Composable
private fun MessageItem(message: ChatMessage) {
    if (message.fromUser) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            message.attachments.forEach { att ->
                Box(
                    modifier = Modifier
                        .background(
                            VoiceColors.ChipBg,
                            androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "${att.badge} · ${att.name}",
                        color = VoiceColors.PlaceholderText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (message.text.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .background(
                            VoiceColors.ChipBg,
                            androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = message.text,
                        color = VoiceColors.PrimaryText,
                        fontSize = 17.sp,
                    )
                }
            }
        }
    } else {
        Text(
            text = message.text,
            color = VoiceColors.PrimaryText,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
