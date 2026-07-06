package cn.gekeai.mychatgpt.ui.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Reads assistant replies aloud with Volcengine Doubao TTS 2.0. Synthesis happens over
 * HTTP: text is sent to the cloud, the returned base64 audio is written to a cache file,
 * and played back with [MediaPlayer]. Because synthesis is asynchronous, [isSpeaking] is
 * set eagerly the moment a [speak] call is accepted and stays true until playback ends,
 * so callers can await completion regardless of network latency.
 */
class SpeechController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var player: MediaPlayer? = null
    private var currentJob: Job? = null

    private val _isSpeaking = mutableStateOf(false)

    /** True from the moment a [speak] call is accepted until playback finishes. */
    val isSpeaking: Boolean get() = _isSpeaking.value

    /** Speaks [text], interrupting whatever is currently being read. */
    fun speak(text: String) {
        if (text.isBlank()) return
        // Mark speaking eagerly so callers can await completion even before the audio
        // has been synthesized and playback has started.
        _isSpeaking.value = true
        // QUEUE_FLUSH semantics: drop any in-flight synthesis or playback first.
        currentJob?.cancel()
        releasePlayer()
        currentJob = scope.launch {
            val audio = DoubaoTtsApi.synthesize(text)
            if (audio == null) {
                _isSpeaking.value = false
                return@launch
            }
            playAudio(audio)
        }
    }

    private suspend fun playAudio(mp3: ByteArray) {
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("doubao-tts", ".mp3", appContext.cacheDir).apply {
                writeBytes(mp3)
                deleteOnExit()
            }
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isSpeaking.value = false
                    releasePlayer()
                    file.delete()
                }
                setOnErrorListener { _, _, _ ->
                    _isSpeaking.value = false
                    releasePlayer()
                    file.delete()
                    true
                }
                // Local cache file, so a synchronous prepare returns quickly.
                prepare()
                start()
            }
        } catch (_: Exception) {
            _isSpeaking.value = false
            releasePlayer()
            file.delete()
        }
    }

    private fun releasePlayer() {
        player?.let { mp ->
            try {
                mp.stop()
            } catch (_: Exception) {
                // Player may not be in a stoppable state; releasing is enough.
            }
            mp.release()
        }
        player = null
    }

    /** Stops any in-progress synthesis or playback. */
    fun stop() {
        currentJob?.cancel()
        currentJob = null
        _isSpeaking.value = false
        releasePlayer()
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}

/**
 * Client for Volcengine Doubao TTS 2.0 (seed-tts-2.0), unidirectional streaming HTTP
 * endpoint. The server replies with newline-delimited JSON: each line carries a `code`
 * and a base64 `data` chunk. Audio chunks are concatenated until the terminal code
 * (20000000) arrives; any positive code is an error. Returns the full MP3 bytes, or
 * `null` on any failure so the caller can degrade gracefully.
 */
private object DoubaoTtsApi {
    private const val ENDPOINT = "https://openspeech.bytedance.com/api/v3/tts/unidirectional"
    private const val RESOURCE_ID = "seed-tts-2.0"

    // TODO: 填入火山引擎控制台的 API Key。
    private const val API_KEY = "4292d0c0-e9cb-4945-af1d-dabf98a46a2c"

    // 大模型男声音色。按需替换成控制台里选定的 speaker ID。
    private const val SPEAKER = "zh_male_m191_uranus_bigtts"

    private const val SAMPLE_RATE = 24_000

    // 流结束标记 code。
    private const val CODE_FINISHED = 20_000_000

    suspend fun synthesize(text: String): ByteArray? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val payload = JSONObject().apply {
                put("req_params", JSONObject().apply {
                    put("text", text)
                    put("speaker", SPEAKER)
                    put("audio_params", JSONObject().apply {
                        put("format", "mp3")
                        put("sample_rate", SAMPLE_RATE)
                    })
                })
            }

            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Api-Key", API_KEY)
                setRequestProperty("X-Api-Resource-Id", RESOURCE_ID)
            }

            connection.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val audio = java.io.ByteArrayOutputStream()
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    val json = try {
                        JSONObject(line)
                    } catch (_: Exception) {
                        continue
                    }
                    val code = json.optInt("code", 0)
                    val data = json.optString("data")
                    if (code == 0 && data.isNotEmpty()) {
                        audio.write(Base64.decode(data, Base64.DEFAULT))
                    }
                    if (code == CODE_FINISHED) break
                    // Any other positive code is a server-side error for this request.
                    if (code > 0) break
                }
            }

            if (audio.size() == 0) null else audio.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
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
