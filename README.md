<p align="center">
  <img src="assets/icon.png" alt="PhotoCraft Express app icon" width="120">
</p>

# PhotoCraft Express

An Android photo editor (Kotlin + Jetpack Compose) whose interface recreates
the Adobe Photoshop Express editor — home photo grid, dark editor chrome,
bottom tool carousel — with entirely original code and hand-drawn icons.

## Credits

The adjustment engine's mathematics follows **PhotoCraft**
([github.com/storytold/photocraft](https://github.com/storytold/photocraft)),
the open-source, clean-room photo editor written in pure Rust by storytold /
the ArtCraft Team and contributors, dual-licensed **MIT OR Apache-2.0**. Its
Levels gamma (with the fitted shadow toe), natural-cubic-spline Curves LUT,
Vibrance pixel model and Saturation grey weights were adapted into Kotlin in
`engine/AdjustMath.kt`; the rest of PhotoCraft was consulted as the reference
for how the operations should behave. Full license texts live in `licenses/`,
and the repo-root `NOTICE` spells out exactly what was consulted vs adapted.

The interface recreates Photoshop Express's layout and flow with original
artwork — no Adobe code, assets or logos. PhotoCraft Express is **not
affiliated with or endorsed by storytold/ArtCraft or Adobe Inc.**

## What changed in v0.2.3

- **Adjustments panel, one continuous strip** — like Looks in v0.2.2:
  the 18 adjustment thumbnails, Split Tone's parameters, Color Mix's
  channels, Blur's modes and parameters, Vignette's parameters and
  Optics now flow as a single horizontally scrolling strip in tab
  order. The tab row tracks the strip position, tapping a tab jumps
  the strip to that section, and the single slider follows the
  selected strip item across sections. The v0.2.2 chip + single-slider
  layouts are unchanged inside each section; only the strip/tab
  behavior changed. The mask selector row on top is unchanged, and
  the Looks strip and its 87-preset catalog are untouched.
- **Crop: zoom while cropping** — v0.2.1 deliberately disabled zoom
  during Crop; that was the wrong call for Express parity. Pinch-zoom
  and pan now work inside Crop (and double-tap toggles zoom), with
  the crop overlay tracking the zoomed image.
- **Crop: handles that actually track your finger** — the old overlay
  had its own drag detector, corners only, no edge handles at all, so
  edge drags either moved the whole rect or did nothing, and it
  passed the raw pixel ratio as the normalized aspect lock (wrong
  ratio on non-square photos). Crop input now flows through the one
  canvas gesture gate: corner AND edge handles with a 56dp screen
  grab radius that stays constant at any zoom (hit-testing runs in
  screen space through the zoom), live resize on corner/edge drags,
  live move on inside drags, draft-during-drag with a single commit
  on release like every other tool. The overlay is draw-only now.
  (Honest residuals: Transform-tab handles still use their own
  detectors and raw pixel deltas, so their drags don't yet account
  for zoom the way the rect handles do; and pinch-plus-handle-drag
  at the same instant is untested on a real phone.)

## What changed in v0.2.2 (correction release)

- **Live sliders, actually fixed this time** — v0.2.1's "live sliders"
  did not work on a real phone: effects still only landed on release.
  The v0.2.1 theory (full-res render, no cancellation) was not the real
  break. The real break: the preview effect restarted — cancelling the
  in-flight render — on every drag tick, and drag ticks arrive faster
  than one render completes, so every render died before finishing and
  only the release value ever surfaced. v0.2.2 replaces that with a
  conflated render worker: drag values are offered to a conflated flow
  (only the latest survives) and a single worker renders each queued
  value to completion, so the canvas advances at render rate throughout
  the drag. The 1024px preview base is also cached instead of being
  rebuilt from the full-resolution source on every render. Undo history
  still commits once, on release. (Honest residual: the worker is
  verified at code level and by a unit test proving intermediate drag
  values publish; the feel of it on a physical phone is still the final
  gate, as with every preview change.)
- **Vignette / Split Tone / Color Mix / Blur tabs, restructured to
  Express's pattern** — icon chips pick the parameter and ONE shared
  slider controls it (was stacked labeled sliders). Vignette: Amount,
  Mid Point, Feather, Roundness chips with small icons; Amount keeps
  its +100..-100 range. Split Tone: Shadow Hue/Saturation and
  Highlight Hue/Saturation chips. Color Mix: colour-dot channel chips
  plus Hue/Saturation/Luminance chips, one slider for the pair. Blur:
  mode chips (None/Full/Radial) stay, parameters become icon chips +
  one slider (Width/Height Radial-only, as before).
- **Adjustment thumbnail strip, cleaned up** — every item is now the
  same fixed width with one consistent gutter and single-line labels
  (no more two-line wraps for "Reduce Color Noise" making items uneven
  heights), thumbnails aligned on one baseline. The row was already a
  lazy row with native fling; the ragged variable-width items were
  what made the glide feel stiff and uneven, so the uniform geometry
  is the fix. All 18 adjustments remain reachable by scrolling.
- **Thumbnail demo renders, verified visible** — a new test renders
  every strip thumbnail's demo adjustment against a photo-like
  synthetic scene and requires a visible difference from the base
  image, so a silently unapplied demo render can no longer ship.
- **Looks catalog, expanded to Express's breadth (87 presets, 13
  categories)** — Normal (7), Free (6), HDR (7), Charm (6), Black &
  White (8), Portrait (6), Matte (6), White Balance (6), Splash (7),
  Nature (6), Light (7), Warm (7), Duo Tone (8). The original 15
  presets are kept with their recipes unchanged (Fade and Matte moved
  from the old Faded group into Matte, the matching Express category).
  Every recipe is an original parameter combination through our own
  engine — no Adobe preset data, LUTs or artwork. The engine grew to
  express them: Looks now carry split tone, grain, vignette, color
  mix, clarity, texture and dehaze, which ADD to the user's panel
  values (same as temperature/tint already did), all scaled by the
  intensity slider; the Subject/Background mask base carries the
  Look's stages too, so the mask gates panel edits, never the Look.
  Splash is a real selective-colour grade (ColorMix desaturates every
  channel except the pop channel); Duo Tone is B&W plus a shadow /
  highlight tint pair. Honest approximations, noted not hidden:
  Portrait glamour softness is fade + low contrast (no blur-based
  skin softening in the engine); B&W Infrared is high contrast with
  crushed blacks (no true infrared channel response); complex
  color-grade LUTs are approximated by tone curves + split tone.
- **Looks strip, restructured to Express's model** — one continuous
  strip across all categories (no more per-category strips): scrolling
  flows Normal → … → Duo Tone, the category tab row auto-tracks the
  strip position, and tapping a tab jumps the strip to that category.
  The catalog expansion slots into this structure by extending the
  category list. Thumbnails render lazily — only visible items plus
  immediate neighbours, cached — so the 87-preset strip still scrolls
  smoothly; nothing renders eagerly on photo load any more.

## What changed in v0.2.1 (fidelity polish)

- **Adjustments panel, restructured to Express 1:1** — a mask selector
  row on top (Full / Subject / Background, each with a small thumbnail:
  Subject dims the background around the person, Background dims the
  person), a horizontal strip of per-adjustment live thumbnails (tap one
  to select it), and one main slider for the selected adjustment. Tabs
  are exactly ADJUSTMENTS / SPLIT TONE / COLOR MIX / BLUR / VIGNETTE /
  OPTICS; OPTICS shows a plain "Lens profile unavailable" state, matching
  what Express itself shows for photos without a lens profile. Subject
  and Background use the on-device person mask (ML Kit Selfie
  Segmentation, already bundled); with no person detected, Subject
  selects nothing and Background selects everything, and the panel shows
  a plain "No person found" notice. Every slider and tab from v0.2.0 is
  kept — this is a layout restructure, not a feature cut.
- **Vignette smoothness, effect and live preview** — the falloff is now a
  smoothstep curve (zero slope at both ends of the feather zone, so no
  seam where the falloff starts or ends and no banding), with channels
  rounded instead of truncated. The interactive preview renders through
  the exact same pipeline math as export.
- **Live sliders everywhere** — the canvas preview now follows every
  slider's transient drag value (Adjustments, Looks intensity, Split
  Tone, Color Mix, Blur, Vignette, Smooth, Face, Makeup, Eyes, Background,
  Overlay, Text, Sticker, Insert and Border sliders), instead of only
  updating on release. Undo history still commits once, on release —
  live preview and single-commit history are separate paths. (Root cause
  of the old behavior: the preview rendered at full resolution with no
  way to abandon a stale render, so drag-time renders queued behind each
  other and only the release value ever surfaced.)
