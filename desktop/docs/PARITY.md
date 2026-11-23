# Linux parity inventory

Base: edited Android `9f55613e9ca3ef97c8f762142ed5a4d0d9ac3157`.
Status is implementation evidence, not a promise that unchecked behavior works.

| Area | Shared source preserved | Linux status / remaining work |
|---|---|---|
| Core command generation and models | All core sources | Clean CI compile and 926 tests pass. Narrow real-media integration tests added. Broad FFmpeg filter compatibility still open. |
| Basic import/probe/export | Models and builder | Desktop adapter compiled. Real source frame/seek and timeline export tested. Still import/export tested. More formats/rotation/error cases need broader tests. |
| Video timeline | Timeline/Clip and effects | Split/remove/reorder/undo and multi-video append wired. Clip trim/duplicate, speed/reverse/pitch, volume/fades/framing/colour and capture/list/easing keys exposed. Five basic transitions exposed; companion-effect transitions, speed curves, still/audio-only timeline sources and richer layer interaction remain missing. |
| Transcript word seek and keep/cut | TranscriptEdits/wordBlocks | UI and core binding implemented. Speech inference/model setup wired and compiled; final app runtime/UI checks pending. |
| Pause/filler cuts | PauseCuts/SilenceDetect + original panel | Wired after words exist, selectable/apply. Actual speech-derived pause/filler test pending. |
| Scene splits | SceneDetect + original panel | Real FFmpeg scene detection passed at known 2s and 4s boundaries. Panel apply and manual UI tests pending. |
| Beat-synced splits | BeatTrack + original panel | Real audio analysis passed 120 BPM fixture. Split/snapping wired. UI test pending. |
| Subject-following crop | FollowCropTrack/SubjectModelIo + original panel | Synthetic moving crop export passed. Native U2-NetP inference/model import wired and compiled; final app runtime/UI checks pending. |
| Preview | Source frame adapter | Honest labelled source-frame seek; render edited preview includes effects/audio via real export and integrated sample-clock playback. Android live-preview parity not done. |
| Audio editor/mastering | DSP/models/builder preserved | Speed/volume, audio bitrate/bitdepth and formats exposed. Ten-band EQ, cuts, pitch, gate/compression/limiter, echo/reverb and stereo controls implemented. Actual24bit44100Hz mastered WAV tested. Placed sound tracks implemented and tested; waveforms, stems and modulation still missing. |
| Photo editor | Image/vector/text-art logic preserved | Basic still export path exposed. Crop/rotation/mirror/detail, perspective/straighten, exposure/highlights/shadows/warmth/tint/hue/fade, filters/effects/corners controls and saved ImageEdit wired. Actual transformed PNG tested and clicked rendered image inspected. SVG tracing and ASCII/ANSI exports now use original core with desktop glyph atlas and format controls, tested actual files. Whole-output still image layers implemented. Photo-native layer interaction, shapes, brush, masks, HDR and live art preview still unported. |
| GIF editor | GIF settings/builder preserved | Width/fps/palette/dither/loops/smaller controls with real240px10fps export test. Exact live palette preview missing. |
| Subtitles/style/lyrics | Full core preserved | Transcript UI only initially. SRT/TXT retimed export and ASS burn-in/style controls implemented;14 native integration checks include actual burnt pixels and240p15fps. Custom fonts/colours, external subtitle import and lyrics workflows pending. |
| Offline AI cutout/erase/translation/dub/stems | Core algorithms/model definitions preserved | Runtime adapters, model gates and integrity import, licences, speech memory limits, source separation, marking gates still need porting. No generative output exposed without required provenance marks. |
| Project files | Original ModelJson | Linux envelope save/open implemented. Source validation; Android project envelope interoperability not yet supported. Need include all editor state once exposed. |
| Command studio | Parser/catalog/lint/recipes preserved | Editable command/alias list, original lint/tidy/recipes, installed FFmpeg catalogue and constrained local single-output run now implemented. Arbitrary-file/filter adapters, generated subtitles/AI marks in manual mode, numbered outputs, completion/help panels and full Android studio remain missing. |
| Metadata/cover/HDR/compression/stabilization | Core preserved | Metadata text tags exposed and tested. Cover/HDR/compression/stabilization adapters and filter availability checks pending. |
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
patched extracted app image were checked; fresh extracted whole-.deb worker and clicked GUI checks passed. Ubuntu22.04 clean CI apt dependency install, desktop-entry launch and remove/purge pass. Actual Zorin install remains unverified.

Rendered edit playback now runs inside the editor with video frames synchronized
to played PCM sample count, pause and seek. Mouse-clicked transport was checked
with a timed PulseAudio virtual sink. Physical speakers, target audio drivers and
live unrendered effects remain unverified. This replaces external ffplay for the
preview workflow, not the Android live player implementation.

Follow crop is deliberately invalidated after structural timeline edits rather
than retained with stale timestamps. This is more conservative than Android's
remapping; rerun tracking after edits. Multi-video project loading is supported with per-source words. Follow crop is explicitly blocked for multi-source projects pending coherent tracks. Beat sample buffering is
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
Wrapped easing inspected with all options visible. Image-file chooser remains unverified.

