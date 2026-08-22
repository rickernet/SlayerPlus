package com.slayerplus;

import net.runelite.api.coords.WorldPoint;

public final class SlayerRecommendation
{
	private final String location;
	private final String method;
	private final String reason;
	private final String travel;
	private final String cannon;
	private final String requirements;
	private final String restriction;
	private final WorldPoint destination;
	private final SlayerTaskStrategy strategy;

	public SlayerRecommendation(
		final String location,
		final String method,
		final String travel,
		final String cannon,
		final String requirements,
		final String restriction)
	{
		this(
			location,
			method,
			"Selected from the available unlocked locations.",
			travel,
			cannon,
			requirements,
			restriction,
			SlayerRouteCatalog.find(location)
		);
	}

	public SlayerRecommendation(
		final String location,
		final String method,
		final String reason,
		final String travel,
		final String cannon,
		final String requirements,
		final String restriction)
	{
		this(
			location,
			method,
			reason,
			travel,
			cannon,
			requirements,
			restriction,
			SlayerRouteCatalog.find(location)
		);
	}

	public SlayerRecommendation(
		final String location,
		final String method,
		final String reason,
		final String travel,
		final String cannon,
		final String requirements,
		final String restriction,
		final WorldPoint destination)
	{
		this(
			location,
			method,
			reason,
			travel,
			cannon,
			requirements,
			restriction,
			destination,
			null
		);
	}

	public SlayerRecommendation(
		final String location,
		final String method,
		final String reason,
		final String travel,
		final String cannon,
		final String requirements,
		final String restriction,
		final WorldPoint destination,
		final SlayerTaskStrategy strategy)
	{
		this.location = safe(location, "No recommendation");
		this.method = safe(method, "No method available");
		this.reason = safe(reason, "No scoring explanation available");
		this.travel = safe(travel, "No route available");
		this.cannon = safe(cannon, "Unknown");
		this.requirements = safe(requirements, "None");
		this.restriction = safe(restriction, "None");
		this.destination = destination;
		this.strategy = strategy;
	}

	public String getLocation()
	{
		return location;
	}

	public String getMethod()
	{
		return method;
	}

	public String getReason()
	{
		return reason;
	}

	public String getTravel()
	{
		return travel;
	}

	public String getCannon()
	{
		return cannon;
	}

	public String getRequirements()
	{
		return requirements;
	}

	public String getRestriction()
	{
		return restriction;
	}

	public WorldPoint getDestination()
	{
		return destination;
	}

	public boolean hasDestination()
	{
		return destination != null;
	}

	public SlayerTaskStrategy getStrategy()
	{
		return strategy;
	}

	public boolean hasTaskStrategy()
	{
		return strategy != null;
	}

	private static String safe(
		final String value,
		final String fallback)
	{
		return value == null || value.trim().isEmpty()
			? fallback
			: value.trim();
	}
}
