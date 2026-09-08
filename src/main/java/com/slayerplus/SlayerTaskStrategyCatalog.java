package com.slayerplus;

import java.util.*;

public final class SlayerTaskStrategyCatalog {
  private static final int STRATEGY_COLUMN_COUNT = 26;
  private static final int PROFILE_COLUMN_COUNT = 3;
  private static final int RECORD_TYPE = 0;
  private static final int STRATEGY_NAME = 1;
  private static final int COMBAT_STYLE = 2;
  private static final int ARMOUR_FOCUS = 3;
  private static final int COST_POLICY = 4;
  private static final int METHOD = 5;
  private static final int RATIONALE = 6;
  private static final int SELECTION_NOTE = 7;
  private static final int REVIEW_DATE = 8;
  private static final int WEAPONS = 9;
  private static final int MAX_DPS_WEAPONS = 10;
  private static final int EFFICIENT_WEAPONS = 11;
  private static final int OPTIONAL_ITEMS = 12;
  private static final int METHOD_TAGS = 13;
  private static final int PRAYER_SLOTS = 14;
  private static final int FOOD_SLOTS = 15;
  private static final int DAMAGE_PROFILE = 16;
  private static final int EXPECTED_DAMAGE_PER_KILL = 17;
  private static final int MINIMUM_FOOD_SLOTS = 18;
  private static final int INVENTORY_TARGET_SLOTS = 19;
  private static final int INCLUDE_RUNE_POUCH = 20;
  private static final int INCLUDE_ANTIVENOM = 21;
  private static final int INCLUDE_STAMINA = 22;
  private static final int STRICT_WEAPON_PROFILE = 23;
  private static final int BOSS_PROFILE = 24;
  private static final int REVIEWED_PROFILE = 25;
  private static final int PROFILE_KEY = 1;
  private static final int PROFILE_MAPPINGS = 2;
  private static final Map<String, Profiles> DATA = loadData();
  private static final Map<String, String> TASK_ALIASES = loadTaskAliases();
  private static final Map<String, Profiles> PROFILES = baseProfiles(DATA);
  private static final Set<String> COVERED_TASKS = TaskResearch.getReviewedTaskNames();
  private static final Set<String> REVIEWED_TASK_KEYS = TaskResearch.getReviewedTaskKeys();

  private SlayerTaskStrategyCatalog() {}

  public static TaskStrategy resolve(
      String assignment,
      Preference.Cannon cannonPreference,
      Preference.Burst burstPreference,
      Preference.CombatStyle combatPreference,
      String location,
      boolean wilderness) {
    var task = canonicalTaskKey(assignment);
    if (!REVIEWED_TASK_KEYS.contains(task)) {
      return unreviewed(assignment);
    }
    if (wilderness) {
      return wildernessStrategyFor(
          assignment, task, cannonPreference, burstPreference, combatPreference);
    }
    var base = PROFILES.get(task);
    if (base == null) {
      return unreviewed(assignment);
    }
    var profiles = profilesForLocation(task, normalize(location), base);
    TaskStrategy selected = null;
    if (combatPreference == Preference.CombatStyle.PREFER_MAGIC && profiles.magic == null) {
      var automatic = profiles.selectAutomatic();
      if (automatic != null
          && !automatic.isBoss()
          && automatic.getStyle() != TaskStrategy.CombatStyle.HYBRID) {
        var elemental = SlayerElementalWeaknessCatalog.preferenceStrategy(assignment, false);
        if (elemental != null) {
          selected =
              elemental.withSelectionNote(
                  "Magic preference applied using the current Wiki-listed elemental weakness;"
                      + " Automatic keeps the reviewed practical method.");
        }
      }
    }
    if (selected == null) {
      selected = profiles.select(cannonPreference, burstPreference, combatPreference);
    }
    if (selected != null
        && !selected.isBoss()
        && cannonPreference == Preference.Cannon.PREFER
        && !selected.hasTag(TaskStrategy.MethodTag.CANNON)
        && locationAllowsCannon(assignment, location)
        && cannonSupportsSelectedCombat(assignment, selected)) {
      selected =
          selected
              .withAdditionalTags(TaskStrategy.MethodTag.CANNON)
              .withSelectionNote(
                  "Cannon preference applied because the individually audited task location permits"
                      + " a dwarf multicannon.");
    }
    return selected;
  }

