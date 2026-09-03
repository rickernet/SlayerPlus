package com.slayerplus;

import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;
import static org.junit.Assert.*;

public class BankRoutesTest {
    private final WorldPoint crafting = new WorldPoint(2935, 3280, 0);

    @Test public void usedCraftingBankBeatsFaladorAndSurvivesReload() {
        BankRoutes routes = new BankRoutes();
        routes.useProfile("account-a", () -> null);
        assertTrue(routes.remember(crafting, false));
        assertEquals(crafting, routes.nearest(crafting));
        BankRoutes reloaded = new BankRoutes();
        reloaded.useProfile("account-a", routes::serialize);
        assertEquals(crafting, reloaded.nearest(crafting));
        assertFalse(reloaded.remember(crafting, false));
    }

    @Test public void adjacentBankVisitsDoNotSaveAgainButDifferentFloorsDo() {
        BankRoutes routes = new BankRoutes();
        routes.useProfile("account-a", () -> null);
        assertTrue(routes.remember(crafting, false));
        String original = routes.serialize();
        assertFalse(routes.remember(new WorldPoint(2936, 3281, 0), false));
        assertEquals(original, routes.serialize());
        assertTrue(routes.remember(new WorldPoint(2935, 3280, 1), false));
    }

    @Test public void profilesStaySeparateAndAreLoadedOnlyOnChange() {
        BankRoutes routes = new BankRoutes();
        AtomicInteger reads = new AtomicInteger();
        routes.useProfile("account-a", () -> { reads.incrementAndGet(); return "2935,3280,0"; });
        routes.useProfile("account-a", () -> { throw new AssertionError("Repeated config read"); });
        assertEquals(1, reads.get());
        routes.useProfile("account-b", () -> null);
        assertNotEquals(crafting, routes.nearest(crafting));
        assertEquals("", routes.serialize());
        routes.useProfile(null, () -> { throw new AssertionError("Anonymous config read"); });
        assertFalse(routes.remember(crafting, false));
        routes.useProfile("account-a", () -> "2935,3280,0");
        assertEquals(crafting, routes.nearest(crafting));
    }

    @Test public void rejectsInstancedInvalidAndCorruptLocations() {
        BankRoutes routes = new BankRoutes();
        routes.useProfile("account-a", () -> "bad;1,2;0,0,0;2935,3280,8;2935,3280,0;999999999999,1,0");
        assertEquals("2935,3280,0", routes.serialize());
        assertFalse(routes.remember(null, false));
        assertFalse(routes.remember(new WorldPoint(-1, 10, 0), false));
        assertFalse(routes.remember(new WorldPoint(4000, 5000, 0), true));
    }

    @Test public void unrestrictedBanksWorkWithoutHistory() {
        BankRoutes routes = new BankRoutes();
        for (WorldPoint bank : new WorldPoint[] {new WorldPoint(2443,3083,0),
            new WorldPoint(2536,3573,0), new WorldPoint(3427,2891,0), new WorldPoint(3308,3120,0)}) {
            assertEquals(bank, routes.nearest(bank));
        }
    }

    @Test public void savingDiscoveredBankDoesNotScheduleLoadoutWork() {
        SlayerPlusPlugin plugin = new SlayerPlusPlugin();
        ConfigChanged event = new ConfigChanged();
        event.setGroup(SlayerPlusConfig.GROUP);
        event.setKey(BankRoutes.CONFIG_KEY);
        // No injected client thread: reaching the scheduling branch would fail.
        plugin.onConfigChanged(event);
    }
}
