package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Global encounter identity catalog.
 *
 * <p>Every ordinary Slayer assignment resolves to STANDARD_TASK unless the
 * player explicitly selects one of the exact boss alternatives registered for
 * that assignment. Direct boss assignments are also recognised here so they
 * are always routed through the strict boss route map instead of the ordinary
 * monster fallback.</p>
 */
public final class SlayerTaskVariantCatalog
{
	private static final List<SlayerTaskVariant> STANDARD_ONLY =
		Collections.singletonList(SlayerTaskVariant.STANDARD_TASK);

	private static final List<BossDefinition> BOSS_DEFINITIONS =
		createBossDefinitions();
	private static final Map<String, List<BossDefinition>> BOSSES_BY_ASSIGNMENT =
		indexBossesByAssignment();
	private static final Map<SlayerTaskVariant, BossDefinition> BOSSES_BY_VARIANT =
		indexBossesByVariant();
	private static final Map<String, BossDefinition> BOSSES_BY_ENCOUNTER =
		indexBossesByEncounter();
	private static final Map<String, DirectBossDefinition> DIRECT_BOSSES =
		createDirectBosses();

	private SlayerTaskVariantCatalog()
	{
	}

	public static List<SlayerTaskVariant> getAvailableVariants(
		final String taskName)
	{
		final BossDefinition directVariant = BOSSES_BY_ENCOUNTER.get(
			normalize(taskName)
		);
		if (directVariant != null)
		{
			return Collections.singletonList(directVariant.variant);
		}

		final List<BossDefinition> bosses = BOSSES_BY_ASSIGNMENT.get(
			normalize(taskName)
		);
		if (bosses == null || bosses.isEmpty())
		{
			return STANDARD_ONLY;
		}

		final List<SlayerTaskVariant> variants = new ArrayList<>();
		variants.add(SlayerTaskVariant.STANDARD_TASK);
		for (final BossDefinition boss : bosses)
		{
			if (!variants.contains(boss.variant))
			{
				variants.add(boss.variant);
			}
		}
		return Collections.unmodifiableList(variants);
	}

	/**
	 * Returns the only valid starting variant for a detected direct boss task,
	 * otherwise STANDARD_TASK for an ordinary assignment.
	 */
	public static SlayerTaskVariant getDefaultVariant(final String taskName)
	{
		final BossDefinition directVariant = BOSSES_BY_ENCOUNTER.get(
			normalize(taskName)
		);
		return directVariant == null
			? SlayerTaskVariant.STANDARD_TASK
			: directVariant.variant;
	}

	public static boolean supportsBossVariant(final String taskName)
	{
		final List<BossDefinition> bosses = BOSSES_BY_ASSIGNMENT.get(
			normalize(taskName)
		);
		return bosses != null && !bosses.isEmpty();
	}

	public static boolean isDirectBossTask(final String taskName)
	{
		final String key = normalize(taskName);
		return BOSSES_BY_ENCOUNTER.containsKey(key)
			|| DIRECT_BOSSES.containsKey(key);
	}

	public static String getOptionLabel(
		final String taskName,
		final SlayerTaskVariant variant)
	{
		if (variant != null && variant.isBoss())
		{
			final BossDefinition boss = findBossForAssignment(
				taskName,
				variant
			);
			if (boss != null)
			{
				return boss.displayName;
			}

			final BossDefinition direct = BOSSES_BY_ENCOUNTER.get(
				normalize(taskName)
			);
			if (direct != null && direct.variant == variant)
			{
				return direct.displayName;
			}
		}

		return SlayerEncounterStandards.cleanEncounterLabel(taskName);
	}

