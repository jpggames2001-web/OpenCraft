package dev.opencraft.net;

import dev.opencraft.game.*;
import dev.opencraft.world.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.zip.InflaterInputStream;

/** Unencrypted protocol 22 (1.0). Obsolete online-mode authentication is not fabricated. */
public final class LegacyClient implements AutoCloseable {
  public record Address(String host, int port) {
    public static Address parse(String input) {
      String s = input.strip();
      if (s.isEmpty()) throw new IllegalArgumentException("Server address required");
      String host = s;
      int port = 25565;
      if (s.startsWith("[")) {
        int end = s.indexOf(']');
        if (end < 0) throw new IllegalArgumentException("Invalid IPv6 address");
        host = s.substring(1, end);
        if (s.length() > end + 1) {
          if (s.charAt(end + 1) != ':') throw new IllegalArgumentException("Invalid address");
          port = Integer.parseInt(s.substring(end + 2));
        }
      } else if (s.indexOf(':') == s.lastIndexOf(':') && s.contains(":")) {
        int i = s.indexOf(':');
        host = s.substring(0, i);
        port = Integer.parseInt(s.substring(i + 1));
      }
      if (host.isBlank() || port < 1 || port > 65535)
        throw new IllegalArgumentException("Invalid server address");
      return new Address(host, port);
    }
  }

  private final ConcurrentLinkedQueue<Runnable> mainEvents = new ConcurrentLinkedQueue<>();
  private final Consumer<World> joined;
  private final Consumer<String> messages;
  private final Consumer<String> disconnected;
  private volatile Socket socket;
  private volatile DataOutputStream out;
  private volatile boolean open = true;
  private World world;
  private int entityId;
  private short transaction;

  public LegacyClient(
      String address,
      String username,
      Consumer<World> joined,
      Consumer<String> messages,
      Consumer<String> disconnected) {
    this.joined = joined;
    this.messages = messages;
    this.disconnected = disconnected;
    Thread.ofVirtual().name("legacy-network").start(() -> run(address, username));
  }

  public void poll() {
    Runnable r;
    while ((r = mainEvents.poll()) != null) r.run();
  }

  private void run(String address, String username) {
    try {
      Address a = Address.parse(address);
      Socket s = new Socket();
      socket = s;
      s.connect(new InetSocketAddress(a.host, a.port), 5000);
      s.setSoTimeout(30000);
      s.setTcpNoDelay(true);
      out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
      var in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
      send(
          d -> {
            d.writeByte(2);
            writeString(d, username);
          });
      if (in.readUnsignedByte() != 2)
        throw new IOException("Server did not return a 1.0 handshake");
      String auth = readString(in, 256);
      if (!auth.equals("-"))
        throw new IOException(
            "This server requires retired Mojang 1.0 authentication. Use an offline-mode test"
                + " server; no credentials were sent.");
      send(
          d -> {
            d.writeByte(1);
            d.writeInt(22);
            writeString(d, username);
            d.writeLong(0);
            d.writeInt(0);
            d.writeByte(0);
            d.writeByte(0);
            d.writeByte(128);
            d.writeByte(0);
          });
      while (open) packet(in);
    } catch (Exception e) {
      if (open)
        mainEvents.add(
            () ->
                disconnected.accept(
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    } finally {
      close();
    }
  }

  public static String ping(String address) throws IOException {
    Address a = Address.parse(address);
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(a.host, a.port), 2000);
      socket.setSoTimeout(2000);
      var out = new DataOutputStream(socket.getOutputStream());
      out.writeByte(0xfe);
      out.flush();
      var in = new DataInputStream(socket.getInputStream());
      if (in.readUnsignedByte() != 0xff) throw new IOException("Not a legacy server");
      return readString(in, 256).replace('§', ' ');
    }
  }

