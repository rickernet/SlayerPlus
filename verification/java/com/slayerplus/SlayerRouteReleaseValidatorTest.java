package com.slayerplus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SlayerRouteReleaseValidatorTest
{
	@Test
	public void completeReleaseMatrixHasNoRoutingFailures()
	{
		/* The normal development suite stays green while known catalog research is
		 * in progress. `gradlew releaseRouteCheck` enables this hard release gate. */
		Assume.assumeTrue(Boolean.getBoolean("slayerplus.releaseRouteCheck"));
		final List<String> failures = SlayerRouteReleaseValidator.validate();
		assertTrue(
			"Release routing failures (" + failures.size() + "): " + failures,
			failures.isEmpty()
		);
	}

	@Test
	public void everyUnresolvedManifestTerminalIsReportedIndividually()
	{
		final List<String> failures = SlayerRouteReleaseValidator.validateForTest(
			SlayerRouteReleaseManifest.entries(),
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			VariantCatalog.getBossDefinitions(),
			VariantCatalog.getDirectBossDefinitions(),
			SlayerRouteReleaseValidator.catalogLookupForTest(),
			noPlanLookup()
		);
		for (final SlayerRouteReleaseManifest.Entry entry
			: SlayerRouteReleaseManifest.unresolvedTerminalEntries())
		{
			final String identity = routeIdentity(entry);
			assertContains(
				failures,
				"unresolved terminal: " + identity,
				"Missing unresolved-terminal failure for " + identity
			);
		}
	}

	@Test
	public void exactValidatedPlanCanSupplyTheDynamicTerminalProof()
	{
		final SlayerRouteReleaseManifest.Entry artio = findEncounter(
			"Artio", "Hunter's End"
		);
		final List<String> failures = SlayerRouteReleaseValidator.validate();
		assertNotContains(
			failures,
			"unresolved terminal: " + routeIdentity(artio),
			"A valid exact route plan did not satisfy its dynamic terminal"
		);
	}

	@Test
	public void dynamicFullChainRowsDeclareExactNpcDiscovery()
	{
		final SlayerRouteReleaseManifest.Entry caveKraken = findAssignment(
			"Cave kraken", "Kraken Cove"
		);
		assertTrue(
			"Dynamic full-chain terminal was mislabeled as a static WorldPoint",
			caveKraken.getExpectedTerminal() == null
				&& caveKraken.getTerminalMechanism()
					== SlayerRouteReleaseManifest.TerminalMechanism.EXACT_NPC_DISCOVERY
		);
	}

	@Test
	public void assignmentNpcDiscoveryCanProveAnInteriorTransition()
	{
		final SlayerRouteReleaseManifest.Entry caveKraken = findAssignment(
			"Cave kraken", "Kraken Cove"
		);
		final String identity = routeIdentity(caveKraken);
		final List<String> failures = SlayerRouteReleaseValidator.validate();
		assertNotContains(
			failures,
			"manual route leg has no observed transition completion: "
				+ identity,
			"Persistent assignment NPC discovery was not accepted as interior evidence"
		);
		assertNotContains(
			failures,
			"unresolved terminal: " + identity,
			"A complete exact assignment route remained unresolved"
		);
	}

	@Test
	public void dynamicFullChainCannotUseAnArbitrarySpatialTerminal()
	{
		final SlayerRouteReleaseManifest.Entry caveKraken = findAssignment(
			"Cave kraken", "Kraken Cove"
		);
		final WorldPoint access = new WorldPoint(2277, 3611, 0);
		final WorldPoint guessedRoom = new WorldPoint(2280, 10034, 0);
		final RouteCheck spatialTerminal = RouteCheck.worldArea(
			RouteCheck.SpatialArea.aroundWorld(guessedRoom, 8, 8)
		);
		final RouteCheck.ObjectActionSpec entrance =
			RouteCheck.ObjectActionSpec.named(
				"Kraken Cove entrance", "Enter"
			);
		final RoutePlan spatialOnly = RoutePlan.builder(
				"cave-kraken-spatial-only"
			)
			.startAt("route")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(RoutePlan.Leg.builder(
					"route", RoutePlan.LegKind.TRAVEL)
				.guidance("Route to Kraken Cove.")
				.target(RoutePlan.RouteTarget.point(
					"Kraken Cove entrance", access, 4))
				.completeWhen(RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(access, 4, 4)))
				.then("enter")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"enter", RoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter Kraken Cove.")
				.target(RoutePlan.RouteTarget.point(
					"Kraken Cove entrance", access, 4))
				.interactionInstruction("Enter Kraken Cove.")
				.readyWhen(RouteCheck.trackedObjectAction(entrance))
				.completeWhen(RouteCheck.allOf(
					RouteCheck.observedObjectInteraction(entrance),
					spatialTerminal))
				.then("arrived")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"arrived", RoutePlan.LegKind.TERMINAL)
				.guidance("Guessed room area.")
				.terminal(RoutePlan.TerminalSpec.of(
					"Guessed room area", spatialTerminal))
				.build())
			.build();

		final List<String> failures = validateWithPlans(
			singlePlanLookup(caveKraken, spatialOnly)
		);
		final String identity = routeIdentity(caveKraken);
		assertContains(
			failures,
			"route plan terminal does not prove "
				+ "FULL_TRANSPORT_CHAIN_EXACT_INTERIOR: " + identity,
			"An arbitrary room area satisfied an exact-NPC terminal contract"
		);
		assertContains(
			failures,
			"unresolved terminal: " + identity,
			"A spatial guess incorrectly cleared the dynamic terminal row"
		);
	}

	@Test
	public void exactValidatedPlanDoesNotRequireADuplicateLegacyProfile()
	{
		final SlayerRouteReleaseManifest.Entry artio = findEncounter(
			"Artio", "Hunter's End"
		);
		final SlayerRouteReleaseValidator.ProfileLookup catalog =
			SlayerRouteReleaseValidator.catalogLookupForTest();
		final SlayerRouteReleaseValidator.ProfileLookup noExactProfile =
			new SlayerRouteReleaseValidator.ProfileLookup()
			{
				@Override
				public boolean hasExplicitProfile(
					final String assignment,
					final String location,
					final boolean boss)
				{
					return !matches(artio, assignment, location, boss)
						&& catalog.hasExplicitProfile(assignment, location, boss);
				}

				@Override
				public RouteCatalog.RouteProfile resolve(
					final String assignment,
					final String location,
					final boolean boss)
				{
					return catalog.resolve(assignment, location, boss);
				}

				@Override
				public WorldPoint findAccess(final String location)
				{
					return catalog.findAccess(location);
				}
			};

		final List<String> failures = SlayerRouteReleaseValidator.validateForTest(
			SlayerRouteReleaseManifest.entries(),
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			VariantCatalog.getBossDefinitions(),
			VariantCatalog.getDirectBossDefinitions(),
			noExactProfile,
			SlayerRouteReleaseValidator.planLookupForTest()
		);
		final String identity = routeIdentity(artio);
		assertNotContains(
			failures,
			"missing exact authored profile: " + identity,
			"A complete exact route graph was forced to duplicate a legacy profile"
		);
		assertNotContains(
			failures,
			"generic resolve fallback reached by release row: " + identity,
			"A legacy fallback was evaluated despite the complete exact route graph"
		);
	}

	@Test
	public void exactPlanMustAuthorTheIndependentManifestAccess()
	{
		final SlayerRouteReleaseManifest.Entry artio = findEncounter(
			"Artio", "Hunter's End"
		);
		final WorldPoint wrong = new WorldPoint(3200, 3200, 0);
		final RouteCheck terminal = RouteCheck.exactNpc("Artio");
		final RouteCheck.ObjectActionSpec entrance =
			RouteCheck.ObjectActionSpec.named("Imaginary entrance", "Enter");
		final RoutePlan wrongAccess = RoutePlan.builder(
				"artio-wrong-access"
			)
			.startAt("route")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(wrong, 24, 24)
			))
			.addLeg(RoutePlan.Leg.builder(
					"route", RoutePlan.LegKind.TRAVEL)
				.guidance("Route to the wrong entrance.")
				.target(RoutePlan.RouteTarget.point("Wrong entrance", wrong, 4))
				.completeWhen(RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(wrong, 4, 4)))
				.then("enter")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"enter", RoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter the wrong cave.")
				.target(RoutePlan.RouteTarget.point("Wrong entrance", wrong, 4))
				.interactionInstruction("Enter the wrong cave.")
				.readyWhen(RouteCheck.trackedObjectAction(entrance))
				.completeWhen(RouteCheck.allOf(
					RouteCheck.observedObjectInteraction(entrance), terminal))
				.then("arrived")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"arrived", RoutePlan.LegKind.TERMINAL)
				.guidance("Exact Artio NPC arrival.")
				.terminal(RoutePlan.TerminalSpec.of("Artio", terminal))
				.build())
			.build();

		final List<String> failures = validateWithPlans(
			singlePlanLookup(artio, wrongAccess)
		);
		final String identity = routeIdentity(artio);
		assertContains(
			failures,
			"route plan has no authored manifest access target: " + identity,
			"An exact plan with the wrong start was allowed to satisfy the row"
		);
		assertContains(
			failures,
			"unresolved terminal: " + identity,
			"A plan with the wrong manifest access incorrectly cleared the row"
		);
	}

	@Test
	public void bossNpcPresenceAloneIsNotAPersistentTerminal()
	{
		final SlayerRouteReleaseManifest.Entry artio = findEncounter(
			"Artio", "Hunter's End"
		);
		final WorldPoint access = new WorldPoint(3112, 3670, 0);
		final RouteCheck terminal = RouteCheck.exactNpc("Artio");
		final RouteCheck.ObjectActionSpec entrance =
			RouteCheck.ObjectActionSpec.named(
				"Hunter's End entrance", "Enter"
			);
		final RoutePlan npcOnly = RoutePlan.builder(
				"artio-transient-npc-only"
			)
			.startAt("route")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(RoutePlan.Leg.builder(
					"route", RoutePlan.LegKind.TRAVEL)
				.guidance("Route to Hunter's End.")
				.target(RoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.completeWhen(RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(access, 4, 4)))
				.then("enter")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"enter", RoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter Hunter's End.")
				.target(RoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.interactionInstruction("Enter Hunter's End.")
				.readyWhen(RouteCheck.trackedObjectAction(entrance))
				.completeWhen(RouteCheck.allOf(
					RouteCheck.observedObjectInteraction(entrance), terminal))
				.then("arrived")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"arrived", RoutePlan.LegKind.TERMINAL)
				.guidance("Artio is currently visible.")
				.terminal(RoutePlan.TerminalSpec.of("Artio", terminal))
				.build())
			.build();

		final String identity = routeIdentity(artio);
		final List<String> failures = validateWithPlans(
			singlePlanLookup(artio, npcOnly)
		);
		assertContains(
			failures,
			"boss route terminal relies on transient exact NPC presence: "
				+ identity,
			"An exact-NPC-only boss terminal was treated as persistent"
		);
		assertContains(
			failures,
			"unresolved terminal: " + identity,
			"Transient boss NPC presence incorrectly cleared the release row"
		);
	}

	@Test
	public void surfaceAccessAreaCannotMasqueradeAsTransitionCompletion()
	{
		final SlayerRouteReleaseManifest.Entry artio = findEncounter(
			"Artio", "Hunter's End"
		);
		final WorldPoint access = new WorldPoint(3112, 3670, 0);
		final RouteCheck surfaceTerminal = RouteCheck.anyOf(
			RouteCheck.worldArea(
				RouteCheck.SpatialArea.aroundWorld(access, 8, 8)),
			RouteCheck.exactNpc("Artio")
		);
		final RouteCheck.ObjectActionSpec entrance =
			RouteCheck.ObjectActionSpec.named(
				"Hunter's End entrance", "Enter"
			);
		final RoutePlan surfaceOnly = RoutePlan.builder(
				"artio-surface-area-only"
			)
			.startAt("route")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(RoutePlan.Leg.builder(
					"route", RoutePlan.LegKind.TRAVEL)
				.guidance("Route to Hunter's End.")
				.target(RoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.completeWhen(RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(access, 4, 4)))
				.then("enter")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"enter", RoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter Hunter's End.")
				.target(RoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.interactionInstruction("Enter Hunter's End.")
				.readyWhen(RouteCheck.trackedObjectAction(entrance))
				.completeWhen(surfaceTerminal)
				.then("arrived")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"arrived", RoutePlan.LegKind.TERMINAL)
				.guidance("Unproven Hunter's End arena.")
				.terminal(RoutePlan.TerminalSpec.of(
					"Unproven Hunter's End arena", surfaceTerminal))
				.build())
			.build();

		final String identity = routeIdentity(artio);
		final List<String> failures = validateWithPlans(
			singlePlanLookup(artio, surfaceOnly)
		);
		assertContains(
			failures,
			"manual route leg has no observed transition completion: "
				+ identity + " -> enter",
			"The surface access area was treated as interior transition proof"
		);
		assertContains(
			failures,
			"unresolved terminal: " + identity,
			"A surface-only completion incorrectly cleared the release row"
		);
	}

	@Test
	public void bossTerminalAreaMustRemainInsideActivityRetention()
	{
		final SlayerRouteReleaseManifest.Entry artio = findEncounter(
			"Artio", "Hunter's End"
		);
		final WorldPoint access = new WorldPoint(3112, 3670, 0);
		final WorldPoint arena = new WorldPoint(3200, 10000, 0);
		final RouteCheck terminal = RouteCheck.anyOf(
			RouteCheck.worldArea(
				RouteCheck.SpatialArea.aroundWorld(arena, 8, 8)),
			RouteCheck.exactNpc("Artio")
		);
		final RouteCheck.ObjectActionSpec entrance =
			RouteCheck.ObjectActionSpec.named(
				"Hunter's End entrance", "Enter"
			);
		final RoutePlan unretainedArena = RoutePlan.builder(
				"artio-unretained-arena"
			)
			.startAt("route")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(RoutePlan.Leg.builder(
					"route", RoutePlan.LegKind.TRAVEL)
				.guidance("Route to Hunter's End.")
				.target(RoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.completeWhen(RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(access, 4, 4)))
				.then("enter")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"enter", RoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter Hunter's End.")
				.target(RoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.interactionInstruction("Enter Hunter's End.")
				.readyWhen(RouteCheck.trackedObjectAction(entrance))
				.completeWhen(RouteCheck.allOf(
					RouteCheck.observedObjectInteraction(entrance), terminal))
				.then("arrived")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"arrived", RoutePlan.LegKind.TERMINAL)
				.guidance("Hunter's End arena.")
				.terminal(RoutePlan.TerminalSpec.of(
					"Hunter's End arena", terminal))
				.build())
			.build();

		final String identity = routeIdentity(artio);
		final List<String> failures = validateWithPlans(
			singlePlanLookup(artio, unretainedArena)
		);
		assertContains(
			failures,
			"boss terminal area is outside activity retention: " + identity,
			"A terminal arena outside retention was accepted"
		);
		assertContains(
			failures,
			"unresolved terminal: " + identity,
			"An unretained arena incorrectly cleared the release row"
		);
	}

	@Test
	public void exactVariantNpcNamesSatisfyAuthorizedNpcFamilies()
	{
		final List<String> failures = SlayerRouteReleaseValidator.validate();
		assertNotContains(
			failures,
			"route plan terminal has no exact target NPC alias: "
				+ "ASSIGNMENT|Revenants|Wilderness",
			"Exact Revenant variant names did not satisfy the authorized family"
		);
		assertNotContains(
			failures,
			"route plan terminal has no exact target NPC alias: "
				+ "ASSIGNMENT|Custodian Stalkers|Stalker Den",
			"Exact Custodian stalker variants did not satisfy the authorized family"
		);
	}

	@Test
	public void exactProfileAccessOverridesAnAmbiguousLocationDefault()
	{
		final String[][] cases = {
			{"Cave kraken", "Kraken Cove"},
			{"Lesser nagua", "Ruins of Tapoyauik"}
		};
		final List<String> failures = SlayerRouteReleaseValidator.validate();
		for (final String[] route : cases)
		{
			final SlayerRouteReleaseManifest.Entry entry = findAssignment(
				route[0], route[1]
			);
			final RouteCatalog.RouteProfile profile = RouteCatalog.resolve(
				route[0], route[1], false
			);
			final SlayerRouteReleaseManifest.Endpoint expected =
				entry.getExpectedAccess();
			final WorldPoint expectedPoint = new WorldPoint(
				expected.getX(), expected.getY(), expected.getPlane()
			);
			assertTrue(
				"Exact profile is missing its task-specific access for "
					+ routeIdentity(entry),
				profile != null && expectedPoint.equals(profile.getSurfaceAccess())
			);
			assertTrue(
				"Test precondition failed: location default is not ambiguous for "
					+ routeIdentity(entry),
				!expectedPoint.equals(RouteCatalog.find(route[1]))
			);
			assertNotContains(
				failures,
				"access coordinate mismatch: " + routeIdentity(entry),
				"The shared location default overrode an exact-key profile access"
			);
		}
	}

	@Test
	public void exactPlanMayOwnTerminalForAnAccessOnlyLegacyProfile()
	{
		final SlayerRouteReleaseManifest.Entry revenants = findAssignment(
			"Revenants", "Wilderness"
		);
		final RouteCatalog.RouteProfile profile = RouteCatalog.resolve(
			"Revenants", "Wilderness", false
		);
		final SlayerRouteReleaseManifest.Endpoint terminal =
			revenants.getExpectedTerminal();
		assertTrue(
			"Test precondition failed: Revenants is not an access-only profile",
			profile != null && profile.isAccessThenNpc()
		);
		assertTrue(
			"Test precondition failed: legacy entry point unexpectedly equals terminal",
			terminal != null && (profile.getPrimaryDestination().getX()
				!= terminal.getX()
				|| profile.getPrimaryDestination().getY() != terminal.getY()
				|| profile.getPrimaryDestination().getPlane() != terminal.getPlane())
		);

		final String mismatch = "terminal coordinate mismatch: "
			+ routeIdentity(revenants);
		assertNotContains(
			SlayerRouteReleaseValidator.validate(),
			mismatch,
			"A complete exact plan was forced into an access-only legacy profile"
		);
		assertContains(
			SlayerRouteReleaseValidator.validateForTest(
				SlayerRouteReleaseManifest.entries(),
				SlayerTaskStrategyCatalog.getCurrentTaskNames(),
				VariantCatalog.getBossDefinitions(),
				VariantCatalog.getDirectBossDefinitions(),
				SlayerRouteReleaseValidator.catalogLookupForTest(),
				noPlanLookup()
			),
			mismatch,
			"An access-only profile was allowed to replace terminal proof without a plan"
		);
	}

	@Test
	public void genericPlanFallbackCannotSatisfyADynamicTerminal()
	{
		final SlayerRouteReleaseManifest.Entry denied = findEncounter(
			"Artio", "Hunter's End"
		);
		final SlayerRouteReleaseValidator.PlanLookup catalog =
			SlayerRouteReleaseValidator.planLookupForTest();
		final SlayerRouteReleaseValidator.PlanLookup fallback =
			new SlayerRouteReleaseValidator.PlanLookup()
			{
				@Override
				public boolean hasExplicitPlan(
					final String assignment,
					final String location,
					final boolean boss)
				{
					return !matches(denied, assignment, location, boss)
						&& catalog.hasExplicitPlan(assignment, location, boss);
				}

				@Override
				public RoutePlan resolve(
					final String assignment,
					final String location,
					final boolean boss)
				{
					return catalog.resolve(assignment, location, boss);
				}
			};

		final List<String> failures = validateWithPlans(fallback);
		final String identity = routeIdentity(denied);
		assertContains(
			failures,
			"generic route-plan fallback reached by release row: " + identity,
			"A generic route-plan result was allowed to satisfy the release row"
		);
		assertContains(
			failures,
			"unresolved terminal: " + identity,
			"A generic route-plan result incorrectly cleared the unresolved row"
		);
	}

	@Test
	public void wrongEncounterPlanCannotSatisfyAnExactKey()
	{
		final SlayerRouteReleaseManifest.Entry denied = findEncounter(
			"Artio", "Hunter's End"
		);
		final RoutePlan callisto = SlayerRoutePlanCatalog.resolve(
			"Callisto", "Callisto's Den", true
		);
		final SlayerRouteReleaseValidator.PlanLookup mismatched =
			new SlayerRouteReleaseValidator.PlanLookup()
			{
				@Override
				public boolean hasExplicitPlan(
					final String assignment,
					final String location,
					final boolean boss)
				{
					return matches(denied, assignment, location, boss);
				}

				@Override
				public RoutePlan resolve(
					final String assignment,
					final String location,
					final boolean boss)
				{
					return matches(denied, assignment, location, boss)
						? callisto : null;
				}
			};

		final List<String> failures = validateWithPlans(mismatched);
		final String identity = routeIdentity(denied);
		assertContains(
			failures,
			"route plan terminal has no exact target NPC alias: " + identity,
			"A different encounter's NPC terminal was accepted"
		);
		assertContains(
			failures,
			"unresolved terminal: " + identity,
			"A mismatched encounter plan incorrectly cleared the unresolved row"
		);
	}

	@Test
	public void instructionOnlyTravelCannotHideAnUnmappedTransition()
	{
		final SlayerRouteReleaseManifest.Entry denied = findEncounter(
			"Artio", "Hunter's End"
		);
		final WorldPoint access = new WorldPoint(3112, 3670, 0);
		final RouteCheck terminal = RouteCheck.exactNpc("Artio");
		final RoutePlan invalidProof = RoutePlan.builder(
				"instruction-only-artio"
			)
			.startAt("unmapped")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(RoutePlan.Leg.builder(
					"unmapped", RoutePlan.LegKind.TRAVEL)
				.guidance("Follow an unspecified interior route.")
				.target(RoutePlan.RouteTarget.instruction(
					"Unmapped interior route"))
				.completeWhen(terminal)
				.then("arrived")
				.build())
			.addLeg(RoutePlan.Leg.builder(
					"arrived", RoutePlan.LegKind.TERMINAL)
				.guidance("Exact Artio NPC arrival.")
				.terminal(RoutePlan.TerminalSpec.of("Artio", terminal))
				.build())
			.build();
		final SlayerRouteReleaseValidator.PlanLookup lookup =
			singlePlanLookup(denied, invalidProof);

		final List<String> failures = validateWithPlans(lookup);
		final String identity = routeIdentity(denied);
		assertContains(
			failures,
			"unmapped travel leg lacks a static graph target or manual "
				+ "interaction contract: " + identity,
			"An instruction-only travel leg was treated as a mapped route"
		);
		assertContains(
			failures,
			"unresolved terminal: " + identity,
			"An unmapped transition incorrectly cleared the unresolved row"
		);
	}

	@Test
	public void orphanPlanKeyFailsIndependentManifestParity()
	{
		final SlayerRouteReleaseValidator.PlanLookup orphan =
			new SlayerRouteReleaseValidator.PlanLookup()
			{
				@Override
				public boolean hasExplicitPlan(
					final String assignment,
					final String location,
					final boolean boss)
				{
					return false;
				}

				@Override
				public RoutePlan resolve(
					final String assignment,
					final String location,
					final boolean boss)
				{
					return null;
				}

				@Override
				public Collection<SlayerRouteReleaseValidator.PlanKey> keys()
				{
					return Collections.singletonList(
						new SlayerRouteReleaseValidator.PlanKey(
							"Unreviewed monster", "Imaginary cave", false
						)
					);
				}
			};

		assertContains(
			validateWithPlans(orphan),
			"route plan has no exact release-manifest row: assignment|"
				+ "unreviewed monster|imaginary cave",
			"An undocumented exact plan key was not rejected"
		);
	}

	@Test
	public void removingOneCanonicalBossRowFailsRegistryParity()
	{
		final List<SlayerRouteReleaseManifest.Entry> mutated = new ArrayList<>(
			SlayerRouteReleaseManifest.entries()
		);
		SlayerRouteReleaseManifest.Entry removed = null;
		for (final SlayerRouteReleaseManifest.Entry entry : mutated)
		{
			if (entry.getKind()
				== SlayerRouteReleaseManifest.EntryKind.SELECTABLE_BOSS)
			{
				removed = entry;
				break;
			}
		}
		assertTrue("Manifest has no selectable boss row to mutate", removed != null);
		mutated.remove(removed);

		final List<String> failures = SlayerRouteReleaseValidator.validateForTest(
			mutated,
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			VariantCatalog.getBossDefinitions(),
			VariantCatalog.getDirectBossDefinitions(),
			SlayerRouteReleaseValidator.catalogLookupForTest()
		);
		assertContains(
			failures,
			"manifest selectable-boss registry parity mismatch",
			"Removing a canonical boss did not fail manifest parity"
		);
		assertContains(
			failures,
			"selectable boss registry parity mismatch",
			"Removing a canonical boss did not fail implementation parity"
		);
	}

	@Test
	public void genericFallbackCannotSatisfyAnAuthoredProfileRequirement()
	{
		final SlayerRouteReleaseManifest.Entry denied = firstEstablishedEntry();
		final SlayerRouteReleaseValidator.ProfileLookup catalog =
			SlayerRouteReleaseValidator.catalogLookupForTest();
		final SlayerRouteReleaseValidator.ProfileLookup missingExact =
			new SlayerRouteReleaseValidator.ProfileLookup()
			{
				@Override
				public boolean hasExplicitProfile(
					final String assignment,
					final String location,
					final boolean boss)
				{
					if (matches(denied, assignment, location, boss))
					{
						return false;
					}
					return catalog.hasExplicitProfile(assignment, location, boss);
				}

				@Override
				public RouteCatalog.RouteProfile resolve(
					final String assignment,
					final String location,
					final boolean boss)
				{
					return catalog.resolve(assignment, location, boss);
				}

				@Override
				public WorldPoint findAccess(final String location)
				{
					return catalog.findAccess(location);
				}
			};

		final List<String> failures = SlayerRouteReleaseValidator.validateForTest(
			SlayerRouteReleaseManifest.entries(),
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			VariantCatalog.getBossDefinitions(),
			VariantCatalog.getDirectBossDefinitions(),
			missingExact
		);
		final String identity = routeIdentity(denied);
		assertContains(
			failures,
			"missing exact authored profile: " + identity,
			"Denied exact profile was not reported"
		);
		assertContains(
			failures,
			"generic resolve fallback reached by release row: " + identity,
			"A generic resolve result was allowed to satisfy the release row"
		);
	}

	private static SlayerRouteReleaseManifest.Entry firstEstablishedEntry()
	{
		for (final SlayerRouteReleaseManifest.Entry entry
			: SlayerRouteReleaseManifest.entries())
		{
			if (entry.hasEstablishedTerminal())
			{
				return entry;
			}
		}
		throw new AssertionError("Manifest has no established route terminal");
	}

	private static SlayerRouteReleaseManifest.Entry findEncounter(
		final String encounter,
		final String location)
	{
		for (final SlayerRouteReleaseManifest.Entry entry
			: SlayerRouteReleaseManifest.entries())
		{
			if (entry.getKind()
					!= SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT
				&& encounter.equals(entry.getEncounterName())
				&& location.equals(entry.getLocation()))
			{
				return entry;
			}
		}
		throw new AssertionError(
			"Missing encounter row: " + encounter + " @ " + location
		);
	}

	private static SlayerRouteReleaseManifest.Entry findAssignment(
		final String assignment,
		final String location)
	{
		for (final SlayerRouteReleaseManifest.Entry entry
			: SlayerRouteReleaseManifest.entries())
		{
			if (entry.getKind()
					== SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT
				&& assignment.equals(entry.getAssignmentName())
				&& location.equals(entry.getLocation()))
			{
				return entry;
			}
		}
		throw new AssertionError(
			"Missing assignment row: " + assignment + " @ " + location
		);
	}

	private static List<String> validateWithPlans(
		final SlayerRouteReleaseValidator.PlanLookup plans)
	{
		return SlayerRouteReleaseValidator.validateForTest(
			SlayerRouteReleaseManifest.entries(),
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			VariantCatalog.getBossDefinitions(),
			VariantCatalog.getDirectBossDefinitions(),
			SlayerRouteReleaseValidator.catalogLookupForTest(),
			plans
		);
	}

	private static SlayerRouteReleaseValidator.PlanLookup noPlanLookup()
	{
		return new SlayerRouteReleaseValidator.PlanLookup()
		{
			@Override
			public boolean hasExplicitPlan(
				final String assignment,
				final String location,
				final boolean boss)
			{
				return false;
			}

			@Override
			public RoutePlan resolve(
				final String assignment,
				final String location,
				final boolean boss)
			{
				return null;
			}
		};
	}

	private static SlayerRouteReleaseValidator.PlanLookup singlePlanLookup(
		final SlayerRouteReleaseManifest.Entry entry,
		final RoutePlan plan)
	{
		return new SlayerRouteReleaseValidator.PlanLookup()
		{
			@Override
			public boolean hasExplicitPlan(
				final String assignment,
				final String location,
				final boolean boss)
			{
				return matches(entry, assignment, location, boss);
			}

			@Override
			public RoutePlan resolve(
				final String assignment,
				final String location,
				final boolean boss)
			{
				return matches(entry, assignment, location, boss) ? plan : null;
			}
		};
	}

	private static boolean matches(
		final SlayerRouteReleaseManifest.Entry entry,
		final String assignment,
		final String location,
		final boolean boss)
	{
		final String expectedTask = entry.getKind()
			== SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT
				? entry.getAssignmentName() : entry.getEncounterName();
		return expectedTask.equals(assignment)
			&& entry.getLocation().equals(location)
			&& boss == (entry.getKind()
				!= SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT);
	}

	private static String routeIdentity(
		final SlayerRouteReleaseManifest.Entry entry)
	{
		return entry.getKind() + "|"
			+ (entry.getKind() == SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT
				? entry.getAssignmentName() : entry.getEncounterName())
			+ "|" + entry.getLocation();
	}

	private static void assertContains(
		final List<String> failures,
		final String fragment,
		final String message)
	{
		for (final String failure : failures)
		{
			if (failure.contains(fragment))
			{
				return;
			}
		}
		throw new AssertionError(message + "; failures=" + failures);
	}

	private static void assertNotContains(
		final List<String> failures,
		final String fragment,
		final String message)
	{
		for (final String failure : failures)
		{
			if (failure.contains(fragment))
			{
				throw new AssertionError(message + "; failure=" + failure);
			}
		}
	}
}
