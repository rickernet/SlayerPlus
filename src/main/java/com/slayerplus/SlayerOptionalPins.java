package com.slayerplus;

import java.util.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.util.Text;

final class SlayerOptionalPins {
  static final String CONFIG_KEY = "optionalItemPinsV1";
  static final int LIMIT = 8;

  private SlayerOptionalPins() {}

  static boolean allowsMenu(String activeTag, int componentId, int itemId, boolean shiftHeld) {
    return componentId == InterfaceID.Bankmain.ITEMS
        && itemId > 0
        && (shiftHeld
            || activeTag != null
                && Text.standardize(BankTagLayout.TAG_NAME).equals(Text.standardize(activeTag)));
  }

  static boolean isMenuTrigger(String option, boolean bankContainsItem) {
    // Match Bank Tags: potion-storage entries have no Examine action.
    return "Examine".equals(option) || "Withdraw-All-but-1".equals(option) && !bankContainsItem;
  }

  static Set<Integer> read(String saved) {
    Set<Integer> pins = new LinkedHashSet<>();
    for (int id : SlayerPlusPlugin.parseItemIds(saved)) {
      if (pins.size() == LIMIT) break;
      pins.add(id);
    }
    return pins;
  }

  static KitPlan prepend(KitPlan plan, List<KitItem> pins) {
    if (plan == null || pins.isEmpty()) return plan;
    Map<Integer, KitItem> optional = new LinkedHashMap<>();
    for (KitItem item : pins) {
      if (optional.size() == LIMIT) break;
      if (item.hasItemId()) optional.putIfAbsent(item.getItemId(), item);
    }
    // BankTagLayout applies its existing equipment/swap filters to unpinned items.
    for (KitItem item : plan.getOptionalItems()) {
      if (item.hasItemId()) optional.putIfAbsent(item.getItemId(), item);
    }
    return new KitPlan(
        plan.getEquipment(),
        plan.getInventory(),
        plan.getOwnedStatus(),
        plan.getLayoutTitle(),
        plan.getEquipmentItems(),
        plan.getInventoryItems(),
        new ArrayList<>(optional.values()));
  }
}