  private void packet(DataInputStream in) throws IOException {
    int id = in.readUnsignedByte();
    switch (id) {
      case 0 -> {
        int keep = in.readInt();
        send(
            d -> {
              d.writeByte(0);
              d.writeInt(keep);
            });
      }
      case 1 -> {
        entityId = in.readInt();
        readString(in, 256);
        long seed = in.readLong();
        int mode = in.readInt();
        int dimension = in.readByte();
        int difficulty = in.readUnsignedByte();
        in.readUnsignedByte();
        in.readUnsignedByte();
        if (dimension != 0)
          throw new IOException("Only the overworld is currently supported by this client");
        World w = new World(new Generator(seed, Generator.Type.SKYBLOCK));
        w.remote = true;
        w.chunks.clear();
        w.tiles.clear();
        w.player.creative = mode == 1;
        w.difficulty = difficulty;
        world = w;
        mainEvents.add(() -> joined.accept(w));
      }
      case 3 -> {
        String text = readString(in, 32767);
        mainEvents.add(() -> messages.accept(text.replaceAll("§.", "")));
      }
      case 4 -> {
        long time = in.readLong();
        event(w -> w.time = time);
      }
      case 5 -> {
        in.readInt();
        in.readShort();
        in.readShort();
        in.readShort();
      }
      case 6 -> {
        int x = in.readInt(), y = in.readInt(), z = in.readInt();
        event(
            w -> {
              w.spawnX = x;
              w.spawnY = y;
              w.spawnZ = z;
            });
      }
      case 8 -> {
        int health = in.readShort(), food = in.readShort();
        float sat = in.readFloat();
        event(
            w -> {
              w.player.health = health;
              w.player.food = food;
              w.player.saturation = sat;
            });
      }
      case 9 -> {
        in.readByte();
        int difficulty = in.readUnsignedByte(), mode = in.readUnsignedByte();
        in.readShort();
        in.readLong();
        event(
            w -> {
              w.chunks.clear();
              w.tiles.clear();
              w.difficulty = difficulty;
              w.player.creative = mode == 1;
              w.player.health = 20;
            });
      }
      case 13 -> {
        double x = in.readDouble();
        in.readDouble();
        double y = in.readDouble(), z = in.readDouble();
        float yaw = in.readFloat(), pitch = in.readFloat();
        boolean ground = in.readBoolean();
        event(
            w -> {
              w.player.x = x;
              w.player.y = y;
              w.player.z = z;
              w.player.yaw = -yaw;
              w.player.pitch = pitch;
              w.player.onGround = ground;
              position(w.player);
            });
      }
      case 17 -> {
        in.readInt();
        in.readByte();
        in.readInt();
        in.readUnsignedByte();
        in.readInt();
      }
      case 18, 19 -> {
        in.readInt();
        in.readByte();
      }
      case 20 -> {
        in.readInt();
        readString(in, 16);
        in.readInt();
        in.readInt();
        in.readInt();
        in.readByte();
        in.readByte();
        in.readShort();
      }
      case 21 -> {
        in.readInt();
        int item = in.readShort(), count = in.readUnsignedByte(), damage = in.readShort();
        double x = in.readInt() / 32., y = in.readInt() / 32., z = in.readInt() / 32.;
        in.readByte();
        in.readByte();
        in.readByte();
        event(w -> w.drop(x, y, z, new Stack(item, count, damage)));
      }
      case 22 -> {
        in.readInt();
        in.readInt();
      }
      case 23 -> {
        in.readInt();
        in.readByte();
        in.readInt();
        in.readInt();
        in.readInt();
        if (in.readInt() > 0) {
          in.readShort();
          in.readShort();
          in.readShort();
        }
      }
      case 24 -> {
        in.readInt();
        in.readByte();
        in.readInt();
        in.readInt();
        in.readInt();
        in.readByte();
        in.readByte();
        metadata(in);
      }
      case 25 -> {
        in.readInt();
        readString(in, 256);
        in.readInt();
        in.readInt();
        in.readInt();
        in.readInt();
      }
      case 26 -> {
        in.readInt();
        in.readInt();
        in.readInt();
        in.readInt();
        in.readShort();
      }
      case 28 -> {
        in.readInt();
        in.readShort();
        in.readShort();
        in.readShort();
      }
      case 29, 30 -> in.readInt();
      case 31 -> {
        in.readInt();
        in.readByte();
        in.readByte();
        in.readByte();
      }
      case 32 -> {
        in.readInt();
        in.readByte();
        in.readByte();
      }
      case 33 -> {
        in.readInt();
        in.readByte();
        in.readByte();
        in.readByte();
        in.readByte();
        in.readByte();
      }
      case 34 -> {
        in.readInt();
        in.readInt();
        in.readInt();
        in.readInt();
        in.readByte();
        in.readByte();
      }
      case 38 -> {
        in.readInt();
        in.readByte();
      }
      case 39 -> {
        in.readInt();
        in.readInt();
      }
      case 40 -> {
        in.readInt();
        metadata(in);
      }
      case 41 -> {
        in.readInt();
        in.readByte();
        in.readByte();
        in.readShort();
      }
      case 42 -> {
        in.readInt();
        in.readByte();
      }
      case 43 -> {
        float xp = in.readFloat();
        int level = in.readShort();
        in.readShort();
        event(w -> w.player.experience = level);
      }
      case 50 -> {
        int x = in.readInt(), z = in.readInt();
        boolean load = in.readBoolean();
        event(
            w -> {
              if (load) w.chunk(x, z);
              else w.chunks.remove(Chunk.key(x, z));
            });
      }
      case 51 -> chunkPacket(in);
      case 52 -> {
        int cx = in.readInt(), cz = in.readInt(), count = in.readUnsignedShort();
        if (count > 32768) throw new IOException("Invalid block batch");
        int[] coordinates = new int[count];
        for (int i = 0; i < count; i++) coordinates[i] = in.readUnsignedShort();
        byte[] types = new byte[count], data = new byte[count];
        in.readFully(types);
        in.readFully(data);
        event(
            w -> {
              for (int i = 0; i < count; i++) {
                int p = coordinates[i];
                w.set(
                    cx * 16 + (p >> 12),
                    p & 255,
                    cz * 16 + ((p >> 8) & 15),
                    Block.of(types[i]),
                    data[i]);
              }
            });
      }
      case 53 -> {
        int x = in.readInt(),
            y = in.readUnsignedByte(),
            z = in.readInt(),
            block = in.readUnsignedByte(),
            meta = in.readUnsignedByte();
        event(w -> w.set(x, y, z, Block.of(block), meta));
      }
      case 54 -> {
        in.readInt();
        in.readShort();
        in.readInt();
        in.readByte();
        in.readByte();
      }
      case 60 -> {
        in.readDouble();
        in.readDouble();
        in.readDouble();
        in.readFloat();
        int n = in.readInt();
        if (n < 0 || n > 1_000_000) throw new IOException("Invalid explosion");
        in.skipNBytes(n * 3L);
      }
      case 61 -> {
        in.readInt();
        in.readInt();
        in.readByte();
        in.readInt();
        in.readInt();
      }
      case 70 -> {
        in.readByte();
        in.readByte();
      }
      case 71 -> {
        in.readInt();
        in.readBoolean();
        in.readInt();
        in.readInt();
        in.readInt();
      }
      case 100 -> {
        in.readByte();
        in.readByte();
        readString(in, 256);
        in.readByte();
        mainEvents.add(
            () ->
                messages.accept(
                    "Remote container received; container synchronization is not supported."));
      }
      case 101 -> in.readByte();
      case 103 -> {
        int window = in.readByte(), slot = in.readShort();
        Stack stack = readStack(in);
        event(
            w -> {
              int index = slot >= 36 && slot < 45 ? slot - 36 : slot >= 9 && slot < 36 ? slot : -1;
              if (window == 0 && index >= 0) w.player.inventory.set(index, stack);
            });
      }
      case 104 -> {
        int window = in.readUnsignedByte(), n = in.readUnsignedShort();
        if (n > 512) throw new IOException("Invalid inventory size");
        Stack[] stacks = new Stack[n];
        for (int i = 0; i < n; i++) stacks[i] = readStack(in);
        event(
            w -> {
              if (window == 0)
                for (int i = 9; i < Math.min(n, 45); i++)
                  w.player.inventory.set(i >= 36 ? i - 36 : i, stacks[i]);
            });
      }
      case 105 -> {
        in.readByte();
        in.readShort();
        in.readShort();
      }
      case 106 -> {
        int window = in.readByte();
        short action = in.readShort();
        boolean accepted = in.readBoolean();
        if (!accepted)
          send(
              d -> {
                d.writeByte(106);
                d.writeByte(window);
                d.writeShort(action);
                d.writeBoolean(true);
              });
      }
      case 130 -> {
        in.readInt();
        in.readShort();
        in.readInt();
        for (int i = 0; i < 4; i++) readString(in, 256);
      }
      case 131 -> {
        in.readShort();
        in.readShort();
        in.skipNBytes(in.readUnsignedByte());
      }
      case 200 -> {
        in.readInt();
        in.readByte();
      }
      case 201 -> {
        readString(in, 16);
        in.readBoolean();
        in.readShort();
      }
      case 250 -> {
        readString(in, 256);
        in.skipNBytes(in.readUnsignedShort());
      }
      case 255 -> throw new IOException(readString(in, 32767));
      default -> throw new IOException("Unsupported legacy packet 0x" + Integer.toHexString(id));
    }
  }

