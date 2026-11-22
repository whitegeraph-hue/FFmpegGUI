# Linux parity inventory

Base: edited Android `9f55613e9ca3ef97c8f762142ed5a4d0d9ac3157`.
Status is implementation evidence, not a promise that unchecked behavior works.

| Area | Shared source preserved | Linux status / remaining work |
|---|---|---|
| Core command generation and models | All core sources | Clean CI compile and 926 tests pass. Narrow real-media integration tests added. Broad FFmpeg filter compatibility still open. |
| Basic import/probe/export | Models and builder | Desktop adapter compiled. Real source frame/seek and timeline export tested. Still import, more formats/rotation/error cases need broader tests. |
| Video timeline | Timeline/Clip and effects | Split, remove, reorder, undo wired. Multi-file intake, transitions, keyframes, speed curves, overlays, layer stack, text and animations still missing UI. |
| Transcript word seek and keep/cut | TranscriptEdits/wordBlocks | UI and core binding implemented. Speech inference/model setup wired and compiled; final app runtime/UI checks pending. |
| Pause/filler cuts | PauseCuts/SilenceDetect + original panel | Wired after words exist, selectable/apply. Actual speech-derived pause/filler test pending. |
| Scene splits | SceneDetect + original panel | Real FFmpeg scene detection passed at known 2s and 4s boundaries. Panel apply and manual UI tests pending. |
| Beat-synced splits | BeatTrack + original panel | Real audio analysis passed 120 BPM fixture. Split/snapping wired. UI test pending. |
| Subject-following crop | FollowCropTrack/SubjectModelIo + original panel | Synthetic moving crop export passed. Native U2-NetP inference/model import wired and compiled; final app runtime/UI checks pending. |
| Preview | Source frame adapter | Honest labelled source-frame seek; render edited preview includes effects/audio via real export and ffplay. Android live-preview parity not done. |
| Audio editor/mastering | DSP/models/builder preserved | Basic speed/volume and formats only. Waveforms, ten-band EQ, compressor, reverb, stems/tracks still need UI/adapters and tests. |
| Photo editor | Image/vector/text-art logic preserved | Basic still export path exposed. Detailed crop/perspective, layers, shapes, brush, masks, HDR, SVG editor, ASCII/ANSI tools still need porting. |
| GIF editor | GIF settings/builder preserved | Basic output only. Palette/dither/frame controls and exact preview missing. |
| Subtitles/style/lyrics | Full core preserved | Transcript UI only initially. Subtitle generation/burn-in/style and lyrics workflows pending. |
| Offline AI cutout/erase/translation/dub/stems | Core algorithms/model definitions preserved | Runtime adapters, model gates and integrity import, licences, speech memory limits, source separation, marking gates still need porting. No generative output exposed without required provenance marks. |
| Project files | Original ModelJson | Linux envelope save/open implemented. Source validation; Android project envelope interoperability not yet supported. Need include all editor state once exposed. |
| Command studio | Parser/catalog/lint/recipes preserved | Read-only generated command initially. Editable commands, lint, recipes and file aliases still missing UI. |
| Metadata/cover/HDR/compression/stabilization | Core preserved | Not exposed or certified on Linux yet. Required adapters and filter availability checks pending. |
| OS integration | None assumed | Debian amd64 with Java runtime and explicit apt FFmpeg/fonts dependencies. Zorin release/actual install/graphics/audio validation pending. No tag or release published. |

## Completion standard

A finished Linux version must not be substituted by the current limited preview.
Continue the inventory until original workflows are implemented and tested or
Mohamed explicitly accepts a documented scope change. No such scope change has
been requested or granted. Master must remain untouched unless separately asked.

Only three production UI files are currently shared in the desktop build, out of roughly 102k lines of Android app code. Most screens remain unported. The newly written desktop shell is not the original Android EditorScreen.

## Later execution evidence

Mouse-driven native file choosers imported all original pinned speech files and
U2-NetP. Actual Listen and Follow buttons then completed through isolated model
workers. This contains an unexplained native GUI coexistence crash, it does not
establish broad target-machine compatibility. Source-classpath GUI and a locally
patched extracted app image were checked; fresh extracted whole-.deb worker and clicked GUI checks passed. System install is still unverified.

Rendered edit playback now runs inside the editor with video frames synchronized
to played PCM sample count, pause and seek. Mouse-clicked transport was checked
with a timed PulseAudio virtual sink. Physical speakers, target audio drivers and
live unrendered effects remain unverified. This replaces external ffplay for the
preview workflow, not the Android live player implementation.

Follow crop is deliberately invalidated after structural timeline edits rather
than retained with stale timestamps. This is more conservative than Android's
remapping; rerun tracking after edits. Single-source project loading is enforced
until multi-source word/track association is ported. Beat sample buffering is
limited to ten minutes to fit the bounded Java heap; streaming analysis remains
work to do. ORT's shipped native x64 library sets a GLIBC 2.27 minimum.
