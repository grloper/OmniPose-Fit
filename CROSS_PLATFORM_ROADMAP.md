# Cross-Platform Architecture Roadmap

## Overview
This document outlines the architecture and implementation plan for transforming the current Android-only push-up counter into a modern, cross-platform, high-performance application as specified in issue #8.

## Current Implementation Status

### ✅ Completed Features
- **Android ML Kit Integration**: Working pose detection with real-time landmark overlay
- **Push-Up Counting Logic**: Robust state machine with quality assessment
- **Performance Optimizations**: Adaptive FPS, frame throttling, hardware acceleration
- **Modular Architecture**: Separated concerns for camera, ML, counting, and UI
- **Testing Infrastructure**: Unit tests for counting logic with mock pose data

### 🏗️ Architecture Foundation

#### Current Module Structure
```
app/src/main/java/com/grloepr/pushtrack/
├── analysis/          # Frame processing and performance optimization
├── camera/           # CameraX integration and lifecycle management  
├── counting/         # Push-up detection and state machine logic
├── pose/            # ML Kit pose detection client
├── permission/      # Camera permission handling
└── ui/             # Jetpack Compose UI components
    ├── overlay/    # Pose visualization overlays
    ├── screen/     # Main camera screen
    └── theme/      # Material Design theming
```

## Cross-Platform Migration Strategy

### Phase 1: Core Logic Extraction (Next Step)
Extract platform-agnostic business logic into shared modules:

#### 1.1 Shared Business Logic Module
```
shared/
├── counting/
│   ├── PushUpCounter.kt         # State machine (no Android deps)
│   ├── PoseAnalyzer.kt          # Geometry calculations
│   └── WorkoutSession.kt        # Session tracking
├── models/
│   ├── PoseData.kt             # Platform-agnostic pose representation
│   ├── WorkoutMetrics.kt       # Performance and workout data
│   └── UserPreferences.kt      # Settings and calibration
└── utils/
    ├── MathUtils.kt            # Angle calculations, smoothing
    └── ValidationUtils.kt      # Input validation
```

#### 1.2 Platform Abstraction Layer
```
shared/
└── platform/
    ├── PoseDetector.kt         # Interface for pose detection
    ├── CameraProvider.kt       # Interface for camera handling  
    ├── StorageProvider.kt      # Interface for data persistence
    └── PerformanceMonitor.kt   # Interface for metrics collection
```

### Phase 2: Technology Stack Selection

#### Option A: Flutter + TensorFlow Lite (Recommended)
**Advantages:**
- Single codebase for Android + iOS
- Excellent performance with tflite_flutter
- Rich ecosystem and tooling
- Proven camera integration

**Implementation:**
```dart
// Flutter structure
lib/
├── core/
│   ├── ml/                     # TFLite model integration
│   ├── camera/                 # Camera plugin integration
│   └── counting/               # Dart port of counting logic
├── features/
│   ├── workout/                # Workout screens and logic
│   └── settings/               # Configuration and calibration
└── shared/
    ├── models/                 # Data models
    ├── widgets/                # Reusable UI components
    └── utils/                  # Helper functions
```

**Key Dependencies:**
- `camera: ^0.10.5` - Camera integration
- `tflite_flutter: ^0.10.4` - TensorFlow Lite
- `provider: ^6.1.1` - State management
- `flutter_riverpod: ^2.4.9` - Advanced state management

#### Option B: Kotlin Multiplatform Mobile (KMM)
**Advantages:**
- Leverage existing Kotlin codebase
- Native UI performance
- Shared business logic, platform-specific UI

**Implementation:**
```
shared/
├── commonMain/kotlin/
│   ├── counting/              # Push-up counting logic
│   ├── ml/                    # ML model interfaces
│   └── data/                  # Data models and repositories
├── androidMain/kotlin/
│   ├── ml/AndroidPoseDetector.kt    # ML Kit implementation
│   └── camera/AndroidCamera.kt     # CameraX implementation
└── iosMain/kotlin/
    ├── ml/IOSPoseDetector.kt        # Core ML implementation
    └── camera/IOSCamera.kt          # AVFoundation implementation

androidApp/                    # Android-specific UI (Compose)
iosApp/                       # iOS-specific UI (SwiftUI)
```

### Phase 3: ML Model Optimization

#### Current: ML Kit Pose Detection
- **Pros**: Easy integration, good accuracy
- **Cons**: Platform-specific, limited optimization control

#### Target: TensorFlow Lite with Custom Pipeline
```
models/
├── movenet_lightning_int8.tflite    # Quantized MoveNet for speed
├── movenet_thunder_fp16.tflite      # Higher accuracy model
└── mediapipe_pose_landmarks.tflite  # Alternative model option
```

**Performance Targets:**
- **≥30 FPS** on mid-range 2020+ devices
- **≤33ms** inference time per frame
- **<100MB** peak memory usage
- **GPU acceleration** on supported devices

#### Implementation Strategy:
1. **Model Conversion**: Convert MoveNet/MediaPipe to TFLite format
2. **Quantization**: Apply INT8 quantization for mobile optimization
3. **Delegate Integration**: 
   - Android: GPU delegate + NNAPI
   - iOS: Core ML delegate + Metal Performance Shaders
4. **Preprocessing Optimization**: 
   - GPU-based YUV→RGB conversion
   - Resize operations on GPU/Metal
   - Buffer reuse to minimize allocations

### Phase 4: Performance Optimization Features

