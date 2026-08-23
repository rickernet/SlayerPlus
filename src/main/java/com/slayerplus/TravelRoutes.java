package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TravelRoutes {
  public static final class Option {
    private final String itemFamily;
    private final String destination;

    private Option(String itemFamily, String destination) {
      this.itemFamily = itemFamily == null ? "" : itemFamily.trim();
      this.destination = destination == null ? "" : destination.trim();
    }

    public String getItemFamily() {
      return itemFamily;
    }

    public String getDestination() {
      return destination;
    }
  }

  private static final CatalogData DATA = loadCatalog();
  private static final Map<String, List<Option>> FALLBACKS = DATA.fallbacks;
  private static final Map<String, String> LOCATION_ALIASES = DATA.aliases;

  static {
    validateOrThrow();
  }

  private TravelRoutes() {}

  public static List<Option> fallbacksFor(String location) {
    String key = normalize(location);
    String alias = LOCATION_ALIASES.get(key);
    if (alias != null) {
      key = alias;
    }
    List<Option> options = FALLBACKS.get(key);
    return options == null ? Collections.emptyList() : options;
  }

  public static int liveCandidatePreference(String family) {
    String item = normalize(family);
    if (item.isEmpty()) {
      return Integer.MIN_VALUE;
    }
    if (item.contains("max cape")) {
      return 100_000;
    }
    if (item.contains("construction cape") || item.contains("construct cape")) {
      return 90_000;
    }
    if (item.contains("eternal")) {
      return 80_000;
    }
    if (item.contains("teleport to house")
        || item.contains("house tablet")
        || item.endsWith(" tablet")
        || item.contains("teleport scroll")) {
      return 10_000;
    }
    return 50_000;
  }

  public static int liveCandidatePreference(String location, String family) {
    String candidate = normalize(family);
    List<Option> reviewed = fallbacksFor(location);
    for (int index = 0; index < reviewed.size(); index++) {
      String expected = normalize(reviewed.get(index).getItemFamily());
      if (!expected.isEmpty()
          && (candidate.equals(expected)
              || candidate.contains(expected)
              || expected.contains(candidate))) {
        return 2_000_000 - index * 200_000 + liveCandidatePreference(family);
      }
    }
    return liveCandidatePreference(family);
  }

  public static boolean requiresExternalTeleportHandoff(
      String location, String family, String destination) {
    String area = normalize(location);
    String item = normalize(family);
    String target = normalize(destination);
    return area.equals("mor ul rek east bank")
        && item.contains("ghommal")
        && (item.contains("hilt") || item.contains("avernic defender"))
        && target.equals("mor ul rek");
  }

  public static boolean allowsLiveCandidate(String location, String family, String destination) {
    String area = normalize(location);
    String item = normalize(family);
    String target = normalize(destination);
    if (area.equals("iorwerth dungeon")) {
      return target.contains("prifddinas") || item.contains("prifddinas");
    }
    if (area.equals("mourner tunnels")) {
      return item.contains("slayer ring")
          && (target.equals("dark beasts") || target.equals("me2 caves"));
    }
    return true;
  }

  public static boolean locksFirstResolvedTravelItem(String location) {
    String area = normalize(location);
    return area.equals("taverley dungeon")
        || area.equals("lassar undercity")
        || area.equals("morytania spider cave");
  }

  public static boolean locksAuthoredTravelItem(String location) {
    String area = normalize(location);
    return area.equals("lassar undercity") || area.equals("morytania spider cave");
  }

  public static void validateOrThrow() {
    for (Map.Entry<String, List<Option>> entry : FALLBACKS.entrySet()) {
      if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
        throw new IllegalStateException("Travel fallback has a blank location");
      }
      if (entry.getValue() == null || entry.getValue().isEmpty()) {
        throw new IllegalStateException(
            "Travel fallback location has no options: " + entry.getKey());
      }
      Set<String> seen = new LinkedHashSet<>();
      for (Option option : entry.getValue()) {
        if (option == null || option.itemFamily.isEmpty() || option.destination.isEmpty()) {
          throw new IllegalStateException(
              "Travel fallback has an incomplete option: " + entry.getKey());
        }
        if (SlayerTravelItemPolicy.hasExplicitUnusableState(option.itemFamily)) {
          throw new IllegalStateException(
              "Travel fallback is authored as an unusable item state: "
                  + entry.getKey()
                  + " -> "
                  + option.itemFamily);
        }
        String key = normalize(option.itemFamily) + "|" + normalize(option.destination);
        if (!seen.add(key)) {
          throw new IllegalStateException(
              "Duplicate travel fallback: " + entry.getKey() + " -> " + key);
        }
      }
    }
    if (allowsLiveCandidate("Iorwerth Dungeon", "Slayer ring (8)", "Gnome Stronghold Caves")) {
      throw new IllegalStateException(
          "Travel regression: Iorwerth Dungeon accepted a Slayer ring route");
    }
    if (!allowsLiveCandidate("Mourner Tunnels", "Slayer ring (8)", "ME2 Caves")) {
      throw new IllegalStateException(
          "Travel regression: Mourner Tunnels rejected the reviewed ME2 Caves route");
    }
    if (!(liveCandidatePreference("Max cape") > liveCandidatePreference("Construct. cape")
        && liveCandidatePreference("Construct. cape")
            > liveCandidatePreference("Teleport to house"))) {
      throw new IllegalStateException(
          "Travel regression: POH physical-item preference must be Max cape > Construction cape >"
              + " house tablet");
    }
    if (!requiresExternalTeleportHandoff("Mor Ul Rek east bank", "Ghommal's hilt 6", "Mor Ul Rek")
        || requiresExternalTeleportHandoff(
            "Catacombs of Kourend", "Xeric's talisman", "Xeric's Heart")) {
      throw new IllegalStateException(
          "Travel regression: external teleport handoff classification is incorrect");
    }
    List<Option> infernoBank = fallbacksFor("Mor Ul Rek east bank");
    if (infernoBank.isEmpty()
        || !normalize(infernoBank.get(0).itemFamily).equals("ghommal s hilt 6")
        || !normalize(infernoBank.get(0).destination).equals("mor ul rek")) {
      throw new IllegalStateException(
          "Travel regression: Inferno prep-bank fallback no longer starts with Ghommal's hilt 6 ->"
              + " Mor Ul Rek");
    }
  }

  private static CatalogData loadCatalog() {
    Map<String, List<Option>> fallbacks = new LinkedHashMap<>();
    Map<String, String> aliases = new LinkedHashMap<>();
    for (String[] fields : ResourceTable.decodedRows("slayer-travel-routes.tsv", 4)) {
      if (fields[0].equals("A")) {
        String from = normalize(fields[1]);
        String to = normalize(fields[2]);
        String previous = aliases.put(from, to);
        if (from.isEmpty() || to.isEmpty() || (previous != null && !previous.equals(to))) {
          throw new IllegalStateException("Invalid travel route alias: " + fields[1]);
        }
      } else if (fields[0].equals("F")) {
        String location = normalize(fields[1]);
        fallbacks
            .computeIfAbsent(location, ignored -> new ArrayList<>())
            .add(new Option(fields[2], fields[3]));
      } else {
        throw new IllegalStateException("Invalid travel route record type: " + fields[0]);
      }
    }
    Map<String, List<Option>> frozen = new LinkedHashMap<>();
    for (Map.Entry<String, List<Option>> entry : fallbacks.entrySet()) {
      frozen.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
    }
    return new CatalogData(
        Collections.unmodifiableMap(frozen), Collections.unmodifiableMap(aliases));
  }

  private static final class CatalogData {
    private final Map<String, List<Option>> fallbacks;
    private final Map<String, String> aliases;

    private CatalogData(Map<String, List<Option>> fallbacks, Map<String, String> aliases) {
      this.fallbacks = fallbacks;
      this.aliases = aliases;
    }
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
  }
}
