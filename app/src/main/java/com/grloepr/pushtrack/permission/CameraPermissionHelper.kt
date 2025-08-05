package com.grloepr.pushtrack.permission

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Helper for managing camera permissions
 */
object CameraPermissionHelper {
    
    /**
     * Composable function to handle camera permission request
     */
    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun RequestCameraPermission(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    ) {
        val cameraPermissionState: PermissionState = rememberPermissionState(
            permission = Manifest.permission.CAMERA
        )
        
        LaunchedEffect(cameraPermissionState.status) {
            if (cameraPermissionState.status.isGranted) {
                onPermissionGranted()
            } else {
                cameraPermissionState.launchPermissionRequest()
            }
        }
        
        if (!cameraPermissionState.status.isGranted) {
            onPermissionDenied()
        }
    }
    
    /**
     * Check if camera permission is granted
     */
    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun isCameraPermissionGranted(): Boolean {
        val cameraPermissionState = rememberPermissionState(
            permission = Manifest.permission.CAMERA
        )
        return cameraPermissionState.status.isGranted
    }
}