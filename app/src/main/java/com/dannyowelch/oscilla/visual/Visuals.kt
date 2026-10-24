package com.dannyowelch.oscilla.visual

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.dannyowelch.oscilla.audio.AudioFeatures
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Field = Color(0xFF07060B)

fun interface VisualDraw {
    fun DrawScope.draw(
        features: AudioFeatures,
        params: VisualParams,
        runtime: VisualRuntime,
        timeMs: Long,
    )
}

interface VisualRuntime

class EmptyRuntime : VisualRuntime

class ParticleRuntime : VisualRuntime {
    val particles = ArrayList<Particle>(200)
    var lastMs: Long = 0L
    var beatHeld: Boolean = false
    val random: Random = Random(7)
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var size: Float,
    var color: Int,
)

class TerrainRuntime : VisualRuntime {
    val rows = ArrayDeque<FloatArray>()
    var lastPushMs: Long = 0L
}

data class VisualSpec(
    val id: String,
    val name: String,
    val blurb: String,
    val draw: VisualDraw,
    val createRuntime: () -> VisualRuntime = { EmptyRuntime() },
)

object VisualCatalog {
    val all: List<VisualSpec> = listOf(
        VisualSpec(
            id = "radial",
            name = "Radial",
            blurb = "Spectrum bars around a pulsing core.",
            draw = VisualDraw { features, params, _, timeMs ->
                drawRadial(features, params, timeMs)
            },
        ),
        VisualSpec(
            id = "lissajous",
            name = "Lissajous",
            blurb = "Oscilloscope line folded into a looping figure.",
            draw = VisualDraw { features, params, _, _ ->
                drawLissajous(features, params)
            },
        ),
        VisualSpec(
            id = "particles",
            name = "Burst",
            blurb = "Particles thrown out on beats.",
            draw = VisualDraw { features, params, runtime, timeMs ->
                drawParticles(features, params, runtime as ParticleRuntime, timeMs)
            },
            createRuntime = { ParticleRuntime() },
        ),
        VisualSpec(
            id = "terrain",
            name = "Terrain",
            blurb = "A scrolling ribbon of the spectrum.",
            draw = VisualDraw { features, params, runtime, timeMs ->
                drawTerrain(features, params, runtime as TerrainRuntime, timeMs)
            },
            createRuntime = { TerrainRuntime() },
        ),
        VisualSpec(
            id = "kaleidoscope",
            name = "Kaleidoscope",
            blurb = "Mirrored arcs that spin with the music.",
            draw = VisualDraw { features, params, _, timeMs ->
                drawKaleidoscope(features, params, timeMs)
            },
        ),
    )

    fun indexOf(id: String): Int {
        val index = all.indexOfFirst { it.id == id }
        return if (index < 0) 0 else index
    }

    fun spec(index: Int): VisualSpec = all[index.mod(all.size)]
}

private fun DrawScope.drawRadial(features: AudioFeatures, params: VisualParams, timeMs: Long) {
    drawRect(Field)
    val palette = Palettes.argb(params.paletteIndex)
    val radius = size.minDimension * 0.46f
    val folds = params.symmetry.coerceIn(1, 8)
    val spin = timeMs * params.speed * 0.025f
    val bands = features.bands
    if (bands.isEmpty()) return
    val count = bands.size
    repeat(folds) { fold ->
        val base = spin + fold * (360f / folds)
        for (index in bands.indices) {
            val angle = (base + index * (360f / count)) * (PI / 180.0)
            val mag = (bands[index] * params.sensitivity * 4.5f).coerceIn(0f, 1.4f)
            val inner = radius * 0.26f
            val outer = inner + mag * radius * 0.7f
            val color = Color(palette[index % palette.size]).copy(alpha = 0.92f)
            drawLine(
                color = color,
                start = Offset(
                    center.x + cos(angle).toFloat() * inner,
                    center.y + sin(angle).toFloat() * inner,
                ),
                end = Offset(
                    center.x + cos(angle).toFloat() * outer,
                    center.y + sin(angle).toFloat() * outer,
                ),
                strokeWidth = (radius / count * 1.7f).coerceAtLeast(2f),
                cap = StrokeCap.Round,
            )
        }
    }
    val pulse = radius * (0.07f + features.bass * params.sensitivity * 0.16f)
    drawCircle(Color(palette[0]).copy(alpha = 0.9f), radius = pulse.coerceAtLeast(4f))
}

