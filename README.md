# PushTrack - Push-Up Counter App

A minimal Android app that provides a camera preview foundation for counting push-ups using computer vision.

## Prerequisites

- **Android Studio**: Latest stable version (Flamingo or newer)
- **Physical Android Device**: Camera functionality requires a real device (emulator won't work properly)
- **USB Debugging**: Enable Developer Options and USB Debugging on your device
- **Android SDK**: API level 24 (Android 7.0) or higher

## How to Build and Run

### 1. Setup Development Environment

1. Install Android Studio from [developer.android.com](https://developer.android.com/studio)
2. Open Android Studio and install the latest Android SDK
3. Enable Developer Options on your Android device:
   - Go to **Settings > About phone**
   - Tap **Build number** 7 times
   - Go back to **Settings > Developer options**
   - Enable **USB debugging**

### 2. Clone and Open Project

```bash
git clone https://github.com/grloper/pushup-counter-video.git
cd pushup-counter-video
```

Open the project in Android Studio:
- Launch Android Studio
- Select "Open an Existing Project"
- Navigate to the cloned directory and select it

### 3. Build the Project

1. Wait for Gradle sync to complete
2. Build the project: **Build > Make Project** (Ctrl+F9)
3. Resolve any build issues if they occur

### 4. Run on Device

1. Connect your Android device via USB
2. Trust the computer when prompted on your device
3. Select your device in the device dropdown in Android Studio
4. Click the **Run** button (green play icon) or press Shift+F10

## App Features

### Current Functionality
- **Camera Preview**: Full-screen live camera feed using CameraX
- **Permission Handling**: Requests camera permission at runtime
- **Permission Denial UI**: Guides users to app settings if permission is denied
- **Overlay UI**: Shows "Reps: 0" placeholder for future push-up counting
- **Lifecycle Management**: Camera properly bound to activity lifecycle

### Camera Permission

The app will request camera permission when first launched. If you deny the permission:
1. The app will show a helpful message
2. Click "Open Settings" to go to app permissions
3. Enable Camera permission
4. Return to the app

**Note**: Camera preview will not work on Android emulators. You must use a physical device.

## Project Structure

```
app/src/main/java/com/grloepr/pushtrack/
├── MainActivity.kt                    # Main activity entry point
├── ui/
│   ├── screen/
│   │   └── PushUpCameraScreen.kt     # Main camera preview composable
│   └── theme/                        # Material Design theming
├── camera/
│   └── CameraBinder.kt               # CameraX provider and binding logic
└── permission/
    └── CameraPermission.kt           # Camera permission handling composables
```

## Troubleshooting

### No Camera Preview
- Ensure you're running on a physical device (not emulator)
- Check that camera permission is granted in device settings
- Try rotating the device to landscape and back to portrait

### Build Errors
- Make sure you have the latest Android SDK installed
- Try **Build > Clean Project** then **Build > Rebuild Project**
- Check that your device meets minimum API level requirements (API 24+)

### Permission Issues
- Go to **Settings > Apps > PushTrack > Permissions**
- Ensure Camera permission is enabled
- If the app doesn't appear in settings, try uninstalling and reinstalling

### Device Not Detected
- Enable USB debugging in Developer Options
- Try different USB cable or port
- Install device-specific USB drivers if needed

## Development Notes

This is the foundation layer for push-up counting functionality. Future features will include:
- ML Kit Pose Detection integration
- Push-up counting algorithm
- Workout tracking and history
- Enhanced UI and calibration options

## Technical Details

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 14)
- **CameraX Version**: 1.3.4
- **Compose BOM**: 2024.09.00
- **Architecture**: Single Activity with Jetpack Compose UI