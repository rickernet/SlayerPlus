package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Exact trip-size policy for non-boss, non-cannon and non-barrage assignments. */
final class SlayerRegularInventoryAuditCatalog
{
	private static final Policy DIRECT = new Policy(2, 6, 12, true);
	private static final Policy TRIVIAL = new Policy(0, 2, 20, false);
	private static final Policy SAFESPOT = new Policy(0, 0, 24, true);
	private static final Policy PRAYER = new Policy(5, 0, 16, true);
	private static final Policy PRAYER_LOOT = new Policy(4, 2, 12, true);
	private static final Policy HEAVY = new Policy(3, 8, 8, true);
	private static final Policy SUPPLY_FREE = new Policy(0, 0, 22, false);
	private static final Map<String, Policy> POLICIES = createPolicies();

	private SlayerRegularInventoryAuditCatalog() { }

	static void apply(
		final String taskName,
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		final String task = normalize(taskName);
		final Policy policy = POLICIES.get(task);
		if (policy != null)
		{
			policy.apply(rules, strategy);
			applyTaskSpecificSupplies(task, rules, strategy);
			return;
		}

		/* A newly introduced task fails conservatively until it is audited. */
		final SlayerMethodRules.DamageControl control = strategy == null
			? SlayerMethodRules.DamageControl.DIRECT_DAMAGE
			: strategy.getDamageProfile()
				== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
					? SlayerMethodRules.DamageControl.PRAYER_PROTECTED
					: strategy.getDamageProfile()
						== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING
							? SlayerMethodRules.DamageControl.SAFESPOT
							: SlayerMethodRules.DamageControl.DIRECT_DAMAGE;
		(control == SlayerMethodRules.DamageControl.SAFESPOT
			? SAFESPOT
			: control == SlayerMethodRules.DamageControl.PRAYER_PROTECTED
				? PRAYER
				: DIRECT).apply(rules, strategy);
	}

	static boolean hasPolicy(final String taskName)
	{
		return POLICIES.containsKey(normalize(taskName));
	}

	static int sizeForRegression()
	{
		return POLICIES.size();
	}

