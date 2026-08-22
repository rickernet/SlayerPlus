package com.slayerplus;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Exhaustive, development-only release gate for Slayer routing.
 *
 * <p>The independent {@link SlayerRouteReleaseManifest} is the expected side
 * of every comparison.  This validator deliberately reads the implementation
 * catalogs separately; it never generates route profiles, boss definitions,
 * or recommendation locations from the manifest itself.</p>
 */
public final class SlayerRouteReleaseValidator
{
	private static final ProfileLookup CATALOG_LOOKUP = new ProfileLookup()
	{
		@Override
		public boolean hasExplicitProfile(
			final String taskName,
			final String location,
			final boolean boss)
		{
			return SlayerRouteCatalog.hasExplicitProfileForRegression(
				taskName, location, boss
			);
		}

		@Override
		public SlayerRouteCatalog.RouteProfile resolve(
			final String taskName,
			final String location,
			final boolean boss)
		{
			return SlayerRouteCatalog.resolve(taskName, location, boss);
		}

		@Override
		public WorldPoint findAccess(final String location)
		{
			return SlayerRouteCatalog.find(location);
		}
	};
	private static final PlanLookup PLAN_CATALOG_LOOKUP = new PlanLookup()
	{
		@Override
		public boolean hasExplicitPlan(
			final String taskName,
			final String location,
			final boolean boss)
		{
			return SlayerRoutePlanCatalog.hasExactPlan(taskName, location, boss);
		}

		@Override
		public SlayerRoutePlan resolve(
			final String taskName,
			final String location,
			final boolean boss)
		{
			return SlayerRoutePlanCatalog.resolve(taskName, location, boss);
		}

		@Override
		public Collection<PlanKey> keys()
		{
			final List<PlanKey> keys = new ArrayList<>();
			for (final SlayerRoutePlanCatalog.RouteKey key
				: SlayerRoutePlanCatalog.keysForRegression())
			{
				keys.add(new PlanKey(
					key.getTaskName(), key.getLocation(), key.isBoss()
				));
			}
			return Collections.unmodifiableList(keys);
		}
	};

	private SlayerRouteReleaseValidator()
	{
	}

	/** Return every release failure instead of hiding later rows behind the first. */
	public static List<String> validate()
	{
		return validateForRegression(
			SlayerRouteReleaseManifest.entries(),
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			SlayerTaskVariantCatalog.getBossDefinitions(),
			SlayerTaskVariantCatalog.getDirectBossDefinitions(),
			CATALOG_LOOKUP,
			PLAN_CATALOG_LOOKUP
		);
	}

	public static void validateOrThrow()
	{
		final List<String> failures = validate();
		if (!failures.isEmpty())
		{
			throw new IllegalStateException(
				"Slayer route release gate failed (" + failures.size()
					+ " failures): " + failures
			);
		}
	}

	/**
	 * Injectable seam used only by regression tests.  It permits mutation-style
	 * tests (missing rows/profiles) without mutating production static maps.
	 */
	static List<String> validateForRegression(
		final List<SlayerRouteReleaseManifest.Entry> manifestEntries,
		final Collection<String> strategyNames,
		final List<SlayerTaskVariantCatalog.EncounterDefinition> selectableBosses,
		final List<SlayerTaskVariantCatalog.EncounterDefinition> directBosses,
		final ProfileLookup profileLookup)
	{
		return validateForRegression(
			manifestEntries,
			strategyNames,
			selectableBosses,
			directBosses,
			profileLookup,
			PLAN_CATALOG_LOOKUP
		);
	}

	static List<String> validateForRegression(
		final List<SlayerRouteReleaseManifest.Entry> manifestEntries,
		final Collection<String> strategyNames,
		final List<SlayerTaskVariantCatalog.EncounterDefinition> selectableBosses,
		final List<SlayerTaskVariantCatalog.EncounterDefinition> directBosses,
		final ProfileLookup profileLookup,
		final PlanLookup planLookup)
	{
		final List<String> failures = new ArrayList<>();
		final List<SlayerRouteReleaseManifest.Entry> entries = manifestEntries == null
			? Collections.emptyList() : manifestEntries;

		validateManifestParity(entries, strategyNames, failures);
		validateAssignmentLocationCoverage(entries, failures);
		validateSelectableBossParity(entries, selectableBosses, failures);
		validateDirectBossParity(entries, directBosses, failures);

		if (profileLookup == null)
		{
			failures.add("profile catalog lookup is unavailable");
			return immutable(failures);
		}
		if (planLookup == null)
		{
			failures.add("route plan catalog lookup is unavailable");
			return immutable(failures);
		}

		validatePlanCatalogKeys(entries, planLookup, failures);

		for (final SlayerRouteReleaseManifest.Entry entry : entries)
		{
			validateEntry(entry, profileLookup, planLookup, failures);
		}
		return immutable(failures);
	}

	interface ProfileLookup
	{
		boolean hasExplicitProfile(String taskName, String location, boolean boss);
		SlayerRouteCatalog.RouteProfile resolve(
			String taskName, String location, boolean boss
		);
		WorldPoint findAccess(String location);
	}

	interface PlanLookup
	{
		boolean hasExplicitPlan(String taskName, String location, boolean boss);
		SlayerRoutePlan resolve(String taskName, String location, boolean boss);

		default Collection<PlanKey> keys()
		{
			return Collections.emptyList();
		}
	}

	static final class PlanKey
	{
		private final String taskName;
		private final String location;
		private final boolean boss;

		PlanKey(final String taskName, final String location, final boolean boss)
		{
			this.taskName = normalize(taskName);
			this.location = normalize(location);
			this.boss = boss;
		}

		String getTaskName()
		{
			return taskName;
		}

		String getLocation()
		{
			return location;
		}

		boolean isBoss()
		{
			return boss;
		}

