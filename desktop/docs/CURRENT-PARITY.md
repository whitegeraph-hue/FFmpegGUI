# Current Linux preview status

Audited 2026-11-23 after d9114fd, with project-switch safety fix 54733c0.
This replaces the opening snapshot in PARITY.md for planning. PARITY.md remains
an execution history. Implemented does not mean every input or every UI path
has been qualified. The original full Linux request is still open.

## Current checkpoint

- d9114fd Debian amd64 preview: all three CI workflows green;933 shared tests,
  71 native checks; actual downloaded packaged launcher and CI apt-installed
  desktop entry inspected. Source monitor stays large at1366x768/nine clips.
- 54733c0 safety successor:72 native checks locally; clears history, selection,
  preview and transient analysis on successful project entry, validates first,
  and routes native chooser and launch project entry through the same path.
  All CI green and downloaded bundled launcher qualified with native project chooser
  and post-switch Undo. Delivered547 replaces d911. For d911 still restart between saved projects.
- 94d0b83 tests/docs milestone: all CI green,73native checks; combined selected
  scene edit plus embedded SRT save/delete-original/reopen/render checked in
  native tests and UI. Virtual audio route advances edited playback clock, not
  physical audio proof.
- Current source adds clip-bound speech with74native checks locally, actual
  selected-clip Parakeet inference and empty repeated-source neighbour in UI.
  CI/package qualification pending.
- Zorin release and physical graphics/audio/HDR remain untested. Java is bundled;
  FFmpeg, DejaVu fonts and eSpeak NG are package dependencies. Models are separate.

## Implemented with bounded evidence

| Area | Current behavior | Qualification limits / remaining work |
|---|---|---|
| Import and source geometry | Video/audio/stills, JPEG orientation1..8, supported Ultra HDR primary normalization, multi-video/still append | Audio-only timeline append and still-first mixed timeline blocked; broader formats/camera profiles need tests |
| Timeline | Split/trim/duplicate/remove/button reorder/Undo; selected occurrence; output ruler/zoom/playhead; speed/reverse/curves; overlap lanes | No thumbnails, audio waveforms, multitrack lanes or drag reorder; richer layer editing remains |
| Clip effects | Framing/colour/speed/pitch/volume/fades, animation keys/easing, ordinary transition families; mixed video/still transition export | Companion-asset punch/punch-back/technical-difficulties gated; representative transitions tested, not every kind/input |
| Preview | Labelled source seek; render-first edited picture/audio playback; pause/resume and output-clock frame steps | Not live edited preview; rendering delay; physical device playback untested |
| Selected smart edits | Pause/scene/beat and transcript cut/keep bind selected occurrence; source-clock mapping; stale result invalidation | Suggestions transient; all-tools selected-clip parity not established; combined scene/subtitle workflow qualified in source/native/UI, not a blanket package test |
| Speech and subject follow | Pinned speech/subject import; isolated inference; transcript edits; selected raw-clip speech with occurrence captions and split/duplicate lineage; single-source follow track | Natural speech can omit material; warnings do not catch every omission. Multi-source follow blocked; tracking quality needs broader footage |
| Audio | Perclip DSP, global mastering, modulation, placed tracks/keys, normalize, encoding; selected played-clip stems | No live DSP/waveforms/instrument splitting. Stems require2GiB free RAM and <=10min; separation bleed possible. Some Vorbis rate/bitrate combinations reject |
| Subtitles and lyrics | Embedded SRT/ASS/SSA; source binding/retiming; burn/style/colours/custom font; spoken SRT/TXT; lyrics metadata/stamping | Imported cues are not the spoken SRT/TXT export. Font files external, fallback if missing. Later edits do not retime stamped lyrics. Combined reopened-project render and virtual-clock playback checked |
| Picture/layers | Tone/detail/geometry/filter/effects/drawing/shapes; still/video/text layers, keys/binding where exposed; SVG/text art/GIF/WebP | No direct spatial layer dragging or live art/palette preview; video layers whole-output only, audio separate; advanced original art controls not all exposed |
| Cutout/erase/HDR | Subject masks and correction; embedded mask recovery; MI-GAN/LaMa separate PNG with disclosure checks; generated/original supported gain maps | Erase does not replace edited source automatically; HDR canvas/direct HDR brush blocked; camera breadth and HDR hardware untested |
| Translation/dub | Trusted direct-pair import; timed translated subtitles; marked standalone Kokoro WAV, optional placed track, project-cue snapshot | No multilingual/pivot routing or voice cloning. Review translation/timing. Later edits do not retime dub snapshot. Invisible marks not guaranteed after recompression/mixing |
| Project files | Linux envelope includes edits, sources, words, subtitles, layers, masks, cover, HDR, encoding and model settings | Sources remain external; no Android-envelope interoperability guarantee. Earlier development fixture opens, not all old distributed projects qualified. Undo/selection/suggestions not persisted |
| Export/studio | Shared builder, safe temp output/cancel/source protection, compression, metadata/cover/stabilization; private export draft, remember success, Undo | Distro filter/codec breadth not fully qualified. Studio bounded single-output argv adapter; no arbitrary paths/network/numbered outputs/full completion/help |
| Desktop interaction | Grouped rail/scrollbars, fixed monitor/timeline, Settings/themes, visible selected scope and format | Tonal rounded mobile continuity, not desktop Material3Expressive components. Only CtrlZ/Esc tested; no broad keyboard parity |

## Priority order

1. Safety547 and combined scene/subtitle workflow are qualified. Qualify the
   current clip-speech successor in CI/package before offering it as an update.
2. Audit each tool's scope against Android selected-clip semantics, starting with
   follow tracking. Speech raw selection is now scoped; source-wide Listen remains
   explicit with no clip selected. Clip words override source words for that
   occurrence. Speech inference is raw, before speed/reverse/DSP, then captions
   follow those edits. With no clip selected, transcript shows source-wide words
   only; reselect a recognized clip for its words. Source Listen does not replace
   keyed clip words. A word spanning a split can appear in both halves.
   Clip speech storage is capped equally on inference, save and load:10,000groups
   and200,000words; a rejected save leaves the old file intact. Multi-source follow still needs a per-clip filter pipeline,
   not removal of its current safety gate.
3. Timeline/layer interaction and keyboard access, preserving the current stable
   monitor and honest render-first behavior. Live-preview parity is a larger task.
4. Remaining adapters and native project interoperability, each with actual
   exported/restored-media checks and clear limits. No blanket parity claim from
   a successful compile or the availability of shared models.
5. Target Zorin installation/physical playback feedback and broader real media.

No release/tag/master merge has been made. Earlier delivered checkpoints remain
unchanged. More tests do not close the full request without the remaining workflows.
