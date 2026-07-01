package cn.gekeai.mychatgpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import cn.gekeai.mychatgpt.ui.theme.MyChatGPTTheme
import cn.gekeai.mychatgpt.ui.voice.VoiceChatScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyChatGPTTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoiceChatScreen()
                }
            }
        }
    }
}
