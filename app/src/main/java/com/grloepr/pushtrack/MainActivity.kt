package com.grloepr.pushtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.grloepr.pushtrack.ui.screen.PushUpCounterScreen
import com.grloepr.pushtrack.ui.theme.PushTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PushTrackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PushUpCounterScreen()
                }
            }
        }
    }
}