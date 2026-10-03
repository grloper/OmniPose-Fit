"""Reproduce original lightweight PCM feedback; no recordings, dependencies or network."""
from pathlib import Path
import wave, math, struct

raw = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
raw.mkdir(parents=True, exist_ok=True)
for name, notes, duration in [
    ("rep_success", [523.25, 659.25], .11),
    ("target_success", [523.25, 659.25, 783.99, 1046.50], .11),
    ("tempo_tick", [880.0], .045),
]:
    rate = 22050
    samples = []
    for hz in notes:
        for i in range(int(rate * duration)):
            t = i / rate
            envelope = min(t / .008, 1) * math.exp(-t * 22) * min((duration - t) / .02, 1)
            value = (math.sin(2 * math.pi * hz * t) + .15 * math.sin(4 * math.pi * hz * t)) * envelope * .32
            samples.append(int(value * 32767))
    with wave.open(str(raw / (name + ".wav")), "wb") as out:
        out.setparams((1, 2, rate, 0, "NONE", "not compressed"))
        out.writeframes(struct.pack("<" + "h" * len(samples), *samples))
    print(name, "duration", len(samples) / rate, "peak", max(abs(v) for v in samples) / 32768)
