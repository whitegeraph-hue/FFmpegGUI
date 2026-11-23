#!/usr/bin/env python3
"""Preserve corresponding source with each binary without tokens, caches or models."""
import subprocess,sys,tarfile,io
from pathlib import Path
root=Path(__file__).resolve().parents[2]
target=Path(sys.argv[1]);target.parent.mkdir(parents=True,exist_ok=True)
revision=subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()
files=subprocess.check_output(['git','-C',str(root),'ls-files','-z']).decode().split('\0')
with tarfile.open(target,'w:gz') as archive:
 for name in files:
  if not name or not (name.startswith(('core/','desktop/','.github/workflows/linux-desktop.yml','.github/workflows/arch-linux.yml','app/src/main/')) or name in ('LICENSE','README.md','docs/MODELS.md')):continue
  if name.startswith('desktop/libs/') and not name.endswith(('README.md','SHA256SUMS')):continue
  p=root/name
  if p.is_file():
   data=subprocess.check_output(['git','-C',str(root),'show',revision+':'+name])
   entry=tarfile.TarInfo('ffmpeggui-source/'+name);entry.size=len(data);entry.mode=0o755 if p.stat().st_mode & 0o111 else 0o644
   archive.addfile(entry,io.BytesIO(data))
 text=('Source revision: '+revision+'\nRepository: '+subprocess.check_output(['git','-C',str(root),'remote','get-url','origin'],text=True).strip()+'\nNative runtime downloads: desktop/docs/NATIVE-MODELS.md; checksums: desktop/libs/SHA256SUMS\n').encode()
 entry=tarfile.TarInfo('ffmpeggui-source/BUILD-REVISION.txt');entry.size=len(text);archive.addfile(entry,io.BytesIO(text))
print('Corresponding source:',target,'revision',revision,flush=True)
