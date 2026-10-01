# Tracking continuity and release validation

Motion tracking estimates 2D joint angles; it does not certify technique, medical suitability, or athletic mastery. Variant skills currently sharing a generic exercise schema display that limitation and cannot automatically unlock progression.

## Continuity contract

- Frame timestamps use Android monotonic elapsed time at analysis submission, not the wall clock at delivery.
- Duplicate or backward timestamps are ignored. Gaps greater than 500 ms discard the incomplete repetition/hold and require start-posture reacquisition. This conservative threshold may interrupt tracking on slow hardware.
- Backgrounding and camera switches invalidate the detector generation; queued or in-flight results from the previous stream are ignored. Completed counts remain; explicit reset clears session totals.
- Every scoring joint must be confident, finite and noncollapsed. Auxiliary visible joints cannot substitute for a missing scoring joint. Occlusion discards an incomplete attempt instead of reusing stale averaged angles.

## Reproduce

Run `gradlew.bat testDebugUnitTest assembleDebug lintDebug` with JDK 17+ and Android SDK 36. Tests use synthetic joint coordinates, not personal camera footage. Regression coverage includes a full repetition, partial movement, hold timing, missing poses, missing scoring joint with otherwise sufficient quorum, time discontinuities, interruption, explicit reset and invalid geometry.

## Required device validation before store submission

Test real front/back camera rotation and mirroring, lighting/occlusion, different bodies and exercise tempos, denied/revoked camera permission, lifecycle interruption, low-end latency, TalkBack and large fonts. Synthetic engine tests do not establish ML Kit pose accuracy or live-camera recognition quality. Debug APKs are development artifacts; release signing, privacy policy, store declarations and Play device testing remain separate release requirements.
