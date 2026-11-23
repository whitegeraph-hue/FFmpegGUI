#!/usr/bin/env python3
"""Run the real isolated stem worker on a caller-supplied clip and sample RSS.
No audio or model weights are downloaded by this script.
"""
import argparse, json, os, pathlib, subprocess, tempfile, time
p=argparse.ArgumentParser()
p.add_argument('--java',required=True);p.add_argument('--classpath',required=True)
p.add_argument('--input',required=True);p.add_argument('--model',required=True)
p.add_argument('--output',required=True);p.add_argument('--timeout',type=int,default=2700)
a=p.parse_args();destination=pathlib.Path(a.output);destination.mkdir()
request=dict(kind='stems',path=str(pathlib.Path(a.input).resolve()),model=str(pathlib.Path(a.model).resolve()))
(destination/'request.json').write_text(json.dumps(request))
env=os.environ.copy();env['FFMPEGGUI_MODEL_WORKER']=str(destination.resolve())
start=time.monotonic();samples=[]
with (destination/'worker.log').open('w') as log:
 process=subprocess.Popen([a.java,'-Xmx256m','-cp',a.classpath,'com.cuscus.ffmpeggui.desktop.MainKt'],env=env,stdout=log,stderr=subprocess.STDOUT)
 try:
  while process.poll() is None:
   elapsed=time.monotonic()-start
   if elapsed>a.timeout:raise TimeoutError('Stem resource check timed out')
   try:
    rss=int(next(line for line in pathlib.Path(f'/proc/{process.pid}/status').read_text().splitlines() if line.startswith('VmRSS:')).split()[1])
    samples.append(dict(seconds=round(elapsed,1),rss_kib=rss))
   except (OSError,StopIteration):pass
   time.sleep(.25)
  if process.wait()!=0:raise RuntimeError((destination/'worker.log').read_text()[-4000:])
 finally:
  if process.poll() is None:process.kill();process.wait()
  (destination/'rss-samples.json').write_text(json.dumps(samples))
summary=dict(elapsed_seconds=round(time.monotonic()-start,1),peak_rss_kib=max(x['rss_kib'] for x in samples),sample_count=len(samples))
for name in ['dialogue.wav','music.wav','effects.wav']:
 f=destination/name
 assert f.stat().st_size>44
 result=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration:stream=sample_rate,channels','-of','json',str(f)]))
 assert result['streams'][0]['sample_rate']=='48000' and result['streams'][0]['channels']==2
 summary[name]=result
(destination/'summary.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary,indent=2))
