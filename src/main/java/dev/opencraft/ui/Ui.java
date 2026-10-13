package dev.opencraft.ui;

import static org.lwjgl.glfw.GLFW.*;

import dev.opencraft.*;
import dev.opencraft.audio.Audio;
import dev.opencraft.game.*;
import dev.opencraft.game.Stack;
import dev.opencraft.net.*;
import dev.opencraft.render.*;
import dev.opencraft.world.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

public final class Ui {
  public enum Screen {
    TITLE,
    GAME,
    WORLDS,
    CREATE,
    RENAME,
    CONFIRM,
    MULTIPLAYER,
    DIRECT,
    SERVER_FORM,
    CONNECTING,
    OPTIONS,
    VIDEO,
    CONTROLS,
    AUDIO,
    LANGUAGE,
    PACKS,
    PAUSE,
    INVENTORY,
    CRAFTING,
    CHEST,
    FURNACE,
    DEATH,
    CHAT,
    ERROR,
    STATS,
    ACHIEVEMENTS
  }

  private record Widget(
      String id,
      float x,
      float y,
      float w,
      float h,
      Runnable action,
      DoubleConsumer slider,
      String field,
      boolean enabled) {
    boolean contains(double mx, double my) {
      return mx >= x && my >= y && mx < x + w && my < y + h;
    }
  }

  private record Slot(
      Inventory inventory,
      int index,
      float x,
      float y,
      boolean output,
      boolean craft,
      boolean palette) {}

  private final App app;
  public Screen screen = Screen.TITLE;
  private final Deque<Screen> history = new ArrayDeque<>();
  private final List<Widget> widgets = new ArrayList<>();
  private final List<Slot> slots = new ArrayList<>();
  private final Map<String, String> fields = new HashMap<>();
  private final List<String> chat = new ArrayList<>();
  private final Map<String, String> statuses = new HashMap<>();
  private List<WorldStore.Save> saves = List.of();
  private List<Path> packs = List.of();
  private List<Items.Item> palette = List.of();
  private int selectedWorld = -1, selectedServer = -1, scroll;
  private String focus = "", drag = "", rebinding = "", error = "", confirmText = "";
  private boolean selectAll, editServer;
  private Runnable confirmAction = () -> {}, primary;
  private Screen errorBack = Screen.TITLE;
  private Inventory crafting = new Inventory(4);
  private Stack cursor = Stack.EMPTY;
  private World.Pos container;
  private Canvas c;
  private int w, h;
  private double mx, my;
  private long ticks;

  public Ui(App app) {
    this.app = app;
    fields.put("worldName", "New World");
    fields.put("seed", "");
    fields.put("serverName", "Minecraft Server");
    fields.put("address", "localhost:25565");
    fields.put("chat", "");
    fields.put("search", "");
    fields.put("username", app.options.username);
  }

  public boolean game() {
    return screen == Screen.GAME;
  }

  public boolean pauses() {
    return app.world != null
        && !app.world.remote
        && switch (screen) {
          case PAUSE,
              OPTIONS,
              VIDEO,
              CONTROLS,
              AUDIO,
              LANGUAGE,
              PACKS,
              STATS,
              ACHIEVEMENTS,
              CONFIRM ->
              true;
          default -> false;
        };
  }

  public void open(Screen next) {
    if (screen != next) history.push(screen);
    screen = next;
    scroll = 0;
    focus = drag = rebinding = "";
    widgets.clear();
    slots.clear();
    app.captureMouse(next == Screen.GAME);
    if (next == Screen.WORLDS) refreshWorlds();
    if (next == Screen.PACKS) refreshPacks();
  }

  public void title() {
    closeInventory();
    history.clear();
    screen = Screen.TITLE;
    focus = "";
    app.captureMouse(false);
  }

  public void enterGame() {
    history.clear();
    screen = Screen.GAME;
    focus = "";
    app.captureMouse(true);
  }

  public void death() {
    closeInventory();
    history.clear();
    screen = Screen.DEATH;
    app.captureMouse(false);
  }

  public void message(String text) {
    chat.add(text);
    if (chat.size() > 50) chat.removeFirst();
  }

  public void error(String text) {
    error = text == null ? "Unknown error" : text;
    errorBack = app.world == null ? Screen.TITLE : Screen.PAUSE;
    open(Screen.ERROR);
  }

  public void connecting() {
    open(Screen.CONNECTING);
  }

  public void inventory(Screen type, World.Pos pos) {
    container = pos;
    crafting = new Inventory(type == Screen.CRAFTING ? 9 : 4);
    cursor = Stack.EMPTY;
    open(type);
  }

  public void closeInventory() {
    if (app.world == null) return;
    Player p = app.world.player;
    cursor = returnStack(p, cursor);
    for (int i = 0; i < crafting.size(); i++) {
      returnStack(p, crafting.get(i));
      crafting.set(i, Stack.EMPTY);
    }
    container = null;
  }

  private Stack returnStack(Player p, Stack s) {
    Stack left = p.inventory.add(s);
    if (!left.empty()) app.world.drop(p.x, p.y + 1, p.z, left);
    return Stack.EMPTY;
  }

  public void back() {
    if (screen == Screen.GAME) {
      open(Screen.PAUSE);
      return;
    }
    if (screen == Screen.CONNECTING) {
      app.disconnect();
      screen = Screen.MULTIPLAYER;
      app.captureMouse(false);
      return;
    }
    if (screen == Screen.DEATH) return;
    if (screen == Screen.INVENTORY
        || screen == Screen.CRAFTING
        || screen == Screen.CHEST
        || screen == Screen.FURNACE) {
      closeInventory();
      enterGame();
      return;
    }
    if (screen == Screen.CHAT) {
      enterGame();
      return;
    }
    if (screen == Screen.ERROR) {
      screen = errorBack;
      history.clear();
      return;
    }
    if (screen == Screen.TITLE) {
      app.quit();
      return;
    }
    Screen previous =
        history.isEmpty() ? app.world == null ? Screen.TITLE : Screen.GAME : history.pop();
    screen = previous;
    focus = rebinding = drag = "";
    scroll = 0;
    app.captureMouse(screen == Screen.GAME);
    app.saveOptions();
  }

