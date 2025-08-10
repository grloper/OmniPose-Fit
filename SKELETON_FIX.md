# Skeleton Direction Fix

## Problem
The skeleton overlay was appearing in the wrong direction (mirrored) when using the front camera. This happened because:

1. The front camera preview is horizontally mirrored for user experience
2. ML Kit pose detection returns landmark coordinates in the original image coordinate system
3. Without coordinate transformation, the skeleton appears on the opposite side of the body

## Solution
Added coordinate mirroring logic in `PoseOverlay.kt`:

1. **Added `isFrontCamera` parameter** to `PoseOverlay` composable
2. **Updated coordinate transformation** in both `drawPoseLandmarks` and `drawPoseConnections`
3. **Applied mirroring formula**: `x = imageWidth - originalX` when front camera is used
4. **Updated `PushUpCameraScreen`** to pass camera selector information to overlay

## Key Changes

### PoseOverlay.kt
- Added `isFrontCamera: Boolean` parameter
- Modified landmark drawing: `val x = if (isFrontCamera) imageWidth - it.position.x else it.position.x`
- Modified connection drawing: Applied same mirroring to both start and end points

### PushUpCameraScreen.kt
- Added `isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA` parameter

## Technical Details
- **Mirroring formula**: `mirroredX = imageWidth - originalX`
- **Applied to**: All landmark points and skeleton connections
- **Y-coordinates**: Unchanged (no vertical mirroring needed)
- **Backward compatibility**: Defaults to `isFrontCamera = false` for existing code

## Testing
Created unit tests in `PoseOverlayTest.kt` to verify coordinate transformation logic:
- Front camera mirroring behavior
- Back camera no-mirroring behavior  
- Edge cases (left edge, right edge, center)

This fix ensures the skeleton overlay correctly aligns with the user's body regardless of which camera is being used.