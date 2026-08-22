package com.slayerplus;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SlayerRoutePlanTest
{
	private static final int REVIEWED_ARENA_WIDGET_COMPONENT = 42_001;

	@Test
	public void wrongDungeonInSameCoarseYBandDoesNotCompleteLayerTransition()
	{
		final WorldPoint surface = new WorldPoint(1600, 3200, 0);
		final WorldPoint reviewedInterior = new WorldPoint(1664, 9664, 0);
		final WorldPoint unrelatedDungeon = new WorldPoint(3200, 9664, 0);
		assertEquals(
			reviewedInterior.getY() >>> 12,
			unrelatedDungeon.getY() >>> 12
		);
		assertFalse(
			reviewedInterior.getRegionID() == unrelatedDungeon.getRegionID()
		);

		final SlayerRoutePredicate transition =
			SlayerRoutePredicate.coordinateLayerTransition(
				SlayerRoutePredicate.CoordinateLayer.worldRegions(
					0, surface.getRegionID()),
				SlayerRoutePredicate.CoordinateLayer.worldRegions(
					0, reviewedInterior.getRegionID())
			);

		assertFalse(transition.matches(SlayerRouteEvidence.builder()
			.previousWorldPoint(surface)
			.worldPoint(unrelatedDungeon)
			.build()));
		assertTrue(transition.matches(SlayerRouteEvidence.builder()
			.previousWorldPoint(surface)
			.worldPoint(reviewedInterior)
			.build()));
	}

	@Test
	public void sameLayerCaveEntranceIsRetainedButNotTreatedAsArrival()
	{
		final WorldPoint caveEntrance = new WorldPoint(3112, 3670, 0);
		final WorldPoint bossCenter = new WorldPoint(3116, 3677, 0);
		assertTrue(caveEntrance.distanceTo(bossCenter) <= 14);

		final SlayerRoutePlan plan = terminalOnlyPlan(
			"same-layer-cave",
			SlayerRoutePredicate.anyOf(
				SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(
						bossCenter, 1, 1)),
				SlayerRoutePredicate.exactNpc("Artio")
			),
			SlayerRoutePredicate.SpatialArea.aroundWorld(
				bossCenter, 192, 192)
		);
		final SlayerRouteEvidence entranceEvidence = SlayerRouteEvidence.builder()
			.worldPoint(caveEntrance)
			.build();

		assertTrue(plan.isWithinActivityRetention(entranceEvidence));
		assertFalse(plan.hasArrived(entranceEvidence));
		assertEquals(
			SlayerRoutePlan.ProgressStatus.ROUTING,
			plan.evaluate(plan.initialCursor(), entranceEvidence).getStatus()
		);
	}

	@Test
	public void unrelatedPohInstanceCannotSatisfyBossTerminal()
	{
		final WorldPoint bossTemplate = new WorldPoint(3100, 9700, 0);
		final WorldPoint pohTemplate = new WorldPoint(1856, 5696, 0);
		final SlayerRoutePlan plan = terminalOnlyPlan(
			"instance-terminal",
			SlayerRoutePredicate.anyOf(
				SlayerRoutePredicate.templateArea(
					SlayerRoutePredicate.SpatialArea.aroundTemplate(
						bossTemplate, 3, 3)),
				SlayerRoutePredicate.visibleWidgetComponent(
					REVIEWED_ARENA_WIDGET_COMPONENT),
				SlayerRoutePredicate.exactNpc("The reviewed boss")
			),
			SlayerRoutePredicate.SpatialArea.aroundTemplate(
				bossTemplate, 96, 96)
		);

		final SlayerRouteEvidence poh = SlayerRouteEvidence.builder()
			.instanced(true)
			.worldPoint(new WorldPoint(3200, 3200, 0))
			.templatePoint(pohTemplate)
			.build();
		assertTrue(poh.isInstanced());
		assertFalse(plan.hasArrived(poh));

		final SlayerRouteEvidence reviewedArenaWidget =
			SlayerRouteEvidence.builder()
				.instanced(true)
				.templatePoint(bossTemplate)
				.visibleWidgetComponent(REVIEWED_ARENA_WIDGET_COMPONENT)
				.build();
		assertTrue(plan.hasArrived(reviewedArenaWidget));
	}

	@Test
	public void manualCheckpointWaitsForInteractionInsteadOfProximity()
	{
		final WorldPoint barrierPoint = new WorldPoint(2500, 9500, 0);
		final WorldPoint interiorLanding = new WorldPoint(2500, 9564, 0);
		final SlayerRoutePredicate.ObjectActionSpec barrier =
			SlayerRoutePredicate.ObjectActionSpec.named(
				"Glowing barrier", "Pass"
			);
		final SlayerRoutePlan plan = SlayerRoutePlan.builder("manual-barrier")
			.startAt("barrier")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(
					barrierPoint, 64, 64)))
			.addLeg(SlayerRoutePlan.Leg.builder(
				"barrier", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Cross the glowing barrier")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Glowing barrier", barrierPoint, 2))
				.interactionInstruction("Click Pass on the glowing barrier")
				.readyWhen(SlayerRoutePredicate.trackedObjectAction(barrier))
				.completeWhen(SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(
						interiorLanding, 3, 3)))
				.then("inside")
				.build())
			.addLeg(terminalLeg(
				"inside",
				SlayerRoutePredicate.exactNpc("Inner guardian")))
			.build();

		final SlayerRouteEvidence objectVisible = SlayerRouteEvidence.builder()
			.worldPoint(barrierPoint)
			.trackedObjectAction(SlayerRouteEvidence.ObjectAction.named(
				"Glowing barrier", "Pass"))
			.build();
		final SlayerRoutePlan.Evaluation waiting = plan.evaluate(
			plan.initialCursor(), objectVisible
		);
		assertEquals(
			SlayerRoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			waiting.getStatus()
		);
		assertFalse(waiting.hasAdvanced());
		assertFalse(waiting.hasArrived());

		final SlayerRouteEvidence interactionObserved =
			SlayerRouteEvidence.builder()
				.worldPoint(barrierPoint)
				.observedObjectInteraction(
					SlayerRouteEvidence.ObjectAction.named(
						"Glowing barrier", "Pass"))
				.build();
		final SlayerRoutePlan.Evaluation failedClick = plan.evaluate(
			waiting.getCursor(), interactionObserved
		);
		assertFalse(failedClick.hasAdvanced());
		assertEquals("barrier", failedClick.getActiveLeg().getId());

		final SlayerRoutePlan.Evaluation advanced = plan.evaluate(
			failedClick.getCursor(), SlayerRouteEvidence.builder()
				.worldPoint(interiorLanding).build()
		);
		assertTrue(advanced.hasAdvanced());
		assertEquals("inside", advanced.getActiveLeg().getId());
		assertFalse(advanced.hasArrived());
	}

	@Test
	public void multiStagePlanAdvancesOneLegPerEvidenceSnapshot()
	{
		final WorldPoint access = new WorldPoint(3000, 3400, 0);
		final WorldPoint barrierPoint = new WorldPoint(1600, 9600, 0);
		final WorldPoint barrierLanding = new WorldPoint(1600, 9620, 0);
		final WorldPoint terminalPoint = new WorldPoint(1664, 9664, 0);
		final SlayerRoutePredicate.ObjectActionSpec barrier =
			SlayerRoutePredicate.ObjectActionSpec.named(
				"Ancient door", "Enter"
			);

		final SlayerRoutePlan plan = SlayerRoutePlan.builder("three-stage")
			.startAt("surface")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(
					terminalPoint, 128, 128)))
			.addLeg(SlayerRoutePlan.Leg.builder(
				"surface", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Travel to the dungeon entrance")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Dungeon entrance", access, 3))
				.completeWhen(SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(
						access, 3, 3)))
				.then("door")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
				"door", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter through the ancient door")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Ancient door", barrierPoint, 2))
				.interactionInstruction("Click Enter on the ancient door")
				.readyWhen(SlayerRoutePredicate.trackedObjectAction(barrier))
				.completeWhen(SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(
						barrierLanding, 2, 2)))
				.then("encounter")
				.build())
			.addLeg(terminalLeg(
				"encounter",
				SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.worldArea(
						SlayerRoutePredicate.SpatialArea.aroundWorld(
							terminalPoint, 2, 2)),
					SlayerRoutePredicate.exactNpc("Ancient guardian")
				)))
			.build();

		SlayerRoutePlan.Cursor cursor = plan.initialCursor();
		SlayerRoutePlan.Evaluation evaluation = plan.evaluate(
			cursor,
			SlayerRouteEvidence.builder().worldPoint(access).build()
		);
		assertTrue(evaluation.hasAdvanced());
		assertEquals("door", evaluation.getActiveLeg().getId());
		cursor = evaluation.getCursor();

		evaluation = plan.evaluate(
			cursor,
			SlayerRouteEvidence.builder()
				.worldPoint(barrierPoint)
				.trackedObjectAction(SlayerRouteEvidence.ObjectAction.named(
					"Ancient door", "Enter"))
				.build()
		);
		assertEquals(
			SlayerRoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			evaluation.getStatus()
		);
		assertFalse(evaluation.hasAdvanced());

		evaluation = plan.evaluate(
			evaluation.getCursor(),
			SlayerRouteEvidence.builder()
				.worldPoint(barrierPoint)
				.observedObjectInteraction(
					SlayerRouteEvidence.ObjectAction.named(
						"Ancient door", "Enter"))
				.build()
		);
		assertFalse(evaluation.hasAdvanced());
		assertEquals("door", evaluation.getActiveLeg().getId());

		evaluation = plan.evaluate(
			evaluation.getCursor(),
			SlayerRouteEvidence.builder().worldPoint(barrierLanding).build()
		);
		assertTrue(evaluation.hasAdvanced());
		assertEquals("encounter", evaluation.getActiveLeg().getId());
		assertFalse(evaluation.hasArrived());

		evaluation = plan.evaluate(
			evaluation.getCursor(),
			SlayerRouteEvidence.builder()
				.worldPoint(terminalPoint)
				.exactNpc("Ancient guardian")
				.build()
		);
		assertTrue(evaluation.hasArrived());
	}

	@Test
	public void decisionLegSelectsFirstMatchingReviewedBranch()
	{
		final WorldPoint fallback = new WorldPoint(3200, 3200, 0);
		final SlayerRoutePlan plan = SlayerRoutePlan.builder("branching")
			.startAt("choose")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(
					fallback, 64, 64)))
			.addLeg(SlayerRoutePlan.Leg.builder(
				"choose", SlayerRoutePlan.LegKind.DECISION)
				.guidance("Choose the available reviewed route")
				.completeWhen(SlayerRoutePredicate.always())
				.thenWhen(
					"Arena widget is already loaded",
					SlayerRoutePredicate.visibleWidgetComponent(
						REVIEWED_ARENA_WIDGET_COMPONENT),
					"encounter")
				.then("fallback")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
				"fallback", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Walk to the fallback entrance")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Fallback entrance", fallback, 2))
				.completeWhen(SlayerRoutePredicate.worldArea(
					SlayerRoutePredicate.SpatialArea.aroundWorld(
						fallback, 2, 2)))
				.then("encounter")
				.build())
			.addLeg(terminalLeg(
				"encounter",
				SlayerRoutePredicate.exactNpc("Reviewed boss")))
			.build();

		final SlayerRoutePlan.Evaluation direct = plan.evaluate(
			plan.initialCursor(),
			SlayerRouteEvidence.builder()
				.visibleWidgetComponent(REVIEWED_ARENA_WIDGET_COMPONENT)
				.build()
		);
		assertEquals("encounter", direct.getActiveLeg().getId());

		final SlayerRoutePlan.Evaluation fallbackRoute = plan.evaluate(
			plan.initialCursor(), SlayerRouteEvidence.empty()
		);
		assertEquals("fallback", fallbackRoute.getActiveLeg().getId());
	}

	@Test
	public void graphValidationRejectsCycleAndUnreachableTerminal()
	{
		final WorldPoint point = new WorldPoint(3200, 3200, 0);
		final SlayerRoutePredicate reachedPoint =
			SlayerRoutePredicate.worldArea(
				SlayerRoutePredicate.SpatialArea.aroundWorld(point, 1, 1));
		final SlayerRoutePlan.Builder builder = SlayerRoutePlan.builder("cycle")
			.startAt("a")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(
				SlayerRoutePredicate.SpatialArea.aroundWorld(point, 8, 8)))
			.addLeg(SlayerRoutePlan.Leg.builder(
				"a", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Reach A")
				.target(SlayerRoutePlan.RouteTarget.point("A", point, 1))
				.completeWhen(reachedPoint)
				.then("b")
				.build())
			.addLeg(SlayerRoutePlan.Leg.builder(
				"b", SlayerRoutePlan.LegKind.TRAVEL)
				.guidance("Reach B")
				.target(SlayerRoutePlan.RouteTarget.point("B", point, 1))
				.completeWhen(reachedPoint)
				.then("a")
				.build())
			.addLeg(terminalLeg(
				"unreachable", SlayerRoutePredicate.exactNpc("Boss")));

		final List<String> errors = builder.validateGraph();
		assertTrue(errors.stream().anyMatch(error ->
			error.contains("unreachable route leg")));
		assertTrue(errors.stream().anyMatch(error ->
			error.contains("contains a cycle")));
		try
		{
			builder.build();
			fail("Invalid graphs must not build");
		}
		catch (final IllegalStateException expected)
		{
			assertTrue(expected.getMessage().contains("cycle"));
		}
	}

	@Test
	public void manualLegCannotUseProximityAsCompletion()
	{
		final WorldPoint point = new WorldPoint(3200, 3200, 0);
		final SlayerRoutePredicate proximity = SlayerRoutePredicate.worldArea(
			SlayerRoutePredicate.SpatialArea.aroundWorld(point, 2, 2));
		try
		{
			SlayerRoutePlan.Leg.builder(
				"unsafe", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Use the entrance")
				.target(SlayerRoutePlan.RouteTarget.point(
					"Entrance", point, 2))
				.interactionInstruction("Click Enter")
				.readyWhen(proximity)
				.completeWhen(proximity)
				.then("terminal")
				.build();
			fail("Proximity alone must not complete manual transitions");
		}
		catch (final IllegalArgumentException expected)
		{
			assertTrue(expected.getMessage().contains(
				"confirmed resultant evidence"));
		}
	}

	@Test
	public void manualLegRejectsAnyOfClickAndResultBecauseClickCanWinAlone()
	{
		final WorldPoint point = new WorldPoint(3200, 3200, 0);
		final SlayerRoutePredicate.ObjectActionSpec entrance =
			SlayerRoutePredicate.ObjectActionSpec.named("Boss gate", "Enter");
		try
		{
			SlayerRoutePlan.Leg.builder(
				"unsafe-click", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter the boss room")
				.target(SlayerRoutePlan.RouteTarget.point("Boss gate", point, 2))
				.interactionInstruction("Click Enter on the boss gate")
				.readyWhen(SlayerRoutePredicate.trackedObjectAction(entrance))
				.completeWhen(SlayerRoutePredicate.anyOf(
					SlayerRoutePredicate.observedObjectInteraction(entrance),
					SlayerRoutePredicate.exactNpc("Reviewed boss")))
				.then("terminal")
				.build();
			fail("A click-only branch must not complete a manual transition");
		}
		catch (final IllegalArgumentException expected)
		{
			assertTrue(expected.getMessage().contains("menu click alone"));
		}
	}

	@Test
	public void observedClickMayOnlyCorroborateIndependentResultEvidence()
	{
		final WorldPoint point = new WorldPoint(3200, 3200, 0);
		final SlayerRoutePredicate.ObjectActionSpec entrance =
			SlayerRoutePredicate.ObjectActionSpec.named("Boss gate", "Enter");
		final SlayerRoutePlan.Leg leg = SlayerRoutePlan.Leg.builder(
				"safe-click", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
			.guidance("Enter the boss room")
			.target(SlayerRoutePlan.RouteTarget.point("Boss gate", point, 2))
			.interactionInstruction("Click Enter on the boss gate")
			.readyWhen(SlayerRoutePredicate.trackedObjectAction(entrance))
			.completeWhen(SlayerRoutePredicate.allOf(
				SlayerRoutePredicate.observedObjectInteraction(entrance),
				SlayerRoutePredicate.exactNpc("Reviewed boss")))
			.then("terminal")
			.build();

		assertFalse(leg.getCompletionPredicate().canMatchObservedInteractionAlone());
	}

	@Test
	public void exactNpcIsAcceptedAsResultantManualCompletion()
	{
		final WorldPoint point = new WorldPoint(3200, 3200, 0);
		final SlayerRoutePredicate.ObjectActionSpec entrance =
			SlayerRoutePredicate.ObjectActionSpec.named("Boss gate", "Enter");
		final SlayerRoutePlan.Leg leg = SlayerRoutePlan.Leg.builder(
				"gate", SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
			.guidance("Enter the boss room")
			.target(SlayerRoutePlan.RouteTarget.point("Boss gate", point, 2))
			.interactionInstruction("Click Enter on the boss gate")
			.readyWhen(SlayerRoutePredicate.trackedObjectAction(entrance))
			.completeWhen(SlayerRoutePredicate.exactNpc("Reviewed boss"))
			.then("terminal")
			.build();

		assertEquals(SlayerRoutePlan.LegKind.MANUAL_INTERACTION, leg.getKind());
		assertTrue(leg.getCompletionPredicate().containsKind(
			SlayerRoutePredicate.Kind.EXACT_NPC));
	}

	private static SlayerRoutePlan terminalOnlyPlan(
		final String id,
		final SlayerRoutePredicate terminalPredicate,
		final SlayerRoutePredicate.SpatialArea retention)
	{
		return SlayerRoutePlan.builder(id)
			.startAt("terminal")
			.activityRetention(SlayerRoutePlan.ActivityRetention.of(retention))
			.addLeg(terminalLeg("terminal", terminalPredicate))
			.build();
	}

	private static SlayerRoutePlan.Leg terminalLeg(
		final String id,
		final SlayerRoutePredicate terminalPredicate)
	{
		return SlayerRoutePlan.Leg.builder(id, SlayerRoutePlan.LegKind.TERMINAL)
			.guidance("Stop routing at the reviewed encounter")
			.terminal(SlayerRoutePlan.TerminalSpec.of(
				"Reviewed encounter terminal", terminalPredicate))
			.build();
	}
}
