# PushTrack - AI Coding Agent Instructions

## Project Overview
PushTrack is an Android fitness app using **ML Kit Pose Detection** for real-time exercise tracking (push-ups, squats, pull-ups) with AI-driven form feedback. Built with **Jetpack Compose**, **CameraX**, and **Kotlin**.

## Critical Architecture Concepts

### 1. Pose Detection Coordinate System (CRITICAL - DO NOT BREAK)
**The skeleton overlay alignment is the app's core achievement.** Changes here require extreme care.

- **PoseOverlay.kt** uses a precise coordinate transformation pipeline:
  1. ML Kit returns landmarks in **rotated image coordinate space** (already accounts for `rotationDegrees` passed to `InputImage.fromMediaImage()`)
  2. For 90°/270° rotations, width and height are swapped in effective dimensions
  3. Front camera mirroring is applied **before** rotation: `x = effectiveImageWidth - x`
  4. Scaling uses `FIT_CENTER` logic to match `PreviewView` letterboxing/pillarboxing
  5. Final mapping: `canvasX = offsetX + x * scale`

- **ImageAnalyzer.kt** extracts **media dimensions** from `mediaImage.width/height` (NOT `inputImage.width/height` which are already rotated)

**Rule**: Never modify coordinate transformation logic without reading `POSE_DETECTION_FIX_SUMMARY.md`. The overlay must stay pixel-perfect aligned.

### 2. Modular Exercise Detection Architecture
**Rep counting is delegated to exercise-specific detectors** (2025 design):

```
ExerciseAnalyzer (orchestrates)
    └── ExerciseDetector interface
          ├── PushupDetector (analyzes gaps, angles → DetectorResult)
          ├── SquatDetector (analyzes knee angles, hip depth → DetectorResult)
          └── Pull-up inline detector (simple nose-to-shoulder check)
```

- Each detector returns `DetectorResult(state, repCompleted, FormFeedback)`
- Rep counting happens **inside detectors** on `Down → Up` state transitions
- `FormFeedback` aggregates weighted `FormSignal` objects for the HUD
- State machines use `MeasurementSmoother` (rolling average) to filter jitter
- Detectors maintain `minStateDurationMs` debounce to prevent false positives

**Rule**: Rep logic belongs in detectors. `ExerciseAnalyzer` only aggregates results and resets on pose loss.

### 3. Form Feedback System
**Multi-signal scoring for real-time coaching** (see `EXERCISE_TRACKING_FEATURES.md`):

- **Signals**: `FormSignal(id, label, score, severity, message, weight)` where score ∈ [0,1]
- **Severities**: INFO (green), WARNING (amber), CRITICAL (red)
- **Overall score**: Weighted average of signal scores
- **HUD rendering**: `FormFeedbackPanel` shows progress bar + top 3 issues
- Detectors expose `debugMetrics` map for tuning (e.g., `depthDelta`, `kneeAngle`)

**Push-up signals**: depth (via normalized torso-scaled gap delta), elbow lockout, plank alignment  
**Squat signals**: hip depth (via leg-length-normalized hip-to-ankle ratio), knee bend, torso angle  

**Rule**: All exercise logic (angles, distances) must be **normalized** by body dimensions (torso length, leg length) to work across different body sizes. Never use raw pixel values for thresholds.

