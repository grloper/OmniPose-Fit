# Exercise Tracking Features - Implementation Summary

## Overview
Added comprehensive exercise tracking capabilities to the pose detection app, including rep counting for three exercises: push-ups, squats, and pull-ups.

## Key Features Implemented

### 1. **Exercise Selection Screen**
- Beautiful card-based UI for selecting exercise type
- Three exercises available: Push-ups, Squats, Pull-ups
- Clean navigation flow

### 2. **Rep Counting System**
- Real-time rep detection using pose landmarks
- State machine-based tracking (Waiting → Down → Up → Rep counted)
- Intelligent angle and position calculations
- Debouncing to prevent false positives

### 3. **Exercise-Specific Detection Algorithms**

#### Push-ups
- **Detection Method**: Compares wrist positions to shoulder positions
- **Down Position**: Wrists below shoulders
- **Up Position**: Wrists at/near shoulder level
- **Optimal Camera**: Front camera (selfie mode)

#### Squats
- **Detection Method**: Tracks hip-to-knee distance
- **Down Position**: Hips below knees
- **Up Position**: Hips at/near knee level
- **Optimal Camera**: Side camera to see full body

#### Pull-ups
- **Detection Method**: Monitors chin position relative to shoulders
- **Up Position**: Chin above shoulders
- **Down Position**: Chin below shoulders
- **Optimal Camera**: Front camera

### 4. **Enhanced UI Components**

#### Rep Counter Display
- Large, visible rep count at top of screen
- Exercise name displayed
- Clean white card with shadow
- Real-time updates

#### Control Buttons
- **Camera Switch**: Toggle front/back camera
- **Reset Button**: Clear rep counter (red button)
- **Back Button**: Return to exercise selection

#### Form Feedback HUD
- Live "Form Health" bar with color-coded score
- Headlines surface the primary coaching cue in real time
- Shows up to three actionable signals (e.g., depth, lockout, core tightness)
- Automatically degrades when pose is lost to prompt re-alignment

### 5. **Camera Default**
- App starts in **FRONT CAMERA** mode for optimal selfie tracking
- Can switch to back camera if needed

## Architecture Improvements

### Detector Lifecycle

```
App Launch
    ↓
Exercise Selection (ExerciseSelectionScreen)
    ↓
ExerciseAnalyzer created with selected ExerciseType
    ↓
Detector instantiated (PushupDetector / SquatDetector / inline PullupDetector)
    ↓
┌─────────────────────────────────────────────────┐
│  Camera Frame Analysis Loop                     │
│                                                  │
│  1. MLKit Pose Detection → Pose object          │
│  2. ExerciseAnalyzer.analyzePose(pose)          │
│     ├─ hasCoreLandmarks() validation            │
│     ├─ detector.analyze(pose, timestamp)        │
│     │   ├─ Landmark extraction & smoothing      │
│     │   ├─ State machine evaluation             │
│     │   ├─ Rep completion detection              │
│     │   └─ Form signal computation               │
│     └─ processResult()                           │
│         ├─ Increment repCount if rep completed  │
│         └─ Emit ExerciseAnalysis                │
│                                                  │
│  3. UI updates via analysisListener callback     │
│     ├─ RepCounterCard shows repCount            │
│     └─ FormFeedbackPanel displays form          │
└─────────────────────────────────────────────────┘
    ↓
Reset button → detector.reset() → Clear state
    ↓
Back button → Navigate to ExerciseSelectionScreen
```

### Data Flow Architecture

```
┌──────────────────┐
│  CameraX Image   │
│   Analyzer       │
└────────┬─────────┘
         │
         ↓
┌────────────────────┐
│  PoseDetectorClient│  (MLKit Pose Detection)
└────────┬───────────┘
         │
         ↓ Pose
┌─────────────────────┐
│  ExerciseAnalyzer   │  (Orchestrator)
│  ┌─────────────┐    │
│  │  Detector   │────┼──→ DetectorResult {
│  │  (modular)  │    │       state: ExerciseState
│  └─────────────┘    │       repCompleted: Boolean
│                     │       formFeedback: FormFeedback {
│                     │         overallScore: Float
│                     │         headline: String
│                     │         signals: List<FormSignal>
│                     │       }
│                     │    }
└──────────┬──────────┘
           │
           ↓ ExerciseAnalysis
┌───────────────────────┐
│  PushUpCameraScreen   │  (Compose UI)
│  ┌──────────────────┐ │
│  │ FormFeedbackPanel│ │  ← Displays form health & signals
│  └──────────────────┘ │
│  ┌──────────────────┐ │
│  │ RepCounterCard   │ │  ← Shows rep count
│  └──────────────────┘ │
└───────────────────────┘
```

### Key Design Principles

