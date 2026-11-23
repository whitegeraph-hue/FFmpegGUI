# Linux native smart-model adapters

Integration artifact for FFmpegGUI. No Android source changes and no model weights included.

## Files

- `DesktopSmartModels.kt`: native CPU speech transcription and mask-centroid subject tracking.
- `DesktopSmartModelsChecks.kt`: standalone checks including real native inference.
- `native-checks.log`: observed check output from this Linux host.

## Integration

Add the adapter under the desktop package. Include these existing original files in desktop compilation, without edits:

- `app/src/main/java/com/cuscus/ffmpeggui/speech/SpeechModel.kt`
- `app/src/main/java/com/cuscus/ffmpeggui/cutout/CutoutModel.kt`

They have no Android dependencies and keep model names, sizes, SHA-256 pins, provenance and download URLs in one place. Use build-time generated shared sources or source-set includes rather than independent copies. The adapter compiles against the unchanged complete core, including `SubjectModelIo`, `RecognizedWords` and `ListenedSound.command`. The last command preserves first-audio-track selection, leading silence from delayed audio timestamps, and mixing voice stems at original levels.

Instantiate `DesktopSmartModels(ffmpeg = absoluteBundledFfmpegPath, cacheDirectory = privateCacheDirectory)`.

Requested APIs:

```kotlin
suspend fun transcribe(path: String, modelDirectory: File): List<SpokenWord>
suspend fun trackSubject(path: String, durationMs: Long, modelFile: File, progress: (Float) -> Unit): List<SubjectPoint>
```

The richer speech overload supports voice stems and per-chunk words/progress. The richer `track` method returns display-frame width and height as well as points, because core crop construction needs dimensions after rotation. Standard FFmpeg autorotation is retained. Tracking samples every 500ms. It uses the original normalization, first ONNX output, and original centroid/confidence definition. Speech preserves 30-second chunks cut at quiet spots within the last two seconds and converts token timings through core `RecognizedWords`.

All inference and file hashing run off the UI dispatcher. These adapters share a process-wide mutex, release native sessions/streams in finally blocks, poll for cancellation during FFmpeg subprocess work and terminate the subprocess on cancellation. Cancellation during a native decode/session run takes effect after that call returns, as in the Android implementation. If other desktop models are added, put them behind the same application-wide model gate or pass a shared gate instead, so memory-heavy engines do not run together.

The app must show actual inference failures to the user, not treat missing models as successful empty results. The adapter throws on missing frames; it does not silently produce a successful partial track. The model import UI and model selection UI still need integration.

## Verified import

`DesktopModelFiles.importFile` copies into a temporary sibling, checks original SHA-256 and size, then installs by an atomic move. It preserves an existing installed file if the new import is corrupt. Install to a private app directory, never overwrite a model while inference is active, and only mark the speech set ready after all four files match. `DesktopModelFiles.speech` rechecks all four before native loading. Cutout infers the original model contract from the supported filename and checks its hash and size before loading. Do not rename imported cutout model files.

Use the original bundled asset for `tokens.txt`, verifying its original hash. Do not obtain new mutable model files and update pins to match them. During this work, the current Hugging Face encoder/decoder/joiner did NOT match the app's existing pins. The existing app's GitHub release did match all three and was used for the passing inference check:

https://github.com/marcomorosi06/FFmpegGUI/releases/tag/parakeet-onnx-1

The app remains offline. The adapter does not download models, accept arbitrary replacement models, or change the app's import policy.

## Runtime dependencies and packaging

Use these published artifacts, inspected and used together in the passing process:

- https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-jvm-2.0.3.jar
  SHA-256 `ad69b6a54462ba3671782617b11d35975c8d6b7c84ae11765e3a06dd0c56c0e5`
- https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-native-lib-linux-x64-2.0.3.jar
  SHA-256 `42330fbcfe4f771386545397ebf8a8dd2176ff913a254331226c01ad87982e17`
- Maven `com.microsoft.onnxruntime:onnxruntime:1.28.0`
  https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.28.0/onnxruntime-1.28.0.jar
  SHA-256 `3a9dc00bed7ee467ded8a257f99fc216570d385f8b06053570d33e9b5d7c7a37`
- Existing `kotlinx-coroutines-core-jvm:1.10.2` and Kotlin standard library.

The sherpa artifact exposes the Java builder API, unlike Android's Kotlin constructor API. Do not add the Android AAR/Kotlin wrapper beside this JVM JAR. Keep the native JAR intact on the runtime classpath. Its loader extracts its own `libsherpa-onnx-jni.so` and `libonnxruntime.so`. ORT Java extracts its own separate native files. Do NOT flatten both JARs' native libraries into one directory: both carry a file named `libonnxruntime.so`, with different versions, and overwriting one breaks symbol version compatibility. Both intact native JARs successfully ran in the same JVM, sequentially.