  private static boolean cannonSupportsSelectedCombat(String assignment, TaskStrategy strategy) {
    return strategy != null
        && (!canonicalTaskKey(assignment).equals("greater demons")
            || strategy.getStyle() != TaskStrategy.CombatStyle.MELEE);
  }

  private static boolean locationAllowsCannon(String assignment, String location) {
    var area = normalize(location);
    var task = canonicalTaskKey(assignment);
    if (task.equals("greater demons") && area.contains("karuulm")) {
      return true;
    }
    return !area.contains("catacombs")
        && !area.contains("slayer tower")
        && !area.contains("fremennik slayer dungeon")
        && !area.contains("karuulm")
        && !area.contains("kraken")
        && !area.contains("god wars")
        && !area.contains("mos le harmless");
  }

  public static TaskStrategy legacy(String method) {
    return unreviewed("Legacy recommendation");
  }

  public static boolean hasExplicitStrategy(String assignment) {
    return REVIEWED_TASK_KEYS.contains(canonicalTaskKey(assignment));
  }

  public static List<String> getCurrentTaskNames() {
    return Collections.unmodifiableList(new ArrayList<>(COVERED_TASKS));
  }

  public static TaskResearch.Entry getResearchRecord(String assignment) {
    return TaskResearch.find(canonicalTaskKey(assignment));
  }

  private static TaskStrategy wildernessStrategyFor(
      String assignment,
      String task,
      Preference.Cannon cannonPreference,
      Preference.Burst burstPreference,
      Preference.CombatStyle combatPreference) {
    var research = TaskResearch.find(assignment);
    if (research == null || !research.isWildernessReviewed()) {
      return unreviewedWilderness(assignment);
    }
    var base = PROFILES.get(task);
    if (base != null) {
      var selected = base.select(cannonPreference, burstPreference, combatPreference);
      if (selected != null
          && selected.hasTag(TaskStrategy.MethodTag.WILDERNESS)
          && selected.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK) {
        return selected.withSelectionNote("Reviewed low-risk Wilderness profile applied.");
      }
    }
    var wilderness = DATA.get("wilderness:" + task);
    if (wilderness != null) {
      return wilderness
          .select(cannonPreference, burstPreference, combatPreference)
          .withSelectionNote(
              "Krystilia assignment: individually reviewed LOW_RISK Wilderness branch applied.");
    }
    return unreviewedWilderness(assignment);
  }

  private static Profiles profilesForLocation(String task, String location, Profiles fallback) {
    if (task.equals("ankou")) {
      if (location.contains("stronghold slayer cave")) {
        return data("location:ankou:stronghold_slayer_cave", fallback);
      }
      if (location.contains("stronghold of security")) {
        return data("location:ankou:stronghold_of_security", fallback);
      }
    }
    if (task.equals("abyssal demons") && location.contains("slayer tower")) {
      return data("location:abyssal_demons:slayer_tower", fallback);
    }
    if (task.equals("bloodveld")) {
      if (location.contains("meiyerditch") || location.contains("buccaneer")) {
        return data("location:bloodveld:multi_cannon", fallback);
      }
      if (location.contains("iorwerth") || location.contains("stronghold slayer cave")) {
        return data("location:bloodveld:single_cannon", fallback);
      }
      if (location.contains("catacombs")) {
        return data("location:bloodveld:catacombs", fallback);
      }
      if (location.contains("slayer tower")) {
        return data("location:bloodveld:slayer_tower", fallback);
      }
      if (location.contains("god wars dungeon")) {
        return data("location:bloodveld:god_wars", fallback);
      }
    }
    if (task.equals("aberrant spectres")) {
      if (location.contains("stronghold slayer cave")) {
        return data("location:aberrant_spectres:stronghold_slayer_cave", fallback);
      }
      if (location.contains("catacombs")) {
        return data("location:aberrant_spectres:catacombs", fallback);
      }
    }
    if (task.equals("hellhounds")) {
      if (location.contains("stronghold slayer cave")
          || location.contains("taverley dungeon")
          || location.contains("karuulm slayer dungeon")) {
        return data("location:hellhounds:cannon", fallback);
      }
      if (location.contains("catacombs")) {
        return data("location:hellhounds:catacombs", fallback);
      }
    }
    if (task.equals("greater demons") && locationAllowsCannon(task, location)) {
      return data("location:greater_demons:cannon", fallback);
    }
    if (task.equals("nechryael") && location.contains("iorwerth")) {
      return data("location:nechryael:iorwerth", fallback);
    }
    return fallback;
  }

