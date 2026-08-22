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
		final List<String> failures = SlayerRouteReleaseValidator.validateForRegression(
			SlayerRouteReleaseManifest.entries(),
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			SlayerTaskVariantCatalog.getBossDefinitions(),
			SlayerTaskVariantCatalog.getDirectBossDefinitions(),
			SlayerRouteReleaseValidator.catalogLookupForRegression(),
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
		final SlayerRoutePredicate spatialTerminal = SlayerRoutePredicate.worldArea(
			SlayerRoutePredicate.SpatialArea.aroundWorld(guessedRoom, 8, 8)
		);
		final SlayerRoutePredicate.ObjectActionSpec entrance =
			SlayerRoutePredicate.ObjectActionSpec.named(
				"Kraken Cove entrance", "Enter"
			);
		final SlayerRoutePlan spatialOnly = SlayerRoutePlan.builder(
				"cave-kraken-spatial-only"
			)
			.startAt("route")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"route", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Route to Kraken Cove.")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Kraken Cove entrance", access, 4))
				.completeWhen(SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(access, 4, 4)))
				.then("enter")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"enter", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter Kraken Cove.")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Kraken Cove entrance", access, 4))
				.interactionInstruction("Enter Kraken Cove.")
				.readyWhen(SlayerRoutePredicate.trackedObjectAction(entrance))
				.completeWhen(SlayerRoutePredicate.allOf(
					SlayerRoutePredicate.observedObjectInteraction(entrance),
					spatialTerminal))
				.then("arrived")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"arrived", SlayerRoutePlan.LegKind.TERMINAL)
				.guidance("Guessed room area.")
				.terminal(SlayerRoutePlan.TerminalSpec.of(
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
			SlayerRouteReleaseValidator.catalogLookupForRegression();
		final SlayerRouteReleaseValidator.ProfileLookup noExactProfile =
			new SlayerRouteReleaseValidator.ProfileLookup()
			{
				@Override
				public boolean hasExplicitProfile(
					final String taskName,
					final String location,
					final boolean boss)
				{
					return !matches(artio, taskName, location, boss)
						&& catalog.hasExplicitProfile(taskName, location, boss);
				}

				@Override
				public SlayerRouteCatalog.RouteProfile resolve(
					final String taskName,
					final String location,
					final boolean boss)
				{
					return catalog.resolve(taskName, location, boss);
				}

				@Override
				public WorldPoint findAccess(final String location)
				{
					return catalog.findAccess(location);
				}
			};

		final List<String> failures = SlayerRouteReleaseValidator.validateForRegression(
			SlayerRouteReleaseManifest.entries(),
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			SlayerTaskVariantCatalog.getBossDefinitions(),
			SlayerTaskVariantCatalog.getDirectBossDefinitions(),
			noExactProfile,
			SlayerRouteReleaseValidator.planLookupForRegression()
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
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.exactNpc("Artio");
		final SlayerRoutePredicate.ObjectActionSpec entrance =
			SlayerRoutePredicate.ObjectActionSpec.named("Imaginary entrance", "Enter");
		final SlayerRoutePlan wrongAccess = SlayerRoutePlan.builder(
				"artio-wrong-access"
			)
			.startAt("route")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(wrong, 24, 24)
			))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"route", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Route to the wrong entrance.")
				.target(SlayerRoutePlan.RouteTarget.point("Wrong entrance", wrong, 4))
				.completeWhen(SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(wrong, 4, 4)))
				.then("enter")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"enter", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter the wrong cave.")
				.target(SlayerRoutePlan.RouteTarget.point("Wrong entrance", wrong, 4))
				.interactionInstruction("Enter the wrong cave.")
				.readyWhen(SlayerRoutePredicate.trackedObjectAction(entrance))
				.completeWhen(SlayerRoutePredicate.allOf(
					SlayerRoutePredicate.observedObjectInteraction(entrance), terminal))
				.then("arrived")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"arrived", SlayerRoutePlan.LegKind.TERMINAL)
				.guidance("Exact Artio NPC arrival.")
				.terminal(SlayerRoutePlan.TerminalSpec.of("Artio", terminal))
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
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.exactNpc("Artio");
		final SlayerRoutePredicate.ObjectActionSpec entrance =
			SlayerRoutePredicate.ObjectActionSpec.named(
				"Hunter's End entrance", "Enter"
			);
		final SlayerRoutePlan npcOnly = SlayerRoutePlan.builder(
				"artio-transient-npc-only"
			)
			.startAt("route")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"route", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Route to Hunter's End.")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.completeWhen(SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(access, 4, 4)))
				.then("enter")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"enter", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter Hunter's End.")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.interactionInstruction("Enter Hunter's End.")
				.readyWhen(SlayerRoutePredicate.trackedObjectAction(entrance))
				.completeWhen(SlayerRoutePredicate.allOf(
					SlayerRoutePredicate.observedObjectInteraction(entrance), terminal))
				.then("arrived")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"arrived", SlayerRoutePlan.LegKind.TERMINAL)
				.guidance("Artio is currently visible.")
				.terminal(SlayerRoutePlan.TerminalSpec.of("Artio", terminal))
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
		final SlayerRoutePredicate surfaceTerminal = SlayerRoutePredicate.anyOf(
			SlayerRoutePredicate.worldArea(
				SlayerRoutePredicate.SpatialArea.aroundWorld(access, 8, 8)),
			SlayerRoutePredicate.exactNpc("Artio")
		);
		final SlayerRoutePredicate.ObjectActionSpec entrance =
			SlayerRoutePredicate.ObjectActionSpec.named(
				"Hunter's End entrance", "Enter"
			);
		final SlayerRoutePlan surfaceOnly = SlayerRoutePlan.builder(
				"artio-surface-area-only"
			)
			.startAt("route")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"route", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Route to Hunter's End.")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.completeWhen(SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(access, 4, 4)))
				.then("enter")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"enter", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter Hunter's End.")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.interactionInstruction("Enter Hunter's End.")
				.readyWhen(SlayerRoutePredicate.trackedObjectAction(entrance))
				.completeWhen(surfaceTerminal)
				.then("arrived")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"arrived", SlayerRoutePlan.LegKind.TERMINAL)
				.guidance("Unproven Hunter's End arena.")
				.terminal(SlayerRoutePlan.TerminalSpec.of(
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
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.anyOf(
			SlayerRoutePredicate.worldArea(
				SlayerRoutePredicate.SpatialArea.aroundWorld(arena, 8, 8)),
			SlayerRoutePredicate.exactNpc("Artio")
		);
		final SlayerRoutePredicate.ObjectActionSpec entrance =
			SlayerRoutePredicate.ObjectActionSpec.named(
				"Hunter's End entrance", "Enter"
			);
		final SlayerRoutePlan unretainedArena = SlayerRoutePlan.builder(
				"artio-unretained-arena"
			)
			.startAt("route")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"route", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Route to Hunter's End.")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.completeWhen(SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(access, 4, 4)))
				.then("enter")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"enter", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter Hunter's End.")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Hunter's End entrance", access, 4))
				.interactionInstruction("Enter Hunter's End.")
				.readyWhen(SlayerRoutePredicate.trackedObjectAction(entrance))
				.completeWhen(SlayerRoutePredicate.allOf(
					SlayerRoutePredicate.observedObjectInteraction(entrance), terminal))
				.then("arrived")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"arrived", SlayerRoutePlan.LegKind.TERMINAL)
				.guidance("Hunter's End arena.")
				.terminal(SlayerRoutePlan.TerminalSpec.of(
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
			final SlayerRouteCatalog.RouteProfile profile = SlayerRouteCatalog.resolve(
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
				!expectedPoint.equals(SlayerRouteCatalog.find(route[1]))
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
		final SlayerRouteCatalog.RouteProfile profile = SlayerRouteCatalog.resolve(
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
			SlayerRouteReleaseValidator.validateForRegression(
				SlayerRouteReleaseManifest.entries(),
				SlayerTaskStrategyCatalog.getCurrentTaskNames(),
				SlayerTaskVariantCatalog.getBossDefinitions(),
				SlayerTaskVariantCatalog.getDirectBossDefinitions(),
				SlayerRouteReleaseValidator.catalogLookupForRegression(),
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
			SlayerRouteReleaseValidator.planLookupForRegression();
		final SlayerRouteReleaseValidator.PlanLookup fallback =
			new SlayerRouteReleaseValidator.PlanLookup()
			{
				@Override
				public boolean hasExplicitPlan(
					final String taskName,
					final String location,
					final boolean boss)
				{
					return !matches(denied, taskName, location, boss)
						&& catalog.hasExplicitPlan(taskName, location, boss);
				}

				@Override
				public SlayerRoutePlan resolve(
					final String taskName,
					final String location,
					final boolean boss)
				{
					return catalog.resolve(taskName, location, boss);
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
		final SlayerRoutePlan callisto = SlayerRoutePlanCatalog.resolve(
			"Callisto", "Callisto's Den", true
		);
		final SlayerRouteReleaseValidator.PlanLookup mismatched =
			new SlayerRouteReleaseValidator.PlanLookup()
			{
				@Override
				public boolean hasExplicitPlan(
					final String taskName,
					final String location,
					final boolean boss)
				{
					return matches(denied, taskName, location, boss);
				}

				@Override
				public SlayerRoutePlan resolve(
					final String taskName,
					final String location,
					final boolean boss)
				{
					return matches(denied, taskName, location, boss)
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
		final SlayerRoutePredicate terminal = SlayerRoutePredicate.exactNpc("Artio");
		final SlayerRoutePlan invalidProof = SlayerRoutePlan.builder(
				"instruction-only-artio"
			)
			.startAt("unmapped")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(access, 24, 24)
			))
			.addLeg(SlayerRoutePlan.Leg.builder(
					"unmapped", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Follow an unspecified interior route.")
				.target(SlayerRoutePlan.RouteTarget.instruction(
					"Unmapped interior route"))
				.completeWhen(terminal)
				.then("arrived")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
					"arrived", SlayerRoutePlan.LegKind.TERMINAL)
				.guidance("Exact Artio NPC arrival.")
				.terminal(SlayerRoutePlan.TerminalSpec.of("Artio", terminal))
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
					final String taskName,
					final String location,
					final boolean boss)
				{
					return false;
				}

				@Override
				public SlayerRoutePlan resolve(
					final String taskName,
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

		final List<String> failures = SlayerRouteReleaseValidator.validateForRegression(
			mutated,
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			SlayerTaskVariantCatalog.getBossDefinitions(),
			SlayerTaskVariantCatalog.getDirectBossDefinitions(),
			SlayerRouteReleaseValidator.catalogLookupForRegression()
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
			SlayerRouteReleaseValidator.catalogLookupForRegression();
		final SlayerRouteReleaseValidator.ProfileLookup missingExact =
			new SlayerRouteReleaseValidator.ProfileLookup()
			{
				@Override
				public boolean hasExplicitProfile(
					final String taskName,
					final String location,
					final boolean boss)
				{
					if (matches(denied, taskName, location, boss))
					{
						return false;
					}
					return catalog.hasExplicitProfile(taskName, location, boss);
				}

				@Override
				public SlayerRouteCatalog.RouteProfile resolve(
					final String taskName,
					final String location,
					final boolean boss)
				{
					return catalog.resolve(taskName, location, boss);
				}

				@Override
				public WorldPoint findAccess(final String location)
				{
					return catalog.findAccess(location);
				}
			};

		final List<String> failures = SlayerRouteReleaseValidator.validateForRegression(
			SlayerRouteReleaseManifest.entries(),
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			SlayerTaskVariantCatalog.getBossDefinitions(),
			SlayerTaskVariantCatalog.getDirectBossDefinitions(),
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
		return SlayerRouteReleaseValidator.validateForRegression(
			SlayerRouteReleaseManifest.entries(),
			SlayerTaskStrategyCatalog.getCurrentTaskNames(),
			SlayerTaskVariantCatalog.getBossDefinitions(),
			SlayerTaskVariantCatalog.getDirectBossDefinitions(),
			SlayerRouteReleaseValidator.catalogLookupForRegression(),
			plans
		);
	}

	private static SlayerRouteReleaseValidator.PlanLookup noPlanLookup()
	{
		return new SlayerRouteReleaseValidator.PlanLookup()
		{
			@Override
			public boolean hasExplicitPlan(
				final String taskName,
				final String location,
				final boolean boss)
			{
				return false;
			}

			@Override
			public SlayerRoutePlan resolve(
				final String taskName,
				final String location,
				final boolean boss)
			{
				return null;
			}
		};
	}

	private static SlayerRouteReleaseValidator.PlanLookup singlePlanLookup(
		final SlayerRouteReleaseManifest.Entry entry,
		final SlayerRoutePlan plan)
	{
		return new SlayerRouteReleaseValidator.PlanLookup()
		{
			@Override
			public boolean hasExplicitPlan(
				final String taskName,
				final String location,
				final boolean boss)
			{
				return matches(entry, taskName, location, boss);
			}

			@Override
			public SlayerRoutePlan resolve(
				final String taskName,
				final String location,
				final boolean boss)
			{
				return matches(entry, taskName, location, boss) ? plan : null;
			}
		};
	}

	private static boolean matches(
		final SlayerRouteReleaseManifest.Entry entry,
		final String taskName,
		final String location,
		final boolean boss)
	{
		final String expectedTask = entry.getKind()
			== SlayerRouteReleaseManifest.EntryKind.ASSIGNMENT
				? entry.getAssignmentName() : entry.getEncounterName();
		return expectedTask.equals(taskName)
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