Multi-source continuation: video append, source-selection chips, per-source
speech words, transcript seek/cuts, pause/scene/beat mapping use source indexes.
26native assertions include real video+silent-video join, decoded output,
retimed per-source SRT and project roundtrip. Multi-source subject follow is
explicitly blocked pending coherent tracks. Still/audio-only timeline source
intake and transition editor remain missing. Project source count no longer
artificially limited to one. Layer sound and still image paths are independent.

Clip editor continuation: source trim/duplicate, clip speed/reverse/pitch,
volume/fades/zoom/pan/colour and source-clock animation keys capture/list/remove/
easing are exposed. Basic fade/wipe-left/wipe-right/slide-left/dissolve selection
and length exposed.29native assertions include actual2x/reverse transition
export/duration/decode and a keyframed PCM volume changing from silence to sound.
Clip controls, transition controls and clicked key capture inspected at1280px.
Companion transitions, speed curves, custom easing and richer visual timeline
remain unported. Mixed-type saved multi-source projects are explicitly rejected.

Clock correction: desktop now distinguishes footage/source position from output
position using original PreviewTimeline and expectedOutputDurationMs. Output
layer times/default ends/ranges and captures use output time. Source transcript
seek/cut and pause/scene/beat use footage mapping rather than joined played time.
Desktop reverse-safe cut/split adapters preserve source ranges, effects and
playback-order fades/transitions without editing original core.30native checks
include combined2x+reverse+fade timed picture, caption pixels, PCM pulse timing,
SRT times, reversed-word removal, slow whole speed, reverse split source order.
New text clip binding/rotation/stretch/blend, image clip binding/animation and
sound volume animation compile; broad clicked interaction remains unverified.

Follow clock correction: only subject track points map with real segment effects
onto joined played time. Source edit actions stay on footage clock.31native
assertions pass, including actual2x/reversed synthetic left/right subject crop
with centred white pixels at0.2s and1.6s. This checks timing, not person quality.

Binding coverage continuation:33native assertions now include real clip-bound
rotated/stretched/screen-blended caption pixels, animated bound picture moving
left to right, and placed-sound volume animation PCM timing. All these states
roundtrip. Fixed newly found picture binding/keys and sound keys envelope
persistence omission. Missing-clip pictures are excluded from request and paths
consistently, while retaining the saved layer for undo/recovery like captions.
Native chooser and broader mouse editing interaction remain unverified.

Video layer continuation: whole-output local video layers, source trim/loop,
shape/rounded corners/blur/outline/shadow exposed and persisted.34native checks
pass including actual timed blue frames from a trimmed second video, decorated
pixels and save/load. Inspected actual rendered frame and image-layer controls.
Video layer audio must be added independently as sound, explicitly disclosed in
UI; clip-bound video layers remain blocked. Chooser navigation still unverified.

Command studio continuation:35native checks include actual installed catalogue,
320x180 manual export and source/network/external-path/filter-script/numbered
output rejection. Local execution is argv-only, selected filters/options and
input aliases, one new media output. Other commands remain edit/lint only.
No network, shell or arbitrary file destinations. Generated subtitle/AI-mark
manual adapters remain explicitly unavailable; manual commands bypass edits.

Speed ramp continuation: per-node speed values and two original-curve presets
exposed; original SpeedCurve passed to clip, clock, export and project JSON.
36native assertions include actual ramp output duration matching predicted clock,
audio/video decode and roundtrip. Ramp controls visually inspected. Custom
node insertion/removal and curve drawing remain missing. Image native chooser
finally selected sticker.png with focused GTK location field and second Return;
status confirms image added, broader subsequent edit interactions still pending.

Studio bounded execution:37native assertions pass. Manual runs cap output to
30minutes and1GiB, preserving a shorter requested -t. A loop with veryslow
encoding was cancelled and left no target or temporary file. An earlier copy
loop completed the30minute cap before cancellation, so that was not counted as
a cancellation pass. Existing output prompts confirmation before studio run.
Ramp+fade+timed picture regression checks predicted duration and actual pixels.

Still drawing checkpoint 2026-11-23 00:41 Cairo:38 real native assertions pass,
including exported red line pixels, source-preserving eraser gap, alpha, shapes
and saved stroke roundtrip. Java2D adapter follows original StrokePainter path
smoothing, arrow and clear semantics. Drawing tab supports free/line/arrow/box/
oval, width/opacity/colour, eraser, undo/clear. Same renderer powers panel preview.
Actual mouse drag and edited-preview click produced the yellow diagonal in
/tmp/drawing-rendered-ui.png, inspected alongside the red line and erased gap.
Refresh drawing background explicitly after other edits; no live total-effects
preview claim. Masks, HDR and AI photo adapters remain. CI d1f7747 and8e342b5
are green. No current package rebuild since f617df8. Master unchanged.