  public void render(Canvas canvas, double mouseX, double mouseY) {
    c = canvas;
    w = c.width;
    h = c.height;
    mx = mouseX;
    my = mouseY;
    ticks++;
    widgets.clear();
    slots.clear();
    primary = null;
    if (screen == Screen.GAME) {
      hud();
      return;
    }
    if (screen == Screen.CHAT) {
      hud();
      c.rect(0, h - 27, w, 27, 0xb0000000);
      field("chat", 2, h - 20, w - 4, fields.get("chat"));
      focus = "chat";
      drawChat(12);
      primary = this::submitChat;
      return;
    }
    if (screen == Screen.INVENTORY
        || screen == Screen.CRAFTING
        || screen == Screen.CHEST
        || screen == Screen.FURNACE) {
      hud();
      container();
      return;
    }
    if (screen == Screen.PAUSE || screen == Screen.DEATH || app.world != null) {
      c.rect(0, 0, w, h, screen == Screen.DEATH ? 0xaa600000 : 0x99000000);
    } else if (screen != Screen.TITLE) c.dirt();
    int left = (w - 200) / 2, top = Math.max(50, h / 4);
    switch (screen) {
      case TITLE -> {
        c.rect(0, 0, w, h, 0x33000000);
        String title = "OPENCRAFT";
        c.text(title, (w - BitmapFont.width(title, 3)) / 2f, 27, 0xffd5d5d5, 3);
        c.centered("Java 1.0 recreation project", 54, 0xffffff55);
        button("Singleplayer", left, top + 10, 200, 20, () -> open(Screen.WORLDS));
        button("Multiplayer", left, top + 34, 200, 20, () -> open(Screen.MULTIPLAYER));
        button("Mods / Texture Packs", left, top + 58, 200, 20, () -> open(Screen.PACKS));
        button("Options...", left, top + 92, 98, 20, () -> open(Screen.OPTIONS));
        button("Quit Game", left + 102, top + 92, 98, 20, app::quit);
        c.text("OpenCraft 0.1.0", 2, h - 10, 0xffffffff);
        c.text("Procedural assets", w - 106, h - 10, 0xffcccccc);
      }
      case WORLDS -> {
        heading("Select World");
        int listTop = 35, listBottom = h - 64, rowH = 32;
        int visible = Math.max(1, (listBottom - listTop) / rowH);
        clampScroll(saves.size(), visible);
        for (int i = scroll; i < Math.min(saves.size(), scroll + visible); i++) {
          WorldStore.Save s = saves.get(i);
          int y = listTop + (i - scroll) * rowH;
          int index = i;
          listEntry(
              "world" + i,
              s.name(),
              s.type().label() + " / " + (s.creative() ? "Creative" : "Survival"),
              20,
              y,
              w - 40,
              rowH - 2,
              selectedWorld == i,
              () -> selectedWorld = index);
        }
        if (saves.isEmpty()) c.centered("No worlds yet. Create one below.", 70, 0xffcccccc);
        button(
            "Play Selected World",
            left,
            h - 58,
            200,
            20,
            selectedWorld >= 0,
            () -> app.loadWorld(saves.get(selectedWorld)));
        int half = (w - 48) / 4;
        button(
            "Create New",
            20,
            h - 34,
            half,
            20,
            () -> {
              fields.put("worldName", "New World");
              fields.put("seed", "");
              open(Screen.CREATE);
            });
        button(
            "Rename",
            24 + half,
            h - 34,
            half,
            20,
            selectedWorld >= 0,
            () -> {
              fields.put("rename", saves.get(selectedWorld).name());
              open(Screen.RENAME);
            });
        button(
            "Delete",
            28 + half * 2,
            h - 34,
            half,
            20,
            selectedWorld >= 0,
            () ->
                confirm(
                    "Delete this world permanently?",
                    () -> {
                      app.store.delete(saves.get(selectedWorld));
                      selectedWorld = -1;
                      refreshWorlds();
                      screen = Screen.WORLDS;
                    }));
        button("Cancel", 32 + half * 3, h - 34, half, 20, this::back);
        primary = selectedWorld < 0 ? null : () -> app.loadWorld(saves.get(selectedWorld));
      }
      case CREATE -> {
        heading("Create New World");
        label("World Name", left, 44);
        field("worldName", left, 56, 200, fields.get("worldName"));
        button(
            "Game Mode: " + (app.newCreative ? "Creative" : "Survival"),
            left,
            84,
            200,
            20,
            () -> app.newCreative = !app.newCreative);
        label("Seed (blank for random)", left, 112);
        field("seed", left, 124, 200, fields.get("seed"));
        button(
            "World Type: " + app.newType.label(),
            left,
            153,
            200,
            20,
            () ->
                app.newType =
                    app.newType == Generator.Type.DEFAULT
                        ? Generator.Type.SKYBLOCK
                        : Generator.Type.DEFAULT);
        c.centered(
            app.newType == Generator.Type.SKYBLOCK
                ? "Floating island in an infinite void"
                : "Procedural biomes, terrain and caves",
            184,
            0xffaaaaaa);
        button(
            "Create New World",
            left,
            h - 52,
            200,
            20,
            () -> app.createWorld(fields.get("worldName"), fields.get("seed")));
        button("Cancel", left, h - 28, 200, 20, this::back);
        primary = () -> app.createWorld(fields.get("worldName"), fields.get("seed"));
      }
      case RENAME -> {
        heading("Rename World");
        label("World Name", left, 65);
        field("rename", left, 80, 200, fields.getOrDefault("rename", ""));
        button(
            "Rename",
            left,
            112,
            200,
            20,
            () -> {
              app.store.rename(saves.get(selectedWorld), fields.get("rename"));
              refreshWorlds();
              back();
            });
        button("Cancel", left, 136, 200, 20, this::back);
      }
      case CONFIRM -> {
        heading("Are you sure?");
        wrapped(confirmText, 20, 70, w - 40, 0xffffffff);
        button(
            "Yes",
            left,
            130,
            98,
            20,
            () -> {
              Screen previous = history.isEmpty() ? Screen.TITLE : history.pop();
              screen = previous;
              confirmAction.run();
            });
        button("No", left + 102, 130, 98, 20, this::back);
      }
      case MULTIPLAYER -> {
        heading("Play Multiplayer");
        int listTop = 35, rowH = 34, visible = Math.max(1, (h - 115) / rowH);
        clampScroll(app.servers.entries.size(), visible);
        for (int i = scroll; i < Math.min(app.servers.entries.size(), scroll + visible); i++) {
          var s = app.servers.entries.get(i);
          int index = i;
          listEntry(
              "server" + i,
              s.name(),
              s.address() + " " + statuses.getOrDefault(s.address(), ""),
              20,
              listTop + (i - scroll) * rowH,
              w - 40,
              rowH - 2,
              i == selectedServer,
              () -> selectedServer = index);
        }
        button(
            "Join Server",
            left,
            h - 77,
            98,
            20,
            selectedServer >= 0,
            () -> join(app.servers.entries.get(selectedServer).address()));
        button("Direct Connect", left + 102, h - 77, 98, 20, () -> open(Screen.DIRECT));
        button(
            "Add Server",
            left,
            h - 53,
            64,
            20,
            () -> {
              editServer = false;
              fields.put("serverName", "Minecraft Server");
              fields.put("address", "localhost:25565");
              open(Screen.SERVER_FORM);
            });
        button(
            "Edit",
            left + 68,
            h - 53,
            64,
            20,
            selectedServer >= 0,
            () -> {
              editServer = true;
              var s = app.servers.entries.get(selectedServer);
              fields.put("serverName", s.name());
              fields.put("address", s.address());
              open(Screen.SERVER_FORM);
            });
        button(
            "Delete",
            left + 136,
            h - 53,
            64,
            20,
            selectedServer >= 0,
            () ->
                confirm(
                    "Remove this server from the list?",
                    () -> {
                      app.servers.entries.remove(selectedServer);
                      app.servers.save();
                      selectedServer = -1;
                    }));
        button("Refresh", left, h - 29, 98, 20, this::pingServers);
        button("Cancel", left + 102, h - 29, 98, 20, this::back);
      }
      case DIRECT, SERVER_FORM -> {
        heading(
            screen == Screen.DIRECT ? "Direct Connect" : editServer ? "Edit Server" : "Add Server");
        if (screen == Screen.SERVER_FORM) {
          label("Server Name", left, 45);
          field("serverName", left, 58, 200, fields.get("serverName"));
        }
        label("Server Address", left, 89);
        field("address", left, 102, 200, fields.get("address"));
        label("Offline-mode username", left, 133);
        field("username", left, 146, 200, fields.get("username"));
        App.Task done =
            screen == Screen.DIRECT
                ? () -> join(fields.get("address"))
                : () -> {
                  LegacyClient.Address.parse(fields.get("address"));
                  var s = new Servers.Server(fields.get("serverName"), fields.get("address"));
                  if (editServer) app.servers.entries.set(selectedServer, s);
                  else app.servers.entries.add(s);
                  app.servers.save();
                  back();
                };
        button(screen == Screen.DIRECT ? "Join Server" : "Done", left, h - 52, 200, 20, done);
        button("Cancel", left, h - 28, 200, 20, this::back);
        primary = () -> app.guard(done);
        c.centered("Legacy 1.0 protocol / offline-mode only", 180, 0xffaaaaaa);
      }
      case CONNECTING -> {
        heading("Connecting to server...");
        c.centered("Connecting / reading legacy handshake", 90, 0xffffffff);
        button("Cancel", left, 145, 200, 20, this::back);
      }
      case OPTIONS -> {
        heading("Options");
        slider(
            "sensitivity",
            "Sensitivity",
            left,
            40,
            200,
            app.options.sensitivity,
            v -> app.options.sensitivity = (float) v);
        slider(
            "fov",
            "FOV",
            left,
            64,
            200,
            (app.options.fov - 30) / 80,
            v -> app.options.fov = (float) (30 + v * 80));
        button(
            "Difficulty: "
                + new String[] {"Peaceful", "Easy", "Normal", "Hard"}[app.options.difficulty],
            left,
            88,
            200,
            20,
            () -> {
              app.options.difficulty = (app.options.difficulty + 1) % 4;
              if (app.world != null) app.world.difficulty = app.options.difficulty;
            });
        button("Music & Sounds...", left, 112, 98, 20, () -> open(Screen.AUDIO));
        button("Video Settings...", left + 102, 112, 98, 20, () -> open(Screen.VIDEO));
        button("Controls...", left, 136, 98, 20, () -> open(Screen.CONTROLS));
        button("Language...", left + 102, 136, 98, 20, () -> open(Screen.LANGUAGE));
        button("Texture Packs...", left, 160, 200, 20, () -> open(Screen.PACKS));
        done();
      }
      case VIDEO -> {
        heading("Video Settings");
        int wide = Math.min(300, w - 20), x = (w - wide) / 2, col = (wide - 4) / 2;
        String[] distances = {"Tiny", "Short", "Normal", "Far"};
        button(
            "Graphics: " + on(app.options.fancy, "Fancy", "Fast"),
            x,
            35,
            col,
            20,
            () -> {
              app.options.fancy = !app.options.fancy;
              app.renderer.invalidate();
            });
        button(
            "Render Distance: " + distances[app.options.renderDistance],
            x + col + 4,
            35,
            col,
            20,
            () -> app.options.renderDistance = (app.options.renderDistance + 1) % 4);
        toggle(
            "Smooth Lighting",
            app.options.smoothLight,
            x,
            59,
            col,
            () -> {
              app.options.smoothLight = !app.options.smoothLight;
              app.renderer.invalidate();
            });
        toggle(
            "3D Anaglyph",
            app.options.anaglyph,
            x + col + 4,
            59,
            col,
            () -> app.options.anaglyph = !app.options.anaglyph);
        button(
            "GUI Scale: " + new String[] {"Auto", "Small", "Normal", "Large"}[app.options.guiScale],
            x,
            83,
            col,
            20,
            () -> app.options.guiScale = (app.options.guiScale + 1) % 4);
        toggle(
            "Advanced OpenGL",
            app.options.advancedOpenGL,
            x + col + 4,
            83,
            col,
            () -> app.options.advancedOpenGL = !app.options.advancedOpenGL);
        toggle(
            "VSync",
            app.options.vsync,
            x,
            107,
            col,
            () -> {
              app.options.vsync = !app.options.vsync;
              app.applyVsync();
            });
        button(
            "Framerate: " + (app.options.fpsLimit == 0 ? "Unlimited" : app.options.fpsLimit),
            x + col + 4,
            107,
            col,
            20,
            () ->
                app.options.fpsLimit =
                    app.options.fpsLimit == 0
                        ? 30
                        : app.options.fpsLimit == 30 ? 60 : app.options.fpsLimit == 60 ? 120 : 0);
        toggle(
            "Clouds",
            app.options.clouds,
            x,
            131,
            col,
            () -> app.options.clouds = !app.options.clouds);
        toggle(
            "View Bobbing",
            app.options.bobbing,
            x + col + 4,
            131,
            col,
            () -> app.options.bobbing = !app.options.bobbing);
        toggle("Fullscreen", app.options.fullscreen, x, 155, col, app::toggleFullscreen);
        slider(
            "brightness",
            "Brightness",
            x + col + 4,
            155,
            col,
            app.options.brightness,
            v -> app.options.brightness = (float) v);
        done();
      }
      case CONTROLS -> {
        heading("Controls");
        int visible = Math.max(1, (h - 76) / 23);
        clampScroll(app.options.keys.size(), visible);
        int i = 0;
        for (var entry : app.options.keys.entrySet()) {
          int index = i++;
          if (index < scroll || index >= scroll + visible) continue;
          int y = 35 + (index - scroll) * 23;
          String action = entry.getKey();
          label(action, 20, y + 6);
          String text =
              rebinding.equals(action) ? "> Press key <" : Options.keyName(entry.getValue());
          button(text, w - 144, y, 96, 20, () -> rebinding = action);
          if (app.options.conflict(action)) c.border(w - 144, y, 96, 20, 0xffff3333);
          button(
              "X",
              w - 44,
              y,
              24,
              20,
              () -> {
                app.options.keys.put(action, 0);
                rebinding = "";
              });
        }
        done();
      }
      case AUDIO -> {
        heading("Music & Sounds");
        slider(
            "master",
            "Master Volume",
            left,
            62,
            200,
            app.options.master,
            v -> app.options.master = (float) v);
        slider(
            "music", "Music", left, 92, 200, app.options.music, v -> app.options.music = (float) v);
        slider(
            "sound",
            "Sound",
            left,
            122,
            200,
            app.options.sound,
            v -> app.options.sound = (float) v);
        c.centered(
            app.audio.available
                ? "Synthetic audio enabled"
                : "Audio device unavailable; game continues",
            160,
            0xffaaaaaa);
        done();
      }
      case LANGUAGE -> {
        heading("Language");
        String[] languages = {"English", "Português", "Deutsch", "Español", "Français"};
        for (int i = 0; i < languages.length; i++) {
          String language = languages[i];
          button(
              (app.options.language.equals(language) ? "> " : "") + language,
              left,
              40 + i * 25,
              200,
              20,
              () -> app.options.language = language);
        }
        done();
      }
      case PACKS -> {
        heading("Texture Packs");
        int visible = Math.max(1, (h - 100) / 30);
        clampScroll(packs.size() + 1, visible);
        for (int i = scroll; i < Math.min(packs.size() + 1, scroll + visible); i++) {
          int index = i;
          String name = i == 0 ? "Default (procedural)" : packs.get(i - 1).getFileName().toString();
          int y = 36 + (i - scroll) * 30;
          boolean active =
              i == 0 ? app.options.texturePack.isEmpty() : app.options.texturePack.equals(name);
          button(
              (active ? "> " : "") + name,
              20,
              y,
              w - 70,
              24,
              () -> {
                Path pack = index == 0 ? null : packs.get(index - 1);
                app.renderer.assets.reload(pack);
                app.options.texturePack = pack == null ? "" : pack.getFileName().toString();
                app.renderer.invalidate();
              });
          c.tile(w - 44, y, 22, 22, i == 0 ? 2 : 54);
        }
        c.centered("Place ZIP packs in the texturepacks folder.", h - 62, 0xffcccccc);
        c.tile(w / 2f - 40, h - 50, 16, 16, 200);
        c.tile(w / 2f - 20, h - 50, 16, 16, 1);
        c.tile(w / 2f, h - 50, 16, 16, 17);
        c.tile(w / 2f + 20, h - 50, 16, 16, 56);
        done();
      }
      case PAUSE -> {
        heading("Game Menu");
        button("Back to Game", left, top, 200, 20, this::enterGame);
        button("Achievements", left, top + 26, 98, 20, () -> open(Screen.ACHIEVEMENTS));
        button("Statistics", left + 102, top + 26, 98, 20, () -> open(Screen.STATS));
        button("Options...", left, top + 52, 200, 20, () -> open(Screen.OPTIONS));
        button("Save and Quit to Title", left, top + 78, 200, 20, app::leaveWorld);
      }
      case DEATH -> {
        c.centered("You died!", 55, 0xffffffff);
        c.centered("Score: " + app.world.player.experience, 85, 0xffffff55);
        button("Respawn", left, 110, 200, 20, app::respawn);
        button("Title Menu", left, 136, 200, 20, app::leaveWorld);
      }
      case ERROR -> {
        heading("Unable to continue");
        wrapped(error, 20, 55, w - 40, 0xffffbbbb);
        button("Back", left, h - 32, 200, 20, this::back);
      }
      case STATS -> {
        heading("Statistics");
        int y = 50;
        for (var e : app.stats.entrySet()) {
          c.text(e.getKey() + ": " + e.getValue(), 20, y, 0xffffffff);
          y += 15;
          if (y > h - 50) break;
        }
        done();
      }
      case ACHIEVEMENTS -> {
        heading("Achievements");
        String[][] achievements = {
          {"Taking Inventory", "Opened inventory"},
          {"Getting Wood", "Mined wood"},
          {"Benchmarking", "Made a crafting table"},
          {"Time to Mine!", "Made a wooden pickaxe"},
          {"Hot Topic", "Made a furnace"},
          {"Acquire Hardware", "Obtained iron ingot"},
          {"DIAMONDS!", "Obtained diamonds"}
        };
        for (int i = 0; i < achievements.length; i++) {
          String key = achievements[i][0];
          int y = 35 + i * 22;
          c.rect(18, y, w - 36, 20, app.achievements.contains(key) ? 0xff395b30 : 0xff333333);
          c.text(key, 24, y + 3, app.achievements.contains(key) ? 0xffffff55 : 0xff999999);
          c.text(achievements[i][1], w - 160, y + 10, 0xffaaaaaa);
        }
        done();
      }
      default -> throw new IllegalStateException("Unexpected screen " + screen);
    }
  }

