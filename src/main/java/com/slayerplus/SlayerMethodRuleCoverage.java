package com.slayerplus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Development-time coverage audit for the shared Slayer method rules. */
public final class SlayerMethodRuleCoverage
{
	private SlayerMethodRuleCoverage() { }

	/**
	 * Audits every current assignment through every selectable preference
	 * combination. Distinct strategies are validated once by coverage key.
	 */
	public static boolean validateCurrentCatalog()
	{
		final List<String> failures = new ArrayList<>();
		final Set<String> validated = new LinkedHashSet<>();

		for (final String taskName : SlayerTaskStrategyCatalog.getCurrentTaskNames())
		{
			if (!SlayerTaskStrategyCatalog.hasExplicitStrategy(taskName))
			{
				failures.add(taskName + ": no individually reviewed strategy");
				continue;
			}

			for (final SlayerPreference.Playstyle playstyle
				: SlayerPreference.Playstyle.values())
			{
				for (final SlayerPreference.Cannon cannon
					: SlayerPreference.Cannon.values())
				{
					for (final SlayerPreference.Burst burst
						: SlayerPreference.Burst.values())
					{
						for (final SlayerPreference.CombatStyle combat
							: SlayerPreference.CombatStyle.values())
						{
							validateSelection(
								taskName,
								playstyle,
								cannon,
								burst,
								combat,
								validated,
								failures
							);
						}
					}
				}
			}
		}

		if (!failures.isEmpty())
		{
			throw new IllegalStateException(
				"Incomplete Slayer method-rule coverage: " + failures
			);
		}
		return true;
	}

	private static void validateSelection(
		final String taskName,
		final SlayerPreference.Playstyle playstyle,
		final SlayerPreference.Cannon cannon,
		final SlayerPreference.Burst burst,
		final SlayerPreference.CombatStyle combat,
		final Set<String> validated,
		final List<String> failures)
	{
		try
		{
			final SlayerTaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				taskName,
				playstyle,
				cannon,
				burst,
				combat,
				"Not restricted",
				false
			);
			if (strategy == null || !strategy.isReviewed())
			{
				failures.add(taskName + ": preference resolved to an unreviewed method");
				return;
			}

			final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
				taskName,
				"Not restricted",
				strategy
			);
			if (SlayerMethodRuleCatalog.selectedMethodRequiresStandardSpellbook(strategy)
				&& rules.getSpellbook() != SlayerMethodRules.Spellbook.STANDARD)
			{
				failures.add(
					taskName + ": explicit Standard-spell method did not resolve to the Standard spellbook"
				);
				return;
			}

			if (rules.getSpellbook() != SlayerMethodRules.Spellbook.NONE
				&& rules.getSpellbook() != SlayerMethodRules.Spellbook.STRATEGY_DEFINED
				&& (rules.getPrimarySpell() == null
					|| rules.getPrimarySpell().trim().isEmpty()))
			{
				failures.add(
					taskName + ": concrete spellbook method has no primary spell"
				);
				return;
			}

			if (validated.add(rules.getCoverageKey()))
			{
				rules.validateFor(taskName, strategy);
			}
		}
		catch (RuntimeException ex)
		{
			failures.add(taskName + ": " + safeMessage(ex));
		}
	}

	private static String safeMessage(final RuntimeException ex)
	{
		return ex == null || ex.getMessage() == null
			? "validation failed"
			: ex.getMessage();
	}
}
