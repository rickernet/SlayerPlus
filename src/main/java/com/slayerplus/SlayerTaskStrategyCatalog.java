package com.slayerplus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Selects an individually reviewed task strategy. The exact reviewed profile
 * declarations live in the adjacent readable TSV; Java retains the selection
 * policy, location overrides, safety gates, and validation.
 */
public final class SlayerTaskStrategyCatalog
{
	private static final String DATA_RESOURCE =
		"/com/slayerplus/slayer-task-strategies.tsv";
	private static final Map<String, Profiles> DATA = loadData();
	private static final Map<String, Profiles> PROFILES = baseProfiles(DATA);
	private static final Set<String> COVERED_TASKS =
		SlayerTaskResearchCatalog.getReviewedTaskNames();
	private static final Set<String> REVIEWED_TASK_KEYS =
		SlayerTaskResearchCatalog.getReviewedTaskKeys();

	private SlayerTaskStrategyCatalog() { }

	public static SlayerTaskStrategy resolve(
		final String taskName,
		final SlayerPreference.Playstyle playstyle,
		final SlayerPreference.Cannon cannonPreference,
		final SlayerPreference.Burst burstPreference,
		final SlayerPreference.CombatStyle combatPreference,
		final String location,
		final boolean wilderness)
	{
		final String task = canonicalTaskKey(taskName);
		if (!REVIEWED_TASK_KEYS.contains(task)) return unreviewed(taskName);
		if (wilderness)
		{
			return wildernessStrategyFor(
				taskName, task, playstyle, cannonPreference,
				burstPreference, combatPreference
			);
		}
		final Profiles base = PROFILES.get(task);
		if (base == null) return unreviewed(taskName);
		final Profiles profiles = profilesForLocation(
			task, normalize(location), base
		);
		SlayerTaskStrategy selected = null;
		if (combatPreference == SlayerPreference.CombatStyle.PREFER_MAGIC
			&& profiles.magic == null)
		{
			final SlayerTaskStrategy automatic = profiles.selectAutomatic(playstyle);
			if (automatic != null && !automatic.isBoss()
				&& automatic.getCombatStyle()
					!= SlayerTaskStrategy.CombatStyle.HYBRID)
			{
				final SlayerTaskStrategy elemental =
					SlayerElementalWeaknessCatalog.preferenceStrategy(taskName, false);
				if (elemental != null)
				{
					selected = elemental.withSelectionNote(
						"Magic preference applied using the current Wiki-listed elemental weakness; Automatic keeps the reviewed practical method."
					);
				}
			}
		}
		if (selected == null)
		{
			selected = profiles.select(
				playstyle, cannonPreference, burstPreference, combatPreference
			);
		}
		final SlayerTaskTravelAuditCatalog.Entry audit =
			SlayerTaskTravelAuditCatalog.find(taskName);
		final String cannonAudit = audit == null ? "" : normalize(audit.getCannon());
		if (selected != null && !selected.isBoss()
			&& cannonPreference == SlayerPreference.Cannon.PREFER
			&& !selected.hasTag(SlayerTaskStrategy.MethodTag.CANNON)
			&& locationAllowsCannon(taskName, location)
			&& cannonSupportsSelectedCombat(taskName, selected)
			&& (cannonAudit.contains("recommended")
				|| cannonAudit.contains("optional")))
		{
			selected = selected.withAdditionalTags(
				SlayerTaskStrategy.MethodTag.CANNON
			).withSelectionNote(
				"Cannon preference applied because the individually audited task location permits a dwarf multicannon."
			);
		}
		return selected;
	}

	private static boolean cannonSupportsSelectedCombat(
		final String taskName,
		final SlayerTaskStrategy strategy)
	{
		return strategy != null
			&& (!canonicalTaskKey(taskName).equals("greater demons")
				|| strategy.getCombatStyle() != SlayerTaskStrategy.CombatStyle.MELEE);
	}

	private static boolean locationAllowsCannon(
		final String taskName,
		final String location)
	{
		final String area = normalize(location);
		final String task = canonicalTaskKey(taskName);
		if (task.equals("greater demons") && area.contains("karuulm")) return true;
		return !area.contains("catacombs")
			&& !area.contains("slayer tower")
			&& !area.contains("fremennik slayer dungeon")
			&& !area.contains("karuulm")
			&& !area.contains("kraken")
			&& !area.contains("god wars")
			&& !area.contains("mos le harmless");
	}

