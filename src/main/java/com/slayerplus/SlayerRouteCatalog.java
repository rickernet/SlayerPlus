package com.slayerplus;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Exact route profiles for reviewed Slayer encounters.
 *
 * <p>The profile key is the selected encounter and the selected location, not
 * merely the assignment name. This prevents a boss selection from silently
 * falling back to the ordinary monster route. Profiles with a verified
 * interior tile route directly to the monster room. Entrance profiles route to
 * a known access point and then continue only after an exact allowed NPC name
 * is loaded.</p>
 */
public final class SlayerRouteCatalog
{
	public static final int CATALOG_REVISION = 15;

	public enum RouteMode
	{
		OPEN_WORLD,
		VERIFIED_INTERIOR,
		TRANSPORT_CHAIN_INTERIOR,
		STAGED_INTERIOR,
		ACCESS_THEN_NPC,
		NPC_ONLY,
		BOSS_OPEN_WORLD,
		BOSS_VERIFIED_INTERIOR,
		BOSS_TRANSPORT_CHAIN_INTERIOR,
		BOSS_STAGED_INTERIOR,
		BOSS_ACCESS_THEN_NPC,
		BOSS_NPC_ONLY
	}

	/** Explicit interaction contract for a staged dungeon transition. */
	public static final class TransitionSpec
	{
		private final WorldPoint approachPoint;
		private final Set<String> objectNameHints;
		private final Set<String> actions;
		private final int searchRadius;

		private TransitionSpec(
			final WorldPoint approachPoint,
			final Set<String> objectNameHints,
			final Set<String> actions,
			final int searchRadius)
		{
			this.approachPoint = approachPoint;
			this.objectNameHints = Collections.unmodifiableSet(
				new LinkedHashSet<>(objectNameHints)
			);
			this.actions = Collections.unmodifiableSet(
				new LinkedHashSet<>(actions)
			);
			this.searchRadius = Math.max(1, searchRadius);
		}

		public WorldPoint getApproachPoint() { return approachPoint; }
		public int getSearchRadius() { return searchRadius; }
		public boolean hasObjectNameHints() { return !objectNameHints.isEmpty(); }
		public Set<String> getObjectNameHints() { return objectNameHints; }
		public Set<String> getActions() { return actions; }

		public boolean matchesObjectName(final String objectName)
		{
			if (objectNameHints.isEmpty())
			{
				return true;
			}
			final String value = normalize(objectName);
			for (final String hint : objectNameHints)
			{
				if (!hint.isEmpty() && value.contains(hint))
				{
					return true;
				}
			}
			return false;
		}

		public boolean matchesAction(final String action)
		{
			return actions.contains(normalize(action));
		}
	}

	/** Explicit interior bounds used to prove that a staged transition occurred. */
	public static final class EncounterArea
	{
		private final WorldPoint center;
		private final int xRadius;
		private final int yRadius;

		private EncounterArea(
			final WorldPoint center,
			final int xRadius,
			final int yRadius)
		{
			this.center = center;
			this.xRadius = Math.max(1, xRadius);
			this.yRadius = Math.max(1, yRadius);
		}

		public boolean contains(final WorldPoint point)
		{
			return point != null && center != null
				&& point.getPlane() == center.getPlane()
				&& sameCoordinateLayer(point, center)
				&& Math.abs(point.getX() - center.getX()) <= xRadius
				&& Math.abs(point.getY() - center.getY()) <= yRadius;
		}
	}

	public static final class RouteProfile
	{
		private static final int ENCOUNTER_REGION_RADIUS = 192;

		private final String taskName;
		private final String location;
		private final WorldPoint primaryDestination;
		private final WorldPoint surfaceAccess;
		private final TransitionSpec transitionSpec;
		private final EncounterArea encounterArea;
		private final String transitionInstruction;
		private final String positioningNote;
		private final RouteMode mode;
		private final Set<String> npcNames;
		private final int targetRadius;
		private final int arrivalRadius;

		private RouteProfile(
			final String taskName,
			final String location,
			final WorldPoint primaryDestination,
			final RouteMode mode,
			final Set<String> npcNames,
			final int targetRadius,
			final int arrivalRadius)
		{
			this(
				taskName,
				location,
				primaryDestination,
				null,
				"",
				"",
				mode,
				npcNames,
				targetRadius,
				arrivalRadius
			);
		}

		private RouteProfile(
			final String taskName,
			final String location,
			final WorldPoint primaryDestination,
			final WorldPoint surfaceAccess,
			final String transitionInstruction,
			final String positioningNote,
			final RouteMode mode,
			final Set<String> npcNames,
			final int targetRadius,
			final int arrivalRadius)
		{
			this.taskName = taskName == null ? "" : taskName.trim();
			this.location = location == null ? "" : location.trim();
			this.primaryDestination = primaryDestination;
			this.surfaceAccess = surfaceAccess;
			this.transitionSpec = surfaceAccess == null
				? null
				: transitionSpecFor(this.location, surfaceAccess);
			this.encounterArea = primaryDestination == null
				? null
				: new EncounterArea(
					primaryDestination,
					ENCOUNTER_REGION_RADIUS,
					ENCOUNTER_REGION_RADIUS
				);
			this.transitionInstruction = transitionInstruction == null
				? ""
				: transitionInstruction.trim();
			this.positioningNote = positioningNote == null
				? ""
				: positioningNote.trim();
			this.mode = mode;
			this.npcNames = Collections.unmodifiableSet(
				new LinkedHashSet<>(npcNames)
			);
			this.targetRadius = Math.max(0, targetRadius);
			this.arrivalRadius = Math.max(1, arrivalRadius);

			if (isStaged())
			{
				validateStagedSurfaceAccess();
			}
		}

		private void validateStagedSurfaceAccess()
		{
			if (surfaceAccess == null)
			{
				throw new IllegalStateException(
					"Staged Slayer route is missing a surface entrance: "
						+ taskName + " @ " + location
				);
			}

			/*
			 * OSRS dungeon coordinate layers are commonly shifted far above ordinary
			 * world-map layers. A staged access point may be a surface entrance or a
			 * verified transport landing, but it must never accidentally reuse the
			 * deep interior target itself as the first destination.
			 */
			if (surfaceAccess.getY() >= 6400)
			{
				throw new IllegalStateException(
					"Staged Slayer route surface entrance is not on the overworld: "
						+ taskName + " @ " + location + " -> " + surfaceAccess
				);
			}

			if (primaryDestination != null
				&& surfaceAccess.equals(primaryDestination))
			{
				throw new IllegalStateException(
					"Staged Slayer route surface entrance equals its interior target: "
						+ taskName + " @ " + location
				);
			}
		}

		public String getTaskName() { return taskName; }
		public String getLocation() { return location; }
		public WorldPoint getPrimaryDestination() { return primaryDestination; }
		public WorldPoint getSurfaceAccess() { return surfaceAccess; }
		public TransitionSpec getTransitionSpec() { return transitionSpec; }
		public EncounterArea getEncounterArea() { return encounterArea; }
		public String getTransitionInstruction() { return transitionInstruction; }
		public String getPositioningNote() { return positioningNote; }
		public RouteMode getMode() { return mode; }

		public boolean isVerifiedInterior()
		{
			return mode == RouteMode.VERIFIED_INTERIOR
				|| mode == RouteMode.TRANSPORT_CHAIN_INTERIOR
				|| mode == RouteMode.STAGED_INTERIOR
				|| mode == RouteMode.BOSS_VERIFIED_INTERIOR
				|| mode == RouteMode.BOSS_TRANSPORT_CHAIN_INTERIOR
				|| mode == RouteMode.BOSS_STAGED_INTERIOR;
		}

		public boolean isStaged()
		{
			return mode == RouteMode.STAGED_INTERIOR
				|| mode == RouteMode.BOSS_STAGED_INTERIOR;
		}

		public boolean isAccessThenNpc()
		{
			return mode == RouteMode.ACCESS_THEN_NPC
				|| mode == RouteMode.BOSS_ACCESS_THEN_NPC;
		}

		public boolean requiresLoadedNpc()
		{
			return mode == RouteMode.NPC_ONLY
				|| mode == RouteMode.BOSS_NPC_ONLY;
		}

		public boolean hasStaticDestination()
		{
			return primaryDestination != null;
		}

		public boolean isBoss()
		{
			return mode == RouteMode.BOSS_OPEN_WORLD
				|| mode == RouteMode.BOSS_ACCESS_THEN_NPC
				|| mode == RouteMode.BOSS_VERIFIED_INTERIOR
				|| mode == RouteMode.BOSS_TRANSPORT_CHAIN_INTERIOR
				|| mode == RouteMode.BOSS_STAGED_INTERIOR
				|| mode == RouteMode.BOSS_NPC_ONLY;
		}

		public boolean isInsideEncounterArea(final WorldPoint playerLocation)
		{
			if (playerLocation == null || primaryDestination == null)
			{
				return false;
			}
			/*
			 * "Not on the surface" is not enough to prove the player is in this
			 * encounter; they may be inside an entirely different dungeon. Require
			 * the reviewed interior coordinate layer and encounter region instead.
			 */
			return encounterArea != null && encounterArea.contains(playerLocation);
		}

		/**
		 * Whether a staged route is still on the side of the reviewed outer
		 * transition.  This is deliberately different from
		 * {@link #isInsideEncounterArea(WorldPoint)}: a player can have crossed the
		 * surface entrance without having reached the final monster room yet.
		 * Treating every intermediate dungeon floor as "outside" made the next
		 * Shortest Path request point back out of the cave.
		 */
		public boolean shouldRouteToSurfaceAccess(
			final WorldPoint playerLocation)
		{
			if (!isStaged() || surfaceAccess == null)
			{
				return false;
			}
			if (playerLocation == null)
			{
				return true;
			}

			/*
			 * Most OSRS caves move to another coordinate layer (commonly a
			 * y-coordinate shifted by 6400).  Once that layer changes, the outer
			 * entrance has been crossed even if the player is on an intermediate
			 * floor.  Same-layer transports such as boats instead use the reviewed
			 * destination area as their completion predicate.
			 */
			if (!sameCoordinateLayer(surfaceAccess, primaryDestination))
			{
				return sameCoordinateLayer(playerLocation, surfaceAccess);
			}
			return !isInsideEncounterArea(playerLocation);
		}

		public boolean isPlausibleLearnedCheckpoint(
			final WorldPoint checkpoint)
		{
			if (checkpoint == null
				|| checkpoint.getPlane() < 0
				|| checkpoint.getPlane() > 3
				|| checkpoint.getX() <= 0
				|| checkpoint.getY() <= 0)
			{
				return false;
			}

			/*
			 * Staged routes have a reviewed underground encounter anchor. A learned
			 * NPC-adjacent tile must remain in that same dungeon coordinate layer;
			 * otherwise a stale or corrupted point can be drawn on the surface map.
			 */
			if (isStaged() && primaryDestination != null)
			{
				return encounterArea != null && encounterArea.contains(checkpoint);
			}
			return true;
		}

		public WorldPoint getRouteDestination(final WorldPoint playerLocation)
		{
			return shouldRouteToSurfaceAccess(playerLocation)
					? surfaceAccess
					: primaryDestination;
		}

		public int getArrivalRadius() { return arrivalRadius; }

		public int getArrivalRadius(final WorldPoint playerLocation)
		{
			return shouldRouteToSurfaceAccess(playerLocation)
					? ACCESS_ARRIVAL_RADIUS
					: arrivalRadius;
		}

		public Set<WorldPoint> getRouteTargets()
		{
			return targetArea(primaryDestination, targetRadius);
		}

		public Set<WorldPoint> getRouteTargets(final WorldPoint playerLocation)
		{
			final WorldPoint destination = getRouteDestination(playerLocation);
			if (destination != null
				&& destination.equals(surfaceAccess)
				&& isStaged()
				&& !usesReachableSurfaceApproachArea())
			{
				return Collections.singleton(destination);
			}

			final int radius = destination != null
				&& destination.equals(surfaceAccess)
					? ACCESS_TARGET_RADIUS
					: targetRadius;
			return targetArea(destination, radius);
		}

		private boolean usesReachableSurfaceApproachArea()
		{
			/*
			 * Shortest Path transport 30842 models the Fossil Island task-cave
			 * trapdoor itself. Give the pathfinder its adjacent walkable tiles as
			 * valid arrivals too, while the transition detector identifies the real
			 * Trap Door interaction locally.
			 */
			return normalize(location).equals("wyvern cave on fossil island");
		}

		public boolean matchesNpc(final String npcName)
		{
			final String normalized = normalizeNpcName(npcName);
			return !normalized.isEmpty() && npcNames.contains(normalized);
		}

		public Set<String> getNpcNames() { return npcNames; }
	}

	private static final int INTERIOR_TARGET_RADIUS = 2;
	private static final int ACCESS_TARGET_RADIUS = 1;
	private static final int INTERIOR_ARRIVAL_RADIUS = 14;
	private static final int ACCESS_ARRIVAL_RADIUS = 2;

