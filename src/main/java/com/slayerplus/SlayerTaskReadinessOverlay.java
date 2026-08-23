package com.slayerplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class SlayerTaskReadinessOverlay extends OverlayPanel {
  private static final Color READY = new Color(74, 214, 126);
  private static final Color WARNING = new Color(255, 171, 64);
  private static final Color MISSING = new Color(255, 92, 92);
  private static final Color MUTED = new Color(190, 190, 190);
  private final Client client;
  private final SlayerPlusPlugin plugin;
  private PreparationCatalog.PreparationPlan renderedPlan;
  private String renderedFingerprint = "";
  private boolean rowsVisible;

  @Inject
  private SlayerTaskReadinessOverlay(Client client, SlayerPlusPlugin plugin) {
    super(plugin);
    this.client = client;
    this.plugin = plugin;
    setPosition(OverlayPosition.TOP_RIGHT);
    setPriority(PRIORITY_HIGH);
    setLayer(OverlayLayer.ABOVE_SCENE);
    panelComponent.setPreferredSize(new Dimension(330, 0));
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    if (client.getGameState() != GameState.LOGGED_IN || !plugin.isGuidedSessionActiveForOverlay()) {
      clearRows();
      return null;
    }
    PreparationCatalog.PreparationPlan plan = plugin.getCurrentPreparationForOverlay();
    boolean setupRequired = plan != null && plan.isActive() && !plan.isReady();
    if (!setupRequired) {
      clearRows();
      return null;
    }
    if (rowsVisible && plan == renderedPlan) {
      return super.render(graphics);
    }
    String fingerprint = renderFingerprint(plan);
    if (rowsVisible && fingerprint.equals(renderedFingerprint)) {
      renderedPlan = plan;
      return super.render(graphics);
    }
    panelComponent.getChildren().clear();
    renderedPlan = plan;
    renderedFingerprint = fingerprint;
    rowsVisible = true;
    panelComponent
        .getChildren()
        .add(
            TitleComponent.builder()
                .text(
                    plan.getEncounterName().isEmpty()
                        ? "Slayer setup required"
                        : plan.getEncounterName() + " setup required")
                .color(WARNING)
                .build());
    panelComponent
        .getChildren()
        .add(
            LineComponent.builder()
                .left(plan.getSpellbookName().isEmpty() ? "Spellbook" : plan.getSpellbookName())
                .right(plan.isSpellbookReady() ? "Ready" : "SWITCH SPELLBOOK")
                .rightColor(plan.isSpellbookReady() ? READY : MISSING)
                .build());
    if (!plan.isLevelReady()) {
      panelComponent
          .getChildren()
          .add(
              LineComponent.builder()
                  .left("Magic level")
                  .right(plan.getMagicLevel() + " / " + plan.getRequiredMagicLevel())
                  .rightColor(MISSING)
                  .build());
    }
    for (PreparationCatalog.RuneStatus rune : plan.getRuneStatuses()) {
      boolean runeReady = rune.isReadyForOneCast();
      panelComponent
          .getChildren()
          .add(
              LineComponent.builder()
                  .left(rune.getName() + " runes")
                  .leftColor(MUTED)
                  .right(rune.getAvailable() + " / " + rune.getPerCast())
                  .rightColor(runeReady ? READY : MISSING)
                  .build());
    }
    if (!plan.getSpellName().isEmpty()) {
      panelComponent
          .getChildren()
          .add(
              LineComponent.builder()
                  .left(plan.getSpellName() + " casts available")
                  .right(Integer.toString(plan.getCastsAvailable()))
                  .rightColor(plan.isRunesReady() ? READY : MISSING)
                  .build());
    }
    return super.render(graphics);
  }

  private void clearRows() {
    if (!rowsVisible && renderedPlan == null) {
      return;
    }
    panelComponent.getChildren().clear();
    renderedPlan = null;
    renderedFingerprint = "";
    rowsVisible = false;
  }

  static String renderFingerprint(PreparationCatalog.PreparationPlan plan) {
    if (plan == null || !plan.isActive() || plan.isReady()) {
      return "hidden";
    }
    StringBuilder value =
        new StringBuilder(128)
            .append(plan.getEncounterName())
            .append('|')
            .append(plan.getSpellbookName())
            .append('|')
            .append(plan.isSpellbookReady())
            .append('|')
            .append(plan.isLevelReady())
            .append('|')
            .append(plan.getMagicLevel())
            .append('|')
            .append(plan.getRequiredMagicLevel())
            .append('|')
            .append(plan.getSpellName())
            .append('|')
            .append(plan.getCastsAvailable())
            .append('|')
            .append(plan.isRunesReady());
    for (PreparationCatalog.RuneStatus rune : plan.getRuneStatuses()) {
      value
          .append('|')
          .append(rune.getName())
          .append(':')
          .append(rune.getAvailable())
          .append('/')
          .append(rune.getPerCast());
    }
    return value.toString();
  }
}
