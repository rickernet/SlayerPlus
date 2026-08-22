package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Wiki-reviewed fallback destination for tasks without a richer location branch. */
final class SlayerTaskTravelAuditCatalog
{
	static final class Entry
	{
		private final String location;
		private final String travel;
		private final String cannon;

		private Entry(final String location, final String travel, final String cannon)
		{
			this.location = location;
			this.travel = travel;
			this.cannon = cannon;
		}

		String getLocation() { return location; }
		String getTravel() { return travel; }
		String getCannon() { return cannon; }
	}

	private static final Map<String, Entry> ENTRIES = createEntries();

	private SlayerTaskTravelAuditCatalog() { }

	static Entry find(final String taskName)
	{
		return ENTRIES.get(normalize(taskName));
	}

	static int sizeForRegression()
	{
		return ENTRIES.size();
	}

	private static Map<String, Entry> createEntries()
	{
		final Map<String, Entry> map = new LinkedHashMap<>();
		put(map, "Slayer Tower", "Slayer ring to Slayer Tower", "Not allowed",
			"Aberrant spectres", "Abyssal demons", "Banshees", "Bloodveld",
			"Crawling hands", "Gargoyles", "Infernal mages", "Nechryael");
		put(map, "Fremennik Slayer Dungeon", "Slayer ring to Fremennik Slayer Dungeon", "Not allowed",
			"Basilisks", "Cave crawlers", "Cockatrice", "Jellies", "Kurask",
			"Pyrefiends", "Rockslugs", "Turoth");
		put(map, "Stronghold of Security", "Skull sceptre to the Stronghold of Security", "Not allowed",
			"Catablepon", "Fleshcrawlers", "Minotaurs");
		put(map, "Stronghold Slayer Cave", "Slayer ring to Stronghold Slayer Cave", "Recommended",
			"Bloodveld", "Fire giants", "Hellhounds");
		put(map, "Catacombs of Kourend", "Xeric's talisman to Xeric's Heart", "Not allowed",
			"Ankou", "Black demons", "Dust devils", "Greater demons", "Nechryael",
			"Skeletons", "Zombies");
		put(map, "Karuulm Slayer Dungeon", "Rada's blessing to Mount Karuulm", "Not allowed",
			"Drakes", "Hydras", "Wyrms");
		put(map, "Lumbridge area", "Lumbridge teleport", "Not needed",
			"Bats", "Birds", "Cows", "Crabs", "Goblins", "Rats", "Spiders");
		put(map, "Taverley Dungeon", "Falador or Taverley teleport", "Optional",
			"Black dragons", "Black demons", "Blue dragons", "Hellhounds");
		put(map, "Edgeville Dungeon", "Amulet of glory to Edgeville", "Optional",
			"Chaos druids", "Earth warriors", "Hill giants", "Hobgoblins");
		put(map, "Brimhaven Dungeon", "Brimhaven teleport or amulet of glory to Karamja", "Optional",
			"Fire giants", "Greater demons", "Metal dragons", "Red dragons", "Moss giants");
		put(map, "Kalphite Slayer Cave", "Desert amulet 4 directly to Kalphite Cave; fairy ring BIQ fallback", "Recommended", "Kalphites");
		put(map, "Lighthouse Dungeon", "Games necklace to Barbarian Outpost", "Recommended", "Dagannoth");
		put(map, "Mourner Tunnels", "Slayer ring to Dark Beasts", "Optional", "Dark beasts");
		put(map, "Iorwerth Dungeon", "Teleport crystal to Prifddinas", "Optional", "Elves");
		put(map, "Mos Le'Harmless Cave", "Trouble Brewing teleport, then run east", "Recommended", "Cave horrors");
		put(map, "Kraken Cove", "Piscatoris or fairy-ring route", "Not allowed", "Cave kraken");
		put(map, "Ancient Cavern", "Games necklace to Barbarian Assault, then use the whirlpool", "Not allowed", "Waterfiends", "Metal dragons");
		put(map, "Asgarnian Ice Dungeon", "Falador teleport, then run south", "Not allowed", "Skeletal wyverns");
		put(map, "Wyvern Cave on Fossil Island", "Digsite pendant to Fossil Island", "Not allowed", "Fossil island wyverns");
		put(map, "Karamja Volcano", "Amulet of glory to Karamja", "Not allowed", "Tzhaar");
		put(map, "Darkmeyer", "Drakan's medallion to Darkmeyer", "Not allowed", "Vampyres", "Venators");
		put(map, "Zanaris", "Fairy ring to Zanaris", "Not allowed", "Otherworldly beings", "Mutated zygomites");
		put(map, "Lunar Isle", "Lunar Isle teleport", "Recommended", "Suqahs");
		put(map, "Death Plateau", "Games necklace to Burthorpe", "Recommended", "Trolls");
		put(map, "God Wars Dungeon", "Ghommal's hilt or Trollheim teleport", "Not allowed", "Aviansies", "Spiritual creatures");
		put(map, "Desert quarry", "Desert amulet or Pollnivneach teleport", "Not allowed", "Lizards");
		put(map, "Mogre beach", "Fairy ring AIQ, then run northwest", "Not allowed", "Mogres");
		put(map, "Dorgesh-Kaan South Dungeon", "Dorgesh-Kaan sphere or fairy ring AJQ", "Not allowed", "Cave bugs", "Cave slimes", "Molanisks");
		put(map, "Brine Rat Cavern", "Rellekka teleport, then run northeast", "Not allowed", "Brine rats");
		put(map, "Isle of Souls", "Soul Wars minigame teleport", "Optional", "Dogs", "Sourhogs");
		put(map, "Kourend Woodland", "Rada's blessing to Kourend Woodland", "Optional", "Lizardmen", "Ogres");
		put(map, "Haunted Woods", "Slayer ring to Slayer Tower, then run east", "Not allowed", "Ghouls", "Werewolves");
		put(map, "Waterbirth Island", "Waterbirth teleport", "Not allowed", "Sea snakes");
		put(map, "Piscatoris area", "Piscatoris teleport", "Not allowed", "Terror dogs");
		put(map, "Feldip Hills", "Feldip hills teleport", "Optional", "Jungle horrors", "Wolves");
		put(map, "Kebos Lowlands", "Skills necklace to Farming Guild", "Optional", "Gryphons");
		put(map, "Bandit Camp", "Pollnivneach teleport, then run west", "Not allowed", "Bandits");
		put(map, "Ardougne area", "Ardougne teleport", "Not allowed", "Bears");
		put(map, "Black Knights' Fortress", "Combat bracelet to the Monastery", "Optional", "Black Knights");
		put(map, "Nardah desert", "Desert amulet to Nardah", "Not allowed", "Crocodiles");
		put(map, "Braindeath Island", "Ectophial to Port Phasmatys, then take the pirate route", "Not allowed", "Fever spiders");
		put(map, "Grimstone Dungeon", "Fairy ring DLP after the first Grimstone visit; otherwise sail from Weiss or use a boat moored at Grimstone with a greater teleport focus", "Not allowed", "Frost dragons");
		put(map, "Catacombs of Kourend", "Xeric's talisman to Xeric's Heart", "Not allowed", "Ghosts");
		put(map, "Karamja", "Amulet of glory to Karamja", "Not allowed", "Harpie bug swarms", "Lesser demons");
		put(map, "Asgarnian Ice Dungeon", "Falador teleport, then run south", "Optional", "Ice giants");
		put(map, "Ice Mountain", "Combat bracelet to the Monastery", "Optional", "Ice warriors");
		put(map, "Sophanem Dungeon", "Pharaoh's sceptre to Sophanem", "Optional", "Scabarites");
		put(map, "Mort'ton", "Mort'ton teleport", "Not allowed", "Shades");
		put(map, "Legends' Guild basement", "Fairy ring BLR or quest cape", "Not allowed", "Shadow warriors");
		put(map, "Lumbridge Swamp Caves", "Lumbridge teleport, then enter the swamp caves", "Not allowed", "Wall beasts");
		put(map, "Poison Waste Dungeon", "Teleport crystal to Prifddinas, then travel south", "Recommended", "Warped Creatures");
		put(map, "Ynysdail Cavern", "Use the built Gwenith rowboat when available; otherwise travel to Port Roberts and sail to Ynysdail with an adamant keel or better", "Not allowed", "Aquanites");
		put(map, "Dwarven Mine", "Skills necklace to the Mining Guild", "Not allowed", "Dwarves");
		put(map, "Wilderness green dragon area", "Burning amulet or games necklace, with an emergency teleport", "Optional", "Green dragons");
		put(map, "Ice Mountain", "Combat bracelet to the Monastery", "Not allowed", "Icefiends");
		put(map, "Killerwatt plane", "Fairy ring to Zanaris, then enter the Killerwatt portal", "Not allowed", "Killerwatts");
		put(map, "Karamja", "Amulet of glory to Karamja", "Not allowed", "Monkeys");
		put(map, "Al Kharid mine", "Ring of dueling to Emir's Arena", "Optional", "Scorpions");
		put(map, "Smoke Devil Dungeon", "Ring of dueling to Emir's Arena", "Recommended", "Smoke devils");
		put(map, "Wilderness", "Start from Ferox Enclave and keep an emergency teleport", "Optional",
			"Dark warriors", "Ents", "Lava Dragons", "Magic axes", "Mammoths",
			"Pirates", "Revenants", "Rogues");
		put(map, "Stalker Den", "Pendant of ates to Nemus Retreat, fairy ring AIS to Auburn Valley, or fairy ring BLS to the Kebos side of Custodia Pass", "Recommended", "Custodian Stalkers");
		put(map, "Ruins of Tapoyauik", "Pendant of ates to Tapoyauik", "Not allowed", "Lesser Nagua", "Amoxliatl");
		put(map, "Morytania Spider Cave", "Drakan's medallion, then travel to the Araxyte cave", "Recommended", "Araxytes", "Araxxor");
		put(map, "Shellbane Gryphon Cave", "Fairy ring CJQ to the Great Conch, then run north; charter ship to the Great Conch is the non-fairy-ring alternative", "Not allowed", "The Shellbane Gryphon");
		put(map, "Maggot King's lair", "Drakan's medallion to Castle Drakan, enter Vampyrium, then use the unlocked Aranei scout shortcut east of Sangvesti", "Not allowed", "The Maggot King");

		/* Boss and demi-boss preparation destinations. */
		put(map, "Abyssal Nexus", "Fairy ring DIP lands inside the Abyssal Nexus", "Not allowed", "The Abyssal Sire");
		put(map, "Karuulm Slayer Dungeon", "Rada's blessing to Mount Karuulm", "Not allowed", "The Alchemical Hydra");
		put(map, "Hunter's End", "Ring of dueling to Ferox Enclave; Carrallanger or games necklace fallback", "Not allowed", "Artio");
		put(map, "Callisto's Den", "Annakarl teleport; games necklace to Corporeal Beast fallback", "Not allowed", "Callisto");
		put(map, "Barrows", "Barrows teleport", "Not allowed", "Barrows Brothers");
		put(map, "Cerberus' Lair", "Key master teleport or Taverley route", "Not allowed", "Cerberus");
		put(map, "Deep Wilderness", "Burning amulet or Wilderness obelisk", "Not allowed", "The Chaos Elemental", "The Chaos Fanatic", "Crazy Archaeologist", "Crazy Archaeologists", "Scorpia");
		put(map, "Fossil Island", "Digsite pendant to Fossil Island", "Not allowed", "Deranged Archaeologist");
		put(map, "Waterbirth Island Dungeon", "Waterbirth teleport", "Not allowed", "Dagannoth Kings");
		put(map, "Ghorrock Dungeon", "Ring of shadows with frozen tablet; icy basalt or Weiss portal fallback", "Not allowed", "Duke Sucellus");
		put(map, "God Wars Dungeon", "Ghommal's hilt or Trollheim teleport", "Not allowed", "General Graardor", "Kree'arra", "K'ril Tsutsaroth", "Commander Zilyana");
		put(map, "Falador Mole Lair", "Ring of wealth to Falador Park, then use a spade", "Not allowed", "The Giant Mole");
		put(map, "Slayer Tower rooftop", "Slayer ring to Slayer Tower", "Not allowed", "The Grotesque Guardians");
		put(map, "Kalphite Lair", "Fairy ring BIQ; Desert amulet 4 to Kalphite Cave fallback", "Not allowed", "The Kalphite Queen");
		put(map, "King Black Dragon Lair", "Burning amulet to Lava Maze", "Not allowed", "The King Black Dragon");
		put(map, "Kraken Cove", "Piscatoris or fairy-ring route", "Not allowed", "The Cave Kraken Boss", "Kraken");
		put(map, "Ghorrock Dungeon", "Ring of shadows with frozen tablet; Weiss portal or fairy ring DKS fallback", "Not allowed", "The Phantom Muspah");
		put(map, "The Scar", "Ring of shadows with scarred tablet; amulet of the eye fallback", "Not allowed", "The Leviathan");
		put(map, "Lassar Undercity", "Ring of shadows with sirenic tablet; Mind Altar teleport fallback", "Not allowed", "The Whisperer");
		put(map, "Stranglewood Temple", "Ring of shadows with strangled tablet; Xeric's Honour fallback", "Not allowed", "Vardorvis");
		put(map, "Forthos Dungeon", "Xeric's talisman to Xeric's Glade", "Not allowed", "Sarachnis");
		put(map, "Catacombs of Kourend", "Xeric's talisman to Xeric's Heart", "Not allowed", "Skotizo");
		put(map, "Smoke Devil Dungeon", "Ring of dueling to Emir's Arena", "Not allowed", "The Thermonuclear Smoke Devil");
		put(map, "Varrock Sewers", "Varrock teleport", "Not allowed", "Scurrius", "Bryophyta");
		put(map, "Edgeville Dungeon", "Amulet of glory to Edgeville", "Not allowed", "Obor");
		put(map, "Lumbridge cow field", "Cowbell amulet", "Not allowed", "Brutus");
		put(map, "Crash Site Cavern", "Royal seed pod to the Grand Tree", "Not allowed", "Demonic gorillas");
		put(map, "Ancient Guthixian Temple", "Guthixian temple teleport; games necklace to Tears of Guthix fallback", "Not allowed", "Tormented demons");
		put(map, "Royal Titans arena", "Giantsoul amulet; fairy ring AIQ fallback", "Not allowed", "Royal Titans");
		put(map, "Wilderness boss cave", "Burning amulet or Wilderness obelisk", "Not allowed", "Spindel", "Venenatis", "Calvar'ion", "Vet'ion");
		put(map, "Ungael", "Fremennik sea boots or enchanted lyre to Rellekka", "Not allowed", "Vorkath");
		put(map, "Fight Caves", "Ghommal's hilt or fairy ring BLP", "Not allowed", "TzTok-Jad");
		put(map, "Mor Ul Rek east bank", "Ghommal's hilt to Mor Ul Rek", "Not allowed", "TzKal-Zuk");
		put(map, "Zul-Andra", "Zul-andra teleport", "Not allowed", "Zulrah");

		return Collections.unmodifiableMap(map);
	}

	private static void put(
		final Map<String, Entry> map,
		final String location,
		final String travel,
		final String cannon,
		final String... tasks)
	{
		for (final String task : tasks)
		{
			map.put(normalize(task), new Entry(location, travel, cannon));
		}
	}

	private static String normalize(final String value)
	{
		return value == null ? "" : value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceFirst("^the\\s+", "");
	}
}
