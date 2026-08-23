package com.slayerplus;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SlayerRoutePlanCatalog {
  private static final Map<RouteKey, RoutePlan> PLANS = SlayerRoutePlanResource.load();
  private static final Set<RouteKey> KEYS =
      Collections.unmodifiableSet(new LinkedHashSet<>(PLANS.keySet()));
  private static final Collection<RoutePlan> UNIQUE_PLANS =
      Collections.unmodifiableSet(new LinkedHashSet<>(PLANS.values()));

  private SlayerRoutePlanCatalog() {}

  public static RoutePlan resolve(String taskName, String location, boolean boss) {
    return PLANS.get(RouteKey.of(taskName, location, boss));
  }

  public static boolean hasExactPlan(String taskName, String location, boolean boss) {
    return resolve(taskName, location, boss) != null;
  }

  public static boolean hasExplicitPlanForRegression(
      String taskName, String location, boolean boss) {
    return hasExactPlan(taskName, location, boss);
  }

  public static Set<RouteKey> keysForRegression() {
    return KEYS;
  }

  public static Collection<RoutePlan> plansForRegression() {
    return UNIQUE_PLANS;
  }

  public static Collection<RoutePlan> allPlans() {
    return UNIQUE_PLANS;
  }

  public static final class RouteKey {
    private final String taskName;
    private final String location;
    private final boolean boss;

    private RouteKey(String taskName, String location, boolean boss) {
      this.taskName = RouteEvidence.normalize(taskName);
      this.location = RouteEvidence.normalize(location);
      this.boss = boss;
    }

    public static RouteKey of(String taskName, String location, boolean boss) {
      return new RouteKey(taskName, location, boss);
    }

    public String getTaskName() {
      return taskName;
    }

    public String getLocation() {
      return location;
    }

    public boolean isBoss() {
      return boss;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof RouteKey)) {
        return false;
      }
      RouteKey that = (RouteKey) other;
      return boss == that.boss && taskName.equals(that.taskName) && location.equals(that.location);
    }

    @Override
    public int hashCode() {
      return Objects.hash(taskName, location, boss);
    }

    @Override
    public String toString() {
      return taskName + "|" + location + "|" + boss;
    }
  }
}
