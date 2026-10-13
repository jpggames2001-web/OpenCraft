package dev.opencraft.render;

import static org.lwjgl.opengl.GL33C.*;

import dev.opencraft.Options;
import dev.opencraft.game.Player;
import dev.opencraft.world.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

public final class Renderer implements AutoCloseable {
  private record ChunkMesh(Mesh solid, Mesh alpha) implements AutoCloseable {
    public void close() {
      solid.close();
      alpha.close();
    }
  }

  private final Map<Long, ChunkMesh> meshes = new HashMap<>();
  private final Mesh entities = new Mesh(),
      selection = new Mesh(),
      clouds = new Mesh(),
      celestial = new Mesh(),
      hand = new Mesh();
  private World lastWorld;
  private final Options options;
  public final Assets assets = new Assets();
  public final Canvas canvas = new Canvas();
  private final Shader shader =
      new Shader(
          """
#version 330 core
layout(location=0) in vec3 position;layout(location=1) in vec2 uv;
layout(location=2) in vec3 color;layout(location=3) in vec2 light;
uniform mat4 projection;uniform mat4 view;
out vec2 texCoord;out vec3 tint;out vec2 lights;out float distanceToCamera;
void main(){vec4 p=view*vec4(position,1);gl_Position=projection*p;texCoord=uv;tint=color;lights=light;distanceToCamera=length(p.xyz);}
""",
          """
#version 330 core
in vec2 texCoord;in vec3 tint;in vec2 lights;in float distanceToCamera;
uniform sampler2D atlas;uniform vec3 fogColor;uniform float fogRange;uniform float daylight;uniform float brightness;uniform float anaglyph;
out vec4 outColor;
void main(){vec4 tex=texture(atlas,texCoord);if(tex.a<.1)discard;float light=max(lights.x*daylight,lights.y);light=mix(.05,1.0,light);light=mix(light,1.0,brightness*.5);vec3 rgb=tex.rgb*tint*light;rgb=mix(rgb,fogColor,smoothstep(fogRange*.45,fogRange,distanceToCamera));if(anaglyph>.5)rgb=vec3(dot(rgb,vec3(.3,.59,.11)),dot(rgb,vec3(.3,.7,0)),dot(rgb,vec3(.3,0,.7)));outColor=vec4(rgb,tex.a);}
""");

  public Renderer(Options options) {
    this.options = options;
  }

  public void invalidate() {
    for (ChunkMesh m : meshes.values()) m.close();
    meshes.clear();
    if (lastWorld != null) for (Chunk c : lastWorld.chunks.values()) c.meshDirty = true;
  }

  public void draw(World world, int width, int height, boolean panorama, double animation) {
    if (options.anaglyph) {
      glColorMask(true, true, true, true);
      glClearColor(0, 0, 0, 1);
      glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
      glColorMask(true, false, false, true);
      drawEye(world, width, height, panorama, animation, -.035f);
      glColorMask(false, true, true, true);
      drawEye(world, width, height, panorama, animation, .035f);
      glColorMask(true, true, true, true);
    } else drawEye(world, width, height, panorama, animation, 0);
  }

