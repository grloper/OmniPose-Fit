# PushTrack - Smart Exercise Counter App

PushTrack is an advanced Android application that uses your device's camera and on-device machine learning to count exercise repetitions in real-time. It goes beyond simple counting by providing form analysis, voice feedback, and personalized calibration to enhance your workout experience.

## Features

- **Multi-Exercise Tracking**: Automatically counts Push-Ups, Squats, and Pull-Ups.
- **Real-time Rep Counting**: Accurately counts repetitions as you perform them.
- **Advanced Form Analysis**: Provides real-time feedback on your form, such as "Keep your back straight" or "Lower your body more".
- **Voice Feedback**: Announces rep counts and form corrections, so you don't have to look at the screen.
- **Personalized Calibration**: A dedicated mode to analyze your personal movement style and optimize the detection algorithm for you.
- **Workout Summary**: After your workout, view detailed statistics including total reps, average form quality, duration, and achievements.
- **Visual Pose Tracking**: Optional skeleton visualization to show detected body landmarks for debugging and analysis.
- **Modern UI**: Clean, intuitive interface with smooth animations, built with Jetpack Compose and Material 3.
- **Customizable Settings**: Control voice feedback, speech rate, UI elements, and more.

## How It Works

PushTrack uses ML Kit's Pose Detection to identify key body landmarks. The app then analyzes the relative positions and angles between these landmarks to determine when a complete repetition has been performed for the selected exercise.

The detection algorithm is optimized for:
- Different exercise types (Push-ups, Squats, Pull-ups)
- Various speeds of movement
- Different lighting conditions
- Multiple body types and positions

## Requirements

- Android device running Android 7.0 (API level 24) or higher
- Camera permission enabled
- A physical device is required for camera functionality (emulators are not supported).

## Usage Instructions

1. **Setup**: Place your phone where it can see your full body.
2. **Grant Permissions**: Allow camera permissions when prompted.
3. **Select Exercise**: Tap the exercise icon in the top right to choose your workout.
4. **Position Yourself**: Get into the starting position for the selected exercise.
5. **Start Exercising**: The app will automatically count your reps and provide feedback.
6. **View Summary**: When you're done, tap the summary button to see your workout results.
7. **Debug Mode**: Tap the "DEBUG" button to show/hide the skeleton visualization.
8. **Settings**: Use the settings button to toggle voice feedback and adjust speech rate.

## Technical Details

PushTrack is built with a modern Android tech stack:
- **UI**: Kotlin & Jetpack Compose for a declarative and responsive UI.
- **Camera**: CameraX API for robust camera access and lifecycle management.
- **ML**: Google's ML Kit for on-device, low-latency pose detection.
- **Architecture**: ViewModel, Coroutines, and Kotlin Flows for asynchronous processing and state management.
- **Database**: Room for storing calibration data and user preferences.
- **UI Components**: Material 3 for a modern look and feel.

The detection algorithm uses multiple signals for accuracy:
- **Primary Metrics**: Elbow angle for push-ups, knee angle for squats, etc.
- **Secondary Metrics**: Head height, shoulder width, and other landmark positions.
- **Movement Analysis**: Velocity and consistency checks to improve reliability.

## Development Setup

1. Clone the repository.
2. Open the project in Android Studio (Hedgehog or newer).
3. Connect an Android device with USB debugging enabled.
4. Build and run the app.

## Performance Considerations

The app includes several optimizations for real-time performance:
- **Image Analysis Strategy**: Processes only the latest camera frame to reduce latency.
- **Low-Resolution Analysis**: Uses a lower resolution for analysis to improve speed without sacrificing accuracy.
- **Intelligent Landmark Filtering**: Focuses only on the landmarks relevant to the current exercise.
- **Adaptive Thresholds**: Uses calibration data to adjust detection thresholds to your body.

## License

This project is licensed under the MIT License. See the [LICENSE.md](LICENSE.md) file for details.

## Acknowledgments

- Google for the powerful ML Kit and Jetpack libraries.
- The open-source community for their invaluable tools and libraries.