## Build & Development Workflows

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Note: JVM heap limited to 1024m in gradle.properties due to build issues
```

### Dependencies (libs.versions.toml)
- **CameraX** 1.3.4: Camera lifecycle, preview, image analysis
- **ML Kit Pose Detection** 18.0.0-beta4: Pose landmark extraction
- **Compose BOM** 2024.09.00: UI framework
- **Kotlin** 2.0.21 with Compose plugin

### Testing Gotchas
- **Always test on physical device** - emulator pose detection doesn't work well
- **Default camera**: Front camera (selfie mode) for push-ups/pull-ups
- **Threshold tuning**: Check `debugMetrics` in `FormFeedback` via Logcat
- **Frame rate**: ImageAnalyzer throttles to ~15 FPS (see `targetAnalysisInterval`)

## Key Conventions & Patterns

### 1. Sealed Class State Machines
```kotlin
sealed class ExerciseState {
    object Waiting : ExerciseState()
    object Down : ExerciseState()
    object Up : ExerciseState()
}
```
Used for type-safe exercise progression. Never add new states without updating all detectors.

### 2. Pose Landmark Validation Pattern
```kotlin
if (!hasValidLandmarks(shoulder, elbow, wrist, minConfidence = 0.45f)) {
    return DetectorResult(state = Waiting, poseVisible = false, ...)
}
```
Always check `inFrameLikelihood` before using landmarks. Default threshold: 0.45f for body, 0.6f for head.

### 3. Measurement Smoothing
```kotlin
private val smoother = MeasurementSmoother(windowSize = 6)
val smoothed = smoother.add(rawValue) // Returns rolling average
```
Used to filter noise in angle/distance measurements. Window size 4-6 balances responsiveness vs stability.

### 4. Compose UI Structure
- **MainActivity** → **PushUpCameraScreen** (entry point)
- **ExerciseSelectionScreen** → card-based picker
- **CameraPreviewScreen** → camera + overlay + HUD
- **PoseOverlay** → Canvas for skeleton rendering
- **FormFeedbackPanel** → Overlay card with progress bar + signals

### 5. Resource Management
Always wrap ML Kit detector in `DisposableEffect`:
```kotlin
DisposableEffect(poseDetectorClient) {
    onDispose { poseDetectorClient.close() }
}
```

## Common Pitfalls

### ❌ Don't: Modify coordinate transformation without understanding rotation
The current system accounts for 0°/90°/180°/270° rotations and front camera mirroring. Breaking this requires re-validating on all device orientations.

### ❌ Don't: Use raw pixel measurements for exercise detection
Body dimensions vary wildly. Always normalize by torso length, leg length, or shoulder width.

### ❌ Don't: Skip state stabilization in detectors
Without `minStateDurationMs` debounce, rep counters trigger on momentary noise.

### ✅ Do: Add debug logging when tuning thresholds
```kotlin
println("Detector: kneeAngle=$angle, hipDepth=$depth, state=$state")
```

### ✅ Do: Test with different body types and camera angles
What works for one person may not work for another. Use percentage-based thresholds.

### ✅ Do: Keep UI composables stateless
State belongs in `remember` or ViewModels, not in Composable parameters.

## Files You'll Modify Most

**Exercise Logic**:
- `exercise/detector/PushupDetector.kt` - Push-up thresholds, form signals
- `exercise/detector/SquatDetector.kt` - Squat thresholds, form signals
- `exercise/ExerciseAnalyzer.kt` - Detector orchestration, pose visibility checks

**UI**:
- `ui/screen/PushUpCameraScreen.kt` - Camera screen, HUD, controls
- `ui/screen/ExerciseSelectionScreen.kt` - Exercise picker cards
- `ui/overlay/PoseOverlay.kt` - **TOUCH WITH EXTREME CARE**

**Core Utilities**:
- `exercise/PoseMath.kt` - Shared angle/distance calculations
- `exercise/FormFeedback.kt` - Form scoring data structures

## Adding New Exercises

1. Create `exercise/detector/NewExerciseDetector.kt` implementing `ExerciseDetector`
2. Add enum entry to `ExerciseType.kt`
3. Update `ExerciseAnalyzer` to instantiate your detector
4. Define exercise-specific `FormSignal` IDs and messages
5. Add card to `ExerciseSelectionScreen`
6. Update `hasCoreLandmarks()` with required landmarks for your exercise

**Example skeleton**:
```kotlin
class PlankDetector : ExerciseDetector {
    private val angleSmoother = MeasurementSmoother(6)
    private var currentState = ExerciseState.Waiting
    
    override fun analyze(pose: Pose, timestamp: Long): DetectorResult {
        // 1. Extract landmarks
        // 2. Normalize measurements by body dimensions
        // 3. Apply smoothing
        // 4. Determine state with debouncing
        // 5. Build FormFeedback with signals
        return DetectorResult(...)
    }
}
```

## Related Documentation
- `POSE_DETECTION_FIX_SUMMARY.md` - Coordinate transformation deep dive
- `EXERCISE_TRACKING_FEATURES.md` - Form feedback system architecture
- `README.md` - Build instructions, known issues

## Questions for Humans
When in doubt about thresholds or body mechanics, ask the user:
- "What camera angle did you test with?" (affects which landmarks are visible)
- "Does the skeleton overlay still align correctly?" (after any coordinate changes)
- "What rep count did you get vs expected?" (helps tune state thresholds)
