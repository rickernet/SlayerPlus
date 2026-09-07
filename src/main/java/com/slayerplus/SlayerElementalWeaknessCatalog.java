package com.slayerplus;

import java.util.*;

class SlayerElementalWeaknessCatalog {
  private enum Element {
    AIR("Wind"),
    EARTH("Earth"),
    FIRE("Fire"),
    WATER("Water");
    private final String spellLabel;

    Element(String spellLabel) {
      this.spellLabel = spellLabel;
    }
  }

  static final class Entry {
    private final Element element;
    private final int percent;

    private Entry(Element element, int percent) {
      this.element = element;
      this.percent = percent;
    }

    String getSpellLabel() {
      return element.spellLabel;
    }

    int getPercent() {
      return percent;
    }
  }

  private static final Map<String, Entry> ENTRIES = createEntries();

  private SlayerElementalWeaknessCatalog() {}

  static Entry find(String assignment) {
    return ENTRIES.get(normalize(assignment));
  }

  static TaskStrategy preferenceStrategy(String assignment, boolean wilderness) {
    Entry entry = find(assignment);
    if (entry == null) {
      return null;
    }
    TaskStrategy.Builder builder =
        TaskStrategy.builder(
                TaskStrategy.CombatStyle.MAGIC,
                "Use the best "
                    + entry.element.spellLabel
                    + " spell against the Wiki-listed "
                    + entry.percent
                    + "% "
                    + entry.element.spellLabel
                    + " weakness")
            .armourFocus(TaskStrategy.ArmourFocus.DAMAGE)
            .costPolicy(
                wilderness ? TaskStrategy.CostPolicy.LOW_RISK : TaskStrategy.CostPolicy.EFFICIENT)
            .weapons(staffPriorities(entry.element))
            .runePouch(true)
            .strictWeaponProfile(true)
            .prayers(3)
            .tags(TaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE)
            .reviewed(TaskResearch.REVIEW_DATE);
    if (wilderness) {
      builder.tags(
          TaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE, TaskStrategy.MethodTag.WILDERNESS);
    }
    return builder.build();
  }

  static int sizeForTest() {
    return ENTRIES.size();
  }

  private static String[] staffPriorities(Element element) {
    switch (element) {
      case WATER:
        return new String[] {
          "harmonised nightmare staff",
          "kodai wand",
          "mist battlestaff",
          "water battlestaff",
          "mystic water staff",
          "staff of water"
        };
      case FIRE:
        return new String[] {
          "twinflame staff",
          "harmonised nightmare staff",
          "smoke battlestaff",
          "lava battlestaff",
          "fire battlestaff",
          "mystic fire staff",
          "staff of fire"
        };
      case EARTH:
        return new String[] {
          "harmonised nightmare staff",
          "staff of the dead",
          "mud battlestaff",
          "earth battlestaff",
          "mystic earth staff",
          "staff of earth"
        };
      case AIR:
      default:
        return new String[] {
          "twinflame staff",
          "harmonised nightmare staff",
          "smoke battlestaff",
          "air battlestaff",
          "mystic air staff",
          "staff of air"
        };
    }
  }

  private static Map<String, Entry> createEntries() {
    Map<String, Entry> map = new LinkedHashMap<>();
    for (String[] row : ResourceTable.rows("slayer-elemental-weaknesses.tsv", 3)) {
      put(map, Element.valueOf(row[1]), Integer.parseInt(row[2]), row[0]);
    }
    return Collections.unmodifiableMap(map);
  }

  private static void put(
      Map<String, Entry> map, Element element, int percent, String... taskNames) {
    for (String assignment : taskNames) {
      String key = normalize(assignment);
      if (map.put(key, new Entry(element, percent)) != null) {
        throw new IllegalStateException("Duplicate elemental weakness audit: " + assignment);
      }
    }
  }

  private static String normalize(String value) {
    return value == null
        ? ""
        : value
            .toLowerCase(Locale.ENGLISH)
            .replace('\u2019', '\'')
            .replaceAll("[^a-z0-9]+", " ")
            .trim()
            .replaceFirst("^the\\s+", "");
  }
}
