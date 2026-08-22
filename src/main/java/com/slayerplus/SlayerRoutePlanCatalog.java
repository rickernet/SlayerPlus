package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

/**
 * Reviewed, explicit route plans for boss encounters and assignment interiors
 * whose final handoff is not safely represented by one blanket destination
 * radius.
 *
 * <p>The lookup is exact after punctuation/case normalization. There is no
 * task-only, location-only, or substring fallback: a plan authored for one
 * encounter must not silently drive a different variant. Boss-room and
 * explicit entrance manual legs advance only from resultant
 * world/template/widget/NPC evidence; a menu click is not proof that the
 * server accepted an interaction. Proximity is used only to reach an
 * interaction checkpoint.</p>
 *
 * <p>Named gamevals are used whenever RuneLite exposes one. The few raw ids
 * are explicitly pinned to a reviewed audit contract above their declaration.
 * Static coordinates are only used for reviewed approach or terminal areas;
 * dynamic interiors without defensible persistent room evidence deliberately
 * retain exact-NPC-only terminals.</p>
 */
public final class SlayerRoutePlanCatalog
{
	/*
	 * These LIVE-cache gamevals are currently emitted into package-private
	 * ObjectID1 by RuneLite 1.12.36, so plugin code cannot legally reference
	 * their generated symbols. Names/values are pinned by the reviewed
	 * Maggot King route contract in .tmp/maggot-king-route-contract.md.
	 */
	private static final int MAGGOT_CASTLE_PORTAL_WRAPPER = 61214;
	private static final int MAGGOT_CASTLE_PORTAL_VISIBLE = 61216;
	private static final int MAGGOT_VAMPYRIUM_BUSH_SHORTCUT = 40787;
	private static final int MAGGOT_LAIR_ENTRANCE = 61048;
	/* Reviewed audit id; RuneLite currently exposes no named gameval for it. */
	private static final int SARACHNIS_WEB_OBJECT_ID = 34858;

	private static final Map<RouteKey, SlayerRoutePlan> PLANS = buildPlans();
	private static final Set<RouteKey> REGRESSION_KEYS =
		Collections.unmodifiableSet(new LinkedHashSet<>(PLANS.keySet()));
	private static final Collection<SlayerRoutePlan> REGRESSION_PLANS =
		Collections.unmodifiableList(new ArrayList<>(PLANS.values()));

	private SlayerRoutePlanCatalog()
	{
	}

	/**
	 * Resolves one exact reviewed task/location/variant key, or {@code null}
	 * when the catalog has no explicit plan for that exact encounter.
	 */
	public static SlayerRoutePlan resolve(
		final String taskName,
		final String location,
		final boolean boss)
	{
		return PLANS.get(RouteKey.of(taskName, location, boss));
	}

	public static boolean hasExplicitPlanForRegression(
		final String taskName,
		final String location,
		final boolean boss)
	{
		return resolve(taskName, location, boss) != null;
	}

	/** Production-facing exact-key presence check. */
	public static boolean hasExactPlan(
		final String taskName,
		final String location,
		final boolean boss)
	{
		return resolve(taskName, location, boss) != null;
	}

	/** Immutable exact keys for release-validator and regression tests. */
	public static Set<RouteKey> keysForRegression()
	{
		return REGRESSION_KEYS;
	}

	/** Immutable authored plans for graph-invariant validation. */
	public static Collection<SlayerRoutePlan> plansForRegression()
	{
		return REGRESSION_PLANS;
	}

	/** Immutable view of every explicitly authored route plan. */
	public static Collection<SlayerRoutePlan> allPlans()
	{
		return REGRESSION_PLANS;
	}

	private static Map<RouteKey, SlayerRoutePlan> buildPlans()
	{
		final Map<RouteKey, SlayerRoutePlan> plans = new LinkedHashMap<>();

		register(plans, "King Black Dragon", "King Black Dragon Lair",
			kingBlackDragon());
		register(plans, "Artio", "Hunter's End", wildernessBoss(
			"artio-hunters-end", point(3112, 3670, 0), "Hunter's End entrance",
			identified(ObjectID.WILD_CALLISTO_SINGLES_ENTRANCE01, "Enter"),
			"Enter Hunter's End.", 7092, 0, "Artio"));
		register(plans, "Callisto", "Callisto's Den", wildernessBoss(
			"callisto-den", point(3290, 3855, 0), "Callisto's Den entrance",
			identified(ObjectID.WILD_CALLISTO_ENTRANCE01, "Enter"),
			"Enter Callisto's Den.", 13473, 0, "Callisto"));
		register(plans, "Venenatis", "Silk Chasm", wildernessBoss(
			"venenatis-silk-chasm", point(3321, 3794, 0), "Silk Chasm entrance",
			identified(ObjectID.WILD_VENANATIS_ENTRANCE01, "Enter"),
			"Enter the Silk Chasm.", 13727, 2, "Venenatis"));
		register(plans, "Vet'ion", "Vet'ion's Rest", wildernessBoss(
			"vetion-rest", point(3219, 3788, 0), "Vet'ion's Rest entrance",
			identified(ObjectID.WILD_VETION_ENTRANCE01, "Enter"),
			"Enter Vet'ion's Rest.", 13215, 1, "Vet'ion", "Vet'ion Reborn"));

		register(plans, "Grotesque Guardians", "Slayer Tower",
			grotesqueGuardians());
		register(plans, "Obor", "Edgeville Dungeon", singleManualWithRetention(
			"obor-edgeville-dungeon", point(3096, 3468, 0),
			"Obor's giant-key gate",
			reviewed(
				ids(ObjectID.HILLGIANT_BOSS_ENTRANCE_R,
					ObjectID.HILLGIANT_BOSS_ENTRANCE_L),
				"Open"),
			"Continue through Edgeville Dungeon and open Obor's giant-key gate.",
			roomTerminal(
				SlayerRoutePredicate.SpatialArea.template(
					3072, 3135, 9792, 9815, 0),
				"Obor"),
			new SlayerRoutePredicate.SpatialArea[] {
				aroundWorld(point(3096, 3468, 0), 28),
				SlayerRoutePredicate.SpatialArea.template(
					3072, 3135, 9792, 9815, 0)
			}));
		register(plans, "Shellbane Gryphon", "The Great Conch",
			shellbaneGryphon());

		register(plans, "Duke Sucellus", "Ghorrock Dungeon", ghorrockBoss(
			"duke-sucellus-ghorrock", "Duke Sucellus entry",
			reviewed(
				ids(ObjectID.DT2_GHORROCK_ENTRY_OP,
					ObjectID.GHORROCK_PRISON_DOOR),
				"Enter", "Open"),
			"Walk from the ring landing and enter Duke Sucellus's chamber.",
			12132, 0, "Duke Sucellus"));
		register(plans, "Phantom Muspah", "Ghorrock Dungeon", ghorrockBoss(
			"phantom-muspah-ghorrock", "Phantom Muspah crevice",
			identified(ObjectID.GHORROCK_DUNGEON_CAVE_ENTRY, "Enter"),
			"Walk from the ring landing and enter the crevice.",
			11681, 0, "Phantom Muspah"));
		register(plans, "Leviathan", "The Scar", singleManualWithRetention(
			"leviathan-scar", point(2081, 6372, 0), "Leviathan wall",
			identified(ObjectID.LEVIATHAN_WALL_CLIMB, "Climb"),
			"Climb the wall into the Leviathan encounter.",
			roomTerminal(regionTemplate(8292, 0), "The Leviathan", "Leviathan"),
			new SlayerRoutePredicate.SpatialArea[] {
				aroundWorld(point(2019, 6434, 0), 24),
				aroundWorld(point(2081, 6372, 0), 24),
				regionTemplate(8292, 0)
			}));
		register(plans, "Vardorvis", "Stranglewood Temple",
			singleManualWithRetention(
				"vardorvis-stranglewood", point(1129, 3418, 0),
				"Vardorvis chamber rocks",
				identified(ObjectID.DT2_STRANGLEWOOD_BOSS_ENTRY_OP, "Enter"),
				"Enter the rocks at the Stranglewood Temple chamber.",
				roomTerminal(regionTemplate(4405, 0), "Vardorvis"),
				new SlayerRoutePredicate.SpatialArea[] {
					aroundWorld(point(1175, 3424, 0), 24),
					aroundWorld(point(1129, 3418, 0), 24),
					regionTemplate(4405, 0)
				}));
		register(plans, "Zulrah", "Zul-Andra", singleManualWithRetention(
			"zulrah-zul-andra", point(2211, 3057, 0), "Zulrah boat",
			identified(ObjectID.SNAKEBOSS_BOAT, "Board", "Quick-board"),
			"Board or quick-board the boat to Zulrah.",
			roomTerminal(
				SlayerRoutePredicate.SpatialArea.template(
					2258, 2278, 3064, 3082, 0),
				"Zulrah"),
			new SlayerRoutePredicate.SpatialArea[] {
				aroundWorld(point(2211, 3057, 0), 28),
				SlayerRoutePredicate.SpatialArea.template(
					2258, 2278, 3064, 3082, 0)
			}));

		register(plans, "Kree'arra", "God Wars Dungeon", godWars(
			"kree-arra-gwd", "Armadyl",
			identified(ObjectID.GODWARS_DUNGEON_DOOR_ARMADYL, "Open"),
			"Cross the Armadyl obstacle, satisfy the kill-count or key requirement, "
				+ "then open the Armadyl boss door.",
			SlayerRoutePredicate.SpatialArea.world(2824, 2842, 5296, 5308, 2),
			"Kree'arra", "Wingman Skree", "Flockleader Geerin", "Flight Kilisa"));
		register(plans, "General Graardor", "God Wars Dungeon",
			bandosGodWars());
		register(plans, "Commander Zilyana", "God Wars Dungeon", godWars(
			"commander-zilyana-gwd", "Saradomin",
			identified(ObjectID.GODWARS_DUNGEON_DOOR_SARADOMIN, "Open"),
			"Complete the Saradomin traversal, satisfy the kill-count or key "
				+ "requirement, then open the Saradomin boss door.",
			SlayerRoutePredicate.SpatialArea.world(2889, 2907, 5258, 5275, 0),
			"Commander Zilyana", "Starlight", "Growler", "Bree"));
		register(plans, "K'ril Tsutsaroth", "God Wars Dungeon", godWars(
			"kril-tsutsaroth-gwd", "Zamorak",
			identified(ObjectID.GODWARS_DUNGEON_DOOR_ZAMORAK, "Open"),
			"Complete the Zamorak traversal, satisfy the kill-count or key "
				+ "requirement, then open the Zamorak boss door.",
			SlayerRoutePredicate.SpatialArea.world(2918, 2936, 5318, 5331, 2),
			"K'ril Tsutsaroth", "Balfrug Kreeyath", "Tstanon Karlak",
			"Zakl'n Gritch"));

		register(plans, "Sarachnis", "Forthos Dungeon", sarachnis());
		register(plans, "Skotizo", "Skotizo's Lair", skotizo());
		register(plans, "Thermonuclear smoke devil", "Smoke Devil Dungeon",
			thermonuclearSmokeDevil());
		register(plans, "Demonic gorillas", "Crash Site Cavern",
			singleManualWithRetention(
				"demonic-gorillas-crash-site", point(2464, 3494, 0),
				"Crash Site Cavern entrance",
				identified(ObjectID.MM2_CAVERN_ENTRANCE, "Enter"),
				"Enter the Crash Site Cavern.",
				roomTerminal(
					SlayerRoutePredicate.SpatialArea.world(
						2064, 2160, 5636, 5688, 0),
					"Demonic gorilla"),
				new SlayerRoutePredicate.SpatialArea[] {
					aroundWorld(point(2464, 3494, 0), 28),
					SlayerRoutePredicate.SpatialArea.world(
						2064, 2160, 5636, 5688, 0)
				}));
		register(plans, "Giant Mole", "Falador Mole Lair", giantMole());
		register(plans, "Barrows Brothers", "Barrows", barrows());
		register(plans, "Dagannoth Kings", "Waterbirth Island Dungeon",
			dagannothKings());
		register(plans, "Maggot King", "Maggot King's lair",
			maggotKing());

		registerAssignment(plans, "Magic axes", "Wilderness", magicAxes());
		registerAssignment(plans, "Revenants", "Wilderness", revenants());
		registerAssignment(plans, "Fleshcrawlers", "Stronghold of Security",
			strongholdFleshcrawlers());
		registerAssignment(plans, "Catablepon", "Stronghold of Security",
			strongholdCatablepon());
		registerAssignment(plans, "Ankou", "Stronghold of Security",
			strongholdAnkou());
		registerAssignment(plans, "Metal dragons", "Ancient Cavern",
			ancientCavernMetalDragons());
		registerAssignment(plans, "Aviansies", "God Wars Dungeon",
			godWarsAssignment("aviansies-gwd", point(2833, 5289, 2),
				exactNpc("Aviansie")));
		registerAssignment(plans, "Spiritual creatures", "God Wars Dungeon",
			godWarsAssignment("spiritual-creatures-gwd", point(2887, 5308, 2),
				exactNpc("Spiritual warrior", "Spiritual ranger", "Spiritual mage")));
		registerAssignment(plans, "Dagannoth", "Waterbirth Island Dungeon",
			waterbirthDagannoth());
		registerAssignment(plans, "Cave kraken", "Kraken Cove", caveKraken());
		registerAssignment(plans, "Araxytes", "Morytania Spider Cave",
			araxytes());
		registerAssignment(plans, "Frost dragons", "Grimstone Dungeon",
			frostDragons());
		registerAssignment(plans, "Ghosts", "Catacombs of Kourend",
			catacombsGhosts());
		registerAssignment(plans, "Scabarites", "Sophanem Dungeon",
			scabarites());
		registerAssignment(plans, "Shadow warriors", "Legends' Guild basement",
			shadowWarriors());
		registerAssignment(plans, "Warped creatures", "Poison Waste Dungeon",
			warpedCreatures());
		registerAssignment(plans, "Lesser nagua", "Ruins of Tapoyauik",
			frostNagua());
		registerAssignment(plans, "Aquanites", "Ynysdail Cavern", aquanites());
		registerAssignment(plans, "Killerwatts", "Killerwatt plane",
			killerwatts());
		registerAssignment(plans, "Otherworldly beings", "Zanaris",
			zanaris("otherworldly-beings-zanaris", exactNpc("Otherworldly being")));
		registerAssignment(plans, "Mutated zygomites", "Zanaris",
			zanaris("mutated-zygomites-zanaris", exactNpc("Mutated zygomite")));
		registerAssignment(plans, "Custodian Stalkers", "Stalker Den",
			custodianStalkers());

		return Collections.unmodifiableMap(plans);
	}

