# OmniPose Fit

An Android exercise-tracking prototype built with Kotlin, Jetpack Compose, CameraX and ML Kit Pose Detection. It renders a skill tree and training UI and applies configurable landmark-angle rules to workout phases and repetition counts.

## What ran

The Windows audit assembled a debug APK with a dedicated JDK/Android SDK and passed ten JVM tests for the dynamic exercise engine. These tests verify bounded engine behavior with synthetic landmark/angle data. No camera session, Android device run or real exercise-form accuracy was validated.

Nineteen skill entries map to ten implemented schemas. Several push variants use elbow-angle phase logic; that does not establish a full posture assessment for each named movement. Existing generated GIF/design previews are illustrative assets, not device recordings.

## Build and inspect

Use JDK 17 and Android SDK platform 36 with the repository's Gradle wrapper:

```sh
./gradlew testDebugUnitTest assembleDebug
```

Windows uses `gradlew.bat`. Keep the SDK path in your local untracked configuration. APK assembly establishes packaging, not successful camera permissions, device compatibility or workout accuracy. The release workflow can publish APKs when triggered; the audit did not trigger a release.

## Implementation and use case

Inspect `app/src/main/java/com/grloepr/pushtrack/engine/` and its matching JVM tests to understand phase transitions. UI screens, overlays, skill-tree progression and camera plumbing live under the same app source tree. A useful next validation is a consenting adult's controlled device session comparing manual rep counts to recorded landmarks, including poor framing and different camera angles.

Camera calibration, hardware performance, exercise correctness and physical-safety outcomes remain unverified. No coaching, injury-prevention or clinical claim is established. Preserve the existing Android application ID and signing identity independently of any repository/display rename.
