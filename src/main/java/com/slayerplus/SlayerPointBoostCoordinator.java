package com.slayerplus;

/** Pure task-number policy for Turael/Aya point boosting. */
public final class SlayerPointBoostCoordinator
{
	public static final int TURAEL_AYA_MASTER_ID = 1;

	private SlayerPointBoostCoordinator()
	{
	}

	public static Decision nextAssignment(
		final int completedStreak,
		final SlayerPreference.BonusMaster bonusMaster)
	{
		final int safeStreak = Math.max(0, completedStreak);
		final int completionNumber = safeStreak + 1;
		final boolean bonusTask = completionNumber % 10 == 0;
		final SlayerPreference.BonusMaster selectedBonusMaster =
			bonusMaster == null
				? SlayerPreference.BonusMaster.KONAR
				: bonusMaster;
		return new Decision(
			completionNumber,
			bonusTask ? selectedBonusMaster.getMasterId()
				: TURAEL_AYA_MASTER_ID,
			bonusTask,
			bonusTask ? 10 : completionNumber % 10
		);
	}

	public static final class Decision
	{
		private final int completionNumber;
		private final int masterId;
		private final boolean bonusTask;
		private final int cycleStep;

		private Decision(
			final int completionNumber,
			final int masterId,
			final boolean bonusTask,
			final int cycleStep)
		{
			this.completionNumber = completionNumber;
			this.masterId = masterId;
			this.bonusTask = bonusTask;
			this.cycleStep = cycleStep;
		}

		public int getCompletionNumber() { return completionNumber; }
		public int getMasterId() { return masterId; }
		public boolean isBonusTask() { return bonusTask; }
		public int getCycleStep() { return cycleStep; }

		public String getProgressText()
		{
			return bonusTask
				? "Bonus task " + completionNumber
				: "Turael/Aya task " + cycleStep + " of 9";
		}
	}
}
