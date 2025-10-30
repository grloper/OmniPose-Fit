# Pushup Counter Video

An Android app for exercise tracking with pose detection using ML Kit.

## Recent Fixes

### Pose Detection Coordinate Transformation

Fixed the horizontal flip issue where skeleton was appearing mirrored (head right, legs left).

**Current State:** 
- Removed manual rotation transformations
- ML Kit's `InputImage.fromMediaImage()` with rotationDegrees handles rotation internally
- Landmarks are returned in the coordinate space of the rotated image
- Added mirroring for front camera to match PreviewView behavior

**Testing Required:**
1. Test on actual device - the skeleton may still need adjustments
2. Check debug logs in logcat for coordinate values
3. Try both front and back cameras
4. Test in different device orientations

## Build Instructions

The app requires Java 11+ and Android SDK 24+.

```bash
./gradlew assembleDebug
```

Note: The gradle build currently has JVM heap size issues. If build fails, reduce memory in `gradle.properties`.

## Key Files Modified

- `app/src/main/java/com/grloepr/pushtrack/ui/overlay/PoseOverlay.kt` - Coordinate transformation
- `app/src/main/java/com/grloepr/pushtrack/analysis/ImageAnalyzer.kt` - Image processing
- Debug logging added to help diagnose remaining issues

## Features

✅ **Modular Exercise Detection System**
- Real-time rep counting for push-ups, squats, and pull-ups
- Exercise-specific detector implementations with pluggable architecture
- State machine-based tracking with anti-jitter stabilization

✅ **Form Feedback & Coaching**
- Multi-signal form analysis (depth, lockout, core alignment, etc.)
- Weighted scoring system with severity levels (INFO/WARNING/CRITICAL)
- Real-time HUD displaying form health scores and coaching cues
- Adaptive baseline calibration for personalized assessment

✅ **Robust Pose Tracking**
- ML Kit Pose Detection with confidence validation
- Temporal smoothing to handle estimation jitter
- Graceful handling of partial occlusion
- Support for both front and back cameras

## Architecture

The app uses a modular detector architecture:
- **ExerciseAnalyzer**: Orchestrates pose analysis and delegates to exercise-specific detectors
- **ExerciseDetector**: Interface for modular exercise implementations
  - `PushupDetector`: Tracks depth, elbow lockout, and core stability
  - `SquatDetector`: Monitors hip depth, knee flexion, and torso angle
- **FormFeedback**: Rich analysis model with weighted signals and overall score
- **Compose HUD**: Real-time display of form health and coaching feedback

See [EXERCISE_TRACKING_FEATURES.md](EXERCISE_TRACKING_FEATURES.md) for detailed architecture documentation.

## Next Steps

Potential future enhancements:
1. Add more exercises (lunges, planks, burpees)
2. Workout session tracking and history
3. Adaptive coaching based on historical performance
4. Social sharing and challenges

