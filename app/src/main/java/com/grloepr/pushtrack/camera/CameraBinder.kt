package com.grloepr.pushtrack.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
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

// Dedicated high-performance thread pool for image analysis
private val analysisExecutor by lazy {
    Executors.newSingleThreadExecutor { r ->
        Thread(r).apply {
            name = "PoseAnalysisThread"
            priority = Thread.MAX_PRIORITY - 1
        }
    }
}

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
): Camera {
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
): Camera {
    val camera = bindCamera(
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
    
    return camera
}

private fun bindCamera(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageAnalyzer: ImageAnalysis.Analyzer?,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
): Camera {
    // Unbind any existing camera use cases before rebinding
    cameraProvider.unbindAll()
    
    // EXTREME PERFORMANCE: Use ultra-low resolution for preview (240x180)
    // This dramatically improves preview FPS while maintaining responsiveness
    val previewResolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(240, 180), // Ultra-low resolution for absolute maximum preview FPS
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        )
        .build()
    
    // For analysis, use a slightly higher resolution but still optimized (320x240)
    val analysisResolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(320, 240), // Lower resolution for better performance with adequate detection
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        )
        .build()
    
    // Create optimized preview use case
    val preview = Preview.Builder()
        .setResolutionSelector(previewResolutionSelector)
        .build()
        .also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
    
    // Create highly optimized image analysis use case if analyzer provided
    val imageAnalysis = imageAnalyzer?.let {
        ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Critical for performance
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888) // Fastest format
            .setImageQueueDepth(1) // Minimum queue depth for best performance
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor, it)
            }
    }
    
    try {
        // Bind use cases to camera
        val useCases = listOfNotNull(preview, imageAnalysis)
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            *useCases.toTypedArray()
        )
        
        // Apply camera performance optimizations
        camera.cameraControl.apply {
            // Enable camera frame rate range optimized for preview smoothness
            enableTorch(false) // Ensure torch is off for better performance
            
            // Set auto-focus to continuous picture mode for smoother preview
            val factory = previewView.meteringPointFactory
            val centerPoint = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
            val action = FocusMeteringAction.Builder(centerPoint)
                .disableAutoCancel()
                .build()
            startFocusAndMetering(action)
        }
        
        return camera
    } catch (exc: Exception) {
        // Handle any errors (e.g., camera not available)
        exc.printStackTrace()
        throw exc
    }
}

// Release executor resources when app is destroyed
fun releaseExecutors() {
    if (!analysisExecutor.isShutdown) {
        analysisExecutor.shutdown()
    }
}