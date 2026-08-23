package com.slayerplus;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

class QuiverAmmo {
  private static final Set<Integer> USABLE_DIZANA_VARIANT_IDS = createUsableDizanaVariantIds();

  private QuiverAmmo() {}

  static boolean isUsableDizanaVariant(int rawItemId) {
    return rawItemId > 0 && USABLE_DIZANA_VARIANT_IDS.contains(rawItemId);
  }

  static int preferUsableDizanaVariant(int currentItemId, int candidateItemId) {
    return dizanaPreference(candidateItemId) > dizanaPreference(currentItemId)
        ? candidateItemId
        : currentItemId;
  }

  private static int dizanaPreference(int rawItemId) {
    if (!isUsableDizanaVariant(rawItemId)) {
      return -1;
    }
    switch (rawItemId) {
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
        return 1;
    }
  }

  private static Set<Integer> createUsableDizanaVariantIds() {
    Set<Integer> itemIds = new HashSet<>();
    addVariationFamily(itemIds, ItemID.DIZANAS_QUIVER_CHARGED);
    addVariationFamily(itemIds, ItemID.DIZANAS_QUIVER_INFINITE);
    addVariationFamily(itemIds, ItemID.SKILLCAPE_MAX_DIZANAS);
    itemIds.remove(ItemID.DIZANAS_QUIVER_BROKEN);
    itemIds.remove(ItemID.DIZANAS_QUIVER_INFINITE_BROKEN);
    itemIds.remove(ItemID.SKILLCAPE_MAX_DIZANAS_BROKEN);
    itemIds.remove(ItemID.SKILLCAPE_MAX_HOOD_DIZANAS);
    itemIds.remove(ItemID.DIZANAS_QUIVER_TROUVER_BROKEN);
    itemIds.remove(ItemID.DIZANAS_QUIVER_TROUVER_MANGLED);
    itemIds.remove(ItemID.DIZANAS_QUIVER_INFINITE_TROUVER_BROKEN);
    itemIds.remove(ItemID.DIZANAS_QUIVER_INFINITE_TROUVER_MANGLED);
    itemIds.remove(ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_BROKEN);
    itemIds.remove(ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_MANGLED);
    return Collections.unmodifiableSet(itemIds);
  }

  private static void addVariationFamily(Set<Integer> itemIds, int representativeItemId) {
    int canonicalItemId = ItemVariationMapping.map(representativeItemId);
    itemIds.add(canonicalItemId);
    itemIds.addAll(ItemVariationMapping.getVariations(canonicalItemId));
  }

