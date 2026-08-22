package com.slayerplus;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Verified nearby arrival points for the Slayer master varbit values. */
public final class SlayerMasterRouteCatalog
{
	private static final Map<Integer, MasterRoute> ROUTES = createRoutes();

	private SlayerMasterRouteCatalog()
	{
	}

	public static MasterRoute find(final int masterId)
	{
		return ROUTES.get(masterId);
	}

	public static String getName(final int masterId)
	{
		final MasterRoute route = find(masterId);
		if (route != null)
		{
			return route.getName();
		}

		return masterId > 0 ? "Master ID " + masterId : "Unknown";
	}

	private static Map<Integer, MasterRoute> createRoutes()
	{
		final Map<Integer, MasterRoute> routes = new LinkedHashMap<>();

		put(routes, 1, "Turael / Aya", 2931, 3536, 0,
			"games necklace");
		put(routes, 2, "Mazchna", 3510, 3509, 0,
			"eternal slayer ring", "slayer ring");
		put(routes, 3, "Vannaka", 3147, 9913, 0,
			"amulet of eternal glory", "amulet of glory");
		put(routes, 4, "Chaeldar", 2445, 4431, 0,
			"eternal slayer ring", "slayer ring");
		put(routes, 5, "Duradel", 2869, 2982, 1,
			"karamja gloves 4", "karamja gloves 3");
		put(routes, 6, "Nieve / Steve", 2432, 3423, 0,
			"eternal slayer ring", "slayer ring");
		put(routes, 7, "Krystilia", 3108, 3516, 0,
			"amulet of eternal glory", "amulet of glory");
		put(routes, 8, "Konar", 1308, 3786, 0,
			"rada s blessing 4", "rada s blessing 3");
		put(routes, 9, "Spria", 3085, 3250, 0,
			"amulet of eternal glory", "amulet of glory");
		/*
		 * Mortimer is the tenth value of VarbitID.SLAYER_MASTER. His in-game
		 * Slayer-ring transport lands at 2581,8633 in the same cavern. SlayerPlus
		 * owns that new transport handoff because Shortest Path releases can lag new
		 * content, then routes the short loaded interior walk to this NPC tile.
		 */
		put(routes, 10, "Mortimer", 2589, 8614, 0,
			"eternal slayer ring", "slayer ring");

		return Collections.unmodifiableMap(routes);
	}

	private static void put(
		final Map<Integer, MasterRoute> routes,
		final int id,
		final String name,
		final int x,
		final int y,
		final int plane,
		final String... returnItemFamilies)
	{
		routes.put(
			id,
			new MasterRoute(
				id,
				name,
				new WorldPoint(x, y, plane),
				returnItemFamilies
			)
		);
	}

	public static final class MasterRoute
	{
		private final int id;
		private final String name;
		private final WorldPoint destination;
		private final List<String> returnItemFamilies;

		private MasterRoute(
			final int id,
			final String name,
			final WorldPoint destination,
			final String... returnItemFamilies)
		{
			this.id = id;
			this.name = name;
			this.destination = destination;
			this.returnItemFamilies = Collections.unmodifiableList(
				Arrays.asList(returnItemFamilies == null
					? new String[0] : returnItemFamilies.clone())
			);
		}

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public WorldPoint getDestination()
		{
			return destination;
		}

		public List<String> getReturnItemFamilies()
		{
			return returnItemFamilies;
		}

		/** Live menu destination for an authored master-return item. */
		public String getReturnDestination(final String itemFamily)
		{
			final String family = itemFamily == null
				? ""
				: itemFamily.trim().toLowerCase(Locale.ENGLISH);
			if (id == 5 && family.startsWith("karamja gloves 4"))
			{
				return "Slayer Master";
			}
			if (id == 5 && family.startsWith("karamja gloves 3"))
			{
				return "Gem Mine";
			}
			if (id == 8 && family.startsWith("rada s blessing"))
			{
				return "Mount Karuulm";
			}
			if (id == 10 && family.contains("slayer ring"))
			{
				return "Wyrmscraig Cavern";
			}
			return name;
		}
	}
}