private fun DrawScope.drawLissajous(features: AudioFeatures, params: VisualParams) {
    drawRect(Field)
    val wave = features.waveform
    if (wave.size < 8) return
    val palette = Palettes.argb(params.paletteIndex)
    val phase = (6 + params.speed * 18f).toInt().coerceAtLeast(1)
    val amp = size.minDimension * 0.36f * params.sensitivity.coerceIn(0.4f, 2.4f)
    val folds = params.symmetry.coerceIn(1, 6)
    repeat(folds) { fold ->
        rotate(degrees = fold * (360f / folds), pivot = center) {
            for (index in 1 until wave.size) {
                val previous = index - 1
                val x0 = wave[previous]
                val y0 = wave[(previous + phase) % wave.size]
                val x1 = wave[index]
                val y1 = wave[(index + phase) % wave.size]
                val color = Color(palette[index % palette.size]).copy(alpha = 0.9f)
                drawLine(
                    color = color,
                    start = Offset(center.x + x0 * amp, center.y + y0 * amp),
                    end = Offset(center.x + x1 * amp, center.y + y1 * amp),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawParticles(
    features: AudioFeatures,
    params: VisualParams,
    runtime: ParticleRuntime,
    timeMs: Long,
) {
    drawRect(Field)
    val dt = if (runtime.lastMs == 0L) 0.016f else ((timeMs - runtime.lastMs).coerceIn(0L, 48L) / 1000f)
    runtime.lastMs = timeMs
    if (features.beat && !runtime.beatHeld) {
        spawnBurst(runtime, params, features)
    }
    runtime.beatHeld = features.beat
    val palette = Palettes.argb(params.paletteIndex)
    val iterator = runtime.particles.iterator()
    while (iterator.hasNext()) {
        val particle = iterator.next()
        particle.life -= dt * params.speed.coerceAtLeast(0.3f)
        if (particle.life <= 0f) {
            iterator.remove()
            continue
        }
        particle.x += particle.vx * dt
        particle.y += particle.vy * dt
        particle.vx *= 0.985f
        particle.vy *= 0.985f
        val color = Color(particle.color).copy(alpha = particle.life.coerceIn(0f, 1f))
        drawCircle(color, radius = particle.size * particle.life, center = Offset(particle.x, particle.y))
    }
    if (runtime.particles.isEmpty() && features.level > 0.02f) {
        drawCircle(
            Color(palette[0]).copy(alpha = 0.35f),
            radius = size.minDimension * 0.04f * (1f + features.level * params.sensitivity),
        )
    }
}

private fun DrawScope.spawnBurst(runtime: ParticleRuntime, params: VisualParams, features: AudioFeatures) {
    val palette = Palettes.argb(params.paletteIndex)
    val count = (16 * params.sensitivity).toInt().coerceIn(8, 36)
    val folds = params.symmetry.coerceIn(1, 8)
    repeat(count) {
        if (runtime.particles.size >= 220) return
        val angle = runtime.random.nextFloat() * (PI.toFloat() * 2f)
        val speed = (80f + features.bass * 900f * params.sensitivity) * (0.6f + runtime.random.nextFloat())
        repeat(folds) { fold ->
            if (runtime.particles.size >= 220) return
            val folded = angle + fold * (PI.toFloat() * 2f / folds)
            runtime.particles.add(
                Particle(
                    x = center.x,
                    y = center.y,
                    vx = cos(folded) * speed,
                    vy = sin(folded) * speed,
                    life = 0.7f + runtime.random.nextFloat() * 0.6f,
                    size = 4f + features.level * 16f * params.sensitivity,
                    color = palette[runtime.random.nextInt(palette.size)],
                ),
            )
        }
    }
}

private fun DrawScope.drawTerrain(
    features: AudioFeatures,
    params: VisualParams,
    runtime: TerrainRuntime,
    timeMs: Long,
) {
    drawRect(Field)
    val interval = (80f / params.speed.coerceAtLeast(0.3f)).toLong().coerceAtLeast(24L)
    if (runtime.lastPushMs == 0L || timeMs - runtime.lastPushMs >= interval) {
        runtime.rows.addLast(features.bands.copyOf())
        while (runtime.rows.size > 36) runtime.rows.removeFirst()
        runtime.lastPushMs = timeMs
    }
    if (runtime.rows.isEmpty()) return
    val palette = Palettes.argb(params.paletteIndex)
    val mirror = params.symmetry >= 2
    runtime.rows.forEachIndexed { rowIndex, row ->
        val depth = rowIndex / runtime.rows.size.toFloat()
        val alpha = 0.25f + depth * 0.75f
        val baseline = size.height * (0.18f + depth * 0.72f)
        val path = Path()
        val gain = size.height * 0.22f * params.sensitivity
        path.moveTo(0f, baseline)
        for (index in row.indices) {
            val x = size.width * index / (row.size - 1).coerceAtLeast(1)
            val y = baseline - row[index] * gain * 5f
            path.lineTo(x, y)
        }
        path.lineTo(size.width, baseline)
        path.close()
        drawPath(path, Color(palette[rowIndex % palette.size]).copy(alpha = alpha.coerceIn(0.15f, 0.95f)))
        if (mirror) {
            rotate(180f, center) {
                drawPath(path, Color(palette[(rowIndex + 1) % palette.size]).copy(alpha = alpha * 0.35f))
            }
        }
    }
}

private fun DrawScope.drawKaleidoscope(features: AudioFeatures, params: VisualParams, timeMs: Long) {
    drawRect(Field)
    val palette = Palettes.argb(params.paletteIndex)
    val folds = params.symmetry.coerceIn(3, 8)
    val spin = timeMs * 0.03f * params.speed
    val bands = features.bands
    repeat(folds) { fold ->
        rotate(spin + fold * (360f / folds), center) {
            if (bands.isEmpty()) return@rotate
            bands.forEachIndexed { index, mag ->
                val reach = size.minDimension * (0.1f + index / bands.size.toFloat() * 0.38f)
                val sweep = 12f + mag * params.sensitivity * 90f
                val color = Color(palette[index % palette.size]).copy(alpha = 0.85f)
                drawArc(
                    color = color,
                    startAngle = -sweep / 2f,
                    sweepAngle = sweep.coerceAtMost(140f),
                    useCenter = false,
                    topLeft = Offset(center.x - reach, center.y - reach),
                    size = Size(reach * 2f, reach * 2f),
                    style = Stroke(width = 5f, cap = StrokeCap.Round),
                )
            }
        }
    }
    val plasma = size.minDimension * (0.05f + features.bass * params.sensitivity * 0.2f)
    drawCircle(Color(palette[1 % palette.size]).copy(alpha = 0.55f), radius = plasma.coerceAtLeast(3f))
    val wobble = sin(timeMs * 0.002 * params.speed).toFloat()
    drawCircle(
        color = Color(palette[2 % palette.size]).copy(alpha = 0.28f),
        radius = size.minDimension * (0.16f + features.treble * 0.2f),
        center = Offset(center.x + wobble * 24f, center.y),
    )
}
