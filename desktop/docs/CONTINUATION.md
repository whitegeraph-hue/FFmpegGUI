# Development checkpoint, 2026-11-22

Current native port is an incomplete milestone. Do not present it as finished.
Original scope and full parity inventory remain active in PARITY.md.

## Proven

- Unchanged full Kotlin core compiles on Desktop. Clean CI 45063765980 and
  45064895818 passed core tests, native export, package generation. Second run
  also passed 12 real native integration assertions and produced GUI pixels.
- Local Java17 application compile includes original-pin model adapters,
  unchanged SpeechModel/CutoutModel definitions and separate intact official
  sherpa JVM/native JARs. Hash checks for native JARs are part of build.
- Integrated runtime classpath real model checks passed with 128MB Java heap:
  pinned Parakeet transcribes official English WAV to 17 timed words; U2-NetP
  infers three centroids; both runtime load orders pass, atomic import rejects
  corruption/preserves existing install; cancellation/temp cleanup pass.
- Native model UI renders readably, tool tabs now wrap. Model import callbacks,
  listen/transcript and follow track callbacks compile but actual clicked UI
  workflows remain unverified. Source frame is explicitly not effect preview.

## Next work, in order

1. Check model CI run 45065659960, head 1ea6da8, and subsequent commits. Inspect
   actual .deb for intact native JARs, explicit ffmpeg/fonts dependencies, Java
   runtime and licences. Extract/install supported and run inference through
   packaged classpath/launcher. No installed-app native proof yet.
2. Verify actual clicked model import/listen/word seek/line cut and keep,
   pause apply, scene apply, beat apply/snapping, follow crop and preview/export.
   Coordinate-based earlier click attempt did not select a tab, not a pass.
   X11 search matched a 1x1 helper window; choose real visible window by geometry.
3. Real-person tracking quality, rotated media, >30-second chunked speech,
   voice-stem path, no-audio failures. Keep original pins. Mutable HF Parakeet
   weights do not match. Original parakeet-onnx-1 release does.
4. Continuous integrated playback/audio and scrubbing remain owed, not replaced
   by still extraction plus external ffplay. Implement a suitable desktop player.
5. Continue original-editor migration per PARITY.md. Only three production UI
   files are shared, most of the Android app UI is unported. No user-approved
   scope reduction. Photos/SVG/ASCII/GIF/audio mastering/overlays/keyframes/
   transitions/dub/stems/translation/marking gates/command studio remain.
6. Target Zorin OS Intel, exact release unknown. Do not infer release or claim
   user-machine compatibility from Ubuntu CI. Need final install/launch tests.

## Local engineering notes

Source at /tmp/ffmpeggui-linux, branch linux-desktop. Java17 and Gradle8.14.3
at /tmp/ffmpeg-build-tools. Shared core first compile exceeds short execution
window; emitted classes complete and CI clean build passed. Incremental UI
builds can use -x :shared-core:compileKotlin locally only. Never describe that
as a clean build. Model checks plus a resident Gradle daemon can exceed this
small host's RAM; stop daemon then run runtime-classpath directly with -Xmx128m.
writeRuntimeClasspath task writes desktop/build/runtime-classpath.txt.

Reproducible native fixtures in desktop/scripts. /tmp/ffmpeggui-models has exact
pins and test WAV. /tmp/ffmpeggui-fixtures has generated media. Never commit
model weights. Native JARs must stay intact, no flattening shared ORT .so names.
Use same application model gate before adding concurrent engines. Imports are
blocked by UI while a job is active. Native cancellation waits for current call.

Master remains 5744861a1c934c4e1a1999b251f33d152fa850ac. No release/tag/PR merge.

## Checkpoint 2026-11-22 22:52 Cairo

