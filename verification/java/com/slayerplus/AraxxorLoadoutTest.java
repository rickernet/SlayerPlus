package com.slayerplus;

import java.util.*;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AraxxorLoadoutTest {
    @Test public void regularAraxytesTargetShortcutStartWithoutChangingAraxxor() {
        for (int agility : new int[]{62, 63, 99}) {
            List<WorldPoint> path = SlayerTaskWaypoints.findPath("Araxytes", "Morytania Spider Cave", agility, 126);
            assertEquals(new WorldPoint(3678, 9820, 0), path.get(path.size() - 1));
        }
        assertEquals(new WorldPoint(3630, 9813, 0), SlayerTaskWaypoints.findSpecific("Araxxor", "Morytania Spider Cave"));
    }
    @Test public void teleportPromptWaitsForRebankAfterArrival() {
        SlayerPlusPlugin plugin = new SlayerPlusPlugin();
        assertTrue(plugin.updateSpiderTeleportTrip(true, true));
        assertFalse(plugin.updateSpiderTeleportTrip(true, false));
        assertFalse(plugin.updateSpiderTeleportTrip(false, false));
        assertFalse(plugin.updateSpiderTeleportTrip(true, true));
        plugin.handleBankClosed();
        assertTrue(plugin.updateSpiderTeleportTrip(true, true));
    }
    @Test public void customTeleportStopsAtCaveAndLeavesWalkingToShortestPath() {
        WorldPoint entrance = new WorldPoint(3657, 3407, 0);
        assertTrue(SlayerPlusPlugin.needsSpiderTeleport(new WorldPoint(2935, 3280, 0), entrance, entrance));
        assertFalse(SlayerPlusPlugin.needsSpiderTeleport(new WorldPoint(3658, 3403, 0), entrance, entrance));
        WorldPoint inside = new WorldPoint(3630, 9813, 0);
        assertFalse(SlayerPlusPlugin.needsSpiderTeleport(inside, inside, entrance));
        assertFalse(SlayerPlusPlugin.needsSpiderTeleport(null, entrance, entrance));
    }
    private final SlayerLoadoutAnalyzer analyzer = new SlayerLoadoutAnalyzer(null);

    @Test public void closingBankPreventsRestockRouteFromOverridingTaskTeleport() {
        SlayerPlusPlugin plugin = new SlayerPlusPlugin();
        assertTrue(plugin.shouldRouteToBank(true));
        assertFalse(plugin.shouldRouteToBank(false));
        plugin.handleBankClosed();
        assertFalse(plugin.shouldRouteToBank(true));
        plugin.handleBankClosed();
        assertFalse(plugin.shouldRouteToBank(true));
    }

    private TaskStrategy strategy(String task) {
        return SlayerTaskStrategyCatalog.resolve(task, Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER, Preference.Burst.NEVER, Preference.CombatStyle.AUTOMATIC,
            "Morytania Spider Cave", false);
    }

    @Test public void ownedScrollBeatsMaxCapeForBothTaskTypes() {
        for (String task : Arrays.asList("Araxxor", "Araxytes")) {
            List<KitItem> items = inventory(task, pool());
            assertEquals(ItemID.TELEPORTSCROLL_SPIDERCAVE, items.get(0).getItemId());
            assertEquals(MethodRules.InventoryGroup.TRAVEL, items.get(0).getInventoryGroup());
            assertEquals(0, count(items, ItemID.SKILLCAPE_MAX));
            assertEquals(0, count(items, ItemID.HALLOWED_TELEPORT));
        }
    }

    @Test public void missingScrollRetainsNormalFallback() {
        List<SlayerLoadoutAnalyzer.OwnedItem> pool = new ArrayList<>();
        pool.add(owned(ItemID.SKILLCAPE_MAX, "Max cape", EquipmentInventorySlot.CAPE));
        assertEquals(ItemID.SKILLCAPE_MAX, inventory("Araxxor", pool).get(0).getItemId());
    }

    @Test public void meleeAraxyteSwitchDoesNotNeedRangingPotion() {
        List<KitItem> items = inventory("Araxxor", pool());
        assertEquals(1, count(items, ItemID.NOXIOUS_HALBERD));
        assertEquals(1, count(items, ItemID.DRAGON_CLAWS));
        assertEquals(0, count(items, ItemID._4DOSERANGERSPOTION));
    }

    @Test public void rangedAraxyteSwitchKeepsRangingPotion() {
        List<SlayerLoadoutAnalyzer.OwnedItem> pool = new ArrayList<>(Arrays.asList(
            owned(ItemID.XBOWS_CROSSBOW_RUNITE, "Rune crossbow", EquipmentInventorySlot.WEAPON),
            owned(ItemID._4DOSERANGERSPOTION, "Ranging potion(4)", null)));
        assertEquals(1, count(inventory("Araxxor", pool), ItemID._4DOSERANGERSPOTION));
    }

    @Test public void braceletsStayOptionalAndFoodNeverBecomesEquipment() {
        List<SlayerLoadoutAnalyzer.OwnedItem> pool = pool();
        List<KitItem> inventory = inventory("Araxxor", pool);
        assertEquals(0, count(inventory, ItemID.BRACELET_OF_SLAUGHTER));
        assertEquals(1, count(analyzer.buildOptionalLayout("Araxxor", strategy("Araxxor"), pool, null), ItemID.BRACELET_OF_SLAUGHTER));
        List<KitItem> gear = SlayerLoadoutAnalyzer.buildEquipmentLayout("Araxxor", strategy("Araxxor"),
            SlayerLoadoutAnalyzer.CombatStyle.MELEE, Collections.emptyList(), Collections.emptyList(), pool, true, null);
        assertEquals(0, count(gear, ItemID.SHARK));
        assertEquals(0, count(gear, ItemID.TBWT_COOKED_KARAMBWAN));
        List<BankTagLayout.InventoryPlacement> slots = BankTagLayout.planInventoryForBankTag(inventory, -1, true);
        Set<Integer> occupied = new HashSet<>();
        for (BankTagLayout.InventoryPlacement slot : slots) {
            assertTrue(slot.getSlotIndex() >= 0 && slot.getSlotIndex() < 28);
            assertTrue(occupied.add(slot.getSlotIndex()));
        }
        assertTrue(slots.size() <= 28);
    }

    @Test public void hallowedCrystalIsNotAUsableTravelRecommendation() {
        assertFalse(SlayerTravelItemPolicy.isUsableDisplayName("Hallowed crystal shard"));
        assertTrue(SlayerTravelItemPolicy.isUsableDisplayName("Spider cave teleport"));
    }

    private List<KitItem> inventory(String task, List<SlayerLoadoutAnalyzer.OwnedItem> pool) {
        List<KitItem> result = analyzer.buildInventoryLayout(task, strategy(task), "Morytania Spider Cave", "",
            SlayerLoadoutAnalyzer.CombatStyle.MELEE, Collections.emptyList(), false, 8, pool, true, Collections.emptyList());
        assertTrue(result.size() <= 28);
        return result;
    }

    private List<SlayerLoadoutAnalyzer.OwnedItem> pool() {
        return Arrays.asList(owned(ItemID.TELEPORTSCROLL_SPIDERCAVE, "Spider cave teleport", null),
            owned(ItemID.SKILLCAPE_MAX, "Max cape", EquipmentInventorySlot.CAPE),
            owned(ItemID.HALLOWED_TELEPORT, "Hallowed crystal shard", null),
            owned(ItemID.NOXIOUS_HALBERD, "Noxious halberd", EquipmentInventorySlot.WEAPON),
            owned(ItemID.DRAGON_CLAWS, "Dragon claws", EquipmentInventorySlot.WEAPON),
            owned(ItemID._4DOSERANGERSPOTION, "Ranging potion(4)", null),
            owned(ItemID.BRACELET_OF_SLAUGHTER, "Bracelet of slaughter", EquipmentInventorySlot.GLOVES),
            owned(ItemID.SHARK, "Shark", null), owned(ItemID.TBWT_COOKED_KARAMBWAN, "Cooked karambwan", null),
            owned(ItemID._4DOSE2RESTORE, "Super restore(4)", null));
    }

    private static SlayerLoadoutAnalyzer.OwnedItem owned(int id, String name, EquipmentInventorySlot slot) {
        return new SlayerLoadoutAnalyzer.OwnedItem(id, name, 100, KitItem.Status.BANK,
            slot == null ? -1 : slot.getSlotIdx(), null, Collections.emptySet());
    }

    private static long count(List<KitItem> items, int id) {
        return items.stream().filter(item -> item.getItemId() == id).count();
    }
}
