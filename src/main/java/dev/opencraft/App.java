package dev.opencraft;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import dev.opencraft.audio.Audio;
import dev.opencraft.game.*;
import dev.opencraft.game.Stack;
import dev.opencraft.net.*;
import dev.opencraft.render.*;
import dev.opencraft.ui.Ui;
import dev.opencraft.world.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

public final class App implements AutoCloseable {
  @FunctionalInterface
  public interface Task {
    void run() throws Exception;
  }

  public final Path data;
  public final Options options;
  public final WorldStore store;
  public final Servers servers;
  public final ConcurrentLinkedQueue<Runnable> events = new ConcurrentLinkedQueue<>();
  public final Map<String, Long> stats = new LinkedHashMap<>();
  public final Set<String> achievements = new LinkedHashSet<>();
  public final long window;
  public final Renderer renderer;
  public final Audio audio;
  public final Ui ui;
  public World world;
  public LegacyClient client;
  public Generator.Type newType = Generator.Type.DEFAULT;
  public boolean newCreative, debug;
  public double mining;
  public int fps, toastTicks;
  public String toast = "";
  private final World panorama;
  private final boolean renderSmoke;
  private final int smokeFrames;
  private final GLFWErrorCallback errorCallback;
  private boolean captured, firstMouse = true, running = true;
  private double lastMouseX, lastMouseY, mouseX, mouseY, uiMouseX, uiMouseY;
  private World.Pos miningPos;
  private int useCooldown, stepCooldown, ticks, attackCooldown, miningDelay;
  private long lastJump;
  private boolean screenshotPending;

  public App(Path data, boolean renderSmoke, boolean nullPlatform, int smokeFrames)
      throws Exception {
    this.data = data.toAbsolutePath().normalize();
    this.renderSmoke = renderSmoke;
    this.smokeFrames = smokeFrames;
    options = new Options(this.data);
    store = new WorldStore(this.data);
    servers = new Servers(this.data);
    loadStats();
    errorCallback = GLFWErrorCallback.createPrint(System.err);
    errorCallback.set();
    if (nullPlatform) glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
    if (!glfwInit())
      throw new IllegalStateException(
          "GLFW could not initialize. A graphical desktop (X11/Wayland or Windows) is required;"
              + " --headless-smoke runs core checks without a display.");
    glfwDefaultWindowHints();
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
    if (nullPlatform) glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
    glfwWindowHint(GLFW_VISIBLE, renderSmoke ? GLFW_FALSE : GLFW_TRUE);
    window = glfwCreateWindow(1100, 720, "OpenCraft", NULL, NULL);
    if (window == NULL) {
      glfwTerminate();
      throw new IllegalStateException("OpenGL 3.3 window creation failed");
    }
    glfwMakeContextCurrent(window);
    GL.createCapabilities();
    glfwSwapInterval(renderSmoke ? 0 : options.vsync ? 1 : 0);
    System.out.println("OpenGL: " + glGetString(GL_VERSION) + " / " + glGetString(GL_RENDERER));
    renderer = new Renderer(options);
    audio = new Audio(options);
    ui = new Ui(this);
    panorama = new World(new Generator(17329, Generator.Type.DEFAULT));
    panorama.player.x = 8;
    panorama.player.z = 8;
    panorama.player.y = 82;
    panorama.loadAround(2);
    registerCallbacks();
    if (!options.texturePack.isEmpty()) {
      Path pack = this.data.resolve("texturepacks").resolve(options.texturePack).normalize();
      if (pack.getParent().equals(this.data.resolve("texturepacks")) && Files.isRegularFile(pack))
        guard(() -> renderer.assets.reload(pack));
    }
    if (options.fullscreen && !renderSmoke) applyFullscreen();
  }

