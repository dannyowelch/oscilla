package com.dannyowelch.oscilla.visual

import kotlin.random.Random

enum class AutoTrigger {
    SECONDS,
    BEATS,
}

data class AutoConfig(
    val enabled: Boolean = false,
    val trigger: AutoTrigger = AutoTrigger.SECONDS,
    val interval: Int = 24,
    val switchVisual: Boolean = true,
)

data class VisualParams(
    val paletteIndex: Int,
    val sensitivity: Float,
    val symmetry: Int,
    val speed: Float,
) {
    companion object {
        const val MIN_SENSITIVITY = 0.55f
        const val MAX_SENSITIVITY = 2.2f
        const val MIN_SYMMETRY = 1
        const val MAX_SYMMETRY = 8
        const val MIN_SPEED = 0.35f
        const val MAX_SPEED = 2f

        val DEFAULT = VisualParams(
            paletteIndex = 0,
            sensitivity = 1.1f,
            symmetry = 4,
            speed = 1f,
        )
    }
}

object Palettes {
    const val COUNT: Int = 6

    private val all: Array<IntArray> = arrayOf(
        intArrayOf(0xFF7CFFCB.toInt(), 0xFF3DDCFF.toInt(), 0xFF6C7BFF.toInt(), 0xFFFF7AD9.toInt()),
        intArrayOf(0xFFFFB703.toInt(), 0xFFFB8500.toInt(), 0xFFFF4D6D.toInt(), 0xFFFFF3B0.toInt()),
        intArrayOf(0xFFB388FF.toInt(), 0xFF80DEEA.toInt(), 0xFFE1BEE7.toInt(), 0xFF18FFFF.toInt()),
        intArrayOf(0xFF9DFF6A.toInt(), 0xFF2EE6A6.toInt(), 0xFFE8FF8A.toInt(), 0xFF14B8A6.toInt()),
        intArrayOf(0xFFFF5D8F.toInt(), 0xFFFF8C42.toInt(), 0xFFFFD166.toInt(), 0xFFEF476F.toInt()),
        intArrayOf(0xFFE0E7FF.toInt(), 0xFFA5B4FC.toInt(), 0xFF818CF8.toInt(), 0xFFF9A8D4.toInt()),
    )

    fun argb(index: Int): IntArray = all[index.floorMod(COUNT)].copyOf()
}

fun randomParams(random: Random): VisualParams {
    return VisualParams(
        paletteIndex = random.nextInt(Palettes.COUNT),
        sensitivity = VisualParams.MIN_SENSITIVITY +
            random.nextFloat() * (VisualParams.MAX_SENSITIVITY - VisualParams.MIN_SENSITIVITY),
        symmetry = VisualParams.MIN_SYMMETRY +
            random.nextInt(VisualParams.MAX_SYMMETRY - VisualParams.MIN_SYMMETRY + 1),
        speed = VisualParams.MIN_SPEED +
            random.nextFloat() * (VisualParams.MAX_SPEED - VisualParams.MIN_SPEED),
    )
}

fun nextVisualIndex(current: Int, count: Int, random: Random): Int {
    if (count <= 1) return 0
    var pick = random.nextInt(count - 1)
    if (pick >= current.floorMod(count)) pick += 1
    return pick
}

fun crossfadeAlpha(elapsedMs: Long, durationMs: Long = 640L): Float {
    if (durationMs <= 0L) return 1f
    return (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Decides when auto mode should reshuffle. The first call arms the clock and
 * does not fire, so opening the show does not immediately change the visual.
 */
class AutoScheduler {
    private var anchorMs: Long? = null
    private var anchorBeats: Int = 0
    private var beatCount: Int = 0
    private var beatHeld: Boolean = false

    fun observe(beat: Boolean) {
        if (beat && !beatHeld) beatCount += 1
        beatHeld = beat
    }

    fun due(nowMs: Long, config: AutoConfig): Boolean {
        if (!config.enabled) return false
        val interval = config.interval.coerceAtLeast(1)
        val anchor = anchorMs
        if (anchor == null) {
            anchorMs = nowMs
            anchorBeats = beatCount
            return false
        }
        return when (config.trigger) {
            AutoTrigger.SECONDS -> nowMs - anchor >= interval * 1_000L
            AutoTrigger.BEATS -> beatCount - anchorBeats >= interval
        }
    }

    fun mark(nowMs: Long) {
        anchorMs = nowMs
        anchorBeats = beatCount
    }

    fun reset() {
        anchorMs = null
        anchorBeats = 0
        beatCount = 0
        beatHeld = false
    }
}

private fun Int.floorMod(modulus: Int): Int {
    val raw = this % modulus
    return if (raw < 0) raw + modulus else raw
}
