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

public final class SlayerBankTagLayoutService
{
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
    /*
     * Dizana's real second ammunition slot is conceptually above the normal ammo
     * slot. Mirror that shape in the mannequin rather than mixing stored quiver
     * arrows/bolts into the 4 x 7 combat inventory.
     */
    private static final int EXTRA_QUIVER_AMMO_POSITION = position(2, 2);

    /*
     * Equipment positions on the left side of the eight-column bank grid.
     * Entries 0..10 preserve RuneLite's normal equipment order:
     * head, cape, amulet, ammo, weapon, body, shield, legs, gloves, boots, ring.
     * Entry 11 is SlayerPlus' explicit Dizana second-ammo state and is rendered
     * one cell directly above the ordinary ammo slot.
     */
    private static final int[] EQUIPMENT_POSITIONS =
    {
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

    public SlayerBankTagLayoutService(
        final Client client,
        final TagManager tagManager,
        final LayoutManager layoutManager,
        final BankTagsService bankTagsService,
        final ConfigManager configManager)
    {
        this.client = client;
        this.tagManager = tagManager;
        this.layoutManager = layoutManager;
        this.bankTagsService = bankTagsService;
        this.configManager = configManager;
    }

    public Result createOrUpdate(final SlayerLoadoutPlan plan)
    {
        return createOrUpdate(plan, -1, Collections.emptyList());
    }

    /**
     * Creates the task layout and optionally reserves the first preparation
     * slot for the exact teleport item selected by Shortest Path.
     */
    public Result createOrUpdate(
        final SlayerLoadoutPlan plan,
        final int travelItemId)
    {
        return createOrUpdate(
            plan,
            travelItemId,
            Collections.emptyList()
        );
    }

    /**
     * Adds non-equipment preparation items before the optional row. This is
     * used for requirements such as a rune pouch and the exact rune stacks
     * needed to load it. Existing equipment/inventory items are never moved or
     * duplicated.
     */
    public Result createOrUpdate(
        final SlayerLoadoutPlan plan,
        final int travelItemId,
        final List<Integer> preparationItemIds)
    {
        return createOrUpdate(
            plan,
            travelItemId,
            preparationItemIds,
            travelItemId > 0
        );
    }

    /**
     * Creates the deterministic layout with an explicit travel-authority state.
     *
     * When shortestPathOwnsTravel is true, inventory source slot zero is never
     * allowed to supply a different analyzer/default teleport. A positive
     * travelItemId is placed in the bottom utility strip; a non-positive ID
     * means Shortest Path resolved to a non-item transport or is still resolving,
     * so the slot is left to the researched supplies without inventing a teleport.
     */
    public Result createOrUpdate(
        final SlayerLoadoutPlan plan,
        final int travelItemId,
        final List<Integer> preparationItemIds,
        final boolean shortestPathOwnsTravel)
    {
        return createOrUpdate(
            plan,
            null,
            travelItemId,
            preparationItemIds,
            shortestPathOwnsTravel
        );
    }

    /**
     * Creates the deterministic layout from the same immutable travel selection
     * consumed by the panel and teleport highlighter. Normal routes place the
     * selected physical item immediately before the rune-pouch anchor in the
     * bottom utility strip. Every other physical alternative Shortest Path
     * exposed for the same route edge is suppressed from the rest of the 4 x 7.
     */
    public Result createOrUpdate(
        final SlayerLoadoutPlan plan,
        final SlayerTravelSelection travelSelection,
        final int travelItemId,
        final List<Integer> preparationItemIds,
        final boolean shortestPathOwnsTravel)
    {
        return createOrUpdate(
            plan,
            travelSelection,
            travelItemId,
            preparationItemIds,
            shortestPathOwnsTravel,
            0,
            true
        );
    }

    /**
     * Extended deterministic travel placement. Normal Slayer routes keep the
     * authoritative travel item in the bottom utility strip. A negative slot
     * index is the explicit
     * staging-only mode: the selected travel item is rendered in the compact
     * strip above the mannequin instead of consuming one of the 28 combat cells.
     * TzKal-Zuk uses that mode while travelling to the Mor Ul Rek preparation bank.
     */
    public Result createOrUpdate(
        final SlayerLoadoutPlan plan,
        final SlayerTravelSelection travelSelection,
        final int travelItemId,
        final List<Integer> preparationItemIds,
        final boolean shortestPathOwnsTravel,
        final int authoritativeTravelSlotIndex,
        final boolean analyzerSourceZeroIsTravel)
    {
        return createOrUpdate(
            plan,
            travelSelection,
            travelItemId,
            preparationItemIds,
            shortestPathOwnsTravel,
            authoritativeTravelSlotIndex,
            analyzerSourceZeroIsTravel,
            -1
        );
    }

    /**
     * Full layout including Dizana's dedicated second ammunition slot. The
     * extra ammo is a gear-state reference, not an inventory cell, so it never
     * consumes one of the 28 authored combat slots.
     */
    public Result createOrUpdate(
        final SlayerLoadoutPlan plan,
        final SlayerTravelSelection travelSelection,
        final int travelItemId,
        final List<Integer> preparationItemIds,
        final boolean shortestPathOwnsTravel,
        final int authoritativeTravelSlotIndex,
        final boolean analyzerSourceZeroIsTravel,
        final int extraQuiverAmmoItemId)
    {
        if (plan == null || !plan.hasConcreteItems())
        {
            return Result.failure(
                "No concrete recommended items are available yet. Open your bank once, then refresh the task."
            );
        }

        final Layout layout = new Layout(TAG_NAME);
        final Set<Integer> taggedItemIds = new LinkedHashSet<>();
        int layoutSlots = 0;

        /*
         * Optional gear remains in the compact strip above the mannequin.
         * Rune-pouch contents are reference information rather than inventory
         * slots, so preparationItemIds are rendered later beneath the 4 x 7
         * inventory grid. Any preparation item that really occupies inventory
         * (pouch, Book of the dead, overflow runes, etc.) is already present in
         * the plan and is therefore omitted from the reference strip.
         */
        final boolean externalStagingTravel = shortestPathOwnsTravel
            && travelItemId > 0
            && authoritativeTravelSlotIndex < 0;
        int optionalIndex = externalStagingTravel ? 1 : 0;
        final List<SlayerLoadoutItem> equipment = plan.getEquipmentItems();

        /*
         * A reviewed final-bank staging teleport is preparation gear, not part of
         * the combat inventory. Keep it visible at the top of the Bank Tag while
         * preserving every one of the 28 authored combat/supply cells below.
         */
        if (externalStagingTravel
            && placeItemId(
                layout,
                taggedItemIds,
                travelItemId,
                STAGING_TRAVEL_STRIP_POSITION
            ))
        {
            layoutSlots++;
        }

        for (final SlayerLoadoutItem item : plan.getOptionalItems())
        {
            if (optionalIndex >= OPTIONAL_LIMIT)
            {
                break;
            }

            /* Lightbearer belongs with the ring equipment, not utility. */
            if (isLightbearer(item))
            {
                continue;
            }

            /*
             * GLOBAL EQUIPMENT AUTHORITY:
             * An item selected for the mannequin must always occupy its actual
             * equipment slot. Task bracelets are also listed as optional task
             * modifiers, but placing one here first caused the later glove-slot
             * placement to be deduplicated and left an empty mannequin cell.
             */
            if (duplicatesConcreteEquipment(item, equipment))
            {
                continue;
            }

            if (place(layout, taggedItemIds, item, optionalIndex))
            {
                layoutSlots++;
                optionalIndex++;
            }
        }

        /*
         * DIZANA VARIANT AUTHORITY
         * ------------------------
         * Do not trust a generic/canonical cape alias when writing the Bank Tag.
         * Resolve the exact usable Dizana variant the player owns right now, with
         * source priority worn -> inventory -> bank. This independently protects
         * the gear layout even if ItemVariationMapping or analyzer aliases change.
         */
        final int resolvedDizanaVariantId =
            resolveOwnedDizanaVariantId(plan);

        /*
         * DIZANA EXTRA-AMMO RULE
         * ----------------------
         * The analyzer now models Dizana's second ammunition compartment as a
         * real terminal equipment entry (index 11). That keeps the ordinary ammo
         * slot independent instead of overloading it with the quiver stack.
         *
         * Start with the authored recommendation. The Inferno safeguard below
         * may replace only a stale/weaker value with better live quiver ammo.
         */
        int recommendedExtraQuiverAmmoItemId =
            equipment.size() > 11 && concrete(equipment.get(11))
                ? equipment.get(11).getItemId()
                : -1;

        /*
         * The live quiver snapshot is passed separately because it can arrive
         * after the panel's previous loadout calculation. For Inferno layouts,
         * compare it with the authored recommendation by exact gameval ID so a
         * better Seeking stack cannot be replaced by a stale ordinary-arrow
         * selection while the Bank Tag is being written.
         */
        if (normalizeName(plan.getLayoutTitle()).contains("tzkal zuk"))
        {
            recommendedExtraQuiverAmmoItemId =
                SlayerQuiverAmmo.preferBetterInfernoArrow(
                    recommendedExtraQuiverAmmoItemId,
                    extraQuiverAmmoItemId
                );
        }

        final boolean hasUsableDizanaForLayout =
            resolvedDizanaVariantId > 0 || planContainsUsableDizanaCape(plan);

        /*
         * Equipment always uses fixed mannequin-shaped positions. Missing or
         * unresolved slots do not shift later equipment into the wrong spot.
         */
        for (int index = 0;
            index < equipment.size() && index < EQUIPMENT_POSITIONS.length;
            index++)
        {
            /* Cape is equipment index 1 in SlayerLoadoutPlan's fixed order. */
            if (index == 1 && resolvedDizanaVariantId > 0)
            {
                if (placeItemId(
                    layout,
                    taggedItemIds,
                    resolvedDizanaVariantId,
                    EQUIPMENT_POSITIONS[index]
                ))
                {
                    layoutSlots++;
                }
                continue;
            }

            /*
             * DIZANA RECOMMENDED-AMMO AUTHORITY
             * ----------------------------
             * Entry 11 is selected from the reviewed weapon/task ammo priorities.
             * Do not replace it with whatever happens to be loaded at write time.
             */
            if (index == 11)
            {
                if (hasUsableDizanaForLayout
                    && recommendedExtraQuiverAmmoItemId > 0
                    && placeNestedQuiverAmmoItemId(
                        layout,
                        taggedItemIds,
                        recommendedExtraQuiverAmmoItemId,
                        EQUIPMENT_POSITIONS[index]
                    ))
                {
                    layoutSlots++;
                }

                continue;
            }

            if (place(
                layout,
                taggedItemIds,
                equipment.get(index),
                EQUIPMENT_POSITIONS[index]
            ))
            {
                layoutSlots++;
            }
        }

        /*
         * If a reviewed strategy includes Lightbearer as a switch rather than
         * the equipped ring, keep it immediately below the ring slot. It is
         * never added globally; it appears only when the generated plan already
         * contains it.
         */
        final SlayerLoadoutItem lightbearer = findLightbearer(plan);
        if (place(
            layout,
            taggedItemIds,
            lightbearer,
            LIGHTBEARER_SWAP_POSITION
        ))
        {
            layoutSlots++;
        }

        /*
         * Render the full researched 4 x 7 inventory, including repeated
         * potion and food slots. The Layout model supports repeated item IDs;
         * only the underlying tag membership is deduplicated.
         */
        final List<SlayerLoadoutItem> inventoryItems = plan.getInventoryItems();

        /*
         * GLOBAL TRAVEL-SYNCHRONIZATION RULE
         * ----------------------------------
         * Once Shortest Path has resolved a physical travel item for this exact
         * route, the 4 x 7 grid may contain only that selected item. While the
         * route is unresolved or uses a non-item transition, retain the analyzer's
         * reviewed slot-zero teleport so the generated grid never silently loses
         * its preparation transport.
         */
        int travelSlotIndex = authoritativeTravelSlotIndex < 0
            ? -1
            : preferredTravelSlot(inventoryItems);
        final boolean reserveTravelSlot = shortestPathOwnsTravel
            && travelItemId > 0
            && travelSlotIndex >= 0;

        /*
         * Slot 28 is globally reserved for a concrete rune pouch. If a future
         * route ever asks Shortest Path to use that cell, keep the pouch contract
         * and move travel to the first inventory cell instead.
         */
        if (reserveTravelSlot
            && travelSlotIndex == RUNE_POUCH_INVENTORY_SLOT
            && containsConcreteRunePouch(inventoryItems))
        {
            travelSlotIndex = TRAVEL_SLOT_WITH_RUNE_POUCH;
        }
        if (reserveTravelSlot
            && placeInventoryItemId(
                layout,
                taggedItemIds,
                travelItemId,
                position(
                    2 + travelSlotIndex / 4,
                    4 + travelSlotIndex % 4
                )
            ))
        {
            layoutSlots++;
        }

        final List<InventoryCandidate> inventoryCandidates = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < inventoryItems.size(); sourceIndex++)
        {
            final SlayerLoadoutItem item = inventoryItems.get(sourceIndex);
            if (!concrete(item) || isLightbearer(item))
            {
                continue;
            }

            /*
             * GLOBAL ONE-TRAVEL-ITEM RULE:
             * - source slot zero is the analyzer-authored fallback and never
             *   competes with Shortest Path;
             * - the selected item itself is rendered only once in the authoritative
             *   reserved travel cell;
             * - every other owned physical alternative posted for the same exact
             *   Shortest Path edge is removed from the remaining 4 x 7 slots.
             */
            if (shouldSuppressInventoryTravelItem(
                shortestPathOwnsTravel,
                sourceIndex,
                travelItemId,
                item,
                travelSelection,
                analyzerSourceZeroIsTravel
            ))
            {
                continue;
            }

            inventoryCandidates.add(new InventoryCandidate(item, sourceIndex));
        }

        for (final InventoryPlacement placement : planInventoryCandidates(
            inventoryCandidates,
            reserveTravelSlot ? travelSlotIndex : -1,
            analyzerSourceZeroIsTravel
        ))
        {
            final int slotIndex = placement.getSlotIndex();
            final int row = 2 + slotIndex / 4;
            final int column = 4 + slotIndex % 4;
            if (placeInventory(
                layout,
                taggedItemIds,
                placement.getItem(),
                position(row, column)
            ))
            {
                layoutSlots++;
            }
        }

        /*
         * Show rune-pouch contents under the gear setup, horizontally aligned
         * with the bottom inventory row. This keeps the reference runes visible
         * without consuming 4 x 7 supply slots, while overflow rune types still
         * remain in the real inventory grid.
         */
        int preparationIndex = 0;
        final Set<Integer> preparationReferenceIds = new LinkedHashSet<>();
        if (preparationItemIds != null)
        {
            for (final int preparationItemId : preparationItemIds)
            {
                if (preparationIndex >= OPTIONAL_LIMIT)
                {
                    break;
                }
                final int referenceId = preparationItemId;
                if (referenceId <= 0
                    || (shortestPathOwnsTravel
                        && travelItemId > 0
                        && referenceId == travelItemId)
                    || (planContainsItemId(plan, referenceId)
                        && !isRuneReferenceId(referenceId))
                    || !preparationReferenceIds.add(referenceId))
                {
                    continue;
                }

                final int row = PREPARATION_REFERENCE_START_ROW
                    + preparationIndex / PREPARATION_REFERENCE_COLUMNS;
                final int column = PREPARATION_REFERENCE_START_COLUMN
                    + preparationIndex % PREPARATION_REFERENCE_COLUMNS;
                if (placePreparationReferenceItemId(
                    layout,
                    taggedItemIds,
                    preparationItemId,
                    position(row, column)
                ))
                {
                    layoutSlots++;
                    preparationIndex++;
                }
            }
        }

		/*
		 * FINAL TRAVEL POSTCONDITION
		 * --------------------------
		 * A resolved physical Shortest Path item must survive every organizer and
		 * duplicate-suppression branch. Repair its one authoritative cell before
		 * persisting, then fail closed if the layout model still cannot retain it.
		 */
		if (shortestPathOwnsTravel && travelItemId > 0
			&& !layoutContainsItemId(layout, travelItemId))
		{
			final int travelPosition = externalStagingTravel
				? STAGING_TRAVEL_STRIP_POSITION
				: position(
					2 + Math.max(0, travelSlotIndex) / 4,
					4 + Math.max(0, travelSlotIndex) % 4
				);
			if (placeInventoryItemId(
				layout, taggedItemIds, travelItemId, travelPosition
			))
			{
				layoutSlots++;
			}
		}
		if (shortestPathOwnsTravel && travelItemId > 0
			&& !layoutContainsItemId(layout, travelItemId))
		{
			return Result.failure(
				"The selected Shortest Path teleport could not be retained in the bank-tag layout."
			);
		}

        if (taggedItemIds.isEmpty())
        {
            return Result.failure(
                "The recommendation contains only unresolved item names. Open your bank once so SlayerPlus can select exact item IDs."
            );
        }

        /* Replace the old dynamic setup rather than creating a new tab per task. */
        tagManager.removeTag(TAG_NAME);
        for (final int itemId : taggedItemIds)
        {
            tagManager.addTag(itemId, TAG_NAME, false);
        }

        layoutManager.saveLayout(layout);

		/*
		 * Retain an exact snapshot solely to validate a later identical reopen.
		 * Any manual edit to either Bank Tags membership or layout makes the fast
		 * path fail closed and sends the request through this original writer again.
		 */
		lastSavedLayout = Arrays.copyOf(
			layout.getLayout(), layout.getLayout().length
		);
		lastSavedTaggedItemIds = new LinkedHashSet<>(taggedItemIds);

        final int iconItemId = chooseIcon(plan, taggedItemIds);
        ensurePersistentTagTab(iconItemId);

        final boolean bankOpen =
            client != null
                && client.getItemContainer(InventoryID.BANK) != null;

        if (bankOpen)
        {
            bankTagsService.openBankTag(
                TAG_NAME,
                BankTagsService.OPTION_ALLOW_MODIFICATIONS
                    | BankTagsService.OPTION_ITEMS_NOT_IN_LAYOUT_AT_BOTTOM
            );
        }

        final int unresolved = plan.getUnresolvedItemCount();
        final String omitted = unresolved > 0
            ? " " + unresolved + " unresolved recommendation"
                + (unresolved == 1 ? " was" : "s were")
                + " omitted."
            : "";

        return Result.success(
            bankOpen
                ? "Opened the real SlayerPlus Current bank-tag layout with "
                    + layoutSlots + " arranged slots." + omitted
                : "Created the SlayerPlus Current bank-tag layout with "
                    + layoutSlots + " arranged slots. Open the bank to view it."
                    + omitted
        );
    }

