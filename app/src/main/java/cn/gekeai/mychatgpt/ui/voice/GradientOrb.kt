package cn.gekeai.mychatgpt.ui.voice

import androidx.compose.animation.core.FastOutSlowInEasing
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
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2.0 * Math.PI).toFloat()

/**
 * A soft sky orb built from overlapping translucent vapor fields. The layers drift
 * on different periods so the colors roll through each other like slow cloud cover
 * instead of moving as a single wave.
 */
@Composable
fun GradientOrb(
    modifier: Modifier = Modifier,
    state: OrbState = OrbState.IDLE,
    amplitude: Float = 0f,
    size: Dp = 96.dp,
) {
    val speaking = state == OrbState.SPEAKING
    val transition = rememberInfiniteTransition(label = "orb")

    @Composable
    fun phase(durationMs: Int, label: String) = transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = label,
    )

    fun duration(idle: Int, listening: Int, speaking: Int) = when (state) {
        OrbState.IDLE -> idle
        OrbState.LISTENING -> listening
        OrbState.SPEAKING -> speaking
    }

    // Non-harmonic periods avoid a visible loop and keep the motion cloud-like.
    val rollA by phase(duration(12800, 3400, 2800), "rollA")
    val rollB by phase(duration(16600, 4600, 3900), "rollB")
    val rollC by phase(duration(21400, 5600, 4800), "rollC")
    val drift by phase(duration(26000, 8000, 6800), "drift")

    val stateEnergy by animateFloatAsState(
        targetValue = when (state) {
            OrbState.IDLE -> 0f
            OrbState.LISTENING -> 1f
            OrbState.SPEAKING -> 1.15f
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "stateEnergy",
    )

    // While the assistant speaks there is no mic signal, so synthesize a lively
    // loudness envelope from two out-of-phase sines — a bumpy 0..1 curve that reads
    // like speech cadence, driving the same scale/color reactions as a real voice.
    val speakOsc by phase(480, "speakOsc")
    val synthAmp = if (speaking) {
        val syllable = 0.5f + 0.5f * sin(speakOsc)
        val flutter = 0.5f + 0.5f * sin(speakOsc * 2.7f + 1.2f)
        (0.36f + syllable * 0.44f + flutter * 0.20f)
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    // Effective loudness: real mic level while listening, synthetic while speaking.
    // A short tween smooths the ~10Hz updates into fluid motion.
    val stateFloor = when (state) {
        OrbState.IDLE -> 0f
        OrbState.LISTENING -> 0.18f
        OrbState.SPEAKING -> 0.28f
    }
    val rawAmp = maxOf(amplitude.coerceIn(0f, 1f), synthAmp, stateFloor)
    val amp by animateFloatAsState(
        targetValue = rawAmp,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "amp",
    )

    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = when (state) {
            OrbState.IDLE -> 1.012f
            OrbState.LISTENING -> 1.055f
            OrbState.SPEAKING -> 1.07f
        },
        animationSpec = infiniteRepeatable(
            tween(duration(3200, 780, 660), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Diameter breathes with loudness (+~13% at full volume); the larger reaction
    // comes from internal vapor motion rather than ballooning the whole orb.
    val voiceScale by animateFloatAsState(
        targetValue = 1f + amp * 0.13f,
        animationSpec = tween(durationMillis = 85, easing = FastOutSlowInEasing),
        label = "voiceScale",
    )

    // State changes create a visible churn even when the mic level is flat; audio
    // still adds extra lift and color density on top.
    val swell = 0.92f + stateEnergy * 0.38f + amp * 0.70f
    val colorEnergy = 0.74f + stateEnergy * 0.42f + amp * 0.42f
    val swirl = 1f + stateEnergy * 0.55f + amp * 0.45f

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse * voiceScale
                scaleY = pulse * voiceScale
            }
            .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            // A light global blur lets the overlapping gradients melt together.
            .blur(3.dp),
    ) {
        val w = this.size.width
        val h = this.size.height
        val d = minOf(w, h)

        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFFF8FCFF),
                0.42f to Color(0xFFEAF6FE),
                0.76f to Color(0xFFD9ECFA),
                1f to Color(0xFFC8E5FA),
            ),
        )

        fun energized(color: Color, alpha: Float) =
            color.copy(alpha = (alpha * colorEnergy).coerceIn(0f, 1f))

        val churn = stateEnergy * 0.75f + amp * 1.15f
        val lift = h * (0.025f + stateEnergy * 0.035f + amp * 0.055f) * sin(rollC + churn)

        mistBlob(
            center = Offset(
                w * (0.50f + swirl * (0.08f * cos(drift) + 0.025f * sin(rollB))),
                h * (0.38f + swirl * 0.045f * sin(drift * 0.74f + 1.1f)),
            ),
            radius = d * (0.48f + 0.08f * amp + 0.035f * stateEnergy),
            color = Color(0xFFFFF4D8),
            alpha = 0.72f + amp * 0.16f + stateEnergy * 0.08f,
        )

        mistBlob(
            center = Offset(
                w * (0.28f + swirl * 0.11f * cos(rollB + 1.7f)),
                h * (0.66f + swirl * 0.06f * sin(rollA + 0.6f)) - lift,
            ),
            radius = d * (0.58f + 0.08f * swell),
            color = Color(0xFF74CAF8),
            alpha = 0.38f * colorEnergy,
        )
        mistBlob(
            center = Offset(
                w * (0.74f + swirl * 0.09f * cos(rollA + 2.2f)),
                h * (0.61f + swirl * 0.075f * sin(rollB + 2.6f)) + lift * 0.4f,
            ),
            radius = d * (0.46f + 0.06f * swell),
            color = Color(0xFF9BE2FF),
            alpha = 0.34f * colorEnergy,
        )

        vaporSheet(
            baseY = h * (0.60f + swirl * 0.045f * sin(rollB + 0.8f)) - lift,
            height = h * 0.52f * swell,
            phaseA = rollA + churn,
            phaseB = rollB + 1.6f,
            phaseC = rollC + 2.4f,
            turbulence = 1f + stateEnergy * 0.35f + amp * 0.45f,
            colorStops = arrayOf(
                0f to energized(Color(0xFF8ED2FA), 0f),
                0.24f to energized(Color(0xFF8ED2FA), 0.48f),
                0.68f to energized(Color(0xFF54B5F5), 0.76f),
                1f to energized(Color(0xFF279CF0), 0.96f),
            ),
        )

        mistBlob(
            center = Offset(
                w * (0.40f + swirl * 0.15f * cos(rollA + 0.2f)),
                h * (0.62f + swirl * 0.07f * sin(rollB + 0.9f)) - lift * 0.7f,
            ),
            radius = d * (0.31f + 0.06f * swell),
            color = Color(0xFF36B2F8),
            alpha = 0.50f * colorEnergy,
        )
        mistBlob(
            center = Offset(
                w * (0.62f + swirl * 0.16f * cos(rollC + 2.7f)),
                h * (0.70f + swirl * 0.065f * sin(rollA + 1.4f)) + lift * 0.25f,
            ),
            radius = d * (0.36f + 0.07f * swell),
            color = Color(0xFF1597F4),
            alpha = 0.54f * colorEnergy,
        )

        vaporSheet(
            baseY = h * (0.72f + swirl * 0.045f * sin(rollA + 2.2f)) - lift * 0.35f,
            height = h * 0.42f * swell,
            phaseA = rollB + 0.5f + churn,
            phaseB = rollC + 2.1f,
            phaseC = rollA + 1.3f,
            turbulence = 1.08f + stateEnergy * 0.45f + amp * 0.50f,
            colorStops = arrayOf(
                0f to energized(Color(0xFF2FA5F5), 0f),
                0.18f to energized(Color(0xFF2FA5F5), 0.56f),
                0.58f to energized(Color(0xFF168BF2), 0.86f),
                1f to energized(Color(0xFF086EE7), 1f),
            ),
        )

        mistBlob(
            center = Offset(
                w * (0.36f + swirl * 0.12f * cos(rollC + 1.1f)),
                h * (0.83f + swirl * 0.04f * sin(rollB + 0.4f)) + lift * 0.35f,
            ),
            radius = d * (0.34f + 0.04f * swell),
            color = Color(0xFF0D7FF0),
            alpha = 0.58f * colorEnergy,
        )
        mistBlob(
            center = Offset(
                w * (0.72f + swirl * 0.11f * cos(rollB + 3.4f)),
                h * (0.86f + swirl * 0.035f * sin(rollC + 1.8f)) + lift * 0.2f,
            ),
            radius = d * (0.30f + 0.05f * swell),
            color = Color(0xFF086DDF),
            alpha = 0.56f * colorEnergy,
        )

        vaporSheet(
            baseY = h * (0.87f + swirl * 0.035f * sin(rollC + 1.7f)) + lift * 0.2f,
            height = h * 0.28f * swell,
            phaseA = rollC + 0.8f + churn,
            phaseB = rollA + 2.6f,
            phaseC = rollB + 0.4f,
            turbulence = 1.16f + stateEnergy * 0.45f + amp * 0.55f,
            colorStops = arrayOf(
                0f to energized(Color(0xFF086DDF), 0f),
                0.22f to energized(Color(0xFF086DDF), 0.70f),
                1f to energized(Color(0xFF055CCB), 1f),
            ),
        )

        mistBlob(
            center = Offset(
                w * (0.38f + swirl * 0.10f * cos(drift + 2.3f)),
                h * (0.52f + swirl * 0.045f * sin(rollA + 2.0f)) - lift * 0.5f,
            ),
            radius = d * (0.21f + 0.05f * amp + 0.025f * stateEnergy),
            color = Color.White,
            alpha = 0.44f + amp * 0.16f + stateEnergy * 0.10f,
        )
        mistBlob(
            center = Offset(
                w * (0.66f + swirl * 0.08f * cos(rollB + 0.8f)),
                h * (0.50f + swirl * 0.05f * sin(drift + 0.5f)),
            ),
            radius = d * (0.18f + 0.04f * amp + 0.02f * stateEnergy),
            color = Color(0xFFFFFDF3),
            alpha = 0.34f + amp * 0.12f + stateEnergy * 0.08f,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0f),
                    0.78f to Color.White.copy(alpha = 0.02f),
                    1f to Color.White.copy(alpha = 0.26f),
                ),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = d * 0.55f,
            ),
            radius = d * 0.55f,
            center = Offset(w * 0.5f, h * 0.5f),
        )
    }
}

private fun DrawScope.mistBlob(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
    midAlpha: Float = alpha * 0.42f,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = alpha.coerceIn(0f, 1f)),
                0.48f to color.copy(alpha = midAlpha.coerceIn(0f, 1f)),
                1f to color.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.vaporSheet(
    baseY: Float,
    height: Float,
    phaseA: Float,
    phaseB: Float,
    phaseC: Float,
    turbulence: Float = 1f,
    colorStops: Array<Pair<Float, Color>>,
) {
    val w = size.width
    val h = size.height
    val steps = 72
    val bottom = (baseY + height).coerceAtMost(h * 1.12f)
    val path = Path().apply {
        moveTo(0f, bottom)
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val x = w * f
            val y = baseY +
                height * 0.105f * turbulence * sin(f * TWO_PI * 0.86f + phaseA) +
                height * 0.062f * turbulence * sin(f * TWO_PI * 1.72f + phaseB) +
                height * 0.036f * turbulence * sin(f * TWO_PI * 3.10f + phaseC)
            if (i == 0) lineTo(0f, y) else lineTo(x, y)
        }
        lineTo(w, bottom)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colorStops = colorStops,
            startY = baseY - height * 0.24f,
            endY = bottom,
        ),
    )
}
