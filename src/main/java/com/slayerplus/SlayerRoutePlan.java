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

/**
 * Immutable ordered/branched route graph with strict terminal evidence.
 *
 * <p>A broad activity-retention area and a narrow arrival contract are
 * deliberately different types. Retention can prevent a route from restarting
 * while a player banks, fights, or moves around an encounter; it can never mark
 * the route complete.</p>
 *
 * <p>Evaluation is pure. It advances at most one graph edge for each evidence
 * snapshot, so one observed interaction cannot accidentally complete multiple
 * manual transitions. The caller persists the returned immutable cursor.</p>
 */
public final class SlayerRoutePlan
{
	public enum LegKind
	{
		TRAVEL,
		MANUAL_INTERACTION,
		DECISION,
		TERMINAL
	}

	public enum ProgressStatus
	{
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

	private SlayerRoutePlan(final Builder builder)
	{
		id = requireText(builder.id, "Route plan id");
		startLegId = requireText(builder.startLegId, "Start leg id");
		legs = Collections.unmodifiableMap(
			new LinkedHashMap<>(builder.legs)
		);
		activityRetention = Objects.requireNonNull(
			builder.activityRetention,
			"activityRetention"
		);
		String terminal = "";
		for (final Leg leg : legs.values())
		{
			if (leg.getKind() == LegKind.TERMINAL)
			{
				terminal = leg.getId();
				break;
			}
		}
		terminalLegId = terminal;
	}

	public static Builder builder(final String id)
	{
		return new Builder(id);
	}

	public String getId()
	{
		return id;
	}

	public String getStartLegId()
	{
		return startLegId;
	}

	public Map<String, Leg> getLegs()
	{
		return legs;
	}

	public Leg getLeg(final String legId)
	{
		return legs.get(legId);
	}

	public ActivityRetention getActivityRetention()
	{
		return activityRetention;
	}

	public TerminalSpec getTerminalSpec()
	{
		return legs.get(terminalLegId).getTerminalSpec();
	}

	public Cursor initialCursor()
	{
		return new Cursor(id, startLegId);
	}

	/** Strict terminal proof; broad retention geometry is never consulted. */
	public boolean hasArrived(final SlayerRouteEvidence evidence)
	{
		return getTerminalSpec().matches(evidence);
	}

	/** Broad activity geometry; this method can never complete a route. */
	public boolean isWithinActivityRetention(
		final SlayerRouteEvidence evidence)
	{
		return activityRetention.contains(evidence);
	}

	/**
	 * Advances at most one graph edge from {@code cursor} using one immutable
	 * evidence snapshot.
	 */
	public Evaluation evaluate(
		final Cursor cursor,
		final SlayerRouteEvidence evidence)
	{
		Objects.requireNonNull(cursor, "cursor");
		Objects.requireNonNull(evidence, "evidence");
		if (!id.equals(cursor.getPlanId()))
		{
			throw new IllegalArgumentException(
				"Cursor belongs to a different route plan"
			);
		}
		final Leg current = legs.get(cursor.getActiveLegId());
		if (current == null)
		{
			throw new IllegalArgumentException(
				"Cursor references an unknown route leg"
			);
		}

		if (current.getKind() == LegKind.TERMINAL)
		{
			return new Evaluation(
				cursor,
				current,
				current.getTerminalSpec().matches(evidence)
					? ProgressStatus.ARRIVED
					: ProgressStatus.ROUTING,
				false
			);
		}

		if (!current.getCompletionPredicate().matches(evidence))
		{
			return new Evaluation(
				cursor,
				current,
				statusWhileIncomplete(current, evidence),
				false
			);
		}

		final Branch selected = current.selectBranch(evidence);
		if (selected == null)
		{
			return new Evaluation(
				cursor,
				current,
				ProgressStatus.WAITING_FOR_BRANCH,
				false
			);
		}

		final Leg next = legs.get(selected.getNextLegId());
		final Cursor advanced = new Cursor(id, next.getId());
		return new Evaluation(
			advanced,
			next,
			statusOnEntry(next, evidence),
			true
		);
	}

