package com.slayerplus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for travel state.
 *
 * <p>A monotonically increasing generation invalidates every deferred result
 * owned by the previous route/profile. Only resolved Shortest Path selections
 * are cached; authored fallbacks remain provisional and can never overwrite a
 * resolved selection.</p>
 */
public final class SlayerTravelCoordinator
{
	private final Map<String, SlayerTravelSelection> resolvedByRoute =
		new LinkedHashMap<>();
	private long generation;
	private SlayerTravelSelection current =
		SlayerTravelSelection.unresolved("", 0L);

	public SlayerTravelSelection beginRoute(final String routeIdentity)
	{
		generation++;
		final String identity = routeIdentity == null ? "" : routeIdentity;
		final SlayerTravelSelection cached = resolvedByRoute.get(identity);
		if (cached != null && !identity.isEmpty())
		{
			current = cached.getStatus() == SlayerTravelSelection.Status.RESOLVED_ITEM
				? SlayerTravelSelection.resolvedItem(
					identity,
					generation,
					cached.getItemId(),
					cached.getItemName(),
					cached.getDestination(),
					cached.getEquivalentItemFamilies()
				)
				: SlayerTravelSelection.resolvedNonItem(
					identity,
					generation,
					cached.getItemName(),
					cached.getDestination()
				);
			return current;
		}
		current = SlayerTravelSelection.unresolved(identity, generation);
		return current;
	}

	public SlayerTravelSelection invalidate()
	{
		generation++;
		current = SlayerTravelSelection.unresolved("", generation);
		return current;
	}

	/**
	 * Clears both the live route and every account-specific resolved route.
	 * Use this only at hard lifecycle boundaries such as logout or plugin
	 * shutdown; normal route/session invalidation intentionally preserves exact
	 * same-account cached selections.
	 */
	public SlayerTravelSelection clearAll()
	{
		resolvedByRoute.clear();
		return invalidate();
	}

	public void invalidateRoute(final String routeIdentity)
	{
		/*
		 * invalidateRoute is a hard generation boundary: the currently active
		 * route is invalidated regardless of which identity the caller supplied.
		 * Evict both identities so a caller that has just swapped recommendation
		 * objects cannot accidentally leave the old active Shortest Path winner
		 * cached under the profile it is leaving.
		 */
		final String activeIdentity = current.getRouteIdentity();
		if (routeIdentity != null && !routeIdentity.isEmpty())
		{
			resolvedByRoute.remove(routeIdentity);
		}
		if (activeIdentity != null && !activeIdentity.isEmpty())
		{
			resolvedByRoute.remove(activeIdentity);
		}
		invalidate();
	}

	public SlayerTravelSelection seedProvisionalItem(
		final String routeIdentity,
		final int itemId,
		final String itemName,
		final String destination)
	{
		return seedProvisionalItem(
			routeIdentity,
			itemId,
			itemName,
			destination,
			java.util.Collections.singleton(itemName == null ? "" : itemName)
		);
	}

	public SlayerTravelSelection seedProvisionalItem(
		final String routeIdentity,
		final int itemId,
		final String itemName,
		final String destination,
		final Set<String> equivalentItemFamilies)
	{
		if (!ownsCurrentRoute(routeIdentity)
			|| itemId <= 0
			|| itemName == null
			|| itemName.trim().isEmpty()
			|| current.isResolved())
		{
			return current;
		}
		current = SlayerTravelSelection.provisionalItem(
			current.getRouteIdentity(),
			current.getGeneration(),
			itemId,
			itemName,
			destination,
			equivalentItemFamilies
		);
		return current;
	}

	public SlayerTravelSelection resolveItem(
		final String routeIdentity,
		final int itemId,
		final String itemName,
		final String destination)
	{
		return resolveItem(
			routeIdentity,
			itemId,
			itemName,
			destination,
			java.util.Collections.singleton(itemName == null ? "" : itemName)
		);
	}

