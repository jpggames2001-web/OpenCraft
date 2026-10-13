package dev.opencraft.world;

import dev.opencraft.game.*;
import dev.opencraft.game.Stack;
import dev.opencraft.physics.Aabb;
import java.io.*;
import java.util.*;

public final class World {
  public record Pos(int x, int y, int z) {}

  public record Hit(Pos pos, int nx, int ny, int nz, double distance) {
    public Pos adjacent() {
      return new Pos(pos.x + nx, pos.y + ny, pos.z + nz);
    }
  }

  public static final class Tile {
    public Inventory inventory;
    public int burn, cook, totalBurn;

    public Tile(int size) {
      inventory = new Inventory(size);
    }
  }

  public static final class Drop {
    public double x, y, z, vy;
    public Stack stack;
    public int age;

    public Drop(double x, double y, double z, Stack s) {
      this.x = x;
      this.y = y;
      this.z = z;
      stack = s;
      vy = .15;
    }
  }

  public static final class Mob {
    public enum Kind {
      PIG,
      SHEEP,
      COW,
      CHICKEN,
      ZOMBIE,
      SKELETON,
      SPIDER,
      CREEPER
    }

    public final Kind kind;
    public double x, y, z, yaw;
    public int health = 20, attackCooldown, fuse;

    public Mob(Kind kind, double x, double y, double z) {
      this.kind = kind;
      this.x = x;
      this.y = y;
      this.z = z;
    }

    public boolean hostile() {
      return kind.ordinal() >= 4;
    }

    public Aabb bounds() {
      return new Aabb(
          x - .4,
          y,
          z - .4,
          x + .4,
          y + (kind == Kind.SPIDER ? .5 : kind.ordinal() < 4 ? .9 : 1.8),
          z + .4);
    }
  }

  public final Map<Long, Chunk> chunks = new LinkedHashMap<>();
  public final Map<Pos, Tile> tiles = new HashMap<>();
  public final List<Drop> drops = new ArrayList<>();
  public final List<Mob> mobs = new ArrayList<>();
  public final Generator generator;
  public final WorldStore.Save save;
  private final WorldStore store;
  private final Random random;
  private final ArrayDeque<Pos> updates = new ArrayDeque<>();
  private final Set<Pos> queuedUpdates = new HashSet<>();
  public Player player = new Player();
  public long time = 6000;
  public int spawnX, spawnY = 66, spawnZ;
  public int difficulty = 2;
  public boolean remote;
  private int ticks;

  public World(Generator generator) {
    this.generator = generator;
    store = null;
    save = null;
    random = new Random(generator.seed());
    init();
  }

  public World(WorldStore store, WorldStore.Save save) throws IOException {
    this.store = store;
    this.save = save;
    generator = new Generator(save.seed(), save.type());
    random = new Random(save.seed());
    if (!store.loadState(this)) init();
  }

  private void init() {
    if (generator.type() == Generator.Type.DEFAULT) {
      for (int radius = 0; radius < 512; radius += 8) {
        int h = generator.surface(radius, 0);
        Biome b = generator.biome(radius, 0);
        if (h > 63 && b != Biome.OCEAN && b != Biome.RIVER) {
          spawnX = radius;
          spawnY = h + 1;
          break;
        }
      }
    } else {
      spawnX = 2;
      spawnY = 65;
      spawnZ = 0;
      var t = new Tile(27);
      t.inventory.set(0, new Stack(Items.LAVA_BUCKET, 1));
      t.inventory.set(1, new Stack(Block.ICE.id, 2));
      t.inventory.set(2, new Stack(Block.SAPLING.id, 1));
      tiles.put(new Pos(2, 65, -2), t);
    }
    player.respawn(this);
  }

