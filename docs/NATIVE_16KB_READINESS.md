# Native 16 KB release readiness

Status: **blocked on the official static RELRO gate; runtime is a separate result**.
No app dependency, pose algorithm, ABI, minimum SDK or packaged vendor binary is changed by this audit. Do not call this a native compatibility fix.

## Reproduce against the artifact you will ship

```sh
python3 scripts/test_native_alignment.py
python3 scripts/audit_native_alignment.py app-release.aab \
  --require-abi armeabi-v7a --require-abi arm64-v8a \
  --require-abi x86 --require-abi x86_64 --output native-audit.json
```

The command inventories every packaged `.so`, records its SHA-256 and ELF program headers, and exits nonzero on malformed/missing native inputs or a failing 64-bit static check. The AAB currently includes all four ABIs, even though standalone APK splits list three. 32-bit libraries are inventoried and structurally checked, without incorrectly applying the 64-bit 16 KB release requirement to them. No exceptions or ignored vendor libraries are configured.

Per the [Android page-size guide](https://developer.android.com/guide/practices/page-sizes), independently verify:

1. Every 64-bit ELF LOAD alignment is at least 16384 bytes, with congruent virtual/file offsets
2. Each GNU_RELRO end satisfies `(p_vaddr + p_memsz) % 16384 == 0`; absence of RELRO is permitted by the guide
3. APK ZIP alignment passes `zipalign -c -P 16 -v 4 app.apk`, and `bundletool dump config --bundle=app-release.aab` requests `PAGE_ALIGNMENT_16K`
4. A device reports `adb shell getconf PAGE_SIZE` as `16384` and the actual camera/pose/reset/switch/exit journey runs successfully, without relying on backcompat mode

The auditor covers steps 1–2, not ZIP layout, installability, exercise accuracy or universal device compatibility. Static failure is not evidence that a crash was observed. Its `trailing_page_writable_load_overlap` field records whether rounding the RELRO end to a 16 KB boundary would cover bytes in a writable LOAD beyond the nominal RELRO end. This diagnostic cannot waive the documented formula or replace runtime tests.

## Baseline evidence (2026-10-01)

Exact app source: `ff77c09cac8af108fbc93964acbe94c266901689` (PR #36). The CI-only PR #37 does not change app source or dependencies. [Source run](https://github.com/grloper/OmniPose-Fit/actions/runs/36910983766).

Release AAB SHA-256: `33eef4ecd8935dbdc702b3fb6b30877b6a77edafaf22a765a5cd67377b3a3e43`.

All eight 64-bit libraries pass LOAD alignment; six fail the guide's RELRO formula:

| Dependency / native library | arm64-v8a RELRO remainder | x86_64 RELRO remainder |
| --- | ---: | ---: |
| graphics-path 1.0.1 / graphics.path | 8192 | 12288 |
| camera-core 1.4.2 / image_processing_util | 12288 | 0 |
| camera-core 1.4.2 / surface_util | 4096 | 4096 |
| mediapipe-internal 17.0.0-beta10 / xeno_native | 0 | 12288 |

Every original 64-bit ELF was byte-matched to its official Google Maven AAR. None of these baseline failing RELRO tails overlaps a following writable LOAD: for example, arm64 graphics.path ends RELRO at `0x6000`, rounds to `0x8000`, and its next writable LOAD begins at `0x9fd0`. This is why the report distinguishes a conservative official static gate from an observed runtime crash.

The [machine-readable evidence](evidence/native-alignment-2026-10-01.json) retains all 16 baseline ELF records, artifact hashes, vendor candidate audits and metadata URLs. Binary files are not modified or committed.

## Why a dependency bump is not presented as a fix

Official Google Maven artifacts were downloaded and independently inspected:

- graphics-path **1.1.0**, the latest stable, still fails the formula for both 64-bit ABIs (remainder 8192)
- camera-core **1.5.3** and **1.6.2**, the latest stable, align image_processing_util correctly but still fail surface_util on both 64-bit ABIs (remainder 4096). They also raise minimum AGP requirements to 8.6.0 and 8.9.1, respectively
- Official Maven metadata still lists ML Kit pose-detection **18.0.0-beta5** and mediapipe-internal **17.0.0-beta10** as latest. The current app already uses them

Sources: [Graphics releases](https://developer.android.com/jetpack/androidx/releases/graphics), [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera), [ML Kit releases](https://developers.google.com/ml-kit/release-notes), and exact Maven URLs in the evidence file. The public MediaPipe Tasks libraries are not a drop-in rebuild of ML Kit's internal `libxeno_native.so`.

## Hosted runtime scope and next action

The separate native workflow uses the officially listed `system-images;android-36;google_apis_ps16k;x86_64` image, requires `PAGE_SIZE=16384`, disables page-size backcompat using the documented emulator properties, and reuses the existing synthetic CameraX/ML Kit instrumentation. Runtime continues even if the static audit fails, and its own failure remains fatal. The original 4 KB validation workflow remains separate. This cloud-only test uses synthetic cameras and no user machine, account, human footage, release signing or publication.

The first API 35 attempt booted with `PAGE_SIZE=16384` but stopped before app installation because that image does not expose the documented backcompat-control properties. [AOSP Android 16 policy](https://android.googlesource.com/platform/system/sepolicy/+/android16-release/private/shell.te) explicitly permits shell control of both properties, unlike Android 15. The workflow therefore uses the supported API 36 image, with the same strict property write/readback assertions; no root access or ignored denial is used.

A passing x86_64 run would establish only that tested binary/journey on that system image. It would not resolve the official static discrepancy or establish arm64 device behavior, all runtime code paths or human-motion accuracy. Inspect the exact run's artifacts and native hashes before making any claim. API 36 x86_64 simulates 16 KB behavior; a real arm64 16 KB device remains a separate release gate.

The safe next step is an upstream-supported corrected/rebuilt SDK or authoritative upstream clarification backed by layout and runtime evidence. Rebuilding AndroidX from its corresponding source with NDK r28+ or both documented page-size linker flags is technically possible, but does not rebuild ML Kit's internal binary; no supported source rebuild for that exact binary was established in this audit. Do not binary-patch ELF headers, suppress the static check, drop ABIs, lower the target/minimum SDK or broadly replace the pose engine to make this gate look green. Any deliberate engine migration needs its own behavior/accuracy regression plan and review.
