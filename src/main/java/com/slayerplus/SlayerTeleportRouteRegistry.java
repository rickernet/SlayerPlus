package com.slayerplus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Menu-facing teleport identity normalization.
 *
 * <p>This is deliberately separate from encounter routing. Shortest Path may
 * use a backend destination label that differs from the live OSRS menu label;
 * the registry contains only equivalences for the same transport destination,
 * never route ranking or fallback policy.</p>
 */
public final class SlayerTeleportRouteRegistry
{
	private SlayerTeleportRouteRegistry()
	{
	}

	public static String menuDestination(
		final String itemName,
		final String routeDestination)
	{
		final String family = normalize(itemName);
		final String destination = normalize(routeDestination);

		/* Shortest Path's Slayer-ring edge uses Dark Beasts; OSRS shows ME2 Caves. */
		if (family.contains("slayer ring")
			&& (destination.equals("dark beasts")
				|| destination.equals("me2 caves")))
		{
			return "ME2 Caves";
		}

		if (family.contains("slayer ring")
			&& isWyrmscraigCavernDestination(destination))
		{
			return "Wyrmscraig Cavern";
		}

		/* Ghommal's Hilt 4+ uses Mor Ul Rek as the live inner-city menu label. */
		if ((family.contains("ghommal s hilt") || family.contains("ghommal s avernic defender"))
			&& isMorUlRekDestination(destination))
		{
			return "Mor Ul Rek";
		}

		/* Karamja gloves 4 use "Slayer Master", not "Duradel", in game. */
		if (family.contains("karamja gloves 4")
			&& isDuradelDestination(destination))
		{
			return "Slayer Master";
		}

		/* Karamja gloves 3/4 use the short live label for the mine teleport. */
		if (family.contains("karamja gloves")
			&& isShiloMineDestination(destination))
		{
			return "Gem Mine";
		}

		if (family.contains("rada s blessing")
			&& isKonarDestination(destination))
		{
			return "Mount Karuulm";
		}

		/*
		 * Shortest Path identifies Achievement diary cape destinations by the
		 * diary taskmaster. The live cape flyout identifies the same destinations
		 * by diary region (for example Jarr is displayed as Desert).
		 */
		if (isAchievementDiaryCape(family))
		{
			final String diaryRegion = achievementDiaryRegion(destination);
			if (diaryRegion != null)
			{
				return diaryRegion;
			}
		}

		/* POH-capable capes are commonly described by Shortest Path as POH/house. */
		if ((family.contains("max cape") || family.contains("construction cape")
			|| family.contains("construct cape"))
			&& isHouseDestination(destination))
		{
			return "Teleport to house";
		}

		return clean(routeDestination);
	}

	public static Set<String> destinationAliases(
		final String itemName,
		final String routeDestination)
	{
		final Set<String> aliases = new LinkedHashSet<>();
		add(aliases, routeDestination);
		add(aliases, menuDestination(itemName, routeDestination));

		final String family = normalize(itemName);
		final String destination = normalize(routeDestination);

		if (family.contains("slayer ring"))
		{
			addSlayerRingAliases(aliases, destination);
		}

		if ((family.contains("ghommal s hilt") || family.contains("ghommal s avernic defender"))
			&& isMorUlRekDestination(destination))
		{
			add(aliases, "Mor Ul Rek");
			add(aliases, "Mor-Ul-Rek");
			add(aliases, "Inner Mor Ul Rek");
		}

		if (family.contains("karamja gloves 4")
			&& isDuradelDestination(destination))
		{
			add(aliases, "Slayer Master");
			add(aliases, "Duradel");
			add(aliases, "Kuradal");
		}

		if (family.contains("karamja gloves")
			&& isShiloMineDestination(destination))
		{
			add(aliases, "Gem Mine");
			add(aliases, "Shilo Village gem mine");
		}

		if (family.contains("rada s blessing")
			&& isKonarDestination(destination))
		{
			add(aliases, "Mount Karuulm");
			add(aliases, "Konar");
		}

		if (isAchievementDiaryCape(family))
		{
			add(aliases, achievementDiaryRegion(destination));
		}

		if ((family.contains("max cape") || family.contains("construction cape")
			|| family.contains("construct cape"))
			&& isHouseDestination(destination))
		{
			add(aliases, "POH");
			add(aliases, "House");
			add(aliases, "Home");
			add(aliases, "Player-owned house");
			/* Live Max/Construction cape menus use the shortened "Tele to POH"
			 * label. Easy Teleports also keys its Max-cape Home replacement from
			 * that exact source text, so keep both shortened and expanded forms. */
			add(aliases, "Tele to POH");
			add(aliases, "Tele to house");
			add(aliases, "Teleport to house");
			add(aliases, "Teleport to POH");
		}

		if ((family.contains("max cape") || family.contains("construction cape")
			|| family.contains("construct cape"))
			&& isPohPortalDestination(destination))
		{
			add(aliases, pohPortalLeaf(routeDestination));
		}

		return aliases;
	}