	private static ProgressStatus statusWhileIncomplete(
		final Leg leg,
		final SlayerRouteEvidence evidence)
	{
		if (leg.getKind() == LegKind.MANUAL_INTERACTION
			&& leg.getReadinessPredicate().matches(evidence))
		{
			return ProgressStatus.WAITING_FOR_INTERACTION;
		}
		return ProgressStatus.ROUTING;
	}

	private static ProgressStatus statusOnEntry(
		final Leg leg,
		final SlayerRouteEvidence evidence)
	{
		if (leg.getKind() == LegKind.TERMINAL)
		{
			return leg.getTerminalSpec().matches(evidence)
				? ProgressStatus.ARRIVED
				: ProgressStatus.ROUTING;
		}
		return statusWhileIncomplete(leg, evidence);
	}

	private static String requireText(final String value, final String label)
	{
		final String trimmed = value == null ? "" : value.trim();
		if (trimmed.isEmpty())
		{
			throw new IllegalArgumentException(label + " cannot be blank");
		}
		return trimmed;
	}

	/** Human-readable target for Shortest Path or manual transition guidance. */
	public static final class RouteTarget
	{
		private final String label;
		private final List<WorldPoint> routingPoints;
		private final int approachRadius;

		private RouteTarget(
			final String label,
			final List<WorldPoint> routingPoints,
			final int approachRadius)
		{
			this.label = requireText(label, "Route target label");
			if (approachRadius < 0)
			{
				throw new IllegalArgumentException(
					"Route target radius cannot be negative"
				);
			}
			final List<WorldPoint> copy = new ArrayList<>();
			if (routingPoints != null)
			{
				for (final WorldPoint routingPoint : routingPoints)
				{
					copy.add(Objects.requireNonNull(
						routingPoint, "routingPoint"
					));
				}
			}
			this.routingPoints = Collections.unmodifiableList(copy);
			this.approachRadius = approachRadius;
		}

		public static RouteTarget point(
			final String label,
			final WorldPoint routingPoint)
		{
			return point(label, routingPoint, 1);
		}

		public static RouteTarget point(
			final String label,
			final WorldPoint routingPoint,
			final int approachRadius)
		{
			return points(
				label,
				Collections.singletonList(
					Objects.requireNonNull(routingPoint, "routingPoint")
				),
				approachRadius
			);
		}

		public static RouteTarget points(
			final String label,
			final List<WorldPoint> routingPoints,
			final int approachRadius)
		{
			if (routingPoints == null || routingPoints.isEmpty())
			{
				throw new IllegalArgumentException(
					"A routed target needs at least one static point"
				);
			}
			return new RouteTarget(label, routingPoints, approachRadius);
		}

		public static RouteTarget instruction(final String label)
		{
			return new RouteTarget(label, Collections.emptyList(), 0);
		}

		public String getLabel()
		{
			return label;
		}

		public WorldPoint getRoutingPoint()
		{
			return routingPoints.isEmpty() ? null : routingPoints.get(0);
		}

		public List<WorldPoint> getRoutingPoints()
		{
			return routingPoints;
		}

		/**
		 * Radius for approach/highlight presentation only. Completion still comes
		 * exclusively from the leg predicate.
		 */
		public int getApproachRadius()
		{
			return approachRadius;
		}

		public boolean hasRoutingPoint()
		{
			return !routingPoints.isEmpty();
		}
	}

	/** Strict final-arrival contract, independent from activity retention. */
	public static final class TerminalSpec
	{
		private final String label;
		private final SlayerRoutePredicate completionPredicate;

		private TerminalSpec(
			final String label,
			final SlayerRoutePredicate completionPredicate)
		{
			this.label = requireText(label, "Terminal label");
			this.completionPredicate = Objects.requireNonNull(
				completionPredicate,
				"completionPredicate"
			);
			if (completionPredicate.containsKind(
				SlayerRoutePredicate.Kind.ALWAYS))
			{
				throw new IllegalArgumentException(
					"A terminal needs explicit arrival evidence"
				);
			}
		}

		public static TerminalSpec of(
			final String label,
			final SlayerRoutePredicate completionPredicate)
		{
			return new TerminalSpec(label, completionPredicate);
		}

		public String getLabel()
		{
			return label;
		}

		public SlayerRoutePredicate getCompletionPredicate()
		{
			return completionPredicate;
		}

		public boolean matches(final SlayerRouteEvidence evidence)
		{
			return completionPredicate.matches(evidence);
		}
	}

