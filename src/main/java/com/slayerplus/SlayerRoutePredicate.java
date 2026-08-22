package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Declarative, immutable completion predicate for a Slayer route leg.
 *
 * <p>Every predicate is inspectable by the release validator. This avoids an
 * opaque lambda whose behavior cannot be compared with the reviewed route
 * manifest.</p>
 */
public final class SlayerRoutePredicate
{
	public enum Kind
	{
		ALWAYS,
		WORLD_AREA,
		TEMPLATE_AREA,
		COORDINATE_LAYER_TRANSITION,
		TRACKED_OBJECT_ACTION,
		OBSERVED_OBJECT_INTERACTION,
		VISIBLE_WIDGET_COMPONENT,
		VISIBLE_WIDGET_GROUP,
		EXACT_NPC,
		ANY_OF,
		ALL_OF
	}

	private static final SlayerRoutePredicate ALWAYS = new SlayerRoutePredicate(
		Kind.ALWAYS,
		null,
		null,
		null,
		null,
		-1,
		Collections.emptySet(),
		Collections.emptyList()
	);

	private final Kind kind;
	private final SpatialArea area;
	private final CoordinateLayer sourceLayer;
	private final CoordinateLayer destinationLayer;
	private final ObjectActionSpec objectActionSpec;
	private final int widgetId;
	private final Set<String> exactNpcNames;
	private final List<SlayerRoutePredicate> children;

	private SlayerRoutePredicate(
		final Kind kind,
		final SpatialArea area,
		final CoordinateLayer sourceLayer,
		final CoordinateLayer destinationLayer,
		final ObjectActionSpec objectActionSpec,
		final int widgetId,
		final Set<String> exactNpcNames,
		final List<SlayerRoutePredicate> children)
	{
		this.kind = Objects.requireNonNull(kind);
		this.area = area;
		this.sourceLayer = sourceLayer;
		this.destinationLayer = destinationLayer;
		this.objectActionSpec = objectActionSpec;
		this.widgetId = widgetId;
		this.exactNpcNames = immutableSet(exactNpcNames);
		this.children = immutableList(children);
	}

	public static SlayerRoutePredicate always()
	{
		return ALWAYS;
	}

	public static SlayerRoutePredicate worldArea(final SpatialArea area)
	{
		return spatial(Kind.WORLD_AREA, area,
			SlayerRouteEvidence.CoordinateSpace.WORLD);
	}

	public static SlayerRoutePredicate templateArea(final SpatialArea area)
	{
		return spatial(Kind.TEMPLATE_AREA, area,
			SlayerRouteEvidence.CoordinateSpace.TEMPLATE);
	}

	private static SlayerRoutePredicate spatial(
		final Kind kind,
		final SpatialArea area,
		final SlayerRouteEvidence.CoordinateSpace requiredSpace)
	{
		Objects.requireNonNull(area, "area");
		if (area.getCoordinateSpace() != requiredSpace)
		{
			throw new IllegalArgumentException(
				kind + " requires a " + requiredSpace + " area"
			);
		}
		return new SlayerRoutePredicate(
			kind, area, null, null, null, -1,
			Collections.emptySet(), Collections.emptyList()
		);
	}

	public static SlayerRoutePredicate coordinateLayerTransition(
		final CoordinateLayer sourceLayer,
		final CoordinateLayer destinationLayer)
	{
		Objects.requireNonNull(sourceLayer, "sourceLayer");
		Objects.requireNonNull(destinationLayer, "destinationLayer");
		if (sourceLayer.getCoordinateSpace()
			!= destinationLayer.getCoordinateSpace())
		{
			throw new IllegalArgumentException(
				"A coordinate transition must use one coordinate space"
			);
		}
		if (sourceLayer.equals(destinationLayer))
		{
			throw new IllegalArgumentException(
				"Source and destination layers must differ"
			);
		}
		return new SlayerRoutePredicate(
			Kind.COORDINATE_LAYER_TRANSITION,
			null,
			sourceLayer,
			destinationLayer,
			null,
			-1,
			Collections.emptySet(),
			Collections.emptyList()
		);
	}

	public static SlayerRoutePredicate trackedObjectAction(
		final ObjectActionSpec spec)
	{
		return objectAction(Kind.TRACKED_OBJECT_ACTION, spec);
	}

	public static SlayerRoutePredicate observedObjectInteraction(
		final ObjectActionSpec spec)
	{
		return objectAction(Kind.OBSERVED_OBJECT_INTERACTION, spec);
	}

