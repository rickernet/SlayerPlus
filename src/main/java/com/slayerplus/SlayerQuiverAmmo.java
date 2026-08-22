package com.slayerplus;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Shared, gameval-backed identity rules for Dizana's quiver ammunition.
 *
 * <p>Seeking arrows have several live display IDs. Item names and equipment
 * stats can briefly lag a game update, so ownership and recommendation code
 * must recognize these IDs without depending on either cache.</p>
 */
final class SlayerQuiverAmmo
{
	/*
	 * Keep this in step with RuneLite's Ammo plugin instead of maintaining a
	 * fragile list of individual IDs. These three variation families include
	 * the usable uncharged, charged, blessed/infinite, Trouver-locked, and
	 * Dizana max-cape forms, while excluding broken, mangled, and hood items.
	 */
	private static final Set<Integer> USABLE_DIZANA_VARIANT_IDS =
		createUsableDizanaVariantIds();

	private SlayerQuiverAmmo()
	{
	}

	static boolean isUsableDizanaVariant(final int rawItemId)
	{
		return rawItemId > 0 && USABLE_DIZANA_VARIANT_IDS.contains(rawItemId);
	}

	/**
	 * Select a usable Dizana variant while scanning a live item container.
	 * Broken, mangled and hood objects can never win. Known combat variants are
	 * ordered max cape, blessed, charged, then uncharged; any future usable
	 * variation-family member still receives a positive fallback score.
	 */
	static int preferUsableDizanaVariant(
		final int currentItemId,
		final int candidateItemId)
	{
		return dizanaPreference(candidateItemId)
			> dizanaPreference(currentItemId)
				? candidateItemId
				: currentItemId;
	}

