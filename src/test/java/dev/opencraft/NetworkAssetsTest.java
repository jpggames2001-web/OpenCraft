package dev.opencraft;

import static org.junit.jupiter.api.Assertions.*;

import dev.opencraft.audio.Audio;
import dev.opencraft.net.*;
import dev.opencraft.render.Assets;
import dev.opencraft.world.World;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NetworkAssetsTest {
  @TempDir Path dir;

  @Test
  void protocolStringsUseBoundedUtf16() throws Exception {
    var bytes = new ByteArrayOutputStream();
    LegacyClient.writeString(new DataOutputStream(bytes), "héllo 世界");
    assertEquals(
        "héllo 世界",
        LegacyClient.readString(
            new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 32));
    assertThrows(
        IOException.class,
        () ->
            LegacyClient.readString(
                new DataInputStream(new ByteArrayInputStream(new byte[] {-1, -1})), 32));
  }

  @Test
  void parsesDefaultPortAndBracketedIpv6() {
    assertEquals(
        new LegacyClient.Address("localhost", 25565), LegacyClient.Address.parse("localhost"));
    assertEquals(new LegacyClient.Address("::1", 25566), LegacyClient.Address.parse("[::1]:25566"));
    assertThrows(IllegalArgumentException.class, () -> LegacyClient.Address.parse("localhost:0"));
  }

  @Test
  void legacyPingPerformsAnActualTcpExchange() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var task =
          CompletableFuture.runAsync(
              () -> {
                try (var socket = server.accept()) {
                  assertEquals(254, socket.getInputStream().read());
                  var out = new DataOutputStream(socket.getOutputStream());
                  out.writeByte(255);
                  LegacyClient.writeString(out, "Test server§1§20");
                  out.flush();
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
      assertEquals("Test server 1 20", LegacyClient.ping("127.0.0.1:" + server.getLocalPort()));
      task.get(3, TimeUnit.SECONDS);
    }
  }

  @Test
  void serverListCrudPersists() throws Exception {
    Servers servers = new Servers(dir);
    servers.entries.add(new Servers.Server("Local", "localhost:25565"));
    servers.save();
    Servers read = new Servers(dir);
    assertEquals(servers.entries, read.entries);
    read.entries.clear();
    read.save();
    assertTrue(new Servers(dir).entries.isEmpty());
  }

  @Test
  void offlineLoginReceivesWorldPositionAndAuthoritativeBlock() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      server.setSoTimeout(3000);
      var ready = new CountDownLatch(1);
      var finish = new CountDownLatch(1);
      var task =
          CompletableFuture.runAsync(
              () -> {
                try (var socket = server.accept()) {
                  socket.setSoTimeout(3000);
                  var in = new DataInputStream(socket.getInputStream());
                  var out = new DataOutputStream(socket.getOutputStream());
                  assertEquals(2, in.readUnsignedByte());
                  assertEquals("Tester", LegacyClient.readString(in, 16));
                  out.writeByte(2);
                  LegacyClient.writeString(out, "-");
                  out.flush();
                  assertEquals(1, in.readUnsignedByte());
                  assertEquals(22, in.readInt());
                  assertEquals("Tester", LegacyClient.readString(in, 16));
                  in.readLong();
                  in.readInt();
                  in.readByte();
                  in.readByte();
                  in.readByte();
                  in.readByte();
                  out.writeByte(1);
                  out.writeInt(123);
                  LegacyClient.writeString(out, "");
                  out.writeLong(42);
                  out.writeInt(1);
                  out.writeByte(0);
                  out.writeByte(2);
                  out.writeByte(128);
                  out.writeByte(20);
                  out.writeByte(13);
                  out.writeDouble(4.5);
                  out.writeDouble(68.62);
                  out.writeDouble(67);
                  out.writeDouble(4.5);
                  out.writeFloat(90);
                  out.writeFloat(0);
                  out.writeBoolean(false);
                  out.writeByte(53);
                  out.writeInt(4);
                  out.writeByte(66);
                  out.writeInt(4);
                  out.writeByte(1);
                  out.writeByte(0);
                  out.flush();
                  ready.countDown();
                  assertTrue(finish.await(3, TimeUnit.SECONDS));
                } catch (Exception e) {
                  throw new CompletionException(e);
                }
              });
      var joined = new AtomicReference<World>();
      var error = new AtomicReference<String>();
      try (var client =
          new LegacyClient(
              "127.0.0.1:" + server.getLocalPort(), "Tester", joined::set, s -> {}, error::set)) {
        assertTrue(ready.await(3, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
          client.poll();
          if (joined.get() != null
              && joined.get().peek(4, 66, 4) == dev.opencraft.world.Block.STONE) break;
          Thread.sleep(5);
        }
        assertNull(error.get());
        World world = joined.get();
        assertNotNull(world);
        assertTrue(world.remote);
        assertTrue(world.player.creative);
        assertEquals(4.5, world.player.x);
        assertEquals(67, world.player.y);
        assertEquals(dev.opencraft.world.Block.STONE, world.peek(4, 66, 4));
        assertEquals(dev.opencraft.world.Block.AIR, world.block(0, 64, 0));
        finish.countDown();
      } finally {
        finish.countDown();
      }
      task.get(3, TimeUnit.SECONDS);
    }
  }

  @Test
  void proceduralAssetsAreDeterministicAndHaveTransparency() {
    var a = Assets.generate();
    var b = Assets.generate();
    assertEquals(512, a.getWidth());
    assertEquals(a.getRGB(20, 20), b.getRGB(20, 20));
    int x = (20 % 32) * 16 + 8, y = (20 / 32) * 16 + 4;
    assertTrue((a.getRGB(x, y) >>> 24) < 255);
  }

  @Test
  void syntheticAudioIsPcmAndSilenceHonorsVolume() {
    byte[] pcm = Audio.synthesize(Audio.Sound.DIG, 1);
    assertEquals(0, pcm.length % 2);
    boolean nonzero = false;
    for (byte v : pcm) if (v != 0) nonzero = true;
    assertTrue(nonzero);
    for (byte v : Audio.synthesize(Audio.Sound.DIG, 0)) assertEquals(0, v);
  }
}
