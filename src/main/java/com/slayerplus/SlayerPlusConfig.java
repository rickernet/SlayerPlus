package com.slayerplus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SlayerPlusConfig.GROUP)
public interface SlayerPlusConfig extends Config
{
	String GROUP = "slayerplus";

	@ConfigSection(
		name = "Slayer workflow",
		description = "Controls normal Slayer sessions or guided Turael point boosting.",
		position = 0
	)
	String WORKFLOW_SECTION = "workflow";

	@ConfigSection(
		name = "Recommendation preferences",
		description = "Controls how SlayerPlus ranks valid task locations.",
		position = 1
	)
	String RECOMMENDATION_SECTION = "recommendation";


	@ConfigSection(
		name = "Loadout scanning",
		description = "Controls equipment, inventory, and bank recommendations.",
		position = 2
	)
	String LOADOUT_SECTION = "loadout";

	@ConfigSection(
		name = "Routing",
		description = "Controls Shortest Path route requests.",
		position = 3
	)
	String ROUTING_SECTION = "routing";

	@ConfigItem(
		keyName = "slayerWorkflow",
		name = "Slayer workflow",
		description = "Normal Slayer follows the assigning master. Turael point boosting uses nine Turael or Aya tasks followed by each tenth task from the selected bonus master.",
		position = 0,
		section = WORKFLOW_SECTION
	)
	default SlayerPreference.Workflow slayerWorkflow()
	{
		return SlayerPreference.Workflow.NORMAL;
	}

	@ConfigItem(
		keyName = "pointBoostBonusMaster",
		name = "Bonus-task master",
		description = "The Slayer master used for every tenth point-boosting task.",
		position = 1,
		section = WORKFLOW_SECTION
	)
	default SlayerPreference.BonusMaster pointBoostBonusMaster()
	{
		return SlayerPreference.BonusMaster.KONAR;
	}

	@ConfigItem(
		keyName = "combatStylePreference",
		name = "Combat style",
		description = "Automatic uses the best individually researched method. A preferred style is used only when the task has a verified viable setup for it.",
		position = 0,
		section = RECOMMENDATION_SECTION
	)
	default SlayerPreference.CombatStyle combatStylePreference()
	{
		return SlayerPreference.CombatStyle.AUTOMATIC;
	}

	@ConfigItem(
		keyName = "playstyle",
		name = "Playstyle",
		description = "Fast XP prioritizes maximum damage without considering gear or charge cost. Profit favors efficient supplies and loot.",
		position = 1,
		section = RECOMMENDATION_SECTION
	)
	default SlayerPreference.Playstyle playstyle()
	{
		return SlayerPreference.Playstyle.FAST_XP;
	}

	@ConfigItem(
		keyName = "cannonPreference",
		name = "Cannon",
		description = "Prefer, allow, or avoid cannon locations.",
		position = 2,
		section = RECOMMENDATION_SECTION
	)
	default SlayerPreference.Cannon cannonPreference()
	{
		return SlayerPreference.Cannon.ALLOW;
	}

	@ConfigItem(
		keyName = "burstPreference",
		name = "Burst / barrage",
		description = "Prefer, allow, or avoid burst and barrage methods.",
		position = 3,
		section = RECOMMENDATION_SECTION
	)
	default SlayerPreference.Burst burstPreference()
	{
		return SlayerPreference.Burst.ALLOW;
	}

	@ConfigItem(
		keyName = "travelPreference",
		name = "Travel",
		description = "Choose how travel routes affect location scoring.",
		position = 4,
		section = RECOMMENDATION_SECTION
	)
	default SlayerPreference.Travel travelPreference()
	{
		return SlayerPreference.Travel.FASTEST;
	}

	@ConfigItem(
		keyName = "shardPreference",
		name = "Shard preference",
		description = "Prefer valid task locations that drop ancient shards, crystal shards, both, or neither. Assigned-area and Wilderness restrictions always take priority.",
		position = 5,
		section = RECOMMENDATION_SECTION
	)
	default SlayerPreference.Shard shardPreference()
	{
		return SlayerPreference.Shard.NO_PREFERENCE;
	}

	@ConfigItem(
		keyName = "showLoadoutRecommendations",
		name = "Show loadout recommendations",
		description = "Scan worn, inventory, and cached bank items for the current task.",
		position = 0,
		section = LOADOUT_SECTION
	)
	default boolean showLoadoutRecommendations()
	{
		return true;
	}


	@ConfigItem(
		keyName = "enableShortestPathRouting",
		name = "Enable route button",
		description = "Send the recommended destination to the Shortest Path plugin.",
		position = 0,
		section = ROUTING_SECTION
	)
	default boolean enableShortestPathRouting()
	{
		return true;
	}

}
