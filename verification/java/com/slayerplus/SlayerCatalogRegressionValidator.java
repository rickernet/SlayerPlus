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
 * <p>The startup audit is intentionally cheap: it validates static catalog contracts that should
 * never depend on live RuneLite state. The deep audit is intended for development/tests and
 * exhaustively resolves the current task matrix without adding that cost to normal plugin startup.
 */
public final class SlayerCatalogRegressionValidator {
  private SlayerCatalogRegressionValidator() {}

  /** Fast, deterministic checks safe to run once during plugin startup. */
  public static void validateStartupOrThrow() {
    validateResearchStrategyRegistryParity();
    SlayerEncounterStandards.validateBossDefinitions(VariantCatalog.getBossDefinitions());
    validateCriticalMethodContracts();
  }

  /**
   * Exhaustive development audit. Do not call this every tick or every normal refresh; it
   * intentionally walks every selectable preference combination.
   */
  public static List<String> validateDeep() {
    final List<String> failures = new ArrayList<>();
    try {
      validateStartupOrThrow();
    } catch (RuntimeException ex) {
      failures.add("startup contract: " + safeMessage(ex));
    }

    final List<String> strategyErrors = SlayerTaskStrategyValidator.validate();
    failures.addAll(strategyErrors);

    try {
      SlayerMethodRuleCoverage.validateCurrentCatalog();
    } catch (RuntimeException ex) {
      failures.add("method coverage: " + safeMessage(ex));
    }

    final Set<String> validatedCoverage = new LinkedHashSet<>();
    for (final String assignment : SlayerTaskStrategyCatalog.getCurrentTaskNames()) {
      final Set<String> locations = new LinkedHashSet<>();
      locations.add("Not restricted");
      locations.addAll(SlayerRecommendationEngine.catalogLocationsForValidation(assignment));

      for (final String location : locations) {
        for (final Preference.Cannon cannon : Preference.Cannon.values()) {
          for (final Preference.Burst burst : Preference.Burst.values()) {
            for (final Preference.CombatStyle combat : Preference.CombatStyle.values()) {
              validateSelection(
                  assignment, location, cannon, burst, combat, validatedCoverage, failures);
            }
          }
        }
      }
    }
    return Collections.unmodifiableList(failures);
  }

  public static void validateDeepOrThrow() {
    final List<String> failures = validateDeep();
    if (!failures.isEmpty()) {
      throw new IllegalStateException(
          "SlayerPlus deep catalog regression audit failed: " + failures);
    }
  }

  private static void validateResearchStrategyRegistryParity() {
    final Set<String> research = normalized(TaskResearch.getReviewedTaskNames());
    final Set<String> strategies = normalized(SlayerTaskStrategyCatalog.getCurrentTaskNames());
    if (!research.equals(strategies)) {
      final Set<String> missingStrategies = new LinkedHashSet<>(research);
      missingStrategies.removeAll(strategies);
      final Set<String> missingResearch = new LinkedHashSet<>(strategies);
      missingResearch.removeAll(research);
      throw new IllegalStateException(
          "Research/strategy task registry drift. Missing strategies="
              + missingStrategies
              + ", missing research="
              + missingResearch);
    }
  }

  private static void validateCriticalMethodContracts() {
    /* Dust Devil stacking requires a real aggression utility in the 4 x 7. */
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Dust devils",
            Preference.Cannon.ALLOW,
            Preference.Burst.ALLOW,
            Preference.CombatStyle.AUTOMATIC,
            "Catacombs of Kourend",
            false);
    if (strategy == null || !strategy.hasTag(TaskStrategy.MethodTag.BARRAGE)) {
      throw new IllegalStateException("Dust Devils lost their reviewed Ancient AoE method");
    }
    final MethodRules rules =
        SlayerMethodRuleCatalog.resolve("Dust devils", "Catacombs of Kourend", strategy);
    boolean aggressionUtility = false;
    for (final MethodRules.RequiredItem item : rules.getRequiredItems()) {
      if (item.getGroup() != MethodRules.InventoryGroup.UTILITY) {
        continue;
      }
      for (final String alternative : item.getAlternatives()) {
        final String name = normalize(alternative);
        if (name.contains("dart") || name.contains("knife") || name.contains("goading potion")) {
          aggressionUtility = true;
          break;
        }
      }
    }
    if (!aggressionUtility) {
      throw new IllegalStateException(
          "Dust Devil stacking method has no owned-resolvable aggression utility slot");
    }

