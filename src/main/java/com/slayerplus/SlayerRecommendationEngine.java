package com.slayerplus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

public final class SlayerRecommendationEngine {
  private static final String TASK_LOCATIONS_RESOURCE = "/com/slayerplus/slayer-task-locations.tsv";
  private static final int KRYSTILIA_MASTER_ID = 7;
  private static final int KONAR_MASTER_ID = 8;
  private static final Map<String, List<LocationOption>> TASK_LOCATIONS = createTaskLocations();
  private final Client client;
  private final SlayerPlusConfig config;
  private final Map<String, List<LocationOption>> taskLocations;

  public SlayerRecommendationEngine(Client client) {
    this(client, null);
  }

  public SlayerRecommendationEngine(Client client, SlayerPlusConfig config) {
    this.client = client;
    this.config = config;
    this.taskLocations = TASK_LOCATIONS;
  }

  static Set<String> catalogLocationsForValidation(String taskName) {
    List<LocationOption> options = TASK_LOCATIONS.get(normalize(taskName));
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

  public Recommendation recommend(String taskName, int masterId, String assignedLocation) {
    String normalizedTask = normalize(taskName);
    if (normalizedTask.isEmpty()) {
      return genericRecommendation("Unknown task", masterId);
    }
    if (masterId == KONAR_MASTER_ID || isAssignedArea(assignedLocation)) {
      return assignedAreaRecommendation(normalizedTask, assignedLocation, masterId);
    }
    if (masterId == KRYSTILIA_MASTER_ID) {
      return wildernessRecommendation(taskName);
    }
    if (masterId == BoostCoordinator.TURAEL_AYA_MASTER_ID
        && config != null
        && config.slayerWorkflow() == Preference.Workflow.TURAEL_POINT_BOOST) {
      Recommendation boost = turaelBoostRecommendation(taskName);
      if (boost != null) {
        return boost;
      }
    }
    List<LocationOption> options = taskLocations.get(normalizedTask);
    if (options == null || options.isEmpty()) {
      return genericRecommendation(taskName, masterId);
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
      return lockedRecommendation(taskName, lockedUpgrade);
    }
    TaskStrategy strategy = strategyFor(normalizedTask, selected.location, false);
    return new Recommendation(
        selected.location,
        strategy.getMethod(),
        buildReason(selected, normalizedTask, selectedScore, masterId)
            + " "
            + strategy.getRationale(),
        selected.travel,
        formatCannon(selected.cannonSupport),
        formatRequirements(selected, lockedUpgrade),
        "Non-Wilderness recommendation",
        RouteCatalog.find(normalizedTask, selected.location),
        strategy);
  }

  private Recommendation turaelBoostRecommendation(String taskName) {
    TuraelBoost.Entry entry = TuraelBoost.find(taskName);
    if (entry == null) {
      return null;
    }
    TaskStrategy strategy =
        strategyFor(taskName, entry.getLocation(), false)
            .withAdditionalTags(TaskStrategy.MethodTag.TURAEL_POINT_BOOST);
    return new Recommendation(
        entry.getLocation(),
        strategy.getMethod(),
        "Turael/Aya point boosting uses the current Wiki point-farming "
            + "location, the lowest practical monster variant, an expeditious "
            + "bracelet when owned, and a cannon only when the selected area "
            + "supports it. "
            + strategy.getRationale(),
        entry.getTravel(),
        entry.getCannon(),
        entry.getRequirements(),
        "Turael/Aya point-boost route",
        RouteCatalog.find(taskName, entry.getLocation()),
        strategy);
  }

  private Recommendation assignedAreaRecommendation(
      String normalizedTask, String assignedLocation, int masterId) {
    String area =
        isAssignedArea(assignedLocation) ? assignedLocation.trim() : "Assigned Slayer area";
    if (masterId != KRYSTILIA_MASTER_ID && isWilderness(area)) {
      return new Recommendation(
          "No non-Wilderness match",
          "Check the assignment with your Slayer master",
          "Safety rule blocked a Wilderness route because Krystilia is not the selected master.",
          "Do not route into the Wilderness",
          "Not recommended",
          "Assigned-area data needs verification",
          "Wilderness blocked unless Krystilia assigned it");
    }
    TaskStrategy strategy =
        strategyFor(normalizedTask, area, masterId == KRYSTILIA_MASTER_ID);
    return new Recommendation(
        area,
        strategy.getMethod(),
        (masterId == KONAR_MASTER_ID
                ? "Konar fixes the destination. Your combat preferences still shape the recommended"
                      + " method and cannon guidance."
                : "The assignment fixes the destination, so preferences are applied only to the"
                      + " method and cannon guidance.")
            + " "
            + strategy.getRationale(),
        travelForArea(area),
        formatCannon(cannonForArea(area)),
        "Use the monster inside the assigned area",
        masterId == KONAR_MASTER_ID ? "Konar — assigned area required" : "Assigned area required",
        RouteCatalog.find(normalizedTask, area),
        strategy);
  }

  private Recommendation wildernessRecommendation(String taskName) {
    String normalized = normalize(taskName);
    String location;
    if (normalized.equals("abyssal demons")
        || normalized.equals("ankou")
        || normalized.equals("black demons")
        || normalized.equals("dust devils")
        || normalized.equals("greater demons")
        || normalized.equals("hellhounds")) {
      location = "Wilderness Slayer Cave";
    } else if (normalized.equals("bloodveld") || normalized.equals("bloodvelds")) {
      location = "Wilderness God Wars Dungeon";
    } else {
      location = "Nearest matching Wilderness spawn";
    }
    TaskStrategy strategy = strategyFor(taskName, location, true);
    return new Recommendation(
        location,
        strategy.getMethod(),
        "Krystilia overrides the normal Wilderness block. Safety and low carried risk take priority"
            + " over the normal profile score. "
            + strategy.getRationale(),
        "Start from Ferox Enclave and use the safest route",
        formatCannon(CannonSupport.OPTIONAL),
        "Krystilia assignment active",
        "Wilderness — protect-item planning required",
        RouteCatalog.find(location),
        strategy);
  }

  private Recommendation genericRecommendation(String taskName, int masterId) {
    SlayerTaskTravelAuditCatalog.Entry auditedTravel = SlayerTaskTravelAuditCatalog.find(taskName);
    if (auditedTravel != null) {
      TaskStrategy auditedStrategy =
          strategyFor(taskName, auditedTravel.getLocation(), masterId == KRYSTILIA_MASTER_ID);
      return new Recommendation(
          auditedTravel.getLocation(),
          auditedStrategy.getMethod(),
          "The task has a Wiki-reviewed fallback destination and loadout. "
              + auditedStrategy.getRationale(),
          auditedTravel.getTravel(),
          auditedTravel.getCannon(),
          "Task-specific equipment and inventory audit active",
          masterId == KRYSTILIA_MASTER_ID
              ? "Krystilia Wilderness task"
              : "Non-Wilderness recommendation",
          RouteCatalog.find(taskName, auditedTravel.getLocation()),
          auditedStrategy);
    }
    TaskStrategy strategy =
        strategyFor(taskName, "Standard Slayer location", masterId == KRYSTILIA_MASTER_ID);
    return new Recommendation(
        "Standard Slayer location",
        strategy.getMethod(),
        (strategy.isReviewed()
                ? "The task has a reviewed combat profile, but its preferred travel destination has"
                      + " not been added to the route catalog yet. "
                : "No generated loadout is available until this task is individually reviewed. ")
            + strategy.getRationale(),
        "Use the nearest unlocked standard task location",
        "Check location rules",
        "Task-specific equipment profile active",
        masterId == KRYSTILIA_MASTER_ID
            ? "Krystilia Wilderness task"
            : "Wilderness locations excluded",
        null,
        strategy);
  }

  private Recommendation lockedRecommendation(String taskName, LocationOption lockedOption) {
    if (lockedOption == null) {
      return genericRecommendation(taskName, 0);
    }
    TaskStrategy strategy = strategyFor(taskName, lockedOption.location, false);
    return new Recommendation(
        "Location locked",
        strategy.getMethod(),
        "The highest-scoring catalog option is locked by an access requirement. "
            + strategy.getRationale(),
        lockedOption.travel,
        formatCannon(lockedOption.cannonSupport),
        requirementName(lockedOption) + " required",
        "Wilderness locations excluded",
        null,
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
    int profit = profitRating(option);
    int afk = afkRating(option);
    int score;
    switch (playstyle()) {
      case PROFIT:
        score = profit * 4 + speed + afk / 4;
        break;
      case FAST_XP:
      default:
        score = speed * 5;
        break;
    }
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
    score += shardPreferenceBonusForRegression(shardPreference(), normalizedTask, option.location);
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

  private int profitRating(LocationOption option) {
    String text = normalize(option.location + " " + option.method);
    int rating = 34;
    if (text.contains("higher value") || text.contains("basilisk knight")) {
      rating += 48;
    }
    if (text.contains("lithkren")
        || text.contains("kraken")
        || text.contains("gargoyle")
        || text.contains("skeletal wyvern")) {
      rating += 35;
    }
    if (text.contains("darkmeyer") || text.contains("cave horror") || text.contains("abyssal")) {
      rating += 24;
    }
    if (text.contains("ancient shard") || text.contains("totem")) {
      rating += 18;
    }
    return clampRating(rating);
  }

  private int afkRating(LocationOption option) {
    String text = normalize(option.method);
    int rating = 45;
    if (text.contains("single combat") || text.contains("melee")) {
      rating += 24;
    }
    if (text.contains("ranged")) {
      rating += 12;
    }
    if (text.contains("cannon")) {
      rating += 12;
    }
    if (text.contains("burst") || text.contains("barrage") || text.contains("stacked")) {
      rating -= 26;
    }
    if (text.contains("finish each kill")
        || text.contains("witchwood")
        || text.contains("boots of stone")) {
      rating -= 18;
    }
    return clampRating(rating);
  }

  private int travelScore(String route) {
    String normalized = normalize(route);
    boolean directTeleport =
        normalized.contains("teleport")
            || normalized.contains("slayer ring")
            || normalized.contains("xeric")
            || normalized.contains("rada")
            || normalized.contains("drakan")
            || normalized.contains("digsite pendant")
            || normalized.contains("fairy ring");
    boolean freeOrReusable =
        normalized.contains("minigame teleport")
            || normalized.contains("fairy ring")
            || normalized.contains("spirit tree")
            || normalized.contains("run from")
            || normalized.contains("then run");
    boolean likelyConsumable =
        normalized.contains("games necklace")
            || normalized.contains("talisman")
            || normalized.contains("slayer ring")
            || normalized.contains("digsite pendant")
            || normalized.contains("teleport crystal")
            || normalized.contains("house teleport");
    switch (travelPreference()) {
      case CHEAPEST:
        if (freeOrReusable) {
          return 52;
        }
        return likelyConsumable ? -20 : 18;
      case AVOID_CONSUMABLES:
        return likelyConsumable ? -70 : 42;
      case FASTEST:
      default:
        return directTeleport ? 45 : 8;
    }
  }

  private String buildReason(
      LocationOption option, String normalizedTask, int score, int masterId) {
    StringBuilder reason = new StringBuilder();
    reason.append(playstyle()).append(" profile selected this unlocked option");
    switch (playstyle()) {
      case PROFIT:
        reason.append(" for its stronger loot and supply-efficiency potential");
        break;
      case FAST_XP:
      default:
        reason.append(" for maximum kill-speed potential without a gear-cost penalty");
        break;
    }
    if (cannonPreference() == Preference.Cannon.PREFER
        && option.cannonSupport != CannonSupport.NOT_ALLOWED) {
      reason.append("; cannon preference increased its score");
    } else if (cannonPreference() == Preference.Cannon.NEVER
        && option.cannonSupport == CannonSupport.NOT_ALLOWED) {
      reason.append("; it naturally avoids cannon use");
    }
    if (burstPreference() == Preference.Burst.PREFER && supportsBurst(option)) {
      reason.append("; burst/barrage preference increased its score");
    } else if (burstPreference() == Preference.Burst.NEVER && !supportsBurst(option)) {
      reason.append("; it avoids burst/barrage");
    }
    if (shardPreferenceBonusForRegression(shardPreference(), normalizedTask, option.location) > 0) {
      reason.append("; shard preference selected a valid shard-producing area");
    }
    reason
        .append(". Travel was ranked by ")
        .append(travelPreference().toString().toLowerCase(Locale.ENGLISH))
        .append(". Score: ")
        .append(score)
        .append('.');
    return reason.toString();
  }

  private String formatMethod(LocationOption option) {
    boolean hasBurst = supportsBurst(option);
    boolean hasCannon = normalize(option.method).contains("cannon");
    if (burstPreference() == Preference.Burst.NEVER
        && cannonPreference() == Preference.Cannon.NEVER
        && hasBurst
        && hasCannon) {
      return "Use melee or ranged; cannon and burst/barrage are disabled by preference.";
    }
    if (burstPreference() == Preference.Burst.NEVER && hasBurst) {
      return option.cannonSupport == CannonSupport.RECOMMENDED
              && cannonPreference() != Preference.Cannon.NEVER
          ? "Use melee or ranged with a cannon where allowed; burst/barrage is disabled by"
                + " preference."
          : "Use melee or ranged; burst/barrage is disabled by preference.";
    }
    if (cannonPreference() == Preference.Cannon.NEVER && hasCannon) {
      return hasBurst
          ? "Burst or barrage without a cannon."
          : "Use the strongest non-cannon setup available here.";
    }
    if (burstPreference() == Preference.Burst.PREFER && hasBurst) {
      return option.method + " — preferred by profile";
    }
    return option.method;
  }

  private static boolean supportsBurst(LocationOption option) {
    String method = normalize(option.method);
    return method.contains("burst") || method.contains("barrage");
  }

  private static int clampRating(int value) {
    return Math.max(0, Math.min(100, value));
  }

  private Preference.Playstyle playstyle() {
    return config == null || config.playstyle() == null
        ? Preference.Playstyle.FAST_XP
        : config.playstyle();
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

  private Preference.Travel travelPreference() {
    return config == null || config.travelPreference() == null
        ? Preference.Travel.FASTEST
        : config.travelPreference();
  }

  private Preference.Shard shardPreference() {
    return config == null || config.shardPreference() == null
        ? Preference.Shard.NO_PREFERENCE
        : config.shardPreference();
  }

  static int shardPreferenceBonusForRegression(
      Preference.Shard preference, String taskName, String location) {
    if (preference == null || preference == Preference.Shard.NO_PREFERENCE) {
      return 0;
    }
    String task = normalize(taskName);
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

  private TaskStrategy strategyFor(String taskName, String location, boolean wilderness) {
    return SlayerTaskStrategyCatalog.resolve(
        taskName,
        playstyle(),
        cannonPreference(),
        burstPreference(),
        combatStylePreference(),
        location,
        wilderness);
  }

  private String methodForTask(String normalizedTask) {
    return strategyFor(normalizedTask, "Assigned Slayer area", false).getMethod();
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
    if (normalized.contains("catacombs")
        || normalized.contains("slayer tower")
        || normalized.contains("fremennik slayer dungeon")
        || normalized.contains("karuulm")
        || normalized.contains("kraken")
        || normalized.contains("god wars")
        || normalized.contains("mos le harmless")) {
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
    for (String[] fields : ResourceTable.decodedRows("slayer-task-locations.tsv", 9)) {
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
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ENGLISH)
        .replace('\u2019', '\'')
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
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
