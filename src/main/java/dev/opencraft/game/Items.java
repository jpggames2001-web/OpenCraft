package dev.opencraft.game;

import dev.opencraft.world.Block;
import java.util.*;

public final class Items {
  public record Item(
      int id,
      String name,
      int maxStack,
      int durability,
      int food,
      int tier,
      String tool,
      int color) {}

  private static final Map<Integer, Item> ITEMS = new LinkedHashMap<>();
  public static final int STICK = 280,
      COAL = 263,
      IRON = 265,
      GOLD = 266,
      DIAMOND = 264,
      BUCKET = 325,
      WATER_BUCKET = 326,
      LAVA_BUCKET = 327;

  static {
    for (Block b : Block.values())
      if (b != Block.AIR) ITEMS.put(b.id, new Item(b.id, b.label, 64, 0, 0, 0, "", b.color));
    toolSet(268, 269, 270, 271, 290, "Wooden", 0, 59, 0xa87946);
    toolSet(272, 273, 274, 275, 291, "Stone", 1, 131, 0x999999);
    toolSet(267, 256, 257, 258, 292, "Iron", 2, 250, 0xd8d8d8);
    toolSet(276, 277, 278, 279, 293, "Diamond", 3, 1561, 0x55c8c0);
    toolSet(283, 284, 285, 286, 294, "Golden", 0, 32, 0xe9ce43);
    add(260, "Apple", 64, 4, 0xc23024);
    add(262, "Arrow", 64, 0, 0xc4bda0);
    add(263, "Coal", 64, 0, 0x303030);
    add(264, "Diamond", 64, 0, 0x58d6cd);
    add(265, "Iron Ingot", 64, 0, 0xcacaca);
    add(266, "Gold Ingot", 64, 0, 0xe1c744);
    ITEMS.put(261, new Item(261, "Bow", 1, 384, 0, 0, "bow", 0xa68148));
    add(280, "Stick", 64, 0, 0x967243);
    add(281, "Bowl", 64, 0, 0x9b723e);
    add(282, "Mushroom Stew", 1, 6, 0xab7850);
    add(287, "String", 64, 0, 0xdedede);
    add(288, "Feather", 64, 0, 0xe7e7df);
    add(289, "Gunpowder", 64, 0, 0x676767);
    add(295, "Seeds", 64, 0, 0x608e32);
    add(296, "Wheat", 64, 0, 0xceaa3e);
    add(297, "Bread", 64, 5, 0xad813b);
    String[] materials = {"Leather", "Chain", "Iron", "Diamond", "Golden"};
    int[] colors = {0x957552, 0x888888, 0xcccccc, 0x55c8c0, 0xe9ce43};
    for (int m = 0; m < 5; m++)
      for (int a = 0; a < 4; a++)
        ITEMS.put(
            298 + m * 4 + a,
            new Item(
                298 + m * 4 + a,
                materials[m] + " " + new String[] {"Helmet", "Chestplate", "Leggings", "Boots"}[a],
                1,
                80 + (m + 1) * 40,
                0,
                m,
                "armor",
                colors[m]));
    add(318, "Flint", 64, 0, 0x555555);
    add(319, "Raw Porkchop", 64, 3, 0xeab1ae);
    add(320, "Cooked Porkchop", 64, 8, 0xbb7952);
    add(321, "Painting", 64, 0, 0xb89649);
    add(322, "Golden Apple", 64, 4, 0xdcc33f);
    add(323, "Sign", 16, 0, 0xa87946);
    add(324, "Wooden Door", 1, 0, 0xa87946);
    add(325, "Bucket", 16, 0, 0xcacaca);
    add(326, "Water Bucket", 1, 0, 0x516dca);
    add(327, "Lava Bucket", 1, 0, 0xe88328);
    add(328, "Minecart", 1, 0, 0x999999);
    add(329, "Saddle", 1, 0, 0xab703a);
    add(330, "Iron Door", 1, 0, 0xcccccc);
    add(331, "Redstone", 64, 0, 0xb61e16);
    add(332, "Snowball", 16, 0, 0xf0f4f4);
    add(333, "Boat", 1, 0, 0xa87946);
    add(334, "Leather", 64, 0, 0x9c683d);
    add(335, "Milk Bucket", 1, 0, 0xeeeeee);
    add(336, "Brick", 64, 0, 0xac5b47);
    add(337, "Clay", 64, 0, 0xa7a7b2);
    add(338, "Sugar Cane", 64, 0, 0x7faa50);
    add(339, "Paper", 64, 0, 0xe8e8e0);
    add(340, "Book", 64, 0, 0x9b694d);
    add(341, "Slimeball", 64, 0, 0x72b84f);
    add(342, "Minecart with Chest", 1, 0, 0x94723c);
    add(343, "Minecart with Furnace", 1, 0, 0x888888);
    add(344, "Egg", 16, 0, 0xe5d8b2);
    add(345, "Compass", 64, 0, 0xbcbdc0);
    add(346, "Fishing Rod", 1, 0, 0x987547);
    add(347, "Clock", 64, 0, 0xdfc245);
    add(348, "Glowstone Dust", 64, 0, 0xd7c966);
    add(349, "Raw Fish", 64, 2, 0x819394);
    add(350, "Cooked Fish", 64, 5, 0xa39178);
    add(351, "Dye", 64, 0, 0x3455a6);
    add(352, "Bone", 64, 0, 0xdedec9);
    add(353, "Sugar", 64, 0, 0xffffff);
    add(354, "Cake", 1, 0, 0xd8b9a1);
    add(355, "Bed", 1, 0, 0xbb3935);
    add(356, "Redstone Repeater", 64, 0, 0xaa5555);
    add(357, "Cookie", 64, 2, 0xb57c3f);
    add(358, "Map", 1, 0, 0xd6ccad);
    ITEMS.put(359, new Item(359, "Shears", 1, 238, 0, 2, "shears", 0xb7b7b7));
    add(360, "Melon", 64, 2, 0xe95449);
    add(361, "Pumpkin Seeds", 64, 0, 0xc2ab83);
    add(362, "Melon Seeds", 64, 0, 0x705b3e);
    add(363, "Raw Beef", 64, 3, 0xac3e38);
    add(364, "Steak", 64, 8, 0x814133);
    add(365, "Raw Chicken", 64, 2, 0xe5b5a4);
    add(366, "Cooked Chicken", 64, 6, 0xad7948);
    add(367, "Rotten Flesh", 64, 4, 0x83533e);
    add(368, "Ender Pearl", 16, 0, 0x23786a);
    add(369, "Blaze Rod", 64, 0, 0xd4ac38);
    add(370, "Ghast Tear", 64, 0, 0xd4e1e1);
    add(371, "Gold Nugget", 64, 0, 0xdbbd3a);
    add(372, "Nether Wart", 64, 0, 0x9b293a);
    add(373, "Potion", 1, 0, 0x845ac2);
    add(374, "Glass Bottle", 64, 0, 0xd9e6e7);
    add(375, "Spider Eye", 64, 2, 0x863952);
    add(376, "Fermented Spider Eye", 64, 0, 0x5e2731);
    add(377, "Blaze Powder", 64, 0, 0xe7b229);
    add(378, "Magma Cream", 64, 0, 0xd18624);
    add(379, "Brewing Stand", 64, 0, 0xaaa15d);
    add(380, "Cauldron", 64, 0, 0x777777);
    add(381, "Eye of Ender", 64, 0, 0x61934c);
    add(382, "Glistering Melon", 64, 0, 0xe7bc48);
  }