    /*
     * Cerberus defaults to the Wiki's Greater Thrall pouch. Death Charge is
     * an optional inventory-space tradeoff and Ward depends on the shield
     * setup; combining all three would exceed even a divine rune pouch.
     */
    final TaskStrategy cerberus =
        SlayerTaskStrategyCatalog.resolve(
            "Cerberus",
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Cerberus' Lair",
            false);
    final MethodRules cerberusRules =
        SlayerMethodRuleCatalog.resolve("Cerberus", "Cerberus' Lair", cerberus);
    if (!cerberusRules.usesThralls()
        || cerberusRules.usesDeathCharge()
        || cerberusRules.usesWardOfArceuus()) {
      throw new IllegalStateException("Cerberus lost its valid Greater Thrall rune package");
    }
    final Map<String, Integer> cerberusRunes = new LinkedHashMap<>();
    for (final MethodRules.RuneRequirement rune : cerberusRules.getPouchRunes()) {
      cerberusRunes.put(normalize(rune.getName()), rune.getMinimumQuantity());
    }
    requireRuneQuantity(cerberusRunes, "fire", 10, "Cerberus");
    requireRuneQuantity(cerberusRunes, "blood", 5, "Cerberus");
    requireRuneQuantity(cerberusRunes, "cosmic", 1, "Cerberus");
    if (cerberusRunes.size() != 3) {
      throw new IllegalStateException(
          "Cerberus Greater Thrall package must contain exactly three rune types");
    }
  }

  private static void validateSelection(
      final String assignment,
      final String location,
      final Preference.Cannon cannon,
      final Preference.Burst burst,
      final Preference.CombatStyle combat,
      final Set<String> validatedCoverage,
      final List<String> failures) {
    try {
      final TaskStrategy strategy =
          SlayerTaskStrategyCatalog.resolve(assignment, cannon, burst, combat, location, false);
      if (strategy == null || !strategy.isReviewed()) {
        failures.add(
            selectionLabel(assignment, location, cannon, burst, combat)
                + ": unreviewed/null strategy");
        return;
      }

      final MethodRules rules = SlayerMethodRuleCatalog.resolve(assignment, location, strategy);
      if (validatedCoverage.add(rules.getCoverageKey())) {
        rules.validateFor(assignment, strategy);
        validateMethodContracts(assignment, strategy, rules);
      }
    } catch (RuntimeException ex) {
      failures.add(
          selectionLabel(assignment, location, cannon, burst, combat) + ": " + safeMessage(ex));
    }
  }

  private static void validateMethodContracts(
      final String assignment, final TaskStrategy strategy, final MethodRules rules) {
    if (strategy.hasTag(TaskStrategy.MethodTag.CANNON)
        && (!rules.usesCannon() || rules.getCannonballQuantity() <= 0)) {
      throw new IllegalStateException("cannon strategy has no structured cannon package");
    }
    if (strategy.hasTag(TaskStrategy.MethodTag.BARRAGE)
        && (rules.getSpellbook() != MethodRules.Spellbook.ANCIENT
            || (!rules.requiresRunePouch() && !strategy.needsRunePouch())
            || rules.getPrimarySpell() == null
            || rules.getPrimarySpell().trim().isEmpty())) {
      throw new IllegalStateException(
          "burst/barrage strategy has no complete Ancient spell package");
    }
    if ((strategy.getDamageProfile() == TaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
            || strategy.getDamageProfile() == TaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING)
        && rules.resolveFoodSlots(strategy, strategy.getFood()) != 0) {
      throw new IllegalStateException("zero-damage method still reserves food");
    }

    final int footprint = mandatoryInventoryFootprint(assignment, strategy, rules);
    if (footprint > 28) {
      throw new IllegalStateException(
          "mandatory inventory footprint exceeds 4x7 after reserving travel slot: " + footprint);
    }
  }

  /**
   * Conservative normal-rune-pouch footprint. Slot one is reserved for the authoritative physical
   * travel item except when the reviewed method stages travel outside the final 4x7. The fixed
   * Inferno package is authored for a four-rune pouch; the live builder safely lets the fourth rune
   * displace the final bulk supply when only a three-rune pouch is owned. Stack quantities do not
   * consume extra slots.
   */
  private static int mandatoryInventoryFootprint(
      final String assignment, final TaskStrategy strategy, final MethodRules rules) {
    final boolean infernoStaging = "tzkal zuk".equals(normalize(assignment));
    int slots = infernoStaging ? 0 : 1;
    if (rules.usesCannon()) {
      slots += 5; // four cannon parts + cannonball stack
    }

    final boolean runePouchRequested =
        rules.requiresRunePouch()
            || strategy.needsRunePouch()
            || (strategy.getStyle() == TaskStrategy.CombatStyle.MAGIC
                && rules.includesRunePouchForMagic());
    int pouchSlotsRemaining = runePouchRequested ? (infernoStaging ? 4 : 3) : 0;
    if (runePouchRequested) {
      slots++;
    }
    if (rules.requiresBookOfDead()) {
      slots++;
    }

    final Set<String> structuredRunes = new LinkedHashSet<>();
    for (final MethodRules.RuneRequirement rune : rules.getPouchRunes()) {
      structuredRunes.add(normalize(rune.getName()));
      if (pouchSlotsRemaining > 0) {
        pouchSlotsRemaining--;
      } else {
        slots++;
      }
    }

    for (final MethodRules.RequiredItem item : rules.getRequiredItems()) {
      if (isStructuredUtilityDuplicate(item, rules)) {
        continue;
      }
      final String display = normalize(item.getDisplayName());
      if (isRuneRequirement(display)) {
        final String rune = display.replace(" runes", "").replace(" rune", "").trim();
        if (isCoveredByStructuredPouchRune(structuredRunes, rune)) {
          continue;
        }
        if (pouchSlotsRemaining > 0) {
          pouchSlotsRemaining--;
          continue;
        }
      }
      slots += item.getSlotCount();
    }

    slots += rules.resolveRestoreSlots(strategy);
    slots += rules.resolveFoodSlots(strategy, strategy.getFood());
    if (rules.includesStyleBoost()) {
      slots++;
    }
    return slots;
  }

  private static boolean isStructuredUtilityDuplicate(
      final MethodRules.RequiredItem item, final MethodRules rules) {
    final String name = normalize(item.getDisplayName());
    return (rules.requiresRunePouch()
            && (name.equals("rune pouch") || name.equals("divine rune pouch")))
        || (rules.requiresBookOfDead() && name.equals("book of the dead"));
  }

  private static boolean isRuneRequirement(final String display) {
    return display.endsWith(" rune") || display.endsWith(" runes");
  }

  private static boolean isCoveredByStructuredPouchRune(
      final Set<String> structuredRunes, final String requiredRune) {
    if (structuredRunes.contains(requiredRune)) {
      return true;
    }
    return structuredRunes.contains("aether")
        && (requiredRune.equals("cosmic") || requiredRune.equals("soul"));
  }

  private static void requireRuneQuantity(
      final Map<String, Integer> runes,
      final String rune,
      final int quantity,
      final String encounter) {
    final Integer actual = runes.get(normalize(rune));
    if (actual == null || actual != quantity) {
      throw new IllegalStateException(
          encounter
              + " Arceuus package has the wrong "
              + rune
              + " quantity: expected "
              + quantity
              + ", got "
              + actual);
    }
  }

  private static Set<String> normalized(final Collection<String> values) {
    final Set<String> result = new LinkedHashSet<>();
    if (values != null) {
      for (final String value : values) {
        final String key = normalize(value);
        if (!key.isEmpty()) {
          result.add(key);
        }
      }
    }
    return result;
  }

  private static String selectionLabel(
      final String task,
      final String location,
      final Preference.Cannon cannon,
      final Preference.Burst burst,
      final Preference.CombatStyle combat) {
    return task + " @ " + location + " [" + cannon + "/" + burst + "/" + combat + "]";
  }

  private static String normalize(final String value) {
    return value == null
        ? ""
        : value
            .toLowerCase(Locale.ENGLISH)
            .replace('\u2019', '\'')
            .replaceAll("[^a-z0-9]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
  }

  private static String safeMessage(final RuntimeException ex) {
    return ex == null || ex.getMessage() == null ? "validation failed" : ex.getMessage();
  }
}
