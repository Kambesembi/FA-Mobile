package com.kambesembi.famobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The loading indicator for slow pages: a spinner that circles once, then uncoils into a little line
 * that zips around the screen marking furry words as they pop up (underlined, wavy or circled, from
 * either side). Each word pops with comic lines before it fades.
 */
@Composable
fun FurryLoader(modifier: Modifier = Modifier.fillMaxSize()) {
    val line = MaterialTheme.colorScheme.primary
    val word = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    val measurer = rememberTextMeasurer()
    val loader = remember { Wanderer() }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(loader) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                loader.step(min((now - last) / 1e9f, 0.05f))
                last = now
                frame = now
            }
        }
    }
    Canvas(modifier) {
        if (frame < 0L) return@Canvas // read, so every frame redraws
        loader.draw(this, measurer, style, line, word)
    }
}

/** One word on screen and the mark the line draws on it. */
private class Word(val text: String, val at: Offset, val size: IntSize, val mark: List<Offset>) {
    /** Cumulative length along [mark]. */
    val lengths: FloatArray = FloatArray(mark.size).also { out ->
        for (i in 1 until mark.size) out[i] = out[i - 1] + (mark[i] - mark[i - 1]).length()
    }
    val markLength get() = lengths.last()
    val middle get() = Offset(at.x + size.width / 2f, at.y + size.height / 2f)

    /** How much of the mark is drawn, in px. */
    var drawn = 0f
    var age = 0f
    var popAge = -1f
    var alpha = 1f

    fun pointAt(distance: Float): Offset {
        if (distance <= 0f) return mark.first()
        for (i in 1 until mark.size) {
            if (lengths[i] >= distance) {
                val f = (distance - lengths[i - 1]) / (lengths[i] - lengths[i - 1]).coerceAtLeast(0.001f)
                return mark[i - 1] + (mark[i] - mark[i - 1]) * f
            }
        }
        return mark.last()
    }
}

private enum class MarkKind { Underline, Wavy, Circle }

/** The line's state: a head on circle, curves and marks, with a fading trail behind it. */
private class Wanderer {
    private val words = listOf(
        "OwO", "UwU", "rawr", ":3", "x3", "nya~", "awoo", "*boop*", "mrrp", "^w^", ">w<", "*wags*", "purr", "henlo", "owo?", "*nuzzles*",
    )
    private val random = Random(System.nanoTime())

    private var area = Size.Zero

    /** Pixels per dp. */
    private var dp = 1f
    private var measure: (String) -> IntSize = { IntSize((it.length * 10 * dp).toInt(), (24 * dp).toInt()) }

    /** Recent head positions, newest last. */
    private val trail = ArrayDeque<Offset>()
    private var head = Offset.Zero
    private var dir = Offset(1f, 0f)

    private enum class Phase { Spin, Travel, Mark, Pop }

    private var phase = Phase.Spin
    private var phaseTime = 0f

    // Spin: once around a small circle in the middle.
    private var spinCenter = Offset.Zero
    private var angle = 0f

    // Travel: a cubic curve from the head to where the next mark starts, arriving along it.
    private var p0 = Offset.Zero
    private var p1 = Offset.Zero
    private var p2 = Offset.Zero
    private var p3 = Offset.Zero
    private var t = 0f
    private var curveLength = 1f

    private var current: Word? = null
    private var previous: Word? = null
    private var lastText = ""

    private val radius get() = 16f * dp
    private val trailLength get() = 70f * dp
    private val speed get() = 460f * dp

