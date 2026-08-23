package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SlayerTaskNpcCatalog {
  private static final CatalogData DATA = loadCatalog();
  private static final Map<String, Set<String>> ALIASES = DATA.aliases;
  private static final Map<String, Set<String>> STANDARD_EXCLUSIONS = DATA.exclusions;

  static {
    validateOrThrow();
  }

  private SlayerTaskNpcCatalog() {}

  public static Set<String> aliasesFor(String taskName) {
    Set<String> aliases = new LinkedHashSet<>();
    addAlias(aliases, taskName);
    addAlias(aliases, singular(normalize(taskName)));
    Set<String> reviewed = ALIASES.get(taskKey(taskName));
    if (reviewed != null) {
      aliases.addAll(reviewed);
    }
    return aliases;
  }

  public static Set<String> aliasesForStandardRoute(String taskName) {
    Set<String> aliases = new LinkedHashSet<>(aliasesFor(taskName));
    Set<String> excluded = STANDARD_EXCLUSIONS.get(taskKey(taskName));
    if (excluded != null) {
      aliases.removeAll(excluded);
    }
    return aliases;
  }

  public static boolean hasTask(String taskName) {
    return ALIASES.containsKey(taskKey(taskName));
  }

  public static void validateOrThrow() {
    for (Map.Entry<String, Set<String>> entry : ALIASES.entrySet()) {
      if (entry.getKey() == null
          || entry.getKey().trim().isEmpty()
          || entry.getValue() == null
          || entry.getValue().isEmpty()) {
        throw new IllegalStateException(
            "Slayer NPC alias catalog contains an incomplete task: " + entry.getKey());
      }
      for (String alias : entry.getValue()) {
        if (alias == null || alias.trim().isEmpty()) {
          throw new IllegalStateException(
              "Slayer NPC alias catalog contains a blank alias: " + entry.getKey());
        }
      }
    }
    for (Map.Entry<String, Set<String>> entry : STANDARD_EXCLUSIONS.entrySet()) {
      Set<String> base = aliasesFor(entry.getKey());
      Set<String> standard = aliasesForStandardRoute(entry.getKey());
      for (String excluded : entry.getValue()) {
        if (!base.contains(excluded)) {
          throw new IllegalStateException(
              "Boss exclusion is not present in the base NPC aliases: "
                  + entry.getKey()
                  + " -> "
                  + excluded);
        }
        if (standard.contains(excluded)) {
          throw new IllegalStateException(
              "Boss NPC leaked into a standard Slayer route: "
                  + entry.getKey()
                  + " -> "
                  + excluded);
        }
      }
    }
  }

  private static CatalogData loadCatalog() {
    Map<String, Set<String>> aliases = new LinkedHashMap<>();
    Map<String, Set<String>> exclusions = new LinkedHashMap<>();
    for (String[] fields : ResourceTable.decodedRows("slayer-task-npcs.tsv", 3)) {
      if (!fields[0].equals("A") && !fields[0].equals("X")) {
        throw new IllegalStateException("Invalid NPC alias resource record: " + fields[0]);
      }
      String[] names = fields[2].split("\\|", -1);
      if (fields[0].equals("A")) {
        put(aliases, fields[1], names);
      } else {
        putExclusions(exclusions, fields[1], names);
      }
    }
    return new CatalogData(
        Collections.unmodifiableMap(aliases), Collections.unmodifiableMap(exclusions));
  }

  private static void putExclusions(
      Map<String, Set<String>> map, String taskName, String... npcNames) {
    Set<String> excluded = new LinkedHashSet<>();
    if (npcNames != null) {
      for (String npcName : npcNames) {
        addAlias(excluded, npcName);
      }
    }
    String key = taskKey(taskName);
    Set<String> immutable = Collections.unmodifiableSet(excluded);
    Set<String> previous = map.put(key, immutable);
    if (previous != null && !previous.equals(immutable)) {
      throw new IllegalStateException(
          "Conflicting duplicate Slayer NPC exclusion entry: " + taskName);
    }
  }

  private static void put(Map<String, Set<String>> map, String taskName, String... npcNames) {
    Set<String> aliases = new LinkedHashSet<>();
    if (npcNames != null) {
      for (String npcName : npcNames) {
        addAlias(aliases, npcName);
      }
    }
    String key = taskKey(taskName);
    Set<String> immutable = Collections.unmodifiableSet(aliases);
    Set<String> previous = map.put(key, immutable);
    if (previous != null && !previous.equals(immutable)) {
      throw new IllegalStateException("Conflicting duplicate Slayer NPC alias entry: " + taskName);
    }
  }

  private static void addAlias(Set<String> aliases, String value) {
    String normalized = normalize(value);
    if (!normalized.isEmpty()) {
      aliases.add(normalized);
      aliases.add(singular(normalized));
    }
  }

  private static String taskKey(String value) {
    String normalized = singular(normalize(value));
    if (normalized.startsWith("the ")) {
      normalized = normalized.substring(4);
    }
    return normalized;
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ENGLISH)
        .replace('\u2019', '\'')
        .replaceAll("[^a-z0-9]+", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  private static String singular(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    if (value.endsWith("wolves")) {
      return value.substring(0, value.length() - 6) + "wolf";
    }
    if (value.endsWith("elves")) {
      return value.substring(0, value.length() - 5) + "elf";
    }
    if (value.endsWith("dwarves")) {
      return value.substring(0, value.length() - 7) + "dwarf";
    }
    if (value.endsWith("men")) {
      return value.substring(0, value.length() - 3) + "man";
    }
    if (value.endsWith("ies") && value.length() > 4) {
      return value.substring(0, value.length() - 3) + "y";
    }
    if (value.endsWith("s")
        && !value.endsWith("ss")
        && !value.endsWith("us")
        && !value.endsWith("is")
        && value.length() > 3) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }

  private static final class CatalogData {
    private final Map<String, Set<String>> aliases;
    private final Map<String, Set<String>> exclusions;

    private CatalogData(Map<String, Set<String>> aliases, Map<String, Set<String>> exclusions) {
      this.aliases = aliases;
      this.exclusions = exclusions;
    }
  }
}
