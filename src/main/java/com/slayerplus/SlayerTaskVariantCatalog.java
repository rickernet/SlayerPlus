package com.slayerplus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

	private static final String VARIANT_RESOURCE =
		"/com/slayerplus/slayer-task-variants.tsv";
	private static final VariantResourceData VARIANT_DATA = loadVariantData();
	private static final List<BossDefinition> BOSS_DEFINITIONS =
		VARIANT_DATA.bosses;
	private static final Map<String, List<BossDefinition>> BOSSES_BY_ASSIGNMENT =
		indexBossesByAssignment();
	private static final Map<SlayerTaskVariant, BossDefinition> BOSSES_BY_VARIANT =
		indexBossesByVariant();
	private static final Map<String, BossDefinition> BOSSES_BY_ENCOUNTER =
		indexBossesByEncounter();
	private static final Map<String, DirectBossDefinition> DIRECT_BOSSES =
		VARIANT_DATA.directBosses;

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

	private static VariantResourceData loadVariantData()
	{
		final List<BossDefinition> bosses = new ArrayList<>();
		final Map<String, DirectBossDefinition> directBosses = new LinkedHashMap<>();
		try (InputStream stream = SlayerTaskVariantCatalog.class
			.getResourceAsStream(VARIANT_RESOURCE))
		{
			if (stream == null)
			{
				throw new IllegalStateException(
					"Missing task-variant resource " + VARIANT_RESOURCE);
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				stream, StandardCharsets.UTF_8)))
			{
				String line;
				int lineNumber = 0;
				while ((line = reader.readLine()) != null)
				{
					lineNumber++;
					if (line.isEmpty() || line.charAt(0) == '#')
					{
						continue;
					}
					final String[] fields = line.split("\\t", -1);
					if (fields.length == 8 && fields[0].equals("B"))
					{
						final String[] assignments = decode(fields[7]).isEmpty()
							? new String[0] : decode(fields[7]).split("\\|", -1);
						bosses.add(new BossDefinition(
							SlayerTaskVariant.valueOf(fields[1]),
							decode(fields[2]),
							SlayerEncounterStandards.cleanEncounterLabel(decode(fields[2])),
							decode(fields[3]), decode(fields[4]), decode(fields[5]),
							decode(fields[6]), assignments));
					}
					else if (fields.length == 3 && fields[0].equals("D"))
					{
						final String encounter = decode(fields[1]);
						directBosses.put(normalize(encounter),
							new DirectBossDefinition(encounter, decode(fields[2])));
					}
					else
					{
						throw new IllegalStateException(
							"Invalid task-variant resource line " + lineNumber);
					}
				}
			}
		}
		catch (final IOException | RuntimeException ex)
		{
			throw new IllegalStateException("Unable to load task variants", ex);
		}
		if (bosses.isEmpty() || directBosses.isEmpty())
		{
			throw new IllegalStateException("Task-variant resource is empty");
		}
		return new VariantResourceData(
			Collections.unmodifiableList(bosses),
			Collections.unmodifiableMap(directBosses));
	}

	private static String decode(final String value)
	{
		return value.replace("%0A", "\n").replace("%09", "\t")
			.replace("%25", "%");
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

	private static final class VariantResourceData
	{
		private final List<BossDefinition> bosses;
		private final Map<String, DirectBossDefinition> directBosses;

		private VariantResourceData(
			final List<BossDefinition> bosses,
			final Map<String, DirectBossDefinition> directBosses)
		{
			this.bosses = bosses;
			this.directBosses = directBosses;
		}
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