  private static Profiles data(String key, Profiles fallback) {
    var profiles = DATA.get(key);
    return profiles == null ? fallback : profiles;
  }

  private static TaskStrategy unreviewed(String assignment) {
    return TaskStrategy.builder(
            TaskStrategy.CombatStyle.HYBRID, "Research pending — loadout disabled")
        .rationale(
            (assignment == null ? "This task" : assignment)
                + " has not yet passed individual strategy review. SlayerPlus will not invent a"
                + " broad equipment template.")
        .prayers(0)
        .food(0)
        .strictWeaponProfile(true)
        .build();
  }

  private static TaskStrategy unreviewedWilderness(String assignment) {
    return TaskStrategy.builder(
            TaskStrategy.CombatStyle.HYBRID, "Wilderness research pending — loadout disabled")
        .costPolicy(TaskStrategy.CostPolicy.LOW_RISK)
        .rationale(
            (assignment == null ? "This task" : assignment)
                + " has no individually reviewed Krystilia profile. The normal-task profile is"
                + " never reused in the Wilderness.")
        .tags(TaskStrategy.MethodTag.WILDERNESS)
        .prayers(0)
        .food(0)
        .strictWeaponProfile(true)
        .build();
  }

  private static Map<String, Profiles> loadData() {
    Map<String, TaskStrategy> strategies = new LinkedHashMap<>();
    Map<String, Profiles> profiles = new LinkedHashMap<>();
    var lineNumber = 0;
    try {
      for (String[] fields :
          ResourceTable.rows(
              "slayer-task-strategies.tsv", PROFILE_COLUMN_COUNT, STRATEGY_COLUMN_COUNT)) {
        lineNumber++;
        if (fields.length == STRATEGY_COLUMN_COUNT && "S".equals(fields[RECORD_TYPE])) {
          var id = restoreEscapedDelimiters(fields[STRATEGY_NAME]);
          if (strategies.put(id, parseStrategy(fields, lineNumber)) != null) {
            throw new IllegalStateException("Duplicate Slayer strategy id " + id);
          }
          continue;
        }
        if (fields.length == PROFILE_COLUMN_COUNT && "P".equals(fields[RECORD_TYPE])) {
          var key = restoreEscapedDelimiters(fields[PROFILE_KEY]);
          var group = new Profiles();
          for (String mapping : fields[PROFILE_MAPPINGS].split(";", -1)) {
            var separator = mapping.indexOf('=');
            if (separator <= 0 || separator == mapping.length() - 1) {
              throw new IllegalStateException(
                  "Invalid Slayer profile mapping at row " + lineNumber);
            }
            var id = mapping.substring(separator + 1);
            var strategy = strategies.get(id);
            if (strategy == null) {
              throw new IllegalStateException(
                  "Unknown Slayer strategy id " + id + " at row " + lineNumber);
            }
            group.set(mapping.substring(0, separator), strategy);
          }
          if (profiles.put(key, group) != null) {
            throw new IllegalStateException("Duplicate Slayer profile " + key);
          }
          continue;
        }
        throw new IllegalStateException(
            "Invalid Slayer strategy row " + lineNumber + ": expected an S or P record");
      }
    } catch (RuntimeException ex) {
      throw new IllegalStateException("Unable to load reviewed Slayer strategies", ex);
    }
    for (Map.Entry<String, Profiles> entry : profiles.entrySet()) {
      entry.getValue().validate(entry.getKey());
    }
    return Collections.unmodifiableMap(profiles);
  }

