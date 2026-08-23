package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.util.Text;

public final class BankTagLayout {
  public static final String TAG_NAME = "SlayerPlus Current";
  private static final int BANK_COLUMNS = 8;
  private static final int OPTIONAL_LIMIT = 8;
  private static final int INVENTORY_LIMIT = 28;
  private static final int RUNE_POUCH_INVENTORY_SLOT = 27;
  private static final int TRAVEL_SLOT_WITH_RUNE_POUCH = 26;
  private static final int TRAVEL_SLOT_WITHOUT_RUNE_POUCH = 27;
  private static final int PREPARATION_REFERENCE_START_ROW = 8;
  private static final int PREPARATION_REFERENCE_START_COLUMN = 0;
  private static final int PREPARATION_REFERENCE_COLUMNS = 4;
  private static final int LIGHTBEARER_SWAP_POSITION = position(7, 2);
  private static final int STAGING_TRAVEL_STRIP_POSITION = position(0, 0);
  private static final int EXTRA_QUIVER_AMMO_POSITION = position(2, 2);
  private static final int[] EQUIPMENT_POSITIONS = {
    position(2, 1),
    position(3, 0),
    position(3, 1),
    position(3, 2),
    position(4, 0),
    position(4, 1),
    position(4, 2),
    position(5, 1),
    position(6, 0),
    position(6, 1),
    position(6, 2),
    EXTRA_QUIVER_AMMO_POSITION
  };
  private final Client client;
  private final TagManager tagManager;
  private final LayoutManager layoutManager;
  private final BankTagsService bankTagsService;
  private final ConfigManager configManager;
  private int[] lastSavedLayout = new int[0];
  private Set<Integer> lastSavedTaggedItemIds = Collections.emptySet();

  public BankTagLayout(
      Client client,
      TagManager tagManager,
      LayoutManager layoutManager,
      BankTagsService bankTagsService,
      ConfigManager configManager) {
    this.client = client;
    this.tagManager = tagManager;
    this.layoutManager = layoutManager;
    this.bankTagsService = bankTagsService;
    this.configManager = configManager;
  }

  public Result createOrUpdate(KitPlan plan) {
    return createOrUpdate(plan, -1, Collections.emptyList());
  }

  public Result createOrUpdate(KitPlan plan, int travelItemId) {
    return createOrUpdate(plan, travelItemId, Collections.emptyList());
  }

  public Result createOrUpdate(
      KitPlan plan, int travelItemId, List<Integer> preparationItemIds) {
    return createOrUpdate(plan, travelItemId, preparationItemIds, travelItemId > 0);
  }

  public Result createOrUpdate(
      KitPlan plan,
      int travelItemId,
      List<Integer> preparationItemIds,
      boolean shortestPathOwnsTravel) {
    return createOrUpdate(plan, null, travelItemId, preparationItemIds, shortestPathOwnsTravel);
  }

  public Result createOrUpdate(
      KitPlan plan,
      TravelChoice travelSelection,
      int travelItemId,
      List<Integer> preparationItemIds,
      boolean shortestPathOwnsTravel) {
    return createOrUpdate(
        plan, travelSelection, travelItemId, preparationItemIds, shortestPathOwnsTravel, 0, true);
  }

  public Result createOrUpdate(
      KitPlan plan,
      TravelChoice travelSelection,
      int travelItemId,
      List<Integer> preparationItemIds,
      boolean shortestPathOwnsTravel,
      int authoritativeTravelSlotIndex,
      boolean analyzerSourceZeroIsTravel) {
    return createOrUpdate(
        plan,
        travelSelection,
        travelItemId,
        preparationItemIds,
        shortestPathOwnsTravel,
        authoritativeTravelSlotIndex,
        analyzerSourceZeroIsTravel,
        -1);
  }

