package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Bank-adjacent route targets used by the guided Slayer session.
 *
 * <p>The coordinates are synchronized with Shortest Path's public bank
 * destination data. Rows that require a quest, skill, varbit, or varplayer are
 * intentionally omitted here because a raw PluginMessage target cannot carry
 * those destination requirements. The resulting set covers ordinary public
 * banks without risking a route to a server-side-locked bank.</p>
 */
public final class SlayerBankRouteCatalog
{
	/*
	 * Reviewed final-preparation bank for Inferno runs. This is the eastern
	 * Mor Ul Rek bank ("Zuk bank") already present in the shared static bank
	 * catalog. TzKal-Zuk routing stages here before the Inferno entrance so the
	 * player can replace the travel item with the final combat supply.
	 */
	private static final WorldPoint INFERNO_PREPARATION_BANK =
		new WorldPoint(2543, 5141, 0);
	/*
	 * Reviewed east Hot vent door on the boundary of inner Mor Ul Rek. Current
	 * Shortest Path transport data does not model the door's Pass transition, so
	 * the no-Hilt branch must finish its first route here. After the player passes
	 * the door, the live TzHaar-Ket-Yil collision check proves that the bank is
	 * reachable and SlayerPlus starts a separate local route.
	 */
	private static final WorldPoint INFERNO_PREPARATION_HOT_VENT_DOOR =
		new WorldPoint(2495, 5157, 0);
	/*
	 * This exact point is also present in Shortest Path's current bank.tsv as a
	 * Mor Ul Rek bank destination. Do not invent a ring of guessed neighbour
	 * tiles around it: for the long-distance handoff we should speak Shortest
	 * Path's own destination language exactly.
	 */
	private static final Set<WorldPoint> INFERNO_PREPARATION_BANK_TARGETS =
		Collections.singleton(INFERNO_PREPARATION_BANK);
	private static final Set<WorldPoint> INFERNO_PREPARATION_HOT_VENT_TARGETS =
		Collections.singleton(INFERNO_PREPARATION_HOT_VENT_DOOR);
	/* Walkable tile directly south of the Seers' Village bank booths. */
	private static final WorldPoint SEERS_VILLAGE_BANK_APPROACH =
		new WorldPoint(2727, 3492, 0);
	private static final int SEERS_VILLAGE_LOCAL_RADIUS = 64;

	private static final Set<WorldPoint> STANDARD_BANKS =
		createStandardBanks();
	private static final Set<WorldPoint> WILDERNESS_BANKS =
		createWildernessBanks();

	private SlayerBankRouteCatalog()
	{
	}

	public static WorldPoint getInfernoPreparationBankTarget()
	{
		return INFERNO_PREPARATION_BANK;
	}

	/**
	 * Exact Shortest Path bank destination for the east Mor Ul Rek / Zuk bank.
	 * Once TzHaar-Ket-Yil is loaded, SlayerPlus replaces this catalog point with
	 * a collision-checked walkable tile beside the live banker.
	 */
	public static Set<WorldPoint> getInfernoPreparationBankApproachTargets()
	{
		return INFERNO_PREPARATION_BANK_TARGETS;
	}

	/**
	 * Exact east inner-city barrier used only when no owned Ghommal teleport can
	 * bypass Mor Ul Rek's manual Pass interaction.
	 */
	public static WorldPoint getInfernoPreparationHotVentDoorTarget()
	{
		return INFERNO_PREPARATION_HOT_VENT_DOOR;
	}

	public static Set<WorldPoint> getInfernoPreparationHotVentDoorTargets()
	{
		return INFERNO_PREPARATION_HOT_VENT_TARGETS;
	}

	public static Set<WorldPoint> getBankTargets(
		final boolean allowWilderness)
	{
		if (!allowWilderness)
		{
			return STANDARD_BANKS;
		}

		final Set<WorldPoint> targets =
			new LinkedHashSet<>(STANDARD_BANKS);
		targets.addAll(WILDERNESS_BANKS);
		return Collections.unmodifiableSet(targets);
	}

	public static WorldPoint getPreferredLocalApproachTarget(
		final WorldPoint playerLocation)
	{
		if (playerLocation == null
			|| playerLocation.getPlane() != SEERS_VILLAGE_BANK_APPROACH.getPlane())
		{
			return null;
		}
		final int distance = Math.max(
			Math.abs(playerLocation.getX() - SEERS_VILLAGE_BANK_APPROACH.getX()),
			Math.abs(playerLocation.getY() - SEERS_VILLAGE_BANK_APPROACH.getY())
		);
		return distance <= SEERS_VILLAGE_LOCAL_RADIUS
			? SEERS_VILLAGE_BANK_APPROACH : null;
	}

