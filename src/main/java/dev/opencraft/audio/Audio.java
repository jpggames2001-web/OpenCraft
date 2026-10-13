package dev.opencraft.audio;

import dev.opencraft.Options;
import java.util.Random;
import java.util.concurrent.*;
import javax.sound.sampled.*;

/** Synthesized mono PCM, mixed on one daemon so gameplay never waits on a sound device. */
public final class Audio implements AutoCloseable {
  public enum Sound {
    CLICK,
    STEP,
    DIG,
    DAMAGE,
    PICKUP,
    SPLASH,
    EXPLODE
  }

  private final Options options;
  private final ArrayBlockingQueue<Sound> events = new ArrayBlockingQueue<>(32);
  private final Thread thread;
  private volatile boolean running = true;
  private volatile SourceDataLine line;
  public volatile boolean available;

  public Audio(Options options) {
    this.options = options;
    thread = Thread.ofPlatform().daemon(true).name("synthetic-audio").start(this::run);
  }

  public void play(Sound s) {
    events.offer(s);
  }

  private void run() {
    try {
      AudioFormat format = new AudioFormat(22050, 16, 1, true, false);
      line = AudioSystem.getSourceDataLine(format);
      line.open(format, 8192);
      line.start();
      available = true;
      while (running) {
        Sound s = events.poll(250, TimeUnit.MILLISECONDS);
        if (s != null) {
          byte[] samples = synthesize(s, options.master * options.sound);
          line.write(samples, 0, samples.length);
        } else if (options.music > 0 && System.nanoTime() % 90_000_000_000L < 250_000_000L) {
          byte[] music = melody(options.master * options.music);
          line.write(music, 0, music.length);
        }
      }
    } catch (LineUnavailableException | IllegalArgumentException e) {
      available = false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      SourceDataLine l = line;
      if (l != null) {
        l.stop();
        l.close();
      }
    }
  }

  public static byte[] synthesize(Sound s, float volume) {
    double duration =
        s == Sound.EXPLODE ? .5 : s == Sound.CLICK ? .07 : s == Sound.PICKUP ? .12 : .16;
    int n = (int) (duration * 22050);
    byte[] bytes = new byte[n * 2];
    Random r = new Random(937 + s.ordinal());
    for (int i = 0; i < n; i++) {
      double t = i / 22050.,
          env = Math.pow(1 - i / (double) n, 2),
          wave =
              switch (s) {
                case CLICK -> Math.sin(t * 2 * Math.PI * 700) * .6 + r.nextDouble() * .2;
                case PICKUP -> Math.sin(t * 2 * Math.PI * (900 + 900 * t)) * .7;
                case DAMAGE -> (r.nextDouble() * 2 - 1) * .6 + Math.sin(t * 2 * Math.PI * 110) * .4;
                case SPLASH -> (r.nextDouble() * 2 - 1) * .5 + Math.sin(t * 2 * Math.PI * 180) * .3;
                case EXPLODE -> (r.nextDouble() * 2 - 1) * .9;
                case DIG, STEP ->
                    (r.nextDouble() * 2 - 1) * .65 + Math.sin(t * 2 * Math.PI * 90) * .15;
              };
      int sample = (int) (Math.max(-1, Math.min(1, wave * env * volume)) * 24000);
      bytes[i * 2] = (byte) sample;
      bytes[i * 2 + 1] = (byte) (sample >> 8);
    }
    return bytes;
  }

  private static byte[] melody(float volume) {
    int n = 22050;
    byte[] b = new byte[n * 2];
    double[] notes = {261.63, 329.63, 392, 523.25};
    for (int i = 0; i < n; i++) {
      double t = i / 22050., phase = (i % 5512) / 5512.;
      double v =
          Math.sin(t * 2 * Math.PI * notes[Math.min(3, i / 5512)])
              * .1
              * Math.sin(phase * Math.PI)
              * volume;
      short s = (short) (v * 32767);
      b[i * 2] = (byte) s;
      b[i * 2 + 1] = (byte) (s >> 8);
    }
    return b;
  }

  public void close() {
    running = false;
    thread.interrupt();
    SourceDataLine l = line;
    if (l != null) l.close();
  }
}
