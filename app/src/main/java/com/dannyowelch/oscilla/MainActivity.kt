package com.dannyowelch.oscilla

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dannyowelch.oscilla.probe.ProbeScreen
import com.dannyowelch.oscilla.ui.OscillaTheme
import com.dannyowelch.oscilla.visual.VisualizerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            var showProbe by rememberSaveable { mutableStateOf(false) }
            OscillaTheme {
                if (showProbe) {
                    ProbeScreen(onBack = { showProbe = false })
                } else {
                    VisualizerScreen(onOpenProbe = { showProbe = true })
                }
            }
        }
    }
}
