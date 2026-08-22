package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Central route-stage precedence for SlayerPlus.
 *
 * <p>The plugin gathers live scene facts (exact entrance, exact NPC, reviewed
 * method position, learned checkpoint); this coordinator is the only place that
 * decides which fact is authoritative for the next Shortest Path leg.</p>
 */
public final class SlayerRouteCoordinator
{
	public enum StageKind
	{
		SURFACE_APPROACH,
		EXACT_ENTRANCE,
		METHOD_POSITION,
		EXACT_NPC,
		LEARNED_CHECKPOINT,
		INTERIOR,
		ACCESS,
		WAIT_FOR_NPC
	}

	public static final class Stage
	{
		private final StageKind kind;
		private final WorldPoint destination;
		private final Set<WorldPoint> targets;

		private Stage(
			final StageKind kind,
			final WorldPoint destination,
			final Set<WorldPoint> targets)
		{
			this.kind = kind;
			this.destination = destination;
			this.targets = targets == null || targets.isEmpty()
				? Collections.emptySet()
				: Collections.unmodifiableSet(new LinkedHashSet<>(targets));
		}

		public StageKind getKind() { return kind; }
		public WorldPoint getDestination() { return destination; }
		public Set<WorldPoint> getTargets() { return targets; }
		public boolean isValid()
		{
			return kind != StageKind.WAIT_FOR_NPC
				&& destination != null
				&& !targets.isEmpty();
		}
	}

	private SlayerRouteCoordinator()
	{
	}

	public static Stage resolve(
		final SlayerRouteCatalog.RouteProfile profile,
		final WorldPoint playerLocation,
		final WorldPoint exactEntrance,
		final Set<WorldPoint> exactEntranceTargets,
		final WorldPoint methodPosition,
		final Set<WorldPoint> methodTargets,
		final WorldPoint exactNpc,
		final Set<WorldPoint> exactNpcTargets,
		final WorldPoint learnedCheckpoint,
		final Set<WorldPoint> learnedTargets)
	{
		if (profile == null)
		{
			return new Stage(StageKind.WAIT_FOR_NPC, null, Collections.emptySet());
		}

		/* A staged route can never expose its interior target before the outer
		 * transition. Intermediate dungeon floors are already past that boundary
		 * and must continue inward rather than being routed back outside. */
		if (profile.shouldRouteToSurfaceAccess(playerLocation))
		{
			if (exactEntrance != null && exactEntranceTargets != null
				&& !exactEntranceTargets.isEmpty())
			{
				return new Stage(
					StageKind.EXACT_ENTRANCE,
					exactEntrance,
					exactEntranceTargets
				);
			}
			final WorldPoint access = profile.getSurfaceAccess();
			return new Stage(
				StageKind.SURFACE_APPROACH,
				access,
				access == null
					? Collections.emptySet()
					: profile.getRouteTargets(playerLocation)
			);
		}

		/* Exact researched setup positions outrank NPC chasing once inside. */
		if (methodPosition != null && methodTargets != null && !methodTargets.isEmpty())
		{
			return new Stage(StageKind.METHOD_POSITION, methodPosition, methodTargets);
		}

		if (exactNpc != null && exactNpcTargets != null && !exactNpcTargets.isEmpty())
		{
			return new Stage(StageKind.EXACT_NPC, exactNpc, exactNpcTargets);
		}

		if (learnedCheckpoint != null && learnedTargets != null && !learnedTargets.isEmpty())
		{
			return new Stage(
				StageKind.LEARNED_CHECKPOINT,
				learnedCheckpoint,
				learnedTargets
			);
		}

		if (profile.requiresLoadedNpc())
		{
			return new Stage(StageKind.WAIT_FOR_NPC, null, Collections.emptySet());
		}

		if (profile.isAccessThenNpc())
		{
			final WorldPoint access = profile.getRouteDestination(playerLocation);
			if (access == null || !sameCoordinateLayer(playerLocation, access))
			{
				return new Stage(StageKind.WAIT_FOR_NPC, null, Collections.emptySet());
			}
			return new Stage(StageKind.ACCESS, access, profile.getRouteTargets(playerLocation));
		}

		final WorldPoint destination = profile.getRouteDestination(playerLocation);
		return new Stage(
			StageKind.INTERIOR,
			destination,
			profile.getRouteTargets(playerLocation)
		);
	}

	private static boolean sameCoordinateLayer(
		final WorldPoint first,
		final WorldPoint second)
	{
		if (first == null || second == null)
		{
			return true;
		}
		return first.getPlane() == second.getPlane()
			&& (first.getY() >>> 12) == (second.getY() >>> 12);
	}
}
