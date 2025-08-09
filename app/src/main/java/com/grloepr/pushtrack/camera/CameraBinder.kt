package com.grloepr.pushtrack.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import java.util.concurrent.Executors
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
    bindCamera(
        cameraProvider = cameraProvider,
        previewView = previewView,
        lifecycleOwner = lifecycleOwner,
        imageAnalyzer = null
    )
}

fun bindCameraWithAnalysis(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageAnalyzer: ImageAnalysis.Analyzer
) {
    bindCamera(
        cameraProvider = cameraProvider,
        previewView = previewView,
        lifecycleOwner = lifecycleOwner,
        imageAnalyzer = imageAnalyzer
    )
}

private fun bindCamera(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageAnalyzer: ImageAnalysis.Analyzer?
) {
    // Unbind any existing camera use cases before rebinding
    cameraProvider.unbindAll()
    
    // Create preview use case
    val preview = Preview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }
    
    // Create image analysis use case if analyzer provided
    val imageAnalysis = imageAnalyzer?.let {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                // Use background executor for analysis
                val analysisExecutor = Executors.newSingleThreadExecutor()
                analysis.setAnalyzer(analysisExecutor, it)
            }
    }
    
    // Select back camera as a default
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    
    try {
        // Bind use cases to camera
        val useCases = listOfNotNull(preview, imageAnalysis)
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            *useCases.toTypedArray()
        )
    } catch (exc: Exception) {
        // Handle any errors (e.g., camera not available)
        exc.printStackTrace()
    }
}