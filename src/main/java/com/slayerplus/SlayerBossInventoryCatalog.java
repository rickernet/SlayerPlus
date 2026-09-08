package com.slayerplus;

import java.util.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class SlayerBossInventoryCatalog {
  public static final String REVIEW_DATE = "2026-08-13";
  private static final int DEFINITION_COLUMN_COUNT = 19;
  private static final int MAPPING_COLUMN_COUNT = 3;
  private static final int RECORD_TYPE = 0;
  private static final int POLICY_NAME = 1;
  private static final int TARGET_SLOTS = 2;
  private static final int RESTORE_SLOTS = 3;
  private static final int FOOD_SLOTS = 4;
  private static final int LOOT_SLOTS = 5;
  private static final int INCLUDE_STYLE_BOOST = 6;
  private static final int INCLUDE_MAGIC_POUCH = 7;
  private static final int LAYOUT_PROFILE = 8;
  private static final int RESTORE_NAME = 9;
  private static final int RESTORE_FALLBACK = 10;
  private static final int FOOD_NAME = 11;
  private static final int FOOD_ALTERNATIVES = 12;
  private static final int SPELLBOOK = 13;
  private static final int SPELL_NAME = 14;
  private static final int INCLUDE_RUNE_POUCH = 15;
  private static final int FILL_POLICY = 16;
  private static final int RUNE_REQUIREMENTS = 17;
  private static final int REQUIRED_ITEMS = 18;
  private static final int ENCOUNTER_NAME = 1;
  private static final int MAPPED_POLICY_NAME = 2;
  private static final Map<String, Policy> POLICIES = loadPolicies();
  private static final Set<String> REVIEWED_BOSS_KEYS = reviewedKeys();

  private SlayerBossInventoryCatalog() {}

  public static Set<String> getReviewedBossKeys() {
    return REVIEWED_BOSS_KEYS;
  }

  public static boolean apply(String assignment, MethodRules.Builder rules, TaskStrategy strategy) {
    String task = canonicalBossKey(assignment);
    if (rules == null || !REVIEWED_BOSS_KEYS.contains(task)) {
      return false;
    }
    Policy policy = POLICIES.get(policyKey(task, strategy));
    if (policy == null) {
      policy = POLICIES.get(task);
    }
    if (policy == null) {
      return false;
    }
    policy.apply(rules);
    return true;
  }

  private static String policyKey(String task, TaskStrategy strategy) {
    if (strategy == null) {
      return task;
    }
    TaskStrategy.CombatStyle style = strategy.getStyle();
    if ((task.equals("k ril tsutsaroth") || task.equals("general graardor"))
        && style == TaskStrategy.CombatStyle.RANGED) {
      return task + "#ranged";
    }
    if (task.equals("kalphite queen") && style == TaskStrategy.CombatStyle.MAGIC) {
      return task + "#magic";
    }
    if (task.equals("vorkath") && style == TaskStrategy.CombatStyle.MELEE) {
      return task + "#melee";
    }
    if (task.equals("tzkal zuk")) {
      String method = normalize(strategy.getMethod());
      if (method.contains("not afk") || method.contains("completion first")) {
        return task + "#safety";
      }
      if (strategy.getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT) {
        return task + "#efficient";
      }
    }
    return task;
  }

  public static String canonicalBossKey(String value) {
    String key = normalize(value);
    if (key.equals("cave kraken boss")) {
      return "kraken";
    }
    if (key.equals("crazy archaeologists")) {
      return "crazy archaeologist";
    }
    return key;
  }

  private static Map<String, Policy> loadPolicies() {
    Map<String, Policy> definitions = new LinkedHashMap<>();
    Map<String, Policy> policies = new LinkedHashMap<>();
    List<String[]> rows =
        ResourceTable.rows(
            "slayer-boss-inventory.tsv", MAPPING_COLUMN_COUNT, DEFINITION_COLUMN_COUNT);
    for (int row = 0; row < rows.size(); row++) {
      String[] fields = rows.get(row);
      int lineNumber = row + 1;
      if (fields.length == DEFINITION_COLUMN_COUNT && fields[RECORD_TYPE].equals("D")) {
        definitions.put(fields[POLICY_NAME], Policy.parse(fields, lineNumber));
        continue;
      }
      if (fields.length == MAPPING_COLUMN_COUNT && fields[RECORD_TYPE].equals("P")) {
        Policy policy = definitions.get(fields[MAPPED_POLICY_NAME]);
        if (policy == null)
          throw new IllegalStateException(
              "Unknown boss policy " + fields[MAPPED_POLICY_NAME] + " at row " + lineNumber);
        policies.put(restoreEscapedDelimiters(fields[ENCOUNTER_NAME]), policy);
        continue;
      }
      throw new IllegalStateException("Invalid boss policy row " + lineNumber);
    }
    return Collections.unmodifiableMap(policies);
  }

  private static Set<String> reviewedKeys() {
    Set<String> keys = new LinkedHashSet<>();
    for (String key : POLICIES.keySet()) {
      int separator = key.indexOf('#');
      keys.add(key.substring(0, separator < 0 ? key.length() : separator));
    }
    return Collections.unmodifiableSet(keys);
  }

  private static String normalize(String value) {
    return SlayerText.encounter(value);
  }

  private static String restoreEscapedDelimiters(String escapedText) {
    if (escapedText == null || escapedText.indexOf('\\') < 0) {
      return escapedText;
    }
    StringBuilder value = new StringBuilder(escapedText.length());
    boolean escaped = false;
    for (int index = 0; index < escapedText.length(); index++) {
      char character = escapedText.charAt(index);
      if (!escaped && character == '\\') {
        escaped = true;
        continue;
      }
      if (escaped) {
        switch (character) {
          case 't':
            value.append('\t');
            break;
          case 'r':
            value.append('\r');
            break;
          case 'n':
            value.append('\n');
            break;
          case 'p':
            value.append('|');
            break;
          case 'c':
            value.append(',');
            break;
          case 's':
            value.append(';');
            break;
          default:
            value.append(character);
            break;
        }
        escaped = false;
      } else {
        value.append(character);
      }
    }
    if (escaped) {
      value.append('\\');
    }
    return value.toString();
  }

  private static String[] splitList(String escapedText) {
    if (!hasValue(escapedText)) {
      return new String[0];
    }
    String[] values = escapedText.split(";", -1);
    for (int index = 0; index < values.length; index++) {
      values[index] = restoreEscapedDelimiters(values[index]);
    }
    return values;
  }

  private static boolean hasValue(String value) {
    return value != null && !value.isEmpty() && !value.equals("-");
  }

  private static final class Policy {
    private int target;
    private Integer restoreSlots;
    private Integer meals;
    private int loot;
    private boolean styleBoost;
    private boolean magicPouch;
    private MethodRules.LayoutProfile layout;
    private String restore;
    private String restoreFallback;
    private String food;
    private String[] foodAlternatives;
    private MethodRules.Spellbook spellbook;
    private String spell;
    private boolean runePouch;
    private String fill;
    private final List<Rune> runes = new ArrayList<>();
    private final List<Required> required = new ArrayList<>();

    private static Policy parse(String[] fields, int lineNumber) {
      try {
        Policy policy = new Policy();
        policy.target = Integer.parseInt(fields[TARGET_SLOTS]);
        policy.restoreSlots =
            fields[RESTORE_SLOTS].isEmpty() ? null : Integer.valueOf(fields[RESTORE_SLOTS]);
        policy.meals = fields[FOOD_SLOTS].isEmpty() ? null : Integer.valueOf(fields[FOOD_SLOTS]);
        policy.loot = Integer.parseInt(fields[LOOT_SLOTS]);
        policy.styleBoost = Boolean.parseBoolean(fields[INCLUDE_STYLE_BOOST]);
        policy.magicPouch = Boolean.parseBoolean(fields[INCLUDE_MAGIC_POUCH]);
        policy.layout = MethodRules.LayoutProfile.valueOf(fields[LAYOUT_PROFILE]);
        policy.restore = restoreEscapedDelimiters(fields[RESTORE_NAME]);
        policy.restoreFallback = restoreEscapedDelimiters(fields[RESTORE_FALLBACK]);
        policy.food = restoreEscapedDelimiters(fields[FOOD_NAME]);
        policy.foodAlternatives = splitList(fields[FOOD_ALTERNATIVES]);
        policy.spellbook = MethodRules.Spellbook.valueOf(fields[SPELLBOOK]);
        policy.spell = restoreEscapedDelimiters(fields[SPELL_NAME]);
        policy.runePouch = Boolean.parseBoolean(fields[INCLUDE_RUNE_POUCH]);
        policy.fill = fields[FILL_POLICY];
        if (hasValue(fields[RUNE_REQUIREMENTS])) {
          for (String value : fields[RUNE_REQUIREMENTS].split(";")) {
            String[] rune = value.split(",", -1);
            policy.runes.add(
                new Rune(
                    Integer.parseInt(rune[0]),
                    restoreEscapedDelimiters(rune[1]),
                    Integer.parseInt(rune[2])));
          }
        }
        if (hasValue(fields[REQUIRED_ITEMS])) {
          for (String value : fields[REQUIRED_ITEMS].split("\\|")) {
            String[] item = value.split(",", -1);
            policy.required.add(
                new Required(
                    restoreEscapedDelimiters(item[0]),
                    Integer.parseInt(item[1]),
                    Integer.parseInt(item[2]),
                    ("RUNES".equals(item[3]) || "RUNES_AMMO".equals(item[3]))
                        ? MethodRules.InventoryGroup.RUNES
                        : MethodRules.InventoryGroup.valueOf(item[3]),
                    Boolean.parseBoolean(item[4]),
                    splitList(item[5])));
          }
        }
        return policy;
      } catch (RuntimeException ex) {
        throw new IllegalStateException("Invalid boss inventory policy at row " + lineNumber, ex);
      }
    }

    private void apply(MethodRules.Builder rules) {
      rules
          .inventoryTarget(target)
          .noInventoryFill()
          .includeRunePouchForMagic(magicPouch)
          .includeStyleBoost(styleBoost)
          .loot(loot)
          .layout(layout)
          .food(food, foodAlternatives);
      if (restoreSlots != null) {
        rules.restoreSlots(restoreSlots);
      }
      if (meals != null) {
        rules.food(meals);
      }
      if (!restore.isEmpty()) {
        rules.restore(restore, restoreFallback);
        if (restoreFallback.isEmpty()) {
          rules.noRestoreFallback();
        }
      }
      if (spellbook != MethodRules.Spellbook.STRATEGY_DEFINED) {
        rules.spell(spellbook, spell);
      }
      rules.runePouch(runePouch);
      for (Rune rune : runes) {
        rules.pouchRune(rune.id, rune.name, rune.quantity);
      }
      for (Required item : required) {
        item.apply(rules);
      }
      if (fill.equals("FOOD")) {
        rules.fillFood();
      } else if (fill.equals("RESTORE")) {
        rules.fillRestore();
      }
    }
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class Rune {
    private final int id;
    private final String name;
    private final int quantity;
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class Required {
    private final String name;
    private final int quantity;
    private final int slots;
    private final MethodRules.InventoryGroup group;
    private final boolean ownedOnly;
    private final String[] alternatives;

    private void apply(MethodRules.Builder rules) {
      if (ownedOnly) {
        rules.ownedOnlyItem(name, quantity, group, alternatives);
      } else if (slots > 1) {
        rules.requiredSlots(name, slots, group, alternatives);
      } else {
        rules.require(name, quantity, group, alternatives);
      }
    }
  }
}
