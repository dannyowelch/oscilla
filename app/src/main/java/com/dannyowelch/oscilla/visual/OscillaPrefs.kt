package com.dannyowelch.oscilla.visual

import android.content.Context
import androidx.core.content.edit
import com.dannyowelch.oscilla.audio.AudioSourceKind

class OscillaPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun source(): AudioSourceKind {
        val stored = prefs.getString(KEY_SOURCE, AudioSourceKind.VISUALIZER.name)
        return AudioSourceKind.entries.firstOrNull { it.name == stored } ?: AudioSourceKind.VISUALIZER
    }

    fun setSource(kind: AudioSourceKind) {
        prefs.edit { putString(KEY_SOURCE, kind.name) }
    }

    fun visualIndex(): Int = prefs.getInt(KEY_VISUAL, 0).coerceIn(0, VisualCatalog.all.lastIndex)

    fun setVisualIndex(index: Int) {
        prefs.edit { putInt(KEY_VISUAL, index) }
    }

    fun params(): VisualParams {
        return VisualParams(
            paletteIndex = prefs.getInt(KEY_PALETTE, VisualParams.DEFAULT.paletteIndex),
            sensitivity = prefs.getFloat(KEY_SENSITIVITY, VisualParams.DEFAULT.sensitivity),
            symmetry = prefs.getInt(KEY_SYMMETRY, VisualParams.DEFAULT.symmetry),
            speed = prefs.getFloat(KEY_SPEED, VisualParams.DEFAULT.speed),
        )
    }

    fun setParams(params: VisualParams) {
        prefs.edit {
            putInt(KEY_PALETTE, params.paletteIndex)
            putFloat(KEY_SENSITIVITY, params.sensitivity)
            putInt(KEY_SYMMETRY, params.symmetry)
            putFloat(KEY_SPEED, params.speed)
        }
    }

    fun auto(): AutoConfig {
        val trigger = if (prefs.getString(KEY_TRIGGER, AutoTrigger.SECONDS.name) == AutoTrigger.BEATS.name) {
            AutoTrigger.BEATS
        } else {
            AutoTrigger.SECONDS
        }
        return AutoConfig(
            enabled = prefs.getBoolean(KEY_AUTO, false),
            trigger = trigger,
            interval = prefs.getInt(KEY_INTERVAL, 24).coerceAtLeast(1),
            switchVisual = prefs.getBoolean(KEY_SWITCH, true),
        )
    }

    fun setAuto(config: AutoConfig) {
        prefs.edit {
            putBoolean(KEY_AUTO, config.enabled)
            putString(KEY_TRIGGER, config.trigger.name)
            putInt(KEY_INTERVAL, config.interval.coerceAtLeast(1))
            putBoolean(KEY_SWITCH, config.switchVisual)
        }
    }

    companion object {
        private const val FILE = "oscilla"
        private const val KEY_SOURCE = "source"
        private const val KEY_VISUAL = "visual"
        private const val KEY_PALETTE = "palette"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_SYMMETRY = "symmetry"
        private const val KEY_SPEED = "speed"
        private const val KEY_AUTO = "auto"
        private const val KEY_TRIGGER = "auto_trigger"
        private const val KEY_INTERVAL = "auto_interval"
        private const val KEY_SWITCH = "auto_switch"
    }
}
