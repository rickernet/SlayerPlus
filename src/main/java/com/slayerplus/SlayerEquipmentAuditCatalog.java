package com.slayerplus;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;

/**
 * Wiki-audited equipment progressions shared by the individually selected
 * Slayer methods.
 *
 * <p>The strategy catalog still owns the monster-specific weapon and method.
 * This catalog owns every other equipment slot.  Keeping these progressions
 * explicit prevents ItemStats scoring from mixing partial Void/Crystal sets or
 * choosing a defensive item when the reviewed method calls for prayer or
 * damage gear.</p>
 */
public final class SlayerEquipmentAuditCatalog
{
	private static final List<String> MELEE_CAPE = values(
		"infernal cape", "fire cape", "mythical cape", "mixed hide cape",
		"ardougne cloak 4", "ardougne cloak 3", "ardougne cloak 2",
		"cape of accomplishment", "obsidian cape", "legends cape"
	);
	private static final List<String> MELEE_NECK = values(
		"amulet of rancour", "amulet of torture", "amulet of fury",
		"amulet of strength", "amulet of glory", "amulet of power",
		"amulet of accuracy"
	);
	private static final List<String> MELEE_BODY = values(
		"torva platebody", "oathplate chest", "bandos chestplate",
		"blood moon chestplate", "fighter torso", "obsidian platebody",
		"proselyte hauberk", "verac s brassard", "dragon platebody",
		"dragon chainbody", "rune platebody", "adamant platebody",
		"mithril platebody", "steel platebody"
	);
	private static final List<String> MELEE_LEGS = values(
		"torva platelegs", "oathplate legs", "bandos tassets",
		"blood moon tassets", "obsidian platelegs", "proselyte cuisse",
		"proselyte tasset", "verac s plateskirt", "dragon platelegs",
		"dragon plateskirt", "rune platelegs", "rune plateskirt",
		"adamant platelegs", "mithril platelegs", "steel platelegs"
	);
	private static final List<String> MELEE_GLOVES = values(
		"expeditious bracelet", "bracelet of slaughter", "ferocious gloves",
		"barrows gloves", "regen bracelet", "dragon gloves", "rune gloves",
		"combat bracelet", "adamant gloves", "mithril gloves"
	);
	private static final List<String> MELEE_BOOTS = values(
		"avernic treads max", "avernic treads pr pe",
		"avernic treads pr et", "avernic treads pr", "primordial boots",
		"avernic treads pe et", "avernic treads pe", "avernic treads et",
		"avernic treads", "dragon boots", "aranea boots", "echo boots",
		"guardian boots", "rune boots", "mixed hide boots",
		"granite boots", "climbing boots"
	);
	private static final List<String> MELEE_RING = values(
		"ultor ring", "berserker ring i", "berserker ring",
		"brimstone ring", "warrior ring i", "warrior ring",
		"ring of wealth", "explorer s ring 4", "ring of dueling"
	);
	private static final List<String> PRAYER_RING = values(
		"ring of the gods i", "ring of the gods", "ultor ring",
		"berserker ring i", "berserker ring"
	);
	private static final List<String> MELEE_OFFHAND = values(
		"avernic defender", "dragon defender", "dragonfire shield",
		"rune defender",
		"adamant defender", "mithril defender", "black defender",
		"steel defender", "iron defender", "bronze defender",
		"toktz ket xil", "book of war",
		"rune kiteshield", "adamant kiteshield"
	);

	private static final List<String> PRAYER_BODY = values(
		"sunfire fanatic cuirass", "proselyte hauberk", "initiate hauberk",
		"vestment robe top", "monk s robe top"
	);
	private static final List<String> PRAYER_LEGS = values(
		"sunfire fanatic chausses", "proselyte cuisse", "proselyte tasset",
		"initiate cuisse", "vestment robe legs", "monk s robe"
	);
	private static final List<String> RANGED_CAPE = values(
		"dizana s quiver", "dizana s max cape", "ava s assembler",
		"masori assembler", "assembler max cape", "ava s accumulator",
		"accumulator max cape", "ranging cape", "ava s attractor",
		"mixed hide cape"
	);
	private static final List<String> RANGED_NECK = values(
		"necklace of rupture", "necklace of anguish", "bonecrusher necklace",
		"dragonbone necklace", "amulet of fury", "amulet of glory",
		"amulet of power", "amulet of accuracy"
	);
	private static final List<String> RANGED_BODY = values(
		"masori body f", "masori body", "hueycoatl hide body",
		"armadyl chestplate", "blessed body", "karil s leathertop",
		"eclipse moon chestplate", "black d hide body",
		"red d hide body", "blue d hide body", "green d hide body",
		"snakeskin body", "hardleather body", "leather body"
	);
	private static final List<String> RANGED_LEGS = values(
		"masori chaps f", "masori chaps", "hueycoatl hide chaps",
		"armadyl chainskirt", "blessed chaps", "karil s leatherskirt",
		"eclipse moon tassets", "black d hide chaps",
		"red d hide chaps", "blue d hide chaps", "green d hide chaps",
		"snakeskin chaps", "studded chaps", "leather chaps"
	);
	private static final List<String> RANGED_GLOVES = values(
		"expeditious bracelet", "bracelet of slaughter", "zaryte vambraces",
		"barrows gloves", "blessed vambraces", "hueycoatl hide vambraces",
		"black d hide vambraces", "red d hide vambraces",
		"blue d hide vambraces", "green d hide vambraces",
		"combat bracelet", "snakeskin vambraces", "leather gloves"
	);
	private static final List<String> RANGED_BOOTS = values(
		"avernic treads max", "avernic treads pr pe",
		"avernic treads pe et", "avernic treads pe",
		"avernic treads pr et", "avernic treads pr", "avernic treads et",
		"avernic treads", "pegasian boots", "ranger boots",
		"blessed boots", "aranea boots", "boots of brimstone",
		"mixed hide boots", "snakeskin boots", "frog leather boots"
	);
	private static final List<String> RANGED_RING = values(
		"venator ring", "ring of the gods i", "ring of suffering i",
		"archers ring i", "archers ring", "brimstone ring",
		"ring of wealth", "explorer s ring 4", "ring of dueling"
	);
	private static final List<String> RANGED_OFFHAND = values(
		"twisted buckler", "dragonfire ward", "odium ward", "book of law",
		"unholy book", "book of balance", "granite shield",
		"rune kiteshield", "adamant kiteshield"
	);
	private static final List<String> RANGED_PRAYER_BODY = values(
		"blessed body", "armadyl chestplate", "masori body f", "masori body",
		"hueycoatl hide body", "karil s leathertop", "black d hide body",
		"red d hide body", "blue d hide body", "green d hide body"
	);
	private static final List<String> RANGED_PRAYER_LEGS = values(
		"blessed chaps", "armadyl chainskirt", "masori chaps f", "masori chaps",
		"hueycoatl hide chaps", "karil s leatherskirt", "black d hide chaps",
		"red d hide chaps", "blue d hide chaps", "green d hide chaps"
	);

