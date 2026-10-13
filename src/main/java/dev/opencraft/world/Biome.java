package dev.opencraft.world;

public enum Biome {
  OCEAN(.5, .5, 0x68a84f, 0x55a44a),
  PLAINS(.8, .4, 0x91bd59, 0x77ab2f),
  DESERT(2, 0, 0xbfb755, 0xaea42a),
  EXTREME_HILLS(.2, .3, 0x8ab689, 0x6da36b),
  FOREST(.7, .8, 0x79c05a, 0x59ae30),
  TAIGA(.05, .8, 0x80b497, 0x619961),
  SWAMPLAND(.8, .9, 0x6a7039, 0x6a7039),
  RIVER(.5, .5, 0x68a84f, 0x55a44a),
  FROZEN_OCEAN(0, .5, 0x80b497, 0x619961),
  FROZEN_RIVER(0, .5, 0x80b497, 0x619961),
  ICE_PLAINS(0, .5, 0x80b497, 0x619961),
  ICE_MOUNTAINS(0, .5, 0x80b497, 0x619961),
  MUSHROOM_ISLAND(.9, 1, 0x55c93f, 0x2bbb0f),
  MUSHROOM_SHORE(.9, 1, 0x55c93f, 0x2bbb0f);
  public final double temperature, rainfall;
  public final int grass, foliage;

  Biome(double t, double r, int g, int f) {
    temperature = t;
    rainfall = r;
    grass = g;
    foliage = f;
  }

  public boolean snowy() {
    return temperature < .15;
  }

  public float[] skyColor() {
    float t = (float) Math.max(-1, Math.min(1, temperature / 3));
    int rgb = java.awt.Color.HSBtoRGB(.62222224f - t * .05f, .5f + t * .1f, 1f);
    return new float[] {((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
  }
}
