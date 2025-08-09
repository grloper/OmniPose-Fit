package com.grloepr.pushtrack.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.util.Size
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
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
) {
    bindCamera(
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
) {
    bindCamera(
        cameraProvider = cameraProvider,
        previewView = previewView,
        lifecycleOwner = lifecycleOwner,
        imageAnalyzer = imageAnalyzer,
        cameraSelector = cameraSelector
    )
    
    // If the analyzer is an ImageAnalyzer, inform it about camera facing
    if (imageAnalyzer is com.grloepr.pushtrack.analysis.ImageAnalyzer) {
        imageAnalyzer.setCameraFacing(cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
    }
}

private fun bindCamera(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageAnalyzer: ImageAnalysis.Analyzer?,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
) {
    // Unbind any existing camera use cases before rebinding
    cameraProvider.unbindAll()
    
    // Create resolution selector that prioritizes frame rate over resolution
    val resolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(1280, 720), // Target 720p for good balance of speed vs quality
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        )
        .build()
    
    // Create preview use case with optimized resolution
    val preview = Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .build()
        .also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
    
    // Create image analysis use case if analyzer provided
    val imageAnalysis = imageAnalyzer?.let {
        ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector) // Use same resolution for consistency
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888) // Optimize format
            .build()
            .also { analysis ->
                // Use background executor for analysis
                val analysisExecutor = Executors.newSingleThreadExecutor()
                analysis.setAnalyzer(analysisExecutor, it)
            }
    }
    
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