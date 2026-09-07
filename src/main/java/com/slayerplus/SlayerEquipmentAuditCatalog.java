package com.slayerplus;

import java.util.*;
import net.runelite.api.EquipmentInventorySlot;

public final class SlayerEquipmentAuditCatalog {
  private static final Map<String, List<String>> LISTS = loadLists();
  private static final Map<String, Map<EquipmentInventorySlot, List<String>>> PROFILES =
      loadProfiles();

  private SlayerEquipmentAuditCatalog() {}

  public static List<String> priorities(
      String assignment,
      TaskStrategy strategy,
      EquipmentInventorySlot slot,
      String selectedWeaponName) {
    if (strategy == null || slot == null) {
      return Collections.emptyList();
    }
    var style = strategy.getStyle();
    var focus = strategy.getArmourFocus();
    var task = normalize(assignment);
    var weapon = normalize(selectedWeaponName);
    var prayer = focus == TaskStrategy.ArmourFocus.PRAYER;
    var magicDefence = focus == TaskStrategy.ArmourFocus.MAGIC_DEFENCE;
    var wilderness =
        strategy.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK
            || strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS);
    if (slot == EquipmentInventorySlot.HEAD) {
      if (task.contains("shellbane gryphon")) {
        return list("equipment_black_mask_i_black_mask");
      }
      if ((task.equals("blue dragon") || task.equals("blue dragons"))
          && style == TaskStrategy.CombatStyle.RANGED) {
        return list("equipment_slayer_helmet_i_black_mask_i");
      }
      return style == TaskStrategy.CombatStyle.MELEE
          ? list("equipment_slayer_helmet_i_black_mask_i_2")
          : list("equipment_slayer_helmet_i_black_mask_i_3");
    }
    if (task.equals("blue dragon") || task.equals("blue dragons")) {
      return profile(
          style == TaskStrategy.CombatStyle.MAGIC
              ? "blue-magic"
              : style == TaskStrategy.CombatStyle.MELEE ? "blue-melee" : "blue-ranged",
          slot);
    }
    if (task.equals("vorkath")) {
      return profile(
          style == TaskStrategy.CombatStyle.MELEE ? "vorkath-melee" : "vorkath-ranged", slot);
    }
    if (task.equals("tzkal zuk")) {
      return ranged(slot, weapon, false);
    }
    if (task.equals("royal titans")) {
      return royalTitans(slot);
    }
    if (task.equals("araxxor")) {
      return araxxor(slot, weapon);
    }
    if (task.equals("dagannoth rex")) {
      return profile("dagannoth-rex", slot);
    }
    if (task.equals("dagannoth kings")) {
      return profile("dagannoth-kings", slot);
    }
    if (slot == EquipmentInventorySlot.AMULET && task.contains("banshee")) {
      return list("equipment_salve_amulet_ei_salve_amulet_e");
    }
    if ((task.equals("blue dragon") || task.equals("blue dragons"))
        && style == TaskStrategy.CombatStyle.RANGED
        && slot == EquipmentInventorySlot.SHIELD) {
      return list("equipment_dragonfire_ward_anti_dragon_shield");
    }
    if ((task.equals("skeletal wyvern") || task.equals("skeletal wyverns"))
        && slot == EquipmentInventorySlot.SHIELD) {
      if (style == TaskStrategy.CombatStyle.RANGED) {
        return list("equipment_dragonfire_ward_ancient_wyvern_shield");
      }
      if (style == TaskStrategy.CombatStyle.MAGIC) {
        return list("equipment_ancient_wyvern_shield_mind_shield");
      }
      return list("equipment_ancient_wyvern_shield_dragonfire_shield");
    }
    if (task.equals("barrows brothers")) {
      return slot == EquipmentInventorySlot.HEAD
          ? list("equipment_ancestral_hat_virtus_mask")
          : profile("barrows", slot);
    }
    if (task.equals("k ril tsutsaroth") && style == TaskStrategy.CombatStyle.RANGED) {
      if (weapon.contains("bow of faerdhinen")) {
        if (slot == EquipmentInventorySlot.BODY) {
          return list("equipment_crystal_body_masori_body_f");
        }
        if (slot == EquipmentInventorySlot.LEGS) {
          return list("equipment_crystal_legs_masori_chaps_f");
        }
      }
      return profile("kril-ranged", slot);
    }
    if (task.equals("k ril tsutsaroth") && style == TaskStrategy.CombatStyle.MELEE) {
      return profile("kril-melee", slot);
    }
    if (task.equals("grotesque guardians") || task.equals("the grotesque guardians")) {
      return profile("grotesque", slot);
    }
    if (task.equals("tormented demons")) {
      return profile("tormented", slot);
    }
    if (task.equals("whisperer") || task.equals("the whisperer")) {
      if (slot == EquipmentInventorySlot.RING && weapon.contains("tumeken s shadow")) {
        return list("equipment_magus_ring_lightbearer");
      }
      return profile("whisperer", slot);
    }
    if (task.equals("kalphite queen") || task.equals("the kalphite queen")) {
      return profile(
          style == TaskStrategy.CombatStyle.MAGIC ? "kalphite-magic" : "kalphite-melee", slot);
    }
    if (task.contains("shellbane gryphon")) {
      switch (slot) {
        case BODY:
          return list("equipment_torva_platebody_oathplate_chest_4");
        case LEGS:
          return list("equipment_torva_platelegs_oathplate_legs_4");
        case SHIELD:
          return list("equipment_tortugan_shield");
        case BOOTS:
          return list("equipment_avernic_treads_echo_boots");
        default:
          break;
      }
    }
    if (task.equals("gryphon") || task.equals("gryphons")) {
      switch (slot) {
        case HEAD:
          return list("equipment_black_mask_i_black_mask");
        case BODY:
          return list("equipment_torva_platebody_oathplate_chest_5");
        case LEGS:
          return list("equipment_torva_platelegs_oathplate_legs_5");
        case BOOTS:
          return list("equipment_guardian_boots_echo_boots");
        default:
          break;
      }
    }
    if (task.contains("maggot king")) {
      return profile("maggot", slot);
    }
    if (wilderness) {
      return wilderness(style, slot);
    }
    if (slot == EquipmentInventorySlot.GLOVES && strategy.isBoss()) {
      return bossGloves(style);
    }
    switch (style) {
      case RANGED:
        return ranged(slot, weapon, prayer);
      case MAGIC:
        return magic(slot, prayer, magicDefence, assignment);
      case HYBRID:
        return hybrid(slot);
      case MELEE:
      default:
        return melee(slot, prayer, magicDefence);
    }
  }

  private static List<String> bossGloves(TaskStrategy.CombatStyle style) {
    switch (style) {
      case RANGED:
        return list("equipment_zaryte_vambraces_barrows_gloves_2");
      case MAGIC:
        return list("equipment_confliction_gauntlets_tormented_bracelet_3");
      case HYBRID:
        return list("equipment_barrows_gloves_ferocious_gloves_2");
      case MELEE:
      default:
        return list("equipment_ferocious_gloves_barrows_gloves_3");
    }
  }

  public static List<String> weaponProgression(TaskStrategy strategy) {
    if (strategy == null) {
      return Collections.emptyList();
    }
    var wilderness =
        strategy.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK
            || strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS);
    if (wilderness) {
      switch (strategy.getStyle()) {
        case MAGIC:
          return list("equipment_accursed_sceptre_thammaron_s_sceptre");
        case RANGED:
          return list("equipment_webweaver_bow_craw_s_bow");
        case MELEE:
        default:
          return list("equipment_abyssal_whip_zombie_axe");
      }
    }
    switch (strategy.getStyle()) {
      case RANGED:
        return list("equipment_venator_bow_toxic_blowpipe");
      case MAGIC:
        return list("equipment_tumeken_s_shadow_eye_of_ayak");
      case HYBRID:
        return list("equipment_tumeken_s_shadow_eye_of_ayak_2");
      case MELEE:
      default:
        return list("equipment_soulreaper_axe_ghrazi_rapier");
    }
  }

  private static List<String> melee(
      EquipmentInventorySlot slot, boolean prayer, boolean magicDefence) {
    var overlay = magicDefence ? "melee-magic-defence" : prayer ? "melee-prayer" : null;
    var values = overlay == null ? null : profile(overlay, slot);
    if (values != null && !values.isEmpty()) {
      return values;
    }
    return profile("melee", slot);
  }

  private static List<String> ranged(EquipmentInventorySlot slot, String weapon, boolean prayer) {
    if (weapon.contains("bow of faerdhinen")) {
      if (slot == EquipmentInventorySlot.BODY) {
        return prepend("crystal body", profile("ranged", slot));
      }
      if (slot == EquipmentInventorySlot.LEGS) {
        return prepend("crystal legs", profile("ranged", slot));
      }
    }
    var values = prayer ? profile("ranged-prayer", slot) : null;
    if (values != null && !values.isEmpty()) {
      return values;
    }
    return profile("ranged", slot);
  }

  private static List<String> magic(
      EquipmentInventorySlot slot, boolean prayer, boolean magicDefence, String assignment) {
    var accuracySensitive = normalize(assignment).contains("smoke devil");
    var overlay =
        magicDefence ? "magic-defence" : prayer && !accuracySensitive ? "magic-prayer" : null;
    var values = overlay == null ? null : profile(overlay, slot);
    if (values != null && !values.isEmpty()) {
      return values;
    }
    return profile("magic", slot);
  }

  private static List<String> hybrid(EquipmentInventorySlot slot) {
    return profile("hybrid", slot);
  }

  private static List<String> royalTitans(EquipmentInventorySlot slot) {
    return profile("royal-titans", slot);
  }

  private static List<String> araxxor(EquipmentInventorySlot slot, String weapon) {
    var soulreaper = weapon.contains("soulreaper axe");
    if (soulreaper) {
      if (slot == EquipmentInventorySlot.BODY) {
        return list("equipment_oathplate_chest_inquisitor_s_hauberk");
      }
      if (slot == EquipmentInventorySlot.LEGS) {
        return list("equipment_oathplate_legs_inquisitor_s_plateskirt");
      }
    }
    return profile("araxxor", slot);
  }

  private static List<String> wilderness(
      TaskStrategy.CombatStyle style, EquipmentInventorySlot slot) {
    var name =
        style == TaskStrategy.CombatStyle.MAGIC
            ? "wilderness-magic"
            : style == TaskStrategy.CombatStyle.MELEE ? "wilderness-melee" : "wilderness-ranged";
    return profile(name, slot);
  }

  private static List<String> prepend(String value, List<String> remaining) {
    var values = new String[remaining.size() + 1];
    values[0] = value;
    for (var i = 0; i < remaining.size(); i++) {
      values[i + 1] = remaining.get(i);
    }
    return values(values);
  }

  private static List<String> values(String... values) {
    return Collections.unmodifiableList(Arrays.asList(values));
  }

  private static List<String> list(String key) {
    var values = LISTS.get(key);
    if (values == null) {
      throw new IllegalStateException("Missing equipment list: " + key);
    }
    return values;
  }

  private static List<String> profile(String name, EquipmentInventorySlot slot) {
    var values = PROFILES.get(name);
    if (values == null) {
      throw new IllegalStateException("Missing equipment profile: " + name);
    }
    return values.getOrDefault(slot, Collections.emptyList());
  }

  private static Map<String, List<String>> loadLists() {
    Map<String, List<String>> lists = new LinkedHashMap<>();
    for (String[] row : ResourceTable.rows("slayer-equipment-lists.tsv", 2)) {
      lists.put(row[0], values(row[1].split("\\|", -1)));
    }
    return Collections.unmodifiableMap(lists);
  }

  private static Map<String, Map<EquipmentInventorySlot, List<String>>> loadProfiles() {
    EquipmentInventorySlot[] slots = {
      EquipmentInventorySlot.CAPE,
      EquipmentInventorySlot.AMULET,
      EquipmentInventorySlot.BODY,
      EquipmentInventorySlot.SHIELD,
      EquipmentInventorySlot.LEGS,
      EquipmentInventorySlot.GLOVES,
      EquipmentInventorySlot.BOOTS,
      EquipmentInventorySlot.RING,
      EquipmentInventorySlot.AMMO
    };
    Map<String, Map<EquipmentInventorySlot, List<String>>> profiles = new LinkedHashMap<>();
    for (String[] row : ResourceTable.rows("slayer-equipment-profiles.tsv", 10)) {
      Map<EquipmentInventorySlot, List<String>> values =
          new EnumMap<>(EquipmentInventorySlot.class);
      for (var i = 0; i < slots.length; i++) {
        if (!row[i + 1].isEmpty() && !row[i + 1].equals("-")) {
          values.put(slots[i], list(row[i + 1]));
        }
      }
      profiles.put(row[0], Collections.unmodifiableMap(values));
    }
    return Collections.unmodifiableMap(profiles);
  }

  private static String normalize(String value) {
    return SlayerText.normalize(value);
  }
}
