package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

class SlayerAchievementDiarySnapshot {
  private static final SlayerAchievementDiarySnapshot EMPTY =
      new SlayerAchievementDiarySnapshot(Collections.emptyMap());
  private final Map<String, Integer> completedTiers;

  private SlayerAchievementDiarySnapshot(Map<String, Integer> completedTiers) {
    this.completedTiers = Collections.unmodifiableMap(new LinkedHashMap<>(completedTiers));
  }

  static SlayerAchievementDiarySnapshot empty() {
    return EMPTY;
  }

  static SlayerAchievementDiarySnapshot capture(Client client) {
    if (client == null) {
      return EMPTY;
    }
    Map<String, Integer> tiers = new LinkedHashMap<>();
    put(
        tiers,
        "ardougne",
        client,
        VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE,
        VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
        VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE,
        VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "desert",
        client,
        VarbitID.DESERT_DIARY_EASY_COMPLETE,
        VarbitID.DESERT_DIARY_MEDIUM_COMPLETE,
        VarbitID.DESERT_DIARY_HARD_COMPLETE,
        VarbitID.DESERT_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "falador",
        client,
        VarbitID.FALADOR_DIARY_EASY_COMPLETE,
        VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE,
        VarbitID.FALADOR_DIARY_HARD_COMPLETE,
        VarbitID.FALADOR_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "fremennik",
        client,
        VarbitID.FREMENNIK_DIARY_EASY_COMPLETE,
        VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE,
        VarbitID.FREMENNIK_DIARY_HARD_COMPLETE,
        VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "kandarin",
        client,
        VarbitID.KANDARIN_DIARY_EASY_COMPLETE,
        VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
        VarbitID.KANDARIN_DIARY_HARD_COMPLETE,
        VarbitID.KANDARIN_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "kourend",
        client,
        VarbitID.KOUREND_DIARY_EASY_COMPLETE,
        VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,
        VarbitID.KOUREND_DIARY_HARD_COMPLETE,
        VarbitID.KOUREND_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "lumbridge",
        client,
        VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE,
        VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,
        VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE,
        VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "morytania",
        client,
        VarbitID.MORYTANIA_DIARY_EASY_COMPLETE,
        VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,
        VarbitID.MORYTANIA_DIARY_HARD_COMPLETE,
        VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "varrock",
        client,
        VarbitID.VARROCK_DIARY_EASY_COMPLETE,
        VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,
        VarbitID.VARROCK_DIARY_HARD_COMPLETE,
        VarbitID.VARROCK_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "western",
        client,
        VarbitID.WESTERN_DIARY_EASY_COMPLETE,
        VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,
        VarbitID.WESTERN_DIARY_HARD_COMPLETE,
        VarbitID.WESTERN_DIARY_ELITE_COMPLETE);
    put(
        tiers,
        "wilderness",
        client,
        VarbitID.WILDERNESS_DIARY_EASY_COMPLETE,
        VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,
        VarbitID.WILDERNESS_DIARY_HARD_COMPLETE,
        VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE);
    tiers.put("karamja", client.getVarbitValue(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE) > 0 ? 4 : 0);
    return new SlayerAchievementDiarySnapshot(tiers);
  }

  private static void put(
      Map<String, Integer> tiers,
      String region,
      Client client,
      int easy,
      int medium,
      int hard,
      int elite) {
    int tier = 0;
    if (client.getVarbitValue(easy) > 0) tier = 1;
    if (client.getVarbitValue(medium) > 0) tier = 2;
    if (client.getVarbitValue(hard) > 0) tier = 3;
    if (client.getVarbitValue(elite) > 0) tier = 4;
    tiers.put(region, tier);
  }

  boolean allowsTravelItem(String itemFamily) {
    String item = normalize(itemFamily);
    if ((item.equals("dramen staff") || item.equals("lunar staff")) && hasTier("lumbridge", 4)) {
      return false;
    }
    if (item.contains("achievement diary cape")) {
      return allEliteComplete();
    }
    Requirement requirement = Requirement.forItem(item);
    if (requirement == null) {
      return true;
    }
    if (requirement.region.equals("karamja")) {
      return true;
    }
    return completedTiers.getOrDefault(requirement.region, 0) >= requirement.tier;
  }