	/** Broad spatial retention only; it is structurally unable to be terminal. */
	public static final class ActivityRetention
	{
		private final List<SlayerRoutePredicate.SpatialArea> areas;

		private ActivityRetention(
			final List<SlayerRoutePredicate.SpatialArea> areas)
		{
			if (areas == null || areas.isEmpty())
			{
				throw new IllegalArgumentException(
					"Activity retention needs at least one explicit area"
				);
			}
			final List<SlayerRoutePredicate.SpatialArea> copy =
				new ArrayList<>();
			for (final SlayerRoutePredicate.SpatialArea area : areas)
			{
				copy.add(Objects.requireNonNull(area, "retention area"));
			}
			this.areas = Collections.unmodifiableList(copy);
		}

		public static ActivityRetention of(
			final SlayerRoutePredicate.SpatialArea... areas)
		{
			return new ActivityRetention(
				areas == null ? Collections.emptyList() :
					java.util.Arrays.asList(areas)
			);
		}

		public List<SlayerRoutePredicate.SpatialArea> getAreas()
		{
			return areas;
		}

		public boolean contains(final SlayerRouteEvidence evidence)
		{
			for (final SlayerRoutePredicate.SpatialArea area : areas)
			{
				if (area.contains(evidence))
				{
					return true;
				}
			}
			return false;
		}
	}

	/** Ordered branch; the first matching branch is selected. */
	public static final class Branch
	{
		private final String label;
		private final String nextLegId;
		private final SlayerRoutePredicate condition;

		private Branch(
			final String label,
			final String nextLegId,
			final SlayerRoutePredicate condition)
		{
			this.label = requireText(label, "Branch label");
			this.nextLegId = requireText(nextLegId, "Next leg id");
			this.condition = Objects.requireNonNull(condition, "condition");
		}

		public static Branch to(final String nextLegId)
		{
			return new Branch(
				"Continue to " + requireText(nextLegId, "Next leg id"),
				nextLegId,
				SlayerRoutePredicate.always()
			);
		}

		public static Branch when(
			final String label,
			final SlayerRoutePredicate condition,
			final String nextLegId)
		{
			return new Branch(label, nextLegId, condition);
		}

		public String getLabel()
		{
			return label;
		}

		public String getNextLegId()
		{
			return nextLegId;
		}

		public SlayerRoutePredicate getCondition()
		{
			return condition;
		}
	}

	public static final class Leg
	{
		private final String id;
		private final LegKind kind;
		private final String guidance;
		private final RouteTarget target;
		private final String interactionInstruction;
		private final SlayerRoutePredicate readinessPredicate;
		private final SlayerRoutePredicate completionPredicate;
		private final TerminalSpec terminalSpec;
		private final List<Branch> branches;

		private Leg(final LegBuilder builder)
		{
			id = requireText(builder.id, "Leg id");
			kind = Objects.requireNonNull(builder.kind, "kind");
			guidance = requireText(builder.guidance, "Leg guidance");
			target = builder.target;
			interactionInstruction = builder.interactionInstruction == null
				? ""
				: builder.interactionInstruction.trim();
			readinessPredicate = builder.readinessPredicate;
			completionPredicate = builder.completionPredicate;
			terminalSpec = builder.terminalSpec;
			branches = Collections.unmodifiableList(
				new ArrayList<>(builder.branches)
			);
			validateLocal();
		}

		public static LegBuilder builder(
			final String id,
			final LegKind kind)
		{
			return new LegBuilder(id, kind);
		}

