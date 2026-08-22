package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SlayerTaskStrategyCatalog
{
	private static final String[] CURRENT_TASKS = new String[]{
		"Aberrant spectres",
		"Abyssal demons",
		"The Abyssal Sire",
		"The Alchemical Hydra",
		"Amoxliatl",
		"Ankou",
		"Aquanites",
		"Araxxor",
		"Araxytes",
		"Artio",
		"Aviansies",
		"Bandits",
		"Banshees",
		"Barrows Brothers",
		"Basilisks",
		"Bats",
		"Bears",
		"Birds",
		"Black demons",
		"Black dragons",
		"Black Knights",
		"Bloodveld",
		"Blue dragons",
		"Brine rats",
		"Callisto",
		"Catablepon",
		"Cave bugs",
		"Cave crawlers",
		"Cave horrors",
		"Cave kraken",
		"Cave slimes",
		"Cerberus",
		"Chaos druids",
		"The Chaos Elemental",
		"The Chaos Fanatic",
		"Cockatrice",
		"Cows",
		"Crabs",
		"Crawling hands",
		"Crazy Archaeologist",
		"Crazy Archaeologists",
		"Crocodiles",
		"Custodian Stalkers",
		"Dagannoth",
		"Dagannoth Kings",
		"Dark beasts",
		"Dark warriors",
		"Deranged Archaeologist",
		"Dogs",
		"Drakes",
		"Duke Sucellus",
		"Dust devils",
		"Dwarves",
		"Earth warriors",
		"Elves",
		"Ents",
		"Fever spiders",
		"Fire giants",
		"Fleshcrawlers",
		"Fossil island wyverns",
		"Frost dragons",
		"Gargoyles",
		"General Graardor",
		"Ghosts",
		"Ghouls",
		"The Giant Mole",
		"Goblins",
		"Greater demons",
		"Green dragons",
		"The Grotesque Guardians",
		"Gryphons",
		"Harpie bug swarms",
		"Hellhounds",
		"Hill giants",
		"Hobgoblins",
		"Hydras",
		"Icefiends",
		"Ice giants",
		"Ice warriors",
		"Infernal mages",
		"TzTok-Jad",
		"Jellies",
		"Jungle horrors",
		"Kalphites",
		"The Kalphite Queen",
		"Killerwatts",
		"The King Black Dragon",
		"The Cave Kraken Boss",
		"Kraken",
		"Kree'arra",
		"K'ril Tsutsaroth",
		"Kurask",
		"Lava Dragons",
		"Lesser demons",
		"Lesser Nagua",
		"Lizardmen",
		"Lizards",
		"The Maggot King",
		"Magic axes",
		"Mammoths",
		"Metal dragons",
		"Minotaurs",
		"Mogres",
		"Molanisks",
		"Monkeys",
		"Moss giants",
		"Mutated zygomites",
		"Nechryael",
		"Ogres",
		"Otherworldly beings",
		"The Phantom Muspah",
		"Pirates",
		"Pyrefiends",
		"Rats",
		"Red dragons",
		"Revenants",
		"Rockslugs",
		"Rogues",
		"Sarachnis",
		"Scabarites",
		"Scorpia",
		"Scorpions",
		"Skotizo",
		"Sea snakes",
		"Shades",
		"Shadow warriors",
		"The Shellbane Gryphon",
		"Skeletal wyverns",
		"Skeletons",
		"Smoke devils",
		"Sourhogs",
		"Spiders",
		"Spindel",
		"Spiritual creatures",
		"Suqahs",
		"Terror dogs",
		"The Leviathan",
		"The Whisperer",
		"The Thermonuclear Smoke Devil",
		"Trolls",
		"Turoth",
		"Tzhaar",
		"Vampyres",
		"Vardorvis",
		"Calvar'ion",
		"Venators",
		"Venenatis",
		"Vet'ion",
		"Vorkath",
		"Scurrius",
		"Obor",
		"Bryophyta",
		"Brutus",
		"Demonic gorillas",
		"Tormented demons",
		"Royal Titans",
		"Wall beasts",
		"Warped Creatures",
		"Waterfiends",
		"Werewolves",
		"Wolves",
		"Wyrms",
		"Commander Zilyana",
		"Zombies",
		"TzKal-Zuk",
		"Zulrah"
	};

	private static final Map<String, Profiles> PROFILES = createProfiles();
	private static final Set<String> COVERED_TASKS = createCoveredTasks();
	private static final Set<String> REVIEWED_TASK_KEYS = createReviewedTaskKeys();

	private SlayerTaskStrategyCatalog()
	{
	}

	public static SlayerTaskStrategy resolve(
		final String taskName,
		final SlayerPreference.Playstyle playstyle,
		final SlayerPreference.Cannon cannonPreference,
		final SlayerPreference.Burst burstPreference,
		final SlayerPreference.CombatStyle combatPreference,
		final String location,
		final boolean wilderness)
	{
		final String task = canonicalTaskKey(taskName);

		/*
		 * Broad templates are deliberately disabled. A task produces a bank
		 * layout only after its method has been individually reviewed.
		 */
		if (!REVIEWED_TASK_KEYS.contains(task))
		{
			return unreviewed(taskName);
		}

		if (wilderness)
		{
			return wildernessStrategyFor(
				taskName,
				task,
				playstyle,
				cannonPreference,
				burstPreference,
				combatPreference
			);
		}

		final Profiles baseProfiles = PROFILES.get(task);
		if (baseProfiles == null)
		{
			return unreviewed(taskName);
		}

		final Profiles profiles = profilesForLocation(
			task,
			normalize(location),
			baseProfiles
		);
		SlayerTaskStrategy selected = null;
		if (combatPreference == SlayerPreference.CombatStyle.PREFER_MAGIC
			&& profiles.magic == null)
		{
			final SlayerTaskStrategy automatic = profiles.selectAutomatic(playstyle);
			if (automatic != null
				&& !automatic.isBoss()
				&& automatic.getCombatStyle() != SlayerTaskStrategy.CombatStyle.HYBRID)
			{
				final SlayerTaskStrategy elemental =
					SlayerElementalWeaknessCatalog.preferenceStrategy(taskName, wilderness);
				if (elemental != null)
				{
					selected = elemental.withSelectionNote(
						"Magic preference applied using the current Wiki-listed elemental weakness; Automatic keeps the reviewed practical method."
					);
				}
			}
		}
		if (selected == null)
		{
			selected = profiles.select(
				playstyle,
				cannonPreference,
				burstPreference,
				combatPreference
			);
		}

		final SlayerTaskTravelAuditCatalog.Entry travelAudit =
			SlayerTaskTravelAuditCatalog.find(taskName);
		final String cannonAudit = travelAudit == null
			? ""
			: normalize(travelAudit.getCannon());
		if (selected != null
			&& !selected.isBoss()
			&& cannonPreference == SlayerPreference.Cannon.PREFER
			&& !selected.hasTag(SlayerTaskStrategy.MethodTag.CANNON)
			&& locationAllowsCannon(taskName, location)
			&& cannonSupportsSelectedCombat(taskName, selected)
			&& (cannonAudit.contains("recommended")
				|| cannonAudit.contains("optional")))
		{
			selected = selected.withAdditionalTags(
				SlayerTaskStrategy.MethodTag.CANNON
			).withSelectionNote(
				"Cannon preference applied because the individually audited task location permits a dwarf multicannon."
			);
		}
		return selected;
	}

	private static boolean cannonSupportsSelectedCombat(
		final String taskName,
		final SlayerTaskStrategy strategy)
	{
		/*
		 * Every reviewed non-Wilderness cannon location for regular Greater demons
		 * is single-combat. Once a demon can attack a melee player, the cannon is
		 * locked to that one opponent. Only the safespotted Ranged/Magic branch can
		 * preserve the intended multi-target cannon behaviour.
		 */
		return strategy != null
			&& (!canonicalTaskKey(taskName).equals("greater demons")
				|| strategy.getCombatStyle()
					!= SlayerTaskStrategy.CombatStyle.MELEE);
	}

	/*
	 * The task-level travel fallback may describe a cannonable location even
	 * when Konar (or an explicit location choice) sends the same monster to a
	 * no-cannon area. Never let that broad fallback re-add CANNON after the
	 * location-specific strategy deliberately removed it.
	 */
	private static boolean locationAllowsCannon(
		final String taskName,
		final String location)
	{
		final String area = normalize(location);
		final String task = canonicalTaskKey(taskName);
		/* Greater demons are explicitly cannonable in their Karuulm room even
		 * though several other Karuulm assignments are not. */
		if (task.equals("greater demons") && area.contains("karuulm"))
		{
			return true;
		}
		return !area.contains("catacombs")
			&& !area.contains("slayer tower")
			&& !area.contains("fremennik slayer dungeon")
			&& !area.contains("karuulm")
			&& !area.contains("kraken")
			&& !area.contains("god wars")
			&& !area.contains("mos le harmless");
	}

	public static SlayerTaskStrategy legacy(final String method)
	{
		return unreviewed("Legacy recommendation");
	}

	public static boolean hasExplicitStrategy(final String taskName)
	{
		return REVIEWED_TASK_KEYS.contains(canonicalTaskKey(taskName));
	}

	public static Set<String> getCoveredTaskNames()
	{
		return COVERED_TASKS;
	}

	public static int getCurrentTaskCount()
	{
		return CURRENT_TASKS.length;
	}

	/** Returns the complete assignment list audited by the shared rule engine. */
	public static List<String> getCurrentTaskNames()
	{
		final List<String> names = new ArrayList<>();
		Collections.addAll(names, CURRENT_TASKS);
		return Collections.unmodifiableList(names);
	}

	public static int getReviewedTaskCount()
	{
		return REVIEWED_TASK_KEYS.size();
	}

	public static int getReviewProgressPercent()
	{
		if (CURRENT_TASKS.length == 0)
		{
			return 100;
		}
		return (int) Math.round(
			REVIEWED_TASK_KEYS.size() * 100.0 / CURRENT_TASKS.length
		);
	}

	public static SlayerTaskResearchCatalog.Entry getResearchRecord(
		final String taskName)
	{
		return SlayerTaskResearchCatalog.find(canonicalTaskKey(taskName));
	}

	public static List<String> getMissingCurrentTasks()
	{
		final List<String> missing = new ArrayList<>();
		for (final String task : CURRENT_TASKS)
		{
			if (!hasExplicitStrategy(task))
			{
				missing.add(task);
			}
		}
		return Collections.unmodifiableList(missing);
	}

	private static Map<String, Profiles> createProfiles()
	{
		final Map<String, Profiles> map = new HashMap<>();
		register(map, "Aberrant spectres", aberrantSpectreProfiles());
		register(map, "Abyssal demons", burstableDemonProfiles());
		register(map, "The Abyssal Sire", abyssalSireProfiles());
		register(map, "The Alchemical Hydra", alchemicalHydraProfiles());
		register(map, "Amoxliatl", amoxliatlProfiles());
		register(map, "Ankou", ankouCatacombsProfiles());
		register(map, "Aquanites", aquaniteProfiles());
		register(map, "Araxxor", araxxorBossProfiles());
		register(map, "Artio", artioProfiles());
		register(map, "Araxytes", araxyteProfiles());
		register(map, "Aviansies", rangedCrossbowProfiles("Ranged only; use a crossbow or bow and bring the required God Wars protection"));
		register(map, "Bandits", lowHitpointRangedProfiles("Use fast Ranged against low-hitpoint bandits; keep desert heat protection and avoid expensive overkill"));
		register(map, "Banshees", lowHitpointRangedProfiles("Wear earmuffs or a Slayer helmet and use fast Ranged against low-hitpoint banshees"));
		register(map, "Barrows Brothers", barrowsProfiles());
		register(map, "Basilisks", basiliskProfiles());
		register(map, "Bats", lowHitpointRangedProfiles("Use fast Ranged against 1 x 1 bats; do not spend Scythe charges on them"));
		register(map, "Bears", lowHitpointRangedProfiles("Use fast Ranged against ordinary bears; in the Wilderness keep the setup disposable"));
		register(map, "Birds", birdProfiles());
		register(map, "Black demons", demonbaneProfiles("Use an efficient demonbane weapon; avoid charge-heavy overkill on ordinary black demons"));
		register(map, "Black dragons", blackDragonProfiles());
		register(map, "Black Knights", lowHitpointRangedProfiles("Use fast Ranged at a dense Black Knight spawn; cannon only in a compatible assignment area"));
		register(map, "Bloodveld", bloodveldProfiles());
		register(map, "Blue dragons", blueDragonProfiles());
		register(map, "Brine rats", brineRatProfiles());
		register(map, "Callisto", callistoProfiles());
		register(map, "Catablepon", lowLevelSafespotProfiles(
			"Use Ranged from a safespot to avoid repeated Strength drain"
		));
		register(map, "Cave bugs", lowLevelSafespotProfiles(
			"Use Ranged from the Dorgesh-Kaan safespot with a safe light source"
		));
		register(map, "Cave crawlers", lowHitpointRangedProfiles("Use fast Ranged with poison protection available for cave crawler poison"));
		register(map, "Cave horrors", caveHorrorProfiles());
		register(map, "Cave kraken", caveKrakenProfiles());
		register(map, "Cave slimes", routineMeleeProfiles("Bring a safe light source and poison protection, then use efficient melee"));
		register(map, "Cerberus", cerberusProfiles());
		register(map, "Chaos druids", lowHitpointRangedProfiles("Use fast Ranged; for the Wilderness location keep only disposable gear and an escape teleport"));
		register(map, "The Chaos Elemental", wildernessBossSimpleRangedProfiles("Use a low-risk ranged setup with item-leaving food arranged to resist unequip attacks"));
		register(map, "The Chaos Fanatic", wildernessBossSimpleRangedProfiles("Use a low-risk ranged setup and avoid his area attack"));
		register(map, "Cockatrice", routineMeleeProfiles("Equip a mirror shield or V's shield to prevent severe stat drain"));
		register(map, "Cows", lowHitpointRangedProfiles("Use fast Ranged at the nearest dense cow spawn"));
		register(map, "Crabs", routineMeleeProfiles("Use fast low-upkeep melee and take advantage of their low Defence and aggression"));
		register(map, "Crawling hands", lowHitpointRangedProfiles("Use fast Ranged against low-hitpoint crawling hands in the Slayer Tower"));
		register(map, "Crazy Archaeologist", wildernessBossMagicProfiles("Use low-risk Magic and dodge the explosive special attack"));
		register(map, "Crazy Archaeologists", wildernessBossMagicProfiles("Use low-risk Magic and dodge the explosive special attack"));
		register(map, "Crocodiles", lowLevelSafespotProfiles(
			"Use Ranged from a riverbank or Nardah safespot"
		));
		register(map, "Custodian Stalkers", custodianStalkerProfiles());
		register(map, "Dagannoth", dagannothProfiles());
		register(map, "Dagannoth Kings", dagannothKingsProfiles());
		register(map, "Dark beasts", darkBeastProfiles());
		register(map, "Dark warriors", routineMeleeProfiles("Use a low-risk Wilderness melee setup and keep an immediate escape available"));
		register(map, "Deranged Archaeologist", researchedBossProfile(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use powered Magic and dodge the explosive special attack",
			false,
			"tumeken s shadow", "eye of ayak", "sanguinesti staff", "trident of the swamp",
			"trident of the seas", "warped sceptre", "iban s staff"
		));
		register(map, "Dogs", lowHitpointRangedProfiles("Use fast Ranged at a dense dog or jackal spawn"));
		register(map, "Drakes", drakeProfiles());
		register(map, "Duke Sucellus", researchedBossProfile(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use high-accuracy slash melee and prepare both arder-musca poisons inside the asylum",
			false,
			"scythe of vitur", "soulreaper axe", "noxious halberd",
			"blade of saeldor", "abyssal tentacle", "abyssal whip"
		));
		register(map, "Dust devils", barrageProfiles("Stack and burst or barrage dust devils"));
		register(map, "Dwarves", lowHitpointRangedProfiles("Use fast Ranged at a dense dwarf spawn"));
		register(map, "Earth warriors", earthWarriorProfiles());
		register(map, "Elves", blowpipeProfiles("Use ranged for consistent kills against Iorwerth elves", false));
		register(map, "Ents", entProfiles());
		register(map, "Fever spiders", feverSpiderProfiles());
		register(map, "Fire giants", fireGiantProfiles());
		register(map, "Fleshcrawlers", lowLevelSafespotProfiles(
			"Use Ranged from a Stronghold safespot for a low-supply task"
		));
		register(map, "Fossil island wyverns", fossilWyvernProfiles());
		register(map, "Frost dragons", frostDragonProfiles());
		register(map, "Gargoyles", gargoyleProfiles());
		register(map, "General Graardor", graardorProfiles());
		register(map, "Ghosts", routineMeleeProfiles("Use efficient melee; undead damage bonuses do not stack with a Slayer helmet"));
		register(map, "Ghouls", lowLevelSafespotProfiles(
			"Use Ranged from the Canifis gravestone safespot"
		));
		register(map, "The Giant Mole", giantMoleProfiles());
		register(map, "Goblins", lowHitpointRangedProfiles("Use fast Ranged at a dense goblin spawn"));
		register(map, "Greater demons", greaterDemonProfiles());
		register(map, "Green dragons", dragonRangedProfiles(false));
		register(map, "The Grotesque Guardians", grotesqueGuardianProfiles());
		register(map, "Gryphons", gryphonProfiles());
		register(map, "Harpie bug swarms", harpieBugProfiles());
		register(map, "Hellhounds", hellhoundProfiles());
		register(map, "Hill giants", lowHitpointRangedProfiles("Use fast Ranged at a dense spawn; cannon only where allowed"));
		register(map, "Hobgoblins", lowHitpointRangedProfiles("Use fast Ranged and a safespot where available"));
		register(map, "Hydras", hydraTaskProfiles());
		register(map, "Icefiends", fireWeaknessProfiles("Use the best Fire spell against the Icefiend's 100% Fire weakness", false));
		register(map, "Ice giants", fireWeaknessProfiles("Use the best Fire spell against the Ice giant's 100% Fire weakness", true));
		register(map, "Ice warriors", fireWeaknessProfiles("Use the best Fire spell against the Ice warrior's 100% Fire weakness", true));
		register(map, "Infernal mages", magicDefenceMeleeProfiles("Wear high Magic-defence gear and use efficient melee while protecting from Magic when appropriate"));
		register(map, "TzTok-Jad", jadProfiles());
		register(map, "Jellies", burstableRangedProfiles("Jellies"));
		register(map, "Jungle horrors", blowpipeProfiles("Use ranged with a Toxic blowpipe for quick, safe kills", false));
		register(map, "Kalphites", kalphiteProfiles());
		register(map, "The Kalphite Queen", kalphiteQueenProfiles());
		register(map, "Killerwatts", airWeaknessProfiles("Safespot with the best Wind spell against the Killerwatt's 60% Air weakness", true));
		register(map, "The King Black Dragon", kingBlackDragonProfiles());
		register(map, "The Cave Kraken Boss", krakenBossProfiles());
		register(map, "Kraken", krakenBossProfiles());
		register(map, "Kree'arra", kreearraProfiles());
		register(map, "K'ril Tsutsaroth", krilProfiles());
		register(map, "Kurask", leafBladedProfiles("Use a leaf-bladed battleaxe or sword; broad ammunition is the ranged alternative"));
		register(map, "Lava Dragons", wildernessMagicProfiles("Use a low-risk Magic setup with antifire protection"));
		register(map, "Lesser demons", demonbaneProfiles("Use an efficient demonbane weapon"));
		register(map, "Lesser Nagua", naguaProfiles());
		register(map, "Lizardmen", blowpipeProfiles("Use ranged; Toxic blowpipe is the practical default for lizardmen and shamans", false));
		register(map, "Lizards", routineMeleeProfiles("Bring enough ice coolers to finish each desert lizard and use efficient melee"));
		register(map, "The Maggot King", maggotKingProfiles());
		register(map, "Magic axes", routineMeleeProfiles("Use a low-risk Wilderness melee setup and keep an immediate escape available"));
		register(map, "Mammoths", wildernessRangedProfiles("Use a low-risk ranged setup"));
		register(map, "Metal dragons", metalDragonProfiles());
		register(map, "Minotaurs", lowLevelSafespotProfiles(
			"Use Ranged from a Stronghold of Security safespot"
		));
		register(map, "Mogres", mogreProfiles());
		register(map, "Molanisks", crushTaskProfiles("Use crush melee after ringing the Slayer bell to dislodge each molanisk", false));
		register(map, "Monkeys", lowHitpointRangedProfiles("Use fast Ranged at the selected ordinary monkey spawn"));
		register(map, "Moss giants", fireWeaknessProfiles("Safespot with the best Fire spell against the Moss giant's 50% Fire weakness", true));
		register(map, "Mutated zygomites", magicDefenceMeleeProfiles("Wear dragonhide or other high Magic-defence armour; bring fungicide spray with charges and use it to finish weakened zygomites"));
		register(map, "Nechryael", barrageProfiles("Stack and burst or barrage nechryaels"));
		register(map, "Ogres", lowLevelSafespotProfiles(
			"Use Ranged from the reviewed ogre safespot; a cannon remains optional where the selected location permits it"
		));
		register(map, "Otherworldly beings", airWeaknessProfiles(
			"Safespot across the Zanaris pond with the best Wind spell against their 35% Air weakness",
			true
		));
		register(map, "The Phantom Muspah", phantomMuspahProfiles());
		register(map, "Pirates", lowHitpointRangedProfiles("Use a low-risk fast Ranged setup at the selected pirate spawn and preserve an escape"));
		register(map, "Pyrefiends", waterWeaknessProfiles("Safespot with the best Water spell against the Pyrefiend's 100% Water weakness", true));
		register(map, "Rats", ratProfiles());
		register(map, "Red dragons", dragonRangedProfiles(false));
		register(map, "Revenants", wildernessRangedProfiles("Use a low-risk ranged setup"));
		register(map, "Rockslugs", rockslugProfiles());
		register(map, "Rogues", routineMeleeProfiles("Use a low-risk Wilderness melee setup and keep an immediate escape available"));
		register(map, "Sarachnis", sarachnisProfiles());
		register(map, "Scabarites", scabariteProfiles());
		register(map, "Scorpia", wildernessBossMagicProfiles("Use a low-risk Magic setup and freeze the boss and guardians"));
		register(map, "Scorpions", lowHitpointRangedProfiles("Use fast Ranged; for Wilderness spawns keep the setup disposable"));
		register(map, "Skotizo", skotizoProfiles());
		register(map, "Sea snakes", routineMeleeProfiles("Use efficient melee with antipoison available for snake poison"));
		register(map, "Shades", airWeaknessProfiles("Use the best Wind spell against the Shade's 40% Air weakness", true));
		register(map, "Shadow warriors", lowLevelSafespotProfiles(
			"Use Ranged through the Legends' Guild dungeon fence safespot"
		));
		register(map, "The Shellbane Gryphon", shellbaneProfiles());
		register(map, "Skeletal wyverns", wyvernProfiles());
		register(map, "Skeletons", routineMeleeProfiles("Use efficient low-risk melee; undead damage bonuses do not stack with a Slayer helmet"));
		register(map, "Smoke devils", smokeDevilProfiles());
		register(map, "Sourhogs", routineMeleeProfiles("Wear reinforced goggles or a Slayer helmet and use efficient melee"));
		register(map, "Spiders", routineMeleeProfiles("Use fast low-upkeep melee; for Wilderness spawns keep the setup disposable"));
		register(map, "Spindel", wildernessCrushProfiles("Use a low-risk crush setup; Ursine chainmace is the efficient Wilderness choice"));
		register(map, "Spiritual creatures", blowpipeProfiles("Use ranged and kill the highest unlocked spiritual creature", false));
		register(map, "Suqahs", suqahProfiles());
		register(map, "Terror dogs", lowLevelSafespotProfiles(
			"Use Ranged from Tarn's room doorway safespot"
		));
		register(map, "The Leviathan", researchedBossProfile(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use high-tier Ranged with a Webweaver special-attack switch for the enrage phase",
			false,
			"bow of faerdhinen", "twisted bow", "zaryte crossbow",
			"webweaver bow", "toxic blowpipe"
		));
		register(map, "The Whisperer", researchedBossProfile(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use high-DPS Magic, preserve sanity through each special, and use a fast ranged switch for Lost Souls",
			false,
			"tumeken s shadow", "eye of ayak", "sanguinesti staff",
			"trident of the swamp", "trident of the seas", "warped sceptre"
		));
		register(map, "The Thermonuclear Smoke Devil", thermonuclearSmokeDevilProfiles());
		register(map, "Trolls", trollProfiles());
		register(map, "Turoth", leafBladedProfiles("Use a leaf-bladed weapon or broad ammunition"));
		register(map, "Tzhaar", tzhaarProfiles());
		register(map, "Vampyres", vampyreProfiles());
		register(map, "Vardorvis", researchedBossProfile(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use high-DPS slash melee and react to axes, the head projectile, and the weakening special",
			false,
			"soulreaper axe", "scythe of vitur", "noxious halberd",
			"blade of saeldor", "abyssal tentacle", "abyssal whip"
		));
		register(map, "Calvar'ion", wildernessCrushProfiles("Use a low-risk crush setup; Ursine chainmace is the efficient Wilderness choice"));
		register(map, "Venators", venatorCreatureProfiles());
		register(map, "Venenatis", wildernessCrushProfiles("Use a low-risk crush setup; Ursine chainmace is the efficient Wilderness choice"));
		register(map, "Vet'ion", wildernessCrushProfiles("Use a low-risk crush setup; Ursine chainmace is the efficient Wilderness choice"));
		register(map, "Vorkath", vorkathProfiles());
		register(map, "Scurrius", scurriusProfiles());
		register(map, "Obor", oborProfiles());
		register(map, "Bryophyta", bryophytaProfiles());
		register(map, "Brutus", brutusProfiles());
		register(map, "Demonic gorillas", demonicGorillaProfiles());
		register(map, "Tormented demons", tormentedDemonProfiles());
		register(map, "Royal Titans", royalTitansProfiles());
		register(map, "Wall beasts", routineMeleeProfiles("Wear a spiny helmet or Slayer helmet to prevent wall-beast grabs and bring a safe light source"));
		register(map, "Warped Creatures", warpedProfiles());
		register(map, "Waterfiends", waterfiendProfiles());
		register(map, "Werewolves", routineMeleeProfiles("Use efficient melee and avoid bringing an unnecessary Wolfbane if it would prevent transformation"));
		register(map, "Wolves", lowLevelSafespotProfiles(
			"Safespot with Ranged, or keep Protect from Melee active; use a dwarf multicannon where the selected area allows it"
		));
		register(map, "Wyrms", wyrmProfiles());
		register(map, "Commander Zilyana", zilyanaProfiles());
		register(map, "Zombies", routineMeleeProfiles("Use efficient low-risk melee; undead damage bonuses do not stack with a Slayer helmet"));
		register(map, "TzKal-Zuk", zukProfiles());
		register(map, "Zulrah", zulrahProfiles());

		final List<String> missing = new ArrayList<>();
		for (final String task : CURRENT_TASKS)
		{
			if (!map.containsKey(normalize(task)))
			{
				missing.add(task);
			}
		}
		if (!missing.isEmpty())
		{
			throw new IllegalStateException(
				"Missing Slayer task strategies: " + missing
			);
		}
		return Collections.unmodifiableMap(map);
	}

	private static Set<String> createCoveredTasks()
	{
		return SlayerTaskResearchCatalog.getReviewedTaskNames();
	}

	private static Set<String> createReviewedTaskKeys()
	{
		return SlayerTaskResearchCatalog.getReviewedTaskKeys();
	}

	private static void register(
		final Map<String, Profiles> map,
		final String taskName,
		final Profiles profiles)
	{
		final String key = normalize(taskName);
		final SlayerTaskResearchCatalog.Entry research =
			SlayerTaskResearchCatalog.find(taskName);
		final Profiles stored = research == null
			? profiles
			: profiles.reviewed(research.getReviewDate());
		if (map.put(key, stored) != null)
		{
			throw new IllegalStateException(
				"Duplicate Slayer task strategy: " + taskName
			);
		}
	}

	private static SlayerTaskStrategy wildernessStrategyFor(
		final String taskName,
		final String normalizedTask,
		final SlayerPreference.Playstyle playstyle,
		final SlayerPreference.Cannon cannonPreference,
		final SlayerPreference.Burst burstPreference,
		final SlayerPreference.CombatStyle combatPreference)
	{
		final SlayerTaskResearchCatalog.Entry research =
			SlayerTaskResearchCatalog.find(taskName);
		if (research == null || !research.isWildernessReviewed())
		{
			return unreviewedWilderness(taskName);
		}

		/* Intrinsically Wilderness encounters already own an individually
		 * authored LOW_RISK profile in PROFILES.  Reuse only that reviewed
		 * Wilderness-tagged profile; never reuse a normal-task setup merely
		 * because the assignment happens to come from Krystilia. */
		final Profiles profiles = PROFILES.get(normalizedTask);
		if (profiles != null)
		{
			final SlayerTaskStrategy selected = profiles.select(
				playstyle,
				cannonPreference,
				burstPreference,
				combatPreference
			);
			if (selected != null
				&& selected.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS)
				&& selected.getCostPolicy() == SlayerTaskStrategy.CostPolicy.LOW_RISK)
			{
				return selected.withSelectionNote(
					"Reviewed low-risk Wilderness profile applied."
				);
			}
		}

		final Profiles krystiliaProfiles = krystiliaProfiles(normalizedTask);
		if (krystiliaProfiles != null)
		{
			return krystiliaProfiles.reviewed(
				SlayerTaskResearchCatalog.REVIEW_DATE
			).select(
				playstyle,
				cannonPreference,
				burstPreference,
				combatPreference
			).withSelectionNote(
				"Krystilia assignment: individually reviewed LOW_RISK Wilderness branch applied."
			);
		}

		return unreviewedWilderness(taskName);
	}

	private static Profiles profilesForLocation(
		final String task,
		final String location,
		final Profiles fallback)
	{
		if (task.equals("ankou"))
		{
			if (location.contains("stronghold slayer cave"))
			{
				return ankouCannonProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
			if (location.contains("stronghold of security"))
			{
				return ankouSafespotProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
		}

		if (task.equals("abyssal demons")
			&& location.contains("slayer tower"))
		{
			return abyssalDemonSingleCombatProfiles().reviewed(
				SlayerTaskResearchCatalog.REVIEW_DATE
			);
		}

		if (task.equals("bloodveld"))
		{
			if (location.contains("meiyerditch")
				|| location.contains("buccaneer"))
			{
				return bloodveldMultiCannonProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
			if (location.contains("iorwerth")
				|| location.contains("stronghold slayer cave"))
			{
				return bloodveldSingleCannonProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
			if (location.contains("catacombs"))
			{
				return bloodveldCatacombsProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
			if (location.contains("slayer tower"))
			{
				return bloodveldSlayerTowerProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
			if (location.contains("god wars dungeon"))
			{
				return bloodveldGodWarsProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
		}

		if (task.equals("aberrant spectres"))
		{
			if (location.contains("stronghold slayer cave"))
			{
				return aberrantSpectreCannonProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
			if (location.contains("catacombs"))
			{
				return aberrantSpectreCatacombsProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
		}

		if (task.equals("hellhounds"))
		{
			if (location.contains("stronghold slayer cave")
				|| location.contains("taverley dungeon")
				|| location.contains("karuulm slayer dungeon"))
			{
				return hellhoundCannonProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
			if (location.contains("catacombs"))
			{
				return hellhoundCatacombsProfiles().reviewed(
					SlayerTaskResearchCatalog.REVIEW_DATE
				);
			}
		}

		if (task.equals("greater demons")
			&& locationAllowsCannon(task, location))
		{
			return greaterDemonSingleCombatCannonProfiles().reviewed(
				SlayerTaskResearchCatalog.REVIEW_DATE
			);
		}

		if (task.equals("nechryael")
			&& location.contains("iorwerth"))
		{
			final SlayerTaskStrategy melee = demonbane(
				"Use demonbane melee against Greater Nechryaels in single combat for crystal shard drops"
			);
			final SlayerTaskStrategy cannon = melee.withAdditionalTags(
				SlayerTaskStrategy.MethodTag.CANNON
			);
			return Profiles.same(melee)
				.withCombatOptions(melee, null, null)
				.withCannonOptions(cannon, melee)
				.reviewed(SlayerTaskResearchCatalog.REVIEW_DATE);
		}

		return fallback;
	}

	private static boolean isBurstTask(final String task)
	{
		return task.equals("abyssal demons")
			|| task.equals("ankou")
			|| task.equals("dust devils")
			|| task.equals("jellies")
			|| task.equals("nechryael")
			|| task.equals("smoke devils");
	}

	private static SlayerTaskStrategy nonBurstAlternative(final String task)
	{
		if (task.equals("abyssal demons"))
		{
			return demonbane("Use efficient demonbane melee; burst and barrage are disabled");
		}
		if (task.equals("dust devils") || task.equals("smoke devils"))
		{
			return blowpipe("Use ranged; burst and barrage are disabled", false);
		}
		return venator("Use a Venator bow; burst and barrage are disabled");
	}

	private static Profiles routineMeleeProfiles(final String method)
	{
		final SlayerTaskStrategy base = efficientMelee(method);
		return Profiles.same(base);
	}

	private static Profiles birdProfiles()
	{
		final SlayerTaskStrategy ranged = rangedCrossbow(
			"Use Ranged against the selected bird variant; this also covers flying variants that cannot be attacked with melee",
			false
		);
		final SlayerTaskStrategy melee = efficientMelee(
			"Use fast low-upkeep melee only against a grounded bird variant such as chickens or terrorbirds"
		);
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	private static Profiles ratProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use a bone mace for its large rat-bane damage bonus; ordinary fast melee is the fallback"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"bone mace", "ghrazi rapier", "blade of saeldor",
				"abyssal tentacle", "abyssal whip", "zombie axe", "dragon scimitar"
			)
			.build();
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use a bone shortbow for its large rat-bane damage bonus"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons("bone shortbow")
			.build();
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use a bone staff for its large rat-bane damage bonus"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons("bone staff")
			.build();
		return Profiles.same(melee).withCombatOptions(melee, ranged, magic);
	}

	private static Profiles magicDefenceMeleeProfiles(final String method)
	{
		final SlayerTaskStrategy base = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.MAGIC_DEFENCE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"ghrazi rapier", "blade of saeldor", "inquisitor s mace",
				"abyssal tentacle", "abyssal whip", "zombie axe", "dragon scimitar"
			)
			.prayerPotionSlots(4)
			.build();
		return Profiles.same(base);
	}

	private static Profiles brineRatProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Bring a spade and food; use the best rat-bone weapon for its flat damage bonus, or ordinary efficient melee"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"bone mace",
				"ghrazi rapier", "blade of saeldor", "abyssal tentacle",
				"abyssal whip", "zombie axe", "dragon scimitar"
			)
			.foodSlots(6)
			.build();
		final SlayerTaskStrategy ranged = rangedCrossbow(
			"Bring a spade and use the south-eastern or north-western Brine Rat Cavern safespot",
			false
		);
		return Profiles.same(melee).withCombatOptions(melee, ranged, null);
	}

	private static Profiles lowLevelSafespotProfiles(final String method)
	{
		final SlayerTaskStrategy ranged = rangedCrossbow(method, false)
			.withSelectionNote(
				"Automatic uses the researched safespot; melee remains available when the account owns no reviewed ranged weapon and the task mechanic permits it."
			);
		final SlayerTaskStrategy melee = efficientMelee(
			"Use efficient melee only when not using the researched safespot"
		);
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	/**
	 * Progression-safe default for ordinary low-hitpoint targets. Fast four-tick
	 * Ranged avoids the overkill and movement delay of slow premium melee while
	 * still retaining a complete early-account ladder when Blowpipe is unowned.
	 */
	private static Profiles lowHitpointRangedProfiles(final String method)
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"toxic blowpipe", "eclipse atlatl", "hunters sunlight crossbow",
				"magic shortbow i", "magic shortbow", "rune knife",
				"rune dart", "adamant knife", "adamant dart",
				"dorgeshuun crossbow", "maple shortbow", "willow shortbow",
				"oak shortbow", "shortbow"
			)
			.prayerPotionSlots(0)
			.foodSlots(2)
			.strictWeaponProfile(true)
			.reviewed("2026-08-16")
			.build();
		final SlayerTaskStrategy melee = efficientMelee(
			"Use fast charge-free melee when the account owns no reviewed Ranged progression item"
		);
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	private static Profiles prayerMelee(final String method)
	{
		final SlayerTaskStrategy base = efficientMelee(method, true);
		return Profiles.same(base);
	}

	/**
	 * Dark beasts are an individually researched melee task. Fast XP uses the
	 * 3x3-target advantage of Scythe where owned and permits a cannon; Profit
	 * avoids charge-heavy weapons; AFK deliberately values prayer bonus and
	 * lets the always-aggressive beast initiate combat so Protect from Melee
	 * handles the normal attack cycle. Ranged/Magic are not exposed as preferred
	 * alternatives merely because they are technically possible: their defences
	 * make melee the established practical method.
	 */
	private static Profiles darkBeastProfiles()
	{
		final SlayerTaskStrategy fastCannon = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"High-DPS melee with Protect from Melee and a dwarf multicannon"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Dark beasts are large 3x3 targets, so Scythe is the premium speed option; "
					+ "stab-focused one-handed weapons are the practical fallback. Keep Protect from Melee active."
			)
			.weapons(
				"scythe of vitur",
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip"
			)
			.maxDpsWeapons(
				"scythe of vitur",
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip"
			)
			.efficientWeapons(
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip"
			)
			.optionalItems(
				"saradomin godsword",
				"herb sack",
				"gem bag",
				"seed box"
			)
			.tags(SlayerTaskStrategy.MethodTag.CANNON)
			.prayerPotionSlots(5)
			.foodSlots(3)
			.strictWeaponProfile(true)
			.reviewed(SlayerTaskResearchCatalog.REVIEW_DATE)
			.build();

		final SlayerTaskStrategy fastNoCannon = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"High-DPS melee with Protect from Melee; no cannon"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"Use Scythe on the large target when charge cost is acceptable; otherwise use a strong stab-focused melee weapon."
			)
			.weapons(
				"scythe of vitur",
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip"
			)
			.maxDpsWeapons(
				"scythe of vitur",
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip"
			)
			.efficientWeapons(
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip"
			)
			.optionalItems("saradomin godsword", "herb sack", "gem bag", "seed box")
			.prayerPotionSlots(5)
			.foodSlots(2)
			.strictWeaponProfile(true)
			.reviewed(SlayerTaskResearchCatalog.REVIEW_DATE)
			.build();

		final SlayerTaskStrategy profit = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Efficient stab-focused melee with Protect from Melee"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Avoid Scythe charge cost; use Fang/Rapier-class melee and preserve room for herbs, gems, seeds, and crystal-shard value where available."
			)
			.weapons(
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip",
				"zombie axe"
			)
			.efficientWeapons(
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip",
				"zombie axe"
			)
			.optionalItems("saradomin godsword", "herb sack", "gem bag", "seed box")
			.prayerPotionSlots(4)
			.foodSlots(2)
			.strictWeaponProfile(true)
			.reviewed(SlayerTaskResearchCatalog.REVIEW_DATE)
			.build();

		final SlayerTaskStrategy afk = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Prayer-bonus melee with Protect from Melee; let each Dark beast attack first"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Dark beasts stay aggressive. Letting the next beast initiate while you remain in melee range avoids unnecessary opening Magic attacks and supports a low-attention prayer setup."
			)
			.weapons(
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip",
				"zombie axe"
			)
			.efficientWeapons(
				"osmumten s fang",
				"ghrazi rapier",
				"blade of saeldor",
				"abyssal tentacle",
				"abyssal whip",
				"zombie axe"
			)
			.optionalItems("saradomin godsword", "herb sack", "gem bag", "seed box")
			.prayerPotionSlots(5)
			.foodSlots(2)
			.strictWeaponProfile(true)
			.reviewed(SlayerTaskResearchCatalog.REVIEW_DATE)
			.build();

		return new Profiles(profit, fastCannon, afk, profit)
			.withCombatOptions(afk, null, null)
			.withCannonOptions(fastCannon, fastNoCannon);
	}

	private static Profiles blowpipeProfiles(
		final String method,
		final boolean prayer)
	{
		final SlayerTaskStrategy balanced = blowpipe(method, prayer);
		final SlayerTaskStrategy afk = rangedPrayer(
			method + "; use prayer gear for a lower-attention trip"
		);
		return new Profiles(balanced, balanced, afk, balanced);
	}

	private static Profiles venatorProfiles(final String method)
	{
		final SlayerTaskStrategy balanced = venator(method);
		final SlayerTaskStrategy fast = blowpipe(
			"Use a Toxic blowpipe for the fastest single-target kills",
			false
		);
		return new Profiles(balanced, fast, balanced, balanced);
	}

	private static Profiles rangedCrossbowProfiles(final String method)
	{
		final SlayerTaskStrategy base = rangedCrossbow(method, false);
		return Profiles.same(base);
	}

	private static Profiles cannonRangedProfiles(final String method)
	{
		final SlayerTaskStrategy base = cannonBlowpipe(method);
		return Profiles.same(base);
	}

	/*
	 * These three tasks are cannon-friendly, not cannon-mandatory. Their
	 * researched no-cannon fallbacks are explicit so Cannon.NEVER can never
	 * leak a cannon-tagged method into the panel or Bank Tag.
	 */
	private static Profiles scabariteProfiles()
	{
		final SlayerTaskStrategy cannon = cannonBlowpipe(
			"Use fast attacks with a dwarf multicannon in a cannonable Scabarite area"
		);
		final SlayerTaskStrategy noCannon = efficientMelee(
			"Use sustainable melee when cannon is disabled; Guthan-style sustain is a practical fallback",
			true
		);
		return Profiles.same(cannon)
			.withCombatOptions(noCannon, cannon, null)
			.withCannonOptions(cannon, noCannon);
	}

	private static Profiles suqahProfiles()
	{
		final SlayerTaskStrategy cannon = cannonBlowpipe(
			"Use a Toxic blowpipe with a dwarf multicannon for fast Suqah tasks"
		);
		final SlayerTaskStrategy noCannon = efficientMelee(
			"Use stab-focused melee with Protect from Melee when cannon is disabled",
			true
		);
		return Profiles.same(cannon)
			.withCombatOptions(noCannon, cannon, null)
			.withCannonOptions(cannon, noCannon);
	}

	private static Profiles trollProfiles()
	{
		final SlayerTaskStrategy cannon = cannonBlowpipe(
			"Use ranged with a dwarf multicannon at dense troll spawns for fast completion"
		);
		final SlayerTaskStrategy noCannon = efficientMelee(
			"Use prayer melee against the selected troll spawn when cannon is disabled",
			true
		);
		return Profiles.same(cannon)
			.withCombatOptions(noCannon, cannon, null)
			.withCannonOptions(cannon, noCannon);
	}

	private static Profiles dragonRangedProfiles(final boolean boss)
	{
		final SlayerTaskStrategy ranged = shieldedDragonRanged(
			"Use a dragon-hunter ranged weapon with full dragonfire protection",
			boss
		);
		final SlayerTaskStrategy melee = dragonStab(
			"Use Dragon hunter lance or another strong stab weapon with a defender and super antifire protection",
			boss
		);
		return new Profiles(ranged, ranged, melee, ranged)
			.withCombatOptions(melee, ranged, null);
	}

	private static Profiles blackDragonProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot ordinary Black dragons with a one-handed crossbow, a dragonfire shield, and antifire protection"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter crossbow", "zaryte crossbow", "armadyl crossbow",
				"hunters sunlight crossbow", "dragon crossbow", "rune crossbow"
			)
			.prayerPotionSlots(0)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.reviewed("2026-08-16")
			.build();
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use strong stab melee with a defender and super antifire protection"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter lance", "osmumten s fang", "ghrazi rapier",
				"zamorakian hasta", "abyssal dagger"
			)
			.prayerPotionSlots(0)
			.strictWeaponProfile(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	private static Profiles blueDragonProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot Blue dragons with a one-handed crossbow and full dragonfire protection"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"The reviewed Taverley setup keeps a dragonfire-protection shield equipped "
					+ "and uses the task-only upper area or another safespot. One-handed "
					+ "crossbows avoid the invalid two-handed-weapon plus shield layout."
			)
			.weapons(
				"dragon hunter crossbow",
				"zaryte crossbow",
				"armadyl crossbow",
				"hunters sunlight crossbow",
				"dragon crossbow",
				"rune crossbow"
			)
			.prayerPotionSlots(0)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.reviewed("2026-08-14")
			.build();

		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use Dragon hunter lance or another strong stab weapon with a defender and super antifire protection"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"The melee alternative uses a one-handed stab weapon, the best owned "
					+ "defender, and full potion-only dragonfire protection. Automatic "
					+ "keeps the safer ranged safespot as its default."
			)
			.weapons(
				"dragon hunter lance",
				"osmumten s fang",
				"ghrazi rapier",
				"zamorakian hasta",
				"abyssal dagger"
			)
			.prayerPotionSlots(0)
			.strictWeaponProfile(true)
			.reviewed("2026-08-14")
			.build();

		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Safespot Blue dragons with Water Blast and dragonfire protection"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Regular Blue dragons have a 50% Water weakness. The reviewed Magic "
					+ "package keeps a one-handed autocast weapon and anti-dragon shield "
					+ "together, and carries the exact Standard-spellbook runes in a pouch."
			)
			.weapons(
				"dragon hunter wand",
				"kodai wand",
				"nightmare staff",
				"master wand",
				"ancient staff",
				"mystic water staff",
				"water battlestaff",
				"steam battlestaff",
				"mud battlestaff"
			)
			.runePouch(true)
			.prayerPotionSlots(0)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.reviewed("2026-08-14")
			.build();

		return new Profiles(ranged, ranged, ranged, ranged)
			.withCombatOptions(melee, ranged, magic);
	}

	private static Profiles vorkathProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use a one-handed dragonbane crossbow, dragonfire shield protection, and Crumble Undead"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"The reviewed default follows the Vorkath crossbow setup: Dragon hunter "
					+ "crossbow where owned, an anti-dragon or dragonfire ward, extended "
					+ "super antifire, venom protection, and a rune pouch for Crumble Undead."
			)
			.weapons(
				"dragon hunter crossbow",
				"zaryte crossbow",
				"dragon crossbow",
				"armadyl crossbow",
				"rune crossbow"
			)
			.optionalItems("toxic blowpipe", "zaryte crossbow")
			.runePouch(true)
			.prayerPotionSlots(4)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-14")
			.build();

		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use Dragon hunter lance or Osmumten's fang with super antifire protection and Crumble Undead"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"dragon hunter lance",
				"osmumten s fang",
				"ghrazi rapier",
				"noxious halberd",
				"zamorakian hasta",
				"abyssal dagger"
			)
			.optionalItems(
				"burning claws", "voidwaker", "dragon claws",
				"elder maul", "bandos godsword", "dragon warhammer",
				"slayer s staff e", "slayer s staff"
			)
			.runePouch(true)
			.prayerPotionSlots(2)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-14")
			.build();

		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, null);
	}

	private static Profiles metalDragonProfiles()
	{
		final SlayerTaskStrategy ranged = shieldedDragonRanged(
			"Use a one-handed dragonbane crossbow with a dragonfire shield and antifire protection",
			false
		);
		final SlayerTaskStrategy melee = dragonStab(
			"Use Dragon hunter lance or a high-accuracy stab weapon with a defender and super antifire protection",
			false
		);
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Cast Earth Wave against the metal dragon's 50% Earth weakness while using a dragonfire shield and antifire protection"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter wand", "harmonised nightmare staff", "nightmare staff",
				"kodai wand", "master wand", "ancient staff",
				"mystic earth staff", "earth battlestaff", "staff of earth"
			)
			.runePouch(true)
			.prayerPotionSlots(3)
			.strictWeaponProfile(true)
			.reviewed("2026-08-16")
			.build();
		return new Profiles(magic, magic, melee, ranged)
			.withCombatOptions(melee, ranged, magic);
	}

	private static Profiles frostDragonProfiles()
	{
		final SlayerTaskStrategy ranged = dragonRanged(
			"Use heavy/dragon-hunter ranged with full dragonfire protection for safe, consistent Frost dragon kills",
			false
		);
		final SlayerTaskStrategy fast = elementalMagic(
			"Use high-level Fire spells for the highest sustained DPS while keeping full dragonfire protection"
		);
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use a Dragon hunter lance on Crush (or another strong crush weapon) with prayer and antifire protection"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Frost dragons are crush-weak, but the practical post-launch farming setup still commonly uses the Dragon hunter lance because its dragonbane bonus remains extremely strong. "
					+ "Use the lance on Crush rather than incorrectly treating the task as a stab-weak dragon."
			)
			.weapons(
				"dragon hunter lance",
				"inquisitor s mace",
				"abyssal bludgeon",
				"dual macuahuitl",
				"zombie axe"
			)
			.prayerPotionSlots(4)
			.strictWeaponProfile(true)
			.reviewed("2026-08-11")
			.build();
		/*
		 * Post-launch research: Fire Magic has the highest consistent DPS and stays
		 * the Fast XP choice despite its rune cost. Long-trip/profit play is commonly
		 * melee, where Dragon hunter lance on Crush is extremely competitive at much
		 * lower operating cost. Ranged remains a viable explicit preference rather
		 * than an Automatic compromise.
		 */
		return new Profiles(melee, fast, melee, melee)
			.withCombatOptions(melee, ranged, fast);
	}

	private static Profiles wyvernProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use one-handed slash or crush melee with a wyvern-protection shield and Protect from Melee"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Skeletal Wyverns have much lower slash and crush defence than stab defence. "
					+ "Dragon hunter lance remains first when used on Swipe or Pound; every "
					+ "fallback is one-handed so the icy-breath shield is never displaced."
			)
			.weapons(
				"dragon hunter lance", "blade of saeldor", "abyssal tentacle",
				"abyssal whip", "inquisitor s mace", "sarachnis cudgel",
				"zombie axe", "dragon scimitar", "dragon mace"
			)
			.runePouch(true)
			.prayerPotionSlots(4)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.reviewed("2026-08-17")
			.build();

		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot with a one-handed crossbow, enchanted dragonstone bolts, and a wyvern-protection shield"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Crossbows outrange the icy breath after the Wyvern is safely trapped. "
					+ "Keep the protection shield available while establishing the safespot; "
					+ "dragonstone bolt effects work on Skeletal Wyverns."
			)
			.weapons(
				"dragon hunter crossbow", "zaryte crossbow", "armadyl crossbow",
				"dragon crossbow", "rune crossbow"
			)
			.runePouch(true)
			.prayerPotionSlots(2)
			.foodSlots(6)
			.strictWeaponProfile(true)
			.reviewed("2026-08-17")
			.build();

		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Safespot with the best Fire spell for the 25% weakness and a wyvern-protection shield"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter wand", "harmonised nightmare staff", "nightmare staff",
				"smoke battlestaff", "staff of the dead", "mystic fire staff",
				"fire battlestaff", "staff of fire"
			)
			.runePouch(true)
			.prayerPotionSlots(2)
			.foodSlots(6)
			.strictWeaponProfile(true)
			.reviewed("2026-08-17")
			.build();

		return new Profiles(melee, melee, ranged, ranged)
			.withCombatOptions(melee, ranged, magic);
	}

	private static Profiles aquaniteProfiles()
	{
		final SlayerTaskStrategy base = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Sever the lure with a fast slash hit, then use stab melee with Protect from Magic; ranged safespotting is the low-risk alternative"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"ghrazi rapier",
				"osmumten s fang",
				"blade of saeldor",
				"zamorakian hasta",
				"abyssal dagger",
				"belle s folly",
				"dragon sword"
			)
			.optionalItems(
				"saradomin godsword", "dragon claws", "voidwaker",
				"abyssal whip", "blade of saeldor", "dragon scimitar",
				"rune scimitar", "seed box", "teleport to house"
			)
			.runePouch(true)
			.prayerPotionSlots(5)
			.foodSlots(4)
			.strictWeaponProfile(true)
			.reviewed("2026-08-16")
			.build();
		final SlayerTaskStrategy afk = rangedPrayer(
			"Safespot with a long-range bow or crossbow for a low-attention trip"
		);
		return new Profiles(base, base, afk, base);
	}

	private static Profiles amoxliatlProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use multi-hitsplat crush melee, step back between attacks, and destroy unstable ice with melee"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"scythe of vitur", "abyssal bludgeon", "dual macuahuitl",
				"ursine chainmace", "zamorakian hasta", "inquisitor s mace",
				"sarachnis cudgel", "glacial temotli", "ghrazi rapier",
				"saradomin sword", "zombie axe", "dragon mace"
			)
			.optionalItems(
				"burning claws", "dragon claws", "dragon dagger",
				"ectoplasmator", "pendant of ates", "teleport to house"
			)
			.runePouch(true)
			.prayerPotionSlots(6)
			.foodSlots(6)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(melee);
	}

	private static Profiles naguaProfiles()
	{
		final SlayerTaskStrategy base = stab(
			"Use the Earthbound Tecpatl or another fast stab weapon",
			false,
			"earthbound tecpatl",
			"osmumten s fang",
			"ghrazi rapier",
			"zamorakian hasta",
			"abyssal dagger"
		);
		return Profiles.same(base);
	}

	private static Profiles crushTaskProfiles(
		final String method,
		final boolean antivenom)
	{
		final SlayerTaskStrategy base = crush(method, antivenom, false);
		return Profiles.same(base);
	}

	private static Profiles gargoyleProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Crush melee with a rock hammer or automatic finisher"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"granite hammer",
				"inquisitor s mace",
				"abyssal bludgeon",
				"dual macuahuitl",
				"sarachnis cudgel",
				"zombie axe"
			)
			.tags(SlayerTaskStrategy.MethodTag.REQUIRED_SPECIAL_GEAR)
			.optionalItems("guthans warspear", "high alchemy rune pouch")
			.prayerPotionSlots(4)
			.strictWeaponProfile(true)
			.build();

		final SlayerTaskStrategy magic = earthMagic(
			"Highest practical Earth spell with a rock hammer or automatic finisher"
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE)
			.withSelectionNote(
			"Magic preference uses the reviewed Earth-weakness alternative."
		);

		return Profiles.same(melee)
			.withCombatOptions(melee, null, magic);
	}

	private static Profiles basiliskProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot with a one-handed Ranged weapon and keep a Mirror shield or V's shield equipped"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"zaryte crossbow",
				"dragon knife",
				"amethyst dart",
				"hunters sunlight crossbow",
				"dragon hunter crossbow",
				"dragon crossbow",
				"armadyl crossbow",
				"rune crossbow"
			)
			.prayerPotionSlots(6)
			.strictWeaponProfile(true)
			.reviewed("2026-08-16")
			.build();
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use fast crush melee with Protect from Magic and a Mirror shield or V's shield"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"inquisitor s mace", "zamorakian hasta", "sarachnis cudgel",
				"ursine chainmace", "dragon mace"
			)
			.prayerPotionSlots(6)
			.strictWeaponProfile(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	private static Profiles bansheeProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Wear a Slayer helmet or earmuffs, use a Salve amulet when available, and use fast charge-free melee"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"ghrazi rapier", "blade of saeldor", "abyssal tentacle",
				"abyssal whip", "zombie axe", "dragon scimitar",
				"rune scimitar", "adamant scimitar"
			)
			.strictWeaponProfile(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(melee);
	}

	private static Profiles batsProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use a fast one-target melee weapon; do not spend Scythe charges on 1 x 1 bats"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"ghrazi rapier", "abyssal whip", "dragon sword",
				"dragon scimitar", "leaf bladed sword", "rune scimitar",
				"adamant scimitar", "black scimitar", "mithril scimitar",
				"steel scimitar", "iron scimitar", "bronze scimitar"
			)
			.strictWeaponProfile(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(melee);
	}

	private static Profiles barrowsProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use the best owned Air spell against every brother, with a Ranged switch for Ahrim and melee for tunnel reward potential"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.MAGIC_DEFENCE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"tumeken s shadow", "eye of ayak", "harmonised nightmare staff", "smoke battlestaff",
				"staff of the dead", "nightmare staff", "kodai wand", "master wand",
				"mystic air staff", "air battlestaff", "staff of air"
			)
			.runePouch(true)
			.prayerPotionSlots(3)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(magic);
	}

	private static Profiles leafBladedProfiles(final String method)
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"leaf bladed battleaxe",
				"leaf bladed sword",
				"leaf bladed spear"
			)
			.tags(SlayerTaskStrategy.MethodTag.REQUIRED_SPECIAL_GEAR)
			.strictWeaponProfile(true)
			.prayerPotionSlots(3)
			.build();

		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Crossbow or bow with broad ammunition"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter crossbow",
				"armadyl crossbow",
				"dragon crossbow",
				"rune crossbow",
				"magic shortbow"
			)
			.tags(SlayerTaskStrategy.MethodTag.REQUIRED_SPECIAL_GEAR)
			.strictWeaponProfile(true)
			.prayerPotionSlots(3)
			.build();

		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Magic Dart with a compatible Slayer staff"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"slayer s staff e",
				"slayer s staff",
				"toxic staff of the dead",
				"staff of the dead"
			)
			.tags(SlayerTaskStrategy.MethodTag.REQUIRED_SPECIAL_GEAR)
			.runePouch(true)
			.strictWeaponProfile(true)
			.prayerPotionSlots(3)
			.build();

		return Profiles.same(melee)
			.withCombatOptions(melee, ranged, magic);
	}

	private static Profiles demonbaneProfiles(final String method)
	{
		final SlayerTaskStrategy base = demonbane(method);
		final SlayerTaskStrategy afk = efficientMelee(method, true);
		return new Profiles(base, base, afk, base);
	}

	private static Profiles greaterDemonProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use Emberlight or Arclight with an offensive melee setup; add a cannon only at a cannonable location"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons("emberlight", "arclight", "silverlight", "abyssal tentacle", "abyssal whip")
			.prayerPotionSlots(2)
			.build();
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot Greater demons with the best owned ranged weapon; use a cannon only where the selected area permits it"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"scorching bow", "venator bow", "toxic blowpipe",
				"bow of faerdhinen", "magic shortbow i", "rune crossbow"
			)
			.zeroDamageWhileSafespotted()
			.build();
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Safespot with the best Water spell to exploit the Greater demon's 40% Water weakness"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(elementalStaffPriorities("water"))
			.runePouch(true)
			.zeroDamageWhileSafespotted()
			.build();
		return Profiles.same(melee).withCombatOptions(melee, ranged, magic);
	}

	private static Profiles greaterDemonSingleCombatCannonProfiles()
	{
		final Profiles base = greaterDemonProfiles();
		final SlayerTaskStrategy cannonSafespot = base.ranged
			.withAdditionalTags(
				SlayerTaskStrategy.MethodTag.CANNON,
				SlayerTaskStrategy.MethodTag.SAFESPOT
			)
			.withSelectionNote(
				"Stay in the reviewed safespot so no Greater demon can attack you; in single-combat this is what lets the cannon continue firing at multiple demons."
			);
		return Profiles.same(base.melee)
			.withCombatOptions(base.melee, cannonSafespot, base.magic)
			.withCannonOptions(cannonSafespot, base.melee);
	}

	private static Profiles burstableDemonProfiles()
	{
		final SlayerTaskStrategy barrage = barrage(
			"Stack and burst or barrage Abyssal demons in the Catacombs"
		);
		final SlayerTaskStrategy afk = venator(
			"Use a Venator bow in Catacombs multi-combat for a lower-attention task"
		);
		final SlayerTaskStrategy melee = demonbane(
			"Use efficient demonbane melee and bank valuable drops"
		);
		return new Profiles(barrage, barrage, afk, melee)
			.withCombatOptions(melee, afk, barrage)
			.withBurstOptions(barrage, melee);
	}

	private static Profiles abyssalDemonSingleCombatProfiles()
	{
		final SlayerTaskStrategy melee = demonbane(
			"Efficient demonbane melee in the Slayer Tower"
		);
		final SlayerTaskStrategy ranged = blowpipe(
			"Fast single-target ranged in the Slayer Tower",
			true
		);
		final SlayerTaskStrategy magic = poweredMagic(
			"Powered Magic in the Slayer Tower; burst stacking is unavailable"
		);
		return Profiles.same(melee)
			.withCombatOptions(melee, ranged, magic)
			.withBurstOptions(null, melee);
	}

	private static Profiles burstableRangedProfiles(final String task)
	{
		final SlayerTaskStrategy barrage = barrage(
			"Stack and burst or barrage " + task
		);
		final SlayerTaskStrategy afk = venator(
			"Use a Venator bow for a lower-attention multi-combat setup"
		);
		return new Profiles(barrage, barrage, afk, barrage)
			.withCombatOptions(null, afk, barrage)
			.withBurstOptions(barrage, afk);
	}

	private static Profiles ankouCatacombsProfiles()
	{
		final SlayerTaskStrategy barrage = barrage(
			"Stack and burst or barrage Ankous in the Catacombs of Kourend"
		).withSelectionNote(
			"Current Wiki default: the Catacombs pack is multicombat and has the dungeon drop table."
		);
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use a Venator bow on the tightly packed Catacombs Ankous; use a Toxic blowpipe when the bow is not owned"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Venator arrows can bounce through this 1x1 multicombat pack. Toxic blowpipe is the fast single-target fallback; neither is recommended in place of barrage when burst/barrage is explicitly preferred."
			)
			.weapons(
				"venator bow", "toxic blowpipe", "bow of faerdhinen",
				"hunters sunlight crossbow", "magic shortbow i", "magic shortbow"
			)
			.optionalItems(
				"ectoplasmator", "bonecrusher", "goading potion",
				"bracelet of slaughter"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.VENATOR,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(5)
			.strictWeaponProfile(true)
			.reviewed("2026-08-17")
			.build();
		final SlayerTaskStrategy melee = efficientMelee(
			"Protect from Melee in the Catacombs when Melee is explicitly selected",
			true
		);
		/* Barrage remains available when explicitly preferred. Automatic/Allow
		 * uses Venator because an unstacked Ankou barrage trip requires active
		 * corner-luring that the plugin cannot perform for the player. */
		return new Profiles(ranged, ranged, ranged, ranged)
			.withCombatOptions(melee, ranged, barrage)
			.withBurstOptions(barrage, ranged);
	}

	private static Profiles ankouCannonProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Cannon Ankous in the Stronghold Slayer Cave while using fast Ranged attacks"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"This room is single-combat, so Venator bounces and barrage stacking are unavailable; the cannon plus Toxic blowpipe is the reviewed fast alternative."
			)
			.weapons(
				"toxic blowpipe", "bow of faerdhinen", "hunters sunlight crossbow",
				"magic shortbow i", "magic shortbow", "rune crossbow"
			)
			.optionalItems("ectoplasmator", "bracelet of slaughter")
			.tags(SlayerTaskStrategy.MethodTag.CANNON)
			.prayerPotionSlots(3)
			.strictWeaponProfile(true)
			.reviewed("2026-08-17")
			.build();
		final SlayerTaskStrategy melee = efficientMelee(
			"Cannon Ankous while using efficient melee",
			true
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.CANNON);
		final SlayerTaskStrategy noCannon = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use fast Ranged attacks with Protect from Melee in the Stronghold Slayer Cave"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"The room remains usable without a cannon, but it is single-combat and cannot use Venator bounces or barrage stacking."
			)
			.weapons(
				"toxic blowpipe", "bow of faerdhinen", "hunters sunlight crossbow",
				"magic shortbow i", "magic shortbow", "rune crossbow"
			)
			.optionalItems("ectoplasmator", "bracelet of slaughter")
			.prayerPotionSlots(5)
			.strictWeaponProfile(true)
			.reviewed("2026-08-17")
			.build();
		final SlayerTaskStrategy magic =
			SlayerElementalWeaknessCatalog.preferenceStrategy("Ankou", false);
		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, magic)
			.withBurstOptions(null, ranged)
			.withCannonOptions(ranged, noCannon);
	}

	private static Profiles ankouSafespotProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot Ankous in the Sepulchre of Death with fast Ranged attacks"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"The Stronghold of Security is a safe, supply-light fallback, but its single-combat room is slower than Catacombs barrage or Venator bow and the cannonable Slayer Cave."
			)
			.weapons(
				"toxic blowpipe", "bow of faerdhinen", "hunters sunlight crossbow",
				"magic shortbow i", "magic shortbow", "rune crossbow"
			)
			.optionalItems("ectoplasmator", "bracelet of slaughter")
			.tags(SlayerTaskStrategy.MethodTag.SAFESPOT)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.reviewed("2026-08-17")
			.build();
		final SlayerTaskStrategy melee = efficientMelee(
			"Use efficient melee in the Sepulchre of Death",
			false
		);
		final SlayerTaskStrategy magic =
			SlayerElementalWeaknessCatalog.preferenceStrategy("Ankou", false);
		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, magic)
			.withBurstOptions(null, ranged);
	}

	private static Profiles barrageProfiles(final String method)
	{
		final SlayerTaskStrategy magic = barrage(method);
		final SlayerTaskStrategy ranged = blowpipe(
			"Fast ranged fallback because burst and barrage are disabled",
			true
		);
		final SlayerTaskStrategy melee = efficientMelee(
			"Prayer melee fallback because burst and barrage are disabled",
			true
		);
		return Profiles.same(magic)
			.withCombatOptions(melee, ranged, magic)
			.withBurstOptions(magic, ranged);
	}

	private static Profiles smokeDevilProfiles()
	{
		final SlayerTaskStrategy noCannonMagic = smokeDevilBarrage(
			"Ancient Magicks Ice Barrage (Ice Burst below 94 Magic) without a cannon",
			false
		);
		final SlayerTaskStrategy magic = smokeDevilBarrage(
			"Ancient Magicks Ice Barrage (Ice Burst below 94 Magic) with a dwarf multicannon",
			true
		);
		final SlayerTaskStrategy ranged = smokeDevilRanged(
			"Cannon with fast ranged attacks; burst and barrage are disabled",
			true
		);
		final SlayerTaskStrategy rangedNoCannon = smokeDevilRanged(
			"Fast ranged attacks without a cannon; burst and barrage are disabled",
			false
		);
		return Profiles.same(magic)
			.withCombatOptions(null, rangedNoCannon, magic)
			.withBurstOptions(magic, rangedNoCannon)
			.withCannonOptions(magic, noCannonMagic);
	}

	private static SlayerTaskStrategy smokeDevilBarrage(
		final String method,
		final boolean cannon)
	{
		final SlayerTaskStrategy.Builder builder = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"Smoke devils use magical ranged attacks, and Protect from Missiles fully negates them. "
					+ "The reviewed multi-combat method therefore uses prayer supplies instead of food, "
					+ "with a cannon to gather the room and a remains pile to stack targets."
			)
			.weapons(
				"kodai wand",
				"nightmare staff",
				"ancient sceptre",
				"master wand",
				"staff of the dead"
			)
			.runePouch(true)
			.tags(
				SlayerTaskStrategy.MethodTag.BURST_BARRAGE,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(6)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true)
			.selectionNote(
				"Switch to Ancient Magicks. Load Water, Blood, and Death runes for Ice Barrage "
					+ "(Water, Chaos, and Death for Ice Burst), wear a Slayer helmet or facemask, "
					+ "keep Protect from Missiles active, and do not reserve inventory slots for food."
			);

		if (cannon)
		{
			builder.tags(SlayerTaskStrategy.MethodTag.CANNON);
		}

		return builder.build();
	}

	private static SlayerTaskStrategy smokeDevilRanged(
		final String method,
		final boolean cannon)
	{
		final SlayerTaskStrategy.Builder builder = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Protect from Missiles fully negates regular Smoke devil attacks, so this reviewed "
					+ "alternative carries prayer supplies rather than food."
			)
			.weapons(
				"toxic blowpipe",
				"bow of faerdhinen",
				"crystal bow",
				"magic shortbow"
			)
			.prayerPotionSlots(6)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true)
			.selectionNote(
				"Wear a Slayer helmet or facemask, keep Protect from Missiles active, "
					+ "and do not reserve inventory slots for food."
			);

		if (cannon)
		{
			builder.tags(SlayerTaskStrategy.MethodTag.CANNON);
		}

		return builder.build();
	}

	private static Profiles hellhoundProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Toxic blowpipe or demonbane ranged with Protect from Melee"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"This is the reviewed single-target regular-Hellhound profile. "
					+ "Toxic blowpipe is the first low-defence target option; "
					+ "Scorching bow is the first demonbane alternative and must "
					+ "use arrows. The ordered list is explicit so Automatic never "
					+ "promotes an arbitrary same-style bank item as researched best."
			)
			.weapons(
				"toxic blowpipe",
				"scorching bow",
				"bow of faerdhinen",
				"hunters sunlight crossbow",
				"crystal bow",
				"magic shortbow",
				"dragon crossbow",
				"rune crossbow"
			)
			.optionalItems(
				"ash sanctifier",
				"bracelet of slaughter",
				"expeditious bracelet"
			)
			.prayerPotionSlots(4)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true)
			.reviewed("2026-08-09")
			.build();

		final SlayerTaskStrategy afkRanged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Venator bow with prayer gear in the Catacombs"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"The Catacombs are multi-combat and Hellhounds are aggressive, "
					+ "so Venator bow ricochets improve both task speed and attention time."
			)
			.weapons(
				"venator bow",
				"toxic blowpipe",
				"scorching bow",
				"bow of faerdhinen"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.VENATOR,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT,
				SlayerTaskStrategy.MethodTag.AUTOMATIC_STYLE_LOCKED
			)
			.optionalItems("ash sanctifier", "bracelet of slaughter")
			.prayerPotionSlots(6)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true)
			.reviewed("2026-08-09")
			.build();

		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Demonbane melee with Protect from Melee"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Melee is a reviewed viable preference for ordinary "
					+ "Hellhounds. Fast XP may use Scythe-class damage without "
					+ "cost weighting, while Profit explicitly prioritizes "
					+ "Emberlight or Arclight with a defender before charged "
					+ "weapons. Protect from Melee prevents their only attack style."
			)
			.weapons(
				"scythe of vitur",
				"emberlight",
				"arclight",
				"blade of saeldor",
				"ghrazi rapier",
				"abyssal whip"
			)
			.maxDpsWeapons(
				"scythe of vitur",
				"emberlight",
				"arclight",
				"blade of saeldor",
				"ghrazi rapier",
				"abyssal whip"
			)
			.efficientWeapons(
				"emberlight",
				"arclight",
				"blade of saeldor",
				"ghrazi rapier",
				"abyssal whip"
			)
			.optionalItems("ash sanctifier", "bracelet of slaughter")
			.prayerPotionSlots(5)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true)
			.reviewed("2026-08-09")
			.build();

		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Highest available Water spell"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Hellhounds have a 50% Water elemental weakness and "
					+ "negligible Magic defence, so Water spells are a "
					+ "verified viable preference. Protect from Melee "
					+ "prevents their only attack style."
			)
			.weapons(
				"kodai wand",
				"nightmare staff",
				"staff of the dead",
				"mist battlestaff",
				"water battlestaff"
			)
			.optionalItems("tome of water", "ash sanctifier")
			.runePouch(true)
			.tags(SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE)
			.prayerPotionSlots(3)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true)
			.reviewed("2026-08-09")
			.build();

		return new Profiles(ranged, ranged, afkRanged, ranged)
			.withCombatOptions(melee, ranged, magic);
	}

	private static Profiles hellhoundCatacombsProfiles()
	{
		final Profiles base = hellhoundProfiles();
		final SlayerTaskStrategy venator = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Venator bow in Catacombs multi-combat with Protect from Melee"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"The selected location is multi-combat. Venator bow is ranked "
					+ "ahead of single-target weapons because its ricochets damage "
					+ "multiple aggressive Hellhounds and increase total task kills per hour."
			)
			.weapons(
				"venator bow",
				"toxic blowpipe",
				"scorching bow",
				"bow of faerdhinen",
				"hunters sunlight crossbow",
				"crystal bow",
				"magic shortbow",
				"dragon crossbow",
				"rune crossbow"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.VENATOR,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT,
				SlayerTaskStrategy.MethodTag.AUTOMATIC_STYLE_LOCKED
			)
			.optionalItems(
				"ash sanctifier",
				"bracelet of slaughter",
				"expeditious bracelet"
			)
			.prayerPotionSlots(5)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true)
			.reviewed("2026-08-09")
			.build();

		return new Profiles(venator, venator, venator, base.profit)
			.withCombatOptions(base.melee, venator, base.magic);
	}

	private static Profiles hellhoundCannonProfiles()
	{
		final Profiles base = hellhoundProfiles();
		final SlayerTaskStrategy cannonRanged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot Ranged with a dwarf multicannon"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"At the Stronghold Slayer Cave, the reviewed fast method is to stand "
					+ "in the Hellhound safespot and attack with Ranged while the dwarf "
					+ "multicannon continues engaging nearby Hellhounds. The method-level "
					+ "safespot and cannon advantage outweighs switching to Melee merely "
					+ "because the account owns a stronger melee weapon."
			)
			.weapons(
				"toxic blowpipe",
				"scorching bow",
				"bow of faerdhinen",
				"hunters sunlight crossbow",
				"crystal bow",
				"magic shortbow",
				"dragon crossbow",
				"rune crossbow"
			)
			.maxDpsWeapons(
				"toxic blowpipe",
				"scorching bow",
				"bow of faerdhinen",
				"hunters sunlight crossbow",
				"crystal bow",
				"magic shortbow",
				"dragon crossbow",
				"rune crossbow"
			)
			.efficientWeapons(
				"toxic blowpipe",
				"scorching bow",
				"hunters sunlight crossbow",
				"magic shortbow",
				"rune crossbow",
				"dragon crossbow",
				"bow of faerdhinen",
				"crystal bow"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.CANNON,
				SlayerTaskStrategy.MethodTag.SAFESPOT,
				SlayerTaskStrategy.MethodTag.AUTOMATIC_STYLE_LOCKED
			)
			.optionalItems(
				"ash sanctifier",
				"bracelet of slaughter",
				"expeditious bracelet"
			)
			.prayerPotionSlots(0)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.reviewed("2026-08-09")
			.build();


		final SlayerTaskStrategy safespotRanged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Ranged from the Stronghold Hellhound safespot"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"The Stronghold safespot still defines the method when cannon use is "
					+ "disabled or when Profit avoids cannonball cost. Profit keeps the "
					+ "Toxic blowpipe as the preferred single-target weapon when owned, "
					+ "then reduces operating cost through economical darts rather than "
					+ "downgrading the entire weapon."
			)
			.weapons(
				"toxic blowpipe",
				"scorching bow",
				"bow of faerdhinen",
				"hunters sunlight crossbow",
				"crystal bow",
				"magic shortbow",
				"dragon crossbow",
				"rune crossbow"
			)
			.maxDpsWeapons(
				"toxic blowpipe",
				"scorching bow",
				"bow of faerdhinen",
				"hunters sunlight crossbow",
				"crystal bow",
				"magic shortbow",
				"dragon crossbow",
				"rune crossbow"
			)
			.efficientWeapons(
				"toxic blowpipe",
				"scorching bow",
				"hunters sunlight crossbow",
				"magic shortbow",
				"rune crossbow",
				"dragon crossbow",
				"bow of faerdhinen",
				"crystal bow"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.SAFESPOT,
				SlayerTaskStrategy.MethodTag.AUTOMATIC_STYLE_LOCKED
			)
			.optionalItems(
				"ash sanctifier",
				"bracelet of slaughter",
				"expeditious bracelet"
			)
			.prayerPotionSlots(0)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.reviewed("2026-08-09")
			.build();

		final SlayerTaskStrategy cannonMelee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Scythe or demonbane melee with a dwarf multicannon"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"This remains available only when the player explicitly prefers Melee. "
					+ "Automatic does not select it at the Stronghold Slayer Cave because "
					+ "meleeing gives up the reviewed Ranged safespot method."
			)
			.weapons(
				"scythe of vitur",
				"emberlight",
				"arclight",
				"blade of saeldor",
				"ghrazi rapier",
				"abyssal whip"
			)
			.maxDpsWeapons(
				"scythe of vitur",
				"emberlight",
				"arclight",
				"blade of saeldor",
				"ghrazi rapier",
				"abyssal whip"
			)
			.efficientWeapons(
				"emberlight",
				"arclight",
				"blade of saeldor",
				"ghrazi rapier",
				"abyssal whip"
			)
			.tags(SlayerTaskStrategy.MethodTag.CANNON)
			.optionalItems(
				"ash sanctifier",
				"bracelet of slaughter",
				"expeditious bracelet"
			)
			.prayerPotionSlots(4)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true)
			.reviewed("2026-08-09")
			.build();

		return new Profiles(
			cannonRanged,
			cannonRanged,
			safespotRanged,
			safespotRanged
		)
			.withCombatOptions(cannonMelee, cannonRanged, base.magic)
			.withCannonOptions(cannonRanged, safespotRanged);
	}

	private static Profiles cerberusProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Demonbane or high-DPS melee with Protect from Magic and prayer switches"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"Cerberus is a separate boss encounter. Automatic uses the reviewed melee setup rather than the ordinary-Hellhound blowpipe loadout."
			)
			.weapons(
				"scythe of vitur",
				"emberlight",
				"arclight",
				"inquisitor s mace",
				"osmumten s fang",
				"abyssal bludgeon"
			)
			.optionalItems(
				"spectral spirit shield",
				"key master teleport",
				"dragon claws",
				"burning claws"
			)
			.prayerPotionSlots(8)
			.foodSlots(8)
			.fillRemainingInventoryWithFood()
			.strictWeaponProfile(true)
			.boss(true)
			.build();

		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Twisted bow or Scorching bow ranged Cerberus"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"Ranged is a reviewed Cerberus alternative. Scorching bow uses arrows; it must never be paired with javelins."
			)
			.weapons(
				"twisted bow",
				"scorching bow",
				"bow of faerdhinen",
				"toxic blowpipe"
			)
			.optionalItems(
				"spectral spirit shield",
				"key master teleport"
			)
			.prayerPotionSlots(8)
			.foodSlots(8)
			.fillRemainingInventoryWithFood()
			.strictWeaponProfile(true)
			.boss(true)
			.build();

		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Highest available Water spell with full Cerberus prayer mechanics"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"Water Magic is retained only as an explicit viable preference; Automatic remains melee."
			)
			.weapons(
				"harmonised nightmare staff",
				"kodai wand",
				"nightmare staff",
				"mist battlestaff",
				"water battlestaff"
			)
			.optionalItems(
				"spectral spirit shield",
				"key master teleport"
			)
			.runePouch(true)
			.tags(SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE)
			.prayerPotionSlots(8)
			.foodSlots(8)
			.fillRemainingInventoryWithFood()
			.strictWeaponProfile(true)
			.boss(true)
			.build();

		return new Profiles(melee, melee, ranged, melee)
			.withCombatOptions(melee, ranged, magic);
	}

	private static SlayerTaskStrategy hellhoundWilderness()
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Low-risk Wilderness ranged setup"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.rationale(
				"Use replaceable ranged gear, Protect from Melee, and only supplies you are willing to risk."
			)
			.weapons(
				"webweaver bow",
				"craw s bow",
				"toxic blowpipe",
				"magic shortbow"
			)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(3)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.reviewed("2026-08-08")
			.build();
	}

	private static SlayerTaskStrategy unreviewed(final String taskName)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Research pending — loadout disabled"
		)
			.rationale(
				"" + (taskName == null ? "This task" : taskName)
					+ " has not yet passed individual strategy review. SlayerPlus will not invent a broad equipment template."
			)
			.prayerPotionSlots(0)
			.foodSlots(0)
			.strictWeaponProfile(true)
			.build();
	}

	private static SlayerTaskStrategy unreviewedWilderness(
		final String taskName)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Wilderness research pending — loadout disabled"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.rationale(
				(taskName == null ? "This task" : taskName)
					+ " has no individually reviewed Krystilia profile. "
					+ "The normal-task profile is never reused in the Wilderness."
			)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(0)
			.foodSlots(0)
			.strictWeaponProfile(true)
			.build();
	}

	private static Profiles aberrantSpectreProfiles()
	{
		final SlayerTaskStrategy melee = efficientMelee(
			"Protect from Magic with efficient melee and a nose peg or Slayer helmet",
			true
		).withSelectionNote("Required face protection is enforced by the loadout analyzer.");
		final SlayerTaskStrategy ranged = blowpipe(
			"Protect from Magic with fast ranged attacks and required face protection",
			true
		);
		return Profiles.same(melee)
			.withCombatOptions(melee, ranged, null);
	}

	private static Profiles aberrantSpectreCannonProfiles()
	{
		final SlayerTaskStrategy ranged = cannonBlowpipe(
			"Cannon Aberrant spectres in the Stronghold Slayer Cave while using Protect from Magic"
		);
		final SlayerTaskStrategy melee = efficientMelee(
			"Melee with a cannon and Protect from Magic in the Stronghold Slayer Cave",
			true
		);
		final SlayerTaskStrategy nonCannon = blowpipe(
			"Fast ranged attacks without a cannon while using Protect from Magic",
			true
		);
		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, null)
			.withCannonOptions(ranged, nonCannon);
	}

	private static Profiles aberrantSpectreCatacombsProfiles()
	{
		final SlayerTaskStrategy ranged = venator(
			"Venator bow against Deviant spectres in Catacombs multi-combat"
		);
		final SlayerTaskStrategy melee = efficientMelee(
			"Prayer melee against Deviant spectres in the Catacombs",
			true
		);
		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, null);
	}

	private static Profiles bloodveldProfiles()
	{
		return bloodveldSingleCannonProfiles();
	}

	private static Profiles bloodveldMultiCannonProfiles()
	{
		final SlayerTaskStrategy ranged = bloodveldVenator(
			"Dwarf multicannon and Venator bow against clustered mutated Bloodvelds",
			true
		);
		final SlayerTaskStrategy melee = bloodveldMelee(
			"Dwarf multicannon and demonbane melee with Protect from Melee",
			true
		);
		final SlayerTaskStrategy nonCannon = bloodveldVenator(
			"Venator bow with Protect from Melee; dwarf multicannon is disabled",
			false
		);
		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, null)
			.withCannonOptions(ranged, nonCannon);
	}

	private static Profiles bloodveldSingleCannonProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot regular or mutated Bloodvelds with fast Ranged attacks and a dwarf multicannon"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"The Stronghold Slayer Cave and Iorwerth Dungeon are single-combat alternatives. "
					+ "A safespot prevents their magic-based melee attack while the cannon accelerates the task."
			)
			.weapons(
				"toxic blowpipe", "bow of faerdhinen", "hunters sunlight crossbow",
				"magic shortbow i", "magic shortbow", "rune crossbow"
			)
			.optionalItems("ash sanctifier", "soul bearer", "bracelet of slaughter")
			.tags(SlayerTaskStrategy.MethodTag.CANNON, SlayerTaskStrategy.MethodTag.SAFESPOT)
			.prayerPotionSlots(0)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.build();
		final SlayerTaskStrategy melee = bloodveldMelee(
			"Demonbane melee with Protect from Melee and a dwarf multicannon",
			true
		);
		final SlayerTaskStrategy nonCannon = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot Bloodvelds with fast Ranged attacks; dwarf multicannon is disabled"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"toxic blowpipe", "bow of faerdhinen", "hunters sunlight crossbow",
				"magic shortbow i", "magic shortbow", "rune crossbow"
			)
			.optionalItems("ash sanctifier", "soul bearer", "bracelet of slaughter")
			.tags(SlayerTaskStrategy.MethodTag.SAFESPOT)
			.prayerPotionSlots(0)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.build();
		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, null)
			.withCannonOptions(ranged, nonCannon);
	}

	private static Profiles bloodveldCatacombsProfiles()
	{
		final SlayerTaskStrategy ranged = bloodveldVenator(
			"Venator bow with a goading potion in Catacombs multi-combat; cannon is not allowed",
			false
		);
		final SlayerTaskStrategy melee = bloodveldMelee(
			"Demonbane melee with Protect from Melee in the Catacombs; cannon is not allowed",
			false
		);
		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, null);
	}

	private static Profiles bloodveldSlayerTowerProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot regular Bloodvelds in the Slayer Tower; cannon is not allowed"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"toxic blowpipe", "bow of faerdhinen", "hunters sunlight crossbow",
				"magic shortbow i", "magic shortbow", "rune crossbow"
			)
			.optionalItems("ash sanctifier", "soul bearer", "bracelet of slaughter")
			.tags(SlayerTaskStrategy.MethodTag.SAFESPOT)
			.prayerPotionSlots(0)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.build();
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Tank regular Bloodvelds in high Magic-defence armour; cannon is not allowed"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.MAGIC_DEFENCE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"emberlight", "soulreaper axe", "arclight", "ghrazi rapier",
				"blade of saeldor", "abyssal tentacle", "abyssal whip", "zombie axe"
			)
			.optionalItems("ash sanctifier", "soul bearer", "bracelet of slaughter")
			.prayerPotionSlots(0)
			.foodSlots(6)
			.strictWeaponProfile(true)
			.build();
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	private static Profiles bloodveldGodWarsProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Safespot God Wars Bloodvelds with Ranged; dwarf multicannon is not allowed"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.MAGIC_DEFENCE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"God Wars Bloodvelds cannot produce the superior variant and share their area with aggressive factions. "
					+ "Carry Saradomin and Zamorak protection; this branch is mainly for an area-locked assignment."
			)
			.weapons(
				"toxic blowpipe", "bow of faerdhinen", "hunters sunlight crossbow",
				"magic shortbow i", "magic shortbow", "rune crossbow"
			)
			.optionalItems("saradomin item", "zamorak item", "ash sanctifier", "bracelet of slaughter")
			.tags(SlayerTaskStrategy.MethodTag.SAFESPOT)
			.prayerPotionSlots(0)
			.zeroDamageWhileSafespotted()
			.strictWeaponProfile(true)
			.build();
		return Profiles.same(ranged);
	}

	private static SlayerTaskStrategy bloodveldVenator(
		final String method,
		final boolean cannon)
	{
		final SlayerTaskStrategy.Builder builder = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Current Slayer training guidance ranks cannon plus Venator bow as the fastest Bloodveld method. "
					+ "Protect from Melee fully prevents the Bloodvelds' magic-based melee attack."
			)
			.weapons(
				"venator bow", "toxic blowpipe", "bow of faerdhinen",
				"hunters sunlight crossbow", "magic shortbow i", "magic shortbow"
			)
			.optionalItems("ash sanctifier", "soul bearer", "bracelet of slaughter")
			.tags(SlayerTaskStrategy.MethodTag.VENATOR, SlayerTaskStrategy.MethodTag.MULTI_COMBAT)
			.prayerPotionSlots(6)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true);
		if (cannon)
		{
			builder.tags(SlayerTaskStrategy.MethodTag.CANNON);
		}
		return builder.build();
	}

	private static SlayerTaskStrategy bloodveldMelee(
		final String method,
		final boolean cannon)
	{
		final SlayerTaskStrategy.Builder builder = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"emberlight", "soulreaper axe", "arclight", "ghrazi rapier",
				"blade of saeldor", "abyssal tentacle", "abyssal whip", "zombie axe"
			)
			.maxDpsWeapons(
				"emberlight", "soulreaper axe", "arclight", "ghrazi rapier",
				"blade of saeldor", "abyssal tentacle", "abyssal whip"
			)
			.efficientWeapons(
				"emberlight", "arclight", "soulreaper axe", "ghrazi rapier",
				"blade of saeldor", "abyssal whip", "zombie axe"
			)
			.optionalItems("ash sanctifier", "soul bearer", "bracelet of slaughter")
			.prayerPotionSlots(6)
			.zeroDamageWhileProtected()
			.strictWeaponProfile(true);
		if (cannon)
		{
			builder.tags(SlayerTaskStrategy.MethodTag.CANNON);
		}
		return builder.build();
	}

	private static Profiles dagannothProfiles()
	{
		final SlayerTaskStrategy ranged = cannonBlowpipe(
			"Use fast ranged attacks with a cannon in the Lighthouse Dungeon"
		);
		final SlayerTaskStrategy afk = venator(
			"Use a Venator bow with a cannon for a lower-attention multi-combat task"
		);
		final SlayerTaskStrategy melee = efficientMelee(
			"Prayer melee against melee-only Dagannoth when the selected location supports it",
			true
		);
		final SlayerTaskStrategy nonCannon = blowpipe(
			"Fast ranged attacks without a cannon",
			true
		);
		return new Profiles(ranged, ranged, afk, ranged)
			.withCombatOptions(melee, ranged, null)
			.withCannonOptions(ranged, nonCannon);
	}

	private static Profiles kalphiteProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use a Keris partisan on Pound with Protect from Melee and a cannon in the multi-combat task-only cave"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"keris partisan of breaching", "keris partisan of the sun",
				"keris partisan of corruption", "keris partisan",
				"keris partisan of amascut",
				"inquisitor s mace", "abyssal bludgeon", "zombie axe"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.CANNON,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(5)
			.damageProfile(SlayerTaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED)
			.build();
		final SlayerTaskStrategy ranged = cannonBlowpipe(
			"Use fast Ranged attacks with a cannon in the multi-combat task-only cave"
		);
		final SlayerTaskStrategy meleeWithoutCannon = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use a Keris partisan on Pound; protect from Melee against soldiers or guardians"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"keris partisan of breaching", "keris partisan of the sun",
				"keris partisan of corruption", "keris partisan",
				"keris partisan of amascut",
				"inquisitor s mace", "abyssal bludgeon", "zombie axe"
			)
			.prayerPotionSlots(5)
			.damageProfile(SlayerTaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED)
			.build();
		return new Profiles(melee, melee, melee, melee)
			.withCombatOptions(melee, ranged, null)
			.withCannonOptions(melee, meleeWithoutCannon);
	}

	private static Profiles wyrmProfiles()
	{
		final SlayerTaskStrategy ranged = blowpipe(
			"Ranged with stone-protection boots in the Karuulm Slayer Dungeon",
			true
		);
		final SlayerTaskStrategy melee = stab(
			"Dragonbane or accurate stab melee with stone-protection boots",
			true,
			"dragon hunter lance", "osmumten s fang", "ghrazi rapier",
			"zamorakian hasta", "abyssal dagger"
		);
		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, null);
	}

	private static Profiles drakeProfiles()
	{
		final SlayerTaskStrategy melee = stab(
			"Dragonbane or accurate stab melee with stone-protection boots",
			true,
			"dragon hunter lance", "osmumten s fang", "ghrazi rapier",
			"zamorakian hasta", "abyssal dagger"
		);
		final SlayerTaskStrategy ranged = blowpipe(
			"Ranged with stone-protection boots; sidestep the special dragonfire attack",
			true
		);
		final SlayerTaskStrategy magic = waterMagic(
			"Highest practical Water spell with stone-protection boots"
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE);
		return Profiles.same(melee)
			.withCombatOptions(melee, ranged, magic);
	}

	private static Profiles fossilWyvernProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use one-handed dragonbane or accurate stab melee with the required "
				+ "wyvern-protection shield; prioritize Ranged Defence"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DEFENCE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter lance", "osmumten s fang", "ghrazi rapier",
				"abyssal tentacle", "abyssal whip", "zamorakian hasta",
				"abyssal dagger"
			)
			.strictWeaponProfile(true)
			.prayerPotionSlots(4)
			.foodSlots(8)
			.build();
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use a one-handed crossbow with the required wyvern-protection shield "
				+ "and prioritize Ranged Defence"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DEFENCE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter crossbow", "hunters sunlight crossbow",
				"zaryte crossbow", "armadyl crossbow", "dragon crossbow",
				"rune crossbow"
			)
			.strictWeaponProfile(true)
			.prayerPotionSlots(4)
			.foodSlots(8)
			.build()
			.withSelectionNote(
				"Two-handed ranged weapons are excluded because the shield is required; "
					+ "the cave is not treated as a safespot."
			);
		return Profiles.same(melee)
			.withCombatOptions(melee, ranged, null);
	}

	private static Profiles hydraTaskProfiles()
	{
		final SlayerTaskStrategy ranged = blowpipe(
			"Use ranged; Toxic blowpipe or Bow of faerdhinen are practical defaults for regular Hydras",
			true
		);
		return Profiles.same(ranged);
	}

	private static Profiles vampyreProfiles()
	{
		final SlayerTaskStrategy base = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use Sunspear or a blisterwood weapon; use Venators for the active reward-focused alternative"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"sunspear",
				"blisterwood flail",
				"blisterwood sickle",
				"ivandis flail"
			)
			.strictWeaponProfile(true)
			.prayerPotionSlots(4)
			.build();
		return Profiles.same(base);
	}

	private static Profiles venatorCreatureProfiles()
	{
		final SlayerTaskStrategy base = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use the Sunspear as the primary weapon and save its special attack for the Venator finish mechanic"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"sunspear",
				"blisterwood flail",
				"ivandis flail"
			)
			.strictWeaponProfile(true)
			.prayerPotionSlots(5)
			.build();
		return Profiles.same(base);
	}

	private static Profiles warpedProfiles()
	{
		final SlayerTaskStrategy balanced = crush(
			"Use efficient crush melee against Warped terrorbirds or tortoises; add a cannon where permitted",
			false,
			false
		);
		final SlayerTaskStrategy afk = rangedPrayer(
			"Safespot Warped tortoises with ranged for a lower-attention task"
		);
		return new Profiles(balanced, balanced, afk, balanced);
	}

	private static Profiles elementalMagicProfiles(final String method)
	{
		final SlayerTaskStrategy base = elementalMagic(method);
		return Profiles.same(base);
	}

	private static Profiles fireGiantProfiles()
	{
		final SlayerTaskStrategy magic = waterMagic(
			"Use Water Surge or Water Wave against the Fire giants' 100% Water weakness"
		).withSelectionNote(
			"Automatic follows the current Slayer training guide's Water-magic recommendation."
		);
		final SlayerTaskStrategy ranged = venator(
			"Use a Venator bow in a compatible multi-combat area"
		).withAdditionalTags(
			SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE
		);
		final SlayerTaskStrategy melee = efficientMelee(
			"Use efficient melee only when explicitly preferred over the reviewed Water-magic method"
		).withAdditionalTags(
			SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE
		);
		return Profiles.same(magic)
			.withCombatOptions(melee, ranged, magic);
	}

	private static Profiles fireWeaknessProfiles(
		final String method,
		final boolean safespot)
	{
		return elementalWeaknessProfiles("fire", method, safespot);
	}

	private static Profiles waterWeaknessProfiles(
		final String method,
		final boolean safespot)
	{
		return elementalWeaknessProfiles("water", method, safespot);
	}

	private static Profiles airWeaknessProfiles(
		final String method,
		final boolean safespot)
	{
		return elementalWeaknessProfiles("air", method, safespot);
	}

	private static Profiles elementalWeaknessProfiles(
		final String element,
		final String method,
		final boolean safespot)
	{
		final SlayerTaskStrategy.Builder magicBuilder =
			SlayerTaskStrategy.builder(
				SlayerTaskStrategy.CombatStyle.MAGIC,
				method
			)
				.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
				.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
				.weapons(elementalStaffPriorities(element))
				.runePouch(true)
				.prayerPotionSlots(0);
		if (safespot)
		{
			magicBuilder.zeroDamageWhileSafespotted();
		}
		final SlayerTaskStrategy magic = magicBuilder.build();
		final SlayerTaskStrategy ranged = rangedCrossbow(
			"Use the documented safespot with Ranged when explicitly preferred",
			false
		).withAdditionalTags(
			SlayerTaskStrategy.MethodTag.SAFESPOT,
			SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE
		);
		final SlayerTaskStrategy melee = efficientMelee(
			"Use efficient melee only when explicitly preferred over the elemental-weakness method"
		).withAdditionalTags(
			SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE
		);
		return Profiles.same(magic)
			.withCombatOptions(melee, ranged, magic);
	}

	private static String[] elementalStaffPriorities(final String element)
	{
		if ("water".equals(element))
		{
			return new String[] {
				"harmonised nightmare staff", "kodai wand", "mist battlestaff",
				"water battlestaff", "mystic water staff", "staff of water"
			};
		}
		if ("fire".equals(element))
		{
			return new String[] {
				"twinflame staff", "harmonised nightmare staff", "smoke battlestaff",
				"lava battlestaff", "fire battlestaff", "mystic fire staff", "staff of fire"
			};
		}
		if ("earth".equals(element))
		{
			return new String[] {
				"harmonised nightmare staff", "staff of the dead", "mud battlestaff",
				"earth battlestaff", "mystic earth staff", "staff of earth"
			};
		}
		return new String[] {
			"twinflame staff", "harmonised nightmare staff", "smoke battlestaff",
			"air battlestaff", "mystic air staff", "staff of air"
		};
	}

	private static Profiles earthWarriorProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use a low-risk crush weapon in the Wilderness and retain an immediate escape"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons("ursine chainmace", "viggora s chainmace", "zombie axe", "dragon mace")
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(0)
			.build();
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Safespot with low-risk Magic, the Wiki's other recommended attack style"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons("blighted ancient ice sack", "trident of the seas", "warped sceptre", "iban s staff")
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS, SlayerTaskStrategy.MethodTag.SAFESPOT)
			.runePouch(true)
			.zeroDamageWhileSafespotted()
			.build();
		return Profiles.same(melee).withCombatOptions(melee, null, magic);
	}

	private static Profiles entProfiles()
	{
		return lowLevelSafespotProfiles(
			"Safespot an Ent behind a nearby tree with Ranged; keep the Wilderness setup low-risk"
		);
	}

	private static Profiles feverSpiderProfiles()
	{
		final SlayerTaskStrategy melee = efficientMelee(
			"Use efficient melee with Slayer gloves against the Fever spiders' low Defence"
		);
		final SlayerTaskStrategy ranged = rangedCrossbow(
			"Safespot with Ranged; carry Relicym's balm for an accidental disease hit",
			false
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.SAFESPOT);
		final SlayerTaskStrategy magic = elementalWeaknessProfiles(
			"fire",
			"Safespot with Fire spells when Magic is explicitly preferred",
			true
		).magic;
		return Profiles.same(melee).withCombatOptions(melee, ranged, magic);
	}

	private static Profiles harpieBugProfiles()
	{
		final Profiles base = elementalWeaknessProfiles(
			"fire",
			"Use the best Fire spell against the Harpie bug swarm's 50% Fire weakness while equipping a lit bug lantern",
			false
		);
		final SlayerTaskStrategy cannon = base.magic.withAdditionalTags(
			SlayerTaskStrategy.MethodTag.CANNON
		).withSelectionNote(
			"Cannon is enabled at a compatible Harpie bug swarm location; the lit bug lantern must remain equipped."
		);
		return base.withCannonOptions(cannon, base.magic);
	}

	private static Profiles mogreProfiles()
	{
		final SlayerTaskStrategy melee = efficientMelee(
			"Lure each Mogre with a fishing explosive and use efficient melee from the shoreline"
		);
		final SlayerTaskStrategy ranged = rangedCrossbow(
			"Lure each Mogre and use the documented shoreline or fairy-ring safespot",
			false
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.SAFESPOT);
		final SlayerTaskStrategy magic = earthMagic(
			"Lure each Mogre and safespot it with Earth spells against its 20% Earth weakness"
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.SAFESPOT);
		return Profiles.same(melee).withCombatOptions(melee, ranged, magic);
	}

	private static Profiles rockslugProfiles()
	{
		final SlayerTaskStrategy melee = efficientMelee(
			"Use efficient melee and finish each Rockslug with the required bag of salt"
		);
		final SlayerTaskStrategy magic = earthMagic(
			"Use Earth spells against the Rockslug's 25% Earth weakness and keep bags of salt for finishing blows"
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE);
		return Profiles.same(melee).withCombatOptions(melee, null, magic);
	}

	private static Profiles waterfiendProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use the best Earth spell against the Waterfiend's 100% Earth weakness"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.MAGIC_DEFENCE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(elementalStaffPriorities("earth"))
			.runePouch(true)
			.prayerPotionSlots(3)
			.build();
		final SlayerTaskStrategy melee = crush(
			"Use crush melee as the documented lower-cost alternative",
			false,
			false
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE);
		return Profiles.same(magic).withCombatOptions(melee, null, magic);
	}

	private static Profiles tzhaarProfiles()
	{
		final SlayerTaskStrategy bloodBarrage = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Stack TzHaar in inner Mor Ul Rek and use Blood Barrage for fast experience and sustain"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"kodai wand", "nightmare staff", "ancient sceptre",
				"master wand", "ancient staff"
			)
			.runePouch(true)
			.tags(
				SlayerTaskStrategy.MethodTag.BURST_BARRAGE,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT,
				SlayerTaskStrategy.MethodTag.AUTOMATIC_STYLE_LOCKED
			)
			.prayerPotionSlots(5)
			.build();
		final SlayerTaskStrategy ranged = blowpipe(
			"Use ranged from a safespot when Blood Barrage is disabled or Ranged is explicitly preferred",
			false
		).withAdditionalTags(
			SlayerTaskStrategy.MethodTag.SAFESPOT
		);
		return Profiles.same(bloodBarrage)
			.withCombatOptions(null, ranged, bloodBarrage)
			.withBurstOptions(bloodBarrage, ranged);
	}

	private static Profiles gryphonProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use stab melee while keeping at least 30 kg worn weight so the regular Gryphon knockback mechanic is controlled"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Post-launch guides favor practical stab melee for regular Gryphons. "
					+ "Their Air weakness remains a viable Magic alternative, but the weakness alone is not enough to make Magic the Automatic method."
			)
			.weapons(
				"ghrazi rapier",
				"osmumten s fang",
				"zamorakian hasta",
				"abyssal dagger",
				"dragon sword",
				"dragon scimitar"
			)
			.prayerPotionSlots(3)
			.strictWeaponProfile(true)
			.reviewed("2026-08-11")
			.build();

		final SlayerTaskStrategy cannonMelee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use stab melee with a dwarf multicannon in the multi-combat Gryphon area while keeping at least 30 kg worn weight"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"The cannon is the reviewed Fast-XP accelerator where the selected Gryphon area permits it; the player still uses the practical stab-melee core setup."
			)
			.weapons(
				"ghrazi rapier",
				"osmumten s fang",
				"zamorakian hasta",
				"abyssal dagger",
				"dragon sword",
				"dragon scimitar"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.CANNON,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(3)
			.strictWeaponProfile(true)
			.reviewed("2026-08-11")
			.build();

		final SlayerTaskStrategy magic = elementalMagic(
			"Use Air spells as the reviewed Magic-preference alternative while still meeting the Gryphon weight mechanic"
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE)
			.withSelectionNote(
				"Air weakness makes Magic viable, but current post-launch play does not justify replacing the practical stab-melee Automatic method solely because of that weakness."
			);

		return Profiles.same(melee)
			.withCombatOptions(melee, null, magic)
			.withCannonOptions(cannonMelee, melee);
	}

	private static Profiles caveKrakenProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Disturb a regular Cave kraken whirlpool, keep Protect from Magic active, and use your best practical powered Magic weapon"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.rationale(
				"Cave kraken are Magic-only. They now have a 50% Earth weakness, but Jagex explicitly cautions that elemental weaknesses do not automatically redefine established metas. Powered staffs remain the practical low-effort default; Earth spells are an optional account-specific DPS alternative."
			)
			.weapons(
				"tumeken s shadow",
				"eye of ayak",
				"sanguinesti staff",
				"trident of the swamp",
				"trident of the seas",
				"warped sceptre"
			)
			.prayerPotionSlots(5)
			.strictWeaponProfile(true)
			.reviewed("2026-08-11")
			.build();
		return Profiles.same(magic);
	}

	private static Profiles caveHorrorProfiles()
	{
		final SlayerTaskStrategy cannon = cannonBlowpipe(
			"Use fast ranged attacks with a witchwood icon and dwarf multicannon in the Mos Le'Harmless Cave"
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.REQUIRED_SPECIAL_GEAR);
		final SlayerTaskStrategy noCannon = blowpipe(
			"Use fast ranged attacks with a witchwood icon; dwarf multicannon is disabled",
			false
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.REQUIRED_SPECIAL_GEAR);
		final SlayerTaskStrategy melee = efficientMelee(
			"Use efficient melee with a witchwood icon only when Melee is explicitly preferred"
		).withAdditionalTags(
			SlayerTaskStrategy.MethodTag.REQUIRED_SPECIAL_GEAR,
			SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE
		);
		return Profiles.same(cannon)
			.withCombatOptions(melee, cannon, null)
			.withCannonOptions(cannon, noCannon);
	}

	private static Profiles krakenBossProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use a fishing explosive on the large whirlpool, then attack Kraken with powered Magic while favouring Magic-defence body and leg armour"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.MAGIC_DEFENCE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"Protection prayers do not reduce Kraken's typeless attacks. The established setup combines strong Magic damage with Magic defence; top-tier Magic robes remain excellent, while weaker robes can lose to strong ranged armour defensively."
			)
			.weapons(
				"tumeken s shadow",
				"eye of ayak",
				"sanguinesti staff",
				"trident of the swamp",
				"trident of the seas",
				"warped sceptre"
			)
			.prayerPotionSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-11")
			.build();
		return Profiles.same(magic);
	}

	private static Profiles poweredMagicProfiles(final String method)
	{
		final SlayerTaskStrategy base = poweredMagic(method);
		return Profiles.same(base);
	}

	private static Profiles frostOrElementalProfiles(final String method)
	{
		return Profiles.same(elementalMagic(method));
	}

	private static Profiles thermonuclearSmokeDevilProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Freeze with an Ice ancient sceptre, step beyond Thermy's attack range, then use powered Magic on Longrange"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"The freeze safespot takes little damage after setup, so carry two emergency food and use the remaining supply slots for prayer restoration."
			)
			.weapons(
				"tumeken s shadow",
				"eye of ayak",
				"sanguinesti staff",
				"trident of the swamp",
				"trident of the seas",
				"warped sceptre"
			)
			.runePouch(true)
			.optionalItems(
				"ice ancient sceptre",
				"ancient sceptre"
			)
			.selectionNote(
				"Use Ancient Magicks and carry Water, Blood, and Death runes for Ice Barrage "
					+ "(Water, Chaos, and Death for Ice Burst). Set Tumeken's shadow or the selected powered staff to Longrange."
			)
			.prayerPotionSlots(5)
			.foodSlots(2)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-09")
			.build();

		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"High-DPS melee with strong Magic defence and offensive prayer"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"The reviewed melee inventory carries prayer restoration and fills the remaining inventory with high-healing food."
			)
			.weapons(
				"scythe of vitur",
				"soulreaper axe",
				"emberlight",
				"arclight",
				"abyssal tentacle",
				"abyssal whip"
			)
			.prayerPotionSlots(5)
			.foodSlots(8)
			.fillRemainingInventoryWithFood()
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-09")
			.build();

		return Profiles.same(magic)
			.withCombatOptions(melee, null, magic);
	}

	private static Profiles jadProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Complete the Fight Caves with a sustainable ranged setup; at Jad, prioritise prayer switches and tag healers without losing the next attack cue"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"This is a wave encounter, so the inventory is built for the entire Fight Caves rather than a single Jad kill."
			)
			.weapons(
				"twisted bow",
				"bow of faerdhinen",
				"toxic blowpipe",
				"zaryte crossbow",
				"armadyl crossbow",
				"dragon crossbow",
				"rune crossbow",
				"crystal bow",
				"magic shortbow i"
			)
			.optionalItems(
				"saradomin brew",
				"super restore",
				"bastion potion"
			)
			.prayerPotionSlots(8)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(ranged);
	}

	private static Profiles zukProfiles()
	{
		final SlayerTaskStrategy fast = zukStrategy(
			"Complete the full Inferno with a sustainable ranged setup; use Ancient Magicks for wave sustain, then stay behind the shield and handle sets during Zuk",
			SlayerTaskStrategy.CostPolicy.MAX_DPS,
			11,
			5
		);
		final SlayerTaskStrategy safety = zukStrategy(
			"Inferno is not AFK: use a completion-first ranged setup with extra healing and prayer regeneration",
			SlayerTaskStrategy.CostPolicy.MAX_DPS,
			8,
			7
		);
		final SlayerTaskStrategy profit = zukStrategy(
			"Complete the full Inferno with a resource-efficient ranged setup while retaining every required wave and Zuk switch",
			SlayerTaskStrategy.CostPolicy.EFFICIENT,
			10,
			6
		);
		return new Profiles(fast, fast, safety, profit);
	}

	private static SlayerTaskStrategy zukStrategy(
		final String method,
		final SlayerTaskStrategy.CostPolicy costPolicy,
		final int prayerPotionSlots,
		final int foodSlots)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(costPolicy)
			.rationale(
				"Zuk cannot be treated as a normal single-room boss: supplies, switches, prayer restoration, and healing must last through the entire Inferno."
			)
			.weapons(
				"twisted bow",
				"bow of faerdhinen",
				"zaryte crossbow",
				"armadyl crossbow",
				"dragon crossbow",
				"rune crossbow"
			)
			.optionalItems(
				"toxic blowpipe",
				"kodai wand",
				"ancient sceptre",
				"ice ancient sceptre",
				"saradomin brew",
				"super restore",
				"extended stamina potion",
				"stamina potion"
			)
			.runePouch(true)
			.prayerPotionSlots(prayerPotionSlots)
			.foodSlots(foodSlots)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
	}

	private static Profiles researchedBossProfile(
		final SlayerTaskStrategy.CombatStyle style,
		final String method,
		final boolean antivenom,
		final String... weapons)
	{
		return Profiles.same(researchedBossStrategy(
			style, method, antivenom, weapons
		));
	}

	private static Profiles alchemicalHydraProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use high-tier Ranged, lure the first three phases over the matching vents, and alternate prayers in the final phase"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"twisted bow", "toxic blowpipe", "dragon hunter crossbow",
				"bow of faerdhinen", "eclipse atlatl"
			)
			.optionalItems("zaryte crossbow", "toxic blowpipe", "bracelet of slaughter")
			.prayerPotionSlots(10)
			.foodSlots(5)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use Scythe or Dragon hunter lance on stab, lure each phase over its matching vent, and alternate prayers in the final phase"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"scythe of vitur", "dragon hunter lance", "osmumten s fang",
				"ghrazi rapier", "blade of saeldor", "inquisitor s mace",
				"abyssal dagger", "noxious halberd", "zamorakian hasta"
			)
			.optionalItems("zaryte crossbow", "saradomin godsword", "bracelet of slaughter")
			.prayerPotionSlots(5)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	private static Profiles sarachnisProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use fast crush melee, Protect from Missiles at range, and switch to Melee prayer when Sarachnis reaches you"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dual macuahuitl", "zamorakian hasta", "sarachnis cudgel",
				"zombie axe", "abyssal tentacle", "saradomin sword"
			)
			.optionalItems("scythe of vitur", "inquisitor s mace", "soulreaper axe")
			.prayerPotionSlots(6)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		final SlayerTaskStrategy magic = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use Tumeken's shadow only when a complete high-tier Magic setup is explicitly preferred",
			false,
			"tumeken s shadow"
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE);
		return Profiles.same(melee).withCombatOptions(melee, null, magic);
	}

	private static SlayerTaskStrategy researchedBossStrategy(
		final SlayerTaskStrategy.CombatStyle style,
		final String method,
		final boolean antivenom,
		final String... weapons)
	{
		return SlayerTaskStrategy.builder(style, method)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(weapons)
			.prayerPotionSlots(5)
			.antivenom(antivenom)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
	}

	private static Profiles graardorProfiles()
	{
		final SlayerTaskStrategy ranged = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use Bow of faerdhinen or another accurate ranged weapon and kite Graardor around the room",
			false,
			"bow of faerdhinen", "twisted bow", "zaryte crossbow",
			"toxic blowpipe"
		);
		final SlayerTaskStrategy magic = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use Tumeken's shadow while kiting Graardor; use Ranged when a complete Shadow setup is unavailable",
			false,
			"tumeken s shadow", "eye of ayak", "sanguinesti staff", "trident of the swamp"
		);
		return Profiles.same(ranged).withCombatOptions(null, ranged, magic);
	}

	private static Profiles giantMoleProfiles()
	{
		final SlayerTaskStrategy melee = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use high-DPS melee with Protect from Melee; carry a spade and safe light source",
			false,
			"scythe of vitur", "osmumten s fang", "ghrazi rapier",
			"inquisitor s mace", "abyssal bludgeon", "zombie axe"
		);
		final SlayerTaskStrategy ranged = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use Twisted bow or Bow of faerdhinen with Protect from Melee",
			false,
			"twisted bow", "bow of faerdhinen", "zaryte crossbow",
			"armadyl crossbow", "toxic blowpipe"
		);
		final SlayerTaskStrategy magic = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use Tumeken's shadow with Protect from Melee",
			false,
			"tumeken s shadow", "eye of ayak", "sanguinesti staff", "trident of the swamp"
		);
		return Profiles.same(ranged).withCombatOptions(melee, ranged, magic);
	}

	private static Profiles kreearraProfiles()
	{
		final SlayerTaskStrategy ranged = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use chinchompas or high-tier Ranged; bring the crossbow and mith grapple required for Armadyl's Eyrie",
			false,
			"black chinchompa", "red chinchompa", "bow of faerdhinen",
			"twisted bow", "zaryte crossbow", "toxic blowpipe"
		).withAdditionalTags(SlayerTaskStrategy.MethodTag.CHINNING);
		final SlayerTaskStrategy magic = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use Tumeken's shadow with an Armadyl protection item",
			false,
			"tumeken s shadow", "eye of ayak", "sanguinesti staff", "trident of the swamp"
		);
		return Profiles.same(ranged).withCombatOptions(null, ranged, magic);
	}

	private static Profiles araxxorBossProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Exploit Araxxor's crush weakness, maintain extended anti-venom+, carry a safe araxyte weapon, and use one accurate Defence-reduction special"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"scythe of vitur", "inquisitor s mace", "soulreaper axe",
				"abyssal bludgeon", "ursine chainmace u", "zamorakian hasta",
				"sarachnis cudgel", "dual macuahuitl", "saradomin sword",
				"zombie axe"
			)
			.optionalItems(
				"elder maul", "dragon warhammer", "dragon claws", "burning claws",
				"noxious halberd", "dragon halberd"
			)
			.runePouch(true)
			.antivenom(true)
			.prayerPotionSlots(4)
			.foodSlots(9)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-19")
			.build();
		return Profiles.same(melee);
	}

	private static Profiles artioProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Freeze Artio, attack with a low-risk Wilderness Magic weapon, and keep an immediate escape"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"accursed sceptre", "thammaron s sceptre", "trident of the swamp",
				"warped sceptre", "trident of the seas", "iban s staff"
			)
			.runePouch(true)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(4)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use a protected low-risk Ranged setup, freeze Artio for control, and keep an immediate Wilderness escape"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"webweaver bow", "craw s bow", "bow of faerdhinen",
				"eclipse atlatl", "dragon crossbow", "rune crossbow",
				"magic shortbow i", "magic shortbow"
			)
			.optionalItems("zaryte crossbow", "saturated heart", "imbued heart")
			.runePouch(true)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(4)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		/* Webweaver is the strongest practical default; Magic remains explicit. */
		return Profiles.same(ranged).withCombatOptions(null, ranged, magic);
	}

	private static Profiles callistoProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Freeze Callisto, use a protected low-risk Wilderness Magic setup, and preserve an immediate escape"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"accursed sceptre", "thammaron s sceptre", "trident of the swamp",
				"warped sceptre", "trident of the seas", "iban s staff"
			)
			.runePouch(true)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(5)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		final SlayerTaskStrategy ranged = wildernessBossRangedProfiles(
			"Use low-risk Ranged only when explicitly preferred"
		).selectAutomatic(SlayerPreference.Playstyle.FAST_XP);
		return Profiles.same(magic).withCombatOptions(null, ranged, magic);
	}

	private static Profiles zilyanaProfiles()
	{
		final SlayerTaskStrategy ranged = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use Bow of faerdhinen or Twisted bow and kite Commander Zilyana around the room",
			false,
			"bow of faerdhinen", "twisted bow", "zaryte crossbow",
			"toxic blowpipe"
		);
		final SlayerTaskStrategy magic = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use Tumeken's shadow while kiting Commander Zilyana",
			false,
			"tumeken s shadow", "sanguinesti staff", "trident of the swamp"
		);
		return Profiles.same(ranged).withCombatOptions(null, ranged, magic);
	}

	private static Profiles abyssalSireProfiles()
	{
		final SlayerTaskStrategy hybrid = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use Scorching bow or Shadow on the respiratory systems, then switch to Emberlight/Arclight or another reviewed melee weapon for phases two and three"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"emberlight", "osmumten s fang", "arclight", "scythe of vitur",
				"noxious halberd", "abyssal bludgeon"
			)
			.optionalItems(
				"scorching bow", "tumeken s shadow", "sanguinesti staff",
				"trident of the swamp", "blood ancient sceptre",
				"dragon warhammer", "bandos godsword"
			)
			.runePouch(true)
			.prayerPotionSlots(5)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(hybrid);
	}

	private static Profiles dagannothKingsProfiles()
	{
		final SlayerTaskStrategy hybrid = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Bring a complete tribrid switch: Magic for Rex, Ranged for Prime, and melee for Supreme"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.HYBRID)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons("tumeken s shadow", "sanguinesti staff", "trident of the swamp", "trident of the seas")
			.optionalItems(
				"twisted bow", "bow of faerdhinen", "zaryte crossbow", "toxic blowpipe",
				"scythe of vitur", "soulreaper axe", "osmumten s fang", "abyssal whip"
			)
			.prayerPotionSlots(8)
			.foodSlots(4)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		return Profiles.same(hybrid);
	}

	private static Profiles grotesqueGuardianProfiles()
	{
		final SlayerTaskStrategy hybrid = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Use Venator bow or Toxic blowpipe against Dawn and the best owned melee progression against Dusk; keep a rock hammer for the finishing blow"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.HYBRID)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"scythe of vitur", "granite hammer", "soulreaper axe",
				"ghrazi rapier", "noxious halberd", "abyssal tentacle",
				"osmumten s fang", "abyssal whip", "abyssal bludgeon",
				"zamorakian hasta", "dragon scimitar"
			)
			.optionalItems(
				"venator bow", "toxic blowpipe", "bow of faerdhinen",
				"eclipse atlatl", "hunters sunlight crossbow",
				"magic shortbow i", "rock hammer"
			)
			.prayerPotionSlots(9)
			.foodSlots(7)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(hybrid);
	}

	private static Profiles kalphiteQueenProfiles()
	{
		final SlayerTaskStrategy hybrid = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Use high-DPS crush melee for phase one and carry a strong Ranged switch for phase two"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.HYBRID)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"keris partisan of breaching", "scythe of vitur",
				"keris partisan of the sun", "keris partisan of corruption",
				"keris partisan", "keris partisan of amascut",
				"inquisitor s mace", "soulreaper axe"
			)
			.prayerPotionSlots(3)
			.foodSlots(12)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use the current Wiki zero-switch Magic method only with Tumeken's shadow or Eye of ayak"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons("tumeken s shadow", "eye of ayak")
			.prayerPotionSlots(1)
			.foodSlots(18)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		return Profiles.same(hybrid)
			.withCombatOptions(hybrid, hybrid, magic);
	}

	private static Profiles kingBlackDragonProfiles()
	{
		final SlayerTaskStrategy ranged = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use a one-handed dragonbane crossbow with a dragonfire shield and antifire protection",
			false,
			"dragon hunter crossbow", "zaryte crossbow", "dragon crossbow", "armadyl crossbow", "rune crossbow"
		);
		final SlayerTaskStrategy melee = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use Dragon hunter lance or another accurate stab weapon with full dragonfire protection",
			false,
			"dragon hunter lance", "osmumten s fang", "ghrazi rapier", "zamorakian hasta"
		);
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	private static Profiles krilProfiles()
	{
		final SlayerTaskStrategy melee = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use the reviewed Magic-defence melee setup with Protect from Melee, poison protection, and a step-under cycle",
			false,
			"emberlight", "osmumten s fang", "arclight"
		);
		final SlayerTaskStrategy ranged = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use Scorching bow with Lightbearer to bind and kite K'ril, then Blood Barrage stacked bodyguards between kills; fall back to Twisted bow or Bow of faerdhinen",
			false,
			"scorching bow", "twisted bow", "bow of faerdhinen"
		);
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	private static Profiles phantomMuspahProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use Bow of faerdhinen or crossbow Ranged with a sapphire-bolt switch for the prayer shield"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons("bow of faerdhinen", "twisted bow", "zaryte crossbow", "dragon crossbow", "rune crossbow")
			.optionalItems("sapphire dragon bolts e", "sapphire bolts e", "stamina potion")
			.prayerPotionSlots(5)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		final SlayerTaskStrategy magic = researchedBossStrategy(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use a powered Magic setup with a Ranged switch for the prayer shield and enrage",
			false,
			"tumeken s shadow", "eye of ayak", "sanguinesti staff", "trident of the swamp", "trident of the seas"
		);
		return Profiles.same(ranged).withCombatOptions(null, ranged, magic);
	}

	private static Profiles skotizoProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use Emberlight or Arclight and destroy active altars quickly; bring a stamina option for altar movement"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons("emberlight", "arclight", "scythe of vitur", "soulreaper axe", "abyssal tentacle")
			.optionalItems("stamina potion", "extended stamina potion")
			.prayerPotionSlots(3)
			.foodSlots(10)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		return Profiles.same(melee).withCombatOptions(melee, null, null);
	}

	private static Profiles zulrahProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use a complete Magic setup with a compact Ranged switch and venom protection; match the active phase"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.HYBRID)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons("tumeken s shadow", "eye of ayak", "sanguinesti staff", "trident of the swamp", "trident of the seas")
			.optionalItems("twisted bow", "bow of faerdhinen", "zaryte crossbow", "toxic blowpipe")
			.antivenom(true)
			.prayerPotionSlots(4)
			.foodSlots(10)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use a complete Bow of faerdhinen or Twisted bow setup with venom protection"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons("twisted bow", "bow of faerdhinen")
			.antivenom(true)
			.prayerPotionSlots(4)
			.foodSlots(10)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		return Profiles.same(magic).withCombatOptions(null, ranged, magic);
	}

	private static Profiles maggotKingProfiles()
	{
		final SlayerTaskStrategy base = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Use Fire Magic while the Maggot King is distant, then equip the complete crush switch for each punish window"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.HYBRID)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"harmonised nightmare staff",
				"tumeken s shadow",
				"twinflame staff",
				"purging staff",
				"smoke battlestaff",
				"staff of the dead",
				"nightmare staff",
				"mystic smoke staff"
			)
			.optionalItems(
				"scythe of vitur",
				"soulreaper axe",
				"inquisitor s mace",
				"abyssal bludgeon",
				"dual macuahuitl",
				"zombie axe"
			)
			.runePouch(true)
			.prayerPotionSlots(5)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(base);
	}

	private static Profiles shellbaneProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use one-handed melee with a Tortugan shield, Protect from Melee, and at least 40 kg worn weight; move one tile for corrosive spit and whirlwinds"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.rationale(
				"The shield and 40 kg weight check are encounter mechanics, so they take priority over a nominally higher-DPS lightweight setup."
			)
			.weapons(
				"ghrazi rapier",
				"osmumten s fang",
				"inquisitor s mace",
				"zombie axe",
				"abyssal whip",
				"dragon scimitar"
			)
			.optionalItems(
				"tortugan shield",
				"super combat potion",
				"prayer potion"
			)
			.prayerPotionSlots(4)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		return Profiles.same(melee);
	}

	private static Profiles araxyteProfiles()
	{
		final SlayerTaskStrategy fast = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use a Venator bow with a dwarf multicannon in the multi-combat Araxyte room; keep Protect from Melee active and maintain antivenom protection"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"venator bow",
				"bow of faerdhinen",
				"toxic blowpipe"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.CANNON,
				SlayerTaskStrategy.MethodTag.VENATOR,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.antivenom(true)
			.prayerPotionSlots(4)
			.build();

		final SlayerTaskStrategy profit = crush(
			"Use efficient crush melee with Protect from Melee and antivenom when conserving cannonballs",
			true,
			false
		);
		return new Profiles(fast, fast, fast, profit)
			.withCombatOptions(profit, fast, null)
			.withCannonOptions(fast, profit);
	}

	private static Profiles custodianStalkerProfiles()
	{
		final SlayerTaskStrategy barrage = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Stack Custodian Stalkers in multi-combat and use Ice Barrage with a dwarf multicannon for maximum Slayer experience"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"kodai wand",
				"nightmare staff",
				"ancient sceptre",
				"master wand"
			)
			.runePouch(true)
			.tags(
				SlayerTaskStrategy.MethodTag.CANNON,
				SlayerTaskStrategy.MethodTag.BURST_BARRAGE,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(5)
			.build();

		final SlayerTaskStrategy barrageNoCannon = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Stack Custodian Stalkers and use Ice/Blood Barrage without a dwarf multicannon"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons("kodai wand", "nightmare staff", "ancient sceptre", "master wand")
			.runePouch(true)
			.tags(
				SlayerTaskStrategy.MethodTag.BURST_BARRAGE,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(5)
			.build();

		final SlayerTaskStrategy afk = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Use a Venator bow with a dwarf multicannon and Protect from Melee for a lower-attention multi-combat task"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"venator bow",
				"bow of faerdhinen",
				"toxic blowpipe"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.CANNON,
				SlayerTaskStrategy.MethodTag.VENATOR,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(4)
			.build();

		final SlayerTaskStrategy melee = efficientMelee(
			"Use slash-focused melee with Protect from Melee and food for bleed damage when cannon/barrage are disabled",
			true
		);

		return new Profiles(melee, barrage, afk, melee)
			.withCombatOptions(melee, afk, barrage)
			.withBurstOptions(barrage, melee)
			.withCannonOptions(barrage, barrageNoCannon);
	}

	private static Profiles wildernessRangedProfiles(final String method)
	{
		return Profiles.same(lowRiskRanged(method));
	}

	private static Profiles wildernessMagicProfiles(final String method)
	{
		return Profiles.same(lowRiskMagic(method));
	}

	private static Profiles wildernessBossRangedProfiles(final String method)
	{
		final SlayerTaskStrategy base = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"webweaver bow",
				"craw s bow",
				"bow of faerdhinen",
				"magic shortbow",
				"rune crossbow"
			)
			.runePouch(true)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(4)
			.boss(true)
			.build();
		return Profiles.same(base);
	}

	private static Profiles wildernessBossSimpleRangedProfiles(final String method)
	{
		final SlayerTaskStrategy base = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"webweaver bow",
				"craw s bow",
				"magic shortbow",
				"rune crossbow"
			)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(4)
			.boss(true)
			.build();
		return Profiles.same(base);
	}

	private static Profiles wildernessBossMagicProfiles(final String method)
	{
		final SlayerTaskStrategy base = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"accursed sceptre",
				"thammaron s sceptre",
				"trident of the seas",
				"iban s staff"
			)
			.runePouch(true)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(4)
			.boss(true)
			.build();
		return Profiles.same(base);
	}

	private static Profiles scurriusProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use a rat bone mace when owned; protect against the active attack style and kill spawned rats for prayer/food sustain"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons("bone mace", "zombie axe", "dragon scimitar", "abyssal whip")
			.prayerPotionSlots(6)
			.foodSlots(12)
			.boss(true)
			.build();
		return Profiles.same(melee);
	}

	private static Profiles oborProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use the best practical Earth spell, Protect from Missiles, and kite/snare Obor when needed"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons("harmonised nightmare staff", "staff of the dead", "earth battlestaff", "mystic earth staff", "staff of earth")
			.runePouch(true)
			.prayerPotionSlots(2)
			.boss(true)
			.build();
		return Profiles.same(magic);
	}

	private static Profiles bryophytaProfiles()
	{
		final SlayerTaskStrategy magic = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use Fire spells from range under Protect from Magic; kill the growthlings with an axe or secateurs when she becomes immune"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons("harmonised nightmare staff", "smoke battlestaff", "mystic fire staff", "fire battlestaff", "staff of fire")
			.runePouch(true)
			.prayerPotionSlots(2)
			.boss(true)
			.build();
		return Profiles.same(magic);
	}

	private static Profiles brutusProfiles()
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Attack Brutus from behind the southern troughs, or move promptly from his charge and stomp attacks"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"toxic blowpipe", "eclipse atlatl", "hunters sunlight crossbow",
				"magic shortbow i", "magic shortbow", "rune crossbow",
				"dorgeshuun crossbow", "maple shortbow"
			)
			.optionalItems("cowbell amulet", "tinderbox")
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use fast melee with Protect from Melee and move out of Brutus' charge and stomp attacks"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons("dragon scimitar", "rune scimitar", "adamant scimitar", "mithril scimitar")
			.optionalItems("cowbell amulet", "tinderbox")
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		return Profiles.same(ranged).withCombatOptions(melee, ranged, null);
	}

	private static Profiles demonicGorillaProfiles()
	{
		final SlayerTaskStrategy hybrid = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Use an imbued Slayer helmet with demonbane melee and a compact Ranged switch as each gorilla changes protection prayer"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.HYBRID)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons("emberlight", "arclight", "osmumten s fang", "abyssal tentacle")
			.optionalItems("scorching bow", "bow of faerdhinen", "toxic blowpipe", "twisted bow", "royal seed pod")
			.prayerPotionSlots(5)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		return Profiles.same(hybrid);
	}

	private static Profiles tormentedDemonProfiles()
	{
		final SlayerTaskStrategy hybrid = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Use demonbane melee with Scorching/Twisted bow or Purging staff; use a slow crush weapon during the shield-down punish window"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.HYBRID)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"emberlight", "arclight", "abyssal bludgeon", "abyssal dagger",
				"osmumten s fang", "abyssal tentacle", "abyssal whip",
				"zamorakian hasta", "sarachnis cudgel", "arkan blade", "zombie axe"
			)
			.prayerPotionSlots(7)
			.foodSlots(5)
			.strictWeaponProfile(true)
			.boss(true)
			.build();
		return Profiles.same(hybrid);
	}

	private static Profiles royalTitansProfiles()
	{
		final SlayerTaskStrategy melee = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			"Use melee as the primary style, switch to Ranged while a Titan is out of reach, and carry Water Wave and Fire Wave for the elemental mechanics"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"scythe of vitur", "inquisitor s mace", "soulreaper axe",
				"blade of saeldor", "ghrazi rapier", "noxious halberd",
				"dual macuahuitl", "osmumten s fang", "abyssal tentacle",
				"abyssal whip", "zombie axe"
			)
			.optionalItems(
				"toxic blowpipe", "bow of faerdhinen", "eclipse atlatl",
				"zaryte crossbow", "armadyl crossbow", "dragon crossbow",
				"twinflame staff", "purging staff", "kodai wand",
				"burning claws", "dragon claws", "crystal halberd"
			)
			.runePouch(true)
			.prayerPotionSlots(5)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.boss(true)
			.reviewed("2026-08-16")
			.build();
		/* All three styles are encounter switches; melee remains the equipped base. */
		return Profiles.same(melee).withCombatOptions(melee, melee, melee);
	}

	private static Profiles wildernessCrushProfiles(final String method)
	{
		final SlayerTaskStrategy base = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"ursine chainmace",
				"viggora s chainmace",
				"zombie axe",
				"dragon mace"
			)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(3)
			.boss(true)
			.build();
		return Profiles.same(base);
	}

	private static SlayerTaskStrategy efficientMelee(final String method)
	{
		return efficientMelee(method, false);
	}

	private static SlayerTaskStrategy efficientMelee(
		final String method,
		final boolean prayer)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.armourFocus(prayer
				? SlayerTaskStrategy.ArmourFocus.PRAYER
				: SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"ghrazi rapier",
				"blade of saeldor",
				"inquisitor s mace",
				"abyssal tentacle",
				"abyssal whip",
				"zombie axe",
				"dragon scimitar"
			)
			.prayerPotionSlots(prayer ? 5 : 2)
			.build();
	}

	private static SlayerTaskStrategy demonbane(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"emberlight",
				"arclight",
				"silverlight",
				"abyssal tentacle",
				"abyssal whip"
			)
			.prayerPotionSlots(4)
			.build();
	}

	private static SlayerTaskStrategy stab(
		final String method,
		final boolean prayer,
		final String... weapons)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.armourFocus(prayer
				? SlayerTaskStrategy.ArmourFocus.PRAYER
				: SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(weapons)
			.prayerPotionSlots(prayer ? 5 : 3)
			.build();
	}

	private static SlayerTaskStrategy crush(
		final String method,
		final boolean antivenom,
		final boolean boss)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.costPolicy(boss
				? SlayerTaskStrategy.CostPolicy.MAX_DPS
				: SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(boss
				? new String[]{
					"scythe of vitur",
					"soulreaper axe",
					"inquisitor s mace",
					"crimson kisten",
					"abyssal bludgeon",
					"dual macuahuitl",
					"zombie axe"
				}
				: new String[]{
					"inquisitor s mace",
					"abyssal bludgeon",
					"dual macuahuitl",
					"sarachnis cudgel",
					"zombie axe",
					"dragon mace"
				})
			.prayerPotionSlots(boss ? 5 : 3)
			.antivenom(antivenom)
			.boss(boss)
			.build();
	}

	private static SlayerTaskStrategy blowpipe(
		final String method,
		final boolean prayer)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.armourFocus(prayer
				? SlayerTaskStrategy.ArmourFocus.PRAYER
				: SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"toxic blowpipe",
				"bow of faerdhinen",
				"crystal bow",
				"magic shortbow"
			)
			.prayerPotionSlots(prayer ? 5 : 3)
			.build();
	}

	private static SlayerTaskStrategy rangedPrayer(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"venator bow",
				"bow of faerdhinen",
				"toxic blowpipe",
				"magic shortbow"
			)
			.prayerPotionSlots(6)
			.build();
	}

	private static SlayerTaskStrategy venator(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"venator bow",
				"bow of faerdhinen",
				"toxic blowpipe",
				"magic shortbow"
			)
			.tags(
				SlayerTaskStrategy.MethodTag.VENATOR,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(5)
			.build();
	}

	private static SlayerTaskStrategy cannonBlowpipe(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"toxic blowpipe",
				"bow of faerdhinen",
				"magic shortbow"
			)
			.tags(SlayerTaskStrategy.MethodTag.CANNON)
			.prayerPotionSlots(3)
			.build();
	}

	private static SlayerTaskStrategy rangedCrossbow(
		final String method,
		final boolean prayer)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.armourFocus(prayer
				? SlayerTaskStrategy.ArmourFocus.PRAYER
				: SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"zaryte crossbow",
				"dragon hunter crossbow",
				"armadyl crossbow",
				"dragon crossbow",
				"rune crossbow"
			)
			.prayerPotionSlots(prayer ? 5 : 3)
			.build();
	}

	private static SlayerTaskStrategy barrage(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(SlayerTaskStrategy.CostPolicy.MAX_DPS)
			.weapons(
				"kodai wand",
				"nightmare staff",
				"ancient sceptre",
				"master wand",
				"staff of the dead"
			)
			.runePouch(true)
			.tags(
				SlayerTaskStrategy.MethodTag.BURST_BARRAGE,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(5)
			.build();
	}

	private static SlayerTaskStrategy poweredMagic(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"tumeken s shadow",
				"sanguinesti staff",
				"trident of the swamp",
				"trident of the seas",
				"warped sceptre"
			)
			/*
			 * Powered staffs supply their own casts. A rune pouch is added only
			 * when the exact researched method independently requires spells or
			 * rune storage; Magic combat by itself is not sufficient justification.
			 */
			.prayerPotionSlots(3)
			.build();
	}

	private static SlayerTaskStrategy elementalMagic(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"twinflame staff",
				"harmonised nightmare staff",
				"smoke battlestaff",
				"mystic air staff",
				"air battlestaff",
				"staff of air",
				"trident of the swamp"
			)
			.runePouch(true)
			.prayerPotionSlots(4)
			.build();
	}

	private static SlayerTaskStrategy earthMagic(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"harmonised nightmare staff",
				"staff of the dead",
				"mystic earth staff",
				"earth battlestaff",
				"staff of earth"
			)
			.runePouch(true)
			.prayerPotionSlots(4)
			.build();
	}

	private static SlayerTaskStrategy waterMagic(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"harmonised nightmare staff",
				"kodai wand",
				"mist battlestaff",
				"water battlestaff",
				"staff of water"
			)
			.runePouch(true)
			.prayerPotionSlots(4)
			.build();
	}

	private static SlayerTaskStrategy dragonRanged(
		final String method,
		final boolean boss)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.costPolicy(boss
				? SlayerTaskStrategy.CostPolicy.MAX_DPS
				: SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter crossbow",
				"twisted bow",
				"bow of faerdhinen",
				"toxic blowpipe",
				"dragon crossbow",
				"rune crossbow"
			)
			.prayerPotionSlots(boss ? 5 : 3)
			.boss(boss)
			.build();
	}

	private static SlayerTaskStrategy shieldedDragonRanged(
		final String method,
		final boolean boss)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(boss
				? SlayerTaskStrategy.CostPolicy.MAX_DPS
				: SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter crossbow", "zaryte crossbow", "armadyl crossbow",
				"hunters sunlight crossbow", "dragon crossbow", "rune crossbow"
			)
			.prayerPotionSlots(boss ? 5 : 0)
			.strictWeaponProfile(true)
			.zeroDamageWhileSafespotted()
			.boss(boss)
			.reviewed("2026-08-16")
			.build();
	}

	private static SlayerTaskStrategy dragonStab(
		final String method,
		final boolean boss)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.PRAYER)
			.costPolicy(boss
				? SlayerTaskStrategy.CostPolicy.MAX_DPS
				: SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(
				"dragon hunter lance",
				"osmumten s fang",
				"ghrazi rapier",
				"zamorakian hasta",
				"abyssal dagger"
			)
			.prayerPotionSlots(boss ? 5 : 4)
			.boss(boss)
			.build();
	}

	private static SlayerTaskStrategy lowRiskRanged(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"webweaver bow",
				"craw s bow",
				"magic shortbow",
				"rune crossbow"
			)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(3)
			.build();
	}

	private static SlayerTaskStrategy lowRiskMagic(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"thammaron s sceptre",
				"accursed sceptre",
				"trident of the seas",
				"iban s staff"
			)
			.runePouch(true)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(3)
			.build();
	}

	/**
	 * Every ordinary Krystilia assignment has its own reviewed Wilderness
	 * branch.  These profiles deliberately share LOW_RISK equipment policy,
	 * but the selected method, combat restrictions, required protection, and
	 * cannon/barrage alternatives remain task-specific.
	 */
	private static Profiles krystiliaProfiles(final String task)
	{
		switch (task)
		{
			case "abyssal demons":
				return krystiliaBurstProfiles("Stack abyssal demons in the Wilderness Slayer Cave and Ice Barrage; use low-risk gear and watch the teleport-blocking level");
			case "ankou":
				return krystiliaBurstProfiles("Stack Ankou in the Wilderness Slayer Cave and burst or barrage them with a low-risk setup");
			case "dust devils":
				return krystiliaBurstProfiles("Wear a face mask or Slayer helmet, stack dust devils in the Wilderness Slayer Cave, and burst or barrage");
			case "jellies":
				return krystiliaBurstProfiles("Stack jellies in the Wilderness Slayer Cave and burst or barrage with disposable Magic gear");
			case "nechryael":
				return krystiliaBurstProfiles("Stack greater nechryael in the Wilderness Slayer Cave and barrage while controlling death spawns");
			case "black dragons":
				return krystiliaDragonProfiles("Safespot black dragons with a one-handed crossbow and dragonfire protection; carry only replaceable supplies");
			case "green dragons":
				return krystiliaDragonProfiles("Use a one-handed crossbow with dragonfire protection at a low-traffic green-dragon location and bank bones/hides often");
			case "aviansies":
				return krystiliaRangedOnlyProfiles("Use low-risk Ranged in the Wilderness God Wars Dungeon with the required god-protection items; this is a slow task and a reasonable block");
			case "bloodveld":
				return krystiliaRangedOnlyProfiles("Use low-risk Ranged in the Wilderness God Wars Dungeon with required god protection; this is a slow task and a reasonable block");
			case "spiritual creatures":
				return krystiliaRangedOnlyProfiles("Use low-risk Ranged in the Wilderness God Wars Dungeon with god protection for every faction crossed; this is a strong block candidate");
			case "pirates":
				return krystiliaRoutineProfiles("Kill zombie pirates at the Chaos Temple with a low-risk setup; use either the Slayer-helmet package or Salve package, never both bonuses");
			case "zombies":
				return krystiliaRoutineProfiles("Kill zombie pirates at the Chaos Temple with a low-risk setup; use either the Slayer-helmet package or Salve package, never both bonuses");
			case "bears":
				return krystiliaRoutineProfiles("Use a low-risk setup at a dense bear spawn; Artio or Callisto remains a separately selected boss variant");
			case "black demons":
				return krystiliaRoutineProfiles("Use low-risk demonbane melee or Ranged in the Wilderness Slayer Cave; block if completion time outweighs the rewards");
			case "greater demons":
				return krystiliaRoutineProfiles("Use low-risk demonbane melee or Ranged in the Wilderness Slayer Cave; K'ril is a separately selected boss variant");
			case "hellhounds":
				return Profiles.same(hellhoundWilderness());
			case "bandits":
				return krystiliaRoutineProfiles("Use low-risk fast attacks at the Wilderness bandit camp and keep an immediate escape available");
			case "black knights":
				return krystiliaRoutineProfiles("Use low-risk fast attacks at the northern Wilderness spawn and avoid unnecessary risk");
			case "chaos druids":
				return krystiliaRoutineProfiles("Use a disposable fast-attack setup in Edgeville Dungeon and bank herbs before accumulated loot becomes meaningful risk");
			case "dark warriors":
				return krystiliaRoutineProfiles("Use a disposable fast-attack setup at the Dark Warriors' Fortress and preserve a one-click escape where possible");
			case "earth warriors":
				return krystiliaRoutineProfiles("Use a low-risk setup in the Wilderness section of Edgeville Dungeon and retain an escape route");
			case "ents":
				return krystiliaRoutineProfiles("Use a low-risk setup at Wilderness ents; this slow low-loot assignment is a reasonable block");
			case "fire giants":
				return krystiliaRoutineProfiles("Use low-risk Ranged in the Wilderness Slayer Cave, adding a cannon only when the player preference enables it");
			case "hill giants":
				return krystiliaRoutineProfiles("Use low-risk fast attacks at a dense Wilderness spawn; Obor is not a valid Wilderness kill replacement");
			case "ice giants":
				return krystiliaRoutineProfiles("Use low-risk Ranged at the Wilderness Ice Plateau and preserve an escape from the deep-Wilderness route");
			case "ice warriors":
				return krystiliaRoutineProfiles("Use low-risk Ranged at the Wilderness Ice Plateau; this high-traffic slow task is a reasonable block");
			case "lesser demons":
				return krystiliaRoutineProfiles("Use low-risk demonbane melee or Ranged at a Wilderness lesser-demon spawn");
			case "magic axes":
				return krystiliaRoutineProfiles("Use low-risk Ranged in the Magic Axe Hut and keep the Wilderness lever route available");
			case "moss giants":
				return krystiliaRoutineProfiles("Use low-risk fast attacks at Wilderness moss giants; cannon only when enabled and compatible with the selected location");
			case "rogues":
				return krystiliaRoutineProfiles("Use a disposable fast-attack setup at the Rogues' Castle and minimise time spent in deep Wilderness");
			case "scorpions":
				return krystiliaRoutineProfiles("Use low-risk fast attacks at a dense Wilderness scorpion spawn; Scorpia is a separately selected boss variant");
			case "skeletons":
				return krystiliaRoutineProfiles("Use low-risk fast attacks at Wilderness skeletons; do not combine Salve and Slayer-helmet bonuses");
			case "spiders":
				return krystiliaRoutineProfiles("Use low-risk fast attacks at a dense Wilderness spider spawn; Venenatis and Spindel are separately selected boss variants");
			default:
				return null;
		}
	}

	private static Profiles krystiliaRoutineProfiles(final String method)
	{
		final SlayerTaskStrategy ranged = lowRiskRanged(method);
		final SlayerTaskStrategy melee = lowRiskMelee(method);
		final SlayerTaskStrategy cannon = lowRiskCannonRanged(method);
		return Profiles.same(ranged)
			.withCombatOptions(melee, ranged, null)
			.withCannonOptions(cannon, ranged);
	}

	private static Profiles krystiliaRangedOnlyProfiles(final String method)
	{
		final SlayerTaskStrategy ranged = lowRiskRanged(method);
		final SlayerTaskStrategy cannon = lowRiskCannonRanged(method);
		return Profiles.same(ranged)
			.withCombatOptions(null, ranged, null)
			.withCannonOptions(cannon, ranged);
	}

	private static Profiles krystiliaDragonProfiles(final String method)
	{
		final SlayerTaskStrategy ranged = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons("rune crossbow", "adamant crossbow", "dorgeshuun crossbow")
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(2)
			.foodSlots(6)
			.strictWeaponProfile(true)
			.build();
		return Profiles.same(ranged).withCombatOptions(null, ranged, null);
	}

	private static Profiles krystiliaBurstProfiles(final String method)
	{
		final SlayerTaskStrategy barrage = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons("ancient staff", "master wand", "staff of the dead", "smoke battlestaff")
			.runePouch(true)
			.tags(
				SlayerTaskStrategy.MethodTag.WILDERNESS,
				SlayerTaskStrategy.MethodTag.BURST_BARRAGE,
				SlayerTaskStrategy.MethodTag.MULTI_COMBAT
			)
			.prayerPotionSlots(4)
			.foodSlots(6)
			.strictWeaponProfile(true)
			.build();
		final SlayerTaskStrategy ranged = lowRiskRanged(
			"Use low-risk Ranged when burst and barrage are disabled"
		);
		return Profiles.same(barrage)
			.withCombatOptions(null, ranged, barrage)
			.withBurstOptions(barrage, ranged);
	}

	private static SlayerTaskStrategy lowRiskMelee(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MELEE,
			method
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons(
				"viggora s chainmace", "ursine chainmace", "zombie axe",
				"dragon scimitar", "dragon mace", "abyssal whip"
			)
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(3)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.build();
	}

	private static SlayerTaskStrategy lowRiskCannonRanged(final String method)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			method + "; use a dwarf multicannon only when the selected area permits it"
		)
			.costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.weapons("webweaver bow", "craw s bow", "magic shortbow", "rune crossbow")
			.tags(
				SlayerTaskStrategy.MethodTag.WILDERNESS,
				SlayerTaskStrategy.MethodTag.CANNON
			)
			.prayerPotionSlots(3)
			.foodSlots(8)
			.strictWeaponProfile(true)
			.build();
	}

	private static SlayerTaskStrategy lowRiskWilderness(final String taskName)
	{
		final String task = normalize(taskName);
		if (task.contains("dragon") || task.contains("archaeologist")
			|| task.contains("fanatic") || task.contains("scorpia"))
		{
			return lowRiskMagic(
				"Use a low-risk Wilderness Magic setup and carry only replaceable supplies"
			);
		}
		return lowRiskRanged(
			"Use a low-risk Wilderness ranged setup and carry only replaceable supplies"
		);
	}

	private static String normalize(final String value)
	{
		if (value == null)
		{
			return "";
		}
		return value
			.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceFirst("^the\\s+", "");
	}

	/** Official task text has accumulated singular/plural and legacy labels.
	 * Resolve those labels before research gating so an authored profile cannot
	 * disappear merely because RuneLite reports the other official spelling. */
	private static String canonicalTaskKey(final String value)
	{
		final String key = normalize(value);
		switch (key)
		{
			case "aberrant spectre": return "aberrant spectres";
			case "abyssal demon": return "abyssal demons";
			case "aquanite": return "aquanites";
			case "araxyte": return "araxytes";
			case "aviansie": return "aviansies";
			case "banshee": return "banshees";
			case "basilisk": return "basilisks";
			case "bat": return "bats";
			case "bear": return "bears";
			case "black demon": return "black demons";
			case "black dragon": return "black dragons";
			case "black knight": return "black knights";
			case "bloodvelds":
				return "bloodveld";
			case "brine rat": return "brine rats";
			case "cave bug": return "cave bugs";
			case "cave crawler": return "cave crawlers";
			case "cave horror": return "cave horrors";
			case "cave slime": return "cave slimes";
			case "chaos druid": return "chaos druids";
			case "crawling hand": return "crawling hands";
			case "crocodile": return "crocodiles";
			case "custodian stalker": return "custodian stalkers";
			case "dagannoths":
				return "dagannoth";
			case "dark warrior": return "dark warriors";
			case "earth warrior": return "earth warriors";
			case "ent": return "ents";
			case "fever spider": return "fever spiders";
			case "fire giant": return "fire giants";
			case "flesh crawler": return "fleshcrawlers";
			case "green dragon": return "green dragons";
			case "harpie bug swarm": return "harpie bug swarms";
			case "hellhound": return "hellhounds";
			case "hill giant": return "hill giants";
			case "hydra": return "hydras";
			case "ice giant": return "ice giants";
			case "ice warrior": return "ice warriors";
			case "infernal mage": return "infernal mages";
			case "jelly":
			case "jellyjellies": return "jellies";
			case "kalphite": return "kalphites";
			case "killerwatt": return "killerwatts";
			case "kurasks":
				return "kurask";
			case "lava dragon": return "lava dragons";
			case "lesser demon": return "lesser demons";
			case "lesser naguas": return "lesser nagua";
			case "magic axe": return "magic axes";
			case "mammoth": return "mammoths";
			case "mogre": return "mogres";
			case "molanisk": return "molanisks";
			case "monkey": return "monkeys";
			case "moss giant": return "moss giants";
			case "mutated zygomite": return "mutated zygomites";
			case "nechryaels":
				return "nechryael";
			case "ogre": return "ogres";
			case "pirate": return "pirates";
			case "pyrefiend": return "pyrefiends";
			case "revenant": return "revenants";
			case "rockslug": return "rockslugs";
			case "rogue": return "rogues";
			case "scorpion": return "scorpions";
			case "skeleton": return "skeletons";
			case "spider": return "spiders";
			case "suqah": return "suqahs";
			case "troll": return "trolls";
			case "vampyre": return "vampyres";
			case "werewolf": return "werewolves";
			case "wolf": return "wolves";
			case "wyrm": return "wyrms";
			case "zombie": return "zombies";
			case "minions of scabaras":
			case "scabarite":
			case "scarabites":
				return "scabarites";
			case "metal dragon":
			case "bronze dragons":
			case "iron dragons":
			case "steel dragons":
			case "mithril dragons":
			case "adamant dragons":
			case "rune dragons":
				return "metal dragons";
			default:
				return key;
		}
	}

	private static final class Profiles
	{
		private final SlayerTaskStrategy balanced;
		private final SlayerTaskStrategy fast;
		private final SlayerTaskStrategy afk;
		private final SlayerTaskStrategy profit;
		private SlayerTaskStrategy melee;
		private SlayerTaskStrategy ranged;
		private SlayerTaskStrategy magic;
		private SlayerTaskStrategy burst;
		private SlayerTaskStrategy nonBurst;
		private SlayerTaskStrategy cannon;
		private SlayerTaskStrategy nonCannon;

		private Profiles(
			final SlayerTaskStrategy balanced,
			final SlayerTaskStrategy fast,
			final SlayerTaskStrategy afk,
			final SlayerTaskStrategy profit)
		{
			this.balanced = balanced;
			this.fast = fast;
			this.afk = afk;
			this.profit = profit;
		}

		private Profiles withCombatOptions(
			final SlayerTaskStrategy melee,
			final SlayerTaskStrategy ranged,
			final SlayerTaskStrategy magic)
		{
			this.melee = melee;
			this.ranged = ranged;
			this.magic = magic;
			return this;
		}

		private Profiles withBurstOptions(
			final SlayerTaskStrategy burst,
			final SlayerTaskStrategy nonBurst)
		{
			this.burst = burst;
			this.nonBurst = nonBurst;
			return this;
		}

		private Profiles withCannonOptions(
			final SlayerTaskStrategy cannon,
			final SlayerTaskStrategy nonCannon)
		{
			this.cannon = cannon;
			this.nonCannon = nonCannon;
			return this;
		}

		private Profiles reviewed(final String date)
		{
			final Profiles copy = new Profiles(
				reviewed(balanced, date),
				reviewed(fast, date),
				reviewed(afk, date),
				reviewed(profit, date)
			);
			copy.melee = reviewed(melee, date);
			copy.ranged = reviewed(ranged, date);
			copy.magic = reviewed(magic, date);
			copy.burst = reviewed(burst, date);
			copy.nonBurst = reviewed(nonBurst, date);
			copy.cannon = reviewed(cannon, date);
			copy.nonCannon = reviewed(nonCannon, date);
			return copy;
		}

		private static SlayerTaskStrategy reviewed(
			final SlayerTaskStrategy strategy,
			final String date)
		{
			return strategy == null ? null : strategy.asReviewed(date);
		}

		private static Profiles same(final SlayerTaskStrategy strategy)
		{
			return new Profiles(strategy, strategy, strategy, strategy);
		}

		private SlayerTaskStrategy select(
			final SlayerPreference.Playstyle playstyle,
			final SlayerPreference.Cannon cannonPreference,
			final SlayerPreference.Burst burstPreference,
			final SlayerPreference.CombatStyle combatPreference)
		{
			SlayerTaskStrategy selected = selectCombatPreference(
				combatPreference
			);
			final boolean explicitCombat = selected != null;
			String selectionNote = "";

			if (!explicitCombat)
			{
				selected = selectAutomatic(playstyle);
			}


			if (!explicitCombat
				&& cannonPreference == SlayerPreference.Cannon.PREFER
				&& cannon != null)
			{
				selected = cannon;
			}

			if (!explicitCombat
				&& burstPreference == SlayerPreference.Burst.PREFER
				&& burst != null)
			{
				selected = burst;
			}

			if (violatesEnabledPreferences(
				selected,
				cannonPreference,
				burstPreference
			))
			{
				selected = safePreferenceFallback(
					selected,
					cannonPreference,
					burstPreference
				);
				selectionNote =
					"Disabled cannon/burst preferences were enforced with a reviewed compatible fallback.";
			}
			selected = applyPlaystyleLoadoutPolicy(selected, playstyle);
			final String authoredSelectionNote = selected.getSelectionNote();

			if (selectionNote.isEmpty() && explicitCombat)
			{
				selectionNote = combatPreference
					+ " applied because this task has a reviewed viable "
					+ selected.getCombatStyle().getLabel()
					+ " setup.";
			}

			if (selectionNote.isEmpty())
			{
				final String burstNote = selected.hasTag(
					SlayerTaskStrategy.MethodTag.BURST_BARRAGE
				)
					? " Burst/Barrage preference permits this method."
					: "";
				final String cannonNote = selected.hasTag(
					SlayerTaskStrategy.MethodTag.CANNON
				)
					? " Cannon preference permits this method."
					: "";
				selectionNote = "Automatic selected the reviewed "
					+ selected.getCombatStyle().getLabel()
					+ " method."
					+ burstNote
					+ cannonNote;
			}

			final String generatedSelectionNote =
				selectionNote + playstyleLoadoutNote(playstyle, selected);
			return selected.withSelectionNote(
				authoredSelectionNote == null
					|| authoredSelectionNote.trim().isEmpty()
						? generatedSelectionNote
						: generatedSelectionNote + " "
							+ authoredSelectionNote.trim()
			);
		}

		private SlayerTaskStrategy applyPlaystyleLoadoutPolicy(
			final SlayerTaskStrategy strategy,
			final SlayerPreference.Playstyle playstyle)
		{
			if (strategy == null)
			{
				return null;
			}

			if (strategy.getCostPolicy()
				== SlayerTaskStrategy.CostPolicy.LOW_RISK
				|| strategy.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS))
			{
				return strategy;
			}

			final SlayerPreference.Playstyle resolved = playstyle == null
				? SlayerPreference.Playstyle.FAST_XP
				: playstyle;

			switch (resolved)
			{
				case PROFIT:
					return strategy.withLoadoutPolicy(
						strategy.getArmourFocus(),
						SlayerTaskStrategy.CostPolicy.EFFICIENT
					);
				case FAST_XP:
				default:
					return strategy.withLoadoutPolicy(
						strategy.getArmourFocus()
								== SlayerTaskStrategy.ArmourFocus.MAGIC_DEFENCE
							? SlayerTaskStrategy.ArmourFocus.MAGIC_DEFENCE
							: SlayerTaskStrategy.ArmourFocus.DAMAGE,
						SlayerTaskStrategy.CostPolicy.MAX_DPS
					);
			}
		}

		private String playstyleLoadoutNote(
			final SlayerPreference.Playstyle playstyle,
			final SlayerTaskStrategy strategy)
		{
			if (strategy != null
				&& (strategy.getCostPolicy()
					== SlayerTaskStrategy.CostPolicy.LOW_RISK
					|| strategy.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS)))
			{
				return " Wilderness safety keeps the reviewed LOW_RISK equipment policy.";
			}

			final SlayerPreference.Playstyle resolved = playstyle == null
				? SlayerPreference.Playstyle.FAST_XP
				: playstyle;

			switch (resolved)
			{
				case PROFIT:
					return " Profit uses the efficient-cost equipment policy.";
				case FAST_XP:
				default:
					return " Fast XP forces damage gear and MAX_DPS equipment without considering item or charge cost.";
			}
		}

		private SlayerTaskStrategy selectCombatPreference(
			final SlayerPreference.CombatStyle combatPreference)
		{
			if (combatPreference == null
				|| combatPreference == SlayerPreference.CombatStyle.AUTOMATIC)
			{
				return null;
			}

			switch (combatPreference)
			{
				case PREFER_MELEE: return melee;
				case PREFER_RANGED: return ranged;
				case PREFER_MAGIC: return magic;
				case AUTOMATIC:
				default: return null;
			}
		}

		private boolean violatesEnabledPreferences(
			final SlayerTaskStrategy strategy,
			final SlayerPreference.Cannon cannonPreference,
			final SlayerPreference.Burst burstPreference)
		{
			return strategy != null
				&& ((cannonPreference == SlayerPreference.Cannon.NEVER
					&& strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
					|| (burstPreference == SlayerPreference.Burst.NEVER
					&& strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE)));
		}

		private SlayerTaskStrategy safePreferenceFallback(
			final SlayerTaskStrategy current,
			final SlayerPreference.Cannon cannonPreference,
			final SlayerPreference.Burst burstPreference)
		{
			final SlayerTaskStrategy[] candidates = new SlayerTaskStrategy[]{
				current, nonCannon, nonBurst, melee, ranged, magic,
				balanced, fast, afk, profit, cannon, burst
			};
			for (final SlayerTaskStrategy candidate : candidates)
			{
				if (candidate != null
					&& !violatesEnabledPreferences(
						candidate,
						cannonPreference,
						burstPreference
					))
				{
					return candidate;
				}
			}
			return current;
		}

		private SlayerTaskStrategy safeNonBurstFallback()
		{
			if (nonBurst != null
				&& !nonBurst.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE))
			{
				return nonBurst;
			}

			final SlayerTaskStrategy[] candidates = new SlayerTaskStrategy[]{
				balanced, fast, afk, profit, melee, ranged, magic
			};
			for (final SlayerTaskStrategy candidate : candidates)
			{
				if (candidate != null
					&& !candidate.hasTag(
						SlayerTaskStrategy.MethodTag.BURST_BARRAGE
					))
				{
					return candidate;
				}
			}

			return balanced;
		}

		private SlayerTaskStrategy safeNonCannonFallback()
		{
			if (nonCannon != null
				&& !nonCannon.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
			{
				return nonCannon;
			}

			final SlayerTaskStrategy[] candidates = new SlayerTaskStrategy[]{
				balanced, fast, afk, profit, melee, ranged, magic
			};
			for (final SlayerTaskStrategy candidate : candidates)
			{
				if (candidate != null
					&& !candidate.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
				{
					return candidate;
				}
			}

			return balanced;
		}

		private SlayerTaskStrategy selectAutomatic(
			final SlayerPreference.Playstyle playstyle)
		{
			if (playstyle == null) return fast;
			switch (playstyle)
			{
				case PROFIT: return profit;
				case FAST_XP:
				default: return fast;
			}
		}
	}
}
