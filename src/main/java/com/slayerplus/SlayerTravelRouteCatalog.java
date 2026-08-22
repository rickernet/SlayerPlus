package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Route-specific physical travel fallbacks and live-candidate constraints. */
public final class SlayerTravelRouteCatalog
{
	public static final class Option
	{
		private final String itemFamily;
		private final String destination;

		private Option(final String itemFamily, final String destination)
		{
			this.itemFamily = itemFamily == null ? "" : itemFamily.trim();
			this.destination = destination == null ? "" : destination.trim();
		}

		public String getItemFamily() { return itemFamily; }
		public String getDestination() { return destination; }
	}

	private static final Map<String, List<Option>> FALLBACKS = createFallbacks();
	private static final Map<String, String> LOCATION_ALIASES = createLocationAliases();

	static
	{
		validateOrThrow();
	}

	private SlayerTravelRouteCatalog()
	{
	}

	public static List<Option> fallbacksFor(final String location)
	{
		String key = normalize(location);
		final String alias = LOCATION_ALIASES.get(key);
		if (alias != null)
		{
			key = alias;
		}
		final List<Option> options = FALLBACKS.get(key);
		return options == null ? Collections.emptyList() : options;
	}

	private static Map<String, String> createLocationAliases()
	{
		final Map<String, String> aliases = new LinkedHashMap<>();
		alias(aliases, "Fossil Island Wyvern Cave", "Wyvern Cave on Fossil Island");
		alias(aliases, "Gnome Stronghold Slayer Cave", "Stronghold Slayer Cave");
		alias(aliases, "Nieve's Slayer Cave", "Stronghold Slayer Cave");
		alias(aliases, "Steve's Slayer Cave", "Stronghold Slayer Cave");
		alias(aliases, "Kalphite Cave", "Kalphite Slayer Cave");
		alias(aliases, "Key Master", "Cerberus' Lair");
		alias(aliases, "Lighthouse", "Lighthouse Dungeon");
		alias(aliases, "Mount Karuulm", "Karuulm Slayer Dungeon");
		alias(aliases, "Slayer Tower (first floor)", "Slayer Tower");
		alias(aliases, "Slayer Tower basement", "Slayer Tower");
		alias(aliases, "The Great Conch", "Shellbane Gryphon Cave");
		alias(aliases, "TzHaar Fight Cave", "Fight Caves");
		alias(aliases, "Scorpia's Cave", "Deep Wilderness");
		alias(aliases, "Silk Chasm", "Wilderness boss cave");
		alias(aliases, "Skeletal Tomb", "Wilderness boss cave");
		alias(aliases, "Vet'ion's Rest", "Wilderness boss cave");
		alias(aliases, "Web Chasm", "Wilderness boss cave");
		alias(aliases, "Wilderness Slayer Cave", "Wilderness");
		return Collections.unmodifiableMap(aliases);
	}

	private static void alias(
		final Map<String, String> aliases,
		final String from,
		final String to)
	{
		aliases.put(normalize(from), normalize(to));
	}

	/**
	 * Deterministic physical-item representative for one exact Shortest Path edge.
	 *
	 * <p>Shortest Path can expose several interchangeable transports for the same
	 * origin/destination edge. That set is authoritative as a route, but its
	 * iteration order is not an item preference. SlayerPlus therefore chooses one
	 * owned representative globally and uses that exact choice for the panel, menu
	 * highlight, and Bank Tag inventory slot 1.</p>
	 */
	public static int liveCandidatePreference(final String family)
	{
		final String item = normalize(family);
		if (item.isEmpty())
		{
			return Integer.MIN_VALUE;
		}

		/* Reusable POH-capable capes beat consumable house tablets. */
		if (item.contains("max cape"))
		{
			return 100_000;
		}
		if (item.contains("construction cape") || item.contains("construct cape"))
		{
			return 90_000;
		}

		/* Permanent/reusable forms beat their charge-limited equivalents. */
		if (item.contains("eternal"))
		{
			return 80_000;
		}

		/* Explicit consumable teleports are the last physical representative. */
		if (item.contains("teleport to house")
			|| item.contains("house tablet")
			|| item.endsWith(" tablet")
			|| item.contains("teleport scroll"))
		{
			return 10_000;
		}

		/* Other reviewed candidates remain valid and deterministic. */
		return 50_000;
	}

