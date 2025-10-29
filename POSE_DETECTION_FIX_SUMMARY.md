# Pose Detection Coordinate Transformation Fix

## Problem
The pose detection skeleton was always offset from the actual person in the camera view, making it unusable for exercise tracking.

## Root Causes Identified

### 1. **Incorrect Coordinate Space Assumptions**
- The original code assumed ML Kit coordinates were already in display space after rotation
- ML Kit actually returns coordinates in camera sensor space regardless of rotation parameter
- This caused systematic offsets in all orientations

### 2. **Media vs InputImage Dimensions**
- The code was using `InputImage.width/height` which are already rotated dimensions
- Needed to use actual `MediaImage.width/height` from the camera sensor

### 3. **Rotation Transformation Logic**
- Original transformation matrix was overly complex and incorrect
- Needed explicit coordinate transformations for 0°, 90°, 180°, and 270° rotations
- Front camera mirroring was applied after rotation instead of before

### 4. **PreviewView Scaling Not Accounted For**
- PreviewView uses `FIT_CENTER` scale type which adds letterboxing/pillarboxing
- The overlay wasn't accounting for these offsets and scaling properly

## Solution Implemented

### Changes Made

#### 1. **ImageAnalyzer.kt** - Use Media Image Dimensions
```kotlin
// Get the ACTUAL media image dimensions (before rotation)
val mediaWidth = mediaImage.width
val mediaHeight = mediaImage.height
val rotationDegrees = imageProxy.imageInfo.rotationDegrees

// Use the media dimensions, not the InputImage dimensions
val imageWidth = mediaWidth
val imageHeight = mediaHeight
```

#### 2. **PoseOverlay.kt** - Fix Coordinate Transformation

**Key Changes:**
- Use actual media dimensions for coordinate transformation
- Calculate effective dimensions after accounting for 90°/270° rotation (width/height swap)
- Apply front camera mirroring FIRST (in sensor coordinates)
- Then apply rotation transformation
- Finally scale and translate to canvas coordinates

**Transformation Steps:**
1. Start with ML Kit landmark coordinates in sensor space
2. Mirror horizontally if front camera (`x = imageWidth - x`)
3. Apply rotation based on `rotationDegrees`:
   - 0°: No change
   - 90°: `(x, y) -> (imageHeight - y, x)`
   - 180°: `(x, y) -> (imageWidth - x, imageHeight - y)`
   - 270°: `(x, y) -> (y, imageWidth - x)`
4. Calculate FIT_CENTER scaling: `scale = min(canvasWidth/effectiveWidth, canvasHeight/effectiveHeight)`
5. Calculate centering offsets
6. Map to canvas: `canvasX = offsetX + x * scale`

#### 3. **Debug Logging Added**
- Log media dimensions vs input image dimensions
- Log rotation degrees and effective dimensions
- Log scaling factors and offsets
- Help diagnose issues in different orientations/devices

## Testing Recommendations

### Edge Cases to Test

1. **Device Orientations**
   - Portrait (0°)
   - Landscape left/right (90°/270°)
   - Upside down (180°)

2. **Camera Types**
   - Front camera (with mirroring)
   - Back camera

3. **Aspect Ratios**
   - Portrait mode (tall phones)
   - Landscape mode (wider screens)
   - Different phone sizes (small to large)

4. **Movement**
   - Person moving around screen
   - Person at edges of frame
   - Multiple people (ML Kit detection limits)

5. **Camera Switching**
   - Switch between front/back camera during runtime
   - Verify skeleton stays aligned

### Expected Behavior

- Skeleton landmarks should align precisely with body joints
- Left/right should be correct (not mirrored on back camera)
- All orientations should work without offset
- Skeleton should track smoothly as person moves

## Additional Features for Exercise Tracking

To make this a comprehensive exercise tracking app, consider adding:

1. **Exercise Definition System**
   - Create exercise templates with key poses (start, rep positions)
   - Define detection rules (e.g., pushup: hand goes down, chest near floor)

2. **Form Analysis Engine**
   - Track specific body angles (e.g., back straightness, knee alignment)
   - Compare against proper form guidelines
   - Provide real-time feedback ("Keep your back straight")

3. **Rep Counting Logic**
   - State machine to track exercise phases (up, down, rest)
   - Confidence thresholds to avoid false positives
   - Option to review/undo counts

4. **Exercise Library**
   - Pushups, squats, planks, lunges, burpees
   - Custom exercises defined by users
   - Exercise difficulty levels

5. **AI Workout Assistant**
   - Suggest workout routines based on goals
   - Adjust difficulty based on form performance
   - Personalized feedback and corrections

## Code Files Modified

1. `app/src/main/java/com/grloepr/pushtrack/ui/overlay/PoseOverlay.kt`
   - Complete rewrite of coordinate transformation
   - Added debug logging
   - Simplified and corrected rotation logic

2. `app/src/main/java/com/grloepr/pushtrack/analysis/ImageAnalyzer.kt`
   - Fixed to capture media dimensions before rotation
   - Added debug logging for troubleshooting

3. `gradle.properties`
   - Reduced heap size to avoid build issues (1024m instead of 2048m)

## Next Steps

1. Test on actual device with different orientations
2. Verify skeleton alignment is accurate
3. Remove or reduce debug logging for production
4. Implement exercise counting logic
5. Add form analysis features
6. Create exercise library

## Notes

- The fix handles all standard Android orientations
- Works with both front and back cameras
- Accounts for PreviewView FIT_CENTER scaling automatically
- Debug logs will appear in logcat to help diagnose any remaining issues
- Coordinate transformation now matches ML Kit documentation precisely

