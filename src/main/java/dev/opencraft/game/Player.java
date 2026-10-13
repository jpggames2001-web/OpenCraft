package dev.opencraft.game;

import dev.opencraft.physics.Aabb;
import dev.opencraft.world.*;
import java.util.List;

public final class Player {
  public double x = .5, y = 66, z = .5, vx, vy, vz, yaw, pitch, fallDistance;
  public boolean onGround, sneaking, sprinting, flying, creative;
  public int health = 20, food = 20, selected, air = 300, hurtTicks, experience;
  public float saturation = 5, exhaustion;
  public Inventory inventory = new Inventory(36), armor = new Inventory(4);
  private int ticks;

  public record Input(double strafe, double forward, boolean jump, boolean sneak, boolean sprint) {
    public static final Input NONE = new Input(0, 0, false, false, false);
  }

  public Aabb bounds() {
    return new Aabb(x - .3, y, z - .3, x + .3, y + 1.8, z + .3);
  }

  public Stack held() {
    return inventory.get(selected);
  }

  public void held(Stack s) {
    inventory.set(selected, s);
  }

  public double eyeY() {
    return y + 1.62 - (sneaking ? .08 : 0);
  }

  public void tick(World world, Input input) {
    ticks++;
    if (hurtTicks > 0) hurtTicks--;
    sneaking = input.sneak;
    sprinting = input.sprint && food > 6 && !sneaking;
    Block feet = world.block((int) Math.floor(x), (int) Math.floor(y + .1), (int) Math.floor(z));
    boolean water = feet == Block.WATER || feet == Block.WATER_FLOW, lava = feet.lava();
    double strafe = input.strafe, forward = input.forward;
    if (sneaking) {
      strafe *= .3;
      forward *= .3;
    }
    double slipperiness =
        world.block((int) Math.floor(x), (int) Math.floor(y - .1), (int) Math.floor(z)) == Block.ICE
            ? .98
            : .6;
    double friction = onGround ? slipperiness * .91 : .91;
    double accel =
        flying
            ? .05
            : water || lava
                ? .02
                : onGround ? .1 * (.16277136 / (friction * friction * friction)) : .02;
    if (sprinting) accel *= 1.3;
    double len = Math.sqrt(strafe * strafe + forward * forward);
    if (len > .01) {
      double scale = accel / Math.max(1, len), a = Math.toRadians(yaw);
      vx += (strafe * Math.cos(a) - forward * Math.sin(a)) * scale;
      vz += (-forward * Math.cos(a) - strafe * Math.sin(a)) * scale;
    }
    if (input.jump) {
      if (flying) vy = .15;
      else if (water || lava) vy += .04;
      else if (onGround) {
        vy = .42;
        if (sprinting) {
          vx -= Math.sin(Math.toRadians(yaw)) * .2;
          vz -= Math.cos(Math.toRadians(yaw)) * .2;
        }
        exhaustion += sprinting ? .8 : .2;
      }
    }
    if (flying && input.sneak) vy = -.15;
    if (feet == Block.COBWEB) {
      vx *= .25;
      vy *= .05;
      vz *= .25;
    }
    if (feet == Block.LADDER || feet == Block.VINES) {
      vx = Math.max(-.15, Math.min(.15, vx));
      vz = Math.max(-.15, Math.min(.15, vz));
      vy = Math.max(sneaking ? 0 : -.15, vy);
      fallDistance = 0;
    }
    move(world, vx, vy, vz);
    if (flying) {
      vy *= .6;
      vx *= .91;
      vz *= .91;
      fallDistance = 0;
    } else if (water) {
      vx *= .8;
      vz *= .8;
      vy = vy * .8 - .02;
      fallDistance = 0;
    } else if (lava) {
      vx *= .5;
      vz *= .5;
      vy = vy * .5 - .02;
      fallDistance = 0;
    } else {
      vy = (vy - .08) * .98;
      vx *= friction;
      vz *= friction;
    }
    if (sprinting) exhaustion += (float) (Math.sqrt(vx * vx + vz * vz) * .1);
    if (world.block((int) Math.floor(x), (int) Math.floor(eyeY()), (int) Math.floor(z)).fluid()
        && !creative) {
      air--;
      if (air <= -20) {
        air = 0;
        damage(2);
      }
    } else air = 300;
    if (!creative && ticks % 20 == 0) {
      if (lava || feet == Block.FIRE) damage(4);
      if (feet == Block.CACTUS) damage(1);
    }
    if (y < -64) {
      boolean wasCreative = creative;
      creative = false;
      damage(4);
      creative = wasCreative;
    }
    if (!creative) {
      if (exhaustion >= 4) {
        exhaustion -= 4;
        if (saturation > 0) saturation = Math.max(0, saturation - 1);
        else food = Math.max(0, food - 1);
      }
      if (ticks % 80 == 0) {
        if (food >= 18 && health < 20) {
          health++;
          exhaustion += 3;
        } else if (food == 0 && (wantsStarvation(world.difficulty))) damage(1);
      }
    }
  }

