package com.slayerplus;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SlayerTravelItemPolicy {
  private static final Pattern ZERO_CHARGE_SUFFIX =
      Pattern.compile(".*\\(\\s*0\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);

  private SlayerTravelItemPolicy() {}

  public static boolean isUsableDisplayName(String displayName) {
    if (displayName == null || displayName.trim().isEmpty()) {
      return false;
    }
    if (ZERO_CHARGE_SUFFIX.matcher(displayName.trim()).matches()) {
      return false;
    }
    String normalized = normalize(displayName);
    if (normalized.isEmpty()) {
      return false;
    }
    return !normalized.contains("hallowed crystal") && !hasExplicitUnusableState(normalized);
  }

  public static boolean hasExplicitUnusableState(String value) {
    String normalized = normalize(value);
    if (normalized.isEmpty()) {
      return true;
    }
    return containsToken(normalized, "inert")
        || containsToken(normalized, "uncharged")
        || containsToken(normalized, "depleted")
        || normalized.endsWith(" no charges")
        || normalized.contains(" 0 charges");
  }

  private static boolean containsToken(String normalized, String token) {
    return normalized.equals(token)
        || normalized.startsWith(token + " ")
        || normalized.endsWith(" " + token)
        || normalized.contains(" " + token + " ");
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ENGLISH)
        .replace('\u2019', '\'')
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }
}
