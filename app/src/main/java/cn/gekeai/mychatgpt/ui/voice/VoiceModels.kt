package cn.gekeai.mychatgpt.ui.voice

import androidx.compose.ui.graphics.Color

/** A file/image attached to the composer, shown as a chip above the input. */
data class Attachment(
    val id: String,
    val name: String,
    /** Short type badge shown in the chip, e.g. "MD", "PDF", "IMG". */
    val badge: String,
)

/** An entry in the "最近" (Recent) grid of the file picker sheet. */
data class RecentFile(
    val id: String,
    val name: String,
    val badge: String,
    /** Placeholder cards render as a shimmering skeleton instead of real content. */
    val loading: Boolean = false,
)

/** A turn in the conversation transcript. */
data class ChatMessage(
    val id: String,
    val text: String,
    val fromUser: Boolean,
    val attachments: List<Attachment> = emptyList(),
    /** When true, an assistant turn shows the copy / like / dislike / share row. */
    val showActions: Boolean = false,
)

/** Composer mode: resting voice controls vs. an active text field with keyboard. */
enum class InputMode { VOICE, TEXT }

/** Visual state driving the orb animation. */
enum class OrbState { IDLE, LISTENING, SPEAKING }

/** Fixed palette matching the ChatGPT reference screenshots. */
object VoiceColors {
    val Screen = Color(0xFFFFFFFF)
    /** Page background — a light gray-white that sets off the white surfaces on top of it. */
    val ScreenBg = Color(0xFFF4F4F5)
    val TitleStrong = Color(0xFF0D0D0D)
    val TitleMuted = Color(0xFF9A9AA0)
    val PlaceholderText = Color(0xFF8E8E93)
    val PrimaryText = Color(0xFF0D0D0D)
    val Dark = Color(0xFF1E1E1E)
    val CircleBg = Color(0xFFFFFFFF)
    val CircleBorder = Color(0xFFE6E6EA)
    val IconMuted = Color(0xFF3C3C43)
    val ChipBg = Color(0xFFF1F1F3)
    val Divider = Color(0xFFE6E6EA)
    val FileIcon = Color(0xFF2F7BF6)
    val Skeleton = Color(0xFFE9E9EC)
    val SheetScrim = Color(0x66000000)
    /** Muted-mic icon tint. */
    val MicMuted = Color(0xFFE5484D)
}
