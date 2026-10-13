package dev.opencraft;

import static org.lwjgl.glfw.GLFW.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Options {
  public boolean fancy = true,
      smoothLight = true,
      anaglyph,
      advancedOpenGL,
      vsync = true,
      fullscreen,
      clouds = true,
      bobbing = true;
  public int renderDistance = 1, guiScale = 0, fpsLimit = 120, difficulty = 2;
  public volatile float master = 1,
      music = .25f,
      sound = 1,
      sensitivity = .5f,
      brightness = .1f,
      fov = 70;
  public String language = "English", texturePack = "", username = "Player";
  public final LinkedHashMap<String, Integer> keys = new LinkedHashMap<>();
  private final Path file;

  public Options(Path data) throws IOException {
    file = data.resolve("options.properties");
    String[] actions = {
      "Forward",
      "Left",
      "Back",
      "Right",
      "Jump",
      "Sneak",
      "Sprint",
      "Inventory",
      "Drop",
      "Chat",
      "Fog",
      "Screenshot",
      "Fullscreen",
      "Attack",
      "Use Item",
      "Pick Block"
    };
    int[] codes = {
      GLFW_KEY_W,
      GLFW_KEY_A,
      GLFW_KEY_S,
      GLFW_KEY_D,
      GLFW_KEY_SPACE,
      GLFW_KEY_LEFT_SHIFT,
      GLFW_KEY_LEFT_CONTROL,
      GLFW_KEY_E,
      GLFW_KEY_Q,
      GLFW_KEY_T,
      GLFW_KEY_F,
      GLFW_KEY_F2,
      GLFW_KEY_F11,
      -1,
      -2,
      -3
    };
    for (int i = 0; i < actions.length; i++) keys.put(actions[i], codes[i]);
    if (Files.exists(file)) {
      Properties p = new Properties();
      try (var in = Files.newInputStream(file)) {
        p.load(in);
      }
      fancy = b(p, "fancy", true);
      smoothLight = b(p, "smoothLight", true);
      anaglyph = b(p, "anaglyph", false);
      advancedOpenGL = b(p, "advancedOpenGL", false);
      vsync = b(p, "vsync", true);
      fullscreen = b(p, "fullscreen", false);
      clouds = b(p, "clouds", true);
      bobbing = b(p, "bobbing", true);
      renderDistance = i(p, "renderDistance", 1, 0, 3);
      guiScale = i(p, "guiScale", 0, 0, 3);
      fpsLimit = i(p, "fpsLimit", 120, 0, 260);
      difficulty = i(p, "difficulty", 2, 0, 3);
      master = f(p, "master", 1, 0, 1);
      music = f(p, "music", .25f, 0, 1);
      sound = f(p, "sound", 1, 0, 1);
      sensitivity = f(p, "sensitivity", .5f, 0, 1);
      brightness = f(p, "brightness", .1f, 0, 1);
      fov = f(p, "fov", 70, 30, 110);
      language = p.getProperty("language", "English");
      texturePack = p.getProperty("texturePack", "");
      username = p.getProperty("username", "Player");
      for (var e : keys.entrySet())
        e.setValue(i(p, "key." + e.getKey(), e.getValue(), -8, GLFW_KEY_LAST));
    }
  }

  private static boolean b(Properties p, String n, boolean d) {
    return Boolean.parseBoolean(p.getProperty(n, Boolean.toString(d)));
  }

  private static int i(Properties p, String n, int d, int min, int max) {
    try {
      return Math.max(min, Math.min(max, Integer.parseInt(p.getProperty(n))));
    } catch (RuntimeException e) {
      return d;
    }
  }

  private static float f(Properties p, String n, float d, float min, float max) {
    try {
      float value = Float.parseFloat(p.getProperty(n));
      return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : d;
    } catch (RuntimeException e) {
      return d;
    }
  }

  public void save() throws IOException {
    Properties p = new Properties();
    String[] names = {
      "fancy",
      "smoothLight",
      "anaglyph",
      "advancedOpenGL",
      "vsync",
      "fullscreen",
      "clouds",
      "bobbing",
      "renderDistance",
      "guiScale",
      "fpsLimit",
      "difficulty",
      "master",
      "music",
      "sound",
      "sensitivity",
      "brightness",
      "fov",
      "language",
      "texturePack",
      "username"
    };
    for (String n : names)
      try {
        p.setProperty(n, String.valueOf(getClass().getField(n).get(this)));
      } catch (ReflectiveOperationException e) {
        throw new IOException(e);
      }
    for (var e : keys.entrySet())
      p.setProperty("key." + e.getKey(), Integer.toString(e.getValue()));
    Files.createDirectories(file.getParent());
    Path tmp = Files.createTempFile(file.getParent(), "options-", ".tmp");
    try {
      try (var out = Files.newOutputStream(tmp)) {
        p.store(out, "OpenCraft options");
      }
      try {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  public int radius() {
    return new int[] {2, 4, 8, 16}[renderDistance];
  }

  public int guiFactor(int w, int h) {
    int max = 1;
    while (w / (max + 1) >= 320 && h / (max + 1) >= 240) max++;
    return guiScale == 0 ? max : Math.min(guiScale, max);
  }

  public boolean conflict(String action) {
    int key = keys.get(action);
    return key != 0
        && keys.entrySet().stream()
            .anyMatch(e -> !e.getKey().equals(action) && e.getValue() == key);
  }

  public static String keyName(int code) {
    if (code == 0) return "Unbound";
    if (code < 0) return "Mouse " + (-code);
    String s = glfwGetKeyName(code, 0);
    if (s != null) return s.toUpperCase(Locale.ROOT);
    return switch (code) {
      case GLFW_KEY_SPACE -> "Space";
      case GLFW_KEY_LEFT_SHIFT -> "LShift";
      case GLFW_KEY_LEFT_CONTROL -> "LCtrl";
      case GLFW_KEY_ESCAPE -> "Escape";
      case GLFW_KEY_ENTER -> "Enter";
      case GLFW_KEY_TAB -> "Tab";
      case GLFW_KEY_F2 -> "F2";
      case GLFW_KEY_F11 -> "F11";
      default -> "Key " + code;
    };
  }
}
