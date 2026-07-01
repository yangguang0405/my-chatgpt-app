package cn.gekeai.mychatgpt.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Round icon button used for +, mic, send and close. */
@Composable
private fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    background: Color = VoiceColors.CircleBg,
    borderColor: Color? = VoiceColors.CircleBorder,
    iconTint: Color = VoiceColors.IconMuted,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** The file/image chip shown above the text field once something is attached. */
@Composable
fun AttachmentChip(
    attachment: Attachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(VoiceColors.ChipBg)
            .padding(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = attachment.badge,
                    color = VoiceColors.PlaceholderText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "移除附件",
                        tint = VoiceColors.IconMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = attachment.name,
                color = VoiceColors.PrimaryText,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The composer. In [InputMode.VOICE] it shows `+  [询问 ChatGPT]  mic  ✕`.
 * In [InputMode.TEXT] it becomes a single rounded container with the attachment
 * chips, the `+`, an editable field and the send `↑`.
 */
@Composable
fun VoiceInputBar(
    mode: InputMode,
    text: String,
    attachments: List<Attachment>,
    onTextChange: (String) -> Unit,
    onActivateText: () -> Unit,
    onPlusClick: () -> Unit,
    onMicClick: () -> Unit,
    onCloseClick: () -> Unit,
    onSend: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        InputMode.VOICE -> VoiceModeBar(
            attachments = attachments,
            onActivateText = onActivateText,
            onPlusClick = onPlusClick,
            onMicClick = onMicClick,
            onCloseClick = onCloseClick,
            onRemoveAttachment = onRemoveAttachment,
            modifier = modifier,
        )

        InputMode.TEXT -> TextModeBar(
            text = text,
            attachments = attachments,
            onTextChange = onTextChange,
            onPlusClick = onPlusClick,
            onSend = onSend,
            onRemoveAttachment = onRemoveAttachment,
            modifier = modifier,
        )
    }
}

@Composable
private fun VoiceModeBar(
    attachments: List<Attachment>,
    onActivateText: () -> Unit,
    onPlusClick: () -> Unit,
    onMicClick: () -> Unit,
    onCloseClick: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                attachments.forEach { att ->
                    AttachmentChip(attachment = att, onRemove = { onRemoveAttachment(att) })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircleButton(
                icon = Icons.Filled.Add,
                contentDescription = "添加",
                onClick = onPlusClick,
                diameter = 40.dp,
                iconSize = 22.dp,
            )
            // Tappable placeholder pill that activates the keyboard.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, VoiceColors.CircleBorder, CircleShape)
                    .clickable(onClick = onActivateText)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "询问 ChatGPT",
                    color = VoiceColors.PlaceholderText,
                    fontSize = 16.sp,
                )
            }
            CircleButton(
                icon = Icons.Outlined.Mic,
                contentDescription = "语音输入",
                onClick = onMicClick,
                diameter = 40.dp,
                iconSize = 22.dp,
            )
            CircleButton(
                icon = Icons.Filled.Close,
                contentDescription = "结束语音对话",
                onClick = onCloseClick,
                diameter = 40.dp,
                iconSize = 22.dp,
                background = VoiceColors.Dark,
                borderColor = null,
                iconTint = Color.White,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TextModeBar(
    text: String,
    attachments: List<Attachment>,
    onTextChange: (String) -> Unit,
    onPlusClick: () -> Unit,
    onSend: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        bringIntoView.bringIntoView()
    }
    val canSend = text.isNotBlank() || attachments.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, VoiceColors.CircleBorder, RoundedCornerShape(26.dp))
            .background(VoiceColors.Screen)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                attachments.forEach { att ->
                    AttachmentChip(attachment = att, onRemove = { onRemoveAttachment(att) })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircleButton(
                icon = Icons.Filled.Add,
                contentDescription = "添加",
                onClick = onPlusClick,
                diameter = 40.dp,
                iconSize = 22.dp,
            )
            Box(modifier = Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        text = "询问 ChatGPT",
                        color = VoiceColors.PlaceholderText,
                        fontSize = 17.sp,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(color = VoiceColors.PrimaryText, fontSize = 17.sp),
                    ),
                    singleLine = false,
                    cursorBrush = SolidColor(VoiceColors.FileIcon),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { if (canSend) onSend() },
                    ),
                )
            }
            CircleButton(
                icon = Icons.Filled.ArrowUpward,
                contentDescription = "发送",
                onClick = { if (canSend) onSend() },
                diameter = 40.dp,
                iconSize = 22.dp,
                background = if (canSend) VoiceColors.Dark else VoiceColors.ChipBg,
                borderColor = null,
                iconTint = if (canSend) Color.White else VoiceColors.PlaceholderText,
                enabled = canSend,
            )
        }
    }
}