	public static ResolvedTarget resolve(
		final String taskName,
		final SlayerTaskVariant requestedVariant,
		final SlayerRecommendation recommendation,
		final SlayerPlusConfig config)
	{
		final SlayerTaskVariant requested = requestedVariant == null
			? getDefaultVariant(taskName)
			: requestedVariant;

		if (requested.isBoss())
		{
			BossDefinition boss = findBossForAssignment(taskName, requested);
			if (boss == null)
			{
				final BossDefinition direct = BOSSES_BY_ENCOUNTER.get(
					normalize(taskName)
				);
				if (direct != null && direct.variant == requested)
				{
					boss = direct;
				}
			}

			/*
			 * Fail closed. A stale/mismatched boss enum must never be translated
			 * back into the ordinary monster route.
			 */
			if (boss == null)
			{
				return ResolvedTarget.invalidBoss(taskName, requested);
			}
			return boss.resolve(config, recommendation);
		}

		final BossDefinition directVariant = BOSSES_BY_ENCOUNTER.get(
			normalize(taskName)
		);
		if (directVariant != null)
		{
			return directVariant.resolve(config, recommendation);
		}

		final DirectBossDefinition directBoss = DIRECT_BOSSES.get(
			normalize(taskName)
		);
		if (directBoss != null)
		{
			return directBoss.resolve(taskName, recommendation, config);
		}

		final SlayerTaskStrategy strategy =
			recommendation != null && recommendation.hasTaskStrategy()
				? recommendation.getStrategy()
				: SlayerTaskStrategyCatalog.legacy(
					recommendation == null ? "" : recommendation.getMethod()
				);

		return new ResolvedTarget(
			taskName,
			SlayerEncounterStandards.cleanEncounterLabel(taskName),
			recommendation == null ? "" : recommendation.getLocation(),
			recommendation == null ? "" : recommendation.getTravel(),
			recommendation == null ? "" : recommendation.getCannon(),
			recommendation == null ? "" : recommendation.getRestriction(),
			strategy,
			SlayerTaskVariant.STANDARD_TASK,
			false,
			true
		);
	}

	private static BossDefinition findBossForAssignment(
		final String taskName,
		final SlayerTaskVariant variant)
	{
		if (variant == null || !variant.isBoss())
		{
			return null;
		}
		final List<BossDefinition> bosses = BOSSES_BY_ASSIGNMENT.get(
			normalize(taskName)
		);
		if (bosses == null)
		{
			return null;
		}
		for (final BossDefinition boss : bosses)
		{
			if (boss.variant == variant)
			{
				return boss;
			}
		}
		return null;
	}

