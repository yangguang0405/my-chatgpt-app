package cn.gekeai.mychatgpt.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private const val TWO_PI = (2.0 * Math.PI).toFloat()

/**
 * The ChatGPT "sky" orb: a pale-blue sky with a warm cream glow, over which several
 * layers of vivid-blue watercolor clouds billow up and down. Each cloud crest is a
 * sum of out-of-phase sines that drift continuously, and the whole "tide" rises and
 * falls — matching the BlueBubble reference frames. A small blur softens everything
 * into a watercolor wash. Motion speeds up and swells higher when [state] is active.
 */
@Composable
fun GradientOrb(
    modifier: Modifier = Modifier,
    state: OrbState = OrbState.IDLE,
    amplitude: Float = 0f,
    size: Dp = 96.dp,
) {
    val active = state != OrbState.IDLE
    val transition = rememberInfiniteTransition(label = "orb")

    @Composable
    fun phase(durationMs: Int, label: String) = transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = label,
    )

    // Continuously drifting phases (non-harmonic periods avoid an obvious loop).
    val pMain by phase(if (active) 4200 else 8200, "pMain")
    val pAlt by phase(if (active) 3100 else 6300, "pAlt")
    val tide by phase(if (active) 5200 else 9700, "tide")
    val drift by phase(if (active) 7000 else 13000, "drift")

    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.06f else 1.015f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 1100 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Diameter grows with live microphone loudness: up to +50% at full volume.
    // A short tween smooths the ~10Hz amplitude updates into fluid motion.
    val voiceScale by animateFloatAsState(
        targetValue = 1f + amplitude.coerceIn(0f, 1f) * 0.5f,
        animationSpec = tween(durationMillis = 110, easing = LinearEasing),
        label = "voiceScale",
    )

    // How high the blue tide swells; larger and more energetic when active, and it
    // surges further with the voice so the clouds visibly react to loud speech.
    val swell = (if (active) 1.35f else 1.0f) + amplitude.coerceIn(0f, 1f) * 0.4f

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse * voiceScale
                scaleY = pulse * voiceScale
            }
            .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            // Soft watercolor edge; clip above keeps the circle outline crisp.
            .blur(3.dp),
    ) {
        val w = this.size.width
        val h = this.size.height

        // 1) Pale sky base.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFFF1F9FE),
                0.5f to Color(0xFFE2F2FC),
                1f to Color(0xFFCFE8FA),
            ),
        )

        // 2) Warm cream glow drifting through the upper-middle (the "sun behind cloud").
        val creamX = w * (0.5f + 0.14f * sin(drift))
        val creamY = h * (0.40f + 0.05f * sin(drift * 0.7f + 1f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF7DE).copy(alpha = 0.9f), Color.Transparent),
                center = Offset(creamX, creamY),
                radius = w * 0.42f,
            ),
            radius = w * 0.42f,
            center = Offset(creamX, creamY),
        )

        // 3) Tide offset shared by the cloud layers so they rise/fall together.
        val tideOff = h * 0.07f * swell * sin(tide)

        // Back layer — lightest blue, large slow swells.
        cloudLayer(
            baseY = h * (0.56f) - tideOff,
            amp1 = h * 0.10f * swell, freq1 = 1.1f, phase1 = pMain,
            amp2 = h * 0.05f * swell, freq2 = 2.3f, phase2 = pAlt,
            colorStops = arrayOf(
                0f to Color(0x008FCBF6),
                0.35f to Color(0xB386C8F5),
                1f to Color(0xFF6FBCF3),
            ),
        )

        // Mid layer — vivid blue, the dominant billow.
        cloudLayer(
            baseY = h * (0.70f) - tideOff * 0.6f,
            amp1 = h * 0.09f * swell, freq1 = 1.4f, phase1 = pAlt + 1.6f,
            amp2 = h * 0.045f * swell, freq2 = 2.9f, phase2 = pMain + 0.8f,
            colorStops = arrayOf(
                0f to Color(0x0034A0FF),
                0.28f to Color(0xC22E9BFB),
                1f to Color(0xFF1E8BFB),
            ),
        )

        // Front layer — deepest, most saturated blue pooling at the bottom.
        cloudLayer(
            baseY = h * (0.83f) + tideOff * 0.4f,
            amp1 = h * 0.06f * swell, freq1 = 1.9f, phase1 = pMain * 1.3f + 2.1f,
            amp2 = h * 0.03f * swell, freq2 = 3.7f, phase2 = pAlt * 1.2f,
            colorStops = arrayOf(
                0f to Color(0x000C77EE),
                0.22f to Color(0xCC0F7BEF),
                1f to Color(0xFF0A6BE6),
            ),
        )

        // 4) A couple of white foam wisps riding the mid-layer crest.
        val crestY = h * 0.66f - tideOff * 0.6f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                center = Offset(w * (0.30f + 0.06f * sin(pAlt)), crestY + h * 0.02f * sin(pMain)),
                radius = w * 0.16f,
            ),
            radius = w * 0.16f,
            center = Offset(w * (0.30f + 0.06f * sin(pAlt)), crestY + h * 0.02f * sin(pMain)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                center = Offset(w * (0.68f - 0.05f * sin(pMain + 1f)), crestY - h * 0.01f),
                radius = w * 0.13f,
            ),
            radius = w * 0.13f,
            center = Offset(w * (0.68f - 0.05f * sin(pMain + 1f)), crestY - h * 0.01f),
        )
    }
}

/**
 * Fills the region below a wavy crest with a vertical gradient. The crest is
 * `baseY + amp1·sin(freq1) + amp2·sin(freq2)` sampled across the width, producing a
 * billowing cloud/wave edge that animates as the phases drift.
 */
private fun DrawScope.cloudLayer(
    baseY: Float,
    amp1: Float,
    freq1: Float,
    phase1: Float,
    amp2: Float,
    freq2: Float,
    phase2: Float,
    colorStops: Array<Pair<Float, Color>>,
) {
    val w = size.width
    val h = size.height
    val steps = 36
    val path = Path().apply {
        moveTo(0f, h)
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val x = w * f
            val y = baseY +
                amp1 * sin(freq1 * f * TWO_PI + phase1) +
                amp2 * sin(freq2 * f * TWO_PI + phase2)
            if (i == 0) lineTo(0f, y) else lineTo(x, y)
        }
        lineTo(w, h)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colorStops = colorStops,
            startY = baseY - amp1 - amp2,
            endY = h,
        ),
    )
}