	public SlayerTravelSelection resolveItem(
		final String routeIdentity,
		final int itemId,
		final String itemName,
		final String destination,
		final Set<String> equivalentItemFamilies)
	{
		if (!ownsCurrentRoute(routeIdentity)
			|| itemId <= 0
			|| itemName == null
			|| itemName.trim().isEmpty())
		{
			return current;
		}
		current = SlayerTravelSelection.resolvedItem(
			current.getRouteIdentity(),
			current.getGeneration(),
			itemId,
			itemName,
			destination,
			equivalentItemFamilies
		);
		resolvedByRoute.put(current.getRouteIdentity(), current);
		return current;
	}

	public SlayerTravelSelection resolveNonItem(
		final String routeIdentity,
		final String family,
		final String destination)
	{
		if (!ownsCurrentRoute(routeIdentity))
		{
			return current;
		}
		current = SlayerTravelSelection.resolvedNonItem(
			current.getRouteIdentity(),
			current.getGeneration(),
			family,
			destination
		);
		resolvedByRoute.put(current.getRouteIdentity(), current);
		return current;
	}

	/**
	 * Repairs only the concrete owned variant of an already-selected physical
	 * travel family. Route identity, generation, destination, status and the set
	 * of Shortest Path edge alternatives are preserved. This prevents Bank Tag
	 * from silently using one charged/cosmetic variant while the highlighter and
	 * panel still point at another.
	 */
	public SlayerTravelSelection replacePhysicalItemVariant(
		final String routeIdentity,
		final int itemId,
		final String itemName)
	{
		if (routeIdentity == null
			|| routeIdentity.isEmpty()
			|| itemId <= 0
			|| itemName == null
			|| itemName.trim().isEmpty())
		{
			return current;
		}

		final boolean live = ownsCurrentRoute(routeIdentity);
		final SlayerTravelSelection selected = live
			? current
			: resolvedByRoute.get(routeIdentity);
		if (selected == null || !selected.hasPhysicalItem())
		{
			return current;
		}

		final SlayerTravelSelection replacement;
		if (selected.isProvisional())
		{
			replacement = SlayerTravelSelection.provisionalItem(
				selected.getRouteIdentity(),
				selected.getGeneration(),
				itemId,
				itemName,
				selected.getDestination(),
				selected.getEquivalentItemFamilies()
			);
		}
		else
		{
			replacement = SlayerTravelSelection.resolvedItem(
				selected.getRouteIdentity(),
				selected.getGeneration(),
				itemId,
				itemName,
				selected.getDestination(),
				selected.getEquivalentItemFamilies()
			);
			resolvedByRoute.put(routeIdentity, replacement);
		}

		if (live)
		{
			current = replacement;
		}
		return live ? current : replacement;
	}

	public SlayerTravelSelection current()
	{
		return current;
	}

	/**
	 * UI-safe current selection. A consumer naming a different profile receives
	 * an unresolved value instead of ever observing stale travel from the route
	 * being left.
	 */
	public SlayerTravelSelection currentFor(final String routeIdentity)
	{
		return ownsCurrentRoute(routeIdentity)
			? current
			: SlayerTravelSelection.unresolved(
				routeIdentity == null ? "" : routeIdentity,
				generation
			);
	}

	public SlayerTravelSelection selectionFor(final String routeIdentity)
	{
		if (ownsCurrentRoute(routeIdentity))
		{
			return current;
		}
		final SlayerTravelSelection cached = routeIdentity == null
			? null
			: resolvedByRoute.get(routeIdentity);
		return cached == null
			? SlayerTravelSelection.unresolved(
				routeIdentity == null ? "" : routeIdentity,
				generation
			)
			: cached;
	}

	public long getGeneration()
	{
		return generation;
	}

	public boolean ownsCurrentRoute(final String routeIdentity)
	{
		return routeIdentity != null
			&& !routeIdentity.isEmpty()
			&& current.getRouteIdentity().equals(routeIdentity);
	}
}