	private static SlayerRoutePlan kingBlackDragon()
	{
		final WorldPoint surface = point(3017, 3850, 0);
		final WorldPoint lever = point(3069, 10255, 0);
		final WorldPoint lair = point(2271, 4680, 0);
		final SlayerRoutePredicate lairArea = SlayerRoutePredicate.anyOf(
			SlayerRoutePredicate.worldArea(regionWorld(9033, 0)),
			SlayerRoutePredicate.templateArea(regionTemplate(9033, 0)));
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			lairArea,
			SlayerRoutePredicate.allOf(
				exactNpc("King Black Dragon"), lairArea));
		return SlayerRoutePlan.builder("king-black-dragon-lair")
			.startAt("route-surface")
			.activityRetention(retention(
				aroundWorld(surface, 24), aroundWorld(lever, 20),
				regionWorld(9033, 0), regionTemplate(9033, 0)))
			.addLeg(travel(
				"route-surface", "Route to the reviewed King Black Dragon ladder.",
				SlayerRoutePlan.RouteTarget.point(
					"King Black Dragon surface ladder", surface, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(surface, 5), atWorld(lever, 6), terminal),
				"route-lever"))
			.addLeg(travel(
				"route-lever", "Descend the ladder and route to the lair lever.",
				SlayerRoutePlan.RouteTarget.point(
					"King Black Dragon lair lever", lever, 4),
				SlayerRoutePredicate.anyOf(atWorld(lever, 6), terminal),
				"route-lair"))
			.addLeg(travel(
				"route-lair", "Follow the reviewed ladder and lever graph to the lair.",
				SlayerRoutePlan.RouteTarget.point("King Black Dragon lair", lair, 4),
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "King Black Dragon lair", terminal))
			.build();
	}

	private static SlayerRoutePlan wildernessBoss(
		final String id,
		final WorldPoint entrance,
		final String targetLabel,
		final SlayerRoutePredicate.ObjectActionSpec checkpoint,
		final String instruction,
		final int roomRegion,
		final int roomPlane,
		final String... npcNames)
	{
		final SlayerRoutePredicate.SpatialArea roomArea =
			regionWorld(roomRegion, roomPlane);
		final SlayerRoutePredicate terminal = roomTerminal(roomArea, npcNames);
		return singleManualWithRetention(
			id, entrance, targetLabel, checkpoint, instruction, terminal,
			new SlayerRoutePredicate.SpatialArea[] {
				aroundWorld(entrance, 24), roomArea
			});
	}

	private static SlayerRoutePlan shellbaneGryphon()
	{
		final WorldPoint entrance = point(3176, 2477, 0);
		final SlayerRoutePredicate.SpatialArea questArena =
			regionTemplate(12682, 0);
		final SlayerRoutePredicate.SpatialArea taskArena =
			regionTemplate(12938, 0);
		final SlayerRoutePredicate terminal = roomTerminal(
			new String[] {"Shellbane Gryphon"}, questArena, taskArena);
		final SlayerRoutePredicate.ObjectActionSpec checkpoint = reviewed(
			ids(ObjectID.TT_LAIR_ENTRANCE_BLOCKED,
				ObjectID.TT_LAIR_ENTRANCE_CLEAR,
				ObjectID.CONCH_GRYPHON_LAIR_ENTRANCE,
				ObjectID.CONCH_GRYPHON_TASK_LAIR_ENTRANCE),
			"Enter");
		return singleManualWithRetention(
			"shellbane-gryphon-great-conch", entrance,
			"Shellbane Gryphon lair entrance", checkpoint,
			"Enter the Shellbane Gryphon lair.", terminal,
			new SlayerRoutePredicate.SpatialArea[] {
				aroundWorld(entrance, 28), questArena, taskArena
			});
	}

	private static SlayerRoutePlan grotesqueGuardians()
	{
		final WorldPoint towerEntry = point(3428, 3536, 0);
		final WorldPoint towerTop = point(3417, 3540, 2);
		final SlayerRoutePredicate.SpatialArea roomArea = regionTemplate(6727, 0);
		final SlayerRoutePredicate terminal = roomTerminal(
			roomArea, "Dusk", "Dawn");
		final SlayerRoutePredicate.ObjectActionSpec roof = reviewed(
			ids(ObjectID.SLAYER_ROOF_ENTRANCE_LOCKED,
				ObjectID.SLAYER_ROOF_ENTRANCE_UNLOCKED,
				ObjectID.SLAYER_ROOF_EXIT,
				ObjectID.SLAYER_ROOF_ENTRANCE),
			"Open", "Enter", "Climb-up", "Climb-down");
		final SlayerRoutePredicate.ObjectActionSpec bell = reviewed(
			ids(ObjectID.SLAYER_ROOF_BELL_NOQUICKSTART,
				ObjectID.SLAYER_ROOF_BELL_QUICKSTART), "Ring");
		return SlayerRoutePlan.builder("grotesque-guardians-slayer-tower")
			.startAt("route-tower-entry")
			.activityRetention(retention(
				aroundWorld(towerEntry, 24), aroundWorld(towerTop, 28), roomArea))
			.addLeg(travel(
				"route-tower-entry", "Route to the Slayer Tower entrance.",
				SlayerRoutePlan.RouteTarget.point(
					"Slayer Tower entrance", towerEntry, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(towerEntry, 5), atWorld(towerTop, 5), terminal),
				"route-roof"))
			.addLeg(travel(
				"route-roof", "Route to the top of the Slayer Tower.",
				SlayerRoutePlan.RouteTarget.point("Slayer Tower roof access", towerTop, 4),
				SlayerRoutePredicate.anyOf(atWorld(towerTop, 5), terminal),
				"open-roof"))
			.addLeg(manual(
				"open-roof", "Use the roof entrance.",
				SlayerRoutePlan.RouteTarget.point("Slayer Tower roof entrance", towerTop, 4),
				"Open or enter the reviewed roof access object.", roof,
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(bell), terminal),
				"ring-bell"))
			.addLeg(manual(
				"ring-bell", "Start the Grotesque Guardians encounter.",
				SlayerRoutePlan.RouteTarget.instruction("Grotesque Guardians bell"),
				"Ring the rooftop bell.", bell,
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Grotesque Guardians rooftop", terminal))
			.build();
	}

	private static SlayerRoutePlan ghorrockBoss(
		final String id,
		final String targetLabel,
		final SlayerRoutePredicate.ObjectActionSpec checkpoint,
		final String instruction,
		final int roomTemplateRegion,
		final int roomPlane,
		final String... npcNames)
	{
		final WorldPoint outerAccess = point(2920, 3930, 0);
		final WorldPoint ringLanding = point(2912, 10336, 0);
		final SlayerRoutePredicate.SpatialArea roomArea =
			regionTemplate(roomTemplateRegion, roomPlane);
		final SlayerRoutePredicate terminal = roomTerminal(roomArea, npcNames);
		return SlayerRoutePlan.builder(id)
			.startAt("route-outer-access")
			.activityRetention(retention(
				aroundWorld(outerAccess, 24), aroundWorld(ringLanding, 36), roomArea))
			.addLeg(travel(
				"route-outer-access", "Route to the reviewed Ghorrock access point.",
				SlayerRoutePlan.RouteTarget.point(
					"Ghorrock Dungeon outer access", outerAccess, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(outerAccess, 5), atWorld(ringLanding, 8), terminal),
				"route-checkpoint"))
			.addLeg(travel(
				"route-checkpoint", "Route from the ring landing to " + targetLabel + ".",
				SlayerRoutePlan.RouteTarget.point(targetLabel, ringLanding, 4),
				SlayerRoutePredicate.anyOf(atWorld(ringLanding, 8), terminal),
				"interact"))
			.addLeg(manual(
				"interact", instruction,
				SlayerRoutePlan.RouteTarget.instruction(targetLabel),
				instruction, checkpoint,
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", targetLabel, terminal))
			.build();
	}

	private static SlayerRoutePlan sarachnis()
	{
		final WorldPoint forthosAccess = point(1667, 3570, 0);
		final WorldPoint dungeonHole = point(1469, 3653, 0);
		final WorldPoint web = point(1841, 9912, 0);
		final SlayerRoutePredicate.SpatialArea roomArea =
			SlayerRoutePredicate.SpatialArea.world(1829, 1855, 9890, 9911, 0);
		final SlayerRoutePredicate.SpatialArea templateRoom =
			templateCopyOf(roomArea);
		final SlayerRoutePredicate terminal = roomTerminal(
			new String[] {"Sarachnis"}, roomArea, templateRoom);
		final SlayerRoutePredicate.ObjectActionSpec webCheckpoint = identified(
			SARACHNIS_WEB_OBJECT_ID, "Pass-through", "Slash");
		return SlayerRoutePlan.builder("sarachnis-forthos-dungeon")
			.startAt("route-forthos-access")
			.activityRetention(retention(
				aroundWorld(forthosAccess, 24), aroundWorld(dungeonHole, 24),
				aroundWorld(web, 28), roomArea, templateRoom))
			.addLeg(travel(
				"route-forthos-access", "Route to the reviewed Forthos Dungeon access.",
				SlayerRoutePlan.RouteTarget.point(
					"Forthos Dungeon access", forthosAccess, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(forthosAccess, 5), atWorld(dungeonHole, 5),
					atWorld(web, 6), terminal),
				"route-dungeon-hole"))
			.addLeg(travel(
				"route-dungeon-hole", "Route through Forthos Dungeon to the reviewed hole.",
				SlayerRoutePlan.RouteTarget.point(
					"Forthos Dungeon hole", dungeonHole, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(dungeonHole, 5), atWorld(web, 6), terminal),
				"route-web"))
			.addLeg(travel(
				"route-web", "Use the reviewed hole edge and route to Sarachnis's web.",
				SlayerRoutePlan.RouteTarget.point("Sarachnis web", web, 4),
				SlayerRoutePredicate.anyOf(atWorld(web, 6), terminal),
				"pass-web"))
			.addLeg(manual(
				"pass-web", "Pass through Sarachnis's web.",
				SlayerRoutePlan.RouteTarget.point("Sarachnis web", web, 4),
				"Pass through or slash the reviewed web.", webCheckpoint,
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Sarachnis room", terminal))
			.build();
	}

	private static SlayerRoutePlan skotizo()
	{
		final WorldPoint surfaceStatue = point(1639, 3673, 0);
		final WorldPoint catacombsAltar = point(1666, 10050, 0);
		final SlayerRoutePredicate.SpatialArea roomArea = regionTemplate(9048, 0);
		final SlayerRoutePredicate terminal = roomTerminal(roomArea, "Skotizo");
		final SlayerRoutePredicate.ObjectActionSpec altar = identified(
			ObjectID.CATA_ALTAR, "Use");
		return SlayerRoutePlan.builder("skotizo-lair")
			.startAt("route-surface-statue")
			.activityRetention(retention(
				aroundWorld(surfaceStatue, 28), aroundWorld(catacombsAltar, 28),
				roomArea))
			.addLeg(travel(
				"route-surface-statue", "Route to King Rada I's Catacombs statue.",
				SlayerRoutePlan.RouteTarget.point(
					"Catacombs surface statue", surfaceStatue, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(surfaceStatue, 5), atWorld(catacombsAltar, 6), terminal),
				"route-catacombs-altar"))
			.addLeg(travel(
				"route-catacombs-altar",
				"Enter the Catacombs through the reviewed statue edge and route to the altar.",
				SlayerRoutePlan.RouteTarget.point(
					"Skotizo altar", catacombsAltar, 4),
				SlayerRoutePredicate.anyOf(atWorld(catacombsAltar, 6), terminal),
				"use-totem"))
			.addLeg(manual(
				"use-totem", "Use the completed dark totem on the altar.",
				SlayerRoutePlan.RouteTarget.point(
					"Skotizo altar", catacombsAltar, 4),
				"Use the completed dark totem on the altar.", altar,
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Skotizo encounter", terminal))
			.build();
	}

	private static SlayerRoutePlan godWars(
		final String id,
		final String faction,
		final SlayerRoutePredicate.ObjectActionSpec door,
		final String instruction,
		final SlayerRoutePredicate.SpatialArea roomArea,
		final String... npcNames)
	{
		final WorldPoint surface = point(2916, 3746, 0);
		final WorldPoint central = point(2882, 5311, 2);
		final SlayerRoutePredicate.SpatialArea templateRoom =
			templateCopyOf(roomArea);
		final SlayerRoutePredicate terminal = roomTerminal(
			npcNames, roomArea, templateRoom);
		return SlayerRoutePlan.builder(id)
			.startAt("route-surface")
			.activityRetention(retention(
				aroundWorld(surface, 28), aroundWorld(central, 36),
				roomArea, templateRoom))
			.addLeg(travel(
				"route-surface", "Route to the God Wars Dungeon surface entrance.",
				SlayerRoutePlan.RouteTarget.point(
					"God Wars Dungeon surface entrance", surface, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(surface, 5), atWorld(central, 8), terminal),
				"route-central"))
			.addLeg(travel(
				"route-central", "Descend into the God Wars Dungeon central chamber.",
				SlayerRoutePlan.RouteTarget.point("God Wars Dungeon central chamber", central, 6),
				SlayerRoutePredicate.anyOf(atWorld(central, 8), terminal),
				"boss-door"))
			.addLeg(manual(
				"boss-door", "Complete the " + faction + " boss-room handoff.",
				SlayerRoutePlan.RouteTarget.instruction(faction + " boss-room door"),
				instruction, door,
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", faction + " boss room", terminal))
			.build();
	}

	private static SlayerRoutePlan bandosGodWars()
	{
		final WorldPoint surface = point(2916, 3746, 0);
		final WorldPoint central = point(2882, 5311, 2);
		final SlayerRoutePredicate.SpatialArea roomArea =
			SlayerRoutePredicate.SpatialArea.world(2864, 2876, 5351, 5369, 2);
		final SlayerRoutePredicate.SpatialArea templateRoom =
			templateCopyOf(roomArea);
		final SlayerRoutePredicate terminal = roomTerminal(new String[] {
			"General Graardor", "Sergeant Strongstack", "Sergeant Steelwill",
			"Sergeant Grimspike"
		}, roomArea, templateRoom);
		final SlayerRoutePredicate.ObjectActionSpec barrier = reviewed(
			ids(ObjectID.GODWARS_ENTRANCE_PILLAR01,
				ObjectID.GODWARS_ICECAVE_BANDOS_DOOR),
			"Open", "Bang", "Pass");
		final SlayerRoutePredicate.ObjectActionSpec door = identified(
			ObjectID.GODWARS_DUNGEON_DOOR_BANDOS, "Open");
		return SlayerRoutePlan.builder("general-graardor-gwd")
			.startAt("route-surface")
			.activityRetention(retention(
				aroundWorld(surface, 28), aroundWorld(central, 36),
				roomArea, templateRoom))
			.addLeg(travel(
				"route-surface", "Route to the God Wars Dungeon surface entrance.",
				SlayerRoutePlan.RouteTarget.point(
					"God Wars Dungeon surface entrance", surface, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(surface, 5), atWorld(central, 8), terminal),
				"route-central"))
			.addLeg(travel(
				"route-central", "Descend into the God Wars Dungeon central chamber.",
				SlayerRoutePlan.RouteTarget.point("God Wars Dungeon central chamber", central, 6),
				SlayerRoutePredicate.anyOf(atWorld(central, 8), terminal),
				"bandos-barrier"))
			.addLeg(manual(
				"bandos-barrier", "Pass the Bandos barrier or gong.",
				SlayerRoutePlan.RouteTarget.instruction("Bandos barrier or gong"),
				"Pass the Bandos barrier or gong.", barrier,
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(door), terminal),
				"boss-door"))
			.addLeg(manual(
				"boss-door", "Complete the Bandos boss-room handoff.",
				SlayerRoutePlan.RouteTarget.instruction("Bandos boss-room door"),
				"Satisfy the kill-count or key requirement, then open the Bandos boss door.",
				door, terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Bandos boss room", terminal))
			.build();
	}

	private static SlayerRoutePlan thermonuclearSmokeDevil()
	{
		final WorldPoint surface = point(2411, 3058, 0);
		final WorldPoint bossRoom = point(2362, 9456, 0);
		final SlayerRoutePredicate dungeonArea = SlayerRoutePredicate.anyOf(
			SlayerRoutePredicate.worldArea(regionWorld(9363, 0)),
			SlayerRoutePredicate.worldArea(regionWorld(9619, 0)));
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			atWorld(bossRoom, 14), exactNpc("Thermonuclear smoke devil"));
		final SlayerRoutePredicate.ObjectActionSpec cave = identified(
			ObjectID.SMOKEDEVIL_CAVE_ENTRANCE, "Enter");
		final SlayerRoutePredicate.ObjectActionSpec crevice = identified(
			ObjectID.SLAYER_CAVE_SMOKEDEVIL_BOSS_ENTRANCE, "Enter");
		return SlayerRoutePlan.builder("thermonuclear-smoke-devil-dungeon")
			.startAt("route-cave")
			.activityRetention(retention(
				aroundWorld(surface, 28), regionWorld(9363, 0),
				regionWorld(9619, 0), aroundWorld(bossRoom, 24)))
			.addLeg(travel(
				"route-cave", "Route to the Smoke Devil Dungeon entrance.",
				SlayerRoutePlan.RouteTarget.point("Smoke Devil Dungeon entrance", surface, 4),
				SlayerRoutePredicate.anyOf(atWorld(surface, 5), dungeonArea, terminal),
				"enter-cave"))
			.addLeg(manual(
				"enter-cave", "Enter the Smoke Devil Dungeon.",
				SlayerRoutePlan.RouteTarget.point("Smoke Devil Dungeon entrance", surface, 4),
				"Enter the Smoke Devil Dungeon.", cave,
				SlayerRoutePredicate.anyOf(dungeonArea, terminal), "enter-boss-room"))
			.addLeg(manual(
				"enter-boss-room", "Enter the Thermonuclear smoke devil room.",
				SlayerRoutePlan.RouteTarget.instruction("Boss-room crevice"),
				"Enter the boss-room crevice.", crevice, terminal, "arrived"))
			.addLeg(terminalLeg(
				"arrived", "Thermonuclear smoke devil room", terminal))
			.build();
	}

	private static SlayerRoutePlan giantMole()
	{
		final WorldPoint mound = point(2996, 3377, 0);
		final WorldPoint landing = point(1759, 5189, 0);
		final SlayerRoutePredicate lairArea = SlayerRoutePredicate.anyOf(
			SlayerRoutePredicate.worldArea(regionWorld(6993, 0)),
			SlayerRoutePredicate.templateArea(regionTemplate(6993, 0)));
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			lairArea,
			SlayerRoutePredicate.allOf(exactNpc("Giant Mole"), lairArea));
		final SlayerRoutePredicate.ObjectActionSpec dig = reviewed(
			ids(ObjectID.MOLE_HILL, ObjectID.MOLE_HILL_SPADE), "Dig");
		return SlayerRoutePlan.builder("giant-mole-falador-lair")
			.startAt("route-mound")
			.activityRetention(retention(
				aroundWorld(mound, 24), regionWorld(6993, 0),
				regionTemplate(6993, 0)))
			.addLeg(travel(
				"route-mound", "Route to the Falador Park mole mound.",
				SlayerRoutePlan.RouteTarget.point("Giant Mole mound", mound, 3),
				atWorld(mound, 4), "dig"))
			.addLeg(manual(
				"dig", "Dig into the Falador Mole Lair.",
				SlayerRoutePlan.RouteTarget.point("Giant Mole mound", mound, 3),
				"Use a spade to dig at the mound.", dig,
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Falador Mole Lair", terminal))
			.build();
	}

	private static SlayerRoutePlan barrows()
	{
		final WorldPoint mounds = point(3565, 3289, 0);
		final SlayerRoutePredicate.ObjectActionSpec stairs = reviewed(
			ids(ObjectID.BARROWS_STAIRS_AHRIM, ObjectID.BARROWS_STAIRS_DHAROK,
				ObjectID.BARROWS_STAIRS_GUTHAN, ObjectID.BARROWS_STAIRS_KARIL,
				ObjectID.BARROWS_STAIRS_TORAG, ObjectID.BARROWS_STAIRS_VERAC),
			"Climb-down");
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			SlayerRoutePredicate.worldArea(regionWorld(14231, 0)),
			exactNpc("Ahrim the Blighted", "Dharok the Wretched",
				"Guthan the Infested", "Karil the Tainted",
				"Torag the Corrupted", "Verac the Defiled"));
		return SlayerRoutePlan.builder("barrows-brothers-dynamic")
			.startAt("route-mounds")
			.activityRetention(retention(
				aroundWorld(mounds, 56), regionWorld(14231, 0)))
			.addLeg(travel(
				"route-mounds", "Route to the Barrows mounds.",
				SlayerRoutePlan.RouteTarget.point("Barrows mounds", mounds, 10),
				atWorld(mounds, 12), "enter-active-mound"))
			.addLeg(manual(
				"enter-active-mound", "Enter the live Barrows crypt selected by game state.",
				SlayerRoutePlan.RouteTarget.point(
					"Barrows mound area", mounds, 12),
				"Use the staircase for the live Barrows brother. The tunnel doors and "
					+ "chest remain a live-state manual handoff.",
				stairs, terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Barrows live-state interior", terminal))
			.build();
	}

	private static SlayerRoutePlan dagannothKings()
	{
		final WorldPoint manifestAccess = point(2443, 3746, 0);
		final WorldPoint jarvaldDeparture = point(2621, 3683, 0);
		final WorldPoint jarvaldLanding = point(2544, 3761, 0);
		final WorldPoint shortcutBottom = point(2547, 3749, 0);
		final WorldPoint shortcutTop = point(2547, 3744, 0);
		final SlayerRoutePredicate bossArea = SlayerRoutePredicate.anyOf(
			SlayerRoutePredicate.worldArea(regionPairWorld(11588, 11589, 0)),
			SlayerRoutePredicate.templateArea(regionPairTemplate(11588, 11589, 0)));
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			bossArea,
			SlayerRoutePredicate.allOf(
				exactNpc("Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme"),
				bossArea));
		final SlayerRoutePredicate.ObjectActionSpec rock = identified(
			ObjectID.DAGANNOTH_CAVEENTRANCE_ROCK, "Climb-over");
		final SlayerRoutePredicate.ObjectActionSpec shortcut = reviewed(
			ids(ObjectID.DAGANNOTH_WATERBIRTH_ROCK_CLIMB_AGILITY_SHORTCUT_BOTTOM,
				ObjectID.DAGANNOTH_WATERBIRTH_ROCK_CLIMB_AGILITY_SHORTCUT_TOP),
			"Climb");
		final SlayerRoutePredicate.ObjectActionSpec pressureDoors = reviewed(
			ids(ObjectID.DAGANNOTH_PRESSURE_DOOR,
				ObjectID.DAGANNOTH_PRESSURE_DOOR2,
				ObjectID.DAGANNOTH_PRESSURE_DOOR3,
				ObjectID.DAGANNOTH_PRESSURE_OPEN),
			"Open");
		return SlayerRoutePlan.builder("dagannoth-kings-waterbirth")
			.startAt("route-manifest-access")
			.activityRetention(retention(
				aroundWorld(manifestAccess, 24),
				aroundWorld(jarvaldDeparture, 24),
				aroundWorld(jarvaldLanding, 36),
				regionPairWorld(11588, 11589, 0),
				regionPairTemplate(11588, 11589, 0)))
			.addLeg(travel(
				"route-manifest-access", "Route to the reviewed Waterbirth access point.",
				SlayerRoutePlan.RouteTarget.point(
					"Waterbirth Island Dungeon access", manifestAccess, 5),
				SlayerRoutePredicate.anyOf(
					atWorld(manifestAccess, 6), atWorld(jarvaldDeparture, 8),
					atWorld(jarvaldLanding, 10), terminal),
				"route-rock"))
			.addLeg(travel(
				"route-rock", "Travel with Jarvald and route to the Waterbirth rock.",
				SlayerRoutePlan.RouteTarget.point("Waterbirth dungeon rock", jarvaldLanding, 8),
				SlayerRoutePredicate.anyOf(atWorld(jarvaldLanding, 10), terminal),
				"climb-rock"))
			.addLeg(manual(
				"climb-rock", "Climb through the Waterbirth dungeon rock.",
				SlayerRoutePlan.RouteTarget.instruction("Waterbirth dungeon rock"),
				"Climb over the Waterbirth dungeon rock.", rock,
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(shortcut),
					SlayerRoutePredicate.trackedObjectAction(pressureDoors), terminal),
				"choose-interior-route"))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"choose-interior-route", SlayerRoutePlan.LegKind.DECISION)
				.guidance("Choose the reviewed agility shortcut when it is available.")
				.completeWhen(SlayerRoutePredicate.always())
				.thenWhen("85 Agility shortcut is available",
					SlayerRoutePredicate.trackedObjectAction(shortcut), "use-shortcut")
				.then("manual-interior-route")
				.build())
			.addLeg(manual(
				"use-shortcut", "Use the 85 Agility shortcut.",
				SlayerRoutePlan.RouteTarget.points(
					"Waterbirth Agility shortcut",
					Arrays.asList(shortcutBottom, shortcutTop), 3),
				"Climb the reviewed Waterbirth Agility shortcut.", shortcut,
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(pressureDoors), terminal),
				"shortcut-interior-route"))
			.addLeg(manual(
				"manual-interior-route",
				"Follow the manual pressure-door and thrownaxe route through the live dungeon.",
				SlayerRoutePlan.RouteTarget.instruction(
					"Pressure-door and thrownaxe route to the Kings"),
				"Carry and use a pet rock for the pressure doors and a Rune thrownaxe "
					+ "for the barrier; follow Shortest Path's live Waterbirth route.",
				pressureDoors, terminal, "arrived"))
			.addLeg(manual(
				"shortcut-interior-route",
				"Continue through the live Waterbirth interior after the shortcut.",
				SlayerRoutePlan.RouteTarget.instruction(
					"Waterbirth interior route to the Kings"),
				"Follow Shortest Path's live route through the remaining pressure doors "
					+ "until exact Dagannoth Kings arena evidence is present.",
				pressureDoors, terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Dagannoth Kings arena", terminal))
			.build();
	}

	private static SlayerRoutePlan maggotKing()
	{
		final WorldPoint castlePortal = point(3161, 7711, 0);
		final WorldPoint westScout = point(2575, 7818, 0);
		final WorldPoint northShortcut = point(2667, 7837, 0);
		final WorldPoint lairEntrance = point(2678, 7851, 0);
		final SlayerRoutePredicate lairTemplate =
			SlayerRoutePredicate.templateArea(regionTemplate(11645, 0));
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			lairTemplate,
			SlayerRoutePredicate.allOf(exactNpc("Maggot King"), lairTemplate));
		final SlayerRoutePredicate.ObjectActionSpec portal = reviewed(
			ids(MAGGOT_CASTLE_PORTAL_WRAPPER,
				MAGGOT_CASTLE_PORTAL_VISIBLE), "Enter");
		final SlayerRoutePredicate.ObjectActionSpec shortcut = identified(
			MAGGOT_VAMPYRIUM_BUSH_SHORTCUT, "Crawl-through");
		final SlayerRoutePredicate.ObjectActionSpec lair = identified(
			MAGGOT_LAIR_ENTRANCE, "Enter");
		final SlayerRoutePredicate vampyriumArrival = SlayerRoutePredicate.anyOf(
			SlayerRoutePredicate.worldArea(regionWorld(10106, 0)),
			SlayerRoutePredicate.worldArea(regionWorld(10362, 0)),
			SlayerRoutePredicate.worldArea(regionWorld(10618, 0)),
			SlayerRoutePredicate.trackedObjectAction(shortcut),
			SlayerRoutePredicate.trackedObjectAction(lair), terminal);
		return SlayerRoutePlan.builder("maggot-king-vampyrium")
			.startAt("route-castle-portal")
			.activityRetention(retention(
				aroundWorld(castlePortal, 24),
				regionWorld(10106, 0), regionWorld(10362, 0),
				regionWorld(10618, 0), regionTemplate(11645, 0)))
			.addLeg(travel(
				"route-castle-portal",
				"Use Drakan's medallion to Castle Drakan, then route to its portal.",
				SlayerRoutePlan.RouteTarget.point(
					"Castle Drakan portal", castlePortal, 4),
				SlayerRoutePredicate.anyOf(atWorld(castlePortal, 5),
					vampyriumArrival), "enter-vampyrium"))
			.addLeg(manual(
				"enter-vampyrium", "Enter the Castle Drakan portal.",
				SlayerRoutePlan.RouteTarget.point(
					"Castle Drakan portal", castlePortal, 4),
				"Enter the Castle Drakan portal.", portal,
				vampyriumArrival,
				"traverse-vampyrium"))
			.addLeg(travel(
				"traverse-vampyrium",
				"Follow the west Aranei scout when unlocked, or continue on foot toward "
					+ "the northern 64 Agility shortcut.",
				SlayerRoutePlan.RouteTarget.points(
					"Vampyrium traversal",
					Arrays.asList(westScout, northShortcut), 5),
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(shortcut),
					SlayerRoutePredicate.trackedObjectAction(lair),
					atWorld(northShortcut, 6), atWorld(lairEntrance, 8), terminal),
				"choose-vampyrium-shortcut"))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"choose-vampyrium-shortcut", SlayerRoutePlan.LegKind.DECISION)
				.guidance("Use the northern bush shortcut only when it is available.")
				.completeWhen(SlayerRoutePredicate.always())
				.thenWhen("64 Agility bush shortcut is available",
					SlayerRoutePredicate.trackedObjectAction(shortcut), "use-shortcut")
				.then("route-lair")
				.build())
			.addLeg(manual(
				"use-shortcut", "Use the northern Vampyrium bush shortcut.",
				SlayerRoutePlan.RouteTarget.point(
					"Northern Vampyrium bush shortcut", northShortcut, 3),
				"Crawl through the northern bush shortcut.", shortcut,
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(lair), terminal),
				"route-lair"))
			.addLeg(travel(
				"route-lair", "Route to the Maggot King's actual lair entrance.",
				SlayerRoutePlan.RouteTarget.point(
					"Maggot King's lair entrance", lairEntrance, 4),
				SlayerRoutePredicate.anyOf(atWorld(lairEntrance, 6), terminal),
				"enter-lair"))
			.addLeg(manual(
				"enter-lair", "Enter the darkwood doorway into the lair.",
				SlayerRoutePlan.RouteTarget.point(
					"Maggot King's lair entrance", lairEntrance, 4),
				"Enter the darkwood doorway.", lair,
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Maggot King lair template", terminal))
			.build();
	}

	private static SlayerRoutePlan magicAxes()
	{
		final WorldPoint westDoor = point(3190, 3957, 0);
		final WorldPoint eastDoor = point(3191, 3963, 0);
		final SlayerRoutePredicate terminal = exactNpc("Magic axe");
		final SlayerRoutePredicate.ObjectActionSpec door = identified(
			ObjectID.TOOLLOCK2, "Pick-lock", "Open");
		return manualEntrancePlan(
			"magic-axes-wilderness-hut", "Magic axe hut doors",
			Arrays.asList(westDoor, eastDoor), door,
			"Use a lockpick to pick the hut lock, or open the already unlocked door.",
			terminal, 24);
	}

	private static SlayerRoutePlan revenants()
	{
		final WorldPoint low = point(3073, 3654, 0);
		final WorldPoint middle = point(3067, 3740, 0);
		final WorldPoint high = point(3124, 3831, 0);
		final WorldPoint lowInterior = point(3197, 10070, 0);
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			atWorld(lowInterior, 12),
			exactNpc("Revenant imp", "Revenant goblin", "Revenant icefiend",
				"Revenant pyrefiend", "Revenant hobgoblin", "Revenant vampire",
				"Revenant werewolf", "Revenant cyclops", "Revenant hellhound",
				"Revenant demon", "Revenant ork", "Revenant dark beast",
				"Revenant knight", "Revenant dragon", "Revenant maledictus"));
		final SlayerRoutePredicate.ObjectActionSpec entrances = reviewed(
			ids(ObjectID.WILD_CAVE_ENTRANCE_LOW,
				ObjectID.WILD_CAVE_ENTRANCE_MID,
				ObjectID.WILD_CAVE_ENTRANCE_HIGH),
			"Enter", "Jump-down");
		return manualEntrancePlan(
			"revenants-wilderness-cave", "Revenant Caves entrance",
			Arrays.asList(low, middle, high), entrances,
			"Enter a reviewed Revenant Caves entrance and pay the live entrance fee "
				+ "when prompted.", terminal, 32);
	}

	private static SlayerRoutePlan strongholdFleshcrawlers()
	{
		return strongholdSecurity(
			"fleshcrawlers-stronghold-security", point(1941, 5097, 0),
			exactNpc("Flesh crawler"),
			new WorldPoint[] {point(3081, 3420, 0), point(1902, 5222, 0)},
			new String[] {"Stronghold entrance", "Vault of War ladder"},
			new SlayerRoutePredicate.ObjectActionSpec[] {
				identified(ObjectID.SOS_DUNG_ENT_OPEN, "Climb-down"),
				identified(ObjectID.SOS_WAR_LADD_DOWN, "Climb-down")
			});
	}

	private static SlayerRoutePlan strongholdCatablepon()
	{
		return strongholdSecurity(
			"catablepon-stronghold-security", point(1950, 4996, 0),
			exactNpc("Catablepon"),
			new WorldPoint[] {point(3081, 3420, 0), point(1902, 5222, 0),
				point(2026, 5218, 0)},
			new String[] {"Stronghold entrance", "Vault of War ladder",
				"Catacomb of Famine ladder"},
			new SlayerRoutePredicate.ObjectActionSpec[] {
				identified(ObjectID.SOS_DUNG_ENT_OPEN, "Climb-down"),
				identified(ObjectID.SOS_WAR_LADD_DOWN, "Climb-down"),
				identified(ObjectID.SOS_FAM_LADD_DOWN, "Climb-down")
			});
	}

	private static SlayerRoutePlan strongholdAnkou()
	{
		return strongholdSecurity(
			"ankou-stronghold-security", point(1972, 4910, 0), exactNpc("Ankou"),
			new WorldPoint[] {point(3081, 3420, 0), point(1902, 5222, 0),
				point(2026, 5218, 0), point(2148, 5284, 0)},
			new String[] {"Stronghold entrance", "Vault of War ladder",
				"Catacomb of Famine ladder", "Pit of Pestilence vine"},
			new SlayerRoutePredicate.ObjectActionSpec[] {
				identified(ObjectID.SOS_DUNG_ENT_OPEN, "Climb-down"),
				identified(ObjectID.SOS_WAR_LADD_DOWN, "Climb-down"),
				identified(ObjectID.SOS_FAM_LADD_DOWN, "Climb-down"),
				identified(ObjectID.SOS_PEST_LADD_DOWN, "Climb-down")
			});
	}

	private static SlayerRoutePlan strongholdSecurity(
		final String id,
		final WorldPoint reviewedTerminalPoint,
		final SlayerRoutePredicate exactTerminalNpc,
		final WorldPoint[] checkpoints,
		final String[] labels,
		final SlayerRoutePredicate.ObjectActionSpec[] interactions)
	{
		if (checkpoints.length != labels.length
			|| checkpoints.length != interactions.length)
		{
			throw new IllegalArgumentException("Stronghold route arrays must align");
		}
		final WorldPoint[] reviewedLandings = {
			point(1859, 5244, 0),
			point(2042, 5245, 0),
			point(2123, 5252, 0),
			point(2358, 5215, 0)
		};
		if (checkpoints.length > reviewedLandings.length)
		{
			throw new IllegalArgumentException(
				"Stronghold route has no reviewed landing for every checkpoint"
			);
		}
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			atWorld(reviewedTerminalPoint, 12), exactTerminalNpc);
		final SlayerRoutePredicate.SpatialArea[] retained =
			new SlayerRoutePredicate.SpatialArea[checkpoints.length * 2 + 1];
		for (int index = 0; index < checkpoints.length; index++)
		{
			retained[index * 2] = aroundWorld(checkpoints[index], 24);
			retained[index * 2 + 1] = aroundWorld(reviewedLandings[index], 24);
		}
		retained[retained.length - 1] = aroundWorld(reviewedTerminalPoint, 24);
		final SlayerRoutePlan.Builder builder = SlayerRoutePlan.builder(id)
			.startAt("route-0")
			.activityRetention(retention(retained));
		for (int index = 0; index < checkpoints.length; index++)
		{
			final String routeId = "route-" + index;
			final String interactionId = "interact-" + index;
			final String next = index + 1 < checkpoints.length
				? "route-" + (index + 1) : "route-terminal";
			builder.addLeg(travel(
				routeId, "Route to " + labels[index] + ".",
				SlayerRoutePlan.RouteTarget.point(labels[index], checkpoints[index], 4),
				SlayerRoutePredicate.anyOf(atWorld(checkpoints[index], 5), terminal),
				interactionId));
			builder.addLeg(manual(
				interactionId, "Use " + labels[index] + ".",
				SlayerRoutePlan.RouteTarget.point(labels[index], checkpoints[index], 4),
				"Use the reviewed " + labels[index] + ".", interactions[index],
				SlayerRoutePredicate.anyOf(
					atWorld(reviewedLandings[index], 10), terminal),
				next));
		}
		return builder
			.addLeg(travel(
				"route-terminal", "Route from the final Stronghold landing to the assignment pack.",
				SlayerRoutePlan.RouteTarget.point(
					"Stronghold assignment pack", reviewedTerminalPoint, 8),
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Stronghold assignment pack", terminal))
			.build();
	}

	private static SlayerRoutePlan ancientCavernMetalDragons()
	{
		final WorldPoint whirlpoolPoint = point(2512, 3511, 0);
		final WorldPoint upperStairs = point(1769, 5365, 1);
		final WorldPoint lowerStairs = point(1778, 5344, 0);
		final WorldPoint mithrilPack = point(1819, 5390, 1);
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			atWorld(mithrilPack, 14), exactNpc("Mithril dragon"));
		final SlayerRoutePredicate.ObjectActionSpec whirlpool = identified(
			ObjectID.BRUT_WHIRLPOOL, "Dive-into", "Dive in", "Enter");
		final SlayerRoutePredicate.ObjectActionSpec descend = identified(
			ObjectID.BRUT_STAIR_LRG_TOP, "Climb-down");
		final SlayerRoutePredicate.ObjectActionSpec ascend = identified(
			ObjectID.BRUT_CAVE_STAIRS_LOW, "Climb-up");
		return threeCheckpointPlan(
			"metal-dragons-ancient-cavern", terminal,
			new WorldPoint[] {whirlpoolPoint, upperStairs, lowerStairs},
			new WorldPoint[] {upperStairs, lowerStairs, point(1778, 5344, 1)},
			new String[] {"Ancient Cavern whirlpool", "upper Ancient Cavern stairs",
				"lower Ancient Cavern stairs"},
			new SlayerRoutePredicate.ObjectActionSpec[] {whirlpool, descend, ascend},
			"Ancient Cavern Mithril dragon pack", mithrilPack);
	}

	private static SlayerRoutePlan godWarsAssignment(
		final String id,
		final WorldPoint assignmentPack,
		final SlayerRoutePredicate exactTerminalNpc)
	{
		final WorldPoint entrance = point(2916, 3746, 0);
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			atWorld(assignmentPack, 18), exactTerminalNpc);
		final SlayerRoutePredicate.ObjectActionSpec hole = reviewed(
			ids(ObjectID.GODWARS_ENTRANCE_GROUNDHOLE01,
				ObjectID.GODWARS_ENTRANCE_GROUNDHOLE02,
				ObjectID.GODWARS_ENTRANCE_MULTI),
			"Climb-down", "Enter", "Use", "Attach-rope");
		return manualEntrancePlan(
			id, "God Wars Dungeon entrance", Collections.singletonList(entrance),
			hole, "Attach a rope on first use, then descend into God Wars Dungeon.",
			terminal, 40);
	}

	private static SlayerRoutePlan waterbirthDagannoth()
	{
		final WorldPoint rockPoint = point(2443, 3746, 0);
		final WorldPoint shortcutBottom = point(2547, 3749, 0);
		final WorldPoint shortcutTop = point(2547, 3744, 0);
		final WorldPoint pack = point(2545, 10141, 0);
		final SlayerRoutePredicate packArea = atWorld(pack, 20);
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			atWorld(pack, 12),
			SlayerRoutePredicate.allOf(exactNpc("Dagannoth"), packArea));
		final SlayerRoutePredicate.ObjectActionSpec rock = identified(
			ObjectID.DAGANNOTH_CAVEENTRANCE_ROCK, "Climb-over");
		final SlayerRoutePredicate.ObjectActionSpec shortcut = reviewed(
			ids(ObjectID.DAGANNOTH_WATERBIRTH_ROCK_CLIMB_AGILITY_SHORTCUT_BOTTOM,
				ObjectID.DAGANNOTH_WATERBIRTH_ROCK_CLIMB_AGILITY_SHORTCUT_TOP),
			"Climb");
		final SlayerRoutePredicate.ObjectActionSpec pressureDoors = reviewed(
			ids(ObjectID.DAGANNOTH_PRESSURE_DOOR,
				ObjectID.DAGANNOTH_PRESSURE_DOOR2,
				ObjectID.DAGANNOTH_PRESSURE_DOOR3,
				ObjectID.DAGANNOTH_PRESSURE_OPEN),
			"Open");
		return SlayerRoutePlan.builder("dagannoth-waterbirth-assignment")
			.startAt("route-rock")
			.activityRetention(retention(
				aroundWorld(rockPoint, 28), aroundWorld(shortcutBottom, 28),
				aroundWorld(pack, 32)))
			.addLeg(travel(
				"route-rock", "Route to the Waterbirth dungeon rock.",
				SlayerRoutePlan.RouteTarget.point(
					"Waterbirth dungeon rock", rockPoint, 5),
				SlayerRoutePredicate.anyOf(atWorld(rockPoint, 6), terminal),
				"climb-rock"))
			.addLeg(manual(
				"climb-rock", "Climb through the Waterbirth dungeon rock.",
				SlayerRoutePlan.RouteTarget.point(
					"Waterbirth dungeon rock", rockPoint, 5),
				"Climb over the Waterbirth dungeon rock.", rock,
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(shortcut),
					SlayerRoutePredicate.trackedObjectAction(pressureDoors), terminal),
				"route-shortcut"))
			.addLeg(travel(
				"route-shortcut", "Route toward the reviewed 85 Agility shortcut.",
				SlayerRoutePlan.RouteTarget.points(
					"Waterbirth Agility shortcut",
					Arrays.asList(shortcutBottom, shortcutTop), 4),
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(shortcut), terminal),
				"choose-interior-route"))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"choose-interior-route", SlayerRoutePlan.LegKind.DECISION)
				.guidance("Use the 85 Agility shortcut when available.")
				.completeWhen(SlayerRoutePredicate.always())
				.thenWhen("85 Agility shortcut is available",
					SlayerRoutePredicate.trackedObjectAction(shortcut), "use-shortcut")
				.then("manual-interior-route")
				.build())
			.addLeg(manual(
				"use-shortcut", "Use the Waterbirth Agility shortcut.",
				SlayerRoutePlan.RouteTarget.points(
					"Waterbirth Agility shortcut",
					Arrays.asList(shortcutBottom, shortcutTop), 4),
				"Climb the reviewed Waterbirth Agility shortcut.", shortcut,
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(pressureDoors), terminal),
				"route-pack"))
			.addLeg(manual(
				"manual-interior-route",
				"Follow the manual pressure-door, pet-rock, and thrownaxe route.",
				SlayerRoutePlan.RouteTarget.instruction(
					"Pressure-door and thrownaxe route"),
				"Carry and use a pet rock for the pressure doors and a Rune thrownaxe "
					+ "for the barrier; follow Shortest Path's live Waterbirth route.",
				pressureDoors, terminal, "arrived"))
			.addLeg(travel(
				"route-pack", "Continue from the shortcut to the Dagannoth pack.",
				SlayerRoutePlan.RouteTarget.point("Waterbirth Dagannoth pack", pack, 8),
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Waterbirth Dagannoth pack", terminal))
			.build();
	}

	private static SlayerRoutePlan caveKraken()
	{
		final WorldPoint entrancePoint = point(2277, 3611, 0);
		final SlayerRoutePredicate terminal = exactNpc("Cave kraken");
		return manualEntrancePlan(
			"cave-kraken-kraken-cove", "Kraken Cove entrance",
			Collections.singletonList(entrancePoint),
			identified(ObjectID.SLAYER_CAVE_KRAKEN_MAINCAVE_ENTRANCE, "Enter"),
			"Enter Kraken Cove; do not use the separate boss crevice.",
			terminal, 32);
	}

	private static SlayerRoutePlan araxytes()
	{
		final WorldPoint entrancePoint = point(3656, 3408, 0);
		return manualEntrancePlan(
			"araxytes-morytania-spider-cave", "Morytania Spider Cave entrance",
			Collections.singletonList(entrancePoint),
			identified(ObjectID.ARAXYTE_CAVE_ENTRY, "Enter"),
			"Enter the Morytania Spider Cave.", exactNpc("Araxyte"), 36);
	}

	private static SlayerRoutePlan frostDragons()
	{
		final WorldPoint entrance = point(2912, 4066, 0);
		final WorldPoint landing = point(2886, 10480, 0);
		/* Grimstone crosses the west/east map-region boundary. DLP/boat and cave
		 * entries can place the player in either reviewed half. */
		final SlayerRoutePredicate.SpatialArea dungeonWest = regionWorld(11427, 0);
		final SlayerRoutePredicate.SpatialArea dungeonEast = regionWorld(11683, 0);
		final SlayerRoutePredicate dungeon = SlayerRoutePredicate.anyOf(
			SlayerRoutePredicate.worldArea(dungeonWest),
			SlayerRoutePredicate.worldArea(dungeonEast));
		/* Center of the ordinary east-side pack. The old 2851,10455 endpoint
		 * pointed toward the separate western task-only section and the old plan
		 * never represented this entrance landing at all. */
		final WorldPoint pack = point(2901, 10463, 0);
		final SlayerRoutePredicate terminal = atWorld(pack, 14);
		return SlayerRoutePlan.builder("frost-dragons-grimstone")
			.startAt("route-entrance")
			.activityRetention(retention(
				aroundWorld(entrance, 28), aroundWorld(landing, 24),
				aroundWorld(pack, 28), dungeonWest, dungeonEast))
			.addLeg(travel(
				"route-entrance", "Route to the Grimstone Dungeon entrance.",
				SlayerRoutePlan.RouteTarget.point(
					"Grimstone Dungeon entrance", entrance, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(entrance, 5),
					dungeon, terminal),
				"enter-dungeon"))
			.addLeg(manual(
				"enter-dungeon", "Enter Grimstone Dungeon.",
				SlayerRoutePlan.RouteTarget.point(
					"Grimstone Dungeon entrance", entrance, 4),
				"Enter the reviewed Grimstone Dungeon cave entrance.",
				identified(ObjectID.GRIMSTONE_CAVE_ENTRANCE, "Enter"),
				SlayerRoutePredicate.anyOf(
					dungeon, terminal),
				"route-pack"))
			.addLeg(travel(
				"route-pack", "Continue from the cave landing to the main Frost dragon pack.",
				SlayerRoutePlan.RouteTarget.point(
					"main Frost dragon pack", pack, 8),
				terminal, "arrived"))
			.addLeg(terminalLeg(
				"arrived", "main Frost dragon pack", terminal))
			.build();
	}

	private static SlayerRoutePlan catacombsGhosts()
	{
		final WorldPoint surfaceStatue = point(1639, 3673, 0);
		final WorldPoint catacombsLanding = point(1666, 10050, 0);
		final SlayerRoutePredicate.SpatialArea ghostRoom = regionWorld(6813, 0);
		final SlayerRoutePredicate.ObjectActionSpec statue = identified(
			ObjectID.ZEAH_KOUREND_STATUE_PLINTH, "Investigate");
		/* This is the center of the larger, official ten-Ghost cluster. The old
		 * 1639,10080 target has no Ghost spawn near it. */
		final WorldPoint pack = point(1689, 10063, 0);
		final SlayerRoutePredicate terminal = atWorld(pack, 14);
		return SlayerRoutePlan.builder("ghosts-catacombs")
			.startAt("route-surface-statue")
			.activityRetention(retention(
				aroundWorld(surfaceStatue, 28),
				aroundWorld(catacombsLanding, 28), aroundWorld(pack, 28), ghostRoom))
			.addLeg(travel(
				"route-surface-statue", "Route to King Rada I's Catacombs statue.",
				SlayerRoutePlan.RouteTarget.point(
					"Catacombs surface statue", surfaceStatue, 4),
				SlayerRoutePredicate.anyOf(
					atWorld(surfaceStatue, 5),
					SlayerRoutePredicate.worldArea(ghostRoom),
					terminal),
				"enter-catacombs"))
			.addLeg(manual(
				"enter-catacombs", "Investigate King Rada I's statue.",
				SlayerRoutePlan.RouteTarget.point(
					"Catacombs surface statue", surfaceStatue, 4),
				"Investigate King Rada I's statue to enter the Catacombs.",
				statue,
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.worldArea(ghostRoom), terminal),
				"route-ghost-pack"))
			.addLeg(travel(
				"route-ghost-pack",
				"Enter through the reviewed statue edge and continue east to the Ghost pack.",
				SlayerRoutePlan.RouteTarget.point(
					"Catacombs Ghost pack", pack, 8),
				terminal, "arrived"))
			.addLeg(terminalLeg(
				"arrived", "Catacombs Ghost pack", terminal))
			.build();
	}

	private static SlayerRoutePlan scabarites()
	{
		final WorldPoint temple = point(3315, 2796, 0);
		final WorldPoint lowerLadder = point(2800, 5159, 0);
		final WorldPoint pack = point(2140, 4392, 2);
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			atWorld(pack, 20), exactNpc("Locust rider", "Scarab mage"));
		return twoCheckpointPlan(
			"scabarites-sophanem-dungeon", terminal,
			temple, "Sophanem temple ladder",
			identified(ObjectID.CONTACT_TEMPLE_TRAPDOOR_OPEN, "Climb-down"),
			lowerLadder,
			lowerLadder, "lower Sophanem ladder",
			identified(ObjectID.CONTACT_BANK_FIXED_LADDER_TOP, "Climb-down"),
			point(2166, 4409, 2),
			"Scabarite pack", pack);
	}

	private static SlayerRoutePlan shadowWarriors()
	{
		final WorldPoint stairs = point(2728, 3348, 0);
		return manualEntrancePlan(
			"shadow-warriors-legends-basement", "Legends' Guild basement stairs",
			Collections.singletonList(stairs),
			identified(ObjectID.STAIRS_CELLAR, "Climb-down"),
			"Climb down the Legends' Guild basement stairs.",
			exactNpc("Shadow warrior"), 28);
	}

	private static SlayerRoutePlan warpedCreatures()
	{
		final WorldPoint sewer = point(2322, 3099, 0);
		return manualEntrancePlan(
			"warped-creatures-poison-waste", "Poison Waste sewer entrance",
			Collections.singletonList(sewer),
			identified(ObjectID.POG_CANYON_SEWER_ENTRANCE_LOWER_02,
				"Enter", "Climb-down"),
			"Use the Poison Waste spirit tree route, then enter the sewer.",
			exactNpc("Warped terrorbird", "Warped tortoise"), 36);
	}

	private static SlayerRoutePlan frostNagua()
	{
		final WorldPoint entrancePoint = point(1693, 3230, 0);
		final WorldPoint ruinsLanding = point(1693, 9630, 2);
		final WorldPoint upperChain = point(1670, 9631, 2);
		final WorldPoint lowerChain = point(1670, 9631, 0);
		final WorldPoint frostNaguaPack = point(1638, 9630, 0);
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			atWorld(frostNaguaPack, 16), exactNpc("Frost Nagua"));
		final SlayerRoutePredicate.ObjectActionSpec entrance = identified(
			ObjectID.TAPOYAUIK_TEMPLE_ENTRANCE, "Enter", "Climb-down");
		final SlayerRoutePredicate.ObjectActionSpec top = identified(
			ObjectID.TAPOYAUIK_POST_QUEST_SHORTCUT_TOP, "Climb-down");
		final SlayerRoutePredicate.ObjectActionSpec blocked = identified(
			ObjectID.TAPOYAUIK_POST_QUEST_SHORTCUT_BOTTOM_BLOCKED, "Pull");
		final SlayerRoutePredicate.ObjectActionSpec clear = identified(
			ObjectID.TAPOYAUIK_POST_QUEST_SHORTCUT_BOTTOM_CLEAR,
			"Climb-down", "Climb");
		return SlayerRoutePlan.builder("frost-nagua-tapoyauik")
			.startAt("route-entrance")
			.activityRetention(retention(
				aroundWorld(entrancePoint, 28), aroundWorld(ruinsLanding, 28),
				aroundWorld(upperChain, 28), aroundWorld(lowerChain, 28),
				aroundWorld(frostNaguaPack, 28)))
			.addLeg(travel(
				"route-entrance", "Route to the Ruins of Tapoyauik entrance.",
				SlayerRoutePlan.RouteTarget.point(
					"Ruins of Tapoyauik entrance", entrancePoint, 4),
				SlayerRoutePredicate.anyOf(atWorld(entrancePoint, 5), terminal),
				"enter-ruins"))
			.addLeg(manual(
				"enter-ruins", "Enter the Ruins of Tapoyauik.",
				SlayerRoutePlan.RouteTarget.point(
					"Ruins of Tapoyauik entrance", entrancePoint, 4),
				"Enter the Ruins of Tapoyauik.", entrance,
				SlayerRoutePredicate.anyOf(atWorld(ruinsLanding, 8), terminal),
				"route-upper-chain"))
			.addLeg(travel(
				"route-upper-chain", "Route to the upper post-quest chain.",
				SlayerRoutePlan.RouteTarget.point("Upper Tapoyauik chain", upperChain, 4),
				SlayerRoutePredicate.anyOf(atWorld(upperChain, 5), terminal),
				"use-upper-chain"))
			.addLeg(manual(
				"use-upper-chain", "Climb down the upper chain.",
				SlayerRoutePlan.RouteTarget.point("Upper Tapoyauik chain", upperChain, 4),
				"Climb down the upper post-quest chain.", top,
				SlayerRoutePredicate.anyOf(atWorld(lowerChain, 6), terminal),
				"find-lower-chain"))
			.addLeg(travel(
				"find-lower-chain", "Locate the lower shortcut chain.",
				SlayerRoutePlan.RouteTarget.point("Lower Tapoyauik chain", lowerChain, 4),
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(blocked),
					SlayerRoutePredicate.trackedObjectAction(clear), terminal),
				"choose-lower-chain"))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"choose-lower-chain", SlayerRoutePlan.LegKind.DECISION)
				.guidance("Unlock the lower chain when it is still blocked.")
				.completeWhen(SlayerRoutePredicate.always())
				.thenWhen("Lower chain must be unlocked",
					SlayerRoutePredicate.trackedObjectAction(blocked), "pull-chain")
				.then("descend-chain")
				.build())
			.addLeg(manual(
				"pull-chain", "Pull the blocked lower chain.",
				SlayerRoutePlan.RouteTarget.instruction("Blocked lower Tapoyauik chain"),
				"Pull the lower chain to unlock it.", blocked,
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.trackedObjectAction(clear), terminal),
				"descend-chain"))
			.addLeg(manual(
				"descend-chain", "Use the unlocked lower chain.",
				SlayerRoutePlan.RouteTarget.instruction("Unlocked lower Tapoyauik chain"),
				"Climb down the unlocked lower chain.", clear,
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Frost Nagua pack", terminal))
			.build();
	}

	private static SlayerRoutePlan aquanites()
	{
		final WorldPoint rowboatPoint = point(2218, 3424, 0);
		final WorldPoint islandLanding = point(2227, 3469, 0);
		final WorldPoint cavePoint = point(2218, 3478, 0);
		final WorldPoint cavernLanding = point(2293, 9913, 0);
		final WorldPoint aquanitePack = point(2275, 9880, 0);
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			atWorld(aquanitePack, 18), exactNpc("Aquanite", "Elder aquanite"));
		final SlayerRoutePredicate.ObjectActionSpec cave = identified(
			ObjectID.YNYSDAIL_CAVE_ENTRANCE, "Enter");
		return SlayerRoutePlan.builder("aquanites-ynysdail-cavern")
			.startAt("travel-to-ynysdail")
			.activityRetention(retention(
				aroundWorld(rowboatPoint, 28), aroundWorld(islandLanding, 28),
				aroundWorld(cavePoint, 32), aroundWorld(cavernLanding, 28),
				aroundWorld(aquanitePack, 32)))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"travel-to-ynysdail", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Use the built Ynysdail rowboat, or sail a player ship to Ynysdail.")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Ynysdail rowboat or player ship", rowboatPoint, 5))
				.completeWhen(SlayerRoutePredicate.anyOf(
					atWorld(islandLanding, 24),
					SlayerRoutePredicate.trackedObjectAction(cave), terminal))
				.then("route-cave")
				.build())
			.addLeg(travel(
				"route-cave", "Route from the Ynysdail landing to the cavern.",
				SlayerRoutePlan.RouteTarget.point("Ynysdail Cavern entrance", cavePoint, 4),
				SlayerRoutePredicate.anyOf(atWorld(cavePoint, 5), terminal),
				"enter-cave"))
			.addLeg(manual(
				"enter-cave", "Enter Ynysdail Cavern.",
				SlayerRoutePlan.RouteTarget.point("Ynysdail Cavern entrance", cavePoint, 4),
				"Enter Ynysdail Cavern.", cave,
				SlayerRoutePredicate.anyOf(atWorld(cavernLanding, 18), terminal),
				"route-pack"))
			.addLeg(travel(
				"route-pack", "Route from the cavern entrance to the Aquanite pack.",
				SlayerRoutePlan.RouteTarget.point(
					"Ynysdail Aquanite pack", aquanitePack, 8),
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", "Ynysdail Aquanite pack", terminal))
			.build();
	}

	private static SlayerRoutePlan killerwatts()
	{
		final WorldPoint groundStairs = point(3108, 3362, 0);
		final WorldPoint spiralStairs = point(3104, 3362, 1);
		final WorldPoint portalPoint = point(3111, 3363, 2);
		return threeCheckpointPlan(
			"killerwatts-draynor-manor", exactNpc("Killerwatt"),
			new WorldPoint[] {groundStairs, spiralStairs, portalPoint},
			new WorldPoint[] {spiralStairs, portalPoint, point(2678, 5214, 2)},
			new String[] {"Draynor Manor stairs", "Draynor Manor spiral stairs",
				"Killerwatt portal"},
			new SlayerRoutePredicate.ObjectActionSpec[] {
				identified(ObjectID.DRAYNOR_MANOR_STAIRS_UP, "Climb-up"),
				identified(ObjectID.DRAYNOR_SPIRALSTAIRS, "Climb-up"),
				identified(ObjectID.DRAYNOR_KILLERWATT_PORTAL, "Enter")
			}, "Killerwatt plane", point(2642, 5202, 2));
	}

	private static SlayerRoutePlan zanaris(
		final String id,
		final SlayerRoutePredicate terminal)
	{
		final WorldPoint shed = point(3202, 3169, 0);
		return manualEntrancePlan(
			id, "Lumbridge Swamp shed", Collections.singletonList(shed),
			identified(ObjectID.ZANARISDOOR, "Open"),
			"Open the shed door while wielding a Dramen or Lunar staff.",
			terminal, 32);
	}

	private static SlayerRoutePlan custodianStalkers()
	{
		final WorldPoint southEntrance = point(1324, 3364, 0);
		return manualEntrancePlan(
			"custodian-stalkers-stalker-den", "south Stalker Den entrance",
			Collections.singletonList(southEntrance),
			identified(ObjectID.STALKER_DEN_EXTERNAL_SHORTCUT_OUTSIDE,
				"Squeeze-through"),
			"Squeeze through the reviewed south entrance.",
			exactNpc("Juvenile custodian stalker", "Mature custodian stalker",
				"Elder custodian stalker"), 36);
	}

	private static SlayerRoutePlan manualEntrancePlan(
		final String id,
		final String targetLabel,
		final List<WorldPoint> approaches,
		final SlayerRoutePredicate.ObjectActionSpec checkpoint,
		final String instruction,
		final SlayerRoutePredicate terminal,
		final int retentionRadius)
	{
		final List<SlayerRoutePredicate> approachPredicates = new ArrayList<>();
		final SlayerRoutePredicate.SpatialArea[] retained =
			new SlayerRoutePredicate.SpatialArea[approaches.size()];
		for (int index = 0; index < approaches.size(); index++)
		{
			approachPredicates.add(atWorld(approaches.get(index), 5));
			retained[index] = aroundWorld(approaches.get(index), retentionRadius);
		}
		approachPredicates.add(terminal);
		return SlayerRoutePlan.builder(id)
			.startAt("route-checkpoint")
			.activityRetention(retention(retained))
			.addLeg(travel(
				"route-checkpoint", "Route to " + targetLabel + ".",
				SlayerRoutePlan.RouteTarget.points(targetLabel, approaches, 4),
				SlayerRoutePredicate.anyOf(approachPredicates), "interact"))
			.addLeg(manual(
				"interact", instruction,
				SlayerRoutePlan.RouteTarget.points(targetLabel, approaches, 4),
				instruction, checkpoint, terminal, "arrived"))
			.addLeg(terminalLeg("arrived", targetLabel, terminal))
			.build();
	}

	private static SlayerRoutePlan twoCheckpointPlan(
		final String id,
		final SlayerRoutePredicate terminal,
		final WorldPoint firstPoint,
		final String firstLabel,
		final SlayerRoutePredicate.ObjectActionSpec first,
		final WorldPoint firstResultPoint,
		final WorldPoint secondPoint,
		final String secondLabel,
		final SlayerRoutePredicate.ObjectActionSpec second,
		final WorldPoint secondResultPoint,
		final String terminalLabel,
		final WorldPoint terminalPoint)
	{
		return checkpointChain(
			id, terminal, new WorldPoint[] {firstPoint, secondPoint},
			new WorldPoint[] {firstResultPoint, secondResultPoint},
			new String[] {firstLabel, secondLabel},
			new SlayerRoutePredicate.ObjectActionSpec[] {first, second},
			terminalLabel, terminalPoint);
	}

	private static SlayerRoutePlan threeCheckpointPlan(
		final String id,
		final SlayerRoutePredicate terminal,
		final WorldPoint[] points,
		final WorldPoint[] resultPoints,
		final String[] labels,
		final SlayerRoutePredicate.ObjectActionSpec[] interactions,
		final String terminalLabel,
		final WorldPoint terminalPoint)
	{
		return checkpointChain(
			id, terminal, points, resultPoints, labels, interactions,
			terminalLabel, terminalPoint);
	}

	private static SlayerRoutePlan checkpointChain(
		final String id,
		final SlayerRoutePredicate terminal,
		final WorldPoint[] points,
		final WorldPoint[] resultPoints,
		final String[] labels,
		final SlayerRoutePredicate.ObjectActionSpec[] interactions,
		final String terminalLabel,
		final WorldPoint terminalPoint)
	{
		if (points.length != resultPoints.length
			|| points.length != labels.length
			|| points.length != interactions.length)
		{
			throw new IllegalArgumentException("Route checkpoint arrays must align");
		}
		final SlayerRoutePredicate.SpatialArea[] retained =
			new SlayerRoutePredicate.SpatialArea[points.length * 2 + 1];
		for (int index = 0; index < points.length; index++)
		{
			retained[index * 2] = aroundWorld(points[index], 24);
			retained[index * 2 + 1] = aroundWorld(resultPoints[index], 24);
		}
		retained[retained.length - 1] = aroundWorld(terminalPoint, 28);
		final SlayerRoutePlan.Builder builder = SlayerRoutePlan.builder(id)
			.startAt("route-0")
			.activityRetention(retention(retained));
		for (int index = 0; index < points.length; index++)
		{
			final String routeId = "route-" + index;
			final String interactionId = "interact-" + index;
			final String next = index + 1 < points.length
				? "route-" + (index + 1) : "route-terminal";
			builder.addLeg(travel(
				routeId, "Route to " + labels[index] + ".",
				SlayerRoutePlan.RouteTarget.point(labels[index], points[index], 4),
				SlayerRoutePredicate.anyOf(atWorld(points[index], 5), terminal),
				interactionId));
			builder.addLeg(manual(
				interactionId, "Use " + labels[index] + ".",
				SlayerRoutePlan.RouteTarget.point(labels[index], points[index], 4),
				"Use the reviewed " + labels[index] + ".", interactions[index],
				SlayerRoutePredicate.anyOf(
					atWorld(resultPoints[index], 8), terminal), next));
		}
		return builder
			.addLeg(travel(
				"route-terminal", "Route from the final handoff to " + terminalLabel + ".",
				SlayerRoutePlan.RouteTarget.point(terminalLabel, terminalPoint, 8),
				terminal, "arrived"))
			.addLeg(terminalLeg("arrived", terminalLabel, terminal))
			.build();
	}

	private static SlayerRoutePlan singleManual(
		final String id,
		final WorldPoint approach,
		final String targetLabel,
		final SlayerRoutePredicate.ObjectActionSpec checkpoint,
		final String instruction,
		final SlayerRoutePredicate terminal,
		final int retentionRadius)
	{
		return singleManualWithRetention(
			id, approach, targetLabel, checkpoint, instruction, terminal,
			new SlayerRoutePredicate.SpatialArea[] {
				aroundWorld(approach, retentionRadius)
			});
	}

	private static SlayerRoutePlan singleManualWithRetention(
		final String id,
		final WorldPoint approach,
		final String targetLabel,
		final SlayerRoutePredicate.ObjectActionSpec checkpoint,
		final String instruction,
		final SlayerRoutePredicate terminal,
		final SlayerRoutePredicate.SpatialArea[] retentionAreas)
	{
		return SlayerRoutePlan.builder(id)
			.startAt("route-checkpoint")
			.activityRetention(retention(retentionAreas))
			.addLeg(travel(
				"route-checkpoint", "Route to " + targetLabel + ".",
				SlayerRoutePlan.RouteTarget.point(targetLabel, approach, 4),
				SlayerRoutePredicate.anyOf(atWorld(approach, 5), terminal),
				"interact"))
			.addLeg(manual(
				"interact", instruction,
				SlayerRoutePlan.RouteTarget.instruction(targetLabel),
				instruction, checkpoint, terminal, "arrived"))
			.addLeg(terminalLeg("arrived", targetLabel, terminal))
			.build();
	}

	private static SlayerRoutePlan.Leg travel(
		final String id,
		final String guidance,
		final SlayerRoutePlan.RouteTarget target,
		final SlayerRoutePredicate completion,
		final String next)
	{
		return SlayerRoutePlan.Leg.builder(id, SlayerRoutePlan.LegKind.TRAVEL)
			.guidance(guidance)
			.target(target)
			.completeWhen(completion)
			.then(next)
			.build();
	}

	private static SlayerRoutePlan.Leg manual(
		final String id,
		final String guidance,
		final SlayerRoutePlan.RouteTarget target,
		final String instruction,
		final SlayerRoutePredicate.ObjectActionSpec checkpoint,
		final SlayerRoutePredicate completion,
		final String next)
	{
		return SlayerRoutePlan.Leg.builder(
				id, SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
			.guidance(guidance)
			.target(target)
			.interactionInstruction(instruction)
			.readyWhen(SlayerRoutePredicate.trackedObjectAction(checkpoint))
			.completeWhen(completion)
			.then(next)
			.build();
	}

	private static SlayerRoutePlan.Leg terminalLeg(
		final String id,
		final String label,
		final SlayerRoutePredicate completion)
	{
		return SlayerRoutePlan.Leg.builder(id, SlayerRoutePlan.LegKind.TERMINAL)
			.guidance("Encounter arrival requires " + label + " evidence.")
			.terminal(SlayerRoutePlan.TerminalSpec.of(label, completion))
			.build();
	}

	private static SlayerRoutePredicate exactNpc(final String... names)
	{
		return SlayerRoutePredicate.exactNpc(names);
	}

	private static SlayerRoutePredicate roomTerminal(
		final SlayerRoutePredicate.SpatialArea roomArea,
		final String... npcNames)
	{
		return roomTerminal(npcNames, roomArea);
	}

	private static SlayerRoutePredicate roomTerminal(
		final String[] npcNames,
		final SlayerRoutePredicate.SpatialArea... roomAreas)
	{
		final List<SlayerRoutePredicate> roomPredicates = new ArrayList<>();
		for (final SlayerRoutePredicate.SpatialArea roomArea : roomAreas)
		{
			roomPredicates.add(roomArea.getCoordinateSpace()
				== SlayerRouteEvidence.CoordinateSpace.WORLD
				? SlayerRoutePredicate.worldArea(roomArea)
				: SlayerRoutePredicate.templateArea(roomArea));
		}
		final SlayerRoutePredicate rooms =
			SlayerRoutePredicate.anyOf(roomPredicates);
		return SlayerRoutePredicate.anyOf(
			rooms,
			SlayerRoutePredicate.allOf(exactNpc(npcNames), rooms));
	}

	private static SlayerRoutePredicate atWorld(
		final WorldPoint point,
		final int radius)
	{
		return SlayerRoutePredicate.worldArea(aroundWorld(point, radius));
	}

	private static SlayerRoutePredicate.ObjectActionSpec identified(
		final int objectId,
		final String... actions)
	{
		return SlayerRoutePredicate.ObjectActionSpec.identified(
			objectId, actions);
	}

	private static SlayerRoutePredicate.ObjectActionSpec reviewed(
		final Collection<Integer> objectIds,
		final String... actions)
	{
		return SlayerRoutePredicate.ObjectActionSpec.reviewed(
			objectIds, Collections.emptySet(), Arrays.asList(actions));
	}

	private static List<Integer> ids(final int... objectIds)
	{
		final List<Integer> result = new ArrayList<>();
		for (final int objectId : objectIds)
		{
			result.add(objectId);
		}
		return result;
	}

	private static SlayerRoutePlan.ActivityRetention retention(
		final SlayerRoutePredicate.SpatialArea... areas)
	{
		return SlayerRoutePlan.ActivityRetention.of(areas);
	}

	private static SlayerRoutePredicate.SpatialArea aroundWorld(
		final WorldPoint point,
		final int radius)
	{
		return SlayerRoutePredicate.SpatialArea.aroundWorld(
			point, radius, radius);
	}

	private static SlayerRoutePredicate.SpatialArea templateCopyOf(
		final SlayerRoutePredicate.SpatialArea worldArea)
	{
		return SlayerRoutePredicate.SpatialArea.template(
			worldArea.getMinX(), worldArea.getMaxX(),
			worldArea.getMinY(), worldArea.getMaxY(), worldArea.getPlane());
	}

	private static SlayerRoutePredicate.SpatialArea regionWorld(
		final int regionId,
		final int plane)
	{
		return regionArea(
			regionId, regionId, plane, SlayerRouteEvidence.CoordinateSpace.WORLD);
	}

	private static SlayerRoutePredicate.SpatialArea regionTemplate(
		final int regionId,
		final int plane)
	{
		return regionArea(
			regionId, regionId, plane, SlayerRouteEvidence.CoordinateSpace.TEMPLATE);
	}

	private static SlayerRoutePredicate.SpatialArea regionPairWorld(
		final int firstRegion,
		final int secondRegion,
		final int plane)
	{
		return regionArea(
			firstRegion, secondRegion, plane,
			SlayerRouteEvidence.CoordinateSpace.WORLD);
	}

	private static SlayerRoutePredicate.SpatialArea regionPairTemplate(
		final int firstRegion,
		final int secondRegion,
		final int plane)
	{
		return regionArea(
			firstRegion, secondRegion, plane,
			SlayerRouteEvidence.CoordinateSpace.TEMPLATE);
	}

	private static SlayerRoutePredicate.SpatialArea regionArea(
		final int firstRegion,
		final int secondRegion,
		final int plane,
		final SlayerRouteEvidence.CoordinateSpace space)
	{
		final int firstX = firstRegion >>> 8;
		final int firstY = firstRegion & 0xff;
		final int secondX = secondRegion >>> 8;
		final int secondY = secondRegion & 0xff;
		final int minX = Math.min(firstX, secondX) * 64;
		final int maxX = (Math.max(firstX, secondX) + 1) * 64 - 1;
		final int minY = Math.min(firstY, secondY) * 64;
		final int maxY = (Math.max(firstY, secondY) + 1) * 64 - 1;
		return space == SlayerRouteEvidence.CoordinateSpace.WORLD
			? SlayerRoutePredicate.SpatialArea.world(
				minX, maxX, minY, maxY, plane)
			: SlayerRoutePredicate.SpatialArea.template(
				minX, maxX, minY, maxY, plane);
	}

	private static WorldPoint point(final int x, final int y, final int plane)
	{
		return new WorldPoint(x, y, plane);
	}

	private static void register(
		final Map<RouteKey, SlayerRoutePlan> plans,
		final String taskName,
		final String location,
		final SlayerRoutePlan plan)
	{
		register(plans, taskName, location, true, plan);
	}

	private static void registerAssignment(
		final Map<RouteKey, SlayerRoutePlan> plans,
		final String taskName,
		final String location,
		final SlayerRoutePlan plan)
	{
		register(plans, taskName, location, false, plan);
	}

	private static void register(
		final Map<RouteKey, SlayerRoutePlan> plans,
		final String taskName,
		final String location,
		final boolean boss,
		final SlayerRoutePlan plan)
	{
		final RouteKey key = RouteKey.of(taskName, location, boss);
		if (plans.putIfAbsent(key, Objects.requireNonNull(plan, "plan")) != null)
		{
			throw new IllegalStateException("Duplicate explicit route key: " + key);
		}
	}

	/** Immutable normalized exact key exposed for release-regression checks. */
	public static final class RouteKey
	{
		private final String taskName;
		private final String location;
		private final boolean boss;

		private RouteKey(
			final String taskName,
			final String location,
			final boolean boss)
		{
			this.taskName = SlayerRouteEvidence.normalize(taskName);
			this.location = SlayerRouteEvidence.normalize(location);
			this.boss = boss;
		}

		public static RouteKey of(
			final String taskName,
			final String location,
			final boolean boss)
		{
			return new RouteKey(taskName, location, boss);
		}

		public String getTaskName()
		{
			return taskName;
		}

		public String getLocation()
		{
			return location;
		}

		public boolean isBoss()
		{
			return boss;
		}

		@Override
		public boolean equals(final Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof RouteKey))
			{
				return false;
			}
			final RouteKey that = (RouteKey) other;
			return boss == that.boss
				&& taskName.equals(that.taskName)
				&& location.equals(that.location);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(taskName, location, boss);
		}

		@Override
		public String toString()
		{
			return taskName + " | " + location + " | boss=" + boss;
		}
	}
}
