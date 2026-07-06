package cn.gekeai.mychatgpt.ui.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Reads assistant replies aloud with the platform [TextToSpeech] engine. The engine
 * initializes asynchronously; anything requested via [speak] before it's ready is held
 * in [pending] and flushed once initialization completes.
 */
class SpeechController(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    private val _isSpeaking = mutableStateOf(false)

    /** True from the moment a [speak] call is accepted until the engine finishes it. */
    val isSpeaking: Boolean get() = _isSpeaking.value

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINA
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
                ready = true
                pending?.let { text ->
                    pending = null
                    speak(text)
                }
            }
        }
    }

    /** Speaks [text], interrupting whatever is currently being read. */
    fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        // Mark speaking eagerly so callers can await completion even before the
        // engine's onStart callback fires (or while it's still initializing).
        _isSpeaking.value = true
        if (engine == null || !ready) {
            pending = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ai-reply")
    }

    /** Stops any in-progress speech. */
    fun stop() {
        pending = null
        _isSpeaking.value = false
        tts?.stop()
    }

    fun dispose() {
        _isSpeaking.value = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

@Composable
fun rememberSpeechController(): SpeechController {
    val context = LocalContext.current
    val controller = remember { SpeechController(context) }
    DisposableEffect(Unit) {
        onDispose { controller.dispose() }
    }
    return controller
}
