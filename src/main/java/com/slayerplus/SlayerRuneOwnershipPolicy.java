package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/** Resolves authored spell requirements to rune types the account actually owns. */
final class SlayerRuneOwnershipPolicy
{
	static final class ResolvedRune
	{
		private final int itemId;
		private final String name;
		private int minimumQuantity;

		private ResolvedRune(
			final int itemId,
			final String name,
			final int minimumQuantity)
		{
			this.itemId = itemId;
			this.name = name;
			this.minimumQuantity = Math.max(1, minimumQuantity);
		}

		int getItemId() { return itemId; }
		String getName() { return name; }
		int getMinimumQuantity() { return minimumQuantity; }
	}

	static final class Resolution
	{
		private final List<ResolvedRune> runes;
		private final List<String> unownedRequirements;

		private Resolution(
			final List<ResolvedRune> runes,
			final List<String> unownedRequirements)
		{
			this.runes = Collections.unmodifiableList(runes);
			this.unownedRequirements = Collections.unmodifiableList(
				unownedRequirements
			);
		}

		List<ResolvedRune> getRunes() { return runes; }
		List<String> getUnownedRequirements() { return unownedRequirements; }
	}

	private SlayerRuneOwnershipPolicy() { }

	static Resolution resolve(
		final List<SlayerMethodRules.PouchRuneRequirement> requirements,
		final Map<Integer, Integer> ownedQuantities)
	{
		if (requirements == null || requirements.isEmpty())
		{
			return new Resolution(
				Collections.emptyList(), Collections.emptyList()
			);
		}

		final Map<Integer, ResolvedRune> selected = new LinkedHashMap<>();
		final List<String> unowned = new ArrayList<>();
		for (final SlayerMethodRules.PouchRuneRequirement requirement : requirements)
		{
			int selectedId = existingSatisfyingRune(
				selected.keySet(), requirement.getItemId()
			);
			if (selectedId <= 0)
			{
				selectedId = bestOwnedRune(
					requirement.getItemId(), requirements, ownedQuantities
				);
			}

			if (selectedId <= 0)
			{
				unowned.add(requirement.getName());
				continue;
			}

			final ResolvedRune existing = selected.get(selectedId);
			if (existing == null)
			{
				selected.put(selectedId, new ResolvedRune(
					selectedId,
					runeName(selectedId),
					requirement.getMinimumQuantity()
				));
			}
			else
			{
				/* Cosmic and Soul are spent by separate Thrall/Death Charge
				 * casts. When one owned Aether stack satisfies both authored
				 * requirements, it must contain the sum, not merely the larger
				 * requirement. Elemental combination runes retain max semantics
				 * because their two elements are consumed by the same spell. */
				if (existing.itemId == ItemID.AETHERRUNE)
				{
					existing.minimumQuantity += requirement.getMinimumQuantity();
				}
				else
				{
					existing.minimumQuantity = Math.max(
						existing.minimumQuantity,
						requirement.getMinimumQuantity()
					);
				}
			}
		}

		return new Resolution(
			new ArrayList<>(selected.values()), unowned
		);
	}

	private static int existingSatisfyingRune(
		final Set<Integer> selectedIds,
		final int requiredId)
	{
		for (final int itemId : selectedIds)
		{
			if (satisfies(itemId, requiredId))
			{
				return itemId;
			}
		}
		return -1;
	}

	private static int bestOwnedRune(
		final int requiredId,
		final List<SlayerMethodRules.PouchRuneRequirement> requirements,
		final Map<Integer, Integer> ownedQuantities)
	{
		int bestId = -1;
		int bestCoverage = -1;
		int bestQuantity = -1;
		for (final int candidateId : candidates(requiredId))
		{
			final int quantity = ownedQuantities == null
				? 0 : ownedQuantities.getOrDefault(candidateId, 0);
			if (quantity <= 0)
			{
				continue;
			}

			int coverage = 0;
			for (final SlayerMethodRules.PouchRuneRequirement requirement
				: requirements)
			{
				if (satisfies(candidateId, requirement.getItemId()))
				{
					coverage++;
				}
			}
			if (coverage > bestCoverage
				|| (coverage == bestCoverage && quantity > bestQuantity)
				|| (coverage == bestCoverage && quantity == bestQuantity
					&& candidateId == requiredId))
			{
				bestId = candidateId;
				bestCoverage = coverage;
				bestQuantity = quantity;
			}
		}
		return bestId;
	}

	private static List<Integer> candidates(final int requiredId)
	{
		final Set<Integer> values = new LinkedHashSet<>();
		values.add(requiredId);
		if (requiredId == ItemID.AIRRUNE)
		{
			values.add(ItemID.MISTRUNE);
			values.add(ItemID.DUSTRUNE);
			values.add(ItemID.SMOKERUNE);
		}
		else if (requiredId == ItemID.WATERRUNE)
		{
			values.add(ItemID.MISTRUNE);
			values.add(ItemID.MUDRUNE);
			values.add(ItemID.STEAMRUNE);
		}
		else if (requiredId == ItemID.EARTHRUNE)
		{
			values.add(ItemID.DUSTRUNE);
			values.add(ItemID.MUDRUNE);
			values.add(ItemID.LAVARUNE);
		}
		else if (requiredId == ItemID.FIRERUNE)
		{
			values.add(ItemID.SMOKERUNE);
			values.add(ItemID.STEAMRUNE);
			values.add(ItemID.LAVARUNE);
		}
		else if (requiredId == ItemID.COSMICRUNE
			|| requiredId == ItemID.SOULRUNE)
		{
			values.add(ItemID.AETHERRUNE);
		}
		return new ArrayList<>(values);
	}

	static boolean satisfies(
		final int candidateId,
		final int requiredId)
	{
		return candidates(requiredId).contains(candidateId);
	}

	private static String runeName(final int itemId)
	{
		if (itemId == ItemID.AIRRUNE) return "Air";
		if (itemId == ItemID.WATERRUNE) return "Water";
		if (itemId == ItemID.EARTHRUNE) return "Earth";
		if (itemId == ItemID.FIRERUNE) return "Fire";
		if (itemId == ItemID.MINDRUNE) return "Mind";
		if (itemId == ItemID.CHAOSRUNE) return "Chaos";
		if (itemId == ItemID.DEATHRUNE) return "Death";
		if (itemId == ItemID.BLOODRUNE) return "Blood";
		if (itemId == ItemID.COSMICRUNE) return "Cosmic";
		if (itemId == ItemID.SOULRUNE) return "Soul";
		if (itemId == ItemID.WRATHRUNE) return "Wrath";
		if (itemId == ItemID.LAWRUNE) return "Law";
		if (itemId == ItemID.NATURERUNE) return "Nature";
		if (itemId == ItemID.MISTRUNE) return "Mist";
		if (itemId == ItemID.DUSTRUNE) return "Dust";
		if (itemId == ItemID.SMOKERUNE) return "Smoke";
		if (itemId == ItemID.MUDRUNE) return "Mud";
		if (itemId == ItemID.STEAMRUNE) return "Steam";
		if (itemId == ItemID.LAVARUNE) return "Lava";
		if (itemId == ItemID.AETHERRUNE) return "Aether";
		return "Rune";
	}
}
