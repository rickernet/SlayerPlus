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
import net.runelite.api.coords.WorldPoint;

public final class RouteCatalog {
  public static final int CATALOG_REVISION = 15;
  private static final String PROFILE_RESOURCE = "/com/slayerplus/slayer-route-profiles.tsv";

  public enum RouteMode {
    OPEN_WORLD,
    VERIFIED_INTERIOR,
    TRANSPORT_CHAIN_INTERIOR,
    STAGED_INTERIOR,
    ACCESS_THEN_NPC,
    NPC_ONLY,
    BOSS_OPEN_WORLD,
    BOSS_VERIFIED_INTERIOR,
    BOSS_TRANSPORT_CHAIN_INTERIOR,
    BOSS_STAGED_INTERIOR,
    BOSS_ACCESS_THEN_NPC,
    BOSS_NPC_ONLY
  }

  public static final class TransitionSpec {
    private final WorldPoint approachPoint;
    private final Set<String> objectNameHints;
    private final Set<String> actions;
    private final int searchRadius;

    private TransitionSpec(
        WorldPoint approachPoint,
        Set<String> objectNameHints,
        Set<String> actions,
        int searchRadius) {
      this.approachPoint = approachPoint;
      this.objectNameHints = Collections.unmodifiableSet(new LinkedHashSet<>(objectNameHints));
      this.actions = Collections.unmodifiableSet(new LinkedHashSet<>(actions));
      this.searchRadius = Math.max(1, searchRadius);
    }

    public WorldPoint getApproachPoint() {
      return approachPoint;
    }

    public int getSearchRadius() {
      return searchRadius;
    }

    public boolean hasObjectNameHints() {
      return !objectNameHints.isEmpty();
    }

    public Set<String> getObjectNameHints() {
      return objectNameHints;
    }

    public Set<String> getActions() {
      return actions;
    }

    public boolean matchesObjectName(String objectName) {
      if (objectNameHints.isEmpty()) {
        return true;
      }
      String value = normalize(objectName);
      for (String hint : objectNameHints) {
        if (!hint.isEmpty() && value.contains(hint)) {
          return true;
        }
      }
      return false;
    }

    public boolean matchesAction(String action) {
      return actions.contains(normalize(action));
    }
  }

  public static final class EncounterArea {
    private final WorldPoint center;
    private final int xRadius;
    private final int yRadius;

    private EncounterArea(WorldPoint center, int xRadius, int yRadius) {
      this.center = center;
      this.xRadius = Math.max(1, xRadius);
      this.yRadius = Math.max(1, yRadius);
    }

    public boolean contains(WorldPoint point) {
      return point != null
          && center != null
          && point.getPlane() == center.getPlane()
          && sameCoordinateLayer(point, center)
          && Math.abs(point.getX() - center.getX()) <= xRadius
          && Math.abs(point.getY() - center.getY()) <= yRadius;
    }
  }

  public static final class RouteProfile {
    private static final int ENCOUNTER_REGION_RADIUS = 192;
    private final String taskName;
    private final String location;
    private final WorldPoint primaryDestination;
    private final WorldPoint surfaceAccess;
    private final TransitionSpec transitionSpec;
    private final EncounterArea encounterArea;
    private final String transitionInstruction;
    private final String positioningNote;
    private final RouteMode mode;
    private final Set<String> npcNames;
    private final int targetRadius;
    private final int arrivalRadius;

    private RouteProfile(
        String taskName,
        String location,
        WorldPoint primaryDestination,
        RouteMode mode,
        Set<String> npcNames,
        int targetRadius,
        int arrivalRadius) {
      this(
          taskName,
          location,
          primaryDestination,
          null,
          "",
          "",
          mode,
          npcNames,
          targetRadius,
          arrivalRadius);
    }

    private RouteProfile(
        String taskName,
        String location,
        WorldPoint primaryDestination,
        WorldPoint surfaceAccess,
        String transitionInstruction,
        String positioningNote,
        RouteMode mode,
        Set<String> npcNames,
        int targetRadius,
        int arrivalRadius) {
      this.taskName = taskName == null ? "" : taskName.trim();
      this.location = location == null ? "" : location.trim();
      this.primaryDestination = primaryDestination;
      this.surfaceAccess = surfaceAccess;
      this.transitionSpec =
          surfaceAccess == null ? null : transitionSpecFor(this.location, surfaceAccess);
      this.encounterArea =
          primaryDestination == null
              ? null
              : new EncounterArea(
                  primaryDestination, ENCOUNTER_REGION_RADIUS, ENCOUNTER_REGION_RADIUS);
      this.transitionInstruction =
          transitionInstruction == null ? "" : transitionInstruction.trim();
      this.positioningNote = positioningNote == null ? "" : positioningNote.trim();
      this.mode = mode;
      this.npcNames = Collections.unmodifiableSet(new LinkedHashSet<>(npcNames));
      this.targetRadius = Math.max(0, targetRadius);
      this.arrivalRadius = Math.max(1, arrivalRadius);
      if (isStaged()) {
        validateStagedSurfaceAccess();
      }
    }

