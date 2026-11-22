#!/usr/bin/env python3
"""Prepare the exact fixture inputs used by DesktopSmartModelsChecks.

Explicit developer/CI utility, not app code. Uses network only when invoked.
Usage: python3 prepare_fixtures.py --repo /path/to/FFmpegGUI --output /tmp/models
Requires Python 3 standard library and ffmpeg on PATH (or --ffmpeg).
"""
import argparse
import hashlib
import pathlib
import shutil
import subprocess
import tempfile
import urllib.request

PINS = {
    "encoder.int8.onnx": (652184281, "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
    "decoder.int8.onnx": (11845275, "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
    "joiner.int8.onnx": (6355277, "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
    "tokens.txt": (93939, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
    "u2netp.onnx": (4574861, "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8"),
    # Checksum of the exact upstream test WAV used by the local passing checks.
    "en.wav": (184608, "148b936b43ce7c546a866e64da059f0458aee2d65e617f16e9d94f06e8d99ed6"),
}
BASE = "https://github.com/marcomorosi06/FFmpegGUI/releases/download/parakeet-onnx-1/"
URLS = {name: BASE + name for name in ("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx")}
URLS["u2netp.onnx"] = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx"
URLS["en.wav"] = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/test_wavs/en.wav"


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for data in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(data)
    return h.hexdigest()


def valid(path, pin):
    return path.is_file() and path.stat().st_size == pin[0] and digest(path) == pin[1]


def obtain(name, out, repo):
    target = out / name
    if valid(target, PINS[name]):
        print("Verified cached", name)
        return
    with tempfile.NamedTemporaryFile(prefix=".fixture-", dir=out, delete=False) as tmp:
        stage = pathlib.Path(tmp.name)
    try:
        if name == "tokens.txt":
            shutil.copyfile(repo / "app/src/main/assets/speech/parakeet-tdt-0.6b-v3-tokens.txt", stage)
        else:
            with urllib.request.urlopen(URLS[name], timeout=120) as response, stage.open("wb") as target_stream:
                shutil.copyfileobj(response, target_stream)
        if not valid(stage, PINS[name]):
            raise RuntimeError("Fixture hash/size mismatch: " + name + ". Do not update pins to work around a changed upstream file.")
        stage.replace(target)
        print("Verified", name)
    finally:
        stage.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for name in PINS:
        obtain(name, args.output, args.repo)
    # Same generator and encoding command as the original local passing check.
    # Bit-identical MP4 bytes are not required across FFmpeg versions; dimensions,
    # duration and actual frame decode/inference are asserted by the Kotlin checks.
    subprocess.run([args.ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
                    "testsrc2=size=320x240:rate=10", "-t", "1.1", "-c:v", "libx264",
                    str(args.output / "video.mp4")], check=True)
    print("Generated video.mp4. Use this directory as the Kotlin checks' first argument, plus --speech-first --speech.")


if __name__ == "__main__":
    main()
