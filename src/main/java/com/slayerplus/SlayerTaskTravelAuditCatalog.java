package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

class SlayerTaskTravelAuditCatalog {
  static final class Entry {
    private final String location;
    private final String travel;
    private final String cannon;

    private Entry(String location, String travel, String cannon) {
      this.location = location;
      this.travel = travel;
      this.cannon = cannon;
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
  }

  private static final Map<String, Entry> ENTRIES = loadEntries();

  private SlayerTaskTravelAuditCatalog() {}

  static Entry find(String taskName) {
    return ENTRIES.get(normalize(taskName));
  }

  static int sizeForRegression() {
    return ENTRIES.size();
  }

  private static Map<String, Entry> loadEntries() {
    Map<String, Entry> entries = new LinkedHashMap<>();
    for (String[] fields : ResourceTable.rows("slayer-task-travel-audit.tsv", 4)) {
      entries.put(normalize(fields[0]), new Entry(fields[1], fields[2], fields[3]));
    }
    return Collections.unmodifiableMap(entries);
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
