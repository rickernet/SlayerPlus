package com.slayerplus;

import java.util.List;

public final class SlayerEncounterStandards {
  private SlayerEncounterStandards() {}

  public static String cleanEncounterLabel(String value) {
    if (value == null || value.trim().isEmpty()) {
      return "Task";
    }
    return value
        .trim()
        .replaceFirst("(?i)^regular\\s+", "")
        .replaceFirst("(?i)\\s*\\((?:boss|bosses)\\)\\s*$", "")
        .replaceFirst("(?i)\\s*\\[(?:boss|bosses)\\]\\s*$", "")
        .replaceFirst("(?i)\\s*[-–—]\\s*(?:boss|bosses)\\s*$", "")
        .trim();
  }

  public static String resolveTaskMethod(
      String encounterName,
      String location,
      boolean bossEncounter,
      String routePositioningNote,
      TaskStrategy strategy) {
    if (strategy != null && strategy.isReviewed()) {
      MethodRules rules = SlayerMethodRuleCatalog.resolve(encounterName, location, strategy);
      String method = safe(rules.getTaskMethod());
      if (!method.isEmpty()) {
        return method;
      }
    }
    String authored = strategy == null ? "" : safe(strategy.getMethod());
    if (!authored.isEmpty()) {
      return authored;
    }
    String route = safe(routePositioningNote);
    return route.isEmpty() ? "No reviewed method is available for this encounter." : route;
  }

  public static String resolveTaskMethod(
      String encounterName,
      boolean bossEncounter,
      String routePositioningNote,
      TaskStrategy strategy) {
    return resolveTaskMethod(encounterName, "", bossEncounter, routePositioningNote, strategy);
  }

  public static boolean validateBossDefinitions(
      List<VariantCatalog.EncounterDefinition> definitions) {
    if (definitions == null) {
      throw new IllegalStateException("Slayer boss catalog is unavailable");
    }
    for (VariantCatalog.EncounterDefinition definition : definitions) {
      if (definition == null) {
        throw new IllegalStateException("Null Slayer boss definition");
      }
      if (definition.getVariant() != null && !definition.getVariant().isBoss()) {
        throw new IllegalStateException(
            "Encounter definition uses a non-boss variant: " + definition.getTargetName());
      }
      if (safe(definition.getTargetName()).isEmpty()) {
        throw new IllegalStateException("Boss definition has no target name");
      }
      if (safe(definition.getLocation()).isEmpty()) {
        throw new IllegalStateException(
            "Boss definition has no location: " + definition.getTargetName());
      }
      if (cleanEncounterLabel(definition.getOptionLabel()).equals("Task")) {
        throw new IllegalStateException(
            "Boss definition has no display label: " + definition.getTargetName());
      }
    }
    return true;
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