Still subject cutout checkpoint 2026-11-23 00:59 Cairo: pinned U2-NetP mask
inference runs in the existing isolated worker, original upright source geometry.
Subject-cutout tab and photo button apply original SUBJECT key. PNG export
shows actual model alpha, mask PNG is embedded into project JSON and restored
into content-hashed durable mask storage. Reopening after original mask deletion
and exporting succeeds. Mask/source overwrite guards and decode geometry/size
checks added.38native checks still pass; isolated suite now also checks real
cutout inference, mixed alpha, export and portable roundtrip. Actual cutout and
render buttons clicked on synthetic test pattern; screenshot inspected. This
is not real-person edge-quality evidence. Attempts to download NASA portrait
from Commons returned403, no portrait claim. Subject cutout is segmentation,
not magic-eraser/inpainting, so no generated-inpainting disclosure is applied.
Full generated-content adapters still need original provenance gates.
Current package remains4dcfd2a (cutout changes newer); clean CI4dcfd2a green.
Package workers fresh at00:46:37 passed but deterministic3line log lacks header;
future logs include command/timestamp/source and package identity. No release.

Manual mask checkpoint 2026-11-23 01:03 Cairo: keep/remove brush uses the
original MaskEditing.paint on upright source pixels, each correction writes a
new mask so Undo retains previous states.39native checks pass, including actual
keep/remove alpha geometry, exported transparent stripe with kept centre,
embedded roundtrip and mask-overwrite rejection. Actual remove stroke dragged
on original-frame canvas, then rendered result inspected: transparent vertical
stripe visible at chosen position. Canvas intentionally stays the original
picture; final alpha requires Render, stated in panel. No final parity claim.
Studio additionally rejects a current mask as output target. Cutout CI8c1e461
still running at01:02:38, run45080710715. Last full package4dcfd2a remains stale.

Custom ramp checkpoint 2026-11-23 01:10 Cairo: chart draws original logarithmic
SpeedCurve. Tap adds up to12nodes, drag changes time/speed, ends stay fixed,
interior node can be removed. Add/drag actual mouse screenshot inspected,
selected56%,1.94x with4nodes.40native tests pass including4node ramp saved
roundtrip, actual duration and full A/V decode. Compile local incremental.

NEXT PRIORITY from review: EXIF orientation is a confirmed real gap. Pillow
created /tmp/ffmpeggui-models/orientation6.jpg (stored240x120, tag274=6), but
host FFmpeg emits240x120 instead of upright120x240. Need normalized ingress
consistent across preview/probe/export/cutout, preserve original source and
saved reload semantics. Do not claim real-photo geometry until fixed. Mask
cleanup also pending; retain Undo and saved embedded mask recovery. Packaged
U2-Net licence verified inside4dcJAR matches sources.json SHA c71d239d... .
Cutout9ed/8c CI were pending last checked. No final package since4dcfd2a.

EXIF checkpoint 2026-11-23 01:19 Cairo: bounded JPEG APP1/TIFF orientation
reader handles1..8including mirror cases. Scoped upright PNGs used consistently
in source preview, export primary/picture inputs, model extraction and studio.
Original source paths/data remain unchanged, saved reload re-normalizes.41native
checks pass including all8corner-pixel cases, upright dimensions, original SHA,
save/reload export and layer rendering. Isolated model suite additionally passes
orientation6cutout dimensions and embedded saved-mask export. It exposed JPEG
image2demuxer reporting40ms and wrongly being treated as video; still detection
now recognises still demuxers, and model EXIF roundtrip passes. Actual upright
120x240 GUI screenshot inspected. Source photo itself is synthetic; no real-face
quality claim. Native inputs above32MP reject before Java decode. JPEG only
EXIF parser; other formats' orientation support not established. Mask disk
retention cleanup remains next, preserving Undo and embedded saved projects.
Current package4dcstale; need clean/exact final package regression later.

Memory/cache checkpoint 2026-11-23 01:26 Cairo: JPEG upright adapter now reads
bounded source strips into one3byte/pixel upright raster, no second full source
raster. OOM is translated into ordinary visible I/O failure.24MP6000x4000 EXIF6
passed under256MB maxHeap with32MiB reserved headroom, expected4000x6000 and
blue corner, original hash unchanged,2.45s. New largeStillChecks CI task uses
same heap.42native checks and expanded isolated model/cancel/retry checks pass
after change.
Masks now in owned session cache: prune only outside current+50history states,
shutdown hook clears session, next launch removes dead-process session dirs.
Other live sessions and legacy flat mask files untouched. Saved projects embed
mask and restore independently; pruning/recovery native test passes. No generic
disk sweeper. Final exact package and Zorin target still owed. Large normalization
test is synthetic, not evidence for every colour profile or real-person quality.

Generated HDR checkpoint 2026-11-23 01:38 Cairo: original core GainMapBuilder,
GreyJpeg and UltraHdr assemble generated whole-picture gain map for JPG from
command builder's final/plain pair. Read bands, limit8MP output to bound core
working arrays. Original-camera maps, area masks, HDR hardware display remain
unverified/unported and are gated.43native checks pass including embedded map,
metadata, ordinary SDR decode, protected drawing gain lower than photo white,
saved ImageHdr roundtrip. SDR picture and grey map inspected. UI intensity and
limitations inspected. Map is deterministic image processing, not AI generation.
Package8b6stale relativeHDR checkpoint; no final delivery claim.8b6CIpending.

