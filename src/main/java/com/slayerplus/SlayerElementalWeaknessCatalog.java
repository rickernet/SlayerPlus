package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Current OSRS Wiki elemental weaknesses for ordinary Slayer assignments.
 *
 * <p>These entries create an explicit Magic-preference branch. They do not
 * replace an established melee/ranged Automatic method merely because an
 * infobox lists a weakness; Automatic changes are authored separately where
 * the task guide calls the elemental method the practical fastest method.</p>
 */
final class SlayerElementalWeaknessCatalog
{
	private enum Element
	{
		AIR("Wind"), EARTH("Earth"), FIRE("Fire"), WATER("Water");

		private final String spellLabel;

		Element(final String spellLabel)
		{
			this.spellLabel = spellLabel;
		}
	}

	static final class Entry
	{
		private final Element element;
		private final int percent;

		private Entry(final Element element, final int percent)
		{
			this.element = element;
			this.percent = percent;
		}

		String getSpellLabel() { return element.spellLabel; }
		int getPercent() { return percent; }
	}

	private static final Map<String, Entry> ENTRIES = createEntries();

	private SlayerElementalWeaknessCatalog() { }

	static Entry find(final String taskName)
	{
		return ENTRIES.get(normalize(taskName));
	}

	static SlayerTaskStrategy preferenceStrategy(
		final String taskName,
		final boolean wilderness)
	{
		final Entry entry = find(taskName);
		if (entry == null)
		{
			return null;
		}

		final SlayerTaskStrategy.Builder builder = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.MAGIC,
			"Use the best " + entry.element.spellLabel + " spell against the Wiki-listed "
				+ entry.percent + "% " + entry.element.spellLabel + " weakness"
		)
			.armourFocus(SlayerTaskStrategy.ArmourFocus.DAMAGE)
			.costPolicy(wilderness
				? SlayerTaskStrategy.CostPolicy.LOW_RISK
				: SlayerTaskStrategy.CostPolicy.EFFICIENT)
			.weapons(staffPriorities(entry.element))
			.runePouch(true)
			.strictWeaponProfile(true)
			.prayerPotionSlots(3)
			.tags(SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE)
			.reviewed(SlayerTaskResearchCatalog.REVIEW_DATE);
		if (wilderness)
		{
			builder.tags(
				SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE,
				SlayerTaskStrategy.MethodTag.WILDERNESS
			);
		}
		return builder.build();
	}

	static int sizeForRegression()
	{
		return ENTRIES.size();
	}

	private static String[] staffPriorities(final Element element)
	{
		switch (element)
		{
			case WATER:
				return new String[]{
					"harmonised nightmare staff", "kodai wand", "mist battlestaff",
					"water battlestaff", "mystic water staff", "staff of water"
				};
			case FIRE:
				return new String[]{
					"twinflame staff", "harmonised nightmare staff", "smoke battlestaff",
					"lava battlestaff", "fire battlestaff", "mystic fire staff", "staff of fire"
				};
			case EARTH:
				return new String[]{
					"harmonised nightmare staff", "staff of the dead", "mud battlestaff",
					"earth battlestaff", "mystic earth staff", "staff of earth"
				};
			case AIR:
			default:
				return new String[]{
					"twinflame staff", "harmonised nightmare staff", "smoke battlestaff",
					"air battlestaff", "mystic air staff", "staff of air"
				};
		}
	}

	private static Map<String, Entry> createEntries()
	{
		final Map<String, Entry> map = new LinkedHashMap<>();
		put(map, Element.AIR, 50, "Aberrant spectres", "Ghosts");
		put(map, Element.AIR, 45, "Aviansies");
		put(map, Element.AIR, 40, "Ankou", "Shades");
		put(map, Element.AIR, 35, "Bats", "Otherworldly beings");
		put(map, Element.AIR, 30, "Banshees");
		put(map, Element.AIR, 60, "Killerwatts");

		put(map, Element.EARTH, 100, "Waterfiends");
		put(map, Element.EARTH, 60, "Dark beasts", "Molanisks");
		put(map, Element.EARTH, 50, "Cave slimes", "Giant Mole", "Wyrms");
		put(map, Element.EARTH, 40, "Basilisks", "Crocodiles", "Gargoyles", "Hydras");
		put(map, Element.EARTH, 35, "Dagannoth", "Jellies", "Skeletons");
		put(map, Element.EARTH, 25, "Hill giants", "Rockslugs");
		put(map, Element.EARTH, 20, "Crabs", "Mogres", "Ogres", "Suqahs");

		put(map, Element.FIRE, 100, "Frost dragons", "Ice giants", "Ice warriors", "Icefiends");
		put(map, Element.FIRE, 50, "Cave bugs", "Harpie bug swarms", "Moss giants", "Spiders", "Zombies");
		put(map, Element.FIRE, 40, "Ents", "Mutated zygomites");
		put(map, Element.FIRE, 30, "Cave horrors");
		put(map, Element.FIRE, 25, "Fever spiders", "Jungle horrors", "Scorpions", "Skeletal wyverns", "Wolves");
		put(map, Element.FIRE, 20, "Fleshcrawlers");

		put(map, Element.WATER, 100, "Fire giants", "Pyrefiends");
		put(map, Element.WATER, 50, "Black dragons", "Blue dragons", "Drakes", "Green dragons", "Hellhounds", "Red dragons");
		put(map, Element.WATER, 40, "Black demons", "Greater demons", "Lesser demons");
		return Collections.unmodifiableMap(map);
	}

	private static void put(
		final Map<String, Entry> map,
		final Element element,
		final int percent,
		final String... taskNames)
	{
		for (final String taskName : taskNames)
		{
			final String key = normalize(taskName);
			if (map.put(key, new Entry(element, percent)) != null)
			{
				throw new IllegalStateException("Duplicate elemental weakness audit: " + taskName);
			}
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