	/*
	 * Inferno is a special full-run boss route: preparation finishes at the east
	 * Mor Ul Rek bank, then a reviewed entrance approach makes the exact entrance
	 * NPC load. The live NPC supplies the final collision-checked endpoint. We do
	 * not route to TzKal-Zuk himself because the boss does not exist until wave 69.
	 */
	private static final Set<String> INFERNO_ENTRY_NPC_NAMES =
		Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
			"tzhaar ket keh"
		)));
	/*
	 * Current OSRS Wiki NPC-map coordinate for TzHaar-Ket-Keh. This is an
	 * approach waypoint only: once the NPC is loaded, the runtime replaces it
	 * with a collision-checked walkable perimeter tile.
	 */
	private static final WorldPoint INFERNO_ENTRY_APPROACH =
		new WorldPoint(2496, 5115, 0);
	/*
	 * Reviewed alternate-realm coordinate, not a projected dungeon layer. The
	 * Maggot King plan routes here after Castle Drakan teleport before the manual
	 * portal/scout/shortcut chain takes ownership.
	 */
	private static final WorldPoint MAGGOT_KING_CASTLE_PORTAL_ACCESS =
		new WorldPoint(3161, 7711, 0);

	private static final Map<String, WorldPoint> ACCESS_ROUTES =
		createAccessRoutes();
	private static final Map<String, RouteProfile> STANDARD_PROFILES =
		createStandardProfiles();
	private static final Map<String, RouteProfile> BOSS_PROFILES =
		createBossProfiles();
	/*
	 * Exact reviewed cannon stand tiles. These are deliberately separate from
	 * the broad monster-room target so guided routing can finish at the actual
	 * gameplay position when the selected strategy uses a cannon.
	 */
	private static final Map<String, WorldPoint> CANNON_POSITIONS =
		createCannonPositions();
	/* Reviewed stand/stack tiles for methods whose destination is more specific
	 * than the ordinary monster room. */
	private static final Map<String, WorldPoint> BARRAGE_POSITIONS =
		createBarragePositions();

	static
	{
		validateCatalog();
	}

	private static void validateCatalog()
	{
		validateAccessRoutes();
		validateProfileMap(STANDARD_PROFILES, false);
		validateProfileMap(BOSS_PROFILES, true);
		validateCannonPositions();
		validateBarragePositions();
		validateInfernoPreparationContract();
	}

	private static void validateInfernoPreparationContract()
	{
		if (!requiresPreparationBank("TzKal-Zuk", "Inferno", true)
			|| requiresPreparationBank("TzKal-Zuk", "Inferno", false)
			|| !getPreparationEntryNpcNames("TzKal-Zuk", "Inferno", true)
				.contains("tzhaar ket keh")
			|| !INFERNO_ENTRY_APPROACH.equals(
				getPreparationEntryApproachTarget(
					"TzKal-Zuk", "Inferno", true
				)
			))
		{
			throw new IllegalStateException(
				"Route regression: TzKal-Zuk must stage at the Inferno preparation bank and finish at TzHaar-Ket-Keh"
			);
		}
	}

	private static void validateAccessRoutes()
	{
		for (final Map.Entry<String, WorldPoint> entry : ACCESS_ROUTES.entrySet())
		{
			final WorldPoint point = entry.getValue();
			if (point == null || point.getX() <= 0 || point.getY() <= 0
				|| point.getPlane() < 0 || point.getPlane() > 3)
			{
				throw new IllegalStateException(
					"Invalid reviewed Slayer access coordinate: " + entry.getKey()
				);
			}
			if (prefersWorldMapEntranceIcon(entry.getKey()) && point.getY() >= 6400
				&& !isReviewedAlternateRealmAccess(entry.getKey(), point))
			{
				throw new IllegalStateException(
					"Dungeon/cave access must be authored as a real surface coordinate; "
						+ "automatic 6400-layer projection is forbidden: "
						+ entry.getKey() + " -> " + point
				);
			}
		}
	}

	private static boolean isReviewedAlternateRealmAccess(
		final String normalizedLocation,
		final WorldPoint point)
	{
		return normalize("Maggot King's lair").equals(normalizedLocation)
			&& MAGGOT_KING_CASTLE_PORTAL_ACCESS.equals(point);
	}

	private static void validateProfileMap(
		final Map<String, RouteProfile> profiles,
		final boolean bossMap)
	{
		for (final Map.Entry<String, RouteProfile> entry : profiles.entrySet())
		{
			final RouteProfile profile = entry.getValue();
			if (profile == null)
			{
				throw new IllegalStateException("Null Slayer route profile");
			}
			if (profile.taskName.isEmpty() || profile.location.isEmpty())
			{
				throw new IllegalStateException("Slayer route profile has blank identity");
			}
			if (profile.mode == null)
			{
				throw new IllegalStateException(
					"Slayer route profile has no route mode: "
						+ profile.taskName + " @ " + profile.location
				);
			}

			final String expectedKey = profileKey(
				normalizeTaskKey(profile.taskName),
				normalize(profile.location)
			);
			if (!expectedKey.equals(entry.getKey()))
			{
				throw new IllegalStateException(
					"Slayer route profile key drift: " + entry.getKey()
						+ " != " + expectedKey
				);
			}

			if (profile.isBoss() != bossMap)
			{
				throw new IllegalStateException(
					"Boss/regular route identity leaked across catalog maps: "
						+ profile.taskName + " @ " + profile.location
				);
			}

			if (profile.npcNames.isEmpty())
			{
				throw new IllegalStateException(
					"Slayer route profile has no exact NPC aliases: "
						+ profile.taskName + " @ " + profile.location
				);
			}

			if (profile.primaryDestination != null)
			{
				validateCoordinate(
					profile.primaryDestination,
					"route destination for " + profile.taskName + " @ " + profile.location
				);
			}
			else if (!profile.requiresLoadedNpc())
			{
				throw new IllegalStateException(
					"Static Slayer route mode has no destination: "
						+ profile.taskName + " @ " + profile.location
				);
			}

			if (profile.isStaged())
			{
				if (profile.transitionSpec == null
					|| profile.surfaceAccess == null
					|| profile.primaryDestination == null
					|| profile.encounterArea == null)
				{
					throw new IllegalStateException(
						"Staged route lacks an explicit transition contract: "
							+ profile.taskName + " @ " + profile.location
					);
				}
				validateCoordinate(
					profile.surfaceAccess,
					"surface access for " + profile.taskName + " @ " + profile.location
				);
				if (!profile.surfaceAccess.equals(profile.transitionSpec.getApproachPoint()))
				{
					throw new IllegalStateException(
						"Staged route transition approach drifted from surface access: "
							+ profile.taskName + " @ " + profile.location
					);
				}
				if (profile.transitionSpec.getActions().isEmpty())
				{
					throw new IllegalStateException(
						"Staged route has no allowed entrance interaction: "
							+ profile.taskName + " @ " + profile.location
					);
				}
				if (profile.transitionInstruction.isEmpty())
				{
					throw new IllegalStateException(
						"Staged route has no transition instruction: "
							+ profile.taskName + " @ " + profile.location
					);
				}
			}

			final WorldPoint access = ACCESS_ROUTES.get(normalize(profile.location));
			if (profile.isVerifiedInterior()
				&& !profile.isStaged()
				&& profile.mode != RouteMode.TRANSPORT_CHAIN_INTERIOR
				&& prefersWorldMapEntranceIcon(profile.location)
				&& access != null
				&& profile.primaryDestination != null
				&& !sameCoordinateLayer(access, profile.primaryDestination))
			{
				throw new IllegalStateException(
					"Cross-layer dungeon interior bypasses the explicit entrance stage: "
						+ profile.taskName + " @ " + profile.location
				);
			}
		}
	}

	private static void validateCannonPositions()
	{
		for (final Map.Entry<String, WorldPoint> entry : CANNON_POSITIONS.entrySet())
		{
			validateCoordinate(entry.getValue(), "cannon position " + entry.getKey());
			RouteProfile profile = STANDARD_PROFILES.get(entry.getKey());
			if (profile == null)
			{
				profile = BOSS_PROFILES.get(entry.getKey());
			}
			if (profile == null)
			{
				throw new IllegalStateException(
					"Cannon position has no matching route profile: " + entry.getKey()
				);
			}
			if (profile.encounterArea != null
				&& !profile.encounterArea.contains(entry.getValue()))
			{
				throw new IllegalStateException(
					"Cannon position is outside the reviewed encounter area: "
						+ profile.taskName + " @ " + profile.location
				);
			}
		}
	}

	private static void validateBarragePositions()
	{
		for (final Map.Entry<String, WorldPoint> entry : BARRAGE_POSITIONS.entrySet())
		{
			validateCoordinate(entry.getValue(), "barrage position " + entry.getKey());
			final RouteProfile profile = STANDARD_PROFILES.get(entry.getKey());
			if (profile == null)
			{
				throw new IllegalStateException(
					"Barrage position has no matching standard route profile: " + entry.getKey()
				);
			}
			if (profile.encounterArea != null
				&& !profile.encounterArea.contains(entry.getValue()))
			{
				throw new IllegalStateException(
					"Barrage position is outside the reviewed encounter area: "
						+ profile.taskName + " @ " + profile.location
				);
			}
		}
	}

	private static void validateCoordinate(
		final WorldPoint point,
		final String label)
	{
		if (point == null || point.getX() <= 0 || point.getY() <= 0
			|| point.getPlane() < 0 || point.getPlane() > 3)
		{
			throw new IllegalStateException("Invalid Slayer " + label + ": " + point);
		}
	}

	private SlayerRouteCatalog()
	{
	}

	/**
	 * Full-run encounters can require a final preparation bank before their exact
	 * entry interaction. At present this is deliberately limited to TzKal-Zuk.
	 */
	public static boolean requiresPreparationBank(
		final String taskName,
		final String location,
		final boolean bossVariant)
	{
		return bossVariant
			&& normalizeTaskKey(taskName).equals("tzkal zuk")
			&& normalize(location).equals("inferno");
	}

	/**
	 * Exact live NPC names that own the final interaction after preparation.
	 * The values are normalized because runtime NPC matching is normalized too.
	 */
	public static Set<String> getPreparationEntryNpcNames(
		final String taskName,
		final String location,
		final boolean bossVariant)
	{
		return requiresPreparationBank(taskName, location, bossVariant)
			? INFERNO_ENTRY_NPC_NAMES
			: Collections.emptySet();
	}

	/**
	 * Reviewed waypoint that makes the final dungeon-entry NPC load after the
	 * preparation-bank segment. The live NPC remains the authoritative endpoint.
	 */
	public static WorldPoint getPreparationEntryApproachTarget(
		final String taskName,
		final String location,
		final boolean bossVariant)
	{
		return requiresPreparationBank(taskName, location, bossVariant)
			? INFERNO_ENTRY_APPROACH
			: null;
	}

	/**
	 * Resolve the exact selected encounter. Boss requests are strict: they never
	 * fall back to a standard-task profile or to a generic location entrance.
	 */
	public static RouteProfile resolve(
		final String taskName,
		final String location,
		final boolean bossVariant)
	{
		final String task = normalizeTaskKey(taskName);
		final String area = normalize(location);
		if (task.isEmpty() || area.isEmpty())
		{
			return null;
		}

		final String key = profileKey(task, area);
		if (bossVariant)
		{
			final RouteProfile reviewedBoss = BOSS_PROFILES.get(key);
			if (reviewedBoss != null)
			{
				return reviewedBoss;
			}

			/*
			 * Every audited boss location has a physical access waypoint. This gives
			 * Shortest Path a useful first destination while the boss is still outside
			 * the loaded scene. Exact NPC aliases remain the only allowed continuation,
			 * so this cannot drift into a regular-task room with a similar name.
			 */
			final WorldPoint bossAccess = ACCESS_ROUTES.get(area);
			if (bossAccess != null)
			{
				return bossAccessProfile(
					taskName,
					location,
					bossAccess,
					defaultNpcAliases(taskName)
				);
			}
			return npcOnlyProfile(
				taskName,
				location,
				defaultNpcAliases(taskName),
				true
			);
		}

		final RouteProfile reviewed = STANDARD_PROFILES.get(key);
		if (reviewed != null)
		{
			return reviewed;
		}

		/*
		 * A Konar-assigned or newly reviewed area may not yet have a verified
		 * room tile. An exact known entrance is safe, provided continuation uses
		 * exact NPC names. Never use this fallback for a boss.
		 */
		final WorldPoint access = ACCESS_ROUTES.get(area);
		if (access == null)
		{
			return npcOnlyProfile(
				taskName,
				location,
				defaultNpcAliases(taskName),
				false
			);
		}

		return accessProfile(
			taskName,
			location,
			access,
			defaultNpcAliases(taskName)
		);
	}

	/** Backwards-compatible standard-task lookup used by recommendations. */
	public static WorldPoint find(
		final String taskName,
		final String location)
	{
		final RouteProfile profile = resolve(taskName, location, false);
		return profile == null ? null : profile.getPrimaryDestination();
	}

	/** Exact location-only access lookup. No fuzzy substring matching. */
	public static WorldPoint find(final String location)
	{
		return ACCESS_ROUTES.get(normalize(location));
	}

	static Set<String> reviewedAccessLocationsForRegression()
	{
		return Collections.unmodifiableSet(
			new LinkedHashSet<>(ACCESS_ROUTES.keySet())
		);
	}

	/** True only for an authored task/location profile. Generic access and
	 * NPC-only fallbacks are intentionally excluded from release coverage. */
	static boolean hasExplicitProfileForRegression(
		final String taskName,
		final String location,
		final boolean bossVariant)
	{
		final String task = normalizeTaskKey(taskName);
		final String area = normalize(location);
		if (task.isEmpty() || area.isEmpty())
		{
			return false;
		}
		return (bossVariant ? BOSS_PROFILES : STANDARD_PROFILES)
			.containsKey(profileKey(task, area));
	}

	static Set<String> explicitProfileKeysForRegression(
		final boolean bossVariant)
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(
			(bossVariant ? BOSS_PROFILES : STANDARD_PROFILES).keySet()
		));
	}

	public static boolean hasTaskSpecificRoute(
		final String taskName,
		final String location)
	{
		final RouteProfile profile = resolve(taskName, location, false);
		return profile != null && profile.isVerifiedInterior();
	}

	public static boolean prefersWorldMapEntranceIcon(final String location)
	{
		final String area = normalize(location);
		return area.contains("dungeon")
			|| area.contains("cave")
			|| area.contains("cavern")
			|| area.contains("catacombs")
			|| area.contains("tunnel")
			|| area.contains("lair")
			|| area.contains("prison")
			|| area.contains("vault")
			|| area.contains("chasm")
			|| area.contains("stronghold")
			|| area.contains("slayer tower")
			|| area.contains("cove");
	}

	/**
	 * Returns the exact authored cannon stand tile for the selected regular
	 * encounter/location, or null when no cannon position has been reviewed.
	 * A missing entry must never be guessed from a room centre or NPC tile.
	 */
	public static WorldPoint findCannonPosition(
		final String taskName,
		final String location)
	{
		final String task = normalizeTaskKey(taskName);
		final String area = normalize(location);
		if (task.isEmpty() || area.isEmpty())
		{
			return null;
		}
		return CANNON_POSITIONS.get(profileKey(task, area));
	}

	/** Returns a reviewed barrage/stacking position, never an arbitrary NPC tile. */
	public static WorldPoint findBarragePosition(
		final String taskName,
		final String location)
	{
		final String task = normalizeTaskKey(taskName);
		final String area = normalize(location);
		if (task.isEmpty() || area.isEmpty())
		{
			return null;
		}
		return BARRAGE_POSITIONS.get(profileKey(task, area));
	}

	public static boolean isWilderness(final String value)
	{
		final String normalized = normalize(value);
		return normalized.contains("wilderness")
			|| normalized.contains("revenant")
			|| normalized.contains("forinthry")
			|| normalized.equals("hunter s end")
			|| normalized.equals("callisto s den")
			|| normalized.equals("scorpia s cave")
			|| normalized.equals("web chasm")
			|| normalized.equals("silk chasm")
			|| normalized.equals("skeletal tomb")
			|| normalized.equals("vet ion s rest");
	}

	/**
	 * Creates several valid end candidates around a room or NPC instead of one
	 * possibly blocked center tile. Shortest Path can choose the closest
	 * reachable candidate and therefore avoids false "unreachable" warnings.
	 */
	public static Set<WorldPoint> targetArea(
		final WorldPoint center,
		final int radius)
	{
		if (center == null)
		{
			return Collections.emptySet();
		}

		final int safeRadius = Math.max(0, radius);
		final Set<WorldPoint> targets = new LinkedHashSet<>();
		for (int dx = -safeRadius; dx <= safeRadius; dx++)
		{
			for (int dy = -safeRadius; dy <= safeRadius; dy++)
			{
				targets.add(new WorldPoint(
					center.getX() + dx,
					center.getY() + dy,
					center.getPlane()
				));
			}
		}
		return Collections.unmodifiableSet(targets);
	}

	private static Map<String, WorldPoint> createCannonPositions()
	{
		final Map<String, WorldPoint> positions = new LinkedHashMap<>();

		/*
		 * Mourner Tunnels: exact verified Dark-beast cannon tile. The Slayer
		 * ring ME2 Caves teleport lands at 2028,4636; the teleport landing is
		 * only the travel stage and must not be treated as the task destination.
		 * Keep this singleton so Shortest Path ends on the cannon stand tile.
		 */
		positions.put(
			profileKey(normalizeTaskKey("Dark beasts"), normalize("Mourner Tunnels")),
			new WorldPoint(1992, 4655, 0)
		);

		/*
		 * Meiyerditch: free central setup tile beside the seven Mutated Bloodveld
		 * spawns. The old 3594,9742 endpoint is itself an NPC spawn and therefore
		 * was neither a stable path destination nor a valid cannon placement.
		 */
		positions.put(
			profileKey(
				normalizeTaskKey("Bloodvelds"),
				normalize("Meiyerditch Laboratories")
			),
			new WorldPoint(3594, 9743, 0)
		);

		/* Current Wiki spawn geometry: centre the cannon on free floor tiles. */
		positions.put(
			profileKey(
				normalizeTaskKey("Bloodvelds"),
				normalize("Buccaneers' Laboratory")
			),
			new WorldPoint(2096, 10099, 0)
		);
		positions.put(
			profileKey(
				normalizeTaskKey("Bloodvelds"),
				normalize("Iorwerth Dungeon")
			),
			new WorldPoint(3236, 12438, 0)
		);
		/* Safe tile 4 in the Wiki's eight-position Stronghold safespot map. */
		positions.put(
			profileKey(
				normalizeTaskKey("Bloodvelds"),
				normalize("Stronghold Slayer Cave")
			),
			new WorldPoint(2465, 9833, 0)
		);

		/*
		 * Mos Le'Harmless Cave: the authored interior endpoint is the open
		 * cannon setup tile in the Cave horror cluster. Keep it in the method
		 * map as well as the route profile so an exact loaded Cave horror cannot
		 * replace the setup destination after the cave transition.
		 */
		positions.put(
			profileKey(
				normalizeTaskKey("Cave horrors"),
				normalize("Mos Le'Harmless Cave")
			),
			new WorldPoint(3775, 9407, 0)
		);

		return Collections.unmodifiableMap(positions);
	}

	private static Map<String, WorldPoint> createBarragePositions()
	{
		final Map<String, WorldPoint> positions = new LinkedHashMap<>();
		/* Centre corridor between the two primary Catacombs rooms. This is the
		 * reviewed lure/stack position; routing to the nearest moving demon can
		 * otherwise finish in the smaller melee room. */
		positions.put(
			profileKey(
				normalizeTaskKey("Abyssal demons"),
				normalize("Catacombs of Kourend")
			),
			new WorldPoint(1675, 10091, 0)
		);
		return Collections.unmodifiableMap(positions);
	}

	private static Map<String, WorldPoint> createAccessRoutes()
	{
		final Map<String, WorldPoint> routes = new LinkedHashMap<>();

		putAccess(routes, "Slayer Tower", 3428, 3536, 0);
		putAccess(routes, "Catacombs of Kourend", 1639, 3673, 0);
		putAccess(routes, "Fremennik Slayer Dungeon", 2796, 3615, 0);
		putAccess(routes, "Kraken Cove", 2280, 3616, 0);
		putAccess(routes, "Lighthouse Dungeon", 2518, 3630, 0);
		putAccess(routes, "Lighthouse", 2518, 3630, 0);
		putAccess(routes, "Jormungand's Prison", 2445, 3747, 0);
		putAccess(routes, "Waterbirth Island Dungeon", 2443, 3746, 0);
		putAccess(routes, "Death Plateau", 2847, 3635, 0);
		putAccess(routes, "Taverley Dungeon", 2885, 3397, 0);
		putAccess(routes, "Cerberus' Lair", 2885, 3397, 0);
		putAccess(routes, "Key Master", 2885, 3397, 0);
		putAccess(routes, "Stronghold of Security", 3081, 3421, 0);
		putAccess(routes, "Stronghold Slayer Cave", 2432, 3423, 0);
		putAccess(routes, "Gnome Stronghold Slayer Cave", 2432, 3423, 0);
		putAccess(routes, "Nieve's Slayer Cave", 2432, 3423, 0);
		putAccess(routes, "Steve's Slayer Cave", 2432, 3423, 0);
		putAccess(routes, "Asgarnian Ice Dungeon", 3009, 3150, 0);
		putAccess(routes, "God Wars Dungeon", 2916, 3746, 0);
		putAccess(routes, "Wilderness God Wars Dungeon", 3017, 3740, 0);
		putAccess(routes, "Chasm of Fire", 1435, 3670, 0);
		putAccess(routes, "Darkmeyer", 3604, 3364, 0);
		/* OSRS Wiki map pin for the physical cave entrance north-east of Darkmeyer. */
		putAccess(routes, "Morytania Spider Cave", 3657, 3407, 0);
		putAccess(routes, "Meiyerditch Laboratories", 3638, 3304, 0);
		putAccess(routes, "Buccaneers' Laboratory", 2075, 3688, 0);
		putAccess(routes, "Slayer Tower (first floor)", 3428, 3536, 0);
		putAccess(routes, "Slayer Tower basement", 3428, 3536, 0);
		putAccess(routes, "Lunar Isle", 2112, 3915, 0);
		/* Exact Dramen/Lunar-staff shed-door approach; Zanaris is the next layer. */
		putAccess(routes, "Zanaris", 3202, 3169, 0);
		putAccess(routes, "Brine Rat Cavern", 2730, 3714, 0);
		putAccess(routes, "Karuulm Slayer Dungeon", 1311, 3783, 0);
		putAccess(routes, "Mount Karuulm", 1311, 3783, 0);
		putAccess(routes, "Smoke Devil Dungeon", 2411, 3058, 0);
		/*
		 * Shortest Path transport 30842 uses the task-only Mushroom Forest
		 * Trap Door at 3680,3854 and lands at 3595,10291. The former 3605,3815
		 * point was not an entrance and made the route fail before or immediately
		 * after the Digsite-pendant leg.
		 */
		putAccess(routes, "Wyvern Cave on Fossil Island", 3680, 3854, 0);
		putAccess(routes, "Fossil Island Wyvern Cave", 3680, 3854, 0);
		putAccess(routes, "Ancient Cavern", 2512, 3511, 0);
		putAccess(routes, "Lletya", 2355, 3172, 0);
		/*
		 * Iorwerth routing must end the surface leg at the dungeon entrance, not at
		 * the Prifddinas teleport-crystal landing. Shortest Path can choose a valid
		 * Prifddinas teleport, then walk the remaining tiles to this reviewed access
		 * anchor; once loaded, SlayerPlus snaps to the interactable entrance object.
		 */
		putAccess(routes, "Iorwerth Dungeon", 3225, 6045, 0);
		/*
		 * Mourner Tunnels has a dedicated Shortest Path transport landing at
		 * 2028,4636. This is not a generic Slayer-ring fallback: the plugin only
		 * accepts it when the completed path explicitly reports the Dark Beasts
		 * destination for this exact route.
		 */
		putAccess(routes, "Mourner Tunnels", 2028, 4636, 0);
		putAccess(routes, "Mos Le'Harmless Cave", 3748, 2974, 0);
		putAccess(routes, "Kalphite Slayer Cave", 3321, 3122, 0);
		putAccess(routes, "Kalphite Cave", 3321, 3122, 0);
		putAccess(routes, "Lithkren Vault", 1568, 5088, 0);
		putAccess(routes, "Wilderness Slayer Cave", 3056, 3765, 0);
		putAccess(routes, "Ferox Enclave", 3130, 3630, 0);

		/*
		 * Catalog-wide audited destinations. Outdoor entries are useful arrival
		 * tiles; dungeon entries are their surface door/transport waypoint. The
		 * exact task NPC becomes authoritative as soon as it is loaded.
		 */
		putAccess(routes, "Abyssal Nexus", 3107, 4791, 0);
		putAccess(routes, "Al Kharid mine", 3298, 3293, 0);
		putAccess(routes, "Ancient Guthixian Temple", 4097, 4419, 0);
		putAccess(routes, "Ardougne area", 2570, 3300, 0);
		putAccess(routes, "Bandit Camp", 3171, 2979, 0);
		putAccess(routes, "Barrows", 3567, 3291, 0);
		putAccess(routes, "Black Knights' Fortress", 3015, 3514, 0);
		putAccess(routes, "Braindeath Island", 3680, 3536, 0);
		putAccess(routes, "Brimhaven Dungeon", 2745, 3152, 0);
		putAccess(routes, "Callisto's Den", 3290, 3855, 0);
		putAccess(routes, "Crash Site Cavern", 2464, 3494, 0);
		putAccess(routes, "Deep Wilderness", 3045, 3925, 0);
		putAccess(routes, "Desert quarry", 3172, 2912, 0);
		putAccess(routes, "Dorgesh-Kaan South Dungeon", 3169, 3173, 0);
		putAccess(routes, "Dwarven Mine", 3018, 3450, 0);
		putAccess(routes, "Edgeville Dungeon", 3096, 3468, 0);
		putAccess(routes, "Falador Mole Lair", 2996, 3377, 0);
		putAccess(routes, "Feldip Hills", 2515, 2954, 0);
		putAccess(routes, "Fight Caves", 2439, 5172, 0);
		putAccess(routes, "TzHaar Fight Cave", 2439, 5172, 0);
		putAccess(routes, "Forthos Dungeon", 1667, 3570, 0);
		putAccess(routes, "Fossil Island", 3683, 3706, 0);
		putAccess(routes, "Ghorrock Dungeon", 2920, 3930, 0);
		putAccess(routes, "Grimstone Dungeon", 2912, 4066, 0);
		putAccess(routes, "Haunted Woods", 3584, 3492, 0);
		putAccess(routes, "Hunter's End", 3112, 3670, 0);
		putAccess(routes, "Ice Mountain", 3010, 3486, 0);
		putAccess(routes, "Isle of Souls", 2210, 2900, 0);
		putAccess(routes, "Kalphite Lair", 3228, 3109, 0);
		putAccess(routes, "Karamja", 2843, 3070, 0);
		putAccess(routes, "Karamja Volcano", 2855, 3168, 0);
		putAccess(routes, "Inferno", 2496, 5115, 0);
		putAccess(routes, "Kebos Lowlands", 1248, 3726, 0);
		/* The exact route begins at Draynor Manor's ground-floor stairs. */
		putAccess(routes, "Killerwatt plane", 3108, 3362, 0);
		putAccess(routes, "King Black Dragon Lair", 3017, 3850, 0);
		putAccess(routes, "Kourend Woodland", 1540, 3464, 0);
		putAccess(routes, "Lassar Undercity", 3000, 3494, 0);
		putAccess(routes, "Legends' Guild basement", 2728, 3348, 0);
		putAccess(routes, "Lumbridge area", 3188, 3220, 0);
		putAccess(routes, "Lumbridge cow field", 3253, 3266, 0);
		putAccess(routes, "Lumbridge Swamp Caves", 3169, 3173, 0);
		/*
		 * The exact plan begins at Castle Drakan's reviewed Vampyrium portal.
		 * Darkmeyer (3604, 3364) is not an access point for this encounter.
		 */
		putAccess(routes, "Maggot King's lair",
			MAGGOT_KING_CASTLE_PORTAL_ACCESS.getX(),
			MAGGOT_KING_CASTLE_PORTAL_ACCESS.getY(),
			MAGGOT_KING_CASTLE_PORTAL_ACCESS.getPlane());
		putAccess(routes, "Mogre beach", 2996, 3106, 0);
		putAccess(routes, "Mor Ul Rek east bank", 2496, 5115, 0);
		putAccess(routes, "Mort'ton", 3488, 3288, 0);
		putAccess(routes, "Nardah desert", 3427, 2911, 0);
		putAccess(routes, "Piscatoris area", 2332, 3683, 0);
		putAccess(routes, "Poison Waste Dungeon", 2322, 3099, 0);
		putAccess(routes, "Royal Titans arena", 2982, 3113, 0);
		/*
		 * The post-quest Tower of Ascension lift is the fast overworld access.
		 * 1362,4511 is Amoxliatl's underground room and must never be used as an
		 * overworld access point.
		 */
		putAccess(routes, "Ruins of Tapoyauik", 1641, 3221, 0);
		/* Scorpion Pit cave entrance used as Scorpia's deterministic bank-start leg. */
		putAccess(routes, "Scorpia's Cave", 3232, 3950, 0);
		/*
		 * The two singles-plus Wilderness bosses use separate surface cave
		 * entrances. Route to the entrance first; loaded-scene discovery takes
		 * over after the player crosses into the lair.
		 */
		putAccess(routes, "Web Chasm", 3294, 3749, 0);
		putAccess(routes, "Skeletal Tomb", 3152, 3644, 0);
		putAccess(routes, "Silk Chasm", 3321, 3794, 0);
		putAccess(routes, "Vet'ion's Rest", 3219, 3788, 0);
		/* The Great Conch's gryphon-cave entrance, not Ynysdail Cavern. */
		putAccess(routes, "The Great Conch", 3176, 2477, 0);
		putAccess(routes, "Shellbane Gryphon Cave", 3176, 2477, 0);
		putAccess(routes, "Slayer Tower rooftop", 3428, 3536, 0);
		putAccess(routes, "Sophanem Dungeon", 3315, 2796, 0);
		/* Skotizo is entered through the Catacombs; the totem interaction remains manual. */
		putAccess(routes, "Skotizo's Lair", 1639, 3673, 0);
		putAccess(routes, "Stalker Den", 1325, 3364, 0);
		putAccess(routes, "Stranglewood Temple", 1128, 3417, 0);
		putAccess(routes, "The Scar", 2081, 6372, 0);
		/* Quest Helper's reviewed Vorkath step anchors the boss at 2273,4065. */
		putAccess(routes, "Ungael", 2640, 3696, 0);
		putAccess(routes, "Varrock Sewers", 3237, 3458, 0);
		/* Current Turael/Aya point-farming endpoints. Open-world entries use a
		 * walkable tile in the selected pack; dungeon entries remain staged by
		 * their separately authored surface access above. */
		putAccess(routes, "Silvarea limestone mine", 3371, 3493, 0);
		putAccess(routes, "South-west of Legends' Guild", 2699, 3318, 0);
		putAccess(routes, "West of Champions' Guild", 3179, 3345, 0);
		putAccess(routes, "North of Nardah", 3422, 2945, 0);
		putAccess(routes, "White Wolf Mountain tunnel", 2876, 3481, 0);
		putAccess(routes, "White Wolf Tunnel pub", 2876, 3481, 0);
		putAccess(routes, "East of Sophanem", 3354, 2783, 0);
		putAccess(routes, "East of Lumbridge", 3259, 3236, 0);
		putAccess(routes, "Karamja glider clearing", 2970, 2972, 0);
		putAccess(routes, "Digsite Dungeon", 3369, 3426, 0);
		putAccess(routes, "Outside H.A.M. Hideout", 3166, 3251, 0);
		putAccess(routes, "White Wolf Mountain", 2868, 3492, 0);
		putAccess(routes, "Alice's farm", 3620, 3528, 0);
		putAccess(routes, "Waterbirth Island", 2525, 3743, 0);
		putAccess(routes, "Wilderness", 3130, 3630, 0);
		putAccess(routes, "Wilderness boss cave", 3130, 3630, 0);
		putAccess(routes, "Wilderness green dragon area", 3331, 3672, 0);
		/* Gwenith end of the built rowboat; the island is not walkable from here. */
		putAccess(routes, "Ynysdail Cavern", 2218, 3424, 0);
		putAccess(routes, "Zul-Andra", 2211, 3057, 0);

		return Collections.unmodifiableMap(routes);
	}

	private static Map<String, RouteProfile> createStandardProfiles()
	{
		final Map<String, RouteProfile> profiles = new LinkedHashMap<>();

		/*
		 * Catacombs of Kourend is a real surface -> dungeon transition. Every
		 * Catacombs encounter must first route to the reviewed Kourend Castle
		 * entrance at 1639,3673, then continue only after the player is on the
		 * Catacombs coordinate layer. Sending an underground room coordinate
		 * directly from the surface skips the entrance and leaves Shortest Path
		 * pointing at an unreachable underground destination.
		 */
		putStagedInterior(profiles, "Abyssal demons", "Catacombs of Kourend",
			1639, 3673, 0, 1670, 10091, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue inside to the selected Slayer monster.",
			"", "Abyssal demon");
		putStagedInterior(profiles, "Bloodvelds", "Catacombs of Kourend",
			1639, 3673, 0, 1681, 10073, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue inside to the selected Slayer monster.",
			"", "Mutated bloodveld");
		putStagedInterior(profiles, "Dust devils", "Catacombs of Kourend",
			1639, 3673, 0, 1715, 10025, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue inside to the Dust devil room.",
			"", "Dust devil");
		putStagedInterior(profiles, "Nechryaels", "Catacombs of Kourend",
			1639, 3673, 0, 1700, 10083, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue inside to the selected Slayer monster.",
			"", "Nechryael");
		putStagedInterior(profiles, "Hellhounds", "Catacombs of Kourend",
			1639, 3673, 0, 1650, 10065, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue inside to the selected Slayer monster.",
			"", "Hellhound");
		putStagedInterior(profiles, "Fire giants", "Catacombs of Kourend",
			1639, 3673, 0, 1634, 10068, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue inside to the selected Slayer monster.",
			"", "Fire giant");
		putStagedInterior(profiles, "Greater demons", "Catacombs of Kourend",
			1639, 3673, 0, 1701, 10102, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue inside to the selected Slayer monster.",
			"", "Greater demon");
		putStagedInterior(profiles, "Black demons", "Catacombs of Kourend",
			1639, 3673, 0, 1722, 10087, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue inside to the selected Slayer monster.",
			"", "Black demon");
		putStagedInterior(profiles, "Aberrant spectres", "Catacombs of Kourend",
			1639, 3673, 0, 1613, 10010, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue inside to the selected Slayer monster.",
			"", "Deviant spectre");
		putStagedInterior(profiles, "Ankou", "Catacombs of Kourend",
			1639, 3673, 0, 1637, 9991, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue southwest to the Ankou pack in Reeking Cove.",
			"", "Ankou");
		putStagedInterior(profiles, "Ghosts", "Catacombs of Kourend",
			1639, 3673, 0, 1689, 10063, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue east to the reviewed Ghost pack.",
			"", "Ghost");
		putStagedInterior(profiles, "Skeletons", "Catacombs of Kourend",
			1639, 3673, 0, 1642, 9996, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue southwest to the reviewed Skeleton pack.",
			"", "Skeleton");
		putStagedInterior(profiles, "Zombies", "Catacombs of Kourend",
			1639, 3673, 0, 1642, 10075, 0,
			"Investigate the King Rada I statue to enter the Catacombs of Kourend; SlayerPlus will continue west to the reviewed Zombie pack.",
			"", "Zombie");

		// Stronghold Slayer Cave reviewed rooms.
		putStagedInteriorFromAccessWithPositioning(
			profiles,
			"Hellhounds",
			"Stronghold Slayer Cave",
			2424, 9793, 0,
			"Range from the side safespot so you are not in direct combat; this lets the dwarf multicannon keep engaging nearby Hellhounds in the single-combat room.",
			"Hellhound"
		);
		putStagedInteriorFromAccessWithPositioning(
			profiles,
			"Hellhounds",
			"Gnome Stronghold Slayer Cave",
			2424, 9793, 0,
			"Range from the side safespot so you are not in direct combat; this lets the dwarf multicannon keep engaging nearby Hellhounds in the single-combat room.",
			"Hellhound"
		);
		putStagedInteriorFromAccess(profiles, "Bloodvelds", "Stronghold Slayer Cave", 2465, 9833, 0,
			"Bloodveld");
		putStagedInteriorFromAccess(profiles, "Ankou", "Stronghold Slayer Cave", 2479, 9806, 0,
			"Ankou");
		putStagedInteriorFromAccess(profiles, "Aberrant spectres", "Stronghold Slayer Cave", 2457, 9788, 0,
			"Aberrant spectre");
		putStagedInteriorFromAccess(profiles, "Fire giants", "Stronghold Slayer Cave", 2401, 9779, 0,
			"Fire giant");

		// Slayer Tower reviewed floors.
		putStagedInteriorFromAccess(profiles, "Abyssal demons", "Slayer Tower", 3447, 9970, 3,
			"Abyssal demon");
		/*
		 * Shortest Path has explicit Slayer Tower staircase transports, so the
		 * ordinary first-floor room is a continuous route from the overworld. The
		 * old 3395,9930,3 point was neither a first-floor nor a Bloodveld spawn.
		 */
		putTraversableInterior(profiles, "Bloodvelds", "Slayer Tower", 3417, 3567, 1,
			"Bloodveld");
		putTraversableInterior(profiles, "Bloodvelds", "Slayer Tower (first floor)", 3417, 3567, 1,
			"Bloodveld");
		putStagedInteriorFromAccess(profiles, "Bloodvelds", "Slayer Tower basement", 3403, 9947, 3,
			"Bloodveld");
		putStagedInteriorFromAccess(profiles, "Nechryaels", "Slayer Tower", 3397, 9980, 3,
			"Nechryael");
		putStagedInteriorFromAccess(profiles, "Gargoyles", "Slayer Tower", 3452, 9935, 3,
			"Gargoyle");
		putStagedInteriorFromAccess(profiles, "Aberrant spectres", "Slayer Tower",
			3422, 3549, 1, "Aberrant spectre");
		putStagedInteriorFromAccess(profiles, "Infernal mages", "Slayer Tower",
			3438, 3559, 1, "Infernal mage");
		putTraversableInterior(profiles, "Banshees", "Slayer Tower", 3441, 3540, 0,
			"Banshee");
		putTraversableInterior(profiles, "Crawling hands", "Slayer Tower", 3414, 3536, 0,
			"Crawling hand");

		// Fremennik and Jormungand dungeon rooms.
		putStagedInteriorFromAccess(profiles, "Basilisks", "Fremennik Slayer Dungeon", 2744, 10004, 0,
			"Basilisk");
		putStagedInteriorFromAccess(profiles, "Turoths", "Fremennik Slayer Dungeon", 2716, 10011, 0,
			"Turoth");
		putStagedInteriorFromAccess(profiles, "Kurasks", "Fremennik Slayer Dungeon", 2701, 10001, 0,
			"Kurask");
		putStagedInteriorFromAccess(profiles, "Cave crawlers", "Fremennik Slayer Dungeon", 2792, 9996, 0,
			"Cave crawler");
		putStagedInteriorFromAccess(profiles, "Cockatrice", "Fremennik Slayer Dungeon", 2789, 10031, 0,
			"Cockatrice");
		putStagedInteriorFromAccess(profiles, "Jellies", "Fremennik Slayer Dungeon", 2705, 10019, 0,
			"Jelly");
		putStagedInteriorFromAccess(profiles, "Pyrefiends", "Fremennik Slayer Dungeon", 2764, 10003, 0,
			"Pyrefiend");
		putStagedInteriorFromAccess(profiles, "Rockslugs", "Fremennik Slayer Dungeon", 2794, 10017, 0,
			"Rockslug");
		putStagedInteriorFromAccess(profiles, "Basilisks", "Jormungand's Prison", 2455, 10388, 0,
			"Basilisk knight");
		putStagedInteriorFromAccess(profiles, "Dagannoths", "Jormungand's Prison", 2420, 10422, 0,
			"Dagannoth");

		// Iorwerth Dungeon reviewed rooms.
		putStagedInteriorFromAccess(profiles, "Kurasks", "Iorwerth Dungeon", 3219, 12369, 0,
			"Kurask");
		putStagedInteriorWide(
			profiles,
			"Dark beasts",
			"Iorwerth Dungeon",
			3225, 6045, 0,
			3227, 12397, 0,
			"Enter the Iorwerth Dungeon at the reviewed entrance; SlayerPlus will continue inside to the Dark beasts.",
			"",
			"Dark beast", "Night beast"
		);
		putStagedInteriorFromAccess(profiles, "Waterfiends", "Iorwerth Dungeon", 3183, 12456, 0,
			"Waterfiend");
		putStagedInteriorFromAccess(profiles, "Nechryaels", "Iorwerth Dungeon", 3228, 12459, 0,
			"Nechryael");
		putStagedInteriorFromAccess(profiles, "Bloodvelds", "Iorwerth Dungeon", 3236, 12438, 0,
			"Mutated bloodveld");
		putStagedInteriorFromAccess(profiles, "Elves", "Iorwerth Dungeon", 3184, 12405, 0,
			"Iorwerth warrior", "Iorwerth archer");

		// Karuulm and Chasm reviewed rooms.
		putStagedInteriorFromAccess(profiles, "Wyrms", "Karuulm Slayer Dungeon", 1273, 10189, 0,
			"Wyrm");
		putStagedInteriorFromAccess(profiles, "Drakes", "Karuulm Slayer Dungeon", 1312, 10242, 1,
			"Drake");
		putStagedInteriorFromAccess(profiles, "Hydras", "Karuulm Slayer Dungeon", 1311, 10241, 0,
			"Hydra");
		putStagedInteriorFromAccessWithPositioning(
			profiles,
			"Greater demons",
			"Chasm of Fire",
			1280, 10208, 1,
			"For the cannon method, attack with Ranged from behind the middle-floor rocks or narrow pillars and remain untargetable. Melee engagement makes the single-combat cannon fire at only that one demon.",
			"Greater demon"
		);
		putStagedInteriorFromAccess(profiles, "Fire giants", "Chasm of Fire", 1282, 10212, 2,
			"Fire giant");
		putStagedInteriorFromAccess(profiles, "Hellhounds", "Chasm of Fire", 1342, 10201, 2,
			"Hellhound");

		// Other verified cave rooms.
		putStagedInteriorWide(
			profiles,
			"Smoke devils",
			"Smoke Devil Dungeon",
			2411, 3058, 0,
			2408, 9438, 0,
			"Enter the Smoke Devil Dungeon; SlayerPlus will continue to the reviewed Smoke Devil barrage room.",
			"Place the dwarf multicannon near the room centre, then stack the Smoke devils around the western skeleton pile before using Ice Barrage or Ice Burst.",
			"Smoke devil"
		);
		putStagedInteriorFromAccessWithPositioning(
			profiles,
			"Cave horrors",
			"Mos Le'Harmless Cave",
			3775, 9407, 0,
			"With cannon enabled, follow the interior route to the reviewed open setup tile before placing the dwarf multicannon.",
			"Cave horror"
		);
		putStagedInteriorFromAccess(profiles, "Skeletal wyverns", "Asgarnian Ice Dungeon", 3057, 9547, 0,
			"Skeletal wyvern");
		putStagedInteriorFromAccess(profiles, "Waterfiends", "Ancient Cavern", 1738, 5356, 0,
			"Waterfiend");
		putStagedInterior(
			profiles,
			"Fossil Island wyverns",
			"Wyvern Cave on Fossil Island",
			3680, 3854, 0,
			3616, 10272, 0,
			"Use the Magic Mushtree to Mushroom Meadow, run south, then climb down "
				+ "the task-only Wyvern Cave trapdoor; SlayerPlus will resume inside.",
			"",
			"Ancient wyvern", "Long-tailed wyvern", "Spitting wyvern", "Taloned wyvern"
		);

		/*
		 * Meiyerditch requires both the slashed-tapestry and staircase transports.
		 * Shortest Path models both, so route through the full chain instead of
		 * stopping at the old Darkmeyer landing and waiting for an NPC that cannot
		 * load there.
		 */
		putTraversableInterior(profiles, "Bloodvelds", "Meiyerditch Laboratories",
			3594, 9743, 0, "Bloodveld", "Mutated bloodveld");
		putStagedInteriorFromAccess(profiles, "Bloodvelds", "Buccaneers' Laboratory",
			2096, 10099, 0, "Mutated bloodveld");
		putStagedInteriorFromAccess(profiles, "Bloodvelds", "God Wars Dungeon",
			2888, 5324, 2, "Bloodveld");
		/*
		 * The Wilderness GWD surface entrance first loads an empty obstacle cavern
		 * on a different plane. Do not mistake that cavern for the Bloodveld room or
		 * send the player back outside. Route to the entrance, wait while the player
		 * crosses the Strength/Agility obstacle, then snap to an exact loaded
		 * Bloodveld in the main dungeon.
		 */
		putAccessProfile(profiles, "Bloodvelds", "Wilderness God Wars Dungeon",
			"Bloodveld");

		// Reviewed locations whose exact room tile is not yet verified. These
		// route to the exact entrance and continue only to an exact NPC alias.
		/*
		 * Regular Cave kraken do not share the boss-room checkpoint. Route to
		 * Kraken Cove's surface icon first, then use an exact loaded regular
		 * Cave kraken/whirlpool in the cove. This avoids accidentally sending a
		 * regular task into the Kraken boss room.
		 */
		putAccessProfileAt(profiles, "Cave kraken", "Kraken Cove",
			2277, 3611, 0, "Cave kraken", "Whirlpool");
		putStagedInteriorFromAccess(profiles, "Dagannoths", "Lighthouse Dungeon", 2523, 10023, 0,
			"Dagannoth");
		putAccessProfile(profiles, "Dagannoths", "Waterbirth Island Dungeon",
			"Dagannoth");
		putStagedInteriorFromAccess(profiles, "Kalphites", "Kalphite Slayer Cave",
			3305, 9497, 0, "Kalphite worker", "Kalphite soldier", "Kalphite guardian");
		putStagedInteriorFromAccess(profiles, "Cave bugs", "Dorgesh-Kaan South Dungeon",
			2735, 5221, 0, "Cave bug");
		putStagedInteriorFromAccess(profiles, "Cave slimes", "Dorgesh-Kaan South Dungeon",
			2735, 5221, 0, "Cave slime");
		putStagedInteriorFromAccess(profiles, "Molanisks", "Dorgesh-Kaan South Dungeon",
			2698, 5222, 0, "Molanisk");
		putTraversableInterior(profiles, "Minotaurs", "Stronghold of Security",
			1880, 5205, 0, "Minotaur");
		putTraversableInterior(profiles, "Rats", "Varrock Sewers",
			3239, 9865, 0, "Giant rat", "Rat");
		putTraversableInterior(profiles, "Dwarves", "White Wolf Tunnel pub",
			2876, 9872, 0, "Dwarf");
		putStagedInterior(profiles, "Skeletons", "Digsite Dungeon",
			3369, 3426, 0, 3368, 9828, 0,
			"Use a rope on the north-west Digsite winch, then operate the winch; SlayerPlus will continue to the nearby level-22 Skeletons.",
			"", "Skeleton");
		putOpenWorld(profiles, "Suqahs", "Lunar Isle", 2112, 3915, 0, "Suqah");
		putOpenWorld(profiles, "Trolls", "Death Plateau", 2847, 3635, 0,
			"Mountain troll", "Troll general", "Thrower troll");
		putAccessProfile(profiles, "Ankou", "Stronghold of Security", "Ankou");
		putStagedInteriorFromAccess(profiles, "Blue dragons", "Taverley Dungeon",
			2892, 9799, 0, "Blue dragon", "Baby blue dragon");
		putStagedInteriorFromAccess(profiles, "Chaos druids", "Edgeville Dungeon",
			3110, 9933, 0, "Chaos druid");
		putStagedInteriorFromAccess(profiles, "Earth warriors", "Edgeville Dungeon",
			3120, 9970, 0, "Earth warrior");
		putStagedInteriorFromAccess(profiles, "Hill giants", "Edgeville Dungeon",
			3105, 9839, 0, "Hill giant");
		putStagedInteriorFromAccess(profiles, "Hobgoblins", "Edgeville Dungeon",
			3127, 9873, 0, "Hobgoblin");
		putStagedInteriorFromAccess(profiles, "Moss giants", "Brimhaven Dungeon",
			2647, 9497, 0, "Moss giant");
		putStagedInteriorFromAccess(profiles, "Red dragons", "Brimhaven Dungeon",
			2699, 9509, 0, "Red dragon", "Baby red dragon");
		/*
		 * A Black dragon assignment has a dedicated task-only upper floor. Shortest
		 * Path already models both required transports: the surface ladder into
		 * Taverley Dungeon and the black-dragon steps at 2906,9813,0 which land at
		 * 2903,9813,1. Keep this as one transport-chain route so the pathfinder can
		 * choose every ordinary door/Agility edge it can legally use, rather than
		 * falling back to the distant main-floor dragons or the blue-dragon stairs.
		 */
		putTraversableInterior(
			profiles,
			"Black dragons",
			"Taverley Dungeon",
			2903, 9813, 1,
			"Black dragon", "Baby black dragon"
		);
		putAccessProfile(profiles, "Spiritual creatures", "God Wars Dungeon",
			"Spiritual ranger", "Spiritual mage", "Spiritual warrior");
		putOpenWorld(profiles, "Vampyres", "Darkmeyer", 3604, 3364, 0,
			"Vyrewatch", "Vyrewatch sentinel");
		putOpenWorld(profiles, "Elves", "Lletya", 2355, 3172, 0,
			"Elf warrior", "Elf archer");
		/*
		 * Mourner Tunnels is a direct-transport route, not a surface-entrance route.
		 * Shortest Path has an explicit Slayer ring -> Dark Beasts transport landing
		 * at 2028,4636. Route to the verified Dark beast cannon tile at 1992,4655 so the
		 * pathfinder can take that transport and walk the final few tiles. Treating
		 * the teleport landing as a dungeon entrance caused the staged entrance logic
		 * to wait for an entrance object that does not exist there.
		 */
		putTraversableInterior(
			profiles,
			"Dark beasts",
			"Mourner Tunnels",
			1992, 4655, 0,
			"Dark beast", "Night beast"
		);
		putAccessProfile(profiles, "Mutated zygomites", "Zanaris", "Mutated zygomite");
		putStagedInteriorFromAccess(profiles, "Brine rats", "Brine Rat Cavern",
			2772, 10155, 0, "Brine rat");
		putAccessProfile(profiles, "Adamant dragons", "Lithkren Vault", "Adamant dragon");
		putAccessProfile(profiles, "Rune dragons", "Lithkren Vault", "Rune dragon");
		/*
		 * Ynysdail is separated from Gwenith by ocean. Route to the mainland
		 * rowboat first; runtime supplies the island cavern-entrance checkpoint
		 * after the player manually rows across, then this interior target takes
		 * over after entering the cavern.
		 */
		putStagedInterior(
			profiles,
			"Aquanites",
			"Ynysdail Cavern",
			2218, 3424, 0,
			2275, 9880, 0,
			"Use the Gwenith rowboat only if it has been built. Otherwise SlayerPlus routes to Port Roberts so you can sail your player-owned boat to Ynysdail; on the island it continues to the cavern.",
			"",
			"Aquanite", "Elder aquanite"
		);

		/*
		 * Exact identity/access profiles for encounters whose authored
		 * SlayerRoutePlan owns every interior transition and the terminal proof.
		 * Keep these as access-only profiles: copying a plan checkpoint here would
		 * create a second, broader arrival contract and could allow the legacy
		 * profile path to stop before the declarative plan has proved arrival.
		 */
		putAccessProfile(profiles, "Catablepon", "Stronghold of Security",
			"Catablepon");
		putAccessProfile(profiles, "Fleshcrawlers", "Stronghold of Security",
			"Flesh crawler");
		putAccessProfile(profiles, "Metal dragons", "Ancient Cavern",
			"Mithril dragon");
		putAccessProfile(profiles, "Aviansies", "God Wars Dungeon",
			"Aviansie");
		putAccessProfile(profiles, "Araxytes", "Morytania Spider Cave",
			"Araxyte");
		putStagedInterior(profiles, "Frost dragons", "Grimstone Dungeon",
			2912, 4066, 0, 2901, 10463, 0,
			"Enter Grimstone Dungeon; SlayerPlus will rebase from the reviewed cave landing and continue to the main Frost dragon pack.",
			"", "Frost dragon");
		putAccessProfile(profiles, "Scabarites", "Sophanem Dungeon",
			"Scarab mage", "Locust rider");
		putAccessProfile(profiles, "Shadow warriors", "Legends' Guild basement",
			"Shadow warrior");
		putAccessProfile(profiles, "Warped creatures", "Poison Waste Dungeon",
			"Warped terrorbird", "Warped tortoise");
		putAccessProfileAt(profiles, "Lesser nagua", "Ruins of Tapoyauik",
			1693, 3230, 0, "Frost nagua");
		putAccessProfile(profiles, "Killerwatts", "Killerwatt plane",
			"Killerwatt");
		putAccessProfile(profiles, "Otherworldly beings", "Zanaris",
			"Otherworldly being");
		putAccessProfile(profiles, "Custodian Stalkers", "Stalker Den",
			"Juvenile custodian stalker", "Mature custodian stalker",
			"Elder custodian stalker");

		/*
		 * Release-manifest routes with reviewed exact terminals. These declarations
		 * are intentionally authored here rather than generated from the independent
		 * manifest, so catalog/manifest drift remains detectable by the release gate.
		 */
		putStagedInterior(profiles, "Ice giants", "Asgarnian Ice Dungeon",
			3009, 3150, 0, 3055, 9568, 0,
			"Enter the Asgarnian Ice Dungeon; SlayerPlus will continue to the reviewed Ice giant pack.",
			"", "Ice giant");
		putStagedInterior(profiles, "Fever spiders", "Braindeath Island",
			3680, 3536, 0, 2150, 5099, 0,
			"Complete the Braindeath Island travel handoff; SlayerPlus will continue to the Fever spider room after the island layer loads.",
			"", "Fever spider");
		putStagedInterior(profiles, "Wall beasts", "Lumbridge Swamp Caves",
			3169, 3173, 0, 3200, 9560, 0,
			"Enter Lumbridge Swamp Caves; SlayerPlus will continue to the reviewed Wall beast corridor.",
			"", "Wall beast");
		putStagedInterior(profiles, "Dwarves", "Dwarven Mine",
			3018, 3450, 0, 3020, 9820, 0,
			"Enter the Dwarven Mine; SlayerPlus will continue to the reviewed Dwarf area.",
			"", "Dwarf");
		putStagedInteriorFromAccess(profiles, "TzHaar", "Karamja Volcano",
			2444, 5138, 0, "TzHaar");

		putOpenWorld(profiles, "Bats", "Lumbridge area", 3188, 3220, 0, "Bat");
		putOpenWorld(profiles, "Birds", "Lumbridge area", 3188, 3220, 0, "Bird", "Chicken");
		putOpenWorld(profiles, "Cows", "Lumbridge area", 3188, 3220, 0, "Cow");
		putOpenWorld(profiles, "Crabs", "Lumbridge area", 3188, 3220, 0, "Crab");
		putOpenWorld(profiles, "Goblins", "Lumbridge area", 3188, 3220, 0, "Goblin");
		putOpenWorld(profiles, "Rats", "Lumbridge area", 3188, 3220, 0, "Rat", "Giant rat");
		putOpenWorld(profiles, "Spiders", "Lumbridge area", 3188, 3220, 0, "Spider", "Giant spider");
		putOpenWorld(profiles, "Bears", "Ardougne area", 2570, 3300, 0, "Bear");
		putOpenWorld(profiles, "Bandits", "Bandit Camp", 3171, 2979, 0, "Bandit");
		putOpenWorld(profiles, "Black Knights", "Black Knights' Fortress", 3015, 3514, 0,
			"Black Knight");
		putOpenWorld(profiles, "Venators", "Darkmeyer", 3604, 3364, 0, "Venator");
		putOpenWorld(profiles, "Lizards", "Desert quarry", 3172, 2912, 0, "Lizard");
		putOpenWorld(profiles, "Jungle horrors", "Feldip Hills", 2515, 2954, 0,
			"Jungle horror");
		putOpenWorld(profiles, "Wolves", "Feldip Hills", 2515, 2954, 0, "Wolf");
		putOpenWorld(profiles, "Ghouls", "Haunted Woods", 3584, 3492, 0, "Ghoul");
		putOpenWorld(profiles, "Werewolves", "Haunted Woods", 3584, 3492, 0, "Werewolf");
		putOpenWorld(profiles, "Icefiends", "Ice Mountain", 3010, 3486, 0, "Icefiend");
		putOpenWorld(profiles, "Ice warriors", "Ice Mountain", 3010, 3486, 0, "Ice warrior");
		putOpenWorld(profiles, "Dogs", "Isle of Souls", 2210, 2900, 0, "Dog");
		putOpenWorld(profiles, "Sourhogs", "Isle of Souls", 2210, 2900, 0, "Sourhog");
		putOpenWorld(profiles, "Harpie bug swarms", "Karamja", 2843, 3070, 0,
			"Harpie Bug Swarm");
		putOpenWorld(profiles, "Lesser demons", "Karamja", 2843, 3070, 0, "Lesser demon");
		putOpenWorld(profiles, "Monkeys", "Karamja", 2843, 3070, 0, "Monkey");
		putOpenWorld(profiles, "Gryphons", "Kebos Lowlands", 1248, 3726, 0, "Gryphon");
		putOpenWorld(profiles, "Lizardmen", "Kourend Woodland", 1540, 3464, 0, "Lizardman");
		putOpenWorld(profiles, "Ogres", "Kourend Woodland", 1540, 3464, 0, "Ogre");
		putOpenWorld(profiles, "Mogres", "Mogre beach", 2996, 3106, 0, "Mogre");
		putOpenWorld(profiles, "Shades", "Mort'ton", 3488, 3288, 0, "Shade");
		putOpenWorld(profiles, "Crocodiles", "Nardah desert", 3427, 2911, 0, "Crocodile");
		putOpenWorld(profiles, "Terror dogs", "Piscatoris area", 2332, 3683, 0, "Terror dog");
		putOpenWorld(profiles, "Sea snakes", "Waterbirth Island", 2525, 3743, 0, "Sea snake");
		putOpenWorld(profiles, "Green dragons", "Wilderness green dragon area", 3331, 3672, 0,
			"Green dragon");
		putOpenWorld(profiles, "Scorpions", "Al Kharid mine", 3298, 3293, 0, "Scorpion");

		/*
		 * Wilderness assignment targets reviewed against current OSRS Wiki LocLine
		 * coordinates and independently exercised against the pinned Shortest Path
		 * graph. Magic axes and Revenants use per-task approaches below because the
		 * shared Wilderness access key cannot represent either manual transition.
		 */
		putOpenWorld(profiles, "Dark warriors", "Wilderness", 3029, 3632, 0,
			"Dark warrior");
		putOpenWorld(profiles, "Ents", "Wilderness", 3202, 3669, 0, "Ent");
		putOpenWorld(profiles, "Lava dragons", "Wilderness", 3201, 3814, 0,
			"Lava dragon");
		putOpenWorld(profiles, "Mammoths", "Wilderness", 3169, 3594, 0, "Mammoth");
		putOpenWorld(profiles, "Pirates", "Wilderness", 3240, 3601, 0,
			"Zombie pirate", "Pirate");
		putOpenWorld(profiles, "Rogues", "Wilderness", 3285, 3933, 0, "Rogue");
		putAccessProfileAt(profiles, "Magic axes", "Wilderness",
			3190, 3957, 0, "Magic axe");
		putAccessProfileAt(profiles, "Revenants", "Wilderness",
			3073, 3654, 0,
			"Revenant imp", "Revenant goblin", "Revenant icefiend",
			"Revenant pyrefiend", "Revenant hobgoblin", "Revenant vampire",
			"Revenant werewolf", "Revenant cyclops", "Revenant hellhound",
			"Revenant demon", "Revenant ork", "Revenant dark beast",
			"Revenant knight", "Revenant dragon", "Revenant maledictus");

		validateCatacombsProfiles(profiles);
		return Collections.unmodifiableMap(profiles);
	}

	private static void validateCatacombsProfiles(
		final Map<String, RouteProfile> profiles)
	{
		final WorldPoint requiredEntrance = ACCESS_ROUTES.get(
			normalize("Catacombs of Kourend")
		);
		if (requiredEntrance == null)
		{
			throw new IllegalStateException(
				"Catacombs of Kourend is missing its reviewed surface entrance"
			);
		}

		for (final RouteProfile profile : profiles.values())
		{
			if (profile == null
				|| !normalize(profile.location).equals(normalize("Catacombs of Kourend")))
			{
				continue;
			}

			if (!profile.isStaged()
				|| profile.surfaceAccess == null
				|| !profile.surfaceAccess.equals(requiredEntrance)
				|| profile.primaryDestination == null
				|| sameCoordinateLayer(profile.surfaceAccess, profile.primaryDestination))
			{
				throw new IllegalStateException(
					"Catacombs route must be surface -> King Rada entrance -> interior: "
						+ profile.taskName + " @ " + profile.location
				);
			}
		}
	}

	private static Map<String, RouteProfile> createBossProfiles()
	{
		final Map<String, RouteProfile> profiles = new LinkedHashMap<>();

		/*
		 * Fairy ring DIP lands directly inside the Abyssal Nexus at 3037,4763.
		 * There is no second surface cave transition.  Once the Nexus loads, the
		 * closest exact Sire NPC becomes authoritative; this reviewed centre anchor
		 * merely keeps Shortest Path on the loaded coordinate layer beforehand.
		 */
		putBossInterior(
			profiles,
			"Abyssal Sire",
			"Abyssal Nexus",
			3037, 4763, 0,
			"Abyssal Sire"
		);
		putBossInterior(
			profiles,
			"The Abyssal Sire",
			"Abyssal Nexus",
			3037, 4763, 0,
			"Abyssal Sire"
		);

		/*
		 * Tormented demons begin with a player-triggered item transition that the
		 * current Shortest Path transport data does not model. Skotizo instead uses
		 * a normal route to King Rada's statue followed by a manual Dark-totem altar
		 * interaction. After each destination layer loads, Shortest Path owns the
		 * collision-safe local walk to the exact demon/boss.
		 */
		putBossInterior(
			profiles,
			"Tormented demons",
			"Ancient Guthixian Temple",
			4097, 4419, 0,
			"Tormented demon"
		);
		putBossStagedInteriorWide(
			profiles,
			"Skotizo",
			"Skotizo's Lair",
			1639, 3673, 0,
			1693, 9886, 0,
			"Use a Dark totem on the altar in the centre of the Catacombs; SlayerPlus will continue locally after the lair loads.",
			"",
			"Skotizo"
		);

		/* Jad does not exist in the loaded scene until wave 63. The route therefore
		 * ends at the reviewed Fight Caves entrance instead of waiting indefinitely
		 * for an exact boss NPC after the player enters the minigame. */
		final WorldPoint fightCaveEntrance = new WorldPoint(2439, 5172, 0);
		putProfile(profiles, new RouteProfile(
			"TzTok-Jad",
			"TzHaar Fight Cave",
			fightCaveEntrance,
			fightCaveEntrance,
			"",
			"",
			RouteMode.BOSS_VERIFIED_INTERIOR,
			npcAliases("TzTok-Jad", "TzTok-Jad"),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));

		/*
		 * Vorkath is a real two-region journey. Shortest Path's Rellekka-to-Ungael
		 * transport starts at 2640,3696 and lands at 2277,4034. Route first to that
		 * exact Torfinn/boat departure tile; after the player travels to Ungael,
		 * the coordinate-layer change unlocks Quest Helper's reviewed Vorkath
		 * anchor at 2273,4065.
		 */
		putBossStagedInteriorWide(
			profiles,
			"Vorkath",
			"Ungael",
			2640, 3696, 0,
			2273, 4065, 0,
			"Talk to Torfinn on Rellekka's third dock to travel to Ungael; SlayerPlus will continue to Vorkath after landing.",
			"",
			"Vorkath"
		);
		/*
		 * The Whisperer has two manual region transitions. Quest Helper's reviewed
		 * route enters Camdozaal at 3000,3494, descends the Lassar sinkhole at
		 * 2922,5827, then approaches the Cathedral/Odd Figure at 2656,6370.
		 * SlayerPlusPlugin supplies the middle checkpoint while Camdozaal is loaded;
		 * this profile owns the surface and final legs (including a direct Ring of
		 * shadows landing inside Lassar Undercity).
		 */
		putBossStagedInteriorWide(
			profiles,
			"Whisperer",
			"Lassar Undercity",
			3000, 3494, 0,
			2656, 6370, 0,
			"Enter Camdozaal west of Ice Mountain; inside, follow SlayerPlus to the sinkhole, descend, then continue to the Cathedral.",
			"",
			"The Whisperer", "Whisperer", "Odd Figure"
		);
		putBossStagedInteriorWide(
			profiles,
			"The Whisperer",
			"Lassar Undercity",
			3000, 3494, 0,
			2656, 6370, 0,
			"Enter Camdozaal west of Ice Mountain; inside, follow SlayerPlus to the sinkhole, descend, then continue to the Cathedral.",
			"",
			"The Whisperer", "Whisperer", "Odd Figure"
		);
		/*
		 * Both rope descents are explicit Shortest Path transports. Route to the
		 * exact surface burrow first, then through the upper lair to the reviewed
		 * lower-chamber entrance instead of stopping at a generic desert tile.
		 */
		putBossStagedInteriorWide(
			profiles,
			"Kalphite Queen",
			"Kalphite Lair",
			3228, 3109, 0,
			3508, 9493, 0,
			"Use the two rope descents unless they are permanently unlocked; SlayerPlus will route through both lair levels to the Queen chamber.",
			"",
			"Kalphite Queen"
		);
		putBossStagedInteriorWide(
			profiles,
			"The Kalphite Queen",
			"Kalphite Lair",
			3228, 3109, 0,
			3508, 9493, 0,
			"Use the two rope descents unless they are permanently unlocked; SlayerPlus will route through both lair levels to the Queen chamber.",
			"",
			"Kalphite Queen"
		);

		putBossStagedInteriorWide(
			profiles,
			"Cerberus",
			"Cerberus' Lair",
			2885, 3397, 0,
			1310, 1253, 0,
			"Enter Taverley Dungeon and continue to the Key Master/Cerberus route.",
			"",
			"Cerberus"
		);
		putBossStagedInteriorWide(
			profiles,
			"Thermonuclear smoke devil",
			"Smoke Devil Dungeon",
			2411, 3058, 0,
			2362, 9456, 0,
			"Enter the Smoke Devil Dungeon and use the boss-room crevice; SlayerPlus will continue only to Thermonuclear smoke devil.",
			"",
			"Thermonuclear smoke devil"
		);
		putBossStagedInteriorWide(
			profiles,
			"Alchemical Hydra",
			"Karuulm Slayer Dungeon",
			1311, 3783, 0,
			1368, 10270, 0,
			"Enter Karuulm Slayer Dungeon and continue to the lower-level Alchemical Hydra lair.",
			"",
			"Alchemical Hydra"
		);
		putBossStagedInteriorWide(
			profiles,
			"Araxxor",
			"Morytania Spider Cave",
			3657, 3407, 0,
			3632, 9815, 0,
			"Enter Morytania Spider Cave and continue through the web tunnel to Araxxor's lair.",
			"",
			"Araxxor"
		);
		/*
		 * The completed Heart of Darkness shortcut is a direct lift from the
		 * Tower of Ascension to the lower Ruins. Keep the underground boss tile
		 * as the second stage rather than presenting it to Shortest Path outside.
		 */
		putBossStagedInteriorWide(
			profiles,
			"Amoxliatl",
			"Ruins of Tapoyauik",
			1641, 3221, 0,
			1362, 4511, 0,
			"Use the Lift Platform in the Tower of Ascension; SlayerPlus will continue west to Amoxliatl after the lower Ruins load.",
			"",
			"Amoxliatl"
		);
		/*
		 * Kraken is a two-step dungeon encounter. After the surface Kraken Cove
		 * entrance, Shortest Path must stop on the exact boss-room entrance tile
		 * at 2280,10016,0. Routing toward the boss-room centre (the old 10034
		 * anchor) can send the player into the rock wall to the west/left of the
		 * actual entrance. Once the room is entered and the exact Kraken loads,
		 * SlayerPlus' normal exact-NPC routing takes over.
		 */
		putBossStagedInteractionCheckpoint(profiles, "Kraken", "Kraken Cove",
			2280, 10016, 0, "Kraken");
		putBossStagedInteractionCheckpoint(profiles, "The Cave Kraken Boss", "Kraken Cove",
			2280, 10016, 0, "Kraken");

		/*
		 * Independently reviewed release-manifest boss terminals. Instance and
		 * dungeon rows retain their explicit outer handoff; open-world bosses never
		 * borrow a generic cave entrance or ordinary-task profile.
		 */
		putBossOpenWorld(profiles, "Deranged Archaeologist", "Fossil Island",
			3683, 3706, 0, "Deranged archaeologist");
		putBossStagedInteriorWide(profiles, "Scorpia", "Scorpia's Cave",
			3232, 3950, 0, 3232, 10346, 0,
			"Enter Scorpia's Cave; SlayerPlus will continue to Scorpia after the cave layer loads.",
			"", "Scorpia");
		putBossStagedInteriorWide(profiles, "Scurrius", "Varrock Sewers",
			3237, 3458, 0, 3299, 9867, 0,
			"Enter Varrock Sewers and use the Scurrius lair entrance; SlayerPlus stops routing when the boss region loads.",
			"", "Scurrius");
		putBossStagedInteriorWide(profiles, "Bryophyta", "Varrock Sewers",
			3237, 3458, 0, 3220, 9933, 0,
			"Enter Varrock Sewers and use the mossy-key boss door; SlayerPlus stops routing when Bryophyta's region loads.",
			"", "Bryophyta");
		putBossTraversableInterior(profiles, "Brutus", "Lumbridge cow field",
			3263, 3297, 0, "Brutus");
		putBossOpenWorld(profiles, "Chaos Elemental", "Wilderness",
			3261, 3927, 0, "Chaos Elemental");
		putBossOpenWorld(profiles, "Chaos Fanatic", "Wilderness",
			2979, 3846, 0, "Chaos Fanatic");
		putBossOpenWorld(profiles, "Crazy Archaeologist", "Wilderness",
			2977, 3702, 0, "Crazy archaeologist");
		putBossInterior(profiles, "TzKal-Zuk", "Inferno",
			2496, 5115, 0, "TzKal-Zuk");
		putBossStagedInteriorWide(profiles, "Spindel", "Web Chasm",
			3294, 3749, 0, 3406, 10145, 0,
			"Enter the Web Chasm; SlayerPlus will continue to the reviewed Spindel chamber after the cave layer loads.",
			"", "Spindel");
		putBossStagedInteriorWide(profiles, "Calvar'ion", "Skeletal Tomb",
			3152, 3644, 0, 3164, 10043, 0,
			"Enter the Skeletal Tomb; SlayerPlus will continue to the reviewed Calvar'ion chamber after the cave layer loads.",
			"", "Calvar'ion");
		putBossStagedInteriorWide(profiles, "Royal Titans", "Asgarnian Ice Dungeon",
			3009, 3150, 0, 2953, 9569, 0,
			"Enter the Asgarnian Ice Dungeon and use the Royal Titans encounter entrance; SlayerPlus stops routing when the encounter region loads.",
			"", "Brandr", "Eldric", "Royal Titan");

		/*
		 * Boss identities below use exact declarative route plans for their door,
		 * traversal, instance, or terminal evidence. The legacy profile therefore
		 * supplies only the independently reviewed outer access and exact NPC alias
		 * set; it must not invent an interior terminal beside the plan.
		 */
		putBossAccessProfile(profiles, "Artio", "Hunter's End", "Artio");
		putBossAccessProfile(profiles, "Callisto", "Callisto's Den", "Callisto");
		putBossAccessProfile(profiles, "Dagannoth Kings",
			"Waterbirth Island Dungeon", "Dagannoth Rex", "Dagannoth Prime",
			"Dagannoth Supreme");
		putBossAccessProfile(profiles, "Grotesque Guardians", "Slayer Tower",
			"Dusk", "Dawn");
		putBossAccessProfile(profiles, "K'ril Tsutsaroth", "God Wars Dungeon",
			"K'ril Tsutsaroth", "Balfrug Kreeyath", "Tstanon Karlak",
			"Zakl'n Gritch");
		putBossAccessProfile(profiles, "King Black Dragon",
			"King Black Dragon Lair", "King Black Dragon");
		putBossAccessProfile(profiles, "Kree'arra", "God Wars Dungeon",
			"Kree'arra", "Wingman Skree", "Flockleader Geerin", "Flight Kilisa");
		putBossAccessProfile(profiles, "Sarachnis", "Forthos Dungeon",
			"Sarachnis");
		putBossAccessProfile(profiles, "Venenatis", "Silk Chasm", "Venenatis");
		putBossAccessProfile(profiles, "Vet'ion", "Vet'ion's Rest",
			"Vet'ion", "Vet'ion Reborn");
		putBossAccessProfile(profiles, "Obor", "Edgeville Dungeon", "Obor");
		putBossAccessProfile(profiles, "Demonic gorillas", "Crash Site Cavern",
			"Demonic gorilla");
		putBossAccessProfile(profiles, "Shellbane Gryphon", "The Great Conch",
			"Shellbane Gryphon");
		putBossAccessProfile(profiles, "Barrows Brothers", "Barrows",
			"Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
			"Karil the Tainted", "Torag the Corrupted", "Verac the Defiled");
		putBossAccessProfile(profiles, "Duke Sucellus", "Ghorrock Dungeon",
			"Duke Sucellus");
		putBossAccessProfile(profiles, "General Graardor", "God Wars Dungeon",
			"General Graardor", "Sergeant Strongstack", "Sergeant Steelwill",
			"Sergeant Grimspike");
		putBossAccessProfile(profiles, "Giant Mole", "Falador Mole Lair",
			"Giant Mole");
		putBossAccessProfile(profiles, "Maggot King", "Maggot King's lair",
			"Maggot King");
		putBossAccessProfile(profiles, "Phantom Muspah", "Ghorrock Dungeon",
			"Phantom Muspah");
		putBossAccessProfile(profiles, "Leviathan", "The Scar",
			"The Leviathan", "Leviathan");
		putBossAccessProfile(profiles, "Vardorvis", "Stranglewood Temple",
			"Vardorvis");
		putBossAccessProfile(profiles, "Commander Zilyana", "God Wars Dungeon",
			"Commander Zilyana", "Starlight", "Growler", "Bree");
		putBossAccessProfile(profiles, "Zulrah", "Zul-Andra", "Zulrah");

		return Collections.unmodifiableMap(profiles);
	}

	private static void putStagedInteriorFromAccess(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String... npcNames)
	{
		putStagedInteriorFromAccessWithPositioning(
			profiles, taskName, location, x, y, plane, "", npcNames
		);
	}

	private static void putStagedInteriorFromAccessWithPositioning(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String positioningNote,
		final String... npcNames)
	{
		final WorldPoint access = ACCESS_ROUTES.get(normalize(location));
		if (access == null)
		{
			throw new IllegalStateException(
				"Explicit staged route is missing an access coordinate: "
					+ taskName + " @ " + location
			);
		}
		final WorldPoint destination = new WorldPoint(x, y, plane);
		if (sameCoordinateLayer(access, destination))
		{
			throw new IllegalStateException(
				"Staged route access and interior are on the same coordinate layer: "
					+ taskName + " @ " + location
			);
		}
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			destination,
			access,
			"Use the reviewed " + location
				+ " entrance; SlayerPlus will continue only after the transition is detected.",
			positioningNote,
			RouteMode.STAGED_INTERIOR,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	private static void putInterior(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String... npcNames)
	{
		final WorldPoint destination = new WorldPoint(x, y, plane);
		final WorldPoint access = ACCESS_ROUTES.get(normalize(location));
		if (access != null && prefersWorldMapEntranceIcon(location)
			&& !sameCoordinateLayer(access, destination))
		{
			throw new IllegalStateException(
				"Cross-layer reviewed interior must be authored as an explicit staged route: "
					+ taskName + " @ " + location
			);
		}
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			destination,
			RouteMode.VERIFIED_INTERIOR,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	/** Exact open-world task target; no entrance or transport handoff is implied. */
	private static void putOpenWorld(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String... npcNames)
	{
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(x, y, plane),
			RouteMode.OPEN_WORLD,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	/**
	 * A reviewed endpoint whose complete staircase/door chain is already present
	 * in Shortest Path's transport graph. This is intentionally distinct from a
	 * one-transition staged dungeon: stopping after the first transition would
	 * send the player backwards before the remaining transport could be used.
	 */
	private static void putTraversableInterior(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String... npcNames)
	{
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(x, y, plane),
			RouteMode.TRANSPORT_CHAIN_INTERIOR,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	private static void putInteriorWithPositioning(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String positioningNote,
		final String... npcNames)
	{
		final WorldPoint destination = new WorldPoint(x, y, plane);
		final WorldPoint access = ACCESS_ROUTES.get(normalize(location));
		if (access != null && prefersWorldMapEntranceIcon(location)
			&& !sameCoordinateLayer(access, destination))
		{
			throw new IllegalStateException(
				"Cross-layer positioned interior must be authored as an explicit staged route: "
					+ taskName + " @ " + location
			);
		}
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			destination,
			null,
			"",
			positioningNote,
			RouteMode.VERIFIED_INTERIOR,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	private static void putStagedInterior(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int accessX,
		final int accessY,
		final int accessPlane,
		final int destinationX,
		final int destinationY,
		final int destinationPlane,
		final String transitionInstruction,
		final String positioningNote,
		final String... npcNames)
	{
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(destinationX, destinationY, destinationPlane),
			new WorldPoint(accessX, accessY, accessPlane),
			transitionInstruction,
			positioningNote,
			RouteMode.STAGED_INTERIOR,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	private static void putStagedInteriorWide(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int accessX,
		final int accessY,
		final int accessPlane,
		final int destinationX,
		final int destinationY,
		final int destinationPlane,
		final String transitionInstruction,
		final String positioningNote,
		final String... npcNames)
	{
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(destinationX, destinationY, destinationPlane),
			new WorldPoint(accessX, accessY, accessPlane),
			transitionInstruction,
			positioningNote,
			RouteMode.STAGED_INTERIOR,
			npcAliases(taskName, npcNames),
			8,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	private static void putAccessProfile(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final String... npcNames)
	{
		final WorldPoint access = ACCESS_ROUTES.get(normalize(location));
		if (access == null)
		{
			throw new IllegalStateException("Missing access route for " + location);
		}
		putProfile(profiles, accessProfile(
			taskName,
			location,
			access,
			npcAliases(taskName, npcNames)
		));
	}

	private static void putAccessProfileAt(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int accessX,
		final int accessY,
		final int accessPlane,
		final String... npcNames)
	{
		putProfile(profiles, accessProfile(
			taskName,
			location,
			new WorldPoint(accessX, accessY, accessPlane),
			npcAliases(taskName, npcNames)
		));
	}

	private static RouteProfile accessProfile(
		final String taskName,
		final String location,
		final WorldPoint access,
		final Set<String> aliases)
	{
		return new RouteProfile(
			taskName,
			location,
			access,
			access,
			"Use the reviewed " + location
				+ " transition; SlayerPlus will continue only to an exact loaded task NPC.",
			"",
			RouteMode.ACCESS_THEN_NPC,
			aliases,
			ACCESS_TARGET_RADIUS,
			ACCESS_ARRIVAL_RADIUS
		);
	}

	private static RouteProfile bossAccessProfile(
		final String taskName,
		final String location,
		final WorldPoint access,
		final Set<String> aliases)
	{
		return new RouteProfile(
			taskName,
			location,
			access,
			access,
			"Use the reviewed " + location
				+ " access route; SlayerPlus will continue only to the exact loaded boss.",
			"",
			RouteMode.BOSS_ACCESS_THEN_NPC,
			aliases,
			ACCESS_TARGET_RADIUS,
			ACCESS_ARRIVAL_RADIUS
		);
	}

	private static void putBossAccessProfile(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final String... npcNames)
	{
		final WorldPoint access = ACCESS_ROUTES.get(normalize(location));
		if (access == null)
		{
			throw new IllegalStateException(
				"Missing boss access route for " + taskName + " @ " + location
			);
		}
		putProfile(profiles, bossAccessProfile(
			taskName,
			location,
			access,
			npcAliases(taskName, npcNames)
		));
	}

	private static RouteProfile npcOnlyProfile(
		final String taskName,
		final String location,
		final Set<String> aliases,
		final boolean boss)
	{
		return new RouteProfile(
			taskName,
			location,
			null,
			boss ? RouteMode.BOSS_NPC_ONLY : RouteMode.NPC_ONLY,
			aliases,
			0,
			INTERIOR_ARRIVAL_RADIUS
		);
	}

	private static void putBossAccess(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String... npcNames)
	{
		final WorldPoint access = new WorldPoint(x, y, plane);
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			access,
			access,
			"Use the reviewed boss access transition; SlayerPlus will continue only to the exact boss.",
			"",
			RouteMode.BOSS_ACCESS_THEN_NPC,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	private static void putBossStagedInterior(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int accessX,
		final int accessY,
		final int accessPlane,
		final int destinationX,
		final int destinationY,
		final int destinationPlane,
		final String transitionInstruction,
		final String positioningNote,
		final String... npcNames)
	{
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(destinationX, destinationY, destinationPlane),
			new WorldPoint(accessX, accessY, accessPlane),
			transitionInstruction,
			positioningNote,
			RouteMode.BOSS_STAGED_INTERIOR,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	/**
	 * Exact interaction checkpoint used for an authored boss-room door, crevice,
	 * whirlpool, tunnel, or other transition tile. Unlike a boss-room area
	 * anchor, this must remain a singleton Shortest Path target so the route ends
	 * at the interaction point rather than somewhere merely near the room.
	 *
	 * <p>The outer dungeon/cave entrance is authored explicitly in the same route
	 * profile. Runtime routing must complete that transition before this exact
	 * checkpoint becomes authoritative; once the boss NPC loads, exact-NPC routing
	 * takes over.</p>
	 */
	private static void putBossStagedInteractionCheckpoint(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String... npcNames)
	{
		final WorldPoint access = ACCESS_ROUTES.get(normalize(location));
		if (access == null)
		{
			throw new IllegalStateException("Missing outer access route for boss checkpoint: " + location);
		}
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(x, y, plane),
			access,
			"Enter " + location
				+ "; SlayerPlus will then route to the exact reviewed boss-room interaction checkpoint.",
			"",
			RouteMode.BOSS_STAGED_INTERIOR,
			npcAliases(taskName, npcNames),
			0,
			2
		));
	}

	private static void putBossStagedInteriorWide(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int accessX,
		final int accessY,
		final int accessPlane,
		final int destinationX,
		final int destinationY,
		final int destinationPlane,
		final String transitionInstruction,
		final String positioningNote,
		final String... npcNames)
	{
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(destinationX, destinationY, destinationPlane),
			new WorldPoint(accessX, accessY, accessPlane),
			transitionInstruction,
			positioningNote,
			RouteMode.BOSS_STAGED_INTERIOR,
			npcAliases(taskName, npcNames),
			8,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	private static void putBossInterior(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String... npcNames)
	{
		/* A direct boss-room coordinate still uses the strict boss map. */
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(x, y, plane),
			RouteMode.BOSS_VERIFIED_INTERIOR,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	/** Exact open-world boss target; no cave or instance handoff is implied. */
	private static void putBossOpenWorld(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String... npcNames)
	{
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(x, y, plane),
			RouteMode.BOSS_OPEN_WORLD,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	/** Boss target whose complete transport chain is already in Shortest Path. */
	private static void putBossTraversableInterior(
		final Map<String, RouteProfile> profiles,
		final String taskName,
		final String location,
		final int x,
		final int y,
		final int plane,
		final String... npcNames)
	{
		putProfile(profiles, new RouteProfile(
			taskName,
			location,
			new WorldPoint(x, y, plane),
			RouteMode.BOSS_TRANSPORT_CHAIN_INTERIOR,
			npcAliases(taskName, npcNames),
			INTERIOR_TARGET_RADIUS,
			INTERIOR_ARRIVAL_RADIUS
		));
	}

	private static void putProfile(
		final Map<String, RouteProfile> profiles,
		final RouteProfile profile)
	{
		final String key = profileKey(
			normalizeTaskKey(profile.taskName),
			normalize(profile.location)
		);
		if (profiles.put(key, profile) != null)
		{
			throw new IllegalStateException(
				"Duplicate Slayer route profile: "
					+ profile.taskName + " @ " + profile.location
			);
		}
	}

	private static Set<String> npcAliases(
		final String taskName,
		final String... npcNames)
	{
		final Set<String> aliases = defaultNpcAliases(taskName);
		if (npcNames != null)
		{
			for (final String npcName : npcNames)
			{
				final String normalized = normalizeNpcName(npcName);
				if (!normalized.isEmpty())
				{
					aliases.add(normalized);
				}
			}
		}
		return aliases;
	}

	private static Set<String> defaultNpcAliases(final String taskName)
	{
		return new LinkedHashSet<>(
			SlayerTaskNpcCatalog.aliasesForStandardRoute(taskName)
		);
	}

	private static String normalizeNpcName(final String value)
	{
		return singularTaskName(normalize(value));
	}

	private static String normalizeTaskKey(final String value)
	{
		return singularTaskName(normalize(value));
	}

	private static String singularTaskName(final String value)
	{
		if (value == null || value.isEmpty())
		{
			return "";
		}
		if (value.endsWith("wolves"))
		{
			return value.substring(0, value.length() - 6) + "wolf";
		}
		if (value.endsWith("elves"))
		{
			return value.substring(0, value.length() - 5) + "elf";
		}
		if (value.endsWith("dwarves"))
		{
			return value.substring(0, value.length() - 7) + "dwarf";
		}
		if (value.endsWith("men"))
		{
			return value.substring(0, value.length() - 3) + "man";
		}
		if (value.endsWith("ies") && value.length() > 4)
		{
			return value.substring(0, value.length() - 3) + "y";
		}
		if (value.endsWith("s")
			&& !value.endsWith("ss")
			&& !value.endsWith("us")
			&& !value.endsWith("is")
			&& value.length() > 3)
		{
			return value.substring(0, value.length() - 1);
		}
		return value;
	}

	private static String profileKey(
		final String normalizedTask,
		final String normalizedLocation)
	{
		return normalizedTask + "|" + normalizedLocation;
	}

	private static void putAccess(
		final Map<String, WorldPoint> routes,
		final String name,
		final int x,
		final int y,
		final int plane)
	{
		/*
		 * Never silently "repair" a dungeon-layer coordinate.  Access coordinates
		 * are authored data and must remain exactly what the catalog declares so a
		 * bad value fails validation instead of becoming a plausible wrong marker.
		 */
		final String key = normalize(name);
		if (key.isEmpty())
		{
			throw new IllegalStateException("Slayer access route has a blank name");
		}
		if (routes.put(key, new WorldPoint(x, y, plane)) != null)
		{
			throw new IllegalStateException("Duplicate Slayer access route: " + name);
		}
	}

	private static TransitionSpec transitionSpecFor(
		final String location,
		final WorldPoint approachPoint)
	{
		final Set<String> names = new LinkedHashSet<>();
		final Set<String> actions = new LinkedHashSet<>();
		final String area = normalize(location);

		if (area.equals("catacombs of kourend"))
		{
			names.add("king rada");
			names.add("statue");
			actions.add("investigate");
			return new TransitionSpec(approachPoint, names, actions, 12);
		}

		if (area.equals("wilderness god wars dungeon"))
		{
			names.add("cave entrance");
			names.add("entrance");
			actions.add("enter");
			return new TransitionSpec(approachPoint, names, actions, 12);
		}

		if (area.equals("ynysdail cavern"))
		{
			names.add("rowboat");
			names.add("rowboat space");
			actions.add("travel");
			actions.add("row");
			actions.add("use");
			return new TransitionSpec(approachPoint, names, actions, 12);
		}

		if (area.equals("wyvern cave on fossil island"))
		{
			names.add("trapdoor");
			names.add("trap door");
			actions.add("climb down");
			return new TransitionSpec(approachPoint, names, actions, 10);
		}

		if (area.equals("digsite dungeon"))
		{
			names.add("winch");
			actions.add("operate");
			actions.add("use");
			actions.add("climb down");
			return new TransitionSpec(approachPoint, names, actions, 10);
		}

		if (area.equals("ruins of tapoyauik"))
		{
			names.add("lift platform");
			actions.add("use");
			actions.add("descend");
			return new TransitionSpec(approachPoint, names, actions, 12);
		}

		/*
		 * Older reviewed routes do not yet have stable object ids recorded. Keep a
		 * deliberately narrow interaction-action contract around their exact authored
		 * approach point. New routes should add object-name hints here when researched.
		 */
		actions.add("enter");
		actions.add("climb down");
		actions.add("climb down stairs");
		actions.add("go through");
		actions.add("walk through");
		actions.add("pass");
		actions.add("descend");
		actions.add("investigate");
		actions.add("use");
		return new TransitionSpec(approachPoint, names, actions, 24);
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

	private static String normalize(final String value)
	{
		if (value == null)
		{
			return "";
		}

		return value
			.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
	}
}
