#!/usr/bin/env python3
"""Opt-in CI fixture download. Original app pins, no application auto-download."""
import argparse
import hashlib
from pathlib import Path
import shutil
import tempfile
import urllib.request

PINS = {
    'kokoro.tar.bz2': (349906910, 'c5f7e2d2caf082bc1d20fb70334a61d99d20b484500aad32e7cf84c128ea3298',
        'https://github.com/modelship-ai/model-bundles/releases/download/tts/kokoro/kokoro-multi-lang-v1_0-c5f7e2d2.tar.bz2'),
    'audioseal-generator.onnx': (58960229, 'ecb230f2d38de821f72f04eda9c40b82f102e058263848f892be4f6e189dd0e7',
        'https://github.com/marcomorosi06/FFmpegGUI/releases/download/ai-markers-1/audioseal-generator.onnx'),
    'audioseal-detector.onnx': (34754353, 'a389f9a5e3fa67a6a3a13480c3799875a5f3251d2d542fada6bd93b5c2bb53fa',
        'https://github.com/marcomorosi06/FFmpegGUI/releases/download/ai-markers-1/audioseal-detector.onnx'),
}

def valid(path, size, sha):
    if not path.is_file() or path.stat().st_size != size:
        return False
    d = hashlib.sha256()
    with path.open('rb') as f:
        for data in iter(lambda: f.read(1024 * 1024), b''):
            d.update(data)
    return d.hexdigest() == sha


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', required=True, type=Path)
    args = p.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for name, (size, sha, url) in PINS.items():
        target = args.output / name
        if valid(target, size, sha):
            print('Verified cached', name, flush=True)
            continue
        with tempfile.NamedTemporaryFile(prefix='.download-', dir=args.output, delete=False) as f:
            stage = Path(f.name)
        try:
            with urllib.request.urlopen(url, timeout=120) as response, stage.open('wb') as out:
                copied = 0
                while True:
                    data = response.read(1024 * 1024)
                    if not data:
                        break
                    copied += len(data)
                    if copied > size:
                        raise ValueError('Fixture exceeds pinned size: ' + name)
                    out.write(data)
            if not valid(stage, size, sha):
                raise ValueError('Original fixture pin mismatch: ' + name + '. Do not weaken the pin.')
            stage.replace(target)
            print('Verified', name, flush=True)
        finally:
            stage.unlink(missing_ok=True)

if __name__ == '__main__':
    main()