  private void heading(String name) {
    c.centered(tr(name), 16, 0xffffffff);
  }

  private void label(String text, int x, int y) {
    c.text(tr(text), x, y, 0xffdddddd);
  }

  private String tr(String s) {
    if (app.options.language.equals("English")) return s;
    String[] english = {
      "Singleplayer",
      "Multiplayer",
      "Options...",
      "Quit Game",
      "Done",
      "Cancel",
      "Back",
      "Select World",
      "Create New World",
      "Game Menu",
      "Back to Game",
      "Save and Quit to Title",
      "Controls",
      "Language",
      "Video Settings",
      "Music & Sounds",
      "World Name",
      "Texture Packs",
      "Respawn",
      "Title Menu"
    };
    String[][] translated = {
      {
        "Um jogador",
        "Multijogador",
        "Opções...",
        "Sair",
        "Concluído",
        "Cancelar",
        "Voltar",
        "Selecionar mundo",
        "Criar novo mundo",
        "Menu do jogo",
        "Voltar ao jogo",
        "Salvar e sair",
        "Controles",
        "Idioma",
        "Vídeo",
        "Música e sons",
        "Nome do mundo",
        "Pacotes de textura",
        "Renascer",
        "Menu principal"
      },
      {
        "Einzelspieler",
        "Mehrspieler",
        "Optionen...",
        "Beenden",
        "Fertig",
        "Abbrechen",
        "Zurück",
        "Welt auswählen",
        "Neue Welt",
        "Spielmenü",
        "Zurück zum Spiel",
        "Speichern und beenden",
        "Steuerung",
        "Sprache",
        "Grafik",
        "Musik und Sound",
        "Weltname",
        "Texturpakete",
        "Wiederbeleben",
        "Hauptmenü"
      },
      {
        "Un jugador",
        "Multijugador",
        "Opciones...",
        "Salir",
        "Hecho",
        "Cancelar",
        "Volver",
        "Seleccionar mundo",
        "Crear mundo",
        "Menú del juego",
        "Volver al juego",
        "Guardar y salir",
        "Controles",
        "Idioma",
        "Vídeo",
        "Música y sonidos",
        "Nombre",
        "Texturas",
        "Reaparecer",
        "Menú principal"
      },
      {
        "Solo",
        "Multijoueur",
        "Options...",
        "Quitter",
        "Terminé",
        "Annuler",
        "Retour",
        "Choisir un monde",
        "Créer un monde",
        "Menu du jeu",
        "Reprendre",
        "Sauver et quitter",
        "Commandes",
        "Langue",
        "Vidéo",
        "Musique et sons",
        "Nom du monde",
        "Textures",
        "Réapparaître",
        "Menu principal"
      }
    };
    int lang =
        switch (app.options.language) {
          case "Português" -> 0;
          case "Deutsch" -> 1;
          case "Español" -> 2;
          default -> 3;
        };
    for (int i = 0; i < english.length; i++) if (english[i].equals(s)) return translated[lang][i];
    return s;
  }

