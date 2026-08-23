package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Independent release checklist; it must not be derived from implementation catalogs. */
final class SlayerRouteReleaseManifest {
  enum EntryKind { ASSIGNMENT, SELECTABLE_BOSS, DIRECT_BOSS }
  enum AreaType { OPEN_WORLD, CAVE, DUNGEON, MULTI_FLOOR, INSTANCE, WAVE, SPECIAL }
  enum TerminalContract {
    OPEN_WORLD_EXACT_TARGET,
    FULL_TRANSPORT_CHAIN_EXACT_INTERIOR,
    MANUAL_STAGE_EXACT_CHECKPOINT,
    INSTANCE_ENTRANCE_OR_WIDGET_STOP,
    INSTANCE_REGION_STOP,
    WAVE_ENTRANCE_OR_WIDGET_STOP
  }
  enum TerminalMechanism {
    STATIC_WORLD_POINT, EXACT_NPC_DISCOVERY, OBJECT_OR_WIDGET_STOP,
    REGION_ARRIVAL_STOP, SPECIAL_HANDOFF
  }

  static final class Endpoint {
    private final int x, y, plane;
    private Endpoint(int x, int y, int plane) { this.x = x; this.y = y; this.plane = plane; }
    int getX() { return x; }
    int getY() { return y; }
    int getPlane() { return plane; }
  }

  static final class Entry {
    private final EntryKind kind;
    private final String assignmentName, encounterName, location, unresolvedReason, wikiPageTitle;
    private final AreaType areaType;
    private final TerminalContract terminalContract;
    private final TerminalMechanism terminalMechanism;
    private final Endpoint expectedAccess, expectedTerminal;
    private final List<String> eligibleAssignments;

    private Entry(
        EntryKind kind, String assignmentName, String encounterName, String location,
        AreaType areaType, TerminalContract terminalContract, Endpoint expectedAccess,
        Endpoint expectedTerminal, String wikiPageTitle, String[] eligibleAssignments) {
      this.kind = kind;
      this.assignmentName = assignmentName;
      this.encounterName = encounterName;
      this.location = location;
      this.areaType = areaType;
      this.terminalContract = terminalContract;
      this.terminalMechanism = mechanismFor(terminalContract, expectedTerminal);
      this.expectedAccess = expectedAccess;
      this.expectedTerminal = expectedTerminal;
      this.unresolvedReason = unresolvedReasonFor(location, terminalContract, expectedTerminal);
      this.wikiPageTitle = wikiPageTitle;
      this.eligibleAssignments = Collections.unmodifiableList(
          new ArrayList<>(Arrays.asList(eligibleAssignments)));
    }

    EntryKind getKind() { return kind; }
    String getAssignmentName() { return assignmentName; }
    String getEncounterName() { return encounterName; }
    String getLocation() { return location; }
    AreaType getAreaType() { return areaType; }
    TerminalContract getTerminalContract() { return terminalContract; }
    TerminalMechanism getTerminalMechanism() { return terminalMechanism; }
    Endpoint getExpectedAccess() { return expectedAccess; }
    Endpoint getExpectedTerminal() { return expectedTerminal; }
    boolean hasEstablishedTerminal() { return expectedTerminal != null; }
    String getUnresolvedReason() { return unresolvedReason; }
    boolean isReleaseReady() { return expectedTerminal != null && unresolvedReason.isEmpty(); }
    String getWikiPageTitle() { return wikiPageTitle; }
    List<String> getEligibleAssignments() { return eligibleAssignments; }
    boolean requiresExactAuthoredProfile() { return true; }
  }

  private static final ManifestData DATA = load();
  private SlayerRouteReleaseManifest() {}

  static List<Entry> entries() { return DATA.entries; }
  static List<Entry> entriesByKind(EntryKind kind) {
    List<Entry> matches = new ArrayList<>();
    for (Entry entry : DATA.entries) if (entry.kind == kind) matches.add(entry);
    return Collections.unmodifiableList(matches);
  }
  static List<Entry> unresolvedTerminalEntries() {
    List<Entry> unresolved = new ArrayList<>();
    for (Entry entry : DATA.entries) if (!entry.isReleaseReady()) unresolved.add(entry);
    return Collections.unmodifiableList(unresolved);
  }
  static Set<String> canonicalAssignmentNames() { return DATA.names.get(EntryKind.ASSIGNMENT); }
  static Set<String> canonicalSelectableBossNames() {
    return DATA.names.get(EntryKind.SELECTABLE_BOSS);
  }
  static Set<String> canonicalDirectBossNames() { return DATA.names.get(EntryKind.DIRECT_BOSS); }
  static int size() { return DATA.entries.size(); }

  private static ManifestData load() {
    EnumMap<EntryKind, Set<String>> names = new EnumMap<>(EntryKind.class);
    for (EntryKind kind : EntryKind.values()) names.put(kind, new LinkedHashSet<>());
    List<Entry> entries = new ArrayList<>();
    for (String[] row : ResourceTable.decodedRows("slayer-route-release-manifest.tsv", 3, 15)) {
      EntryKind kind = EntryKind.valueOf(row[1]);
      if (row[0].equals("N")) {
        if (!names.get(kind).add(row[2]))
          throw new IllegalStateException("Duplicate canonical manifest name: " + row[2]);
      } else if (row[0].equals("E")) {
        entries.add(new Entry(
            kind, row[2], row[3], row[4], AreaType.valueOf(row[5]),
            TerminalContract.valueOf(row[6]), endpoint(row, 7, false), endpoint(row, 10, true),
            row[13], row[14].split("\\|", -1)));
      } else {
        throw new IllegalStateException("Unknown manifest record: " + row[0]);
      }
    }
    EnumMap<EntryKind, Set<String>> frozen = new EnumMap<>(EntryKind.class);
    for (EntryKind kind : EntryKind.values())
      frozen.put(kind, Collections.unmodifiableSet(names.get(kind)));
    validate(entries, frozen);
    return new ManifestData(Collections.unmodifiableList(entries), Collections.unmodifiableMap(frozen));
  }

