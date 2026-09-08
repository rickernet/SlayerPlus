package com.slayerplus;

import java.util.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

class SlayerRegularInventoryAuditCatalog {
  private static final Policy DIRECT = new Policy(2, 6, 12, true);
  private static final Policy SAFESPOT = new Policy(0, 0, 24, true);
  private static final Policy PRAYER = new Policy(5, 0, 16, true);
  private static final Map<String, Policy> POLICIES = createPolicies();

  private SlayerRegularInventoryAuditCatalog() {}

  static void apply(String assignment, MethodRules.Builder rules, TaskStrategy strategy) {
    String task = normalize(assignment);
    Policy policy = POLICIES.get(task);
    if (policy != null) {
      policy.apply(rules, strategy);
      applyTaskSpecificSupplies(task, rules, strategy);
      return;
    }
    MethodRules.DamageControl control =
        strategy == null
            ? MethodRules.DamageControl.DIRECT_DAMAGE
            : strategy.getDamageProfile() == TaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
                ? MethodRules.DamageControl.PRAYER_PROTECTED
                : strategy.getDamageProfile() == TaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING
                    ? MethodRules.DamageControl.SAFESPOT
                    : MethodRules.DamageControl.DIRECT_DAMAGE;
    (control == MethodRules.DamageControl.SAFESPOT
            ? SAFESPOT
            : control == MethodRules.DamageControl.PRAYER_PROTECTED ? PRAYER : DIRECT)
        .apply(rules, strategy);
  }

  static int sizeForTest() {
    return POLICIES.size();
  }

