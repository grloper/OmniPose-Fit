# Modular Exercise Detection Implementation

## Overview
This implementation adds modular exercise detection capabilities to the PushTrack app, supporting push-ups, pull-ups, and squats with a strategy-based architecture that ensures O(1) complexity per frame.

## Architecture

### Core Components

#### 1. ExerciseDetector Interface
- **Purpose**: Defines the contract for all exercise detection implementations
- **Key Methods**:
  - `processPose(pose: Pose)`: Process a pose frame in O(1) time
  - `reset()`: Reset detector state
  - `getRepCount()`: Get current repetition count
  - `getCurrentPhase()`: Get current exercise phase

#### 2. BaseExerciseDetector Abstract Class
- **Purpose**: Provides template method implementation ensuring O(1) complexity
- **Features**:
  - Template method pattern for consistent behavior
  - Fixed-size velocity tracking
  - Adaptive threshold calculation based on movement speed
  - Position count management with confirmation logic

#### 3. Exercise-Specific Detectors

##### PushUpDetector
- **Primary Signal**: Average elbow angle (shoulder-elbow-wrist)
- **Thresholds**: 
  - Down: < 90° (arms bent)
  - Up: > 160° (arms extended)
- **Detection Method**: "elbow_angle"

##### PullUpDetector  
- **Primary Signal**: Average elbow angle
- **Secondary Signal**: Head-to-hands vertical distance
- **Thresholds**:
  - Up (pulled): < 60° elbow angle OR head close to hands
  - Down (hanging): > 140° elbow angle OR head far from hands
- **Detection Methods**: "elbow_angle" or "head_hands_distance"

##### SquatDetector
- **Primary Signal**: Average knee angle (hip-knee-ankle)
- **Secondary Signal**: Normalized hip height
- **Thresholds**:
  - Down (squatting): < 110° knee angle OR low hip height
  - Up (standing): > 160° knee angle OR high hip height
- **Detection Methods**: "knee_angle" or "hip_height"

### Utility Classes

#### AngleCalculator
- **Purpose**: O(1) angle calculations between pose landmarks
- **Key Functions**:
  - `calculateElbowAngle(pose, isLeftArm)`: Single arm elbow angle
  - `calculateAverageElbowAngle(pose)`: Average of both arms
  - `calculateKneeAngle(pose, isLeftLeg)`: Single leg knee angle
  - `calculateAverageKneeAngle(pose)`: Average of both legs
  - `calculateHipAngle(pose, isLeftSide)`: Hip angle for torso analysis

#### VelocityTracker
- **Purpose**: O(1) movement velocity analysis using fixed-size circular buffer
- **Features**:
  - Fixed buffer size (5 samples by default)
  - Circular buffer prevents memory growth
  - Adaptive threshold calculation based on velocity
  - Fast/slow movement detection

#### EnhancedPostureAnalyzer
- **Purpose**: Multi-exercise form analysis with O(1) complexity
- **Features**:
  - Exercise-specific form quality scoring
  - Fixed-size quality history buffer (10 samples)
  - Feedback generation with cooldown periods
  - Support for push-ups, pull-ups, and squats

## O(1) Complexity Guarantees

### Key Optimizations

1. **Fixed-Size Buffers**: All tracking uses circular buffers with fixed maximum sizes
2. **No Growing Collections**: Position counts, velocity samples, and quality history use bounded storage
3. **Constant-Time Operations**: All angle calculations and state updates run in O(1)
4. **Template Method**: Consistent processing workflow prevents complexity variations

### Specific O(1) Implementations

- **VelocityTracker**: 5-sample circular buffer
- **EnhancedPostureAnalyzer**: 10-sample quality buffer
- **Position Tracking**: Simple up/down counters, no historical storage
- **Angle Calculations**: Direct vector math, no iterative processes

## Exercise Detection Logic

### Push-Up Detection
```
1. Calculate average elbow angle from both arms
2. Track angle velocity for adaptive thresholds
3. Determine phase: DOWN (<90°), UP (>160°), TRANSITIONING
4. Confirm phase with adaptive threshold based on movement speed
5. Count rep on DOWN -> UP transition
```

### Pull-Up Detection
```
1. Calculate average elbow angle (primary signal)
2. Calculate head-to-hands distance (secondary signal)
3. Use elbow angle if available, otherwise fall back to distance
4. Determine phase: UP (<60° OR close distance), DOWN (>140° OR far distance)
5. Count rep on DOWN -> UP transition
```

### Squat Detection
```
1. Calculate average knee angle (primary signal)
2. Track normalized hip height (secondary signal)
3. Use knee angle if available, otherwise fall back to hip height
4. Determine phase: DOWN (<110° OR low hip), UP (>160° OR high hip)
5. Count rep on DOWN -> UP transition
```

## Factory Pattern

### ExerciseDetectorFactory
- **Purpose**: Centralized creation of exercise detectors
- **Benefits**:
  - Easy extension for new exercise types
  - Consistent detector configuration
  - Type-safe detector creation

## Testing

### Unit Tests Provided
- **PullUpDetectorTest**: Comprehensive testing of pull-up detection logic
- **SquatDetectorTest**: Complete squat detection verification
- **AngleCalculatorTest**: Utility class testing with mock poses

### Test Coverage
- Initial state verification
- Phase detection accuracy
- Rep counting logic
- Reset functionality
- Missing landmark handling
- Multi-signal detection fallbacks
- Confidence calculation

## Integration

### Using the Modular Detectors

```kotlin
// Create detector for specific exercise
val detector = ExerciseDetectorFactory.createDetector(ExerciseType.PUSH_UP)

// Process pose frames
detector.processPose(pose)

// Get current state
val currentCount = detector.getRepCount()
val currentPhase = detector.getCurrentPhase()
val state = detector.state.value

// Reset when needed
detector.reset()
```

### Switching Exercise Types

```kotlin
// Switch to different exercise
val pullUpDetector = ExerciseDetectorFactory.createDetector(ExerciseType.PULL_UP)
val squatDetector = ExerciseDetectorFactory.createDetector(ExerciseType.SQUAT)
```

## Performance Characteristics

- **Memory Usage**: Bounded by fixed-size buffers
- **CPU Usage**: O(1) per frame processing
- **Latency**: Adaptive thresholds reduce confirmation delay for fast movements
- **Accuracy**: Multi-signal detection with confidence scoring

## Future Extensions

To add a new exercise type:

1. Add to `ExerciseType` enum
2. Create new detector extending `BaseExerciseDetector`
3. Implement abstract methods:
   - `calculatePrimaryAngle(pose)`
   - `determinePhase(pose, primaryAngle)`
   - `calculateConfidence(pose, primaryAngle)`
   - `getDetectionMethod()`
4. Add to `ExerciseDetectorFactory`
5. Add support in `EnhancedPostureAnalyzer`
6. Create unit tests

The modular architecture ensures that adding new exercises doesn't affect existing detection logic or performance characteristics.