Photo canvas checkpoint 2026-11-23 01:45 Cairo: PictureCanvas controls, fill/
transparent, source layer X/Y/width/opacity/rotation/stack and automatic fit.
photoPlacement persisted in desktop envelope and passed to original builder.
Drawing size uses canvas geometry when active.44native checks include actual
320x180blue canvas, white rotated photo, transparent corner, placement+canvas
roundtrip. Actual render and scrolled controls inspected. Direct drag canvas
interaction and rich native layer selection still missing. HDR+canvas explicitly
rejected until its adapter exists. Latest full package8b6predatesHDR/canvas.

### 2026-11-23 01:50 EET: transition families
- Clip editor now exposes all original transition kinds that do not require separately ported companion assets, using the unchanged core graph/expressions. Punch/punch-back and technical-difficulties remain gated.
- Native check 45 actually renders seven representative families (flash, cube, RGB split, hearts, zoom blur, page turn and native wipe), decodes their midpoint, checks output time and project reload. Representative coverage is not all-kind visual proof. Solid-colour inputs cannot establish blur quality.
- Transition controls and seven-render midpoint montage inspected. No Zorin compatibility or final-package claim. Current local package predates this change.

### 2026-11-23 02:00 EET: subtitle fonts and styling
- Subtitle panel now selects validated single TTF/OTF fonts up to 16 MB, edits ARGB text/highlight/shadow, toggles drop shadow and one/two lines. User font paths persist, files are not embedded. Missing fonts fall back to packaged DejaVu Sans.
- Export copies only the selected font into its temporary font directory, not the user's full directory. Original FontFacts, SubtitleLayout and ASS writer are reused. Other built-in families remain unexposed.
- 46 native checks pass, including genuine custom-serif versus fallback-sans pixel differences, green burn-in, invalid-font rejection, save/reload and missing-file fallback. Colour editing was actually typed through UI and inspected, rendered serif/green/shadow output inspected. Font chooser UI itself is not yet a clicked proof.
- First same-family bold/regular difference assertion did not establish a difference because libass selected regular by style weight; replaced with distinct-family proof, not counted as a functional pass.

### 2026-11-23 02:10 EET: review follow-through
- Missing/unreadable custom subtitle fonts show a visible fallback warning on reopened projects. UI inspected. Desktop ASS-family boundary removes commas and control characters; core unchanged. Native assertion ensures a crafted family string cannot add an Events record.
- HDR enabled with non-JPG output is explicitly rejected with a useful message, and the image controls warn. No silent SDR export with the toggle enabled.
- 47 native checks pass. Dedicated 8MP HDR assembly succeeds at 256MB heap with32MiB reserve in4.73s (fixture preparation included); sampled peak RSS189864KiB,5.03s wall. This is adapter proof, not full editor export or HDR hardware appearance. CI adds largeHdrChecks.
- d9dc528 exact incremental package exists with816archived tracked-source files aligned; package checksum573aba34bf79810878105e5d33c2c21029ef6c809d8be038ddaf8408af491aab. Extracted package worker suite passes02:04:08–02:04:32. It predates this review follow-through.

### 2026-11-23 02:19 EET: external subtitle files
- SRT/ASS/SSA import bound to the selected source, original parser/retimer/style layout. Imported content embedded in project (2 MB cap), enable/remove, explicit priority over spoken burn-in. SRT can use subtitle style; ASS retains own style. Core removes embedded font/graphics sections. Spoken SRT/TXT export remains spoken words, not imported cues.
- 48 native checks pass after transcript-priority fix. Actual source trim2–4s at2x: cue2.5–3.5s becomes0.25–0.75s, rendered pixels inspected. Default SRT/ASS render, embedded-section stripping and saved recovery after original subtitle deletion pass. Reverse mapping exercised, detailed complex overlap/global speed coverage remains in original retimer tests, not all re-proved here.
- Actual file chooser imported external.ass, result UI inspected. Development process shutdown emitted detached-child cleanup notice; no model or export failure, not target-OS proof. Projects preserve content but not fonts.

### 2026-11-23 02:27 EET: stabilization and subtitle review
- Two-pass stabilization now has controls/persistence and uses unchanged buildStabilizationAnalysis, with installed-filter preflight and temporary transforms. Actual joined two-clip speed/reverse export and project reload pass. UI Render clicked, result inspected. This is pipeline proof, not handheld quality/crop certification. Auto zoom can crop edges, disclosed.
- Subtitle import already had metadata length preflight; actual read now bounded to2,000,001bytes to catch file growth. Strict UTF-8 or UTF-16 BOM detection, clear unsupported-encoding error. Tests cover UTF16LE, invalid encoding and overlimit. No-cues-left UI warning added. Combined two-clip fade+whole-video2x SRT render decodes caption pixels at expected time.
-49native checks pass after both additions. Local exact package remains d9dc528 stale.

