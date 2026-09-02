package com.slayerplus;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerRefreshPerformanceTest
{
	@Test
	public void combatProgressDoesNotRebuildButTaskAndInputChangesDo()
	{
		assertFalse(SlayerPlusPlugin.shouldRebuildLoadout(false, true, "task", "task", 100, 99));
		assertFalse(SlayerPlusPlugin.shouldRebuildLoadout(false, true, "task", "task", 99, 99));
		assertTrue(SlayerPlusPlugin.shouldRebuildLoadout(true, true, "task", "task", 100, 99));
		assertTrue(SlayerPlusPlugin.shouldRebuildLoadout(false, false, "task", "task", 100, 99));
		assertTrue(SlayerPlusPlugin.shouldRebuildLoadout(false, true, "old", "new", 100, 99));
		assertTrue(SlayerPlusPlugin.shouldRebuildLoadout(false, true, "task", "task", 1, 150));
	}

	@Test
	public void quantityEventsAreLightButPouchTypeAndTaskChangesAreNot()
	{
		assertTrue(SlayerPlusPlugin.isProgressOnlyVarChange(VarPlayerID.SLAYER_COUNT, -1));
		for (int id : new int[]{VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
			VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4,
			VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6})
		{
			assertTrue(SlayerPlusPlugin.isProgressOnlyVarChange(-1, id));
		}
		assertFalse(SlayerPlusPlugin.isProgressOnlyVarChange(-1, VarbitID.RUNE_POUCH_TYPE_1));
		assertFalse(SlayerPlusPlugin.isProgressOnlyVarChange(VarPlayerID.SLAYER_TARGET, -1));
	}

	@Test
	public void coalescingNeverLosesAFullRefreshRequest()
	{
		SlayerPlusPlugin plugin = new SlayerPlusPlugin();
		plugin.scheduleProgressRefresh();
		assertFalse(plugin.loadoutRefreshPending);
		plugin.scheduleRefresh(false);
		plugin.scheduleProgressRefresh();
		assertTrue(plugin.loadoutRefreshPending);
		plugin = new SlayerPlusPlugin();
		plugin.scheduleRefresh(true);
		plugin.scheduleProgressRefresh();
		assertTrue(plugin.loadoutRefreshPending);
	}

	@Test
	public void finisherQuantitiesUpdateWithoutRerankingEquipmentOrTravel()
	{
		KitItem travel = new KitItem("Slayer ring", ItemID.SLAYER_BAG_OF_SALT, 1, KitItem.Status.INVENTORY)
			.withInventoryGroup(MethodRules.InventoryGroup.TRAVEL);
		KitItem salt = new KitItem("Bag of salt", ItemID.SLAYER_BAG_OF_SALT, 25, KitItem.Status.BANK)
			.withInventoryGroup(MethodRules.InventoryGroup.UTILITY);
		KitItem fungicide = new KitItem("Fungicide", -1, 3, KitItem.Status.MISSING)
			.withInventoryGroup(MethodRules.InventoryGroup.UTILITY);
		KitPlan plan = new KitPlan("equipment", "inventory", "owned", "task",
			Collections.emptyList(), Arrays.asList(travel, salt, fungicide), Collections.emptyList());
		assertSame(plan, plan.withRemainingFinishers(25));
		KitPlan updated = plan.withRemainingFinishers(20);
		assertSame(travel, updated.getInventoryItems().get(0));
		assertEquals(20, updated.getInventoryItems().get(1).getQuantity());
		assertEquals(2, updated.getInventoryItems().get(2).getQuantity());
		assertEquals(KitItem.Status.MISSING, updated.getInventoryItems().get(2).getStatus());
		assertEquals(MethodRules.InventoryGroup.UTILITY, updated.getInventoryItems().get(1).getInventoryGroup());
	}

	@Test
	public void repeatedSidebarUpdatesDoNotRebuildDropdownModels() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			SlayerPlusPanel panel = new SlayerPlusPanel();
			try
			{
				panel.configureOwnedSlayerHelmets(Collections.singletonList("Slayer helmet"));
				panel.configureTaskVariants("Gargoyles", TaskVariant.STANDARD_TASK);
				JComboBox<?> helmets = comboContaining(panel, "Slayer helmet");
				JComboBox<?> variants = comboContaining(panel,
					VariantCatalog.getOptionLabel("Gargoyles", TaskVariant.STANDARD_TASK));
				assertNotNull(helmets);
				assertNotNull(variants);
				AtomicInteger events = new AtomicInteger();
				ListDataListener listener = new ListDataListener() {
					public void intervalAdded(ListDataEvent event) { events.incrementAndGet(); }
					public void intervalRemoved(ListDataEvent event) { events.incrementAndGet(); }
					public void contentsChanged(ListDataEvent event) { events.incrementAndGet(); }
				};
				helmets.getModel().addListDataListener(listener);
				variants.getModel().addListDataListener(listener);
				for (int i = 0; i < 50; i++)
				{
					panel.configureOwnedSlayerHelmets(Collections.singletonList("Slayer helmet"));
					panel.configureTaskVariants("Gargoyles", TaskVariant.STANDARD_TASK);
				}
				assertEquals(0, events.get());
				panel.configureOwnedSlayerHelmets(Arrays.asList("Slayer helmet", "Slayer helmet (i)"));
				assertTrue(events.get() > 0);
				assertEquals(4, helmets.getItemCount());
				panel.configureTaskVariants("Rockslugs", TaskVariant.STANDARD_TASK);
				assertEquals(VariantCatalog.getAvailableVariants("Rockslugs").size(), variants.getItemCount());
			}
			finally { panel.disposeResources(); }
		});
	}

	private static JComboBox<?> comboContaining(Component component, String item)
	{
		if (component instanceof JComboBox)
		{
			JComboBox<?> combo = (JComboBox<?>) component;
			for (int i = 0; i < combo.getItemCount(); i++)
			{
				if (item.equals(combo.getItemAt(i))) { return combo; }
			}
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				JComboBox<?> found = comboContaining(child, item);
				if (found != null) { return found; }
			}
		}
		return null;
	}
}
