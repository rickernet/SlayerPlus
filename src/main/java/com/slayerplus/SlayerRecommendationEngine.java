package com.slayerplus;

import static com.slayerplus.SlayerText.matchesAny;

import java.io.*;
import java.util.*;
import net.runelite.api.*;

public final class SlayerRecommendationEngine {
  private static final int KRYSTILIA = 7;
  private static final int KONAR_MASTER_ID = 8;
  private static final Map<String, List<LocationOption>> TASK_LOCATIONS = createTaskLocations();
  private final Client client;
  private final SlayerPlusConfig config;
  private final Map<String, List<LocationOption>> taskLocations;

  public SlayerRecommendationEngine(Client client, SlayerPlusConfig config) {
    this.client = client;
    this.config = config;
    this.taskLocations = TASK_LOCATIONS;
  }

  static Set<String> catalogLocationsForValidation(String assignment) {
    List<LocationOption> options = TASK_LOCATIONS.get(normalize(assignment));
    if (options == null || options.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> locations = new LinkedHashSet<>();
    for (LocationOption option : options) {
      if (option != null && option.location != null && !option.location.trim().isEmpty()) {
        locations.add(option.location.trim());
      }
    }
    return Collections.unmodifiableSet(locations);
  }

  public Recommendation recommend(String assignment, int masterId, String assignedLocation) {
    String normalizedTask = normalize(assignment);
    if (normalizedTask.isEmpty()) {
      return genericRecommendation("Unknown task", masterId);
    }
    if (masterId == KONAR_MASTER_ID || isAssignedArea(assignedLocation)) {
      return assignedAreaRecommendation(normalizedTask, assignedLocation, masterId);
    }
    if (masterId == KRYSTILIA) {
      return wildernessRecommendation(assignment);
    }
    if (masterId == BoostCoordinator.TURAEL_AYA_MASTER_ID
        && config != null
        && config.slayerWorkflow() == Preference.Workflow.TURAEL_POINT_BOOST) {
      Recommendation boost = turaelBoostRecommendation(assignment);
      if (boost != null) {
        return boost;
      }
    }
    List<LocationOption> options = taskLocations.get(normalizedTask);
    if (options == null || options.isEmpty()) {
      return genericRecommendation(assignment, masterId);
    }
    LocationOption selected = null;
    int selectedScore = Integer.MIN_VALUE;
    LocationOption lockedUpgrade = null;
    int lockedScore = Integer.MIN_VALUE;
    for (LocationOption option : options) {
      if (option.wilderness) {
        continue;
      }
      int score = scoreOption(option, normalizedTask, masterId);
      if (isUnlocked(option)) {
        if (selected == null || score > selectedScore) {
          selected = option;
          selectedScore = score;
        }
      } else if (lockedUpgrade == null || score > lockedScore) {
        lockedUpgrade = option;
        lockedScore = score;
      }
    }
    if (selected == null) {
      return lockedRecommendation(assignment, lockedUpgrade);
    }
    TaskStrategy strategy = strategyFor(normalizedTask, selected.location, false);
    return new Recommendation(
        selected.location,
        strategy.getMethod(),
        selected.travel,
        formatCannon(selected.cannonSupport),
        formatRequirements(selected, lockedUpgrade),
        "Non-Wilderness recommendation",
        strategy);
  }

  private Recommendation turaelBoostRecommendation(String assignment) {
    TuraelBoost.Entry entry = TuraelBoost.find(assignment);
    if (entry == null) {
      return null;
    }
    TaskStrategy strategy =
        strategyFor(assignment, entry.getLocation(), false)
            .withAdditionalTags(TaskStrategy.MethodTag.TURAEL_POINT_BOOST);
    return new Recommendation(
        entry.getLocation(),
        strategy.getMethod(),
        entry.getTravel(),
        entry.getCannon(),
        entry.getRequirements(),
        "Turael/Aya point-boost route",
        strategy);
  }

  private Recommendation assignedAreaRecommendation(
      String normalizedTask, String assignedLocation, int masterId) {
    String area =
        isAssignedArea(assignedLocation) ? assignedLocation.trim() : "Assigned Slayer area";
    if (masterId != KRYSTILIA && isWilderness(area)) {
      return new Recommendation(
          "No non-Wilderness match",
          "Check the assignment with your Slayer master",
          "Do not route into the Wilderness",
          "Not recommended",
          "Assigned-area data needs verification",
          "Wilderness blocked unless Krystilia assigned it");
    }
    TaskStrategy strategy = strategyFor(normalizedTask, area, masterId == KRYSTILIA);
    return new Recommendation(
        area,
        strategy.getMethod(),
        travelForArea(area),
        formatCannon(cannonForArea(area)),
        "Use the monster inside the assigned area",
        masterId == KONAR_MASTER_ID ? "Konar — assigned area required" : "Assigned area required",
        strategy);
  }

  private Recommendation wildernessRecommendation(String assignment) {
    String normalized = normalize(assignment);
    String location;
    if (SlayerLoadoutData.list("wilderness_slayer_cave_tasks").contains(normalized)) {
      location = "Wilderness Slayer Cave";
    } else if (normalized.equals("bloodveld") || normalized.equals("bloodvelds")) {
      location = "Wilderness God Wars Dungeon";
    } else {
      location = "Nearest matching Wilderness spawn";
    }
    TaskStrategy strategy = strategyFor(assignment, location, true);
    return new Recommendation(
        location,
        strategy.getMethod(),
        "Start from Ferox Enclave and use the safest route",
        formatCannon(CannonSupport.OPTIONAL),
        "Krystilia assignment active",
        "Wilderness — protect-item planning required",
        strategy);
  }

  private Recommendation genericRecommendation(String assignment, int masterId) {
    TaskStrategy strategy =
        strategyFor(assignment, "Standard Slayer location", masterId == KRYSTILIA);
    return new Recommendation(
        "Standard Slayer location",
        strategy.getMethod(),
        "Use the nearest unlocked standard task location",
        "Check location rules",
        "Task-specific equipment profile active",
        masterId == KRYSTILIA ? "Krystilia Wilderness task" : "Wilderness locations excluded",
        strategy);
  }

  private Recommendation lockedRecommendation(String assignment, LocationOption lockedOption) {
    if (lockedOption == null) {
      return genericRecommendation(assignment, 0);
    }
    TaskStrategy strategy = strategyFor(assignment, lockedOption.location, false);
    return new Recommendation(
        "Location locked",
        strategy.getMethod(),
        lockedOption.travel,
        formatCannon(lockedOption.cannonSupport),
        requirementName(lockedOption) + " required",
        "Wilderness locations excluded",
        strategy);
  }

  private boolean isUnlocked(LocationOption option) {
    return (option.quest == null || option.quest.getState(client) == QuestState.FINISHED)
        && (option.skill == null || client.getRealSkillLevel(option.skill) >= option.skillLevel);
  }

  private String formatRequirements(LocationOption selected, LocationOption lockedUpgrade) {
    String selectedRequirement;
    if (selected.skill != null) {
      selectedRequirement = selected.skillLevel + " " + selected.skill.getName() + " — met";
    } else {
      selectedRequirement = selected.quest == null ? "None" : selected.questName + " — completed";
    }
    if (lockedUpgrade == null || lockedUpgrade == selected) {
      return selectedRequirement;
    }
    return selectedRequirement
        + "; "
        + requirementName(lockedUpgrade)
        + " unlocks "
        + lockedUpgrade.location;
  }

  private static String requirementName(LocationOption option) {
    if (option == null) {
      return "Location requirement";
    }
    if (option.skill != null) {
      return option.skillLevel + " " + option.skill.getName();
    }
    return option.questName == null || option.questName.trim().isEmpty()
        ? "Location requirement"
        : option.questName;
  }

  private int scoreOption(LocationOption option, String normalizedTask, int masterId) {
    int speed = speedRating(option);
    int score = speedRating(option) * 5;
    boolean cannonAvailable =
        option.cannonSupport == CannonSupport.RECOMMENDED
            || option.cannonSupport == CannonSupport.OPTIONAL;
    boolean cannonUnlocked = Quest.DWARF_CANNON.getState(client) == QuestState.FINISHED;
    switch (cannonPreference()) {
      case PREFER:
        if (cannonAvailable && cannonUnlocked) {
          score += option.cannonSupport == CannonSupport.RECOMMENDED ? 70 : 38;
        } else {
          score -= 18;
        }
        break;
      case NEVER:
        if (option.cannonSupport == CannonSupport.RECOMMENDED) {
          score -= 130;
        } else if (option.cannonSupport == CannonSupport.OPTIONAL) {
          score += 6;
        } else {
          score += 18;
        }
        break;
      case ALLOW:
      default:
        if (cannonAvailable && cannonUnlocked) {
          score += 12;
        }
        break;
    }
    boolean burstAvailable = supportsBurst(option);
    switch (burstPreference()) {
      case PREFER:
        score += burstAvailable ? 72 : -16;
        break;
      case NEVER:
        score += burstAvailable ? -130 : 18;
        break;
      case ALLOW:
      default:
        if (burstAvailable) {
          score += 12;
        }
        break;
    }
    score += travelScore(option.travel);
    score += shardPreferenceBonusForTest(shardPreference(), normalizedTask, option.location);
    return score;
  }

  private int speedRating(LocationOption option) {
    String text = normalize(option.location + " " + option.method);
    int rating = 42;
    if (text.contains("burst") || text.contains("barrage")) {
      rating += 42;
    }
    if (text.contains("cannon")) {
      rating += 32;
    }
    if (text.contains("multi combat") || text.contains("clustered") || text.contains("stacked")) {
      rating += 14;
    }
    if (text.contains("single combat")) {
      rating -= 14;
    }
    return clampRating(rating);
  }

  private int travelScore(String route) {
    String normalized = normalize(route);
    boolean directTeleport =
        matchesAny(normalized, SlayerLoadoutData.list("direct_teleport_fragments"));
    return directTeleport ? 45 : 8;
  }

  private static boolean supportsBurst(LocationOption option) {
    String method = normalize(option.method);
    return method.contains("burst") || method.contains("barrage");
  }

  private static int clampRating(int value) {
    return Math.max(0, Math.min(100, value));
  }

  private Preference.CombatStyle combatStylePreference() {
    return config == null || config.combatStylePreference() == null
        ? Preference.CombatStyle.AUTOMATIC
        : config.combatStylePreference();
  }

  private Preference.Cannon cannonPreference() {
    return config == null || config.cannonPreference() == null
        ? Preference.Cannon.ALLOW
        : config.cannonPreference();
  }

  private Preference.Burst burstPreference() {
    return config == null || config.burstPreference() == null
        ? Preference.Burst.ALLOW
        : config.burstPreference();
  }

  private Preference.Shard shardPreference() {
    return config == null || config.shardPreference() == null
        ? Preference.Shard.NO_PREFERENCE
        : config.shardPreference();
  }

  static int shardPreferenceBonusForTest(
      Preference.Shard preference, String assignment, String location) {
    if (preference == null || preference == Preference.Shard.NO_PREFERENCE) {
      return 0;
    }
    String task = normalize(assignment);
    String area = normalize(location);
    boolean ancientShardArea =
        !task.equals("ghost")
            && !task.equals("ghosts")
            && (area.contains("catacombs of kourend") || area.contains("giants den"));
    boolean crystalShardArea = area.contains("iorwerth dungeon");
    switch (preference) {
      case ANCIENT_SHARD:
        return ancientShardArea ? 1000 : 0;
      case CRYSTAL_SHARD:
        return crystalShardArea ? 1000 : 0;
      case ANCIENT_AND_CRYSTAL_SHARD:
        return ancientShardArea || crystalShardArea ? 1000 : 0;
      case NO_PREFERENCE:
      default:
        return 0;
    }
  }

  private String formatCannon(CannonSupport support) {
    if (support == CannonSupport.NOT_ALLOWED) {
      return "Not allowed";
    }
    if (support == CannonSupport.UNKNOWN) {
      return cannonPreference() == Preference.Cannon.NEVER
          ? "Disabled by preference"
          : "Check assigned-area rules";
    }
    if (cannonPreference() == Preference.Cannon.NEVER) {
      return "Disabled by preference";
    }
    if (Quest.DWARF_CANNON.getState(client) != QuestState.FINISHED) {
      return "Locked — complete Dwarf Cannon";
    }
    if (cannonPreference() == Preference.Cannon.PREFER) {
      return support == CannonSupport.RECOMMENDED
          ? "Preferred and recommended"
          : "Preferred where practical";
    }
    return support == CannonSupport.RECOMMENDED ? "Recommended" : "Optional";
  }

  private TaskStrategy strategyFor(String assignment, String location, boolean wilderness) {
    return SlayerTaskStrategyCatalog.resolve(
        assignment,
        cannonPreference(),
        burstPreference(),
        combatStylePreference(),
        location,
        wilderness);
  }

  private static String travelForArea(String area) {
    String normalized = normalize(area);
    if (normalized.contains("catacombs of kourend")) {
      return "Xeric's talisman to Xeric's Heart";
    }
    if (normalized.contains("slayer tower")) {
      return "Slayer ring to Slayer Tower";
    }
    if (normalized.contains("fremennik slayer dungeon")) {
      return "Slayer ring to Fremennik Slayer Dungeon";
    }
    if (normalized.contains("stronghold slayer cave")) {
      return "Slayer ring to Stronghold Slayer Cave";
    }
    if (normalized.contains("karuulm")) {
      return "Rada's blessing to Mount Karuulm";
    }
    if (normalized.contains("lighthouse")) {
      return "Games necklace to Barbarian Outpost, then run north";
    }
    if (normalized.contains("lunar isle")) {
      return "Lunar Isle teleport";
    }
    if (normalized.contains("meiyerditch")) {
      return "Drakan's medallion to Darkmeyer, then enter the laboratories";
    }
    if (normalized.contains("mos le harmless")) {
      return "Trouble Brewing minigame teleport, then run east";
    }
    if (normalized.contains("wilderness god wars")) {
      return "Cemetery teleport or Wilderness obelisk, then enter the Wilderness God Wars Dungeon";
    }
    if (normalized.contains("god wars")) {
      return "Trollheim teleport, then enter God Wars Dungeon";
    }
    if (normalized.contains("brimhaven dungeon")) {
      return "House teleport to Brimhaven, then run south";
    }
    if (normalized.contains("taverley dungeon")) {
      return "Falador teleport, then run northwest";
    }
    return "Use the nearest unlocked teleport to the assigned area";
  }

  private static CannonSupport cannonForArea(String area) {
    String normalized = normalize(area);
    if (matchesAny(normalized, SlayerLoadoutData.list("cannon_restricted_locations"))) {
      return CannonSupport.NOT_ALLOWED;
    }
    if (normalized.contains("lighthouse")
        || normalized.contains("meiyerditch")
        || normalized.contains("stronghold slayer cave")
        || normalized.contains("iorwerth dungeon")
        || normalized.contains("lunar isle")
        || normalized.contains("kalphite slayer cave")
        || normalized.contains("death plateau")
        || normalized.contains("smoke devil dungeon")) {
      return CannonSupport.RECOMMENDED;
    }
    return CannonSupport.UNKNOWN;
  }

  private static boolean isAssignedArea(String area) {
    if (area == null) {
      return false;
    }
    String normalized = normalize(area);
    return !normalized.isEmpty()
        && !normalized.equals("not restricted")
        && !normalized.equals("assigned area")
        && !normalized.equals("unknown")
        && !normalized.equals("-");
  }

  private static boolean isWilderness(String value) {
    String normalized = normalize(value);
    return normalized.contains("wilderness")
        || normalized.contains("revenant caves")
        || normalized.contains("forinthry");
  }

  private static Map<String, List<LocationOption>> createTaskLocations() {
    Map<String, List<LocationOption>> groups = new LinkedHashMap<>();
    for (String[] fields :
        ResourceTable.rowsWithEscapedDelimiters("slayer-task-locations.tsv", 9)) {
      if (!fields[0].equals("O")) {
        throw new IllegalStateException("Invalid task-location row type: " + fields[0]);
      }
      String aliasKey = fields[1];
      String requirementType = fields[6];
      Quest quest = requirementType.equals("QUEST") ? Quest.valueOf(fields[7]) : null;
      Skill skill = requirementType.equals("SKILL") ? Skill.valueOf(fields[7]) : null;
      int skillLevel = skill == null || fields[8].isEmpty() ? 0 : Integer.parseInt(fields[8]);
      groups
          .computeIfAbsent(aliasKey, unused -> new ArrayList<>())
          .add(
              new LocationOption(
                  fields[2],
                  fields[3],
                  fields[4],
                  CannonSupport.valueOf(fields[5]),
                  quest,
                  quest == null ? "" : fields[8],
                  skill,
                  skillLevel,
                  false));
    }
    Map<String, List<LocationOption>> result = new HashMap<>();
    for (Map.Entry<String, List<LocationOption>> group : groups.entrySet()) {
      List<LocationOption> options =
          Collections.unmodifiableList(new ArrayList<>(group.getValue()));
      for (String alias : group.getKey().split("\\|", -1)) {
        if (result.put(normalize(alias), options) != null) {
          throw new IllegalStateException("Duplicate task-location alias " + alias);
        }
      }
    }
    if (result.isEmpty()) {
      throw new IllegalStateException("Task-location resource is empty");
    }
    return Collections.unmodifiableMap(result);
  }

  private static String normalize(String value) {
    return SlayerText.normalize(value);
  }

  private enum CannonSupport {
    RECOMMENDED,
    OPTIONAL,
    NOT_ALLOWED,
    UNKNOWN
  }

  private static final class LocationOption {
    private final String location;
    private final String method;
    private final String travel;
    private final CannonSupport cannonSupport;
    private final Quest quest;
    private final String questName;
    private final Skill skill;
    private final int skillLevel;
    private final boolean wilderness;

    private LocationOption(
        String location,
        String method,
        String travel,
        CannonSupport cannonSupport,
        Quest quest,
        String questName,
        Skill skill,
        int skillLevel,
        boolean wilderness) {
      this.location = location;
      this.method = method;
      this.travel = travel;
      this.cannonSupport = cannonSupport;
      this.quest = quest;
      this.questName = questName;
      this.skill = skill;
      this.skillLevel = Math.max(0, skillLevel);
      this.wilderness = wilderness;
    }
  }
}
