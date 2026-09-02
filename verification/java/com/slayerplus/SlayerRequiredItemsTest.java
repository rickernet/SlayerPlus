package com.slayerplus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import com.slayerplus.SlayerLoadoutAnalyzer.CombatStyle;
import com.slayerplus.SlayerLoadoutAnalyzer.OwnedItem;
import com.slayerplus.SlayerLoadoutAnalyzer.Requirement;

import static org.junit.Assert.*;

public class SlayerRequiredItemsTest
{
	private final SlayerLoadoutAnalyzer analyzer = new SlayerLoadoutAnalyzer(null);

	@Test
	public void saltSurvivesEveryTaskAliasAndCombatStyle()
	{
		for (String task : Arrays.asList("Rockslug", "Rockslugs", "Rock slug", "Rock slugs"))
		{
			for (CombatStyle style : CombatStyle.values())
			{
				List<KitItem> layout = inventory(task, style,
					Collections.singletonList(owned(ItemID.SLAYER_BAG_OF_SALT, "Bag of salt", null)));
				KitItem salt = named(layout, "Bag of salt");
				assertEquals(ItemID.SLAYER_BAG_OF_SALT, salt.getItemId());
				assertEquals(25, salt.getQuantity());
				assertEquals(MethodRules.InventoryGroup.UTILITY, salt.getInventoryGroup());
			}
		}
	}

	@Test
	public void requiredToolsAreVisibleEvenWhenNotOwned()
	{
		String[][] cases = {
			{"Rockslugs", "Bag of salt"}, {"Gargoyles", "Rock hammer"},
			{"Lizards", "Ice cooler"}, {"Desert lizards", "Ice cooler"},
			{"Mutated zygomites", "Fungicide spray"}, {"Ancient zygomites", "Fungicide"},
			{"Molanisks", "Slayer bell"}, {"Mogres", "Fishing explosive"},
			{"Warped creatures", "Crystal chime"}, {"Warped terrorbirds", "Crystal chime"},
			{"Warped tortoises", "Crystal chime"}
		};
		for (String[] entry : cases)
		{
			KitItem item = named(inventory(entry[0], CombatStyle.MELEE,
				Collections.emptyList()), entry[1]);
			assertEquals(entry[0], KitItem.Status.MISSING, item.getStatus());
			assertEquals(entry[0], MethodRules.InventoryGroup.UTILITY, item.getInventoryGroup());
		}
	}

	@Test
	public void sprayCannotMasqueradeAsRefills()
	{
		OwnedItem spray = owned(ItemID.SLAYER_SPRAY_PUMP_0, "Fungicide spray 0", null);
		List<KitItem> layout = inventory("Mutated zygomites", CombatStyle.MELEE,
			Collections.singletonList(spray));
		assertEquals(ItemID.SLAYER_SPRAY_PUMP_0, named(layout, "Fungicide spray 0").getItemId());
		assertEquals(KitItem.Status.MISSING, named(layout, "Fungicide").getStatus());
		assertEquals(3, named(layout, "Fungicide").getQuantity());
		layout = inventory("Mutated zygomites", CombatStyle.MELEE,
			Arrays.asList(spray, owned(ItemID.SLAYER_FUNGICIDE, "Fungicide", null)));
		assertEquals(ItemID.SLAYER_FUNGICIDE, named(layout, "Fungicide").getItemId());
		assertEquals(3, named(layout, "Fungicide").getQuantity());
	}

	@Test
	public void iceCoolersAreOnlyForDesertLizards()
	{
		assertTrue(SlayerLoadoutAnalyzer.requiresIceCooler("Lizards", "Kharidian Desert"));
		for (String task : Arrays.asList("Lizardman", "Lizardmen", "Lizardman shamans", "Sulphur lizards"))
		{
			assertFalse(task, SlayerLoadoutAnalyzer.requiresIceCooler(task, ""));
		}
		assertFalse(SlayerLoadoutAnalyzer.requiresIceCooler("Lizards", "Karuulm Slayer Dungeon"));
	}

	@Test
	public void missingFinisherReservesASlotBeforeFoodFillsTheInventory()
	{
		KitItem salt = new KitItem("Bag of salt", -1, 25, KitItem.Status.MISSING)
			.withInventoryGroup(MethodRules.InventoryGroup.UTILITY);
		KitItem food = new KitItem("Shark", ItemID.SHARK, 1, KitItem.Status.BANK);
		List<KitItem> layout = SlayerLoadoutAnalyzer.enforceConcreteInventoryTarget(
			Collections.singletonList(salt), MethodRules.builder().inventoryTarget(28).fillFood().build(),
			null, food);
		assertEquals(28, layout.size());
		assertSame(salt, layout.get(0));
	}

	@Test
	public void everyLeafBladedWeaponAndMagicDartStaffRemainValid()
	{
		for (String task : Arrays.asList("Turoths", "Kurasks"))
		{
			for (OwnedItem weapon : Arrays.asList(
				owned(ItemID.LEAFBLADED_BATTLEAXE, "Leaf-bladed battleaxe", EquipmentInventorySlot.WEAPON),
				owned(ItemID.LEAFBLADED_SWORD, "Leaf-bladed sword", EquipmentInventorySlot.WEAPON),
				owned(ItemID.SLAYER_LEAFBLADED_SPEAR, "Leaf-bladed spear", EquipmentInventorySlot.WEAPON)))
			{
				assertEquals(weaponId(weapon, task, CombatStyle.MELEE),
					equipment(task, CombatStyle.MELEE, Arrays.asList(
						owned(ItemID.ABYSSAL_WHIP, "Abyssal whip", EquipmentInventorySlot.WEAPON), weapon)).get(4).getItemId());
			}
			assertEquals(ItemID.SLAYER_STAFF, equipment(task, CombatStyle.MAGIC,
				Collections.singletonList(owned(ItemID.SLAYER_STAFF, "Slayer's staff", EquipmentInventorySlot.WEAPON))).get(4).getItemId());
			assertFalse(equipment(task, CombatStyle.MELEE, Collections.singletonList(
				owned(ItemID.ABYSSAL_WHIP, "Abyssal whip", EquipmentInventorySlot.WEAPON))).get(4).hasItemId());
		}
	}