	private static boolean isAchievementDiaryCape(final String normalizedFamily)
	{
		return normalizedFamily.contains("achievement diary cape")
			|| normalizedFamily.contains("achievement cape");
	}

	private static String achievementDiaryRegion(final String destination)
	{
		/*
		 * Shortest Path's transport display is not limited to the taskmaster name.
		 * Current builds can report values such as
		 * "Achievement diary cape: 2. Jarr".  The top-level cape entry is still
		 * identifiable by item id, but the standalone second-level region menu has
		 * no item context.  Reduce that decorated display to the same live
		 * taskmaster label used by the cape interface before mapping it to a region.
		 */
		String taskmaster = normalize(destination)
			.replaceFirst("^(?:achievement diary cape|achievement cape)\\s+", "")
			.replaceFirst("^\\d+\\s+", "")
			.trim();
		switch (taskmaster)
		{
			case "two pints":
				return "Ardougne";
			case "jarr":
				return "Desert";
			case "sir rebral":
				return "Falador";
			case "thorodin":
				return "Fremennik";
			case "the wedge":
			case "wedge":
				return "Kandarin";
			case "pirate jackie the fruit":
			case "pirate jackie":
				return "Karamja";
			case "elise":
				return "Kourend & Kebos";
			case "hatius cosaintus":
			case "hatius":
				return "Lumbridge & Draynor";
			case "le sabre":
			case "le sabr":
			case "le sabre taskmaster":
				return "Morytania";
			case "toby":
				return "Varrock";
			case "lesser fanatic":
				return "Wilderness";
			case "elder gnome child":
				return "Western Provinces";
			case "twiggy o korn":
			case "twiggy":
				return "Twiggy O'Korn";
			default:
				return null;
		}
	}

	/**
	 * Match an Easy Teleports replacement config key to the authoritative route
	 * destination without depending on the user's configured replacement text.
	 *
	 * <p>The preferred path is object identity captured before Easy Teleports
	 * rewrites the UI. This matcher is a compatibility fallback for interfaces or
	 * event orderings where that identity was not available.</p>
	 */
	public static boolean matchesEasyTeleportsConfigKey(
		final String itemName,
		final String routeDestination,
		final String configKey)
	{
		final String key = normalizeConfigKey(configKey);
		if (key.isEmpty())
		{
			return false;
		}

		final Set<String> aliases = destinationAliases(itemName, routeDestination);
		for (final String alias : aliases)
		{
			final String normalizedAlias = normalize(alias);
			if (normalizedAlias.isEmpty())
			{
				continue;
			}
			if (key.equals(normalizedAlias))
			{
				return true;
			}

			final Set<String> aliasTokens = tokens(normalizedAlias);
			final Set<String> keyTokens = tokens(key);
			if (aliasTokens.isEmpty() || keyTokens.isEmpty())
			{
				continue;
			}

			/*
			 * Easy Teleports often prefixes keys with an item-family word, e.g.
			 * replacementGamesWintertodtCamp or replacementSkillsFarmingGuild.
			 * Requiring the destination's meaningful tokens to be represented keeps
			 * arbitrary replacement values isolated to the correct route.
			 */
			final Set<String> meaningfulAliasTokens = meaningfulTokens(aliasTokens);
			if (!meaningfulAliasTokens.isEmpty()
				&& keyTokens.containsAll(meaningfulAliasTokens))
			{
				return true;
			}

			final Set<String> meaningfulKeyTokens = meaningfulTokens(keyTokens);
			if (meaningfulKeyTokens.size() >= 2
				&& aliasTokens.containsAll(meaningfulKeyTokens))
			{
				return true;
			}
		}
		return false;
	}

