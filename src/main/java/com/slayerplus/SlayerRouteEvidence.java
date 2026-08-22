package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Immutable client-thread snapshot consumed by {@link SlayerRoutePlan}.
 *
 * <p>The route model deliberately receives data only. It never scans the
 * scene, reads widgets, or queries the client. Runtime integrations can update
 * the tracked sets from spawn, despawn, menu, widget, and NPC events and build
 * one cheap snapshot when route evidence changes.</p>
 *
 * <p>{@link #isInstanced()} is context only. There is intentionally no generic
 * "any instance" completion predicate: a player-owned house or unrelated boss
 * instance must never complete a Slayer route.</p>
 */
public final class SlayerRouteEvidence
{
	public enum CoordinateSpace
	{
		WORLD,
		TEMPLATE
	}

	private static final SlayerRouteEvidence EMPTY = builder().build();

	private final WorldPoint previousWorldPoint;
	private final WorldPoint worldPoint;
	private final WorldPoint previousTemplatePoint;
	private final WorldPoint templatePoint;
	private final boolean instanced;
	private final Set<ObjectAction> trackedObjectActions;
	private final Set<ObjectAction> observedObjectInteractions;
	private final Set<Integer> visibleWidgetComponents;
	private final Set<Integer> visibleWidgetGroups;
	private final Set<String> exactNpcNames;

	private SlayerRouteEvidence(final Builder builder)
	{
		previousWorldPoint = builder.previousWorldPoint;
		worldPoint = builder.worldPoint;
		previousTemplatePoint = builder.previousTemplatePoint;
		templatePoint = builder.templatePoint;
		instanced = builder.instanced;
		trackedObjectActions = immutableCopy(builder.trackedObjectActions);
		observedObjectInteractions = immutableCopy(
			builder.observedObjectInteractions
		);
		visibleWidgetComponents = immutableCopy(
			builder.visibleWidgetComponents
		);
		visibleWidgetGroups = immutableCopy(builder.visibleWidgetGroups);
		exactNpcNames = immutableCopy(builder.exactNpcNames);
	}

	public static SlayerRouteEvidence empty()
	{
		return EMPTY;
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public WorldPoint getPreviousWorldPoint()
	{
		return previousWorldPoint;
	}

	public WorldPoint getWorldPoint()
	{
		return worldPoint;
	}

	public WorldPoint getPreviousTemplatePoint()
	{
		return previousTemplatePoint;
	}

	public WorldPoint getTemplatePoint()
	{
		return templatePoint;
	}

	public boolean isInstanced()
	{
		return instanced;
	}

	public Set<ObjectAction> getTrackedObjectActions()
	{
		return trackedObjectActions;
	}

	public Set<ObjectAction> getObservedObjectInteractions()
	{
		return observedObjectInteractions;
	}

	public Set<Integer> getVisibleWidgetComponents()
	{
		return visibleWidgetComponents;
	}

	public Set<Integer> getVisibleWidgetGroups()
	{
		return visibleWidgetGroups;
	}

	public Set<String> getExactNpcNames()
	{
		return exactNpcNames;
	}

	WorldPoint point(final CoordinateSpace space)
	{
		return space == CoordinateSpace.TEMPLATE ? templatePoint : worldPoint;
	}

	WorldPoint previousPoint(final CoordinateSpace space)
	{
		return space == CoordinateSpace.TEMPLATE
			? previousTemplatePoint
			: previousWorldPoint;
	}

	static String normalize(final String value)
	{
		return value == null ? "" : value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
	}

	private static <T> Set<T> immutableCopy(final Set<T> source)
	{
		if (source == null || source.isEmpty())
		{
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(new LinkedHashSet<>(source));
	}

	/** Exact object/action observation captured by the runtime event bridge. */
	public static final class ObjectAction
	{
		private final int objectId;
		private final String objectName;
		private final String action;

		private ObjectAction(
			final int objectId,
			final String objectName,
			final String action)
		{
			this.objectId = objectId;
			this.objectName = normalize(objectName);
			this.action = normalize(action);
			if (objectId <= 0 && this.objectName.isEmpty())
			{
				throw new IllegalArgumentException(
					"An object action needs a reviewed id or exact name"
				);
			}
			if (this.action.isEmpty())
			{
				throw new IllegalArgumentException(
					"An object action needs an exact action"
				);
			}
		}

		public static ObjectAction of(
			final int objectId,
			final String objectName,
			final String action)
		{
			return new ObjectAction(objectId, objectName, action);
		}

		public static ObjectAction named(
			final String objectName,
			final String action)
		{
			return new ObjectAction(-1, objectName, action);
		}

		public int getObjectId()
		{
			return objectId;
		}

		public String getObjectName()
		{
			return objectName;
		}

		public String getAction()
		{
			return action;
		}

		@Override
		public boolean equals(final Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof ObjectAction))
			{
				return false;
			}
			final ObjectAction that = (ObjectAction) other;
			return objectId == that.objectId
				&& objectName.equals(that.objectName)
				&& action.equals(that.action);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(objectId, objectName, action);
		}
	}

	public static final class Builder
	{
		private WorldPoint previousWorldPoint;
		private WorldPoint worldPoint;
		private WorldPoint previousTemplatePoint;
		private WorldPoint templatePoint;
		private boolean instanced;
		private final Set<ObjectAction> trackedObjectActions =
			new LinkedHashSet<>();
		private final Set<ObjectAction> observedObjectInteractions =
			new LinkedHashSet<>();
		private final Set<Integer> visibleWidgetComponents =
			new LinkedHashSet<>();
		private final Set<Integer> visibleWidgetGroups =
			new LinkedHashSet<>();
		private final Set<String> exactNpcNames = new LinkedHashSet<>();

		private Builder()
		{
		}

		public Builder previousWorldPoint(final WorldPoint value)
		{
			previousWorldPoint = value;
			return this;
		}

		public Builder worldPoint(final WorldPoint value)
		{
			worldPoint = value;
			return this;
		}

		public Builder previousTemplatePoint(final WorldPoint value)
		{
			previousTemplatePoint = value;
			return this;
		}

		public Builder templatePoint(final WorldPoint value)
		{
			templatePoint = value;
			return this;
		}

		public Builder instanced(final boolean value)
		{
			instanced = value;
			return this;
		}

		public Builder trackedObjectAction(final ObjectAction value)
		{
			trackedObjectActions.add(Objects.requireNonNull(value));
			return this;
		}

		public Builder observedObjectInteraction(final ObjectAction value)
		{
			observedObjectInteractions.add(Objects.requireNonNull(value));
			return this;
		}

		public Builder visibleWidgetComponent(final int componentId)
		{
			if (componentId < 0)
			{
				throw new IllegalArgumentException(
					"Widget component ids cannot be negative"
				);
			}
			visibleWidgetComponents.add(componentId);
			return this;
		}

		public Builder visibleWidgetGroup(final int groupId)
		{
			if (groupId < 0)
			{
				throw new IllegalArgumentException(
					"Widget group ids cannot be negative"
				);
			}
			visibleWidgetGroups.add(groupId);
			return this;
		}

		public Builder exactNpc(final String npcName)
		{
			final String normalized = normalize(npcName);
			if (normalized.isEmpty())
			{
				throw new IllegalArgumentException(
					"Exact NPC names cannot be blank"
				);
			}
			exactNpcNames.add(normalized);
			return this;
		}

		public SlayerRouteEvidence build()
		{
			return new SlayerRouteEvidence(this);
		}
	}
}