	private static final List<String> MAGIC_CAPE = values(
		"imbued god cape", "god cape", "ardougne cloak 4",
		"cape of accomplishment", "vestment cloak", "obsidian cape"
	);
	private static final List<String> MAGIC_NECK = values(
		"occult necklace", "bonecrusher necklace", "dragonbone necklace",
		"amulet of fury", "amulet of glory", "amulet of magic",
		"amulet of power", "amulet of accuracy"
	);
	private static final List<String> MAGIC_BODY = values(
		"virtus robe top", "ancestral robe top", "blue moon chestplate",
		"ahrim s robetop", "dagon hai robe top", "bloodbark body",
		"infinity top", "mystic robe top", "skeletal top", "splitbark body",
		"xerician top", "chaos robe top", "wizard robe top"
	);
	private static final List<String> MAGIC_LEGS = values(
		"virtus robe bottom", "ancestral robe bottom", "blue moon tassets",
		"ahrim s robeskirt", "dagon hai robe bottom", "bloodbark legs",
		"infinity bottoms", "mystic robe bottom", "skeletal bottoms",
		"splitbark legs", "xerician robe", "chaos robe", "wizard robe skirt"
	);
	private static final List<String> MAGIC_GLOVES = values(
		"expeditious bracelet", "bracelet of slaughter", "confliction gauntlets",
		"tormented bracelet", "barrows gloves", "regen bracelet",
		"infinity gloves", "combat bracelet", "mystic gloves"
	);
	private static final List<String> MAGIC_BOOTS = values(
		"avernic treads max", "avernic treads pr et",
		"avernic treads pe et", "avernic treads et",
		"avernic treads pr pe", "avernic treads pr", "avernic treads pe",
		"avernic treads", "eternal boots", "devout boots", "holy sandals",
		"aranea boots", "ancient ceremonial boots", "infinity boots",
		"wizard boots", "mystic boots"
	);
	private static final List<String> MAGIC_RING = values(
		"magus ring", "ring of the gods i", "seers ring i", "brimstone ring",
		"seers ring", "ring of wealth", "explorer s ring 4", "ring of dueling"
	);
	private static final List<String> MAGIC_OFFHAND = values(
		"elidinis ward f", "elidinis ward", "arcane spirit shield",
		"ancient wyvern shield", "malediction ward", "mage s book",
		"book of darkness", "unholy book", "book of balance",
		"tome of fire", "tome of water"
	);

	private static final List<String> MAGIC_DEFENCE_BODY = values(
		"masori body f", "masori body", "armadyl chestplate", "karil s leathertop",
		"hueycoatl hide body", "blood moon chestplate", "blessed body", "black d hide body",
		"red d hide body", "blue d hide body", "green d hide body"
	);
	private static final List<String> MAGIC_DEFENCE_LEGS = values(
		"masori chaps f", "masori chaps", "armadyl chainskirt", "karil s leatherskirt",
		"hueycoatl hide chaps", "blood moon tassets", "blessed chaps", "black d hide chaps",
		"red d hide chaps", "blue d hide chaps", "green d hide chaps"
	);

	private static final List<String> WILDERNESS_RANGED_CAPE = values(
		"ava s assembler", "ava s accumulator", "ranging cape", "ava s attractor"
	);
	private static final List<String> WILDERNESS_RANGED_NECK = values(
		"necklace of rupture", "necklace of anguish", "amulet of glory", "amulet of power"
	);
	private static final List<String> WILDERNESS_RANGED_BODY = values(
		"black d hide body", "red d hide body", "blue d hide body", "green d hide body"
	);
	private static final List<String> WILDERNESS_RANGED_LEGS = values(
		"black d hide chaps", "red d hide chaps", "blue d hide chaps", "green d hide chaps"
	);
	private static final List<String> WILDERNESS_RANGED_GLOVES = values(
		"expeditious bracelet", "barrows gloves", "black d hide vambraces",
		"red d hide vambraces", "green d hide vambraces", "combat bracelet"
	);
	private static final List<String> WILDERNESS_BOOTS = values(
		"mixed hide boots", "snakeskin boots", "frog leather boots", "climbing boots"
	);
	private static final List<String> WILDERNESS_RING = values(
		"ring of wealth", "explorer s ring 4", "ring of dueling"
	);

