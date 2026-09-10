package com.slayerplus;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

final class ShortestPathBridge {
  private static final String NAMESPACE = "shortestpath";
  private static final int STAGED_ROUTE_DISTANCE = 256;
  private final EventBus eventBus;
  private WorldPoint lastTarget;
  private boolean lastBankDetour;
  private boolean lastTeleportsAllowed = true;
  private boolean lastPohJewelleryBox;
  private boolean lastStagedRoute;

  ShortestPathBridge(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  void routeTo(WorldPoint target, boolean allowBankDetour) {
    routeTo(target, allowBankDetour, true, false, false);
  }

  void routeTo(WorldPoint target, boolean allowBankDetour, boolean allowTeleports) {
    routeTo(target, allowBankDetour, allowTeleports, false, false);
  }

  void routeToTaskArea(
      WorldPoint target,
      boolean allowTeleports,
      boolean usePohJewelleryBox,
      boolean stagedRoute) {
    routeTo(target, false, allowTeleports, usePohJewelleryBox, stagedRoute);
  }

  private void routeTo(
      WorldPoint target,
      boolean allowBankDetour,
      boolean allowTeleports,
      boolean usePohJewelleryBox,
      boolean stagedRoute) {
    if (eventBus == null
        || target == null
        || (target.equals(lastTarget)
            && allowBankDetour == lastBankDetour
            && allowTeleports == lastTeleportsAllowed
            && usePohJewelleryBox == lastPohJewelleryBox
            && stagedRoute == lastStagedRoute)) {
      return;
    }
    lastTarget = target;
    lastBankDetour = allowBankDetour;
    lastTeleportsAllowed = allowTeleports;
    lastPohJewelleryBox = usePohJewelleryBox;
    lastStagedRoute = stagedRoute;
    Map<String, Object> config = new HashMap<>();
    config.put("includeBankPath", allowBankDetour);
    config.put("useTeleportationItems", allowTeleports ? "Inventory" : "None");
    config.put("useAgilityShortcuts", true);
    config.put("showTransportInfo", true);
    if (usePohJewelleryBox) {
      config.put("usePoh", true);
      config.put("pohJewelleryBoxTier", "Ornate");
    }
    if (stagedRoute) {
      config.put("unreachableTargetDistanceThreshold", STAGED_ROUTE_DISTANCE);
    }
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
    invalidate();
    if (eventBus != null) {
      try {
        eventBus.post(new PluginMessage(NAMESPACE, "clear"));
      } catch (RuntimeException ex) {
      }
    }
  }

  void invalidate() {
    lastTarget = null;
  }
}