		@Override
		public boolean equals(final Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof PlanKey))
			{
				return false;
			}
			final PlanKey that = (PlanKey) other;
			return boss == that.boss
				&& taskName.equals(that.taskName)
				&& location.equals(that.location);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(taskName, location, boss);
		}

		@Override
		public String toString()
		{
			return (boss ? "boss" : "assignment") + "|"
				+ taskName + "|" + location;
		}
	}

	static ProfileLookup catalogLookupForRegression()
	{
		return CATALOG_LOOKUP;
	}

	static PlanLookup planLookupForRegression()
	{
		return PLAN_CATALOG_LOOKUP;
	}

	private static void validateManifestParity(
		final List<SlayerRouteReleaseManifest.Entry> entries,
		final Collection<String> strategyNames,
		final List<String> failures)
	{
		final Set<String> manifestAssignments = new LinkedHashSet<>();
		final Set<String> manifestSelectableBosses = new LinkedHashSet<>();
		final Set<String> manifestDirectBosses = new LinkedHashSet<>();
		final Set<String> rowKeys = new LinkedHashSet<>();

		for (final SlayerRouteReleaseManifest.Entry entry : entries)
		{
			if (entry == null)
			{
				failures.add("manifest contains a null row");
				continue;
			}

			final String rowKey = rowKey(entry);
			if (!rowKeys.add(rowKey))
			{
				failures.add("duplicate manifest row: " + rowKey);
			}

			switch (entry.getKind())
			{
				case ASSIGNMENT:
					manifestAssignments.add(identityKey(entry.getAssignmentName()));
					break;
				case SELECTABLE_BOSS:
					manifestSelectableBosses.add(identityKey(entry.getEncounterName()));
					break;
				case DIRECT_BOSS:
					manifestDirectBosses.add(identityKey(entry.getEncounterName()));
					break;
				default:
					failures.add("unknown manifest row kind: " + rowKey);
					break;
			}
		}

		compareExact(
			"manifest assignment registry",
			normalizedIdentities(
				SlayerRouteReleaseManifest.canonicalAssignmentNames()
			),
			manifestAssignments,
			failures
		);
		compareExact(
			"manifest selectable-boss registry",
			normalizedIdentities(
				SlayerRouteReleaseManifest.canonicalSelectableBossNames()
			),
			manifestSelectableBosses,
			failures
		);
		compareExact(
			"manifest direct-boss registry",
			normalizedIdentities(
				SlayerRouteReleaseManifest.canonicalDirectBossNames()
			),
			manifestDirectBosses,
			failures
		);

		final Set<String> expectedStrategies = new LinkedHashSet<>();
		expectedStrategies.addAll(normalizedIdentities(
			SlayerRouteReleaseManifest.canonicalAssignmentNames()
		));
		expectedStrategies.addAll(normalizedIdentities(
			SlayerRouteReleaseManifest.canonicalSelectableBossNames()
		));
		expectedStrategies.addAll(normalizedIdentities(
			SlayerRouteReleaseManifest.canonicalDirectBossNames()
		));
		compareExact(
			"manifest/strategy task registry",
			expectedStrategies,
			normalizedIdentities(strategyNames),
			failures
		);
	}

	private static void validateAssignmentLocationCoverage(
		final List<SlayerRouteReleaseManifest.Entry> entries,
		final List<String> failures)
	{
		final Map<String, Set<String>> manifestLocations = new LinkedHashMap<>();
		for (final SlayerRouteReleaseManifest.Entry entry : entries)
		{
			if (entry == null
				|| entry.getKind() != SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT)
			{
				continue;
			}
			addToSetMap(
				manifestLocations,
				identityKey(entry.getAssignmentName()),
				normalize(entry.getLocation())
			);
		}

		for (final String assignment
			: SlayerRouteReleaseManifest.canonicalAssignmentNames())
		{
			final String assignmentKey = identityKey(assignment);
			final Set<String> expected = manifestLocations.get(assignmentKey);
			if (expected == null || expected.isEmpty())
			{
				failures.add(
					"canonical assignment has no manifest location: " + assignment
				);
				continue;
			}

			final Set<String> implementationLocations = new LinkedHashSet<>();
			for (final String location
				: SlayerRecommendationEngine.catalogLocationsForValidation(assignment))
			{
				implementationLocations.add(normalize(location));
			}
			if (implementationLocations.isEmpty())
			{
				final SlayerTaskTravelAuditCatalog.Entry travel =
					SlayerTaskTravelAuditCatalog.find(assignment);
				if (travel != null && !normalize(travel.getLocation()).isEmpty())
				{
					implementationLocations.add(normalize(travel.getLocation()));
				}
			}

			if (implementationLocations.isEmpty())
			{
				failures.add(
					"implementation assignment has no selectable or audited location: "
						+ assignment
				);
				continue;
			}

			compareExact(
				"assignment location registry for " + assignment,
				expected,
				implementationLocations,
				failures
			);
		}
	}

	private static void validateSelectableBossParity(
		final List<SlayerRouteReleaseManifest.Entry> entries,
		final List<SlayerTaskVariantCatalog.EncounterDefinition> definitions,
		final List<String> failures)
	{
		final Map<String, BossContract> expected = manifestBossContracts(
			entries, SlayerRouteReleaseManifest.EntryKind.SELECTABLE_BOSS, failures
		);
		final Map<String, BossContract> observed = implementationBossContracts(
			definitions, "selectable boss", failures
		);

		compareExact(
			"selectable boss registry",
			expected.keySet(), observed.keySet(), failures
		);
		for (final Map.Entry<String, BossContract> item : expected.entrySet())
		{
			final BossContract actual = observed.get(item.getKey());
			if (actual == null)
			{
				continue;
			}
			final BossContract wanted = item.getValue();
			if (!wanted.location.equals(actual.location))
			{
				failures.add(
					"selectable boss location mismatch: " + wanted.displayName
						+ " expected=" + wanted.location + " actual=" + actual.location
				);
			}
			compareExact(
				"selectable boss eligible assignments for " + wanted.displayName,
				wanted.assignments, actual.assignments, failures
			);
		}
	}

	private static void validateDirectBossParity(
		final List<SlayerRouteReleaseManifest.Entry> entries,
		final List<SlayerTaskVariantCatalog.EncounterDefinition> definitions,
		final List<String> failures)
	{
		final Map<String, BossContract> expected = manifestBossContracts(
			entries, SlayerRouteReleaseManifest.EntryKind.DIRECT_BOSS, failures
		);
		final Map<String, BossContract> observed = implementationBossContracts(
			definitions, "direct boss", failures
		);
		compareExact(
			"direct boss registry", expected.keySet(), observed.keySet(), failures
		);
		for (final Map.Entry<String, BossContract> item : expected.entrySet())
		{
			final BossContract actual = observed.get(item.getKey());
			if (actual != null
				&& !item.getValue().location.equals(actual.location))
			{
				failures.add(
					"direct boss location mismatch: " + item.getValue().displayName
						+ " expected=" + item.getValue().location
						+ " actual=" + actual.location
				);
			}
		}
	}

	private static Map<String, BossContract> manifestBossContracts(
		final List<SlayerRouteReleaseManifest.Entry> entries,
		final SlayerRouteReleaseManifest.EntryKind kind,
		final List<String> failures)
	{
		final Map<String, BossContract> contracts = new LinkedHashMap<>();
		for (final SlayerRouteReleaseManifest.Entry entry : entries)
		{
			if (entry == null || entry.getKind() != kind)
			{
				continue;
			}
			final String key = identityKey(entry.getEncounterName());
			final BossContract contract = new BossContract(
				entry.getEncounterName(), normalize(entry.getLocation())
			);
			for (final String assignment : entry.getEligibleAssignments())
			{
				contract.assignments.add(normalize(assignment));
			}
			if (contracts.put(key, contract) != null)
			{
				failures.add(
					"duplicate " + kind + " manifest encounter: "
						+ entry.getEncounterName()
				);
			}
		}
		return contracts;
	}

	private static Map<String, BossContract> implementationBossContracts(
		final List<SlayerTaskVariantCatalog.EncounterDefinition> definitions,
		final String label,
		final List<String> failures)
	{
		final Map<String, BossContract> contracts = new LinkedHashMap<>();
		if (definitions == null)
		{
			failures.add(label + " catalog is unavailable");
			return contracts;
		}

		for (final SlayerTaskVariantCatalog.EncounterDefinition definition
			: definitions)
		{
			if (definition == null)
			{
				failures.add(label + " catalog contains a null definition");
				continue;
			}
			final String key = identityKey(definition.getTargetName());
			BossContract contract = contracts.get(key);
			if (contract == null)
			{
				contract = new BossContract(
					definition.getTargetName(), normalize(definition.getLocation())
				);
				contracts.put(key, contract);
			}
			else if (!contract.location.equals(normalize(definition.getLocation())))
			{
				failures.add(
					label + " has multiple locations for one encounter: "
						+ definition.getTargetName()
				);
			}
			final String assignment = normalize(definition.getAssignmentName());
			if (!assignment.isEmpty())
			{
				contract.assignments.add(assignment);
			}
		}
		return contracts;
	}

	/**
	 * The declarative catalog is an independent proof source.  Every plan key
	 * must name one exact manifest row; an undocumented plan is not allowed to
	 * silently legitimize a generic or misspelled route.
	 */
	private static void validatePlanCatalogKeys(
		final List<SlayerRouteReleaseManifest.Entry> entries,
		final PlanLookup lookup,
		final List<String> failures)
	{
		final Set<PlanKey> manifestKeys = new LinkedHashSet<>();
		for (final SlayerRouteReleaseManifest.Entry entry : entries)
		{
			if (entry == null)
			{
				continue;
			}
			final boolean boss = entry.getKind()
				!= SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT;
			final String taskName = boss
				? entry.getEncounterName() : entry.getAssignmentName();
			manifestKeys.add(new PlanKey(
				taskName, entry.getLocation(), boss
			));
		}

		final Collection<PlanKey> catalogKeys;
		try
		{
			catalogKeys = lookup.keys();
		}
		catch (final RuntimeException ex)
		{
			failures.add(
				"route plan key enumeration failed: " + ex.getClass().getSimpleName()
			);
			return;
		}
		if (catalogKeys == null)
		{
			failures.add("route plan catalog key enumeration returned null");
			return;
		}

		final Set<PlanKey> observedKeys = new LinkedHashSet<>();
		final Map<String, PlanKey> planIds = new LinkedHashMap<>();
		for (final PlanKey key : catalogKeys)
		{
			if (key == null)
			{
				failures.add("route plan catalog contains a null exact key");
				continue;
			}
			if (!observedKeys.add(key))
			{
				failures.add("duplicate exact route plan key: " + key);
			}
			if (!manifestKeys.contains(key))
			{
				failures.add(
					"route plan has no exact release-manifest row: " + key
				);
			}

			final SlayerRoutePlan plan;
			try
			{
				plan = lookup.resolve(
					key.getTaskName(), key.getLocation(), key.isBoss()
				);
			}
			catch (final RuntimeException ex)
			{
				failures.add(
					"exact route plan key throws during resolve: " + key + " -> "
						+ ex.getClass().getSimpleName()
				);
				continue;
			}
			if (plan == null)
			{
				failures.add("exact route plan key resolves null: " + key);
				continue;
			}
			final String normalizedId = normalize(plan.getId());
			final PlanKey previous = planIds.putIfAbsent(normalizedId, key);
			if (normalizedId.isEmpty())
			{
				failures.add("route plan has a blank id: " + key);
			}
			else if (previous != null && !previous.equals(key))
			{
				failures.add(
					"route plan id is reused across exact keys: " + plan.getId()
						+ " -> " + previous + ", " + key
				);
			}
		}
	}

	/**
	 * Validate an exact declarative plan without trusting the plan builder's
	 * own checks. A valid plan is a complete successor routing contract for its
	 * exact manifest key; the legacy profile is not required in parallel when
	 * this independent validation succeeds.
	 */
	private static boolean validateExplicitPlan(
		final SlayerRouteReleaseManifest.Entry entry,
		final String taskName,
		final boolean boss,
		final PlanLookup lookup,
		final List<String> failures)
	{
		final String identity = rowIdentity(entry);
		final boolean explicit;
		final SlayerRoutePlan plan;
		try
		{
			explicit = lookup.hasExplicitPlan(
				taskName, entry.getLocation(), boss
			);
			plan = lookup.resolve(taskName, entry.getLocation(), boss);
		}
		catch (final RuntimeException ex)
		{
			failures.add(
				"route plan lookup failed: " + identity + " -> "
					+ ex.getClass().getSimpleName()
			);
			return false;
		}

		if (!explicit)
		{
			if (plan != null)
			{
				failures.add(
					"generic route-plan fallback reached by release row: "
						+ identity + " -> " + plan.getId()
				);
			}
			return false;
		}
		if (plan == null)
		{
			failures.add("exact declarative route plan resolves null: " + identity);
			return false;
		}

		final List<String> planFailures = new ArrayList<>();
		validatePlanGraph(plan, identity, planFailures);
		validatePlanLegContracts(entry, plan, identity, planFailures);
		validatePlanTerminal(
			entry, taskName, plan, identity, planFailures
		);
		validatePlanAccess(entry, plan, identity, planFailures);
		validatePlanRetention(entry, plan, identity, planFailures);
		failures.addAll(planFailures);
		return planFailures.isEmpty();
	}

	private static void validatePlanGraph(
		final SlayerRoutePlan plan,
		final String identity,
		final List<String> failures)
	{
		if (normalize(plan.getId()).isEmpty())
		{
			failures.add("route plan has a blank id: " + identity);
		}
		final Map<String, SlayerRoutePlan.Leg> legs = plan.getLegs();
		if (legs == null || legs.isEmpty())
		{
			failures.add("route plan graph is empty: " + identity);
			return;
		}
		final String start = plan.getStartLegId();
		if (normalize(start).isEmpty() || !legs.containsKey(start))
		{
			failures.add("route plan start leg is missing: " + identity);
		}

		int terminalCount = 0;
		int transitionLegCount = 0;
		String terminalId = "";
		final Map<String, Integer> indegrees = new LinkedHashMap<>();
		for (final String legId : legs.keySet())
		{
			indegrees.put(legId, 0);
		}
		for (final Map.Entry<String, SlayerRoutePlan.Leg> item : legs.entrySet())
		{
			final String legId = item.getKey();
			final SlayerRoutePlan.Leg leg = item.getValue();
			if (leg == null)
			{
				failures.add("route plan graph contains a null leg: " + identity);
				continue;
			}
			if (!legId.equals(leg.getId()))
			{
				failures.add(
					"route plan leg key/id drift: " + identity + " -> "
						+ legId + "/" + leg.getId()
				);
			}
			if (leg.getKind() == SlayerRoutePlan.LegKind.TERMINAL)
			{
				terminalCount++;
				terminalId = legId;
				if (!leg.getBranches().isEmpty())
				{
					failures.add(
						"route plan terminal has outgoing branches: " + identity
					);
				}
			}
			else
			{
				transitionLegCount++;
				if (leg.getBranches().isEmpty())
				{
					failures.add(
						"route plan non-terminal has no branch: " + identity
							+ " -> " + legId
					);
				}
			}

			boolean sawDefault = false;
			for (int index = 0; index < leg.getBranches().size(); index++)
			{
				final SlayerRoutePlan.Branch branch = leg.getBranches().get(index);
				if (branch == null || branch.getCondition() == null)
				{
					failures.add(
						"route plan contains a null branch contract: " + identity
							+ " -> " + legId
					);
					continue;
				}
				final String next = branch.getNextLegId();
				if (!legs.containsKey(next))
				{
					failures.add(
						"route plan branch targets a missing leg: " + identity
							+ " -> " + legId + "/" + next
					);
				}
				else
				{
					indegrees.put(next, indegrees.get(next) + 1);
				}
				if (branch.getCondition().getKind()
					== SlayerRoutePredicate.Kind.ALWAYS)
				{
					if (sawDefault || index != leg.getBranches().size() - 1)
					{
						failures.add(
							"route plan branch default is ambiguous: " + identity
								+ " -> " + legId
						);
					}
					sawDefault = true;
				}
			}
		}
		if (terminalCount != 1)
		{
			failures.add(
				"route plan needs exactly one terminal: " + identity
					+ " -> " + terminalCount
			);
		}
		if (transitionLegCount == 0)
		{
			failures.add(
				"route plan has no approach or transition leg: " + identity
			);
		}

		if (legs.containsKey(start))
		{
			final Set<String> reachable = reachablePlanLegs(start, legs);
			for (final String legId : legs.keySet())
			{
				if (!reachable.contains(legId))
				{
					failures.add(
						"route plan contains an unreachable leg: " + identity
							+ " -> " + legId
					);
				}
			}
		}

		final Deque<String> ready = new ArrayDeque<>();
		for (final Map.Entry<String, Integer> item : indegrees.entrySet())
		{
			if (item.getValue() == 0)
			{
				ready.addLast(item.getKey());
			}
		}
		int processed = 0;
		while (!ready.isEmpty())
		{
			final SlayerRoutePlan.Leg leg = legs.get(ready.removeFirst());
			processed++;
			if (leg == null)
			{
				continue;
			}
			for (final SlayerRoutePlan.Branch branch : leg.getBranches())
			{
				if (branch == null || !indegrees.containsKey(branch.getNextLegId()))
				{
					continue;
				}
				final String next = branch.getNextLegId();
				final int degree = indegrees.get(next) - 1;
				indegrees.put(next, degree);
				if (degree == 0)
				{
					ready.addLast(next);
				}
			}
		}
		if (processed != legs.size())
		{
			failures.add("route plan graph contains a cycle: " + identity);
		}
		else if (terminalCount == 1)
		{
			final Map<String, Boolean> memo = new HashMap<>();
			for (final String legId : legs.keySet())
			{
				if (!planLegReachesTerminal(legId, terminalId, legs, memo))
				{
					failures.add(
						"route plan leg cannot reach terminal: " + identity
							+ " -> " + legId
					);
				}
			}
		}
	}

	private static Set<String> reachablePlanLegs(
		final String start,
		final Map<String, SlayerRoutePlan.Leg> legs)
	{
		final Set<String> reached = new LinkedHashSet<>();
		final Deque<String> pending = new ArrayDeque<>();
		pending.addLast(start);
		while (!pending.isEmpty())
		{
			final String legId = pending.removeFirst();
			if (!reached.add(legId))
			{
				continue;
			}
			final SlayerRoutePlan.Leg leg = legs.get(legId);
			if (leg == null)
			{
				continue;
			}
			for (final SlayerRoutePlan.Branch branch : leg.getBranches())
			{
				if (branch != null && legs.containsKey(branch.getNextLegId()))
				{
					pending.addLast(branch.getNextLegId());
				}
			}
		}
		return reached;
	}

	private static boolean planLegReachesTerminal(
		final String legId,
		final String terminalId,
		final Map<String, SlayerRoutePlan.Leg> legs,
		final Map<String, Boolean> memo)
	{
		if (legId.equals(terminalId))
		{
			return true;
		}
		final Boolean known = memo.get(legId);
		if (known != null)
		{
			return known;
		}
		final SlayerRoutePlan.Leg leg = legs.get(legId);
		if (leg == null || leg.getBranches().isEmpty())
		{
			memo.put(legId, false);
			return false;
		}
		for (final SlayerRoutePlan.Branch branch : leg.getBranches())
		{
			if (branch == null
				|| !planLegReachesTerminal(
					branch.getNextLegId(), terminalId, legs, memo))
			{
				memo.put(legId, false);
				return false;
			}
		}
		memo.put(legId, true);
		return true;
	}

	private static void validatePlanLegContracts(
		final SlayerRouteReleaseManifest.Entry entry,
		final SlayerRoutePlan plan,
		final String identity,
		final List<String> failures)
	{
		for (final SlayerRoutePlan.Leg leg : plan.getLegs().values())
		{
			if (leg == null)
			{
				continue;
			}
			final SlayerRoutePlan.RouteTarget target = leg.getTarget();
			if ((leg.getKind() == SlayerRoutePlan.LegKind.TRAVEL
				|| leg.getKind() == SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				&& target == null)
			{
				failures.add(
					"route plan movement leg has no target: " + identity
						+ " -> " + leg.getId()
				);
			}
			if (leg.getKind() == SlayerRoutePlan.LegKind.TRAVEL
				&& target != null && !target.hasRoutingPoint())
			{
				failures.add(
					"unmapped travel leg lacks a static graph target or manual "
						+ "interaction contract: " + identity + " -> " + leg.getId()
				);
			}
			if (target != null)
			{
				for (final WorldPoint point : target.getRoutingPoints())
				{
					if (point == null || point.getX() <= 0 || point.getY() <= 0
						|| point.getPlane() < 0 || point.getPlane() > 3)
					{
						failures.add(
							"route plan has an invalid graph target: " + identity
								+ " -> " + leg.getId() + "/" + pointText(point)
						);
					}
				}
			}

			if (leg.getKind() != SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
			{
				continue;
			}
			if (normalize(leg.getInteractionInstruction()).isEmpty())
			{
				failures.add(
					"manual route leg has no interaction instruction: "
						+ identity + " -> " + leg.getId()
				);
			}
			final SlayerRoutePredicate readiness = leg.getReadinessPredicate();
			final SlayerRoutePredicate completion = leg.getCompletionPredicate();
			if (readiness == null || !readiness.containsKind(
				SlayerRoutePredicate.Kind.TRACKED_OBJECT_ACTION))
			{
				failures.add(
					"manual route leg has no tracked object/action readiness: "
						+ identity + " -> " + leg.getId()
				);
			}
			if (completion == null || !hasStrongManualCompletion(
				completion, entry))
			{
				failures.add(
					"manual route leg has no observed transition completion: "
						+ identity + " -> " + leg.getId()
				);
			}
			if (readiness != null && completion != null
				&& completion.containsKind(
					SlayerRoutePredicate.Kind.OBSERVED_OBJECT_INTERACTION)
				&& !hasMatchingObjectContract(readiness, completion))
			{
				failures.add(
					"manual route readiness/completion object contracts differ: "
						+ identity + " -> " + leg.getId()
				);
			}
		}
	}

	private static boolean hasStrongManualCompletion(
		final SlayerRoutePredicate predicate,
		final SlayerRouteReleaseManifest.Entry entry)
	{
		return predicate.containsKind(
				SlayerRoutePredicate.Kind.OBSERVED_OBJECT_INTERACTION)
			|| predicate.containsKind(
				SlayerRoutePredicate.Kind.COORDINATE_LAYER_TRANSITION)
			|| predicate.containsKind(
				SlayerRoutePredicate.Kind.VISIBLE_WIDGET_COMPONENT)
			|| predicate.containsKind(
				SlayerRoutePredicate.Kind.VISIBLE_WIDGET_GROUP)
			|| hasEncounterSpecificSpatialTerminal(
				predicate, entry.getExpectedAccess())
			/* Ordinary assignment packs remain present while their area is loaded,
			 * so exact NPC discovery is valid evidence that the entrance transition
			 * completed. Boss NPCs can be absent before/between kills and remain
			 * subject to the persistent room/widget/object requirement below. */
			|| (entry.getKind()
				== SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT
				&& predicate.containsKind(SlayerRoutePredicate.Kind.EXACT_NPC));
	}

	private static boolean hasMatchingObjectContract(
		final SlayerRoutePredicate readiness,
		final SlayerRoutePredicate completion)
	{
		final List<SlayerRoutePredicate.ObjectActionSpec> tracked =
			new ArrayList<>();
		final List<SlayerRoutePredicate.ObjectActionSpec> observed =
			new ArrayList<>();
		collectObjectSpecs(
			readiness, SlayerRoutePredicate.Kind.TRACKED_OBJECT_ACTION, tracked
		);
		collectObjectSpecs(
			completion,
			SlayerRoutePredicate.Kind.OBSERVED_OBJECT_INTERACTION,
			observed
		);
		for (final SlayerRoutePredicate.ObjectActionSpec first : tracked)
		{
			for (final SlayerRoutePredicate.ObjectActionSpec second : observed)
			{
				if (sameObjectContract(first, second))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static boolean sameObjectContract(
		final SlayerRoutePredicate.ObjectActionSpec first,
		final SlayerRoutePredicate.ObjectActionSpec second)
	{
		if (first == null || second == null)
		{
			return false;
		}
		final boolean sameIdentity = !Collections.disjoint(
			first.getObjectIds(), second.getObjectIds())
			|| !Collections.disjoint(
				first.getObjectNames(), second.getObjectNames());
		return sameIdentity && !Collections.disjoint(
			first.getActions(), second.getActions());
	}

	private static void collectObjectSpecs(
		final SlayerRoutePredicate predicate,
		final SlayerRoutePredicate.Kind kind,
		final List<SlayerRoutePredicate.ObjectActionSpec> result)
	{
		if (predicate == null)
		{
			return;
		}
		if (predicate.getKind() == kind && predicate.getObjectActionSpec() != null)
		{
			result.add(predicate.getObjectActionSpec());
		}
		for (final SlayerRoutePredicate child : predicate.getChildren())
		{
			collectObjectSpecs(child, kind, result);
		}
	}

	private static void validatePlanTerminal(
		final SlayerRouteReleaseManifest.Entry entry,
		final String taskName,
		final SlayerRoutePlan plan,
		final String identity,
		final List<String> failures)
	{
		final SlayerRoutePlan.TerminalSpec terminal = plan.getTerminalSpec();
		if (terminal == null || terminal.getCompletionPredicate() == null)
		{
			failures.add("route plan terminal predicate is missing: " + identity);
			return;
		}
		final SlayerRoutePredicate predicate = terminal.getCompletionPredicate();
		if (predicate.containsKind(SlayerRoutePredicate.Kind.ALWAYS))
		{
			failures.add("route plan terminal accepts unconditional arrival: " + identity);
		}

		final boolean exactNpc = predicate.containsKind(
			SlayerRoutePredicate.Kind.EXACT_NPC);
		final boolean spatial = predicate.containsKind(
				SlayerRoutePredicate.Kind.WORLD_AREA)
			|| predicate.containsKind(SlayerRoutePredicate.Kind.TEMPLATE_AREA);
		final boolean transition = predicate.containsKind(
			SlayerRoutePredicate.Kind.COORDINATE_LAYER_TRANSITION);
		final boolean observedObject = predicate.containsKind(
			SlayerRoutePredicate.Kind.OBSERVED_OBJECT_INTERACTION);
		final boolean widget = predicate.containsKind(
				SlayerRoutePredicate.Kind.VISIBLE_WIDGET_COMPONENT)
			|| predicate.containsKind(
				SlayerRoutePredicate.Kind.VISIBLE_WIDGET_GROUP);

		boolean mechanismValid;
		switch (entry.getTerminalContract())
		{
			case OPEN_WORLD_EXACT_TARGET:
				mechanismValid = spatial;
				break;
			case FULL_TRANSPORT_CHAIN_EXACT_INTERIOR:
				/* Moving spawn packs deliberately declare exact-NPC discovery in the
				 * manifest; rows with an independently reviewed point remain spatial. */
				mechanismValid = entry.getTerminalMechanism()
					== SlayerRouteReleaseManifest.TerminalMechanism.EXACT_NPC_DISCOVERY
					? exactNpc : spatial;
				break;
			case INSTANCE_ENTRANCE_OR_WIDGET_STOP:
			case WAVE_ENTRANCE_OR_WIDGET_STOP:
				mechanismValid = observedObject || widget || exactNpc;
				break;
			case INSTANCE_REGION_STOP:
				mechanismValid = spatial || transition || exactNpc;
				break;
			case MANUAL_STAGE_EXACT_CHECKPOINT:
				mechanismValid = observedObject || widget || transition || exactNpc;
				break;
			default:
				mechanismValid = false;
				break;
		}
		if (!mechanismValid)
		{
			failures.add(
				"route plan terminal does not prove "
					+ entry.getTerminalContract() + ": " + identity
			);
		}

		/*
		 * Boss NPCs can be absent before the spawn and between kills.  Exact NPC
		 * discovery may strengthen a terminal, but it cannot be the only durable
		 * stop signal for a boss row whose manifest has no static terminal.  The
		 * plan must also prove persistent encounter presence through an authored
		 * spatial area, visible widget, or currently tracked encounter object.
		 */
		final boolean boss = entry.getKind()
			!= SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT;
		final boolean persistentEncounterPresence =
			hasEncounterSpecificSpatialTerminal(
				predicate, entry.getExpectedAccess()) || widget
			|| hasDistinctPersistentTerminalObject(plan, predicate);
		if (boss && entry.getExpectedTerminal() == null && exactNpc
			&& !persistentEncounterPresence)
		{
			failures.add(
				"boss route terminal relies on transient exact NPC presence: "
					+ identity
			);
		}

		if (exactNpc && !hasExpectedPlanNpcAlias(predicate, taskName))
		{
			failures.add(
				"route plan terminal has no exact target NPC alias: " + identity
			);
		}
		final WorldPoint expected = toWorldPoint(entry.getExpectedTerminal());
		if (expected != null && !anySpatialAreaContains(predicate, expected))
		{
			failures.add(
				"route plan spatial terminal excludes manifest terminal: "
					+ identity + " -> " + pointText(expected)
			);
		}
	}

	private static boolean hasEncounterSpecificSpatialTerminal(
		final SlayerRoutePredicate predicate,
		final SlayerRouteReleaseManifest.Endpoint expectedAccess)
	{
		final List<SlayerRoutePredicate.SpatialArea> areas = new ArrayList<>();
		collectSpatialAreas(predicate, areas);
		for (final SlayerRoutePredicate.SpatialArea area : areas)
		{
			if (area.getCoordinateSpace()
				== SlayerRouteEvidence.CoordinateSpace.TEMPLATE)
			{
				return true;
			}
			if (expectedAccess == null
				|| area.getPlane() != expectedAccess.getPlane()
				|| expectedAccess.getX() < area.getMinX()
				|| expectedAccess.getX() > area.getMaxX()
				|| expectedAccess.getY() < area.getMinY()
				|| expectedAccess.getY() > area.getMaxY())
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasDistinctPersistentTerminalObject(
		final SlayerRoutePlan plan,
		final SlayerRoutePredicate terminal)
	{
		final List<SlayerRoutePredicate.ObjectActionSpec> terminalObjects =
			new ArrayList<>();
		collectObjectSpecs(
			terminal, SlayerRoutePredicate.Kind.TRACKED_OBJECT_ACTION,
			terminalObjects
		);
		if (terminalObjects.isEmpty())
		{
			return false;
		}

		final List<SlayerRoutePredicate.ObjectActionSpec> transitionObjects =
			new ArrayList<>();
		for (final SlayerRoutePlan.Leg leg : plan.getLegs().values())
		{
			if (leg == null || leg.getKind() == SlayerRoutePlan.LegKind.TERMINAL)
			{
				continue;
			}
			collectObjectSpecs(
				leg.getReadinessPredicate(),
				SlayerRoutePredicate.Kind.TRACKED_OBJECT_ACTION,
				transitionObjects
			);
		}

		for (final SlayerRoutePredicate.ObjectActionSpec terminalObject
			: terminalObjects)
		{
			boolean reusedTransition = false;
			for (final SlayerRoutePredicate.ObjectActionSpec transitionObject
				: transitionObjects)
			{
				if (sameObjectContract(terminalObject, transitionObject))
				{
					reusedTransition = true;
					break;
				}
			}
			if (!reusedTransition)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The manifest access is the reviewed handoff from ordinary travel into the
	 * encounter graph. It must be represented by an authored route target. It is
	 * deliberately not checked against activity retention: retention describes
	 * where an already-active encounter stays active, not how the player enters.
	 */
	private static void validatePlanAccess(
		final SlayerRouteReleaseManifest.Entry entry,
		final SlayerRoutePlan plan,
		final String identity,
		final List<String> failures)
	{
		final WorldPoint expected = toWorldPoint(entry.getExpectedAccess());
		if (expected == null)
		{
			return;
		}
		for (final SlayerRoutePlan.Leg leg : plan.getLegs().values())
		{
			if (leg == null || leg.getTarget() == null)
			{
				continue;
			}
			final int radius = leg.getTarget().getApproachRadius();
			for (final WorldPoint point : leg.getTarget().getRoutingPoints())
			{
				if (point != null && point.getPlane() == expected.getPlane()
					&& Math.abs(point.getX() - expected.getX()) <= radius
					&& Math.abs(point.getY() - expected.getY()) <= radius)
				{
					return;
				}
			}
		}
		failures.add(
			"route plan has no authored manifest access target: " + identity
				+ " -> " + pointText(expected)
		);
	}

	private static boolean hasExpectedPlanNpcAlias(
		final SlayerRoutePredicate predicate,
		final String taskName)
	{
		final Set<String> expected = new LinkedHashSet<>();
		expected.add(normalize(taskName));
		expected.add(identityKey(taskName));
		for (final String alias : SlayerTaskNpcCatalog.aliasesFor(taskName))
		{
			expected.add(normalize(alias));
			expected.add(identityKey(alias));
		}
		final Set<String> actual = new LinkedHashSet<>();
		collectExactNpcNames(predicate, actual);
		for (final String value : actual)
		{
			if (expected.contains(value) || expected.contains(identityKey(value))
				|| matchesExpectedNpcFamily(value, expected))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean matchesExpectedNpcFamily(
		final String actual,
		final Set<String> expected)
	{
		for (final String family : expected)
		{
			if (family.isEmpty())
			{
				continue;
			}
			/* The NPC catalog independently authorizes the family. The route plan
			 * must still enumerate a complete exact NPC name; only a leading or
			 * trailing in-game variant qualifier is accepted here. */
			if (actual.startsWith(family + " ")
				|| actual.endsWith(" " + family))
			{
				return true;
			}
		}
		return false;
	}

	private static void collectExactNpcNames(
		final SlayerRoutePredicate predicate,
		final Set<String> result)
	{
		if (predicate == null)
		{
			return;
		}
		if (predicate.getKind() == SlayerRoutePredicate.Kind.EXACT_NPC)
		{
			for (final String name : predicate.getExactNpcNames())
			{
				result.add(normalize(name));
			}
		}
		for (final SlayerRoutePredicate child : predicate.getChildren())
		{
			collectExactNpcNames(child, result);
		}
	}

	private static boolean anySpatialAreaContains(
		final SlayerRoutePredicate predicate,
		final WorldPoint point)
	{
		if (predicate == null)
		{
			return false;
		}
		if ((predicate.getKind() == SlayerRoutePredicate.Kind.WORLD_AREA
			|| predicate.getKind() == SlayerRoutePredicate.Kind.TEMPLATE_AREA)
			&& predicate.getArea() != null && predicate.getArea().contains(point))
		{
			return true;
		}
		for (final SlayerRoutePredicate child : predicate.getChildren())
		{
			if (anySpatialAreaContains(child, point))
			{
				return true;
			}
		}
		return false;
	}

	private static void validatePlanRetention(
		final SlayerRouteReleaseManifest.Entry entry,
		final SlayerRoutePlan plan,
		final String identity,
		final List<String> failures)
	{
		final SlayerRoutePlan.ActivityRetention retention =
			plan.getActivityRetention();
		if (retention == null || retention.getAreas() == null
			|| retention.getAreas().isEmpty())
		{
			failures.add("route plan activity retention is missing: " + identity);
			return;
		}
		for (final SlayerRoutePredicate.SpatialArea area : retention.getAreas())
		{
			if (area == null || area.getMinX() > area.getMaxX()
				|| area.getMinY() > area.getMaxY() || area.getPlane() < 0
				|| area.getPlane() > 3)
			{
				failures.add("route plan has invalid activity retention: " + identity);
			}
		}

		final SlayerRoutePredicate terminal =
			plan.getTerminalSpec().getCompletionPredicate();
		if (!hasTerminalProofSeparateFromRetention(
			terminal, retention.getAreas()))
		{
			failures.add(
				"route plan reuses broad activity retention as terminal proof: "
					+ identity
			);
		}

		if (entry.getKind()
			== SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT)
		{
			return;
		}

		final List<SlayerRoutePredicate.SpatialArea> terminalAreas =
			new ArrayList<>();
		collectSpatialAreas(terminal, terminalAreas);
		for (final SlayerRoutePredicate.SpatialArea terminalArea : terminalAreas)
		{
			if (!isCoveredByRetention(terminalArea, retention.getAreas()))
			{
				failures.add(
					"boss terminal area is outside activity retention: "
						+ identity + " -> " + areaText(terminalArea)
				);
			}
		}

		if (terminalAreas.isEmpty()
			&& (terminal.containsKind(
					SlayerRoutePredicate.Kind.VISIBLE_WIDGET_COMPONENT)
				|| terminal.containsKind(
					SlayerRoutePredicate.Kind.VISIBLE_WIDGET_GROUP)
				|| hasDistinctPersistentTerminalObject(plan, terminal))
			&& !hasSeparateEncounterRetention(
				entry.getExpectedAccess(), retention.getAreas()))
		{
			failures.add(
				"boss non-spatial terminal has no retained encounter scene: "
					+ identity
			);
		}
	}

	private static void collectSpatialAreas(
		final SlayerRoutePredicate predicate,
		final List<SlayerRoutePredicate.SpatialArea> result)
	{
		if (predicate == null)
		{
			return;
		}
		if ((predicate.getKind() == SlayerRoutePredicate.Kind.WORLD_AREA
			|| predicate.getKind() == SlayerRoutePredicate.Kind.TEMPLATE_AREA)
			&& predicate.getArea() != null)
		{
			result.add(predicate.getArea());
		}
		for (final SlayerRoutePredicate child : predicate.getChildren())
		{
			collectSpatialAreas(child, result);
		}
	}

	private static boolean isCoveredByRetention(
		final SlayerRoutePredicate.SpatialArea terminal,
		final List<SlayerRoutePredicate.SpatialArea> retentionAreas)
	{
		for (final SlayerRoutePredicate.SpatialArea retained : retentionAreas)
		{
			if (retained != null && terminal != null
				&& retained.getCoordinateSpace() == terminal.getCoordinateSpace()
				&& retained.getPlane() == terminal.getPlane()
				&& retained.getMinX() <= terminal.getMinX()
				&& retained.getMaxX() >= terminal.getMaxX()
				&& retained.getMinY() <= terminal.getMinY()
				&& retained.getMaxY() >= terminal.getMaxY())
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasSeparateEncounterRetention(
		final SlayerRouteReleaseManifest.Endpoint access,
		final List<SlayerRoutePredicate.SpatialArea> retentionAreas)
	{
		if (access == null)
		{
			return false;
		}
		for (final SlayerRoutePredicate.SpatialArea retained : retentionAreas)
		{
			if (retained == null)
			{
				continue;
			}
			if (retained.getCoordinateSpace()
				== SlayerRouteEvidence.CoordinateSpace.TEMPLATE)
			{
				return true;
			}
			if (retained.getPlane() != access.getPlane()
				|| (retained.getMinY() >>> 12) != (access.getY() >>> 12)
				|| (retained.getMaxY() >>> 12) != (access.getY() >>> 12))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasTerminalProofSeparateFromRetention(
		final SlayerRoutePredicate predicate,
		final List<SlayerRoutePredicate.SpatialArea> retentionAreas)
	{
		if (predicate == null)
		{
			return false;
		}
		switch (predicate.getKind())
		{
			case EXACT_NPC:
			case OBSERVED_OBJECT_INTERACTION:
			case VISIBLE_WIDGET_COMPONENT:
			case VISIBLE_WIDGET_GROUP:
			case COORDINATE_LAYER_TRANSITION:
				return true;
			case WORLD_AREA:
			case TEMPLATE_AREA:
				for (final SlayerRoutePredicate.SpatialArea retained : retentionAreas)
				{
					if (sameArea(predicate.getArea(), retained))
					{
						return false;
					}
				}
				return true;
			case ANY_OF:
			case ALL_OF:
				for (final SlayerRoutePredicate child : predicate.getChildren())
				{
					if (hasTerminalProofSeparateFromRetention(
						child, retentionAreas))
					{
						return true;
					}
				}
				return false;
			default:
				return false;
		}
	}

	private static boolean sameArea(
		final SlayerRoutePredicate.SpatialArea first,
		final SlayerRoutePredicate.SpatialArea second)
	{
		return first != null && second != null
			&& first.getCoordinateSpace() == second.getCoordinateSpace()
			&& first.getMinX() == second.getMinX()
			&& first.getMaxX() == second.getMaxX()
			&& first.getMinY() == second.getMinY()
			&& first.getMaxY() == second.getMaxY()
			&& first.getPlane() == second.getPlane();
	}

	private static void validateEntry(
		final SlayerRouteReleaseManifest.Entry entry,
		final ProfileLookup lookup,
		final PlanLookup planLookup,
		final List<String> failures)
	{
		if (entry == null)
		{
			return;
		}

		final String identity = rowIdentity(entry);
		final String taskName = entry.getKind()
			== SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT
				? entry.getAssignmentName() : entry.getEncounterName();
		final boolean boss = entry.getKind()
			!= SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT;
		final boolean validPlan = validateExplicitPlan(
			entry, taskName, boss, planLookup, failures
		);

		validateEntryShape(entry, identity, validPlan, failures);

		final boolean explicit = lookup.hasExplicitProfile(
			taskName, entry.getLocation(), boss
		);
		final SlayerRouteCatalog.RouteProfile profile = lookup.resolve(
			taskName, entry.getLocation(), boss
		);
		if (!explicit)
		{
			if (!validPlan)
			{
				failures.add("missing exact authored profile: " + identity);
				if (profile != null)
				{
					failures.add(
						"generic resolve fallback reached by release row: " + identity
							+ " -> " + profile.getMode()
					);
				}
			}
			return;
		}
		if (profile == null)
		{
			failures.add("exact authored profile resolves null: " + identity);
			return;
		}

		if (profile.isBoss() != boss)
		{
			failures.add("boss/assignment profile identity mismatch: " + identity);
		}
		if (!routeTaskKey(profile.getTaskName()).equals(routeTaskKey(taskName))
			|| !normalize(profile.getLocation()).equals(normalize(entry.getLocation())))
		{
			failures.add(
				"resolved profile identity drift: " + identity + " -> "
					+ profile.getTaskName() + " @ " + profile.getLocation()
			);
		}
		if (!hasExpectedNpcAlias(profile, taskName))
		{
			failures.add("profile has no exact target NPC alias: " + identity);
		}
		if ((profile.requiresLoadedNpc() || profile.isAccessThenNpc())
			&& !validPlan)
		{
			failures.add(
				"release profile has no authored static terminal: " + identity
					+ " -> " + profile.getMode()
			);
		}

		final SlayerRouteReleaseManifest.Endpoint expectedTerminal =
			entry.getExpectedTerminal();
		final SlayerRouteReleaseManifest.Endpoint expectedAccess =
			entry.getExpectedAccess();
		/*
		 * ACCESS_THEN_NPC/NPC_ONLY profiles are intentionally entry/discovery
		 * shims: they cannot encode a separate static terminal.  A fully validated
		 * exact plan may own that terminal contract.  Direct and staged profiles
		 * still have to agree with the independent manifest even when a plan exists.
		 */
		final boolean exactPlanOwnsLegacyTerminal = validPlan
			&& (profile.isAccessThenNpc() || profile.requiresLoadedNpc());
		if (expectedTerminal != null && !exactPlanOwnsLegacyTerminal
			&& !samePoint(profile.getPrimaryDestination(), expectedTerminal))
		{
			failures.add(
				"terminal coordinate mismatch: " + identity + " expected="
					+ pointText(expectedTerminal) + " actual="
					+ pointText(profile.getPrimaryDestination())
			);
		}

		/*
		 * An exact profile may author an encounter-specific access point even when
		 * another encounter shares the same display location.  The location-only
		 * access map cannot represent both (for example regular Cave kraken versus
		 * Kraken, or Lesser nagua versus Amoxliatl).  A non-null profile access is
		 * therefore the stronger, exact-key contract; use the shared map only when
		 * the exact profile does not carry one.
		 */
		final WorldPoint profileAccess = profile.getSurfaceAccess();
		final boolean hasExactProfileAccess = profileAccess != null;
		final WorldPoint authoredAccess = hasExactProfileAccess
			? profileAccess : lookup.findAccess(entry.getLocation());
		/*
		 * Open-world boss aliases intentionally share broad display locations such
		 * as "Wilderness".  A location-only access map cannot represent several
		 * different exact boss tiles.  Those rows are instead held to the stronger
		 * profile-terminal check above plus access == terminal below.  An exact
		 * profile access, when present, remains subject to this check.
		 */
		if ((hasExactProfileAccess
				|| entry.getTerminalContract()
					!= SlayerRouteReleaseManifest.TerminalContract.OPEN_WORLD_EXACT_TARGET)
			&& expectedAccess != null
			&& !samePoint(authoredAccess, expectedAccess))
		{
			failures.add(
				"access coordinate mismatch: " + identity + " expected="
					+ pointText(expectedAccess) + " actual=" + pointText(authoredAccess)
			);
		}

		validateModeContract(entry, profile, identity, validPlan, failures);
		validateStageGraph(entry, profile, identity, validPlan, failures);
		if (boss)
		{
			validateBossIsolation(entry, profile, identity, lookup, failures);
		}
	}

	private static void validateEntryShape(
		final SlayerRouteReleaseManifest.Entry entry,
		final String identity,
		final boolean validPlan,
		final List<String> failures)
	{
		if ((!entry.isReleaseReady() || !entry.hasEstablishedTerminal())
			&& !validPlan)
		{
			failures.add(
				"unresolved terminal: " + identity + " - "
					+ entry.getUnresolvedReason()
			);
		}
		if (entry.getExpectedAccess() == null)
		{
			failures.add("unresolved access coordinate: " + identity);
		}
		else
		{
			validateEndpoint(entry.getExpectedAccess(), "access", identity, failures);
		}
		if (entry.getExpectedTerminal() != null)
		{
			validateEndpoint(
				entry.getExpectedTerminal(), "terminal", identity, failures
			);
		}
		if (normalize(entry.getWikiPageTitle()).isEmpty())
		{
			failures.add("manifest row has no Wiki source title: " + identity);
		}

		final SlayerRouteReleaseManifest.TerminalMechanism expectedMechanism =
			expectedMechanism(
				entry.getTerminalContract(), entry.getExpectedTerminal()
			);
		if (entry.getTerminalMechanism() != expectedMechanism)
		{
			failures.add(
				"terminal mechanism mismatch: " + identity + " expected="
					+ expectedMechanism + " actual=" + entry.getTerminalMechanism()
			);
		}
		if (entry.getAreaType() == SlayerRouteReleaseManifest.AreaType.OPEN_WORLD
			&& entry.getTerminalContract()
				!= SlayerRouteReleaseManifest.TerminalContract.OPEN_WORLD_EXACT_TARGET)
		{
			failures.add("open-world row has a non-open-world contract: " + identity);
		}
		if (entry.getTerminalContract()
				== SlayerRouteReleaseManifest.TerminalContract.OPEN_WORLD_EXACT_TARGET
			&& entry.getAreaType() != SlayerRouteReleaseManifest.AreaType.OPEN_WORLD)
		{
			failures.add("open-world contract has a non-open-world area: " + identity);
		}
		if (entry.getAreaType() == SlayerRouteReleaseManifest.AreaType.WAVE
			&& entry.getTerminalContract()
				!= SlayerRouteReleaseManifest.TerminalContract.WAVE_ENTRANCE_OR_WIDGET_STOP)
		{
			failures.add("wave row has a non-wave terminal contract: " + identity);
		}
	}

	private static void validateModeContract(
		final SlayerRouteReleaseManifest.Entry entry,
		final SlayerRouteCatalog.RouteProfile profile,
		final String identity,
		final boolean validPlan,
		final List<String> failures)
	{
		final boolean crossLayer = entry.getExpectedAccess() != null
			&& entry.getExpectedTerminal() != null
			&& !sameCoordinateLayer(
				entry.getExpectedAccess(), entry.getExpectedTerminal()
			);
		final boolean enclosedTransition = entry.getAreaType()
			== SlayerRouteReleaseManifest.AreaType.CAVE
			|| entry.getAreaType() == SlayerRouteReleaseManifest.AreaType.DUNGEON
			|| entry.getAreaType() == SlayerRouteReleaseManifest.AreaType.MULTI_FLOOR;
		if (enclosedTransition && !hasFullTransitionContract(profile)
			&& !validPlan)
		{
			failures.add(
				"cave/dungeon route has only a terminal point, not a full transition contract: "
					+ identity + " -> " + profile.getMode()
			);
		}

		switch (entry.getTerminalContract())
		{
			case OPEN_WORLD_EXACT_TARGET:
				if (entry.getExpectedAccess() != null
					&& entry.getExpectedTerminal() != null
					&& !samePoint(entry.getExpectedAccess(), entry.getExpectedTerminal()))
				{
					failures.add(
						"open-world access and terminal differ: " + identity
					);
				}
				if (!isDirectVerifiedMode(profile.getMode()))
				{
					failures.add(
						"open-world profile is not direct and exact: " + identity
							+ " -> " + profile.getMode()
					);
				}
				break;
			case FULL_TRANSPORT_CHAIN_EXACT_INTERIOR:
				if (crossLayer && !isCrossLayerCapable(profile.getMode())
					&& !validPlan)
				{
					failures.add(
						"cross-layer route lacks an authored transport/stage mode: "
							+ identity + " -> " + profile.getMode()
					);
				}
				break;
			case MANUAL_STAGE_EXACT_CHECKPOINT:
				if (!profile.isStaged() && !validPlan)
				{
					failures.add(
						"manual checkpoint route is not explicitly staged: "
							+ identity + " -> " + profile.getMode()
					);
				}
				else if (!validPlan && (profile.getTransitionSpec() == null
					|| !profile.getTransitionSpec().hasObjectNameHints()))
				{
					failures.add(
						"manual checkpoint has no named interaction contract: "
							+ identity
					);
				}
				break;
			case INSTANCE_ENTRANCE_OR_WIDGET_STOP:
			case INSTANCE_REGION_STOP:
			case WAVE_ENTRANCE_OR_WIDGET_STOP:
				if (!profile.isBoss())
				{
					failures.add("instance/wave terminal is not boss-strict: " + identity);
				}
				if (crossLayer && !profile.isStaged() && !validPlan)
				{
					failures.add(
						"cross-layer instance/wave route is not staged: " + identity
							+ " -> " + profile.getMode()
					);
				}
				break;
			default:
				failures.add("unhandled terminal contract: " + identity);
				break;
		}
	}

	private static void validateStageGraph(
		final SlayerRouteReleaseManifest.Entry entry,
		final SlayerRouteCatalog.RouteProfile profile,
		final String identity,
		final boolean validPlan,
		final List<String> failures)
	{
		final WorldPoint terminal = toWorldPoint(entry.getExpectedTerminal());
		final WorldPoint access = toWorldPoint(entry.getExpectedAccess());
		/* Access/discovery legacy profiles do not encode the interior graph. Once
		 * the exact declarative plan has independently proved access, transitions,
		 * terminal, and retention, re-running legacy terminal graph assertions is
		 * duplicative and produces false mismatches such as Revenant Caves. */
		if (validPlan && (profile.isAccessThenNpc() || profile.requiresLoadedNpc()))
		{
			return;
		}
		if (terminal == null)
		{
			return;
		}

		if (!profile.hasStaticDestination())
		{
			failures.add("profile has no deterministic terminal: " + identity);
			return;
		}
		if (profile.getRouteTargets(null).isEmpty())
		{
			failures.add("profile has no bank-start route targets: " + identity);
		}
		if (profile.getRouteTargets(terminal).isEmpty())
		{
			failures.add("profile has no terminal route targets: " + identity);
		}
		if (!profile.isInsideEncounterArea(terminal))
		{
			failures.add("terminal is outside its encounter stop area: " + identity);
		}
		if (!terminal.equals(profile.getRouteDestination(terminal)))
		{
			failures.add("terminal routes back out of the encounter: " + identity);
		}
		if (profile.getArrivalRadius(terminal) <= 0)
		{
			failures.add("terminal has a non-positive arrival radius: " + identity);
		}

		if (!profile.isStaged())
		{
			if (!terminal.equals(profile.getRouteDestination(null)))
			{
				failures.add(
					"non-staged profile does not start toward its exact terminal: "
						+ identity
				);
			}
			return;
		}

		if (access == null)
		{
			failures.add("staged profile has no manifest access point: " + identity);
			return;
		}
		if (profile.getTransitionSpec() == null
			|| profile.getTransitionSpec().getActions().isEmpty())
		{
			failures.add("staged profile has no interaction actions: " + identity);
		}
		if (profile.getTransitionInstruction().isEmpty())
		{
			failures.add("staged profile has no transition instruction: " + identity);
		}
		if (profile.isInsideEncounterArea(access))
		{
			failures.add(
				"staged access is already inside the terminal stop area: " + identity
			);
		}
		if (!access.equals(profile.getRouteDestination(null))
			|| !access.equals(profile.getRouteDestination(access)))
		{
			failures.add(
				"staged graph does not begin and remain at its access checkpoint: "
					+ identity
			);
		}

		if (!sameCoordinateLayer(access, terminal))
		{
			final WorldPoint intermediateInterior = new WorldPoint(
				terminal.getX() + 512, terminal.getY(), terminal.getPlane()
			);
			if (access.equals(profile.getRouteDestination(intermediateInterior)))
			{
				failures.add(
					"cross-layer intermediate stage cycles back to the outer access: "
						+ identity
				);
			}
		}
	}

	private static void validateBossIsolation(
		final SlayerRouteReleaseManifest.Entry entry,
		final SlayerRouteCatalog.RouteProfile bossProfile,
		final String identity,
		final ProfileLookup lookup,
		final List<String> failures)
	{
		if (!isBossMode(bossProfile.getMode()))
		{
			failures.add("boss profile uses a standard route mode: " + identity);
		}

		for (final String assignment : entry.getEligibleAssignments())
		{
			/* Direct-boss manifests use the encounter itself as their sole eligible
			 * assignment.  Resolving that same identity with boss=false only exercises
			 * RouteCatalog's standard fallback; it is not an ordinary Slayer route from
			 * which a boss alias could leak.  Selectable bosses still test every distinct
			 * ordinary assignment below. */
			if (routeTaskKey(assignment).equals(
				routeTaskKey(entry.getEncounterName())))
			{
				continue;
			}
			final SlayerRouteCatalog.RouteProfile standard = lookup.resolve(
				assignment, entry.getLocation(), false
			);
			if (standard == null)
			{
				continue;
			}
			for (final String bossAlias
				: SlayerTaskNpcCatalog.aliasesFor(entry.getEncounterName()))
			{
				if (standard.matchesNpc(bossAlias))
				{
					failures.add(
						"boss NPC leaked into standard route: " + identity
							+ " via " + assignment + " -> " + bossAlias
					);
					break;
				}
			}
		}
	}

	private static boolean hasExpectedNpcAlias(
		final SlayerRouteCatalog.RouteProfile profile,
		final String taskName)
	{
		for (final String alias : SlayerTaskNpcCatalog.aliasesFor(taskName))
		{
			if (profile.matchesNpc(alias))
			{
				return true;
			}
		}
		return profile.matchesNpc(taskName);
	}

	private static boolean isDirectVerifiedMode(
		final SlayerRouteCatalog.RouteMode mode)
	{
		return mode == SlayerRouteCatalog.RouteMode.OPEN_WORLD
			|| mode == SlayerRouteCatalog.RouteMode.BOSS_OPEN_WORLD;
	}

	private static boolean isCrossLayerCapable(
		final SlayerRouteCatalog.RouteMode mode)
	{
		return mode == SlayerRouteCatalog.RouteMode.TRANSPORT_CHAIN_INTERIOR
			|| mode == SlayerRouteCatalog.RouteMode.STAGED_INTERIOR
			|| mode == SlayerRouteCatalog.RouteMode.BOSS_TRANSPORT_CHAIN_INTERIOR
			|| mode == SlayerRouteCatalog.RouteMode.BOSS_STAGED_INTERIOR;
	}

	private static boolean hasFullTransitionContract(
		final SlayerRouteCatalog.RouteProfile profile)
	{
		return profile != null
			&& (profile.getMode()
				== SlayerRouteCatalog.RouteMode.TRANSPORT_CHAIN_INTERIOR
				|| profile.getMode() == SlayerRouteCatalog.RouteMode.STAGED_INTERIOR
				|| profile.getMode()
					== SlayerRouteCatalog.RouteMode.BOSS_TRANSPORT_CHAIN_INTERIOR
				|| profile.getMode() == SlayerRouteCatalog.RouteMode.BOSS_STAGED_INTERIOR);
	}

	private static boolean isBossMode(final SlayerRouteCatalog.RouteMode mode)
	{
		return mode == SlayerRouteCatalog.RouteMode.BOSS_OPEN_WORLD
			|| mode == SlayerRouteCatalog.RouteMode.BOSS_VERIFIED_INTERIOR
			|| mode == SlayerRouteCatalog.RouteMode.BOSS_TRANSPORT_CHAIN_INTERIOR
			|| mode == SlayerRouteCatalog.RouteMode.BOSS_STAGED_INTERIOR
			|| mode == SlayerRouteCatalog.RouteMode.BOSS_ACCESS_THEN_NPC
			|| mode == SlayerRouteCatalog.RouteMode.BOSS_NPC_ONLY;
	}

	private static SlayerRouteReleaseManifest.TerminalMechanism expectedMechanism(
		final SlayerRouteReleaseManifest.TerminalContract contract,
		final SlayerRouteReleaseManifest.Endpoint expectedTerminal)
	{
		switch (contract)
		{
			case OPEN_WORLD_EXACT_TARGET:
				return SlayerRouteReleaseManifest.TerminalMechanism.STATIC_WORLD_POINT;
			case FULL_TRANSPORT_CHAIN_EXACT_INTERIOR:
				return expectedTerminal == null
					? SlayerRouteReleaseManifest.TerminalMechanism.EXACT_NPC_DISCOVERY
					: SlayerRouteReleaseManifest.TerminalMechanism.STATIC_WORLD_POINT;
			case MANUAL_STAGE_EXACT_CHECKPOINT:
				return SlayerRouteReleaseManifest.TerminalMechanism.SPECIAL_HANDOFF;
			case INSTANCE_ENTRANCE_OR_WIDGET_STOP:
			case WAVE_ENTRANCE_OR_WIDGET_STOP:
				return SlayerRouteReleaseManifest.TerminalMechanism.OBJECT_OR_WIDGET_STOP;
			case INSTANCE_REGION_STOP:
				return SlayerRouteReleaseManifest.TerminalMechanism.REGION_ARRIVAL_STOP;
			default:
				throw new IllegalStateException("Unhandled terminal contract: " + contract);
		}
	}

	private static void validateEndpoint(
		final SlayerRouteReleaseManifest.Endpoint endpoint,
		final String label,
		final String identity,
		final List<String> failures)
	{
		if (endpoint.getX() <= 0 || endpoint.getY() <= 0
			|| endpoint.getPlane() < 0 || endpoint.getPlane() > 3)
		{
			failures.add(
				"invalid " + label + " coordinate: " + identity + " -> "
					+ pointText(endpoint)
			);
		}
	}

	private static Map<String, Set<String>> addToSetMap(
		final Map<String, Set<String>> map,
		final String key,
		final String value)
	{
		Set<String> values = map.get(key);
		if (values == null)
		{
			values = new LinkedHashSet<>();
			map.put(key, values);
		}
		values.add(value);
		return map;
	}

	private static void compareExact(
		final String label,
		final Set<String> expected,
		final Set<String> observed,
		final List<String> failures)
	{
		if (expected.equals(observed))
		{
			return;
		}
		final Set<String> missing = new LinkedHashSet<>(expected);
		missing.removeAll(observed);
		final Set<String> unexpected = new LinkedHashSet<>(observed);
		unexpected.removeAll(expected);
		failures.add(
			label + " parity mismatch; missing=" + missing
				+ ", unexpected=" + unexpected
		);
	}

	private static Set<String> normalizedIdentities(
		final Collection<String> values)
	{
		final Set<String> result = new LinkedHashSet<>();
		if (values != null)
		{
			for (final String value : values)
			{
				final String key = identityKey(value);
				if (!key.isEmpty())
				{
					result.add(key);
				}
			}
		}
		return result;
	}

	private static String identityKey(final String value)
	{
		String key = normalize(value).replaceFirst("^the\\s+", "");
		/* RuneLite's task title is an alias for the selectable Kraken encounter. */
		if (key.equals("cave kraken boss"))
		{
			return "kraken";
		}
		return key;
	}

	/** Mirrors RouteCatalog's route-key singularization, but is deliberately
	 * kept separate from canonical manifest identity.  Direct assignment aliases
	 * such as Crazy Archaeologist/Archaeologists must remain independently present
	 * in the manifest even though both intentionally resolve one authored route. */
	private static String routeTaskKey(final String value)
	{
		final String normalized = identityKey(value);
		if (normalized.endsWith("wolves"))
		{
			return normalized.substring(0, normalized.length() - 6) + "wolf";
		}
		if (normalized.endsWith("elves"))
		{
			return normalized.substring(0, normalized.length() - 5) + "elf";
		}
		if (normalized.endsWith("dwarves"))
		{
			return normalized.substring(0, normalized.length() - 7) + "dwarf";
		}
		if (normalized.endsWith("men"))
		{
			return normalized.substring(0, normalized.length() - 3) + "man";
		}
		if (normalized.endsWith("ies") && normalized.length() > 4)
		{
			return normalized.substring(0, normalized.length() - 3) + "y";
		}
		if (normalized.endsWith("s")
			&& !normalized.endsWith("ss")
			&& !normalized.endsWith("us")
			&& !normalized.endsWith("is")
			&& normalized.length() > 3)
		{
			return normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	private static String rowKey(final SlayerRouteReleaseManifest.Entry entry)
	{
		return entry.getKind() + "|" + normalize(entry.getAssignmentName())
			+ "|" + normalize(entry.getEncounterName())
			+ "|" + normalize(entry.getLocation());
	}

	private static String rowIdentity(final SlayerRouteReleaseManifest.Entry entry)
	{
		return entry.getKind() + "|"
			+ (entry.getKind() == SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT
				? entry.getAssignmentName() : entry.getEncounterName())
			+ "|" + entry.getLocation();
	}

	private static boolean samePoint(
		final WorldPoint actual,
		final SlayerRouteReleaseManifest.Endpoint expected)
	{
		return actual != null && expected != null
			&& actual.getX() == expected.getX()
			&& actual.getY() == expected.getY()
			&& actual.getPlane() == expected.getPlane();
	}

	private static boolean samePoint(
		final SlayerRouteReleaseManifest.Endpoint first,
		final SlayerRouteReleaseManifest.Endpoint second)
	{
		return first != null && second != null
			&& first.getX() == second.getX()
			&& first.getY() == second.getY()
			&& first.getPlane() == second.getPlane();
	}

	private static boolean sameCoordinateLayer(
		final SlayerRouteReleaseManifest.Endpoint first,
		final SlayerRouteReleaseManifest.Endpoint second)
	{
		return first != null && second != null
			&& first.getPlane() == second.getPlane()
			&& (first.getY() >>> 12) == (second.getY() >>> 12);
	}

	private static boolean sameCoordinateLayer(
		final WorldPoint first,
		final WorldPoint second)
	{
		return first != null && second != null
			&& first.getPlane() == second.getPlane()
			&& (first.getY() >>> 12) == (second.getY() >>> 12);
	}

	private static WorldPoint toWorldPoint(
		final SlayerRouteReleaseManifest.Endpoint endpoint)
	{
		return endpoint == null ? null : new WorldPoint(
			endpoint.getX(), endpoint.getY(), endpoint.getPlane()
		);
	}

	private static String pointText(
		final SlayerRouteReleaseManifest.Endpoint endpoint)
	{
		return endpoint == null ? "<unknown>" : endpoint.getX() + ","
			+ endpoint.getY() + "," + endpoint.getPlane();
	}

	private static String pointText(final WorldPoint point)
	{
		return point == null ? "<missing>" : point.getX() + ","
			+ point.getY() + "," + point.getPlane();
	}

	private static String areaText(
		final SlayerRoutePredicate.SpatialArea area)
	{
		return area == null ? "<missing>" : area.getCoordinateSpace() + "["
			+ area.getMinX() + ".." + area.getMaxX() + ","
			+ area.getMinY() + ".." + area.getMaxY() + ","
			+ area.getPlane() + "]";
	}

	private static String normalize(final String value)
	{
		return value == null ? "" : value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceAll("\\s+", " ");
	}

	private static List<String> immutable(final List<String> values)
	{
		return Collections.unmodifiableList(new ArrayList<>(values));
	}

	private static final class BossContract
	{
		private final String displayName;
		private final String location;
		private final Set<String> assignments = new LinkedHashSet<>();

		private BossContract(final String displayName, final String location)
		{
			this.displayName = displayName;
			this.location = location;
		}
	}
}
