#!/usr/bin/env python3
"""Exercise the package's actual desktop-entry Exec, without a shell."""
import configparser,shlex,subprocess,sys
from pathlib import Path
parser=configparser.ConfigParser(interpolation=None)
parser.read(sys.argv[1])
entry=parser['Desktop Entry']
assert entry['Type']=='Application'
args=[part for part in shlex.split(entry['Exec']) if part not in ('%f','%F','%u','%U')]
assert args and Path(args[0]).is_file(),args
print('Desktop entry invocation:',args,flush=True)
subprocess.run(args,check=True,timeout=60)
