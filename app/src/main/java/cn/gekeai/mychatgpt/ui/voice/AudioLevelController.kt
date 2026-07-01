package cn.gekeai.mychatgpt.ui.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Captures the microphone via [AudioRecord] and exposes a smoothed, normalized
 * loudness in [amplitude] (0f = silence, 1f = loud). The orb reads [amplitude] to
 * scale its diameter while the user is speaking. Reads of [amplitude] in a
 * composable are snapshot reads, so recomposition follows the live sound level.
 */
class AudioLevelController {
    private val _amplitude = mutableFloatStateOf(0f)

    /** Smoothed loudness in 0f..1f, updated ~10× per second while listening. */
    val amplitude: Float get() = _amplitude.floatValue

    private var record: AudioRecord? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isRunning: Boolean get() = job != null

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (job != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return
        // ~100ms of audio per read so the level updates about ten times a second.
        val bufSize = maxOf(minBuf, sampleRate / 10 * 2)

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
            )
        } catch (_: Exception) {
            null
        } ?: return

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }
        record = rec
        rec.startRecording()

        job = scope.launch {
            val buf = ShortArray(bufSize / 2)
            var smoothed = 0f
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    var sum = 0.0
                    for (i in 0 until n) {
                        val s = buf[i].toDouble()
                        sum += s * s
                    }
                    val rms = sqrt(sum / n) // 0..32768
                    // Normalize: typical speech RMS sits well below full scale, so a
                    // gain divisor of ~5000 maps a normal voice toward the top of 0..1.
                    val norm = (rms / 5_000.0).coerceIn(0.0, 1.0).toFloat()
                    // Fast attack, slow release for a lively but non-jittery level.
                    smoothed = if (norm > smoothed) {
                        smoothed + (norm - smoothed) * 0.6f
                    } else {
                        smoothed + (norm - smoothed) * 0.2f
                    }
                    _amplitude.floatValue = smoothed
                } else {
                    delay(16)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        record?.let { r ->
            try {
                if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop()
            } catch (_: Exception) {
                // ignore
            }
            r.release()
        }
        record = null
        _amplitude.floatValue = 0f
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}

@Composable
fun rememberAudioLevelController(): AudioLevelController {
    val controller = remember { AudioLevelController() }
    DisposableEffect(Unit) {
        onDispose { controller.dispose() }
    }
    return controller
}