  public Chunk chunk(int cx, int cz) {
    boolean existed = chunks.containsKey(Chunk.key(cx, cz));
    Chunk result =
        chunks.computeIfAbsent(
            Chunk.key(cx, cz),
            k -> {
              try {
                if (remote) return new Chunk(cx, cz);
                Chunk c = store == null ? null : store.loadChunk(save, cx, cz);
                return c == null ? generator.generate(cx, cz) : c;
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    if (!existed)
      for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        Chunk neighbor = chunks.get(Chunk.key(cx + d[0], cz + d[1]));
        if (neighbor != null) neighbor.meshDirty = neighbor.lightingDirty = true;
      }
    return result;
  }

  public Block block(int x, int y, int z) {
    return y < 0 || y >= 128 ? Block.AIR : chunk(x >> 4, z >> 4).block(x & 15, y, z & 15);
  }

  public Block peek(int x, int y, int z) {
    Chunk c = chunks.get(Chunk.key(x >> 4, z >> 4));
    return c == null || y < 0 || y >= 128 ? Block.AIR : c.block(x & 15, y, z & 15);
  }

  public int data(int x, int y, int z) {
    return y < 0 || y >= 128 ? 0 : chunk(x >> 4, z >> 4).data[Chunk.index(x & 15, y, z & 15)] & 15;
  }

  public int light(int x, int y, int z, boolean sky) {
    if (y >= 128) return sky ? 15 : 0;
    if (y < 0) return 0;
    Chunk c = chunks.get(Chunk.key(x >> 4, z >> 4));
    return c == null ? 0 : (sky ? c.sky : c.light)[Chunk.index(x & 15, y, z & 15)] & 15;
  }

  public void set(int x, int y, int z, Block b, int meta) {
    if (y < 0 || y >= 128) return;
    Chunk c = chunk(x >> 4, z >> 4);
    c.put(x & 15, y, z & 15, b, meta);
    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
      Chunk n = chunks.get(Chunk.key((x + d[0]) >> 4, (z + d[1]) >> 4));
      if (n != null) n.meshDirty = n.lightingDirty = true;
    }
    schedule(new Pos(x, y, z));
    for (int[] d : DIR) schedule(new Pos(x + d[0], y + d[1], z + d[2]));
  }

  private static final int[][] DIR = {
    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
  };

  private void schedule(Pos p) {
    if (p.y >= 0 && p.y < 128 && queuedUpdates.add(p)) updates.addLast(p);
  }

  public void loadAround(int radius) {
    int cx = (int) Math.floor(player.x) >> 4, cz = (int) Math.floor(player.z) >> 4;
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++) chunk(cx + dx, cz + dz);
  }

  public List<Aabb> collisions(Aabb box) {
    List<Aabb> out = new ArrayList<>();
    for (int x = (int) Math.floor(box.minX()); x <= Math.floor(box.maxX()); x++)
      for (int z = (int) Math.floor(box.minZ()); z <= Math.floor(box.maxZ()); z++)
        for (int y = Math.max(0, (int) Math.floor(box.minY()) - 1);
            y <= Math.min(127, Math.floor(box.maxY()));
            y++) {
          Block b = block(x, y, z);
          if (!b.solid) continue;
          int meta = data(x, y, z);
          Aabb a;
          if ((b == Block.WOOD_DOOR
                  || b == Block.IRON_DOOR
                  || b == Block.FENCE_GATE
                  || b == Block.TRAPDOOR)
              && (meta & 4) != 0) continue;
          if (b == Block.WOOD_DOOR || b == Block.IRON_DOOR)
            a = new Aabb(x, y, z, x + 1, y + 1, z + .1875);
          else if (b == Block.FENCE) a = new Aabb(x + .25, y, z + .25, x + .75, y + 1.5, z + .75);
          else a = new Aabb(x, y, z, x + 1, y + b.height(), z + 1);
          if (a.intersects(box)) out.add(a);
        }
    return out;
  }

