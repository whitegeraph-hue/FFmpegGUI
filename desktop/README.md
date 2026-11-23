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
  plays it inside the editor with an audio-sample clock, including cuts and effects. Physical target audio remains unverified. If a video cannot open the audio
  device, motion can continue with an explicit silent-video notice. Audio-only
  playback still fails clearly. Decoder failures remain errors.
- Split, duplicate, precise source trim, remove and reorder clips. Drag the small
  arrow handle to an insertion marker, then release to commit one move. Escape
  or releasing outside the timeline cancels. No edge auto-scroll yet; use Fit
  or scroll first, or use the Left/Right buttons. Handles require clips over45dp.
  Timeline-focused Alt+Left/Right reorder in visible output direction, including
  global reverse. Home/End seek to output start/end. These keys stay out of text
  fields. Buttons and keys share drag's occurrence/footage preservation.
  A transition moves with its clip, changing its neighbor/fade pair, as with
  the arrow buttons. Drag is mouse-tested, not physical touchpad/HiDPI qualified.
  Reordering keeps a selected occurrence and its source offset. Undo restores
  the edit, selected clip and seek position (50 in-memory history entries).
  Redo restores the undone edit and selection; a new edit clears Redo. Saving
  does not clear history. Successful project entry clears both directions.
  Text-field Ctrl+Z/Y/Shift+Z stays with text editing, not project history.
- Unsaved project changes are shown in the header. Opening another source or
  project, or closing the window, asks before discarding edits. Keep editing or
  Escape cancels that action. A successful Save project clears the warning;
  failed saves and rejected project loads do not. This is not autosave or crash
  recovery. Save projects explicitly before leaving the app.
- Save remembers the opened or last successfully saved project destination for
  this session. Save as chooses another destination. Ctrl+S saves, Ctrl+Shift+S
  opens Save as, Ctrl+O opens media and Ctrl+Shift+O opens a project. The Open
  shortcuts use the same unsaved-edit guard as the buttons. Failed saves/loads
  and canceled choosers do not change the remembered destination.
- Narrower windows use separate labelled project and timeline action rows.
  Transport controls scroll horizontally rather than forcing the output clock
  into a narrow column. This has virtual-display checks, not physical HiDPI proof.
- Zoomable output-time timeline with separate lanes during transitions and
  original-source thumbnails. Thumbnail tasks are limited to the visible area
  plus a small margin, use one decoder at a time and a 64-image cache. They are
  not live effect previews. A synthetic 100-clip, 3840×2160 source workflow was
  checked at 1366×768; this is not a general large-project performance claim.
- Timeline source peaks are a mono 8kHz envelope, not the edited mix or a true-peak meter. They follow clip source trim/speed/reverse on the output ruler, but do not include gain, fades, DSP, transitions or added tracks. Stereo cancellation is possible. Work is limited to visible clips, one decoder, 20seconds, 30minute sources and16 cached4096-bin envelopes; unsupported inputs show a label.
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
three stereo 48 kHz WAV outputs. It requires at least 2 GiB free RAM and
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

A ten-minute synthetic tone/pink-noise resource check on Ubuntu22.04 completed
in333seconds, three600s outputs, sampled peak1,453,416KiB worker RSS. The
free-memory gate was raised to2GiB to leave headroom above this higher peak.
This does not prove all content/platform memory or sound separation quality.
Source run: https://github.com/whitegeraph-hue/FFmpegGUI/actions/runs/45093189855

### Subtitle translation

Translation tab imports five trusted direct-pair Marian/Opus-MT files, records
SHA-256 at import and checks hashes before every use. These are local integrity
records, not publisher-authenticated pins. Use files from a trusted source.
No automatic downloads. Original SentencePiece/Marian/greedy decoding and
subtitle rewrite are reused inside an isolated worker with700MiB freeRAM
preflight,500cues/100000characters per request and30minute process limit.
Direct pairs only: multilingual and English-pivot routes remain unported.
A cue over1000characters or256tokens rejects; decoder length/repetition
limit rejects instead of replacing subtitles with a known partial result.
These bounds do not guarantee linguistic completeness or correctness.

Import SRT/ASS/SSA first. Translation retains cue times/source binding,
embeds the new subtitles in the project and supports Undo. Inline styling
can change. Review every translation before export. Spoken words stay
separate. Opus-MT authors are University of Helsinki, Jorg Tiedemann and
contributors, ONNX conversions by Xenova. Model licences vary. The tested
en-fr base card lists Apache2.0, not the blanketCC-BY4.0 description in the
original Android model registry. No translation weights are redistributed.
Sources: https://huggingface.co/Helsinki-NLP/opus-mt-en-fr/raw/main/README.md
and https://huggingface.co/Xenova/opus-mt-en-fr/raw/main/README.md .

Opt-in FFMPEGGUI_TRANSLATION_CHECKS accepts a directory with the five en-fr
files; use a fresh XDG_DATA_HOME. Actual test download revision was
28726206f80896b90035bd99cccd5cc1e151f916. Short English/French inference,
clock retention, file-corruption rejection, cancel/retry and oversize cue
checks pass, not broad language/corpus quality proof.

Existing AI picture disclosure is retained when detected in a visual source or
active picture layer, even with metadata stripping enabled. This reads standard
PNG/JPEG XMP and bounded head/tail metadata in other containers. It does not find
unmarked AI or guarantee invisible-watermark retention. Unsupported disclosure
formats refuse output. Picture metadata writers are capped at32MB, so reduce
output size or quality if that check rejects. Generated Ultra HDR gain maps are
preserved when the standard disclosure is added. Generative erase/dub remain
unavailable until their own complete adapter and disclosure checks land.

Magic eraser currently saves a separate new PNG from the original still picture
and an external matching upright alpha mask. Opaque mask pixels are filled,
transparent pixels kept. MI-GAN and TrustMark files must match the original
pins; TrustMark models must be imported first. Up to2MP and1.2GiB available RAM.
The saved PNG must pass invisible TrustMark readback and carry XMP disclosure.
It is not silently made the project source. Later edits or recompression can
lose the invisible mark. Direct mask painting, LaMa and integrated re-editing
are still in progress. Notices/licences are under picture-ai in app resources.
A direct source-pixel erase-mask canvas is now available, with brush width,
unselect, Undo and Clear. For a rotated EXIF JPEG, choose an upright PNG copy
or an external matching upright mask. The mask is separate from project drawing.

Arch Linux x86_64 packaging and validation: [Arch preview](docs/ARCH-LINUX.md).

Timeline ruler: click or drag to seek and focus timeline shortcuts. Seeking
does not select another clip or add an Undo entry. Source frame updates are
debounced; drag seeking is not a live-effects preview.
