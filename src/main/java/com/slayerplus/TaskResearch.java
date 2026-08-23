package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TaskResearch {
  public static final String REVIEW_DATE = "2026-08-13";
  private static final Map<String, Entry> ENTRIES = createEntries();

  private TaskResearch() {}

  public static Entry find(String taskName) {
    return ENTRIES.get(normalize(taskName));
  }

  public static Set<String> getReviewedTaskNames() {
    Set<String> names = new LinkedHashSet<>();
    for (Entry entry : ENTRIES.values()) {
      names.add(entry.getTaskName());
    }
    return Collections.unmodifiableSet(names);
  }

  public static Set<String> getReviewedTaskKeys() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(ENTRIES.keySet()));
  }

  public static java.util.Collection<Entry> getEntries() {
    return Collections.unmodifiableCollection(ENTRIES.values());
  }

  private static Map<String, Entry> createEntries() {
    Map<String, Entry> entries = new LinkedHashMap<>();
    for (String[] row : ResourceTable.rows("slayer-task-research.tsv", 2)) {
      put(entries, row[0], Boolean.parseBoolean(row[1]));
    }
    return Collections.unmodifiableMap(entries);
  }

  private static void put(Map<String, Entry> entries, String taskName, boolean wildernessReviewed) {
    String key = normalize(taskName);
    if (entries.put(key, new Entry(taskName, REVIEW_DATE, wildernessReviewed)) != null) {
      throw new IllegalStateException("Duplicate Slayer research entry: " + taskName);
    }
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return value
        .toLowerCase(Locale.ENGLISH)
        .replace('\u2019', '\'')
        .replaceAll("[^a-z0-9]+", " ")
        .trim()
        .replaceFirst("^the\\s+", "");
  }

  public static final class Entry {
    private final String taskName;
    private final String reviewDate;
    private final boolean wildernessReviewed;

    private Entry(String taskName, String reviewDate, boolean wildernessReviewed) {
      this.taskName = taskName == null ? "" : taskName.trim();
      this.reviewDate = reviewDate == null ? "" : reviewDate.trim();
      this.wildernessReviewed = wildernessReviewed;
    }

    public String getTaskName() {
      return taskName;
    }

    public String getReviewDate() {
      return reviewDate;
    }

    public boolean isWildernessReviewed() {
      return wildernessReviewed;
    }

    public java.util.List<String> getLocations() {
      java.util.Set<String> locations =
          SlayerRecommendationEngine.catalogLocationsForValidation(taskName);
      return locations.isEmpty()
          ? java.util.Collections.singletonList("Not restricted")
          : java.util.Collections.unmodifiableList(new ArrayList<>(locations));
    }
  }
}
