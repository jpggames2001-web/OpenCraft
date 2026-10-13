# OpenCraft

A runnable Java 21 voxel survival game with LWJGL 3 and OpenGL 3.3 Core. The code, bitmap font, textures, meshes and PCM sound effects are generated independently in this repository.

**Status:** this implementation does **not** meet the requested 100% Minecraft Java Edition 1.0 fidelity. The world generator, graphics, interface details and several simulation systems differ. [FIDELITY.md](FIDELITY.md) records the implemented behavior and outstanding work explicitly.

## Build and run

A prebuilt JAR is included in [`dist/opencraft-0.1.0.jar`](dist/opencraft-0.1.0.jar). On the GitHub file page, choose **Download raw file**. This package needs a Java 21 runtime and an OpenGL 3.3 desktop; Maven is needed only to rebuild from source.

Download and test on Linux without building:

```bash
mkdir -p ~/OpenCraft-test
cd ~/OpenCraft-test
curl --fail --location --output opencraft-0.1.0.jar https://github.com/jpggames2001-web/OpenCraft/raw/refs/heads/main/dist/opencraft-0.1.0.jar
curl --fail --location --output SHA256SUMS https://github.com/jpggames2001-web/OpenCraft/raw/refs/heads/main/dist/SHA256SUMS
sha256sum --check SHA256SUMS
java -jar opencraft-0.1.0.jar
```

On Arch Linux, `sudo pacman -S jre21-openjdk` installs the runtime. The checksum identifies the committed binary; it is not a claim that the game's behavior exactly matches Minecraft 1.0.

Building from source requires a **JDK 21**, **Maven 3.9+**, an x86-64 machine, and a graphics driver supporting OpenGL 3.3. Both Linux and Windows native libraries are included in the packaged JAR. Java 17 is insufficient because this implementation uses Java 21 collection methods and virtual threads.

Arch Linux:

```bash
sudo pacman -S jdk21-openjdk maven
./build.sh
./run.sh
```

Windows, after installing JDK 21 and Maven and adding their `bin` directories to `PATH`:

```bat
build.cmd
run.cmd
```

Alternatively, on either platform:

```text
mvn --batch-mode --no-transfer-progress --strict-checksums verify
java -jar target/opencraft-0.1.0.jar
```

The shell launcher activates the prepared Java toolchain automatically in this cloud environment. Build after changing source; `run.sh` and `run.cmd` reuse an existing JAR.

Runtime data uses `%APPDATA%\OpenCraft` on Windows, `$XDG_DATA_HOME/opencraft` on Linux, or `~/.local/share/opencraft` as the Linux fallback. Override it when needed:

```bash
./run.sh --data-dir /workspace/opencraft-data
```

A graphical desktop is required for normal play. Cloud onboarding does not expose a desktop preview. The headless checks below exercise the code without a display.

## Play

Singleplayer → Create New World offers exactly **Default** and **SkyBlock**. Seeds accept a number or text; an empty seed chooses a random value. Survival and Creative are available. Saves support selection, rename, deletion, autosave, and save on exit.

Default generates seeded terrain, caves, ore veins, water, trees and climate-based biomes in 16×128×16 chunks. SkyBlock generates an L-shaped dirt/grass island, a tree and a chest containing a lava bucket, two ice blocks and a sapling. All other terrain is void. Break ice to obtain water; adjacent water sources can form an infinite source, and lava touching water makes obsidian or cobblestone.

Default bindings:

| Action | Binding |
| --- | --- |
| Move | W / A / S / D |
| Look | Mouse |
| Jump / swim | Space |
| Sneak | Left Shift |
| Sprint | Left Ctrl |
| Attack / mine | Left mouse |
| Place / interact / eat | Right mouse |
| Pick block in Creative | Middle mouse |
| Select hotbar slot | 1–9 / wheel |
| Inventory | E |
| Drop one item | Q |
| Chat / local commands | T |
| Pause / back | Escape |
| Debug information | F3 |
| Screenshot while playing | F2 |
| Fullscreen while playing | F11 |
| Change render distance | F |

Double-tap Space in Creative to toggle flight. Space rises and Shift descends. Controls can be rebound to keys or mouse buttons; duplicate assignments are highlighted, and X clears a binding.

