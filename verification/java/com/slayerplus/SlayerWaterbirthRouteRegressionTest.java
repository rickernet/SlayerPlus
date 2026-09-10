package com.slayerplus;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class SlayerWaterbirthRouteRegressionTest {
  private static final List<WorldPoint> ROUTE =
      Arrays.asList(
          new WorldPoint(2620, 3684, 0),
          new WorldPoint(2543, 3741, 0),
          new WorldPoint(2546, 10144, 0),
          new WorldPoint(1807, 4405, 3),
          new WorldPoint(1823, 4404, 2));

  @Test
  public void holdsAtMainlandDockUntilBoatTransition() {
    assertEquals(
        ROUTE.get(0), SlayerPlusPlugin.nextUnreachedStage(new WorldPoint(2621, 3683, 0), ROUTE));
  }

  @Test
  public void doesNotSkipShortcutOnWaterbirthArrival() {
    assertEquals(
        ROUTE.get(1), SlayerPlusPlugin.nextUnreachedStage(new WorldPoint(2546, 3755, 0), ROUTE));
  }

  @Test
  public void routesFromShortcutToCaveEntrance() {
    assertEquals(
        ROUTE.get(1), SlayerPlusPlugin.nextUnreachedStage(new WorldPoint(2547, 3744, 0), ROUTE));
  }

  @Test
  public void holdsAtCaveEntranceUntilDungeonTransition() {
    assertEquals(
        ROUTE.get(1), SlayerPlusPlugin.nextUnreachedStage(new WorldPoint(2543, 3741, 0), ROUTE));
  }

  @Test
  public void holdsAtDungeonLadderUntilTransition() {
    assertEquals(
        ROUTE.get(2), SlayerPlusPlugin.nextUnreachedStage(new WorldPoint(2546, 10144, 0), ROUTE));
  }

  @Test
  public void doesNotRestartRouteAfterDungeonLadderTransition() {
    assertEquals(
        ROUTE.get(3), SlayerPlusPlugin.nextUnreachedStage(new WorldPoint(1799, 4406, 3), ROUTE));
  }

  @Test
  public void usesRecordedPlaneForNextDungeonStage() {
    assertEquals(
        ROUTE.get(4), SlayerPlusPlugin.nextUnreachedStage(new WorldPoint(1815, 4405, 2), ROUTE));
  }

  @Test
  public void maxCapeWaterbirthRouteHighlightsHome() {
    assertEquals(true, SlayerPlusPlugin.usesPohTravelItem("Max cape"));
    assertEquals(true, SlayerPlusPlugin.usesPohTravelItem("Construction cape"));
    assertEquals(false, SlayerPlusPlugin.usesPohTravelItem("Games necklace(8)"));
    assertEquals(
        "home",
        SlayerPlusPlugin.pohTravelDestination(
            "Waterbirth Island Dungeon", "Max cape", "rellekka"));
    assertEquals(
        "rellekka",
        SlayerPlusPlugin.pohTravelDestination(
            "Waterbirth Island Dungeon", "Fremennik sea boots 4", "rellekka"));
    assertEquals(
        "home",
        SlayerPlusPlugin.pohTravelDestination(
            "Ancient Guthixian Temple", "Max cape", "tears of guthix"));
  }
}
