# Validation — 8 November 2026

## Build and automated checks

- Verified Minecraft 26.3 through Mojang's released-version manifest, Loader 0.20.1 through Fabric's version metadata, and the actual Fabric API 0.165.2+26.3 dependency.
- Built with JDK 25.0.5.0.3, Gradle 9.8.2, and Fabric Loom 1.19.4. `./gradlew clean build` and subsequent builds after the rendering fixes succeeded.
- Five JUnit tests pass with zero failures/errors. Coverage includes monotonic deadline accuracy at simulated 60/144/240/360 FPS; pause/resume, time edits, stalls, and expiry; duration parsing; uninterrupted reel retargeting and wraparound; and monotonic easing for all three styles.
- Inspected the packaged jar: client-only Fabric metadata targeting exactly 26.3, Java classes, both fonts, texture/icon assets, translations, and licenses are included.
- `git diff --check` passed.
- The first GitHub Actions run hit the starter wrapper's 10-second Gradle-download timeout before compilation. The wrapper now allows 60 seconds with three retries while retaining SHA-256 distribution verification. A subsequent clean runner exposed a resource-processing closure referencing the Gradle project at execution time; capturing the version during configuration fixes it. Clean local builds were verified with both a new and reused configuration-cache entry.

## Checks performed in Minecraft

The final implementation was launched in Minecraft 26.3 with Fabric Loader 0.20.1 and Fabric API 0.165.2+26.3. Testing used an approved disposable local creative world in the development run folder, 1180×812 and 854×480 client windows with automatic GUI scaling, and Mesa 25.0.7 llvmpipe Vulkan software rendering. No DonutSMP connection was made.

Observed and exercised:

- The glass studio, bundled fonts, live preview, actual item models, item rotation, and enchantment glint render in-world.
- The 30-second preset resets the timer. Start runs the countdown to zero, the final ten seconds use the urgent color, and individual digits visibly roll through the clipped window.
- Pause holds the remaining value, reset restores the configured duration, and the add/subtract controls and shortcuts change time by ten seconds. F7 opens settings, F8 controls the timer, and F6 hides/restores the HUD.
- The library contains 1,657 registered non-air items. Searching `netherite` filters the real-icon grid to 16 results. Selecting the netherite sword returns to settings and persists `minecraft:netherite_sword` in the configuration.
- A diamond sword named **Streamer Blade** with **Sharpness V** was created in the local world. F10 copies it. Switching to an empty hotbar slot leaves the selected auction item unchanged. Inspection of the saved item confirms both `minecraft:custom_name` and the Sharpness V enchantment component.
- Dragging the HUD changes its placement, and scrolling in positioning mode resizes it. The updated normalized coordinates and scale are written to the configuration.
- After resizing the client to 854×480, the HUD scales and stays within the viewport with its normalized position.
- After a full client restart and reconnect to the local world, the captured custom-named enchanted item, size, and position restore correctly. The timer starts paused at the selected preset duration.

The in-world check exposed a thin neighboring-reel glyph fragment while the timer was at rest. Rendering now submits only relevant reel rows and uses a tighter clip window. A rebuilt client was relaunched and the clean resting timer was visually confirmed in-world. Timer glyph components and formatted strings are cached to reduce per-frame allocation. The picker search hint contrast and automatic focus were also improved.

## Limits

The tests above establish observed behavior on this local client and deterministic timer math. They are not a real 60–240+ FPS GPU performance benchmark. No DonutSMP session, live auction, third-party shader/mod compatibility matrix, exhaustive resolution/GUI-scale matrix, or real-account authentication test was performed. Additional color/font combinations, custom key remapping, and custom-preset edge cases were not all manually exercised.

The offline development client logged unavailable Mojang authentication/Realms services. Those were unrelated to the local-world HUD checks. No account credentials or private run-directory state are included in the deliverables.

## Reproduce

```sh
./gradlew clean build
./gradlew test
./gradlew runClient -Pstudio
```

Join a disposable local world to open the studio automatically with `-Pstudio`, or press F7 in a normal world. For further coverage, vary GUI scale and resolution, try other fonts and easing curves, save/remove custom presets, and rebind controls. These are follow-up checks, not claims of completed testing.
