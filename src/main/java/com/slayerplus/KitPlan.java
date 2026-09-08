package com.slayerplus;

import java.util.*;
import lombok.Getter;

@Getter
public final class KitPlan {
  private final String ownedStatus;
  private final String layoutTitle;
  private final List<KitItem> equipmentItems;
  private final List<KitItem> inventoryItems;
  private final List<KitItem> optionalItems;

  public KitPlan(String ownedStatus) {
    this(
        ownedStatus,
        "Recommended setup",
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList());
  }

  public KitPlan(
      String ownedStatus,
      String layoutTitle,
      List<KitItem> equipmentItems,
      List<KitItem> inventoryItems,
      List<KitItem> optionalItems) {
    this.ownedStatus = safe(ownedStatus, "No item scan available");
    this.layoutTitle = safe(layoutTitle, "Recommended setup");
    this.equipmentItems = immutableCopy(equipmentItems);
    this.inventoryItems = immutableCopy(inventoryItems);
    this.optionalItems = immutableCopy(optionalItems);
  }

  public List<KitItem> getBankWithdrawalItems() {
    List<KitItem> result = new ArrayList<>();
    Set<Integer> seenIds = new LinkedHashSet<>();
    addBankedUnique(result, seenIds, equipmentItems);
    addBankedUnique(result, seenIds, inventoryItems);
    return Collections.unmodifiableList(result);
  }

  KitPlan withRemainingFinishers(int remaining) {
    List<KitItem> updated = null;
    for (int i = 0; i < inventoryItems.size(); i++) {
      KitItem item = inventoryItems.get(i);
      String name = item.getDisplayName().toLowerCase(Locale.ROOT);
      int quantity =
          name.equals("fungicide")
              ? 1 + (Math.max(1, remaining) - 1) / 10
              : name.equals("bag of salt")
                      || name.equals("ice cooler")
                      || name.contains("fishing explosive")
                  ? Math.max(1, remaining)
                  : item.getQuantity();
      quantity = Math.min(item.getQuantity(), quantity);
      if (quantity != item.getQuantity()) {
        if (updated == null) {
          updated = new ArrayList<>(inventoryItems);
        }
        updated.set(i, item.withQuantity(quantity));
      }
    }
    return updated == null
        ? this
        : new KitPlan(ownedStatus, layoutTitle, equipmentItems, updated, optionalItems);
  }

  public boolean hasVisualLayout() {
    return !equipmentItems.isEmpty() || !inventoryItems.isEmpty();
  }

  public boolean hasConcreteItems() {
    return getConcreteItemCount() > 0;
  }

  public int getConcreteItemCount() {
    return countConcrete(equipmentItems)
        + countConcrete(inventoryItems)
        + countConcrete(optionalItems);
  }

  public int getUnresolvedItemCount() {
    return countUnresolved(equipmentItems)
        + countUnresolved(inventoryItems)
        + countUnresolved(optionalItems);
  }

  public static KitPlan hidden() {
    return new KitPlan(
        "Loadout recommendations disabled",
        "Loadout recommendations disabled",
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList());
  }

  public static KitPlan researchPending(String assignment) {
    String task =
        assignment == null || assignment.trim().isEmpty() ? "This task" : assignment.trim();
    return new KitPlan(
        "SlayerPlus will not create a broad or guessed loadout.",
        task + " — strategy review pending",
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList());
  }

  public static KitPlan empty() {
    return new KitPlan(
        "No active loadout",
        "Waiting for Slayer task",
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList());
  }

  private static void addBankedUnique(
      List<KitItem> destination, Set<Integer> seenIds, List<KitItem> source) {
    for (KitItem item : source) {
      if (item != null && item.isBanked() && item.hasItemId() && seenIds.add(item.getItemId())) {
        destination.add(item);
      }
    }
  }

  private static int countConcrete(List<KitItem> items) {
    int count = 0;
    for (KitItem item : items) {
      if (item != null && item.hasItemId()) {
        count++;
      }
    }
    return count;
  }

  private static int countUnresolved(List<KitItem> items) {
    int count = 0;
    for (KitItem item : items) {
      if (item != null
          && !item.hasItemId()
          && (item.getStatus() == KitItem.Status.MISSING
              || item.getStatus() == KitItem.Status.UNKNOWN)) {
        count++;
      }
    }
    return count;
  }

  private static List<KitItem> immutableCopy(List<KitItem> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    List<KitItem> copy = new ArrayList<>();
    for (KitItem item : source) {
      if (item != null) {
        copy.add(item);
      }
    }
    return Collections.unmodifiableList(copy);
  }

  private static String safe(String value, String fallback) {
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }
}
