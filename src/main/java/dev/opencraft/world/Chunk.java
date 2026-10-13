package dev.opencraft.world;

public final class Chunk {
  public static final int WIDTH = 16, HEIGHT = 128, VOLUME = 32768;
  public final int x, z;
  public final byte[] blocks = new byte[VOLUME],
      data = new byte[VOLUME],
      sky = new byte[VOLUME],
      light = new byte[VOLUME];
  public final Biome[] biomes = new Biome[256];
  public boolean dirty = true, meshDirty = true, lightingDirty = true;
  public long revision;

  public Chunk(int x, int z) {
    this.x = x;
    this.z = z;
    java.util.Arrays.fill(biomes, Biome.PLAINS);
  }

  public static int index(int x, int y, int z) {
    return (x << 11) | (z << 7) | y;
  }

  public Block block(int x, int y, int z) {
    return y < 0 || y >= HEIGHT ? Block.AIR : Block.of(blocks[index(x & 15, y, z & 15)]);
  }

  public void put(int x, int y, int z, Block b, int meta) {
    if (y < 0 || y >= HEIGHT) return;
    int i = index(x & 15, y, z & 15);
    blocks[i] = (byte) b.id;
    data[i] = (byte) meta;
    dirty = meshDirty = lightingDirty = true;
    revision++;
  }

  public int height(int x, int z) {
    for (int y = 127; y >= 0; y--) if (block(x, y, z).solid) return y + 1;
    return 0;
  }

  public static long key(int x, int z) {
    return ((long) x << 32) | (z & 0xffffffffL);
  }
}