  private void event(Consumer<World> action) {
    World current = world;
    if (current != null) mainEvents.add(() -> action.accept(current));
  }

  private void chunkPacket(DataInputStream in) throws IOException {
    int x = in.readInt(),
        y = in.readShort(),
        z = in.readInt(),
        sx = in.readUnsignedByte() + 1,
        sy = in.readUnsignedByte() + 1,
        sz = in.readUnsignedByte() + 1,
        len = in.readInt();
    if (sx > 16 || sy > 128 || sz > 16 || len < 0 || len > 2_000_000)
      throw new IOException("Invalid chunk dimensions");
    byte[] compressed = in.readNBytes(len);
    if (compressed.length != len) throw new EOFException();
    int volume = sx * sy * sz, expected = volume + (volume + 1) / 2 * 3;
    byte[] raw;
    try (var inflater = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
      raw = inflater.readNBytes(expected + 1);
    }
    if (raw.length != expected) throw new IOException("Invalid decompressed chunk size");
    event(
        w -> {
          for (int dx = 0; dx < sx; dx++)
            for (int dz = 0; dz < sz; dz++)
              for (int dy = 0; dy < sy; dy++) {
                int i = (dx * sz + dz) * sy + dy, wx = x + dx, wy = y + dy, wz = z + dz;
                if (wy < 0 || wy >= 128) continue;
                Chunk c = w.chunk(wx >> 4, wz >> 4);
                int j = Chunk.index(wx & 15, wy, wz & 15);
                c.blocks[j] = raw[i];
                c.data[j] = (byte) ((raw[volume + i / 2] >> ((i & 1) * 4)) & 15);
                c.light[j] =
                    (byte) ((raw[volume + (volume + 1) / 2 + i / 2] >> ((i & 1) * 4)) & 15);
                c.sky[j] =
                    (byte) ((raw[volume + (volume + 1) / 2 * 2 + i / 2] >> ((i & 1) * 4)) & 15);
                c.meshDirty = true;
                c.lightingDirty = false;
              }
        });
  }

