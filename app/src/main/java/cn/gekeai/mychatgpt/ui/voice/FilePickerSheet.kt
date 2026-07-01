package cn.gekeai.mychatgpt.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "添加文件" (Add files) modal sheet: an upload row and a 2-column grid of
 * recent files. Tapping a card toggles its selection; the bottom button commits
 * the chosen files via [onAttach].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerSheet(
    files: List<RecentFile>,
    onDismiss: () -> Unit,
    onUpload: () -> Unit,
    onAttach: (List<RecentFile>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(setOf<String>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            // Header: title centered, close button to the right.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "添加文件",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoiceColors.PrimaryText,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(VoiceColors.ChipBg)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = VoiceColors.IconMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Upload row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUpload)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Upload,
                    contentDescription = null,
                    tint = VoiceColors.PrimaryText,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "上传文件",
                    fontSize = 18.sp,
                    color = VoiceColors.PrimaryText,
                )
            }
            HorizontalDivider(
                color = VoiceColors.Divider,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Text(
                text = "最近",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = VoiceColors.PrimaryText,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 6.dp,
                    bottom = 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(files, key = { it.id }) { file ->
                    FileCard(
                        file = file,
                        selected = file.id in selected,
                        onToggle = {
                            selected = if (file.id in selected) selected - file.id else selected + file.id
                        },
                    )
                }
            }

            // Commit button appears once something is selected.
            if (selected.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(VoiceColors.Dark)
                            .clickable {
                                onAttach(files.filter { it.id in selected })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "附加 ${selected.size} 个项目",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileCard(
    file: RecentFile,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    if (file.loading) {
        SkeletonCard()
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) VoiceColors.PrimaryText else VoiceColors.Divider,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onToggle)
            .padding(16.dp),
    ) {
        Text(
            text = file.name,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = VoiceColors.PrimaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = VoiceColors.FileIcon,
                modifier = Modifier.size(26.dp),
            )
            SelectionIndicator(selected = selected)
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    if (selected) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(VoiceColors.PrimaryText),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选择",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(1.5.dp, VoiceColors.Divider, CircleShape),
        )
    }
}

@Composable
private fun SkeletonCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, VoiceColors.Divider, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(VoiceColors.Skeleton),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(VoiceColors.Skeleton),
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(VoiceColors.Skeleton),
        )
    }
}
