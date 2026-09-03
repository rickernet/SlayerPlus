package com.slayerplus;

import java.util.*;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.banktags.tabs.Layout;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerBraceletAlternativeTest {
    @Test public void oppositeBraceletSitsOneBankRowBelowGlovesInBothDirections() {
        int[] bracelets = {ItemID.EXPEDITIOUS_BRACELET, ItemID.BRACELET_OF_SLAUGHTER};
        for (int i = 0; i < bracelets.length; i++) {
            Layout layout = new Layout(BankTagLayout.TAG_NAME);
            Set<Integer> tags = new LinkedHashSet<>();
            layout.setItemAtPos(bracelets[i], 48);
            tags.add(bracelets[i]);
            assertTrue(BankTagLayout.placeBraceletAlternative(layout, tags, bracelets[i], Collections.emptySet()));
            assertEquals(bracelets[i], layout.getLayout()[48]);
            assertEquals(bracelets[1 - i], layout.getLayout()[56]);
            assertEquals(2, tags.size());
        }
    }

    @Test public void ordinaryGlovesAndMissingRecommendationsDoNotAddBracelets() {
        Layout layout = new Layout(BankTagLayout.TAG_NAME);
        Set<Integer> tags = new LinkedHashSet<>();
        int[] original = layout.getLayout().clone();
        assertFalse(BankTagLayout.placeBraceletAlternative(layout, tags, -1, Collections.emptySet()));
        assertFalse(BankTagLayout.placeBraceletAlternative(layout, tags, ItemID.SHARK, Collections.emptySet()));
        assertArrayEquals(original, layout.getLayout());
        assertTrue(tags.isEmpty());
    }

    @Test public void automaticOptionalBraceletDoesNotConsumeTheGearAlternative() {
        for (int recommended : new int[] {ItemID.EXPEDITIOUS_BRACELET, ItemID.BRACELET_OF_SLAUGHTER}) {
            int alternative = recommended == ItemID.EXPEDITIOUS_BRACELET ? ItemID.BRACELET_OF_SLAUGHTER : ItemID.EXPEDITIOUS_BRACELET;
            KitItem optional = new KitItem("Bracelet", alternative, 1, KitItem.Status.BANK);
            Layout layout = new Layout(BankTagLayout.TAG_NAME);
            Set<Integer> tags = new LinkedHashSet<>();
            assertFalse(BankTagLayout.showOptionalItem(optional, Collections.emptyList(), alternative, Collections.emptySet()));
            assertTrue(BankTagLayout.placeBraceletAlternative(layout, tags, recommended, Collections.emptySet()));
            assertEquals(alternative, layout.getLayout()[56]);
            assertTrue(BankTagLayout.showOptionalItem(optional, Collections.emptyList(), alternative, Collections.singleton(alternative)));
            assertTrue(BankTagLayout.showOptionalItem(optional, Collections.emptyList(), -1, Collections.emptySet()));
        }
    }

    @Test public void explicitPinRemainsInOptionalRowAndAlternativePosition() {
        Layout layout = new Layout(BankTagLayout.TAG_NAME);
        int alternative = ItemID.BRACELET_OF_SLAUGHTER;
        Set<Integer> tags = new LinkedHashSet<>(Collections.singleton(alternative));
        layout.setItemAtPos(alternative, 0);
        assertTrue(BankTagLayout.placeBraceletAlternative(layout, tags, ItemID.EXPEDITIOUS_BRACELET, tags));
        assertEquals(alternative, layout.getLayout()[0]);
        assertEquals(alternative, layout.getLayout()[56]);
    }

    @Test public void barrowsReusesCombatGlovesInsteadOfSlayerBracelets() {
        TaskStrategy strategy = TaskStrategy.builder(TaskStrategy.CombatStyle.MAGIC, "Air spells").boss(true).build();
        List<String> gloves = SlayerEquipmentAuditCatalog.priorities("Barrows Brothers", strategy, EquipmentInventorySlot.GLOVES, "Tumeken's shadow");
        assertEquals(Arrays.asList("confliction gauntlets", "tormented bracelet", "barrows gloves"), gloves.subList(0, 3));
        assertFalse(gloves.contains("expeditious bracelet"));
        assertFalse(gloves.contains("bracelet of slaughter"));
    }
}