	/**
	 * A reviewed destination-specific teleport outranks a generic reusable route.
	 * The fallback list is authored fastest-to-slowest for that exact location;
	 * global reusable-item preference is only a tie-breaker when the candidate is
	 * not one of those direct options. This prevents a Max cape/POH edge from
	 * replacing a Spider cave teleport on an Araxxor route.
	 */
	public static int liveCandidatePreference(
		final String location,
		final String family)
	{
		final String candidate = normalize(family);
		final List<Option> reviewed = fallbacksFor(location);
		for (int index = 0; index < reviewed.size(); index++)
		{
			final String expected = normalize(
				reviewed.get(index).getItemFamily()
			);
			if (!expected.isEmpty()
				&& (candidate.equals(expected)
					|| candidate.contains(expected)
					|| expected.contains(candidate)))
			{
				return 2_000_000 - index * 200_000
					+ liveCandidatePreference(family);
			}
		}
		return liveCandidatePreference(family);
	}

	/**
	 * Returns true when SlayerPlus must own the first transport interaction and
	 * hand pathfinding off only after the player lands. This is used for reviewed
	 * physical teleports that are valid OSRS travel but are not currently modeled
	 * as transports by the installed Shortest Path graph. Sending Shortest Path
	 * the final destination before that teleport would produce a false
	 * "destination could not be reached" result.
	 */
	public static boolean requiresExternalTeleportHandoff(
		final String location,
		final String family,
		final String destination)
	{
		final String area = normalize(location);
		final String item = normalize(family);
		final String target = normalize(destination);
		return area.equals("mor ul rek east bank")
			&& item.contains("ghommal")
			&& (item.contains("hilt") || item.contains("avernic defender"))
			&& target.equals("mor ul rek");
	}

	public static boolean allowsLiveCandidate(
		final String location,
		final String family,
		final String destination)
	{
		final String area = normalize(location);
		final String item = normalize(family);
		final String target = normalize(destination);

		if (area.equals("iorwerth dungeon"))
		{
			return target.contains("prifddinas") || item.contains("prifddinas");
		}
		if (area.equals("mourner tunnels"))
		{
			return item.contains("slayer ring")
				&& (target.equals("dark beasts") || target.equals("me2 caves"));
		}
		return true;
	}

	/**
	 * Taverley routes can be recalculated through several nearly equal bank-owned
	 * transports (for example a Games necklace or a max-cape teleport). Lassar has
	 * a reviewed direct Ring of shadows route which must not be replaced by a
	 * generic POH route after the bank tag has selected it. Keep those owned choices
	 * for the current leg so the bank tag, panel and highlighter cannot oscillate
	 * while the pathfinder publishes follow-up results.
	 */
	public static boolean locksFirstResolvedTravelItem(final String location)
	{
		final String area = normalize(location);
		return area.equals("taverley dungeon")
			|| area.equals("lassar undercity")
			|| area.equals("morytania spider cave");
	}

	/**
	 * Exact authored routes which outrank a later generic Shortest Path transport.
	 * The item is still ownership-aware because the catalog fallback is resolved
	 * against the player's live bank before this policy is consulted.
	 */
	public static boolean locksAuthoredTravelItem(final String location)
	{
		final String area = normalize(location);
		return area.equals("lassar undercity")
			|| area.equals("morytania spider cave");
	}

