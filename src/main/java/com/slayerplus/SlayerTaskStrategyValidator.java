package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight integrity checks for the reviewed task catalog. This can be
 * called by unit tests or a development-only startup assertion without
 * depending on RuneLite APIs.
 */
public final class SlayerTaskStrategyValidator
{
	private SlayerTaskStrategyValidator()
	{
	}

	public static List<String> validate()
	{
		final List<String> errors = new ArrayList<>();

		for (final SlayerTaskResearchCatalog.Entry entry
			: SlayerTaskResearchCatalog.getEntries())
		{
			if (entry.getLocations().isEmpty())
			{
				errors.add(entry.getTaskName() + ": no reviewed location");
				continue;
			}

			final SlayerTaskStrategy automatic =
				SlayerTaskStrategyCatalog.resolve(
					entry.getTaskName(),
					SlayerPreference.Playstyle.FAST_XP,
					SlayerPreference.Cannon.ALLOW,
					SlayerPreference.Burst.ALLOW,
					SlayerPreference.CombatStyle.AUTOMATIC,
					entry.getLocations().get(0),
					false
				);

			validateConcreteStrategy(
				entry.getTaskName() + " automatic",
				automatic,
				errors
			);

			if (entry.isWildernessReviewed())
			{
				final SlayerTaskStrategy wilderness =
					SlayerTaskStrategyCatalog.resolve(
						entry.getTaskName(),
						SlayerPreference.Playstyle.FAST_XP,
						SlayerPreference.Cannon.ALLOW,
						SlayerPreference.Burst.NEVER,
						SlayerPreference.CombatStyle.AUTOMATIC,
						"Wilderness Slayer Cave",
						true
					);

				validateConcreteStrategy(
					entry.getTaskName() + " Wilderness",
					wilderness,
					errors
				);
				if (wilderness.getCostPolicy()
					!= SlayerTaskStrategy.CostPolicy.LOW_RISK)
				{
					errors.add(
						entry.getTaskName()
							+ ": reviewed Wilderness profile is not LOW_RISK"
					);
				}
			}
		}

		validateAutomaticPreferenceOnlyGuardrail(errors);
		validateDisabledPreferenceGuardrails(errors);
		validateExpectedFallbacks(errors);
		validateTzKalZukProfiles(errors);
		return Collections.unmodifiableList(errors);
	}

	public static void validateOrThrow()
	{
		final List<String> errors = validate();
		if (!errors.isEmpty())
		{
			throw new IllegalStateException(
				"SlayerPlus strategy validation failed: " + errors
			);
		}
	}

