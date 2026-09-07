package com.slayerplus;

import java.util.*;
import lombok.Getter;

public final class VariantCatalog {
  private static final List<TaskVariant> STANDARD_ONLY =
      Collections.singletonList(TaskVariant.STANDARD_TASK);
  private static final VariantResourceData VARIANT_DATA = loadVariantData();
  private static final List<BossDefinition> BOSS_DEFINITIONS = VARIANT_DATA.bosses;
  private static final Map<String, List<BossDefinition>> BOSSES_BY_ASSIGNMENT =
      indexBossesByAssignment();
  private static final Map<String, BossDefinition> BOSSES_BY_ENCOUNTER = indexBossesByEncounter();
  private static final Map<String, DirectBossDefinition> DIRECT_BOSSES = VARIANT_DATA.directBosses;

  private VariantCatalog() {}

  public static List<TaskVariant> getAvailableVariants(String assignment) {
    BossDefinition directVariant = BOSSES_BY_ENCOUNTER.get(normalize(assignment));
    if (directVariant != null) {
      return Collections.singletonList(directVariant.variant);
    }
    List<BossDefinition> bosses = BOSSES_BY_ASSIGNMENT.get(normalize(assignment));
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

  public static TaskVariant getDefaultVariant(String assignment) {
    BossDefinition directVariant = BOSSES_BY_ENCOUNTER.get(normalize(assignment));
    return directVariant == null ? TaskVariant.STANDARD_TASK : directVariant.variant;
  }

  public static String getOptionLabel(String assignment, TaskVariant variant) {
    if (variant != null && variant.isBoss()) {
      BossDefinition boss = findBossForAssignment(assignment, variant);
      if (boss != null) {
        return boss.displayName;
      }
      BossDefinition direct = BOSSES_BY_ENCOUNTER.get(normalize(assignment));
      if (direct != null && direct.variant == variant) {
        return direct.displayName;
      }
    }
    return SlayerEncounterStandards.cleanEncounterLabel(assignment);
  }

  public static ResolvedTarget resolve(
      String assignment,
      TaskVariant requestedVariant,
      Recommendation recommendation,
      SlayerPlusConfig config) {
    TaskVariant requested =
        requestedVariant == null ? getDefaultVariant(assignment) : requestedVariant;
    if (requested.isBoss()) {
      BossDefinition boss = findBossForAssignment(assignment, requested);
      if (boss == null) {
        BossDefinition direct = BOSSES_BY_ENCOUNTER.get(normalize(assignment));
        if (direct != null && direct.variant == requested) {
          boss = direct;
        }
      }
      if (boss == null) {
        return ResolvedTarget.invalidBoss(assignment, requested);
      }
      return boss.resolve(config, recommendation);
    }
    BossDefinition directVariant = BOSSES_BY_ENCOUNTER.get(normalize(assignment));
    if (directVariant != null) {
      return directVariant.resolve(config, recommendation);
    }
    DirectBossDefinition directBoss = DIRECT_BOSSES.get(normalize(assignment));
    if (directBoss != null) {
      return directBoss.resolve(assignment, recommendation, config);
    }
    TaskStrategy strategy =
        recommendation != null && recommendation.hasTaskStrategy()
            ? recommendation.getStrategy()
            : SlayerTaskStrategyCatalog.legacy(
                recommendation == null ? "" : recommendation.getMethod());
    return new ResolvedTarget(
        assignment,
        SlayerEncounterStandards.cleanEncounterLabel(assignment),
        recommendation == null ? "" : recommendation.getLocation(),
        recommendation == null ? "" : recommendation.getTravel(),
        recommendation == null ? "" : recommendation.getCannon(),
        recommendation == null ? "" : recommendation.getRestriction(),
        strategy,
        TaskVariant.STANDARD_TASK,
        false,
        true);
  }

  private static BossDefinition findBossForAssignment(String assignment, TaskVariant variant) {
    if (variant == null || !variant.isBoss()) {
      return null;
    }
    List<BossDefinition> bosses = BOSSES_BY_ASSIGNMENT.get(normalize(assignment));
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
        String[] assignments = fields[7].isEmpty() ? new String[0] : fields[7].split("\\|", -1);
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

  private static Map<String, BossDefinition> indexBossesByEncounter() {
    Map<String, BossDefinition> index = new LinkedHashMap<>();
    for (BossDefinition boss : BOSS_DEFINITIONS) {
      index.put(normalize(boss.encounter), boss);
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
                "", boss.encounter, boss.displayName, boss.location, boss.variant));
        continue;
      }
      for (String assignment : boss.assignments) {
        definitions.add(
            new EncounterDefinition(
                assignment, boss.encounter, boss.displayName, boss.location, boss.variant));
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
              boss.encounter,
              SlayerEncounterStandards.cleanEncounterLabel(boss.encounter),
              boss.location,
              TaskVariant.STANDARD_TASK));
    }
    return Collections.unmodifiableList(definitions);
  }

  private static TaskStrategy resolveStrategy(
      String assignment, String location, SlayerPlusConfig config) {
    return SlayerTaskStrategyCatalog.resolve(
        assignment,
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
    private final String encounter;
    private final String displayName;
    private final String location;
    private final String travel;
    private final String cannon;
    private final String restriction;
    private final Set<String> assignments;

    private BossDefinition(
        TaskVariant variant,
        String encounter,
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
      this.encounter = encounter;
      this.displayName = displayName;
      this.location = location;
      this.travel = travel;
      this.cannon = cannon;
      this.restriction = restriction;
      this.assignments =
          Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(assignments)));
    }

    private ResolvedTarget resolve(SlayerPlusConfig config, Recommendation recommendation) {
      TaskStrategy catalogStrategy = resolveStrategy(encounter, location, config);
      TaskStrategy recommendationStrategy =
          recommendation != null
                  && recommendation.hasTaskStrategy()
                  && recommendation.getStrategy().isBoss()
                  && normalize(recommendation.getLocation()).equals(normalize(location))
              ? recommendation.getStrategy()
              : null;
      return new ResolvedTarget(
          encounter,
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
    private final String encounter;
    private final String location;

    private DirectBossDefinition(String encounter, String location) {
      this.encounter = encounter;
      this.location = location;
    }

    private ResolvedTarget resolve(
        String detectedTaskName, Recommendation recommendation, SlayerPlusConfig config) {
      String resolvedLocation =
          location == null || location.trim().isEmpty()
              ? recommendation == null ? "" : recommendation.getLocation()
              : location;
      return new ResolvedTarget(
          encounter,
          SlayerEncounterStandards.cleanEncounterLabel(detectedTaskName),
          resolvedLocation,
          recommendation == null ? "" : recommendation.getTravel(),
          recommendation == null ? "" : recommendation.getCannon(),
          recommendation == null ? "" : recommendation.getRestriction(),
          resolveStrategy(encounter, resolvedLocation, config),
          TaskVariant.STANDARD_TASK,
          true,
          true);
    }
  }

  @Getter
  public static final class EncounterDefinition {
    private final String assignmentName;
    private final String encounter;
    private final String displayName;
    private final String location;
    private final TaskVariant variant;

    public EncounterDefinition(
        String assignmentName,
        String encounter,
        String displayName,
        String location,
        TaskVariant variant) {
      this.assignmentName = safeDefinition(assignmentName);
      this.encounter = safeDefinition(encounter);
      this.displayName = safeDefinition(displayName);
      this.location = safeDefinition(location);
      this.variant = variant == null ? TaskVariant.STANDARD_TASK : variant;
    }

    public String getTaskName() {
      return encounter;
    }

    public String getTargetName() {
      return encounter;
    }

    public String getOptionLabel() {
      return displayName;
    }

    public boolean isBoss() {
      return variant != null && variant.isBoss();
    }

    private static String safeDefinition(String value) {
      return value == null ? "" : value.trim();
    }
  }

  @Getter
  public static final class ResolvedTarget {
    private final String assignment;
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
        String assignment,
        String displayName,
        String location,
        String travel,
        String cannon,
        String restriction,
        TaskStrategy strategy,
        TaskVariant variant,
        boolean bossEncounter,
        boolean valid) {
      this.assignment = safe(assignment, "Unknown task");
      this.displayName = safe(displayName, this.assignment);
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
      return assignment;
    }

    public boolean isBoss() {
      return bossEncounter;
    }

    private static String safe(String value, String fallback) {
      return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
  }
}
