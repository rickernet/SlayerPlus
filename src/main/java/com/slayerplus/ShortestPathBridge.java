package com.slayerplus;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

final class ShortestPathBridge {
  private static final String NAMESPACE = "shortestpath";
  private final EventBus eventBus;
  private WorldPoint lastTarget;
  private boolean lastBankDetour;
  private boolean lastTeleportsAllowed = true;

  ShortestPathBridge(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  void routeTo(WorldPoint target, boolean allowBankDetour) {
    routeTo(target, allowBankDetour, true);
  }

  void routeTo(WorldPoint target, boolean allowBankDetour, boolean allowTeleports) {
    if (eventBus == null
        || target == null
        || (target.equals(lastTarget)
            && allowBankDetour == lastBankDetour
            && allowTeleports == lastTeleportsAllowed)) {
      return;
    }
    lastTarget = target;
    lastBankDetour = allowBankDetour;
    lastTeleportsAllowed = allowTeleports;
    Map<String, Object> config = new HashMap<>();
    config.put("includeBankPath", allowBankDetour);
    config.put("useTeleportationItems", allowTeleports ? "Inventory" : "None");
    config.put("useAgilityShortcuts", true);
    config.put("showTransportInfo", true);
    Map<String, Object> data = new HashMap<>();
    data.put("target", target);
    data.put("config", config);
    try {
      eventBus.post(new PluginMessage(NAMESPACE, "path", data));
    } catch (RuntimeException ex) {
      lastTarget = null;
    }
  }

  void clear() {
    if (lastTarget == null) {
      return;
    }
    lastTarget = null;
    if (eventBus != null) {
      try {
        eventBus.post(new PluginMessage(NAMESPACE, "clear"));
      } catch (RuntimeException ex) {
      }
    }
  }
}
