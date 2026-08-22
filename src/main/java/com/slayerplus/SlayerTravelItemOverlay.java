package com.slayerplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Draws an item-shaped marker over the one physical teleport selected for the
 * active route step. Backup travel-kit items remain unmarked.
 */
final class SlayerTravelItemOverlay extends WidgetItemOverlay
{
	private static final Color ROUTE_ITEM_COLOR = new Color(0, 255, 80);

	private final ItemManager itemManager;
	private final SlayerPlusPlugin plugin;

	@Inject
	private SlayerTravelItemOverlay(
		final ItemManager itemManager,
		final SlayerPlusPlugin plugin)
	{
		this.itemManager = itemManager;
		this.plugin = plugin;
		showOnInventory();
		showOnBank();
		showOnEquipment();
	}

	@Override
	public Dimension render(final Graphics2D graphics)
	{
		/* Avoid even walking visible bank/inventory widgets between travel legs. */
		return plugin.getSelectedTravelItemIdForOverlay() > 0
			? super.render(graphics)
			: null;
	}

	@Override
	public void renderItemOverlay(
		final Graphics2D graphics,
		final int itemId,
		final WidgetItem widgetItem)
	{
		if (graphics == null
			|| widgetItem == null
			|| itemId != plugin.getSelectedTravelItemIdForOverlay())
		{
			return;
		}

		final Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds == null)
		{
			return;
		}

		graphics.drawImage(
			itemManager.getItemOutline(
				itemId,
				widgetItem.getQuantity(),
				ROUTE_ITEM_COLOR
			),
			bounds.x,
			bounds.y,
			null
		);
	}
}