    fun step(dt: Float) {
        if (area == Size.Zero) return
        phaseTime += dt
        current?.let { it.age += dt }
        previous?.let { old ->
            old.age += dt
            if (old.popAge >= 0f) old.popAge += dt
            old.alpha = (old.alpha - dt / 0.25f).coerceAtLeast(0f)
            if (old.alpha <= 0f) previous = null
        }
        val before = head
        when (phase) {
            Phase.Spin -> {
                angle += dt * 2f * PI.toFloat() / SPIN_SECONDS
                head = spinCenter + Offset(cos(angle), sin(angle)) * radius
                dir = Offset(-sin(angle), cos(angle))
                if (angle >= 2f * PI.toFloat() + START_ANGLE) startTravel()
            }
            Phase.Travel -> {
                t = (t + dt * speed / curveLength).coerceAtMost(1f)
                val next = bezier(t)
                if (next != head) dir = (next - head).unit()
                head = next
                if (t >= 1f) {
                    phase = Phase.Mark
                    phaseTime = 0f
                }
            }
            Phase.Mark -> {
                val word = current ?: return startTravel()
                word.drawn = (word.drawn + dt * speed * 0.9f).coerceAtMost(word.markLength)
                val next = word.pointAt(word.drawn)
                if (next != head) dir = (next - head).unit()
                head = next
                if (word.drawn >= word.markLength) {
                    word.popAge = 0f
                    phase = Phase.Pop
                    phaseTime = 0f
                }
            }
            Phase.Pop -> {
                current?.let { it.popAge += dt }
                head += dir * speed * 0.35f * dt
                if (phaseTime > POP_SECONDS) startTravel()
            }
        }
        if (head != before) trail.addLast(head)
        trimTrail()
    }

    private fun startTravel() {
        previous = current
        val word = pickWord()
        current = word
        val start = word.mark[0]
        val arrive = (word.mark[1] - word.mark[0]).unit()
        p0 = head
        val reach = (start - head).length().coerceIn(30f * dp, 200f * dp) * 0.45f
        p1 = head + dir * reach
        p2 = start - arrive * reach
        p3 = start
        t = 0f
        curveLength = approximateLength()
        phase = Phase.Travel
        phaseTime = 0f
    }

    private fun pickWord(): Word {
        var text = words[random.nextInt(words.size)]
        if (text == lastText) text = words[(words.indexOf(text) + 1) % words.size]
        lastText = text
        val size = measure(text)
        val margin = 36f * dp
        val maxX = (area.width - size.width - margin).coerceAtLeast(margin)
        val minY = 40f * dp
        val maxY = (area.height - size.height - margin).coerceAtLeast(minY)
        // Not right on top of the last word.
        val last = current
        var at: Offset
        var tries = 0
        do {
            at = Offset(margin + random.nextFloat() * (maxX - margin), minY + random.nextFloat() * (maxY - minY))
            tries++
        } while (tries < 8 && last != null && (at - last.at).length() < 110f * dp)
        val kind = MarkKind.entries[random.nextInt(MarkKind.entries.size)]
        return Word(text, at, size, markFor(kind, random.nextBoolean(), at, size))
    }

    /** The mark's points, left to right or right to left (a circle starts on that side). */
    private fun markFor(kind: MarkKind, fromLeft: Boolean, at: Offset, size: IntSize): List<Offset> {
        val y = at.y + size.height + 3f * dp
        val x0 = at.x - 5f * dp
        val x1 = at.x + size.width + 5f * dp
        val middleX = at.x + size.width / 2f
        val points = when (kind) {
            MarkKind.Underline -> (0..12).map { i -> Offset(x0 + (x1 - x0) * i / 12f, y + sin(i / 12f * PI.toFloat()) * 1.5f * dp) }
            MarkKind.Wavy -> (0..32).map { i ->
                val f = i / 32f
                Offset(x0 + (x1 - x0) * f, y + sin(f * 6f * PI.toFloat()) * 3f * dp)
            }
            MarkKind.Circle -> {
                val c = Offset(middleX, at.y + size.height / 2f)
                val rx = size.width / 2f + 14f * dp
                val ry = size.height / 2f + 9f * dp
                val turn = if (random.nextBoolean()) 1f else -1f
                // A hand-drawn loop: a bit more than once around, the radius drifting slightly.
                (0..48).map { i ->
                    val f = i / 48f
                    val a = turn * f * 2.15f * PI.toFloat()
                    val drift = 1f + 0.06f * f
                    Offset(c.x - cos(a) * rx * drift, c.y + sin(a) * ry * drift)
                }
            }
        }
        return when {
            fromLeft -> points
            // A circle from the right: the same loop, mirrored.
            kind == MarkKind.Circle -> points.map { Offset(2f * middleX - it.x, it.y) }
            else -> points.reversed()
        }
    }

    private fun bezier(t: Float): Offset {
        val u = 1f - t
        return p0 * (u * u * u) + p1 * (3f * u * u * t) + p2 * (3f * u * t * t) + p3 * (t * t * t)
    }

