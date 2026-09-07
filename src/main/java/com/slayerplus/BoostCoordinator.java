package com.slayerplus;

import lombok.Getter;

public final class BoostCoordinator {
  public static final int TURAEL_AYA_MASTER_ID = 1;

  private BoostCoordinator() {}

  public static Decision nextAssignment(int completedStreak, Preference.BonusMaster bonusMaster) {
    int safeStreak = Math.max(0, completedStreak);
    int completionNumber = safeStreak + 1;
    boolean bonusTask = completionNumber % 10 == 0;
    Preference.BonusMaster selectedBonusMaster =
        bonusMaster == null ? Preference.BonusMaster.KONAR : bonusMaster;
    return new Decision(
        completionNumber,
        bonusTask ? selectedBonusMaster.getMasterId() : TURAEL_AYA_MASTER_ID,
        bonusTask,
        bonusTask ? 10 : completionNumber % 10);
  }

  @Getter
  public static final class Decision {
    private final int completionNumber;
    private final int masterId;
    private final boolean bonusTask;
    private final int cycleStep;

    private Decision(int completionNumber, int masterId, boolean bonusTask, int cycleStep) {
      this.completionNumber = completionNumber;
      this.masterId = masterId;
      this.bonusTask = bonusTask;
      this.cycleStep = cycleStep;
    }

    public String getProgressText() {
      return bonusTask
          ? "Bonus task " + completionNumber
          : "Turael/Aya task " + cycleStep + " of 9";
    }
  }
}
