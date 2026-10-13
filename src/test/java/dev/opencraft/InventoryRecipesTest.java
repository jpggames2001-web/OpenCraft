package dev.opencraft;

import static org.junit.jupiter.api.Assertions.*;

import dev.opencraft.game.*;
import dev.opencraft.game.Stack;
import org.junit.jupiter.api.Test;

class InventoryRecipesTest {
  @Test
  void fullInventoryReturnsRemainderWithoutLoss() {
    Inventory inv = new Inventory(2);
    inv.set(0, new Stack(4, 60));
    inv.set(1, new Stack(5, 64));
    Stack left = inv.add(new Stack(4, 12));
    assertEquals(64, inv.get(0).count());
    assertEquals(8, left.count());
    assertEquals(72, inv.count(4) + left.count());
  }

  @Test
  void mergesBeforeUsingEmptySlot() {
    Inventory inv = new Inventory(3);
    inv.set(2, new Stack(4, 60));
    assertTrue(inv.add(new Stack(4, 6)).empty());
    assertEquals(64, inv.get(2).count());
    assertEquals(2, inv.get(0).count());
  }

  @Test
  void incompatibleMetadataDoesNotMerge() {
    Inventory inv = new Inventory(1);
    inv.set(0, new Stack(35, 10, 1));
    Stack left = inv.add(new Stack(35, 5, 2));
    assertEquals(5, left.count());
    assertEquals(10, inv.get(0).count());
  }

  @Test
  void rightClickSplitsOddStackThenPlacesOne() {
    Inventory inv = new Inventory(2);
    inv.set(0, new Stack(5, 7));
    Stack cursor = inv.click(0, Stack.EMPTY, true);
    assertEquals(4, cursor.count());
    assertEquals(3, inv.get(0).count());
    cursor = inv.click(1, cursor, true);
    assertEquals(1, inv.get(1).count());
    assertEquals(3, cursor.count());
  }

  @Test
  void clickingFullStackDoesNotDestroyCursor() {
    Inventory inv = new Inventory(1);
    inv.set(0, new Stack(5, 64));
    Stack cursor = inv.click(0, new Stack(5, 20), false);
    assertEquals(20, cursor.count());
    assertEquals(64, inv.get(0).count());
  }

  @Test
  void partialGridSupportsShiftedRecipe() {
    Inventory grid = new Inventory(9);
    grid.set(8, new Stack(17, 2));
    var match = Recipes.match(grid, 3).orElseThrow();
    assertEquals(new Stack(5, 4), match.result());
    Recipes.consume(grid, match);
    assertEquals(1, grid.get(8).count());
  }

  @Test
  void extraIngredientPreventsCrafting() {
    Inventory grid = new Inventory(4);
    grid.set(0, new Stack(17, 1));
    grid.set(3, new Stack(1, 1));
    assertTrue(Recipes.match(grid, 2).isEmpty());
  }

  @Test
  void mirroredAxeWorks() {
    Inventory grid = new Inventory(9);
    for (int i : new int[] {0, 1, 4}) grid.set(i, new Stack(5, 1));
    grid.set(3, new Stack(280, 1));
    grid.set(6, new Stack(280, 1));
    assertEquals(271, Recipes.match(grid, 3).orElseThrow().result().id());
  }

  @Test
  void threeByThreeRecipesDoNotFitSmallGrid() {
    Inventory grid = new Inventory(4);
    for (int i = 0; i < 4; i++) grid.set(i, new Stack(4, 1));
    assertTrue(Recipes.match(grid, 2).isEmpty());
  }

  @Test
  void shapelessRecipesIgnorePosition() {
    Inventory grid = new Inventory(9);
    grid.set(2, new Stack(40, 1));
    grid.set(6, new Stack(281, 1));
    grid.set(8, new Stack(39, 1));
    assertEquals(282, Recipes.match(grid, 3).orElseThrow().result().id());
  }

  @Test
  void stackDurabilityBreaksTool() {
    assertTrue(new Stack(270, 1, 58).hurt().empty());
    assertEquals(1, new Stack(270, 1, 0).hurt().damage());
  }

  @Test
  void consumingMissingItemsDoesNotMutateInventory() {
    Inventory inv = new Inventory(1);
    inv.set(0, new Stack(5, 3));
    assertFalse(inv.consume(5, 4));
    assertEquals(3, inv.count(5));
  }
}
