# Skeleton Coordinate Transformation Fixes

## Problem Summary
The pose detection skeleton overlay was not aligning correctly with the user's body when using the front camera from different viewing angles. This was caused by improper coordinate transformation that didn't account for:

1. **Device rotation**: Camera orientation changes weren't properly handled
2. **Mirroring order**: Front camera mirroring was applied after scaling/rotation
3. **Hardcoded values**: Rotation degrees were hardcoded to 0 instead of using actual values

## Technical Root Causes

### 1. Missing Rotation Information
```kotlin
// BEFORE: Hardcoded rotation
val poseFrameResult = poseResult.toPoseFrameResult(
    rotationDegrees = 0,  // ❌ Always 0, ignoring actual device orientation
    isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
)

// AFTER: Use actual rotation from camera
val poseFrameResult = poseResult.toPoseFrameResult(
    isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
)
// ✅ Now uses actual rotation from ImageAnalyzer
```

### 2. Incorrect Transformation Order
```kotlin
// BEFORE: Mirroring after rotation/scaling
val (rotatedX, rotatedY, ...) = applyRotation(x, y)
val scaledX = rotatedX * scale
if (isFrontCamera) scaledX = canvasWidth - scaledX  // ❌ Wrong order

// AFTER: Mirroring before transformations
val mirroredX = if (isFrontCamera) imageWidth - x else x  // ✅ Mirror first
val (rotatedX, rotatedY, ...) = applyRotation(mirroredX, y)
val scaledX = rotatedX * scale
```

### 3. Data Flow Improvements
```kotlin
// Enhanced data capture in ImageAnalyzer
val rotationDegrees = imageProxy.imageInfo.rotationDegrees  // ✅ Capture actual rotation
_poseResults.tryEmit(PoseDetectionResult(pose, imageWidth, imageHeight, rotationDegrees))
```

## Fixed Coordinate Transformation Logic

### For Different Device Orientations:

**Portrait (0°)**: No rotation needed
- Front camera: X = imageWidth - originalX
- Back camera: X = originalX

**Landscape Left (90°)**: Rotate coordinates
- newX = imageHeight - originalY
- newY = mirroredX (where mirroredX accounts for front camera)

**Upside Down (180°)**: Flip both axes
- newX = imageWidth - mirroredX
- newY = imageHeight - originalY

**Landscape Right (270°)**: Rotate opposite direction
- newX = originalY
- newY = imageWidth - mirroredX

### Scaling and Centering
After coordinate transformation:
1. Calculate scale to maintain aspect ratio
2. Apply uniform scaling to both X and Y
3. Center the pose within the canvas bounds

## Benefits of the Fix

1. **Accurate Alignment**: Skeleton overlay now correctly aligns with body parts
2. **Orientation Support**: Works correctly in all device orientations
3. **Front Camera Mirroring**: Properly mirrors the skeleton for intuitive front camera view
4. **Different Viewing Angles**: Handles various camera positions relative to the user

## Testing

Added comprehensive tests in `PoseOverlayTest.kt`:
- Front/back camera coordinate mirroring verification
- Edge case testing (left edge, right edge, center)
- Rotation transformation validation
- Integration with actual pose landmark data

## Files Modified

1. **ImageAnalyzer.kt**: Capture and pass rotation degrees
2. **PoseResultExtensions.kt**: Use actual rotation data
3. **PushUpCameraScreen.kt**: Remove hardcoded rotation values
4. **EnhancedPoseOverlay.kt**: Fix transformation order and logic
5. **PoseOverlayTest.kt**: Add comprehensive test coverage

This fix ensures the skeleton detection is accurate regardless of camera angle, device orientation, or front/back camera usage.