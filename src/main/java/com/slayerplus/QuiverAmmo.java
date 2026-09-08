package com.slayerplus;

import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

final class QuiverAmmo {
  private static final Set<Integer> USABLE_QUIVERS = usableQuivers();
  private static final Map<Integer, Arrow> ARROWS = arrows();

  private QuiverAmmo() {}

  static boolean isUsableDizanaVariant(int id) {
    return id > 0 && USABLE_QUIVERS.contains(id);
  }

  static int preferUsableDizanaVariant(int current, int candidate) {
    return quiverRank(candidate) > quiverRank(current) ? candidate : current;
  }

  private static int quiverRank(int id) {
    if (!isUsableDizanaVariant(id)) {
      return -1;
    }
    if (id == ItemID.SKILLCAPE_MAX_DIZANAS || id == ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER) {
      return 400;
    }
    if (id == ItemID.DIZANAS_QUIVER_INFINITE || id == ItemID.DIZANAS_QUIVER_INFINITE_TROUVER) {
      return 300;
    }
    if (id == ItemID.DIZANAS_QUIVER_CHARGED || id == ItemID.DIZANAS_QUIVER_CHARGED_TROUVER) {
      return 200;
    }
    if (id == ItemID.DIZANAS_QUIVER_UNCHARGED || id == ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER) {
      return 100;
    }
    return 1;
  }

  private static Set<Integer> usableQuivers() {
    Set<Integer> ids = new HashSet<>();
    addFamily(ids, ItemID.DIZANAS_QUIVER_CHARGED);
    addFamily(ids, ItemID.DIZANAS_QUIVER_INFINITE);
    addFamily(ids, ItemID.SKILLCAPE_MAX_DIZANAS);
    int[] unusable = {
      ItemID.DIZANAS_QUIVER_BROKEN,
      ItemID.DIZANAS_QUIVER_INFINITE_BROKEN,
      ItemID.SKILLCAPE_MAX_DIZANAS_BROKEN,
      ItemID.SKILLCAPE_MAX_HOOD_DIZANAS,
      ItemID.DIZANAS_QUIVER_TROUVER_BROKEN,
      ItemID.DIZANAS_QUIVER_TROUVER_MANGLED,
      ItemID.DIZANAS_QUIVER_INFINITE_TROUVER_BROKEN,
      ItemID.DIZANAS_QUIVER_INFINITE_TROUVER_MANGLED,
      ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_BROKEN,
      ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER_MANGLED
    };
    for (int id : unusable) {
      ids.remove(id);
    }
    return Set.copyOf(ids);
  }

  private static void addFamily(Set<Integer> ids, int representative) {
    int canonical = ItemVariationMapping.map(representative);
    ids.add(canonical);
    ids.addAll(ItemVariationMapping.getVariations(canonical));
  }