### 2026-11-23 02:44 EET: real-media qualification
- Real natural speech has a reproduced model/runtime boundary-sensitivity limitation, shared with Android's model/chunk design.65s LibriSpeech gives139words ending57.5s; standalone audible tails sometimes0tokens while different boundaries recognize them. Native diagnostics and independent reviewer reproduced. No chunk/model tuning shipped to fit one sample. Long-speech accuracy remains open.
- User-visible permanent transcript caution and detected empty non-silent chunk time-range warnings added. The actual65s sample warns57880–65000ms. Partial omissions are not detected, non-speech may warn, no reliability claim. Fresh isolated worker suite passes. Empty recognition no longer states that the source has no speech.
- Opt-in DesktopRealMediaChecks accepts user-supplied footage/model and writes real follow/combined stabilized outputs for inspection. First5s TensorFlow pose-squats scaled+gray-padded to landscape:10points,360x640follow, combined stabilization and saved project pass. Four sampled frames inspected, person retained. One mostly horizontally stationary subject, not lateral/occlusion/camera-motion certification. Fixture not included in repository or package.
- Fixture sources: https://github.com/tensorflow/tfjs-models/blob/master/pose-detection/test_data/pose_squats.mp4 ; https://github.com/tensorflow/tfjs-models/blob/master/LICENSE ; https://huggingface.co/datasets/podscripter-project/test-fixtures (LibriSpeech CC-BY4.0).

### 2026-11-23 02:50 EET: lyrics timing
- Original LyricsText/LrcLines power plain/synced generation from reviewed edited transcript, manual output-clock stamp/clear, active line, remove timing and metadata export. First250lines get timing controls, all content retained. UI explicitly warns subsequent timeline edits do not retime metadata, regenerate after edits. No burned-lyrics claim.
-50native checks pass: transcript at whole2x starts LRC0.25s, manual1.23s stamp, actual MP3 lyric metadata, saved lyrics recovery. Actual Stamp clicked, updated text/active line inspected. Player-specific display support not verified.

### 2026-11-23 03:26 EET: scene stems and cover art
- Original Bandit int8 shared separator in isolated workers, three durable WAVs
  from selected original source. Not timeline/instrument separation. Parent
  copies only known validated outputs before worker cleanup, new directory
  only, existing rejected and partial cancelled outputs removed. Original model
  licence/notice/provenance bundled and copied with exports; weights separate.
- Short speech fixture parent-export checks pass; actual mouse button/save
  correctly rejects low memory in2GiB GUI host. Concise resource error inspected.
- Ten-minute synthetic tone/pink-noise real inference in CI333seconds, peak
  workerRSS1,453,416KiB, each WAV600s/stereo48k.2GiB freeRAM gate now required,
  conservative headroom, not all-machine memory or real-scene quality proof.
  Source https://github.com/whitegeraph-hue/FFmpegGUI/actions/runs/45093189855 .
-600px square album art from local still picture is embedded in project;
  original source unchanged, original CoverTags/coverWriting used for MP3,
  M4A,FLAC,OGG,OPUS. Other formats reject rather than silently omit cover.
-51native checks pass: actual five-format art export/decode and centre pixels,
  saved recovery after picture deletion, malformed payload/unsupported output
  rejection. Defaults at16kHz mono192kbps fail Vorbis in this FFmpeg; cover
  proof explicitly uses48kHz96kbps. This encoding combination issue remains.
  Embedded source art automatic import and final package/Zorin checks remain.

### 2026-11-23 03:33 EET: remaining mastering parameters
- Modulation CHORUS/FLANGER/PHASER/TREMOLO/VIBRATO with rate/depth, lo-fi
  bit depth, gate reduction, compressor attack/release/makeup and limiter
  ceiling exposed using original AudioEffects and filter chain.
-52native checks pass including actual five-family modulated PCM difference,
  duration and full saved AudioEffects recovery. UI scrolled/inspected; no
  physical-listening or live DSP claim. Vorbis encoder setup rejection now
  gives specific sample-rate/bitrate guidance, never silently changes settings.

### 2026-11-23 03:44 EET: compression
- Original compression model and x265 two-pass builder exposed, distinct pass
  status, bounded x265 native pools. JPG six-attempt quality fitting follows
  original binary search; PNG palette/strong compression and lossy-audio
  bitrate presets exposed. Settings persist in Linux envelope.
- A target is enforced before output move. Impossible/oversize results fail,
  preserving an existing destination. Unsupported formats, HDR and PNG target
  fitting explicitly reject. No automatic dimension changes.
-53native checks pass including actual HEVC decode/target/phase order/project,
  JPG fit on seeded noise, PNG palette, MP3 preset, unsupported SVG and tiny
  target preserving output/temporary cleanup. Not an all-input size guarantee.