	/** Fast catalog invariants used at startup and by the deep regression audit. */
	public static void validateOrThrow()
	{
		for (final Map.Entry<String, List<Option>> entry : FALLBACKS.entrySet())
		{
			if (entry.getKey() == null || entry.getKey().trim().isEmpty())
			{
				throw new IllegalStateException("Travel fallback has a blank location");
			}
			if (entry.getValue() == null || entry.getValue().isEmpty())
			{
				throw new IllegalStateException(
					"Travel fallback location has no options: " + entry.getKey()
				);
			}

			final Set<String> seen = new LinkedHashSet<>();
			for (final Option option : entry.getValue())
			{
				if (option == null
					|| option.itemFamily.isEmpty()
					|| option.destination.isEmpty())
				{
					throw new IllegalStateException(
						"Travel fallback has an incomplete option: " + entry.getKey()
					);
				}
				if (SlayerTravelItemPolicy.hasExplicitUnusableState(option.itemFamily))
				{
					throw new IllegalStateException(
						"Travel fallback is authored as an unusable item state: "
							+ entry.getKey() + " -> " + option.itemFamily
					);
				}
				final String key = normalize(option.itemFamily)
					+ "|" + normalize(option.destination);
				if (!seen.add(key))
				{
					throw new IllegalStateException(
						"Duplicate travel fallback: " + entry.getKey() + " -> " + key
					);
				}
			}
		}

		/* These two restrictions caused real regressions and must stay fail-closed. */
		if (allowsLiveCandidate(
			"Iorwerth Dungeon", "Slayer ring (8)", "Gnome Stronghold Caves"
		))
		{
			throw new IllegalStateException(
				"Travel regression: Iorwerth Dungeon accepted a Slayer ring route"
			);
		}
		if (!allowsLiveCandidate(
			"Mourner Tunnels", "Slayer ring (8)", "ME2 Caves"
		))
		{
			throw new IllegalStateException(
				"Travel regression: Mourner Tunnels rejected the reviewed ME2 Caves route"
			);
		}

		/* Shortest Path's POH edge exposes these as alternatives, not a ranking. */
		if (!(liveCandidatePreference("Max cape")
			> liveCandidatePreference("Construct. cape")
			&& liveCandidatePreference("Construct. cape")
				> liveCandidatePreference("Teleport to house")))
		{
			throw new IllegalStateException(
				"Travel regression: POH physical-item preference must be Max cape > Construction cape > house tablet"
			);
		}

		if (!requiresExternalTeleportHandoff(
			"Mor Ul Rek east bank", "Ghommal's hilt 6", "Mor Ul Rek"
		) || requiresExternalTeleportHandoff(
			"Catacombs of Kourend", "Xeric's talisman", "Xeric's Heart"
		))
		{
			throw new IllegalStateException(
				"Travel regression: external teleport handoff classification is incorrect"
			);
		}

		final List<Option> infernoBank = fallbacksFor("Mor Ul Rek east bank");
		if (infernoBank.isEmpty()
			|| !normalize(infernoBank.get(0).itemFamily).equals("ghommal s hilt 6")
			|| !normalize(infernoBank.get(0).destination).equals("mor ul rek"))
		{
			throw new IllegalStateException(
				"Travel regression: Inferno prep-bank fallback no longer starts with Ghommal's hilt 6 -> Mor Ul Rek"
			);
		}
	}