1. **Single Responsibility**: Each detector owns its exercise-specific logic
2. **Pluggable Architecture**: New exercises require only a new `ExerciseDetector` implementation
3. **Temporal Smoothing**: All measurements use rolling averages to handle pose estimation jitter
4. **Weighted Scoring**: Form signals contribute proportionally to overall score based on importance
5. **Progressive Disclosure**: HUD shows most critical issues first (lowest score)

### New Files Created
1. `ExerciseType.kt` - Enum for exercise types
2. `ExerciseState.kt` - State machine states
3. `ExerciseAnalysis.kt` - Transport object for rep state + form feedback
4. `FormFeedback.kt` - Models overall score and detailed form signals
5. `MeasurementSmoother.kt` - Shared rolling-average utility
6. `PoseMath.kt` - Reusable pose landmark helpers
7. `exercise/detector/PushupDetector.kt` - Push-up specific heuristics
8. `exercise/detector/SquatDetector.kt` - Squat specific heuristics
9. `exercise/detector/ExerciseDetector.kt` - Detector abstraction contract
10. `ExerciseSelectionScreen.kt` - UI for exercise selection

### Modified Files
1. `ExerciseAnalyzer.kt` - Delegates to modular detectors and emits `ExerciseAnalysis`
2. `PushUpCameraScreen.kt` - Binds analysis stream, renders form HUD, resets cleanly
3. `EXERCISE_TRACKING_FEATURES.md` - Updated documentation

## Technical Highlights

### Detection Logic and Measurement Computation

#### Push-up Detection Algorithm

```kotlin
// 1. Extract key landmarks
val shoulderPoint = average(leftShoulder, rightShoulder)
val hipPoint = average(leftHip, rightHip)
val supportPoint = average(leftWrist, rightWrist)

// 2. Normalize gap measurement by torso length
val torsoLength = distance(shoulderPoint, hipPoint).coerceAtLeast(1.0)
val normalizedGap = ((supportPoint.y - shoulderPoint.y) / torsoLength).coerceIn(0.0, 2.0)
val smoothedGap = gapSmoother.add(normalizedGap)

// 3. Compute elbow angles
val elbowAngles = [
    calculateAngle(leftShoulder, leftElbow, leftWrist),
    calculateAngle(rightShoulder, rightElbow, rightWrist)
]
val smoothedElbow = elbowSmoother.add(average(elbowAngles))

// 4. State detection with dual criteria
val elbowDown = smoothedElbow < 116°
val elbowUp = smoothedElbow > 158°
val depthDown = (baseline - smoothedGap) > 0.12
val depthRecovered = smoothedGap > (baseline - 0.05)

// 5. Determine state (with fallback thresholds)
state = when {
    (elbowDown && (depthDown || fallbackDown)) -> ExerciseState.Down
    (elbowUp && (depthRecovered || fallbackUp)) -> ExerciseState.Up
    else -> ExerciseState.Waiting
}

// 6. Adaptive baseline calibration
if (state == Up) {
    baselineGap = 0.8 * existingBaseline + 0.2 * smoothedGap
}
```

**Key Innovations:**
- **Dual-criteria detection**: Both elbow angle AND depth must agree for state transition
- **Fallback thresholds**: Absolute gap values used when baseline unreliable
- **Adaptive baseline**: Recalibrates to athlete's natural range of motion
- **Torso normalization**: Makes gap measurement body-size independent

#### Squat Detection Algorithm

```kotlin
// 1. Extract and select best knee angle
val kneeAngles = [
    calculateAngle(leftHip, leftKnee, leftAnkle),
    calculateAngle(rightHip, rightKnee, rightAnkle)
]
val kneeConfidence = [
    landmarkSetConfidence(leftHip, leftKnee, leftAnkle),
    landmarkSetConfidence(rightHip, rightKnee, rightAnkle)
]
val selectedAngle = kneeAngles[argmax(kneeConfidence)]
val smoothedKneeAngle = kneeAngleSmoother.add(selectedAngle)

// 2. Normalize hip depth by leg length
val legLength = abs(shoulderY - ankleY).coerceAtLeast(1f)
val hipDepth = abs(hipY - ankleY) / legLength
val smoothedHipDepth = hipDepthSmoother.add(hipDepth)

// 3. State detection with dual criteria
val downByAngle = smoothedKneeAngle < 110°
val upByAngle = smoothedKneeAngle > 155°
val downByDepth = smoothedHipDepth < 0.34
val upByDepth = smoothedHipDepth > 0.44

state = when {
    downByAngle && downByDepth -> ExerciseState.Down
    upByAngle && upByDepth -> ExerciseState.Up
    else -> ExerciseState.Waiting
}
```

**Key Innovations:**
- **Confidence-based side selection**: Uses the leg with better landmark visibility
- **Leg-length normalization**: Depth measurement adapts to athlete height
- **Dual-criteria gating**: Prevents false positives from single metric

