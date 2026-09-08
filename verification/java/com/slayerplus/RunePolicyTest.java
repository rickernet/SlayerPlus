package com.slayerplus;

import static org.junit.Assert.*;

import java.util.*;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class RunePolicyTest {
  @Test
  public void normalRuneWinsWhenCombinationSavesNoSlot() {
    assertEquals(
        Collections.singletonList(ItemID.COSMICRUNE),
        resolve(Map.of(ItemID.COSMICRUNE, 100, ItemID.AETHERRUNE, 10000), ItemID.COSMICRUNE));
    assertEquals(
        Collections.singletonList(ItemID.FIRERUNE),
        resolve(
            Map.of(ItemID.FIRERUNE, 100, ItemID.SMOKERUNE, 10000, ItemID.LAVARUNE, 5000),
            ItemID.FIRERUNE));
  }

  @Test
  public void combinationsStillWinWhenTheyReplaceTwoStacks() {
    assertEquals(
        Collections.singletonList(ItemID.AETHERRUNE),
        resolve(
            Map.of(ItemID.COSMICRUNE, 10000, ItemID.SOULRUNE, 10000, ItemID.AETHERRUNE, 100),
            ItemID.COSMICRUNE,
            ItemID.SOULRUNE));
    assertEquals(
        Collections.singletonList(ItemID.DUSTRUNE),
        resolve(
            Map.of(ItemID.AIRRUNE, 10000, ItemID.EARTHRUNE, 10000, ItemID.DUSTRUNE, 100),
            ItemID.AIRRUNE,
            ItemID.EARTHRUNE));
  }

  @Test
  public void alreadyCoveredRequirementDoesNotFavorAnotherCombination() {
    assertEquals(
        Arrays.asList(ItemID.MISTRUNE, ItemID.EARTHRUNE),
        resolve(
            Map.of(ItemID.MISTRUNE, 10000, ItemID.DUSTRUNE, 5000, ItemID.EARTHRUNE, 100),
            ItemID.WATERRUNE,
            ItemID.AIRRUNE,
            ItemID.EARTHRUNE));
  }

  @Test
  public void combinationIsStillUsableWhenNormalRuneIsUnavailable() {
    assertEquals(
        Collections.singletonList(ItemID.AETHERRUNE),
        resolve(Map.of(ItemID.COSMICRUNE, 0, ItemID.AETHERRUNE, 100), ItemID.COSMICRUNE));
  }

  private static List<Integer> resolve(Map<Integer, Integer> owned, int... required) {
    MethodRules.Builder rules = MethodRules.builder();
    for (int id : required) {
      rules.pouchRune(id, "Rune", 1);
    }
    RunePolicy.Resolution result = RunePolicy.resolve(rules.build().getPouchRunes(), owned);
    assertTrue(result.getUnownedRequirements().isEmpty());
    return result.getRunes().stream()
        .map(RunePolicy.ResolvedRune::getItemId)
        .collect(Collectors.toList());
  }
}
