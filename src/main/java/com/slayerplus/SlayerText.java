package com.slayerplus;

import java.util.List;
import java.util.Locale;

final class SlayerText {
  private SlayerText() {}

  static String normalize(String value) {
    return value == null
        ? ""
        : value
            .toLowerCase(Locale.ENGLISH)
            .replace('\u2019', '\'')
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
  }

  static String encounter(String value) {
    return normalize(value).replaceFirst("^the\\s+", "");
  }

  static boolean matchesAny(String value, List<String> fragments) {
    for (String fragment : fragments) {
      if (value.contains(fragment)) {
        return true;
      }
    }
    return false;
  }
}
