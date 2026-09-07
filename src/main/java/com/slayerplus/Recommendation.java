package com.slayerplus;

import lombok.Getter;

@Getter
public final class Recommendation {
  private final String location;
  private final String method;
  private final String reason;
  private final String travel;
  private final String cannon;
  private final String requirements;
  private final String restriction;
  private final TaskStrategy strategy;

  public Recommendation(
      String location,
      String method,
      String reason,
      String travel,
      String cannon,
      String requirements,
      String restriction) {
    this(location, method, reason, travel, cannon, requirements, restriction, null);
  }

  public Recommendation(
      String location,
      String method,
      String reason,
      String travel,
      String cannon,
      String requirements,
      String restriction,
      TaskStrategy strategy) {
    this.location = safe(location, "No recommendation");
    this.method = safe(method, "No method available");
    this.reason = safe(reason, "No scoring explanation available");
    this.travel = safe(travel, "No route available");
    this.cannon = safe(cannon, "Unknown");
    this.requirements = safe(requirements, "None");
    this.restriction = safe(restriction, "None");
    this.strategy = strategy;
  }

  public boolean hasTaskStrategy() {
    return strategy != null;
  }

  private static String safe(String value, String fallback) {
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }
}