  private void button(String label, float x, float y, float width, float height, App.Task action) {
    button(label, x, y, width, height, true, action);
  }

  private void button(
      String label, float x, float y, float width, float height, boolean enabled, App.Task action) {
    Widget b = new Widget(label, x, y, width, height, () -> app.guard(action), null, null, enabled);
    widgets.add(b);
    boolean hovered = enabled && b.contains(mx, my);
    c.rect(x, y, width, height, 0xff111111);
    c.rect(
        x + 1,
        y + 1,
        width - 2,
        height - 2,
        enabled ? hovered ? 0xff7b80a0 : 0xff646464 : 0xff353535);
    c.rect(x + 1, y + 1, width - 2, 1, enabled ? 0xffbbbbbb : 0xff555555);
    String text = tr(label);
    int max = (int) (width / 6) - 1;
    if (text.length() > max) text = text.substring(0, Math.max(0, max - 1)) + ".";
    c.text(
        text,
        x + (width - BitmapFont.width(text, 1)) / 2,
        y + (height - 7) / 2,
        enabled ? hovered ? 0xffffffaa : 0xffffffff : 0xff888888);
  }

  private void toggle(String text, boolean on, int x, int y, int width, App.Task action) {
    button(text + ": " + (on ? "ON" : "OFF"), x, y, width, 20, action);
  }

