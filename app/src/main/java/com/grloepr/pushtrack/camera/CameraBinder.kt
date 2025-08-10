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
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// ULTRA-HIGH-PERFORMANCE thread pool with maximum priority for real-time analysis
private val analysisExecutor by lazy {
    Executors.newSingleThreadExecutor { r ->
        Thread(r).apply {
            name = "UltraPoseAnalysisThread"
            priority = Thread.MAX_PRIORITY  // Maximum priority for real-time processing
            isDaemon = false
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
    
    // EXTREME PERFORMANCE: Ultra-low resolution optimized for ground-position selfie push-ups
    val previewResolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(160, 120), // ULTRA-low resolution for maximum preview FPS
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        )
        .build()
    
    // For analysis, use minimal resolution but sufficient for pose landmark detection
    val analysisResolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(192, 144), // Minimal resolution for pose detection
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        )
        .build()
    
    // Create ultra-optimized preview use case
    val preview = Preview.Builder()
        .setResolutionSelector(previewResolutionSelector)
        .build()
        .also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
    
    // Create EXTREME performance image analysis use case
    val imageAnalysis = imageAnalyzer?.let {
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Critical for performance
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888) // Fastest format
            .setImageQueueDepth(1) // Absolute minimum queue for best performance
            .build()
            
        // Use setAnalyzer method instead of direct property assignment
        analysis.setAnalyzer(analysisExecutor, it)
        
        analysis
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