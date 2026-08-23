package com.slayerplus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

class SlayerRoutePlanResource {
  private SlayerRoutePlanResource() {}

  static Map<SlayerRoutePlanCatalog.RouteKey, RoutePlan> load() {
    Map<String, PlanData> plans = new LinkedHashMap<>();
    List<KeyData> keys = new ArrayList<>();
    List<String[]> rows = ResourceTable.rows("slayer-route-plans.tsv");
    for (int row = 0; row < rows.size(); row++) {
      String[] raw = rows.get(row);
      String[] fields = new String[raw.length];
      for (int i = 0; i < raw.length; i++) {
        fields[i] = decode(raw[i]);
      }
      readRecord(fields, row + 1, plans, keys);
    }
    Map<String, RoutePlan> built = new LinkedHashMap<>();
    for (PlanData data : plans.values()) {
      built.put(data.id, data.build());
    }
    Map<SlayerRoutePlanCatalog.RouteKey, RoutePlan> result = new LinkedHashMap<>();
    for (KeyData key : keys) {
      RoutePlan plan = built.get(key.planId);
      if (plan == null || result.putIfAbsent(key.key(), plan) != null) {
        throw new IllegalStateException("Invalid or duplicate route key " + key);
      }
    }
    if (result.isEmpty()) {
      throw new IllegalStateException("Route-plan resource is empty");
    }
    return Collections.unmodifiableMap(result);
  }

  private static void readRecord(
      String[] f, int line, Map<String, PlanData> plans, List<KeyData> keys) {
    try {
      switch (f[0]) {
        case "K":
          require(f, 5);
          keys.add(new KeyData(f[1], f[2], Boolean.parseBoolean(f[3]), f[4]));
          break;
        case "P":
          require(f, 3);
          if (plans.putIfAbsent(f[1], new PlanData(f[1], f[2])) != null) {
            throw new IllegalArgumentException("duplicate plan " + f[1]);
          }
          break;
        case "R":
          require(f, 3);
          plan(plans, f[1]).retention.add(parseArea(f[2]));
          break;
        case "L":
          require(f, 13);
          plan(plans, f[1]).addLeg(new LegData(f));
          break;
        case "B":
          require(f, 6);
          plan(plans, f[1])
              .leg(f[2])
              .branches
              .add(new BranchData(f[3], f[4], parsePredicate(f[5])));
          break;
        default:
          throw new IllegalArgumentException("unknown record " + f[0]);
      }
    } catch (RuntimeException ex) {
      throw new IllegalStateException("Invalid route-plan resource line " + line, ex);
    }
  }

  private static PlanData plan(Map<String, PlanData> plans, String id) {
    PlanData result = plans.get(id);
    if (result == null) {
      throw new IllegalArgumentException("unknown plan " + id);
    }
    return result;
  }

  private static void require(String[] fields, int count) {
    if (fields.length != count) {
      throw new IllegalArgumentException("expected " + count + " fields, got " + fields.length);
    }
  }

  private static RouteCheck parsePredicate(String expression) {
    if (expression == null || expression.isEmpty()) {
      return null;
    }
    if (expression.equals("a")) {
      return RouteCheck.always();
    }
    if (expression.length() < 3
        || expression.charAt(1) != '('
        || expression.charAt(expression.length() - 1) != ')') {
      throw new IllegalArgumentException("bad predicate " + expression);
    }
    char kind = expression.charAt(0);
    String body = expression.substring(2, expression.length() - 1);
    switch (kind) {
      case 'w':
        return RouteCheck.worldArea(parseArea(body));
      case 't':
        return RouteCheck.templateArea(parseArea(body));
      case 'c':
        List<String> layers = splitTopLevel(body, '>');
        if (layers.size() != 2) {
          throw new IllegalArgumentException("bad layer transition");
        }
        return RouteCheck.coordinateLayerTransition(
            parseLayer(layers.get(0)), parseLayer(layers.get(1)));
      case 'o':
        return RouteCheck.trackedObjectAction(parseObject(body));
      case 'i':
        return RouteCheck.observedObjectInteraction(parseObject(body));
      case 'v':
        return RouteCheck.visibleWidgetComponent(Integer.parseInt(body));
      case 'g':
        return RouteCheck.visibleWidgetGroup(Integer.parseInt(body));
      case 'n':
        return RouteCheck.exactNpc(parseStrings(body));
      case 'y':
        return RouteCheck.anyOf(parseChildren(body));
      case 'l':
        return RouteCheck.allOf(parseChildren(body));
      default:
        throw new IllegalArgumentException("unknown predicate " + kind);
    }
  }

