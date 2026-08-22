package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Independent release checklist for Slayer routing.
 *
 * <p>This is intentionally static data. It must not be populated from the
 * recommendation, strategy, travel, or route catalogs: its purpose is to give
 * release validation an independent statement of the encounters and terminal
 * contracts which those catalogs are expected to implement.</p>
 */
final class SlayerRouteReleaseManifest
{
	private static final Set<String> CANONICAL_ASSIGNMENT_NAMES = setOf(
		"Aberrant spectres", "Abyssal demons", "Ankou", "Aquanites", "Araxytes",
		"Aviansies", "Bandits", "Banshees", "Basilisks", "Bats", "Bears", "Birds",
		"Black demons", "Black dragons", "Black Knights", "Bloodveld", "Blue dragons",
		"Brine rats", "Catablepon", "Cave bugs", "Cave crawlers", "Cave horrors",
		"Cave kraken", "Cave slimes", "Chaos druids", "Cockatrice", "Cows", "Crabs",
		"Crawling hands", "Crocodiles", "Custodian Stalkers", "Dagannoth", "Dark beasts",
		"Dark warriors", "Dogs", "Drakes", "Dust devils", "Dwarves", "Earth warriors",
		"Elves", "Ents", "Fever spiders", "Fire giants", "Fleshcrawlers",
		"Fossil Island wyverns", "Frost dragons", "Gargoyles", "Ghosts", "Ghouls",
		"Goblins", "Greater demons", "Green dragons", "Gryphons", "Harpie bug swarms",
		"Hellhounds", "Hill giants", "Hobgoblins", "Hydras", "Ice giants", "Ice warriors",
		"Icefiends", "Infernal mages", "Jellies", "Jungle horrors", "Kalphites",
		"Killerwatts", "Kurask", "Lava dragons", "Lesser demons", "Lesser nagua",
		"Lizardmen", "Lizards", "Magic axes", "Mammoths", "Metal dragons", "Minotaurs",
		"Mogres", "Molanisks", "Monkeys", "Moss giants", "Mutated zygomites",
		"Nechryael", "Ogres", "Otherworldly beings", "Pirates", "Pyrefiends", "Rats",
		"Red dragons", "Revenants", "Rockslugs", "Rogues", "Scabarites", "Scorpions",
		"Sea snakes", "Shades", "Shadow warriors", "Skeletal wyverns", "Skeletons",
		"Smoke devils", "Sourhogs", "Spiders", "Spiritual creatures", "Suqahs",
		"Terror dogs", "Trolls", "Turoth", "TzHaar", "Vampyres", "Venators",
		"Wall beasts", "Warped creatures", "Waterfiends", "Werewolves", "Wolves",
		"Wyrms", "Zombies"
	);
	private static final Set<String> CANONICAL_SELECTABLE_BOSS_NAMES = setOf(
		"Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Artio", "Callisto",
		"Cerberus", "Dagannoth Kings", "Deranged Archaeologist", "Grotesque Guardians",
		"K'ril Tsutsaroth", "Kalphite Queen", "King Black Dragon", "Kraken", "Kree'arra",
		"Sarachnis", "Scorpia", "Skotizo", "Thermonuclear smoke devil", "TzTok-Jad",
		"TzKal-Zuk", "Spindel", "Venenatis", "Calvar'ion", "Vet'ion", "Vorkath",
		"Scurrius", "Obor", "Bryophyta", "Brutus", "Demonic gorillas",
		"Tormented demons", "Royal Titans", "Shellbane Gryphon"
	);
	private static final Set<String> CANONICAL_DIRECT_BOSS_NAMES = setOf(
		"Barrows Brothers", "Chaos Elemental", "Chaos Fanatic", "Crazy Archaeologist",
		"Crazy Archaeologists", "Deranged Archaeologist", "Duke Sucellus",
		"General Graardor", "Giant Mole", "Maggot King", "Phantom Muspah", "Leviathan",
		"The Whisperer", "Vardorvis", "Commander Zilyana", "Zulrah"
	);
	private static final List<Entry> ENTRIES = createEntries();

	private SlayerRouteReleaseManifest()
	{
	}

	enum EntryKind
	{
		ASSIGNMENT,
		SELECTABLE_BOSS,
		DIRECT_BOSS
	}

	enum AreaType
	{
		OPEN_WORLD,
		CAVE,
		DUNGEON,
		MULTI_FLOOR,
		INSTANCE,
		WAVE,
		SPECIAL
	}

	/** The exact terminal behavior an authored route must provide. */
	enum TerminalContract
	{
		OPEN_WORLD_EXACT_TARGET,
		FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
		MANUAL_STAGE_EXACT_CHECKPOINT,
		INSTANCE_ENTRANCE_OR_WIDGET_STOP,
		INSTANCE_REGION_STOP,
		WAVE_ENTRANCE_OR_WIDGET_STOP
	}

	enum TerminalMechanism
	{
		STATIC_WORLD_POINT,
		EXACT_NPC_DISCOVERY,
		OBJECT_OR_WIDGET_STOP,
		REGION_ARRIVAL_STOP,
		SPECIAL_HANDOFF
	}

	static final class Endpoint
	{
		private final int x;
		private final int y;
		private final int plane;

		private Endpoint(final int x, final int y, final int plane)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
		}

		int getX()
		{
			return x;
		}

		int getY()
		{
			return y;
		}

