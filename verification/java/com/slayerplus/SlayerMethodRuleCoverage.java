package com.slayerplus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SlayerMethodRuleCoverage
{
	private SlayerMethodRuleCoverage() { }

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
			for (final Preference.Playstyle playstyle : Preference.Playstyle.values())
			{
				for (final Preference.Cannon cannon : Preference.Cannon.values())
				{
					for (final Preference.Burst burst : Preference.Burst.values())
					{
						for (final Preference.CombatStyle combat : Preference.CombatStyle.values())
						{
							validateSelection(taskName, playstyle, cannon, burst, combat, validated, failures);
						}
					}
				}
			}
		}
		if (!failures.isEmpty())
		{
			throw new IllegalStateException("Incomplete Slayer method-rule coverage: " + failures);
		}
		return true;
	}

	private static void validateSelection(
		final String taskName,
		final Preference.Playstyle playstyle,
		final Preference.Cannon cannon,
		final Preference.Burst burst,
		final Preference.CombatStyle combat,
		final Set<String> validated,
		final List<String> failures)
	{
		try
		{
			final TaskStrategy strategy = SlayerTaskStrategyCatalog.resolve(
				taskName, playstyle, cannon, burst, combat, "Not restricted", false);
			if (strategy == null || !strategy.isReviewed())
			{
				failures.add(taskName + ": preference resolved to an unreviewed method");
				return;
			}
			final MethodRules rules = SlayerMethodRuleCatalog.resolve(
				taskName, "Not restricted", strategy);
			if (SlayerMethodRuleCatalog.selectedMethodRequiresStandardSpellbook(strategy)
				&& rules.getSpellbook() != MethodRules.Spellbook.STANDARD)
			{
				failures.add(taskName + ": explicit Standard-spell method did not resolve to the Standard spellbook");
				return;
			}
			if (rules.getSpellbook() != MethodRules.Spellbook.NONE
				&& rules.getSpellbook() != MethodRules.Spellbook.STRATEGY_DEFINED
				&& (rules.getPrimarySpell() == null || rules.getPrimarySpell().trim().isEmpty()))
			{
				failures.add(taskName + ": concrete spellbook method has no primary spell");
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
		return ex == null || ex.getMessage() == null ? "validation failed" : ex.getMessage();
	}
}