#### Form Signal Computation Examples

**Push-up Depth Score:**
```kotlin
fun depthScore(depthDelta: Double): Float {
    val target = 0.16  // Expected normalized depth
    return (depthDelta / target).coerceIn(0.0, 1.2).toFloat().coerceIn(0f, 1f)
}
```
- `depthDelta > 0.16`: Perfect depth (score = 1.0)
- `depthDelta = 0.12`: Moderate depth (score = 0.75, WARNING)
- `depthDelta = 0.08`: Shallow (score = 0.5, CRITICAL)

**Squat Depth Score:**
```kotlin
fun depthScore(hipDepth: Double?): Float {
    // Lower hipDepth = deeper squat. Target: 0.30, threshold: 0.44
    val normalized = ((0.44 - hipDepth) / (0.44 - 0.30)).coerceIn(0.0, 1.2)
    return normalized.toFloat().coerceIn(0f, 1f)
}
```
- `hipDepth = 0.28`: Deep squat (score > 1.0, clamped to 1.0)
- `hipDepth = 0.34`: Parallel (score = 0.71, INFO)
- `hipDepth = 0.40`: Shallow (score = 0.29, CRITICAL)

**Core Alignment Score (Push-ups):**
```kotlin
fun coreScore(hipAlignment: Double?): Float {
    val deviation = abs(hipAlignment)  // Deviation from neutral spine
    return (1.0 - (deviation / 0.3)).coerceIn(0.0, 1.2).toFloat().coerceIn(0f, 1f)
}
```
- `deviation = 0.0`: Perfect plank (score = 1.0)
- `deviation = 0.15`: Moderate sag (score = 0.5, WARNING)
- `deviation = 0.35`: Severe sag (score = 0.0, CRITICAL)

### Modular Detector Architecture

#### Interface Design
All exercise detectors implement the `ExerciseDetector` interface:
```kotlin
interface ExerciseDetector {
    fun analyze(pose: Pose, timestampMs: Long): DetectorResult
    fun onPoseLost(timestampMs: Long)
    fun reset()
}

data class DetectorResult(
    val state: ExerciseState,
    val repCompleted: Boolean,
    val formFeedback: FormFeedback,
    val poseVisible: Boolean = true
)
```

#### Flow Architecture
1. **ExerciseAnalyzer** (orchestrator)
   - Validates pose visibility using `hasCoreLandmarks()`
   - Delegates analysis to the appropriate `ExerciseDetector` based on `ExerciseType`
   - Tracks rep count across detector results
   - Emits `ExerciseAnalysis` to UI layer via callback
   - Handles pose loss timeout (900ms) and triggers detector reset

2. **Exercise-Specific Detectors** (`PushupDetector`, `SquatDetector`)
   - Maintain internal state machine (`ExerciseState.Waiting/Down/Up`)
   - Apply measurement smoothing to reduce jitter
   - Compute form signals with weighted scores
   - Return `DetectorResult` with rep completion flag and rich feedback

3. **State Management**
   - State stabilization prevents rapid transitions (220-240ms minimum duration)
   - Rep completed on `Down → Up` transition (single source of truth)
   - Baseline calibration adapts to athlete's range of motion

#### Form Feedback Signal Architecture

Each detector computes multiple **form signals** that assess specific aspects of exercise quality:

##### PushupDetector Signals

| Signal ID | Label | Weight | Measurement | Scoring Logic |
|-----------|-------|--------|-------------|---------------|
| `depth` | Depth | 1.3 | Distance from shoulders to support surface, normalized by torso length | Target: 0.16 delta. Score = (observed / target) capped at 1.0 |
| `lockout` | Lockout | 1.0 | Elbow angle at top position | Score = (angle - 140) / (168 - 140), capped 0-1 |
| `core` | Core | 0.9 | Hip alignment relative to shoulder-hip line | Score = 1 - (abs(hipAlignment) / 0.3), capped 0-1 |

**Signal Smoothing:** 6-sample rolling average for elbow angle, gap, and hip alignment

**Severity Thresholds:**
- INFO: score > 0.75
- WARNING: 0.55 < score ≤ 0.75
- CRITICAL: score ≤ 0.55

**Feedback Messages by Score Range:**
- Depth: "Solid push-up depth" (>0.85) → "Lower your chest closer to the ground" (≤0.6)
- Lockout: "Full elbow lockout" (>0.85) → "Press harder—lock your elbows out" (≤0.6)
- Core: "Strong plank line" (>0.85) → "Tighten your core—avoid hip sag" (≤0.6)

##### SquatDetector Signals

