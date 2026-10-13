package dev.opencraft.render;

import static org.lwjgl.opengl.GL33C.*;

import dev.opencraft.game.Items;
import dev.opencraft.world.Block;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;

public final class Assets implements AutoCloseable {
  public static final int TILE = 16, COLS = 32, SIZE = 512;
  private final int texture;
  private BufferedImage atlas;

  public Assets() {
    texture = glGenTextures();
    reload(null);
  }

  public int texture() {
    return texture;
  }

  public void reload(Path pack) {
    BufferedImage generated = generate();
    if (pack != null)
      try (ZipFile zip = new ZipFile(pack.toFile())) {
        BufferedImage terrain = read(zip, "terrain.png");
        if (terrain != null && terrain.getWidth() >= 256 && terrain.getHeight() >= 256) {
          int scale = terrain.getWidth() / 16;
          for (Block b : Block.values()) {
            int classic = classic(b.id);
            if (classic >= 0)
              copyTile(
                  terrain, (classic % 16) * scale, (classic / 16) * scale, scale, generated, b.id);
          }
          copyTile(terrain, 0, 0, scale, generated, 200);
          copyTile(terrain, 3 * scale, 0, scale, generated, 201);
          copyTile(terrain, 5 * scale, scale, scale, generated, 202);
          copyTile(terrain, 11 * scale, 2 * scale, scale, generated, 203);
        }
        for (int id = 0; id < 512; id++) {
          BufferedImage custom = read(zip, "tiles/" + id + ".png");
          if (custom != null) copyTile(custom, 0, 0, custom.getWidth(), generated, id);
        }
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot load texture pack: " + e.getMessage(), e);
      }
    atlas = generated;
    ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
    for (int y = 0; y < SIZE; y++)
      for (int x = 0; x < SIZE; x++) {
        int c = atlas.getRGB(x, y);
        pixels.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >> 24));
      }
    pixels.flip();
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIZE, SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  }

  private static BufferedImage read(ZipFile zip, String name) throws IOException {
    ZipEntry e = zip.getEntry(name);
    if (e == null) return null;
    if (e.getSize() > 8_000_000) throw new IOException("Texture exceeds size limit");
    try (InputStream in = zip.getInputStream(e)) {
      byte[] bytes = in.readNBytes(8_000_001);
      if (bytes.length > 8_000_000) throw new IOException("Texture exceeds size limit");
      try (var imageIn = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(imageIn);
        if (!readers.hasNext()) return null;
        var reader = readers.next();
        try {
          reader.setInput(imageIn);
          if (reader.getWidth(0) > 2048 || reader.getHeight(0) > 2048)
            throw new IOException("Texture dimensions exceed limit");
          return reader.read(0);
        } finally {
          reader.dispose();
        }
      }
    }
  }

  private static int classic(int id) {
    return switch (id) {
      case 1 -> 1;
      case 2 -> 3;
      case 3 -> 2;
      case 4 -> 16;
      case 5 -> 4;
      case 6 -> 15;
      case 7 -> 17;
      case 8, 9 -> 205;
      case 10, 11 -> 237;
      case 12 -> 18;
      case 13 -> 19;
      case 14 -> 32;
      case 15 -> 33;
      case 16 -> 34;
      case 17 -> 20;
      case 18 -> 52;
      case 20 -> 49;
      case 21 -> 160;
      case 22 -> 144;
      case 24 -> 192;
      case 35 -> 64;
      case 37 -> 13;
      case 38 -> 12;
      case 39 -> 29;
      case 40 -> 28;
      case 41 -> 23;
      case 42 -> 22;
      case 45 -> 7;
      case 46 -> 8;
      case 47 -> 35;
      case 48 -> 36;
      case 49 -> 37;
      case 50 -> 80;
      case 54 -> 26;
      case 56 -> 50;
      case 57 -> 24;
      case 58 -> 59;
      case 61 -> 44;
      case 62 -> 45;
      case 73, 74 -> 51;
      case 79 -> 67;
      case 80 -> 66;
      case 81 -> 70;
      case 82 -> 72;
      case 86 -> 118;
      case 87 -> 103;
      case 88 -> 104;
      case 89 -> 105;
      case 98 -> 54;
      case 103 -> 136;
      case 112 -> 224;
      case 121 -> 175;
      default -> -1;
    };
  }

  private static void copyTile(
      BufferedImage src, int sx, int sy, int size, BufferedImage dst, int id) {
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 16; x++) {
        int px = Math.min(src.getWidth() - 1, sx + x * size / 16),
            py = Math.min(src.getHeight() - 1, sy + y * size / 16);
        if (px >= 0 && py >= 0)
          dst.setRGB((id % 32) * 16 + x, (id / 32) * 16 + y, src.getRGB(px, py));
      }
  }

  public static BufferedImage generate() {
    BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    for (int id = 1; id < 512; id++) {
      Block b = Block.of(id);
      int base = id < 256 ? b.color : Items.get(id).color();
      Random r = new Random(id * 49831L + 17);
      for (int y = 0; y < 16; y++)
        for (int x = 0; x < 16; x++) {
          int c = shade(base, r.nextInt(43) - 21), alpha = 255;
          if (id == 200 || id == 201) {
            c =
                shade(
                    id == 200 ? 0xaaaaaa : y < 3 ? 0x5c943b : Block.DIRT.color, r.nextInt(43) - 21);
          }
          if (b == Block.STONE
              || b == Block.COBBLESTONE
              || b == Block.MOSSY_COBBLESTONE
              || b == Block.BEDROCK) {
            if (b != Block.STONE && (x + ((y / 4) % 2) * 3) % 7 == 0
                || b != Block.STONE && y % 5 == 0) c = shade(base, -38);
          }
          if (b.name().endsWith("_ORE")) {
            c = shade(Block.STONE.color, r.nextInt(43) - 21);
            if (((x * 7 + y * 11 + id) % 23) < 4 && x > 1 && x < 14 && y > 1 && y < 14)
              c = shade(oreColor(b), r.nextInt(51) - 25);
          }
          if (b == Block.LOG) c = shade(base, ((x * 3) % 7) * 9 - 25);
          if (id == 202)
            c = shade(0xb38c51, (Math.max(Math.abs(x - 7), Math.abs(y - 7)) % 3) * 18 - 25);
          if (b == Block.PLANKS
              || b == Block.WOOD_STAIRS
              || b == Block.FENCE
              || b == Block.WOOD_DOOR) {
            if (y % 4 == 0 || x == (y / 4 % 2) * 8) c = shade(base, -35);
          }
          if (b == Block.BRICKS || b == Block.STONE_BRICKS) {
            if (y % 4 == 0 || (x + (y / 4 % 2) * 4) % 8 == 0) c = 0xffaaaa99;
          }
          if (b == Block.LEAVES) {
            c = shade(0xb8b8b8, r.nextInt(70) - 45);
            if (r.nextInt(7) == 0) alpha = 0;
          }
          if (b == Block.GLASS || b == Block.GLASS_PANE || b == Block.ICE) {
            alpha = b == Block.ICE ? 150 : 35;
            if (x == 0 || y == 0 || x == 15 || y == 15 || x == y && x < 6) {
              alpha = 210;
              c = 0xdcefee;
            }
          }
          if (b.fluid()) {
            alpha = b.lava() ? 255 : 175;
            c = shade(base, (int) (Math.sin(x * .8 + y * .6) * 18) + r.nextInt(15));
          }
          if (b == Block.CHEST) {
            c = shade(base, -8);
            if (x < 2 || x > 13 || y < 2 || y == 7 || y > 13) c = 0x604b22;
            if (x >= 7 && x <= 8 && y >= 6 && y <= 9) c = 0xd5ca99;
          }
          if (b == Block.CRAFTING_TABLE || id == 203) {
            c = shade(0xac814c, r.nextInt(25) - 12);
            if (x % 5 == 0 || y % 5 == 0) c = 0x48351f;
          }
          if (b == Block.FURNACE || b == Block.FURNACE_LIT) {
            if (x > 3 && x < 12 && (y > 2 && y < 6 || y > 8 && y < 13))
              c = b == Block.FURNACE_LIT && y > 8 ? 0xee8a20 : 0x222222;
          }
          if (b == Block.TNT) {
            if (y < 4 || y > 11) c = 0xdabd8d;
            else if (x % 5 == 0) c = 0x352b21;
          }
          if (b == Block.BOOKSHELF) {
            if (y % 7 == 0) c = 0x76572e;
            else c = new int[] {0xaa3333, 0x3f7197, 0xbbaa44, 0x437941}[(x / 2) % 4];
          }
          if (b.plant()) {
            alpha = 0;
            if (b == Block.TORCH || b == Block.REDSTONE_TORCH) {
              if (x >= 7 && x <= 8 && y >= 5) {
                alpha = 255;
                c = y < 8 ? b.emission == 14 ? 0xffdc63 : 0xff3333 : 0x9c743d;
              }
            } else if (x == 7 || x == 8 || Math.abs(x - 7) == (15 - y) / 2) {
              alpha = 255;
              c = b.color;
            }
          }
          if (id == 204 || id == 205 || id == 206) {
            alpha = 255;
            c = id == 204 ? 0xfff3b0 : id == 205 ? 0xe0e3de : 0xffffff;
            if (id != 206 && (x < 1 || x > 14 || y < 1 || y > 14)) alpha = 0;
            if (id == 205 && ((x * 3 + y * 7) % 11) < 2) c = 0xaeb8b7;
          }
          if (id >= 256) {
            alpha = 0;
            var item = Items.get(id);
            String tool = item.tool();
            if (!tool.isEmpty() && !tool.equals("armor")) {
              if (Math.abs(x - (15 - y)) <= 1 && y > 5) {
                alpha = 255;
                c = 0x936b3a;
              }
              if (y < 6 && x > 3 && x < 14) {
                alpha = 255;
                c = shade(base, r.nextInt(30) - 15);
              }
            } else if ((x - 7) * (x - 7) + (y - 7) * (y - 7) < 48) {
              alpha = 255;
              c = shade(base, r.nextInt(37) - 18);
              if (y < 5) c = shade(c, 20);
            }
          }
          if (id >= 325 && id <= 327 || id == 335) {
            alpha = 0;
            if (y >= 5 && y <= 13 && x >= 3 + (y - 5) / 5 && x <= 12 - (y - 5) / 5) {
              alpha = 255;
              c = x < 5 || x > 10 || y > 11 ? 0xaab0b3 : 0x575d60;
            }
            if (y >= 3 && y <= 5 && x >= 3 && x <= 12) {
              alpha = 255;
              c =
                  y == 3
                      ? 0xbcc3c7
                      : id == 326
                          ? 0x3f61c2
                          : id == 327 ? 0xf48223 : id == 335 ? 0xeeeeee : 0x50585b;
            }
          }
          out.setRGB((id % 32) * 16 + x, (id / 32) * 16 + y, (alpha << 24) | (c & 0xffffff));
        }
    }
    return out;
  }

  private static int oreColor(Block b) {
    return switch (b) {
      case COAL_ORE -> 0x272727;
      case IRON_ORE -> 0xc5a990;
      case GOLD_ORE -> 0xedcf48;
      case DIAMOND_ORE -> 0x55d9cf;
      case REDSTONE_ORE, REDSTONE_ORE_LIT -> 0xd23030;
      case LAPIS_ORE -> 0x3159be;
      default -> b.color;
    };
  }

  private static int shade(int c, int d) {
    return clamp(((c >> 16) & 255) + d) << 16
        | clamp(((c >> 8) & 255) + d) << 8
        | clamp((c & 255) + d);
  }

  private static int clamp(int n) {
    return Math.max(0, Math.min(255, n));
  }

  public void export(Path path) throws IOException {
    ImageIO.write(atlas, "png", path.toFile());
  }

  public void close() {
    glDeleteTextures(texture);
  }
}
