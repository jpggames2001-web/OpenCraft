package dev.opencraft.world;

import dev.opencraft.game.*;
import dev.opencraft.game.Stack;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Versioned, bounded, compressed persistence. Writes replace files atomically when supported. */
public final class WorldStore {
  public record Save(
      UUID id, String name, long seed, Generator.Type type, boolean creative, long played) {}

  private final Path root;

  public WorldStore(Path data) throws IOException {
    root = data.resolve("saves").toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  private Path directory(Save s) {
    return root.resolve(s.id().toString());
  }

  public List<Save> list() throws IOException {
    List<Save> result = new ArrayList<>();
    try (var paths = Files.list(root)) {
      for (Path p : paths.filter(Files::isDirectory).toList()) {
        Path f = p.resolve("level.properties");
        if (!Files.isRegularFile(f)) continue;
        try (InputStream in = Files.newInputStream(f)) {
          Properties props = new Properties();
          props.load(in);
          result.add(
              new Save(
                  UUID.fromString(p.getFileName().toString()),
                  props.getProperty("name", "World"),
                  Long.parseLong(props.getProperty("seed")),
                  Generator.Type.valueOf(props.getProperty("type")),
                  Boolean.parseBoolean(props.getProperty("creative")),
                  Long.parseLong(props.getProperty("played", "0"))));
        } catch (IllegalArgumentException ex) {
          System.err.println("Ignoring malformed save metadata: " + p.getFileName());
        }
      }
    }
    result.sort(Comparator.comparingLong(Save::played).reversed());
    return result;
  }

  public Save create(String name, long seed, Generator.Type type, boolean creative)
      throws IOException {
    Save s =
        new Save(
            UUID.randomUUID(),
            name.isBlank() ? "New World" : name.strip(),
            seed,
            type,
            creative,
            System.currentTimeMillis());
    Files.createDirectories(directory(s).resolve("chunks"));
    metadata(s);
    return s;
  }

  private void metadata(Save s) throws IOException {
    Properties p = new Properties();
    p.setProperty("name", s.name);
    p.setProperty("seed", Long.toString(s.seed));
    p.setProperty("type", s.type.name());
    p.setProperty("creative", Boolean.toString(s.creative));
    p.setProperty("played", Long.toString(s.played));
    atomic(directory(s).resolve("level.properties"), out -> p.store(out, "OpenCraft world"));
  }

  public Save rename(Save s, String name) throws IOException {
    if (name.isBlank()) throw new IOException("World name cannot be empty");
    Save renamed = new Save(s.id, name.strip(), s.seed, s.type, s.creative, s.played);
    metadata(renamed);
    return renamed;
  }

  public void delete(Save s) throws IOException {
    Path dir = directory(s);
    if (!dir.getParent().equals(root) || Files.isSymbolicLink(dir))
      throw new IOException("Unsafe save path");
    if (!Files.exists(dir)) return;
    try (var files = Files.walk(dir)) {
      for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
    }
  }

  public Chunk loadChunk(Save s, int x, int z) throws IOException {
    Path file = directory(s).resolve("chunks").resolve(x + "_" + z + ".oc");
    if (!Files.exists(file)) return null;
    try (var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
      if (in.readInt() != 0x4f434348 || in.readInt() != 1)
        throw new IOException("Unsupported chunk format");
      if (in.readInt() != x || in.readInt() != z)
        throw new IOException("Chunk coordinate mismatch");
      Chunk c = new Chunk(x, z);
      in.readFully(c.blocks);
      in.readFully(c.data);
      for (int i = 0; i < 256; i++) {
        int b = in.readUnsignedByte();
        if (b >= Biome.values().length) throw new IOException("Invalid biome");
        c.biomes[i] = Biome.values()[b];
      }
      c.dirty = false;
      return c;
    }
  }

  public void save(World w) throws IOException {
    Path dir = directory(w.save);
    Files.createDirectories(dir.resolve("chunks"));
    for (Chunk c : w.chunks.values())
      if (c.dirty) {
        atomic(
            dir.resolve("chunks").resolve(c.x + "_" + c.z + ".oc"),
            out -> {
              var gzip = new GZIPOutputStream(out);
              var d = new DataOutputStream(gzip);
              d.writeInt(0x4f434348);
              d.writeInt(1);
              d.writeInt(c.x);
              d.writeInt(c.z);
              d.write(c.blocks);
              d.write(c.data);
              for (Biome b : c.biomes) d.writeByte(b.ordinal());
              d.flush();
              gzip.finish();
            });
        c.dirty = false;
      }
    atomic(
        dir.resolve("state.oc"),
        out -> {
          var gzip = new GZIPOutputStream(out);
          var d = new DataOutputStream(gzip);
          d.writeInt(0x4f435354);
          d.writeInt(1);
          d.writeLong(w.time);
          d.writeInt(w.spawnX);
          d.writeInt(w.spawnY);
          d.writeInt(w.spawnZ);
          Player p = w.player;
          d.writeDouble(p.x);
          d.writeDouble(p.y);
          d.writeDouble(p.z);
          d.writeDouble(p.yaw);
          d.writeDouble(p.pitch);
          d.writeInt(p.health);
          d.writeInt(p.food);
          d.writeFloat(p.saturation);
          d.writeInt(p.selected);
          d.writeInt(p.experience);
          d.writeBoolean(p.creative);
          p.inventory.write(d);
          p.armor.write(d);
          d.writeInt(w.tiles.size());
          for (var e : w.tiles.entrySet()) {
            World.Pos pos = e.getKey();
            World.Tile t = e.getValue();
            d.writeInt(pos.x());
            d.writeInt(pos.y());
            d.writeInt(pos.z());
            t.inventory.write(d);
            d.writeInt(t.burn);
            d.writeInt(t.cook);
            d.writeInt(t.totalBurn);
          }
          d.writeInt(w.drops.size());
          for (World.Drop drop : w.drops) {
            d.writeDouble(drop.x);
            d.writeDouble(drop.y);
            d.writeDouble(drop.z);
            d.writeInt(drop.stack.id());
            d.writeByte(drop.stack.count());
            d.writeInt(drop.stack.damage());
            d.writeInt(drop.age);
          }
          d.writeInt(w.mobs.size());
          for (World.Mob m : w.mobs) {
            d.writeByte(m.kind.ordinal());
            d.writeDouble(m.x);
            d.writeDouble(m.y);
            d.writeDouble(m.z);
            d.writeInt(m.health);
          }
          d.flush();
          gzip.finish();
        });
    metadata(
        new Save(
            w.save.id,
            w.save.name,
            w.save.seed,
            w.save.type,
            w.player.creative,
            System.currentTimeMillis()));
  }

  public boolean loadState(World w) throws IOException {
    Path file = directory(w.save).resolve("state.oc");
    if (!Files.exists(file)) {
      w.player.creative = w.save.creative;
      return false;
    }
    try (var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
      if (in.readInt() != 0x4f435354 || in.readInt() != 1)
        throw new IOException("Unsupported player state");
      w.time = in.readLong();
      w.spawnX = in.readInt();
      w.spawnY = in.readInt();
      w.spawnZ = in.readInt();
      Player p = w.player;
      p.x = in.readDouble();
      p.y = in.readDouble();
      p.z = in.readDouble();
      p.yaw = in.readDouble();
      p.pitch = in.readDouble();
      p.health = in.readInt();
      p.food = in.readInt();
      p.saturation = in.readFloat();
      p.selected = in.readInt();
      p.experience = in.readInt();
      p.creative = in.readBoolean();
      if (!Double.isFinite(p.x)
          || !Double.isFinite(p.y)
          || !Double.isFinite(p.z)
          || p.selected < 0
          || p.selected > 8) throw new IOException("Invalid player coordinates or slot");
      p.inventory = Inventory.read(in, 36);
      p.armor = Inventory.read(in, 4);
      int tiles = bounded(in.readInt(), 100000);
      for (int n = 0; n < tiles; n++) {
        var pos = new World.Pos(in.readInt(), in.readInt(), in.readInt());
        int size = in.readInt();
        if (size != 3 && size != 27) throw new IOException("Invalid tile size");
        World.Tile t = new World.Tile(size);
        for (int i = 0; i < size; i++)
          t.inventory.set(i, new Stack(in.readInt(), in.readUnsignedByte(), in.readInt()));
        t.burn = in.readInt();
        t.cook = in.readInt();
        t.totalBurn = in.readInt();
        w.tiles.put(pos, t);
      }
      int drops = bounded(in.readInt(), 100000);
      for (int n = 0; n < drops; n++) {
        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
        World.Drop d =
            new World.Drop(x, y, z, new Stack(in.readInt(), in.readUnsignedByte(), in.readInt()));
        d.age = in.readInt();
        w.drops.add(d);
      }
      int mobs = bounded(in.readInt(), 10000);
      for (int n = 0; n < mobs; n++) {
        int kind = in.readUnsignedByte();
        if (kind >= World.Mob.Kind.values().length) throw new IOException("Invalid entity type");
        World.Mob m =
            new World.Mob(
                World.Mob.Kind.values()[kind], in.readDouble(), in.readDouble(), in.readDouble());
        m.health = in.readInt();
        w.mobs.add(m);
      }
      return true;
    }
  }

  private static int bounded(int n, int max) throws IOException {
    if (n < 0 || n > max) throw new IOException("Corrupt object count");
    return n;
  }

  @FunctionalInterface
  private interface Writer {
    void write(OutputStream out) throws IOException;
  }

  private static void atomic(Path path, Writer writer) throws IOException {
    Files.createDirectories(path.getParent());
    Path tmp = Files.createTempFile(path.getParent(), ".write-", ".tmp");
    try {
      try (OutputStream out = Files.newOutputStream(tmp)) {
        writer.write(out);
      }
      try {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tmp);
    }
  }
}
