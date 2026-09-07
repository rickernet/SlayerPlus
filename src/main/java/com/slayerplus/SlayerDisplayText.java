package com.slayerplus;

import java.util.*;

public final class SlayerDisplayText {
  private static final String TEST_PREFIX = "TEST:";
  private static final String REVIEW_PENDING_SUFFIX = " — strategy review pending";
  private static final Map<String, String> CANONICAL_TASK_NAMES = createCanonicalTaskNames();

  private SlayerDisplayText() {}

  public static String assignment(String value) {
    if (value == null || value.trim().isEmpty()) {
      return "Unknown task";
    }
    String task = value.trim();
    boolean simulated = task.regionMatches(true, 0, TEST_PREFIX, 0, TEST_PREFIX.length());
    if (simulated) {
      task = task.substring(TEST_PREFIX.length()).trim();
    }
    String canonical = canonicalTaskName(task);
    return simulated ? TEST_PREFIX + " " + canonical : canonical;
  }

  public static String bankSetupTitle(String value) {
    if (value == null || value.trim().isEmpty()) {
      return "Waiting for Slayer task";
    }
    String title = value.trim();
    if (title.equalsIgnoreCase("Waiting for Slayer task")) {
      return "Waiting for Slayer task";
    }
    if (title.equalsIgnoreCase("Recommended setup")) {
      return "Recommended setup";
    }
    if (title.equalsIgnoreCase("Loadout recommendations disabled")) {
      return "Loadout recommendations disabled";
    }
    if (title
        .toLowerCase(Locale.ENGLISH)
        .endsWith(REVIEW_PENDING_SUFFIX.toLowerCase(Locale.ENGLISH))) {
      String task = title.substring(0, title.length() - REVIEW_PENDING_SUFFIX.length()).trim();
      return assignment(task) + REVIEW_PENDING_SUFFIX;
    }
    return assignment(title);
  }

  private static String canonicalTaskName(String task) {
    String key = normalize(task);
    String canonical = CANONICAL_TASK_NAMES.get(key);
    if (canonical != null) {
      return canonical;
    }
    String lower = task.toLowerCase(Locale.ENGLISH);
    return lower.isEmpty() ? "Unknown task" : titleCaseWords(lower);
  }

  private static Map<String, String> createCanonicalTaskNames() {
    Map<String, String> names = new LinkedHashMap<>();
    for (String reviewedName : TaskResearch.getReviewedTaskNames()) {
      names.put(normalize(reviewedName), titleCaseWords(reviewedName));
    }
    addBossNames(names, VariantCatalog.getBossDefinitions());
    addBossNames(names, VariantCatalog.getDirectBossDefinitions());
    return Collections.unmodifiableMap(names);
  }

  private static void addBossNames(
      Map<String, String> names, Iterable<VariantCatalog.EncounterDefinition> definitions) {
    for (VariantCatalog.EncounterDefinition definition : definitions) {
      String displayName = definition.getDisplayName();
      String canonicalDisplayName = titleCaseWords(displayName);
      names.put(normalize(definition.getEncounter()), canonicalDisplayName);
      names.put(normalize(displayName), canonicalDisplayName);
    }
  }

  private static String titleCaseWords(String value) {
    String[] words = value.trim().split("\\s+");
    StringBuilder result = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (result.length() > 0) {
        result.append(' ');
      }
      result.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) {
        result.append(word.substring(1));
      }
    }
    return result.toString();
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " ");
  }
}