  static int normalizeSeekingArrowItemId(int rawItemId) {
    if (rawItemId <= 0) {
      return -1;
    }
    switch (rawItemId) {
      case ItemID.SEEKING_BRONZE_ARROW:
      case ItemID.SEEKING_BRONZE_ARROW_2:
      case ItemID.SEEKING_BRONZE_ARROW_3:
      case ItemID.SEEKING_BRONZE_ARROW_4:
      case ItemID.SEEKING_BRONZE_ARROW_5:
        return ItemID.SEEKING_BRONZE_ARROW;
      case ItemID.SEEKING_IRON_ARROW:
      case ItemID.SEEKING_IRON_ARROW_2:
      case ItemID.SEEKING_IRON_ARROW_3:
      case ItemID.SEEKING_IRON_ARROW_4:
      case ItemID.SEEKING_IRON_ARROW_5:
        return ItemID.SEEKING_IRON_ARROW;
      case ItemID.SEEKING_STEEL_ARROW:
      case ItemID.SEEKING_STEEL_ARROW_2:
      case ItemID.SEEKING_STEEL_ARROW_3:
      case ItemID.SEEKING_STEEL_ARROW_4:
      case ItemID.SEEKING_STEEL_ARROW_5:
        return ItemID.SEEKING_STEEL_ARROW;
      case ItemID.SEEKING_MITHRIL_ARROW:
      case ItemID.SEEKING_MITHRIL_ARROW_2:
      case ItemID.SEEKING_MITHRIL_ARROW_3:
      case ItemID.SEEKING_MITHRIL_ARROW_4:
      case ItemID.SEEKING_MITHRIL_ARROW_5:
        return ItemID.SEEKING_MITHRIL_ARROW;
      case ItemID.SEEKING_ADAMANT_ARROW:
      case ItemID.SEEKING_ADAMANT_ARROW_2:
      case ItemID.SEEKING_ADAMANT_ARROW_3:
      case ItemID.SEEKING_ADAMANT_ARROW_4:
      case ItemID.SEEKING_ADAMANT_ARROW_5:
        return ItemID.SEEKING_ADAMANT_ARROW;
      case ItemID.SEEKING_RUNE_ARROW:
      case ItemID.SEEKING_RUNE_ARROW_2:
      case ItemID.SEEKING_RUNE_ARROW_3:
      case ItemID.SEEKING_RUNE_ARROW_4:
      case ItemID.SEEKING_RUNE_ARROW_5:
        return ItemID.SEEKING_RUNE_ARROW;
      case ItemID.SEEKING_AMETHYST_ARROW:
      case ItemID.SEEKING_AMETHYST_ARROW_2:
      case ItemID.SEEKING_AMETHYST_ARROW_3:
      case ItemID.SEEKING_AMETHYST_ARROW_4:
      case ItemID.SEEKING_AMETHYST_ARROW_5:
        return ItemID.SEEKING_AMETHYST_ARROW;
      case ItemID.SEEKING_DRAGON_ARROW:
      case ItemID.SEEKING_DRAGON_ARROW2:
      case ItemID.SEEKING_DRAGON_ARROW3:
      case ItemID.SEEKING_DRAGON_ARROW4:
      case ItemID.SEEKING_DRAGON_ARROW5:
        return ItemID.SEEKING_DRAGON_ARROW;
      case ItemID.SEEKING_SLAYER_BROAD_ARROWS:
      case ItemID.SEEKING_SLAYER_BROAD_ARROWS_2:
      case ItemID.SEEKING_SLAYER_BROAD_ARROWS_3:
      case ItemID.SEEKING_SLAYER_BROAD_ARROWS_4:
      case ItemID.SEEKING_SLAYER_BROAD_ARROWS_5:
        return ItemID.SEEKING_SLAYER_BROAD_ARROWS;
      default:
        return rawItemId;
    }
  }

  static boolean isSeekingArrow(int itemId) {
    switch (normalizeSeekingArrowItemId(itemId)) {
      case ItemID.SEEKING_BRONZE_ARROW:
      case ItemID.SEEKING_IRON_ARROW:
      case ItemID.SEEKING_STEEL_ARROW:
      case ItemID.SEEKING_MITHRIL_ARROW:
      case ItemID.SEEKING_ADAMANT_ARROW:
      case ItemID.SEEKING_RUNE_ARROW:
      case ItemID.SEEKING_AMETHYST_ARROW:
      case ItemID.SEEKING_DRAGON_ARROW:
      case ItemID.SEEKING_SLAYER_BROAD_ARROWS:
        return true;
      default:
        return false;
    }
  }

  static String seekingArrowMatchName(int itemId) {
    switch (normalizeSeekingArrowItemId(itemId)) {
      case ItemID.SEEKING_BRONZE_ARROW:
        return "seeking bronze arrow";
      case ItemID.SEEKING_IRON_ARROW:
        return "seeking iron arrow";
      case ItemID.SEEKING_STEEL_ARROW:
        return "seeking steel arrow";
      case ItemID.SEEKING_MITHRIL_ARROW:
        return "seeking mithril arrow";
      case ItemID.SEEKING_ADAMANT_ARROW:
        return "seeking adamant arrow";
      case ItemID.SEEKING_RUNE_ARROW:
        return "seeking rune arrow";
      case ItemID.SEEKING_AMETHYST_ARROW:
        return "seeking amethyst arrow";
      case ItemID.SEEKING_DRAGON_ARROW:
        return "seeking dragon arrow";
      case ItemID.SEEKING_SLAYER_BROAD_ARROWS:
        return "seeking broad arrow";
      default:
        return "";
    }
  }

