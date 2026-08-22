package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SlayerLoadoutPlan
{
    private final String equipment;
    private final String inventory;
    private final String ownedStatus;
    private final String layoutTitle;
    private final List<SlayerLoadoutItem> equipmentItems;
    private final List<SlayerLoadoutItem> inventoryItems;
    private final List<SlayerLoadoutItem> optionalItems;

    public SlayerLoadoutPlan(
        final String equipment,
        final String inventory,
        final String ownedStatus)
    {
        this(
            equipment,
            inventory,
            ownedStatus,
            "Recommended setup",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    public SlayerLoadoutPlan(
        final String equipment,
        final String inventory,
        final String ownedStatus,
        final String layoutTitle,
        final List<SlayerLoadoutItem> equipmentItems,
        final List<SlayerLoadoutItem> inventoryItems,
        final List<SlayerLoadoutItem> optionalItems)
    {
        this.equipment = safe(equipment, "Use your strongest task setup");
        this.inventory = safe(inventory, "Food and task supplies");
        this.ownedStatus = safe(ownedStatus, "No item scan available");
        this.layoutTitle = safe(layoutTitle, "Recommended setup");
        this.equipmentItems = immutableCopy(equipmentItems);
        this.inventoryItems = immutableCopy(inventoryItems);
        this.optionalItems = immutableCopy(optionalItems);
    }

    public String getEquipment()
    {
        return equipment;
    }

    public String getInventory()
    {
        return inventory;
    }

    public String getOwnedStatus()
    {
        return ownedStatus;
    }

    public String getLayoutTitle()
    {
        return layoutTitle;
    }

    public List<SlayerLoadoutItem> getEquipmentItems()
    {
        return equipmentItems;
    }

    public List<SlayerLoadoutItem> getInventoryItems()
    {
        return inventoryItems;
    }

    public List<SlayerLoadoutItem> getOptionalItems()
    {
        return optionalItems;
    }

    public List<SlayerLoadoutItem> getBankWithdrawalItems()
    {
        final List<SlayerLoadoutItem> result = new ArrayList<>();
        final Set<Integer> seenIds = new LinkedHashSet<>();

        addBankedUnique(result, seenIds, equipmentItems);
        addBankedUnique(result, seenIds, inventoryItems);
        addBankedUnique(result, seenIds, optionalItems);
        return Collections.unmodifiableList(result);
    }

    public boolean hasVisualLayout()
    {
        return !equipmentItems.isEmpty() || !inventoryItems.isEmpty();
    }

    public boolean hasConcreteItems()
    {
        return getConcreteItemCount() > 0;
    }

    public int getConcreteItemCount()
    {
        return countConcrete(equipmentItems)
            + countConcrete(inventoryItems)
            + countConcrete(optionalItems);
    }

    public int getUnresolvedItemCount()
    {
        return countUnresolved(equipmentItems)
            + countUnresolved(inventoryItems)
            + countUnresolved(optionalItems);
    }

    public static SlayerLoadoutPlan hidden()
    {
        return new SlayerLoadoutPlan(
            "Hidden in settings",
            "Hidden in settings",
            "Loadout recommendations disabled",
            "Loadout recommendations disabled",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    public static SlayerLoadoutPlan researchPending(final String taskName)
    {
        final String task = taskName == null || taskName.trim().isEmpty()
            ? "This task"
            : taskName.trim();
        return new SlayerLoadoutPlan(
            "Research pending",
            "No inventory generated",
            "SlayerPlus will not create a broad or guessed loadout.",
            task + " — strategy review pending",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    public static SlayerLoadoutPlan empty()
    {
        return new SlayerLoadoutPlan(
            "—",
            "—",
            "No active loadout",
            "Waiting for Slayer task",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    private static void addBankedUnique(
        final List<SlayerLoadoutItem> destination,
        final Set<Integer> seenIds,
        final List<SlayerLoadoutItem> source)
    {
        for (final SlayerLoadoutItem item : source)
        {
            if (item != null
                && item.isBanked()
                && item.hasItemId()
                && seenIds.add(item.getItemId()))
            {
                destination.add(item);
            }
        }
    }

    private static int countConcrete(
        final List<SlayerLoadoutItem> items)
    {
        int count = 0;
        for (final SlayerLoadoutItem item : items)
        {
            if (item != null && item.hasItemId())
            {
                count++;
            }
        }
        return count;
    }

    private static int countUnresolved(
        final List<SlayerLoadoutItem> items)
    {
        int count = 0;
        for (final SlayerLoadoutItem item : items)
        {
            if (item != null
                && !item.hasItemId()
                && (item.getStatus() == SlayerLoadoutItem.Status.MISSING
                    || item.getStatus() == SlayerLoadoutItem.Status.UNKNOWN))
            {
                count++;
            }
        }
        return count;
    }

    private static List<SlayerLoadoutItem> immutableCopy(
        final List<SlayerLoadoutItem> source)
    {
        if (source == null || source.isEmpty())
        {
            return Collections.emptyList();
        }

        final List<SlayerLoadoutItem> copy = new ArrayList<>();
        for (final SlayerLoadoutItem item : source)
        {
            if (item != null)
            {
                copy.add(item);
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static String safe(
        final String value,
        final String fallback)
    {
        return value == null || value.trim().isEmpty()
            ? fallback
            : value.trim();
    }
}
