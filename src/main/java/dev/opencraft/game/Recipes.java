package dev.opencraft.game;

import java.util.*;

public final class Recipes {
  public record Match(Stack result, int[] consumed) {
    public Match {
      consumed = consumed.clone();
    }

    public int[] consumed() {
      return consumed.clone();
    }
  }

  private record Recipe(int w, int h, int[] input, Stack output, boolean shapeless) {}

  private static final List<Recipe> RECIPES = new ArrayList<>();

  static {
    shape(5, 4, "L", 'L', 17);
    shape(280, 4, "P", "P", 'P', 5);
    shape(58, 1, "PP", "PP", 'P', 5);
    shape(54, 1, "PPP", "P P", "PPP", 'P', 5);
    shape(61, 1, "CCC", "C C", "CCC", 'C', 4);
    shape(50, 4, "C", "S", 'C', 263, 'S', 280);
    int[][] tools = {
      {268, 269, 270, 271, 290, 5},
      {272, 273, 274, 275, 291, 4},
      {267, 256, 257, 258, 292, 265},
      {276, 277, 278, 279, 293, 264},
      {283, 284, 285, 286, 294, 266}
    };
    for (int[] t : tools) {
      shape(t[0], 1, "M", "M", "S", 'M', t[5], 'S', 280);
      shape(t[1], 1, "M", "S", "S", 'M', t[5], 'S', 280);
      shape(t[2], 1, "MMM", " S ", " S ", 'M', t[5], 'S', 280);
      shape(t[3], 1, "MM", "MS", " S", 'M', t[5], 'S', 280);
      shape(t[4], 1, "MM", " S", " S", 'M', t[5], 'S', 280);
    }
    shape(325, 1, "I I", " I ", 'I', 265);
    shape(85, 2, "SSS", "SSS", 'S', 280);
    shape(107, 1, "SPS", "SPS", 'S', 280, 'P', 5);
    shape(65, 3, "S S", "SSS", "S S", 'S', 280);
    shape(324, 1, "PP", "PP", "PP", 'P', 5);
    shape(330, 1, "II", "II", "II", 'I', 265);
    shape(355, 1, "WWW", "PPP", 'W', 35, 'P', 5);
    shape(297, 1, "WWW", 'W', 296);
    shape(281, 4, "P P", " P ", 'P', 5);
    shape(46, 1, "GSG", "SGS", "GSG", 'G', 289, 'S', 12);
    shape(45, 1, "BB", "BB", 'B', 336);
    shape(24, 1, "SS", "SS", 'S', 12);
    shape(89, 1, "GG", "GG", 'G', 348);
    shape(80, 1, "SS", "SS", 'S', 332);
    shape(82, 1, "CC", "CC", 'C', 337);
    for (int[] pair : new int[][] {{41, 266}, {42, 265}, {57, 264}, {22, 351}}) {
      shape(pair[0], 1, "III", "III", "III", 'I', pair[1]);
      shape(pair[1], 9, "B", 'B', pair[0]);
    }
    shape(53, 4, "P  ", "PP ", "PPP", 'P', 5);
    shape(67, 4, "C  ", "CC ", "CCC", 'C', 4);
    shape(108, 4, "C  ", "CC ", "CCC", 'C', 45);
    shape(109, 4, "C  ", "CC ", "CCC", 'C', 98);
    shape(98, 4, "SS", "SS", 'S', 1);
    shape(44, 3, "SSS", 'S', 1);
    shape(96, 2, "PPP", "PPP", 'P', 5);
    shape(66, 16, "I I", "ISI", "I I", 'I', 265, 'S', 280);
    shape(27, 6, "G G", "GSG", "GRG", 'G', 266, 'S', 280, 'R', 331);
    shape(328, 1, "I I", "III", 'I', 265);
    shape(333, 1, "P P", "PPP", 'P', 5);
    shape(345, 1, " I ", "IRI", " I ", 'I', 265, 'R', 331);
    shape(347, 1, " G ", "GRG", " G ", 'G', 266, 'R', 331);
    shape(359, 1, " I", "I ", 'I', 265);
    shape(339, 3, "SSS", 'S', 338);
    shape(340, 1, "P", "P", "P", 'P', 339);
    shape(47, 1, "PPP", "BBB", "PPP", 'P', 5, 'B', 340);
    shape(323, 1, "PPP", "PPP", " S ", 'P', 5, 'S', 280);
    shape(262, 4, "F", "S", "E", 'F', 318, 'S', 280, 'E', 288);
    shape(261, 1, " SE", "S E", " SE", 'S', 280, 'E', 287);
    shape(76, 1, "R", "S", 'R', 331, 'S', 280);
    shape(69, 1, "S", "C", 'S', 280, 'C', 4);
    shape(77, 1, "S", "S", 'S', 1);
    shape(70, 1, "SS", 'S', 1);
    shape(72, 1, "PP", 'P', 5);
    shape(356, 1, "TRT", "SSS", 'T', 76, 'R', 331, 'S', 1);
    shape(354, 1, "MMM", "SES", "WWW", 'M', 335, 'S', 353, 'E', 344, 'W', 296);
    shape(357, 8, "WCW", 'W', 296, 'C', 351);
    shape(101, 16, "III", "III", 'I', 265);
    shape(102, 16, "GGG", "GGG", 'G', 20);
    shape(23, 1, "CCC", "CBC", "CRC", 'C', 4, 'B', 261, 'R', 331);
    shape(33, 1, "PPP", "CIC", "CRC", 'P', 5, 'C', 4, 'I', 265, 'R', 331);
    shape(116, 1, " B ", "DOD", "OOO", 'B', 340, 'D', 264, 'O', 49);
    shape(379, 1, " R ", "CCC", 'R', 369, 'C', 4);
    shape(380, 1, "I I", "I I", "III", 'I', 265);
    int[][] armor = {{334, 298}, {265, 306}, {264, 310}, {266, 314}};
    for (int[] a : armor) {
      shape(a[1], 1, "MMM", "M M", 'M', a[0]);
      shape(a[1] + 1, 1, "M M", "MMM", "MMM", 'M', a[0]);
      shape(a[1] + 2, 1, "MMM", "M M", "M M", 'M', a[0]);
      shape(a[1] + 3, 1, "M M", "M M", 'M', a[0]);
    }
    shapeless(282, 1, 281, 39, 40);
    shapeless(353, 1, 338);
    shapeless(371, 9, 266);
    shapeless(266, 1, 371, 371, 371, 371, 371, 371, 371, 371, 371);
    shapeless(377, 2, 369);
    shapeless(381, 1, 368, 377);
    shapeless(378, 1, 341, 377);
    shapeless(382, 1, 360, 371);
    shapeless(361, 4, 86);
    shapeless(362, 1, 360);
    shapeless(29, 1, 33, 341);
    shapeless(342, 1, 328, 54);
    shapeless(343, 1, 328, 61);
    shapeless(91, 1, 86, 50);
  }

