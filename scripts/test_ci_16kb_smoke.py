"""Check page-size/backcompat enforcement without starting a real emulator."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("ci_android_smoke.sh").resolve()


class PageSizeSmokeTest(unittest.TestCase):
    def run_fake(self, page_size, linker="false", package="true"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sdk, runner = root/"sdk", root/"runner"
            runner.mkdir()
            def put(path, text):
                target = sdk/path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text("#!/bin/bash\n" + text)
                target.chmod(0o755)
            put("emulator/emulator", '''case "$1" in
-version) echo synthetic;;
-list-avds) echo omnipose-ci;;
-accel-check) exit 0;;
-avd) exec sleep 30;;
esac
''')
            put("cmdline-tools/latest/bin/avdmanager", '''echo "$*" >> "$RUNNER_TEMP/avd-calls"
if [ "$1" = create ]; then
  mkdir -p "$ANDROID_AVD_HOME"
  echo "path=$ANDROID_AVD_HOME/omnipose-ci.avd" > "$ANDROID_AVD_HOME/omnipose-ci.ini"
fi
''')
            put("platform-tools/adb", '''echo "$*" >> "$RUNNER_TEMP/adb-calls"
case "$*" in
"get-state") exit 1;;
"shell getprop sys.boot_completed") echo 1;;
"shell pm path android") echo package:/system/framework/framework-res.apk;;
"shell getconf PAGE_SIZE") echo "$FAKE_PAGE_SIZE";;
"shell getprop bionic.linker.16kb.app_compat.enabled") echo "$FAKE_LINKER";;
"shell getprop pm.16kb.app_compat.disabled") echo "$FAKE_PACKAGE";;
install*) exit 42;;
esac
''')
            # A harmless dummy file permits the hash step; install then stops the fixture.
            apk = root/"app/build/outputs/apk/debug/app-universal-debug.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"synthetic")
            env = dict(os.environ, ANDROID_HOME=str(sdk), RUNNER_TEMP=str(runner),
                       SYSTEM_IMAGE="system-images;android-36;google_apis_ps16k;x86_64",
                       EXPECTED_PAGE_SIZE="16384", FAKE_PAGE_SIZE=page_size,
                       FAKE_LINKER=linker, FAKE_PACKAGE=package)
            result = subprocess.run(["bash", str(SCRIPT)], cwd=root, env=env,
                                    text=True, capture_output=True, timeout=10)
            calls = (runner/"adb-calls").read_text()
            avd = (runner/"avd-calls").read_text()
            return result, calls, avd

    def test_wrong_page_size_fails_before_install(self):
        result, calls, avd = self.run_fake("4096")
        self.assertEqual(result.returncode, 1)
        self.assertIn("Expected PAGE_SIZE=16384", result.stderr)
        self.assertNotIn("install ", calls)
        self.assertIn("google_apis_ps16k", avd)

    def test_compat_readback_required_before_install(self):
        for linker, package in [("true", "true"), ("false", "false"), ("", "")]:
            result, calls, _ = self.run_fake("16384", linker, package)
            self.assertEqual(result.returncode, 1)
            self.assertNotIn("install ", calls)

    def test_confirmed_16kb_without_compat_reaches_install(self):
        result, calls, _ = self.run_fake("16384")
        self.assertEqual(result.returncode, 42)
        self.assertIn("setprop bionic.linker.16kb.app_compat.enabled false", calls)
        self.assertIn("setprop pm.16kb.app_compat.disabled true", calls)
        self.assertIn("install app/build/outputs/apk/debug/app-universal-debug.apk", calls)


if __name__ == "__main__":
    unittest.main()
