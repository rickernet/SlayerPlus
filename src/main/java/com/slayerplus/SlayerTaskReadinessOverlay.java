package com.slayerplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class SlayerTaskReadinessOverlay extends OverlayPanel {
  private static final Color READY = new Color(74, 214, 126);
  private static final Color WARNING = new Color(255, 171, 64);
  private static final Color MISSING = new Color(255, 92, 92);
  private static final Color MUTED = new Color(190, 190, 190);
  private String renderedFingerprint = "";
  private boolean rowsVisible;

  @Inject
  SlayerTaskReadinessOverlay(SlayerPlusPlugin plugin) {
    super(plugin);
    setPosition(OverlayPosition.TOP_RIGHT);
    setPriority(PRIORITY_HIGH);
    setLayer(OverlayLayer.ABOVE_SCENE);
    setResizable(false);
    setClearChildren(false);
    panelComponent.setPreferredSize(new Dimension(240, 0));
  }

  void update(PreparationCatalog.PreparationPlan plan) {
    boolean setupRequired = plan != null && plan.isActive() && !plan.isReady();
    if (!setupRequired) {
      clearRows();
      return;
    }
    String fingerprint = renderFingerprint(plan);
    if (rowsVisible && fingerprint.equals(renderedFingerprint)) {
      return;
    }
    panelComponent.getChildren().clear();
    renderedFingerprint = fingerprint;
    rowsVisible = true;
    panelComponent
        .getChildren()
        .add(
            TitleComponent.builder()
                .text("Slayer setup required")
                .color(WARNING)
                .build());
    panelComponent
        .getChildren()
        .add(
            LineComponent.builder()
                .left(plan.spellbookName().isEmpty() ? "Spellbook" : plan.spellbookName())
                .right(plan.isSpellbookReady() ? "Ready" : "Switch")
                .rightColor(plan.isSpellbookReady() ? READY : MISSING)
                .build());
    if (!plan.getRequiredItemName().isEmpty()) {
      panelComponent
          .getChildren()
          .add(
              LineComponent.builder()
                  .left(plan.getRequiredItemName())
                  .leftColor(MUTED)
                  .right(plan.isRequiredItemReady() ? "Ready" : "Missing")
                  .rightColor(plan.isRequiredItemReady() ? READY : MISSING)
                  .build());
    }
    if (!plan.getSpellName().isEmpty()) {
      for (String spell : plan.getSpellName().split("\\s*\\+\\s*")) {
        panelComponent
            .getChildren()
            .add(LineComponent.builder().left("Spell: " + spell).leftColor(MUTED).build());
      }
    }
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
      boolean runeReady = rune.isReady();
      panelComponent
          .getChildren()
          .add(
              LineComponent.builder()
                  .left(rune.getName() + " runes")
                  .leftColor(MUTED)
                  .right(rune.getAvailable() + " / " + rune.getRequired())
                  .rightColor(runeReady ? READY : MISSING)
                  .build());
    }
    if (plan.getCastsAvailable() > 0) {
      panelComponent
          .getChildren()
          .add(
              LineComponent.builder()
                  .left("Casts available")
                  .right(Integer.toString(plan.getCastsAvailable()))
                  .rightColor(plan.isRunesReady() ? READY : MISSING)
                  .build());
    }
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    return rowsVisible ? super.render(graphics) : null;
  }

  private void clearRows() {
    if (!rowsVisible) {
      return;
    }
    panelComponent.getChildren().clear();
    renderedFingerprint = "";
    rowsVisible = false;
  }

  static String renderFingerprint(PreparationCatalog.PreparationPlan plan) {
    if (plan == null || !plan.isActive() || plan.isReady()) {
      return "hidden";
    }
    StringBuilder value =
        new StringBuilder(128)
            .append(plan.spellbookName())
            .append('|')
            .append(plan.isSpellbookReady())
            .append('|')
            .append(plan.isLevelReady())
            .append('|')
            .append(plan.getMagicLevel())
            .append('|')
            .append(plan.getRequiredMagicLevel())
            .append('|')
            .append(plan.getRequiredItemName())
            .append('|')
            .append(plan.isRequiredItemReady())
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
          .append(rune.getRequired());
    }
    return value.toString();
  }
}
