package com.slayerplus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SlayerMethodRuleCoverage {
  private SlayerMethodRuleCoverage() {}

  public static boolean validateCurrentCatalog() {
    final List<String> failures = new ArrayList<>();
    final Set<String> validated = new LinkedHashSet<>();
    for (final String assignment : SlayerTaskStrategyCatalog.getCurrentTaskNames()) {
      if (!SlayerTaskStrategyCatalog.hasExplicitStrategy(assignment)) {
        failures.add(assignment + ": no individually reviewed strategy");
        continue;
      }
      for (final Preference.Playstyle playstyle : Preference.Playstyle.values()) {
        for (final Preference.Cannon cannon : Preference.Cannon.values()) {
          for (final Preference.Burst burst : Preference.Burst.values()) {
            for (final Preference.CombatStyle combat : Preference.CombatStyle.values()) {
              validateSelection(assignment, playstyle, cannon, burst, combat, validated, failures);
            }
          }
        }
      }
    }
    if (!failures.isEmpty()) {
      throw new IllegalStateException("Incomplete Slayer method-rule coverage: " + failures);
    }
    return true;
  }

  private static void validateSelection(
      final String assignment,
      final Preference.Playstyle playstyle,
      final Preference.Cannon cannon,
      final Preference.Burst burst,
      final Preference.CombatStyle combat,
      final Set<String> validated,
      final List<String> failures) {
    try {
      final TaskStrategy strategy =
          SlayerTaskStrategyCatalog.resolve(
              assignment, playstyle, cannon, burst, combat, "Not restricted", false);
      if (strategy == null || !strategy.isReviewed()) {
        failures.add(assignment + ": preference resolved to an unreviewed method");
        return;
      }
      final MethodRules rules =
          SlayerMethodRuleCatalog.resolve(assignment, "Not restricted", strategy);
      if (SlayerMethodRuleCatalog.selectedMethodRequiresStandardSpellbook(strategy)
          && rules.getSpellbook() != MethodRules.Spellbook.STANDARD) {
        failures.add(
            assignment
                + ": explicit Standard-spell method did not resolve to the Standard spellbook");
        return;
      }
      if (rules.getSpellbook() != MethodRules.Spellbook.NONE
          && rules.getSpellbook() != MethodRules.Spellbook.STRATEGY_DEFINED
          && (rules.getPrimarySpell() == null || rules.getPrimarySpell().trim().isEmpty())) {
        failures.add(assignment + ": concrete spellbook method has no primary spell");
        return;
      }
      if (validated.add(rules.getCoverageKey())) {
        rules.validateFor(assignment, strategy);
      }
    } catch (RuntimeException ex) {
      failures.add(assignment + ": " + safeMessage(ex));
    }
  }

  private static String safeMessage(final RuntimeException ex) {
    return ex == null || ex.getMessage() == null ? "validation failed" : ex.getMessage();
  }
}