  private static void shape(int id, int count, Object... args) {
    List<String> rows = new ArrayList<>();
    int p = 0;
    while (p < args.length && args[p] instanceof String) rows.add((String) args[p++]);
    Map<Character, Integer> map = new HashMap<>();
    while (p < args.length) {
      char k = (char) args[p++];
      map.put(k, (int) args[p++]);
    }
    int w = rows.getFirst().length(), h = rows.size();
    int[] in = new int[w * h];
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) in[x + y * w] = map.getOrDefault(rows.get(y).charAt(x), 0);
    RECIPES.add(new Recipe(w, h, in, new Stack(id, count), false));
  }

  private static void shapeless(int id, int count, int... in) {
    RECIPES.add(new Recipe(0, 0, in, new Stack(id, count), true));
  }

  public static Optional<Match> match(Inventory grid, int width) {
    int height = grid.size() / width;
    for (Recipe r : RECIPES) {
      if (r.shapeless) {
        List<Integer> need = new ArrayList<>();
        for (int i : r.input) need.add(i);
        List<Integer> used = new ArrayList<>();
        boolean ok = true;
        for (int i = 0; i < grid.size(); i++)
          if (!grid.get(i).empty()) {
            if (!need.remove(Integer.valueOf(grid.get(i).id()))) {
              ok = false;
              break;
            }
            used.add(i);
          }
        if (ok && need.isEmpty())
          return Optional.of(
              new Match(r.output, used.stream().mapToInt(Integer::intValue).toArray()));
      } else
        for (int mirror = 0; mirror < 2; mirror++)
          for (int oy = 0; oy <= height - r.h; oy++)
            for (int ox = 0; ox <= width - r.w; ox++) {
              boolean ok = true;
              List<Integer> used = new ArrayList<>();
              for (int y = 0; y < height && ok; y++)
                for (int x = 0; x < width; x++) {
                  int ix = x - ox, iy = y - oy;
                  int want =
                      ix < 0 || iy < 0 || ix >= r.w || iy >= r.h
                          ? 0
                          : r.input[(mirror == 0 ? ix : r.w - 1 - ix) + iy * r.w];
                  int slot = x + y * width;
                  if (grid.get(slot).id() != want) {
                    ok = false;
                    break;
                  }
                  if (want != 0) used.add(slot);
                }
              if (ok)
                return Optional.of(
                    new Match(r.output, used.stream().mapToInt(Integer::intValue).toArray()));
            }
    }
    return Optional.empty();
  }

  public static void consume(Inventory grid, Match match) {
    for (int i : match.consumed) {
      Stack s = grid.remove(i, 1);
      if (s.id() == 335) grid.set(i, new Stack(325, 1));
    }
  }

  public static int count() {
    return RECIPES.size();
  }
}
