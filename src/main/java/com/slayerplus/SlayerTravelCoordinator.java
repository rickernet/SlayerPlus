package com.slayerplus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class SlayerTravelCoordinator {
  private final Map<String, TravelChoice> resolvedByRoute = new LinkedHashMap<>();
  private long generation;
  private TravelChoice current = TravelChoice.unresolved("", 0L);

  public TravelChoice beginRoute(String routeIdentity) {
    generation++;
    String identity = routeIdentity == null ? "" : routeIdentity;
    TravelChoice cached = resolvedByRoute.get(identity);
    if (cached != null && !identity.isEmpty()) {
      current =
          cached.getStatus() == TravelChoice.Status.RESOLVED_ITEM
              ? TravelChoice.resolvedItem(
                  identity,
                  generation,
                  cached.getItemId(),
                  cached.getItemName(),
                  cached.getDestination(),
                  cached.getEquivalentItemFamilies())
              : TravelChoice.resolvedNonItem(
                  identity, generation, cached.getItemName(), cached.getDestination());
      return current;
    }
    current = TravelChoice.unresolved(identity, generation);
    return current;
  }

  public TravelChoice invalidate() {
    generation++;
    current = TravelChoice.unresolved("", generation);
    return current;
  }

  public TravelChoice clearAll() {
    resolvedByRoute.clear();
    return invalidate();
  }

  public void invalidateRoute(String routeIdentity) {
    String activeIdentity = current.getRouteIdentity();
    if (routeIdentity != null && !routeIdentity.isEmpty()) {
      resolvedByRoute.remove(routeIdentity);
    }
    if (activeIdentity != null && !activeIdentity.isEmpty()) {
      resolvedByRoute.remove(activeIdentity);
    }
    invalidate();
  }

  public TravelChoice seedProvisionalItem(
      String routeIdentity, int itemId, String itemName, String destination) {
    return seedProvisionalItem(
        routeIdentity,
        itemId,
        itemName,
        destination,
        java.util.Collections.singleton(itemName == null ? "" : itemName));
  }

  public TravelChoice seedProvisionalItem(
      String routeIdentity,
      int itemId,
      String itemName,
      String destination,
      Set<String> equivalentItemFamilies) {
    if (!ownsCurrentRoute(routeIdentity)
        || itemId <= 0
        || itemName == null
        || itemName.trim().isEmpty()
        || current.isResolved()) {
      return current;
    }
    current =
        TravelChoice.provisionalItem(
            current.getRouteIdentity(),
            current.getGeneration(),
            itemId,
            itemName,
            destination,
            equivalentItemFamilies);
    return current;
  }

  public TravelChoice resolveItem(
      String routeIdentity, int itemId, String itemName, String destination) {
    return resolveItem(
        routeIdentity,
        itemId,
        itemName,
        destination,
        java.util.Collections.singleton(itemName == null ? "" : itemName));
  }

  public TravelChoice resolveItem(
      String routeIdentity,
      int itemId,
      String itemName,
      String destination,
      Set<String> equivalentItemFamilies) {
    if (!ownsCurrentRoute(routeIdentity)
        || itemId <= 0
        || itemName == null
        || itemName.trim().isEmpty()) {
      return current;
    }
    current =
        TravelChoice.resolvedItem(
            current.getRouteIdentity(),
            current.getGeneration(),
            itemId,
            itemName,
            destination,
            equivalentItemFamilies);
    resolvedByRoute.put(current.getRouteIdentity(), current);
    return current;
  }

  public TravelChoice resolveNonItem(
      String routeIdentity, String family, String destination) {
    if (!ownsCurrentRoute(routeIdentity)) {
      return current;
    }
    current =
        TravelChoice.resolvedNonItem(
            current.getRouteIdentity(), current.getGeneration(), family, destination);
    resolvedByRoute.put(current.getRouteIdentity(), current);
    return current;
  }

  public TravelChoice replacePhysicalItemVariant(
      String routeIdentity, int itemId, String itemName) {
    if (routeIdentity == null
        || routeIdentity.isEmpty()
        || itemId <= 0
        || itemName == null
        || itemName.trim().isEmpty()) {
      return current;
    }
    boolean live = ownsCurrentRoute(routeIdentity);
    TravelChoice selected = live ? current : resolvedByRoute.get(routeIdentity);
    if (selected == null || !selected.hasPhysicalItem()) {
      return current;
    }
    TravelChoice replacement;
    if (selected.isProvisional()) {
      replacement =
          TravelChoice.provisionalItem(
              selected.getRouteIdentity(),
              selected.getGeneration(),
              itemId,
              itemName,
              selected.getDestination(),
              selected.getEquivalentItemFamilies());
    } else {
      replacement =
          TravelChoice.resolvedItem(
              selected.getRouteIdentity(),
              selected.getGeneration(),
              itemId,
              itemName,
              selected.getDestination(),
              selected.getEquivalentItemFamilies());
      resolvedByRoute.put(routeIdentity, replacement);
    }
    if (live) {
      current = replacement;
    }
    return live ? current : replacement;
  }

  public TravelChoice current() {
    return current;
  }

  public TravelChoice currentFor(String routeIdentity) {
    return ownsCurrentRoute(routeIdentity)
        ? current
        : TravelChoice.unresolved(routeIdentity == null ? "" : routeIdentity, generation);
  }

  public TravelChoice selectionFor(String routeIdentity) {
    if (ownsCurrentRoute(routeIdentity)) {
      return current;
    }
    TravelChoice cached =
        routeIdentity == null ? null : resolvedByRoute.get(routeIdentity);
    return cached == null
        ? TravelChoice.unresolved(routeIdentity == null ? "" : routeIdentity, generation)
        : cached;
  }

  public long getGeneration() {
    return generation;
  }

  public boolean ownsCurrentRoute(String routeIdentity) {
    return routeIdentity != null
        && !routeIdentity.isEmpty()
        && current.getRouteIdentity().equals(routeIdentity);
  }
}
