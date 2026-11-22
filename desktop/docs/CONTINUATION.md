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