  public Result createOrUpdate(
      KitPlan plan,
      TravelChoice travelSelection,
      int travelItemId,
      List<Integer> preparationItemIds,
      boolean shortestPathOwnsTravel,
      int authoritativeTravelSlotIndex,
      boolean analyzerSourceZeroIsTravel,
      int extraQuiverAmmoItemId) {
    if (plan == null || !plan.hasConcreteItems()) {
      return Result.failure(
          "No concrete recommended items are available yet. Open your bank once, then refresh the"
              + " task.");
    }
    Layout layout = new Layout(TAG_NAME);
    Set<Integer> taggedItemIds = new LinkedHashSet<>();
    int layoutSlots = 0;
    boolean externalStagingTravel =
        shortestPathOwnsTravel && travelItemId > 0 && authoritativeTravelSlotIndex < 0;
    int optionalIndex = externalStagingTravel ? 1 : 0;
    List<KitItem> equipment = plan.getEquipmentItems();
    if (externalStagingTravel
        && placeItemId(layout, taggedItemIds, travelItemId, STAGING_TRAVEL_STRIP_POSITION)) {
      layoutSlots++;
    }
    for (KitItem item : plan.getOptionalItems()) {
      if (optionalIndex >= OPTIONAL_LIMIT) {
        break;
      }
      if (isLightbearer(item)) {
        continue;
      }
      if (duplicatesConcreteEquipment(item, equipment)) {
        continue;
      }
      if (place(layout, taggedItemIds, item, optionalIndex)) {
        layoutSlots++;
        optionalIndex++;
      }
    }
    int resolvedDizanaVariantId = resolveOwnedDizanaVariantId(plan);
    int recommendedExtraQuiverAmmoItemId =
        equipment.size() > 11 && concrete(equipment.get(11)) ? equipment.get(11).getItemId() : -1;
    if (normalizeName(plan.getLayoutTitle()).contains("tzkal zuk")) {
      recommendedExtraQuiverAmmoItemId =
          QuiverAmmo.preferBetterInfernoArrow(
              recommendedExtraQuiverAmmoItemId, extraQuiverAmmoItemId);
    }
    boolean hasUsableDizanaForLayout =
        resolvedDizanaVariantId > 0 || planContainsUsableDizanaCape(plan);
    for (int index = 0; index < equipment.size() && index < EQUIPMENT_POSITIONS.length; index++) {
      if (index == 1 && resolvedDizanaVariantId > 0) {
        if (placeItemId(
            layout, taggedItemIds, resolvedDizanaVariantId, EQUIPMENT_POSITIONS[index])) {
          layoutSlots++;
        }
        continue;
      }
      if (index == 11) {
        if (hasUsableDizanaForLayout
            && recommendedExtraQuiverAmmoItemId > 0
            && placeNestedQuiverAmmoItemId(
                layout,
                taggedItemIds,
                recommendedExtraQuiverAmmoItemId,
                EQUIPMENT_POSITIONS[index])) {
          layoutSlots++;
        }
        continue;
      }
      if (place(layout, taggedItemIds, equipment.get(index), EQUIPMENT_POSITIONS[index])) {
        layoutSlots++;
      }
    }
    KitItem lightbearer = findLightbearer(plan);
    if (place(layout, taggedItemIds, lightbearer, LIGHTBEARER_SWAP_POSITION)) {
      layoutSlots++;
    }
    List<KitItem> inventoryItems = plan.getInventoryItems();
    int travelSlotIndex =
        authoritativeTravelSlotIndex < 0 ? -1 : preferredTravelSlot(inventoryItems);
    boolean reserveTravelSlot = shortestPathOwnsTravel && travelItemId > 0 && travelSlotIndex >= 0;
    if (reserveTravelSlot
        && travelSlotIndex == RUNE_POUCH_INVENTORY_SLOT
        && containsConcreteRunePouch(inventoryItems)) {
      travelSlotIndex = TRAVEL_SLOT_WITH_RUNE_POUCH;
    }
    if (reserveTravelSlot
        && placeInventoryItemId(
            layout,
            taggedItemIds,
            travelItemId,
            position(2 + travelSlotIndex / 4, 4 + travelSlotIndex % 4))) {
      layoutSlots++;
    }
    List<InventoryCandidate> inventoryCandidates = new ArrayList<>();
    for (int sourceIndex = 0; sourceIndex < inventoryItems.size(); sourceIndex++) {
      KitItem item = inventoryItems.get(sourceIndex);
      if (!concrete(item) || isLightbearer(item)) {
        continue;
      }
      if (shouldSuppressInventoryTravelItem(
          shortestPathOwnsTravel,
          sourceIndex,
          travelItemId,
          item,
          travelSelection,
          analyzerSourceZeroIsTravel)) {
        continue;
      }
      inventoryCandidates.add(new InventoryCandidate(item, sourceIndex));
    }
    for (InventoryPlacement placement :
        planInventoryCandidates(
            inventoryCandidates,
            reserveTravelSlot ? travelSlotIndex : -1,
            analyzerSourceZeroIsTravel)) {
      int slotIndex = placement.getSlotIndex();
      int row = 2 + slotIndex / 4;
      int column = 4 + slotIndex % 4;
      if (placeInventory(layout, taggedItemIds, placement.getItem(), position(row, column))) {
        layoutSlots++;
      }
    }
    int preparationIndex = 0;
    Set<Integer> preparationReferenceIds = new LinkedHashSet<>();
    if (preparationItemIds != null) {
      for (int preparationItemId : preparationItemIds) {
        if (preparationIndex >= OPTIONAL_LIMIT) {
          break;
        }
        int referenceId = preparationItemId;
        if (referenceId <= 0
            || (shortestPathOwnsTravel && travelItemId > 0 && referenceId == travelItemId)
            || (planContainsItemId(plan, referenceId) && !isRuneReferenceId(referenceId))
            || !preparationReferenceIds.add(referenceId)) {
          continue;
        }
        int row =
            PREPARATION_REFERENCE_START_ROW + preparationIndex / PREPARATION_REFERENCE_COLUMNS;
        int column =
            PREPARATION_REFERENCE_START_COLUMN + preparationIndex % PREPARATION_REFERENCE_COLUMNS;
        if (placePreparationReferenceItemId(
            layout, taggedItemIds, preparationItemId, position(row, column))) {
          layoutSlots++;
          preparationIndex++;
        }
      }
    }
    if (shortestPathOwnsTravel && travelItemId > 0 && !layoutContainsItemId(layout, travelItemId)) {
      int travelPosition =
          externalStagingTravel
              ? STAGING_TRAVEL_STRIP_POSITION
              : position(
                  2 + Math.max(0, travelSlotIndex) / 4, 4 + Math.max(0, travelSlotIndex) % 4);
      if (placeInventoryItemId(layout, taggedItemIds, travelItemId, travelPosition)) {
        layoutSlots++;
      }
    }
    if (shortestPathOwnsTravel && travelItemId > 0 && !layoutContainsItemId(layout, travelItemId)) {
      return Result.failure(
          "The selected Shortest Path teleport could not be retained in the bank-tag layout.");
    }
    if (taggedItemIds.isEmpty()) {
      return Result.failure(
          "The recommendation contains only unresolved item names. Open your bank once so"
              + " SlayerPlus can select exact item IDs.");
    }
    tagManager.removeTag(TAG_NAME);
    for (int itemId : taggedItemIds) {
      tagManager.addTag(itemId, TAG_NAME, false);
    }
    layoutManager.saveLayout(layout);
    lastSavedLayout = Arrays.copyOf(layout.getLayout(), layout.getLayout().length);
    lastSavedTaggedItemIds = new LinkedHashSet<>(taggedItemIds);
    int iconItemId = chooseIcon(plan, taggedItemIds);
    ensurePersistentTagTab(iconItemId);
    boolean bankOpen = client != null && client.getItemContainer(InventoryID.BANK) != null;
    if (bankOpen) {
      bankTagsService.openBankTag(
          TAG_NAME,
          BankTagsService.OPTION_ALLOW_MODIFICATIONS
              | BankTagsService.OPTION_ITEMS_NOT_IN_LAYOUT_AT_BOTTOM);
    }
    int unresolved = plan.getUnresolvedItemCount();
    String omitted =
        unresolved > 0
            ? " "
                + unresolved
                + " unresolved recommendation"
                + (unresolved == 1 ? " was" : "s were")
                + " omitted."
            : "";
    return Result.success(
        bankOpen
            ? "Opened the real SlayerPlus Current bank-tag layout with "
                + layoutSlots
                + " arranged slots."
                + omitted
            : "Created the SlayerPlus Current bank-tag layout with "
                + layoutSlots
                + " arranged slots. Open the bank to view it."
                + omitted);
  }

