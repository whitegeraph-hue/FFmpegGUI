#!/usr/bin/env python3
"""Explicit CI-only pinned Bandit fixture. No app auto-download."""
import argparse,hashlib,pathlib,subprocess,urllib.request,tempfile
p=argparse.ArgumentParser();p.add_argument('--output',required=True,type=pathlib.Path);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
dest=a.output/'bandit-v2-multi-int8.onnx'
digest='3a4a054b8e07c553d5f19f9f72ad52a1d5049c6355e0512f33b9e2f5ae7579a9'
if not dest.is_file() or dest.stat().st_size!=40260047 or hashlib.sha256(dest.read_bytes()).hexdigest()!=digest:
 with tempfile.NamedTemporaryFile(dir=a.output,delete=False) as f: stage=pathlib.Path(f.name)
 try:
  with urllib.request.urlopen('https://github.com/marcomorosi06/FFmpegGUI/releases/download/bandit-int8-onnx-1/bandit-v2-multi-int8.onnx',timeout=180) as r,stage.open('wb') as out:
   total=0
   while True:
    b=r.read(1048576)
    if not b:break
    total+=len(b)
    if total>40260047:raise ValueError('Oversized model')
    out.write(b)
  assert stage.stat().st_size==40260047 and hashlib.sha256(stage.read_bytes()).hexdigest()==digest
  stage.replace(dest)
 finally:stage.unlink(missing_ok=True)
subprocess.run(['ffmpeg','-v','error','-y','-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-t','3','-ac','2',str(a.output/'input.wav')],check=True)