	private SlayerEquipmentAuditCatalog() { }

	public static List<String> priorities(
		final String taskName,
		final SlayerTaskStrategy strategy,
		final EquipmentInventorySlot slot,
		final String selectedWeaponName)
	{
		if (strategy == null || slot == null)
		{
			return Collections.emptyList();
		}

		final SlayerTaskStrategy.CombatStyle style = strategy.getCombatStyle();
		final SlayerTaskStrategy.ArmourFocus focus = strategy.getArmourFocus();
		final String task = normalize(taskName);
		final String weapon = normalize(selectedWeaponName);
		final boolean prayer = focus == SlayerTaskStrategy.ArmourFocus.PRAYER;
		final boolean magicDefence = focus == SlayerTaskStrategy.ArmourFocus.MAGIC_DEFENCE;
		final boolean wilderness = strategy.getCostPolicy()
			== SlayerTaskStrategy.CostPolicy.LOW_RISK
			|| strategy.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS);

		if (slot == EquipmentInventorySlot.HEAD)
		{
			if (task.contains("shellbane gryphon"))
			{
				return values("black mask i", "black mask", "slayer helmet i", "slayer helmet", "granite helm", "obsidian helmet", "adamant full helm");
			}
			if ((task.equals("blue dragon") || task.equals("blue dragons"))
				&& style == SlayerTaskStrategy.CombatStyle.RANGED)
			{
				return values(
					"slayer helmet i", "black mask i",
					"slayer helmet", "black mask",
					"masori mask f", "masori mask", "armadyl helmet",
					"blessed coif", "karil s coif", "eclipse moon helm",
					"black d hide coif", "red d hide coif",
					"blue d hide coif", "green d hide coif",
					"snakeskin bandana"
				);
			}
			/*
			 * An imbued helmet retains the melee Slayer bonus and is also the
			 * required progression for ranged/magic. Prefer it globally, while
			 * still allowing the unimbued versions for melee-only assignments.
			 */
			return style == SlayerTaskStrategy.CombatStyle.MELEE
				? values(
					"slayer helmet i", "black mask i",
					"slayer helmet", "black mask"
				)
				: values("slayer helmet i", "black mask i");
		}

		if (task.equals("royal titans"))
		{
			return royalTitans(slot);
		}

		if (task.equals("araxxor"))
		{
			return araxxor(slot, weapon);
		}

		if (slot == EquipmentInventorySlot.AMULET
			&& task.contains("banshee"))
		{
			return values(
				"salve amulet ei", "salve amulet e", "salve amulet i",
				"salve amulet", "amulet of rancour", "amulet of torture",
				"amulet of fury", "amulet of strength", "amulet of glory",
				"amulet of power"
			);
		}

		if ((task.equals("blue dragon") || task.equals("blue dragons"))
			&& style == SlayerTaskStrategy.CombatStyle.RANGED
			&& slot == EquipmentInventorySlot.SHIELD)
		{
			return values(
				"dragonfire ward", "anti dragon shield",
				"dragonfire shield", "ancient wyvern shield"
			);
		}

		if ((task.equals("skeletal wyvern") || task.equals("skeletal wyverns"))
			&& slot == EquipmentInventorySlot.SHIELD)
		{
			if (style == SlayerTaskStrategy.CombatStyle.RANGED)
			{
				return values(
					"dragonfire ward", "ancient wyvern shield",
					"dragonfire shield", "mind shield", "elemental shield"
				);
			}
			if (style == SlayerTaskStrategy.CombatStyle.MAGIC)
			{
				return values(
					"ancient wyvern shield", "mind shield", "elemental shield",
					"dragonfire shield", "dragonfire ward"
				);
			}
			return values(
				"ancient wyvern shield", "dragonfire shield",
				"mind shield", "elemental shield", "dragonfire ward"
			);
		}

		if (task.equals("barrows brothers"))
		{
			switch (slot)
			{
				case HEAD: return values(
					"ancestral hat", "virtus mask", "ahrim s hood",
					"infinity hat", "dagon hai hat", "bloodbark helm",
					"mystic hat", "barrows helm", "helm of neitiznot",
					"rune full helm"
				);
				case CAPE: return MAGIC_CAPE;
				case AMULET: return MAGIC_NECK;
				case BODY: return values(
					"ancestral robe top", "virtus robe top", "ahrim s robetop",
					"infinity top", "dagon hai robe top", "bloodbark body",
					"mystic robe top", "barrows platebody", "rune platebody",
					"black d hide body"
				);
				case LEGS: return values(
					"ancestral robe bottom", "virtus robe bottom", "ahrim s robeskirt",
					"infinity bottoms", "dagon hai robe bottom", "bloodbark legs",
					"mystic robe bottom", "barrows platelegs", "rune platelegs",
					"black d hide chaps"
				);
				case GLOVES: return MAGIC_GLOVES;
				case BOOTS: return MAGIC_BOOTS;
				case RING: return values(
					"magus ring", "seers ring i", "brimstone ring",
					"ring of dueling"
				);
				case SHIELD: return MAGIC_OFFHAND;
				default: break;
			}
		}

