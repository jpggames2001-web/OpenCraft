package dev.opencraft;

import static org.junit.jupiter.api.Assertions.*;

import dev.opencraft.game.*;
import dev.opencraft.world.*;
import org.junit.jupiter.api.Test;

class WorldTest {
  @Test
  void seedIsDeterministicAtNegativeCoordinates() {
    Generator a = new Generator(42, Generator.Type.DEFAULT),
        b = new Generator(42, Generator.Type.DEFAULT);
    assertArrayEquals(a.generate(-2, 3).blocks, b.generate(-2, 3).blocks);
    assertArrayEquals(a.generate(-2, 3).biomes, b.generate(-2, 3).biomes);
    assertFalse(
        java.util.Arrays.equals(
            a.generate(0, 0).blocks,
            new Generator(43, Generator.Type.DEFAULT).generate(0, 0).blocks));
  }

  @Test
  void chunkIndexCoversEntireVolumeWithoutAliases() {
    boolean[] seen = new boolean[32768];
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 0; y < 128; y++) {
          int i = Chunk.index(x, y, z);
          assertFalse(seen[i]);
          seen[i] = true;
        }
  }

  @Test
  void skyblockHasVoidAndStarterChest() {
    World w = new World(new Generator(10, Generator.Type.SKYBLOCK));
    assertEquals(Block.GRASS, w.block(0, 64, -1));
    assertEquals(Block.CHEST, w.block(2, 65, -2));
    assertEquals(Block.AIR, w.block(4, 64, 4));
    assertEquals(Block.AIR, w.block(-400, 80, 400));
    var inv = w.tile(new World.Pos(2, 65, -2)).inventory;
    assertEquals(1, inv.count(327));
    assertEquals(2, inv.count(79));
  }

  @Test
  void worldAddressesNegativeBlockCoordinates() {
    World w = new World(new Generator(1, Generator.Type.SKYBLOCK));
    w.set(-17, 127, -33, Block.DIAMOND_BLOCK, 0);
    assertEquals(Block.DIAMOND_BLOCK, w.chunk(-2, -3).block(15, 127, 15));
    assertEquals(Block.AIR, w.block(-17, 128, -33));
  }

  @Test
  void defaultHasBedrockAndMultipleBiomes() {
    Generator g = new Generator(42, Generator.Type.DEFAULT);
    Chunk generated = g.generate(0, 0);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) assertEquals(Block.BEDROCK, generated.block(x, 0, z));
    var biomes = new java.util.HashSet<Biome>();
    for (int x = -2000; x <= 2000; x += 100)
      for (int z = -2000; z <= 2000; z += 100) biomes.add(g.biome(x, z));
    assertTrue(biomes.size() >= 5);
  }

  @Test
  void sunlightAndTorchPropagateAndOpaqueBlocksStopThem() {
    World w = new World(new Generator(1, Generator.Type.SKYBLOCK));
    Chunk c = w.chunk(3, 3);
    w.set(50, 50, 50, Block.TORCH, 0);
    w.set(51, 50, 50, Block.STONE, 0);
    Lighting.update(c, w);
    assertEquals(15, w.light(50, 127, 50, true));
    assertEquals(14, w.light(50, 50, 50, false));
    assertEquals(0, w.light(51, 50, 50, false));
    assertEquals(13, w.light(50, 50, 51, false));
  }

  @Test
  void roofCreatesShadowButReceivesIndirectSkylight() {
    World w = new World(new Generator(1, Generator.Type.SKYBLOCK));
    Chunk c = w.chunk(3, 3);
    w.set(50, 70, 50, Block.STONE, 0);
    Lighting.update(c, w);
    assertEquals(0, w.light(50, 70, 50, true));
    assertEquals(14, w.light(50, 69, 50, true));
  }

  @Test
  void removingTorchRemovesItsLocalLight() {
    World w = new World(new Generator(1, Generator.Type.SKYBLOCK));
    Chunk c = w.chunk(3, 3);
    w.set(50, 50, 50, Block.TORCH, 0);
    Lighting.update(c, w);
    w.set(50, 50, 50, Block.AIR, 0);
    Lighting.update(c, w);
    assertEquals(0, w.light(50, 50, 51, false));
  }

  @Test
  void waterAndLavaProduceObsidian() {
    World w = new World(new Generator(1, Generator.Type.SKYBLOCK));
    w.set(10, 64, 10, Block.STONE, 0);
    w.set(10, 65, 10, Block.LAVA, 0);
    w.set(11, 65, 10, Block.WATER, 0);
    for (int i = 0; i < 5; i++) w.tick();
    assertEquals(Block.OBSIDIAN, w.block(10, 65, 10));
  }

  @Test
  void sandFalls() {
    World w = new World(new Generator(1, Generator.Type.SKYBLOCK));
    w.set(0, 67, -1, Block.SAND, 0);
    for (int i = 0; i < 15; i++) w.tick();
    assertEquals(Block.SAND, w.block(0, 65, -1));
  }

  @Test
  void furnaceConsumesFuelAndProducesItemsAfter200Ticks() {
    World w = new World(new Generator(1, Generator.Type.SKYBLOCK));
    var p = new World.Pos(0, 65, -2);
    w.set(p.x(), p.y(), p.z(), Block.FURNACE, 0);
    var tile = w.tile(p);
    tile.inventory.set(0, new dev.opencraft.game.Stack(15, 2));
    tile.inventory.set(1, new dev.opencraft.game.Stack(263, 1));
    for (int i = 0; i < 200; i++) w.tick();
    assertEquals(265, tile.inventory.get(2).id());
    assertEquals(1, tile.inventory.get(2).count());
    assertEquals(1, tile.inventory.get(0).count());
    assertTrue(tile.inventory.get(1).empty());
    assertEquals(Block.FURNACE_LIT, w.block(p.x(), p.y(), p.z()));
  }

  @Test
  void raycastReturnsFaceAndAdjacentPlacementCoordinate() {
    World w = new World(new Generator(1, Generator.Type.SKYBLOCK));
    w.set(8, 70, 8, Block.STONE, 0);
    World.Hit hit = w.raycast(8.5, 70.5, 12, 0, 0, -1, 5);
    assertNotNull(hit);
    assertEquals(new World.Pos(8, 70, 8), hit.pos());
    assertEquals(1, hit.nz());
    assertEquals(new World.Pos(8, 70, 9), hit.adjacent());
    assertEquals(3, hit.distance(), 1e-9);
  }

  @Test
  void remoteWorldDoesNotGenerateAnIsland() {
    World w = new World(new Generator(1, Generator.Type.SKYBLOCK));
    w.remote = true;
    assertEquals(Block.AIR, w.block(0, 64, 0));
  }
}