  private static void applyTaskSpecificSupplies(
      String task, MethodRules.Builder rules, TaskStrategy strategy) {
    if (task.equals("bandits") || task.equals("crocodiles") || task.equals("lizards")) {
      rules.require(
          "Desert heat protection",
          1,
          MethodRules.InventoryGroup.PROTECTION,
          "circlet of water",
          "desert amulet 4",
          "waterskin 4",
          "waterskin 3",
          "waterskin 2",
          "waterskin 1");
    }
    if (task.equals("harpie bug swarms")) {
      rules
          .restore("prayer potion", "super restore")
          .restoreSlots(3)
          .food(6)
          .loot(10)
          .require(
              "Poison protection",
              1,
              MethodRules.InventoryGroup.PROTECTION,
              "antidote plus plus",
              "superantipoison",
              "antipoison")
          .require("Nature runes", 200, MethodRules.InventoryGroup.RUNES, "nature rune");
    }
    if (task.equals("spiritual creatures")) {
      rules.require(
          PotionPolicy.EXTENDED_STAMINA_DISPLAY,
          1,
          MethodRules.InventoryGroup.UTILITY,
          PotionPolicy.staminaAlternatives());
    }
    if (task.equals("greater demons")) {
      rules.require("Ash sanctifier", 1, MethodRules.InventoryGroup.UTILITY, "ash sanctifier");
    }
    if (task.equals("basilisks")) {
      boolean ranged = strategy != null && strategy.getStyle() == TaskStrategy.CombatStyle.RANGED;
      rules
          .restore("prayer potion", "super restore")
          .restoreSlots(6)
          .food(ranged ? 0 : 6)
          .loot(ranged ? 16 : 9)
          .spell(MethodRules.Spellbook.STANDARD, "Telekinetic Grab / High Level Alchemy")
          .runePouch(true)
          .pouchRune(net.runelite.api.gameval.ItemID.AIRRUNE, "Air", 500)
          .pouchRune(net.runelite.api.gameval.ItemID.LAWRUNE, "Law", 200)
          .pouchRune(net.runelite.api.gameval.ItemID.NATURERUNE, "Nature", 200);
    }
    if (task.equals("aquanites")) {
      rules
          .restore("prayer potion", "super restore")
          .restoreSlots(5)
          .food(4)
          .loot(10)
          .spell(MethodRules.Spellbook.STANDARD, "High Level Alchemy")
          .runePouch(true)
          .pouchRune(net.runelite.api.gameval.ItemID.FIRERUNE, "Fire", 1000)
          .pouchRune(net.runelite.api.gameval.ItemID.NATURERUNE, "Nature", 250)
          .require(
              "Fast Slash lure-severing weapon",
              1,
              MethodRules.InventoryGroup.SWITCH,
              "saradomin godsword",
              "dragon claws",
              "voidwaker",
              "abyssal whip",
              "blade of saeldor",
              "dragon scimitar",
              "rune scimitar")
          .require("Seed box", 1, MethodRules.InventoryGroup.UTILITY, "seed box");
    }
    if (task.equals("skeletal wyvern") || task.equals("skeletal wyverns")) {
      TaskStrategy.CombatStyle style =
          strategy == null ? TaskStrategy.CombatStyle.MELEE : strategy.getStyle();
      boolean safespot =
          style == TaskStrategy.CombatStyle.RANGED || style == TaskStrategy.CombatStyle.MAGIC;
      rules
          .restore("prayer potion", "super restore")
          .restoreSlots(safespot ? 2 : 4)
          .food(safespot ? 6 : 8)
          .loot(safespot ? 16 : 11);
      if (style == TaskStrategy.CombatStyle.MELEE) {
        rules.require(
            "Special attack weapon",
            1,
            MethodRules.InventoryGroup.SWITCH,
            "voidwaker",
            "dragon claws",
            "burning claws",
            "dragon dagger p plus plus",
            "dragon dagger p plus",
            "dragon dagger p",
            "dragon dagger");
      }
      if (style != TaskStrategy.CombatStyle.MAGIC) {
        rules
            .spell(MethodRules.Spellbook.STANDARD, "High Level Alchemy")
            .runePouch(true)
            .pouchRune(net.runelite.api.gameval.ItemID.FIRERUNE, "Fire", 1000)
            .pouchRune(net.runelite.api.gameval.ItemID.NATURERUNE, "Nature", 250);
      }
    }
    if (task.equals("kalphite") || task.equals("kalphites")) {
      rules
          .restore("prayer potion", "super restore")
          .restoreSlots(5)
          .food(0)
          .loot(9)
          .require(
              "Poison protection",
              1,
              MethodRules.InventoryGroup.PROTECTION,
              "antidote plus plus",
              "sanfew serum",
              "superantipoison",
              "antipoison")
          .require(
              "Special attack weapon",
              1,
              MethodRules.InventoryGroup.SWITCH,
              "dragon dagger p plus plus",
              "dragon dagger p plus",
              "dragon dagger p",
              "dragon dagger");
    }
  }

  private static Map<String, Policy> createPolicies() {
    Map<String, Policy> map = new LinkedHashMap<>();
    for (String[] row : ResourceTable.rows("slayer-regular-inventory.tsv", 5)) {
      put(
          map,
          new Policy(
              Integer.parseInt(row[1]),
              Integer.parseInt(row[2]),
              Integer.parseInt(row[3]),
              Boolean.parseBoolean(row[4])),
          row[0]);
    }
    return Collections.unmodifiableMap(map);
  }

  private static void put(Map<String, Policy> map, Policy policy, String... taskNames) {
    for (String assignment : taskNames) {
      String key = normalize(assignment);
      if (map.put(key, policy) != null) {
        throw new IllegalStateException("Duplicate regular inventory audit: " + assignment);
      }
    }
  }

  private static String normalize(String value) {
    return SlayerText.encounter(value);
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class Policy {
    private final int restoreSlots;
    private final int food;
    private final int loot;
    private final boolean styleBoost;

    private void apply(MethodRules.Builder rules, TaskStrategy strategy) {
      boolean preventsExpectedDamage =
          strategy != null
              && (strategy.getDamageProfile() == TaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
                  || strategy.getDamageProfile()
                      == TaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING);
      rules
          .restore("prayer potion", "super restore")
          .restoreSlots(restoreSlots)
          .food(preventsExpectedDamage ? 0 : food)
          .loot(loot)
          .includeStyleBoost(styleBoost);
    }
  }
}
