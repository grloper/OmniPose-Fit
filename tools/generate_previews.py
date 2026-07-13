#!/usr/bin/env python3
"""
Generates photorealistic AI-athlete demonstration videos for OmniPose Fit and
installs them as in-app preview assets.

For every exercise schema in app/src/main/assets/exercises/ this script:
  1. builds a movement-exact prompt (camera plane, tempo and rep depth are
     derived from the schema itself),
  2. calls a text-to-video model through the Hugging Face Inference Providers
     API (routed to fal.ai / Replicate / etc. — pick with --provider),
  3. post-processes the clip with ffmpeg (scale to 640px, H.264 yuv420p,
     faststart, optional ping-pong loop),
  4. writes it to app/src/main/assets/previews/{exercise_id}.mp4 — exactly the
     file the in-app player picks up. No code changes needed.

Requirements:
  pip install "huggingface_hub>=0.26" imageio-ffmpeg
  export HF_TOKEN=hf_...        # needs Inference Providers credit/billing

Usage:
  python tools/generate_previews.py                       # all exercises
  python tools/generate_previews.py --exercise squat      # one exercise
  python tools/generate_previews.py --dry-run             # print prompts only
  python tools/generate_previews.py --model Wan-AI/Wan2.2-T2V-A14B --provider fal-ai

Tips for best results:
  - Generate 3-4 seeds per exercise (--seeds 4) and keep the best clip.
  - The definitive QC step: play the clip on a monitor and point the app's
    camera at it — if the rep engine counts clean reps, the form is right.
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SCHEMA_DIR = REPO_ROOT / "app/src/main/assets/exercises"
OUTPUT_DIR = REPO_ROOT / "app/src/main/assets/previews"

CAMERA_HINTS = {
    "SAGITTAL": "filmed from a perfect side profile so the full depth of the "
                "movement is visible",
    "FRONTAL": "filmed facing the camera straight-on so left/right symmetry "
               "is visible",
    "ANY": "filmed from a clear three-quarter angle",
}

MOVEMENT_NOTES = {
    "squat": "performing slow, perfect deep squats: a controlled two-second "
             "descent until the hips drop below the knees, a brief pause at "
             "the bottom, then a powerful controlled rise to full standing "
             "lockout",
    "pushup": "performing slow, perfect push-ups on the floor: rigid straight "
              "plank line from head to heels, chest lowered until the elbows "
              "reach ninety degrees, then a controlled press back to full "
              "elbow lockout",
    "pullup": "performing slow, perfect pull-ups on a bar: starting from a "
              "dead hang with straight arms, pulling smoothly until the chin "
              "clears the bar, then lowering under full control back to a "
              "dead hang",
}

NEGATIVE = ("morphing limbs, extra fingers, distorted anatomy, camera shake, "
            "camera movement, zoom, jump cuts, scene change, text, captions, "
            "watermark, logo, blurry, low quality")


def build_prompt(schema: dict) -> str:
    ex_id = schema["exercise_id"]
    plane = (schema.get("validation") or {}).get("optimal_camera_plane", "ANY")
    movement = MOVEMENT_NOTES.get(
        ex_id,
        f"performing slow, perfect {schema.get('display_name', ex_id)} repetitions "
        "with textbook form through the complete range of motion",
    )
    return (
        "Professional fitness demonstration video. A single athletic person in "
        "simple dark training clothes inside a modern dark gym, "
        f"{movement}, {CAMERA_HINTS.get(plane, CAMERA_HINTS['ANY'])}. "
        "Entire body always fully in frame, locked-off tripod shot, no camera "
        "movement, clean uncluttered dark background, soft cinematic lighting "
        "with a subtle cyan rim light, photorealistic, sharp focus, 4k detail."
    )


def ffmpeg_exe() -> str:
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return "ffmpeg"


def post_process(raw: Path, out: Path, pingpong: bool) -> None:
    """Normalise the generated clip for in-app playback."""
    vf = "scale=640:-2"
    if pingpong:
        # forward + reversed copy -> seamless loop even if start != end pose
        vf = "split[a][b];[b]reverse[r];[a][r]concat=n=2:v=1,scale=640:-2"
    cmd = [
        ffmpeg_exe(), "-y", "-i", str(raw),
        "-filter_complex" if pingpong else "-vf", vf,
        "-an", "-c:v", "libx264", "-crf", "23", "-pix_fmt", "yuv420p",
        "-movflags", "+faststart", str(out),
    ]
    subprocess.run(cmd, check=True, capture_output=True)


def generate(args: argparse.Namespace) -> int:
    schemas = []
    for f in sorted(SCHEMA_DIR.glob("*.json")):
        schema = json.loads(f.read_text())
        if not args.exercise or schema["exercise_id"] in args.exercise:
            schemas.append(schema)
    if not schemas:
        print(f"No matching schemas in {SCHEMA_DIR}", file=sys.stderr)
        return 1

    if args.dry_run:
        for s in schemas:
            print(f"\n─── {s['exercise_id']} " + "─" * 40)
            print(build_prompt(s))
        return 0

    token = args.token or os.environ.get("HF_TOKEN") or os.environ.get("HUGGINGFACE_TOKEN")
    if not token:
        print("ERROR: provide a Hugging Face token via --token or HF_TOKEN.\n"
              "Create one at https://huggingface.co/settings/tokens (Inference "
              "Providers must be enabled/funded on your account).", file=sys.stderr)
        return 1

    from huggingface_hub import InferenceClient
    client = InferenceClient(provider=args.provider, api_key=token)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    failures = 0
    for schema in schemas:
        ex_id = schema["exercise_id"]
        prompt = build_prompt(schema)
        for seed in range(args.seeds):
            suffix = "" if args.seeds == 1 else f".seed{seed}"
            out = OUTPUT_DIR / f"{ex_id}{suffix}.mp4"
            print(f"▶ {ex_id} (seed {seed}) via {args.provider}:{args.model} …")
            try:
                video = client.text_to_video(
                    prompt,
                    model=args.model,
                    negative_prompt=NEGATIVE,
                    num_frames=args.num_frames,
                    seed=seed,
                )
                with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp:
                    tmp.write(video)
                    raw = Path(tmp.name)
                post_process(raw, out, pingpong=args.pingpong)
                raw.unlink(missing_ok=True)
                print(f"  ✓ wrote {out.relative_to(REPO_ROOT)} "
                      f"({out.stat().st_size // 1024} KB)")
            except Exception as e:  # keep going; report at the end
                failures += 1
                print(f"  ✗ {ex_id} seed {seed} failed: {e}", file=sys.stderr)

    if failures:
        print(f"\n{failures} generation(s) failed.", file=sys.stderr)
        return 2
    print("\nDone. Rebuild the app — the detail sheets pick the clips up "
          "automatically. If you generated multiple seeds, keep the best clip "
          "as {exercise_id}.mp4 and delete the rest.")
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--model", default="Wan-AI/Wan2.2-T2V-A14B",
                   help="text-to-video model id on the Hub (default: %(default)s)")
    p.add_argument("--provider", default="fal-ai",
                   help="HF Inference Provider to route through (default: %(default)s)")
    p.add_argument("--token", default=None, help="HF token (falls back to $HF_TOKEN)")
    p.add_argument("--exercise", nargs="*", default=None,
                   help="only these exercise ids (default: all)")
    p.add_argument("--seeds", type=int, default=1,
                   help="clips to generate per exercise (default: 1)")
    p.add_argument("--num-frames", type=int, default=81, dest="num_frames",
                   help="frames to request from the model (default: 81)")
    p.add_argument("--pingpong", action="store_true",
                   help="make a seamless forward+reverse loop")
    p.add_argument("--dry-run", action="store_true",
                   help="print the prompts without calling any API")
    return generate(p.parse_args())


if __name__ == "__main__":
    sys.exit(main())
