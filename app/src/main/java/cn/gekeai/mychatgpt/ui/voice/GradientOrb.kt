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
    val speaking = state == OrbState.SPEAKING
    val transition = rememberInfiniteTransition(label = "orb")

    @Composable
    fun phase(durationMs: Int, label: String) = transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = label,
    )

    // Continuously drifting phases (non-harmonic periods avoid an obvious loop).
    val pMain by phase(if (active) 2400 else 8200, "pMain")
    val pAlt by phase(if (active) 1700 else 6300, "pAlt")
    val tide by phase(if (active) 2900 else 9700, "tide")
    val drift by phase(if (active) 7000 else 13000, "drift")

    // While the assistant speaks there is no mic signal, so synthesize a lively
    // loudness envelope from two out-of-phase sines — a bumpy 0..1 curve that reads
    // like speech cadence, driving the same scale/color reactions as a real voice.
    val speakOsc by phase(720, "speakOsc")
    val synthAmp = if (speaking) {
        ((0.55f + 0.45f * sin(speakOsc)) * (0.6f + 0.4f * sin(speakOsc * 2.3f + 1f)))
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    // Effective loudness: real mic level while listening, synthetic while speaking.
    // A short tween smooths the ~10Hz updates into fluid motion.
    val rawAmp = maxOf(amplitude.coerceIn(0f, 1f), synthAmp)
    val amp by animateFloatAsState(
        targetValue = rawAmp,
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "amp",
    )

    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.06f else 1.015f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 1100 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Diameter breathes only slightly with loudness (+~14% at full volume) so the
    // orb pulses gently rather than ballooning.
    val voiceScale by animateFloatAsState(
        targetValue = 1f + amp * 0.14f,
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "voiceScale",
    )

    // How high the blue tide swells; larger and more energetic when active, and it
    // surges hard with the voice so the clouds churn vividly on loud speech.
    val swell = (if (active) 1.35f else 1.0f) + amp * 0.95f

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
        // Loudness brightens and swells the glow so the orb visibly flares on peaks.
        val creamX = w * (0.5f + 0.14f * sin(drift))
        val creamY = h * (0.40f + 0.05f * sin(drift * 0.7f + 1f))
        val creamRadius = w * (0.42f + 0.12f * amp)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF7DE).copy(alpha = (0.9f + 0.1f * amp).coerceAtMost(1f)),
                    Color.Transparent,
                ),
                center = Offset(creamX, creamY),
                radius = creamRadius,
            ),
            radius = creamRadius,
            center = Offset(creamX, creamY),
        )

        // 3) Tide offset shared by the cloud layers so they rise/fall together. When
        // active the tide swings far harder and loud moments scramble the crests, so
        // the blue heaves up and down through the whole orb like a churning sea.
        val churn = amp * 2.2f
        val tideMag = if (active) 0.17f else 0.07f
        val tideOff = h * tideMag * swell * sin(tide + churn)

        // Base heights and wave sizes surge when active: the layers ride much higher
        // (so blue reaches the top of the orb) and the crests grow tall enough to
        // overlap and roll over one another.
        // When active the crests are broad, rounded ocean swells rather than sharp
        // peaks: the secondary (high-freq) ripple is kept small and the base
        // frequencies are lowered, and the whole edge is drawn with smooth beziers.
        // Back layer — lightest blue, large slow swells.
        cloudLayer(
            baseY = h * (if (active) 0.32f else 0.56f) - tideOff,
            amp1 = h * (if (active) 0.20f else 0.10f) * swell, freq1 = if (active) 0.9f else 1.1f, phase1 = pMain + churn,
            amp2 = h * (if (active) 0.05f else 0.05f) * swell, freq2 = if (active) 1.7f else 2.3f, phase2 = pAlt + churn * 1.4f,
            colorStops = arrayOf(
                0f to Color(0x008FCBF6),
                0.35f to Color(0xB386C8F5),
                1f to Color(0xFF6FBCF3),
            ),
            smooth = active,
        )

        // Mid layer — vivid blue, the dominant billow.
        cloudLayer(
            baseY = h * (if (active) 0.50f else 0.70f) - tideOff * 0.6f,
            amp1 = h * (if (active) 0.17f else 0.09f) * swell, freq1 = if (active) 1.1f else 1.4f, phase1 = pAlt + 1.6f + churn * 1.7f,
            amp2 = h * (if (active) 0.05f else 0.045f) * swell, freq2 = if (active) 2.0f else 2.9f, phase2 = pMain + 0.8f + churn * 2.1f,
            colorStops = arrayOf(
                0f to Color(0x0034A0FF),
                0.28f to Color(0xC22E9BFB),
                1f to Color(0xFF1E8BFB),
            ),
            smooth = active,
        )

        // Front layer — deepest, most saturated blue pooling at the bottom.
        cloudLayer(
            baseY = h * (if (active) 0.66f else 0.83f) + tideOff * 0.4f,
            amp1 = h * (if (active) 0.12f else 0.06f) * swell, freq1 = if (active) 1.4f else 1.9f, phase1 = pMain * 1.3f + 2.1f + churn * 2.4f,
            amp2 = h * (if (active) 0.035f else 0.03f) * swell, freq2 = if (active) 2.6f else 3.7f, phase2 = pAlt * 1.2f + churn * 2.8f,
            colorStops = arrayOf(
                0f to Color(0x000C77EE),
                0.22f to Color(0xCC0F7BEF),
                1f to Color(0xFF0A6BE6),
            ),
            smooth = active,
        )

        // 4) A couple of white foam wisps riding the mid-layer crest. Louder speech
        // lifts their opacity so the crest flashes brighter on peaks.
        val crestY = h * (if (active) 0.46f else 0.66f) - tideOff * 0.6f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.85f + 0.15f * amp).coerceAtMost(1f)),
                    Color.Transparent,
                ),
                center = Offset(w * (0.30f + 0.06f * sin(pAlt)), crestY + h * 0.02f * sin(pMain)),
                radius = w * (0.16f + 0.05f * amp),
            ),
            radius = w * (0.16f + 0.05f * amp),
            center = Offset(w * (0.30f + 0.06f * sin(pAlt)), crestY + h * 0.02f * sin(pMain)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.6f + 0.2f * amp).coerceAtMost(1f)),
                    Color.Transparent,
                ),
                center = Offset(w * (0.68f - 0.05f * sin(pMain + 1f)), crestY - h * 0.01f),
                radius = w * (0.13f + 0.04f * amp),
            ),
            radius = w * (0.13f + 0.04f * amp),
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
    smooth: Boolean = false,
) {
    val w = size.width
    val h = size.height
    val steps = 36
    fun crest(f: Float) = baseY +
        amp1 * sin(freq1 * f * TWO_PI + phase1) +
        amp2 * sin(freq2 * f * TWO_PI + phase2)
    val path = Path().apply {
        moveTo(0f, h)
        if (smooth) {
            // Round the sampled crest into a continuous curve: each segment ends at
            // the midpoint between two samples, using the sample itself as the bezier
            // control point. This turns the polyline into smooth ocean swells with no
            // sharp peaks or V-notches.
            var px = 0f
            var py = crest(0f)
            lineTo(px, py)
            for (i in 1..steps) {
                val f = i / steps.toFloat()
                val x = w * f
                val y = crest(f)
                val mx = (px + x) / 2f
                val my = (py + y) / 2f
                quadraticBezierTo(px, py, mx, my)
                px = x
                py = y
            }
            lineTo(px, py)
        } else {
            for (i in 0..steps) {
                val f = i / steps.toFloat()
                val x = w * f
                val y = crest(f)
                if (i == 0) lineTo(0f, y) else lineTo(x, y)
            }
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
