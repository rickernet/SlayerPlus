package com.slayerplus;

import java.util.Locale;
import net.runelite.api.gameval.ItemID;

/**
 * Converts each individually reviewed SlayerTaskStrategy into the single rule
 * object used by both the Task Method panel and the bank-tag loadout engine.
 *
 * The strategy catalog remains the task-by-task research catalog. This class is
 * the shared policy layer: damage prevention, food, restores, exact-dose
 * fallback, spell supplies, and deterministic layout are applied identically
 * to every task, location, boss variant, and selected combat method.
 */
public final class SlayerMethodRuleCatalog
{
	/*
	 * Full catalog coverage is a development/test concern. Never run it from a
	 * static initializer: doing so makes any incomplete research entry capable
	 * of aborting RuneLite class initialization before login. Runtime selections
	 * are still validated individually by resolve().
	 */
	private SlayerMethodRuleCatalog() { }

	public static SlayerMethodRules resolve(
		final String taskName,
		final String location,
		final SlayerTaskStrategy strategy)
	{
		final String task = normalize(taskName);
		final SlayerMethodRules.Builder rules = defaults(
			taskName,
			location,
			strategy
		);

		/* Every reviewed encounter receives an explicit Bank Tag supply policy. */
		applyBankTagLoadoutRules(task, location, rules, strategy);

		/* Regular Smoke devils and Thermy are separate researched encounters. */
		if (task.contains("thermonuclear smoke devil"))
		{
			applyThermonuclearRules(rules, strategy);
		}
		else if (task.equals("smoke devils") || task.equals("smoke devil"))
		{
			applyRegularSmokeDevilRules(rules, strategy);
		}

		/*
		 * Regular Cave kraken are not a generic damage-taking Magic task. Protect
		 * from Magic makes the trip extremely light on food, and powered staffs
		 * do not inherently require a rune pouch.
		 */
		if ((task.equals("cave kraken") || task.equals("cave krakens"))
			&& (strategy == null || !strategy.isBoss()))
		{
			rules.taskMethod(
				"Disturb a regular whirlpool, keep Protect from Magic active, and use the selected powered Magic weapon."
			).damageControl(SlayerMethodRules.DamageControl.PRAYER_PROTECTED)
				.restore("prayer potion", "super restore")
				.restoreSlots(5)
				.foodSlots(2)
				.reservedLootSlots(16);
		}

		/*
		 * Dark beasts are prayer-melee with a small but real food allowance.
		 * Letting an aggressive beast initiate in melee avoids routine opening
		 * Magic hits, but cannoning can create extra Magic contact and a Night beast
		 * superior can justify emergency healing. Do not globally zero-food this task.
		 */
		if (task.equals("dark beast") || task.equals("dark beasts"))
		{
			final boolean cannonMethod = strategy != null
				&& strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON);
			rules.damageControl(SlayerMethodRules.DamageControl.DIRECT_DAMAGE)
				.restore("prayer potion", "super restore")
				.restoreSlots(cannonMethod ? 5 : 4)
				.foodSlots(cannonMethod ? 3 : 2)
				.reservedLootSlots(cannonMethod ? 8 : 12);
		}

		/*
		 * Spellbook setup is selected-method state, not task-name state. Mark any
		 * explicitly authored Standard-spell method before considering optional
		 * Arceuus utility so the two books can never overwrite each other.
		 */
		applyResearchedSpellbookRules(rules, strategy);
		applyWikiArceuusUtilityRules(task, rules, strategy);

