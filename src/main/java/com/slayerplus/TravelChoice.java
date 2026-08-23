package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TravelChoice {
  public enum Status {
    UNRESOLVED,
    PROVISIONAL_ITEM,
    RESOLVED_ITEM,
    RESOLVED_NON_ITEM
  }

  public enum Source {
    NONE,
    AUTHORED_FALLBACK,
    SHORTEST_PATH
  }

  private final String routeIdentity;
  private final long generation;
  private final Status status;
  private final Source source;
  private final int itemId;
  private final String itemName;
  private final String destination;
  private final Set<String> equivalentItemFamilies;

  private TravelChoice(
      String routeIdentity,
      long generation,
      Status status,
      Source source,
      int itemId,
      String itemName,
      String destination,
      Set<String> equivalentItemFamilies) {
    this.routeIdentity = routeIdentity == null ? "" : routeIdentity;
    this.generation = generation;
    this.status = status == null ? Status.UNRESOLVED : status;
    this.source = source == null ? Source.NONE : source;
    this.itemId = itemId;
    this.itemName = itemName == null ? "" : itemName.trim();
    this.destination = destination == null ? "" : destination.trim();
    this.equivalentItemFamilies = immutableFamilies(equivalentItemFamilies);
  }

  public static TravelChoice unresolved(String routeIdentity, long generation) {
    return new TravelChoice(
        routeIdentity,
        generation,
        Status.UNRESOLVED,
        Source.NONE,
        -1,
        "",
        "",
        Collections.emptySet());
  }

  public static TravelChoice provisionalItem(
      String routeIdentity, long generation, int itemId, String itemName, String destination) {
    return provisionalItem(
        routeIdentity, generation, itemId, itemName, destination, singleFamily(itemName));
  }

  public static TravelChoice provisionalItem(
      String routeIdentity,
      long generation,
      int itemId,
      String itemName,
      String destination,
      Set<String> equivalentItemFamilies) {
    return new TravelChoice(
        routeIdentity,
        generation,
        Status.PROVISIONAL_ITEM,
        Source.AUTHORED_FALLBACK,
        itemId,
        itemName,
        destination,
        equivalentItemFamilies);
  }

  public static TravelChoice resolvedItem(
      String routeIdentity, long generation, int itemId, String itemName, String destination) {
    return resolvedItem(
        routeIdentity, generation, itemId, itemName, destination, singleFamily(itemName));
  }

  public static TravelChoice resolvedItem(
      String routeIdentity,
      long generation,
      int itemId,
      String itemName,
      String destination,
      Set<String> equivalentItemFamilies) {
    return new TravelChoice(
        routeIdentity,
        generation,
        Status.RESOLVED_ITEM,
        Source.SHORTEST_PATH,
        itemId,
        itemName,
        destination,
        equivalentItemFamilies);
  }

  public static TravelChoice resolvedNonItem(
      String routeIdentity, long generation, String family, String destination) {
    return new TravelChoice(
        routeIdentity,
        generation,
        Status.RESOLVED_NON_ITEM,
        Source.SHORTEST_PATH,
        -1,
        family,
        destination,
        Collections.emptySet());
  }

  public String getRouteIdentity() {
    return routeIdentity;
  }

  public long getGeneration() {
    return generation;
  }

  public Status getStatus() {
    return status;
  }

  public Source getSource() {
    return source;
  }

  public int getItemId() {
    return itemId;
  }

  public String getItemName() {
    return itemName;
  }

  public String getDestination() {
    return destination;
  }

  public Set<String> getEquivalentItemFamilies() {
    return equivalentItemFamilies;
  }

  public boolean belongsTo(String identity) {
    return identity != null && !identity.isEmpty() && routeIdentity.equals(identity);
  }

  public boolean hasPhysicalItem() {
    return itemId > 0 && (status == Status.PROVISIONAL_ITEM || status == Status.RESOLVED_ITEM);
  }

  public boolean isResolved() {
    return status == Status.RESOLVED_ITEM || status == Status.RESOLVED_NON_ITEM;
  }

  public boolean isProvisional() {
    return status == Status.PROVISIONAL_ITEM;
  }

  public String travelIdentity() {
    return generation
        + "|"
        + routeIdentity
        + "|"
        + status
        + "|"
        + itemId
        + "|"
        + itemName
        + "|"
        + destination
        + "|"
        + equivalentItemFamilies;
  }

  public boolean isStructurallyValid() {
    if (routeIdentity.isEmpty()) {
      return status == Status.UNRESOLVED && itemId <= 0;
    }
    if (status == Status.PROVISIONAL_ITEM || status == Status.RESOLVED_ITEM) {
      return itemId > 0 && !itemName.isEmpty() && !equivalentItemFamilies.isEmpty();
    }
    if (status == Status.RESOLVED_NON_ITEM) {
      return itemId <= 0;
    }
    return itemId <= 0;
  }

  private static Set<String> singleFamily(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> result = new LinkedHashSet<>();
    result.add(value.trim());
    return result;
  }

  private static Set<String> immutableFamilies(Set<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> copy = new LinkedHashSet<>();
    for (String value : source) {
      if (value != null && !value.trim().isEmpty()) {
        copy.add(value.trim());
      }
    }
    return copy.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(copy);
  }
}
