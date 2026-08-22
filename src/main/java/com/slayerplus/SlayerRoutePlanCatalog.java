package com.slayerplus;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exact reviewed cave, dungeon, manual-interaction, and boss route plans. */
public final class SlayerRoutePlanCatalog
{
	private static final Map<RouteKey, SlayerRoutePlan> PLANS =
		SlayerRoutePlanResource.load();
	private static final Set<RouteKey> KEYS = Collections.unmodifiableSet(
		new LinkedHashSet<>(PLANS.keySet()));
	private static final Collection<SlayerRoutePlan> UNIQUE_PLANS =
		Collections.unmodifiableSet(new LinkedHashSet<>(PLANS.values()));

	private SlayerRoutePlanCatalog()
	{
	}

	public static SlayerRoutePlan resolve(
		final String taskName,
		final String location,
		final boolean boss)
	{
		return PLANS.get(RouteKey.of(taskName, location, boss));
	}

	public static boolean hasExactPlan(
		final String taskName,
		final String location,
		final boolean boss)
	{
		return resolve(taskName, location, boss) != null;
	}

	public static boolean hasExplicitPlanForRegression(
		final String taskName,
		final String location,
		final boolean boss)
	{
		return hasExactPlan(taskName, location, boss);
	}

	public static Set<RouteKey> keysForRegression()
	{
		return KEYS;
	}

	public static Collection<SlayerRoutePlan> plansForRegression()
	{
		return UNIQUE_PLANS;
	}

	public static Collection<SlayerRoutePlan> allPlans()
	{
		return UNIQUE_PLANS;
	}

	/** Immutable normalized exact key exposed for release checks. */
	public static final class RouteKey
	{
		private final String taskName;
		private final String location;
		private final boolean boss;

		private RouteKey(
			final String taskName,
			final String location,
			final boolean boss)
		{
			this.taskName = SlayerRouteEvidence.normalize(taskName);
			this.location = SlayerRouteEvidence.normalize(location);
			this.boss = boss;
		}

		public static RouteKey of(
			final String taskName,
			final String location,
			final boolean boss)
		{
			return new RouteKey(taskName, location, boss);
		}

		public String getTaskName()
		{
			return taskName;
		}

		public String getLocation()
		{
			return location;
		}

		public boolean isBoss()
		{
			return boss;
		}

		@Override
		public boolean equals(final Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof RouteKey))
			{
				return false;
			}
			final RouteKey that = (RouteKey) other;
			return boss == that.boss
				&& taskName.equals(that.taskName)
				&& location.equals(that.location);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(taskName, location, boss);
		}

		@Override
		public String toString()
		{
			return taskName + "|" + location + "|" + boss;
		}
	}

}
