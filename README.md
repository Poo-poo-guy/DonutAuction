# DonutAuction+

A client-side Fabric auction overlay for **Minecraft Java 26.3**. A glass studio, real rotating item models, and individually rolling countdown digits keep the auction readable on stream.

## Install

1. Use Minecraft **26.3** with **Java 25** and **Fabric Loader 0.20.1 or newer**.
2. Install **Fabric API 0.165.2+26.3** (or a compatible newer release for 26.3).
3. Copy `donutauction-1.0.0.jar` from `build/libs` or the supplied release folder to your client's `.minecraft/mods` folder.
4. Join your world/server and press **F7** to open the studio.

Nothing is installed on the server. The mod sends no auction commands or network messages, reads no server auctions, and does not automate bidding. It is a local livestream display. Its use on DonutSMP depends on that server's current client-mod rules; no live DonutSMP connection was used during development.

## Default shortcuts

| Action | Default |
| --- | --- |
| Show / hide HUD | F6 |
| Open studio | F7 |
| Start / pause / resume | F8 |
| Reset to selected duration | F9 |
| Copy main-hand item | F10 |
| Add 10 seconds | `=` |
| Subtract 10 seconds | `-` |

Change shortcuts in **Studio → Controls** or **Minecraft → Options → Controls → Key Binds → DonutAuction+**. Click a shortcut, then press a keyboard key or mouse button; Delete unbinds and Escape cancels. Vanilla persists bindings in `options.txt`. In-world hotkeys do not intercept typing in other screens.

## Studio

- **Appearance:** HUD size (50–200%, starting at 65%), opacity, corner radius, rotation speed (including reverse/stop), soft shadow, three fonts, and individual hex colors for the background, accent, item name, timer, and final ten seconds. Hex input accepts `#RRGGBB` or `RRGGBB`; a complete valid color updates the preview immediately.
- **Timer:** enter seconds, `mm:ss`, or `hh:mm:ss`, then **Set duration**. Supports 1 second through 99:59:59. Setting a duration or choosing a preset resets and pauses the timer. Built-in presets: 30 seconds, 1 minute, 2 minutes, 5 minutes. Save up to eight presets; use the adjacent × button to remove any preset. Start/resume, pause, reset, and ±10-second buttons are live. Adding time to an ended timer leaves it paused. The timer never goes below zero.
- **Rolling digits:** choose Cinematic (cubic in/out), Silk (smoothstep), or Swift (quintic out), and 150–850 ms rolls. Changing style/speed takes effect on the next roll, preserving an in-flight animation's continuity. Each digit is an independent, clipped reel; reset and time edits also roll.
- **Item:** **Pick Item** slides and fades the library into the same glass panel over 300 ms. Search localized item names or registry IDs, scroll the icon grid, and click to select and return. Back/Escape returns without changing the selection. The picker contains registered non-air items and uses Minecraft's item renderer.
- **Use Held Item:** copies the main-hand `ItemStack`, including count, custom name, enchantments, model data, and supported components. Switching slots, moving, or changing the original stack does not alter the auction item. Empty hands leave the selection unchanged. The stack is encoded with Minecraft's registry-aware item codec, and decoded when a server/world connection supplies its registries. Components needing a removed mod or absent server registry may fail to load; failures are logged and the saved JSON is retained.
- **Position HUD:** drag the overlay, scroll to resize, and press Escape to return. Position is stored as a fraction of the available viewport, so it remains inside the screen across resolutions and GUI scales. The studio scales to fit the window and includes a live preview even while the HUD is hidden.

The clock uses `System.nanoTime()` deadlines, independent of rendering and the 20 Hz game tick. It continues while settings are open or the client loses focus; it does not run while Minecraft is closed. On launch it starts paused at the configured duration. It displays `hh:mm:ss` using ceiling seconds, so zero appears at the real deadline, with the configured visual roll completing afterward. Frame stalls jump the animation toward the current value without accumulating a queue of stale seconds.

## Settings and assets

Settings are in `.minecraft/config/donutauction.json`; the selected stack is included. Changes save after mouse release, duration/preset/item operations, closing the studio, and client shutdown. Saves use an atomic replacement where supported. Invalid configuration files are backed up before defaults are loaded. Finite values and ranges are validated on load/save.

Studio Sans and Studio Mono use bundled DejaVu fonts, with Minecraft fallback for additional glyphs. See `FONT-LICENSE.txt`. All textures and the icon are included. Surface rendering uses a nine-sliced antialiased mask and Minecraft GUI pipelines; item rotation uses a dedicated vanilla picture-in-picture renderer with model resolution, full-bright item lighting, and enchantment glint. It does not rotate a flat screenshot.

## Build

Use a **JDK 25** installation:

```sh
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean build
```

Windows: set `JAVA_HOME` to JDK 25 and use `gradlew.bat build`.

The included wrapper downloads **Gradle 9.8.2**. **Fabric Loom 1.19.4**, Minecraft **26.3**, Loader **0.20.1**, and Fabric API **0.165.2+26.3** are pinned. Output:

- `build/libs/donutauction-1.0.0.jar` — install this file.
- `build/libs/donutauction-1.0.0-sources.jar` — Java sources and resources.

Development client:

```sh
./gradlew runClient
./gradlew runClient -Pstudio
```

`-Pstudio` opens the studio after joining a local world for UI checks. Item components in 26.3 require a world registry before stacks can be constructed. The development-only JVM property does not change normal installed behavior. On a headless Linux host, start an X11 desktop and set `DISPLAY` before launching.

Core tests:

```sh
./gradlew test
```

See `docs/VALIDATION.md` for the checks actually performed and their limits. The source archive includes the complete Gradle project and wrapper; it excludes game assets, build caches, and private run-directory state.

## Compatibility verification

Verified before implementation using Mojang's [version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json), [Fabric Loader metadata for 26.3](https://meta.fabricmc.net/v2/versions/loader/26.3), and the actual [Fabric API 0.165.2+26.3 POM](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.165.2+26.3/fabric-api-0.165.2+26.3.pom). [Fabric's 26.3 porting notes](https://www.fabricmc.net/2026/09/15/263.html) describe SDL input and rendering changes. This project uses 26.3's unobfuscated names, `GuiGraphicsExtractor`, `InputConstants.Type.KEYBOARD`, the Fabric HUD registry, and the PiP renderer registry. It does not silently target an older version.

Not affiliated with Mojang, Microsoft, DonutSMP, or Fabric. Source code is CC0-1.0; bundled fonts retain their own license.
