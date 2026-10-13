package dev.opencraft.world;

import static dev.opencraft.world.Block.*;

import java.util.Random;

public final class Generator {
  public enum Type {
    DEFAULT,
    SKYBLOCK;

    public String label() {
      return this == DEFAULT ? "Default" : "SkyBlock";
    }
  }

  private final long seed;
  private final Type type;
  private final Noise relief, detail, climate, caves;

  public Generator(long seed, Type type) {
    this.seed = seed;
    this.type = type;
    relief = new Noise(seed);
    detail = new Noise(seed + 1);
    climate = new Noise(seed + 2);
    caves = new Noise(seed + 3);
  }

  public Biome biome(int x, int z) {
    if (type == Type.SKYBLOCK) return Biome.PLAINS;
    double land = relief.octave(x / 450., 0, z / 450., 4),
        t = climate.octave(x / 320., 0, z / 320., 3),
        wet = climate.octave(x / 260., 50, z / 260., 3);
    if (land < -.23) return t < -.3 ? Biome.FROZEN_OCEAN : Biome.OCEAN;
    if (Math.abs(detail.at(x / 230., 0, z / 230.)) < .022)
      return t < -.3 ? Biome.FROZEN_RIVER : Biome.RIVER;
    if (t < -.28) return land > .17 ? Biome.ICE_MOUNTAINS : Biome.ICE_PLAINS;
    if (t < -.12) return Biome.TAIGA;
    if (t > .2 && wet < .05) return Biome.DESERT;
    if (land > .22) return Biome.EXTREME_HILLS;
    if (wet > .23) return Biome.SWAMPLAND;
    if (wet > .02) return Biome.FOREST;
    return Biome.PLAINS;
  }

  public int surface(int x, int z) {
    Biome b = biome(x, z);
    double n = relief.octave(x / 160., 0, z / 160., 5),
        fine = detail.octave(x / 48., 0, z / 48., 3);
    double base =
        switch (b) {
          case OCEAN, FROZEN_OCEAN -> 47;
          case RIVER, FROZEN_RIVER -> 58;
          case EXTREME_HILLS, ICE_MOUNTAINS -> 80;
          case SWAMPLAND -> 62;
          default -> 66;
        };
    double scale = b == Biome.EXTREME_HILLS || b == Biome.ICE_MOUNTAINS ? 42 : 15;
    return Math.max(8, Math.min(116, (int) (base + n * scale + fine * 5)));
  }

  public Chunk generate(int cx, int cz) {
    Chunk c = new Chunk(cx, cz);
    if (type == Type.SKYBLOCK) {
      skyblock(c);
      return c;
    }
    Random r = new Random(seed + cx * 341873128712L + cz * 132897987541L);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        int wx = cx * 16 + x, wz = cz * 16 + z, h = surface(wx, wz);
        Biome b = biome(wx, wz);
        c.biomes[x + z * 16] = b;
        for (int y = 0; y < 128; y++) {
          Block v = AIR;
          if (y <= r.nextInt(5)) v = BEDROCK;
          else if (y < h - 4) v = STONE;
          else if (y < h) v = b == Biome.DESERT || h < 63 ? SAND : DIRT;
          else if (y == h) v = b == Biome.DESERT || h < 63 ? SAND : GRASS;
          else if (y <= 62) v = b.snowy() && y == 62 ? ICE : WATER;
          if (y > 5 && y < h - 4 && Math.abs(caves.at(wx / 28., y / 20., wz / 28.)) > .64)
            v = y < 10 ? LAVA : AIR;
          c.blocks[Chunk.index(x, y, z)] = (byte) v.id;
        }
        if (b.snowy() && h >= 62) c.put(x, h + 1, z, SNOW, 0);
      }
    ore(c, r, COAL_ORE, 20, 16, 128);
    ore(c, r, IRON_ORE, 20, 8, 64);
    ore(c, r, GOLD_ORE, 2, 8, 32);
    ore(c, r, REDSTONE_ORE, 8, 7, 16);
    ore(c, r, DIAMOND_ORE, 1, 7, 16);
    ore(c, r, LAPIS_ORE, 1, 6, 32);
    // Evaluate tree centers beyond each boundary so adjacent chunks reproduce the same canopy.
    for (int wx = cx * 16 - 3; wx < cx * 16 + 19; wx++)
      for (int wz = cz * 16 - 3; wz < cz * 16 + 19; wz++) {
        Random tr = new Random(seed ^ (wx * 73428767L) ^ (wz * 912931L));
        Biome b = biome(wx, wz);
        int chance =
            b == Biome.FOREST || b == Biome.TAIGA
                ? 48
                : b == Biome.PLAINS ? 320 : b == Biome.SWAMPLAND ? 90 : 1000000;
        int h = surface(wx, wz);
        if (tr.nextInt(chance) == 0 && h > 62 && h < 110)
          tree(c, wx, h + 1, wz, 4 + tr.nextInt(3), b == Biome.TAIGA);
        if (wx >= cx * 16 && wx < cx * 16 + 16 && wz >= cz * 16 && wz < cz * 16 + 16 && h >= 63) {
          int px = wx & 15, pz = wz & 15;
          if (b == Biome.DESERT && tr.nextInt(120) == 0) {
            for (int y = 1; y <= 3; y++) c.put(px, h + y, pz, CACTUS, 0);
          } else if (c.block(px, h + 1, pz) == AIR && tr.nextInt(24) == 0)
            c.put(
                px,
                h + 1,
                pz,
                tr.nextBoolean() ? TALL_GRASS : tr.nextBoolean() ? ROSE : DANDELION,
                0);
        }
      }
    return c;
  }

  private static void ore(Chunk c, Random r, Block b, int count, int size, int maxY) {
    for (int n = 0; n < count; n++) {
      int x = r.nextInt(16), y = r.nextInt(maxY), z = r.nextInt(16);
      for (int k = 0; k < size; k++) {
        if (c.block(x, y, z) == STONE) c.put(x, y, z, b, 0);
        x = Math.max(0, Math.min(15, x + r.nextInt(3) - 1));
        y = Math.max(1, Math.min(127, y + r.nextInt(3) - 1));
        z = Math.max(0, Math.min(15, z + r.nextInt(3) - 1));
      }
    }
  }

  private static void place(Chunk c, int wx, int y, int wz, Block b) {
    if (wx >> 4 == c.x && wz >> 4 == c.z) c.put(wx & 15, y, wz & 15, b, 0);
  }

  private static void tree(Chunk c, int x, int y, int z, int h, boolean pine) {
    for (int dy = h - 3; dy <= h; dy++) {
      int radius = pine ? (h - dy) / 2 + 1 : dy == h ? 1 : 2;
      for (int dx = -radius; dx <= radius; dx++)
        for (int dz = -radius; dz <= radius; dz++)
          if (Math.abs(dx) != radius || Math.abs(dz) != radius || dy < h - 1)
            place(c, x + dx, y + dy, z + dz, LEAVES);
    }
    for (int dy = 0; dy < h; dy++) place(c, x, y + dy, z, LOG);
  }

  private static void skyblock(Chunk c) {
    for (int wx = -3; wx <= 3; wx++)
      for (int wz = -3; wz <= 3; wz++)
        if (wx <= 0 || wz <= 0) {
          place(c, wx, 62, wz, DIRT);
          place(c, wx, 63, wz, DIRT);
          place(c, wx, 64, wz, GRASS);
        }
    tree(c, -2, 65, -2, 5, false);
    place(c, 2, 65, -2, CHEST);
  }

  public long seed() {
    return seed;
  }

  public Type type() {
    return type;
  }
}