	private static Map<String, List<Option>> createFallbacks()
	{
		final Map<String, List<Option>> map = new LinkedHashMap<>();
		put(map, "Catacombs of Kourend",
			option("xeric s talisman", "Xeric's Heart"),
			option("kourend castle teleport", "Kourend Castle"),
			option("achievement diary cape", "Elise"),
			option("max cape", "Teleport to house"),
			option("construct cape", "Teleport to house"),
			option("rada s blessing", "Kourend Woodland"),
			option("skills necklace", "Farming Guild"),
			option("games necklace", "Wintertodt Camp"));
		put(map, "Mourner Tunnels",
			option("eternal slayer ring", "ME2 Caves"),
			option("slayer ring", "ME2 Caves"));
		put(map, "Iorwerth Dungeon",
			option("eternal teleport crystal", "Prifddinas"),
			option("teleport crystal", "Prifddinas"),
			option("prifddinas tablet", "Prifddinas"));
		put(map, "Taverley Dungeon",
			option("taverley teleport", "Taverley"),
			option("falador teleport", "Falador"),
			option("explorer s ring 4", "Cabbage patch"),
			option("explorer s ring 3", "Cabbage patch"),
			option("explorer s ring 2", "Cabbage patch"),
			option("skills necklace", "Crafting Guild"),
			option("games necklace", "Burthorpe"));
		put(map, "Ancient Cavern",
			option("games necklace", "Barbarian Assault"),
			option("barbarian teleport", "Barbarian Outpost"));
		put(map, "Asgarnian Ice Dungeon",
			option("explorer s ring 4", "Cabbage patch"),
			option("explorer s ring 3", "Cabbage patch"),
			option("explorer s ring 2", "Cabbage patch"),
			option("falador teleport", "Falador"),
			option("skills necklace", "Crafting Guild"));
		put(map, "Brimhaven Dungeon",
			option("brimhaven teleport", "Brimhaven"),
			option("achievement diary cape", "Pirate Jackie the Fruit"),
			option("tai bwo wannai teleport", "Tai Bwo Wannai"),
			option("amulet of glory", "Karamja"));
		put(map, "Brine Rat Cavern",
			option("fremennik sea boots 4", "Rellekka"),
			option("enchanted lyre i", "Rellekka"),
			option("enchanted lyre", "Rellekka"),
			option("rellekka teleport", "Rellekka"));
		put(map, "Chasm of Fire",
			option("xeric s talisman", "Xeric's Glade"),
			option("skills necklace", "Woodcutting Guild"));
		put(map, "Darkmeyer",
			option("drakan s medallion", "Darkmeyer"));
		put(map, "Death Plateau",
			option("games necklace", "Burthorpe"),
			option("combat bracelet", "Warriors' Guild"));
		put(map, "Fremennik Slayer Dungeon",
			option("eternal slayer ring", "Fremennik Slayer Dungeon"),
			option("slayer ring", "Fremennik Slayer Dungeon"));
		put(map, "God Wars Dungeon",
			option("ghommal s hilt 6", "God Wars Dungeon"),
			option("ghommal s avernic defender 6", "God Wars Dungeon"),
			option("ghommal s hilt 5", "God Wars Dungeon"),
			option("ghommal s avernic defender 5", "God Wars Dungeon"),
			option("ghommal s hilt 4", "God Wars Dungeon"),
			option("ghommal s hilt 3", "God Wars Dungeon"),
			option("trollheim teleport", "Trollheim"));
		put(map, "Jormungand's Prison",
			option("fremennik sea boots 4", "Rellekka"),
			option("enchanted lyre i", "Rellekka"),
			option("enchanted lyre", "Rellekka"),
			option("rellekka teleport", "Rellekka"));
		put(map, "Kalphite Slayer Cave",
			option("desert amulet 4", "Kalphite Cave"),
			option("pharaoh s sceptre", "Jaldraocht"),
			option("ring of dueling", "Emir's Arena"));
		put(map, "Karuulm Slayer Dungeon",
			option("rada s blessing 4", "Mount Karuulm"),
			option("rada s blessing 3", "Mount Karuulm"),
			option("rada s blessing 2", "Kourend Woodland"),
			option("rada s blessing 1", "Kourend Woodland"));
		put(map, "Kraken Cove",
			option("piscatoris teleport", "Piscatoris"),
			option("western banner 4", "Piscatoris"),
			option("slayer ring", "Fremennik Slayer Dungeon"));
		put(map, "Lighthouse Dungeon",
			option("games necklace", "Barbarian Outpost"));
		put(map, "Lithkren Vault",
			option("digsite pendant", "Lithkren"));
		put(map, "Lletya",
			option("eternal teleport crystal", "Lletya"),
			option("teleport crystal", "Lletya"));
		put(map, "Lunar Isle",
			option("lunar isle teleport", "Lunar Isle"),
			option("fremennik sea boots 4", "Rellekka"),
			option("enchanted lyre i", "Rellekka"));
		put(map, "Meiyerditch Laboratories",
			option("drakan s medallion", "Darkmeyer"),
			option("drakan s medallion", "Ver Sinhaza"));
		put(map, "Mos Le'Harmless Cave",
			option("ectophial", "Port Phasmatys"),
			option("mos le harmless teleport", "Mos Le'Harmless"));
		put(map, "Slayer Tower",
			option("eternal slayer ring", "Slayer Tower"),
			option("slayer ring", "Slayer Tower"),
			option("morytania legs 4", "Burgh de Rott"),
			option("morytania legs 3", "Burgh de Rott"),
			option("achievement diary cape", "Le-sabre"),
			option("ectophial", "Port Phasmatys"));
		put(map, "Smoke Devil Dungeon",
			option("ring of dueling", "Emir's Arena"),
			option("desert amulet 4", "Nardah"));
		put(map, "Sophanem Dungeon",
			option("pharaoh s sceptre", "Jalsavrah"),
			option("pharaoh s sceptre", "Jaleustrophos"));
		put(map, "Stronghold of Security",
			option("skull sceptre", "Stronghold of Security"),
			option("chronicle", "Champions' Guild"));
		put(map, "Stronghold Slayer Cave",
			option("eternal slayer ring", "Stronghold Slayer Cave"),
			option("slayer ring", "Stronghold Slayer Cave"));
		put(map, "Waterbirth Island Dungeon",
			option("waterbirth teleport", "Waterbirth Island"),
			option("fremennik sea boots 4", "Rellekka"),
			option("enchanted lyre i", "Rellekka"));
		put(map, "Wyvern Cave on Fossil Island",
			option("digsite pendant", "Fossil Island"));
		put(map, "Zanaris",
			option("lunar staff", "Fairy ring"),
			option("dramen staff", "Fairy ring"));
		put(map, "Ungael",
			option("fremennik sea boots 4", "Rellekka"),
			option("fremennik sea boots 3", "Rellekka"),
			option("fremennik sea boots 2", "Rellekka"),
			option("fremennik sea boots 1", "Rellekka"),
			option("enchanted lyre i", "Rellekka"),
			option("enchanted lyre", "Rellekka"),
			option("rellekka teleport", "Rellekka"));
		put(map, "Hunter's End",
			option("ring of dueling", "Ferox Enclave"),
			option("carrallanger teleport", "Carrallanger"),
			option("games necklace", "Corporeal Beast"));
		put(map, "Callisto's Den",
			option("wilderness sword 4", "Fountain of Rune"),
			option("wilderness sword 3", "Fountain of Rune"),
			option("annakarl teleport", "Demonic Ruins"),
			option("annakarl teleport tablet", "Demonic Ruins"),
			option("games necklace", "Corporeal Beast"));
		put(map, "Ghorrock Dungeon",
			option("ring of shadows", "Ghorrock Dungeon"),
			option("icy basalt", "Weiss"),
			option("games necklace", "Wintertodt Camp"));
		put(map, "The Scar",
			option("ring of shadows", "The Scar"),
			option("amulet of the eye", "Temple of the Eye"),
			option("necklace of passage", "Wizards' Tower"));
		put(map, "Lassar Undercity",
			option("ring of shadows", "Lassar Undercity"),
			option("mind altar teleport", "Mind Altar"),
			option("lassar teleport", "Ice Mountain"));
		put(map, "Stranglewood Temple",
			option("ring of shadows", "Stranglewood Temple"),
			option("xeric s talisman", "Xeric's Honour"),
			option("rada s blessing", "Kourend Woodland"));
		put(map, "Falador Mole Lair",
			option("ring of wealth", "Falador Park"),
			option("explorer s ring 4", "Cabbage patch"),
			option("explorer s ring 3", "Cabbage patch"),
			option("explorer s ring 2", "Cabbage patch"),
			option("falador teleport", "Falador"));
		put(map, "Kalphite Lair",
			option("desert amulet 4", "Kalphite Cave"),
			option("pharaoh s sceptre", "Jaldraocht"),
			option("ring of dueling", "Emir's Arena"));
		put(map, "Ruins of Tapoyauik",
			option("pendant of ates", "Tapoyauik"));
		put(map, "Morytania Spider Cave",
			option("spider cave teleport", "Morytania Spider Cave"),
			option("drakan s medallion", "Darkmeyer"),
			option("morytania legs 4", "Ectofuntus"),
			option("lunar staff", "Fairy ring ALQ"),
			option("dramen staff", "Fairy ring ALQ"),
			option("ectophial", "Port Phasmatys"));
		put(map, "Ynysdail Cavern",
			option("eternal teleport crystal", "Prifddinas"),
			option("teleport crystal", "Prifddinas"),
			option("prifddinas tablet", "Prifddinas"),
			option("piscatoris teleport", "Piscatoris"),
			option("sailor s amulet", "Port Roberts"));
		put(map, "Stalker Den",
			option("pendant of ates", "Nemus Retreat"),
			option("lunar staff", "Fairy ring AIS"),
			option("dramen staff", "Fairy ring AIS"),
			option("lunar staff", "Fairy ring BLS"),
			option("dramen staff", "Fairy ring BLS"));
		put(map, "Shellbane Gryphon Cave",
			option("lunar staff", "Fairy ring CJQ"),
			option("dramen staff", "Fairy ring CJQ"));
		put(map, "Maggot King's lair",
			option("drakan s medallion", "Castle Drakan"));
		put(map, "Abyssal Nexus",
			option("lunar staff", "Fairy ring DIP"),
			option("dramen staff", "Fairy ring DIP"));
		put(map, "Al Kharid mine",
			option("ring of dueling", "Emir's Arena"),
			option("amulet of glory", "Al Kharid"));
		put(map, "Ancient Guthixian Temple",
			option("guthixian temple teleport", "Ancient Guthixian Temple"),
			option("games necklace", "Tears of Guthix"),
			option("max cape", "POH jewellery box -> Tears of Guthix"),
			option("construct cape", "POH jewellery box -> Tears of Guthix"),
			option("teleport to house", "POH jewellery box -> Tears of Guthix"));
		put(map, "Skotizo's Lair",
			option("xeric s talisman", "Xeric's Heart"),
			option("kourend castle teleport", "Kourend Castle"),
			option("max cape", "Teleport to house"),
			option("construct cape", "Teleport to house"),
			option("rada s blessing", "Kourend Woodland"),
			option("skills necklace", "Farming Guild"),
			option("games necklace", "Wintertodt Camp"));
		put(map, "Ardougne area",
			option("ardougne cloak 4", "Ardougne farm"),
			option("ardougne cloak 3", "Ardougne farm"),
			option("ardougne cloak 2", "Ardougne farm"),
			option("ardougne cloak 1", "Ardougne Monastery"),
			option("achievement diary cape", "Two-pints"),
			option("ardougne teleport", "Ardougne"),
			option("skills necklace", "Fishing Guild"));
		put(map, "Bandit Camp",
			option("pollnivneach teleport", "Pollnivneach"),
			option("desert amulet 4", "Nardah"));
		put(map, "Barrows",
			option("barrows teleport", "Barrows"),
			option("morytania legs 4", "Burgh de Rott"),
			option("mort ton teleport", "Mort'ton"));
		put(map, "Black Knights' Fortress",
			option("combat bracelet", "Monastery"),
			option("falador teleport", "Falador"));
		put(map, "Braindeath Island",
			option("ectophial", "Port Phasmatys"));
		put(map, "Cerberus' Lair",
			option("key master teleport", "Cerberus' Lair"),
			option("taverley teleport", "Taverley"),
			option("falador teleport", "Falador"));
		put(map, "Crash Site Cavern",
			option("royal seed pod", "Grand Tree"),
			option("spirit tree", "Grand Tree"));
		put(map, "Deep Wilderness",
			option("wilderness sword 4", "Fountain of Rune"),
			option("wilderness sword 3", "Fountain of Rune"),
			option("burning amulet", "Lava Maze"),
			option("annakarl teleport", "Demonic Ruins"),
			option("games necklace", "Corporeal Beast"));
		put(map, "Desert quarry",
			option("desert amulet 4", "Nardah"),
			option("pollnivneach teleport", "Pollnivneach"),
			option("ring of dueling", "Emir's Arena"));
		put(map, "Dorgesh-Kaan South Dungeon",
			option("lunar staff", "Fairy ring AJQ"),
			option("dramen staff", "Fairy ring AJQ"),
			option("dorgesh kaan sphere", "Dorgesh-Kaan"));
		put(map, "Dwarven Mine",
			option("skills necklace", "Mining Guild"),
			option("falador teleport", "Falador"));
		put(map, "Edgeville Dungeon",
			option("amulet of glory", "Edgeville"),
			option("ring of wealth", "Grand Exchange"));
		put(map, "Feldip Hills",
			option("feldip hills teleport", "Feldip Hills"),
			option("ring of dueling", "Castle Wars"));
		put(map, "Fight Caves",
			option("ghommal s hilt 6", "Mor Ul Rek"),
			option("ghommal s avernic defender 6", "Mor Ul Rek"),
			option("ghommal s hilt 5", "Mor Ul Rek"),
			option("ghommal s avernic defender 5", "Mor Ul Rek"),
			option("ghommal s hilt 4", "Mor Ul Rek"),
			option("lunar staff", "Fairy ring BLP"),
			option("dramen staff", "Fairy ring BLP"));
		put(map, "Forthos Dungeon",
			option("xeric s talisman", "Xeric's Glade"),
			option("rada s blessing", "Kourend Woodland"));
		put(map, "Fossil Island",
			option("digsite pendant", "Fossil Island"));
		put(map, "Haunted Woods",
			option("eternal slayer ring", "Slayer Tower"),
			option("slayer ring", "Slayer Tower"),
			option("ectophial", "Port Phasmatys"));
		put(map, "Ice Mountain",
			option("combat bracelet", "Monastery"),
			option("falador teleport", "Falador"));
		put(map, "Icy seas",
			option("sailor s amulet", "Port Roberts"),
			option("piscatoris teleport", "Piscatoris"));
		put(map, "Grimstone Dungeon",
			option("lunar staff", "Fairy ring DLP"),
			option("dramen staff", "Fairy ring DLP"),
			option("icy basalt", "Weiss"));
		put(map, "Isle of Souls",
			option("games necklace", "Soul Wars"));
		put(map, "Karamja",
			option("karamja gloves 4", "Duradel"),
			option("karamja gloves 3", "Shilo Village gem mine"),
			option("achievement diary cape", "Pirate Jackie the Fruit"),
			option("amulet of glory", "Karamja"));
		put(map, "Karamja Volcano",
			option("karamja gloves 4", "Duradel"),
			option("karamja gloves 3", "Shilo Village gem mine"),
			option("amulet of glory", "Karamja"),
			option("lunar staff", "Fairy ring BLP"),
			option("dramen staff", "Fairy ring BLP"));
		put(map, "Kebos Lowlands",
			option("skills necklace", "Farming Guild"),
			option("rada s blessing", "Kourend Woodland"));
		put(map, "Killerwatt plane",
			option("draynor manor teleport", "Draynor Manor"),
			option("amulet of glory", "Draynor Village"));
		put(map, "King Black Dragon Lair",
			option("burning amulet", "Lava Maze"),
			option("annakarl teleport", "Demonic Ruins"));
		put(map, "Kourend Woodland",
			option("rada s blessing", "Kourend Woodland"),
			option("skills necklace", "Farming Guild"));
		put(map, "Legends' Guild basement",
			option("quest point cape", "Legends' Guild"),
			option("lunar staff", "Fairy ring BLR"),
			option("dramen staff", "Fairy ring BLR"));
		put(map, "Lumbridge area",
			option("lumbridge teleport", "Lumbridge"));
		put(map, "Lumbridge cow field",
			option("cowbell amulet", "Cow field"),
			option("lumbridge teleport", "Lumbridge"));
		put(map, "Lumbridge Swamp Caves",
			option("lumbridge teleport", "Lumbridge"),
			option("lumbridge graveyard teleport", "Lumbridge Graveyard"));
		put(map, "Mort'ton",
			option("mort ton teleport", "Mort'ton"),
			option("morytania legs 4", "Burgh de Rott"));
		put(map, "Mogre beach",
			option("lunar staff", "Fairy ring AIQ"),
			option("dramen staff", "Fairy ring AIQ"),
			option("ring of dueling", "Emir's Arena"));
		put(map, "Nardah desert",
			option("desert amulet 4", "Nardah"),
			option("nardah teleport", "Nardah"));
		put(map, "Piscatoris area",
			option("piscatoris teleport", "Piscatoris"),
			option("western banner 4", "Piscatoris"));
		put(map, "Poison Waste Dungeon",
			option("eternal teleport crystal", "Prifddinas"),
			option("teleport crystal", "Prifddinas"));
		put(map, "Royal Titans arena",
			option("giantsoul amulet", "Royal Titans"),
			option("lunar staff", "Fairy ring AIQ"),
			option("dramen staff", "Fairy ring AIQ"));
		put(map, "Slayer Tower rooftop",
			option("eternal slayer ring", "Slayer Tower"),
			option("slayer ring", "Slayer Tower"));
		put(map, "Varrock Sewers",
			option("varrock teleport", "Varrock"),
			option("chronicle", "Champions' Guild"));
		put(map, "Waterbirth Island",
			option("waterbirth teleport", "Waterbirth Island"),
			option("fremennik sea boots 4", "Rellekka"),
			option("enchanted lyre", "Rellekka"));
		put(map, "Wilderness",
			option("ring of dueling", "Ferox Enclave"),
			option("games necklace", "Corporeal Beast"),
			option("burning amulet", "Bandit Camp"));
		put(map, "Ferox Enclave",
			option("ring of dueling", "Ferox Enclave"),
			option("games necklace", "Corporeal Beast"));
		put(map, "Wilderness God Wars Dungeon",
			option("ghorrock teleport", "Frozen Waste Plateau"),
			option("games necklace", "Wintertodt Camp"),
			option("burning amulet", "Lava Maze"));
		put(map, "Buccaneers' Laboratory",
			option("max cape", "POH fairy ring CIS"),
			option("construct cape", "POH fairy ring CIS"),
			option("teleport to house", "POH fairy ring CIS"));
		put(map, "Wilderness boss cave",
			option("burning amulet", "Lava Maze"),
			option("games necklace", "Corporeal Beast"),
			option("ring of dueling", "Ferox Enclave"));
		put(map, "Wilderness green dragon area",
			option("burning amulet", "Chaos Temple"),
			option("games necklace", "Corporeal Beast"),
			option("ring of dueling", "Ferox Enclave"));
		put(map, "Zul-Andra",
			option("zul andra teleport", "Zul-Andra"),
			option("lunar staff", "Fairy ring BJS"),
			option("dramen staff", "Fairy ring BJS"));

		/* Turael/Aya point-boost locations. Ordering is fastest owned first. */
		put(map, "Silvarea limestone mine",
			option("digsite pendant", "Digsite"),
			option("ring of the elements", "Earth Altar"),
			option("varrock teleport", "Varrock"));
		put(map, "South-west of Legends' Guild",
			option("lunar staff", "Fairy ring BLR"),
			option("dramen staff", "Fairy ring BLR"),
			option("quest point cape", "Legends' Guild"),
			option("ardougne teleport", "Ardougne"));
		put(map, "West of Champions' Guild",
			option("combat bracelet", "Champions' Guild"),
			option("chronicle", "Champions' Guild"),
			option("varrock teleport", "Varrock"));
		put(map, "North of Nardah",
			option("desert amulet 4", "Nardah"),
			option("nardah teleport", "Nardah"),
			option("lunar staff", "Fairy ring DLQ"),
			option("dramen staff", "Fairy ring DLQ"));
		put(map, "East of Sophanem",
			option("pharaoh s sceptre", "Jaleustrophos"),
			option("lunar staff", "Fairy ring DLQ"),
			option("dramen staff", "Fairy ring DLQ"));
		put(map, "White Wolf Mountain tunnel",
			option("games necklace", "Burthorpe"),
			option("taverley teleport", "Taverley"),
			option("camelot teleport", "Camelot"));
		put(map, "White Wolf Tunnel pub",
			option("games necklace", "Burthorpe"),
			option("taverley teleport", "Taverley"),
			option("camelot teleport", "Camelot"));
		put(map, "East of Lumbridge",
			option("lumbridge teleport", "Lumbridge"));
		put(map, "Karamja glider clearing",
			option("lunar staff", "Fairy ring CKR"),
			option("dramen staff", "Fairy ring CKR"),
			option("royal seed pod", "Grand Tree"),
			option("spirit tree", "Grand Tree"),
			option("amulet of glory", "Karamja"));
		put(map, "Digsite Dungeon",
			option("digsite pendant", "Digsite"),
			option("varrock teleport", "Varrock"));
		put(map, "Outside H.A.M. Hideout",
			option("amulet of glory", "Draynor Village"),
			option("explorer s ring 4", "Cabbage patch"),
			option("lumbridge teleport", "Lumbridge"));
		put(map, "White Wolf Mountain",
			option("games necklace", "Burthorpe"),
			option("camelot teleport", "Camelot"),
			option("taverley teleport", "Taverley"));
		put(map, "Alice's farm",
			option("ectophial", "Ectofuntus"),
			option("morytania legs 4", "Ectofuntus"),
			option("morytania legs 3", "Burgh de Rott"));

		/*
		 * Inferno final-preparation staging. These reusable Combat Achievement
		 * items teleport directly into inner Mor Ul Rek near the east/Zuk bank.
		 * SlayerPlus owns this first interaction because current Shortest Path data
		 * does not model the Ghommal -> Mor Ul Rek edge; Shortest Path resumes after
		 * the landing and remains authoritative for the local walk to the banker.
		 */
		put(map, "Mor Ul Rek east bank",
			option("ghommal s hilt 6", "Mor Ul Rek"),
			option("ghommal s avernic defender 6", "Mor Ul Rek"),
			option("ghommal s hilt 5", "Mor Ul Rek"),
			option("ghommal s avernic defender 5", "Mor Ul Rek"),
			option("ghommal s hilt 4", "Mor Ul Rek"));
		return Collections.unmodifiableMap(map);
	}

	private static void put(
		final Map<String, List<Option>> map,
		final String location,
		final Option... values)
	{
		final List<Option> options = new ArrayList<>();
		Collections.addAll(options, values);
		final String key = normalize(location);
		if (key.isEmpty())
		{
			throw new IllegalStateException("Travel fallback location cannot be blank");
		}
		if (map.put(key, Collections.unmodifiableList(options)) != null)
		{
			throw new IllegalStateException(
				"Duplicate travel fallback location: " + location
			);
		}
	}

	private static Option option(final String family, final String destination)
	{
		return new Option(family, destination);
	}

	private static String normalize(final String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
	}
}