| Signal ID | Label | Weight | Measurement | Scoring Logic |
|-----------|-------|--------|-------------|---------------|
| `depth` | Depth | 1.2 | Hip-to-ankle vertical distance, normalized by leg length | Score = (0.44 - observed) / (0.44 - 0.30), capped 0-1 |
| `knees` | Knees | 1.0 | Knee flexion angle | Score = (155 - angle) / (155 - 95), capped 0-1 |
| `torso` | Torso | 0.9 | Torso angle deviation from vertical (180°) | Score = 1 - (deviation / 35), capped 0-1 |

**Signal Smoothing:** 6-sample rolling average for knee angle, hip depth, and torso angle

**Severity Thresholds:** Same as push-ups (INFO > 0.75, WARNING > 0.55, CRITICAL ≤ 0.55)

**Feedback Messages by Score Range:**
- Depth: "Excellent squat depth" (>0.85) → "Drive hips back and squat deeper" (≤0.6)
- Knees: "Controlled knee bend" (>0.85) → "Bend knees more to initiate the squat" (≤0.6)
- Torso: "Strong upright torso" (>0.85) → "Lift your chest to avoid collapsing" (≤0.6)

#### Overall Form Score Computation
```kotlin
overallScore = Σ(signal.weight × signal.score) / Σ(signal.weight)
```
The overall score is a weighted average of all signals, clamped to [0, 1].

#### Form Feedback HUD Display Logic
1. **Headline:** Message from the signal with the lowest score (most critical issue)
2. **Progress Bar:** Colored by primary signal severity (INFO=blue, WARNING=orange, CRITICAL=red)
3. **Signal List:** Up to 3 signals shown, sorted by score (worst first)
4. **Pose Loss:** Overrides all feedback with "Step fully into frame" message

#### Debug Metrics
Detectors expose raw metrics for tuning and troubleshooting:
- **Push-ups:** `gap`, `depthDelta`, `elbow`, `hipAlign`
- **Squats:** `hipDepth`, `kneeAngle`, `torsoAngle`

These are available in `FormFeedback.debugMetrics` but not currently displayed in the UI.

### Confidence Validation
- Ensures 70% of key landmarks are visible (raised from 75% requirement)
- Checks `inFrameLikelihood > 0.45f` for each landmark
- Gracefully handles partial occlusion
- Exercise-specific landmark requirements:
  - **Push-ups:** shoulders, elbows, wrists, hips (8 landmarks)
  - **Squats:** hips, knees, ankles (6 landmarks)
  - **Pull-ups:** nose, shoulders (3 landmarks)

### State Stabilization & Anti-Jitter
- Minimum state duration: 220ms (push-ups), 240ms (squats)
- Prevents rapid false triggering from pose estimation noise
- `Waiting` state can be entered immediately (no minimum duration)
- Baseline gap recalibration on `Up` state with exponential smoothing (80/20 blend)

## Skeleton Alignment Preserved

✅ **CRITICAL**: All skeleton rendering code remains unchanged
- `PoseOverlay.kt` - UNMODIFIED
- Coordinate transformation logic - PRESERVED
- Front camera mirroring - MAINTAINED
- Rep counting works alongside skeleton (doesn't interfere)

## Usage Flow

1. **App Launch** → Permission request
2. **Exercise Selection** → Choose exercise type
3. **Camera Start** → Skeleton overlay appears
4. **Position Yourself** → Follow on-screen skeleton
5. **Start Exercise** → Counter automatically tracks reps
6. **Reset/Back** → Clear counter or change exercise

## Camera Recommendations

### Push-ups & Pull-ups
- **Best**: Front camera (selfie mode)
- Reason: Need to see upper body clearly
- Position: Place phone on ground or vertical support

### Squats
- **Best**: Back camera (or side view if possible)
- Reason: Need to see full body including legs
- Position: Prop phone horizontally to see side profile

## Future Enhancement Ideas

1. **Adaptive Coaching**
   - Personalize feedback weights per athlete over time
   - Store historical form scores for trend analysis
   - Expand signals (e.g., shoulder protraction, ankle dorsiflexion)

2. **Workout Modes**
   - Timed workouts
   - Target rep goals
   - Rest timers between sets

3. **Exercise History**
   - Save workout sessions
   - Track progress over time
   - Visualize trends

4. **Additional Exercises**
   - Lunges
   - Planks
   - Burpees
   - Custom exercises

5. **AI Coaching**
   - Real-time form correction
   - Personalized workout plans
   - Progress recommendations

## Testing Checklist

- [x] Skeleton still aligns perfectly
- [ ] Push-up detection works accurately
- [ ] Squat detection works accurately  
- [ ] Pull-up detection works accurately
- [ ] Rep counter increments correctly
- [ ] Reset button clears counter
- [ ] Camera switching works smoothly
- [ ] Back navigation returns to selection
- [ ] Front camera mirroring preserved
- [ ] Back camera works for squats

## Code Quality

- Clean separation of concerns
- Modular architecture
- Reusable components
- Well-documented logic
- Type-safe enums and sealed classes
- Proper resource cleanup

