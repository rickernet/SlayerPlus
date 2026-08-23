package com.slayerplus;

import java.util.Collection;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SlayerRoutePlanCatalogTest
{
	private static final Expected[] AUDITED = {
		boss("King Black Dragon", "King Black Dragon Lair"),
		boss("Artio", "Hunter's End"),
		boss("Callisto", "Callisto's Den"),
		boss("Venenatis", "Silk Chasm"),
		boss("Vet'ion", "Vet'ion's Rest"),
		boss("Grotesque Guardians", "Slayer Tower"),
		boss("Obor", "Edgeville Dungeon"),
		boss("Shellbane Gryphon", "The Great Conch"),
		boss("Duke Sucellus", "Ghorrock Dungeon"),
		boss("Phantom Muspah", "Ghorrock Dungeon"),
		boss("Leviathan", "The Scar"),
		boss("Vardorvis", "Stranglewood Temple"),
		boss("Zulrah", "Zul-Andra"),
		boss("Kree'arra", "God Wars Dungeon"),
		boss("General Graardor", "God Wars Dungeon"),
		boss("Commander Zilyana", "God Wars Dungeon"),
		boss("K'ril Tsutsaroth", "God Wars Dungeon"),
		boss("Sarachnis", "Forthos Dungeon"),
		boss("Skotizo", "Skotizo's Lair"),
		boss("Thermonuclear smoke devil", "Smoke Devil Dungeon"),
		boss("Demonic gorillas", "Crash Site Cavern"),
		boss("Giant Mole", "Falador Mole Lair"),
		boss("Barrows Brothers", "Barrows"),
		boss("Dagannoth Kings", "Waterbirth Island Dungeon"),
		boss("Maggot King", "Maggot King's lair"),
		assignment("Magic axes", "Wilderness"),
		assignment("Revenants", "Wilderness"),
		assignment("Fleshcrawlers", "Stronghold of Security"),
		assignment("Catablepon", "Stronghold of Security"),
		assignment("Ankou", "Stronghold of Security"),
		assignment("Metal dragons", "Ancient Cavern"),
		assignment("Aviansies", "God Wars Dungeon"),
		assignment("Spiritual creatures", "God Wars Dungeon"),
		assignment("Dagannoth", "Waterbirth Island Dungeon"),
		assignment("Cave kraken", "Kraken Cove"),
		assignment("Araxytes", "Morytania Spider Cave"),
		assignment("Frost dragons", "Grimstone Dungeon"),
		assignment("Ghosts", "Catacombs of Kourend"),
		assignment("Scabarites", "Sophanem Dungeon"),
		assignment("Shadow warriors", "Legends' Guild basement"),
		assignment("Warped creatures", "Poison Waste Dungeon"),
		assignment("Lesser nagua", "Ruins of Tapoyauik"),
		assignment("Aquanites", "Ynysdail Cavern"),
		assignment("Killerwatts", "Killerwatt plane"),
		assignment("Otherworldly beings", "Zanaris"),
		assignment("Mutated zygomites", "Zanaris"),
		assignment("Custodian Stalkers", "Stalker Den")
	};

	@Test
	public void catalogHasEveryAuditedExactKeyWithNoBossAssignmentFallback()
	{
		assertEquals(AUDITED.length, SlayerRoutePlanCatalog.keysForRegression().size());
		assertEquals(AUDITED.length, SlayerRoutePlanCatalog.allPlans().size());
		for (final Expected expected : AUDITED)
		{
			assertNotNull(expected.task, SlayerRoutePlanCatalog.resolve(
				expected.task, expected.location, expected.boss));
			assertTrue(SlayerRoutePlanCatalog.hasExactPlan(
				expected.task, expected.location, expected.boss));
			assertTrue(SlayerRoutePlanCatalog.hasExplicitPlanForRegression(
				expected.task, expected.location, expected.boss));
			assertNull(SlayerRoutePlanCatalog.resolve(
				expected.task, expected.location, !expected.boss));
		}

		assertNull(SlayerRoutePlanCatalog.resolve(
			"Artio", "Callisto's Den", true));
	}

	@Test
	public void lookupNormalizesOnlyCaseAndPunctuation()
	{
		final RoutePlan canonical = SlayerRoutePlanCatalog.resolve(
			"Vet'ion", "Vet'ion's Rest", true);
		assertSame(canonical, SlayerRoutePlanCatalog.resolve(
			"  VET’ION  ", "vet ion s rest", true));
		assertNull(SlayerRoutePlanCatalog.resolve(
			"Vet'ion", "Skeletal Tomb", true));
	}

	@Test
	public void exportedKeysAndPlansAreImmutable()
	{
		assertImmutable(SlayerRoutePlanCatalog.keysForRegression());
		assertImmutable(SlayerRoutePlanCatalog.plansForRegression());
		assertImmutable(SlayerRoutePlanCatalog.allPlans());
	}

	@Test
	public void everyPlanHasInspectableTerminalRetentionAndSafeManualLegs()
	{
		for (final RoutePlan plan : SlayerRoutePlanCatalog.allPlans())
		{
			assertNotNull(plan.getTerminalSpec());
			assertFalse(plan.getTerminalSpec().getCompletionPredicate()
				.containsKind(RouteCheck.Kind.ALWAYS));
			assertFalse(plan.getActivityRetention().getAreas().isEmpty());

			int terminals = 0;
			for (final RoutePlan.Leg leg : plan.getLegs().values())
			{
				if (leg.getKind() == RoutePlan.LegKind.TERMINAL)
				{
					terminals++;
					continue;
				}
				if (leg.getKind() != RoutePlan.LegKind.MANUAL_INTERACTION)
				{
					continue;
				}
				assertNotNull(plan.getId(), leg.getTarget());
				assertFalse(plan.getId(), leg.getInteractionInstruction().trim().isEmpty());
				assertNotNull(plan.getId(), leg.getReadinessPredicate());
				assertTrue(plan.getId(), leg.getReadinessPredicate()
					.containsKind(RouteCheck.Kind.TRACKED_OBJECT_ACTION));
				assertTrue(plan.getId(), hasStrongManualCompletion(
					leg.getCompletionPredicate()));
				assertFalse(plan.getId(), leg.getCompletionPredicate()
					.canMatchObservedInteractionAlone());
			}
			assertEquals(plan.getId(), 1, terminals);
		}
	}

	@Test
	public void sameLayerWildernessEntranceDoesNotArriveByProximity()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Artio", "Hunter's End", true);
		final WorldPoint entrance = new WorldPoint(3112, 3670, 0);
		final RouteEvidence proximity = RouteEvidence.builder()
			.worldPoint(entrance)
			.build();

		assertFalse(plan.hasArrived(proximity));
		final RoutePlan.Evaluation atCheckpoint = plan.evaluate(
			plan.initialCursor(), proximity);
		assertTrue(atCheckpoint.hasAdvanced());
		assertEquals("interact", atCheckpoint.getCursor().getActiveLegId());

		final RoutePlan.Evaluation stillWaiting = plan.evaluate(
			atCheckpoint.getCursor(), proximity);
		assertFalse(stillWaiting.hasAdvanced());
		assertFalse(plan.hasArrived(proximity));
	}

	@Test
	public void artioEntranceClickCannotAdvanceBeforeResultantRoomEvidence()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Artio", "Hunter's End", true);
		final WorldPoint entrance = new WorldPoint(3112, 3670, 0);
		final RoutePlan.Cursor manualCursor = plan.evaluate(
			plan.initialCursor(), RouteEvidence.builder()
				.worldPoint(entrance).build()).getCursor();
		final RouteEvidence interaction = RouteEvidence.builder()
			.worldPoint(entrance)
			.observedObjectInteraction(RouteEvidence.ObjectAction.of(
				ObjectID.WILD_CALLISTO_SINGLES_ENTRANCE01, "", "Enter"))
			.build();

		final RoutePlan.Evaluation afterInteraction = plan.evaluate(
			manualCursor, interaction);
		assertFalse(afterInteraction.hasAdvanced());
		assertEquals("interact", afterInteraction.getCursor().getActiveLegId());
		assertFalse(afterInteraction.hasArrived());
		assertFalse(plan.hasArrived(interaction));

		final RouteEvidence arena = RouteEvidence.builder()
			.worldPoint(regionCenter(7092, 0))
			.exactNpc("Artio").build();
		final RoutePlan.Evaluation arrived = plan.evaluate(
			manualCursor, arena);
		assertTrue(arrived.hasAdvanced());
		assertTrue(arrived.hasArrived());
		assertTrue(plan.hasArrived(arena));
	}

	@Test
	public void wildernessBossRoomsPersistBetweenKillsAndRejectEntranceEvidence()
	{
		assertPersistentBossRoom(
			"Artio", "Hunter's End", "Artio", 7092, 0,
			new WorldPoint(3112, 3670, 0));
		assertPersistentBossRoom(
			"Callisto", "Callisto's Den", "Callisto", 13473, 0,
			new WorldPoint(3290, 3855, 0));
		assertPersistentBossRoom(
			"Venenatis", "Silk Chasm", "Venenatis", 13727, 2,
			new WorldPoint(3321, 3794, 0));
		assertPersistentBossRoom(
			"Vet'ion", "Vet'ion's Rest", "Vet'ion", 13215, 1,
			new WorldPoint(3219, 3788, 0));
	}

	@Test
	public void dt2TemplateBossRoomsPersistBetweenKillsAndRejectWorldLookalikes()
	{
		assertPersistentTemplateBossRoom(
			"Duke Sucellus", "Ghorrock Dungeon", "Duke Sucellus", 12132, 0);
		assertPersistentTemplateBossRoom(
			"Phantom Muspah", "Ghorrock Dungeon", "Phantom Muspah", 11681, 0);
		assertPersistentTemplateBossRoom(
			"Leviathan", "The Scar", "The Leviathan", 8292, 0);
		assertPersistentTemplateBossRoom(
			"Vardorvis", "Stranglewood Temple", "Vardorvis", 4405, 0);
	}

	@Test
	public void godWarsBossRoomsPersistBetweenKillsAndRejectCentralChamber()
	{
		final WorldPoint central = new WorldPoint(2882, 5311, 2);
		assertPersistentCopiedWorldBossRoom(
			"Kree'arra", "God Wars Dungeon", "Kree'arra",
			new WorldPoint(2833, 5302, 2), central);
		assertPersistentCopiedWorldBossRoom(
			"General Graardor", "God Wars Dungeon", "General Graardor",
			new WorldPoint(2870, 5360, 2), central);
		assertPersistentCopiedWorldBossRoom(
			"Commander Zilyana", "God Wars Dungeon", "Commander Zilyana",
			new WorldPoint(2898, 5266, 0), central);
		assertPersistentCopiedWorldBossRoom(
			"K'ril Tsutsaroth", "God Wars Dungeon", "K'ril Tsutsaroth",
			new WorldPoint(2927, 5324, 2), central);
	}

	@Test
	public void additionalReviewedBossRoomsRejectEntrancesWrongSpacesAndPlanes()
	{
		assertPersistentTemplateBossRoom(
			"Grotesque Guardians", "Slayer Tower", "Dusk", 6727, 0,
			new WorldPoint(3428, 3536, 0));
		assertPersistentTemplateBossArea(
			"Obor", "Edgeville Dungeon", "Obor",
			new WorldPoint(3092, 9815, 0),
			new WorldPoint(3096, 3468, 0));
		assertFalse(SlayerRoutePlanCatalog.resolve(
			"Obor", "Edgeville Dungeon", true).hasArrived(
				RouteEvidence.builder().instanced(true)
					.templatePoint(new WorldPoint(3096, 9833, 0)).build()));
		assertPersistentTemplateBossRoom(
			"Skotizo", "Skotizo's Lair", "Skotizo", 9048, 0,
			new WorldPoint(1639, 3673, 0));
		assertPersistentTemplateBossArea(
			"Zulrah", "Zul-Andra", "Zulrah",
			new WorldPoint(2268, 3073, 0),
			new WorldPoint(2211, 3057, 0));
		assertPersistentCopiedWorldBossRoom(
			"Sarachnis", "Forthos Dungeon", "Sarachnis",
			new WorldPoint(1842, 9900, 0),
			new WorldPoint(1841, 9912, 0));
		assertPersistentWorldBossRoom(
			"Demonic gorillas", "Crash Site Cavern", "Demonic gorilla",
			new WorldPoint(2112, 5662, 0),
			new WorldPoint(2464, 3494, 0));
	}

	@Test
	public void shellbaneAcceptsOnlyBothBossArenasAndWaitsForResultantRoom()
	{
		final WorldPoint exterior = new WorldPoint(3176, 2477, 0);
		assertPersistentTemplateBossRoom(
			"Shellbane Gryphon", "The Great Conch", "Shellbane Gryphon",
			12682, 0, exterior);
		assertPersistentTemplateBossRoom(
			"Shellbane Gryphon", "The Great Conch", "Shellbane Gryphon",
			12938, 0, exterior);

		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Shellbane Gryphon", "The Great Conch", true);
		final RouteEvidence ordinaryCave = RouteEvidence.builder()
			.instanced(true).templatePoint(regionCenter(12426, 0)).build();
		assertFalse(plan.hasArrived(ordinaryCave));
		assertFalse(plan.isWithinActivityRetention(ordinaryCave));

		final RouteEvidence click = RouteEvidence.builder()
			.worldPoint(exterior)
			.observedObjectInteraction(RouteEvidence.ObjectAction.of(
				ObjectID.CONCH_GRYPHON_LAIR_ENTRANCE, "", "Enter"))
			.build();
		assertFalse(plan.getLeg("interact").getCompletionPredicate().matches(click));
	}

	@Test
	public void sarachnisUsesReviewedNorthBoundaryWebCheckpoint()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Sarachnis", "Forthos Dungeon", true);
		final WorldPoint reviewedWeb = new WorldPoint(1841, 9912, 0);

		assertNotNull(plan);
		assertTrue(plan.getLeg("route-web").getTarget()
			.getRoutingPoints().contains(reviewedWeb));
		assertTrue(plan.getLeg("pass-web").getTarget()
			.getRoutingPoints().contains(reviewedWeb));
		assertTrue(plan.getLeg("pass-web").getReadinessPredicate().matches(
			RouteEvidence.builder().trackedObjectAction(
				RouteEvidence.ObjectAction.of(
					34858, "", "Pass-through")).build()));
	}

	@Test
	public void unrelatedPohInstanceNeverCompletesAnInstancedBossPlan()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Phantom Muspah", "Ghorrock Dungeon", true);
		final RouteEvidence poh = RouteEvidence.builder()
			.instanced(true)
			.worldPoint(new WorldPoint(3200, 3200, 0))
			.templatePoint(new WorldPoint(1856, 5696, 0))
			.build();
		assertFalse(plan.hasArrived(poh));
	}

	@Test
	public void grotesqueGuardiansRequiresBothManualStagesBeforeRoomTerminal()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Grotesque Guardians", "Slayer Tower", true);
		final WorldPoint roof = new WorldPoint(3417, 3540, 2);
		RoutePlan.Cursor cursor = plan.evaluate(plan.initialCursor(),
			RouteEvidence.builder().worldPoint(roof).build()).getCursor();
		assertEquals("route-roof", cursor.getActiveLegId());
		cursor = plan.evaluate(cursor,
			RouteEvidence.builder().worldPoint(roof).build()).getCursor();
		assertEquals("open-roof", cursor.getActiveLegId());

		final RoutePlan.Evaluation failedRoofClick = plan.evaluate(
			cursor, RouteEvidence.builder()
			.worldPoint(roof)
			.observedObjectInteraction(RouteEvidence.ObjectAction.of(
				ObjectID.SLAYER_ROOF_ENTRANCE_UNLOCKED, "", "Enter"))
			.build());
		assertFalse(failedRoofClick.hasAdvanced());

		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(roof)
			.trackedObjectAction(RouteEvidence.ObjectAction.of(
				ObjectID.SLAYER_ROOF_BELL_QUICKSTART, "", "Ring"))
			.build()).getCursor();
		assertEquals("ring-bell", cursor.getActiveLegId());

		final RoutePlan.Evaluation noBell = plan.evaluate(
			cursor, RouteEvidence.builder().worldPoint(roof).build());
		assertFalse(noBell.hasAdvanced());

		final RoutePlan.Evaluation failedBellClick = plan.evaluate(
			cursor, RouteEvidence.builder()
			.observedObjectInteraction(RouteEvidence.ObjectAction.of(
				ObjectID.SLAYER_ROOF_BELL_QUICKSTART, "", "Ring"))
			.build());
		assertFalse(failedBellClick.hasAdvanced());

		final RouteEvidence roomEvidence = RouteEvidence.builder()
			.instanced(true)
			.templatePoint(regionCenter(6727, 0))
			.exactNpc("Dawn")
			.build();
		cursor = plan.evaluate(cursor, roomEvidence).getCursor();
		assertEquals("arrived", cursor.getActiveLegId());
		assertFalse(plan.hasArrived(RouteEvidence.builder().build()));
		assertFalse(plan.hasArrived(RouteEvidence.builder()
			.exactNpc("Dawn").build()));
		assertTrue(plan.hasArrived(roomEvidence));
	}

	@Test
	public void exactReviewedRegionsStopGraphRoutesWithoutCoarseLayerGuessing()
	{
		final RoutePlan kbd = SlayerRoutePlanCatalog.resolve(
			"King Black Dragon", "King Black Dragon Lair", true);
		assertTrue(kbd.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(2271, 4680, 0)).build()));
		assertFalse(kbd.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(3070, 4680, 0)).build()));

		final RoutePlan kings = SlayerRoutePlanCatalog.resolve(
			"Dagannoth Kings", "Waterbirth Island Dungeon", true);
		assertTrue(kings.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(2890, 4400, 0)).build()));
		assertFalse(kings.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(2550, 4400, 0)).build()));
	}

	@Test
	public void barrowsCryptRegionStopsRoutingButSurfaceRegionDoesNot()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Barrows Brothers", "Barrows", true);
		final RouteEvidence crypt = RouteEvidence.builder()
			.worldPoint(new WorldPoint(3551, 9694, 0)).build();
		final RouteEvidence surface = RouteEvidence.builder()
			.worldPoint(new WorldPoint(3565, 3289, 0)).build();

		assertTrue(plan.hasArrived(crypt));
		assertTrue(plan.isWithinActivityRetention(crypt));
		assertFalse(plan.hasArrived(surface));
	}

	@Test
	public void thermyCaveClickWaitsForDungeonAndBossRoomEvidence()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Thermonuclear smoke devil", "Smoke Devil Dungeon", true);
		final WorldPoint entrance = new WorldPoint(2411, 3058, 0);
		RoutePlan.Cursor cursor = plan.evaluate(plan.initialCursor(),
			RouteEvidence.builder().worldPoint(entrance).build()).getCursor();
		assertEquals("enter-cave", cursor.getActiveLegId());

		final RouteEvidence click = RouteEvidence.builder()
			.worldPoint(entrance)
			.observedObjectInteraction(RouteEvidence.ObjectAction.of(
				ObjectID.SMOKEDEVIL_CAVE_ENTRANCE, "", "Enter"))
			.build();
		assertFalse(plan.evaluate(cursor, click).hasAdvanced());

		final RouteEvidence dungeon = RouteEvidence.builder()
			.worldPoint(new WorldPoint(2400, 9440, 0)).build();
		final RoutePlan.Evaluation inside = plan.evaluate(cursor, dungeon);
		assertTrue(inside.hasAdvanced());
		assertEquals("enter-boss-room", inside.getCursor().getActiveLegId());
		assertFalse(plan.hasArrived(dungeon));

		final RouteEvidence bossRoom = RouteEvidence.builder()
			.worldPoint(new WorldPoint(2362, 9456, 0)).build();
		assertTrue(plan.hasArrived(bossRoom));
		assertTrue(plan.isWithinActivityRetention(bossRoom));
	}

	@Test
	public void assignmentEntranceProximityCannotReplaceObservedInteraction()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Magic axes", "Wilderness", false);
		final WorldPoint door = new WorldPoint(3190, 3957, 0);
		final RouteEvidence proximity = RouteEvidence.builder()
			.worldPoint(door).build();

		assertFalse(plan.hasArrived(proximity));
		final RoutePlan.Evaluation atDoor = plan.evaluate(
			plan.initialCursor(), proximity);
		assertTrue(atDoor.hasAdvanced());
		assertEquals("interact", atDoor.getCursor().getActiveLegId());
		assertFalse(plan.evaluate(atDoor.getCursor(), proximity).hasAdvanced());

		final RouteEvidence interaction = RouteEvidence.builder()
			.worldPoint(door)
			.observedObjectInteraction(RouteEvidence.ObjectAction.of(
				ObjectID.TOOLLOCK2, "", "Pick-lock"))
			.build();
		final RoutePlan.Evaluation afterDoor = plan.evaluate(
			atDoor.getCursor(), interaction);
		assertFalse(afterDoor.hasAdvanced());
		assertFalse(afterDoor.hasArrived());
		assertFalse(plan.hasArrived(interaction));
		final RouteEvidence pack = RouteEvidence.builder()
			.exactNpc("Magic axe").build();
		assertTrue(plan.evaluate(atDoor.getCursor(), pack).hasAdvanced());
		assertTrue(plan.hasArrived(pack));
	}

	@Test
	public void strongholdAssignmentCannotSkipItsReviewedFloorChain()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Ankou", "Stronghold of Security", false);
		RoutePlan.Cursor cursor = plan.evaluate(plan.initialCursor(),
			RouteEvidence.builder()
				.worldPoint(new WorldPoint(3081, 3420, 0)).build())
			.getCursor();
		assertEquals("interact-0", cursor.getActiveLegId());

		final RoutePlan.Evaluation failedEntranceClick = plan.evaluate(
			cursor, RouteEvidence.builder()
			.observedObjectInteraction(RouteEvidence.ObjectAction.of(
				ObjectID.SOS_DUNG_ENT_OPEN, "", "Climb-down"))
			.build());
		assertFalse(failedEntranceClick.hasAdvanced());
		assertEquals("interact-0",
			failedEntranceClick.getCursor().getActiveLegId());

		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(1859, 5244, 0)).build()).getCursor();
		assertEquals("route-1", cursor.getActiveLegId());

		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(1902, 5222, 0)).build()).getCursor();
		assertEquals("interact-1", cursor.getActiveLegId());

		final RoutePlan.Evaluation unrelatedLaterLanding = plan.evaluate(
			cursor, RouteEvidence.builder()
				.worldPoint(new WorldPoint(2123, 5252, 0))
				.observedObjectInteraction(RouteEvidence.ObjectAction.of(
					ObjectID.SOS_PEST_LADD_DOWN, "", "Climb-down"))
				.build());
		assertFalse(unrelatedLaterLanding.hasAdvanced());
		assertEquals("interact-1",
			unrelatedLaterLanding.getCursor().getActiveLegId());

		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(2042, 5245, 0)).build()).getCursor();
		assertEquals("route-2", cursor.getActiveLegId());
	}

	@Test
	public void aquaniteClicksWaitForIslandAndCavernResultEvidence()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Aquanites", "Ynysdail Cavern", false);
		RoutePlan.Cursor cursor = plan.initialCursor();

		final RoutePlan.Evaluation failedRowboatClick = plan.evaluate(
			cursor, RouteEvidence.builder()
				.observedObjectInteraction(RouteEvidence.ObjectAction.of(
					ObjectID.AMENITY_ROWBOAT_YNYSDAIL_IN, "", "Travel"))
				.build());
		assertFalse(failedRowboatClick.hasAdvanced());

		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(2227, 3469, 0)).build()).getCursor();
		assertEquals("route-cave", cursor.getActiveLegId());
		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(2218, 3478, 0)).build()).getCursor();
		assertEquals("enter-cave", cursor.getActiveLegId());

		final RoutePlan.Evaluation failedCaveClick = plan.evaluate(
			cursor, RouteEvidence.builder()
				.observedObjectInteraction(RouteEvidence.ObjectAction.of(
					ObjectID.YNYSDAIL_CAVE_ENTRANCE, "", "Enter"))
				.build());
		assertFalse(failedCaveClick.hasAdvanced());

		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(2293, 9913, 0)).build()).getCursor();
		assertEquals("route-pack", cursor.getActiveLegId());
		final RoutePlan.Evaluation arrived = plan.evaluate(
			cursor, RouteEvidence.builder()
				.worldPoint(new WorldPoint(2275, 9880, 0)).build());
		assertTrue(arrived.hasAdvanced());
		assertTrue(arrived.hasArrived());
	}

	@Test
	public void frostNaguaClicksWaitForEachResultantTempleStage()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Lesser nagua", "Ruins of Tapoyauik", false);
		RoutePlan.Cursor cursor = plan.evaluate(plan.initialCursor(),
			RouteEvidence.builder()
				.worldPoint(new WorldPoint(1693, 3230, 0)).build()).getCursor();
		assertEquals("enter-ruins", cursor.getActiveLegId());

		final RoutePlan.Evaluation failedEntranceClick = plan.evaluate(
			cursor, RouteEvidence.builder()
				.observedObjectInteraction(RouteEvidence.ObjectAction.of(
					ObjectID.TAPOYAUIK_TEMPLE_ENTRANCE, "", "Enter"))
				.build());
		assertFalse(failedEntranceClick.hasAdvanced());

		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(1693, 9630, 2)).build()).getCursor();
		assertEquals("route-upper-chain", cursor.getActiveLegId());
		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(1670, 9631, 2)).build()).getCursor();
		assertEquals("use-upper-chain", cursor.getActiveLegId());

		final RoutePlan.Evaluation failedChainClick = plan.evaluate(
			cursor, RouteEvidence.builder()
				.observedObjectInteraction(RouteEvidence.ObjectAction.of(
					ObjectID.TAPOYAUIK_POST_QUEST_SHORTCUT_TOP, "", "Climb-down"))
				.build());
		assertFalse(failedChainClick.hasAdvanced());
		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(1670, 9631, 0)).build()).getCursor();
		assertEquals("find-lower-chain", cursor.getActiveLegId());
	}

	@Test
	public void frostDragonsRequireTheCaveLandingBeforeRoutingToTheMainPack()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Frost dragons", "Grimstone Dungeon", false);
		RoutePlan.Cursor cursor = plan.evaluate(plan.initialCursor(),
			RouteEvidence.builder()
				.worldPoint(new WorldPoint(2912, 4066, 0)).build()).getCursor();
		assertEquals("enter-dungeon", cursor.getActiveLegId());

		final RoutePlan.Evaluation failedClick = plan.evaluate(
			cursor, RouteEvidence.builder()
				.observedObjectInteraction(RouteEvidence.ObjectAction.of(
					ObjectID.GRIMSTONE_CAVE_ENTRANCE, "", "Enter"))
				.build());
		assertFalse(failedClick.hasAdvanced());

		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(2894, 10452, 0)).build()).getCursor();
		assertEquals("route-pack", cursor.getActiveLegId());
		assertTrue(plan.getLeg("route-pack").getTarget().getRoutingPoints()
			.contains(new WorldPoint(2901, 10463, 0)));
		assertFalse(plan.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(2886, 10480, 0)).build()));
		assertTrue(plan.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(2901, 10463, 0)).build()));

		/* DLP and boat arrivals are not guaranteed to use the historical cave
		 * landing tile. Any verified Grimstone interior start must advance toward
		 * the pack instead of reactivating the overworld entrance. */
		RoutePlan.Cursor startInside = plan.evaluate(plan.initialCursor(),
			RouteEvidence.builder()
				.worldPoint(new WorldPoint(2878, 10439, 0)).build()).getCursor();
		assertEquals("enter-dungeon", startInside.getActiveLegId());
		startInside = plan.evaluate(startInside,
			RouteEvidence.builder()
				.worldPoint(new WorldPoint(2878, 10439, 0)).build()).getCursor();
		assertEquals("route-pack", startInside.getActiveLegId());
	}

	@Test
	public void ghostsRouteFromTheCatacombsLandingToAnActualGhostCluster()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Ghosts", "Catacombs of Kourend", false);
		RoutePlan.Cursor cursor = plan.evaluate(plan.initialCursor(),
			RouteEvidence.builder()
				.worldPoint(new WorldPoint(1639, 3673, 0)).build()).getCursor();
		assertEquals("enter-catacombs", cursor.getActiveLegId());

		final RoutePlan.Evaluation failedClick = plan.evaluate(
			cursor, RouteEvidence.builder()
				.observedObjectInteraction(RouteEvidence.ObjectAction.of(
					ObjectID.ZEAH_KOUREND_STATUE_PLINTH, "", "Investigate"))
				.build());
		assertFalse(failedClick.hasAdvanced());

		cursor = plan.evaluate(cursor, RouteEvidence.builder()
			.worldPoint(new WorldPoint(1666, 10050, 0)).build()).getCursor();
		assertEquals("route-ghost-pack", cursor.getActiveLegId());
		assertTrue(plan.getLeg("route-ghost-pack").getTarget().getRoutingPoints()
			.contains(new WorldPoint(1689, 10063, 0)));
		assertFalse(plan.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(1639, 10080, 0)).build()));
		assertTrue(plan.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(1689, 10063, 0)).build()));

		/* Starting elsewhere in the reviewed Ghost-room region is still an
		 * interior start and must never reactivate the surface statue route. */
		RoutePlan.Cursor startInside = plan.evaluate(plan.initialCursor(),
			RouteEvidence.builder()
				.worldPoint(new WorldPoint(1705, 10090, 0)).build()).getCursor();
		assertEquals("enter-catacombs", startInside.getActiveLegId());
		startInside = plan.evaluate(startInside,
			RouteEvidence.builder()
				.worldPoint(new WorldPoint(1705, 10090, 0)).build()).getCursor();
		assertEquals("route-ghost-pack", startInside.getActiveLegId());
	}

	@Test
	public void dynamicAssignmentInteriorsUseOnlyExactNpcTerminals()
	{
		assertExactNpcOnlyTerminal(SlayerRoutePlanCatalog.resolve(
			"Warped creatures", "Poison Waste Dungeon", false));
		assertExactNpcOnlyTerminal(SlayerRoutePlanCatalog.resolve(
			"Custodian Stalkers", "Stalker Den", false));

		assertFalse(SlayerRoutePlanCatalog.resolve(
			"Warped creatures", "Poison Waste Dungeon", false)
			.hasArrived(RouteEvidence.builder()
				.worldPoint(new WorldPoint(2322, 3099, 0)).build()));
		assertTrue(SlayerRoutePlanCatalog.resolve(
			"Warped creatures", "Poison Waste Dungeon", false)
			.hasArrived(RouteEvidence.builder()
				.exactNpc("Warped terrorbird").build()));
	}

	@Test
	public void maggotKingStopsOnlyInReviewedTemplateOrOnExactBossEvidence()
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			"Maggot King", "Maggot King's lair", true);
		assertNotNull(plan);
		assertFalse(plan.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(3604, 3364, 0)).build()));
		assertFalse(plan.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(2911, 8036, 0)).build()));
		assertTrue(plan.hasArrived(RouteEvidence.builder()
			.instanced(true)
			.templatePoint(new WorldPoint(2911, 8036, 0)).build()));
		assertFalse(plan.hasArrived(RouteEvidence.builder()
			.exactNpc("Maggot King").build()));
		assertTrue(plan.hasArrived(RouteEvidence.builder()
			.instanced(true)
			.templatePoint(new WorldPoint(2911, 8036, 0))
			.exactNpc("Maggot King").build()));
	}

	@Test
	public void reviewedBossGraphsBeginAtTheirAuthoredSurfaceAccess()
	{
		assertStartTarget("King Black Dragon", "King Black Dragon Lair",
			"route-surface", new WorldPoint(3017, 3850, 0));
		assertStartTarget("Grotesque Guardians", "Slayer Tower",
			"route-tower-entry", new WorldPoint(3428, 3536, 0));
		assertStartTarget("Kree'arra", "God Wars Dungeon",
			"route-surface", new WorldPoint(2916, 3746, 0));
		assertStartTarget("General Graardor", "God Wars Dungeon",
			"route-surface", new WorldPoint(2916, 3746, 0));
		assertStartTarget("Commander Zilyana", "God Wars Dungeon",
			"route-surface", new WorldPoint(2916, 3746, 0));
		assertStartTarget("K'ril Tsutsaroth", "God Wars Dungeon",
			"route-surface", new WorldPoint(2916, 3746, 0));
		assertStartTarget("Duke Sucellus", "Ghorrock Dungeon",
			"route-outer-access", new WorldPoint(2920, 3930, 0));
		assertStartTarget("Phantom Muspah", "Ghorrock Dungeon",
			"route-outer-access", new WorldPoint(2920, 3930, 0));
		assertStartTarget("Sarachnis", "Forthos Dungeon",
			"route-forthos-access", new WorldPoint(1667, 3570, 0));
		assertStartTarget("Skotizo", "Skotizo's Lair",
			"route-surface-statue", new WorldPoint(1639, 3673, 0));
		assertStartTarget("Dagannoth Kings", "Waterbirth Island Dungeon",
			"route-manifest-access", new WorldPoint(2443, 3746, 0));
	}

	@Test
	public void waterbirthManualNavigationCannotCompleteFromClickOrNpcAlone()
	{
		final RoutePlan kings = SlayerRoutePlanCatalog.resolve(
			"Dagannoth Kings", "Waterbirth Island Dungeon", true);
		final RoutePlan.Leg kingsManual = kings.getLeg("manual-interior-route");
		final RouteEvidence pressureDoorClick = RouteEvidence.builder()
			.observedObjectInteraction(RouteEvidence.ObjectAction.of(
				ObjectID.DAGANNOTH_PRESSURE_DOOR, "", "Open"))
			.build();

		assertFalse(kingsManual.getCompletionPredicate().matches(pressureDoorClick));
		assertFalse(kings.hasArrived(RouteEvidence.builder()
			.exactNpc("Dagannoth Rex").build()));
		assertTrue(kings.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(2890, 4400, 0)).build()));

		final RoutePlan assignment = SlayerRoutePlanCatalog.resolve(
			"Dagannoth", "Waterbirth Island Dungeon", false);
		assertFalse(assignment.getLeg("manual-interior-route")
			.getCompletionPredicate().matches(pressureDoorClick));
		assertFalse(assignment.hasArrived(RouteEvidence.builder()
			.exactNpc("Dagannoth").build()));
		assertTrue(assignment.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(2545, 10141, 0))
			.exactNpc("Dagannoth").build()));
	}

	private static void assertExactNpcOnlyTerminal(final RoutePlan plan)
	{
		assertNotNull(plan);
		final RouteCheck terminal =
			plan.getTerminalSpec().getCompletionPredicate();
		assertTrue(terminal.containsKind(RouteCheck.Kind.EXACT_NPC));
		assertFalse(terminal.containsKind(RouteCheck.Kind.WORLD_AREA));
		assertFalse(terminal.containsKind(RouteCheck.Kind.TEMPLATE_AREA));
	}

	private static boolean hasStrongManualCompletion(
		final RouteCheck predicate)
	{
		return predicate.containsKind(
				RouteCheck.Kind.COORDINATE_LAYER_TRANSITION)
			|| predicate.containsKind(
				RouteCheck.Kind.VISIBLE_WIDGET_COMPONENT)
			|| predicate.containsKind(
				RouteCheck.Kind.VISIBLE_WIDGET_GROUP)
			|| predicate.containsKind(
				RouteCheck.Kind.EXACT_NPC)
			|| predicate.containsKind(RouteCheck.Kind.WORLD_AREA)
			|| predicate.containsKind(RouteCheck.Kind.TEMPLATE_AREA);
	}

	private static void assertStartTarget(
		final String task,
		final String location,
		final String expectedLegId,
		final WorldPoint expectedTarget)
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			task, location, true);
		assertNotNull(task, plan);
		assertEquals(task, expectedLegId, plan.getStartLegId());
		final RoutePlan.Leg start = plan.getLeg(expectedLegId);
		assertNotNull(task, start);
		assertNotNull(task, start.getTarget());
		assertTrue(task,
			start.getTarget().getRoutingPoints().contains(expectedTarget));
		assertTrue(task, plan.isWithinActivityRetention(
			RouteEvidence.builder().worldPoint(expectedTarget).build()));
	}

	private static void assertPersistentBossRoom(
		final String task,
		final String location,
		final String npc,
		final int regionId,
		final int plane,
		final WorldPoint entrance)
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			task, location, true);
		final WorldPoint room = regionCenter(regionId, plane);

		assertNotNull(task, plan);
		assertTrue(task, plan.hasArrived(
			RouteEvidence.builder().worldPoint(room).build()));
		assertTrue(task, plan.isWithinActivityRetention(
			RouteEvidence.builder().worldPoint(room).build()));
		assertFalse(task, plan.hasArrived(
			RouteEvidence.builder().worldPoint(entrance).build()));
		assertFalse(task, plan.hasArrived(
			RouteEvidence.builder().exactNpc(npc).build()));
		assertFalse(task, plan.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(room.getX(), room.getY(), (plane + 1) & 3))
			.build()));
	}

	private static void assertPersistentTemplateBossRoom(
		final String task,
		final String location,
		final String npc,
		final int regionId,
		final int plane,
		final WorldPoint... rejectedEntrances)
	{
		final WorldPoint room = regionCenter(regionId, plane);
		assertPersistentTemplateBossArea(
			task, location, npc, room, rejectedEntrances);
	}

	private static void assertPersistentTemplateBossArea(
		final String task,
		final String location,
		final String npc,
		final WorldPoint room,
		final WorldPoint... rejectedEntrances)
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			task, location, true);
		final RouteEvidence roomEvidence = RouteEvidence.builder()
			.instanced(true).templatePoint(room).build();

		assertNotNull(task, plan);
		assertTrue(task, plan.hasArrived(roomEvidence));
		assertTrue(task, plan.isWithinActivityRetention(roomEvidence));
		assertTrue(task, hasManualCompletionMatching(plan, roomEvidence));
		assertFalse(task, plan.hasArrived(
			RouteEvidence.builder().exactNpc(npc).build()));
		assertFalse(task, plan.hasArrived(
			RouteEvidence.builder().worldPoint(room).build()));
		assertFalse(task, plan.hasArrived(RouteEvidence.builder()
			.instanced(true)
			.templatePoint(new WorldPoint(
				room.getX(), room.getY(), room.getPlane() + 1))
			.build()));
		for (final WorldPoint entrance : rejectedEntrances)
		{
			assertFalse(task, plan.hasArrived(
				RouteEvidence.builder().worldPoint(entrance).build()));
		}
	}

	private static void assertPersistentWorldBossRoom(
		final String task,
		final String location,
		final String npc,
		final WorldPoint room,
		final WorldPoint... rejectedEntrances)
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			task, location, true);
		final RouteEvidence roomEvidence = RouteEvidence.builder()
			.worldPoint(room).build();

		assertNotNull(task, plan);
		assertTrue(task, plan.hasArrived(roomEvidence));
		assertTrue(task, plan.isWithinActivityRetention(roomEvidence));
		assertTrue(task, hasManualCompletionMatching(plan, roomEvidence));
		assertFalse(task, plan.hasArrived(
			RouteEvidence.builder().exactNpc(npc).build()));
		assertFalse(task, plan.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(room.getX(), room.getY(), room.getPlane() + 1))
			.build()));
		assertFalse(task, plan.hasArrived(RouteEvidence.builder()
			.instanced(true).templatePoint(room).build()));
		for (final WorldPoint entrance : rejectedEntrances)
		{
			assertFalse(task, plan.hasArrived(
				RouteEvidence.builder().worldPoint(entrance).build()));
		}
	}

	private static void assertPersistentCopiedWorldBossRoom(
		final String task,
		final String location,
		final String npc,
		final WorldPoint room,
		final WorldPoint... rejectedEntrances)
	{
		final RoutePlan plan = SlayerRoutePlanCatalog.resolve(
			task, location, true);
		final RouteEvidence worldEvidence = RouteEvidence.builder()
			.worldPoint(room).build();
		final RouteEvidence templateEvidence = RouteEvidence.builder()
			.instanced(true).templatePoint(room).build();

		assertNotNull(task, plan);
		assertTrue(task, plan.hasArrived(worldEvidence));
		assertTrue(task, plan.hasArrived(templateEvidence));
		assertTrue(task, plan.isWithinActivityRetention(worldEvidence));
		assertTrue(task, plan.isWithinActivityRetention(templateEvidence));
		assertTrue(task, hasManualCompletionMatching(plan, worldEvidence));
		assertTrue(task, hasManualCompletionMatching(plan, templateEvidence));
		assertFalse(task, plan.hasArrived(
			RouteEvidence.builder().exactNpc(npc).build()));
		assertFalse(task, plan.hasArrived(RouteEvidence.builder()
			.worldPoint(new WorldPoint(room.getX(), room.getY(), room.getPlane() + 1))
			.build()));
		assertFalse(task, plan.hasArrived(RouteEvidence.builder()
			.instanced(true).templatePoint(new WorldPoint(
				room.getX(), room.getY(), room.getPlane() + 1)).build()));
		for (final WorldPoint entrance : rejectedEntrances)
		{
			assertFalse(task, plan.hasArrived(
				RouteEvidence.builder().worldPoint(entrance).build()));
			assertFalse(task, plan.hasArrived(RouteEvidence.builder()
				.instanced(true).templatePoint(entrance).build()));
		}
	}

	private static boolean hasManualCompletionMatching(
		final RoutePlan plan,
		final RouteEvidence evidence)
	{
		for (final RoutePlan.Leg leg : plan.getLegs().values())
		{
			if (leg.getKind() == RoutePlan.LegKind.MANUAL_INTERACTION
				&& leg.getCompletionPredicate().matches(evidence))
			{
				return true;
			}
		}
		return false;
	}

	private static WorldPoint regionCenter(final int regionId, final int plane)
	{
		return new WorldPoint(
			(regionId >>> 8) * 64 + 32,
			(regionId & 0xff) * 64 + 32,
			plane);
	}

	private static void assertImmutable(final Collection<?> values)
	{
		try
		{
			values.clear();
			fail("Expected an immutable collection");
		}
		catch (final UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

	private static Expected boss(
		final String task,
		final String location)
	{
		return new Expected(task, location, true);
	}

	private static Expected assignment(
		final String task,
		final String location)
	{
		return new Expected(task, location, false);
	}

	private static final class Expected
	{
		private final String task;
		private final String location;
		private final boolean boss;

		private Expected(
			final String task,
			final String location,
			final boolean boss)
		{
			this.task = task;
			this.location = location;
			this.boss = boss;
		}
	}
}
