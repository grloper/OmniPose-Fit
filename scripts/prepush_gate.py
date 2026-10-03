#!/usr/bin/env python3
"""Same exact-snapshot source/workflow/build gate locally and in CI."""
import argparse, hashlib, json, os, shutil, subprocess, sys
from pathlib import Path

TASKS = {
    "omni": ["testDebugUnitTest", "lintDebug", "assembleDebug", "assembleDebugAndroidTest", "bundleRelease"],
    "swipe": ["testPlayDebugUnitTest", "testFdroidDebugUnitTest", "testCloudDebugUnitTest",
              "lintPlayDebug", "lintFdroidDebug", "lintCloudDebug", "assemblePlayDebug",
              "assembleFdroidDebug", "assembleCloudDebug", "assemblePlayDebugAndroidTest",
              "assembleFdroidDebugAndroidTest", "assembleCloudDebugAndroidTest", "bundlePlayRelease"],
}
def execute(args, **kwargs):
    print("+ " + " ".join(map(str, args)), flush=True)
    subprocess.run(args, check=True, **kwargs)
def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()
def tool(env, name):
    result = os.environ.get(env) or shutil.which(name)
    if not result:
        raise SystemExit(f"{name} required; use pinned installer or set {env}")
    return result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--app", choices=TASKS, required=True)
    parser.add_argument("--expected-head")
    parser.add_argument("--static-only", action="store_true")
    options = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    os.chdir(root)
    head = git("rev-parse", "HEAD")
    if options.expected_head and head != options.expected_head:
        raise SystemExit("Expected commit differs from checked-out snapshot")
    if git("status", "--porcelain"):
        raise SystemExit("Freeze a clean committed tree before validating or pushing")
    tree = git("rev-parse", "HEAD^{tree}")
    actionlint, shellcheck, bash = tool("ACTIONLINT", "actionlint"), tool("SHELLCHECK", "shellcheck"), tool("TEST_BASH", "bash")
    files = git("ls-files").splitlines()
    python_count = 0
    for relative in files:
        if relative.endswith(".py"):
            compile(Path(relative).read_text(encoding="utf-8-sig"), relative, "exec")
            python_count += 1
        elif relative.endswith(".sh"):
            content = Path(relative).read_text(encoding="utf-8").encode("utf-8")
            execute([bash, "-n"], input=content)
            execute([shellcheck, "--severity=warning", "--shell=bash", "-"], input=content)
    workflows = sorted(Path(".github/workflows").glob("*.y*ml"))
    execute([actionlint, "-shellcheck=" + shellcheck, "-pyflakes=", *map(str, workflows)])
    if options.app == "omni":
        execute([sys.executable, "scripts/test_require_exact_validation.py"])
        execute([sys.executable, "scripts/test_ci_android_smoke.py"])
    else:
        execute([sys.executable, "-m", "unittest", "discover", "-s", "tools", "-p", "test_*.py"])
    if not options.static_only:
        wrapper = "./gradlew.bat" if os.name == "nt" else "./gradlew"
        execute([wrapper, *TASKS[options.app], "--no-daemon", "--max-workers=2",
                 "-Dorg.gradle.jvmargs=-Xmx1536m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8",
                 "-Pkotlin.compiler.execution.strategy=in-process"])
    if head != git("rev-parse", "HEAD") or git("status", "--porcelain"):
        raise SystemExit("Snapshot changed during checks; freeze and repeat the gate")
    output = Path("evidence/prepush-snapshot.json")
    output.parent.mkdir(exist_ok=True)
    output.write_text(json.dumps({"head_sha": head, "tree_sha": tree, "app": options.app,
        "python_ast_files": python_count, "workflows": [str(p) for p in workflows],
        "gradle_tasks": [] if options.static_only else TASKS[options.app],
        "static_checks_passed": True, "build_checks_passed": not options.static_only,
        "runtime_validation": "Separate exact-head emulator CI is mandatory before merge",
        "artifact_hashes": {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in Path("app/build/outputs").rglob("*") if not options.static_only and p.is_file()
            and p.suffix in (".apk", ".aab")}}, indent=2), encoding="utf-8")
    print("PASS frozen snapshot " + head, flush=True)
if __name__ == "__main__":
    main()
