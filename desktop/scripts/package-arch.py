#!/usr/bin/env python3
"""Stage a local app image for makepkg. Run makepkg as a regular user on Arch."""
import argparse,hashlib,shutil,tarfile
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--image',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
a=parser.parse_args()
repo=Path(__file__).resolve().parents[2]
out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
image=a.image.resolve()
assert (image/'bin/ffmpeggui-linux').is_file(),image
with tarfile.open(out/'app-image.tar.gz','w:gz') as tar:tar.add(image,arcname='ffmpeggui-linux')
shutil.copy2(repo/'LICENSE',out/'LICENSE')
(out/'ffmpeggui-linux-launcher').write_text('#!/bin/sh\nexec /opt/ffmpeggui-linux/bin/ffmpeggui-linux "$@"\n')
(out/'ffmpeggui-linux.desktop').write_text('[Desktop Entry]\nType=Application\nName=FFmpegGUI\nComment=Native Linux video, audio and picture editor (preview)\nExec=ffmpeggui-linux\nIcon=ffmpeggui-linux\nTerminal=false\nCategories=AudioVideo;AudioVideoEditing;\n')
text=(repo/'desktop/packaging/arch/PKGBUILD').read_text()
files=['app-image.tar.gz','LICENSE','ffmpeggui-linux.desktop','ffmpeggui-linux-launcher']
hashes=[hashlib.sha256((out/f).read_bytes()).hexdigest() for f in files]
text=text.replace("sha256sums=('SKIP' 'SKIP' 'SKIP' 'SKIP')",'sha256sums=('+' '.join(repr(h) for h in hashes)+')')
(out/'PKGBUILD').write_text(text)
print(out)
