package dev.opencraft.world;

import java.util.Random;

/** Seeded improved Perlin noise, with octave composition. */
public final class Noise {
  private final int[] p = new int[512];

  public Noise(long seed) {
    var r = new Random(seed);
    int[] a = new int[256];
    for (int i = 0; i < 256; i++) a[i] = i;
    for (int i = 255; i > 0; i--) {
      int j = r.nextInt(i + 1), t = a[i];
      a[i] = a[j];
      a[j] = t;
    }
    for (int i = 0; i < 512; i++) p[i] = a[i & 255];
  }

  private static double fade(double t) {
    return t * t * t * (t * (t * 6 - 15) + 10);
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  private static double grad(int h, double x, double y, double z) {
    h &= 15;
    double u = h < 8 ? x : y, v = h < 4 ? y : h == 12 || h == 14 ? x : z;
    return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
  }

  public double at(double x, double y, double z) {
    int xi = (int) Math.floor(x) & 255,
        yi = (int) Math.floor(y) & 255,
        zi = (int) Math.floor(z) & 255;
    x -= Math.floor(x);
    y -= Math.floor(y);
    z -= Math.floor(z);
    double u = fade(x), v = fade(y), w = fade(z);
    int a = p[xi] + yi,
        aa = p[a] + zi,
        ab = p[a + 1] + zi,
        b = p[xi + 1] + yi,
        ba = p[b] + zi,
        bb = p[b + 1] + zi;
    return lerp(
        lerp(
            lerp(grad(p[aa], x, y, z), grad(p[ba], x - 1, y, z), u),
            lerp(grad(p[ab], x, y - 1, z), grad(p[bb], x - 1, y - 1, z), u),
            v),
        lerp(
            lerp(grad(p[aa + 1], x, y, z - 1), grad(p[ba + 1], x - 1, y, z - 1), u),
            lerp(grad(p[ab + 1], x, y - 1, z - 1), grad(p[bb + 1], x - 1, y - 1, z - 1), u),
            v),
        w);
  }

  public double octave(double x, double y, double z, int n) {
    double sum = 0, weight = 0, amp = 1;
    for (int i = 0; i < n; i++) {
      sum += at(x, y, z) * amp;
      weight += amp;
      x *= 2;
      y *= 2;
      z *= 2;
      amp *= .5;
    }
    return sum / weight;
  }
}
