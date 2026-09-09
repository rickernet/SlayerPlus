package com.slayerplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

class SlayerTravelItemOverlay extends WidgetItemOverlay {
  private static final Color TRAVEL_ITEM_COLOR = new Color(0, 255, 80);
  private final ItemManager itemManager;
  private final SlayerPlusPlugin plugin;
  private final Client client;
  private int activeItemId = -1;
  private int cachedItemId = -1;
  private int cachedQuantity = -1;
  private BufferedImage cachedOutline;

  @Inject
  private SlayerTravelItemOverlay(ItemManager itemManager, SlayerPlusPlugin plugin, Client client) {
    this.itemManager = itemManager;
    this.plugin = plugin;
    this.client = client;
    showOnInventory();
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    activeItemId = plugin.getTravelItemIdForOverlay();
    String instruction = plugin.directTeleportInstruction();
    if (!instruction.isEmpty()) {
      Player player = client.getLocalPlayer();
      Point point = player == null ? null : player.getCanvasTextLocation(graphics, instruction, 40);
      if (point != null) {
        OverlayUtil.renderTextLocation(graphics, point, instruction, TRAVEL_ITEM_COLOR);
      }
    }
    return activeItemId > 0 ? super.render(graphics) : null;
  }

  @Override
  public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
    if (graphics == null || widgetItem == null || itemId != activeItemId) {
      return;
    }
    Rectangle bounds = widgetItem.getCanvasBounds();
    if (bounds == null) {
      return;
    }
    int quantity = widgetItem.getQuantity();
    if (cachedOutline == null || cachedItemId != itemId || cachedQuantity != quantity) {
      cachedItemId = itemId;
      cachedQuantity = quantity;
      cachedOutline = itemManager.getItemOutline(itemId, quantity, TRAVEL_ITEM_COLOR);
    }
    graphics.drawImage(cachedOutline, bounds.x, bounds.y, null);
  }
}
