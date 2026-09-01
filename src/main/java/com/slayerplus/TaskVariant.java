package com.slayerplus;
public enum TaskVariant{STANDARD_TASK(false),ABYSSAL_SIRE(true),ALCHEMICAL_HYDRA(true),AMOXLIATL(true),ARAXXOR(true),ARTIO(true),CALLISTO(true),CERBERUS(true),DAGANNOTH_KINGS(true),DAGANNOTH_REX(true),DERANGED_ARCHAEOLOGIST(true),GROTESQUE_GUARDIANS(true),KRIL_TSUTSAROTH(true),KALPHITE_QUEEN(true),KING_BLACK_DRAGON(true),KRAKEN_BOSS(true),KREEARRA(true),SARACHNIS(true),SCORPIA(true),SKOTIZO(true),THERMONUCLEAR_SMOKE_DEVIL(true),TZTOK_JAD(true),TZKAL_ZUK(true),SPINDEL(true),VENENATIS(true),CALVARION(true),VETION(true),VORKATH(true),SCURRIUS(true),OBOR(true),BRYOPHYTA(true),BRUTUS(true),DEMONIC_GORILLAS(true),TORMENTED_DEMONS(true),ROYAL_TITANS(true),SHELLBANE_GRYPHON(true),BABY_BLUE_DRAGONS(true);
private final boolean boss;
TaskVariant(boolean boss){this.boss=boss;
}public boolean isBoss(){return boss;
}}