    private void validateStagedSurfaceAccess() {
      if (surfaceAccess == null) {
        throw new IllegalStateException(
            "Staged Slayer route is missing a surface entrance: " + taskName + " @ " + location);
      }
      if (surfaceAccess.getY() >= 6400) {
        throw new IllegalStateException(
            "Staged Slayer route surface entrance is not on the overworld: "
                + taskName
                + " @ "
                + location
                + " -> "
                + surfaceAccess);
      }
      if (primaryDestination != null && surfaceAccess.equals(primaryDestination)) {
        throw new IllegalStateException(
            "Staged Slayer route surface entrance equals its interior target: "
                + taskName
                + " @ "
                + location);
      }
    }

    public String getTaskName() {
      return taskName;
    }

    public String getLocation() {
      return location;
    }

    public WorldPoint getPrimaryDestination() {
      return primaryDestination;
    }

    public WorldPoint getSurfaceAccess() {
      return surfaceAccess;
    }

    public TransitionSpec getTransitionSpec() {
      return transitionSpec;
    }

    public EncounterArea getEncounterArea() {
      return encounterArea;
    }

    public String getTransitionInstruction() {
      return transitionInstruction;
    }

    public String getPositioningNote() {
      return positioningNote;
    }

    public RouteMode getMode() {
      return mode;
    }

    public boolean isVerifiedInterior() {
      return mode == RouteMode.VERIFIED_INTERIOR
          || mode == RouteMode.TRANSPORT_CHAIN_INTERIOR
          || mode == RouteMode.STAGED_INTERIOR
          || mode == RouteMode.BOSS_VERIFIED_INTERIOR
          || mode == RouteMode.BOSS_TRANSPORT_CHAIN_INTERIOR
          || mode == RouteMode.BOSS_STAGED_INTERIOR;
    }

    public boolean isStaged() {
      return mode == RouteMode.STAGED_INTERIOR || mode == RouteMode.BOSS_STAGED_INTERIOR;
    }

    public boolean isAccessThenNpc() {
      return mode == RouteMode.ACCESS_THEN_NPC || mode == RouteMode.BOSS_ACCESS_THEN_NPC;
    }

    public boolean requiresLoadedNpc() {
      return mode == RouteMode.NPC_ONLY || mode == RouteMode.BOSS_NPC_ONLY;
    }

    public boolean hasStaticDestination() {
      return primaryDestination != null;
    }

    public boolean isBoss() {
      return mode == RouteMode.BOSS_OPEN_WORLD
          || mode == RouteMode.BOSS_ACCESS_THEN_NPC
          || mode == RouteMode.BOSS_VERIFIED_INTERIOR
          || mode == RouteMode.BOSS_TRANSPORT_CHAIN_INTERIOR
          || mode == RouteMode.BOSS_STAGED_INTERIOR
          || mode == RouteMode.BOSS_NPC_ONLY;
    }

    public boolean isInsideEncounterArea(WorldPoint playerLocation) {
      if (playerLocation == null || primaryDestination == null) {
        return false;
      }
      return encounterArea != null && encounterArea.contains(playerLocation);
    }

    public boolean shouldRouteToSurfaceAccess(WorldPoint playerLocation) {
      if (!isStaged() || surfaceAccess == null) {
        return false;
      }
      if (playerLocation == null) {
        return true;
      }
      if (!sameCoordinateLayer(surfaceAccess, primaryDestination)) {
        return sameCoordinateLayer(playerLocation, surfaceAccess);
      }
      return !isInsideEncounterArea(playerLocation);
    }

    public boolean isPlausibleLearnedCheckpoint(WorldPoint checkpoint) {
      if (checkpoint == null
          || checkpoint.getPlane() < 0
          || checkpoint.getPlane() > 3
          || checkpoint.getX() <= 0
          || checkpoint.getY() <= 0) {
        return false;
      }
      if (isStaged() && primaryDestination != null) {
        return encounterArea != null && encounterArea.contains(checkpoint);
      }
      return true;
    }

    public WorldPoint getRouteDestination(WorldPoint playerLocation) {
      return shouldRouteToSurfaceAccess(playerLocation) ? surfaceAccess : primaryDestination;
    }

    public int getArrivalRadius() {
      return arrivalRadius;
    }