	/**
	 * Reopens the exact layout written by the most recent successful call without
	 * repeating removeTag/addTag/saveLayout. Returns false when anything was edited,
	 * allowing the caller to fall back to the unchanged full writer above.
	 */
	public boolean reopenLastSavedLayoutIfUnchanged()
	{
		if (lastSavedLayout.length == 0 || lastSavedTaggedItemIds.isEmpty())
		{
			return false;
		}
		final Layout persistedLayout = layoutManager.loadLayout(TAG_NAME);
		final List<Integer> persistedItems = tagManager.getItemsForTag(TAG_NAME);
		if (persistedLayout == null
			|| !samePersistedStateForRegression(
				lastSavedLayout,
				persistedLayout.getLayout(),
				lastSavedTaggedItemIds,
				persistedItems == null
					? Collections.emptySet()
					: new LinkedHashSet<>(persistedItems)
			))
		{
			return false;
		}

		if (client != null
			&& client.getItemContainer(InventoryID.BANK) != null)
		{
			bankTagsService.openBankTag(
				TAG_NAME,
				BankTagsService.OPTION_ALLOW_MODIFICATIONS
					| BankTagsService.OPTION_ITEMS_NOT_IN_LAYOUT_AT_BOTTOM
			);
		}
		return true;
	}