		if (task.equals("k ril tsutsaroth")
			&& style == SlayerTaskStrategy.CombatStyle.RANGED)
		{
			switch (slot)
			{
				case CAPE: return RANGED_CAPE;
				case AMULET: return values("necklace of anguish", "amulet of fury", "amulet of glory");
				case BODY: return weapon.contains("bow of faerdhinen")
					? values("crystal body", "masori body f", "masori body", "armadyl chestplate", "karil s leathertop", "hueycoatl hide body", "blessed body", "black d hide body")
					: values("masori body f", "masori body", "armadyl chestplate", "karil s leathertop", "hueycoatl hide body", "blessed body", "black d hide body");
				case LEGS: return weapon.contains("bow of faerdhinen")
					? values("crystal legs", "masori chaps f", "masori chaps", "armadyl chainskirt", "karil s leatherskirt", "hueycoatl hide chaps", "blessed chaps", "black d hide chaps")
					: values("masori chaps f", "masori chaps", "armadyl chainskirt", "karil s leatherskirt", "hueycoatl hide chaps", "blessed chaps", "black d hide chaps");
				case GLOVES: return values("zaryte vambraces", "barrows gloves", "zamorak bracers", "black spiky vambraces");
				case BOOTS: return values("avernic treads max", "pegasian boots", "echo boots", "ranger boots", "blessed boots", "aranea boots", "mixed hide boots");
				case RING: return values("lightbearer", "venator ring", "archers ring i", "ring of suffering i");
				case SHIELD: return RANGED_OFFHAND;
				default: break;
			}
		}

		if (task.equals("k ril tsutsaroth")
			&& style == SlayerTaskStrategy.CombatStyle.MELEE)
		{
			switch (slot)
			{
				case CAPE: return values("infernal cape", "fire cape");
				case AMULET: return values("amulet of blood fury", "amulet of rancour", "amulet of torture", "amulet of fury", "amulet of glory");
				case BODY: return values("masori body f", "blood moon chestplate", "karil s leathertop", "crystal body", "blessed body", "black d hide body", "mixed hide top");
				case LEGS: return values("masori chaps f", "crystal legs", "blood moon tassets", "karil s leatherskirt", "blessed chaps", "black d hide chaps", "mixed hide legs");
				case SHIELD: return values("avernic defender", "spectral spirit shield", "elysian spirit shield", "dragon defender", "dragonfire shield");
				case GLOVES: return values("barrows gloves", "ferocious gloves", "rune gloves", "regen bracelet");
				case BOOTS: return values("avernic treads max", "primordial boots", "echo boots", "guardian boots", "dragon boots");
				case RING: return values("lightbearer", "bellator ring", "ultor ring", "ring of suffering i", "berserker ring i", "brimstone ring");
				case AMMO: return values("rada s blessing 4", "unholy blessing");
				default: break;
			}
		}

		if (task.equals("grotesque guardians")
			|| task.equals("the grotesque guardians"))
		{
			/* The equipped base is the Wiki melee set. Dawn's ranged pieces are
			 * authored as inventory switches by SlayerBossInventoryCatalog. */
			switch (slot)
			{
				case CAPE: return values(
					"infernal cape", "fire cape", "ranging cape"
				);
				case AMULET: return values(
					"amulet of rancour", "amulet of torture", "amulet of blood fury",
					"amulet of fury", "amulet of glory", "amulet of strength"
				);
				case BODY: return values(
					"torva platebody", "oathplate chest", "bandos chestplate",
					"blood moon chestplate", "fighter torso", "obsidian platebody",
					"eclipse moon chestplate", "black d hide body"
				);
				case LEGS: return values(
					"torva platelegs", "oathplate legs", "bandos tassets",
					"blood moon tassets", "obsidian platelegs", "eclipse moon tassets",
					"verac s plateskirt", "dragon platelegs"
				);
				case SHIELD: return values(
					"avernic defender", "dragon defender", "dragonfire shield"
				);
				case GLOVES: return values(
					"ferocious gloves", "barrows gloves", "dragon gloves", "rune gloves"
				);
				case BOOTS: return values(
					"avernic treads max", "primordial boots", "dragon boots",
					"aranea boots", "echo boots", "guardian boots", "rune boots",
					"mixed hide boots"
				);
				case RING: return values(
					"ultor ring", "bellator ring", "berserker ring i", "lightbearer",
					"brimstone ring", "ring of shadows", "ring of suffering ri",
					"archers ring i", "treasonous ring i", "warrior ring i",
					"tyrannical ring i"
				);
				default: break;
			}
		}

		if (task.equals("tormented demons"))
		{
			switch (slot)
			{
				case CAPE: return MELEE_CAPE;
				case AMULET: return MELEE_NECK;
				case BODY: return values("torva platebody", "oathplate chest", "bandos chestplate", "inquisitor s hauberk", "blood moon chestplate", "eclipse moon chestplate", "obsidian platebody", "blue moon chestplate", "mixed hide top");
				case LEGS: return values("torva platelegs", "oathplate legs", "bandos tassets", "inquisitor s plateskirt", "blood moon tassets", "eclipse moon tassets", "obsidian platelegs", "blue moon tassets", "mixed hide legs");
				case SHIELD: return MELEE_OFFHAND;
				case GLOVES: return values("ferocious gloves", "barrows gloves", "regen bracelet", "combat bracelet");
				case BOOTS: return values("avernic treads max", "primordial boots", "aranea boots", "dragon boots", "echo boots", "mixed hide boots", "climbing boots");
				case RING: return values("ultor ring", "berserker ring i", "lightbearer", "ring of suffering i", "ring of shadows");
				default: break;
			}
		}