  private void registerCallbacks() {
    glfwSetFramebufferSizeCallback(window, (win, w, h) -> glViewport(0, 0, w, h));
    glfwSetCursorPosCallback(
        window,
        (win, x, y) -> {
          mouseX = x;
          mouseY = y;
          if (captured && world != null) {
            if (!firstMouse) {
              double factor = Math.pow(options.sensitivity * .6 + .2, 3) * 8 * .15;
              world.player.yaw += (x - lastMouseX) * factor;
              world.player.pitch =
                  Math.max(-90, Math.min(90, world.player.pitch + (y - lastMouseY) * factor));
            }
            firstMouse = false;
            lastMouseX = x;
            lastMouseY = y;
          } else updatePointer();
        });
    glfwSetMouseButtonCallback(
        window,
        (win, button, action, mods) -> {
          if (!ui.game()) ui.mouse(button, action == GLFW_PRESS, (mods & GLFW_MOD_SHIFT) != 0);
          else if (action == GLFW_PRESS) {
            if (options.keys.get("Pick Block") == -button - 1) pick();
          }
        });
    glfwSetScrollCallback(window, (win, x, y) -> ui.scroll(y));
    glfwSetCharCallback(window, (win, point) -> ui.character(point));
    glfwSetKeyCallback(
        window,
        (win, key, scancode, action, mods) -> {
          if (action != GLFW_PRESS && action != GLFW_REPEAT) return;
          guard(
              () -> {
                if (ui.key(key, mods)) return;
                if (action == GLFW_REPEAT) return;
                if (world == null) return;
                Player p = world.player;
                if (key == GLFW_KEY_F3) {
                  debug = !debug;
                  return;
                }
                if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                  selectSlot(key - GLFW_KEY_1);
                  return;
                }
                if (key == options.keys.get("Inventory")) {
                  achieve("Taking Inventory");
                  ui.inventory(Ui.Screen.INVENTORY, null);
                } else if (key == options.keys.get("Drop")) {
                  Stack s = p.inventory.remove(p.selected, 1);
                  world.drop(p.x, p.eyeY(), p.z, s);
                } else if (key == options.keys.get("Chat")) ui.open(Ui.Screen.CHAT);
                else if (key == options.keys.get("Screenshot")) screenshotPending = true;
                else if (key == options.keys.get("Fullscreen")) toggleFullscreen();
                else if (key == options.keys.get("Fog"))
                  options.renderDistance = (options.renderDistance + 1) % 4;
                else if (key == options.keys.get("Jump") && p.creative) {
                  long now = System.nanoTime();
                  if (now - lastJump < 300_000_000) {
                    p.flying = !p.flying;
                    lastJump = 0;
                  } else lastJump = now;
                }
              });
        });
    glfwSetWindowFocusCallback(
        window,
        (win, focused) -> {
          if (!focused && ui.game() && !renderSmoke) ui.open(Ui.Screen.PAUSE);
        });
  }

  public void run() throws Exception {
    long previous = System.nanoTime(), fpsAt = previous;
    double accumulator = 0;
    int frameCount = 0, frames = 0;
    while (running && !glfwWindowShouldClose(window)) {
      long begin = System.nanoTime();
      double delta = Math.min(.25, (begin - previous) / 1e9);
      previous = begin;
      accumulator += delta;
      glfwPollEvents();
      Runnable event;
      while ((event = events.poll()) != null) event.run();
      if (client != null) client.poll();
      while (accumulator >= .05) {
        guard(this::tick);
        accumulator -= .05;
      }
      if (renderSmoke) smokeStep(frames);
      int width, height;
      try (var stack = MemoryStack.stackPush()) {
        var wb = stack.mallocInt(1);
        var hb = stack.mallocInt(1);
        glfwGetFramebufferSize(window, wb, hb);
        width = wb.get(0);
        height = hb.get(0);
      }
      if (width <= 0 || height <= 0) {
        LockSupport.parkNanos(20_000_000);
        continue;
      }
      renderer.draw(world == null ? panorama : world, width, height, world == null, glfwGetTime());
      int factor = options.guiFactor(width, height);
      renderer.canvas.begin(width / factor, height / factor);
      updatePointer();
      ui.render(renderer.canvas, uiMouseX, uiMouseY);
      renderer.canvas.end(renderer.assets.texture());
      if (screenshotPending) {
        screenshotPending = false;
        Path file =
            data.resolve("screenshots")
                .resolve("screenshot-" + System.currentTimeMillis() + ".png");
        renderer.screenshot(file, width, height);
        notify("Screenshot saved");
      }
      if (renderSmoke) {
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException("OpenGL error: " + error);
        if (frames == 0 || frames % 5 == 4)
          renderer.screenshot(
              data.resolve("render-smoke")
                  .resolve(String.format(Locale.ROOT, "%03d-%s.png", frames, ui.screen)),
              width,
              height);
        if (++frames >= smokeFrames) {
          System.out.println("RENDER_SMOKE_OK: " + frames + " frames, OpenGL errors=0");
          running = false;
        }
      }
      glfwSwapBuffers(window);
      frameCount++;
      if (begin - fpsAt >= 1_000_000_000L) {
        fps = frameCount;
        frameCount = 0;
        fpsAt = begin;
      }
      if (!options.vsync && options.fpsLimit > 0 && !renderSmoke) {
        long wait = 1_000_000_000L / options.fpsLimit - (System.nanoTime() - begin);
        if (wait > 0) LockSupport.parkNanos(wait);
      }
    }
  }

  private void updatePointer() {
    if (ui == null) return;
    try (var stack = MemoryStack.stackPush()) {
      var wi = stack.mallocInt(1);
      var he = stack.mallocInt(1);
      var fw = stack.mallocInt(1);
      var fh = stack.mallocInt(1);
      glfwGetWindowSize(window, wi, he);
      glfwGetFramebufferSize(window, fw, fh);
      int factor = options.guiFactor(fw.get(0), fh.get(0));
      uiMouseX = mouseX * fw.get(0) / Math.max(1, wi.get(0)) / factor;
      uiMouseY = mouseY * fh.get(0) / Math.max(1, he.get(0)) / factor;
      ui.move(uiMouseX, uiMouseY);
    }
  }

  private boolean pressed(String action) {
    int key = options.keys.get(action);
    return key < 0
        ? glfwGetMouseButton(window, -key - 1) == GLFW_PRESS
        : key > 0 && glfwGetKey(window, key) == GLFW_PRESS;
  }

  private void tick() throws Exception {
    ticks++;
    if (toastTicks > 0) toastTicks--;
    if (world == null || ui.pauses()) return;
    Player p = world.player;
    if (p.health <= 0) {
      if (ui.screen != Ui.Screen.DEATH) ui.death();
      return;
    }
    if (!world.remote) ensureChunks();
    Player.Input input =
        ui.game()
            ? new Player.Input(
                (pressed("Right") ? 1 : 0) - (pressed("Left") ? 1 : 0),
                (pressed("Forward") ? 1 : 0) - (pressed("Back") ? 1 : 0),
                pressed("Jump"),
                pressed("Sneak"),
                pressed("Sprint"))
            : Player.Input.NONE;
    double oldX = p.x, oldZ = p.z;
    int oldHealth = p.health;
    p.tick(world, input);
    if (!world.remote) world.tick();
    else if (client != null) client.position(p);
    if (p.health < oldHealth) audio.play(Audio.Sound.DAMAGE);
    double moved = Math.hypot(p.x - oldX, p.z - oldZ);
    if (moved > .01) {
      stat("Distance walked (cm)", Math.round(moved * 100));
      if (p.onGround && --stepCooldown <= 0) {
        audio.play(Audio.Sound.STEP);
        stepCooldown = p.sprinting ? 6 : 9;
      }
    }
    if (useCooldown > 0) useCooldown--;
    if (attackCooldown > 0) attackCooldown--;
    if (miningDelay > 0) miningDelay--;
    if (ui.game()) {
      if (pressed("Attack")) attack();
      else {
        mining = 0;
        miningPos = null;
      }
      if (pressed("Use Item") && useCooldown == 0) {
        use();
        useCooldown = 5;
      }
    }
    if (ticks % 20 == 0) {
      stat("Time played (seconds)", 1);
      if (p.inventory.count(265) > 0) achieve("Acquire Hardware");
      if (p.inventory.count(264) > 0) achieve("DIAMONDS!");
    }
    if (ticks % 1200 == 0 && !world.remote) {
      world.save();
      saveStats();
      notify("World saved");
    }
  }

  private void ensureChunks() throws IOException {
    Player p = world.player;
    int cx = (int) Math.floor(p.x) >> 4, cz = (int) Math.floor(p.z) >> 4, radius = options.radius();
    int generated = 0;
    outer:
    for (int distance = 0; distance <= radius; distance++)
      for (int dx = -distance; dx <= distance; dx++)
        for (int dz = -distance; dz <= distance; dz++)
          if (Math.max(Math.abs(dx), Math.abs(dz)) == distance
              && !world.chunks.containsKey(Chunk.key(cx + dx, cz + dz))) {
            world.chunk(cx + dx, cz + dz);
            if (++generated >= 2) break outer;
          }
    if (ticks % 200 == 0) {
      boolean distant =
          world.chunks.values().stream()
              .anyMatch(c -> Math.abs(c.x - cx) > radius + 2 || Math.abs(c.z - cz) > radius + 2);
      if (distant) {
        world.save();
        world
            .chunks
            .values()
            .removeIf(c -> Math.abs(c.x - cx) > radius + 2 || Math.abs(c.z - cz) > radius + 2);
      }
    }
  }

  private void attack() {
    if (miningDelay > 0) return;
    Player p = world.player;
    double yaw = Math.toRadians(p.yaw),
        pitch = Math.toRadians(p.pitch),
        dx = -Math.sin(yaw) * Math.cos(pitch),
        dy = -Math.sin(pitch),
        dz = -Math.cos(yaw) * Math.cos(pitch);
    if (attackCooldown == 0)
      for (World.Mob m : world.mobs) {
        double tx = m.x - p.x,
            ty = m.y + .8 - p.eyeY(),
            tz = m.z - p.z,
            dist = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (dist < 3 && dist > 0 && (tx * dx + ty * dy + tz * dz) / dist > .9) {
          m.health -=
              Items.get(p.held().id()).tool().equals("sword")
                  ? 4 + Items.get(p.held().id()).tier() * 2
                  : 1;
          attackCooldown = 10;
          audio.play(Audio.Sound.DAMAGE);
          return;
        }
      }
    World.Hit hit = world.target();
    if (hit == null) {
      mining = 0;
      miningPos = null;
      return;
    }
    if (!hit.pos().equals(miningPos)) {
      miningPos = hit.pos();
      mining = 0;
      if (world.remote && client != null) client.dig(hit, 0);
    }
    World.Pos pos = hit.pos();
    Block block = world.block(pos.x(), pos.y(), pos.z());
    mining += world.miningProgress(block);
    if (mining >= 1) {
      if (world.remote && client != null) client.dig(hit, 2);
      else world.breakBlock(pos);
      audio.play(Audio.Sound.DIG);
      stat("Blocks mined", 1);
      if (block == Block.LOG) achieve("Getting Wood");
      mining = 0;
      miningPos = null;
      miningDelay = 5;
    }
  }

  private void use() {
    Player p = world.player;
    if (p.eat()) {
      audio.play(Audio.Sound.PICKUP);
      return;
    }
    World.Hit hit = world.target();
    if (hit == null) return;
    World.Pos pos = hit.pos();
    Block b = world.block(pos.x(), pos.y(), pos.z());
    Stack held = p.held();
    if (world.remote && client != null) {
      client.place(hit, held);
      return;
    }
    if (!p.sneaking) {
      if (b == Block.CRAFTING_TABLE) {
        ui.inventory(Ui.Screen.CRAFTING, pos);
        return;
      }
      if (b == Block.CHEST || b == Block.DISPENSER) {
        ui.inventory(Ui.Screen.CHEST, pos);
        return;
      }
      if (b == Block.FURNACE || b == Block.FURNACE_LIT) {
        ui.inventory(Ui.Screen.FURNACE, pos);
        return;
      }
      if (b == Block.WOOD_DOOR || b == Block.FENCE_GATE || b == Block.TRAPDOOR) {
        int meta = world.data(pos.x(), pos.y(), pos.z()) ^ 4;
        world.set(pos.x(), pos.y(), pos.z(), b, meta);
        if (b == Block.WOOD_DOOR) {
          int dy = world.block(pos.x(), pos.y() + 1, pos.z()) == b ? 1 : -1;
          world.set(pos.x(), pos.y() + dy, pos.z(), b, meta);
        }
        return;
      }
      if (b == Block.LEVER) {
        world.set(pos.x(), pos.y(), pos.z(), b, world.data(pos.x(), pos.y(), pos.z()) ^ 8);
        return;
      }
      if (b == Block.BED) {
        if (world.daylight() < .5) {
          world.time = world.time / 24000 * 24000 + 30000;
          p.health = Math.min(20, p.health + 5);
          notify("Sleeping until morning");
        } else notify("You can only sleep at night");
        world.spawnX = pos.x();
        world.spawnY = pos.y() + 1;
        world.spawnZ = pos.z();
        return;
      }
    }
    if (held.id() == 325) {
      for (double d = 0; d <= 5; d += .1) {
        double yaw = Math.toRadians(p.yaw), pitch = Math.toRadians(p.pitch);
        int x = (int) Math.floor(p.x - Math.sin(yaw) * Math.cos(pitch) * d),
            y = (int) Math.floor(p.eyeY() - Math.sin(pitch) * d),
            z = (int) Math.floor(p.z - Math.cos(yaw) * Math.cos(pitch) * d);
        Block fluid = world.block(x, y, z);
        if (fluid.fluid() && world.data(x, y, z) == 0) {
          world.set(x, y, z, Block.AIR, 0);
          if (!p.creative) {
            p.held(held.withCount(held.count() - 1));
            p.inventory.add(new Stack(fluid.lava() ? 327 : 326, 1));
          }
          audio.play(Audio.Sound.SPLASH);
          return;
        }
      }
      return;
    }
    if (Items.get(held.id()).tool().equals("hoe")
        && (b == Block.GRASS || b == Block.DIRT)
        && world.block(pos.x(), pos.y() + 1, pos.z()) == Block.AIR) {
      world.set(pos.x(), pos.y(), pos.z(), Block.FARMLAND, 0);
      p.held(held.hurt());
      return;
    }
    World.Pos place = hit.adjacent();
    Block existing = world.block(place.x(), place.y(), place.z());
    if (existing != Block.AIR && !existing.plant() && !existing.fluid()) return;
    int id = held.id() == 326 ? 9 : held.id() == 327 ? 11 : Items.placedBlock(held.id());
    if (held.id() == 295 && b == Block.FARMLAND) id = 59;
    if (id == 0 || held.empty()) return;
    Block placed = Block.of(id);
    if (place.y() < 0 || place.y() >= 128) return;
    if (placed.solid
        && new dev.opencraft.physics.Aabb(
                place.x(),
                place.y(),
                place.z(),
                place.x() + 1,
                place.y() + placed.height(),
                place.z() + 1)
            .intersects(p.bounds())) return;
    if (placed == Block.WOOD_DOOR || placed == Block.IRON_DOOR) {
      if (world.block(place.x(), place.y() + 1, place.z()) != Block.AIR || place.y() > 126) return;
      world.set(place.x(), place.y() + 1, place.z(), placed, 8);
    }
    world.set(place.x(), place.y(), place.z(), placed, 0);
    if (placed == Block.CHEST || placed == Block.FURNACE) world.tile(place);
    if (!p.creative) {
      if (held.id() == 326 || held.id() == 327) p.held(new Stack(325, 1));
      else p.held(held.withCount(held.count() - 1));
    }
    audio.play(Audio.Sound.STEP);
    stat("Blocks placed", 1);
  }

  private void pick() {
    if (world == null) return;
    World.Hit hit = world.target();
    if (hit == null) return;
    int id = world.block(hit.pos().x(), hit.pos().y(), hit.pos().z()).id;
    Player p = world.player;
    for (int i = 0; i < 9; i++)
      if (p.inventory.get(i).id() == id) {
        selectSlot(i);
        return;
      }
    if (p.creative) p.held(new Stack(id, 64));
  }

  public void selectSlot(int slot) {
    if (world == null) return;
    world.player.selected = slot;
    if (client != null && world.remote) client.select(slot);
  }

  public void captureMouse(boolean capture) {
    captured = capture;
    firstMouse = true;
    glfwSetInputMode(window, GLFW_CURSOR, capture ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    if (glfwRawMouseMotionSupported())
      glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, capture ? GLFW_TRUE : GLFW_FALSE);
  }

  public void applyVsync() {
    glfwSwapInterval(options.vsync ? 1 : 0);
  }

  public void toggleFullscreen() {
    options.fullscreen = !options.fullscreen;
    applyFullscreen();
  }

  private void applyFullscreen() {
    long monitor = options.fullscreen ? glfwGetPrimaryMonitor() : NULL;
    GLFWVidMode mode = monitor != NULL ? glfwGetVideoMode(monitor) : null;
    if (mode != null)
      glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
    else glfwSetWindowMonitor(window, NULL, 80, 80, 1100, 720, GLFW_DONT_CARE);
  }

  public void createWorld(String name, String seedText) {
    guard(
        () -> {
          long seed;
          if (seedText.isBlank()) seed = new Random().nextLong();
          else
            try {
              seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
              seed = seedText.hashCode();
            }
          loadWorld(store.create(name, seed, newType, newCreative));
        });
  }

  public void loadWorld(WorldStore.Save save) {
    guard(
        () -> {
          if (world != null) world.save();
          disconnect();
          world = new World(store, save);
          world.player.creative = save.creative();
          world.difficulty = options.difficulty;
          world.loadAround(1);
          renderer.invalidate();
          ui.enterGame();
          notify("Loaded " + save.name());
        });
  }

  public void leaveWorld() {
    guard(
        () -> {
          ui.closeInventory();
          if (world != null) world.save();
          saveStats();
          disconnect();
          world = null;
          renderer.invalidate();
          ui.title();
        });
  }

  public void respawn() {
    if (world == null) return;
    if (world.remote && client != null) client.respawn();
    else {
      Player p = world.player;
      for (int i = 0; i < p.inventory.size(); i++) {
        world.drop(p.x, p.y, p.z, p.inventory.get(i));
        p.inventory.set(i, Stack.EMPTY);
      }
      p.respawn(world);
    }
    ui.enterGame();
  }

  public void connect(String address) {
    LegacyClient.Address.parse(address);
    disconnect();
    ui.connecting();
    client =
        new LegacyClient(
            address,
            options.username,
            w -> {
              world = w;
              renderer.invalidate();
              ui.enterGame();
            },
            ui::message,
            error -> {
              world = null;
              renderer.invalidate();
              ui.error(error);
            });
  }

  public void disconnect() {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  public void chat(String text) {
    if (world != null && world.remote && client != null) {
      client.chat(text);
      return;
    }
    Player p = world == null ? null : world.player;
    if (text.startsWith("/") && p != null) {
      String[] parts = text.split("\\s+");
      guard(
          () -> {
            switch (parts[0]) {
              case "/time" -> {
                if (parts.length < 2) throw new IllegalArgumentException("/time day|night|ticks");
                world.time =
                    parts[1].equals("day")
                        ? 6000
                        : parts[1].equals("night") ? 18000 : Long.parseLong(parts[1]);
              }
              case "/gamemode" -> {
                if (parts.length < 2)
                  throw new IllegalArgumentException("/gamemode survival|creative");
                p.creative = parts[1].equals("creative") || parts[1].equals("1");
              }
              case "/give" -> {
                if (parts.length < 2) throw new IllegalArgumentException("/give itemId [count]");
                p.inventory.add(
                    new Stack(
                        Integer.parseInt(parts[1]),
                        parts.length > 2
                            ? Math.max(1, Math.min(64, Integer.parseInt(parts[2])))
                            : 64));
              }
              case "/seed" -> ui.message("Seed: " + world.generator.seed());
              case "/tp" -> {
                if (parts.length != 4) throw new IllegalArgumentException("/tp x y z");
                double x = Double.parseDouble(parts[1]),
                    y = Double.parseDouble(parts[2]),
                    z = Double.parseDouble(parts[3]);
                if (!Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(z)
                    || Math.abs(x) > 30_000_000
                    || Math.abs(z) > 30_000_000)
                  throw new IllegalArgumentException("Invalid position");
                p.x = x;
                p.y = y;
                p.z = z;
                p.vx = p.vy = p.vz = 0;
              }
              case "/help" -> ui.message("/give /gamemode /time /tp /seed");
              default -> ui.message("Unknown command. Use /help.");
            }
          });
    } else ui.message("<" + options.username + "> " + text);
  }

  public void stat(String name, long value) {
    stats.merge(name, value, Long::sum);
  }

  private void achieve(String name) {
    if (achievements.add(name)) {
      notify("Achievement: " + name);
      audio.play(Audio.Sound.PICKUP);
    }
  }

  public void unlockFor(int item) {
    switch (item) {
      case 58 -> achieve("Benchmarking");
      case 270 -> achieve("Time to Mine!");
      case 61 -> achieve("Hot Topic");
      default -> {}
    }
  }

  public void notify(String text) {
    toast = text;
    toastTicks = 80;
  }

  public void saveOptions() {
    guard(options::save);
  }

  public void guard(Task task) {
    try {
      task.run();
    } catch (Exception e) {
      e.printStackTrace(System.err);
      ui.error(e.getMessage());
    }
  }

  public void quit() {
    running = false;
  }

  private void loadStats() throws IOException {
    Path file = data.resolve("statistics.properties");
    if (!Files.exists(file)) return;
    Properties p = new Properties();
    try (var in = Files.newInputStream(file)) {
      p.load(in);
    }
    for (String key : p.stringPropertyNames())
      if (key.startsWith("achievement.")) achievements.add(key.substring(12));
      else
        try {
          stats.put(key, Long.parseLong(p.getProperty(key)));
        } catch (NumberFormatException ignored) {
        }
  }

  private void saveStats() throws IOException {
    Properties p = new Properties();
    for (var e : stats.entrySet()) p.setProperty(e.getKey(), Long.toString(e.getValue()));
    for (String a : achievements) p.setProperty("achievement." + a, "true");
    Path tmp = Files.createTempFile(data, "statistics-", ".tmp");
    try {
      try (var out = Files.newOutputStream(tmp)) {
        p.store(out, "Statistics and achievements");
      }
      Files.move(tmp, data.resolve("statistics.properties"), StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  private void smokeStep(int frame) throws Exception {
    if (frame == 2) {
      smokeClick((renderer.canvas.width) / 2., renderer.canvas.height / 4. + 20);
      requireSmoke(ui.screen == Ui.Screen.WORLDS, "Singleplayer button");
    }
    if (frame == 3) {
      smokeClick(30, renderer.canvas.height - 24);
      requireSmoke(ui.screen == Ui.Screen.CREATE, "Create world submenu");
    }
    if (frame == 4) {
      int left = (renderer.canvas.width - 200) / 2;
      smokeClick(left + 100, 163);
      requireSmoke(newType == Generator.Type.SKYBLOCK, "World type toggle");
      smokeClick(left + 100, 94);
      requireSmoke(newCreative, "Game mode toggle");
      smokeClick(left + 100, 64);
      ui.key(GLFW_KEY_A, GLFW_MOD_CONTROL);
      for (int code : "Render Validation".codePoints().toArray()) ui.character(code);
      smokeClick(left + 100, renderer.canvas.height - 42);
      requireSmoke(world != null && ui.game(), "Create world button");
    }
    if (frame == 5 && world == null) {
      newType = Generator.Type.SKYBLOCK;
      newCreative = true;
      createWorld("Render validation", "12345");
      world.player.inventory.set(0, new Stack(1, 64));
      world.player.inventory.set(1, new Stack(17, 8));
      world.player.yaw = 35;
      world.player.pitch = 30;
    }
    if (frame == 6) {
      world.player.inventory.set(0, new Stack(1, 64));
      world.player.inventory.set(1, new Stack(17, 8));
      world.player.yaw = 35;
      world.player.pitch = 30;
    }
    if (frame == 26 || frame == 27) {
      boolean before = options.fancy;
      smokeClick((renderer.canvas.width - 300) / 2. + 70, 45);
      requireSmoke(options.fancy != before, "Graphics toggle");
    }
    if (frame == 28) {
      smokeClick(renderer.canvas.width / 2. + 72, 69);
      requireSmoke(options.anaglyph, "Stereo anaglyph toggle");
    }
    if (frame == 30) options.anaglyph = false;
    if (frame == 31) {
      smokeClick(renderer.canvas.width - 96, 45);
      ui.key(GLFW_KEY_R, 0);
      requireSmoke(options.keys.get("Forward") == GLFW_KEY_R, "Key rebinding");
      smokeClick(renderer.canvas.width - 96, 45);
      ui.key(GLFW_KEY_W, 0);
    }
    if (frame == 36) {
      smokeClick((renderer.canvas.width - 200) / 2. + 50, 72);
      ui.mouse(GLFW_MOUSE_BUTTON_LEFT, false, false);
      requireSmoke(Math.abs(options.master - .25) < .01, "Volume slider");
    }
    if (frame == 41) {
      smokeClick(renderer.canvas.width / 2., 100);
      requireSmoke(options.language.equals("Deutsch"), "Language selection");
      smokeClick(renderer.canvas.width / 2., 50);
    }
    if (frame == 56) {
      int x = (renderer.canvas.width - 176) / 2, y = (renderer.canvas.height - 180) / 2;
      smokeClick(x + 34, y + 151);
      smokeClick(x + 32, y + 22, GLFW_MOUSE_BUTTON_RIGHT);
      smokeClick(x + 34, y + 151);
      smokeClick(x + 146, y + 32);
      smokeClick(x + 46, y + 151);
      requireSmoke(world.player.inventory.count(5) == 4, "Crafting UI transaction");
    }
    if (frame == 61) {
      int x = (renderer.canvas.width - 176) / 2, y = (renderer.canvas.height - 166) / 2;
      smokeClick(x + 10, y + 20);
      smokeClick(x + 64, y + 144);
      requireSmoke(world.player.inventory.count(327) == 1, "Chest item transfer");
      smokeClick(x + 64, y + 144);
      smokeClick(x + 10, y + 20);
    }
    if (frame == 66) {
      for (int i = 0; i < 200; i++) world.tick();
      int x = (renderer.canvas.width - 176) / 2, y = (renderer.canvas.height - 166) / 2;
      smokeClick(x + 118, y + 38);
      smokeClick(x + 82, y + 144);
      requireSmoke(world.player.inventory.count(265) == 1, "Furnace output transfer");
    }
    if (frame == 121) {
      leaveWorld();
      ui.open(Ui.Screen.WORLDS);
    }
    if (frame == 122) smokeClick(30, 45);
    if (frame == 123) {
      int half = (renderer.canvas.width - 48) / 4;
      smokeClick(24 + half + half / 2., renderer.canvas.height - 24);
      requireSmoke(ui.screen == Ui.Screen.RENAME, "Rename world submenu");
    }
    if (frame == 124) {
      smokeReplaceField(renderer.canvas.width / 2., 90, "Renamed Validation");
      smokeClick(renderer.canvas.width / 2., 122);
      requireSmoke(
          store.list().getFirst().name().equals("Renamed Validation"), "World rename persistence");
    }
    if (frame == 125) {
      int half = (renderer.canvas.width - 48) / 4;
      smokeClick(28 + half * 2 + half / 2., renderer.canvas.height - 24);
      requireSmoke(ui.screen == Ui.Screen.CONFIRM, "Delete confirmation submenu");
    }
    if (frame == 126) {
      int before = store.list().size();
      smokeClick((renderer.canvas.width - 200) / 2. + 49, 140);
      requireSmoke(store.list().size() == before - 1, "World deletion");
    }
    if (frame == 127) ui.open(Ui.Screen.MULTIPLAYER);
    if (frame == 128) {
      smokeClick((renderer.canvas.width - 200) / 2. + 32, renderer.canvas.height - 43);
      requireSmoke(ui.screen == Ui.Screen.SERVER_FORM, "Add server submenu");
    }
    if (frame == 129) {
      smokeReplaceField(renderer.canvas.width / 2., 68, "Local Fixture");
      smokeReplaceField(renderer.canvas.width / 2., 112, "127.0.0.1:25565");
      smokeClick(renderer.canvas.width / 2., renderer.canvas.height - 42);
      requireSmoke(
          servers.entries.stream().anyMatch(s -> s.name().equals("Local Fixture")),
          "Add server persistence");
    }
    if (frame == 130) smokeClick(30, 45);
    if (frame == 131) {
      smokeClick((renderer.canvas.width - 200) / 2. + 100, renderer.canvas.height - 43);
      requireSmoke(ui.screen == Ui.Screen.SERVER_FORM, "Edit server submenu");
    }
    if (frame == 132) {
      smokeReplaceField(renderer.canvas.width / 2., 68, "Edited Fixture");
      smokeClick(renderer.canvas.width / 2., renderer.canvas.height - 42);
      requireSmoke(
          servers.entries.getFirst().name().equals("Edited Fixture"), "Edit server persistence");
    }
    if (frame == 133)
      smokeClick((renderer.canvas.width - 200) / 2. + 168, renderer.canvas.height - 43);
    if (frame == 134) {
      int before = servers.entries.size();
      smokeClick((renderer.canvas.width - 200) / 2. + 49, 140);
      requireSmoke(servers.entries.size() == before - 1, "Server deletion");
    }
    if (frame == 135) {
      Path packs = data.resolve("texturepacks");
      Files.createDirectories(packs);
      var tile =
          new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) tile.setRGB(x, y, 0xff3273b9);
      try (var zip =
          new java.util.zip.ZipOutputStream(Files.newOutputStream(packs.resolve("fixture.zip")))) {
        zip.putNextEntry(new java.util.zip.ZipEntry("tiles/1.png"));
        javax.imageio.ImageIO.write(tile, "png", zip);
        zip.closeEntry();
      }
      ui.open(Ui.Screen.PACKS);
    }
    if (frame == 136) {
      smokeClick(30, 76);
      requireSmoke(options.texturePack.equals("fixture.zip"), "Texture pack selection");
      Path atlas = data.resolve("pack-validation.png");
      renderer.assets.export(atlas);
      requireSmoke(
          javax.imageio.ImageIO.read(atlas.toFile()).getRGB(24, 8) == 0xff3273b9,
          "Texture pack material replacement");
    }
    if (frame == 137) {
      smokeClick(30, 46);
      requireSmoke(options.texturePack.isEmpty(), "Default texture restoration");
    }
    if (frame == 138) ui.title();
    if (frame == 139) {
      newCreative = false;
      newType = Generator.Type.DEFAULT;
      createWorld("Survival validation", "12345");
      world.player.inventory.set(0, new Stack(270, 1));
      world.player.armor.set(0, new Stack(306, 1));
      world.player.armor.set(3, new Stack(309, 1));
      ui.inventory(Ui.Screen.INVENTORY, null);
      requireSmoke(
          !world.player.creative && ui.screen == Ui.Screen.INVENTORY, "Survival inventory");
    }
    if (frame == 140) {
      ui.key(options.keys.get("Inventory"), 0);
      requireSmoke(ui.game(), "Inventory binding closes container");
    }
    if (frame >= 15 && frame % 5 == 0) {
      Ui.Screen[] screens = {
        Ui.Screen.PAUSE,
        Ui.Screen.OPTIONS,
        Ui.Screen.VIDEO,
        Ui.Screen.CONTROLS,
        Ui.Screen.AUDIO,
        Ui.Screen.LANGUAGE,
        Ui.Screen.PACKS,
        Ui.Screen.INVENTORY,
        Ui.Screen.CRAFTING,
        Ui.Screen.CHEST,
        Ui.Screen.FURNACE,
        Ui.Screen.DEATH,
        Ui.Screen.WORLDS,
        Ui.Screen.CREATE,
        Ui.Screen.MULTIPLAYER,
        Ui.Screen.DIRECT,
        Ui.Screen.SERVER_FORM,
        Ui.Screen.CHAT,
        Ui.Screen.STATS,
        Ui.Screen.ACHIEVEMENTS,
        Ui.Screen.GAME
      };
      int index = (frame - 15) / 5;
      if (index < screens.length) {
        Ui.Screen next = screens[index];
        if (next == Ui.Screen.CHEST) {
          var pos = new World.Pos(2, 65, -2);
          ui.inventory(next, pos);
        } else if (next == Ui.Screen.FURNACE) {
          var pos = new World.Pos(0, 65, -2);
          world.set(pos.x(), pos.y(), pos.z(), Block.FURNACE, 0);
          var t = world.tile(pos);
          t.inventory.set(0, new Stack(15, 2));
          t.inventory.set(1, new Stack(263, 1));
          ui.inventory(next, pos);
        } else if (next == Ui.Screen.INVENTORY || next == Ui.Screen.CRAFTING)
          ui.inventory(next, null);
        else ui.open(next);
      }
    }
  }

  private void smokeClick(double x, double y) {
    smokeClick(x, y, GLFW_MOUSE_BUTTON_LEFT);
  }

  private void smokeReplaceField(double x, double y, String value) {
    smokeClick(x, y);
    ui.key(GLFW_KEY_A, GLFW_MOD_CONTROL);
    for (int point : value.codePoints().toArray()) ui.character(point);
  }

  private void smokeClick(double x, double y, int button) {
    ui.move(x, y);
    ui.mouse(button, true, false);
    ui.mouse(button, false, false);
  }

  private static void requireSmoke(boolean value, String check) {
    if (!value) throw new IllegalStateException("Render interaction failed: " + check);
    System.out.println("UI_SMOKE_OK: " + check);
  }

  public void close() throws Exception {
    Exception failure = null;
    try {
      if (ui != null) ui.closeInventory();
      if (world != null) world.save();
      options.save();
      saveStats();
    } catch (Exception e) {
      failure = e;
    } finally {
      disconnect();
      audio.close();
      renderer.close();
      org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window);
      glfwDestroyWindow(window);
      glfwTerminate();
      errorCallback.free();
    }
    if (failure != null) throw failure;
  }
}
