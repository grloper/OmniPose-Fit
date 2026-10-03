#!/usr/bin/env bash
# Synthetic CI device only. No account sign-in or human camera footage.
set -Eeuo pipefail

: "${ANDROID_HOME:?Android SDK is required}"
: "${RUNNER_TEMP:?A runner-local temporary directory is required}"
mkdir -p evidence
stage=initialization
on_error() { local code=$1 command=$2; printf 'stage=%s\ncommand=%s\nexit=%s\n' "$stage" "$command" "$code" > evidence/failure.txt; return "$code"; }
trap 'on_error "$?" "$BASH_COMMAND"' ERR
export ANDROID_SDK_HOME="$RUNNER_TEMP/omnipose-sdk-user"
export ANDROID_USER_HOME="$ANDROID_SDK_HOME/.android"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
export ANDROID_SERIAL=emulator-5554
mkdir -p "$ANDROID_AVD_HOME"
ADB="$ANDROID_HOME/platform-tools/adb"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVD_NAME=omnipose-ci
SYSTEM_IMAGE='system-images;android-35;google_apis;x86_64'
emulator_pid=
recorder_pid=
SOAK_MINUTES="${SOAK_MINUTES:-0}"
if ! [[ "$SOAK_MINUTES" =~ ^([0-9]|[12][0-9]|30)$ ]]; then
  echo "SOAK_MINUTES must be an integer from 0 through 30" >&2
  exit 1
fi

cleanup() {
  local status=$?
  trap - EXIT
  set +e
  # Missing/offline devices must not make failure diagnostics hang indefinitely.
  timeout --kill-after=5s 5 "$ADB" devices -l > evidence/adb-devices.txt 2>&1
  if timeout --kill-after=5s 5 "$ADB" get-state 2>/dev/null | grep -qx device; then
    timeout --kill-after=5s 15 "$ADB" logcat -d > evidence/logcat.txt 2>&1
    timeout --kill-after=5s 15 "$ADB" pull /sdcard/journey.mp4 evidence/journey.mp4 > evidence/video-pull.txt 2>&1
    if [ ! -d evidence/practice-screens ]; then
      timeout --kill-after=5s 15 "$ADB" pull /sdcard/Android/data/com.grloepr.pushtrack.codextest/files/practice-evidence evidence/practice-screens > evidence/practice-pull.txt 2>&1
    fi
    timeout --kill-after=5s 10 "$ADB" emu kill > evidence/emulator-stop.txt 2>&1
  fi
  if [ -n "$recorder_pid" ]; then kill "$recorder_pid" 2>/dev/null; fi
  if [ -n "$emulator_pid" ]; then kill "$emulator_pid" 2>/dev/null; fi
  if [ "$status" -ne 0 ]; then
    for log in toolchain.txt avd-create.txt avd-list.txt emulator-avds.txt acceleration.txt emulator.log; do
      if [ -f "evidence/$log" ]; then
        printf '\n--- %s ---\n' "$log" >&2
        tail -60 "evidence/$log" >&2
      fi
    done
  fi
  printf '%s\n' "$status" > evidence/smoke-exit-code.txt
  exit "$status"
}
trap cleanup EXIT

{
  printf 'ANDROID_HOME=%s\nANDROID_USER_HOME=%s\nANDROID_AVD_HOME=%s\n' "$ANDROID_HOME" "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
  "$EMULATOR" -version
  "$ADB" version
} > evidence/toolchain.txt 2>&1

# Give avdmanager and emulator exactly the same preference and AVD directories.
# Explicit --path also records where the disk image is expected to be created.
printf 'no\n' | timeout --kill-after=5s 60 "$AVDMANAGER" create avd --name "$AVD_NAME" \
  --package "$SYSTEM_IMAGE" --path "$ANDROID_AVD_HOME/$AVD_NAME.avd" --force \
  > evidence/avd-create.txt 2>&1
timeout --kill-after=5s 15 "$AVDMANAGER" list avd > evidence/avd-list.txt 2>&1
timeout --kill-after=5s 15 "$EMULATOR" -list-avds > evidence/emulator-avds.txt 2>&1
if [ ! -f "$ANDROID_AVD_HOME/$AVD_NAME.ini" ] || ! grep -qx "$AVD_NAME" evidence/emulator-avds.txt; then
  echo "AVD creation did not produce an emulator-visible definition" >&2
  cat evidence/avd-create.txt evidence/avd-list.txt evidence/emulator-avds.txt >&2
  exit 1
fi
cp "$ANDROID_AVD_HOME/$AVD_NAME.ini" evidence/avd.ini
timeout --kill-after=5s 15 "$EMULATOR" -accel-check > evidence/acceleration.txt 2>&1
timeout --kill-after=5s 20 "$ADB" start-server > evidence/adb-start.txt 2>&1
"$EMULATOR" -avd "$AVD_NAME" -port 5554 -no-window -no-audio -no-snapshot \
  -no-boot-anim -gpu swiftshader_indirect -camera-back emulated -camera-front emulated \
  > evidence/emulator.log 2>&1 &