  private static TaskStrategy parseStrategy(String[] fields, int lineNumber) {
    try {
      var builder =
          TaskStrategy.builder(
                  TaskStrategy.CombatStyle.valueOf(restoreEscapedDelimiters(fields[COMBAT_STYLE])),
                  restoreEscapedDelimiters(fields[METHOD]))
              .armourFocus(
                  TaskStrategy.ArmourFocus.valueOf(restoreEscapedDelimiters(fields[ARMOUR_FOCUS])))
              .costPolicy(
                  TaskStrategy.CostPolicy.valueOf(restoreEscapedDelimiters(fields[COST_POLICY])))
              .rationale(restoreEscapedDelimiters(fields[RATIONALE]))
              .selectionNote(restoreEscapedDelimiters(fields[SELECTION_NOTE]))
              .weapons(splitList(fields[WEAPONS]))
              .maxDpsWeapons(splitList(fields[MAX_DPS_WEAPONS]))
              .efficientWeapons(splitList(fields[EFFICIENT_WEAPONS]))
              .optionalItems(splitList(fields[OPTIONAL_ITEMS]))
              .tags(tags(fields[METHOD_TAGS]))
              .prayers(Integer.parseInt(fields[PRAYER_SLOTS]))
              .food(Integer.parseInt(fields[FOOD_SLOTS]))
              .damageProfile(TaskStrategy.DamageProfile.valueOf(fields[DAMAGE_PROFILE]))
              .damagePerKill(Double.parseDouble(fields[EXPECTED_DAMAGE_PER_KILL]))
              .minimumFoodSlots(Integer.parseInt(fields[MINIMUM_FOOD_SLOTS]))
              .inventoryTargetSlots(Integer.parseInt(fields[INVENTORY_TARGET_SLOTS]))
              .runePouch(Boolean.parseBoolean(fields[INCLUDE_RUNE_POUCH]))
              .antivenom(Boolean.parseBoolean(fields[INCLUDE_ANTIVENOM]))
              .stamina(Boolean.parseBoolean(fields[INCLUDE_STAMINA]))
              .strictWeaponProfile(Boolean.parseBoolean(fields[STRICT_WEAPON_PROFILE]))
              .boss(Boolean.parseBoolean(fields[BOSS_PROFILE]));
      if (Boolean.parseBoolean(fields[REVIEWED_PROFILE])) {
        builder.reviewed(restoreEscapedDelimiters(fields[REVIEW_DATE]));
      }
      return builder.build();
    } catch (RuntimeException ex) {
      throw new IllegalStateException("Invalid Slayer strategy at row " + lineNumber, ex);
    }
  }

  private static String[] splitList(String escapedText) {
    var value = restoreEscapedDelimiters(escapedText);
    return value.isEmpty() ? new String[0] : value.split("; ", -1);
  }

  private static TaskStrategy.MethodTag[] tags(String escapedText) {
    var values = splitList(escapedText);
    var tags = new TaskStrategy.MethodTag[values.length];
    for (var index = 0; index < values.length; index++) {
      tags[index] =
          "BURST_BARRAGE".equals(values[index])
              ? TaskStrategy.MethodTag.BARRAGE
              : TaskStrategy.MethodTag.valueOf(values[index]);
    }
    return tags;
  }

