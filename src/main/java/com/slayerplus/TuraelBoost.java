package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

class TuraelBoost {
  static final class Entry {
    private final String location;
    private final String travel;
    private final String cannon;
    private final String requirements;

    private Entry(String location, String travel, String cannon, String requirements) {
      this.location = location;
      this.travel = travel;
      this.cannon = cannon;
      this.requirements = requirements;
    }

    String getLocation() {
      return location;
    }

    String getTravel() {
      return travel;
    }

    String getCannon() {
      return cannon;
    }

    String getRequirements() {
      return requirements;
    }

    boolean supportsCannon() {
      String value = normalize(cannon);
      return !value.equals("not allowed") && !value.equals("not needed");
    }
  }

  private static final Map<String, Entry> ENTRIES = loadEntries();

  private TuraelBoost() {}

  static Entry find(String taskName) {
    return ENTRIES.get(normalize(taskName));
  }

  static boolean isBoostProfile(String taskName, String location) {
    Entry entry = find(taskName);
    return entry != null && normalize(entry.location).equals(normalize(location));
  }

  static int sizeForRegression() {
    return ENTRIES.size();
  }

  private static Map<String, Entry> loadEntries() {
    Map<String, Entry> entries = new LinkedHashMap<>();
    for (String[] fields : ResourceTable.rows("slayer-turael-boost.tsv", 5)) {
      put(entries, fields[0], new Entry(fields[1], fields[2], fields[3], fields[4]));
    }
    entries.put(normalize("Dwarf"), entries.get(normalize("Dwarves")));
    entries.put(normalize("Wolf"), entries.get(normalize("Wolves")));
    return Collections.unmodifiableMap(entries);
  }

  private static void put(Map<String, Entry> entries, String task, Entry entry) {
    entries.put(normalize(task), entry);
    if (normalize(task).endsWith("s")) {
      entries.put(normalize(task).replaceFirst("s$", ""), entry);
    }
  }

  private static String normalize(String value) {
    return value == null
        ? ""
        : value
            .toLowerCase(Locale.ENGLISH)
            .replace('\u2019', '\'')
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
  }
}