- **Canvas zoom** — pinch to zoom (up to 8×) with pan, double-tap to zoom
  in/out (2.5× at the tap point), and a Reset zoom chip while zoomed.
  Zoom is a view transform only: it never alters the edit state, brush
  and tool coordinates are mapped back through it, and it resets when a
  new photo is loaded. Zoom gestures are inactive while cropping or
  split-comparing (those keep their own interactions); a previously set
  zoom stays visible.
- **Looks fix** — the v0.2.0 category tabs could never leave the visible
  category (the panel snapped back to the current look's category on
  every recomposition). Category taps now stick, and both the category
  tab row and the preset strip are explicitly full-width scrollable, so
  all 14 presets across all 6 categories are reachable.

Interactive-preview note: the live preview renders from a 1024px
long-edge downscale through the same pipeline as export (export itself
still renders at full resolution), and a superseded preview render
aborts at the next pipeline stage boundary instead of running to
completion — so slider drags stay smooth even on heavy pipelines.

## What works in v0.2.0

The carousel now matches Photoshop Express's tool order and names (Premium
and Get Adobe Express are deliberately absent — this app has no paywall,
no sign-in and no cloud features, by design):

- **Home** — app bar with search (filters by file name) and an About/credits
  dialog (now with a "Check for updates" button), a prominent Edit entry
  (system photo picker), a "jump back in" card for the last edited photo,
  and a grid of the device's photos (MediaStore).