### 2026-11-23 03:54 EET: direct subtitle translation
- Original Marian/SentencePiece/greedy decoder and SubtitleTranslation reused
  in disposable workers. Five-file atomic-new model import, bounded copies,
  manifest hashes checked on use, no publisher pin claim, no auto downloads.
  Direct pairs only,500short cues,256token input bound,30minute worker limit.
  Length/repetition-limit outputs reject instead of known partial replacement.
- Actual Xenova en-fr revision28726206f80896b90035bd99cccd5cc1e151f916
  returns sensible French for two cues. Time preservation, model corruption
  rejection, cancellation/retry and oversized cue tests pass. Actual Translate
  button clicked and2cue completion inspected, not a broad quality claim.
-54native checks pass including ASS timing/leading-tag preservation and saved
  subtitle recovery. Cue source binding/Undo retained. Original spoken words
  untouched. Import five-file chooser not yet clicked end-to-end.
- Model licence must be checked per model. Live tested en-fr base README
  lists Apache2.0, unlike blanketCC-BY4.0 in original Android OpusMt registry.
  No translation weights bundled; model-specific sources in desktop README.

### 2026-11-23 04:00 EET: translation import hardening
- Streaming bounded protobuf preflight refuses all tensor external_data and
  data_location fields, including nested graph attributes. Also refuses
  sparse tensors, training graphs, local functions and malformed wire data.
  Runs before native ONNX session creation both during import and use.
- Config language pair must match the repository pair; cue failures include
  their one-based index. Local hashes are integrity checks, not publisher trust.
  Disposable workers have ordinary account file access, not OS sandboxing.
-55 native checks pass; synthetic external-data/nested/malformed forms reject.
  Real single-file en-fr import/inference/cancel/retry regression passes again.

### 2026-11-23 04:06 EET: existing picture disclosure preservation
- Original AiMarksReader/XMP and AiSeal used for marked visual inputs/layers.
  PNG/JPEG XMP parsed; other formats use bounded2MB head/tail scanning.
  This is not detection of unmarked AI or invisible-watermark preservation.
- Output seal after rendering, including manual studio, cannot be removed by
  metadata strip. Container-only tags get an explicit remux and verified scan.
  Formats without a supported disclosure location fail before destination move.
  Original byte-array picture writers are limited to32MB files; large visual
  outputs fail with guidance rather than exhausting the Java heap.
-56native checks pass: PNG/JPG/WebP pixels/decode and disclosure, MP4/MKV with
  marked picture layer despite stripAll, hidden layer excluded, recovery,
  manual studio, unsupported BMP preserving existing file and generated HDR
  gain map still readable after disclosure seal. Pixel montage inspected.
- ONNX attribute tensors5/list10/graphs11 and sparse15/22/23 now explicitly
  exercised in addition to initializer/nested g6 cases. No native-sandbox claim.

### 2026-11-23 04:22 EET: MI-GAN new-PNG workflow and TrustMark
- Original pinned MI-GAN, original crop/grow/blend and PictureMarking/Marks
  constants. Plain CPU original pinned TrustMark encoder/decoder, strength
  bounds and invisible readback both in-memory and after saved PNG reload.
  Original source unchanged, output must be a new PNG, no automatic project
  replacement. Original still only,2MP cap,1.2GiB available-memory preflight.
- Separate model imports, no weights bundled. Generative import requires
  marking pins first, operation checks again. Not merely XMP as substitute
  for invisible mark. Disposable worker/cancel/size bounded transfer/new-only
  destination checks pass. Original notices/licences included as resources.
- Actual MI-GAN synthetic black rectangle removal and narrow photo patch
  inference inspected.56native checks pass separately. Actual Choose alpha
  mask, Erase to new PNG, save chooser and completion clicked and inspected.
  Real-photo inference is not broad quality validation. Models' chooser UI
  imports not yet clicked. External upright alpha mask only; direct painting,
  LaMa, clean-twin repeat editing and edit-pipeline mark resealing still open.
- Direct model test parent peak540MB at640x360,not a2MP resource benchmark.
  Output PNG holds mark at save; later transforms/recompression unguaranteed.

### 2026-11-23 04:27 EET: direct erase mask painting
- Scoped Swing source-pixel mask canvas with width/unselect/5stroke Undo/Clear,
  separate from project drawing. Direct actual drag, Use mask, Erase/save and
  completion inspected. Rotated JPEG requires upright copy or external mask,
  rather than placing a raw-coordinate mask over a normalized source.
- Near2MP1920x1040 direct+worker real inference completes10.5s, sampled direct
  parent peak540764KiB. This is one synthetic resource check, not all inputs or
  combined parent/worker peak. Conservative1.2GiB preflight retained.
- Decode converts indexed/RGB to mutable ARGB so generated colours are not
  quantized back to a source palette. Original alpha remains supported.

### 2026-11-23 04:42 EET: audio marking foundation, not exposed dub
- Original pinned AudioSeal generator/detector, original5s windows/resampler/
  joins and constant all-zero payload. No dub/TTS control exposed yet.
- Actual17word natural test WAV: marked speech share1.0 with correct message,
  saved PCM WAV reload1.0, unmarked source share0.01408. This is one fixture,
  not broad robustness or post-compression certification.60second mono bound.