emulator_pid=$!

booted=false
deadline=$((SECONDS + 240))
while [ "$SECONDS" -lt "$deadline" ]; do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    echo "Emulator exited before Android became ready" >&2
    tail -80 evidence/emulator.log >&2
    exit 1
  fi
  boot_status="$(timeout --kill-after=5s 5 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [ "$boot_status" = 1 ] && timeout --kill-after=5s 5 "$ADB" shell pm path android 2>/dev/null | grep -q '^package:'; then
    booted=true
    break
  fi
  sleep 2
done
if [ "$booted" != true ]; then
  echo "Android boot/package-manager readiness exceeded 240 seconds" >&2
  tail -80 evidence/emulator.log >&2
  exit 1
fi
timeout --kill-after=5s 10 "$ADB" shell getprop > evidence/device-properties.txt
timeout --kill-after=5s 10 "$ADB" shell getconf PAGE_SIZE > evidence/page-size.txt
timeout --kill-after=5s 120 "$ADB" install app/build/outputs/apk/debug/app-universal-debug.apk | tee evidence/install.txt
timeout --kill-after=5s 10 "$ADB" logcat -c
# The APKs are already built. Install instrumentation before starting recording;
# recording while Gradle prepares its connected task captured only the launcher.
timeout --kill-after=5s 120 "$ADB" install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk > evidence/test-install.txt
timeout --kill-after=5s 190 "$ADB" shell screenrecord --time-limit 180 /sdcard/journey.mp4 > evidence/screenrecord.log 2>&1 &
recorder_pid=$!

# Keep real instrumentation mandatory. Readiness and cleanup are infrastructure gates,
# never substitutes for the CameraX/ML Kit/reset/switch/exit assertions.
# AGP 8.5.2 exposes this stable keep-installed option; retain the tested ABI APK
# for identity capture/relaunch instead of reinstalling a different artifact.
stage=instrumentation
timeout --kill-after=5s "$((720 + SOAK_MINUTES * 60))" ./gradlew connectedDebugAndroidTest --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Pandroid.testInstrumentationRunnerArguments.soakMinutes="$SOAK_MINUTES" \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true | tee evidence/instrumentation.txt
stage=optional-recording
if wait "$recorder_pid"; then
  if ! timeout --kill-after=5s 15 "$ADB" pull /sdcard/journey.mp4 evidence/journey.mp4 > evidence/video-pull.txt 2>&1; then
    printf '{"available":false,"reason":"recorder artifact unavailable"}\n' > evidence/recording-unavailable.json
  fi
else
  printf '{"available":false,"reason":"recorder exited unsuccessfully"}\n' > evidence/recording-unavailable.json
fi
recorder_pid=
stage=launch
timeout --kill-after=5s 30 "$ADB" shell am start -W -n com.grloepr.pushtrack.codextest/com.grloepr.pushtrack.MainActivity | tee evidence/launch.txt
sleep 8
timeout --kill-after=5s 10 "$ADB" shell pidof com.grloepr.pushtrack.codextest > evidence/pid.txt
timeout --kill-after=5s 30 "$ADB" shell uiautomator dump /sdcard/window.xml
timeout --kill-after=5s 15 "$ADB" pull /sdcard/window.xml evidence/window.xml
timeout --kill-after=5s 15 "$ADB" shell screencap -p /sdcard/screen.png
timeout --kill-after=5s 15 "$ADB" pull /sdcard/screen.png evidence/screen.png
timeout --kill-after=5s 15 "$ADB" logcat -d > evidence/logcat.txt
stage=mandatory-runtime-evidence
timeout --kill-after=5s 30 "$ADB" pull /sdcard/Android/data/com.grloepr.pushtrack.codextest/files/evidence evidence/runtime-screens
for screen in exercise-detail training-controls-empty-synthetic-camera training-paused synthetic-event-counter-one synthetic-event-target-dialog synthetic-event-after-target; do
  test -s "evidence/runtime-screens/$screen.png"
done
stage=mandatory-practice-evidence
timeout --kill-after=5s 30 "$ADB" pull /sdcard/Android/data/com.grloepr.pushtrack.codextest/files/practice-evidence evidence/practice-screens
for screen in library-normal manual-normal exercise-detail-normal library-font2 manual-font2-keyboard manual-font2-controls; do
  test -s "evidence/practice-screens/$screen.png"
done
stage=logcat-validation
if grep -q 'FATAL EXCEPTION' evidence/logcat.txt; then
  echo 'Runtime fatal exception found in logcat' >&2
  false
fi