		if (task.equals("whisperer") || task.equals("the whisperer"))
		{
			switch (slot)
			{
				case CAPE: return MAGIC_CAPE;
				case AMULET: return MAGIC_NECK;
				case BODY: return values(
					"ancestral robe top", "virtus robe top", "ahrim s robetop",
					"blue moon chestplate", "bloodbark body", "infinity top",
					"mystic robe top"
				);
				case LEGS: return values(
					"ancestral robe bottom", "virtus robe bottom", "ahrim s robeskirt",
					"blue moon tassets", "bloodbark legs", "infinity bottoms",
					"mystic robe bottom"
				);
				case GLOVES: return values(
					"confliction gauntlets", "tormented bracelet", "barrows gloves",
					"infinity gloves", "combat bracelet", "mystic gloves"
				);
				case BOOTS: return values(
					"avernic treads max", "eternal boots", "infinity boots",
					"aranea boots", "wizard boots", "mystic boots"
				);
				case RING: return weapon.contains("tumeken s shadow")
					? values("magus ring", "lightbearer", "ring of the gods i", "ring of suffering i", "seers ring i")
					: values("lightbearer", "ring of the gods i", "ring of suffering i", "seers ring i", "magus ring");
				case SHIELD: return values(
					"elidinis ward f", "arcane spirit shield", "elidinis ward",
					"ancient wyvern shield", "malediction ward", "mage s book",
					"book of darkness"
				);
				case AMMO: return values(
					"rada s blessing 4", "god blessing", "rada s blessing 3",
					"rada s blessing 2"
				);
				default: break;
			}
		}

		if (task.equals("kalphite queen") || task.equals("the kalphite queen"))
		{
			if (style == SlayerTaskStrategy.CombatStyle.MAGIC)
			{
				switch (slot)
				{
					case CAPE: return MAGIC_CAPE;
					case AMULET: return values("occult necklace", "amulet of fury");
					case BODY: return values("ancestral robe top", "virtus robe top");
					case LEGS: return values("ancestral robe bottom", "virtus robe bottom");
					case SHIELD: return values("elysian spirit shield", "elidinis ward f", "arcane spirit shield", "mage s book");
					case GLOVES: return values("confliction gauntlets", "tormented bracelet");
					case BOOTS: return values("avernic treads max", "eternal boots", "infinity boots", "aranea boots");
					case RING: return values("magus ring", "lightbearer", "seers ring i", "ring of suffering i");
					case AMMO: return values("rada s blessing 4", "god blessing", "rada s blessing 3", "rada s blessing 2");
					default: break;
				}
			}
			else
			{
				switch (slot)
				{
					case CAPE: return MELEE_CAPE;
					case AMULET: return MELEE_NECK;
					case BODY: return MELEE_BODY;
					case LEGS: return MELEE_LEGS;
					case SHIELD: return values("avernic defender", "dragon defender");
					case GLOVES: return values("ferocious gloves", "barrows gloves", "regen bracelet", "combat bracelet");
					case BOOTS: return values("avernic treads max", "primordial boots", "echo boots", "dragon boots");
					case RING: return values("lightbearer", "ultor ring", "berserker ring i", "ring of suffering i");
					case AMMO: return values("rada s blessing 4", "god blessing", "rada s blessing 3", "rada s blessing 2");
					default: break;
				}
			}
		}

		if (task.contains("shellbane gryphon"))
		{
			switch (slot)
			{
				case BODY: return values("torva platebody", "oathplate chest", "bandos chestplate", "granite body", "obsidian platebody", "adamant platebody");
				case LEGS: return values("torva platelegs", "oathplate legs", "bandos tassets", "obsidian platelegs", "adamant platelegs");
				case SHIELD: return values("tortugan shield");
				case BOOTS: return values("avernic treads", "echo boots", "guardian boots", "primordial boots", "granite boots", "dragon boots");
				default: break;
			}
		}

		if (task.equals("gryphon") || task.equals("gryphons"))
		{
			switch (slot)
			{
				case HEAD: return values(
					"black mask i", "black mask", "slayer helmet i",
					"slayer helmet", "granite helm", "obsidian helmet",
					"adamant full helm"
				);
				case BODY: return values(
					"torva platebody", "oathplate chest", "bandos chestplate",
					"granite body", "obsidian platebody", "barrows platebody",
					"adamant platebody"
				);
				case LEGS: return values(
					"torva platelegs", "oathplate legs", "bandos tassets",
					"obsidian platelegs", "barrows platelegs", "adamant platelegs"
				);
				case BOOTS: return values(
					"guardian boots", "echo boots", "granite boots",
					"dragon boots", "rune boots"
				);
				default: break;
			}
		}

		if (task.contains("maggot king"))
		{
			switch (slot)
			{
				case CAPE: return MAGIC_CAPE;
				case AMULET: return MAGIC_NECK;
				case BODY: return MAGIC_BODY;
				case LEGS: return MAGIC_LEGS;
				case GLOVES: return MAGIC_GLOVES;
				/* Avernic treads and the melee ring are efficient shared slots. */
				case BOOTS: return values(
					"avernic treads max", "avernic treads pr et", "avernic treads pe et",
					"avernic treads", "primordial boots", "eternal boots", "dragon boots"
				);
				case RING: return values(
					"ultor ring", "lightbearer", "berserker ring i", "berserker ring",
					"magus ring", "brimstone ring"
				);
				case SHIELD: return values("tome of fire", "elidinis ward f", "elidinis ward", "malediction ward");
				default: break;
			}
		}

