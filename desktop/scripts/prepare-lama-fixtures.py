#!/usr/bin/env python3
"""Explicit CI fixtures for the fixed desktop LaMa conversion. No app downloads."""
import argparse, hashlib, pathlib, struct, subprocess, tempfile, urllib.request, zlib
PINS = {
 'lama_fp32.onnx': (208044816, '1faef5301d78db7dda502fe59966957ec4b79dd64e16f03ed96913c7a4eb68d6', 'https://huggingface.co/Carve/LaMa-ONNX/resolve/c3c0c9e468934d62e79c329e35d82dd09ff8c444/lama_fp32.onnx'),
 'trustmark-encoder-q.onnx': (17312208, '19b3d1b25836130ffd78775a8f61539f993375d1823ef0e59ba5b8dffb4f892d', 'https://github.com/marcomorosi06/FFmpegGUI/releases/download/ai-markers-1/trustmark-encoder-q.onnx'),
 'trustmark-decoder-q.onnx': (47401222, 'ee3268f057c9dabef680e169302f5973d0589feea86189ed229a896cc3aa88df', 'https://github.com/marcomorosi06/FFmpegGUI/releases/download/ai-markers-1/trustmark-decoder-q.onnx'),
}
def valid(path, size, digest):
 if not path.is_file() or path.stat().st_size != size: return False
 h=hashlib.sha256()
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''): h.update(b)
 return h.hexdigest()==digest

def png(path):
 def chunk(t,b): return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
 rows=b''.join(b'\x00'+b''.join(bytes((255,255,255,255)) if 255<=x<356 and 155<=y<266 else bytes(4) for x in range(640)) for y in range(360))
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',640,360,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b''))
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
 markers=a.output.parent/'ffmpeggui-markers';markers.mkdir(exist_ok=True)
 for name,(size,digest,url) in PINS.items():
  dest=(markers if name.startswith('trustmark') else a.output)/name
  if valid(dest,size,digest): continue
  with tempfile.NamedTemporaryFile(dir=dest.parent,delete=False) as f: stage=pathlib.Path(f.name)
  try:
   with urllib.request.urlopen(url,timeout=180) as r,stage.open('wb') as out:
    count=0
    while True:
     data=r.read(1048576)
     if not data: break
     count+=len(data)
     if count>size: raise RuntimeError('Oversized model '+name)
     out.write(data)
   if not valid(stage,size,digest): raise RuntimeError('Model pin mismatch '+name)
   stage.replace(dest)
  finally: stage.unlink(missing_ok=True)
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','testsrc2=size=640x360:rate=1','-vf','drawbox=x=260:y=160:w=91:h=101:color=black:t=fill','-frames:v','1','-threads','1',str(a.output/'source.png')],check=True)
 png(a.output/'mask.png')
 print('Verified exact LaMa and TrustMark fixtures',flush=True)
if __name__=='__main__': main()
