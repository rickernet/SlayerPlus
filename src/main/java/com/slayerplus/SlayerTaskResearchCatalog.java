package com.slayerplus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
/**
 * Research approval registry for SlayerPlus strategy profiles.
 *
 * Every entry in this file corresponds to an individually authored profile in
 * SlayerTaskStrategyCatalog.  Keeping approval separate from strategy code
 * prevents an unreviewed broad fallback from silently becoming a recommendation.
 */
public final class SlayerTaskResearchCatalog
{
	public static final String REVIEW_DATE = "2026-08-13";
	private static final Map<String, Entry> ENTRIES = createEntries();

	private SlayerTaskResearchCatalog() { }

	public static Entry find(final String taskName)
	{
		return ENTRIES.get(normalize(taskName));
	}

	public static Set<String> getReviewedTaskNames()
	{
		final Set<String> names = new LinkedHashSet<>();
		for (final Entry entry : ENTRIES.values())
		{
			names.add(entry.getTaskName());
		}
		return Collections.unmodifiableSet(names);
	}

	public static Set<String> getReviewedTaskKeys()
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(ENTRIES.keySet()));
	}

	/**
	 * Compatibility view used by the startup strategy validator.
	 */
	public static java.util.Collection<Entry> getEntries()
	{
		return Collections.unmodifiableCollection(ENTRIES.values());
	}

	private static Map<String, Entry> createEntries()
	{
		final Map<String, Entry> entries = new LinkedHashMap<>();
		put(entries, "Aberrant spectres", false);
		put(entries, "Abyssal demons", true);
		put(entries, "The Abyssal Sire", false);
		put(entries, "The Alchemical Hydra", false);
		put(entries, "Amoxliatl", false);
		put(entries, "Ankou", true);
		put(entries, "Aquanites", false);
		put(entries, "Araxxor", false);
		put(entries, "Araxytes", false);
		put(entries, "Artio", true);
		put(entries, "Aviansies", true);
		put(entries, "Bandits", true);
		put(entries, "Banshees", false);
		put(entries, "Barrows Brothers", false);
		put(entries, "Basilisks", false);
		put(entries, "Bats", false);
		put(entries, "Bears", true);
		put(entries, "Birds", false);
		put(entries, "Black demons", true);
		put(entries, "Black dragons", true);
		put(entries, "Black Knights", true);
		put(entries, "Bloodveld", true);
		put(entries, "Blue dragons", false);
		put(entries, "Brine rats", false);
		put(entries, "Callisto", true);
		put(entries, "Catablepon", false);
		put(entries, "Cave bugs", false);
		put(entries, "Cave crawlers", false);
		put(entries, "Cave horrors", false);
		put(entries, "Cave kraken", false);
		put(entries, "Cave slimes", false);
		put(entries, "Cerberus", false);
		put(entries, "Chaos druids", true);
		put(entries, "The Chaos Elemental", true);
		put(entries, "The Chaos Fanatic", true);
		put(entries, "Cockatrice", false);
		put(entries, "Cows", false);
		put(entries, "Crabs", false);
		put(entries, "Crawling hands", false);
		put(entries, "Crazy Archaeologist", true);
		put(entries, "Crazy Archaeologists", true);
		put(entries, "Crocodiles", false);
		put(entries, "Custodian Stalkers", false);
		put(entries, "Dagannoth", false);
		put(entries, "Dagannoth Kings", false);
		put(entries, "Dark beasts", false);
		put(entries, "Dark warriors", true);
		put(entries, "Deranged Archaeologist", false);
		put(entries, "Dogs", false);
		put(entries, "Drakes", false);
		put(entries, "Duke Sucellus", false);
		put(entries, "Dust devils", true);
		put(entries, "Dwarves", false);
		put(entries, "Earth warriors", true);
		put(entries, "Elves", false);
		put(entries, "Ents", true);
		put(entries, "Fever spiders", false);
		put(entries, "Fire giants", true);
		put(entries, "Fleshcrawlers", false);
		put(entries, "Fossil Island wyverns", false);
		put(entries, "Frost dragons", false);
		put(entries, "Gargoyles", false);
		put(entries, "General Graardor", false);
		put(entries, "Ghosts", false);
		put(entries, "Ghouls", false);
		put(entries, "The Giant Mole", false);
		put(entries, "Goblins", false);
		put(entries, "Greater demons", true);
		put(entries, "Green dragons", true);
		put(entries, "The Grotesque Guardians", false);
		put(entries, "Gryphons", false);
		put(entries, "Harpie bug swarms", false);
		put(entries, "Hellhounds", true);
		put(entries, "Hill giants", true);
		put(entries, "Hobgoblins", false);
		put(entries, "Hydras", false);
		put(entries, "Icefiends", false);
		put(entries, "Ice giants", true);
		put(entries, "Ice warriors", true);
		put(entries, "Infernal mages", false);
		put(entries, "TzTok-Jad", false);
		put(entries, "Jellies", true);
		put(entries, "Jungle horrors", false);
		put(entries, "Kalphites", false);
		put(entries, "The Kalphite Queen", false);
		put(entries, "Killerwatts", false);
		put(entries, "The King Black Dragon", false);
		put(entries, "The Cave Kraken Boss", false);
		put(entries, "Kraken", false);
		put(entries, "Kree'arra", false);
		put(entries, "K'ril Tsutsaroth", false);
		put(entries, "Kurask", false);
		put(entries, "Lava dragons", true);
		put(entries, "Lesser demons", true);
		put(entries, "Lesser nagua", false);
		put(entries, "Lizardmen", false);
		put(entries, "Lizards", false);
		put(entries, "The Maggot King", false);
		put(entries, "Magic axes", true);
		put(entries, "Mammoths", true);
		put(entries, "Metal dragons", false);
		put(entries, "Minotaurs", false);
		put(entries, "Mogres", false);
		put(entries, "Molanisks", false);
		put(entries, "Monkeys", false);
		put(entries, "Moss giants", true);
		put(entries, "Mutated zygomites", false);
		put(entries, "Nechryael", true);
		put(entries, "Ogres", false);
		put(entries, "Otherworldly beings", false);
		put(entries, "The Phantom Muspah", false);
		put(entries, "Pirates", true);
		put(entries, "Pyrefiends", false);
		put(entries, "Rats", false);
		put(entries, "Red dragons", false);
		put(entries, "Revenants", true);
		put(entries, "Rockslugs", false);
		put(entries, "Rogues", true);
		put(entries, "Sarachnis", false);
		put(entries, "Scabarites", false);
		put(entries, "Scorpia", true);
		put(entries, "Scorpions", true);
		put(entries, "Skotizo", false);
		put(entries, "Sea snakes", false);
		put(entries, "Shades", false);
		put(entries, "Shadow warriors", false);
		put(entries, "The Shellbane Gryphon", false);
		put(entries, "Skeletal wyverns", false);
		put(entries, "Skeletons", true);
		put(entries, "Smoke devils", false);
		put(entries, "Sourhogs", false);
		put(entries, "Spiders", true);
		put(entries, "Spindel", true);
		put(entries, "Spiritual creatures", true);
		put(entries, "Suqahs", false);
		put(entries, "Terror dogs", false);
		put(entries, "The Leviathan", false);
		put(entries, "The Whisperer", false);
		put(entries, "The Thermonuclear Smoke Devil", false);
		put(entries, "Trolls", false);
		put(entries, "Turoth", false);
		put(entries, "TzHaar", false);
		put(entries, "Vampyres", false);
		put(entries, "Vardorvis", false);
		put(entries, "Calvar'ion", true);
		put(entries, "Venators", false);
		put(entries, "Venenatis", true);
		put(entries, "Vet'ion", true);
		put(entries, "Vorkath", false);
		put(entries, "Scurrius", false);
		put(entries, "Obor", false);
		put(entries, "Bryophyta", false);
		put(entries, "Brutus", false);
		put(entries, "Demonic gorillas", false);
		put(entries, "Tormented demons", false);
		put(entries, "Royal Titans", false);
		put(entries, "Wall beasts", false);
		put(entries, "Warped creatures", false);
		put(entries, "Waterfiends", false);
		put(entries, "Werewolves", false);
		put(entries, "Wolves", false);
		put(entries, "Wyrms", false);
		put(entries, "Commander Zilyana", false);
		put(entries, "Zombies", true);
		put(entries, "TzKal-Zuk", false);
		put(entries, "Zulrah", false);
		return Collections.unmodifiableMap(entries);
	}

	private static void put(final Map<String, Entry> entries, final String taskName, final boolean wildernessReviewed)
	{
		final String key = normalize(taskName);
		if (entries.put(key, new Entry(taskName, REVIEW_DATE, wildernessReviewed)) != null)
		{
			throw new IllegalStateException("Duplicate Slayer research entry: " + taskName);
		}
	}

	private static String normalize(final String value)
	{
		if (value == null) return "";
		return value.toLowerCase(Locale.ENGLISH).replace('\u2019', '\'').replaceAll("[^a-z0-9]+", " ").trim().replaceFirst("^the\\s+", "");
	}

	public static final class Entry
	{
		private final String taskName;
		private final String reviewDate;
		private final boolean wildernessReviewed;

		private Entry(final String taskName, final String reviewDate, final boolean wildernessReviewed)
		{
			this.taskName = taskName == null ? "" : taskName.trim();
			this.reviewDate = reviewDate == null ? "" : reviewDate.trim();
			this.wildernessReviewed = wildernessReviewed;
		}

		public String getTaskName() { return taskName; }
		public String getReviewDate() { return reviewDate; }
		public boolean isWildernessReviewed() { return wildernessReviewed; }

		/** Validation view backed by the same selectable-location catalog as the UI. */
		public java.util.List<String> getLocations()
		{
			final java.util.Set<String> locations =
				SlayerRecommendationEngine.catalogLocationsForValidation(taskName);
			return locations.isEmpty()
				? java.util.Collections.singletonList("Not restricted")
				: java.util.Collections.unmodifiableList(
					new ArrayList<>(locations)
				);
		}
	}
}
