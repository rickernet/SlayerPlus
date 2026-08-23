package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

public final class BankRoutes {

    private static final WorldPoint INFERNO_PREPARATION_BANK = new WorldPoint(2543, 5141, 0);

    private static final WorldPoint INFERNO_PREPARATION_HOT_VENT_DOOR = new WorldPoint(2495, 5157, 0);

    private static final Set<WorldPoint> INFERNO_PREPARATION_BANK_TARGETS = Collections.singleton(INFERNO_PREPARATION_BANK);

    private static final Set<WorldPoint> INFERNO_PREPARATION_HOT_VENT_TARGETS = Collections.singleton(INFERNO_PREPARATION_HOT_VENT_DOOR);

    private static final WorldPoint SEERS_VILLAGE_BANK_APPROACH = new WorldPoint(2727, 3492, 0);

    private static final int SEERS_VILLAGE_LOCAL_RADIUS = 64;

    private static final Set<WorldPoint> STANDARD_BANKS = createStandardBanks();

    private static final Set<WorldPoint> WILDERNESS_BANKS = createWildernessBanks();

    private BankRoutes() {
    }

    public static WorldPoint getInfernoPreparationBankTarget() {
        return INFERNO_PREPARATION_BANK;
    }

    public static Set<WorldPoint> getInfernoPreparationBankApproachTargets() {
        return INFERNO_PREPARATION_BANK_TARGETS;
    }

    public static WorldPoint getInfernoPreparationHotVentDoorTarget() {
        return INFERNO_PREPARATION_HOT_VENT_DOOR;
    }

    public static Set<WorldPoint> getInfernoPreparationHotVentDoorTargets() {
        return INFERNO_PREPARATION_HOT_VENT_TARGETS;
    }

    public static Set<WorldPoint> getBankTargets(boolean allowWilderness) {
        if (!allowWilderness) {
            return STANDARD_BANKS;
        }
        Set<WorldPoint> targets = new LinkedHashSet<>(STANDARD_BANKS);
        targets.addAll(WILDERNESS_BANKS);
        return Collections.unmodifiableSet(targets);
    }

    public static WorldPoint getPreferredLocalApproachTarget(WorldPoint playerLocation) {
        if (playerLocation == null || playerLocation.getPlane() != SEERS_VILLAGE_BANK_APPROACH.getPlane()) {
            return null;
        }
        var distance = Math.max(Math.abs(playerLocation.getX() - SEERS_VILLAGE_BANK_APPROACH.getX()), Math.abs(playerLocation.getY() - SEERS_VILLAGE_BANK_APPROACH.getY()));
        return distance <= SEERS_VILLAGE_LOCAL_RADIUS ? SEERS_VILLAGE_BANK_APPROACH : null;
    }

    private static Set<WorldPoint> createStandardBanks() {
        return loadBankTargets("standard");
    }

    private static Set<WorldPoint> createWildernessBanks() {
        return loadBankTargets("wilderness");
    }

    private static Set<WorldPoint> loadBankTargets(String group) {
        final Set<WorldPoint> targets = new LinkedHashSet<>();
        for (String[] row : ResourceTable.rows("slayer-bank-targets.tsv", 4)) {
            if (row[0].equals(group)) {
                targets.add(new WorldPoint(Integer.parseInt(row[1]), Integer.parseInt(row[2]), Integer.parseInt(row[3])));
            }
        }
        return Collections.unmodifiableSet(targets);
    }
}