  private static Endpoint endpoint(String[] row, int offset, boolean nullable) {
    if (nullable && row[offset].isEmpty() && row[offset + 1].isEmpty()
        && row[offset + 2].isEmpty()) return null;
    return new Endpoint(Integer.parseInt(row[offset]), Integer.parseInt(row[offset + 1]),
        Integer.parseInt(row[offset + 2]));
  }

  private static TerminalMechanism mechanismFor(
      TerminalContract terminalContract, Endpoint expectedTerminal) {
    switch (terminalContract) {
      case OPEN_WORLD_EXACT_TARGET: return TerminalMechanism.STATIC_WORLD_POINT;
      case FULL_TRANSPORT_CHAIN_EXACT_INTERIOR:
        return expectedTerminal == null ? TerminalMechanism.EXACT_NPC_DISCOVERY
            : TerminalMechanism.STATIC_WORLD_POINT;
      case INSTANCE_ENTRANCE_OR_WIDGET_STOP:
      case WAVE_ENTRANCE_OR_WIDGET_STOP: return TerminalMechanism.OBJECT_OR_WIDGET_STOP;
      case INSTANCE_REGION_STOP: return TerminalMechanism.REGION_ARRIVAL_STOP;
      case MANUAL_STAGE_EXACT_CHECKPOINT: return TerminalMechanism.SPECIAL_HANDOFF;
      default: throw new IllegalStateException("Unhandled terminal contract: " + terminalContract);
    }
  }

  private static String unresolvedReasonFor(
      String location, TerminalContract contract, Endpoint terminal) {
    if (terminal != null) return "";
    switch (contract) {
      case OPEN_WORLD_EXACT_TARGET:
        return "Authoritative open-world target WorldPoint is UNKNOWN for " + location;
      case FULL_TRANSPORT_CHAIN_EXACT_INTERIOR:
        return "Authoritative interior terminal WorldPoint is UNKNOWN for " + location;
      case MANUAL_STAGE_EXACT_CHECKPOINT:
        return "Authoritative manual interaction checkpoint WorldPoint is UNKNOWN for " + location;
      case INSTANCE_ENTRANCE_OR_WIDGET_STOP:
        return "Authoritative instance entrance object/widget WorldPoint is UNKNOWN for " + location;
      case INSTANCE_REGION_STOP:
        return "Authoritative instance arrival/stop region WorldPoint is UNKNOWN for " + location;
      case WAVE_ENTRANCE_OR_WIDGET_STOP:
        return "Authoritative wave entrance NPC/widget WorldPoint is UNKNOWN for " + location;
      default: throw new IllegalStateException("Unhandled terminal contract: " + contract);
    }
  }

  private static void validate(List<Entry> entries, EnumMap<EntryKind, Set<String>> expected) {
    Set<String> keys = new LinkedHashSet<>();
    EnumMap<EntryKind, Set<String>> observed = new EnumMap<>(EntryKind.class);
    for (EntryKind kind : EntryKind.values()) observed.put(kind, new LinkedHashSet<>());
    for (Entry entry : entries) {
      if (entry.encounterName.isEmpty() || entry.location.isEmpty() || entry.areaType == null
          || entry.terminalContract == null || entry.terminalMechanism == null)
        throw new IllegalStateException("Incomplete Slayer route release manifest row");
      if ((entry.expectedTerminal == null) == entry.unresolvedReason.trim().isEmpty())
        throw new IllegalStateException("Manifest row must establish a terminal or remain unresolved: "
            + entry.encounterName + " at " + entry.location);
      String key = entry.kind + "|" + entry.assignmentName + "|" + entry.encounterName + "|"
          + entry.location;
      if (!keys.add(key)) throw new IllegalStateException("Duplicate manifest row: " + key);
      observed.get(entry.kind).add(entry.kind == EntryKind.ASSIGNMENT
          ? entry.assignmentName : entry.encounterName);
    }
    for (EntryKind kind : EntryKind.values()) {
      if (expected.get(kind).equals(observed.get(kind))) continue;
      Set<String> missing = new LinkedHashSet<>(expected.get(kind));
      missing.removeAll(observed.get(kind));
      Set<String> unexpected = new LinkedHashSet<>(observed.get(kind));
      unexpected.removeAll(expected.get(kind));
      throw new IllegalStateException("Manifest " + kind + " parity failure; missing=" + missing
          + ", unexpected=" + unexpected);
    }
  }

  private static final class ManifestData {
    private final List<Entry> entries;
    private final Map<EntryKind, Set<String>> names;
    private ManifestData(List<Entry> entries, Map<EntryKind, Set<String>> names) {
      this.entries = entries;
      this.names = names;
    }
  }
}