	private static List<BossDefinition> createBossDefinitions()
	{
		final List<BossDefinition> bosses = new ArrayList<>();

		bosses.add(boss(SlayerTaskVariant.ABYSSAL_SIRE,
			"Abyssal Sire", "Abyssal Nexus",
			"Fairy ring DIP lands inside the Abyssal Nexus", "Not allowed",
			"Requires 85 Slayer and an Abyssal demon task",
			"Abyssal demons"));
		bosses.add(boss(SlayerTaskVariant.ALCHEMICAL_HYDRA,
			"Alchemical Hydra", "Karuulm Slayer Dungeon",
			"Rada's blessing to Mount Karuulm, then descend to the Hydra level", "Not allowed",
			"Requires 95 Slayer and a Hydra task",
			"Hydras"));
		bosses.add(boss(SlayerTaskVariant.AMOXLIATL,
			"Amoxliatl", "Ruins of Tapoyauik",
			"Travel to the Ruins of Tapoyauik and use the unlocked route to Amoxliatl", "Not allowed",
			"Lesser Nagua boss alternative",
			"Lesser Nagua"));
		bosses.add(boss(SlayerTaskVariant.ARAXXOR,
			"Araxxor", "Morytania Spider Cave",
			"Travel to the Morytania Spider Cave and enter Araxxor's lair", "Not allowed",
			"Requires an eligible Araxyte or Spider task",
			"Araxytes", "Spiders"));
		bosses.add(boss(SlayerTaskVariant.ARTIO,
			"Artio", "Hunter's End",
			"Travel through the Wilderness to Hunter's End", "Not allowed",
			"Wilderness boss alternative for Bears",
			"Bears"));
		bosses.add(boss(SlayerTaskVariant.CALLISTO,
			"Callisto", "Callisto's Den",
			"Travel through the Wilderness to Callisto's Den", "Not allowed",
			"Wilderness boss alternative for Bears",
			"Bears"));
		bosses.add(boss(SlayerTaskVariant.CERBERUS,
			"Cerberus", "Cerberus' Lair",
			"Key master teleport, Taverley house portal, or Falador west shortcut", "Not allowed",
			"Requires 91 Slayer and an active Hellhound or Cerberus task",
			"Hellhounds"));
		bosses.add(boss(SlayerTaskVariant.DAGANNOTH_KINGS,
			"Dagannoth Kings", "Waterbirth Island Dungeon",
			"Waterbirth teleport or travel from Rellekka, then descend to the Kings", "Not allowed",
			"Boss alternative for Dagannoth tasks",
			"Dagannoth", "Dagannoths"));
		bosses.add(boss(SlayerTaskVariant.DERANGED_ARCHAEOLOGIST,
			"Deranged Archaeologist", "Fossil Island",
			"Use a digsite pendant to Fossil Island, then travel south-east to the ruins", "Not allowed",
			"Non-Wilderness alternative for a directly assigned Crazy archaeologist task",
			"Crazy Archaeologist", "Crazy Archaeologists"));
		bosses.add(boss(SlayerTaskVariant.GROTESQUE_GUARDIANS,
			"Grotesque Guardians", "Slayer Tower",
			"Slayer ring to Slayer Tower, then climb to the rooftop", "Not allowed",
			"Requires a Gargoyle task and access to the rooftop",
			"Gargoyles"));
		bosses.add(boss(SlayerTaskVariant.KRIL_TSUTSAROTH,
			"K'ril Tsutsaroth", "God Wars Dungeon",
			"Trollheim teleport, then enter the Zamorak encampment", "Not allowed",
			"Boss alternative for Greater demon tasks",
			"Greater demons"));
		bosses.add(boss(SlayerTaskVariant.KALPHITE_QUEEN,
			"Kalphite Queen", "Kalphite Lair",
			"Use fairy ring BIQ or Desert amulet 4, then take both rope descents", "Not allowed",
			"Boss alternative for Kalphite tasks",
			"Kalphites"));
		bosses.add(boss(SlayerTaskVariant.KING_BLACK_DRAGON,
			"King Black Dragon", "King Black Dragon Lair",
			"Use a Wilderness KBD entrance, then enter the lair", "Not allowed",
			"Boss alternative for Black dragon tasks",
			"Black dragons"));
		bosses.add(boss(SlayerTaskVariant.KRAKEN_BOSS,
			"Kraken", "Kraken Cove",
			"Travel to Kraken Cove and enter the boss instance", "Not allowed",
			"Requires a Cave kraken task",
			"Cave kraken"));
		bosses.add(boss(SlayerTaskVariant.KREEARRA,
			"Kree'arra", "God Wars Dungeon",
			"Trollheim teleport, then enter the Armadyl encampment", "Not allowed",
			"Boss alternative for Aviansie tasks",
			"Aviansies"));
		bosses.add(boss(SlayerTaskVariant.SARACHNIS,
			"Sarachnis", "Forthos Dungeon",
			"Travel to the Forthos Ruin and descend to Sarachnis", "Not allowed",
			"Boss alternative for Spider tasks",
			"Spiders"));
		bosses.add(boss(SlayerTaskVariant.SCORPIA,
			"Scorpia", "Scorpia's Cave",
			"Travel through the deep Wilderness to Scorpia's Cave", "Not allowed",
			"Wilderness boss alternative for Scorpion tasks",
			"Scorpions"));
		bosses.add(boss(SlayerTaskVariant.SKOTIZO,
			"Skotizo", "Skotizo's Lair",
			"Use a dark totem at the Catacombs altar", "Not allowed",
			"Boss alternative for Black demon or Greater demon tasks where the assignment location permits it",
			"Black demons", "Greater demons"));
		bosses.add(boss(SlayerTaskVariant.THERMONUCLEAR_SMOKE_DEVIL,
			"Thermonuclear smoke devil", "Smoke Devil Dungeon",
			"Fairy ring BKP or Castle Wars, then enter the Smoke Devil Dungeon and use the boss-room crevice", "Not allowed",
			"Requires 93 Slayer and an active Smoke Devil task",
			"Smoke devils"));
		bosses.add(boss(SlayerTaskVariant.TZTOK_JAD,
			"TzTok-Jad", "TzHaar Fight Cave",
			"Travel to Mor Ul Rek and enter the Fight Cave", "Not allowed",
			"TzHaar boss alternative",
			"Tzhaar"));
		bosses.add(boss(SlayerTaskVariant.TZKAL_ZUK,
			"TzKal-Zuk", "Inferno",
			"Travel to Mor Ul Rek and enter the Inferno", "Not allowed",
			"TzHaar boss alternative requiring Inferno access",
			"Tzhaar"));
		bosses.add(boss(SlayerTaskVariant.SPINDEL,
			"Spindel", "Web Chasm",
			"Travel through the Wilderness to the Web Chasm", "Not allowed",
			"Wilderness Spider boss alternative",
			"Spiders"));
		bosses.add(boss(SlayerTaskVariant.VENENATIS,
			"Venenatis", "Silk Chasm",
			"Travel through the Wilderness to the Silk Chasm", "Not allowed",
			"Wilderness Spider boss alternative",
			"Spiders"));
		bosses.add(boss(SlayerTaskVariant.CALVARION,
			"Calvar'ion", "Skeletal Tomb",
			"Travel through the Wilderness to the Skeletal Tomb", "Not allowed",
			"Wilderness Skeleton boss alternative",
			"Skeletons"));
		bosses.add(boss(SlayerTaskVariant.VETION,
			"Vet'ion", "Vet'ion's Rest",
			"Travel through the Wilderness to Vet'ion's Rest", "Not allowed",
			"Wilderness Skeleton boss alternative",
			"Skeletons"));
		bosses.add(boss(SlayerTaskVariant.VORKATH,
			"Vorkath", "Ungael",
			"Use the Fremennik route to Ungael", "Not allowed",
			"Boss alternative for eligible Blue dragon or Zombie tasks",
			"Blue dragons", "Zombies"));
		bosses.add(boss(SlayerTaskVariant.SCURRIUS,
			"Scurrius", "Varrock Sewers",
			"Varrock teleport, then enter the sewers and Scurrius' lair", "Not allowed",
			"Boss alternative for Rat tasks",
			"Rats"));
		bosses.add(boss(SlayerTaskVariant.OBOR,
			"Obor", "Edgeville Dungeon",
			"Edgeville teleport, then enter the giant-key lair", "Not allowed",
			"Boss alternative for Hill giant tasks",
			"Hill giants"));
		bosses.add(boss(SlayerTaskVariant.BRYOPHYTA,
			"Bryophyta", "Varrock Sewers",
			"Varrock teleport, then enter Bryophyta's mossy-key lair", "Not allowed",
			"Boss alternative for Moss giant tasks",
			"Moss giants"));
		bosses.add(boss(SlayerTaskVariant.BRUTUS,
			"Brutus", "Lumbridge cow field",
			"Use a cowbell amulet or Lumbridge teleport, then enter Brutus' cow-field instance", "Not allowed",
			"Requires completion of The Ides of Milk; boss alternative for Cow tasks",
			"Cows"));
		bosses.add(boss(SlayerTaskVariant.DEMONIC_GORILLAS,
			"Demonic gorillas", "Crash Site Cavern",
			"Use a royal seed pod or spirit tree to the Gnome Stronghold, then enter the Crash Site Cavern", "Not allowed",
			"Requires completion of Monkey Madness II; demi-boss alternative for Black demon or Monkey tasks",
			"Black demons", "Monkeys"));
		bosses.add(boss(SlayerTaskVariant.TORMENTED_DEMONS,
			"Tormented demons", "Ancient Guthixian Temple",
			"Use a Guthixian temple teleport; games necklace to Tears of Guthix is the fallback", "Not allowed",
			"Requires completion of While Guthix Sleeps; demi-boss alternative for Greater demon tasks",
			"Greater demons"));
		bosses.add(boss(SlayerTaskVariant.ROYAL_TITANS,
			"Royal Titans", "Asgarnian Ice Dungeon",
			"Use a giantsoul amulet; fairy ring AIQ is the fallback to the Asgarnian Ice Dungeon", "Not allowed",
			"Boss alternative for Fire giant or Ice giant tasks",
			"Fire giants", "Ice giants"));
		bosses.add(boss(SlayerTaskVariant.SHELLBANE_GRYPHON,
			"Shellbane Gryphon", "The Great Conch",
			"Fairy ring CJQ to the Great Conch, then run north to the boss cave", "Not allowed",
			"Requires an eligible Gryphon task",
			"Gryphons"));

		return Collections.unmodifiableList(bosses);
	}

