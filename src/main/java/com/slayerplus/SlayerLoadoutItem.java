package com.slayerplus;

public final class SlayerLoadoutItem
{
	public enum SwitchStyle
	{
		MAGIC,
		RANGED,
		MELEE,
		OTHER
	}

    public enum Status
    {
        EQUIPPED("Equipped"),
        INVENTORY("In inventory"),
        BANK("In bank"),
        MISSING("Missing"),
        UNKNOWN("Bank not scanned");

        private final String label;

        Status(final String label)
        {
            this.label = label;
        }

        public String getLabel()
        {
            return label;
        }
    }

    private final String displayName;
    private final int itemId;
    private final int quantity;
	private final Status status;
	private final boolean equipmentSwitch;
	private final SwitchStyle switchStyle;
	private final SlayerMethodRules.InventoryGroup inventoryGroup;

    public SlayerLoadoutItem(
        final String displayName,
        final int itemId,
        final int quantity,
        final Status status)
	{
		this(
			displayName,
			itemId,
			quantity,
			status,
			false,
			SwitchStyle.OTHER,
			SlayerMethodRules.InventoryGroup.OTHER
		);
	}

	private SlayerLoadoutItem(
		final String displayName,
		final int itemId,
		final int quantity,
		final Status status,
		final boolean equipmentSwitch,
		final SwitchStyle switchStyle,
		final SlayerMethodRules.InventoryGroup inventoryGroup)
    {
        this.displayName = displayName == null || displayName.trim().isEmpty()
            ? "Recommended item"
            : displayName.trim();
        this.itemId = itemId;
        this.quantity = Math.max(1, quantity);
        this.status = status == null ? Status.UNKNOWN : status;
		this.equipmentSwitch = equipmentSwitch;
		this.switchStyle = switchStyle == null
			? SwitchStyle.OTHER
			: switchStyle;
		this.inventoryGroup = inventoryGroup == null
			? SlayerMethodRules.InventoryGroup.OTHER
			: inventoryGroup;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public int getItemId()
    {
        return itemId;
    }

    public int getQuantity()
    {
        return quantity;
    }

    public Status getStatus()
    {
        return status;
    }

	public boolean isEquipmentSwitch()
	{
		return equipmentSwitch;
	}

	public SwitchStyle getSwitchStyle()
	{
		return switchStyle;
	}

	public SlayerMethodRules.InventoryGroup getInventoryGroup()
	{
		return inventoryGroup;
	}

	public SlayerLoadoutItem asEquipmentSwitch(final SwitchStyle style)
	{
		return new SlayerLoadoutItem(
			displayName,
			itemId,
			quantity,
			status,
			true,
			style,
			inventoryGroup
		);
	}

	public SlayerLoadoutItem withInventoryGroup(
		final SlayerMethodRules.InventoryGroup group)
	{
		return new SlayerLoadoutItem(
			displayName,
			itemId,
			quantity,
			status,
			equipmentSwitch,
			switchStyle,
			group
		);
	}

    public boolean hasItemId()
    {
        return itemId > 0;
    }

    public boolean isBanked()
    {
        return status == Status.BANK;
    }
}
