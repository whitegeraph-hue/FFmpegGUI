#!/usr/bin/env python3
"""Original test phrase, eSpeak NG 1.50, en, speed145, default pitch/amplitude.
Requires espeak-ng and ffmpeg on PATH. Never uses personal audio or network access.
Checked-in WAVs are qualification inputs, not a bundled sound-library recording.
"""
import subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'app/src/androidTest/assets'
phrase='Please check the old telephone voice. The quick brown fox jumps over the lazy dog. Testing one two three.'
with tempfile.TemporaryDirectory() as folder:
    source=Path(folder)/'speech.wav'
    # Keep en (the synthesizer default used for v1), not a changing system voice.
    subprocess.run(['espeak-ng','-v','en','-s','145','-w',str(source),phrase],check=True)
    for rate in (8000,16000):
        subprocess.run(['ffmpeg','-y','-v','error','-i',str(source),'-ar',str(rate),str(root/f'creative-speech{rate//1000}.wav')],check=True)
