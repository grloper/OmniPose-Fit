# OmniPose Fit - AI Coding Agent Instructions

## Project Overview
OmniPose Fit is an Android calisthenics app using **ML Kit Pose Detection** for
real-time, schema-driven exercise tracking with gamified skill progression.
Built with **Jetpack Compose**, **CameraX**, and **Kotlin**.

## Critical Architecture Concepts

### 1. Pose Detection Coordinate System (CRITICAL - DO NOT BREAK)
**Skeleton overlay alignment is pixel-perfect and must stay that way.**

- `ui/overlay/PoseCoordinateMapper.kt` owns the transformation pipeline:
  1. ML Kit returns landmarks in **rotated image coordinate space** (rotation is
     handled by `InputImage.fromMediaImage()` + `rotationDegrees`)
  2. For 90°/270° rotations, effective width/height are swapped
  3. Front camera mirroring: `x = effectiveImageWidth - x`
  4. `FIT_CENTER` scaling matches `PreviewView` letterboxing
- `analysis/ImageAnalyzer.kt` passes **media dimensions** (`mediaImage.width/height`),
  NOT the already-rotated `inputImage` dimensions.

### 2. Schema-Driven Dynamic Engine (no hardcoded routines)
Exercises are defined by JSON files in `app/src/main/assets/exercises/`:
`tracking_joints` (named joint triples), `states` (START / INFLECTION_POINT / END
angle constraints), `validation` (optimal camera plane + required joints).

- `engine/DynamicExerciseEngine.kt` is a generic state machine:
  SEARCHING → READY → ECCENTRIC → BOTTOM → CONCENTRIC → rep++.
  It knows nothing about specific exercises. To add a movement, add a JSON file —
  do not add Kotlin detectors.
- The engine layer is deliberately **pure Kotlin** operating on
  `engine/PoseSnapshot.kt` (SDK-agnostic). Keep vendor types (ML Kit `Pose`)
  out of `engine/`, `progression/` and `anatomy/` — this preserves the planned
  Kotlin Multiplatform migration path.

### 3. Spatial Camera Guidance
`engine/PoseValidator.kt` judges the camera plane from torso-normalized lateral
compression of L/R joint pairs; `engine/AlignmentMonitor.kt` adds smoothing +
dwell hysteresis so the `CameraAngleBanner` never flickers. Tune thresholds in
`PoseValidator`, not in the UI.

### 4. Progression & Gamification
`progression/SkillGraph.kt` is a validated DAG (prerequisites must sit on lower
tiers — an `init` block enforces this). Mastery persists via SharedPreferences
in `progression/SkillTreeState.kt`. Node visual states: LOCKED (padlock),
AVAILABLE (pulsing ring), MASTERED (gold glow) in `ui/tree/SkillTreeScreen.kt`.

## Conventions
- Dark theme only: use palette colors from `ui/theme/Color.kt`
  (ElectricCyan / NeonViolet / VoltLime / AchievementGold / SignalAmber).
- Floating HUD elements wrap in `ui/components/GlassPanel.kt`.
- Animations: Compose `rememberInfiniteTransition` / `AnimatedContent`;
  keep camera-path work allocation-free per frame where possible.
- Analysis runs at ~15 FPS on a background executor (`ImageAnalyzer`), UI state
  flows through a single `EngineFrame` snapshot per pose.
