package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Point-boost-only destinations and trip requirements for Turael/Aya's current
 * assignment pool.  Keeping this separate prevents a fast boost task from
 * inheriting a boss alternative or a long-trip inventory intended for normal
 * Slayer.
 */
final class SlayerTuraelBoostCatalog
{
	static final class Entry
	{
		private final String location;
		private final String travel;
		private final String cannon;
		private final String requirements;

		private Entry(
			final String location,
			final String travel,
			final String cannon,
			final String requirements)
		{
			this.location = location;
			this.travel = travel;
			this.cannon = cannon;
			this.requirements = requirements;
		}

		String getLocation() { return location; }
		String getTravel() { return travel; }
		String getCannon() { return cannon; }
		String getRequirements() { return requirements; }

		boolean supportsCannon()
		{
			final String value = normalize(cannon);
			return !value.equals("not allowed") && !value.equals("not needed");
		}
	}

	private static final Map<String, Entry> ENTRIES = createEntries();

	private SlayerTuraelBoostCatalog() { }

	static Entry find(final String taskName)
	{
		return ENTRIES.get(normalize(taskName));
	}

	static boolean isBoostProfile(final String taskName, final String location)
	{
		final Entry entry = find(taskName);
		return entry != null && normalize(entry.location).equals(normalize(location));
	}

	static int sizeForRegression()
	{
		return ENTRIES.size();
	}

	private static Map<String, Entry> createEntries()
	{
		final Map<String, Entry> map = new LinkedHashMap<>();
		put(map, "Banshees", "Slayer Tower", "Slayer ring to Slayer Tower; fairy ring CKS fallback", "Not allowed", "15 Slayer, earmuffs or a Slayer helmet, and Priest in Peril");
		put(map, "Bats", "Silvarea limestone mine", "Digsite pendant to the Digsite; Ring of the elements to Earth Altar fallback", "Recommended", "5 combat");
		put(map, "Bears", "South-west of Legends' Guild", "Fairy ring BLR; Ardougne teleport fallback", "Recommended", "13 combat");
		put(map, "Birds", "West of Champions' Guild", "Combat bracelet to Champions' Guild", "Recommended", "None");
		put(map, "Cave bugs", "Dorgesh-Kaan South Dungeon", "Fairy ring AJQ", "Not allowed", "7 Slayer, Death to the Dorgeshuun, and a safe light source");
		put(map, "Cave crawlers", "Fremennik Slayer Dungeon", "Fairy ring AJR; Slayer ring fallback", "Not allowed", "10 Slayer, 10 combat, and poison protection");
		put(map, "Cave slimes", "Dorgesh-Kaan South Dungeon", "Fairy ring AJQ", "Not allowed", "17 Slayer, 15 combat, Death to the Dorgeshuun, a safe light source, and poison protection");
		put(map, "Cows", "Lumbridge cow field", "Lumbridge teleport", "Not needed", "None");
		put(map, "Crawling hands", "Slayer Tower", "Slayer ring to Slayer Tower; fairy ring CKS fallback", "Not allowed", "5 Slayer and Priest in Peril");
		put(map, "Dogs", "East of Sophanem", "Pharaoh's sceptre to Jaleustrophos; fairy ring DLQ fallback", "Recommended", "15 combat");
		put(map, "Dwarves", "White Wolf Tunnel pub", "Games necklace to Burthorpe; Taverley teleport fallback", "Not allowed", "6 combat");
		put(map, "Ghosts", "Catacombs of Kourend", "Xeric's talisman to Xeric's Heart", "Not allowed", "13 combat");
		put(map, "Goblins", "East of Lumbridge", "Lumbridge teleport", "Not needed", "None");
		put(map, "Icefiends", "Ice Mountain", "Combat bracelet to the Monastery; Falador teleport fallback", "Not allowed", "20 combat");
		put(map, "Kalphites", "Kalphite Slayer Cave", "Desert amulet 4 to Kalphite Cave; fairy ring BIQ fallback", "Recommended", "15 combat and a Slayer assignment for the task-only cave");
		put(map, "Lizards", "North of Nardah", "Fairy ring DLQ; Desert amulet to Nardah fallback", "Not allowed", "22 Slayer, ice coolers, and desert heat protection");
		put(map, "Minotaurs", "Stronghold of Security", "Skull sceptre to the Stronghold; amulet of glory to Edgeville fallback", "Recommended", "7 combat");
		put(map, "Monkeys", "Karamja glider clearing", "Fairy ring CKR; gnome glider or amulet of glory fallback", "Recommended", "None");
		put(map, "Rats", "Varrock Sewers", "Varrock teleport", "Recommended", "None");
		put(map, "Scorpions", "Al Kharid mine", "Ring of dueling to Emir's Arena", "Recommended", "7 combat");
		put(map, "Skeletons", "Digsite Dungeon", "Digsite pendant to the Digsite", "Recommended", "15 combat, Dig Site access, and a rope for the north-west winch");
		put(map, "Spiders", "Outside H.A.M. Hideout", "Draynor Village teleport; Lumbridge teleport fallback", "Recommended", "None");
		put(map, "Wolves", "White Wolf Mountain", "Games necklace to Burthorpe; Camelot teleport fallback", "Recommended", "20 combat");
		put(map, "Zombies", "Alice's farm", "Ectophial to the Ectofuntus", "Recommended", "10 combat");
		/* Do not rely on dropping the final 's' for irregular RuneLite task text. */
		map.put(normalize("Dwarf"), map.get(normalize("Dwarves")));
		map.put(normalize("Wolf"), map.get(normalize("Wolves")));
		return Collections.unmodifiableMap(map);
	}

	private static void put(
		final Map<String, Entry> map,
		final String task,
		final String location,
		final String travel,
		final String cannon,
		final String requirements)
	{
		final Entry entry = new Entry(location, travel, cannon, requirements);
		map.put(normalize(task), entry);
		/* RuneLite has used both singular and plural task text over time. */
		if (normalize(task).endsWith("s"))
		{
			map.put(normalize(task).replaceFirst("s$", ""), entry);
		}
	}

	private static String normalize(final String value)
	{
		return value == null ? "" : value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
	}
}
