package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Spellbook and rune-pouch readiness for the selected reviewed Slayer method.
 *
 * This is intentionally method-driven rather than monster-driven. Ancient
 * barrage/freeze methods and Arceuus Thrall/Death Charge methods use the same
 * preparation path, so any future catalog entry automatically receives the
 * correct spellbook, pouch, Book of the dead, and rune validation.
 */
public final class SlayerTaskPreparationCatalog
{
	private static final int SPELLBOOK_VARBIT = VarbitID.SPELLBOOK;
	private static final int STANDARD_SPELLBOOK = 0;
	private static final int ANCIENT_SPELLBOOK = 1;
	private static final int LUNAR_SPELLBOOK = 2;
	private static final int ARCEUUS_SPELLBOOK = 3;
	private static final int ICE_BURST_LEVEL = 70;
	private static final int ICE_BARRAGE_LEVEL = 94;

	private static final int BOOK_OF_THE_DEAD = ItemID.BOOK_OF_THE_DEAD;
	private static final int AIR_RUNE = ItemID.AIRRUNE;
	private static final int WATER_RUNE = ItemID.WATERRUNE;
	private static final int EARTH_RUNE = ItemID.EARTHRUNE;
	private static final int MIND_RUNE = ItemID.MINDRUNE;
	private static final int FIRE_RUNE = ItemID.FIRERUNE;
	private static final int CHAOS_RUNE = ItemID.CHAOSRUNE;
	private static final int DEATH_RUNE = ItemID.DEATHRUNE;
	private static final int BLOOD_RUNE = ItemID.BLOODRUNE;
	private static final int COSMIC_RUNE = ItemID.COSMICRUNE;
	private static final int SOUL_RUNE = ItemID.SOULRUNE;
	private static final int WRATH_RUNE = ItemID.WRATHRUNE;

	/* Combination runes count as both of their component runes. */
	private static final int MIST_RUNE = ItemID.MISTRUNE;   // Air + Water
	private static final int DUST_RUNE = ItemID.DUSTRUNE;   // Air + Earth
	private static final int SMOKE_RUNE = ItemID.SMOKERUNE; // Air + Fire
	private static final int MUD_RUNE = ItemID.MUDRUNE;     // Water + Earth
	private static final int STEAM_RUNE = ItemID.STEAMRUNE; // Water + Fire
	private static final int LAVA_RUNE = ItemID.LAVARUNE;   // Earth + Fire
	private static final int AETHER_RUNE = ItemID.AETHERRUNE; // Cosmic + Soul

	private static final int[] RUNE_TYPE_VARBITS =
	{
		VarbitID.RUNE_POUCH_TYPE_1,
		VarbitID.RUNE_POUCH_TYPE_2,
		VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4,
		VarbitID.RUNE_POUCH_TYPE_5,
		VarbitID.RUNE_POUCH_TYPE_6
	};

	private static final int[] RUNE_QUANTITY_VARBITS =
	{
		VarbitID.RUNE_POUCH_QUANTITY_1,
		VarbitID.RUNE_POUCH_QUANTITY_2,
		VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4,
		VarbitID.RUNE_POUCH_QUANTITY_5,
		VarbitID.RUNE_POUCH_QUANTITY_6
	};

	private static final int[] RUNE_POUCH_IDS =
	{
		ItemID.DIVINE_RUNE_POUCH,
		ItemID.DIVINE_RUNE_POUCH_TROUVER,
		ItemID.BH_RUNE_POUCH,
		ItemID.BH_RUNE_POUCH_TROUVER
	};

	private static final int[] DIVINE_RUNE_POUCH_IDS =
	{
		ItemID.DIVINE_RUNE_POUCH,
		ItemID.DIVINE_RUNE_POUCH_TROUVER
	};

	private SlayerTaskPreparationCatalog() { }

	/** Authoritative v175 resolver used by SlayerPlusPlugin. */
	public static PreparationPlan resolve(
		final String encounterName,
		final String location,
		final SlayerTaskStrategy strategy,
		final Client client,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> bankItems)
	{
		if (strategy == null)
		{
			return PreparationPlan.none();
		}

		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			encounterName,
			location,
			strategy
		);
		if (rules.getSpellbook() == SlayerMethodRules.Spellbook.ARCEUUS
			&& (rules.usesThralls() || rules.usesDeathCharge()))
		{
			return resolveArceuus(
				rules,
				client,
				inventory,
				equipment,
				bankItems
			);
		}

		if (rules.getSpellbook() == SlayerMethodRules.Spellbook.ANCIENT)
		{
			return resolveAncients(
				rules,
				client,
				inventory,
				equipment,
				bankItems
			);
		}

