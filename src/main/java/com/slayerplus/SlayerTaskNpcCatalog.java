package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Current Slayer assignment-to-NPC aliases used by RuneLite's Slayer task
 * model. Keeping aliases separate from route coordinates allows every cave
 * route to lock onto the correct loaded encounter instead of guessing from a
 * plural task name. Boss routes still use their own strict profile and never
 * inherit ordinary-monster aliases.
 */
public final class SlayerTaskNpcCatalog
{
	private static final Map<String, Set<String>> ALIASES = createAliases();
	private static final Map<String, Set<String>> STANDARD_EXCLUSIONS =
		createStandardExclusions();

	static
	{
		validateOrThrow();
	}

	private SlayerTaskNpcCatalog()
	{
	}

	public static Set<String> aliasesFor(final String taskName)
	{
		final Set<String> aliases = new LinkedHashSet<>();
		addAlias(aliases, taskName);
		addAlias(aliases, singular(normalize(taskName)));

		final Set<String> reviewed = ALIASES.get(taskKey(taskName));
		if (reviewed != null)
		{
			aliases.addAll(reviewed);
		}
		return aliases;
	}

	public static Set<String> aliasesForStandardRoute(final String taskName)
	{
		final Set<String> aliases = new LinkedHashSet<>(aliasesFor(taskName));
		final Set<String> excluded = STANDARD_EXCLUSIONS.get(taskKey(taskName));
		if (excluded != null)
		{
			aliases.removeAll(excluded);
		}
		return aliases;
	}

	public static boolean hasTask(final String taskName)
	{
		return ALIASES.containsKey(taskKey(taskName));
	}

	/** Structural guard for exact-NPC routing and boss/regular isolation. */
	public static void validateOrThrow()
	{
		for (final Map.Entry<String, Set<String>> entry : ALIASES.entrySet())
		{
			if (entry.getKey() == null || entry.getKey().trim().isEmpty()
				|| entry.getValue() == null || entry.getValue().isEmpty())
			{
				throw new IllegalStateException(
					"Slayer NPC alias catalog contains an incomplete task: "
						+ entry.getKey()
				);
			}
			for (final String alias : entry.getValue())
			{
				if (alias == null || alias.trim().isEmpty())
				{
					throw new IllegalStateException(
						"Slayer NPC alias catalog contains a blank alias: "
							+ entry.getKey()
					);
				}
			}
		}

		for (final Map.Entry<String, Set<String>> entry
			: STANDARD_EXCLUSIONS.entrySet())
		{
			final Set<String> base = aliasesFor(entry.getKey());
			final Set<String> standard = aliasesForStandardRoute(entry.getKey());
			for (final String excluded : entry.getValue())
			{
				if (!base.contains(excluded))
				{
					throw new IllegalStateException(
						"Boss exclusion is not present in the base NPC aliases: "
							+ entry.getKey() + " -> " + excluded
					);
				}
				if (standard.contains(excluded))
				{
					throw new IllegalStateException(
						"Boss NPC leaked into a standard Slayer route: "
							+ entry.getKey() + " -> " + excluded
					);
				}
			}
		}
	}