	private static void applyTaskSpecificSupplies(
		final String task,
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		if (task.equals("bandits") || task.equals("crocodiles")
			|| task.equals("lizards"))
		{
			rules.requiredItem(
				"Desert heat protection",
				1,
				SlayerMethodRules.InventoryGroup.PROTECTION,
				"circlet of water", "desert amulet 4", "waterskin 4",
				"waterskin 3", "waterskin 2", "waterskin 1"
			);
		}
		if (task.equals("harpie bug swarms"))
		{
			rules.restore("prayer potion", "super restore")
				.restoreSlots(3).foodSlots(6).reservedLootSlots(10)
				.requiredItem("Poison protection", 1,
					SlayerMethodRules.InventoryGroup.PROTECTION,
					"antidote plus plus", "superantipoison", "antipoison")
				.requiredItem("Nature runes", 200,
					SlayerMethodRules.InventoryGroup.RUNES_AMMO,
					"nature rune");
		}
		if (task.equals("spiritual creatures"))
		{
			rules.requiredItem(
				SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
				1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				SlayerPotionPolicy.staminaAlternatives()
			);
		}
		if (task.equals("greater demons"))
		{
			rules.requiredItem(
				"Ash sanctifier",
				1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				"ash sanctifier"
			);
		}
		if (task.equals("basilisks"))
		{
			final boolean ranged = strategy != null
				&& strategy.getCombatStyle()
					== SlayerTaskStrategy.CombatStyle.RANGED;
			rules.restore("prayer potion", "super restore")
				.restoreSlots(6)
				.foodSlots(ranged ? 0 : 6)
				.reservedLootSlots(ranged ? 16 : 9)
				.spell(SlayerMethodRules.Spellbook.STANDARD, "Telekinetic Grab / High Level Alchemy")
				.runePouch(true)
				.pouchRune(net.runelite.api.gameval.ItemID.AIRRUNE, "Air", 500)
				.pouchRune(net.runelite.api.gameval.ItemID.LAWRUNE, "Law", 200)
				.pouchRune(net.runelite.api.gameval.ItemID.NATURERUNE, "Nature", 200);
		}
		if (task.equals("aquanites"))
		{
			/*
			 * The lure must first be severed with Slash before the reduced Stab
			 * defence is exploited. High Alchemy and a seed box extend the trip.
			 */
			rules.restore("prayer potion", "super restore")
				.restoreSlots(5).foodSlots(4).reservedLootSlots(10)
				.spell(SlayerMethodRules.Spellbook.STANDARD, "High Level Alchemy")
				.runePouch(true)
				.pouchRune(net.runelite.api.gameval.ItemID.FIRERUNE, "Fire", 1000)
				.pouchRune(net.runelite.api.gameval.ItemID.NATURERUNE, "Nature", 250)
				.requiredItem("Fast Slash lure-severing weapon", 1,
					SlayerMethodRules.InventoryGroup.SWITCH,
					"saradomin godsword", "dragon claws", "voidwaker",
					"abyssal whip", "blade of saeldor", "dragon scimitar",
					"rune scimitar")
				.requiredItem("Seed box", 1,
					SlayerMethodRules.InventoryGroup.UTILITY, "seed box");
		}
		if (task.equals("skeletal wyvern") || task.equals("skeletal wyverns"))
		{
			final SlayerTaskStrategy.CombatStyle style = strategy == null
				? SlayerTaskStrategy.CombatStyle.MELEE
				: strategy.getCombatStyle();
			final boolean safespot = style == SlayerTaskStrategy.CombatStyle.RANGED
				|| style == SlayerTaskStrategy.CombatStyle.MAGIC;
			rules.restore("prayer potion", "super restore")
				.restoreSlots(safespot ? 2 : 4)
				.foodSlots(safespot ? 6 : 8)
				.reservedLootSlots(safespot ? 16 : 11);

			/*
			 * The reviewed melee setup uses spare special energy to shorten these
			 * high-Defence kills. Keep this method-only: a ranged or Magic safespot
			 * should preserve the slot for the valuable bones and alchable drops.
			 */
			if (style == SlayerTaskStrategy.CombatStyle.MELEE)
			{
				rules.requiredItem("Special attack weapon", 1,
					SlayerMethodRules.InventoryGroup.SWITCH,
					"voidwaker", "dragon claws", "burning claws",
					"dragon dagger p plus plus", "dragon dagger p plus",
					"dragon dagger p", "dragon dagger");
			}

			if (style != SlayerTaskStrategy.CombatStyle.MAGIC)
			{
				rules.spell(SlayerMethodRules.Spellbook.STANDARD, "High Level Alchemy")
					.runePouch(true)
					.pouchRune(net.runelite.api.gameval.ItemID.FIRERUNE, "Fire", 1000)
					.pouchRune(net.runelite.api.gameval.ItemID.NATURERUNE, "Nature", 250);
			}
		}
		if (task.equals("kalphite") || task.equals("kalphites"))
		{
			/*
			 * The reviewed task-only cave method camps Protect from Melee while
			 * the cannon clears the multi-combat soldier pack. Five restores match
			 * the documented full-task setup; food is unnecessary when executed.
			 */
			rules.restore("prayer potion", "super restore")
				.restoreSlots(5)
				.foodSlots(0)
				.reservedLootSlots(9)
				.requiredItem(
					"Poison protection",
					1,
					SlayerMethodRules.InventoryGroup.PROTECTION,
					"antidote plus plus", "sanfew serum",
					"superantipoison", "antipoison"
				)
				.requiredItem(
					"Special attack weapon",
					1,
					SlayerMethodRules.InventoryGroup.SWITCH,
					"dragon dagger p plus plus", "dragon dagger p plus",
					"dragon dagger p", "dragon dagger"
				);
		}
	}

