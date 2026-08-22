package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.game.ItemStats;

public final class SlayerLoadoutAnalyzer
{
	/*
	 * Dizana's quiver exposes a second ammo slot outside InventoryID.WORN.
	 * Keep it in ownership analysis as a synthetic equipment-only slot so it
	 * can satisfy bow/crossbow ammunition without being mistaken for the normal
	 * ammo slot (EquipmentInventorySlot.AMMO = 13).
	 */
	private static final int EXTRA_QUIVER_AMMO_SLOT = 14;
	/*
	 * Standard arrows ranked by their live-game Ranged Strength values. Seeking
	 * variants are the same arrow tiers while stored in the quiver, and are listed
	 * first within each tier so the nested stack remains visible when it ties a
	 * loose stack. Keep every standard tier here; stopping at rune/broad made a
	 * player who owned only lower-tier arrows appear to own no usable ammunition.
	 */
	private static final String[] STRONGEST_STANDARD_ARROW_PRIORITY =
	{
		"seeking dragon arrow", "dragon arrow",
		"seeking amethyst arrow", "amethyst arrow",
		"seeking rune arrow", "rune arrow",
		"seeking adamant arrow", "adamant arrow",
		"seeking broad arrow", "broad arrow",
		"seeking mithril arrow", "mithril arrow",
		"seeking steel arrow", "steel arrow",
		"seeking iron arrow", "iron arrow",
		"seeking bronze arrow", "bronze arrow"
	};
	private static final String[] STRONGEST_NON_DRAGON_ARROW_PRIORITY =
	{
		"seeking amethyst arrow", "amethyst arrow",
		"seeking rune arrow", "rune arrow",
		"seeking adamant arrow", "adamant arrow",
		"seeking broad arrow", "broad arrow",
		"seeking mithril arrow", "mithril arrow",
		"seeking steel arrow", "steel arrow",
		"seeking iron arrow", "iron arrow",
		"seeking bronze arrow", "bronze arrow"
	};
	/*
	 * Profit is a cost-per-kill policy, not a second MAX_DPS alias. Current Wiki
	 * values put Rune far below Amethyst and especially Dragon arrows while keeping
	 * most of the useful Ranged Strength. Bosses retain Amethyst as the practical
	 * stronger budget tier; Dragon remains a last high-tier ownership fallback.
	 */
	private static final String[] EFFICIENT_REGULAR_ARROW_PRIORITY =
	{
		"rune arrow", "seeking rune arrow",
		"amethyst arrow", "seeking amethyst arrow",
		"dragon arrow", "seeking dragon arrow",
		"adamant arrow", "seeking adamant arrow",
		"broad arrow", "seeking broad arrow",
		"mithril arrow", "seeking mithril arrow",
		"steel arrow", "seeking steel arrow",
		"iron arrow", "seeking iron arrow",
		"bronze arrow", "seeking bronze arrow"
	};
	private static final String[] EFFICIENT_BOSS_ARROW_PRIORITY =
	{
		"amethyst arrow", "seeking amethyst arrow",
		"rune arrow", "seeking rune arrow",
		"dragon arrow", "seeking dragon arrow",
		"adamant arrow", "seeking adamant arrow",
		"broad arrow", "seeking broad arrow",
		"mithril arrow", "seeking mithril arrow",
		"steel arrow", "seeking steel arrow",
		"iron arrow", "seeking iron arrow",
		"bronze arrow", "seeking bronze arrow"
	};
	/*
	 * Zuk's defence makes the Seeking accuracy/minimum-hit effect valuable enough
	 * for Seeking Amethyst to outrank ordinary Dragon arrows. Keep this separate
	 * from the generic strength-first order so other Slayer encounters retain their
	 * normal ammunition policy.
	 */
	private static final String[] INFERNO_ARROW_PRIORITY =
	{
		"seeking dragon arrow",
		"seeking amethyst arrow",
		"dragon arrow",
		"seeking rune arrow",
		"amethyst arrow",
		"rune arrow",
		"seeking adamant arrow",
		"seeking broad arrow",
		"adamant arrow",
		"seeking mithril arrow",
		"broad arrow",
		"seeking steel arrow",
		"mithril arrow",
		"seeking iron arrow",
		"steel arrow",
		"seeking bronze arrow",
		"iron arrow",
		"bronze arrow"
	};

	private final ItemManager itemManager;
	private Map<Integer, Integer> cachedBankSnapshotSource =
		Collections.emptyMap();
	private List<OwnedItem> cachedBankOwnedItems =
		Collections.emptyList();
	private String slayerHelmetPreference = SlayerHelmetPreference.COMBAT_ACHIEVEMENT;
	private int randomSlayerHelmetItemId = -1;
	private int randomSlayerHelmetPoolSignature;
	private boolean randomSlayerHelmetRerollPending;
	private boolean desertEliteDiaryComplete;
	private SlayerAchievementDiarySnapshot achievementDiaries =
		SlayerAchievementDiarySnapshot.empty();