  public Hit raycast(double x, double y, double z, double dx, double dy, double dz, double reach) {
    int bx = (int) Math.floor(x),
        by = (int) Math.floor(y),
        bz = (int) Math.floor(z),
        nx = 0,
        ny = 0,
        nz = 0;
    double tx = boundary(x, dx),
        ty = boundary(y, dy),
        tz = boundary(z, dz),
        stepX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx),
        stepY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy),
        stepZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz),
        distance = 0;
    while (distance <= reach) {
      Block b = block(bx, by, bz);
      if (b != Block.AIR && !b.fluid()) return new Hit(new Pos(bx, by, bz), nx, ny, nz, distance);
      if (tx < ty && tx < tz) {
        distance = tx;
        tx += stepX;
        nx = dx > 0 ? -1 : 1;
        ny = nz = 0;
        bx += dx > 0 ? 1 : -1;
      } else if (ty < tz) {
        distance = ty;
        ty += stepY;
        ny = dy > 0 ? -1 : 1;
        nx = nz = 0;
        by += dy > 0 ? 1 : -1;
      } else {
        distance = tz;
        tz += stepZ;
        nz = dz > 0 ? -1 : 1;
        nx = ny = 0;
        bz += dz > 0 ? 1 : -1;
      }
    }
    return null;
  }

  private static double boundary(double p, double d) {
    return d == 0
        ? Double.POSITIVE_INFINITY
        : d > 0 ? (Math.floor(p) + 1 - p) / d : (p - Math.floor(p)) / -d;
  }

  public Hit target() {
    double yaw = Math.toRadians(player.yaw), pitch = Math.toRadians(player.pitch);
    return raycast(
        player.x,
        player.eyeY(),
        player.z,
        -Math.sin(yaw) * Math.cos(pitch),
        -Math.sin(pitch),
        -Math.cos(yaw) * Math.cos(pitch),
        player.creative ? 5 : 4.5);
  }

  public Tile tile(Pos p) {
    Block b = block(p.x, p.y, p.z);
    return tiles.computeIfAbsent(
        p, k -> new Tile(b == Block.FURNACE || b == Block.FURNACE_LIT ? 3 : 27));
  }

  public void drop(double x, double y, double z, Stack stack) {
    if (!stack.empty()) drops.add(new Drop(x, y, z, stack));
  }

  public void breakBlock(Pos p) {
    Block b = block(p.x, p.y, p.z);
    if (b == Block.AIR || b.hardness < 0) return;
    int metadata = data(p.x, p.y, p.z);
    set(p.x, p.y, p.z, Block.AIR, 0);
    if (!player.creative) {
      int id =
          switch (b) {
            case STONE -> 4;
            case COAL_ORE -> 263;
            case DIAMOND_ORE -> 264;
            case REDSTONE_ORE, REDSTONE_ORE_LIT -> 331;
            case LAPIS_ORE -> 351;
            case TALL_GRASS -> random.nextInt(8) == 0 ? 295 : 0;
            case LEAVES -> random.nextInt(20) == 0 ? 6 : 0;
            case GLASS, GLASS_PANE -> 0;
            case ICE -> 0;
            case GRASS -> 3;
            case FURNACE_LIT -> 61;
            case WHEAT -> metadata >= 7 ? 296 : 295;
            default -> b.id;
          };
      var tool = Items.get(player.held().id());
      boolean mineable =
          !requiresPick(b) || tool.tool().equals("pickaxe") && tool.tier() >= requiredTier(b);
      if (id != 0 && mineable) drop(p.x + .5, p.y + .5, p.z + .5, new Stack(id, 1));
      player.held(player.held().hurt());
      player.exhaustion += .025f;
    }
    Tile tile = tiles.remove(p);
    if (tile != null)
      for (int i = 0; i < tile.inventory.size(); i++)
        drop(p.x + .5, p.y + .5, p.z + .5, tile.inventory.get(i));
    if (b == Block.ICE) set(p.x, p.y, p.z, Block.WATER, 0);
    if (b == Block.WOOD_DOOR || b == Block.IRON_DOOR) {
      int dy = block(p.x, p.y + 1, p.z) == b ? 1 : -1;
      set(p.x, p.y + dy, p.z, Block.AIR, 0);
    }
  }

  public static boolean requiresPick(Block b) {
    return switch (b) {
      case STONE,
          COBBLESTONE,
          GOLD_ORE,
          IRON_ORE,
          COAL_ORE,
          LAPIS_ORE,
          DIAMOND_ORE,
          REDSTONE_ORE,
          REDSTONE_ORE_LIT,
          OBSIDIAN,
          IRON_BLOCK,
          GOLD_BLOCK,
          DIAMOND_BLOCK,
          FURNACE,
          FURNACE_LIT,
          STONE_BRICKS ->
          true;
      default -> false;
    };
  }

  public static int requiredTier(Block b) {
    return switch (b) {
      case OBSIDIAN -> 3;
      case DIAMOND_ORE, GOLD_ORE, REDSTONE_ORE, REDSTONE_ORE_LIT, GOLD_BLOCK, DIAMOND_BLOCK -> 2;
      case IRON_ORE, LAPIS_ORE, IRON_BLOCK -> 1;
      default -> 0;
    };
  }

  public double miningProgress(Block b) {
    if (b.hardness < 0) return 0;
    if (player.creative || b.hardness == 0) return 1;
    var item = Items.get(player.held().id());
    String wanted =
        requiresPick(b)
            ? "pickaxe"
            : switch (b) {
              case LOG, PLANKS, CHEST, CRAFTING_TABLE, BOOKSHELF, WOOD_DOOR -> "axe";
              case GRASS, DIRT, SAND, GRAVEL, CLAY, SNOW, SNOW_BLOCK, FARMLAND -> "shovel";
              case LEAVES, WOOL -> "shears";
              default -> "";
            };
    double speed =
        item.tool().equals(wanted) && !wanted.isEmpty()
            ? item.tool().equals("shears")
                ? 15
                : new double[] {2, 4, 6, 8}[Math.min(3, item.tier())]
            : 1;
    if (!player.onGround) speed /= 5;
    if (block((int) player.x, (int) player.eyeY(), (int) player.z).fluid()) speed /= 5;
    boolean valid =
        !requiresPick(b) || item.tool().equals("pickaxe") && item.tier() >= requiredTier(b);
    return speed / b.hardness / (valid ? 30 : 100);
  }

  public void tick() {
    ticks++;
    time++;
    if (ticks % 5 == 0) {
      for (int n = 0, size = Math.min(128, updates.size()); n < size; n++) {
        Pos p = updates.removeFirst();
        queuedUpdates.remove(p);
        updateBlock(p);
      }
    }
    tickTiles();
    tickDrops();
    tickMobs();
    randomTicks();
    if (ticks % 200 == 0 && mobs.size() < 12) spawnMob();
    int relit = 0;
    for (Chunk c : chunks.values()) if (c.lightingDirty && relit++ < 2) Lighting.update(c, this);
  }

  private void updateBlock(Pos p) {
    Block b = block(p.x, p.y, p.z), below = block(p.x, p.y - 1, p.z);
    if ((b == Block.SAND || b == Block.GRAVEL)
        && p.y > 0
        && (below == Block.AIR || below.fluid())) {
      set(p.x, p.y, p.z, Block.AIR, 0);
      set(p.x, p.y - 1, p.z, b, 0);
      return;
    }
    if (b.fluid()) {
      boolean lava = b.lava();
      int level = data(p.x, p.y, p.z);
      if (lava) {
        for (int[] d : DIR) {
          Block neighbor = block(p.x + d[0], p.y + d[1], p.z + d[2]);
          if (neighbor == Block.WATER || neighbor == Block.WATER_FLOW) {
            set(p.x, p.y, p.z, level == 0 ? Block.OBSIDIAN : Block.COBBLESTONE, 0);
            return;
          }
        }
      }
      if (!lava && level > 0) {
        int sources = 0;
        for (int[] d : DIR)
          if (d[1] == 0 && block(p.x + d[0], p.y, p.z + d[2]) == Block.WATER) sources++;
        if (sources >= 2 && below.solid) {
          set(p.x, p.y, p.z, Block.WATER, 0);
          level = 0;
        }
      }
      Block flow = lava ? Block.LAVA_FLOW : Block.WATER_FLOW;
      if (p.y > 0 && (below == Block.AIR || below.plant())) set(p.x, p.y - 1, p.z, flow, 8);
      else {
        int next = (level & 7) + (lava ? 2 : 1);
        if (next < 8)
          for (int[] d : DIR)
            if (d[1] == 0) {
              Block n = block(p.x + d[0], p.y, p.z + d[2]);
              if (n == Block.AIR || n.plant()) set(p.x + d[0], p.y, p.z + d[2], flow, next);
            }
      }
    }
    if (b == Block.REDSTONE_WIRE) {
      int power = 0;
      for (int[] d : DIR) {
        Block n = block(p.x + d[0], p.y + d[1], p.z + d[2]);
        int m = data(p.x + d[0], p.y + d[1], p.z + d[2]);
        if (n == Block.REDSTONE_TORCH || n == Block.LEVER && (m & 8) != 0) power = 15;
        else if (n == Block.REDSTONE_WIRE) power = Math.max(power, m - 1);
      }
      if (power != data(p.x, p.y, p.z)) set(p.x, p.y, p.z, b, power);
    }
    if (b == Block.IRON_DOOR) {
      boolean powered = false;
      for (int[] d : DIR) {
        Block n = block(p.x + d[0], p.y + d[1], p.z + d[2]);
        if (n == Block.REDSTONE_TORCH
            || n == Block.REDSTONE_WIRE && data(p.x + d[0], p.y + d[1], p.z + d[2]) > 0)
          powered = true;
      }
      int m = data(p.x, p.y, p.z), next = powered ? m | 4 : m & ~4;
      if (m != next) set(p.x, p.y, p.z, b, next);
    }
    if ((b == Block.SAPLING || b == Block.ROSE || b == Block.DANDELION || b == Block.TALL_GRASS)
        && !below.solid) {
      set(p.x, p.y, p.z, Block.AIR, 0);
      drop(p.x + .5, p.y, p.z + .5, new Stack(b.id, 1));
    }
  }

  private void tickTiles() {
    for (var e : tiles.entrySet()) {
      Pos p = e.getKey();
      Tile t = e.getValue();
      Block b = peek(p.x, p.y, p.z);
      if (b != Block.FURNACE && b != Block.FURNACE_LIT) continue;
      Inventory inv = t.inventory;
      int output = Items.smelt(inv.get(0).id());
      boolean can =
          output != 0
              && (inv.get(2).empty() || inv.get(2).id() == output && inv.get(2).count() < 64);
      if (t.burn > 0) t.burn--;
      if (t.burn == 0 && can) {
        t.burn = Items.fuel(inv.get(1).id());
        t.totalBurn = t.burn;
        if (t.burn > 0) {
          Stack fuel = inv.remove(1, 1);
          if (fuel.id() == 327) inv.set(1, new Stack(325, 1));
        }
      }
      if (t.burn > 0 && can) {
        if (++t.cook >= 200) {
          inv.remove(0, 1);
          inv.set(2, new Stack(output, inv.get(2).count() + 1));
          t.cook = 0;
        }
      } else t.cook = 0;
      Block desired = t.burn > 0 ? Block.FURNACE_LIT : Block.FURNACE;
      if (b != desired) set(p.x, p.y, p.z, desired, 0);
    }
  }

  private void tickDrops() {
    Iterator<Drop> it = drops.iterator();
    while (it.hasNext()) {
      Drop d = it.next();
      d.age++;
      d.vy -= .04;
      double next = d.y + d.vy;
      if (block((int) Math.floor(d.x), (int) Math.floor(next), (int) Math.floor(d.z)).solid) {
        d.y = Math.floor(next) + 1.05;
        d.vy = 0;
      } else d.y = next;
      d.vy *= .98;
      if (d.age > 10
          && Math.abs(d.x - player.x) < 1.2
          && Math.abs(d.z - player.z) < 1.2
          && Math.abs(d.y - player.y) < 2) {
        d.stack = player.inventory.add(d.stack);
        if (d.stack.empty()) {
          it.remove();
          continue;
        }
      }
      if (d.age >= 6000 || d.y < -64) it.remove();
    }
  }

  private void tickMobs() {
    Iterator<Mob> it = mobs.iterator();
    while (it.hasNext()) {
      Mob m = it.next();
      if (m.health <= 0) {
        drop(
            m.x,
            m.y + .5,
            m.z,
            new Stack(
                switch (m.kind) {
                  case PIG -> 319;
                  case COW -> 363;
                  case SHEEP -> 35;
                  case CHICKEN -> 365;
                  case ZOMBIE -> 367;
                  case SKELETON -> 352;
                  case SPIDER -> 287;
                  case CREEPER -> 289;
                },
                1 + random.nextInt(2)));
        it.remove();
        continue;
      }
      if (m.attackCooldown > 0) m.attackCooldown--;
      double dx = player.x - m.x, dz = player.z - m.z, d = Math.hypot(dx, dz);
      boolean chase = m.hostile() && d < 24 && healthActive();
      if (chase) m.yaw = Math.atan2(dx, dz);
      else if (ticks % 60 == 0) m.yaw = random.nextDouble() * Math.PI * 2;
      double speed = chase ? .08 : .025,
          nx = m.x + Math.sin(m.yaw) * speed,
          nz = m.z + Math.cos(m.yaw) * speed;
      if (collisions(m.bounds().move(nx - m.x, 0, nz - m.z)).isEmpty()) {
        m.x = nx;
        m.z = nz;
      } else if (collisions(m.bounds().move(nx - m.x, .5, nz - m.z)).isEmpty()) {
        m.x = nx;
        m.z = nz;
        m.y += .5;
      }
      if (collisions(m.bounds().move(0, -.1, 0)).isEmpty()) m.y -= .1;
      if (chase && d < 1.5 && m.attackCooldown == 0) {
        if (m.kind == Mob.Kind.CREEPER) {
          if (++m.fuse >= 30) {
            explode(m.x, m.y, m.z, 3);
            it.remove();
            continue;
          }
        } else {
          player.damage(difficulty + 1);
          m.attackCooldown = 20;
        }
      } else m.fuse = Math.max(0, m.fuse - 1);
      if (daylight() > .8
          && (m.kind == Mob.Kind.ZOMBIE || m.kind == Mob.Kind.SKELETON)
          && ticks % 20 == 0
          && light((int) m.x, (int) m.y + 2, (int) m.z, true) == 15) m.health--;
      if (m.y < -64) it.remove();
    }
  }

  private boolean healthActive() {
    return player.health > 0 && !player.creative && difficulty > 0;
  }

  private void spawnMob() {
    double a = random.nextDouble() * Math.PI * 2, r = 24 + random.nextInt(16);
    int x = (int) (player.x + Math.cos(a) * r),
        z = (int) (player.z + Math.sin(a) * r),
        y = chunk(x >> 4, z >> 4).height(x & 15, z & 15);
    if (y < 63) return;
    boolean hostile = daylight() < .3 && difficulty > 0;
    Mob.Kind kind = Mob.Kind.values()[(hostile ? 4 : 0) + random.nextInt(4)];
    mobs.add(new Mob(kind, x + .5, y, z + .5));
  }

  private void randomTicks() {
    for (Chunk c : new ArrayList<>(chunks.values()))
      for (int i = 0; i < 24; i++) {
        int x = random.nextInt(16),
            z = random.nextInt(16),
            y = random.nextInt(128),
            wx = c.x * 16 + x,
            wz = c.z * 16 + z;
        Block b = c.block(x, y, z);
        if (b == Block.DIRT && c.block(x, y + 1, z) == Block.AIR && light(wx, y + 1, wz, true) > 8)
          set(wx, y, wz, Block.GRASS, 0);
        if (b == Block.GRASS && c.block(x, y + 1, z).opacity() == 15) set(wx, y, wz, Block.DIRT, 0);
        if (b == Block.SAPLING && c.sky[Chunk.index(x, y, z)] >= 9 && random.nextInt(7) == 0)
          growTree(wx, y, wz);
        if (b == Block.WHEAT && data(wx, y, wz) < 7 && random.nextInt(3) == 0)
          set(wx, y, wz, b, data(wx, y, wz) + 1);
      }
  }

  public void growTree(int x, int y, int z) {
    int h = 4 + random.nextInt(3);
    if (y + h + 1 >= 128) return;
    for (int dy = 0; dy <= h; dy++) if (block(x, y + dy, z).solid) return;
    for (int dy = h - 2; dy <= h; dy++)
      for (int dx = -2; dx <= 2; dx++)
        for (int dz = -2; dz <= 2; dz++)
          if (block(x + dx, y + dy, z + dz) == Block.AIR)
            set(x + dx, y + dy, z + dz, Block.LEAVES, 0);
    for (int dy = 0; dy < h; dy++) set(x, y + dy, z, Block.LOG, 0);
  }

  public void explode(double x, double y, double z, int radius) {
    for (int bx = (int) x - radius; bx <= x + radius; bx++)
      for (int by = (int) y - radius; by <= y + radius; by++)
        for (int bz = (int) z - radius; bz <= z + radius; bz++)
          if (Math.pow(bx - x, 2) + Math.pow(by - y, 2) + Math.pow(bz - z, 2) < radius * radius
              && block(bx, by, bz).hardness >= 0
              && block(bx, by, bz) != Block.OBSIDIAN) breakBlock(new Pos(bx, by, bz));
    if (Math.hypot(x - player.x, z - player.z) < radius * 2) player.damage(radius * 3);
  }

  public float daylight() {
    double phase = (time % 24000) / 24000.;
    return (float) Math.max(.05, Math.min(1, Math.cos((phase - .25) * Math.PI * 2) * 1.3 + .25));
  }

  public void save() throws IOException {
    if (store != null && !remote) store.save(this);
  }
}
