package com.slayerplus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Applies the individually reviewed 4x7 supply policy for each boss. */
public final class SlayerBossInventoryCatalog
{
	public static final String REVIEW_DATE = "2026-08-13";
	private static final String RESOURCE = "/com/slayerplus/slayer-boss-inventory.tsv";
	private static final Map<String, Policy> POLICIES = loadPolicies();
	private static final Set<String> REVIEWED_BOSS_KEYS = reviewedKeys();

	private SlayerBossInventoryCatalog() { }
	public static Set<String> getReviewedBossKeys() { return REVIEWED_BOSS_KEYS; }

	public static boolean apply(final String taskName,
		final SlayerMethodRules.Builder rules, final SlayerTaskStrategy strategy)
	{
		final String task = canonicalBossKey(taskName);
		if (rules == null || !REVIEWED_BOSS_KEYS.contains(task)) return false;
		Policy policy = POLICIES.get(policyKey(task, strategy));
		if (policy == null) policy = POLICIES.get(task);
		if (policy == null) return false;
		policy.apply(rules);
		return true;
	}

	private static String policyKey(final String task,
		final SlayerTaskStrategy strategy)
	{
		if (strategy == null) return task;
		final SlayerTaskStrategy.CombatStyle style = strategy.getCombatStyle();
		if ((task.equals("k ril tsutsaroth") || task.equals("general graardor"))
			&& style == SlayerTaskStrategy.CombatStyle.RANGED) return task + "#ranged";
		if (task.equals("kalphite queen")
			&& style == SlayerTaskStrategy.CombatStyle.MAGIC) return task + "#magic";
		if (task.equals("vorkath")
			&& style == SlayerTaskStrategy.CombatStyle.MELEE) return task + "#melee";
		if (task.equals("tzkal zuk"))
		{
			final String method = normalize(strategy.getMethod());
			if (method.contains("not afk") || method.contains("completion first"))
				return task + "#safety";
			if (strategy.getCostPolicy() == SlayerTaskStrategy.CostPolicy.EFFICIENT)
				return task + "#efficient";
		}
		return task;
	}

	public static String canonicalBossKey(final String value)
	{
		final String key = normalize(value);
		if (key.equals("cave kraken boss")) return "kraken";
		if (key.equals("crazy archaeologists")) return "crazy archaeologist";
		return key;
	}

