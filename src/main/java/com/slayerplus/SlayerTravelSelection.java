package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable authoritative travel state for one exact Slayer route generation.
 *
 * <p>All consumers (Bank Tag, panel text, teleport highlighting and Shortest
 * Path synchronization) read the same object. Provisional authored items may
 * keep inventory slot 1 useful while Shortest Path is calculating, but only a
 * resolved Shortest Path selection is cached as authoritative.</p>
 *
 * <p>The equivalent-item-family set records the physical alternatives Shortest
 * Path exposed for the same used route edge. One member is selected as the
 * authoritative item; the remaining members are suppression metadata only so
 * the Bank Tag cannot render competing equivalent teleports elsewhere in the
 * 4 x 7 inventory.</p>
 */
public final class SlayerTravelSelection
{
	public enum Status
	{
		UNRESOLVED,
		PROVISIONAL_ITEM,
		RESOLVED_ITEM,
		RESOLVED_NON_ITEM
	}

	public enum Source
	{
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

	private SlayerTravelSelection(
		final String routeIdentity,
		final long generation,
		final Status status,
		final Source source,
		final int itemId,
		final String itemName,
		final String destination,
		final Set<String> equivalentItemFamilies)
	{
		this.routeIdentity = routeIdentity == null ? "" : routeIdentity;
		this.generation = generation;
		this.status = status == null ? Status.UNRESOLVED : status;
		this.source = source == null ? Source.NONE : source;
		this.itemId = itemId;
		this.itemName = itemName == null ? "" : itemName.trim();
		this.destination = destination == null ? "" : destination.trim();
		this.equivalentItemFamilies = immutableFamilies(equivalentItemFamilies);
	}

	public static SlayerTravelSelection unresolved(
		final String routeIdentity,
		final long generation)
	{
		return new SlayerTravelSelection(
			routeIdentity,
			generation,
			Status.UNRESOLVED,
			Source.NONE,
			-1,
			"",
			"",
			Collections.emptySet()
		);
	}

	public static SlayerTravelSelection provisionalItem(
		final String routeIdentity,
		final long generation,
		final int itemId,
		final String itemName,
		final String destination)
	{
		return provisionalItem(
			routeIdentity,
			generation,
			itemId,
			itemName,
			destination,
			singleFamily(itemName)
		);
	}

	public static SlayerTravelSelection provisionalItem(
		final String routeIdentity,
		final long generation,
		final int itemId,
		final String itemName,
		final String destination,
		final Set<String> equivalentItemFamilies)
	{
		return new SlayerTravelSelection(
			routeIdentity,
			generation,
			Status.PROVISIONAL_ITEM,
			Source.AUTHORED_FALLBACK,
			itemId,
			itemName,
			destination,
			equivalentItemFamilies
		);
	}

	public static SlayerTravelSelection resolvedItem(
		final String routeIdentity,
		final long generation,
		final int itemId,
		final String itemName,
		final String destination)
	{
		return resolvedItem(
			routeIdentity,
			generation,
			itemId,
			itemName,
			destination,
			singleFamily(itemName)
		);
	}

	public static SlayerTravelSelection resolvedItem(
		final String routeIdentity,
		final long generation,
		final int itemId,
		final String itemName,
		final String destination,
		final Set<String> equivalentItemFamilies)
	{
		return new SlayerTravelSelection(
			routeIdentity,
			generation,
			Status.RESOLVED_ITEM,
			Source.SHORTEST_PATH,
			itemId,
			itemName,
			destination,
			equivalentItemFamilies
		);
	}

	public static SlayerTravelSelection resolvedNonItem(
		final String routeIdentity,
		final long generation,
		final String family,
		final String destination)
	{
		return new SlayerTravelSelection(
			routeIdentity,
			generation,
			Status.RESOLVED_NON_ITEM,
			Source.SHORTEST_PATH,
			-1,
			family,
			destination,
			Collections.emptySet()
		);
	}

	public String getRouteIdentity() { return routeIdentity; }
	public long getGeneration() { return generation; }
	public Status getStatus() { return status; }
	public Source getSource() { return source; }
	public int getItemId() { return itemId; }
	public String getItemName() { return itemName; }
	public String getDestination() { return destination; }
	public Set<String> getEquivalentItemFamilies() { return equivalentItemFamilies; }

	public boolean belongsTo(final String identity)
	{
		return identity != null && !identity.isEmpty()
			&& routeIdentity.equals(identity);
	}

	public boolean hasPhysicalItem()
	{
		return itemId > 0
			&& (status == Status.PROVISIONAL_ITEM
				|| status == Status.RESOLVED_ITEM);
	}

	public boolean isResolved()
	{
		return status == Status.RESOLVED_ITEM
			|| status == Status.RESOLVED_NON_ITEM;
	}

	public boolean isProvisional()
	{
		return status == Status.PROVISIONAL_ITEM;
	}

	/**
	 * Stable, allocation-cheap identity for UI consumers. The generation and all
	 * travel-decision fields move together, so a route/profile change cannot leave
	 * a highlighter or panel observing half of the previous decision.
	 */
	public String travelIdentity()
	{
		return generation
			+ "|" + routeIdentity
			+ "|" + status
			+ "|" + itemId
			+ "|" + itemName
			+ "|" + destination
			+ "|" + equivalentItemFamilies;
	}

	/** Fast structural sanity check used by the shared regression guard. */
	public boolean isStructurallyValid()
	{
		if (routeIdentity.isEmpty())
		{
			return status == Status.UNRESOLVED && itemId <= 0;
		}
		if (status == Status.PROVISIONAL_ITEM || status == Status.RESOLVED_ITEM)
		{
			return itemId > 0
				&& !itemName.isEmpty()
				&& !equivalentItemFamilies.isEmpty();
		}
		if (status == Status.RESOLVED_NON_ITEM)
		{
			return itemId <= 0;
		}
		return itemId <= 0;
	}

	private static Set<String> singleFamily(final String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return Collections.emptySet();
		}
		final Set<String> result = new LinkedHashSet<>();
		result.add(value.trim());
		return result;
	}

	private static Set<String> immutableFamilies(final Set<String> source)
	{
		if (source == null || source.isEmpty())
		{
			return Collections.emptySet();
		}
		final Set<String> copy = new LinkedHashSet<>();
		for (final String value : source)
		{
			if (value != null && !value.trim().isEmpty())
			{
				copy.add(value.trim());
			}
		}
		return copy.isEmpty()
			? Collections.emptySet()
			: Collections.unmodifiableSet(copy);
	}
}
