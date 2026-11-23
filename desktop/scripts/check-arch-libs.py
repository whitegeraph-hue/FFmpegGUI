#!/usr/bin/env python3
"""Fail if any shipped ELF has an unresolved shared-library dependency."""
import pathlib,subprocess,sys,os
root=pathlib.Path(sys.argv[1]);failed=[];count=0
for path in root.rglob('*'):
 if not path.is_file():continue
 with path.open('rb') as f:
  if f.read(4)!=b'\x7fELF':continue
 count+=1
 env=dict(os.environ,LD_LIBRARY_PATH=':'.join(str(root/p) for p in ['lib/runtime/lib','lib/runtime/lib/server','lib/app']))
 result=subprocess.run(['ldd',str(path)],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,env=env)
 if 'not found' in result.stdout:failed.append((str(path),result.stdout))
print('Checked shipped ELF files:',count)
for path,details in failed:print(path,details)
if failed or count==0:raise SystemExit(1)
