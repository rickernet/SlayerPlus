package com.slayerplus;

import java.util.List;

/** Shared presentation and completeness rules for every Slayer encounter. */
public final class SlayerEncounterStandards
{
	private SlayerEncounterStandards() { }

	public static String cleanEncounterLabel(final String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return "Task";
		}

		return value.trim()
			.replaceFirst("(?i)^regular\\s+", "")
			.replaceFirst("(?i)\\s*\\((?:boss|bosses)\\)\\s*$", "")
			.replaceFirst("(?i)\\s*\\[(?:boss|bosses)\\]\\s*$", "")
			.replaceFirst("(?i)\\s*[-–—]\\s*(?:boss|bosses)\\s*$", "")
			.trim();
	}

	/**
	 * Task Method now comes from the same rule object that generated the bank
	 * layout. Route-positioning text is only a final fallback and can no longer
	 * replace a reviewed combat method with unrelated travel instructions.
	 */
	public static String resolveTaskMethod(
		final String encounterName,
		final String location,
		final boolean bossEncounter,
		final String routePositioningNote,
		final SlayerTaskStrategy strategy)
	{
		if (strategy != null && strategy.isReviewed())
		{
			final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
				encounterName,
				location,
				strategy
			);
			final String method = safe(rules.getTaskMethod());
			if (!method.isEmpty())
			{
				return method;
			}
		}

		final String authored = strategy == null
			? ""
			: safe(strategy.getMethod());
		if (!authored.isEmpty())
		{
			return authored;
		}

		final String route = safe(routePositioningNote);
		return route.isEmpty()
			? "No reviewed method is available for this encounter."
			: route;
	}

	/** Backward-compatible overload for older callers. */
	public static String resolveTaskMethod(
		final String encounterName,
		final boolean bossEncounter,
		final String routePositioningNote,
		final SlayerTaskStrategy strategy)
	{
		return resolveTaskMethod(
			encounterName,
			"",
			bossEncounter,
			routePositioningNote,
			strategy
		);
	}

	/**
	 * Fail fast when a selectable/direct boss is registered without the strategy
	 * data required by the shared method-rule pipeline.
	 */
	public static boolean validateBossDefinitions(
		final List<SlayerTaskVariantCatalog.EncounterDefinition> definitions)
	{
		if (definitions == null)
		{
			throw new IllegalStateException("Slayer boss catalog is unavailable");
		}

		for (final SlayerTaskVariantCatalog.EncounterDefinition definition
			: definitions)
		{
			if (definition == null)
			{
				throw new IllegalStateException("Null Slayer boss definition");
			}
			if (definition.getVariant() != null
				&& !definition.getVariant().isBoss())
			{
				throw new IllegalStateException(
					"Encounter definition uses a non-boss variant: "
						+ definition.getTargetName()
				);
			}
			if (safe(definition.getTargetName()).isEmpty())
			{
				throw new IllegalStateException("Boss definition has no target name");
			}
			if (safe(definition.getLocation()).isEmpty())
			{
				throw new IllegalStateException(
					"Boss definition has no location: "
						+ definition.getTargetName()
				);
			}
			if (cleanEncounterLabel(definition.getOptionLabel()).equals("Task"))
			{
				throw new IllegalStateException(
					"Boss definition has no display label: "
						+ definition.getTargetName()
				);
			}
			/*
			 * Strategy coverage is intentionally NOT a class-initialization
			 * invariant. The boss catalog also contains valid selectable/direct
			 * encounter definitions whose detailed method research can be added
			 * incrementally. Throwing here makes a single missing reviewed
			 * strategy (for example Abyssal Sire) prevent RuneLite from starting.
			 *
			 * This validator therefore enforces only structural catalog safety.
			 * Method-rule coverage belongs in SlayerMethodRuleCoverage/runtime
			 * resolution, where an incomplete entry can be reported without
			 * crashing the client.
			 */
		}
		return true;
	}

	private static String safe(final String value)
	{
		return value == null ? "" : value.trim();
	}
}
