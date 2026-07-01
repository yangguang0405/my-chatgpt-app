package cn.gekeai.mychatgpt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ChatGPT voice replica uses a fixed light scheme (white surfaces, black accents),
// independent of the system theme / dynamic color, to match the reference 1:1.
private val ChatGptLightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0D0D0D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFF2F2F5),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFE5E5EA),
)

@Composable
fun MyChatGPTTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ChatGptLightColorScheme,
        typography = Typography,
        content = content
    )
}