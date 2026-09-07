package com.slayerplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SlayerRegressionTest {

  @Test
  public void exactAssignmentChatRestoresTaskBeforeVarpsSettle() {
    final SlayerTaskChatUpdate assignment =
        SlayerTaskChatUpdate.parse("You're assigned to kill ankou; only 57 more to go.");
    assertNotNull(assignment);
    assertEquals("Ankou", assignment.getTaskName());
    assertEquals(57, assignment.getRemaining());
    assertEquals(57, SlayerPlusPlugin.firstPositive(0, -1, 57));
  }

  @Test
  public void exactStatusChatRestoresStreakAndPoints() {
    final SlayerTaskChatUpdate status =
        SlayerTaskChatUpdate.parse(
            "You\u2019ve completed 766 tasks in a row and currently have a total of 289 points.");
    assertNotNull(status);
    assertEquals(766, status.getStreak());
    assertEquals(289, status.getPoints());
    assertEquals(766, SlayerPlusPlugin.preferLiveOrProfileValue(0, 766, -1));
    assertEquals(289, SlayerPlusPlugin.preferLiveOrProfileValue(0, 0, 289));
  }

  @Test
  public void versionedSidebarIconIsPackagedWithThePlugin() {
    assertNotNull(SlayerPlusPlugin.class.getResource("slayerplus_sidebar_v2.png"));
  }

  @Test
  public void turaelPointBoostingUsesNineEasyTasksThenTheSelectedBonusMaster() {
    assertPointBoostDecision(0, 1, 1, false);
    assertPointBoostDecision(8, 9, 1, false);
    assertPointBoostDecision(9, 10, 8, true);
    assertPointBoostDecision(49, 50, 8, true);
    assertPointBoostDecision(99, 100, 8, true);
    assertPointBoostDecision(249, 250, 8, true);
    assertPointBoostDecision(999, 1000, 8, true);

    final BoostCoordinator.Decision duradel =
        BoostCoordinator.nextAssignment(759, Preference.BonusMaster.DURADEL);
    assertTrue(duradel.isBonusTask());
    assertEquals(760, duradel.getCompletionNumber());
    assertEquals(5, duradel.getMasterId());
    assertEquals("Bonus task 760", duradel.getProgressText());

    final BoostCoordinator.Decision afterBonus =
        BoostCoordinator.nextAssignment(760, Preference.BonusMaster.DURADEL);
    assertFalse(afterBonus.isBonusTask());
    assertEquals(1, afterBonus.getMasterId());
    assertEquals("Turael/Aya task 1 of 9", afterBonus.getProgressText());
  }

  @Test
  public void turaelWorkflowRecommendationCarriesItsCompactTripMarker() {
    final SlayerPlusConfig boostConfig =
        new SlayerPlusConfig() {
          @Override
          public Preference.Workflow slayerWorkflow() {
            return Preference.Workflow.TURAEL_POINT_BOOST;
          }
        };
    final SlayerRecommendationEngine engine = new SlayerRecommendationEngine(null, boostConfig);
    final Recommendation recommendation =
        engine.recommend("Bats", BoostCoordinator.TURAEL_AYA_MASTER_ID, "Not restricted");

    assertEquals("Silvarea limestone mine", recommendation.getLocation());
    assertTrue(recommendation.getStrategy().hasTag(TaskStrategy.MethodTag.TURAEL_POINT_BOOST));
    assertEquals(
        2,
        SlayerMethodRuleCatalog.resolve(
                "Bats", recommendation.getLocation(), recommendation.getStrategy())
            .resolveFoodSlots(
                recommendation.getStrategy(), recommendation.getStrategy().getFood()));
  }

  @Test
  public void turaelTravelKitKeepsSeveralTeleportsWithoutDuplicatingWornGear() {
    KitPlan plan =
        new KitPlan(
            "Ready",
            "Ready",
            "Ready",
            "Turael boost",
            Collections.singletonList(new KitItem("Max cape", 100, 1, KitItem.Status.EQUIPPED)),
            Collections.singletonList(
                new KitItem("Emergency food", 200, 1, KitItem.Status.BANK)
                    .withInventoryGroup(MethodRules.InventoryGroup.FOOD)),
            Collections.emptyList());

    /* Worn travel gear is not copied into the pack. */
    assertTrue(plan == SlayerPlusPlugin.appendMasterReturnTeleportForTest(plan, 100, "Max cape"));

    for (int id = 301; id <= 304; id++) {
      plan = SlayerPlusPlugin.appendMasterReturnTeleportForTest(plan, id, "Travel item " + id);
    }
    assertEquals(5, plan.getInventoryItems().size());
    assertEquals(
        4,
        plan.getInventoryItems().stream()
            .filter(item -> item.getDisplayName().startsWith("Travel item"))
            .count());
    assertEquals(
        1,
        plan.getInventoryItems().stream()
            .filter(item -> item.getInventoryGroup() == MethodRules.InventoryGroup.FOOD)
            .count());
  }

  @Test
  public void turaelHazardTasksKeepTheirNonNegotiableSupplies() {
    assertTuraelRequiredItem("Cave bugs", "Safe light source");
    assertTuraelRequiredItem("Cave slimes", "Safe light source");
    assertTuraelRequiredItem("Cave slimes", "Poison protection");
    assertTuraelRequiredItem("Cave crawlers", "Poison protection");
    assertTuraelRequiredItem("Lizards", "Ice coolers");
    assertTuraelRequiredItem("Lizards", "Desert heat protection");
    assertTuraelRequiredItem("Skeletons", "Rope");
  }

  @Test
  public void turaelNoCannonLocationsCannotInheritABroadCannonStrategy() {
    for (final String task :
        Arrays.asList(
            "Banshees",
            "Cave bugs",
            "Cave crawlers",
            "Cave slimes",
            "Crawling hands",
            "Dwarves",
            "Ghosts")) {
      final TuraelBoost.Entry entry = TuraelBoost.find(task);
      assertNotNull(task, entry);
      assertFalse(task, entry.supportsCannon());
      final TaskStrategy cannonStrategy =
          TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "Cannon regression")
              .tags(TaskStrategy.MethodTag.CANNON, TaskStrategy.MethodTag.TURAEL_POINT_BOOST)
              .reviewed("2026-08-18")
              .build();
      final MethodRules rules =
          SlayerMethodRuleCatalog.resolve(task, entry.getLocation(), cannonStrategy);
      assertEquals(task, 0, rules.getCannonballQuantity());
    }
  }

  private static void assertTuraelRequiredItem(final String task, final String displayName) {
    final TuraelBoost.Entry entry = TuraelBoost.find(task);
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
                task,
                Preference.Playstyle.FAST_XP,
                Preference.Cannon.ALLOW,
                Preference.Burst.NEVER,
                Preference.CombatStyle.AUTOMATIC,
                entry.getLocation(),
                false)
            .withAdditionalTags(TaskStrategy.MethodTag.TURAEL_POINT_BOOST);
    final MethodRules rules = SlayerMethodRuleCatalog.resolve(task, entry.getLocation(), strategy);
    assertTrue(
        task + " -> " + displayName,
        rules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals(displayName)));
  }

  private static void assertPointBoostDecision(
      final int completedStreak,
      final int completionNumber,
      final int masterId,
      final boolean bonusTask) {
    final BoostCoordinator.Decision decision =
        BoostCoordinator.nextAssignment(completedStreak, Preference.BonusMaster.KONAR);
    assertEquals(completionNumber, decision.getCompletionNumber());
    assertEquals(masterId, decision.getMasterId());
    assertEquals(bonusTask, decision.isBonusTask());
  }

  @Test
  public void skeletalWyvernsUseProtectedProgressiveGearAndReviewedSupplies() {
    final TaskStrategy melee =
        SlayerTaskStrategyCatalog.resolve(
            "Skeletal wyverns",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Asgarnian Ice Dungeon",
            false);
    assertEquals(TaskStrategy.CombatStyle.MELEE, melee.getStyle());
    assertEquals("dragon hunter lance", melee.getWeaponPriorities().get(0));
    assertTrue(melee.getWeaponPriorities().contains("abyssal whip"));
    assertFalse(melee.getWeaponPriorities().contains("osmumten s fang"));

    final List<String> meleeShields =
        SlayerEquipmentAuditCatalog.priorities(
            "Skeletal wyverns", melee, EquipmentInventorySlot.SHIELD, "Dragon hunter lance");
    assertEquals("ancient wyvern shield", meleeShields.get(0));
    assertTrue(meleeShields.contains("elemental shield"));
    assertFalse(meleeShields.contains("anti dragon shield"));

    final MethodRules meleeRules =
        SlayerMethodRuleCatalog.resolve("Skeletal wyverns", "Asgarnian Ice Dungeon", melee);
    assertEquals(4, meleeRules.resolveRestoreSlots(melee));
    assertEquals(8, meleeRules.resolveFoodSlots(melee, melee.getFood()));
    assertTrue(meleeRules.includesStyleBoost());
    assertTrue(
        meleeRules.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Special attack weapon")
                        && item.getSlotCount() == 1));
    assertEquals(MethodRules.Spellbook.STANDARD, meleeRules.getSpellbook());
    assertEquals("High Level Alchemy", meleeRules.getPrimarySpell());
    assertTrue(meleeRules.requiresRunePouch());
    assertFalse(
        meleeRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().toLowerCase().contains("antifire")));

    final TaskStrategy ranged =
        SlayerTaskStrategyCatalog.resolve(
            "Skeletal wyverns",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_RANGED,
            "Asgarnian Ice Dungeon",
            false);
    assertEquals(TaskStrategy.CombatStyle.RANGED, ranged.getStyle());
    assertTrue(ranged.getMethod().contains("dragonstone bolts"));
    assertEquals(
        "dragonfire ward",
        SlayerEquipmentAuditCatalog.priorities(
                "Skeletal wyverns", ranged, EquipmentInventorySlot.SHIELD, "Dragon hunter crossbow")
            .get(0));
    final MethodRules rangedRules =
        SlayerMethodRuleCatalog.resolve("Skeletal wyverns", "Asgarnian Ice Dungeon", ranged);
    assertEquals(2, rangedRules.resolveRestoreSlots(ranged));
    assertEquals(6, rangedRules.resolveFoodSlots(ranged, ranged.getFood()));
    assertTrue(rangedRules.includesStyleBoost());
    assertFalse(
        rangedRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Special attack weapon")));

    final TaskStrategy magic =
        SlayerTaskStrategyCatalog.resolve(
            "Skeletal wyverns",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_MAGIC,
            "Asgarnian Ice Dungeon",
            false);
    assertEquals(TaskStrategy.CombatStyle.MAGIC, magic.getStyle());
    final MethodRules magicRules =
        SlayerMethodRuleCatalog.resolve("Skeletal wyverns", "Asgarnian Ice Dungeon", magic);
    assertEquals("Fire Surge or Fire Wave", magicRules.getPrimarySpell());
    assertEquals(4, magicRules.getPouchRunes().size());
    assertEquals(
        "ancient wyvern shield",
        SlayerEquipmentAuditCatalog.priorities(
                "Skeletal wyverns", magic, EquipmentInventorySlot.SHIELD, "Dragon hunter wand")
            .get(0));
    assertEquals(2, magicRules.resolveRestoreSlots(magic));
    assertEquals(6, magicRules.resolveFoodSlots(magic, magic.getFood()));
    assertTrue(magicRules.includesStyleBoost());
  }

  @Test
  public void portraitPollingSkipsIdleTicksAndThrottlesFailedRetries() {
    assertFalse(SlayerPlusPlugin.shouldCheckPortraitForTest(4, false, 0));
    assertTrue(SlayerPlusPlugin.shouldCheckPortraitForTest(5, false, 0));
    assertFalse(SlayerPlusPlugin.shouldCheckPortraitForTest(8, true, 10));
    assertTrue(SlayerPlusPlugin.shouldCheckPortraitForTest(10, true, 10));
  }

  @Test
  public void profitArrowPolicyDiffersFromFastXpAndHonorsCompatibility() {
    final List<String> regularProfit =
        SlayerLoadoutAnalyzer.standardArrowPriorityForTest(
            TaskStrategy.CostPolicy.EFFICIENT, false, true);
    assertEquals("rune arrow", regularProfit.get(0));
    assertTrue(regularProfit.indexOf("amethyst arrow") < regularProfit.indexOf("dragon arrow"));

    final List<String> bossProfit =
        SlayerLoadoutAnalyzer.standardArrowPriorityForTest(
            TaskStrategy.CostPolicy.EFFICIENT, true, true);
    assertEquals("amethyst arrow", bossProfit.get(0));

    final List<String> fastXp =
        SlayerLoadoutAnalyzer.standardArrowPriorityForTest(
            TaskStrategy.CostPolicy.MAX_DPS, false, true);
    assertEquals("seeking dragon arrow", fastXp.get(0));

    final List<String> incompatibleBow =
        SlayerLoadoutAnalyzer.standardArrowPriorityForTest(
            TaskStrategy.CostPolicy.MAX_DPS, false, false);
    assertFalse(incompatibleBow.stream().anyMatch(arrow -> arrow.contains("dragon arrow")));
  }

  @Test
  public void combatAchievementHelmetOptionReplacesLegacyAutomaticChoice() {
    assertEquals(
        HelmetPreference.COMBAT_ACHIEVEMENT, HelmetPreference.normalize("Automatic (best)"));
    assertEquals(
        3, SlayerLoadoutAnalyzer.combatAchievementHelmetTierForTest("TzKal slayer helmet (i)"));
    assertEquals(
        2, SlayerLoadoutAnalyzer.combatAchievementHelmetTierForTest("Vampyric slayer helmet (i)"));
    assertEquals(
        1, SlayerLoadoutAnalyzer.combatAchievementHelmetTierForTest("TzTok slayer helmet (i)"));
    assertEquals(
        0, SlayerLoadoutAnalyzer.combatAchievementHelmetTierForTest("Purple slayer helmet (i)"));
  }

  @Test
  public void ownedSlayerHelmetSnapshotRoundTripsAndRejectsInvalidIds() {
    final List<Integer> itemIds = Arrays.asList(11864, 25177, 11864, -1);
    assertEquals("11864,25177,11864", SlayerPlusPlugin.serializeItemIds(itemIds));
    assertEquals(
        new java.util.LinkedHashSet<>(Arrays.asList(11864, 25177)),
        SlayerPlusPlugin.parseItemIds("11864,broken,25177,11864,-4"));
  }

  @Test
  public void shardPreferenceOverridesEfficiencyOnlyAtValidShardLocations() {
    assertEquals(
        0,
        SlayerRecommendationEngine.shardPreferenceBonusForTest(
            Preference.Shard.NO_PREFERENCE, "Bloodvelds", "Catacombs of Kourend"));
    assertEquals(
        1000,
        SlayerRecommendationEngine.shardPreferenceBonusForTest(
            Preference.Shard.ANCIENT_SHARD, "Bloodvelds", "Catacombs of Kourend"));
    assertEquals(
        1000,
        SlayerRecommendationEngine.shardPreferenceBonusForTest(
            Preference.Shard.CRYSTAL_SHARD, "Bloodvelds", "Iorwerth Dungeon"));
    assertEquals(
        1000,
        SlayerRecommendationEngine.shardPreferenceBonusForTest(
            Preference.Shard.ANCIENT_AND_CRYSTAL_SHARD, "Nechryaels", "Catacombs of Kourend"));
    assertEquals(
        1000,
        SlayerRecommendationEngine.shardPreferenceBonusForTest(
            Preference.Shard.ANCIENT_AND_CRYSTAL_SHARD, "Nechryaels", "Iorwerth Dungeon"));
    assertEquals(
        0,
        SlayerRecommendationEngine.shardPreferenceBonusForTest(
            Preference.Shard.ANCIENT_SHARD, "Ghosts", "Catacombs of Kourend"));
    assertEquals(
        0,
        SlayerRecommendationEngine.shardPreferenceBonusForTest(
            Preference.Shard.CRYSTAL_SHARD, "Bloodvelds", "Stronghold Slayer Cave"));
  }

  @Test
  public void iorwerthNechryaelsUseReviewedMeleeInsteadOfCatacombsBarrage() {
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Nechryaels",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.ALLOW,
            Preference.CombatStyle.AUTOMATIC,
            "Iorwerth Dungeon",
            false);
    assertEquals(TaskStrategy.CombatStyle.MELEE, strategy.getStyle());
    assertTrue(strategy.getMethod().contains("crystal shard"));
    assertFalse(strategy.hasTag(TaskStrategy.MethodTag.BARRAGE));
  }

  @Test
  public void runePouchBossUtilitiesSelectTheirActualSpellbooks() {
    assertEquals(MethodRules.Spellbook.ANCIENT, bossInventoryRules("Scorpia").getSpellbook());
    assertEquals(MethodRules.Spellbook.ANCIENT, bossInventoryRules("Leviathan").getSpellbook());
    assertEquals(MethodRules.Spellbook.STANDARD, bossInventoryRules("Royal Titans").getSpellbook());
    assertEquals(
        MethodRules.Spellbook.STANDARD, bossInventoryRules("Demonic Gorillas").getSpellbook());
  }

  @Test
  public void prayerCostSavingsPreserveOffenceOutsideAllowedEconomySlots() {
    for (final TaskStrategy.CombatStyle style :
        Arrays.asList(
            TaskStrategy.CombatStyle.MELEE,
            TaskStrategy.CombatStyle.RANGED,
            TaskStrategy.CombatStyle.MAGIC)) {
      final TaskStrategy strategy =
          TaskStrategy.builder(style, "Regression prayer-cost profile")
              .armourFocus(TaskStrategy.ArmourFocus.PRAYER)
              .build();
      final List<String> cape =
          SlayerEquipmentAuditCatalog.priorities(
              "Bloodveld", strategy, EquipmentInventorySlot.CAPE, "");
      final List<String> body =
          SlayerEquipmentAuditCatalog.priorities(
              "Bloodveld", strategy, EquipmentInventorySlot.BODY, "");
      final List<String> legs =
          SlayerEquipmentAuditCatalog.priorities(
              "Bloodveld", strategy, EquipmentInventorySlot.LEGS, "");
      final List<String> boots =
          SlayerEquipmentAuditCatalog.priorities(
              "Bloodveld", strategy, EquipmentInventorySlot.BOOTS, "");
      final List<String> ring =
          SlayerEquipmentAuditCatalog.priorities(
              "Bloodveld", strategy, EquipmentInventorySlot.RING, "");

      assertFalse(cape.isEmpty());
      assertFalse(body.isEmpty());
      assertFalse(legs.isEmpty());
      assertFalse(boots.isEmpty());
      assertFalse(ring.isEmpty());
      assertTrue(
          body.get(0).contains("body")
              || body.get(0).contains("hauberk")
              || body.get(0).contains("cuirass"));
      assertTrue(
          legs.get(0).contains("chaps")
              || legs.get(0).contains("cuisse")
              || legs.get(0).contains("chausses"));
    }

    final TaskStrategy melee =
        TaskStrategy.builder(TaskStrategy.CombatStyle.MELEE, "Regression melee profile")
            .armourFocus(TaskStrategy.ArmourFocus.PRAYER)
            .build();
    assertEquals(
        "infernal cape",
        SlayerEquipmentAuditCatalog.priorities("Bloodveld", melee, EquipmentInventorySlot.CAPE, "")
            .get(0));
    assertEquals(
        "avernic treads max",
        SlayerEquipmentAuditCatalog.priorities("Bloodveld", melee, EquipmentInventorySlot.BOOTS, "")
            .get(0));
    assertEquals(
        "ring of the gods i",
        SlayerEquipmentAuditCatalog.priorities("Bloodveld", melee, EquipmentInventorySlot.RING, "")
            .get(0));
  }

  @Test
  public void taskBraceletsRemainInTheGloveSlotGlobally() {
    final KitItem slaughter = new KitItem("Bracelet of slaughter", 21183, 1, KitItem.Status.BANK);
    final KitItem expeditious = new KitItem("Expeditious bracelet", 21177, 1, KitItem.Status.BANK);

    assertTrue(SlayerLoadoutAnalyzer.gloveFallbackMatchesSlotForTest("Bracelet of slaughter"));
    assertTrue(SlayerLoadoutAnalyzer.gloveFallbackMatchesSlotForTest("Expeditious bracelet"));
    assertTrue(
        BankTagLayout.optionalItemDuplicatesEquipmentForTest(
            slaughter, Collections.singletonList(slaughter)));
    assertFalse(
        BankTagLayout.optionalItemDuplicatesEquipmentForTest(
            expeditious, Collections.singletonList(slaughter)));
  }

  @Test
  public void settingsChangesRefreshOnlyAnExistingBankTag() {
    assertFalse(SlayerPlusPlugin.shouldRefreshExistingBankTag(false, false));
    assertTrue(SlayerPlusPlugin.shouldRefreshExistingBankTag(true, false));
    assertTrue(SlayerPlusPlugin.shouldRefreshExistingBankTag(false, true));
  }

  @Test
  public void potionPolicyPrefersUsefulExtendedUptimeAndPreservesPlusTiers() {
    assertEquals("extended stamina potion", PotionPolicy.staminaAlternatives()[0]);
    assertEquals("goading potion", PotionPolicy.goadingAlternatives()[0]);
    assertEquals(6, PotionPolicy.effectiveDoseUnits("Extended stamina potion", 3));
    assertEquals(4, PotionPolicy.effectiveDoseUnits("Stamina potion", 4));
    assertEquals(8, PotionPolicy.effectiveDoseUnits("Extended anti-venom+", 4));
    assertEquals(
        "divine bastion potion",
        PotionPolicy.rangedBoostAlternatives(TaskStrategy.CostPolicy.MAX_DPS)[0]);
    assertEquals(
        "ranging potion",
        PotionPolicy.rangedBoostAlternatives(TaskStrategy.CostPolicy.EFFICIENT)[0]);
    assertEquals("saturated heart", PotionPolicy.magicBoostAlternatives()[0]);
    assertEquals(
        "Extended stamina potion(3)",
        SlayerLoadoutAnalyzer.preferredInventoryItemForTest(
            Arrays.asList("Stamina potion(4)", "Extended stamina potion(3)"),
            PotionPolicy.staminaAlternatives()));
    assertEquals(
        "Saturated heart",
        SlayerLoadoutAnalyzer.preferredInventoryItemForTest(
            Arrays.asList("Magic potion(4)", "Saturated heart"),
            PotionPolicy.magicBoostAlternatives()));
    assertEquals(
        "anti venom plus 4",
        SlayerLoadoutAnalyzer.normalizePotionDisplayNameForTest("Anti-venom+(4)"));
    assertEquals(
        "antidote plus plus 4",
        SlayerLoadoutAnalyzer.normalizePotionDisplayNameForTest("Antidote++(4)"));
    assertEquals("extended super antifire", PotionPolicy.potionOnlyAntifireAlternatives()[0]);
    assertTrue(
        Arrays.stream(PotionPolicy.potionOnlyAntifireAlternatives())
            .noneMatch(name -> name.equals("extended antifire")));
  }

  @Test
  public void researchedSafespotBaselineReplacesUnsafeGenericMeleeTasks() {
    for (final String task :
        Arrays.asList(
            "Catablepon",
            "Cave bugs",
            "Crocodiles",
            "Fleshcrawlers",
            "Ghouls",
            "Minotaurs",
            "Shadow warriors",
            "Terror dogs")) {
      final TaskStrategy strategy =
          SlayerTaskStrategyCatalog.resolve(
              task,
              Preference.Playstyle.FAST_XP,
              Preference.Cannon.NEVER,
              Preference.Burst.NEVER,
              Preference.CombatStyle.AUTOMATIC,
              "Not restricted",
              false);
      assertEquals(task, TaskStrategy.CombatStyle.RANGED, strategy.getStyle());
      assertTrue(task, strategy.getMethod().toLowerCase().contains("safespot"));
    }

    final TaskStrategy otherworldly =
        SlayerTaskStrategyCatalog.resolve(
            "Otherworldly beings",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Not restricted",
            false);
    assertEquals(TaskStrategy.CombatStyle.MAGIC, otherworldly.getStyle());
    assertTrue(otherworldly.getMethod().contains("35% Air weakness"));
  }

  @Test
  public void everySlayerMasterHasAReviewedReturnTeleportProgression() {
    for (int masterId = 1; masterId <= 10; masterId++) {
      final MasterRoutes.MasterRoute route = MasterRoutes.find(masterId);
      assertNotNull("Missing Slayer master " + masterId, route);
      assertFalse(
          "Missing return teleport for " + route.getName(),
          route.getReturnItemFamilies().isEmpty());
    }
    assertEquals("karamja gloves 4", MasterRoutes.find(5).getReturnItemFamilies().get(0));
    assertEquals("Slayer Master", MasterRoutes.find(5).getReturnDestination("karamja gloves 4"));
    assertEquals("Gem Mine", MasterRoutes.find(5).getReturnDestination("karamja gloves 3"));
    assertEquals("rada s blessing 4", MasterRoutes.find(8).getReturnItemFamilies().get(0));
    assertEquals("Mount Karuulm", MasterRoutes.find(8).getReturnDestination("rada s blessing 4"));
    assertEquals("Mortimer", MasterRoutes.getName(10));
    assertEquals(new WorldPoint(2589, 8614, 0), MasterRoutes.find(10).getDestination());
    assertEquals(
        "Wyrmscraig Cavern", MasterRoutes.find(10).getReturnDestination("eternal slayer ring"));
  }

  @Test
  public void travelDestinationAliasesMatchSlayerRingMenuNames() {
    assertTrue(
        SlayerPlusPlugin.travelDestinationNamesMatch(
            "Fremennik Dungeon", "Fremennik Slayer Dungeon"));
    assertFalse(
        SlayerPlusPlugin.travelDestinationNamesMatch("Stronghold", "Fremennik Slayer Dungeon"));
  }

  @Test
  public void caveCrawlerRouteEndsInsideTheFremennikDungeon() {
    assertEquals(
        new WorldPoint(2790, 9996, 0),
        SlayerTaskWaypoints.findSpecific("Cave crawlers", "Fremennik Slayer Dungeon"));
  }

  @Test
  public void selectedSlayerRingRoutesToItsUndergroundArrivalFirst() {
    List<WorldPoint> path =
        SlayerPlusPlugin.prioritizeTravelArrival(
            Arrays.asList(new WorldPoint(2797, 3616, 0), new WorldPoint(2790, 9996, 0)),
            "Fremennik Slayer Dungeon",
            "Slayer ring (eternal)");
    assertEquals(new WorldPoint(2802, 9999, 0), path.get(0));
    assertEquals(new WorldPoint(2790, 9996, 0), path.get(1));
    assertEquals(2, path.size());
  }

  @Test
  public void wildcardInventoryRequirementsApplyToEveryCombatStyle() {
    assertTrue(SlayerLoadoutAnalyzer.requirementStyleMatches("*", "RANGED"));
    assertTrue(SlayerLoadoutAnalyzer.requirementStyleMatches("magic", "MAGIC"));
    assertFalse(SlayerLoadoutAnalyzer.requirementStyleMatches("magic", "RANGED"));
  }

  @Test
  public void routingUsesResolvedTaskSnapshotAcrossLoginAndCompletionBoundaries() {
    assertEquals(57, SlayerPlusPlugin.effectiveTaskRemainingForTest(57, 0));
    assertEquals(0, SlayerPlusPlugin.effectiveTaskRemainingForTest(0, 57));
    assertEquals(57, SlayerPlusPlugin.effectiveTaskRemainingForTest(-1, 57));
    assertTrue(SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(GameState.HOPPING));
    assertTrue(
        SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(GameState.CONNECTION_LOST));
    assertTrue(SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(GameState.LOGGING_IN));
    assertFalse(SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(GameState.LOADING));
    assertFalse(SlayerPlusPlugin.shouldInvalidateTaskObservationForGameState(GameState.LOGGED_IN));
  }

  @Test
  public void liveZeroWinsTheTaskCompletionRaceAgainstStaleCaches() {
    assertEquals(0, SlayerPlusPlugin.resolveTaskRemainingForTest(57, 57, 0, 57, -1));
    assertEquals(57, SlayerPlusPlugin.resolveTaskRemainingForTest(-1, 57, 0, 57, -1));
    assertEquals(42, SlayerPlusPlugin.resolveTaskRemainingForTest(57, 42, 42, 57, -1));
  }

  @Test
  public void liveVarpWinsOverAStaleThirdPartySlayerServiceCount() {
    // RuneLite's built-in Slayer plugin can lag one kill behind the
    // server-driven varp after a fast kill. The raw varp is the more
    // authoritative source, so it must win when the two disagree.
    assertEquals(63, SlayerPlusPlugin.resolveTaskRemainingForTest(64, 64, 63, 64, -1));
  }

  @Test
  public void masterReturnTeleportUsesBottomUtilityCellWithoutMovingRunePouch() {
    final List<KitItem> inventory = new ArrayList<>();
    inventory.add(
        new KitItem("Task teleport", 900, 1, KitItem.Status.BANK)
            .withInventoryGroup(MethodRules.InventoryGroup.TRAVEL));
    for (int index = 1; index < 27; index++) {
      inventory.add(
          new KitItem("Food " + index, 1000 + index, 1, KitItem.Status.BANK)
              .withInventoryGroup(MethodRules.InventoryGroup.FOOD));
    }
    inventory.add(
        new KitItem("Divine rune pouch", 2000, 1, KitItem.Status.BANK)
            .withInventoryGroup(MethodRules.InventoryGroup.UTILITY));

    final KitPlan enriched =
        SlayerPlusPlugin.appendMasterReturnTeleportForTest(
            new KitPlan(
                "Gear",
                "Inventory",
                "Owned",
                "Test",
                Collections.emptyList(),
                inventory,
                Collections.emptyList()),
            3000,
            "Karamja gloves 4");
    assertEquals(28, enriched.getInventoryItems().size());
    assertTrue(enriched.getInventoryItems().stream().anyMatch(item -> item.getItemId() == 3000));

    final List<BankTagLayout.InventoryPlacement> placements =
        BankTagLayout.planInventoryForBankTag(enriched.getInventoryItems(), -1, true);
    assertEquals(25, slotFor(placements, "Karamja gloves 4"));
    assertEquals(26, slotFor(placements, "Task teleport"));
    assertEquals(27, slotFor(placements, "Divine rune pouch"));
  }

  @Test
  public void arceuusThrallPreparationPublishesEveryRequiredRune() {
    final TaskStrategy whisperer =
        SlayerTaskStrategyCatalog.resolve(
            "The Whisperer",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Lassar Undercity",
            false);
    final PreparationCatalog.PreparationPlan preparation =
        PreparationCatalog.resolve(
            "The Whisperer",
            "Lassar Undercity",
            whisperer,
            null,
            null,
            null,
            Map.of(
                ItemID.FIRERUNE, 1000,
                ItemID.BLOODRUNE, 500,
                ItemID.COSMICRUNE, 500));
    assertEquals("tumeken s shadow", whisperer.getWeaponPriorities().get(0));
    assertTrue(whisperer.getWeaponPriorities().contains("eye of ayak"));

    assertTrue(preparation.isActive());
    assertEquals(3, preparation.getRuneStatuses().size());
    assertEquals(
        Arrays.asList("Fire", "Blood", "Cosmic"),
        preparation.getRuneStatuses().stream()
            .map(PreparationCatalog.RuneStatus::getName)
            .collect(java.util.stream.Collectors.toList()));
    assertTrue(
        preparation.getRuneStatuses().stream()
            .allMatch(status -> status.getRequired() > 0 && status.getAvailable() == 0));
    final PreparationCatalog.PreparationPlan refreshedPreparation =
        PreparationCatalog.resolve(
            "The Whisperer",
            "Lassar Undercity",
            whisperer,
            null,
            null,
            null,
            Map.of(
                ItemID.FIRERUNE, 1000,
                ItemID.BLOODRUNE, 500,
                ItemID.COSMICRUNE, 500));
    assertTrue(preparation != refreshedPreparation);
    assertEquals(
        SlayerTaskReadinessOverlay.renderFingerprint(preparation),
        SlayerTaskReadinessOverlay.renderFingerprint(refreshedPreparation));
    final MethodRules rules =
        SlayerMethodRuleCatalog.resolve("The Whisperer", "Lassar Undercity", whisperer);
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Blackstone fragment")));
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Lost Souls weapon")
                        && item.getAlternatives().get(0).equals("venator bow")));
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Special attack weapon")
                        && item.getAlternatives().get(0).equals("eldritch nightmare staff")));
  }

  @Test
  public void regularBlueDragonsNeverPairLightbearerWithoutASpecWeapon() {
    assertTrue(!SlayerLoadoutAnalyzer.regularBlueDragonsAllowLightbearerForTest());
    assertTrue(SlayerLoadoutAnalyzer.vorkathAllowsLightbearerForTest());
  }

  @Test
  public void vorkathUsesWikiRunePouchPackageAndRuneReferencesStayBelowGear() {
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Vorkath",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Ungael",
            false);
    final MethodRules rules = SlayerMethodRuleCatalog.resolve("Vorkath", "Ungael", strategy);

    assertEquals(4, rules.getPouchRunes().size());
    assertEquals(ItemID.AIRRUNE, rules.getPouchRunes().get(0).getItemId());
    assertEquals(ItemID.EARTHRUNE, rules.getPouchRunes().get(1).getItemId());
    assertEquals(ItemID.CHAOSRUNE, rules.getPouchRunes().get(2).getItemId());
    assertEquals(ItemID.LAWRUNE, rules.getPouchRunes().get(3).getItemId());
    assertEquals(MethodRules.Spellbook.STANDARD, rules.getSpellbook());
    assertTrue(rules.requiresRunePouch());
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Diamond dragon bolts (e) switch")));
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Special attack weapon")));
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Crumble Undead autocast switch")));
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Tick-safe healing")));
    assertTrue(
        !rules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Crumble Undead staff")));
    assertTrue(BankTagLayout.preparationReferencesRenderBelowGearForTest());

    final PreparationCatalog.PreparationPlan preparation =
        PreparationCatalog.resolve(
            "Vorkath",
            "Ungael",
            strategy,
            null,
            null,
            null,
            Map.of(
                ItemID.DUSTRUNE, 1000,
                ItemID.CHAOSRUNE, 1000,
                ItemID.LAWRUNE, 1000));
    assertTrue(preparation.isActive());
    assertEquals(ItemID.BH_RUNE_POUCH, (int) preparation.getTagIds().get(0));
    assertTrue(preparation.getTagIds().contains(ItemID.DUSTRUNE));
    assertTrue(preparation.getTagIds().contains(ItemID.CHAOSRUNE));
    assertTrue(preparation.getTagIds().contains(ItemID.LAWRUNE));
  }

  @Test
  public void globalRunePolicyUsesOnlyOwnedRunesAndCollapsesCombinations() {
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Vorkath",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Ungael",
            false);
    final MethodRules rules = SlayerMethodRuleCatalog.resolve("Vorkath", "Ungael", strategy);

    final RunePolicy.Resolution dustPackage =
        RunePolicy.resolve(
            rules.getPouchRunes(),
            Map.of(
                ItemID.DUSTRUNE, 500,
                ItemID.CHAOSRUNE, 500,
                ItemID.LAWRUNE, 500));
    assertEquals(3, dustPackage.getRunes().size());
    assertEquals(ItemID.DUSTRUNE, dustPackage.getRunes().get(0).getItemId());
    assertTrue(dustPackage.getUnownedRequirements().isEmpty());

    final RunePolicy.Resolution basePackage =
        RunePolicy.resolve(
            rules.getPouchRunes(),
            Map.of(
                ItemID.AIRRUNE, 500,
                ItemID.EARTHRUNE, 500,
                ItemID.CHAOSRUNE, 500));
    assertEquals(3, basePackage.getRunes().size());
    assertTrue(
        basePackage.getRunes().stream()
            .noneMatch(
                rune -> rune.getItemId() == ItemID.DUSTRUNE || rune.getItemId() == ItemID.LAWRUNE));
    assertEquals(Collections.singletonList("Law"), basePackage.getUnownedRequirements());

    final PreparationCatalog.PreparationPlan noRunes =
        PreparationCatalog.resolve(
            "Vorkath", "Ungael", strategy, null, null, null, Collections.emptyMap());
    assertEquals(Collections.singletonList(ItemID.BH_RUNE_POUCH), noRunes.getTagIds());
  }

  @Test
  public void bankTagDoesNotReplaceUnrelatedPlannedCapeWithOwnedQuiver() {
    assertTrue(!BankTagLayout.shouldResolveOwnedDizanaVariantForPlan(ItemID.COINS));
    assertTrue(BankTagLayout.shouldResolveOwnedDizanaVariantForPlan(ItemID.DIZANAS_QUIVER_CHARGED));
  }

  @Test
  public void vorkathOwnedMeleeOverrideSurvivesBossVariantResolution() {
    final TaskStrategy melee =
        SlayerTaskStrategyCatalog.resolve(
            "Vorkath",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_MELEE,
            "Ungael",
            false);
    final Recommendation ownedRecommendation =
        new Recommendation(
            "Ungael",
            melee.getMethod(),
            "Owned melee setup",
            "Travel to Rellekka",
            "Not allowed",
            "Rune pouch",
            "Boss alternative",
            melee);

    final VariantCatalog.ResolvedTarget resolved =
        VariantCatalog.resolve(
            "Blue dragons", TaskVariant.VORKATH, ownedRecommendation, new SlayerPlusConfig() {});

    assertEquals("Vorkath", resolved.getTaskName());
    assertEquals(TaskStrategy.CombatStyle.MELEE, resolved.getStrategy().getStyle());
    final MethodRules meleeRules =
        SlayerMethodRuleCatalog.resolve("Vorkath", "Ungael", resolved.getStrategy());
    assertEquals(3, meleeRules.resolveRestoreSlots(resolved.getStrategy()));
    assertTrue(
        !meleeRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().contains("Diamond dragon bolts")));
  }

  @Test
  public void everyNamedDefenderIncludingGhommalVariantsMatchesShieldFallback() {
    assertTrue(
        SlayerLoadoutAnalyzer.defenderFallbackMatchesShieldForTest(
            "Ghommal's avernic defender 5 (l)"));
    assertTrue(SlayerLoadoutAnalyzer.defenderFallbackMatchesShieldForTest("Dragon defender"));
    assertTrue(
        !SlayerLoadoutAnalyzer.defenderFallbackMatchesShieldForTest("Avernic defender hilt"));
    assertTrue(!SlayerLoadoutAnalyzer.defenderFallbackMatchesShieldForTest("Ghommal's hilt 6"));
  }

  @Test
  public void blueDragonSafespotInventoryLeavesDropSpaceWithoutRestores() {
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Blue dragons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Taverley Dungeon",
            false);
    final MethodRules rules =
        SlayerMethodRuleCatalog.resolve("Blue dragons", "Taverley Dungeon", strategy);

    assertEquals(24, rules.getLoot());
    assertEquals(0, rules.resolveRestoreSlots(strategy));
    assertEquals(0, rules.resolveFoodSlots(strategy, strategy.getFood()));
  }

  @Test
  public void blueDragonRangedMethodHasCompleteOwnedGearProgression() {
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Blue dragons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_RANGED,
            "Taverley Dungeon",
            false);
    final List<String> weapons = strategy.getWeaponPriorities();
    assertEquals("dragon hunter crossbow", weapons.get(0));
    assertTrue(weapons.indexOf("armadyl crossbow") < weapons.indexOf("dragon crossbow"));
    assertTrue(weapons.contains("hunters sunlight crossbow"));
    assertEquals("rune crossbow", weapons.get(weapons.size() - 1));

    final List<String> heads =
        SlayerEquipmentAuditCatalog.priorities(
            "Blue dragons", strategy, EquipmentInventorySlot.HEAD, "Rune crossbow");
    assertEquals("slayer helmet i", heads.get(0));
    assertTrue(heads.contains("blessed coif"));

    final List<String> shields =
        SlayerEquipmentAuditCatalog.priorities(
            "Blue dragons", strategy, EquipmentInventorySlot.SHIELD, "Rune crossbow");
    assertTrue(shields.contains("dragonfire ward"));
  }

  @Test
  public void blueDragonMagicUsesWaterWeaknessAndStructuredRunePouch() {
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Blue dragons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_MAGIC,
            "Taverley Dungeon",
            false);
    final MethodRules rules =
        SlayerMethodRuleCatalog.resolve("Blue dragons", "Taverley Dungeon", strategy);

    assertEquals(TaskStrategy.CombatStyle.MAGIC, strategy.getStyle());
    assertTrue(strategy.getMethod().contains("Water Blast"));
    assertEquals("dragon hunter wand", strategy.getWeaponPriorities().get(0));
    assertEquals(MethodRules.Spellbook.STANDARD, rules.getSpellbook());
    assertTrue(rules.requiresRunePouch());
    assertEquals(3, rules.getPouchRunes().size());
    assertEquals(ItemID.WATERRUNE, rules.getPouchRunes().get(1).getItemId());
    assertEquals(24, rules.getLoot());
    assertEquals(0, rules.resolveFoodSlots(strategy, strategy.getFood()));
  }

  @Test
  public void blueDragonMeleeDoesNotInheritSafespotInventory() {
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Blue dragons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_MELEE,
            "Taverley Dungeon",
            false);
    final MethodRules rules =
        SlayerMethodRuleCatalog.resolve("Blue dragons", "Taverley Dungeon", strategy);

    assertEquals(TaskStrategy.CombatStyle.MELEE, strategy.getStyle());
    assertEquals(12, rules.getLoot());
    assertEquals(6, rules.resolveFoodSlots(strategy, strategy.getFood()));
  }

  @Test
  public void regularDragonRangedPackagesNeverPairTwoHandedWeaponsWithTheRequiredShield() {
    for (final String task : Arrays.asList("Green dragons", "Red dragons", "Black dragons")) {
      final TaskStrategy strategy =
          SlayerTaskStrategyCatalog.resolve(
              task,
              Preference.Playstyle.FAST_XP,
              Preference.Cannon.NEVER,
              Preference.Burst.NEVER,
              Preference.CombatStyle.PREFER_RANGED,
              "Standard Slayer location",
              false);
      assertEquals(TaskStrategy.CombatStyle.RANGED, strategy.getStyle());
      assertFalse(strategy.getWeaponPriorities().contains("twisted bow"));
      assertFalse(strategy.getWeaponPriorities().contains("bow of faerdhinen"));
      assertFalse(strategy.getWeaponPriorities().contains("toxic blowpipe"));
      assertTrue(strategy.getWeaponPriorities().contains("rune crossbow"));
    }
  }

  @Test
  public void metalDragonsOfferCompleteEarthWaveAndDragonfirePackages() {
    final TaskStrategy magic =
        SlayerTaskStrategyCatalog.resolve(
            "Metal dragons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_MAGIC,
            "Brimhaven Dungeon",
            false);
    final MethodRules rules =
        SlayerMethodRuleCatalog.resolve("Metal dragons", "Brimhaven Dungeon", magic);

    assertEquals(TaskStrategy.CombatStyle.MAGIC, magic.getStyle());
    assertTrue(magic.getMethod().contains("Earth Wave"));
    assertFalse(magic.getWeaponPriorities().contains("tumeken s shadow"));
    assertEquals(MethodRules.Spellbook.STANDARD, rules.getSpellbook());
    assertEquals(3, rules.getPouchRunes().size());
    assertEquals(ItemID.AIRRUNE, rules.getPouchRunes().get(0).getItemId());
    assertEquals(ItemID.EARTHRUNE, rules.getPouchRunes().get(1).getItemId());
    assertEquals(ItemID.BLOODRUNE, rules.getPouchRunes().get(2).getItemId());
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals(PotionPolicy.EXTENDED_ANTIFIRE_DISPLAY)
                        && item.getSlotCount() == 2));
  }

  @Test
  public void royalTitansUseMeleeBaseWithCompleteMechanicSwitches() {
    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "Royal Titans",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Royal Titans arena",
            false);
    final MethodRules rules =
        SlayerMethodRuleCatalog.resolve("Royal Titans", "Royal Titans arena", strategy);

    assertEquals(TaskStrategy.CombatStyle.MELEE, strategy.getStyle());
    assertEquals("scythe of vitur", strategy.getWeaponPriorities().get(0));
    assertEquals(5, rules.resolveRestoreSlots(strategy));
    assertEquals(MethodRules.Spellbook.STANDARD, rules.getSpellbook());
    assertEquals(4, rules.getPouchRunes().size());
    assertFalse(
        rules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Melee weapon")));
    for (final String required :
        Arrays.asList(
            "Ranged weapon switch",
            "Ranged body switch",
            "Ranged legs switch",
            "Elemental spell staff",
            "Magic cape switch",
            "Magic body switch",
            "Magic legs switch",
            "Magic amulet switch",
            "Magic glove switch",
            "Melee special attack weapon")) {
      assertTrue(
          required,
          rules.getRequiredItems().stream()
              .anyMatch(item -> item.getDisplayName().equals(required)));
    }
    assertEquals(
        "infernal cape",
        SlayerEquipmentAuditCatalog.priorities(
                "Royal Titans", strategy, EquipmentInventorySlot.CAPE, "Scythe of vitur")
            .get(0));
  }

  @Test
  public void ordinaryEquipmentSlotsUseOwnedFallbacksInsteadOfBlankPlaceholders() {
    for (final EquipmentInventorySlot slot :
        Arrays.asList(
            EquipmentInventorySlot.HEAD,
            EquipmentInventorySlot.CAPE,
            EquipmentInventorySlot.AMULET,
            EquipmentInventorySlot.BODY,
            EquipmentInventorySlot.WEAPON,
            EquipmentInventorySlot.SHIELD,
            EquipmentInventorySlot.LEGS,
            EquipmentInventorySlot.GLOVES,
            EquipmentInventorySlot.BOOTS,
            EquipmentInventorySlot.RING)) {
      assertTrue(
          slot.name(),
          SlayerLoadoutAnalyzer.ordinaryOwnedSlotFallbackForTest(
              slot, "Regression owned " + slot.name().toLowerCase()));
    }
  }

  @Test
  public void vorkathInventoryUsesSwitchSupplyAndUtilityRows() {
    final List<KitItem> inventory =
        Arrays.asList(
            groupItem("Fremennik sea boots 4", MethodRules.InventoryGroup.TRAVEL),
            groupItem("Divine rune pouch", MethodRules.InventoryGroup.UTILITY),
            switchItem("Slayer's staff", KitItem.SwitchStyle.MAGIC)
                .withInventoryGroup(MethodRules.InventoryGroup.SWITCH),
            switchItem("Zaryte crossbow", KitItem.SwitchStyle.RANGED)
                .withInventoryGroup(MethodRules.InventoryGroup.SWITCH),
            groupItem("Diamond dragon bolts (e)", MethodRules.InventoryGroup.RUNES),
            groupItem("Divine ranging potion", MethodRules.InventoryGroup.BOOST),
            groupItem("Extended anti-venom+", MethodRules.InventoryGroup.PROTECTION),
            groupItem("Extended super antifire", MethodRules.InventoryGroup.PROTECTION),
            groupItem("Prayer potion A", MethodRules.InventoryGroup.RESTORE),
            groupItem("Prayer potion B", MethodRules.InventoryGroup.RESTORE),
            groupItem("Prayer potion C", MethodRules.InventoryGroup.RESTORE),
            groupItem("Anglerfish", MethodRules.InventoryGroup.FOOD),
            groupItem("Guthix rest", MethodRules.InventoryGroup.FOOD));
    final List<BankTagLayout.InventoryPlacement> placements =
        BankTagLayout.planInventoryForBankTag(inventory, -1, true);

    assertEquals(0, slotFor(placements, "Slayer's staff"));
    assertEquals(1, slotFor(placements, "Zaryte crossbow"));
    assertEquals(2, slotFor(placements, "Diamond dragon bolts (e)"));
    assertEquals(3, slotFor(placements, "Divine ranging potion"));
    assertEquals(4, slotFor(placements, "Extended anti-venom+"));
    assertEquals(5, slotFor(placements, "Extended super antifire"));
    assertEquals(6, slotFor(placements, "Prayer potion A"));
    assertEquals(9, slotFor(placements, "Anglerfish"));
    assertEquals(10, slotFor(placements, "Guthix rest"));
    assertEquals(26, slotFor(placements, "Fremennik sea boots 4"));
    assertEquals(27, slotFor(placements, "Divine rune pouch"));
  }

  @Test
  public void sidebarUsesSettingsInsteadOfPlaceholderLeaderboard() {
    final SlayerPlusPanel panel = new SlayerPlusPanel();
    assertTrue(containsLabelText(panel, "SETTINGS"));
    assertTrue(containsLabelText(panel, "Combat style"));
    assertTrue(containsLabelText(panel, "Slayer helm"));
    assertTrue(containsLabelText(panel, "Shard preference"));
    assertTrue(containsLabelText(panel, "Travel priority"));
    assertFalse(containsLabelText(panel, "ROUTING & DISPLAY"));
    assertFalse(containsLabelText(panel, "Enable Shortest Path routing"));
    assertFalse(containsLabelText(panel, "Show loadout recommendations"));
    assertTrue(!containsLabelText(panel, "SLAYER LEADERBOARD"));
  }

  @Test
  public void deepCatalogContractsRemainValid() {
    SlayerCatalogRegressionValidator.validateDeepOrThrow();
  }

  @Test
  public void officialTaskAliasesResolveToReviewedProfiles() {
    for (final String task :
        Arrays.asList(
            "Bloodvelds",
            "Dagannoths",
            "Kurasks",
            "Nechryaels",
            "Minions of Scabaras",
            "Bronze dragons",
            "Iron dragons",
            "Steel dragons",
            "Mithril dragons",
            "Adamant dragons",
            "Rune dragons")) {
      assertTrue(task, SlayerTaskStrategyCatalog.hasExplicitStrategy(task));
      final TaskStrategy strategy =
          SlayerTaskStrategyCatalog.resolve(
              task,
              Preference.Playstyle.FAST_XP,
              Preference.Cannon.ALLOW,
              Preference.Burst.ALLOW,
              Preference.CombatStyle.AUTOMATIC,
              "Not restricted",
              false);
      assertTrue(task, strategy.isReviewed());
      assertTrue(task, !strategy.getWeaponPriorities().isEmpty());
    }
  }

  @Test
  public void currentBossAndDemiBossAlternativesAreSelectable() {
    assertTrue(VariantCatalog.getAvailableVariants("Cows").contains(TaskVariant.BRUTUS));
    assertTrue(
        VariantCatalog.getAvailableVariants("Black demons").contains(TaskVariant.DEMONIC_GORILLAS));
    assertTrue(
        VariantCatalog.getAvailableVariants("Monkeys").contains(TaskVariant.DEMONIC_GORILLAS));
    assertTrue(
        VariantCatalog.getAvailableVariants("Greater demons")
            .contains(TaskVariant.TORMENTED_DEMONS));
    assertTrue(
        VariantCatalog.getAvailableVariants("Crazy Archaeologist")
            .contains(TaskVariant.DERANGED_ARCHAEOLOGIST));
  }

  @Test
  public void inactiveTaskHeaderDoesNotOverlapWithStatusCopy() {
    final SlayerPlusPanel panel = new SlayerPlusPanel();
    panel.showNoTask(34, 753, "Duradel");

    assertTrue(containsLabelText(panel, "CURRENT TASK"));
    assertTrue(containsLabelText(panel, "No active task"));
    assertTrue(!containsLabelText(panel, "Get a Slayer assignment to begin"));
  }

  @Test
  public void pointBoostStatusExpandsTheCurrentTaskCard() {
    final SlayerPlusPanel panel = new SlayerPlusPanel();
    panel.showTask("Araxytes", 183, 231, "Kuradal", "", 119, 768);
    final int compactHeight = panel.currentTaskCardMaximumHeightForTest();

    panel.showPointBoostStatus("Point boosting resumes after this existing assignment.");

    assertTrue(containsLabelText(panel, "Point boosting resumes after this existing assignment."));
    assertTrue(panel.currentTaskCardMaximumHeightForTest() > compactHeight);
  }

  @Test
  public void sidebarWrappingTracksTheAvailableCardWidth() {
    final SlayerPlusPanel panel = new SlayerPlusPanel();
    panel.showTask(
        "A deliberately long Slayer assignment name for responsive wrapping",
        10,
        10,
        "Kuradal",
        "",
        119,
        768);

    panel.setCurrentTaskContainerWidthForTest(140);
    final int narrowWrapWidth = panel.currentTaskRenderedWrapWidthForTest();
    final int narrowHeight = panel.currentTaskNamePreferredHeightForTest();

    panel.setCurrentTaskContainerWidthForTest(230);
    final int wideWrapWidth = panel.currentTaskRenderedWrapWidthForTest();
    final int wideHeight = panel.currentTaskNamePreferredHeightForTest();

    assertTrue(wideWrapWidth > narrowWrapWidth);
    assertTrue(wideHeight < narrowHeight);
  }

  @Test
  public void currentTaskAndBankTagNamesUseCanonicalCapitalization() {
    for (final String reviewedName : TaskResearch.getReviewedTaskNames()) {
      final String displayName = SlayerDisplayText.assignment(reviewedName);
      for (final String word : displayName.split("\\s+")) {
        assertTrue(
            "Task word is not title-cased: " + displayName,
            word.isEmpty()
                || !Character.isLetter(word.charAt(0))
                || Character.isUpperCase(word.charAt(0)));
      }
    }
    assertEquals("The Whisperer", SlayerDisplayText.assignment("THE WHISPERER"));
    assertEquals("TzKal-Zuk", SlayerDisplayText.assignment("TZKAL-ZUK"));
    assertEquals("TzTok-Jad", SlayerDisplayText.assignment("TZTOK-JAD"));
    assertEquals("Kree'arra", SlayerDisplayText.assignment("KREE'ARRA"));
    assertEquals("K'ril Tsutsaroth", SlayerDisplayText.assignment("K'RIL TSUTSAROTH"));
    assertEquals("Greater Demons", SlayerDisplayText.assignment("GREATER DEMONS"));
    assertEquals("Fossil Island Wyverns", SlayerDisplayText.assignment("FOSSIL ISLAND WYVERNS"));
    assertEquals("Skeletal Wyverns", SlayerDisplayText.assignment("skeletal wyverns"));
    assertEquals("Skeletal Wyverns", SlayerDisplayText.bankSetupTitle("SKELETAL WYVERNS"));
    assertEquals("TEST: The Whisperer", SlayerDisplayText.assignment("test: the whisperer"));
    assertEquals(
        "Waiting for Slayer task", SlayerDisplayText.bankSetupTitle("WAITING FOR SLAYER TASK"));

    final SlayerPlusPanel panel = new SlayerPlusPanel();
    panel.showTask("TEST: THE WHISPERER", 3, 3, "Duradel", "", 79, 756);
    assertTrue(containsLabelText(panel, "TEST: The Whisperer"));
    assertTrue(containsLabelText(panel, "3 / 3 remaining"));

    panel.showRecommendation(
        null,
        new KitPlan(
            "—",
            "—",
            "No item scan available",
            "THE WHISPERER • Lassar Undercity",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()));
    assertTrue(containsLabelText(panel, "The Whisperer"));
    assertTrue(containsLabelText(panel, "Lassar Undercity"));
  }

  @Test
  public void infernoBankSnapshotPrefersBestSupportedGhommalVariant() {
    final int[] everyMorUlRekVariant = {
      ItemID.CA_OFFHAND_ELITE,
      ItemID.CA_OFFHAND_MASTER,
      ItemID.CA_OFFHAND_GRANDMASTER,
      ItemID.INFERNAL_DEFENDER_GHOMMAL_5,
      ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER,
      ItemID.INFERNAL_DEFENDER_GHOMMAL_6,
      ItemID.INFERNAL_DEFENDER_GHOMMAL_6_TROUVER
    };
    for (final int itemId : everyMorUlRekVariant) {
      assertEquals(
          itemId, SlayerPlusPlugin.preferredInfernoTravelItemId(Collections.singleton(itemId)));
    }

    assertEquals(
        ItemID.CA_OFFHAND_GRANDMASTER,
        SlayerPlusPlugin.preferredInfernoTravelItemId(
            Arrays.asList(
                ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER,
                ItemID.CA_OFFHAND_ELITE,
                ItemID.CA_OFFHAND_GRANDMASTER)));
    assertEquals(
        ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER,
        SlayerPlusPlugin.preferredInfernoTravelItemId(
            Collections.singleton(ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER)));
    assertEquals(
        -1, SlayerPlusPlugin.preferredInfernoTravelItemId(Collections.singleton(ItemID.COINS)));
  }

  @Test
  public void bankTagInventoryPresentationOnlyGroupsVisualSupplies() {
    final List<BankTagLayout.InventoryPlacement> placements =
        BankTagLayout.planInventoryForBankTag(
            Arrays.asList(
                item("Teleport"),
                item("Shark"),
                item("Super restore(4)"),
                item("Cannonballs"),
                item("Anti-venom+(4)"),
                item("Ranging potion(4)"),
                item("Ranged switch"),
                item("Saradomin brew(4)"),
                item("Manta ray"),
                item("Prayer potion(4)"),
                item("Shark")),
            -1,
            true);

    assertEquals(
        Arrays.asList(
            "Cannonballs",
            "Ranged switch",
            "Ranging potion(4)",
            "Anti-venom+(4)",
            "Saradomin brew(4)",
            "Prayer potion(4)",
            "Super restore(4)",
            "Manta ray",
            "Shark",
            "Shark",
            "Teleport"),
        namesBySlot(placements));
    assertEquals(27, slotFor(placements, "Teleport"));
  }

  @Test
  public void bankTagPresentationPinsRunePouchToLastInventorySlot() {
    final List<BankTagLayout.InventoryPlacement> placements =
        BankTagLayout.planInventoryForBankTag(
            Arrays.asList(
                item("First switch"), item("Task tool"), item("Rune pouch"), item("Cannonballs")),
            -1,
            false);

    assertEquals(0, slotFor(placements, "First switch"));
    assertEquals(1, slotFor(placements, "Cannonballs"));
    assertEquals(26, slotFor(placements, "Task tool"));
    assertEquals(27, slotFor(placements, "Rune pouch"));
  }

  @Test
  public void bankTagPresentationPlacesMatchingTopsDirectlyAboveLegs() {
    final List<BankTagLayout.InventoryPlacement> placements =
        BankTagLayout.planInventoryForBankTag(
            Arrays.asList(
                item("Masori chaps (f)"),
                item("Ancestral robe bottom"),
                item("Masori body (f)"),
                item("Shark"),
                item("Ancestral robe top"),
                item("Proselyte cuisse"),
                item("Proselyte hauberk"),
                item("Divine rune pouch")),
            0,
            false);

    assertDirectlyAbove(placements, "Masori body (f)", "Masori chaps (f)");
    assertDirectlyAbove(placements, "Ancestral robe top", "Ancestral robe bottom");
    assertDirectlyAbove(placements, "Proselyte hauberk", "Proselyte cuisse");
    assertEquals(27, slotFor(placements, "Divine rune pouch"));
  }

  @Test
  public void bankTagPresentationUsesCannonBlockAndBottomUtilityStrip() {
    final List<BankTagLayout.InventoryPlacement> placements =
        BankTagLayout.planInventoryForBankTag(
            Arrays.asList(
                item("Teleport to house"),
                item("Cannon furnace"),
                item("Cannon base"),
                item("Cannon barrels"),
                item("Cannon stand"),
                item("Cannonballs"),
                groupItem("Open herb sack", MethodRules.InventoryGroup.UTILITY),
                groupItem("Book of the dead", MethodRules.InventoryGroup.UTILITY),
                item("Prayer potion(4)"),
                item("Divine rune pouch")),
            -1,
            true);

    assertEquals(0, slotFor(placements, "Cannon base"));
    assertEquals(1, slotFor(placements, "Cannon stand"));
    assertEquals(4, slotFor(placements, "Cannon barrels"));
    assertEquals(5, slotFor(placements, "Cannon furnace"));
    assertEquals(24, slotFor(placements, "Open herb sack"));
    assertEquals(25, slotFor(placements, "Book of the dead"));
    assertEquals(26, slotFor(placements, "Teleport to house"));
    assertEquals(27, slotFor(placements, "Divine rune pouch"));
  }

  @Test
  public void bankTagPresentationStacksSameStyleSwitchGearDownColumns() {
    final List<BankTagLayout.InventoryPlacement> placements =
        BankTagLayout.planInventoryForBankTag(
            Arrays.asList(
                switchItem("Toxic blowpipe", KitItem.SwitchStyle.RANGED),
                switchItem("Ancient Magicks weapon", KitItem.SwitchStyle.MAGIC),
                item("Saradomin brew(4)"),
                switchItem("Mage leg switch", KitItem.SwitchStyle.MAGIC),
                switchItem("Mage body switch", KitItem.SwitchStyle.MAGIC),
                switchItem("Magic off-hand switch", KitItem.SwitchStyle.MAGIC),
                switchItem("Magic damage switch", KitItem.SwitchStyle.MAGIC),
                item("Super restore(4)"),
                item("Divine rune pouch")),
            -1,
            false);

    assertEquals(0, slotFor(placements, "Mage body switch"));
    assertEquals(4, slotFor(placements, "Mage leg switch"));
    assertEquals(1, slotFor(placements, "Ancient Magicks weapon"));
    assertEquals(5, slotFor(placements, "Magic off-hand switch"));
    assertEquals(2, slotFor(placements, "Magic damage switch"));
    assertEquals(3, slotFor(placements, "Toxic blowpipe"));
    assertEquals(6, slotFor(placements, "Saradomin brew(4)"));
    assertEquals(7, slotFor(placements, "Super restore(4)"));
    assertEquals(27, slotFor(placements, "Divine rune pouch"));
  }

  @Test
  public void wikiEquipmentProgressionsReachOwnedTiersAndDriveBankTags() {
    final TaskStrategy melee =
        TaskStrategy.builder(TaskStrategy.CombatStyle.MELEE, "General melee Slayer")
            .weapons("Abyssal whip")
            .build();
    final List<String> meleeHead =
        SlayerEquipmentAuditCatalog.priorities(
            "Dagannoth", melee, EquipmentInventorySlot.HEAD, "Abyssal whip");
    assertEquals("slayer helmet i", meleeHead.get(0));
    assertTrue(meleeHead.indexOf("slayer helmet") > meleeHead.indexOf("black mask i"));

    final List<String> meleeBody =
        SlayerEquipmentAuditCatalog.priorities(
            "Dagannoth", melee, EquipmentInventorySlot.BODY, "Abyssal whip");
    assertTrue(meleeBody.indexOf("bandos chestplate") < meleeBody.indexOf("fighter torso"));
    assertTrue(meleeBody.indexOf("fighter torso") < meleeBody.indexOf("rune platebody"));

    final TaskStrategy ranged =
        TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "General ranged Slayer")
            .weapons("Toxic blowpipe")
            .build();
    final List<String> rangedBody =
        SlayerEquipmentAuditCatalog.priorities(
            "Bloodveld", ranged, EquipmentInventorySlot.BODY, "Toxic blowpipe");
    assertEquals("masori body f", rangedBody.get(0));
    assertTrue(rangedBody.indexOf("blessed body") < rangedBody.indexOf("black d hide body"));
    assertTrue(rangedBody.contains("green d hide body"));

    final TaskStrategy magic =
        TaskStrategy.builder(TaskStrategy.CombatStyle.MAGIC, "General magic Slayer")
            .weapons("Trident of the seas")
            .build();
    final List<String> magicBody =
        SlayerEquipmentAuditCatalog.priorities(
            "Metal dragon", magic, EquipmentInventorySlot.BODY, "Trident of the seas");
    assertEquals("virtus robe top", magicBody.get(0));
    assertTrue(magicBody.indexOf("mystic robe top") < magicBody.indexOf("xerician top"));

    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Saradomin d'hide body", "Blessed body"));
    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Imbued Guthix cape", "Imbued god cape"));
    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Staff of fire", "Elemental staff"));

    final List<String> weapons = SlayerLoadoutAnalyzer.weaponProgressionForTest(melee);
    assertEquals("Abyssal whip", weapons.get(0));
    assertTrue(weapons.contains("rune scimitar"));
    assertTrue(weapons.contains("iron scimitar"));
  }

  @Test
  public void wildernessAndStrictProfilesDoNotReceiveUnsafeGearFallbacks() {
    final TaskStrategy wilderness =
        TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "Wilderness ranged Slayer")
            .costPolicy(TaskStrategy.CostPolicy.LOW_RISK)
            .weapons("Webweaver bow")
            .build();
    final List<String> wildernessBodies =
        SlayerEquipmentAuditCatalog.priorities(
            "Revenants", wilderness, EquipmentInventorySlot.BODY, "Webweaver bow");
    assertEquals("black d hide body", wildernessBodies.get(0));
    assertTrue(!wildernessBodies.contains("masori body f"));

    final TaskStrategy strict =
        TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "Mechanic-specific ranged method")
            .weapons("Dragon hunter crossbow")
            .strictWeaponProfile(true)
            .build();
    final List<String> strictWeapons = SlayerLoadoutAnalyzer.weaponProgressionForTest(strict);
    assertEquals(1, strictWeapons.size());
    assertEquals("Dragon hunter crossbow", strictWeapons.get(0));

    final TaskStrategy boss =
        TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "Ranged boss method")
            .boss(true)
            .build();
    final List<String> bossGloves =
        SlayerEquipmentAuditCatalog.priorities("Vorkath", boss, EquipmentInventorySlot.GLOVES, "");
    assertEquals("zaryte vambraces", bossGloves.get(0));
    assertTrue(!bossGloves.contains("expeditious bracelet"));
    assertTrue(!bossGloves.contains("bracelet of slaughter"));
  }

  @Test
  public void upgradedAndCosmeticEquipmentVariantsKeepTheirRealProgressionTier() {
    final List<String> masori = Arrays.asList("masori body f", "masori body", "blessed body");
    assertEquals(
        0, SlayerLoadoutAnalyzer.directEquipmentProgressionRankForTest("Masori body (f)", masori));
    assertEquals(
        1, SlayerLoadoutAnalyzer.directEquipmentProgressionRankForTest("Masori body", masori));
    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Radiant oathplate chest", "Oathplate chest"));
    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Imbued Saradomin max cape", "Imbued god cape"));
    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Infernal max cape", "Infernal cape"));
    assertEquals(
        "Infernal max cape",
        SlayerLoadoutAnalyzer.preferredInventoryEquipmentForTest(
            Arrays.asList("Fire cape", "Infernal max cape"),
            "infernal cape",
            "fire cape",
            "mythical cape"));
    assertEquals(
        "Imbued Saradomin max cape",
        SlayerLoadoutAnalyzer.preferredInventoryEquipmentForTest(
            Arrays.asList("God cape", "Imbued Saradomin max cape"), "imbued god cape", "god cape"));
    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Dizana's max cape", "Dizana's quiver"));
    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Masori assembler max cape", "Ava's assembler"));
    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Saradomin coif", "Blessed coif"));
    assertTrue(
        SlayerLoadoutAnalyzer.equipmentProgressionNameMatchesForTest(
            "Dharok's platebody 50", "Barrows platebody"));
    assertTrue(
        !SlayerLoadoutAnalyzer.usableEquipmentVariantForTest(
            "Dharok's platebody 0", EquipmentInventorySlot.BODY));
    assertTrue(
        !SlayerLoadoutAnalyzer.usableEquipmentVariantForTest(
            "Scythe of vitur (uncharged)", EquipmentInventorySlot.WEAPON));
    assertTrue(
        SlayerLoadoutAnalyzer.usableEquipmentVariantForTest(
            "Dizana's quiver (uncharged)", EquipmentInventorySlot.CAPE));

    final TaskStrategy ranged =
        TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "Current ranged progression").build();
    final List<String> rangedBoots =
        SlayerEquipmentAuditCatalog.priorities(
            "Wyrms", ranged, EquipmentInventorySlot.BOOTS, "Toxic blowpipe");
    assertTrue(rangedBoots.indexOf("avernic treads pe") < rangedBoots.indexOf("avernic treads"));
    assertTrue(rangedBoots.indexOf("avernic treads") < rangedBoots.indexOf("pegasian boots"));

    final TaskStrategy magic =
        TaskStrategy.builder(TaskStrategy.CombatStyle.MAGIC, "Current magic progression").build();
    assertTrue(
        SlayerEquipmentAuditCatalog.weaponProgression(magic).indexOf("eye of ayak")
            < SlayerEquipmentAuditCatalog.weaponProgression(magic).indexOf("sanguinesti staff"));
  }

  @Test
  public void wikiCorrectionsKeepFireGiantsTzhaarAndBrutusExplicit() {
    final TaskStrategy fireGiants =
        SlayerTaskStrategyCatalog.resolve(
            "Fire giants",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.ALLOW,
            Preference.Burst.ALLOW,
            Preference.CombatStyle.AUTOMATIC,
            "Catacombs of Kourend",
            false);
    assertEquals(TaskStrategy.CombatStyle.MAGIC, fireGiants.getStyle());
    assertTrue(fireGiants.getMethod().contains("Water"));

    final TaskStrategy tzhaar =
        SlayerTaskStrategyCatalog.resolve(
            "Tzhaar",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.ALLOW,
            Preference.CombatStyle.AUTOMATIC,
            "Mor Ul Rek",
            false);
    assertTrue(tzhaar.getMethod().contains("Blood Barrage"));

    final TaskStrategy brutus =
        SlayerTaskStrategyCatalog.resolve(
            "Brutus",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Lumbridge cow field",
            false);
    final MethodRules brutusRules =
        SlayerMethodRuleCatalog.resolve("Brutus", "Lumbridge cow field", brutus);
    assertEquals("prayer potion", brutusRules.getPrimaryRestoreFamily());
    assertEquals(0, brutusRules.resolveRestoreSlots(brutus));
    assertTrue(
        brutusRules.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Divine ranging potion")
                        && item.getSlotCount() == 1));
    assertTrue(
        brutusRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Optional cooking tool")));
  }

  @Test
  public void lowLevelWikiMethodsRemainVisibleInGeneratedLoadouts() {
    final TaskStrategy birds =
        SlayerTaskStrategyCatalog.resolve(
            "Birds",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.ALLOW,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Lumbridge area",
            false);
    assertEquals(TaskStrategy.CombatStyle.RANGED, birds.getStyle());
    assertTrue(birds.getMethod().contains("flying variants"));

    final TaskStrategy rats =
        SlayerTaskStrategyCatalog.resolve(
            "Rats",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Lumbridge area",
            false);
    assertEquals("bone mace", rats.getWeaponPriorities().get(0));

    final TaskStrategy wolves =
        SlayerTaskStrategyCatalog.resolve(
            "Wolves",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.ALLOW,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Feldip Hills",
            false);
    assertEquals(TaskStrategy.CombatStyle.RANGED, wolves.getStyle());

    final MethodRules crocodiles =
        SlayerMethodRuleCatalog.resolve(
            "Crocodiles",
            "Nardah desert",
            SlayerTaskStrategyCatalog.resolve(
                "Crocodiles",
                Preference.Playstyle.FAST_XP,
                Preference.Cannon.NEVER,
                Preference.Burst.NEVER,
                Preference.CombatStyle.AUTOMATIC,
                "Nardah desert",
                false));
    assertTrue(
        crocodiles.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Desert heat protection")));
  }

  @Test
  public void genericOneByOneMeleeTargetsNeverInheritScytheFallback() {
    final TaskStrategy bats =
        SlayerTaskStrategyCatalog.resolve(
            "Bats",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Feldip Hills",
            false);
    assertFalse(
        SlayerLoadoutAnalyzer.weaponProgressionForTest("Bats", bats).contains("scythe of vitur"));
    assertEquals(TaskStrategy.CombatStyle.RANGED, bats.getStyle());
    assertTrue(bats.getWeaponPriorities().contains("toxic blowpipe"));
    assertTrue(bats.getWeaponPriorities().contains("shortbow"));
    assertFalse(bats.getWeaponPriorities().contains("blade of saeldor"));
    assertFalse(SlayerTargetFootprintCatalog.isReviewedMultiTileScytheTarget("Bats"));

    final TaskStrategy araxxor =
        SlayerTaskStrategyCatalog.resolve(
            "Araxxor",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Morytania Spider Cave",
            false);
    assertEquals("scythe of vitur", araxxor.getWeaponPriorities().get(0));
    assertTrue(
        SlayerLoadoutAnalyzer.weaponProgressionForTest("Araxxor", araxxor)
            .contains("scythe of vitur"));
    assertTrue(SlayerTargetFootprintCatalog.isReviewedMultiTileScytheTarget("Araxxor"));

    final TaskStrategy accidentalOneByOne =
        TaskStrategy.builder(
                TaskStrategy.CombatStyle.MELEE,
                "Regression profile with an accidentally authored Scythe")
            .weapons("scythe of vitur", "abyssal whip")
            .strictWeaponProfile(true)
            .build();
    assertEquals(
        Collections.singletonList("abyssal whip"),
        SlayerLoadoutAnalyzer.weaponProgressionForTest(
            "Unreviewed one tile target", accidentalOneByOne));
  }

  @Test
  public void grotesqueGuardiansUseCompleteReviewedHybridLoadout() {
    assertTrue(SlayerLoadoutAnalyzer.requiresGenericRockHammerForTest("Gargoyles"));
    assertFalse(SlayerLoadoutAnalyzer.requiresGenericRockHammerForTest("The Grotesque Guardians"));

    final TaskStrategy strategy =
        SlayerTaskStrategyCatalog.resolve(
            "The Grotesque Guardians",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Slayer Tower rooftop",
            false);
    assertEquals(TaskStrategy.CombatStyle.HYBRID, strategy.getStyle());
    assertEquals("scythe of vitur", strategy.getWeaponPriorities().get(0));
    assertTrue(strategy.getWeaponPriorities().contains("noxious halberd"));
    assertFalse(strategy.getWeaponPriorities().contains("inquisitor s mace"));

    final MethodRules inventory = bossInventoryRules("Grotesque Guardians");
    assertEquals(9, inventory.resolveRestoreSlots(strategy));
    assertEquals(6, inventory.resolveFoodSlots(strategy, 0));
    for (final String required :
        Arrays.asList(
            "Dawn primary ranged weapon",
            "Ranged necklace switch",
            "Ranged cape switch",
            "Ranged body switch",
            "Ranged legs switch",
            "Rock hammer")) {
      assertTrue(
          required,
          inventory.getRequiredItems().stream()
              .anyMatch(item -> item.getDisplayName().equals(required)));
    }

    final List<String> bodies =
        SlayerEquipmentAuditCatalog.priorities(
            "The Grotesque Guardians", strategy, EquipmentInventorySlot.BODY, "scythe of vitur");
    assertEquals("torva platebody", bodies.get(0));
    assertTrue(bodies.contains("fighter torso"));
    final List<String> rings =
        SlayerEquipmentAuditCatalog.priorities(
            "The Grotesque Guardians", strategy, EquipmentInventorySlot.RING, "scythe of vitur");
    assertEquals("ultor ring", rings.get(0));
    assertTrue(rings.contains("lightbearer"));

    final MethodRules complete =
        SlayerMethodRuleCatalog.resolve(
            "The Grotesque Guardians", "Slayer Tower rooftop", strategy);
    assertEquals(MethodRules.Spellbook.ARCEUUS, complete.getSpellbook());
    assertTrue(complete.requiresRunePouch());
    assertTrue(
        complete.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Book of the dead")));
  }

  @Test
  public void diaryTeleportsAreGatedByClaimedTier() {
    final Map<String, Integer> tiers = new HashMap<>();
    tiers.put("lumbridge", 2);
    tiers.put("wilderness", 3);
    final SlayerAchievementDiarySnapshot snapshot =
        SlayerAchievementDiarySnapshot.forRegression(tiers);
    assertTrue(snapshot.allowsTravelItem("Explorer's ring 2"));
    assertFalse(snapshot.allowsTravelItem("Explorer's ring 3"));
    assertTrue(snapshot.allowsTravelItem("Wilderness sword 3"));
    assertFalse(snapshot.allowsTravelItem("Wilderness sword 4"));
    assertTrue(snapshot.allowsTravelItem("Karamja gloves 4"));
    assertFalse(snapshot.allowsTravelItem("Achievement diary cape"));

    final Map<String, Integer> allElite = new HashMap<>();
    for (final String region :
        Arrays.asList(
            "ardougne",
            "desert",
            "falador",
            "fremennik",
            "kandarin",
            "karamja",
            "kourend",
            "lumbridge",
            "morytania",
            "varrock",
            "western",
            "wilderness")) {
      allElite.put(region, 4);
    }
    assertTrue(
        SlayerAchievementDiarySnapshot.forRegression(allElite)
            .allowsTravelItem("Achievement diary cape"));
  }

  @Test
  public void diaryLoadoutBenefitsAreTierGated() {
    final SlayerAchievementDiarySnapshot none = SlayerAchievementDiarySnapshot.empty();
    assertFalse(none.unlocksAshSanctifier());
    assertFalse(none.unlocksBonecrusher());
    assertFalse(none.unlocksGiantMoleLocator());
    assertFalse(none.removesKaruulmBootRequirement());
    assertTrue(none.allowsTravelItem("Dramen staff"));

    final Map<String, Integer> tiers = new HashMap<>();
    tiers.put("falador", 3);
    tiers.put("fremennik", 4);
    tiers.put("kandarin", 3);
    tiers.put("karamja", 4);
    tiers.put("kourend", 4);
    tiers.put("lumbridge", 4);
    tiers.put("morytania", 3);
    tiers.put("western", 4);
    tiers.put("wilderness", 4);
    final SlayerAchievementDiarySnapshot unlocked =
        SlayerAchievementDiarySnapshot.forRegression(tiers);

    assertTrue(unlocked.unlocksAshSanctifier());
    assertTrue(unlocked.unlocksBonecrusher());
    assertTrue(unlocked.unlocksGiantMoleLocator());
    assertTrue(unlocked.removesKaruulmBootRequirement());
    assertTrue(unlocked.improvesEnchantedBoltSpecials());
    assertTrue(unlocked.improvesBarrowsRuneRewards());
    assertTrue(unlocked.hasFightCavesDailyResurrection());
    assertTrue(unlocked.hasZulrahDailyResurrection());
    assertTrue(unlocked.notesAviansieAdamantBars());
    assertTrue(unlocked.notesDagannothKingBones());
    assertTrue(unlocked.notesBrimhavenDungeonDrops());
    assertTrue(unlocked.notesWildernessDragonBones());
    assertEquals(75, unlocked.slayerTowerExperienceBonusTenthsPercent());
    assertFalse(unlocked.allowsTravelItem("Dramen staff"));
    assertFalse(unlocked.allowsTravelItem("Lunar staff"));
  }

  @Test
  public void diaryUtilityPlaceholdersRequireTheirUnlockingDiary() {
    final SlayerAchievementDiarySnapshot none = SlayerAchievementDiarySnapshot.empty();
    assertFalse(none.allowsLoadoutReward("Ash sanctifier", Arrays.asList("ash sanctifier")));
    assertFalse(none.allowsLoadoutReward("Bonecrusher", Arrays.asList("bonecrusher")));
    assertFalse(none.allowsLoadoutReward("Mole locator", Arrays.asList("falador shield 3")));

    final Map<String, Integer> tiers = new HashMap<>();
    tiers.put("falador", 3);
    tiers.put("kourend", 3);
    tiers.put("morytania", 3);
    final SlayerAchievementDiarySnapshot unlocked =
        SlayerAchievementDiarySnapshot.forRegression(tiers);
    assertTrue(unlocked.allowsLoadoutReward("Ash sanctifier", Arrays.asList("ash sanctifier")));
    assertTrue(unlocked.allowsLoadoutReward("Bonecrusher", Arrays.asList("bonecrusher")));
    assertTrue(unlocked.allowsLoadoutReward("Mole locator", Arrays.asList("falador shield 3")));

    assertTrue(
        SlayerLoadoutAnalyzer.requiresKaruulmProtectionBootsForTest(
            "Alchemical Hydra", "Mount Karuulm", false));
    assertFalse(
        SlayerLoadoutAnalyzer.requiresKaruulmProtectionBootsForTest(
            "Alchemical Hydra", "Mount Karuulm", true));
  }

  @Test
  public void wikiElementalWeaknessesAreRealMagicPreferenceBranches() {
    assertTrue(SlayerElementalWeaknessCatalog.sizeForTest() >= 50);

    final TaskStrategy blackDemonMagic =
        SlayerTaskStrategyCatalog.resolve(
            "Black demons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_MAGIC,
            "Catacombs of Kourend",
            false);
    assertEquals(TaskStrategy.CombatStyle.MAGIC, blackDemonMagic.getStyle());
    assertTrue(blackDemonMagic.getMethod().contains("40% Water weakness"));

    final TaskStrategy blackDemonAutomatic =
        SlayerTaskStrategyCatalog.resolve(
            "Black demons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Catacombs of Kourend",
            false);
    assertEquals(TaskStrategy.CombatStyle.MELEE, blackDemonAutomatic.getStyle());

    final TaskStrategy waterfiends =
        SlayerTaskStrategyCatalog.resolve(
            "Waterfiends",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Ancient Cavern",
            false);
    assertEquals(TaskStrategy.CombatStyle.MAGIC, waterfiends.getStyle());
    assertTrue(waterfiends.getMethod().contains("100% Earth weakness"));
  }

  @Test
  public void auditedCannonAlternativeHonorsPreferAndNever() {
    final TaskStrategy preferred =
        SlayerTaskStrategyCatalog.resolve(
            "Hill giants",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.PREFER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Edgeville Dungeon",
            false);
    assertTrue(preferred.hasTag(TaskStrategy.MethodTag.CANNON));

    final TaskStrategy disabled =
        SlayerTaskStrategyCatalog.resolve(
            "Hill giants",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Edgeville Dungeon",
            false);
    assertTrue(!disabled.hasTag(TaskStrategy.MethodTag.CANNON));
  }

  @Test
  public void bloodveldLocationsUseTheirReviewedGearAndInventoryBranches() {
    final TaskStrategy meiyerditch =
        SlayerTaskStrategyCatalog.resolve(
            "Bloodveld",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.ALLOW,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Meiyerditch Laboratories",
            false);
    assertEquals("venator bow", meiyerditch.getWeaponPriorities().get(0));
    assertEquals(TaskStrategy.ArmourFocus.DAMAGE, meiyerditch.getArmourFocus());
    assertTrue(meiyerditch.hasTag(TaskStrategy.MethodTag.CANNON));
    assertTrue(meiyerditch.hasTag(TaskStrategy.MethodTag.VENATOR));
    assertEquals(TaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED, meiyerditch.getDamageProfile());

    final MethodRules meiyerditchRules =
        SlayerMethodRuleCatalog.resolve("Bloodveld", "Meiyerditch Laboratories", meiyerditch);
    assertEquals(6, meiyerditchRules.resolveRestoreSlots(meiyerditch));
    assertEquals(0, meiyerditchRules.resolveFoodSlots(meiyerditch, 0));
    assertTrue(meiyerditchRules.usesCannon());
    assertEquals(2000, meiyerditchRules.getCannonballQuantity());
    assertTrue(meiyerditchRules.requiresRunePouch());
    assertEquals(ItemID.NATURERUNE, meiyerditchRules.getPouchRunes().get(0).getItemId());
    assertEquals(ItemID.FIRERUNE, meiyerditchRules.getPouchRunes().get(1).getItemId());
    assertTrue(
        meiyerditchRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Ash sanctifier")));
    assertTrue(
        meiyerditchRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Soul bearer")));

    final TaskStrategy catacombs =
        SlayerTaskStrategyCatalog.resolve(
            "Bloodveld",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.PREFER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Catacombs of Kourend",
            false);
    assertTrue(catacombs.hasTag(TaskStrategy.MethodTag.VENATOR));
    assertTrue(!catacombs.hasTag(TaskStrategy.MethodTag.CANNON));
    final MethodRules catacombsRules =
        SlayerMethodRuleCatalog.resolve("Bloodveld", "Catacombs of Kourend", catacombs);
    assertEquals(5, catacombsRules.resolveRestoreSlots(catacombs));
    assertTrue(
        catacombsRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals(PotionPolicy.GOADING_DISPLAY)));

    final TaskStrategy tower =
        SlayerTaskStrategyCatalog.resolve(
            "Bloodveld",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.PREFER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Slayer Tower",
            false);
    assertTrue(tower.hasTag(TaskStrategy.MethodTag.SAFESPOT));
    assertTrue(!tower.hasTag(TaskStrategy.MethodTag.CANNON));

    final TaskStrategy godWars =
        SlayerTaskStrategyCatalog.resolve(
            "Bloodveld",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.PREFER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "God Wars Dungeon",
            false);
    assertTrue(godWars.hasTag(TaskStrategy.MethodTag.SAFESPOT));
    assertTrue(!godWars.hasTag(TaskStrategy.MethodTag.CANNON));
    assertTrue(godWars.getMethod().contains("God Wars"));
  }

  @Test
  public void greaterDemonFamilyUsesCompleteReviewedEncounterLoadouts() {
    final TaskStrategy regular =
        SlayerTaskStrategyCatalog.resolve(
            "Greater demons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Catacombs of Kourend",
            false);
    assertEquals(TaskStrategy.CombatStyle.MELEE, regular.getStyle());
    assertEquals("emberlight", regular.getWeaponPriorities().get(0));
    final MethodRules regularRules =
        SlayerMethodRuleCatalog.resolve("Greater demons", "Catacombs of Kourend", regular);
    assertTrue(
        regularRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Ash sanctifier")));

    final TaskStrategy water =
        SlayerTaskStrategyCatalog.resolve(
            "Greater demons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_MAGIC,
            "Catacombs of Kourend",
            false);
    assertEquals(TaskStrategy.CombatStyle.MAGIC, water.getStyle());
    assertTrue(water.getMethod().contains("40% Water weakness"));
    final TaskStrategy karuulmCannon =
        SlayerTaskStrategyCatalog.resolve(
            "Greater demons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.PREFER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Karuulm Slayer Dungeon",
            false);
    assertTrue(karuulmCannon.hasTag(TaskStrategy.MethodTag.CANNON));
    assertEquals(TaskStrategy.CombatStyle.RANGED, karuulmCannon.getStyle());

    final TaskStrategy chasmCannon =
        SlayerTaskStrategyCatalog.resolve(
            "Greater demons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.PREFER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Chasm of Fire",
            false);
    assertEquals(TaskStrategy.CombatStyle.RANGED, chasmCannon.getStyle());
    assertTrue(chasmCannon.hasTag(TaskStrategy.MethodTag.CANNON));
    assertTrue(chasmCannon.hasTag(TaskStrategy.MethodTag.SAFESPOT));

    final TaskStrategy explicitChasmMelee =
        SlayerTaskStrategyCatalog.resolve(
            "Greater demons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.PREFER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_MELEE,
            "Chasm of Fire",
            false);
    assertEquals(TaskStrategy.CombatStyle.MELEE, explicitChasmMelee.getStyle());
    assertFalse(explicitChasmMelee.hasTag(TaskStrategy.MethodTag.CANNON));

    final TaskStrategy kril =
        SlayerTaskStrategyCatalog.resolve(
            "K'ril Tsutsaroth",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "God Wars Dungeon",
            false);
    assertEquals(TaskStrategy.CombatStyle.RANGED, kril.getStyle());
    assertEquals("scorching bow", kril.getWeaponPriorities().get(0));
    assertEquals(
        "lightbearer",
        SlayerEquipmentAuditCatalog.priorities(
                "K'ril Tsutsaroth",
                kril,
                net.runelite.api.EquipmentInventorySlot.RING,
                "scorching bow")
            .get(0));
    final MethodRules krilRules =
        SlayerMethodRuleCatalog.resolve("K'ril Tsutsaroth", "God Wars Dungeon", kril);
    assertEquals(9, krilRules.resolveRestoreSlots(kril));
    assertEquals(MethodRules.Spellbook.ANCIENT, krilRules.getSpellbook());
    assertEquals("Blood Barrage", krilRules.getPrimarySpell());
    assertTrue(krilRules.requiresRunePouch());
    assertEquals(3, krilRules.getPouchRunes().size());
    assertEquals("Fire", krilRules.getPouchRunes().get(0).getName());
    assertEquals("Blood", krilRules.getPouchRunes().get(1).getName());
    assertEquals("Death", krilRules.getPouchRunes().get(2).getName());
    assertTrue(
        krilRules.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Divine ranging potion")
                        && item.getSlotCount() == 3));
    assertTrue(
        krilRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Poison protection")));
    assertTrue(
        krilRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Zamorak protection switch")));
    assertTrue(
        krilRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Blood Barrage weapon switch")));
    assertTrue(
        krilRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Magic body switch")));
    assertEquals(
        "necklace of anguish",
        SlayerEquipmentAuditCatalog.priorities(
                "K'ril Tsutsaroth",
                kril,
                net.runelite.api.EquipmentInventorySlot.AMULET,
                "scorching bow")
            .get(0));
    assertEquals(
        "pegasian boots",
        SlayerEquipmentAuditCatalog.priorities(
                "K'ril Tsutsaroth",
                kril,
                net.runelite.api.EquipmentInventorySlot.BOOTS,
                "scorching bow")
            .get(1));

    final TaskStrategy meleeKril =
        SlayerTaskStrategyCatalog.resolve(
            "K'ril Tsutsaroth",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.PREFER_MELEE,
            "God Wars Dungeon",
            false);
    final MethodRules meleeKrilRules =
        SlayerMethodRuleCatalog.resolve("K'ril Tsutsaroth", "God Wars Dungeon", meleeKril);
    assertEquals("emberlight", meleeKril.getWeaponPriorities().get(0));
    assertTrue(
        meleeKrilRules.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Special attack weapon")
                        && item.getAlternatives().get(0).equals("saradomin godsword")));
    assertEquals(
        "masori body f",
        SlayerEquipmentAuditCatalog.priorities(
                "K'ril Tsutsaroth",
                meleeKril,
                net.runelite.api.EquipmentInventorySlot.BODY,
                "emberlight")
            .get(0));
    assertEquals(
        "avernic defender",
        SlayerEquipmentAuditCatalog.priorities(
                "K'ril Tsutsaroth",
                meleeKril,
                net.runelite.api.EquipmentInventorySlot.SHIELD,
                "emberlight")
            .get(0));

    final TaskStrategy skotizo =
        SlayerTaskStrategyCatalog.resolve(
            "Skotizo",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Skotizo's Lair",
            false);
    final MethodRules skotizoRules =
        SlayerMethodRuleCatalog.resolve("Skotizo", "Skotizo's Lair", skotizo);
    assertTrue(
        skotizoRules.getRequiredItems().stream()
            .anyMatch(item -> item.getDisplayName().equals("Dark totem")));

    final TaskStrategy tormented =
        SlayerTaskStrategyCatalog.resolve(
            "Tormented demons",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Ancient Guthixian Temple",
            false);
    final MethodRules tormentedRules =
        SlayerMethodRuleCatalog.resolve("Tormented demons", "Ancient Guthixian Temple", tormented);
    assertEquals(7, tormentedRules.resolveRestoreSlots(tormented));
    assertEquals(5, tormentedRules.resolveFoodSlots(tormented, tormented.getFood()));
    assertEquals(3, tormentedRules.getLoot());
    assertEquals("emberlight", tormented.getWeaponPriorities().get(0));
    assertTrue(tormented.getWeaponPriorities().contains("abyssal bludgeon"));
    assertTrue(tormented.getWeaponPriorities().contains("arkan blade"));
    assertEquals(MethodRules.Spellbook.ARCEUUS, tormentedRules.getSpellbook());
    assertTrue(tormentedRules.requiresBookOfDead());
    assertTrue(tormentedRules.requiresRunePouch());
    assertTrue(
        tormentedRules.getPouchRunes().stream()
            .anyMatch(rune -> rune.getName().equals("Fire") && rune.getMinimumQuantity() >= 1000));
    assertTrue(
        tormentedRules.getPouchRunes().stream()
            .anyMatch(rune -> rune.getName().equals("Cosmic") && rune.getMinimumQuantity() >= 500));
    assertTrue(
        tormentedRules.getPouchRunes().stream()
            .anyMatch(rune -> rune.getName().equals("Soul") && rune.getMinimumQuantity() >= 500));
    for (final String required :
        Arrays.asList(
            "Secondary weapon switch",
            "Secondary body switch",
            "Secondary legs switch",
            "Magic off-hand switch",
            "Shield-down crush weapon",
            "Special attack weapon")) {
      assertTrue(
          tormentedRules.getRequiredItems().stream()
              .anyMatch(item -> item.getDisplayName().equals(required)));
    }
    assertTrue(
        tormentedRules.getRequiredItems().stream()
            .filter(item -> item.getDisplayName().equals("Secondary weapon switch"))
            .anyMatch(
                item ->
                    item.getAlternatives().indexOf("purging staff")
                            > item.getAlternatives().indexOf("twisted bow")
                        && item.getAlternatives().indexOf("purging staff")
                            < item.getAlternatives().indexOf("toxic blowpipe")));
    assertTrue(
        tormentedRules.getRequiredItems().stream()
            .filter(item -> item.getDisplayName().equals("Shield-down crush weapon"))
            .noneMatch(item -> item.getAlternatives().contains("saradomin godsword")));
    assertTrue(
        tormentedRules.getRequiredItems().stream()
            .filter(item -> item.getDisplayName().equals("Shield-down crush weapon"))
            .anyMatch(
                item ->
                    item.getAlternatives().indexOf("dragon 2h sword")
                        < item.getAlternatives().indexOf("tzhaar ket om")));
    assertEquals(
        "avernic defender",
        SlayerEquipmentAuditCatalog.priorities(
                "Tormented demons",
                tormented,
                net.runelite.api.EquipmentInventorySlot.SHIELD,
                "emberlight")
            .get(0));
    assertTrue(
        SlayerEquipmentAuditCatalog.priorities(
                "Tormented demons",
                tormented,
                net.runelite.api.EquipmentInventorySlot.BODY,
                "emberlight")
            .contains("inquisitor s hauberk"));
    assertTrue(
        SlayerEquipmentAuditCatalog.priorities(
                "Tormented demons",
                tormented,
                net.runelite.api.EquipmentInventorySlot.BOOTS,
                "emberlight")
            .contains("climbing boots"));
  }

  @Test
  public void cerberusUsesAValidGreaterThrallRunePouch() {
    final TaskStrategy cerberus =
        SlayerTaskStrategyCatalog.resolve(
            "Cerberus",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Cerberus' Lair",
            false);
    final MethodRules rules =
        SlayerMethodRuleCatalog.resolve("Cerberus", "Cerberus' Lair", cerberus);

    assertEquals(MethodRules.Spellbook.ARCEUUS, rules.getSpellbook());
    assertEquals("Resurrect Greater Ghost", rules.getPrimarySpell());
    assertTrue(rules.usesThralls());
    assertFalse(rules.usesDeathCharge());
    assertFalse(rules.usesWardOfArceuus());
    assertTrue(rules.requiresRunePouch());
    assertTrue(rules.requiresBookOfDead());
    assertEquals(
        Arrays.asList("Fire", "Blood", "Cosmic"),
        rules.getPouchRunes().stream()
            .map(MethodRules.RuneRequirement::getName)
            .collect(java.util.stream.Collectors.toList()));
  }

  @Test
  public void aviansiesAndKreeCarryRequiredGodWarsProtection() {
    final TaskStrategy aviansies =
        SlayerTaskStrategyCatalog.resolve(
            "Aviansies",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "God Wars Dungeon",
            false);
    final MethodRules regular =
        SlayerMethodRuleCatalog.resolve("Aviansies", "God Wars Dungeon", aviansies);
    for (final String item : Arrays.asList("Armadyl protection", "Zamorak protection")) {
      assertTrue(
          regular.getRequiredItems().stream()
              .anyMatch(required -> required.getDisplayName().equals(item)));
      assertTrue(
          bossInventoryRules("Kree'arra").getRequiredItems().stream()
              .anyMatch(required -> required.getDisplayName().equals(item)));
    }
  }

  @Test
  public void bryophytaAndDangerousBossTripsHaveCompleteSupplyPolicies() {
    final MethodRules bryophyta = bossInventoryRules("Bryophyta");
    assertTrue(bryophyta.fillsRemainingWithFood());
    assertTrue(
        bryophyta.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Growthling tool")
                        && item.getAlternatives().contains("dragon axe")
                        && !item.getAlternatives().contains("zombie axe")));

    for (final String boss :
        Arrays.asList(
            "Abyssal Sire",
            "Amoxliatl",
            "Cerberus",
            "Grotesque Guardians",
            "K'ril Tsutsaroth",
            "Kalphite Queen",
            "Kree'arra",
            "Skotizo",
            "Vorkath",
            "Royal Titans",
            "Duke Sucellus",
            "General Graardor",
            "Maggot King",
            "Phantom Muspah",
            "Leviathan",
            "Whisperer",
            "Vardorvis",
            "Commander Zilyana",
            "Zulrah")) {
      final MethodRules rules = bossInventoryRules(boss);
      assertTrue(
          boss + " has no remaining-slot supply policy",
          rules.fillsRemainingWithFood() || rules.fillsRemainingWithRestore());
      assertEquals(boss + " unexpectedly reserves empty cells", 0, rules.getLoot());
    }
  }

  @Test
  public void jadAndVardorvisUseTheirReviewedTripInventories() {
    final MethodRules jad = bossInventoryRules("TzTok-Jad");
    assertTrue(jad.fillsRemainingWithRestore());
    assertEquals(0, jad.getLoot());
    assertTrue(
        jad.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Saradomin brew") && item.getSlotCount() == 8));
    assertTrue(
        jad.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Super restore") && item.getSlotCount() == 15));
    assertTrue(
        jad.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals(PotionPolicy.EXTENDED_STAMINA_DISPLAY)
                        && item.getSlotCount() == 2));

    final MethodRules vardorvis = bossInventoryRules("Vardorvis");
    assertTrue(vardorvis.fillsRemainingWithFood());
    assertEquals(3, vardorvis.resolveRestoreSlots(null));
    for (final String required :
        Arrays.asList(
            "Divine super combat potion", "Special attack weapon", "Fast Stranglewood return")) {
      assertTrue(
          vardorvis.getRequiredItems().stream()
              .anyMatch(item -> item.getDisplayName().equals(required)));
    }
  }

  @Test
  public void infernoKeepsReturnTeleportsOutAndCarriesEnoughBrews() {
    assertFalse(SlayerPlusPlugin.shouldAddPersistentReturnTeleports(true));
    assertTrue(SlayerPlusPlugin.shouldAddPersistentReturnTeleports(false));

    final TaskStrategy fast =
        TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "Fast on-task Inferno repeat")
            .costPolicy(TaskStrategy.CostPolicy.MAX_DPS)
            .build();
    final TaskStrategy efficient =
        TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "Efficient on-task Inferno repeat")
            .costPolicy(TaskStrategy.CostPolicy.EFFICIENT)
            .build();
    final TaskStrategy safety =
        TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "Completion first")
            .costPolicy(TaskStrategy.CostPolicy.LOW_RISK)
            .build();

    assertInfernoSupplies(fast, 7, 9);
    assertInfernoSupplies(efficient, 8, 8);
    assertInfernoSupplies(safety, 8, 7);
  }

  @Test
  public void chinchompasAreLimitedToTheExplicitlyReviewedKreeMethod() {
    final TaskStrategy kree =
        SlayerTaskStrategyCatalog.resolve(
            "Kree'arra",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "God Wars Dungeon",
            false);
    assertTrue(kree.hasTag(TaskStrategy.MethodTag.CHINNING));
    final TaskStrategy bats =
        SlayerTaskStrategyCatalog.resolve(
            "Bats",
            Preference.Playstyle.FAST_XP,
            Preference.Cannon.NEVER,
            Preference.Burst.NEVER,
            Preference.CombatStyle.AUTOMATIC,
            "Standard Slayer location",
            false);
    assertFalse(bats.hasTag(TaskStrategy.MethodTag.CHINNING));
  }

  @Test
  public void krystiliaTasksUseLowRiskBlightedSuppliesAndAnEscape() {
    for (final String task :
        Arrays.asList(
            "Abyssal demons",
            "Ankou",
            "Aviansies",
            "Black dragons",
            "Bloodveld",
            "Greater demons",
            "Hellhounds",
            "Nechryael")) {
      final String location =
          task.equals("Aviansies") ? "Wilderness God Wars Dungeon" : "Wilderness Slayer Cave";
      final TaskStrategy strategy =
          SlayerTaskStrategyCatalog.resolve(
              task,
              Preference.Playstyle.FAST_XP,
              Preference.Cannon.ALLOW,
              Preference.Burst.ALLOW,
              Preference.CombatStyle.AUTOMATIC,
              location,
              true);
      assertTrue(task, strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS));
      assertEquals(task, TaskStrategy.CostPolicy.LOW_RISK, strategy.getCostPolicy());
      final MethodRules rules = SlayerMethodRuleCatalog.resolve(task, location, strategy);
      assertEquals(task, "blighted super restore", rules.getPrimaryRestoreFamily());
      assertTrue(
          task,
          rules.getRequiredItems().stream()
              .anyMatch(item -> item.getDisplayName().equals("Looting bag")));
      assertTrue(
          task,
          rules.getRequiredItems().stream()
              .anyMatch(item -> item.getDisplayName().equals("Level-30 Wilderness escape")));
    }
  }

  @Test
  public void whileGuthixSleepsChangesDuradelDisplayToKuradalOnly() {
    assertEquals("Duradel", SlayerPlusPlugin.masterDisplayNameForTest(5, false, false));
    assertEquals("Kuradal", SlayerPlusPlugin.masterDisplayNameForTest(5, true, false));
    assertEquals(MasterRoutes.getName(8), SlayerPlusPlugin.masterDisplayNameForTest(8, true, true));
  }

  @Test
  public void monkeyMadness2ChangesNieveDisplayToSteveOnly() {
    assertEquals("Nieve", SlayerPlusPlugin.masterDisplayNameForTest(6, false, false));
    assertEquals("Steve", SlayerPlusPlugin.masterDisplayNameForTest(6, false, true));
    assertEquals(
        MasterRoutes.getName(8), SlayerPlusPlugin.masterDisplayNameForTest(8, false, true));
  }

  @Test
  public void dagannothRexVariantResolvesToMagicStrategyAtWaterbirthDungeon() {
    assertTrue(
        VariantCatalog.getAvailableVariants("Dagannoth").contains(TaskVariant.DAGANNOTH_REX));

    final VariantCatalog.ResolvedTarget resolved =
        VariantCatalog.resolve(
            "Dagannoth", TaskVariant.DAGANNOTH_REX, null, new SlayerPlusConfig() {});

    assertEquals("Dagannoth Rex", resolved.getTaskName());
    assertEquals("Waterbirth Island Dungeon", resolved.getLocation());
    assertEquals(TaskStrategy.CombatStyle.MAGIC, resolved.getStrategy().getStyle());

    final MethodRules rules =
        SlayerMethodRuleCatalog.resolve(
            "Dagannoth Rex", "Waterbirth Island Dungeon", resolved.getStrategy());
    assertNotNull(rules);
  }

  private static void assertDirectlyAbove(
      final List<BankTagLayout.InventoryPlacement> placements,
      final String top,
      final String legs) {
    final int topSlot = slotFor(placements, top);
    final int legsSlot = slotFor(placements, legs);
    assertEquals(topSlot % 4, legsSlot % 4);
    assertEquals(topSlot + 4, legsSlot);
  }

  private static boolean containsLabelText(final Component component, final String expected) {
    if (component instanceof JLabel) {
      final JLabel label = (JLabel) component;
      final String text = label.getText();
      final String tooltip = label.getToolTipText();
      if ((text != null && text.contains(expected))
          || (tooltip != null && tooltip.contains(expected))) {
        return true;
      }
    }
    if (component instanceof Container) {
      for (final Component child : ((Container) component).getComponents()) {
        if (containsLabelText(child, expected)) {
          return true;
        }
      }
    }
    return false;
  }

  private static int slotFor(
      final List<BankTagLayout.InventoryPlacement> placements, final String name) {
    return placements.stream()
        .filter(placement -> name.equals(placement.getItem().getDisplayName()))
        .findFirst()
        .orElseThrow(AssertionError::new)
        .getSlotIndex();
  }

  private static List<String> namesBySlot(final List<BankTagLayout.InventoryPlacement> placements) {
    return placements.stream()
        .sorted(Comparator.comparingInt(BankTagLayout.InventoryPlacement::getSlotIndex))
        .map(placement -> placement.getItem().getDisplayName())
        .collect(java.util.stream.Collectors.toList());
  }

  private static KitItem item(final String name) {
    return new KitItem(name, 1, 1, KitItem.Status.BANK);
  }

  private static MethodRules bossInventoryRules(final String boss) {
    final MethodRules.Builder builder = MethodRules.builder();
    assertTrue(SlayerBossInventoryCatalog.apply(boss, builder, null));
    return builder.build();
  }

  private static void assertInfernoSupplies(
      final TaskStrategy strategy, final int expectedBrews, final int expectedRestores) {
    final MethodRules.Builder builder = MethodRules.builder();
    assertTrue(SlayerBossInventoryCatalog.apply("TzKal-Zuk", builder, strategy));
    final MethodRules rules = builder.build();
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Saradomin brew")
                        && item.getSlotCount() == expectedBrews));
    assertTrue(
        rules.getRequiredItems().stream()
            .anyMatch(
                item ->
                    item.getDisplayName().equals("Super restore")
                        && item.getSlotCount() == expectedRestores));
    assertFalse(
        rules.getRequiredItems().stream()
            .anyMatch(
                item -> {
                  final String name = item.getDisplayName().toLowerCase(java.util.Locale.ENGLISH);
                  return name.contains("max cape") || name.contains("karamja gloves");
                }));
  }

  private static KitItem switchItem(final String name, final KitItem.SwitchStyle style) {
    return item(name).asEquipmentSwitch(style);
  }

  private static KitItem groupItem(final String name, final MethodRules.InventoryGroup group) {
    return item(name).withInventoryGroup(group);
  }
}