Branch tip 0796ff9. All model-boundary/playback source pushed. Master untouched.
The original in-process GUI Listen click crashed sherpa token parsing despite
identical headless inputs passing. Root cause unknown. Both model engines now
run headless isolated workers with pins revalidated, cancellation/descendant
cleanup, shutdown hook, parent watchdog, JSON IPC and packaged launcher reuse.
The inherited _JPACKAGE_LAUNCHER marker must be removed in worker environment.

Fresh full package at desktop/build/compose/binaries/main/deb/ was rebuilt then
postprocessed/extracted to `/tmp/final package spaces`. SHA256
c0517ca6ca66bb22c5fba3dbdc27fcdcf9486bf0b900e7262783d73c22e693de.
It includes latest watchdog/test source except subsequent small path handling,
error-display and atomic-postprocessor edits in 0796ff9. Do not present that
package hash as a clean 0796ff9 build. Fresh clean CI must replace it.

Passed actual mouse clicks from full extracted package: Listen gives17 words,
Follow sets crop. Imports were previously tested through native file pickers.
Source/patched package earlier screenshots do not substitute fresh package
screenshots `/tmp/final-deb-click-listen.png` and `...follow.png`. Both sent.
Worker suite passed via actual package launcher in path with spaces and symlink:
speech+track, cancel, forced crash as failure, retry. GUI double-click only one
worker; killed worker yields error and editor survives. Parent SIGKILL while
real FFmpeg grandchild was briefly suspended: worker+FFmpeg gone after1s.
Evidence `/tmp/pkg-subtree-kill.log`, sent.35s speech:155words,last34879ms.
Speech is disk-backed30s chunks, not full audio heap buffering. Beat is currently
full sample array, ten-minute cap disclosed. ORT GLIBC floor2.27 declared.

Rendered preview plays inside editor. Actual clicked playback using timed
PulseAudio virtual sink advanced burn-in0.48->2.64s across roughly2s, pause
observed. This is not physical speakers or full live-effects preview parity.
Player seek/rapid stop/close cleanup, large-frame/long-timeline stress still
need more coverage. Playback implementation is new and requires review.

Next real work, not more duplicate model checks:
1. Check new0796ff9 CI and clean artifact. Previous3ca2768 CI45068074048 green.
2. Complete licenses: include all actually shipped JVM/native notices and
U2-NetP attribution. Current bundle is not yet license-complete for delivery.
3. Actual system install/target Zorin release+graphics/audio validation remain.
4. Broader original UI migration: subtitles/style, encoding controls, command
studio, photo/audio editors, multi-source timeline/layers/effects. PARITY.md is
still full outstanding scope, no user scope reduction approved.
5. Do not call this complete, tag, release or merge master. A usable development
milestone can be delivered only with honest gaps and after delivery checks.

Build Java17 /tmp/ffmpeg-build-tools/jdk-17.0.21.1+2, Gradle8.14.3.
Stop Gradle before native inference on this2GB host. Model fixtures in
/tmp/ffmpeggui-models, media fixtures /tmp/ffmpeggui-fixtures. Local incremental
builds skipped already-compiled shared-core, clean CI compiles it.
UI click scripts /tmp/final-deb-click-ui.py, /tmp/pkg-subtree-kill.py,
/tmp/timed-play-ui.py use actual xdotool window1280px (avoid1px helper).
Pulse virtual sink configured /tmp/ffgui-pulse.conf, set ALSA_CONFIG_PATH only
for tests. Do not ship test null-sink configuration.

## Checkpoint 2026-11-22 23:16 Cairo

Recent branches:487807d audio/GIF, a08f735 photo/tone and packaging menu fix,
4a3da6b SVG/text-art and dependency/remove tests, ac2e0fd timed text layers.
20native assertions now pass locally, with actual audio bitdepth/rate, GIF,
PNG geometry/tone/corners, SVG/ASCII/ANSI files and timed text pixels.
PARITY.md describes remaining full-app scope; not ready for final delivery.
Actual clicked photo render and add-text UI inspected. Rendered preview now
uses its frame (previous code made but never displayed previewFrame), clears
on project mutation, hides playback for stills. Live art preview explicitly
blocked; export works. WEBP unported.

