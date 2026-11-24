# Creative effects expansion, 24 November 2026

Development work, not a claim of full CapCut or InShot parity. The goal is original local equivalents with matching Android/Linux catalogs and test builds. No proprietary presets, recordings or paid assets are copied.

## New shared catalogs
- 20 voice recipes including clean/reset: Old telephone, AM radio, walkie-talkie, megaphone, worn tape, tiny/deep/giant, alien, robot flutter, cyborg, double voice, dreamy/cave/stadium, underwater, 8-bit, ghost, warm narration. These replace DSP in the chosen scope; they are not AI voice models.
- 25 original algorithmically synthesized sound cues: whooshes/swipes/risers, bass impacts, clicks/pops/bleeps, notifications/chimes, telephone ring/busy, heartbeat, synthetic rain/wind. Not recorded Foley or another app's audio library.
- 10 new procedural looks: sepia, cool/warm tone, scanlines, poster colours, negative flash, faded matte, high contrast, dream blur, neon edges. Android shader previews are approximations; export is the authoritative check, particularly blur/edge and tone looks. Existing pre-Android13 look-preview limits still apply.
- 8 transitions: Venetian blinds, diamond mosaic, checker reveal, zigzag wipe, oval iris, angular sweep, stepped bars, cross reveal.
- 12 colour recipes including reset, composed from existing controls. These are new one-tap presets, not new filter algorithms. Blur/grain finishing touches require export inspection.

Linux adds a timed visual-effects panel with output or existing-clip occurrence binding and project persistence. Bound ranges use local played time including global speed; splitting redistributes coverage, duplication copies effects, removed clips hide their bound effects. Changing speed/trim changes the time coverage, so review after editing. Whole-output ranges remain fixed output times. These looks affect the composite, including layers, while the bound occurrence is playing, not isolated source pixels. Desktop playback remains render-first.

## User paths
Android: Audio effects for Voice presets; Volume/audio panel for Original sound cues; Colour for recipes; existing Effects picker for looks; Transitions for new shapes.
Linux: Audio or Clip editor > Sound for voice presets; Sound tracks > Original sound cues; Effects for timed looks; Clip editor > Colour for recipes; Clip editor > Transition, with group/search.

## Still outside this catalog
Changing proprietary pack catalogs, branded templates/stickers/music, licensed real-world recordings, face/body segmentation effects, AI generative style transfer, cloud effects and advanced motion/asset workflows are not recreated by these local recipes. Existing Android asset overlays and companion transitions are not automatically ported to Linux. The original broad effects request remains open beyond this catalog.
Known heavy multi-clip4K export failure and unqualified physical Zorin/audio/graphics remain unchanged.

## Current research
Vendor pages fetched 24 November 2026, category evidence rather than exhaustive manifests:
- https://www.capcut.com/resource/how-to-use-capcut (lens/retro/distortion/body; blur/split/slide/mask)
- https://www.capcut.com/tools/voice-filters (robot/chipmunk/deep/echo; AI services are not ordinary DSP)
- https://www.capcut.com/tools/sound-effects (themed library recordings and editable placement)
- https://www.capcut.com/tools/video-transition (fade/glitch/slide/zoom/3D, duration)
- https://www.inshot.com/ (effects packs including paint/flair/lens/blur/3D)
- https://play.google.com/store/apps/details?hl=en_GB&id=com.camerasideas.instashot (VHS/glitch/stop-motion/retro/RGB, 60+ transitions, sound/voice effects)
- https://www.makeuseof.com/inshot-mobile-app-how-to-edit-videos/ (2022 independent UI corroboration only, not current pack count)

Build, emulator and package qualification are reported separately against exact commits.

Projects with timed looks use desktop envelope version2 so old builds reject rather than silently discard effects. Sound-cue WAV references remain local absolute paths, keep generated files with your media; project transfer/regeneration is not automatic.

Telephone qualification includes original 16 kHz synthetic speech and deterministic noise, not only render success. The preset avoids EQ bands at 8/16 kHz because FFmpeg can erase a 16 kHz input at its Nyquist EQ edge; the 3.2 kHz low-pass provides the upper cut. Numerical spectral checks do not establish listening quality.

DSP/export rate behavior: non-neutral DSP and placed-sound EQ now work at 48 kHz to keep the supported EQ bands below Nyquist. Automatic output follows the processed rate; explicit output sample-rate settings are still applied at encoding. This can change output size/CPU work and is not claimed neutral without measurement. Android's live player processes its negotiated rate and bypasses at/above-Nyquist biquads; it is not sample-exact with low-rate exports. Desktop remains render-first. Check an exported copy for final sound.

Speech fixture provenance: scripts/make-creative-speech-fixtures.py uses eSpeak NG 1.50 with an original English test phrase at speed145, then FFmpeg PCM resampling. No user speech or third-party performance was recorded. eSpeak NG is GPLv3-or-later; its COPYING section2 limits output coverage to output that itself contains covered work. These fixtures synthesize our original phrase, not a copy of program/data or a recorded performance. Source: https://raw.githubusercontent.com/espeak-ng/espeak-ng/master/COPYING . The fixture is test-only and is not distributed as a commercial sound pack. Reproducing identical bytes requires the pinned synthesizer/data and FFmpeg versions. Cue v1 seeds are now explicitly keyed by name and fixed IDs, so enum reordering does not alter existing cue bytes.

Android qualification found non-finite samples from the telephone recipe's EQ-plus-bitcrusher combination in bundled FFmpeg6.1.6. Stage ablation passed without EQ or without crusher, but failed without compressor/mono/limiter. The telephone recipe now omits bitcrushing and retains band-limit, retained EQ, compression, mono and limiting. The underlying EQ-plus-crusher trigger remains unidentified. This is a narrower original telephone recipe, not a general shared-defect fix or silent finite-sample sanitizer.

The manual combination of EQ and lo-fi bitcrushing remains a known Android non-finite-output risk in this bundled runtime. No supplied preset now combines those two stages. The recipe change does not claim to repair every manual DSP combination.
