package com.slayerplus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cross-catalog regression checks for SlayerPlus.
 *
 * <p>The startup audit is intentionally cheap: it validates static catalog
 * contracts that should never depend on live RuneLite state. The deep audit is
 * intended for development/tests and exhaustively resolves the current task
 * matrix without adding that cost to normal plugin startup.</p>
 */
public final class SlayerCatalogRegressionValidator
{
	private SlayerCatalogRegressionValidator()
	{
	}

	/** Fast, deterministic checks safe to run once during plugin startup. */
	public static void validateStartupOrThrow()
	{
		validateResearchStrategyRegistryParity();
		SlayerTaskNpcCatalog.validateOrThrow();
		TravelRoutes.validateOrThrow();
		SlayerEncounterStandards.validateBossDefinitions(
			VariantCatalog.getBossDefinitions()
		);
		validateBossRouteIsolation();
		validateCriticalMethodContracts();
	}

	/**
	 * Exhaustive development audit. Do not call this every tick or every normal
	 * refresh; it intentionally walks every selectable preference combination.
	 */
	public static List<String> validateDeep()
	{
		final List<String> failures = new ArrayList<>();
		try
		{
			validateStartupOrThrow();
		}
		catch (RuntimeException ex)
		{
			failures.add("startup contract: " + safeMessage(ex));
		}

		try
		{
			validateBankStartRouteCoverage();
		}
		catch (RuntimeException ex)
		{
			failures.add("route coverage: " + safeMessage(ex));
		}

		final List<String> strategyErrors = SlayerTaskStrategyValidator.validate();
		failures.addAll(strategyErrors);

		try
		{
			SlayerMethodRuleCoverage.validateCurrentCatalog();
		}
		catch (RuntimeException ex)
		{
			failures.add("method coverage: " + safeMessage(ex));
		}

		final Set<String> validatedCoverage = new LinkedHashSet<>();
		for (final String taskName : SlayerTaskStrategyCatalog.getCurrentTaskNames())
		{
			final Set<String> locations = new LinkedHashSet<>();
			locations.add("Not restricted");
			locations.addAll(
				SlayerRecommendationEngine.catalogLocationsForValidation(taskName)
			);

			for (final String location : locations)
			{
				for (final Preference.Playstyle playstyle
					: Preference.Playstyle.values())
				{
					for (final Preference.Cannon cannon
						: Preference.Cannon.values())
					{
						for (final Preference.Burst burst
							: Preference.Burst.values())
						{
							for (final Preference.CombatStyle combat
								: Preference.CombatStyle.values())
							{
								validateSelection(
									taskName,
									location,
									playstyle,
									cannon,
									burst,
									combat,
									validatedCoverage,
									failures
								);
							}
						}
					}
				}
			}
		}
		return Collections.unmodifiableList(failures);
	}

	public static void validateDeepOrThrow()
	{
		final List<String> failures = validateDeep();
		if (!failures.isEmpty())
		{
			throw new IllegalStateException(
				"SlayerPlus deep catalog regression audit failed: " + failures
			);
		}
	}

	private static void validateResearchStrategyRegistryParity()
	{
		final Set<String> research = normalized(
			TaskResearch.getReviewedTaskNames()
		);
		final Set<String> strategies = normalized(
			SlayerTaskStrategyCatalog.getCurrentTaskNames()
		);
		if (!research.equals(strategies))
		{
			final Set<String> missingStrategies = new LinkedHashSet<>(research);
			missingStrategies.removeAll(strategies);
			final Set<String> missingResearch = new LinkedHashSet<>(strategies);
			missingResearch.removeAll(research);
			throw new IllegalStateException(
				"Research/strategy task registry drift. Missing strategies="
					+ missingStrategies + ", missing research=" + missingResearch
			);
		}
	}