92683 CI failed postinst xdg-desktop-menu on headless Ubuntu. a08f735 removes
those session-menu calls, owns usr/share/applications desktop entry instead.
All four maintainer scripts locally pass sh -n, desktop-file-validate passes.
4a3da6b CI additionally removes ffmpeg/fonts before apt install, validates
actual installed entry, then remove/purge. Check exact clean runs before any
install claim:45070406929 (a08),45070644779 (4a),45070961352 (ac2).

Skiko0.9.4.2 gradle.properties pins Skia m132-a00c390e98-1, exact commit
 a00c390e98b1a8741f11774c742ff60d767365ac. Added notices from official release
archive plus pinned DEPS libraries including ICU/FreeType/HarfBuzz/JPEG/WebP/
Expat/Brotli/Wuffs; sources.json hashes verified. Added per-package source
archive via source-bundle.py and processResources. Bundle build locally passed,
but fresh clean package inspection remains needed. Notice collection is not
legal certification. Weights are not in source. Native jars excluded with
verified download instructions and hashes instead.

## Checkpoint 2026-11-22 23:41 Cairo

Latest migration adds multi-video timeline append and per-source transcript,
pause/scene/beat mapping. 26 actual native integration assertions pass.
Multi-source subject-follow is blocked explicitly, not silently misapplied.
Still/audio-only timeline append remains absent. Source-selection/transcript
UI inspected for both sources. Subtitle count corrected for per-source words.
735 CI passed45072389399; fd CI45072878124 running at23:40.
Current package still predates recent source. No delivery or completion.

## Checkpoint 2026-11-22 23:54 Cairo

Current work fixes output vs footage clocks after reviewer found real defect.
30native checks pass combined speed/reverse/transitions with timed caption,
picture, PCM, SRT and reverse-safe cuts. Last fade/transition playback-order
adapter correction needs compile+suite rerun. Current build source archive still
from1f36033 until commit. Existing74package stale, must rebuild exact committed
revision before eventual delivery. No full parity or target-machine claim.
New layer binding/keys compile but interaction/render coverage still owed.
Next: latest CI, regression suite, pushed exact package, then remaining original
editor work per PARITY. Image native chooser still unverified. No release/tag.

## Checkpoint 2026-11-22 23:59 Cairo
Follow points corrected separately to real-effects joined clock.31native checks
pass actual2x/reversed synthetic subject centre at0.2/1.6s. Final adapter changes
in23d passed30checks before that. Build a current committed package next.

## Checkpoint 2026-11-23 00:24 Cairo

Latest migrations c7b1ef2 binding persistence+33tests, a41a36c video layers+34,
6ec6b26 guarded command studio+35. Now speed ramp controls+36tests pass locally.
All core unchanged. Image native chooser successfully added sticker.png after
focusing actual GTK dialog before ctrl+l, then two Returns. Earlier misses
were script focus, not app rejection. Ramp controls visually inspected.
Current exact local packagef617 is now stale. It had matching806file source
archive and actual launcher worker tests, hash f1eacd9fe0d76739368f1d7663eb712505c83f34fcdd4a88a080103c9c5f44bf.
No user delivery. Remaining broad parity still active, including AI adapters,
photo drawing/masks/HDR, richer timeline, full live preview and actual target.

## Checkpoint 2026-11-23 00:31 Cairo

Tip next commit bounds manual command execution.37native checks pass, including
loop cancellation/temp cleanup, ramp+transition+output-time image pixels.
Manual output cap30min/1GiB keeps shorter -t. Existing-target studio save asks
confirmation in Main.kt. Both filter whitelist and deny check retained.
8e342b5 ramp and6ec6b26 studio pushed. Final exact package stillf617, stale.
Chooser proof actual second sticker.png entry now inspected, sent to parent.
Next: check settled clean CI and current package; broader original tools still
not done. No delivery, tag/release or master changes. Token deleted after push.

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
