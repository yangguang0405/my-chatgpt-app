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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cn.gekeai.mychatgpt.R
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
    var micMuted by remember { mutableStateOf(false) }
    var idCounter by remember { mutableIntStateOf(0) }
    fun nextId(): String = "id${idCounter++}"

    // Live microphone level — drives the orb diameter while the user is speaking.
    val context = LocalContext.current
    val audio = rememberAudioLevelController()
    val speech = rememberSpeechController()

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

    // Poll the scripted-conversation endpoint. Each turn shows the human prompt at
    // once, pauses briefly, then streams the AI reply in.
    LaunchedEffect(Unit) {
        while (true) {
            val turn = ChatScriptApi.fetchNext()
            if (turn == null) {
                // 404 / no message queued — wait a beat and poll again.
                delay(3000)
                continue
            }

            // Only show the current turn — drop everything from the previous one.
            messages.clear()

            if (turn.human.isNotEmpty()) {
                // Simulate speech recognition: show "正在转录" first, then the text.
                val humanId = nextId()
                messages.add(ChatMessage(humanId, "正在转录…", fromUser = true, transcribing = true))
                delay(2000)
                val hi = messages.indexOfFirst { it.id == humanId }
                if (hi >= 0) {
                    messages[hi] = ChatMessage(humanId, turn.human, fromUser = true)
                }
            }

            // Pause 1–2s before the assistant starts replying.
            delay(1500)

            if (turn.ai.isNotEmpty()) {
                // Simulate the assistant thinking: show "正在思考" first, then reply.
                val aiId = nextId()
                messages.add(ChatMessage(aiId, "正在思考…", fromUser = false, transcribing = true))
                delay(2000)

                orbState = OrbState.SPEAKING
                // Read the reply aloud while it streams in.
                speech.speak(turn.ai)
                // Stream the reply one character at a time.
                val builder = StringBuilder()
                turn.ai.forEach { ch ->
                    builder.append(ch)
                    val index = messages.indexOfFirst { it.id == aiId }
                    if (index >= 0) {
                        messages[index] = ChatMessage(aiId, builder.toString(), fromUser = false)
                    }
                    delay(35)
                }

                // Keep the orb in SPEAKING until the read-aloud actually finishes,
                // then drop to IDLE and reveal the action row.
                while (speech.isSpeaking) {
                    delay(100)
                }
                orbState = OrbState.IDLE
                val index = messages.indexOfFirst { it.id == aiId }
                if (index >= 0) {
                    messages[index] = messages[index].copy(showActions = true)
                }
            }

            delay(1500)
        }
    }

    fun send() {
        if (inputText.isBlank() && attachments.isEmpty()) return
        // Only show the current turn — drop the previous conversation.
        messages.clear()
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
        if (micMuted) {
            // Un-mute: resume capturing (re-checking the permission).
            micMuted = false
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // Mute: stop capturing and show the red mic-off icon.
            micMuted = true
            audio.stop()
        }
    }

    // Start capturing as soon as the screen opens so the orb reacts to the mic
    // right away (requesting the permission the first time if it isn't granted).
    LaunchedEffect(Unit) {
        if (!micMuted && !audio.isRunning) {
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
            .background(VoiceColors.ScreenBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Title bar — Drawer icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.drawer),
                        contentDescription = "ChatGPT",
                        tint = VoiceColors.TitleStrong,
                        modifier = Modifier.size(28.dp),
                    )
                }
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
                        speech.stop()
                        messages.clear()
                        attachments.clear()
                        inputText = ""
                        inputMode = InputMode.VOICE
                        orbState = OrbState.IDLE
                        micMuted = false
                    },
                    onSend = { send() },
                    onRemoveAttachment = { attachments.remove(it) },
                    micMuted = micMuted,
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
                        color = VoiceColors.PlaceholderText,
                        fontSize = 17.sp,
                        fontStyle = if (message.transcribing) FontStyle.Italic else FontStyle.Normal,
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message.text,
                color = if (message.transcribing) VoiceColors.PlaceholderText else VoiceColors.PrimaryText,
                fontSize = 17.sp,
                lineHeight = 30.sp,
                fontStyle = if (message.transcribing) FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.fillMaxWidth(),
            )
            if (message.showActions) {
                MessageActions(text = message.text)
            }
        }
    }
}

/** The copy / like / dislike / share row shown beneath a finished assistant reply. */
@Composable
private fun MessageActions(text: String) {
    val clipboard = LocalClipboardManager.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(Icons.Outlined.ContentCopy, "复制") {
            clipboard.setText(AnnotatedString(text))
        }
        ActionButton(Icons.Outlined.ThumbUp, "赞") {}
        ActionButton(Icons.Outlined.ThumbDown, "踩") {}
        ActionButton(Icons.Outlined.IosShare, "分享") {}
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = VoiceColors.TitleMuted,
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    )
}
