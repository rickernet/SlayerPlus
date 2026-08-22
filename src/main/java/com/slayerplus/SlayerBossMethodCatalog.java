package com.slayerplus;

import java.util.Locale;

/**
 * Boss-only Task Method resolver. The selected SlayerTaskStrategy remains the
 * source of truth for combat style and weapon order; this class adds the
 * encounter mechanics that must accompany that exact setup.
 */
public final class SlayerBossMethodCatalog
{
	private SlayerBossMethodCatalog() {}

	public static String resolve(
		final String bossName,
		final SlayerTaskStrategy strategy,
		final String routePositioningNote)
	{
		final String key = normalize(bossName);
		final String bossMethod = safe(mechanics(key, strategy));
		if (!bossMethod.isEmpty()
			&& !bossMethod.equals("Follow the boss mechanics while using the selected setup"))
		{
			return limitMethod(bossMethod);
		}

		final String authored = strategy == null ? "" : safe(strategy.getMethod());
		if (!authored.isEmpty())
		{
			return limitMethod(authored);
		}

		final String route = safe(routePositioningNote);
		return route.isEmpty()
			? "Use the selected setup and follow the boss mechanics."
			: limitMethod(route);
	}

	private static String limitMethod(final String value)
	{
		final String clean = safe(value).replaceAll("\\s+", " ");
		final int maxLength = 280;
		if (clean.length() <= maxLength)
		{
			return finishSentence(clean);
		}

		int cut = clean.lastIndexOf('.', maxLength);
		if (cut < 120)
		{
			cut = clean.lastIndexOf(' ', maxLength - 1);
		}
		if (cut < 1)
		{
			cut = maxLength - 1;
		}
		return finishSentence(clean.substring(0, cut).trim());
	}

	private static String finishSentence(final String value)
	{
		final String clean = safe(value);
		if (clean.isEmpty())
		{
			return clean;
		}
		final char last = clean.charAt(clean.length() - 1);
		return last == '.' || last == '!' || last == '?'
			? clean
			: clean + ".";
	}