    private fun approximateLength(): Float {
        var length = 0f
        var prev = p0
        for (i in 1..16) {
            val p = bezier(i / 16f)
            length += (p - prev).length()
            prev = p
        }
        return length.coerceAtLeast(1f)
    }

    private fun trimTrail() {
        var length = 0f
        var keep = trail.size
        for (i in trail.size - 1 downTo 1) {
            length += (trail[i] - trail[i - 1]).length()
            if (length > trailLength) {
                keep = trail.size - i + 1
                break
            }
        }
        while (trail.size > keep) trail.removeFirst()
    }

    fun draw(scope: DrawScope, measurer: TextMeasurer, style: TextStyle, line: Color, word: Color) = with(scope) {
        if (area != size) {
            val first = area == Size.Zero
            area = size
            dp = density
            measure = { measurer.measure(it, style).size }
            if (first) {
                spinCenter = Offset(size.width / 2f, size.height / 2f)
                angle = START_ANGLE
                head = spinCenter + Offset(cos(angle), sin(angle)) * radius
            }
        }
        val stroke = 3.5f * dp
        previous?.let { drawWord(measurer, style, it, line, word, stroke) }
        current?.let { drawWord(measurer, style, it, line, word, stroke) }
        // The line: thin and faint at the tail, full at the head; flat ends, so the pieces join without beads.
        for (i in 1 until trail.size) {
            val f = i / trail.size.toFloat()
            drawLine(line.copy(alpha = f), trail[i - 1], trail[i], strokeWidth = stroke * (0.4f + 0.6f * f), cap = StrokeCap.Butt)
        }
        if (trail.isNotEmpty()) drawCircle(line, stroke * 0.6f, head)
    }

    private fun DrawScope.drawWord(measurer: TextMeasurer, style: TextStyle, w: Word, line: Color, word: Color, stroke: Float) {
        if (w.alpha <= 0f) return
        val appear = (w.age / 0.18f).coerceIn(0f, 1f)
        // Pops in with a little overshoot, and again when its mark is done.
        val popIn = 0.7f + 0.3f * appear + 0.1f * sin(appear * PI.toFloat())
        val pop = if (w.popAge >= 0f) 0.18f * sin((w.popAge / POP_SECONDS).coerceIn(0f, 1f) * PI.toFloat()) else 0f
        val alpha = appear * w.alpha
        scale(popIn + pop, pivot = w.middle) {
            drawText(measurer.measure(w.text, style), color = word.copy(alpha = alpha * word.alpha), topLeft = w.at)
        }
        // The mark, as far as the line has drawn it.
        var prev = w.mark[0]
        for (i in 1 until w.mark.size) {
            if (w.lengths[i - 1] >= w.drawn) break
            val end = if (w.lengths[i] <= w.drawn) w.mark[i] else w.pointAt(w.drawn)
            drawLine(line.copy(alpha = 0.9f * w.alpha), prev, end, strokeWidth = stroke * 0.75f, cap = StrokeCap.Round)
            prev = end
        }
        // Comic action lines bursting out around the word.
        if (w.popAge >= 0f) {
            val f = (w.popAge / POP_SECONDS).coerceIn(0f, 1f)
            val grow = min(1f, f * 2.5f)
            val fade = (1f - f) * w.alpha
            val rx = w.size.width / 2f + 20f * dp
            val ry = w.size.height / 2f + 14f * dp
            for (k in 0 until BURST_LINES) {
                val a = k * 2f * PI.toFloat() / BURST_LINES + 0.3f
                val unit = Offset(cos(a), sin(a))
                val from = w.middle + Offset(unit.x * rx, unit.y * ry)
                val length = (if (k % 2 == 0) 12f else 8f) * dp * grow
                drawLine(line.copy(alpha = fade), from, from + unit * length, strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
            }
        }
    }

    companion object {
        private const val SPIN_SECONDS = 0.4f
        private const val POP_SECONDS = 0.3f
        private const val BURST_LINES = 8
        private const val START_ANGLE = (-PI / 2).toFloat()
    }
}

private fun Offset.length() = hypot(x, y)
private fun Offset.unit(): Offset = length().let { if (it == 0f) this else this / it }