	static boolean samePersistedStateForRegression(
		final int[] expectedLayout,
		final int[] actualLayout,
		final Set<Integer> expectedItems,
		final Set<Integer> actualItems)
	{
		return Arrays.equals(expectedLayout, actualLayout)
			&& expectedItems != null
			&& expectedItems.equals(actualItems);
	}

    static boolean shouldSuppressInventoryTravelItem(
        final boolean shortestPathOwnsTravel,
        final int sourceIndex,
        final int selectedTravelItemId,
        final SlayerLoadoutItem item,
        final SlayerTravelSelection travelSelection)
    {
        return shouldSuppressInventoryTravelItem(
            shortestPathOwnsTravel,
            sourceIndex,
            selectedTravelItemId,
            item,
            travelSelection,
            true
        );
    }

    static boolean shouldSuppressInventoryTravelItem(
        final boolean shortestPathOwnsTravel,
        final int sourceIndex,
        final int selectedTravelItemId,
        final SlayerLoadoutItem item,
        final SlayerTravelSelection travelSelection,
        final boolean analyzerSourceZeroIsTravel)
    {
        if (!shortestPathOwnsTravel)
        {
            return false;
        }

        /*
         * Suppress the analyzer fallback only after Shortest Path has supplied
         * the concrete replacement that is actually reserved in the 4 x 7.
         */
        if (selectedTravelItemId > 0
            && item != null
            && (item.getInventoryGroup()
                == SlayerMethodRules.InventoryGroup.TRAVEL
                || (analyzerSourceZeroIsTravel
                    && sourceIndex == 0
                    && item.getInventoryGroup()
                        == SlayerMethodRules.InventoryGroup.OTHER)))
        {
            return true;
        }

        if (item == null)
        {
            return false;
        }

        if (selectedTravelItemId > 0
            && item.hasItemId()
            && item.getItemId() == selectedTravelItemId)
        {
            return true;
        }

        return travelSelection != null
            && travelSelection.hasPhysicalItem()
            && matchesEquivalentTravelFamily(
                item.getDisplayName(),
                travelSelection.getEquivalentItemFamilies()
            );
    }