- **Editor top bar** (Express order) — back · help (?) · auto-enhance
  (wand) · compare · download · share · ⋮ menu. Help opens the
  About/credits dialog with a short how-to. The wand is a real
  histogram-based auto-enhance (auto exposure/contrast/vibrance, applied
  as an undoable edit state — it lands in the Adjustments sliders).
  Compare is a split before/after view with a draggable divider (tap the
  button to toggle it); holding the button still shows the untouched
  original until release. Download saves straight to the gallery.
  The ⋮ menu holds About, Check for updates and Reset edits (undoable).
  Undo/redo are floating buttons at the canvas's bottom-left — undo is
  always there, redo appears beside it whenever a redoable state exists.
  The bar sits inside the safe-drawing inset gate, so nothing renders
  under the status bar or camera cutout.
- **Looks** — 14 presets with live-rendered thumbnails from the current
  photo and a 0–100 intensity slider, now grouped into categories like
  Express: Normal (Original, Vivid, Punch, Warm, Cool), Black & White
  (Mono, Noir), HDR (Drama, Chrome), Warm (Golden, Autumn), Light (Breeze,
  Pastel), Faded (Fade, Matte).
- **Adjustments** — the 11 original sliders plus Texture, Clarity, Dehaze,
  Reduce Color Noise, Reduce Luminance Noise, Fade and Grain, with tabs:
  Split Tone (shadow/highlight hue + saturation), Color Mix (8 channels —
  red, orange, yellow, green, aqua, blue, purple, magenta — each
  Hue/Saturation/Luminance), Blur (Full amount, or Radial with a draggable
  on-canvas ellipse + Amount/Feather/Roundness/size) and Vignette
  (Amount/Mid Point/Feather/Roundness).
- **Heal** — tap-to-heal spot tool with adjustable brush size: the patch is
  sampled from the ring around the spot and feathered in. Honest spot-heal,
  not AI inpainting.
- **Crop** — tabs Aspect Ratio / Rotate / Transform. Aspect chips now
  include social sizes (1.91:1, 4:5, plus the original set), rotate 90°,
  flip H/V, and a free-crop overlay with corner handles, move,
  rule-of-thirds grid and dimmed surround. Transform is corner-drag
  perspective/keystone correction, warped in the pipeline.
- **Smooth** — strength slider. **Global smoothing in this version, not
  face-masked** — it softens the whole photo.
- **Face** — on-device ML Kit face detection. Panels Face / Lips / Nose /
  Eyes / Contour with Face Size, Face Width and per-feature adjustments via
  landmark-guided mesh warp, bounded to subtle ranges. No face in the photo
  → a plain "No face found" state, like Express.
- **Reshape** — liquify brush: Reshape (push), Swell, Twirl, Restore modes
  with a brush-size slider; mesh warp, undo-integrated.
- **Makeup** — face-detection-based overlays at landmark positions with
  intensity: lip tint (colour choices), blush, eye shadow, eyeliner.
  Landmark overlays, not AI makeup.
- **Draw** — freehand brush strokes (colour, width, opacity) as a layer.
- **Background** — **people-only in this version** (see below): on-device
  ML Kit selfie segmentation gives Background Blur or a solid-colour
  Background Replace for photos containing a person; otherwise a plain
  "No person found" state.
- **Insert Objects** — insert another photo from the picker as a movable,
  scalable, rotatable layer, plus a brush-selection mode: brush on the photo
  to select an area, and the inserted photo can be clipped to that brushed
  region.
- **Overlays** — named overlay layers with intensity: Light Leak, Bokeh,
  Warm Gradient, Cool Gradient, Old Photo.