	private static Map<String, Policy> createPolicies()
	{
		final Map<String, Policy> map = new LinkedHashMap<>();

		put(map, PRAYER_LOOT,
			"Aberrant spectres", "Basilisks", "Bloodveld", "Dark beasts",
			"Infernal mages", "Spiritual creatures");
		put(map, new Policy(5, 8, 6, true), "Suqahs");
		put(map, HEAVY,
			"Aquanites", "Drakes", "Fossil island wyverns", "Frost dragons",
			"Hydras", "Skeletal wyverns", "Waterfiends", "Wyrms");
		put(map, SAFESPOT,
			"Ankou", "Blue dragons", "Catablepon", "Cave bugs", "Crocodiles",
			"Fever spiders", "Fleshcrawlers", "Ghouls", "Hobgoblins", "Jungle horrors",
			"Killerwatts", "Minotaurs", "Ogres", "Otherworldly beings", "Pyrefiends",
			"Shades", "Shadow warriors", "Terror dogs", "Wolves");
		put(map, PRAYER,
			"Abyssal demons", "Aviansies", "Cave kraken", "Dust devils",
			"Jellies", "Nechryael", "Smoke devils");
		put(map, SUPPLY_FREE, "Vampyres");
		put(map, TRIVIAL,
			"Bandits", "Banshees", "Bats", "Bears", "Birds", "Black Knights",
			"Cave crawlers", "Chaos druids", "Cows", "Crabs", "Crawling hands",
			"Dogs", "Dwarves", "Goblins", "Hill giants", "Icefiends", "Monkeys",
			"Pirates", "Rats", "Scorpions", "Spiders");

		put(map, DIRECT,
			"Araxytes", "Black demons", "Black dragons", "Brine rats",
			"Cave horrors", "Cave slimes", "Cockatrice",
			"Custodian Stalkers", "Dagannoth", "Dark warriors", "Earth warriors",
			"Elves", "Ents", "Fire giants", "Gargoyles",
			"Ghosts", "Greater demons", "Green dragons", "Gryphons",
			"Harpie bug swarms", "Hellhounds", "Ice giants",
			"Ice warriors", "Kalphite", "Kalphites", "Kurask", "Lava Dragons",
			"Lesser demons", "Lesser Nagua", "Lizardmen", "Lizards",
			"Magic axes", "Mammoths", "Metal dragons", "Mogres", "Molanisks",
			"Moss giants", "Mutated zygomites",
			"Red dragons", "Revenants", "Rockslugs", "Rogues", "Scabarites",
			"Sea snakes", "Skeletons", "Sourhogs", "Trolls",
			"Turoth", "Tzhaar", "Venators", "Wall beasts", "Warped Creatures",
			"Werewolves", "Zombies");

		return Collections.unmodifiableMap(map);
	}

	private static void put(
		final Map<String, Policy> map,
		final Policy policy,
		final String... taskNames)
	{
		for (final String taskName : taskNames)
		{
			final String key = normalize(taskName);
			if (map.put(key, policy) != null)
			{
				throw new IllegalStateException(
					"Duplicate regular inventory audit: " + taskName
				);
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

	private static final class Policy
	{
		private final int restoreSlots;
		private final int foodSlots;
		private final int lootSlots;
		private final boolean styleBoost;

		private Policy(
			final int restoreSlots,
			final int foodSlots,
			final int lootSlots,
			final boolean styleBoost)
		{
			this.restoreSlots = restoreSlots;
			this.foodSlots = foodSlots;
			this.lootSlots = lootSlots;
			this.styleBoost = styleBoost;
		}

		private void apply(
			final SlayerMethodRules.Builder rules,
			final SlayerTaskStrategy strategy)
		{
			/*
			 * A task-wide policy supplies the normal trip shape, but the selected
			 * location/method is more specific.  Do not let a generic DIRECT policy
			 * reintroduce food for a reviewed protection-prayer or safespot method.
			 */
			final boolean preventsExpectedDamage = strategy != null
				&& (strategy.getDamageProfile()
					== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
					|| strategy.getDamageProfile()
						== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING);
			rules.restore("prayer potion", "super restore")
				.restoreSlots(restoreSlots)
				.foodSlots(preventsExpectedDamage ? 0 : foodSlots)
				.reservedLootSlots(lootSlots)
				.includeStyleBoost(styleBoost);
		}
	}
}