Inventory left-click picks up, merges or swaps a stack. Right-click splits a stack or places one item. Shift-click transfers stacks; Shift-click crafting output repeats the recipe while space and ingredients remain. Closing a crafting screen returns unused ingredients. A crafting table opens a 3×3 grid, a chest opens storage, and a furnace opens input, fuel and output. Furnaces take 200 simulation ticks per smelt.

Local commands: `/give itemId [count]`, `/gamemode survival|creative`, `/time day|night|ticks`, `/tp x y z`, `/seed`, `/help`. These are development commands and are available in local Survival as well as Creative.

## Menus and assets

The menu hierarchy includes world creation/selection/rename/delete, server management/direct connection, video settings, controls, audio, languages, texture packs, pause, statistics, achievements, inventory, crafting, chest, furnace and death/respawn.

ZIP texture packs go in the runtime `texturepacks/` directory. Select a pack to apply it and preview its materials. Supported inputs:

- A classic `terrain.png` atlas, mapped for the supported legacy texture positions.
- `tiles/<numeric-id>.png`, one square image per block/item or special material. Images are resampled to 16×16.
- Special tiles: 200 grass top, 201 grass side, 202 log end, 203 crafting top, 204 sun, 205 moon, 206 star.

Missing tiles retain the procedural defaults. Archives are read directly, without filesystem extraction; image dimensions and entry sizes are bounded. This is a texture-pack loader, not a Java mod loader. Sound synthesis can gracefully disable itself when no audio device is present.

## Multiplayer

The server list, add/edit/delete, refresh ping and Direct Connect are implemented. The experimental client uses the legacy **protocol 22** handshake, offline-mode login, position updates, chat, chunk reception, block updates and player inventory packets. It is not a modern Minecraft client.

Original online-mode 1.0 authentication cannot be reproduced using the retired authentication service. The client rejects servers that require it rather than sending credentials. Remote containers and complete entity synchronization are not implemented; do not use this client to manage valuable remote inventory. Compatibility with an actual Minecraft 1.0 server has not been established. TCP tests use a local protocol fixture.

## Validation

```bash
./build.sh
./run.sh --headless-smoke --data-dir /workspace/opencraft-validation
```

The suite covers inventory conservation, recipes, jump/gravity/step/sneak collision behavior, fluids, light, chunk addressing, deterministic seeds, save restoration, corruption detection, options, sound samples, server-list persistence and a real local TCP handshake. The headless smoke creates its own temporary save; it does not modify existing worlds.

On Linux with Mesa/EGL, the graphical smoke can render without an X server:

```bash
EGL_PLATFORM=surfaceless MESA_SHADER_CACHE_DIR=/tmp/opencraft-mesa-cache \
  ./run.sh --render-smoke --null-platform --frames 145 \
  --data-dir /tmp/opencraft-render-validation
```

It creates a test world, renders the menu hierarchy, checks OpenGL errors, and clicks through world creation/rename/delete, settings, rebinding, crafting, chest/furnace transactions, server-list editing and texture-pack switching. Screenshots are written under `render-smoke/`. Run it with a dedicated data directory: it intentionally creates and deletes test worlds and changes options there. Without Mesa/EGL, omit `--null-platform` and run from a graphical desktop.

The GitHub Actions workflow runs the build and headless checks on Linux and Windows when pushed. Its Windows job has not been executed in the current cloud task.

## Source layout

| Package | Responsibility |
| --- | --- |
| `dev.opencraft` | Entry points, 20 Hz loop, input, options, startup validation |
| `world` | Chunk storage, seeded Perlin generation, biomes, lighting, block ticks, compressed saves |
| `physics` | AABB clipping and collision primitives |
| `game` | Player movement/survival, stack-safe inventory, item registry and recipes |
| `render` | Core OpenGL shaders, VAO/VBO meshes, transparency, fog, bitmap text, procedural texture atlas |
| `ui` | Screen navigation, fields/sliders, container transactions and HUD |
| `audio` | Asynchronous PCM synthesis using `javax.sound` |
| `net` | Persisted server list and experimental legacy protocol client |

See [VALIDATION.md](VALIDATION.md) for the checks performed and [DEPLOYMENT.md](DEPLOYMENT.md) for the exact commit and push commands. No commit or push is performed by the application or build scripts.
