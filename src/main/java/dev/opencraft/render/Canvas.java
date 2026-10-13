package dev.opencraft.render;

import static org.lwjgl.opengl.GL33C.*;

import org.joml.Matrix4f;

public final class Canvas implements AutoCloseable {
  private final Mesh mesh = new Mesh();
  private final Shader shader =
      new Shader(
          """
#version 330 core
layout(location=0) in vec3 pos;
layout(location=1) in vec2 uv;
layout(location=2) in vec3 color;
layout(location=3) in vec2 flags;
uniform mat4 projection;
out vec2 texCoord;out vec4 tint;out float textured;
void main(){gl_Position=projection*vec4(pos,1);texCoord=uv;tint=vec4(color,flags.x);textured=flags.y;}
""",
          """
#version 330 core
in vec2 texCoord;in vec4 tint;in float textured;
uniform sampler2D atlas;out vec4 outColor;
void main(){outColor=tint*mix(vec4(1),texture(atlas,texCoord),textured);if(outColor.a<.01)discard;}
""");
  private Mesher.Floats vertices = new Mesher.Floats();
  public int width, height;

  public void begin(int w, int h) {
    width = w;
    height = h;
    vertices = new Mesher.Floats();
  }

  public void rect(float x, float y, float w, float h, int color) {
    quad(x, y, w, h, 0, 0, 0, 0, color, 0);
  }

  public void tile(float x, float y, float w, float h, int id) {
    quad(x, y, w, h, (id % 32) / 32f, (id / 32) / 32f, 1 / 32f, 1 / 32f, 0xffffffff, 1);
  }

  private void quad(
      float x,
      float y,
      float w,
      float h,
      float u,
      float v,
      float du,
      float dv,
      int color,
      float textured) {
    float r = ((color >> 16) & 255) / 255f,
        g = ((color >> 8) & 255) / 255f,
        b = (color & 255) / 255f,
        a = ((color >>> 24) & 255) / 255f;
    float[][] corners = {
      {x, y, u, v}, {x, y + h, u, v + dv}, {x + w, y + h, u + du, v + dv}, {x + w, y, u + du, v}
    };
    for (int n : new int[] {0, 1, 2, 0, 2, 3}) {
      var p = corners[n];
      vertices.add(p[0], p[1], 0, p[2], p[3], r, g, b, a, textured);
    }
  }

  public void text(String s, float x, float y, int color) {
    BitmapFont.draw(this, s, x, y, color, 1, true);
  }

  public void text(String s, float x, float y, int color, int scale) {
    BitmapFont.draw(this, s, x, y, color, scale, true);
  }

  public void centered(String s, float y, int color) {
    text(s, (width - BitmapFont.width(s, 1)) / 2f, y, color);
  }

  public void border(float x, float y, float w, float h, int color) {
    rect(x, y, w, 1, color);
    rect(x, y + h - 1, w, 1, color);
    rect(x, y, 1, h, color);
    rect(x + w - 1, y, 1, h, color);
  }

  public void dirt() {
    for (int y = 0; y < height; y += 32) for (int x = 0; x < width; x += 32) tile(x, y, 32, 32, 3);
    rect(0, 0, width, height, 0xb0000000);
  }

  public void end(int texture) {
    shader.use();
    shader.matrix("projection", new Matrix4f().ortho2D(0, width, height, 0));
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glBindTexture(GL_TEXTURE_2D, texture);
    mesh.upload(vertices.array());
    mesh.draw();
  }

  public void close() {
    mesh.close();
    shader.close();
  }
}