  private static List<RouteCheck> parseChildren(String body) {
    List<RouteCheck> result = new ArrayList<>();
    for (String child : splitTopLevel(body, ';')) {
      result.add(parsePredicate(child));
    }
    return result;
  }

  private static List<String> splitTopLevel(String value, char delimiter) {
    List<String> result = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '(') depth++;
      else if (c == ')') depth--;
      else if (c == delimiter && depth == 0) {
        result.add(value.substring(start, i));
        start = i + 1;
      }
    }
    result.add(value.substring(start));
    return result;
  }

  private static RouteCheck.SpatialArea parseArea(String value) {
    String[] p = value.split(",", -1);
    if (p.length != 6) {
      throw new IllegalArgumentException("bad area " + value);
    }
    int minX = Integer.parseInt(p[1]);
    int maxX = Integer.parseInt(p[2]);
    int minY = Integer.parseInt(p[3]);
    int maxY = Integer.parseInt(p[4]);
    int plane = Integer.parseInt(p[5]);
    return p[0].equals("W")
        ? RouteCheck.SpatialArea.world(minX, maxX, minY, maxY, plane)
        : RouteCheck.SpatialArea.template(minX, maxX, minY, maxY, plane);
  }

  private static RouteCheck.CoordinateLayer parseLayer(String value) {
    String[] p = value.split(",", -1);
    if (p.length != 3) {
      throw new IllegalArgumentException("bad layer " + value);
    }
    int[] regions = ints(p[2], '~');
    return p[0].equals("W")
        ? RouteCheck.CoordinateLayer.worldRegions(Integer.parseInt(p[1]), regions)
        : RouteCheck.CoordinateLayer.templateRegions(Integer.parseInt(p[1]), regions);
  }

  private static RouteCheck.ObjectActionSpec parseObject(String value) {
    String[] p = value.split("\\|", -1);
    if (p.length != 3) {
      throw new IllegalArgumentException("bad object action " + value);
    }
    Set<Integer> ids = new LinkedHashSet<>();
    for (int id : ints(p[0], '~')) {
      ids.add(id);
    }
    return RouteCheck.ObjectActionSpec.reviewed(
        ids, parseStrings(p[1]), parseStrings(p[2]));
  }

  private static int[] ints(String value, char delimiter) {
    if (value.isEmpty()) {
      return new int[0];
    }
    String[] values = value.split(java.util.regex.Pattern.quote(Character.toString(delimiter)));
    int[] result = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = Integer.parseInt(values[i]);
    }
    return result;
  }

  private static Collection<String> parseStrings(String value) {
    if (value.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (String part : value.split("~", -1)) {
      result.add(decode(part));
    }
    return result;
  }

  private static List<WorldPoint> parsePoints(String value) {
    if (value.isEmpty()) {
      return Collections.emptyList();
    }
    List<WorldPoint> result = new ArrayList<>();
    for (String point : value.split(";", -1)) {
      int[] values = ints(point, ',');
      if (values.length != 3) {
        throw new IllegalArgumentException("bad point " + point);
      }
      result.add(new WorldPoint(values[0], values[1], values[2]));
    }
    return result;
  }

  private static String decode(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) == '%' && i + 2 < value.length()) {
        int hi = Character.digit(value.charAt(i + 1), 16);
        int lo = Character.digit(value.charAt(i + 2), 16);
        if (hi >= 0 && lo >= 0) {
          out.append((char) ((hi << 4) | lo));
          i += 2;
          continue;
        }
      }
      out.append(value.charAt(i));
    }
    return out.toString();
  }

  private static final class KeyData {
    private final String task;
    private final String location;
    private final boolean boss;
    private final String planId;

    private KeyData(String task, String location, boolean boss, String planId) {
      this.task = task;
      this.location = location;
      this.boss = boss;
      this.planId = planId;
    }

    private SlayerRoutePlanCatalog.RouteKey key() {
      return SlayerRoutePlanCatalog.RouteKey.of(task, location, boss);
    }

    @Override
    public String toString() {
      return task + "|" + location + "|" + boss;
    }
  }

  private static final class PlanData {
    private final String id;
    private final String start;
    private final List<RouteCheck.SpatialArea> retention = new ArrayList<>();
    private final Map<String, LegData> legs = new LinkedHashMap<>();

    private PlanData(String id, String start) {
      this.id = id;
      this.start = start;
    }

    private void addLeg(LegData leg) {
      if (legs.putIfAbsent(leg.id, leg) != null) {
        throw new IllegalArgumentException("duplicate leg " + leg.id);
      }
    }

    private LegData leg(String legId) {
      LegData result = legs.get(legId);
      if (result == null) {
        throw new IllegalArgumentException("unknown leg " + legId);
      }
      return result;
    }

    private RoutePlan build() {
      RoutePlan.Builder builder =
          RoutePlan.builder(id)
              .startAt(start)
              .activityRetention(
                  RoutePlan.ActivityRetention.of(
                      retention.toArray(new RouteCheck.SpatialArea[0])));
      for (LegData leg : legs.values()) {
        builder.addLeg(leg.build());
      }
      return builder.build();
    }
  }

  private static final class LegData {
    private final String id;
    private final RoutePlan.LegKind kind;
    private final String guidance;
    private final String targetLabel;
    private final int radius;
    private final List<WorldPoint> points;
    private final String instruction;
    private final RouteCheck readiness;
    private final RouteCheck completion;
    private final String terminalLabel;
    private final RouteCheck terminal;
    private final List<BranchData> branches = new ArrayList<>();

    private LegData(String[] f) {
      id = f[2];
      kind = RoutePlan.LegKind.valueOf(f[3]);
      guidance = f[4];
      targetLabel = f[5];
      radius = Integer.parseInt(f[6]);
      points = parsePoints(f[7]);
      instruction = f[8];
      readiness = parsePredicate(f[9]);
      completion = parsePredicate(f[10]);
      terminalLabel = f[11];
      terminal = parsePredicate(f[12]);
    }

    private RoutePlan.Leg build() {
      RoutePlan.LegBuilder builder = RoutePlan.Leg.builder(id, kind).guidance(guidance);
      if (!targetLabel.isEmpty()) {
        builder.target(
            points.isEmpty()
                ? RoutePlan.RouteTarget.instruction(targetLabel)
                : RoutePlan.RouteTarget.points(targetLabel, points, radius));
      }
      if (kind == RoutePlan.LegKind.TERMINAL) {
        builder.terminal(RoutePlan.TerminalSpec.of(terminalLabel, terminal));
      } else {
        builder.completeWhen(completion);
        if (kind == RoutePlan.LegKind.MANUAL_INTERACTION) {
          builder.interactionInstruction(instruction).readyWhen(readiness);
        }
        for (BranchData branch : branches) {
          builder.thenWhen(branch.label, branch.condition, branch.next);
        }
      }
      return builder.build();
    }
  }

  private static final class BranchData {
    private final String label;
    private final String next;
    private final RouteCheck condition;

    private BranchData(String label, String next, RouteCheck condition) {
      this.label = label;
      this.next = next;
      this.condition = condition;
    }
  }
}