	private static SlayerRoutePredicate objectAction(
		final Kind kind,
		final ObjectActionSpec spec)
	{
		return new SlayerRoutePredicate(
			kind, null, null, null,
			Objects.requireNonNull(spec, "spec"), -1,
			Collections.emptySet(), Collections.emptyList()
		);
	}

	public static SlayerRoutePredicate visibleWidgetComponent(
		final int componentId)
	{
		return widget(Kind.VISIBLE_WIDGET_COMPONENT, componentId);
	}

	public static SlayerRoutePredicate visibleWidgetGroup(final int groupId)
	{
		return widget(Kind.VISIBLE_WIDGET_GROUP, groupId);
	}

	private static SlayerRoutePredicate widget(final Kind kind, final int id)
	{
		if (id < 0)
		{
			throw new IllegalArgumentException("Widget ids cannot be negative");
		}
		return new SlayerRoutePredicate(
			kind, null, null, null, null, id,
			Collections.emptySet(), Collections.emptyList()
		);
	}

	public static SlayerRoutePredicate exactNpc(final String... npcNames)
	{
		return exactNpc(Arrays.asList(npcNames));
	}

	public static SlayerRoutePredicate exactNpc(
		final Collection<String> npcNames)
	{
		final Set<String> normalized = normalizedStrings(npcNames);
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException(
				"An exact NPC predicate needs at least one exact name"
			);
		}
		return new SlayerRoutePredicate(
			Kind.EXACT_NPC, null, null, null, null, -1,
			normalized, Collections.emptyList()
		);
	}

	public static SlayerRoutePredicate anyOf(
		final SlayerRoutePredicate... predicates)
	{
		return composite(Kind.ANY_OF, Arrays.asList(predicates));
	}

	public static SlayerRoutePredicate anyOf(
		final Collection<SlayerRoutePredicate> predicates)
	{
		return composite(Kind.ANY_OF, predicates);
	}

	public static SlayerRoutePredicate allOf(
		final SlayerRoutePredicate... predicates)
	{
		return composite(Kind.ALL_OF, Arrays.asList(predicates));
	}

	public static SlayerRoutePredicate allOf(
		final Collection<SlayerRoutePredicate> predicates)
	{
		return composite(Kind.ALL_OF, predicates);
	}

	private static SlayerRoutePredicate composite(
		final Kind kind,
		final Collection<SlayerRoutePredicate> predicates)
	{
		if (predicates == null || predicates.isEmpty())
		{
			throw new IllegalArgumentException(
				kind + " requires at least one predicate"
			);
		}
		final List<SlayerRoutePredicate> copy = new ArrayList<>();
		for (final SlayerRoutePredicate predicate : predicates)
		{
			copy.add(Objects.requireNonNull(predicate, "predicate"));
		}
		if (copy.size() == 1)
		{
			return copy.get(0);
		}
		return new SlayerRoutePredicate(
			kind, null, null, null, null, -1,
			Collections.emptySet(), copy
		);
	}

	public Kind getKind()
	{
		return kind;
	}

	public SpatialArea getArea()
	{
		return area;
	}

	public CoordinateLayer getSourceLayer()
	{
		return sourceLayer;
	}

	public CoordinateLayer getDestinationLayer()
	{
		return destinationLayer;
	}

	public ObjectActionSpec getObjectActionSpec()
	{
		return objectActionSpec;
	}

	public int getWidgetId()
	{
		return widgetId;
	}

	public Set<String> getExactNpcNames()
	{
		return exactNpcNames;
	}

	public List<SlayerRoutePredicate> getChildren()
	{
		return children;
	}

	public boolean matches(final SlayerRouteEvidence evidence)
	{
		if (evidence == null)
		{
			return false;
		}
		switch (kind)
		{
			case ALWAYS:
				return true;
			case WORLD_AREA:
			case TEMPLATE_AREA:
				return area.contains(evidence);
			case COORDINATE_LAYER_TRANSITION:
				return matchesLayerTransition(evidence);
			case TRACKED_OBJECT_ACTION:
				return matchesObjectAction(
				evidence.getTrackedObjectActions());
			case OBSERVED_OBJECT_INTERACTION:
				return matchesObjectAction(
					evidence.getObservedObjectInteractions());
			case VISIBLE_WIDGET_COMPONENT:
				return evidence.getVisibleWidgetComponents().contains(widgetId);
			case VISIBLE_WIDGET_GROUP:
				return evidence.getVisibleWidgetGroups().contains(widgetId);
			case EXACT_NPC:
				return !Collections.disjoint(
					exactNpcNames, evidence.getExactNpcNames());
			case ANY_OF:
				for (final SlayerRoutePredicate child : children)
				{
					if (child.matches(evidence))
					{
						return true;
					}
				}
				return false;
			case ALL_OF:
				for (final SlayerRoutePredicate child : children)
				{
					if (!child.matches(evidence))
					{
						return false;
					}
				}
				return true;
			default:
				return false;
		}
	}

	public boolean containsKind(final Kind expected)
	{
		if (kind == expected)
		{
			return true;
		}
		for (final SlayerRoutePredicate child : children)
		{
			if (child.containsKind(expected))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns {@code true} when this predicate can be satisfied by a menu-click
	 * observation without any evidence that the requested transition actually
	 * completed.  Manual route legs use this structural check at construction
	 * time so an {@code anyOf(click, destination)} predicate cannot accidentally
	 * persist progress after a failed click.
	 */
	public boolean canMatchObservedInteractionAlone()
	{
		switch (kind)
		{
			case ALWAYS:
			case OBSERVED_OBJECT_INTERACTION:
				return true;
			case ANY_OF:
				for (final SlayerRoutePredicate child : children)
				{
					if (child.canMatchObservedInteractionAlone())
					{
						return true;
					}
				}
				return false;
			case ALL_OF:
				for (final SlayerRoutePredicate child : children)
				{
					if (!child.canMatchObservedInteractionAlone())
					{
						return false;
					}
				}
				return true;
			default:
				return false;
		}
	}

	/**
	 * Returns whether the predicate contains reviewed spatial result evidence
	 * that is outside every routed approach square for the manual checkpoint.
	 * Template-space evidence is necessarily distinct from an ordinary world
	 * approach point.
	 */
	public boolean containsSpatialResultOutside(
		final Collection<WorldPoint> approachPoints,
		final int approachRadius)
	{
		if (kind == Kind.WORLD_AREA || kind == Kind.TEMPLATE_AREA)
		{
			if (area == null)
			{
				return false;
			}
			if (area.getCoordinateSpace()
				== SlayerRouteEvidence.CoordinateSpace.TEMPLATE)
			{
				return true;
			}
			if (approachPoints == null || approachPoints.isEmpty())
			{
				return true;
			}
			for (final WorldPoint approachPoint : approachPoints)
			{
				if (area.intersectsApproach(approachPoint, approachRadius))
				{
					return false;
				}
			}
			return true;
		}
		for (final SlayerRoutePredicate child : children)
		{
			if (child.containsSpatialResultOutside(
				approachPoints, approachRadius))
			{
				return true;
			}
		}
		return false;
	}

	private boolean matchesLayerTransition(
		final SlayerRouteEvidence evidence)
	{
		final SlayerRouteEvidence.CoordinateSpace space =
			sourceLayer.getCoordinateSpace();
		return sourceLayer.contains(evidence.previousPoint(space))
			&& destinationLayer.contains(evidence.point(space));
	}

	private boolean matchesObjectAction(
		final Set<SlayerRouteEvidence.ObjectAction> observations)
	{
		for (final SlayerRouteEvidence.ObjectAction observation : observations)
		{
			if (objectActionSpec.matches(observation))
			{
				return true;
			}
		}
		return false;
	}

	private static <T> Set<T> immutableSet(final Set<T> source)
	{
		if (source == null || source.isEmpty())
		{
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(new LinkedHashSet<>(source));
	}

	private static <T> List<T> immutableList(final List<T> source)
	{
		if (source == null || source.isEmpty())
		{
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<>(source));
	}

	private static Set<String> normalizedStrings(
		final Collection<String> source)
	{
		if (source == null || source.isEmpty())
		{
			return Collections.emptySet();
		}
		final Set<String> result = new LinkedHashSet<>();
		for (final String value : source)
		{
			final String normalized = SlayerRouteEvidence.normalize(value);
			if (!normalized.isEmpty())
			{
				result.add(normalized);
			}
		}
		return result;
	}

	/** Exact rectangular area in one reviewed coordinate space and plane. */
	public static final class SpatialArea
	{
		private final SlayerRouteEvidence.CoordinateSpace coordinateSpace;
		private final int minX;
		private final int maxX;
		private final int minY;
		private final int maxY;
		private final int plane;

		private SpatialArea(
			final SlayerRouteEvidence.CoordinateSpace coordinateSpace,
			final int minX,
			final int maxX,
			final int minY,
			final int maxY,
			final int plane)
		{
			this.coordinateSpace = Objects.requireNonNull(coordinateSpace);
			if (minX > maxX || minY > maxY || plane < 0)
			{
				throw new IllegalArgumentException("Invalid route area bounds");
			}
			this.minX = minX;
			this.maxX = maxX;
			this.minY = minY;
			this.maxY = maxY;
			this.plane = plane;
		}

		public static SpatialArea world(
			final int minX,
			final int maxX,
			final int minY,
			final int maxY,
			final int plane)
		{
			return new SpatialArea(
				SlayerRouteEvidence.CoordinateSpace.WORLD,
				minX, maxX, minY, maxY, plane
			);
		}

		public static SpatialArea template(
			final int minX,
			final int maxX,
			final int minY,
			final int maxY,
			final int plane)
		{
			return new SpatialArea(
				SlayerRouteEvidence.CoordinateSpace.TEMPLATE,
				minX, maxX, minY, maxY, plane
			);
		}

		public static SpatialArea aroundWorld(
			final WorldPoint center,
			final int xRadius,
			final int yRadius)
		{
			return around(
				SlayerRouteEvidence.CoordinateSpace.WORLD,
				center, xRadius, yRadius
			);
		}

		public static SpatialArea aroundTemplate(
			final WorldPoint center,
			final int xRadius,
			final int yRadius)
		{
			return around(
				SlayerRouteEvidence.CoordinateSpace.TEMPLATE,
				center, xRadius, yRadius
			);
		}

		private static SpatialArea around(
			final SlayerRouteEvidence.CoordinateSpace coordinateSpace,
			final WorldPoint center,
			final int xRadius,
			final int yRadius)
		{
			Objects.requireNonNull(center, "center");
			if (xRadius < 0 || yRadius < 0)
			{
				throw new IllegalArgumentException(
					"Route area radii cannot be negative"
				);
			}
			return new SpatialArea(
				coordinateSpace,
				center.getX() - xRadius,
				center.getX() + xRadius,
				center.getY() - yRadius,
				center.getY() + yRadius,
				center.getPlane()
			);
		}

		public SlayerRouteEvidence.CoordinateSpace getCoordinateSpace()
		{
			return coordinateSpace;
		}

		public int getMinX()
		{
			return minX;
		}

		public int getMaxX()
		{
			return maxX;
		}

		public int getMinY()
		{
			return minY;
		}

		public int getMaxY()
		{
			return maxY;
		}

		public int getPlane()
		{
			return plane;
		}

		public boolean contains(final SlayerRouteEvidence evidence)
		{
			return evidence != null && contains(evidence.point(coordinateSpace));
		}

		public boolean contains(final WorldPoint point)
		{
			return point != null
				&& point.getPlane() == plane
				&& point.getX() >= minX
				&& point.getX() <= maxX
				&& point.getY() >= minY
				&& point.getY() <= maxY;
		}

		private boolean intersectsApproach(
			final WorldPoint point,
			final int radius)
		{
			if (point == null || point.getPlane() != plane)
			{
				return false;
			}
			final int safeRadius = Math.max(0, radius);
			return point.getX() + safeRadius >= minX
				&& point.getX() - safeRadius <= maxX
				&& point.getY() + safeRadius >= minY
				&& point.getY() - safeRadius <= maxY;
		}
	}

	/**
	 * Exact coordinate layer represented by reviewed map regions and plane.
	 * This intentionally does not use the historical {@code y >>> 12}
	 * approximation, which cannot distinguish unrelated dungeons.
	 */
	public static final class CoordinateLayer
	{
		private final SlayerRouteEvidence.CoordinateSpace coordinateSpace;
		private final int plane;
		private final Set<Integer> regionIds;

		private CoordinateLayer(
			final SlayerRouteEvidence.CoordinateSpace coordinateSpace,
			final int plane,
			final Set<Integer> regionIds)
		{
			this.coordinateSpace = Objects.requireNonNull(coordinateSpace);
			if (plane < 0 || regionIds == null || regionIds.isEmpty())
			{
				throw new IllegalArgumentException(
					"A coordinate layer needs a plane and reviewed regions"
				);
			}
			this.plane = plane;
			this.regionIds = immutableSet(regionIds);
		}

		public static CoordinateLayer worldRegions(
			final int plane,
			final int... regionIds)
		{
			return regions(
				SlayerRouteEvidence.CoordinateSpace.WORLD,
				plane, regionIds
			);
		}

		public static CoordinateLayer templateRegions(
			final int plane,
			final int... regionIds)
		{
			return regions(
				SlayerRouteEvidence.CoordinateSpace.TEMPLATE,
				plane, regionIds
			);
		}

		private static CoordinateLayer regions(
			final SlayerRouteEvidence.CoordinateSpace coordinateSpace,
			final int plane,
			final int... regionIds)
		{
			if (regionIds == null || regionIds.length == 0)
			{
				throw new IllegalArgumentException(
					"A coordinate layer needs reviewed regions"
				);
			}
			final Set<Integer> reviewed = new LinkedHashSet<>();
			for (final int regionId : regionIds)
			{
				if (regionId < 0)
				{
					throw new IllegalArgumentException(
						"Region ids cannot be negative"
					);
				}
				reviewed.add(regionId);
			}
			return new CoordinateLayer(coordinateSpace, plane, reviewed);
		}

		public SlayerRouteEvidence.CoordinateSpace getCoordinateSpace()
		{
			return coordinateSpace;
		}

		public int getPlane()
		{
			return plane;
		}

		public Set<Integer> getRegionIds()
		{
			return regionIds;
		}

		public boolean contains(final WorldPoint point)
		{
			return point != null
				&& point.getPlane() == plane
				&& regionIds.contains(point.getRegionID());
		}

		@Override
		public boolean equals(final Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof CoordinateLayer))
			{
				return false;
			}
			final CoordinateLayer that = (CoordinateLayer) other;
			return plane == that.plane
				&& coordinateSpace == that.coordinateSpace
				&& regionIds.equals(that.regionIds);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(coordinateSpace, plane, regionIds);
		}
	}

	/** Reviewed identities and exact menu action accepted for one object step. */
	public static final class ObjectActionSpec
	{
		private final Set<Integer> objectIds;
		private final Set<String> objectNames;
		private final Set<String> actions;

		private ObjectActionSpec(
			final Collection<Integer> objectIds,
			final Collection<String> objectNames,
			final Collection<String> actions)
		{
			final Set<Integer> reviewedIds = new LinkedHashSet<>();
			if (objectIds != null)
			{
				for (final Integer objectId : objectIds)
				{
					if (objectId == null || objectId <= 0)
					{
						throw new IllegalArgumentException(
							"Reviewed object ids must be positive"
						);
					}
					reviewedIds.add(objectId);
				}
			}
			this.objectIds = immutableSet(reviewedIds);
			this.objectNames = immutableSet(normalizedStrings(objectNames));
			this.actions = immutableSet(normalizedStrings(actions));
			if (this.objectIds.isEmpty() && this.objectNames.isEmpty())
			{
				throw new IllegalArgumentException(
					"An object spec needs reviewed ids or exact names"
				);
			}
			if (this.actions.isEmpty())
			{
				throw new IllegalArgumentException(
					"An object spec needs at least one exact action"
				);
			}
		}

		public static ObjectActionSpec named(
			final String objectName,
			final String... actions)
		{
			return new ObjectActionSpec(
				Collections.emptySet(),
				Collections.singleton(objectName),
				Arrays.asList(actions)
			);
		}

		public static ObjectActionSpec identified(
			final int objectId,
			final String... actions)
		{
			return new ObjectActionSpec(
				Collections.singleton(objectId),
				Collections.emptySet(),
				Arrays.asList(actions)
			);
		}

		public static ObjectActionSpec reviewed(
			final Collection<Integer> objectIds,
			final Collection<String> objectNames,
			final Collection<String> actions)
		{
			return new ObjectActionSpec(objectIds, objectNames, actions);
		}

		public Set<Integer> getObjectIds()
		{
			return objectIds;
		}

		public Set<String> getObjectNames()
		{
			return objectNames;
		}

		public Set<String> getActions()
		{
			return actions;
		}

		private boolean matches(
			final SlayerRouteEvidence.ObjectAction observation)
		{
			final boolean identityMatches =
				(!objectIds.isEmpty()
					&& objectIds.contains(observation.getObjectId()))
				|| (!objectNames.isEmpty()
					&& objectNames.contains(observation.getObjectName()));
			return identityMatches && actions.contains(observation.getAction());
		}
	}
}