  public void move(World w, double dx, double dy, double dz) {
    Aabb a = bounds();
    double ox = dx, oy = dy, oz = dz;
    if (onGround && sneaking) {
      while (dx != 0 && w.collisions(a.move(dx, -1, 0)).isEmpty()) dx = reduce(dx, .05);
      while (dz != 0 && w.collisions(a.move(0, -1, dz)).isEmpty()) dz = reduce(dz, .05);
      while (dx != 0 && dz != 0 && w.collisions(a.move(dx, -1, dz)).isEmpty()) {
        dx = reduce(dx, .05);
        dz = reduce(dz, .05);
      }
    }
    double wantedX = dx, wantedZ = dz;
    List<Aabb> boxes = w.collisions(a.expand(dx, dy, dz));
    for (Aabb b : boxes) dy = b.clipY(a, dy);
    a = a.move(0, dy, 0);
    for (Aabb b : boxes) dx = b.clipX(a, dx);
    a = a.move(dx, 0, 0);
    for (Aabb b : boxes) dz = b.clipZ(a, dz);
    a = a.move(0, 0, dz);
    if ((onGround || oy < 0 && oy != dy) && (wantedX != dx || wantedZ != dz)) {
      Aabb step = bounds();
      double sy = .5, sx = wantedX, sz = wantedZ;
      List<Aabb> candidates = w.collisions(step.expand(sx, sy, sz));
      for (Aabb b : candidates) sy = b.clipY(step, sy);
      step = step.move(0, sy, 0);
      for (Aabb b : candidates) sx = b.clipX(step, sx);
      step = step.move(sx, 0, 0);
      for (Aabb b : candidates) sz = b.clipZ(step, sz);
      step = step.move(0, 0, sz);
      double down = -sy;
      for (Aabb b : candidates) down = b.clipY(step, down);
      step = step.move(0, down, 0);
      if (sx * sx + sz * sz > dx * dx + dz * dz) {
        a = step;
        dx = sx;
        dz = sz;
        dy = sy + down;
      }
    }
    x = (a.minX() + a.maxX()) / 2;
    y = a.minY();
    z = (a.minZ() + a.maxZ()) / 2;
    onGround = oy != dy && oy < 0;
    if (ox != dx) vx = 0;
    if (oy != dy) vy = 0;
    if (oz != dz) vz = 0;
    if (onGround) {
      if (fallDistance > 3 && !creative) damage((int) Math.ceil(fallDistance - 3));
      fallDistance = 0;
    } else if (dy < 0) fallDistance -= dy;
  }

  private static double reduce(double v, double n) {
    return Math.abs(v) < n ? 0 : v - Math.signum(v) * n;
  }

  private boolean wantsStarvation(int difficulty) {
    return switch (difficulty) {
      case 0 -> false;
      case 1 -> health > 10;
      case 2 -> health > 1;
      default -> health > 0;
    };
  }

  public void damage(int amount) {
    if (creative || hurtTicks > 0 || health <= 0) return;
    int protection = 0;
    for (int i = 0; i < 4; i++)
      if (!armor.get(i).empty()) {
        protection += 2 + Items.get(armor.get(i).id()).tier();
        armor.set(i, armor.get(i).hurt());
      }
    health = Math.max(0, health - Math.max(1, amount * (25 - Math.min(20, protection)) / 25));
    hurtTicks = 10;
  }

  public boolean eat() {
    Stack s = held();
    int n = Items.get(s.id()).food();
    if (n == 0 || food == 20) return false;
    food = Math.min(20, food + n);
    saturation = Math.min(food, saturation + n * .6f);
    held(s.withCount(s.count() - 1));
    if (s.id() == 282) inventory.add(new Stack(281, 1));
    return true;
  }

  public void respawn(World w) {
    x = w.spawnX + .5;
    z = w.spawnZ + .5;
    y = w.spawnY;
    vx = vy = vz = 0;
    fallDistance = 0;
    health = food = 20;
    air = 300;
    flying = false;
  }
}
