package dev.opencraft;

import static org.junit.jupiter.api.Assertions.*;

import dev.opencraft.game.Stack;
import dev.opencraft.world.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
  @TempDir Path dir;

  @Test
  void roundTripPreservesChunksPlayerChestAndFurnace() throws Exception {
    WorldStore store = new WorldStore(dir);
    var save = store.create("World", 99, Generator.Type.SKYBLOCK, false);
    World w = new World(store, save);
    w.player.inventory.set(5, new Stack(278, 1, 50));
    w.player.x = 12.25;
    w.player.y = 76;
    w.player.health = 13;
    w.time = 12345;
    w.set(-17, 90, 33, Block.DIAMOND_BLOCK, 2);
    var pos = new World.Pos(0, 65, -2);
    w.set(pos.x(), pos.y(), pos.z(), Block.FURNACE, 0);
    var t = w.tile(pos);
    t.inventory.set(0, new Stack(15, 5));
    t.burn = 120;
    t.cook = 99;
    w.save();
    World read = new World(store, save);
    assertEquals(12.25, read.player.x);
    assertEquals(13, read.player.health);
    assertEquals(12345, read.time);
    assertEquals(new Stack(278, 1, 50), read.player.inventory.get(5));
    assertEquals(Block.DIAMOND_BLOCK, read.block(-17, 90, 33));
    assertEquals(2, read.data(-17, 90, 33));
    assertEquals(99, read.tile(pos).cook);
    assertEquals(5, read.tile(pos).inventory.get(0).count());
    assertEquals(1, read.tile(new World.Pos(2, 65, -2)).inventory.count(327));
  }

  @Test
  void renameKeepsIdentityAndDeleteRemovesOnlySelectedSave() throws Exception {
    WorldStore store = new WorldStore(dir);
    var a = store.create("../../outside", 1, Generator.Type.DEFAULT, false);
    var b = store.create("B", 2, Generator.Type.SKYBLOCK, true);
    var renamed = store.rename(a, "A");
    assertEquals(a.id(), renamed.id());
    store.delete(a);
    assertEquals(1, store.list().size());
    assertEquals(b.id(), store.list().getFirst().id());
    assertFalse(Files.exists(dir.getParent().resolve("outside")));
  }

  @Test
  void corruptChunkFailsWithoutSilentlyReplacingUserData() throws Exception {
    WorldStore store = new WorldStore(dir);
    var save = store.create("A", 1, Generator.Type.DEFAULT, false);
    World w = new World(store, save);
    w.chunk(0, 0);
    w.save();
    Path file = dir.resolve("saves").resolve(save.id().toString()).resolve("chunks/0_0.oc");
    Files.writeString(file, "corrupt");
    assertThrows(java.io.IOException.class, () -> store.loadChunk(save, 0, 0));
    assertEquals("corrupt", Files.readString(file));
  }

  @Test
  void optionsPersistAndClampMalformedValues() throws Exception {
    Options options = new Options(dir);
    options.keys.put("Forward", 82);
    options.music = .3f;
    options.save();
    Options read = new Options(dir);
    assertEquals(82, read.keys.get("Forward"));
    assertEquals(.3f, read.music);
    Files.writeString(dir.resolve("options.properties"), "fov=NaN\nrenderDistance=999\n");
    read = new Options(dir);
    assertEquals(70, read.fov);
    assertEquals(3, read.renderDistance);
  }

  @Test
  void headlessSmokeExercisesInstalledApplication() throws Exception {
    HeadlessSmoke.run(dir);
  }
}
