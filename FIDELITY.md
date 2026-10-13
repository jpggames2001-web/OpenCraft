# Fidelity and validation status

The original requirement is an identical recreation of Minecraft Java Edition 1.0. **That objective has not been achieved.** Passing this project's tests establishes the behavior described below; it does not establish parity with the original game.

There is no original-game reference fixture, exhaustive specification or differential replay suite in this repository. Exact seed compatibility, numerical physics parity under every condition, pixel-perfect menus and matching all original block/entity rules have not been verified.

## Implemented and exercised

| System | Evidence and current behavior |
| --- | --- |
| Build | Java 21/Maven produces a runnable JAR containing Linux/Windows x64 LWJGL natives |
| Graphics | OpenGL 3.3 Core shaders, VAOs/VBOs, exposed-face chunk meshing, fog, sky, sun/moon, clouds, tinting, transparency, optional AO/frustum culling and stereo anaglyph |
| Assets | Code-generated 16×16 materials, a code-defined bitmap font, procedural PCM effects, and texture-pack switching |
| Worlds | Seeded Default terrain and SkyBlock island/void; 16×128×16 block/data/light arrays; biome columns |
| Movement | 20 Hz simulation; 0.6×1.8 player bounds; 0.42 jump impulse, 0.08 gravity, 0.98 vertical drag; axis clipping, half-block stepping, sneaking-edge protection and liquid damping |
| Mining/placing | Raycast selection, hardness/tool-speed progress, drops, placement collision checks and block interactions |
| Inventory | Stack-conserving merges, splitting, swapping, right/Shift-click behavior, hotbar, armor and Creative palette |
| Crafting | Shaped, mirrored and shapeless recipes, 2×2/3×3 grids and repeated crafting |
| Containers | Local chest storage and fuel/input/output furnace simulation with 200-tick smelting |
| Block ticks | Sand/gravel falling, limited liquid spreading/mixing, sapling growth, grass spread and basic crop progression |
| Survival | Health, food/saturation/exhaustion, fall damage, drowning, hazard damage, death and respawn |
| Time/light | 24000-tick cycle; per-chunk sky/block light flood fill, cross-border seeding and directional shading |
| Saves | UUID directories, bounded versioned binary state, gzip chunks, atomic replacement, inventory/container/entity restoration, rename/delete |
| UI | All menus explicitly listed in the request have navigation and actions; settings persist |
| Network | Local fixture validates ping, protocol-22 handshake/offline login, position reception and authoritative block updates |

## Known differences and missing original systems

- **Terrain:** the generator is a new Perlin-based implementation. It does not implement original GenLayer biome selection, original cave carvers or population ordering. The same seed will not yield the original world. Structures, villages, strongholds, ravines, dungeons, the Nether and the End are absent.
- **Biomes:** biome definitions include temperature/rainfall/tints, but selection and vegetation are approximate. Original color-map interpolation and every biome-specific feature are not reproduced.
- **Physics:** constants follow classic conventions; corner cases, step decisions, swimming escape behavior, ladders, sprint exhaustion and creative flight are not validated against original replays. Broad-phase collision and shape support are incomplete for many special blocks.
- **Block behavior:** numeric IDs exist for the 1.0-era block catalogue, but registration does not mean that every original behavior exists. Pistons, repeaters, dispensers, rails/minecarts, note blocks, portals, enchantment tables, brewing stands, cauldrons, fences/stairs/doors in every orientation and tile metadata are not complete simulations.
- **Liquids/redstone:** spreading/mixing and wire/lever/iron-door power cover basic cases. Liquid decay/drainage, original scheduled-tick ordering, full circuit behavior and light-removal convergence across all borders are incomplete.
- **Items/recipes:** tool sets and many standard crafting/smelting recipes are present. All metadata variants, every original recipe, enchantments, potions, durability effects, repair rules and equipment modifiers are not complete.
- **Entities:** eight simple mob kinds can spawn, move, receive damage and drop items. These are independently implemented approximations, not the original AI, models, spawn algorithms or animations. Bosses, projectiles, boats, minecarts, breeding and complete entity persistence are absent.
- **Rendering/assets:** procedural artwork, bitmap glyphs, models and synthesized sounds are visibly/audibly different. Panorama is a rotating generated scene, not the original six-image panorama. Smooth lighting, transparency sorting, weather, hand animations, skin rendering and all item models are not exact.
- **UI:** the requested menu hierarchy is implemented, but exact layouts, button text, focus rules and all historical screens are not identical. Music/sound/language/world-type choices include requested extensions to the 1.0 baseline. Translation covers common menu labels rather than the full game. Achievements/statistics are a subset; resource packs do not execute mods.
- **Multiplayer:** server management is functional. Gameplay networking remains experimental: online-mode authentication, remote container synchronization, authoritative entity rendering and the complete legacy packet semantics are unresolved. An actual 1.0 server has not been used for validation.
- **Save compatibility:** `.oc` files are OpenCraft's format, not Minecraft region/NBT saves. No import/export compatibility is claimed.
- **Platforms:** the current task tests Linux x64 under Debian/Mesa llvmpipe. Arch Linux and Windows runtime checks remain unexecuted; CI and startup scripts are supplied for them.

Completing the original objective requires original-version behavior fixtures and differential tests for generation, physics, block tick ordering, every recipe/entity, multiplayer packets and UI/image output, followed by implementing the missing systems. The current implementation should not be advertised as a complete or identical 1.0 recreation.
