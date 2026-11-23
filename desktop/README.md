# FFmpegGUI Linux port, development preview

This is a native Compose Desktop port of the edited Android app at `9f55613`.
It is **not finished** and is not full Android feature parity. It is not a
Python rewrite or an Android emulator. Android source and master are untouched.

## Target

Preparing Debian amd64 packaging for Mohamed's Intel Zorin OS machine.
The Zorin release is unknown. CI builds on Ubuntu 22.04, but that does not
establish compatibility with every Zorin release or the user's graphics/audio.

The package includes Java. It declares `ffmpeg` and `fonts-dejavu-core` as apt
dependencies, instead of silently expecting unspecified executables or fonts.
A graphical package installer may need internet and administrator permission
to install those dependencies. Actual editing and AI inference are offline.
No media is uploaded. FFmpeg/ffprobe use the distribution-maintained
packages so their security updates remain available. Unsupported filters are
errors, never silently removed. The supported FFmpeg surface is still being
tested, not every command the Android FFmpegKit runtime can generate.

## Build

Java 17 and Gradle 8.14.3:

```
gradle -p desktop :shared-core:test nativeChecks packageDeb createDistributable
```

Generate integration fixtures first:

```
python3 desktop/scripts/make-fixtures.py /tmp/ffmpeggui-fixtures
```

## Architecture

`shared-core` compiles the original `core/src/main/kotlin` directly with no
fork. The production smart-panel file, state types and colors are generated
from the original app source each build. Only Android string-resource lookup
is replaced, with values from the original strings.xml. This keeps one source
for those screens. Other screens may need API-specific migrations: Desktop
currently uses Kotlin 2.1.20 / Compose 1.8.2, unlike Android's newer versions.
Compilation of these panels does not prove all Android expressive UI compiles.

DesktopMedia replaces platform probing, cancellation-aware process execution,
frame capture and output handling. Timeline, cut plans, effects, command
building, project model JSON and smart-detector logic come from the shared core.

## Current behavior

- Open video, audio and still images. Decode source frames and seek on the
  edited timeline. The source-frame view is labelled, it does not pretend to
  show effects. Render edited preview generates the actual edited output and
  plays it inside the editor with an audio-sample clock, including cuts and effects. Physical target audio remains unverified.
- Split, remove and reorder clips. Undo (50 in-memory history entries).
- Speed, volume, rotation, horizontal flip, brightness, contrast, saturation.
- MP4/MKV/AVI/GIF and common audio/image output formats supported by the UI.
- Real scene and beat analysis, selectable boundaries and application to the
  timeline; beat snapping applies to manual splits.
- Transcript line cut/keep and word seek use original core semantics. Native
  speech and subject inference/model setup passed mouse-clicked GUI and extracted-package worker checks. Native engines run in isolated local processes. Target Zorin installation is still pending.
- Save/open Linux project envelope uses original Timeline/Effects JSON. This
  is not yet an Android project-import/export compatibility guarantee.
- Export uses a temporary file then atomic move. Source overwrite is refused;
  an incomplete output is removed. Cancellation terminates the subprocess.

See docs/PARITY.md for the full outstanding scope. A package appearing in CI
artifacts is a development milestone, not completion of the Linux request.

## Licence

EUPL-1.2, same as the app. See ../LICENSE. Native dependencies retain their own
licences. Model weights are not committed to git. No paid services are used.

### Scene stems

The Scene stems tab exports dialogue, music and effects from the selected
original source, not the edited timeline. Import the original pinned
`bandit-v2-multi-int8.onnx` using Models. No model is downloaded automatically.
Bandit v2 model authors: Karn N. Watcharasupat, Chih-Wei Wu and Iroro Orife.
Model licence: CC BY-SA 4.0; upstream code: Apache 2.0. The original
licence, notice and provenance files travel with each exported directory.
The model release is linked from Models; the pinned SHA-256 is checked
both on import and on use. No model weights are included in this package.

This path uses the original shared separator in a disposable process, with
three stereo 48 kHz WAV outputs. It requires at least 1.5 GiB free RAM and
limits each source to ten minutes. A short speech-only fixture peaked near
1.13 GiB RSS with CPU memory arena/pattern reuse disabled. This is not a
general memory or audio-quality guarantee. Existing output directories
are rejected; failures and cancellation remove the new partial directory.
Review the results. Instrument splitting and automatic timeline integration
are not yet ported.

Opt-in actual-model check: set `FFMPEGGUI_STEMS_CHECKS` to a folder containing
`input.wav` and the pinned model. It verifies independent durable outputs,
durations, notices, existing-folder rejection and cancellation cleanup.
It writes `proof-output` and refuses to replace it.