  private static void metadata(DataInputStream in) throws IOException {
    for (int n = 0; n < 256; n++) {
      int b = in.readUnsignedByte();
      if (b == 127) return;
      switch (b >> 5) {
        case 0 -> in.readByte();
        case 1 -> in.readShort();
        case 2 -> in.readInt();
        case 3 -> in.readFloat();
        case 4 -> readString(in, 32767);
        case 5 -> readStack(in);
        case 6 -> {
          in.readInt();
          in.readInt();
          in.readInt();
        }
        default -> throw new IOException("Invalid metadata");
      }
    }
    throw new IOException("Unterminated metadata");
  }

  public static void writeString(DataOutput out, String value) throws IOException {
    if (value.length() > 32767) throw new IOException("String too long");
    out.writeShort(value.length());
    out.writeChars(value);
  }

  public static String readString(DataInput in, int max) throws IOException {
    int n = in.readShort();
    if (n < 0 || n > max) throw new IOException("Invalid string length");
    StringBuilder b = new StringBuilder(n);
    for (int i = 0; i < n; i++) b.append(in.readChar());
    return b.toString();
  }

  private static Stack readStack(DataInput in) throws IOException {
    int id = in.readShort();
    if (id < 0) return Stack.EMPTY;
    int count = in.readUnsignedByte(), damage = in.readShort();
    try {
      return new Stack(id, count, damage);
    } catch (IllegalArgumentException ex) {
      throw new IOException("Invalid remote stack", ex);
    }
  }