	/**
	 * Every selectable location must offer a deterministic first Shortest Path
	 * target from the bank. NPC-only discovery is useful after arrival, but it
	 * cannot start a route while the encounter is outside the loaded scene.
	 */
	private static void validateBankStartRouteCoverage()
	{
		final List<String> missing = new ArrayList<>();
		for (final String taskName : SlayerTaskStrategyCatalog.getCurrentTaskNames())
		{
			final Set<String> selectableLocations =
				SlayerRecommendationEngine.catalogLocationsForValidation(taskName);
			if (selectableLocations.isEmpty())
			{
				final SlayerTaskTravelAuditCatalog.Entry travel =
					SlayerTaskTravelAuditCatalog.find(taskName);
				if (travel == null || normalize(travel.getLocation()).isEmpty())
				{
					missing.add(taskName + " @ <no audited travel location>");
					continue;
				}
				if (!hasBankStartRoute(taskName, travel.getLocation(), false))
				{
					missing.add(taskName + " @ " + travel.getLocation());
				}
				continue;
			}

			for (final String location : selectableLocations)
			{
				if (!hasBankStartRoute(taskName, location, false))
				{
					missing.add(taskName + " @ " + location);
				}
			}
		}

		for (final VariantCatalog.EncounterDefinition definition
			: VariantCatalog.getBossDefinitions())
		{
			if (!hasBankStartRoute(
				definition.getTargetName(), definition.getLocation(), true))
			{
				missing.add(definition.getTargetName() + " @ "
					+ definition.getLocation() + " [boss]");
			}
		}

		/* Directly assigned bosses use STANDARD_TASK as their selector identity,
		 * but still require strict boss routing and a deterministic bank-start leg. */
		for (final VariantCatalog.EncounterDefinition definition
			: VariantCatalog.getDirectBossDefinitions())
		{
			if (!hasBankStartRoute(
				definition.getTargetName(), definition.getLocation(), true))
			{
				missing.add(definition.getTargetName() + " @ "
					+ definition.getLocation() + " [direct boss]");
			}
		}

		if (!missing.isEmpty())
		{
			throw new IllegalStateException(
				"No bank-start Shortest Path target: " + missing
			);
		}
	}

	private static boolean hasBankStartRoute(
		final String taskName,
		final String location,
		final boolean boss)
	{
		if (RouteCatalog.requiresPreparationBank(taskName, location, boss))
		{
			return BankRoutes.getInfernoPreparationBankTarget() != null;
		}

		final RouteCatalog.RouteProfile profile = RouteCatalog.resolve(
			taskName,
			location,
			boss
		);
		return profile != null && !profile.getRouteTargets(null).isEmpty();
	}

	private static void validateBossRouteIsolation()
	{
		for (final VariantCatalog.EncounterDefinition definition
			: VariantCatalog.getBossDefinitions())
		{
			final String bossName = definition.getTargetName();
			final String location = definition.getLocation();
			final RouteCatalog.RouteProfile boss = RouteCatalog.resolve(
				bossName,
				location,
				true
			);
			if (boss == null || !boss.isBoss() || !boss.matchesNpc(bossName))
			{
				throw new IllegalStateException(
					"Boss route is not strict to its encounter: " + bossName
						+ " @ " + location
				);
			}

			final String assignment = definition.getAssignmentName();
			if (assignment == null || assignment.trim().isEmpty())
			{
				continue;
			}
			final RouteCatalog.RouteProfile standard = RouteCatalog.resolve(
				assignment,
				location,
				false
			);
			if (standard != null && standard.matchesNpc(bossName))
			{
				throw new IllegalStateException(
					"Boss NPC leaked into standard route: " + assignment
						+ " -> " + bossName + " @ " + location
				);
			}
		}
	}