	private static Map<String, Set<String>> createAliases()
	{
		final Map<String, Set<String>> map = new LinkedHashMap<>();

		put(map, "Aberrant spectres", "Aberrant spectre", "Deviant spectre", "Spectre");
		put(map, "Abyssal demons", "Abyssal demon", "Abyssal Sire");
		put(map, "The Abyssal Sire", "Abyssal Sire");
		put(map, "The Alchemical Hydra", "Alchemical Hydra");
		put(map, "Alchemical Hydra", "Alchemical Hydra");
		put(map, "Amoxliatl", "Amoxliatl");
		put(map, "Artio", "Artio");
		put(map, "Ankou", "Ankou");
		put(map, "Aquanites", "Aquanite");
		put(map, "Araxxor", "Araxxor");
		put(map, "Araxytes", "Araxyte", "Araxxor");
		put(map, "Aviansies", "Aviansie", "Kree'arra", "Flight Kilisa", "Flockleader Geerin", "Wingman Skree");
		put(map, "Bandits", "Bandit", "Black Heather", "Donny the Lad", "Speedy Keith");
		put(map, "Banshees", "Banshee");
		put(map, "Barrows Brothers", "Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested", "Karil the Tainted", "Torag the Corrupted", "Verac the Defiled");
		put(map, "Basilisks", "Basilisk", "Basilisk knight");
		put(map, "Bats", "Bat", "Death wing");
		put(map, "Bears", "Bear", "Callisto", "Artio");
		put(map, "Birds", "Chicken", "Rooster", "Terrorbird", "Seagull", "Vulture", "Duck", "Penguin", "Baby Roc");
		put(map, "Black demons", "Black demon", "Demonic gorilla", "Balfrug Kreeyath", "Skotizo", "Porazdir");
		put(map, "Black dragons", "Black dragon", "King Black Dragon", "Baby black dragon");
		put(map, "Black Knights", "Black Knight");
		put(map, "Bloodveld", "Bloodveld", "Mutated bloodveld");
		put(map, "Bloodvelds", "Bloodveld", "Mutated bloodveld");
		put(map, "Blue dragons", "Blue dragon", "Baby blue dragon", "Vorkath");
		put(map, "Brine rats", "Brine rat");
		put(map, "Callisto", "Callisto");
		put(map, "Calvar'ion", "Calvar'ion");
		put(map, "Catablepon", "Catablepon");
		put(map, "Cave bugs", "Cave bug");
		put(map, "Cave crawlers", "Cave crawler", "Chasm crawler");
		put(map, "Cave horrors", "Cave horror", "Cave abomination");
		put(map, "Cave kraken", "Cave kraken", "Kraken");
		put(map, "Cave slimes", "Cave slime");
		put(map, "Cerberus", "Cerberus");
		put(map, "Chaos druids", "Chaos druid", "Elder Chaos druid");
		put(map, "The Chaos Elemental", "Chaos Elemental");
		put(map, "The Chaos Fanatic", "Chaos Fanatic");
		put(map, "Cockatrice", "Cockatrice", "Cockathrice");
		put(map, "Cows", "Cow", "Buffalo", "Brutus");
		put(map, "Brutus", "Brutus");
		put(map, "Crabs", "Ammonite Crab", "Frost Crab", "King Sand Crab", "Rock Crab", "Giant Rock Crab", "Sand Crab", "Swamp Crab");
		put(map, "Crawling hands", "Crawling hand", "Crushing hand");
		put(map, "Crazy Archaeologists", "Crazy archaeologist");
		put(map, "Crocodiles", "Crocodile");
		put(map, "Custodian Stalkers", "Custodian stalker", "Ancient Custodian");
		put(map, "Dagannoth", "Dagannoth");
		put(map, "Dagannoths", "Dagannoth");
		put(map, "Dagannoth Kings", "Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme");
		put(map, "Demonic gorillas", "Demonic gorilla");
		put(map, "Dark beasts", "Dark beast", "Night beast");
		put(map, "Dark warriors", "Dark warrior");
		put(map, "Deranged Archaeologist", "Deranged archaeologist");
		put(map, "Dogs", "Dog", "Jackal", "Temple Guardian");
		put(map, "Drakes", "Drake");
		put(map, "Duke Sucellus", "Duke Sucellus");
		put(map, "Dust devils", "Dust devil", "Choke devil");
		put(map, "Dwarves", "Dwarf", "Black Guard");
		put(map, "Earth warriors", "Earth warrior");
		put(map, "Elves", "Elf", "Elf warrior", "Elf archer", "Iorwerth Warrior", "Iorwerth Archer");
		put(map, "Ents", "Ent");
		put(map, "Fever spiders", "Fever spider");
		put(map, "Fire giants", "Fire giant", "Branda the Fire Queen");
		put(map, "Fleshcrawlers", "Flesh crawler");
		put(map, "Fossil island wyverns", "Ancient wyvern", "Long-tailed wyvern", "Spitting wyvern", "Taloned wyvern");
		put(map, "Frost dragons", "Frost dragon");
		put(map, "Gargoyles", "Gargoyle", "Dusk", "Dawn");
		put(map, "General Graardor", "General Graardor");
		put(map, "Ghosts", "Ghost", "Death wing", "Tortured soul", "Forgotten Soul", "Revenant");
		put(map, "Ghouls", "Ghoul");
		put(map, "The Giant Mole", "Giant Mole");
		put(map, "Goblins", "Goblin", "Sergeant Strongstack", "Sergeant Grimspike", "Sergeant Steelwill");
		put(map, "Greater demons", "Greater demon", "K'ril Tsutsaroth", "Tstanon Karlak", "Skotizo", "Tormented Demon");
		put(map, "Green dragons", "Green dragon", "Baby green dragon", "Elvarg");
		put(map, "The Grotesque Guardians", "Dusk", "Dawn");
		put(map, "Gryphons", "Gryphon", "Shellbane Gryphon");
		put(map, "Harpie bug swarms", "Harpie Bug Swarm");
		put(map, "Hellhounds", "Hellhound", "Cerberus");
		put(map, "Hill giants", "Hill giant", "Cyclops", "Reanimated giant", "Obor");
		put(map, "Hobgoblins", "Hobgoblin");
		put(map, "Hydras", "Hydra", "Alchemical Hydra");
		put(map, "Icefiends", "Icefiend");
		put(map, "Ice giants", "Ice giant", "Eldric the Ice King");
		put(map, "Ice warriors", "Ice warrior", "Icelord");
		put(map, "Infernal mages", "Infernal mage", "Malevolent mage");
		put(map, "TzTok-Jad", "TzTok-Jad");
		put(map, "Jellies", "Jelly");
		put(map, "Jungle horrors", "Jungle horror");
		put(map, "Kalphites", "Kalphite worker", "Kalphite soldier", "Kalphite guardian", "Kalphite Queen");
		put(map, "The Kalphite Queen", "Kalphite Queen");
		put(map, "Killerwatts", "Killerwatt");
		put(map, "The King Black Dragon", "King Black Dragon");
		put(map, "The Cave Kraken Boss", "Kraken");
		put(map, "Kree'arra", "Kree'arra");
		put(map, "K'ril Tsutsaroth", "K'ril Tsutsaroth");
		put(map, "Kurask", "Kurask");
		put(map, "Kurasks", "Kurask");
		put(map, "Lava Dragons", "Lava dragon");
		put(map, "Lesser demons", "Lesser demon", "Zakl'n Gritch");
		put(map, "Lesser Nagua", "Lesser Nagua", "Sulphur Nagua", "Frost Nagua", "Amoxliatl");
		put(map, "Lizardmen", "Lizardman");
		put(map, "Lizards", "Lizard", "Desert lizard", "Small lizard");
		put(map, "The Maggot King", "Maggot King");
		put(map, "Magic axes", "Magic axe");
		put(map, "Mammoths", "Mammoth");
		put(map, "Metal dragons", "Bronze dragon", "Iron Dragon", "Steel dragon", "Mithril dragon", "Adamant dragon", "Rune dragon");
		put(map, "Minotaurs", "Minotaur");
		put(map, "Mogres", "Mogre");
		put(map, "Molanisks", "Molanisk");
		put(map, "Monkeys", "Monkey", "Tortured gorilla", "Demonic gorilla", "Padulah");
		put(map, "Moss giants", "Moss giant", "Bryophyta");
		put(map, "Mutated zygomites", "Mutated zygomite", "Zygomite", "Fungi");
		put(map, "Nechryael", "Nechryael", "Nechryarch");
		put(map, "Nechryaels", "Nechryael", "Nechryarch");
		put(map, "Ogres", "Ogre", "Enclave guard", "Mogre", "Ogress", "Skogre", "Zogre");
		put(map, "Otherworldly beings", "Otherworldly being");
		put(map, "The Phantom Muspah", "Phantom Muspah");
		put(map, "Pirates", "Pirate");
		put(map, "Pyrefiends", "Pyrefiend", "Flaming pyrelord");
		put(map, "Rats", "Rat");
		put(map, "Red dragons", "Red dragon", "Baby red dragon");
		put(map, "Revenants", "Revenant");
		put(map, "Rockslugs", "Rockslug");
		put(map, "Rogues", "Rogue");
		put(map, "Sarachnis", "Sarachnis");
		put(map, "Scabarites", "Scarab swarm", "Locust rider", "Scarab mage", "Small Scarab");
		put(map, "Scorpia", "Scorpia");
		put(map, "Scurrius", "Scurrius");
		put(map, "Skotizo", "Skotizo");
		put(map, "Spindel", "Spindel");
		put(map, "Scorpions", "Scorpion", "Scorpia", "Lobstrosity");
		put(map, "Sea snakes", "Sea snake young", "Sea snake hatchling");
		put(map, "Shades", "Shade", "Loar", "Phrin", "Riyl", "Asyn", "Fiyr", "Urium");
		put(map, "Shadow warriors", "Shadow warrior");
		put(map, "The Shellbane Gryphon", "Shellbane Gryphon");
		put(map, "Royal Titans", "Branda the Fire Queen", "Eldric the Ice King");
		put(map, "Obor", "Obor");
		put(map, "Bryophyta", "Bryophyta");
		put(map, "Shellbane Gryphon", "Shellbane Gryphon");
		put(map, "Skeletal wyverns", "Skeletal wyvern");
		put(map, "Skeletons", "Skeleton", "Vet'ion", "Calvar'ion", "Skeletal Mystic");
		put(map, "Smoke devils", "Smoke devil");
		put(map, "Sourhogs", "Sourhog");
		put(map, "Spiders", "Spider", "Kalrag", "Sarachnis", "Venenatis", "Spindel", "Araxxor", "Araxyte");
		put(map, "Spiritual creatures", "Spiritual ranger", "Spiritual mage", "Spiritual warrior");
		put(map, "Suqahs", "Suqah");
		put(map, "Terror dogs", "Terror dog");
		put(map, "The Leviathan", "The Leviathan", "Leviathan");
		put(map, "The Whisperer", "The Whisperer", "Whisperer");
		put(map, "The Thermonuclear Smoke Devil", "Thermonuclear smoke devil");
		put(map, "Thermonuclear smoke devil", "Thermonuclear smoke devil");
		put(map, "Trolls", "Troll", "Mountain troll", "Troll general", "Thrower troll", "Dad", "Arrg", "Stick", "Kraka", "Pee Hat", "Rock", "Twig", "Berry");
		put(map, "Tormented demons", "Tormented demon");
		put(map, "Turoth", "Turoth");
		put(map, "Turoths", "Turoth");
		put(map, "Tzhaar", "TzHaar", "TzTok-Jad", "TzKal-Zuk");
		put(map, "Vampyres", "Vampyre", "Vyrewatch", "Vyrewatch sentinel");
		put(map, "Vardorvis", "Vardorvis");
		put(map, "Venators", "Venator");
		put(map, "Venenatis", "Venenatis");
		put(map, "Vet'ion", "Vet'ion");
		put(map, "Vorkath", "Vorkath");
		put(map, "Wall beasts", "Wall beast");
		put(map, "Warped Creatures", "Warped terrorbird", "Warped tortoise", "Mutated terrorbird", "Mutated tortoise");
		put(map, "Waterfiends", "Waterfiend");
		put(map, "Werewolves", "Werewolf");
		put(map, "Wolves", "Wolf");
		put(map, "Wyrms", "Wyrm", "Wyrmling", "Strykewyrm");
		put(map, "Commander Zilyana", "Commander Zilyana");
		put(map, "Zombies", "Zombie", "Undead", "Vorkath", "Zogre");
		put(map, "TzKal-Zuk", "TzKal-Zuk");
		put(map, "Zulrah", "Zulrah");

		return Collections.unmodifiableMap(map);
	}

