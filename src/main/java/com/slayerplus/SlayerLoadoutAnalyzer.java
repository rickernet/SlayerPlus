package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemVariationMapping;

public final class SlayerLoadoutAnalyzer {
  private static final Map<String, List<String>> LOADOUT_LISTS =
      loadLists("slayer-loadout-lists.tsv");
  private static final Map<String, String[]> LOADOUT_ARRAYS =
      loadArrays("slayer-loadout-arrays.tsv");
  private static final int EXTRA_QUIVER_AMMO_SLOT = 14;
  private static final String[] STRONGEST_STANDARD_ARROW_PRIORITY = array("a026");
  private static final String[] STRONGEST_NON_DRAGON_ARROW_PRIORITY = array("a025");
  private static final String[] EFFICIENT_REGULAR_ARROW_PRIORITY = array("a024");
  private static final String[] EFFICIENT_BOSS_ARROW_PRIORITY = array("a023");
  private static final String[] INFERNO_ARROW_PRIORITY = array("a022");
  private final ItemManager itemManager;
  private Map<Integer, Integer> cachedBankSnapshotSource = Collections.emptyMap();
  private List<OwnedItem> cachedBankOwnedItems = Collections.emptyList();
  private String slayerHelmetPreference = HelmetPreference.COMBAT_ACHIEVEMENT;
  private int randomSlayerHelmetItemId = -1;
  private int randomSlayerHelmetPoolSignature;
  private boolean randomSlayerHelmetRerollPending;
  private boolean desertEliteDiaryComplete;
  private SlayerAchievementDiarySnapshot achievementDiaries =
      SlayerAchievementDiarySnapshot.empty();

  public SlayerLoadoutAnalyzer(ItemManager itemManager) {
    this.itemManager = itemManager;
  }

  public void invalidateBankSnapshot() {
    cachedBankSnapshotSource = Collections.emptyMap();
    cachedBankOwnedItems = Collections.emptyList();
  }

  public void setHelmetPreference(String preference) {
    String normalized = HelmetPreference.normalize(preference);
    if (!normalized.equals(slayerHelmetPreference)) {
      slayerHelmetPreference = normalized;
      randomSlayerHelmetItemId = -1;
      randomSlayerHelmetPoolSignature = 0;
      randomSlayerHelmetRerollPending = false;
    }
  }

  public void setDesertEliteDiaryComplete(boolean complete) {
    desertEliteDiaryComplete = complete;
  }

  public void setAchievementDiaries(SlayerAchievementDiarySnapshot snapshot) {
    achievementDiaries = snapshot == null ? SlayerAchievementDiarySnapshot.empty() : snapshot;
  }

  public boolean rerollRandomSlayerHelmet() {
    if (!HelmetPreference.isRandom(slayerHelmetPreference)) {
      return false;
    }
    randomSlayerHelmetRerollPending = true;
    return true;
  }

  public List<String> ownedSlayerHelmetNames(
      ItemContainer inventory, ItemContainer equipment, Map<Integer, Integer> cachedBankItems) {
    List<OwnedItem> owned = new ArrayList<>();
    owned.addAll(snapshotOwned(equipment, KitItem.Status.EQUIPPED, true));
    owned.addAll(snapshotOwned(inventory, KitItem.Status.INVENTORY, false));
    owned.addAll(snapshotBank(cachedBankItems));
    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (OwnedItem item : owned) {
      if (isSlayerHelmet(item)) {
        names.add(item.displayName);
      }
    }
    return new ArrayList<>(names);
  }

  public Set<Integer> ownedSlayerHelmetItemIds(
      ItemContainer inventory, ItemContainer equipment, Map<Integer, Integer> cachedBankItems) {
    List<OwnedItem> owned = new ArrayList<>();
    owned.addAll(snapshotOwned(equipment, KitItem.Status.EQUIPPED, true));
    owned.addAll(snapshotOwned(inventory, KitItem.Status.INVENTORY, false));
    owned.addAll(snapshotBank(cachedBankItems));
    Set<Integer> itemIds = new LinkedHashSet<>();
    for (OwnedItem item : owned) {
      if (isSlayerHelmet(item)) {
        itemIds.add(item.itemId);
      }
    }
    return itemIds;
  }

  public List<String> slayerHelmetNamesForItemIds(Iterable<Integer> itemIds) {
    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    if (itemIds == null || itemManager == null) {
      return new ArrayList<>(names);
    }
    for (Integer itemId : itemIds) {
      if (itemId == null || itemId <= 0) {
        continue;
      }
      String displayName = itemManager.getItemComposition(itemId).getName();
      if (normalize(displayName).contains("slayer helmet")) {
        names.add(displayName);
      }
    }
    return new ArrayList<>(names);
  }

  public Recommendation resolveOwnedAutomaticRecommendation(
      String taskName,
      Recommendation recommendation,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> cachedBankItems,
      boolean bankScanned,
      TaskVariant taskVariant,
      SlayerPlusConfig config) {
    if (recommendation == null
        || !recommendation.hasTaskStrategy()
        || config == null
        || config.combatStylePreference() != Preference.CombatStyle.AUTOMATIC
        || !bankScanned) {
      return recommendation;
    }
    VariantCatalog.ResolvedTarget effectiveTarget =
        VariantCatalog.resolve(taskName, taskVariant, recommendation, config);
    if (effectiveTarget == null
        || !effectiveTarget.isValid()
        || effectiveTarget.getStrategy() == null) {
      return recommendation;
    }
    String effectiveTask = effectiveTarget.getTaskName();
    String effectiveLocation = effectiveTarget.getLocation();
    TaskStrategy effectiveStrategy = effectiveTarget.getStrategy();
    List<OwnedItem> allOwned = new ArrayList<>();
    allOwned.addAll(snapshotOwned(equipment, KitItem.Status.EQUIPPED, true));
    allOwned.addAll(snapshotOwned(inventory, KitItem.Status.INVENTORY, false));
    allOwned.addAll(snapshotBank(cachedBankItems));
    boolean wilderness =
        isWildernessLoadout(
            normalize(effectiveLocation), normalize(effectiveTarget.getRestriction()));
    List<OwnedStrategyCandidate> candidates = new ArrayList<>();
    addOwnedStrategyCandidate(
        candidates, effectiveStrategy, allOwned, effectiveTask, config.playstyle(), true);
    boolean automaticStyleLocked =
        effectiveStrategy.hasTag(TaskStrategy.MethodTag.AUTOMATIC_STYLE_LOCKED);
    if (!automaticStyleLocked) {
      for (Preference.CombatStyle preference :
          new Preference.CombatStyle[] {
            Preference.CombatStyle.PREFER_MELEE,
            Preference.CombatStyle.PREFER_RANGED,
            Preference.CombatStyle.PREFER_MAGIC
          }) {
        TaskStrategy candidate =
            SlayerTaskStrategyCatalog.resolve(
                effectiveTask,
                config.playstyle(),
                config.cannonPreference(),
                config.burstPreference(),
                preference,
                effectiveLocation,
                wilderness);
        addOwnedStrategyCandidate(
            candidates, candidate, allOwned, effectiveTask, config.playstyle(), false);
      }
    }
    OwnedStrategyCandidate best = null;
    for (OwnedStrategyCandidate candidate : candidates) {
      if (best == null || candidate.score > best.score) {
        best = candidate;
      }
    }
    if (best == null) {
      return recommendation;
    }
    String ownedReason =
        "Automatic compared the reviewed viable combat styles against "
            + "your real bank, inventory, and equipped items. "
            + best.weapon.displayName
            + " produced the strongest owned setup for this method. ";
    TaskStrategy selectedStrategy =
        effectiveStrategy.hasTag(TaskStrategy.MethodTag.TURAEL_POINT_BOOST)
            ? best.strategy.withAdditionalTags(TaskStrategy.MethodTag.TURAEL_POINT_BOOST)
            : best.strategy;
    String resolvedMethod =
        SlayerMethodRuleCatalog.resolve(effectiveTask, effectiveLocation, selectedStrategy)
            .getTaskMethod();
    return new Recommendation(
        effectiveLocation,
        resolvedMethod,
        ownedReason + best.strategy.getRationale(),
        effectiveTarget.getTravel(),
        effectiveTarget.getCannon(),
        recommendation.getRequirements(),
        effectiveTarget.getRestriction(),
        recommendation.getDestination(),
        selectedStrategy);
  }