	private static BossDefinition boss(
		final SlayerTaskVariant variant,
		final String encounterName,
		final String location,
		final String travel,
		final String cannon,
		final String restriction,
		final String... assignments)
	{
		return new BossDefinition(
			variant,
			encounterName,
			SlayerEncounterStandards.cleanEncounterLabel(encounterName),
			location,
			travel,
			cannon,
			restriction,
			assignments
		);
	}

	private static Map<String, List<BossDefinition>> indexBossesByAssignment()
	{
		final Map<String, List<BossDefinition>> index = new LinkedHashMap<>();
		for (final BossDefinition boss : BOSS_DEFINITIONS)
		{
			for (final String assignment : boss.assignments)
			{
				final String key = normalize(assignment);
				List<BossDefinition> entries = index.get(key);
				if (entries == null)
				{
					entries = new ArrayList<>();
					index.put(key, entries);
				}
				entries.add(boss);
			}
		}
		for (final Map.Entry<String, List<BossDefinition>> entry : index.entrySet())
		{
			entry.setValue(Collections.unmodifiableList(entry.getValue()));
		}
		return Collections.unmodifiableMap(index);
	}

	private static Map<SlayerTaskVariant, BossDefinition> indexBossesByVariant()
	{
		final Map<SlayerTaskVariant, BossDefinition> index = new LinkedHashMap<>();
		for (final BossDefinition boss : BOSS_DEFINITIONS)
		{
			if (index.put(boss.variant, boss) != null)
			{
				throw new IllegalStateException(
					"Duplicate Slayer boss variant: " + boss.variant
				);
			}
		}
		return Collections.unmodifiableMap(index);
	}

