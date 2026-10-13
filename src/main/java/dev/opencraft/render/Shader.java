package dev.opencraft.render;

import static org.lwjgl.opengl.GL33C.*;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class Shader implements AutoCloseable {
  public final int id;

  public Shader(String vertex, String fragment) {
    int v = compile(GL_VERTEX_SHADER, vertex), f = 0;
    try {
      f = compile(GL_FRAGMENT_SHADER, fragment);
      id = glCreateProgram();
      glAttachShader(id, v);
      glAttachShader(id, f);
      glLinkProgram(id);
      if (glGetProgrami(id, GL_LINK_STATUS) == 0) {
        String log = glGetProgramInfoLog(id);
        glDeleteProgram(id);
        throw new IllegalStateException(log);
      }
    } finally {
      glDeleteShader(v);
      if (f != 0) glDeleteShader(f);
    }
  }

  private static int compile(int type, String source) {
    int id = glCreateShader(type);
    glShaderSource(id, source);
    glCompileShader(id);
    if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) {
      String log = glGetShaderInfoLog(id);
      glDeleteShader(id);
      throw new IllegalStateException(log);
    }
    return id;
  }

  public void use() {
    glUseProgram(id);
  }

  public void matrix(String n, Matrix4f m) {
    try (var stack = MemoryStack.stackPush()) {
      glUniformMatrix4fv(glGetUniformLocation(id, n), false, m.get(stack.mallocFloat(16)));
    }
  }

  public void value(String n, float v) {
    glUniform1f(glGetUniformLocation(id, n), v);
  }

  public void vector(String n, float x, float y, float z) {
    glUniform3f(glGetUniformLocation(id, n), x, y, z);
  }

  public void close() {
    glDeleteProgram(id);
  }
}
