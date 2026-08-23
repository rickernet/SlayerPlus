package com.slayerplus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class SlayerTeleportRouteRegistry {
  private SlayerTeleportRouteRegistry() {}

  public static String menuDestination(String itemName, String routeDestination) {
    String family = normalize(itemName);
    String destination = normalize(routeDestination);
    if (family.contains("slayer ring")
        && (destination.equals("dark beasts") || destination.equals("me2 caves"))) {
      return "ME2 Caves";
    }
    if (family.contains("slayer ring") && isWyrmscraigCavernDestination(destination)) {
      return "Wyrmscraig Cavern";
    }
    if ((family.contains("ghommal s hilt") || family.contains("ghommal s avernic defender"))
        && isMorUlRekDestination(destination)) {
      return "Mor Ul Rek";
    }
    if (family.contains("karamja gloves 4") && isDuradelDestination(destination)) {
      return "Slayer Master";
    }
    if (family.contains("karamja gloves") && isShiloMineDestination(destination)) {
      return "Gem Mine";
    }
    if (family.contains("rada s blessing") && isKonarDestination(destination)) {
      return "Mount Karuulm";
    }
    if (isAchievementDiaryCape(family)) {
      String diaryRegion = achievementDiaryRegion(destination);
      if (diaryRegion != null) {
        return diaryRegion;
      }
    }
    if ((family.contains("max cape")
            || family.contains("construction cape")
            || family.contains("construct cape"))
        && isHouseDestination(destination)) {
      return "Teleport to house";
    }
    return clean(routeDestination);
  }

  public static Set<String> destinationAliases(String itemName, String routeDestination) {
    Set<String> aliases = new LinkedHashSet<>();
    add(aliases, routeDestination);
    add(aliases, menuDestination(itemName, routeDestination));
    String family = normalize(itemName);
    String destination = normalize(routeDestination);
    if (family.contains("slayer ring")) {
      addSlayerRingAliases(aliases, destination);
    }
    if ((family.contains("ghommal s hilt") || family.contains("ghommal s avernic defender"))
        && isMorUlRekDestination(destination)) {
      add(aliases, "Mor Ul Rek");
      add(aliases, "Mor-Ul-Rek");
      add(aliases, "Inner Mor Ul Rek");
    }
    if (family.contains("karamja gloves 4") && isDuradelDestination(destination)) {
      add(aliases, "Slayer Master");
      add(aliases, "Duradel");
      add(aliases, "Kuradal");
    }
    if (family.contains("karamja gloves") && isShiloMineDestination(destination)) {
      add(aliases, "Gem Mine");
      add(aliases, "Shilo Village gem mine");
    }
    if (family.contains("rada s blessing") && isKonarDestination(destination)) {
      add(aliases, "Mount Karuulm");
      add(aliases, "Konar");
    }
    if (isAchievementDiaryCape(family)) {
      add(aliases, achievementDiaryRegion(destination));
    }
    if ((family.contains("max cape")
            || family.contains("construction cape")
            || family.contains("construct cape"))
        && isHouseDestination(destination)) {
      add(aliases, "POH");
      add(aliases, "House");
      add(aliases, "Home");
      add(aliases, "Player-owned house");
      add(aliases, "Tele to POH");
      add(aliases, "Tele to house");
      add(aliases, "Teleport to house");
      add(aliases, "Teleport to POH");
    }
    if ((family.contains("max cape")
            || family.contains("construction cape")
            || family.contains("construct cape"))
        && isPohPortalDestination(destination)) {
      add(aliases, pohPortalLeaf(routeDestination));
    }
    return aliases;
  }

  private static boolean isAchievementDiaryCape(String normalizedFamily) {
    return normalizedFamily.contains("achievement diary cape")
        || normalizedFamily.contains("achievement cape");
  }

  private static String achievementDiaryRegion(String destination) {
    String taskmaster =
        normalize(destination)
            .replaceFirst("^(?:achievement diary cape|achievement cape)\\s+", "")
            .replaceFirst("^\\d+\\s+", "")
            .trim();
    switch (taskmaster) {
      case "two pints":
        return "Ardougne";
      case "jarr":
        return "Desert";
      case "sir rebral":
        return "Falador";
      case "thorodin":
        return "Fremennik";
      case "the wedge":
      case "wedge":
        return "Kandarin";
      case "pirate jackie the fruit":
      case "pirate jackie":
        return "Karamja";
      case "elise":
        return "Kourend & Kebos";
      case "hatius cosaintus":
      case "hatius":
        return "Lumbridge & Draynor";
      case "le sabre":
      case "le sabr":
      case "le sabre taskmaster":
        return "Morytania";
      case "toby":
        return "Varrock";
      case "lesser fanatic":
        return "Wilderness";
      case "elder gnome child":
        return "Western Provinces";
      case "twiggy o korn":
      case "twiggy":
        return "Twiggy O'Korn";
      default:
        return null;
    }
  }

  public static boolean matchesEasyTeleportsConfigKey(
      String itemName, String routeDestination, String configKey) {
    String key = normalizeConfigKey(configKey);
    if (key.isEmpty()) {
      return false;
    }
    Set<String> aliases = destinationAliases(itemName, routeDestination);
    for (String alias : aliases) {
      String normalizedAlias = normalize(alias);
      if (normalizedAlias.isEmpty()) {
        continue;
      }
      if (key.equals(normalizedAlias)) {
        return true;
      }
      Set<String> aliasTokens = tokens(normalizedAlias);
      Set<String> keyTokens = tokens(key);
      if (aliasTokens.isEmpty() || keyTokens.isEmpty()) {
        continue;
      }
      Set<String> meaningfulAliasTokens = meaningfulTokens(aliasTokens);
      if (!meaningfulAliasTokens.isEmpty() && keyTokens.containsAll(meaningfulAliasTokens)) {
        return true;
      }
      Set<String> meaningfulKeyTokens = meaningfulTokens(keyTokens);
      if (meaningfulKeyTokens.size() >= 2 && aliasTokens.containsAll(meaningfulKeyTokens)) {
        return true;
      }
    }
    return false;
  }

  private static void addSlayerRingAliases(Set<String> aliases, String destination) {
    if (destination.equals("dark beasts") || destination.equals("me2 caves")) {
      add(aliases, "Dark Beasts");
      add(aliases, "ME2 Caves");
      return;
    }
    if (destination.equals("stronghold slayer cave")
        || destination.equals("gnome stronghold caves")
        || destination.equals("slayer stronghold")) {
      add(aliases, "Stronghold Slayer Cave");
      add(aliases, "Gnome Stronghold Caves");
      add(aliases, "Slayer Stronghold");
      return;
    }
    if (destination.equals("morytania slayer tower") || destination.equals("slayer tower")) {
      add(aliases, "Morytania Slayer Tower");
      add(aliases, "Slayer Tower");
      return;
    }
    if (destination.equals("rellekka slayer caves") || destination.equals("rellekka caves")) {
      add(aliases, "Rellekka Slayer Caves");
      add(aliases, "Rellekka Caves");
      return;
    }
    if (destination.equals("tarn s lair") || destination.equals("haunted mine")) {
      add(aliases, "Tarn's Lair");
      add(aliases, "Haunted Mine");
      return;
    }
    if (isWyrmscraigCavernDestination(destination)) {
      add(aliases, "Wyrmscraig Cavern");
      add(aliases, "Wyrmscraig Caverns");
      add(aliases, "Mortimer");
    }
  }

  private static boolean isWyrmscraigCavernDestination(String destination) {
    return destination.equals("wyrmscraig cavern")
        || destination.equals("wyrmscraig caverns")
        || destination.equals("mortimer");
  }

  private static boolean isMorUlRekDestination(String destination) {
    return destination.equals("mor ul rek")
        || destination.equals("morulrek")
        || destination.equals("inner mor ul rek")
        || destination.equals("inner city");
  }

  private static boolean isDuradelDestination(String destination) {
    return destination.equals("duradel")
        || destination.equals("kuradal")
        || destination.equals("slayer master");
  }

  private static boolean isShiloMineDestination(String destination) {
    return destination.equals("gem mine")
        || destination.equals("shilo village gem mine")
        || destination.equals("shilo village mine");
  }

  private static boolean isKonarDestination(String destination) {
    return destination.equals("konar")
        || destination.equals("mount karuulm")
        || destination.equals("karuulm");
  }

  private static boolean isHouseDestination(String destination) {
    return destination.equals("poh")
        || destination.equals("house")
        || destination.equals("home")
        || destination.equals("player owned house")
        || destination.equals("tele to house")
        || destination.equals("tele to poh")
        || destination.startsWith("tele to house ")
        || destination.startsWith("tele to poh ")
        || destination.equals("teleport to house")
        || destination.equals("teleport to poh")
        || destination.startsWith("teleport to house ")
        || destination.startsWith("teleport to poh ");
  }

  private static boolean isPohPortalDestination(String destination) {
    return destination.startsWith("poh portal ") || destination.startsWith("poh portals ");
  }

  private static String pohPortalLeaf(String routeDestination) {
    String destination = clean(routeDestination);
    String leaf = destination.replaceFirst("(?i)^POH\\s+Portals?\\s*:?\\s*", "");
    return leaf.equals(destination) ? "" : leaf;
  }

  private static Set<String> meaningfulTokens(Set<String> values) {
    Set<String> result = new HashSet<>();
    for (String token : values) {
      if (token.length() < 3 || GENERIC_CONFIG_TOKENS.contains(token)) {
        continue;
      }
      result.add(token);
    }
    return result;
  }

  private static Set<String> tokens(String value) {
    String normalized = normalize(value);
    if (normalized.isEmpty()) {
      return new HashSet<>();
    }
    return new HashSet<>(Arrays.asList(normalized.split("\\s+")));
  }

  private static final Set<String> GENERIC_CONFIG_TOKENS =
      new HashSet<>(
          Arrays.asList(
              "replacement",
              "teleport",
              "teleports",
              "ring",
              "necklace",
              "amulet",
              "bracelet",
              "cape",
              "gloves",
              "legs",
              "blessing",
              "pendant",
              "talisman",
              "games",
              "skills",
              "combat",
              "wealth",
              "glory",
              "slayer",
              "max",
              "construction",
              "construct",
              "radas",
              "rad",
              "of",
              "the",
              "to"));

  private static String normalizeConfigKey(String key) {
    if (key == null) {
      return "";
    }
    String value = key.replaceFirst("^replacement", "");
    value = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
    return normalize(value);
  }

  private static void add(Set<String> values, String value) {
    String clean = clean(value);
    if (!clean.isEmpty()) {
      values.add(clean);
    }
  }

  private static String clean(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("(?i)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
  }

  private static String normalize(String value) {
    return clean(value)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }
}