  static boolean isUnderlyingArrow(int ordinaryItemId, int seekingItemId) {
    switch (normalizeSeekingArrowItemId(seekingItemId)) {
      case ItemID.SEEKING_BRONZE_ARROW:
        return ordinaryItemId == ItemID.BRONZE_ARROW;
      case ItemID.SEEKING_IRON_ARROW:
        return ordinaryItemId == ItemID.IRON_ARROW;
      case ItemID.SEEKING_STEEL_ARROW:
        return ordinaryItemId == ItemID.STEEL_ARROW;
      case ItemID.SEEKING_MITHRIL_ARROW:
        return ordinaryItemId == ItemID.MITHRIL_ARROW;
      case ItemID.SEEKING_ADAMANT_ARROW:
        return ordinaryItemId == ItemID.ADAMANT_ARROW;
      case ItemID.SEEKING_RUNE_ARROW:
        return ordinaryItemId == ItemID.RUNE_ARROW;
      case ItemID.SEEKING_AMETHYST_ARROW:
        return ordinaryItemId == ItemID.AMETHYST_ARROW;
      case ItemID.SEEKING_DRAGON_ARROW:
        return ordinaryItemId == ItemID.DRAGON_ARROW;
      case ItemID.SEEKING_SLAYER_BROAD_ARROWS:
        return ordinaryItemId == ItemID.SLAYER_BROAD_ARROWS;
      default:
        return false;
    }
  }

  static int restoreSeekingIdentityFromPlaceholders(
      int rawAmmoItemId, Iterable<Integer> canonicalPlaceholderItemIds) {
    int ammoItemId = normalizeSeekingArrowItemId(rawAmmoItemId);
    if (ammoItemId <= 0 || isSeekingArrow(ammoItemId) || canonicalPlaceholderItemIds == null) {
      return ammoItemId;
    }
    for (Integer rawPlaceholderItemId : canonicalPlaceholderItemIds) {
      if (rawPlaceholderItemId == null) {
        continue;
      }
      int placeholderItemId = normalizeSeekingArrowItemId(rawPlaceholderItemId);
      if (isSeekingArrow(placeholderItemId) && isUnderlyingArrow(ammoItemId, placeholderItemId)) {
        return placeholderItemId;
      }
    }
    return ammoItemId;
  }

  static int resolveExactIdentity(int widgetItemId, int varpItemId, int cachedItemId) {
    int widget = normalizeSeekingArrowItemId(widgetItemId);
    int varp = normalizeSeekingArrowItemId(varpItemId);
    int cached = normalizeSeekingArrowItemId(cachedItemId);
    if (isSeekingArrow(varp)) {
      return varp;
    }
    if (isSeekingArrow(widget) && (varp <= 0 || isUnderlyingArrow(varp, widget))) {
      return widget;
    }
    if (varp > 0) {
      return varp;
    }
    if (widget > 0) {
      return widget;
    }
    return cached;
  }

  static Snapshot resolveSnapshot(
      boolean wearingQuiver,
      int storedItemId,
      int storedQuantity,
      int widgetItemId,
      int widgetQuantity,
      int varpItemId,
      int varpQuantity,
      int cachedItemId) {
    Snapshot stored = Snapshot.of(storedItemId, storedQuantity);
    int liveItemId = resolveExactIdentity(widgetItemId, varpItemId, cachedItemId);
    int liveQuantity = varpQuantity > 0 ? varpQuantity : widgetQuantity > 0 ? widgetQuantity : 0;
    Snapshot live = Snapshot.of(liveItemId, liveQuantity);
    if (!wearingQuiver) {
      Snapshot current = stored.isPresent() ? preserveSeekingIdentity(stored, live) : live;
      return preserveSeekingIdentity(current, Snapshot.of(cachedItemId, current.quantity));
    }
    if (live.isPresent()) {
      return live;
    }
    if (stored.isPresent()) {
      return stored;
    }
    return Snapshot.empty();
  }