- Current upstream Kokoro archive does not match original Android archive pin:
  downloaded349886476bytes SHA60703707bc05ccd48d60e1eb723f38b07d6e474795a0b6a588d7220400e0f7a4,
  expected349906910bytes SHAc5f7e2d2caf082bc1d20fb70334a61d99d20b484500aad32e7cf84c128ea3298.
  Model/voices/tokens/USlexicon/phontab inside match their original pins, but
  that is not whole-archive identity. Original pin not weakened, no Kokoro
  import or synthesis exposed. Original archive recovery remains open.

### 2026-11-23 04:52 EET: pinned Kokoro foundation, not a user-facing dub tool
- Recovered original349906910byte archive from an independent mirror and verified
  original full SHA256c5f7e2d2caf082bc1d20fb70334a61d99d20b484500aad32e7cf84c128ea3298.
  https://github.com/modelship-ai/model-bundles/releases/tag/tts/kokoro
  Original pin unchanged. Hash-verified private copy, bounded original TAR/BZip2
  import, original primary pins plus per-file hashes rechecked before inference.
- Actual CPU two-cue24kHz speech, original timing/pace/trim/fit/fade, sequential
  model release then required AudioSeal and XMP. Saved WAV reload share0.99908
  in one test; no broad listening/quality or compression robustness claim.
- Compatibility finding: sherpa2 removed built-in eSpeak. Its lexicon skips
  unlisted words, including ordinary inflections. Fail-closed ASCII vocabulary
  and symbol/number checks now precede synthesis; OOV/native unknown-phoneme
  diagnostics refuse worker output. External phonemization/multilingual/Piper
  voice support remain open. No dub UI exposed until the frontend is usable.
  https://k2-fsa.github.io/sherpa/onnx/faqs/update-v1.x-to-v2.x-espeak-ng.html
- Disposable child tests cover synthesis, unchanged existing destination,
  unknown-word failure without output, cancellation, retry and changed model
  refusal. One-minute/30cue/US-English bounds and1.2GiB free memory preflight.
  Direct+child back-to-back in the same parent hit the gate because native
  memory was retained there; fresh worker-only checks pass. No lowered gate.
- Archive contains eSpeak NG GPL data; weights remain unbundled. AudioSeal MIT
  licence is included; current licence audit is scoped, not legal certification.

### 2026-11-23 05:06 EET: eSpeak frontend and verified standalone dubbing
- Distro eSpeak NG CLI now produces IPA for sherpa GenerationConfig, so missing
  lexicon words/numbers are no longer silently dropped. Direct ProcessBuilder,
  private UTF-8 input file,20second timeout,16KB stdout/4KB error limits while
  running, error/unknown-model-token refusal,380phoneme limit. No shell/Python.
  Apostrophes/hyphens pass phonemizer; unsupported symbols fail explicitly.
- AudioSeal null-share output is now rejected, even for short words: no verified
  mark, no delivered output. Model detector stays alive through saved16bit WAV
  reload and verifies fresh-share threshold and constant message after quantizing.
  Actual null/silent test rejected; natural source mark/reload remains1.0.
- Actual two-cue arbitrary-word/numeric synthesis (including generated/carries/
  1969),12second saved WAV reloaded0.996875marked share. Audio transcript contains
  both formerly omitted words and the number. This is narrow intelligibility,
  not a listening panel or real-world robustness guarantee.
- New Dubbing panel exports standalone new WAV from text or independent SRT
  times. Actual Generate/Save/completion clicked and screenshot inspected; audio
  transcription confirms default sentence. Project media/mix remain unchanged.
  US-English only,30cues/one-minute/300characters each,1.2GiB freeRAM. Native
  disposable worker cancel/retry/unsupported-input/existing-output tests pass.
  Import model UI pickers not clicked.20voices not all listening-tested.
- espeak-ng added as ordinary apt dependency; its GPL implementation/data are
  supplied by package manager, not embedded here. No dependency change to the
  already delivered17c binary. Full package refresh/lifecycle and broad licence
  audit still open for this checkpoint. No multilingual/Piper/integrated dub
  or export-pipeline invisible re-seal claim.

### 2026-11-23 05:18 EET: imported AI voice disclosure propagation
- Existing explicit AI-generated voice comment/XMP/tag detected by bounded2MB
  head/tail scan on main audio/video sources and sound layers. This does not
  detect unmarked synthetic audio. Saved projects re-probe the source marks.
- Normal export and manual studio reapply speech disclosure after render/strip,
  read it back before move; container-tag formats get explicit verified remux.
  Sound layers muted or volume0do not add speech disclosure. Source-audio
  disclosure remains conservative even if a manual command removes that audio.
-57native tests pass including actual tagged tone fixture through MP4/MKV/MP3/
  FLAC/WAV, metadata strip, manual studio, source-audio export, project reload,
  muted/zero-volume exclusion and image-only exclusion. This verifies metadata,
  not an invisible AudioSeal mark on the fixture or resealing transformed audio.