	private static void addSlayerRingAliases(
		final Set<String> aliases,
		final String destination)
	{
		if (destination.equals("dark beasts") || destination.equals("me2 caves"))
		{
			add(aliases, "Dark Beasts");
			add(aliases, "ME2 Caves");
			return;
		}
		if (destination.equals("stronghold slayer cave")
			|| destination.equals("gnome stronghold caves")
			|| destination.equals("slayer stronghold"))
		{
			add(aliases, "Stronghold Slayer Cave");
			add(aliases, "Gnome Stronghold Caves");
			add(aliases, "Slayer Stronghold");
			return;
		}
		if (destination.equals("morytania slayer tower")
			|| destination.equals("slayer tower"))
		{
			add(aliases, "Morytania Slayer Tower");
			add(aliases, "Slayer Tower");
			return;
		}
		if (destination.equals("rellekka slayer caves")
			|| destination.equals("rellekka caves"))
		{
			add(aliases, "Rellekka Slayer Caves");
			add(aliases, "Rellekka Caves");
			return;
		}
		if (destination.equals("tarn s lair")
			|| destination.equals("haunted mine"))
		{
			add(aliases, "Tarn's Lair");
			add(aliases, "Haunted Mine");
			return;
		}
		if (isWyrmscraigCavernDestination(destination))
		{
			add(aliases, "Wyrmscraig Cavern");
			add(aliases, "Wyrmscraig Caverns");
			add(aliases, "Mortimer");
		}
	}

	private static boolean isWyrmscraigCavernDestination(
		final String destination)
	{
		return destination.equals("wyrmscraig cavern")
			|| destination.equals("wyrmscraig caverns")
			|| destination.equals("mortimer");
	}

	private static boolean isMorUlRekDestination(final String destination)
	{
		return destination.equals("mor ul rek")
			|| destination.equals("morulrek")
			|| destination.equals("inner mor ul rek")
			|| destination.equals("inner city");
	}

	private static boolean isDuradelDestination(final String destination)
	{
		return destination.equals("duradel")
			|| destination.equals("kuradal")
			|| destination.equals("slayer master");
	}

	private static boolean isShiloMineDestination(final String destination)
	{
		return destination.equals("gem mine")
			|| destination.equals("shilo village gem mine")
			|| destination.equals("shilo village mine");
	}

	private static boolean isKonarDestination(final String destination)
	{
		return destination.equals("konar")
			|| destination.equals("mount karuulm")
			|| destination.equals("karuulm");
	}

	private static boolean isHouseDestination(final String destination)
	{
		return destination.equals("poh")
			|| destination.equals("house")
			|| destination.equals("home")
			|| destination.equals("player owned house")
			|| destination.equals("tele to house")
			|| destination.equals("tele to poh")
			|| destination.startsWith("tele to house ")
			|| destination.startsWith("tele to poh ")
			|| destination.equals("teleport to house")
			|| destination.equals("teleport to poh")
			|| destination.startsWith("teleport to house ")
			|| destination.startsWith("teleport to poh ");
	}

	private static boolean isPohPortalDestination(final String destination)
	{
		return destination.startsWith("poh portal ")
			|| destination.startsWith("poh portals ");
	}

	private static String pohPortalLeaf(final String routeDestination)
	{
		final String destination = clean(routeDestination);
		final String leaf = destination.replaceFirst(
			"(?i)^POH\\s+Portals?\\s*:?\\s*",
			""
		);
		return leaf.equals(destination) ? "" : leaf;
	}

	private static Set<String> meaningfulTokens(final Set<String> values)
	{
		final Set<String> result = new HashSet<>();
		for (final String token : values)
		{
			if (token.length() < 3 || GENERIC_CONFIG_TOKENS.contains(token))
			{
				continue;
			}
			result.add(token);
		}
		return result;
	}

	private static Set<String> tokens(final String value)
	{
		final String normalized = normalize(value);
		if (normalized.isEmpty())
		{
			return new HashSet<>();
		}
		return new HashSet<>(Arrays.asList(normalized.split("\\s+")));
	}

	private static final Set<String> GENERIC_CONFIG_TOKENS = new HashSet<>(
		Arrays.asList(
			"replacement", "teleport", "teleports", "ring", "necklace",
			"amulet", "bracelet", "cape", "gloves", "legs", "blessing",
			"pendant", "talisman", "games", "skills", "combat", "wealth",
			"glory", "slayer", "max", "construction", "construct", "radas",
			"rad", "of", "the", "to"
		)
	);

	private static String normalizeConfigKey(final String key)
	{
		if (key == null)
		{
			return "";
		}
		String value = key.replaceFirst("^replacement", "");
		value = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
		return normalize(value);
	}

	private static void add(final Set<String> values, final String value)
	{
		final String clean = clean(value);
		if (!clean.isEmpty())
		{
			values.add(clean);
		}
	}

	private static String clean(final String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.replaceAll("(?i)<[^>]+>", " ")
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static String normalize(final String value)
	{
		return clean(value).toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", " ")
			.replaceAll("\\s+", " ")
			.trim();
	}
}
