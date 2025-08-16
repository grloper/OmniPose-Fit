package com.grloepr.pushtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.grloepr.pushtrack.ui.screen.PushUpCameraScreen
import com.grloepr.pushtrack.ui.theme.PushTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PushTrackTheme {
                PushUpCameraScreen()
            }
        }
    }
}