- Dubbing UI notes importing standalone output via Sound tracks and preservation
  limits. Fresh generated WAV still has production saved-sample readback; later
  edits/recompression remain unguaranteed. Invisible pipeline reseal remains open.

### 2026-11-23 05:25 EET: edited cue snapshot and optional generated track
- Dubbing can snapshot enabled project subtitles or transcript onto final output
  clock using original retimer/layout plus global speed.58native checks include
  source trim+clip2x+global2x and transcript global speed. Later timeline edits do
  not retime that snapshot or a saved dub, explicitly disclosed.
- Optional add saved WAV as separate Sound track uses source probe, unchanged
  original audio, undoable project mutation; Undo does not delete saved file.
  No automatic duck/mute, alignment inference or invisible mix reseal claimed.
- Voice-only MP4/MKV reimport is not marked as generated picture; combined and
  generic external picture type remain recognized. Metadata tests are not a
  guarantee about invisible marks after editing.
- Actual Add saved WAV checkbox, Generate/Save, completion and Undo clicked;
  completion shows added marked Sound track, Sound tracks controls appear,
  Undo removes track leaving generated WAV on disk. Pixels inspected. Project
  cue snapshot mapping is native-test covered; its UI button not yet clicked.


### 2026-11-23 05:50 EET: generated HDR area masks
- Original HdrAreas alignment/share and GainMapBuilder now take an upright
  source alpha mask for subject/background/brush areas. Crop, rotation,
  perspective and border use shared geometry. Canvas and original-camera HDR
  remain explicitly blocked. No HDR hardware preview claim.
- Matching external masks are validated, copied into owned mask storage,
  embedded in projects, restored with bounded geometry checks and retained
  across Undo. Current subject mask can be reused. Direct HDR painting remains
  unported; BRUSH accepts a separately painted alpha mask.
-59 native tests pass, including actual rotated half-mask gain-map JPGs,
  inverse background, exported reread, save/delete-mask/load/reexport, missing
  mask rejection and output/project overwrite protection. Actual gain-map
  pixels inspected. Controls displayed and inspected; chooser not yet clicked.
- LaMa adapter checkpoint9ed9dbd remains non-UI pending clean packaged actual
  inference and visual inspection. Local2GiB host correctly refuses its2,000MiB
  available-memory guard; no output claim. Desktop conversion pin does not
  weaken or replace the original Android unpinned LaMa placeholder.


### 2026-11-23 06:00 EET: original HDR and exact packaged LaMa
- Original embedded Ultra HDR gain maps now decode with bounded primary/source
  size, are brought through shared geometry, converted from map fractions to
  original stops, then use GainMapBuilder.original with original strength.
  EXIF gain-map orientation is normalized; camera/vendor format breadth and
  physical HDR hardware remain unverified. Canvas still blocked.
- Visual inspection caught distro FFmpeg/ffprobe selecting the appended map
  rather than primary picture. Supported Ultra HDR now probes primary ImageIO
  dimensions and normalizes primary pixels to a private PNG for media inputs.
 60native checks pass, including actual white primary preview/output pixels,
  dimensions, rotated original-map geometry/max stops and plain-input refusal.
- Clean Ubuntu9ed9dbd exact packaged launcher LaMa inference passes, including
  saved TrustMark decode, metadata, source dimensions, selected pixels changed,
  existing destination protection and cancellation. CI output pixels inspected:
  the synthetic black box is filled, with some banding; not broad photo quality.
  https://github.com/whitegeraph-hue/FFmpegGUI/actions/runs/45107591030
- LaMa import/selector now exposed behind original TrustMark gate and exact
  desktop conversion pin, no auto-download;2,000MiB freeRAM requirement retained.
  Local host cannot run it, correctly refuses. Selector/limits displayed and
  visually inspected. Import picker not clicked; inference tested in CI.


### 2026-11-23 06:22 EET: selected existing clip smart-tool correction
- User clarified existing split clip, not arbitrary range. No new arbitrary
  range UI shipped. Android smart actions now follow selectedClipId and analyze
  its source; pause/scene/beat detections map through ClipToolScope onto only
  that occurrence on unsped footage clock. No duplicate-source collateral cut.
- Selection change cancels/clears results. Android timeline snapshot guards and
  mutation invalidation refuse stale apply. Linux explicit Clip N smart scope,
  source selection reset and beat/follow mutation invalidation added.
- Shared pause removal handles edge cuts below the100ms split threshold,
  windows speed curves, retains absolute source-clock keyframes and preserves
  neighbour clip objects unchanged. One project/timeline mutation, hence one Undo.
-61 native checks pass including reversed2x duplicate occurrence,50ms edge,
  expected played duration and actual stable-colour outside pixels at shifted
  times. Core JUnit tests added; local test dependency not cached, CI needed.
- Android source compile/UI/device proof remains pending. Follow crop, speech,
  stems and other tool-specific selected-clip adapters remain open. This is not
  fulfillment of all-tools scope. Analysis can still read whole source for
  context; only selected clip results mutate the edit.
