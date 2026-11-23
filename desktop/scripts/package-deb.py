#!/usr/bin/env python3
"""Add explicit distribution-maintained runtime dependencies and licence to jpackage output."""
import subprocess,tempfile,sys,shutil
from pathlib import Path
path=Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory() as folder:
 subprocess.run(['dpkg-deb','-R',str(path),folder],check=True)
 control=Path(folder)/'DEBIAN/control'
 lines=control.read_text().splitlines()
 found=False
 for i,line in enumerate(lines):
  if line.startswith('Depends:'):
   dependencies=[d.strip() for d in line.split(':',1)[1].split(',') if not d.strip().startswith('libc6')]
   dependencies += ['libc6 (>= 2.27)', 'ffmpeg', 'fonts-dejavu-core', 'espeak-ng']
   lines[i]='Depends: '+', '.join(dict.fromkeys(dependencies))
   found=True
 if not found:lines.append('Depends: libc6 (>= 2.27), ffmpeg, fonts-dejavu-core, espeak-ng')
 control.write_text('\n'.join(lines)+'\n')
 license_path=Path(folder)/'usr/share/doc/ffmpeggui-linux/copyright'
 license_path.parent.mkdir(parents=True,exist_ok=True)
 license_path.write_text((Path(__file__).resolve().parents[2]/'LICENSE').read_text())
 # Own the desktop entry as a normal package file. jpackage's xdg-desktop-menu
 # script fails on a headless machine with no desktop-directories directory.
 # dpkg installs/removes this file even when no graphical session is running.
 entries=list((Path(folder)/'opt/ffmpeggui-linux/lib').glob('*.desktop'))
 assert len(entries)==1,entries
 menu=Path(folder)/'usr/share/applications'
 menu.mkdir(parents=True,exist_ok=True)
 shutil.copy2(entries[0],menu/entries[0].name)
 for name in ('postinst','prerm'):
  script=Path(folder)/'DEBIAN'/name
  if script.exists():
   script.write_text('\n'.join(line for line in script.read_text().splitlines() if not line.strip().startswith('xdg-desktop-menu '))+'\n')
 for name in ('preinst','postinst','prerm','postrm'):
  script=Path(folder)/'DEBIAN'/name
  if script.exists():subprocess.run(['sh','-n',str(script)],check=True)
 built=path.with_name(path.name+'.repacked')
 try:
  subprocess.run(['dpkg-deb','--build','--root-owner-group',folder,str(built)],check=True)
  built.replace(path)
 finally:built.unlink(missing_ok=True)
subprocess.run(['dpkg-deb','-f',str(path),'Package','Version','Architecture','Depends'],check=True)