  private void drawEye(
      World world, int width, int height, boolean panorama, double animation, float eyeOffset) {
    if (world != lastWorld) {
      invalidate();
      lastWorld = world;
      for (Chunk c : world.chunks.values()) c.meshDirty = true;
    }
    Player p = world.player;
    float day = world.daylight();
    Biome biome = world.generator.biome((int) p.x, (int) p.z);
    float[] sky = biome.skyColor();
    float r = sky[0] * day, g = sky[1] * day, b = sky[2] * day;
    if (panorama) {
      r = .48f;
      g = .67f;
      b = .94f;
      day = 1;
    }
    Block eye =
        world.peek((int) Math.floor(p.x), (int) Math.floor(p.eyeY()), (int) Math.floor(p.z));
    float fog = options.radius() * 16f;
    if (eye.fluid() && !panorama) {
      r = eye.lava() ? .6f : .03f;
      g = .07f;
      b = eye.lava() ? .02f : .3f;
      fog = eye.lava() ? 3 : 14;
    }
    glViewport(0, 0, width, height);
    glClearColor(r, g, b, 1);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    shader.use();
    shader.matrix(
        "projection",
        new Matrix4f()
            .perspective(
                (float) Math.toRadians(options.fov + (p.sprinting ? 7 : 0)),
                width / (float) Math.max(1, height),
                .05f,
                512));
    double px = panorama ? 8 : p.x,
        py = panorama ? 82 : p.eyeY(),
        pz = panorama ? 8 : p.z,
        yaw = panorama ? animation * 3 : p.yaw,
        pitch = panorama ? 18 : p.pitch;
    px += Math.cos(Math.toRadians(yaw)) * eyeOffset;
    pz -= Math.sin(Math.toRadians(yaw)) * eyeOffset;
    Matrix4f view = new Matrix4f();
    if (!panorama && options.bobbing && p.onGround && Math.hypot(p.vx, p.vz) > .03)
      view.translate(
              (float) Math.sin(animation * 11) * .02f,
              (float) Math.abs(Math.cos(animation * 11)) * .02f,
              0)
          .rotateZ((float) Math.sin(animation * 11) * .008f);
    view.rotateX((float) Math.toRadians(pitch))
        .rotateY((float) Math.toRadians(yaw))
        .translate((float) -px, (float) -py, (float) -pz);
    shader.matrix("view", view);
    shader.vector("fogColor", r, g, b);
    shader.value("fogRange", fog);
    shader.value("daylight", day);
    shader.value("brightness", options.brightness);
    shader.value("anaglyph", options.anaglyph ? 1 : 0);
    glBindTexture(GL_TEXTURE_2D, assets.texture());
    drawSky(px, py, pz, world.time);
    shader.value("fogRange", fog);
    var frustum =
        new FrustumIntersection(
            new Matrix4f()
                .perspective(
                    (float) Math.toRadians(options.fov + (p.sprinting ? 7 : 0)),
                    width / (float) Math.max(1, height),
                    .05f,
                    512)
                .mul(view));
    int rebuilt = 0;
    int cx = (int) Math.floor(px) >> 4,
        cz = (int) Math.floor(pz) >> 4,
        radius = panorama ? 2 : options.radius();
    for (var it = meshes.entrySet().iterator(); it.hasNext(); ) {
      var e = it.next();
      if (!world.chunks.containsKey(e.getKey())) {
        e.getValue().close();
        it.remove();
      }
    }
    List<Chunk> visible =
        world.chunks.values().stream()
            .filter(
                c ->
                    Math.abs(c.x - cx) <= radius
                        && Math.abs(c.z - cz) <= radius
                        && (!options.advancedOpenGL
                            || frustum.testAab(
                                c.x * 16, 0, c.z * 16, c.x * 16 + 16, 128, c.z * 16 + 16)))
            .sorted(Comparator.comparingDouble(c -> Math.pow(c.x - cx, 2) + Math.pow(c.z - cz, 2)))
            .toList();
    for (Chunk c : visible) {
      long key = Chunk.key(c.x, c.z);
      ChunkMesh mesh = meshes.get(key);
      if ((mesh == null || c.meshDirty) && rebuilt < 2) {
        if (c.lightingDirty) Lighting.update(c, world);
        var result = Mesher.build(world, c, options.smoothLight, options.fancy);
        if (mesh == null) {
          mesh = new ChunkMesh(new Mesh(), new Mesh());
          meshes.put(key, mesh);
        }
        mesh.solid.upload(result.opaque());
        mesh.alpha.upload(result.transparent());
        c.meshDirty = false;
        rebuilt++;
      }
      if (mesh != null) mesh.solid.draw();
    }
    drawEntities(world, animation);
    if (options.clouds) drawClouds(px, pz, world.time);
    glDepthMask(false);
    for (int i = visible.size() - 1; i >= 0; i--) {
      Chunk c = visible.get(i);
      ChunkMesh m = meshes.get(Chunk.key(c.x, c.z));
      if (m != null) m.alpha.draw();
    }
    glDepthMask(true);
    if (!panorama) {
      World.Hit hit = world.target();
      if (hit != null) {
        Mesher.Floats f = new Mesher.Floats();
        var pos = hit.pos();
        Mesher.cube(
            f,
            pos.x() - .003f,
            pos.y() - .003f,
            pos.z() - .003f,
            1.006f,
            1.006f,
            1.006f,
            20,
            .2f,
            .2f,
            .2f,
            1,
            1);
        selection.upload(f.array());
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        selection.draw();
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
      }
      drawHand(p, animation);
    }
  }

  private void drawSky(double x, double y, double z, long time) {
    Mesher.Floats f = new Mesher.Floats();
    double angle = (time % 24000) / 24000. * Math.PI * 2;
    shader.value("fogRange", 1024);
    glDepthMask(false);
    Mesher.billboard(
        f,
        (float) (x + Math.cos(angle) * 100) - 6,
        (float) (y + Math.sin(angle) * 100) - 6,
        (float) z - 120,
        12,
        12,
        204,
        1);
    Mesher.billboard(
        f,
        (float) (x - Math.cos(angle) * 100) - 5,
        (float) (y - Math.sin(angle) * 100) - 5,
        (float) z - 120,
        10,
        10,
        205,
        1);
    if (lastWorld.daylight() < .3) {
      Random r = new Random(42);
      for (int i = 0; i < 80; i++)
        Mesher.billboard(
            f,
            (float) x + r.nextFloat() * 300 - 150,
            (float) y + r.nextFloat() * 160 + 10,
            (float) z - 140,
            .7f,
            .7f,
            206,
            1);
    }
    celestial.upload(f.array());
    celestial.draw();
    glDepthMask(true);
  }

