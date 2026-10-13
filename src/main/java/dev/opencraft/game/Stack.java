package dev.opencraft.game;

public record Stack(int id, int count, int damage) {
  public static final Stack EMPTY = new Stack(0, 0, 0);

  public Stack {
    if (count < 0 || count > 64 || damage < 0) throw new IllegalArgumentException("Invalid stack");
    if (count == 0) {
      id = 0;
      damage = 0;
    }
  }

  public Stack(int id, int count) {
    this(id, count, 0);
  }

  public boolean empty() {
    return count == 0;
  }

  public Stack withCount(int n) {
    return n <= 0 ? EMPTY : new Stack(id, n, damage);
  }

  public boolean compatible(Stack s) {
    return id == s.id && damage == s.damage;
  }

  public int max() {
    return Items.get(id).maxStack();
  }

  public Stack hurt() {
    var item = Items.get(id);
    return item.durability() == 0
        ? this
        : damage + 1 >= item.durability() ? EMPTY : new Stack(id, count, damage + 1);
  }
}
