#!/usr/bin/env bash
# Synthetic CI device only. No account sign-in or human camera footage.
set -Eeuo pipefail

: "${ANDROID_HOME:?Android SDK is required}"
: "${RUNNER_TEMP:?A runner-local temporary directory is required}"
mkdir -p evidence
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

cleanup() {
  local status=$?
  trap - EXIT
  set +e
  # Missing/offline devices must not make failure diagnostics hang indefinitely.
  timeout --kill-after=5s 5 "$ADB" devices -l > evidence/adb-devices.txt 2>&1
  if timeout --kill-after=5s 5 "$ADB" get-state 2>/dev/null | grep -qx device; then
    timeout --kill-after=5s 15 "$ADB" logcat -d > evidence/logcat.txt 2>&1
    timeout --kill-after=5s 15 "$ADB" pull /sdcard/journey.mp4 evidence/journey.mp4 > evidence/video-pull.txt 2>&1
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
timeout --kill-after=5s 75 "$ADB" shell screenrecord --time-limit 60 /sdcard/journey.mp4 > evidence/screenrecord.log 2>&1 &
recorder_pid=$!

# Keep real instrumentation mandatory. Readiness and cleanup are infrastructure gates,
# never substitutes for the CameraX/ML Kit/reset/switch/exit assertions.
timeout --kill-after=5s 360 ./gradlew connectedDebugAndroidTest --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process | tee evidence/instrumentation.txt
wait "$recorder_pid"
recorder_pid=
timeout --kill-after=5s 30 "$ADB" shell am start -W -n com.grloepr.pushtrack/.MainActivity | tee evidence/launch.txt
sleep 8
timeout --kill-after=5s 10 "$ADB" shell pidof com.grloepr.pushtrack > evidence/pid.txt
timeout --kill-after=5s 30 "$ADB" shell uiautomator dump /sdcard/window.xml
timeout --kill-after=5s 15 "$ADB" pull /sdcard/window.xml evidence/window.xml
timeout --kill-after=5s 15 "$ADB" shell screencap -p /sdcard/screen.png
timeout --kill-after=5s 15 "$ADB" pull /sdcard/screen.png evidence/screen.png
timeout --kill-after=5s 15 "$ADB" logcat -d > evidence/logcat.txt
! grep -q 'FATAL EXCEPTION' evidence/logcat.txt