  private static void add(int id, String n, int max, int food, int color) {
    ITEMS.put(id, new Item(id, n, max, 0, food, 0, "", color));
  }

  private static void toolSet(
      int sword,
      int shovel,
      int pick,
      int axe,
      int hoe,
      String name,
      int tier,
      int dur,
      int color) {
    int[] ids = {sword, shovel, pick, axe, hoe};
    String[] types = {"sword", "shovel", "pickaxe", "axe", "hoe"};
    for (int i = 0; i < 5; i++)
      ITEMS.put(
          ids[i],
          new Item(
              ids[i],
              name + " " + types[i].substring(0, 1).toUpperCase() + types[i].substring(1),
              1,
              dur,
              0,
              tier,
              types[i],
              color));
  }

  public static Item get(int id) {
    return ITEMS.getOrDefault(id, new Item(id, "Unknown " + id, 64, 0, 0, 0, "", 0xff00ff));
  }

  public static Collection<Item> all() {
    return Collections.unmodifiableCollection(ITEMS.values());
  }

  public static int fuel(int id) {
    return switch (id) {
      case 263 -> 1600;
      case 327 -> 20000;
      case 280 -> 100;
      case 17, 5, 58, 54, 85, 107, 53 -> 300;
      default -> get(id).tool().isEmpty() ? 0 : 200;
    };
  }

  public static int smelt(int id) {
    return switch (id) {
      case 15 -> 265;
      case 14 -> 266;
      case 12 -> 20;
      case 4 -> 1;
      case 337 -> 336;
      case 319 -> 320;
      case 363 -> 364;
      case 365 -> 366;
      case 349 -> 350;
      case 17 -> 263;
      case 81 -> 351;
      default -> 0;
    };
  }

  public static int placedBlock(int id) {
    return switch (id) {
      case 323 -> 63;
      case 324 -> 64;
      case 330 -> 71;
      case 331 -> 55;
      case 338 -> 83;
      case 354 -> 92;
      case 355 -> 26;
      case 356 -> 93;
      case 361 -> 104;
      case 362 -> 105;
      case 372 -> 115;
      case 379 -> 117;
      case 380 -> 118;
      default -> id < 256 ? id : 0;
    };
  }
}