	private static int dizanaPreference(final int rawItemId)
	{
		if (!isUsableDizanaVariant(rawItemId))
		{
			return -1;
		}

		switch (rawItemId)
		{
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

	private static Set<Integer> createUsableDizanaVariantIds()
	{
		final Set<Integer> itemIds = new HashSet<>();
		addVariationFamily(itemIds, ItemID.DIZANAS_QUIVER_CHARGED);
		addVariationFamily(itemIds, ItemID.DIZANAS_QUIVER_INFINITE);
		addVariationFamily(itemIds, ItemID.SKILLCAPE_MAX_DIZANAS);

		/*
		 * RuneLite deliberately groups repair/lock-state variants for pricing and
		 * bank matching. Those objects are not wearable quivers, so explicitly
		 * remove them after expanding the otherwise future-safe families.
		 */
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

	private static void addVariationFamily(
		final Set<Integer> itemIds,
		final int representativeItemId)
	{
		final int canonicalItemId = ItemVariationMapping.map(representativeItemId);
		itemIds.add(canonicalItemId);
		itemIds.addAll(ItemVariationMapping.getVariations(canonicalItemId));
	}

	static int normalizeSeekingArrowItemId(final int rawItemId)
	{
		if (rawItemId <= 0)
		{
			return -1;
		}

		switch (rawItemId)
		{
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

	static boolean isSeekingArrow(final int itemId)
	{
		switch (normalizeSeekingArrowItemId(itemId))
		{
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

	static String seekingArrowMatchName(final int itemId)
	{
		switch (normalizeSeekingArrowItemId(itemId))
		{
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

	static boolean isUnderlyingArrow(
		final int ordinaryItemId,
		final int seekingItemId)
	{
		switch (normalizeSeekingArrowItemId(seekingItemId))
		{
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

	/**
	 * Restore the exact Seeking identity of a quiver-held stack from a matching
	 * Jagex bank placeholder.
	 *
	 * <p>The dedicated quiver container can expose only the underlying ordinary
	 * arrow ID while the quiver is unequipped. A retained bank placeholder is
	 * then the only exact identity still visible to the client. The placeholder
	 * is evidence only: it may refine an existing, matching quiver stack, but it
	 * can never create ownership by itself.</p>
	 */
	static int restoreSeekingIdentityFromPlaceholders(
		final int rawAmmoItemId,
		final Iterable<Integer> canonicalPlaceholderItemIds)
	{
		final int ammoItemId = normalizeSeekingArrowItemId(rawAmmoItemId);
		if (ammoItemId <= 0
			|| isSeekingArrow(ammoItemId)
			|| canonicalPlaceholderItemIds == null)
		{
			return ammoItemId;
		}

		for (final Integer rawPlaceholderItemId : canonicalPlaceholderItemIds)
		{
			if (rawPlaceholderItemId == null)
			{
				continue;
			}

			final int placeholderItemId =
				normalizeSeekingArrowItemId(rawPlaceholderItemId);
			if (isSeekingArrow(placeholderItemId)
				&& isUnderlyingArrow(ammoItemId, placeholderItemId))
			{
				return placeholderItemId;
			}
		}
		return ammoItemId;
	}

	/**
	 * Resolve already-canonicalized, compatible live candidates without allowing
	 * a stale cached Seeking arrow to replace current game state.
	 */
	static int resolveExactIdentity(
		final int widgetItemId,
		final int varpItemId,
		final int cachedItemId)
	{
		final int widget = normalizeSeekingArrowItemId(widgetItemId);
		final int varp = normalizeSeekingArrowItemId(varpItemId);
		final int cached = normalizeSeekingArrowItemId(cachedItemId);

		/* The varp is authoritative when it already carries exact Seeking identity. */
		if (isSeekingArrow(varp))
		{
			return varp;
		}

		/*
		 * A dedicated item-object widget may preserve Seeking identity while a
		 * temporary/older varp exposes the corresponding ordinary arrow.
		 */
		if (isSeekingArrow(widget)
			&& (varp <= 0 || isUnderlyingArrow(varp, widget)))
		{
			return widget;
		}
		if (varp > 0)
		{
			return varp;
		}
		if (widget > 0)
		{
			return widget;
		}
		return cached;
	}

	/**
	 * Select the quiver stack from already-normalized, compatible client state.
	 *
	 * <p>A populated dedicated item container is primary while the quiver is
	 * unequipped, and the TEMP_AMMO varps are primary while it is worn. During
	 * login RuneLite can allocate the container before filling it, so an empty
	 * container must fall back to the persistent varps instead of erasing them.</p>
	 */
	static Snapshot resolveSnapshot(
		final boolean wearingQuiver,
		final int storedItemId,
		final int storedQuantity,
		final int widgetItemId,
		final int widgetQuantity,
		final int varpItemId,
		final int varpQuantity,
		final int cachedItemId)
	{
		final Snapshot stored = Snapshot.of(storedItemId, storedQuantity);
		final int liveItemId = resolveExactIdentity(
			widgetItemId,
			varpItemId,
			cachedItemId
		);
		final int liveQuantity = varpQuantity > 0
			? varpQuantity
			: widgetQuantity > 0
				? widgetQuantity
				: 0;
		final Snapshot live = Snapshot.of(liveItemId, liveQuantity);

		if (!wearingQuiver)
		{
			/*
			 * Inventory 879 can exist as an allocated-but-empty client container
			 * before its first server update. It must not erase the persistent TEMP_AMMO
			 * varps during login; that exact mistake made the analyzer fall back to a
			 * loose stack of regular Dragon arrows. A populated container remains the
			 * primary unequipped source, with an exact Seeking live ID allowed to restore
			 * identity if the container exposes only its corresponding ordinary arrow.
			 */
			final Snapshot current = stored.isPresent()
				? preserveSeekingIdentity(stored, live)
				: live;

			/*
			 * The server can replace a banked Seeking arrow with its ordinary
			 * compatibility ID after first publishing the exact ID. Keep that exact
			 * identity only when it describes the same underlying arrow. An empty
			 * current snapshot still clears the cache, and the worn path below stays
			 * fully authoritative when the player changes ammunition.
			 */
			return preserveSeekingIdentity(
				current,
				Snapshot.of(cachedItemId, current.quantity)
			);
		}

		if (live.isPresent())
		{
			return live;
		}
		if (stored.isPresent())
		{
			return stored;
		}
		return Snapshot.empty();
	}

	/**
	 * Recover a banked quiver's hidden Seeking stack when the client publishes no
	 * ammo container, varp or widget at all. This fallback is deliberately gated
	 * by a real usable quiver found in the live bank and can only use an exact
	 * Seeking item identity already retained by a placeholder/gear tag. Any live
	 * snapshot remains authoritative.
	 */
	static Snapshot restoreHiddenBankedSeekingAmmo(
		final boolean bankedUsableQuiverFound,
		final Snapshot liveSnapshot,
		final Iterable<Integer> exactIdentityHints)
	{
		final Snapshot current = liveSnapshot == null
			? Snapshot.empty()
			: liveSnapshot;
		if (current.isPresent() || !bankedUsableQuiverFound)
		{
			return current;
		}

		int bestItemId = -1;
		int bestRank = Integer.MAX_VALUE;
		if (exactIdentityHints != null)
		{
			for (final Integer rawItemId : exactIdentityHints)
			{
				if (rawItemId == null)
				{
					continue;
				}
				final int itemId = normalizeSeekingArrowItemId(rawItemId);
				if (!isSeekingArrow(itemId))
				{
					continue;
				}
				final int rank = infernoPreference(itemId);
				if (rank < bestRank)
				{
					bestItemId = itemId;
					bestRank = rank;
				}
			}
		}

		/* Quantity is unknown while banked; one is sufficient for ownership/ranking. */
		return bestItemId > 0
			? Snapshot.of(bestItemId, 1)
			: Snapshot.empty();
	}

	private static Snapshot preserveSeekingIdentity(
		final Snapshot primary,
		final Snapshot secondary)
	{
		if (!primary.isPresent() || !secondary.isPresent())
		{
			return primary;
		}
		if (!isSeekingArrow(primary.itemId)
			&& isSeekingArrow(secondary.itemId)
			&& isUnderlyingArrow(primary.itemId, secondary.itemId))
		{
			return Snapshot.of(secondary.itemId, primary.quantity);
		}
		return primary;
	}

	/** Lower values are preferred for the reviewed Inferno arrow policy. */
	static int infernoPreference(final int rawItemId)
	{
		final int itemId = normalizeSeekingArrowItemId(rawItemId);
		switch (itemId)
		{
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

	static int preferBetterInfernoArrow(
		final int recommendedItemId,
		final int liveQuiverItemId)
	{
		final int recommendedRank = infernoPreference(recommendedItemId);
		final int liveRank = infernoPreference(liveQuiverItemId);
		if (liveRank < recommendedRank)
		{
			return normalizeSeekingArrowItemId(liveQuiverItemId);
		}
		return recommendedItemId;
	}

	static final class Snapshot
	{
		private static final Snapshot EMPTY = new Snapshot(-1, 0);

		private final int itemId;
		private final int quantity;

		private Snapshot(final int itemId, final int quantity)
		{
			this.itemId = itemId;
			this.quantity = quantity;
		}

		static Snapshot empty()
		{
			return EMPTY;
		}

		static Snapshot of(final int rawItemId, final int quantity)
		{
			if (rawItemId <= 0 || quantity <= 0)
			{
				return EMPTY;
			}
			return new Snapshot(
				normalizeSeekingArrowItemId(rawItemId),
				quantity
			);
		}

		int getItemId()
		{
			return itemId;
		}

		int getQuantity()
		{
			return quantity;
		}

		boolean isPresent()
		{
			return itemId > 0 && quantity > 0;
		}
	}
}
