package dev.opencraft.game;

import java.io.*;
import java.util.Arrays;

public final class Inventory {
  private final Stack[] slots;

  public Inventory(int size) {
    slots = new Stack[size];
    Arrays.fill(slots, Stack.EMPTY);
  }

  public int size() {
    return slots.length;
  }

  public Stack get(int i) {
    return slots[i];
  }

  public void set(int i, Stack s) {
    slots[i] = s == null ? Stack.EMPTY : s;
  }

  /** Returns the unaccepted remainder; merging never loses items. */
  public Stack add(Stack s) {
    if (s.empty()) return s;
    int left = s.count();
    for (int pass = 0; pass < 2; pass++)
      for (int i = 0; i < size(); i++) {
        Stack a = get(i);
        if (pass == 0 && (a.empty() || !a.compatible(s)) || pass == 1 && !a.empty()) continue;
        int n = Math.min(left, s.max() - a.count());
        if (n > 0) {
          set(i, new Stack(s.id(), a.count() + n, s.damage()));
          left -= n;
        }
        if (left == 0) return Stack.EMPTY;
      }
    return s.withCount(left);
  }

  public Stack remove(int i, int amount) {
    Stack s = get(i);
    int n = Math.min(amount, s.count());
    set(i, s.withCount(s.count() - n));
    return s.withCount(n);
  }

  public int count(int id) {
    int n = 0;
    for (Stack s : slots) if (s.id() == id) n += s.count();
    return n;
  }

  public int capacity(Stack s) {
    int n = 0;
    for (Stack a : slots) if (a.empty() || a.compatible(s)) n += s.max() - a.count();
    return n;
  }

  public Stack insert(int index, Stack s) {
    if (s.empty()) return s;
    Stack existing = get(index);
    if (!existing.empty() && !existing.compatible(s)) return s;
    int count = Math.min(s.count(), s.max() - existing.count());
    if (count > 0) set(index, new Stack(s.id(), existing.count() + count, s.damage()));
    return s.withCount(s.count() - count);
  }

  public boolean consume(int id, int n) {
    if (count(id) < n) return false;
    for (int i = 0; i < size() && n > 0; i++)
      if (get(i).id() == id) {
        int take = Math.min(n, get(i).count());
        remove(i, take);
        n -= take;
      }
    return true;
  }

  /** Classic left-click swap/merge and right-click split/place-one semantics. */
  public Stack click(int i, Stack cursor, boolean right) {
    Stack a = get(i);
    if (cursor.empty()) return right ? remove(i, (a.count() + 1) / 2) : remove(i, a.count());
    if (a.empty() || a.compatible(cursor)) {
      int n = Math.min(right ? 1 : cursor.count(), cursor.max() - a.count());
      set(i, new Stack(cursor.id(), a.count() + n, cursor.damage()));
      return cursor.withCount(cursor.count() - n);
    }
    if (right) return cursor;
    set(i, cursor);
    return a;
  }

  public void write(DataOutput out) throws IOException {
    out.writeInt(size());
    for (Stack s : slots) {
      out.writeInt(s.id());
      out.writeByte(s.count());
      out.writeInt(s.damage());
    }
  }

  public static Inventory read(DataInput in, int expected) throws IOException {
    int n = in.readInt();
    if (n != expected) throw new IOException("Invalid inventory size " + n);
    var inv = new Inventory(n);
    for (int i = 0; i < n; i++) {
      int id = in.readInt(), count = in.readUnsignedByte(), damage = in.readInt();
      try {
        inv.set(i, new Stack(id, count, damage));
      } catch (IllegalArgumentException e) {
        throw new IOException("Corrupt stack", e);
      }
    }
    return inv;
  }
}
