package com.slayerplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SlayerBraceletChargeTrackerTest {
  @Test
  public void parsesSlaughterCheckMessage() {
    final SlayerBraceletChargeTracker.Update update =
        SlayerBraceletChargeTracker.parse("Your bracelet of slaughter has 19 charges left.");

    assertEquals(ItemID.BRACELET_OF_SLAUGHTER, update.getItemId());
    assertEquals(SlayerBraceletChargeTracker.SLAUGHTER_KEY, update.getConfigKey());
    assertEquals(19, update.getCharges());
  }

  @Test
  public void parsesExpeditiousActivationMessage() {
    final SlayerBraceletChargeTracker.Update update =
        SlayerBraceletChargeTracker.parse(
            "Your expeditious bracelet helps you progress your slayer task faster. "
                + "It has 11 charges left.");

    assertEquals(ItemID.EXPEDITIOUS_BRACELET, update.getItemId());
    assertEquals(SlayerBraceletChargeTracker.EXPEDITIOUS_KEY, update.getConfigKey());
    assertEquals(11, update.getCharges());
  }

  @Test
  public void reportsZeroAfterCrumbleAndFullAfterRegeneration() {
    assertEquals(
        0,
        SlayerBraceletChargeTracker.parse(
                "Your bracelet of slaughter prevents your slayer count from decreasing. "
                    + "It then crumbles to dust.")
            .getCharges());
    assertEquals(
        SlayerBraceletChargeTracker.MAX_CHARGES,
        SlayerBraceletChargeTracker.parse(
                "Your expeditious bracelet helps you progress your slayer faster. "
                    + "It then regenerates itself to full charge!")
            .getCharges());
  }

  @Test
  public void ignoresUnrelatedMessagesAndItems() {
    assertNull(SlayerBraceletChargeTracker.parse("Your Slayer task is complete."));
    assertEquals("", SlayerBraceletChargeTracker.configKey(-1));
  }

  @Test
  public void depletedIndicatorSlowlyPulsesBetweenGreyAndRed() {
    assertNotEquals(
        SlayerBraceletChargeInfoBox.depletedTextColorForTest(600L),
        SlayerBraceletChargeInfoBox.depletedTextColorForTest(1800L));
  }
}
