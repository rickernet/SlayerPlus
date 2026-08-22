package com.slayerplus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/** Loads the reviewed exact route graph bundled with the plugin. */
final class SlayerRoutePlanResource
{
	private static final String RESOURCE =
		"/com/slayerplus/slayer-route-plans.tsv";

	private SlayerRoutePlanResource()
	{
	}

	static Map<SlayerRoutePlanCatalog.RouteKey, SlayerRoutePlan> load()
	{
		final Map<String, PlanData> plans = new LinkedHashMap<>();
		final List<KeyData> keys = new ArrayList<>();
		try (InputStream stream = SlayerRoutePlanResource.class
			.getResourceAsStream(RESOURCE))
		{
			if (stream == null)
			{
				throw new IllegalStateException("Missing route-plan resource " + RESOURCE);
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
					final String[] raw = line.split("\\t", -1);
					final String[] fields = new String[raw.length];
					for (int i = 0; i < raw.length; i++)
					{
						fields[i] = decode(raw[i]);
					}
					readRecord(fields, lineNumber, plans, keys);
				}
			}
		}
		catch (final IOException ex)
		{
			throw new IllegalStateException("Unable to read route-plan resource", ex);
		}

		final Map<String, SlayerRoutePlan> built = new LinkedHashMap<>();
		for (final PlanData data : plans.values())
		{
			built.put(data.id, data.build());
		}
		final Map<SlayerRoutePlanCatalog.RouteKey, SlayerRoutePlan> result =
			new LinkedHashMap<>();
		for (final KeyData key : keys)
		{
			final SlayerRoutePlan plan = built.get(key.planId);
			if (plan == null || result.putIfAbsent(key.key(), plan) != null)
			{
				throw new IllegalStateException("Invalid or duplicate route key " + key);
			}
		}
		if (result.isEmpty())
		{
			throw new IllegalStateException("Route-plan resource is empty");
		}
		return Collections.unmodifiableMap(result);
	}

	private static void readRecord(
		final String[] f,
		final int line,
		final Map<String, PlanData> plans,
		final List<KeyData> keys)
	{
		try
		{
			switch (f[0])
			{
				case "K":
					require(f, 5);
					keys.add(new KeyData(f[1], f[2], Boolean.parseBoolean(f[3]), f[4]));
					break;
				case "P":
					require(f, 3);
					if (plans.putIfAbsent(f[1], new PlanData(f[1], f[2])) != null)
					{
						throw new IllegalArgumentException("duplicate plan " + f[1]);
					}
					break;
				case "R":
					require(f, 3);
					plan(plans, f[1]).retention.add(parseArea(f[2]));
					break;
				case "L":
					require(f, 13);
					plan(plans, f[1]).addLeg(new LegData(f));
					break;
				case "B":
					require(f, 6);
					plan(plans, f[1]).leg(f[2]).branches.add(
						new BranchData(f[3], f[4], parsePredicate(f[5])));
					break;
				default:
					throw new IllegalArgumentException("unknown record " + f[0]);
			}
		}
		catch (final RuntimeException ex)
		{
			throw new IllegalStateException(
				"Invalid route-plan resource line " + line, ex);
		}
	}

	private static PlanData plan(
		final Map<String, PlanData> plans,
		final String id)
	{
		final PlanData result = plans.get(id);
		if (result == null)
		{
			throw new IllegalArgumentException("unknown plan " + id);
		}
		return result;
	}

	private static void require(final String[] fields, final int count)
	{
		if (fields.length != count)
		{
			throw new IllegalArgumentException(
				"expected " + count + " fields, got " + fields.length);
		}
	}

	private static SlayerRoutePredicate parsePredicate(final String expression)
	{
		if (expression == null || expression.isEmpty())
		{
			return null;
		}
		if (expression.equals("a"))
		{
			return SlayerRoutePredicate.always();
		}
		if (expression.length() < 3 || expression.charAt(1) != '('
			|| expression.charAt(expression.length() - 1) != ')')
		{
			throw new IllegalArgumentException("bad predicate " + expression);
		}
		final char kind = expression.charAt(0);
		final String body = expression.substring(2, expression.length() - 1);
		switch (kind)
		{
			case 'w': return SlayerRoutePredicate.worldArea(parseArea(body));
			case 't': return SlayerRoutePredicate.templateArea(parseArea(body));
			case 'c':
				final List<String> layers = splitTopLevel(body, '>');
				if (layers.size() != 2)
				{
					throw new IllegalArgumentException("bad layer transition");
				}
				return SlayerRoutePredicate.coordinateLayerTransition(
					parseLayer(layers.get(0)), parseLayer(layers.get(1)));
			case 'o': return SlayerRoutePredicate.trackedObjectAction(parseObject(body));
			case 'i': return SlayerRoutePredicate.observedObjectInteraction(parseObject(body));
			case 'v': return SlayerRoutePredicate.visibleWidgetComponent(Integer.parseInt(body));
			case 'g': return SlayerRoutePredicate.visibleWidgetGroup(Integer.parseInt(body));
			case 'n': return SlayerRoutePredicate.exactNpc(parseStrings(body));
			case 'y': return SlayerRoutePredicate.anyOf(parseChildren(body));
			case 'l': return SlayerRoutePredicate.allOf(parseChildren(body));
			default: throw new IllegalArgumentException("unknown predicate " + kind);
		}
	}

	private static List<SlayerRoutePredicate> parseChildren(final String body)
	{
		final List<SlayerRoutePredicate> result = new ArrayList<>();
		for (final String child : splitTopLevel(body, ';'))
		{
			result.add(parsePredicate(child));
		}
		return result;
	}

	private static List<String> splitTopLevel(final String value, final char delimiter)
	{
		final List<String> result = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < value.length(); i++)
		{
			final char c = value.charAt(i);
			if (c == '(') depth++;
			else if (c == ')') depth--;
			else if (c == delimiter && depth == 0)
			{
				result.add(value.substring(start, i));
				start = i + 1;
			}
		}
		result.add(value.substring(start));
		return result;
	}

	private static SlayerRoutePredicate.SpatialArea parseArea(final String value)
	{
		final String[] p = value.split(",", -1);
		if (p.length != 6)
		{
			throw new IllegalArgumentException("bad area " + value);
		}
		final int minX = Integer.parseInt(p[1]);
		final int maxX = Integer.parseInt(p[2]);
		final int minY = Integer.parseInt(p[3]);
		final int maxY = Integer.parseInt(p[4]);
		final int plane = Integer.parseInt(p[5]);
		return p[0].equals("W")
			? SlayerRoutePredicate.SpatialArea.world(minX, maxX, minY, maxY, plane)
			: SlayerRoutePredicate.SpatialArea.template(minX, maxX, minY, maxY, plane);
	}

	private static SlayerRoutePredicate.CoordinateLayer parseLayer(final String value)
	{
		final String[] p = value.split(",", -1);
		if (p.length != 3)
		{
			throw new IllegalArgumentException("bad layer " + value);
		}
		final int[] regions = ints(p[2], '~');
		return p[0].equals("W")
			? SlayerRoutePredicate.CoordinateLayer.worldRegions(
				Integer.parseInt(p[1]), regions)
			: SlayerRoutePredicate.CoordinateLayer.templateRegions(
				Integer.parseInt(p[1]), regions);
	}

	private static SlayerRoutePredicate.ObjectActionSpec parseObject(final String value)
	{
		final String[] p = value.split("\\|", -1);
		if (p.length != 3)
		{
			throw new IllegalArgumentException("bad object action " + value);
		}
		final Set<Integer> ids = new LinkedHashSet<>();
		for (final int id : ints(p[0], '~'))
		{
			ids.add(id);
		}
		return SlayerRoutePredicate.ObjectActionSpec.reviewed(
			ids, parseStrings(p[1]), parseStrings(p[2]));
	}

	private static int[] ints(final String value, final char delimiter)
	{
		if (value.isEmpty())
		{
			return new int[0];
		}
		final String[] values = value.split(java.util.regex.Pattern.quote(
			Character.toString(delimiter)));
		final int[] result = new int[values.length];
		for (int i = 0; i < values.length; i++)
		{
			result[i] = Integer.parseInt(values[i]);
		}
		return result;
	}

	private static Collection<String> parseStrings(final String value)
	{
		if (value.isEmpty())
		{
			return Collections.emptyList();
		}
		final List<String> result = new ArrayList<>();
		for (final String part : value.split("~", -1))
		{
			result.add(decode(part));
		}
		return result;
	}

	private static List<WorldPoint> parsePoints(final String value)
	{
		if (value.isEmpty())
		{
			return Collections.emptyList();
		}
		final List<WorldPoint> result = new ArrayList<>();
		for (final String point : value.split(";", -1))
		{
			final int[] values = ints(point, ',');
			if (values.length != 3)
			{
				throw new IllegalArgumentException("bad point " + point);
			}
			result.add(new WorldPoint(values[0], values[1], values[2]));
		}
		return result;
	}

	private static String decode(final String value)
	{
		final StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++)
		{
			if (value.charAt(i) == '%' && i + 2 < value.length())
			{
				final int hi = Character.digit(value.charAt(i + 1), 16);
				final int lo = Character.digit(value.charAt(i + 2), 16);
				if (hi >= 0 && lo >= 0)
				{
					out.append((char) ((hi << 4) | lo));
					i += 2;
					continue;
				}
			}
			out.append(value.charAt(i));
		}
		return out.toString();
	}

	private static final class KeyData
	{
		private final String task;
		private final String location;
		private final boolean boss;
		private final String planId;

		private KeyData(
			final String task,
			final String location,
			final boolean boss,
			final String planId)
		{
			this.task = task;
			this.location = location;
			this.boss = boss;
			this.planId = planId;
		}

		private SlayerRoutePlanCatalog.RouteKey key()
		{
			return SlayerRoutePlanCatalog.RouteKey.of(task, location, boss);
		}

		@Override
		public String toString()
		{
			return task + "|" + location + "|" + boss;
		}
	}

	private static final class PlanData
	{
		private final String id;
		private final String start;
		private final List<SlayerRoutePredicate.SpatialArea> retention =
			new ArrayList<>();
		private final Map<String, LegData> legs = new LinkedHashMap<>();

		private PlanData(final String id, final String start)
		{
			this.id = id;
			this.start = start;
		}

		private void addLeg(final LegData leg)
		{
			if (legs.putIfAbsent(leg.id, leg) != null)
			{
				throw new IllegalArgumentException("duplicate leg " + leg.id);
			}
		}

		private LegData leg(final String legId)
		{
			final LegData result = legs.get(legId);
			if (result == null)
			{
				throw new IllegalArgumentException("unknown leg " + legId);
			}
			return result;
		}

		private SlayerRoutePlan build()
		{
			final SlayerRoutePlan.Builder builder = SlayerRoutePlan.builder(id)
				.startAt(start)
				.activityRetention(SlayerRoutePlan.ActivityRetention.of(
					retention.toArray(new SlayerRoutePredicate.SpatialArea[0])));
			for (final LegData leg : legs.values())
			{
				builder.addLeg(leg.build());
			}
			return builder.build();
		}
	}

	private static final class LegData
	{
		private final String id;
		private final SlayerRoutePlan.LegKind kind;
		private final String guidance;
		private final String targetLabel;
		private final int radius;
		private final List<WorldPoint> points;
		private final String instruction;
		private final SlayerRoutePredicate readiness;
		private final SlayerRoutePredicate completion;
		private final String terminalLabel;
		private final SlayerRoutePredicate terminal;
		private final List<BranchData> branches = new ArrayList<>();

		private LegData(final String[] f)
		{
			id = f[2];
			kind = SlayerRoutePlan.LegKind.valueOf(f[3]);
			guidance = f[4];
			targetLabel = f[5];
			radius = Integer.parseInt(f[6]);
			points = parsePoints(f[7]);
			instruction = f[8];
			readiness = parsePredicate(f[9]);
			completion = parsePredicate(f[10]);
			terminalLabel = f[11];
			terminal = parsePredicate(f[12]);
		}

		private SlayerRoutePlan.Leg build()
		{
			final SlayerRoutePlan.LegBuilder builder = SlayerRoutePlan.Leg.builder(id, kind)
				.guidance(guidance);
			if (!targetLabel.isEmpty())
			{
				builder.target(points.isEmpty()
					? SlayerRoutePlan.RouteTarget.instruction(targetLabel)
					: SlayerRoutePlan.RouteTarget.points(targetLabel, points, radius));
			}
			if (kind == SlayerRoutePlan.LegKind.TERMINAL)
			{
				builder.terminal(SlayerRoutePlan.TerminalSpec.of(terminalLabel, terminal));
			}
			else
			{
				builder.completeWhen(completion);
				if (kind == SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
				{
					builder.interactionInstruction(instruction).readyWhen(readiness);
				}
				for (final BranchData branch : branches)
				{
					builder.thenWhen(branch.label, branch.condition, branch.next);
				}
			}
			return builder.build();
		}
	}

	private static final class BranchData
	{
		private final String label;
		private final String next;
		private final SlayerRoutePredicate condition;

		private BranchData(
			final String label,
			final String next,
			final SlayerRoutePredicate condition)
		{
			this.label = label;
			this.next = next;
			this.condition = condition;
		}
	}
}