  private static void addOwnedStrategyCandidate(
      List<OwnedStrategyCandidate> candidates,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      String encounterName,
      Preference.Playstyle playstyle,
      boolean originalAutomatic) {
    if (strategy == null || !strategy.isReviewed()) {
      return;
    }
    for (OwnedStrategyCandidate existing : candidates) {
      if (sameStrategy(existing.strategy, strategy)) {
        return;
      }
    }
    List<String> priorities =
        strategy.getWeaponPrioritiesForPolicy().isEmpty()
            ? strategy.getWeaponPriorities()
            : strategy.getWeaponPrioritiesForPolicy();
    OwnedItem weapon =
        findPreferredEquipmentAllowed(
            allOwned, EquipmentInventorySlot.WEAPON, false, priorities, strategy);
    if (weapon == null) {
      return;
    }
    int rank = matchingPriorityIndex(weapon, priorities);
    if (rank < 0 || !hasRequiredRangedAmmo(strategy, weapon, allOwned)) {
      return;
    }
    int score = Math.max(10, 125 - rank * 18);
    score += ownedCoreEquipmentCoverage(strategy, allOwned) * 12;
    if (normalize(encounterName).equals("vorkath")
        && !hasCompleteVorkathCore(strategy, weapon, allOwned)) {
      return;
    }
    if (originalAutomatic) {
      score += 4;
    }
    if (strategy.hasTag(TaskStrategy.MethodTag.CANNON)) {
      score += 28;
    }
    if (strategy.hasTag(TaskStrategy.MethodTag.MULTI_COMBAT)) {
      score += 8;
    }
    if (strategy.hasTag(TaskStrategy.MethodTag.VENATOR)
        && weapon.matchesName("venator bow")) {
      score += 42;
    }
    if (strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE)) {
      score += 36;
    }
    Preference.Playstyle resolvedPlaystyle =
        playstyle == null ? Preference.Playstyle.FAST_XP : playstyle;
    switch (resolvedPlaystyle) {
      case PROFIT:
        if (strategy.getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT) {
          score += 18;
        }
        score -= operatingCostPenalty(weapon);
        break;
      case FAST_XP:
      default:
        if (strategy.getCostPolicy() == TaskStrategy.CostPolicy.MAX_DPS) {
          score += 14;
        }
        if (strategy.getArmourFocus() == TaskStrategy.ArmourFocus.DAMAGE) {
          score += 8;
        }
        break;
    }
    candidates.add(new OwnedStrategyCandidate(strategy, weapon, score));
  }

  private static boolean sameStrategy(TaskStrategy left, TaskStrategy right) {
    return left == right
        || (left != null
            && right != null
            && left.getCombatStyle() == right.getCombatStyle()
            && normalize(left.getMethod()).equals(normalize(right.getMethod())));
  }

  private static int ownedCoreEquipmentCoverage(
      TaskStrategy strategy, List<OwnedItem> allOwned) {
    CombatStyle style = combatStyle(strategy);
    int coverage = 0;
    for (EquipmentInventorySlot slot :
        new EquipmentInventorySlot[] {
          EquipmentInventorySlot.HEAD,
          EquipmentInventorySlot.CAPE,
          EquipmentInventorySlot.AMULET,
          EquipmentInventorySlot.BODY,
          EquipmentInventorySlot.LEGS,
          EquipmentInventorySlot.GLOVES,
          EquipmentInventorySlot.BOOTS,
          EquipmentInventorySlot.RING
        }) {
      if (findBestEquipment(allOwned, slot, strategy, style, false) != null) {
        coverage++;
      }
    }
    return coverage;
  }

  private static boolean hasCompleteVorkathCore(
      TaskStrategy strategy, OwnedItem weapon, List<OwnedItem> allOwned) {
    if (strategy == null || weapon == null) {
      return false;
    }
    CombatStyle style = combatStyle(strategy);
    boolean ranged = style == CombatStyle.RANGED;
    OwnedItem slayerHead = findPreferredVorkathSlayerHead(allOwned, ranged);
    boolean slayerPackage =
        slayerHead != null
            && containsAnyOwned(
                allOwned,
                ranged ? "necklace of anguish" : "amulet of rancour",
                ranged ? "amulet of fury" : "amulet of torture",
                "amulet of fury");
    boolean salvePackage =
        containsAnyOwned(
            allOwned,
            ranged ? "salve amulet ei" : "salve amulet e",
            ranged ? "salve amulet i" : "salve amulet ei");
    boolean head =
        findPreferredEquipment(
                allOwned, EquipmentInventorySlot.HEAD, false, ranged ? list("l001") : list("l002"))
            != null;
    boolean body =
        findPreferredEquipment(
                allOwned, EquipmentInventorySlot.BODY, false, ranged ? list("l003") : list("l004"))
            != null;
    boolean legs =
        findPreferredEquipment(
                allOwned, EquipmentInventorySlot.LEGS, false, ranged ? list("l005") : list("l006"))
            != null;
    boolean offHand =
        weapon.isTwoHanded()
            || findPreferredEquipment(
                    allOwned,
                    EquipmentInventorySlot.SHIELD,
                    false,
                    ranged ? list("l007") : list("l008"))
                != null;
    return (slayerPackage || salvePackage) && head && body && legs && offHand;
  }

  private static OwnedItem findPreferredVorkathSlayerHead(
      List<OwnedItem> allOwned, boolean ranged) {
    return findPreferredEquipment(
        allOwned, EquipmentInventorySlot.HEAD, false, ranged ? list("l009") : list("l010"));
  }

  private static int matchingPriorityIndex(OwnedItem weapon, List<String> priorities) {
    for (int index = 0; index < priorities.size(); index++) {
      String fragment = normalize(priorities.get(index));
      if (!fragment.isEmpty() && weapon.matchesName(fragment)) {
        return index;
      }
    }
    return -1;
  }

  private static boolean hasRequiredRangedAmmo(
      TaskStrategy strategy, OwnedItem weapon, List<OwnedItem> allOwned) {
    if (strategy.getCombatStyle() != TaskStrategy.CombatStyle.RANGED) {
      return true;
    }
    if (weapon.matchesName("blowpipe")
        || weapon.matchesName("bow of faerdhinen")
        || weapon.matchesName("crystal bow")
        || weapon.matchesName("webweaver bow")
        || weapon.matchesName("craw s bow")
        || weapon.matchesName("chinchompa")
        || weapon.matchesName(" dart")
        || weapon.normalizedName.endsWith("dart")
        || weapon.matchesName(" knife")
        || weapon.normalizedName.endsWith("knife")) {
      return true;
    }
    if (weapon.matchesName("ballista")) {
      return containsAnyOwned(allOwned, "dragon javelin", "amethyst javelin", "rune javelin");
    }
    if (weapon.matchesName("atlatl")) {
      return containsAnyOwned(allOwned, "atlatl dart");
    }
    if (weapon.matchesName("hunters sunlight crossbow")) {
      return containsAnyOwned(allOwned, "moonlight antler bolts", "sunlight antler bolts");
    }
    if (weapon.matchesName("crossbow")) {
      return containsAnyOwned(
          allOwned,
          "dragonstone dragon bolts e",
          "dragonstone bolts e",
          "ruby dragon bolts e",
          "diamond dragon bolts e",
          "dragon bolts",
          "amethyst broad bolts",
          "runite bolts",
          "broad bolts");
    }
    return containsAnyOwned(
        allOwned, "dragon arrow", "amethyst arrow", "rune arrow", "broad arrow");
  }

  private static boolean containsAnyOwned(List<OwnedItem> allOwned, String... fragments) {
    for (OwnedItem item : allOwned) {
      for (String fragment : fragments) {
        if (item.matchesName(fragment)) {
          return true;
        }
      }
    }
    return false;
  }

  private static Map<Integer, Integer> ownedItemQuantities(List<OwnedItem> allOwned) {
    Map<Integer, Integer> quantities = new LinkedHashMap<>();
    if (allOwned == null) {
      return quantities;
    }
    for (OwnedItem item : allOwned) {
      if (item != null && item.itemId > 0 && item.quantity > 0) {
        quantities.merge(item.itemId, item.quantity, Integer::sum);
      }
    }
    return quantities;
  }

  private static int operatingCostPenalty(OwnedItem weapon) {
    if (weapon.matchesName("scythe of vitur")) {
      return 60;
    }
    if (weapon.matchesName("toxic blowpipe")
        || weapon.matchesName("sanguinesti staff")
        || weapon.matchesName("tumeken s shadow")) {
      return 34;
    }
    if (weapon.matchesName("arclight")
        || weapon.matchesName("crystal bow")
        || weapon.matchesName("bow of faerdhinen")) {
      return 12;
    }
    return 0;
  }

  public KitPlan analyze(
      String taskName,
      Recommendation recommendation,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> cachedBankItems,
      boolean bankScanned) {
    return analyze(
        taskName,
        recommendation,
        inventory,
        equipment,
        cachedBankItems,
        bankScanned,
        TaskVariant.STANDARD_TASK,
        null,
        -1);
  }

  public KitPlan analyze(
      String taskName,
      Recommendation recommendation,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> cachedBankItems,
      boolean bankScanned,
      TaskVariant taskVariant,
      SlayerPlusConfig config) {
    return analyze(
        taskName,
        recommendation,
        inventory,
        equipment,
        cachedBankItems,
        bankScanned,
        taskVariant,
        config,
        -1);
  }

  public KitPlan analyze(
      String taskName,
      Recommendation recommendation,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> cachedBankItems,
      boolean bankScanned,
      TaskVariant taskVariant,
      SlayerPlusConfig config,
      int remainingKills) {
    return analyze(
        taskName,
        recommendation,
        inventory,
        equipment,
        cachedBankItems,
        bankScanned,
        taskVariant,
        config,
        remainingKills,
        -1,
        0);
  }

  public KitPlan analyze(
      String taskName,
      Recommendation recommendation,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> cachedBankItems,
      boolean bankScanned,
      TaskVariant taskVariant,
      SlayerPlusConfig config,
      int remainingKills,
      int extraQuiverAmmoItemId,
      int extraQuiverAmmoQuantity) {
    List<OwnedItem> inventoryItems =
        snapshotOwned(inventory, KitItem.Status.INVENTORY, false);
    List<OwnedItem> equipmentItems =
        snapshotOwned(equipment, KitItem.Status.EQUIPPED, true);
    List<OwnedItem> bankItems = snapshotBank(cachedBankItems);
    List<OwnedItem> allOwned = new ArrayList<>();
    allOwned.addAll(equipmentItems);
    allOwned.addAll(inventoryItems);
    allOwned.addAll(bankItems);
    OwnedItem extraQuiverAmmo =
        snapshotExtraQuiverAmmo(extraQuiverAmmoItemId, extraQuiverAmmoQuantity);
    if (extraQuiverAmmo != null) {
      allOwned.add(extraQuiverAmmo);
    }
    Set<String> inventoryNames = namesOf(inventoryItems);
    Set<String> equipmentNames = namesOf(equipmentItems);
    Set<String> bankNames = namesOf(bankItems);
    VariantCatalog.ResolvedTarget target =
        VariantCatalog.resolve(taskName, taskVariant, recommendation, config);
    String task = normalize(target.getTaskName());
    String location = normalize(target.getLocation());
    String method = target.getStrategy() == null ? "" : normalize(target.getStrategy().getMethod());
    String cannon = normalize(target.getCannon());
    String travel = normalize(target.getTravel());
    String restriction = normalize(target.getRestriction());
    boolean wildernessLoadout = isWildernessLoadout(location, restriction);
    List<OwnedItem> recommendationOwned = filterRecommendationItems(allOwned, wildernessLoadout);
    TaskStrategy strategy = target.getStrategy();
    if (strategy == null || !strategy.isReviewed()) {
      return KitPlan.researchPending(target.getDisplayName());
    }
    CombatStyle style = combatStyle(strategy);
    List<Requirement> requirements =
        requirementsFor(task, location, style, remainingKills, desertEliteDiaryComplete);
    boolean cannonSuggested = isCannonSuggested(cannon);
    int recommendedFoodSlots = strategy.getRecommendedFoodSlots(remainingKills);
    OwnedItem selectedWeapon =
        selectRecommendedWeapon(
            task, strategy, style, requirements, equipmentItems, recommendationOwned);
    List<KitItem> equipmentLayout =
        applyHelmetPreference(
            buildEquipmentLayout(
                task,
                strategy,
                style,
                requirements,
                equipmentItems,
                recommendationOwned,
                bankScanned,
                selectedWeapon),
            allOwned,
            style);
    return new KitPlan(
        buildEquipmentText(strategy, style, requirements),
        buildInventoryText(strategy, style, requirements, cannonSuggested, recommendedFoodSlots),
        buildOwnedText(
            style,
            requirements,
            cannonSuggested,
            inventoryNames,
            equipmentNames,
            bankNames,
            bankScanned),
        buildLayoutTitle(target.getDisplayName(), target.getLocation()),
        equipmentLayout,
        buildInventoryLayout(
            task,
            strategy,
            location,
            travel,
            style,
            requirements,
            cannonSuggested,
            recommendedFoodSlots,
            recommendationOwned,
            bankScanned,
            equipmentLayout),
        buildOptionalLayout(task, strategy, recommendationOwned, selectedWeapon));
  }

  private List<KitItem> applyHelmetPreference(
      List<KitItem> layout, List<OwnedItem> allOwned, CombatStyle style) {
    if (layout == null || layout.isEmpty()) {
      return layout;
    }
    Map<String, OwnedItem> helmetsByName = new LinkedHashMap<>();
    for (OwnedItem item : allOwned) {
      if (isSlayerHelmet(item) && isSlayerHelmetValidForStyle(item, style)) {
        helmetsByName.putIfAbsent(item.normalizedName, item);
      }
    }
    List<OwnedItem> helmets = new ArrayList<>(helmetsByName.values());
    if (helmets.isEmpty()) {
      return layout;
    }
    helmets.sort((left, right) -> Integer.compare(left.itemId, right.itemId));
    OwnedItem selected = null;
    if (HelmetPreference.isCombatAchievement(slayerHelmetPreference)) {
      selected = firstCombatAchievementHelmet(helmets);
    } else if (HelmetPreference.isRandom(slayerHelmetPreference)) {
      int signature = 1;
      for (OwnedItem helmet : helmets) {
        signature = 31 * signature + helmet.itemId;
      }
      if (signature != randomSlayerHelmetPoolSignature) {
        randomSlayerHelmetPoolSignature = signature;
      }
      for (OwnedItem helmet : helmets) {
        if (helmet.itemId == randomSlayerHelmetItemId) {
          selected = helmet;
          break;
        }
      }
      if (selected == null || randomSlayerHelmetRerollPending) {
        List<OwnedItem> eligible = new ArrayList<>(helmets);
        if (randomSlayerHelmetRerollPending && eligible.size() > 1) {
          eligible.removeIf(helmet -> helmet.itemId == randomSlayerHelmetItemId);
        }
        selected = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        randomSlayerHelmetItemId = selected.itemId;
        randomSlayerHelmetRerollPending = false;
      }
    } else {
      for (OwnedItem helmet : helmets) {
        if (helmet.matchesExactDisplayName(slayerHelmetPreference)) {
          selected = helmet;
          break;
        }
      }
    }
    if (selected == null) {
      return layout;
    }
    for (int index = 0; index < layout.size(); index++) {
      KitItem current = layout.get(index);
      String name = normalize(current.getDisplayName());
      if (name.contains("slayer helmet") || name.contains("black mask")) {
        layout.set(index, selected.toLoadoutItem(1));
        break;
      }
    }
    return layout;
  }

  private static OwnedItem firstCombatAchievementHelmet(List<OwnedItem> helmets) {
    OwnedItem selected = null;
    int selectedTier = 0;
    for (OwnedItem helmet : helmets) {
      int tier = combatAchievementHelmetTierForRegression(helmet.displayName);
      if (tier > selectedTier) {
        selected = helmet;
        selectedTier = tier;
      }
    }
    return selected;
  }

  static int combatAchievementHelmetTierForRegression(String itemName) {
    String name = normalize(itemName);
    if (name.startsWith("tzkal slayer helmet")) return 3;
    if (name.startsWith("vampyric slayer helmet")) return 2;
    if (name.startsWith("tztok slayer helmet")) return 1;
    return 0;
  }

  private static boolean isSlayerHelmet(OwnedItem item) {
    return item != null
        && item.normalizedName.contains("slayer helmet")
        && (item.equipmentSlot < 0
            || item.equipmentSlot == EquipmentInventorySlot.HEAD.getSlotIdx());
  }

  private static boolean isSlayerHelmetValidForStyle(OwnedItem item, CombatStyle style) {
    if (item == null || style == CombatStyle.MELEE || style == CombatStyle.FLEXIBLE) {
      return item != null;
    }
    return item.normalizedName.endsWith(" i") || item.normalizedName.contains(" i uncharged");
  }

  public Set<String> snapshotNames(ItemContainer container) {
    if (container == null || itemManager == null) {
      return Collections.emptySet();
    }
    Set<String> names = new HashSet<>();
    for (Item item : container.getItems()) {
      if (!isRealOwnedItem(item, false)) {
        continue;
      }
      try {
        int canonicalId = itemManager.canonicalize(item.getId());
        ItemComposition composition = itemManager.getItemComposition(canonicalId);
        if (composition != null) {
          names.addAll(getMatchNames(canonicalId, composition.getName()));
        }
      } catch (RuntimeException ignored) {
      }
    }
    return names;
  }

  public Map<Integer, Integer> snapshotItems(ItemContainer container) {
    if (container == null || itemManager == null) {
      return Collections.emptyMap();
    }
    Map<Integer, Integer> items = new LinkedHashMap<>();
    for (Item item : container.getItems()) {
      if (!isRealOwnedItem(item, true)) {
        continue;
      }
      int itemId = item.getId();
      int quantity = item.getQuantity();
      items.put(itemId, items.getOrDefault(itemId, 0) + quantity);
    }
    return items;
  }

  private List<OwnedItem> snapshotOwned(
      ItemContainer container, KitItem.Status status, boolean equipmentContainer) {
    if (container == null || itemManager == null) {
      return Collections.emptyList();
    }
    List<OwnedItem> items = new ArrayList<>();
    Item[] containerItems = container.getItems();
    for (int index = 0; index < containerItems.length; index++) {
      Item item = containerItems[index];
      if (!isRealOwnedItem(item, false)) {
        continue;
      }
      int itemId = item.getId();
      String name = getItemName(itemId);
      if (name.isEmpty()) {
        continue;
      }
      items.add(
          new OwnedItem(
              itemId,
              name,
              item.getQuantity(),
              status,
              equipmentContainer ? index : -1,
              getEquipmentStats(itemId),
              getMatchNames(itemId, name)));
    }
    return items;
  }

  private OwnedItem snapshotExtraQuiverAmmo(int itemId, int quantity) {
    if (itemId <= 0 || quantity <= 0 || itemManager == null) {
      return null;
    }
    int normalizedItemId = QuiverAmmo.normalizeSeekingArrowItemId(itemId);
    String name = getItemName(normalizedItemId);
    if (name.isEmpty()) {
      name = QuiverAmmo.seekingArrowMatchName(normalizedItemId);
    }
    if (name.isEmpty()) {
      return null;
    }
    return new OwnedItem(
        normalizedItemId,
        name,
        quantity,
        KitItem.Status.EQUIPPED,
        EXTRA_QUIVER_AMMO_SLOT,
        getEquipmentStats(normalizedItemId),
        getMatchNames(normalizedItemId, name));
  }

  private List<OwnedItem> snapshotBank(Map<Integer, Integer> cachedBankItems) {
    if (cachedBankItems == null || cachedBankItems.isEmpty() || itemManager == null) {
      return Collections.emptyList();
    }
    if (cachedBankSnapshotSource.equals(cachedBankItems)) {
      return cachedBankOwnedItems;
    }
    List<OwnedItem> items = new ArrayList<>();
    for (Map.Entry<Integer, Integer> entry : cachedBankItems.entrySet()) {
      if (entry.getKey() == null
          || entry.getKey() <= 0
          || entry.getValue() == null
          || entry.getValue() <= 0) {
        continue;
      }
      int itemId = entry.getKey();
      String name = getItemName(itemId);
      if (name.isEmpty()) {
        continue;
      }
      items.add(
          new OwnedItem(
              itemId,
              name,
              entry.getValue(),
              KitItem.Status.BANK,
              -1,
              getEquipmentStats(itemId),
              getMatchNames(itemId, name)));
    }
    cachedBankSnapshotSource = Collections.unmodifiableMap(new LinkedHashMap<>(cachedBankItems));
    cachedBankOwnedItems = Collections.unmodifiableList(items);
    return cachedBankOwnedItems;
  }

  private boolean isRealOwnedItem(Item item, boolean requireDefinition) {
    if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
      return false;
    }
    try {
      ItemComposition composition = itemManager.getItemComposition(item.getId());
      return composition == null
          ? !requireDefinition
          : composition.getPlaceholderTemplateId() == -1;
    } catch (RuntimeException ignored) {
      return !requireDefinition;
    }
  }

  private Set<String> getMatchNames(int itemId, String displayName) {
    Set<String> names = new LinkedHashSet<>();
    addMatchName(names, displayName);
    addSeekingQuiverMatchName(names, itemId);
    try {
      int variationBase = ItemVariationMapping.map(itemId);
      addMatchName(names, getItemName(variationBase));
    } catch (RuntimeException ignored) {
    }
    try {
      Collection<ItemMapping> mappings = ItemMapping.map(itemId);
      if (mappings != null) {
        for (ItemMapping mapping : mappings) {
          addMatchName(names, getItemName(mapping.getTradeableItem()));
        }
      }
    } catch (RuntimeException ignored) {
    }
    return Collections.unmodifiableSet(names);
  }

  private static void addSeekingQuiverMatchName(Set<String> names, int itemId) {
    addMatchName(names, QuiverAmmo.seekingArrowMatchName(itemId));
  }

  private static void addMatchName(Set<String> names, String candidate) {
    String normalized = normalize(candidate);
    if (!normalized.isEmpty() && !normalized.equals("null")) {
      names.add(normalized);
    }
  }

  private int safeCanonicalize(int itemId) {
    try {
      return itemManager.canonicalize(itemId);
    } catch (RuntimeException ignored) {
      return itemId;
    }
  }

  private String getItemName(int itemId) {
    try {
      ItemComposition composition = itemManager.getItemComposition(itemId);
      if (composition == null
          || composition.getName() == null
          || composition.getName().equalsIgnoreCase("null")) {
        return "";
      }
      return composition.getName().trim();
    } catch (RuntimeException ignored) {
      return "";
    }
  }

  private ItemEquipmentStats getEquipmentStats(int itemId) {
    try {
      ItemStats stats = itemManager.getItemStats(itemId);
      if (stats == null || !stats.isEquipable()) {
        return null;
      }
      return stats.getEquipment();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static boolean isWildernessLoadout(String location, String restriction) {
    if (restriction.contains("non wilderness")) {
      return false;
    }
    return location.contains("wilderness")
        || restriction.contains("wilderness allowed")
        || restriction.contains("krystilia");
  }

  private static List<OwnedItem> filterRecommendationItems(
      List<OwnedItem> allOwned, boolean wildernessLoadout) {
    if (wildernessLoadout) {
      return allOwned;
    }
    List<OwnedItem> filtered = new ArrayList<>();
    for (OwnedItem item : allOwned) {
      if (!item.normalizedName.contains("blighted")) {
        filtered.add(item);
      }
    }
    return filtered;
  }

  private static Set<String> namesOf(List<OwnedItem> items) {
    Set<String> names = new HashSet<>();
    for (OwnedItem item : items) {
      names.addAll(item.normalizedNames);
    }
    return names;
  }

  private static String buildLayoutTitle(String taskName, String location) {
    String safeTask =
        taskName == null || taskName.trim().isEmpty() ? "Recommended setup" : taskName.trim();
    if (location == null || location.trim().isEmpty()) {
      return safeTask;
    }
    return safeTask + " \u2022 " + location.trim();
  }

  private static OwnedItem selectRecommendedWeapon(
      String taskName,
      TaskStrategy strategy,
      CombatStyle style,
      List<Requirement> requirements,
      List<OwnedItem> equipped,
      List<OwnedItem> allOwned) {
    Requirement weaponRequirement = requirementForSlot(requirements, EquipmentInventorySlot.WEAPON);
    Requirement shieldRequirement = requirementForSlot(requirements, EquipmentInventorySlot.SHIELD);
    String[] weaponPriorities = weaponProgression(taskName, strategy, style).toArray(new String[0]);
    return selectEquipmentOwned(
        EquipmentInventorySlot.WEAPON,
        weaponRequirement,
        strategy,
        style,
        equipped,
        allOwned,
        shieldRequirement != null,
        weaponPriorities);
  }

  private static List<String> weaponProgression(
      String taskName, TaskStrategy strategy, CombatStyle style) {
    List<String> progression = new ArrayList<>();
    if (strategy != null) {
      progression.addAll(strategy.getWeaponPrioritiesForPolicy());
      if (!strategy.isStrictWeaponProfile()) {
        for (String fallback : SlayerEquipmentAuditCatalog.weaponProgression(strategy)) {
          if (!progression.contains(fallback)) {
            progression.add(fallback);
          }
        }
      }
    }
    if (progression.isEmpty()) {
      Collections.addAll(progression, weaponFragments(style));
    }
    progression.removeIf(weapon -> !SlayerTargetFootprintCatalog.allowsWeapon(taskName, weapon));
    return progression;
  }

  static List<String> weaponProgressionForRegression(String taskName, TaskStrategy strategy) {
    return Collections.unmodifiableList(
        weaponProgression(taskName, strategy, combatStyle(strategy)));
  }

  static List<String> weaponProgressionForRegression(TaskStrategy strategy) {
    return weaponProgressionForRegression("", strategy);
  }

  private static List<KitItem> buildEquipmentLayout(
      String task,
      TaskStrategy strategy,
      CombatStyle style,
      List<Requirement> requirements,
      List<OwnedItem> equipped,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      OwnedItem selectedWeapon) {
    if (normalize(task).equals("tzkal zuk")) {
      return buildInfernoEquipmentLayout(
          strategy, style, equipped, allOwned, bankScanned, selectedWeapon);
    }
    if (normalize(task).equals("blue dragon") || normalize(task).equals("blue dragons")) {
      if (style == CombatStyle.MAGIC) {
        return buildReviewedBlueDragonMagicLayout(strategy, allOwned, bankScanned, selectedWeapon);
      }
      return buildReviewedDragonEquipmentLayout(
          false, strategy, style, requirements, equipped, allOwned, bankScanned, selectedWeapon);
    }
    if (normalize(task).equals("vorkath")) {
      return buildReviewedDragonEquipmentLayout(
          true, strategy, style, requirements, equipped, allOwned, bankScanned, selectedWeapon);
    }
    List<KitItem> layout = new ArrayList<>();
    Requirement headRequirement = requirementForSlot(requirements, EquipmentInventorySlot.HEAD);
    Requirement weaponRequirement = requirementForSlot(requirements, EquipmentInventorySlot.WEAPON);
    Requirement shieldRequirement = requirementForSlot(requirements, EquipmentInventorySlot.SHIELD);
    layout.add(
        auditedEquipmentItem(
            task,
            EquipmentInventorySlot.HEAD,
            headRequirement,
            "Slayer helmet / black mask",
            strategy,
            style,
            equipped,
            allOwned,
            bankScanned,
            false,
            selectedWeapon));
    layout.add(
        auditedCapeEquipmentItem(task, strategy, style, allOwned, bankScanned, selectedWeapon));
    layout.add(
        auditedEquipmentItem(
            task,
            EquipmentInventorySlot.AMULET,
            requirementForSlot(requirements, EquipmentInventorySlot.AMULET),
            style == CombatStyle.MAGIC
                ? "Magic amulet"
                : style == CombatStyle.RANGED ? "Ranged amulet" : "Strength amulet",
            strategy,
            style,
            equipped,
            allOwned,
            bankScanned,
            false,
            selectedWeapon));
    layout.add(
        normalize(task).equals("araxxor")
            ? buildAraxxorSwitchAmmoItem(style, selectedWeapon, strategy, allOwned, bankScanned)
            : buildAmmoItem(style, selectedWeapon, strategy, allOwned, bankScanned));
    layout.add(
        toEquipmentItem(
            selectedWeapon,
            weaponRequirement == null
                ? strategy != null
                    ? strategy.getMethod()
                    : style == CombatStyle.MAGIC
                        ? "Magic weapon"
                        : style == CombatStyle.RANGED ? "Ranged weapon" : "Melee weapon"
                : weaponRequirement.displayName,
            bankScanned));
    layout.add(
        auditedEquipmentItem(
            task,
            EquipmentInventorySlot.BODY,
            null,
            style == CombatStyle.MAGIC
                ? "Magic body"
                : style == CombatStyle.RANGED ? "Ranged body" : "Melee body",
            strategy,
            style,
            equipped,
            allOwned,
            bankScanned,
            false,
            selectedWeapon));
    if (selectedWeapon == null) {
      layout.add(
          missingItem("No off-hand selected until a compatible weapon is found", bankScanned));
    } else if (selectedWeapon.isTwoHanded() && shieldRequirement == null) {
      layout.add(
          new KitItem(
              "Two-handed weapon — no off-hand", -1, 1, KitItem.Status.EQUIPPED));
    } else {
      layout.add(
          auditedEquipmentItem(
              task,
              EquipmentInventorySlot.SHIELD,
              shieldRequirement,
              style == CombatStyle.MAGIC
                  ? "Magic off-hand"
                  : style == CombatStyle.RANGED ? "Ranged off-hand" : "Defender / shield",
              strategy,
              style,
              equipped,
              allOwned,
              bankScanned,
              false,
              selectedWeapon));
    }
    layout.add(
        auditedEquipmentItem(
            task,
            EquipmentInventorySlot.LEGS,
            null,
            style == CombatStyle.MAGIC
                ? "Magic legs"
                : style == CombatStyle.RANGED ? "Ranged legs" : "Melee legs",
            strategy,
            style,
            equipped,
            allOwned,
            bankScanned,
            false,
            selectedWeapon));
    layout.add(
        auditedEquipmentItem(
            task,
            EquipmentInventorySlot.GLOVES,
            requirementForSlot(requirements, EquipmentInventorySlot.GLOVES),
            "Combat gloves",
            strategy,
            style,
            equipped,
            allOwned,
            bankScanned,
            false,
            selectedWeapon));
    layout.add(
        auditedEquipmentItem(
            task,
            EquipmentInventorySlot.BOOTS,
            requirementForSlot(requirements, EquipmentInventorySlot.BOOTS),
            style == CombatStyle.MAGIC
                ? "Magic boots"
                : style == CombatStyle.RANGED ? "Ranged boots" : "Melee boots",
            strategy,
            style,
            equipped,
            allOwned,
            bankScanned,
            false,
            selectedWeapon));
    layout.add(
        auditedEquipmentItem(
            task,
            EquipmentInventorySlot.RING,
            null,
            style == CombatStyle.MAGIC
                ? "Magic ring"
                : style == CombatStyle.RANGED ? "Ranged ring" : "Melee ring",
            strategy,
            style,
            equipped,
            allOwned,
            bankScanned,
            false,
            selectedWeapon));
    KitItem extraQuiverAmmo =
        buildDizanaExtraAmmoItem(false, style, selectedWeapon, strategy, allOwned, bankScanned);
    if (extraQuiverAmmo != null) {
      layout.add(extraQuiverAmmo);
    }
    return layout;
  }

  private static List<KitItem> buildReviewedDragonEquipmentLayout(
      boolean vorkath,
      TaskStrategy strategy,
      CombatStyle style,
      List<Requirement> requirements,
      List<OwnedItem> equipped,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      OwnedItem selectedWeapon) {
    List<KitItem> layout = new ArrayList<>();
    String encounterTask = vorkath ? "Vorkath" : "Blue dragons";
    boolean ranged = style == CombatStyle.RANGED;
    boolean completeMasori =
        ranged
            && containsAnyOwned(allOwned, "masori mask")
            && containsAnyOwned(allOwned, "masori body")
            && containsAnyOwned(allOwned, "masori chaps");
    boolean completeEliteVoid =
        vorkath
            && ranged
            && containsAnyOwned(allOwned, "void ranger helm")
            && containsAnyOwned(allOwned, "elite void top")
            && containsAnyOwned(allOwned, "elite void robe")
            && containsAnyOwned(allOwned, "void knight gloves");
    boolean completeRegularVoid =
        vorkath
            && ranged
            && containsAnyOwned(allOwned, "void ranger helm")
            && containsAnyOwned(allOwned, "void knight top")
            && containsAnyOwned(allOwned, "void knight robe")
            && containsAnyOwned(allOwned, "void knight gloves");
    OwnedItem vorkathSlayerHead = vorkath ? findPreferredVorkathSlayerHead(allOwned, ranged) : null;
    boolean useVorkathSlayerHead = vorkathSlayerHead != null;
    boolean useEliteVoid = !useVorkathSlayerHead && !completeMasori && completeEliteVoid;
    boolean useRegularVoid =
        !useVorkathSlayerHead && !completeMasori && !useEliteVoid && completeRegularVoid;
    layout.add(
        toEquipmentItem(
            useVorkathSlayerHead
                ? vorkathSlayerHead
                : findPreferredEquipment(
                    allOwned,
                    EquipmentInventorySlot.HEAD,
                    false,
                    vorkath
                        ? ranged
                            ? useEliteVoid || useRegularVoid ? list("l011") : list("l012")
                            : list("l002")
                        : ranged ? list("l013") : list("l010")),
            vorkath ? "Slayer helmet / reviewed Vorkath helm" : "Imbued Slayer helmet / black mask",
            bankScanned));
    layout.add(
        vorkath && ranged
            ? toEquipmentItem(
                findPreferredDizanaCapeOrFallback(allOwned),
                "Dizana's quiver / Ava's assembler",
                bankScanned)
            : auditedCapeEquipmentItem(
                encounterTask, strategy, style, allOwned, bankScanned, selectedWeapon));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned,
                EquipmentInventorySlot.AMULET,
                false,
                vorkath
                    ? useVorkathSlayerHead
                        ? ranged ? list("l014") : list("l015")
                        : ranged ? list("l016") : list("l017")
                    : ranged
                        ? dragonEquipmentProgression(
                            encounterTask,
                            strategy,
                            EquipmentInventorySlot.AMULET,
                            selectedWeapon,
                            list("l014"))
                        : dragonEquipmentProgression(
                            encounterTask,
                            strategy,
                            EquipmentInventorySlot.AMULET,
                            selectedWeapon,
                            list("l015"))),
            vorkath
                ? useVorkathSlayerHead
                    ? ranged ? "Necklace of anguish" : "Melee damage amulet"
                    : "Salve amulet (ei)"
                : ranged ? "Necklace of anguish" : "Melee amulet",
            bankScanned));
    layout.add(buildAmmoItem(style, selectedWeapon, strategy, allOwned, bankScanned));
    layout.add(
        toEquipmentItem(
            selectedWeapon,
            vorkath
                ? "Dragon hunter crossbow / Dragon hunter lance"
                : "Dragon hunter crossbow / one-handed crossbow",
            bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned,
                EquipmentInventorySlot.BODY,
                false,
                ranged
                    ? useEliteVoid || useRegularVoid
                        ? asList(useEliteVoid ? "elite void top" : "void knight top")
                        : dragonEquipmentProgression(
                            encounterTask,
                            strategy,
                            EquipmentInventorySlot.BODY,
                            selectedWeapon,
                            Collections.emptyList())
                    : dragonEquipmentProgression(
                        encounterTask,
                        strategy,
                        EquipmentInventorySlot.BODY,
                        selectedWeapon,
                        list("l018"))),
            ranged
                ? (useEliteVoid
                    ? "Elite void top"
                    : useRegularVoid ? "Void knight top" : "Ranged body")
                : "Melee body",
            bankScanned));
    if (ranged) {
      layout.add(
          toEquipmentItem(
              findPreferredEquipment(allOwned, EquipmentInventorySlot.SHIELD, false, list("l019")),
              "Dragonfire ward / Anti-dragon shield",
              bankScanned));
    } else if (selectedWeapon != null && selectedWeapon.isTwoHanded()) {
      layout.add(
          new KitItem(
              "Two-handed weapon — no defender", -1, 1, KitItem.Status.EQUIPPED));
    } else {
      boolean blueDragonDefender = !vorkath;
      layout.add(
          toEquipmentItem(
              findPreferredEquipment(
                  allOwned,
                  EquipmentInventorySlot.SHIELD,
                  false,
                  vorkath || blueDragonDefender ? list("l020") : list("l021")),
              vorkath || blueDragonDefender
                  ? "Best owned defender"
                  : "Dragonfire shield protection",
              bankScanned));
    }
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned,
                EquipmentInventorySlot.LEGS,
                false,
                ranged
                    ? useEliteVoid || useRegularVoid
                        ? asList(useEliteVoid ? "elite void robe" : "void knight robe")
                        : dragonEquipmentProgression(
                            encounterTask,
                            strategy,
                            EquipmentInventorySlot.LEGS,
                            selectedWeapon,
                            Collections.emptyList())
                    : dragonEquipmentProgression(
                        encounterTask,
                        strategy,
                        EquipmentInventorySlot.LEGS,
                        selectedWeapon,
                        list("l022"))),
            ranged
                ? (useEliteVoid
                    ? "Elite void robe"
                    : useRegularVoid ? "Void knight robe" : "Ranged legs")
                : "Melee legs",
            bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned,
                EquipmentInventorySlot.GLOVES,
                false,
                ranged
                    ? useEliteVoid || useRegularVoid
                        ? list("l023")
                        : dragonEquipmentProgression(
                            encounterTask,
                            strategy,
                            EquipmentInventorySlot.GLOVES,
                            selectedWeapon,
                            Collections.emptyList())
                    : dragonEquipmentProgression(
                        encounterTask,
                        strategy,
                        EquipmentInventorySlot.GLOVES,
                        selectedWeapon,
                        list("l024"))),
            ranged
                ? (useEliteVoid || useRegularVoid ? "Void knight gloves" : "Ranged gloves")
                : "Melee gloves",
            bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned,
                EquipmentInventorySlot.BOOTS,
                false,
                dragonEquipmentProgression(
                    encounterTask,
                    strategy,
                    EquipmentInventorySlot.BOOTS,
                    selectedWeapon,
                    Collections.emptyList())),
            ranged ? "Ranged boots" : "Melee boots",
            bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned,
                EquipmentInventorySlot.RING,
                false,
                dragonEquipmentProgression(
                    encounterTask,
                    strategy,
                    EquipmentInventorySlot.RING,
                    selectedWeapon,
                    dragonRingExtras(vorkath))),
            ranged ? "Ranged ring" : "Melee ring",
            bankScanned));
    KitItem extraQuiverAmmo =
        buildDizanaExtraAmmoItem(false, style, selectedWeapon, strategy, allOwned, bankScanned);
    if (extraQuiverAmmo != null) {
      layout.add(extraQuiverAmmo);
    }
    return layout;
  }

  private static List<String> dragonRingExtras(boolean vorkath) {
    return vorkath ? Collections.singletonList("lightbearer") : Collections.emptyList();
  }

  static boolean regularBlueDragonsAllowLightbearerForRegression() {
    return dragonRingExtras(false).contains("lightbearer");
  }

  static boolean vorkathAllowsLightbearerForRegression() {
    return dragonRingExtras(true).contains("lightbearer");
  }

  private static List<KitItem> buildReviewedBlueDragonMagicLayout(
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      OwnedItem selectedWeapon) {
    List<KitItem> layout = new ArrayList<>();
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(allOwned, EquipmentInventorySlot.HEAD, false, list("l009")),
            "Imbued Slayer helmet / black mask",
            bankScanned));
    layout.add(
        auditedCapeEquipmentItem(
            "Blue dragons", strategy, CombatStyle.MAGIC, allOwned, bankScanned, selectedWeapon));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned,
                EquipmentInventorySlot.AMULET,
                false,
                dragonEquipmentProgression(
                    "Blue dragons",
                    strategy,
                    EquipmentInventorySlot.AMULET,
                    selectedWeapon,
                    Collections.emptyList())),
            "Magic damage amulet",
            bankScanned));
    layout.add(buildPassiveAmmoItem(selectedWeapon, strategy, allOwned, bankScanned));
    layout.add(toEquipmentItem(selectedWeapon, "Dragon hunter wand / Water staff", bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned,
                EquipmentInventorySlot.BODY,
                false,
                dragonEquipmentProgression(
                    "Blue dragons",
                    strategy,
                    EquipmentInventorySlot.BODY,
                    selectedWeapon,
                    Collections.emptyList())),
            "Magic body",
            bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(allOwned, EquipmentInventorySlot.SHIELD, false, list("l025")),
            "Anti-dragon shield",
            bankScanned));
    for (EquipmentInventorySlot slot :
        new EquipmentInventorySlot[] {
          EquipmentInventorySlot.LEGS,
          EquipmentInventorySlot.GLOVES,
          EquipmentInventorySlot.BOOTS,
          EquipmentInventorySlot.RING
        }) {
      layout.add(
          toEquipmentItem(
              findPreferredEquipment(
                  allOwned,
                  slot,
                  false,
                  dragonEquipmentProgression(
                      "Blue dragons", strategy, slot, selectedWeapon, Collections.emptyList())),
              "Magic " + slot.name().toLowerCase(Locale.ENGLISH),
              bankScanned));
    }
    return layout;
  }

  private static List<String> dragonEquipmentProgression(
      String task,
      TaskStrategy strategy,
      EquipmentInventorySlot slot,
      OwnedItem selectedWeapon,
      List<String> encounterPriorities) {
    Set<String> merged = new LinkedHashSet<>();
    if (encounterPriorities != null) {
      merged.addAll(encounterPriorities);
    }
    merged.addAll(
        SlayerEquipmentAuditCatalog.priorities(
            task, strategy, slot, selectedWeapon == null ? "" : selectedWeapon.normalizedName));
    return new ArrayList<>(merged);
  }

  private static List<KitItem> buildInfernoEquipmentLayout(
      TaskStrategy strategy,
      CombatStyle style,
      List<OwnedItem> equipped,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      OwnedItem selectedWeapon) {
    List<KitItem> layout = new ArrayList<>();
    boolean bowfa = matchesWeaponName(selectedWeapon, "", "bow of faerdhinen");
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(allOwned, EquipmentInventorySlot.HEAD, false, list("l026")),
            "Imbued Slayer helmet / black mask",
            bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredDizanaCapeOrFallback(allOwned),
            "Dizana's quiver / Ava's assembler",
            bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(allOwned, EquipmentInventorySlot.AMULET, false, list("l014")),
            "Necklace of anguish",
            bankScanned));
    layout.add(buildInfernoAmmoItem(selectedWeapon, strategy, allOwned, bankScanned));
    layout.add(toEquipmentItem(selectedWeapon, "Twisted bow / Bow of faerdhinen", bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned, EquipmentInventorySlot.BODY, false, bowfa ? list("l027") : list("l028")),
            bowfa ? "Crystal body" : "Masori body",
            bankScanned));
    if (selectedWeapon == null) {
      layout.add(
          missingItem("No off-hand selected until a compatible weapon is found", bankScanned));
    } else if (selectedWeapon.isTwoHanded()) {
      layout.add(
          new KitItem(
              "Two-handed weapon — no off-hand", -1, 1, KitItem.Status.EQUIPPED));
    } else {
      layout.add(
          toEquipmentItem(
              findPreferredEquipment(allOwned, EquipmentInventorySlot.SHIELD, false, list("l029")),
              "Twisted buckler",
              bankScanned));
    }
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned, EquipmentInventorySlot.LEGS, false, bowfa ? list("l030") : list("l031")),
            bowfa ? "Crystal legs" : "Masori chaps",
            bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(allOwned, EquipmentInventorySlot.GLOVES, false, list("l032")),
            "Zaryte vambraces / Barrows gloves",
            bankScanned));
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(allOwned, EquipmentInventorySlot.BOOTS, false, list("l033")),
            "Avernic treads / Pegasian boots",
            bankScanned));
    boolean completionFirst =
        strategy != null && normalize(strategy.getMethod()).contains("not afk");
    layout.add(
        toEquipmentItem(
            findPreferredEquipment(
                allOwned,
                EquipmentInventorySlot.RING,
                false,
                completionFirst ? list("l034") : list("l035")),
            completionFirst ? "Ring of suffering (i)" : "Venator ring",
            bankScanned));
    KitItem extraQuiverAmmo =
        buildDizanaExtraAmmoItem(
            true, CombatStyle.RANGED, selectedWeapon, strategy, allOwned, bankScanned);
    if (extraQuiverAmmo != null) {
      layout.add(extraQuiverAmmo);
    }
    return layout;
  }

  private static KitItem buildInfernoAmmoItem(
      OwnedItem selectedWeapon,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned) {
    return buildInfernoAmmoItem(selectedWeapon, strategy, allOwned, bankScanned, true);
  }

  private static KitItem buildInfernoAmmoItem(
      OwnedItem selectedWeapon,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      boolean reserveNormalAmmoForDizana) {
    String fallbackName =
        strategy != null && !strategy.getWeaponPrioritiesForPolicy().isEmpty()
            ? normalize(strategy.getWeaponPrioritiesForPolicy().get(0))
            : "";
    if (reserveNormalAmmoForDizana
        && hasUsableDizanaCape(allOwned)
        && canUseDizanaExtraAmmo(selectedWeapon, fallbackName)) {
      return buildPassiveAmmoItem(selectedWeapon, strategy, allOwned, bankScanned);
    }
    if (matchesWeaponName(selectedWeapon, fallbackName, "bow of faerdhinen")
        || matchesWeaponName(selectedWeapon, fallbackName, "toxic blowpipe")) {
      return buildPassiveAmmoItem(selectedWeapon, strategy, allOwned, bankScanned);
    }
    if (matchesWeaponName(selectedWeapon, fallbackName, "crossbow")) {
      return recommendedAmmunitionItem(
          !reserveNormalAmmoForDizana,
          "Ruby dragon bolts (e)",
          allOwned,
          bankScanned,
          "ruby dragon bolts e",
          "ruby bolts e",
          "diamond dragon bolts e",
          "diamond bolts e");
    }
    return bestOwnedInfernoArrow(!reserveNormalAmmoForDizana, allOwned, bankScanned);
  }

  private static KitItem buildAmmoItem(
      CombatStyle style,
      OwnedItem selectedWeapon,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned) {
    return buildAmmoItem(style, selectedWeapon, strategy, allOwned, bankScanned, true);
  }

  private static KitItem buildAraxxorSwitchAmmoItem(
      CombatStyle style,
      OwnedItem selectedWeapon,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned) {
    OwnedItem safeWeapon =
        findPreferredInventoryItem(allOwned, araxxorSafeWeaponAlternatives(), true);
    List<String> ammunition =
        araxxorSafeAmmunitionAlternatives(safeWeapon == null ? "" : safeWeapon.normalizedName);
    if (ammunition.isEmpty()) {
      return buildAmmoItem(style, selectedWeapon, strategy, allOwned, bankScanned);
    }
    return strictAmmoItem(
        "Safe araxyte ammunition", allOwned, bankScanned, ammunition.toArray(new String[0]));
  }

  private static KitItem buildAmmoItem(
      CombatStyle style,
      OwnedItem selectedWeapon,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      boolean reserveNormalAmmoForDizana) {
    if (style != CombatStyle.RANGED) {
      return buildPassiveAmmoItem(selectedWeapon, strategy, allOwned, bankScanned);
    }
    String weaponName =
        selectedWeapon != null
            ? selectedWeapon.normalizedName
            : strategy != null && !strategy.getWeaponPrioritiesForPolicy().isEmpty()
                ? normalize(strategy.getWeaponPrioritiesForPolicy().get(0))
                : "";
    if (reserveNormalAmmoForDizana
        && hasUsableDizanaCape(allOwned)
        && canUseDizanaExtraAmmo(selectedWeapon, weaponName)) {
      return buildPassiveAmmoItem(selectedWeapon, strategy, allOwned, bankScanned);
    }
    if (matchesWeaponName(selectedWeapon, weaponName, "blowpipe")
        || matchesWeaponName(selectedWeapon, weaponName, "bow of faerdhinen")
        || matchesWeaponName(selectedWeapon, weaponName, "crystal bow")
        || matchesWeaponName(selectedWeapon, weaponName, "webweaver bow")
        || matchesWeaponName(selectedWeapon, weaponName, "craw s bow")
        || matchesWeaponName(selectedWeapon, weaponName, "chinchompa")) {
      return buildPassiveAmmoItem(selectedWeapon, strategy, allOwned, bankScanned);
    }
    if (matchesWeaponName(selectedWeapon, weaponName, "ballista")) {
      return strictAmmoItem(
          "Javelins", allOwned, bankScanned, "dragon javelin", "amethyst javelin", "rune javelin");
    }
    if (matchesWeaponName(selectedWeapon, weaponName, "atlatl")) {
      return strictAmmoItem("Atlatl darts", allOwned, bankScanned, "atlatl dart");
    }
    if (matchesWeaponName(selectedWeapon, weaponName, "hunters sunlight crossbow")) {
      return recommendedAmmunitionItem(
          !reserveNormalAmmoForDizana,
          "Antler bolts",
          allOwned,
          bankScanned,
          "moonlight antler bolts",
          "sunlight antler bolts");
    }
    if (matchesWeaponName(selectedWeapon, weaponName, "crossbow")) {
      String method =
          strategy == null ? "" : normalize(strategy.getMethod() + " " + strategy.getRationale());
      if (method.contains("dragonstone bolt")) {
        return recommendedAmmunitionItem(
            !reserveNormalAmmoForDizana,
            "Enchanted dragonstone bolts",
            allOwned,
            bankScanned,
            "dragonstone dragon bolts e",
            "dragonstone bolts e",
            "ruby dragon bolts e",
            "diamond dragon bolts e",
            "dragon bolts",
            "runite bolts",
            "broad bolts");
      }
      return recommendedAmmunitionItem(
          !reserveNormalAmmoForDizana,
          "Compatible bolts",
          allOwned,
          bankScanned,
          "ruby dragon bolts e",
          "diamond dragon bolts e",
          "dragon bolts",
          "amethyst broad bolts",
          "runite bolts",
          "broad bolts");
    }
    return bestOwnedStandardArrow(
        !reserveNormalAmmoForDizana, selectedWeapon, weaponName, strategy, allOwned, bankScanned);
  }

  private static KitItem bestOwnedStandardArrow(
      boolean includeExtraQuiverSlot,
      OwnedItem selectedWeapon,
      String fallbackWeaponName,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned) {
    String[] priorities =
        standardArrowPriorityForPolicy(
            strategy, supportsDragonArrows(selectedWeapon, fallbackWeaponName));
    return recommendedAmmunitionItem(
        includeExtraQuiverSlot,
        strategy != null && strategy.getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT
            ? "Cost-efficient owned arrows"
            : "Best owned arrows",
        allOwned,
        bankScanned,
        priorities);
  }

  private static String[] standardArrowPriorityForPolicy(
      TaskStrategy strategy, boolean dragonCompatible) {
    if (strategy != null && strategy.getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT) {
      return removeDragonArrowsIfUnsupported(
          strategy.isBoss() ? EFFICIENT_BOSS_ARROW_PRIORITY : EFFICIENT_REGULAR_ARROW_PRIORITY,
          dragonCompatible);
    }
    return dragonCompatible
        ? STRONGEST_STANDARD_ARROW_PRIORITY
        : STRONGEST_NON_DRAGON_ARROW_PRIORITY;
  }

  private static String[] removeDragonArrowsIfUnsupported(
      String[] priorities, boolean dragonCompatible) {
    if (dragonCompatible) {
      return priorities;
    }
    List<String> compatible = new ArrayList<>();
    for (String priority : priorities) {
      if (!normalize(priority).contains("dragon arrow")) {
        compatible.add(priority);
      }
    }
    return compatible.toArray(new String[0]);
  }

  private static boolean supportsDragonArrows(OwnedItem selectedWeapon, String fallbackWeaponName) {
    String weaponName =
        selectedWeapon == null ? normalize(fallbackWeaponName) : selectedWeapon.normalizedName;
    return weaponName.contains("twisted bow")
        || weaponName.contains("venator bow")
        || weaponName.contains("scorching bow")
        || weaponName.contains("dark bow")
        || weaponName.contains("3rd age bow");
  }

  static List<String> standardArrowPriorityForRegression(
      TaskStrategy.CostPolicy policy, boolean boss, boolean dragonCompatible) {
    TaskStrategy strategy =
        TaskStrategy.builder(TaskStrategy.CombatStyle.RANGED, "Arrow policy regression")
            .costPolicy(policy)
            .boss(boss)
            .build();
    return Arrays.asList(standardArrowPriorityForPolicy(strategy, dragonCompatible));
  }

  private static KitItem bestOwnedInfernoArrow(
      boolean includeExtraQuiverSlot, List<OwnedItem> allOwned, boolean bankScanned) {
    OwnedItem selected = null;
    int selectedRank = Integer.MAX_VALUE;
    for (OwnedItem item : allOwned) {
      if (item == null
          || (!includeExtraQuiverSlot && item.equipmentSlot == EXTRA_QUIVER_AMMO_SLOT)) {
        continue;
      }
      int rank = QuiverAmmo.infernoPreference(item.itemId);
      if (rank < selectedRank
          || (rank == selectedRank
              && item.equipmentSlot == EXTRA_QUIVER_AMMO_SLOT
              && selected != null
              && selected.equipmentSlot != EXTRA_QUIVER_AMMO_SLOT)) {
        selected = item;
        selectedRank = rank;
      }
    }
    if (selected != null && selectedRank < Integer.MAX_VALUE) {
      return selected.toLoadoutItem(1);
    }
    return recommendedAmmunitionItem(
        includeExtraQuiverSlot,
        "Best owned arrows for the Inferno",
        allOwned,
        bankScanned,
        INFERNO_ARROW_PRIORITY);
  }

  private static KitItem buildDizanaExtraAmmoItem(
      boolean inferno,
      CombatStyle style,
      OwnedItem selectedWeapon,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned) {
    if (style != CombatStyle.RANGED || !hasUsableDizanaCape(allOwned)) {
      return null;
    }
    String fallbackName =
        selectedWeapon != null
            ? selectedWeapon.normalizedName
            : strategy != null && !strategy.getWeaponPrioritiesForPolicy().isEmpty()
                ? normalize(strategy.getWeaponPrioritiesForPolicy().get(0))
                : "";
    if (!canUseDizanaExtraAmmo(selectedWeapon, fallbackName)) {
      return null;
    }
    return inferno
        ? buildInfernoAmmoItem(selectedWeapon, strategy, allOwned, bankScanned, false)
        : buildAmmoItem(style, selectedWeapon, strategy, allOwned, bankScanned, false);
  }

  private static boolean canUseDizanaExtraAmmo(
      OwnedItem selectedWeapon, String fallbackWeaponName) {
    String weaponName =
        selectedWeapon != null ? selectedWeapon.normalizedName : normalize(fallbackWeaponName);
    if (weaponName.isEmpty()) {
      return false;
    }
    if (weaponName.contains("crossbow")) {
      return true;
    }
    if (!weaponName.contains("bow")) {
      return false;
    }
    return !weaponName.contains("bow of faerdhinen")
        && !weaponName.contains("crystal bow")
        && !weaponName.contains("webweaver bow")
        && !weaponName.contains("craw s bow");
  }

  private static boolean matchesWeaponName(
      OwnedItem selectedWeapon, String fallbackName, String fragment) {
    return selectedWeapon != null
        ? selectedWeapon.matchesName(fragment)
        : normalize(fallbackName).contains(normalize(fragment));
  }

  private static KitItem buildPassiveAmmoItem(
      OwnedItem selectedWeapon,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned) {
    if (strategy != null
        && strategy.getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT
        && benefitsFromLuckyPenny(selectedWeapon)) {
      return strictAmmoItem(
          "Ghommal's lucky penny",
          allOwned,
          bankScanned,
          "ghommal s lucky penny",
          "ghommals lucky penny");
    }
    return strictAmmoItem(
        "Prayer blessing",
        allOwned,
        bankScanned,
        "rada s blessing 4",
        "rada s blessing 3",
        "rada s blessing 2",
        "rada s blessing 1",
        "holy blessing",
        "unholy blessing",
        "war blessing",
        "peaceful blessing",
        "honourable blessing",
        "blessing");
  }

  private static boolean benefitsFromLuckyPenny(OwnedItem selectedWeapon) {
    if (selectedWeapon == null) {
      return false;
    }
    return selectedWeapon.matchesName("scythe of vitur");
  }

  private static KitItem buildCapeEquipmentItem(
      TaskStrategy strategy,
      CombatStyle style,
      List<OwnedItem> equipped,
      List<OwnedItem> allOwned,
      boolean bankScanned) {
    return equipmentItem(
        EquipmentInventorySlot.CAPE,
        null,
        style == CombatStyle.RANGED
            ? "Ava's assembler / accumulator"
            : style == CombatStyle.MAGIC ? "Magic cape" : "Melee cape",
        strategy,
        style,
        equipped,
        allOwned,
        bankScanned,
        false,
        style == CombatStyle.RANGED
            ? array("a001")
            : style == CombatStyle.MAGIC ? array("a002") : array("a003"));
  }

  private static OwnedItem findPreferredDizanaCapeOrFallback(List<OwnedItem> allOwned) {
    OwnedItem dizana = findPreferredUsableDizanaCape(allOwned);
    return dizana != null
        ? dizana
        : findPreferredEquipment(allOwned, EquipmentInventorySlot.CAPE, false, list("l036"));
  }

  private static OwnedItem findPreferredUsableDizanaCape(List<OwnedItem> allOwned) {
    if (allOwned == null) {
      return null;
    }
    OwnedItem best = null;
    int bestScore = Integer.MIN_VALUE;
    for (OwnedItem item : allOwned) {
      int score = dizanaCapePreferenceScore(item);
      if (score > bestScore) {
        best = item;
        bestScore = score;
      }
    }
    return bestScore > 0 ? best : null;
  }

  private static int dizanaCapePreferenceScore(OwnedItem item) {
    if (item == null) {
      return -1;
    }
    switch (Math.abs(item.itemId)) {
      case ItemID.SKILLCAPE_MAX_DIZANAS:
      case ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER:
        return 400;
      case ItemID.DIZANAS_QUIVER_INFINITE:
      case ItemID.DIZANAS_QUIVER_INFINITE_TROUVER:
        return 300;
      case ItemID.DIZANAS_QUIVER_CHARGED:
      case ItemID.DIZANAS_QUIVER_CHARGED_TROUVER:
        return 200;
      case ItemID.DIZANAS_QUIVER_UNCHARGED:
      case ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER:
        return 100;
      case ItemID.DIZANAS_QUIVER_BROKEN:
      case ItemID.DIZANAS_QUIVER_INFINITE_BROKEN:
      case ItemID.SKILLCAPE_MAX_DIZANAS_BROKEN:
      case ItemID.DIZANAS_QUIVER_TROUVER_BROKEN:
      case ItemID.DIZANAS_QUIVER_TROUVER_MANGLED:
      case ItemID.DIZANAS_QUIVER_INFINITE_TROUVER_BROKEN:
      case ItemID.DIZANAS_QUIVER_INFINITE_TROUVER_MANGLED:
      case ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_BROKEN:
      case ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_MANGLED:
        return -1;
      default:
        break;
    }
    String name = item.normalizedName;
    if (!name.contains("dizana")
        || (!name.contains("quiver") && !name.contains("max cape"))
        || name.contains("max hood")
        || name.contains("broken")
        || name.contains("mangled")) {
      return -1;
    }
    if (name.contains("max cape")) {
      return 400;
    }
    if (name.contains("blessed")) {
      return 300;
    }
    if (!name.contains("uncharged")) {
      return 200;
    }
    return 100;
  }

  private static boolean hasUsableDizanaCape(List<OwnedItem> allOwned) {
    return findPreferredUsableDizanaCape(allOwned) != null;
  }

  private static boolean isUsableDizanaCape(OwnedItem item) {
    return dizanaCapePreferenceScore(item) > 0;
  }

  private static OwnedItem findExtraQuiverAmmo(List<OwnedItem> allOwned) {
    if (allOwned == null) {
      return null;
    }
    for (OwnedItem item : allOwned) {
      if (item != null && item.equipmentSlot == EXTRA_QUIVER_AMMO_SLOT && item.quantity > 0) {
        return item;
      }
    }
    return null;
  }

  private static boolean isQuiverAmmoCompatibleWithWeapon(
      OwnedItem quiverAmmo, OwnedItem selectedWeapon, String fallbackWeaponName) {
    if (quiverAmmo == null || !canUseDizanaExtraAmmo(selectedWeapon, fallbackWeaponName)) {
      return false;
    }
    String weaponName =
        selectedWeapon != null ? selectedWeapon.normalizedName : normalize(fallbackWeaponName);
    if (weaponName.contains("crossbow")) {
      return quiverAmmo.matchesName("bolt");
    }
    return quiverAmmo.matchesName("arrow");
  }

  private static KitItem strictAmmoItem(
      String placeholder, List<OwnedItem> allOwned, boolean bankScanned, String... alternatives) {
    OwnedItem selected =
        findPreferredEquipment(allOwned, EquipmentInventorySlot.AMMO, false, asList(alternatives));
    return toEquipmentItem(selected, placeholder, bankScanned);
  }

  private static KitItem recommendedAmmunitionItem(
      boolean includeExtraQuiverSlot,
      String placeholder,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      String... alternatives) {
    if (!includeExtraQuiverSlot) {
      return strictAmmoItem(placeholder, allOwned, bankScanned, alternatives);
    }
    for (String alternative : alternatives) {
      String fragment = normalize(alternative);
      if (fragment.isEmpty()) {
        continue;
      }
      boolean seekingAlternative = fragment.startsWith("seeking ") && fragment.contains("arrow");
      for (OwnedItem item : allOwned) {
        if ((!seekingAlternative || item.isExactSeekingArrow())
            && item.matchesName(fragment)
            && (item.equipmentSlot == EXTRA_QUIVER_AMMO_SLOT
                || item.matchesSlot(EquipmentInventorySlot.AMMO))) {
          return item.toLoadoutItem(1);
        }
      }
    }
    return missingItem(placeholder, bankScanned);
  }

  private static KitItem equipmentItem(
      EquipmentInventorySlot slot,
      Requirement requirement,
      String placeholder,
      TaskStrategy strategy,
      CombatStyle style,
      List<OwnedItem> equipped,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      boolean requireOneHanded,
      String... alternatives) {
    OwnedItem selected =
        selectEquipmentOwned(
            slot, requirement, strategy, style, equipped, allOwned, requireOneHanded, alternatives);
    return toEquipmentItem(
        selected, requirement == null ? placeholder : requirement.displayName, bankScanned);
  }

  private static OwnedItem selectEquipmentOwned(
      EquipmentInventorySlot slot,
      Requirement requirement,
      TaskStrategy strategy,
      CombatStyle style,
      List<OwnedItem> equipped,
      List<OwnedItem> allOwned,
      boolean requireOneHanded,
      String... alternatives) {
    if (requirement != null) {
      return findPreferredEquipment(allOwned, slot, requireOneHanded, requirement.alternatives);
    }
    if (slot == EquipmentInventorySlot.HEAD) {
      OwnedItem slayerHead = findPreferredEquipment(allOwned, slot, false, list("l037"));
      if (slayerHead != null) {
        return slayerHead;
      }
    }
    if (slot == EquipmentInventorySlot.WEAPON) {
      OwnedItem namedWeapon =
          findPreferredEquipmentAllowed(
              allOwned, slot, requireOneHanded, asList(alternatives), strategy);
      if (namedWeapon != null) {
        return namedWeapon;
      }
      if (strategy != null && strategy.isStrictWeaponProfile()) {
        List<String> reviewedFallbacks = strategy.getWeaponPriorities();
        if (!reviewedFallbacks.isEmpty()) {
          OwnedItem allowedFallback =
              findPreferredEquipmentAllowed(
                  allOwned, slot, requireOneHanded, reviewedFallbacks, strategy);
          if (allowedFallback != null) {
            return allowedFallback;
          }
          if (strategy.getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT) {
            return findPreferredEquipment(allOwned, slot, requireOneHanded, reviewedFallbacks);
          }
        }
        return findBestCompatibleOwnedSlot(allOwned, slot, strategy, style, requireOneHanded);
      }
    }
    OwnedItem best = findBestEquipment(allOwned, slot, strategy, style, requireOneHanded);
    if (best != null) {
      return best;
    }
    OwnedItem worn = findEquippedSlot(equipped, slot);
    if (worn != null
        && isStyleCompatible(worn, style, slot)
        && (!requireOneHanded || !worn.isTwoHanded())
        && !isDisallowedByPolicy(worn, strategy, slot)) {
      return worn;
    }
    return findPreferredEquipment(allOwned, slot, requireOneHanded, asList(alternatives));
  }

  private static KitItem toEquipmentItem(
      OwnedItem selected, String placeholder, boolean bankScanned) {
    return selected == null ? missingItem(placeholder, bankScanned) : selected.toLoadoutItem(1);
  }

  private static OwnedItem findPreferredEquipment(
      List<OwnedItem> owned,
      EquipmentInventorySlot slot,
      boolean requireOneHanded,
      List<String> alternatives) {
    return findPreferredEquipmentVariant(owned, slot, requireOneHanded, alternatives, null, false);
  }

  private static OwnedItem findPreferredEquipmentAllowed(
      List<OwnedItem> owned,
      EquipmentInventorySlot slot,
      boolean requireOneHanded,
      List<String> alternatives,
      TaskStrategy strategy) {
    return findPreferredEquipmentVariant(
        owned, slot, requireOneHanded, alternatives, strategy, true);
  }

  private static OwnedItem findPreferredEquipmentVariant(
      List<OwnedItem> owned,
      EquipmentInventorySlot slot,
      boolean requireOneHanded,
      List<String> alternatives,
      TaskStrategy strategy,
      boolean enforcePolicy) {
    if (owned == null || alternatives == null) {
      return null;
    }
    for (int priorityIndex = 0; priorityIndex < alternatives.size(); priorityIndex++) {
      String fragment = normalize(alternatives.get(priorityIndex));
      if (fragment.isEmpty()) {
        continue;
      }
      OwnedItem bestVariant = null;
      long bestVariantScore = Long.MIN_VALUE;
      for (OwnedItem item : owned) {
        if (item == null
            || !item.matchesEquipmentProgression(fragment)
            || !matchesEquipmentSlot(item, slot)
            || !isUsableEquipmentVariant(item, slot)
            || requireOneHanded && item.isTwoHanded()
            || enforcePolicy && isDisallowedByPolicy(item, strategy, slot)) {
          continue;
        }
        int directRank = item.directEquipmentProgressionRank(alternatives);
        if (directRank >= 0 && directRank != priorityIndex) {
          continue;
        }
        long variantScore = equipmentVariantScore(item, slot);
        if (bestVariant == null || variantScore > bestVariantScore) {
          bestVariant = item;
          bestVariantScore = variantScore;
        }
      }
      if (bestVariant != null) {
        return bestVariant;
      }
    }
    return null;
  }

  private static boolean isUsableEquipmentVariant(OwnedItem item, EquipmentInventorySlot slot) {
    if (item == null) {
      return false;
    }
    String name = item.normalizedName;
    if (name.contains("broken")
        || name.contains("mangled")
        || name.contains("depleted")
        || name.contains("inactive")
        || name.contains("max hood")) {
      return false;
    }
    if (slot == EquipmentInventorySlot.WEAPON
        && (name.contains("uncharged") || name.contains(" empty"))) {
      return false;
    }
    return !(name.matches("(?:dharok|guthan|torag|verac) s .+ 0"));
  }

  static boolean usableEquipmentVariantForRegression(
      String displayName, EquipmentInventorySlot slot) {
    return isUsableEquipmentVariant(
        new OwnedItem(
            -1,
            displayName,
            1,
            KitItem.Status.BANK,
            slot == null ? -1 : slot.getSlotIdx(),
            null,
            Collections.singleton(normalize(displayName))),
        slot);
  }

  private static long equipmentVariantScore(OwnedItem item, EquipmentInventorySlot slot) {
    if (item == null || item.equipmentStats == null) {
      return Long.MIN_VALUE / 2L + (item == null ? 0L : ownershipTieBreaker(item.status));
    }
    return Math.max(
            meleeScore(item.equipmentStats, slot),
            Math.max(rangedScore(item.equipmentStats, slot), magicScore(item.equipmentStats, slot)))
        + ownershipTieBreaker(item.status);
  }

  private static OwnedItem findBestEquipment(
      List<OwnedItem> owned,
      EquipmentInventorySlot slot,
      TaskStrategy strategy,
      CombatStyle style,
      boolean requireOneHanded) {
    OwnedItem best = null;
    long bestScore = Long.MIN_VALUE;
    for (OwnedItem item : owned) {
      if (!matchesEquipmentSlot(item, slot)
          || item.equipmentStats == null
          || !isStyleCompatible(item, style, slot)
          || (requireOneHanded && item.isTwoHanded())
          || isDisallowedByPolicy(item, strategy, slot)) {
        continue;
      }
      long score = equipmentScore(item, strategy, style, slot);
      if (best == null || score > bestScore) {
        best = item;
        bestScore = score;
      }
    }
    return best;
  }

  private static boolean matchesEquipmentSlot(OwnedItem item, EquipmentInventorySlot slot) {
    if (item == null || slot == null) {
      return false;
    }
    if (item.matchesSlot(slot)) {
      return true;
    }
    if (slot == EquipmentInventorySlot.GLOVES && isGloveSlotItemName(item.normalizedName)) {
      return true;
    }
    return slot == EquipmentInventorySlot.SHIELD && isDefenderName(item.normalizedName);
  }

  private static boolean isGloveSlotItemName(String value) {
    String name = normalize(value);
    return name.contains("bracelet")
        || name.contains("gloves")
        || name.contains("gauntlets")
        || name.contains("vambraces");
  }

  static boolean gloveFallbackMatchesSlotForRegression(String displayName) {
    return isGloveSlotItemName(displayName);
  }

  private static boolean isDefenderName(String value) {
    String name = normalize(value);
    return name.contains("defender") && !name.contains("defender hilt");
  }

  static boolean defenderFallbackMatchesShieldForRegression(String displayName) {
    return isDefenderName(displayName);
  }

  private static KitItem auditedCapeEquipmentItem(
      String task,
      TaskStrategy strategy,
      CombatStyle style,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      OwnedItem selectedWeapon) {
    if (style == CombatStyle.RANGED) {
      OwnedItem dizana = findPreferredUsableDizanaCape(allOwned);
      if (dizana != null) {
        return dizana.toLoadoutItem(1);
      }
    }
    return auditedEquipmentItem(
        task,
        EquipmentInventorySlot.CAPE,
        null,
        style == CombatStyle.RANGED
            ? "Dizana's quiver / Ava's assembler"
            : style == CombatStyle.MAGIC ? "Magic cape" : "Melee cape",
        strategy,
        style,
        Collections.emptyList(),
        allOwned,
        bankScanned,
        false,
        selectedWeapon);
  }

  private static KitItem auditedEquipmentItem(
      String task,
      EquipmentInventorySlot slot,
      Requirement requirement,
      String placeholder,
      TaskStrategy strategy,
      CombatStyle style,
      List<OwnedItem> equipped,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      boolean requireOneHanded,
      OwnedItem selectedWeapon) {
    List<String> alternatives =
        requirement == null
            ? SlayerEquipmentAuditCatalog.priorities(
                task, strategy, slot, selectedWeapon == null ? "" : selectedWeapon.normalizedName)
            : requirement.alternatives;
    OwnedItem selected = findPreferredEquipment(allOwned, slot, requireOneHanded, alternatives);
    if (selected == null && requirement == null) {
      selected = findBestCompatibleOwnedSlot(allOwned, slot, strategy, style, requireOneHanded);
    }
    if (selected == null && requirement == null) {
      OwnedItem worn = findEquippedSlot(equipped, slot);
      if (worn != null
          && isStyleCompatible(worn, style, slot)
          && (!requireOneHanded || !worn.isTwoHanded())
          && !isDisallowedByPolicy(worn, strategy, slot)) {
        selected = worn;
      }
    }
    return toEquipmentItem(
        selected, requirement == null ? placeholder : requirement.displayName, bankScanned);
  }

  private static OwnedItem findBestCompatibleOwnedSlot(
      List<OwnedItem> owned,
      EquipmentInventorySlot slot,
      TaskStrategy strategy,
      CombatStyle style,
      boolean requireOneHanded) {
    if (owned == null || slot == null) {
      return null;
    }
    OwnedItem best = null;
    long bestScore = Long.MIN_VALUE;
    for (OwnedItem item : owned) {
      if (!matchesEquipmentSlot(item, slot)
          || !isUsableEquipmentVariant(item, slot)
          || !isStyleCompatible(item, style, slot)
          || requireOneHanded && item.isTwoHanded()
          || isDisallowedByPolicy(item, strategy, slot)) {
        continue;
      }
      long score =
          item.equipmentStats == null
              ? equipmentVariantScore(item, slot)
              : equipmentScore(item, strategy, style, slot);
      if (best == null || score > bestScore) {
        best = item;
        bestScore = score;
      }
    }
    return best;
  }

  static boolean ordinaryOwnedSlotFallbackForRegression(
      EquipmentInventorySlot slot, String displayName) {
    if (slot == null || displayName == null) {
      return false;
    }
    OwnedItem item =
        new OwnedItem(
            -1,
            displayName,
            1,
            KitItem.Status.BANK,
            slot.getSlotIdx(),
            null,
            Collections.singleton(normalize(displayName)));
    return findBestCompatibleOwnedSlot(
            Collections.singletonList(item), slot, null, CombatStyle.FLEXIBLE, false)
        == item;
  }

  private static long equipmentScore(
      OwnedItem item, TaskStrategy strategy, CombatStyle style, EquipmentInventorySlot slot) {
    ItemEquipmentStats stats = item.equipmentStats;
    if (stats == null) {
      return Long.MIN_VALUE;
    }
    long melee = meleeScore(stats, slot);
    long ranged = rangedScore(stats, slot);
    long magic = magicScore(stats, slot);
    long selected;
    switch (style) {
      case MAGIC:
        selected = magic;
        break;
      case RANGED:
        selected = ranged;
        break;
      case MELEE:
        selected = melee;
        break;
      case FLEXIBLE:
      default:
        selected = Math.max(melee, Math.max(ranged, magic));
        break;
    }
    if (strategy != null && slot != EquipmentInventorySlot.WEAPON) {
      int defence =
          stats.getDstab()
              + stats.getDslash()
              + stats.getDcrush()
              + stats.getDmagic()
              + stats.getDrange();
      switch (strategy.getArmourFocus()) {
        case PRAYER:
          selected += stats.getPrayer() * 60_000L;
          break;
        case DEFENCE:
          selected += defence * 1_500L;
          break;
        case HYBRID:
          selected += (melee + ranged + magic) / 5L;
          break;
        case DAMAGE:
        default:
          break;
      }
    }
    return selected + ownershipTieBreaker(item.status);
  }

  private static boolean isStyleCompatible(
      OwnedItem item, CombatStyle style, EquipmentInventorySlot slot) {
    if (item == null
        || style == CombatStyle.FLEXIBLE
        || (slot != EquipmentInventorySlot.WEAPON && slot != EquipmentInventorySlot.SHIELD)) {
      return true;
    }
    ItemEquipmentStats stats = item.equipmentStats;
    String name = item.normalizedName;
    if (slot == EquipmentInventorySlot.WEAPON) {
      switch (style) {
        case RANGED:
          return name.contains("bow")
              || name.contains("crossbow")
              || name.contains("blowpipe")
              || name.contains("ballista")
              || name.contains("atlatl")
              || name.contains("chinchompa")
              || (stats != null && (stats.getArange() > 0 || stats.getRstr() > 0));
        case MAGIC:
          return name.contains("staff")
              || name.contains("wand")
              || name.contains("trident")
              || name.contains("sceptre")
              || name.contains("sanguinesti")
              || name.contains("tumeken")
              || (stats != null && (stats.getAmagic() > 0 || stats.getMdmg() > 0));
        case MELEE:
          return name.contains("scythe")
              || name.contains("axe")
              || name.contains("sword")
              || name.contains("whip")
              || name.contains("rapier")
              || name.contains("mace")
              || name.contains("spear")
              || name.contains("hasta")
              || name.contains("halberd")
              || name.contains("fang")
              || name.contains("claws")
              || (stats != null
                  && (Math.max(stats.getAstab(), Math.max(stats.getAslash(), stats.getAcrush())) > 0
                      || stats.getStr() > 0));
        case FLEXIBLE:
        default:
          return true;
      }
    }
    switch (style) {
      case RANGED:
        return name.contains("buckler")
            || name.contains("dragonfire ward")
            || name.contains("odium ward")
            || name.contains("book of law")
            || (stats != null && (stats.getArange() > 0 || stats.getRstr() > 0));
      case MAGIC:
        return name.contains("elidinis")
            || name.contains("mage s book")
            || name.contains("book of darkness")
            || name.contains("arcane spirit shield")
            || name.contains("malediction ward")
            || name.contains("ancient wyvern shield")
            || (stats != null && (stats.getAmagic() > 0 || stats.getMdmg() > 0));
      case MELEE:
        return name.contains("defender")
            || name.contains("dragonfire shield")
            || name.contains("elysian spirit shield")
            || name.contains("spectral spirit shield")
            || name.contains("crystal shield")
            || name.contains("toktz ket xile")
            || (stats != null
                && (Math.max(stats.getAstab(), Math.max(stats.getAslash(), stats.getAcrush())) > 0
                    || stats.getStr() > 0));
      case FLEXIBLE:
      default:
        return true;
    }
  }

  private static boolean isDisallowedByPolicy(
      OwnedItem item, TaskStrategy strategy, EquipmentInventorySlot slot) {
    if (item == null || strategy == null || slot != EquipmentInventorySlot.WEAPON) {
      return false;
    }
    String name = item.normalizedName;
    if (name.contains("chinchompa") && !strategy.hasTag(TaskStrategy.MethodTag.CHINNING)) {
      return true;
    }
    if (strategy.getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT && !strategy.isBoss()) {
      return name.contains("scythe of vitur")
          || name.contains("soulreaper axe")
          || name.contains("tumeken s shadow")
          || name.contains("eye of ayak")
          || name.contains("sanguinesti staff");
    }
    if (strategy.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK) {
      return name.contains("scythe of vitur")
          || name.contains("twisted bow")
          || name.contains("tumeken s shadow")
          || name.contains("soulreaper axe")
          || name.contains("zaryte crossbow")
          || name.contains("bow of faerdhinen")
          || name.contains("eye of ayak")
          || name.contains("sanguinesti staff");
    }
    return false;
  }

  private static long meleeScore(ItemEquipmentStats stats, EquipmentInventorySlot slot) {
    int attack = Math.max(stats.getAstab(), Math.max(stats.getAslash(), stats.getAcrush()));
    int defence =
        stats.getDstab()
            + stats.getDslash()
            + stats.getDcrush()
            + stats.getDmagic()
            + stats.getDrange();
    long speedBonus =
        slot == EquipmentInventorySlot.WEAPON && stats.getAspeed() > 0
            ? Math.max(0, 8 - stats.getAspeed()) * 40L
            : 0L;
    return stats.getStr() * 10_000L
        + attack * 120L
        + stats.getPrayer() * 40L
        + defence
        + speedBonus;
  }

  private static long rangedScore(ItemEquipmentStats stats, EquipmentInventorySlot slot) {
    int defence =
        stats.getDstab()
            + stats.getDslash()
            + stats.getDcrush()
            + stats.getDmagic()
            + stats.getDrange();
    long speedBonus =
        slot == EquipmentInventorySlot.WEAPON && stats.getAspeed() > 0
            ? Math.max(0, 8 - stats.getAspeed()) * 40L
            : 0L;
    return stats.getRstr() * 10_000L
        + stats.getArange() * 120L
        + stats.getPrayer() * 40L
        + defence
        + speedBonus;
  }

  private static long magicScore(ItemEquipmentStats stats, EquipmentInventorySlot slot) {
    int defence =
        stats.getDstab()
            + stats.getDslash()
            + stats.getDcrush()
            + stats.getDmagic()
            + stats.getDrange();
    long speedBonus =
        slot == EquipmentInventorySlot.WEAPON && stats.getAspeed() > 0
            ? Math.max(0, 8 - stats.getAspeed()) * 40L
            : 0L;
    return Math.round(stats.getMdmg() * 10_000.0)
        + stats.getAmagic() * 120L
        + stats.getPrayer() * 40L
        + defence
        + speedBonus;
  }

  private static long ownershipTieBreaker(KitItem.Status status) {
    if (status == KitItem.Status.EQUIPPED) {
      return 3L;
    }
    if (status == KitItem.Status.INVENTORY) {
      return 2L;
    }
    if (status == KitItem.Status.BANK) {
      return 1L;
    }
    return 0L;
  }

  private static List<String> asList(String... values) {
    List<String> result = new ArrayList<>();
    if (values != null) {
      Collections.addAll(result, values);
    }
    return result;
  }

  private static boolean isTzKalZukInferno(String task, String location) {
    return normalize(task).equals("tzkal zuk") && normalize(location).equals("inferno");
  }

  private List<KitItem> buildInventoryLayout(
      String task,
      TaskStrategy strategy,
      String location,
      String travel,
      CombatStyle style,
      List<Requirement> requirements,
      boolean cannonSuggested,
      int recommendedFoodSlots,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      List<KitItem> equipmentLayout) {
    MethodRules methodRules = SlayerMethodRuleCatalog.resolve(task, location, strategy);
    List<KitItem> layout = new ArrayList<>();
    boolean analyzerOwnsTravelSlot = !isTzKalZukInferno(task, location);
    if (analyzerOwnsTravelSlot) {
      KitItem selectedTravel =
          chooseTeleport(task, location, travel, allOwned, bankScanned);
      addInventoryItem(layout, selectedTravel);
      if (normalize(task).equals("tormented demons")
          && (selectedTravel == null
              || !normalize(selectedTravel.getDisplayName())
                  .contains("guthixian temple teleport"))) {
        OwnedItem litLantern = firstExactDisplayOwned(allOwned, "sapphire lantern");
        addInventoryItem(
            layout,
            litLantern == null
                ? missingItem("Lit sapphire lantern", bankScanned)
                : litLantern.toLoadoutItem(1));
      }
    }
    for (Requirement requirement : requirements) {
      if (!requirement.equipment) {
        addInventoryItem(
            layout,
            chooseItem(
                requirement.displayName,
                requirement.quantity,
                allOwned,
                bankScanned,
                requirement.alternatives));
      }
    }
    boolean resolvedCannonMethod = cannonSuggested || methodRules.usesCannon();
    if (resolvedCannonMethod) {
      addInventoryItem(layout, chooseItem("Cannon base", 1, allOwned, bankScanned, "cannon base"));
      addInventoryItem(
          layout, chooseItem("Cannon stand", 1, allOwned, bankScanned, "cannon stand"));
      addInventoryItem(
          layout, chooseItem("Cannon barrels", 1, allOwned, bankScanned, "cannon barrels"));
      addInventoryItem(
          layout, chooseItem("Cannon furnace", 1, allOwned, bankScanned, "cannon furnace"));
      int cannonballs =
          methodRules.getCannonballQuantity() > 0
              ? methodRules.getCannonballQuantity()
              : SlayerMethodRuleCatalog.auditedCannonballQuantity(task);
      addInventoryItem(
          layout, chooseItem("Cannonballs", cannonballs, allOwned, bankScanned, "cannonball"));
    }
    boolean runePouchRequested =
        (strategy != null && strategy.needsRunePouch())
            || methodRules.requiresRunePouch()
            || style == CombatStyle.MAGIC && methodRules.includesRunePouchForMagic();
    int runePouchCapacity = runePouchRequested ? ownedRunePouchCapacity(allOwned) : 0;
    if (runePouchRequested) {
      addInventoryItem(
          layout,
          chooseItem("Rune pouch", 1, allOwned, bankScanned, "divine rune pouch", "rune pouch"));
    }
    if (methodRules.requiresBookOfDead()) {
      addInventoryItem(
          layout, chooseItem("Book of the dead", 1, allOwned, bankScanned, "book of the dead"));
    }
    RunePolicy.Resolution resolvedRunePackage =
        RunePolicy.resolve(
            methodRules.getPouchRunes(), ownedItemQuantities(allOwned));
    int pouchRuneSlotsRemaining = runePouchCapacity;
    for (RunePolicy.ResolvedRune rune : resolvedRunePackage.getRunes()) {
      if (pouchRuneSlotsRemaining > 0) {
        pouchRuneSlotsRemaining--;
        continue;
      }
      addInventoryItem(
          layout,
          chooseItem(
              rune.getName() + " runes",
              rune.getMinimumQuantity(),
              allOwned,
              bankScanned,
              normalize(rune.getName()) + " rune"));
    }
    java.util.Set<String> structuredPouchRunes = new java.util.LinkedHashSet<>();
    for (RunePolicy.ResolvedRune rune : resolvedRunePackage.getRunes()) {
      structuredPouchRunes.add(normalize(rune.getName()));
    }
    java.util.Set<String> unavailableStructuredRunes = new java.util.LinkedHashSet<>();
    for (String rune : resolvedRunePackage.getUnownedRequirements()) {
      unavailableStructuredRunes.add(normalize(rune));
    }
    boolean tormentedDemons = normalize(task).equals("tormented demons");
    boolean maggotKing = normalize(task).contains("maggot king");
    KitItem tormentedSecondaryWeapon = null;
    KitItem maggotCrushWeapon = null;
    for (MethodRules.RequiredItem required : methodRules.getRequiredItems()) {
      if (!achievementDiaries.allowsLoadoutReward(
          required.getDisplayName(), required.getAlternatives())) {
        continue;
      }
      if (isStructuredUtilityDuplicate(required, methodRules)) {
        continue;
      }
      if (isRuneRequirement(required)) {
        String requiredRune =
            normalize(required.getDisplayName()).replace(" runes", "").replace(" rune", "").trim();
        if (isCoveredByStructuredPouchRune(structuredPouchRunes, requiredRune)) {
          continue;
        }
        if (unavailableStructuredRunes.contains(requiredRune)) {
          continue;
        }
        if (pouchRuneSlotsRemaining > 0) {
          pouchRuneSlotsRemaining--;
          continue;
        }
      }
      List<String> requiredAlternatives = required.getAlternatives();
      if (maggotKing && maggotCrushWeapon != null) {
        if (skipMaggotKingRequirement(required.getDisplayName(), maggotCrushWeapon)) {
          continue;
        }
        requiredAlternatives =
            maggotKingSwitchAlternatives(
                required.getDisplayName(), maggotCrushWeapon, requiredAlternatives);
      }
      if (tormentedDemons && tormentedSecondaryWeapon != null) {
        if (skipTormentedRequirement(required.getDisplayName(), tormentedSecondaryWeapon)) {
          continue;
        }
        requiredAlternatives =
            tormentedSwitchAlternatives(
                required.getDisplayName(), tormentedSecondaryWeapon, requiredAlternatives);
      }
      KitItem requiredItem =
          chooseItem(
              required.getDisplayName(),
              required.getSlotCount() > 1 ? 1 : required.getQuantity(),
              allOwned,
              bankScanned,
              requiredAlternatives,
              required.getGroup() == MethodRules.InventoryGroup.SWITCH);
      if (required.isOwnedOnly() && (requiredItem == null || !requiredItem.hasItemId())) {
        continue;
      }
      if (tormentedDemons && required.getDisplayName().equals("Secondary weapon switch")) {
        tormentedSecondaryWeapon = requiredItem;
      }
      if (maggotKing && required.getDisplayName().equals("Crush punish weapon")) {
        maggotCrushWeapon = requiredItem;
      }
      if (required.getGroup() == MethodRules.InventoryGroup.SWITCH) {
        KitItem.SwitchStyle switchStyle =
            tormentedDemons
                    && isTormentedSecondaryRequirement(required.getDisplayName())
                    && isPurgingStaff(tormentedSecondaryWeapon)
                ? KitItem.SwitchStyle.MAGIC
                : inferSwitchStyle(required, style);
        requiredItem = requiredItem.asEquipmentSwitch(switchStyle);
        if (isAlreadyEquippedInPlan(requiredItem, equipmentLayout)) {
          continue;
        }
      }
      for (int slot = 0; slot < required.getSlotCount() && layout.size() < 28; slot++) {
        addInventoryItem(layout, requiredItem);
      }
    }
    if (tormentedDemons) {
      addInventoryItem(
          layout,
          tormentedSwitchAmmunition(tormentedSecondaryWeapon, strategy, allOwned, bankScanned));
    }
    if (methodRules.includesStyleBoost()) {
      TaskStrategy.CostPolicy potionCostPolicy =
          strategy == null ? TaskStrategy.CostPolicy.EFFICIENT : strategy.getCostPolicy();
      if (style == CombatStyle.MAGIC) {
        addInventoryItem(
            layout,
            chooseItem(
                "Magic boost",
                1,
                allOwned,
                bankScanned,
                PotionPolicy.magicBoostAlternatives()));
      } else if (style == CombatStyle.RANGED) {
        addInventoryItem(
            layout,
            chooseItem(
                "Ranging potion",
                1,
                allOwned,
                bankScanned,
                PotionPolicy.rangedBoostAlternatives(potionCostPolicy)));
      } else if (style == CombatStyle.FLEXIBLE) {
        addInventoryItem(
            layout,
            chooseItem(
                "Super combat potion",
                1,
                allOwned,
                bankScanned,
                PotionPolicy.meleeBoostAlternatives(potionCostPolicy)));
        addInventoryItem(
            layout,
            chooseItem(
                "Ranging potion",
                1,
                allOwned,
                bankScanned,
                PotionPolicy.rangedBoostAlternatives(potionCostPolicy)));
        if (hybridUsesCombatMagic(strategy)) {
          addInventoryItem(
              layout,
              chooseItem(
                  "Magic boost",
                  1,
                  allOwned,
                  bankScanned,
                  PotionPolicy.magicBoostAlternatives()));
        }
      } else {
        addInventoryItem(
            layout,
            chooseItem(
                "Super combat potion",
                1,
                allOwned,
                bankScanned,
                PotionPolicy.meleeBoostAlternatives(potionCostPolicy)));
      }
    }
    if (strategy != null && strategy.needsAntivenom()) {
      addInventoryItem(
          layout,
          chooseItem(
              PotionPolicy.EXTENDED_ANTIVENOM_DISPLAY,
              1,
              allOwned,
              bankScanned,
              PotionPolicy.antivenomAlternatives()));
    }
    if (strategy != null && strategy.needsStamina()) {
      addInventoryItem(
          layout,
          chooseItem(
              PotionPolicy.EXTENDED_STAMINA_DISPLAY,
              1,
              allOwned,
              bankScanned,
              PotionPolicy.staminaAlternatives()));
    }
    KitItem prayerRestore = chooseRestore(methodRules, allOwned, bankScanned);
    int prayerSlots = methodRules.resolveRestoreSlots(strategy);
    for (int i = 0; i < prayerSlots && layout.size() < 28; i++) {
      addInventoryItem(layout, prayerRestore);
    }
    KitItem food =
        chooseItem(
            methodRules.getFoodDisplayName(),
            1,
            allOwned,
            bankScanned,
            methodRules.getFoodAlternatives());
    int foodSlots = methodRules.resolveFoodSlots(strategy, recommendedFoodSlots);
    for (int i = 0; i < foodSlots && layout.size() < 28; i++) {
      layout.add(food);
    }
    int target =
        Math.min(
            methodRules.getInventoryTarget(), Math.max(0, 28 - methodRules.getReservedLootSlots()));
    if (methodRules.fillsRemainingWithRestore()) {
      while (layout.size() < target && layout.size() < 28) {
        layout.add(prayerRestore);
      }
    } else if (methodRules.fillsRemainingWithFood()) {
      while (layout.size() < target && layout.size() < 28) {
        layout.add(food);
      }
    }
    return enforceConcreteInventoryTarget(
        organizeInventoryLayout(layout, methodRules, analyzerOwnsTravelSlot),
        methodRules,
        prayerRestore,
        food);
  }

  private static List<String> maggotKingSwitchAlternatives(
      String displayName, KitItem crushWeapon, List<String> defaults) {
    String requirement = normalize(displayName);
    String weapon = normalize(crushWeapon == null ? "" : crushWeapon.getDisplayName());
    if (weapon.contains("soulreaper axe")) {
      if (requirement.equals("melee body switch")) {
        return list("l038");
      }
      if (requirement.equals("melee legs switch")) {
        return list("l039");
      }
    }
    return defaults;
  }

  private static boolean skipMaggotKingRequirement(
      String displayName, KitItem crushWeapon) {
    return normalize(displayName).equals("melee defender switch")
        && isTwoHandedMaggotKingWeapon(crushWeapon);
  }

  private static boolean isTwoHandedMaggotKingWeapon(KitItem crushWeapon) {
    String weapon = normalize(crushWeapon == null ? "" : crushWeapon.getDisplayName());
    return weapon.contains("scythe of vitur")
        || weapon.contains("soulreaper axe")
        || weapon.contains("abyssal bludgeon")
        || weapon.contains("dual macuahuitl");
  }

  private static List<String> tormentedSwitchAlternatives(
      String displayName, KitItem rangedWeapon, List<String> defaults) {
    String requirement = normalize(displayName);
    String weapon = normalize(rangedWeapon == null ? "" : rangedWeapon.getDisplayName());
    if (requirement.equals("secondary body switch")) {
      if (isPurgingStaff(rangedWeapon)) {
        return list("l040");
      }
      if (weapon.contains("bow of faerdhinen")) {
        return list("l041");
      }
      if (weapon.contains("eclipse atlatl")) {
        return list("l042");
      }
    }
    if (requirement.equals("secondary legs switch")) {
      if (isPurgingStaff(rangedWeapon)) {
        return list("l043");
      }
      if (weapon.contains("bow of faerdhinen")) {
        return list("l044");
      }
      if (weapon.contains("eclipse atlatl")) {
        return list("l045");
      }
    }
    return defaults;
  }

  private static List<String> araxxorSafeWeaponAlternatives() {
    return list("l046");
  }

  private static List<String> araxxorSafeAmmunitionAlternatives(String safeWeaponName) {
    String weapon = normalize(safeWeaponName);
    if (weapon.isEmpty()) {
      return java.util.Collections.emptyList();
    }
    if (weapon.contains("hunters sunlight crossbow")) {
      return list("l047");
    }
    if (weapon.contains("karil") && weapon.contains("crossbow")) {
      return list("l048");
    }
    if (weapon.contains("heavy ballista")) {
      return list("l049");
    }
    if (weapon.contains("crossbow")) {
      return list("l050");
    }
    return java.util.Collections.emptyList();
  }

  static List<String> araxxorSafeAmmunitionForRegression(String safeWeaponName) {
    return new ArrayList<>(araxxorSafeAmmunitionAlternatives(safeWeaponName));
  }

  private static boolean skipTormentedRequirement(
      String displayName, KitItem secondaryWeapon) {
    String requirement = normalize(displayName);
    if (requirement.equals("magic off hand switch")) {
      return !isPurgingStaff(secondaryWeapon);
    }
    if (requirement.equals("divine ranging potion")) {
      return isPurgingStaff(secondaryWeapon);
    }
    return false;
  }

  private static boolean isTormentedSecondaryRequirement(String displayName) {
    String requirement = normalize(displayName);
    return requirement.equals("secondary weapon switch")
        || requirement.equals("secondary body switch")
        || requirement.equals("secondary legs switch")
        || requirement.equals("magic off hand switch");
  }

  private static boolean isPurgingStaff(KitItem secondaryWeapon) {
    return secondaryWeapon != null
        && normalize(secondaryWeapon.getDisplayName()).contains("purging staff");
  }

  private static KitItem tormentedSwitchAmmunition(
      KitItem rangedWeapon,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      boolean bankScanned) {
    String weapon = normalize(rangedWeapon == null ? "" : rangedWeapon.getDisplayName());
    if (weapon.contains("purging staff")) {
      return null;
    }
    if (weapon.contains("toxic blowpipe") || weapon.contains("bow of faerdhinen")) {
      return null;
    }
    if (weapon.contains("eclipse atlatl")) {
      return recommendedAmmunitionItem(true, "Atlatl darts", allOwned, bankScanned, "atlatl dart")
          .asEquipmentSwitch(KitItem.SwitchStyle.RANGED);
    }
    if (weapon.contains("hunters sunlight crossbow")) {
      return recommendedAmmunitionItem(
              true,
              "Antler bolts",
              allOwned,
              bankScanned,
              "moonlight antler bolts",
              "sunlight antler bolts")
          .asEquipmentSwitch(KitItem.SwitchStyle.RANGED);
    }
    if (weapon.contains("crossbow")) {
      return recommendedAmmunitionItem(
              true,
              "Compatible bolts",
              allOwned,
              bankScanned,
              "ruby dragon bolts e",
              "diamond dragon bolts e",
              "dragon bolts",
              "ruby bolts e",
              "diamond bolts e",
              "amethyst broad bolts",
              "runite bolts",
              "broad bolts")
          .asEquipmentSwitch(KitItem.SwitchStyle.RANGED);
    }
    return bestOwnedStandardArrow(true, null, weapon, strategy, allOwned, bankScanned)
        .asEquipmentSwitch(KitItem.SwitchStyle.RANGED);
  }

  private static boolean isAlreadyEquippedInPlan(
      KitItem candidate, List<KitItem> equipmentLayout) {
    if (candidate == null || equipmentLayout == null) {
      return false;
    }
    for (KitItem equipped : equipmentLayout) {
      if (equipped == null) {
        continue;
      }
      if (candidate.hasItemId()
          && equipped.hasItemId()
          && candidate.getItemId() == equipped.getItemId()) {
        return true;
      }
      if (!candidate.hasItemId()
          && !equipped.hasItemId()
          && normalize(candidate.getDisplayName()).equals(normalize(equipped.getDisplayName()))) {
        return true;
      }
    }
    return false;
  }

  private static List<KitItem> enforceConcreteInventoryTarget(
      List<KitItem> source,
      MethodRules rules,
      KitItem prayerRestore,
      KitItem food) {
    if (source == null || source.isEmpty() || rules == null) {
      return source;
    }
    int target =
        Math.min(
            Math.max(0, rules.getInventoryTarget()),
            Math.max(0, 28 - rules.getReservedLootSlots()));
    if (target <= 0) {
      return source;
    }
    List<KitItem> concrete = new ArrayList<>();
    List<KitItem> unresolved = new ArrayList<>();
    for (KitItem item : source) {
      if (item == null) {
        continue;
      }
      if (item.hasItemId()) {
        concrete.add(item);
      } else {
        unresolved.add(item);
      }
    }
    if (!rules.fillsRemainingWithRestore() && !rules.fillsRemainingWithFood()) {
      return source;
    }
    KitItem preferredFiller = rules.fillsRemainingWithRestore() ? prayerRestore : food;
    KitItem secondaryFiller = rules.fillsRemainingWithRestore() ? food : prayerRestore;
    fillConcreteInventory(concrete, target, preferredFiller);
    fillConcreteInventory(concrete, target, secondaryFiller);
    List<KitItem> result =
        new ArrayList<>(Math.min(28, concrete.size() + unresolved.size()));
    result.addAll(concrete.subList(0, Math.min(28, concrete.size())));
    for (KitItem item : unresolved) {
      if (result.size() >= 28) {
        break;
      }
      result.add(item);
    }
    return result;
  }

  private static void fillConcreteInventory(
      List<KitItem> layout, int target, KitItem filler) {
    if (layout == null || filler == null || !filler.hasItemId()) {
      return;
    }
    while (layout.size() < target && layout.size() < 28) {
      layout.add(filler);
    }
  }

  private static int ownedRunePouchCapacity(List<OwnedItem> allOwned) {
    boolean regular = false;
    if (allOwned != null) {
      for (OwnedItem item : allOwned) {
        if (item == null) {
          continue;
        }
        String name = item.normalizedName;
        if (name.contains("divine rune pouch")) {
          return 4;
        }
        if (name.contains("rune pouch")) {
          regular = true;
        }
      }
    }
    return regular ? 3 : 0;
  }

  private static boolean isStructuredUtilityDuplicate(
      MethodRules.RequiredItem required, MethodRules rules) {
    if (required == null || rules == null) {
      return false;
    }
    String name = normalize(required.getDisplayName());
    if (rules.requiresRunePouch()
        && (name.equals("rune pouch") || name.equals("divine rune pouch"))) {
      return true;
    }
    return rules.requiresBookOfDead() && name.equals("book of the dead");
  }

  private static boolean isCoveredByStructuredPouchRune(
      java.util.Set<String> structuredPouchRunes, String requiredRune) {
    if (structuredPouchRunes == null || structuredPouchRunes.isEmpty()) {
      return false;
    }
    if (structuredPouchRunes.contains(requiredRune)) {
      return true;
    }
    if (structuredPouchRunes.contains("aether")
        && ("cosmic".equals(requiredRune) || "soul".equals(requiredRune))) {
      return true;
    }
    return false;
  }

  private static boolean isRuneRequirement(MethodRules.RequiredItem required) {
    if (required == null || required.getGroup() != MethodRules.InventoryGroup.RUNES_AMMO) {
      return false;
    }
    String name = normalize(required.getDisplayName());
    return name.endsWith(" rune") || name.endsWith(" runes");
  }

  private static KitItem chooseRestore(
      MethodRules rules, List<OwnedItem> allOwned, boolean bankScanned) {
    if (rules == null || !rules.hasRestorePolicy()) {
      return missingItem("Prayer restoration", bankScanned);
    }
    OwnedItem owned = fullestOwnedDose(allOwned, rules.getPrimaryRestoreFamily());
    if (owned == null
        && rules.allowsRestoreFallback()
        && !rules.getFallbackRestoreFamily().isEmpty()) {
      owned = fullestOwnedDose(allOwned, rules.getFallbackRestoreFamily());
    }
    return owned == null
        ? missingItem(displayPotionFamily(rules.getPrimaryRestoreFamily()), bankScanned)
        : owned.toLoadoutItem(1);
  }

  private static String displayPotionFamily(String family) {
    if (family == null || family.trim().isEmpty()) {
      return "Prayer restoration";
    }
    String value = family.trim();
    return Character.toUpperCase(value.charAt(0)) + value.substring(1) + "(4)";
  }

  private static OwnedItem fullestOwnedDose(List<OwnedItem> allOwned, String family) {
    String normalizedFamily = stripPotionDose(normalize(family));
    if (normalizedFamily.isEmpty()) {
      return null;
    }
    return firstExactDisplayOwned(
        allOwned,
        normalizedFamily + " 4",
        normalizedFamily + " 3",
        normalizedFamily + " 2",
        normalizedFamily + " 1");
  }

  private static String stripPotionDose(String value) {
    return value == null ? "" : value.replaceFirst("\\s+[1-4]$", "").trim();
  }

  private static List<KitItem> organizeInventoryLayout(
      List<KitItem> source, MethodRules rules, boolean sourceSlotZeroIsTravel) {
    Map<MethodRules.InventoryGroup, List<KitItem>> groups = new LinkedHashMap<>();
    for (MethodRules.InventoryGroup group : MethodRules.InventoryGroup.values()) {
      groups.put(group, new ArrayList<>());
    }
    for (int index = 0; index < source.size(); index++) {
      KitItem item = source.get(index);
      if (item == null) {
        continue;
      }
      MethodRules.InventoryGroup group =
          sourceSlotZeroIsTravel && index == 0
              ? MethodRules.InventoryGroup.TRAVEL
              : classifyInventoryItem(item, rules);
      groups.get(group).add(item.withInventoryGroup(group));
    }
    List<KitItem> organized = new ArrayList<>(source.size());
    addGroup(organized, groups, MethodRules.InventoryGroup.TRAVEL, rules);
    List<KitItem> utilities = groups.get(MethodRules.InventoryGroup.UTILITY);
    if (utilities != null && !utilities.isEmpty()) {
      List<KitItem> runePouches = new ArrayList<>();
      for (int index = utilities.size() - 1; index >= 0; index--) {
        KitItem utility = utilities.get(index);
        if (normalize(utility == null ? "" : utility.getDisplayName()).contains("rune pouch")) {
          runePouches.add(0, utility);
          utilities.remove(index);
        }
      }
      organized.addAll(runePouches);
    }
    addGroup(organized, groups, MethodRules.InventoryGroup.SWITCH, rules);
    addGroup(organized, groups, MethodRules.InventoryGroup.UTILITY, rules);
    addGroup(organized, groups, MethodRules.InventoryGroup.RUNES_AMMO, rules);
    addGroup(organized, groups, MethodRules.InventoryGroup.PROTECTION, rules);
    addGroup(organized, groups, MethodRules.InventoryGroup.BOOST, rules);
    addGroup(organized, groups, MethodRules.InventoryGroup.FOOD, rules);
    addGroup(organized, groups, MethodRules.InventoryGroup.RESTORE, rules);
    addGroup(organized, groups, MethodRules.InventoryGroup.OTHER, rules);
    return organized;
  }

  private static void addGroup(
      List<KitItem> destination,
      Map<MethodRules.InventoryGroup, List<KitItem>> groups,
      MethodRules.InventoryGroup group,
      MethodRules rules) {
    List<KitItem> items = groups.get(group);
    if (items == null || items.isEmpty()) {
      return;
    }
    items.sort(
        (left, right) -> {
          int leftPriority = inventoryAuthoredPriority(left, rules, group);
          int rightPriority = inventoryAuthoredPriority(right, rules, group);
          if (leftPriority != rightPriority) {
            return Integer.compare(leftPriority, rightPriority);
          }
          int family = inventoryVisualFamily(left).compareTo(inventoryVisualFamily(right));
          if (family != 0) {
            return family;
          }
          return normalize(left == null ? "" : left.getDisplayName())
              .compareTo(normalize(right == null ? "" : right.getDisplayName()));
        });
    if (isSingletonInventoryGroup(group)) {
      Set<String> seen = new HashSet<>();
      items.removeIf(item -> !seen.add(itemIdentity(item)));
    }
    destination.addAll(items);
  }

  private static boolean isSingletonInventoryGroup(MethodRules.InventoryGroup group) {
    return group == MethodRules.InventoryGroup.TRAVEL
        || group == MethodRules.InventoryGroup.SWITCH
        || group == MethodRules.InventoryGroup.UTILITY
        || group == MethodRules.InventoryGroup.RUNES_AMMO
        || group == MethodRules.InventoryGroup.PROTECTION;
  }

  private static String itemIdentity(KitItem item) {
    if (item == null) {
      return "null";
    }
    return item.hasItemId() ? "id:" + item.getItemId() : "name:" + normalize(item.getDisplayName());
  }

  private static int inventoryAuthoredPriority(
      KitItem item, MethodRules rules, MethodRules.InventoryGroup group) {
    String name = normalize(item == null ? "" : item.getDisplayName());
    if (name.contains("rune pouch")) {
      return -1000;
    }
    if (rules == null) {
      return Integer.MAX_VALUE;
    }
    int index = 0;
    for (MethodRules.RequiredItem required : rules.getRequiredItems()) {
      if (required.getGroup() != group) {
        index++;
        continue;
      }
      String display = normalize(required.getDisplayName());
      if (!display.isEmpty() && name.equals(display)) {
        return index;
      }
      for (String alternative : required.getAlternatives()) {
        String normalizedAlternative = normalize(alternative);
        if (!normalizedAlternative.isEmpty() && name.contains(normalizedAlternative)) {
          return index;
        }
      }
      index++;
    }
    return Integer.MAX_VALUE;
  }

  private static String inventoryVisualFamily(KitItem item) {
    if (item == null) {
      return "";
    }
    return stripPotionDose(normalize(item.getDisplayName())).replaceFirst("^divine\\s+", "").trim();
  }

  private static MethodRules.InventoryGroup classifyInventoryItem(
      KitItem item, MethodRules rules) {
    String name = normalize(item.getDisplayName());
    if (rules != null) {
      for (MethodRules.RequiredItem required : rules.getRequiredItems()) {
        if (name.equals(normalize(required.getDisplayName()))) {
          return required.getGroup();
        }
        for (String alternative : required.getAlternatives()) {
          if (name.contains(normalize(alternative))) {
            return required.getGroup();
          }
        }
      }
    }
    if (name.contains("rune pouch")
        || name.contains("book of the dead")
        || name.contains("herb sack")
        || name.contains("gem bag")
        || name.contains("bonecrusher")
        || name.contains("goading potion")) {
      return MethodRules.InventoryGroup.UTILITY;
    }
    if (name.contains("sceptre") || name.contains("switch")) {
      return MethodRules.InventoryGroup.SWITCH;
    }
    if (name.contains(" rune")
        || name.endsWith("runes")
        || name.contains("cannon")
        || name.contains("dart")
        || name.contains("arrow")
        || name.contains("bolt")) {
      return MethodRules.InventoryGroup.RUNES_AMMO;
    }
    if (name.contains("saradomin brew")) {
      return MethodRules.InventoryGroup.FOOD;
    }
    if (name.contains("heart")
        || name.contains("combat potion")
        || name.contains("ranging potion")
        || name.contains("magic potion")
        || name.contains("bastion potion")
        || name.contains("battlemage potion")
        || name.contains("ancient brew")
        || name.contains("forgotten brew")) {
      return MethodRules.InventoryGroup.BOOST;
    }
    if (name.contains("antivenom")
        || name.contains("anti venom")
        || name.contains("antipoison")
        || name.contains("stamina")
        || name.contains("antifire")) {
      return MethodRules.InventoryGroup.PROTECTION;
    }
    if (name.contains("super restore")
        || name.contains("prayer potion")
        || name.contains("prayer restoration")
        || name.contains("prayer regeneration")) {
      return MethodRules.InventoryGroup.RESTORE;
    }
    if (isFoodName(name)) {
      return MethodRules.InventoryGroup.FOOD;
    }
    return MethodRules.InventoryGroup.OTHER;
  }

  private static KitItem.SwitchStyle inferSwitchStyle(
      MethodRules.RequiredItem required, CombatStyle fallbackStyle) {
    if (required == null) {
      return switchStyle(fallbackStyle);
    }
    String description = normalize(required.getDisplayName());
    for (String alternative : required.getAlternatives()) {
      description += " " + normalize(alternative);
    }
    if (description.contains("blowpipe")
        || description.contains("shortbow")
        || description.contains("longbow")
        || description.contains("crossbow")
        || description.contains("ballista")
        || description.contains(" ranged ")
        || description.startsWith("ranged ")
        || description.contains(" range switch")
        || description.contains("masori")
        || description.contains("armadyl")
        || description.contains("karil")) {
      return KitItem.SwitchStyle.RANGED;
    }
    if (description.contains(" magic ")
        || description.startsWith("magic ")
        || description.contains(" mage ")
        || description.startsWith("mage ")
        || description.contains("magicks")
        || description.contains("ancient")
        || description.contains("sceptre")
        || description.contains("staff")
        || description.contains("wand")
        || description.contains("ancestral")
        || description.contains("virtus")
        || description.contains("ahrim")
        || description.contains("bloodbark")) {
      return KitItem.SwitchStyle.MAGIC;
    }
    if (description.contains(" melee ")
        || description.startsWith("melee ")
        || description.contains("scythe")
        || description.contains("godsword")
        || description.contains("whip")
        || description.contains("claws")
        || description.contains("defender")
        || description.contains("torva")
        || description.contains("bandos")) {
      return KitItem.SwitchStyle.MELEE;
    }
    return switchStyle(fallbackStyle);
  }

  private static KitItem.SwitchStyle switchStyle(CombatStyle style) {
    if (style == CombatStyle.MAGIC) {
      return KitItem.SwitchStyle.MAGIC;
    }
    if (style == CombatStyle.RANGED) {
      return KitItem.SwitchStyle.RANGED;
    }
    if (style == CombatStyle.MELEE) {
      return KitItem.SwitchStyle.MELEE;
    }
    return KitItem.SwitchStyle.OTHER;
  }

  private static boolean isFoodName(String name) {
    return name.contains("anglerfish")
        || name.contains("manta ray")
        || name.contains("dark crab")
        || name.contains("shark")
        || name.contains("sea turtle")
        || name.contains("karambwan")
        || name.contains("monkfish")
        || name.contains("high healing food");
  }

  private List<KitItem> buildOptionalLayout(
      String task,
      TaskStrategy strategy,
      List<OwnedItem> allOwned,
      OwnedItem selectedWeapon) {
    List<KitItem> optional = new ArrayList<>();
    addBlowpipeDartRecommendation(optional, strategy, selectedWeapon, allOwned);
    if (strategy != null) {
      for (String itemName : strategy.getOptionalItemPriorities()) {
        if (!achievementDiaries.allowsLoadoutReward(
            itemName, Collections.singletonList(itemName))) {
          continue;
        }
        addOwnedOptional(optional, allOwned, itemName);
      }
    }
    addOwnedOptional(optional, allOwned, "bracelet of slaughter");
    addOwnedOptional(optional, allOwned, "expeditious bracelet");
    addOwnedOptional(optional, allOwned, "slayer ring");
    if (achievementDiaries.unlocksAshSanctifier() && isAshSanctifierTask(task)) {
      addOwnedOptional(optional, allOwned, "ash sanctifier");
    }
    if (achievementDiaries.unlocksBonecrusher()
        && strategy != null
        && !strategy.isBoss()
        && isBonecrusherTask(task)) {
      addOwnedOptional(optional, allOwned, "bonecrusher");
    }
    addOwnedOptional(optional, allOwned, "herb sack");
    addOwnedOptional(optional, allOwned, "gem bag");
    return deduplicateItems(optional);
  }

  private static boolean isAshSanctifierTask(String taskName) {
    String task = normalize(taskName);
    return task.contains("demon")
        || task.contains("hellhound")
        || task.contains("bloodveld")
        || task.contains("nechryael")
        || task.contains("smoke devil")
        || task.contains("pyrefiend")
        || task.contains("fiend")
        || task.contains("cerberus");
  }

  private static boolean isBonecrusherTask(String taskName) {
    String task = normalize(taskName);
    return task.contains("dragon")
        || task.contains("wyvern")
        || task.contains("wyrm")
        || task.contains("drake")
        || task.contains("hydra")
        || task.contains("dagannoth")
        || task.contains("kalphite")
        || task.contains("basilisk")
        || task.contains("giant")
        || task.contains("troll")
        || task.contains("bat")
        || task.contains("mole")
        || task.contains("ankou");
  }

  private void addBlowpipeDartRecommendation(
      List<KitItem> destination,
      TaskStrategy strategy,
      OwnedItem selectedWeapon,
      List<OwnedItem> allOwned) {
    if (destination == null || selectedWeapon == null || !selectedWeapon.matchesName("blowpipe")) {
      return;
    }
    OwnedItem darts =
        strategy != null && strategy.getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT
            ? chooseEfficientBlowpipeDarts(allOwned)
            : firstExactOwned(
                allOwned, "dragon dart", "amethyst dart", "rune dart", "adamant dart");
    if (darts != null) {
      destination.add(darts.toLoadoutItem(1000));
    }
  }

  private OwnedItem chooseEfficientBlowpipeDarts(List<OwnedItem> allOwned) {
    OwnedItem amethyst = firstExactOwned(allOwned, "amethyst dart");
    OwnedItem rune = firstExactOwned(allOwned, "rune dart");
    if (amethyst != null && rune != null) {
      int amethystPrice = safeItemPrice(amethyst);
      int runePrice = safeItemPrice(rune);
      if (amethystPrice > 0 && runePrice > 0) {
        return amethystPrice <= runePrice ? amethyst : rune;
      }
      return amethyst;
    }
    if (amethyst != null) {
      return amethyst;
    }
    if (rune != null) {
      return rune;
    }
    return firstExactOwned(allOwned, "adamant dart");
  }

  private int safeItemPrice(OwnedItem item) {
    if (item == null || itemManager == null) {
      return 0;
    }
    try {
      return Math.max(0, itemManager.getItemPrice(item.itemId));
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private static OwnedItem firstExactOwned(List<OwnedItem> allOwned, String... exactNames) {
    if (allOwned == null || exactNames == null) {
      return null;
    }
    for (String exactName : exactNames) {
      for (OwnedItem item : allOwned) {
        if (item.matchesExactDisplayName(exactName)) {
          return item;
        }
      }
      for (OwnedItem item : allOwned) {
        if (item.matchesExactName(exactName)) {
          return item;
        }
      }
    }
    return null;
  }

  private static List<KitItem> deduplicateItems(List<KitItem> source) {
    List<KitItem> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (KitItem item : source) {
      if (item == null) {
        continue;
      }
      String key =
          item.hasItemId() ? "id:" + item.getItemId() : "name:" + normalize(item.getDisplayName());
      if (seen.add(key)) {
        result.add(item);
      }
    }
    return result;
  }

  private static void addOwnedOptional(
      List<KitItem> destination, List<OwnedItem> allOwned, String... alternatives) {
    OwnedItem owned = findPreferred(allOwned, alternatives);
    if (owned != null) {
      destination.add(owned.toLoadoutItem(1));
    }
  }

  private KitItem chooseTeleport(
      String task, String location, String travel, List<OwnedItem> allOwned, boolean bankScanned) {
    String combined = location + " " + travel;
    String normalizedTask = normalize(task);
    if (normalizedTask.equals("royal titans")) {
      return chooseItem(
          "Giantsoul amulet",
          1,
          allOwned,
          bankScanned,
          "giantsoul amulet",
          "dramen staff",
          "lunar staff");
    }
    if (normalizedTask.equals("tormented demons")) {
      OwnedItem direct = firstExactDisplayOwned(allOwned, "guthixian temple teleport");
      return direct == null
          ? chooseItem(
              "Games necklace / POH jewellery box", 1, allOwned, bankScanned, "games necklace")
          : direct.toLoadoutItem(1);
    }
    List<TravelRoutes.Option> reviewedOptions =
        TravelRoutes.fallbacksFor(location);
    if (!reviewedOptions.isEmpty()) {
      List<String> alternatives = new ArrayList<>();
      for (TravelRoutes.Option option : reviewedOptions) {
        if (achievementDiaries.allowsTravelItem(option.getItemFamily())) {
          alternatives.add(option.getItemFamily());
        }
      }
      if (alternatives.isEmpty()) {
        return null;
      }
      TravelRoutes.Option preferred = reviewedOptions.get(0);
      for (TravelRoutes.Option option : reviewedOptions) {
        if (achievementDiaries.allowsTravelItem(option.getItemFamily())) {
          preferred = option;
          break;
        }
      }
      return chooseItem(
          preferred.getItemFamily() + " -> " + preferred.getDestination(),
          1,
          allOwned,
          bankScanned,
          alternatives);
    }
    if (combined.contains("cowbell")) {
      return chooseItem("Cowbell amulet", 1, allOwned, bankScanned, "cowbell amulet");
    }
    if (combined.contains("giantsoul")) {
      return chooseItem(
          "Giantsoul amulet",
          1,
          allOwned,
          bankScanned,
          "giantsoul amulet",
          "dramen staff",
          "lunar staff");
    }
    if (combined.contains("guthixian temple")) {
      return chooseItem(
          "Guthixian temple teleport",
          1,
          allOwned,
          bankScanned,
          "guthixian temple teleport",
          "games necklace");
    }
    if (combined.contains("key master")) {
      return chooseItem(
          "Key master teleport", 1, allOwned, bankScanned, "key master teleport", "games necklace");
    }
    if (combined.contains("barrows teleport")) {
      return chooseItem(
          "Barrows teleport",
          1,
          allOwned,
          bankScanned,
          "barrows teleport",
          "morytania legs 3",
          "morytania legs 4");
    }
    if (combined.contains("ring of shadows") || combined.contains("ancient vault")) {
      return chooseItem("Ring of shadows", 1, allOwned, bankScanned, "ring of shadows");
    }
    if (combined.contains("burning amulet") || combined.contains("wilderness obelisk")) {
      return chooseItem(
          "Wilderness travel",
          1,
          allOwned,
          bankScanned,
          "burning amulet",
          "royal seed pod",
          "seed pod");
    }
    if (combined.contains("digsite pendant")) {
      return chooseItem("Digsite pendant", 1, allOwned, bankScanned, "digsite pendant");
    }
    if (combined.contains("drakan")) {
      return chooseItem("Drakan's medallion", 1, allOwned, bankScanned, "drakan s medallion");
    }
    if (combined.contains("varrock teleport")) {
      return chooseItem(
          "Varrock teleport", 1, allOwned, bankScanned, "varrock teleport", "varrock tablet");
    }
    if (combined.contains("amulet of glory")) {
      return chooseItem("Amulet of glory", 1, allOwned, bankScanned, "amulet of glory");
    }
    if (combined.contains("ring of dueling")) {
      return chooseItem("Ring of dueling", 1, allOwned, bankScanned, "ring of dueling");
    }
    if (combined.contains("skills necklace")) {
      return chooseItem("Skills necklace", 1, allOwned, bankScanned, "skills necklace");
    }
    if (combined.contains("combat bracelet")) {
      return chooseItem("Combat bracelet", 1, allOwned, bankScanned, "combat bracelet");
    }
    if (combined.contains("ectophial")) {
      return chooseItem("Ectophial", 1, allOwned, bankScanned, "ectophial");
    }
    if (combined.contains("mort ton teleport")) {
      return chooseItem("Mort'ton teleport", 1, allOwned, bankScanned, "mort ton teleport");
    }
    if (combined.contains("pollnivneach teleport")) {
      return chooseItem("Pollnivneach teleport", 1, allOwned, bankScanned, "pollnivneach teleport");
    }
    if (combined.contains("zul andra")) {
      return chooseItem("Zul-andra teleport", 1, allOwned, bankScanned, "zul andra teleport");
    }
    if (combined.contains("catacomb") || combined.contains("xeric")) {
      return chooseItem("Xeric's talisman", 1, allOwned, bankScanned, "xeric s talisman");
    }
    if (combined.contains("karuulm") || combined.contains("rada")) {
      return chooseItem("Rada's blessing", 1, allOwned, bankScanned, "rada s blessing");
    }
    if (combined.contains("fremennik") || combined.contains("slayer cave")) {
      return chooseItem("Slayer ring", 1, allOwned, bankScanned, "slayer ring");
    }
    if (combined.contains("fairy ring")) {
      if (achievementDiaries.hasTier("lumbridge", 4)) {
        return null;
      }
      return chooseItem(
          "Dramen or lunar staff", 1, allOwned, bankScanned, "lunar staff", "dramen staff");
    }
    return chooseItem(
        "Teleport out",
        1,
        allOwned,
        bankScanned,
        "max cape",
        "construction cape",
        "teleport to house",
        "house tab",
        "slayer ring",
        "ring of dueling",
        "games necklace");
  }

  private static OwnedItem firstExactDisplayOwned(List<OwnedItem> allOwned, String... exactNames) {
    if (allOwned == null || exactNames == null) {
      return null;
    }
    for (String exactName : exactNames) {
      for (OwnedItem item : allOwned) {
        if (item != null && item.matchesExactDisplayName(exactName)) {
          return item;
        }
      }
    }
    return null;
  }

  private static KitItem chooseItem(
      String displayName,
      int quantity,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      List<String> alternatives) {
    return chooseItem(displayName, quantity, allOwned, bankScanned, alternatives, false);
  }

  private static KitItem chooseItem(
      String displayName,
      int quantity,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      List<String> alternatives,
      boolean equipmentProgression) {
    OwnedItem owned = findPreferredInventoryItem(allOwned, alternatives, equipmentProgression);
    return owned == null
        ? missingItem(displayName, quantity, bankScanned)
        : owned.toLoadoutItem(quantity);
  }

  private static OwnedItem findPreferredInventoryItem(
      List<OwnedItem> allOwned, List<String> alternatives) {
    return findPreferredInventoryItem(allOwned, alternatives, false);
  }

  private static OwnedItem findPreferredInventoryItem(
      List<OwnedItem> allOwned, List<String> alternatives, boolean equipmentProgression) {
    if (alternatives == null) {
      return null;
    }
    if (equipmentProgression) {
      return findPreferredInventoryEquipment(allOwned, alternatives);
    }
    List<String> potionFamilies = new ArrayList<>();
    List<String> nonPotionAlternatives = new ArrayList<>();
    for (String alternative : alternatives) {
      String normalized = normalize(alternative);
      if (normalized.isEmpty()) {
        continue;
      }
      if (isPotionFamilyName(normalized)) {
        break;
      }
      OwnedItem exact = firstExactDisplayOwned(allOwned, normalized);
      if (exact != null) {
        return exact;
      }
    }
    for (String alternative : alternatives) {
      String normalized = normalize(alternative);
      if (normalized.isEmpty()) {
        continue;
      }
      if (hasExplicitPotionDose(normalized)) {
        OwnedItem exact = firstExactDisplayOwned(allOwned, normalized);
        if (exact != null) {
          return exact;
        }
      } else if (isPotionFamilyName(normalized)) {
        potionFamilies.add(stripPotionDose(normalized));
      } else {
        nonPotionAlternatives.add(normalized);
      }
    }
    OwnedItem bestPotion = null;
    int bestUsefulDoseUnits = -1;
    int bestFamilyPreference = Integer.MAX_VALUE;
    for (int familyIndex = 0; familyIndex < potionFamilies.size(); familyIndex++) {
      String family = potionFamilies.get(familyIndex);
      for (int dose = 4; dose >= 1; dose--) {
        OwnedItem owned = firstExactDisplayOwned(allOwned, family + " " + dose);
        if (owned == null) {
          continue;
        }
        int usefulDoseUnits = PotionPolicy.effectiveDoseUnits(family, dose);
        if (usefulDoseUnits > bestUsefulDoseUnits
            || (usefulDoseUnits == bestUsefulDoseUnits && familyIndex < bestFamilyPreference)) {
          bestPotion = owned;
          bestUsefulDoseUnits = usefulDoseUnits;
          bestFamilyPreference = familyIndex;
        }
        break;
      }
    }
    if (bestPotion != null) {
      return bestPotion;
    }
    for (String alternative : nonPotionAlternatives) {
      OwnedItem exact = firstExactDisplayOwned(allOwned, alternative);
      if (exact != null) {
        return exact;
      }
    }
    return findPreferred(allOwned, alternatives);
  }

  private static OwnedItem findPreferredInventoryEquipment(
      List<OwnedItem> allOwned, List<String> progression) {
    if (allOwned == null || progression == null) {
      return null;
    }
    for (String tier : progression) {
      String normalizedTier = normalize(tier);
      if (normalizedTier.isEmpty()) {
        continue;
      }
      OwnedItem exact = firstExactDisplayOwned(allOwned, normalizedTier);
      if (exact != null) {
        return exact;
      }
      for (OwnedItem item : allOwned) {
        if (item != null && item.matchesEquipmentProgression(normalizedTier)) {
          return item;
        }
      }
    }
    return null;
  }

  private static boolean hasExplicitPotionDose(String name) {
    return isPotionFamilyName(name) && name.matches(".*\\s[1-4]$");
  }

  private static boolean isPotionFamilyName(String name) {
    return name.contains("potion")
        || name.contains("super restore")
        || name.contains("brew")
        || name.contains("serum")
        || name.contains("antidote")
        || name.contains("antivenom")
        || name.contains("anti venom")
        || name.contains("antipoison");
  }

  private static KitItem chooseItem(
      String displayName,
      int quantity,
      List<OwnedItem> allOwned,
      boolean bankScanned,
      String... alternatives) {
    List<String> values = new ArrayList<>();
    Collections.addAll(values, alternatives);
    return chooseItem(displayName, quantity, allOwned, bankScanned, values);
  }

  private static void addInventoryItem(List<KitItem> layout, KitItem item) {
    if (item != null && layout.size() < 28) {
      layout.add(item);
    }
  }

  private static KitItem missingItem(String displayName, boolean bankScanned) {
    return missingItem(displayName, 1, bankScanned);
  }

  private static KitItem missingItem(
      String displayName, int quantity, boolean bankScanned) {
    return new KitItem(
        displayName,
        -1,
        Math.max(1, quantity),
        bankScanned ? KitItem.Status.MISSING : KitItem.Status.UNKNOWN);
  }

  private static Requirement requirementForSlot(
      List<Requirement> requirements, EquipmentInventorySlot slot) {
    for (Requirement requirement : requirements) {
      if (!requirement.equipment) {
        continue;
      }
      String name = normalize(requirement.displayName);
      if (slot == EquipmentInventorySlot.HEAD
          && (name.contains("earmuff")
              || name.contains("face mask")
              || name.contains("nose peg")
              || name.contains("spiny helmet")
              || name.contains("goggle"))) {
        return requirement;
      }
      if (slot == EquipmentInventorySlot.AMULET && name.contains("witchwood")) {
        return requirement;
      }
      if (slot == EquipmentInventorySlot.BOOTS && name.contains("boots")) {
        return requirement;
      }
      if (slot == EquipmentInventorySlot.GLOVES && name.contains("gloves")) {
        return requirement;
      }
      if (slot == EquipmentInventorySlot.SHIELD
          && (name.contains("shield") || name.contains("bug lantern"))) {
        return requirement;
      }
      if (slot == EquipmentInventorySlot.WEAPON
          && (name.contains("weapon") || name.contains("staff"))) {
        return requirement;
      }
      if (slot == EquipmentInventorySlot.AMMO
          && (name.contains("ammunition") || name.contains("bolts") || name.contains("arrows"))) {
        return requirement;
      }
    }
    return null;
  }

  private static OwnedItem findEquippedSlot(List<OwnedItem> equipped, EquipmentInventorySlot slot) {
    int slotIndex = slot.getSlotIdx();
    for (OwnedItem item : equipped) {
      if (item.equipmentSlot == slotIndex) {
        return item;
      }
    }
    return null;
  }

  private static OwnedItem findPreferred(List<OwnedItem> owned, List<String> alternatives) {
    if (owned == null || alternatives == null) {
      return null;
    }
    for (String alternative : alternatives) {
      String fragment = normalize(alternative);
      if (fragment.isEmpty()) {
        continue;
      }
      for (OwnedItem item : owned) {
        if (item != null && item.matchesExactDisplayName(fragment)) {
          return item;
        }
      }
      for (OwnedItem item : owned) {
        if (item != null && item.matchesName(fragment)) {
          return item;
        }
      }
    }
    return null;
  }

  private static OwnedItem findPreferred(List<OwnedItem> owned, String... alternatives) {
    List<String> values = new ArrayList<>();
    Collections.addAll(values, alternatives);
    return findPreferred(owned, values);
  }

  private static String[] weaponFragments(CombatStyle style) {
    if (style == CombatStyle.MAGIC) {
      return array("a004");
    }
    if (style == CombatStyle.RANGED) {
      return array("a005");
    }
    return array("a006");
  }

  private static String[] bodyFragments(CombatStyle style) {
    if (style == CombatStyle.MAGIC) {
      return array("a007");
    }
    if (style == CombatStyle.RANGED) {
      return array("a008");
    }
    return array("a009");
  }

  private static String[] legsFragments(CombatStyle style) {
    if (style == CombatStyle.MAGIC) {
      return array("a010");
    }
    if (style == CombatStyle.RANGED) {
      return array("a011");
    }
    return array("a012");
  }

  private static String[] shieldFragments(CombatStyle style) {
    if (style == CombatStyle.MAGIC) {
      return array("a013");
    }
    if (style == CombatStyle.RANGED) {
      return array("a014");
    }
    return array("a015");
  }

  private static String[] bootsFragments(CombatStyle style) {
    if (style == CombatStyle.MAGIC) {
      return array("a016");
    }
    if (style == CombatStyle.RANGED) {
      return array("a017");
    }
    return array("a018");
  }

  private static String[] ringFragments(CombatStyle style) {
    if (style == CombatStyle.MAGIC) {
      return array("a019");
    }
    if (style == CombatStyle.RANGED) {
      return array("a020");
    }
    return array("a021");
  }

  private static String buildEquipmentText(
      TaskStrategy strategy, CombatStyle style, List<Requirement> requirements) {
    List<String> parts = new ArrayList<>();
    parts.add("Slayer helmet or black mask");
    if (strategy != null) {
      parts.add(strategy.getMethod());
    } else {
      switch (style) {
        case MAGIC:
          parts.add("magic-damage weapon and prayer/magic gear");
          break;
        case RANGED:
          parts.add("ranged weapon, ammo, and ranged armour");
          break;
        case MELEE:
          parts.add("melee weapon and strength gear");
          break;
        case FLEXIBLE:
        default:
          parts.add("task-appropriate combat switches");
          break;
      }
    }
    for (Requirement requirement : requirements) {
      if (requirement.equipment) {
        parts.add(requirement.displayName);
      }
    }
    return join(parts, "; ");
  }

  private static String buildInventoryText(
      TaskStrategy strategy,
      CombatStyle style,
      List<Requirement> requirements,
      boolean cannonSuggested,
      int recommendedFoodSlots) {
    List<String> parts = new ArrayList<>();
    String foodText =
        recommendedFoodSlots > 0
            ? " and " + recommendedFoodSlots + " food slot" + (recommendedFoodSlots == 1 ? "" : "s")
            : "";
    switch (style) {
      case MAGIC:
        parts.add("runes or rune pouch");
        parts.add("Magic boost and prayer potions" + foodText);
        break;
      case RANGED:
        parts.add("ammo");
        parts.add("ranging/prayer potions" + foodText);
        break;
      case MELEE:
        parts.add("super combat/prayer potions" + foodText);
        break;
      case FLEXIBLE:
      default:
        parts.add("combat-switch potions and prayer supplies" + foodText);
        break;
    }
    if (recommendedFoodSlots == 0
        && strategy != null
        && (strategy.getDamageProfile() == TaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED
            || strategy.getDamageProfile()
                == TaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING)) {
      parts.add(
          "No food: the reviewed method expects zero incoming damage "
              + "while its protection method is maintained");
    }
    if (strategy != null && strategy.needsAntivenom()) {
      parts.add("antivenom");
    }
    if (strategy != null && strategy.needsStamina()) {
      parts.add("extended stamina potion (regular stamina fallback)");
    }
    if (cannonSuggested) {
      parts.add("multicannon pieces and cannonballs");
    }
    for (Requirement requirement : requirements) {
      if (!requirement.equipment) {
        parts.add(requirement.displayName);
      }
    }
    return join(parts, "; ");
  }

  private static String buildOwnedText(
      CombatStyle style,
      List<Requirement> requirements,
      boolean cannonSuggested,
      Set<String> inventory,
      Set<String> equipment,
      Set<String> bank,
      boolean bankScanned) {
    List<String> status = new ArrayList<>();
    status.add(styleStatus(style, equipment));
    for (Requirement requirement : requirements) {
      status.add(
          requirement.displayName
              + ": "
              + availability(requirement, inventory, equipment, bank, bankScanned));
    }
    if (cannonSuggested) {
      status.add("Cannon: " + cannonAvailability(inventory, equipment, bank, bankScanned));
    }
    if (!bankScanned) {
      status.add("Bank not scanned — open it once");
    } else {
      status.add("Bank cache ready");
    }
    return join(status, "; ");
  }

  private static String availability(
      Requirement requirement,
      Set<String> inventory,
      Set<String> equipment,
      Set<String> bank,
      boolean bankScanned) {
    if (containsAny(equipment, requirement.alternatives)) {
      return "equipped";
    }
    if (containsAny(inventory, requirement.alternatives)) {
      return "carried";
    }
    if (containsAny(bank, requirement.alternatives)) {
      return "in bank";
    }
    return bankScanned ? "missing" : "not carried; bank unknown";
  }

  private static boolean hybridUsesCombatMagic(TaskStrategy strategy) {
    if (strategy == null) {
      return false;
    }
    String text = normalize(strategy.getMethod() + " " + strategy.getRationale());
    return text.contains("magic")
        || text.contains("mage")
        || text.contains("powered staff")
        || text.contains("tumeken")
        || text.contains("trident");
  }

  private static String cannonAvailability(
      Set<String> inventory, Set<String> equipment, Set<String> bank, boolean bankScanned) {
    Set<String> available = new HashSet<>();
    available.addAll(inventory);
    available.addAll(equipment);
    available.addAll(bank);
    boolean base = containsText(available, "cannon base");
    boolean stand = containsText(available, "cannon stand");
    boolean barrels = containsText(available, "cannon barrels");
    boolean furnace = containsText(available, "cannon furnace");
    boolean cannonballs = containsText(available, "cannonball");
    if (base && stand && barrels && furnace && cannonballs) {
      return "all pieces and ammo found";
    }
    if (!bankScanned) {
      return "not fully carried; bank unknown";
    }
    List<String> missing = new ArrayList<>();
    if (!(base && stand && barrels && furnace)) {
      missing.add("pieces");
    }
    if (!cannonballs) {
      missing.add("cannonballs");
    }
    return "missing " + join(missing, " and ");
  }

  private static String styleStatus(CombatStyle style, Set<String> equipment) {
    if (style == CombatStyle.FLEXIBLE) {
      return equipment.isEmpty() ? "Equipment scan unavailable" : "Equipped setup detected";
    }
    boolean detected;
    switch (style) {
      case MAGIC:
        detected =
            containsAnyText(
                equipment,
                "staff",
                "wand",
                "trident",
                "sceptre",
                "sanguinesti",
                "tumeken",
                "ancestral",
                "virtus",
                "ahrim",
                "occult");
        break;
      case RANGED:
        detected =
            containsAnyText(
                equipment,
                "bow",
                "crossbow",
                "blowpipe",
                "atlatl",
                "masori",
                "armadyl",
                "karil",
                "ava",
                "quiver");
        break;
      case MELEE:
      default:
        detected =
            containsAnyText(
                equipment,
                "scimitar",
                "sword",
                "whip",
                "rapier",
                "mace",
                "axe",
                "lance",
                "scythe",
                "fang",
                "torva",
                "bandos",
                "defender");
        break;
    }
    return detected
        ? "Equipped " + style.label + " setup detected"
        : "No clear " + style.label + " setup equipped";
  }

  private List<Requirement> requirementsFor(
      String task,
      String location,
      CombatStyle style,
      int remainingKills,
      boolean desertEliteDiaryComplete) {
    List<Requirement> requirements = new ArrayList<>();
    String combined = task + " " + location;
    if (requiresKalphiteQueenRopesForRegression(task, location, desertEliteDiaryComplete)) {
      requirements.add(inventoryRequirement(2, "Rope", "rope"));
    }
    if (requiresGenericRockHammerForRegression(task)) {
      requirements.add(inventoryRequirement("Rock hammer", "rock hammer"));
    }
    if (task.contains("lizard") && !task.contains("lizardmen")) {
      requirements.add(
          inventoryRequirement(
              consumableFinisherQuantity(remainingKills), "Ice cooler", "ice cooler"));
    }
    if (task.contains("rockslug")) {
      requirements.add(
          inventoryRequirement(
              consumableFinisherQuantity(remainingKills), "Bag of salt", "bag of salt"));
    }
    if (task.contains("molanisk")) {
      requirements.add(inventoryRequirement("Slayer bell", "slayer bell"));
    }
    if (task.contains("mogre")) {
      requirements.add(
          inventoryRequirement(
              consumableFinisherQuantity(remainingKills),
              "Fishing explosive",
              "fishing explosive"));
    }
    if (task.contains("brine rat")) {
      requirements.add(inventoryRequirement("Spade", "spade"));
    }
    if (task.contains("cave bug") || task.contains("cave slime")) {
      requirements.add(
          inventoryRequirement(
              "Safe light source",
              "bruma torch",
              "bullseye lantern",
              "emerald lantern",
              "sapphire lantern",
              "oil lantern",
              "candle lantern"));
    }
    if (task.contains("cave crawler") || task.contains("cave slime")) {
      requirements.add(
          inventoryRequirement(
              "Poison protection",
              "antidote plus plus",
              "antidote plus",
              "superantipoison",
              "antipoison"));
    }
    if (task.equals("crocodiles")
        || task.equals("crocodile")
        || (task.equals("lizards") || task.equals("lizard"))
        || task.equals("bandits")
        || task.equals("bandit")) {
      requirements.add(
          inventoryRequirement(
              "Desert heat protection",
              "circlet of water",
              "desert amulet 4",
              "waterskin 4",
              "waterskin 3",
              "waterskin 2",
              "waterskin 1"));
    }
    if (task.contains("sea snake")) {
      requirements.add(
          inventoryRequirement(
              "Poison protection",
              "antidote plus plus",
              "antidote plus",
              "superantipoison",
              "antipoison"));
    }
    if (task.contains("sourhog")) {
      requirements.add(
          equipmentRequirement(
              "Reinforced goggles or Slayer helmet", "reinforced goggles", "slayer helmet"));
    }
    if (task.equals("giant mole")) {
      requirements.add(inventoryRequirement("Spade", "spade"));
      requirements.add(
          inventoryRequirement(
              "Safe light source",
              "bruma torch",
              "bullseye lantern",
              "emerald lantern",
              "sapphire lantern",
              "oil lantern",
              "candle lantern"));
    }
    if (task.contains("deranged archaeologist")) {
      requirements.add(
          inventoryRequirement(
              "Swamp-vine axe",
              "dragon axe",
              "rune axe",
              "adamant axe",
              "mithril axe",
              "black axe",
              "steel axe",
              "iron axe",
              "bronze axe"));
    }
    if (task.contains("general graardor")) {
      requirements.add(inventoryRequirement("Hammer", "hammer", "imcando hammer"));
    }
    if (task.contains("kree arra")) {
      requirements.add(inventoryRequirement("Mith grapple", "mith grapple"));
    }
    if (location.contains("brimhaven dungeon")) {
      requirements.add(
          inventoryRequirement(
              "Axe",
              "infernal axe",
              "crystal axe",
              "dragon axe",
              "rune axe",
              "adamant axe",
              "mithril axe",
              "steel axe",
              "iron axe",
              "bronze axe"));
    }
    if (task.contains("cave horror")) {
      requirements.add(equipmentRequirement("Witchwood icon", "witchwood icon"));
    }
    if (task.contains("fever spider") && style == CombatStyle.MELEE) {
      requirements.add(equipmentRequirement("Slayer gloves", "slayer gloves"));
    } else if (task.contains("fever spider")) {
      requirements.add(inventoryRequirement("Relicym's balm", "relicym s balm"));
    }
    if (task.contains("harpie bug swarm")) {
      requirements.add(equipmentRequirement("Lit bug lantern", "lit bug lantern"));
    }
    if (requiresKaruulmProtectionBootsForRegression(
        task, location, achievementDiaries.removesKaruulmBootRequirement())) {
      requirements.add(
          equipmentRequirement(
              "Stone-protection boots", "boots of stone", "granite boots", "boots of brimstone"));
    }
    if (task.contains("basilisk") || task.contains("cockatrice")) {
      requirements.add(
          equipmentRequirement("Mirror shield or V's shield", "mirror shield", "v s shield"));
    }
    if (task.contains("kurask") || task.contains("turoth")) {
      if (style == CombatStyle.RANGED) {
        requirements.add(
            equipmentRequirement(
                "Broad ammunition",
                "broad bolts",
                "amethyst broad bolts",
                "broad arrows",
                "amethyst broad arrows"));
      } else if (style == CombatStyle.MAGIC) {
        requirements.add(
            equipmentRequirement(
                "Magic Dart staff",
                "slayer s staff e",
                "slayer s staff",
                "toxic staff of the dead",
                "staff of the dead"));
      } else {
        requirements.add(
            equipmentRequirement(
                "Leaf-bladed weapon",
                "leaf bladed battleaxe",
                "leaf bladed sword",
                "leaf bladed spear"));
      }
    }
    if (task.contains("zygomite")) {
      requirements.add(inventoryRequirement("Fungicide spray", "fungicide spray"));
    }
    if (task.contains("fossil island wyvern")
        || task.contains("skeletal wyvern")
        || combined.contains("wyvern cave on fossil island")) {
      if (style == CombatStyle.RANGED) {
        requirements.add(
            equipmentRequirement(
                "Wyvern-protection shield",
                "dragonfire ward",
                "mind shield",
                "elemental shield",
                "dragonfire shield",
                "ancient wyvern shield"));
      } else {
        requirements.add(
            equipmentRequirement(
                "Wyvern-protection shield",
                "ancient wyvern shield",
                "dragonfire shield",
                "mind shield",
                "elemental shield",
                "dragonfire ward"));
      }
    }
    if ((task.equals("blue dragon") || task.equals("blue dragons"))) {
      if (style != CombatStyle.MELEE) {
        requirements.add(
            equipmentRequirement(
                "Dragonfire shield protection",
                "anti dragon shield",
                "dragonfire shield",
                "dragonfire ward",
                "ancient wyvern shield"));
      }
    } else if (usesReviewedDragonPackage(task) && style != CombatStyle.MELEE) {
      requirements.add(
          equipmentRequirement(
              "Dragonfire shield protection",
              "dragonfire ward",
              "anti dragon shield",
              "dragonfire shield",
              "ancient wyvern shield"));
    } else if (isDragonTask(task) && !task.equals("vorkath") && !usesReviewedDragonPackage(task)) {
      requirements.add(
          equipmentRequirement(
              "Dragonfire shield protection",
              "anti dragon shield",
              "dragonfire shield",
              "dragonfire ward",
              "ancient wyvern shield"));
      requirements.add(
          inventoryRequirement(
              PotionPolicy.EXTENDED_ANTIFIRE_DISPLAY,
              PotionPolicy.shieldedAntifireAlternatives()));
    }
    if (task.contains("vampyre") || task.contains("vampire") || task.contains("venator")) {
      requirements.add(
          equipmentRequirement(
              "Sunspear or blisterwood weapon",
              "sunspear",
              "blisterwood flail",
              "blisterwood sickle",
              "blisterwood polearm",
              "ivandis flail"));
    }
    if (task.contains("banshee")) {
      requirements.add(
          equipmentRequirement("Earmuffs or Slayer helmet", "earmuffs", "slayer helmet"));
    }
    if (task.contains("aquanite")) {
      requirements.add(
          inventoryRequirement(
              "Fast slash lure-severing weapon",
              "abyssal whip",
              "blade of saeldor",
              "dragon scimitar",
              "rune scimitar",
              "adamant scimitar"));
    }
    if (task.contains("dust devil") || task.contains("smoke devil")) {
      requirements.add(
          equipmentRequirement("Face mask or Slayer helmet", "face mask", "slayer helmet"));
    }
    if (task.contains("aberrant spectre")) {
      requirements.add(
          equipmentRequirement("Nose peg or Slayer helmet", "nose peg", "slayer helmet"));
    }
    if (task.contains("wall beast")) {
      requirements.add(
          equipmentRequirement("Spiny helmet or Slayer helmet", "spiny helmet", "slayer helmet"));
      requirements.add(
          inventoryRequirement(
              "Safe light source",
              "bruma torch",
              "bullseye lantern",
              "emerald lantern",
              "sapphire lantern",
              "oil lantern",
              "candle lantern"));
    }
    if (task.contains("killerwatt")) {
      requirements.add(equipmentRequirement("Insulated boots", "insulated boots"));
    }
    if (task.contains("shellbane gryphon")) {
      requirements.add(equipmentRequirement("Tortugan shield", "tortugan shield"));
    }
    if (task.contains("warped creature")) {
      requirements.add(inventoryRequirement("Crystal chime", "crystal chime"));
    }
    return requirements;
  }

  static boolean requiresGenericRockHammerForRegression(String taskName) {
    String task = normalize(taskName);
    return task.contains("gargoyle") && !task.contains("grotesque guardian");
  }

  static boolean requiresKalphiteQueenRopesForRegression(
      String task, String location, boolean desertEliteDiaryComplete) {
    if (desertEliteDiaryComplete || task == null || location == null) {
      return false;
    }
    String normalizedTask = normalize(task);
    return (normalizedTask.equals("kalphite queen") || normalizedTask.equals("the kalphite queen"))
        && normalize(location).equals("kalphite lair");
  }

  static boolean requiresKaruulmProtectionBootsForRegression(
      String task, String location, boolean kourendEliteDiaryComplete) {
    if (kourendEliteDiaryComplete) {
      return false;
    }
    String normalizedTask = normalize(task);
    String normalizedLocation = normalize(location);
    return normalizedTask.contains("wyrm")
        || normalizedTask.contains("drake")
        || normalizedTask.contains("hydra")
        || normalizedLocation.contains("karuulm");
  }

  private static boolean isDragonTask(String task) {
    return task.contains("dragon")
        && !task.contains("dragonfly")
        && !task.contains("dragon impling");
  }

  private static boolean usesReviewedDragonPackage(String task) {
    return task.equals("blue dragon")
        || task.equals("blue dragons")
        || task.equals("black dragon")
        || task.equals("black dragons")
        || task.equals("green dragon")
        || task.equals("green dragons")
        || task.equals("red dragon")
        || task.equals("red dragons")
        || task.equals("metal dragon")
        || task.equals("metal dragons")
        || task.equals("bronze dragon")
        || task.equals("bronze dragons")
        || task.equals("iron dragon")
        || task.equals("iron dragons")
        || task.equals("steel dragon")
        || task.equals("steel dragons")
        || task.equals("mithril dragon")
        || task.equals("mithril dragons")
        || task.equals("adamant dragon")
        || task.equals("adamant dragons")
        || task.equals("rune dragon")
        || task.equals("rune dragons");
  }

  private static CombatStyle combatStyle(TaskStrategy strategy) {
    if (strategy == null) {
      return CombatStyle.FLEXIBLE;
    }
    switch (strategy.getCombatStyle()) {
      case MAGIC:
        return CombatStyle.MAGIC;
      case RANGED:
        return CombatStyle.RANGED;
      case MELEE:
        return CombatStyle.MELEE;
      case HYBRID:
      default:
        return CombatStyle.FLEXIBLE;
    }
  }

  private static boolean isCannonSuggested(String cannon) {
    return (cannon.contains("recommended")
            || cannon.contains("preferred")
            || cannon.contains("optional"))
        && !cannon.contains("not allowed")
        && !cannon.contains("disabled")
        && !cannon.contains("locked");
  }

  private static Requirement equipmentRequirement(String displayName, String... alternatives) {
    return new Requirement(displayName, true, 1, alternatives);
  }

  private static Requirement inventoryRequirement(String displayName, String... alternatives) {
    return new Requirement(displayName, false, 1, alternatives);
  }

  private static Requirement inventoryRequirement(
      int quantity, String displayName, String... alternatives) {
    return new Requirement(displayName, false, quantity, alternatives);
  }

  private static int consumableFinisherQuantity(int remainingKills) {
    return remainingKills > 0 ? remainingKills : 1;
  }

  private static boolean containsAny(Set<String> names, List<String> alternatives) {
    for (String alternative : alternatives) {
      if (containsText(names, alternative)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsAnyText(Set<String> names, String... fragments) {
    for (String fragment : fragments) {
      if (containsText(names, fragment)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsText(Set<String> names, String fragment) {
    String normalizedFragment = normalize(fragment);
    for (String name : names) {
      if (name.contains(normalizedFragment)) {
        return true;
      }
    }
    return false;
  }

  private static String join(List<String> values, String separator) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (value == null || value.trim().isEmpty()) {
        continue;
      }
      if (result.length() > 0) {
        result.append(separator);
      }
      result.append(value.trim());
    }
    return result.toString();
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ENGLISH)
        .replace('\u2019', '\'')
        .replace("+", " plus ")
        .replaceAll("[^a-z0-9]+", " ")
        .trim()
        .replaceFirst("^the\\s+", "");
  }

  static String normalizePotionDisplayNameForRegression(String value) {
    return normalize(value);
  }

  static String preferredInventoryItemForRegression(
      List<String> ownedDisplayNames, String... alternatives) {
    return preferredInventoryItemForRegression(ownedDisplayNames, false, alternatives);
  }

  static String preferredInventoryEquipmentForRegression(
      List<String> ownedDisplayNames, String... progression) {
    return preferredInventoryItemForRegression(ownedDisplayNames, true, progression);
  }

  private static String preferredInventoryItemForRegression(
      List<String> ownedDisplayNames, boolean equipmentProgression, String... alternatives) {
    List<OwnedItem> owned = new ArrayList<>();
    for (String displayName : ownedDisplayNames) {
      owned.add(
          new OwnedItem(
              -1,
              displayName,
              1,
              KitItem.Status.BANK,
              -1,
              null,
              Collections.singleton(normalize(displayName))));
    }
    OwnedItem selected =
        findPreferredInventoryItem(owned, Arrays.asList(alternatives), equipmentProgression);
    return selected == null ? "" : selected.displayName;
  }

  static boolean equipmentProgressionNameMatchesForRegression(
      String itemName, String progressionName) {
    return matchesEquipmentProgressionName(normalize(itemName), normalize(progressionName));
  }

  static int directEquipmentProgressionRankForRegression(
      String itemName, List<String> progression) {
    return directEquipmentProgressionRank(normalize(itemName), progression);
  }

  private static int directEquipmentProgressionRank(String candidate, List<String> progression) {
    if (candidate == null || candidate.isEmpty() || progression == null) {
      return -1;
    }
    for (int index = 0; index < progression.size(); index++) {
      if (matchesEquipmentProgressionName(candidate, normalize(progression.get(index)))) {
        return index;
      }
    }
    return -1;
  }

  private static boolean matchesEquipmentProgressionName(String candidate, String progressionName) {
    if (candidate == null
        || progressionName == null
        || candidate.isEmpty()
        || progressionName.isEmpty()) {
      return false;
    }
    if (candidate.contains(progressionName)) {
      return true;
    }
    boolean godItem =
        candidate.startsWith("ancient ")
            || candidate.startsWith("armadyl ")
            || candidate.startsWith("bandos ")
            || candidate.startsWith("guthix ")
            || candidate.startsWith("saradomin ")
            || candidate.startsWith("zamorak ");
    if (progressionName.equals("blessed body")) {
      return godItem && candidate.endsWith("d hide body");
    }
    if (progressionName.equals("blessed coif")) {
      return godItem && candidate.endsWith("coif");
    }
    if (progressionName.equals("blessed chaps")) {
      return godItem && candidate.endsWith("chaps");
    }
    if (progressionName.equals("blessed vambraces")) {
      return godItem && (candidate.endsWith("bracers") || candidate.endsWith("vambraces"));
    }
    if (progressionName.equals("blessed boots")) {
      return godItem && candidate.endsWith("d hide boots");
    }
    if (progressionName.equals("imbued god cape")) {
      return candidate.matches("imbued (?:saradomin|guthix|zamorak)(?: max)? cape");
    }
    if (progressionName.equals("god cape")) {
      return candidate.matches("(?:saradomin|guthix|zamorak)(?: max)? cape");
    }
    if (progressionName.equals("dizana s quiver")) {
      return candidate.matches("(?:blessed )?dizana s (?:quiver|max cape)(?: [a-z0-9]+)*");
    }
    if (progressionName.equals("dizana s max cape")) {
      return candidate.contains("dizana s max cape");
    }
    if (progressionName.equals("ava s assembler")) {
      return candidate.contains("assembler max cape") || candidate.contains("masori assembler");
    }
    if (progressionName.equals("ava s accumulator")) {
      return candidate.contains("accumulator max cape");
    }
    if (progressionName.equals("infernal cape")) {
      return candidate.contains("infernal max cape");
    }
    if (progressionName.equals("fire cape")) {
      return candidate.contains("fire max cape");
    }
    if (progressionName.equals("mythical cape")) {
      return candidate.contains("mythical max cape");
    }
    if (progressionName.equals("ardougne cloak 4")) {
      return candidate.contains("ardougne max cape");
    }
    if (progressionName.equals("cape of accomplishment")) {
      return isCapeOfAccomplishmentName(candidate);
    }
    if (progressionName.equals("vestment robe top")) {
      return godItem && candidate.endsWith("robe top");
    }
    if (progressionName.equals("vestment robe legs")) {
      return godItem && (candidate.endsWith("robe legs") || candidate.endsWith("robe bottom"));
    }
    if (progressionName.equals("vestment cloak")) {
      return godItem && candidate.endsWith("cloak");
    }
    if (progressionName.equals("elemental staff")) {
      return candidate.equals("staff of air")
          || candidate.equals("staff of water")
          || candidate.equals("staff of earth")
          || candidate.equals("staff of fire");
    }
    if (progressionName.equals("mystic staff")) {
      return candidate.startsWith("mystic ") && candidate.endsWith(" staff");
    }
    if (progressionName.equals("barrows helm")) {
      return isBarrowsFamily(candidate, "helm");
    }
    if (progressionName.equals("barrows platebody")) {
      return isBarrowsFamily(candidate, "platebody") || candidate.startsWith("verac s brassard");
    }
    if (progressionName.equals("barrows platelegs")) {
      return isBarrowsFamily(candidate, "platelegs") || candidate.startsWith("verac s plateskirt");
    }
    return false;
  }

  private static boolean isBarrowsFamily(String candidate, String piece) {
    return candidate.matches("(?:dharok|guthan|torag) s " + piece + "(?: [a-z0-9]+)*");
  }

  private static boolean isCapeOfAccomplishmentName(String candidate) {
    if (candidate.equals("max cape") || candidate.equals("max cape t")) {
      return true;
    }
    return candidate.matches(
        "(?:attack|strength|defence|ranging|prayer|magic|runecraft|"
            + "construction|hitpoints|agility|herblore|thieving|crafting|"
            + "fletching|slayer|hunter|mining|smithing|fishing|cooking|"
            + "firemaking|woodcutting|farming|sailing|quest point|music|"
            + "achievement diary) cape(?: t)?");
  }

  private enum CombatStyle {
    MAGIC("magic"),
    RANGED("ranged"),
    MELEE("melee"),
    FLEXIBLE("flexible");
    private final String label;

    CombatStyle(String label) {
      this.label = label;
    }
  }

  private static final class OwnedStrategyCandidate {
    private final TaskStrategy strategy;
    private final OwnedItem weapon;
    private final int score;

    private OwnedStrategyCandidate(TaskStrategy strategy, OwnedItem weapon, int score) {
      this.strategy = strategy;
      this.weapon = weapon;
      this.score = score;
    }
  }

  private static final class OwnedItem {
    private final int itemId;
    private final String displayName;
    private final String normalizedName;
    private final Set<String> normalizedNames;
    private final int quantity;
    private final KitItem.Status status;
    private final int equipmentSlot;
    private final ItemEquipmentStats equipmentStats;

    private OwnedItem(
        int itemId,
        String displayName,
        int quantity,
        KitItem.Status status,
        int equipmentSlot,
        ItemEquipmentStats equipmentStats,
        Set<String> normalizedNames) {
      this.itemId = itemId;
      this.displayName = displayName;
      this.normalizedName = normalize(displayName);
      Set<String> aliases = new LinkedHashSet<>();
      if (normalizedNames != null) {
        aliases.addAll(normalizedNames);
      }
      aliases.add(this.normalizedName);
      this.normalizedNames = Collections.unmodifiableSet(aliases);
      this.quantity = quantity;
      this.status = status;
      this.equipmentSlot = equipmentSlot;
      this.equipmentStats = equipmentStats;
    }

    private boolean matchesName(String fragment) {
      String normalizedFragment = normalize(fragment);
      if (normalizedFragment.isEmpty()) {
        return false;
      }
      for (String candidate : normalizedNames) {
        if (candidate.contains(normalizedFragment)) {
          return true;
        }
      }
      return false;
    }

    private boolean matchesEquipmentProgression(String fragment) {
      String normalizedFragment = normalize(fragment);
      if (normalizedFragment.isEmpty()) {
        return false;
      }
      for (String candidate : normalizedNames) {
        if (matchesEquipmentProgressionName(candidate, normalizedFragment)) {
          return true;
        }
      }
      return false;
    }

    private int directEquipmentProgressionRank(List<String> progression) {
      return SlayerLoadoutAnalyzer.directEquipmentProgressionRank(normalizedName, progression);
    }

    private boolean matchesExactName(String value) {
      String normalizedValue = normalize(value);
      return !normalizedValue.isEmpty() && normalizedNames.contains(normalizedValue);
    }

    private boolean matchesExactDisplayName(String value) {
      String normalizedValue = normalize(value);
      return !normalizedValue.isEmpty() && normalizedName.equals(normalizedValue);
    }

    private boolean isExactSeekingArrow() {
      return QuiverAmmo.isSeekingArrow(itemId)
          || normalizedName.startsWith("seeking ") && normalizedName.contains("arrow");
    }

    private boolean matchesSlot(EquipmentInventorySlot slot) {
      if (equipmentStats != null) {
        return equipmentStats.getSlot() == slot.getSlotIdx();
      }
      return equipmentSlot == slot.getSlotIdx();
    }

    private boolean isTwoHanded() {
      if (equipmentStats != null && equipmentStats.isTwoHanded()) {
        return true;
      }
      boolean twoHandedBow =
          !matchesName("crossbow")
              && (matchesName(" bow")
                  || normalizedName.startsWith("bow ")
                  || matchesName("shortbow")
                  || matchesName("longbow"));
      return matchesName("blowpipe")
          || twoHandedBow
          || matchesName("ballista")
          || matchesName("scythe")
          || matchesName("godsword")
          || matchesName("halberd")
          || matchesName("spear")
          || matchesName("2h sword")
          || matchesName("bludgeon");
    }

    private KitItem toLoadoutItem(int requestedQuantity) {
      return new KitItem(displayName, itemId, Math.max(1, requestedQuantity), status);
    }
  }

  private static final class Requirement {
    private final String displayName;
    private final boolean equipment;
    private final int quantity;
    private final List<String> alternatives;

    private Requirement(
        String displayName, boolean equipment, int quantity, String... alternatives) {
      this.displayName = displayName;
      this.equipment = equipment;
      this.quantity = Math.max(1, quantity);
      this.alternatives = new ArrayList<>();
      for (String alternative : alternatives) {
        this.alternatives.add(normalize(alternative));
      }
    }
  }

  private static List<String> list(String key) {
    List<String> values = LOADOUT_LISTS.get(key);
    if (values == null) {
      throw new IllegalStateException("Missing loadout list: " + key);
    }
    return new ArrayList<>(values);
  }

  private static String[] array(String key) {
    String[] values = LOADOUT_ARRAYS.get(key);
    if (values == null) {
      throw new IllegalStateException("Missing loadout array: " + key);
    }
    return values.clone();
  }

  private static Map<String, List<String>> loadLists(String resourceName) {
    Map<String, List<String>> values = new LinkedHashMap<>();
    for (String[] row : ResourceTable.rows(resourceName, 2)) {
      List<String> entries =
          row[1].isEmpty() ? Collections.emptyList() : Arrays.asList(row[1].split("\\|", -1));
      if (values.put(row[0], Collections.unmodifiableList(new ArrayList<>(entries))) != null) {
        throw new IllegalStateException("Duplicate loadout list: " + row[0]);
      }
    }
    return Collections.unmodifiableMap(values);
  }

  private static Map<String, String[]> loadArrays(String resourceName) {
    Map<String, String[]> values = new LinkedHashMap<>();
    for (String[] row : ResourceTable.rows(resourceName, 2)) {
      String[] entries = row[1].isEmpty() ? new String[0] : row[1].split("\\|", -1);
      if (values.put(row[0], entries) != null) {
        throw new IllegalStateException("Duplicate loadout array: " + row[0]);
      }
    }
    return Collections.unmodifiableMap(values);
  }
}
