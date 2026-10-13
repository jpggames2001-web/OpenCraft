package dev.opencraft.world;

import java.util.Arrays;

/** Classic numeric block IDs, separate face materials, collision and mining data. */
public enum Block {
  AIR(0, "Air", false, 0, 0, 0x000000),
  STONE(1, "Stone", true, 1.5f, 0, 0x888888),
  GRASS(2, "Grass", true, .6f, 0, 0x63933a),
  DIRT(3, "Dirt", true, .5f, 0, 0x866043),
  COBBLESTONE(4, "Cobblestone", true, 2, 0, 0x777777),
  PLANKS(5, "Wooden Planks", true, 2, 0, 0xaa844f),
  SAPLING(6, "Sapling", false, 0, 0, 0x438b30),
  BEDROCK(7, "Bedrock", true, -1, 0, 0x444444),
  WATER_FLOW(8, "Flowing Water", false, 100, 0, 0x385bcc),
  WATER(9, "Water", false, 100, 0, 0x385bcc),
  LAVA_FLOW(10, "Flowing Lava", false, 100, 15, 0xf15c13),
  LAVA(11, "Lava", false, 100, 15, 0xf15c13),
  SAND(12, "Sand", true, .5f, 0, 0xd8ce98),
  GRAVEL(13, "Gravel", true, .6f, 0, 0x968e88),
  GOLD_ORE(14, "Gold Ore", true, 3, 0, 0xb4a26b),
  IRON_ORE(15, "Iron Ore", true, 3, 0, 0xa49484),
  COAL_ORE(16, "Coal Ore", true, 3, 0, 0x5d5d5d),
  LOG(17, "Wood", true, 2, 0, 0x6b5031),
  LEAVES(18, "Leaves", true, .2f, 0, 0x438b30),
  SPONGE(19, "Sponge", true, .6f, 0, 0xd0cc55),
  GLASS(20, "Glass", true, .3f, 0, 0xb5dcd8),
  LAPIS_ORE(21, "Lapis Lazuli Ore", true, 3, 0, 0x54669f),
  LAPIS_BLOCK(22, "Lapis Lazuli Block", true, 3, 0, 0x234bbb),
  DISPENSER(23, "Dispenser", true, 3.5f, 0, 0x777777),
  SANDSTONE(24, "Sandstone", true, .8f, 0, 0xd6c88c),
  NOTE_BLOCK(25, "Note Block", true, .8f, 0, 0x826044),
  BED(26, "Bed", true, .2f, 0, 0xb33030),
  POWERED_RAIL(27, "Powered Rail", false, .7f, 0, 0xb78a39),
  DETECTOR_RAIL(28, "Detector Rail", false, .7f, 0, 0x777777),
  STICKY_PISTON(29, "Sticky Piston", true, .5f, 0, 0x7b9d4c),
  COBWEB(30, "Cobweb", false, 4, 0, 0xdddddd),
  TALL_GRASS(31, "Tall Grass", false, 0, 0, 0x54883b),
  DEAD_BUSH(32, "Dead Bush", false, 0, 0, 0x8d622f),
  PISTON(33, "Piston", true, .5f, 0, 0xa38c60),
  WOOL(35, "Wool", true, .8f, 0, 0xe7e7e7),
  DANDELION(37, "Dandelion", false, 0, 0, 0xf8db32),
  ROSE(38, "Rose", false, 0, 0, 0xc62d30),
  BROWN_MUSHROOM(39, "Brown Mushroom", false, 0, 1, 0x96745a),
  RED_MUSHROOM(40, "Red Mushroom", false, 0, 0, 0xc23232),
  GOLD_BLOCK(41, "Gold Block", true, 3, 0, 0xe4c63c),
  IRON_BLOCK(42, "Iron Block", true, 5, 0, 0xcacaca),
  DOUBLE_SLAB(43, "Double Slab", true, 2, 0, 0x999999),
  SLAB(44, "Slab", true, 2, 0, 0x999999),
  BRICKS(45, "Bricks", true, 2, 0, 0xa15d4b),
  TNT(46, "TNT", true, 0, 0, 0xca4630),
  BOOKSHELF(47, "Bookshelf", true, 1.5f, 0, 0xa67c44),
  MOSSY_COBBLESTONE(48, "Moss Stone", true, 2, 0, 0x647451),
  OBSIDIAN(49, "Obsidian", true, 50, 0, 0x30203e),
  TORCH(50, "Torch", false, 0, 14, 0xe3b954),
  FIRE(51, "Fire", false, 0, 15, 0xff8d13),
  SPAWNER(52, "Monster Spawner", true, 5, 0, 0x354346),
  WOOD_STAIRS(53, "Wooden Stairs", true, 2, 0, 0xaa844f),
  CHEST(54, "Chest", true, 2.5f, 0, 0xa7762d),
  REDSTONE_WIRE(55, "Redstone", false, 0, 0, 0xac1515),
  DIAMOND_ORE(56, "Diamond Ore", true, 3, 0, 0x6cafaf),
  DIAMOND_BLOCK(57, "Diamond Block", true, 5, 0, 0x53c5c1),
  CRAFTING_TABLE(58, "Crafting Table", true, 2.5f, 0, 0x986c3a),
  WHEAT(59, "Wheat", false, 0, 0, 0x9ca64d),
  FARMLAND(60, "Farmland", true, .6f, 0, 0x64452c),
  FURNACE(61, "Furnace", true, 3.5f, 0, 0x777777),
  FURNACE_LIT(62, "Burning Furnace", true, 3.5f, 13, 0x777777),
  SIGN(63, "Sign", false, 1, 0, 0xaa844f),
  WOOD_DOOR(64, "Wooden Door", true, 3, 0, 0x99723b),
  LADDER(65, "Ladder", false, .4f, 0, 0xad8750),
  RAIL(66, "Rail", false, .7f, 0, 0x827254),
  COBBLE_STAIRS(67, "Stone Stairs", true, 2, 0, 0x777777),
  WALL_SIGN(68, "Wall Sign", false, 1, 0, 0xaa844f),
  LEVER(69, "Lever", false, .5f, 0, 0x888888),
  STONE_PLATE(70, "Stone Pressure Plate", true, .5f, 0, 0x888888),
  IRON_DOOR(71, "Iron Door", true, 5, 0, 0xcacaca),
  WOOD_PLATE(72, "Wooden Pressure Plate", true, .5f, 0, 0xaa844f),
  REDSTONE_ORE(73, "Redstone Ore", true, 3, 0, 0x8b5b5b),
  REDSTONE_ORE_LIT(74, "Glowing Redstone Ore", true, 3, 9, 0x9f4b4b),
  REDSTONE_TORCH_OFF(75, "Redstone Torch", false, 0, 0, 0x99543a),
  REDSTONE_TORCH(76, "Redstone Torch", false, 0, 7, 0xe54a35),
  STONE_BUTTON(77, "Stone Button", false, .5f, 0, 0x888888),
  SNOW(78, "Snow", false, .1f, 0, 0xf3f7f9),
  ICE(79, "Ice", true, .5f, 0, 0x91c5ec),
  SNOW_BLOCK(80, "Snow Block", true, .2f, 0, 0xf3f7f9),
  CACTUS(81, "Cactus", true, .4f, 0, 0x478b35),
  CLAY(82, "Clay", true, .6f, 0, 0xa7afbc),
  SUGAR_CANE(83, "Sugar Cane", false, 0, 0, 0x86b64e),
  JUKEBOX(84, "Jukebox", true, 2, 0, 0x82563f),
  FENCE(85, "Fence", true, 2, 0, 0xaa844f),
  PUMPKIN(86, "Pumpkin", true, 1, 0, 0xd08726),
  NETHERRACK(87, "Netherrack", true, .4f, 0, 0x8b4444),
  SOUL_SAND(88, "Soul Sand", true, .5f, 0, 0x685146),
  GLOWSTONE(89, "Glowstone", true, .3f, 15, 0xbdaa68),
  PORTAL(90, "Portal", false, -1, 11, 0x6933bb),
  JACK_O_LANTERN(91, "Jack o Lantern", true, 1, 15, 0xd08726),
  CAKE(92, "Cake", true, .5f, 0, 0xe3c7b0),
  REPEATER_OFF(93, "Redstone Repeater", false, 0, 0, 0xa05050),
  REPEATER_ON(94, "Powered Repeater", false, 0, 9, 0xb55050),
  TRAPDOOR(96, "Trapdoor", true, 3, 0, 0xaa844f),
  MONSTER_EGG(97, "Monster Egg", true, .75f, 0, 0x888888),
  STONE_BRICKS(98, "Stone Bricks", true, 1.5f, 0, 0x888888),
  HUGE_BROWN_MUSHROOM(99, "Huge Mushroom", true, .2f, 0, 0x96745a),
  HUGE_RED_MUSHROOM(100, "Huge Red Mushroom", true, .2f, 0, 0xc23232),
  IRON_BARS(101, "Iron Bars", true, 5, 0, 0x999999),
  GLASS_PANE(102, "Glass Pane", true, .3f, 0, 0xb5dcd8),
  MELON(103, "Melon", true, 1, 0, 0x81a23b),
  PUMPKIN_STEM(104, "Pumpkin Stem", false, 0, 0, 0x86a43a),
  MELON_STEM(105, "Melon Stem", false, 0, 0, 0x86a43a),
  VINES(106, "Vines", false, .2f, 0, 0x438b30),
  FENCE_GATE(107, "Fence Gate", true, 2, 0, 0xaa844f),
  BRICK_STAIRS(108, "Brick Stairs", true, 2, 0, 0xa15d4b),
  STONE_BRICK_STAIRS(109, "Stone Brick Stairs", true, 1.5f, 0, 0x888888),
  MYCELIUM(110, "Mycelium", true, .6f, 0, 0x7c6576),
  LILY_PAD(111, "Lily Pad", false, 0, 0, 0x357335),
  NETHER_BRICK(112, "Nether Brick", true, 2, 0, 0x40212a),
  NETHER_FENCE(113, "Nether Brick Fence", true, 2, 0, 0x40212a),
  NETHER_STAIRS(114, "Nether Brick Stairs", true, 2, 0, 0x40212a),
  NETHER_WART(115, "Nether Wart", false, 0, 0, 0xa32535),
  ENCHANTING_TABLE(116, "Enchantment Table", true, 5, 0, 0x643a68),
  BREWING_STAND(117, "Brewing Stand", false, .5f, 1, 0xaca163),
  CAULDRON(118, "Cauldron", true, 2, 0, 0x555555),
  END_PORTAL(119, "End Portal", false, -1, 15, 0x112322),
  END_PORTAL_FRAME(120, "End Portal Frame", true, -1, 1, 0x799679),
  END_STONE(121, "End Stone", true, 3, 0, 0xdce0a5),
  DRAGON_EGG(122, "Dragon Egg", true, 3, 0, 0x261b34);
  public final int id, emission, color;
  public final String label;
  public final boolean solid;
  public final float hardness;
  private static final Block[] IDS = new Block[256];

