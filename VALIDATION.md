# Validation performed in the cloud task

Validation targets the current OpenCraft implementation, not parity with the original Minecraft 1.0 binary.

## Automated build and core checks

- Java: Eclipse Temurin 21.0.12.1+1; Maven: 3.9.11.
- `mvn --batch-mode --no-transfer-progress --strict-checksums verify`: **47 tests passed**, zero failures, errors or skipped tests.
- `build.sh` and `run.sh` executed in their documented working directory.
- Packaged-JAR `--headless-smoke`: **passed**. It exercises Default/SkyBlock generation, starter chest, crafting, AABB collision, chunk/player/inventory persistence and light.
- Dependency downloads used the supplied cloud egress proxy and a trusted CA bundle. JDK/Maven distribution archives passed official checksum verification; Maven dependency checksum checking remained enabled.

Test groups: 12 inventory/recipe tests, 13 world tests, 10 physics tests, 5 persistence tests and 7 network/asset/audio tests. Maven's detailed reports are under `target/surefire-reports/` after a build.

## Native graphics and interface checks

The packaged application ran on Linux x64 with Mesa 25.0.7, llvmpipe and an EGL context through GLFW's null platform. The context reported OpenGL 4.5 Core; the application requests and uses the OpenGL 3.3 Core feature set.

The 145-frame graphical smoke completed with **zero OpenGL errors** and assertions for:

- Singleplayer navigation; world creation; Default/SkyBlock and Survival/Creative toggles.
- Graphics settings, stereo anaglyph, key rebinding, volume slider and language selection.
- Crafting, chest item transfer, furnace smelting and output transfer.
- World rename persistence, deletion confirmation and deletion.
- Server-list add, edit, persistence and deletion.
- Texture-pack selection, actual material replacement and restoring generated textures.
- Survival inventory and closing containers with the bound Inventory key.

The smoke renders the remaining documented screens and writes screenshots, but merely rendering a screen does not prove every action on it is correct. Images of the world, title, video settings and container screens were inspected during the task.

## Limits of this evidence

Windows CI is configured but has not run in this task. Arch Linux desktop startup, a hardware graphics driver, actual audio playback, actual Minecraft 1.0 server interoperability, and original-game differential comparisons remain unverified.

[FIDELITY.md](FIDELITY.md) lists missing original systems and known differences. A copy of the validated JAR and its SHA-256 checksum are included in `dist/`; [DEPLOYMENT.md](DEPLOYMENT.md) supplies the publication commands. Saved cloud configuration is a draft until the user reviews/saves it and publishes the environment; restoration in a new task has not been tested.
