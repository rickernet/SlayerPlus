package com.slayerplus;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class BarrowsInventoryTest {
  private final SlayerLoadoutAnalyzer analyzer = new SlayerLoadoutAnalyzer(null);
  private final TaskStrategy strategy =
      SlayerTaskStrategyCatalog.resolve(
          "Barrows Brothers",
          Preference.Cannon.NEVER,
          Preference.Burst.NEVER,
          Preference.CombatStyle.AUTOMATIC,
          "Barrows",
          false);

  @Test
  public void barrowsHasShortestPathDestination() {
    assertEquals(
        Collections.singletonList(new WorldPoint(3565, 3314, 0)),
        SlayerTaskWaypoints.findPath("Barrows Brothers", "Barrows"));
  }

  @Test
  public void shadowGetsOneCapeMagicBoostAndLootPouchWithoutRangedSwitch() {
    List<SlayerLoadoutAnalyzer.OwnedItem> pool = base();
    pool.add(owned(ItemID.RING_OF_DUELING_8, "Ring of dueling(8)", EquipmentInventorySlot.RING));
    List<KitItem> items = inventory(ItemID.TUMEKENS_SHADOW, pool);
    assertEquals(1, count(items, ItemID.SKILLCAPE_MAX));
    assertEquals(0, count(items, ItemID.RING_OF_DUELING_8));
    assertEquals(1, count(items, ItemID.SATURATED_HEART));
    assertEquals(1, count(items, ItemID.BH_RUNE_POUCH));
    assertEquals(3, count(items, ItemID._4DOSE2RESTORE));
    assertEquals(0, count(items, ItemID._4DOSESTATRESTORE));
    assertEquals(0, count(items, ItemID._4DOSERANGERSPOTION));
    assertFalse(
        items.stream()
            .anyMatch(
                item ->
                    item.isEquipmentSwitch()
                        && item.getSwitchStyle() == KitItem.SwitchStyle.RANGED));
    assertFalse(
        PreparationCatalog.resolve(
                "Barrows Brothers",
                "Barrows",
                strategy,
                null,
                null,
                null,
                Collections.emptyMap(),
                ItemID.TUMEKENS_SHADOW)
            .isActive());
  }

  @Test
  public void airSpellsSkipRangedAndPoweredStaffBoostButRetainCastingRequirements() {
    List<KitItem> items = inventory(ItemID.SMOKE_BATTLESTAFF, base());
    assertEquals(0, count(items, ItemID._4DOSERANGERSPOTION));
    assertEquals(0, count(items, ItemID.SATURATED_HEART));
    assertFalse(
        items.stream()
            .anyMatch(
                item ->
                    item.isEquipmentSwitch()
                        && item.getSwitchStyle() == KitItem.SwitchStyle.RANGED));
    assertTrue(
        PreparationCatalog.resolve(
                "Barrows Brothers",
                "Barrows",
                strategy,
                null,
                null,
                null,
                Collections.emptyMap(),
                ItemID.SMOKE_BATTLESTAFF)
            .isActive());
  }

  @Test
  public void otherPoweredStavesGetCompatibleRangedSwitchAmmunition() {
    Object[][] cases = {
      {ItemID.MAGIC_SHORTBOW, "Magic shortbow", ItemID.RUNE_ARROW, "Rune arrow"},
      {ItemID.BARROWS_KARIL_WEAPON, "Karil's crossbow", ItemID.BARROWS_KARIL_AMMO, "Bolt rack"},
      {
        ItemID.DTTD_BONE_CROSSBOW,
        "Dorgeshuun crossbow",
        ItemID.DTTD_BONE_CROSSBOW_BOLT,
        "Bone bolts"
      },
      {
        ItemID.HUNTING_CROSSBOW_SUNLIGHT,
        "Hunters' sunlight crossbow",
        ItemID.SUNLIGHT_ANTELOPE_BOLT,
        "Sunlight antler bolts"
      },
      {
        ItemID.XBOWS_CROSSBOW_RUNITE,
        "Rune crossbow",
        ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE,
        "Runite bolts"
      }
    };
    for (Object[] entry : cases) {
      List<SlayerLoadoutAnalyzer.OwnedItem> pool = base();
      pool.add(owned((int) entry[0], (String) entry[1], EquipmentInventorySlot.WEAPON));
      pool.add(owned((int) entry[2], (String) entry[3], EquipmentInventorySlot.AMMO));
      List<KitItem> items = inventory(ItemID.TOTS_CHARGED, pool);
      assertEquals(entry[1].toString(), 1, count(items, (int) entry[0]));
      assertEquals(entry[1].toString(), 1, count(items, (int) entry[2]));
      assertEquals(1, count(items, ItemID._4DOSERANGERSPOTION));
    }
  }

  @Test
  public void blowpipeNeedsNoLooseAmmoAndRangedArmourOnlyAppearsForRangedSwitch() {
    List<SlayerLoadoutAnalyzer.OwnedItem> pool = base();
    pool.add(owned(ItemID.TOXIC_BLOWPIPE_LOADED, "Toxic blowpipe", EquipmentInventorySlot.WEAPON));
    pool.add(owned(ItemID.RUNE_ARROW, "Rune arrow", EquipmentInventorySlot.AMMO));
    pool.add(owned(ItemID.BLACK_DRAGONHIDE_BODY, "Black d'hide body", EquipmentInventorySlot.BODY));
    List<KitItem> items = inventory(ItemID.TOTS_CHARGED, pool);
    assertEquals(1, count(items, ItemID.TOXIC_BLOWPIPE_LOADED));
    assertEquals(1, count(items, ItemID.BLACK_DRAGONHIDE_BODY));
    assertEquals(0, count(items, ItemID.RUNE_ARROW));
    assertEquals(0, count(inventory(ItemID.TUMEKENS_SHADOW, pool), ItemID.BLACK_DRAGONHIDE_BODY));
  }

  @Test
  public void optionalLockpickIsOnlyAddedWhenOwnedAndSpadeStaysRequired() {
    List<SlayerLoadoutAnalyzer.OwnedItem> pool = base();
    List<KitItem> items = inventory(ItemID.TUMEKENS_SHADOW, pool);
    assertFalse(items.stream().anyMatch(item -> item.getDisplayName().equals("Tunnel shortcut")));
    assertEquals(1, count(items, ItemID.SPADE));
    pool.add(owned(ItemID.STRANGE_OLD_LOCKPICK, "Strange old lockpick", null));
    assertEquals(1, count(inventory(ItemID.TUMEKENS_SHADOW, pool), ItemID.STRANGE_OLD_LOCKPICK));
    assertTrue(
        inventory(ItemID.TUMEKENS_SHADOW, Collections.emptyList()).stream()
            .anyMatch(item -> item.getDisplayName().equals("Spade") && !item.hasItemId()));
  }

  @Test
  public void prayerPotionFallbackRetainsStatRestoreAndMissingLootPouchIsNotRequired() {
    List<SlayerLoadoutAnalyzer.OwnedItem> pool =
        Arrays.asList(
            owned(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", null),
            owned(ItemID._4DOSESTATRESTORE, "Restore potion(4)", null),
            owned(ItemID.SHARK, "Shark", null));
    List<KitItem> items = inventory(ItemID.TUMEKENS_SHADOW, pool);
    assertEquals(3, count(items, ItemID._4DOSEPRAYERRESTORE));
    assertEquals(1, count(items, ItemID._4DOSESTATRESTORE));
    assertFalse(items.stream().anyMatch(item -> item.getDisplayName().equals("Rune pouch")));
  }

  private List<KitItem> inventory(int weapon, List<SlayerLoadoutAnalyzer.OwnedItem> pool) {
    List<KitItem> gear =
        Arrays.asList(
            null, null, null, null, new KitItem("Weapon", weapon, 1, KitItem.Status.BANK));
    List<KitItem> items =
        analyzer.buildInventoryLayout(
            "Barrows Brothers",
            strategy,
            "Barrows",
            "",
            SlayerLoadoutAnalyzer.CombatStyle.MAGIC,
            Collections.emptyList(),
            false,
            8,
            pool,
            true,
            gear);
    assertTrue(items.size() <= 28);
    return items;
  }

  private static List<SlayerLoadoutAnalyzer.OwnedItem> base() {
    return new ArrayList<>(
        Arrays.asList(
            owned(ItemID.SKILLCAPE_MAX, "Max cape", EquipmentInventorySlot.CAPE),
            owned(ItemID.SATURATED_HEART, "Saturated heart", null),
            owned(ItemID.BH_RUNE_POUCH, "Rune pouch", null),
            owned(ItemID.SPADE, "Spade", null),
            owned(ItemID.SHARK, "Shark", null),
            owned(ItemID._4DOSE2RESTORE, "Super restore(4)", null),
            owned(ItemID._4DOSESTATRESTORE, "Restore potion(4)", null),
            owned(ItemID._4DOSERANGERSPOTION, "Ranging potion(4)", null)));
  }

  private static SlayerLoadoutAnalyzer.OwnedItem owned(
      int id, String name, EquipmentInventorySlot slot) {
    return new SlayerLoadoutAnalyzer.OwnedItem(
        id,
        name,
        100,
        KitItem.Status.BANK,
        slot == null ? -1 : slot.getSlotIdx(),
        null,
        Collections.emptySet());
  }

  private static long count(List<KitItem> items, int id) {
    return items.stream().filter(item -> item.getItemId() == id).count();
  }
}
