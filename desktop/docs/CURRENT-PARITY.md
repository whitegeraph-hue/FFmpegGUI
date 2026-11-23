# Current Linux preview status

Audited 2026-11-23 through qualified 121b4d7; latest delivered build remains 0978ed9. PARITY.md remains execution history. The original full
Linux request remains open.

## Current checkpoint

- Qualified, not yet delivered: 121b4d7 adds one previous-manual-save backup
  and precise text/picture alignment. All four exact-SHA CI green, 933 shared
  tests, 91 unique native groups in exact Debian and clean Arch packages.
  Actual Debian and installed non-root Arch Apply/Undo and backup-recovery
  workflows checked. Manual backup is not autosave, history or power-loss
  recovery; physical target limits below still apply.
- Later source acceptance from a blank 1366x768 launch imported two videos,
  split and trimmed a selected occurrence, exercised reorder/Undo, added text,
  source-bound imported SRT and a soundtrack, saved, reopened in a fresh launch,
  played the rendered edit and exported through the native chooser. Independent
  H.264/AAC output probe and full decode pass; sampled frames show caption on
  both sources and the retimed SRT. This session exposed shared tool scrolling
  hiding subtitle import. The local fix resets on tool changes, including return
  visits, but retains scroll during same-tool editing. Actual before/after pixels
  confirm both behaviors. This combined source session is not yet exact-package
  qualification. PCM decoding confirms audio data, not physical listening or
  subjective mix quality.

- Delivered 0978ed9 after fixture-order repair: all four exact-SHA CI green,
  933 shared tests, Arch clean unfiltered90 native checks. Independent exact
  Debian90 native groups passed in disjoint44+46 processes. Exact Debian and
  non-root installed Arch compact launchers checked edge-scroll and alignment
  follow-through in their respective batches. Text/picture alignment saves and
  Undo, text Escape/outside cancellation, and actual saved-position text/picture
  renders checked. This is an alignment guide, not a composite preview.

- Delivered 6f1107: all four CI green. Exact Debian and Arch packages checked;
  Debian has 933 shared tests and both packages pass 87 native checks. Arch
  checks cover clean declared dependencies, 27 ELF library checks, non-root
  GUI/model workers, reinstall/file integrity and removal. Actual package drag,
  Undo and outside-release cancellation were checked on both packages.
- Arch package is x86_64 with bundled Java, not an AUR submission. Its userspace
  version record, generated PKGBUILD and namcap warnings ship in the CI proof.
  No claim of physical Arch audio, Wayland, touchpad or HiDPI qualification.
- Bundled launcher checks cover responsive controls, Undo/Redo, focused field
  history isolation, source peaks and Save/Save as destination. Save overwrites
  without backup in the latest delivered build. A previous-manual-save backup
  is qualified in 121b4d7 but not yet delivered. Save as switches the destination. History is 50 in-memory
  entries, not autosave or crash recovery.
- Delivered keyboard/reorder batch: buttons use the same occurrence-preserving
  move as drag; Left/Right refer to visible direction under global reverse.
  Timeline-focused Alt+arrows reorder, Home/End seek output start/end with
  reversed lane ownership. Source thumbnail decode at the source endpoint uses
  the final frame interval without changing the edit clock. Local
  source native suite and both exact package suites pass 87 checks.
- Clip-bound speech, official Rounded Symbols, source thumbnails, viewport
  culling, Undo selection/seek and a full-lane output guide are qualified in
  their earlier source/package checkpoints. The synthetic100clip4K-source test
  is bounded workflow evidence, not production HEVC/Intel performance proof.
- Zorin release and physical graphics/audio/HDR/GTK/HiDPI remain untested.
  Java is bundled; FFmpeg, DejaVu fonts and eSpeak NG are package dependencies.
  Models are separate. Initial no-control-focused synthetic-launch shortcut
  behavior remains unverified; focused field/timeline shortcuts were checked.

- Delivered compact workspace follow-through: at 1024x640, single scrollable timeline
  action row, shorter help and combined source/output header leave full clip lanes
  and a larger monitor. Actual trim/Apply/Undo/split/save/render checked. Wide
  1600x900 layout inspected. Not physical HiDPI proof.
- Delivered playback follow-through: a video may continue silently when audio-device
  acquisition/open is unavailable, with an explicit notice. Audio-only playback
  still reports failure. Decode/process failures are not converted to silent
  success. Controlled unavailable-device motion/pause/resume checks pass;
  physical audio-device success remains unqualified.

- Qualified a188955 follow-through after a0ae310: ruler and frame-step endpoint
  seeks share the Home/End visible-lane ownership. Regression reproduced a
  reversed-first-clip left-step at Home jumping to 750 ms. Local fix preserves
  zero/end; 86 native checks pass and actual compact endpoint pixels checked.
  All four exact-SHA CI pass. Independent Debian package86 native pass; exact
  Debian/Arch launchers checked for active-play drag staying paused, field focus,
  zoom+scroll, endpoints and busy seek rejection. Later owner-run checks on both
  exact packages confirmed rendering resumed after the controlled process pause
  and reached Rendered/ready, with enabled controls and no error. Final screenshots
  were inspected. This is owner execution evidence, not a separate reviewer rerun.
  Delivered with 6f1107.
