package com.slayerplus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

public final class RoutePlan {
  public enum LegKind {
    TRAVEL,
    MANUAL_INTERACTION,
    DECISION,
    TERMINAL
  }

  public enum ProgressStatus {
    ROUTING,
    WAITING_FOR_INTERACTION,
    WAITING_FOR_BRANCH,
    ARRIVED
  }

  private final String id;
  private final String startLegId;
  private final Map<String, Leg> legs;
  private final ActivityRetention activityRetention;
  private final String terminalLegId;

  private RoutePlan(Builder builder) {
    id = requireText(builder.id, "Route plan id");
    startLegId = requireText(builder.startLegId, "Start leg id");
    legs = Collections.unmodifiableMap(new LinkedHashMap<>(builder.legs));
    activityRetention = Objects.requireNonNull(builder.activityRetention, "activityRetention");
    String terminal = "";
    for (Leg leg : legs.values()) {
      if (leg.getKind() == LegKind.TERMINAL) {
        terminal = leg.getId();
        break;
      }
    }
    terminalLegId = terminal;
  }

  public static Builder builder(String id) {
    return new Builder(id);
  }

  public String getId() {
    return id;
  }

  public String getStartLegId() {
    return startLegId;
  }

  public Map<String, Leg> getLegs() {
    return legs;
  }

  public Leg getLeg(String legId) {
    return legs.get(legId);
  }

  public ActivityRetention getActivityRetention() {
    return activityRetention;
  }

  public TerminalSpec getTerminalSpec() {
    return legs.get(terminalLegId).getTerminalSpec();
  }

  public Cursor initialCursor() {
    return new Cursor(id, startLegId);
  }

  public boolean hasArrived(RouteEvidence evidence) {
    return getTerminalSpec().matches(evidence);
  }

  public boolean isWithinActivityRetention(RouteEvidence evidence) {
    return activityRetention.contains(evidence);
  }

  public Evaluation evaluate(Cursor cursor, RouteEvidence evidence) {
    Objects.requireNonNull(cursor, "cursor");
    Objects.requireNonNull(evidence, "evidence");
    if (!id.equals(cursor.getPlanId())) {
      throw new IllegalArgumentException("Cursor belongs to a different route plan");
    }
    Leg current = legs.get(cursor.getActiveLegId());
    if (current == null) {
      throw new IllegalArgumentException("Cursor references an unknown route leg");
    }
    if (current.getKind() == LegKind.TERMINAL) {
      return new Evaluation(
          cursor,
          current,
          current.getTerminalSpec().matches(evidence)
              ? ProgressStatus.ARRIVED
              : ProgressStatus.ROUTING,
          false);
    }
    if (!current.getCompletionPredicate().matches(evidence)) {
      return new Evaluation(cursor, current, statusWhileIncomplete(current, evidence), false);
    }
    Branch selected = current.selectBranch(evidence);
    if (selected == null) {
      return new Evaluation(cursor, current, ProgressStatus.WAITING_FOR_BRANCH, false);
    }
    Leg next = legs.get(selected.getNextLegId());
    Cursor advanced = new Cursor(id, next.getId());
    return new Evaluation(advanced, next, statusOnEntry(next, evidence), true);
  }

  private static ProgressStatus statusWhileIncomplete(Leg leg, RouteEvidence evidence) {
    if (leg.getKind() == LegKind.MANUAL_INTERACTION
        && leg.getReadinessPredicate().matches(evidence)) {
      return ProgressStatus.WAITING_FOR_INTERACTION;
    }
    return ProgressStatus.ROUTING;
  }

  private static ProgressStatus statusOnEntry(Leg leg, RouteEvidence evidence) {
    if (leg.getKind() == LegKind.TERMINAL) {
      return leg.getTerminalSpec().matches(evidence)
          ? ProgressStatus.ARRIVED
          : ProgressStatus.ROUTING;
    }
    return statusWhileIncomplete(leg, evidence);
  }

