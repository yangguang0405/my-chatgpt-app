package cn.gekeai.mychatgpt.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class PlusAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The menu shown when tapping `+`: 相机 / 照片 / 文件 / 直播视频 / 共享屏幕.
 * Rendered as a bottom-left anchored card over a dismiss scrim; the composer
 * controls remain interactive on top of this overlay.
 */
@Composable
fun PlusMenuOverlay(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onLiveVideo: () -> Unit,
    onShareScreen: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        PlusAction(Icons.Outlined.PhotoCamera, "相机", onCamera),
        PlusAction(Icons.Outlined.Image, "照片", onPhotos),
        PlusAction(Icons.Outlined.AttachFile, "文件", onFiles),
        PlusAction(Icons.Outlined.Videocam, "直播视频", onLiveVideo),
        PlusAction(Icons.AutoMirrored.Outlined.ScreenShare, "共享屏幕", onShareScreen),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            // Transparent, tap-to-dismiss scrim (the reference barely dims).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomStart,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White,
            shadowElevation = 12.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(start = 16.dp, end = 80.dp, bottom = bottomInset + 8.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                actions.forEach { action ->
                    PlusMenuRow(action = action)
                }
            }
        }
    }
}

@Composable
private fun PlusMenuRow(action: PlusAction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = action.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(VoiceColors.ChipBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = VoiceColors.PrimaryText,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = action.label,
            color = VoiceColors.PrimaryText,
            fontSize = 18.sp,
        )
    }
}
