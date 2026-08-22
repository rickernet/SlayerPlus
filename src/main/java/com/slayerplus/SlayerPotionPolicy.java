package com.slayerplus;

import java.util.Locale;

/**
 * Shared, effect-aware potion families used by every Slayer inventory.
 *
 * <p>Alternative order expresses the preferred effect when two owned potions
 * have equal useful uptime. Extended families receive two uptime units per
 * dose because their relevant protection/effect lasts twice as long as the
 * base family. This keeps the global "fullest useful potion" rule while no
 * longer allowing a regular four-dose potion to incorrectly beat an extended
 * three-dose potion.</p>
 */
final class SlayerPotionPolicy
{
	static final String EXTENDED_STAMINA_DISPLAY = "Extended stamina potion";
	static final String EXTENDED_ANTIVENOM_DISPLAY = "Extended anti-venom+";
	static final String EXTENDED_ANTIFIRE_DISPLAY = "Extended antifire";
	static final String EXTENDED_SUPER_ANTIFIRE_DISPLAY =
		"Extended super antifire";
	static final String GOADING_DISPLAY = "Goading potion";

	private SlayerPotionPolicy()
	{
	}

	static String[] staminaAlternatives()
	{
		return new String[]{
			"extended stamina potion",
			"stamina potion"
		};
	}

	static String[] goadingAlternatives()
	{
		return new String[]{"goading potion"};
	}

	static String[] antivenomAlternatives()
	{
		return new String[]{
			"extended anti venom plus",
			"anti venom plus",
			"anti venom"
		};
	}

	static String[] rangedBoostAlternatives(
		final SlayerTaskStrategy.CostPolicy costPolicy)
	{
		if (costPolicy == SlayerTaskStrategy.CostPolicy.MAX_DPS)
		{
			return new String[]{
				"divine bastion potion",
				"divine ranging potion",
				"bastion potion",
				"ranging potion"
			};
		}
		return new String[]{
			"ranging potion",
			"bastion potion",
			"divine ranging potion",
			"divine bastion potion"
		};
	}

	static String[] meleeBoostAlternatives(
		final SlayerTaskStrategy.CostPolicy costPolicy)
	{
		return costPolicy == SlayerTaskStrategy.CostPolicy.MAX_DPS
			? new String[]{"divine super combat potion", "super combat potion"}
			: new String[]{"super combat potion", "divine super combat potion"};
	}

	static String[] magicBoostAlternatives()
	{
		return new String[]{
			"saturated heart",
			"imbued heart",
			"forgotten brew",
			"divine battlemage potion",
			"divine magic potion",
			"battlemage potion",
			"magic potion",
			"ancient brew"
		};
	}

	/** Dragonfire protection when the reviewed loadout also requires a shield. */
	static String[] shieldedAntifireAlternatives()
	{
		return new String[]{
			"extended antifire",
			"antifire potion",
			"extended super antifire",
			"super antifire"
		};
	}

	/** Full potion-only dragonfire protection for encounters such as Vorkath. */
	static String[] fullAntifireAlternatives()
	{
		return new String[]{
			"extended super antifire",
			"super antifire",
			"extended antifire"
		};
	}

	/** Full potion-only protection when the loadout intentionally uses a defender. */
	static String[] potionOnlyAntifireAlternatives()
	{
		return new String[]{
			"extended super antifire",
			"super antifire"
		};
	}

	static int effectiveDoseUnits(final String potionFamily, final int doses)
	{
		final int safeDoses = Math.max(0, Math.min(4, doses));
		return isDoubleDurationFamily(potionFamily)
			? safeDoses * 2
			: safeDoses;
	}

	private static boolean isDoubleDurationFamily(final String value)
	{
		final String family = normalize(value);
		return family.equals("extended stamina potion")
			|| family.equals("extended anti venom plus")
			|| family.equals("extended antifire")
			|| family.equals("extended super antifire");
	}

	private static String normalize(final String value)
	{
		return value == null
			? ""
			: value.toLowerCase(Locale.ENGLISH)
				.replace("+", " plus ")
				.replaceAll("[^a-z0-9]+", " ")
				.replaceAll("\\s+", " ")
				.trim();
	}
}
