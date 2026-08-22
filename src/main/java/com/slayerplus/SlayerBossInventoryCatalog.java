package com.slayerplus;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Individually researched inventory policy for every boss encounter currently
 * supported by SlayerPlus.
 *
 * Bosses deliberately do not inherit a generic supply template.  The policy is
 * applied after the exact encounter/method has been resolved, so quantities,
 * loot space, food family, and mandatory utility can differ boss by boss.
 */
public final class SlayerBossInventoryCatalog
{
	public static final String REVIEW_DATE = "2026-08-13";

	private static final Set<String> REVIEWED_BOSS_KEYS = Collections.unmodifiableSet(
		new LinkedHashSet<>(Arrays.asList(
			"abyssal sire",
			"alchemical hydra",
			"amoxliatl",
			"araxxor",
			"artio",
			"callisto",
			"cerberus",
			"dagannoth kings",
			"grotesque guardians",
			"k ril tsutsaroth",
			"kalphite queen",
			"king black dragon",
			"kraken",
			"kree arra",
			"sarachnis",
			"scorpia",
			"skotizo",
			"thermonuclear smoke devil",
			"tztok jad",
			"tzkal zuk",
			"spindel",
			"venenatis",
			"calvar ion",
			"vet ion",
			"vorkath",
			"scurrius",
			"obor",
			"bryophyta",
			"brutus",
			"demonic gorillas",
			"tormented demons",
			"royal titans",
			"shellbane gryphon",
			"barrows brothers",
			"chaos elemental",
			"chaos fanatic",
			"crazy archaeologist",
			"deranged archaeologist",
			"duke sucellus",
			"general graardor",
			"giant mole",
			"maggot king",
			"phantom muspah",
			"leviathan",
			"whisperer",
			"vardorvis",
			"commander zilyana",
			"zulrah"
		))
	);

	private SlayerBossInventoryCatalog() { }

	public static Set<String> getReviewedBossKeys()
	{
		return REVIEWED_BOSS_KEYS;
	}