Include the source and model licenses/notices already maintained by the project. No weights belong in git. FFmpeg must be supplied by the app's packaging decision; the test used host FFmpeg explicitly, so these checks do not establish bundled-FFmpeg readiness.

ELF version inspection of these exact artifacts found maximum required GLIBC versions of 2.16 for sherpa JNI/native ORT and 2.27 for the ORT Java runtime library. This is not a target-Zorin compatibility guarantee; the Zorin release is unknown, and the final package must still be tested for native loading, display integration, and install dependencies on supported target releases.

## Checks actually run

Compiled the unchanged complete core plus the two original model-definition files and these two adapter/check files using Kotlin compiler 2.1.20. JVM target 11 was chosen only for this host's installed OpenJDK 11; the application can keep its Java 17 target. Native checks ran headless on Linux x86_64, including both native runtimes in one JVM.

Passed:

- Zero-mask and weighted-mask centroid parity.
- Actual pinned U2-NetP verified copy/import.
- Corrupt import rejection, previous install preservation, temporary-stage cleanup.
- Actual U2-NetP inference on three frames of a real generated H.264 video, with normalized point bounds, 320x240 display dimensions, timestamp and final-progress assertions.
- Cancellation and temporary frame/process-log cleanup.
- Actual pinned Parakeet native inference on the sherpa project's English test WAV, producing 17 timed words.
- Temporary audio cleanup.

Test WAV source:
https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/test_wavs/en.wav

For a desktop Gradle test harness, place the checks in a separate source set or add a JavaExec task with `mainClass = "com.cuscus.ffmpeggui.desktop.DesktopSmartModelsChecksKt"`, runtime classpath including the adapters and dependencies, and args `[absoluteModelDirectory, "--speech"]`. Provide pinned speech files, tokens.txt, u2netp.onnx, en.wav and a 1.1-second 320x240 video.mp4. The checks never download those files.

Not yet verified: original IS-Net/BiRefNet real inference, rotated/non-square real-person tracking quality, long-file multi-chunk speech, voice-stem input path in an end-to-end test, native-call immediate cancellation, app UI binding, package native resource extraction under jpackage, or the user's Zorin machine. This is working adapter evidence, not complete app parity.

## API provenance

Official Java sources inspected at v2.0.3:

- https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v2.0.3/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/OfflineRecognizer.java
- https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v2.0.3/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/OfflineRecognizerConfig.java
- https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v2.0.3/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/OfflineModelConfig.java
- https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v2.0.3/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/OfflineRecognizerResult.java

Original implementation and contracts inspected in the isolated linux-desktop checkout: SpeechTranscriber.kt, SpeechEngine.kt, SubjectTracker.kt, CutoutEngine.kt, SpeechModel.kt, CutoutModel.kt, SubjectModelIo.kt, SpokenWords.kt and ListenedSound.kt. No branch writes were made.

## GUI native containment

Actual mouse-driven GUI testing exposed a sherpa token-parser process exit while
the identical pinned files passed in a headless JVM. The root cause is not yet
known. The GUI had loaded Skiko/Compose and GTK; the headless run had not. This
is a measured coexistence difference, not proof that GTK or a locale caused it.
Both runs used the same 256 MB Java heap cap. Native inference is now isolated
from the unsaved editor in a child process, for both speech and subject models.
The child verifies pins again, runs no GUI and sends JSON/progress files through
a private temporary directory. Native crashes become errors, not empty results.
Cancellation kills the child and descendants; the child watches its parent.

Packaged workers reuse the package launcher, not a nonexistent runtime/bin/java.
The inherited _JPACKAGE_LAUNCHER restart marker must be cleared, or the launcher
mistakes the worker for an internal re-exec and omits its configured main class.


Standalone dubbing: import exact original Kokoro archive (c5f7e2d2 prefix,349906910
bytes) after original pinned AudioSeal generator/detector. Current upstream
repacked archive is rejected. Original-hash mirror:
https://github.com/modelship-ai/model-bundles/releases/tag/tts/kokoro
Uses espeak-ng from apt, separately licensed GPL. No model weights bundled.
New WAV only,US-English,30cues/one-minute,1.2GiB free RAM, no automatic mixing.
Short outputs without enough voiced audio to verify AudioSeal are refused.
Saved16bit WAV mark/payload readback required. Other languages/Piper and wider
speech-quality validation remain unfinished. Existing17c package predates this.