		/*
		 * Rune-pouch layout is spellbook-agnostic. Standard, Lunar, Arceuus
		 * methods without Thralls/Death Charge, and future spellbooks all use
		 * the same 3/4-type pouch-capacity rule whenever their reviewed method
		 * declares rune requirements.
		 */
		if (rules.requiresRunePouch()
			|| !rules.getPouchRunes().isEmpty()
			|| (rules.getSpellbook() != SlayerMethodRules.Spellbook.NONE
				&& rules.getSpellbook()
					!= SlayerMethodRules.Spellbook.STRATEGY_DEFINED))
		{
			return resolveRuleRuneReferences(
				rules, client, inventory, equipment, bankItems
			);
		}
		return PreparationPlan.none();
	}

	/**
	 * Compatibility overload for older callers. It preserves the prior Smoke
	 * Devil preparation behaviour; the plugin itself now uses the method-driven
	 * overload above.
	 */
	public static PreparationPlan resolve(
		final String taskName,
		final SlayerTaskVariant variant,
		final SlayerRecommendation recommendation,
		final Client client,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> bankItems)
	{
		if (!isStandardSmokeDevilMagicMethod(taskName, variant, recommendation))
		{
			return PreparationPlan.none();
		}
		return resolveLegacySmokeDevils(
			client,
			inventory,
			equipment,
			bankItems
		);
	}

	private static PreparationPlan resolveArceuus(
		final SlayerMethodRules rules,
		final Client client,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> bankItems)
	{
		final int magicLevel = client == null
			? 0 : client.getRealSkillLevel(Skill.MAGIC);
		final boolean correctBook = client != null
			&& client.getVarbitValue(SPELLBOOK_VARBIT) == ARCEUUS_SPELLBOOK;
		final boolean levelReady = magicLevel >= rules.getMinimumMagicLevel();

		final int carriedPouchId = firstCarriedPouch(
			inventory, equipment, false
		);
		final int ownedPouchId = firstOwnedPouch(
			inventory, equipment, bankItems, false
		);
		final int layoutPouchId = ownedPouchId > 0
			? ownedPouchId : ItemID.BH_RUNE_POUCH;
		final int pouchCapacity = pouchCapacity(layoutPouchId);

		final boolean bookReady = !rules.requiresBookOfDead()
			|| contains(inventory, BOOK_OF_THE_DEAD)
			|| contains(equipment, BOOK_OF_THE_DEAD);
		final boolean pouchReady = carriedPouchId > 0;
		final Map<Integer, Integer> pouchContents = readPouchContents(client);
		final Map<Integer, Integer> ownedItems = ownedItemQuantities(
			pouchContents, inventory, equipment, bankItems
		);
		final Map<Integer, Integer> carriedRunes = mergeCarriedRuneContents(
			pouchContents, inventory, equipment
		);
		final SlayerRuneOwnershipPolicy.Resolution resolvedRunePackage =
			SlayerRuneOwnershipPolicy.resolve(
				rules.getPouchRunes(), ownedItems
			);
		final List<String> missingRunes = new ArrayList<>();
		final List<String> overflowRunes = new ArrayList<>();
		final List<RuneStatus> runeStatuses = new ArrayList<>();
		final List<SlayerRuneOwnershipPolicy.ResolvedRune> runes =
			resolvedRunePackage.getRunes();
		for (int i = 0; i < runes.size(); i++)
		{
			final SlayerRuneOwnershipPolicy.ResolvedRune rune = runes.get(i);
			final boolean inPouch = i < pouchCapacity;
			final int available = inPouch
				? pouchContents.getOrDefault(rune.getItemId(), 0)
				: carriedRunes.getOrDefault(rune.getItemId(), 0);
			runeStatuses.add(new RuneStatus(
				rune.getName(), rune.getMinimumQuantity(), available
			));
			if (inPouch)
			{
				if (available < rune.getMinimumQuantity())
				{
					missingRunes.add(rune.getName());
				}
			}
			else
			{
				overflowRunes.add(rune.getName());
				if (available < rune.getMinimumQuantity())
				{
					missingRunes.add(rune.getName());
				}
			}
		}
		addUnownedRequirementStatuses(
			rules.getPouchRunes(),
			runes,
			pouchContents,
			runeStatuses
		);

		final boolean ready = correctBook
			&& levelReady
			&& pouchReady
			&& bookReady
			&& missingRunes.isEmpty()
			&& resolvedRunePackage.getUnownedRequirements().isEmpty();

		final List<String> warnings = new ArrayList<>();
		if (!correctBook)
		{
			warnings.add("Switch to the Arceuus spellbook");
		}
		if (!levelReady)
		{
			warnings.add(rules.getMinimumMagicLevel() + " Magic is required");
		}
		if (!pouchReady)
		{
			warnings.add("Withdraw a rune pouch");
		}
		if (!bookReady)
		{
			warnings.add("Bring Book of the dead");
		}
		if (!missingRunes.isEmpty())
		{
			warnings.add("Bring or load " + joinNames(missingRunes));
		}
		if (!resolvedRunePackage.getUnownedRequirements().isEmpty())
		{
			warnings.add("No owned rune available for " + joinNames(
				resolvedRunePackage.getUnownedRequirements()
			));
		}

		final List<Integer> bankTagItemIds = new ArrayList<>();
		bankTagItemIds.add(layoutPouchId);
		if (rules.requiresBookOfDead())
		{
			bankTagItemIds.add(BOOK_OF_THE_DEAD);
		}
		for (final SlayerRuneOwnershipPolicy.ResolvedRune rune : runes)
		{
			bankTagItemIds.add(rune.getItemId());
		}

		final String utility = rules.usesThralls() && rules.usesDeathCharge()
			? "Thralls + Death Charge"
			: rules.usesThralls() ? "Thralls" : "Death Charge";
		final String headline = ready
			? "Arceuus ready • " + utility
			: "Preparation required • " + utility;
		final String detail = ready
			? "Spellbook, book, and rune pouch are ready."
			: String.join(". ", warnings) + ".";
		final List<String> pouchNames = new ArrayList<>();
		for (int i = 0; i < runes.size() && i < pouchCapacity; i++)
		{
			pouchNames.add(runes.get(i).getName());
		}
		final String bankNote;
		if (pouchNames.isEmpty())
		{
			bankNote = "No owned rune package can currently cast " + utility + ".";
		}
		else
		{
			bankNote = "Load " + joinNames(pouchNames)
				+ " into the rune pouch"
				+ (overflowRunes.isEmpty()
					? "."
					: "; carry " + joinNames(overflowRunes) + " in the inventory.")
				+ (resolvedRunePackage.getUnownedRequirements().isEmpty()
					? ""
					: " No owned rune is available for " + joinNames(
						resolvedRunePackage.getUnownedRequirements()) + ".");
		}
		final String routeWarning = ready
			? "" : "Before routing: " + String.join("; ", warnings) + ".";

		return new PreparationPlan(
			true, ready, headline, detail, bankNote, routeWarning, bankTagItemIds,
			SlayerMethodRules.Spellbook.ARCEUUS,
			correctBook,
			levelReady,
			magicLevel,
			runeStatuses,
			utility,
			0,
			missingRunes.isEmpty()
		);
	}

	private static PreparationPlan resolveAncients(
		final SlayerMethodRules rules,
		final Client client,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> bankItems)
	{
		/*
		 * v175 keeps the proven Smoke Devil barrage readiness path. Other Ancient
		 * methods already receive their rune requirements from their rule entries;
		 * a future spell-specific readiness entry can be added without changing
		 * the Arceuus system.
		 */
		final String spell = normalize(rules.getPrimarySpell());
		/*
		 * The legacy Smoke Devil resolver predates structured pouch-rune rules.
		 * Only use it when the selected method has no authored pouch package.
		 * Modern encounters such as TzKal-Zuk intentionally define Blood + Ice
		 * Barrage together with Soul/Blood/Death/Water and must remain on the
		 * rule-driven path below.
		 */
		if (rules.getPouchRunes().isEmpty()
			&& (spell.contains("ice barrage") || spell.contains("ice burst")))
		{
			return resolveLegacySmokeDevils(
				client,
				inventory,
				equipment,
				bankItems
			);
		}
		return resolveRuleRuneReferences(
			rules, client, inventory, equipment, bankItems
		);
	}

	private static PreparationPlan resolveRuleRuneReferences(
		final SlayerMethodRules rules,
		final Client client,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> bankItems)
	{
		/*
		 * GLOBAL SPELL-SETUP RULE
		 * -----------------------
		 * A researched spellbook requirement stays visible even when the player is
		 * missing its pouch/runes. Missing preparation is exactly when this card is
		 * most useful, so never turn that state into PreparationPlan.none().
		 */
		final String bookName = spellbookDisplayName(rules.getSpellbook());
		final int expectedBook = spellbookVarbitValue(rules.getSpellbook());
		final boolean correctBook = expectedBook < 0
			|| (client != null && client.getVarbitValue(SPELLBOOK_VARBIT) == expectedBook);

		final boolean pouchRequired = rules.requiresRunePouch()
			|| !rules.getPouchRunes().isEmpty();
		final int carriedPouchId = firstCarriedPouch(inventory, equipment, false);
		final int ownedPouchId = firstOwnedPouch(
			inventory, equipment, bankItems, false
		);
		final int capacity = pouchCapacity(
			carriedPouchId > 0 ? carriedPouchId : ownedPouchId
		);
		final int referenceCapacity = capacity > 0 ? capacity : 3;
		final boolean pouchReady = !pouchRequired || carriedPouchId > 0;
		final Map<Integer, Integer> pouchContents = readPouchContents(client);
		final Map<Integer, Integer> ownedItems = ownedItemQuantities(
			pouchContents, inventory, equipment, bankItems
		);

		final List<Integer> bankTagItemIds = new ArrayList<>();
		if (pouchRequired)
		{
			bankTagItemIds.add(
				ownedPouchId > 0 ? ownedPouchId : ItemID.BH_RUNE_POUCH
			);
		}

		final List<String> runeNames = new ArrayList<>();
		final List<String> missingRunes = new ArrayList<>();
		final List<String> unownedRunes = new ArrayList<>();
		final List<String> overflowRunes = new ArrayList<>();
		final List<RuneStatus> runeStatuses = new ArrayList<>();

		/* Prefer the structured pouch requirements used by the shared loadout. */
		if (!rules.getPouchRunes().isEmpty())
		{
			final SlayerRuneOwnershipPolicy.Resolution resolvedRunes =
				SlayerRuneOwnershipPolicy.resolve(
					rules.getPouchRunes(), ownedItems
				);
			unownedRunes.addAll(resolvedRunes.getUnownedRequirements());
			int runeIndex = 0;
			for (final SlayerRuneOwnershipPolicy.ResolvedRune rune
				: resolvedRunes.getRunes())
			{
				final int available = pouchContents.getOrDefault(
					rune.getItemId(), 0
				);
				runeNames.add(rune.getName());
				runeStatuses.add(new RuneStatus(
					rune.getName(), rune.getMinimumQuantity(), available
				));

				if (runeIndex < referenceCapacity)
				{
					if (available < rune.getMinimumQuantity())
					{
						missingRunes.add(rune.getName());
					}
					bankTagItemIds.add(rune.getItemId());
				}
				else
				{
					overflowRunes.add(rune.getName());
				}
				runeIndex++;
			}
			addUnownedRequirementStatuses(
				rules.getPouchRunes(),
				resolvedRunes.getRunes(),
				pouchContents,
				runeStatuses
			);
		}
		else
		{
			/* Compatibility with older authored rune requirements. */
			for (final SlayerMethodRules.RequiredItem required : rules.getRequiredItems())
			{
				if (required.getGroup() != SlayerMethodRules.InventoryGroup.RUNES_AMMO)
				{
					continue;
				}
				final int runeId = runeIdForName(required.getDisplayName());
				if (runeId <= 0)
				{
					continue;
				}
				final String runeName = cleanRuneName(required.getDisplayName());
				final int available = effectiveRuneQuantity(pouchContents, runeId);
				runeNames.add(runeName);
				runeStatuses.add(new RuneStatus(runeName, 1, available));
				if (runeNames.size() <= referenceCapacity)
				{
					if (available <= 0)
					{
						missingRunes.add(runeName);
					}
					final int ownedRuneId = preferredSatisfyingRuneId(
						ownedItems, runeId
					);
					if (ownedRuneId > 0)
					{
						bankTagItemIds.add(ownedRuneId);
					}
				}
				else
				{
					overflowRunes.add(runeName);
				}
			}
		}

		final boolean explicitRunesReady = missingRunes.isEmpty()
			&& unownedRunes.isEmpty();
		final boolean ready = correctBook && pouchReady && explicitRunesReady;
		final List<String> warnings = new ArrayList<>();
		if (!correctBook)
		{
			warnings.add("Switch to " + bookName);
		}
		if (!pouchReady)
		{
			warnings.add("Withdraw a rune pouch");
		}
		if (!missingRunes.isEmpty())
		{
			warnings.add("Load " + joinNames(missingRunes) + " into the rune pouch");
		}
		if (!unownedRunes.isEmpty())
		{
			warnings.add("No owned rune available for " + joinNames(unownedRunes));
		}

		final String spell = safeSpellName(rules.getPrimarySpell());
		final String headline = bookName + " • " + spell;
		final String detail;
		if (ready)
		{
			detail = runeNames.isEmpty()
				? "Spellbook is ready; use the runes required by the selected spell."
				: "Spellbook and rune pouch are ready.";
		}
		else
		{
			detail = String.join(". ", warnings) + ".";
		}

		final String bankNote;
		if (!runeNames.isEmpty())
		{
			bankNote = "Load " + joinNames(runeNames) + " into the rune pouch"
				+ (overflowRunes.isEmpty()
					? "."
					: "; carry " + joinNames(overflowRunes) + " in the inventory.");
		}
		else if (pouchRequired)
		{
			bankNote = "Use the rune pouch for the runes required by " + spell + ".";
		}
		else
		{
			bankNote = "Use the runes required by " + spell + ".";
		}

		final String routeWarning = ready || warnings.isEmpty()
			? ""
			: "Before routing: " + String.join("; ", warnings) + ".";
		final int magicLevel = client == null
			? 0 : client.getRealSkillLevel(Skill.MAGIC);
		final Map<Integer, Integer> carriedRuneContents =
			mergeCarriedRuneContents(pouchContents, inventory, equipment);
		final int castsAvailable = calculateCastsAvailable(
			spell, carriedRuneContents
		);

		return new PreparationPlan(
			true, ready, headline, detail, bankNote, routeWarning, bankTagItemIds,
			rules.getSpellbook(),
			correctBook, true, magicLevel, runeStatuses, spell, castsAvailable,
			explicitRunesReady
		);
	}

	/**
	 * Ownership controls which physical rune item is placed in the Bank Tag, but
	 * it must never hide an authored spell requirement from the readiness box.
	 * Publish the base requirement with zero/current carried quantity whenever no
	 * owned base or combination rune was selected for it.
	 */
	private static void addUnownedRequirementStatuses(
		final List<SlayerMethodRules.PouchRuneRequirement> requirements,
		final List<SlayerRuneOwnershipPolicy.ResolvedRune> resolvedRunes,
		final Map<Integer, Integer> pouchContents,
		final List<RuneStatus> statuses)
	{
		if (requirements == null || statuses == null)
		{
			return;
		}
		for (final SlayerMethodRules.PouchRuneRequirement requirement
			: requirements)
		{
			boolean covered = false;
			if (resolvedRunes != null)
			{
				for (final SlayerRuneOwnershipPolicy.ResolvedRune resolved
					: resolvedRunes)
				{
					if (SlayerRuneOwnershipPolicy.satisfies(
						resolved.getItemId(), requirement.getItemId()
					))
					{
						covered = true;
						break;
					}
				}
			}
			if (!covered)
			{
				statuses.add(new RuneStatus(
					requirement.getName(),
					requirement.getMinimumQuantity(),
					effectiveRuneQuantity(
						pouchContents, requirement.getItemId()
					)
				));
			}
		}
	}

	/**
	 * Number of casts the currently carried rune package can support. For a
	 * combined recommendation such as Blood Barrage + Ice Barrage, return the
	 * lower individual count so the displayed number is a conservative guarantee:
	 * either selected spell can still be cast at least this many times.
	 */
	private static int calculateCastsAvailable(
		final String spellName,
		final Map<Integer, Integer> carriedRunes)
	{
		final String spell = normalize(spellName);
		if (spell.contains("blood barrage") && spell.contains("ice barrage"))
		{
			return Math.min(
				bloodBarrageCasts(carriedRunes),
				iceBarrageCasts(carriedRunes)
			);
		}
		if (spell.contains("blood barrage"))
		{
			return bloodBarrageCasts(carriedRunes);
		}
		if (spell.contains("ice barrage"))
		{
			return iceBarrageCasts(carriedRunes);
		}
		if (spell.contains("ice burst"))
		{
			return iceBurstCasts(carriedRunes);
		}
		return 0;
	}

	private static int bloodBarrageCasts(final Map<Integer, Integer> runes)
	{
		return castsFrom(
			runes,
			new int[][]
			{
				{SOUL_RUNE, 1},
				{BLOOD_RUNE, 4},
				{DEATH_RUNE, 4}
			}
		);
	}

	private static int iceBarrageCasts(final Map<Integer, Integer> runes)
	{
		return castsFrom(
			runes,
			new int[][]
			{
				{WATER_RUNE, 6},
				{BLOOD_RUNE, 2},
				{DEATH_RUNE, 4}
			}
		);
	}

	private static int iceBurstCasts(final Map<Integer, Integer> runes)
	{
		return castsFrom(
			runes,
			new int[][]
			{
				{WATER_RUNE, 4},
				{CHAOS_RUNE, 4},
				{DEATH_RUNE, 2}
			}
		);
	}

	private static int castsFrom(
		final Map<Integer, Integer> runes,
		final int[][] costs)
	{
		if (runes == null || runes.isEmpty() || costs == null || costs.length == 0)
		{
			return 0;
		}

		int casts = Integer.MAX_VALUE;
		for (final int[] cost : costs)
		{
			if (cost == null || cost.length < 2 || cost[1] <= 0)
			{
				continue;
			}
			casts = Math.min(
				casts,
				effectiveRuneQuantity(runes, cost[0]) / cost[1]
			);
		}
		return casts == Integer.MAX_VALUE ? 0 : Math.max(0, casts);
	}

	/**
	 * Cast counts use everything the player is actually carrying, not the bank.
	 * This also covers the fourth rune spilling out of a normal three-slot pouch.
	 */
	private static Map<Integer, Integer> mergeCarriedRuneContents(
		final Map<Integer, Integer> pouchContents,
		final ItemContainer inventory,
		final ItemContainer equipment)
	{
		final Map<Integer, Integer> contents = new LinkedHashMap<>();
		if (pouchContents != null)
		{
			contents.putAll(pouchContents);
		}
		mergeContainer(contents, inventory);
		mergeContainer(contents, equipment);
		return contents;
	}

	private static Map<Integer, Integer> ownedItemQuantities(
		final Map<Integer, Integer> pouchContents,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> bankItems)
	{
		final Map<Integer, Integer> contents = new LinkedHashMap<>();
		if (bankItems != null)
		{
			for (final Map.Entry<Integer, Integer> entry : bankItems.entrySet())
			{
				if (entry.getKey() != null && entry.getKey() > 0
					&& entry.getValue() != null && entry.getValue() > 0)
				{
					contents.merge(
						entry.getKey(), entry.getValue(), Integer::sum
					);
				}
			}
		}
		if (pouchContents != null)
		{
			for (final Map.Entry<Integer, Integer> entry : pouchContents.entrySet())
			{
				if (entry.getKey() != null && entry.getKey() > 0
					&& entry.getValue() != null && entry.getValue() > 0)
				{
					contents.merge(
						entry.getKey(), entry.getValue(), Integer::sum
					);
				}
			}
		}
		mergeContainer(contents, inventory);
		mergeContainer(contents, equipment);
		return contents;
	}

	private static void mergeContainer(
		final Map<Integer, Integer> contents,
		final ItemContainer container)
	{
		if (contents == null || container == null)
		{
			return;
		}
		for (final Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			contents.merge(item.getId(), item.getQuantity(), Integer::sum);
		}
	}

	private static int spellbookVarbitValue(final SlayerMethodRules.Spellbook spellbook)
	{
		if (spellbook == SlayerMethodRules.Spellbook.STANDARD) return STANDARD_SPELLBOOK;
		if (spellbook == SlayerMethodRules.Spellbook.ANCIENT) return ANCIENT_SPELLBOOK;
		if (spellbook == SlayerMethodRules.Spellbook.LUNAR) return LUNAR_SPELLBOOK;
		if (spellbook == SlayerMethodRules.Spellbook.ARCEUUS) return ARCEUUS_SPELLBOOK;
		return -1;
	}

	private static String spellbookDisplayName(final SlayerMethodRules.Spellbook spellbook)
	{
		if (spellbook == SlayerMethodRules.Spellbook.STANDARD) return "Standard spellbook";
		if (spellbook == SlayerMethodRules.Spellbook.ANCIENT) return "Ancient Magicks";
		if (spellbook == SlayerMethodRules.Spellbook.LUNAR) return "Lunar spellbook";
		if (spellbook == SlayerMethodRules.Spellbook.ARCEUUS) return "Arceuus spellbook";
		return "Spellbook";
	}

	private static String safeSpellName(final String value)
	{
		return value == null || value.trim().isEmpty() ? "Selected spell" : value.trim();
	}

	private static int pouchCapacity(final int pouchItemId)
	{
		for (final int divineId : DIVINE_RUNE_POUCH_IDS)
		{
			if (pouchItemId == divineId)
			{
				return 4;
			}
		}
		return pouchItemId > 0 ? 3 : 0;
	}

	private static int runeIdForName(final String value)
	{
		final String name = normalize(value);
		if (name.contains("air rune")) return AIR_RUNE;
		if (name.contains("water rune")) return WATER_RUNE;
		if (name.contains("earth rune")) return EARTH_RUNE;
		if (name.contains("fire rune")) return FIRE_RUNE;
		if (name.contains("mind rune")) return MIND_RUNE;
		if (name.contains("chaos rune")) return CHAOS_RUNE;
		if (name.contains("death rune")) return DEATH_RUNE;
		if (name.contains("blood rune")) return BLOOD_RUNE;
		if (name.contains("cosmic rune")) return COSMIC_RUNE;
		if (name.contains("soul rune")) return SOUL_RUNE;
		if (name.contains("wrath rune")) return WRATH_RUNE;
		if (name.contains("aether rune")) return AETHER_RUNE;
		return -1;
	}


	/**
	 * Returns the effective quantity available for a rune requirement. A
	 * combination rune contributes its full stack to either component it can
	 * substitute for (e.g. Mud satisfies both Water and Earth requirements).
	 */
	private static int effectiveRuneQuantity(
		final Map<Integer, Integer> contents,
		final int requiredRuneId)
	{
		if (contents == null || contents.isEmpty())
		{
			return 0;
		}

		int quantity = contents.getOrDefault(requiredRuneId, 0);
		for (final int candidateId : combinationRuneIdsFor(requiredRuneId))
		{
			quantity += contents.getOrDefault(candidateId, 0);
		}
		return quantity;
	}

	/**
	 * For Bank Tag preparation, preserve an already-loaded valid combination
	 * rune instead of incorrectly asking the player to replace it with the base
	 * rune. Returns -1 when the account owns no satisfying rune so Bank Tags never
	 * manufacture an unowned rune recommendation.
	 */
	private static int preferredSatisfyingRuneId(
		final Map<Integer, Integer> contents,
		final int requiredRuneId)
	{
		if (contents != null
			&& contents.getOrDefault(requiredRuneId, 0) > 0)
		{
			return requiredRuneId;
		}

		int bestId = -1;
		int bestQuantity = 0;
		if (contents != null)
		{
			for (final int candidateId : combinationRuneIdsFor(requiredRuneId))
			{
				final int quantity = contents.getOrDefault(candidateId, 0);
				if (quantity > bestQuantity)
				{
					bestId = candidateId;
					bestQuantity = quantity;
				}
			}
		}
		return bestId;
	}

	private static int[] combinationRuneIdsFor(final int requiredRuneId)
	{
		switch (requiredRuneId)
		{
			case AIR_RUNE:
				return new int[] {MIST_RUNE, DUST_RUNE, SMOKE_RUNE};
			case WATER_RUNE:
				return new int[] {MIST_RUNE, MUD_RUNE, STEAM_RUNE};
			case EARTH_RUNE:
				return new int[] {DUST_RUNE, MUD_RUNE, LAVA_RUNE};
			case FIRE_RUNE:
				return new int[] {SMOKE_RUNE, STEAM_RUNE, LAVA_RUNE};
			case COSMIC_RUNE:
			case SOUL_RUNE:
				return new int[] {AETHER_RUNE};
			default:
				return new int[0];
		}
	}

	private static String cleanRuneName(final String value)
	{
		final String normalized = normalize(value);
		if (normalized.endsWith(" runes"))
		{
			return normalized.substring(0, normalized.length() - 6);
		}
		if (normalized.endsWith(" rune"))
		{
			return normalized.substring(0, normalized.length() - 5);
		}
		return normalized;
	}

	private static PreparationPlan resolveLegacySmokeDevils(
		final Client client,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> bankItems)
	{
		final int magicLevel = client == null
			? 0 : client.getRealSkillLevel(Skill.MAGIC);
		final boolean barrage = magicLevel >= ICE_BARRAGE_LEVEL;
		final String spellName = barrage ? "Ice Barrage" : "Ice Burst";
		final List<RuneRequirement> runes = barrage
			? barrageRunes() : burstRunes();

		final int carriedPouchId = firstCarriedPouch(
			inventory,
			equipment,
			false
		);
		final int ownedPouchId = firstOwnedPouch(
			inventory,
			equipment,
			bankItems,
			false
		);
		final int layoutPouchId = ownedPouchId > 0
			? ownedPouchId : ItemID.BH_RUNE_POUCH;

		final Map<Integer, Integer> pouchContents = readPouchContents(client);
		final Map<Integer, Integer> ownedItems = ownedItemQuantities(
			pouchContents, inventory, equipment, bankItems
		);
		final List<String> missingRunes = new ArrayList<>();
		for (final RuneRequirement rune : runes)
		{
			if (effectiveRuneQuantity(pouchContents, rune.itemId) <= 0)
			{
				missingRunes.add(rune.name);
			}
		}

		final boolean ancientBook = client != null
			&& client.getVarbitValue(SPELLBOOK_VARBIT) == ANCIENT_SPELLBOOK;
		final boolean levelReady = magicLevel >= ICE_BURST_LEVEL;
		final boolean pouchOwned = carriedPouchId > 0;
		final boolean pouchLoaded = missingRunes.isEmpty();
		final boolean ready = ancientBook
			&& levelReady && pouchOwned && pouchLoaded;

		final List<String> warnings = new ArrayList<>();
		if (!ancientBook)
		{
			warnings.add("Switch to Ancient Magicks");
		}
		if (!levelReady)
		{
			warnings.add("70 Magic is required for Ice Burst");
		}
		if (!pouchOwned)
		{
			warnings.add("Withdraw a rune pouch");
		}
		if (!missingRunes.isEmpty())
		{
			warnings.add(
				"Load " + joinNames(missingRunes) + " into the rune pouch"
			);
		}

		final List<Integer> bankTagItemIds = new ArrayList<>();
		bankTagItemIds.add(layoutPouchId);
		for (final RuneRequirement rune : runes)
		{
			final int ownedRuneId = preferredSatisfyingRuneId(
				ownedItems, rune.itemId
			);
			if (ownedRuneId > 0)
			{
				bankTagItemIds.add(ownedRuneId);
			}
		}

		final String runeNames = joinRuneNames(runes);
		final Map<Integer, Integer> carriedRuneContents =
			mergeCarriedRuneContents(pouchContents, inventory, equipment);
		final int castsAvailable = calculateCastsAvailable(
			spellName, carriedRuneContents
		);
		final List<RuneStatus> runeStatuses = new ArrayList<>();
		for (final RuneRequirement rune : runes)
		{
			runeStatuses.add(new RuneStatus(
				rune.name,
				rune.perCast,
				effectiveRuneQuantity(carriedRuneContents, rune.itemId)
			));
		}
		return new PreparationPlan(
			true,
			ready,
			ready
				? "Ancient Magicks ready • " + spellName
				: "Preparation required • " + spellName,
			ready
				? "Rune pouch contains " + runeNames + "."
				: String.join(". ", warnings) + ".",
			"Load " + runeNames + " into the rune pouch before leaving.",
			ready ? "" : "Before routing: " + String.join("; ", warnings) + ".",
			bankTagItemIds,
			SlayerMethodRules.Spellbook.ANCIENT,
			ancientBook,
			levelReady,
			magicLevel,
			runeStatuses,
			spellName,
			castsAvailable,
			pouchLoaded
		);
	}

	private static boolean isStandardSmokeDevilMagicMethod(
		final String taskName,
		final SlayerTaskVariant variant,
		final SlayerRecommendation recommendation)
	{
		if (!normalize(taskName).equals("smoke devils")
			|| (variant != null && variant.isBoss())
			|| recommendation == null)
		{
			return false;
		}
		final String method = normalize(recommendation.getMethod());
		return method.contains("barrage")
			|| method.contains("burst")
			|| method.contains("ancient magicks");
	}

	private static List<RuneRequirement> barrageRunes()
	{
		final List<RuneRequirement> runes = new ArrayList<>();
		runes.add(new RuneRequirement(WATER_RUNE, "Water", 6));
		runes.add(new RuneRequirement(BLOOD_RUNE, "Blood", 2));
		runes.add(new RuneRequirement(DEATH_RUNE, "Death", 4));
		return Collections.unmodifiableList(runes);
	}

	private static List<RuneRequirement> burstRunes()
	{
		final List<RuneRequirement> runes = new ArrayList<>();
		runes.add(new RuneRequirement(WATER_RUNE, "Water", 4));
		runes.add(new RuneRequirement(CHAOS_RUNE, "Chaos", 4));
		runes.add(new RuneRequirement(DEATH_RUNE, "Death", 2));
		return Collections.unmodifiableList(runes);
	}

	private static Map<Integer, Integer> readPouchContents(final Client client)
	{
		if (client == null)
		{
			return Collections.emptyMap();
		}
		final EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		if (runeEnum == null)
		{
			return Collections.emptyMap();
		}

		final Map<Integer, Integer> contents = new LinkedHashMap<>();
		for (int slot = 0; slot < RUNE_TYPE_VARBITS.length; slot++)
		{
			final int pouchRuneType = client.getVarbitValue(RUNE_TYPE_VARBITS[slot]);
			final int quantity = client.getVarbitValue(RUNE_QUANTITY_VARBITS[slot]);
			if (pouchRuneType <= 0 || quantity <= 0)
			{
				continue;
			}
			final int runeItemId = runeEnum.getIntValue(pouchRuneType);
			if (runeItemId > 0)
			{
				contents.merge(runeItemId, quantity, Integer::sum);
			}
		}
		return contents;
	}

	private static int firstCarriedPouch(
		final ItemContainer inventory,
		final ItemContainer equipment,
		final boolean divineOnly)
	{
		final int[] candidates = divineOnly
			? DIVINE_RUNE_POUCH_IDS : RUNE_POUCH_IDS;
		for (final int pouchId : candidates)
		{
			if (contains(inventory, pouchId) || contains(equipment, pouchId))
			{
				return pouchId;
			}
		}
		return -1;
	}

	private static int firstOwnedPouch(
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> bankItems,
		final boolean divineOnly)
	{
		final int[] candidates = divineOnly
			? DIVINE_RUNE_POUCH_IDS : RUNE_POUCH_IDS;
		for (final int pouchId : candidates)
		{
			if (contains(inventory, pouchId)
				|| contains(equipment, pouchId)
				|| bankItems != null && bankItems.getOrDefault(pouchId, 0) > 0)
			{
				return pouchId;
			}
		}
		return -1;
	}

	private static boolean contains(
		final ItemContainer container,
		final int itemId)
	{
		if (container == null)
		{
			return false;
		}
		for (final Item item : container.getItems())
		{
			if (item != null && item.getId() == itemId && item.getQuantity() > 0)
			{
				return true;
			}
		}
		return false;
	}

	private static String joinPouchRuneNames(
		final List<SlayerMethodRules.PouchRuneRequirement> runes)
	{
		final List<String> names = new ArrayList<>();
		for (final SlayerMethodRules.PouchRuneRequirement rune : runes)
		{
			names.add(rune.getName());
		}
		return joinNames(names);
	}

	private static String joinRuneNames(final List<RuneRequirement> runes)
	{
		final List<String> names = new ArrayList<>();
		for (final RuneRequirement rune : runes)
		{
			names.add(rune.name);
		}
		return joinNames(names);
	}

	private static String joinNames(final List<String> values)
	{
		if (values == null || values.isEmpty())
		{
			return "";
		}
		if (values.size() == 1)
		{
			return values.get(0);
		}
		if (values.size() == 2)
		{
			return values.get(0) + " and " + values.get(1);
		}
		return String.join(", ", values.subList(0, values.size() - 1))
			+ ", and " + values.get(values.size() - 1);
	}

	private static String normalize(final String value)
	{
		return value == null
			? ""
			: value.toLowerCase(Locale.ENGLISH)
				.replace('\u2019', '\'')
				.replaceAll("[^a-z0-9]+", " ")
				.trim()
				.replaceFirst("^the\\s+", "");
	}

	private static final class RuneRequirement
	{
		private final int itemId;
		private final String name;
		private final int perCast;

		private RuneRequirement(
			final int itemId,
			final String name,
			final int perCast)
		{
			this.itemId = itemId;
			this.name = name;
			this.perCast = Math.max(1, perCast);
		}
	}

	public static final class RuneStatus
	{
		private final String name;
		private final int required;
		private final int available;

		public RuneStatus(final String name, final int required, final int available)
		{
			this.name = name == null ? "" : name.trim();
			this.required = Math.max(0, required);
			this.available = Math.max(0, available);
		}

		public String getName() { return name; }
		public String getRuneName() { return name; }
		public int getRequired() { return required; }
		public int getRequiredQuantity() { return required; }
		public int getAvailable() { return available; }
		public int getAvailableQuantity() { return available; }
		public int getPerCast() { return required; }
		public boolean isReady() { return available >= required; }
		public boolean isAvailable() { return isReady(); }
		public boolean isReadyForOneCast() { return isReady(); }
	}

	public static final class PreparationPlan
	{
		private static final PreparationPlan NONE = new PreparationPlan(
			false, true, "", "", "", "", Collections.emptyList(),
			SlayerMethodRules.Spellbook.NONE
		);

		private final boolean active;
		private final boolean ready;
		private final String headline;
		private final String detail;
		private final String bankNote;
		private final String routeWarning;
		private final List<Integer> bankTagItemIds;
		private final SlayerMethodRules.Spellbook requiredSpellbook;
		private final boolean spellbookReady;
		private final boolean levelReady;
		private final int magicLevel;
		private final List<RuneStatus> runeStatuses;
		private final String spellName;
		private final int castsAvailable;
		private final boolean runesReady;

		private PreparationPlan(
			final boolean active,
			final boolean ready,
			final String headline,
			final String detail,
			final String bankNote,
			final String routeWarning,
			final List<Integer> bankTagItemIds,
			final SlayerMethodRules.Spellbook requiredSpellbook)
		{
			this(active, ready, headline, detail, bankNote, routeWarning,
				bankTagItemIds, requiredSpellbook, ready, ready, 0, Collections.emptyList(),
				headline, 0, ready);
		}

		private PreparationPlan(
			final boolean active,
			final boolean ready,
			final String headline,
			final String detail,
			final String bankNote,
			final String routeWarning,
			final List<Integer> bankTagItemIds,
			final SlayerMethodRules.Spellbook requiredSpellbook,
			final boolean spellbookReady,
			final boolean levelReady,
			final int magicLevel,
			final List<RuneStatus> runeStatuses,
			final String spellName,
			final int castsAvailable,
			final boolean runesReady)
		{
			this.active = active;
			this.ready = ready;
			this.headline = safe(headline);
			this.detail = safe(detail);
			this.bankNote = safe(bankNote);
			this.routeWarning = safe(routeWarning);
			this.bankTagItemIds = Collections.unmodifiableList(
				new ArrayList<>(new LinkedHashSet<>(bankTagItemIds))
			);
			this.requiredSpellbook = requiredSpellbook == null
				? SlayerMethodRules.Spellbook.NONE : requiredSpellbook;
			this.spellbookReady = spellbookReady;
			this.levelReady = levelReady;
			this.magicLevel = Math.max(0, magicLevel);
			this.runeStatuses = Collections.unmodifiableList(
				new ArrayList<>(runeStatuses == null
					? Collections.emptyList() : runeStatuses)
			);
			this.spellName = safe(spellName);
			this.castsAvailable = Math.max(0, castsAvailable);
			this.runesReady = runesReady;
		}

		public static PreparationPlan none() { return NONE; }
		public boolean isActive() { return active; }
		public boolean isReady() { return ready; }
		public String getHeadline() { return headline; }
		public String getDetail() { return detail; }
		public String getBankNote() { return bankNote; }
		public String getRouteWarning() { return routeWarning; }
		public List<Integer> getBankTagItemIds() { return bankTagItemIds; }

		/* Compatibility API consumed by SlayerTaskReadinessOverlay. */
		public boolean isSpellbookReady() { return spellbookReady; }
		public SlayerMethodRules.Spellbook getRequiredSpellbook()
		{
			return requiredSpellbook;
		}
		public boolean isLevelReady() { return levelReady; }
		public int getMagicLevel() { return magicLevel; }
		public List<RuneStatus> getRuneStatuses() { return runeStatuses; }
		public String getSpellName() { return spellName; }
		public int getCastsAvailable() { return castsAvailable; }
		public boolean isRunesReady() { return runesReady; }

		/* Compatibility API for the readiness overlay. The panel's headline/detail
		 * remain authoritative; these aliases prevent older overlays from depending
		 * on a separate preparation model. */
		public String getEncounterName() { return ""; }
		public String getSpellbookName()
		{
			if (requiredSpellbook == SlayerMethodRules.Spellbook.ANCIENT) return "Ancient Magicks";
			if (requiredSpellbook == SlayerMethodRules.Spellbook.ARCEUUS) return "Arceuus spellbook";
			if (requiredSpellbook == SlayerMethodRules.Spellbook.LUNAR) return "Lunar spellbook";
			if (requiredSpellbook == SlayerMethodRules.Spellbook.STANDARD) return "Standard spellbook";
			return active ? "Spellbook" : "";
		}
		public int getRequiredMagicLevel()
		{
			final String normalized = spellName.toLowerCase(Locale.ENGLISH);
			if (normalized.contains("ice barrage")) return ICE_BARRAGE_LEVEL;
			if (normalized.contains("ice burst")) return ICE_BURST_LEVEL;
			return 0;
		}

		private static String safe(final String value)
		{
			return value == null ? "" : value.trim();
		}
	}
}