	/**
	 * Applies the exact researched boss inventory policy.  Returns false for an
	 * unknown encounter so callers can fail closed instead of silently applying
	 * a broad boss fallback.
	 */
	public static boolean apply(
		final String taskName,
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		final String task = canonicalBossKey(taskName);
		if (rules == null || !REVIEWED_BOSS_KEYS.contains(task))
		{
			return false;
		}

		/* Remove any generic strategy-level fill before exact boss policy wins. */
		rules.inventoryTarget(28)
			.noInventoryFill()
			.includeRunePouchForMagic(false)
			.food(
				"High-healing food",
				"anglerfish", "manta ray", "dark crab",
				"moonlight antelope", "shark", "sea turtle",
				"cooked karambwan", "monkfish"
			);

		switch (task)
		{
			case "abyssal sire":
				rules.restoreSlots(4).foodSlots(7).reservedLootSlots(0)
					.includeStyleBoost(false)
					.requiredItem("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredItem("Respiratory-system weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"scorching bow", "tumeken s shadow", "sanguinesti staff",
						"trident of the swamp", "trident of the seas")
					.requiredItem("Special attack weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"dragon warhammer", "bandos godsword", "elder maul",
						"burning claws", "dragon claws")
					.requiredItem("Ash sanctifier", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "ash sanctifier")
					.fillRemainingWithFood();
				return true;
			case "alchemical hydra":
				/* Balanced multi-kill trip; most Hydra drops stack or can be alched. */
				rules.restoreSlots(9).foodSlots(6).reservedLootSlots(3)
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"antidote plus plus", "sanfew serum", "superantipoison")
					.fillRemainingWithFood();
				return true;
			case "amoxliatl":
				rules.restoreSlots(6).foodSlots(6).reservedLootSlots(0)
					.includeStyleBoost(false)
					.requiredItem("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredItem("Special attack weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"burning claws", "dragon claws", "dragon dagger")
					.requiredItem("Ectoplasmator", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "ectoplasmator")
					.requiredItem("Exit teleport", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"teleport to house", "construction cape", "max cape", "desert amulet 4")
					.fillRemainingWithFood();
				return true;
			case "araxxor":
				/* Current Wiki multi-kill package: carry both boosts for the melee
				 * kill and ranged araxyte switch, retain two pickup cells, and fill
				 * every other cell with actual sustain rather than blank space. */
				rules.restoreSlots(4).foodSlots(0).reservedLootSlots(2)
					.includeStyleBoost(false)
					.requiredItem("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredItem("Divine ranging potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine ranging potion", "divine bastion potion",
						"ranging potion", "bastion potion")
					.requiredItem("Extended anti-venom+", 1,
						SlayerMethodRules.InventoryGroup.PROTECTION,
						"extended anti venom plus", "anti venom plus",
						"antidote plus plus", "sanfew serum")
					.requiredItem("Special attack weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"elder maul", "burning claws", "dragon claws",
						"dragon warhammer", "voidwaker")
					.requiredItem("Safe araxyte weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"noxious halberd", "heavy ballista",
						"hunters sunlight crossbow", "rune crossbow", "karil s crossbow",
						"dragon halberd", "crystal halberd",
						"dharok s greataxe", "zombie axe")
					.requiredSlots("Combo food", 2,
						SlayerMethodRules.InventoryGroup.FOOD,
						"cooked karambwan", "halibut")
					.fillRemainingWithFood();
				return true;
			case "artio":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(4).foodSlots(0).reservedLootSlots(2)
					.spell(SlayerMethodRules.Spellbook.ANCIENT, "Ice Barrage or Ice Burst")
					.runePouch(true)
					.pouchRune(ItemID.WATERRUNE, "Water", 1000)
					.pouchRune(ItemID.BLOODRUNE, "Blood", 500)
					.pouchRune(ItemID.DEATHRUNE, "Death", 1000)
					.food("Blighted high-healing food",
						"blighted anglerfish", "blighted manta ray",
						"blighted karambwan", "anglerfish", "manta ray")
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem("Emergency Wilderness teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					.requiredItem(SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY, 1,
						SlayerMethodRules.InventoryGroup.UTILITY, SlayerPotionPolicy.staminaAlternatives())
					.requiredItem("Freeze autocast switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"ice ancient sceptre", "ancient sceptre", "kodai wand",
						"nightmare staff", "master wand", "ancient staff")
					.fillRemainingWithFood();
				return true;
			case "callisto":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(5).foodSlots(0).reservedLootSlots(2)
					.spell(SlayerMethodRules.Spellbook.ANCIENT, "Ice Barrage or Ice Burst")
					.runePouch(true)
					.pouchRune(ItemID.WATERRUNE, "Water", 1000)
					.pouchRune(ItemID.BLOODRUNE, "Blood", 500)
					.pouchRune(ItemID.DEATHRUNE, "Death", 1000)
					.food("Blighted high-healing food",
						"blighted anglerfish", "blighted manta ray",
						"blighted karambwan", "anglerfish", "manta ray")
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem("Emergency Wilderness teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					.requiredItem(SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY, 1,
						SlayerMethodRules.InventoryGroup.UTILITY, SlayerPotionPolicy.staminaAlternatives())
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"antidote plus plus", "antidote plus", "superantipoison")
					.requiredItem("Freeze autocast switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"ice ancient sceptre", "ancient sceptre", "kodai wand",
						"nightmare staff", "master wand", "ancient staff")
					.fillRemainingWithFood();
				return true;
			case "cerberus":
				/* Short trips are supply-limited: fill every remaining slot with food. */
				rules.restoreSlots(7).foodSlots(0).reservedLootSlots(0)
					.requiredItem("Soul-attack one-handed weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"emberlight", "arclight", "osmumten s fang", "abyssal whip")
					.requiredItem("Special attack weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"burning claws", "dragon claws", "elder maul", "voidwaker")
					.fillRemainingWithFood();
				return true;
			case "dagannoth kings":
				/* Tribrid switches consume slots; prayer sustain matters more than food. */
				rules.restoreSlots(8).foodSlots(4).reservedLootSlots(5)
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"antidote plus plus", "sanfew serum", "superantipoison")
					.requiredItem(
						SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
						1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						SlayerPotionPolicy.staminaAlternatives()
					)
					.fillRemainingWithFood();
				return true;
			case "grotesque guardians":
				/*
				 * Current Wiki long-trip setup: a melee base, complete Dawn ranged
				 * switches, nine restores, up to seven food/brews, both combat boosts, a
				 * hammer, and an optional Arceuus utility package. Empty switch cells
				 * become food so lower-progression players still receive a full trip.
				 */
				rules.restoreSlots(9).foodSlots(6).reservedLootSlots(0)
					.includeStyleBoost(false)
					.requiredItem("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredItem("Divine ranging potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine ranging potion", "ranging potion")
					.requiredItem("Dawn primary ranged weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"venator bow", "toxic blowpipe", "bow of faerdhinen",
						"eclipse atlatl", "hunters sunlight crossbow", "magic shortbow i")
					.requiredItem("Ranged necklace switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"necklace of anguish", "amulet of fury", "amulet of glory")
					.requiredItem("Ranged cape switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"dizana s quiver", "dizana s max cape", "ava s assembler",
						"masori assembler", "assembler max cape", "ava s accumulator",
						"accumulator max cape", "ranging cape")
					.requiredItem("Ranged body switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"masori body f", "masori body", "crystal body",
						"eclipse moon chestplate", "blessed body",
						"karil s leathertop", "black d hide body")
					.requiredItem("Ranged legs switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"masori chaps f", "masori chaps", "crystal legs",
						"eclipse moon tassets", "blessed chaps",
						"karil s leatherskirt", "black d hide chaps")
					.requiredItem("Rock hammer", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"granite hammer", "rock hammer", "rock thrownhammer")
					.fillRemainingWithFood();
				return true;
			case "k ril tsutsaroth":
				/*
				 * The current solo default is Scorching bow + Lightbearer. Nine
				 * restores support the documented long trip; the remaining cells
				 * become food after poison protection and the method's boost.
				 */
				rules.restoreSlots(9).foodSlots(0).reservedLootSlots(0)
					.spell(SlayerMethodRules.Spellbook.ANCIENT, "Blood Barrage")
					.runePouch(true)
					.pouchRune(ItemID.FIRERUNE, "Fire", 1000)
					.pouchRune(ItemID.BLOODRUNE, "Blood", 500)
					.pouchRune(ItemID.DEATHRUNE, "Death", 1000)
					.requiredItem("Blood Barrage weapon switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"kodai wand", "nightmare staff", "ancient sceptre",
						"master wand", "ancient staff")
					.requiredItem("Magic body switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"ancestral robe top", "virtus robe top", "ahrim s robetop",
						"bloodbark body", "blue moon chestplate", "infinity top",
						"mystic robe top")
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"extended anti venom plus", "anti venom plus", "antidote plus plus",
						"sanfew serum", "superantipoison", "antipoison")
					.requiredItem("Zamorak protection switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"unholy blessing", "zamorak bracers", "zamorak d hide body",
						"zamorak d hide chaps", "zamorak cape", "zamorak cloak",
						"zamorak stole", "zamorak mitre", "zamorak robe top",
						"zamorak robe legs", "zamorak full helm", "zamorak platebody",
						"zamorak platelegs", "zamorak plateskirt", "zamorak kiteshield")
					.fillRemainingWithFood();
				if (strategy != null
					&& strategy.getCombatStyle() == SlayerTaskStrategy.CombatStyle.RANGED)
				{
					rules.includeStyleBoost(false)
						.requiredSlots("Divine ranging potion", 3,
							SlayerMethodRules.InventoryGroup.BOOST,
							"divine ranging potion", "ranging potion");
				}
				else
				{
					rules.includeStyleBoost(false)
						.requiredSlots("Divine super combat potion", 3,
							SlayerMethodRules.InventoryGroup.BOOST,
							"divine super combat potion", "super combat potion")
						.requiredItem("Special attack weapon", 1,
							SlayerMethodRules.InventoryGroup.SWITCH,
							"saradomin godsword", "voidwaker", "dragon claws",
							"ancient godsword", "elder maul");
				}
				return true;
			case "kalphite queen":
				if (strategy != null
					&& strategy.getCombatStyle() == SlayerTaskStrategy.CombatStyle.MAGIC)
				{
					rules.restoreSlots(1).foodSlots(0).reservedLootSlots(0)
						.requiredItem("Magic boost", 1,
							SlayerMethodRules.InventoryGroup.BOOST,
							"saturated heart", "imbued heart")
						.requiredItem("Poison protection", 1,
							SlayerMethodRules.InventoryGroup.PROTECTION,
							"sanfew serum", "antidote plus plus", "superantipoison")
						.requiredItem("House teleport", 1,
							SlayerMethodRules.InventoryGroup.UTILITY,
							"teleport to house", "construction cape", "max cape")
						.requiredItem("Special attack weapon", 1,
							SlayerMethodRules.InventoryGroup.SWITCH,
							"eye of ayak", "eldritch nightmare staff",
							"ancient godsword", "saradomin godsword", "toxic blowpipe")
						.fillRemainingWithFood();
					return true;
				}
				rules.restoreSlots(3).foodSlots(0).reservedLootSlots(0)
					.includeStyleBoost(false)
					.requiredItem("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredItem("Divine bastion potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine bastion potion", "bastion potion",
						"divine ranging potion", "ranging potion")
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"antidote plus plus", "sanfew serum", "superantipoison")
					.requiredItem("Defence-reduction special weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"elder maul", "dragon warhammer")
					.requiredItem("Ranged weapon switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"twisted bow", "bow of faerdhinen", "toxic blowpipe",
						"zaryte crossbow", "dragon crossbow", "rune crossbow")
					.requiredItem("Ranged body switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"masori body f", "masori body", "crystal body",
						"armadyl chestplate", "blessed body", "karil s leathertop",
						"black d hide body")
					.requiredItem("Ranged legs switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"masori chaps f", "masori chaps", "crystal legs",
						"armadyl chainskirt", "blessed chaps", "karil s leatherskirt",
						"black d hide chaps")
					.requiredItem("Ranged necklace switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"necklace of rupture", "necklace of anguish", "amulet of fury")
					.requiredItem("Ranged cape switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"dizana s quiver", "dizana s max cape", "ava s assembler",
						"masori assembler", "assembler max cape", "ava s accumulator")
					.requiredItem("House teleport", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"teleport to house", "construction cape", "max cape")
					.fillRemainingWithFood();
				return true;
			case "king black dragon":
				rules.restoreSlots(4).foodSlots(0).reservedLootSlots(1)
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"antidote plus plus", "sanfew serum", "superantipoison", "antipoison")
					.requiredItem("Dragonfire protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						SlayerPotionPolicy.shieldedAntifireAlternatives())
					.requiredItem("Emergency teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"teleport to house", "royal seed pod", "seed pod")
					.fillRemainingWithFood();
				return true;
			case "kraken":
				rules.restoreSlots(8).foodSlots(4).reservedLootSlots(7)
					.requiredItem("Fishing explosive", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"fishing explosive")
					.fillRemainingWithFood();
				return true;
			case "kree arra":
				/* Armadyl solo inventories are brew/restore heavy rather than food heavy. */
				rules.restoreSlots(9).foodSlots(0).reservedLootSlots(0)
					.spell(SlayerMethodRules.Spellbook.ANCIENT, "Blood Barrage")
					.runePouch(true)
					.pouchRune(ItemID.FIRERUNE, "Fire", 1000)
					.pouchRune(ItemID.BLOODRUNE, "Blood", 500)
					.pouchRune(ItemID.DEATHRUNE, "Death", 1000)
					.requiredItem(
						SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
						1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						SlayerPotionPolicy.staminaAlternatives()
					)
					.requiredSlots("Saradomin brew", 6, SlayerMethodRules.InventoryGroup.FOOD,
						"saradomin brew")
					.includeStyleBoost(false)
					.requiredSlots("Divine ranging potion", 2,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine ranging potion", "ranging potion")
					.requiredItem("Mith grapple", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "mith grapple")
					.requiredItem("Armadyl protection", 1,
						SlayerMethodRules.InventoryGroup.PROTECTION,
						"honourable blessing", "armadyl pendant", "book of law",
						"armadyl bracers", "armadyl d hide boots",
						"armadyl d hide body", "armadyl chaps", "armadyl cloak")
					.requiredItem("Zamorak protection", 1,
						SlayerMethodRules.InventoryGroup.PROTECTION,
						"unholy blessing", "unholy book", "zamorak bracers",
						"zamorak d hide boots", "zamorak d hide body",
						"zamorak chaps", "zamorak cloak")
					.requiredItem("Blood Barrage weapon switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"eldritch nightmare staff", "kodai wand", "blood ancient sceptre",
						"ancient sceptre", "master wand", "ancient staff")
					.requiredItem("Ranged weapon switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"zaryte crossbow", "twisted bow", "bow of faerdhinen",
						"dragon crossbow", "armadyl crossbow", "toxic blowpipe")
					.requiredItem("Ecumenical key", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "ecumenical key")
					.requiredItem("Bones to peaches", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "bones to peaches")
					.requiredItem("One-click exit teleport", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"ectophial", "teleport to house", "royal seed pod", "seed pod")
					.fillRemainingWithFood();
				return true;
			case "sarachnis":
				rules.restoreSlots(6).foodSlots(0).reservedLootSlots(1)
					.requiredItem("Web-cutting backup", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"knife", "slash weapon")
					.fillRemainingWithFood();
				return true;
			case "scorpia":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(6).foodSlots(0).reservedLootSlots(2)
					.requiredSlots("Saradomin brew", 4, SlayerMethodRules.InventoryGroup.FOOD, "saradomin brew")
					.spell(SlayerMethodRules.Spellbook.ANCIENT, "Ice Barrage or Ice Burst")
					.runePouch(true)
					.pouchRune(ItemID.WATERRUNE, "Water", 1000)
					.pouchRune(ItemID.BLOODRUNE, "Blood", 500)
					.pouchRune(ItemID.DEATHRUNE, "Death", 1000)
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"sanfew serum", "extended anti venom plus",
						"anti venom plus", "antidote plus plus")
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem("Level-30 Wilderness escape", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					.requiredItem(SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY, 1,
						SlayerMethodRules.InventoryGroup.UTILITY, SlayerPotionPolicy.staminaAlternatives())
					.food("Blighted high-healing food",
						"blighted anglerfish", "blighted manta ray", "blighted karambwan",
						"anglerfish", "manta ray", "cooked karambwan")
					.fillRemainingWithFood();
				return true;
			case "skotizo":
				rules.restoreSlots(3).foodSlots(0).reservedLootSlots(0)
					.requiredItem("Dark totem", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "dark totem")
					.requiredItem("Emergency teleport", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"teleport to house", "construction cape", "royal seed pod")
					.requiredItem(
						SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
						1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						SlayerPotionPolicy.staminaAlternatives()
					)
					.fillRemainingWithFood();
				return true;
			case "thermonuclear smoke devil":
				/* Exact freeze/melee quantities are finalized by method-specific rules. */
				return true;
			case "tztok jad":
				rules.restoreSlots(0).foodSlots(0).reservedLootSlots(0)
					.includeStyleBoost(false)
					.requiredSlots("Saradomin brew", 8, SlayerMethodRules.InventoryGroup.FOOD, "saradomin brew")
					.requiredSlots("Super restore", 15, SlayerMethodRules.InventoryGroup.RESTORE,
						"super restore", "prayer potion")
					.requiredItem("Ranging potion", 1, SlayerMethodRules.InventoryGroup.BOOST,
						"divine ranging potion", "ranging potion")
					.requiredSlots(
						SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
						2,
						SlayerMethodRules.InventoryGroup.UTILITY,
						SlayerPotionPolicy.staminaAlternatives()
					)
					/* Current Fight Caves guidance fills any spare cells with prayer. */
					.fillRemainingWithRestore();
				return true;
			case "tzkal zuk":
				return applyTzKalZukInventory(rules, strategy);
			case "spindel":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(4).foodSlots(0).reservedLootSlots(1)
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem("Emergency Wilderness teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					.requiredItem(SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY, 1,
						SlayerMethodRules.InventoryGroup.UTILITY, SlayerPotionPolicy.staminaAlternatives())
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"sanfew serum", "extended anti venom plus",
						"anti venom plus", "antidote plus plus")
					.food("Blighted high-healing food",
						"blighted anglerfish", "blighted manta ray", "blighted karambwan",
						"anglerfish", "manta ray", "cooked karambwan")
					.fillRemainingWithFood();
				return true;
			case "venenatis":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(5).foodSlots(0).reservedLootSlots(1)
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem("Emergency Wilderness teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					.requiredItem(SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY, 1,
						SlayerMethodRules.InventoryGroup.UTILITY, SlayerPotionPolicy.staminaAlternatives())
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"sanfew serum", "extended anti venom plus",
						"anti venom plus", "antidote plus plus")
					.food("Blighted high-healing food",
						"blighted anglerfish", "blighted manta ray", "blighted karambwan",
						"anglerfish", "manta ray", "cooked karambwan")
					.fillRemainingWithFood();
				return true;
			case "calvar ion":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(4).foodSlots(8).reservedLootSlots(3)
					.requiredSlots("Saradomin brew", 2, SlayerMethodRules.InventoryGroup.FOOD, "saradomin brew")
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem("Emergency Wilderness teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					.food("Blighted high-healing food",
						"blighted anglerfish", "blighted manta ray", "blighted karambwan",
						"anglerfish", "manta ray", "cooked karambwan")
					.fillRemainingWithFood();
				return true;
			case "vet ion":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(5).foodSlots(0).reservedLootSlots(1)
					.requiredSlots("Saradomin brew", 2, SlayerMethodRules.InventoryGroup.FOOD, "saradomin brew")
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem("Emergency Wilderness teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					.food("Blighted high-healing food",
						"blighted anglerfish", "blighted manta ray", "blighted karambwan",
						"anglerfish", "manta ray", "cooked karambwan")
					.fillRemainingWithFood();
				return true;
			case "vorkath":
				rules.restoreSlots(
					strategy != null
						&& strategy.getCombatStyle()
							== SlayerTaskStrategy.CombatStyle.MELEE
							? 3 : 4
				).foodSlots(0).reservedLootSlots(0)
					.spell(
						SlayerMethodRules.Spellbook.STANDARD,
						"Crumble Undead"
					)
					.requiredItem(
						SlayerPotionPolicy.EXTENDED_SUPER_ANTIFIRE_DISPLAY,
						1,
						SlayerMethodRules.InventoryGroup.PROTECTION,
						SlayerPotionPolicy.fullAntifireAlternatives()
					)
					.requiredItem(
						SlayerPotionPolicy.EXTENDED_ANTIVENOM_DISPLAY,
						1,
						SlayerMethodRules.InventoryGroup.PROTECTION,
						SlayerPotionPolicy.antivenomAlternatives()
					)
					.runePouch(true)
					.pouchRune(ItemID.AIRRUNE, "Air", 100)
					.pouchRune(ItemID.EARTHRUNE, "Earth", 100)
					.pouchRune(ItemID.CHAOSRUNE, "Chaos", 100)
					.pouchRune(ItemID.LAWRUNE, "Law", 100)
					.requiredItem(
						"Crumble Undead autocast switch",
						1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"slayer s staff e", "slayer s staff", "toxic staff of the dead",
						"staff of the dead", "staff of light", "staff of balance",
						"void knight mace", "skull sceptre i"
					)
					.requiredItem(
						"Tick-safe healing",
						1,
						SlayerMethodRules.InventoryGroup.FOOD,
						"guthix rest", "cooked karambwan"
					);
				if (strategy == null
					|| strategy.getCombatStyle()
						== SlayerTaskStrategy.CombatStyle.RANGED)
				{
					rules.requiredItem(
						"Diamond dragon bolts (e) switch",
						1,
						SlayerMethodRules.InventoryGroup.RUNES_AMMO,
						"diamond dragon bolts e", "diamond bolts e"
					);
					rules.requiredItem(
						"Special attack weapon",
						1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"zaryte crossbow", "elder maul", "tonalztics of ralos",
						"bandos godsword", "dragon warhammer", "armadyl crossbow",
						"toxic blowpipe"
					);
				}
				else
				{
					rules.requiredItem(
						"Special attack weapon",
						1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"burning claws", "voidwaker", "dragon claws", "elder maul",
						"bandos godsword", "dragon warhammer", "bone dagger"
					);
				}
				rules.fillRemainingWithFood();
				return true;
			case "scurrius":
				rules.restoreSlots(6).foodSlots(12).reservedLootSlots(4)
					.fillRemainingWithFood();
				return true;
			case "obor":
				rules.restoreSlots(2).foodSlots(0).reservedLootSlots(1)
					.requiredItem("Giant key", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"giant key")
					.fillRemainingWithFood();
				return true;
			case "bryophyta":
				rules.restoreSlots(2).foodSlots(10).reservedLootSlots(5)
					.requiredItem("Mossy key", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"mossy key")
					.requiredItem("Growthling tool", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"crystal axe", "infernal axe", "3rd age axe", "dragon axe",
						"rune axe", "magic secateurs", "adamant axe", "mithril axe",
						"black axe", "steel axe", "iron axe", "bronze axe")
					.requiredItem("Poison protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						"antidote plus plus", "sanfew serum", "superantipoison", "antipoison")
					.fillRemainingWithFood();
				return true;
			case "brutus":
				rules.restore("prayer potion", "super restore")
					.restoreSlots(0).foodSlots(8).reservedLootSlots(15)
					.includeStyleBoost(false)
					.requiredSlots(
						"Divine ranging potion",
						1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine ranging potion", "ranging potion"
					)
					.requiredItem("Cowbell amulet", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"cowbell amulet")
					.requiredItem("Optional cooking tool", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "tinderbox");
				return true;
			case "demonic gorillas":
				rules.restoreSlots(5).foodSlots(8).reservedLootSlots(6)
					.requiredItem("Emergency teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "ring of dueling")
					.spell(SlayerMethodRules.Spellbook.STANDARD, "High Level Alchemy")
					.runePouch(true)
					.pouchRune(ItemID.FIRERUNE, "Fire", 1000)
					.pouchRune(ItemID.NATURERUNE, "Nature", 300);
				return true;
			case "tormented demons":
				rules.restoreSlots(7).foodSlots(5).reservedLootSlots(3)
					.includeStyleBoost(false)
					.requiredItem("Secondary weapon switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"scorching bow", "twisted bow", "purging staff", "toxic blowpipe",
						"bow of faerdhinen", "eclipse atlatl",
						"hunters sunlight crossbow", "dragon crossbow", "rune crossbow")
					.requiredItem("Secondary body switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"masori body f", "masori body", "eclipse moon chestplate",
						"crystal body", "blessed body", "black d hide body", "mixed hide top",
						"ancestral robe top", "virtus robe top", "ahrim s robetop",
						"blue moon chestplate", "bloodbark body", "mystic robe top")
					.requiredItem("Secondary legs switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"masori chaps f", "masori chaps", "eclipse moon tassets",
						"crystal legs", "blessed chaps", "black d hide chaps", "mixed hide legs",
						"ancestral robe bottom", "virtus robe bottom", "ahrim s robeskirt",
						"blue moon tassets", "bloodbark legs", "mystic robe bottom")
					.requiredItem("Magic off-hand switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"elidinis ward f", "arcane spirit shield", "elidinis ward",
						"malediction ward", "ancient wyvern shield", "mage s book",
						"book of darkness", "book of the dead")
					.requiredItem("Shield-down crush weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"dharok s greataxe", "dragon 2h sword", "tzhaar ket om",
						"granite maul", "rune 2h sword")
					.requiredItem("Special attack weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"burning claws", "dragon claws", "abyssal dagger",
						"saradomin godsword", "dragon dagger")
					.requiredSlots("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredSlots("Divine ranging potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine ranging potion", "ranging potion")
					.requiredItem("Emergency teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "teleport to house")
					.fillRemainingWithFood();
				return true;
			case "royal titans":
				/*
				 * Water/Fire Wave capability is an encounter mechanic, not a
				 * generic Magic recommendation.  Keep all four rune types in the
				 * pouch when available so the inventory still has room for the
				 * melee/ranged switches and seed-heavy loot.
				 */
				rules.restoreSlots(5).foodSlots(0).reservedLootSlots(0)
					.includeStyleBoost(false)
					.spell(SlayerMethodRules.Spellbook.STANDARD, "Water Wave / Fire Wave")
					.runePouch(true)
					.pouchRune(ItemID.AIRRUNE, "Air", 500)
					.pouchRune(ItemID.BLOODRUNE, "Blood", 100)
					.pouchRune(ItemID.WATERRUNE, "Water", 700)
					.pouchRune(ItemID.FIRERUNE, "Fire", 700)
					.requiredItem("Ranged weapon switch", 1, SlayerMethodRules.InventoryGroup.SWITCH,
						"toxic blowpipe", "bow of faerdhinen", "eclipse atlatl",
						"zaryte crossbow", "armadyl crossbow", "dragon crossbow",
						"hunters sunlight crossbow", "rune crossbow")
					.requiredItem("Ranged body switch", 1, SlayerMethodRules.InventoryGroup.SWITCH,
						"masori body f", "masori body", "crystal body", "armadyl chestplate",
						"eclipse moon chestplate", "blessed body", "black d hide body")
					.requiredItem("Ranged legs switch", 1, SlayerMethodRules.InventoryGroup.SWITCH,
						"masori chaps f", "masori chaps", "crystal legs", "armadyl chainskirt",
						"eclipse moon tassets", "blessed chaps", "black d hide chaps")
					.requiredItem("Elemental spell staff", 1, SlayerMethodRules.InventoryGroup.SWITCH,
						"twinflame staff", "purging staff", "kodai wand",
						"master wand", "ancient staff")
					.requiredItem("Magic cape switch", 1, SlayerMethodRules.InventoryGroup.SWITCH,
						"imbued god cape", "god cape", "ardougne cloak 4")
					.requiredItem("Magic body switch", 1, SlayerMethodRules.InventoryGroup.SWITCH,
						"ancestral robe top", "virtus robe top", "ahrim s robetop",
						"blue moon chestplate", "bloodbark body", "mystic robe top")
					.requiredItem("Magic legs switch", 1, SlayerMethodRules.InventoryGroup.SWITCH,
						"ancestral robe bottom", "virtus robe bottom", "ahrim s robeskirt",
						"blue moon tassets", "bloodbark legs", "mystic robe bottom")
					.requiredItem("Magic amulet switch", 1, SlayerMethodRules.InventoryGroup.SWITCH,
						"occult necklace", "amulet of fury", "amulet of glory")
					.requiredItem("Magic glove switch", 1, SlayerMethodRules.InventoryGroup.SWITCH,
						"tormented bracelet", "barrows gloves", "rune gloves")
					.requiredItem("Melee special attack weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"dragon claws", "burning claws", "crystal halberd",
						"dragon dagger")
					.requiredItem("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredItem("Divine ranging potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine ranging potion", "ranging potion")
					.requiredItem("Emergency teleport", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"teleport to house", "construction cape", "max cape")
					.fillRemainingWithFood();
				return true;
			case "shellbane gryphon":
				rules.restoreSlots(4).foodSlots(10).reservedLootSlots(5)
					.includeStyleBoost(false)
					.requiredItem("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredItem("Tortugan shield", 1,
						SlayerMethodRules.InventoryGroup.SWITCH, "tortugan shield")
					.fillRemainingWithFood();
				return true;
			case "barrows brothers":
				rules.restoreSlots(3).foodSlots(6).reservedLootSlots(5)
					.includeStyleBoost(false)
					.requiredItem("Spade", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "spade")
					.requiredItem("Ranged weapon switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"toxic blowpipe", "hunters sunlight crossbow", "magic shortbow i",
						"rune crossbow", "dorgeshuun crossbow")
					.requiredItem("Melee reward-potential weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"dragon dagger", "abyssal whip", "dragon scimitar",
						"rune scimitar")
					.requiredItem("Ranging potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine ranging potion", "ranging potion")
					.requiredItem("Combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion",
						"combat potion")
					.requiredItem("Stat restore", 1,
						SlayerMethodRules.InventoryGroup.RESTORE,
						"super restore", "restore potion")
					.requiredItem("Tunnel shortcut", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"strange old lockpick")
					.requiredItem("Exit and restoration teleport", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"ring of dueling", "construction cape", "max cape",
						"teleport to house")
					.fillRemainingWithFood();
				return true;
			case "chaos elemental":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(4).foodSlots(0).reservedLootSlots(0)
					.food("Item-leaving food", "summer pie", "wild pie", "curry")
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem("Level-30 Wilderness escape", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					.requiredItem(SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY, 1,
						SlayerMethodRules.InventoryGroup.UTILITY, SlayerPotionPolicy.staminaAlternatives())
					.requiredItem("Venom protection", 1, SlayerMethodRules.InventoryGroup.PROTECTION,
						SlayerPotionPolicy.antivenomAlternatives())
					.fillRemainingWithFood();
				return true;
			case "chaos fanatic":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(5).foodSlots(0).reservedLootSlots(0)
					.food("Item-leaving food", "summer pie", "wild pie", "curry")
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem("Level-30 Wilderness escape", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					/*
					 * Arrival travel remains owned by Shortest Path.  Do not
					 * hard-code a Ghorrock teleport into the boss inventory.
					 */
					.fillRemainingWithFood();
				return true;
			case "crazy archaeologist":
				rules.restore("blighted super restore", "super restore")
					.restoreSlots(4).foodSlots(8).reservedLootSlots(9)
					.requiredItem("Looting bag", 1, SlayerMethodRules.InventoryGroup.UTILITY, "looting bag")
					.requiredItem(SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY, 1,
						SlayerMethodRules.InventoryGroup.UTILITY, SlayerPotionPolicy.staminaAlternatives())
					.requiredItem("Emergency Wilderness teleport", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"royal seed pod", "seed pod", "dragonstone teleport scroll")
					.fillRemainingWithFood();
				return true;
			case "deranged archaeologist":
				rules.restoreSlots(3).foodSlots(5).reservedLootSlots(12);
				return true;
			case "duke sucellus":
				rules.restoreSlots(5).foodSlots(7).reservedLootSlots(0)
					.requiredSlots("Saradomin brew", 2, SlayerMethodRules.InventoryGroup.FOOD, "saradomin brew")
					.requiredItem("Best owned pickaxe", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"dragon pickaxe", "crystal pickaxe", "rune pickaxe", "adamant pickaxe")
					.fillRemainingWithFood();
				return true;
			case "general graardor":
				rules.requiredItem(
					SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
					1,
					SlayerMethodRules.InventoryGroup.UTILITY,
					SlayerPotionPolicy.staminaAlternatives()
				);
				if (strategy != null
					&& strategy.getCombatStyle() == SlayerTaskStrategy.CombatStyle.RANGED)
				{
					rules.restoreSlots(8).foodSlots(5).reservedLootSlots(5);
				}
				else
				{
					rules.restoreSlots(6).foodSlots(0).reservedLootSlots(1)
						.requiredSlots("Saradomin brew", 9, SlayerMethodRules.InventoryGroup.FOOD,
							"saradomin brew");
				}
				rules.reservedLootSlots(0).fillRemainingWithFood();
				return true;
			case "giant mole":
				rules.restoreSlots(11).foodSlots(0).reservedLootSlots(3)
					.requiredItem("Spade", 1, SlayerMethodRules.InventoryGroup.UTILITY, "spade")
					.requiredSlots(
						SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
						2,
						SlayerMethodRules.InventoryGroup.UTILITY,
						SlayerPotionPolicy.staminaAlternatives()
					)
					.requiredItem("Mole locator", 1, SlayerMethodRules.InventoryGroup.UTILITY,
						"falador shield 4", "falador shield 3")
					.fillRemainingWithFood();
				return true;
			case "maggot king":
				/*
				 * Current reviewed repeat-kill method: Fire Magic at range, a
				 * complete crush switch for punish windows, poison protection,
				 * and a crush special attack backed by the Surge potion.  Empty
				 * progression cells become food so every account receives a full
				 * 28-slot trip rather than an under-supplied boss inventory.
				 */
				rules.restoreSlots(4).foodSlots(0).reservedLootSlots(0)
					.layout(SlayerMethodRules.LayoutProfile.HYBRID_BOSS)
					.includeStyleBoost(false)
					.spell(SlayerMethodRules.Spellbook.STANDARD, "Fire Surge")
					.runePouch(true)
					.pouchRune(ItemID.WRATHRUNE, "Wrath", 250)
					.pouchRune(ItemID.AIRRUNE, "Air", 1750)
					.pouchRune(ItemID.FIRERUNE, "Fire", 1250)
					.requiredItem("Magic boost", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"saturated heart", "imbued heart", "divine magic potion", "magic potion")
					.requiredItem("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredItem("Surge potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST, "surge potion")
					.requiredItem("Crush punish weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"scythe of vitur", "soulreaper axe", "inquisitor s mace",
						"abyssal bludgeon", "dual macuahuitl", "zombie axe")
					.requiredItem("Crush special attack weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"crimson kisten", "elder maul", "dragon warhammer")
					.requiredItem("Melee amulet switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"amulet of blood fury", "amulet of torture", "amulet of strength", "amulet of glory")
					.requiredItem("Melee cape switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"infernal cape", "fire cape", "mythical cape", "obsidian cape")
					.requiredItem("Melee body switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"inquisitor s hauberk", "torva platebody", "oathplate chest",
						"bandos chestplate",
						"blood moon chestplate", "fighter torso")
					.requiredItem("Melee legs switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"inquisitor s plateskirt", "torva platelegs", "oathplate legs",
						"bandos tassets",
						"blood moon tassets", "obsidian platelegs")
					.requiredItem("Melee glove switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"ferocious gloves", "barrows gloves", "regen bracelet", "combat bracelet")
					.requiredItem("Melee boot switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"avernic treads max", "avernic treads pr et", "avernic treads pe et",
						"avernic treads", "primordial boots", "dragon boots")
					.requiredItem("Melee defender switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"avernic defender", "dragon defender", "dragon defender t",
						"rune defender")
					.requiredItem("Poison protection", 1,
						SlayerMethodRules.InventoryGroup.PROTECTION,
						"extended anti venom plus", "anti venom plus", "sanfew serum",
						"antidote plus plus", "superantipoison")
					.requiredItem("Emergency teleport", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"teleport to house", "construction cape", "max cape", "drakan s medallion")
					.fillRemainingWithFood();
				return true;
			case "phantom muspah":
				rules.restoreSlots(5).foodSlots(0).reservedLootSlots(0)
					.requiredItem("Sapphire-bolt crossbow", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"zaryte crossbow", "dragon crossbow", "armadyl crossbow", "rune crossbow")
					.requiredItem("Enchanted sapphire bolts", 1,
						SlayerMethodRules.InventoryGroup.RUNES_AMMO,
						"sapphire dragon bolts e", "sapphire bolts e")
					.requiredItem(
						SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
						1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						SlayerPotionPolicy.staminaAlternatives()
					)
					.fillRemainingWithFood();
				return true;
			case "leviathan":
				rules.restoreSlots(5).foodSlots(0).reservedLootSlots(0)
					.spell(SlayerMethodRules.Spellbook.ANCIENT, "Shadow spell")
					.runePouch(true)
					.pouchRune(ItemID.AIRRUNE, "Air", 1000)
					.pouchRune(ItemID.DEATHRUNE, "Death", 1000)
					.pouchRune(ItemID.BLOODRUNE, "Blood", 500)
					.pouchRune(ItemID.SOULRUNE, "Soul", 1000)
					.requiredItem("Shadow spell staff", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"ancient sceptre", "thammaron s sceptre", "master wand", "ancient staff")
					.requiredItem("Backside ranged weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"twisted bow", "zaryte crossbow", "dragon crossbow", "rune crossbow")
					.requiredItem(SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY, 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						SlayerPotionPolicy.staminaAlternatives())
					.fillRemainingWithFood();
				return true;
			case "whisperer":
				/* The Blackstone fragment is mandatory. Current Wiki mechanics also
				 * call for one fast ranged Lost Souls weapon and optionally a sustain/
				 * stat-drain special; both are ownership-resolved before restores fill
				 * the remaining trip space. */
				rules.restoreSlots(4).foodSlots(3).reservedLootSlots(0)
					.requiredItem(
						"Blackstone fragment",
						1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"blackstone fragment"
					)
					.requiredItem(
						"Lost Souls weapon",
						1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"venator bow", "toxic blowpipe", "rune thrownaxe"
					)
					.requiredItem(
						"Special attack weapon",
						1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"eldritch nightmare staff", "accursed sceptre",
						"eye of ayak", "toxic blowpipe", "seercull"
					)
					.fillRemainingWithRestore();
				return true;
			case "vardorvis":
				rules.restoreSlots(3).foodSlots(0).reservedLootSlots(0)
					.includeStyleBoost(false)
					.requiredItem("Divine super combat potion", 1,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine super combat potion", "super combat potion")
					.requiredItem("Special attack weapon", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"dragon claws", "burning claws", "arkan blade",
						"voidwaker", "dragon dagger p plus plus", "dragon dagger")
					.requiredItem("Fast Stranglewood return", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"ring of shadows")
					.fillRemainingWithFood();
				return true;
			case "commander zilyana":
				rules.restoreSlots(8).foodSlots(0).reservedLootSlots(0)
					.requiredSlots("Saradomin brew", 9, SlayerMethodRules.InventoryGroup.FOOD, "saradomin brew")
					.requiredItem(
						SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
						2,
						SlayerMethodRules.InventoryGroup.UTILITY,
						SlayerPotionPolicy.staminaAlternatives()
					)
					.requiredSlots("Divine ranging potion", 2,
						SlayerMethodRules.InventoryGroup.BOOST,
						"divine ranging potion", "ranging potion")
					.requiredItem("Minion weapon switch", 1,
						SlayerMethodRules.InventoryGroup.SWITCH,
						"toxic blowpipe", "venator bow", "sanguinesti staff")
					.requiredItem("Ecumenical key", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "ecumenical key")
					.requiredItem("Bones to peaches", 1,
						SlayerMethodRules.InventoryGroup.UTILITY, "bones to peaches")
					.requiredItem("Emergency teleport", 1,
						SlayerMethodRules.InventoryGroup.UTILITY,
						"ghommal s hilt", "teleport to house", "construction cape", "max cape")
					.fillRemainingWithFood();
				return true;
			case "zulrah":
				rules.restoreSlots(3).foodSlots(0).reservedLootSlots(0)
					.requiredItem(
						SlayerPotionPolicy.EXTENDED_ANTIVENOM_DISPLAY,
						1,
						SlayerMethodRules.InventoryGroup.PROTECTION,
						SlayerPotionPolicy.antivenomAlternatives()
					)
					.requiredSlots("Cooked karambwan", 4, SlayerMethodRules.InventoryGroup.FOOD,
						"cooked karambwan")
					.fillRemainingWithFood();
				return true;
			default:
				return false;
		}
	}


	/**
	 * TzKal-Zuk is a complete Inferno run, so its inventory is a fixed
	 * endurance package rather than a generic boss food/restore template.
	 *
	 * The final high-end on-task package uses seven structural inventory slots
	 * with a divine rune pouch: rune pouch, blowpipe, Ancient weapon, mage body,
	 * mage legs, magic off-hand and one compact magic-damage switch. The
	 * remaining 21 slots are authored supplies. A
	 * normal three-rune pouch spills Water into one real inventory slot, so the
	 * overflow naturally displaces only the last bulk supply rather than an
	 * essential switch. Travel is not part of the final Inferno inventory: the
	 * guided staging flow shows the exact Mor Ul Rek travel item in the utility
	 * strip above the 4 x 7 until the east/Zuk bank is opened.
	 */
	private static boolean applyTzKalZukInventory(
		final SlayerMethodRules.Builder rules,
		final SlayerTaskStrategy strategy)
	{
		final String method = strategy == null
			? ""
			: normalize(strategy.getMethod());
		final boolean safetyFirst =
			method.contains("not afk")
				|| method.contains("completion first");
		final boolean efficient =
			strategy != null
				&& strategy.getCostPolicy()
					== SlayerTaskStrategy.CostPolicy.EFFICIENT;
		final String[] ancientWeaponPriority = safetyFirst
			? new String[]{
				"blood ancient sceptre",
				"kodai wand",
				"eldritch nightmare staff",
				"ice ancient sceptre",
				"ancient sceptre",
				"nightmare staff",
				"master wand",
				"ancient staff"
			}
			: new String[]{
				"kodai wand",
				"eldritch nightmare staff",
				"blood ancient sceptre",
				"ice ancient sceptre",
				"ancient sceptre",
				"nightmare staff",
				"master wand",
				"ancient staff"
			};

		/*
		 * Full on-task Inferno package. Soul/Blood/Death are the core pouch
		 * runes for Blood + Ice Barrage; Water is fourth so a normal three-slot
		 * rune pouch naturally spills only Water into the real inventory while a
		 * Divine rune pouch keeps all four inside.
		 *
		 * The final Zuk bank layout intentionally has no permanent travel slot.
		 * The guided staging flow renders the exact Mor Ul Rek travel item above
		 * the 4 x 7 until the east bank is opened, while the complete 28-slot
		 * Inferno loadout remains intact below.
		 */
		rules.restore("super restore", "prayer potion")
			.restoreSlots(0)
			.foodSlots(0)
			.reservedLootSlots(0)
			.includeStyleBoost(false)
			.layout(SlayerMethodRules.LayoutProfile.HYBRID_BOSS)
			.spell(
				SlayerMethodRules.Spellbook.ANCIENT,
				"Blood Barrage + Ice Barrage"
			)
			.runePouch(true)
			.pouchRune(ItemID.SOULRUNE, "Soul", 2000)
			.pouchRune(ItemID.BLOODRUNE, "Blood", 8000)
			.pouchRune(ItemID.DEATHRUNE, "Death", 8000)
			.pouchRune(ItemID.WATERRUNE, "Water", 8000)
			.requiredItem(
				"Toxic blowpipe",
				1,
				SlayerMethodRules.InventoryGroup.SWITCH,
				"toxic blowpipe"
			)
			.requiredItem(
				"Ancient Magicks weapon",
				1,
				SlayerMethodRules.InventoryGroup.SWITCH,
				ancientWeaponPriority
			)
			.requiredItem(
				"Mage body switch",
				1,
				SlayerMethodRules.InventoryGroup.SWITCH,
				"virtus robe top",
				"ancestral robe top",
				"ahrim s robetop",
				"bloodbark body"
			)
			.requiredItem(
				"Mage leg switch",
				1,
				SlayerMethodRules.InventoryGroup.SWITCH,
				"virtus robe bottom",
				"ancestral robe bottom",
				"ahrim s robeskirt",
				"bloodbark legs"
			)
			.requiredItem(
				"Magic off-hand switch",
				1,
				SlayerMethodRules.InventoryGroup.SWITCH,
				"elidinis ward f",
				"elysian spirit shield",
				"crystal shield",
				"elidinis ward"
			)
			.requiredItem(
				"Magic damage switch",
				1,
				SlayerMethodRules.InventoryGroup.SWITCH,
				"confliction gauntlets",
				"occult necklace",
				"tormented bracelet"
			);

		if (safetyFirst)
		{
			/* Completion-first repeat: healing-biased 21-slot supply package. */
			rules.requiredItem(
				SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
				1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				SlayerPotionPolicy.staminaAlternatives()
			)
				.requiredSlots(
					"Bastion potion",
					2,
					SlayerMethodRules.InventoryGroup.BOOST,
					"bastion potion",
					"divine bastion potion"
				)
				.requiredSlots(
					"Prayer regeneration potion",
					3,
					SlayerMethodRules.InventoryGroup.RESTORE,
					"prayer regeneration potion"
				)
				.requiredSlots(
					"Saradomin brew",
					8,
					SlayerMethodRules.InventoryGroup.FOOD,
					"saradomin brew"
				)
				.requiredSlots(
					"Super restore",
					7,
					SlayerMethodRules.InventoryGroup.RESTORE,
					"super restore"
				);
			return true;
		}

		if (efficient)
		{
			/* Resource-efficient repeat while preserving substantial healing. */
			rules.requiredItem(
				SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
				1,
				SlayerMethodRules.InventoryGroup.UTILITY,
				SlayerPotionPolicy.staminaAlternatives()
			)
				.requiredSlots(
					"Bastion potion",
					2,
					SlayerMethodRules.InventoryGroup.BOOST,
					"bastion potion",
					"divine bastion potion"
				)
				.requiredSlots(
					"Prayer regeneration potion",
					2,
					SlayerMethodRules.InventoryGroup.RESTORE,
					"prayer regeneration potion"
				)
				.requiredSlots(
					"Saradomin brew",
					8,
					SlayerMethodRules.InventoryGroup.FOOD,
					"saradomin brew"
				)
				.requiredSlots(
					"Super restore",
					8,
					SlayerMethodRules.InventoryGroup.RESTORE,
					"super restore"
				);
			return true;
		}

		/* Fast repeat: one fewer brew than safety profiles, as reviewed by the Wiki. */
		rules.requiredItem(
			SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
			1,
			SlayerMethodRules.InventoryGroup.UTILITY,
			SlayerPotionPolicy.staminaAlternatives()
		)
			.requiredSlots(
				"Bastion potion",
				2,
				SlayerMethodRules.InventoryGroup.BOOST,
				"divine bastion potion",
				"bastion potion"
			)
			.requiredSlots(
				"Prayer regeneration potion",
				2,
				SlayerMethodRules.InventoryGroup.RESTORE,
				"prayer regeneration potion"
			)
			.requiredSlots(
				"Saradomin brew",
				7,
				SlayerMethodRules.InventoryGroup.FOOD,
				"saradomin brew"
			)
			.requiredSlots(
				"Super restore",
				9,
				SlayerMethodRules.InventoryGroup.RESTORE,
				"super restore"
			);
		return true;
	}

	public static String canonicalBossKey(final String value)
	{
		String key = normalize(value);
		if (key.equals("cave kraken boss"))
		{
			return "kraken";
		}
		if (key.equals("crazy archaeologists"))
		{
			return "crazy archaeologist";
		}
		return key;
	}

	private static String normalize(final String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceFirst("^the\\s+", "");
	}
}
