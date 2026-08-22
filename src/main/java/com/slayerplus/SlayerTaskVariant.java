package com.slayerplus;

/**
 * Selects the exact encounter a loadout and guided route should target.
 * STANDARD_TASK always means the ordinary assignment monster. Every other
 * value is a strict boss encounter and must resolve through the boss route map.
 */
public enum SlayerTaskVariant
{
	STANDARD_TASK(false),
	ABYSSAL_SIRE(true),
	ALCHEMICAL_HYDRA(true),
	AMOXLIATL(true),
	ARAXXOR(true),
	ARTIO(true),
	CALLISTO(true),
	CERBERUS(true),
	DAGANNOTH_KINGS(true),
	DERANGED_ARCHAEOLOGIST(true),
	GROTESQUE_GUARDIANS(true),
	KRIL_TSUTSAROTH(true),
	KALPHITE_QUEEN(true),
	KING_BLACK_DRAGON(true),
	KRAKEN_BOSS(true),
	KREEARRA(true),
	SARACHNIS(true),
	SCORPIA(true),
	SKOTIZO(true),
	THERMONUCLEAR_SMOKE_DEVIL(true),
	TZTOK_JAD(true),
	TZKAL_ZUK(true),
	SPINDEL(true),
	VENENATIS(true),
	CALVARION(true),
	VETION(true),
	VORKATH(true),
	SCURRIUS(true),
	OBOR(true),
	BRYOPHYTA(true),
	BRUTUS(true),
	DEMONIC_GORILLAS(true),
	TORMENTED_DEMONS(true),
	ROYAL_TITANS(true),
	SHELLBANE_GRYPHON(true);

	private final boolean boss;

	SlayerTaskVariant(final boolean boss)
	{
		this.boss = boss;
	}

	public boolean isBoss()
	{
		return boss;
	}
}
