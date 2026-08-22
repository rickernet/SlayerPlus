package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Canonical player-facing names without changing task-matching identities. */
public final class SlayerDisplayText
{
	private static final String TEST_PREFIX = "TEST:";
	private static final String REVIEW_PENDING_SUFFIX =
		" — strategy review pending";
	private static final Map<String, String> CANONICAL_TASK_NAMES =
		createCanonicalTaskNames();

	private SlayerDisplayText()
	{
	}

	public static String taskName(final String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return "Unknown task";
		}

		String task = value.trim();
		final boolean simulated = task.regionMatches(
			true, 0, TEST_PREFIX, 0, TEST_PREFIX.length()
		);
		if (simulated)
		{
			task = task.substring(TEST_PREFIX.length()).trim();
		}

		final String canonical = canonicalTaskName(task);
		return simulated ? TEST_PREFIX + " " + canonical : canonical;
	}

	public static String bankSetupTitle(final String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return "Waiting for Slayer task";
		}

		final String title = value.trim();
		if (title.equalsIgnoreCase("Waiting for Slayer task"))
		{
			return "Waiting for Slayer task";
		}
		if (title.equalsIgnoreCase("Recommended setup"))
		{
			return "Recommended setup";
		}
		if (title.equalsIgnoreCase("Loadout recommendations disabled"))
		{
			return "Loadout recommendations disabled";
		}
		if (title.toLowerCase(Locale.ENGLISH).endsWith(
			REVIEW_PENDING_SUFFIX.toLowerCase(Locale.ENGLISH)))
		{
			final String task = title.substring(
				0, title.length() - REVIEW_PENDING_SUFFIX.length()
			).trim();
			return taskName(task) + REVIEW_PENDING_SUFFIX;
		}
		return taskName(title);
	}

	private static String canonicalTaskName(final String task)
	{
		final String key = normalize(task);
		final String canonical = CANONICAL_TASK_NAMES.get(key);
		if (canonical != null)
		{
			return canonical;
		}

		final String lower = task.toLowerCase(Locale.ENGLISH);
		return lower.isEmpty() ? "Unknown task" : titleCaseWords(lower);
	}

	private static Map<String, String> createCanonicalTaskNames()
	{
		final Map<String, String> names = new LinkedHashMap<>();
		for (final String reviewedName
			: SlayerTaskResearchCatalog.getReviewedTaskNames())
		{
			names.put(normalize(reviewedName), titleCaseWords(reviewedName));
		}
		addBossNames(names, SlayerTaskVariantCatalog.getBossDefinitions());
		addBossNames(names, SlayerTaskVariantCatalog.getDirectBossDefinitions());
		return Collections.unmodifiableMap(names);
	}

	private static void addBossNames(
		final Map<String, String> names,
		final Iterable<SlayerTaskVariantCatalog.EncounterDefinition> definitions)
	{
		for (final SlayerTaskVariantCatalog.EncounterDefinition definition
			: definitions)
		{
			final String displayName = definition.getDisplayName();
			final String canonicalDisplayName = titleCaseWords(displayName);
			names.put(normalize(definition.getEncounterName()), canonicalDisplayName);
			names.put(normalize(displayName), canonicalDisplayName);
		}
	}

	/**
	 * Capitalize each ordinary task-name word while preserving RuneScape's
	 * authored internal casing and punctuation (for example TzHaar,
	 * TzTok-Jad, Kree'arra, and K'ril Tsutsaroth).
	 */
	private static String titleCaseWords(final String value)
	{
		final String[] words = value.trim().split("\\s+");
		final StringBuilder result = new StringBuilder();
		for (final String word : words)
		{
			if (word.isEmpty())
			{
				continue;
			}
			if (result.length() > 0)
			{
				result.append(' ');
			}
			result.append(Character.toUpperCase(word.charAt(0)));
			if (word.length() > 1)
			{
				result.append(word.substring(1));
			}
		}
		return result.toString();
	}

	private static String normalize(final String value)
	{
		return value == null
			? ""
			: value.trim().toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " ");
	}
}