  public boolean reopenLastSavedLayoutIfUnchanged() {
    if (lastSavedLayout.length == 0 || lastSavedTaggedItemIds.isEmpty()) {
      return false;
    }
    Layout persistedLayout = layoutManager.loadLayout(TAG_NAME);
    List<Integer> persistedItems = tagManager.getItemsForTag(TAG_NAME);
    if (persistedLayout == null
        || !samePersistedStateForRegression(
            lastSavedLayout,
            persistedLayout.getLayout(),
            lastSavedTaggedItemIds,
            persistedItems == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(persistedItems))) {
      return false;
    }
    if (client != null && client.getItemContainer(InventoryID.BANK) != null) {
      bankTagsService.openBankTag(
          TAG_NAME,
          BankTagsService.OPTION_ALLOW_MODIFICATIONS
              | BankTagsService.OPTION_ITEMS_NOT_IN_LAYOUT_AT_BOTTOM);
    }
    return true;
  }

  static boolean samePersistedStateForRegression(
      int[] expectedLayout,
      int[] actualLayout,
      Set<Integer> expectedItems,
      Set<Integer> actualItems) {
    return Arrays.equals(expectedLayout, actualLayout)
        && expectedItems != null
        && expectedItems.equals(actualItems);
  }

  static boolean shouldSuppressInventoryTravelItem(
      boolean shortestPathOwnsTravel,
      int sourceIndex,
      int selectedTravelItemId,
      KitItem item,
      TravelChoice travelSelection) {
    return shouldSuppressInventoryTravelItem(
        shortestPathOwnsTravel, sourceIndex, selectedTravelItemId, item, travelSelection, true);
  }

  static boolean shouldSuppressInventoryTravelItem(
      boolean shortestPathOwnsTravel,
      int sourceIndex,
      int selectedTravelItemId,
      KitItem item,
      TravelChoice travelSelection,
      boolean analyzerSourceZeroIsTravel) {
    if (!shortestPathOwnsTravel) {
      return false;
    }
    if (selectedTravelItemId > 0
        && item != null
        && (item.getInventoryGroup() == MethodRules.InventoryGroup.TRAVEL
            || (analyzerSourceZeroIsTravel
                && sourceIndex == 0
                && item.getInventoryGroup() == MethodRules.InventoryGroup.OTHER))) {
      return true;
    }
    if (item == null) {
      return false;
    }
    if (selectedTravelItemId > 0 && item.hasItemId() && item.getItemId() == selectedTravelItemId) {
      return true;
    }
    return travelSelection != null
        && travelSelection.hasPhysicalItem()
        && matchesEquivalentTravelFamily(
            item.getDisplayName(), travelSelection.getEquivalentItemFamilies());
  }

  static boolean matchesEquivalentTravelFamily(String itemDisplayName, Set<String> routeFamilies) {
    String itemFamily = canonicalTravelFamily(itemDisplayName);
    if (itemFamily.isEmpty() || routeFamilies == null || routeFamilies.isEmpty()) {
      return false;
    }
    for (String family : routeFamilies) {
      String routeFamily = canonicalTravelFamily(family);
      if (!routeFamily.isEmpty() && itemFamily.equals(routeFamily)) {
        return true;
      }
    }
    return false;
  }

  private static String canonicalTravelFamily(String value) {
    if (value == null || value.trim().isEmpty()) {
      return "";
    }
    String normalized =
        Text.removeTags(value)
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
    normalized =
        normalized
            .replaceFirst("\\s+(?:[a-z]?\\d+|t|l)$", "")
            .replaceAll("\\beternal\\b", " ")
            .replace("construction cape", "construct cape")
            .replaceAll("\\s+", " ")
            .trim();
    return normalized;
  }

