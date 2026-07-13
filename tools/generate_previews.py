#!/usr/bin/env python3
"""
Generates photorealistic AI-athlete demonstration videos for OmniPose Fit and
installs them as in-app preview assets.

For every exercise schema in app/src/main/assets/exercises/ this script:
  1. builds a movement-exact prompt (camera plane, tempo and rep depth are
     derived from the schema itself),
  2. renders a clip through one of three backends (--backend), and
  3. post-processes it with ffmpeg (scale to 640px, H.264 yuv420p, faststart,
     optional ping-pong loop),
  4. writing it to app/src/main/assets/previews/{exercise_id}.mp4 — exactly the
     file the in-app player picks up. No code changes needed.

Backends (see docs/VIDEO_MODELS.md):
  hf-api        Hugging Face Inference Providers (fal/Replicate/…). Needs a
                funded HF token. No GPU required.
  hf-endpoint   Your own HF Inference Endpoint (--endpoint URL).
  local         Self-hosted diffusers on your own CUDA GPU (--pipeline
                wan|cogvideox|ltx|hunyuan|mochi). Zero per-clip cost.

Requirements:
  hf-api / hf-endpoint:  pip install "huggingface_hub>=0.26" imageio-ffmpeg
  local:                 pip install "diffusers>=0.32" transformers accelerate \
                             torch imageio-ffmpeg

Usage:
  python tools/generate_previews.py --dry-run                     # prompts only
  python tools/generate_previews.py                              # hf-api, all
  HF_TOKEN=hf_... python tools/generate_previews.py --seeds 4
  python tools/generate_previews.py --backend local --pipeline wan \
      --model Wan-AI/Wan2.2-TI2V-5B-Diffusers --offload --pingpong
  python tools/generate_previews.py --backend hf-endpoint \
      --endpoint https://xxxx.endpoints.huggingface.cloud --token hf_...

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


# ── generation backends ──────────────────────────────────────────────────────
# Default diffusers pipeline class + model per --pipeline for the local backend.
LOCAL_DEFAULTS = {
    "wan":       "Wan-AI/Wan2.2-TI2V-5B-Diffusers",
    "cogvideox": "THUDM/CogVideoX1.5-5B",
    "ltx":       "Lightricks/LTX-Video",
    "hunyuan":   "tencent/HunyuanVideo",
    "mochi":     "genmo/mochi-1-preview",
}

_local_pipe_cache: dict = {}


def resolve_token(args) -> str | None:
    return args.token or os.environ.get("HF_TOKEN") or os.environ.get("HUGGINGFACE_TOKEN")


def render_via_hf(args, prompt: str, seed: int, raw: Path) -> None:
    """hf-api (Inference Providers) or hf-endpoint (your own Endpoint)."""
    from huggingface_hub import InferenceClient
    token = resolve_token(args)
    if args.backend == "hf-endpoint":
        if not args.endpoint:
            raise SystemExit("--endpoint URL is required for the hf-endpoint backend")
        client = InferenceClient(base_url=args.endpoint, api_key=token)
        model = None  # the endpoint *is* the model
    else:
        if not token:
            raise SystemExit(
                "provide a Hugging Face token via --token or HF_TOKEN "
                "(https://huggingface.co/settings/tokens; Inference Providers must be funded)"
            )
        client = InferenceClient(provider=args.provider, api_key=token)
        model = args.model or "Wan-AI/Wan2.2-T2V-A14B"
    video = client.text_to_video(
        prompt, model=model, negative_prompt=NEGATIVE,
        num_frames=args.num_frames, seed=seed,
    )
    raw.write_bytes(video)


def _load_local_pipeline(args):
    """Loads (and caches) a diffusers text-to-video pipeline on the local GPU."""
    model = args.model or LOCAL_DEFAULTS[args.pipeline]
    key = (args.pipeline, model, args.offload)
    if key in _local_pipe_cache:
        return _local_pipe_cache[key]

    import torch
    dtype = torch.bfloat16
    if args.pipeline == "wan":
        from diffusers import WanPipeline
        pipe = WanPipeline.from_pretrained(model, torch_dtype=dtype)
    elif args.pipeline == "cogvideox":
        from diffusers import CogVideoXPipeline
        pipe = CogVideoXPipeline.from_pretrained(model, torch_dtype=dtype)
    elif args.pipeline == "ltx":
        from diffusers import LTXPipeline
        pipe = LTXPipeline.from_pretrained(model, torch_dtype=dtype)
    elif args.pipeline == "hunyuan":
        from diffusers import HunyuanVideoPipeline
        pipe = HunyuanVideoPipeline.from_pretrained(model, torch_dtype=dtype)
    elif args.pipeline == "mochi":
        from diffusers import MochiPipeline
        pipe = MochiPipeline.from_pretrained(model, torch_dtype=dtype)
    else:
        raise SystemExit(f"unknown --pipeline '{args.pipeline}'")

    if args.offload:
        pipe.enable_model_cpu_offload()   # fits big models on ~24GB cards
    else:
        pipe.to("cuda")
    _local_pipe_cache[key] = pipe
    return pipe


def render_via_local(args, prompt: str, seed: int, raw: Path) -> None:
    """Self-hosted diffusers generation on your own GPU (no API)."""
    import torch
    from diffusers.utils import export_to_video
    pipe = _load_local_pipeline(args)
    kwargs = dict(
        prompt=prompt,
        negative_prompt=NEGATIVE,
        num_frames=args.num_frames,
        num_inference_steps=args.steps,
        guidance_scale=args.guidance,
        generator=torch.Generator(device="cpu").manual_seed(seed),
    )
    if args.height and args.width:
        kwargs.update(height=args.height, width=args.width)
    frames = pipe(**kwargs).frames[0]
    export_to_video(frames, str(raw), fps=args.fps)


def render(args, prompt: str, seed: int, raw: Path) -> None:
    if args.backend == "local":
        render_via_local(args, prompt, seed, raw)
    else:
        render_via_hf(args, prompt, seed, raw)


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

    where = {
        "hf-api": f"hf-api {args.provider}:{args.model or 'Wan2.2-T2V-A14B'}",
        "hf-endpoint": f"endpoint {args.endpoint}",
        "local": f"local diffusers:{args.pipeline}",
    }[args.backend]

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    failures = 0
    for schema in schemas:
        ex_id = schema["exercise_id"]
        prompt = build_prompt(schema)
        for seed in range(args.seeds):
            suffix = "" if args.seeds == 1 else f".seed{seed}"
            out = OUTPUT_DIR / f"{ex_id}{suffix}.mp4"
            print(f"▶ {ex_id} (seed {seed}) via {where} …")
            raw = None
            try:
                with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp:
                    raw = Path(tmp.name)
                render(args, prompt, seed, raw)
                post_process(raw, out, pingpong=args.pingpong)
                print(f"  ✓ wrote {out.relative_to(REPO_ROOT)} "
                      f"({out.stat().st_size // 1024} KB)")
            except Exception as e:  # keep going; report at the end
                failures += 1
                print(f"  ✗ {ex_id} seed {seed} failed: {e}", file=sys.stderr)
            finally:
                if raw is not None:
                    raw.unlink(missing_ok=True)

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
    p.add_argument("--backend", choices=["hf-api", "hf-endpoint", "local"],
                   default="hf-api",
                   help="hf-api = Inference Providers; hf-endpoint = your own HF "
                        "Endpoint (--endpoint); local = diffusers on your GPU "
                        "(--pipeline). Default: %(default)s")
    p.add_argument("--model", default=None,
                   help="model id (default depends on backend/pipeline)")
    p.add_argument("--provider", default="fal-ai",
                   help="HF Inference Provider for hf-api (default: %(default)s)")
    p.add_argument("--endpoint", default=None,
                   help="base URL of your HF Inference Endpoint (hf-endpoint backend)")
    p.add_argument("--pipeline", choices=list(LOCAL_DEFAULTS), default="wan",
                   help="diffusers pipeline for the local backend (default: %(default)s)")
    p.add_argument("--offload", action="store_true",
                   help="local: enable CPU offload to fit big models on ~24GB GPUs")
    p.add_argument("--token", default=None, help="HF token (falls back to $HF_TOKEN)")
    p.add_argument("--exercise", nargs="*", default=None,
                   help="only these exercise ids (default: all)")
    p.add_argument("--seeds", type=int, default=1,
                   help="clips to generate per exercise (default: 1)")
    p.add_argument("--num-frames", type=int, default=81, dest="num_frames",
                   help="frames to request/generate (default: 81)")
    p.add_argument("--steps", type=int, default=50,
                   help="local: denoising steps (default: 50)")
    p.add_argument("--guidance", type=float, default=6.0,
                   help="local: classifier-free guidance scale (default: 6.0)")
    p.add_argument("--fps", type=int, default=16,
                   help="local: fps used when encoding frames (default: 16)")
    p.add_argument("--height", type=int, default=None, help="local: frame height")
    p.add_argument("--width", type=int, default=None, help="local: frame width")
    p.add_argument("--pingpong", action="store_true",
                   help="make a seamless forward+reverse loop")
    p.add_argument("--dry-run", action="store_true",
                   help="print the prompts without calling any API")
    return generate(p.parse_args())


if __name__ == "__main__":
    sys.exit(main())