  private void drawHand(Player player, double animation) {
    Mesher.Floats f = new Mesher.Floats();
    if (player.held().empty())
      Mesher.cube(f, 0, -.2f, 0, .16f, .5f, .16f, 35, .73f, .55f, .38f, 1, 0);
    else Mesher.item(f, player.held().id());
    hand.upload(f.array());
    glClear(GL_DEPTH_BUFFER_BIT);
    shader.matrix(
        "view",
        new Matrix4f()
            .translate(.35f, -.38f + (float) Math.sin(animation * 3) * .004f, -.8f)
            .rotateY(-.6f)
            .rotateX(-.15f));
    shader.value("fogRange", 1024);
    hand.draw();
  }

  private void drawEntities(World w, double animation) {
    Mesher.Floats f = new Mesher.Floats();
    for (World.Drop d : w.drops) {
      int texture = d.stack.id();
      Mesher.cube(
          f,
          (float) d.x - .12f,
          (float) (d.y + .1 + Math.sin(animation * 3) * .04),
          (float) d.z - .12f,
          .24f,
          .24f,
          .24f,
          texture,
          1,
          1,
          1,
          1,
          0);
    }
    for (World.Mob m : w.mobs) {
      int color =
          switch (m.kind) {
            case PIG -> 0xe4a7a7;
            case SHEEP -> 0xddddcc;
            case COW -> 0x6a503d;
            case CHICKEN -> 0xdddddd;
            case ZOMBIE -> 0x55924a;
            case SKELETON -> 0xccccb8;
            case SPIDER -> 0x493b33;
            case CREEPER -> 0x589749;
          };
      float r = ((color >> 16) & 255) / 255f,
          g = ((color >> 8) & 255) / 255f,
          b = (color & 255) / 255f;
      float height = m.hostile() ? 1.1f : .55f;
      Mesher.cube(
          f,
          (float) m.x - .3f,
          (float) m.y + .3f,
          (float) m.z - .22f,
          .6f,
          height,
          .44f,
          35,
          r,
          g,
          b,
          1,
          0);
      Mesher.cube(
          f,
          (float) m.x - .27f,
          (float) m.y + height + .3f,
          (float) m.z - .27f,
          .54f,
          .5f,
          .54f,
          35,
          r,
          g,
          b,
          1,
          0);
      for (float dx : new float[] {-.23f, .12f})
        Mesher.cube(
            f,
            (float) m.x + dx,
            (float) m.y,
            (float) m.z - .14f,
            .12f,
            .4f,
            .25f,
            35,
            r * .7f,
            g * .7f,
            b * .7f,
            1,
            0);
    }
    entities.upload(f.array());
    entities.draw();
  }

  private void drawClouds(double px, double pz, long time) {
    Mesher.Floats f = new Mesher.Floats();
    int ox = (int) Math.floor((px + time * .02) / 32) - 3, oz = (int) Math.floor(pz / 32) - 3;
    for (int x = ox; x < ox + 7; x++)
      for (int z = oz; z < oz + 7; z++)
        if (Math.floorMod(x * 13 + z * 17, 7) < 3)
          Mesher.cube(f, (float) (x * 32 - time * .02), 112, z * 32, 28, 3, 28, 35, 1, 1, 1, 1, 0);
    clouds.upload(f.array());
    clouds.draw();
  }

  public void screenshot(Path path, int w, int h) throws IOException {
    ByteBuffer bytes = BufferUtils.createByteBuffer(w * h * 4);
    glReadBuffer(GL_BACK);
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        int i = (x + (h - y - 1) * w) * 4;
        image.setRGB(
            x,
            y,
            (bytes.get(i) & 255) << 16 | (bytes.get(i + 1) & 255) << 8 | (bytes.get(i + 2) & 255));
      }
    Files.createDirectories(path.getParent());
    ImageIO.write(image, "png", path.toFile());
  }

  public void close() {
    invalidate();
    entities.close();
    selection.close();
    clouds.close();
    celestial.close();
    hand.close();
    shader.close();
    canvas.close();
    assets.close();
  }
}