  static Snapshot restoreHiddenBankedSeekingAmmo(
      boolean bankedUsableQuiverFound,
      Snapshot liveSnapshot,
      Iterable<Integer> exactIdentityHints) {
    Snapshot current = liveSnapshot == null ? Snapshot.empty() : liveSnapshot;
    if (current.isPresent() || !bankedUsableQuiverFound) {
      return current;
    }
    int bestItemId = -1;
    int bestRank = Integer.MAX_VALUE;
    if (exactIdentityHints != null) {
      for (Integer rawItemId : exactIdentityHints) {
        if (rawItemId == null) {
          continue;
        }
        int itemId = normalizeSeekingArrowItemId(rawItemId);
        if (!isSeekingArrow(itemId)) {
          continue;
        }
        int rank = infernoPreference(itemId);
        if (rank < bestRank) {
          bestItemId = itemId;
          bestRank = rank;
        }
      }
    }
    return bestItemId > 0 ? Snapshot.of(bestItemId, 1) : Snapshot.empty();
  }

  private static Snapshot preserveSeekingIdentity(Snapshot primary, Snapshot secondary) {
    if (!primary.isPresent() || !secondary.isPresent()) {
      return primary;
    }
    if (!isSeekingArrow(primary.itemId)
        && isSeekingArrow(secondary.itemId)
        && isUnderlyingArrow(primary.itemId, secondary.itemId)) {
      return Snapshot.of(secondary.itemId, primary.quantity);
    }
    return primary;
  }

  static int infernoPreference(int rawItemId) {
    int itemId = normalizeSeekingArrowItemId(rawItemId);
    switch (itemId) {
      case ItemID.SEEKING_DRAGON_ARROW:
        return 0;
      case ItemID.SEEKING_AMETHYST_ARROW:
        return 1;
      case ItemID.DRAGON_ARROW:
        return 2;
      case ItemID.SEEKING_RUNE_ARROW:
        return 3;
      case ItemID.AMETHYST_ARROW:
        return 4;
      case ItemID.RUNE_ARROW:
        return 5;
      case ItemID.SEEKING_ADAMANT_ARROW:
        return 6;
      case ItemID.SEEKING_SLAYER_BROAD_ARROWS:
        return 7;
      case ItemID.ADAMANT_ARROW:
        return 8;
      case ItemID.SEEKING_MITHRIL_ARROW:
        return 9;
      case ItemID.SLAYER_BROAD_ARROWS:
        return 10;
      case ItemID.SEEKING_STEEL_ARROW:
        return 11;
      case ItemID.MITHRIL_ARROW:
        return 12;
      case ItemID.SEEKING_IRON_ARROW:
        return 13;
      case ItemID.STEEL_ARROW:
        return 14;
      case ItemID.SEEKING_BRONZE_ARROW:
        return 15;
      case ItemID.IRON_ARROW:
        return 16;
      case ItemID.BRONZE_ARROW:
        return 17;
      default:
        return Integer.MAX_VALUE;
    }
  }

  static int preferBetterInfernoArrow(int recommendedItemId, int liveQuiverItemId) {
    int recommendedRank = infernoPreference(recommendedItemId);
    int liveRank = infernoPreference(liveQuiverItemId);
    if (liveRank < recommendedRank) {
      return normalizeSeekingArrowItemId(liveQuiverItemId);
    }
    return recommendedItemId;
  }

  static final class Snapshot {
    private static final Snapshot EMPTY = new Snapshot(-1, 0);
    private final int itemId;
    private final int quantity;

    private Snapshot(int itemId, int quantity) {
      this.itemId = itemId;
      this.quantity = quantity;
    }

    static Snapshot empty() {
      return EMPTY;
    }

    static Snapshot of(int rawItemId, int quantity) {
      if (rawItemId <= 0 || quantity <= 0) {
        return EMPTY;
      }
      return new Snapshot(normalizeSeekingArrowItemId(rawItemId), quantity);
    }

    int getItemId() {
      return itemId;
    }

    int getQuantity() {
      return quantity;
    }

    boolean isPresent() {
      return itemId > 0 && quantity > 0;
    }
  }
}