  private static void writeStack(DataOutput out, Stack s) throws IOException {
    if (s.empty()) out.writeShort(-1);
    else {
      out.writeShort(s.id());
      out.writeByte(s.count());
      out.writeShort(s.damage());
    }
  }

  @FunctionalInterface
  private interface Packet {
    void write(DataOutputStream out) throws IOException;
  }

  private synchronized void send(Packet packet) throws IOException {
    if (out == null || !open) throw new IOException("Not connected");
    packet.write(out);
    out.flush();
  }

  private void safely(Packet p) {
    try {
      send(p);
    } catch (IOException e) {
      if (open) mainEvents.add(() -> disconnected.accept(e.getMessage()));
      close();
    }
  }

  public void position(Player p) {
    safely(
        d -> {
          d.writeByte(13);
          d.writeDouble(p.x);
          d.writeDouble(p.y);
          d.writeDouble(p.eyeY());
          d.writeDouble(p.z);
          d.writeFloat((float) -p.yaw);
          d.writeFloat((float) p.pitch);
          d.writeBoolean(p.onGround);
        });
  }

  public void chat(String text) {
    safely(
        d -> {
          d.writeByte(3);
          writeString(d, text.substring(0, Math.min(100, text.length())));
        });
  }

  public void select(int slot) {
    safely(
        d -> {
          d.writeByte(16);
          d.writeShort(slot);
        });
  }

  public void dig(World.Hit hit, int status) {
    safely(
        d -> {
          d.writeByte(14);
          d.writeByte(status);
          d.writeInt(hit.pos().x());
          d.writeByte(hit.pos().y());
          d.writeInt(hit.pos().z());
          d.writeByte(face(hit));
        });
  }

  public void place(World.Hit hit, Stack stack) {
    safely(
        d -> {
          d.writeByte(15);
          d.writeInt(hit.pos().x());
          d.writeByte(hit.pos().y());
          d.writeInt(hit.pos().z());
          d.writeByte(face(hit));
          writeStack(d, stack);
        });
  }

  private static int face(World.Hit h) {
    return h.ny() < 0 ? 0 : h.ny() > 0 ? 1 : h.nz() < 0 ? 2 : h.nz() > 0 ? 3 : h.nx() < 0 ? 4 : 5;
  }

  public void respawn() {
    safely(
        d -> {
          d.writeByte(9);
          d.writeByte(0);
          d.writeByte(2);
          d.writeByte(0);
          d.writeShort(128);
          d.writeLong(0);
        });
  }

  public void click(int slot, boolean right, boolean shift, Stack stack) {
    short action = ++transaction;
    safely(
        d -> {
          d.writeByte(102);
          d.writeByte(0);
          d.writeShort(slot);
          d.writeByte(right ? 1 : 0);
          d.writeShort(action);
          d.writeBoolean(shift);
          writeStack(d, stack);
        });
  }

  public void close() {
    open = false;
    Socket s = socket;
    if (s != null)
      try {
        s.close();
      } catch (IOException ignored) {
      }
  }
}