	private static Map<String, BossDefinition> indexBossesByEncounter()
	{
		final Map<String, BossDefinition> index = new LinkedHashMap<>();
		for (final BossDefinition boss : BOSS_DEFINITIONS)
		{
			index.put(normalize(boss.encounterName), boss);
		}

		/*
		 * RuneLite/Jagex task text does not always use the same title as the
		 * encounter catalog. Keep those task-name aliases attached to the exact
		 * concrete boss variant so a direct boss assignment can never be treated
		 * as STANDARD_TASK.
		 */
		putEncounterAlias(index, "Cave Kraken Boss", SlayerTaskVariant.KRAKEN_BOSS);
		putEncounterAlias(index, "The Cave Kraken Boss", SlayerTaskVariant.KRAKEN_BOSS);
		return Collections.unmodifiableMap(index);
	}

	private static void putEncounterAlias(
		final Map<String, BossDefinition> index,
		final String alias,
		final SlayerTaskVariant variant)
	{
		for (final BossDefinition boss : BOSS_DEFINITIONS)
		{
			if (boss.variant == variant)
			{
				index.put(normalize(alias), boss);
				return;
			}
		}
	}

	private static Map<String, DirectBossDefinition> createDirectBosses()
	{
		final Map<String, DirectBossDefinition> bosses = new LinkedHashMap<>();
		putDirect(bosses, "Barrows Brothers", "Barrows");
		putDirect(bosses, "Chaos Elemental", "Wilderness");
		putDirect(bosses, "Chaos Fanatic", "Wilderness");
		putDirect(bosses, "Crazy Archaeologist", "Wilderness");
		putDirect(bosses, "Crazy Archaeologists", "Wilderness");
		putDirect(bosses, "Deranged Archaeologist", "Fossil Island");
		putDirect(bosses, "Duke Sucellus", "Ghorrock Dungeon");
		putDirect(bosses, "General Graardor", "God Wars Dungeon");
		putDirect(bosses, "Giant Mole", "Falador Mole Lair");
		putDirect(bosses, "Maggot King", "Maggot King's lair");
		putDirect(bosses, "Phantom Muspah", "Ghorrock Dungeon");
		putDirect(bosses, "Leviathan", "The Scar");
		putDirect(bosses, "The Whisperer", "Lassar Undercity");
		putDirect(bosses, "Vardorvis", "Stranglewood Temple");
		putDirect(bosses, "Commander Zilyana", "God Wars Dungeon");
		putDirect(bosses, "Zulrah", "Zul-Andra");
		return Collections.unmodifiableMap(bosses);
	}

