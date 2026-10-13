package dev.opencraft;

import dev.opencraft.game.*;
import dev.opencraft.world.*;
import java.nio.file.*;

public final class HeadlessSmoke {
  private HeadlessSmoke() {}

  public static void run(Path data) throws Exception {
    WorldStore store = new WorldStore(Files.createTempDirectory(data, "smoke-"));
    var save = store.create("Validation", 12345, Generator.Type.SKYBLOCK, false);
    var w = new World(store, save);
    w.loadAround(1);
    require(w.block(0, 64, -1) == Block.GRASS, "SkyBlock surface");
    require(w.block(200, 64, 200) == Block.AIR, "SkyBlock void");
    require(w.tile(new World.Pos(2, 65, -2)).inventory.count(327) == 1, "Starter chest");
    w.player.inventory.add(new Stack(17, 1));
    Inventory grid = new Inventory(4);
    grid.set(0, w.player.inventory.remove(0, 1));
    var match = Recipes.match(grid, 2).orElseThrow();
    require(match.result().id() == 5 && match.result().count() == 4, "Crafting recipe");
    Recipes.consume(grid, match);
    w.player.inventory.add(match.result());
    for (int i = 0; i < 40; i++) w.player.tick(w, Player.Input.NONE);
    require(w.player.onGround, "Player collision");
    w.set(0, 65, -2, Block.CRAFTING_TABLE, 0);
    w.save();
    World restored = new World(store, save);
    require(restored.block(0, 65, -2) == Block.CRAFTING_TABLE, "Block persistence");
    require(restored.player.inventory.count(5) == 4, "Inventory persistence");
    World normal = new World(new Generator(12345, Generator.Type.DEFAULT));
    Chunk chunk = normal.chunk(0, 0);
    Lighting.update(chunk, normal);
    require(chunk.block(0, 0, 0) == Block.BEDROCK, "Default terrain foundation");
    require(normal.light(0, 127, 0, true) == 15, "Skylight");
    System.out.println(
        "HEADLESS_SMOKE_OK: world generation, island chest, crafting, collision, saves and"
            + " lighting");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
