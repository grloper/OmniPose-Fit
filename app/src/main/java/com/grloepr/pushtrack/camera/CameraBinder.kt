package com.grloepr.pushtrack.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun rememberCameraProvider(): ProcessCameraProvider? {
    val context = LocalContext.current
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    
    LaunchedEffect(Unit) {
        cameraProvider = getCameraProvider(context)
    }
    
    return cameraProvider
}

private suspend fun getCameraProvider(context: android.content.Context): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(context).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(context))
        }
    }

fun bindCameraPreview(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    // Unbind any existing camera use cases before rebinding
    cameraProvider.unbindAll()
    
    // Create preview use case
    val preview = Preview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }
    
    // Select back camera as a default
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    
    try {
        // Bind use cases to camera
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview
        )
    } catch (exc: Exception) {
        // Handle any errors (e.g., camera not available)
        exc.printStackTrace()
    }
}