	private static void validateCriticalMethodContracts()
	{
		/* Dust Devil stacking requires a real aggression utility in the 4 x 7. */
		for (final Preference.Playstyle playstyle
			: new Preference.Playstyle[] {
				Preference.Playstyle.FAST_XP,
				Preference.Playstyle.PROFIT
			})
		{
			final TaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				"Dust devils",
				playstyle,
				Preference.Cannon.ALLOW,
				Preference.Burst.ALLOW,
				Preference.CombatStyle.AUTOMATIC,
				"Catacombs of Kourend",
				false
			);
			if (strategy == null
				|| !strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE))
			{
				throw new IllegalStateException(
					"Dust Devil " + playstyle + " lost its reviewed Ancient AoE method"
				);
			}
			final MethodRules rules = SlayerMethodRuleCatalog.resolve(
				"Dust devils",
				"Catacombs of Kourend",
				strategy
			);
			boolean aggressionUtility = false;
			for (final MethodRules.RequiredItem item : rules.getRequiredItems())
			{
				if (item.getGroup() != MethodRules.InventoryGroup.UTILITY)
				{
					continue;
				}
				for (final String alternative : item.getAlternatives())
				{
					final String name = normalize(alternative);
					if (name.contains("dart") || name.contains("knife")
						|| name.contains("goading potion"))
					{
						aggressionUtility = true;
						break;
					}
				}
			}
			if (!aggressionUtility)
			{
				throw new IllegalStateException(
					"Dust Devil stacking method has no owned-resolvable aggression utility slot"
				);
			}
		}

		final RouteCatalog.RouteProfile dustRoute = RouteCatalog.resolve(
			"Dust devils", "Catacombs of Kourend", false
		);
		if (dustRoute == null || !dustRoute.isStaged()
			|| dustRoute.getTransitionSpec() == null
			|| !dustRoute.getTransitionSpec().matchesAction("Investigate"))
		{
			throw new IllegalStateException(
				"Dust Devil Catacombs route lost its explicit King Rada transition"
			);
		}

		/*
		 * Cerberus defaults to the Wiki's Greater Thrall pouch. Death Charge is
		 * an optional inventory-space tradeoff and Ward depends on the shield
		 * setup; combining all three would exceed even a divine rune pouch.
		 */
		final TaskStrategy cerberus = SlayerTaskStrategyCatalog.resolve(
			"Cerberus",
			Preference.Playstyle.FAST_XP,
			Preference.Cannon.NEVER,
			Preference.Burst.NEVER,
			Preference.CombatStyle.AUTOMATIC,
			"Cerberus' Lair",
			false
		);
		final MethodRules cerberusRules = SlayerMethodRuleCatalog.resolve(
			"Cerberus", "Cerberus' Lair", cerberus
		);
		if (!cerberusRules.usesThralls() || cerberusRules.usesDeathCharge()
			|| cerberusRules.usesWardOfArceuus())
		{
			throw new IllegalStateException(
				"Cerberus lost its valid Greater Thrall rune package"
			);
		}
		final Map<String, Integer> cerberusRunes = new LinkedHashMap<>();
		for (final MethodRules.PouchRuneRequirement rune
			: cerberusRules.getPouchRunes())
		{
			cerberusRunes.put(normalize(rune.getName()), rune.getMinimumQuantity());
		}
		requireRuneQuantity(cerberusRunes, "fire", 10, "Cerberus");
		requireRuneQuantity(cerberusRunes, "blood", 5, "Cerberus");
		requireRuneQuantity(cerberusRunes, "cosmic", 1, "Cerberus");
		if (cerberusRunes.size() != 3)
		{
			throw new IllegalStateException(
				"Cerberus Greater Thrall package must contain exactly three rune types"
			);
		}
	}

	private static void validateSelection(
		final String taskName,
		final String location,
		final Preference.Playstyle playstyle,
		final Preference.Cannon cannon,
		final Preference.Burst burst,
		final Preference.CombatStyle combat,
		final Set<String> validatedCoverage,
		final List<String> failures)
	{
		try
		{
			final TaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				taskName,
				playstyle,
				cannon,
				burst,
				combat,
				location,
				false
			);
			if (strategy == null || !strategy.isReviewed())
			{
				failures.add(selectionLabel(
					taskName, location, playstyle, cannon, burst, combat
				) + ": unreviewed/null strategy");
				return;
			}

			final MethodRules rules = SlayerMethodRuleCatalog.resolve(
				taskName,
				location,
				strategy
			);
			if (validatedCoverage.add(rules.getCoverageKey()))
			{
				rules.validateFor(taskName, strategy);
				validateMethodContracts(taskName, strategy, rules);
			}
		}
		catch (RuntimeException ex)
		{
			failures.add(selectionLabel(
				taskName, location, playstyle, cannon, burst, combat
			) + ": " + safeMessage(ex));
		}
	}

	private static void validateMethodContracts(
		final String taskName,
		final TaskStrategy strategy,
		final MethodRules rules)
	{
		if (strategy.hasTag(TaskStrategy.MethodTag.CANNON)
			&& (!rules.usesCannon() || rules.getCannonballQuantity() <= 0))
		{
			throw new IllegalStateException(
				"cannon strategy has no structured cannon package"
			);
		}
		if (strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE)
			&& (rules.getSpellbook() != MethodRules.Spellbook.ANCIENT
				|| (!rules.requiresRunePouch() && !strategy.needsRunePouch())
				|| rules.getPrimarySpell() == null
				|| rules.getPrimarySpell().trim().isEmpty()))
		{
			throw new IllegalStateException(
				"burst/barrage strategy has no complete Ancient spell package"
			);
		}
		if ((strategy.getDamageProfile()
				== TaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
				|| strategy.getDamageProfile()
				== TaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING)
			&& rules.resolveFoodSlots(strategy, strategy.getFoodSlots()) != 0)
		{
			throw new IllegalStateException(
				"zero-damage method still reserves food"
			);
		}

		final int footprint = mandatoryInventoryFootprint(
			taskName,
			strategy,
			rules
		);
		if (footprint > 28)
		{
			throw new IllegalStateException(
				"mandatory inventory footprint exceeds 4x7 after reserving travel slot: "
					+ footprint
			);
		}
	}

	/**
	 * Conservative normal-rune-pouch footprint. Slot one is reserved for the
	 * authoritative physical travel item except when the reviewed method stages
	 * travel outside the final 4x7. The fixed Inferno package is authored for a
	 * four-rune pouch; the live builder safely lets the fourth rune displace the
	 * final bulk supply when only a three-rune pouch is owned. Stack quantities do
	 * not consume extra slots.
	 */
	private static int mandatoryInventoryFootprint(
		final String taskName,
		final TaskStrategy strategy,
		final MethodRules rules)
	{
		final boolean infernoStaging = "tzkal zuk".equals(normalize(taskName));
		int slots = infernoStaging ? 0 : 1;
		if (rules.usesCannon())
		{
			slots += 5; // four cannon parts + cannonball stack
		}

		final boolean runePouchRequested = rules.requiresRunePouch()
			|| strategy.needsRunePouch()
			|| (strategy.getCombatStyle() == TaskStrategy.CombatStyle.MAGIC
				&& rules.includesRunePouchForMagic());
		int pouchSlotsRemaining = runePouchRequested
			? (infernoStaging ? 4 : 3)
			: 0;
		if (runePouchRequested)
		{
			slots++;
		}
		if (rules.requiresBookOfDead())
		{
			slots++;
		}

		final Set<String> structuredRunes = new LinkedHashSet<>();
		for (final MethodRules.PouchRuneRequirement rune : rules.getPouchRunes())
		{
			structuredRunes.add(normalize(rune.getName()));
			if (pouchSlotsRemaining > 0)
			{
				pouchSlotsRemaining--;
			}
			else
			{
				slots++;
			}
		}

		for (final MethodRules.RequiredItem item : rules.getRequiredItems())
		{
			if (isStructuredUtilityDuplicate(item, rules))
			{
				continue;
			}
			final String display = normalize(item.getDisplayName());
			if (isRuneRequirement(display))
			{
				final String rune = display
					.replace(" runes", "")
					.replace(" rune", "")
					.trim();
				if (isCoveredByStructuredPouchRune(structuredRunes, rune))
				{
					continue;
				}
				if (pouchSlotsRemaining > 0)
				{
					pouchSlotsRemaining--;
					continue;
				}
			}
			slots += item.getSlotCount();
		}

		slots += rules.resolveRestoreSlots(strategy);
		slots += rules.resolveFoodSlots(strategy, strategy.getFoodSlots());
		if (rules.includesStyleBoost())
		{
			slots++;
		}
		return slots;
	}

	private static boolean isStructuredUtilityDuplicate(
		final MethodRules.RequiredItem item,
		final MethodRules rules)
	{
		final String name = normalize(item.getDisplayName());
		return (rules.requiresRunePouch()
			&& (name.equals("rune pouch") || name.equals("divine rune pouch")))
			|| (rules.requiresBookOfDead() && name.equals("book of the dead"));
	}

	private static boolean isRuneRequirement(final String display)
	{
		return display.endsWith(" rune") || display.endsWith(" runes");
	}

	private static boolean isCoveredByStructuredPouchRune(
		final Set<String> structuredRunes,
		final String requiredRune)
	{
		if (structuredRunes.contains(requiredRune))
		{
			return true;
		}
		return structuredRunes.contains("aether")
			&& (requiredRune.equals("cosmic") || requiredRune.equals("soul"));
	}

	private static void requireRuneQuantity(
		final Map<String, Integer> runes,
		final String rune,
		final int quantity,
		final String encounter)
	{
		final Integer actual = runes.get(normalize(rune));
		if (actual == null || actual != quantity)
		{
			throw new IllegalStateException(
				encounter + " Arceuus package has the wrong " + rune
					+ " quantity: expected " + quantity + ", got " + actual
			);
		}
	}

	private static Set<String> normalized(final Collection<String> values)
	{
		final Set<String> result = new LinkedHashSet<>();
		if (values != null)
		{
			for (final String value : values)
			{
				final String key = normalize(value);
				if (!key.isEmpty())
				{
					result.add(key);
				}
			}
		}
		return result;
	}

	private static String selectionLabel(
		final String task,
		final String location,
		final Preference.Playstyle playstyle,
		final Preference.Cannon cannon,
		final Preference.Burst burst,
		final Preference.CombatStyle combat)
	{
		return task + " @ " + location + " ["
			+ playstyle + "/" + cannon + "/" + burst + "/" + combat + "]";
	}

	private static String normalize(final String value)
	{
		return value == null
			? ""
			: value.toLowerCase(Locale.ENGLISH)
				.replace('\u2019', '\'')
				.replaceAll("[^a-z0-9]+", " ")
				.trim()
				.replaceAll("\\s+", " ");
	}

	private static String safeMessage(final RuntimeException ex)
	{
		return ex == null || ex.getMessage() == null
			? "validation failed"
			: ex.getMessage();
	}
}
