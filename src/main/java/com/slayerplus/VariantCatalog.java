package com.slayerplus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class VariantCatalog {
  private static final List<TaskVariant> STANDARD_ONLY =
      Collections.singletonList(TaskVariant.STANDARD_TASK);
  private static final String VARIANT_RESOURCE = "/com/slayerplus/slayer-task-variants.tsv";
  private static final VariantResourceData VARIANT_DATA = loadVariantData();
  private static final List<BossDefinition> BOSS_DEFINITIONS = VARIANT_DATA.bosses;
  private static final Map<String, List<BossDefinition>> BOSSES_BY_ASSIGNMENT =
      indexBossesByAssignment();
  private static final Map<TaskVariant, BossDefinition> BOSSES_BY_VARIANT =
      indexBossesByVariant();
  private static final Map<String, BossDefinition> BOSSES_BY_ENCOUNTER = indexBossesByEncounter();
  private static final Map<String, DirectBossDefinition> DIRECT_BOSSES = VARIANT_DATA.directBosses;

  private VariantCatalog() {}

  public static List<TaskVariant> getAvailableVariants(String taskName) {
    BossDefinition directVariant = BOSSES_BY_ENCOUNTER.get(normalize(taskName));
    if (directVariant != null) {
      return Collections.singletonList(directVariant.variant);
    }
    List<BossDefinition> bosses = BOSSES_BY_ASSIGNMENT.get(normalize(taskName));
    if (bosses == null || bosses.isEmpty()) {
      return STANDARD_ONLY;
    }
    List<TaskVariant> variants = new ArrayList<>();
    variants.add(TaskVariant.STANDARD_TASK);
    for (BossDefinition boss : bosses) {
      if (!variants.contains(boss.variant)) {
        variants.add(boss.variant);
      }
    }
    return Collections.unmodifiableList(variants);
  }

  public static TaskVariant getDefaultVariant(String taskName) {
    BossDefinition directVariant = BOSSES_BY_ENCOUNTER.get(normalize(taskName));
    return directVariant == null ? TaskVariant.STANDARD_TASK : directVariant.variant;
  }

  public static boolean supportsBossVariant(String taskName) {
    List<BossDefinition> bosses = BOSSES_BY_ASSIGNMENT.get(normalize(taskName));
    return bosses != null && !bosses.isEmpty();
  }

  public static boolean isDirectBossTask(String taskName) {
    String key = normalize(taskName);
    return BOSSES_BY_ENCOUNTER.containsKey(key) || DIRECT_BOSSES.containsKey(key);
  }

  public static String getOptionLabel(String taskName, TaskVariant variant) {
    if (variant != null && variant.isBoss()) {
      BossDefinition boss = findBossForAssignment(taskName, variant);
      if (boss != null) {
        return boss.displayName;
      }
      BossDefinition direct = BOSSES_BY_ENCOUNTER.get(normalize(taskName));
      if (direct != null && direct.variant == variant) {
        return direct.displayName;
      }
    }
    return SlayerEncounterStandards.cleanEncounterLabel(taskName);
  }

  public static ResolvedTarget resolve(
      String taskName,
      TaskVariant requestedVariant,
      Recommendation recommendation,
      SlayerPlusConfig config) {
    TaskVariant requested =
        requestedVariant == null ? getDefaultVariant(taskName) : requestedVariant;
    if (requested.isBoss()) {
      BossDefinition boss = findBossForAssignment(taskName, requested);
      if (boss == null) {
        BossDefinition direct = BOSSES_BY_ENCOUNTER.get(normalize(taskName));
        if (direct != null && direct.variant == requested) {
          boss = direct;
        }
      }
      if (boss == null) {
        return ResolvedTarget.invalidBoss(taskName, requested);
      }
      return boss.resolve(config, recommendation);
    }
    BossDefinition directVariant = BOSSES_BY_ENCOUNTER.get(normalize(taskName));
    if (directVariant != null) {
      return directVariant.resolve(config, recommendation);
    }
    DirectBossDefinition directBoss = DIRECT_BOSSES.get(normalize(taskName));
    if (directBoss != null) {
      return directBoss.resolve(taskName, recommendation, config);
    }
    TaskStrategy strategy =
        recommendation != null && recommendation.hasTaskStrategy()
            ? recommendation.getStrategy()
            : SlayerTaskStrategyCatalog.legacy(
                recommendation == null ? "" : recommendation.getMethod());
    return new ResolvedTarget(
        taskName,
        SlayerEncounterStandards.cleanEncounterLabel(taskName),
        recommendation == null ? "" : recommendation.getLocation(),
        recommendation == null ? "" : recommendation.getTravel(),
        recommendation == null ? "" : recommendation.getCannon(),
        recommendation == null ? "" : recommendation.getRestriction(),
        strategy,
        TaskVariant.STANDARD_TASK,
        false,
        true);
  }

  private static BossDefinition findBossForAssignment(String taskName, TaskVariant variant) {
    if (variant == null || !variant.isBoss()) {
      return null;
    }
    List<BossDefinition> bosses = BOSSES_BY_ASSIGNMENT.get(normalize(taskName));
    if (bosses == null) {
      return null;
    }
    for (BossDefinition boss : bosses) {
      if (boss.variant == variant) {
        return boss;
      }
    }
    return null;
  }

  private static VariantResourceData loadVariantData() {
    List<BossDefinition> bosses = new ArrayList<>();
    Map<String, DirectBossDefinition> directBosses = new LinkedHashMap<>();
    for (String[] fields : ResourceTable.decodedRows("slayer-task-variants.tsv", 3, 8)) {
      if (fields.length == 8 && fields[0].equals("B")) {
        String[] assignments =
            fields[7].isEmpty() ? new String[0] : fields[7].split("\\|", -1);
        bosses.add(
            new BossDefinition(
                TaskVariant.valueOf(fields[1]),
                fields[2],
                SlayerEncounterStandards.cleanEncounterLabel(fields[2]),
                fields[3],
                fields[4],
                fields[5],
                fields[6],
                assignments));
      } else if (fields.length == 3 && fields[0].equals("D")) {
        String encounter = fields[1];
        directBosses.put(normalize(encounter), new DirectBossDefinition(encounter, fields[2]));
      } else {
        throw new IllegalStateException("Invalid task-variant row type: " + fields[0]);
      }
    }
    if (bosses.isEmpty() || directBosses.isEmpty()) {
      throw new IllegalStateException("Task-variant resource is empty");
    }
    return new VariantResourceData(
        Collections.unmodifiableList(bosses), Collections.unmodifiableMap(directBosses));
  }

  private static Map<String, List<BossDefinition>> indexBossesByAssignment() {
    Map<String, List<BossDefinition>> index = new LinkedHashMap<>();
    for (BossDefinition boss : BOSS_DEFINITIONS) {
      for (String assignment : boss.assignments) {
        String key = normalize(assignment);
        List<BossDefinition> entries = index.get(key);
        if (entries == null) {
          entries = new ArrayList<>();
          index.put(key, entries);
        }
        entries.add(boss);
      }
    }
    for (Map.Entry<String, List<BossDefinition>> entry : index.entrySet()) {
      entry.setValue(Collections.unmodifiableList(entry.getValue()));
    }
    return Collections.unmodifiableMap(index);
  }

  private static Map<TaskVariant, BossDefinition> indexBossesByVariant() {
    Map<TaskVariant, BossDefinition> index = new LinkedHashMap<>();
    for (BossDefinition boss : BOSS_DEFINITIONS) {
      if (index.put(boss.variant, boss) != null) {
        throw new IllegalStateException("Duplicate Slayer boss variant: " + boss.variant);
      }
    }
    return Collections.unmodifiableMap(index);
  }

  private static Map<String, BossDefinition> indexBossesByEncounter() {
    Map<String, BossDefinition> index = new LinkedHashMap<>();
    for (BossDefinition boss : BOSS_DEFINITIONS) {
      index.put(normalize(boss.encounterName), boss);
    }
    putEncounterAlias(index, "Cave Kraken Boss", TaskVariant.KRAKEN_BOSS);
    putEncounterAlias(index, "The Cave Kraken Boss", TaskVariant.KRAKEN_BOSS);
    return Collections.unmodifiableMap(index);
  }

  private static void putEncounterAlias(
      Map<String, BossDefinition> index, String alias, TaskVariant variant) {
    for (BossDefinition boss : BOSS_DEFINITIONS) {
      if (boss.variant == variant) {
        index.put(normalize(alias), boss);
        return;
      }
    }
  }

  public static List<EncounterDefinition> getBossDefinitions() {
    List<EncounterDefinition> definitions = new ArrayList<>();
    for (BossDefinition boss : BOSS_DEFINITIONS) {
      if (boss.assignments.isEmpty()) {
        definitions.add(
            new EncounterDefinition(
                "", boss.encounterName, boss.displayName, boss.location, boss.variant));
        continue;
      }
      for (String assignment : boss.assignments) {
        definitions.add(
            new EncounterDefinition(
                assignment, boss.encounterName, boss.displayName, boss.location, boss.variant));
      }
    }
    return Collections.unmodifiableList(definitions);
  }

  public static List<EncounterDefinition> getDirectBossDefinitions() {
    List<EncounterDefinition> definitions = new ArrayList<>();
    for (DirectBossDefinition boss : DIRECT_BOSSES.values()) {
      definitions.add(
          new EncounterDefinition(
              "",
              boss.encounterName,
              SlayerEncounterStandards.cleanEncounterLabel(boss.encounterName),
              boss.location,
              TaskVariant.STANDARD_TASK));
    }
    return Collections.unmodifiableList(definitions);
  }

  private static TaskStrategy resolveStrategy(
      String taskName, String location, SlayerPlusConfig config) {
    return SlayerTaskStrategyCatalog.resolve(
        taskName,
        config == null ? Preference.Playstyle.FAST_XP : config.playstyle(),
        config == null ? Preference.Cannon.ALLOW : config.cannonPreference(),
        config == null ? Preference.Burst.ALLOW : config.burstPreference(),
        config == null ? Preference.CombatStyle.AUTOMATIC : config.combatStylePreference(),
        location,
        false);
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ENGLISH)
        .replace('\u2019', '\'')
        .replaceAll("[^a-z0-9]+", " ")
        .trim()
        .replaceFirst("^the\\s+", "");
  }

  private static final class VariantResourceData {
    private final List<BossDefinition> bosses;
    private final Map<String, DirectBossDefinition> directBosses;

    private VariantResourceData(
        List<BossDefinition> bosses, Map<String, DirectBossDefinition> directBosses) {
      this.bosses = bosses;
      this.directBosses = directBosses;
    }
  }

  private static final class BossDefinition {
    private final TaskVariant variant;
    private final String encounterName;
    private final String displayName;
    private final String location;
    private final String travel;
    private final String cannon;
    private final String restriction;
    private final Set<String> assignments;

    private BossDefinition(
        TaskVariant variant,
        String encounterName,
        String displayName,
        String location,
        String travel,
        String cannon,
        String restriction,
        String... assignments) {
      if (variant == null || !variant.isBoss()) {
        throw new IllegalArgumentException("Boss definition requires a boss variant");
      }
      this.variant = variant;
      this.encounterName = encounterName;
      this.displayName = displayName;
      this.location = location;
      this.travel = travel;
      this.cannon = cannon;
      this.restriction = restriction;
      this.assignments =
          Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(assignments)));
    }

    private ResolvedTarget resolve(SlayerPlusConfig config, Recommendation recommendation) {
      TaskStrategy catalogStrategy = resolveStrategy(encounterName, location, config);
      TaskStrategy recommendationStrategy =
          recommendation != null
                  && recommendation.hasTaskStrategy()
                  && recommendation.getStrategy().isBoss()
                  && normalize(recommendation.getLocation()).equals(normalize(location))
              ? recommendation.getStrategy()
              : null;
      return new ResolvedTarget(
          encounterName,
          displayName,
          location,
          travel,
          cannon,
          restriction,
          recommendationStrategy == null ? catalogStrategy : recommendationStrategy,
          variant,
          true,
          true);
    }
  }

  private static final class DirectBossDefinition {
    private final String encounterName;
    private final String location;

    private DirectBossDefinition(String encounterName, String location) {
      this.encounterName = encounterName;
      this.location = location;
    }

    private ResolvedTarget resolve(
        String detectedTaskName, Recommendation recommendation, SlayerPlusConfig config) {
      String resolvedLocation =
          location == null || location.trim().isEmpty()
              ? recommendation == null ? "" : recommendation.getLocation()
              : location;
      return new ResolvedTarget(
          encounterName,
          SlayerEncounterStandards.cleanEncounterLabel(detectedTaskName),
          resolvedLocation,
          recommendation == null ? "" : recommendation.getTravel(),
          recommendation == null ? "" : recommendation.getCannon(),
          recommendation == null ? "" : recommendation.getRestriction(),
          resolveStrategy(encounterName, resolvedLocation, config),
          TaskVariant.STANDARD_TASK,
          true,
          true);
    }
  }

  public static final class EncounterDefinition {
    private final String assignmentName;
    private final String encounterName;
    private final String displayName;
    private final String location;
    private final TaskVariant variant;

    public EncounterDefinition(
        String assignmentName,
        String encounterName,
        String displayName,
        String location,
        TaskVariant variant) {
      this.assignmentName = safeDefinition(assignmentName);
      this.encounterName = safeDefinition(encounterName);
      this.displayName = safeDefinition(displayName);
      this.location = safeDefinition(location);
      this.variant = variant == null ? TaskVariant.STANDARD_TASK : variant;
    }

    public String getAssignmentName() {
      return assignmentName;
    }

    public String getTaskName() {
      return encounterName;
    }

    public String getEncounterName() {
      return encounterName;
    }

    public String getTargetName() {
      return encounterName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getOptionLabel() {
      return displayName;
    }

    public String getLocation() {
      return location;
    }

    public TaskVariant getVariant() {
      return variant;
    }

    public boolean isBoss() {
      return variant != null && variant.isBoss();
    }

    private static String safeDefinition(String value) {
      return value == null ? "" : value.trim();
    }
  }

  public static final class ResolvedTarget {
    private final String taskName;
    private final String displayName;
    private final String location;
    private final String travel;
    private final String cannon;
    private final String restriction;
    private final TaskStrategy strategy;
    private final TaskVariant variant;
    private final boolean bossEncounter;
    private final boolean valid;

    private ResolvedTarget(
        String taskName,
        String displayName,
        String location,
        String travel,
        String cannon,
        String restriction,
        TaskStrategy strategy,
        TaskVariant variant,
        boolean bossEncounter,
        boolean valid) {
      this.taskName = safe(taskName, "Unknown task");
      this.displayName = safe(displayName, this.taskName);
      this.location = safe(location, "");
      this.travel = safe(travel, "");
      this.cannon = safe(cannon, "");
      this.restriction = safe(restriction, "");
      this.strategy = strategy;
      this.variant = variant == null ? TaskVariant.STANDARD_TASK : variant;
      this.bossEncounter = bossEncounter;
      this.valid = valid;
    }

    private static ResolvedTarget invalidBoss(String assignmentName, TaskVariant variant) {
      return new ResolvedTarget(
          assignmentName,
          "Boss selection unavailable",
          "",
          "",
          "",
          "Boss variant does not belong to this assignment",
          null,
          variant,
          true,
          false);
    }

    public String getTaskName() {
      return taskName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getLocation() {
      return location;
    }

    public String getTravel() {
      return travel;
    }

    public String getCannon() {
      return cannon;
    }

    public String getRestriction() {
      return restriction;
    }

    public TaskStrategy getStrategy() {
      return strategy;
    }

    public TaskVariant getVariant() {
      return variant;
    }

    public boolean isBoss() {
      return bossEncounter;
    }

    public boolean isValid() {
      return valid;
    }

    private static String safe(String value, String fallback) {
      return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
  }
}
