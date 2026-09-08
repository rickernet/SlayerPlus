package com.slayerplus;

import static org.junit.Assert.*;

import java.util.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.banktags.tabs.Layout;
import org.junit.Test;

public class SlayerOptionalPinsTest {
  @Test
  public void menuNeedsSlayerTagOrShiftAndAlwaysNeedsBankItem() {
    int bank = InterfaceID.Bankmain.ITEMS;
    assertTrue(SlayerOptionalPins.allowsMenu("slayerplus current", bank, ItemID.SHARK, false));
    assertFalse(SlayerOptionalPins.allowsMenu("Other tag", bank, ItemID.SHARK, false));
    assertFalse(SlayerOptionalPins.allowsMenu(null, bank, ItemID.SHARK, false));
    assertTrue(SlayerOptionalPins.allowsMenu("Other tag", bank, ItemID.SHARK, true));
    assertTrue(SlayerOptionalPins.allowsMenu(null, bank, ItemID.SHARK, true));
    assertFalse(SlayerOptionalPins.allowsMenu(BankTagLayout.TAG_NAME, -1, ItemID.SHARK, true));
    assertFalse(SlayerOptionalPins.allowsMenu(BankTagLayout.TAG_NAME, bank, -1, true));
    assertFalse(
        SlayerOptionalPins.allowsMenu(
            BankTagLayout.TAG_NAME, InterfaceID.Bankmain.ITEMS_CONTAINER, ItemID.SHARK, false));
    assertFalse(
        SlayerOptionalPins.allowsMenu(
            null, InterfaceID.Bankmain.ITEMS_CONTAINER, ItemID.SHARK, true));
    assertFalse(
        SlayerOptionalPins.allowsMenu(
            BankTagLayout.TAG_NAME, InterfaceID.Bankmain.TABS, ItemID.SHARK, true));
  }

  @Test
  public void triggerAddsOneEntryAndDoesNotReenterForItsOwnOption() {
    assertTrue(SlayerOptionalPins.isMenuTrigger("Examine", true));
    assertTrue(SlayerOptionalPins.isMenuTrigger("Examine", false));
    assertFalse(SlayerOptionalPins.isMenuTrigger("Withdraw-All-but-1", true));
    assertTrue(SlayerOptionalPins.isMenuTrigger("Withdraw-All-but-1", false));
    for (String option :
        Arrays.asList(
            "Withdraw-1",
            "Withdraw-All",
            "Cancel",
            "Pin to SlayerPlus optional row",
            "Unpin from SlayerPlus optional row",
            null)) {
      assertFalse(SlayerOptionalPins.isMenuTrigger(option, false));
    }
  }

  @Test
  public void pinsRoundTripInOrderAndRejectInvalidIds() {
    Set<Integer> pins =
        SlayerOptionalPins.read(
            ItemID.SHARK + ",bad,-1,0," + ItemID.SHARK + "," + ItemID.BH_RUNE_POUCH);
    assertEquals(Arrays.asList(ItemID.SHARK, ItemID.BH_RUNE_POUCH), new ArrayList<>(pins));
    assertEquals(pins, SlayerOptionalPins.read(SlayerPlusPlugin.serializeItemIds(pins)));
    assertTrue(SlayerOptionalPins.read(null).isEmpty());
    assertEquals(8, SlayerOptionalPins.read("1,2,3,4,5,6,7,8,9").size());
  }

  @Test
  public void pinsLeadOptionalRowWithoutChangingRequiredLoadout() {
    KitItem shark = item(ItemID.SHARK);
    KitItem pouch = item(ItemID.BH_RUNE_POUCH);
    KitPlan original =
        new KitPlan(
            "owned",
            "task",
            Collections.singletonList(shark),
            Collections.singletonList(pouch),
            Arrays.asList(pouch, shark));
    KitPlan result = SlayerOptionalPins.prepend(original, Collections.singletonList(shark));
    assertEquals(Arrays.asList(shark, pouch), result.getOptionalItems());
    assertEquals(original.getEquipmentItems(), result.getEquipmentItems());
    assertEquals(original.getInventoryItems(), result.getInventoryItems());
    assertEquals(Arrays.asList(pouch, shark), original.getOptionalItems());
    assertSame(original, SlayerOptionalPins.prepend(original, Collections.emptyList()));
    assertNull(SlayerOptionalPins.prepend(null, Collections.singletonList(shark)));
  }

  @Test
  public void pinnedEquipmentKeepsBothOptionalAndEquipmentPositions() {
    Layout layout = new Layout(BankTagLayout.TAG_NAME);
    Set<Integer> tags = new LinkedHashSet<>();
    Set<Integer> pins = Collections.singleton(ItemID.SHARK);
    assertTrue(BankTagLayout.placeEquipmentItemId(layout, tags, ItemID.SHARK, 0, pins));
    assertTrue(BankTagLayout.placeEquipmentItemId(layout, tags, ItemID.SHARK, 17, pins));
    assertEquals(ItemID.SHARK, layout.getLayout()[0]);
    assertEquals(ItemID.SHARK, layout.getLayout()[17]);
    assertEquals(1, tags.size());
    assertFalse(
        BankTagLayout.placeEquipmentItemId(layout, tags, ItemID.SHARK, 18, Collections.emptySet()));
    assertFalse(BankTagLayout.placeEquipmentItemId(layout, tags, -1, 18, pins));
  }

  private static KitItem item(int id) {
    return new KitItem("Item", id, 1, KitItem.Status.BANK);
  }
}