	public static SlayerTaskStrategy legacy(final String method)
	{
		return unreviewed("Legacy recommendation");
	}

	public static boolean hasExplicitStrategy(final String taskName)
	{
		return REVIEWED_TASK_KEYS.contains(canonicalTaskKey(taskName));
	}

	public static Set<String> getCoveredTaskNames() { return COVERED_TASKS; }
	public static int getCurrentTaskCount() { return REVIEWED_TASK_KEYS.size(); }
	public static int getReviewedTaskCount() { return REVIEWED_TASK_KEYS.size(); }
	public static int getReviewProgressPercent() { return 100; }

	public static List<String> getCurrentTaskNames()
	{
		return Collections.unmodifiableList(new ArrayList<>(COVERED_TASKS));
	}

	public static SlayerTaskResearchCatalog.Entry getResearchRecord(
		final String taskName)
	{
		return SlayerTaskResearchCatalog.find(canonicalTaskKey(taskName));
	}

	public static List<String> getMissingCurrentTasks()
	{
		final List<String> missing = new ArrayList<>();
		for (final String task : COVERED_TASKS)
		{
			if (!PROFILES.containsKey(canonicalTaskKey(task))) missing.add(task);
		}
		return Collections.unmodifiableList(missing);
	}

	private static SlayerTaskStrategy wildernessStrategyFor(
		final String taskName,
		final String task,
		final SlayerPreference.Playstyle playstyle,
		final SlayerPreference.Cannon cannonPreference,
		final SlayerPreference.Burst burstPreference,
		final SlayerPreference.CombatStyle combatPreference)
	{
		final SlayerTaskResearchCatalog.Entry research =
			SlayerTaskResearchCatalog.find(taskName);
		if (research == null || !research.isWildernessReviewed())
			return unreviewedWilderness(taskName);
		final Profiles base = PROFILES.get(task);
		if (base != null)
		{
			final SlayerTaskStrategy selected = base.select(
				playstyle, cannonPreference, burstPreference, combatPreference
			);
			if (selected != null
				&& selected.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS)
				&& selected.getCostPolicy() == SlayerTaskStrategy.CostPolicy.LOW_RISK)
			{
				return selected.withSelectionNote(
					"Reviewed low-risk Wilderness profile applied."
				);
			}
		}
		final Profiles wilderness = DATA.get("wilderness:" + task);
		if (wilderness != null)
		{
			return wilderness.select(
				playstyle, cannonPreference, burstPreference, combatPreference
			).withSelectionNote(
				"Krystilia assignment: individually reviewed LOW_RISK Wilderness branch applied."
			);
		}
		return unreviewedWilderness(taskName);
	}

	private static Profiles profilesForLocation(
		final String task,
		final String location,
		final Profiles fallback)
	{
		if (task.equals("ankou"))
		{
			if (location.contains("stronghold slayer cave"))
				return data("location:ankou:stronghold_slayer_cave", fallback);
			if (location.contains("stronghold of security"))
				return data("location:ankou:stronghold_of_security", fallback);
		}
		if (task.equals("abyssal demons") && location.contains("slayer tower"))
			return data("location:abyssal_demons:slayer_tower", fallback);
		if (task.equals("bloodveld"))
		{
			if (location.contains("meiyerditch") || location.contains("buccaneer"))
				return data("location:bloodveld:multi_cannon", fallback);
			if (location.contains("iorwerth") || location.contains("stronghold slayer cave"))
				return data("location:bloodveld:single_cannon", fallback);
			if (location.contains("catacombs"))
				return data("location:bloodveld:catacombs", fallback);
			if (location.contains("slayer tower"))
				return data("location:bloodveld:slayer_tower", fallback);
			if (location.contains("god wars dungeon"))
				return data("location:bloodveld:god_wars", fallback);
		}
		if (task.equals("aberrant spectres"))
		{
			if (location.contains("stronghold slayer cave"))
				return data("location:aberrant_spectres:stronghold_slayer_cave", fallback);
			if (location.contains("catacombs"))
				return data("location:aberrant_spectres:catacombs", fallback);
		}
		if (task.equals("hellhounds"))
		{
			if (location.contains("stronghold slayer cave")
				|| location.contains("taverley dungeon")
				|| location.contains("karuulm slayer dungeon"))
				return data("location:hellhounds:cannon", fallback);
			if (location.contains("catacombs"))
				return data("location:hellhounds:catacombs", fallback);
		}
		if (task.equals("greater demons") && locationAllowsCannon(task, location))
			return data("location:greater_demons:cannon", fallback);
		if (task.equals("nechryael") && location.contains("iorwerth"))
			return data("location:nechryael:iorwerth", fallback);
		return fallback;
	}

	private static Profiles data(final String key, final Profiles fallback)
	{
		final Profiles profiles = DATA.get(key);
		return profiles == null ? fallback : profiles;
	}

	private static SlayerTaskStrategy unreviewed(final String taskName)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Research pending — loadout disabled"
		).rationale(
			(taskName == null ? "This task" : taskName)
				+ " has not yet passed individual strategy review. SlayerPlus will not invent a broad equipment template."
		).prayerPotionSlots(0).foodSlots(0)
			.strictWeaponProfile(true).build();
	}

	private static SlayerTaskStrategy unreviewedWilderness(final String taskName)
	{
		return SlayerTaskStrategy.builder(
			SlayerTaskStrategy.CombatStyle.HYBRID,
			"Wilderness research pending — loadout disabled"
		).costPolicy(SlayerTaskStrategy.CostPolicy.LOW_RISK)
			.rationale((taskName == null ? "This task" : taskName)
				+ " has no individually reviewed Krystilia profile. The normal-task profile is never reused in the Wilderness.")
			.tags(SlayerTaskStrategy.MethodTag.WILDERNESS)
			.prayerPotionSlots(0).foodSlots(0)
			.strictWeaponProfile(true).build();
	}

	private static Map<String, Profiles> loadData()
	{
		final InputStream stream = SlayerTaskStrategyCatalog.class
			.getResourceAsStream(DATA_RESOURCE);
		if (stream == null)
			throw new IllegalStateException("Missing Slayer strategy resource " + DATA_RESOURCE);
		final Map<String, SlayerTaskStrategy> strategies = new LinkedHashMap<>();
		final Map<String, Profiles> profiles = new LinkedHashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			stream, StandardCharsets.UTF_8)))
		{
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null)
			{
				lineNumber++;
				if (line.isEmpty() || line.charAt(0) == '#') continue;
				final String[] fields = line.split("\\t", -1);
				if (fields.length == 26 && "S".equals(fields[0]))
				{
					final String id = unescape(fields[1]);
					if (strategies.put(id, parseStrategy(fields, lineNumber)) != null)
						throw new IllegalStateException("Duplicate Slayer strategy id " + id);
					continue;
				}
				if (fields.length == 3 && "P".equals(fields[0]))
				{
					final String key = unescape(fields[1]);
					final Profiles group = new Profiles();
					for (final String mapping : fields[2].split(";", -1))
					{
						final int separator = mapping.indexOf('=');
						if (separator <= 0 || separator == mapping.length() - 1)
							throw new IllegalStateException("Invalid Slayer profile mapping at row " + lineNumber);
						final String id = mapping.substring(separator + 1);
						final SlayerTaskStrategy strategy = strategies.get(id);
						if (strategy == null)
							throw new IllegalStateException("Unknown Slayer strategy id " + id + " at row " + lineNumber);
						group.set(mapping.substring(0, separator), strategy);
					}
					if (profiles.put(key, group) != null)
						throw new IllegalStateException("Duplicate Slayer profile " + key);
					continue;
				}
				throw new IllegalStateException("Invalid Slayer strategy row "
					+ lineNumber + ": expected an S or P record");
			}
		}
		catch (IOException | RuntimeException ex)
		{
			throw new IllegalStateException("Unable to load reviewed Slayer strategies", ex);
		}
		for (final Map.Entry<String, Profiles> entry : profiles.entrySet())
			entry.getValue().validate(entry.getKey());
		return Collections.unmodifiableMap(profiles);
	}

	private static SlayerTaskStrategy parseStrategy(
		final String[] fields,
		final int lineNumber)
	{
		try
		{
			final SlayerTaskStrategy.Builder builder = SlayerTaskStrategy.builder(
				SlayerTaskStrategy.CombatStyle.valueOf(unescape(fields[2])),
				unescape(fields[5])
			).armourFocus(SlayerTaskStrategy.ArmourFocus.valueOf(unescape(fields[3])))
				.costPolicy(SlayerTaskStrategy.CostPolicy.valueOf(unescape(fields[4])))
				.rationale(unescape(fields[6]))
				.selectionNote(unescape(fields[7]))
				.weapons(list(fields[9]))
				.maxDpsWeapons(list(fields[10]))
				.efficientWeapons(list(fields[11]))
				.optionalItems(list(fields[12]))
				.tags(tags(fields[13]))
				.prayerPotionSlots(Integer.parseInt(fields[14]))
				.foodSlots(Integer.parseInt(fields[15]))
				.damageProfile(SlayerTaskStrategy.DamageProfile.valueOf(fields[16]))
				.expectedDamagePerKill(Double.parseDouble(fields[17]))
				.minimumFoodSlots(Integer.parseInt(fields[18]))
				.inventoryTargetSlots(Integer.parseInt(fields[19]))
				.runePouch(Boolean.parseBoolean(fields[20]))
				.antivenom(Boolean.parseBoolean(fields[21]))
				.stamina(Boolean.parseBoolean(fields[22]))
				.strictWeaponProfile(Boolean.parseBoolean(fields[23]))
				.boss(Boolean.parseBoolean(fields[24]));
			if (Boolean.parseBoolean(fields[25])) builder.reviewed(unescape(fields[8]));
			return builder.build();
		}
		catch (RuntimeException ex)
		{
			throw new IllegalStateException("Invalid Slayer strategy at row " + lineNumber, ex);
		}
	}

	private static String[] list(final String encoded)
	{
		final String value = unescape(encoded);
		return value.isEmpty() ? new String[0] : value.split("; ", -1);
	}

	private static SlayerTaskStrategy.MethodTag[] tags(final String encoded)
	{
		final String[] values = list(encoded);
		final SlayerTaskStrategy.MethodTag[] tags =
			new SlayerTaskStrategy.MethodTag[values.length];
		for (int index = 0; index < values.length; index++)
			tags[index] = SlayerTaskStrategy.MethodTag.valueOf(values[index]);
		return tags;
	}

	private static String unescape(final String encoded)
	{
		if (encoded == null || encoded.indexOf('\\') < 0) return encoded;
		final StringBuilder value = new StringBuilder(encoded.length());
		boolean escaped = false;
		for (int index = 0; index < encoded.length(); index++)
		{
			final char character = encoded.charAt(index);
			if (!escaped && character == '\\')
			{
				escaped = true;
				continue;
			}
			if (escaped)
			{
				switch (character)
				{
					case 't': value.append('\t'); break;
					case 'r': value.append('\r'); break;
					case 'n': value.append('\n'); break;
					case 'p': value.append('|'); break;
					default: value.append(character); break;
				}
				escaped = false;
			}
			else value.append(character);
		}
		if (escaped) value.append('\\');
		return value.toString();
	}

	private static Map<String, Profiles> baseProfiles(final Map<String, Profiles> data)
	{
		final Map<String, Profiles> base = new HashMap<>();
		for (final Map.Entry<String, Profiles> entry : data.entrySet())
		{
			if (entry.getKey().startsWith("base:"))
				base.put(entry.getKey().substring(5), entry.getValue());
		}
		return Collections.unmodifiableMap(base);
	}

	private static String normalize(final String value)
	{
		if (value == null) return "";
		return value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim().replaceFirst("^the\\s+", "");
	}

	private static String canonicalTaskKey(final String value)
	{
		final String key = normalize(value);
		switch (key)
		{
			case "aberrant spectre": return "aberrant spectres";
			case "abyssal demon": return "abyssal demons";
			case "aquanite": return "aquanites";
			case "araxyte": return "araxytes";
			case "aviansie": return "aviansies";
			case "banshee": return "banshees";
			case "basilisk": return "basilisks";
			case "bat": return "bats";
			case "bear": return "bears";
			case "black demon": return "black demons";
			case "black dragon": return "black dragons";
			case "black knight": return "black knights";
			case "bloodvelds": return "bloodveld";
			case "brine rat": return "brine rats";
			case "cave bug": return "cave bugs";
			case "cave crawler": return "cave crawlers";
			case "cave horror": return "cave horrors";
			case "cave slime": return "cave slimes";
			case "chaos druid": return "chaos druids";
			case "crawling hand": return "crawling hands";
			case "crocodile": return "crocodiles";
			case "custodian stalker": return "custodian stalkers";
			case "dagannoths": return "dagannoth";
			case "dark warrior": return "dark warriors";
			case "earth warrior": return "earth warriors";
			case "ent": return "ents";
			case "fever spider": return "fever spiders";
			case "fire giant": return "fire giants";
			case "flesh crawler": return "fleshcrawlers";
			case "green dragon": return "green dragons";
			case "harpie bug swarm": return "harpie bug swarms";
			case "hellhound": return "hellhounds";
			case "hill giant": return "hill giants";
			case "hydra": return "hydras";
			case "ice giant": return "ice giants";
			case "ice warrior": return "ice warriors";
			case "infernal mage": return "infernal mages";
			case "jelly": case "jellyjellies": return "jellies";
			case "kalphite": return "kalphites";
			case "killerwatt": return "killerwatts";
			case "kurasks": return "kurask";
			case "lava dragon": return "lava dragons";
			case "lesser demon": return "lesser demons";
			case "lesser naguas": return "lesser nagua";
			case "magic axe": return "magic axes";
			case "mammoth": return "mammoths";
			case "mogre": return "mogres";
			case "molanisk": return "molanisks";
			case "monkey": return "monkeys";
			case "moss giant": return "moss giants";
			case "mutated zygomite": return "mutated zygomites";
			case "nechryaels": return "nechryael";
			case "ogre": return "ogres";
			case "pirate": return "pirates";
			case "pyrefiend": return "pyrefiends";
			case "revenant": return "revenants";
			case "rockslug": return "rockslugs";
			case "rogue": return "rogues";
			case "scorpion": return "scorpions";
			case "skeleton": return "skeletons";
			case "spider": return "spiders";
			case "suqah": return "suqahs";
			case "troll": return "trolls";
			case "vampyre": return "vampyres";
			case "werewolf": return "werewolves";
			case "wolf": return "wolves";
			case "wyrm": return "wyrms";
			case "zombie": return "zombies";
			case "minions of scabaras": case "scabarite": case "scarabites":
				return "scabarites";
			case "metal dragon": case "bronze dragons": case "iron dragons":
			case "steel dragons": case "mithril dragons":
			case "adamant dragons": case "rune dragons": return "metal dragons";
			default: return key;
		}
	}

	private static final class Profiles
	{
		private SlayerTaskStrategy balanced;
		private SlayerTaskStrategy fast;
		private SlayerTaskStrategy afk;
		private SlayerTaskStrategy profit;
		private SlayerTaskStrategy melee;
		private SlayerTaskStrategy ranged;
		private SlayerTaskStrategy magic;
		private SlayerTaskStrategy burst;
		private SlayerTaskStrategy nonBurst;
		private SlayerTaskStrategy cannon;
		private SlayerTaskStrategy nonCannon;

		private void set(final String role, final SlayerTaskStrategy value)
		{
			switch (role)
			{
				case "balanced": balanced = value; break;
				case "fast": fast = value; break;
				case "afk": afk = value; break;
				case "profit": profit = value; break;
				case "melee": melee = value; break;
				case "ranged": ranged = value; break;
				case "magic": magic = value; break;
				case "burst": burst = value; break;
				case "non_burst": nonBurst = value; break;
				case "cannon": cannon = value; break;
				case "non_cannon": nonCannon = value; break;
				default: throw new IllegalStateException("Unknown strategy role " + role);
			}
		}

		private void validate(final String key)
		{
			if (balanced == null || fast == null || afk == null || profit == null)
				throw new IllegalStateException("Incomplete Slayer strategy group " + key);
		}

		private SlayerTaskStrategy select(
			final SlayerPreference.Playstyle playstyle,
			final SlayerPreference.Cannon cannonPreference,
			final SlayerPreference.Burst burstPreference,
			final SlayerPreference.CombatStyle combatPreference)
		{
			SlayerTaskStrategy selected = selectCombatPreference(combatPreference);
			final boolean explicitCombat = selected != null;
			String note = "";
			if (!explicitCombat) selected = selectAutomatic(playstyle);
			if (!explicitCombat && cannonPreference == SlayerPreference.Cannon.PREFER
				&& cannon != null) selected = cannon;
			if (!explicitCombat && burstPreference == SlayerPreference.Burst.PREFER
				&& burst != null) selected = burst;
			if (violatesEnabledPreferences(selected, cannonPreference, burstPreference))
			{
				selected = safePreferenceFallback(
					selected, cannonPreference, burstPreference
				);
				note = "Disabled cannon/burst preferences were enforced with a reviewed compatible fallback.";
			}
			selected = applyPlaystyleLoadoutPolicy(selected, playstyle);
			final String authoredNote = selected.getSelectionNote();
			if (note.isEmpty() && explicitCombat)
				note = combatPreference + " applied because this task has a reviewed viable "
					+ selected.getCombatStyle().getLabel() + " setup.";
			if (note.isEmpty())
			{
				note = "Automatic selected the reviewed "
					+ selected.getCombatStyle().getLabel() + " method."
					+ (selected.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE)
						? " Burst/Barrage preference permits this method." : "")
					+ (selected.hasTag(SlayerTaskStrategy.MethodTag.CANNON)
						? " Cannon preference permits this method." : "");
			}
			final String generated = note + playstyleLoadoutNote(playstyle, selected);
			return selected.withSelectionNote(
				authoredNote == null || authoredNote.trim().isEmpty()
					? generated : generated + " " + authoredNote.trim()
			);
		}

		private SlayerTaskStrategy applyPlaystyleLoadoutPolicy(
			final SlayerTaskStrategy strategy,
			final SlayerPreference.Playstyle playstyle)
		{
			if (strategy == null) return null;
			if (strategy.getCostPolicy() == SlayerTaskStrategy.CostPolicy.LOW_RISK
				|| strategy.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS)) return strategy;
			final SlayerPreference.Playstyle resolved = playstyle == null
				? SlayerPreference.Playstyle.FAST_XP : playstyle;
			if (resolved == SlayerPreference.Playstyle.PROFIT)
			{
				return strategy.withLoadoutPolicy(
					strategy.getArmourFocus(), SlayerTaskStrategy.CostPolicy.EFFICIENT
				);
			}
			return strategy.withLoadoutPolicy(
				SlayerTaskStrategy.ArmourFocus.DAMAGE,
				SlayerTaskStrategy.CostPolicy.MAX_DPS
			);
		}

		private String playstyleLoadoutNote(
			final SlayerPreference.Playstyle playstyle,
			final SlayerTaskStrategy strategy)
		{
			if (strategy == null
				|| strategy.getCostPolicy() == SlayerTaskStrategy.CostPolicy.LOW_RISK
				|| strategy.hasTag(SlayerTaskStrategy.MethodTag.WILDERNESS)) return "";
			final SlayerPreference.Playstyle resolved = playstyle == null
				? SlayerPreference.Playstyle.FAST_XP : playstyle;
			return resolved == SlayerPreference.Playstyle.PROFIT
				? " Profit uses the efficient-cost equipment policy."
				: " Fast XP forces damage gear and MAX_DPS equipment without considering item or charge cost.";
		}

		private SlayerTaskStrategy selectCombatPreference(
			final SlayerPreference.CombatStyle preference)
		{
			if (preference == null || preference == SlayerPreference.CombatStyle.AUTOMATIC)
				return null;
			switch (preference)
			{
				case PREFER_MELEE: return melee;
				case PREFER_RANGED: return ranged;
				case PREFER_MAGIC: return magic;
				default: return null;
			}
		}

		private boolean violatesEnabledPreferences(
			final SlayerTaskStrategy strategy,
			final SlayerPreference.Cannon cannonPreference,
			final SlayerPreference.Burst burstPreference)
		{
			return strategy != null
				&& ((cannonPreference == SlayerPreference.Cannon.NEVER
					&& strategy.hasTag(SlayerTaskStrategy.MethodTag.CANNON))
					|| (burstPreference == SlayerPreference.Burst.NEVER
					&& strategy.hasTag(SlayerTaskStrategy.MethodTag.BURST_BARRAGE)));
		}

		private SlayerTaskStrategy safePreferenceFallback(
			final SlayerTaskStrategy current,
			final SlayerPreference.Cannon cannonPreference,
			final SlayerPreference.Burst burstPreference)
		{
			final SlayerTaskStrategy[] candidates = {
				current, nonCannon, nonBurst, melee, ranged, magic,
				balanced, fast, afk, profit, cannon, burst
			};
			for (final SlayerTaskStrategy candidate : candidates)
			{
				if (candidate != null && !violatesEnabledPreferences(
					candidate, cannonPreference, burstPreference)) return candidate;
			}
			return current;
		}

		private SlayerTaskStrategy selectAutomatic(
			final SlayerPreference.Playstyle playstyle)
		{
			return playstyle == SlayerPreference.Playstyle.PROFIT ? profit : fast;
		}
	}
}
