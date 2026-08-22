package com.slayerplus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Small, side-effect-free parser for assignment/status messages emitted by the
 * game.  The live Slayer varps remain authoritative; this only bridges the
 * short window where RuneLite has displayed the message but the varps/profile
 * state have not settled yet.
 */
final class SlayerTaskChatUpdate
{
	private static final Pattern ASSIGNMENT = Pattern.compile(
		"^you(?:'|\u2019)re assigned to kill (.+?);\\s*(?:only\\s+)?([\\d,]+) more to go\\.?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern STATUS = Pattern.compile(
		"^you(?:'|\u2019)ve completed ([\\d,]+) tasks? in a row and currently have a total of ([\\d,]+) points?\\.?$",
		Pattern.CASE_INSENSITIVE
	);

	private final String taskName;
	private final int remaining;
	private final int streak;
	private final int points;

	private SlayerTaskChatUpdate(
		final String taskName,
		final int remaining,
		final int streak,
		final int points)
	{
		this.taskName = taskName;
		this.remaining = remaining;
		this.streak = streak;
		this.points = points;
	}

	static SlayerTaskChatUpdate parse(final String rawMessage)
	{
		if (rawMessage == null)
		{
			return null;
		}

		final String message = Text.removeTags(rawMessage)
			.replace('\u00a0', ' ')
			.trim();
		final Matcher assignment = ASSIGNMENT.matcher(message);
		if (assignment.matches())
		{
			return new SlayerTaskChatUpdate(
				formatTaskName(assignment.group(1)),
				parseNumber(assignment.group(2)),
				-1,
				-1
			);
		}

		final Matcher status = STATUS.matcher(message);
		if (status.matches())
		{
			return new SlayerTaskChatUpdate(
				"",
				-1,
				parseNumber(status.group(1)),
				parseNumber(status.group(2))
			);
		}
		return null;
	}

	private static int parseNumber(final String value)
	{
		try
		{
			return Integer.parseInt(value.replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			return -1;
		}
	}

	private static String formatTaskName(final String value)
	{
		final String trimmed = value == null ? "" : value.trim();
		if (trimmed.isEmpty())
		{
			return "";
		}
		final String lower = trimmed.toLowerCase(Locale.ENGLISH);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	String getTaskName()
	{
		return taskName;
	}

	int getRemaining()
	{
		return remaining;
	}

	int getStreak()
	{
		return streak;
	}

	int getPoints()
	{
		return points;
	}
}
