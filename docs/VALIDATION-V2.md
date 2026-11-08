# DonutAuction+ V2 validation — 8 November 2026

## Build and automated tests

Minecraft remains **26.3**, with Fabric Loader **0.20.1**, Fabric API **0.165.2+26.3**, Loom **1.19.4**, Gradle **9.8.2**, and Java **25**. The existing project was upgraded in place.

`./gradlew clean build` succeeds. Eight JUnit tests pass: the original five timer/reel tests and three ending-state tests. They cover monotonic timing at simulated 60/144/240/360 FPS, pause/resume, edits and expiry, duration parsing, rolling-digit retargeting, easing, analytically integrated final rotation, the stopped completed state, interrupted-ending angle continuity, and timer cycle boundaries.

## Rendering design

The old interface enlarged a 64×64 rounded texture through the GUI's nearest-sampled texture pipeline. V2 removes that mask. Rounded panels, circles and controls now submit contour geometry with a coverage fringe calculated in framebuffer pixels from the GUI and local transforms. Cached contour samples avoid per-frame trigonometry; one geometry state replaces each nine-slice shape. Thin panel borders use framebuffer-pixel widths. Gradients use vertex color interpolation.

This is **geometric edge-coverage anti-aliasing**, not an SDF shader or whole-screen blur. Typography uses real Inter, Manrope, Outfit and Plus Jakarta Sans TTF assets at weights 400/600/700, with their SIL OFL licenses. Antialiased glyph coverage is cached in GPU atlases with linear sampling and transparent white padding. Raster density increases for large text transforms, and inactive atlases are released. Unsupported glyphs fall back to Minecraft's font. Item models and textures stay in Minecraft's item/PiP rendering path with their original pixel-art sampling.

## In-world observations and interactions

Tested in the previously approved disposable local creative world using Mesa llvmpipe Vulkan software rendering, at **1180×812** and **854×480**, with Minecraft's automatic GUI scale adapting between the window sizes.

- The complete studio, fields, sliders, toggles, circles, panels and borders render with the new geometry and typography. The first geometry prototype revealed a winding-order/culling defect; it was corrected before the final build.
- The font dropdown visibly contains exactly four families. Selecting Manrope persisted the choice. The completed auction remained completed while opening settings and changing typography.
- Three-second auctions reached the completion message, with the item in its stationary final orientation. The final build was also checked with an enlarged HUD to exercise adaptive glyph density and coverage edges.
- Adding ten seconds after completion restored paused countdown mode and normal rotation.
- The updated item picker opened in the shared panel. Its focused custom-rendered search field accepted `emerald`, filtering 1,657 items to four matching real-item icons. Selecting Emerald returned to settings, cleared completion, and restored the configured three-second countdown.
- Rounded UI and premium typography remained inside the smaller viewport after resizing. The HUD's previous item name, Sharpness V component, and position survived migration from the V1 configuration.
- Saving a three-second snapshot added an auction snapshot without removing the original numeric 30/60/120/300-second presets. The saved JSON contains duration, the captured **Streamer Blade** diamond sword, Sharpness V, typography and other visual settings. Snapshot data does not recursively contain other snapshots.

## Limits and the interrupted final check

The final click to reload that snapshot was **not executed**: the automatic approval-review service reported a usage-limit failure. This was a review-service failure, not a determination that the operation was unsafe. Snapshot reload therefore is implemented but was not manually verified during this session. No workaround was used to execute the blocked click.

There was no real GPU benchmark at 240 FPS, DonutSMP server connection, OBS Game Capture test, exhaustive Unicode/IME test, or full third-party shader/mod compatibility matrix. The software renderer and simulated-FPS tests do not establish a hardware FPS guarantee. Mojang/Realms authentication and audio-device warnings in this offline environment were unrelated to the local-world rendering checks.

See `VALIDATION.md` for the earlier V1 checks, which are historical rather than claims of re-running every V1 interaction on V2. The final V2 jar and full Gradle project are provided separately.