		if (wilderness)
		{
			return wilderness(style, slot);
		}
		if (slot == EquipmentInventorySlot.GLOVES && strategy.isBoss())
		{
			return bossGloves(style);
		}

		switch (style)
		{
			case RANGED:
				return ranged(slot, weapon, prayer);
			case MAGIC:
				return magic(slot, prayer, magicDefence, taskName);
			case HYBRID:
				return hybrid(slot);
			case MELEE:
			default:
				return melee(slot, prayer, magicDefence);
		}
	}

	private static List<String> bossGloves(
		final SlayerTaskStrategy.CombatStyle style)
	{
		switch (style)
		{
			case RANGED:
				return values(
					"zaryte vambraces", "barrows gloves", "blessed vambraces",
					"hueycoatl hide vambraces", "black d hide vambraces",
					"red d hide vambraces", "blue d hide vambraces",
					"green d hide vambraces", "combat bracelet"
				);
			case MAGIC:
				return values(
					"confliction gauntlets", "tormented bracelet", "barrows gloves",
					"regen bracelet", "infinity gloves", "combat bracelet",
					"mystic gloves"
				);
			case HYBRID:
				return values(
					"barrows gloves", "ferocious gloves", "zaryte vambraces",
					"tormented bracelet", "combat bracelet"
				);
			case MELEE:
			default:
				return values(
					"ferocious gloves", "barrows gloves", "regen bracelet",
					"dragon gloves", "rune gloves", "combat bracelet",
					"adamant gloves", "mithril gloves"
				);
		}
	}

	/**
	 * Complete owned-weapon fallback used only after a task's researched weapon
	 * order has been exhausted. Strict mechanic/boss profiles never use it.
	 */
	public static List<String> weaponProgression(
		final SlayerTaskStrategy strategy)
	{
		if (strategy == null)
		{
			return Collections.emptyList();
		}
		final boolean wilderness = strategy.getCostPolicy()
			== SlayerTaskStrategy.CostPolicy.LOW_RISK
			|| strategy.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS);
		if (wilderness)
		{
			switch (strategy.getCombatStyle())
			{
				case MAGIC:
					return values("accursed sceptre", "thammaron s sceptre",
						"trident of the seas", "iban s staff", "mystic staff",
						"battlestaff", "elemental staff");
				case RANGED:
					return values("webweaver bow", "craw s bow", "toxic blowpipe",
						"magic shortbow i", "rune crossbow", "magic shortbow",
						"dorgeshuun crossbow", "maple shortbow");
				case MELEE:
				default:
					return values("abyssal whip", "zombie axe", "dragon scimitar",
						"dragon sword", "rune scimitar", "adamant scimitar",
						"mithril scimitar");
			}
		}

		switch (strategy.getCombatStyle())
		{
			case RANGED:
				return values(
					"venator bow", "toxic blowpipe", "bow of faerdhinen",
					"twisted bow", "magic shortbow i", "hunters sunlight crossbow",
					"rune crossbow", "magic shortbow", "crystal bow",
					"dorgeshuun crossbow", "yew shortbow", "bone shortbow",
					"maple shortbow", "willow shortbow", "oak shortbow", "shortbow"
				);
			case MAGIC:
				return values(
					"tumeken s shadow", "eye of ayak", "sanguinesti staff",
					"trident of the swamp",
					"trident of the seas", "warped sceptre", "nightmare staff",
					"ancient sceptre", "master wand", "ancient staff",
					"iban s staff", "mystic staff", "battlestaff", "elemental staff"
				);
			case HYBRID:
				return values(
					"tumeken s shadow", "eye of ayak", "sanguinesti staff",
					"trident of the swamp",
					"bow of faerdhinen", "toxic blowpipe", "osmumten s fang",
					"abyssal whip", "dragon scimitar"
				);
			case MELEE:
			default:
				return values(
					/*
					 * Scythe is intentionally not a generic melee fallback. Its extra
					 * hits depend on NPC footprint, so only a task profile that was
					 * reviewed for a sufficiently large target may opt into it.
					 */
					"soulreaper axe", "ghrazi rapier",
					"noxious halberd", "osmumten s fang", "blade of saeldor",
					"inquisitor s mace", "abyssal tentacle", "abyssal whip",
					"abyssal bludgeon", "zombie axe", "sarachnis cudgel",
					"dragon scimitar", "dragon sword", "dragon longsword",
					"rune scimitar", "brine sabre", "granite hammer",
					"adamant scimitar", "mithril scimitar", "black scimitar",
					"steel scimitar", "iron scimitar"
				);
		}
	}

	private static List<String> melee(
		final EquipmentInventorySlot slot,
		final boolean prayer,
		final boolean magicDefence)
	{
		switch (slot)
		{
			case CAPE: return MELEE_CAPE;
			case AMULET: return MELEE_NECK;
			case BODY:
				return magicDefence ? MAGIC_DEFENCE_BODY
					: prayer ? PRAYER_BODY : MELEE_BODY;
			case SHIELD: return MELEE_OFFHAND;
			case LEGS:
				return magicDefence ? MAGIC_DEFENCE_LEGS
					: prayer ? PRAYER_LEGS : MELEE_LEGS;
			case GLOVES: return MELEE_GLOVES;
			case BOOTS: return MELEE_BOOTS;
			case RING: return prayer ? PRAYER_RING : MELEE_RING;
			default: return Collections.emptyList();
		}
	}

	private static List<String> ranged(
		final EquipmentInventorySlot slot,
		final String weapon,
		final boolean prayer)
	{
		switch (slot)
		{
			case CAPE: return RANGED_CAPE;
			case AMULET: return RANGED_NECK;
			case BODY:
				return weapon.contains("bow of faerdhinen")
					? prepend("crystal body", RANGED_BODY)
					: prayer ? RANGED_PRAYER_BODY : RANGED_BODY;
			case SHIELD: return RANGED_OFFHAND;
			case LEGS:
				return weapon.contains("bow of faerdhinen")
					? prepend("crystal legs", RANGED_LEGS)
					: prayer ? RANGED_PRAYER_LEGS : RANGED_LEGS;
			case GLOVES: return RANGED_GLOVES;
			case BOOTS: return RANGED_BOOTS;
			case RING: return prayer
				? values("ring of the gods i", "venator ring", "archers ring i", "archers ring")
				: RANGED_RING;
			default: return Collections.emptyList();
		}
	}

	private static List<String> magic(
		final EquipmentInventorySlot slot,
		final boolean prayer,
		final boolean magicDefence,
		final String taskName)
	{
		final boolean accuracySensitive = normalize(taskName).contains("smoke devil");
		switch (slot)
		{
			case CAPE: return MAGIC_CAPE;
			case AMULET: return MAGIC_NECK;
			case BODY:
				return magicDefence ? MAGIC_DEFENCE_BODY
					: prayer && !accuracySensitive ? PRAYER_BODY : MAGIC_BODY;
			case SHIELD: return MAGIC_OFFHAND;
			case LEGS:
				return magicDefence ? MAGIC_DEFENCE_LEGS
					: prayer && !accuracySensitive ? PRAYER_LEGS : MAGIC_LEGS;
			case GLOVES: return MAGIC_GLOVES;
			case BOOTS: return MAGIC_BOOTS;
			case RING: return prayer && !accuracySensitive
				? values("ring of the gods i", "magus ring", "seers ring i")
				: MAGIC_RING;
			default: return Collections.emptyList();
		}
	}

	private static List<String> hybrid(final EquipmentInventorySlot slot)
	{
		switch (slot)
		{
			case CAPE: return values("infernal cape", "fire cape", "dizana s quiver", "ava s assembler", "imbued god cape");
			case AMULET: return values("amulet of rancour", "amulet of fury", "necklace of anguish", "occult necklace");
			case BODY: return values("blood moon chestplate", "eclipse moon chestplate", "blue moon chestplate", "karil s leathertop");
			case SHIELD: return values("avernic defender", "dragon defender", "dragonfire ward", "elidinis ward");
			case LEGS: return values("blood moon tassets", "eclipse moon tassets", "blue moon tassets", "karil s leatherskirt");
			case GLOVES: return values("barrows gloves", "ferocious gloves", "zaryte vambraces", "tormented bracelet");
			case BOOTS: return values("avernic treads", "primordial boots", "pegasian boots", "eternal boots");
			case RING: return values("lightbearer", "brimstone ring", "ultor ring", "venator ring", "magus ring");
			default: return Collections.emptyList();
		}
	}

	private static List<String> royalTitans(final EquipmentInventorySlot slot)
	{
		switch (slot)
		{
			case CAPE:
				return values("infernal cape", "fire cape");
			case AMULET:
				return values(
					"amulet of rancour", "amulet of torture", "amulet of fury",
					"amulet of strength", "amulet of glory"
				);
			case BODY:
				return values(
					"torva platebody", "oathplate chest", "inquisitor s hauberk",
					"bandos chestplate", "blood moon chestplate", "fighter torso",
					"obsidian platebody"
				);
			case SHIELD:
				return values("avernic defender", "dragon defender");
			case LEGS:
				return values(
					"torva platelegs", "oathplate legs", "inquisitor s plateskirt",
					"bandos tassets", "blood moon tassets", "obsidian platelegs"
				);
			case GLOVES:
				return values("ferocious gloves", "barrows gloves", "dragon gloves", "rune gloves");
			case BOOTS:
				return values(
					"avernic treads max", "primordial boots", "aranea boots",
					"dragon boots", "rune boots"
				);
			case RING:
				return values(
					"ultor ring", "berserker ring i", "lightbearer",
					"brimstone ring", "berserker ring"
				);
			case AMMO:
				return values("rada s blessing 4", "rada s blessing 3", "holy blessing", "unholy blessing");
			default:
				return Collections.emptyList();
		}
	}

	private static List<String> araxxor(
		final EquipmentInventorySlot slot,
		final String weapon)
	{
		final boolean soulreaper = weapon.contains("soulreaper axe");
		switch (slot)
		{
			case CAPE:
				return values(
					"infernal cape", "fire cape", "mythical cape",
					"ardougne cloak 4", "ardougne cloak 3"
				);
			case AMULET:
				return values(
					"amulet of rancour", "amulet of torture", "amulet of blood fury",
					"amulet of fury", "amulet of glory"
				);
			case BODY:
				return soulreaper
					? values(
						"oathplate chest", "inquisitor s hauberk", "torva platebody",
						"bandos chestplate", "blood moon chestplate", "fighter torso",
						"torag s platebody", "dharok s platebody", "guthan s platebody",
						"verac s brassard"
					)
					: values(
						"inquisitor s hauberk", "torva platebody", "oathplate chest",
						"bandos chestplate", "blood moon chestplate", "fighter torso",
						"torag s platebody", "dharok s platebody", "guthan s platebody",
						"verac s brassard"
					);
			case LEGS:
				return soulreaper
					? values(
						"oathplate legs", "inquisitor s plateskirt", "torva platelegs",
						"bandos tassets", "blood moon tassets", "torag s platelegs",
						"dharok s platelegs", "guthan s chainskirt", "verac s plateskirt"
					)
					: values(
						"inquisitor s plateskirt", "torva platelegs", "oathplate legs",
						"bandos tassets", "blood moon tassets", "torag s platelegs",
						"dharok s platelegs", "guthan s chainskirt", "verac s plateskirt"
					);
			case SHIELD:
				return values(
					"avernic defender", "dragon defender", "rune defender",
					"toktz ket xil"
				);
			case AMMO:
				return values(
					"rada s blessing 4", "rada s blessing 3", "rada s blessing 2",
					"holy blessing", "unholy blessing"
				);
			case GLOVES:
				return values(
					"ferocious gloves", "barrows gloves", "dragon gloves", "rune gloves"
				);
			case BOOTS:
				return values(
					"avernic treads max", "primordial boots", "dragon boots",
					"aranea boots", "echo boots", "guardian boots", "rune boots",
					"mixed hide boots"
				);
			case RING:
				return values(
					"ultor ring", "berserker ring i", "ring of suffering ri",
					"lightbearer", "berserker ring"
				);
			default:
				return Collections.emptyList();
		}
	}

	private static List<String> wilderness(
		final SlayerTaskStrategy.CombatStyle style,
		final EquipmentInventorySlot slot)
	{
		switch (style)
		{
			case RANGED:
				switch (slot)
				{
					case CAPE: return WILDERNESS_RANGED_CAPE;
					case AMULET: return WILDERNESS_RANGED_NECK;
					case BODY: return WILDERNESS_RANGED_BODY;
					case LEGS: return WILDERNESS_RANGED_LEGS;
					case GLOVES: return WILDERNESS_RANGED_GLOVES;
					case BOOTS: return WILDERNESS_BOOTS;
					case RING: return WILDERNESS_RING;
					case SHIELD: return values("odium ward", "book of law", "unholy book");
					default: return Collections.emptyList();
				}
			case MAGIC:
				switch (slot)
				{
					case CAPE: return values("imbued god cape", "god cape", "ardougne cloak 4");
					case AMULET: return values("occult necklace", "amulet of glory", "amulet of power");
					case BODY: return values("mystic robe top", "xerician top", "monk s robe top");
					case LEGS: return values("mystic robe bottom", "xerician robe", "monk s robe");
					case GLOVES: return values("expeditious bracelet", "barrows gloves", "mystic gloves", "combat bracelet");
					case BOOTS: return values("mystic boots", "climbing boots");
					case RING: return WILDERNESS_RING;
					case SHIELD: return values("book of darkness", "unholy book", "book of balance");
					default: return Collections.emptyList();
				}
			case HYBRID:
				return wilderness(SlayerTaskStrategy.CombatStyle.RANGED, slot);
			case MELEE:
			default:
				switch (slot)
				{
					case CAPE: return values("mixed hide cape", "ardougne cloak 4", "cape of accomplishment");
					case AMULET: return values("amulet of glory", "amulet of strength", "amulet of power");
					case BODY: return values("black d hide body", "proselyte hauberk", "rune platebody", "monk s robe top");
					case LEGS: return values("black d hide chaps", "proselyte cuisse", "rune platelegs", "monk s robe");
					case GLOVES: return values("expeditious bracelet", "barrows gloves", "combat bracelet");
					case BOOTS: return WILDERNESS_BOOTS;
					case RING: return WILDERNESS_RING;
					case SHIELD: return values("dragon defender", "rune defender", "adamant defender", "book of war");
					default: return Collections.emptyList();
				}
		}
	}

	public static Map<EquipmentInventorySlot, List<String>> auditedSlotsForRegression(
		final String taskName,
		final SlayerTaskStrategy strategy,
		final String selectedWeaponName)
	{
		final Map<EquipmentInventorySlot, List<String>> result =
			new EnumMap<>(EquipmentInventorySlot.class);
		for (final EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			if (slot == EquipmentInventorySlot.WEAPON
				|| slot == EquipmentInventorySlot.AMMO)
			{
				continue;
			}
			final List<String> values = priorities(
				taskName, strategy, slot, selectedWeaponName
			);
			if (!values.isEmpty())
			{
				result.put(slot, values);
			}
		}
		return Collections.unmodifiableMap(result);
	}

	private static List<String> prepend(
		final String value,
		final List<String> remaining)
	{
		final String[] values = new String[remaining.size() + 1];
		values[0] = value;
		for (int i = 0; i < remaining.size(); i++)
		{
			values[i + 1] = remaining.get(i);
		}
		return values(values);
	}

	private static List<String> values(final String... values)
	{
		return Collections.unmodifiableList(Arrays.asList(values));
	}

	private static String normalize(final String value)
	{
		return value == null ? "" : value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
	}
}