  private static String requireText(String value, String label) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(label + " cannot be blank");
    }
    return trimmed;
  }

  public static final class RouteTarget {
    private final String label;
    private final List<WorldPoint> routingPoints;
    private final int approachRadius;

    private RouteTarget(String label, List<WorldPoint> routingPoints, int approachRadius) {
      this.label = requireText(label, "Route target label");
      if (approachRadius < 0) {
        throw new IllegalArgumentException("Route target radius cannot be negative");
      }
      List<WorldPoint> copy = new ArrayList<>();
      if (routingPoints != null) {
        for (WorldPoint routingPoint : routingPoints) {
          copy.add(Objects.requireNonNull(routingPoint, "routingPoint"));
        }
      }
      this.routingPoints = Collections.unmodifiableList(copy);
      this.approachRadius = approachRadius;
    }

    public static RouteTarget point(String label, WorldPoint routingPoint) {
      return point(label, routingPoint, 1);
    }

    public static RouteTarget point(String label, WorldPoint routingPoint, int approachRadius) {
      return points(
          label,
          Collections.singletonList(Objects.requireNonNull(routingPoint, "routingPoint")),
          approachRadius);
    }

    public static RouteTarget points(
        String label, List<WorldPoint> routingPoints, int approachRadius) {
      if (routingPoints == null || routingPoints.isEmpty()) {
        throw new IllegalArgumentException("A routed target needs at least one static point");
      }
      return new RouteTarget(label, routingPoints, approachRadius);
    }

    public static RouteTarget instruction(String label) {
      return new RouteTarget(label, Collections.emptyList(), 0);
    }

    public String getLabel() {
      return label;
    }

    public WorldPoint getRoutingPoint() {
      return routingPoints.isEmpty() ? null : routingPoints.get(0);
    }

    public List<WorldPoint> getRoutingPoints() {
      return routingPoints;
    }

    public int getApproachRadius() {
      return approachRadius;
    }

    public boolean hasRoutingPoint() {
      return !routingPoints.isEmpty();
    }
  }

  public static final class TerminalSpec {
    private final String label;
    private final RouteCheck completionPredicate;

    private TerminalSpec(String label, RouteCheck completionPredicate) {
      this.label = requireText(label, "Terminal label");
      this.completionPredicate = Objects.requireNonNull(completionPredicate, "completionPredicate");
      if (completionPredicate.containsKind(RouteCheck.Kind.ALWAYS)) {
        throw new IllegalArgumentException("A terminal needs explicit arrival evidence");
      }
    }

    public static TerminalSpec of(String label, RouteCheck completionPredicate) {
      return new TerminalSpec(label, completionPredicate);
    }

    public String getLabel() {
      return label;
    }

    public RouteCheck getCompletionPredicate() {
      return completionPredicate;
    }

    public boolean matches(RouteEvidence evidence) {
      return completionPredicate.matches(evidence);
    }
  }

  public static final class ActivityRetention {
    private final List<RouteCheck.SpatialArea> areas;

    private ActivityRetention(List<RouteCheck.SpatialArea> areas) {
      if (areas == null || areas.isEmpty()) {
        throw new IllegalArgumentException("Activity retention needs at least one explicit area");
      }
      List<RouteCheck.SpatialArea> copy = new ArrayList<>();
      for (RouteCheck.SpatialArea area : areas) {
        copy.add(Objects.requireNonNull(area, "retention area"));
      }
      this.areas = Collections.unmodifiableList(copy);
    }

    public static ActivityRetention of(RouteCheck.SpatialArea... areas) {
      return new ActivityRetention(
          areas == null ? Collections.emptyList() : java.util.Arrays.asList(areas));
    }

    public List<RouteCheck.SpatialArea> getAreas() {
      return areas;
    }

    public boolean contains(RouteEvidence evidence) {
      for (RouteCheck.SpatialArea area : areas) {
        if (area.contains(evidence)) {
          return true;
        }
      }
      return false;
    }
  }

  public static final class Branch {
    private final String label;
    private final String nextLegId;
    private final RouteCheck condition;

    private Branch(String label, String nextLegId, RouteCheck condition) {
      this.label = requireText(label, "Branch label");
      this.nextLegId = requireText(nextLegId, "Next leg id");
      this.condition = Objects.requireNonNull(condition, "condition");
    }

    public static Branch to(String nextLegId) {
      return new Branch(
          "Continue to " + requireText(nextLegId, "Next leg id"),
          nextLegId,
          RouteCheck.always());
    }

    public static Branch when(String label, RouteCheck condition, String nextLegId) {
      return new Branch(label, nextLegId, condition);
    }

    public String getLabel() {
      return label;
    }

    public String getNextLegId() {
      return nextLegId;
    }

    public RouteCheck getCondition() {
      return condition;
    }
  }

  public static final class Leg {
    private final String id;
    private final LegKind kind;
    private final String guidance;
    private final RouteTarget target;
    private final String interactionInstruction;
    private final RouteCheck readinessPredicate;
    private final RouteCheck completionPredicate;
    private final TerminalSpec terminalSpec;
    private final List<Branch> branches;

    private Leg(LegBuilder builder) {
      id = requireText(builder.id, "Leg id");
      kind = Objects.requireNonNull(builder.kind, "kind");
      guidance = requireText(builder.guidance, "Leg guidance");
      target = builder.target;
      interactionInstruction =
          builder.interactionInstruction == null ? "" : builder.interactionInstruction.trim();
      readinessPredicate = builder.readinessPredicate;
      completionPredicate = builder.completionPredicate;
      terminalSpec = builder.terminalSpec;
      branches = Collections.unmodifiableList(new ArrayList<>(builder.branches));
      validateLocal();
    }

    public static LegBuilder builder(String id, LegKind kind) {
      return new LegBuilder(id, kind);
    }

    private void validateLocal() {
      if (kind == LegKind.TERMINAL) {
        if (terminalSpec == null) {
          throw new IllegalArgumentException("A terminal leg needs a TerminalSpec");
        }
        if (completionPredicate != null || readinessPredicate != null || !branches.isEmpty()) {
          throw new IllegalArgumentException("Terminal completion belongs only in TerminalSpec");
        }
        return;
      }
      if (terminalSpec != null || completionPredicate == null) {
        throw new IllegalArgumentException("A non-terminal leg needs one completion predicate");
      }
      if (branches.isEmpty()) {
        throw new IllegalArgumentException("A non-terminal leg needs at least one branch");
      }
      if ((kind == LegKind.TRAVEL || kind == LegKind.MANUAL_INTERACTION) && target == null) {
        throw new IllegalArgumentException("Travel and manual legs need a guidance target");
      }
      if (kind == LegKind.MANUAL_INTERACTION) {
        if (interactionInstruction.isEmpty()) {
          throw new IllegalArgumentException("A manual leg needs an interaction instruction");
        }
        if (readinessPredicate == null
            || readinessPredicate.containsKind(RouteCheck.Kind.ALWAYS)) {
          throw new IllegalArgumentException("A manual leg needs explicit checkpoint readiness");
        }
        if (completionPredicate.canMatchObservedInteractionAlone()) {
          throw new IllegalArgumentException(
              "Manual completion cannot advance from a menu click alone");
        }
        boolean hasStrongCompletion =
            completionPredicate.containsKind(RouteCheck.Kind.COORDINATE_LAYER_TRANSITION)
                || completionPredicate.containsKind(
                    RouteCheck.Kind.VISIBLE_WIDGET_COMPONENT)
                || completionPredicate.containsKind(RouteCheck.Kind.VISIBLE_WIDGET_GROUP)
                || completionPredicate.containsKind(RouteCheck.Kind.EXACT_NPC)
                || completionPredicate.containsSpatialResultOutside(
                    target.getRoutingPoints(), target.getApproachRadius());
        if (!hasStrongCompletion) {
          throw new IllegalArgumentException(
              "Manual completion needs confirmed resultant evidence");
        }
      } else if (readinessPredicate != null || !interactionInstruction.isEmpty()) {
        throw new IllegalArgumentException("Only manual legs use readiness predicates");
      }
      if (kind == LegKind.DECISION) {
        if (completionPredicate.getKind() != RouteCheck.Kind.ALWAYS) {
          throw new IllegalArgumentException("A decision leg must be immediately selectable");
        }
      } else if (completionPredicate.containsKind(RouteCheck.Kind.ALWAYS)) {
        throw new IllegalArgumentException(
            "Travel and manual legs need explicit completion evidence");
      }
    }

    private Branch selectBranch(RouteEvidence evidence) {
      for (Branch branch : branches) {
        if (branch.getCondition().matches(evidence)) {
          return branch;
        }
      }
      return null;
    }

    public String getId() {
      return id;
    }

    public LegKind getKind() {
      return kind;
    }

    public String getGuidance() {
      return guidance;
    }

    public RouteTarget getTarget() {
      return target;
    }

    public String getInteractionInstruction() {
      return interactionInstruction;
    }

    public RouteCheck getReadinessPredicate() {
      return readinessPredicate;
    }

    public RouteCheck getCompletionPredicate() {
      return kind == LegKind.TERMINAL ? terminalSpec.getCompletionPredicate() : completionPredicate;
    }

    public TerminalSpec getTerminalSpec() {
      return terminalSpec;
    }

    public List<Branch> getBranches() {
      return branches;
    }
  }

  public static final class LegBuilder {
    private final String id;
    private final LegKind kind;
    private String guidance;
    private RouteTarget target;
    private String interactionInstruction;
    private RouteCheck readinessPredicate;
    private RouteCheck completionPredicate;
    private TerminalSpec terminalSpec;
    private final List<Branch> branches = new ArrayList<>();

    private LegBuilder(String id, LegKind kind) {
      this.id = id;
      this.kind = kind;
    }

    public LegBuilder guidance(String value) {
      guidance = value;
      return this;
    }

    public LegBuilder target(RouteTarget value) {
      target = value;
      return this;
    }

    public LegBuilder interactionInstruction(String value) {
      interactionInstruction = value;
      return this;
    }

    public LegBuilder readyWhen(RouteCheck value) {
      readinessPredicate = value;
      return this;
    }

    public LegBuilder completeWhen(RouteCheck value) {
      completionPredicate = value;
      return this;
    }

    public LegBuilder terminal(TerminalSpec value) {
      terminalSpec = value;
      return this;
    }

    public LegBuilder then(String nextLegId) {
      branches.add(Branch.to(nextLegId));
      return this;
    }

    public LegBuilder thenWhen(String label, RouteCheck condition, String nextLegId) {
      branches.add(Branch.when(label, condition, nextLegId));
      return this;
    }

    public Leg build() {
      return new Leg(this);
    }
  }

  public static final class Cursor {
    private final String planId;
    private final String activeLegId;

    private Cursor(String planId, String activeLegId) {
      this.planId = requireText(planId, "Cursor plan id");
      this.activeLegId = requireText(activeLegId, "Cursor active leg id");
    }

    public String getPlanId() {
      return planId;
    }

    public String getActiveLegId() {
      return activeLegId;
    }
  }

  public static final class Evaluation {
    private final Cursor cursor;
    private final Leg activeLeg;
    private final ProgressStatus status;
    private final boolean advanced;

    private Evaluation(Cursor cursor, Leg activeLeg, ProgressStatus status, boolean advanced) {
      this.cursor = cursor;
      this.activeLeg = activeLeg;
      this.status = status;
      this.advanced = advanced;
    }

    public Cursor getCursor() {
      return cursor;
    }

    public Leg getActiveLeg() {
      return activeLeg;
    }

    public ProgressStatus getStatus() {
      return status;
    }

    public boolean hasAdvanced() {
      return advanced;
    }

    public boolean hasArrived() {
      return status == ProgressStatus.ARRIVED;
    }
  }

  public static final class Builder {
    private final String id;
    private String startLegId;
    private final Map<String, Leg> legs = new LinkedHashMap<>();
    private ActivityRetention activityRetention;

    private Builder(String id) {
      this.id = id;
    }

    public Builder startAt(String value) {
      startLegId = value;
      return this;
    }

    public Builder activityRetention(ActivityRetention value) {
      activityRetention = value;
      return this;
    }

    public Builder addLeg(Leg leg) {
      Objects.requireNonNull(leg, "leg");
      if (legs.putIfAbsent(leg.getId(), leg) != null) {
        throw new IllegalArgumentException("Duplicate route leg id: " + leg.getId());
      }
      return this;
    }

    public List<String> validateGraph() {
      return Collections.unmodifiableList(validate(id, startLegId, legs, activityRetention));
    }

    public RoutePlan build() {
      List<String> errors = validateGraph();
      if (!errors.isEmpty()) {
        throw new IllegalStateException("Invalid Slayer route plan: " + String.join("; ", errors));
      }
      return new RoutePlan(this);
    }
  }

  private static List<String> validate(
      String id, String startLegId, Map<String, Leg> legs, ActivityRetention retention) {
    List<String> errors = new ArrayList<>();
    if (id == null || id.trim().isEmpty()) {
      errors.add("route id is blank");
    }
    if (startLegId == null || startLegId.trim().isEmpty()) {
      errors.add("start leg id is blank");
    } else if (!legs.containsKey(startLegId)) {
      errors.add("start leg does not exist: " + startLegId);
    }
    if (retention == null) {
      errors.add("activity retention is missing");
    }
    if (legs.isEmpty()) {
      errors.add("route graph is empty");
      return errors;
    }
    int terminalCount = 0;
    String terminalId = "";
    for (Leg leg : legs.values()) {
      if (leg.getKind() == LegKind.TERMINAL) {
        terminalCount++;
        terminalId = leg.getId();
      }
      boolean sawDefault = false;
      for (int index = 0; index < leg.getBranches().size(); index++) {
        Branch branch = leg.getBranches().get(index);
        if (!legs.containsKey(branch.getNextLegId())) {
          errors.add(leg.getId() + " targets missing leg " + branch.getNextLegId());
        }
        if (branch.getCondition().getKind() == RouteCheck.Kind.ALWAYS) {
          if (sawDefault) {
            errors.add(leg.getId() + " has multiple default branches");
          }
          sawDefault = true;
          if (index != leg.getBranches().size() - 1) {
            errors.add(leg.getId() + " has branches after its default");
          }
        }
      }
    }
    if (terminalCount != 1) {
      errors.add("route graph needs exactly one terminal; found " + terminalCount);
    }
    if (startLegId != null && legs.containsKey(startLegId)) {
      Set<String> reachable = reachableFrom(startLegId, legs);
      for (String legId : legs.keySet()) {
        if (!reachable.contains(legId)) {
          errors.add("unreachable route leg: " + legId);
        }
      }
    }
    Set<String> cycleMembers = cycleMembers(legs);
    if (!cycleMembers.isEmpty()) {
      errors.add("route graph contains a cycle: " + cycleMembers);
    } else if (terminalCount == 1) {
      Map<String, Boolean> memo = new HashMap<>();
      for (String legId : legs.keySet()) {
        if (!reachesTerminal(legId, terminalId, legs, memo)) {
          errors.add("route leg cannot reach terminal: " + legId);
        }
      }
    }
    return errors;
  }

  private static Set<String> reachableFrom(String startLegId, Map<String, Leg> legs) {
    Set<String> reachable = new LinkedHashSet<>();
    Deque<String> pending = new ArrayDeque<>();
    pending.push(startLegId);
    while (!pending.isEmpty()) {
      String current = pending.pop();
      if (!reachable.add(current)) {
        continue;
      }
      Leg leg = legs.get(current);
      if (leg == null) {
        continue;
      }
      for (Branch branch : leg.getBranches()) {
        if (legs.containsKey(branch.getNextLegId())) {
          pending.push(branch.getNextLegId());
        }
      }
    }
    return reachable;
  }

  private static Set<String> cycleMembers(Map<String, Leg> legs) {
    Map<String, Integer> states = new HashMap<>();
    Set<String> cycles = new LinkedHashSet<>();
    for (String legId : legs.keySet()) {
      detectCycles(legId, legs, states, new ArrayDeque<>(), cycles);
    }
    return cycles;
  }

  private static void detectCycles(
      String legId,
      Map<String, Leg> legs,
      Map<String, Integer> states,
      Deque<String> stack,
      Set<String> cycles) {
    int state = states.getOrDefault(legId, 0);
    if (state == 2) {
      return;
    }
    if (state == 1) {
      cycles.add(legId);
      for (String member : stack) {
        cycles.add(member);
        if (member.equals(legId)) {
          break;
        }
      }
      return;
    }
    states.put(legId, 1);
    stack.push(legId);
    Leg leg = legs.get(legId);
    if (leg != null) {
      for (Branch branch : leg.getBranches()) {
        if (legs.containsKey(branch.getNextLegId())) {
          detectCycles(branch.getNextLegId(), legs, states, stack, cycles);
        }
      }
    }
    stack.pop();
    states.put(legId, 2);
  }

  private static boolean reachesTerminal(
      String legId, String terminalId, Map<String, Leg> legs, Map<String, Boolean> memo) {
    if (legId.equals(terminalId)) {
      return true;
    }
    if (memo.containsKey(legId)) {
      return memo.get(legId);
    }
    Leg leg = legs.get(legId);
    if (leg == null || leg.getBranches().isEmpty()) {
      memo.put(legId, false);
      return false;
    }
    for (Branch branch : leg.getBranches()) {
      if (!reachesTerminal(branch.getNextLegId(), terminalId, legs, memo)) {
        memo.put(legId, false);
        return false;
      }
    }
    memo.put(legId, true);
    return true;
  }
}
