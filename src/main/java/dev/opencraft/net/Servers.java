package dev.opencraft.net;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Servers {
  public record Server(String name, String address) {}

  public final List<Server> entries = new ArrayList<>();
  private final Path path;

  public Servers(Path data) throws IOException {
    path = data.resolve("servers.properties");
    if (Files.exists(path)) {
      Properties p = new Properties();
      try (var in = Files.newInputStream(path)) {
        p.load(in);
      }
      int n = Integer.parseInt(p.getProperty("count", "0"));
      if (n < 0 || n > 1000) throw new IOException("Invalid server list");
      for (int i = 0; i < n; i++)
        entries.add(
            new Server(
                p.getProperty(i + ".name", "Server"), p.getProperty(i + ".address", "localhost")));
    }
  }

  public void save() throws IOException {
    Properties p = new Properties();
    p.setProperty("count", Integer.toString(entries.size()));
    for (int i = 0; i < entries.size(); i++) {
      p.setProperty(i + ".name", entries.get(i).name);
      p.setProperty(i + ".address", entries.get(i).address);
    }
    Path tmp = Files.createTempFile(path.getParent(), "servers-", ".tmp");
    try {
      try (var out = Files.newOutputStream(tmp)) {
        p.store(out, "OpenCraft servers");
      }
      Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }
}
