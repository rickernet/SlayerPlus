package com.slayerplus;

import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class SlayerQuiverRegressionTest
{
	@Test
	public void everyUsableDizanaQuiverVariantIsRecognized()
	{
		assertUsableDizanaVariants(
			ItemID.DIZANAS_QUIVER_UNCHARGED,
			ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER,
			ItemID.DIZANAS_QUIVER_CHARGED,
			ItemID.DIZANAS_QUIVER_CHARGED_TROUVER,
			ItemID.DIZANAS_QUIVER_INFINITE,
			ItemID.DIZANAS_QUIVER_INFINITE_TROUVER,
			ItemID.SKILLCAPE_MAX_DIZANAS,
			ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER
		);
	}

	@Test
	public void unusableDizanaObjectsAreNotTreatedAsWornQuivers()
	{
		assertUnusableDizanaVariants(
			-1,
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
		);
	}

	@Test
	public void bankScanFindsEveryVariantAndRejectsBrokenObjects()
	{
		assertEquals(
			ItemID.DIZANAS_QUIVER_UNCHARGED,
			SlayerQuiverAmmo.preferUsableDizanaVariant(
				ItemID.DIZANAS_QUIVER_BROKEN,
				ItemID.DIZANAS_QUIVER_UNCHARGED
			)
		);
		assertEquals(
			ItemID.DIZANAS_QUIVER_CHARGED_TROUVER,
			SlayerQuiverAmmo.preferUsableDizanaVariant(
				ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER,
				ItemID.DIZANAS_QUIVER_CHARGED_TROUVER
			)
		);
		assertEquals(
			ItemID.DIZANAS_QUIVER_INFINITE_TROUVER,
			SlayerQuiverAmmo.preferUsableDizanaVariant(
				ItemID.DIZANAS_QUIVER_CHARGED,
				ItemID.DIZANAS_QUIVER_INFINITE_TROUVER
			)
		);
		assertEquals(
			ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER,
			SlayerQuiverAmmo.preferUsableDizanaVariant(
				ItemID.DIZANAS_QUIVER_INFINITE,
				ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER
			)
		);
		assertEquals(
			-1,
			SlayerQuiverAmmo.preferUsableDizanaVariant(
				-1,
				ItemID.DIZANAS_QUIVER_TROUVER_MANGLED
			)
		);
	}

	@Test
	public void seekingArrowsRemainSeparateFromOrdinaryVariationFamilies()
	{
		assertSeparateVariationFamily(
			ItemID.DRAGON_ARROW,
			ItemID.SEEKING_DRAGON_ARROW
		);
		assertSeparateVariationFamily(
			ItemID.AMETHYST_ARROW,
			ItemID.SEEKING_AMETHYST_ARROW
		);
	}

	@Test
	public void everyLiveSeekingDisplayIdNormalizesToItsBaseItem()
	{
		assertSeekingFamily(ItemID.SEEKING_BRONZE_ARROW,
			ItemID.SEEKING_BRONZE_ARROW, ItemID.SEEKING_BRONZE_ARROW_2,
			ItemID.SEEKING_BRONZE_ARROW_3, ItemID.SEEKING_BRONZE_ARROW_4,
			ItemID.SEEKING_BRONZE_ARROW_5);
		assertSeekingFamily(ItemID.SEEKING_IRON_ARROW,
			ItemID.SEEKING_IRON_ARROW, ItemID.SEEKING_IRON_ARROW_2,
			ItemID.SEEKING_IRON_ARROW_3, ItemID.SEEKING_IRON_ARROW_4,
			ItemID.SEEKING_IRON_ARROW_5);
		assertSeekingFamily(ItemID.SEEKING_STEEL_ARROW,
			ItemID.SEEKING_STEEL_ARROW, ItemID.SEEKING_STEEL_ARROW_2,
			ItemID.SEEKING_STEEL_ARROW_3, ItemID.SEEKING_STEEL_ARROW_4,
			ItemID.SEEKING_STEEL_ARROW_5);
		assertSeekingFamily(ItemID.SEEKING_MITHRIL_ARROW,
			ItemID.SEEKING_MITHRIL_ARROW, ItemID.SEEKING_MITHRIL_ARROW_2,
			ItemID.SEEKING_MITHRIL_ARROW_3, ItemID.SEEKING_MITHRIL_ARROW_4,
			ItemID.SEEKING_MITHRIL_ARROW_5);
		assertSeekingFamily(ItemID.SEEKING_ADAMANT_ARROW,
			ItemID.SEEKING_ADAMANT_ARROW, ItemID.SEEKING_ADAMANT_ARROW_2,
			ItemID.SEEKING_ADAMANT_ARROW_3, ItemID.SEEKING_ADAMANT_ARROW_4,
			ItemID.SEEKING_ADAMANT_ARROW_5);
		assertSeekingFamily(ItemID.SEEKING_RUNE_ARROW,
			ItemID.SEEKING_RUNE_ARROW, ItemID.SEEKING_RUNE_ARROW_2,
			ItemID.SEEKING_RUNE_ARROW_3, ItemID.SEEKING_RUNE_ARROW_4,
			ItemID.SEEKING_RUNE_ARROW_5);
		assertSeekingFamily(ItemID.SEEKING_AMETHYST_ARROW,
			ItemID.SEEKING_AMETHYST_ARROW, ItemID.SEEKING_AMETHYST_ARROW_2,
			ItemID.SEEKING_AMETHYST_ARROW_3, ItemID.SEEKING_AMETHYST_ARROW_4,
			ItemID.SEEKING_AMETHYST_ARROW_5);
		assertSeekingFamily(ItemID.SEEKING_DRAGON_ARROW,
			ItemID.SEEKING_DRAGON_ARROW, ItemID.SEEKING_DRAGON_ARROW2,
			ItemID.SEEKING_DRAGON_ARROW3, ItemID.SEEKING_DRAGON_ARROW4,
			ItemID.SEEKING_DRAGON_ARROW5);
		assertSeekingFamily(ItemID.SEEKING_SLAYER_BROAD_ARROWS,
			ItemID.SEEKING_SLAYER_BROAD_ARROWS,
			ItemID.SEEKING_SLAYER_BROAD_ARROWS_2,
			ItemID.SEEKING_SLAYER_BROAD_ARROWS_3,
			ItemID.SEEKING_SLAYER_BROAD_ARROWS_4,
			ItemID.SEEKING_SLAYER_BROAD_ARROWS_5);
	}

	@Test
	public void exactLiveIdentityNeverLetsStaleCacheOverrideCurrentState()
	{
		assertEquals(
			ItemID.SEEKING_DRAGON_ARROW,
			SlayerQuiverAmmo.resolveExactIdentity(
				ItemID.DRAGON_ARROW,
				ItemID.SEEKING_DRAGON_ARROW5,
				ItemID.SEEKING_AMETHYST_ARROW
			)
		);
		assertEquals(
			ItemID.SEEKING_DRAGON_ARROW,
			SlayerQuiverAmmo.resolveExactIdentity(
				ItemID.SEEKING_DRAGON_ARROW3,
				ItemID.DRAGON_ARROW,
				ItemID.SEEKING_AMETHYST_ARROW
			)
		);
		assertEquals(
			ItemID.DRAGON_ARROW,
			SlayerQuiverAmmo.resolveExactIdentity(
				ItemID.SEEKING_AMETHYST_ARROW,
				ItemID.DRAGON_ARROW,
				ItemID.SEEKING_DRAGON_ARROW
			)
		);
		assertEquals(
			ItemID.RUNE_ARROW,
			SlayerQuiverAmmo.resolveExactIdentity(
				-1,
				ItemID.RUNE_ARROW,
				ItemID.SEEKING_DRAGON_ARROW
			)
		);
	}

	@Test
	public void unequippedStartupUsesPersistentQuiverContainerImmediately()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.resolveSnapshot(
				false,
				ItemID.SEEKING_DRAGON_ARROW4,
				176,
				-1,
				0,
				ItemID.SEEKING_RUNE_ARROW,
				500,
				-1
			);

		assertEquals(ItemID.SEEKING_DRAGON_ARROW, snapshot.getItemId());
		assertEquals(176, snapshot.getQuantity());
	}

	@Test
	public void equippedQuiverUsesLiveTemporaryState()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.resolveSnapshot(
				true,
				ItemID.SEEKING_RUNE_ARROW,
				500,
				ItemID.SEEKING_DRAGON_ARROW3,
				176,
				ItemID.DRAGON_ARROW,
				176,
				-1
			);

		assertEquals(ItemID.SEEKING_DRAGON_ARROW, snapshot.getItemId());
		assertEquals(176, snapshot.getQuantity());
	}

	@Test
	public void noLiveOrStoredStateClearsStaleCachedIdentity()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.resolveSnapshot(
				false,
				-1,
				0,
				-1,
				0,
				-1,
				0,
				ItemID.SEEKING_DRAGON_ARROW
			);

		assertFalse(snapshot.isPresent());
		assertEquals(-1, snapshot.getItemId());
		assertEquals(0, snapshot.getQuantity());
	}

	@Test
	public void hiddenBankedQuiverUsesExactSeekingTagHint()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.restoreHiddenBankedSeekingAmmo(
				true,
				SlayerQuiverAmmo.Snapshot.empty(),
				Arrays.asList(
					ItemID.SEEKING_AMETHYST_ARROW_4,
					ItemID.SEEKING_DRAGON_ARROW3
				)
			);

		assertEquals(ItemID.SEEKING_DRAGON_ARROW, snapshot.getItemId());
		assertEquals(1, snapshot.getQuantity());
	}

	@Test
	public void hiddenSeekingHintCannotCreateAmmoWithoutBankedQuiver()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.restoreHiddenBankedSeekingAmmo(
				false,
				SlayerQuiverAmmo.Snapshot.empty(),
				Collections.singleton(ItemID.SEEKING_DRAGON_ARROW)
			);

		assertFalse(snapshot.isPresent());
	}

	@Test
	public void liveQuiverAmmoOverridesHiddenIdentityHint()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.restoreHiddenBankedSeekingAmmo(
				true,
				SlayerQuiverAmmo.Snapshot.of(ItemID.RUNE_ARROW, 176),
				Collections.singleton(ItemID.SEEKING_DRAGON_ARROW)
			);

		assertEquals(ItemID.RUNE_ARROW, snapshot.getItemId());
		assertEquals(176, snapshot.getQuantity());
	}

	@Test
	public void unequippedOrdinaryAliasKeepsMatchingObservedSeekingIdentity()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.resolveSnapshot(
				false,
				ItemID.DRAGON_ARROW,
				176,
				-1,
				0,
				ItemID.DRAGON_ARROW,
				176,
				ItemID.SEEKING_DRAGON_ARROW
			);

		assertEquals(ItemID.SEEKING_DRAGON_ARROW, snapshot.getItemId());
		assertEquals(176, snapshot.getQuantity());
	}

	@Test
	public void wornOrdinaryAmmoReplacesStaleSeekingIdentity()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.resolveSnapshot(
				true,
				ItemID.SEEKING_DRAGON_ARROW,
				176,
				-1,
				0,
				ItemID.DRAGON_ARROW,
				176,
				ItemID.SEEKING_DRAGON_ARROW
			);

		assertEquals(ItemID.DRAGON_ARROW, snapshot.getItemId());
		assertEquals(176, snapshot.getQuantity());
	}

	@Test
	public void allocatedEmptyContainerDoesNotHideStartupSeekingVarp()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.resolveSnapshot(
				false,
				-1,
				0,
				-1,
				0,
				ItemID.SEEKING_DRAGON_ARROW5,
				176,
				-1
			);

		assertEquals(ItemID.SEEKING_DRAGON_ARROW, snapshot.getItemId());
		assertEquals(176, snapshot.getQuantity());
		assertEquals(
			ItemID.SEEKING_DRAGON_ARROW,
			SlayerQuiverAmmo.preferBetterInfernoArrow(
				ItemID.DRAGON_ARROW,
				snapshot.getItemId()
			)
		);
	}

	@Test
	public void ordinaryStoredIdCannotEraseMatchingSeekingVarpIdentity()
	{
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.resolveSnapshot(
				false,
				ItemID.DRAGON_ARROW,
				176,
				-1,
				0,
				ItemID.SEEKING_DRAGON_ARROW3,
				176,
				-1
			);

		assertEquals(ItemID.SEEKING_DRAGON_ARROW, snapshot.getItemId());
		assertEquals(176, snapshot.getQuantity());
	}

	@Test
	public void matchingBankPlaceholderRestoresUnequippedQuiverSeekingIdentity()
	{
		final int restoredItemId =
			SlayerQuiverAmmo.restoreSeekingIdentityFromPlaceholders(
				ItemID.DRAGON_ARROW,
				Arrays.asList(
					ItemID.SEEKING_AMETHYST_ARROW_4,
					ItemID.SEEKING_DRAGON_ARROW3
				)
			);

		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.resolveSnapshot(
				false,
				restoredItemId,
				176,
				-1,
				0,
				-1,
				0,
				-1
			);

		assertEquals(ItemID.SEEKING_DRAGON_ARROW, snapshot.getItemId());
		assertEquals(176, snapshot.getQuantity());
		assertEquals(
			ItemID.SEEKING_DRAGON_ARROW,
			SlayerQuiverAmmo.preferBetterInfernoArrow(
				ItemID.DRAGON_ARROW,
				snapshot.getItemId()
			)
		);
	}

	@Test
	public void placeholderEvidenceCannotInventOrChangeUnmatchedQuiverAmmo()
	{
		assertEquals(
			-1,
			SlayerQuiverAmmo.restoreSeekingIdentityFromPlaceholders(
				-1,
				Collections.singleton(ItemID.SEEKING_DRAGON_ARROW)
			)
		);
		assertEquals(
			ItemID.RUNE_ARROW,
			SlayerQuiverAmmo.restoreSeekingIdentityFromPlaceholders(
				ItemID.RUNE_ARROW,
				Collections.singleton(ItemID.SEEKING_DRAGON_ARROW)
			)
		);
	}

	@Test
	public void liveBetterSeekingArrowReplacesStaleInfernoRecommendation()
	{
		assertEquals(
			ItemID.SEEKING_AMETHYST_ARROW,
			SlayerQuiverAmmo.preferBetterInfernoArrow(
				ItemID.DRAGON_ARROW,
				ItemID.SEEKING_AMETHYST_ARROW_3
			)
		);
		assertEquals(
			ItemID.SEEKING_DRAGON_ARROW,
			SlayerQuiverAmmo.preferBetterInfernoArrow(
				ItemID.DRAGON_ARROW,
				ItemID.SEEKING_DRAGON_ARROW4
			)
		);
	}

	@Test
	public void weakerArrowDoesNotReplaceBetterInfernoRecommendation()
	{
		assertEquals(
			ItemID.DRAGON_ARROW,
			SlayerQuiverAmmo.preferBetterInfernoArrow(
				ItemID.DRAGON_ARROW,
				ItemID.SEEKING_RUNE_ARROW
			)
		);
	}

	private static void assertSeparateVariationFamily(
		final int ordinaryItemId,
		final int seekingItemId)
	{
		final int ordinaryFamily = ItemVariationMapping.map(ordinaryItemId);
		final int seekingFamily = ItemVariationMapping.map(seekingItemId);
		assertFalse(
			"Seeking and ordinary arrows must not share a RuneLite variation family",
			ordinaryFamily == seekingFamily
		);
	}

	private static void assertSeekingFamily(
		final int expectedBaseItemId,
		final int... displayItemIds)
	{
		for (final int displayItemId : displayItemIds)
		{
			assertEquals(
				expectedBaseItemId,
				SlayerQuiverAmmo.normalizeSeekingArrowItemId(displayItemId)
			);
			assertTrue(SlayerQuiverAmmo.isSeekingArrow(displayItemId));
			assertFalse(
				SlayerQuiverAmmo.seekingArrowMatchName(displayItemId).isEmpty()
			);
		}
	}

	private static void assertUsableDizanaVariants(final int... itemIds)
	{
		for (final int itemId : itemIds)
		{
			final boolean wearingUsableVariant =
				SlayerQuiverAmmo.isUsableDizanaVariant(itemId);
			assertTrue(
				"Expected usable Dizana variant: " + itemId,
				wearingUsableVariant
			);

			/* Every wearable form must route resolution to the live worn slot. */
			final SlayerQuiverAmmo.Snapshot snapshot =
				SlayerQuiverAmmo.resolveSnapshot(
					wearingUsableVariant,
					ItemID.SEEKING_RUNE_ARROW,
					500,
					-1,
					0,
					ItemID.SEEKING_DRAGON_ARROW5,
					176,
					-1
				);
			assertEquals(ItemID.SEEKING_DRAGON_ARROW, snapshot.getItemId());
			assertEquals(176, snapshot.getQuantity());
		}
	}

	private static void assertUnusableDizanaVariants(final int... itemIds)
	{
		for (final int itemId : itemIds)
		{
			assertFalse(
				"Expected unusable Dizana object: " + itemId,
				SlayerQuiverAmmo.isUsableDizanaVariant(itemId)
			);
		}
	}
}
