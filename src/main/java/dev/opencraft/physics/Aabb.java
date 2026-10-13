package dev.opencraft.physics;

public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
  public Aabb move(double x, double y, double z) {
    return new Aabb(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
  }

  public Aabb expand(double x, double y, double z) {
    return new Aabb(
        minX + Math.min(0, x),
        minY + Math.min(0, y),
        minZ + Math.min(0, z),
        maxX + Math.max(0, x),
        maxY + Math.max(0, y),
        maxZ + Math.max(0, z));
  }

  public boolean intersects(Aabb a) {
    return maxX > a.minX
        && minX < a.maxX
        && maxY > a.minY
        && minY < a.maxY
        && maxZ > a.minZ
        && minZ < a.maxZ;
  }

  public double clipX(Aabb a, double d) {
    if (a.maxY <= minY || a.minY >= maxY || a.maxZ <= minZ || a.minZ >= maxZ) return d;
    if (d > 0 && a.maxX <= minX) d = Math.min(d, minX - a.maxX);
    if (d < 0 && a.minX >= maxX) d = Math.max(d, maxX - a.minX);
    return d;
  }

  public double clipY(Aabb a, double d) {
    if (a.maxX <= minX || a.minX >= maxX || a.maxZ <= minZ || a.minZ >= maxZ) return d;
    if (d > 0 && a.maxY <= minY) d = Math.min(d, minY - a.maxY);
    if (d < 0 && a.minY >= maxY) d = Math.max(d, maxY - a.minY);
    return d;
  }

  public double clipZ(Aabb a, double d) {
    if (a.maxX <= minX || a.minX >= maxX || a.maxY <= minY || a.minY >= maxY) return d;
    if (d > 0 && a.maxZ <= minZ) d = Math.min(d, minZ - a.maxZ);
    if (d < 0 && a.minZ >= maxZ) d = Math.max(d, maxZ - a.minZ);
    return d;
  }
}