		private void validateLocal()
		{
			if (kind == LegKind.TERMINAL)
			{
				if (terminalSpec == null)
				{
					throw new IllegalArgumentException(
						"A terminal leg needs a TerminalSpec"
					);
				}
				if (completionPredicate != null || readinessPredicate != null
					|| !branches.isEmpty())
				{
					throw new IllegalArgumentException(
						"Terminal completion belongs only in TerminalSpec"
					);
				}
				return;
			}

			if (terminalSpec != null || completionPredicate == null)
			{
				throw new IllegalArgumentException(
					"A non-terminal leg needs one completion predicate"
				);
			}
			if (branches.isEmpty())
			{
				throw new IllegalArgumentException(
					"A non-terminal leg needs at least one branch"
				);
			}
			if ((kind == LegKind.TRAVEL || kind == LegKind.MANUAL_INTERACTION)
				&& target == null)
			{
				throw new IllegalArgumentException(
					"Travel and manual legs need a guidance target"
				);
			}
			if (kind == LegKind.MANUAL_INTERACTION)
			{
				if (interactionInstruction.isEmpty())
				{
					throw new IllegalArgumentException(
						"A manual leg needs an interaction instruction"
					);
				}
				if (readinessPredicate == null
					|| readinessPredicate.containsKind(
						SlayerRoutePredicate.Kind.ALWAYS))
				{
					throw new IllegalArgumentException(
						"A manual leg needs explicit checkpoint readiness"
					);
				}
				if (completionPredicate.canMatchObservedInteractionAlone())
				{
					throw new IllegalArgumentException(
						"Manual completion cannot advance from a menu click alone"
					);
				}
				final boolean hasStrongCompletion =
					completionPredicate.containsKind(
						SlayerRoutePredicate.Kind.COORDINATE_LAYER_TRANSITION)
					|| completionPredicate.containsKind(
						SlayerRoutePredicate.Kind.VISIBLE_WIDGET_COMPONENT)
					|| completionPredicate.containsKind(
						SlayerRoutePredicate.Kind.VISIBLE_WIDGET_GROUP)
					|| completionPredicate.containsKind(
						SlayerRoutePredicate.Kind.EXACT_NPC)
					|| completionPredicate.containsSpatialResultOutside(
						target.getRoutingPoints(), target.getApproachRadius());
				if (!hasStrongCompletion)
				{
					throw new IllegalArgumentException(
						"Manual completion needs confirmed resultant evidence"
					);
				}
			}
			else if (readinessPredicate != null
				|| !interactionInstruction.isEmpty())
			{
				throw new IllegalArgumentException(
					"Only manual legs use readiness predicates"
				);
			}
			if (kind == LegKind.DECISION)
			{
				if (completionPredicate.getKind()
					!= SlayerRoutePredicate.Kind.ALWAYS)
				{
					throw new IllegalArgumentException(
						"A decision leg must be immediately selectable"
					);
				}
			}
			else if (completionPredicate.containsKind(
				SlayerRoutePredicate.Kind.ALWAYS))
			{
				throw new IllegalArgumentException(
					"Travel and manual legs need explicit completion evidence"
				);
			}
		}

		private Branch selectBranch(final SlayerRouteEvidence evidence)
		{
			for (final Branch branch : branches)
			{
				if (branch.getCondition().matches(evidence))
				{
					return branch;
				}
			}
			return null;
		}

		public String getId()
		{
			return id;
		}

		public LegKind getKind()
		{
			return kind;
		}

		public String getGuidance()
		{
			return guidance;
		}

		public RouteTarget getTarget()
		{
			return target;
		}

		public String getInteractionInstruction()
		{
			return interactionInstruction;
		}

		public SlayerRoutePredicate getReadinessPredicate()
		{
			return readinessPredicate;
		}

		public SlayerRoutePredicate getCompletionPredicate()
		{
			return kind == LegKind.TERMINAL
				? terminalSpec.getCompletionPredicate()
				: completionPredicate;
		}

		public TerminalSpec getTerminalSpec()
		{
			return terminalSpec;
		}

		public List<Branch> getBranches()
		{
			return branches;
		}
	}

	public static final class LegBuilder
	{
		private final String id;
		private final LegKind kind;
		private String guidance;
		private RouteTarget target;
		private String interactionInstruction;
		private SlayerRoutePredicate readinessPredicate;
		private SlayerRoutePredicate completionPredicate;
		private TerminalSpec terminalSpec;
		private final List<Branch> branches = new ArrayList<>();

		private LegBuilder(final String id, final LegKind kind)
		{
			this.id = id;
			this.kind = kind;
		}

		public LegBuilder guidance(final String value)
		{
			guidance = value;
			return this;
		}

		public LegBuilder target(final RouteTarget value)
		{
			target = value;
			return this;
		}

		public LegBuilder interactionInstruction(final String value)
		{
			interactionInstruction = value;
			return this;
		}

		public LegBuilder readyWhen(final SlayerRoutePredicate value)
		{
			readinessPredicate = value;
			return this;
		}

