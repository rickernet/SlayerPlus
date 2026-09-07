package com.slayerplus;

import java.util.*;

class SlayerTargetFootprintCatalog {
  private static final Set<String> MULTI_TILE_SCYTHE_TARGETS =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "abyssal sire",
                  "the abyssal sire",
                  "alchemical hydra",
                  "the alchemical hydra",
                  "amoxliatl",
                  "araxxor",
                  "cerberus",
                  "dark beast",
                  "dark beasts",
                  "dagannoth kings",
                  "duke sucellus",
                  "giant mole",
                  "grotesque guardians",
                  "the grotesque guardians",
                  "hellhound",
                  "hellhounds",
                  "kalphite queen",
                  "the kalphite queen",
                  "maggot king",
                  "the maggot king",
                  "sarachnis",
                  "skotizo",
                  "thermonuclear smoke devil",
                  "the thermonuclear smoke devil",
                  "vardorvis")));

  private SlayerTargetFootprintCatalog() {}

  static boolean allowsWeapon(String assignment, String weapon) {
    String key = normalize(weapon);
    if (!key.contains("scythe of vitur")) {
      return true;
    }
    return MULTI_TILE_SCYTHE_TARGETS.contains(normalize(assignment));
  }

  static boolean isReviewedMultiTileScytheTarget(String assignment) {
    return MULTI_TILE_SCYTHE_TARGETS.contains(normalize(assignment));
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ROOT)
        .replace('\u2019', '\'')
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }
}
