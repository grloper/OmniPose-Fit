package com.grloepr.pushtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.grloepr.pushtrack.camera.releaseExecutors
import com.grloepr.pushtrack.ui.screen.PushUpCameraScreen
import com.grloepr.pushtrack.ui.theme.PushTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Improve camera performance by optimizing window flags
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            PushTrackTheme {
                // Set up hardware acceleration and optimize surface composition
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PushUpApp()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources to prevent leaks
        releaseExecutors()
    }
}

@Composable
fun PushUpApp() {
    PushUpCameraScreen()
}