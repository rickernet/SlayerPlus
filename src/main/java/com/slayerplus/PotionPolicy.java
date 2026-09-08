package com.slayerplus;

import java.util.Locale;

class PotionPolicy {
  static final String EXTENDED_STAMINA_DISPLAY = "Extended stamina potion";
  static final String EXTENDED_ANTIVENOM_DISPLAY = "Extended anti-venom+";
  static final String EXTENDED_ANTIFIRE_DISPLAY = "Extended antifire";
  static final String EXTENDED_SUPER_ANTIFIRE_DISPLAY = "Extended super antifire";
  static final String GOADING_DISPLAY = "Goading potion";

  private PotionPolicy() {}

  static String[] staminaAlternatives() {
    return SlayerLoadoutData.array("stamina_potions");
  }

  static String[] goadingAlternatives() {
    return SlayerLoadoutData.array("goading_potions");
  }

  static String[] antivenomAlternatives() {
    return SlayerLoadoutData.array("venom_protection_potions");
  }

  static String[] rangedBoostAlternatives(TaskStrategy.CostPolicy costPolicy) {
    if (costPolicy == TaskStrategy.CostPolicy.MAX_DPS) {
      return SlayerLoadoutData.array("maximum_damage_ranged_boosts");
    }
    return SlayerLoadoutData.array("efficient_ranged_boosts");
  }

  static String[] meleeBoostAlternatives(TaskStrategy.CostPolicy costPolicy) {
    return costPolicy == TaskStrategy.CostPolicy.MAX_DPS
        ? SlayerLoadoutData.array("maximum_damage_melee_boosts")
        : SlayerLoadoutData.array("efficient_melee_boosts");
  }

  static String[] magicBoostAlternatives() {
    return SlayerLoadoutData.array("magic_boosts");
  }

  static String[] shieldedAntifireAlternatives() {
    return SlayerLoadoutData.array("shield_compatible_antifire_potions");
  }

  static String[] potionOnlyAntifireAlternatives() {
    return SlayerLoadoutData.array("shield_free_antifire_potions");
  }

  static int effectiveDoseUnits(String potionFamily, int doses) {
    int safeDoses = Math.max(0, Math.min(4, doses));
    return isDoubleDurationFamily(potionFamily) ? safeDoses * 2 : safeDoses;
  }

  private static boolean isDoubleDurationFamily(String value) {
    String family = normalize(value);
    return family.equals("extended stamina potion")
        || family.equals("extended anti venom plus")
        || family.equals("extended antifire")
        || family.equals("extended super antifire");
  }

  private static String normalize(String value) {
    return value == null
        ? ""
        : value
            .toLowerCase(Locale.ENGLISH)
            .replace("+", " plus ")
            .replaceAll("[^a-z0-9]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
  }
}