		public LegBuilder completeWhen(final SlayerRoutePredicate value)
		{
			completionPredicate = value;
			return this;
		}

		public LegBuilder terminal(final TerminalSpec value)
		{
			terminalSpec = value;
			return this;
		}

		public LegBuilder then(final String nextLegId)
		{
			branches.add(Branch.to(nextLegId));
			return this;
		}

		public LegBuilder thenWhen(
			final String label,
			final SlayerRoutePredicate condition,
			final String nextLegId)
		{
			branches.add(Branch.when(label, condition, nextLegId));
			return this;
		}

		public Leg build()
		{
			return new Leg(this);
		}
	}

	/** Immutable runtime cursor; callers store it between evidence events. */
	public static final class Cursor
	{
		private final String planId;
		private final String activeLegId;

		private Cursor(final String planId, final String activeLegId)
		{
			this.planId = requireText(planId, "Cursor plan id");
			this.activeLegId = requireText(
				activeLegId, "Cursor active leg id"
			);
		}

		public String getPlanId()
		{
			return planId;
		}

		public String getActiveLegId()
		{
			return activeLegId;
		}
	}

	public static final class Evaluation
	{
		private final Cursor cursor;
		private final Leg activeLeg;
		private final ProgressStatus status;
		private final boolean advanced;

		private Evaluation(
			final Cursor cursor,
			final Leg activeLeg,
			final ProgressStatus status,
			final boolean advanced)
		{
			this.cursor = cursor;
			this.activeLeg = activeLeg;
			this.status = status;
			this.advanced = advanced;
		}

		public Cursor getCursor()
		{
			return cursor;
		}

		public Leg getActiveLeg()
		{
			return activeLeg;
		}

		public ProgressStatus getStatus()
		{
			return status;
		}

		public boolean hasAdvanced()
		{
			return advanced;
		}

		public boolean hasArrived()
		{
			return status == ProgressStatus.ARRIVED;
		}
	}

	public static final class Builder
	{
		private final String id;
		private String startLegId;
		private final Map<String, Leg> legs = new LinkedHashMap<>();
		private ActivityRetention activityRetention;

		private Builder(final String id)
		{
			this.id = id;
		}

		public Builder startAt(final String value)
		{
			startLegId = value;
			return this;
		}

		public Builder activityRetention(final ActivityRetention value)
		{
			activityRetention = value;
			return this;
		}

		public Builder addLeg(final Leg leg)
		{
			Objects.requireNonNull(leg, "leg");
			if (legs.putIfAbsent(leg.getId(), leg) != null)
			{
				throw new IllegalArgumentException(
					"Duplicate route leg id: " + leg.getId()
				);
			}
			return this;
		}

		/** Returns every graph/release invariant violation without throwing. */
		public List<String> validateGraph()
		{
			return Collections.unmodifiableList(validate(
				id, startLegId, legs, activityRetention
			));
		}

		public SlayerRoutePlan build()
		{
			final List<String> errors = validateGraph();
			if (!errors.isEmpty())
			{
				throw new IllegalStateException(
					"Invalid Slayer route plan: " + String.join("; ", errors)
				);
			}
			return new SlayerRoutePlan(this);
		}
	}

