# Arch Linux x86_64 preview

This package is being qualified alongside the Debian/Zorin version. It is not
an AUR submission or an official Arch repository package. No release/tag exists.

The package installs a self-contained Compose app and Java runtime under
`/opt/ffmpeggui-linux`, a launcher in `/usr/bin`, and a desktop entry and icon.
FFmpeg, DejaVu fonts, eSpeak NG and native display/audio libraries are pacman
dependencies. Model weights are not included. Existing model import gates apply.
Bundled runtime licences/notices and corresponding source stay in the app image;
the project EUPL licence is also under `/usr/share/licenses/ffmpeggui-linux`.

After downloading a qualified `.pkg.tar.zst`, inspect its metadata with
`pacman -Qip FILE`, then install with `sudo pacman -U FILE`. Launch FFmpegGUI
from the application menu or `ffmpeggui-linux`. Remove with
`sudo pacman -R ffmpeggui-linux`. Do not install the Debian archive on Arch.

To build the package from this branch, build the app image first:

```
gradle -p desktop createDistributable
python3 desktop/scripts/package-arch.py --image desktop/build/compose/binaries/main/app/ffmpeggui-linux --output /tmp/ffmpeggui-arch
cd /tmp/ffmpeggui-arch
makepkg
```

Run makepkg as a normal user, with base-devel installed. The staging helper
writes hashes for the local image and packaging inputs. The checked-in recipe
is a template, not an unchecked remote binary download. Keep the staged inputs
and PKGBUILD together for inspection/rebuilding.

The Arch workflow builds with makepkg in current rolling Arch userspace, installs
with pacman, checks the installed bundled launcher, native media graphs, pinned
models and isolated workers, and removes the package. This does not prove
physical Intel graphics/audio, Wayland, touchpad or HiDPI behavior. Arch is
rolling, so record package versions with any failure report.

Namcap currently flags the bundled JVM's class files as needing java-runtime
and its ELF layout under /opt. This package intentionally bundles its JVM and
does not change the system Java selection. Unused-library/dependency warnings
need to be read with the bundled runtime and subprocess/dlopen dependencies in
mind; they are not represented as a clean namcap result.

Physical audio remains untested. PipeWire users may need pipewire-alsa for the
Java ALSA output path. Pure Wayland sessions need XWayland for this X11 GUI.
