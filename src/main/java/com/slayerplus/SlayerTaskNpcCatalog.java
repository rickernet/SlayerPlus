package com.slayerplus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Current Slayer assignment-to-NPC aliases used by RuneLite's Slayer task
 * model. Keeping aliases separate from route coordinates allows every cave
 * route to lock onto the correct loaded encounter instead of guessing from a
 * plural task name. Boss routes still use their own strict profile and never
 * inherit ordinary-monster aliases.
 */
public final class SlayerTaskNpcCatalog
{
	private static final String RESOURCE =
		"/com/slayerplus/slayer-task-npcs.tsv";
	private static final CatalogData DATA = loadCatalog();
	private static final Map<String, Set<String>> ALIASES = DATA.aliases;
	private static final Map<String, Set<String>> STANDARD_EXCLUSIONS =
		DATA.exclusions;

	static
	{
		validateOrThrow();
	}

	private SlayerTaskNpcCatalog()
	{
	}

	public static Set<String> aliasesFor(final String taskName)
	{
		final Set<String> aliases = new LinkedHashSet<>();
		addAlias(aliases, taskName);
		addAlias(aliases, singular(normalize(taskName)));

		final Set<String> reviewed = ALIASES.get(taskKey(taskName));
		if (reviewed != null)
		{
			aliases.addAll(reviewed);
		}
		return aliases;
	}

	public static Set<String> aliasesForStandardRoute(final String taskName)
	{
		final Set<String> aliases = new LinkedHashSet<>(aliasesFor(taskName));
		final Set<String> excluded = STANDARD_EXCLUSIONS.get(taskKey(taskName));
		if (excluded != null)
		{
			aliases.removeAll(excluded);
		}
		return aliases;
	}

	public static boolean hasTask(final String taskName)
	{
		return ALIASES.containsKey(taskKey(taskName));
	}

	/** Structural guard for exact-NPC routing and boss/regular isolation. */
	public static void validateOrThrow()
	{
		for (final Map.Entry<String, Set<String>> entry : ALIASES.entrySet())
		{
			if (entry.getKey() == null || entry.getKey().trim().isEmpty()
				|| entry.getValue() == null || entry.getValue().isEmpty())
			{
				throw new IllegalStateException(
					"Slayer NPC alias catalog contains an incomplete task: "
						+ entry.getKey()
				);
			}
			for (final String alias : entry.getValue())
			{
				if (alias == null || alias.trim().isEmpty())
				{
					throw new IllegalStateException(
						"Slayer NPC alias catalog contains a blank alias: "
							+ entry.getKey()
					);
				}
			}
		}

		for (final Map.Entry<String, Set<String>> entry
			: STANDARD_EXCLUSIONS.entrySet())
		{
			final Set<String> base = aliasesFor(entry.getKey());
			final Set<String> standard = aliasesForStandardRoute(entry.getKey());
			for (final String excluded : entry.getValue())
			{
				if (!base.contains(excluded))
				{
					throw new IllegalStateException(
						"Boss exclusion is not present in the base NPC aliases: "
							+ entry.getKey() + " -> " + excluded
					);
				}
				if (standard.contains(excluded))
				{
					throw new IllegalStateException(
						"Boss NPC leaked into a standard Slayer route: "
							+ entry.getKey() + " -> " + excluded
					);
				}
			}
		}
	}