  static {
    Arrays.fill(IDS, AIR);
    for (Block b : values()) IDS[b.id] = b;
  }

  Block(int id, String label, boolean solid, float hardness, int emission, int color) {
    this.id = id;
    this.label = label;
    this.solid = solid;
    this.hardness = hardness;
    this.emission = emission;
    this.color = color;
  }

  public static Block of(int id) {
    return IDS[id & 255];
  }

  public boolean fluid() {
    return id >= 8 && id <= 11;
  }

  public boolean lava() {
    return this == LAVA || this == LAVA_FLOW;
  }

  public boolean transparent() {
    return !solid || this == LEAVES || this == GLASS || this == GLASS_PANE || this == ICE;
  }

  public int opacity() {
    return this == LEAVES || fluid() || this == ICE ? 3 : transparent() ? 0 : 15;
  }

  public boolean plant() {
    return !solid
        && !fluid()
        && this != AIR
        && this != PORTAL
        && this != FIRE
        && this != END_PORTAL;
  }

  public boolean fullCube() {
    return solid
        && this != SLAB
        && this != BED
        && this != FENCE
        && this != FENCE_GATE
        && this != CAKE
        && this != STONE_PLATE
        && this != WOOD_PLATE
        && this != WOOD_DOOR
        && this != IRON_DOOR
        && this != TRAPDOOR;
  }

  public int texture(int face) {
    if (this == GRASS) return face == 2 ? 200 : face == 3 ? DIRT.id : 201;
    if (this == LOG) return face == 2 || face == 3 ? 202 : id;
    if (this == CRAFTING_TABLE) return face == 2 ? 203 : face == 3 ? PLANKS.id : id;
    return id;
  }

  public double height() {
    return switch (this) {
      case SLAB -> .5;
      case BED -> .5625;
      case CAKE -> .5;
      case STONE_PLATE, WOOD_PLATE -> .0625;
      case FENCE, FENCE_GATE -> 1.5;
      default -> 1;
    };
  }
}