  private static Map<Integer, Arrow> arrows() {
    Map<Integer, Arrow> result = new HashMap<>();
    add(
        result,
        ItemID.SEEKING_BRONZE_ARROW,
        ItemID.BRONZE_ARROW,
        "seeking bronze arrow",
        15,
        ItemID.SEEKING_BRONZE_ARROW_2,
        ItemID.SEEKING_BRONZE_ARROW_3,
        ItemID.SEEKING_BRONZE_ARROW_4,
        ItemID.SEEKING_BRONZE_ARROW_5);
    add(
        result,
        ItemID.SEEKING_IRON_ARROW,
        ItemID.IRON_ARROW,
        "seeking iron arrow",
        13,
        ItemID.SEEKING_IRON_ARROW_2,
        ItemID.SEEKING_IRON_ARROW_3,
        ItemID.SEEKING_IRON_ARROW_4,
        ItemID.SEEKING_IRON_ARROW_5);
    add(
        result,
        ItemID.SEEKING_STEEL_ARROW,
        ItemID.STEEL_ARROW,
        "seeking steel arrow",
        11,
        ItemID.SEEKING_STEEL_ARROW_2,
        ItemID.SEEKING_STEEL_ARROW_3,
        ItemID.SEEKING_STEEL_ARROW_4,
        ItemID.SEEKING_STEEL_ARROW_5);
    add(
        result,
        ItemID.SEEKING_MITHRIL_ARROW,
        ItemID.MITHRIL_ARROW,
        "seeking mithril arrow",
        9,
        ItemID.SEEKING_MITHRIL_ARROW_2,
        ItemID.SEEKING_MITHRIL_ARROW_3,
        ItemID.SEEKING_MITHRIL_ARROW_4,
        ItemID.SEEKING_MITHRIL_ARROW_5);
    add(
        result,
        ItemID.SEEKING_ADAMANT_ARROW,
        ItemID.ADAMANT_ARROW,
        "seeking adamant arrow",
        6,
        ItemID.SEEKING_ADAMANT_ARROW_2,
        ItemID.SEEKING_ADAMANT_ARROW_3,
        ItemID.SEEKING_ADAMANT_ARROW_4,
        ItemID.SEEKING_ADAMANT_ARROW_5);
    add(
        result,
        ItemID.SEEKING_RUNE_ARROW,
        ItemID.RUNE_ARROW,
        "seeking rune arrow",
        3,
        ItemID.SEEKING_RUNE_ARROW_2,
        ItemID.SEEKING_RUNE_ARROW_3,
        ItemID.SEEKING_RUNE_ARROW_4,
        ItemID.SEEKING_RUNE_ARROW_5);
    add(
        result,
        ItemID.SEEKING_AMETHYST_ARROW,
        ItemID.AMETHYST_ARROW,
        "seeking amethyst arrow",
        1,
        ItemID.SEEKING_AMETHYST_ARROW_2,
        ItemID.SEEKING_AMETHYST_ARROW_3,
        ItemID.SEEKING_AMETHYST_ARROW_4,
        ItemID.SEEKING_AMETHYST_ARROW_5);
    add(
        result,
        ItemID.SEEKING_DRAGON_ARROW,
        ItemID.DRAGON_ARROW,
        "seeking dragon arrow",
        0,
        ItemID.SEEKING_DRAGON_ARROW2,
        ItemID.SEEKING_DRAGON_ARROW3,
        ItemID.SEEKING_DRAGON_ARROW4,
        ItemID.SEEKING_DRAGON_ARROW5);
    add(
        result,
        ItemID.SEEKING_SLAYER_BROAD_ARROWS,
        ItemID.SLAYER_BROAD_ARROWS,
        "seeking broad arrow",
        7,
        ItemID.SEEKING_SLAYER_BROAD_ARROWS_2,
        ItemID.SEEKING_SLAYER_BROAD_ARROWS_3,
        ItemID.SEEKING_SLAYER_BROAD_ARROWS_4,
        ItemID.SEEKING_SLAYER_BROAD_ARROWS_5);
    return Map.copyOf(result);
  }

  private static void add(
      Map<Integer, Arrow> map,
      int canonical,
      int ordinary,
      String name,
      int rank,
      int... variants) {
    Arrow arrow = new Arrow(canonical, ordinary, name, rank);
    map.put(canonical, arrow);
    for (int id : variants) {
      map.put(id, arrow);
    }
  }

  static int arrow(int id) {
    Arrow arrow = ARROWS.get(id);
    return id <= 0 ? -1 : arrow == null ? id : arrow.canonical;
  }

  static boolean isSeekingArrow(int id) {
    return ARROWS.containsKey(id);
  }

  static String seekingArrowMatchName(int id) {
    Arrow arrow = ARROWS.get(id);
    return arrow == null ? "" : arrow.name;
  }

  static boolean isUnderlyingArrow(int ordinary, int seeking) {
    Arrow arrow = ARROWS.get(seeking);
    return arrow != null && arrow.ordinary == ordinary;
  }

  static int restoreSeekingIdentityFromPlaceholders(int rawAmmo, Iterable<Integer> hints) {
    int ammo = arrow(rawAmmo);
    if (ammo <= 0 || isSeekingArrow(ammo) || hints == null) {
      return ammo;
    }
    for (Integer raw : hints) {
      if (raw != null && isUnderlyingArrow(ammo, raw)) {
        return arrow(raw);
      }
    }
    return ammo;
  }

