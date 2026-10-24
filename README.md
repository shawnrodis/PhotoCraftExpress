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
