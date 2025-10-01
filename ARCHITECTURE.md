# Simplified Pose Tracker Architecture

## Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                         MainActivity                         │
│                    (Application Entry)                       │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                     PushUpCameraScreen                       │
│                   (Main Composable UI)                       │
├─────────────────────────────────────────────────────────────┤
│  • Permission handling                                       │
│  • Camera preview                                            │
│  • Pose overlay rendering                                    │
│  • Camera toggle button                                      │
└──────────┬──────────────────────┬──────────────┬────────────┘
           │                      │              │
           ▼                      ▼              ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐
│CameraPermission  │  │  CameraBinder    │  │ PoseOverlay  │
│   (Composable)   │  │   (CameraX)      │  │ (Composable) │
└──────────────────┘  └────────┬─────────┘  └──────────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │   ImageAnalyzer      │
                    │  (Frame Processor)   │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ PoseDetectorClient   │
                    │   (ML Kit Wrapper)   │
                    └──────────────────────┘
```

## Data Flow

```
Camera Frames
    │
    ▼
ImageAnalyzer (throttle to ~15 FPS)
    │
    ▼
PoseDetectorClient (ML Kit)
    │
    ▼
PoseDetectionResult {pose, imageWidth, imageHeight}
    │
    ▼
PoseOverlay (renders skeleton)
```

## Key Components

### UI Layer
- **MainActivity**: Application entry point, sets up Compose theme
- **PushUpCameraScreen**: Main screen with camera preview and pose overlay
- **CameraPermission**: Permission request and denial UI
- **PoseOverlay**: Draws skeleton landmarks and connections

### Camera Layer
- **CameraBinder**: CameraX lifecycle management and binding
- **ImageAnalyzer**: Processes camera frames at controlled rate

### ML Layer
- **PoseDetectorClient**: Wraps ML Kit pose detection API
- **PoseDetectionResult**: Data class holding pose and image dimensions

## Pose Visualization

The skeleton overlay uses color-coded landmarks:

- **Yellow (6px)**: Head landmarks (nose, eyes, ears)
  - Confidence threshold: >0.6
- **Green (8px)**: Upper body (shoulders, elbows, wrists, hips)
  - Confidence threshold: >0.5
- **Cyan (7px)**: Lower body (knees, ankles, heels, feet)
  - Confidence threshold: >0.3
- **Blue lines (3px)**: Connections between landmarks

## Performance Optimizations

1. **Frame Throttling**: 15 FPS target (66ms between frames)
2. **Backpressure Strategy**: KEEP_ONLY_LATEST drops old frames
3. **Background Processing**: All ML processing off main thread
4. **Processing Guards**: Prevents concurrent detections
5. **Confidence Filtering**: Adaptive thresholds for different body parts

## Coordinate Transformation

Camera coordinates are transformed for proper overlay alignment:
- Scale factors: `scaleX = canvasWidth / imageWidth`
- Mirror for front camera: `x = imageWidth - landmark.x`
- Direct mapping for back camera: `x = landmark.x`
