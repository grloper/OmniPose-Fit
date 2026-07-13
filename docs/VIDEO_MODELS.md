# Self-hosting a text-to-video model for OmniPose Fit previews

`tools/generate_previews.py` can drive three backends (`--backend`):

| Backend | What it is | When to use |
| --- | --- | --- |
| `hf-api` (default) | Hugging Face Inference Providers (fal/Replicate/…) | Fastest start, pay-per-clip, no GPU needed |
| `hf-endpoint` | Your own HF Inference Endpoint (`--endpoint URL`) | Host it yourself on HF's infra, private + scalable |
| `local` | `diffusers` in-process on your own GPU (`--pipeline`) | Full self-host, zero per-clip cost |

All three write straight into `app/src/main/assets/previews/{exercise}.mp4`, then
the app auto-detects the clips — no code change.

## Model picker (self-hosting, early 2026)

| Model | HF id | Min VRAM | Notes for our use case |
| --- | --- | --- | --- |
| **Wan 2.2 T2V A14B** | `Wan-AI/Wan2.2-T2V-A14B-Diffusers` | ~48–80 GB (24 GB via ComfyUI GGUF quant) | **Best open human motion** — top pick if you have big iron or rent an A100/H100 |
| **Wan 2.2 TI2V 5B** | `Wan-AI/Wan2.2-TI2V-5B-Diffusers` | ~24 GB (single RTX 4090), 720p | **Sweet spot for consumer self-host** |
| HunyuanVideo | `tencent/HunyuanVideo` | ~45–60 GB (24 GB w/ offload, slow) | Cinematic realism, heavier |
| Mochi 1 | `genmo/mochi-1-preview` | ~24 GB+ (multi-GPU ideal) | Excellent motion, VRAM-hungry |
| CogVideoX-1.5 5B | `THUDM/CogVideoX1.5-5B` | ~16–24 GB | Easiest diffusers support, solid quality |
| LTX-Video | `Lightricks/LTX-Video` | ~12–24 GB, **very fast** | Best for rapid iteration; fidelity below Wan/Hunyuan |

**Recommendation for exercise demos:** Wan 2.2 — the A14B if you can rent an
A100 80 GB (~$1.5–2/hr on RunPod/Vast; a full set of 19 clips is well under an
hour, i.e. a few dollars), or the TI2V-5B if you're on a 24 GB consumer card.

### The form-accuracy caveat (important)

Pure **text-to-video won't guarantee exact joint angles** — it renders a
plausible squat, not *your* 85° squat. For provably-correct form, self-host a
**pose-controlled** variant and drive it with a skeleton:

- **Wan 2.2 Animate / VACE** (ControlNet-style pose conditioning), or
- image-to-video from a good first frame.

You can generate the driving skeleton *from the app's own schemas* (the exact
angle windows in `assets/exercises/*.json`), so the footage matches what the
tracker coaches by construction. Ask and I'll add a Blender/OpenPose driver.

## Quick start (local, single GPU)

```bash
pip install "diffusers>=0.32" transformers accelerate torch imageio-ffmpeg
# consumer 24 GB card:
python tools/generate_previews.py \
  --backend local --pipeline wan \
  --model Wan-AI/Wan2.2-TI2V-5B-Diffusers --offload --pingpong
# fast iteration on smaller cards:
python tools/generate_previews.py --backend local --pipeline ltx --seeds 4
```

## Quick start (your own HF Inference Endpoint)

Deploy any text-to-video model as an Endpoint, then:

```bash
python tools/generate_previews.py \
  --backend hf-endpoint --endpoint https://xxxx.endpoints.huggingface.cloud \
  --token hf_...
```

## Rent-a-GPU options

RunPod, Vast.ai, Lambda, Modal, and fal serverless all give CUDA GPUs by the
minute. A100 80 GB ≈ $1.5–2/hr, H100 ≈ $2–3/hr. Apple Silicon (MPS) can run
LTX-Video / CogVideoX slowly; the 14B-class models are impractical on Mac.