	private static CatalogData loadCatalog()
	{
		final Map<String, Set<String>> aliases = new LinkedHashMap<>();
		final Map<String, Set<String>> exclusions = new LinkedHashMap<>();
		try (InputStream stream = SlayerTaskNpcCatalog.class
			.getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				throw new IllegalStateException("Missing NPC alias resource " + RESOURCE);
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				stream, StandardCharsets.UTF_8)))
			{
				String line;
				int lineNumber = 0;
				while ((line = reader.readLine()) != null)
				{
					lineNumber++;
					if (line.isEmpty() || line.charAt(0) == '#')
					{
						continue;
					}
					final String[] fields = line.split("\\t", -1);
					if (fields.length != 3
						|| (!fields[0].equals("A") && !fields[0].equals("X")))
					{
						throw new IllegalStateException(
							"Invalid NPC alias resource line " + lineNumber);
					}
					final String task = decode(fields[1]);
					final String[] names = decode(fields[2]).split("\\|", -1);
					if (fields[0].equals("A"))
					{
						put(aliases, task, names);
					}
					else
					{
						putExclusions(exclusions, task, names);
					}
				}
			}
		}
		catch (final IOException | RuntimeException ex)
		{
			throw new IllegalStateException("Unable to load NPC aliases", ex);
		}
		return new CatalogData(
			Collections.unmodifiableMap(aliases),
			Collections.unmodifiableMap(exclusions));
	}

	private static String decode(final String value)
	{
		return value.replace("%7C", "|").replace("%0A", "\n")
			.replace("%09", "\t").replace("%25", "%");
	}
	private static void putExclusions(
		final Map<String, Set<String>> map,
		final String taskName,
		final String... npcNames)
	{
		final Set<String> excluded = new LinkedHashSet<>();
		if (npcNames != null)
		{
			for (final String npcName : npcNames)
			{
				addAlias(excluded, npcName);
			}
		}
		final String key = taskKey(taskName);
		final Set<String> immutable = Collections.unmodifiableSet(excluded);
		final Set<String> previous = map.put(key, immutable);
		if (previous != null && !previous.equals(immutable))
		{
			throw new IllegalStateException(
				"Conflicting duplicate Slayer NPC exclusion entry: " + taskName
			);
		}
	}

	private static void put(
		final Map<String, Set<String>> map,
		final String taskName,
		final String... npcNames)
	{
		final Set<String> aliases = new LinkedHashSet<>();
		if (npcNames != null)
		{
			for (final String npcName : npcNames)
			{
				addAlias(aliases, npcName);
			}
		}
		final String key = taskKey(taskName);
		final Set<String> immutable = Collections.unmodifiableSet(aliases);
		final Set<String> previous = map.put(key, immutable);
		if (previous != null && !previous.equals(immutable))
		{
			throw new IllegalStateException(
				"Conflicting duplicate Slayer NPC alias entry: " + taskName
			);
		}
	}

	private static void addAlias(
		final Set<String> aliases,
		final String value)
	{
		final String normalized = normalize(value);
		if (!normalized.isEmpty())
		{
			aliases.add(normalized);
			aliases.add(singular(normalized));
		}
	}

	private static String taskKey(final String value)
	{
		String normalized = singular(normalize(value));
		if (normalized.startsWith("the "))
		{
			normalized = normalized.substring(4);
		}
		return normalized;
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
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceAll("\\s+", " ");
	}

	private static String singular(final String value)
	{
		if (value == null || value.isEmpty())
		{
			return "";
		}
		if (value.endsWith("wolves"))
		{
			return value.substring(0, value.length() - 6) + "wolf";
		}
		if (value.endsWith("elves"))
		{
			return value.substring(0, value.length() - 5) + "elf";
		}
		if (value.endsWith("dwarves"))
		{
			return value.substring(0, value.length() - 7) + "dwarf";
		}
		if (value.endsWith("men"))
		{
			return value.substring(0, value.length() - 3) + "man";
		}
		if (value.endsWith("ies") && value.length() > 4)
		{
			return value.substring(0, value.length() - 3) + "y";
		}
		if (value.endsWith("s")
			&& !value.endsWith("ss")
			&& !value.endsWith("us")
			&& !value.endsWith("is")
			&& value.length() > 3)
		{
			return value.substring(0, value.length() - 1);
		}
		return value;
	}

	private static final class CatalogData
	{
		private final Map<String, Set<String>> aliases;
		private final Map<String, Set<String>> exclusions;

		private CatalogData(
			final Map<String, Set<String>> aliases,
			final Map<String, Set<String>> exclusions)
		{
			this.aliases = aliases;
			this.exclusions = exclusions;
		}
	}
}

