package dev.opencraft.world;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class Lighting {
  private static final int[][] DIR = {
    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
  };

  private Lighting() {}

  public static void update(Chunk c, World world) {
    Arrays.fill(c.sky, (byte) 0);
    Arrays.fill(c.light, (byte) 0);
    ArrayDeque<Integer> skyQueue = new ArrayDeque<>(), blockQueue = new ArrayDeque<>();
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) {
        int sunlight = 15;
        for (int y = 127; y >= 0; y--) {
          int i = Chunk.index(x, y, z);
          Block b = c.block(x, y, z);
          int opacity = b.opacity();
          if (opacity > 0) sunlight = Math.max(0, sunlight - opacity);
          c.sky[i] = (byte) sunlight;
          c.light[i] = (byte) b.emission;
          if (sunlight > 0) skyQueue.add(i);
          if (b.emission > 0) blockQueue.add(i);
        }
      }
    // Existing neighboring borders seed cross-chunk propagation without generating terrain.
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        if (x == 0 || x == 15 || z == 0 || z == 15)
          for (int y = 0; y < 128; y++) {
            int i = Chunk.index(x, y, z), loss = Math.max(1, c.block(x, y, z).opacity());
            for (int[] d : DIR)
              if (x + d[0] < 0 || x + d[0] >= 16 || z + d[2] < 0 || z + d[2] >= 16) {
                int wx = c.x * 16 + x + d[0], wz = c.z * 16 + z + d[2];
                int s = world.light(wx, y, wz, true) - loss,
                    l = world.light(wx, y, wz, false) - loss;
                if (s > c.sky[i]) {
                  c.sky[i] = (byte) s;
                  skyQueue.add(i);
                }
                if (l > c.light[i]) {
                  c.light[i] = (byte) l;
                  blockQueue.add(i);
                }
              }
          }
    flood(c, c.sky, skyQueue);
    flood(c, c.light, blockQueue);
    c.lightingDirty = false;
    c.meshDirty = true;
  }

  private static void flood(Chunk c, byte[] levels, ArrayDeque<Integer> q) {
    while (!q.isEmpty()) {
      int i = q.removeFirst(), x = i >> 11, z = (i >> 7) & 15, y = i & 127, value = levels[i];
      if (value < 2) continue;
      for (int[] d : DIR) {
        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
        if (nx < 0 || nx >= 16 || ny < 0 || ny >= 128 || nz < 0 || nz >= 16) continue;
        int ni = Chunk.index(nx, ny, nz), v = value - Math.max(1, c.block(nx, ny, nz).opacity());
        if (v > levels[ni]) {
          levels[ni] = (byte) v;
          q.addLast(ni);
        }
      }
    }
  }
}