	private static void putDirect(
		final Map<String, DirectBossDefinition> bosses,
		final String encounterName,
		final String location)
	{
		bosses.put(
			normalize(encounterName),
			new DirectBossDefinition(encounterName, location)
		);
	}

	public static List<EncounterDefinition> getBossDefinitions()
	{
		final List<EncounterDefinition> definitions = new ArrayList<>();
		for (final BossDefinition boss : BOSS_DEFINITIONS)
		{
			if (boss.assignments.isEmpty())
			{
				definitions.add(new EncounterDefinition(
					"", boss.encounterName, boss.displayName,
					boss.location, boss.variant
				));
				continue;
			}
			for (final String assignment : boss.assignments)
			{
				definitions.add(new EncounterDefinition(
					assignment,
					boss.encounterName,
					boss.displayName,
					boss.location,
					boss.variant
				));
			}
		}
		return Collections.unmodifiableList(definitions);
	}

	/** Direct boss assignments are audited separately from selectable variants. */
	public static List<EncounterDefinition> getDirectBossDefinitions()
	{
		final List<EncounterDefinition> definitions = new ArrayList<>();
		for (final DirectBossDefinition boss : DIRECT_BOSSES.values())
		{
			definitions.add(new EncounterDefinition(
				"", boss.encounterName,
				SlayerEncounterStandards.cleanEncounterLabel(boss.encounterName),
				boss.location, SlayerTaskVariant.STANDARD_TASK
			));
		}
		return Collections.unmodifiableList(definitions);
	}

