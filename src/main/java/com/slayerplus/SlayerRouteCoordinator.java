package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

public final class SlayerRouteCoordinator {
  public enum StageKind {
    SURFACE_APPROACH,
    EXACT_ENTRANCE,
    METHOD_POSITION,
    EXACT_NPC,
    LEARNED_CHECKPOINT,
    INTERIOR,
    ACCESS,
    WAIT_FOR_NPC
  }

  public static final class Stage {
    private final StageKind kind;
    private final WorldPoint destination;
    private final Set<WorldPoint> targets;

    private Stage(StageKind kind, WorldPoint destination, Set<WorldPoint> targets) {
      this.kind = kind;
      this.destination = destination;
      this.targets =
          targets == null || targets.isEmpty()
              ? Collections.emptySet()
              : Collections.unmodifiableSet(new LinkedHashSet<>(targets));
    }

    public StageKind getKind() {
      return kind;
    }

    public WorldPoint getDestination() {
      return destination;
    }

    public Set<WorldPoint> getTargets() {
      return targets;
    }

    public boolean isValid() {
      return kind != StageKind.WAIT_FOR_NPC && destination != null && !targets.isEmpty();
    }
  }

  private SlayerRouteCoordinator() {}

  public static Stage resolve(
      RouteCatalog.RouteProfile profile,
      WorldPoint playerLocation,
      WorldPoint exactEntrance,
      Set<WorldPoint> exactEntranceTargets,
      WorldPoint methodPosition,
      Set<WorldPoint> methodTargets,
      WorldPoint exactNpc,
      Set<WorldPoint> exactNpcTargets,
      WorldPoint learnedCheckpoint,
      Set<WorldPoint> learnedTargets) {
    if (profile == null) {
      return new Stage(StageKind.WAIT_FOR_NPC, null, Collections.emptySet());
    }
    if (profile.shouldRouteToSurfaceAccess(playerLocation)) {
      if (exactEntrance != null
          && exactEntranceTargets != null
          && !exactEntranceTargets.isEmpty()) {
        return new Stage(StageKind.EXACT_ENTRANCE, exactEntrance, exactEntranceTargets);
      }
      WorldPoint access = profile.getSurfaceAccess();
      return new Stage(
          StageKind.SURFACE_APPROACH,
          access,
          access == null ? Collections.emptySet() : profile.getRouteTargets(playerLocation));
    }
    if (methodPosition != null && methodTargets != null && !methodTargets.isEmpty()) {
      return new Stage(StageKind.METHOD_POSITION, methodPosition, methodTargets);
    }
    if (exactNpc != null && exactNpcTargets != null && !exactNpcTargets.isEmpty()) {
      return new Stage(StageKind.EXACT_NPC, exactNpc, exactNpcTargets);
    }
    if (learnedCheckpoint != null && learnedTargets != null && !learnedTargets.isEmpty()) {
      return new Stage(StageKind.LEARNED_CHECKPOINT, learnedCheckpoint, learnedTargets);
    }
    if (profile.requiresLoadedNpc()) {
      return new Stage(StageKind.WAIT_FOR_NPC, null, Collections.emptySet());
    }
    if (profile.isAccessThenNpc()) {
      WorldPoint access = profile.getRouteDestination(playerLocation);
      if (access == null || !sameCoordinateLayer(playerLocation, access)) {
        return new Stage(StageKind.WAIT_FOR_NPC, null, Collections.emptySet());
      }
      return new Stage(StageKind.ACCESS, access, profile.getRouteTargets(playerLocation));
    }
    WorldPoint destination = profile.getRouteDestination(playerLocation);
    return new Stage(StageKind.INTERIOR, destination, profile.getRouteTargets(playerLocation));
  }

  private static boolean sameCoordinateLayer(WorldPoint first, WorldPoint second) {
    if (first == null || second == null) {
      return true;
    }
    return first.getPlane() == second.getPlane() && (first.getY() >>> 12) == (second.getY() >>> 12);
  }
}