	private static Map<String, Set<String>> createStandardExclusions()
	{
		final Map<String, Set<String>> map = new LinkedHashMap<>();
		putExclusions(map, "Abyssal demons", "Abyssal Sire");
		putExclusions(map, "Araxytes", "Araxxor");
		putExclusions(map, "Aviansies", "Kree'arra", "Flight Kilisa", "Flockleader Geerin", "Wingman Skree");
		putExclusions(map, "Bears", "Callisto", "Artio");
		putExclusions(map, "Black demons", "Skotizo", "Demonic gorilla");
		putExclusions(map, "Black dragons", "King Black Dragon");
		putExclusions(map, "Blue dragons", "Vorkath");
		putExclusions(map, "Cave kraken", "Kraken");
		putExclusions(map, "Gargoyles", "Dusk", "Dawn");
		putExclusions(map, "Gryphons", "Shellbane Gryphon");
		putExclusions(map, "Greater demons", "K'ril Tsutsaroth", "Skotizo", "Tormented Demon");
		putExclusions(map, "Hellhounds", "Cerberus");
		putExclusions(map, "Hill giants", "Obor");
		putExclusions(map, "Hydras", "Alchemical Hydra");
		putExclusions(map, "Tzhaar", "TzTok-Jad", "TzKal-Zuk");
		putExclusions(map, "Ice giants", "Eldric the Ice King");
		putExclusions(map, "Kalphites", "Kalphite Queen");
		putExclusions(map, "Lesser Nagua", "Amoxliatl");
		putExclusions(map, "Moss giants", "Bryophyta");
		putExclusions(map, "Cows", "Brutus");
		putExclusions(map, "Monkeys", "Demonic gorilla");
		putExclusions(map, "Fire giants", "Branda the Fire Queen");
		putExclusions(map, "Scorpions", "Scorpia");
		putExclusions(map, "Skeletons", "Vet'ion", "Calvar'ion");
		putExclusions(map, "Spiders", "Sarachnis", "Venenatis", "Spindel", "Araxxor");
		putExclusions(map, "Zombies", "Vorkath");
		return Collections.unmodifiableMap(map);
	}