  private String on(boolean on, String yes, String no) {
    return on ? yes : no;
  }

  private void done() {
    button(
        "Done",
        (w - 200) / 2,
        h - 28,
        200,
        20,
        () -> {
          app.saveOptions();
          back();
        });
  }

  private void slider(
      String id, String label, int x, int y, int width, double value, DoubleConsumer changed) {
    var widget = new Widget(id, x, y, width, 20, null, changed, null, true);
    widgets.add(widget);
    c.rect(x, y, width, 20, 0xff262626);
    c.border(x, y, width, 20, 0xff999999);
    float px = x + 2 + (float) value * (width - 8);
    c.rect(px, y + 1, 6, 18, 0xffaaaacc);
    c.text(tr(label) + ": " + Math.round(value * 100) + "%", x + 5, y + 7, 0xffffffff);
  }

  private void field(String key, int x, int y, int width, String value) {
    var widget =
        new Widget(
            key,
            x,
            y,
            width,
            20,
            () -> {
              focus = key;
              selectAll = false;
            },
            null,
            key,
            true);
    widgets.add(widget);
    fields.put(key, value);
    c.rect(x, y, width, 20, 0xff000000);
    c.border(x, y, width, 20, focus.equals(key) ? 0xffffffff : 0xff999999);
    int max = (width - 10) / 6;
    String displayed = value.length() > max ? value.substring(value.length() - max) : value;
    c.text(
        displayed + (focus.equals(key) && ticks / 20 % 2 == 0 ? "_" : ""),
        x + 4,
        y + 7,
        0xffffffff);
  }

  private void listEntry(
      String id,
      String title,
      String subtitle,
      int x,
      int y,
      int width,
      int height,
      boolean selected,
      Runnable select) {
    Widget widget = new Widget(id, x, y, width, height, select, null, null, true);
    widgets.add(widget);
    c.rect(x, y, width, height, selected ? 0xc0444444 : 0x70000000);
    if (selected) c.border(x, y, width, height, 0xffffffff);
    c.text(title, x + 5, y + 5, 0xffffffff);
    String clipped =
        subtitle.length() > (width - 10) / 6
            ? subtitle.substring(0, Math.max(0, (width - 10) / 6 - 1))
            : subtitle;
    c.text(clipped, x + 5, y + 17, 0xff999999);
  }