	private static String mechanics(
		final String boss,
		final SlayerTaskStrategy strategy)
	{
		final SlayerTaskStrategy.CombatStyle style = strategy == null
			? null : strategy.getCombatStyle();
		switch (boss)
		{
			case "abyssal sire":
				return "Stun the Sire, destroy all four respiratory systems, then avoid the poison pools and move away before the final-phase explosion";
			case "alchemical hydra":
				return "Drag Hydra onto the correct vent, change protection prayer after every third attack, avoid poison and lightning, and move behind it during the flame wall";
			case "araxxor":
				return "Keep the selected attack style active, avoid venom and acid, kill or control spawned araxytes as required, and continue moving during the enrage phase";
			case "amoxliatl":
				return "Avoid the marked floor attacks, keep attacking with the selected setup, and reposition immediately when the arena mechanic forces movement";
			case "artio":
			case "callisto":
				return "Protect from Missiles, freeze or bind the bear when using the ranged or magic method, avoid knockback and falling debris, and keep an escape teleport ready";
			case "cerberus":
				if (style == SlayerTaskStrategy.CombatStyle.RANGED)
				{
					return "Attack from range, react to the three-headed combo, switch prayers for the ghost sequence, and move clear of lava pools";
				}
				if (style == SlayerTaskStrategy.CombatStyle.MAGIC)
				{
					return "Cast from range, react to the three-headed combo, switch prayers for the ghost sequence, and move clear of lava pools";
				}
				return "Stay in melee range, react to the three-headed combo, switch prayers for the ghost sequence, and step away from lava pools before returning to attack";
			case "dagannoth kings":
				return "Use the correct weakness for each King—melee Supreme, ranged Prime, and magic Rex—while keeping the other Kings out of combat whenever possible";
			case "grotesque guardians":
				return "Use ranged on Dawn and melee on Dusk, collect useful energy orbs, avoid falling rocks and lightning, and keep moving during Dusk's final phase";
			case "k ril tsutsaroth":
				return style == SlayerTaskStrategy.CombatStyle.RANGED
					? "Bind and kite K'ril with the Scorching bow special, keep Protect from Missiles active, then stack bodyguards and use Blood Barrage to heal between kills"
					: "Use the selected demonbane melee setup, protect from Melee, step under or reposition between attacks when required, and clean up the bodyguards after the kill";
			case "skotizo":
				return "Use Arclight or the selected demonbane weapon, destroy active altars when their damage reduction becomes significant, and pray against the active attack style";
			case "kalphite queen":
				return "Use the selected melee setup for phase one, switch to the ranged or magic gear included for phase two, and stand underneath between attacks to reduce incoming damage";
			case "king black dragon":
				return "Use dragonfire protection plus the selected attack style, pray against the most dangerous remaining attack, and manage poison, venom, and stat-drain breath effects";
			case "kraken":
				return "Use a fishing explosive on the large whirlpool to wake Kraken and all four tentacles, attack Kraken with powered Magic, use Augury or Mystic Might for offence, and rely on Magic defence because protection prayers do not reduce its typeless attacks";
			case "kree arra":
				return "Use ranged only, keep Protect from Missiles active, stay away from melee distance, and use chinchompas or the selected weapon according to the equipped setup";
			case "sarachnis":
				return "Use crush, switch protection prayer with Sarachnis's attack style, kill or tank the spawned spiders according to the trip plan, and avoid the webbed floor repositioning";
			case "spindel":
			case "venenatis":
				return "Use crush, change prayer for melee and ranged attacks, move away from web and prayer-drain mechanics, kill spiderlings when required, and keep an escape teleport ready";
			case "scorpia":
				return "Freeze Scorpia, attack from distance, freeze or kill the guardians before they heal her, and keep a deep-Wilderness escape plan ready";
			case "thermonuclear smoke devil":
				if (style == SlayerTaskStrategy.CombatStyle.MAGIC)
				{
					final String method = strategy == null ? "" : safe(strategy.getMethod());
					if (containsIgnoreCase(method, "blood barrage")
						|| containsIgnoreCase(method, "blood burst"))
					{
						return "Use Blood Barrage for sustain; protection prayers do not reduce Thermy's typeless attacks";
					}
					return "Freeze Thermy with the Ice ancient sceptre, move beyond its 8-tile range, then attack with Shadow on Longrange; refreeze before it reaches you";
				}
				if (style == SlayerTaskStrategy.CombatStyle.RANGED)
				{
					return "Freeze Thermy, move beyond its 8-tile range, and attack from maximum distance; refreeze before it reaches you";
				}
				return "Attack once, step underneath between attacks, then move back out when ready; protection prayers do not reduce Thermy's typeless attacks";
			case "tztok jad":
				return "Prioritize correct prayer switches, tag the healers without losing sight of Jad's next attack, and keep attacking with the selected ranged setup";
			case "tzkal zuk":
				return "Stay behind the moving shield, handle each set with the selected Inferno setup, kill healers during the final phase, and never step outside shield protection during Zuk attacks";
			case "calvar ion":
			case "vet ion":
				return "Use crush, avoid lightning and shield-bash tiles, kill both hellhounds before resuming damage, and keep an escape teleport ready";
			case "vorkath":
				return style == SlayerTaskStrategy.CombatStyle.MELEE
					? "Use the selected stab weapon with dragonfire protection, walk the acid phase, kill the zombified spawn immediately, and disable run before the rapid-fire attack"
					: "Use the selected ranged weapon with dragonfire protection, walk the acid phase, kill the zombified spawn immediately, and disable run before the rapid-fire attack";
			case "scurrius":
				return "Use a rat-bone weapon when selected, avoid falling debris, kill or ignore giant rats according to the phase, and use the food piles when the encounter allows";
			case "obor":
				return "Protect from Melee, avoid standing at low health after a knockback, and continue attacking with the selected setup";
			case "bryophyta":
				return "Protect from Magic, kill the growthlings with the required axe interaction, avoid poison clouds, and resume attacking only after the growthlings are cleared";
			case "brutus":
				return "Use the trough safespot when Ranged, or Protect from Melee when fighting directly, and move clear of every charge and stomp attack";
			case "demonic gorillas":
				return "Switch between demonbane melee and Ranged when the gorilla changes protection prayer, react to its attack-style changes, and move away from the boulder shadow";
			case "tormented demons":
				return "Match the demon's protection prayer with the alternate combat style, exploit the shield-down accuracy phase, and use the appropriate heavy hit during the exposed window";
			case "royal titans":
				return "Use the selected style against the appropriate Titan, react to arena hazards and protection changes, and complete required switches before resuming damage";
			case "shellbane gryphon":
				return "Use the selected reviewed setup, avoid marked arena attacks, and follow the encounter's movement and phase changes before resuming damage";
			case "barrows brothers":
				return "Use magic against the melee brothers, the selected secondary style where appropriate, manage prayer carefully, and enter the tunnels with enough resources for the final brother";
			case "chaos elemental":
				return "Keep inventory slots filled to limit unequipping, avoid the teleport attack when possible, and keep a deep-Wilderness escape plan ready";
			case "chaos fanatic":
				return "Protect from Magic, move away from the green special-attack tiles, and keep a Wilderness escape teleport ready";
			case "crazy archaeologist":
			case "deranged archaeologist":
				return "Protect from Missiles, move several tiles away when the explosive book attack is thrown, and resume attacking after the impact";
			case "duke sucellus":
				return "Prepare both potions before waking Duke, attack from the correct side, step behind a pillar for the gaze attack, and avoid vents and floor hazards";
			case "general graardor":
				return style == SlayerTaskStrategy.CombatStyle.RANGED
					? "Use the selected ranged door-altar or kiting cycle, maintain the required tile rhythm, and clean up the bodyguards after the kill"
					: "Protect from Melee, tank or step under between attacks as the selected melee method requires, and clean up the bodyguards after the kill";
			case "giant mole":
				return "Keep Protect from Melee active, use a light source and locator, and chase each dig while preserving run energy";
			case "maggot king":
				return "Begin with Protect from Missiles, use Fire Magic while the boss is distant, move off each projectile landing tile, cure its poison, then equip the crush switch and use the special attack during punish windows";
			case "phantom muspah":
				return "Switch between the selected ranged and magic options when required, kite the melee phase, use Smite or Sapphire bolts for the prayer shield, and avoid spikes";
			case "leviathan":
				return "Switch prayers for each projectile, use the shadow spell reset when required by the method, move with the enrage orb, and avoid boulders and lightning";
			case "whisperer":
				return "Use magic, manage sanity, enter the shadow realm for each special, use the fast ranged switch on Lost Souls, keep distance or bind after specials, avoid tentacles and waves, and keep moving during enrage";
			case "vardorvis":
				return "Use slash, avoid axes and floor spikes, protect from the head projectile, and keep attacking through enrage without bringing unnecessary antivenom";
			case "commander zilyana":
				return "Use ranged, kite Zilyana around the room without entering melee range, and clean up the bodyguards after each kill";
			case "zulrah":
				return "Use the included ranged and magic switches, change protection prayer and position for each rotation, avoid venom clouds, and kill snakelings only when necessary";
			default:
				return "Follow the boss mechanics while using the selected setup";
		}
	}

	private static void appendSentence(final StringBuilder text, final String value)
	{
		final String clean = safe(value);
		if (clean.isEmpty()) return;
		if (text.length() > 0) text.append(' ');
		text.append(clean);
		final char last = text.charAt(text.length() - 1);
		if (last != '.' && last != '!' && last != '?') text.append('.');
	}

	private static String normalize(final String value)
	{
		return safe(value).toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
	}

	private static boolean containsIgnoreCase(final String text, final String part)
	{
		return text.toLowerCase(Locale.ENGLISH).contains(part.toLowerCase(Locale.ENGLISH));
	}

	private static String safe(final String value)
	{
		return value == null ? "" : value.trim();
	}
}