- Delivered 6f1107 source marking: timeline-focused I/O and trim-panel playhead buttons set
  selected occurrence source in/out. Reverse keeps source-time semantics, not
  visual left/right. Invalid range, other occurrence, still, busy and no-op marks
  do not edit. One Undo restores trim/selection/position. Exact Debian/Arch compact
  launchers checked for buttons, focused keys, reverse, active-play pause,
  Undo, field isolation and busy/render completion. These are owner checks,
  not physical-target qualification.

## Implemented with bounded evidence

| Area | Current behavior | Qualification limits / remaining work |
|---|---|---|
| Import and source geometry | Video/audio/stills, JPEG orientation1..8, supported Ultra HDR primary normalization, multi-video/still append | Audio-only timeline append and still-first mixed timeline blocked; broader formats/camera profiles need tests |
| Timeline | Split/trim/duplicate/remove/handle and button reorder/Undo/Redo; selected occurrence; output ruler/zoom/playhead; speed/reverse/curves; overlap lanes | Source thumbnails are original footage, not live edits. Source mono peaks are qualified original sources, not an edited mix. No multitrack lanes; edge auto-scroll is package-qualified through the delivered batch; richer layer editing remains |
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
   access are qualified. Drag handle and keyboard/button follow-through are
  exact-package qualified through 6f1107. Drag-edge scrolling and normalized layer alignment are now package-qualified.
  Live edited preview, edited-mix waveforms
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

Clip controls are grouped as Trim, Motion, Colour, Sound and Transition. Exact source in/out drafts apply only on Apply; invalid ranges cannot change the edit. Timeline-local plain S, D, arrows, Space and Delete/Backspace split, duplicate, step, play or remove. Text fields keep their own keys. Reorder uses left/right buttons or the small arrow handle. Drag commits on release; Escape or outside release cancels. Local drag-edge scrolling uses a bounded speed near the viewport edge, keeps the dragged occurrence alive offscreen and preserves Escape/outside cancellation. Package-qualified in delivered0978ed9, following the earlier44ad7ba edge batch. Delivered Alt+arrows use visible direction, including global reverse; buttons now share drag's selected-footage preservation. Thumbnails are original-source stills, not live effects previews. The cache is at most64 160x90 images, decoding one at a time with a10second process timeout.

Qualification: real1366x768 click/keyboard workflow selected, sought, split, duplicated, trimmed, undid, saved with native chooser, reopened and rendered; dense9clips and60ms selection, grouped controls and rail scroll were inspected. S and Split agree at sped/overlap position. Native checks cover lane-owned seek under per-clip/global speed/reverse, ramps and transitions. Render succeeds in the test environment; physical Zorin audio and hardware are not qualified. Full app parity is still not complete.

UI checkpoint limits: a transition overlap switches the timeline to short alternating lanes with thumbnails when wide enough. Very short clips are reachable through the clip menu or zoom. The cache/decode work is bounded, but production4KHEVC responsiveness on physical Intel has not been qualified. Expanding a recognized clip's trim cannot create captions for newly included audio; run Listen again on the expanded clip. Selecting a clip resets scene/pause suggestions as before.


### Rounded Material Symbols
Desktop header actions, tool categories, editing sub-tools, grouped clip controls, transport and timeline actions use bundled official Google Material Symbols Rounded static24px paths, retaining text labels. Selected category uses filled symbol variant as well as the tonal selected surface. These are actual current Symbols assets, not Unicode placeholders, and work offline. This does not claim the full Android Material3Expressive component library or animated variable-font interpolation is ported. Sources, hashes and Apache2 license are bundled under material-symbols in the app resources. Official guide: https://developers.google.com/fonts/docs/material_symbols .

### Delivered ruler interaction follow-through

Ruler click and drag now focus the timeline, so Home/End and frame-step keys
work after leaving a trim field. Dragging seeks the output clock without
changing clip selection, project contents or Undo history. The controller
clamps endpoints and blocks seeks during a job. Source frame decoding keeps
the existing 120ms debounce; this is not continuous live effect rendering.
Actual 1024px checks cover normal/global-reverse projects, 2.25x zoom,
horizontally scrolled ruler coordinates and rendered-preview cueing.

Ruler seeking stops playback and stays paused on release. Press Play (or Space
with the timeline focused) to resume. Render/analysis jobs disable seeking.

### Delivered drag-edge scrolling

At2.25x zoom, an8occurrence project was dragged beyond the original viewport
right and left. Actual compact pixels show scrolling while held and release
commits once. Saved occurrence IDs confirm normal/reverse insertion, Undo restores
original order, Escape and outside release leave order/history unchanged.88 native
checks passed in two non-overlapping44group local runs (a single full run exceeds
the local command time limit). CI remained unfiltered. Reviewed and package-qualified in the delivered batch.

### Delivered layer alignment pad

Text and picture panels offer a compact dialog to set base X/Y together with
one drag and one Undo. Both picture and independently visible text were rendered
from moved saved-project positions and inspected. This is a normalized alignment guide, not a composite
preview or direct spatial bounding-box editor: the shared model uses fractions
of the free space around the layer. Render remains required. Position keys,
hidden/locked layers and cover fills disable the pad; other key properties stay
intact. Escape/outside release cancels. Local text/picture drag/save/Undo and
rendered saved-project positions checked; exact Debian/Arch qualification completed
in delivered0978ed9.
