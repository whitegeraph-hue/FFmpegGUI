#!/usr/bin/env python3
"""Generated media only. No personal content or network access."""
import math,struct,wave,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]); root.mkdir(parents=True,exist_ok=True)
rate=16000
for name in ['beat','silence']:
 with wave.open(str(root/(name+'.wav')),'wb') as w:
  w.setparams((1,2,rate,0,'NONE','not compressed'))
  values=[]
  for i in range(rate*8):
   t=i/rate
   if name=='beat':
    phase=t%0.5
    value=.7*math.sin(2*math.pi*900*t)*math.exp(-phase*60) if phase<.09 else 0
   else: value=.4*math.sin(2*math.pi*440*t) if t<2 or t>4 else 0
   values.append(struct.pack('<h',int(value*32767)))
  w.writeframes(b''.join(values))
def run(*args): subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y',*args],check=True)
run('-f','lavfi','-i','color=red:size=640x360:rate=25:duration=2','-f','lavfi','-i','color=blue:size=640x360:rate=25:duration=2','-f','lavfi','-i','color=yellow:size=640x360:rate=25:duration=2','-i',str(root/'beat.wav'),'-filter_complex','[0:v][1:v][2:v]concat=n=3:v=1:a=0[v]','-map','[v]','-map','3:a','-t','6','-c:v','libx264','-pix_fmt','yuv420p','-c:a','aac',str(root/'scenes.mp4'))
run('-f','lavfi','-i','testsrc2=size=640x360:rate=25','-i',str(root/'beat.wav'),'-t','8','-c:v','libx264','-pix_fmt','yuv420p','-c:a','aac',str(root/'video.mp4'))
run('-i',str(root/'video.mp4'),'-an','-c:v','copy',str(root/'silent.mp4'))
print(root)

with wave.open(str(root/'impulses.wav'),'wb') as w:
 w.setparams((1,2,48000,0,'NONE','not compressed'))
 w.writeframes(b''.join(struct.pack('<h',int(10000*math.sin(2*math.pi*600*i/48000))) if any(a<=i/48000<a+.01 for a in (.05,.5,1.,1.8)) else struct.pack('<h',0) for i in range(96000)))