		final SlayerMethodRules result = rules.build();
		result.validateFor(taskName, strategy);
		return result;
	}

	private static SlayerMethodRules.Builder defaults(
		final String taskName,
		final String location,
		final SlayerTaskStrategy strategy)
	{
		final boolean reviewed = strategy != null && strategy.isReviewed();
		final SlayerTaskResearchCatalog.Entry research =
			SlayerTaskStrategyCatalog.getResearchRecord(taskName);
		final String reviewDate = research == null
			? "catalog migration"
			: safe(research.getReviewDate());
		final SlayerMethodRules.DamageControl damageControl =
			resolveDamageControl(strategy);
		final SlayerMethodRules.LayoutProfile layout =
			resolveLayoutProfile(location, strategy, damageControl);

		final SlayerMethodRules.Builder rules = SlayerMethodRules.builder()
			.coverageKey(buildCoverageKey(taskName, location, strategy))
			.taskMethod(resolveConciseTaskMethod(taskName, strategy))
			.combatStyle(strategy == null ? null : strategy.getCombatStyle())
			.research(
				wikiSource(taskName, strategy),
				reviewDate,
				reviewed
			)
			.damageControl(damageControl)
			.layout(layout);

		/*
		 * Use the potion family named by the reviewed strategy when it is
		 * explicit. Otherwise regular prayer tasks prefer Prayer potions while
		 * bosses prefer Super restores. The alternate family is used only when
		 * the primary family is not owned.
		 */
		final String restoreResearch = strategy == null
			? ""
			: normalize(strategy.getMethod() + " " + strategy.getRationale());
		if (restoreResearch.contains("super restore"))
		{
			rules.restore("super restore", "prayer potion");
		}
		else if (restoreResearch.contains("prayer potion"))
		{
			rules.restore("prayer potion", "super restore");
		}
		else if (SlayerBossInventoryCatalog.getReviewedBossKeys().contains(
			SlayerBossInventoryCatalog.canonicalBossKey(taskName)))
		{
			rules.restore("super restore", "prayer potion");
		}
		else
		{
			rules.restore("prayer potion", "super restore");
		}

		if (strategy == null)
		{
			return rules;
		}

		if (damageControl == SlayerMethodRules.DamageControl.PRAYER_PROTECTED
			|| damageControl == SlayerMethodRules.DamageControl.SAFESPOT
			|| damageControl == SlayerMethodRules.DamageControl.FREEZE_SAFESPOT)
		{
			/* No generic food when the reviewed method prevents expected damage. */
			rules.foodSlots(0);
		}

		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
		{
			rules.cannon(true, auditedCannonballQuantity(taskName));
		}

		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE))
		{
			final boolean dustDevilMethod =
				normalize(taskName).equals("dust devils");
			final boolean nechryaelMethod =
				normalize(taskName).equals("nechryael");
			final boolean tzhaarBloodMethod =
				normalize(taskName).equals("tzhaar");
			final boolean dustDevilProfitBurst =
				dustDevilMethod
					&& strategy.getCostPolicy() == SlayerTaskStrategy.CostPolicy.EFFICIENT;
			rules.layout(SlayerMethodRules.LayoutProfile.BARRAGE)
				.spell(
					SlayerMethodRules.Spellbook.ANCIENT,
					tzhaarBloodMethod
						? "Blood Barrage"
						: dustDevilProfitBurst ? "Ice Burst" : "Ice Barrage or Ice Burst"
				)
				.requiredItem(
					tzhaarBloodMethod ? "Fire runes" : "Water runes",
					5000,
					SlayerMethodRules.InventoryGroup.RUNES_AMMO,
					tzhaarBloodMethod ? "fire rune" : "water rune"
				);

			if (dustDevilProfitBurst)
			{
				rules.requiredItem(
					"Chaos runes",
					4000,
					SlayerMethodRules.InventoryGroup.RUNES_AMMO,
					"chaos rune"
				);
			}
			else
			{
				rules.requiredItem(
					"Blood runes",
					2000,
					SlayerMethodRules.InventoryGroup.RUNES_AMMO,
					"blood rune"
				);
			}

			rules.requiredItem(
				"Death runes",
				4000,
				SlayerMethodRules.InventoryGroup.RUNES_AMMO,
				"death rune"
			);

			/* Goading makes eligible multi-target barrage trips more AFK, but it
			 * remains a convenience rather than a hard requirement. Include the
			 * player's fullest owned dose in the 4 x 7 grid and omit it completely
			 * when none is owned. */
			rules.ownedOnlyItem(
				SlayerPotionPolicy.GOADING_DISPLAY,
				1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				SlayerPotionPolicy.goadingAlternatives()
			);

			/* Every barrage trip needs a cheap, fast manual tagging option. Goading
			 * potion remains a convenience for its reviewed tasks, but it must not be
			 * the only way to collect a fresh or split monster stack. */
			rules.requiredItem(
				"Tagging darts/knives",
				100,
				SlayerMethodRules.InventoryGroup.UTILITY,
				"mithril dart",
				"steel dart",
				"iron dart",
				"bronze dart",
				"adamant dart",
				"mithril knife",
				"steel knife",
				"iron knife",
				"bronze knife",
				"adamant knife"
			);
		}

		if (strategy.fillsInventoryToTarget())
		{
			rules.inventoryTarget(strategy.getInventoryTargetSlots());
			if (!preventsExpectedDamage(damageControl))
			{
				rules.fillRemainingWithFood();
			}
		}

		return rules;
	}

	static int auditedCannonballQuantity(final String taskName)
	{
		final String task = normalize(taskName);
		if (matches(task, "smoke devils", "bloodveld", "araxytes"))
		{
			return 2000;
		}
		if (matches(task, "dagannoth", "kalphites", "suqahs", "trolls",
			"warped creatures", "gryphons", "scabarites"))
		{
			return 1500;
		}
		return 1000;
	}


	/**
	 * Global authored Bank Tag supply policy. Strategy selection remains task-by-
	 * task in SlayerTaskStrategyCatalog; this layer translates the selected
	 * researched method into deterministic quantities and real inventory slots.
	 */
	private static void applyBankTagLoadoutRules(
		final String task,
		final String location,
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		if (strategy == null)
		{
			return;
		}

		rules.inventoryTarget(28);

		if (SlayerBossInventoryCatalog.getReviewedBossKeys().contains(
			SlayerBossInventoryCatalog.canonicalBossKey(task)))
		{
			applyBossBankTagRules(task, rules, strategy);
			return;
		}

		if (strategy.hasTag(
			SlayerTaskStrategy.MethodTag.TURAEL_POINT_BOOST
		))
		{
			applyTuraelBoostRules(task, rules, strategy);
			return;
		}

		/* Include ordinary tasks selected through a reviewed Krystilia branch. */
		if (isWildernessEncounter(task, location, strategy))
		{
			rules.restore("blighted super restore", "super restore")
				.food(
					"Blighted high-healing food",
					"blighted anglerfish", "blighted manta ray",
					"blighted karambwan", "anglerfish", "manta ray",
					"cooked karambwan"
				)
				.reservedLootSlots(8)
				.restoreSlots(3)
				.foodSlots(preventsExpectedDamage(resolveDamageControl(strategy)) ? 0 : 6)
				.requiredItem("Looting bag", 1,
					SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
				.requiredItem("Level-30 Wilderness escape", 1,
					SlayerMethodRules.InventoryGroup.UTILITY,
					"royal seed pod", "seed pod", "dragonstone teleport scroll",
					"amulet of glory", "ring of wealth");
			if (isDragonEncounter(task))
			{
				rules.requiredSlots(
					strategy.getCombatStyle() == SlayerTaskStrategy.CombatStyle.MELEE
						? SlayerPotionPolicy.EXTENDED_SUPER_ANTIFIRE_DISPLAY
						: SlayerPotionPolicy.EXTENDED_ANTIFIRE_DISPLAY,
					1,
					SlayerMethodRules.InventoryGroup.PROTECTION,
					strategy.getCombatStyle() == SlayerTaskStrategy.CombatStyle.MELEE
						? SlayerPotionPolicy.potionOnlyAntifireAlternatives()
						: SlayerPotionPolicy.shieldedAntifireAlternatives()
				);
			}
			applyGodWarsProtection(task, location, rules);
			return;
		}

		/* Continue into the ordinary style supply policy after adding the
		 * non-negotiable dungeon protection pieces. */
		applyGodWarsProtection(task, location, rules);

		if (task.equals("ankou"))
		{
			applyAnkouBankTagRules(location, rules, strategy);
			return;
		}

		/* Barrage/freeze methods are prayer-and-rune trips, not food trips. */
		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE))
		{
			rules.reservedLootSlots(8)
				.restoreSlots(5)
				.foodSlots(0);
			return;
		}

		if (task.equals("bloodveld") || task.equals("bloodvelds"))
		{
			applyBloodveldBankTagRules(location, rules, strategy);
			return;
		}

		/* Kalphites have a reviewed five-restore cannon trip, not the generic
		 * three-restore cannon template used by shorter assignments. */
		if (task.equals("kalphite") || task.equals("kalphites"))
		{
			SlayerRegularInventoryAuditCatalog.apply(task, rules, strategy);
			return;
		}

		/* Cannon methods keep room for drops while carrying the four cannon parts. */
		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
		{
			rules.reservedLootSlots(8)
				.restoreSlots(3)
				.foodSlots(preventsExpectedDamage(resolveDamageControl(strategy)) ? 0 : 6);
			return;
		}

		/* Antifire potions do not reduce a Skeletal Wyvern's icy breath. */
		if (task.equals("skeletal wyvern") || task.equals("skeletal wyverns"))
		{
			SlayerRegularInventoryAuditCatalog.apply(task, rules, strategy);
			return;
		}

		/* Fossil Island wyverns use their reviewed heavy-trip policy. They are
		 * dragon-like, but the generic dragon template understates their food. */
		if (task.equals("fossil island wyvern")
			|| task.equals("fossil island wyverns"))
		{
			SlayerRegularInventoryAuditCatalog.apply(task, rules, strategy);
			return;
		}

		/* Dragon assignments need antifire gear/supplies in addition to normal trip supplies. */
		if (isDragonEncounter(task))
		{
			final boolean melee = strategy.getCombatStyle()
				== SlayerTaskStrategy.CombatStyle.MELEE;
			final boolean reviewedChromatic = matches(task,
				"blue dragon", "blue dragons", "black dragon", "black dragons",
				"green dragon", "green dragons", "red dragon", "red dragons");
			final boolean metal = isMetalDragonTask(task);
			if (reviewedChromatic || metal)
			{
				rules.requiredSlots(
					melee
						? SlayerPotionPolicy.EXTENDED_SUPER_ANTIFIRE_DISPLAY
						: SlayerPotionPolicy.EXTENDED_ANTIFIRE_DISPLAY,
					metal ? 2 : 1,
					SlayerMethodRules.InventoryGroup.PROTECTION,
					melee
						? SlayerPotionPolicy.potionOnlyAntifireAlternatives()
						: SlayerPotionPolicy.shieldedAntifireAlternatives()
				);
			}
			/*
			 * The reviewed regular Blue-dragon method is a shielded safespot trip.
			 * It consumes no prayer and deliberately leaves almost the entire pack
			 * empty for paired bone/hide drops instead of inventing three restores.
			 */
			if (task.equals("blue dragon") || task.equals("blue dragons"))
			{
				final boolean safespot = strategy != null
					&& (strategy.getCombatStyle()
						== SlayerTaskStrategy.CombatStyle.RANGED
						|| strategy.getCombatStyle()
							== SlayerTaskStrategy.CombatStyle.MAGIC);
				rules.reservedLootSlots(safespot ? 24 : 12)
					.restoreSlots(0)
					.foodSlots(safespot ? 0 : 6);
				return;
			}
			if (metal)
			{
				final boolean lithkren = normalize(location).contains("lithkren");
				rules.reservedLootSlots(lithkren ? 6 : 12)
					.restoreSlots(lithkren ? 4 : melee ? 4 : 0)
					.foodSlots(lithkren ? 8 : melee ? 4 : 0)
					.includeStyleBoost(true);
				if (lithkren)
				{
					rules.requiredItem("Insulated boots", 1,
						SlayerMethodRules.InventoryGroup.PROTECTION, "insulated boots")
						.requiredItem("Poison protection", 1,
							SlayerMethodRules.InventoryGroup.PROTECTION,
							"antidote plus plus", "antidote plus", "superantipoison", "antipoison");
				}
				return;
			}
			rules.reservedLootSlots(10)
				.restoreSlots(3)
				.foodSlots(preventsExpectedDamage(resolveDamageControl(strategy)) ? 0 : 6);
			return;
		}

		SlayerRegularInventoryAuditCatalog.apply(task, rules, strategy);
	}

	private static void applyTuraelBoostRules(
		final String task,
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		final SlayerTuraelBoostCatalog.Entry boostEntry =
			SlayerTuraelBoostCatalog.find(task);
		final boolean cannon = strategy.hasTag(
			SlayerTaskStrategy.MethodTag.CANNON
		) && boostEntry != null && boostEntry.supportsCannon();
		rules.restore("prayer potion", "super restore")
			.restoreSlots(0)
			.foodSlots(2)
			.reservedLootSlots(cannon ? 12 : 20)
			.includeStyleBoost(true);
		if (cannon)
		{
			rules.cannon(true, 300);
		}
		else
		{
			/* A broad task strategy must never re-enable a cannon in a reviewed
			 * no-cannon boost location such as Slayer Tower or the Fremennik cave. */
			rules.cannon(false, 0);
		}

		if (matches(task, "cave bug", "cave bugs", "cave slime", "cave slimes"))
		{
			rules.requiredItem("Safe light source", 1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				"bruma torch", "bullseye lantern", "emerald lantern",
				"sapphire lantern", "oil lantern", "candle lantern");
		}
		if (matches(task, "cave crawler", "cave crawlers", "cave slime", "cave slimes"))
		{
			rules.requiredItem("Poison protection", 1,
				SlayerMethodRules.InventoryGroup.PROTECTION,
				"antidote plus plus", "antidote plus", "superantipoison",
				"antipoison");
		}
		if (matches(task, "lizard", "lizards"))
		{
			rules.requiredItem("Ice coolers", 30,
				SlayerMethodRules.InventoryGroup.UTILITY, "ice cooler")
				.requiredItem("Desert heat protection", 1,
					SlayerMethodRules.InventoryGroup.PROTECTION,
					"circlet of water", "desert amulet 4", "waterskin 4",
					"waterskin 3", "waterskin 2", "waterskin 1");
		}
		if (matches(task, "skeleton", "skeletons"))
		{
			rules.requiredItem("Rope", 1,
				SlayerMethodRules.InventoryGroup.UTILITY, "rope");
		}
	}

	private static boolean applyGodWarsProtection(
		final String task,
		final String location,
		final SlayerMethodRules.Builder rules)
	{
		final String area = normalize(location);
		if (!area.contains("god wars")
			|| !(task.equals("aviansie") || task.equals("aviansies")
				|| task.equals("spiritual creature")
				|| task.equals("spiritual creatures")))
		{
			return false;
		}
		rules.requiredItem("Armadyl protection", 1,
			SlayerMethodRules.InventoryGroup.PROTECTION,
			"honourable blessing", "armadyl pendant", "book of law",
			"armadyl bracers", "armadyl d hide boots",
			"armadyl d hide body", "armadyl chaps", "armadyl cloak")
			.requiredItem("Zamorak protection", 1,
				SlayerMethodRules.InventoryGroup.PROTECTION,
				"unholy blessing", "unholy book", "zamorak bracers",
				"zamorak d hide boots", "zamorak d hide body",
				"zamorak chaps", "zamorak cloak");
		return true;
	}

	private static void applyBloodveldBankTagRules(
		final String selectedLocation,
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		final String location = normalize(selectedLocation);
		final boolean longPrayerTrip = location.contains("meiyerditch")
			|| location.contains("buccaneer");
		final boolean catacombs = location.contains("catacombs");
		final boolean protectedMethod = preventsExpectedDamage(
			resolveDamageControl(strategy)
		);

		rules.restore("prayer potion", "super restore")
			.restoreSlots(longPrayerTrip ? 6 : catacombs ? 5
				: protectedMethod && strategy.getArmourFocus()
					!= SlayerTaskStrategy.ArmourFocus.PRAYER ? 0 : 4)
			.foodSlots(protectedMethod ? 0 : 6)
			.reservedLootSlots(strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON) ? 8 : 12)
			.includeStyleBoost(true)
			.spell(
				SlayerMethodRules.Spellbook.STANDARD,
				"High Level Alchemy"
			)
			.runePouch(true)
			.pouchRune(ItemID.NATURERUNE, "Nature", 200)
			.pouchRune(ItemID.FIRERUNE, "Fire", 1000)
			.requiredItem(
				"Ash sanctifier", 1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				"ash sanctifier"
			)
			.requiredItem(
				"Soul bearer", 1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				"soul bearer"
			);

		if (catacombs
			&& strategy.hasTag(SlayerTaskStrategy.MethodTag.VENATOR))
		{
			rules.requiredItem(
				SlayerPotionPolicy.GOADING_DISPLAY,
				1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				SlayerPotionPolicy.goadingAlternatives()
			);
		}
	}

	private static void applyAnkouBankTagRules(
		final String selectedLocation,
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		final String location = normalize(selectedLocation);
		final boolean safespot = location.contains("stronghold of security");
		final boolean cannon = strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON);
		final boolean barrage = strategy.hasTag(
			SlayerTaskStrategy.MethodTag.BURST_BARRAGE
		);
		rules.restore("prayer potion", "super restore")
			.restoreSlots(safespot ? 0 : cannon ? 3 : 5)
			.foodSlots(barrage || safespot ? 0 : 2)
			.reservedLootSlots(barrage || cannon ? 8 : safespot ? 20 : 14)
			.includeStyleBoost(true);
	}

	private static void applyBossBankTagRules(
		final String task,
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		if (!SlayerBossInventoryCatalog.apply(task, rules, strategy))
		{
			/* Fail closed: supported bosses must never inherit a generic template. */
			throw new IllegalStateException(
				"Missing researched boss inventory policy: " + task
			);
		}
	}

	private static boolean isWildernessEncounter(
		final String task,
		final String location,
		final SlayerTaskStrategy strategy)
	{
		return strategy != null
			&& strategy.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS)
			|| isWilderness(location)
			|| matches(task,
			"callisto", "venenatis", "vet ion", "vet'ion", "scorpia",
			"chaos elemental", "the chaos elemental", "chaos fanatic", "the chaos fanatic",
			"crazy archaeologists", "revenants", "lava dragons", "green dragons",
			"mammoths", "rogues", "magic axes", "dark warriors", "earth warriors", "ents");
	}

	private static boolean isDragonEncounter(final String task)
	{
		return task.contains("dragon") || task.contains("wyvern");
	}

	private static boolean isMetalDragonTask(final String task)
	{
		return matches(task,
			"metal dragon", "metal dragons", "bronze dragon", "bronze dragons",
			"iron dragon", "iron dragons", "steel dragon", "steel dragons",
			"mithril dragon", "mithril dragons", "adamant dragon", "adamant dragons",
			"rune dragon", "rune dragons");
	}

	/**
	 * Global spellbook rule for methods whose authored strategy actually casts a
	 * Standard-spellbook combat spell. Merely using Magic or a powered staff does
	 * not opt a task into a spellbook. Exact rune quantities for elemental
	 * "highest available" methods are deliberately left to preparation/runtime
	 * selection rather than pretending every account can cast the same tier.
	 */
	private static void applyResearchedSpellbookRules(
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		if (!selectedMethodRequiresStandardSpellbook(strategy))
		{
			return;
		}

		final String method = normalize(strategy.getMethod());
		if (method.contains("magic dart"))
		{
			rules.spell(SlayerMethodRules.Spellbook.STANDARD, "Magic Dart")
				.runePouch(true)
				.pouchRune(ItemID.MINDRUNE, "Mind", 4)
				.pouchRune(ItemID.DEATHRUNE, "Death", 1);
			return;
		}

		if (mentionsWaterAndFireSpells(method))
		{
			rules.spell(
				SlayerMethodRules.Spellbook.STANDARD,
				"Water / Fire spells"
			).runePouch(true)
				.pouchRune(ItemID.AIRRUNE, "Air", 5000)
				.pouchRune(ItemID.WATERRUNE, "Water", 5000)
				.pouchRune(ItemID.FIRERUNE, "Fire", 5000)
				.pouchRune(ItemID.WRATHRUNE, "Wrath", 2000);
			return;
		}

		if (method.contains("water blast"))
		{
			rules.spell(
				SlayerMethodRules.Spellbook.STANDARD,
				"Water Blast"
			).runePouch(true)
				.pouchRune(ItemID.AIRRUNE, "Air", 5000)
				.pouchRune(ItemID.WATERRUNE, "Water", 5000)
				.pouchRune(ItemID.DEATHRUNE, "Death", 2000);
			return;
		}

		if (method.contains("water spell") || method.contains("water spells"))
		{
			rules.spell(
				SlayerMethodRules.Spellbook.STANDARD,
				"Water Surge or Water Wave"
			).runePouch(true)
				.pouchRune(ItemID.AIRRUNE, "Air", 5000)
				.pouchRune(ItemID.WATERRUNE, "Water", 5000)
				.pouchRune(ItemID.WRATHRUNE, "Wrath", 2000)
				.pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
			return;
		}

		if (method.contains("earth wave"))
		{
			rules.spell(
				SlayerMethodRules.Spellbook.STANDARD,
				"Earth Wave"
			).runePouch(true)
				.pouchRune(ItemID.AIRRUNE, "Air", 5000)
				.pouchRune(ItemID.EARTHRUNE, "Earth", 5000)
				.pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
			return;
		}

		if (method.contains("earth spell") || method.contains("earth spells"))
		{
			rules.spell(
				SlayerMethodRules.Spellbook.STANDARD,
				"Earth Surge or Earth Wave"
			).runePouch(true)
				.pouchRune(ItemID.AIRRUNE, "Air", 5000)
				.pouchRune(ItemID.EARTHRUNE, "Earth", 5000)
				.pouchRune(ItemID.WRATHRUNE, "Wrath", 2000)
				.pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
			return;
		}

		if (method.contains("fire spell") || method.contains("fire spells"))
		{
			rules.spell(
				SlayerMethodRules.Spellbook.STANDARD,
				"Fire Surge or Fire Wave"
			).runePouch(true)
				.pouchRune(ItemID.AIRRUNE, "Air", 5000)
				.pouchRune(ItemID.FIRERUNE, "Fire", 5000)
				.pouchRune(ItemID.WRATHRUNE, "Wrath", 2000)
				.pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
			return;
		}

		if (method.contains("air spell") || method.contains("air spells")
			|| method.contains("wind spell") || method.contains("wind spells"))
		{
			rules.spell(
				SlayerMethodRules.Spellbook.STANDARD,
				"Wind Surge or Wind Wave"
			).runePouch(true)
				.pouchRune(ItemID.AIRRUNE, "Air", 5000)
				.pouchRune(ItemID.WRATHRUNE, "Wrath", 2000)
				.pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
		}
	}

	/** Package-private so the development coverage audit cannot drift away from runtime. */
	static boolean selectedMethodRequiresStandardSpellbook(
		final SlayerTaskStrategy strategy)
	{
		if (strategy == null)
		{
			return false;
		}

		final String method = normalize(strategy.getMethod());
		return method.contains("magic dart")
			|| mentionsWaterAndFireSpells(method)
			|| method.contains("water blast")
			|| method.contains("water spell")
			|| method.contains("earth wave")
			|| method.contains("earth spell")
			|| method.contains("fire spell")
			|| method.contains("air spell")
			|| method.contains("wind spell");
	}

	private static boolean mentionsWaterAndFireSpells(final String method)
	{
		return method != null
			&& method.contains("water")
			&& method.contains("fire")
			&& method.contains("spell");
	}

	/**
	 * Current OSRS Wiki audit for Slayer encounters where the reviewed method
	 * explicitly recommends or includes Thralls. This is deliberately
	 * conservative: a task is not opted in merely because Thralls are usable.
	 * Ancient-Magicks methods are left untouched because only one spellbook can
	 * be active at a time.
	 */
	private static void applyWikiArceuusUtilityRules(
		final String task,
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		if (strategy == null
			|| strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE)
			|| task.contains("thermonuclear smoke devil")
			|| selectedMethodRequiresStandardSpellbook(strategy))
		{
			return;
		}

		final SlayerTaskStrategy.CombatStyle style = strategy.getCombatStyle();
		boolean thralls = false;
		boolean deathCharge = false;
		boolean ward = false;

		/* Regular Slayer-task pages with an explicit Arceuus recommendation. */
		if ((task.equals("abyssal demons") || task.equals("abyssal demon"))
			&& style == SlayerTaskStrategy.CombatStyle.MELEE)
		{
			thralls = true;
			deathCharge = true;
		}
		else if ((task.equals("nechryael") || task.equals("nechryaels"))
			&& style == SlayerTaskStrategy.CombatStyle.MELEE)
		{
			thralls = true;
			deathCharge = true;
		}
		else if ((task.equals("trolls") || task.equals("troll"))
			&& style == SlayerTaskStrategy.CombatStyle.MELEE)
		{
			thralls = true;
			deathCharge = true;
		}

		/* Boss strategy pages whose current inventories include Thralls. */
		if (matches(task, "cerberus"))
		{
			/*
			 * The reviewed default is a Greater Thrall pouch. Death Charge is
			 * optional at Cerberus because its extra rune types cost inventory
			 * space, while Ward of Arceuus is conditional on the player's shield
			 * choice. Combining all three would require five rune types and cannot
			 * be represented by even a divine rune pouch.
			 */
			thralls = true;
		}
		else if (matches(task, "amoxliatl", "araxxor", "sarachnis", "vardorvis",
			"grotesque guardians", "the grotesque guardians", "zulrah",
			"kalphite queen", "the kalphite queen"))
		{
			thralls = true;
			deathCharge = true;
		}
		else if (matches(task, "phantom muspah", "the phantom muspah"))
		{
			thralls = true;
			deathCharge = true;
		}
		else if (matches(task, "whisperer", "the whisperer"))
		{
			/* Wiki offers Thralls or ice spells; the normal SlayerPlus method
			 * is not an Ancient freeze method, so prefer the Thrall package. */
			thralls = true;
		}
		else if (matches(task, "general graardor"))
		{
			/* Wiki recommends Thralls OR Blood Barrage. SlayerPlus' normal
			 * ranged/kite method uses Thralls; Ancient sustain remains separate. */
			thralls = true;
		}
		/*
		 * Kraken intentionally has no unconditional Arceuus package. Jagex made
		 * Thralls usable there, but current strategy guidance treats Thralls/Death
		 * Charge as one optional rune-pouch setup alongside elemental spells, Blood
		 * spells, or alchemy. The default powered-staff method therefore stays
		 * spellbook-neutral.
		 */
		else if (matches(task, "alchemical hydra", "the alchemical hydra"))
		{
			/* Current Wiki long-trip inventory includes Greater Ghost + Death
			 * Charge; this is the efficient sustained Slayer-boss package. */
			thralls = true;
			deathCharge = true;
		}
		else if (matches(task, "tormented demons", "tormented demon"))
		{
			/* Every current Wiki inventory tier includes Thralls and Death Charge. */
			thralls = true;
			deathCharge = true;
		}
		else if (matches(task, "venenatis", "spindel"))
		{
			/* Explicitly optional on the Wiki; include only when the selected
			 * encounter is already a Wilderness boss method. */
			thralls = strategy.isBoss();
			deathCharge = strategy.isBoss();
		}

		if (!thralls)
		{
			return;
		}

		rules.taskMethod(withThrallNote(
			resolveConciseTaskMethod(task, strategy),
			deathCharge,
			ward
		)).arceuusUtility(thralls, deathCharge, ward)
			.includeRunePouchForMagic(true)
			.requiredItem(
				"Book of the dead", 1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				"book of the dead"
			)
			.requiredItem(
				"Rune pouch", 1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				"divine rune pouch", "rune pouch"
			)
			.requiredItem(
				"Fire runes", 1000,
				SlayerMethodRules.InventoryGroup.RUNES_AMMO,
				"fire rune"
			)
			.requiredItem(
				"Cosmic runes", 500,
				SlayerMethodRules.InventoryGroup.RUNES_AMMO,
				"cosmic rune"
			)
			.requiredItem(
				"Blood runes", 500,
				SlayerMethodRules.InventoryGroup.RUNES_AMMO,
				"blood rune"
			);

		if (deathCharge)
		{
			rules.requiredItem(
				"Death runes", 500,
				SlayerMethodRules.InventoryGroup.RUNES_AMMO,
				"death rune"
			).requiredItem(
				"Soul runes", 500,
				SlayerMethodRules.InventoryGroup.RUNES_AMMO,
				"soul rune"
			);
		}

		if (ward)
		{
			/* Ward of Arceuus shares blood/soul support with this package; the
			 * readiness layer can surface the spell requirement without another
			 * inventory item. */
		}
	}

	private static String withThrallNote(
		final String method,
		final boolean deathCharge,
		final boolean ward)
	{
		String base = concise(method);
		if (base.endsWith("."))
		{
			base = base.substring(0, base.length() - 1);
		}
		final String suffix = ward
			? "; keep a thrall active and use Ward of Arceuus/Death Charge when useful"
			: deathCharge
				? "; keep a thrall active and use Death Charge"
				: "; keep a thrall active";
		return concise(base + suffix);
	}

	private static boolean matches(final String value, final String... names)
	{
		for (final String name : names)
		{
			if (value.equals(normalize(name)))
			{
				return true;
			}
		}
		return false;
	}

	private static void applyRegularSmokeDevilRules(
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		final SlayerTaskStrategy.CombatStyle style = strategy == null
			? null : strategy.getCombatStyle();
		final boolean barrage = strategy != null
			&& strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE);

		if (style == SlayerTaskStrategy.CombatStyle.MAGIC && barrage)
		{
			rules.taskMethod(
				"Keep Protect from Missiles active, stack the group with a cannon, then Ice Barrage them."
			).layout(SlayerMethodRules.LayoutProfile.BARRAGE);
		}
		else if (style == SlayerTaskStrategy.CombatStyle.RANGED)
		{
			rules.taskMethod(
				"Keep Protect from Missiles active and use the selected ranged setup; cannon where allowed."
			);
		}
		else
		{
			rules.taskMethod(
				"Keep Protect from Missiles active and use the selected setup; cannon where allowed."
			);
		}

		rules.damageControl(SlayerMethodRules.DamageControl.PRAYER_PROTECTED)
			.restore("prayer potion", "super restore")
			.foodSlots(0);
	}

	private static void applyThermonuclearRules(
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		final SlayerTaskStrategy.CombatStyle style = strategy == null
			? null : strategy.getCombatStyle();
		final String authored = strategy == null ? "" : safe(strategy.getMethod());

		if (style == SlayerTaskStrategy.CombatStyle.MAGIC
			&& (contains(authored, "blood barrage")
				|| contains(authored, "blood burst")))
		{
			rules.taskMethod(
				"Use Blood Barrage for sustain; protection prayers do not reduce Thermy's typeless attacks."
			).damageControl(SlayerMethodRules.DamageControl.DIRECT_DAMAGE)
				.layout(SlayerMethodRules.LayoutProfile.BARRAGE)
				.spell(SlayerMethodRules.Spellbook.ANCIENT, "Blood Barrage or Blood Burst")
				.restore("super restore", "prayer potion")
				.restoreSlots(4)
				.foodSlots(4)
				.reservedLootSlots(6);
			return;
		}

		if (style == SlayerTaskStrategy.CombatStyle.MAGIC)
		{
			rules.taskMethod(
				"Freeze Thermy, move beyond its 8-tile range, then use Shadow on Longrange; refreeze before it reaches you."
			).damageControl(SlayerMethodRules.DamageControl.FREEZE_SAFESPOT)
				.layout(SlayerMethodRules.LayoutProfile.FREEZE_MAGIC)
				.spell(SlayerMethodRules.Spellbook.ANCIENT, "Best available ice spell")
				.restore("super restore", "prayer potion")
				/*
				 * Freeze + Shadow is a low-damage safespot trip, not a Redemption
				 * inventory. Community-style Magic setups commonly use about five
				 * Super restore(4)s; leave the rest of the inventory available for
				 * drops instead of filling every free slot with prayer supplies.
				 */
				.restoreSlots(5)
				.foodSlots(2)
				.inventoryTarget(28)
				.reservedLootSlots(8)
				.runePouch(true)
				.pouchRune(ItemID.WATERRUNE, "Water", 5000)
				.pouchRune(ItemID.BLOODRUNE, "Blood", 2000)
				.pouchRune(ItemID.DEATHRUNE, "Death", 4000)
				.requiredItem(
					"Ice ancient sceptre",
					1,
					SlayerMethodRules.InventoryGroup.SWITCH,
					"ice ancient sceptre",
					"ancient sceptre"
				);
			return;
		}

		if (style == SlayerTaskStrategy.CombatStyle.RANGED)
		{
			rules.taskMethod(
				"Attack from range and step underneath between attacks when Thermy closes in; protection prayers do not help."
			).damageControl(SlayerMethodRules.DamageControl.STEP_UNDER)
				.layout(SlayerMethodRules.LayoutProfile.BOSS)
				.restore("super restore", "prayer potion")
				.restoreSlots(5)
				.foodSlots(8)
				.reservedLootSlots(5);
			return;
		}

		rules.taskMethod(
			"Attack once, step underneath between attacks, then step back out when ready; protection prayers do not help."
		).damageControl(SlayerMethodRules.DamageControl.STEP_UNDER)
			.layout(SlayerMethodRules.LayoutProfile.BOSS)
			.restore("super restore", "prayer potion")
			.restoreSlots(5)
			.foodSlots(8)
			.reservedLootSlots(5);
	}

	private static SlayerMethodRules.DamageControl resolveDamageControl(
		final SlayerTaskStrategy strategy)
	{
		if (strategy == null)
		{
			return SlayerMethodRules.DamageControl.STRATEGY_DEFINED;
		}
		if (strategy.getDamageProfile()
			== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED)
		{
			return SlayerMethodRules.DamageControl.PRAYER_PROTECTED;
		}
		if (strategy.getDamageProfile()
			== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING)
		{
			return SlayerMethodRules.DamageControl.SAFESPOT;
		}
		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.SAFESPOT))
		{
			return SlayerMethodRules.DamageControl.SAFESPOT;
		}
		if (strategy.isBoss())
		{
			return SlayerMethodRules.DamageControl.BOSS_MECHANICS;
		}
		return SlayerMethodRules.DamageControl.DIRECT_DAMAGE;
	}

	private static SlayerMethodRules.LayoutProfile resolveLayoutProfile(
		final String location,
		final SlayerTaskStrategy strategy,
		final SlayerMethodRules.DamageControl damageControl)
	{
		if (isWilderness(location))
		{
			return SlayerMethodRules.LayoutProfile.WILDERNESS;
		}
		if (strategy == null)
		{
			return SlayerMethodRules.LayoutProfile.STANDARD;
		}
		if (strategy.isBoss())
		{
			return strategy.getCombatStyle()
				== SlayerTaskStrategy.CombatStyle.HYBRID
					? SlayerMethodRules.LayoutProfile.HYBRID_BOSS
					: SlayerMethodRules.LayoutProfile.BOSS;
		}
		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE))
		{
			return SlayerMethodRules.LayoutProfile.BARRAGE;
		}
		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
		{
			return SlayerMethodRules.LayoutProfile.CANNON;
		}
		if (damageControl == SlayerMethodRules.DamageControl.PRAYER_PROTECTED)
		{
			return SlayerMethodRules.LayoutProfile.PRAYER_TASK;
		}
		return SlayerMethodRules.LayoutProfile.STANDARD;
	}

	private static String resolveConciseTaskMethod(
		final String taskName,
		final SlayerTaskStrategy strategy)
	{
		if (strategy != null && strategy.isBoss())
		{
			final String bossMethod = bossMethod(taskName, strategy);
			if (!bossMethod.isEmpty())
			{
				return concise(bossMethod);
			}
		}
		if (strategy != null && !safe(strategy.getMethod()).isEmpty())
		{
			return concise(strategy.getMethod());
		}
		return "Use the reviewed setup and follow the encounter's required protection and positioning.";
	}

	private static String bossMethod(
		final String bossName,
		final SlayerTaskStrategy strategy)
	{
		final String boss = normalize(bossName);
		final SlayerTaskStrategy.CombatStyle style = strategy == null
			? null : strategy.getCombatStyle();
		switch (boss)
		{
			case "abyssal sire":
			case "the abyssal sire":
				return "Stun Sire, destroy all four respiratory systems, avoid poison pools, and move away before the final explosion.";
			case "alchemical hydra":
			case "the alchemical hydra":
				return "Move Hydra onto the correct vent, switch prayer every third attack, and avoid poison, lightning, and flame walls.";
			case "araxxor":
				return "Avoid venom and acid, control the spawned araxytes, and keep moving through the enrage phase.";
			case "amoxliatl":
				return "Avoid marked floor attacks and reposition immediately when the arena mechanic forces movement.";
			case "artio":
			case "callisto":
				return "Protect from Missiles, freeze the bear when applicable, avoid knockback and debris, and keep an escape ready.";
			case "cerberus":
				return "React to the three-headed combo, switch prayers for the ghosts, and move clear of lava pools.";
			case "dagannoth kings":
				return "Melee Supreme, range Prime, and mage Rex while keeping the other Kings out of combat when possible.";
			case "grotesque guardians":
			case "the grotesque guardians":
				return "Range Dawn, melee Dusk, avoid rocks and lightning, and keep moving during Dusk's final phase.";
			case "k ril tsutsaroth":
				return style == SlayerTaskStrategy.CombatStyle.RANGED
					? "Bind and kite K'ril with Scorching bow, Protect from Missiles, then stack the bodyguards and Blood Barrage them for healing."
					: "Use demonbane melee, Protect from Melee, and step under or reposition between attacks.";
			case "skotizo":
				return "Use demonbane, destroy active altars when their damage reduction matters, and pray against the active style.";
			case "kalphite queen":
			case "the kalphite queen":
				return "Use melee for phase one, switch to ranged or magic for phase two, and stand underneath between attacks.";
			case "king black dragon":
			case "the king black dragon":
				return "Maintain dragonfire protection and manage poison, venom, and stat-draining breath effects.";
			case "kraken":
			case "the cave kraken boss":
				return "Use a fishing explosive on the large whirlpool, attack Kraken with powered Magic, and use offensive Magic prayer rather than a protection prayer.";
			case "kree arra":
				return "Use ranged only, keep Protect from Missiles active, and stay outside melee distance.";
			case "sarachnis":
				return "Use crush, switch protection prayer with Sarachnis's style, and handle the spawned spiders.";
			case "spindel":
			case "venenatis":
				return "Use crush, switch prayers, avoid webs and prayer drain, and keep a Wilderness escape ready.";
			case "scorpia":
				return "Freeze Scorpia, attack from distance, and freeze or kill the guardians before they heal her.";
			case "tztok jad":
				return "Prioritise prayer switches, tag the healers safely, and keep watching Jad's next attack.";
			case "tzkal zuk":
				return "Stay behind the shield, handle each set, and never step outside protection during Zuk's attacks.";
			case "calvar ion":
			case "vet ion":
				return "Use crush, avoid lightning and shield-bash tiles, kill both hellhounds, and keep an escape ready.";
			case "vorkath":
				return "Maintain dragonfire protection, kill the spawn immediately, and walk the acid phase with run disabled.";
			case "scurrius":
				return "Use a rat-bone weapon when available, avoid falling debris, and use the food piles when needed.";
			case "obor":
				return "Protect from Melee and avoid sitting at low health after a knockback.";
			case "bryophyta":
				return "Protect from Magic, clear the growthlings with an axe, and avoid poison clouds.";
			case "royal titans":
				return "Attack the appropriate Titan, react to arena hazards, and complete required switches before resuming damage.";
			case "shellbane gryphon":
				return "Avoid marked arena attacks and follow each movement or phase mechanic before resuming damage.";
			case "barrows brothers":
				return "Mage the melee brothers, use the selected secondary style where needed, and conserve prayer for the tunnels.";
			case "chaos elemental":
				return "Keep inventory slots filled to limit unequipping, avoid teleports when possible, and keep an escape ready.";
			case "chaos fanatic":
				return "Protect from Magic, move away from green special-attack tiles, and keep an escape ready.";
			case "crazy archaeologist":
			case "deranged archaeologist":
				return "Protect from Missiles and move several tiles away when the explosive book attack is thrown.";
			case "duke sucellus":
				return "Prepare both potions, attack from the correct side, use pillars for the gaze, and avoid vents.";
			case "general graardor":
				return style == SlayerTaskStrategy.CombatStyle.RANGED
					? "Use the selected door-altar or kite cycle, keep the tile rhythm, and clean up the bodyguards."
					: "Protect from Melee, step under as required, and clean up the bodyguards after the kill.";
			case "giant mole":
				return "Keep Protect from Melee active, use a light source and locator, and chase each dig efficiently.";
			case "maggot king":
				return "Follow the reviewed phase, movement, and special-attack requirements before resuming damage.";
			case "phantom muspah":
			case "the phantom muspah":
				return "Kite the melee phase, break the prayer shield with Smite or sapphire bolts, and avoid spikes.";
			case "leviathan":
			case "the leviathan":
				return "Switch prayers for each projectile, reset with a shadow spell when required, and move with the enrage orb.";
			case "whisperer":
			case "the whisperer":
				return "Use magic, manage sanity, enter the shadow realm for specials, use the fast ranged switch on Lost Souls, keep distance or bind after specials, and keep moving during enrage.";
			case "vardorvis":
				return "Use slash, avoid axes and floor spikes, and protect from the head projectile through enrage.";
			case "commander zilyana":
				return "Use ranged and kite Zilyana around the room without entering melee distance.";
			case "zulrah":
				return "Switch ranged and magic with each phase, change prayer and position, and avoid venom clouds.";
			default:
				return "";
		}
	}

	private static String concise(final String value)
	{
		String clean = safe(value).replaceAll("\\s+", " ");
		if (clean.isEmpty())
		{
			return clean;
		}
		final int secondSentence = clean.indexOf(". ");
		if (secondSentence > 35
			&& secondSentence + 1 <= SlayerMethodRules.MAX_TASK_METHOD_LENGTH)
		{
			clean = clean.substring(0, secondSentence + 1);
		}
		if (clean.length() > SlayerMethodRules.MAX_TASK_METHOD_LENGTH)
		{
			int cut = clean.lastIndexOf(' ',
				SlayerMethodRules.MAX_TASK_METHOD_LENGTH - 1);
			if (cut < 80)
			{
				cut = SlayerMethodRules.MAX_TASK_METHOD_LENGTH - 1;
			}
			clean = clean.substring(0, cut).trim();
		}
		final char last = clean.charAt(clean.length() - 1);
		return last == '.' || last == '!' || last == '?'
			? clean
			: clean + ".";
	}

	private static String buildCoverageKey(
		final String taskName,
		final String location,
		final SlayerTaskStrategy strategy)
	{
		return normalize(taskName)
			+ "|" + normalize(location)
			+ "|" + (strategy == null ? "none" : strategy.getCombatStyle().name())
			+ "|" + normalize(strategy == null ? "" : strategy.getMethod());
	}

	private static String wikiSource(
		final String taskName,
		final SlayerTaskStrategy strategy)
	{
		final String name = safe(taskName).replaceFirst("(?i)^the\\s+", "");
		return strategy != null && strategy.isBoss()
			? "OSRS Wiki: " + name + "/Strategies"
			: "OSRS Wiki: Slayer task/" + name;
	}

	private static boolean preventsExpectedDamage(
		final SlayerMethodRules.DamageControl value)
	{
		return value == SlayerMethodRules.DamageControl.PRAYER_PROTECTED
			|| value == SlayerMethodRules.DamageControl.SAFESPOT
			|| value == SlayerMethodRules.DamageControl.FREEZE_SAFESPOT;
	}

	private static boolean isWilderness(final String value)
	{
		final String normalized = normalize(value);
		return normalized.contains("wilderness")
			|| normalized.contains("revenant caves")
			|| normalized.contains("deep wild");
	}

	private static boolean contains(final String text, final String part)
	{
		return safe(text).toLowerCase(Locale.ENGLISH)
			.contains(safe(part).toLowerCase(Locale.ENGLISH));
	}

	private static String normalize(final String value)
	{
		return safe(value).toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceFirst("^the\\s+", "");
	}

	private static String safe(final String value)
	{
		return value == null ? "" : value.trim();
	}
}