#### 4.1 Advanced Frame Processing
```kotlin
// Shared performance module
class AdvancedFrameProcessor {
    // Zero-copy buffer management
    private val bufferPool = RingBufferPool<ImageBuffer>()
    
    // Adaptive quality scaling
    private val qualityScaler = AdaptiveQualityScaler()
    
    // GPU preprocessing pipeline
    private val gpuPreprocessor = GPUImagePreprocessor()
}
```

#### 4.2 Smart Resource Management
- **Dynamic Model Loading**: Load appropriate model based on device capabilities
- **Thermal Throttling**: Reduce processing when device gets hot
- **Battery Optimization**: Adapt frame rate based on battery level
- **Memory Management**: Aggressive buffer reuse and garbage collection optimization

### Phase 5: Cross-Platform UI Implementation

#### Flutter Implementation
```dart
// Cross-platform camera screen
class PushUpCameraScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: Stack(
        children: [
          // Camera preview with platform camera plugin
          CameraPreview(controller: ref.watch(cameraProvider)),
          
          // Pose overlay (custom painter)
          PoseOverlay(landmarks: ref.watch(poseProvider)),
          
          // Push-up counter overlay
          PushUpCounterOverlay(
            count: ref.watch(pushUpCounterProvider),
            state: ref.watch(pushUpStateProvider),
          ),
        ],
      ),
    );
  }
}
```

#### KMM Implementation
**Android (Compose):**
```kotlin
@Composable
fun PushUpCameraScreen(viewModel: PushUpViewModel) {
    // Existing Compose implementation with shared logic
}
```

**iOS (SwiftUI):**
```swift
struct PushUpCameraScreen: View {
    @StateObject private var viewModel = PushUpViewModel()
    
    var body: some View {
        ZStack {
            CameraPreview(session: viewModel.cameraSession)
            PoseOverlay(landmarks: viewModel.poseLandmarks)
            PushUpCounterOverlay(count: viewModel.repCount)
        }
    }
}
```

## Migration Timeline

### Week 1-2: Foundation
- [x] ✅ Extract business logic into platform-agnostic modules
- [x] ✅ Implement robust push-up counting state machine
- [x] ✅ Add comprehensive testing infrastructure
- [x] ✅ Performance optimization for current Android implementation

### Week 3-4: Cross-Platform Setup
- [ ] 🎯 Set up Flutter project structure OR KMM project structure
- [ ] 🎯 Port counting logic to chosen cross-platform framework
- [ ] 🎯 Implement platform abstraction interfaces
- [ ] 🎯 Set up CI/CD for cross-platform builds

### Week 5-6: ML Integration
- [ ] 📋 Convert and optimize TensorFlow Lite models
- [ ] 📋 Implement TFLite integration for both platforms
- [ ] 📋 Add GPU/NNAPI/Core ML delegate support
- [ ] 📋 Performance testing and optimization

### Week 7-8: UI Implementation
- [ ] 📋 Port camera integration to cross-platform
- [ ] 📋 Implement pose overlay rendering
- [ ] 📋 Add cross-platform UI components
- [ ] 📋 Implement settings and calibration screens

### Week 9-10: Advanced Features
- [ ] 📋 Add workout session tracking
- [ ] 📋 Implement workout history and analytics
- [ ] 📋 Add user preferences and calibration
- [ ] 📋 Performance monitoring and adaptive optimization

### Week 11-12: Testing & Polish
- [ ] 📋 Comprehensive testing on multiple devices
- [ ] 📋 Performance optimization and profiling
- [ ] 📋 UI/UX polish and accessibility
- [ ] 📋 Documentation and deployment preparation

## Technical Requirements Validation

### ✅ Real-time ML (≥ 30 FPS)
- Current implementation achieves adaptive 15-30 FPS
- TensorFlow Lite optimization will target 30+ FPS consistently

### ✅ Accurate counting (≥ 95% accuracy)
- Robust state machine with hysteresis and smoothing
- Quality assessment prevents false positives
- Comprehensive test coverage with mock data

### ✅ Stable performance without UI lag
- Background processing with coroutines
- Adaptive frame throttling based on performance
- GPU acceleration and hardware optimization

### ✅ Modular architecture for future features
- Clean separation of concerns
- Platform abstraction layer
- Extensible workout tracking and analytics foundation

## Risk Mitigation

### Technical Risks
1. **Performance on older devices**: Adaptive quality scaling and fallback models
2. **Cross-platform ML complexity**: Thorough testing and platform-specific optimizations
3. **Camera integration challenges**: Robust error handling and fallback mechanisms

### Resource Risks
1. **Development timeline**: Phased approach with working milestones
2. **Testing coverage**: Automated testing on device farms
3. **Platform expertise**: Leverage community resources and documentation

## Success Metrics

### Performance KPIs
- **Inference Time**: ≤33ms per frame (30+ FPS)
- **Memory Usage**: <100MB peak memory
- **Battery Impact**: <10% additional drain during 30-min workout
- **Accuracy**: ≥95% rep counting accuracy on test dataset

### User Experience KPIs
- **App Launch Time**: <3 seconds to camera ready
- **UI Responsiveness**: <16ms frame time (60 FPS UI)
- **Stability**: <0.1% crash rate
- **Cross-Platform Parity**: Feature and performance consistency across platforms

This roadmap provides a clear path from the current Android implementation to a modern, cross-platform, high-performance push-up counter that meets all requirements specified in issue #8.