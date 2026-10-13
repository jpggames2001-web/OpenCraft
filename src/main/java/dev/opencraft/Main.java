package dev.opencraft;

import java.nio.file.*;
import java.util.*;

public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    var flags = new HashSet<String>();
    Path data = defaultData();
    int frames = 145;
    for (int i = 0; i < args.length; i++)
      switch (args[i]) {
        case "--data-dir" -> {
          if (++i == args.length) throw new IllegalArgumentException("--data-dir requires a path");
          data = Path.of(args[i]);
        }
        case "--frames" -> {
          if (++i == args.length) throw new IllegalArgumentException("--frames requires a number");
          frames = Integer.parseInt(args[i]);
          if (frames < 1 || frames > 10000)
            throw new IllegalArgumentException("Invalid frame count");
        }
        case "--headless-smoke", "--render-smoke", "--null-platform" -> flags.add(args[i]);
        case "--help" -> {
          System.out.println(
              "OpenCraft: java -jar opencraft-0.1.0.jar [--data-dir PATH] [--headless-smoke]"
                  + " [--render-smoke --frames N] [--null-platform]");
          return;
        }
        default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
      }
    Files.createDirectories(data);
    if (flags.contains("--headless-smoke")) {
      HeadlessSmoke.run(data);
      return;
    }
    try (var app =
        new App(
            data, flags.contains("--render-smoke"), flags.contains("--null-platform"), frames)) {
      app.run();
    }
  }

  public static Path defaultData() {
    String xdg = System.getenv("XDG_DATA_HOME"), appdata = System.getenv("APPDATA");
    boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    if (windows && appdata != null) return Path.of(appdata, "OpenCraft");
    return xdg != null
        ? Path.of(xdg, "opencraft")
        : Path.of(System.getProperty("user.home"), ".local", "share", "opencraft");
  }
}
