# Pose Tracker - Real-time Skeleton Overlay App

A minimal Android application that uses ML Kit Pose Detection to display a live skeleton overlay on camera preview. This app demonstrates real-time pose detection with visual feedback.

## Features

- **Real-time Pose Detection**: Detects body landmarks using ML Kit Pose Detection
- **Skeleton Overlay**: Visual overlay showing detected pose landmarks and connections
- **Camera Switching**: Support for both front and back cameras
- **Optimized Performance**: Low-latency detection optimized for real-time tracking
- **Clean UI**: Minimal interface focused on pose visualization

## How It Works

The app uses ML Kit's Pose Detection to identify key body landmarks such as shoulders, elbows, wrists, hips, knees, and ankles. These landmarks are displayed as colored circles connected by lines to form a skeleton overlay:

- **Yellow circles**: Head landmarks (nose, eyes, ears)
- **Green circles**: Upper body landmarks (shoulders, elbows, wrists, hips)
- **Cyan circles**: Lower body landmarks (knees, ankles, heels, feet)
- **Blue lines**: Connections between landmarks showing body structure

## Requirements

- Android device running Android 7.0 (API level 24) or higher
- Camera permission enabled
- Physical device (not an emulator) for camera functionality

## Usage Instructions

1. **Grant Permissions**: Allow camera permissions when prompted
2. **View Pose**: Point the camera at a person to see the skeleton overlay
3. **Switch Camera**: Tap the camera button to switch between front and back cameras

## Technical Details

Built with:
- **Kotlin** and **Jetpack Compose** for UI
- **CameraX API** for camera access
- **ML Kit Pose Detection** for body landmark detection
- **Coroutines and Flows** for asynchronous processing
- **Material 3** components for modern UI

The pose detection pipeline:
- Captures frames from camera at ~15 FPS
- Processes frames with ML Kit Pose Detection
- Transforms coordinates to align with camera preview
- Renders landmarks and connections on overlay

## Development Setup

1. Clone the repository
2. Open in Android Studio (Arctic Fox or newer)
3. Connect an Android device with USB debugging enabled
4. Build and run the app

## Performance Considerations

The app includes optimizations for real-time performance:
- Frame throttling to ~15 FPS to prevent overload
- Backpressure strategy to drop frames when processing falls behind
- Background processing for pose detection
- Adaptive confidence thresholds for different body parts

## License

[Insert License Information Here]

## Acknowledgments

- Google ML Kit for pose detection capabilities
- Android Jetpack libraries