	private static void validateConcreteStrategy(
		final String label,
		final SlayerTaskStrategy strategy,
		final List<String> errors)
	{
		if (strategy == null)
		{
			errors.add(label + ": null strategy");
			return;
		}
		if (!strategy.isReviewed())
		{
			errors.add(label + ": strategy is not marked reviewed");
		}
		if (strategy.getWeaponPriorities().isEmpty())
		{
			errors.add(label + ": no weapon priorities");
		}
		if (strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE)
			&& !strategy.needsRunePouch())
		{
			errors.add(label + ": burst/barrage profile lacks rune pouch");
		}
		if (strategy.isBoss()
			&& !strategy.hasTag(SlayerTaskStrategy.MethodTag.BOSS))
		{
			// Older unreviewed helpers may not carry structured tags, but every
			// reviewed boss profile should once bosses enter the research gate.
			errors.add(label + ": boss flag lacks BOSS method tag");
		}
		if ((strategy.getDamageProfile()
				== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
				|| strategy.getDamageProfile()
				== SlayerTaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING)
			&& strategy.getRecommendedFoodSlots(200) != 0)
		{
			errors.add(label + ": zero-damage profile still reserves food");
		}
	}

	private static void validateAutomaticPreferenceOnlyGuardrail(
		final List<String> errors)
	{
		/*
		 * Global research invariant: a style that is authored only as a viable
		 * player-preference alternative must never become Automatic just because
		 * the NPC has an elemental/style weakness.  Automatic is reserved for the
		 * researched practical/meta method for the requested playstyle.
		 */
		for (final String taskName : SlayerTaskStrategyCatalog.getCurrentTaskNames())
		{
			final SlayerTaskResearchCatalog.Entry research =
				SlayerTaskResearchCatalog.find(taskName);
			final String location = research == null || research.getLocations().isEmpty()
				? "Not restricted"
				: research.getLocations().get(0);

			for (final SlayerPreference.Playstyle playstyle
				: SlayerPreference.Playstyle.values())
			{
				for (final SlayerPreference.Cannon cannon
					: SlayerPreference.Cannon.values())
				{
					for (final SlayerPreference.Burst burst
						: SlayerPreference.Burst.values())
					{
						final SlayerTaskStrategy automatic =
							SlayerTaskStrategyCatalog.resolve(
								taskName,
								playstyle,
								cannon,
								burst,
								SlayerPreference.CombatStyle.AUTOMATIC,
								location,
								false
							);

						if (automatic != null
							&& automatic.hasTag(
								SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE
							))
						{
							errors.add(
								taskName + ": Automatic selected a preference-only alternative for "
									+ playstyle + "/" + cannon + "/" + burst
							);
						}
					}
				}
			}
		}
	}

	private static void validateDisabledPreferenceGuardrails(
		final List<String> errors)
	{
		/*
		 * Global preference invariant: NEVER means never. Location capability or
		 * a Fast-XP default may not reintroduce a cannon/barrage method after the
		 * player disabled it. This audits the full current catalog rather than a
		 * handful of representative tasks.
		 */
		for (final String taskName : SlayerTaskStrategyCatalog.getCurrentTaskNames())
		{
			final SlayerTaskResearchCatalog.Entry research =
				SlayerTaskResearchCatalog.find(taskName);
			final String location = research == null || research.getLocations().isEmpty()
				? "Not restricted"
				: research.getLocations().get(0);

			for (final SlayerPreference.Playstyle playstyle
				: SlayerPreference.Playstyle.values())
			{
				for (final SlayerPreference.Burst burst
					: SlayerPreference.Burst.values())
				{
					for (final SlayerPreference.CombatStyle combat
						: SlayerPreference.CombatStyle.values())
					{
						final SlayerTaskStrategy strategy =
							SlayerTaskStrategyCatalog.resolve(
								taskName,
								playstyle,
								SlayerPreference.Cannon.NEVER,
								burst,
								combat,
								location,
								false
							);
						if (strategy != null
							&& strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
						{
							errors.add(taskName + ": Cannon.NEVER leaked a cannon method");
						}
					}
				}

				for (final SlayerPreference.Cannon cannon
					: SlayerPreference.Cannon.values())
				{
					for (final SlayerPreference.CombatStyle combat
						: SlayerPreference.CombatStyle.values())
					{
						final SlayerTaskStrategy strategy =
							SlayerTaskStrategyCatalog.resolve(
								taskName,
								playstyle,
								cannon,
								SlayerPreference.Burst.NEVER,
								combat,
								location,
								false
							);
						if (strategy != null
							&& strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE))
						{
							errors.add(taskName + ": Burst.NEVER leaked a burst/barrage method");
						}
					}
				}
			}
		}
	}


	private static void validateTzKalZukProfiles(
		final List<String> errors)
	{
		final SlayerTaskStrategy fast = resolveZuk(
			SlayerPreference.Playstyle.FAST_XP
		);
		final SlayerTaskStrategy profit = resolveZuk(
			SlayerPreference.Playstyle.PROFIT
		);

		validateZukStrategy("Fast XP", fast, errors);
		validateZukStrategy("Profit", profit, errors);

		if (profit != null
			&& profit.getCostPolicy()
				!= SlayerTaskStrategy.CostPolicy.EFFICIENT)
		{
			errors.add(
				"TzKal-Zuk: Profit profile is not resource-efficient"
			);
		}

		validateZukInventory("Fast XP", fast, 2, 7, 9, 2, 1, errors);
		validateZukInventory("Profit", profit, 2, 8, 8, 2, 1, errors);
	}

	private static SlayerTaskStrategy resolveZuk(
		final SlayerPreference.Playstyle playstyle)
	{
		return SlayerTaskStrategyCatalog.resolve(
			"TzKal-Zuk",
			playstyle,
			SlayerPreference.Cannon.NEVER,
			SlayerPreference.Burst.ALLOW,
			SlayerPreference.CombatStyle.AUTOMATIC,
			"Inferno",
			false
		);
	}

	private static void validateZukStrategy(
		final String profile,
		final SlayerTaskStrategy strategy,
		final List<String> errors)
	{
		if (strategy == null)
		{
			errors.add("TzKal-Zuk " + profile + ": null strategy");
			return;
		}

		if (strategy.getCombatStyle()
			!= SlayerTaskStrategy.CombatStyle.RANGED)
		{
			errors.add("TzKal-Zuk " + profile + ": must remain Ranged");
		}

		if (!strategy.isBoss() || !strategy.needsRunePouch())
		{
			errors.add(
				"TzKal-Zuk " + profile
					+ ": full-Inferno boss/rune-pouch requirements were lost"
			);
		}

		final List<String> weapons = strategy.getWeaponPriorities();
		if (weapons.isEmpty()
			|| !"twisted bow".equals(normalize(weapons.get(0)))
			|| !containsNormalized(weapons, "bow of faerdhinen")
			|| !containsNormalized(weapons, "zaryte crossbow")
			|| !containsNormalized(weapons, "armadyl crossbow")
			|| !containsNormalized(weapons, "dragon crossbow")
			|| !containsNormalized(weapons, "rune crossbow"))
		{
			errors.add(
				"TzKal-Zuk " + profile
					+ ": reviewed main-weapon progression changed"
			);
		}

		if (containsNormalized(weapons, "toxic blowpipe")
			|| !containsNormalized(
				strategy.getOptionalItemPriorities(),
				"toxic blowpipe"
			))
		{
			errors.add(
				"TzKal-Zuk " + profile
					+ ": Toxic blowpipe must be a secondary switch, not the main weapon"
			);
		}
	}

	private static void validateZukInventory(
		final String profile,
		final SlayerTaskStrategy strategy,
		final int prayerRegen,
		final int saradominBrews,
		final int restores,
		final int bastions,
		final int staminas,
		final List<String> errors)
	{
		if (strategy == null)
		{
			return;
		}

		final SlayerMethodRules rules = SlayerMethodRuleCatalog.resolve(
			"TzKal-Zuk",
			"Inferno",
			strategy
		);

		if (rules.getSpellbook() != SlayerMethodRules.Spellbook.ANCIENT
			|| !normalize(rules.getPrimarySpell()).contains("blood barrage")
			|| !normalize(rules.getPrimarySpell()).contains("ice barrage")
			|| !rules.requiresRunePouch()
			|| rules.getPouchRunes().size() != 4)
		{
			errors.add(
				"TzKal-Zuk " + profile
					+ ": Blood/Ice Barrage four-rune package is incomplete"
			);
		}

		final java.util.Set<String> runeNames = new java.util.LinkedHashSet<>();
		for (final SlayerMethodRules.PouchRuneRequirement rune : rules.getPouchRunes())
		{
			runeNames.add(normalize(rune.getName()));
		}
		if (!runeNames.contains("soul")
			|| !runeNames.contains("blood")
			|| !runeNames.contains("death")
			|| !runeNames.contains("water")
			|| runeNames.contains("fire"))
		{
			errors.add(
				"TzKal-Zuk " + profile
					+ ": Inferno rune pouch must be Soul/Blood/Death/Water, never Fire"
			);
		}

		assertRequiredSlots(rules, profile, "Toxic blowpipe", 1, errors);
		assertRequiredSlots(rules, profile, "Ancient Magicks weapon", 1, errors);
		assertRequiredSlots(rules, profile, "Mage body switch", 1, errors);
		assertRequiredSlots(rules, profile, "Mage leg switch", 1, errors);
		assertRequiredSlots(rules, profile, "Magic off-hand switch", 1, errors);
		assertRequiredSlots(rules, profile, "Magic damage switch", 1, errors);
		assertRequiredSlots(
			rules, profile, "Prayer regeneration potion", prayerRegen, errors
		);
		assertRequiredSlots(
			rules, profile, "Saradomin brew", saradominBrews, errors
		);
		assertRequiredSlots(
			rules, profile, "Super restore", restores, errors
		);
		assertRequiredSlots(
			rules, profile, "Bastion potion", bastions, errors
		);
		assertRequiredSlots(
			rules, profile, SlayerPotionPolicy.EXTENDED_STAMINA_DISPLAY,
			staminas, errors
		);
		assertRequiredSlots(rules, profile, "Armadyl brew", 0, errors);

		int authoredInventorySlots = rules.requiresRunePouch() ? 1 : 0;
		for (final SlayerMethodRules.RequiredItem item : rules.getRequiredItems())
		{
			authoredInventorySlots += item.getSlotCount();
		}
		if (authoredInventorySlots != 28)
		{
			errors.add(
				"TzKal-Zuk " + profile
					+ ": final Inferno inventory must author exactly 28 slots, found "
					+ authoredInventorySlots
			);
		}
	}

	private static void assertRequiredSlots(
		final SlayerMethodRules rules,
		final String profile,
		final String displayName,
		final int expected,
		final List<String> errors)
	{
		int actual = 0;
		for (final SlayerMethodRules.RequiredItem item
			: rules.getRequiredItems())
		{
			if (normalize(item.getDisplayName())
				.equals(normalize(displayName)))
			{
				actual += item.getSlotCount();
			}
		}

		if (actual != expected)
		{
			errors.add(
				"TzKal-Zuk " + profile + ": " + displayName
					+ " expected " + expected + " slots but found " + actual
			);
		}
	}

	private static boolean containsNormalized(
		final List<String> values,
		final String expected)
	{
		final String target = normalize(expected);
		for (final String value : values)
		{
			if (normalize(value).equals(target))
			{
				return true;
			}
		}
		return false;
	}

	private static String normalize(final String value)
	{
		return value == null
			? ""
			: value.toLowerCase(java.util.Locale.ENGLISH)
				.replace('\u2019', '\'')
				.replaceAll("[^a-z0-9]+", " ")
				.trim()
				.replaceFirst("^the\\s+", "");
	}

	private static void validateExpectedFallbacks(
		final List<String> errors)
	{
		final SlayerTaskStrategy dustNoBurst =
			SlayerTaskStrategyCatalog.resolve(
				"Dust devils",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.NEVER,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Catacombs of Kourend",
				false
			);
		if (dustNoBurst.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE))
		{
			errors.add("Dust devils: Burst.NEVER returned a barrage profile");
		}

		final SlayerTaskStrategy krakenMeleePreference =
			SlayerTaskStrategyCatalog.resolve(
				"Cave kraken",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.PREFER_MELEE,
				"Kraken Cove",
				false
			);
		if (krakenMeleePreference.getCombatStyle()
			!= SlayerTaskStrategy.CombatStyle.MAGIC)
		{
			errors.add("Cave kraken: unsafe melee preference did not fall back to Magic");
		}

		final SlayerTaskStrategy abyssalTower =
			SlayerTaskStrategyCatalog.resolve(
				"Abyssal demons",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.PREFER,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Slayer Tower",
				false
			);
		if (abyssalTower.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE))
		{
			errors.add("Abyssal demons: Slayer Tower returned a multi-target barrage profile");
		}

		final SlayerTaskStrategy kalphiteNoCannon =
			SlayerTaskStrategyCatalog.resolve(
				"Kalphites",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.NEVER,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Kalphite Slayer Cave",
				false
			);
		if (kalphiteNoCannon.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
		{
			errors.add("Kalphites: Cannon.NEVER returned a cannon profile");
		}

		final SlayerTaskStrategy smokeNoCannon =
			SlayerTaskStrategyCatalog.resolve(
				"Smoke devils",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.NEVER,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Smoke Devil Dungeon",
				false
			);
		if (smokeNoCannon.hasTag(SlayerTaskStrategy.MethodTag.CANNON)
			|| !smokeNoCannon.hasTag(
				SlayerTaskStrategy.MethodTag.BURST_BARRAGE
			))
		{
			errors.add(
				"Smoke devils: Cannon.NEVER did not preserve the reviewed no-cannon barrage fallback"
			);
		}

		final SlayerTaskStrategy regularHellhound =
			SlayerTaskStrategyCatalog.resolve(
				"Hellhounds",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Catacombs of Kourend",
				false
			);
		if (regularHellhound.getCombatStyle()
				!= SlayerTaskStrategy.CombatStyle.RANGED
			|| !regularHellhound.hasTag(
				SlayerTaskStrategy.MethodTag.VENATOR
			)
			|| regularHellhound.getWeaponPriorities().isEmpty()
			|| !regularHellhound.getWeaponPriorities().get(0)
				.equals("venator bow")
			|| regularHellhound.getDamageProfile()
				!= SlayerTaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
			|| regularHellhound.getRecommendedFoodSlots(200) != 0)
		{
			errors.add(
				"Hellhounds: Catacombs Automatic must use the reviewed Venator multi-target profile with zero food while protected"
			);
		}

		final SlayerTaskStrategy cannonHellhound =
			SlayerTaskStrategyCatalog.resolve(
				"Hellhounds",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Stronghold Slayer Cave",
				false
			);
		if (!cannonHellhound.hasTag(SlayerTaskStrategy.MethodTag.CANNON)
			|| !cannonHellhound.hasTag(
				SlayerTaskStrategy.MethodTag.SAFESPOT
			)
			|| !cannonHellhound.hasTag(
				SlayerTaskStrategy.MethodTag.AUTOMATIC_STYLE_LOCKED
			)
			|| cannonHellhound.getCombatStyle()
				!= SlayerTaskStrategy.CombatStyle.RANGED
			|| cannonHellhound.getWeaponPriorities().size() < 2
			|| !cannonHellhound.getWeaponPriorities().get(0)
				.equals("toxic blowpipe")
			|| !cannonHellhound.getWeaponPriorities().get(1)
				.equals("scorching bow"))
		{
			errors.add(
				"Hellhounds: Stronghold Automatic must remain the researched Ranged safespot plus cannon method"
			);
		}


		final SlayerTaskStrategy profitStrongholdHellhound =
			SlayerTaskStrategyCatalog.resolve(
				"Hellhounds",
				SlayerPreference.Playstyle.PROFIT,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Stronghold Slayer Cave",
				false
			);
		if (profitStrongholdHellhound.getCombatStyle()
				!= SlayerTaskStrategy.CombatStyle.RANGED
			|| !profitStrongholdHellhound.hasTag(
				SlayerTaskStrategy.MethodTag.SAFESPOT
			)
			|| !profitStrongholdHellhound.hasTag(
				SlayerTaskStrategy.MethodTag.AUTOMATIC_STYLE_LOCKED
			)
			|| profitStrongholdHellhound.hasTag(
				SlayerTaskStrategy.MethodTag.CANNON
			)
			|| profitStrongholdHellhound.getWeaponPrioritiesForPolicy()
				.isEmpty()
			|| !profitStrongholdHellhound.getWeaponPrioritiesForPolicy()
				.get(0).equals("toxic blowpipe"))
		{
			errors.add(
				"Hellhounds: Stronghold Profit Automatic must keep blowpipe-first single-target Ranged safespotting without forcing cannon cost"
			);
		}


		final SlayerTaskStrategy fastMeleeHellhound =
			SlayerTaskStrategyCatalog.resolve(
				"Hellhounds",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.NEVER,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.PREFER_MELEE,
				"Catacombs of Kourend",
				false
			);
		final SlayerTaskStrategy profitMeleeHellhound =
			SlayerTaskStrategyCatalog.resolve(
				"Hellhounds",
				SlayerPreference.Playstyle.PROFIT,
				SlayerPreference.Cannon.NEVER,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.PREFER_MELEE,
				"Catacombs of Kourend",
				false
			);
		if (fastMeleeHellhound.getWeaponPrioritiesForPolicy().isEmpty()
			|| !fastMeleeHellhound.getWeaponPrioritiesForPolicy().get(0)
				.equals("scythe of vitur"))
		{
			errors.add(
				"Hellhounds: Fast XP melee should keep the max-DPS Scythe-first order"
			);
		}
		if (profitMeleeHellhound.getWeaponPrioritiesForPolicy().isEmpty()
			|| !profitMeleeHellhound.getWeaponPrioritiesForPolicy().get(0)
				.equals("emberlight")
			|| profitMeleeHellhound.getCostPolicy()
				!= SlayerTaskStrategy.CostPolicy.EFFICIENT)
		{
			errors.add(
				"Hellhounds: Profit melee must prioritize Emberlight before charge-heavy weapons"
			);
		}

		final SlayerTaskStrategy cerberusAutomatic =
			SlayerTaskStrategyCatalog.resolve(
				"Cerberus",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Cerberus' Lair",
				false
			);
		if (cerberusAutomatic.getCombatStyle()
				!= SlayerTaskStrategy.CombatStyle.MELEE
			|| cerberusAutomatic.hasTag(
				SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE
			))
		{
			errors.add(
				"Cerberus: Automatic must remain the researched melee meta; Water Magic is preference-only"
			);
		}

		final SlayerTaskStrategy gryphonAutomatic =
			SlayerTaskStrategyCatalog.resolve(
				"Gryphons",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Great Conch",
				false
			);
		final SlayerTaskStrategy gryphonMagicPreference =
			SlayerTaskStrategyCatalog.resolve(
				"Gryphons",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.PREFER_MAGIC,
				"Great Conch",
				false
			);
		if (gryphonAutomatic.getCombatStyle()
				!= SlayerTaskStrategy.CombatStyle.MELEE
			|| gryphonMagicPreference.getCombatStyle()
				!= SlayerTaskStrategy.CombatStyle.MAGIC
			|| !gryphonMagicPreference.hasTag(
				SlayerTaskStrategy.MethodTag.PREFERENCE_ONLY_ALTERNATIVE
			))
		{
			errors.add(
				"Gryphons: Automatic must use practical stab melee while Air Magic remains an explicit preference alternative"
			);
		}

		final SlayerTaskStrategy frostFast =
			SlayerTaskStrategyCatalog.resolve(
				"Frost dragons",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Grimstone",
				false
			);
		final SlayerTaskStrategy frostProfit =
			SlayerTaskStrategyCatalog.resolve(
				"Frost dragons",
				SlayerPreference.Playstyle.PROFIT,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Grimstone",
				false
			);
		if (frostFast.getCombatStyle()
				!= SlayerTaskStrategy.CombatStyle.MAGIC
			|| frostProfit.getCombatStyle()
				!= SlayerTaskStrategy.CombatStyle.MELEE
			|| !frostProfit.getMethod().toLowerCase().contains("crush")
			|| frostProfit.getWeaponPriorities().isEmpty()
			|| !frostProfit.getWeaponPriorities().get(0)
				.equals("dragon hunter lance"))
		{
			errors.add(
				"Frost dragons: Fast XP should keep high-DPS Fire Magic while Profit/AFK use the researched Dragon hunter lance-on-Crush melee method"
			);
		}

		final SlayerTaskStrategy reviewedWilderness =
			SlayerTaskStrategyCatalog.resolve(
				"Abyssal demons",
				SlayerPreference.Playstyle.FAST_XP,
				SlayerPreference.Cannon.ALLOW,
				SlayerPreference.Burst.ALLOW,
				SlayerPreference.CombatStyle.AUTOMATIC,
				"Wilderness Slayer Cave",
				true
			);
		if (!reviewedWilderness.isReviewed()
			|| reviewedWilderness.getCostPolicy()
				!= SlayerTaskStrategy.CostPolicy.LOW_RISK
			|| !reviewedWilderness.hasTag(
				SlayerTaskStrategy.MethodTag.WILDERNESS
			))
		{
			errors.add("Abyssal demons: reviewed low-risk Krystilia profile was not selected");
		}
	}
}
