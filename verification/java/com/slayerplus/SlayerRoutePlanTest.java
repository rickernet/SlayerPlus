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

		final RouteCheck transition =
			RouteCheck.coordinateLayerTransition(
				RouteCheck.CoordinateLayer.worldRegions(
					0, surface.getRegionID()),
				RouteCheck.CoordinateLayer.worldRegions(
					0, reviewedInterior.getRegionID())
			);

		assertFalse(transition.matches(RouteEvidence.builder()
			.previousWorldPoint(surface)
			.worldPoint(unrelatedDungeon)
			.build()));
		assertTrue(transition.matches(RouteEvidence.builder()
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

		final RoutePlan plan = terminalOnlyPlan(
			"same-layer-cave",
			RouteCheck.anyOf(
				RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(
						bossCenter, 1, 1)),
				RouteCheck.exactNpc("Artio")
			),
			RouteCheck.SpatialArea.aroundWorld(
				bossCenter, 192, 192)
		);
		final RouteEvidence entranceEvidence = RouteEvidence.builder()
			.worldPoint(caveEntrance)
			.build();

		assertTrue(plan.isWithinActivityRetention(entranceEvidence));
		assertFalse(plan.hasArrived(entranceEvidence));
		assertEquals(
			RoutePlan.ProgressStatus.ROUTING,
			plan.evaluate(plan.initialCursor(), entranceEvidence).getStatus()
		);
	}

	@Test
	public void unrelatedPohInstanceCannotSatisfyBossTerminal()
	{
		final WorldPoint bossTemplate = new WorldPoint(3100, 9700, 0);
		final WorldPoint pohTemplate = new WorldPoint(1856, 5696, 0);
		final RoutePlan plan = terminalOnlyPlan(
			"instance-terminal",
			RouteCheck.anyOf(
				RouteCheck.templateArea(
					RouteCheck.SpatialArea.aroundTemplate(
						bossTemplate, 3, 3)),
				RouteCheck.visibleWidgetComponent(
					REVIEWED_ARENA_WIDGET_COMPONENT),
				RouteCheck.exactNpc("The reviewed boss")
			),
			RouteCheck.SpatialArea.aroundTemplate(
				bossTemplate, 96, 96)
		);

		final RouteEvidence poh = RouteEvidence.builder()
			.instanced(true)
			.worldPoint(new WorldPoint(3200, 3200, 0))
			.templatePoint(pohTemplate)
			.build();
		assertTrue(poh.isInstanced());
		assertFalse(plan.hasArrived(poh));

		final RouteEvidence reviewedArenaWidget =
			RouteEvidence.builder()
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
		final RouteCheck.ObjectActionSpec barrier =
			RouteCheck.ObjectActionSpec.named(
				"Glowing barrier", "Pass"
			);
		final RoutePlan plan = RoutePlan.builder("manual-barrier")
			.startAt("barrier")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(
					barrierPoint, 64, 64)))
			.addLeg(RoutePlan.Leg.builder(
				"barrier", RoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Cross the glowing barrier")
				.target(RoutePlan.RouteTarget.point(
					"Glowing barrier", barrierPoint, 2))
				.interactionInstruction("Click Pass on the glowing barrier")
				.readyWhen(RouteCheck.trackedObjectAction(barrier))
				.completeWhen(RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(
						interiorLanding, 3, 3)))
				.then("inside")
				.build())
			.addLeg(terminalLeg(
				"inside",
				RouteCheck.exactNpc("Inner guardian")))
			.build();

		final RouteEvidence objectVisible = RouteEvidence.builder()
			.worldPoint(barrierPoint)
			.trackedObjectAction(RouteEvidence.ObjectAction.named(
				"Glowing barrier", "Pass"))
			.build();
		final RoutePlan.Evaluation waiting = plan.evaluate(
			plan.initialCursor(), objectVisible
		);
		assertEquals(
			RoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			waiting.getStatus()
		);
		assertFalse(waiting.hasAdvanced());
		assertFalse(waiting.hasArrived());

		final RouteEvidence interactionObserved =
			RouteEvidence.builder()
				.worldPoint(barrierPoint)
				.observedObjectInteraction(
					RouteEvidence.ObjectAction.named(
						"Glowing barrier", "Pass"))
				.build();
		final RoutePlan.Evaluation failedClick = plan.evaluate(
			waiting.getCursor(), interactionObserved
		);
		assertFalse(failedClick.hasAdvanced());
		assertEquals("barrier", failedClick.getActiveLeg().getId());

		final RoutePlan.Evaluation advanced = plan.evaluate(
			failedClick.getCursor(), RouteEvidence.builder()
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
		final RouteCheck.ObjectActionSpec barrier =
			RouteCheck.ObjectActionSpec.named(
				"Ancient door", "Enter"
			);

		final RoutePlan plan = RoutePlan.builder("three-stage")
			.startAt("surface")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(
					terminalPoint, 128, 128)))
			.addLeg(RoutePlan.Leg.builder(
				"surface", RoutePlan.LegKind.TRAVEL)
				.guidance("Travel to the dungeon entrance")
				.target(RoutePlan.RouteTarget.point(
					"Dungeon entrance", access, 3))
				.completeWhen(RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(
						access, 3, 3)))
				.then("door")
				.build())
			.addLeg(RoutePlan.Leg.builder(
				"door", RoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter through the ancient door")
				.target(RoutePlan.RouteTarget.point(
					"Ancient door", barrierPoint, 2))
				.interactionInstruction("Click Enter on the ancient door")
				.readyWhen(RouteCheck.trackedObjectAction(barrier))
				.completeWhen(RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(
						barrierLanding, 2, 2)))
				.then("encounter")
				.build())
			.addLeg(terminalLeg(
				"encounter",
				RouteCheck.anyOf(
					RouteCheck.worldArea(
						RouteCheck.SpatialArea.aroundWorld(
							terminalPoint, 2, 2)),
					RouteCheck.exactNpc("Ancient guardian")
				)))
			.build();

		RoutePlan.Cursor cursor = plan.initialCursor();
		RoutePlan.Evaluation evaluation = plan.evaluate(
			cursor,
			RouteEvidence.builder().worldPoint(access).build()
		);
		assertTrue(evaluation.hasAdvanced());
		assertEquals("door", evaluation.getActiveLeg().getId());
		cursor = evaluation.getCursor();

		evaluation = plan.evaluate(
			cursor,
			RouteEvidence.builder()
				.worldPoint(barrierPoint)
				.trackedObjectAction(RouteEvidence.ObjectAction.named(
					"Ancient door", "Enter"))
				.build()
		);
		assertEquals(
			RoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			evaluation.getStatus()
		);
		assertFalse(evaluation.hasAdvanced());

		evaluation = plan.evaluate(
			evaluation.getCursor(),
			RouteEvidence.builder()
				.worldPoint(barrierPoint)
				.observedObjectInteraction(
					RouteEvidence.ObjectAction.named(
						"Ancient door", "Enter"))
				.build()
		);
		assertFalse(evaluation.hasAdvanced());
		assertEquals("door", evaluation.getActiveLeg().getId());

		evaluation = plan.evaluate(
			evaluation.getCursor(),
			RouteEvidence.builder().worldPoint(barrierLanding).build()
		);
		assertTrue(evaluation.hasAdvanced());
		assertEquals("encounter", evaluation.getActiveLeg().getId());
		assertFalse(evaluation.hasArrived());

		evaluation = plan.evaluate(
			evaluation.getCursor(),
			RouteEvidence.builder()
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
		final RoutePlan plan = RoutePlan.builder("branching")
			.startAt("choose")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(
					fallback, 64, 64)))
			.addLeg(RoutePlan.Leg.builder(
				"choose", RoutePlan.LegKind.DECISION)
				.guidance("Choose the available reviewed route")
				.completeWhen(RouteCheck.always())
				.thenWhen(
					"Arena widget is already loaded",
					RouteCheck.visibleWidgetComponent(
						REVIEWED_ARENA_WIDGET_COMPONENT),
					"encounter")
				.then("fallback")
				.build())
			.addLeg(RoutePlan.Leg.builder(
				"fallback", RoutePlan.LegKind.TRAVEL)
				.guidance("Walk to the fallback entrance")
				.target(RoutePlan.RouteTarget.point(
					"Fallback entrance", fallback, 2))
				.completeWhen(RouteCheck.worldArea(
					RouteCheck.SpatialArea.aroundWorld(
						fallback, 2, 2)))
				.then("encounter")
				.build())
			.addLeg(terminalLeg(
				"encounter",
				RouteCheck.exactNpc("Reviewed boss")))
			.build();

		final RoutePlan.Evaluation direct = plan.evaluate(
			plan.initialCursor(),
			RouteEvidence.builder()
				.visibleWidgetComponent(REVIEWED_ARENA_WIDGET_COMPONENT)
				.build()
		);
		assertEquals("encounter", direct.getActiveLeg().getId());

		final RoutePlan.Evaluation fallbackRoute = plan.evaluate(
			plan.initialCursor(), RouteEvidence.empty()
		);
		assertEquals("fallback", fallbackRoute.getActiveLeg().getId());
	}

	@Test
	public void graphValidationRejectsCycleAndUnreachableTerminal()
	{
		final WorldPoint point = new WorldPoint(3200, 3200, 0);
		final RouteCheck reachedPoint =
			RouteCheck.worldArea(
				RouteCheck.SpatialArea.aroundWorld(point, 1, 1));
		final RoutePlan.Builder builder = RoutePlan.builder("cycle")
			.startAt("a")
			.activityRetention(RoutePlan.ActivityRetention.of(
				RouteCheck.SpatialArea.aroundWorld(point, 8, 8)))
			.addLeg(RoutePlan.Leg.builder(
				"a", RoutePlan.LegKind.TRAVEL)
				.guidance("Reach A")
				.target(RoutePlan.RouteTarget.point("A", point, 1))
				.completeWhen(reachedPoint)
				.then("b")
				.build())
			.addLeg(RoutePlan.Leg.builder(
				"b", RoutePlan.LegKind.TRAVEL)
				.guidance("Reach B")
				.target(RoutePlan.RouteTarget.point("B", point, 1))
				.completeWhen(reachedPoint)
				.then("a")
				.build())
			.addLeg(terminalLeg(
				"unreachable", RouteCheck.exactNpc("Boss")));

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
		final RouteCheck proximity = RouteCheck.worldArea(
			RouteCheck.SpatialArea.aroundWorld(point, 2, 2));
		try
		{
			RoutePlan.Leg.builder(
				"unsafe", RoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Use the entrance")
				.target(RoutePlan.RouteTarget.point(
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
		final RouteCheck.ObjectActionSpec entrance =
			RouteCheck.ObjectActionSpec.named("Boss gate", "Enter");
		try
		{
			RoutePlan.Leg.builder(
				"unsafe-click", RoutePlan.LegKind.MANUAL_INTERACTION)
				.guidance("Enter the boss room")
				.target(RoutePlan.RouteTarget.point("Boss gate", point, 2))
				.interactionInstruction("Click Enter on the boss gate")
				.readyWhen(RouteCheck.trackedObjectAction(entrance))
				.completeWhen(RouteCheck.anyOf(
					RouteCheck.observedObjectInteraction(entrance),
					RouteCheck.exactNpc("Reviewed boss")))
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
		final RouteCheck.ObjectActionSpec entrance =
			RouteCheck.ObjectActionSpec.named("Boss gate", "Enter");
		final RoutePlan.Leg leg = RoutePlan.Leg.builder(
				"safe-click", RoutePlan.LegKind.MANUAL_INTERACTION)
			.guidance("Enter the boss room")
			.target(RoutePlan.RouteTarget.point("Boss gate", point, 2))
			.interactionInstruction("Click Enter on the boss gate")
			.readyWhen(RouteCheck.trackedObjectAction(entrance))
			.completeWhen(RouteCheck.allOf(
				RouteCheck.observedObjectInteraction(entrance),
				RouteCheck.exactNpc("Reviewed boss")))
			.then("terminal")
			.build();

		assertFalse(leg.getCompletionPredicate().canMatchObservedInteractionAlone());
	}

	@Test
	public void exactNpcIsAcceptedAsResultantManualCompletion()
	{
		final WorldPoint point = new WorldPoint(3200, 3200, 0);
		final RouteCheck.ObjectActionSpec entrance =
			RouteCheck.ObjectActionSpec.named("Boss gate", "Enter");
		final RoutePlan.Leg leg = RoutePlan.Leg.builder(
				"gate", RoutePlan.LegKind.MANUAL_INTERACTION)
			.guidance("Enter the boss room")
			.target(RoutePlan.RouteTarget.point("Boss gate", point, 2))
			.interactionInstruction("Click Enter on the boss gate")
			.readyWhen(RouteCheck.trackedObjectAction(entrance))
			.completeWhen(RouteCheck.exactNpc("Reviewed boss"))
			.then("terminal")
			.build();

		assertEquals(RoutePlan.LegKind.MANUAL_INTERACTION, leg.getKind());
		assertTrue(leg.getCompletionPredicate().containsKind(
			RouteCheck.Kind.EXACT_NPC));
	}

	private static RoutePlan terminalOnlyPlan(
		final String id,
		final RouteCheck terminalPredicate,
		final RouteCheck.SpatialArea retention)
	{
		return RoutePlan.builder(id)
			.startAt("terminal")
			.activityRetention(RoutePlan.ActivityRetention.of(retention))
			.addLeg(terminalLeg("terminal", terminalPredicate))
			.build();
	}

	private static RoutePlan.Leg terminalLeg(
		final String id,
		final RouteCheck terminalPredicate)
	{
		return RoutePlan.Leg.builder(id, RoutePlan.LegKind.TERMINAL)
			.guidance("Stop routing at the reviewed encounter")
			.terminal(RoutePlan.TerminalSpec.of(
				"Reviewed encounter terminal", terminalPredicate))
			.build();
	}
}