	private static Set<WorldPoint> createStandardBanks()
	{
		final Set<WorldPoint> targets = new LinkedHashSet<>();

		// Al Kharid
		add(targets, 3269, 3164, 0);
		add(targets, 3269, 3166, 0);
		add(targets, 3269, 3167, 0);
		add(targets, 3269, 3168, 0);
		add(targets, 3269, 3169, 0);

		// Arceuus
		add(targets, 1624, 3741, 0);
		add(targets, 1635, 3741, 0);
		add(targets, 1624, 3744, 0);
		add(targets, 1635, 3744, 0);
		add(targets, 1624, 3746, 0);
		add(targets, 1635, 3746, 0);
		add(targets, 1624, 3749, 0);
		add(targets, 1635, 3749, 0);

		// Ardougne
		add(targets, 2655, 3280, 0);
		add(targets, 2655, 3283, 0);
		add(targets, 2655, 3286, 0);
		add(targets, 2615, 3332, 0);
		add(targets, 2618, 3332, 0);
		add(targets, 2619, 3332, 0);

		// Bank Boat
		add(targets, 2280, 2544, 0);

		// Barbarian Outpost
		add(targets, 2536, 3573, 0);

		// Blast Mine
		add(targets, 1479, 3857, 0);
		add(targets, 1499, 3857, 0);
		add(targets, 1501, 3871, 0);
		add(targets, 1478, 3873, 0);

		// Castle Wars
		add(targets, 2443, 3083, 0);

		// Catherby
		add(targets, 2807, 3441, 0);
		add(targets, 2809, 3441, 0);
		add(targets, 2810, 3441, 0);
		add(targets, 2811, 3441, 0);

		// Chambers of Xeric
		add(targets, 3333, 5194, 0);
		add(targets, 3304, 5199, 0);
		add(targets, 3279, 5200, 0);

		// Clan Hall
		add(targets, 1745, 5476, 0);
		add(targets, 1746, 5476, 0);
		add(targets, 1747, 5476, 0);
		add(targets, 1748, 5476, 0);

		// Deepfin Point
		add(targets, 1935, 2754, 0);

		// Draynor Village
		add(targets, 3092, 3242, 0);
		add(targets, 3092, 3243, 0);
		add(targets, 3092, 3245, 0);

		// Edgeville
		add(targets, 3094, 3489, 0);
		add(targets, 3094, 3491, 0);
		add(targets, 3096, 3494, 0);
		add(targets, 3098, 3494, 0);

		// Emir's Arena
		add(targets, 3382, 3269, 0);

		// Falador
		add(targets, 3010, 3355, 0);
		add(targets, 3011, 3355, 0);
		add(targets, 3012, 3355, 0);
		add(targets, 3013, 3355, 0);
		add(targets, 3014, 3355, 0);
		add(targets, 3015, 3355, 0);
		add(targets, 2945, 3368, 0);
		add(targets, 2946, 3368, 0);
		add(targets, 2947, 3368, 0);
		add(targets, 2948, 3368, 0);
		add(targets, 2949, 3368, 0);

		// Farming Guild
		add(targets, 1253, 3741, 0);

		// Fishing Guild
		add(targets, 2586, 3418, 0);
		add(targets, 2586, 3419, 0);
		add(targets, 2586, 3421, 0);
		add(targets, 2586, 3422, 0);

		// Grand Exchange
		add(targets, 3162, 3489, 0);
		add(targets, 3167, 3489, 0);
		add(targets, 3162, 3490, 0);
		add(targets, 3167, 3490, 0);

		// Hosidius
		add(targets, 1746, 3598, 0);
		add(targets, 1748, 3598, 0);
		add(targets, 1748, 3599, 0);
		add(targets, 1748, 3600, 0);

		// Hosidius Kitchen
		add(targets, 1674, 3615, 0);
		add(targets, 1676, 3615, 0);

		// Hosidius Sand Crabs
		add(targets, 1719, 3465, 0);

		// Hosidius Vinery
		add(targets, 1809, 3566, 0);

		// Kourend Castle
		add(targets, 1610, 3681, 2);
		add(targets, 1611, 3681, 2);
		add(targets, 1612, 3681, 2);
		add(targets, 1613, 3681, 2);

		// Land's End
		add(targets, 1512, 3421, 0);

		// Lovakengj
		add(targets, 1522, 3738, 0);
		add(targets, 1530, 3738, 0);
		add(targets, 1520, 3740, 0);
		add(targets, 1522, 3740, 0);
		add(targets, 1524, 3740, 0);
		add(targets, 1526, 3740, 0);
		add(targets, 1528, 3740, 0);
		add(targets, 1530, 3740, 0);
		add(targets, 1436, 3822, 0);
		add(targets, 1438, 3822, 0);
		add(targets, 1436, 3824, 0);
		add(targets, 1438, 3828, 0);
		add(targets, 1436, 3832, 0);
		add(targets, 1436, 3834, 0);
		add(targets, 1438, 3834, 0);

		// Lumbridge Castle
		add(targets, 3208, 3220, 2);
		add(targets, 3209, 3220, 2);

		// Mage Training Arena
		add(targets, 3365, 3318, 1);

		// Mining Guild
		add(targets, 3013, 9718, 0);

		// Mor Ul Rek
		add(targets, 2543, 5141, 0);
		add(targets, 2445, 5180, 0);

		// Motherlode Mine
		add(targets, 3760, 5666, 0);

		// Mount Karuulm
		add(targets, 1324, 3824, 0);

		// Mount Quidamortem
		add(targets, 1254, 3571, 0);

		// Nardah
		add(targets, 3427, 2889, 0);
		add(targets, 3427, 2891, 0);
		add(targets, 3427, 2893, 0);
		add(targets, 3427, 2894, 0);

		// Ourania
		add(targets, 3013, 5625, 0);

		// Port Khazard
		add(targets, 2661, 3162, 0);

		// Port Piscarilius
		add(targets, 1796, 3790, 0);
		add(targets, 1800, 3790, 0);
		add(targets, 1804, 3790, 0);
		add(targets, 1808, 3790, 0);

		// Port Roberts
		add(targets, 1881, 3315, 1);
		add(targets, 1882, 3315, 1);
		add(targets, 1883, 3315, 1);
		add(targets, 1884, 3315, 1);

		// Rogues' Den
		add(targets, 3040, 4969, 1);

		// Ruins of Unkah
		add(targets, 3156, 2835, 0);

		// Seers' Village
		add(targets, 2727, 3492, 0);

		// Shantay Pass
		add(targets, 3308, 3120, 0);

		// Shayzien
		add(targets, 1487, 3590, 0);
		add(targets, 1488, 3592, 0);
		add(targets, 1487, 3594, 0);

		// Shayzien Encampment
		add(targets, 1483, 3646, 0);
		add(targets, 1486, 3646, 0);

		// Soul Wars
		add(targets, 2212, 2859, 0);
		add(targets, 2020, 5931, 0);

		// Sulphur Mine
		add(targets, 1478, 3856, 0);
		add(targets, 1454, 3858, 0);
		add(targets, 1477, 3874, 0);

		// The Pandemonium
		add(targets, 3038, 3000, 0);

		// Tree Gnome Stronghold
		add(targets, 2445, 3424, 1);
		add(targets, 2446, 3424, 1);
		add(targets, 2445, 3425, 1);
		add(targets, 2446, 3427, 1);
		add(targets, 2448, 3482, 1);
		add(targets, 2449, 3482, 1);
		add(targets, 2450, 3482, 1);
		add(targets, 2442, 3487, 1);
		add(targets, 2442, 3488, 1);
		add(targets, 2442, 3489, 1);

		// Varrock
		add(targets, 3251, 3420, 0);
		add(targets, 3252, 3420, 0);
		add(targets, 3253, 3420, 0);
		add(targets, 3254, 3420, 0);
		add(targets, 3255, 3420, 0);
		add(targets, 3256, 3420, 0);
		add(targets, 3185, 3436, 0);
		add(targets, 3185, 3438, 0);
		add(targets, 3185, 3440, 0);
		add(targets, 3185, 3442, 0);
		add(targets, 3185, 3444, 0);

		// Void Knights' Outpost
		add(targets, 2665, 2653, 0);
		add(targets, 2666, 2653, 0);
		add(targets, 2667, 2653, 0);
		add(targets, 2668, 2653, 0);

		// Warriors' Guild
		add(targets, 2843, 3543, 0);

		// Wintertodt Camp
		add(targets, 1640, 3944, 0);

		// Woodcutting Guild
		add(targets, 1592, 3476, 0);
		add(targets, 1551, 9873, 0);

		// Yanille
		add(targets, 2613, 3091, 0);
		add(targets, 2613, 3092, 0);
		add(targets, 2613, 3094, 0);

		return Collections.unmodifiableSet(targets);
	}

	private static Set<WorldPoint> createWildernessBanks()
	{
		final Set<WorldPoint> targets = new LinkedHashSet<>();

		// Daimon's Crater
		add(targets, 3420, 4058, 0);
		add(targets, 3426, 4069, 0);

		// Ferox Enclave
		add(targets, 3130, 3631, 0);

		return Collections.unmodifiableSet(targets);
	}

	private static void add(
		final Set<WorldPoint> targets,
		final int x,
		final int y,
		final int plane)
	{
		targets.add(new WorldPoint(x, y, plane));
	}
}