    static boolean matchesEquivalentTravelFamily(
        final String itemDisplayName,
        final Set<String> routeFamilies)
    {
        final String itemFamily = canonicalTravelFamily(itemDisplayName);
        if (itemFamily.isEmpty() || routeFamilies == null || routeFamilies.isEmpty())
        {
            return false;
        }

        for (final String family : routeFamilies)
        {
            final String routeFamily = canonicalTravelFamily(family);
            if (!routeFamily.isEmpty() && itemFamily.equals(routeFamily))
            {
                return true;
            }
        }
        return false;
    }

    private static String canonicalTravelFamily(final String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return "";
        }

        String normalized = Text.removeTags(value)
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        normalized = normalized
            .replaceFirst("\\s+(?:[a-z]?\\d+|t|l)$", "")
            .replaceAll("\\beternal\\b", " ")
            .replace("construction cape", "construct cape")
            .replaceAll("\\s+", " ")
            .trim();
        return normalized;
    }

    private void ensurePersistentTagTab(final int iconItemId)
    {
        final String standardizedTag = Text.standardize(TAG_NAME);
        final String configuredTabs = configManager.getConfiguration(
            BankTagsPlugin.CONFIG_GROUP,
            BankTagsPlugin.TAG_TABS_CONFIG
        );
        final List<String> tabs = new ArrayList<>(
            Text.fromCSV(configuredTabs == null ? "" : configuredTabs)
        );

        boolean found = false;
        for (final String tab : tabs)
        {
            if (standardizedTag.equals(Text.standardize(tab)))
            {
                found = true;
                break;
            }
        }

        if (!found)
        {
            tabs.add(standardizedTag);
            configManager.setConfiguration(
                BankTagsPlugin.CONFIG_GROUP,
                BankTagsPlugin.TAG_TABS_CONFIG,
                Text.toCSV(tabs)
            );
        }

        if (iconItemId > 0)
        {
            configManager.setConfiguration(
                BankTagsPlugin.CONFIG_GROUP,
                BankTagsPlugin.TAG_ICON_PREFIX + standardizedTag,
                Integer.toString(iconItemId)
            );
        }
    }

    private static int chooseIcon(
        final SlayerLoadoutPlan plan,
        final Set<Integer> fallback)
    {
        final List<SlayerLoadoutItem> equipment = plan.getEquipmentItems();
        if (!equipment.isEmpty() && concrete(equipment.get(0)))
        {
            return equipment.get(0).getItemId();
        }
        if (equipment.size() > 4 && concrete(equipment.get(4)))
        {
            return equipment.get(4).getItemId();
        }
        return fallback.iterator().next();
    }

    private int resolveOwnedDizanaVariantId(
        final SlayerLoadoutPlan plan)
    {
        final List<SlayerLoadoutItem> equipment =
            plan == null ? Collections.emptyList() : plan.getEquipmentItems();
        final int plannedCapeId = equipment.size() > 1
            && concrete(equipment.get(1))
                ? equipment.get(1).getItemId()
                : -1;

        /*
         * Quiver-container data is ammo authority, not cape-selection authority.
         * Only repair the exact Dizana variant when this task's analyzed cape is
         * already a Dizana item. Otherwise simply owning a quiver would replace
         * Ava, magic, and melee capes in every generated Bank Tag.
         */
        if (!shouldResolveOwnedDizanaVariantForPlan(plannedCapeId))
        {
            return -1;
        }

        /* Preserve the analyzer's exact usable variant when it is still live. */
        if (isUsableDizanaVariantId(plannedCapeId)
            && isExactItemLive(plannedCapeId))
        {
            return plannedCapeId;
        }

        int resolved = bestDizanaVariantInContainer(
            client == null ? null : client.getItemContainer(InventoryID.WORN)
        );
        if (resolved > 0)
        {
            return resolved;
        }

        resolved = bestDizanaVariantInContainer(
            client == null ? null : client.getItemContainer(InventoryID.INV)
        );
        if (resolved > 0)
        {
            return resolved;
        }

        resolved = bestDizanaVariantInContainer(
            client == null ? null : client.getItemContainer(InventoryID.BANK)
        );
        if (resolved > 0)
        {
            return resolved;
        }

        /* Bank may be closed while the analyzed cache is still authoritative. */
        return isUsableDizanaVariantId(plannedCapeId)
            ? plannedCapeId
            : -1;
    }

    private boolean isExactItemLive(final int itemId)
    {
        if (itemId <= 0 || client == null)
        {
            return false;
        }
        return containerHasExactItem(
                client.getItemContainer(InventoryID.WORN), itemId)
            || containerHasExactItem(
                client.getItemContainer(InventoryID.INV), itemId)
            || containerHasExactItem(
                client.getItemContainer(InventoryID.BANK), itemId);
    }

    private static boolean containerHasExactItem(
        final ItemContainer container,
        final int itemId)
    {
        if (container == null || itemId <= 0)
        {
            return false;
        }
        for (final Item item : container.getItems())
        {
            if (item != null
                && item.getId() == itemId
                && item.getQuantity() > 0)
            {
                return true;
            }
        }
        return false;
    }

    private static int bestDizanaVariantInContainer(
        final ItemContainer container)
    {
        if (container == null)
        {
            return -1;
        }

        int bestId = -1;
        int bestScore = -1;
        for (final Item item : container.getItems())
        {
            if (item == null || item.getQuantity() <= 0)
            {
                continue;
            }
            final int itemId = item.getId();
            if (itemId <= 0)
            {
                continue;
            }
            final int score = dizanaVariantScore(itemId);
            if (score > bestScore)
            {
                bestScore = score;
                bestId = itemId;
            }
        }
        return bestScore > 0 ? bestId : -1;
    }

    private static boolean isUsableDizanaVariantId(final int rawItemId)
    {
        return rawItemId > 0 && dizanaVariantScore(rawItemId) > 0;
    }

    static boolean shouldResolveOwnedDizanaVariantForPlan(
        final int plannedCapeId)
    {
        return isUsableDizanaVariantId(plannedCapeId);
    }

    private static boolean isRuneReferenceId(final int itemId)
    {
        switch (itemId)
        {
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

    private static int dizanaVariantScore(final int itemId)
    {
        switch (itemId)
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
                return -1;
        }
    }

    private static boolean planContainsUsableDizanaCape(
        final SlayerLoadoutPlan plan)
    {
        if (plan == null)
        {
            return false;
        }

        for (final SlayerLoadoutItem item : plan.getEquipmentItems())
        {
            if (item == null || item.getDisplayName() == null)
            {
                continue;
            }

            if (item.hasItemId()
                && isUsableDizanaVariantId(item.getItemId()))
            {
                return true;
            }

            final String name = normalizeName(item.getDisplayName());
            if (name.contains("dizana")
                && (name.contains("quiver") || name.contains("max cape"))
                && !name.contains("max hood")
                && !name.contains("broken")
                && !name.contains("mangled"))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean planContainsItemId(
        final SlayerLoadoutPlan plan,
        final int rawItemId)
    {
        final int itemId = rawItemId;
        if (itemId <= 0)
        {
            return false;
        }
        return containsItemId(plan.getEquipmentItems(), itemId)
            || containsItemId(plan.getInventoryItems(), itemId)
            || containsItemId(plan.getOptionalItems(), itemId);
    }

    private static boolean containsItemId(
        final List<SlayerLoadoutItem> items,
        final int itemId)
    {
        for (final SlayerLoadoutItem item : items)
        {
            if (concrete(item)
                && item.getItemId() == itemId)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean duplicatesConcreteEquipment(
        final SlayerLoadoutItem optionalItem,
        final List<SlayerLoadoutItem> equipment)
    {
        return concrete(optionalItem)
            && containsItemId(equipment, optionalItem.getItemId());
    }

    static boolean optionalItemDuplicatesEquipmentForRegression(
        final SlayerLoadoutItem optionalItem,
        final List<SlayerLoadoutItem> equipment)
    {
        return duplicatesConcreteEquipment(optionalItem, equipment);
    }

    private static SlayerLoadoutItem findLightbearer(
        final SlayerLoadoutPlan plan)
    {
        SlayerLoadoutItem item = findLightbearer(plan.getEquipmentItems());
        if (item != null)
        {
            return item;
        }

        item = findLightbearer(plan.getInventoryItems());
        if (item != null)
        {
            return item;
        }

        return findLightbearer(plan.getOptionalItems());
    }

    private static SlayerLoadoutItem findLightbearer(
        final List<SlayerLoadoutItem> items)
    {
        if (items == null)
        {
            return null;
        }

        for (final SlayerLoadoutItem item : items)
        {
            if (isLightbearer(item) && concrete(item))
            {
                return item;
            }
        }
        return null;
    }

    private static boolean isLightbearer(final SlayerLoadoutItem item)
    {
        if (item == null || item.getDisplayName() == null)
        {
            return false;
        }

        return normalizeName(item.getDisplayName()).contains("lightbearer");
    }

    /**
     * Presentation-only policy for the 4 x 7 Bank Tag inventory.
     *
     * <p>The analyzer remains authoritative for item selection and quantities.
     * This planner assigns only visual cells: matching switch tops sit directly
     * above their legs, the rune pouch owns slot 28, and remaining supplies are
     * grouped consistently. No item is added to or removed from the loadout.</p>
     */
    static List<InventoryPlacement> planInventoryForBankTag(
        final List<SlayerLoadoutItem> source,
        final int reservedSlotIndex,
        final boolean sourceSlotZeroIsTravel)
    {
        if (source == null || source.isEmpty())
        {
            return Collections.emptyList();
        }

        final List<InventoryCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < source.size(); index++)
        {
            final SlayerLoadoutItem item = source.get(index);
            if (concrete(item) && !isLightbearer(item))
            {
                candidates.add(new InventoryCandidate(item, index));
            }
        }
        return planInventoryCandidates(
            candidates,
            reservedSlotIndex,
            sourceSlotZeroIsTravel
        );
    }

    private static List<InventoryPlacement> planInventoryCandidates(
        final List<InventoryCandidate> source,
        final int requestedReservedSlotIndex,
        final boolean sourceSlotZeroIsTravel)
    {
        if (source == null || source.isEmpty())
        {
            return Collections.emptyList();
        }

        final List<InventoryCandidate> remaining = new ArrayList<>(source);
        final List<InventoryPlacement> result = new ArrayList<>(source.size());
        final boolean[] occupied = new boolean[INVENTORY_LIMIT];
        final InventoryCandidate runePouch = firstRunePouch(remaining);
        int reservedSlotIndex = requestedReservedSlotIndex;
        if (runePouch != null && reservedSlotIndex == INVENTORY_LIMIT - 1)
        {
            reservedSlotIndex = 0;
        }
        if (reservedSlotIndex >= 0 && reservedSlotIndex < INVENTORY_LIMIT)
        {
            occupied[reservedSlotIndex] = true;
        }

        if (runePouch != null)
        {
            final int runePouchSlot = RUNE_POUCH_INVENTORY_SLOT;
            occupied[runePouchSlot] = true;
            result.add(new InventoryPlacement(runePouch.item, runePouchSlot));
            remaining.remove(runePouch);
        }

        /*
         * Keep authored travel beside the rune pouch in the bottom utility strip.
         * Shortest Path's authoritative item uses the same visual cell through
         * the reserved-slot path above.
         */
        if (sourceSlotZeroIsTravel)
        {
            final InventoryCandidate authoredTravel = findSourceIndex(
                remaining,
                0
            );
            if (authoredTravel != null)
            {
                final int preferred = runePouch == null
                    ? TRAVEL_SLOT_WITHOUT_RUNE_POUCH
                    : TRAVEL_SLOT_WITH_RUNE_POUCH;
                final int slot = !occupied[preferred]
                    ? preferred
                    : lastFreeSlot(occupied);
                if (slot >= 0)
                {
                    occupied[slot] = true;
                    result.add(new InventoryPlacement(authoredTravel.item, slot));
                    remaining.remove(authoredTravel);
                }
            }
        }

        /*
         * GLOBAL EQUIPMENT-SWITCH GRID
         * ----------------------------
         * The analyzer preserves which required items are real switches and
         * their combat style. Put the largest same-style package first, reading
         * down columns (1 -> 5, 2 -> 6, 3 -> 7, 4 -> 8). A different style
         * starts at the next column. Supplies are not allowed to split a switch
         * pair, though they may use the lower half of a final odd column.
         */
        placeEquipmentSwitchGroups(remaining, occupied, result);

        /* Legacy/unannotated body-and-leg recommendations retain the same shape. */
        final List<InventoryCandidate> tops = new ArrayList<>();
        final List<InventoryCandidate> legs = new ArrayList<>();
        for (final InventoryCandidate candidate : remaining)
        {
            if (!candidate.item.isEquipmentSwitch()
                && isArmorTop(candidate.item))
            {
                tops.add(candidate);
            }
            else if (!candidate.item.isEquipmentSwitch()
                && isArmorLegs(candidate.item))
            {
                legs.add(candidate);
            }
        }

        for (final InventoryCandidate top : tops)
        {
            final InventoryCandidate legsItem = takeMatchingLegs(top, legs);
            if (legsItem == null)
            {
                continue;
            }

            final int topSlot = firstFreeVerticalPair(occupied);
            if (topSlot < 0)
            {
                break;
            }

            final int legsSlot = topSlot + 4;
            occupied[topSlot] = true;
            occupied[legsSlot] = true;
            result.add(new InventoryPlacement(top.item, topSlot));
            result.add(new InventoryPlacement(legsItem.item, legsSlot));
            remaining.remove(top);
            remaining.remove(legsItem);
        }

        placeCannonBlock(remaining, occupied, result);
        placeBottomUtilities(remaining, occupied, result);

        /* List.sort is stable, so remaining core items keep authored order. */
        remaining.sort((left, right) ->
        {
            final int leftGroup = bankInventoryVisualGroup(left.item);
            final int rightGroup = bankInventoryVisualGroup(right.item);
            if (leftGroup != rightGroup)
            {
                return Integer.compare(leftGroup, rightGroup);
            }
            if (leftGroup == 0)
            {
                return 0;
            }

            final int family = bankInventoryVisualFamily(left.item).compareTo(
                bankInventoryVisualFamily(right.item)
            );
            if (family != 0)
            {
                return family;
            }
            return normalizeName(left.item.getDisplayName()).compareTo(
                normalizeName(right.item.getDisplayName())
            );
        });

        for (final InventoryCandidate candidate : remaining)
        {
            final int slot = firstFreeSlot(occupied);
            if (slot < 0)
            {
                break;
            }
            occupied[slot] = true;
            result.add(new InventoryPlacement(candidate.item, slot));
        }

        return result;
    }

    private static void placeCannonBlock(
        final List<InventoryCandidate> remaining,
        final boolean[] occupied,
        final List<InventoryPlacement> result)
    {
        final List<InventoryCandidate> parts = new ArrayList<>();
        for (final InventoryCandidate candidate : remaining)
        {
            if (cannonPartOrder(candidate.item) >= 0)
            {
                parts.add(candidate);
            }
        }
        if (parts.size() < 4)
        {
            return;
        }

        parts.sort((left, right) -> Integer.compare(
            cannonPartOrder(left.item),
            cannonPartOrder(right.item)
        ));
        final int blockStart = firstFreeTwoByTwoBlock(occupied);
        if (blockStart < 0)
        {
            return;
        }

        final int[] positions = {
            blockStart,
            blockStart + 1,
            blockStart + 4,
            blockStart + 5
        };
        for (int index = 0; index < 4; index++)
        {
            final InventoryCandidate part = parts.get(index);
            final int slot = positions[index];
            occupied[slot] = true;
            result.add(new InventoryPlacement(part.item, slot));
            remaining.remove(part);
        }
    }

    private static int cannonPartOrder(final SlayerLoadoutItem item)
    {
        final String name = normalizeName(
            item == null ? "" : item.getDisplayName()
        );
        if (name.contains("cannon base"))
        {
            return 0;
        }
        if (name.contains("cannon stand"))
        {
            return 1;
        }
        if (name.contains("cannon barrels"))
        {
            return 2;
        }
        if (name.contains("cannon furnace"))
        {
            return 3;
        }
        return -1;
    }

    private static int firstFreeTwoByTwoBlock(final boolean[] occupied)
    {
        final int[] topRows = {0, 2, 4};
        for (final int row : topRows)
        {
            for (int column = 0; column < 3; column++)
            {
                final int start = row * 4 + column;
                if (!occupied[start] && !occupied[start + 1]
                    && !occupied[start + 4] && !occupied[start + 5])
                {
                    return start;
                }
            }
        }
        return -1;
    }

    private static void placeBottomUtilities(
        final List<InventoryCandidate> remaining,
        final boolean[] occupied,
        final List<InventoryPlacement> result)
    {
        final List<InventoryCandidate> utilities = new ArrayList<>();
        for (final InventoryCandidate candidate : remaining)
        {
            if (isBottomUtility(candidate.item))
            {
                utilities.add(candidate);
            }
        }
        if (utilities.isEmpty())
        {
            return;
        }

        final List<Integer> slots = bottomAlignedFreeSlots(
            occupied,
            utilities.size()
        );
        for (int index = 0; index < utilities.size() && index < slots.size(); index++)
        {
            final InventoryCandidate utility = utilities.get(index);
            final int slot = slots.get(index);
            occupied[slot] = true;
            result.add(new InventoryPlacement(utility.item, slot));
            remaining.remove(utility);
        }
    }

    private static List<Integer> bottomAlignedFreeSlots(
        final boolean[] occupied,
        final int requestedCount)
    {
        final List<Integer> result = new ArrayList<>();
        int remaining = requestedCount;
        for (int row = 6; row >= 0 && remaining > 0; row--)
        {
            final List<Integer> free = new ArrayList<>();
            for (int column = 0; column < 4; column++)
            {
                final int slot = row * 4 + column;
                if (!occupied[slot])
                {
                    free.add(slot);
                }
            }

            final int take = Math.min(remaining, free.size());
            final int first = free.size() - take;
            for (int index = first; index < free.size(); index++)
            {
                result.add(free.get(index));
            }
            remaining -= take;
        }
        return result;
    }

    private static boolean isBottomUtility(final SlayerLoadoutItem item)
    {
        if (item == null || item.isEquipmentSwitch())
        {
            return false;
        }
        if (item.getInventoryGroup() == SlayerMethodRules.InventoryGroup.UTILITY)
        {
            return !isRunePouch(item);
        }

        final String name = normalizeName(item.getDisplayName());
        return name.contains("herb sack") || name.contains("seed box")
            || name.contains("soul bearer") || name.contains("bonecrusher")
            || name.contains("ash sanctifier") || name.contains("holy wrench")
            || name.contains("looting bag") || name.contains("gem bag")
            || name.contains("book of the dead")
            || name.contains("explorer s ring")
            || name.contains("rock hammer") || name.contains("bag of salt")
            || name.contains("ice cooler") || name.contains("slayer bell")
            || name.contains("fungicide") || name.contains("fishing explosive")
            || name.contains("task tool");
    }

    private static void placeEquipmentSwitchGroups(
        final List<InventoryCandidate> remaining,
        final boolean[] occupied,
        final List<InventoryPlacement> result)
    {
        final List<SwitchGroup> groups = new ArrayList<>();
        for (final InventoryCandidate candidate : remaining)
        {
            if (!candidate.item.isEquipmentSwitch())
            {
                continue;
            }

            SwitchGroup matching = null;
            for (final SwitchGroup group : groups)
            {
                if (group.style == candidate.item.getSwitchStyle())
                {
                    matching = group;
                    break;
                }
            }
            if (matching == null)
            {
                matching = new SwitchGroup(candidate.item.getSwitchStyle());
                groups.add(matching);
            }
            matching.items.add(candidate);
        }

        /* Stable sort: equal-size style packages retain authored style order. */
        groups.sort((left, right) -> Integer.compare(
            right.items.size(),
            left.items.size()
        ));

        final List<Integer> verticalColumns = new ArrayList<>();
        final int[] topRows = {0, 2, 4};
        for (final int topRow : topRows)
        {
            for (int column = 0; column < 4; column++)
            {
                final int topSlot = topRow * 4 + column;
                if (!occupied[topSlot] && !occupied[topSlot + 4])
                {
                    verticalColumns.add(topSlot);
                }
            }
        }

        int columnIndex = 0;
        for (final SwitchGroup group : groups)
        {
            final List<InventoryCandidate> ordered =
                orderSwitchGroup(group.items);
            int itemIndex = 0;
            while (itemIndex < ordered.size()
                && columnIndex < verticalColumns.size())
            {
                final int topSlot = verticalColumns.get(columnIndex++);
                final InventoryCandidate upper = ordered.get(itemIndex++);
                occupied[topSlot] = true;
                result.add(new InventoryPlacement(upper.item, topSlot));
                remaining.remove(upper);

                if (itemIndex < ordered.size())
                {
                    final int lowerSlot = topSlot + 4;
                    final InventoryCandidate lower = ordered.get(itemIndex++);
                    occupied[lowerSlot] = true;
                    result.add(new InventoryPlacement(lower.item, lowerSlot));
                    remaining.remove(lower);
                }
            }
        }
    }

    private static List<InventoryCandidate> orderSwitchGroup(
        final List<InventoryCandidate> source)
    {
        final List<InventoryCandidate> remaining = new ArrayList<>(source);
        final List<InventoryCandidate> ordered = new ArrayList<>(source.size());
        final List<InventoryCandidate> tops = new ArrayList<>();
        final List<InventoryCandidate> legs = new ArrayList<>();
        for (final InventoryCandidate candidate : source)
        {
            if (isArmorTop(candidate.item))
            {
                tops.add(candidate);
            }
            else if (isArmorLegs(candidate.item))
            {
                legs.add(candidate);
            }
        }

        for (final InventoryCandidate top : tops)
        {
            final InventoryCandidate legsItem = takeMatchingLegs(top, legs);
            if (legsItem != null)
            {
                ordered.add(top);
                ordered.add(legsItem);
                remaining.remove(top);
                remaining.remove(legsItem);
            }
        }
        ordered.addAll(remaining);
        return ordered;
    }

    private static InventoryCandidate firstRunePouch(
        final List<InventoryCandidate> candidates)
    {
        for (final InventoryCandidate candidate : candidates)
        {
            if (isRunePouch(candidate.item))
            {
                return candidate;
            }
        }
        return null;
    }

    private static InventoryCandidate findSourceIndex(
        final List<InventoryCandidate> candidates,
        final int sourceIndex)
    {
        for (final InventoryCandidate candidate : candidates)
        {
            if (candidate.sourceIndex == sourceIndex)
            {
                return candidate;
            }
        }
        return null;
    }

    private static InventoryCandidate takeMatchingLegs(
        final InventoryCandidate top,
        final List<InventoryCandidate> availableLegs)
    {
        if (availableLegs.isEmpty())
        {
            return null;
        }

        final String topFamily = armorSetFamily(top.item);
        for (int index = 0; index < availableLegs.size(); index++)
        {
            final InventoryCandidate candidate = availableLegs.get(index);
            if (!topFamily.isEmpty()
                && topFamily.equals(armorSetFamily(candidate.item)))
            {
                availableLegs.remove(index);
                return candidate;
            }
        }

        /* Catalogs author switch tops and legs in the same style order. */
        return availableLegs.remove(0);
    }

    private static int firstFreeVerticalPair(final boolean[] occupied)
    {
        final int[] topRows = {0, 2, 4};
        for (final int topRow : topRows)
        {
            for (int column = 0; column < 4; column++)
            {
                final int topSlot = topRow * 4 + column;
                final int legsSlot = topSlot + 4;
                if (!occupied[topSlot] && !occupied[legsSlot])
                {
                    return topSlot;
                }
            }
        }
        return -1;
    }

    private static int firstFreeSlot(final boolean[] occupied)
    {
        for (int slot = 0; slot < occupied.length; slot++)
        {
            if (!occupied[slot])
            {
                return slot;
            }
        }
        return -1;
    }

    private static int lastFreeSlot(final boolean[] occupied)
    {
        for (int slot = occupied.length - 1; slot >= 0; slot--)
        {
            if (!occupied[slot])
            {
                return slot;
            }
        }
        return -1;
    }

    private static int preferredTravelSlot(
        final List<SlayerLoadoutItem> inventoryItems)
    {
        return containsConcreteRunePouch(inventoryItems)
            ? TRAVEL_SLOT_WITH_RUNE_POUCH
            : TRAVEL_SLOT_WITHOUT_RUNE_POUCH;
    }

    private static boolean containsConcreteRunePouch(
        final List<SlayerLoadoutItem> items)
    {
        if (items == null)
        {
            return false;
        }
        for (final SlayerLoadoutItem item : items)
        {
            if (concrete(item) && isRunePouch(item))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isRunePouch(final SlayerLoadoutItem item)
    {
        return normalizeName(item == null ? "" : item.getDisplayName())
            .contains("rune pouch");
    }

    private static boolean isArmorTop(final SlayerLoadoutItem item)
    {
        final String name = normalizeName(
            item == null ? "" : item.getDisplayName()
        );
        final String words = " " + name + " ";
        return name.contains("robe top") || name.contains("robetop")
            || name.contains("leathertop") || name.contains("platebody")
            || name.contains("chainbody") || name.contains("chestplate")
            || name.contains("hauberk") || name.contains("torso")
            || name.contains("tunic") || words.contains(" body ")
            || words.contains(" top ");
    }

    private static boolean isArmorLegs(final SlayerLoadoutItem item)
    {
        final String name = normalizeName(
            item == null ? "" : item.getDisplayName()
        );
        final String words = " " + name + " ";
        return name.contains("robe bottom") || name.contains("robebottom")
            || name.contains("robeskirt") || name.contains("leatherskirt")
            || name.contains("platelegs") || name.contains("plateskirt")
            || name.contains("chainskirt") || name.contains("tassets")
            || name.contains("chaps") || name.contains("trousers")
            || name.contains("cuisse")
            || words.contains(" leg ") || words.contains(" legs ")
            || words.contains(" skirt ")
            || words.contains(" bottoms ");
    }

    private static String armorSetFamily(final SlayerLoadoutItem item)
    {
        return normalizeName(item == null ? "" : item.getDisplayName())
            .replaceAll(
                "\\b(?:robe top|robe bottom|robetop|robebottom|robeskirt|"
                    + "leathertop|leatherskirt|platebody|platelegs|plateskirt|"
                    + "chainbody|chainskirt|chestplate|hauberk|torso|tunic|"
                    + "tassets|chaps|trousers|cuisse|body|top|leg|legs|skirt|bottoms)\\b",
                " "
            )
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static final class InventoryCandidate
    {
        private final SlayerLoadoutItem item;
        private final int sourceIndex;

        private InventoryCandidate(
            final SlayerLoadoutItem item,
            final int sourceIndex)
        {
            this.item = item;
            this.sourceIndex = sourceIndex;
        }
    }

    private static final class SwitchGroup
    {
        private final SlayerLoadoutItem.SwitchStyle style;
        private final List<InventoryCandidate> items = new ArrayList<>();

        private SwitchGroup(final SlayerLoadoutItem.SwitchStyle style)
        {
            this.style = style == null
                ? SlayerLoadoutItem.SwitchStyle.OTHER
                : style;
        }
    }

    static final class InventoryPlacement
    {
        private final SlayerLoadoutItem item;
        private final int slotIndex;

        private InventoryPlacement(
            final SlayerLoadoutItem item,
            final int slotIndex)
        {
            this.item = item;
            this.slotIndex = slotIndex;
        }

        SlayerLoadoutItem getItem()
        {
            return item;
        }

        int getSlotIndex()
        {
            return slotIndex;
        }
    }

    private static int bankInventoryVisualGroup(final SlayerLoadoutItem item)
    {
        final String name = normalizeName(
            item == null ? "" : item.getDisplayName()
        );

        if (name.contains("saradomin brew"))
        {
            return 3;
        }
        if (name.contains("goading potion"))
        {
            return 0;
        }
        if (name.contains("super restore")
            || name.contains("prayer potion")
            || name.contains("prayer restoration")
            || name.contains("prayer regeneration")
            || name.contains("sanfew serum"))
        {
            return 4;
        }
        if (isBankInventoryFood(name))
        {
            return 5;
        }
        if (name.contains("anti venom") || name.contains("antivenom")
            || name.contains("antipoison") || name.contains("stamina potion")
            || name.contains("antifire"))
        {
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
            || name.contains("saturated heart"))
        {
            return 1;
        }
        return 0;
    }

    private static boolean isBankInventoryFood(final String name)
    {
        return name.contains("anglerfish") || name.contains("manta ray")
            || name.contains("dark crab") || name.contains("shark")
            || name.contains("sea turtle") || name.contains("karambwan")
            || name.contains("guthix rest") || name.contains("monkfish")
            || name.contains("high healing food");
    }

    private static String bankInventoryVisualFamily(
        final SlayerLoadoutItem item)
    {
        return normalizeName(item == null ? "" : item.getDisplayName())
            .replaceFirst("^divine ", "")
            .replaceFirst(" [1-4]$", "")
            .trim();
    }

    private static String normalizeName(final String value)
    {
        return value == null
            ? ""
            : value.toLowerCase(java.util.Locale.ENGLISH)
                .replace('’', '\'')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static boolean place(
        final Layout layout,
        final Set<Integer> taggedItemIds,
        final SlayerLoadoutItem item,
        final int position)
    {
        if (!concrete(item))
        {
            return false;
        }

        return placeItemId(
            layout,
            taggedItemIds,
            item.getItemId(),
            position
        );
    }

    private static boolean placeInventory(
        final Layout layout,
        final Set<Integer> taggedItemIds,
        final SlayerLoadoutItem item,
        final int position)
    {
        if (!concrete(item))
        {
            return false;
        }

        return placeInventoryItemId(
            layout,
            taggedItemIds,
            item.getItemId(),
            position
        );
    }

    /**
     * Inventory layouts intentionally allow the same item ID at multiple
     * positions. Bank Tag Layouts supports repeated layout entries (Layout
     * exposes count(itemId)); this is required to visually represent e.g.
     * eight restores and the remaining food slots instead of collapsing every
     * stackable/same-ID supply into one cell. The tag itself remains unique.
     */
    private static boolean placeInventoryItemId(
        final Layout layout,
        final Set<Integer> taggedItemIds,
        final int rawItemId,
        final int position)
    {
        if (rawItemId <= 0)
        {
            return false;
        }

        taggedItemIds.add(rawItemId);
        layout.setItemAtPos(rawItemId, position);
        return true;
    }

	private static boolean layoutContainsItemId(
		final Layout layout,
		final int itemId)
	{
		if (layout == null || itemId <= 0 || layout.getLayout() == null)
		{
			return false;
		}
		for (final int value : layout.getLayout())
		{
			if (value == itemId)
			{
				return true;
			}
		}
		return false;
	}

    /**
     * Keep the recommended Dizana ammunition in its dedicated layout cell even
     * when the same item ID is tagged elsewhere in the loadout.
     */
    private static boolean placeNestedQuiverAmmoItemId(
        final Layout layout,
        final Set<Integer> taggedItemIds,
        final int rawItemId,
        final int position)
    {
        /*
         * This path receives a recommended game item ID. Negative values are
         * "no item" sentinels and must never be converted with Math.abs().
         */
        if (rawItemId <= 0)
        {
            return false;
        }

        taggedItemIds.add(rawItemId);
        layout.setItemAtPos(rawItemId, position);
        return true;
    }

    /**
     * Rune-pouch contents are explanatory references below the mannequin. A
     * rune can also be a real overflow stack in the inventory grid, so layout
     * placement must allow the same item ID in both places while tag membership
     * remains deduplicated.
     */
    private static boolean placePreparationReferenceItemId(
        final Layout layout,
        final Set<Integer> taggedItemIds,
        final int rawItemId,
        final int position)
    {
        if (rawItemId <= 0)
        {
            return false;
        }
        taggedItemIds.add(rawItemId);
        layout.setItemAtPos(rawItemId, position);
        return true;
    }

    private static boolean placeItemId(
        final Layout layout,
        final Set<Integer> taggedItemIds,
        final int rawItemId,
        final int position)
    {
        if (rawItemId <= 0 || !taggedItemIds.add(rawItemId))
        {
            return false;
        }

        layout.setItemAtPos(rawItemId, position);
        return true;
    }

    private static boolean concrete(final SlayerLoadoutItem item)
    {
        return item != null && item.hasItemId();
    }

    private static int position(final int row, final int column)
    {
        return row * BANK_COLUMNS + column;
    }

    static boolean hasDedicatedExtraQuiverAmmoPositionForRegression()
    {
        return EQUIPMENT_POSITIONS.length > 11
            && EXTRA_QUIVER_AMMO_POSITION == position(2, 2)
            && EQUIPMENT_POSITIONS[3] == position(3, 2)
            && EQUIPMENT_POSITIONS[11] == EXTRA_QUIVER_AMMO_POSITION
            && EXTRA_QUIVER_AMMO_POSITION != EQUIPMENT_POSITIONS[3];
    }

    static boolean preparationReferencesRenderBelowGearForRegression()
    {
        int lowestEquipmentRow = 0;
        for (final int equipmentPosition : EQUIPMENT_POSITIONS)
        {
            lowestEquipmentRow = Math.max(
                lowestEquipmentRow,
                equipmentPosition / BANK_COLUMNS
            );
        }
        return PREPARATION_REFERENCE_START_ROW > lowestEquipmentRow
            && PREPARATION_REFERENCE_START_COLUMN == 0
            && PREPARATION_REFERENCE_COLUMNS == 4;
    }

    static boolean usesExternalStagingTravelStrip(
        final int authoritativeTravelSlotIndex)
    {
        return authoritativeTravelSlotIndex < 0;
    }

    public static final class Result
    {
        private final boolean success;
        private final String message;

        private Result(final boolean success, final String message)
        {
            this.success = success;
            this.message = message;
        }

        public static Result success(final String message)
        {
            return new Result(true, message);
        }

        public static Result failure(final String message)
        {
            return new Result(false, message);
        }

        public boolean isSuccess()
        {
            return success;
        }

        public String getMessage()
        {
            return message;
        }
    }
}
