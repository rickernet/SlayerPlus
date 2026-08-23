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
    return new String[] {"extended stamina potion", "stamina potion"};
  }

  static String[] goadingAlternatives() {
    return new String[] {"goading potion"};
  }

  static String[] antivenomAlternatives() {
    return new String[] {"extended anti venom plus", "anti venom plus", "anti venom"};
  }

  static String[] rangedBoostAlternatives(TaskStrategy.CostPolicy costPolicy) {
    if (costPolicy == TaskStrategy.CostPolicy.MAX_DPS) {
      return new String[] {
        "divine bastion potion", "divine ranging potion", "bastion potion", "ranging potion"
      };
    }
    return new String[] {
      "ranging potion", "bastion potion", "divine ranging potion", "divine bastion potion"
    };
  }

  static String[] meleeBoostAlternatives(TaskStrategy.CostPolicy costPolicy) {
    return costPolicy == TaskStrategy.CostPolicy.MAX_DPS
        ? new String[] {"divine super combat potion", "super combat potion"}
        : new String[] {"super combat potion", "divine super combat potion"};
  }

  static String[] magicBoostAlternatives() {
    return new String[] {
      "saturated heart",
      "imbued heart",
      "forgotten brew",
      "divine battlemage potion",
      "divine magic potion",
      "battlemage potion",
      "magic potion",
      "ancient brew"
    };
  }

  static String[] shieldedAntifireAlternatives() {
    return new String[] {
      "extended antifire", "antifire potion", "extended super antifire", "super antifire"
    };
  }

  static String[] fullAntifireAlternatives() {
    return new String[] {"extended super antifire", "super antifire", "extended antifire"};
  }

  static String[] potionOnlyAntifireAlternatives() {
    return new String[] {"extended super antifire", "super antifire"};
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
