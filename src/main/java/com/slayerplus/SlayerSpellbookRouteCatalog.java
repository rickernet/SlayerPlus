package com.slayerplus;

import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Reviewed permanent spellbook-change points used by the guided session.
 *
 * <p>These are the authoritative overworld fallbacks. The live plugin may stage
 * through a Max/Construction cape and use a POH altar only after its actual
 * object is observed in the loaded house; an incompatible or absent altar falls
 * back to these points.</p>
 */
public final class SlayerSpellbookRouteCatalog
{
	static final int STANDARD = 0;
	static final int ANCIENT = 1;
	static final int LUNAR = 2;
	static final int ARCEUUS = 3;

	private static final Route ANCIENT_ROUTE = new Route(
		"Ancient Magicks",
		new WorldPoint(3231, 9311, 0),
		"Pray at the altar inside the Ancient Pyramid to switch spellbooks."
	);
	private static final Route LUNAR_ROUTE = new Route(
		"Lunar spellbook",
		new WorldPoint(2156, 3864, 0),
		"Pray at the Astral Altar to switch spellbooks."
	);
	private static final Route ARCEUUS_ROUTE = new Route(
		"Arceuus spellbook",
		new WorldPoint(1714, 3883, 0),
		"Do not use the Dark Altar itself. Talk to Tyss beside it and ask to activate the Arceuus spellbook."
	);
	private static final Route STANDARD_FROM_ARCEUUS_ROUTE = new Route(
		"Standard spellbook",
		new WorldPoint(1714, 3883, 0),
		"Do not use the Dark Altar itself. Talk to Tyss beside it and ask him to lift the Arceuus spellbook."
	);

	private SlayerSpellbookRouteCatalog()
	{
	}

	public static Route resolve(
		final String requiredSpellbook,
		final int currentSpellbook)
	{
		final String required = normalize(requiredSpellbook);
		if (required.contains("ancient"))
		{
			return currentSpellbook == ANCIENT ? null : ANCIENT_ROUTE;
		}
		if (required.contains("lunar"))
		{
			return currentSpellbook == LUNAR ? null : LUNAR_ROUTE;
		}
		if (required.contains("arceuus"))
		{
			return currentSpellbook == ARCEUUS ? null : ARCEUUS_ROUTE;
		}
		if (required.contains("standard"))
		{
			switch (currentSpellbook)
			{
				case ANCIENT: return ANCIENT_ROUTE;
				case LUNAR: return LUNAR_ROUTE;
				case ARCEUUS: return STANDARD_FROM_ARCEUUS_ROUTE;
				case STANDARD:
				default: return null;
			}
		}
		return null;
	}

	private static String normalize(final String value)
	{
		return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ENGLISH);
	}

	public static final class Route
	{
		private final String spellbookName;
		private final WorldPoint destination;
		private final String interaction;

		private Route(
			final String spellbookName,
			final WorldPoint destination,
			final String interaction)
		{
			this.spellbookName = spellbookName;
			this.destination = destination;
			this.interaction = interaction;
		}

		public String getSpellbookName() { return spellbookName; }
		public WorldPoint getDestination() { return destination; }
		public String getInteraction() { return interaction; }
		public Set<WorldPoint> getTargets()
		{
			return SlayerRouteCatalog.targetArea(destination, 2);
		}
	}
}