	private static void putExclusions(
		final Map<String, Set<String>> map,
		final String taskName,
		final String... npcNames)
	{
		final Set<String> excluded = new LinkedHashSet<>();
		if (npcNames != null)
		{
			for (final String npcName : npcNames)
			{
				addAlias(excluded, npcName);
			}
		}
		final String key = taskKey(taskName);
		final Set<String> immutable = Collections.unmodifiableSet(excluded);
		final Set<String> previous = map.put(key, immutable);
		if (previous != null && !previous.equals(immutable))
		{
			throw new IllegalStateException(
				"Conflicting duplicate Slayer NPC exclusion entry: " + taskName
			);
		}
	}

	private static void put(
		final Map<String, Set<String>> map,
		final String taskName,
		final String... npcNames)
	{
		final Set<String> aliases = new LinkedHashSet<>();
		if (npcNames != null)
		{
			for (final String npcName : npcNames)
			{
				addAlias(aliases, npcName);
			}
		}
		final String key = taskKey(taskName);
		final Set<String> immutable = Collections.unmodifiableSet(aliases);
		final Set<String> previous = map.put(key, immutable);
		if (previous != null && !previous.equals(immutable))
		{
			throw new IllegalStateException(
				"Conflicting duplicate Slayer NPC alias entry: " + taskName
			);
		}
	}