	private static SlayerTaskStrategy resolveStrategy(
		final String taskName,
		final String location,
		final SlayerPlusConfig config)
	{
		return SlayerTaskStrategyCatalog.resolve(
			taskName,
			config == null
				? SlayerPreference.Playstyle.FAST_XP
				: config.playstyle(),
			config == null
				? SlayerPreference.Cannon.ALLOW
				: config.cannonPreference(),
			config == null
				? SlayerPreference.Burst.ALLOW
				: config.burstPreference(),
			config == null
				? SlayerPreference.CombatStyle.AUTOMATIC
				: config.combatStylePreference(),
			location,
			false
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

	private static final class BossDefinition
	{
		private final SlayerTaskVariant variant;
		private final String encounterName;
		private final String displayName;
		private final String location;
		private final String travel;
		private final String cannon;
		private final String restriction;
		private final Set<String> assignments;

		private BossDefinition(
			final SlayerTaskVariant variant,
			final String encounterName,
			final String displayName,
			final String location,
			final String travel,
			final String cannon,
			final String restriction,
			final String... assignments)
		{
			if (variant == null || !variant.isBoss())
			{
				throw new IllegalArgumentException("Boss definition requires a boss variant");
			}
			this.variant = variant;
			this.encounterName = encounterName;
			this.displayName = displayName;
			this.location = location;
			this.travel = travel;
			this.cannon = cannon;
			this.restriction = restriction;
			this.assignments = Collections.unmodifiableSet(
				new LinkedHashSet<>(Arrays.asList(assignments))
			);
		}

		private ResolvedTarget resolve(
			final SlayerPlusConfig config,
			final SlayerRecommendation recommendation)
		{
			final SlayerTaskStrategy catalogStrategy =
				resolveStrategy(encounterName, location, config);
			final SlayerTaskStrategy recommendationStrategy =
				recommendation != null
					&& recommendation.hasTaskStrategy()
					&& recommendation.getStrategy().isBoss()
					&& normalize(recommendation.getLocation()).equals(
						normalize(location)
					)
						? recommendation.getStrategy()
						: null;
			return new ResolvedTarget(
				encounterName,
				displayName,
				location,
				travel,
				cannon,
				restriction,
				recommendationStrategy == null
					? catalogStrategy : recommendationStrategy,
				variant,
				true,
				true
			);
		}
	}

	private static final class DirectBossDefinition
	{
		private final String encounterName;
		private final String location;

		private DirectBossDefinition(
			final String encounterName,
			final String location)
		{
			this.encounterName = encounterName;
			this.location = location;
		}

		private ResolvedTarget resolve(
			final String detectedTaskName,
			final SlayerRecommendation recommendation,
			final SlayerPlusConfig config)
		{
			final String resolvedLocation = location == null || location.trim().isEmpty()
				? recommendation == null ? "" : recommendation.getLocation()
				: location;
			return new ResolvedTarget(
				encounterName,
				SlayerEncounterStandards.cleanEncounterLabel(detectedTaskName),
				resolvedLocation,
				recommendation == null ? "" : recommendation.getTravel(),
				recommendation == null ? "" : recommendation.getCannon(),
				recommendation == null ? "" : recommendation.getRestriction(),
				resolveStrategy(encounterName, resolvedLocation, config),
				SlayerTaskVariant.STANDARD_TASK,
				true,
				true
			);
		}
	}

	/**
	 * Compatibility description used by the global encounter standards/audit
	 * layer. Keeping this type in the shared variant catalog lets validators
	 * reason about encounter identity without duplicating boss-specific logic.
	 */
	public static final class EncounterDefinition
	{
		private final String assignmentName;
		private final String encounterName;
		private final String displayName;
		private final String location;
		private final SlayerTaskVariant variant;

		public EncounterDefinition(
			final String assignmentName,
			final String encounterName,
			final String displayName,
			final String location,
			final SlayerTaskVariant variant)
		{
			this.assignmentName = safeDefinition(assignmentName);
			this.encounterName = safeDefinition(encounterName);
			this.displayName = safeDefinition(displayName);
			this.location = safeDefinition(location);
			this.variant = variant == null
				? SlayerTaskVariant.STANDARD_TASK
				: variant;
		}

		public String getAssignmentName() { return assignmentName; }
		public String getTaskName() { return encounterName; }
		public String getEncounterName() { return encounterName; }
		public String getTargetName() { return encounterName; }
		public String getDisplayName() { return displayName; }
		public String getOptionLabel() { return displayName; }
		public String getLocation() { return location; }
		public SlayerTaskVariant getVariant() { return variant; }
		public boolean isBoss() { return variant != null && variant.isBoss(); }

		private static String safeDefinition(final String value)
		{
			return value == null ? "" : value.trim();
		}
	}

	public static final class ResolvedTarget
	{
		private final String taskName;
		private final String displayName;
		private final String location;
		private final String travel;
		private final String cannon;
		private final String restriction;
		private final SlayerTaskStrategy strategy;
		private final SlayerTaskVariant variant;
		private final boolean bossEncounter;
		private final boolean valid;

		private ResolvedTarget(
			final String taskName,
			final String displayName,
			final String location,
			final String travel,
			final String cannon,
			final String restriction,
			final SlayerTaskStrategy strategy,
			final SlayerTaskVariant variant,
			final boolean bossEncounter,
			final boolean valid)
		{
			this.taskName = safe(taskName, "Unknown task");
			this.displayName = safe(displayName, this.taskName);
			this.location = safe(location, "");
			this.travel = safe(travel, "");
			this.cannon = safe(cannon, "");
			this.restriction = safe(restriction, "");
			this.strategy = strategy;
			this.variant = variant == null
				? SlayerTaskVariant.STANDARD_TASK
				: variant;
			this.bossEncounter = bossEncounter;
			this.valid = valid;
		}

		private static ResolvedTarget invalidBoss(
			final String assignmentName,
			final SlayerTaskVariant variant)
		{
			return new ResolvedTarget(
				assignmentName,
				"Boss selection unavailable",
				"",
				"",
				"",
				"Boss variant does not belong to this assignment",
				null,
				variant,
				true,
				false
			);
		}

		public String getTaskName() { return taskName; }
		public String getDisplayName() { return displayName; }
		public String getLocation() { return location; }
		public String getTravel() { return travel; }
		public String getCannon() { return cannon; }
		public String getRestriction() { return restriction; }
		public SlayerTaskStrategy getStrategy() { return strategy; }
		public SlayerTaskVariant getVariant() { return variant; }
		public boolean isBoss() { return bossEncounter; }
		public boolean isValid() { return valid; }

		private static String safe(
			final String value,
			final String fallback)
		{
			return value == null || value.trim().isEmpty()
				? fallback
				: value.trim();
		}
	}
}
