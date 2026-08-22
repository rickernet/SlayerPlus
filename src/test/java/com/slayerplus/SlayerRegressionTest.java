package com.slayerplus;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SlayerRegressionTest
{
	@Test
	public void exactAssignmentChatRestoresTaskBeforeVarpsSettle()
	{
		final SlayerTaskChatUpdate assignment = SlayerTaskChatUpdate.parse(
			"You're assigned to kill ankou; only 57 more to go."
		);
		assertNotNull(assignment);
		assertEquals("Ankou", assignment.getTaskName());
		assertEquals(57, assignment.getRemaining());
		assertEquals(57, SlayerPlusPlugin.firstPositive(0, -1, 57));
	}

	@Test
	public void exactStatusChatRestoresStreakAndPoints()
	{
		final SlayerTaskChatUpdate status = SlayerTaskChatUpdate.parse(
			"You\u2019ve completed 766 tasks in a row and currently have a total of 289 points."
		);
		assertNotNull(status);
		assertEquals(766, status.getStreak());
		assertEquals(289, status.getPoints());
		assertEquals(
			766,
			SlayerPlusPlugin.preferLiveOrProfileValue(0, 766, -1)
		);
		assertEquals(
			289,
			SlayerPlusPlugin.preferLiveOrProfileValue(0, 0, 289)
		);
	}

	@Test
	public void versionedSidebarIconIsPackagedWithThePlugin()
	{
		assertNotNull(SlayerPlusPlugin.class.getResource(
			"slayerplus_sidebar_v2.png"
		));
	}

	@Test
	public void turaelPointBoostingUsesNineEasyTasksThenTheSelectedBonusMaster()
	{
		assertPointBoostDecision(0, 1, 1, false);
		assertPointBoostDecision(8, 9, 1, false);
		assertPointBoostDecision(9, 10, 8, true);
		assertPointBoostDecision(49, 50, 8, true);
		assertPointBoostDecision(99, 100, 8, true);
		assertPointBoostDecision(249, 250, 8, true);
		assertPointBoostDecision(999, 1000, 8, true);

		final SlayerPointBoostCoordinator.Decision duradel =
			SlayerPointBoostCoordinator.nextAssignment(
				759,
				SlayerPreference.BonusMaster.DURADEL
			);
		assertTrue(duradel.isBonusTask());
		assertEquals(760, duradel.getCompletionNumber());
		assertEquals(5, duradel.getMasterId());
		assertEquals("Bonus task 760", duradel.getProgressText());

		final SlayerPointBoostCoordinator.Decision afterBonus =
			SlayerPointBoostCoordinator.nextAssignment(
				760,
				SlayerPreference.BonusMaster.DURADEL
			);
		assertFalse(afterBonus.isBonusTask());
		assertEquals(1, afterBonus.getMasterId());
		assertEquals("Turael/Aya task 1 of 9", afterBonus.getProgressText());
	}

	@Test
	public void everyTuraelPointBoostTaskHasAReviewedFastTripAndCompactLoadout()
	{
		final String[] assignments = {
			"Banshees", "Bats", "Bears", "Birds", "Cave bugs",
			"Cave crawlers", "Cave slimes", "Cows", "Crawling hands",
			"Dogs", "Dwarves", "Ghosts", "Goblins", "Icefiends",
			"Kalphites", "Lizards", "Minotaurs", "Monkeys", "Rats",
			"Scorpions", "Skeletons", "Spiders", "Wolves", "Zombies"
		};
		assertEquals(24, assignments.length);
		assertTrue(SlayerTuraelBoostCatalog.sizeForRegression() >= 24);
		assertNotNull(SlayerTuraelBoostCatalog.find("Dwarf"));
		assertNotNull(SlayerTuraelBoostCatalog.find("Wolf"));

		for (final String task : assignments)
		{
			final SlayerTuraelBoostCatalog.Entry entry =
				SlayerTuraelBoostCatalog.find(task);
			assertNotNull(task, entry);
			assertFalse(task, entry.getLocation().isEmpty());
			assertFalse(task, entry.getTravel().isEmpty());
			assertFalse(task, entry.getRequirements().isEmpty());

			final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				task,
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.NEVER,
				SlayerPreference.CombatStyle.AUTOMATIC,
				entry.getLocation(),
				false
			).withAdditionalTags(
				SlayerTaskStrategy.MethodTag.TURAEL_POINT_BOOST
			);
			assertTrue(task, strategy.isReviewed());
			final SlayerRouteCatalog.RouteProfile route =
				SlayerRouteCatalog.resolve(task, entry.getLocation(), false);
			assertNotNull(task, route);
			assertNotNull(task, route.getPrimaryDestination());
			assertTrue(task,
				route.getMode() != SlayerRouteCatalog.RouteMode.NPC_ONLY);
			assertFalse(task,
				SlayerTravelRouteCatalog.fallbacksFor(entry.getLocation()).isEmpty());

			final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
				task, entry.getLocation(), strategy
			);
			assertEquals(task, 0, rules.resolveRestoreSlots(strategy));
			assertEquals(task, 2,
				rules.resolveFoodSlots(strategy, strategy.getFoodSlots()));
			if (strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
			{
				assertEquals(task, 300, rules.getCannonballQuantity());
			}
		}
	}

	@Test
	public void turaelWorkflowRecommendationCarriesItsCompactTripMarker()
	{
		final SlayerPlusConfig boostConfig = new SlayerPlusConfig()
		{
			@Override
			public SlayerPreference.Workflow slayerWorkflow()
			{
				return SlayerPreference.Workflow.TURAEL_POINT_BOOST;
			}
		};
		final SlayerRecommendationEngine engine =
			new SlayerRecommendationEngine(null, boostConfig);
		final SlayerRecommendation recommendation = engine.recommend(
			"Bats",
			SlayerPointBoostCoordinator.TURAEL_AYA_MASTER_ID,
			"Not restricted"
		);

		assertEquals("Silvarea limestone mine", recommendation.getLocation());
		assertTrue(recommendation.getStrategy().hasTag(
			SlayerTaskStrategy.MethodTag.TURAEL_POINT_BOOST
		));
		assertEquals(2, SlayerMethodRuleCatalog.resolve(
			"Bats", recommendation.getLocation(), recommendation.getStrategy()
		).resolveFoodSlots(
			recommendation.getStrategy(),
			recommendation.getStrategy().getFoodSlots()
		));
	}

	@Test
	public void turaelTravelKitKeepsSeveralTeleportsWithoutDuplicatingWornGear()
	{
		SlayerLoadoutPlan plan = new SlayerLoadoutPlan(
			"Ready", "Ready", "Ready", "Turael boost",
			Collections.singletonList(new SlayerLoadoutItem(
				"Max cape", 100, 1, SlayerLoadoutItem.Status.EQUIPPED
			)),
			Collections.singletonList(new SlayerLoadoutItem(
				"Emergency food", 200, 1, SlayerLoadoutItem.Status.BANK
			).withInventoryGroup(SlayerMethodRules.InventoryGroup.FOOD)),
			Collections.emptyList()
		);

		/* Worn travel gear is not copied into the pack. */
		assertTrue(plan == SlayerPlusPlugin.appendMasterReturnTeleportForRegression(
			plan, 100, "Max cape"
		));

		for (int id = 301; id <= 304; id++)
		{
			plan = SlayerPlusPlugin.appendMasterReturnTeleportForRegression(
				plan, id, "Travel item " + id
			);
		}
		assertEquals(5, plan.getInventoryItems().size());
		assertEquals(4, plan.getInventoryItems().stream()
			.filter(item -> item.getDisplayName().startsWith("Travel item"))
			.count());
		assertEquals(1, plan.getInventoryItems().stream()
			.filter(item -> item.getInventoryGroup()
				== SlayerMethodRules.InventoryGroup.FOOD)
			.count());
	}

	@Test
	public void turaelHazardTasksKeepTheirNonNegotiableSupplies()
	{
		assertTuraelRequiredItem("Cave bugs", "Safe light source");
		assertTuraelRequiredItem("Cave slimes", "Safe light source");
		assertTuraelRequiredItem("Cave slimes", "Poison protection");
		assertTuraelRequiredItem("Cave crawlers", "Poison protection");
		assertTuraelRequiredItem("Lizards", "Ice coolers");
		assertTuraelRequiredItem("Lizards", "Desert heat protection");
		assertTuraelRequiredItem("Skeletons", "Rope");
	}

	@Test
	public void turaelDungeonRoutesContinueBeyondTheirSurfaceEntrance()
	{
		assertStagedBoostDungeon("Cave bugs", "Dorgesh-Kaan South Dungeon");
		assertStagedBoostDungeon("Cave slimes", "Dorgesh-Kaan South Dungeon");
		assertStagedBoostDungeon("Cave crawlers", "Fremennik Slayer Dungeon");
		assertStagedBoostDungeon("Ghosts", "Catacombs of Kourend");
		assertStagedBoostDungeon("Skeletons", "Digsite Dungeon");

		assertTransportChainBoostDungeon("Dwarves", "White Wolf Tunnel pub");
		assertTransportChainBoostDungeon("Minotaurs", "Stronghold of Security");
		assertTransportChainBoostDungeon("Rats", "Varrock Sewers");

		final SlayerRouteCatalog.RouteProfile digsite =
			SlayerRouteCatalog.resolve("Skeletons", "Digsite Dungeon", false);
		assertNotNull(digsite.getTransitionSpec());
		assertTrue(digsite.getTransitionSpec().matchesObjectName("Winch"));
		assertTrue(digsite.getTransitionSpec().matchesAction("Operate"));
	}

	@Test
	public void turaelNoCannonLocationsCannotInheritABroadCannonStrategy()
	{
		for (final String task : Arrays.asList(
			"Banshees", "Cave bugs", "Cave crawlers", "Cave slimes",
			"Crawling hands", "Dwarves", "Ghosts"
		))
		{
			final SlayerTuraelBoostCatalog.Entry entry =
				SlayerTuraelBoostCatalog.find(task);
			assertNotNull(task, entry);
			assertFalse(task, entry.supportsCannon());
			final SlayerTaskStrategy cannonStrategy = SlayerTaskStrategy.builder(
				SlayerTaskStrategy.CombatStyle.RANGED,
				"Cannon regression"
			).tags(
				SlayerTaskStrategy.MethodTag.CANNON,
				SlayerTaskStrategy.MethodTag.TURAEL_POINT_BOOST
			).reviewed("2026-08-18").build();
			final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
				task, entry.getLocation(), cannonStrategy
			);
			assertEquals(task, 0, rules.getCannonballQuantity());
		}
	}

	private static void assertStagedBoostDungeon(
		final String task,
		final String location)
	{
		final SlayerRouteCatalog.RouteProfile route =
			SlayerRouteCatalog.resolve(task, location, false);
		assertNotNull(task, route);
		assertEquals(task, SlayerRouteCatalog.RouteMode.STAGED_INTERIOR,
			route.getMode());
		assertNotNull(task, route.getSurfaceAccess());
		assertNotNull(task, route.getPrimaryDestination());
		assertFalse(task, route.getSurfaceAccess().equals(
			route.getPrimaryDestination()));
		assertTrue(task, route.isInsideEncounterArea(
			route.getPrimaryDestination()));
	}

	private static void assertTransportChainBoostDungeon(
		final String task,
		final String location)
	{
		final SlayerRouteCatalog.RouteProfile route =
			SlayerRouteCatalog.resolve(task, location, false);
		assertNotNull(task, route);
		assertEquals(task, SlayerRouteCatalog.RouteMode.TRANSPORT_CHAIN_INTERIOR,
			route.getMode());
		assertTrue(task, route.getPrimaryDestination().getY() >= 5000);
	}

	private static void assertTuraelRequiredItem(
		final String task,
		final String displayName)
	{
		final SlayerTuraelBoostCatalog.Entry entry =
			SlayerTuraelBoostCatalog.find(task);
		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			task,
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.ALLOW,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			entry.getLocation(),
			false
		).withAdditionalTags(
			SlayerTaskStrategy.MethodTag.TURAEL_POINT_BOOST
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			task, entry.getLocation(), strategy
		);
		assertTrue(task + " -> " + displayName,
			rules.getRequiredItems().stream().anyMatch(
				item -> item.getDisplayName().equals(displayName)
			));
	}

	private static void assertPointBoostDecision(
		final int completedStreak,
		final int completionNumber,
		final int masterId,
		final boolean bonusTask)
	{
		final SlayerPointBoostCoordinator.Decision decision =
			SlayerPointBoostCoordinator.nextAssignment(
				completedStreak,
				SlayerPreference.BonusMaster.KONAR
			);
		assertEquals(completionNumber, decision.getCompletionNumber());
		assertEquals(masterId, decision.getMasterId());
		assertEquals(bonusTask, decision.isBonusTask());
	}

	@Test
	public void skeletalWyvernsUseProtectedProgressiveGearAndReviewedSupplies()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategyCatalog.resolve(
			"Skeletal wyverns",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Asgarnian Ice Dungeon",
			false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MELEE,
			melee.getCombatStyle());
		assertEquals("dragon hunter lance", melee.getWeaponPriorities().get(0));
		assertTrue(melee.getWeaponPriorities().contains("abyssal whip"));
		assertFalse(melee.getWeaponPriorities().contains("osmumten s fang"));

		final List<String> meleeShields = SlayerEquipmentAuditCatalog.priorities(
			"Skeletal wyverns", melee, EquipmentInventorySlot.SHIELD,
			"Dragon hunter lance"
		);
		assertEquals("ancient wyvern shield", meleeShields.get(0));
		assertTrue(meleeShields.contains("elemental shield"));
		assertFalse(meleeShields.contains("anti dragon shield"));

		final SlayerMethodRules meleeRules = SlayerMethodRuleCatalog.resolve(
			"Skeletal wyverns", "Asgarnian Ice Dungeon", melee
		);
		assertEquals(4, meleeRules.resolveRestoreSlots(melee));
		assertEquals(8, meleeRules.resolveFoodSlots(melee, melee.getFoodSlots()));
		assertTrue(meleeRules.includesStyleBoost());
		assertTrue(meleeRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Special attack weapon")
				&& item.getSlotCount() == 1
		));
		assertEquals(SlayerMethodRules.Spellbook.STANDARD,
			meleeRules.getSpellbook());
		assertEquals("High Level Alchemy", meleeRules.getPrimarySpell());
		assertTrue(meleeRules.requiresRunePouch());
		assertFalse(meleeRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().toLowerCase().contains("antifire")
		));

		final SlayerTaskStrategy ranged = SlayerTaskStrategyCatalog.resolve(
			"Skeletal wyverns",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_RANGED,
			"Asgarnian Ice Dungeon",
			false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED,
			ranged.getCombatStyle());
		assertTrue(ranged.getMethod().contains("dragonstone bolts"));
		assertEquals("dragonfire ward", SlayerEquipmentAuditCatalog.priorities(
			"Skeletal wyverns", ranged, EquipmentInventorySlot.SHIELD,
			"Dragon hunter crossbow"
		).get(0));
		final SlayerMethodRules rangedRules = SlayerMethodRuleCatalog.resolve(
			"Skeletal wyverns", "Asgarnian Ice Dungeon", ranged
		);
		assertEquals(2, rangedRules.resolveRestoreSlots(ranged));
		assertEquals(6, rangedRules.resolveFoodSlots(ranged, ranged.getFoodSlots()));
		assertTrue(rangedRules.includesStyleBoost());
		assertFalse(rangedRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Special attack weapon")
		));

		final SlayerTaskStrategy magic = SlayerTaskStrategyCatalog.resolve(
			"Skeletal wyverns",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_MAGIC,
			"Asgarnian Ice Dungeon",
			false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MAGIC,
			magic.getCombatStyle());
		final SlayerMethodRules magicRules = SlayerMethodRuleCatalog.resolve(
			"Skeletal wyverns", "Asgarnian Ice Dungeon", magic
		);
		assertEquals("Fire Surge or Fire Wave", magicRules.getPrimarySpell());
		assertEquals(4, magicRules.getPouchRunes().size());
		assertEquals("ancient wyvern shield", SlayerEquipmentAuditCatalog.priorities(
			"Skeletal wyverns", magic, EquipmentInventorySlot.SHIELD,
			"Dragon hunter wand"
		).get(0));
		assertEquals(2, magicRules.resolveRestoreSlots(magic));
		assertEquals(6, magicRules.resolveFoodSlots(magic, magic.getFoodSlots()));
		assertTrue(magicRules.includesStyleBoost());
	}

	@Test
	public void eternalSlayerRingOutranksChargedRingsForEternalFamily()
	{
		final int eternal = SlayerPlusPlugin.travelNameMatchQualityForRegression(
			"Slayer ring (eternal)", "Eternal slayer ring"
		);
		final int charged = SlayerPlusPlugin.travelNameMatchQualityForRegression(
			"Slayer ring (8)", "Eternal slayer ring"
		);

		assertTrue(eternal > charged);
		assertTrue(SlayerPlusPlugin.travelNameMatchQualityForRegression(
			"Slayer ring (8)", "Slayer ring"
		) > 0);
	}

	@Test
	public void portraitPollingSkipsIdleTicksAndThrottlesFailedRetries()
	{
		assertFalse(SlayerPlusPlugin.shouldCheckPortraitForRegression(
			4, false, 0
		));
		assertTrue(SlayerPlusPlugin.shouldCheckPortraitForRegression(
			5, false, 0
		));
		assertFalse(SlayerPlusPlugin.shouldCheckPortraitForRegression(
			8, true, 10
		));
		assertTrue(SlayerPlusPlugin.shouldCheckPortraitForRegression(
			10, true, 10
		));
	}

	@Test
	public void caveHorrorCannonRouteContinuesToTheReviewedInteriorSetupTile()
	{
		final SlayerRouteCatalog.RouteProfile route = SlayerRouteCatalog.resolve(
			"Cave horrors", "Mos Le'Harmless Cave", false
		);
		final WorldPoint setupTile = new WorldPoint(3775, 9407, 0);
		final WorldPoint loadedNpc = new WorldPoint(3782, 9410, 0);

		assertNotNull(route);
		assertTrue(route.isStaged());
		assertEquals(new WorldPoint(3748, 2974, 0), route.getSurfaceAccess());
		assertEquals(setupTile, route.getPrimaryDestination());
		assertEquals(setupTile, SlayerRouteCatalog.findCannonPosition(
			"Cave horror", "Mos Le'Harmless Cave"
		));

		final SlayerRouteCoordinator.Stage inside =
			SlayerRouteCoordinator.resolve(
				route,
				new WorldPoint(3768, 9398, 0),
				null, Collections.emptySet(),
				setupTile, Collections.singleton(setupTile),
				loadedNpc, Collections.singleton(loadedNpc),
				null, Collections.emptySet()
			);
		assertEquals(SlayerRouteCoordinator.StageKind.METHOD_POSITION,
			inside.getKind());
		assertEquals(setupTile, inside.getDestination());
		assertEquals(Collections.singleton(setupTile), inside.getTargets());
	}

	@Test
	public void fossilIslandWyvernsUseReachableTrapdoorStageAndReviewedGear()
	{
		final SlayerRouteCatalog.RouteProfile route = SlayerRouteCatalog.resolve(
			"Fossil Island wyverns", "Wyvern Cave on Fossil Island", false
		);
		assertNotNull(route);
		assertTrue(route.isStaged());
		assertEquals(new WorldPoint(3680, 3854, 0), route.getSurfaceAccess());
		assertEquals(9, route.getRouteTargets(new WorldPoint(3600, 3810, 0)).size());
		final SlayerRouteCoordinator.Stage surfaceStage =
			SlayerRouteCoordinator.resolve(
				route,
				new WorldPoint(3200, 3200, 0),
				null, Collections.emptySet(),
				null, Collections.emptySet(),
				null, Collections.emptySet(),
				null, Collections.emptySet()
			);
		assertEquals(SlayerRouteCoordinator.StageKind.SURFACE_APPROACH,
			surfaceStage.getKind());
		assertEquals(9, surfaceStage.getTargets().size());
		assertTrue(route.getTransitionInstruction().contains("Magic Mushtree"));
		assertTrue(route.getTransitionSpec().matchesObjectName("Trapdoor"));
		assertTrue(route.getTransitionSpec().matchesObjectName("Trap door"));
		assertTrue(route.getTransitionSpec().matchesAction("Climb-down"));
		assertTrue(SlayerTeleportHighlighter.isContextlessSelectedDestinationChoice(
			"Digsite pendant (3)",
			"Fossil Island",
			Collections.singleton("fossil island")
		));
		assertFalse(SlayerTeleportHighlighter.isContextlessSelectedDestinationChoice(
			"Digsite pendant (3)",
			"Digsite",
			Collections.singleton("fossil island")
		));
		assertTrue(SlayerTeleportHighlighter.firstStageActionPreference(
			"Rub", "Digsite pendant"
		) > 0);
		final WorldPoint transportLanding = new WorldPoint(3595, 10291, 0);
		assertTrue(route.isInsideEncounterArea(transportLanding));
		assertEquals(new WorldPoint(3616, 10272, 0),
			route.getRouteDestination(transportLanding));

		final SlayerTaskStrategy automatic = SlayerTaskStrategyCatalog.resolve(
			"Fossil Island wyverns",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Wyvern Cave on Fossil Island",
			false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MELEE,
			automatic.getCombatStyle());
		assertEquals(SlayerTaskStrategy.ArmourFocus.DAMAGE,
			automatic.getArmourFocus());
		assertTrue(automatic.getWeaponPriorities().contains("abyssal whip"));

		final SlayerTaskStrategy ranged = SlayerTaskStrategyCatalog.resolve(
			"Fossil Island wyverns",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_RANGED,
			"Wyvern Cave on Fossil Island",
			false
		);
		assertEquals("dragon hunter crossbow",
			ranged.getWeaponPriorities().get(0));
		assertTrue(ranged.getWeaponPriorities().contains(
			"hunters sunlight crossbow"
		));
		assertTrue(ranged.getSelectionNote().contains("not treated as a safespot"));

		final SlayerMethodRules supplies = SlayerMethodRuleCatalog.resolve(
			"Fossil Island wyverns", "Wyvern Cave on Fossil Island", automatic
		);
		assertEquals(3, supplies.resolveRestoreSlots(automatic));
		assertEquals(8, supplies.resolveFoodSlots(automatic, automatic.getFoodSlots()));
	}

	@Test
	public void hotPathOptimizationGuardsPreserveRealChanges()
	{
		final SlayerLoadoutPlan fingerprintPlan = new SlayerLoadoutPlan(
			"equipment", "inventory", "owned", "Kalphites",
			Arrays.asList(new SlayerLoadoutItem(
				"Slayer helmet", 1, 1, SlayerLoadoutItem.Status.BANK
			)),
			Arrays.asList(new SlayerLoadoutItem(
				"Prayer potion(4)", 2, 1, SlayerLoadoutItem.Status.BANK
			)),
			Collections.emptyList()
		);
		final String originalBankTagState = SlayerPlusPlugin.bankTagStateFingerprint(
			fingerprintPlan, null, 3, Arrays.asList(4, 5),
			true, 0, true, -1
		);
		assertEquals(originalBankTagState, SlayerPlusPlugin.bankTagStateFingerprint(
			fingerprintPlan, null, 3, Arrays.asList(4, 5),
			true, 0, true, -1
		));
		assertFalse(originalBankTagState.equals(
			SlayerPlusPlugin.bankTagStateFingerprint(
				fingerprintPlan, null, 6, Arrays.asList(4, 5),
				true, 0, true, -1
			)
		));
		assertFalse(originalBankTagState.equals(
			SlayerPlusPlugin.bankTagStateFingerprint(
				fingerprintPlan, null, 3, Arrays.asList(4, 7),
				true, 0, true, -1
			)
		));
		assertFalse(SlayerPlusPlugin.shouldPollExtraQuiverSnapshotForRegression(1));
		assertFalse(SlayerPlusPlugin.shouldPollExtraQuiverSnapshotForRegression(4));
		assertTrue(SlayerPlusPlugin.shouldPollExtraQuiverSnapshotForRegression(5));
		assertTrue(SlayerPlusPlugin.shouldPollExtraQuiverSnapshotForRegression(10));
		assertTrue(SlayerPlusPlugin.isQuiverRelevantInterfaceGroupForRegression(
			InterfaceID.EQUIPMENT
		));
		assertTrue(SlayerPlusPlugin.isQuiverRelevantInterfaceGroupForRegression(
			InterfaceID.DIZANAS_QUIVER
		));
		assertFalse(SlayerPlusPlugin.isQuiverRelevantInterfaceGroupForRegression(
			InterfaceID.CHATMENU
		));
		assertFalse(SlayerPlusPlugin.shouldSkipUnchangedBankScanForRegression(
			false, true
		));
		assertFalse(SlayerPlusPlugin.shouldSkipUnchangedBankScanForRegression(
			true, false
		));
		assertTrue(SlayerPlusPlugin.shouldSkipUnchangedBankScanForRegression(
			true, true
		));
		assertFalse(SlayerPlusPlugin.shouldRefreshCarriedContainerForRegression(
			InventoryID.WORN, false, true
		));
		assertTrue(SlayerPlusPlugin.shouldRefreshCarriedContainerForRegression(
			InventoryID.WORN, true, false
		));
		assertFalse(SlayerPlusPlugin.shouldRefreshCarriedContainerForRegression(
			InventoryID.INV, true, false
		));
		assertTrue(SlayerPlusPlugin.shouldRefreshCarriedContainerForRegression(
			InventoryID.INV, false, true
		));

		assertFalse(SlayerPlusPlugin.shouldPersistSnapshotForRegression(
			true, Collections.singleton(1), Collections.singleton(1)
		));
		assertTrue(SlayerPlusPlugin.shouldPersistSnapshotForRegression(
			true, Collections.singleton(1), Collections.singleton(2)
		));
		assertTrue(SlayerPlusPlugin.shouldPersistSnapshotForRegression(
			false, 5, 5
		));

		assertFalse(SlayerTeleportHighlighter.shouldRefreshOpenMenuOnTick(
			false, true, true
		));
		assertFalse(SlayerTeleportHighlighter.shouldRefreshOpenMenuOnTick(
			true, false, false
		));
		assertTrue(SlayerTeleportHighlighter.shouldRefreshOpenMenuOnTick(
			true, true, false
		));
		assertTrue(SlayerTeleportHighlighter.shouldRefreshOpenMenuOnTick(
			true, false, true
		));
		assertFalse(SlayerTeleportHighlighter.shouldInspectWidgetGroupForRegression(
			InterfaceID.BANKMAIN
		));
		assertTrue(SlayerPlusPlugin.shouldHandleBankWidgetLoadForRegression(
			false, InterfaceID.BANKMAIN
		));
		assertFalse(SlayerPlusPlugin.shouldHandleBankWidgetLoadForRegression(
			true, InterfaceID.BANKMAIN
		));
		assertFalse(SlayerPlusPlugin.shouldHandleBankWidgetLoadForRegression(
			false, InterfaceID.CHATMENU
		));
		assertTrue(SlayerPlusPlugin.shouldReconcileBankVisibilityForRegression(
			false, true
		));
		assertTrue(SlayerPlusPlugin.shouldReconcileBankVisibilityForRegression(
			true, false
		));
		assertFalse(SlayerPlusPlugin.shouldReconcileBankVisibilityForRegression(
			true, true
		));
		assertTrue(SlayerTeleportHighlighter.shouldInspectWidgetGroupForRegression(
			InterfaceID.CHATMENU
		));
		assertTrue(SlayerTeleportHighlighter.shouldInspectWidgetGroupForRegression(
			InterfaceID.GRAPHICAL_MULTI
		));
		assertTrue(SlayerBankTagLayoutService.samePersistedStateForRegression(
			new int[] {1, -1, 2},
			new int[] {1, -1, 2},
			new java.util.LinkedHashSet<>(Arrays.asList(1, 2)),
			new java.util.LinkedHashSet<>(Arrays.asList(2, 1))
		));
		assertFalse(SlayerBankTagLayoutService.samePersistedStateForRegression(
			new int[] {1, -1, 2},
			new int[] {1, 2, -1},
			new java.util.LinkedHashSet<>(Arrays.asList(1, 2)),
			new java.util.LinkedHashSet<>(Arrays.asList(1, 2))
		));
		assertFalse(SlayerBankTagLayoutService.samePersistedStateForRegression(
			new int[] {1, -1, 2},
			new int[] {1, -1, 2},
			new java.util.LinkedHashSet<>(Arrays.asList(1, 2)),
			Collections.singleton(1)
		));
	}

	@Test
	public void nearbyBankDiscoveryAcceptsOnlyRealBankActions()
	{
		assertTrue(SlayerPlusPlugin.hasBankActionForRegression(
			new String[] {"Use", "Bank", "Collect"}
		));
		assertTrue(SlayerPlusPlugin.hasBankActionForRegression(
			new String[] {null, " bank "}
		));
		assertFalse(SlayerPlusPlugin.hasBankActionForRegression(
			new String[] {"Deposit", "Collect"}
		));
		assertFalse(SlayerPlusPlugin.hasBankActionForRegression(null));

		assertTrue(SlayerPlusPlugin.isBankObjectForRegression(
			"Bank chest", new String[] {"Use", "Collect"}
		));
		assertTrue(SlayerPlusPlugin.isBankObjectForRegression(
			"Bank chest", new String[] {"Bank", "Collect"}
		));
		assertFalse(SlayerPlusPlugin.isBankObjectForRegression(
			"Bank deposit box", new String[] {"Deposit"}
		));
		assertFalse(SlayerPlusPlugin.isBankObjectForRegression(
			"Brimstone chest", new String[] {"Open"}
		));
		assertFalse(SlayerPlusPlugin.isBankObjectForRegression(
			"Storage chest", new String[] {"Use"}
		));

		final WorldPoint banker = new WorldPoint(3300, 3100, 0);
		final WorldPoint perimeter = new WorldPoint(3299, 3100, 0);
		assertEquals(perimeter,
			SlayerPlusPlugin.liveBankNpcTargetForRegression(perimeter, banker));
		assertEquals(banker,
			SlayerPlusPlugin.liveBankNpcTargetForRegression(null, banker));
	}

	@Test
	public void profitArrowPolicyDiffersFromFastXpAndHonorsCompatibility()
	{
		final List<String> regularProfit =
			SlayerLoadoutAnalyzer.standardArrowPriorityForRegression(
				SlayerTaskStrategy.CostPolicy.EFFICIENT,
				false,
				true
			);
		assertEquals("rune arrow", regularProfit.get(0));
		assertTrue(regularProfit.indexOf("amethyst arrow")
			< regularProfit.indexOf("dragon arrow"));

		final List<String> bossProfit =
			SlayerLoadoutAnalyzer.standardArrowPriorityForRegression(
				SlayerTaskStrategy.CostPolicy.EFFICIENT,
				true,
				true
			);
		assertEquals("amethyst arrow", bossProfit.get(0));

		final List<String> fastXp =
			SlayerLoadoutAnalyzer.standardArrowPriorityForRegression(
				SlayerTaskStrategy.CostPolicy.MAX_DPS,
				false,
				true
			);
		assertEquals("seeking dragon arrow", fastXp.get(0));

		final List<String> incompatibleBow =
			SlayerLoadoutAnalyzer.standardArrowPriorityForRegression(
				SlayerTaskStrategy.CostPolicy.MAX_DPS,
				false,
				false
			);
		assertFalse(incompatibleBow.stream().anyMatch(
			arrow -> arrow.contains("dragon arrow")
		));
	}

	@Test
	public void guidedSpellbookRoutesCoverEveryPermanentSpellbook()
	{
		assertEquals(
			new WorldPoint(3231, 9311, 0),
			SlayerSpellbookRouteCatalog.resolve(
				"Ancient Magicks", SlayerSpellbookRouteCatalog.STANDARD
			).getDestination()
		);
		assertEquals(
			new WorldPoint(2156, 3864, 0),
			SlayerSpellbookRouteCatalog.resolve(
				"Lunar spellbook", SlayerSpellbookRouteCatalog.STANDARD
			).getDestination()
		);
		assertEquals(
			new WorldPoint(1714, 3883, 0),
			SlayerSpellbookRouteCatalog.resolve(
				"Arceuus spellbook", SlayerSpellbookRouteCatalog.STANDARD
			).getDestination()
		);
		final SlayerSpellbookRouteCatalog.Route standardFromArceuus =
			SlayerSpellbookRouteCatalog.resolve(
				"Standard spellbook", SlayerSpellbookRouteCatalog.ARCEUUS
			);
		assertEquals(new WorldPoint(1714, 3883, 0), standardFromArceuus.getDestination());
		assertEquals("Standard spellbook", standardFromArceuus.getSpellbookName());
		assertTrue(standardFromArceuus.getInteraction().contains("Talk to Tyss"));
		assertTrue(standardFromArceuus.getInteraction().contains("lift"));
		assertEquals(
			new WorldPoint(3231, 9311, 0),
			SlayerSpellbookRouteCatalog.resolve(
				"Standard spellbook", SlayerSpellbookRouteCatalog.ANCIENT
			).getDestination()
		);
		assertTrue(SlayerSpellbookRouteCatalog.resolve(
			"Standard spellbook", SlayerSpellbookRouteCatalog.STANDARD
		) == null);
	}

	@Test
	public void postSpellbookRoutingBanksOnlyWhenPreparationNeedsIt()
	{
		final SlayerLoadoutPlan carried = new SlayerLoadoutPlan(
			"Carried", "Carried", "Ready", "Ready",
			Collections.singletonList(new SlayerLoadoutItem(
				"Slayer helmet", ItemID.SLAYER_RING_8, 1,
				SlayerLoadoutItem.Status.EQUIPPED
			)),
			Collections.singletonList(new SlayerLoadoutItem(
				"Rune pouch", ItemID.BH_RUNE_POUCH, 1,
				SlayerLoadoutItem.Status.INVENTORY
			)),
			Collections.emptyList()
		);
		assertTrue(SlayerPlusPlugin.shouldReturnToBankAfterSpellbookForRegression(
			false, carried
		));
		assertTrue(SlayerPlusPlugin.shouldReturnToBankAfterSpellbookForRegression(
			true, carried
		));

		final SlayerLoadoutPlan banked = new SlayerLoadoutPlan(
			"Banked", "Banked", "Needs bank", "Needs bank",
			Collections.singletonList(new SlayerLoadoutItem(
				"Slayer helmet", ItemID.SLAYER_RING_8, 1,
				SlayerLoadoutItem.Status.BANK
			)),
			Collections.emptyList(),
			Collections.emptyList()
		);
		assertTrue(SlayerPlusPlugin.shouldReturnToBankAfterSpellbookForRegression(
			true, banked
		));
	}

	@Test
	public void combatAchievementHelmetOptionReplacesLegacyAutomaticChoice()
	{
		assertEquals(
			SlayerHelmetPreference.COMBAT_ACHIEVEMENT,
			SlayerHelmetPreference.normalize("Automatic (best)")
		);
		assertEquals(3, SlayerLoadoutAnalyzer
			.combatAchievementHelmetTierForRegression("TzKal slayer helmet (i)"));
		assertEquals(2, SlayerLoadoutAnalyzer
			.combatAchievementHelmetTierForRegression("Vampyric slayer helmet (i)"));
		assertEquals(1, SlayerLoadoutAnalyzer
			.combatAchievementHelmetTierForRegression("TzTok slayer helmet (i)"));
		assertEquals(0, SlayerLoadoutAnalyzer
			.combatAchievementHelmetTierForRegression("Purple slayer helmet (i)"));
	}

	@Test
	public void ownedSlayerHelmetSnapshotRoundTripsAndRejectsInvalidIds()
	{
		final List<Integer> itemIds = Arrays.asList(11864, 25177, 11864, -1);
		assertEquals("11864,25177,11864", SlayerPlusPlugin.serializeItemIds(itemIds));
		assertEquals(
			new java.util.LinkedHashSet<>(Arrays.asList(11864, 25177)),
			SlayerPlusPlugin.parseItemIds("11864,broken,25177,11864,-4")
		);
	}

	@Test
	public void seersBankUsesReviewedWalkableApproachTile()
	{
		final WorldPoint approach = new WorldPoint(2727, 3492, 0);
		assertTrue(SlayerBankRouteCatalog.getBankTargets(false).contains(approach));
		assertFalse(SlayerBankRouteCatalog.getBankTargets(false).contains(
			new WorldPoint(2727, 3493, 0)
		));
		assertEquals(
			approach,
			SlayerBankRouteCatalog.getPreferredLocalApproachTarget(
				new WorldPoint(2757, 3477, 0)
			)
		);
		assertTrue(SlayerBankRouteCatalog.getPreferredLocalApproachTarget(
			new WorldPoint(3164, 3487, 0)
		) == null);
	}

	@Test
	public void shardPreferenceOverridesEfficiencyOnlyAtValidShardLocations()
	{
		assertEquals(0, SlayerRecommendationEngine.shardPreferenceBonusForRegression(
			SlayerPreference.Shard.NO_PREFERENCE, "Bloodvelds", "Catacombs of Kourend"
		));
		assertEquals(1000, SlayerRecommendationEngine.shardPreferenceBonusForRegression(
			SlayerPreference.Shard.ANCIENT_SHARD, "Bloodvelds", "Catacombs of Kourend"
		));
		assertEquals(1000, SlayerRecommendationEngine.shardPreferenceBonusForRegression(
			SlayerPreference.Shard.CRYSTAL_SHARD, "Bloodvelds", "Iorwerth Dungeon"
		));
		assertEquals(1000, SlayerRecommendationEngine.shardPreferenceBonusForRegression(
			SlayerPreference.Shard.ANCIENT_AND_CRYSTAL_SHARD, "Nechryaels", "Catacombs of Kourend"
		));
		assertEquals(1000, SlayerRecommendationEngine.shardPreferenceBonusForRegression(
			SlayerPreference.Shard.ANCIENT_AND_CRYSTAL_SHARD, "Nechryaels", "Iorwerth Dungeon"
		));
		assertEquals(0, SlayerRecommendationEngine.shardPreferenceBonusForRegression(
			SlayerPreference.Shard.ANCIENT_SHARD, "Ghosts", "Catacombs of Kourend"
		));
		assertEquals(0, SlayerRecommendationEngine.shardPreferenceBonusForRegression(
			SlayerPreference.Shard.CRYSTAL_SHARD, "Bloodvelds", "Stronghold Slayer Cave"
		));
	}

	@Test
	public void iorwerthNechryaelsUseReviewedMeleeInsteadOfCatacombsBarrage()
	{
		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			"Nechryaels", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.ALLOW,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Iorwerth Dungeon", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MELEE, strategy.getCombatStyle());
		assertTrue(strategy.getMethod().contains("crystal shard"));
		assertFalse(strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE));
	}

	@Test
	public void runePouchBossUtilitiesSelectTheirActualSpellbooks()
	{
		assertEquals(
			SlayerMethodRules.Spellbook.ANCIENT,
			bossInventoryRules("Scorpia").getSpellbook()
		);
		assertEquals(
			SlayerMethodRules.Spellbook.ANCIENT,
			bossInventoryRules("Leviathan").getSpellbook()
		);
		assertEquals(
			SlayerMethodRules.Spellbook.STANDARD,
			bossInventoryRules("Royal Titans").getSpellbook()
		);
		assertEquals(
			SlayerMethodRules.Spellbook.STANDARD,
			bossInventoryRules("Demonic Gorillas").getSpellbook()
		);
	}

	@Test
	public void prayerCostSavingsPreserveOffenceOutsideAllowedEconomySlots()
	{
		for (final SlayerTaskStrategy.CombatStyle style
			: Arrays.asList(
				SlayerTaskStrategy.CombatStyle.MELEE,
				SlayerTaskStrategy.CombatStyle.RANGED,
				SlayerTaskStrategy.CombatStyle.MAGIC
			))
		{
			final SlayerTaskStrategy strategy = SlayerTaskStrategy.builder(
				style, "Regression prayer-cost profile"
			)
				.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
				.build();
			final List<String> cape = SlayerEquipmentAuditCatalog.priorities(
				"Bloodveld", strategy, EquipmentInventorySlot.CAPE, ""
			);
			final List<String> body = SlayerEquipmentAuditCatalog.priorities(
				"Bloodveld", strategy, EquipmentInventorySlot.BODY, ""
			);
			final List<String> legs = SlayerEquipmentAuditCatalog.priorities(
				"Bloodveld", strategy, EquipmentInventorySlot.LEGS, ""
			);
			final List<String> boots = SlayerEquipmentAuditCatalog.priorities(
				"Bloodveld", strategy, EquipmentInventorySlot.BOOTS, ""
			);
			final List<String> ring = SlayerEquipmentAuditCatalog.priorities(
				"Bloodveld", strategy, EquipmentInventorySlot.RING, ""
			);

			assertFalse(cape.isEmpty());
			assertFalse(body.isEmpty());
			assertFalse(legs.isEmpty());
			assertFalse(boots.isEmpty());
			assertFalse(ring.isEmpty());
			assertTrue(body.get(0).contains("body")
				|| body.get(0).contains("hauberk")
				|| body.get(0).contains("cuirass"));
			assertTrue(legs.get(0).contains("chaps")
				|| legs.get(0).contains("cuisse")
				|| legs.get(0).contains("chausses"));
		}

		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE, "Regression melee profile"
		).armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER).build();
		assertEquals("infernal cape", SlayerEquipmentAuditCatalog.priorities(
			"Bloodveld", melee, EquipmentInventorySlot.CAPE, ""
		).get(0));
		assertEquals("avernic treads max", SlayerEquipmentAuditCatalog.priorities(
			"Bloodveld", melee, EquipmentInventorySlot.BOOTS, ""
		).get(0));
		assertEquals("ring of the gods i", SlayerEquipmentAuditCatalog.priorities(
			"Bloodveld", melee, EquipmentInventorySlot.RING, ""
		).get(0));
	}

	@Test
	public void taskBraceletsRemainInTheGloveSlotGlobally()
	{
		final SlayerLoadoutItem slaughter = new SlayerLoadoutItem(
			"Bracelet of slaughter", 21183, 1,
			SlayerLoadoutItem.Status.BANK
		);
		final SlayerLoadoutItem expeditious = new SlayerLoadoutItem(
			"Expeditious bracelet", 21177, 1,
			SlayerLoadoutItem.Status.BANK
		);

		assertTrue(SlayerLoadoutAnalyzer.gloveFallbackMatchesSlotForRegression(
			"Bracelet of slaughter"
		));
		assertTrue(SlayerLoadoutAnalyzer.gloveFallbackMatchesSlotForRegression(
			"Expeditious bracelet"
		));
		assertTrue(
			SlayerBankTagLayoutService.optionalItemDuplicatesEquipmentForRegression(
				slaughter,
				Collections.singletonList(slaughter)
			)
		);
		assertFalse(
			SlayerBankTagLayoutService.optionalItemDuplicatesEquipmentForRegression(
				expeditious,
				Collections.singletonList(slaughter)
			)
		);
	}

	@Test
	public void settingsChangesRefreshOnlyAnExistingBankTag()
	{
		assertFalse(SlayerPlusPlugin.shouldRefreshExistingBankTag(false, false));
		assertTrue(SlayerPlusPlugin.shouldRefreshExistingBankTag(true, false));
		assertTrue(SlayerPlusPlugin.shouldRefreshExistingBankTag(false, true));
	}

	@Test
	public void bankRouteRebasesOnlyAfterTeleportScaleMovement()
	{
		final WorldPoint master = new WorldPoint(2869, 2982, 0);
		assertFalse(SlayerPlusPlugin.shouldRebaseBankRoute(
			master, new WorldPoint(2875, 2988, 0)
		));
		assertTrue(SlayerPlusPlugin.shouldRebaseBankRoute(
			master, new WorldPoint(3164, 3487, 0)
		));
		assertTrue(SlayerPlusPlugin.shouldRebaseBankRoute(
			master, new WorldPoint(2869, 2982, 1)
		));
	}

	@Test
	public void rebasedBankRouteSendsTheTeleportLandingAsItsStart()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint landing = new WorldPoint(3164, 3487, 0);
		final WorldPoint bank = new WorldPoint(3162, 3489, 0);

		assertTrue(bridge.routeToAny(
			landing,
			Collections.singleton(bank),
			true,
			false,
			false
		));

		final PluginMessage message = posted.get();
		assertNotNull(message);
		assertEquals(landing, message.getData().get("start"));
		assertEquals(
			Collections.singleton(bank),
			message.getData().get("target")
		);
	}

	@Test
	public void auditedBossAccessRoutesUseTheirOwnEntrances()
	{
		assertEquals(
			new WorldPoint(3176, 2477, 0),
			SlayerRouteCatalog.resolve(
				"Shellbane Gryphon", "The Great Conch", true
			).getSurfaceAccess()
		);
		assertEquals(
			new WorldPoint(3294, 3749, 0),
			SlayerRouteCatalog.resolve(
				"Spindel", "Web Chasm", true
			).getSurfaceAccess()
		);
		assertEquals(
			new WorldPoint(3152, 3644, 0),
			SlayerRouteCatalog.resolve(
				"Calvar'ion", "Skeletal Tomb", true
			).getSurfaceAccess()
		);
		assertEquals(
			new WorldPoint(2439, 5172, 0),
			SlayerRouteCatalog.resolve(
				"TzTok-Jad", "TzHaar Fight Cave", true
			).getSurfaceAccess()
		);
	}

	@Test
	public void everyReviewedBloodveldLocationUsesItsOwnVerifiedRoute()
	{
		assertBloodveldRoute(
			"Meiyerditch Laboratories",
			new WorldPoint(3594, 9743, 0),
			null,
			"Mutated bloodveld"
		);
		assertEquals(
			new WorldPoint(3594, 9743, 0),
			SlayerRouteCatalog.findCannonPosition(
				"Bloodveld", "Meiyerditch Laboratories"
			)
		);
		assertBloodveldRoute(
			"Iorwerth Dungeon",
			new WorldPoint(3236, 12438, 0),
			new WorldPoint(3225, 6045, 0),
			"Mutated bloodveld"
		);
		assertBloodveldRoute(
			"Catacombs of Kourend",
			new WorldPoint(1681, 10073, 0),
			new WorldPoint(1639, 3673, 0),
			"Mutated bloodveld"
		);
		assertBloodveldRoute(
			"Stronghold Slayer Cave",
			new WorldPoint(2465, 9833, 0),
			new WorldPoint(2432, 3423, 0),
			"Bloodveld"
		);
		assertBloodveldRoute(
			"Slayer Tower",
			new WorldPoint(3417, 3567, 1),
			null,
			"Bloodveld"
		);
		assertBloodveldRoute(
			"Slayer Tower (first floor)",
			new WorldPoint(3417, 3567, 1),
			null,
			"Bloodveld"
		);
		assertBloodveldRoute(
			"Slayer Tower basement",
			new WorldPoint(3403, 9947, 3),
			new WorldPoint(3428, 3536, 0),
			"Bloodveld"
		);
		assertBloodveldRoute(
			"God Wars Dungeon",
			new WorldPoint(2888, 5324, 2),
			new WorldPoint(2916, 3746, 0),
			"Bloodveld"
		);
		assertBloodveldRoute(
			"Wilderness God Wars Dungeon",
			new WorldPoint(3017, 3740, 0),
			new WorldPoint(3017, 3740, 0),
			"Bloodveld"
		);
		assertBloodveldRoute(
			"Buccaneers' Laboratory",
			new WorldPoint(2096, 10099, 0),
			new WorldPoint(2075, 3688, 0),
			"Mutated bloodveld"
		);

		assertEquals(new WorldPoint(2096, 10099, 0),
			SlayerRouteCatalog.findCannonPosition(
				"Bloodveld", "Buccaneers' Laboratory"));
		assertEquals(new WorldPoint(3236, 12438, 0),
			SlayerRouteCatalog.findCannonPosition(
				"Bloodveld", "Iorwerth Dungeon"));
		assertEquals(new WorldPoint(2465, 9833, 0),
			SlayerRouteCatalog.findCannonPosition(
				"Bloodveld", "Stronghold Slayer Cave"));
	}

	@Test
	public void qualifiedBloodveldRouteStagesAtTheMissingMeiyerditchShortcut()
	{
		assertTrue(SlayerPlusPlugin.shouldUseMeiyerditchShortcutForRegression(
			"Bloodveld",
			"Meiyerditch Laboratories",
			new WorldPoint(3200, 3200, 0),
			93
		));
		assertTrue(SlayerPlusPlugin.shouldUseMeiyerditchShortcutForRegression(
			"Bloodvelds",
			"Meiyerditch Laboratories",
			new WorldPoint(3500, 9803, 0),
			99
		));
		assertTrue(!SlayerPlusPlugin.shouldUseMeiyerditchShortcutForRegression(
			"Bloodveld",
			"Meiyerditch Laboratories",
			new WorldPoint(3200, 3200, 0),
			92
		));
		assertTrue(!SlayerPlusPlugin.shouldUseMeiyerditchShortcutForRegression(
			"Bloodveld",
			"Meiyerditch Laboratories",
			new WorldPoint(3535, 9768, 0),
			99
		));
	}

	@Test
	public void undergroundTaskRoutesAvoidMisalignedWorldMapPanels()
	{
		assertTrue(SlayerPlusPlugin.shouldUseLocalTaskRouteForRegression(
			new WorldPoint(3492, 9824, 0),
			new WorldPoint(3500, 9803, 0)
		));
		assertTrue(SlayerPlusPlugin.shouldUseLocalTaskRouteForRegression(
			new WorldPoint(3535, 9768, 0),
			new WorldPoint(3594, 9743, 0)
		));
		assertTrue(!SlayerPlusPlugin.shouldUseLocalTaskRouteForRegression(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3500, 9803, 0)
		));

		assertTrue(SlayerPlusPlugin.shouldRetireTravelRecommendationForLocalRoute(
			true, true, false
		));
		assertTrue(SlayerPlusPlugin.shouldRetireTravelRecommendationForLocalRoute(
			true, false, true
		));
		assertTrue(!SlayerPlusPlugin.shouldRetireTravelRecommendationForLocalRoute(
			false, true, true
		));
		assertTrue(!SlayerPlusPlugin.shouldRetireTravelRecommendationForLocalRoute(
			true, false, false
		));
	}

	@Test
	public void kalphiteQueenUpperLairRoutesToTheDeeperRope()
	{
		final WorldPoint deeperRope = new WorldPoint(3508, 9498, 2);
		assertEquals(
			deeperRope,
			SlayerPlusPlugin.kalphiteQueenInteriorTransitionForRegression(
				"The Kalphite Queen",
				"Kalphite Lair",
				new WorldPoint(3483, 9510, 2)
			)
		);
		assertEquals(
			deeperRope,
			SlayerPlusPlugin.kalphiteQueenInteriorTransitionForRegression(
				"Kalphite Queen",
				"Kalphite Lair",
				new WorldPoint(3510, 9499, 2)
			)
		);
		assertEquals(null, SlayerPlusPlugin.kalphiteQueenInteriorTransitionForRegression(
			"The Kalphite Queen",
			"Kalphite Lair",
			new WorldPoint(3508, 9493, 0)
		));
		assertEquals(null, SlayerPlusPlugin.kalphiteQueenInteriorTransitionForRegression(
			"Kalphites",
			"Kalphite Slayer Cave",
			new WorldPoint(3483, 9510, 2)
		));

		assertFalse(SlayerPlusPlugin.shouldEnableAgilityShortcutsForStageForRegression(
			"The Kalphite Queen", "Kalphite Lair", deeperRope, 99, false
		));
		assertFalse(SlayerPlusPlugin.shouldEnableAgilityShortcutsForStageForRegression(
			"The Kalphite Queen", "Kalphite Lair", deeperRope, 85, true
		));
		assertTrue(SlayerPlusPlugin.shouldEnableAgilityShortcutsForStageForRegression(
			"The Kalphite Queen", "Kalphite Lair", deeperRope, 86, true
		));
		assertTrue(SlayerPlusPlugin.shouldEnableAgilityShortcutsForStageForRegression(
			"Bloodveld", "Meiyerditch Laboratories", deeperRope, 99, false
		));

		assertTrue(SlayerLoadoutAnalyzer.requiresKalphiteQueenRopesForRegression(
			"The Kalphite Queen", "Kalphite Lair", false
		));
		assertFalse(SlayerLoadoutAnalyzer.requiresKalphiteQueenRopesForRegression(
			"The Kalphite Queen", "Kalphite Lair", true
		));
	}

	@Test
	public void kalphiteQueenShortcutIsDisabledWithoutDesertEliteDiary()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3483, 9510, 2);
		final WorldPoint deeperRope = new WorldPoint(3508, 9498, 2);

		assertTrue(bridge.routeToLocalTaskArea(
			start, Collections.singleton(deeperRope), true, false
		));
		final PluginMessage message = posted.get();
		assertNotNull(message);
		final Map<?, ?> config = (Map<?, ?>) message.getData().get("config");
		assertEquals(Boolean.FALSE, config.get("useAgilityShortcuts"));
		assertEquals(Boolean.FALSE, config.get("drawMap"));
	}

	@Test
	public void reviewedBloodveldSetupTilesAreNotWikiNpcSpawnTiles()
	{
		assertTrue(!Arrays.asList(
			new WorldPoint(3230, 12434, 0),
			new WorldPoint(3234, 12435, 0),
			new WorldPoint(3237, 12443, 0),
			new WorldPoint(3239, 12433, 0),
			new WorldPoint(3239, 12438, 0),
			new WorldPoint(3242, 12441, 0)
		).contains(SlayerRouteCatalog.findCannonPosition(
			"Bloodveld", "Iorwerth Dungeon")));

		assertTrue(!Arrays.asList(
			new WorldPoint(2434, 9817, 0),
			new WorldPoint(2434, 9824, 0),
			new WorldPoint(2440, 9821, 0),
			new WorldPoint(2447, 9822, 0),
			new WorldPoint(2451, 9817, 0),
			new WorldPoint(2453, 9822, 0),
			new WorldPoint(2465, 9830, 0),
			new WorldPoint(2470, 9834, 0),
			new WorldPoint(2472, 9830, 0),
			new WorldPoint(2485, 9822, 0),
			new WorldPoint(2488, 9827, 0),
			new WorldPoint(2489, 9818, 0)
		).contains(SlayerRouteCatalog.findCannonPosition(
			"Bloodveld", "Stronghold Slayer Cave")));

		assertTrue(!Arrays.asList(
			new WorldPoint(2089, 10093, 0),
			new WorldPoint(2091, 10101, 0),
			new WorldPoint(2092, 10096, 0),
			new WorldPoint(2096, 10094, 0),
			new WorldPoint(2096, 10100, 0),
			new WorldPoint(2097, 10089, 0),
			new WorldPoint(2097, 10104, 0),
			new WorldPoint(2101, 10099, 0)
		).contains(SlayerRouteCatalog.findCannonPosition(
			"Bloodveld", "Buccaneers' Laboratory")));
	}

	private static void assertBloodveldRoute(
		final String location,
		final WorldPoint destination,
		final WorldPoint access,
		final String npcName)
	{
		final SlayerRouteCatalog.RouteProfile profile = SlayerRouteCatalog.resolve(
			"Bloodveld", location, false
		);
		assertNotNull(location, profile);
		assertEquals(location, destination, profile.getPrimaryDestination());
		assertEquals(location, access, profile.getSurfaceAccess());
		assertTrue(location, profile.getNpcNames().contains(
			npcName.toLowerCase(java.util.Locale.ENGLISH)
		));
	}

	@Test
	public void routeDiscoveryRetriesAreThrottledButProfileChangesAreImmediate()
	{
		assertTrue(
			SlayerRecommendationEngine.catalogLocationsForValidation("Bloodveld")
				.contains("Meiyerditch Laboratories")
		);
		assertTrue(
			SlayerRecommendationEngine.catalogLocationsForValidation("Blue dragons")
				.contains("Taverley Dungeon")
		);
		assertTrue(SlayerPlusPlugin.shouldDiscoverNearbyRouteEntity(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3250, 3250, 0),
			96
		));
		assertFalse(SlayerPlusPlugin.shouldDiscoverNearbyRouteEntity(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3400, 3400, 0),
			96
		));
		assertFalse(SlayerPlusPlugin.shouldDiscoverNearbyRouteEntity(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3200, 3200, 1),
			96
		));
		assertTrue(SlayerPlusPlugin.routeDiscoveryDue(
			100L, Long.MIN_VALUE, "bloodveld|meiyerditch", ""
		));
		assertFalse(SlayerPlusPlugin.routeDiscoveryDue(
			101L, 100L, "bloodveld|meiyerditch", "bloodveld|meiyerditch"
		));
		assertTrue(SlayerPlusPlugin.routeDiscoveryDue(
			103L, 100L, "bloodveld|meiyerditch", "bloodveld|meiyerditch"
		));
		assertTrue(SlayerPlusPlugin.routeDiscoveryDue(
			101L, 100L, "vorkath|vorkath", "bloodveld|meiyerditch"
		));
	}

	@Test
	public void portraitEquipmentMaskUsesTheActualCaptureStride()
	{
		final boolean[] source = new boolean[6 * 6];
		source[4 * 6 + 4] = true;
		final boolean[] expanded = PlayerPortraitRenderer.expandMask(source, 6);

		assertEquals(36, expanded.length);
		assertTrue(expanded[3 * 6 + 3]);
		assertTrue(expanded[4 * 6 + 4]);
		assertTrue(expanded[5 * 6 + 5]);
		assertFalse(expanded[2 * 6 + 2]);
	}

	@Test
	public void portraitFinalPassUsesCompleteThreeTimesSupersampling()
	{
		assertEquals(87, PlayerPortraitRenderer.supersampledZoom(260));
		assertEquals(
			168,
			PlayerPortraitRenderer.supersampledVerticalOffset(
				100,
				260,
				90.0,
				512
			)
		);
		assertEquals(
			3,
			PlayerPortraitRenderer.supersampledCaptureMarginOffset(
				260,
				512
			)
		);
		assertEquals(
			740,
			PlayerPortraitRenderer.lockedScaleSourceHeight(800, 3)
		);
		assertEquals(
			20,
			PlayerPortraitRenderer.lockedOutputTargetY(120)
		);
		assertEquals(
			352.0,
			PlayerPortraitRenderer.projectCalibrationCoordinate(
				224.0,
				704,
				3
			),
			0.001
		);
		assertEquals(
			352.0,
			PlayerPortraitRenderer.projectCalibrationCoordinate(
				90.0,
				90.0,
				704,
				3
			),
			0.001
		);
		assertEquals(
			124.0,
			PlayerPortraitRenderer.projectCalibrationCoordinate(
				14.0,
				90.0,
				704,
				3
			),
			0.001
		);
		assertEquals(
			370.0,
			PlayerPortraitRenderer.projectCalibrationCoordinate(
				230.0,
				704,
				3
			),
			0.001
		);

		final java.awt.image.BufferedImage empty =
			new java.awt.image.BufferedImage(
				116,
				116,
				java.awt.image.BufferedImage.TYPE_INT_ARGB
			);
		assertFalse(PlayerPortraitRenderer.hasSufficientVisiblePixels(empty));
		for (int y = 20; y < 100; y++)
		{
			for (int x = 30; x < 90; x++)
			{
				empty.setRGB(x, y, 0xFFFFFFFF);
			}
		}
		assertTrue(PlayerPortraitRenderer.hasSufficientVisiblePixels(empty));

		final java.awt.image.BufferedImage clippedStrip =
			new java.awt.image.BufferedImage(
				116,
				116,
				java.awt.image.BufferedImage.TYPE_INT_ARGB
			);
		for (int y = 94; y < 116; y++)
		{
			for (int x = 0; x < 116; x++)
			{
				clippedStrip.setRGB(x, y, 0xFFFFFFFF);
			}
		}
		assertFalse(PlayerPortraitRenderer.hasSufficientVisiblePixels(
			clippedStrip
		));
	}

	@Test
	public void portraitDepthLightingIsDirectionalAndGeometryIndependent()
	{
		final int[] bounds = {10, 20, 110, 120};
		final double upperLeft = PlayerPortraitRenderer
			.portraitDirectionalLight(10, 20, bounds);
		final double center = PlayerPortraitRenderer
			.portraitDirectionalLight(60, 70, bounds);
		final double lowerRight = PlayerPortraitRenderer
			.portraitDirectionalLight(110, 120, bounds);

		assertTrue(upperLeft > center);
		assertEquals(0.0, center, 0.001);
		assertTrue(center > lowerRight);
	}

	@Test
	public void potionPolicyPrefersUsefulExtendedUptimeAndPreservesPlusTiers()
	{
		assertEquals(
			"extended stamina potion",
			SlayerPotionPolicy.staminaAlternatives()[0]
		);
		assertEquals(
			"goading potion",
			SlayerPotionPolicy.goadingAlternatives()[0]
		);
		assertEquals(
			6,
			SlayerPotionPolicy.effectiveDoseUnits(
				"Extended stamina potion",
				3
			)
		);
		assertEquals(
			4,
			SlayerPotionPolicy.effectiveDoseUnits("Stamina potion", 4)
		);
		assertEquals(
			8,
			SlayerPotionPolicy.effectiveDoseUnits(
				"Extended anti-venom+",
				4
			)
		);
		assertEquals(
			"divine bastion potion",
			SlayerPotionPolicy.rangedBoostAlternatives(
				SlayerTaskStrategy.CostPolicy.MAX_DPS
			)[0]
		);
		assertEquals(
			"ranging potion",
			SlayerPotionPolicy.rangedBoostAlternatives(
				SlayerTaskStrategy.CostPolicy.EFFICIENT
			)[0]
		);
		assertEquals(
			"saturated heart",
			SlayerPotionPolicy.magicBoostAlternatives()[0]
		);
		assertEquals(
			"Extended stamina potion(3)",
			SlayerLoadoutAnalyzer.preferredInventoryItemForRegression(
				Arrays.asList(
					"Stamina potion(4)",
					"Extended stamina potion(3)"
				),
				SlayerPotionPolicy.staminaAlternatives()
			)
		);
		assertEquals(
			"Saturated heart",
			SlayerLoadoutAnalyzer.preferredInventoryItemForRegression(
				Arrays.asList("Magic potion(4)", "Saturated heart"),
				SlayerPotionPolicy.magicBoostAlternatives()
			)
		);
		assertEquals(
			"anti venom plus 4",
			SlayerLoadoutAnalyzer.normalizePotionDisplayNameForRegression(
				"Anti-venom+(4)"
			)
		);
		assertEquals(
			"antidote plus plus 4",
			SlayerLoadoutAnalyzer.normalizePotionDisplayNameForRegression(
				"Antidote++(4)"
			)
		);
		assertEquals(
			"extended super antifire",
			SlayerPotionPolicy.potionOnlyAntifireAlternatives()[0]
		);
		assertTrue(Arrays.stream(
			SlayerPotionPolicy.potionOnlyAntifireAlternatives()
		).noneMatch(name -> name.equals("extended antifire")));
	}

	@Test
	public void researchedSafespotBaselineReplacesUnsafeGenericMeleeTasks()
	{
		for (final String task : Arrays.asList(
			"Catablepon", "Cave bugs", "Crocodiles", "Fleshcrawlers",
			"Ghouls", "Minotaurs",
			"Shadow warriors", "Terror dogs"
		))
		{
			final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				task,
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.NEVER,
				SlayerPreference.Burst.NEVER,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Not restricted",
				false
			);
			assertEquals(task,
				SlayerTaskStrategy.CombatStyle.RANGED,
				strategy.getCombatStyle());
			assertTrue(task, strategy.getMethod().toLowerCase().contains("safespot"));
		}

		final SlayerTaskStrategy otherworldly = SlayerTaskStrategyCatalog.resolve(
			"Otherworldly beings",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Not restricted",
			false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MAGIC, otherworldly.getCombatStyle());
		assertTrue(otherworldly.getMethod().contains("35% Air weakness"));
	}

	@Test
	public void blueDragonsAndVorkathHaveReviewedTravelMethods()
	{
		final List<SlayerTravelRouteCatalog.Option> taverley =
			SlayerTravelRouteCatalog.fallbacksFor("Taverley Dungeon");
		assertEquals("taverley teleport", taverley.get(0).getItemFamily());
		assertEquals("Taverley", taverley.get(0).getDestination());
		assertEquals("falador teleport", taverley.get(1).getItemFamily());

		final List<SlayerTravelRouteCatalog.Option> ungael =
			SlayerTravelRouteCatalog.fallbacksFor("Ungael");
		assertEquals("fremennik sea boots 4", ungael.get(0).getItemFamily());
		assertEquals("Rellekka", ungael.get(0).getDestination());
		assertEquals("fremennik sea boots 3", ungael.get(1).getItemFamily());
		assertEquals("fremennik sea boots 2", ungael.get(2).getItemFamily());
		assertEquals("fremennik sea boots 1", ungael.get(3).getItemFamily());
		assertEquals("enchanted lyre i", ungael.get(4).getItemFamily());

		final SlayerRouteCatalog.RouteProfile vorkath =
			SlayerRouteCatalog.resolve("Vorkath", "Ungael", true);
		assertNotNull(vorkath);
		assertTrue(vorkath.isBoss());
		assertTrue(vorkath.isStaged());
		assertEquals(new WorldPoint(2640, 3696, 0), vorkath.getSurfaceAccess());
		assertEquals(new WorldPoint(2273, 4065, 0), vorkath.getPrimaryDestination());
	}

	@Test
	public void taverleyBlackDragonsUseTheirDedicatedUpperFloorTransportChain()
	{
		final SlayerRouteCatalog.RouteProfile blackDragons =
			SlayerRouteCatalog.resolve(
				"Black dragons", "Taverley Dungeon", false
			);

		assertNotNull(blackDragons);
		assertEquals(
			SlayerRouteCatalog.RouteMode.TRANSPORT_CHAIN_INTERIOR,
			blackDragons.getMode()
		);
		assertEquals(
			new WorldPoint(2903, 9813, 1),
			blackDragons.getPrimaryDestination()
		);
		assertNull(blackDragons.getSurfaceAccess());
		assertTrue(blackDragons.getNpcNames().contains("black dragon"));
		assertTrue(blackDragons.getNpcNames().contains("baby black dragon"));

		final SlayerRouteCoordinator.Stage fromSurface =
			SlayerRouteCoordinator.resolve(
				blackDragons,
				new WorldPoint(2884, 3395, 0),
				null, java.util.Collections.emptySet(),
				null, java.util.Collections.emptySet(),
				null, java.util.Collections.emptySet(),
				null, java.util.Collections.emptySet()
			);
		assertTrue(fromSurface.isValid());
		assertEquals(
			SlayerRouteCoordinator.StageKind.INTERIOR,
			fromSurface.getKind()
		);
		assertEquals(
			new WorldPoint(2903, 9813, 1),
			fromSurface.getDestination()
		);
	}

	@Test
	public void everySlayerMasterHasAReviewedReturnTeleportProgression()
	{
		for (int masterId = 1; masterId <= 10; masterId++)
		{
			final SlayerMasterRouteCatalog.MasterRoute route =
				SlayerMasterRouteCatalog.find(masterId);
			assertNotNull("Missing Slayer master " + masterId, route);
			assertFalse(
				"Missing return teleport for " + route.getName(),
				route.getReturnItemFamilies().isEmpty()
			);
		}
		assertEquals(
			"karamja gloves 4",
			SlayerMasterRouteCatalog.find(5).getReturnItemFamilies().get(0)
		);
		assertEquals(
			"Slayer Master",
			SlayerMasterRouteCatalog.find(5).getReturnDestination(
				"karamja gloves 4"
			)
		);
		assertEquals(
			"Gem Mine",
			SlayerMasterRouteCatalog.find(5).getReturnDestination(
				"karamja gloves 3"
			)
		);
		assertEquals(
			"rada s blessing 4",
			SlayerMasterRouteCatalog.find(8).getReturnItemFamilies().get(0)
		);
		assertEquals(
			"Mount Karuulm",
			SlayerMasterRouteCatalog.find(8).getReturnDestination(
				"rada s blessing 4"
			)
		);
		assertEquals("Mortimer", SlayerMasterRouteCatalog.getName(10));
		assertEquals(
			new WorldPoint(2589, 8614, 0),
			SlayerMasterRouteCatalog.find(10).getDestination()
		);
		assertEquals(
			"Wyrmscraig Cavern",
			SlayerMasterRouteCatalog.find(10).getReturnDestination(
				"eternal slayer ring"
			)
		);
	}

	@Test
	public void openingBankDuringActiveTaskAlwaysResumesBankAwareTaskRoute()
	{
		assertTrue(SlayerPlusPlugin.shouldResumeTaskAfterBankForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.TASKING, 25
		));
		assertTrue(SlayerPlusPlugin.shouldResumeTaskAfterBankForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_TASK, 25
		));
		assertFalse(SlayerPlusPlugin.shouldResumeTaskAfterBankForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_MASTER, 0
		));
		assertFalse(SlayerPlusPlugin.shouldResumeTaskAfterBankForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.TASKING, 0
		));
		assertTrue(SlayerPlusPlugin.shouldContinueMasterAfterBankPickupForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_MASTER, true
		));
		assertFalse(SlayerPlusPlugin.shouldContinueMasterAfterBankPickupForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_MASTER, false
		));
		assertFalse(SlayerPlusPlugin.shouldContinueMasterAfterBankPickupForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_TASK, true
		));
		assertTrue(SlayerPlusPlugin.shouldResumeTaskAfterBankCloseForRegression(
			true, SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_BANK, 25
		));
		assertTrue(SlayerPlusPlugin.shouldResumeTaskAfterBankCloseForRegression(
			true, SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_TASK, 25
		));
		assertTrue(SlayerPlusPlugin.shouldResumeTaskAfterBankCloseForRegression(
			true, SlayerPlusPlugin.GuidedSessionPhase.TASKING, 25
		));
		assertFalse(SlayerPlusPlugin.shouldResumeTaskAfterBankCloseForRegression(
			false, SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_TASK, 25
		));
		assertFalse(SlayerPlusPlugin.shouldResumeTaskAfterBankCloseForRegression(
			true, SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_MASTER, 0
		));
	}

	@Test
	public void routingUsesResolvedTaskSnapshotAcrossLoginAndCompletionBoundaries()
	{
		assertEquals(57, SlayerPlusPlugin.effectiveTaskRemainingForRegression(
			false, "", 57, 0
		));
		assertEquals(0, SlayerPlusPlugin.effectiveTaskRemainingForRegression(
			false, "", 0, 57
		));
		assertEquals(57, SlayerPlusPlugin.effectiveTaskRemainingForRegression(
			false, "", -1, 57
		));
		assertEquals(100, SlayerPlusPlugin.effectiveTaskRemainingForRegression(
			true, "Ankou", 0, 0
		));
		assertTrue(SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(
			GameState.HOPPING
		));
		assertTrue(SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(
			GameState.CONNECTION_LOST
		));
		assertTrue(SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(
			GameState.LOGGING_IN
		));
		assertFalse(SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(
			GameState.LOADING
		));
		assertFalse(SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(
			GameState.LOGGED_IN
		));
	}

	@Test
	public void delayedTaskCountsAndBankOnlyEncounterTeleportsCannotBreakRouting()
	{
		assertTrue(SlayerPlusPlugin.shouldConfirmTaskAreaAfterCountDecreaseForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.TASKING
		));
		assertFalse(SlayerPlusPlugin.shouldConfirmTaskAreaAfterCountDecreaseForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_BANK
		));
		assertFalse(SlayerPlusPlugin.shouldConfirmTaskAreaAfterCountDecreaseForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_TASK
		));
		assertTrue(SlayerPlusPlugin.mayUseEncounterTravelMatch(true, false));
		assertTrue(SlayerPlusPlugin.mayUseEncounterTravelMatch(false, true));
		assertFalse(SlayerPlusPlugin.mayUseEncounterTravelMatch(false, false));
	}

	@Test
	public void movementInsideReviewedEncounterDoesNotLookLikeBankDetour()
	{
		final SlayerRouteCatalog.RouteProfile profile = SlayerRouteCatalog.resolve(
			"Black dragons", "Taverley Dungeon", false
		);
		assertNotNull(profile);
		final WorldPoint destination = profile.getPrimaryDestination();
		assertNotNull(destination);
		final WorldPoint distantInside = new WorldPoint(
			destination.getX() + 100,
			destination.getY(),
			destination.getPlane()
		);
		assertTrue(profile.isInsideEncounterArea(distantInside));
		assertFalse(SlayerPlusPlugin.shouldCountTaskAreaExitForRegression(
			distantInside, destination, profile
		));

		final WorldPoint outside = new WorldPoint(
			destination.getX() + 250,
			destination.getY(),
			destination.getPlane()
		);
		assertFalse(profile.isInsideEncounterArea(outside));
		assertTrue(SlayerPlusPlugin.shouldCountTaskAreaExitForRegression(
			outside, destination, profile
		));
	}

	@Test
	public void directTaskTeleportOutranksGenericBankCape()
	{
		assertTrue(
			SlayerTravelRouteCatalog.locksFirstResolvedTravelItem(
				"Morytania Spider Cave"
			)
		);
		assertTrue(
			SlayerTravelRouteCatalog.locksAuthoredTravelItem(
				"Morytania Spider Cave"
			)
		);
		assertTrue(
			SlayerTravelRouteCatalog.liveCandidatePreference(
				"Morytania Spider Cave", "Spider cave teleport"
			) > SlayerTravelRouteCatalog.liveCandidatePreference(
				"Morytania Spider Cave", "Max cape"
			)
		);
		assertTrue(
			SlayerTravelRouteCatalog.liveCandidatePreference(
				"Morytania Spider Cave", "Spider cave teleport"
			) > SlayerTravelRouteCatalog.liveCandidatePreference(
				"Morytania Spider Cave", "Drakan's medallion"
			)
		);
		assertTrue(
			SlayerPlusPlugin.shouldPreferDirectConsumableTeleportForRegression(
				"Morytania Spider Cave", "Spider cave teleport"
			)
		);
		assertFalse(
			SlayerPlusPlugin.shouldPreferDirectConsumableTeleportForRegression(
				"Morytania Spider Cave", "Max cape"
			)
		);
		final WorldPoint caveEntrance = new WorldPoint(3657, 3407, 0);
		assertTrue(SlayerPlusPlugin.isAraxxorSpiderTeleportLandingForRegression(
			"Araxxor",
			"Morytania Spider Cave",
			new WorldPoint(3658, 3408, 0),
			caveEntrance,
			false
		));
		assertTrue(SlayerPlusPlugin.isAraxxorSpiderTeleportLandingForRegression(
			"Araxxor",
			"Morytania Spider Cave",
			new WorldPoint(3707, 3407, 0),
			caveEntrance,
			false
		));
		assertTrue(SlayerPlusPlugin.isAraxxorSpiderTeleportLandingForRegression(
			"Araxxor",
			"Morytania Spider Cave",
			new WorldPoint(3700, 9800, 0),
			caveEntrance,
			true
		));
		assertFalse(SlayerPlusPlugin.isAraxxorSpiderTeleportLandingForRegression(
			"Araxxor",
			"Morytania Spider Cave",
			new WorldPoint(3200, 3200, 0),
			caveEntrance,
			false
		));
	}

	@Test
	public void mortimerRingLandingAndMasterTileAreRecognizedAsOneCavern()
	{
		assertTrue(SlayerPlusPlugin.isMortimerCavernForRegression(
			new WorldPoint(2581, 8633, 0)
		));
		assertTrue(SlayerPlusPlugin.isMortimerCavernForRegression(
			SlayerMasterRouteCatalog.find(10).getDestination()
		));
		assertFalse(SlayerPlusPlugin.isMortimerCavernForRegression(
			new WorldPoint(2581, 8633, 1)
		));
		assertFalse(SlayerPlusPlugin.isMortimerCavernForRegression(
			new WorldPoint(2445, 4431, 0)
		));
	}

	@Test
	public void karamjaGlovesUseTheirLiveMasterReturnMenuLabels()
	{
		assertEquals(
			"Slayer Master",
			SlayerTeleportRouteRegistry.menuDestination(
				"Karamja gloves 4", "Duradel"
			)
		);
		assertTrue(SlayerTeleportRouteRegistry.destinationAliases(
			"Karamja gloves 4", "Duradel"
		).contains("Slayer Master"));
		assertEquals(
			"Gem Mine",
			SlayerTeleportRouteRegistry.menuDestination(
				"Karamja gloves 3", "Shilo Village gem mine"
			)
		);
		assertEquals(
			"Mount Karuulm",
			SlayerTeleportRouteRegistry.menuDestination(
				"Rada's blessing 4", "Konar"
			)
		);
		assertEquals(
			"Wyrmscraig Cavern",
			SlayerTeleportRouteRegistry.menuDestination(
				"Slayer ring (8)", "Mortimer"
			)
		);
		assertTrue(SlayerTeleportRouteRegistry.destinationAliases(
			"Slayer ring (eternal)", "Wyrmscraig Cavern"
		).contains("Mortimer"));
	}

	@Test
	public void achievementDiaryCapeUsesLiveRegionLabelsForEveryTaskmaster()
	{
		assertEquals("Desert", SlayerTeleportRouteRegistry.menuDestination(
			"Achievement diary cape", "Jarr"));
		assertEquals("Kourend & Kebos", SlayerTeleportRouteRegistry.menuDestination(
			"Achievement diary cape (t)", "Elise"));
		assertEquals("Morytania", SlayerTeleportRouteRegistry.menuDestination(
			"Achievement diary cape", "Le-sabrè"));
		assertEquals("Western Provinces", SlayerTeleportRouteRegistry.menuDestination(
			"Achievement diary cape", "Elder gnome child"));

		final Set<String> jarrAliases = SlayerTeleportRouteRegistry.destinationAliases(
			"Achievement diary cape", "Jarr");
		assertTrue(jarrAliases.contains("Desert"));
		final Set<String> normalizedJarrAliases =
			java.util.Collections.singleton("desert");
		assertTrue(SlayerTeleportHighlighter.isContextlessSelectedDestinationChoice(
			"Achievement diary cape", "Desert", normalizedJarrAliases));
		assertFalse(SlayerTeleportHighlighter.isContextlessSelectedDestinationChoice(
			"Achievement diary cape", "Fremennik", normalizedJarrAliases));
	}

	@Test
	public void achievementDiaryCapeDecoratedTransportNamesHighlightTheLeafRegion()
	{
		assertEquals("Desert", SlayerTeleportRouteRegistry.menuDestination(
			"Achievement diary cape", "Achievement diary cape: 2. Jarr"));
		assertEquals("Kourend & Kebos", SlayerTeleportRouteRegistry.menuDestination(
			"Achievement diary cape", "Achievement diary cape: 7. Elise"));
		assertTrue(SlayerTeleportRouteRegistry.destinationAliases(
			"Achievement diary cape", "Achievement diary cape: 2. Jarr")
			.contains("Desert"));
		assertTrue(SlayerTeleportHighlighter.isContextlessSelectedDestinationChoice(
			"Achievement diary cape", "Desert",
			java.util.Collections.singleton("desert")));
	}

	@Test
	public void liveZeroWinsTheTaskCompletionRaceAgainstStaleCaches()
	{
		assertEquals(0, SlayerPlusPlugin.resolveTaskRemainingForRegression(
			57, 57, 0, 57, -1));
		assertEquals(57, SlayerPlusPlugin.resolveTaskRemainingForRegression(
			-1, 57, 0, 57, -1));
		assertEquals(42, SlayerPlusPlugin.resolveTaskRemainingForRegression(
			57, 42, 42, 57, -1));
		assertFalse(SlayerPlusPlugin.shouldExposeTravelHighlightForRegression(
			true, true));
		assertTrue(SlayerPlusPlugin.shouldExposeTravelHighlightForRegression(
			true, false));
	}

	@Test
	public void pohSpellbookAltarsExposeOnlyTheirRealSwitches()
	{
		assertTrue(SlayerPlusPlugin.pohAltarSupportsSpellbookForRegression(
			net.runelite.api.gameval.ObjectID.POH_ALTAR_OCCULT,
			"Arceuus spellbook"));
		assertTrue(SlayerPlusPlugin.pohAltarSupportsSpellbookForRegression(
			net.runelite.api.gameval.ObjectID.POH_ALTAR_ANCIENT,
			"Ancient Magicks"));
		assertTrue(SlayerPlusPlugin.pohAltarSupportsSpellbookForRegression(
			net.runelite.api.gameval.ObjectID.POH_ALTAR_ANCIENT,
			"Standard spellbook"));
		assertFalse(SlayerPlusPlugin.pohAltarSupportsSpellbookForRegression(
			net.runelite.api.gameval.ObjectID.POH_ALTAR_ANCIENT,
			"Arceuus spellbook"));
		assertFalse(SlayerPlusPlugin.isPohSpellbookAltarIdForRegression(
			-1));
	}

	@Test
	public void masterReturnTeleportUsesBottomUtilityCellWithoutMovingRunePouch()
	{
		final List<SlayerLoadoutItem> inventory = new ArrayList<>();
		inventory.add(new SlayerLoadoutItem(
			"Task teleport", 900, 1, SlayerLoadoutItem.Status.BANK
		).withInventoryGroup(SlayerMethodRules.InventoryGroup.TRAVEL));
		for (int index = 1; index < 27; index++)
		{
			inventory.add(new SlayerLoadoutItem(
				"Food " + index,
				1000 + index,
				1,
				SlayerLoadoutItem.Status.BANK
			).withInventoryGroup(SlayerMethodRules.InventoryGroup.FOOD));
		}
		inventory.add(new SlayerLoadoutItem(
			"Divine rune pouch", 2000, 1, SlayerLoadoutItem.Status.BANK
		).withInventoryGroup(SlayerMethodRules.InventoryGroup.UTILITY));

		final SlayerLoadoutPlan enriched =
			SlayerPlusPlugin.appendMasterReturnTeleportForRegression(
				new SlayerLoadoutPlan(
					"Gear", "Inventory", "Owned", "Test",
					Collections.emptyList(), inventory, Collections.emptyList()
				),
				3000,
				"Karamja gloves 4"
			);
		assertEquals(28, enriched.getInventoryItems().size());
		assertTrue(enriched.getInventoryItems().stream().anyMatch(
			item -> item.getItemId() == 3000
		));

		final List<SlayerBankTagLayoutService.InventoryPlacement> placements =
			SlayerBankTagLayoutService.planInventoryForBankTag(
				enriched.getInventoryItems(), -1, true
			);
		assertEquals(25, slotFor(placements, "Karamja gloves 4"));
		assertEquals(26, slotFor(placements, "Task teleport"));
		assertEquals(27, slotFor(placements, "Divine rune pouch"));
	}

	@Test
	public void whispererUsesEveryReviewedRouteLeg()
	{
		final SlayerRouteCatalog.RouteProfile whisperer =
			SlayerRouteCatalog.resolve("Whisperer", "Lassar Undercity", true);
		assertNotNull(whisperer);
		assertTrue(whisperer.isBoss());
		assertTrue(whisperer.isStaged());
		assertEquals(
			new WorldPoint(3000, 3494, 0),
			whisperer.getSurfaceAccess()
		);
		assertEquals(
			new WorldPoint(2656, 6370, 0),
			whisperer.getPrimaryDestination()
		);
		assertTrue(whisperer.getNpcNames().contains("whisperer"));
		final SlayerRouteCatalog.RouteProfile titledWhisperer =
			SlayerRouteCatalog.resolve("The Whisperer", "Lassar Undercity", true);
		assertNotNull(titledWhisperer);
		assertEquals(whisperer.getSurfaceAccess(), titledWhisperer.getSurfaceAccess());
		assertEquals(
			whisperer.getPrimaryDestination(),
			titledWhisperer.getPrimaryDestination()
		);
		final SlayerTaskVariantCatalog.ResolvedTarget directAssignment =
			SlayerTaskVariantCatalog.resolve(
				"The Whisperer",
				SlayerTaskVariant.STANDARD_TASK,
				null,
				null
			);
		assertTrue(directAssignment.isValid());
		assertTrue(directAssignment.isBoss());
		assertEquals("The Whisperer", directAssignment.getTaskName());
		assertEquals("Lassar Undercity", directAssignment.getLocation());
		assertTrue(directAssignment.getStrategy().isReviewed());
		final List<SlayerTravelRouteCatalog.Option> travel =
			SlayerTravelRouteCatalog.fallbacksFor("Lassar Undercity");
		assertEquals("ring of shadows", travel.get(0).getItemFamily());
		assertEquals("Lassar Undercity", travel.get(0).getDestination());
		assertEquals("mind altar teleport", travel.get(1).getItemFamily());
		assertEquals("Mind Altar", travel.get(1).getDestination());
		assertTrue(SlayerTravelRouteCatalog.locksAuthoredTravelItem(
			"Lassar Undercity"
		));
		assertTrue(!SlayerTravelRouteCatalog.locksAuthoredTravelItem(
			"Taverley Dungeon"
		));
		final SlayerTravelSelection lassarRing =
			SlayerTravelSelection.resolvedItem(
				"whisperer|lassar-undercity",
				7L,
				ItemID.RING_OF_SHADOWS,
				"Ring of shadows",
				"Lassar Undercity",
				Collections.singleton("ring of shadows")
			);
		assertTrue(SlayerPlusPlugin.shouldRetainVerifiedTravelItem(
			"Lassar Undercity", lassarRing, true
		));
		assertTrue(!SlayerPlusPlugin.shouldRetainVerifiedTravelItem(
			"Lassar Undercity", lassarRing, false
		));
		assertTrue(SlayerPlusPlugin.bossNpcNameMatchesTaskForRegression(
			"The Whisperer", "The Whisperer"
		));
		assertTrue(SlayerPlusPlugin.bossNpcNameMatchesTaskForRegression(
			"Whisperer", "The Whisperer"
		));
		assertTrue(!SlayerPlusPlugin.bossNpcNameMatchesTaskForRegression(
			"The Whisperer", "Odd Figure"
		));
		assertTrue(SlayerPlusPlugin.shouldConfirmWhispererArenaForRegression(
			"The Whisperer", true
		));
		assertTrue(SlayerPlusPlugin.shouldConfirmWhispererArenaForRegression(
			"Whisperer", true
		));
		assertTrue(!SlayerPlusPlugin.shouldConfirmWhispererArenaForRegression(
			"The Whisperer", false
		));
		assertTrue(!SlayerPlusPlugin.shouldConfirmWhispererArenaForRegression(
			"Vardorvis", true
		));
		final WorldPoint lassarRingLanding = new WorldPoint(2588, 6435, 0);
		assertEquals(
			lassarRingLanding,
			SlayerPlusPlugin.whispererRingLandingForRegression()
		);
		assertEquals(
			new WorldPoint(2593, 6424, 0),
			SlayerPlusPlugin.whispererCathedralTeleporterApproachForRegression(
				"The Whisperer", "Lassar Undercity", lassarRingLanding
			)
		);
		assertEquals(
			null,
			SlayerPlusPlugin.whispererCathedralTeleporterApproachForRegression(
				"The Whisperer", "Lassar Undercity",
				new WorldPoint(2652, 6405, 0)
			)
		);
		assertTrue(
			SlayerPlusPlugin.isWhispererCathedralTeleporterLandingForRegression(
				"Whisperer", "Lassar Undercity",
				new WorldPoint(2652, 6405, 0)
			)
		);
		assertTrue(whisperer.isInsideEncounterArea(lassarRingLanding));
		assertTrue(SlayerPlusPlugin.isWhispererLassarInteriorForRegression(
			"The Whisperer", "Lassar Undercity", lassarRingLanding
		));
		assertTrue(!SlayerPlusPlugin.isWhispererLassarInteriorForRegression(
			"The Whisperer", "Lassar Undercity", new WorldPoint(3000, 3494, 0)
		));
		assertTrue(SlayerPlusPlugin.isWhispererRingTravelForRegression(
			"The Whisperer", "Lassar Undercity",
			"Ring of shadows", "Lassar Undercity"
		));
		assertTrue(!SlayerPlusPlugin.isWhispererRingTravelForRegression(
			"The Whisperer", "Lassar Undercity",
			"Max cape", "Mind Altar"
		));

		assertEquals(
			new WorldPoint(2922, 5827, 0),
			SlayerPlusPlugin.whispererIntermediateDestinationForRegression(
				"Whisperer",
				"Lassar Undercity",
				new WorldPoint(2978, 5798, 0)
			)
		);
		assertEquals(
			new WorldPoint(2922, 5827, 0),
			SlayerPlusPlugin.whispererIntermediateDestinationForRegression(
				"The Whisperer",
				"Lassar Undercity",
				new WorldPoint(2978, 5798, 0)
			)
		);
		assertEquals(
			null,
			SlayerPlusPlugin.whispererIntermediateDestinationForRegression(
				"Whisperer",
				"Lassar Undercity",
				new WorldPoint(3200, 3200, 0)
			)
		);
		assertEquals(
			null,
			SlayerPlusPlugin.whispererIntermediateDestinationForRegression(
				"Whisperer",
				"Lassar Undercity",
				new WorldPoint(2588, 6435, 0)
			)
		);
	}

	@Test
	public void kalphitesUseReviewedGearInventoryAndEveryRouteLeg()
	{
		final SlayerTaskStrategy regular = SlayerTaskStrategyCatalog.resolve(
			"Kalphites", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.PREFER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Kalphite Slayer Cave", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MELEE, regular.getCombatStyle());
		assertEquals("keris partisan of breaching", regular.getWeaponPriorities().get(0));
		assertTrue(regular.hasTag(SlayerTaskStrategy.MethodTag.CANNON));
		assertTrue(regular.hasTag(SlayerTaskStrategy.MethodTag.MULTI_COMBAT));
		final SlayerMethodRules regularRules = SlayerMethodRuleCatalog.resolve(
			"Kalphites", "Kalphite Slayer Cave", regular
		);
		assertTrue(regularRules.usesCannon());
		assertEquals(1500, regularRules.getCannonballQuantity());
		assertEquals(5, regularRules.resolveRestoreSlots(regular));
		assertEquals(0, regularRules.resolveFoodSlots(regular, regular.getFoodSlots()));
		assertTrue(regularRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Special attack weapon")
		));
		assertTrue(regularRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Poison protection")
		));

		final SlayerTaskStrategy noCannon = SlayerTaskStrategyCatalog.resolve(
			"Kalphites", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Kalphite Slayer Cave", false
		);
		assertFalse(noCannon.hasTag(SlayerTaskStrategy.MethodTag.CANNON));
		assertEquals("keris partisan of breaching", noCannon.getWeaponPriorities().get(0));

		final SlayerRouteCatalog.RouteProfile cave = SlayerRouteCatalog.resolve(
			"Kalphites", "Kalphite Slayer Cave", false
		);
		assertTrue(cave.isStaged());
		assertEquals(new WorldPoint(3321, 3122, 0), cave.getSurfaceAccess());
		assertEquals(new WorldPoint(3305, 9497, 0), cave.getPrimaryDestination());

		final SlayerTaskStrategy queen = SlayerTaskStrategyCatalog.resolve(
			"The Kalphite Queen", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Kalphite Lair", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.HYBRID, queen.getCombatStyle());
		assertEquals("keris partisan of breaching", queen.getWeaponPriorities().get(0));
		final SlayerMethodRules queenRules = SlayerMethodRuleCatalog.resolve(
			"The Kalphite Queen", "Kalphite Lair", queen
		);
		assertEquals(3, queenRules.resolveRestoreSlots(queen));
		assertTrue(queenRules.requiresRunePouch());
		assertTrue(queenRules.requiresBookOfDead());
		assertEquals(Arrays.asList("Fire", "Cosmic", "Soul", "Blood", "Death"),
			queenRules.getPouchRunes().stream()
				.map(SlayerMethodRules.PouchRuneRequirement::getName)
				.collect(java.util.stream.Collectors.toList()));
		final SlayerRuneOwnershipPolicy.Resolution queenBaseRunes =
			SlayerRuneOwnershipPolicy.resolve(
				queenRules.getPouchRunes(),
				Map.of(
					ItemID.FIRERUNE, 1000,
					ItemID.COSMICRUNE, 500,
					ItemID.SOULRUNE, 500,
					ItemID.BLOODRUNE, 500,
					ItemID.DEATHRUNE, 500
				)
			);
		assertEquals(5, queenBaseRunes.getRunes().size());
		assertTrue(queenBaseRunes.getUnownedRequirements().isEmpty());
		final SlayerRuneOwnershipPolicy.Resolution queenAetherRunes =
			SlayerRuneOwnershipPolicy.resolve(
				queenRules.getPouchRunes(),
				Map.of(
					ItemID.FIRERUNE, 1000,
					ItemID.AETHERRUNE, 500,
					ItemID.BLOODRUNE, 500,
					ItemID.DEATHRUNE, 500
				)
			);
		assertEquals(4, queenAetherRunes.getRunes().size());
		assertEquals(ItemID.AETHERRUNE, queenAetherRunes.getRunes().get(1).getItemId());
		assertEquals(2, queenAetherRunes.getRunes().get(1).getMinimumQuantity());
		assertTrue(queenAetherRunes.getUnownedRequirements().isEmpty());
		final SlayerRuneOwnershipPolicy.Resolution queenNoRunes =
			SlayerRuneOwnershipPolicy.resolve(
				queenRules.getPouchRunes(), Collections.emptyMap()
			);
		assertTrue(queenNoRunes.getRunes().isEmpty());
		assertEquals(5, queenNoRunes.getUnownedRequirements().size());
		assertTrue(queenRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Divine super combat potion")
		));
		assertTrue(queenRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Divine bastion potion")
		));
		assertTrue(queenRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Ranged weapon switch")
		));
		assertTrue(queenRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("House teleport")
		));

		final SlayerTaskStrategy queenMagic = SlayerTaskStrategyCatalog.resolve(
			"The Kalphite Queen", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_MAGIC,
			"Kalphite Lair", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MAGIC, queenMagic.getCombatStyle());
		assertEquals(Arrays.asList("tumeken s shadow", "eye of ayak"),
			queenMagic.getWeaponPriorities());
		assertTrue(queenMagic.isStrictWeaponProfile());

		final List<String> queenMagicBody = SlayerEquipmentAuditCatalog.priorities(
			"The Kalphite Queen", queenMagic, EquipmentInventorySlot.BODY,
			"Tumeken's shadow"
		);
		assertEquals(Arrays.asList("ancestral robe top", "virtus robe top"),
			queenMagicBody);

		final SlayerRouteCatalog.RouteProfile queenRoute = SlayerRouteCatalog.resolve(
			"The Kalphite Queen", "Kalphite Lair", true
		);
		assertTrue(queenRoute.isBoss());
		assertTrue(queenRoute.isStaged());
		assertEquals(new WorldPoint(3228, 3109, 0), queenRoute.getSurfaceAccess());
		assertEquals(new WorldPoint(3508, 9493, 0), queenRoute.getPrimaryDestination());
		assertTrue(queenRoute.getNpcNames().contains("kalphite queen"));

		final List<SlayerTravelRouteCatalog.Option> caveTravel =
			SlayerTravelRouteCatalog.fallbacksFor("Kalphite Slayer Cave");
		assertEquals("desert amulet 4", caveTravel.get(0).getItemFamily());
		assertEquals("Kalphite Cave", caveTravel.get(0).getDestination());
		final List<SlayerTravelRouteCatalog.Option> queenTravel =
			SlayerTravelRouteCatalog.fallbacksFor("Kalphite Lair");
		assertEquals("Kalphite Cave", queenTravel.get(0).getDestination());
	}

	@Test
	public void arceuusThrallPreparationPublishesEveryRequiredRune()
	{
		final SlayerTaskStrategy whisperer = SlayerTaskStrategyCatalog.resolve(
			"The Whisperer",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Lassar Undercity",
			false
		);
		final SlayerTaskPreparationCatalog.PreparationPlan preparation =
			SlayerTaskPreparationCatalog.resolve(
				"The Whisperer",
				"Lassar Undercity",
				whisperer,
				null,
				null,
				null,
				Map.of(
					ItemID.FIRERUNE, 1000,
					ItemID.BLOODRUNE, 500,
					ItemID.COSMICRUNE, 500
				)
			);
		assertEquals("tumeken s shadow", whisperer.getWeaponPriorities().get(0));
		assertTrue(whisperer.getWeaponPriorities().contains("eye of ayak"));

		assertTrue(preparation.isActive());
		assertEquals(3, preparation.getRuneStatuses().size());
		assertEquals(
			Arrays.asList("Fire", "Blood", "Cosmic"),
			preparation.getRuneStatuses().stream()
				.map(SlayerTaskPreparationCatalog.RuneStatus::getName)
				.collect(java.util.stream.Collectors.toList())
		);
		assertTrue(preparation.getRuneStatuses().stream().allMatch(
			status -> status.getRequired() > 0 && status.getAvailable() == 0
		));
		final SlayerTaskPreparationCatalog.PreparationPlan refreshedPreparation =
			SlayerTaskPreparationCatalog.resolve(
				"The Whisperer",
				"Lassar Undercity",
				whisperer,
				null,
				null,
				null,
				Map.of(
					ItemID.FIRERUNE, 1000,
					ItemID.BLOODRUNE, 500,
					ItemID.COSMICRUNE, 500
				)
			);
		assertTrue(preparation != refreshedPreparation);
		assertEquals(
			SlayerTaskReadinessOverlay.renderFingerprint(preparation),
			SlayerTaskReadinessOverlay.renderFingerprint(refreshedPreparation)
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"The Whisperer", "Lassar Undercity", whisperer
		);
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Blackstone fragment")
		));
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Lost Souls weapon")
				&& item.getAlternatives().get(0).equals("venator bow")
		));
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Special attack weapon")
				&& item.getAlternatives().get(0)
					.equals("eldritch nightmare staff")
		));
	}

	@Test
	public void regularBlueDragonsNeverPairLightbearerWithoutASpecWeapon()
	{
		assertTrue(!SlayerLoadoutAnalyzer
			.regularBlueDragonsAllowLightbearerForRegression());
		assertTrue(SlayerLoadoutAnalyzer
			.vorkathAllowsLightbearerForRegression());
	}

	@Test
	public void duplicateShortestPathTransportUpdatesDoNotRebuildConsumers()
	{
		assertTrue(!SlayerPlusPlugin.shouldRefreshTravelConsumers(
			"7|route|RESOLVED_ITEM|1|Teleport|Town|[]",
			"7|route|RESOLVED_ITEM|1|Teleport|Town|[]"
		));
		assertTrue(SlayerPlusPlugin.shouldRefreshTravelConsumers(
			"7|route|RESOLVED_ITEM|1|Teleport|Town|[]",
			"7|route|RESOLVED_ITEM|2|Better teleport|Town|[]"
		));
		assertTrue(SlayerPlusPlugin.shouldRefreshTravelConsumers(
			"7|route|PROVISIONAL_ITEM|1|Teleport|Town|[]",
			"7|route|RESOLVED_ITEM|1|Teleport|Town|[]"
		));
	}

	@Test
	public void taverleyKeepsItsFirstVerifiedOwnedShortestPathTeleport()
	{
		final SlayerTravelSelection resolved =
			SlayerTravelSelection.resolvedItem(
				"blue-dragons|taverley",
				4L,
				ItemID.NECKLACE_OF_MINIGAMES_8,
				"Games necklace(8)",
				"Burthorpe",
				Collections.singleton("Games necklace")
			);

		assertTrue(SlayerPlusPlugin.shouldRetainVerifiedTravelItem(
			"Taverley Dungeon", resolved, true
		));
		assertTrue(!SlayerPlusPlugin.shouldRetainVerifiedTravelItem(
			"Taverley Dungeon", resolved, false
		));
		assertTrue(!SlayerPlusPlugin.shouldRetainVerifiedTravelItem(
			"Ancient Cavern", resolved, true
		));
		assertTrue(!SlayerPlusPlugin.shouldRetainVerifiedTravelItem(
			"Taverley Dungeon",
			SlayerTravelSelection.provisionalItem(
				"blue-dragons|taverley",
				4L,
				ItemID.NECKLACE_OF_MINIGAMES_8,
				"Games necklace(8)",
				"Burthorpe",
				Collections.singleton("Games necklace")
			),
			true
		));
	}

	@Test
	public void blueDragonsAndVorkathResolveEveryRequiredRouteStage()
	{
		final WorldPoint bank = new WorldPoint(3200, 3200, 0);
		final SlayerRouteCatalog.RouteProfile blueDragons =
			SlayerRouteCatalog.resolve(
				"Blue dragons", "Taverley Dungeon", false
			);
		final SlayerRouteCoordinator.Stage blueSurface =
			SlayerRouteCoordinator.resolve(
				blueDragons,
				bank,
				null, Collections.emptySet(),
				null, Collections.emptySet(),
				null, Collections.emptySet(),
				null, Collections.emptySet()
			);
		assertTrue(blueSurface.isValid());
		assertEquals(
			SlayerRouteCoordinator.StageKind.SURFACE_APPROACH,
			blueSurface.getKind()
		);
		assertEquals(new WorldPoint(2885, 3397, 0), blueSurface.getDestination());

		final SlayerRouteCatalog.RouteProfile vorkath =
			SlayerRouteCatalog.resolve("Vorkath", "Ungael", true);
		final SlayerRouteCoordinator.Stage rellekka =
			SlayerRouteCoordinator.resolve(
				vorkath,
				bank,
				null, Collections.emptySet(),
				null, Collections.emptySet(),
				null, Collections.emptySet(),
				null, Collections.emptySet()
			);
		assertTrue(rellekka.isValid());
		assertEquals(
			SlayerRouteCoordinator.StageKind.SURFACE_APPROACH,
			rellekka.getKind()
		);
		assertEquals(new WorldPoint(2640, 3696, 0), rellekka.getDestination());

		final SlayerRouteCoordinator.Stage ungael =
			SlayerRouteCoordinator.resolve(
				vorkath,
				new WorldPoint(2277, 4034, 0),
				null, Collections.emptySet(),
				null, Collections.emptySet(),
				null, Collections.emptySet(),
				null, Collections.emptySet()
			);
		assertTrue(ungael.isValid());
		assertEquals(
			SlayerRouteCoordinator.StageKind.INTERIOR,
			ungael.getKind()
		);
		assertEquals(new WorldPoint(2273, 4065, 0), ungael.getDestination());
	}

	@Test
	public void vorkathUsesWikiRunePouchPackageAndRuneReferencesStayBelowGear()
	{
		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			"Vorkath",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Ungael",
			false
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"Vorkath", "Ungael", strategy
		);

		assertEquals(4, rules.getPouchRunes().size());
		assertEquals(ItemID.AIRRUNE, rules.getPouchRunes().get(0).getItemId());
		assertEquals(ItemID.EARTHRUNE, rules.getPouchRunes().get(1).getItemId());
		assertEquals(ItemID.CHAOSRUNE, rules.getPouchRunes().get(2).getItemId());
		assertEquals(ItemID.LAWRUNE, rules.getPouchRunes().get(3).getItemId());
		assertEquals(
			SlayerMethodRules.Spellbook.STANDARD,
			rules.getSpellbook()
		);
		assertTrue(rules.requiresRunePouch());
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Diamond dragon bolts (e) switch")
		));
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Special attack weapon")
		));
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Crumble Undead autocast switch")
		));
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Tick-safe healing")
		));
		assertTrue(!rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Crumble Undead staff")
		));
		assertTrue(
			SlayerBankTagLayoutService.preparationReferencesRenderBelowGearForRegression()
		);

		final SlayerTaskPreparationCatalog.PreparationPlan preparation =
			SlayerTaskPreparationCatalog.resolve(
				"Vorkath",
				"Ungael",
				strategy,
				null,
				null,
				null,
				Map.of(
					ItemID.DUSTRUNE, 1000,
					ItemID.CHAOSRUNE, 1000,
					ItemID.LAWRUNE, 1000
				)
			);
		assertTrue(preparation.isActive());
		assertEquals(ItemID.BH_RUNE_POUCH,
			(int) preparation.getBankTagItemIds().get(0));
		assertTrue(preparation.getBankTagItemIds().contains(ItemID.DUSTRUNE));
		assertTrue(preparation.getBankTagItemIds().contains(ItemID.CHAOSRUNE));
		assertTrue(preparation.getBankTagItemIds().contains(ItemID.LAWRUNE));
	}

	@Test
	public void globalRunePolicyUsesOnlyOwnedRunesAndCollapsesCombinations()
	{
		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			"Vorkath",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Ungael",
			false
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"Vorkath", "Ungael", strategy
		);

		final SlayerRuneOwnershipPolicy.Resolution dustPackage =
			SlayerRuneOwnershipPolicy.resolve(
				rules.getPouchRunes(),
				Map.of(
					ItemID.DUSTRUNE, 500,
					ItemID.CHAOSRUNE, 500,
					ItemID.LAWRUNE, 500
				)
			);
		assertEquals(3, dustPackage.getRunes().size());
		assertEquals(ItemID.DUSTRUNE,
			dustPackage.getRunes().get(0).getItemId());
		assertTrue(dustPackage.getUnownedRequirements().isEmpty());

		final SlayerRuneOwnershipPolicy.Resolution basePackage =
			SlayerRuneOwnershipPolicy.resolve(
				rules.getPouchRunes(),
				Map.of(
					ItemID.AIRRUNE, 500,
					ItemID.EARTHRUNE, 500,
					ItemID.CHAOSRUNE, 500
				)
			);
		assertEquals(3, basePackage.getRunes().size());
		assertTrue(basePackage.getRunes().stream().noneMatch(
			rune -> rune.getItemId() == ItemID.DUSTRUNE
				|| rune.getItemId() == ItemID.LAWRUNE
		));
		assertEquals(Collections.singletonList("Law"),
			basePackage.getUnownedRequirements());

		final SlayerTaskPreparationCatalog.PreparationPlan noRunes =
			SlayerTaskPreparationCatalog.resolve(
				"Vorkath", "Ungael", strategy,
				null, null, null, Collections.emptyMap()
			);
		assertEquals(Collections.singletonList(ItemID.BH_RUNE_POUCH),
			noRunes.getBankTagItemIds());
	}

	@Test
	public void bankTagDoesNotReplaceUnrelatedPlannedCapeWithOwnedQuiver()
	{
		assertTrue(!SlayerBankTagLayoutService
			.shouldResolveOwnedDizanaVariantForPlan(ItemID.COINS));
		assertTrue(SlayerBankTagLayoutService
			.shouldResolveOwnedDizanaVariantForPlan(
				ItemID.DIZANAS_QUIVER_CHARGED
			));
	}

	@Test
	public void vorkathOwnedMeleeOverrideSurvivesBossVariantResolution()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategyCatalog.resolve(
			"Vorkath",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_MELEE,
			"Ungael",
			false
		);
		final SlayerRecommendation ownedRecommendation =
			new SlayerRecommendation(
				"Ungael",
				melee.getMethod(),
				"Owned melee setup",
				"Travel to Rellekka",
				"Not allowed",
				"Rune pouch",
				"Boss alternative",
				null,
				melee
			);

		final SlayerTaskVariantCatalog.ResolvedTarget resolved =
			SlayerTaskVariantCatalog.resolve(
				"Blue dragons",
				SlayerTaskVariant.VORKATH,
				ownedRecommendation,
				new SlayerPlusConfig() { }
			);

		assertEquals("Vorkath", resolved.getTaskName());
		assertEquals(
			SlayerTaskStrategy.CombatStyle.MELEE,
			resolved.getStrategy().getCombatStyle()
		);
		final SlayerMethodRules meleeRules = SlayerMethodRuleCatalog.resolve(
			"Vorkath", "Ungael", resolved.getStrategy()
		);
		assertEquals(3, meleeRules.resolveRestoreSlots(resolved.getStrategy()));
		assertTrue(!meleeRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().contains("Diamond dragon bolts")
		));
	}

	@Test
	public void everyNamedDefenderIncludingGhommalVariantsMatchesShieldFallback()
	{
		assertTrue(SlayerLoadoutAnalyzer.defenderFallbackMatchesShieldForRegression(
			"Ghommal's avernic defender 5 (l)"
		));
		assertTrue(SlayerLoadoutAnalyzer.defenderFallbackMatchesShieldForRegression(
			"Dragon defender"
		));
		assertTrue(!SlayerLoadoutAnalyzer.defenderFallbackMatchesShieldForRegression(
			"Avernic defender hilt"
		));
		assertTrue(!SlayerLoadoutAnalyzer.defenderFallbackMatchesShieldForRegression(
			"Ghommal's hilt 6"
		));
	}

	@Test
	public void blueDragonSafespotInventoryLeavesDropSpaceWithoutRestores()
	{
		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			"Blue dragons",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Taverley Dungeon",
			false
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"Blue dragons", "Taverley Dungeon", strategy
		);

		assertEquals(24, rules.getReservedLootSlots());
		assertEquals(0, rules.resolveRestoreSlots(strategy));
		assertEquals(0, rules.resolveFoodSlots(strategy, strategy.getFoodSlots()));
	}

	@Test
	public void blueDragonRangedMethodHasCompleteOwnedGearProgression()
	{
		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			"Blue dragons",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_RANGED,
			"Taverley Dungeon",
			false
		);
		final List<String> weapons = strategy.getWeaponPriorities();
		assertEquals("dragon hunter crossbow", weapons.get(0));
		assertTrue(weapons.indexOf("armadyl crossbow")
			< weapons.indexOf("dragon crossbow"));
		assertTrue(weapons.contains("hunters sunlight crossbow"));
		assertEquals("rune crossbow", weapons.get(weapons.size() - 1));

		final List<String> heads = SlayerEquipmentAuditCatalog.priorities(
			"Blue dragons", strategy, EquipmentInventorySlot.HEAD,
			"Rune crossbow"
		);
		assertEquals("slayer helmet i", heads.get(0));
		assertTrue(heads.contains("blessed coif"));

		final List<String> shields = SlayerEquipmentAuditCatalog.priorities(
			"Blue dragons", strategy, EquipmentInventorySlot.SHIELD,
			"Rune crossbow"
		);
		assertTrue(shields.contains("dragonfire ward"));
	}

	@Test
	public void blueDragonMagicUsesWaterWeaknessAndStructuredRunePouch()
	{
		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			"Blue dragons",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_MAGIC,
			"Taverley Dungeon",
			false
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"Blue dragons", "Taverley Dungeon", strategy
		);

		assertEquals(SlayerTaskStrategy.CombatStyle.MAGIC,
			strategy.getCombatStyle());
		assertTrue(strategy.getMethod().contains("Water Blast"));
		assertEquals("dragon hunter wand", strategy.getWeaponPriorities().get(0));
		assertEquals(SlayerMethodRules.Spellbook.STANDARD, rules.getSpellbook());
		assertTrue(rules.requiresRunePouch());
		assertEquals(3, rules.getPouchRunes().size());
		assertEquals(ItemID.WATERRUNE, rules.getPouchRunes().get(1).getItemId());
		assertEquals(24, rules.getReservedLootSlots());
		assertEquals(0, rules.resolveFoodSlots(strategy, strategy.getFoodSlots()));
	}

	@Test
	public void blueDragonMeleeDoesNotInheritSafespotInventory()
	{
		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			"Blue dragons",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_MELEE,
			"Taverley Dungeon",
			false
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"Blue dragons", "Taverley Dungeon", strategy
		);

		assertEquals(SlayerTaskStrategy.CombatStyle.MELEE,
			strategy.getCombatStyle());
		assertEquals(12, rules.getReservedLootSlots());
		assertEquals(6, rules.resolveFoodSlots(strategy, strategy.getFoodSlots()));
	}

	@Test
	public void regularDragonRangedPackagesNeverPairTwoHandedWeaponsWithTheRequiredShield()
	{
		for (final String task : Arrays.asList(
			"Green dragons", "Red dragons", "Black dragons"))
		{
			final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				task,
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.NEVER,
				SlayerPreference.Burst.NEVER,
				SlayerPreference.CombatStyle.PREFER_RANGED,
				"Standard Slayer location",
				false
			);
			assertEquals(SlayerTaskStrategy.CombatStyle.RANGED,
				strategy.getCombatStyle());
			assertFalse(strategy.getWeaponPriorities().contains("twisted bow"));
			assertFalse(strategy.getWeaponPriorities().contains("bow of faerdhinen"));
			assertFalse(strategy.getWeaponPriorities().contains("toxic blowpipe"));
			assertTrue(strategy.getWeaponPriorities().contains("rune crossbow"));
		}
	}

	@Test
	public void metalDragonsOfferCompleteEarthWaveAndDragonfirePackages()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategyCatalog.resolve(
			"Metal dragons",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_MAGIC,
			"Brimhaven Dungeon",
			false
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"Metal dragons", "Brimhaven Dungeon", magic
		);

		assertEquals(SlayerTaskStrategy.CombatStyle.MAGIC, magic.getCombatStyle());
		assertTrue(magic.getMethod().contains("Earth Wave"));
		assertFalse(magic.getWeaponPriorities().contains("tumeken s shadow"));
		assertEquals(SlayerMethodRules.Spellbook.STANDARD, rules.getSpellbook());
		assertEquals(3, rules.getPouchRunes().size());
		assertEquals(ItemID.AIRRUNE, rules.getPouchRunes().get(0).getItemId());
		assertEquals(ItemID.EARTHRUNE, rules.getPouchRunes().get(1).getItemId());
		assertEquals(ItemID.BLOODRUNE, rules.getPouchRunes().get(2).getItemId());
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals(
				SlayerPotionPolicy.EXTENDED_ANTIFIRE_DISPLAY)
				&& item.getSlotCount() == 2
		));
	}

	@Test
	public void royalTitansUseMeleeBaseWithCompleteMechanicSwitches()
	{
		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			"Royal Titans",
			SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Royal Titans arena",
			false
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"Royal Titans", "Royal Titans arena", strategy
		);

		assertEquals(SlayerTaskStrategy.CombatStyle.MELEE,
			strategy.getCombatStyle());
		assertEquals("scythe of vitur", strategy.getWeaponPriorities().get(0));
		assertEquals(5, rules.resolveRestoreSlots(strategy));
		assertEquals(SlayerMethodRules.Spellbook.STANDARD, rules.getSpellbook());
		assertEquals(4, rules.getPouchRunes().size());
		assertFalse(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Melee weapon")
		));
		for (final String required : Arrays.asList(
			"Ranged weapon switch", "Ranged body switch", "Ranged legs switch",
			"Elemental spell staff", "Magic cape switch", "Magic body switch",
			"Magic legs switch", "Magic amulet switch", "Magic glove switch",
			"Melee special attack weapon"))
		{
			assertTrue(required, rules.getRequiredItems().stream().anyMatch(
				item -> item.getDisplayName().equals(required)
			));
		}
		assertEquals("infernal cape", SlayerEquipmentAuditCatalog.priorities(
			"Royal Titans", strategy, EquipmentInventorySlot.CAPE,
			"Scythe of vitur"
		).get(0));
	}

	@Test
	public void ordinaryEquipmentSlotsUseOwnedFallbacksInsteadOfBlankPlaceholders()
	{
		for (final EquipmentInventorySlot slot : Arrays.asList(
			EquipmentInventorySlot.HEAD,
			EquipmentInventorySlot.CAPE,
			EquipmentInventorySlot.AMULET,
			EquipmentInventorySlot.BODY,
			EquipmentInventorySlot.WEAPON,
			EquipmentInventorySlot.SHIELD,
			EquipmentInventorySlot.LEGS,
			EquipmentInventorySlot.GLOVES,
			EquipmentInventorySlot.BOOTS,
			EquipmentInventorySlot.RING))
		{
			assertTrue(slot.name(),
				SlayerLoadoutAnalyzer.ordinaryOwnedSlotFallbackForRegression(
					slot, "Regression owned " + slot.name().toLowerCase()
				));
		}
	}

	@Test
	public void vorkathInventoryUsesSwitchSupplyAndUtilityRows()
	{
		final List<SlayerLoadoutItem> inventory = Arrays.asList(
			groupItem("Fremennik sea boots 4", SlayerMethodRules.InventoryGroup.TRAVEL),
			groupItem("Divine rune pouch", SlayerMethodRules.InventoryGroup.UTILITY),
			switchItem("Slayer's staff", SlayerLoadoutItem.SwitchStyle.MAGIC)
				.withInventoryGroup(SlayerMethodRules.InventoryGroup.SWITCH),
			switchItem("Zaryte crossbow", SlayerLoadoutItem.SwitchStyle.RANGED)
				.withInventoryGroup(SlayerMethodRules.InventoryGroup.SWITCH),
			groupItem("Diamond dragon bolts (e)", SlayerMethodRules.InventoryGroup.RUNES_AMMO),
			groupItem("Divine ranging potion", SlayerMethodRules.InventoryGroup.BOOST),
			groupItem("Extended anti-venom+", SlayerMethodRules.InventoryGroup.PROTECTION),
			groupItem("Extended super antifire", SlayerMethodRules.InventoryGroup.PROTECTION),
			groupItem("Prayer potion A", SlayerMethodRules.InventoryGroup.RESTORE),
			groupItem("Prayer potion B", SlayerMethodRules.InventoryGroup.RESTORE),
			groupItem("Prayer potion C", SlayerMethodRules.InventoryGroup.RESTORE),
			groupItem("Anglerfish", SlayerMethodRules.InventoryGroup.FOOD),
			groupItem("Guthix rest", SlayerMethodRules.InventoryGroup.FOOD)
		);
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements =
			SlayerBankTagLayoutService.planInventoryForBankTag(
				inventory, -1, true
			);

		assertEquals(0, slotFor(placements, "Slayer's staff"));
		assertEquals(1, slotFor(placements, "Zaryte crossbow"));
		assertEquals(2, slotFor(placements, "Diamond dragon bolts (e)"));
		assertEquals(3, slotFor(placements, "Divine ranging potion"));
		assertEquals(4, slotFor(placements, "Extended anti-venom+"));
		assertEquals(5, slotFor(placements, "Extended super antifire"));
		assertEquals(6, slotFor(placements, "Prayer potion A"));
		assertEquals(9, slotFor(placements, "Anglerfish"));
		assertEquals(10, slotFor(placements, "Guthix rest"));
		assertEquals(26, slotFor(placements, "Fremennik sea boots 4"));
		assertEquals(27, slotFor(placements, "Divine rune pouch"));
	}

	@Test
	public void sidebarUsesSettingsInsteadOfPlaceholderLeaderboard()
	{
		final SlayerPlusPanel panel = new SlayerPlusPanel();
		assertTrue(containsLabelText(panel, "SETTINGS"));
		assertTrue(containsLabelText(panel, "Combat style"));
		assertTrue(containsLabelText(panel, "Slayer helm"));
		assertTrue(containsLabelText(panel, "Shard preference"));
		assertTrue(containsLabelText(panel, "Travel priority"));
		assertFalse(containsLabelText(panel, "ROUTING & DISPLAY"));
		assertFalse(containsLabelText(panel, "Enable Shortest Path routing"));
		assertFalse(containsLabelText(panel, "Show loadout recommendations"));
		assertTrue(!containsLabelText(panel, "SLAYER LEADERBOARD"));
	}

	@Test
	public void deepCatalogContractsRemainValid()
	{
		SlayerCatalogRegressionValidator.validateDeepOrThrow();
	}

	@Test
	public void officialTaskAliasesResolveToReviewedProfiles()
	{
		for (final String task : Arrays.asList(
			"Bloodvelds", "Dagannoths", "Kurasks", "Nechryaels",
			"Minions of Scabaras", "Bronze dragons", "Iron dragons",
			"Steel dragons", "Mithril dragons", "Adamant dragons",
			"Rune dragons"
		))
		{
			assertTrue(task, SlayerTaskStrategyCatalog.hasExplicitStrategy(task));
			final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				task,
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Not restricted",
				false
			);
			assertTrue(task, strategy.isReviewed());
			assertTrue(task, !strategy.getWeaponPriorities().isEmpty());
		}
	}

	@Test
	public void currentBossAndDemiBossAlternativesAreSelectable()
	{
		assertTrue(SlayerTaskVariantCatalog.getAvailableVariants("Cows")
			.contains(SlayerTaskVariant.BRUTUS));
		assertTrue(SlayerTaskVariantCatalog.getAvailableVariants("Black demons")
			.contains(SlayerTaskVariant.DEMONIC_GORILLAS));
		assertTrue(SlayerTaskVariantCatalog.getAvailableVariants("Monkeys")
			.contains(SlayerTaskVariant.DEMONIC_GORILLAS));
		assertTrue(SlayerTaskVariantCatalog.getAvailableVariants("Greater demons")
			.contains(SlayerTaskVariant.TORMENTED_DEMONS));
		assertTrue(SlayerTaskVariantCatalog.getAvailableVariants("Crazy Archaeologist")
			.contains(SlayerTaskVariant.DERANGED_ARCHAEOLOGIST));
	}

	@Test
	public void developmentTaskSelectorCoversEveryReviewedTaskAndEncounter()
	{
		assertFalse(SlayerTaskResearchCatalog.getReviewedTaskNames().isEmpty());
		for (final String taskName :
			SlayerTaskResearchCatalog.getReviewedTaskNames())
		{
			final List<SlayerTaskVariant> variants =
				SlayerTaskVariantCatalog.getAvailableVariants(taskName);
			assertFalse(taskName, variants.isEmpty());
			for (final SlayerTaskVariant variant : variants)
			{
				assertTrue(
					taskName + " / " + variant,
					!SlayerTaskVariantCatalog.getOptionLabel(
						taskName,
						variant
					).trim().isEmpty()
				);
			}
		}
	}

	@Test
	public void architectureContractsRemainValid()
	{
		SlayerArchitectureRegressionGuard.validateOrThrow();
	}

	@Test
	public void infernoPreparationRouteAcceptsAdjacentArrivalAtZukBankObject()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);
		final WorldPoint zukBank = new WorldPoint(2543, 5141, 0);

		assertTrue(bridge.routeToPreparationBank(
			start,
			Collections.singleton(zukBank),
			true,
			true,
			true
		));

		final PluginMessage message = posted.get();
		assertNotNull(message);
		assertEquals("shortestpath", message.getNamespace());
		assertEquals("path", message.getName());
		assertEquals(start, message.getData().get("start"));
		assertEquals(
			Collections.singleton(zukBank),
			message.getData().get("target")
		);
		final Map<?, ?> config = (Map<?, ?>) message.getData().get("config");
		assertEquals("Inventory and Bank", config.get("useTeleportationItems"));
		assertEquals(Boolean.TRUE, config.get("includeBankPath"));
		assertEquals(Boolean.TRUE, config.get("postTransports"));
		assertEquals(Boolean.TRUE, config.get("showTransportInfo"));
		assertEquals(Boolean.FALSE, config.get("showBankPickupInfo"));
		assertEquals(Boolean.TRUE, config.get("useAgilityShortcuts"));
		assertEquals(0, config.get("costAgilityShortcuts"));
		assertEquals(2, config.get("unreachableTargetDistanceThreshold"));
	}

	@Test
	public void spellbookRouteUsesOwnedBankTeleportsWithoutReplacingTaskTravel()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);
		final WorldPoint altar = new WorldPoint(3231, 9311, 0);

		assertTrue(bridge.routeToSpellbookChange(
			start,
			Collections.singleton(altar),
			true,
			true
		));

		final PluginMessage message = posted.get();
		assertNotNull(message);
		assertEquals(start, message.getData().get("start"));
		assertEquals(Collections.singleton(altar), message.getData().get("target"));
		final Map<?, ?> config = (Map<?, ?>) message.getData().get("config");
		assertEquals("Inventory and Bank", config.get("useTeleportationItems"));
		assertEquals(Boolean.TRUE, config.get("includeBankPath"));
		assertEquals(Boolean.TRUE, config.get("showTransportInfo"));
		assertEquals(Boolean.FALSE, config.get("showBankPickupInfo"));
		assertEquals(Boolean.FALSE, config.get("postTransports"));
		assertFalse(config.containsKey("usePoh"));
		assertEquals(1_000_000, config.get("costNonConsumableTeleportationItems"));
	}

	@Test
	public void bankDiscoveryKeepsNativeTransportTextVisible()
	{
		assertTrue(ShortestPathBridge.showsNativeTransportInfoForRegression(true, true));
		assertTrue(ShortestPathBridge.showsNativeTransportInfoForRegression(false, true));
		assertTrue(ShortestPathBridge.showsNativeTransportInfoForRegression(false, false));
	}

	@Test
	public void araxxorRouteKeepsShortestPathActiveWhilePreferringDirectScroll()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);
		final WorldPoint cave = new WorldPoint(3657, 3407, 0);

		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(cave),
			true,
			true,
			true,
			true,
			true
		));

		final PluginMessage message = posted.get();
		assertNotNull(message);
		final Map<?, ?> config = (Map<?, ?>) message.getData().get("config");
		assertEquals("Inventory and Bank", config.get("useTeleportationItems"));
		assertEquals(Boolean.TRUE, config.get("includeBankPath"));
		assertEquals(Boolean.TRUE, config.get("showTransportInfo"));
		assertEquals(Boolean.TRUE, config.get("postTransports"));
		assertEquals(1_000_000, config.get("costNonConsumableTeleportationItems"));
	}

	@Test
	public void unsupportedEncounterTeleportsUseManualFirstLegs()
	{
		assertTrue(SlayerPlusPlugin.isUnsupportedEncounterTeleportItem(
			"Ancient Guthixian Temple", "Guthixian temple teleport"
		));
		assertTrue(SlayerPlusPlugin.isUnsupportedEncounterTeleportItem(
			"Skotizo's Lair", "Dark totem"
		));
		assertFalse(SlayerPlusPlugin.isUnsupportedEncounterTeleportItem(
			"Ancient Guthixian Temple", "Games necklace(8)"
		));
		assertEquals(
			new WorldPoint(3245, 9500, 2),
			SlayerPlusPlugin.tormentedTearsLandingForRegression()
		);
		assertEquals(
			new WorldPoint(3241, 9525, 2),
			SlayerPlusPlugin.tormentedLightCreatureApproachForRegression()
		);
		assertTrue(SlayerPlusPlugin.isOnTearsOfGuthixLayer(
			new WorldPoint(3245, 9500, 2)
		));
		assertTrue(SlayerPlusPlugin.isOnTearsOfGuthixUpperLayer(
			new WorldPoint(3245, 9500, 2)
		));
		assertFalse(SlayerPlusPlugin.isOnTearsOfGuthixUpperLayer(
			new WorldPoint(3245, 9500, 0)
		));
		assertTrue(SlayerPlusPlugin.isOnAncientGuthixianTempleLayer(
			new WorldPoint(4097, 4419, 0)
		));
		assertFalse(SlayerPlusPlugin.shouldSubmitTormentedPath(
			false,
			new WorldPoint(3245, 9500, 2),
			new WorldPoint(3245, 9500, 2)
		));
		assertTrue(SlayerPlusPlugin.shouldSubmitTormentedPath(
			true,
			new WorldPoint(3245, 9500, 2),
			new WorldPoint(3245, 9500, 2)
		));
		assertTrue(SlayerPlusPlugin.shouldSubmitTormentedPath(
			false,
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3245, 9500, 2)
		));
		assertEquals(
			Arrays.asList(
				"guthixian temple teleport",
				"games necklace",
				"max cape",
				"construct cape",
				"teleport to house"
			),
			SlayerTravelRouteCatalog.fallbacksFor("Ancient Guthixian Temple")
				.stream()
				.map(SlayerTravelRouteCatalog.Option::getItemFamily)
				.collect(java.util.stream.Collectors.toList())
		);
		assertFalse(SlayerPlusPlugin.isUnsupportedEncounterTeleportItem(
			"Skotizo's Lair", "Xeric's talisman"
		));
		assertEquals(
			"xeric s talisman",
			SlayerTravelRouteCatalog.fallbacksFor("Skotizo's Lair")
				.get(0).getItemFamily()
		);
		assertEquals(
			new WorldPoint(1666, 10050, 0),
			SlayerPlusPlugin.skotizoCatacombsAltarForRegression()
		);
		assertTrue(SlayerPlusPlugin.isOnCatacombsCoordinateLayer(
			new WorldPoint(1666, 10050, 0)
		));
		assertFalse(SlayerPlusPlugin.isOnCatacombsCoordinateLayer(
			new WorldPoint(1639, 3673, 0)
		));
		assertTrue(SlayerPlusPlugin.isOnSkotizoLairCoordinateLayer(
			new WorldPoint(1693, 9886, 0)
		));
		assertFalse(SlayerPlusPlugin.isOnSkotizoLairCoordinateLayer(
			new WorldPoint(1666, 10050, 0)
		));

		final SlayerRouteCatalog.RouteProfile tormented =
			SlayerRouteCatalog.resolve(
				"Tormented demons", "Ancient Guthixian Temple", true
			);
		assertNotNull(tormented);
		assertTrue(tormented.isBoss());
		assertEquals(
			new WorldPoint(4097, 4419, 0),
			tormented.getPrimaryDestination()
		);

		final SlayerRouteCatalog.RouteProfile skotizo =
			SlayerRouteCatalog.resolve("Skotizo", "Skotizo's Lair", true);
		assertNotNull(skotizo);
		assertTrue(skotizo.isBoss());
		assertTrue(skotizo.isStaged());
		assertEquals(
			new WorldPoint(1639, 3673, 0),
			skotizo.getSurfaceAccess()
		);
		assertEquals(
			new WorldPoint(1693, 9886, 0),
			skotizo.getPrimaryDestination()
		);
	}

	@Test
	public void inactiveOwnedQuestCapeIsNotTreatedAsUsable()
	{
		assertTrue(SlayerPlusPlugin.shouldSuppressInactiveQuestCapeForRegression(
			true, 332, 333
		));
		assertFalse(SlayerPlusPlugin.shouldSuppressInactiveQuestCapeForRegression(
			true, 333, 333
		));
		assertFalse(SlayerPlusPlugin.shouldSuppressInactiveQuestCapeForRegression(
			false, 332, 333
		));
	}

	@Test
	public void guidedRoutingLetsShortestPathResolveInstancedPohStart()
	{
		assertEquals(null, SlayerPlusPlugin.shortestPathStartForRegression(
			new WorldPoint(3200, 3200, 0),
			true
		));
		assertEquals(new WorldPoint(2953, 3224, 0),
			SlayerPlusPlugin.shortestPathStartForRegression(
				new WorldPoint(2953, 3224, 0),
				false
			)
		);
		assertEquals(null, SlayerPlusPlugin.shortestPathStartForRegression(
			null,
			false
		));
	}

	@Test
	public void spellbookRouteRequiresAnAuthoritativeBankSnapshot()
	{
		assertTrue(
			SlayerPlusPlugin.needsBankSnapshotBeforeSpellbookRouteForRegression(false)
		);
		assertFalse(
			SlayerPlusPlugin.needsBankSnapshotBeforeSpellbookRouteForRegression(true)
		);
	}

	@Test
	public void infernoNoHiltBranchStopsAtReviewedEastHotVentDoor()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);
		final WorldPoint hotVent = new WorldPoint(2495, 5157, 0);

		assertEquals(
			hotVent,
			SlayerBankRouteCatalog.getInfernoPreparationHotVentDoorTarget()
		);
		assertTrue(bridge.routeToPreparationAccess(
			start,
			SlayerBankRouteCatalog.getInfernoPreparationHotVentDoorTargets(),
			true,
			true,
			true
		));

		final PluginMessage message = posted.get();
		assertNotNull(message);
		assertEquals(start, message.getData().get("start"));
		assertEquals(
			Collections.singleton(hotVent),
			message.getData().get("target")
		);
		final Map<?, ?> config = (Map<?, ?>) message.getData().get("config");
		assertEquals("Inventory and Bank", config.get("useTeleportationItems"));
		assertEquals(Boolean.TRUE, config.get("includeBankPath"));
		assertEquals(Boolean.TRUE, config.get("postTransports"));
		assertEquals(Boolean.FALSE, config.get("usePoh"));
		assertEquals(2, config.get("unreachableTargetDistanceThreshold"));
	}

	@Test
	public void postGhommalBankHandoffUsesLocalBankArrivalTolerance()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(2500, 5150, 0);
		final WorldPoint zukBank = new WorldPoint(2543, 5141, 0);

		assertTrue(bridge.routeToLocalBankTarget(start, zukBank, true));

		final PluginMessage message = posted.get();
		assertNotNull(message);
		assertEquals(start, message.getData().get("start"));
		assertEquals(zukBank, message.getData().get("target"));
		final Map<?, ?> config = (Map<?, ?>) message.getData().get("config");
		assertEquals("None", config.get("useTeleportationItems"));
		assertEquals(Boolean.FALSE, config.get("includeBankPath"));
		assertEquals(Boolean.FALSE, config.get("postTransports"));
		assertEquals(Boolean.FALSE, config.get("drawMap"));
		assertEquals(2, config.get("unreachableTargetDistanceThreshold"));
	}

	@Test
	public void infernoEntranceRouteUsesExactLocalEndpointWithoutTeleports()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(2543, 5141, 0);
		final WorldPoint entrancePerimeter = new WorldPoint(2500, 5100, 0);

		assertTrue(bridge.routeToExactLocalTarget(
			start, entrancePerimeter, true
		));

		final PluginMessage message = posted.get();
		assertNotNull(message);
		assertEquals(entrancePerimeter, message.getData().get("target"));
		final Map<?, ?> config = (Map<?, ?>) message.getData().get("config");
		assertEquals("None", config.get("useTeleportationItems"));
		assertEquals(Boolean.FALSE, config.get("includeBankPath"));
		assertEquals(Boolean.FALSE, config.get("postTransports"));
		assertEquals(Boolean.FALSE, config.get("drawMap"));
		assertEquals(0, config.get("unreachableTargetDistanceThreshold"));
	}

	@Test
	public void infernoEntryApproachIsASeparateReviewedWaypoint()
	{
		final WorldPoint approach = new WorldPoint(2496, 5115, 0);
		assertEquals(
			approach,
			SlayerRouteCatalog.getPreparationEntryApproachTarget(
				"TzKal-Zuk", "Inferno", true
			)
		);
		assertEquals(
			null,
			SlayerRouteCatalog.getPreparationEntryApproachTarget(
				"TzKal-Zuk", "Inferno", false
			)
		);

		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint bank = new WorldPoint(2543, 5141, 0);
		assertTrue(bridge.routeToLocalTaskArea(
			bank, Collections.singleton(approach), true
		));

		final PluginMessage message = posted.get();
		assertNotNull(message);
		assertEquals(bank, message.getData().get("start"));
		assertEquals(
			Collections.singleton(approach),
			message.getData().get("target")
		);
		final Map<?, ?> config = (Map<?, ?>) message.getData().get("config");
		assertEquals("None", config.get("useTeleportationItems"));
		assertEquals(Boolean.FALSE, config.get("drawMap"));
		assertEquals(6, config.get("unreachableTargetDistanceThreshold"));
	}

	@Test
	public void manualGhommalHandoffRemainsVisibleWhileRoutinePathsStayCompact()
	{
		assertTrue(SlayerPlusPlugin.isTormentedChasmWallForRegression(
			"Stone wall", new String[]{"Climb-up", null}));
		assertFalse(SlayerPlusPlugin.isTormentedChasmWallForRegression(
			"Stone wall", new String[]{"Examine", null}));
		assertTrue(SlayerPlusPlugin.isTormentedChasmExitForRegression(
			"Cave opening", new String[]{"Enter", null}));
		assertTrue(SlayerPlusPlugin.isTormentedChasmExitForRegression(
			"Cave opening", new String[]{"Climb-through", null}));
		assertFalse(SlayerPlusPlugin.isTormentedChasmExitForRegression(
			"Cave opening", new String[]{"Examine", null}));
		assertTrue(
			SlayerPlusPlugin.isTormentedLightCreatureAttractionMessage(
				"<col=ef1020>The light creature is attracted to your beam and comes towards you...</col>"
			)
		);
		assertFalse(
			SlayerPlusPlugin.isTormentedLightCreatureAttractionMessage(
				"You rub the necklace..."
			)
		);
		assertEquals(
			"Click the light creature → Into the chasm",
			SlayerPlusPanel.playerFacingTravelAction(
				"Light creature → Into the chasm"
			)
		);
		assertEquals(
			"Climb the stone wall",
			SlayerPlusPanel.playerFacingTravelAction("Climb the stone wall")
		);
		assertEquals(
			"After landing, run south, climb both walls, then enter the skull.",
			SlayerPlusPanel.playerFacingTravelNote(
				"Light creature → Into the chasm"
			)
		);
		assertEquals(
			"Withdraw and use Ghommal's avernic defender 5 (l) \u2192 Mor Ul Rek",
			SlayerPlusPanel.playerFacingTravelAction(
				"Ghommal's avernic defender 5 (l) \u2192 Mor Ul Rek"
			)
		);
		assertEquals(
			"Teleport to Mor Ul Rek. Shortest Path starts after you land.",
			SlayerPlusPanel.playerFacingTravelNote(
				"Ghommal's avernic defender 5 (l) \u2192 Mor Ul Rek"
			)
		);
		assertEquals(
			"Use it on a light creature, descend, run south, climb both walls, then enter the skull.",
			SlayerPlusPanel.playerFacingTravelNote("Lit Sapphire lantern")
		);
		assertEquals(
			"",
			SlayerPlusPanel.compactSessionStatus(
				true,
				true,
				"Inferno preparation — withdraw the Ghommal item and use Mor Ul Rek"
			)
		);
		assertEquals(
			"Pass through the glowing Hot vent door. SlayerPlus will start the local Zuk-bank route after you cross.",
			SlayerPlusPanel.compactSessionStatus(
				true,
				true,
				"At the east Mor Ul Rek Hot vent door — Pass through the glowing barrier"
			)
		);
		assertEquals(
			"",
			SlayerPlusPanel.compactSessionStatus(
				true,
				true,
				"Routing to the selected task area"
			)
		);
	}

	@Test
	public void ghommalNextStepCalloutIsAttachedToTheVisibleBankView()
	{
		final SlayerPlusPanel panel = new SlayerPlusPanel();
		panel.showTravelRecommendation(
			"Ghommal's avernic defender 5 (l) \u2192 Mor Ul Rek",
			""
		);

		assertTrue(containsLabelText(panel, "NEXT STEP"));
		assertTrue(containsLabelText(
			panel,
			"Withdraw and use Ghommal's avernic defender 5 (l)"
		));
		assertTrue(containsLabelText(
			panel,
			"Shortest Path starts after you land."
		));
	}

	@Test
	public void inactiveTaskHeaderDoesNotOverlapWithStatusCopy()
	{
		final SlayerPlusPanel panel = new SlayerPlusPanel();
		panel.showNoTask(34, 753, "Duradel");

		assertTrue(containsLabelText(panel, "CURRENT TASK"));
		assertTrue(containsLabelText(panel, "No active task"));
		assertTrue(!containsLabelText(
			panel,
			"Get a Slayer assignment to begin"
		));
	}

	@Test
	public void pointBoostStatusExpandsTheCurrentTaskCard()
	{
		final SlayerPlusPanel panel = new SlayerPlusPanel();
		panel.showTask("Araxytes", 183, 231, "Kuradal", "", 119, 768);
		final int compactHeight =
			panel.currentTaskCardMaximumHeightForRegression();

		panel.showPointBoostStatus(
			"Point boosting resumes after this existing assignment."
		);

		assertTrue(containsLabelText(
			panel,
			"Point boosting resumes after this existing assignment."
		));
		assertTrue(
			panel.currentTaskCardMaximumHeightForRegression() > compactHeight
		);
	}

	@Test
	public void noteOnlyTravelHandoffRemainsVisible()
	{
		final SlayerPlusPanel panel = new SlayerPlusPanel();
		panel.showTravelRecommendation(
			"",
			"Follow Shortest Path to the Inferno entrance."
		);

		assertTrue(containsLabelText(panel, "NEXT STEP"));
		assertTrue(containsLabelText(
			panel,
			"Follow Shortest Path to the Inferno entrance."
		));
	}

	@Test
	public void infernoEntranceHandoffUsesTheInstructionWithoutAddingUse()
	{
		final SlayerPlusPanel panel = new SlayerPlusPanel();
		panel.showTravelRecommendation(
			"Follow Shortest Path \u2192 Inferno entrance",
			"The final 28-slot setup is ready. Follow the route to TzHaar-Ket-Keh."
		);

		assertTrue(containsLabelText(
			panel,
			"Follow Shortest Path \u2192 Inferno entrance"
		));
		assertTrue(containsLabelText(
			panel,
			"The final 28-slot setup is ready. Follow the route to TzHaar-Ket-Keh."
		));
		assertFalse(containsLabelText(
			panel,
			"Use Follow Shortest Path"
		));
	}

	@Test
	public void longSidebarTextWrapsAndExpandsItsOwningCard()
	{
		final SlayerPlusPanel panel = new SlayerPlusPanel();
		panel.showTask("Bats", 10, 10, "Kuradal", "", 119, 768);
		final int shortTaskHeight =
			panel.currentTaskNamePreferredHeightForRegression();

		panel.showTask(
			"Extremely long multi-part Slayer assignment requiring clean wrapping",
			10,
			10,
			"Kuradal",
			"A particularly long assigned dungeon sub-area name",
			119,
			768,
			true
		);

		assertTrue(
			panel.currentTaskNamePreferredHeightForRegression()
				> shortTaskHeight
		);

		panel.showTravelRecommendation("Games necklace", "");
		final int shortTravelHeight =
			panel.bankTravelMaximumHeightForRegression();
		panel.showTravelRecommendation(
			"A very long teleport item instruction that must remain fully visible",
			""
		);
		assertTrue(
			panel.bankTravelMaximumHeightForRegression()
				> shortTravelHeight
		);
	}

	@Test
	public void sidebarWrappingTracksTheAvailableCardWidth()
	{
		final SlayerPlusPanel panel = new SlayerPlusPanel();
		panel.showTask(
			"A deliberately long Slayer assignment name for responsive wrapping",
			10,
			10,
			"Kuradal",
			"",
			119,
			768
		);

		panel.setCurrentTaskContainerWidthForRegression(140);
		final int narrowWrapWidth =
			panel.currentTaskRenderedWrapWidthForRegression();
		final int narrowHeight =
			panel.currentTaskNamePreferredHeightForRegression();

		panel.setCurrentTaskContainerWidthForRegression(230);
		final int wideWrapWidth =
			panel.currentTaskRenderedWrapWidthForRegression();
		final int wideHeight =
			panel.currentTaskNamePreferredHeightForRegression();

		assertTrue(wideWrapWidth > narrowWrapWidth);
		assertTrue(wideHeight < narrowHeight);
	}

	@Test
	public void currentTaskAndBankTagNamesUseCanonicalCapitalization()
	{
		for (final String reviewedName
			: SlayerTaskResearchCatalog.getReviewedTaskNames())
		{
			final String displayName = SlayerDisplayText.taskName(reviewedName);
			for (final String word : displayName.split("\\s+"))
			{
				assertTrue(
					"Task word is not title-cased: " + displayName,
					word.isEmpty() || !Character.isLetter(word.charAt(0))
						|| Character.isUpperCase(word.charAt(0))
				);
			}
		}
		assertEquals("The Whisperer", SlayerDisplayText.taskName("THE WHISPERER"));
		assertEquals("TzKal-Zuk", SlayerDisplayText.taskName("TZKAL-ZUK"));
		assertEquals("TzTok-Jad", SlayerDisplayText.taskName("TZTOK-JAD"));
		assertEquals("Kree'arra", SlayerDisplayText.taskName("KREE'ARRA"));
		assertEquals("K'ril Tsutsaroth", SlayerDisplayText.taskName("K'RIL TSUTSAROTH"));
		assertEquals("Greater Demons", SlayerDisplayText.taskName("GREATER DEMONS"));
		assertEquals(
			"Fossil Island Wyverns",
			SlayerDisplayText.taskName("FOSSIL ISLAND WYVERNS")
		);
		assertEquals(
			"Skeletal Wyverns",
			SlayerDisplayText.taskName("skeletal wyverns")
		);
		assertEquals(
			"Skeletal Wyverns",
			SlayerDisplayText.bankSetupTitle("SKELETAL WYVERNS")
		);
		assertEquals(
			"TEST: The Whisperer",
			SlayerDisplayText.taskName("test: the whisperer")
		);
		assertEquals(
			"Waiting for Slayer task",
			SlayerDisplayText.bankSetupTitle("WAITING FOR SLAYER TASK")
		);

		final SlayerPlusPanel panel = new SlayerPlusPanel();
		panel.showTask("TEST: THE WHISPERER", 3, 3, "Duradel", "", 79, 756);
		assertTrue(containsLabelText(panel, "TEST: The Whisperer"));
		assertTrue(containsLabelText(panel, "3 / 3 remaining"));

		panel.showRecommendation(
			null,
			new SlayerLoadoutPlan(
				"—",
				"—",
				"No item scan available",
				"THE WHISPERER • Lassar Undercity",
				Collections.emptyList(),
				Collections.emptyList(),
				Collections.emptyList()
			),
			true
		);
		assertTrue(containsLabelText(panel, "The Whisperer"));
		assertTrue(containsLabelText(panel, "Lassar Undercity"));
	}

	@Test
	public void ghommalMenuHighlightsDestinationsAndNeverDrop()
	{
		final String defender = "Ghommal's avernic defender 5 (l)";
		final int destinations =
			SlayerTeleportHighlighter.firstStageActionPreference(
				"Destinations",
				defender
			);
		final int teleport =
			SlayerTeleportHighlighter.firstStageActionPreference(
				"Teleport",
				defender
			);

		assertTrue(destinations > teleport);
		assertTrue(teleport > 0);
		assertTrue(SlayerTeleportHighlighter.firstStageActionPreference(
			"Drop",
			defender
		) < 0);
		assertTrue(SlayerTeleportHighlighter.firstStageActionPreference(
			"Dismantle",
			defender
		) < 0);
		assertTrue(SlayerTeleportHighlighter.firstStageActionPreference(
			"Wield",
			defender
		) < 0);
		assertTrue(SlayerTeleportHighlighter.firstStageActionPreference(
			"Use",
			defender
		) < 0);
		assertEquals(
			"Mor Ul Rek",
			SlayerTeleportRouteRegistry.menuDestination(
				defender,
				"Mor Ul Rek"
			)
		);
		assertTrue(
			SlayerTeleportHighlighter.isContextlessGhommalDestinationChoice(
				defender,
				"Mor Ul Rek",
				Collections.singleton("mor ul rek")
			)
		);
		assertTrue(
			!SlayerTeleportHighlighter.isContextlessGhommalDestinationChoice(
				defender,
				"Drop",
				Collections.singleton("mor ul rek")
			)
		);
		assertTrue(
			!SlayerTeleportHighlighter.isContextlessGhommalDestinationChoice(
				"Max cape",
				"Mor Ul Rek",
				Collections.singleton("mor ul rek")
			)
		);
		assertTrue(
			SlayerTeleportHighlighter.shouldDeferGhommalDestinationInterface(
				InterfaceID.GRAPHICAL_MULTI,
				defender
			)
		);
		assertEquals(
			InterfaceID.GRAPHICAL_MULTI,
			InterfaceID.GraphicalMulti.GRAPHICAL_MULTI_2B >>> 16
		);
		assertTrue(
			!SlayerTeleportHighlighter.shouldDeferGhommalDestinationInterface(
				InterfaceID.CHATMENU,
				defender
			)
		);
	}

	@Test
	public void maxCapeHouseDestinationKeepsEveryLiveMenuAlias()
	{
		final Set<String> aliases = SlayerTeleportRouteRegistry.destinationAliases(
			"Max cape",
			"Teleport to house"
		);

		assertTrue(aliases.contains("Home"));
		assertTrue(aliases.contains("POH"));
		assertTrue(aliases.contains("House"));
		assertTrue(aliases.contains("Tele to POH"));
		assertEquals(
			"Teleport to house",
			SlayerTeleportRouteRegistry.menuDestination(
				"Max cape",
				"Teleport to house"
			)
		);
	}

	@Test
	public void inFlightTaskTravelSelectionRemainsAuthoritative()
	{
		final SlayerTravelCoordinator coordinator = new SlayerTravelCoordinator();
		final String requestedRoute = "bloodveld|meiyerditch|live-request";
		coordinator.beginRoute(requestedRoute);
		coordinator.resolveItem(
			requestedRoute,
			ItemID.SKILLCAPE_MAX_WORN,
			"Max cape",
			"Teleport to house"
		);

		assertTrue(coordinator.currentFor(requestedRoute).hasPhysicalItem());
		assertEquals(
			"Max cape",
			coordinator.currentFor(requestedRoute).getItemName()
		);
		assertTrue(!coordinator.currentFor(
			"bloodveld|meiyerditch|later-display-resolution"
		).hasPhysicalItem());
	}

	@Test
	public void activeShortestPathRecalculationKeepsPhysicalTeleportHighlight()
	{
		final SlayerTravelSelection cape = SlayerTravelSelection.resolvedItem(
			"bloodveld-route",
			42L,
			ItemID.SKILLCAPE_MAX_WORN,
			"Max cape",
			"Teleport to house"
		);

		assertTrue(SlayerPlusPlugin.shouldRetainPinnedTravelHighlight(
			42L, "bloodveld-route", cape,
			42L, "bloodveld-route", true
		));
		assertFalse(SlayerPlusPlugin.shouldRetainPinnedTravelHighlight(
			43L, "bloodveld-route", cape,
			42L, "bloodveld-route", true
		));
		assertFalse(SlayerPlusPlugin.shouldRetainPinnedTravelHighlight(
			42L, "bloodveld-route", cape,
			42L, "bloodveld-route", false
		));
	}

	@Test
	public void ghommalHighlighterRecognizesEveryCurrentHiltVariant()
	{
		final String[] hilts =
		{
			"Ghommal's hilt 1",
			"Ghommal's hilt 2",
			"Ghommal's hilt 3",
			"Ghommal's hilt 4",
			"Ghommal's hilt 5",
			"Ghommal's hilt 6",
			"Ghommal's avernic defender 5",
			"Ghommal's avernic defender 5 (l)",
			"Ghommal's avernic defender 6",
			"Ghommal's avernic defender 6 (l)"
		};
		for (final String item : hilts)
		{
			assertTrue(item, SlayerTeleportHighlighter.isGhommalItemName(item));
			assertEquals(
				item,
				3000,
				SlayerTeleportHighlighter.firstStageActionPreference(
					"Destinations",
					item
				)
			);
		}
	}

	@Test
	public void authoritativeGhommalStagingItemSurvivesRouteRestartForBankTag()
	{
		final SlayerTravelCoordinator coordinator = new SlayerTravelCoordinator();
		final String route = "prepbank|tzkal-zuk|mor-ul-rek";
		coordinator.beginRoute(route);
		coordinator.resolveItem(
			route,
			ItemID.CA_OFFHAND_GRANDMASTER,
			"Ghommal's hilt 6",
			"Mor Ul Rek",
			Collections.singleton("Ghommal's hilt 6")
		);

		coordinator.beginRoute(route);
		final SlayerTravelSelection restored = coordinator.selectionFor(route);
		assertTrue(restored.isResolved());
		assertTrue(restored.hasPhysicalItem());
		assertEquals(ItemID.CA_OFFHAND_GRANDMASTER, restored.getItemId());
		assertEquals("Ghommal's hilt 6", restored.getItemName());
		assertEquals("Mor Ul Rek", restored.getDestination());
	}

	@Test
	public void infernoBankSnapshotPrefersBestSupportedGhommalVariant()
	{
		final int[] everyMorUlRekVariant =
		{
			ItemID.CA_OFFHAND_ELITE,
			ItemID.CA_OFFHAND_MASTER,
			ItemID.CA_OFFHAND_GRANDMASTER,
			ItemID.INFERNAL_DEFENDER_GHOMMAL_5,
			ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER,
			ItemID.INFERNAL_DEFENDER_GHOMMAL_6,
			ItemID.INFERNAL_DEFENDER_GHOMMAL_6_TROUVER
		};
		for (final int itemId : everyMorUlRekVariant)
		{
			assertEquals(
				itemId,
				SlayerPlusPlugin.preferredInfernoTravelItemId(
					Collections.singleton(itemId)
				)
			);
		}

		assertEquals(
			ItemID.CA_OFFHAND_GRANDMASTER,
			SlayerPlusPlugin.preferredInfernoTravelItemId(Arrays.asList(
				ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER,
				ItemID.CA_OFFHAND_ELITE,
				ItemID.CA_OFFHAND_GRANDMASTER
			))
		);
		assertEquals(
			ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER,
			SlayerPlusPlugin.preferredInfernoTravelItemId(
				Collections.singleton(
					ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER
				)
			)
		);
		assertEquals(
			-1,
			SlayerPlusPlugin.preferredInfernoTravelItemId(
				Collections.singleton(ItemID.COINS)
			)
		);
	}

	@Test
	public void rememberedGhommalRemainsEligibleUntilARealBankScanReplacesIt()
	{
		final int remembered = ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER;
		assertTrue(SlayerPlusPlugin.shouldUseRememberedInfernoTravelItem(
			true,
			false,
			true,
			remembered,
			remembered
		));
		assertTrue(!SlayerPlusPlugin.shouldUseRememberedInfernoTravelItem(
			true,
			true,
			true,
			remembered,
			remembered
		));
		assertTrue(!SlayerPlusPlugin.shouldUseRememberedInfernoTravelItem(
			true,
			false,
			true,
			remembered,
			ItemID.CA_OFFHAND_ELITE
		));
	}

	@Test
	public void bankTagInventoryPresentationOnlyGroupsVisualSupplies()
	{
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements =
			SlayerBankTagLayoutService.planInventoryForBankTag(
				Arrays.asList(
					item("Teleport"),
					item("Shark"),
					item("Super restore(4)"),
					item("Cannonballs"),
					item("Anti-venom+(4)"),
					item("Ranging potion(4)"),
					item("Ranged switch"),
					item("Saradomin brew(4)"),
					item("Manta ray"),
					item("Prayer potion(4)"),
					item("Shark")
				),
				-1,
				true
			);

		assertEquals(
			Arrays.asList(
				"Cannonballs",
				"Ranged switch",
				"Ranging potion(4)",
				"Anti-venom+(4)",
				"Saradomin brew(4)",
				"Prayer potion(4)",
				"Super restore(4)",
				"Manta ray",
				"Shark",
				"Shark",
				"Teleport"
			),
			namesBySlot(placements)
		);
		assertEquals(27, slotFor(placements, "Teleport"));
	}

	@Test
	public void bankTagPresentationPinsRunePouchToLastInventorySlot()
	{
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements =
			SlayerBankTagLayoutService.planInventoryForBankTag(
				Arrays.asList(
					item("First switch"),
					item("Task tool"),
					item("Rune pouch"),
					item("Cannonballs")
				),
				-1,
				false
			);

		assertEquals(0, slotFor(placements, "First switch"));
		assertEquals(1, slotFor(placements, "Cannonballs"));
		assertEquals(26, slotFor(placements, "Task tool"));
		assertEquals(27, slotFor(placements, "Rune pouch"));
	}

	@Test
	public void bankTagPresentationPlacesMatchingTopsDirectlyAboveLegs()
	{
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements =
			SlayerBankTagLayoutService.planInventoryForBankTag(
				Arrays.asList(
					item("Masori chaps (f)"),
					item("Ancestral robe bottom"),
					item("Masori body (f)"),
					item("Shark"),
					item("Ancestral robe top"),
					item("Proselyte cuisse"),
					item("Proselyte hauberk"),
					item("Divine rune pouch")
				),
				0,
				false
			);

		assertDirectlyAbove(
			placements,
			"Masori body (f)",
			"Masori chaps (f)"
		);
		assertDirectlyAbove(
			placements,
			"Ancestral robe top",
			"Ancestral robe bottom"
		);
		assertDirectlyAbove(
			placements,
			"Proselyte hauberk",
			"Proselyte cuisse"
		);
		assertEquals(27, slotFor(placements, "Divine rune pouch"));
	}

	@Test
	public void bankTagPresentationUsesCannonBlockAndBottomUtilityStrip()
	{
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements =
			SlayerBankTagLayoutService.planInventoryForBankTag(
				Arrays.asList(
					item("Teleport to house"),
					item("Cannon furnace"),
					item("Cannon base"),
					item("Cannon barrels"),
					item("Cannon stand"),
					item("Cannonballs"),
					groupItem(
						"Open herb sack",
						SlayerMethodRules.InventoryGroup.UTILITY
					),
					groupItem(
						"Book of the dead",
						SlayerMethodRules.InventoryGroup.UTILITY
					),
					item("Prayer potion(4)"),
					item("Divine rune pouch")
				),
				-1,
				true
			);

		assertEquals(0, slotFor(placements, "Cannon base"));
		assertEquals(1, slotFor(placements, "Cannon stand"));
		assertEquals(4, slotFor(placements, "Cannon barrels"));
		assertEquals(5, slotFor(placements, "Cannon furnace"));
		assertEquals(24, slotFor(placements, "Open herb sack"));
		assertEquals(25, slotFor(placements, "Book of the dead"));
		assertEquals(26, slotFor(placements, "Teleport to house"));
		assertEquals(27, slotFor(placements, "Divine rune pouch"));
	}

	@Test
	public void bankTagPresentationStacksSameStyleSwitchGearDownColumns()
	{
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements =
			SlayerBankTagLayoutService.planInventoryForBankTag(
				Arrays.asList(
					switchItem(
						"Toxic blowpipe",
						SlayerLoadoutItem.SwitchStyle.RANGED
					),
					switchItem(
						"Ancient Magicks weapon",
						SlayerLoadoutItem.SwitchStyle.MAGIC
					),
					item("Saradomin brew(4)"),
					switchItem(
						"Mage leg switch",
						SlayerLoadoutItem.SwitchStyle.MAGIC
					),
					switchItem(
						"Mage body switch",
						SlayerLoadoutItem.SwitchStyle.MAGIC
					),
					switchItem(
						"Magic off-hand switch",
						SlayerLoadoutItem.SwitchStyle.MAGIC
					),
					switchItem(
						"Magic damage switch",
						SlayerLoadoutItem.SwitchStyle.MAGIC
					),
					item("Super restore(4)"),
					item("Divine rune pouch")
				),
				-1,
				false
			);

		assertEquals(0, slotFor(placements, "Mage body switch"));
		assertEquals(4, slotFor(placements, "Mage leg switch"));
		assertEquals(1, slotFor(placements, "Ancient Magicks weapon"));
		assertEquals(5, slotFor(placements, "Magic off-hand switch"));
		assertEquals(2, slotFor(placements, "Magic damage switch"));
		assertEquals(3, slotFor(placements, "Toxic blowpipe"));
		assertEquals(6, slotFor(placements, "Saradomin brew(4)"));
		assertEquals(7, slotFor(placements, "Super restore(4)"));
		assertEquals(27, slotFor(placements, "Divine rune pouch"));
	}

	@Test
	public void everyCurrentTaskHasAuditedTravelEquipmentAndInventoryRules()
	{
		for (final String task : SlayerTaskStrategyCatalog.getCurrentTaskNames())
		{
			final SlayerTaskTravelAuditCatalog.Entry travel =
				SlayerTaskTravelAuditCatalog.find(task);
			assertNotNull(task + " has no reviewed fallback travel", travel);
			assertTrue(task + " has blank travel text", !travel.getTravel().isEmpty());
			assertTrue(
				task + " has no concrete physical teleport route for " + travel.getLocation(),
				!SlayerTravelRouteCatalog.fallbacksFor(travel.getLocation()).isEmpty()
			);
			assertTrue(
				task + " still contains a placeholder travel instruction",
				!travel.getTravel().toLowerCase(java.util.Locale.ENGLISH)
					.contains("nearest reviewed")
			);

			final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				task,
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				travel.getLocation(),
				false
			);
			assertTrue(task + " has no reviewed strategy", strategy.isReviewed());
			assertTrue(
				task + " has incomplete non-weapon equipment progression",
				SlayerEquipmentAuditCatalog.auditedSlotsForRegression(
					task, strategy, strategy.getWeaponPriorities().isEmpty()
						? "" : strategy.getWeaponPriorities().get(0)
				).size() >= 8
			);
			final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
				task, travel.getLocation(), strategy
			);
			assertTrue(task + " has unreviewed inventory rules", rules.isReviewed());
			assertTrue(task + " has no inventory target", rules.getInventoryTarget() > 0);

			final SlayerRouteCatalog.RouteProfile route = SlayerRouteCatalog.resolve(
				task, travel.getLocation(), strategy.isBoss()
			);
			assertNotNull(task + " has no Shortest Path route", route);
			assertTrue(
				task + " still requires the encounter NPC to be loaded before routing",
				route.hasStaticDestination()
			);

			if (!strategy.isBoss())
			{
				assertTrue(
					task + " has no individually registered inventory policy",
					SlayerRegularInventoryAuditCatalog.hasPolicy(task)
				);
			}
		}
	}

	@Test
	public void wikiEquipmentProgressionsReachOwnedTiersAndDriveBankTags()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"General melee Slayer"
		).weapons("Abyssal whip").build();
		final List<String> meleeHead = SlayerEquipmentAuditCatalog.priorities(
			"Dagannoth", melee, EquipmentInventorySlot.HEAD, "Abyssal whip"
		);
		assertEquals("slayer helmet i", meleeHead.get(0));
		assertTrue(meleeHead.indexOf("slayer helmet")
			> meleeHead.indexOf("black mask i"));

		final List<String> meleeBody = SlayerEquipmentAuditCatalog.priorities(
			"Dagannoth", melee, EquipmentInventorySlot.BODY, "Abyssal whip"
		);
		assertTrue(meleeBody.indexOf("bandos chestplate")
			< meleeBody.indexOf("fighter torso"));
		assertTrue(meleeBody.indexOf("fighter torso")
			< meleeBody.indexOf("rune platebody"));

		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"General ranged Slayer"
		).weapons("Toxic blowpipe").build();
		final List<String> rangedBody = SlayerEquipmentAuditCatalog.priorities(
			"Bloodveld", ranged, EquipmentInventorySlot.BODY, "Toxic blowpipe"
		);
		assertEquals("masori body f", rangedBody.get(0));
		assertTrue(rangedBody.indexOf("blessed body")
			< rangedBody.indexOf("black d hide body"));
		assertTrue(rangedBody.contains("green d hide body"));

		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"General magic Slayer"
		).weapons("Trident of the seas").build();
		final List<String> magicBody = SlayerEquipmentAuditCatalog.priorities(
			"Metal dragon", magic, EquipmentInventorySlot.BODY,
			"Trident of the seas"
		);
		assertEquals("virtus robe top", magicBody.get(0));
		assertTrue(magicBody.indexOf("mystic robe top")
			< magicBody.indexOf("xerician top"));

		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Saradomin d'hide body", "Blessed body"
		));
		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Imbued Guthix cape", "Imbued god cape"
		));
		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Staff of fire", "Elemental staff"
		));

		final List<String> weapons =
			SlayerLoadoutAnalyzer.weaponProgressionForRegression(melee);
		assertEquals("Abyssal whip", weapons.get(0));
		assertTrue(weapons.contains("rune scimitar"));
		assertTrue(weapons.contains("iron scimitar"));
	}

	@Test
	public void wildernessAndStrictProfilesDoNotReceiveUnsafeGearFallbacks()
	{
		final SlayerTaskStrategy wilderness = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Wilderness ranged Slayer"
		).costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons("Webweaver bow")
			.build();
		final List<String> wildernessBodies =
			SlayerEquipmentAuditCatalog.priorities(
				"Revenants", wilderness, EquipmentInventorySlot.BODY,
				"Webweaver bow"
			);
		assertEquals("black d hide body", wildernessBodies.get(0));
		assertTrue(!wildernessBodies.contains("masori body f"));

		final SlayerTaskStrategy strict = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Mechanic-specific ranged method"
		).weapons("Dragon hunter crossbow")
			.strictWeaponProfile(true)
			.build();
		final List<String> strictWeapons =
			SlayerLoadoutAnalyzer.weaponProgressionForRegression(strict);
		assertEquals(1, strictWeapons.size());
		assertEquals("Dragon hunter crossbow", strictWeapons.get(0));

		final SlayerTaskStrategy boss = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Ranged boss method"
		).boss(true).build();
		final List<String> bossGloves = SlayerEquipmentAuditCatalog.priorities(
			"Vorkath", boss, EquipmentInventorySlot.GLOVES, ""
		);
		assertEquals("zaryte vambraces", bossGloves.get(0));
		assertTrue(!bossGloves.contains("expeditious bracelet"));
		assertTrue(!bossGloves.contains("bracelet of slaughter"));
	}

	@Test
	public void upgradedAndCosmeticEquipmentVariantsKeepTheirRealProgressionTier()
	{
		final List<String> masori = Arrays.asList(
			"masori body f", "masori body", "blessed body"
		);
		assertEquals(
			0,
			SlayerLoadoutAnalyzer.directEquipmentProgressionRankForRegression(
				"Masori body (f)", masori
			)
		);
		assertEquals(
			1,
			SlayerLoadoutAnalyzer.directEquipmentProgressionRankForRegression(
				"Masori body", masori
			)
		);
		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Radiant oathplate chest", "Oathplate chest"
		));
		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Imbued Saradomin max cape", "Imbued god cape"
		));
		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Infernal max cape", "Infernal cape"
		));
		assertEquals(
			"Infernal max cape",
			SlayerLoadoutAnalyzer.preferredInventoryEquipmentForRegression(
				Arrays.asList("Fire cape", "Infernal max cape"),
				"infernal cape", "fire cape", "mythical cape"
			)
		);
		assertEquals(
			"Imbued Saradomin max cape",
			SlayerLoadoutAnalyzer.preferredInventoryEquipmentForRegression(
				Arrays.asList("God cape", "Imbued Saradomin max cape"),
				"imbued god cape", "god cape"
			)
		);
		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Dizana's max cape", "Dizana's quiver"
		));
		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Masori assembler max cape", "Ava's assembler"
		));
		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Saradomin coif", "Blessed coif"
		));
		assertTrue(SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForRegression(
			"Dharok's platebody 50", "Barrows platebody"
		));
		assertTrue(!SlayerLoadoutAnalyzer.usableEquipmentVariantForRegression(
			"Dharok's platebody 0", EquipmentInventorySlot.BODY
		));
		assertTrue(!SlayerLoadoutAnalyzer.usableEquipmentVariantForRegression(
			"Scythe of vitur (uncharged)", EquipmentInventorySlot.WEAPON
		));
		assertTrue(SlayerLoadoutAnalyzer.usableEquipmentVariantForRegression(
			"Dizana's quiver (uncharged)", EquipmentInventorySlot.CAPE
		));

		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Current ranged progression"
		).build();
		final List<String> rangedBoots = SlayerEquipmentAuditCatalog.priorities(
			"Wyrms", ranged, EquipmentInventorySlot.BOOTS, "Toxic blowpipe"
		);
		assertTrue(rangedBoots.indexOf("avernic treads pe")
			< rangedBoots.indexOf("avernic treads"));
		assertTrue(rangedBoots.indexOf("avernic treads")
			< rangedBoots.indexOf("pegasian boots"));

		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Current magic progression"
		).build();
		assertTrue(
			SlayerEquipmentAuditCatalog.weaponProgression(magic)
				.indexOf("eye of ayak")
				< SlayerEquipmentAuditCatalog.weaponProgression(magic)
					.indexOf("sanguinesti staff")
		);
	}

	@Test
	public void unresolvedShortestPathStillKeepsReviewedTeleportInFourBySevenGrid()
	{
		final SlayerLoadoutItem reviewedTeleport = new SlayerLoadoutItem(
			"Slayer ring (8)",
			ItemID.SLAYER_RING_8,
			1,
			SlayerLoadoutItem.Status.BANK
		);
		assertTrue(!SlayerBankTagLayoutService.shouldSuppressInventoryTravelItem(
			true,
			0,
			-1,
			reviewedTeleport,
			SlayerTravelSelection.unresolved("route", 1L)
		));
		assertTrue(SlayerBankTagLayoutService.shouldSuppressInventoryTravelItem(
			true,
			0,
			ItemID.SLAYER_RING_8,
			reviewedTeleport,
			SlayerTravelSelection.unresolved("route", 1L)
		));

		final String teleportName = "Slayer ring (8)";
		final String pouchName = "Divine rune pouch";
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements =
			SlayerBankTagLayoutService.planInventoryForBankTag(
				Arrays.asList(
					new SlayerLoadoutItem(
						teleportName, ItemID.SLAYER_RING_8, 1,
						SlayerLoadoutItem.Status.BANK
					),
					new SlayerLoadoutItem(
						"Shark", ItemID.SHARK, 1,
						SlayerLoadoutItem.Status.BANK
					),
					new SlayerLoadoutItem(
						pouchName, ItemID.DIVINE_RUNE_POUCH, 1,
						SlayerLoadoutItem.Status.BANK
					)
				),
				-1,
				true
			);
		assertEquals(26, slotFor(placements, teleportName));
		assertEquals(27, slotFor(placements, pouchName));
	}

	@Test
	public void wikiCorrectionsKeepFireGiantsTzhaarAndBrutusExplicit()
	{
		final SlayerTaskStrategy fireGiants = SlayerTaskStrategyCatalog.resolve(
			"Fire giants", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.ALLOW, SlayerPreference.Burst.ALLOW,
			SlayerPreference.CombatStyle.AUTOMATIC, "Catacombs of Kourend", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MAGIC, fireGiants.getCombatStyle());
		assertTrue(fireGiants.getMethod().contains("Water"));

		final SlayerTaskStrategy tzhaar = SlayerTaskStrategyCatalog.resolve(
			"Tzhaar", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.ALLOW,
			SlayerPreference.CombatStyle.AUTOMATIC, "Mor Ul Rek", false
		);
		assertTrue(tzhaar.getMethod().contains("Blood Barrage"));

		final SlayerTaskStrategy brutus = SlayerTaskStrategyCatalog.resolve(
			"Brutus", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Lumbridge cow field", false
		);
		final SlayerMethodRules brutusRules = SlayerMethodRuleCatalog.resolve(
			"Brutus", "Lumbridge cow field", brutus
		);
		assertEquals("prayer potion", brutusRules.getPrimaryRestoreFamily());
		assertEquals(0, brutusRules.resolveRestoreSlots(brutus));
		assertTrue(brutusRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Divine ranging potion")
				&& item.getSlotCount() == 1
		));
		assertTrue(brutusRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Optional cooking tool")
		));
	}

	@Test
	public void lowLevelWikiMethodsRemainVisibleInGeneratedLoadouts()
	{
		final SlayerTaskStrategy birds = SlayerTaskStrategyCatalog.resolve(
			"Birds", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.ALLOW, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Lumbridge area", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED, birds.getCombatStyle());
		assertTrue(birds.getMethod().contains("flying variants"));

		final SlayerTaskStrategy rats = SlayerTaskStrategyCatalog.resolve(
			"Rats", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Lumbridge area", false
		);
		assertEquals("bone mace", rats.getWeaponPriorities().get(0));

		final SlayerTaskStrategy wolves = SlayerTaskStrategyCatalog.resolve(
			"Wolves", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.ALLOW, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Feldip Hills", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED, wolves.getCombatStyle());

		final SlayerMethodRules crocodiles = SlayerMethodRuleCatalog.resolve(
			"Crocodiles", "Nardah desert",
			SlayerTaskStrategyCatalog.resolve(
				"Crocodiles", SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
				SlayerPreference.CombatStyle.AUTOMATIC, "Nardah desert", false
			)
		);
		assertTrue(crocodiles.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Desert heat protection")
		));
	}

	@Test
	public void newerSlayerContentUsesItsActualWikiRoutesAndSupplies()
	{
		final SlayerTaskTravelAuditCatalog.Entry aquaniteTravel =
			SlayerTaskTravelAuditCatalog.find("Aquanites");
		assertEquals("Ynysdail Cavern", aquaniteTravel.getLocation());
		assertEquals("Not allowed", aquaniteTravel.getCannon());
		assertTrue(!SlayerTravelRouteCatalog.fallbacksFor("Ynysdail Cavern").isEmpty());
		final SlayerRouteCatalog.RouteProfile aquaniteRoute =
			SlayerRouteCatalog.resolve("Aquanites", "Ynysdail Cavern", false);
		assertNotNull(aquaniteRoute);
		assertTrue(aquaniteRoute.isStaged());
		assertEquals(new WorldPoint(2218, 3424, 0), aquaniteRoute.getSurfaceAccess());
		assertEquals(new WorldPoint(2275, 9880, 0), aquaniteRoute.getPrimaryDestination());
		assertEquals(
			new WorldPoint(2218, 3477, 0),
			SlayerPlusPlugin.aquaniteIslandDestinationForRegression(
				"Aquanites", "Ynysdail Cavern", new WorldPoint(2227, 3469, 0)
			)
		);
		assertEquals(
			null,
			SlayerPlusPlugin.aquaniteIslandDestinationForRegression(
				"Aquanites", "Ynysdail Cavern", new WorldPoint(2218, 3425, 0)
			)
		);
		assertFalse(SlayerPlusPlugin.hasBuiltYnysdailRowboatForRegression(0));
		assertTrue(SlayerPlusPlugin.hasBuiltYnysdailRowboatForRegression(1));
		assertEquals(
			new WorldPoint(1889, 3292, 0),
			SlayerPlusPlugin.aquaniteSailingDepartureForRegression(
				"Aquanites", "Ynysdail Cavern",
				new WorldPoint(3200, 3200, 0), false
			)
		);
		assertEquals(
			null,
			SlayerPlusPlugin.aquaniteSailingDepartureForRegression(
				"Aquanites", "Ynysdail Cavern",
				new WorldPoint(3200, 3200, 0), true
			)
		);
		assertTrue(
			SlayerPlusPlugin.shouldRejectTravelItemForSpellbookRouteForRegression(
				true, "Hallowed crystal shard"
			)
		);
		assertFalse(
			SlayerPlusPlugin.shouldRejectTravelItemForSpellbookRouteForRegression(
				false, "Spider cave teleport"
			)
		);
		assertTrue(
			SlayerPlusPlugin.shouldRejectTravelItemForSpellbookRouteForRegression(
				false, "Hallowed crystal shard"
			)
		);
		assertFalse(SlayerTravelRouteCatalog.fallbacksFor(
			"Morytania Spider Cave"
		).stream().anyMatch(option -> option.getItemFamily().equalsIgnoreCase(
			"Hallowed crystal shard"
		)));
		assertTrue(SlayerPlusPlugin.pohSpellbookTravelFamiliesForRegression()
			.contains("teleport to house"));
		assertEquals(
			null,
			SlayerPlusPlugin.aquaniteIslandDestinationForRegression(
				"Aquanites", "Ynysdail Cavern", new WorldPoint(2300, 3400, 0)
			)
		);

		final SlayerRouteCatalog.RouteProfile amoxliatlRoute =
			SlayerRouteCatalog.resolve("Amoxliatl", "Ruins of Tapoyauik", true);
		assertNotNull(amoxliatlRoute);
		assertTrue(amoxliatlRoute.isStaged());
		assertEquals(new WorldPoint(1641, 3221, 0), amoxliatlRoute.getSurfaceAccess());
		assertEquals(new WorldPoint(1362, 4511, 0), amoxliatlRoute.getPrimaryDestination());

		final SlayerTaskTravelAuditCatalog.Entry stalkerTravel =
			SlayerTaskTravelAuditCatalog.find("Custodian Stalkers");
		assertEquals("Stalker Den", stalkerTravel.getLocation());
		assertTrue(stalkerTravel.getTravel().contains("AIS"));

		final SlayerTaskTravelAuditCatalog.Entry shellbaneTravel =
			SlayerTaskTravelAuditCatalog.find("The Shellbane Gryphon");
		assertEquals("Shellbane Gryphon Cave", shellbaneTravel.getLocation());
		assertTrue(shellbaneTravel.getTravel().contains("CJQ"));

		final SlayerTaskStrategy maggot = SlayerTaskStrategyCatalog.resolve(
			"The Maggot King", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Maggot King's lair", false
		);
		final SlayerMethodRules maggotRules = SlayerMethodRuleCatalog.resolve(
			"The Maggot King", "Maggot King's lair", maggot
		);
		assertTrue(maggotRules.getPouchRunes().stream().anyMatch(
			rune -> rune.getItemId() == ItemID.WRATHRUNE
		));
		assertTrue(maggotRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Surge potion")
		));
		assertEquals("harmonised nightmare staff", maggot.getWeaponPriorities().get(0));
		assertTrue(maggot.getWeaponPriorities().contains("tumeken s shadow"));
		assertFalse(maggot.getOptionalItemPriorities().contains("venator bow"));
		for (final String required : Arrays.asList(
			"Crush punish weapon", "Crush special attack weapon",
			"Melee amulet switch", "Melee cape switch", "Melee body switch",
			"Melee legs switch", "Melee glove switch", "Melee boot switch",
			"Melee defender switch", "Poison protection",
			"Emergency teleport"))
		{
			assertTrue(required, maggotRules.getRequiredItems().stream().anyMatch(
				item -> item.getDisplayName().equals(required)
			));
		}
		assertTrue(maggotRules.fillsRemainingWithFood());
		assertEquals(0, maggotRules.getReservedLootSlots());
		final SlayerMethodRules.RequiredItem maggotCape = maggotRules
			.getRequiredItems().stream()
			.filter(item -> item.getDisplayName().equals("Melee cape switch"))
			.findFirst().orElseThrow(AssertionError::new);
		assertEquals("infernal cape", maggotCape.getAlternatives().get(0));
		assertTrue(maggotCape.getAlternatives().indexOf("infernal cape")
			< maggotCape.getAlternatives().indexOf("fire cape"));
	}

	@Test
	public void genericOneByOneMeleeTargetsNeverInheritScytheFallback()
	{
		final SlayerTaskStrategy bats = SlayerTaskStrategyCatalog.resolve(
			"Bats", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Feldip Hills", false
		);
		assertFalse(
			SlayerLoadoutAnalyzer.weaponProgressionForRegression("Bats", bats)
				.contains("scythe of vitur")
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED, bats.getCombatStyle());
		assertTrue(bats.getWeaponPriorities().contains("toxic blowpipe"));
		assertTrue(bats.getWeaponPriorities().contains("shortbow"));
		assertFalse(bats.getWeaponPriorities().contains("blade of saeldor"));
		assertFalse(SlayerTargetFootprintCatalog.isReviewedMultiTileScytheTarget("Bats"));

		final SlayerTaskStrategy araxxor = SlayerTaskStrategyCatalog.resolve(
			"Araxxor", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Morytania Spider Cave", false
		);
		assertEquals("scythe of vitur", araxxor.getWeaponPriorities().get(0));
		assertTrue(
			SlayerLoadoutAnalyzer.weaponProgressionForRegression("Araxxor", araxxor)
				.contains("scythe of vitur")
		);
		assertTrue(SlayerTargetFootprintCatalog.isReviewedMultiTileScytheTarget("Araxxor"));

		final SlayerTaskStrategy accidentalOneByOne = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Regression profile with an accidentally authored Scythe"
		)
			.weapons("scythe of vitur", "abyssal whip")
			.strictWeaponProfile(true)
			.build();
		assertEquals(
			Collections.singletonList("abyssal whip"),
			SlayerLoadoutAnalyzer.weaponProgressionForRegression(
				"Unreviewed one tile target", accidentalOneByOne
			)
		);
	}

	@Test
	public void everyReviewedAccessLocationHasAConcreteTravelBranch()
	{
		/* Inferno uses SlayerPlus' reviewed Ghommal-hilt/manual preparation flow
		 * instead of a generic TravelRouteCatalog fallback. */
		final Set<String> intentionallyLocal = Collections.singleton("inferno");
		final List<String> missing = new ArrayList<>();
		for (final String location : SlayerRouteCatalog.reviewedAccessLocationsForRegression())
		{
			if (!intentionallyLocal.contains(location)
				&& SlayerTravelRouteCatalog.fallbacksFor(location).isEmpty())
			{
				missing.add(location);
			}
		}
		assertTrue("Missing reviewed travel branches: " + missing, missing.isEmpty());
	}

	@Test
	public void augustEncounterPatchKeepsExactGearSuppliesAndDungeonAnchors()
	{
		final SlayerTaskStrategy aquanites = SlayerTaskStrategyCatalog.resolve(
			"Aquanites", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Ynysdail Cavern", false
		);
		final SlayerMethodRules aquaniteRules = SlayerMethodRuleCatalog.resolve(
			"Aquanites", "Ynysdail Cavern", aquanites
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MELEE, aquanites.getCombatStyle());
		assertTrue(aquanites.needsRunePouch());
		assertTrue(aquaniteRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Fast Slash lure-severing weapon")
		));
		assertTrue(aquaniteRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Seed box")
		));
		assertTrue(aquaniteRules.getPouchRunes().stream().anyMatch(
			rune -> rune.getItemId() == ItemID.NATURERUNE
		));

		final SlayerTaskStrategy artio = SlayerTaskStrategyCatalog.resolve(
			"Artio", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Hunter's End", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED, artio.getCombatStyle());
		assertEquals("webweaver bow", artio.getWeaponPriorities().get(0));

		final SlayerRouteCatalog.RouteProfile sire = SlayerRouteCatalog.resolve(
			"Abyssal Sire", "Abyssal Nexus", true
		);
		assertNotNull(sire);
		assertEquals(new WorldPoint(3037, 4763, 0), sire.getPrimaryDestination());

		final SlayerRouteCatalog.RouteProfile araxxor = SlayerRouteCatalog.resolve(
			"Araxxor", "Morytania Spider Cave", true
		);
		assertNotNull(araxxor);
		assertEquals(new WorldPoint(3657, 3407, 0), araxxor.getSurfaceAccess());
		assertEquals(new WorldPoint(3632, 9815, 0), araxxor.getPrimaryDestination());

		final SlayerMethodRules kree = bossInventoryRules("Kree'arra");
		assertEquals(9, kree.resolveRestoreSlots(null));
		assertTrue(kree.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Saradomin brew")
				&& item.getSlotCount() == 6
		));

		final SlayerMethodRules zilyana = bossInventoryRules("Commander Zilyana");
		assertEquals(8, zilyana.resolveRestoreSlots(null));
		assertTrue(zilyana.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Bones to peaches")
		));

		final SlayerMethodRules araxxorInventory = bossInventoryRules("Araxxor");
		assertTrue(araxxorInventory.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Combo food")
				&& item.getSlotCount() == 2
		));
		assertTrue(araxxorInventory.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Safe araxyte weapon")
				&& item.getAlternatives().contains("heavy ballista")
		));
		assertFalse(araxxorInventory.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Safe araxyte ammunition")
		));
		assertEquals(
			"dragon javelin",
			SlayerLoadoutAnalyzer.araxxorSafeAmmunitionForRegression(
				"Heavy ballista"
			).get(0)
		);
		assertTrue(
			SlayerLoadoutAnalyzer.araxxorSafeAmmunitionForRegression(
				"Noxious halberd"
			).isEmpty()
		);
		assertTrue(araxxorInventory.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Divine ranging potion")
		));
		assertTrue(araxxorInventory.fillsRemainingWithFood());
		assertEquals(2, araxxorInventory.getReservedLootSlots());
		final SlayerTaskStrategy araxxorStrategy =
			SlayerTaskStrategyCatalog.resolve(
				"Araxxor", SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Morytania Spider Cave", false
			);
		assertEquals("inquisitor s hauberk", SlayerEquipmentAuditCatalog
			.priorities(
				"Araxxor", araxxorStrategy,
				EquipmentInventorySlot.BODY, "Scythe of vitur"
			).get(0));
		assertEquals("oathplate chest", SlayerEquipmentAuditCatalog
			.priorities(
				"Araxxor", araxxorStrategy,
				EquipmentInventorySlot.BODY, "Soulreaper axe"
			).get(0));
		assertEquals("ultor ring", SlayerEquipmentAuditCatalog
			.priorities(
				"Araxxor", araxxorStrategy,
				EquipmentInventorySlot.RING, "Scythe of vitur"
			).get(0));

		final SlayerTaskStrategy blackDragons = SlayerTaskStrategyCatalog.resolve(
			"Black dragons", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Taverley Dungeon", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED, blackDragons.getCombatStyle());
		assertEquals("dragon hunter crossbow", blackDragons.getWeaponPriorities().get(0));
		assertFalse(blackDragons.getWeaponPriorities().contains("twisted bow"));
		assertFalse(blackDragons.getWeaponPriorities().contains("toxic blowpipe"));

		final SlayerTaskStrategy blackKnights = SlayerTaskStrategyCatalog.resolve(
			"Black Knights", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Black Knights' Fortress", false
		);
		final SlayerMethodRules blackKnightRules = SlayerMethodRuleCatalog.resolve(
			"Black Knights", "Black Knights' Fortress", blackKnights
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED, blackKnights.getCombatStyle());
		assertEquals(0, blackKnightRules.resolveRestoreSlots(blackKnights));
		assertEquals(2, blackKnightRules.resolveFoodSlots(blackKnights, 0));
	}

	@Test
	public void grotesqueGuardiansUseCompleteReviewedHybridLoadout()
	{
		assertTrue(SlayerLoadoutAnalyzer
			.requiresGenericRockHammerForRegression("Gargoyles"));
		assertFalse(SlayerLoadoutAnalyzer
			.requiresGenericRockHammerForRegression("The Grotesque Guardians"));

		final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
			"The Grotesque Guardians", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC, "Slayer Tower rooftop", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.HYBRID, strategy.getCombatStyle());
		assertEquals("scythe of vitur", strategy.getWeaponPriorities().get(0));
		assertTrue(strategy.getWeaponPriorities().contains("noxious halberd"));
		assertFalse(strategy.getWeaponPriorities().contains("inquisitor s mace"));

		final SlayerMethodRules inventory = bossInventoryRules("Grotesque Guardians");
		assertEquals(9, inventory.resolveRestoreSlots(strategy));
		assertEquals(6, inventory.resolveFoodSlots(strategy, 0));
		for (final String required : Arrays.asList(
			"Dawn primary ranged weapon",
			"Ranged necklace switch", "Ranged cape switch",
			"Ranged body switch", "Ranged legs switch",
			"Rock hammer"))
		{
			assertTrue(required, inventory.getRequiredItems().stream().anyMatch(
				item -> item.getDisplayName().equals(required)
			));
		}

		final List<String> bodies = SlayerEquipmentAuditCatalog.priorities(
			"The Grotesque Guardians", strategy,
			EquipmentInventorySlot.BODY, "scythe of vitur"
		);
		assertEquals("torva platebody", bodies.get(0));
		assertTrue(bodies.contains("fighter torso"));
		final List<String> rings = SlayerEquipmentAuditCatalog.priorities(
			"The Grotesque Guardians", strategy,
			EquipmentInventorySlot.RING, "scythe of vitur"
		);
		assertEquals("ultor ring", rings.get(0));
		assertTrue(rings.contains("lightbearer"));

		final SlayerMethodRules complete = SlayerMethodRuleCatalog.resolve(
			"The Grotesque Guardians", "Slayer Tower rooftop", strategy
		);
		assertEquals(SlayerMethodRules.Spellbook.ARCEUUS, complete.getSpellbook());
		assertTrue(complete.requiresRunePouch());
		assertTrue(complete.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Book of the dead")
		));
	}

	@Test
	public void diaryTeleportsAreGatedByClaimedTier()
	{
		final Map<String, Integer> tiers = new HashMap<>();
		tiers.put("lumbridge", 2);
		tiers.put("wilderness", 3);
		final SlayerAchievementDiarySnapshot snapshot =
			SlayerAchievementDiarySnapshot.forRegression(tiers);
		assertTrue(snapshot.allowsTravelItem("Explorer's ring 2"));
		assertFalse(snapshot.allowsTravelItem("Explorer's ring 3"));
		assertTrue(snapshot.allowsTravelItem("Wilderness sword 3"));
		assertFalse(snapshot.allowsTravelItem("Wilderness sword 4"));
		assertTrue(snapshot.allowsTravelItem("Karamja gloves 4"));
		assertFalse(snapshot.allowsTravelItem("Achievement diary cape"));

		final Map<String, Integer> allElite = new HashMap<>();
		for (final String region : Arrays.asList(
			"ardougne", "desert", "falador", "fremennik", "kandarin",
			"karamja", "kourend", "lumbridge", "morytania", "varrock",
			"western", "wilderness"))
		{
			allElite.put(region, 4);
		}
		assertTrue(SlayerAchievementDiarySnapshot.forRegression(allElite)
			.allowsTravelItem("Achievement diary cape"));
	}

	@Test
	public void diaryLoadoutBenefitsAreTierGated()
	{
		final SlayerAchievementDiarySnapshot none =
			SlayerAchievementDiarySnapshot.empty();
		assertFalse(none.unlocksAshSanctifier());
		assertFalse(none.unlocksBonecrusher());
		assertFalse(none.unlocksGiantMoleLocator());
		assertFalse(none.removesKaruulmBootRequirement());
		assertTrue(none.allowsTravelItem("Dramen staff"));

		final Map<String, Integer> tiers = new HashMap<>();
		tiers.put("falador", 3);
		tiers.put("fremennik", 4);
		tiers.put("kandarin", 3);
		tiers.put("karamja", 4);
		tiers.put("kourend", 4);
		tiers.put("lumbridge", 4);
		tiers.put("morytania", 3);
		tiers.put("western", 4);
		tiers.put("wilderness", 4);
		final SlayerAchievementDiarySnapshot unlocked =
			SlayerAchievementDiarySnapshot.forRegression(tiers);

		assertTrue(unlocked.unlocksAshSanctifier());
		assertTrue(unlocked.unlocksBonecrusher());
		assertTrue(unlocked.unlocksGiantMoleLocator());
		assertTrue(unlocked.removesKaruulmBootRequirement());
		assertTrue(unlocked.improvesEnchantedBoltSpecials());
		assertTrue(unlocked.improvesBarrowsRuneRewards());
		assertTrue(unlocked.hasFightCavesDailyResurrection());
		assertTrue(unlocked.hasZulrahDailyResurrection());
		assertTrue(unlocked.notesAviansieAdamantBars());
		assertTrue(unlocked.notesDagannothKingBones());
		assertTrue(unlocked.notesBrimhavenDungeonDrops());
		assertTrue(unlocked.notesWildernessDragonBones());
		assertEquals(75, unlocked.slayerTowerExperienceBonusTenthsPercent());
		assertFalse(unlocked.allowsTravelItem("Dramen staff"));
		assertFalse(unlocked.allowsTravelItem("Lunar staff"));
	}

	@Test
	public void diaryUtilityPlaceholdersRequireTheirUnlockingDiary()
	{
		final SlayerAchievementDiarySnapshot none =
			SlayerAchievementDiarySnapshot.empty();
		assertFalse(none.allowsLoadoutReward(
			"Ash sanctifier", Arrays.asList("ash sanctifier")
		));
		assertFalse(none.allowsLoadoutReward(
			"Bonecrusher", Arrays.asList("bonecrusher")
		));
		assertFalse(none.allowsLoadoutReward(
			"Mole locator", Arrays.asList("falador shield 3")
		));

		final Map<String, Integer> tiers = new HashMap<>();
		tiers.put("falador", 3);
		tiers.put("kourend", 3);
		tiers.put("morytania", 3);
		final SlayerAchievementDiarySnapshot unlocked =
			SlayerAchievementDiarySnapshot.forRegression(tiers);
		assertTrue(unlocked.allowsLoadoutReward(
			"Ash sanctifier", Arrays.asList("ash sanctifier")
		));
		assertTrue(unlocked.allowsLoadoutReward(
			"Bonecrusher", Arrays.asList("bonecrusher")
		));
		assertTrue(unlocked.allowsLoadoutReward(
			"Mole locator", Arrays.asList("falador shield 3")
		));

		assertTrue(SlayerLoadoutAnalyzer
			.requiresKaruulmProtectionBootsForRegression(
				"Alchemical Hydra", "Mount Karuulm", false
			));
		assertFalse(SlayerLoadoutAnalyzer
			.requiresKaruulmProtectionBootsForRegression(
				"Alchemical Hydra", "Mount Karuulm", true
			));
	}

	@Test
	public void wikiElementalWeaknessesAreRealMagicPreferenceBranches()
	{
		assertTrue(SlayerElementalWeaknessCatalog.sizeForRegression() >= 50);

		final SlayerTaskStrategy blackDemonMagic = SlayerTaskStrategyCatalog.resolve(
			"Black demons", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_MAGIC,
			"Catacombs of Kourend", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MAGIC, blackDemonMagic.getCombatStyle());
		assertTrue(blackDemonMagic.getMethod().contains("40% Water weakness"));

		final SlayerTaskStrategy blackDemonAutomatic = SlayerTaskStrategyCatalog.resolve(
			"Black demons", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Catacombs of Kourend", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MELEE, blackDemonAutomatic.getCombatStyle());

		final SlayerTaskStrategy waterfiends = SlayerTaskStrategyCatalog.resolve(
			"Waterfiends", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Ancient Cavern", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MAGIC, waterfiends.getCombatStyle());
		assertTrue(waterfiends.getMethod().contains("100% Earth weakness"));
	}

	@Test
	public void auditedCannonAlternativeHonorsPreferAndNever()
	{
		final SlayerTaskStrategy preferred = SlayerTaskStrategyCatalog.resolve(
			"Hill giants", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.PREFER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Edgeville Dungeon", false
		);
		assertTrue(preferred.hasTag(SlayerTaskStrategy.MethodTag.CANNON));

		final SlayerTaskStrategy disabled = SlayerTaskStrategyCatalog.resolve(
			"Hill giants", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Edgeville Dungeon", false
		);
		assertTrue(!disabled.hasTag(SlayerTaskStrategy.MethodTag.CANNON));
	}

	@Test
	public void bloodveldLocationsUseTheirReviewedGearAndInventoryBranches()
	{
		final SlayerTaskStrategy meiyerditch = SlayerTaskStrategyCatalog.resolve(
			"Bloodveld", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.ALLOW, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Meiyerditch Laboratories", false
		);
		assertEquals("venator bow", meiyerditch.getWeaponPriorities().get(0));
		assertEquals(SlayerTaskStrategy.ArmourFocus.DAMAGE,
			meiyerditch.getArmourFocus());
		assertTrue(meiyerditch.hasTag(SlayerTaskStrategy.MethodTag.CANNON));
		assertTrue(meiyerditch.hasTag(SlayerTaskStrategy.MethodTag.VENATOR));
		assertEquals(SlayerTaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED,
			meiyerditch.getDamageProfile());

		final SlayerMethodRules meiyerditchRules = SlayerMethodRuleCatalog.resolve(
			"Bloodveld", "Meiyerditch Laboratories", meiyerditch
		);
		assertEquals(6, meiyerditchRules.resolveRestoreSlots(meiyerditch));
		assertEquals(0, meiyerditchRules.resolveFoodSlots(meiyerditch, 0));
		assertTrue(meiyerditchRules.usesCannon());
		assertEquals(2000, meiyerditchRules.getCannonballQuantity());
		assertTrue(meiyerditchRules.requiresRunePouch());
		assertEquals(ItemID.NATURERUNE,
			meiyerditchRules.getPouchRunes().get(0).getItemId());
		assertEquals(ItemID.FIRERUNE,
			meiyerditchRules.getPouchRunes().get(1).getItemId());
		assertTrue(meiyerditchRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Ash sanctifier")
		));
		assertTrue(meiyerditchRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Soul bearer")
		));

		final SlayerTaskStrategy catacombs = SlayerTaskStrategyCatalog.resolve(
			"Bloodveld", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.PREFER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Catacombs of Kourend", false
		);
		assertTrue(catacombs.hasTag(SlayerTaskStrategy.MethodTag.VENATOR));
		assertTrue(!catacombs.hasTag(SlayerTaskStrategy.MethodTag.CANNON));
		final SlayerMethodRules catacombsRules = SlayerMethodRuleCatalog.resolve(
			"Bloodveld", "Catacombs of Kourend", catacombs
		);
		assertEquals(5, catacombsRules.resolveRestoreSlots(catacombs));
		assertTrue(catacombsRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals(SlayerPotionPolicy.GOADING_DISPLAY)
		));

		final SlayerTaskStrategy tower = SlayerTaskStrategyCatalog.resolve(
			"Bloodveld", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.PREFER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Slayer Tower", false
		);
		assertTrue(tower.hasTag(SlayerTaskStrategy.MethodTag.SAFESPOT));
		assertTrue(!tower.hasTag(SlayerTaskStrategy.MethodTag.CANNON));

		final SlayerTaskStrategy godWars = SlayerTaskStrategyCatalog.resolve(
			"Bloodveld", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.PREFER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"God Wars Dungeon", false
		);
		assertTrue(godWars.hasTag(SlayerTaskStrategy.MethodTag.SAFESPOT));
		assertTrue(!godWars.hasTag(SlayerTaskStrategy.MethodTag.CANNON));
		assertTrue(godWars.getMethod().contains("God Wars"));
	}

	@Test
	public void greaterDemonFamilyUsesCompleteReviewedEncounterLoadouts()
	{
		final SlayerTaskStrategy regular = SlayerTaskStrategyCatalog.resolve(
			"Greater demons", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Catacombs of Kourend", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MELEE, regular.getCombatStyle());
		assertEquals("emberlight", regular.getWeaponPriorities().get(0));
		final SlayerMethodRules regularRules = SlayerMethodRuleCatalog.resolve(
			"Greater demons", "Catacombs of Kourend", regular
		);
		assertTrue(regularRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Ash sanctifier")
		));

		final SlayerTaskStrategy water = SlayerTaskStrategyCatalog.resolve(
			"Greater demons", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_MAGIC,
			"Catacombs of Kourend", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.MAGIC, water.getCombatStyle());
		assertTrue(water.getMethod().contains("40% Water weakness"));
		final SlayerTaskStrategy karuulmCannon = SlayerTaskStrategyCatalog.resolve(
			"Greater demons", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.PREFER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Karuulm Slayer Dungeon", false
		);
		assertTrue(karuulmCannon.hasTag(SlayerTaskStrategy.MethodTag.CANNON));
		assertEquals(
			SlayerTaskStrategy.CombatStyle.RANGED,
			karuulmCannon.getCombatStyle()
		);

		final SlayerTaskStrategy chasmCannon = SlayerTaskStrategyCatalog.resolve(
			"Greater demons", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.PREFER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Chasm of Fire", false
		);
		assertEquals(
			SlayerTaskStrategy.CombatStyle.RANGED,
			chasmCannon.getCombatStyle()
		);
		assertTrue(chasmCannon.hasTag(SlayerTaskStrategy.MethodTag.CANNON));
		assertTrue(chasmCannon.hasTag(SlayerTaskStrategy.MethodTag.SAFESPOT));

		final SlayerTaskStrategy explicitChasmMelee =
			SlayerTaskStrategyCatalog.resolve(
				"Greater demons", SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.PREFER, SlayerPreference.Burst.NEVER,
				SlayerPreference.CombatStyle.PREFER_MELEE,
				"Chasm of Fire", false
			);
		assertEquals(
			SlayerTaskStrategy.CombatStyle.MELEE,
			explicitChasmMelee.getCombatStyle()
		);
		assertFalse(explicitChasmMelee.hasTag(
			SlayerTaskStrategy.MethodTag.CANNON
		));

		final SlayerTaskStrategy kril = SlayerTaskStrategyCatalog.resolve(
			"K'ril Tsutsaroth", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"God Wars Dungeon", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED, kril.getCombatStyle());
		assertEquals("scorching bow", kril.getWeaponPriorities().get(0));
		assertEquals("lightbearer", SlayerEquipmentAuditCatalog.priorities(
			"K'ril Tsutsaroth", kril,
			net.runelite.api.EquipmentInventorySlot.RING,
			"scorching bow"
		).get(0));
		final SlayerMethodRules krilRules = SlayerMethodRuleCatalog.resolve(
			"K'ril Tsutsaroth", "God Wars Dungeon", kril
		);
		assertEquals(9, krilRules.resolveRestoreSlots(kril));
		assertEquals(SlayerMethodRules.Spellbook.ANCIENT, krilRules.getSpellbook());
		assertEquals("Blood Barrage", krilRules.getPrimarySpell());
		assertTrue(krilRules.requiresRunePouch());
		assertEquals(3, krilRules.getPouchRunes().size());
		assertEquals("Fire", krilRules.getPouchRunes().get(0).getName());
		assertEquals("Blood", krilRules.getPouchRunes().get(1).getName());
		assertEquals("Death", krilRules.getPouchRunes().get(2).getName());
		assertTrue(krilRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Divine ranging potion")
				&& item.getSlotCount() == 3
		));
		assertTrue(krilRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Poison protection")
		));
		assertTrue(krilRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Zamorak protection switch")
		));
		assertTrue(krilRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Blood Barrage weapon switch")
		));
		assertTrue(krilRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Magic body switch")
		));
		assertEquals("necklace of anguish", SlayerEquipmentAuditCatalog.priorities(
			"K'ril Tsutsaroth", kril,
			net.runelite.api.EquipmentInventorySlot.AMULET,
			"scorching bow"
		).get(0));
		assertEquals("pegasian boots", SlayerEquipmentAuditCatalog.priorities(
			"K'ril Tsutsaroth", kril,
			net.runelite.api.EquipmentInventorySlot.BOOTS,
			"scorching bow"
		).get(1));

		final SlayerTaskStrategy meleeKril = SlayerTaskStrategyCatalog.resolve(
			"K'ril Tsutsaroth", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.PREFER_MELEE,
			"God Wars Dungeon", false
		);
		final SlayerMethodRules meleeKrilRules = SlayerMethodRuleCatalog.resolve(
			"K'ril Tsutsaroth", "God Wars Dungeon", meleeKril
		);
		assertEquals("emberlight", meleeKril.getWeaponPriorities().get(0));
		assertTrue(meleeKrilRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Special attack weapon")
				&& item.getAlternatives().get(0).equals("saradomin godsword")
		));
		assertEquals("masori body f", SlayerEquipmentAuditCatalog.priorities(
			"K'ril Tsutsaroth", meleeKril,
			net.runelite.api.EquipmentInventorySlot.BODY,
			"emberlight"
		).get(0));
		assertEquals("avernic defender", SlayerEquipmentAuditCatalog.priorities(
			"K'ril Tsutsaroth", meleeKril,
			net.runelite.api.EquipmentInventorySlot.SHIELD,
			"emberlight"
		).get(0));

		final SlayerTaskStrategy skotizo = SlayerTaskStrategyCatalog.resolve(
			"Skotizo", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Skotizo's Lair", false
		);
		final SlayerMethodRules skotizoRules = SlayerMethodRuleCatalog.resolve(
			"Skotizo", "Skotizo's Lair", skotizo
		);
		assertTrue(skotizoRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Dark totem")
		));

		final SlayerTaskStrategy tormented = SlayerTaskStrategyCatalog.resolve(
			"Tormented demons", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Ancient Guthixian Temple", false
		);
		final SlayerMethodRules tormentedRules = SlayerMethodRuleCatalog.resolve(
			"Tormented demons", "Ancient Guthixian Temple", tormented
		);
		assertEquals(7, tormentedRules.resolveRestoreSlots(tormented));
		assertEquals(5, tormentedRules.resolveFoodSlots(tormented, tormented.getFoodSlots()));
		assertEquals(3, tormentedRules.getReservedLootSlots());
		assertEquals("emberlight", tormented.getWeaponPriorities().get(0));
		assertTrue(tormented.getWeaponPriorities().contains("abyssal bludgeon"));
		assertTrue(tormented.getWeaponPriorities().contains("arkan blade"));
		assertEquals(SlayerMethodRules.Spellbook.ARCEUUS, tormentedRules.getSpellbook());
		assertTrue(tormentedRules.requiresBookOfDead());
		assertTrue(tormentedRules.requiresRunePouch());
		assertTrue(tormentedRules.getPouchRunes().stream().anyMatch(
			rune -> rune.getName().equals("Fire")
				&& rune.getMinimumQuantity() >= 1000
		));
		assertTrue(tormentedRules.getPouchRunes().stream().anyMatch(
			rune -> rune.getName().equals("Cosmic")
				&& rune.getMinimumQuantity() >= 500
		));
		assertTrue(tormentedRules.getPouchRunes().stream().anyMatch(
			rune -> rune.getName().equals("Soul")
				&& rune.getMinimumQuantity() >= 500
		));
		for (final String required : Arrays.asList(
			"Secondary weapon switch", "Secondary body switch", "Secondary legs switch",
			"Magic off-hand switch",
			"Shield-down crush weapon", "Special attack weapon"))
		{
			assertTrue(tormentedRules.getRequiredItems().stream().anyMatch(
				item -> item.getDisplayName().equals(required)
			));
		}
		assertTrue(tormentedRules.getRequiredItems().stream()
			.filter(item -> item.getDisplayName().equals("Secondary weapon switch"))
			.anyMatch(item -> item.getAlternatives().indexOf("purging staff")
				> item.getAlternatives().indexOf("twisted bow")
				&& item.getAlternatives().indexOf("purging staff")
				< item.getAlternatives().indexOf("toxic blowpipe")));
		assertTrue(tormentedRules.getRequiredItems().stream()
			.filter(item -> item.getDisplayName().equals("Shield-down crush weapon"))
			.noneMatch(item -> item.getAlternatives().contains("saradomin godsword")));
		assertTrue(tormentedRules.getRequiredItems().stream()
			.filter(item -> item.getDisplayName().equals("Shield-down crush weapon"))
			.anyMatch(item -> item.getAlternatives().indexOf("dragon 2h sword")
				< item.getAlternatives().indexOf("tzhaar ket om")));
		assertEquals("avernic defender", SlayerEquipmentAuditCatalog.priorities(
			"Tormented demons", tormented,
			net.runelite.api.EquipmentInventorySlot.SHIELD,
			"emberlight"
		).get(0));
		assertTrue(SlayerEquipmentAuditCatalog.priorities(
			"Tormented demons", tormented,
			net.runelite.api.EquipmentInventorySlot.BODY,
			"emberlight"
		).contains("inquisitor s hauberk"));
		assertTrue(SlayerEquipmentAuditCatalog.priorities(
			"Tormented demons", tormented,
			net.runelite.api.EquipmentInventorySlot.BOOTS,
			"emberlight"
		).contains("climbing boots"));
	}

	@Test
	public void cerberusUsesAValidGreaterThrallRunePouch()
	{
		final SlayerTaskStrategy cerberus = SlayerTaskStrategyCatalog.resolve(
			"Cerberus", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Cerberus' Lair", false
		);
		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"Cerberus", "Cerberus' Lair", cerberus
		);

		assertEquals(SlayerMethodRules.Spellbook.ARCEUUS, rules.getSpellbook());
		assertEquals("Resurrect Greater Ghost", rules.getPrimarySpell());
		assertTrue(rules.usesThralls());
		assertFalse(rules.usesDeathCharge());
		assertFalse(rules.usesWardOfArceuus());
		assertTrue(rules.requiresRunePouch());
		assertTrue(rules.requiresBookOfDead());
		assertEquals(Arrays.asList("Fire", "Blood", "Cosmic"),
			rules.getPouchRunes().stream()
				.map(SlayerMethodRules.PouchRuneRequirement::getName)
				.collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void carriedTravelAndCompleteCarriedLoadoutBypassAnUnnecessaryBankLeg()
	{
		final SlayerLoadoutPlan carried = new SlayerLoadoutPlan(
			"Ready", "Ready", "Ready", "Ready",
			Collections.singletonList(new SlayerLoadoutItem(
				"Max cape", ItemID.SLAYER_RING_8, 1,
				SlayerLoadoutItem.Status.EQUIPPED
			)),
			Collections.singletonList(new SlayerLoadoutItem(
				"Task supplies", ItemID.SHARK, 20,
				SlayerLoadoutItem.Status.INVENTORY
			)),
			Collections.emptyList()
		);
		assertTrue(SlayerPlusPlugin.carriedTravelOutranksBankForRegression());
		assertTrue(SlayerPlusPlugin.isCarriedLoadoutReadyForRegression(carried));

		final SlayerLoadoutPlan needsBank = new SlayerLoadoutPlan(
			"Ready", "Banked", "Needs bank", "Needs bank",
			carried.getEquipmentItems(),
			Collections.singletonList(new SlayerLoadoutItem(
				"Task supplies", ItemID.SHARK, 20,
				SlayerLoadoutItem.Status.BANK
			)),
			Collections.emptyList()
		);
		assertFalse(SlayerPlusPlugin.isCarriedLoadoutReadyForRegression(needsBank));
	}

	@Test
	public void abyssalDemonBarrageUsesTheReviewedCatacombsStackTile()
	{
		assertEquals(
			new WorldPoint(1675, 10091, 0),
			SlayerRouteCatalog.findBarragePosition(
				"Abyssal demons", "Catacombs of Kourend"
			)
		);
		assertTrue(SlayerRouteCatalog.findBarragePosition(
			"Abyssal demons", "Slayer Tower"
		) == null);
	}

	@Test
	public void aviansiesAndKreeCarryRequiredGodWarsProtection()
	{
		final SlayerTaskStrategy aviansies = SlayerTaskStrategyCatalog.resolve(
			"Aviansies", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"God Wars Dungeon", false
		);
		final SlayerMethodRules regular = SlayerMethodRuleCatalog.resolve(
			"Aviansies", "God Wars Dungeon", aviansies
		);
		for (final String item : Arrays.asList(
			"Armadyl protection", "Zamorak protection"))
		{
			assertTrue(regular.getRequiredItems().stream().anyMatch(
				required -> required.getDisplayName().equals(item)
			));
			assertTrue(bossInventoryRules("Kree'arra").getRequiredItems().stream()
				.anyMatch(required -> required.getDisplayName().equals(item)));
		}
	}

	@Test
	public void bryophytaAndDangerousBossTripsHaveCompleteSupplyPolicies()
	{
		final SlayerMethodRules bryophyta = bossInventoryRules("Bryophyta");
		assertTrue(bryophyta.fillsRemainingWithFood());
		assertTrue(bryophyta.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Growthling tool")
				&& item.getAlternatives().contains("dragon axe")
				&& !item.getAlternatives().contains("zombie axe")
		));

		for (final String boss : Arrays.asList(
			"Abyssal Sire", "Amoxliatl", "Cerberus", "Grotesque Guardians",
			"K'ril Tsutsaroth", "Kalphite Queen", "Kree'arra", "Skotizo",
			"Vorkath", "Royal Titans", "Duke Sucellus", "General Graardor",
			"Maggot King", "Phantom Muspah", "Leviathan", "Whisperer",
			"Vardorvis", "Commander Zilyana", "Zulrah"))
		{
			final SlayerMethodRules rules = bossInventoryRules(boss);
			assertTrue(boss + " has no remaining-slot supply policy",
				rules.fillsRemainingWithFood() || rules.fillsRemainingWithRestore());
			assertEquals(boss + " unexpectedly reserves empty cells", 0,
				rules.getReservedLootSlots());
		}
	}

	@Test
	public void jadAndVardorvisUseTheirReviewedTripInventories()
	{
		final SlayerMethodRules jad = bossInventoryRules("TzTok-Jad");
		assertTrue(jad.fillsRemainingWithRestore());
		assertEquals(0, jad.getReservedLootSlots());
		assertTrue(jad.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Saradomin brew")
				&& item.getSlotCount() == 8
		));
		assertTrue(jad.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Super restore")
				&& item.getSlotCount() == 15
		));
		assertTrue(jad.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals(
				SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY
			) && item.getSlotCount() == 2
		));

		final SlayerMethodRules vardorvis = bossInventoryRules("Vardorvis");
		assertTrue(vardorvis.fillsRemainingWithFood());
		assertEquals(3, vardorvis.resolveRestoreSlots(null));
		for (final String required : Arrays.asList(
			"Divine super combat potion", "Special attack weapon",
			"Fast Stranglewood return"))
		{
			assertTrue(vardorvis.getRequiredItems().stream().anyMatch(
				item -> item.getDisplayName().equals(required)
			));
		}
	}

	@Test
	public void infernoKeepsReturnTeleportsOutAndCarriesEnoughBrews()
	{
		assertFalse(SlayerPlusPlugin.shouldAddPersistentReturnTeleports(true));
		assertTrue(SlayerPlusPlugin.shouldAddPersistentReturnTeleports(false));

		final SlayerTaskStrategy fast = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Fast on-task Inferno repeat"
		).costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS).build();
		final SlayerTaskStrategy efficient = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Efficient on-task Inferno repeat"
		).costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT).build();
		final SlayerTaskStrategy safety = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Completion first"
		).costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK).build();

		assertInfernoSupplies(fast, 7, 9);
		assertInfernoSupplies(efficient, 8, 8);
		assertInfernoSupplies(safety, 8, 7);
	}

	@Test
	public void chinchompasAreLimitedToTheExplicitlyReviewedKreeMethod()
	{
		final SlayerTaskStrategy kree = SlayerTaskStrategyCatalog.resolve(
			"Kree'arra", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"God Wars Dungeon", false
		);
		assertTrue(kree.hasTag(SlayerTaskStrategy.MethodTag.CHINNING));
		final SlayerTaskStrategy bats = SlayerTaskStrategyCatalog.resolve(
			"Bats", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Standard Slayer location", false
		);
		assertFalse(bats.hasTag(SlayerTaskStrategy.MethodTag.CHINNING));
	}

	@Test
	public void krystiliaTasksUseLowRiskBlightedSuppliesAndAnEscape()
	{
		for (final String task : Arrays.asList(
			"Abyssal demons", "Ankou", "Aviansies", "Black dragons",
			"Bloodveld", "Greater demons", "Hellhounds", "Nechryael"))
		{
			final String location = task.equals("Aviansies")
				? "Wilderness God Wars Dungeon" : "Wilderness Slayer Cave";
			final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				task, SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW, SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC, location, true
			);
			assertTrue(task, strategy.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS));
			assertEquals(task, SlayerTaskStrategy.CostPolicy.LOW_RISK,
				strategy.getCostPolicy());
			final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
				task, location, strategy
			);
			assertEquals(task, "blighted super restore",
				rules.getPrimaryRestoreFamily());
			assertTrue(task, rules.getRequiredItems().stream().anyMatch(
				item -> item.getDisplayName().equals("Looting bag")));
			assertTrue(task, rules.getRequiredItems().stream().anyMatch(
				item -> item.getDisplayName().equals("Level-30 Wilderness escape")));
		}
	}

	@Test
	public void whileGuthixSleepsChangesDuradelDisplayToKuradalOnly()
	{
		assertEquals("Duradel",
			SlayerPlusPlugin.masterDisplayNameForRegression(5, false));
		assertEquals("Kuradal",
			SlayerPlusPlugin.masterDisplayNameForRegression(5, true));
		assertEquals(SlayerMasterRouteCatalog.getName(8),
			SlayerPlusPlugin.masterDisplayNameForRegression(8, true));
	}

	@Test
	public void ankouUsesLocationAwareWikiReviewedMethodsAndRoute()
	{
		assertTrue(SlayerRecommendationEngine.catalogLocationsForValidation("Ankou")
			.contains("Catacombs of Kourend"));
		assertEquals("Catacombs of Kourend",
			SlayerTaskTravelAuditCatalog.find("Ankou").getLocation());

		final SlayerTaskStrategy ranged = SlayerTaskStrategyCatalog.resolve(
			"Ankou", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Catacombs of Kourend", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED,
			ranged.getCombatStyle());
		assertEquals("venator bow", ranged.getWeaponPriorities().get(0));
		assertEquals("toxic blowpipe", ranged.getWeaponPriorities().get(1));
		assertTrue(ranged.hasTag(SlayerTaskStrategy.MethodTag.VENATOR));
		final SlayerMethodRules rangedRules = SlayerMethodRuleCatalog.resolve(
			"Ankou", "Catacombs of Kourend", ranged
		);
		assertEquals(5, rangedRules.resolveRestoreSlots(ranged));
		assertEquals(2, rangedRules.resolveFoodSlots(ranged, ranged.getFoodSlots()));

		final SlayerTaskStrategy barrage = SlayerTaskStrategyCatalog.resolve(
			"Ankou", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.PREFER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Catacombs of Kourend", false
		);
		assertTrue(barrage.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE));
		final SlayerMethodRules barrageRules = SlayerMethodRuleCatalog.resolve(
			"Ankou", "Catacombs of Kourend", barrage
		);
		assertTrue(barrageRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Tagging darts/knives")
				&& item.getAlternatives().contains("mithril dart")
				&& item.getAlternatives().contains("steel knife")
		));
		assertTrue(barrageRules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Goading potion")
				&& item.isOwnedOnly()
				&& item.getGroup() == SlayerMethodRules.InventoryGroup.UTILITY
		));
		assertEquals(5, barrageRules.resolveRestoreSlots(barrage));
		assertEquals(0,
			barrageRules.resolveFoodSlots(barrage, barrage.getFoodSlots()));

		final SlayerTaskStrategy automaticAllow = SlayerTaskStrategyCatalog.resolve(
			"Ankou", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.ALLOW,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Catacombs of Kourend", false
		);
		assertEquals(SlayerTaskStrategy.CombatStyle.RANGED,
			automaticAllow.getCombatStyle());
		assertTrue(automaticAllow.hasTag(SlayerTaskStrategy.MethodTag.VENATOR));
		assertFalse(automaticAllow.hasTag(
			SlayerTaskStrategy.MethodTag.BURST_BARRAGE));

		final SlayerTaskStrategy cannon = SlayerTaskStrategyCatalog.resolve(
			"Ankou", SlayerPreference.Playstyle.FAST_XP,
			SlayerPreference.Cannon.PREFER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Stronghold Slayer Cave", false
		);
		assertEquals("toxic blowpipe", cannon.getWeaponPriorities().get(0));
		assertTrue(cannon.hasTag(SlayerTaskStrategy.MethodTag.CANNON));
		assertFalse(cannon.hasTag(SlayerTaskStrategy.MethodTag.VENATOR));
		final SlayerMethodRules cannonRules = SlayerMethodRuleCatalog.resolve(
			"Ankou", "Stronghold Slayer Cave", cannon
		);
		assertEquals(3, cannonRules.resolveRestoreSlots(cannon));
		assertEquals(2,
			cannonRules.resolveFoodSlots(cannon, cannon.getFoodSlots()));

		final SlayerTaskStrategy safespot = SlayerTaskStrategyCatalog.resolve(
			"Ankou", SlayerPreference.Playstyle.PROFIT,
			SlayerPreference.Cannon.NEVER, SlayerPreference.Burst.NEVER,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Stronghold of Security", false
		);
		assertEquals("toxic blowpipe", safespot.getWeaponPriorities().get(0));
		assertTrue(safespot.hasTag(SlayerTaskStrategy.MethodTag.SAFESPOT));

		final SlayerRouteCatalog.RouteProfile route = SlayerRouteCatalog.resolve(
			"Ankou", "Catacombs of Kourend", false
		);
		assertNotNull(route);
		assertEquals(new WorldPoint(1637, 9991, 0),
			route.getPrimaryDestination());
	}

	private static void assertDirectlyAbove(
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements,
		final String top,
		final String legs)
	{
		final int topSlot = slotFor(placements, top);
		final int legsSlot = slotFor(placements, legs);
		assertEquals(topSlot % 4, legsSlot % 4);
		assertEquals(topSlot + 4, legsSlot);
	}

	private static boolean containsLabelText(
		final Component component,
		final String expected)
	{
		if (component instanceof JLabel)
		{
			final JLabel label = (JLabel) component;
			final String text = label.getText();
			final String tooltip = label.getToolTipText();
			if ((text != null && text.contains(expected))
				|| (tooltip != null && tooltip.contains(expected)))
			{
				return true;
			}
		}
		if (component instanceof Container)
		{
			for (final Component child : ((Container) component).getComponents())
			{
				if (containsLabelText(child, expected))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static int slotFor(
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements,
		final String name)
	{
		return placements.stream()
			.filter(placement -> name.equals(
				placement.getItem().getDisplayName()
			))
			.findFirst()
			.orElseThrow(AssertionError::new)
			.getSlotIndex();
	}

	private static List<String> namesBySlot(
		final List<SlayerBankTagLayoutService.InventoryPlacement> placements)
	{
		return placements.stream()
			.sorted(Comparator.comparingInt(
				SlayerBankTagLayoutService.InventoryPlacement::getSlotIndex
			))
			.map(placement -> placement.getItem().getDisplayName())
			.collect(java.util.stream.Collectors.toList());
	}

	private static SlayerLoadoutItem item(final String name)
	{
		return new SlayerLoadoutItem(
			name,
			1,
			1,
			SlayerLoadoutItem.Status.BANK
		);
	}

	private static SlayerMethodRules bossInventoryRules(final String boss)
	{
		final SlayerMethodRules.Builder builder = SlayerMethodRules.builder();
		assertTrue(SlayerBossInventoryCatalog.apply(boss, builder, null));
		return builder.build();
	}

	private static void assertInfernoSupplies(
		final SlayerTaskStrategy strategy,
		final int expectedBrews,
		final int expectedRestores)
	{
		final SlayerMethodRules.Builder builder = SlayerMethodRules.builder();
		assertTrue(SlayerBossInventoryCatalog.apply(
			"TzKal-Zuk",
			builder,
			strategy
		));
		final SlayerMethodRules rules = builder.build();
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Saradomin brew")
				&& item.getSlotCount() == expectedBrews
		));
		assertTrue(rules.getRequiredItems().stream().anyMatch(
			item -> item.getDisplayName().equals("Super restore")
				&& item.getSlotCount() == expectedRestores
		));
		assertFalse(rules.getRequiredItems().stream().anyMatch(item ->
		{
			final String name = item.getDisplayName().toLowerCase(
				java.util.Locale.ENGLISH
			);
			return name.contains("max cape") || name.contains("karamja gloves");
		}));
	}

	private static SlayerLoadoutItem switchItem(
		final String name,
		final SlayerLoadoutItem.SwitchStyle style)
	{
		return item(name).asEquipmentSwitch(style);
	}

	private static SlayerLoadoutItem groupItem(
		final String name,
		final SlayerMethodRules.InventoryGroup group)
	{
		return item(name).withInventoryGroup(group);
	}
}