	public SlayerLoadoutAnalyzer(final ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	public void invalidateBankSnapshot()
	{
		cachedBankSnapshotSource = Collections.emptyMap();
		cachedBankOwnedItems = Collections.emptyList();
	}

	public void setSlayerHelmetPreference(final String preference)
	{
		final String normalized = SlayerHelmetPreference.normalize(preference);
		if (!normalized.equals(slayerHelmetPreference))
		{
			slayerHelmetPreference = normalized;
			randomSlayerHelmetItemId = -1;
			randomSlayerHelmetPoolSignature = 0;
			randomSlayerHelmetRerollPending = false;
		}
	}

	public void setDesertEliteDiaryComplete(final boolean complete)
	{
		desertEliteDiaryComplete = complete;
	}

	public void setAchievementDiaries(
		final SlayerAchievementDiarySnapshot snapshot)
	{
		achievementDiaries = snapshot == null
			? SlayerAchievementDiarySnapshot.empty() : snapshot;
	}

	public boolean rerollRandomSlayerHelmet()
	{
		if (!SlayerHelmetPreference.isRandom(slayerHelmetPreference))
		{
			return false;
		}
		randomSlayerHelmetRerollPending = true;
		return true;
	}

	public List<String> ownedSlayerHelmetNames(
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> cachedBankItems)
	{
		final List<OwnedItem> owned = new ArrayList<>();
		owned.addAll(snapshotOwned(equipment, SlayerLoadoutItem.Status.EQUIPPED, true));
		owned.addAll(snapshotOwned(inventory, SlayerLoadoutItem.Status.INVENTORY, false));
		owned.addAll(snapshotBank(cachedBankItems));
		final Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (final OwnedItem item : owned)
		{
			if (isSlayerHelmet(item))
			{
				names.add(item.displayName);
			}
		}
		return new ArrayList<>(names);
	}

	public Set<Integer> ownedSlayerHelmetItemIds(
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> cachedBankItems)
	{
		final List<OwnedItem> owned = new ArrayList<>();
		owned.addAll(snapshotOwned(equipment, SlayerLoadoutItem.Status.EQUIPPED, true));
		owned.addAll(snapshotOwned(inventory, SlayerLoadoutItem.Status.INVENTORY, false));
		owned.addAll(snapshotBank(cachedBankItems));
		final Set<Integer> itemIds = new LinkedHashSet<>();
		for (final OwnedItem item : owned)
		{
			if (isSlayerHelmet(item))
			{
				itemIds.add(item.itemId);
			}
		}
		return itemIds;
	}

	public List<String> slayerHelmetNamesForItemIds(
		final Iterable<Integer> itemIds)
	{
		final Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		if (itemIds == null || itemManager == null)
		{
			return new ArrayList<>(names);
		}
		for (final Integer itemId : itemIds)
		{
			if (itemId == null || itemId <= 0)
			{
				continue;
			}
			final String displayName = itemManager.getItemComposition(itemId).getName();
			if (normalize(displayName).contains("slayer helmet"))
			{
				names.add(displayName);
			}
		}
		return new ArrayList<>(names);
	}

	/**
	 * Re-evaluates an Automatic recommendation against items the player really
	 * owns. The catalog still defines every allowed method and weapon; this
	 * method only chooses between those reviewed candidates. It never invents a
	 * generic same-style fallback.
	 *
	 * The bank must have been scanned before Automatic is changed. Until then,
	 * inventory and equipment alone are not enough to conclude that a stronger
	 * setup is unavailable in the bank.
	 */
	public SlayerRecommendation resolveOwnedAutomaticRecommendation(
		final String taskName,
		final SlayerRecommendation recommendation,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> cachedBankItems,
		final boolean bankScanned,
		final SlayerTaskVariant taskVariant,
		final SlayerPlusConfig config)
	{
		if (recommendation == null
			|| !recommendation.hasTaskStrategy()
			|| config == null
			|| config.combatStylePreference()
				!= SlayerPreference.CombatStyle.AUTOMATIC
			|| !bankScanned)
		{
			return recommendation;
		}

		/*
		 * Resolve the selected encounter before comparing owned styles. A boss
		 * variant (for example Blue dragons -> Vorkath) has its own reviewed
		 * strategies; comparing the parent assignment here previously locked the
		 * loadout to ranged before ownership was considered at all.
		 */
		final SlayerTaskVariantCatalog.ResolvedTarget effectiveTarget =
			SlayerTaskVariantCatalog.resolve(
				taskName, taskVariant, recommendation, config
			);
		if (effectiveTarget == null
			|| !effectiveTarget.isValid()
			|| effectiveTarget.getStrategy() == null)
		{
			return recommendation;
		}
		final String effectiveTask = effectiveTarget.getTaskName();
		final String effectiveLocation = effectiveTarget.getLocation();
		final SlayerTaskStrategy effectiveStrategy =
			effectiveTarget.getStrategy();

		final List<OwnedItem> allOwned = new ArrayList<>();
		allOwned.addAll(snapshotOwned(
			equipment,
			SlayerLoadoutItem.Status.EQUIPPED,
			true
		));
		allOwned.addAll(snapshotOwned(
			inventory,
			SlayerLoadoutItem.Status.INVENTORY,
			false
		));
		allOwned.addAll(snapshotBank(cachedBankItems));

		final boolean wilderness = isWildernessLoadout(
			normalize(effectiveLocation),
			normalize(effectiveTarget.getRestriction())
		);
		final List<OwnedStrategyCandidate> candidates = new ArrayList<>();
		addOwnedStrategyCandidate(
			candidates,
			effectiveStrategy,
			allOwned,
			effectiveTask,
			config.playstyle(),
			true
		);

		final boolean automaticStyleLocked =
			effectiveStrategy.hasTag(
				SlayerTaskStrategy.MethodTag.AUTOMATIC_STYLE_LOCKED
			);

		if (!automaticStyleLocked)
		{
			for (final SlayerPreference.CombatStyle preference
				: new SlayerPreference.CombatStyle[]{
					SlayerPreference.CombatStyle.PREFER_MELEE,
					SlayerPreference.CombatStyle.PREFER_RANGED,
					SlayerPreference.CombatStyle.PREFER_MAGIC
				})
			{
				final SlayerTaskStrategy candidate =
					SlayerTaskStrategyCatalog.resolve(
						effectiveTask,
						config.playstyle(),
						config.cannonPreference(),
						config.burstPreference(),
						preference,
						effectiveLocation,
						wilderness
					);
				addOwnedStrategyCandidate(
					candidates,
					candidate,
					allOwned,
					effectiveTask,
					config.playstyle(),
					false
				);
			}
		}

		OwnedStrategyCandidate best = null;
		for (final OwnedStrategyCandidate candidate : candidates)
		{
			if (best == null || candidate.score > best.score)
			{
				best = candidate;
			}
		}

		if (best == null)
		{
			return recommendation;
		}

		final String ownedReason =
			"Automatic compared the reviewed viable combat styles against "
				+ "your real bank, inventory, and equipped items. "
				+ best.weapon.displayName
				+ " produced the strongest owned setup for this method. ";

		final SlayerTaskStrategy selectedStrategy =
			effectiveStrategy.hasTag(
				SlayerTaskStrategy.MethodTag.TURAEL_POINT_BOOST
			)
				? best.strategy.withAdditionalTags(
					SlayerTaskStrategy.MethodTag.TURAEL_POINT_BOOST
				)
				: best.strategy;
		final String resolvedMethod = SlayerMethodRuleCatalog.resolve(
			effectiveTask,
			effectiveLocation,
			selectedStrategy
		).getTaskMethod();

		return new SlayerRecommendation(
			effectiveLocation,
			resolvedMethod,
			ownedReason + best.strategy.getRationale(),
			effectiveTarget.getTravel(),
			effectiveTarget.getCannon(),
			recommendation.getRequirements(),
			effectiveTarget.getRestriction(),
			recommendation.getDestination(),
			selectedStrategy
		);
	}

	private static void addOwnedStrategyCandidate(
		final List<OwnedStrategyCandidate> candidates,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final String encounterName,
		final SlayerPreference.Playstyle playstyle,
		final boolean originalAutomatic)
	{
		if (strategy == null || !strategy.isReviewed())
		{
			return;
		}

		for (final OwnedStrategyCandidate existing : candidates)
		{
			if (sameStrategy(existing.strategy, strategy))
			{
				return;
			}
		}

		final List<String> priorities =
			strategy.getWeaponPrioritiesForPolicy().isEmpty()
				? strategy.getWeaponPriorities()
				: strategy.getWeaponPrioritiesForPolicy();
		final OwnedItem weapon = findPreferredEquipmentAllowed(
			allOwned,
			EquipmentInventorySlot.WEAPON,
			false,
			priorities,
			strategy
		);

		if (weapon == null)
		{
			return;
		}

		final int rank = matchingPriorityIndex(weapon, priorities);
		if (rank < 0
			|| !hasRequiredRangedAmmo(strategy, weapon, allOwned))
		{
			return;
		}

		int score = Math.max(10, 125 - rank * 18);
		score += ownedCoreEquipmentCoverage(strategy, allOwned) * 12;
		if (normalize(encounterName).equals("vorkath")
			&& !hasCompleteVorkathCore(strategy, weapon, allOwned))
		{
			return;
		}
		if (originalAutomatic)
		{
			score += 4;
		}
		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
		{
			score += 28;
		}
		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.MULTI_COMBAT))
		{
			score += 8;
		}
		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.VENATOR)
			&& weapon.matchesName("venator bow"))
		{
			score += 42;
		}
		if (strategy.hasTag(
			SlayerTaskStrategy.MethodTag.BURST_BARRAGE
		))
		{
			score += 36;
		}

		final SlayerPreference.Playstyle resolvedPlaystyle =
			playstyle == null
				? SlayerPreference.Playstyle.FAST_XP
				: playstyle;
		switch (resolvedPlaystyle)
		{
			case PROFIT:
				if (strategy.getCostPolicy()
					== SlayerTaskStrategy.CostPolicy.EFFICIENT)
				{
					score += 18;
				}
				score -= operatingCostPenalty(weapon);
				break;
			case FAST_XP:
			default:
				if (strategy.getCostPolicy()
					== SlayerTaskStrategy.CostPolicy.MAX_DPS)
				{
					score += 14;
				}
				if (strategy.getArmourFocus()
					== SlayerTaskStrategy.ArmourFocus.DAMAGE)
				{
					score += 8;
				}
				break;
		}

		candidates.add(new OwnedStrategyCandidate(
			strategy,
			weapon,
			score
		));
	}

	private static boolean sameStrategy(
		final SlayerTaskStrategy left,
		final SlayerTaskStrategy right)
	{
		return left == right
			|| (left != null
				&& right != null
				&& left.getCombatStyle() == right.getCombatStyle()
				&& normalize(left.getMethod()).equals(
					normalize(right.getMethod())
				));
	}

	private static int ownedCoreEquipmentCoverage(
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned)
	{
		final CombatStyle style = combatStyle(strategy);
		int coverage = 0;
		for (final EquipmentInventorySlot slot : new EquipmentInventorySlot[]{
			EquipmentInventorySlot.HEAD,
			EquipmentInventorySlot.CAPE,
			EquipmentInventorySlot.AMULET,
			EquipmentInventorySlot.BODY,
			EquipmentInventorySlot.LEGS,
			EquipmentInventorySlot.GLOVES,
			EquipmentInventorySlot.BOOTS,
			EquipmentInventorySlot.RING
		})
		{
			if (findBestEquipment(allOwned, slot, strategy, style, false) != null)
			{
				coverage++;
			}
		}
		return coverage;
	}

	/**
	 * Vorkath is never suggested as a partial style package. The weapon, Salve,
	 * body/legs, and the weapon-compatible off-hand are the defining pieces of
	 * the reviewed setup. Lesser reviewed pieces are valid; a missing defining
	 * piece makes Automatic try the other combat style instead.
	 */
	private static boolean hasCompleteVorkathCore(
		final SlayerTaskStrategy strategy,
		final OwnedItem weapon,
		final List<OwnedItem> allOwned)
	{
		if (strategy == null || weapon == null)
		{
			return false;
		}
		final CombatStyle style = combatStyle(strategy);
		final boolean ranged = style == CombatStyle.RANGED;
		final OwnedItem slayerHead = findPreferredVorkathSlayerHead(
			allOwned, ranged
		);
		final boolean slayerPackage = slayerHead != null
			&& containsAnyOwned(
				allOwned,
				ranged ? "necklace of anguish" : "amulet of rancour",
				ranged ? "amulet of fury" : "amulet of torture",
				"amulet of fury"
			);
		final boolean salvePackage = containsAnyOwned(
			allOwned,
			ranged ? "salve amulet ei" : "salve amulet e",
			ranged ? "salve amulet i" : "salve amulet ei"
		);
		final boolean head = findPreferredEquipment(
			allOwned,
			EquipmentInventorySlot.HEAD,
			false,
			ranged
				? asList("masori mask", "void ranger helm", "slayer helmet i",
					"crystal helm", "serpentine helm", "blessed coif")
				: asList("torva full helm", "slayer helmet i", "neitiznot faceguard",
					"serpentine helm", "oathplate helm", "justiciar faceguard",
					"blood moon helm", "helm of neitiznot", "barrows helm")
		) != null;
		final boolean body = findPreferredEquipment(
			allOwned,
			EquipmentInventorySlot.BODY,
			false,
			ranged
				? asList("masori body", "elite void top", "void knight top",
					"crystal body", "karil s leathertop", "blessed body",
					"black d hide body")
				: asList("torva platebody", "bandos chestplate", "oathplate chest",
					"justiciar chestguard", "blood moon chestplate",
					"barrows platebody", "fighter torso", "obsidian platebody")
		) != null;
		final boolean legs = findPreferredEquipment(
			allOwned,
			EquipmentInventorySlot.LEGS,
			false,
			ranged
				? asList("masori chaps", "elite void robe", "void knight robe",
					"crystal legs", "karil s leatherskirt", "blessed chaps",
					"black d hide chaps")
				: asList("torva platelegs", "bandos tassets", "oathplate legs",
					"justiciar legguards", "blood moon tassets",
					"barrows platelegs", "obsidian platelegs")
		) != null;
		final boolean offHand = weapon.isTwoHanded()
			|| findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.SHIELD,
				false,
				ranged
					? asList("dragonfire ward", "anti dragon shield")
					: asList("avernic defender", "dragon defender",
						"rune defender", "toktz ket xil", "rune kiteshield")
			) != null;
		return (slayerPackage || salvePackage)
			&& head && body && legs && offHand;
	}

	private static OwnedItem findPreferredVorkathSlayerHead(
		final List<OwnedItem> allOwned,
		final boolean ranged)
	{
		return findPreferredEquipment(
			allOwned,
			EquipmentInventorySlot.HEAD,
			false,
			ranged
				? asList("slayer helmet i", "black mask i")
				: asList(
					"slayer helmet i", "black mask i",
					"slayer helmet", "black mask"
				)
		);
	}

	private static int matchingPriorityIndex(
		final OwnedItem weapon,
		final List<String> priorities)
	{
		for (int index = 0; index < priorities.size(); index++)
		{
			final String fragment = normalize(priorities.get(index));
			if (!fragment.isEmpty()
				&& weapon.matchesName(fragment))
			{
				return index;
			}
		}
		return -1;
	}

	private static boolean hasRequiredRangedAmmo(
		final SlayerTaskStrategy strategy,
		final OwnedItem weapon,
		final List<OwnedItem> allOwned)
	{
		if (strategy.getCombatStyle()
			!= SlayerTaskStrategy.CombatStyle.RANGED)
		{
			return true;
		}

		if (weapon.matchesName("blowpipe")
			|| weapon.matchesName("bow of faerdhinen")
			|| weapon.matchesName("crystal bow")
			|| weapon.matchesName("webweaver bow")
			|| weapon.matchesName("craw s bow")
			|| weapon.matchesName("chinchompa")
			|| weapon.matchesName(" dart")
			|| weapon.normalizedName.endsWith("dart")
			|| weapon.matchesName(" knife")
			|| weapon.normalizedName.endsWith("knife"))
		{
			return true;
		}

		if (weapon.matchesName("ballista"))
		{
			return containsAnyOwned(allOwned,
				"dragon javelin", "amethyst javelin", "rune javelin");
		}
		if (weapon.matchesName("atlatl"))
		{
			return containsAnyOwned(allOwned, "atlatl dart");
		}
		if (weapon.matchesName("hunters sunlight crossbow"))
		{
			return containsAnyOwned(allOwned,
				"moonlight antler bolts", "sunlight antler bolts");
		}
		if (weapon.matchesName("crossbow"))
		{
			return containsAnyOwned(allOwned,
				"dragonstone dragon bolts e", "dragonstone bolts e",
				"ruby dragon bolts e", "diamond dragon bolts e",
				"dragon bolts", "amethyst broad bolts",
				"runite bolts", "broad bolts");
		}

		return containsAnyOwned(allOwned,
			"dragon arrow", "amethyst arrow",
			"rune arrow", "broad arrow");
	}

	private static boolean containsAnyOwned(
		final List<OwnedItem> allOwned,
		final String... fragments)
	{
		for (final OwnedItem item : allOwned)
		{
			for (final String fragment : fragments)
			{
				if (item.matchesName(fragment))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static Map<Integer, Integer> ownedItemQuantities(
		final List<OwnedItem> allOwned)
	{
		final Map<Integer, Integer> quantities = new LinkedHashMap<>();
		if (allOwned == null)
		{
			return quantities;
		}
		for (final OwnedItem item : allOwned)
		{
			if (item != null && item.itemId > 0 && item.quantity > 0)
			{
				quantities.merge(item.itemId, item.quantity, Integer::sum);
			}
		}
		return quantities;
	}

	private static int operatingCostPenalty(final OwnedItem weapon)
	{
		if (weapon.matchesName("scythe of vitur"))
		{
			return 60;
		}
		if (weapon.matchesName("toxic blowpipe")
			|| weapon.matchesName("sanguinesti staff")
			|| weapon.matchesName("tumeken s shadow"))
		{
			return 34;
		}
		if (weapon.matchesName("arclight")
			|| weapon.matchesName("crystal bow")
			|| weapon.matchesName("bow of faerdhinen"))
		{
			return 12;
		}
		return 0;
	}

	public SlayerLoadoutPlan analyze(
		final String taskName,
		final SlayerRecommendation recommendation,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> cachedBankItems,
		final boolean bankScanned)
	{
		return analyze(
			taskName,
			recommendation,
			inventory,
			equipment,
			cachedBankItems,
			bankScanned,
			SlayerTaskVariant.STANDARD_TASK,
			null,
			-1
		);
	}

	public SlayerLoadoutPlan analyze(
		final String taskName,
		final SlayerRecommendation recommendation,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> cachedBankItems,
		final boolean bankScanned,
		final SlayerTaskVariant taskVariant,
		final SlayerPlusConfig config)
	{
		return analyze(
			taskName,
			recommendation,
			inventory,
			equipment,
			cachedBankItems,
			bankScanned,
			taskVariant,
			config,
			-1
		);
	}

	public SlayerLoadoutPlan analyze(
		final String taskName,
		final SlayerRecommendation recommendation,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> cachedBankItems,
		final boolean bankScanned,
		final SlayerTaskVariant taskVariant,
		final SlayerPlusConfig config,
		final int remainingKills)
	{
		return analyze(
			taskName,
			recommendation,
			inventory,
			equipment,
			cachedBankItems,
			bankScanned,
			taskVariant,
			config,
			remainingKills,
			-1,
			0
		);
	}

	/**
	 * Full ownership analysis including Dizana's extra quiver ammunition slot.
	 * The extra slot is not part of InventoryID.WORN, so the plugin snapshots it
	 * from the same Dizana quiver varps used by RuneLite's built-in AmmoPlugin
	 * and passes the exact item/quantity here separately.
	 */
	public SlayerLoadoutPlan analyze(
		final String taskName,
		final SlayerRecommendation recommendation,
		final ItemContainer inventory,
		final ItemContainer equipment,
		final Map<Integer, Integer> cachedBankItems,
		final boolean bankScanned,
		final SlayerTaskVariant taskVariant,
		final SlayerPlusConfig config,
		final int remainingKills,
		final int extraQuiverAmmoItemId,
		final int extraQuiverAmmoQuantity)
	{
		final List<OwnedItem> inventoryItems =
			snapshotOwned(
				inventory,
				SlayerLoadoutItem.Status.INVENTORY,
				false
			);
		final List<OwnedItem> equipmentItems =
			snapshotOwned(
				equipment,
				SlayerLoadoutItem.Status.EQUIPPED,
				true
			);
		final List<OwnedItem> bankItems =
			snapshotBank(cachedBankItems);

		final List<OwnedItem> allOwned = new ArrayList<>();
		allOwned.addAll(equipmentItems);
		allOwned.addAll(inventoryItems);
		allOwned.addAll(bankItems);
		final OwnedItem extraQuiverAmmo = snapshotExtraQuiverAmmo(
			extraQuiverAmmoItemId,
			extraQuiverAmmoQuantity
		);
		if (extraQuiverAmmo != null)
		{
			allOwned.add(extraQuiverAmmo);
		}

		final Set<String> inventoryNames = namesOf(inventoryItems);
		final Set<String> equipmentNames = namesOf(equipmentItems);
		final Set<String> bankNames = namesOf(bankItems);

		final SlayerTaskVariantCatalog.ResolvedTarget target =
			SlayerTaskVariantCatalog.resolve(
				taskName,
				taskVariant,
				recommendation,
				config
			);

		final String task = normalize(target.getTaskName());
		final String location = normalize(target.getLocation());
		final String method = target.getStrategy() == null
			? ""
			: normalize(target.getStrategy().getMethod());
		final String cannon = normalize(target.getCannon());
		final String travel = normalize(target.getTravel());
		final String restriction = normalize(target.getRestriction());

		final boolean wildernessLoadout =
			isWildernessLoadout(location, restriction);
		final List<OwnedItem> recommendationOwned =
			filterRecommendationItems(
				allOwned,
				wildernessLoadout
			);

		final SlayerTaskStrategy strategy = target.getStrategy();
		if (strategy == null || !strategy.isReviewed())
		{
			return SlayerLoadoutPlan.researchPending(
				target.getDisplayName()
			);
		}

		final CombatStyle style = combatStyle(strategy);
		final List<Requirement> requirements =
			requirementsFor(
				task,
				location,
				style,
				remainingKills,
				desertEliteDiaryComplete
			);
		final boolean cannonSuggested =
			isCannonSuggested(cannon);
		final int recommendedFoodSlots =
			strategy.getRecommendedFoodSlots(remainingKills);
		final OwnedItem selectedWeapon =
			selectRecommendedWeapon(
				task,
				strategy,
				style,
				requirements,
				equipmentItems,
				recommendationOwned
			);

		final List<SlayerLoadoutItem> equipmentLayout =
			applySlayerHelmetPreference(
				buildEquipmentLayout(
					task,
					strategy,
					style,
					requirements,
					equipmentItems,
					recommendationOwned,
					bankScanned,
					selectedWeapon
				),
				allOwned,
				style
			);

		return new SlayerLoadoutPlan(
			buildEquipmentText(strategy, style, requirements),
			buildInventoryText(
				strategy,
				style,
				requirements,
				cannonSuggested,
				recommendedFoodSlots
			),
			buildOwnedText(
				style,
				requirements,
				cannonSuggested,
				inventoryNames,
				equipmentNames,
				bankNames,
				bankScanned
			),
			buildLayoutTitle(target.getDisplayName(), target.getLocation()),
			equipmentLayout,
			buildInventoryLayout(
				task,
				strategy,
				location,
				travel,
				style,
				requirements,
				cannonSuggested,
				recommendedFoodSlots,
				recommendationOwned,
				bankScanned,
				equipmentLayout
			),
			buildOptionalLayout(
				task,
				strategy,
				recommendationOwned,
				selectedWeapon
			)
		);
	}

	private List<SlayerLoadoutItem> applySlayerHelmetPreference(
		final List<SlayerLoadoutItem> layout,
		final List<OwnedItem> allOwned,
		final CombatStyle style)
	{
		if (layout == null || layout.isEmpty())
		{
			return layout;
		}

		final Map<String, OwnedItem> helmetsByName = new LinkedHashMap<>();
		for (final OwnedItem item : allOwned)
		{
			if (isSlayerHelmet(item)
				&& isSlayerHelmetValidForStyle(item, style))
			{
				helmetsByName.putIfAbsent(item.normalizedName, item);
			}
		}
		final List<OwnedItem> helmets = new ArrayList<>(helmetsByName.values());
		if (helmets.isEmpty())
		{
			return layout;
		}
		helmets.sort((left, right) -> Integer.compare(left.itemId, right.itemId));

		OwnedItem selected = null;
		if (SlayerHelmetPreference.isCombatAchievement(slayerHelmetPreference))
		{
			selected = firstCombatAchievementHelmet(helmets);
		}
		else if (SlayerHelmetPreference.isRandom(slayerHelmetPreference))
		{
			int signature = 1;
			for (final OwnedItem helmet : helmets)
			{
				signature = 31 * signature + helmet.itemId;
			}
			if (signature != randomSlayerHelmetPoolSignature)
			{
				randomSlayerHelmetPoolSignature = signature;
			}
			for (final OwnedItem helmet : helmets)
			{
				if (helmet.itemId == randomSlayerHelmetItemId)
				{
					selected = helmet;
					break;
				}
			}
			if (selected == null || randomSlayerHelmetRerollPending)
			{
				final List<OwnedItem> eligible = new ArrayList<>(helmets);
				if (randomSlayerHelmetRerollPending && eligible.size() > 1)
				{
					eligible.removeIf(
						helmet -> helmet.itemId == randomSlayerHelmetItemId
					);
				}
				selected = eligible.get(
					ThreadLocalRandom.current().nextInt(eligible.size())
				);
				randomSlayerHelmetItemId = selected.itemId;
				randomSlayerHelmetRerollPending = false;
			}
		}
		else
		{
			for (final OwnedItem helmet : helmets)
			{
				if (helmet.matchesExactDisplayName(slayerHelmetPreference))
				{
					selected = helmet;
					break;
				}
			}
		}

		if (selected == null)
		{
			return layout;
		}
		for (int index = 0; index < layout.size(); index++)
		{
			final SlayerLoadoutItem current = layout.get(index);
			final String name = normalize(current.getDisplayName());
			if (name.contains("slayer helmet") || name.contains("black mask"))
			{
				layout.set(index, selected.toLoadoutItem(1));
				break;
			}
		}
		return layout;
	}

	private static OwnedItem firstCombatAchievementHelmet(
		final List<OwnedItem> helmets)
	{
		OwnedItem selected = null;
		int selectedTier = 0;
		for (final OwnedItem helmet : helmets)
		{
			final int tier = combatAchievementHelmetTierForRegression(
				helmet.displayName
			);
			if (tier > selectedTier)
			{
				selected = helmet;
				selectedTier = tier;
			}
		}
		return selected;
	}

	static int combatAchievementHelmetTierForRegression(final String itemName)
	{
		final String name = normalize(itemName);
		if (name.startsWith("tzkal slayer helmet")) return 3;
		if (name.startsWith("vampyric slayer helmet")) return 2;
		if (name.startsWith("tztok slayer helmet")) return 1;
		return 0;
	}

	private static boolean isSlayerHelmet(final OwnedItem item)
	{
		return item != null
			&& item.normalizedName.contains("slayer helmet")
			&& (item.equipmentSlot < 0
				|| item.equipmentSlot == EquipmentInventorySlot.HEAD.getSlotIdx());
	}

	private static boolean isSlayerHelmetValidForStyle(
		final OwnedItem item,
		final CombatStyle style)
	{
		if (item == null || style == CombatStyle.MELEE
			|| style == CombatStyle.FLEXIBLE)
		{
			return item != null;
		}
		/* Ranged and Magic Slayer bonuses require an imbued helmet variant. */
		return item.normalizedName.endsWith(" i")
			|| item.normalizedName.contains(" i uncharged");
	}

	public Set<String> snapshotNames(final ItemContainer container)
	{
		if (container == null || itemManager == null)
		{
			return Collections.emptySet();
		}

		final Set<String> names = new HashSet<>();
		for (final Item item : container.getItems())
		{
			if (!isRealOwnedItem(item, false))
			{
				continue;
			}

			try
			{
				final int canonicalId = itemManager.canonicalize(item.getId());
				final ItemComposition composition =
					itemManager.getItemComposition(canonicalId);
				if (composition != null)
				{
					names.addAll(getMatchNames(
						canonicalId,
						composition.getName()
					));
				}
			}
			catch (RuntimeException ignored)
			{
				// A malformed or transient item definition should not break the panel.
			}
		}

		return names;
	}


	public Map<Integer, Integer> snapshotItems(
		final ItemContainer container)
	{
		if (container == null || itemManager == null)
		{
			return Collections.emptyMap();
		}

		final Map<Integer, Integer> items =
			new LinkedHashMap<>();

		for (final Item item : container.getItems())
		{
			if (!isRealOwnedItem(item, true))
			{
				continue;
			}

			/*
			 * Preserve the exact owned item ID. Canonicalizing here can collapse
			 * dose variants (for example Super restore(4) into another dose),
			 * which makes every downstream task layout recommend the wrong item.
			 * Functional equivalence is handled later through match aliases.
			 */
			final int itemId = item.getId();
			final int quantity = item.getQuantity();

			items.put(
				itemId,
				items.getOrDefault(itemId, 0) + quantity
			);
		}

		return items;
	}

	private List<OwnedItem> snapshotOwned(
		final ItemContainer container,
		final SlayerLoadoutItem.Status status,
		final boolean equipmentContainer)
	{
		if (container == null || itemManager == null)
		{
			return Collections.emptyList();
		}

		final List<OwnedItem> items = new ArrayList<>();
		final Item[] containerItems = container.getItems();

		for (int index = 0;
			index < containerItems.length;
			index++)
		{
			final Item item = containerItems[index];

			if (!isRealOwnedItem(item, false))
			{
				continue;
			}

			/* Keep the exact worn/inventory variant and dose for the bank tag. */
			final int itemId = item.getId();
			final String name = getItemName(itemId);

			if (name.isEmpty())
			{
				continue;
			}

			items.add(
				new OwnedItem(
					itemId,
					name,
					item.getQuantity(),
					status,
					equipmentContainer ? index : -1,
					getEquipmentStats(itemId),
					getMatchNames(itemId, name)
				)
			);
		}

		return items;
	}

	private OwnedItem snapshotExtraQuiverAmmo(
		final int itemId,
		final int quantity)
	{
		if (itemId <= 0 || quantity <= 0 || itemManager == null)
		{
			return null;
		}

		final int normalizedItemId =
			SlayerQuiverAmmo.normalizeSeekingArrowItemId(itemId);
		String name = getItemName(normalizedItemId);
		if (name.isEmpty())
		{
			name = SlayerQuiverAmmo.seekingArrowMatchName(normalizedItemId);
		}
		if (name.isEmpty())
		{
			return null;
		}

		return new OwnedItem(
			normalizedItemId,
			name,
			quantity,
			SlayerLoadoutItem.Status.EQUIPPED,
			EXTRA_QUIVER_AMMO_SLOT,
			getEquipmentStats(normalizedItemId),
			getMatchNames(normalizedItemId, name)
		);
	}

	private List<OwnedItem> snapshotBank(
		final Map<Integer, Integer> cachedBankItems)
	{
		if (cachedBankItems == null
			|| cachedBankItems.isEmpty()
			|| itemManager == null)
		{
			return Collections.emptyList();
		}

		if (cachedBankSnapshotSource.equals(cachedBankItems))
		{
			return cachedBankOwnedItems;
		}

		final List<OwnedItem> items = new ArrayList<>();
		for (final Map.Entry<Integer, Integer> entry
			: cachedBankItems.entrySet())
		{
			if (entry.getKey() == null
				|| entry.getKey() <= 0
				|| entry.getValue() == null
				|| entry.getValue() <= 0)
			{
				continue;
			}

			/* Keep the exact bank variant and dose; aliases handle equivalence. */
			final int itemId = entry.getKey();
			final String name = getItemName(itemId);
			if (name.isEmpty())
			{
				continue;
			}

			items.add(
				new OwnedItem(
					itemId,
					name,
					entry.getValue(),
					SlayerLoadoutItem.Status.BANK,
					-1,
					getEquipmentStats(itemId),
					getMatchNames(itemId, name)
				)
			);
		}

		cachedBankSnapshotSource = Collections.unmodifiableMap(
			new LinkedHashMap<>(cachedBankItems)
		);
		cachedBankOwnedItems = Collections.unmodifiableList(items);
		return cachedBankOwnedItems;
	}

	/**
	 * Returns true only for an item stack the player actually owns.
	 *
	 * Bank placeholders are separate item definitions. ItemManager#canonicalize
	 * intentionally converts them to the normal item ID, so placeholder status
	 * must be checked before canonicalization. A non-positive quantity is also
	 * never treated as ownership.
	 */
	private boolean isRealOwnedItem(
		final Item item,
		final boolean requireDefinition)
	{
		if (item == null
			|| item.getId() <= 0
			|| item.getQuantity() <= 0)
		{
			return false;
		}

		try
		{
			final ItemComposition composition =
				itemManager.getItemComposition(item.getId());

			return composition == null
				? !requireDefinition
				: composition.getPlaceholderTemplateId() == -1;
		}
		catch (RuntimeException ignored)
		{
			/*
			 * Inventory and worn slots cannot contain placeholders, so a positive
			 * quantity remains useful there. Bank ownership is stricter: when the
			 * item definition cannot be verified, omit it rather than risk treating
			 * a placeholder as owned.
			 */
			return !requireDefinition;
		}
	}

	/**
	 * Builds the functional names an owned item may be matched by. The exact
	 * owned item ID and display name are retained for the generated bank tag,
	 * while RuneLite mappings provide aliases for charged, recoloured, skinned,
	 * and ornamented forms. Owning an ornament kit by itself does not qualify,
	 * because ItemMapping indexes the transformed item IDs rather than the
	 * tradeable component/kit ID.
	 */
	private Set<String> getMatchNames(
		final int itemId,
		final String displayName)
	{
		final Set<String> names = new LinkedHashSet<>();
		addMatchName(names, displayName);
		addSeekingQuiverMatchName(names, itemId);

		try
		{
			final int variationBase =
				ItemVariationMapping.map(itemId);
			addMatchName(names, getItemName(variationBase));
		}
		catch (RuntimeException ignored)
		{
			// A missing variation entry should not hide the real owned item.
		}

		try
		{
			final Collection<ItemMapping> mappings =
				ItemMapping.map(itemId);
			if (mappings != null)
			{
				for (final ItemMapping mapping : mappings)
				{
					addMatchName(
						names,
						getItemName(mapping.getTradeableItem())
					);
				}
			}
		}
		catch (RuntimeException ignored)
		{
			// ItemMapping is supplemental; the exact item remains usable.
		}

		return Collections.unmodifiableSet(names);
	}

	/**
	 * Seeking arrows are separate item families, but their metadata may briefly
	 * lag their live game IDs. Preserve their functional identity so the priority
	 * "Seeking first, ordinary second" can select the stack inside Dizana's
	 * quiver instead of a loose ordinary stack from inventory or bank.
	 */
	private static void addSeekingQuiverMatchName(
		final Set<String> names,
		final int itemId)
	{
		addMatchName(names, SlayerQuiverAmmo.seekingArrowMatchName(itemId));
	}

	private static void addMatchName(
		final Set<String> names,
		final String candidate)
	{
		final String normalized = normalize(candidate);
		if (!normalized.isEmpty() && !normalized.equals("null"))
		{
			names.add(normalized);
		}
	}

	private int safeCanonicalize(final int itemId)
	{
		try
		{
			return itemManager.canonicalize(itemId);
		}
		catch (RuntimeException ignored)
		{
			return itemId;
		}
	}

	private String getItemName(final int itemId)
	{
		try
		{
			final ItemComposition composition =
				itemManager.getItemComposition(itemId);

			if (composition == null
				|| composition.getName() == null
				|| composition.getName().equalsIgnoreCase("null"))
			{
				return "";
			}

			return composition.getName().trim();
		}
		catch (RuntimeException ignored)
		{
			return "";
		}
	}

	private ItemEquipmentStats getEquipmentStats(
		final int itemId)
	{
		try
		{
			final ItemStats stats =
				itemManager.getItemStats(itemId);

			if (stats == null || !stats.isEquipable())
			{
				return null;
			}

			return stats.getEquipment();
		}
		catch (RuntimeException ignored)
		{
			/*
			 * A newly added item can briefly lack local stats during a
			 * RuneLite data refresh. Name-based fallbacks remain available.
			 */
			return null;
		}
	}



	private static boolean isWildernessLoadout(
		final String location,
		final String restriction)
	{
		if (restriction.contains("non wilderness"))
		{
			return false;
		}

		return location.contains("wilderness")
			|| restriction.contains("wilderness allowed")
			|| restriction.contains("krystilia");
	}

	private static List<OwnedItem> filterRecommendationItems(
		final List<OwnedItem> allOwned,
		final boolean wildernessLoadout)
	{
		if (wildernessLoadout)
		{
			return allOwned;
		}

		final List<OwnedItem> filtered = new ArrayList<>();
		for (final OwnedItem item : allOwned)
		{
			if (!item.normalizedName.contains("blighted"))
			{
				filtered.add(item);
			}
		}
		return filtered;
	}

	private static Set<String> namesOf(
		final List<OwnedItem> items)
	{
		final Set<String> names = new HashSet<>();

		for (final OwnedItem item : items)
		{
			names.addAll(item.normalizedNames);
		}

		return names;
	}

	private static String buildLayoutTitle(
		final String taskName,
		final String location)
	{
		final String safeTask =
			taskName == null || taskName.trim().isEmpty()
				? "Recommended setup"
				: taskName.trim();

		if (location == null || location.trim().isEmpty())
		{
			return safeTask;
		}

		return safeTask + " \u2022 " + location.trim();
	}


	private static OwnedItem selectRecommendedWeapon(
		final String taskName,
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<Requirement> requirements,
		final List<OwnedItem> equipped,
		final List<OwnedItem> allOwned)
	{
		final Requirement weaponRequirement =
			requirementForSlot(
				requirements,
				EquipmentInventorySlot.WEAPON
			);
		final Requirement shieldRequirement =
			requirementForSlot(
				requirements,
				EquipmentInventorySlot.SHIELD
			);
		final String[] weaponPriorities = weaponProgression(
			taskName,
			strategy,
			style
		).toArray(new String[0]);

		return selectEquipmentOwned(
			EquipmentInventorySlot.WEAPON,
			weaponRequirement,
			strategy,
			style,
			equipped,
			allOwned,
			shieldRequirement != null,
			weaponPriorities
		);
	}

	private static List<String> weaponProgression(
		final String taskName,
		final SlayerTaskStrategy strategy,
		final CombatStyle style)
	{
		final List<String> progression = new ArrayList<>();
		if (strategy != null)
		{
			progression.addAll(strategy.getWeaponPrioritiesForPolicy());
			if (!strategy.isStrictWeaponProfile())
			{
				for (final String fallback
					: SlayerEquipmentAuditCatalog.weaponProgression(strategy))
				{
					if (!progression.contains(fallback))
					{
						progression.add(fallback);
					}
				}
			}
		}
		if (progression.isEmpty())
		{
			Collections.addAll(progression, weaponFragments(style));
		}
		progression.removeIf(weapon ->
			!SlayerTargetFootprintCatalog.allowsWeapon(taskName, weapon));
		return progression;
	}

	static List<String> weaponProgressionForRegression(
		final String taskName,
		final SlayerTaskStrategy strategy)
	{
		return Collections.unmodifiableList(weaponProgression(
			taskName,
			strategy,
			combatStyle(strategy)
		));
	}

	static List<String> weaponProgressionForRegression(
		final SlayerTaskStrategy strategy)
	{
		return weaponProgressionForRegression("", strategy);
	}

	private static List<SlayerLoadoutItem>
		buildEquipmentLayout(
			final String task,
			final SlayerTaskStrategy strategy,
			final CombatStyle style,
			final List<Requirement> requirements,
			final List<OwnedItem> equipped,
			final List<OwnedItem> allOwned,
			final boolean bankScanned,
			final OwnedItem selectedWeapon)
	{
		if (normalize(task).equals("tzkal zuk"))
		{
			return buildInfernoEquipmentLayout(
				strategy,
				style,
				equipped,
				allOwned,
				bankScanned,
				selectedWeapon
			);
		}
		if (normalize(task).equals("blue dragon")
			|| normalize(task).equals("blue dragons"))
		{
			if (style == CombatStyle.MAGIC)
			{
				return buildReviewedBlueDragonMagicLayout(
					strategy, allOwned, bankScanned, selectedWeapon
				);
			}
			return buildReviewedDragonEquipmentLayout(
				false, strategy, style, requirements, equipped, allOwned,
				bankScanned, selectedWeapon
			);
		}
		if (normalize(task).equals("vorkath"))
		{
			return buildReviewedDragonEquipmentLayout(
				true, strategy, style, requirements, equipped, allOwned,
				bankScanned, selectedWeapon
			);
		}

		final List<SlayerLoadoutItem> layout =
			new ArrayList<>();

		final Requirement headRequirement =
			requirementForSlot(
				requirements,
				EquipmentInventorySlot.HEAD
			);
		final Requirement weaponRequirement =
			requirementForSlot(
				requirements,
				EquipmentInventorySlot.WEAPON
			);
		final Requirement shieldRequirement =
			requirementForSlot(
				requirements,
				EquipmentInventorySlot.SHIELD
			);

		layout.add(auditedEquipmentItem(
			task,
			EquipmentInventorySlot.HEAD,
			headRequirement,
			"Slayer helmet / black mask",
			strategy,
			style,
			equipped,
			allOwned,
			bankScanned,
			false,
			selectedWeapon
		));

		layout.add(auditedCapeEquipmentItem(
			task, strategy, style, allOwned, bankScanned, selectedWeapon
		));

		layout.add(auditedEquipmentItem(
			task,
			EquipmentInventorySlot.AMULET,
			requirementForSlot(
				requirements,
				EquipmentInventorySlot.AMULET
			),
			style == CombatStyle.MAGIC
				? "Magic amulet"
				: style == CombatStyle.RANGED
					? "Ranged amulet"
					: "Strength amulet",
			strategy,
			style,
			equipped,
			allOwned,
			bankScanned,
			false,
			selectedWeapon
		));



		layout.add(normalize(task).equals("araxxor")
			? buildAraxxorSwitchAmmoItem(
				style, selectedWeapon, strategy, allOwned, bankScanned
			)
			: buildAmmoItem(
				style, selectedWeapon, strategy, allOwned, bankScanned
			));

		layout.add(toEquipmentItem(
			selectedWeapon,
			weaponRequirement == null
				? strategy != null
					? strategy.getMethod()
					: style == CombatStyle.MAGIC
						? "Magic weapon"
						: style == CombatStyle.RANGED
							? "Ranged weapon"
							: "Melee weapon"
				: weaponRequirement.displayName,
			bankScanned
		));

		layout.add(auditedEquipmentItem(
			task,
			EquipmentInventorySlot.BODY,
			null,
			style == CombatStyle.MAGIC
				? "Magic body"
				: style == CombatStyle.RANGED
					? "Ranged body"
					: "Melee body",
			strategy,
			style,
			equipped,
			allOwned,
			bankScanned,
			false,
			selectedWeapon
		));

		if (selectedWeapon == null)
		{
			layout.add(missingItem(
				"No off-hand selected until a compatible weapon is found",
				bankScanned
			));
		}
		else if (selectedWeapon.isTwoHanded()
			&& shieldRequirement == null)
		{
			layout.add(new SlayerLoadoutItem(
				"Two-handed weapon — no off-hand",
				-1,
				1,
				SlayerLoadoutItem.Status.EQUIPPED
			));
		}
		else
		{
			layout.add(auditedEquipmentItem(
				task,
				EquipmentInventorySlot.SHIELD,
				shieldRequirement,
				style == CombatStyle.MAGIC
					? "Magic off-hand"
					: style == CombatStyle.RANGED
						? "Ranged off-hand"
						: "Defender / shield",
				strategy,
				style,
				equipped,
				allOwned,
				bankScanned,
				false,
				selectedWeapon
			));
		}

		layout.add(auditedEquipmentItem(
			task,
			EquipmentInventorySlot.LEGS,
			null,
			style == CombatStyle.MAGIC
				? "Magic legs"
				: style == CombatStyle.RANGED
					? "Ranged legs"
					: "Melee legs",
			strategy,
			style,
			equipped,
			allOwned,
			bankScanned,
			false,
			selectedWeapon
		));

		layout.add(auditedEquipmentItem(
			task,
			EquipmentInventorySlot.GLOVES,
			requirementForSlot(
				requirements,
				EquipmentInventorySlot.GLOVES
			),
			"Combat gloves",
			strategy,
			style,
			equipped,
			allOwned,
			bankScanned,
			false,
			selectedWeapon
		));

		layout.add(auditedEquipmentItem(
			task,
			EquipmentInventorySlot.BOOTS,
			requirementForSlot(
				requirements,
				EquipmentInventorySlot.BOOTS
			),
			style == CombatStyle.MAGIC
				? "Magic boots"
				: style == CombatStyle.RANGED
					? "Ranged boots"
					: "Melee boots",
			strategy,
			style,
			equipped,
			allOwned,
			bankScanned,
			false,
			selectedWeapon
		));

		layout.add(auditedEquipmentItem(
			task,
			EquipmentInventorySlot.RING,
			null,
			style == CombatStyle.MAGIC
				? "Magic ring"
				: style == CombatStyle.RANGED
					? "Ranged ring"
					: "Melee ring",
			strategy,
			style,
			equipped,
			allOwned,
			bankScanned,
			false,
			selectedWeapon
		));

		/*
		 * Dizana's quiver adds a genuine second ammunition equipment state.
		 * Keep RuneLite's eleven normal equipment entries unchanged and append
		 * this as entry 11 (the twelfth entry). The Bank Tag renderer maps that
		 * terminal entry to the dedicated cell above the ordinary ammo slot.
		 */
		final SlayerLoadoutItem extraQuiverAmmo = buildDizanaExtraAmmoItem(
			false,
			style,
			selectedWeapon,
			strategy,
			allOwned,
			bankScanned
		);
		if (extraQuiverAmmo != null)
		{
			layout.add(extraQuiverAmmo);
		}

		return layout;
	}

	/**
	 * Cross-slot reviewed equipment for regular Blue dragons and Vorkath.
	 * Generic per-slot scoring can create partial Void sets, combine Slayer helm
	 * with Salve even though their bonuses do not stack, or select the wrong
	 * dragonfire/off-hand package.
	 */
	private static List<SlayerLoadoutItem> buildReviewedDragonEquipmentLayout(
		final boolean vorkath,
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<Requirement> requirements,
		final List<OwnedItem> equipped,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final OwnedItem selectedWeapon)
	{
		final List<SlayerLoadoutItem> layout = new ArrayList<>();
		final String encounterTask = vorkath ? "Vorkath" : "Blue dragons";
		final boolean ranged = style == CombatStyle.RANGED;
		final boolean completeMasori = ranged
			&& containsAnyOwned(allOwned, "masori mask")
			&& containsAnyOwned(allOwned, "masori body")
			&& containsAnyOwned(allOwned, "masori chaps");
		final boolean completeEliteVoid = vorkath && ranged
			&& containsAnyOwned(allOwned, "void ranger helm")
			&& containsAnyOwned(allOwned, "elite void top")
			&& containsAnyOwned(allOwned, "elite void robe")
			&& containsAnyOwned(allOwned, "void knight gloves");
		final boolean completeRegularVoid = vorkath && ranged
			&& containsAnyOwned(allOwned, "void ranger helm")
			&& containsAnyOwned(allOwned, "void knight top")
			&& containsAnyOwned(allOwned, "void knight robe")
			&& containsAnyOwned(allOwned, "void knight gloves");
		final OwnedItem vorkathSlayerHead = vorkath
			? findPreferredVorkathSlayerHead(allOwned, ranged) : null;
		final boolean useVorkathSlayerHead = vorkathSlayerHead != null;
		final boolean useEliteVoid = !useVorkathSlayerHead
			&& !completeMasori && completeEliteVoid;
		final boolean useRegularVoid = !useVorkathSlayerHead
			&& !completeMasori && !useEliteVoid && completeRegularVoid;

		layout.add(toEquipmentItem(
			useVorkathSlayerHead
				? vorkathSlayerHead
				: findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.HEAD,
				false,
				vorkath
					? ranged
						? useEliteVoid || useRegularVoid
							? asList("void ranger helm")
							: asList(
								"masori mask", "slayer helmet i", "crystal helm",
								"serpentine helm", "blessed coif"
							)
						: asList(
							"torva full helm", "slayer helmet i", "neitiznot faceguard",
							"serpentine helm", "oathplate helm", "justiciar faceguard",
							"blood moon helm", "helm of neitiznot", "barrows helm"
						)
					: ranged
						? asList(
							"slayer helmet i", "black mask i",
							"slayer helmet", "black mask",
							"masori mask f", "masori mask", "armadyl helmet", "blessed coif",
							"karil s coif", "eclipse moon helm",
							"black d hide coif", "red d hide coif",
							"blue d hide coif", "green d hide coif",
							"snakeskin bandana"
						)
						: asList(
							"slayer helmet i", "black mask i",
							"slayer helmet", "black mask"
						)
			),
			vorkath ? "Slayer helmet / reviewed Vorkath helm"
				: "Imbued Slayer helmet / black mask",
			bankScanned
		));

		layout.add(vorkath && ranged
			? toEquipmentItem(
				findPreferredDizanaCapeOrFallback(allOwned),
				"Dizana's quiver / Ava's assembler",
				bankScanned
			)
			: auditedCapeEquipmentItem(
				encounterTask, strategy, style, allOwned, bankScanned,
				selectedWeapon
			));

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.AMULET,
				false,
				vorkath
					? useVorkathSlayerHead
						? ranged
							? asList("necklace of anguish", "amulet of fury")
							: asList("amulet of rancour", "amulet of torture", "amulet of fury")
						: ranged
							? asList("salve amulet ei", "salve amulet i")
							: asList("salve amulet ei", "salve amulet e")
					: ranged
						? dragonEquipmentProgression(
							encounterTask, strategy, EquipmentInventorySlot.AMULET,
							selectedWeapon, asList("necklace of anguish", "amulet of fury")
						)
						: dragonEquipmentProgression(
							encounterTask, strategy, EquipmentInventorySlot.AMULET,
							selectedWeapon, asList(
								"amulet of rancour", "amulet of torture", "amulet of fury"
							)
						)
			),
			vorkath
				? useVorkathSlayerHead
					? ranged ? "Necklace of anguish" : "Melee damage amulet"
					: "Salve amulet (ei)"
				: ranged ? "Necklace of anguish" : "Melee amulet",
			bankScanned
		));

		layout.add(buildAmmoItem(
			style, selectedWeapon, strategy, allOwned, bankScanned
		));
		layout.add(toEquipmentItem(
			selectedWeapon,
			vorkath ? "Dragon hunter crossbow / Dragon hunter lance"
				: "Dragon hunter crossbow / one-handed crossbow",
			bankScanned
		));

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.BODY,
				false,
				ranged
					? useEliteVoid || useRegularVoid
						? asList(useEliteVoid ? "elite void top" : "void knight top")
						: dragonEquipmentProgression(
							encounterTask, strategy, EquipmentInventorySlot.BODY,
							selectedWeapon, Collections.emptyList()
						)
					: dragonEquipmentProgression(
						encounterTask, strategy, EquipmentInventorySlot.BODY,
						selectedWeapon, asList(
							"torva platebody", "oathplate chest", "bandos chestplate",
							"justiciar chestguard", "blood moon chestplate",
							"barrows platebody", "fighter torso", "obsidian platebody"
						)
					)
			),
			ranged ? (useEliteVoid ? "Elite void top"
				: useRegularVoid ? "Void knight top" : "Ranged body") : "Melee body",
			bankScanned
		));

		if (ranged)
		{
			layout.add(toEquipmentItem(
				findPreferredEquipment(
					allOwned,
					EquipmentInventorySlot.SHIELD,
					false,
					asList(
						"dragonfire ward", "anti dragon shield",
						"dragonfire shield", "ancient wyvern shield"
					)
				),
				"Dragonfire ward / Anti-dragon shield",
				bankScanned
			));
		}
		else if (selectedWeapon != null && selectedWeapon.isTwoHanded())
		{
			layout.add(new SlayerLoadoutItem(
				"Two-handed weapon — no defender",
				-1,
				1,
				SlayerLoadoutItem.Status.EQUIPPED
			));
		}
		else
		{
			/*
			 * The reviewed regular-Blue melee method always carries full potion-only
			 * protection. Do not make the defender recommendation disappear merely
			 * because the potion itself is currently missing from the bank scan; the
			 * missing potion and defender must remain visible as one complete package.
			 */
			final boolean blueDragonDefender = !vorkath;
			layout.add(toEquipmentItem(
				findPreferredEquipment(
					allOwned,
					EquipmentInventorySlot.SHIELD,
					false,
					vorkath || blueDragonDefender
						? asList(
							"avernic defender", "dragon defender",
							"rune defender", "adamant defender", "mithril defender",
							"black defender", "steel defender", "iron defender",
							"bronze defender", "toktz ket xil", "rune kiteshield",
							"dragonfire shield", "anti dragon shield"
						)
						: asList("dragonfire shield", "anti dragon shield", "dragonfire ward")
				),
				vorkath || blueDragonDefender
					? "Best owned defender" : "Dragonfire shield protection",
				bankScanned
			));
		}

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.LEGS,
				false,
				ranged
					? useEliteVoid || useRegularVoid
						? asList(useEliteVoid ? "elite void robe" : "void knight robe")
						: dragonEquipmentProgression(
							encounterTask, strategy, EquipmentInventorySlot.LEGS,
							selectedWeapon, Collections.emptyList()
						)
					: dragonEquipmentProgression(
						encounterTask, strategy, EquipmentInventorySlot.LEGS,
						selectedWeapon, asList(
							"torva platelegs", "oathplate legs", "bandos tassets",
							"justiciar legguards", "blood moon tassets",
							"barrows platelegs", "obsidian platelegs"
						)
					)
			),
			ranged ? (useEliteVoid ? "Elite void robe"
				: useRegularVoid ? "Void knight robe" : "Ranged legs") : "Melee legs",
			bankScanned
		));

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.GLOVES,
				false,
				ranged
					? useEliteVoid || useRegularVoid
						? asList("void knight gloves")
						: dragonEquipmentProgression(
							encounterTask, strategy, EquipmentInventorySlot.GLOVES,
							selectedWeapon, Collections.emptyList()
						)
					: dragonEquipmentProgression(
						encounterTask, strategy, EquipmentInventorySlot.GLOVES,
						selectedWeapon, asList(
							"ferocious gloves", "barrows gloves", "dragon gloves",
							"rune gloves", "granite gloves"
						)
					)
			),
			ranged ? (useEliteVoid || useRegularVoid
				? "Void knight gloves" : "Ranged gloves") : "Melee gloves",
			bankScanned
		));

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.BOOTS,
				false,
				dragonEquipmentProgression(
					encounterTask, strategy, EquipmentInventorySlot.BOOTS,
					selectedWeapon, Collections.emptyList()
				)
			),
			ranged ? "Ranged boots" : "Melee boots",
			bankScanned
		));

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.RING,
				false,
				dragonEquipmentProgression(
					encounterTask, strategy, EquipmentInventorySlot.RING,
					selectedWeapon, dragonRingExtras(vorkath)
				)
			),
			ranged ? "Ranged ring" : "Melee ring",
			bankScanned
		));

		final SlayerLoadoutItem extraQuiverAmmo = buildDizanaExtraAmmoItem(
			false, style, selectedWeapon, strategy, allOwned, bankScanned
		);
		if (extraQuiverAmmo != null)
		{
			layout.add(extraQuiverAmmo);
		}
		return layout;
	}

	/*
	 * Lightbearer has no offensive stats and is only a reviewed dragon-task ring
	 * when the encounter also carries a meaningful special-attack package.
	 * Vorkath's inventory rules always author that weapon switch; ordinary Blue
	 * dragons do not, so their ring slot must remain on the style's damage
	 * progression instead of selecting an unpaired Lightbearer.
	 */
	private static List<String> dragonRingExtras(final boolean vorkath)
	{
		return vorkath
			? Collections.singletonList("lightbearer")
			: Collections.emptyList();
	}

	static boolean regularBlueDragonsAllowLightbearerForRegression()
	{
		return dragonRingExtras(false).contains("lightbearer");
	}

	static boolean vorkathAllowsLightbearerForRegression()
	{
		return dragonRingExtras(true).contains("lightbearer");
	}

	/** Complete Water-spell safespot package for regular Blue dragons. */
	private static List<SlayerLoadoutItem> buildReviewedBlueDragonMagicLayout(
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final OwnedItem selectedWeapon)
	{
		final List<SlayerLoadoutItem> layout = new ArrayList<>();
		layout.add(toEquipmentItem(findPreferredEquipment(
			allOwned, EquipmentInventorySlot.HEAD, false,
			asList("slayer helmet i", "black mask i")
		), "Imbued Slayer helmet / black mask", bankScanned));
		layout.add(auditedCapeEquipmentItem(
			"Blue dragons", strategy, CombatStyle.MAGIC,
			allOwned, bankScanned, selectedWeapon
		));
		layout.add(toEquipmentItem(findPreferredEquipment(
			allOwned, EquipmentInventorySlot.AMULET, false,
			dragonEquipmentProgression(
				"Blue dragons", strategy, EquipmentInventorySlot.AMULET,
				selectedWeapon, Collections.emptyList()
			)
		), "Magic damage amulet", bankScanned));
		layout.add(buildPassiveAmmoItem(
			selectedWeapon, strategy, allOwned, bankScanned
		));
		layout.add(toEquipmentItem(
			selectedWeapon, "Dragon hunter wand / Water staff", bankScanned
		));
		layout.add(toEquipmentItem(findPreferredEquipment(
			allOwned, EquipmentInventorySlot.BODY, false,
			dragonEquipmentProgression(
				"Blue dragons", strategy, EquipmentInventorySlot.BODY,
				selectedWeapon, Collections.emptyList()
			)
		), "Magic body", bankScanned));
		layout.add(toEquipmentItem(findPreferredEquipment(
			allOwned, EquipmentInventorySlot.SHIELD, false,
			asList("anti dragon shield", "dragonfire shield", "ancient wyvern shield")
		), "Anti-dragon shield", bankScanned));
		for (final EquipmentInventorySlot slot : new EquipmentInventorySlot[]{
			EquipmentInventorySlot.LEGS,
			EquipmentInventorySlot.GLOVES,
			EquipmentInventorySlot.BOOTS,
			EquipmentInventorySlot.RING
		})
		{
			layout.add(toEquipmentItem(findPreferredEquipment(
				allOwned, slot, false,
				dragonEquipmentProgression(
					"Blue dragons", strategy, slot,
					selectedWeapon, Collections.emptyList()
				)
			), "Magic " + slot.name().toLowerCase(Locale.ENGLISH), bankScanned));
		}
		return layout;
	}

	/**
	 * Keeps encounter-specific set pieces first, then supplies the complete
	 * global progression so a lower-tier owned item still fills the bank tag.
	 */
	private static List<String> dragonEquipmentProgression(
		final String task,
		final SlayerTaskStrategy strategy,
		final EquipmentInventorySlot slot,
		final OwnedItem selectedWeapon,
		final List<String> encounterPriorities)
	{
		final Set<String> merged = new LinkedHashSet<>();
		if (encounterPriorities != null)
		{
			merged.addAll(encounterPriorities);
		}
		merged.addAll(SlayerEquipmentAuditCatalog.priorities(
			task,
			strategy,
			slot,
			selectedWeapon == null ? "" : selectedWeapon.normalizedName
		));
		return new ArrayList<>(merged);
	}


	/**
	 * Dedicated on-task Inferno equipment resolver.
	 *
	 * Generic ranged stat scoring is intentionally bypassed here.  Inferno has
	 * cross-slot synergies that raw per-slot scores cannot express:
	 *  - the imbued Slayer helm is mandatory for the task's ranged/magic bonus;
	 *  - Bowfa must remain paired with crystal body + crystal legs;
	 *  - on-task speed profiles prefer Venator ring, while the completion-first
	 *    profile prefers Ring of suffering (ri); Lightbearer remains the reviewed
	 *    secondary option for extra blowpipe special attacks.
	 */
	private static List<SlayerLoadoutItem> buildInfernoEquipmentLayout(
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<OwnedItem> equipped,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final OwnedItem selectedWeapon)
	{
		final List<SlayerLoadoutItem> layout = new ArrayList<>();
		final boolean bowfa = matchesWeaponName(
			selectedWeapon,
			"",
			"bow of faerdhinen"
		);

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.HEAD,
				false,
				asList(
					"slayer helmet i",
					"black mask 10 i",
					"black mask 9 i",
					"black mask 8 i",
					"black mask 7 i",
					"black mask 6 i",
					"black mask 5 i",
					"black mask 4 i",
					"black mask 3 i",
					"black mask 2 i",
					"black mask 1 i",
					"black mask i"
				)
			),
			"Imbued Slayer helmet / black mask",
			bankScanned
		));

		layout.add(toEquipmentItem(
			findPreferredDizanaCapeOrFallback(allOwned),
			"Dizana's quiver / Ava's assembler",
			bankScanned
		));

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.AMULET,
				false,
				asList(
					"necklace of anguish",
					"amulet of fury"
				)
			),
			"Necklace of anguish",
			bankScanned
		));

		layout.add(buildInfernoAmmoItem(
			selectedWeapon,
			strategy,
			allOwned,
			bankScanned
		));

		layout.add(toEquipmentItem(
			selectedWeapon,
			"Twisted bow / Bow of faerdhinen",
			bankScanned
		));

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.BODY,
				false,
				bowfa
					? asList(
						"crystal body",
						"masori body",
						"armadyl chestplate",
						"karil s leathertop",
						"d hide body"
					)
					: asList(
						"masori body",
						"crystal body",
						"armadyl chestplate",
						"karil s leathertop",
						"d hide body"
					)
			),
			bowfa ? "Crystal body" : "Masori body",
			bankScanned
		));

		if (selectedWeapon == null)
		{
			layout.add(missingItem(
				"No off-hand selected until a compatible weapon is found",
				bankScanned
			));
		}
		else if (selectedWeapon.isTwoHanded())
		{
			layout.add(new SlayerLoadoutItem(
				"Two-handed weapon — no off-hand",
				-1,
				1,
				SlayerLoadoutItem.Status.EQUIPPED
			));
		}
		else
		{
			layout.add(toEquipmentItem(
				findPreferredEquipment(
					allOwned,
					EquipmentInventorySlot.SHIELD,
					false,
					asList(
						"twisted buckler",
						"dragonfire ward",
						"book of law"
					)
				),
				"Twisted buckler",
				bankScanned
			));
		}

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.LEGS,
				false,
				bowfa
					? asList(
						"crystal legs",
						"masori chaps",
						"armadyl chainskirt",
						"karil s leatherskirt",
						"d hide chaps"
					)
					: asList(
						"masori chaps",
						"crystal legs",
						"armadyl chainskirt",
						"karil s leatherskirt",
						"d hide chaps"
					)
			),
			bowfa ? "Crystal legs" : "Masori chaps",
			bankScanned
		));

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.GLOVES,
				false,
				asList(
					"zaryte vambraces",
					"barrows gloves",
					"blessed vambraces",
					"combat bracelet"
				)
			),
			"Zaryte vambraces / Barrows gloves",
			bankScanned
		));

		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.BOOTS,
				false,
				asList(
					"avernic treads",
					"echo boots",
					"pegasian boots",
					"ranger boots",
					"blessed boots"
				)
			),
			"Avernic treads / Pegasian boots",
			bankScanned
		));

		final boolean completionFirst = strategy != null
			&& normalize(strategy.getMethod()).contains("not afk");
		layout.add(toEquipmentItem(
			findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.RING,
				false,
				completionFirst
					? asList(
						"ring of suffering i",
						"lightbearer",
						"ring of the gods i",
						"venator ring",
						"archers ring i",
						"archers ring"
					)
					: asList(
						"venator ring",
						"lightbearer",
						"ring of suffering i",
						"ring of the gods i",
						"archers ring i",
						"archers ring"
					)
			),
			completionFirst ? "Ring of suffering (i)" : "Venator ring",
			bankScanned
		));

		final SlayerLoadoutItem extraQuiverAmmo = buildDizanaExtraAmmoItem(
			true,
			CombatStyle.RANGED,
			selectedWeapon,
			strategy,
			allOwned,
			bankScanned
		);
		if (extraQuiverAmmo != null)
		{
			layout.add(extraQuiverAmmo);
		}

		return layout;
	}

	private static SlayerLoadoutItem buildInfernoAmmoItem(
		final OwnedItem selectedWeapon,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		return buildInfernoAmmoItem(
			selectedWeapon,
			strategy,
			allOwned,
			bankScanned,
			true
		);
	}

	private static SlayerLoadoutItem buildInfernoAmmoItem(
		final OwnedItem selectedWeapon,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final boolean reserveNormalAmmoForDizana)
	{
		final String fallbackName = strategy != null
			&& !strategy.getWeaponPrioritiesForPolicy().isEmpty()
				? normalize(strategy.getWeaponPrioritiesForPolicy().get(0))
				: "";

		if (reserveNormalAmmoForDizana
			&& hasUsableDizanaCape(allOwned)
			&& canUseDizanaExtraAmmo(selectedWeapon, fallbackName))
		{
			return buildPassiveAmmoItem(
				selectedWeapon,
				strategy,
				allOwned,
				bankScanned
			);
		}

		if (matchesWeaponName(
			selectedWeapon,
			fallbackName,
			"bow of faerdhinen"
		) || matchesWeaponName(
			selectedWeapon,
			fallbackName,
			"toxic blowpipe"
		))
		{
			return buildPassiveAmmoItem(
				selectedWeapon,
				strategy,
				allOwned,
				bankScanned
			);
		}

		if (matchesWeaponName(
			selectedWeapon,
			fallbackName,
			"crossbow"
		))
		{
			return recommendedAmmunitionItem(
				!reserveNormalAmmoForDizana,
				"Ruby dragon bolts (e)",
				allOwned,
				bankScanned,
				"ruby dragon bolts e",
				"ruby bolts e",
				"diamond dragon bolts e",
				"diamond bolts e"
			);
		}

		return bestOwnedInfernoArrow(
			!reserveNormalAmmoForDizana,
			allOwned,
			bankScanned
		);
	}

	private static SlayerLoadoutItem buildAmmoItem(
		final CombatStyle style,
		final OwnedItem selectedWeapon,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		return buildAmmoItem(
			style,
			selectedWeapon,
			strategy,
			allOwned,
			bankScanned,
			true
		);
	}

	/**
	 * Araxxor's ranged weapon is a safety switch for clearing araxytes, not a
	 * second inventory loadout. Put that switch's ammunition in the normal ammo
	 * equipment slot, replacing a blessing, so ammunition never consumes one of
	 * the 28 inventory cells. If the best owned safe weapon is a halberd or other
	 * melee fallback, retain the normal passive ammo-slot recommendation.
	 */
	private static SlayerLoadoutItem buildAraxxorSwitchAmmoItem(
		final CombatStyle style,
		final OwnedItem selectedWeapon,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		final OwnedItem safeWeapon = findPreferredInventoryItem(
			allOwned,
			araxxorSafeWeaponAlternatives(),
			true
		);
		final List<String> ammunition = araxxorSafeAmmunitionAlternatives(
			safeWeapon == null ? "" : safeWeapon.normalizedName
		);
		if (ammunition.isEmpty())
		{
			return buildAmmoItem(
				style, selectedWeapon, strategy, allOwned, bankScanned
			);
		}

		return strictAmmoItem(
			"Safe araxyte ammunition",
			allOwned,
			bankScanned,
			ammunition.toArray(new String[0])
		);
	}

	private static SlayerLoadoutItem buildAmmoItem(
		final CombatStyle style,
		final OwnedItem selectedWeapon,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final boolean reserveNormalAmmoForDizana)
	{
		if (style != CombatStyle.RANGED)
		{
			return buildPassiveAmmoItem(
				selectedWeapon,
				strategy,
				allOwned,
				bankScanned
			);
		}

		final String weaponName = selectedWeapon != null
			? selectedWeapon.normalizedName
			: strategy != null
				&& !strategy.getWeaponPrioritiesForPolicy().isEmpty()
				? normalize(
					strategy.getWeaponPrioritiesForPolicy().get(0)
				)
				: "";

		if (reserveNormalAmmoForDizana
			&& hasUsableDizanaCape(allOwned)
			&& canUseDizanaExtraAmmo(selectedWeapon, weaponName))
		{
			return buildPassiveAmmoItem(
				selectedWeapon,
				strategy,
				allOwned,
				bankScanned
			);
		}

		if (matchesWeaponName(selectedWeapon, weaponName, "blowpipe")
			|| matchesWeaponName(selectedWeapon, weaponName, "bow of faerdhinen")
			|| matchesWeaponName(selectedWeapon, weaponName, "crystal bow")
			|| matchesWeaponName(selectedWeapon, weaponName, "webweaver bow")
			|| matchesWeaponName(selectedWeapon, weaponName, "craw s bow")
			|| matchesWeaponName(selectedWeapon, weaponName, "chinchompa"))
		{
			return buildPassiveAmmoItem(
				selectedWeapon,
				strategy,
				allOwned,
				bankScanned
			);
		}

		if (matchesWeaponName(selectedWeapon, weaponName, "ballista"))
		{
			return strictAmmoItem(
				"Javelins",
				allOwned,
				bankScanned,
				"dragon javelin",
				"amethyst javelin",
				"rune javelin"
			);
		}

		if (matchesWeaponName(selectedWeapon, weaponName, "atlatl"))
		{
			return strictAmmoItem(
				"Atlatl darts",
				allOwned,
				bankScanned,
				"atlatl dart"
			);
		}

		if (matchesWeaponName(selectedWeapon, weaponName, "hunters sunlight crossbow"))
		{
			return recommendedAmmunitionItem(
				!reserveNormalAmmoForDizana,
				"Antler bolts",
				allOwned,
				bankScanned,
				"moonlight antler bolts",
				"sunlight antler bolts"
			);
		}

		if (matchesWeaponName(selectedWeapon, weaponName, "crossbow"))
		{
			final String method = strategy == null
				? ""
				: normalize(strategy.getMethod() + " " + strategy.getRationale());
			if (method.contains("dragonstone bolt"))
			{
				return recommendedAmmunitionItem(
					!reserveNormalAmmoForDizana,
					"Enchanted dragonstone bolts",
					allOwned,
					bankScanned,
					"dragonstone dragon bolts e",
					"dragonstone bolts e",
					"ruby dragon bolts e",
					"diamond dragon bolts e",
					"dragon bolts",
					"runite bolts",
					"broad bolts"
				);
			}
			return recommendedAmmunitionItem(
				!reserveNormalAmmoForDizana,
				"Compatible bolts",
				allOwned,
				bankScanned,
				"ruby dragon bolts e",
				"diamond dragon bolts e",
				"dragon bolts",
				"amethyst broad bolts",
				"runite bolts",
				"broad bolts"
			);
		}

		/*
		 * Bows, including the Scorching bow, use arrows. This strict lookup
		 * intentionally refuses to fall back to unrelated high-strength ammo.
		 */
		return bestOwnedStandardArrow(
			!reserveNormalAmmoForDizana,
			selectedWeapon,
			weaponName,
			strategy,
			allOwned,
			bankScanned
		);
	}

	private static SlayerLoadoutItem bestOwnedStandardArrow(
		final boolean includeExtraQuiverSlot,
		final OwnedItem selectedWeapon,
		final String fallbackWeaponName,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		final String[] priorities = standardArrowPriorityForPolicy(
			strategy,
			supportsDragonArrows(selectedWeapon, fallbackWeaponName)
		);
		return recommendedAmmunitionItem(
			includeExtraQuiverSlot,
			strategy != null
				&& strategy.getCostPolicy()
					== SlayerTaskStrategy.CostPolicy.EFFICIENT
					? "Cost-efficient owned arrows"
					: "Best owned arrows",
			allOwned,
			bankScanned,
			priorities
		);
	}

	private static String[] standardArrowPriorityForPolicy(
		final SlayerTaskStrategy strategy,
		final boolean dragonCompatible)
	{
		if (strategy != null
			&& strategy.getCostPolicy()
				== SlayerTaskStrategy.CostPolicy.EFFICIENT)
		{
			return removeDragonArrowsIfUnsupported(
				strategy.isBoss()
					? EFFICIENT_BOSS_ARROW_PRIORITY
					: EFFICIENT_REGULAR_ARROW_PRIORITY,
				dragonCompatible
			);
		}
		return dragonCompatible
			? STRONGEST_STANDARD_ARROW_PRIORITY
			: STRONGEST_NON_DRAGON_ARROW_PRIORITY;
	}

	private static String[] removeDragonArrowsIfUnsupported(
		final String[] priorities,
		final boolean dragonCompatible)
	{
		if (dragonCompatible)
		{
			return priorities;
		}
		final List<String> compatible = new ArrayList<>();
		for (final String priority : priorities)
		{
			if (!normalize(priority).contains("dragon arrow"))
			{
				compatible.add(priority);
			}
		}
		return compatible.toArray(new String[0]);
	}

	private static boolean supportsDragonArrows(
		final OwnedItem selectedWeapon,
		final String fallbackWeaponName)
	{
		final String weaponName = selectedWeapon == null
			? normalize(fallbackWeaponName)
			: selectedWeapon.normalizedName;
		return weaponName.contains("twisted bow")
			|| weaponName.contains("venator bow")
			|| weaponName.contains("scorching bow")
			|| weaponName.contains("dark bow")
			|| weaponName.contains("3rd age bow");
	}

	static List<String> standardArrowPriorityForRegression(
		final SlayerTaskStrategy.CostPolicy policy,
		final boolean boss,
		final boolean dragonCompatible)
	{
		final SlayerTaskStrategy strategy = SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.RANGED,
			"Arrow policy regression"
		).costPolicy(policy).boss(boss).build();
		return Arrays.asList(standardArrowPriorityForPolicy(
			strategy,
			dragonCompatible
		));
	}

	private static SlayerLoadoutItem bestOwnedInfernoArrow(
		final boolean includeExtraQuiverSlot,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		OwnedItem selected = null;
		int selectedRank = Integer.MAX_VALUE;
		for (final OwnedItem item : allOwned)
		{
			if (item == null
				|| (!includeExtraQuiverSlot
					&& item.equipmentSlot == EXTRA_QUIVER_AMMO_SLOT))
			{
				continue;
			}

			final int rank = SlayerQuiverAmmo.infernoPreference(item.itemId);
			if (rank < selectedRank
				|| (rank == selectedRank
					&& item.equipmentSlot == EXTRA_QUIVER_AMMO_SLOT
					&& selected != null
					&& selected.equipmentSlot != EXTRA_QUIVER_AMMO_SLOT))
			{
				selected = item;
				selectedRank = rank;
			}
		}

		if (selected != null && selectedRank < Integer.MAX_VALUE)
		{
			return selected.toLoadoutItem(1);
		}

		/* Name fallback keeps future arrow tiers usable until gamevals update. */
		return recommendedAmmunitionItem(
			includeExtraQuiverSlot,
			"Best owned arrows for the Inferno",
			allOwned,
			bankScanned,
			INFERNO_ARROW_PRIORITY
		);
	}

	/**
	 * Build SlayerPlus' dedicated Dizana second-ammo gear entry.
	 *
	 * This is a recommendation slot, not a mirror of the live quiver contents.
	 * Use the same weapon, encounter and cost-policy ammo resolver as the normal
	 * loadout, while leaving the original ammo slot free for a blessing or penny.
	 */
	private static SlayerLoadoutItem buildDizanaExtraAmmoItem(
		final boolean inferno,
		final CombatStyle style,
		final OwnedItem selectedWeapon,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		if (style != CombatStyle.RANGED || !hasUsableDizanaCape(allOwned))
		{
			return null;
		}

		final String fallbackName = selectedWeapon != null
			? selectedWeapon.normalizedName
			: strategy != null
				&& !strategy.getWeaponPrioritiesForPolicy().isEmpty()
					? normalize(strategy.getWeaponPrioritiesForPolicy().get(0))
					: "";
		if (!canUseDizanaExtraAmmo(selectedWeapon, fallbackName))
		{
			return null;
		}

		return inferno
			? buildInfernoAmmoItem(
				selectedWeapon,
				strategy,
				allOwned,
				bankScanned,
				false
			)
			: buildAmmoItem(
				style,
				selectedWeapon,
				strategy,
				allOwned,
				bankScanned,
				false
			);
	}

	private static boolean canUseDizanaExtraAmmo(
		final OwnedItem selectedWeapon,
		final String fallbackWeaponName)
	{
		final String weaponName = selectedWeapon != null
			? selectedWeapon.normalizedName
			: normalize(fallbackWeaponName);
		if (weaponName.isEmpty())
		{
			return false;
		}

		/* Dizana's extra slot accepts arrows or bolts only. */
		if (weaponName.contains("crossbow"))
		{
			return true;
		}

		if (!weaponName.contains("bow"))
		{
			return false;
		}

		/* Self-ammo bows do not consume arrows from either ammo slot. */
		return !weaponName.contains("bow of faerdhinen")
			&& !weaponName.contains("crystal bow")
			&& !weaponName.contains("webweaver bow")
			&& !weaponName.contains("craw s bow");
	}

	private static boolean matchesWeaponName(
		final OwnedItem selectedWeapon,
		final String fallbackName,
		final String fragment)
	{
		return selectedWeapon != null
			? selectedWeapon.matchesName(fragment)
			: normalize(fallbackName).contains(normalize(fragment));
	}

	private static SlayerLoadoutItem buildPassiveAmmoItem(
		final OwnedItem selectedWeapon,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		/*
		 * Live-game behaviour: Ghommal's lucky penny must currently occupy the
		 * ammo slot to provide its 5% charge-preservation effect. For an
		 * EFFICIENT/Profit setup using a charged Scythe, that recurring-cost
		 * saving outweighs the small prayer bonus from a blessing.
		 *
		 * Jagex has announced a future account-unlock conversion, but the
		 * plugin must not treat a future-update proposal as live behaviour.
		 */
		if (strategy != null
			&& strategy.getCostPolicy()
				== SlayerTaskStrategy.CostPolicy.EFFICIENT
			&& benefitsFromLuckyPenny(selectedWeapon))
		{
			return strictAmmoItem(
				"Ghommal's lucky penny",
				allOwned,
				bankScanned,
				"ghommal s lucky penny",
				"ghommals lucky penny"
			);
		}

		/*
		 * When Dizana's second slot supplies the arrows/bolts, the original ammo
		 * slot becomes a genuine independent equipment slot. Prefer the strongest
		 * prayer blessing deterministically instead of leaving arrows duplicated in
		 * the old slot. Rada's blessing tiers are ordered explicitly because the
		 * generic name fragment would otherwise select whichever owned tier happened
		 * to be encountered first.
		 */
		return strictAmmoItem(
			"Prayer blessing",
			allOwned,
			bankScanned,
			"rada s blessing 4",
			"rada s blessing 3",
			"rada s blessing 2",
			"rada s blessing 1",
			"holy blessing",
			"unholy blessing",
			"war blessing",
			"peaceful blessing",
			"honourable blessing",
			"blessing"
		);
	}

	private static boolean benefitsFromLuckyPenny(
		final OwnedItem selectedWeapon)
	{
		if (selectedWeapon == null)
		{
			return false;
		}

		/*
		 * Keep this list deliberately explicit. A charged weapon should only be
		 * added after its interaction with the Penny has been verified.
		 */
		return selectedWeapon.matchesName("scythe of vitur");
	}

	private static SlayerLoadoutItem buildCapeEquipmentItem(
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<OwnedItem> equipped,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		return equipmentItem(
			EquipmentInventorySlot.CAPE,
			null,
			style == CombatStyle.RANGED
				? "Ava's assembler / accumulator"
				: style == CombatStyle.MAGIC
					? "Magic cape"
					: "Melee cape",
			strategy,
			style,
			equipped,
			allOwned,
			bankScanned,
			false,
			style == CombatStyle.RANGED
				? new String[]{
					"assembler", "accumulator", "ava"
				}
				: style == CombatStyle.MAGIC
					? new String[]{
						"imbued god cape", "god cape"
					}
					: new String[]{
						"infernal cape",
						"fire cape",
						"mythical cape"
					}
		);
	}

	private static OwnedItem findPreferredDizanaCapeOrFallback(
		final List<OwnedItem> allOwned)
	{
		final OwnedItem dizana = findPreferredUsableDizanaCape(allOwned);
		return dizana != null
			? dizana
			: findPreferredEquipment(
				allOwned,
				EquipmentInventorySlot.CAPE,
				false,
				asList(
					"ava s assembler",
					"assembler",
					"ava s accumulator",
					"accumulator"
				)
			);
	}

	/**
	 * Dizana family recognition is name-based on purpose: charged, uncharged,
	 * blessed, Trouver-locked (l), and max-cape variants use separate item IDs,
	 * while RuneLite's equipment-stat cache can briefly lag a newly-added variant.
	 * Broken/mangled death states and the cosmetic max hood are recognized as
	 * family members but never recommended as ready combat gear.
	 */
	private static OwnedItem findPreferredUsableDizanaCape(
		final List<OwnedItem> allOwned)
	{
		if (allOwned == null)
		{
			return null;
		}

		OwnedItem best = null;
		int bestScore = Integer.MIN_VALUE;
		for (final OwnedItem item : allOwned)
		{
			final int score = dizanaCapePreferenceScore(item);
			/*
			 * allOwned is ordered equipped -> inventory -> bank. On equal combat
			 * variants, keep the first one so an already-worn (l) form is not
			 * needlessly replaced by an identical-performance bank copy.
			 */
			if (score > bestScore)
			{
				best = item;
				bestScore = score;
			}
		}
		return bestScore > 0 ? best : null;
	}

	private static int dizanaCapePreferenceScore(final OwnedItem item)
	{
		if (item == null)
		{
			return -1;
		}

		/*
		 * Use the live RuneLite gameval ids first. Dizana variants are split across
		 * three ItemVariationMapping families and those families deliberately also
		 * contain broken/mangled death-state ids, so name-only / variation-only
		 * matching is not strong enough for a combat gear recommendation.
		 */
		switch (Math.abs(item.itemId))
		{
			case ItemID.SKILLCAPE_MAX_DIZANAS:
			case ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER:
				return 400;
			case ItemID.DIZANAS_QUIVER_INFINITE:
			case ItemID.DIZANAS_QUIVER_INFINITE_TROUVER:
				return 300;
			case ItemID.DIZANAS_QUIVER_CHARGED:
			case ItemID.DIZANAS_QUIVER_CHARGED_TROUVER:
				return 200;
			case ItemID.DIZANAS_QUIVER_UNCHARGED:
			case ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER:
				return 100;
			case ItemID.DIZANAS_QUIVER_BROKEN:
			case ItemID.DIZANAS_QUIVER_INFINITE_BROKEN:
			case ItemID.SKILLCAPE_MAX_DIZANAS_BROKEN:
			case ItemID.DIZANAS_QUIVER_TROUVER_BROKEN:
			case ItemID.DIZANAS_QUIVER_TROUVER_MANGLED:
			case ItemID.DIZANAS_QUIVER_INFINITE_TROUVER_BROKEN:
			case ItemID.DIZANAS_QUIVER_INFINITE_TROUVER_MANGLED:
			case ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_BROKEN:
			case ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_MANGLED:
				return -1;
			default:
				break;
		}

		/*
		 * Future-proof fallback for a newly-added usable variant whose gameval id
		 * has not yet been added above. Exact live ids always win when known.
		 */
		final String name = item.normalizedName;
		if (!name.contains("dizana")
			|| (!name.contains("quiver") && !name.contains("max cape"))
			|| name.contains("max hood")
			|| name.contains("broken")
			|| name.contains("mangled"))
		{
			return -1;
		}
		if (name.contains("max cape"))
		{
			return 400;
		}
		if (name.contains("blessed"))
		{
			return 300;
		}
		if (!name.contains("uncharged"))
		{
			return 200;
		}
		return 100;
	}

	private static boolean hasUsableDizanaCape(
		final List<OwnedItem> allOwned)
	{
		return findPreferredUsableDizanaCape(allOwned) != null;
	}

	private static boolean isUsableDizanaCape(final OwnedItem item)
	{
		return dizanaCapePreferenceScore(item) > 0;
	}

	private static OwnedItem findExtraQuiverAmmo(
		final List<OwnedItem> allOwned)
	{
		if (allOwned == null)
		{
			return null;
		}
		for (final OwnedItem item : allOwned)
		{
			if (item != null
				&& item.equipmentSlot == EXTRA_QUIVER_AMMO_SLOT
				&& item.quantity > 0)
			{
				return item;
			}
		}
		return null;
	}

	private static boolean isQuiverAmmoCompatibleWithWeapon(
		final OwnedItem quiverAmmo,
		final OwnedItem selectedWeapon,
		final String fallbackWeaponName)
	{
		if (quiverAmmo == null
			|| !canUseDizanaExtraAmmo(selectedWeapon, fallbackWeaponName))
		{
			return false;
		}

		final String weaponName = selectedWeapon != null
			? selectedWeapon.normalizedName
			: normalize(fallbackWeaponName);
		if (weaponName.contains("crossbow"))
		{
			return quiverAmmo.matchesName("bolt");
		}
		return quiverAmmo.matchesName("arrow");
	}

	private static SlayerLoadoutItem strictAmmoItem(
		final String placeholder,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final String... alternatives)
	{
		final OwnedItem selected = findPreferredEquipment(
			allOwned,
			EquipmentInventorySlot.AMMO,
			false,
			asList(alternatives)
		);

		return toEquipmentItem(selected, placeholder, bankScanned);
	}

	private static SlayerLoadoutItem recommendedAmmunitionItem(
		final boolean includeExtraQuiverSlot,
		final String placeholder,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final String... alternatives)
	{
		if (!includeExtraQuiverSlot)
		{
			return strictAmmoItem(
				placeholder,
				allOwned,
				bankScanned,
				alternatives
			);
		}

		for (final String alternative : alternatives)
		{
			final String fragment = normalize(alternative);
			if (fragment.isEmpty())
			{
				continue;
			}
			final boolean seekingAlternative =
				fragment.startsWith("seeking ")
					&& fragment.contains("arrow");

			for (final OwnedItem item : allOwned)
			{
				/*
				 * ItemVariationMapping aliases are useful for ordinary charged/skinned
				 * gear, but an ordinary arrow must never satisfy a Seeking preference.
				 * That previously selected a loose bank stack before the real nested
				 * quiver stack was examined.
				 */
				if ((!seekingAlternative || item.isExactSeekingArrow())
					&& item.matchesName(fragment)
					&& (item.equipmentSlot == EXTRA_QUIVER_AMMO_SLOT
						|| item.matchesSlot(EquipmentInventorySlot.AMMO)))
				{
					return item.toLoadoutItem(1);
				}
			}
		}

		return missingItem(placeholder, bankScanned);
	}

	private static SlayerLoadoutItem equipmentItem(
		final EquipmentInventorySlot slot,
		final Requirement requirement,
		final String placeholder,
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<OwnedItem> equipped,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final boolean requireOneHanded,
		final String... alternatives)
	{
		final OwnedItem selected = selectEquipmentOwned(
			slot,
			requirement,
			strategy,
			style,
			equipped,
			allOwned,
			requireOneHanded,
			alternatives
		);

		return toEquipmentItem(
			selected,
			requirement == null
				? placeholder
				: requirement.displayName,
			bankScanned
		);
	}

	private static OwnedItem selectEquipmentOwned(
		final EquipmentInventorySlot slot,
		final Requirement requirement,
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<OwnedItem> equipped,
		final List<OwnedItem> allOwned,
		final boolean requireOneHanded,
		final String... alternatives)
	{
		if (requirement != null)
		{
			return findPreferredEquipment(
				allOwned,
				slot,
				requireOneHanded,
				requirement.alternatives
			);
		}

		if (slot == EquipmentInventorySlot.HEAD)
		{
			final OwnedItem slayerHead = findPreferredEquipment(
				allOwned,
				slot,
				false,
				asList("slayer helmet", "black mask")
			);
			if (slayerHead != null)
			{
				return slayerHead;
			}
		}

		if (slot == EquipmentInventorySlot.WEAPON)
		{
			final OwnedItem namedWeapon = findPreferredEquipmentAllowed(
				allOwned,
				slot,
				requireOneHanded,
				asList(alternatives),
				strategy
			);
			if (namedWeapon != null)
			{
				return namedWeapon;
			}

			if (strategy != null
				&& strategy.isStrictWeaponProfile())
			{
				/*
				 * A policy-specific list is the first choice. If none of those
				 * weapons are owned, try the broader reviewed list rather than
				 * inventing an arbitrary same-style weapon. For Profit this
				 * permits a costly reviewed weapon only as a last resort.
				 */
				final List<String> reviewedFallbacks =
					strategy.getWeaponPriorities();
				if (!reviewedFallbacks.isEmpty())
				{
					final OwnedItem allowedFallback =
						findPreferredEquipmentAllowed(
							allOwned,
							slot,
							requireOneHanded,
							reviewedFallbacks,
							strategy
						);
					if (allowedFallback != null)
					{
						return allowedFallback;
					}

					if (strategy.getCostPolicy()
						== SlayerTaskStrategy.CostPolicy.EFFICIENT)
					{
						return findPreferredEquipment(
							allOwned,
							slot,
							requireOneHanded,
							reviewedFallbacks
						);
					}
				}
				/*
				 * A reviewed progression is a ranking, not permission to leave the
				 * weapon slot empty. If the account owns none of its named tiers,
				 * fall back to the strongest compatible owned weapon. Explicit hard
				 * requirements were handled above and deliberately do not use this
				 * fallback.
				 */
				return findBestCompatibleOwnedSlot(
					allOwned, slot, strategy, style, requireOneHanded
				);
			}
		}

		final OwnedItem best = findBestEquipment(
			allOwned,
			slot,
			strategy,
			style,
			requireOneHanded
		);
		if (best != null)
		{
			return best;
		}

		final OwnedItem worn = findEquippedSlot(equipped, slot);
		if (worn != null
			&& isStyleCompatible(worn, style, slot)
			&& (!requireOneHanded || !worn.isTwoHanded())
			&& !isDisallowedByPolicy(worn, strategy, slot))
		{
			return worn;
		}

		return findPreferredEquipment(
			allOwned,
			slot,
			requireOneHanded,
			asList(alternatives)
		);
	}

	private static SlayerLoadoutItem toEquipmentItem(
		final OwnedItem selected,
		final String placeholder,
		final boolean bankScanned)
	{
		return selected == null
			? missingItem(placeholder, bankScanned)
			: selected.toLoadoutItem(1);
	}

	private static OwnedItem findPreferredEquipment(
		final List<OwnedItem> owned,
		final EquipmentInventorySlot slot,
		final boolean requireOneHanded,
		final List<String> alternatives)
	{
		return findPreferredEquipmentVariant(
			owned, slot, requireOneHanded, alternatives, null, false
		);
	}

	private static OwnedItem findPreferredEquipmentAllowed(
		final List<OwnedItem> owned,
		final EquipmentInventorySlot slot,
		final boolean requireOneHanded,
		final List<String> alternatives,
		final SlayerTaskStrategy strategy)
	{
		return findPreferredEquipmentVariant(
			owned, slot, requireOneHanded, alternatives, strategy, true
		);
	}

	/**
	 * Selects a progression tier first, then the strongest exact owned variant
	 * within that tier. RuneLite's ItemMapping aliases are intentionally only a
	 * fallback: if the owned display name directly belongs to a later tier, a
	 * broad tradeable/variation alias may not promote it into an earlier tier.
	 */
	private static OwnedItem findPreferredEquipmentVariant(
		final List<OwnedItem> owned,
		final EquipmentInventorySlot slot,
		final boolean requireOneHanded,
		final List<String> alternatives,
		final SlayerTaskStrategy strategy,
		final boolean enforcePolicy)
	{
		if (owned == null || alternatives == null)
		{
			return null;
		}

		for (int priorityIndex = 0;
			priorityIndex < alternatives.size();
			priorityIndex++)
		{
			final String fragment = normalize(alternatives.get(priorityIndex));
			if (fragment.isEmpty())
			{
				continue;
			}

			OwnedItem bestVariant = null;
			long bestVariantScore = Long.MIN_VALUE;
			for (final OwnedItem item : owned)
			{
				if (item == null
					|| !item.matchesEquipmentProgression(fragment)
					|| !matchesEquipmentSlot(item, slot)
					|| !isUsableEquipmentVariant(item, slot)
					|| requireOneHanded && item.isTwoHanded()
					|| enforcePolicy
						&& isDisallowedByPolicy(item, strategy, slot))
				{
					continue;
				}

				final int directRank = item.directEquipmentProgressionRank(
					alternatives
				);
				if (directRank >= 0 && directRank != priorityIndex)
				{
					/*
					 * Example: unfortified Masori can have a mapping relationship
					 * with fortified Masori, but its exact name belongs to the next
					 * tier and must not inherit the fortified tier's stats/rank.
					 */
					continue;
				}

				final long variantScore = equipmentVariantScore(item, slot);
				if (bestVariant == null || variantScore > bestVariantScore)
				{
					bestVariant = item;
					bestVariantScore = variantScore;
				}
			}
			if (bestVariant != null)
			{
				return bestVariant;
			}
		}

		return null;
	}

	private static boolean isUsableEquipmentVariant(
		final OwnedItem item,
		final EquipmentInventorySlot slot)
	{
		if (item == null)
		{
			return false;
		}
		final String name = item.normalizedName;
		if (name.contains("broken")
			|| name.contains("mangled")
			|| name.contains("depleted")
			|| name.contains("inactive")
			|| name.contains("max hood"))
		{
			return false;
		}
		if (slot == EquipmentInventorySlot.WEAPON
			&& (name.contains("uncharged") || name.contains(" empty")))
		{
			return false;
		}
		return !(name.matches(
			"(?:dharok|guthan|torag|verac) s .+ 0"
		));
	}

	static boolean usableEquipmentVariantForRegression(
		final String displayName,
		final EquipmentInventorySlot slot)
	{
		return isUsableEquipmentVariant(
			new OwnedItem(
				-1,
				displayName,
				1,
				SlayerLoadoutItem.Status.BANK,
				slot == null ? -1 : slot.getSlotIdx(),
				null,
				Collections.singleton(normalize(displayName))
			),
			slot
		);
	}

	private static long equipmentVariantScore(
		final OwnedItem item,
		final EquipmentInventorySlot slot)
	{
		if (item == null || item.equipmentStats == null)
		{
			return Long.MIN_VALUE / 2L + (item == null
				? 0L : ownershipTieBreaker(item.status));
		}
		return Math.max(
			meleeScore(item.equipmentStats, slot),
			Math.max(
				rangedScore(item.equipmentStats, slot),
				magicScore(item.equipmentStats, slot)
			)
		) + ownershipTieBreaker(item.status);
	}

	private static OwnedItem findBestEquipment(
		final List<OwnedItem> owned,
		final EquipmentInventorySlot slot,
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final boolean requireOneHanded)
	{
		OwnedItem best = null;
		long bestScore = Long.MIN_VALUE;

		for (final OwnedItem item : owned)
		{
			if (!matchesEquipmentSlot(item, slot)
				|| item.equipmentStats == null
				|| !isStyleCompatible(item, style, slot)
				|| (requireOneHanded && item.isTwoHanded())
				|| isDisallowedByPolicy(item, strategy, slot))
			{
				continue;
			}

			final long score =
				equipmentScore(item, strategy, style, slot);

			if (best == null || score > bestScore)
			{
				best = item;
				bestScore = score;
			}
		}

		return best;
	}

	private static boolean matchesEquipmentSlot(
		final OwnedItem item,
		final EquipmentInventorySlot slot)
	{
		if (item == null || slot == null)
		{
			return false;
		}
		if (item.matchesSlot(slot))
		{
			return true;
		}
		/*
		 * Bracelets, gloves, gauntlets, and vambraces all occupy RuneScape's glove
		 * slot. Keep that invariant even when a bank snapshot temporarily lacks
		 * ItemEquipmentStats, which is the same metadata gap handled below for
		 * newly-added defender variants.
		 */
		if (slot == EquipmentInventorySlot.GLOVES
			&& isGloveSlotItemName(item.normalizedName))
		{
			return true;
		}
		/*
		 * Some bank snapshots temporarily lack equipment metadata for newly-added
		 * Ghommal/Avernic variants. Their canonical names are still authoritative:
		 * every actual defender is a shield-slot item, while Ghommal's hilt is not.
		 */
		return slot == EquipmentInventorySlot.SHIELD
			&& isDefenderName(item.normalizedName);
	}

	private static boolean isGloveSlotItemName(final String value)
	{
		final String name = normalize(value);
		return name.contains("bracelet")
			|| name.contains("gloves")
			|| name.contains("gauntlets")
			|| name.contains("vambraces");
	}

	static boolean gloveFallbackMatchesSlotForRegression(
		final String displayName)
	{
		return isGloveSlotItemName(displayName);
	}

	private static boolean isDefenderName(final String value)
	{
		final String name = normalize(value);
		return name.contains("defender")
			&& !name.contains("defender hilt");
	}

	static boolean defenderFallbackMatchesShieldForRegression(
		final String displayName)
	{
		return isDefenderName(displayName);
	}

	private static SlayerLoadoutItem auditedCapeEquipmentItem(
		final String task,
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final OwnedItem selectedWeapon)
	{
		if (style == CombatStyle.RANGED)
		{
			final OwnedItem dizana = findPreferredUsableDizanaCape(allOwned);
			if (dizana != null)
			{
				return dizana.toLoadoutItem(1);
			}
		}
		return auditedEquipmentItem(
			task,
			EquipmentInventorySlot.CAPE,
			null,
			style == CombatStyle.RANGED
				? "Dizana's quiver / Ava's assembler"
				: style == CombatStyle.MAGIC
					? "Magic cape"
					: "Melee cape",
			strategy,
			style,
			Collections.emptyList(),
			allOwned,
			bankScanned,
			false,
			selectedWeapon
		);
	}

	/**
	 * Selects from the audited progression first, then guarantees the best
	 * compatible owned fallback for an ordinary slot. A hard requirement remains
	 * unresolved when its required family is not owned; substituting unrelated
	 * equipment there would hide a real preparation error.
	 */
	private static SlayerLoadoutItem auditedEquipmentItem(
		final String task,
		final EquipmentInventorySlot slot,
		final Requirement requirement,
		final String placeholder,
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<OwnedItem> equipped,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final boolean requireOneHanded,
		final OwnedItem selectedWeapon)
	{
		final List<String> alternatives = requirement == null
			? SlayerEquipmentAuditCatalog.priorities(
				task,
				strategy,
				slot,
				selectedWeapon == null ? "" : selectedWeapon.normalizedName
			)
			: requirement.alternatives;
		OwnedItem selected = findPreferredEquipment(
			allOwned, slot, requireOneHanded, alternatives
		);
		if (selected == null && requirement == null)
		{
			selected = findBestCompatibleOwnedSlot(
				allOwned, slot, strategy, style, requireOneHanded
			);
		}
		if (selected == null && requirement == null)
		{
			final OwnedItem worn = findEquippedSlot(equipped, slot);
			if (worn != null
				&& isStyleCompatible(worn, style, slot)
				&& (!requireOneHanded || !worn.isTwoHanded())
				&& !isDisallowedByPolicy(worn, strategy, slot))
			{
				selected = worn;
			}
		}
		return toEquipmentItem(
			selected,
			requirement == null ? placeholder : requirement.displayName,
			bankScanned
		);
	}

	private static OwnedItem findBestCompatibleOwnedSlot(
		final List<OwnedItem> owned,
		final EquipmentInventorySlot slot,
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final boolean requireOneHanded)
	{
		if (owned == null || slot == null)
		{
			return null;
		}
		OwnedItem best = null;
		long bestScore = Long.MIN_VALUE;
		for (final OwnedItem item : owned)
		{
			if (!matchesEquipmentSlot(item, slot)
				|| !isUsableEquipmentVariant(item, slot)
				|| !isStyleCompatible(item, style, slot)
				|| requireOneHanded && item.isTwoHanded()
				|| isDisallowedByPolicy(item, strategy, slot))
			{
				continue;
			}
			final long score = item.equipmentStats == null
				? equipmentVariantScore(item, slot)
				: equipmentScore(item, strategy, style, slot);
			if (best == null || score > bestScore)
			{
				best = item;
				bestScore = score;
			}
		}
		return best;
	}

	static boolean ordinaryOwnedSlotFallbackForRegression(
		final EquipmentInventorySlot slot,
		final String displayName)
	{
		if (slot == null || displayName == null)
		{
			return false;
		}
		final OwnedItem item = new OwnedItem(
			-1,
			displayName,
			1,
			SlayerLoadoutItem.Status.BANK,
			slot.getSlotIdx(),
			null,
			Collections.singleton(normalize(displayName))
		);
		return findBestCompatibleOwnedSlot(
			Collections.singletonList(item), slot, null,
			CombatStyle.FLEXIBLE, false
		) == item;
	}

	private static long equipmentScore(
		final OwnedItem item,
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final EquipmentInventorySlot slot)
	{
		final ItemEquipmentStats stats = item.equipmentStats;
		if (stats == null)
		{
			return Long.MIN_VALUE;
		}

		final long melee = meleeScore(stats, slot);
		final long ranged = rangedScore(stats, slot);
		final long magic = magicScore(stats, slot);
		long selected;

		switch (style)
		{
			case MAGIC:
				selected = magic;
				break;
			case RANGED:
				selected = ranged;
				break;
			case MELEE:
				selected = melee;
				break;
			case FLEXIBLE:
			default:
				selected = Math.max(
					melee,
					Math.max(ranged, magic)
				);
				break;
		}

		if (strategy != null
			&& slot != EquipmentInventorySlot.WEAPON)
		{
			final int defence =
				stats.getDstab()
					+ stats.getDslash()
					+ stats.getDcrush()
					+ stats.getDmagic()
					+ stats.getDrange();

			switch (strategy.getArmourFocus())
			{
				case PRAYER:
					selected += stats.getPrayer() * 60_000L;
					break;
				case DEFENCE:
					selected += defence * 1_500L;
					break;
				case HYBRID:
					selected += (melee + ranged + magic) / 5L;
					break;
				case DAMAGE:
				default:
					break;
			}
		}

		return selected + ownershipTieBreaker(item.status);
	}

	private static boolean isStyleCompatible(
		final OwnedItem item,
		final CombatStyle style,
		final EquipmentInventorySlot slot)
	{
		if (item == null
			|| style == CombatStyle.FLEXIBLE
			|| (slot != EquipmentInventorySlot.WEAPON
				&& slot != EquipmentInventorySlot.SHIELD))
		{
			return true;
		}

		final ItemEquipmentStats stats = item.equipmentStats;
		final String name = item.normalizedName;

		if (slot == EquipmentInventorySlot.WEAPON)
		{
			switch (style)
			{
				case RANGED:
					return name.contains("bow")
						|| name.contains("crossbow")
						|| name.contains("blowpipe")
						|| name.contains("ballista")
						|| name.contains("atlatl")
						|| name.contains("chinchompa")
						|| (stats != null
							&& (stats.getArange() > 0
								|| stats.getRstr() > 0));
				case MAGIC:
					return name.contains("staff")
						|| name.contains("wand")
						|| name.contains("trident")
						|| name.contains("sceptre")
						|| name.contains("sanguinesti")
						|| name.contains("tumeken")
						|| (stats != null
							&& (stats.getAmagic() > 0
								|| stats.getMdmg() > 0));
				case MELEE:
					return name.contains("scythe")
						|| name.contains("axe")
						|| name.contains("sword")
						|| name.contains("whip")
						|| name.contains("rapier")
						|| name.contains("mace")
						|| name.contains("spear")
						|| name.contains("hasta")
						|| name.contains("halberd")
						|| name.contains("fang")
						|| name.contains("claws")
						|| (stats != null
							&& (Math.max(
								stats.getAstab(),
								Math.max(
									stats.getAslash(),
									stats.getAcrush()
								)
							) > 0
								|| stats.getStr() > 0));
				case FLEXIBLE:
				default:
					return true;
			}
		}

		switch (style)
		{
			case RANGED:
				return name.contains("buckler")
					|| name.contains("dragonfire ward")
					|| name.contains("odium ward")
					|| name.contains("book of law")
					|| (stats != null
						&& (stats.getArange() > 0
							|| stats.getRstr() > 0));
			case MAGIC:
				return name.contains("elidinis")
					|| name.contains("mage s book")
					|| name.contains("book of darkness")
					|| name.contains("arcane spirit shield")
					|| name.contains("malediction ward")
					|| name.contains("ancient wyvern shield")
					|| (stats != null
						&& (stats.getAmagic() > 0
							|| stats.getMdmg() > 0));
			case MELEE:
				return name.contains("defender")
					|| name.contains("dragonfire shield")
					|| name.contains("elysian spirit shield")
					|| name.contains("spectral spirit shield")
					|| name.contains("crystal shield")
					|| name.contains("toktz ket xile")
					|| (stats != null
						&& (Math.max(
							stats.getAstab(),
							Math.max(
								stats.getAslash(),
								stats.getAcrush()
							)
						) > 0
							|| stats.getStr() > 0));
			case FLEXIBLE:
			default:
				return true;
		}
	}

	private static boolean isDisallowedByPolicy(
		final OwnedItem item,
		final SlayerTaskStrategy strategy,
		final EquipmentInventorySlot slot)
	{
		if (item == null
			|| strategy == null
			|| slot != EquipmentInventorySlot.WEAPON)
		{
			return false;
		}

		final String name = item.normalizedName;
		/* Chins are valid only for an explicitly reviewed stack/AoE method. */
		if (name.contains("chinchompa")
			&& !strategy.hasTag(SlayerTaskStrategy.MethodTag.CHINNING))
		{
			return true;
		}
		if (strategy.getCostPolicy()
			== SlayerTaskStrategy.CostPolicy.EFFICIENT
			&& !strategy.isBoss())
		{
			return name.contains("scythe of vitur")
				|| name.contains("soulreaper axe")
				|| name.contains("tumeken s shadow")
				|| name.contains("eye of ayak")
				|| name.contains("sanguinesti staff");
		}

		if (strategy.getCostPolicy()
			== SlayerTaskStrategy.CostPolicy.LOW_RISK)
		{
			return name.contains("scythe of vitur")
				|| name.contains("twisted bow")
				|| name.contains("tumeken s shadow")
				|| name.contains("soulreaper axe")
				|| name.contains("zaryte crossbow")
				|| name.contains("bow of faerdhinen")
				|| name.contains("eye of ayak")
				|| name.contains("sanguinesti staff");
		}

		return false;
	}

	private static long meleeScore(
		final ItemEquipmentStats stats,
		final EquipmentInventorySlot slot)
	{
		final int attack = Math.max(
			stats.getAstab(),
			Math.max(stats.getAslash(), stats.getAcrush())
		);
		final int defence =
			stats.getDstab()
				+ stats.getDslash()
				+ stats.getDcrush()
				+ stats.getDmagic()
				+ stats.getDrange();

		final long speedBonus =
			slot == EquipmentInventorySlot.WEAPON
				&& stats.getAspeed() > 0
				? Math.max(0, 8 - stats.getAspeed()) * 40L
				: 0L;

		return stats.getStr() * 10_000L
			+ attack * 120L
			+ stats.getPrayer() * 40L
			+ defence
			+ speedBonus;
	}

	private static long rangedScore(
		final ItemEquipmentStats stats,
		final EquipmentInventorySlot slot)
	{
		final int defence =
			stats.getDstab()
				+ stats.getDslash()
				+ stats.getDcrush()
				+ stats.getDmagic()
				+ stats.getDrange();

		final long speedBonus =
			slot == EquipmentInventorySlot.WEAPON
				&& stats.getAspeed() > 0
				? Math.max(0, 8 - stats.getAspeed()) * 40L
				: 0L;

		return stats.getRstr() * 10_000L
			+ stats.getArange() * 120L
			+ stats.getPrayer() * 40L
			+ defence
			+ speedBonus;
	}

	private static long magicScore(
		final ItemEquipmentStats stats,
		final EquipmentInventorySlot slot)
	{
		final int defence =
			stats.getDstab()
				+ stats.getDslash()
				+ stats.getDcrush()
				+ stats.getDmagic()
				+ stats.getDrange();

		final long speedBonus =
			slot == EquipmentInventorySlot.WEAPON
				&& stats.getAspeed() > 0
				? Math.max(0, 8 - stats.getAspeed()) * 40L
				: 0L;

		return Math.round(stats.getMdmg() * 10_000.0)
			+ stats.getAmagic() * 120L
			+ stats.getPrayer() * 40L
			+ defence
			+ speedBonus;
	}

	private static long ownershipTieBreaker(
		final SlayerLoadoutItem.Status status)
	{
		if (status == SlayerLoadoutItem.Status.EQUIPPED)
		{
			return 3L;
		}
		if (status == SlayerLoadoutItem.Status.INVENTORY)
		{
			return 2L;
		}
		if (status == SlayerLoadoutItem.Status.BANK)
		{
			return 1L;
		}
		return 0L;
	}

	private static List<String> asList(
		final String... values)
	{
		final List<String> result = new ArrayList<>();
		if (values != null)
		{
			Collections.addAll(result, values);
		}
		return result;
	}

	private static boolean isTzKalZukInferno(
		final String task,
		final String location)
	{
		return normalize(task).equals("tzkal zuk")
			&& normalize(location).equals("inferno");
	}

	private List<SlayerLoadoutItem>
		buildInventoryLayout(
			final String task,
			final SlayerTaskStrategy strategy,
			final String location,
			final String travel,
			final CombatStyle style,
			final List<Requirement> requirements,
			final boolean cannonSuggested,
			final int recommendedFoodSlots,
			final List<OwnedItem> allOwned,
			final boolean bankScanned,
			final List<SlayerLoadoutItem> equipmentLayout)
	{
		final SlayerMethodRules methodRules =
			SlayerMethodRuleCatalog.resolve(task, location, strategy);
		final List<SlayerLoadoutItem> layout = new ArrayList<>();
		final boolean analyzerOwnsTravelSlot =
			!isTzKalZukInferno(task, location);

		/*
		 * TzKal-Zuk uses a two-stage preparation flow. The final Inferno loadout is
		 * all 28 combat/supply slots; SlayerPlus temporarily renders the exact
		 * Shortest Path staging teleport above the 4 x 7 until the east Mor Ul Rek
		 * bank is opened. Do not consume any final Inferno combat slot with travel.
		 */
		if (analyzerOwnsTravelSlot)
		{
			final SlayerLoadoutItem selectedTravel = chooseTeleport(
				task, location, travel, allOwned, bankScanned
			);
			addInventoryItem(
				layout,
				selectedTravel
			);
			if (normalize(task).equals("tormented demons")
				&& (selectedTravel == null
					|| !normalize(selectedTravel.getDisplayName())
						.contains("guthixian temple teleport")))
			{
				final OwnedItem litLantern = firstExactDisplayOwned(
					allOwned, "sapphire lantern"
				);
				addInventoryItem(
					layout,
					litLantern == null
						? missingItem("Lit sapphire lantern", bankScanned)
						: litLantern.toLoadoutItem(1)
				);
			}
		}

		for (final Requirement requirement : requirements)
		{
			if (!requirement.equipment)
			{
				addInventoryItem(
					layout,
					chooseItem(
						requirement.displayName,
						requirement.quantity,
						allOwned,
						bankScanned,
						requirement.alternatives
					)
				);
			}
		}

		final boolean resolvedCannonMethod = cannonSuggested
			|| methodRules.usesCannon();
		if (resolvedCannonMethod)
		{
			addInventoryItem(layout, chooseItem(
				"Cannon base", 1, allOwned, bankScanned, "cannon base"
			));
			addInventoryItem(layout, chooseItem(
				"Cannon stand", 1, allOwned, bankScanned, "cannon stand"
			));
			addInventoryItem(layout, chooseItem(
				"Cannon barrels", 1, allOwned, bankScanned, "cannon barrels"
			));
			addInventoryItem(layout, chooseItem(
				"Cannon furnace", 1, allOwned, bankScanned, "cannon furnace"
			));
			final int cannonballs = methodRules.getCannonballQuantity() > 0
				? methodRules.getCannonballQuantity()
				: SlayerMethodRuleCatalog.auditedCannonballQuantity(task);
			addInventoryItem(layout, chooseItem(
				"Cannonballs", cannonballs, allOwned, bankScanned, "cannonball"
			));
		}

		final boolean runePouchRequested =
			(strategy != null && strategy.needsRunePouch())
				|| methodRules.requiresRunePouch()
				|| style == CombatStyle.MAGIC
					&& methodRules.includesRunePouchForMagic();
		final int runePouchCapacity = runePouchRequested
			? ownedRunePouchCapacity(allOwned) : 0;
		if (runePouchRequested)
		{
			addInventoryItem(layout, chooseItem(
				"Rune pouch", 1, allOwned, bankScanned,
				"divine rune pouch", "rune pouch"
			));
		}

		if (methodRules.requiresBookOfDead())
		{
			addInventoryItem(layout, chooseItem(
				"Book of the dead", 1, allOwned, bankScanned,
				"book of the dead"
			));
		}

		/*
		 * Rune-pouch contents are references, not inventory slots. Reserve the
		 * actual owned pouch capacity (3 normal / 4 divine). Rune types beyond
		 * that capacity remain real 4 x 7 inventory stacks. The global ownership
		 * policy omits rune types the account does not own and collapses compatible
		 * combination runes (for example Dust satisfying both Air and Earth).
		 */
		final SlayerRuneOwnershipPolicy.Resolution resolvedRunePackage =
			SlayerRuneOwnershipPolicy.resolve(
				methodRules.getPouchRunes(), ownedItemQuantities(allOwned)
			);
		int pouchRuneSlotsRemaining = runePouchCapacity;
		for (final SlayerRuneOwnershipPolicy.ResolvedRune rune
			: resolvedRunePackage.getRunes())
		{
			if (pouchRuneSlotsRemaining > 0)
			{
				pouchRuneSlotsRemaining--;
				continue;
			}
			addInventoryItem(layout, chooseItem(
				rune.getName() + " runes",
				rune.getMinimumQuantity(),
				allOwned,
				bankScanned,
				normalize(rune.getName()) + " rune"
			));
		}

		final java.util.Set<String> structuredPouchRunes =
			new java.util.LinkedHashSet<>();
		for (final SlayerRuneOwnershipPolicy.ResolvedRune rune
			: resolvedRunePackage.getRunes())
		{
			structuredPouchRunes.add(normalize(rune.getName()));
		}
		final java.util.Set<String> unavailableStructuredRunes =
			new java.util.LinkedHashSet<>();
		for (final String rune : resolvedRunePackage.getUnownedRequirements())
		{
			unavailableStructuredRunes.add(normalize(rune));
		}

		final boolean tormentedDemons = normalize(task).equals("tormented demons");
		final boolean maggotKing = normalize(task).contains("maggot king");
		SlayerLoadoutItem tormentedSecondaryWeapon = null;
		SlayerLoadoutItem maggotCrushWeapon = null;
		for (final SlayerMethodRules.RequiredItem required
			: methodRules.getRequiredItems())
		{
			if (!achievementDiaries.allowsLoadoutReward(
				required.getDisplayName(), required.getAlternatives()
			))
			{
				continue;
			}
			/*
			 * The structured method rule already inserted the actual rune pouch and
			 * Book of the dead above. Older catalog entries still list those same
			 * items in requiredItems for compatibility. Do not turn those legacy
			 * declarations into duplicate 4 x 7 slots.
			 */
			if (isStructuredUtilityDuplicate(required, methodRules))
			{
				continue;
			}

			if (isRuneRequirement(required))
			{
				final String requiredRune = normalize(required.getDisplayName())
					.replace(" runes", "").replace(" rune", "").trim();

				/*
				 * A rune already present in the structured spell package was
				 * handled above (inside pouch or as overflow). Do not add the
				 * legacy RequiredItem copy a second time.
				 */
				if (isCoveredByStructuredPouchRune(structuredPouchRunes, requiredRune))
				{
					continue;
				}

				/*
				 * The ownership resolver deliberately omits a rune when neither the
				 * base rune nor a compatible combination rune is owned. Do not let
				 * the legacy RequiredItem declaration add that rune back as an
				 * unobtainable placeholder.
				 */
				if (unavailableStructuredRunes.contains(requiredRune))
				{
					continue;
				}

				if (pouchRuneSlotsRemaining > 0)
				{
					pouchRuneSlotsRemaining--;
					continue;
				}
			}
			List<String> requiredAlternatives = required.getAlternatives();
			if (maggotKing && maggotCrushWeapon != null)
			{
				if (skipMaggotKingRequirement(
					required.getDisplayName(), maggotCrushWeapon
				))
				{
					continue;
				}
				requiredAlternatives = maggotKingSwitchAlternatives(
					required.getDisplayName(), maggotCrushWeapon,
					requiredAlternatives
				);
			}
			if (tormentedDemons && tormentedSecondaryWeapon != null)
			{
				if (skipTormentedRequirement(
					required.getDisplayName(), tormentedSecondaryWeapon
				))
				{
					continue;
				}
				requiredAlternatives = tormentedSwitchAlternatives(
					required.getDisplayName(),
					tormentedSecondaryWeapon,
					requiredAlternatives
				);
			}
			SlayerLoadoutItem requiredItem = chooseItem(
				required.getDisplayName(),
				required.getSlotCount() > 1 ? 1 : required.getQuantity(),
				allOwned,
				bankScanned,
				requiredAlternatives,
				required.getGroup() == SlayerMethodRules.InventoryGroup.SWITCH
			);
			if (required.isOwnedOnly()
				&& (requiredItem == null || !requiredItem.hasItemId()))
			{
				continue;
			}
			if (tormentedDemons
				&& required.getDisplayName().equals("Secondary weapon switch"))
			{
				tormentedSecondaryWeapon = requiredItem;
			}
			if (maggotKing
				&& required.getDisplayName().equals("Crush punish weapon"))
			{
				maggotCrushWeapon = requiredItem;
			}
			if (required.getGroup() == SlayerMethodRules.InventoryGroup.SWITCH)
			{
				final SlayerLoadoutItem.SwitchStyle switchStyle =
					tormentedDemons
						&& isTormentedSecondaryRequirement(required.getDisplayName())
						&& isPurgingStaff(tormentedSecondaryWeapon)
							? SlayerLoadoutItem.SwitchStyle.MAGIC
							: inferSwitchStyle(required, style);
				requiredItem = requiredItem.asEquipmentSwitch(
					switchStyle
				);
				/* Shared hybrid armour is already worn; never ask for the same
				 * physical item again as an inventory switch. */
				if (isAlreadyEquippedInPlan(requiredItem, equipmentLayout))
				{
					continue;
				}
			}
			for (int slot = 0;
				slot < required.getSlotCount() && layout.size() < 28;
				slot++)
			{
				addInventoryItem(layout, requiredItem);
			}
		}

		if (tormentedDemons)
		{
			addInventoryItem(
				layout,
				tormentedSwitchAmmunition(
					tormentedSecondaryWeapon,
					strategy,
					allOwned,
					bankScanned
				)
			);
		}

		if (methodRules.includesStyleBoost())
		{
			final SlayerTaskStrategy.CostPolicy potionCostPolicy =
				strategy == null
					? SlayerTaskStrategy.CostPolicy.EFFICIENT
					: strategy.getCostPolicy();
			if (style == CombatStyle.MAGIC)
			{
				addInventoryItem(layout, chooseItem(
					"Magic boost", 1, allOwned, bankScanned,
					SlayerPotionPolicy.magicBoostAlternatives()
				));
			}
			else if (style == CombatStyle.RANGED)
			{
				addInventoryItem(layout, chooseItem(
					"Ranging potion", 1, allOwned, bankScanned,
					SlayerPotionPolicy.rangedBoostAlternatives(
						potionCostPolicy
					)
				));
			}
			else if (style == CombatStyle.FLEXIBLE)
			{
				addInventoryItem(layout, chooseItem(
					"Super combat potion", 1, allOwned, bankScanned,
					SlayerPotionPolicy.meleeBoostAlternatives(
						potionCostPolicy
					)
				));
				addInventoryItem(layout, chooseItem(
					"Ranging potion", 1, allOwned, bankScanned,
					SlayerPotionPolicy.rangedBoostAlternatives(
						potionCostPolicy
					)
				));
				if (hybridUsesCombatMagic(strategy))
				{
					addInventoryItem(layout, chooseItem(
						"Magic boost", 1, allOwned, bankScanned,
						SlayerPotionPolicy.magicBoostAlternatives()
					));
				}
			}
			else
			{
				addInventoryItem(layout, chooseItem(
					"Super combat potion", 1, allOwned, bankScanned,
					SlayerPotionPolicy.meleeBoostAlternatives(
						potionCostPolicy
					)
				));
			}
		}

		if (strategy != null && strategy.needsAntivenom())
		{
			addInventoryItem(layout, chooseItem(
				SlayerPotionPolicy.EXTENDED_ANTIVENOM_DISPLAY,
				1,
				allOwned,
				bankScanned,
				SlayerPotionPolicy.antivenomAlternatives()
			));
		}

		if (strategy != null && strategy.needsStamina())
		{
			addInventoryItem(layout, chooseItem(
				SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
				1,
				allOwned,
				bankScanned,
				SlayerPotionPolicy.staminaAlternatives()
			));
		}

		final SlayerLoadoutItem prayerRestore = chooseRestore(
			methodRules,
			allOwned,
			bankScanned
		);
		final int prayerSlots = methodRules.resolveRestoreSlots(strategy);
		for (int i = 0; i < prayerSlots && layout.size() < 28; i++)
		{
			addInventoryItem(layout, prayerRestore);
		}

		final SlayerLoadoutItem food = chooseItem(
			methodRules.getFoodDisplayName(),
			1,
			allOwned,
			bankScanned,
			methodRules.getFoodAlternatives()
		);
		final int foodSlots = methodRules.resolveFoodSlots(
			strategy,
			recommendedFoodSlots
		);
		for (int i = 0; i < foodSlots && layout.size() < 28; i++)
		{
			layout.add(food);
		}

		final int target = Math.min(
			methodRules.getInventoryTarget(),
			Math.max(0, 28 - methodRules.getReservedLootSlots())
		);
		if (methodRules.fillsRemainingWithRestore())
		{
			while (layout.size() < target && layout.size() < 28)
			{
				layout.add(prayerRestore);
			}
		}
		else if (methodRules.fillsRemainingWithFood())
		{
			while (layout.size() < target && layout.size() < 28)
			{
				layout.add(food);
			}
		}

		return enforceConcreteInventoryTarget(
			organizeInventoryLayout(
				layout, methodRules, analyzerOwnsTravelSlot
			),
			methodRules,
			prayerRestore,
			food
		);
	}

	private static List<String> maggotKingSwitchAlternatives(
		final String displayName,
		final SlayerLoadoutItem crushWeapon,
		final List<String> defaults)
	{
		final String requirement = normalize(displayName);
		final String weapon = normalize(
			crushWeapon == null ? "" : crushWeapon.getDisplayName()
		);
		/* Soulreaper's set effect requires coherent Oathplate pieces. */
		if (weapon.contains("soulreaper axe"))
		{
			if (requirement.equals("melee body switch"))
			{
				return asList(
					"oathplate chest", "inquisitor s hauberk", "torva platebody",
					"bandos chestplate", "blood moon chestplate", "fighter torso"
				);
			}
			if (requirement.equals("melee legs switch"))
			{
				return asList(
					"oathplate legs", "inquisitor s plateskirt", "torva platelegs",
					"bandos tassets", "blood moon tassets", "obsidian platelegs"
				);
			}
		}
		return defaults;
	}

	private static boolean skipMaggotKingRequirement(
		final String displayName,
		final SlayerLoadoutItem crushWeapon)
	{
		return normalize(displayName).equals("melee defender switch")
			&& isTwoHandedMaggotKingWeapon(crushWeapon);
	}

	private static boolean isTwoHandedMaggotKingWeapon(
		final SlayerLoadoutItem crushWeapon)
	{
		final String weapon = normalize(
			crushWeapon == null ? "" : crushWeapon.getDisplayName()
		);
		return weapon.contains("scythe of vitur")
			|| weapon.contains("soulreaper axe")
			|| weapon.contains("abyssal bludgeon")
			|| weapon.contains("dual macuahuitl");
	}

	private static List<String> tormentedSwitchAlternatives(
		final String displayName,
		final SlayerLoadoutItem rangedWeapon,
		final List<String> defaults)
	{
		final String requirement = normalize(displayName);
		final String weapon = normalize(
			rangedWeapon == null ? "" : rangedWeapon.getDisplayName()
		);
		if (requirement.equals("secondary body switch"))
		{
			if (isPurgingStaff(rangedWeapon))
			{
				return asList("ancestral robe top", "virtus robe top", "ahrim s robetop", "blue moon chestplate", "bloodbark body", "mystic robe top");
			}
			if (weapon.contains("bow of faerdhinen"))
			{
				return asList("crystal body", "masori body f", "masori body", "blessed body", "black d hide body", "mixed hide top");
			}
			if (weapon.contains("eclipse atlatl"))
			{
				return asList("eclipse moon chestplate", "masori body f", "masori body", "blessed body", "black d hide body", "mixed hide top");
			}
		}
		if (requirement.equals("secondary legs switch"))
		{
			if (isPurgingStaff(rangedWeapon))
			{
				return asList("ancestral robe bottom", "virtus robe bottom", "ahrim s robeskirt", "blue moon tassets", "bloodbark legs", "mystic robe bottom");
			}
			if (weapon.contains("bow of faerdhinen"))
			{
				return asList("crystal legs", "masori chaps f", "masori chaps", "blessed chaps", "black d hide chaps", "mixed hide legs");
			}
			if (weapon.contains("eclipse atlatl"))
			{
				return asList("eclipse moon tassets", "masori chaps f", "masori chaps", "blessed chaps", "black d hide chaps", "mixed hide legs");
			}
		}
		return defaults;
	}

	private static List<String> araxxorSafeWeaponAlternatives()
	{
		return asList(
			"noxious halberd", "heavy ballista",
			"hunters sunlight crossbow", "rune crossbow", "karil s crossbow",
			"dragon halberd", "crystal halberd",
			"dharok s greataxe", "zombie axe"
		);
	}

	private static List<String> araxxorSafeAmmunitionAlternatives(
		final String safeWeaponName)
	{
		final String weapon = normalize(safeWeaponName);
		if (weapon.isEmpty())
		{
			return java.util.Collections.emptyList();
		}
		if (weapon.contains("hunters sunlight crossbow"))
		{
			return asList("moonlight antler bolts", "sunlight antler bolts");
		}
		if (weapon.contains("karil") && weapon.contains("crossbow"))
		{
			return asList("bolt rack");
		}
		if (weapon.contains("heavy ballista"))
		{
			return asList("dragon javelin", "amethyst javelin", "rune javelin");
		}
		if (weapon.contains("crossbow"))
		{
			return asList(
				"dragonstone dragon bolts e", "diamond dragon bolts e",
				"dragon bolts", "diamond bolts e", "runite bolts",
				"amethyst broad bolts", "broad bolts"
			);
		}
		/* Halberds and the reviewed crush fallbacks need no ammunition. */
		return java.util.Collections.emptyList();
	}

	static List<String> araxxorSafeAmmunitionForRegression(
		final String safeWeaponName)
	{
		return new ArrayList<>(
			araxxorSafeAmmunitionAlternatives(safeWeaponName)
		);
	}

	private static boolean skipTormentedRequirement(
		final String displayName,
		final SlayerLoadoutItem secondaryWeapon)
	{
		final String requirement = normalize(displayName);
		if (requirement.equals("magic off hand switch"))
		{
			return !isPurgingStaff(secondaryWeapon);
		}
		if (requirement.equals("divine ranging potion"))
		{
			return isPurgingStaff(secondaryWeapon);
		}
		return false;
	}

	private static boolean isTormentedSecondaryRequirement(
		final String displayName)
	{
		final String requirement = normalize(displayName);
		return requirement.equals("secondary weapon switch")
			|| requirement.equals("secondary body switch")
			|| requirement.equals("secondary legs switch")
			|| requirement.equals("magic off hand switch");
	}

	private static boolean isPurgingStaff(
		final SlayerLoadoutItem secondaryWeapon)
	{
		return secondaryWeapon != null
			&& normalize(secondaryWeapon.getDisplayName()).contains("purging staff");
	}

	private static SlayerLoadoutItem tormentedSwitchAmmunition(
		final SlayerLoadoutItem rangedWeapon,
		final SlayerTaskStrategy strategy,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		final String weapon = normalize(
			rangedWeapon == null ? "" : rangedWeapon.getDisplayName()
		);
		if (weapon.contains("purging staff"))
		{
			return null;
		}
		if (weapon.contains("toxic blowpipe")
			|| weapon.contains("bow of faerdhinen"))
		{
			return null;
		}
		if (weapon.contains("eclipse atlatl"))
		{
			return recommendedAmmunitionItem(
				true, "Atlatl darts", allOwned, bankScanned, "atlatl dart"
			).asEquipmentSwitch(SlayerLoadoutItem.SwitchStyle.RANGED);
		}
		if (weapon.contains("hunters sunlight crossbow"))
		{
			return recommendedAmmunitionItem(
				true, "Antler bolts", allOwned, bankScanned,
				"moonlight antler bolts", "sunlight antler bolts"
			).asEquipmentSwitch(SlayerLoadoutItem.SwitchStyle.RANGED);
		}
		if (weapon.contains("crossbow"))
		{
			return recommendedAmmunitionItem(
				true, "Compatible bolts", allOwned, bankScanned,
				"ruby dragon bolts e", "diamond dragon bolts e", "dragon bolts",
				"ruby bolts e", "diamond bolts e", "amethyst broad bolts",
				"runite bolts", "broad bolts"
			).asEquipmentSwitch(SlayerLoadoutItem.SwitchStyle.RANGED);
		}
		return bestOwnedStandardArrow(
			true,
			null,
			weapon,
			strategy,
			allOwned,
			bankScanned
		)
			.asEquipmentSwitch(SlayerLoadoutItem.SwitchStyle.RANGED);
	}

	private static boolean isAlreadyEquippedInPlan(
		final SlayerLoadoutItem candidate,
		final List<SlayerLoadoutItem> equipmentLayout)
	{
		if (candidate == null || equipmentLayout == null)
		{
			return false;
		}
		for (final SlayerLoadoutItem equipped : equipmentLayout)
		{
			if (equipped == null)
			{
				continue;
			}
			if (candidate.hasItemId() && equipped.hasItemId()
				&& candidate.getItemId() == equipped.getItemId())
			{
				return true;
			}
			if (!candidate.hasItemId() && !equipped.hasItemId()
				&& normalize(candidate.getDisplayName()).equals(
					normalize(equipped.getDisplayName())))
			{
				return true;
			}
		}
		return false;
	}

	private static List<SlayerLoadoutItem> enforceConcreteInventoryTarget(
		final List<SlayerLoadoutItem> source,
		final SlayerMethodRules rules,
		final SlayerLoadoutItem prayerRestore,
		final SlayerLoadoutItem food)
	{
		if (source == null || source.isEmpty() || rules == null)
		{
			return source;
		}

		final int target = Math.min(
			Math.max(0, rules.getInventoryTarget()),
			Math.max(0, 28 - rules.getReservedLootSlots())
		);
		if (target <= 0)
		{
			return source;
		}

		final List<SlayerLoadoutItem> concrete = new ArrayList<>();
		final List<SlayerLoadoutItem> unresolved = new ArrayList<>();
		for (final SlayerLoadoutItem item : source)
		{
			if (item == null)
			{
				continue;
			}
			if (item.hasItemId())
			{
				concrete.add(item);
			}
			else
			{
				unresolved.add(item);
			}
		}

		if (!rules.fillsRemainingWithRestore()
			&& !rules.fillsRemainingWithFood())
		{
			return source;
		}

		final SlayerLoadoutItem preferredFiller =
			rules.fillsRemainingWithRestore() ? prayerRestore : food;
		final SlayerLoadoutItem secondaryFiller =
			rules.fillsRemainingWithRestore() ? food : prayerRestore;

		fillConcreteInventory(concrete, target, preferredFiller);
		fillConcreteInventory(concrete, target, secondaryFiller);

		final List<SlayerLoadoutItem> result = new ArrayList<>(
			Math.min(28, concrete.size() + unresolved.size())
		);
		result.addAll(concrete.subList(0, Math.min(28, concrete.size())));
		for (final SlayerLoadoutItem item : unresolved)
		{
			if (result.size() >= 28)
			{
				break;
			}
			result.add(item);
		}
		return result;
	}

	private static void fillConcreteInventory(
		final List<SlayerLoadoutItem> layout,
		final int target,
		final SlayerLoadoutItem filler)
	{
		if (layout == null || filler == null || !filler.hasItemId())
		{
			return;
		}

		while (layout.size() < target && layout.size() < 28)
		{
			layout.add(filler);
		}
	}

	private static int ownedRunePouchCapacity(
		final List<OwnedItem> allOwned)
	{
		boolean regular = false;
		if (allOwned != null)
		{
			for (final OwnedItem item : allOwned)
			{
				if (item == null)
				{
					continue;
				}
				final String name = item.normalizedName;
				if (name.contains("divine rune pouch"))
				{
					return 4;
				}
				if (name.contains("rune pouch"))
				{
					regular = true;
				}
			}
		}
		return regular ? 3 : 0;
	}

	private static boolean isStructuredUtilityDuplicate(
		final SlayerMethodRules.RequiredItem required,
		final SlayerMethodRules rules)
	{
		if (required == null || rules == null)
		{
			return false;
		}

		final String name = normalize(required.getDisplayName());
		if (rules.requiresRunePouch()
			&& (name.equals("rune pouch")
				|| name.equals("divine rune pouch")))
		{
			return true;
		}
		return rules.requiresBookOfDead()
			&& name.equals("book of the dead");
	}

	private static boolean isCoveredByStructuredPouchRune(
		final java.util.Set<String> structuredPouchRunes,
		final String requiredRune)
	{
		if (structuredPouchRunes == null || structuredPouchRunes.isEmpty())
		{
			return false;
		}

		if (structuredPouchRunes.contains(requiredRune))
		{
			return true;
		}

		/*
		 * Aether acts as both Cosmic and Soul for Arceuus packages. Older
		 * compatibility rules may still list Cosmic/Soul separately, but those
		 * should not become extra 4 x 7 inventory stacks when Aether is already
		 * assigned to the rune pouch.
		 */
		if (structuredPouchRunes.contains("aether")
			&& ("cosmic".equals(requiredRune) || "soul".equals(requiredRune)))
		{
			return true;
		}

		return false;
	}

	private static boolean isRuneRequirement(
		final SlayerMethodRules.RequiredItem required)
	{
		if (required == null
			|| required.getGroup() != SlayerMethodRules.InventoryGroup.RUNES_AMMO)
		{
			return false;
		}
		final String name = normalize(required.getDisplayName());
		return name.endsWith(" rune") || name.endsWith(" runes");
	}

	private static SlayerLoadoutItem chooseRestore(
		final SlayerMethodRules rules,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		if (rules == null || !rules.hasRestorePolicy())
		{
			return missingItem("Prayer restoration", bankScanned);
		}

		OwnedItem owned = fullestOwnedDose(
			allOwned,
			rules.getPrimaryRestoreFamily()
		);
		if (owned == null
			&& rules.allowsRestoreFallback()
			&& !rules.getFallbackRestoreFamily().isEmpty())
		{
			owned = fullestOwnedDose(
				allOwned,
				rules.getFallbackRestoreFamily()
			);
		}

		return owned == null
			? missingItem(displayPotionFamily(rules.getPrimaryRestoreFamily()), bankScanned)
			: owned.toLoadoutItem(1);
	}

	private static String displayPotionFamily(final String family)
	{
		if (family == null || family.trim().isEmpty())
		{
			return "Prayer restoration";
		}
		final String value = family.trim();
		return Character.toUpperCase(value.charAt(0))
			+ value.substring(1) + "(4)";
	}

	private static OwnedItem fullestOwnedDose(
		final List<OwnedItem> allOwned,
		final String family)
	{
		final String normalizedFamily = stripPotionDose(normalize(family));
		if (normalizedFamily.isEmpty())
		{
			return null;
		}

		return firstExactDisplayOwned(
			allOwned,
			normalizedFamily + " 4",
			normalizedFamily + " 3",
			normalizedFamily + " 2",
			normalizedFamily + " 1"
		);
	}

	private static String stripPotionDose(final String value)
	{
		return value == null
			? ""
			: value.replaceFirst("\\s+[1-4]$", "").trim();
	}

	private static List<SlayerLoadoutItem> organizeInventoryLayout(
		final List<SlayerLoadoutItem> source,
		final SlayerMethodRules rules,
		final boolean sourceSlotZeroIsTravel)
	{
		final Map<SlayerMethodRules.InventoryGroup, List<SlayerLoadoutItem>> groups =
			new LinkedHashMap<>();
		for (final SlayerMethodRules.InventoryGroup group
			: SlayerMethodRules.InventoryGroup.values())
		{
			groups.put(group, new ArrayList<>());
		}

		for (int index = 0; index < source.size(); index++)
		{
			final SlayerLoadoutItem item = source.get(index);
			if (item == null)
			{
				continue;
			}
			final SlayerMethodRules.InventoryGroup group =
				sourceSlotZeroIsTravel && index == 0
					? SlayerMethodRules.InventoryGroup.TRAVEL
					: classifyInventoryItem(item, rules);
			groups.get(group).add(item.withInventoryGroup(group));
		}

		/*
		 * GLOBAL 4 x 7 PRESENTATION RULE
		 * ------------------------------
		 * Every task uses the same compact visual language: travel, rune pouch, and
		 * authored gear switches first, then utility/ammunition/protection/boosts,
		 * then contiguous food
		 * and restore blocks. Items inside a group are sorted by a dose-insensitive
		 * visual family so identical supplies never look randomly scattered.
		 */
		final List<SlayerLoadoutItem> organized = new ArrayList<>(source.size());
		addGroup(organized, groups, SlayerMethodRules.InventoryGroup.TRAVEL, rules);

		/*
		 * Rune pouch is the anchor utility for spell-enabled encounters. Put it
		 * before gear switches so switch packages begin on a predictable edge of
		 * the 4 x 7 instead of leaving the pouch buried after six switch items.
		 */
		final List<SlayerLoadoutItem> utilities =
			groups.get(SlayerMethodRules.InventoryGroup.UTILITY);
		if (utilities != null && !utilities.isEmpty())
		{
			final List<SlayerLoadoutItem> runePouches = new ArrayList<>();
			for (int index = utilities.size() - 1; index >= 0; index--)
			{
				final SlayerLoadoutItem utility = utilities.get(index);
				if (normalize(utility == null ? "" : utility.getDisplayName())
					.contains("rune pouch"))
				{
					runePouches.add(0, utility);
					utilities.remove(index);
				}
			}
			organized.addAll(runePouches);
		}

		addGroup(organized, groups, SlayerMethodRules.InventoryGroup.SWITCH, rules);
		addGroup(organized, groups, SlayerMethodRules.InventoryGroup.UTILITY, rules);
		addGroup(organized, groups, SlayerMethodRules.InventoryGroup.RUNES_AMMO, rules);
		addGroup(organized, groups, SlayerMethodRules.InventoryGroup.PROTECTION, rules);
		addGroup(organized, groups, SlayerMethodRules.InventoryGroup.BOOST, rules);
		addGroup(organized, groups, SlayerMethodRules.InventoryGroup.FOOD, rules);
		addGroup(organized, groups, SlayerMethodRules.InventoryGroup.RESTORE, rules);
		addGroup(organized, groups, SlayerMethodRules.InventoryGroup.OTHER, rules);
		return organized;
	}

	private static void addGroup(
		final List<SlayerLoadoutItem> destination,
		final Map<SlayerMethodRules.InventoryGroup, List<SlayerLoadoutItem>> groups,
		final SlayerMethodRules.InventoryGroup group,
		final SlayerMethodRules rules)
	{
		final List<SlayerLoadoutItem> items = groups.get(group);
		if (items == null || items.isEmpty())
		{
			return;
		}

		/*
		 * GLOBAL VISUAL AUTHORING RULE
		 * ----------------------------
		 * Required-item declaration order is intentional encounter research. Keep
		 * that order inside each visual group before falling back to family/name
		 * sorting. This keeps switch packages in a human-authored sequence while
		 * repeated potions/food still form deterministic contiguous blocks.
		 */
		items.sort((left, right) ->
		{
			final int leftPriority = inventoryAuthoredPriority(left, rules, group);
			final int rightPriority = inventoryAuthoredPriority(right, rules, group);
			if (leftPriority != rightPriority)
			{
				return Integer.compare(leftPriority, rightPriority);
			}
			final int family = inventoryVisualFamily(left).compareTo(
				inventoryVisualFamily(right)
			);
			if (family != 0)
			{
				return family;
			}
			return normalize(left == null ? "" : left.getDisplayName()).compareTo(
				normalize(right == null ? "" : right.getDisplayName())
			);
		});
		if (isSingletonInventoryGroup(group))
		{
			final Set<String> seen = new HashSet<>();
			items.removeIf(item -> !seen.add(itemIdentity(item)));
		}
		destination.addAll(items);
	}

	private static boolean isSingletonInventoryGroup(
		final SlayerMethodRules.InventoryGroup group)
	{
		return group == SlayerMethodRules.InventoryGroup.TRAVEL
			|| group == SlayerMethodRules.InventoryGroup.SWITCH
			|| group == SlayerMethodRules.InventoryGroup.UTILITY
			|| group == SlayerMethodRules.InventoryGroup.RUNES_AMMO
			|| group == SlayerMethodRules.InventoryGroup.PROTECTION;
	}

	private static String itemIdentity(final SlayerLoadoutItem item)
	{
		if (item == null)
		{
			return "null";
		}
		return item.hasItemId()
			? "id:" + item.getItemId()
			: "name:" + normalize(item.getDisplayName());
	}

	private static int inventoryAuthoredPriority(
		final SlayerLoadoutItem item,
		final SlayerMethodRules rules,
		final SlayerMethodRules.InventoryGroup group)
	{
		final String name = normalize(item == null ? "" : item.getDisplayName());
		if (name.contains("rune pouch"))
		{
			return -1000;
		}
		if (rules == null)
		{
			return Integer.MAX_VALUE;
		}
		int index = 0;
		for (final SlayerMethodRules.RequiredItem required : rules.getRequiredItems())
		{
			if (required.getGroup() != group)
			{
				index++;
				continue;
			}
			final String display = normalize(required.getDisplayName());
			if (!display.isEmpty() && name.equals(display))
			{
				return index;
			}
			for (final String alternative : required.getAlternatives())
			{
				final String normalizedAlternative = normalize(alternative);
				if (!normalizedAlternative.isEmpty()
					&& name.contains(normalizedAlternative))
				{
					return index;
				}
			}
			index++;
		}
		return Integer.MAX_VALUE;
	}

	private static String inventoryVisualFamily(final SlayerLoadoutItem item)
	{
		if (item == null)
		{
			return "";
		}
		return stripPotionDose(normalize(item.getDisplayName()))
			.replaceFirst("^divine\\s+", "")
			.trim();
	}

	private static SlayerMethodRules.InventoryGroup classifyInventoryItem(
		final SlayerLoadoutItem item,
		final SlayerMethodRules rules)
	{
		final String name = normalize(item.getDisplayName());
		if (rules != null)
		{
			for (final SlayerMethodRules.RequiredItem required
				: rules.getRequiredItems())
			{
				if (name.equals(normalize(required.getDisplayName())))
				{
					return required.getGroup();
				}
				for (final String alternative : required.getAlternatives())
				{
					if (name.contains(normalize(alternative)))
					{
						return required.getGroup();
					}
				}
			}
		}

		if (name.contains("rune pouch") || name.contains("book of the dead")
			|| name.contains("herb sack") || name.contains("gem bag")
			|| name.contains("bonecrusher") || name.contains("goading potion"))
		{
			return SlayerMethodRules.InventoryGroup.UTILITY;
		}
		if (name.contains("sceptre") || name.contains("switch"))
		{
			return SlayerMethodRules.InventoryGroup.SWITCH;
		}
		if (name.contains(" rune") || name.endsWith("runes")
			|| name.contains("cannon") || name.contains("dart")
			|| name.contains("arrow") || name.contains("bolt"))
		{
			return SlayerMethodRules.InventoryGroup.RUNES_AMMO;
		}
		if (name.contains("saradomin brew"))
		{
			return SlayerMethodRules.InventoryGroup.FOOD;
		}
		if (name.contains("heart") || name.contains("combat potion")
			|| name.contains("ranging potion") || name.contains("magic potion")
			|| name.contains("bastion potion")
			|| name.contains("battlemage potion")
			|| name.contains("ancient brew")
			|| name.contains("forgotten brew"))
		{
			return SlayerMethodRules.InventoryGroup.BOOST;
		}
		if (name.contains("antivenom") || name.contains("anti venom")
			|| name.contains("antipoison") || name.contains("stamina")
			|| name.contains("antifire"))
		{
			return SlayerMethodRules.InventoryGroup.PROTECTION;
		}
		if (name.contains("super restore") || name.contains("prayer potion")
			|| name.contains("prayer restoration")
			|| name.contains("prayer regeneration"))
		{
			return SlayerMethodRules.InventoryGroup.RESTORE;
		}
		if (isFoodName(name))
		{
			return SlayerMethodRules.InventoryGroup.FOOD;
		}
		return SlayerMethodRules.InventoryGroup.OTHER;
	}

	private static SlayerLoadoutItem.SwitchStyle inferSwitchStyle(
		final SlayerMethodRules.RequiredItem required,
		final CombatStyle fallbackStyle)
	{
		if (required == null)
		{
			return switchStyle(fallbackStyle);
		}

		String description = normalize(required.getDisplayName());
		for (final String alternative : required.getAlternatives())
		{
			description += " " + normalize(alternative);
		}

		/* Weapon-type words beat material names such as "magic shortbow". */
		if (description.contains("blowpipe")
			|| description.contains("shortbow")
			|| description.contains("longbow")
			|| description.contains("crossbow")
			|| description.contains("ballista")
			|| description.contains(" ranged ")
			|| description.startsWith("ranged ")
			|| description.contains(" range switch")
			|| description.contains("masori")
			|| description.contains("armadyl")
			|| description.contains("karil"))
		{
			return SlayerLoadoutItem.SwitchStyle.RANGED;
		}
		if (description.contains(" magic ")
			|| description.startsWith("magic ")
			|| description.contains(" mage ")
			|| description.startsWith("mage ")
			|| description.contains("magicks")
			|| description.contains("ancient")
			|| description.contains("sceptre")
			|| description.contains("staff")
			|| description.contains("wand")
			|| description.contains("ancestral")
			|| description.contains("virtus")
			|| description.contains("ahrim")
			|| description.contains("bloodbark"))
		{
			return SlayerLoadoutItem.SwitchStyle.MAGIC;
		}
		if (description.contains(" melee ")
			|| description.startsWith("melee ")
			|| description.contains("scythe")
			|| description.contains("godsword")
			|| description.contains("whip")
			|| description.contains("claws")
			|| description.contains("defender")
			|| description.contains("torva")
			|| description.contains("bandos"))
		{
			return SlayerLoadoutItem.SwitchStyle.MELEE;
		}
		return switchStyle(fallbackStyle);
	}

	private static SlayerLoadoutItem.SwitchStyle switchStyle(
		final CombatStyle style)
	{
		if (style == CombatStyle.MAGIC)
		{
			return SlayerLoadoutItem.SwitchStyle.MAGIC;
		}
		if (style == CombatStyle.RANGED)
		{
			return SlayerLoadoutItem.SwitchStyle.RANGED;
		}
		if (style == CombatStyle.MELEE)
		{
			return SlayerLoadoutItem.SwitchStyle.MELEE;
		}
		return SlayerLoadoutItem.SwitchStyle.OTHER;
	}

	private static boolean isFoodName(final String name)
	{
		return name.contains("anglerfish") || name.contains("manta ray")
			|| name.contains("dark crab") || name.contains("shark")
			|| name.contains("sea turtle") || name.contains("karambwan")
			|| name.contains("monkfish") || name.contains("high healing food");
	}

	private List<SlayerLoadoutItem>
		buildOptionalLayout(
			final String task,
			final SlayerTaskStrategy strategy,
			final List<OwnedItem> allOwned,
			final OwnedItem selectedWeapon)
	{
		final List<SlayerLoadoutItem> optional =
			new ArrayList<>();

		addBlowpipeDartRecommendation(
			optional,
			strategy,
			selectedWeapon,
			allOwned
		);

		if (strategy != null)
		{
			for (final String itemName
				: strategy.getOptionalItemPriorities())
			{
				if (!achievementDiaries.allowsLoadoutReward(
					itemName, Collections.singletonList(itemName)
				))
				{
					continue;
				}
				addOwnedOptional(optional, allOwned, itemName);
			}
		}

		addOwnedOptional(optional, allOwned, "bracelet of slaughter");
		addOwnedOptional(optional, allOwned, "expeditious bracelet");
		addOwnedOptional(optional, allOwned, "slayer ring");

		if (achievementDiaries.unlocksAshSanctifier()
			&& isAshSanctifierTask(task))
		{
			addOwnedOptional(optional, allOwned, "ash sanctifier");
		}

		/* Bonecrusher is useful only where the target actually drops bones. */
		if (achievementDiaries.unlocksBonecrusher()
			&& strategy != null && !strategy.isBoss()
			&& isBonecrusherTask(task))
		{
			addOwnedOptional(optional, allOwned, "bonecrusher");
		}
		addOwnedOptional(optional, allOwned, "herb sack");
		addOwnedOptional(optional, allOwned, "gem bag");

		return deduplicateItems(optional);
	}

	private static boolean isAshSanctifierTask(final String taskName)
	{
		final String task = normalize(taskName);
		return task.contains("demon") || task.contains("hellhound")
			|| task.contains("bloodveld") || task.contains("nechryael")
			|| task.contains("smoke devil") || task.contains("pyrefiend")
			|| task.contains("fiend") || task.contains("cerberus");
	}

	private static boolean isBonecrusherTask(final String taskName)
	{
		final String task = normalize(taskName);
		return task.contains("dragon") || task.contains("wyvern")
			|| task.contains("wyrm") || task.contains("drake")
			|| task.contains("hydra") || task.contains("dagannoth")
			|| task.contains("kalphite") || task.contains("basilisk")
			|| task.contains("giant") || task.contains("troll")
			|| task.contains("bat") || task.contains("mole")
			|| task.contains("ankou");
	}

	private void addBlowpipeDartRecommendation(
		final List<SlayerLoadoutItem> destination,
		final SlayerTaskStrategy strategy,
		final OwnedItem selectedWeapon,
		final List<OwnedItem> allOwned)
	{
		if (destination == null
			|| selectedWeapon == null
			|| !selectedWeapon.matchesName("blowpipe"))
		{
			return;
		}

		final OwnedItem darts =
			strategy != null
				&& strategy.getCostPolicy()
					== SlayerTaskStrategy.CostPolicy.EFFICIENT
				? chooseEfficientBlowpipeDarts(allOwned)
				: firstExactOwned(
					allOwned,
					"dragon dart",
					"amethyst dart",
					"rune dart",
					"adamant dart"
				);

		if (darts != null)
		{
			destination.add(darts.toLoadoutItem(1000));
		}
	}

	private OwnedItem chooseEfficientBlowpipeDarts(
		final List<OwnedItem> allOwned)
	{
		final OwnedItem amethyst =
			firstExactOwned(allOwned, "amethyst dart");
		final OwnedItem rune =
			firstExactOwned(allOwned, "rune dart");

		if (amethyst != null && rune != null)
		{
			final int amethystPrice = safeItemPrice(amethyst);
			final int runePrice = safeItemPrice(rune);

			if (amethystPrice > 0 && runePrice > 0)
			{
				return amethystPrice <= runePrice
					? amethyst
					: rune;
			}

			// With no live price available, preserve the stronger economical tier.
			return amethyst;
		}

		if (amethyst != null)
		{
			return amethyst;
		}
		if (rune != null)
		{
			return rune;
		}

		/*
		 * Profit never recommends Dragon darts as the economical fallback.
		 * If no cheaper loose stack is owned, leave the layout slot empty;
		 * the Bank Tag panel will still name Amethyst darts as the suggested
		 * switch without inserting an unowned item.
		 */
		return firstExactOwned(
			allOwned,
			"adamant dart"
		);
	}

	private int safeItemPrice(final OwnedItem item)
	{
		if (item == null || itemManager == null)
		{
			return 0;
		}

		try
		{
			return Math.max(0, itemManager.getItemPrice(item.itemId));
		}
		catch (RuntimeException ignored)
		{
			return 0;
		}
	}

	private static OwnedItem firstExactOwned(
		final List<OwnedItem> allOwned,
		final String... exactNames)
	{
		if (allOwned == null || exactNames == null)
		{
			return null;
		}

		for (final String exactName : exactNames)
		{
			/* Exact owned IDs/doses always beat variation aliases. */
			for (final OwnedItem item : allOwned)
			{
				if (item.matchesExactDisplayName(exactName))
				{
					return item;
				}
			}

			/* Ornament, charge, and skin equivalence remains a fallback. */
			for (final OwnedItem item : allOwned)
			{
				if (item.matchesExactName(exactName))
				{
					return item;
				}
			}
		}

		return null;
	}

	private static List<SlayerLoadoutItem> deduplicateItems(
		final List<SlayerLoadoutItem> source)
	{
		final List<SlayerLoadoutItem> result = new ArrayList<>();
		final Set<String> seen = new HashSet<>();
		for (final SlayerLoadoutItem item : source)
		{
			if (item == null)
			{
				continue;
			}
			final String key = item.hasItemId()
				? "id:" + item.getItemId()
				: "name:" + normalize(item.getDisplayName());
			if (seen.add(key))
			{
				result.add(item);
			}
		}
		return result;
	}

	private static void addOwnedOptional(
		final List<SlayerLoadoutItem> destination,
		final List<OwnedItem> allOwned,
		final String... alternatives)
	{
		final OwnedItem owned =
			findPreferred(allOwned, alternatives);

		if (owned != null)
		{
			destination.add(owned.toLoadoutItem(1));
		}
	}

	private SlayerLoadoutItem chooseTeleport(
		final String task,
		final String location,
		final String travel,
		final List<OwnedItem> allOwned,
		final boolean bankScanned)
	{
		final String combined = location + " " + travel;
		final String normalizedTask = normalize(task);
		if (normalizedTask.equals("royal titans"))
		{
			return chooseItem("Giantsoul amulet", 1, allOwned, bankScanned, "giantsoul amulet", "dramen staff", "lunar staff");
		}
		if (normalizedTask.equals("tormented demons"))
		{
			final OwnedItem direct = firstExactDisplayOwned(
				allOwned, "guthixian temple teleport"
			);
			return direct == null
				? chooseItem(
					"Games necklace / POH jewellery box",
					1,
					allOwned,
					bankScanned,
					"games necklace"
				)
				: direct.toLoadoutItem(1);
		}
		final List<SlayerTravelRouteCatalog.Option> reviewedOptions =
			SlayerTravelRouteCatalog.fallbacksFor(location);
		if (!reviewedOptions.isEmpty())
		{
			final List<String> alternatives = new ArrayList<>();
			for (final SlayerTravelRouteCatalog.Option option : reviewedOptions)
			{
				if (achievementDiaries.allowsTravelItem(option.getItemFamily()))
				{
					alternatives.add(option.getItemFamily());
				}
			}
			if (alternatives.isEmpty())
			{
				/*
				 * Lumbridge Elite makes fairy rings staffless. When every
				 * reviewed option is a dramen/lunar staff, there is no travel
				 * inventory item to add rather than a missing-item placeholder.
				 */
				return null;
			}
			SlayerTravelRouteCatalog.Option preferred = reviewedOptions.get(0);
			for (final SlayerTravelRouteCatalog.Option option : reviewedOptions)
			{
				if (achievementDiaries.allowsTravelItem(option.getItemFamily()))
				{
					preferred = option;
					break;
				}
			}
			return chooseItem(
				preferred.getItemFamily() + " -> " + preferred.getDestination(),
				1,
				allOwned,
				bankScanned,
				alternatives
			);
		}
		if (combined.contains("cowbell"))
		{
			return chooseItem("Cowbell amulet", 1, allOwned, bankScanned, "cowbell amulet");
		}
		if (combined.contains("giantsoul"))
		{
			return chooseItem("Giantsoul amulet", 1, allOwned, bankScanned, "giantsoul amulet", "dramen staff", "lunar staff");
		}
		if (combined.contains("guthixian temple"))
		{
			return chooseItem("Guthixian temple teleport", 1, allOwned, bankScanned, "guthixian temple teleport", "games necklace");
		}
		if (combined.contains("key master"))
		{
			return chooseItem("Key master teleport", 1, allOwned, bankScanned, "key master teleport", "games necklace");
		}
		if (combined.contains("barrows teleport"))
		{
			return chooseItem("Barrows teleport", 1, allOwned, bankScanned, "barrows teleport", "morytania legs 3", "morytania legs 4");
		}
		if (combined.contains("ring of shadows") || combined.contains("ancient vault"))
		{
			return chooseItem("Ring of shadows", 1, allOwned, bankScanned, "ring of shadows");
		}
		if (combined.contains("burning amulet") || combined.contains("wilderness obelisk"))
		{
			return chooseItem("Wilderness travel", 1, allOwned, bankScanned, "burning amulet", "royal seed pod", "seed pod");
		}
		if (combined.contains("digsite pendant"))
		{
			return chooseItem("Digsite pendant", 1, allOwned, bankScanned, "digsite pendant");
		}
		if (combined.contains("drakan"))
		{
			return chooseItem("Drakan's medallion", 1, allOwned, bankScanned, "drakan s medallion");
		}
		if (combined.contains("varrock teleport"))
		{
			return chooseItem("Varrock teleport", 1, allOwned, bankScanned, "varrock teleport", "varrock tablet");
		}
		if (combined.contains("amulet of glory"))
		{
			return chooseItem("Amulet of glory", 1, allOwned, bankScanned, "amulet of glory");
		}
		if (combined.contains("ring of dueling"))
		{
			return chooseItem("Ring of dueling", 1, allOwned, bankScanned, "ring of dueling");
		}
		if (combined.contains("skills necklace"))
		{
			return chooseItem("Skills necklace", 1, allOwned, bankScanned, "skills necklace");
		}
		if (combined.contains("combat bracelet"))
		{
			return chooseItem("Combat bracelet", 1, allOwned, bankScanned, "combat bracelet");
		}
		if (combined.contains("ectophial"))
		{
			return chooseItem("Ectophial", 1, allOwned, bankScanned, "ectophial");
		}
		if (combined.contains("mort ton teleport"))
		{
			return chooseItem("Mort'ton teleport", 1, allOwned, bankScanned, "mort ton teleport");
		}
		if (combined.contains("pollnivneach teleport"))
		{
			return chooseItem("Pollnivneach teleport", 1, allOwned, bankScanned, "pollnivneach teleport");
		}
		if (combined.contains("zul andra"))
		{
			return chooseItem("Zul-andra teleport", 1, allOwned, bankScanned, "zul andra teleport");
		}

		if (combined.contains("catacomb")
			|| combined.contains("xeric"))
		{
			return chooseItem(
				"Xeric's talisman",
				1,
				allOwned,
				bankScanned,
				"xeric s talisman"
			);
		}

		if (combined.contains("karuulm")
			|| combined.contains("rada"))
		{
			return chooseItem(
				"Rada's blessing",
				1,
				allOwned,
				bankScanned,
				"rada s blessing"
			);
		}

		if (combined.contains("fremennik")
			|| combined.contains("slayer cave"))
		{
			return chooseItem(
				"Slayer ring",
				1,
				allOwned,
				bankScanned,
				"slayer ring"
			);
		}

		if (combined.contains("fairy ring"))
		{
			if (achievementDiaries.hasTier("lumbridge", 4))
			{
				return null;
			}
			return chooseItem(
				"Dramen or lunar staff",
				1,
				allOwned,
				bankScanned,
				"lunar staff",
				"dramen staff"
			);
		}

		return chooseItem(
			"Teleport out",
			1,
			allOwned,
			bankScanned,
			"max cape",
			"construction cape",
			"teleport to house",
			"house tab",
			"slayer ring",
			"ring of dueling",
			"games necklace"
		);
	}

	private static OwnedItem firstExactDisplayOwned(
		final List<OwnedItem> allOwned,
		final String... exactNames)
	{
		if (allOwned == null || exactNames == null)
		{
			return null;
		}

		for (final String exactName : exactNames)
		{
			for (final OwnedItem item : allOwned)
			{
				if (item != null
					&& item.matchesExactDisplayName(exactName))
				{
					return item;
				}
			}
		}

		return null;
	}

	private static SlayerLoadoutItem chooseItem(
		final String displayName,
		final int quantity,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final List<String> alternatives)
	{
		return chooseItem(
			displayName, quantity, allOwned, bankScanned, alternatives, false
		);
	}

	private static SlayerLoadoutItem chooseItem(
		final String displayName,
		final int quantity,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final List<String> alternatives,
		final boolean equipmentProgression)
	{
		final OwnedItem owned = findPreferredInventoryItem(
			allOwned,
			alternatives,
			equipmentProgression
		);

		return owned == null
			? missingItem(displayName, quantity, bankScanned)
			: owned.toLoadoutItem(quantity);
	}

	/**
	 * Inventory consumables keep their exact owned dose globally. Generic
	 * potion-family alternatives resolve 4 -> 3 -> 2 -> 1 before variation
	 * aliases are considered. Equipment equivalence remains handled by the
	 * existing matcher.
	 */
	private static OwnedItem findPreferredInventoryItem(
		final List<OwnedItem> allOwned,
		final List<String> alternatives)
	{
		return findPreferredInventoryItem(allOwned, alternatives, false);
	}

	private static OwnedItem findPreferredInventoryItem(
		final List<OwnedItem> allOwned,
		final List<String> alternatives,
		final boolean equipmentProgression)
	{
		if (alternatives == null)
		{
			return null;
		}
		if (equipmentProgression)
		{
			return findPreferredInventoryEquipment(allOwned, alternatives);
		}

		final List<String> potionFamilies = new ArrayList<>();
		final List<String> nonPotionAlternatives = new ArrayList<>();

		/*
		 * Preserve deliberately authored reusable-item precedence. For example,
		 * a saturated or imbued heart must be considered before the fallback
		 * potion families that follow it in the magic-boost policy.
		 */
		for (final String alternative : alternatives)
		{
			final String normalized = normalize(alternative);
			if (normalized.isEmpty())
			{
				continue;
			}
			if (isPotionFamilyName(normalized))
			{
				break;
			}
			final OwnedItem exact = firstExactDisplayOwned(
				allOwned, normalized
			);
			if (exact != null)
			{
				return exact;
			}
		}

		/*
		 * An explicitly authored dose is an exact strategy constraint. Generic
		 * potion families, however, compete by DOSE FIRST across all equivalent
		 * families. This prevents e.g. Divine bastion(2) from beating Bastion(4)
		 * merely because the divine family happened to be listed first.
		 */
		for (final String alternative : alternatives)
		{
			final String normalized = normalize(alternative);
			if (normalized.isEmpty())
			{
				continue;
			}

			if (hasExplicitPotionDose(normalized))
			{
				final OwnedItem exact = firstExactDisplayOwned(allOwned, normalized);
				if (exact != null)
				{
					return exact;
				}
			}
			else if (isPotionFamilyName(normalized))
			{
				potionFamilies.add(stripPotionDose(normalized));
			}
			else
			{
				nonPotionAlternatives.add(normalized);
			}
		}

		OwnedItem bestPotion = null;
		int bestUsefulDoseUnits = -1;
		int bestFamilyPreference = Integer.MAX_VALUE;
		for (int familyIndex = 0;
			familyIndex < potionFamilies.size();
			familyIndex++)
		{
			final String family = potionFamilies.get(familyIndex);
			for (int dose = 4; dose >= 1; dose--)
			{
				final OwnedItem owned = firstExactDisplayOwned(
					allOwned, family + " " + dose
				);
				if (owned == null)
				{
					continue;
				}
				final int usefulDoseUnits =
					SlayerPotionPolicy.effectiveDoseUnits(family, dose);
				if (usefulDoseUnits > bestUsefulDoseUnits
					|| (usefulDoseUnits == bestUsefulDoseUnits
						&& familyIndex < bestFamilyPreference))
				{
					bestPotion = owned;
					bestUsefulDoseUnits = usefulDoseUnits;
					bestFamilyPreference = familyIndex;
				}
				/* This family cannot have a fuller owned dose. */
				break;
			}
		}
		if (bestPotion != null)
		{
			return bestPotion;
		}

		for (final String alternative : nonPotionAlternatives)
		{
			final OwnedItem exact = firstExactDisplayOwned(
				allOwned, alternative
			);
			if (exact != null)
			{
				return exact;
			}
		}

		return findPreferred(allOwned, alternatives);
	}

	/**
	 * Equipment switches are authored from strongest to weakest. Resolve each
	 * tier completely before considering the next one, including cosmetic,
	 * charged, locked and max-cape variants that retain that tier's stats.
	 */
	private static OwnedItem findPreferredInventoryEquipment(
		final List<OwnedItem> allOwned,
		final List<String> progression)
	{
		if (allOwned == null || progression == null)
		{
			return null;
		}

		for (final String tier : progression)
		{
			final String normalizedTier = normalize(tier);
			if (normalizedTier.isEmpty())
			{
				continue;
			}

			final OwnedItem exact = firstExactDisplayOwned(
				allOwned, normalizedTier
			);
			if (exact != null)
			{
				return exact;
			}

			for (final OwnedItem item : allOwned)
			{
				if (item != null && item.matchesEquipmentProgression(normalizedTier))
				{
					return item;
				}
			}
		}
		return null;
	}

	private static boolean hasExplicitPotionDose(final String name)
	{
		return isPotionFamilyName(name) && name.matches(".*\\s[1-4]$");
	}

	private static boolean isPotionFamilyName(final String name)
	{
		return name.contains("potion")
			|| name.contains("super restore")
			|| name.contains("brew")
			|| name.contains("serum")
			|| name.contains("antidote")
			|| name.contains("antivenom")
			|| name.contains("anti venom")
			|| name.contains("antipoison");
	}

	private static SlayerLoadoutItem chooseItem(
		final String displayName,
		final int quantity,
		final List<OwnedItem> allOwned,
		final boolean bankScanned,
		final String... alternatives)
	{
		final List<String> values = new ArrayList<>();
		Collections.addAll(values, alternatives);

		return chooseItem(
			displayName,
			quantity,
			allOwned,
			bankScanned,
			values
		);
	}

	private static void addInventoryItem(
		final List<SlayerLoadoutItem> layout,
		final SlayerLoadoutItem item)
	{
		if (item != null && layout.size() < 28)
		{
			layout.add(item);
		}
	}

	private static SlayerLoadoutItem missingItem(
		final String displayName,
		final boolean bankScanned)
	{
		return missingItem(displayName, 1, bankScanned);
	}

	private static SlayerLoadoutItem missingItem(
		final String displayName,
		final int quantity,
		final boolean bankScanned)
	{
		return new SlayerLoadoutItem(
			displayName,
			-1,
			Math.max(1, quantity),
			bankScanned
				? SlayerLoadoutItem.Status.MISSING
				: SlayerLoadoutItem.Status.UNKNOWN
		);
	}

	private static Requirement requirementForSlot(
		final List<Requirement> requirements,
		final EquipmentInventorySlot slot)
	{
		for (final Requirement requirement : requirements)
		{
			if (!requirement.equipment)
			{
				continue;
			}

			final String name =
				normalize(requirement.displayName);

			if (slot == EquipmentInventorySlot.HEAD
				&& (name.contains("earmuff")
					|| name.contains("face mask")
					|| name.contains("nose peg")
					|| name.contains("spiny helmet")
					|| name.contains("goggle")))
			{
				return requirement;
			}

			if (slot == EquipmentInventorySlot.AMULET
				&& name.contains("witchwood"))
			{
				return requirement;
			}

			if (slot == EquipmentInventorySlot.BOOTS
				&& name.contains("boots"))
			{
				return requirement;
			}

			if (slot == EquipmentInventorySlot.GLOVES
				&& name.contains("gloves"))
			{
				return requirement;
			}

			if (slot == EquipmentInventorySlot.SHIELD
				&& (name.contains("shield")
					|| name.contains("bug lantern")))
			{
				return requirement;
			}

			if (slot == EquipmentInventorySlot.WEAPON
				&& (name.contains("weapon")
					|| name.contains("staff")))
			{
				return requirement;
			}

			if (slot == EquipmentInventorySlot.AMMO
				&& (name.contains("ammunition")
					|| name.contains("bolts")
					|| name.contains("arrows")))
			{
				return requirement;
			}
		}

		return null;
	}

	private static OwnedItem findEquippedSlot(
		final List<OwnedItem> equipped,
		final EquipmentInventorySlot slot)
	{
		final int slotIndex = slot.getSlotIdx();

		for (final OwnedItem item : equipped)
		{
			if (item.equipmentSlot == slotIndex)
			{
				return item;
			}
		}

		return null;
	}

	private static OwnedItem findPreferred(
		final List<OwnedItem> owned,
		final List<String> alternatives)
	{
		if (owned == null || alternatives == null)
		{
			return null;
		}

		for (final String alternative : alternatives)
		{
			final String fragment = normalize(alternative);
			if (fragment.isEmpty())
			{
				continue;
			}

			/*
			 * Global item-selection rule: an exact owned item/dose wins before
			 * any variation alias. This prevents lower-dose potions from
			 * satisfying a 4-dose request while preserving ornament/skin
			 * equivalence as a fallback for equipment.
			 */
			for (final OwnedItem item : owned)
			{
				if (item != null
					&& item.matchesExactDisplayName(fragment))
				{
					return item;
				}
			}

			for (final OwnedItem item : owned)
			{
				if (item != null && item.matchesName(fragment))
				{
					return item;
				}
			}
		}

		return null;
	}

	private static OwnedItem findPreferred(
		final List<OwnedItem> owned,
		final String... alternatives)
	{
		final List<String> values = new ArrayList<>();
		Collections.addAll(values, alternatives);
		return findPreferred(owned, values);
	}

	private static String[] weaponFragments(
		final CombatStyle style)
	{
		if (style == CombatStyle.MAGIC)
		{
			return new String[]{
				"tumeken",
				"sanguinesti",
				"trident",
				"nightmare staff",
				"ancient sceptre",
				"kodai",
				"wand",
				"staff"
			};
		}

		if (style == CombatStyle.RANGED)
		{
			return new String[]{
				"twisted bow",
				"bow of faerdhinen",
				"blowpipe",
				"crossbow",
				"bow",
				"atlatl"
			};
		}

		return new String[]{
			"scythe",
			"soulreaper axe",
			"osmumten",
			"fang",
			"rapier",
			"abyssal whip",
			"whip",
			"lance",
			"mace",
			"sword",
			"axe"
		};
	}

	private static String[] bodyFragments(
		final CombatStyle style)
	{
		if (style == CombatStyle.MAGIC)
		{
			return new String[]{
				"ancestral robe top",
				"virtus robe top",
				"ahrim s robetop"
			};
		}

		if (style == CombatStyle.RANGED)
		{
			return new String[]{
				"masori body",
				"armadyl chestplate",
				"karil s leathertop"
			};
		}

		return new String[]{
			"torva platebody",
			"bandos chestplate",
			"fighter torso",
			"proselyte hauberk"
		};
	}

	private static String[] legsFragments(
		final CombatStyle style)
	{
		if (style == CombatStyle.MAGIC)
		{
			return new String[]{
				"ancestral robe bottom",
				"virtus robe bottom",
				"ahrim s robeskirt"
			};
		}

		if (style == CombatStyle.RANGED)
		{
			return new String[]{
				"masori chaps",
				"armadyl chainskirt",
				"karil s leatherskirt"
			};
		}

		return new String[]{
			"torva platelegs",
			"bandos tassets",
			"proselyte cuisse"
		};
	}

	private static String[] shieldFragments(
		final CombatStyle style)
	{
		if (style == CombatStyle.MAGIC)
		{
			return new String[]{
				"elidinis ward",
				"mage s book",
				"book of darkness"
			};
		}

		if (style == CombatStyle.RANGED)
		{
			return new String[]{
				"twisted buckler",
				"dragonfire ward",
				"book of law"
			};
		}

		return new String[]{
			"avernic defender",
			"dragon defender",
			"defender"
		};
	}

	private static String[] bootsFragments(
		final CombatStyle style)
	{
		if (style == CombatStyle.MAGIC)
		{
			return new String[]{
				"avernic treads",
				"eternal boots",
				"infinity boots"
			};
		}

		if (style == CombatStyle.RANGED)
		{
			return new String[]{
				"avernic treads",
				"pegasian boots",
				"ranger boots"
			};
		}

		return new String[]{
			"avernic treads",
			"primordial boots",
			"dragon boots"
		};
	}

	private static String[] ringFragments(
		final CombatStyle style)
	{
		if (style == CombatStyle.MAGIC)
		{
			return new String[]{
				"magus ring",
				"seers ring",
				"lightbearer"
			};
		}

		if (style == CombatStyle.RANGED)
		{
			return new String[]{
				"venator ring",
				"archers ring",
				"lightbearer"
			};
		}

		return new String[]{
			"ultor ring",
			"berserker ring",
			"lightbearer"
		};
	}

	private static String buildEquipmentText(
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<Requirement> requirements)
	{
		final List<String> parts = new ArrayList<>();
		parts.add("Slayer helmet or black mask");

		if (strategy != null)
		{
			parts.add(strategy.getMethod());
		}
		else
		{
			switch (style)
			{
				case MAGIC:
					parts.add("magic-damage weapon and prayer/magic gear");
					break;
				case RANGED:
					parts.add("ranged weapon, ammo, and ranged armour");
					break;
				case MELEE:
					parts.add("melee weapon and strength gear");
					break;
				case FLEXIBLE:
				default:
					parts.add("task-appropriate combat switches");
					break;
			}
		}

		for (final Requirement requirement : requirements)
		{
			if (requirement.equipment)
			{
				parts.add(requirement.displayName);
			}
		}

		return join(parts, "; ");
	}

	private static String buildInventoryText(
		final SlayerTaskStrategy strategy,
		final CombatStyle style,
		final List<Requirement> requirements,
		final boolean cannonSuggested,
		final int recommendedFoodSlots)
	{
		final List<String> parts = new ArrayList<>();

		final String foodText = recommendedFoodSlots > 0
			? " and " + recommendedFoodSlots + " food slot"
				+ (recommendedFoodSlots == 1 ? "" : "s")
			: "";

		switch (style)
		{
			case MAGIC:
				parts.add("runes or rune pouch");
				parts.add("Magic boost and prayer potions" + foodText);
				break;
			case RANGED:
				parts.add("ammo");
				parts.add("ranging/prayer potions" + foodText);
				break;
			case MELEE:
				parts.add("super combat/prayer potions" + foodText);
				break;
			case FLEXIBLE:
			default:
				parts.add("combat-switch potions and prayer supplies" + foodText);
				break;
		}

		if (recommendedFoodSlots == 0
			&& strategy != null
			&& (strategy.getDamageProfile()
				== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
				|| strategy.getDamageProfile()
				== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING))
		{
			parts.add(
				"No food: the reviewed method expects zero incoming damage "
					+ "while its protection method is maintained"
			);
		}

		if (strategy != null && strategy.needsAntivenom())
		{
			parts.add("antivenom");
		}
		if (strategy != null && strategy.needsStamina())
		{
			parts.add("extended stamina potion (regular stamina fallback)");
		}
		if (cannonSuggested)
		{
			parts.add("multicannon pieces and cannonballs");
		}

		for (final Requirement requirement : requirements)
		{
			if (!requirement.equipment)
			{
				parts.add(requirement.displayName);
			}
		}

		return join(parts, "; ");
	}

	private static String buildOwnedText(
		final CombatStyle style,
		final List<Requirement> requirements,
		final boolean cannonSuggested,
		final Set<String> inventory,
		final Set<String> equipment,
		final Set<String> bank,
		final boolean bankScanned)
	{
		final List<String> status = new ArrayList<>();
		status.add(styleStatus(style, equipment));

		for (final Requirement requirement : requirements)
		{
			status.add(
				requirement.displayName
					+ ": "
					+ availability(
						requirement,
						inventory,
						equipment,
						bank,
						bankScanned
					)
			);
		}

		if (cannonSuggested)
		{
			status.add(
				"Cannon: "
					+ cannonAvailability(
						inventory,
						equipment,
						bank,
						bankScanned
					)
			);
		}

		if (!bankScanned)
		{
			status.add("Bank not scanned — open it once");
		}
		else
		{
			status.add("Bank cache ready");
		}

		return join(status, "; ");
	}

	private static String availability(
		final Requirement requirement,
		final Set<String> inventory,
		final Set<String> equipment,
		final Set<String> bank,
		final boolean bankScanned)
	{
		if (containsAny(equipment, requirement.alternatives))
		{
			return "equipped";
		}
		if (containsAny(inventory, requirement.alternatives))
		{
			return "carried";
		}
		if (containsAny(bank, requirement.alternatives))
		{
			return "in bank";
		}
		return bankScanned ? "missing" : "not carried; bank unknown";
	}

	private static boolean hybridUsesCombatMagic(
		final SlayerTaskStrategy strategy)
	{
		if (strategy == null)
		{
			return false;
		}
		final String text = normalize(
			strategy.getMethod() + " " + strategy.getRationale()
		);
		return text.contains("magic")
			|| text.contains("mage")
			|| text.contains("powered staff")
			|| text.contains("tumeken")
			|| text.contains("trident");
	}

	private static String cannonAvailability(
		final Set<String> inventory,
		final Set<String> equipment,
		final Set<String> bank,
		final boolean bankScanned)
	{
		final Set<String> available = new HashSet<>();
		available.addAll(inventory);
		available.addAll(equipment);
		available.addAll(bank);

		final boolean base = containsText(available, "cannon base");
		final boolean stand = containsText(available, "cannon stand");
		final boolean barrels = containsText(available, "cannon barrels");
		final boolean furnace = containsText(available, "cannon furnace");
		final boolean cannonballs = containsText(available, "cannonball");

		if (base && stand && barrels && furnace && cannonballs)
		{
			return "all pieces and ammo found";
		}
		if (!bankScanned)
		{
			return "not fully carried; bank unknown";
		}

		final List<String> missing = new ArrayList<>();
		if (!(base && stand && barrels && furnace))
		{
			missing.add("pieces");
		}
		if (!cannonballs)
		{
			missing.add("cannonballs");
		}
		return "missing " + join(missing, " and ");
	}

	private static String styleStatus(
		final CombatStyle style,
		final Set<String> equipment)
	{
		if (style == CombatStyle.FLEXIBLE)
		{
			return equipment.isEmpty()
				? "Equipment scan unavailable"
				: "Equipped setup detected";
		}

		final boolean detected;
		switch (style)
		{
			case MAGIC:
				detected = containsAnyText(
					equipment,
					"staff", "wand", "trident", "sceptre", "sanguinesti",
					"tumeken", "ancestral", "virtus", "ahrim", "occult"
				);
				break;
			case RANGED:
				detected = containsAnyText(
					equipment,
					"bow", "crossbow", "blowpipe", "atlatl", "masori",
					"armadyl", "karil", "ava", "quiver"
				);
				break;
			case MELEE:
			default:
				detected = containsAnyText(
					equipment,
					"scimitar", "sword", "whip", "rapier", "mace", "axe",
					"lance", "scythe", "fang", "torva", "bandos", "defender"
				);
				break;
		}

		return detected
			? "Equipped " + style.label + " setup detected"
			: "No clear " + style.label + " setup equipped";
	}

	private List<Requirement> requirementsFor(
		final String task,
		final String location,
		final CombatStyle style,
		final int remainingKills,
		final boolean desertEliteDiaryComplete)
	{
		final List<Requirement> requirements = new ArrayList<>();
		final String combined = task + " " + location;

		if (requiresKalphiteQueenRopesForRegression(
			task, location, desertEliteDiaryComplete
		))
		{
			requirements.add(inventoryRequirement(2, "Rope", "rope"));
		}

		if (requiresGenericRockHammerForRegression(task))
		{
			requirements.add(inventoryRequirement(
				"Rock hammer", "rock hammer"
			));
		}
		if (task.contains("lizard")
			&& !task.contains("lizardmen"))
		{
			requirements.add(inventoryRequirement(
				consumableFinisherQuantity(remainingKills),
				"Ice cooler", "ice cooler"
			));
		}
		if (task.contains("rockslug"))
		{
			requirements.add(inventoryRequirement(
				consumableFinisherQuantity(remainingKills),
				"Bag of salt", "bag of salt"
			));
		}
		if (task.contains("molanisk"))
		{
			requirements.add(inventoryRequirement(
				"Slayer bell", "slayer bell"
			));
		}
		if (task.contains("mogre"))
		{
			requirements.add(inventoryRequirement(
				consumableFinisherQuantity(remainingKills),
				"Fishing explosive", "fishing explosive"
			));
		}
		if (task.contains("brine rat"))
		{
			requirements.add(inventoryRequirement(
				"Spade", "spade"
			));
		}
		if (task.contains("cave bug") || task.contains("cave slime"))
		{
			requirements.add(inventoryRequirement(
				"Safe light source",
				"bruma torch", "bullseye lantern", "emerald lantern",
				"sapphire lantern", "oil lantern", "candle lantern"
			));
		}
		if (task.contains("cave crawler") || task.contains("cave slime"))
		{
			requirements.add(inventoryRequirement(
				"Poison protection",
				"antidote plus plus", "antidote plus", "superantipoison",
				"antipoison"
			));
		}
		if (task.equals("crocodiles") || task.equals("crocodile")
			|| (task.equals("lizards") || task.equals("lizard"))
			|| task.equals("bandits") || task.equals("bandit"))
		{
			requirements.add(inventoryRequirement(
				"Desert heat protection",
				"circlet of water", "desert amulet 4", "waterskin 4",
				"waterskin 3", "waterskin 2", "waterskin 1"
			));
		}
		if (task.contains("sea snake"))
		{
			requirements.add(inventoryRequirement(
				"Poison protection",
				"antidote plus plus", "antidote plus", "superantipoison",
				"antipoison"
			));
		}
		if (task.contains("sourhog"))
		{
			requirements.add(equipmentRequirement(
				"Reinforced goggles or Slayer helmet",
				"reinforced goggles", "slayer helmet"
			));
		}
		if (task.equals("giant mole"))
		{
			requirements.add(inventoryRequirement("Spade", "spade"));
			requirements.add(inventoryRequirement(
				"Safe light source",
				"bruma torch", "bullseye lantern", "emerald lantern",
				"sapphire lantern", "oil lantern", "candle lantern"
			));
		}
		if (task.contains("deranged archaeologist"))
		{
			requirements.add(inventoryRequirement(
				"Swamp-vine axe",
				"dragon axe", "rune axe", "adamant axe", "mithril axe",
				"black axe", "steel axe", "iron axe", "bronze axe"
			));
		}
		if (task.contains("general graardor"))
		{
			requirements.add(inventoryRequirement(
				"Hammer", "hammer", "imcando hammer"
			));
		}
		if (task.contains("kree arra"))
		{
			requirements.add(inventoryRequirement(
				"Mith grapple", "mith grapple"
			));
		}
		if (location.contains("brimhaven dungeon"))
		{
			requirements.add(inventoryRequirement(
				"Axe",
				"infernal axe", "crystal axe", "dragon axe", "rune axe",
				"adamant axe", "mithril axe", "steel axe", "iron axe", "bronze axe"
			));
		}
		if (task.contains("cave horror"))
		{
			requirements.add(equipmentRequirement(
				"Witchwood icon", "witchwood icon"
			));
		}
		if (task.contains("fever spider") && style == CombatStyle.MELEE)
		{
			requirements.add(equipmentRequirement(
				"Slayer gloves", "slayer gloves"
			));
		}
		else if (task.contains("fever spider"))
		{
			requirements.add(inventoryRequirement(
				"Relicym's balm",
				"relicym s balm"
			));
		}
		if (task.contains("harpie bug swarm"))
		{
			requirements.add(equipmentRequirement(
				"Lit bug lantern", "lit bug lantern"
			));
		}
		if (requiresKaruulmProtectionBootsForRegression(
			task,
			location,
			achievementDiaries.removesKaruulmBootRequirement()
		))
		{
			requirements.add(equipmentRequirement(
				"Stone-protection boots",
				"boots of stone", "granite boots",
				"boots of brimstone"
			));
		}
		if (task.contains("basilisk")
			|| task.contains("cockatrice"))
		{
			requirements.add(equipmentRequirement(
				"Mirror shield or V's shield",
				"mirror shield", "v s shield"
			));
		}
		if (task.contains("kurask") || task.contains("turoth"))
		{
			if (style == CombatStyle.RANGED)
			{
				requirements.add(equipmentRequirement(
					"Broad ammunition",
					"broad bolts", "amethyst broad bolts",
					"broad arrows", "amethyst broad arrows"
				));
			}
			else if (style == CombatStyle.MAGIC)
			{
				requirements.add(equipmentRequirement(
					"Magic Dart staff",
					"slayer s staff e", "slayer s staff",
					"toxic staff of the dead", "staff of the dead"
				));
			}
			else
			{
				requirements.add(equipmentRequirement(
					"Leaf-bladed weapon",
					"leaf bladed battleaxe",
					"leaf bladed sword",
					"leaf bladed spear"
				));
			}
		}
		if (task.contains("zygomite"))
		{
			requirements.add(inventoryRequirement(
				"Fungicide spray", "fungicide spray"
			));
		}
		if (task.contains("fossil island wyvern")
			|| task.contains("skeletal wyvern")
			|| combined.contains("wyvern cave on fossil island"))
		{
			if (style == CombatStyle.RANGED)
			{
				requirements.add(equipmentRequirement(
					"Wyvern-protection shield",
					"dragonfire ward", "mind shield", "elemental shield",
					"dragonfire shield", "ancient wyvern shield"
				));
			}
			else
			{
				requirements.add(equipmentRequirement(
					"Wyvern-protection shield",
					"ancient wyvern shield", "dragonfire shield",
					"mind shield", "elemental shield", "dragonfire ward"
				));
			}
		}
		if ((task.equals("blue dragon") || task.equals("blue dragons")))
		{
			if (style != CombatStyle.MELEE)
			{
				requirements.add(equipmentRequirement(
					"Dragonfire shield protection",
					"anti dragon shield", "dragonfire shield",
					"dragonfire ward", "ancient wyvern shield"
				));
			}
		}
		else if (usesReviewedDragonPackage(task) && style != CombatStyle.MELEE)
		{
			requirements.add(equipmentRequirement(
				"Dragonfire shield protection",
				"dragonfire ward", "anti dragon shield",
				"dragonfire shield", "ancient wyvern shield"
			));
		}
		else if (isDragonTask(task) && !task.equals("vorkath")
			&& !usesReviewedDragonPackage(task))
		{
			requirements.add(equipmentRequirement(
				"Dragonfire shield protection",
				"anti dragon shield", "dragonfire shield",
				"dragonfire ward", "ancient wyvern shield"
			));
			requirements.add(inventoryRequirement(
				SlayerPotionPolicy.EXTENDED_ANTIFIRE_DISPLAY,
				SlayerPotionPolicy.shieldedAntifireAlternatives()
			));
		}
		if (task.contains("vampyre")
			|| task.contains("vampire")
			|| task.contains("venator"))
		{
			requirements.add(equipmentRequirement(
				"Sunspear or blisterwood weapon",
				"sunspear", "blisterwood flail",
				"blisterwood sickle", "blisterwood polearm",
				"ivandis flail"
			));
		}
		if (task.contains("banshee"))
		{
			requirements.add(equipmentRequirement(
				"Earmuffs or Slayer helmet",
				"earmuffs", "slayer helmet"
			));
		}
		if (task.contains("aquanite"))
		{
			requirements.add(inventoryRequirement(
				"Fast slash lure-severing weapon",
				"abyssal whip", "blade of saeldor", "dragon scimitar",
				"rune scimitar", "adamant scimitar"
			));
		}
		if (task.contains("dust devil") || task.contains("smoke devil"))
		{
			requirements.add(equipmentRequirement(
				"Face mask or Slayer helmet",
				"face mask", "slayer helmet"
			));
		}
		if (task.contains("aberrant spectre"))
		{
			requirements.add(equipmentRequirement(
				"Nose peg or Slayer helmet",
				"nose peg", "slayer helmet"
			));
		}
		if (task.contains("wall beast"))
		{
			requirements.add(equipmentRequirement(
				"Spiny helmet or Slayer helmet",
				"spiny helmet", "slayer helmet"
			));
			requirements.add(inventoryRequirement(
				"Safe light source",
				"bruma torch", "bullseye lantern", "emerald lantern",
				"sapphire lantern", "oil lantern", "candle lantern"
			));
		}
		if (task.contains("killerwatt"))
		{
			requirements.add(equipmentRequirement(
				"Insulated boots", "insulated boots"
			));
		}
		if (task.contains("shellbane gryphon"))
		{
			requirements.add(equipmentRequirement(
				"Tortugan shield", "tortugan shield"
			));
		}
		if (task.contains("warped creature"))
		{
			requirements.add(inventoryRequirement(
				"Crystal chime", "crystal chime"
			));
		}

		return requirements;
	}

	/* Grotesque Guardians own an encounter-specific finisher rule whose
	 * alternatives include Granite hammer and Rock thrownhammer. Applying the
	 * regular-Gargoyle fallback too creates a second, rock-hammer-only slot. */
	static boolean requiresGenericRockHammerForRegression(final String taskName)
	{
		final String task = normalize(taskName);
		return task.contains("gargoyle")
			&& !task.contains("grotesque guardian");
	}

	static boolean requiresKalphiteQueenRopesForRegression(
		final String task,
		final String location,
		final boolean desertEliteDiaryComplete)
	{
		if (desertEliteDiaryComplete || task == null || location == null)
		{
			return false;
		}
		final String normalizedTask = normalize(task);
		return (normalizedTask.equals("kalphite queen")
			|| normalizedTask.equals("the kalphite queen"))
			&& normalize(location).equals("kalphite lair");
	}

	static boolean requiresKaruulmProtectionBootsForRegression(
		final String task,
		final String location,
		final boolean kourendEliteDiaryComplete)
	{
		if (kourendEliteDiaryComplete)
		{
			return false;
		}
		final String normalizedTask = normalize(task);
		final String normalizedLocation = normalize(location);
		return normalizedTask.contains("wyrm")
			|| normalizedTask.contains("drake")
			|| normalizedTask.contains("hydra")
			|| normalizedLocation.contains("karuulm");
	}

	private static boolean isDragonTask(final String task)
	{
		return task.contains("dragon")
			&& !task.contains("dragonfly")
			&& !task.contains("dragon impling");
	}

	private static boolean usesReviewedDragonPackage(final String task)
	{
		return task.equals("blue dragon") || task.equals("blue dragons")
			|| task.equals("black dragon") || task.equals("black dragons")
			|| task.equals("green dragon") || task.equals("green dragons")
			|| task.equals("red dragon") || task.equals("red dragons")
			|| task.equals("metal dragon") || task.equals("metal dragons")
			|| task.equals("bronze dragon") || task.equals("bronze dragons")
			|| task.equals("iron dragon") || task.equals("iron dragons")
			|| task.equals("steel dragon") || task.equals("steel dragons")
			|| task.equals("mithril dragon") || task.equals("mithril dragons")
			|| task.equals("adamant dragon") || task.equals("adamant dragons")
			|| task.equals("rune dragon") || task.equals("rune dragons");
	}

	private static CombatStyle combatStyle(
		final SlayerTaskStrategy strategy)
	{
		if (strategy == null)
		{
			return CombatStyle.FLEXIBLE;
		}

		switch (strategy.getCombatStyle())
		{
			case MAGIC:
				return CombatStyle.MAGIC;
			case RANGED:
				return CombatStyle.RANGED;
			case MELEE:
				return CombatStyle.MELEE;
			case HYBRID:
			default:
				return CombatStyle.FLEXIBLE;
		}
	}

	private static boolean isCannonSuggested(final String cannon)
	{
		return (cannon.contains("recommended")
			|| cannon.contains("preferred")
			|| cannon.contains("optional"))
			&& !cannon.contains("not allowed")
			&& !cannon.contains("disabled")
			&& !cannon.contains("locked");
	}

	private static Requirement equipmentRequirement(
		final String displayName,
		final String... alternatives)
	{
		return new Requirement(displayName, true, 1, alternatives);
	}

	private static Requirement inventoryRequirement(
		final String displayName,
		final String... alternatives)
	{
		return new Requirement(displayName, false, 1, alternatives);
	}

	private static Requirement inventoryRequirement(
		final int quantity,
		final String displayName,
		final String... alternatives)
	{
		return new Requirement(displayName, false, quantity, alternatives);
	}

	private static int consumableFinisherQuantity(final int remainingKills)
	{
		return remainingKills > 0 ? remainingKills : 1;
	}

	private static boolean containsAny(
		final Set<String> names,
		final List<String> alternatives)
	{
		for (final String alternative : alternatives)
		{
			if (containsText(names, alternative))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsAnyText(
		final Set<String> names,
		final String... fragments)
	{
		for (final String fragment : fragments)
		{
			if (containsText(names, fragment))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsText(
		final Set<String> names,
		final String fragment)
	{
		final String normalizedFragment = normalize(fragment);
		for (final String name : names)
		{
			if (name.contains(normalizedFragment))
			{
				return true;
			}
		}
		return false;
	}

	private static String join(
		final List<String> values,
		final String separator)
	{
		final StringBuilder result = new StringBuilder();
		for (final String value : values)
		{
			if (value == null || value.trim().isEmpty())
			{
				continue;
			}
			if (result.length() > 0)
			{
				result.append(separator);
			}
			result.append(value.trim());
		}
		return result.toString();
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
			/* Preserve meaningful potion tiers: Anti-venom+ and Antidote++
			 * are authored as "plus" and "plus plus" alternatives. */
			.replace("+", " plus ")
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceFirst("^the\\s+", "");
	}

	static String normalizePotionDisplayNameForRegression(final String value)
	{
		return normalize(value);
	}

	static String preferredInventoryItemForRegression(
		final List<String> ownedDisplayNames,
		final String... alternatives)
	{
		return preferredInventoryItemForRegression(
			ownedDisplayNames, false, alternatives
		);
	}

	static String preferredInventoryEquipmentForRegression(
		final List<String> ownedDisplayNames,
		final String... progression)
	{
		return preferredInventoryItemForRegression(
			ownedDisplayNames, true, progression
		);
	}

	private static String preferredInventoryItemForRegression(
		final List<String> ownedDisplayNames,
		final boolean equipmentProgression,
		final String... alternatives)
	{
		final List<OwnedItem> owned = new ArrayList<>();
		for (final String displayName : ownedDisplayNames)
		{
			owned.add(new OwnedItem(
				-1,
				displayName,
				1,
				SlayerLoadoutItem.Status.BANK,
				-1,
				null,
				Collections.singleton(normalize(displayName))
			));
		}
		final OwnedItem selected = findPreferredInventoryItem(
			owned,
			Arrays.asList(alternatives),
			equipmentProgression
		);
		return selected == null ? "" : selected.displayName;
	}

	static boolean equipmentProgressionNameMatchesForRegression(
		final String itemName,
		final String progressionName)
	{
		return matchesEquipmentProgressionName(
			normalize(itemName),
			normalize(progressionName)
		);
	}

	static int directEquipmentProgressionRankForRegression(
		final String itemName,
		final List<String> progression)
	{
		return directEquipmentProgressionRank(
			normalize(itemName), progression
		);
	}

	private static int directEquipmentProgressionRank(
		final String candidate,
		final List<String> progression)
	{
		if (candidate == null || candidate.isEmpty() || progression == null)
		{
			return -1;
		}
		for (int index = 0; index < progression.size(); index++)
		{
			if (matchesEquipmentProgressionName(
				candidate, normalize(progression.get(index))))
			{
				return index;
			}
		}
		return -1;
	}

	private static boolean matchesEquipmentProgressionName(
		final String candidate,
		final String progressionName)
	{
		if (candidate == null || progressionName == null
			|| candidate.isEmpty() || progressionName.isEmpty())
		{
			return false;
		}
		if (candidate.contains(progressionName))
		{
			return true;
		}

		final boolean godItem = candidate.startsWith("ancient ")
			|| candidate.startsWith("armadyl ")
			|| candidate.startsWith("bandos ")
			|| candidate.startsWith("guthix ")
			|| candidate.startsWith("saradomin ")
			|| candidate.startsWith("zamorak ");
		if (progressionName.equals("blessed body"))
		{
			return godItem && candidate.endsWith("d hide body");
		}
		if (progressionName.equals("blessed coif"))
		{
			return godItem && candidate.endsWith("coif");
		}
		if (progressionName.equals("blessed chaps"))
		{
			return godItem && candidate.endsWith("chaps");
		}
		if (progressionName.equals("blessed vambraces"))
		{
			return godItem && (candidate.endsWith("bracers")
				|| candidate.endsWith("vambraces"));
		}
		if (progressionName.equals("blessed boots"))
		{
			return godItem && candidate.endsWith("d hide boots");
		}
		if (progressionName.equals("imbued god cape"))
		{
			return candidate.matches(
				"imbued (?:saradomin|guthix|zamorak)(?: max)? cape"
			);
		}
		if (progressionName.equals("god cape"))
		{
			return candidate.matches(
				"(?:saradomin|guthix|zamorak)(?: max)? cape"
			);
		}
		if (progressionName.equals("dizana s quiver"))
		{
			return candidate.matches(
				"(?:blessed )?dizana s (?:quiver|max cape)(?: [a-z0-9]+)*"
			);
		}
		if (progressionName.equals("dizana s max cape"))
		{
			return candidate.contains("dizana s max cape");
		}
		if (progressionName.equals("ava s assembler"))
		{
			return candidate.contains("assembler max cape")
				|| candidate.contains("masori assembler");
		}
		if (progressionName.equals("ava s accumulator"))
		{
			return candidate.contains("accumulator max cape");
		}
		if (progressionName.equals("infernal cape"))
		{
			return candidate.contains("infernal max cape");
		}
		if (progressionName.equals("fire cape"))
		{
			return candidate.contains("fire max cape");
		}
		if (progressionName.equals("mythical cape"))
		{
			return candidate.contains("mythical max cape");
		}
		if (progressionName.equals("ardougne cloak 4"))
		{
			return candidate.contains("ardougne max cape");
		}
		if (progressionName.equals("cape of accomplishment"))
		{
			return isCapeOfAccomplishmentName(candidate);
		}
		if (progressionName.equals("vestment robe top"))
		{
			return godItem && candidate.endsWith("robe top");
		}
		if (progressionName.equals("vestment robe legs"))
		{
			return godItem && (candidate.endsWith("robe legs")
				|| candidate.endsWith("robe bottom"));
		}
		if (progressionName.equals("vestment cloak"))
		{
			return godItem && candidate.endsWith("cloak");
		}
		if (progressionName.equals("elemental staff"))
		{
			return candidate.equals("staff of air")
				|| candidate.equals("staff of water")
				|| candidate.equals("staff of earth")
				|| candidate.equals("staff of fire");
		}
		if (progressionName.equals("mystic staff"))
		{
			return candidate.startsWith("mystic ")
				&& candidate.endsWith(" staff");
		}
		if (progressionName.equals("barrows helm"))
		{
			return isBarrowsFamily(candidate, "helm");
		}
		if (progressionName.equals("barrows platebody"))
		{
			return isBarrowsFamily(candidate, "platebody")
				|| candidate.startsWith("verac s brassard");
		}
		if (progressionName.equals("barrows platelegs"))
		{
			return isBarrowsFamily(candidate, "platelegs")
				|| candidate.startsWith("verac s plateskirt");
		}
		return false;
	}

	private static boolean isBarrowsFamily(
		final String candidate,
		final String piece)
	{
		return candidate.matches(
			"(?:dharok|guthan|torag) s " + piece + "(?: [a-z0-9]+)*"
		);
	}

	private static boolean isCapeOfAccomplishmentName(
		final String candidate)
	{
		if (candidate.equals("max cape") || candidate.equals("max cape t"))
		{
			return true;
		}
		return candidate.matches(
			"(?:attack|strength|defence|ranging|prayer|magic|runecraft|"
				+ "construction|hitpoints|agility|herblore|thieving|crafting|"
				+ "fletching|slayer|hunter|mining|smithing|fishing|cooking|"
				+ "firemaking|woodcutting|farming|sailing|quest point|music|"
				+ "achievement diary) cape(?: t)?"
		);
	}

	private enum CombatStyle
	{
		MAGIC("magic"),
		RANGED("ranged"),
		MELEE("melee"),
		FLEXIBLE("flexible");

		private final String label;

		CombatStyle(final String label)
		{
			this.label = label;
		}
	}



	private static final class OwnedStrategyCandidate
	{
		private final SlayerTaskStrategy strategy;
		private final OwnedItem weapon;
		private final int score;

		private OwnedStrategyCandidate(
			final SlayerTaskStrategy strategy,
			final OwnedItem weapon,
			final int score)
		{
			this.strategy = strategy;
			this.weapon = weapon;
			this.score = score;
		}
	}

	private static final class OwnedItem
	{
		private final int itemId;
		private final String displayName;
		private final String normalizedName;
		private final Set<String> normalizedNames;
		private final int quantity;
		private final SlayerLoadoutItem.Status status;
		private final int equipmentSlot;
		private final ItemEquipmentStats equipmentStats;

		private OwnedItem(
			final int itemId,
			final String displayName,
			final int quantity,
			final SlayerLoadoutItem.Status status,
			final int equipmentSlot,
			final ItemEquipmentStats equipmentStats,
			final Set<String> normalizedNames)
		{
			this.itemId = itemId;
			this.displayName = displayName;
			this.normalizedName = normalize(displayName);
			final Set<String> aliases = new LinkedHashSet<>();
			if (normalizedNames != null)
			{
				aliases.addAll(normalizedNames);
			}
			aliases.add(this.normalizedName);
			this.normalizedNames = Collections.unmodifiableSet(aliases);
			this.quantity = quantity;
			this.status = status;
			this.equipmentSlot = equipmentSlot;
			this.equipmentStats = equipmentStats;
		}

		private boolean matchesName(final String fragment)
		{
			final String normalizedFragment = normalize(fragment);
			if (normalizedFragment.isEmpty())
			{
				return false;
			}

			for (final String candidate : normalizedNames)
			{
				if (candidate.contains(normalizedFragment))
				{
					return true;
				}
			}
			return false;
		}

		private boolean matchesEquipmentProgression(final String fragment)
		{
			final String normalizedFragment = normalize(fragment);
			if (normalizedFragment.isEmpty())
			{
				return false;
			}
			for (final String candidate : normalizedNames)
			{
				if (matchesEquipmentProgressionName(candidate, normalizedFragment))
				{
					return true;
				}
			}
			return false;
		}

		private int directEquipmentProgressionRank(
			final List<String> progression)
		{
			return SlayerLoadoutAnalyzer.directEquipmentProgressionRank(
				normalizedName, progression
			);
		}

		private boolean matchesExactName(final String value)
		{
			final String normalizedValue = normalize(value);
			return !normalizedValue.isEmpty()
				&& normalizedNames.contains(normalizedValue);
		}

		private boolean matchesExactDisplayName(final String value)
		{
			final String normalizedValue = normalize(value);
			return !normalizedValue.isEmpty()
				&& normalizedName.equals(normalizedValue);
		}

		private boolean isExactSeekingArrow()
		{
			return SlayerQuiverAmmo.isSeekingArrow(itemId)
				|| normalizedName.startsWith("seeking ")
				&& normalizedName.contains("arrow");
		}

		private boolean matchesSlot(
			final EquipmentInventorySlot slot)
		{
			if (equipmentStats != null)
			{
				return equipmentStats.getSlot() == slot.getSlotIdx();
			}

			return equipmentSlot == slot.getSlotIdx();
		}

		private boolean isTwoHanded()
		{
			if (equipmentStats != null && equipmentStats.isTwoHanded())
			{
				return true;
			}

			/*
			 * Keep off-hands out even if a newly released weapon's local item
			 * stats have not populated yet.
			 */
			final boolean twoHandedBow =
				!matchesName("crossbow")
					&& (matchesName(" bow")
						|| normalizedName.startsWith("bow ")
						|| matchesName("shortbow")
						|| matchesName("longbow"));

			return matchesName("blowpipe")
				|| twoHandedBow
				|| matchesName("ballista")
				|| matchesName("scythe")
				|| matchesName("godsword")
				|| matchesName("halberd")
				|| matchesName("spear")
				|| matchesName("2h sword")
				|| matchesName("bludgeon");
		}

		private SlayerLoadoutItem toLoadoutItem(
			final int requestedQuantity)
		{
			return new SlayerLoadoutItem(
				displayName,
				itemId,
				Math.max(1, requestedQuantity),
				status
			);
		}
	}

	private static final class Requirement
	{
		private final String displayName;
		private final boolean equipment;
		private final int quantity;
		private final List<String> alternatives;

		private Requirement(
			final String displayName,
			final boolean equipment,
			final int quantity,
			final String... alternatives)
		{
			this.displayName = displayName;
			this.equipment = equipment;
			this.quantity = Math.max(1, quantity);
			this.alternatives = new ArrayList<>();
			for (final String alternative : alternatives)
			{
				this.alternatives.add(normalize(alternative));
			}
		}
	}
}
