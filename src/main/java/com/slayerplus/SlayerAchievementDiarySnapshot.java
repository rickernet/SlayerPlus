package com.slayerplus;

import java.util.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

final class SlayerAchievementDiarySnapshot {
  private static final SlayerAchievementDiarySnapshot EMPTY =
      new SlayerAchievementDiarySnapshot(Map.of());
  private static final String[] REGIONS = {
    "ardougne",
    "desert",
    "falador",
    "fremennik",
    "kandarin",
    "kourend",
    "lumbridge",
    "morytania",
    "varrock",
    "western",
    "wilderness"
  };
  private static final int[][] VARBITS = {
    {
      VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE,
      VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
      VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE,
      VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.DESERT_DIARY_EASY_COMPLETE,
      VarbitID.DESERT_DIARY_MEDIUM_COMPLETE,
      VarbitID.DESERT_DIARY_HARD_COMPLETE,
      VarbitID.DESERT_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.FALADOR_DIARY_EASY_COMPLETE,
      VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE,
      VarbitID.FALADOR_DIARY_HARD_COMPLETE,
      VarbitID.FALADOR_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.FREMENNIK_DIARY_EASY_COMPLETE,
      VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE,
      VarbitID.FREMENNIK_DIARY_HARD_COMPLETE,
      VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.KANDARIN_DIARY_EASY_COMPLETE,
      VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
      VarbitID.KANDARIN_DIARY_HARD_COMPLETE,
      VarbitID.KANDARIN_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.KOUREND_DIARY_EASY_COMPLETE,
      VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,
      VarbitID.KOUREND_DIARY_HARD_COMPLETE,
      VarbitID.KOUREND_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE,
      VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,
      VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE,
      VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.MORYTANIA_DIARY_EASY_COMPLETE,
      VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,
      VarbitID.MORYTANIA_DIARY_HARD_COMPLETE,
      VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.VARROCK_DIARY_EASY_COMPLETE,
      VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,
      VarbitID.VARROCK_DIARY_HARD_COMPLETE,
      VarbitID.VARROCK_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.WESTERN_DIARY_EASY_COMPLETE,
      VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,
      VarbitID.WESTERN_DIARY_HARD_COMPLETE,
      VarbitID.WESTERN_DIARY_ELITE_COMPLETE
    },
    {
      VarbitID.WILDERNESS_DIARY_EASY_COMPLETE,
      VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,
      VarbitID.WILDERNESS_DIARY_HARD_COMPLETE,
      VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE
    }
  };
  private static final String[][] REWARD_PREFIXES = {
    {"ardougne cloak ", "ardougne"},
    {"desert amulet ", "desert"},
    {"falador shield ", "falador"},
    {"fremennik sea boots ", "fremennik"},
    {"kandarin headgear ", "kandarin"},
    {"karamja gloves ", "karamja"},
    {"rada s blessing ", "kourend"},
    {"explorer s ring ", "lumbridge"},
    {"morytania legs ", "morytania"},
    {"varrock armour ", "varrock"},
    {"western banner ", "western"},
    {"wilderness sword ", "wilderness"}
  };
  private final Map<String, Integer> completedTiers;

  private SlayerAchievementDiarySnapshot(Map<String, Integer> tiers) {
    completedTiers = Map.copyOf(tiers);
  }

  static SlayerAchievementDiarySnapshot empty() {
    return EMPTY;
  }

  static SlayerAchievementDiarySnapshot capture(Client client) {
    if (client == null) {
      return EMPTY;
    }
    Map<String, Integer> tiers = new LinkedHashMap<>();
    for (int regionIndex = 0; regionIndex < REGIONS.length; regionIndex++) {
      int tier = 0;
      for (int tierIndex = 0; tierIndex < 4; tierIndex++) {
        if (client.getVarbitValue(VARBITS[regionIndex][tierIndex]) > 0) {
          tier = tierIndex + 1;
        }
      }
      tiers.put(REGIONS[regionIndex], tier);
    }
    tiers.put("karamja", client.getVarbitValue(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE) > 0 ? 4 : 0);
    return new SlayerAchievementDiarySnapshot(tiers);
  }

  boolean allowsTravelItem(String family) {
    String item = normalize(family);
    if ((item.equals("dramen staff") || item.equals("lunar staff")) && hasTier("lumbridge", 4)) {
      return false;
    }
    if (item.contains("achievement diary cape")) {
      return allEliteComplete();
    }
    Requirement requirement = Requirement.forItem(item);
    return requirement == null
        || requirement.region.equals("karamja")
        || hasTier(requirement.region, requirement.tier);
  }

  boolean hasTier(String region, int tier) {
    return completedTiers.getOrDefault(normalize(region), 0) >= Math.max(1, Math.min(4, tier));
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

  boolean allowsLoadoutReward(String name, Iterable<String> alternatives) {
    StringBuilder combined = new StringBuilder(normalize(name));
    if (alternatives != null) {
      for (String alternative : alternatives) {
        combined.append(' ').append(normalize(alternative));
      }
    }
    String value = combined.toString();
    if (value.contains("ash sanctifier")) {
      return unlocksAshSanctifier();
    }
    if (value.contains("bonecrusher")) {
      return unlocksBonecrusher();
    }
    return !value.contains("mole locator") || unlocksGiantMoleLocator();
  }

  boolean allEliteComplete() {
    return completedTiers.size() == 12
        && completedTiers.values().stream().allMatch(tier -> tier >= 4);
  }

  static SlayerAchievementDiarySnapshot forRegression(Map<String, Integer> tiers) {
    return new SlayerAchievementDiarySnapshot(tiers == null ? Map.of() : tiers);
  }

  @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
  private static final class Requirement {
    final String region;
    final int tier;

    static Requirement forItem(String item) {
      for (String[] row : REWARD_PREFIXES) {
        if (item.startsWith(row[0])) {
          for (int tier = 4; tier >= 1; tier--) {
            if (item.endsWith(" " + tier)) {
              return new Requirement(row[1], tier);
            }
          }
        }
      }
      return null;
    }
  }

  private static String normalize(String value) {
    return SlayerText.normalize(value);
  }
}
