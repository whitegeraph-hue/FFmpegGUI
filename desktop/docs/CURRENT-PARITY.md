# Current Linux preview status

Audited 2026-11-23 through delivered004face and local drag qualification. PARITY.md
remains execution history. The original full Linux request remains open.

## Current checkpoint

- Delivered004face: all three CI green,933shared tests and80native checks against
  exact package jars. Bundled launcher checks cover responsive controls, Undo/Redo,
  focused field history isolation, source peaks and Save/Save as destination.
  Save overwrites without backup. Save as switches the destination. History is
  50 in-memory entries, not autosave or crash recovery.
- Next drag checkpoint: local compile and81native pass. Actual virtual display
  checks include1024x640,1280x720, global reverse, zoomed/scrolled content,
  one Undo, no-op, Escape and outside release. Exact package is pending.
- Clip-bound speech, official Rounded Symbols, source thumbnails, viewport
  culling, Undo selection/seek and a full-lane output guide are qualified in
  their earlier source/package checkpoints. The synthetic100clip4K-source test
  is bounded workflow evidence, not production HEVC/Intel performance proof.
- Zorin release and physical graphics/audio/HDR/GTK/HiDPI remain untested.
  Java is bundled; FFmpeg, DejaVu fonts and eSpeak NG are package dependencies.
  Models are separate. Initial no-control-focused synthetic-launch shortcut
  behavior remains unverified; focused field/timeline shortcuts were checked.

## Implemented with bounded evidence

| Area | Current behavior | Qualification limits / remaining work |
|---|---|---|
| Import and source geometry | Video/audio/stills, JPEG orientation1..8, supported Ultra HDR primary normalization, multi-video/still append | Audio-only timeline append and still-first mixed timeline blocked; broader formats/camera profiles need tests |
| Timeline | Split/trim/duplicate/remove/handle and button reorder/Undo/Redo; selected occurrence; output ruler/zoom/playhead; speed/reverse/curves; overlap lanes | Source thumbnails are original footage, not live edits. Source mono peaks are qualified original sources, not an edited mix. No multitrack lanes or drag edge auto-scroll; richer layer editing remains |
| Clip effects | Framing/colour/speed/pitch/volume/fades, animation keys/easing, ordinary transition families; mixed video/still transition export | Companion-asset punch/punch-back/technical-difficulties gated; representative transitions tested, not every kind/input |
| Preview | Labelled source seek; render-first edited picture/audio playback; pause/resume and output-clock frame steps | Not live edited preview; rendering delay; physical device playback untested |
| Selected smart edits | Pause/scene/beat and transcript cut/keep bind selected occurrence; source-clock mapping; stale result invalidation | Suggestions transient; all-tools selected-clip parity not established; combined scene/subtitle workflow qualified in source/native/UI, not a blanket package test |
| Speech and subject follow | Pinned speech/subject import; isolated inference; transcript edits; selected raw-clip speech with occurrence captions and split/duplicate lineage; single-source follow track | Natural speech can omit material; warnings do not catch every omission. Multi-source follow blocked; tracking quality needs broader footage |
| Audio | Perclip DSP, global mastering, modulation, placed tracks/keys, normalize, encoding; selected played-clip stems | No live DSP/edited-mix waveforms/instrument splitting. Stems require2GiB free RAM and <=10min; separation bleed possible. Some Vorbis rate/bitrate combinations reject |
| Subtitles and lyrics | Embedded SRT/ASS/SSA; source binding/retiming; burn/style/colours/custom font; spoken SRT/TXT; lyrics metadata/stamping | Imported cues are not the spoken SRT/TXT export. Font files external, fallback if missing. Later edits do not retime stamped lyrics. Combined reopened-project render and virtual-clock playback checked |
| Picture/layers | Tone/detail/geometry/filter/effects/drawing/shapes; still/video/text layers, keys/binding where exposed; SVG/text art/GIF/WebP | No direct spatial layer dragging or live art/palette preview; video layers whole-output only, audio separate; advanced original art controls not all exposed |
| Cutout/erase/HDR | Subject masks and correction; embedded mask recovery; MI-GAN/LaMa separate PNG with disclosure checks; generated/original supported gain maps | Erase does not replace edited source automatically; HDR canvas/direct HDR brush blocked; camera breadth and HDR hardware untested |
| Translation/dub | Trusted direct-pair import; timed translated subtitles; marked standalone Kokoro WAV, optional placed track, project-cue snapshot | No multilingual/pivot routing or voice cloning. Review translation/timing. Later edits do not retime dub snapshot. Invisible marks not guaranteed after recompression/mixing |
| Project files | Linux envelope includes edits, sources, words, subtitles, layers, masks, cover, HDR, encoding and model settings | Sources remain external; no Android-envelope interoperability guarantee. Earlier development fixture opens, not all old distributed projects qualified. Undo/selection/suggestions not persisted |
| Export/studio | Shared builder, safe temp output/cancel/source protection, compression, metadata/cover/stabilization; private export draft, remember success, Undo | Distro filter/codec breadth not fully qualified. Studio bounded single-output argv adapter; no arbitrary paths/network/numbered outputs/full completion/help |
| Desktop interaction | Grouped rail/scrollbars, fixed monitor/timeline, Settings/themes, visible selected scope and format | Official bundled Rounded Symbols, not full Expressive components/motion. Focused Open/Save/Undo and timeline-local keys checked; no broad keyboard or physical target guarantee |

