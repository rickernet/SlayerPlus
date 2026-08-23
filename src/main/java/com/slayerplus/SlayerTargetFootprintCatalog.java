package com.slayerplus;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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

  static boolean allowsWeapon(String taskName, String weaponName) {
    String weapon = normalize(weaponName);
    if (!weapon.contains("scythe of vitur")) {
      return true;
    }
    return MULTI_TILE_SCYTHE_TARGETS.contains(normalize(taskName));
  }

  static boolean isReviewedMultiTileScytheTarget(String taskName) {
    return MULTI_TILE_SCYTHE_TARGETS.contains(normalize(taskName));
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
