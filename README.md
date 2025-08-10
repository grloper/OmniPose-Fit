# PushTrack - Smart Push-Up Counter App

PushTrack is an Android application that uses computer vision and ML Kit to count push-ups in real-time. The app leverages the device's camera and Google's ML Kit Pose Detection to accurately track body movements and count push-up repetitions.

## Features

- **Real-time Push-Up Counting**: Automatically counts push-ups as you perform them
- **Visual Pose Tracking**: Optional skeleton visualization to show detected body landmarks
- **Camera Switching**: Support for both front and back cameras
- **Optimized Performance**: Low-latency detection optimized for real-time tracking
- **Debug Mode**: Toggle skeleton visualization and debugging information
- **Modern UI**: Clean, intuitive interface with animated counters and feedback

## How It Works

PushTrack uses ML Kit's Pose Detection to identify key body landmarks such as shoulders, elbows, and wrists. The app then analyzes the relative positions and angles between these landmarks to determine when a complete push-up has been performed.

The detection algorithm is optimized for:
- Ground position push-ups (phone placed in front of user)
- Different speeds of movement
- Various lighting conditions
- Multiple body types and positions

## Requirements

- Android device running Android 7.0 (API level 24) or higher
- Camera permission enabled
- Physical device (not an emulator) for camera functionality

## Usage Instructions

1. **Setup**: Place your phone on the ground in front of you where it can see your upper body
2. **Grant Permissions**: Allow camera permissions when prompted
3. **Position Yourself**: Get into push-up position facing the phone
4. **Start Exercising**: The app will automatically count your push-ups
5. **Debug Mode**: Tap the "DEBUG" button to show/hide skeleton visualization
6. **Switch Camera**: Use the camera button to switch between front and back cameras
7. **Reset Counter**: Press the reset button to start counting from zero

## Technical Details

PushTrack is built with:
- Kotlin and Jetpack Compose for UI
- CameraX API for camera access
- ML Kit for pose detection
- Coroutines and Flows for asynchronous processing
- Material 3 components for modern UI

The push-up detection algorithm uses multiple signals for accuracy:
- Elbow angle tracking
- Head height position
- Shoulder width changes
- Movement velocity analysis

## Development Setup

1. Clone the repository
2. Open in Android Studio (Arctic Fox or newer)
3. Connect an Android device with USB debugging enabled
4. Build and run the app

## Performance Considerations

The app includes several optimizations for real-time performance:
- Frame skipping for optimal processing
- Low-resolution image analysis
- Intelligent landmark filtering
- Adaptive thresholds based on movement speed

## License

[Insert License Information Here]

## Acknowledgments

- Google ML Kit for pose detection capabilities
- Android Jetpack libraries