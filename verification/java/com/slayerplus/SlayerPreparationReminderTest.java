package com.slayerplus;

import static org.junit.Assert.*;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Test;

public class SlayerPreparationReminderTest {
  @Test
  public void runeConsumptionStopsSchedulingRefreshesAfterSetup() {
    assertFalse(SlayerPlusPlugin.shouldRefreshProgress(-1, true));
    assertTrue(SlayerPlusPlugin.shouldRefreshProgress(-1, false));
    assertTrue(SlayerPlusPlugin.shouldRefreshProgress(VarPlayerID.SLAYER_COUNT, true));
    assertTrue(SlayerPlusPlugin.shouldRefreshProgress(VarPlayerID.SLAYER_COUNT, false));
  }

  @Test
  public void fulfilledTaskDoesNotRecheckDuringCombatOrWorldHop() {
    for (int remaining = 100; remaining > 0; remaining--) {
      assertFalse(
          SlayerPlusPlugin.shouldRefreshPreparation(
              true, "task", "task", remaining + 1, remaining));
    }
    assertFalse(SlayerPlusPlugin.shouldRefreshPreparation(true, "task", "task", 75, 75));
    assertFalse(SlayerPlusPlugin.shouldRefreshPreparation(true, "task", "task", -1, 75));
  }

  @Test
  public void pendingAndNewTaskRequirementsAreCheckedAgain() {
    assertTrue(SlayerPlusPlugin.shouldRefreshPreparation(false, "task", "task", 75, 75));
    assertTrue(SlayerPlusPlugin.shouldRefreshPreparation(true, "old task", "new task", 75, 60));
    assertTrue(SlayerPlusPlugin.shouldRefreshPreparation(true, "same task", "same task", 1, 150));
    assertTrue(SlayerPlusPlugin.shouldRefreshPreparation(true, "", "task", 75, 75));
    assertTrue(SlayerPlusPlugin.shouldRefreshPreparation(true, "task|melee", "task|magic", 75, 75));
  }

  @Test
  public void cachedRowsSurviveRenderingAndEquivalentUpdates() {
    SlayerTaskReadinessOverlay overlay = new SlayerTaskReadinessOverlay(new SlayerPlusPlugin());
    overlay.update(plan(false, false, false));
    assertEquals(OverlayPosition.TOP_RIGHT, overlay.getPosition());
    assertFalse(overlay.isClearChildren());
    int count = overlay.getPanelComponent().getChildren().size();
    assertEquals(6, count); // Title, spellbook, spell, three rune rows; no explanatory footer.
    Object firstRow = overlay.getPanelComponent().getChildren().get(0);
    Graphics2D graphics = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB).createGraphics();
    try {
      assertNotNull(overlay.render(graphics));
      assertNotNull(overlay.render(graphics));
      assertEquals(count, overlay.getPanelComponent().getChildren().size());
      overlay.update(plan(false, false, false));
      assertSame(firstRow, overlay.getPanelComponent().getChildren().get(0));
    } finally {
      graphics.dispose();
    }
  }

  @Test
  public void panelHidesWhenReadyAndReturnsForAnotherIncompleteSetup() {
    SlayerTaskReadinessOverlay overlay = new SlayerTaskReadinessOverlay(new SlayerPlusPlugin());
    overlay.update(plan(false, true, false));
    assertFalse(overlay.getPanelComponent().getChildren().isEmpty());
    overlay.update(plan(false, false, true));
    assertFalse(overlay.getPanelComponent().getChildren().isEmpty());
    overlay.update(plan(true, true, true));
    assertTrue(overlay.getPanelComponent().getChildren().isEmpty());
    assertNull(overlay.render(null));
    overlay.update(plan(false, false, false));
    assertFalse(overlay.getPanelComponent().getChildren().isEmpty());
    overlay.update(PreparationCatalog.PreparationPlan.none());
    assertNull(overlay.render(null));
    assertTrue(overlay.getPanelComponent().getChildren().isEmpty());
  }

  @Test
  public void arceuusReminderListsEachActualSpellEvenWithNoRunes() {
    TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Araxxor",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Morytania Spider Cave",
            false);
    PreparationCatalog.PreparationPlan plan =
        PreparationCatalog.resolve(
            "Araxxor", "Morytania Spider Cave", strategy, null, null, null, Collections.emptyMap());
    assertTrue(plan.getSpellName().contains("Resurrect Greater Ghost"));
    assertTrue(plan.getSpellName().contains("Death Charge"));
    SlayerTaskReadinessOverlay overlay = new SlayerTaskReadinessOverlay(new SlayerPlusPlugin());
    overlay.update(plan);
    int spellCount = plan.getSpellName().split("\\s*\\+\\s*").length;
    assertEquals(
        2 + spellCount + (plan.isLevelReady() ? 0 : 1) + plan.getRuneStatuses().size(),
        overlay.getPanelComponent().getChildren().size());
  }

  private static PreparationCatalog.PreparationPlan plan(
      boolean ready, boolean bookReady, boolean runesReady) {
    return new PreparationCatalog.PreparationPlan(
        true,
        ready,
        "Setup",
        ready ? "Ready" : "Check spellbook and rune pouch.",
        "Load Water, Blood and Death runes",
        "",
        Collections.emptyList(),
        MethodRules.Spellbook.ANCIENT,
        bookReady,
        true,
        99,
        Arrays.asList(
            new PreparationCatalog.RuneStatus("Water", 6, runesReady ? 600 : 0),
            new PreparationCatalog.RuneStatus("Blood", 2, runesReady ? 200 : 0),
            new PreparationCatalog.RuneStatus("Death", 4, runesReady ? 400 : 0)),
        "Ice Barrage",
        runesReady ? 100 : 0,
        runesReady);
  }
}
