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
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
): AutoCloseable {
    return bindCamera(
        cameraProvider = cameraProvider,
        previewView = previewView,
        lifecycleOwner = lifecycleOwner,
        imageAnalyzer = null,
        cameraSelector = cameraSelector
    )
}

fun bindCameraWithAnalysis(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageAnalyzer: ImageAnalysis.Analyzer,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
): AutoCloseable {
    return bindCamera(
        cameraProvider = cameraProvider,
        previewView = previewView,
        lifecycleOwner = lifecycleOwner,
        imageAnalyzer = imageAnalyzer,
        cameraSelector = cameraSelector
    )
}

private fun bindCamera(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageAnalyzer: ImageAnalysis.Analyzer?,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
): AutoCloseable {
    // The caller owns only these use cases and their executor.
    // Never tear down another screen's camera bindings.
    val analysisExecutor = imageAnalyzer?.let { Executors.newSingleThreadExecutor() }
    
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
                analysis.setAnalyzer(requireNotNull(analysisExecutor), it)
            }
    }
    
    val useCases = listOfNotNull(preview, imageAnalysis)
    try {
        // Bind once for this screen/camera configuration.
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            *useCases.toTypedArray()
        )
    } catch (exc: Exception) {
        imageAnalysis?.clearAnalyzer()
        analysisExecutor?.shutdown()
        throw exc
    }
    return AutoCloseable {
        imageAnalysis?.clearAnalyzer()
        cameraProvider.unbind(*useCases.toTypedArray())
        analysisExecutor?.shutdown()
    }
}