  static int resolveExactIdentity(int widgetId, int varpId, int cachedId) {
    int widget = arrow(widgetId), varp = arrow(varpId);
    if (isSeekingArrow(varp)) {
      return varp;
    }
    if (isSeekingArrow(widget) && (varp <= 0 || isUnderlyingArrow(varp, widget))) {
      return widget;
    }
    return varp > 0 ? varp : widget > 0 ? widget : arrow(cachedId);
  }

  static Snapshot resolveSnapshot(
      boolean wearing,
      int storedId,
      int storedQty,
      int widgetId,
      int widgetQty,
      int varpId,
      int varpQty,
      int cachedId) {
    Snapshot stored = Snapshot.of(storedId, storedQty);
    Snapshot live =
        Snapshot.of(
            resolveExactIdentity(widgetId, varpId, cachedId),
            varpQty > 0 ? varpQty : Math.max(0, widgetQty));
    if (!wearing) {
      Snapshot current = stored.isPresent() ? preserveSeekingIdentity(stored, live) : live;
      return preserveSeekingIdentity(current, Snapshot.of(cachedId, current.quantity));
    }
    return live.isPresent() ? live : stored;
  }

  static Snapshot restoreHiddenBankedSeekingAmmo(
      boolean quiverFound, Snapshot live, Iterable<Integer> hints) {
    Snapshot current = live == null ? Snapshot.empty() : live;
    if (current.isPresent() || !quiverFound) {
      return current;
    }
    int best = -1, rank = Integer.MAX_VALUE;
    if (hints != null) {
      for (Integer raw : hints) {
        int candidate = raw == null ? -1 : arrow(raw);
        int candidateRank = infernoPreference(candidate);
        if (isSeekingArrow(candidate) && candidateRank < rank) {
          best = candidate;
          rank = candidateRank;
        }
      }
    }
    return Snapshot.of(best, 1);
  }

  private static Snapshot preserveSeekingIdentity(Snapshot primary, Snapshot secondary) {
    if (primary.isPresent()
        && secondary.isPresent()
        && !isSeekingArrow(primary.itemId)
        && isSeekingArrow(secondary.itemId)
        && isUnderlyingArrow(primary.itemId, secondary.itemId)) {
      return Snapshot.of(secondary.itemId, primary.quantity);
    }
    return primary;
  }

  static int infernoPreference(int id) {
    Arrow arrow = ARROWS.get(id);
    if (arrow != null) {
      return arrow.rank;
    }
    int normalized = arrow(id);
    int[] ordinary = {
      ItemID.DRAGON_ARROW,
      ItemID.AMETHYST_ARROW,
      ItemID.RUNE_ARROW,
      ItemID.ADAMANT_ARROW,
      ItemID.SLAYER_BROAD_ARROWS,
      ItemID.MITHRIL_ARROW,
      ItemID.STEEL_ARROW,
      ItemID.IRON_ARROW,
      ItemID.BRONZE_ARROW
    };
    int[] ranks = {2, 4, 5, 8, 10, 12, 14, 16, 17};
    for (int index = 0; index < ordinary.length; index++) {
      if (normalized == ordinary[index]) {
        return ranks[index];
      }
    }
    return Integer.MAX_VALUE;
  }

  static int preferBetterInfernoArrow(int recommended, int live) {
    return infernoPreference(live) < infernoPreference(recommended) ? arrow(live) : recommended;
  }

  @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
  private static final class Arrow {
    final int canonical, ordinary;
    final String name;
    final int rank;
  }

  @Getter
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  static final class Snapshot {
    private static final Snapshot EMPTY = new Snapshot(-1, 0);
    private final int itemId, quantity;

    static Snapshot empty() {
      return EMPTY;
    }

    static Snapshot of(int id, int quantity) {
      return id > 0 && quantity > 0 ? new Snapshot(arrow(id), quantity) : EMPTY;
    }

    boolean isPresent() {
      return itemId > 0 && quantity > 0;
    }
  }
}