	private static List<String> validate(
		final String id,
		final String startLegId,
		final Map<String, Leg> legs,
		final ActivityRetention retention)
	{
		final List<String> errors = new ArrayList<>();
		if (id == null || id.trim().isEmpty())
		{
			errors.add("route id is blank");
		}
		if (startLegId == null || startLegId.trim().isEmpty())
		{
			errors.add("start leg id is blank");
		}
		else if (!legs.containsKey(startLegId))
		{
			errors.add("start leg does not exist: " + startLegId);
		}
		if (retention == null)
		{
			errors.add("activity retention is missing");
		}
		if (legs.isEmpty())
		{
			errors.add("route graph is empty");
			return errors;
		}

		int terminalCount = 0;
		String terminalId = "";
		for (final Leg leg : legs.values())
		{
			if (leg.getKind() == LegKind.TERMINAL)
			{
				terminalCount++;
				terminalId = leg.getId();
			}
			boolean sawDefault = false;
			for (int index = 0; index < leg.getBranches().size(); index++)
			{
				final Branch branch = leg.getBranches().get(index);
				if (!legs.containsKey(branch.getNextLegId()))
				{
					errors.add(
						leg.getId() + " targets missing leg "
							+ branch.getNextLegId()
					);
				}
				if (branch.getCondition().getKind()
					== SlayerRoutePredicate.Kind.ALWAYS)
				{
					if (sawDefault)
					{
						errors.add(
							leg.getId() + " has multiple default branches"
						);
					}
					sawDefault = true;
					if (index != leg.getBranches().size() - 1)
					{
						errors.add(
							leg.getId() + " has branches after its default"
						);
					}
				}
			}
		}
		if (terminalCount != 1)
		{
			errors.add(
				"route graph needs exactly one terminal; found " + terminalCount
			);
		}

		if (startLegId != null && legs.containsKey(startLegId))
		{
			final Set<String> reachable = reachableFrom(startLegId, legs);
			for (final String legId : legs.keySet())
			{
				if (!reachable.contains(legId))
				{
					errors.add("unreachable route leg: " + legId);
				}
			}
		}

		final Set<String> cycleMembers = cycleMembers(legs);
		if (!cycleMembers.isEmpty())
		{
			errors.add("route graph contains a cycle: " + cycleMembers);
		}
		else if (terminalCount == 1)
		{
			final Map<String, Boolean> memo = new HashMap<>();
			for (final String legId : legs.keySet())
			{
				if (!reachesTerminal(legId, terminalId, legs, memo))
				{
					errors.add(
						"route leg cannot reach terminal: " + legId
					);
				}
			}
		}
		return errors;
	}

	private static Set<String> reachableFrom(
		final String startLegId,
		final Map<String, Leg> legs)
	{
		final Set<String> reachable = new LinkedHashSet<>();
		final Deque<String> pending = new ArrayDeque<>();
		pending.push(startLegId);
		while (!pending.isEmpty())
		{
			final String current = pending.pop();
			if (!reachable.add(current))
			{
				continue;
			}
			final Leg leg = legs.get(current);
			if (leg == null)
			{
				continue;
			}
			for (final Branch branch : leg.getBranches())
			{
				if (legs.containsKey(branch.getNextLegId()))
				{
					pending.push(branch.getNextLegId());
				}
			}
		}
		return reachable;
	}

	private static Set<String> cycleMembers(final Map<String, Leg> legs)
	{
		final Map<String, Integer> states = new HashMap<>();
		final Set<String> cycles = new LinkedHashSet<>();
		for (final String legId : legs.keySet())
		{
			detectCycles(legId, legs, states, new ArrayDeque<>(), cycles);
		}
		return cycles;
	}

	private static void detectCycles(
		final String legId,
		final Map<String, Leg> legs,
		final Map<String, Integer> states,
		final Deque<String> stack,
		final Set<String> cycles)
	{
		final int state = states.getOrDefault(legId, 0);
		if (state == 2)
		{
			return;
		}
		if (state == 1)
		{
			cycles.add(legId);
			for (final String member : stack)
			{
				cycles.add(member);
				if (member.equals(legId))
				{
					break;
				}
			}
			return;
		}
		states.put(legId, 1);
		stack.push(legId);
		final Leg leg = legs.get(legId);
		if (leg != null)
		{
			for (final Branch branch : leg.getBranches())
			{
				if (legs.containsKey(branch.getNextLegId()))
				{
					detectCycles(
						branch.getNextLegId(), legs, states, stack, cycles
					);
				}
			}
		}
		stack.pop();
		states.put(legId, 2);
	}

	private static boolean reachesTerminal(
		final String legId,
		final String terminalId,
		final Map<String, Leg> legs,
		final Map<String, Boolean> memo)
	{
		if (legId.equals(terminalId))
		{
			return true;
		}
		if (memo.containsKey(legId))
		{
			return memo.get(legId);
		}
		final Leg leg = legs.get(legId);
		if (leg == null || leg.getBranches().isEmpty())
		{
			memo.put(legId, false);
			return false;
		}
		for (final Branch branch : leg.getBranches())
		{
			if (!reachesTerminal(
				branch.getNextLegId(), terminalId, legs, memo))
			{
				memo.put(legId, false);
				return false;
			}
		}
		memo.put(legId, true);
		return true;
	}
}
