package com.slayerplus;

import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SlayerRouteLifecycleRegressionTest
{
	@Test
	public void exactPlansKeepExactLegacyProfileIdentity()
	{
		for (final SlayerRoutePlanCatalog.RouteKey key
			: SlayerRoutePlanCatalog.keysForTest())
		{
			assertTrue(
				"Exact plan lost its exact RouteProfile: " + key,
				RouteCatalog.hasExplicitProfileForTest(
					key.getTaskName(), key.getLocation(), key.isBoss()
				)
			);
		}
	}

	@Test
	public void crossLayerStagedRouteContinuesInwardFromIntermediateLayer()
	{
		final RouteCatalog.RouteProfile route = RouteCatalog.resolve(
			"Abyssal demons", "Catacombs of Kourend", false
		);
		assertNotNull(route);
		assertTrue(route.isStaged());

		final WorldPoint intermediateLayer = new WorldPoint(2000, 6500, 0);
		assertFalse(route.insideArea(intermediateLayer));
		assertFalse(route.shouldRouteToSurfaceAccess(intermediateLayer));

		final SlayerRouteCoordinator.Stage stage = resolveWithoutLiveTargets(
			route, intermediateLayer
		);
		assertTrue(stage.isValid());
		assertEquals(SlayerRouteCoordinator.StageKind.INTERIOR, stage.getKind());
		assertEquals(route.getPrimaryDestination(), stage.getDestination());
	}

	@Test
	public void sameLayerStagedRouteKeepsSurfaceStageUntilDestinationArea()
	{
		final RouteCatalog.RouteProfile route = RouteCatalog.resolve(
			"Vorkath", "Ungael", true
		);
		assertNotNull(route);
		assertTrue(route.isStaged());

		final WorldPoint sameLayerOutsideDestination = new WorldPoint(2500, 3900, 0);
		assertFalse(route.insideArea(sameLayerOutsideDestination));
		assertTrue(route.shouldRouteToSurfaceAccess(sameLayerOutsideDestination));

		final SlayerRouteCoordinator.Stage beforeDestination =
			resolveWithoutLiveTargets(route, sameLayerOutsideDestination);
		assertTrue(beforeDestination.isValid());
		assertEquals(
			SlayerRouteCoordinator.StageKind.SURFACE_APPROACH,
			beforeDestination.getKind()
		);
		assertEquals(route.getSurfaceAccess(), beforeDestination.getDestination());

		final WorldPoint ungaelLanding = new WorldPoint(2277, 4034, 0);
		assertTrue(route.insideArea(ungaelLanding));
		final SlayerRouteCoordinator.Stage afterDestination =
			resolveWithoutLiveTargets(route, ungaelLanding);
		assertTrue(afterDestination.isValid());
		assertEquals(
			SlayerRouteCoordinator.StageKind.INTERIOR,
			afterDestination.getKind()
		);
		assertEquals(route.getPrimaryDestination(), afterDestination.getDestination());
	}

	@Test
	public void npcOnlyProfilesPermitExactNpcDiscoveryAfterSceneTransition()
	{
		final WorldPoint loadedScene = new WorldPoint(5000, 5000, 0);
		assertExactNpcDiscoveryAfterTransition(
			RouteCatalog.resolve(
				"Lifecycle regression NPC", "Uncatalogued lifecycle area", false
			),
			RouteCatalog.RouteMode.NPC_ONLY,
			loadedScene
		);
		assertExactNpcDiscoveryAfterTransition(
			RouteCatalog.resolve(
				"Lifecycle regression boss", "Uncatalogued lifecycle area", true
			),
			RouteCatalog.RouteMode.BOSS_NPC_ONLY,
			loadedScene
		);
	}

	@Test
	public void accessThenNpcProfilesPermitExactNpcDiscoveryAfterTransition()
	{
		final WorldPoint interiorScene = new WorldPoint(3417, 9970, 3);
		assertExactNpcDiscoveryAfterTransition(
			RouteCatalog.resolve(
				"Lifecycle regression NPC", "Slayer Tower", false
			),
			RouteCatalog.RouteMode.ACCESS_THEN_NPC,
			interiorScene
		);
		assertExactNpcDiscoveryAfterTransition(
			RouteCatalog.resolve(
				"Lifecycle regression boss", "Slayer Tower", true
			),
			RouteCatalog.RouteMode.BOSS_ACCESS_THEN_NPC,
			interiorScene
		);
	}

	private static void assertExactNpcDiscoveryAfterTransition(
		final RouteCatalog.RouteProfile route,
		final RouteCatalog.RouteMode expectedMode,
		final WorldPoint here)
	{
		assertNotNull(route);
		assertEquals(expectedMode, route.getMode());

		final SlayerRouteCoordinator.Stage waiting = resolveWithoutLiveTargets(
			route, here
		);
		assertFalse(waiting.isValid());
		assertEquals(SlayerRouteCoordinator.StageKind.WAIT_FOR_NPC, waiting.getKind());

		assertTrue(SlayerPlusPlugin.shouldAttemptExactNpcDiscoveryForTest(
			route, here, 64
		));

		final WorldPoint exactNpc = new WorldPoint(
			here.getX() + 2,
			here.getY() + 1,
			here.getPlane()
		);
		final SlayerRouteCoordinator.Stage discovered = SlayerRouteCoordinator.resolve(
			route,
			here,
			null, Collections.emptySet(),
			null, Collections.emptySet(),
			exactNpc, Collections.singleton(exactNpc),
			null, Collections.emptySet()
		);
		assertTrue(discovered.isValid());
		assertEquals(SlayerRouteCoordinator.StageKind.EXACT_NPC, discovered.getKind());
		assertEquals(exactNpc, discovered.getDestination());
	}

	private static SlayerRouteCoordinator.Stage resolveWithoutLiveTargets(
		final RouteCatalog.RouteProfile route,
		final WorldPoint here)
	{
		return SlayerRouteCoordinator.resolve(
			route,
			here,
			null, Collections.emptySet(),
			null, Collections.emptySet(),
			null, Collections.emptySet(),
			null, Collections.emptySet()
		);
	}
}
