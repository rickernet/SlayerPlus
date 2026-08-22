package com.slayerplus;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared usability rules for physical teleport items.
 *
 * <p>Every place that promotes an owned item into authoritative travel state
 * must use this policy.  This prevents an inert/uncharged/depleted form from
 * entering Bank Tag slot 1 merely because its display name still matches the
 * requested teleport family.</p>
 */
public final class SlayerTravelItemPolicy
{
	private static final Pattern ZERO_CHARGE_SUFFIX = Pattern.compile(
		".*\\(\\s*0\\s*\\)\\s*$",
		Pattern.CASE_INSENSITIVE
	);

	private SlayerTravelItemPolicy()
	{
	}

	public static boolean isUsableDisplayName(final String displayName)
	{
		if (displayName == null || displayName.trim().isEmpty())
		{
			return false;
		}

		if (ZERO_CHARGE_SUFFIX.matcher(displayName.trim()).matches())
		{
			return false;
		}

		final String normalized = normalize(displayName);
		if (normalized.isEmpty())
		{
			return false;
		}

		return !hasExplicitUnusableState(normalized);
	}

	public static boolean hasExplicitUnusableState(final String value)
	{
		final String normalized = normalize(value);
		if (normalized.isEmpty())
		{
			return true;
		}

		return containsToken(normalized, "inert")
			|| containsToken(normalized, "uncharged")
			|| containsToken(normalized, "depleted")
			|| normalized.endsWith(" no charges")
			|| normalized.contains(" 0 charges");
	}

	private static boolean containsToken(
		final String normalized,
		final String token)
	{
		return normalized.equals(token)
			|| normalized.startsWith(token + " ")
			|| normalized.endsWith(" " + token)
			|| normalized.contains(" " + token + " ");
	}

	private static String normalize(final String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
	}
}
