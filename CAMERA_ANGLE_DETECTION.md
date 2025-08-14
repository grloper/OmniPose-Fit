# Camera Angle Adaptive Push-Up Detection

## Problem Addressed
The push-up detection was failing when users placed their phone on the ground leaning against a wall (very low camera angle). The previous ground-level detection system assumed the camera was positioned at a higher angle looking down at the person.

## Solution: Camera Angle Detection System

### Core Innovation
**Automatic Camera Angle Classification**: The system now analyzes pose landmark patterns to determine camera positioning and adapts the detection method accordingly.

### Camera Angle Types
1. **HIGH_ANGLE**: Camera positioned above person (traditional setup)
2. **MID_ANGLE**: Camera at moderate angle  
3. **LOW_ANGLE**: Camera positioned at ground level (phone against wall)
4. **GROUND_LEVEL**: Camera essentially at same level as person
5. **UNKNOWN**: Angle not yet determined

### Detection Methods by Camera Angle

#### Low-Angle/Ground-Level Cameras
- **Movement-Based Detection**: Uses shoulder-to-wrist distance changes instead of absolute angles
- **Distance Pattern Analysis**: Tracks movement patterns that remain consistent regardless of camera perspective
- **Circular Buffer Tracking**: Efficient O(1) history tracking for movement patterns
- **Pseudo-Angle Mapping**: Converts distance measurements to angle-compatible values

#### High-Angle Cameras  
- **Traditional Elbow Angle Detection**: Uses ground-level reference system
- **Ground Contact Analysis**: Hand/foot positioning for context-aware thresholds
- **90+ Degree Thresholds**: When in ground contact, uses higher angle requirements

#### Mid-Angle Cameras
- **Balanced Approach**: Hybrid of movement and angle detection
- **Moderate Thresholds**: Balanced between low and high angle requirements

### Adaptive Threshold System

**Camera-Specific Thresholds**:
- Low-Angle: DOWN=70°+, UP=120°+ (movement-based pseudo-angles)
- High-Angle: DOWN=90°+, UP=135°+ (traditional elbow angles)  
- Mid-Angle: DOWN=80°+, UP=125°+ (balanced approach)

**Form Validation**:
- Low-Angle: Very forgiving torso requirements (+25° tolerance)
- High-Angle: Traditional torso validation
- Unknown: Conservative tolerances for reliability

### Technical Features

#### Camera Angle Detection Algorithm
```kotlin
// Analyzes body proportions and orientations
val torsoAspectRatio = torsoWidth / torsoHeight
val headToBodyRatio = headWristDistance / bodyHeight

// Indicators for low-angle camera:
// - Torso appears very wide (foreshortening)
// - Head appears close to hands
// - Body compressed vertically
// - Shoulders appear above hips in image
```

#### Movement-Based Detection
```kotlin
// Track shoulder-to-wrist distances over time
val leftDistance = sqrt((shoulder.x - wrist.x)² + (shoulder.y - wrist.y)²)
val rightDistance = sqrt((shoulder.x - wrist.x)² + (shoulder.y - wrist.y)²)

// Convert to pseudo-angles for compatibility
val normalizedDistance = (avgDistance - minDistance) / distanceRange
val pseudoAngle = 70f + (normalizedDistance * 100f)
```

#### Calibration System
- **10 frames** to establish camera angle with confidence scoring
- **50 frames** for threshold adaptation based on observed movement patterns
- **Automatic fallback** to hybrid detection if angle detection fails

### Performance Optimizations

1. **O(1) Complexity**: Circular buffers prevent memory growth
2. **Lazy Evaluation**: Camera angle detection only runs until established  
3. **Efficient Caching**: Reuses calculations across detection methods
4. **Graceful Degradation**: Multiple fallback paths ensure reliability

### Debug Information

Enhanced debug output includes:
- Camera angle classification and confidence
- Detection method being used (movement vs. traditional)
- Threshold values and adjustments
- Movement pattern analysis
- Form validation with camera-specific tolerances

## User Impact

✅ **Works with phone on ground against wall** - the exact scenario that was failing  
✅ **Automatic adaptation** - no user configuration required  
✅ **Maintains accuracy** for all existing camera positions  
✅ **Real-time feedback** showing detection status and method  
✅ **Improved reliability** across diverse usage scenarios  

## Testing Scenarios

The enhanced detector now handles:
1. Phone placed high above user (traditional)
2. Phone at moderate angles (typical selfie position)  
3. Phone on ground leaning against wall (problematic scenario)
4. Phone held at various angles during exercise
5. Mixed lighting and pose visibility conditions

## Backward Compatibility

- All existing functionality preserved
- Ground-level detection still available for high-angle cameras
- Existing thresholds used as fallbacks
- No breaking changes to the API