  private void wrapped(String text, int x, int y, int width, int color) {
    int max = Math.max(10, width / 6);
    for (String line : text.split("\n")) {
      while (line.length() > max) {
        int end = line.lastIndexOf(' ', max);
        if (end <= 0) end = max;
        c.text(line.substring(0, end), x, y, color);
        y += 12;
        line = line.substring(end).stripLeading();
      }
      c.text(line, x, y, color);
      y += 12;
    }
  }

  private void clampScroll(int total, int visible) {
    scroll = Math.max(0, Math.min(scroll, Math.max(0, total - visible)));
  }

  private void refreshWorlds() {
    app.guard(
        () -> {
          saves = app.store.list();
          if (selectedWorld >= saves.size()) selectedWorld = -1;
        });
  }

  private void refreshPacks() {
    app.guard(
        () -> {
          Path path = app.data.resolve("texturepacks");
          Files.createDirectories(path);
          try (var paths = Files.list(path)) {
            packs =
                paths
                    .filter(
                        p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted()
                    .toList();
          }
        });
  }

  private void confirm(String text, App.Task action) {
    confirmText = text;
    confirmAction = () -> app.guard(action);
    open(Screen.CONFIRM);
  }

  private void join(String address) {
    String username = fields.get("username").strip();
    if (!username.matches("[A-Za-z0-9_]{1,16}"))
      throw new IllegalArgumentException(
          "Username must contain 1-16 letters, digits or underscores");
    app.options.username = username;
    app.connect(address);
  }

  private void pingServers() {
    for (var s : app.servers.entries) {
      statuses.put(s.address(), "Pinging...");
      Thread.ofVirtual()
          .start(
              () -> {
                String text;
                try {
                  text = LegacyClient.ping(s.address());
                } catch (Exception e) {
                  text = "Unreachable";
                }
                String result = text;
                app.events.add(() -> statuses.put(s.address(), result));
              });
    }
  }

  public void move(double x, double y) {
    mx = x;
    my = y;
    if (!drag.isEmpty())
      for (Widget widget : widgets)
        if (widget.id.equals(drag) && widget.slider != null)
          widget.slider.accept(Math.max(0, Math.min(1, (x - widget.x) / widget.w)));
  }

  public void mouse(int button, boolean pressed, boolean shift) {
    if (!pressed) {
      drag = "";
      return;
    }
    if (!rebinding.isEmpty()) {
      app.options.keys.put(rebinding, -button - 1);
      rebinding = "";
      return;
    }
    if (screen == Screen.GAME) return;
    for (int i = widgets.size() - 1; i >= 0; i--) {
      Widget widget = widgets.get(i);
      if (widget.enabled && widget.contains(mx, my)) {
        app.audio.play(Audio.Sound.CLICK);
        if (widget.slider != null) {
          drag = widget.id;
          widget.slider.accept(Math.max(0, Math.min(1, (mx - widget.x) / widget.w)));
        } else app.guard(widget.action::run);
        return;
      }
    }
    if (screen == Screen.INVENTORY
        || screen == Screen.CRAFTING
        || screen == Screen.CHEST
        || screen == Screen.FURNACE) {
      for (Slot slot : slots)
        if (mx >= slot.x && my >= slot.y && mx < slot.x + 16 && my < slot.y + 16) {
          clickSlot(slot, button == GLFW_MOUSE_BUTTON_RIGHT, shift);
          return;
        }
      int panelHeight =
          screen == Screen.INVENTORY && app.world.player.creative
              ? 228
              : screen == Screen.CRAFTING ? 180 : screen == Screen.INVENTORY ? 178 : 166;
      int panelX = (w - 176) / 2, panelY = (h - panelHeight) / 2;
      boolean outside =
          mx < panelX || mx >= panelX + 176 || my < panelY || my >= panelY + panelHeight;
      if (!cursor.empty() && outside) {
        int count = button == GLFW_MOUSE_BUTTON_RIGHT ? 1 : cursor.count();
        app.world.drop(
            app.world.player.x,
            app.world.player.y + 1,
            app.world.player.z,
            cursor.withCount(count));
        cursor = cursor.withCount(cursor.count() - count);
      }
    }
  }

  public boolean key(int key, int mods) {
    if (!rebinding.isEmpty()) {
      app.options.keys.put(rebinding, key == GLFW_KEY_ESCAPE ? 0 : key);
      rebinding = "";
      return true;
    }
    if (key == GLFW_KEY_ESCAPE) {
      back();
      return true;
    }
    if (focus.isEmpty()
        && key == app.options.keys.get("Inventory")
        && (screen == Screen.INVENTORY
            || screen == Screen.CRAFTING
            || screen == Screen.CHEST
            || screen == Screen.FURNACE)) {
      back();
      return true;
    }
    if (screen == Screen.GAME) return false;
    if (key == GLFW_KEY_ENTER) {
      if (primary != null) app.guard(primary::run);
      return true;
    }
    if (key == GLFW_KEY_TAB) {
      List<String> names = widgets.stream().map(Widget::field).filter(Objects::nonNull).toList();
      if (!names.isEmpty()) {
        int index = names.indexOf(focus);
        focus =
            names.get(Math.floorMod(index + ((mods & GLFW_MOD_SHIFT) != 0 ? -1 : 1), names.size()));
      }
      return true;
    }
    if (!focus.isEmpty()) {
      String text = fields.getOrDefault(focus, "");
      if (key == GLFW_KEY_A && (mods & GLFW_MOD_CONTROL) != 0) selectAll = true;
      if (key == GLFW_KEY_BACKSPACE) {
        if (selectAll) {
          text = "";
          selectAll = false;
        } else if (!text.isEmpty())
          text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        fields.put(focus, text);
      }
      if (key == GLFW_KEY_V && (mods & GLFW_MOD_CONTROL) != 0) {
        String value = glfwGetClipboardString(app.window);
        if (value != null) for (int point : value.codePoints().toArray()) character(point);
      }
      return true;
    }
    return true;
  }

  public void character(int point) {
    if (focus.isEmpty() || !Character.isValidCodePoint(point) || Character.isISOControl(point))
      return;
    String old = selectAll ? "" : fields.getOrDefault(focus, "");
    selectAll = false;
    if (old.length() < 128) fields.put(focus, old + new String(Character.toChars(point)));
  }

  public void scroll(double amount) {
    if (screen == Screen.GAME) {
      app.selectSlot(Math.floorMod(app.world.player.selected - (int) Math.signum(amount), 9));
    } else scroll = Math.max(0, scroll - (int) (amount * 2));
  }

  private void submitChat() {
    String text = fields.get("chat").strip();
    if (!text.isEmpty()) app.chat(text);
    fields.put("chat", "");
    enterGame();
  }

  private void drawChat(int count) {
    int n = Math.min(count, chat.size());
    for (int i = 0; i < n; i++) {
      String line = chat.get(chat.size() - n + i);
      if (line.length() > w / 6 - 3) line = line.substring(0, w / 6 - 3);
      c.rect(2, h - 38 - (n - i) * 10, w - 4, 10, 0x55000000);
      c.text(line, 4, h - 36 - (n - i) * 10, 0xffffffff);
    }
  }

  private void hud() {
    if (app.world == null) return;
    Player p = app.world.player;
    int x = (w - 182) / 2, y = h - 22;
    c.rect(x, y, 182, 22, 0xb0555555);
    for (int i = 0; i < 9; i++) {
      c.border(x + 1 + i * 20, y + 1, 20, 20, i == p.selected ? 0xffffffff : 0xff888888);
      item(p.inventory.get(i), x + 3 + i * 20, y + 3);
    }
    if (!p.creative) {
      for (int i = 0; i < 10; i++) {
        heart(
            x + i * 8,
            y - 11,
            p.health >= i * 2 + 2 ? 0xffbd2626 : p.health == i * 2 + 1 ? 0xffad7226 : 0xff333333);
        heart(x + 102 + i * 8, y - 11, p.food >= i * 2 + 2 ? 0xffb38d3f : 0xff333333);
      }
      if (p.air < 300) c.text("AIR " + Math.max(0, p.air / 30), x + 104, y - 22, 0xff88bbff);
      c.rect(x, y - 2, 182, 2, 0xff333333);
      c.rect(x, y - 2, Math.min(182, p.experience % 100 * 1.82f), 2, 0xff80aa33);
    }
    c.rect(w / 2f - 4, h / 2f, 9, 1, 0xffffffff);
    c.rect(w / 2f, h / 2f - 4, 1, 9, 0xffffffff);
    if (app.mining > 0)
      c.rect(w / 2f - 20, h / 2f + 10, (float) (40 * Math.min(1, app.mining)), 2, 0xffdddddd);
    if (app.debug) {
      c.text("OpenCraft / Java 21 / " + app.fps + " FPS", 2, 2, 0xffffffff);
      c.text(
          String.format(Locale.ROOT, "XYZ %.2f / %.2f / %.2f", p.x, p.y, p.z), 2, 13, 0xffffffff);
      c.text(
          "Biome "
              + app.world.generator.biome((int) p.x, (int) p.z)
              + " / Chunks "
              + app.world.chunks.size(),
          2,
          24,
          0xffffffff);
      c.text(
          "Light "
              + app.world.light((int) p.x, (int) p.y, (int) p.z, true)
              + " / "
              + app.world.light((int) p.x, (int) p.y, (int) p.z, false),
          2,
          35,
          0xffffffff);
    }
    if (app.toastTicks > 0) c.centered(app.toast, h - 47, 0xffffff55);
    if (screen == Screen.CHAT) drawChat(10);
  }

  private void heart(float x, float y, int color) {
    c.rect(x + 1, y, 2, 2, color);
    c.rect(x + 4, y, 2, 2, color);
    c.rect(x, y + 2, 7, 2, color);
    c.rect(x + 1, y + 4, 5, 1, color);
    c.rect(x + 2, y + 5, 3, 1, color);
    c.rect(x + 3, y + 6, 1, 1, color);
  }

  private void container() {
    c.rect(0, 0, w, h, 0x88000000);
    Player p = app.world.player;
    boolean creative = screen == Screen.INVENTORY && p.creative;
    int width = 176,
        height =
            creative
                ? 228
                : screen == Screen.CRAFTING ? 180 : screen == Screen.INVENTORY ? 178 : 166,
        x = (w - width) / 2,
        y = (h - height) / 2;
    c.rect(x - 2, y - 2, width + 4, height + 4, 0xff333333);
    c.rect(x, y, width, height, 0xffc6c6c6);
    c.border(x, y, width, height, 0xffffffff);
    String title =
        screen == Screen.CHEST
            ? "Chest"
            : screen == Screen.FURNACE
                ? "Furnace"
                : screen == Screen.CRAFTING ? "Crafting" : "Inventory";
    c.text(title, x + 8, y + 6, 0xff333333);
    int invY =
        creative ? 142 : screen == Screen.CRAFTING ? 91 : screen == Screen.INVENTORY ? 92 : 84;
    if (creative) {
      String search = fields.get("search");
      field("search", x + 8, y + 18, 160, search);
      palette =
          Items.all().stream()
              .filter(
                  it ->
                      it.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
              .toList();
      int rows = 5;
      clampScroll((palette.size() + 8) / 9, rows);
      Inventory display = new Inventory(45);
      for (int i = 0; i < 45; i++) {
        int index = scroll * 9 + i;
        if (index < palette.size())
          display.set(i, new Stack(palette.get(index).id(), palette.get(index).maxStack()));
        slot(display, i, x + 8 + (i % 9) * 18, y + 43 + (i / 9) * 18, false, false, true);
      }
    } else if (screen == Screen.CHEST) {
      World.Tile tile = app.world.tile(container);
      for (int i = 0; i < 27; i++)
        slot(tile.inventory, i, x + 8 + i % 9 * 18, y + 18 + i / 9 * 18, false, false, false);
    } else if (screen == Screen.FURNACE) {
      World.Tile tile = app.world.tile(container);
      slot(tile.inventory, 0, x + 56, y + 18, false, false, false);
      slot(tile.inventory, 1, x + 56, y + 54, false, false, false);
      slot(tile.inventory, 2, x + 116, y + 36, true, false, false);
      c.rect(x + 81, y + 39, 24, 8, 0xff777777);
      c.rect(x + 81, y + 39, 24 * tile.cook / 200f, 8, 0xffeeeeee);
      if (tile.burn > 0)
        c.rect(
            x + 59,
            y + 38,
            10,
            Math.max(1, 13f * tile.burn / Math.max(1, tile.totalBurn)),
            0xffef9933);
    } else {
      int size = screen == Screen.CRAFTING ? 3 : 2, gx = x + (size == 3 ? 30 : 88), gy = y + 20;
      for (int i = 0; i < crafting.size(); i++)
        slot(crafting, i, gx + i % size * 18, gy + i / size * 18, false, false, false);
      Inventory result = new Inventory(1);
      Recipes.match(crafting, size).ifPresent(m -> result.set(0, m.result()));
      slot(result, 0, x + 144, y + 30, true, true, false);
      c.text(">", x + 128, y + 35, 0xff555555);
      if (size == 2) {
        for (int i = 0; i < 4; i++) slot(p.armor, i, x + 8, y + 17 + i * 18, false, false, false);
        c.rect(x + 32, y + 17, 45, 68, 0xff222222);
        c.rect(x + 45, y + 23, 16, 16, 0xffbb976f);
        c.rect(x + 42, y + 39, 22, 23, 0xff457daf);
        c.rect(x + 44, y + 62, 8, 18, 0xff383879);
        c.rect(x + 55, y + 62, 8, 18, 0xff383879);
      }
    }
    if (screen != Screen.INVENTORY) c.text("Inventory", x + 8, y + invY - 10, 0xff333333);
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        slot(
            p.inventory,
            9 + row * 9 + col,
            x + 8 + col * 18,
            y + invY + row * 18,
            false,
            false,
            false);
    for (int i = 0; i < 9; i++)
      slot(p.inventory, i, x + 8 + i * 18, y + invY + 58, false, false, false);
    String tooltip = null;
    for (Slot slot : slots)
      if (mx >= slot.x && my >= slot.y && mx < slot.x + 16 && my < slot.y + 16) {
        c.rect(slot.x, slot.y, 16, 16, 0x66ffffff);
        Stack s = slot.inventory.get(slot.index);
        if (!s.empty()) tooltip = Items.get(s.id()).name();
      }
    if (tooltip != null && cursor.empty()) {
      float tx = Math.min(w - BitmapFont.width(tooltip, 1) - 8, (float) mx + 8),
          ty = Math.max(0, (float) my - 14);
      c.rect(tx - 3, ty - 3, BitmapFont.width(tooltip, 1) + 6, 13, 0xee180f24);
      c.text(tooltip, tx, ty, 0xffffffff);
    }
    if (!cursor.empty()) item(cursor, (float) mx - 8, (float) my - 8);
  }

  private void slot(
      Inventory inv, int index, float x, float y, boolean output, boolean craft, boolean palette) {
    c.rect(x - 1, y - 1, 18, 18, 0xff666666);
    c.rect(x, y, 16, 16, 0xff999999);
    c.rect(x, y + 16, 17, 1, 0xffffffff);
    item(inv.get(index), x, y);
    slots.add(new Slot(inv, index, x, y, output, craft, palette));
  }

  private void item(Stack s, float x, float y) {
    if (s.empty()) return;
    c.tile(x, y, 16, 16, s.id());
    if (s.count() > 1) {
      String text = Integer.toString(s.count());
      c.text(text, x + 17 - BitmapFont.width(text, 1), y + 10, 0xffffffff);
    }
    int dur = Items.get(s.id()).durability();
    if (dur > 0 && s.damage() > 0) {
      c.rect(x + 1, y + 14, 14, 2, 0xff000000);
      c.rect(x + 1, y + 14, 14 * (1 - s.damage() / (float) dur), 1, 0xff55cc33);
    }
  }

  private void clickSlot(Slot slot, boolean right, boolean shift) {
    Player p = app.world.player;
    if (slot.palette) {
      Stack s = slot.inventory.get(slot.index);
      if (shift) p.inventory.add(s);
      else cursor = s;
      return;
    }
    if (slot.craft) {
      int width = crafting.size() == 9 ? 3 : 2;
      do {
        var match = Recipes.match(crafting, width);
        if (match.isEmpty()) return;
        Stack result = match.get().result();
        if (shift) {
          if (p.inventory.capacity(result) < result.count()) return;
          p.inventory.add(result);
        } else {
          if (!cursor.empty()
              && (!cursor.compatible(result) || cursor.count() + result.count() > cursor.max()))
            return;
          cursor = new Stack(result.id(), cursor.count() + result.count(), result.damage());
        }
        Recipes.consume(crafting, match.get());
        app.stat("Items crafted", result.count());
        app.unlockFor(result.id());
      } while (shift);
      return;
    }
    if (slot.output && !cursor.empty()) {
      Stack output = slot.inventory.get(slot.index);
      if (output.empty() || !cursor.compatible(output)) return;
      int take = Math.min(right ? 1 : output.count(), cursor.max() - cursor.count());
      slot.inventory.remove(slot.index, take);
      cursor = cursor.withCount(cursor.count() + take);
      return;
    }
    if (slot.inventory == p.armor
        && !cursor.empty()
        && (!Items.get(cursor.id()).tool().equals("armor")
            || (cursor.id() - 298) % 4 != slot.index)) return;
    if (shift) {
      Stack s = slot.inventory.get(slot.index);
      Inventory target =
          slot.inventory == p.inventory
                  && container != null
                  && (screen == Screen.CHEST || screen == Screen.FURNACE)
              ? app.world.tile(container).inventory
              : p.inventory;
      if (slot.inventory == p.inventory && container != null && screen == Screen.FURNACE) {
        if (Items.smelt(s.id()) != 0) s = target.insert(0, s);
        else if (Items.fuel(s.id()) > 0) s = target.insert(1, s);
      } else if (target == slot.inventory) {
        int start = slot.index < 9 ? 9 : 0, end = slot.index < 9 ? 36 : 9;
        for (int i = start; i < end && !s.empty(); i++) {
          Stack a = target.get(i);
          if (a.empty() || a.compatible(s)) {
            int n = Math.min(s.count(), s.max() - a.count());
            target.set(i, new Stack(s.id(), a.count() + n, s.damage()));
            s = s.withCount(s.count() - n);
          }
        }
      } else s = target.add(s);
      slot.inventory.set(slot.index, s);
    } else cursor = slot.inventory.click(slot.index, cursor, right);
    if (app.world.remote && slot.inventory == p.inventory) {
      int remoteSlot = slot.index < 9 ? slot.index + 36 : slot.index;
      app.client.click(remoteSlot, right, shift, slot.inventory.get(slot.index));
    }
  }
}