    public int getArrivalRadius(WorldPoint playerLocation) {
      return shouldRouteToSurfaceAccess(playerLocation) ? ACCESS_ARRIVAL_RADIUS : arrivalRadius;
    }

    public Set<WorldPoint> getRouteTargets() {
      return targetArea(primaryDestination, targetRadius);
    }

    public Set<WorldPoint> getRouteTargets(WorldPoint playerLocation) {
      WorldPoint destination = getRouteDestination(playerLocation);
      if (destination != null
          && destination.equals(surfaceAccess)
          && isStaged()
          && !usesReachableSurfaceApproachArea()) {
        return Collections.singleton(destination);
      }
      int radius =
          destination != null && destination.equals(surfaceAccess)
              ? ACCESS_TARGET_RADIUS
              : targetRadius;
      return targetArea(destination, radius);
    }

    private boolean usesReachableSurfaceApproachArea() {
      return normalize(location).equals("wyvern cave on fossil island");
    }

    public boolean matchesNpc(String npcName) {
      String normalized = normalizeNpcName(npcName);
      return !normalized.isEmpty() && npcNames.contains(normalized);
    }

    public Set<String> getNpcNames() {
      return npcNames;
    }
  }

  private static final int INTERIOR_TARGET_RADIUS = 2;
  private static final int ACCESS_TARGET_RADIUS = 1;
  private static final int INTERIOR_ARRIVAL_RADIUS = 14;
  private static final int ACCESS_ARRIVAL_RADIUS = 2;
  private static final Set<String> INFERNO_ENTRY_NPC_NAMES =
      Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("tzhaar ket keh")));
  private static final WorldPoint INFERNO_ENTRY_APPROACH = new WorldPoint(2496, 5115, 0);
  private static final List<String[]> RESOURCE_ROWS = loadResourceRows();
  private static final Map<String, WorldPoint> ACCESS_ROUTES = loadAccessRoutes();
  private static final ProfileData PROFILE_DATA = loadProfiles();
  private static final Map<String, RouteProfile> STANDARD_PROFILES = PROFILE_DATA.standardProfiles;
  private static final Map<String, RouteProfile> BOSS_PROFILES = PROFILE_DATA.bossProfiles;
  private static final Map<String, WorldPoint> CANNON_POSITIONS = createCannonPositions();
  private static final Map<String, WorldPoint> BARRAGE_POSITIONS = createBarragePositions();

  private RouteCatalog() {}

  public static boolean requiresPreparationBank(
      String taskName, String location, boolean bossVariant) {
    return bossVariant
        && normalizeTaskKey(taskName).equals("tzkal zuk")
        && normalize(location).equals("inferno");
  }

  public static Set<String> getPreparationEntryNpcNames(
      String taskName, String location, boolean bossVariant) {
    return requiresPreparationBank(taskName, location, bossVariant)
        ? INFERNO_ENTRY_NPC_NAMES
        : Collections.emptySet();
  }

  public static WorldPoint getPreparationEntryApproachTarget(
      String taskName, String location, boolean bossVariant) {
    return requiresPreparationBank(taskName, location, bossVariant) ? INFERNO_ENTRY_APPROACH : null;
  }

  public static RouteProfile resolve(String taskName, String location, boolean bossVariant) {
    String task = normalizeTaskKey(taskName);
    String area = normalize(location);
    if (task.isEmpty() || area.isEmpty()) {
      return null;
    }
    String key = profileKey(task, area);
    if (bossVariant) {
      RouteProfile reviewedBoss = BOSS_PROFILES.get(key);
      if (reviewedBoss != null) {
        return reviewedBoss;
      }
      WorldPoint bossAccess = ACCESS_ROUTES.get(area);
      if (bossAccess != null) {
        return bossAccessProfile(taskName, location, bossAccess, defaultNpcAliases(taskName));
      }
      return npcOnlyProfile(taskName, location, defaultNpcAliases(taskName), true);
    }
    RouteProfile reviewed = STANDARD_PROFILES.get(key);
    if (reviewed != null) {
      return reviewed;
    }
    WorldPoint access = ACCESS_ROUTES.get(area);
    if (access == null) {
      return npcOnlyProfile(taskName, location, defaultNpcAliases(taskName), false);
    }
    return accessProfile(taskName, location, access, defaultNpcAliases(taskName));
  }

  public static WorldPoint find(String taskName, String location) {
    RouteProfile profile = resolve(taskName, location, false);
    return profile == null ? null : profile.getPrimaryDestination();
  }

  public static WorldPoint find(String location) {
    return ACCESS_ROUTES.get(normalize(location));
  }

  static Set<String> reviewedAccessLocationsForRegression() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(ACCESS_ROUTES.keySet()));
  }

  static boolean hasExplicitProfileForRegression(
      String taskName, String location, boolean bossVariant) {
    String task = normalizeTaskKey(taskName);
    String area = normalize(location);
    if (task.isEmpty() || area.isEmpty()) {
      return false;
    }
    return (bossVariant ? BOSS_PROFILES : STANDARD_PROFILES).containsKey(profileKey(task, area));
  }

  static Set<String> explicitProfileKeysForRegression(boolean bossVariant) {
    return Collections.unmodifiableSet(
        new LinkedHashSet<>((bossVariant ? BOSS_PROFILES : STANDARD_PROFILES).keySet()));
  }

  public static boolean hasTaskSpecificRoute(String taskName, String location) {
    RouteProfile profile = resolve(taskName, location, false);
    return profile != null && profile.isVerifiedInterior();
  }

  public static boolean prefersWorldMapEntranceIcon(String location) {
    String area = normalize(location);
    return area.contains("dungeon")
        || area.contains("cave")
        || area.contains("cavern")
        || area.contains("catacombs")
        || area.contains("tunnel")
        || area.contains("lair")
        || area.contains("prison")
        || area.contains("vault")
        || area.contains("chasm")
        || area.contains("stronghold")
        || area.contains("slayer tower")
        || area.contains("cove");
  }

  public static WorldPoint findCannonPosition(String taskName, String location) {
    String task = normalizeTaskKey(taskName);
    String area = normalize(location);
    if (task.isEmpty() || area.isEmpty()) {
      return null;
    }
    return CANNON_POSITIONS.get(profileKey(task, area));
  }

  public static WorldPoint findBarragePosition(String taskName, String location) {
    String task = normalizeTaskKey(taskName);
    String area = normalize(location);
    if (task.isEmpty() || area.isEmpty()) {
      return null;
    }
    return BARRAGE_POSITIONS.get(profileKey(task, area));
  }

  public static boolean isWilderness(String value) {
    String normalized = normalize(value);
    return normalized.contains("wilderness")
        || normalized.contains("revenant")
        || normalized.contains("forinthry")
        || normalized.equals("hunter s end")
        || normalized.equals("callisto s den")
        || normalized.equals("scorpia s cave")
        || normalized.equals("web chasm")
        || normalized.equals("silk chasm")
        || normalized.equals("skeletal tomb")
        || normalized.equals("vet ion s rest");
  }

  public static Set<WorldPoint> targetArea(WorldPoint center, int radius) {
    if (center == null) {
      return Collections.emptySet();
    }
    int safeRadius = Math.max(0, radius);
    Set<WorldPoint> targets = new LinkedHashSet<>();
    for (int dx = -safeRadius; dx <= safeRadius; dx++) {
      for (int dy = -safeRadius; dy <= safeRadius; dy++) {
        targets.add(new WorldPoint(center.getX() + dx, center.getY() + dy, center.getPlane()));
      }
    }
    return Collections.unmodifiableSet(targets);
  }

  private static List<String[]> loadResourceRows() {
    List<String[]> rows = ResourceTable.decodedRows("slayer-route-profiles.tsv", 5, 13);
    for (String[] fields : rows) {
      if ((fields[0].equals("A")) != (fields.length == 5)) {
        throw new IllegalStateException("Invalid route profile row type: " + fields[0]);
      }
    }
    return rows;
  }

  private static Map<String, WorldPoint> loadAccessRoutes() {
    Map<String, WorldPoint> routes = new LinkedHashMap<>();
    for (String[] fields : RESOURCE_ROWS) {
      if (fields[0].equals("A")) {
        putAccess(routes, fields[1], integer(fields[2]), integer(fields[3]), integer(fields[4]));
      }
    }
    return Collections.unmodifiableMap(routes);
  }

  private static ProfileData loadProfiles() {
    Map<String, RouteProfile> standard = new LinkedHashMap<>();
    Map<String, RouteProfile> bosses = new LinkedHashMap<>();
    for (String[] fields : RESOURCE_ROWS) {
      if (fields[0].equals("S") || fields[0].equals("B")) {
        applyProfileRow(fields[0].equals("B") ? bosses : standard, fields);
      }
    }
    addFightCaveProfile(bosses);
    return new ProfileData(
        Collections.unmodifiableMap(standard), Collections.unmodifiableMap(bosses));
  }

  private static void applyProfileRow(Map<String, RouteProfile> profiles, String[] fields) {
    String helper = fields[1];
    String task = fields[2];
    String location = fields[3];
    int[] access = coordinates(fields, 4);
    int[] destination = coordinates(fields, 7);
    String transition = fields[10];
    String note = fields[11];
    String[] npcs = splitNames(fields[12]);
    switch (helper) {
      case "putOpenWorld":
        putOpenWorld(
            profiles, task, location, destination[0], destination[1], destination[2], npcs);
        break;
      case "putTraversableInterior":
        putTraversableInterior(
            profiles, task, location, destination[0], destination[1], destination[2], npcs);
        break;
      case "putStagedInterior":
        putStagedInterior(
            profiles,
            task,
            location,
            access[0],
            access[1],
            access[2],
            destination[0],
            destination[1],
            destination[2],
            transition,
            note,
            npcs);
        break;
      case "putStagedInteriorWide":
        putStagedInteriorWide(
            profiles,
            task,
            location,
            access[0],
            access[1],
            access[2],
            destination[0],
            destination[1],
            destination[2],
            transition,
            note,
            npcs);
        break;
      case "putStagedInteriorFromAccess":
        putStagedInteriorFromAccess(
            profiles, task, location, destination[0], destination[1], destination[2], npcs);
        break;
      case "putStagedInteriorFromAccessWithPositioning":
        putStagedInteriorFromAccessWithPositioning(
            profiles, task, location, destination[0], destination[1], destination[2], note, npcs);
        break;
      case "putAccessProfile":
        putAccessProfile(profiles, task, location, npcs);
        break;
      case "putAccessProfileAt":
        putAccessProfileAt(profiles, task, location, access[0], access[1], access[2], npcs);
        break;
      case "putBossOpenWorld":
        putBossOpenWorld(
            profiles, task, location, destination[0], destination[1], destination[2], npcs);
        break;
      case "putBossInterior":
        putBossInterior(
            profiles, task, location, destination[0], destination[1], destination[2], npcs);
        break;
      case "putBossTraversableInterior":
        putBossTraversableInterior(
            profiles, task, location, destination[0], destination[1], destination[2], npcs);
        break;
      case "putBossStagedInteriorWide":
        putBossStagedInteriorWide(
            profiles,
            task,
            location,
            access[0],
            access[1],
            access[2],
            destination[0],
            destination[1],
            destination[2],
            transition,
            note,
            npcs);
        break;
      case "putBossStagedInteractionCheckpoint":
        putBossStagedInteractionCheckpoint(
            profiles, task, location, destination[0], destination[1], destination[2], npcs);
        break;
      case "putBossAccessProfile":
        putBossAccessProfile(profiles, task, location, npcs);
        break;
      default:
        throw new IllegalStateException("Unsupported route profile helper " + helper);
    }
  }

  private static void addFightCaveProfile(Map<String, RouteProfile> profiles) {
    WorldPoint entrance = new WorldPoint(2439, 5172, 0);
    putProfile(
        profiles,
        new RouteProfile(
            "TzTok-Jad",
            "TzHaar Fight Cave",
            entrance,
            entrance,
            "",
            "",
            RouteMode.BOSS_VERIFIED_INTERIOR,
            npcAliases("TzTok-Jad", "TzTok-Jad"),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static int[] coordinates(String[] fields, int offset) {
    if (fields[offset].isEmpty()) {
      return new int[] {0, 0, 0};
    }
    return new int[] {
      integer(fields[offset]), integer(fields[offset + 1]), integer(fields[offset + 2])
    };
  }

  private static int integer(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      throw new IllegalStateException("Invalid route coordinate " + value, ex);
    }
  }

  private static String[] splitNames(String value) {
    return value.isEmpty() ? new String[0] : value.split("\\|", -1);
  }

  private static String decode(String value) {
    return value.replace("%7C", "|").replace("%0A", "\n").replace("%09", "\t").replace("%25", "%");
  }

  private static final class ProfileData {
    private final Map<String, RouteProfile> standardProfiles;
    private final Map<String, RouteProfile> bossProfiles;

    private ProfileData(
        Map<String, RouteProfile> standardProfiles, Map<String, RouteProfile> bossProfiles) {
      this.standardProfiles = standardProfiles;
      this.bossProfiles = bossProfiles;
    }
  }

  private static Map<String, WorldPoint> createCannonPositions() {
    Map<String, WorldPoint> positions = new LinkedHashMap<>();
    positions.put(
        profileKey(normalizeTaskKey("Dark beasts"), normalize("Mourner Tunnels")),
        new WorldPoint(1992, 4655, 0));
    positions.put(
        profileKey(normalizeTaskKey("Bloodvelds"), normalize("Meiyerditch Laboratories")),
        new WorldPoint(3594, 9743, 0));
    positions.put(
        profileKey(normalizeTaskKey("Bloodvelds"), normalize("Buccaneers' Laboratory")),
        new WorldPoint(2096, 10099, 0));
    positions.put(
        profileKey(normalizeTaskKey("Bloodvelds"), normalize("Iorwerth Dungeon")),
        new WorldPoint(3236, 12438, 0));
    positions.put(
        profileKey(normalizeTaskKey("Bloodvelds"), normalize("Stronghold Slayer Cave")),
        new WorldPoint(2465, 9833, 0));
    positions.put(
        profileKey(normalizeTaskKey("Cave horrors"), normalize("Mos Le'Harmless Cave")),
        new WorldPoint(3775, 9407, 0));
    return Collections.unmodifiableMap(positions);
  }

  private static Map<String, WorldPoint> createBarragePositions() {
    Map<String, WorldPoint> positions = new LinkedHashMap<>();
    positions.put(
        profileKey(normalizeTaskKey("Abyssal demons"), normalize("Catacombs of Kourend")),
        new WorldPoint(1675, 10091, 0));
    return Collections.unmodifiableMap(positions);
  }

  private static void putStagedInteriorFromAccess(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String... npcNames) {
    putStagedInteriorFromAccessWithPositioning(
        profiles, taskName, location, x, y, plane, "", npcNames);
  }

  private static void putStagedInteriorFromAccessWithPositioning(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String positioningNote,
      String... npcNames) {
    WorldPoint access = ACCESS_ROUTES.get(normalize(location));
    if (access == null) {
      throw new IllegalStateException(
          "Explicit staged route is missing an access coordinate: " + taskName + " @ " + location);
    }
    WorldPoint destination = new WorldPoint(x, y, plane);
    if (sameCoordinateLayer(access, destination)) {
      throw new IllegalStateException(
          "Staged route access and interior are on the same coordinate layer: "
              + taskName
              + " @ "
              + location);
    }
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            destination,
            access,
            "Use the reviewed "
                + location
                + " entrance; SlayerPlus will continue only after the transition is detected.",
            positioningNote,
            RouteMode.STAGED_INTERIOR,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putInterior(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String... npcNames) {
    WorldPoint destination = new WorldPoint(x, y, plane);
    WorldPoint access = ACCESS_ROUTES.get(normalize(location));
    if (access != null
        && prefersWorldMapEntranceIcon(location)
        && !sameCoordinateLayer(access, destination)) {
      throw new IllegalStateException(
          "Cross-layer reviewed interior must be authored as an explicit staged route: "
              + taskName
              + " @ "
              + location);
    }
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            destination,
            RouteMode.VERIFIED_INTERIOR,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putOpenWorld(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String... npcNames) {
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(x, y, plane),
            RouteMode.OPEN_WORLD,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putTraversableInterior(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String... npcNames) {
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(x, y, plane),
            RouteMode.TRANSPORT_CHAIN_INTERIOR,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putInteriorWithPositioning(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String positioningNote,
      String... npcNames) {
    WorldPoint destination = new WorldPoint(x, y, plane);
    WorldPoint access = ACCESS_ROUTES.get(normalize(location));
    if (access != null
        && prefersWorldMapEntranceIcon(location)
        && !sameCoordinateLayer(access, destination)) {
      throw new IllegalStateException(
          "Cross-layer positioned interior must be authored as an explicit staged route: "
              + taskName
              + " @ "
              + location);
    }
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            destination,
            null,
            "",
            positioningNote,
            RouteMode.VERIFIED_INTERIOR,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putStagedInterior(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int accessX,
      int accessY,
      int accessPlane,
      int destinationX,
      int destinationY,
      int destinationPlane,
      String transitionInstruction,
      String positioningNote,
      String... npcNames) {
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(destinationX, destinationY, destinationPlane),
            new WorldPoint(accessX, accessY, accessPlane),
            transitionInstruction,
            positioningNote,
            RouteMode.STAGED_INTERIOR,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putStagedInteriorWide(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int accessX,
      int accessY,
      int accessPlane,
      int destinationX,
      int destinationY,
      int destinationPlane,
      String transitionInstruction,
      String positioningNote,
      String... npcNames) {
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(destinationX, destinationY, destinationPlane),
            new WorldPoint(accessX, accessY, accessPlane),
            transitionInstruction,
            positioningNote,
            RouteMode.STAGED_INTERIOR,
            npcAliases(taskName, npcNames),
            8,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putAccessProfile(
      Map<String, RouteProfile> profiles, String taskName, String location, String... npcNames) {
    WorldPoint access = ACCESS_ROUTES.get(normalize(location));
    if (access == null) {
      throw new IllegalStateException("Missing access route for " + location);
    }
    putProfile(profiles, accessProfile(taskName, location, access, npcAliases(taskName, npcNames)));
  }

  private static void putAccessProfileAt(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int accessX,
      int accessY,
      int accessPlane,
      String... npcNames) {
    putProfile(
        profiles,
        accessProfile(
            taskName,
            location,
            new WorldPoint(accessX, accessY, accessPlane),
            npcAliases(taskName, npcNames)));
  }

  private static RouteProfile accessProfile(
      String taskName, String location, WorldPoint access, Set<String> aliases) {
    return new RouteProfile(
        taskName,
        location,
        access,
        access,
        "Use the reviewed "
            + location
            + " transition; SlayerPlus will continue only to an exact loaded task NPC.",
        "",
        RouteMode.ACCESS_THEN_NPC,
        aliases,
        ACCESS_TARGET_RADIUS,
        ACCESS_ARRIVAL_RADIUS);
  }

  private static RouteProfile bossAccessProfile(
      String taskName, String location, WorldPoint access, Set<String> aliases) {
    return new RouteProfile(
        taskName,
        location,
        access,
        access,
        "Use the reviewed "
            + location
            + " access route; SlayerPlus will continue only to the exact loaded boss.",
        "",
        RouteMode.BOSS_ACCESS_THEN_NPC,
        aliases,
        ACCESS_TARGET_RADIUS,
        ACCESS_ARRIVAL_RADIUS);
  }

  private static void putBossAccessProfile(
      Map<String, RouteProfile> profiles, String taskName, String location, String... npcNames) {
    WorldPoint access = ACCESS_ROUTES.get(normalize(location));
    if (access == null) {
      throw new IllegalStateException(
          "Missing boss access route for " + taskName + " @ " + location);
    }
    putProfile(
        profiles, bossAccessProfile(taskName, location, access, npcAliases(taskName, npcNames)));
  }

  private static RouteProfile npcOnlyProfile(
      String taskName, String location, Set<String> aliases, boolean boss) {
    return new RouteProfile(
        taskName,
        location,
        null,
        boss ? RouteMode.BOSS_NPC_ONLY : RouteMode.NPC_ONLY,
        aliases,
        0,
        INTERIOR_ARRIVAL_RADIUS);
  }

  private static void putBossAccess(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String... npcNames) {
    WorldPoint access = new WorldPoint(x, y, plane);
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            access,
            access,
            "Use the reviewed boss access transition; SlayerPlus will continue only to the exact"
                + " boss.",
            "",
            RouteMode.BOSS_ACCESS_THEN_NPC,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putBossStagedInterior(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int accessX,
      int accessY,
      int accessPlane,
      int destinationX,
      int destinationY,
      int destinationPlane,
      String transitionInstruction,
      String positioningNote,
      String... npcNames) {
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(destinationX, destinationY, destinationPlane),
            new WorldPoint(accessX, accessY, accessPlane),
            transitionInstruction,
            positioningNote,
            RouteMode.BOSS_STAGED_INTERIOR,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putBossStagedInteractionCheckpoint(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String... npcNames) {
    WorldPoint access = ACCESS_ROUTES.get(normalize(location));
    if (access == null) {
      throw new IllegalStateException(
          "Missing outer access route for boss checkpoint: " + location);
    }
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(x, y, plane),
            access,
            "Enter "
                + location
                + "; SlayerPlus will then route to the exact reviewed boss-room interaction"
                + " checkpoint.",
            "",
            RouteMode.BOSS_STAGED_INTERIOR,
            npcAliases(taskName, npcNames),
            0,
            2));
  }

  private static void putBossStagedInteriorWide(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int accessX,
      int accessY,
      int accessPlane,
      int destinationX,
      int destinationY,
      int destinationPlane,
      String transitionInstruction,
      String positioningNote,
      String... npcNames) {
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(destinationX, destinationY, destinationPlane),
            new WorldPoint(accessX, accessY, accessPlane),
            transitionInstruction,
            positioningNote,
            RouteMode.BOSS_STAGED_INTERIOR,
            npcAliases(taskName, npcNames),
            8,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putBossInterior(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String... npcNames) {
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(x, y, plane),
            RouteMode.BOSS_VERIFIED_INTERIOR,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putBossOpenWorld(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String... npcNames) {
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(x, y, plane),
            RouteMode.BOSS_OPEN_WORLD,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putBossTraversableInterior(
      Map<String, RouteProfile> profiles,
      String taskName,
      String location,
      int x,
      int y,
      int plane,
      String... npcNames) {
    putProfile(
        profiles,
        new RouteProfile(
            taskName,
            location,
            new WorldPoint(x, y, plane),
            RouteMode.BOSS_TRANSPORT_CHAIN_INTERIOR,
            npcAliases(taskName, npcNames),
            INTERIOR_TARGET_RADIUS,
            INTERIOR_ARRIVAL_RADIUS));
  }

  private static void putProfile(Map<String, RouteProfile> profiles, RouteProfile profile) {
    String key = profileKey(normalizeTaskKey(profile.taskName), normalize(profile.location));
    if (profiles.put(key, profile) != null) {
      throw new IllegalStateException(
          "Duplicate Slayer route profile: " + profile.taskName + " @ " + profile.location);
    }
  }

  private static Set<String> npcAliases(String taskName, String... npcNames) {
    Set<String> aliases = defaultNpcAliases(taskName);
    if (npcNames != null) {
      for (String npcName : npcNames) {
        String normalized = normalizeNpcName(npcName);
        if (!normalized.isEmpty()) {
          aliases.add(normalized);
        }
      }
    }
    return aliases;
  }

  private static Set<String> defaultNpcAliases(String taskName) {
    return new LinkedHashSet<>(SlayerTaskNpcCatalog.aliasesForStandardRoute(taskName));
  }

  private static String normalizeNpcName(String value) {
    return singularTaskName(normalize(value));
  }

  private static String normalizeTaskKey(String value) {
    return singularTaskName(normalize(value));
  }

  private static String singularTaskName(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    if (value.endsWith("wolves")) {
      return value.substring(0, value.length() - 6) + "wolf";
    }
    if (value.endsWith("elves")) {
      return value.substring(0, value.length() - 5) + "elf";
    }
    if (value.endsWith("dwarves")) {
      return value.substring(0, value.length() - 7) + "dwarf";
    }
    if (value.endsWith("men")) {
      return value.substring(0, value.length() - 3) + "man";
    }
    if (value.endsWith("ies") && value.length() > 4) {
      return value.substring(0, value.length() - 3) + "y";
    }
    if (value.endsWith("s")
        && !value.endsWith("ss")
        && !value.endsWith("us")
        && !value.endsWith("is")
        && value.length() > 3) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }

  private static String profileKey(String normalizedTask, String normalizedLocation) {
    return normalizedTask + "|" + normalizedLocation;
  }

  private static void putAccess(
      Map<String, WorldPoint> routes, String name, int x, int y, int plane) {
    String key = normalize(name);
    if (key.isEmpty()) {
      throw new IllegalStateException("Slayer access route has a blank name");
    }
    if (routes.put(key, new WorldPoint(x, y, plane)) != null) {
      throw new IllegalStateException("Duplicate Slayer access route: " + name);
    }
  }

  private static TransitionSpec transitionSpecFor(String location, WorldPoint approachPoint) {
    Set<String> names = new LinkedHashSet<>();
    Set<String> actions = new LinkedHashSet<>();
    String area = normalize(location);
    if (area.equals("catacombs of kourend")) {
      names.add("king rada");
      names.add("statue");
      actions.add("investigate");
      return new TransitionSpec(approachPoint, names, actions, 12);
    }
    if (area.equals("wilderness god wars dungeon")) {
      names.add("cave entrance");
      names.add("entrance");
      actions.add("enter");
      return new TransitionSpec(approachPoint, names, actions, 12);
    }
    if (area.equals("ynysdail cavern")) {
      names.add("rowboat");
      names.add("rowboat space");
      actions.add("travel");
      actions.add("row");
      actions.add("use");
      return new TransitionSpec(approachPoint, names, actions, 12);
    }
    if (area.equals("wyvern cave on fossil island")) {
      names.add("trapdoor");
      names.add("trap door");
      actions.add("climb down");
      return new TransitionSpec(approachPoint, names, actions, 10);
    }
    if (area.equals("digsite dungeon")) {
      names.add("winch");
      actions.add("operate");
      actions.add("use");
      actions.add("climb down");
      return new TransitionSpec(approachPoint, names, actions, 10);
    }
    if (area.equals("ruins of tapoyauik")) {
      names.add("lift platform");
      actions.add("use");
      actions.add("descend");
      return new TransitionSpec(approachPoint, names, actions, 12);
    }
    actions.add("enter");
    actions.add("climb down");
    actions.add("climb down stairs");
    actions.add("go through");
    actions.add("walk through");
    actions.add("pass");
    actions.add("descend");
    actions.add("investigate");
    actions.add("use");
    return new TransitionSpec(approachPoint, names, actions, 24);
  }

  private static boolean sameCoordinateLayer(WorldPoint first, WorldPoint second) {
    if (first == null || second == null) {
      return true;
    }
    return first.getPlane() == second.getPlane() && (first.getY() >>> 12) == (second.getY() >>> 12);
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
}
