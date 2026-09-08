package com.slayerplus;

import java.util.*;

/** Ordered item-name and matching-fragment data used by loadout selection and layout rendering. */
final class SlayerLoadoutData {
  private static final Map<String, List<String>> LISTS = load("slayer-loadout-lists.tsv");
  private static final Map<String, List<String>> ARRAYS = load("slayer-loadout-arrays.tsv");

  private SlayerLoadoutData() {}

  static List<String> list(String key) {
    return value(LISTS, key);
  }

  static String[] array(String key) {
    return value(ARRAYS, key).toArray(new String[0]);
  }

  private static List<String> value(Map<String, List<String>> values, String key) {
    List<String> result = values.get(key);
    if (result == null) {
      throw new IllegalStateException("Missing Slayer loadout data: " + key);
    }
    return result;
  }

  private static Map<String, List<String>> load(String resource) {
    Map<String, List<String>> values = new LinkedHashMap<>();
    for (String[] row : ResourceTable.rows(resource, 2)) {
      List<String> previous =
          values.put(row[0], Collections.unmodifiableList(Arrays.asList(row[1].split("\\|", -1))));
      if (previous != null) {
        throw new IllegalStateException("Duplicate Slayer loadout data key: " + row[0]);
      }
    }
    return Collections.unmodifiableMap(values);
  }
}
