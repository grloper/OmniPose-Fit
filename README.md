# PushTrack - Smart Push-Up Counter App

PushTrack is an Android application that uses computer vision and ML Kit to count push-ups in real-time. The app leverages the device's camera and Google's ML Kit Pose Detection to accurately track body movements and count push-up repetitions.

## Features

- **Multi-Exercise Detection**: Automatically detects and counts push-ups, pull-ups, and squats
- **Smart Modes**: Auto-detection mode that automatically identifies exercise type based on body position
- **Manual Exercise Selection**: Choose specific exercise types with visual feedback
- **Modular Architecture**: Extensible design for easy addition of new exercise types
- **Real-time Counting**: O(1) complexity detection algorithms optimized for performance
- **Visual Pose Tracking**: Optional skeleton visualization to show detected body landmarks
- **Form Quality Analysis**: Real-time feedback on exercise form and posture
- **Voice Feedback**: Optional voice announcements for rep counts and form guidance
- **Camera Switching**: Support for both front and back cameras
- **Optimized Performance**: Low-latency detection optimized for real-time tracking
- **Debug Mode**: Toggle skeleton visualization and debugging information
- **Modern UI**: Clean, intuitive interface with animated counters and feedback

## How It Works

PushTrack uses ML Kit's Pose Detection to identify key body landmarks such as shoulders, elbows, wrists, hips, knees, and ankles. The app features a modular detection system that analyzes the relative positions and angles between these landmarks to determine when complete repetitions have been performed for different exercise types.

### Supported Exercises

- **Push-ups**: Uses elbow angle analysis to detect up/down movements
- **Pull-ups**: Combines elbow angle and head-to-hands distance for accurate detection
- **Squats**: Utilizes knee angle and hip height tracking for rep counting

### Modular Architecture

The detection system is built on a strategy pattern with:
- **ExerciseDetector Interface**: Common contract for all exercise detectors
- **BaseExerciseDetector**: Abstract class providing O(1) complexity template method
- **Specific Detectors**: PushUpDetector, PullUpDetector, SquatDetector
- **Utility Classes**: AngleCalculator, VelocityTracker, EnhancedPostureAnalyzer

The detection algorithms are optimized for:
- **O(1) Complexity**: Fixed-time processing per frame regardless of exercise duration
- **Multi-signal Detection**: Uses multiple pose landmarks for increased accuracy
- **Adaptive Thresholds**: Adjusts detection sensitivity based on movement speed
- **Various Body Types**: Works with different heights, builds, and exercise positions

## Requirements

- Android device running Android 7.0 (API level 24) or higher
- Camera permission enabled
- Physical device (not an emulator) for camera functionality

## Usage Instructions

1. **Setup**: Place your phone where it can see your upper body (for push-ups/pull-ups) or full body (for squats)
2. **Grant Permissions**: Allow camera permissions when prompted
3. **Exercise Selection**: 
   - **Smart Mode**: Enable smart mode for automatic exercise type detection
   - **Manual Mode**: Tap the exercise selector button to choose specific exercise types
4. **Start Exercising**: Begin your workout - the app will automatically detect and count reps
5. **Monitor Form**: Watch for real-time form feedback and quality indicators
6. **Voice Feedback**: Enable voice announcements in settings for audio guidance
4. **Position Yourself**: Get into the starting position for your chosen exercise
5. **Start Exercising**: The app will automatically count your repetitions
6. **Debug Mode**: Tap the "DEBUG" button to show/hide skeleton visualization
7. **Switch Camera**: Use the camera button to switch between front and back cameras
8. **Reset Counter**: Press the reset button to start counting from zero

## Technical Details

PushTrack is built with:
- Kotlin and Jetpack Compose for UI
- CameraX API for camera access
- ML Kit for pose detection
- Coroutines and Flows for asynchronous processing
- Material 3 components for modern UI

The exercise detection algorithms use multiple signals for accuracy:
- **Push-ups**: Elbow angle tracking, body alignment analysis
- **Pull-ups**: Elbow angle tracking, head-to-hands distance measurement
- **Squats**: Knee angle tracking, hip height analysis, torso alignment
- **Universal**: Movement velocity analysis, adaptive thresholds, form quality scoring

## Architecture

### Detection System

The modular detection system consists of:

- **ExerciseDetector Interface**: Defines the contract for all exercise detectors
- **BaseExerciseDetector**: Abstract class implementing O(1) complexity template method
- **Exercise-Specific Detectors**:
  - `PushUpDetector`: Elbow angle-based detection
  - `PullUpDetector`: Combined elbow angle and head-position detection
  - `SquatDetector`: Knee angle and hip height detection
- **Utility Classes**:
  - `AngleCalculator`: O(1) angle calculations between pose landmarks
  - `VelocityTracker`: Fixed-size circular buffer for movement speed analysis
  - `EnhancedPostureAnalyzer`: Multi-exercise form analysis with O(1) complexity

### Key Design Principles

- **O(1) Complexity**: All per-frame operations run in constant time
- **Strategy Pattern**: Easy addition of new exercise types
- **Fixed-Size Buffers**: No growing state that could impact performance
- **Template Method**: Consistent detection workflow across all exercises

## Development Setup

1. Clone the repository
2. Open in Android Studio (Arctic Fox or newer)
3. Connect an Android device with USB debugging enabled
4. Build and run the app

## Performance Considerations

The app includes several optimizations for real-time performance:
- **O(1) Complexity**: Detection algorithms run in constant time per frame
- **Frame Throttling**: Optimal processing rate to prevent overload
- **Fixed-Size Buffers**: Circular buffers prevent memory growth over time
- **Adaptive Thresholds**: Dynamic adjustment based on movement speed
- **Multi-signal Detection**: Fallback detection methods for increased reliability
- **Efficient Angle Calculations**: Optimized vector math for pose analysis

## License

[Insert License Information Here]

## Acknowledgments

- Google ML Kit for pose detection capabilities
- Android Jetpack libraries