  boolean hasTier(String region, int tier) {
    return completedTiers.getOrDefault(normalize(region), 0) >= Math.max(1, Math.min(4, tier));
  }

  boolean improvesEnchantedBoltSpecials() {
    return hasTier("kandarin", 3);
  }

  boolean unlocksAshSanctifier() {
    return hasTier("kourend", 3);
  }

  boolean unlocksBonecrusher() {
    return hasTier("morytania", 3);
  }

  boolean unlocksGiantMoleLocator() {
    return hasTier("falador", 3);
  }

  boolean removesKaruulmBootRequirement() {
    return hasTier("kourend", 4);
  }

  boolean improvesBarrowsRuneRewards() {
    return hasTier("morytania", 3);
  }

  int slayerTowerExperienceBonusTenthsPercent() {
    return Math.min(4, completedTiers.getOrDefault("morytania", 0)) * 25;
  }

  boolean hasFightCavesDailyResurrection() {
    return hasTier("karamja", 4);
  }

  boolean hasZulrahDailyResurrection() {
    return hasTier("western", 4);
  }

  boolean notesBrimhavenDungeonDrops() {
    return hasTier("karamja", 4);
  }

  boolean notesWildernessDragonBones() {
    return hasTier("wilderness", 4);
  }

  boolean notesAviansieAdamantBars() {
    return hasTier("fremennik", 3);
  }

  boolean notesDagannothKingBones() {
    return hasTier("fremennik", 4);
  }

  boolean allowsWildernessBossLair(String encounterName) {
    String encounter = normalize(encounterName);
    if (encounter.equals("callisto")
        || encounter.equals("venenatis")
        || encounter.equals("vet ion")) {
      return hasTier("wilderness", 2);
    }
    if (encounter.equals("artio")
        || encounter.equals("spindel")
        || encounter.equals("calvar ion")) {
      return hasTier("wilderness", 3);
    }
    return true;
  }

  boolean allowsLoadoutReward(String displayName, Iterable<String> alternatives) {
    String combined = normalize(displayName);
    if (alternatives != null) {
      for (String alternative : alternatives) {
        combined += " " + normalize(alternative);
      }
    }
    if (combined.contains("ash sanctifier")) {
      return unlocksAshSanctifier();
    }
    if (combined.contains("bonecrusher")) {
      return unlocksBonecrusher();
    }
    if (combined.contains("mole locator")) {
      return unlocksGiantMoleLocator();
    }
    return true;
  }

  boolean allEliteComplete() {
    return completedTiers.size() == 12
        && completedTiers.values().stream().allMatch(tier -> tier >= 4);
  }

  static SlayerAchievementDiarySnapshot forRegression(Map<String, Integer> completedTiers) {
    return new SlayerAchievementDiarySnapshot(
        completedTiers == null ? Collections.emptyMap() : completedTiers);
  }

  private static final class Requirement {
    private final String region;
    private final int tier;

    private Requirement(String region, int tier) {
      this.region = region;
      this.tier = tier;
    }

    private static Requirement forItem(String item) {
      if (item.startsWith("ardougne cloak ")) return numbered("ardougne", item);
      if (item.startsWith("desert amulet ")) return numbered("desert", item);
      if (item.startsWith("falador shield ")) return numbered("falador", item);
      if (item.startsWith("fremennik sea boots ")) return numbered("fremennik", item);
      if (item.startsWith("kandarin headgear ")) return numbered("kandarin", item);
      if (item.startsWith("karamja gloves ")) return numbered("karamja", item);
      if (item.startsWith("rada s blessing ")) return numbered("kourend", item);
      if (item.startsWith("explorer s ring ")) return numbered("lumbridge", item);
      if (item.startsWith("morytania legs ")) return numbered("morytania", item);
      if (item.startsWith("varrock armour ")) return numbered("varrock", item);
      if (item.startsWith("western banner ")) return numbered("western", item);
      if (item.startsWith("wilderness sword ")) return numbered("wilderness", item);
      return null;
    }

    private static Requirement numbered(String region, String item) {
      for (int tier = 4; tier >= 1; tier--) {
        if (item.endsWith(" " + tier)) {
          return new Requirement(region, tier);
        }
      }
      return null;
    }
  }

  private static String normalize(String value) {
    return value == null
        ? ""
        : value
            .toLowerCase(Locale.ENGLISH)
            .replace('\u2019', '\'')
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
  }
}