	@Test
	public void broadArrowsWinOverOrdinaryArrowsAndMissingBroadAmmoNeverFallsBack()
	{
		for (String task : Arrays.asList("Turoths", "Kurasks"))
		{
			OwnedItem bow = owned(ItemID.MAGIC_SHORTBOW, "Magic shortbow", EquipmentInventorySlot.WEAPON);
			OwnedItem normal = owned(ItemID.RUNE_ARROW, "Rune arrow", EquipmentInventorySlot.AMMO);
			OwnedItem broad = owned(ItemID.SLAYER_BROAD_ARROWS, "Broad arrows", EquipmentInventorySlot.AMMO);
			assertEquals(ItemID.SLAYER_BROAD_ARROWS,
				equipment(task, CombatStyle.RANGED, Arrays.asList(bow, normal, broad)).get(3).getItemId());
			assertFalse(equipment(task, CombatStyle.RANGED, Arrays.asList(bow, normal)).get(3).hasItemId());
		}
	}

	@Test
	public void crossbowsUseBroadBoltsEvenWithAQuiverAndABlowpipeOwned()
	{
		for (String task : Arrays.asList("Turoths", "Kurasks"))
		{
			OwnedItem crossbow = owned(ItemID.XBOWS_CROSSBOW_RUNITE, "Rune crossbow", EquipmentInventorySlot.WEAPON);
			OwnedItem blowpipe = owned(ItemID.TOXIC_BLOWPIPE_LOADED, "Toxic blowpipe", EquipmentInventorySlot.WEAPON);
			OwnedItem quiver = owned(ItemID.DIZANAS_QUIVER_INFINITE, "Blessed Dizana's quiver", EquipmentInventorySlot.CAPE);
			OwnedItem normal = owned(ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE, "Runite bolts", EquipmentInventorySlot.AMMO);
			for (OwnedItem broad : Arrays.asList(
				owned(ItemID.SLAYER_BROAD_BOLT, "Broad bolts", EquipmentInventorySlot.AMMO),
				owned(ItemID.SLAYER_BROAD_BOLT_AMETHYST, "Amethyst broad bolts", EquipmentInventorySlot.AMMO)))
			{
				List<KitItem> layout = equipment(task, CombatStyle.RANGED,
					Arrays.asList(blowpipe, crossbow, quiver, normal, broad));
				assertEquals(ItemID.XBOWS_CROSSBOW_RUNITE, layout.get(4).getItemId());
				assertTrue(layout.get(3).hasItemId());
				assertTrue(layout.get(3).getDisplayName().contains("broad")
					|| layout.get(3).getDisplayName().contains("Broad"));
				assertFalse(layout.stream().anyMatch(item -> item.getItemId() == ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE));
			}
			assertFalse(equipment(task, CombatStyle.RANGED,
				Arrays.asList(crossbow, normal, quiver)).get(3).hasItemId());
			assertFalse(equipment(task, CombatStyle.RANGED,
				Collections.singletonList(blowpipe)).get(4).hasItemId());
		}
	}

	private int weaponId(OwnedItem item, String task, CombatStyle style)
	{
		KitItem weapon = equipment(task, style, Collections.singletonList(item)).get(4);
		assertTrue(task + " must select the owned required weapon", weapon.hasItemId());
		return weapon.getItemId();
	}

	private List<KitItem> equipment(String task, CombatStyle style, List<OwnedItem> pool)
	{
		TaskStrategy strategy = strategy(style);
		List<Requirement> requirements = analyzer.requirementsFor(task, "", style, 25, false);
		OwnedItem weapon = SlayerLoadoutAnalyzer.selectRecommendedWeapon(task, strategy, style,
			requirements, Collections.emptyList(), pool);
		return SlayerLoadoutAnalyzer.buildEquipmentLayout(task, strategy, style, requirements,
			Collections.emptyList(), pool, true, weapon);
	}

	private List<KitItem> inventory(String task, CombatStyle style, List<OwnedItem> pool)
	{
		List<KitItem> layout = analyzer.buildInventoryLayout(task, strategy(style), "", "", style,
			analyzer.requirementsFor(task, "", style, 25, false), false, 8, pool, true, Collections.emptyList());
		assertTrue(task + " exceeds 4x7", layout.size() <= 28);
		return layout;
	}

	private static TaskStrategy strategy(CombatStyle style)
	{
		return TaskStrategy.builder(style == CombatStyle.FLEXIBLE ? TaskStrategy.CombatStyle.HYBRID
			: TaskStrategy.CombatStyle.valueOf(style.name()), "Required items regression")
			.reviewed("2026-09-02").build();
	}

	private static OwnedItem owned(int id, String name, EquipmentInventorySlot slot)
	{
		return new OwnedItem(id, name, 100, KitItem.Status.BANK,
			slot == null ? -1 : slot.getSlotIdx(), null, Collections.emptySet());
	}

	private static KitItem named(List<KitItem> layout, String name)
	{
		return layout.stream().filter(item -> name.equals(item.getDisplayName())).findFirst()
			.orElseThrow(() -> new AssertionError("Missing recommendation: " + name));
	}
}
