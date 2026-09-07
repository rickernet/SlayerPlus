package com.slayerplus;

import java.util.List;
import java.util.Locale;
import net.runelite.api.gameval.ItemID;

final class PoweredMagic {
  // KitPlan order: head, cape, amulet, ammunition, weapon, remaining equipment.
  private static final int WEAPON_INDEX = 4;

  private PoweredMagic() {}

  static int weaponId(List<KitItem> equipment) {
    return equipment == null
            || equipment.size() <= WEAPON_INDEX
            || equipment.get(WEAPON_INDEX) == null
        ? -1
        : equipment.get(WEAPON_INDEX).getItemId();
  }

  static boolean usesBuiltInSpell(int id) {
    switch (id) {
      case ItemID.TUMEKENS_SHADOW:
      case ItemID.TUMEKENS_SHADOW_UNCHARGED:
      case ItemID.DEADMAN_BLIGHTED_TUMEKENS_SHADOW:
      case ItemID.DEADMAN_BLIGHTED_TUMEKENS_SHADOW_UNCHARGED:
      case ItemID.EYE_OF_AYAK:
      case ItemID.EYE_OF_AYAK_UNCHARGED:
      case ItemID.SANGUINESTI_STAFF:
      case ItemID.SANGUINESTI_STAFF_UNCHARGED:
      case ItemID.SANGUINESTI_STAFF_OR:
      case ItemID.SANGUINESTI_STAFF_UNCHARGED_OR:
      case ItemID.WARPED_SCEPTRE:
      case ItemID.WARPED_SCEPTRE_UNCHARGED:
      case ItemID.TOTS:
      case ItemID.TOTS_CHARGED:
      case ItemID.TOTS_UNCHARGED:
      case ItemID.TOTS_I_CHARGED:
      case ItemID.TOTS_I_UNCHARGED:
      case ItemID.TOXIC_TOTS_CHARGED:
      case ItemID.TOXIC_TOTS_UNCHARGED:
      case ItemID.TOXIC_TOTS_I_CHARGED:
      case ItemID.TOXIC_TOTS_I_UNCHARGED:
      case ItemID.TOTS_ORN:
      case ItemID.TOTS_CHARGED_ORN:
      case ItemID.TOTS_UNCHARGED_ORN:
      case ItemID.TOTS_I_CHARGED_ORN:
      case ItemID.TOTS_I_UNCHARGED_ORN:
      case ItemID.TOXIC_TOTS_CHARGED_ORN:
      case ItemID.TOXIC_TOTS_UNCHARGED_ORN:
      case ItemID.TOXIC_TOTS_I_CHARGED_ORN:
      case ItemID.TOXIC_TOTS_I_UNCHARGED_ORN:
      case ItemID.WILD_CAVE_ACCURSED_CHARGED:
      case ItemID.WILD_CAVE_ACCURSED_UNCHARGED:
      case ItemID.WILD_CAVE_SCEPTRE_CHARGED:
      case ItemID.WILD_CAVE_SCEPTRE_UNCHARGED:
      case ItemID.RAT_BONE_STAFF:
        return true;
      default:
        return false;
    }
  }

  static boolean replacesStandardAttack(String spell) {
    if (spell == null) return false;
    String name = spell.toLowerCase(Locale.ENGLISH);
    return name.equals("magic dart")
        || name.matches(
            ".*\\b(air|wind|water|earth|fire) (spells?|strike|bolt|blast|wave|surge)\\b.*");
  }
}