  private static String restoreEscapedDelimiters(String escapedText) {
    if (escapedText == null || escapedText.indexOf('\\') < 0) {
      return escapedText;
    }
    var value = new StringBuilder(escapedText.length());
    var escaped = false;
    for (var index = 0; index < escapedText.length(); index++) {
      var character = escapedText.charAt(index);
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

  private static Map<String, Profiles> baseProfiles(Map<String, Profiles> data) {
    final Map<String, Profiles> base = new HashMap<>();
    for (Map.Entry<String, Profiles> entry : data.entrySet()) {
      if (entry.getKey().startsWith("base:")) {
        base.put(entry.getKey().substring(5), entry.getValue());
      }
    }
    return Collections.unmodifiableMap(base);
  }

  private static String normalize(String value) {
    return SlayerText.encounter(value);
  }

  private static String canonicalTaskKey(String value) {
    var key = normalize(value);
    return TASK_ALIASES.getOrDefault(key, key);
  }

  private static Map<String, String> loadTaskAliases() {
    Map<String, String> aliases = new HashMap<>();
    for (String[] row : ResourceTable.rows("slayer-task-aliases.tsv", 2)) {
      var previous = aliases.put(normalize(row[0]), normalize(row[1]));
      if (previous != null) {
        throw new IllegalStateException("Duplicate task alias " + row[0]);
      }
    }
    return Collections.unmodifiableMap(aliases);
  }

  private static final class Profiles {
    private TaskStrategy balanced;
    private TaskStrategy fast;
    private TaskStrategy afk;
    private TaskStrategy profit;
    private TaskStrategy melee;
    private TaskStrategy ranged;
    private TaskStrategy magic;
    private TaskStrategy burst;
    private TaskStrategy nonBurst;
    private TaskStrategy cannon;
    private TaskStrategy nonCannon;

    private void set(String role, TaskStrategy value) {
      switch (role) {
        case "balanced":
          balanced = value;
          break;
        case "fast":
          fast = value;
          break;
        case "afk":
          afk = value;
          break;
        case "profit":
          profit = value;
          break;
        case "melee":
          melee = value;
          break;
        case "ranged":
          ranged = value;
          break;
        case "magic":
          magic = value;
          break;
        case "burst":
          burst = value;
          break;
        case "non_burst":
          nonBurst = value;
          break;
        case "cannon":
          cannon = value;
          break;
        case "non_cannon":
          nonCannon = value;
          break;
        default:
          throw new IllegalStateException("Unknown strategy role " + role);
      }
    }

    private void validate(String key) {
      if (balanced == null || fast == null || afk == null || profit == null)
        throw new IllegalStateException("Incomplete Slayer strategy group " + key);
    }

    private TaskStrategy select(
        Preference.Cannon cannonPreference,
        Preference.Burst burstPreference,
        Preference.CombatStyle combatPreference) {
      var selected = selectCombatPreference(combatPreference);
      var explicitCombat = selected != null;
      var note = "";
      if (!explicitCombat) {
        selected = selectAutomatic();
      }
      if (!explicitCombat && cannonPreference == Preference.Cannon.PREFER && cannon != null) {
        selected = cannon;
      }
      if (!explicitCombat && burstPreference == Preference.Burst.PREFER && burst != null) {
        selected = burst;
      }
      if (violatesEnabledPreferences(selected, cannonPreference, burstPreference)) {
        selected = safePreferenceFallback(selected, cannonPreference, burstPreference);
        note =
            "Disabled cannon/burst preferences were enforced with a reviewed compatible fallback.";
      }
      selected = applyAutomaticLoadoutPolicy(selected);
      var authoredNote = selected.getSelectionNote();
      if (note.isEmpty() && explicitCombat)
        note =
            combatPreference
                + " applied because this task has a reviewed viable "
                + selected.getStyle().getLabel()
                + " setup.";
      if (note.isEmpty()) {
        note =
            "Automatic selected the reviewed "
                + selected.getStyle().getLabel()
                + " method."
                + (selected.hasTag(TaskStrategy.MethodTag.BARRAGE)
                    ? " Burst/Barrage preference permits this method."
                    : "")
                + (selected.hasTag(TaskStrategy.MethodTag.CANNON)
                    ? " Cannon preference permits this method."
                    : "");
      }
      var generated = note + automaticLoadoutNote(selected);
      return selected.withSelectionNote(
          authoredNote == null || authoredNote.trim().isEmpty()
              ? generated
              : generated + " " + authoredNote.trim());
    }

    private TaskStrategy applyAutomaticLoadoutPolicy(TaskStrategy strategy) {
      if (strategy == null) {
        return null;
      }
      if (strategy.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK
          || strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS)) {
        return strategy;
      }
      return strategy.withLoadoutPolicy(
          TaskStrategy.ArmourFocus.DAMAGE, TaskStrategy.CostPolicy.MAX_DPS);
    }

    private String automaticLoadoutNote(TaskStrategy strategy) {
      if (strategy == null
          || strategy.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK
          || strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS)) {
        return "";
      }
      return " Automatic loadouts use damage gear and MAX_DPS equipment without considering item"
          + " or charge cost.";
    }

    private TaskStrategy selectCombatPreference(Preference.CombatStyle preference) {
      if (preference == null || preference == Preference.CombatStyle.AUTOMATIC) {
        return null;
      }
      switch (preference) {
        case PREFER_MELEE:
          return melee;
        case PREFER_RANGED:
          return ranged;
        case PREFER_MAGIC:
          return magic;
        default:
          return null;
      }
    }

    private boolean violatesEnabledPreferences(
        TaskStrategy strategy,
        Preference.Cannon cannonPreference,
        Preference.Burst burstPreference) {
      return strategy != null
          && ((cannonPreference == Preference.Cannon.NEVER
                  && strategy.hasTag(TaskStrategy.MethodTag.CANNON))
              || (burstPreference == Preference.Burst.NEVER
                  && strategy.hasTag(TaskStrategy.MethodTag.BARRAGE)));
    }

    private TaskStrategy safePreferenceFallback(
        TaskStrategy current,
        Preference.Cannon cannonPreference,
        Preference.Burst burstPreference) {
      TaskStrategy[] candidates = {
        current, nonCannon, nonBurst, melee, ranged, magic, balanced, fast, afk, profit, cannon,
        burst
      };
      for (TaskStrategy candidate : candidates) {
        if (candidate != null
            && !violatesEnabledPreferences(candidate, cannonPreference, burstPreference)) {
          return candidate;
        }
      }
      return current;
    }

    private TaskStrategy selectAutomatic() {
      return fast;
    }
  }
}
