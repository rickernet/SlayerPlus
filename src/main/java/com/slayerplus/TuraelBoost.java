package com.slayerplus;

import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

class TuraelBoost {
  @Getter(AccessLevel.PACKAGE)
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  static final class Entry {
    private final String location;
    private final String travel;
    private final String cannon;
    private final String requirements;

    boolean supportsCannon() {
      String value = normalize(cannon);
      return !value.equals("not allowed") && !value.equals("not needed");
    }
  }

  private static final Map<String, Entry> ENTRIES = loadEntries();

  private TuraelBoost() {}

  static Entry find(String assignment) {
    return ENTRIES.get(normalize(assignment));
  }

  static int sizeForTest() {
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
    return SlayerText.normalize(value);
  }
}