		int getPlane()
		{
			return plane;
		}
	}

	static final class Entry
	{
		private final EntryKind kind;
		private final String assignmentName;
		private final String encounterName;
		private final String location;
		private final AreaType areaType;
		private final TerminalContract terminalContract;
		private final TerminalMechanism terminalMechanism;
		private final Endpoint expectedAccess;
		private final Endpoint expectedTerminal;
		private final String unresolvedReason;
		private final String wikiPageTitle;
		private final List<String> eligibleAssignments;

		private Entry(
			final EntryKind kind,
			final String assignmentName,
			final String encounterName,
			final String location,
			final AreaType areaType,
			final TerminalContract terminalContract,
			final TerminalMechanism terminalMechanism,
			final Endpoint expectedAccess,
			final Endpoint expectedTerminal,
			final String wikiPageTitle,
			final String... eligibleAssignments)
		{
			this.kind = kind;
			this.assignmentName = assignmentName;
			this.encounterName = encounterName;
			this.location = location;
			this.areaType = areaType;
			this.terminalContract = terminalContract;
			this.terminalMechanism = terminalMechanism;
			this.expectedAccess = expectedAccess;
			this.expectedTerminal = expectedTerminal;
			this.unresolvedReason = unresolvedReasonFor(
				location, terminalContract, expectedTerminal
			);
			this.wikiPageTitle = wikiPageTitle;
			this.eligibleAssignments = Collections.unmodifiableList(
				new ArrayList<>(Arrays.asList(eligibleAssignments))
			);
		}

		EntryKind getKind()
		{
			return kind;
		}

		String getAssignmentName()
		{
			return assignmentName;
		}

		String getEncounterName()
		{
			return encounterName;
		}

		String getLocation()
		{
			return location;
		}

		AreaType getAreaType()
		{
			return areaType;
		}

		TerminalContract getTerminalContract()
		{
			return terminalContract;
		}

		TerminalMechanism getTerminalMechanism()
		{
			return terminalMechanism;
		}

		Endpoint getExpectedAccess()
		{
			return expectedAccess;
		}

		Endpoint getExpectedTerminal()
		{
			return expectedTerminal;
		}

		boolean hasEstablishedTerminal()
		{
			return expectedTerminal != null;
		}

		String getUnresolvedReason()
		{
			return unresolvedReason;
		}

		boolean isReleaseReady()
		{
			return expectedTerminal != null && unresolvedReason.isEmpty();
		}

		String getWikiPageTitle()
		{
			return wikiPageTitle;
		}

		List<String> getEligibleAssignments()
		{
			return eligibleAssignments;
		}

		boolean requiresExactAuthoredProfile()
		{
			return true;
		}
	}

	static List<Entry> entries()
	{
		return ENTRIES;
	}

	static List<Entry> entriesByKind(final EntryKind kind)
	{
		final List<Entry> matches = new ArrayList<>();
		for (final Entry entry : ENTRIES)
		{
			if (entry.kind == kind)
			{
				matches.add(entry);
			}
		}
		return Collections.unmodifiableList(matches);
	}

	static List<Entry> unresolvedTerminalEntries()
	{
		final List<Entry> unresolved = new ArrayList<>();
		for (final Entry entry : ENTRIES)
		{
			if (!entry.isReleaseReady())
			{
				unresolved.add(entry);
			}
		}
		return Collections.unmodifiableList(unresolved);
	}

	static Set<String> canonicalAssignmentNames()
	{
		return CANONICAL_ASSIGNMENT_NAMES;
	}

	static Set<String> canonicalSelectableBossNames()
	{
		return CANONICAL_SELECTABLE_BOSS_NAMES;
	}

	static Set<String> canonicalDirectBossNames()
	{
		return CANONICAL_DIRECT_BOSS_NAMES;
	}

	static int size()
	{
		return ENTRIES.size();
	}

	private static List<Entry> createEntries()
	{
		final List<Entry> entries = new ArrayList<>();

		/* Ordinary assignments. Multiple rows are deliberate location choices. */
		assignment(entries, "Aberrant spectres", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1613, 10010, 0));
		assignment(entries, "Abyssal demons", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1670, 10091, 0));
		assignment(entries, "Ankou", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1637, 9991, 0));
		assignment(entries, "Black demons", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1722, 10087, 0));
		assignment(entries, "Bloodveld", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1681, 10073, 0));
		assignment(entries, "Dust devils", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1715, 10025, 0));
		assignment(entries, "Fire giants", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1634, 10068, 0));
		assignment(entries, "Ghosts", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1689, 10063, 0));
		assignment(entries, "Greater demons", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1701, 10102, 0));
		assignment(entries, "Hellhounds", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1650, 10065, 0));
		assignment(entries, "Nechryael", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1700, 10083, 0));
		assignment(entries, "Skeletons", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1642, 9996, 0));
		assignment(entries, "Zombies", "Catacombs of Kourend", AreaType.DUNGEON,
			point(1639, 3673, 0), point(1642, 10075, 0));
		assignment(entries, "Aberrant spectres", "Slayer Tower", AreaType.MULTI_FLOOR,
			point(3428, 3536, 0), point(3422, 3549, 1));
		assignment(entries, "Infernal mages", "Slayer Tower", AreaType.MULTI_FLOOR,
			point(3428, 3536, 0), point(3438, 3559, 1));
		assignment(entries, "Abyssal demons", "Slayer Tower", AreaType.MULTI_FLOOR,
			point(3428, 3536, 0), point(3447, 9970, 3));
		assignment(entries, "Banshees", "Slayer Tower", AreaType.MULTI_FLOOR,
			point(3428, 3536, 0), point(3441, 3540, 0));
		assignment(entries, "Crawling hands", "Slayer Tower", AreaType.MULTI_FLOOR,
			point(3428, 3536, 0), point(3414, 3536, 0));
		assignment(entries, "Gargoyles", "Slayer Tower", AreaType.MULTI_FLOOR,
			point(3428, 3536, 0), point(3452, 9935, 3));
		assignments(entries, "Slayer Tower (first floor)", AreaType.MULTI_FLOOR,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3428, 3536, 0), point(3417, 3567, 1), "Bloodveld");
		assignments(entries, "Slayer Tower basement", AreaType.MULTI_FLOOR,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3428, 3536, 0), point(3403, 9947, 3), "Bloodveld");
		assignment(entries, "Aberrant spectres", "Stronghold Slayer Cave", AreaType.CAVE,
			point(2432, 3423, 0), point(2457, 9788, 0));
		assignment(entries, "Ankou", "Stronghold Slayer Cave", AreaType.CAVE,
			point(2432, 3423, 0), point(2479, 9806, 0));
		assignment(entries, "Bloodveld", "Stronghold Slayer Cave", AreaType.CAVE,
			point(2432, 3423, 0), point(2465, 9833, 0));
		assignment(entries, "Hellhounds", "Stronghold Slayer Cave", AreaType.CAVE,
			point(2432, 3423, 0), point(2424, 9793, 0));
		assignments(entries, "Stronghold of Security", AreaType.MULTI_FLOOR,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3081, 3421, 0), null, "Ankou", "Catablepon", "Fleshcrawlers");
		assignment(entries, "Minotaurs", "Stronghold of Security", AreaType.MULTI_FLOOR,
			point(3081, 3421, 0), point(1880, 5205, 0));
		assignment(entries, "Cockatrice", "Fremennik Slayer Dungeon", AreaType.DUNGEON,
			point(2796, 3615, 0), point(2789, 10031, 0));
		assignment(entries, "Jellies", "Fremennik Slayer Dungeon", AreaType.DUNGEON,
			point(2796, 3615, 0), point(2705, 10019, 0));
		assignment(entries, "Pyrefiends", "Fremennik Slayer Dungeon", AreaType.DUNGEON,
			point(2796, 3615, 0), point(2764, 10003, 0));
		assignment(entries, "Rockslugs", "Fremennik Slayer Dungeon", AreaType.DUNGEON,
			point(2796, 3615, 0), point(2794, 10017, 0));
		assignment(entries, "Basilisks", "Fremennik Slayer Dungeon", AreaType.DUNGEON,
			point(2796, 3615, 0), point(2744, 10004, 0));
		assignment(entries, "Cave crawlers", "Fremennik Slayer Dungeon", AreaType.DUNGEON,
			point(2796, 3615, 0), point(2792, 9996, 0));
		assignment(entries, "Kurask", "Fremennik Slayer Dungeon", AreaType.DUNGEON,
			point(2796, 3615, 0), point(2701, 10001, 0));
		assignment(entries, "Turoth", "Fremennik Slayer Dungeon", AreaType.DUNGEON,
			point(2796, 3615, 0), point(2716, 10011, 0));
		assignment(entries, "Basilisks", "Jormungand's Prison", AreaType.DUNGEON,
			point(2445, 3747, 0), point(2455, 10388, 0));
		assignment(entries, "Dagannoth", "Jormungand's Prison", AreaType.DUNGEON,
			point(2445, 3747, 0), point(2420, 10422, 0));
		assignment(entries, "Bloodveld", "Iorwerth Dungeon", AreaType.DUNGEON,
			point(3225, 6045, 0), point(3236, 12438, 0));
		assignment(entries, "Dark beasts", "Iorwerth Dungeon", AreaType.DUNGEON,
			point(3225, 6045, 0), point(3227, 12397, 0));
		assignment(entries, "Elves", "Iorwerth Dungeon", AreaType.DUNGEON,
			point(3225, 6045, 0), point(3184, 12405, 0));
		assignment(entries, "Kurask", "Iorwerth Dungeon", AreaType.DUNGEON,
			point(3225, 6045, 0), point(3219, 12369, 0));
		assignment(entries, "Nechryael", "Iorwerth Dungeon", AreaType.DUNGEON,
			point(3225, 6045, 0), point(3228, 12459, 0));
		assignment(entries, "Waterfiends", "Iorwerth Dungeon", AreaType.DUNGEON,
			point(3225, 6045, 0), point(3183, 12456, 0));
		assignment(entries, "Drakes", "Karuulm Slayer Dungeon", AreaType.MULTI_FLOOR,
			point(1311, 3783, 0), point(1312, 10242, 1));
		assignment(entries, "Hydras", "Karuulm Slayer Dungeon", AreaType.MULTI_FLOOR,
			point(1311, 3783, 0), point(1311, 10241, 0));
		assignment(entries, "Wyrms", "Karuulm Slayer Dungeon", AreaType.MULTI_FLOOR,
			point(1311, 3783, 0), point(1273, 10189, 0));
		assignments(entries, "Chasm of Fire", AreaType.MULTI_FLOOR,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(1435, 3670, 0), point(1280, 10208, 1), "Greater demons");
		assignment(entries, "Black dragons", "Taverley Dungeon", AreaType.MULTI_FLOOR,
			point(2885, 3397, 0), point(2903, 9813, 1));
		assignment(entries, "Blue dragons", "Taverley Dungeon", AreaType.MULTI_FLOOR,
			point(2885, 3397, 0), point(2892, 9799, 0));
		assignment(entries, "Chaos druids", "Edgeville Dungeon", AreaType.DUNGEON,
			point(3096, 3468, 0), point(3110, 9933, 0));
		assignment(entries, "Earth warriors", "Edgeville Dungeon", AreaType.DUNGEON,
			point(3096, 3468, 0), point(3120, 9970, 0));
		assignment(entries, "Hill giants", "Edgeville Dungeon", AreaType.DUNGEON,
			point(3096, 3468, 0), point(3105, 9839, 0));
		assignment(entries, "Hobgoblins", "Edgeville Dungeon", AreaType.DUNGEON,
			point(3096, 3468, 0), point(3127, 9873, 0));
		assignment(entries, "Moss giants", "Brimhaven Dungeon", AreaType.DUNGEON,
			point(2745, 3152, 0), point(2647, 9497, 0));
		assignment(entries, "Red dragons", "Brimhaven Dungeon", AreaType.DUNGEON,
			point(2745, 3152, 0), point(2699, 9509, 0));
		assignments(entries, "Ancient Cavern", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2512, 3511, 0), null, "Metal dragons");
		assignment(entries, "Waterfiends", "Ancient Cavern", AreaType.DUNGEON,
			point(2512, 3511, 0), point(1738, 5356, 0));
		assignments(entries, "Asgarnian Ice Dungeon", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3009, 3150, 0), point(3055, 9568, 0), "Ice giants");
		assignment(entries, "Skeletal wyverns", "Asgarnian Ice Dungeon", AreaType.DUNGEON,
			point(3009, 3150, 0), point(3057, 9547, 0));
		assignments(entries, "God Wars Dungeon", AreaType.MULTI_FLOOR,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2916, 3746, 0), null, "Aviansies", "Spiritual creatures");
		assignment(entries, "Bloodveld", "God Wars Dungeon", AreaType.MULTI_FLOOR,
			point(2916, 3746, 0), point(2888, 5324, 2));
		assignment(entries, "Cave bugs", "Dorgesh-Kaan South Dungeon", AreaType.DUNGEON,
			point(3169, 3173, 0), point(2735, 5221, 0));
		assignment(entries, "Cave slimes", "Dorgesh-Kaan South Dungeon", AreaType.DUNGEON,
			point(3169, 3173, 0), point(2735, 5221, 0));
		assignment(entries, "Molanisks", "Dorgesh-Kaan South Dungeon", AreaType.DUNGEON,
			point(3169, 3173, 0), point(2698, 5222, 0));
		assignments(entries, "Lighthouse Dungeon", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2518, 3630, 0), point(2523, 10023, 0), "Dagannoth");
		assignments(entries, "Waterbirth Island Dungeon", AreaType.MULTI_FLOOR,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2443, 3746, 0), null, "Dagannoth");
		assignments(entries, "Kalphite Slayer Cave", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3321, 3122, 0), point(3305, 9497, 0), "Kalphites");
		assignments(entries, "Kraken Cove", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2277, 3611, 0), null, "Cave kraken");
		assignments(entries, "Morytania Spider Cave", AreaType.CAVE,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT,
			point(3657, 3407, 0), null, "Araxytes");
		assignments(entries, "Mos Le'Harmless Cave", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3748, 2974, 0), point(3775, 9407, 0), "Cave horrors");
		assignments(entries, "Smoke Devil Dungeon", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2411, 3058, 0), point(2408, 9438, 0), "Smoke devils");
		assignments(entries, "Wyvern Cave on Fossil Island", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3680, 3854, 0), point(3616, 10272, 0), "Fossil Island wyverns");
		assignments(entries, "Meiyerditch Laboratories", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3638, 3304, 0), point(3594, 9743, 0), "Bloodveld");
		assignments(entries, "Buccaneers' Laboratory", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2075, 3688, 0), point(2096, 10099, 0), "Bloodveld");
		assignments(entries, "Mourner Tunnels", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2028, 4636, 0), point(1992, 4655, 0), "Dark beasts");
		assignment(entries, "Brine rats", "Brine Rat Cavern", AreaType.CAVE,
			point(2730, 3714, 0), point(2772, 10155, 0));
		assignments(entries, "Braindeath Island", AreaType.SPECIAL,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3680, 3536, 0), point(2150, 5099, 0), "Fever spiders");
		assignments(entries, "Grimstone Dungeon", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2912, 4066, 0), point(2901, 10463, 0), "Frost dragons");
		assignments(entries, "Sophanem Dungeon", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3315, 2796, 0), null, "Scabarites");
		assignments(entries, "Legends' Guild basement", AreaType.DUNGEON,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT,
			point(2728, 3348, 0), null, "Shadow warriors");
		assignments(entries, "Lumbridge Swamp Caves", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3169, 3173, 0), point(3200, 9560, 0), "Wall beasts");
		assignments(entries, "Poison Waste Dungeon", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(2322, 3099, 0), null, "Warped creatures");
		assignments(entries, "Ruins of Tapoyauik", AreaType.MULTI_FLOOR,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(1693, 3230, 0), null, "Lesser nagua");
		assignments(entries, "Ynysdail Cavern", AreaType.SPECIAL,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT,
			point(2218, 3424, 0), null, "Aquanites");
		assignments(entries, "Killerwatt plane", AreaType.SPECIAL,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT,
			point(3108, 3362, 0), null, "Killerwatts");
		assignments(entries, "Zanaris", AreaType.SPECIAL,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3202, 3169, 0), null, "Otherworldly beings", "Mutated zygomites");

		assignments(entries, "Lumbridge area", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3188, 3220, 0), point(3188, 3220, 0),
			"Bats", "Birds", "Cows", "Crabs", "Goblins", "Rats", "Spiders");
		assignments(entries, "Ardougne area", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2570, 3300, 0), point(2570, 3300, 0), "Bears");
		assignments(entries, "Bandit Camp", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3171, 2979, 0), point(3171, 2979, 0), "Bandits");
		assignments(entries, "Black Knights' Fortress", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3015, 3514, 0), point(3015, 3514, 0), "Black Knights");
		assignments(entries, "Darkmeyer", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3604, 3364, 0), point(3604, 3364, 0), "Vampyres", "Venators");
		assignments(entries, "Death Plateau", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2847, 3635, 0), point(2847, 3635, 0), "Trolls");
		assignments(entries, "Desert quarry", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3172, 2912, 0), point(3172, 2912, 0), "Lizards");
		assignments(entries, "Dwarven Mine", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3018, 3450, 0), point(3020, 9820, 0), "Dwarves");
		assignments(entries, "Feldip Hills", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2515, 2954, 0), point(2515, 2954, 0), "Jungle horrors", "Wolves");
		assignments(entries, "Haunted Woods", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3584, 3492, 0), point(3584, 3492, 0), "Ghouls", "Werewolves");
		assignments(entries, "Ice Mountain", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3010, 3486, 0), point(3010, 3486, 0), "Icefiends", "Ice warriors");
		assignments(entries, "Isle of Souls", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2210, 2900, 0), point(2210, 2900, 0), "Dogs", "Sourhogs");
		assignments(entries, "Karamja", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2843, 3070, 0), point(2843, 3070, 0),
			"Harpie bug swarms", "Lesser demons", "Monkeys");
		assignment(entries, "TzHaar", "Karamja Volcano", AreaType.CAVE,
			point(2855, 3168, 0), point(2444, 5138, 0));
		assignments(entries, "Kebos Lowlands", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(1248, 3726, 0), point(1248, 3726, 0), "Gryphons");
		assignments(entries, "Kourend Woodland", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(1540, 3464, 0), point(1540, 3464, 0), "Lizardmen", "Ogres");
		assignments(entries, "Lletya", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2355, 3172, 0), point(2355, 3172, 0), "Elves");
		assignments(entries, "Lunar Isle", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2112, 3915, 0), point(2112, 3915, 0), "Suqahs");
		assignments(entries, "Mogre beach", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2996, 3106, 0), point(2996, 3106, 0), "Mogres");
		assignments(entries, "Mort'ton", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3488, 3288, 0), point(3488, 3288, 0), "Shades");
		assignments(entries, "Nardah desert", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3427, 2911, 0), point(3427, 2911, 0), "Crocodiles");
		assignments(entries, "Piscatoris area", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2332, 3683, 0), point(2332, 3683, 0), "Terror dogs");
		assignments(entries, "Stalker Den", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(1325, 3364, 0), null, "Custodian Stalkers");
		assignments(entries, "Waterbirth Island", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(2525, 3743, 0), point(2525, 3743, 0), "Sea snakes");
		/*
		 * Exact Wilderness targets use current OSRS Wiki NPC LocLine coordinates.
		 * Each was independently reached by the pinned Shortest Path graph from
		 * Ferox Enclave; Lava Dragon Isle was also reached with agility shortcuts
		 * disabled. The Magic axe hut and Revenant Caves use separately authored
		 * object-gated plans below rather than pretending they are open-world packs.
		 */
		assignments(entries, "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3029, 3632, 0), point(3029, 3632, 0), "Dark warriors");
		assignments(entries, "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3202, 3669, 0), point(3202, 3669, 0), "Ents");
		assignments(entries, "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3201, 3814, 0), point(3201, 3814, 0), "Lava dragons");
		assignments(entries, "Wilderness", AreaType.SPECIAL,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT,
			point(3190, 3957, 0), null, "Magic axes");
		assignments(entries, "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3169, 3594, 0), point(3169, 3594, 0), "Mammoths");
		assignments(entries, "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3240, 3601, 0), point(3240, 3601, 0), "Pirates");
		assignments(entries, "Wilderness", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			point(3073, 3654, 0), point(3197, 10070, 0), "Revenants");
		assignments(entries, "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3285, 3933, 0), point(3285, 3933, 0), "Rogues");
		assignments(entries, "Wilderness green dragon area", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3331, 3672, 0), point(3331, 3672, 0), "Green dragons");
		assignments(entries, "Al Kharid mine", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET,
			point(3298, 3293, 0), point(3298, 3293, 0), "Scorpions");

		/* Selectable boss and demi-boss variants: one independent row per variant. */
		selectable(entries, "Abyssal Sire", "Abyssal Nexus", AreaType.SPECIAL,
			TerminalContract.INSTANCE_REGION_STOP, point(3107, 4791, 0), point(3037, 4763, 0),
			"Abyssal Sire", "Abyssal demons");
		selectable(entries, "Alchemical Hydra", "Karuulm Slayer Dungeon", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(1311, 3783, 0), point(1368, 10270, 0),
			"Alchemical Hydra", "Hydras");
		selectable(entries, "Amoxliatl", "Ruins of Tapoyauik", AreaType.INSTANCE,
			TerminalContract.INSTANCE_REGION_STOP, point(1641, 3221, 0), point(1362, 4511, 0),
			"Amoxliatl", "Lesser Nagua");
		selectable(entries, "Araxxor", "Morytania Spider Cave", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3657, 3407, 0), point(3632, 9815, 0),
			"Araxxor", "Araxytes", "Spiders");
		selectable(entries, "Artio", "Hunter's End", AreaType.CAVE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3112, 3670, 0), null,
			"Artio", "Bears");
		selectable(entries, "Callisto", "Callisto's Den", AreaType.CAVE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3290, 3855, 0), null,
			"Callisto", "Bears");
		selectable(entries, "Cerberus", "Cerberus' Lair", AreaType.DUNGEON,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR, point(2885, 3397, 0), point(1310, 1253, 0),
			"Cerberus", "Hellhounds");
		selectable(entries, "Dagannoth Kings", "Waterbirth Island Dungeon", AreaType.MULTI_FLOOR,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR, point(2443, 3746, 0), null,
			"Dagannoth Kings", "Dagannoth", "Dagannoths");
		selectable(entries, "Deranged Archaeologist", "Fossil Island", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET, point(3683, 3706, 0), point(3683, 3706, 0),
			"Deranged archaeologist", "Crazy Archaeologist", "Crazy Archaeologists");
		selectable(entries, "Grotesque Guardians", "Slayer Tower", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3428, 3536, 0), null,
			"Grotesque Guardians", "Gargoyles");
		selectable(entries, "K'ril Tsutsaroth", "God Wars Dungeon", AreaType.MULTI_FLOOR,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(2916, 3746, 0), null,
			"K'ril Tsutsaroth", "Greater demons");
		selectable(entries, "Kalphite Queen", "Kalphite Lair", AreaType.MULTI_FLOOR,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR, point(3228, 3109, 0), point(3508, 9493, 0),
			"Kalphite Queen", "Kalphites");
		selectable(entries, "King Black Dragon", "King Black Dragon Lair", AreaType.DUNGEON,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(3017, 3850, 0), null,
			"King Black Dragon", "Black dragons");
		selectable(entries, "Kraken", "Kraken Cove", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(2280, 3616, 0), point(2280, 10016, 0),
			"Kraken", "Cave kraken");
		selectable(entries, "Kree'arra", "God Wars Dungeon", AreaType.MULTI_FLOOR,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(2916, 3746, 0), null,
			"Kree'arra", "Aviansies");
		selectable(entries, "Sarachnis", "Forthos Dungeon", AreaType.DUNGEON,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(1667, 3570, 0), null,
			"Sarachnis", "Spiders");
		selectable(entries, "Scorpia", "Scorpia's Cave", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR, point(3232, 3950, 0), point(3232, 10346, 0),
			"Scorpia", "Scorpions");
		selectable(entries, "Skotizo", "Skotizo's Lair", AreaType.INSTANCE,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(1639, 3673, 0), null,
			"Skotizo", "Black demons", "Greater demons");
		selectable(entries, "Thermonuclear smoke devil", "Smoke Devil Dungeon", AreaType.DUNGEON,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(2411, 3058, 0), null,
			"Thermonuclear smoke devil", "Smoke devils");
		selectable(entries, "TzTok-Jad", "TzHaar Fight Cave", AreaType.WAVE,
			TerminalContract.WAVE_ENTRANCE_OR_WIDGET_STOP, point(2439, 5172, 0), point(2439, 5172, 0),
			"TzTok-Jad", "TzHaar");
		selectable(entries, "TzKal-Zuk", "Inferno", AreaType.WAVE,
			TerminalContract.WAVE_ENTRANCE_OR_WIDGET_STOP, point(2496, 5115, 0), point(2496, 5115, 0),
			"TzKal-Zuk", "TzHaar");
		selectable(entries, "Spindel", "Web Chasm", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR, point(3294, 3749, 0), point(3406, 10145, 0),
			"Spindel", "Spiders");
		selectable(entries, "Venenatis", "Silk Chasm", AreaType.CAVE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3321, 3794, 0), null,
			"Venenatis", "Spiders");
		selectable(entries, "Calvar'ion", "Skeletal Tomb", AreaType.CAVE,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR, point(3152, 3644, 0), point(3164, 10043, 0),
			"Calvar'ion", "Skeletons");
		selectable(entries, "Vet'ion", "Vet'ion's Rest", AreaType.CAVE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3219, 3788, 0), null,
			"Vet'ion", "Skeletons");
		selectable(entries, "Vorkath", "Ungael", AreaType.INSTANCE,
			TerminalContract.INSTANCE_REGION_STOP, point(2640, 3696, 0), point(2273, 4065, 0),
			"Vorkath", "Blue dragons", "Zombies");
		selectable(entries, "Scurrius", "Varrock Sewers", AreaType.INSTANCE,
			TerminalContract.INSTANCE_REGION_STOP, point(3237, 3458, 0), point(3299, 9867, 0),
			"Scurrius", "Rats");
		selectable(entries, "Obor", "Edgeville Dungeon", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3096, 3468, 0), null,
			"Obor", "Hill giants");
		selectable(entries, "Bryophyta", "Varrock Sewers", AreaType.INSTANCE,
			TerminalContract.INSTANCE_REGION_STOP, point(3237, 3458, 0), point(3220, 9933, 0),
			"Bryophyta", "Moss giants");
		selectable(entries, "Brutus", "Lumbridge cow field", AreaType.INSTANCE,
			TerminalContract.INSTANCE_REGION_STOP, point(3253, 3266, 0), point(3263, 3297, 0),
			"Brutus", "Cows");
		selectable(entries, "Demonic gorillas", "Crash Site Cavern", AreaType.CAVE,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(2464, 3494, 0), null,
			"Demonic gorilla", "Black demons", "Monkeys");
		selectable(entries, "Tormented demons", "Ancient Guthixian Temple", AreaType.SPECIAL,
			TerminalContract.INSTANCE_REGION_STOP, point(4097, 4419, 0), point(4097, 4419, 0),
			"Tormented demon", "Greater demons");
		selectable(entries, "Royal Titans", "Asgarnian Ice Dungeon", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3009, 3150, 0), point(2953, 9569, 0),
			"Royal Titans", "Fire giants", "Ice giants");
		selectable(entries, "Shellbane Gryphon", "The Great Conch", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3176, 2477, 0), null,
			"Shellbane Gryphon", "Gryphons");

		/* Direct boss assignments. */
		direct(entries, "Barrows Brothers", "Barrows", AreaType.SPECIAL,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(3567, 3291, 0), null, "Barrows");
		direct(entries, "Chaos Elemental", "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET, point(3261, 3927, 0), point(3261, 3927, 0), "Chaos Elemental");
		direct(entries, "Chaos Fanatic", "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET, point(2979, 3846, 0), point(2979, 3846, 0), "Chaos Fanatic");
		direct(entries, "Crazy Archaeologist", "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET, point(2977, 3702, 0), point(2977, 3702, 0), "Crazy archaeologist");
		direct(entries, "Crazy Archaeologists", "Wilderness", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET, point(2977, 3702, 0), point(2977, 3702, 0), "Crazy archaeologist");
		direct(entries, "Deranged Archaeologist", "Fossil Island", AreaType.OPEN_WORLD,
			TerminalContract.OPEN_WORLD_EXACT_TARGET, point(3683, 3706, 0), point(3683, 3706, 0),
			"Deranged archaeologist");
		direct(entries, "Duke Sucellus", "Ghorrock Dungeon", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(2920, 3930, 0), null, "Duke Sucellus");
		direct(entries, "General Graardor", "God Wars Dungeon", AreaType.MULTI_FLOOR,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(2916, 3746, 0), null, "General Graardor");
		direct(entries, "Giant Mole", "Falador Mole Lair", AreaType.DUNGEON,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(2996, 3377, 0), null, "Giant Mole");
		direct(entries, "Maggot King", "Maggot King's lair", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(3161, 7711, 0), null, "Maggot King");
		direct(entries, "Phantom Muspah", "Ghorrock Dungeon", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(2920, 3930, 0), null, "Phantom Muspah");
		direct(entries, "Leviathan", "The Scar", AreaType.INSTANCE,
			TerminalContract.INSTANCE_REGION_STOP, point(2081, 6372, 0), null, "The Leviathan");
		direct(entries, "The Whisperer", "Lassar Undercity", AreaType.INSTANCE,
			TerminalContract.INSTANCE_REGION_STOP, point(3000, 3494, 0), point(2656, 6370, 0), "The Whisperer");
		direct(entries, "Vardorvis", "Stranglewood Temple", AreaType.INSTANCE,
			TerminalContract.INSTANCE_REGION_STOP, point(1128, 3417, 0), null, "Vardorvis");
		direct(entries, "Commander Zilyana", "God Wars Dungeon", AreaType.MULTI_FLOOR,
			TerminalContract.MANUAL_STAGE_EXACT_CHECKPOINT, point(2916, 3746, 0), null,
			"Commander Zilyana");
		direct(entries, "Zulrah", "Zul-Andra", AreaType.INSTANCE,
			TerminalContract.INSTANCE_ENTRANCE_OR_WIDGET_STOP, point(2211, 3057, 0), null, "Zulrah");

		validate(entries);
		return Collections.unmodifiableList(entries);
	}

	private static void assignments(
		final List<Entry> entries,
		final String location,
		final AreaType areaType,
		final TerminalContract terminalContract,
		final Endpoint expectedAccess,
		final Endpoint expectedTerminal,
		final String... names)
	{
		for (final String name : names)
		{
			entries.add(new Entry(
				EntryKind.ASSIGNMENT,
				name,
				name,
				location,
				areaType,
				terminalContract,
				mechanismFor(terminalContract, expectedTerminal),
				expectedAccess,
				expectedTerminal,
				name,
				name
			));
		}
	}

	private static void assignment(
		final List<Entry> entries,
		final String name,
		final String location,
		final AreaType areaType,
		final Endpoint expectedAccess,
		final Endpoint expectedTerminal)
	{
		assignments(
			entries,
			location,
			areaType,
			TerminalContract.FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
			expectedAccess,
			expectedTerminal,
			name
		);
	}

	private static void selectable(
		final List<Entry> entries,
		final String encounterName,
		final String location,
		final AreaType areaType,
		final TerminalContract terminalContract,
		final Endpoint expectedAccess,
		final Endpoint expectedTerminal,
		final String wikiPageTitle,
		final String... assignments)
	{
		entries.add(new Entry(
			EntryKind.SELECTABLE_BOSS,
			"",
			encounterName,
			location,
			areaType,
			terminalContract,
			mechanismFor(terminalContract, expectedTerminal),
			expectedAccess,
			expectedTerminal,
			wikiPageTitle,
			assignments
		));
	}

	private static void direct(
		final List<Entry> entries,
		final String encounterName,
		final String location,
		final AreaType areaType,
		final TerminalContract terminalContract,
		final Endpoint expectedAccess,
		final Endpoint expectedTerminal,
		final String wikiPageTitle)
	{
		entries.add(new Entry(
			EntryKind.DIRECT_BOSS,
			encounterName,
			encounterName,
			location,
			areaType,
			terminalContract,
			mechanismFor(terminalContract, expectedTerminal),
			expectedAccess,
			expectedTerminal,
			wikiPageTitle,
			encounterName
		));
	}

	private static Endpoint point(final int x, final int y, final int plane)
	{
		return new Endpoint(x, y, plane);
	}

	private static TerminalMechanism mechanismFor(
		final TerminalContract terminalContract,
		final Endpoint expectedTerminal)
	{
		switch (terminalContract)
		{
			case OPEN_WORLD_EXACT_TARGET:
				return TerminalMechanism.STATIC_WORLD_POINT;
			case FULL_TRANSPORT_CHAIN_EXACT_INTERIOR:
				/* A reviewed full chain may stop on an exact loaded NPC rather than
				 * inventing a single combat-room tile for a moving spawn pack. */
				return expectedTerminal == null
					? TerminalMechanism.EXACT_NPC_DISCOVERY
					: TerminalMechanism.STATIC_WORLD_POINT;
			case INSTANCE_ENTRANCE_OR_WIDGET_STOP:
			case WAVE_ENTRANCE_OR_WIDGET_STOP:
				return TerminalMechanism.OBJECT_OR_WIDGET_STOP;
			case INSTANCE_REGION_STOP:
				return TerminalMechanism.REGION_ARRIVAL_STOP;
			case MANUAL_STAGE_EXACT_CHECKPOINT:
				return TerminalMechanism.SPECIAL_HANDOFF;
			default:
				throw new IllegalStateException("Unhandled terminal contract: " + terminalContract);
		}
	}

	private static String unresolvedReasonFor(
		final String location,
		final TerminalContract terminalContract,
		final Endpoint expectedTerminal)
	{
		if (expectedTerminal != null)
		{
			return "";
		}

		switch (terminalContract)
		{
			case OPEN_WORLD_EXACT_TARGET:
				return "Authoritative open-world target WorldPoint is UNKNOWN for " + location;
			case FULL_TRANSPORT_CHAIN_EXACT_INTERIOR:
				return "Authoritative interior terminal WorldPoint is UNKNOWN for " + location;
			case MANUAL_STAGE_EXACT_CHECKPOINT:
				return "Authoritative manual interaction checkpoint WorldPoint is UNKNOWN for " + location;
			case INSTANCE_ENTRANCE_OR_WIDGET_STOP:
				return "Authoritative instance entrance object/widget WorldPoint is UNKNOWN for " + location;
			case INSTANCE_REGION_STOP:
				return "Authoritative instance arrival/stop region WorldPoint is UNKNOWN for " + location;
			case WAVE_ENTRANCE_OR_WIDGET_STOP:
				return "Authoritative wave entrance NPC/widget WorldPoint is UNKNOWN for " + location;
			default:
				throw new IllegalStateException("Unhandled terminal contract: " + terminalContract);
		}
	}

	private static Set<String> setOf(final String... values)
	{
		final Set<String> result = new LinkedHashSet<>();
		for (final String value : values)
		{
			if (value == null || value.trim().isEmpty())
			{
				throw new IllegalStateException("Blank canonical manifest name");
			}
			if (!result.add(value))
			{
				throw new IllegalStateException("Duplicate canonical manifest name: " + value);
			}
		}
		return Collections.unmodifiableSet(result);
	}

	private static void validate(final List<Entry> entries)
	{
		final Set<String> keys = new LinkedHashSet<>();
		final Set<String> assignmentNames = new LinkedHashSet<>();
		final Set<String> selectableBossNames = new LinkedHashSet<>();
		final Set<String> directBossNames = new LinkedHashSet<>();
		for (final Entry entry : entries)
		{
			if (entry.encounterName == null || entry.encounterName.trim().isEmpty()
				|| entry.location == null || entry.location.trim().isEmpty()
				|| entry.areaType == null || entry.terminalContract == null
				|| entry.terminalMechanism == null)
			{
				throw new IllegalStateException("Incomplete Slayer route release manifest row");
			}
			if ((entry.expectedTerminal == null) == entry.unresolvedReason.trim().isEmpty())
			{
				throw new IllegalStateException(
					"Manifest row must either establish a terminal or retain an unresolved reason: "
						+ entry.encounterName + " at " + entry.location
				);
			}
			final String key = entry.kind + "|" + entry.assignmentName
				+ "|" + entry.encounterName + "|" + entry.location;
			if (!keys.add(key))
			{
				throw new IllegalStateException("Duplicate Slayer route release manifest row: " + key);
			}

			switch (entry.kind)
			{
				case ASSIGNMENT:
					assignmentNames.add(entry.assignmentName);
					break;
				case SELECTABLE_BOSS:
					selectableBossNames.add(entry.encounterName);
					break;
				case DIRECT_BOSS:
					directBossNames.add(entry.encounterName);
					break;
				default:
					throw new IllegalStateException("Unhandled manifest entry kind: " + entry.kind);
			}
		}

		validateParity("assignment", CANONICAL_ASSIGNMENT_NAMES, assignmentNames);
		validateParity("selectable boss", CANONICAL_SELECTABLE_BOSS_NAMES, selectableBossNames);
		validateParity("direct boss", CANONICAL_DIRECT_BOSS_NAMES, directBossNames);
	}

	private static void validateParity(
		final String label,
		final Set<String> expected,
		final Set<String> observed)
	{
		if (expected.equals(observed))
		{
			return;
		}

		final Set<String> missing = new LinkedHashSet<>(expected);
		missing.removeAll(observed);
		final Set<String> unexpected = new LinkedHashSet<>(observed);
		unexpected.removeAll(expected);
		throw new IllegalStateException(
			"Slayer route manifest " + label + " parity failure; missing=" + missing
				+ ", unexpected=" + unexpected
		);
	}
}