	private static Map<String, Policy> loadPolicies()
	{
		final InputStream stream = SlayerBossInventoryCatalog.class.getResourceAsStream(RESOURCE);
		if (stream == null) throw new IllegalStateException("Missing Slayer boss inventory resource " + RESOURCE);
		final Map<String, Policy> definitions = new LinkedHashMap<>();
		final Map<String, Policy> policies = new LinkedHashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
		{
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null)
			{
				lineNumber++;
				if (line.isEmpty() || line.charAt(0) == '#') continue;
				final String[] fields = line.split("\\t", -1);
				if (fields.length == 19 && fields[0].equals("D"))
				{
					definitions.put(fields[1], Policy.parse(fields, lineNumber));
					continue;
				}
				if (fields.length == 3 && fields[0].equals("P"))
				{
					final Policy policy = definitions.get(fields[2]);
					if (policy == null) throw new IllegalStateException(
						"Unknown boss policy " + fields[2] + " at row " + lineNumber);
					policies.put(unescape(fields[1]), policy);
					continue;
				}
				throw new IllegalStateException("Invalid boss policy row " + lineNumber);
			}
		}
		catch (IOException | RuntimeException ex)
		{
			throw new IllegalStateException("Unable to load boss inventory policies", ex);
		}
		return Collections.unmodifiableMap(policies);
	}

	private static Set<String> reviewedKeys()
	{
		final Set<String> keys = new LinkedHashSet<>();
		for (final String key : POLICIES.keySet())
		{
			final int separator = key.indexOf('#');
			keys.add(key.substring(0, separator < 0 ? key.length() : separator));
		}
		return Collections.unmodifiableSet(keys);
	}

	private static String normalize(final String value)
	{
		if (value == null) return "";
		return value.toLowerCase(Locale.ENGLISH).replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ").trim().replaceFirst("^the\\s+", "");
	}

	private static String unescape(final String encoded)
	{
		if (encoded == null || encoded.indexOf('\\') < 0) return encoded;
		final StringBuilder value = new StringBuilder(encoded.length());
		boolean escaped = false;
		for (int i = 0; i < encoded.length(); i++)
		{
			final char c = encoded.charAt(i);
			if (!escaped && c == '\\') { escaped = true; continue; }
			if (escaped)
			{
				switch (c)
				{
					case 't': value.append('\t'); break;
					case 'r': value.append('\r'); break;
					case 'n': value.append('\n'); break;
					case 'p': value.append('|'); break;
					case 'c': value.append(','); break;
					case 's': value.append(';'); break;
					default: value.append(c); break;
				}
				escaped = false;
			}
			else value.append(c);
		}
		if (escaped) value.append('\\');
		return value.toString();
	}

	private static String[] list(final String encoded)
	{
		if (encoded.isEmpty()) return new String[0];
		final String[] values = encoded.split(";", -1);
		for (int i = 0; i < values.length; i++) values[i] = unescape(values[i]);
		return values;
	}

	private static final class Policy
	{
		private int target;
		private Integer restoreSlots;
		private Integer foodSlots;
		private int lootSlots;
		private boolean styleBoost;
		private boolean magicPouch;
		private SlayerMethodRules.LayoutProfile layout;
		private String restore;
		private String restoreFallback;
		private String food;
		private String[] foodAlternatives;
		private SlayerMethodRules.Spellbook spellbook;
		private String spell;
		private boolean runePouch;
		private String fill;
		private final List<Rune> runes = new ArrayList<>();
		private final List<Required> required = new ArrayList<>();

		private static Policy parse(final String[] f, final int line)
		{
			try
			{
				final Policy p = new Policy();
				p.target = Integer.parseInt(f[2]);
				p.restoreSlots = f[3].isEmpty() ? null : Integer.valueOf(f[3]);
				p.foodSlots = f[4].isEmpty() ? null : Integer.valueOf(f[4]);
				p.lootSlots = Integer.parseInt(f[5]);
				p.styleBoost = Boolean.parseBoolean(f[6]);
				p.magicPouch = Boolean.parseBoolean(f[7]);
				p.layout = SlayerMethodRules.LayoutProfile.valueOf(f[8]);
				p.restore = unescape(f[9]);
				p.restoreFallback = unescape(f[10]);
				p.food = unescape(f[11]);
				p.foodAlternatives = list(f[12]);
				p.spellbook = SlayerMethodRules.Spellbook.valueOf(f[13]);
				p.spell = unescape(f[14]);
				p.runePouch = Boolean.parseBoolean(f[15]);
				p.fill = f[16];
				if (!f[17].isEmpty()) for (final String value : f[17].split(";"))
				{
					final String[] rune = value.split(",", -1);
					p.runes.add(new Rune(Integer.parseInt(rune[0]), unescape(rune[1]), Integer.parseInt(rune[2])));
				}
				if (!f[18].isEmpty()) for (final String value : f[18].split("\\|"))
				{
					final String[] item = value.split(",", -1);
					p.required.add(new Required(unescape(item[0]), Integer.parseInt(item[1]),
						Integer.parseInt(item[2]), SlayerMethodRules.InventoryGroup.valueOf(item[3]),
						Boolean.parseBoolean(item[4]), list(item[5])));
				}
				return p;
			}
			catch (RuntimeException ex)
			{
				throw new IllegalStateException("Invalid boss inventory policy at row " + line, ex);
			}
		}

		private void apply(final SlayerMethodRules.Builder rules)
		{
			rules.inventoryTarget(target).noInventoryFill()
				.includeRunePouchForMagic(magicPouch).includeStyleBoost(styleBoost)
				.reservedLootSlots(lootSlots).layout(layout).food(food, foodAlternatives);
			if (restoreSlots != null) rules.restoreSlots(restoreSlots);
			if (foodSlots != null) rules.foodSlots(foodSlots);
			if (!restore.isEmpty())
			{
				rules.restore(restore, restoreFallback);
				if (restoreFallback.isEmpty()) rules.noRestoreFallback();
			}
			if (spellbook != SlayerMethodRules.Spellbook.STRATEGY_DEFINED)
				rules.spell(spellbook, spell);
			rules.runePouch(runePouch);
			for (final Rune rune : runes) rules.pouchRune(rune.id, rune.name, rune.quantity);
			for (final Required item : required) item.apply(rules);
			if (fill.equals("FOOD")) rules.fillRemainingWithFood();
			else if (fill.equals("RESTORE")) rules.fillRemainingWithRestore();
		}
	}

	private static final class Rune
	{
		private final int id;
		private final String name;
		private final int quantity;
		private Rune(final int id, final String name, final int quantity)
		{ this.id = id; this.name = name; this.quantity = quantity; }
	}

	private static final class Required
	{
		private final String name;
		private final int quantity;
		private final int slots;
		private final SlayerMethodRules.InventoryGroup group;
		private final boolean ownedOnly;
		private final String[] alternatives;
		private Required(final String name, final int quantity, final int slots,
			final SlayerMethodRules.InventoryGroup group, final boolean ownedOnly,
			final String[] alternatives)
		{ this.name = name; this.quantity = quantity; this.slots = slots;
			this.group = group; this.ownedOnly = ownedOnly; this.alternatives = alternatives; }
		private void apply(final SlayerMethodRules.Builder rules)
		{
			if (ownedOnly) rules.ownedOnlyItem(name, quantity, group, alternatives);
			else if (slots > 1) rules.requiredSlots(name, slots, group, alternatives);
			else rules.requiredItem(name, quantity, group, alternatives);
		}
	}
}
