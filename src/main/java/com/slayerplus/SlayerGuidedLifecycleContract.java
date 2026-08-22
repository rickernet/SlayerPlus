package com.slayerplus;

import java.util.Locale;
import java.util.Objects;

/**
 * Pure lifecycle contract shared by guided-route decisions and regression tests.
 *
 * <p>This class deliberately contains no RuneLite client state.  It makes the two
 * cross-scene invariants explicit and testable without logging into the game:</p>
 *
 * <ol>
 *     <li>Every kind of bank detour retains the exact unfinished assignment,
 *     selected variant, encounter and location.</li>
 *     <li>A completed task returns to its recorded master by preferring a carried
 *     teleport, then a teleport known to be in the scanned bank, then walking.</li>
 * </ol>
 */
final class SlayerGuidedLifecycleContract
{
	private SlayerGuidedLifecycleContract()
	{
	}

	enum BankEndpointKind
	{
		BOOTH,
		CHEST,
		BANKER
	}

	enum MasterReturnPlan
	{
		CARRIED_TELEPORT,
		BANK_OWNED_TELEPORT,
		PHYSICAL_PATH
	}

	static TaskRouteKey taskRouteKey(
		final String assignmentName,
		final SlayerTaskVariant variant,
		final String encounterName,
		final String location)
	{
		return new TaskRouteKey(
			assignmentName,
			variant,
			encounterName,
			location
		);
	}

	/**
	 * Returns the original immutable key for every supported bank endpoint while
	 * the assignment is unfinished.  Null means that no task route may be resumed.
	 */
	static TaskRouteKey resumeTaskAfterBank(
		final TaskRouteKey beforeDetour,
		final int remaining,
		final BankEndpointKind endpointKind)
	{
		if (endpointKind == null
			|| !canResumeTaskAfterBank(beforeDetour, remaining))
		{
			return null;
		}
		return beforeDetour;
	}

	static boolean canResumeTaskAfterBank(
		final TaskRouteKey beforeDetour,
		final int remaining)
	{
		return beforeDetour != null
			&& beforeDetour.isComplete()
			&& remaining > 0;
	}

	/** Source-level precedence for the post-task route to the recorded master. */
	static MasterReturnPlan masterReturnPlan(
		final boolean carriedTeleportAvailable,
		final boolean bankSnapshotAvailable,
		final boolean bankOwnedTeleportAvailable)
	{
		if (carriedTeleportAvailable)
		{
			return MasterReturnPlan.CARRIED_TELEPORT;
		}
		if (bankSnapshotAvailable && bankOwnedTeleportAvailable)
		{
			return MasterReturnPlan.BANK_OWNED_TELEPORT;
		}
		return MasterReturnPlan.PHYSICAL_PATH;
	}

	static final class TaskRouteKey
	{
		private final String assignmentName;
		private final SlayerTaskVariant variant;
		private final String encounterName;
		private final String location;

		private TaskRouteKey(
			final String assignmentName,
			final SlayerTaskVariant variant,
			final String encounterName,
			final String location)
		{
			this.assignmentName = clean(assignmentName);
			this.variant = variant == null
				? SlayerTaskVariant.STANDARD_TASK
				: variant;
			this.encounterName = clean(encounterName);
			this.location = clean(location);
		}

		String getAssignmentName()
		{
			return assignmentName;
		}

		SlayerTaskVariant getVariant()
		{
			return variant;
		}

		String getEncounterName()
		{
			return encounterName;
		}

		String getLocation()
		{
			return location;
		}

		boolean belongsToAssignment(final String currentAssignmentName)
		{
			return normalize(assignmentName).equals(
				normalize(currentAssignmentName)
			);
		}

		boolean isComplete()
		{
			return !assignmentName.isEmpty()
				&& !encounterName.isEmpty()
				&& !location.isEmpty();
		}

		String routeIdentity()
		{
			return "assignment=" + normalize(assignmentName)
				+ "|encounter=" + normalize(encounterName)
				+ "|location=" + normalize(location)
				+ "|variant=" + String.valueOf(variant);
		}

		@Override
		public boolean equals(final Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof TaskRouteKey))
			{
				return false;
			}
			final TaskRouteKey that = (TaskRouteKey) other;
			return normalize(assignmentName).equals(normalize(that.assignmentName))
				&& variant == that.variant
				&& normalize(encounterName).equals(normalize(that.encounterName))
				&& normalize(location).equals(normalize(that.location));
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(
				normalize(assignmentName),
				variant,
				normalize(encounterName),
				normalize(location)
			);
		}

		private static String clean(final String value)
		{
			return value == null ? "" : value.trim();
		}

		private static String normalize(final String value)
		{
			return clean(value).toLowerCase(Locale.ENGLISH);
		}
	}
}
