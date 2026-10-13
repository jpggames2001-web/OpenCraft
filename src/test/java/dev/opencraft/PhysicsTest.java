package dev.opencraft;

import static org.junit.jupiter.api.Assertions.*;

import dev.opencraft.game.Player;
import dev.opencraft.physics.Aabb;
import dev.opencraft.world.*;
import org.junit.jupiter.api.Test;

class PhysicsTest {
  private World island() {
    World w = new World(new Generator(42, Generator.Type.SKYBLOCK));
    w.player.x = .5;
    w.player.y = 65;
    w.player.z = -.5;
    for (int x = -1; x <= 5; x++)
      for (int z = -2; z <= 0; z++) for (int y = 65; y < 81; y++) w.set(x, y, z, Block.AIR, 0);
    for (int i = 0; i < 2; i++) w.player.tick(w, Player.Input.NONE);
    return w;
  }

  @Test
  void touchingFacesAreNotIntersections() {
    Aabb a = new Aabb(0, 0, 0, 1, 1, 1);
    assertFalse(a.intersects(a.move(1, 0, 0)));
    assertTrue(a.intersects(a.move(.99, 0, 0)));
  }

  @Test
  void wallClipsMovementWithoutPenetration() {
    World w = island();
    w.set(1, 65, -1, Block.STONE, 0);
    w.set(1, 66, -1, Block.STONE, 0);
    w.player.move(w, 2, 0, 0);
    assertEquals(.7, w.player.x, 1e-8);
  }

  @Test
  void gravityLandsOnSurface() {
    World w = island();
    w.player.y = 70;
    for (int i = 0; i < 80; i++) w.player.tick(w, Player.Input.NONE);
    assertEquals(65, w.player.y, 1e-8);
    assertTrue(w.player.onGround);
    assertTrue(w.player.health < 20);
  }

  @Test
  void classicJumpApexIsApproximatelyOneAndAQuarterBlocks() {
    World w = island();
    w.player.tick(w, new Player.Input(0, 0, true, false, false));
    double apex = w.player.y;
    for (int i = 0; i < 40; i++) {
      w.player.tick(w, Player.Input.NONE);
      apex = Math.max(apex, w.player.y);
    }
    assertTrue(apex > 66.2 && apex < 66.3, "apex " + apex);
    assertEquals(65, w.player.y, 1e-8);
  }

  @Test
  void playerStepsUpHalfSlab() {
    World w = island();
    w.set(1, 65, -1, Block.SLAB, 0);
    w.player.move(w, 1, -.08, 0);
    assertTrue(w.player.x > .7);
    assertEquals(65.5, w.player.y, 1e-8);
  }

  @Test
  void sneakingPreventsWalkingOffEdge() {
    World w = island();
    w.player.x = 3.5;
    w.player.sneaking = true;
    w.player.onGround = true;
    w.player.move(w, 2, -.08, 0);
    assertTrue(w.player.x < 4.31);
    assertEquals(65, w.player.y, 1e-8);
  }

  @Test
  void waterUsesReducedGravity() {
    World w = island();
    w.set(0, 65, -1, Block.WATER, 0);
    w.player.onGround = false;
    w.player.vy = 0;
    w.player.tick(w, Player.Input.NONE);
    assertEquals(-.02, w.player.vy, 1e-8);
  }

  @Test
  void creativePlayerIgnoresDamage() {
    Player p = new Player();
    p.creative = true;
    p.damage(100);
    assertEquals(20, p.health);
  }

  @Test
  void repeatedDamageRespectsInvulnerabilityWindow() {
    Player p = new Player();
    p.damage(4);
    p.damage(4);
    assertEquals(16, p.health);
  }

  @Test
  void speedConvergesToClassicWalkingDistance() {
    World w = island();
    for (int x = -10; x < 10; x++) for (int z = -20; z <= 0; z++) w.set(x, 64, z, Block.GRASS, 0);
    double start = w.player.z;
    for (int i = 0; i < 20; i++) w.player.tick(w, new Player.Input(0, 1, false, false, false));
    assertTrue(start - w.player.z > 3.8 && start - w.player.z < 4.5);
  }
}
