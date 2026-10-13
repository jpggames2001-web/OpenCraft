package dev.opencraft.render;

import static org.lwjgl.opengl.GL33C.*;

import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;

public final class Mesh implements AutoCloseable {
  private final int vao = glGenVertexArrays(), vbo = glGenBuffers();
  private int count;

  public Mesh() {
    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    int[] lengths = {3, 2, 3, 2};
    int offset = 0;
    for (int i = 0; i < lengths.length; i++) {
      glEnableVertexAttribArray(i);
      glVertexAttribPointer(i, lengths[i], GL_FLOAT, false, 40, offset * 4L);
      offset += lengths[i];
    }
    glBindVertexArray(0);
  }

  public void upload(float[] vertices) {
    count = vertices.length / 10;
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
    try {
      buffer.put(vertices).flip();
      glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
    } finally {
      MemoryUtil.memFree(buffer);
    }
  }

  public void draw() {
    if (count > 0) {
      glBindVertexArray(vao);
      glDrawArrays(GL_TRIANGLES, 0, count);
    }
  }

  public void close() {
    glDeleteBuffers(vbo);
    glDeleteVertexArrays(vao);
  }
}