## Priority order

1. Continue the requested desktop editing experience, preserving the qualified
   saved-project, source-protection, cancel and occurrence-scope behavior.
2. Timeline/layer interaction and keyboard access. Redo and short-window tool
   access are qualified. Drag handle exact-package qualification is next. Live edited preview, edited-mix waveforms
   and direct spatial layer dragging remain separate unfinished work.
3. Remaining tool adapters and selected-clip semantics. Multi-source follow needs
   a per-clip filter pipeline, not removal of its current safety gate. Source
   Listen remains explicit, while clip words belong only to that occurrence.
4. Native project interoperability and broader media checks. No blanket parity
   claim follows from compilation or availability of shared models.
5. Target Zorin installation/physical playback feedback and broader real media.

No release/tag/master merge has been made. Earlier delivered checkpoints remain
unchanged. More tests do not close the full request without the remaining workflows.


## Desktop editing UI checkpoint

The monitor keeps its place while the 204dp timeline shows source thumbnails, file names, output-time clip spans and a fixed ruler. Fit/zoom and scrolling are separate from editing. Single click selects and seeks inside that occurrence, including its transition lane; double click opens its controls. A clip menu exposes very short clips without pixel hunting. The menu shows exact source durations in milliseconds.

Clip controls are grouped as Trim, Motion, Colour, Sound and Transition. Exact source in/out drafts apply only on Apply; invalid ranges cannot change the edit. Timeline-local plain S, D, arrows, Space and Delete/Backspace split, duplicate, step, play or remove. Text fields keep their own keys. Reorder uses left/right buttons, not drag. Thumbnails are original-source stills, not live effects previews. The cache is at most64 160x90 images, decoding one at a time with a10second process timeout.

Qualification: real1366x768 click/keyboard workflow selected, sought, split, duplicated, trimmed, undid, saved with native chooser, reopened and rendered; dense9clips and60ms selection, grouped controls and rail scroll were inspected. S and Split agree at sped/overlap position. Native checks cover lane-owned seek under per-clip/global speed/reverse, ramps and transitions. Render succeeds in the test environment; physical Zorin audio and hardware are not qualified. Full app parity is still not complete.

UI checkpoint limits: a transition overlap switches the timeline to short alternating lanes with thumbnails when wide enough. Very short clips are reachable through the clip menu or zoom. The cache/decode work is bounded, but production4KHEVC responsiveness on physical Intel has not been qualified. Expanding a recognized clip's trim cannot create captions for newly included audio; run Listen again on the expanded clip. Selecting a clip resets scene/pause suggestions as before.


### Rounded Material Symbols
Desktop header actions, tool categories, editing sub-tools, grouped clip controls, transport and timeline actions use bundled official Google Material Symbols Rounded static24px paths, retaining text labels. Selected category uses filled symbol variant as well as the tonal selected surface. These are actual current Symbols assets, not Unicode placeholders, and work offline. This does not claim the full Android Material3Expressive component library or animated variable-font interpolation is ported. Sources, hashes and Apache2 license are bundled under material-symbols in the app resources. Official guide: https://developers.google.com/fonts/docs/material_symbols .