	private static void addAlias(
		final Set<String> aliases,
		final String value)
	{
		final String normalized = normalize(value);
		if (!normalized.isEmpty())
		{
			aliases.add(normalized);
			aliases.add(singular(normalized));
		}
	}

	private static String taskKey(final String value)
	{
		String normalized = singular(normalize(value));
		if (normalized.startsWith("the "))
		{
			normalized = normalized.substring(4);
		}
		return normalized;
	}

	private static String normalize(final String value)
	{
		if (value == null)
		{
			return "";
		}
		return value
			.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceAll("\\s+", " ");
	}

	private static String singular(final String value)
	{
		if (value == null || value.isEmpty())
		{
			return "";
		}
		if (value.endsWith("wolves"))
		{
			return value.substring(0, value.length() - 6) + "wolf";
		}
		if (value.endsWith("elves"))
		{
			return value.substring(0, value.length() - 5) + "elf";
		}
		if (value.endsWith("dwarves"))
		{
			return value.substring(0, value.length() - 7) + "dwarf";
		}
		if (value.endsWith("men"))
		{
			return value.substring(0, value.length() - 3) + "man";
		}
		if (value.endsWith("ies") && value.length() > 4)
		{
			return value.substring(0, value.length() - 3) + "y";
		}
		if (value.endsWith("s")
			&& !value.endsWith("ss")
			&& !value.endsWith("us")
			&& !value.endsWith("is")
			&& value.length() > 3)
		{
			return value.substring(0, value.length() - 1);
		}
		return value;
	}
}
