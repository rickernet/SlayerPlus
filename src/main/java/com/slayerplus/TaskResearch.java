package com.slayerplus;

import java.util.*;
import lombok.Getter;

public final class TaskResearch {
  public static final String REVIEW_DATE = "2026-08-13";
  private static final Map<String, Entry> ENTRIES = createEntries();

  private TaskResearch() {}

  public static Entry find(String assignment) {
    return ENTRIES.get(normalize(assignment));
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

  private static void put(
      Map<String, Entry> entries, String assignment, boolean wildernessReviewed) {
    String key = normalize(assignment);
    if (entries.put(key, new Entry(assignment, REVIEW_DATE, wildernessReviewed)) != null) {
      throw new IllegalStateException("Duplicate Slayer research entry: " + assignment);
    }
  }

  private static String normalize(String value) {
    return SlayerText.encounter(value);
  }

  @Getter
  public static final class Entry {
    private final String assignment;
    private final String reviewDate;
    private final boolean wildernessReviewed;

    private Entry(String assignment, String reviewDate, boolean wildernessReviewed) {
      this.assignment = assignment == null ? "" : assignment.trim();
      this.reviewDate = reviewDate == null ? "" : reviewDate.trim();
      this.wildernessReviewed = wildernessReviewed;
    }

    public String getTaskName() {
      return assignment;
    }

    public java.util.List<String> getLocations() {
      java.util.Set<String> locations =
          SlayerRecommendationEngine.catalogLocationsForValidation(assignment);
      return locations.isEmpty()
          ? Collections.singletonList("Not restricted")
          : Collections.unmodifiableList(new ArrayList<>(locations));
    }
  }
}
