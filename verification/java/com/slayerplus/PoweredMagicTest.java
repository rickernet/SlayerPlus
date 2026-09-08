package com.slayerplus;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class PoweredMagicTest {
  @Test
  public void actualBarrowsCatalogFollowsRecommendedWeapon() {
    TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Barrows Brothers",
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Barrows",
            false);
    assertNotNull(strategy);
    assertFalse(
        PreparationCatalog.resolve(
                "Barrows Brothers",
                "Barrows",
                strategy,
                null,
                null,
                null,
                Collections.emptyMap(),
                ItemID.TUMEKENS_SHADOW)
            .isActive());
    assertTrue(
        PreparationCatalog.resolve(
                "Barrows Brothers",
                "Barrows",
                strategy,
                null,
                null,
                null,
                Collections.emptyMap(),
                ItemID.SLAYER_STAFF)
            .isActive());
  }

  @Test
  public void sharedRuleUsesSelectedWeaponForEveryTask() {
    TaskStrategy strategy = strategy();
    for (String task : Arrays.asList("Barrows Brothers", "Fire giants", "Blue dragons")) {
      for (int weapon :
          new int[] {
            ItemID.TUMEKENS_SHADOW,
            ItemID.TOTS_CHARGED,
            ItemID.SANGUINESTI_STAFF,
            ItemID.WARPED_SCEPTRE,
            ItemID.EYE_OF_AYAK,
            ItemID.WILD_CAVE_SCEPTRE_CHARGED,
            ItemID.WILD_CAVE_ACCURSED_CHARGED,
            ItemID.RAT_BONE_STAFF
          }) {
        MethodRules rules = SlayerMethodRuleCatalog.resolve(task, "", strategy, weapon);
        assertTrue(task, rules.getPouchRunes().isEmpty());
        assertFalse(task, rules.requiresRunePouch());
        assertFalse(
            task,
            PreparationCatalog.resolve(
                    task, "", strategy, null, null, null, Collections.emptyMap(), weapon)
                .isActive());
      }
      MethodRules normal = SlayerMethodRuleCatalog.resolve(task, "", strategy, ItemID.SLAYER_STAFF);
      assertEquals(task, MethodRules.Spellbook.STANDARD, normal.getSpellbook());
      assertFalse(task, normal.getPouchRunes().isEmpty());
    }
  }

  @Test
  public void explicitCombatPackagesAreRemovedButUtilityAndFreezesStay() {
    for (String spell : Arrays.asList("Fire Surge", "Water Wave / Fire Wave", "Magic Dart")) {
      MethodRules rules =
          spell(MethodRules.Spellbook.STANDARD, spell).withoutStandardCombatSpell().build();
      assertEquals(MethodRules.Spellbook.NONE, rules.getSpellbook());
      assertTrue(rules.getPouchRunes().isEmpty());
      assertFalse(rules.requiresRunePouch());
    }
    for (String utility : Arrays.asList("Crumble Undead", "High Level Alchemy")) {
      MethodRules rules =
          spell(MethodRules.Spellbook.STANDARD, utility).withoutStandardCombatSpell().build();
      assertEquals(MethodRules.Spellbook.STANDARD, rules.getSpellbook());
      assertFalse(rules.getPouchRunes().isEmpty());
    }
    for (MethodRules.Spellbook book :
        Arrays.asList(MethodRules.Spellbook.ANCIENT, MethodRules.Spellbook.ARCEUUS)) {
      MethodRules rules = spell(book, "Encounter spell").withoutStandardCombatSpell().build();
      assertEquals(book, rules.getSpellbook());
      assertTrue(rules.requiresRunePouch());
    }
    MethodRules thralls =
        SlayerMethodRuleCatalog.resolve("Zulrah", "", strategy(), ItemID.TUMEKENS_SHADOW);
    assertEquals(MethodRules.Spellbook.ARCEUUS, thralls.getSpellbook());
    assertTrue(thralls.usesThralls());
    MethodRules alchemy =
        SlayerMethodRuleCatalog.resolve("Bloodveld", "", strategy(), ItemID.TUMEKENS_SHADOW);
    assertEquals(MethodRules.Spellbook.STANDARD, alchemy.getSpellbook());
    assertTrue(
        alchemy.getPouchRunes().stream().anyMatch(rune -> rune.getItemId() == ItemID.NATURERUNE));
    assertFalse(
        alchemy.getPouchRunes().stream().anyMatch(rune -> rune.getItemId() == ItemID.WRATHRUNE));
  }

  @Test
  public void inventoryAndReminderAgreeAndWeaponSlotIsNotAmmoSlot() {
    SlayerLoadoutAnalyzer analyzer = new SlayerLoadoutAnalyzer(null);
    List<KitItem> equipment =
        Arrays.asList(
            null,
            null,
            null,
            new KitItem("Shadow in wrong slot", ItemID.TUMEKENS_SHADOW, 1, KitItem.Status.BANK),
            new KitItem("Slayer staff", ItemID.SLAYER_STAFF, 1, KitItem.Status.BANK));
    assertEquals(ItemID.SLAYER_STAFF, PoweredMagic.weaponId(equipment));
    assertEquals(-1, PoweredMagic.weaponId(Collections.emptyList()));
    assertTrue(hasPouch(inventory(analyzer, equipment)));
    equipment.set(4, new KitItem("Shadow", ItemID.TUMEKENS_SHADOW, 1, KitItem.Status.BANK));
    assertFalse(hasPouch(inventory(analyzer, equipment)));
  }

  private static TaskStrategy strategy() {
    return TaskStrategy.builder(TaskStrategy.CombatStyle.MAGIC, "Use Air spells")
        .runePouch(true)
        .reviewed("2026-09-02")
        .build();
  }

  private static MethodRules.Builder spell(MethodRules.Spellbook book, String name) {
    return MethodRules.builder()
        .spell(book, name)
        .runePouch(true)
        .pouchRune(ItemID.AIRRUNE, "Air", 100);
  }

  private static List<KitItem> inventory(SlayerLoadoutAnalyzer analyzer, List<KitItem> equipment) {
    return analyzer.buildInventoryLayout(
        "Barrows Brothers",
        strategy(),
        "",
        "",
        SlayerLoadoutAnalyzer.CombatStyle.MAGIC,
        Collections.emptyList(),
        false,
        8,
        Collections.emptyList(),
        true,
        equipment);
  }

  private static boolean hasPouch(List<KitItem> items) {
    return items.stream().anyMatch(item -> item.getDisplayName().equals("Rune pouch"));
  }
}
