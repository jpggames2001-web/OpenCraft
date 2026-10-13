package dev.opencraft.render;

import dev.opencraft.world.*;
import java.util.Arrays;

public final class Mesher {
  public record Result(float[] opaque, float[] transparent) {}

  public static final class Floats {
    private float[] values = new float[8192];
    private int size;

    public void add(float... n) {
      if (size + n.length > values.length)
        values = Arrays.copyOf(values, Math.max(values.length * 2, size + n.length));
      System.arraycopy(n, 0, values, size, n.length);
      size += n.length;
    }

    public float[] array() {
      return Arrays.copyOf(values, size);
    }
  }

  private static final int[][] NORMAL = {
    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
  };
  private static final float[][][] FACE = {
    {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
    {{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},
    {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
    {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
    {{1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},
    {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}}
  };
  private static final float[] SHADE = {.6f, .6f, 1, .5f, .8f, .8f};

  public static Result build(World w, Chunk c, boolean smooth, boolean fancy) {
    Floats solid = new Floats(), alpha = new Floats();
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++)
        for (int y = 0; y < 128; y++) {
          Block b = c.block(x, y, z);
          if (b == Block.AIR) continue;
          int wx = c.x * 16 + x, wz = c.z * 16 + z, meta = c.data[Chunk.index(x, y, z)] & 15;
          Floats out =
              b.fluid() || b == Block.GLASS || b == Block.GLASS_PANE || b == Block.ICE
                  ? alpha
                  : solid;
          if (b.plant() || b == Block.PORTAL) {
            cross(out, wx, y, wz, b.id, 1, 1, 1, 1, Math.max(.2f, b.emission / 15f));
            continue;
          }
          if ((b == Block.WOOD_DOOR
                  || b == Block.IRON_DOOR
                  || b == Block.FENCE_GATE
                  || b == Block.TRAPDOOR)
              && (meta & 4) != 0) continue;
          float height = (float) Math.min(1, b.height());
          if (b.fluid()) height = meta >= 8 ? 1 : .875f - (meta & 7) * .09f;
          for (int f = 0; f < 6; f++) {
            int[] n = NORMAL[f];
            Block neighbor = w.peek(wx + n[0], y + n[1], wz + n[2]);
            if (neighbor == b && (b.transparent() || b.fluid())) continue;
            if (neighbor.solid
                && (!neighbor.transparent() || neighbor == Block.LEAVES && !fancy)
                && neighbor.fullCube()) continue;
            int tint = 0xffffff;
            if (b == Block.LEAVES) tint = c.biomes[x + z * 16].foliage;
            else if (b == Block.GRASS && f == 2) tint = c.biomes[x + z * 16].grass;
            float sky = w.light(wx + n[0], y + n[1], wz + n[2], true) / 15f,
                light =
                    Math.max(
                        b.emission / 15f, w.light(wx + n[0], y + n[1], wz + n[2], false) / 15f);
            face(
                out,
                wx,
                y,
                wz,
                1,
                height,
                1,
                f,
                b.texture(f),
                ((tint >> 16) & 255) / 255f * SHADE[f],
                ((tint >> 8) & 255) / 255f * SHADE[f],
                (tint & 255) / 255f * SHADE[f],
                sky,
                light,
                smooth ? w : null);
          }
        }
    return new Result(solid.array(), alpha.array());
  }

  public static void cube(
      Floats out,
      float x,
      float y,
      float z,
      float sx,
      float sy,
      float sz,
      int texture,
      float r,
      float g,
      float b,
      float sky,
      float light) {
    for (int f = 0; f < 6; f++)
      face(
          out,
          x,
          y,
          z,
          sx,
          sy,
          sz,
          f,
          texture,
          r * SHADE[f],
          g * SHADE[f],
          b * SHADE[f],
          sky,
          light,
          null);
  }

  public static void item(Floats out, int id) {
    if (id < 256) {
      Block b = Block.of(id);
      for (int f = 0; f < 6; f++)
        face(
            out, 0, 0, 0, .3f, .3f, .3f, f, b.texture(f), SHADE[f], SHADE[f], SHADE[f], 1, 0, null);
    } else face(out, 0, 0, 0, .4f, .4f, 0, 4, id, 1, 1, 1, 1, 0, null);
  }

  public static void billboard(
      Floats out,
      float x,
      float y,
      float z,
      float width,
      float height,
      int texture,
      float brightness) {
    face(out, x, y, z, width, height, 0, 4, texture, 1, 1, 1, 0, brightness, null);
  }

  private static void face(
      Floats out,
      float x,
      float y,
      float z,
      float sx,
      float sy,
      float sz,
      int f,
      int tex,
      float r,
      float g,
      float b,
      float sky,
      float light,
      World ao) {
    int[] indices = {0, 1, 2, 0, 2, 3};
    float u = (tex % 32) / 32f, v = (tex / 32) / 32f, edge = 1 / 32f;
    float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
    for (int index : indices) {
      float[] p = FACE[f][index];
      float shade = 1;
      if (ao != null) {
        int nx = (int) Math.floor(x + p[0] + NORMAL[f][0] * .01),
            ny = (int) Math.floor(y + p[1] + NORMAL[f][1] * .01),
            nz = (int) Math.floor(z + p[2] + NORMAL[f][2] * .01);
        int count = 0;
        for (int[] d : NORMAL)
          if (ao.peek(nx + d[0], ny + d[1], nz + d[2]).opacity() == 15) count++;
        shade = Math.max(.55f, 1 - count * .08f);
      }
      out.add(
          x + p[0] * sx,
          y + p[1] * sy,
          z + p[2] * sz,
          u + .0002f + uv[index][0] * (edge - .0004f),
          v + .0002f + uv[index][1] * (edge - .0004f),
          r * shade,
          g * shade,
          b * shade,
          sky,
          light);
    }
  }

  private static void cross(
      Floats out,
      float x,
      float y,
      float z,
      int tex,
      float r,
      float g,
      float b,
      float sky,
      float light) {
    float[][][] quads = {
      {{0, 0, 0}, {0, 1, 0}, {1, 1, 1}, {1, 0, 1}}, {{1, 0, 0}, {1, 1, 0}, {0, 1, 1}, {0, 0, 1}}
    };
    for (float[][] q : quads)
      for (int i : new int[] {0, 1, 2, 0, 2, 3, 2, 1, 0, 3, 2, 0}) {
        float[] p = q[i];
        float u = (tex % 32 + (i >= 2 ? 1 : 0)) / 32f,
            v = (tex / 32 + (i == 0 || i == 3 ? 1 : 0)) / 32f;
        out.add(x + p[0], y + p[1], z + p[2], u, v, r, g, b, sky, light);
      }
  }
}
