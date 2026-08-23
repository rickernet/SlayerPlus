package com.slayerplus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

class SlayerTaskChatUpdate {
  private static final Pattern ASSIGNMENT =
      Pattern.compile(
          "^you(?:'|\u2019)re assigned to kill (.+?);\\s*(?:only\\s+)?([\\d,]+) more to go\\.?$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern STATUS =
      Pattern.compile(
          "^you(?:'|\u2019)ve completed ([\\d,]+) tasks? in a row and currently have a total of"
              + " ([\\d,]+) points?\\.?$",
          Pattern.CASE_INSENSITIVE);
  private final String taskName;
  private final int remaining;
  private final int streak;
  private final int points;

  private SlayerTaskChatUpdate(String taskName, int remaining, int streak, int points) {
    this.taskName = taskName;
    this.remaining = remaining;
    this.streak = streak;
    this.points = points;
  }

  static SlayerTaskChatUpdate parse(String rawMessage) {
    if (rawMessage == null) {
      return null;
    }
    String message = Text.removeTags(rawMessage).replace('\u00a0', ' ').trim();
    Matcher assignment = ASSIGNMENT.matcher(message);
    if (assignment.matches()) {
      return new SlayerTaskChatUpdate(
          formatTaskName(assignment.group(1)), parseNumber(assignment.group(2)), -1, -1);
    }
    Matcher status = STATUS.matcher(message);
    if (status.matches()) {
      return new SlayerTaskChatUpdate(
          "", -1, parseNumber(status.group(1)), parseNumber(status.group(2)));
    }
    return null;
  }

  private static int parseNumber(String value) {
    try {
      return Integer.parseInt(value.replace(",", ""));
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  private static String formatTaskName(String value) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    String lower = trimmed.toLowerCase(Locale.ENGLISH);
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }

  String getTaskName() {
    return taskName;
  }

  int getRemaining() {
    return remaining;
  }

  int getStreak() {
    return streak;
  }

  int getPoints() {
    return points;
  }
}
