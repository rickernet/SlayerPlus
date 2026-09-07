package com.slayerplus;

class HelmetPreference {
  static final String CONFIG_KEY = "slayerHelmetPreference";
  static final String COMBAT_ACHIEVEMENT = "Combat achievement";
  static final String RANDOM_OWNED = "Random owned";

  private HelmetPreference() {}

  static String normalize(String value) {
    if (value == null || value.trim().isEmpty()) {
      return COMBAT_ACHIEVEMENT;
    }
    String normalized = value.trim();
    return "Automatic".equals(normalized) || "Automatic (best)".equals(normalized)
        ? COMBAT_ACHIEVEMENT
        : normalized;
  }

  static boolean isCombatAchievement(String value) {
    return COMBAT_ACHIEVEMENT.equals(normalize(value));
  }

  static boolean isRandom(String value) {
    return RANDOM_OWNED.equals(normalize(value));
  }
}
