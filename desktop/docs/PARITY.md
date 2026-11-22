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
| Preview | Source frame adapter | Honest labelled source-frame seek; render edited preview includes effects/audio via real export and integrated sample-clock playback. Android live-preview parity not done. |
| Audio editor/mastering | DSP/models/builder preserved | Speed/volume, audio bitrate/bitdepth and formats exposed. Ten-band EQ, cuts, pitch, gate/compression/limiter, echo/reverb and stereo controls implemented. Actual24bit44100Hz mastered WAV tested. Waveforms, stems/tracks and modulation still missing. |
| Photo editor | Image/vector/text-art logic preserved | Basic still export path exposed. Crop/rotation/mirror/detail, perspective/straighten, exposure/highlights/shadows/warmth/tint/hue/fade, filters/effects/corners controls and saved ImageEdit wired. Actual transformed PNG tested and clicked rendered image inspected. SVG tracing and ASCII/ANSI exports now use original core with desktop glyph atlas and format controls, tested actual files. Layers, shapes, brush, masks, HDR and live art preview still unported. |
| GIF editor | GIF settings/builder preserved | Width/fps/palette/dither/loops/smaller controls with real240px10fps export test. Exact live palette preview missing. |
| Subtitles/style/lyrics | Full core preserved | Transcript UI only initially. SRT/TXT retimed export and ASS burn-in/style controls implemented;14 native integration checks include actual burnt pixels and240p15fps. Custom fonts/colours, external subtitle import and lyrics workflows pending. |
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

Subtitle and encoding continuation: mouse-driven Listen, Subtitles tab and burn
switch produced17words and visible style controls. Actual ASS render pixels
inspected (Hello Linux, outlined yellow, bottom centre). SRT removes cut words
and retimes2x correctly in native test. Custom font/colour and external subtitle
file import still unported. Original core stays unchanged.

Audio/GIF continuation: ten-band EQ, low/high cuts, pitch, noise gate,
compressor, limiter, echo, reverb, stereo width/balance/mono/swap are now bound
to shared AudioEffects and persisted with ModelJson. The16native checks verify
actual mastered PCM differs and output is pcm_s24le/44100Hz. GIF palette size,
width, fps, dither, loop and smaller options are exposed and persisted; real
240px10fps GIF checked. Waveforms, live DSP, modulation and stems remain gaps.

Photo continuation:18native assertions now include original-preserving still
crop/rotation, perspective/straightening/tone/filter/effect/corners and ImageEdit
save/load. Actual rendered PNG and clicked horizontal-perspective preview were
inspected. Preview now displays the rendered frame rather than leaving it
unused, clears on change and has a separate still-image label.

Art export continuation: original VectorTracer and TextArtRenderer/Writer now
receive edited PNG pixels, with desktop DejaVu Sans Mono glyph measurements.
SVG settings, text modes/columns/paper/dither and ANSI colour depth exposed.
Real SVG parses with paths, ASCII has expected columns, ANSI contains colour
codes; project settings roundtrip tested (19native assertions). Actual SVG and
ASCII previews were rendered and inspected separately. The editor honestly
blocks live art preview until a renderer is ported. WEBP now has distro libwebp adapter, actual320x180alpha output tested.

Text layers: whole-output TextOverlay add/edit/remove, X/Y/size/opacity, colour
swatches, bold/outline/box, hidden, stack order, start/end and fades are exposed
and persisted. Hidden/obsolete clip captions are filtered in request mapping,
as Android does.20native checks include real time-gated caption pixels and
roundtrip. UI add-layer click and rendered caption pixels inspected. Clip
binding, rotation/blend/stretch and keyframe UI remain missing.

WebP continuation: FFmpeg PNG render plus Linux libwebp encode, quality and
max-side controls.21native assertions include decoded WebP dimensions and
transparent corners. This deliberately uses distro FFmpeg capability, failing
if libwebp is unavailable rather than silently changing output format.

Caption animation: capture X/Y/size/opacity at the output playhead, list/remove
keys and select original easing modes.23native checks include actual changing
caption position over rendered time, persistence, plus placed sound mix.
Sound tracks: multiple local files, output placement, trim, repeats, speed,
pitch, EQ, fades, normalize, mute, remove, project save/load and overwrite
protection. Actual delayed/repeated mix is silent before/after its range.
Waveform and sound keyframes, clip-bound text, custom curves remain missing.

Canvas/tags/image-layer continuation: output ratio/fit controls, metadata editor,
whole-output still image layers with placement/opacity/rotation/stretch/flips,
colour, blend, stacking and times/fades are bound to original core.25native
checks include actual portrait output tags and time-gated image composite,
roundtrips and image-source overwrite protection. Easing options now wrap.
Sound-file chooser was clicked successfully for en.wav, controls inspected.
Image-file chooser and wrapped easing final visual verification remain pending.