  private void ensurePersistentTagTab(int iconItemId) {
    String standardizedTag = Text.standardize(TAG_NAME);
    String configuredTabs =
        configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG);
    List<String> tabs = new ArrayList<>(Text.fromCSV(configuredTabs == null ? "" : configuredTabs));
    boolean found = false;
    for (String tab : tabs) {
      if (standardizedTag.equals(Text.standardize(tab))) {
        found = true;
        break;
      }
    }
    if (!found) {
      tabs.add(standardizedTag);
      configManager.setConfiguration(
          BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG, Text.toCSV(tabs));
    }
    if (iconItemId > 0) {
      configManager.setConfiguration(
          BankTagsPlugin.CONFIG_GROUP,
          BankTagsPlugin.TAG_ICON_PREFIX + standardizedTag,
          Integer.toString(iconItemId));
    }
  }

  private static int chooseIcon(KitPlan plan, Set<Integer> fallback) {
    List<KitItem> equipment = plan.getEquipmentItems();
    if (!equipment.isEmpty() && concrete(equipment.get(0))) {
      return equipment.get(0).getItemId();
    }
    if (equipment.size() > 4 && concrete(equipment.get(4))) {
      return equipment.get(4).getItemId();
    }
    return fallback.iterator().next();
  }

  private int resolveOwnedDizanaVariantId(KitPlan plan) {
    List<KitItem> equipment =
        plan == null ? Collections.emptyList() : plan.getEquipmentItems();
    int plannedCapeId =
        equipment.size() > 1 && concrete(equipment.get(1)) ? equipment.get(1).getItemId() : -1;
    if (!shouldResolveOwnedDizanaVariantForPlan(plannedCapeId)) {
      return -1;
    }
    if (isUsableDizanaVariantId(plannedCapeId) && isExactItemLive(plannedCapeId)) {
      return plannedCapeId;
    }
    int resolved =
        bestDizanaVariantInContainer(
            client == null ? null : client.getItemContainer(InventoryID.WORN));
    if (resolved > 0) {
      return resolved;
    }
    resolved =
        bestDizanaVariantInContainer(
            client == null ? null : client.getItemContainer(InventoryID.INV));
    if (resolved > 0) {
      return resolved;
    }
    resolved =
        bestDizanaVariantInContainer(
            client == null ? null : client.getItemContainer(InventoryID.BANK));
    if (resolved > 0) {
      return resolved;
    }
    return isUsableDizanaVariantId(plannedCapeId) ? plannedCapeId : -1;
  }

  private boolean isExactItemLive(int itemId) {
    if (itemId <= 0 || client == null) {
      return false;
    }
    return containerHasExactItem(client.getItemContainer(InventoryID.WORN), itemId)
        || containerHasExactItem(client.getItemContainer(InventoryID.INV), itemId)
        || containerHasExactItem(client.getItemContainer(InventoryID.BANK), itemId);
  }

  private static boolean containerHasExactItem(ItemContainer container, int itemId) {
    if (container == null || itemId <= 0) {
      return false;
    }
    for (Item item : container.getItems()) {
      if (item != null && item.getId() == itemId && item.getQuantity() > 0) {
        return true;
      }
    }
    return false;
  }

  private static int bestDizanaVariantInContainer(ItemContainer container) {
    if (container == null) {
      return -1;
    }
    int bestId = -1;
    int bestScore = -1;
    for (Item item : container.getItems()) {
      if (item == null || item.getQuantity() <= 0) {
        continue;
      }
      int itemId = item.getId();
      if (itemId <= 0) {
        continue;
      }
      int score = dizanaVariantScore(itemId);
      if (score > bestScore) {
        bestScore = score;
        bestId = itemId;
      }
    }
    return bestScore > 0 ? bestId : -1;
  }

  private static boolean isUsableDizanaVariantId(int rawItemId) {
    return rawItemId > 0 && dizanaVariantScore(rawItemId) > 0;
  }

  static boolean shouldResolveOwnedDizanaVariantForPlan(int plannedCapeId) {
    return isUsableDizanaVariantId(plannedCapeId);
  }

  private static boolean isRuneReferenceId(int itemId) {
    switch (itemId) {
      case ItemID.AIRRUNE:
      case ItemID.WATERRUNE:
      case ItemID.EARTHRUNE:
      case ItemID.FIRERUNE:
      case ItemID.MINDRUNE:
      case ItemID.CHAOSRUNE:
      case ItemID.DEATHRUNE:
      case ItemID.BLOODRUNE:
      case ItemID.COSMICRUNE:
      case ItemID.SOULRUNE:
      case ItemID.LAWRUNE:
      case ItemID.WRATHRUNE:
      case ItemID.MISTRUNE:
      case ItemID.DUSTRUNE:
      case ItemID.SMOKERUNE:
      case ItemID.MUDRUNE:
      case ItemID.STEAMRUNE:
      case ItemID.LAVARUNE:
      case ItemID.AETHERRUNE:
        return true;
      default:
        return false;
    }
  }

  private static int dizanaVariantScore(int itemId) {
    switch (itemId) {
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
      default:
        return -1;
    }
  }

  private static boolean planContainsUsableDizanaCape(KitPlan plan) {
    if (plan == null) {
      return false;
    }
    for (KitItem item : plan.getEquipmentItems()) {
      if (item == null || item.getDisplayName() == null) {
        continue;
      }
      if (item.hasItemId() && isUsableDizanaVariantId(item.getItemId())) {
        return true;
      }
      String name = normalizeName(item.getDisplayName());
      if (name.contains("dizana")
          && (name.contains("quiver") || name.contains("max cape"))
          && !name.contains("max hood")
          && !name.contains("broken")
          && !name.contains("mangled")) {
        return true;
      }
    }
    return false;
  }

  private static boolean planContainsItemId(KitPlan plan, int rawItemId) {
    int itemId = rawItemId;
    if (itemId <= 0) {
      return false;
    }
    return containsItemId(plan.getEquipmentItems(), itemId)
        || containsItemId(plan.getInventoryItems(), itemId)
        || containsItemId(plan.getOptionalItems(), itemId);
  }

  private static boolean containsItemId(List<KitItem> items, int itemId) {
    for (KitItem item : items) {
      if (concrete(item) && item.getItemId() == itemId) {
        return true;
      }
    }
    return false;
  }

  private static boolean duplicatesConcreteEquipment(
      KitItem optionalItem, List<KitItem> equipment) {
    return concrete(optionalItem) && containsItemId(equipment, optionalItem.getItemId());
  }

  static boolean optionalItemDuplicatesEquipmentForRegression(
      KitItem optionalItem, List<KitItem> equipment) {
    return duplicatesConcreteEquipment(optionalItem, equipment);
  }

  private static KitItem findLightbearer(KitPlan plan) {
    KitItem item = findLightbearer(plan.getEquipmentItems());
    if (item != null) {
      return item;
    }
    item = findLightbearer(plan.getInventoryItems());
    if (item != null) {
      return item;
    }
    return findLightbearer(plan.getOptionalItems());
  }

  private static KitItem findLightbearer(List<KitItem> items) {
    if (items == null) {
      return null;
    }
    for (KitItem item : items) {
      if (isLightbearer(item) && concrete(item)) {
        return item;
      }
    }
    return null;
  }

  private static boolean isLightbearer(KitItem item) {
    if (item == null || item.getDisplayName() == null) {
      return false;
    }
    return normalizeName(item.getDisplayName()).contains("lightbearer");
  }

  static List<InventoryPlacement> planInventoryForBankTag(
      List<KitItem> source, int reservedSlotIndex, boolean sourceSlotZeroIsTravel) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    List<InventoryCandidate> candidates = new ArrayList<>();
    for (int index = 0; index < source.size(); index++) {
      KitItem item = source.get(index);
      if (concrete(item) && !isLightbearer(item)) {
        candidates.add(new InventoryCandidate(item, index));
      }
    }
    return planInventoryCandidates(candidates, reservedSlotIndex, sourceSlotZeroIsTravel);
  }

  private static List<InventoryPlacement> planInventoryCandidates(
      List<InventoryCandidate> source,
      int requestedReservedSlotIndex,
      boolean sourceSlotZeroIsTravel) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    List<InventoryCandidate> remaining = new ArrayList<>(source);
    List<InventoryPlacement> result = new ArrayList<>(source.size());
    boolean[] occupied = new boolean[INVENTORY_LIMIT];
    InventoryCandidate runePouch = firstRunePouch(remaining);
    int reservedSlotIndex = requestedReservedSlotIndex;
    if (runePouch != null && reservedSlotIndex == INVENTORY_LIMIT - 1) {
      reservedSlotIndex = 0;
    }
    if (reservedSlotIndex >= 0 && reservedSlotIndex < INVENTORY_LIMIT) {
      occupied[reservedSlotIndex] = true;
    }
    if (runePouch != null) {
      int runePouchSlot = RUNE_POUCH_INVENTORY_SLOT;
      occupied[runePouchSlot] = true;
      result.add(new InventoryPlacement(runePouch.item, runePouchSlot));
      remaining.remove(runePouch);
    }
    if (sourceSlotZeroIsTravel) {
      InventoryCandidate authoredTravel = findSourceIndex(remaining, 0);
      if (authoredTravel != null) {
        int preferred =
            runePouch == null ? TRAVEL_SLOT_WITHOUT_RUNE_POUCH : TRAVEL_SLOT_WITH_RUNE_POUCH;
        int slot = !occupied[preferred] ? preferred : lastFreeSlot(occupied);
        if (slot >= 0) {
          occupied[slot] = true;
          result.add(new InventoryPlacement(authoredTravel.item, slot));
          remaining.remove(authoredTravel);
        }
      }
    }
    placeEquipmentSwitchGroups(remaining, occupied, result);
    List<InventoryCandidate> tops = new ArrayList<>();
    List<InventoryCandidate> legs = new ArrayList<>();
    for (InventoryCandidate candidate : remaining) {
      if (!candidate.item.isEquipmentSwitch() && isArmorTop(candidate.item)) {
        tops.add(candidate);
      } else if (!candidate.item.isEquipmentSwitch() && isArmorLegs(candidate.item)) {
        legs.add(candidate);
      }
    }
    for (InventoryCandidate top : tops) {
      InventoryCandidate legsItem = takeMatchingLegs(top, legs);
      if (legsItem == null) {
        continue;
      }
      int topSlot = firstFreeVerticalPair(occupied);
      if (topSlot < 0) {
        break;
      }
      int legsSlot = topSlot + 4;
      occupied[topSlot] = true;
      occupied[legsSlot] = true;
      result.add(new InventoryPlacement(top.item, topSlot));
      result.add(new InventoryPlacement(legsItem.item, legsSlot));
      remaining.remove(top);
      remaining.remove(legsItem);
    }
    placeCannonBlock(remaining, occupied, result);
    placeBottomUtilities(remaining, occupied, result);
    remaining.sort(
        (left, right) -> {
          int leftGroup = bankInventoryVisualGroup(left.item);
          int rightGroup = bankInventoryVisualGroup(right.item);
          if (leftGroup != rightGroup) {
            return Integer.compare(leftGroup, rightGroup);
          }
          if (leftGroup == 0) {
            return 0;
          }
          int family =
              bankInventoryVisualFamily(left.item).compareTo(bankInventoryVisualFamily(right.item));
          if (family != 0) {
            return family;
          }
          return normalizeName(left.item.getDisplayName())
              .compareTo(normalizeName(right.item.getDisplayName()));
        });
    for (InventoryCandidate candidate : remaining) {
      int slot = firstFreeSlot(occupied);
      if (slot < 0) {
        break;
      }
      occupied[slot] = true;
      result.add(new InventoryPlacement(candidate.item, slot));
    }
    return result;
  }

  private static void placeCannonBlock(
      List<InventoryCandidate> remaining, boolean[] occupied, List<InventoryPlacement> result) {
    List<InventoryCandidate> parts = new ArrayList<>();
    for (InventoryCandidate candidate : remaining) {
      if (cannonPartOrder(candidate.item) >= 0) {
        parts.add(candidate);
      }
    }
    if (parts.size() < 4) {
      return;
    }
    parts.sort(
        (left, right) -> Integer.compare(cannonPartOrder(left.item), cannonPartOrder(right.item)));
    int blockStart = firstFreeTwoByTwoBlock(occupied);
    if (blockStart < 0) {
      return;
    }
    int[] positions = {blockStart, blockStart + 1, blockStart + 4, blockStart + 5};
    for (int index = 0; index < 4; index++) {
      InventoryCandidate part = parts.get(index);
      int slot = positions[index];
      occupied[slot] = true;
      result.add(new InventoryPlacement(part.item, slot));
      remaining.remove(part);
    }
  }

  private static int cannonPartOrder(KitItem item) {
    String name = normalizeName(item == null ? "" : item.getDisplayName());
    if (name.contains("cannon base")) {
      return 0;
    }
    if (name.contains("cannon stand")) {
      return 1;
    }
    if (name.contains("cannon barrels")) {
      return 2;
    }
    if (name.contains("cannon furnace")) {
      return 3;
    }
    return -1;
  }

  private static int firstFreeTwoByTwoBlock(boolean[] occupied) {
    int[] topRows = {0, 2, 4};
    for (int row : topRows) {
      for (int column = 0; column < 3; column++) {
        int start = row * 4 + column;
        if (!occupied[start]
            && !occupied[start + 1]
            && !occupied[start + 4]
            && !occupied[start + 5]) {
          return start;
        }
      }
    }
    return -1;
  }

  private static void placeBottomUtilities(
      List<InventoryCandidate> remaining, boolean[] occupied, List<InventoryPlacement> result) {
    List<InventoryCandidate> utilities = new ArrayList<>();
    for (InventoryCandidate candidate : remaining) {
      if (isBottomUtility(candidate.item)) {
        utilities.add(candidate);
      }
    }
    if (utilities.isEmpty()) {
      return;
    }
    List<Integer> slots = bottomAlignedFreeSlots(occupied, utilities.size());
    for (int index = 0; index < utilities.size() && index < slots.size(); index++) {
      InventoryCandidate utility = utilities.get(index);
      int slot = slots.get(index);
      occupied[slot] = true;
      result.add(new InventoryPlacement(utility.item, slot));
      remaining.remove(utility);
    }
  }

  private static List<Integer> bottomAlignedFreeSlots(boolean[] occupied, int requestedCount) {
    List<Integer> result = new ArrayList<>();
    int remaining = requestedCount;
    for (int row = 6; row >= 0 && remaining > 0; row--) {
      List<Integer> free = new ArrayList<>();
      for (int column = 0; column < 4; column++) {
        int slot = row * 4 + column;
        if (!occupied[slot]) {
          free.add(slot);
        }
      }
      int take = Math.min(remaining, free.size());
      int first = free.size() - take;
      for (int index = first; index < free.size(); index++) {
        result.add(free.get(index));
      }
      remaining -= take;
    }
    return result;
  }

  private static boolean isBottomUtility(KitItem item) {
    if (item == null || item.isEquipmentSwitch()) {
      return false;
    }
    if (item.getInventoryGroup() == MethodRules.InventoryGroup.UTILITY) {
      return !isRunePouch(item);
    }
    String name = normalizeName(item.getDisplayName());
    return name.contains("herb sack")
        || name.contains("seed box")
        || name.contains("soul bearer")
        || name.contains("bonecrusher")
        || name.contains("ash sanctifier")
        || name.contains("holy wrench")
        || name.contains("looting bag")
        || name.contains("gem bag")
        || name.contains("book of the dead")
        || name.contains("explorer s ring")
        || name.contains("rock hammer")
        || name.contains("bag of salt")
        || name.contains("ice cooler")
        || name.contains("slayer bell")
        || name.contains("fungicide")
        || name.contains("fishing explosive")
        || name.contains("task tool");
  }

  private static void placeEquipmentSwitchGroups(
      List<InventoryCandidate> remaining, boolean[] occupied, List<InventoryPlacement> result) {
    List<SwitchGroup> groups = new ArrayList<>();
    for (InventoryCandidate candidate : remaining) {
      if (!candidate.item.isEquipmentSwitch()) {
        continue;
      }
      SwitchGroup matching = null;
      for (SwitchGroup group : groups) {
        if (group.style == candidate.item.getSwitchStyle()) {
          matching = group;
          break;
        }
      }
      if (matching == null) {
        matching = new SwitchGroup(candidate.item.getSwitchStyle());
        groups.add(matching);
      }
      matching.items.add(candidate);
    }
    groups.sort((left, right) -> Integer.compare(right.items.size(), left.items.size()));
    List<Integer> verticalColumns = new ArrayList<>();
    int[] topRows = {0, 2, 4};
    for (int topRow : topRows) {
      for (int column = 0; column < 4; column++) {
        int topSlot = topRow * 4 + column;
        if (!occupied[topSlot] && !occupied[topSlot + 4]) {
          verticalColumns.add(topSlot);
        }
      }
    }
    int columnIndex = 0;
    for (SwitchGroup group : groups) {
      List<InventoryCandidate> ordered = orderSwitchGroup(group.items);
      int itemIndex = 0;
      while (itemIndex < ordered.size() && columnIndex < verticalColumns.size()) {
        int topSlot = verticalColumns.get(columnIndex++);
        InventoryCandidate upper = ordered.get(itemIndex++);
        occupied[topSlot] = true;
        result.add(new InventoryPlacement(upper.item, topSlot));
        remaining.remove(upper);
        if (itemIndex < ordered.size()) {
          int lowerSlot = topSlot + 4;
          InventoryCandidate lower = ordered.get(itemIndex++);
          occupied[lowerSlot] = true;
          result.add(new InventoryPlacement(lower.item, lowerSlot));
          remaining.remove(lower);
        }
      }
    }
  }

  private static List<InventoryCandidate> orderSwitchGroup(List<InventoryCandidate> source) {
    List<InventoryCandidate> remaining = new ArrayList<>(source);
    List<InventoryCandidate> ordered = new ArrayList<>(source.size());
    List<InventoryCandidate> tops = new ArrayList<>();
    List<InventoryCandidate> legs = new ArrayList<>();
    for (InventoryCandidate candidate : source) {
      if (isArmorTop(candidate.item)) {
        tops.add(candidate);
      } else if (isArmorLegs(candidate.item)) {
        legs.add(candidate);
      }
    }
    for (InventoryCandidate top : tops) {
      InventoryCandidate legsItem = takeMatchingLegs(top, legs);
      if (legsItem != null) {
        ordered.add(top);
        ordered.add(legsItem);
        remaining.remove(top);
        remaining.remove(legsItem);
      }
    }
    ordered.addAll(remaining);
    return ordered;
  }

  private static InventoryCandidate firstRunePouch(List<InventoryCandidate> candidates) {
    for (InventoryCandidate candidate : candidates) {
      if (isRunePouch(candidate.item)) {
        return candidate;
      }
    }
    return null;
  }

  private static InventoryCandidate findSourceIndex(
      List<InventoryCandidate> candidates, int sourceIndex) {
    for (InventoryCandidate candidate : candidates) {
      if (candidate.sourceIndex == sourceIndex) {
        return candidate;
      }
    }
    return null;
  }

  private static InventoryCandidate takeMatchingLegs(
      InventoryCandidate top, List<InventoryCandidate> availableLegs) {
    if (availableLegs.isEmpty()) {
      return null;
    }
    String topFamily = armorSetFamily(top.item);
    for (int index = 0; index < availableLegs.size(); index++) {
      InventoryCandidate candidate = availableLegs.get(index);
      if (!topFamily.isEmpty() && topFamily.equals(armorSetFamily(candidate.item))) {
        availableLegs.remove(index);
        return candidate;
      }
    }
    return availableLegs.remove(0);
  }

  private static int firstFreeVerticalPair(boolean[] occupied) {
    int[] topRows = {0, 2, 4};
    for (int topRow : topRows) {
      for (int column = 0; column < 4; column++) {
        int topSlot = topRow * 4 + column;
        int legsSlot = topSlot + 4;
        if (!occupied[topSlot] && !occupied[legsSlot]) {
          return topSlot;
        }
      }
    }
    return -1;
  }

  private static int firstFreeSlot(boolean[] occupied) {
    for (int slot = 0; slot < occupied.length; slot++) {
      if (!occupied[slot]) {
        return slot;
      }
    }
    return -1;
  }

  private static int lastFreeSlot(boolean[] occupied) {
    for (int slot = occupied.length - 1; slot >= 0; slot--) {
      if (!occupied[slot]) {
        return slot;
      }
    }
    return -1;
  }

  private static int preferredTravelSlot(List<KitItem> inventoryItems) {
    return containsConcreteRunePouch(inventoryItems)
        ? TRAVEL_SLOT_WITH_RUNE_POUCH
        : TRAVEL_SLOT_WITHOUT_RUNE_POUCH;
  }

  private static boolean containsConcreteRunePouch(List<KitItem> items) {
    if (items == null) {
      return false;
    }
    for (KitItem item : items) {
      if (concrete(item) && isRunePouch(item)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isRunePouch(KitItem item) {
    return normalizeName(item == null ? "" : item.getDisplayName()).contains("rune pouch");
  }

  private static boolean isArmorTop(KitItem item) {
    String name = normalizeName(item == null ? "" : item.getDisplayName());
    String words = " " + name + " ";
    return name.contains("robe top")
        || name.contains("robetop")
        || name.contains("leathertop")
        || name.contains("platebody")
        || name.contains("chainbody")
        || name.contains("chestplate")
        || name.contains("hauberk")
        || name.contains("torso")
        || name.contains("tunic")
        || words.contains(" body ")
        || words.contains(" top ");
  }

  private static boolean isArmorLegs(KitItem item) {
    String name = normalizeName(item == null ? "" : item.getDisplayName());
    String words = " " + name + " ";
    return name.contains("robe bottom")
        || name.contains("robebottom")
        || name.contains("robeskirt")
        || name.contains("leatherskirt")
        || name.contains("platelegs")
        || name.contains("plateskirt")
        || name.contains("chainskirt")
        || name.contains("tassets")
        || name.contains("chaps")
        || name.contains("trousers")
        || name.contains("cuisse")
        || words.contains(" leg ")
        || words.contains(" legs ")
        || words.contains(" skirt ")
        || words.contains(" bottoms ");
  }

  private static String armorSetFamily(KitItem item) {
    return normalizeName(item == null ? "" : item.getDisplayName())
        .replaceAll(
            "\\b(?:robe top|robe bottom|robetop|robebottom|robeskirt|"
                + "leathertop|leatherskirt|platebody|platelegs|plateskirt|"
                + "chainbody|chainskirt|chestplate|hauberk|torso|tunic|"
                + "tassets|chaps|trousers|cuisse|body|top|leg|legs|skirt|bottoms)\\b",
            " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static final class InventoryCandidate {
    private final KitItem item;
    private final int sourceIndex;

    private InventoryCandidate(KitItem item, int sourceIndex) {
      this.item = item;
      this.sourceIndex = sourceIndex;
    }
  }

  private static final class SwitchGroup {
    private final KitItem.SwitchStyle style;
    private final List<InventoryCandidate> items = new ArrayList<>();

    private SwitchGroup(KitItem.SwitchStyle style) {
      this.style = style == null ? KitItem.SwitchStyle.OTHER : style;
    }
  }

  static final class InventoryPlacement {
    private final KitItem item;
    private final int slotIndex;

    private InventoryPlacement(KitItem item, int slotIndex) {
      this.item = item;
      this.slotIndex = slotIndex;
    }

    KitItem getItem() {
      return item;
    }

    int getSlotIndex() {
      return slotIndex;
    }
  }

  private static int bankInventoryVisualGroup(KitItem item) {
    String name = normalizeName(item == null ? "" : item.getDisplayName());
    if (name.contains("saradomin brew")) {
      return 3;
    }
    if (name.contains("goading potion")) {
      return 0;
    }
    if (name.contains("super restore")
        || name.contains("prayer potion")
        || name.contains("prayer restoration")
        || name.contains("prayer regeneration")
        || name.contains("sanfew serum")) {
      return 4;
    }
    if (isBankInventoryFood(name)) {
      return 5;
    }
    if (name.contains("anti venom")
        || name.contains("antivenom")
        || name.contains("antipoison")
        || name.contains("stamina potion")
        || name.contains("antifire")) {
      return 2;
    }
    if (name.contains("combat potion")
        || name.contains("ranging potion")
        || name.contains("magic potion")
        || name.contains("bastion potion")
        || name.contains("battlemage potion")
        || name.contains("ancient brew")
        || name.contains("forgotten brew")
        || name.contains("imbued heart")
        || name.contains("saturated heart")) {
      return 1;
    }
    return 0;
  }

  private static boolean isBankInventoryFood(String name) {
    return name.contains("anglerfish")
        || name.contains("manta ray")
        || name.contains("dark crab")
        || name.contains("shark")
        || name.contains("sea turtle")
        || name.contains("karambwan")
        || name.contains("guthix rest")
        || name.contains("monkfish")
        || name.contains("high healing food");
  }

  private static String bankInventoryVisualFamily(KitItem item) {
    return normalizeName(item == null ? "" : item.getDisplayName())
        .replaceFirst("^divine ", "")
        .replaceFirst(" [1-4]$", "")
        .trim();
  }

  private static String normalizeName(String value) {
    return value == null
        ? ""
        : value
            .toLowerCase(java.util.Locale.ENGLISH)
            .replace('’', '\'')
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
  }

  private static boolean place(
      Layout layout, Set<Integer> taggedItemIds, KitItem item, int position) {
    if (!concrete(item)) {
      return false;
    }
    return placeItemId(layout, taggedItemIds, item.getItemId(), position);
  }

  private static boolean placeInventory(
      Layout layout, Set<Integer> taggedItemIds, KitItem item, int position) {
    if (!concrete(item)) {
      return false;
    }
    return placeInventoryItemId(layout, taggedItemIds, item.getItemId(), position);
  }

  private static boolean placeInventoryItemId(
      Layout layout, Set<Integer> taggedItemIds, int rawItemId, int position) {
    if (rawItemId <= 0) {
      return false;
    }
    taggedItemIds.add(rawItemId);
    layout.setItemAtPos(rawItemId, position);
    return true;
  }

  private static boolean layoutContainsItemId(Layout layout, int itemId) {
    if (layout == null || itemId <= 0 || layout.getLayout() == null) {
      return false;
    }
    for (int value : layout.getLayout()) {
      if (value == itemId) {
        return true;
      }
    }
    return false;
  }

  private static boolean placeNestedQuiverAmmoItemId(
      Layout layout, Set<Integer> taggedItemIds, int rawItemId, int position) {
    if (rawItemId <= 0) {
      return false;
    }
    taggedItemIds.add(rawItemId);
    layout.setItemAtPos(rawItemId, position);
    return true;
  }

  private static boolean placePreparationReferenceItemId(
      Layout layout, Set<Integer> taggedItemIds, int rawItemId, int position) {
    if (rawItemId <= 0) {
      return false;
    }
    taggedItemIds.add(rawItemId);
    layout.setItemAtPos(rawItemId, position);
    return true;
  }

  private static boolean placeItemId(
      Layout layout, Set<Integer> taggedItemIds, int rawItemId, int position) {
    if (rawItemId <= 0 || !taggedItemIds.add(rawItemId)) {
      return false;
    }
    layout.setItemAtPos(rawItemId, position);
    return true;
  }

  private static boolean concrete(KitItem item) {
    return item != null && item.hasItemId();
  }

  private static int position(int row, int column) {
    return row * BANK_COLUMNS + column;
  }

  static boolean hasDedicatedExtraQuiverAmmoPositionForRegression() {
    return EQUIPMENT_POSITIONS.length > 11
        && EXTRA_QUIVER_AMMO_POSITION == position(2, 2)
        && EQUIPMENT_POSITIONS[3] == position(3, 2)
        && EQUIPMENT_POSITIONS[11] == EXTRA_QUIVER_AMMO_POSITION
        && EXTRA_QUIVER_AMMO_POSITION != EQUIPMENT_POSITIONS[3];
  }

  static boolean preparationReferencesRenderBelowGearForRegression() {
    int lowestEquipmentRow = 0;
    for (int equipmentPosition : EQUIPMENT_POSITIONS) {
      lowestEquipmentRow = Math.max(lowestEquipmentRow, equipmentPosition / BANK_COLUMNS);
    }
    return PREPARATION_REFERENCE_START_ROW > lowestEquipmentRow
        && PREPARATION_REFERENCE_START_COLUMN == 0
        && PREPARATION_REFERENCE_COLUMNS == 4;
  }

  static boolean usesExternalStagingTravelStrip(int authoritativeTravelSlotIndex) {
    return authoritativeTravelSlotIndex < 0;
  }

  public static final class Result {
    private final boolean success;
    private final String message;

    private Result(boolean success, String message) {
      this.success = success;
      this.message = message;
    }

    public static Result success(String message) {
      return new Result(true, message);
    }

    public static Result failure(String message) {
      return new Result(false, message);
    }

    public boolean isSuccess() {
      return success;
    }

    public String getMessage() {
      return message;
    }
  }
}