- **Text** — tabs Styles / Font / Color / Stroke: add/edit text, drag to
  position, system sans/serif/monospace, bold/italic, size, colour, and a
  stroke (outline width + colour). Built-in style presets: Basic, Quote
  (large serif italic), Meme (bold white with a thick black outline).
- **Stickers** — built-in drawn set (star, heart, arrow, circle, square,
  triangle, sparkle, smiley): tap to place, drag to move, scale + rotate.
- **Borders** — tabs Basic / Edges / Frames: a plain width+colour frame,
  decorative Edges (torn, scallop, double-line) and Frames (Polaroid,
  Filmstrip, Vintage).
- **Eyes** — Enhance (face-gated eye brightening with intensity) plus Red
  Eye and Pet Eye modes: tap on the glowing eyes to correct them to a
  natural tone. The tap modes work without face detection.
- **History** — undo/redo over immutable edit-state snapshots; slider drags
  and brush drags commit once, on release.
- **Export / Share** — Express-style sheet: a preview of the rendered
  photo on top, then format (JPEG / PNG), size (Original, long edge
  1080 / 2048, 50%, or custom W×H with an aspect lock) with the resulting
  dimensions shown (e.g. "1080 × 810 px"), and quality (default 85%,
  honoured in the actual encode — PNG is lossless, so quality doesn't
  change a PNG). A watermark row offers None or our own name drawn as
  plain text ("PhotoCraft Express", bottom-right, or centred with rule
  lines) — never Adobe's marks. Actions: Save to Gallery (the existing
  MediaStore path), Share via the system share sheet (which naturally
  covers Messages, Messenger, Drive and others — no app-specific targets
  are hardcoded), Set as Wallpaper, and Print (system print UI). No
  Facebook integration. The render itself is unchanged: full resolution
  with the 24 MP cap, FileProvider staging for shares.
- **Updates** — the About dialog has "Check for updates", and the app
  checks GitHub Releases on launch at most once per 24 hours. A newer
  release shows its notes with a Download button; the APK downloads to
  cache and hands off to the system installer. Nothing ever installs
  silently — the user always taps to install. The first true one-tap
  update requires the app to already be installed from a GitHub release,
  so the signature matches.

## Honest partials in v0.2.0

- **Background is people-only.** It uses ML Kit selfie segmentation, so it
  works on photos containing a person and shows "No person found"
  otherwise. General-subject segmentation is not in this version.
- **Smooth is global, not face-masked.** It softens the whole photo.
- **Heal is a ring-sample spot heal**, not AI inpainting; it removes small
  blemishes well and will not reconstruct structure.
- **Makeup is landmark overlays**, not AI makeup.
- Nothing in the carousel is a fake-working stub: every tool above does
  what its panel says. Premium, Get Adobe Express, the Adobe/Google/
  Facebook sign-in wall and Adobe's cloud GENERATE AI features are
  deliberately excluded — this app is free, offline and account-free.

## Engine notes

The whole render pipeline is pure Kotlin over raw ARGB pixel arrays
(`engine/PixelPipeline.kt`), so it is covered by JVM unit tests: identity at
defaults, exposure monotonicity, LUT composition, the PhotoCraft vibrance/
levels/curve behaviours, crop geometry and the dihedral remaps that keep the
crop rect correct across rotate/flip. Geometry (rotate/flips/crop) renders
first; then the Look LUT and the Adjust LUT compose into one 256-entry LUT;
then the per-pixel stage (mono blend → temperature/tint gains → vibrance →
saturation); sharpening is an unsharp mask over a three-pass box blur.

## Building

Uses the workspace-local toolchain (JDK 17, Gradle 8.7, Android SDK 35):

```sh
. ~/workspace/tools/env/android-env.sh
./gradlew :app:testDebugUnitTest   # JVM tests (see below)
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Package `com.shawnrodis.photocraftexpress`, minSdk 26, targetSdk 35,
version 0.2.0 (versionCode 3).

Build note: Gradle's Java networking is blocked in the build sandbox, so
the ML Kit dependency closure is resolved offline from a local Maven mirror
at `~/workspace/tools/local-maven` (wired in `settings.gradle.kts`); build
with `--offline` if the shared Gradle home contends. The face-detection
and selfie-segmentation AARs bundle their models on-device
(`models_bundled/*.tflite` in the face AAR, the `selfiesegmentation`
`.tflite` in the segmentation AAR), so no model download happens at
runtime — but the face/segmentation APIs still route through Play
Services classes, so a device without Play Services may fail analysis
gracefully (Face/Background tools show their "not found" states).
