package com.slayerplus;

import java.util.Arrays;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlayerRoutePlanRuntimeRegressionTest
{
	@Test
	public void loadedEntranceKeepsRoutingUntilPlayerReachesApproachRadius()
	{
		final WorldPoint entrance = new WorldPoint(3112, 3670, 0);
		final RoutePlan.RouteTarget target =
			RoutePlan.RouteTarget.point("Reviewed cave entrance", entrance, 4);

		assertFalse(SlayerPlusPlugin.shouldPauseForRoutePlanForTest(
			RoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			new WorldPoint(3080, 3670, 0), target));
		assertTrue(SlayerPlusPlugin.shouldPauseForRoutePlanForTest(
			RoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			new WorldPoint(3109, 3670, 0), target));
	}

	@Test
	public void wrongPlaneNeverPausesAtManualApproach()
	{
		final WorldPoint entrance = new WorldPoint(3112, 3670, 0);
		final RoutePlan.RouteTarget target =
			RoutePlan.RouteTarget.point("Reviewed cave entrance", entrance, 4);

		assertFalse(SlayerPlusPlugin.shouldPauseForRoutePlanForTest(
			RoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			new WorldPoint(3112, 3670, 1), target));
	}

	@Test
	public void anyReviewedApproachPointCanOwnTheManualHandoff()
	{
		final RoutePlan.RouteTarget target =
			RoutePlan.RouteTarget.points(
				"Reviewed entrances",
				Arrays.asList(
					new WorldPoint(3073, 3654, 0),
					new WorldPoint(3067, 3740, 0),
					new WorldPoint(3124, 3831, 0)),
				5);

		assertTrue(SlayerPlusPlugin.shouldPauseForRoutePlanForTest(
			RoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			new WorldPoint(3120, 3831, 0), target));
		assertFalse(SlayerPlusPlugin.shouldPauseForRoutePlanForTest(
			RoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			new WorldPoint(3200, 3831, 0), target));
	}

	@Test
	public void instructionOnlyManualLegWaitsButRoutingLegDoesNot()
	{
		final RoutePlan.RouteTarget instruction =
			RoutePlan.RouteTarget.instruction("Live-state dungeon handoff");

		assertTrue(SlayerPlusPlugin.shouldPauseForRoutePlanForTest(
			RoutePlan.ProgressStatus.WAITING_FOR_INTERACTION,
			new WorldPoint(3200, 3200, 0), instruction));
		assertFalse(SlayerPlusPlugin.shouldPauseForRoutePlanForTest(
			RoutePlan.ProgressStatus.ROUTING,
			new WorldPoint(3200, 3200, 0), instruction));
	}
}
