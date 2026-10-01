from pathlib import Path
import os, subprocess, tempfile, time
script=Path(__file__).with_name('ci_android_smoke.sh').resolve()
with tempfile.TemporaryDirectory(prefix='omni-smoke-test-') as tmp:
 root=Path(tmp); sdk=root/'sdk'; run=root/'runner'; run.mkdir()
 def put(rel,content):
  p=sdk/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('#!/bin/bash\n'+content);p.chmod(0o755)
 put('platform-tools/adb','printf "%s\\n" "$*" >> "$RUNNER_TEMP/adb-calls"\nif [ "$1" = get-state ]; then exit 1; fi\nexit 0\n')
 put('cmdline-tools/latest/bin/avdmanager','echo "synthetic avdmanager: deliberately produces no ini"\nexit 0\n')
 put('emulator/emulator','if [ "$1" = -version ]; then echo "synthetic emulator"; exit 0; fi\nif [ "$1" = -list-avds ]; then exit 0; fi\necho "ERROR: emulator must not launch without AVD"; exit 9\n')
 env=dict(os.environ,ANDROID_HOME=str(sdk),RUNNER_TEMP=str(run))
 t=time.monotonic(); result=subprocess.run(['bash',str(script)],cwd=root,env=env,text=True,capture_output=True,timeout=10);elapsed=time.monotonic()-t
 assert result.returncode==1,(result.returncode,result.stderr)
 assert 'AVD creation did not produce' in result.stderr,result.stderr
 calls=(run/'adb-calls').read_text();assert 'logcat' not in calls and 'pull' not in calls,calls
 assert (root/'evidence/smoke-exit-code.txt').read_text().strip()=='1'
 print(f'PASS missing AVD fails before emulator launch; offline cleanup skips blocking reads; preserves exit1 ({elapsed:.2f}s)')
 # Once a definition is present but emulator exits, fail early rather than waiting 240s.
 put('cmdline-tools/latest/bin/avdmanager','if [ "$1" = create ]; then mkdir -p "$ANDROID_AVD_HOME"; echo "path=$ANDROID_AVD_HOME/omnipose-ci.avd" > "$ANDROID_AVD_HOME/omnipose-ci.ini"; fi\nexit 0\n')
 put('emulator/emulator','case "$1" in -version) echo "synthetic emulator";; -list-avds) echo omnipose-ci;; -accel-check) echo "synthetic acceleration";; -avd) echo "synthetic early emulator failure"; exit 8;; esac\n')
 result=subprocess.run(['bash',str(script)],cwd=root,env=env,text=True,capture_output=True,timeout=10)
 assert result.returncode==1,(result.returncode,result.stderr)
 assert 'Emulator exited before Android became ready' in result.stderr,result.stderr
 assert 'synthetic early emulator failure' in result.stderr,result.stderr
 print('PASS early emulator exit retains log and fails without starting instrumentation')

 # A test failure remains fatal, and the actual Gradle invocation preserves its APKs.
 put('emulator/emulator', 'case "$1" in -version) echo "synthetic emulator";; -list-avds) echo omnipose-ci;; -accel-check) exit 0;; -avd) exec sleep 30;; esac\n')
 put('platform-tools/adb', 'printf "%s\\n" "$*" >> "$RUNNER_TEMP/adb-calls"\ncase "$*" in "get-state") exit 1;; "shell getprop sys.boot_completed") echo 1;; "shell pm path android") echo package:/system/framework/framework-res.apk;; "shell getconf PAGE_SIZE") echo 4096;; esac\n')
 apk=root/'app/build/outputs/apk/debug/app-universal-debug.apk';apk.parent.mkdir(parents=True,exist_ok=True);apk.write_bytes(b'synthetic')
 gradle=root/'gradlew';gradle.write_text('#!/bin/bash\nprintf "%s\\n" "$*" > "$RUNNER_TEMP/gradle-args"\nexit 42\n');gradle.chmod(0o755)
 result=subprocess.run(['bash',str(script)],cwd=root,env=env,text=True,capture_output=True,timeout=10)
 assert result.returncode==42,(result.returncode,result.stderr)
 assert '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' in (run/'gradle-args').read_text()
 assert (root/'evidence/smoke-exit-code.txt').read_text().strip()=='42'
 print('PASS instrumentation failure stays fatal and Gradle retains tested APKs for evidence')
