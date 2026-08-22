package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

public final class SlayerRecommendationEngine
{
	private static final int KRYSTILIA_MASTER_ID = 7;
	private static final int KONAR_MASTER_ID = 8;
	private static final Map<String, List<LocationOption>> TASK_LOCATIONS =
		createTaskLocations();

	private final Client client;
	private final SlayerPlusConfig config;
	private final Map<String, List<LocationOption>> taskLocations;

	public SlayerRecommendationEngine(final Client client)
	{
		this(client, null);
	}

	public SlayerRecommendationEngine(
		final Client client,
		final SlayerPlusConfig config)
	{
		this.client = client;
		this.config = config;
		this.taskLocations = TASK_LOCATIONS;
	}

	static Set<String> catalogLocationsForValidation(final String taskName)
	{
		final List<LocationOption> options = TASK_LOCATIONS.get(
			normalize(taskName)
		);
		if (options == null || options.isEmpty())
		{
			return Collections.emptySet();
		}
		final Set<String> locations = new LinkedHashSet<>();
		for (final LocationOption option : options)
		{
			if (option != null
				&& option.location != null
				&& !option.location.trim().isEmpty())
			{
				locations.add(option.location.trim());
			}
		}
		return Collections.unmodifiableSet(locations);
	}

	public SlayerRecommendation recommend(
		final String taskName,
		final int masterId,
		final String assignedLocation)
	{
		final String normalizedTask = normalize(taskName);

		if (normalizedTask.isEmpty())
		{
			return genericRecommendation(
				"Unknown task",
				masterId
			);
		}

		if (masterId == KONAR_MASTER_ID
			|| isAssignedArea(assignedLocation))
		{
			return assignedAreaRecommendation(
				normalizedTask,
				assignedLocation,
				masterId
			);
		}

		if (masterId == KRYSTILIA_MASTER_ID)
		{
			return wildernessRecommendation(taskName);
		}

		if (masterId == SlayerPointBoostCoordinator.TURAEL_AYA_MASTER_ID
			&& config != null
			&& config.slayerWorkflow()
				== SlayerPreference.Workflow.TURAEL_POINT_BOOST)
		{
			final SlayerRecommendation boost = turaelBoostRecommendation(taskName);
			if (boost != null)
			{
				return boost;
			}
		}

		final List<LocationOption> options =
			taskLocations.get(normalizedTask);

		if (options == null || options.isEmpty())
		{
			return genericRecommendation(
				taskName,
				masterId
			);
		}

		LocationOption selected = null;
		int selectedScore = Integer.MIN_VALUE;
		LocationOption lockedUpgrade = null;
		int lockedScore = Integer.MIN_VALUE;

		for (final LocationOption option : options)
		{
			if (option.wilderness)
			{
				continue;
			}

			final int score = scoreOption(option, normalizedTask, masterId);

			if (isUnlocked(option))
			{
				if (selected == null || score > selectedScore)
				{
					selected = option;
					selectedScore = score;
				}
			}
			else if (lockedUpgrade == null || score > lockedScore)
			{
				lockedUpgrade = option;
				lockedScore = score;
			}
		}

		if (selected == null)
		{
			return lockedRecommendation(
				taskName,
				lockedUpgrade
			);
		}

		final SlayerTaskStrategy strategy = strategyFor(
			normalizedTask,
			selected.location,
			false
		);

		return new SlayerRecommendation(
			selected.location,
			strategy.getMethod(),
			buildReason(selected, normalizedTask, selectedScore, masterId)
				+ " "
				+ strategy.getRationale(),
			selected.travel,
			formatCannon(selected.cannonSupport),
			formatRequirements(selected, lockedUpgrade),
			"Non-Wilderness recommendation",
			SlayerRouteCatalog.find(
				normalizedTask,
				selected.location
			),
			strategy
		);
	}

	private SlayerRecommendation turaelBoostRecommendation(
		final String taskName)
	{
		final SlayerTuraelBoostCatalog.Entry entry =
			SlayerTuraelBoostCatalog.find(taskName);
		if (entry == null)
		{
			return null;
		}

		final SlayerTaskStrategy strategy = strategyFor(
			taskName,
			entry.getLocation(),
			false
		).withAdditionalTags(
			SlayerTaskStrategy.MethodTag.TURAEL_POINT_BOOST
		);
		return new SlayerRecommendation(
			entry.getLocation(),
			strategy.getMethod(),
			"Turael/Aya point boosting uses the current Wiki point-farming "
				+ "location, the lowest practical monster variant, an expeditious "
				+ "bracelet when owned, and a cannon only when the selected area "
				+ "supports it. " + strategy.getRationale(),
			entry.getTravel(),
			entry.getCannon(),
			entry.getRequirements(),
			"Turael/Aya point-boost route",
			SlayerRouteCatalog.find(taskName, entry.getLocation()),
			strategy
		);
	}

	private SlayerRecommendation assignedAreaRecommendation(
		final String normalizedTask,
		final String assignedLocation,
		final int masterId)
	{
		final String area = isAssignedArea(assignedLocation)
			? assignedLocation.trim()
			: "Assigned Slayer area";

		if (masterId != KRYSTILIA_MASTER_ID
			&& isWilderness(area))
		{
			return new SlayerRecommendation(
				"No non-Wilderness match",
				"Check the assignment with your Slayer master",
				"Safety rule blocked a Wilderness route because Krystilia is not the selected master.",
				"Do not route into the Wilderness",
				"Not recommended",
				"Assigned-area data needs verification",
				"Wilderness blocked unless Krystilia assigned it"
			);
		}

		final SlayerTaskStrategy strategy = strategyFor(
			normalizedTask,
			area,
			masterId == KRYSTILIA_MASTER_ID
		);

		return new SlayerRecommendation(
			area,
			strategy.getMethod(),
			(masterId == KONAR_MASTER_ID
				? "Konar fixes the destination. Your combat preferences still shape the recommended method and cannon guidance."
				: "The assignment fixes the destination, so preferences are applied only to the method and cannon guidance.")
				+ " "
				+ strategy.getRationale(),
			travelForArea(area),
			formatCannon(cannonForArea(area)),
			"Use the monster inside the assigned area",
			masterId == KONAR_MASTER_ID
				? "Konar — assigned area required"
				: "Assigned area required",
			SlayerRouteCatalog.find(
				normalizedTask,
				area
			),
			strategy
		);
	}

	private SlayerRecommendation wildernessRecommendation(
		final String taskName)
	{
		final String normalized = normalize(taskName);
		final String location;

		if (normalized.equals("abyssal demons")
			|| normalized.equals("ankou")
			|| normalized.equals("black demons")
			|| normalized.equals("dust devils")
			|| normalized.equals("greater demons")
			|| normalized.equals("hellhounds"))
		{
			location = "Wilderness Slayer Cave";
		}
		else if (normalized.equals("bloodveld")
			|| normalized.equals("bloodvelds"))
		{
			location = "Wilderness God Wars Dungeon";
		}
		else
		{
			location = "Nearest matching Wilderness spawn";
		}

		final SlayerTaskStrategy strategy = strategyFor(
			taskName,
			location,
			true
		);

		return new SlayerRecommendation(
			location,
			strategy.getMethod(),
			"Krystilia overrides the normal Wilderness block. Safety and low carried risk take priority over the normal profile score. "
				+ strategy.getRationale(),
			"Start from Ferox Enclave and use the safest route",
			formatCannon(CannonSupport.OPTIONAL),
			"Krystilia assignment active",
			"Wilderness — protect-item planning required",
			SlayerRouteCatalog.find(location),
			strategy
		);
	}

	private SlayerRecommendation genericRecommendation(
		final String taskName,
		final int masterId)
	{
		final SlayerTaskTravelAuditCatalog.Entry auditedTravel =
			SlayerTaskTravelAuditCatalog.find(taskName);
		if (auditedTravel != null)
		{
			final SlayerTaskStrategy auditedStrategy = strategyFor(
				taskName,
				auditedTravel.getLocation(),
				masterId == KRYSTILIA_MASTER_ID
			);
			return new SlayerRecommendation(
				auditedTravel.getLocation(),
				auditedStrategy.getMethod(),
				"The task has a Wiki-reviewed fallback destination and loadout. "
					+ auditedStrategy.getRationale(),
				auditedTravel.getTravel(),
				auditedTravel.getCannon(),
				"Task-specific equipment and inventory audit active",
				masterId == KRYSTILIA_MASTER_ID
					? "Krystilia Wilderness task"
					: "Non-Wilderness recommendation",
				SlayerRouteCatalog.find(
					taskName,
					auditedTravel.getLocation()
				),
				auditedStrategy
			);
		}
		final SlayerTaskStrategy strategy = strategyFor(
			taskName,
			"Standard Slayer location",
			masterId == KRYSTILIA_MASTER_ID
		);

		return new SlayerRecommendation(
			"Standard Slayer location",
			strategy.getMethod(),
			(strategy.isReviewed()
				? "The task has a reviewed combat profile, but its preferred travel destination has not been added to the route catalog yet. "
				: "No generated loadout is available until this task is individually reviewed. ")
				+ strategy.getRationale(),
			"Use the nearest unlocked standard task location",
			"Check location rules",
			"Task-specific equipment profile active",
			masterId == KRYSTILIA_MASTER_ID
				? "Krystilia Wilderness task"
				: "Wilderness locations excluded",
			null,
			strategy
		);
	}


	private SlayerRecommendation lockedRecommendation(
		final String taskName,
		final LocationOption lockedOption)
	{
		if (lockedOption == null)
		{
			return genericRecommendation(taskName, 0);
		}

		final SlayerTaskStrategy strategy = strategyFor(
			taskName,
			lockedOption.location,
			false
		);

		return new SlayerRecommendation(
			"Location locked",
			strategy.getMethod(),
			"The highest-scoring catalog option is locked by an access requirement. "
				+ strategy.getRationale(),
			lockedOption.travel,
			formatCannon(lockedOption.cannonSupport),
			requirementName(lockedOption) + " required",
			"Wilderness locations excluded",
			null,
			strategy
		);
	}


	private boolean isUnlocked(final LocationOption option)
	{
		return (option.quest == null
			|| option.quest.getState(client) == QuestState.FINISHED)
			&& (option.skill == null
				|| client.getRealSkillLevel(option.skill) >= option.skillLevel);
	}

	private String formatRequirements(
		final LocationOption selected,
		final LocationOption lockedUpgrade)
	{
		final String selectedRequirement;
		if (selected.skill != null)
		{
			selectedRequirement = selected.skillLevel + " "
				+ selected.skill.getName() + " — met";
		}
		else
		{
			selectedRequirement = selected.quest == null
				? "None"
				: selected.questName + " — completed";
		}

		if (lockedUpgrade == null || lockedUpgrade == selected)
		{
			return selectedRequirement;
		}

		return selectedRequirement
			+ "; "
			+ requirementName(lockedUpgrade)
			+ " unlocks "
			+ lockedUpgrade.location;
	}

	private static String requirementName(final LocationOption option)
	{
		if (option == null)
		{
			return "Location requirement";
		}
		if (option.skill != null)
		{
			return option.skillLevel + " " + option.skill.getName();
		}
		return option.questName == null || option.questName.trim().isEmpty()
			? "Location requirement"
			: option.questName;
	}

	private int scoreOption(
		final LocationOption option,
		final String normalizedTask,
		final int masterId)
	{
		final int speed = speedRating(option);
		final int profit = profitRating(option);
		final int afk = afkRating(option);

		int score;
		switch (playstyle())
		{
			case PROFIT:
				score = profit * 4 + speed + afk / 4;
				break;
			case FAST_XP:
			default:
				/*
				 * Fast XP is intentionally single-purpose. Profit and supply
				 * cost do not influence the base location score.
				 */
				score = speed * 5;
				break;
		}

		final boolean cannonAvailable =
			option.cannonSupport == CannonSupport.RECOMMENDED
				|| option.cannonSupport == CannonSupport.OPTIONAL;
		final boolean cannonUnlocked =
			Quest.DWARF_CANNON.getState(client)
				== QuestState.FINISHED;

		switch (cannonPreference())
		{
			case PREFER:
				if (cannonAvailable && cannonUnlocked)
				{
					score += option.cannonSupport
						== CannonSupport.RECOMMENDED
							? 70
							: 38;
				}
				else
				{
					score -= 18;
				}
				break;
			case NEVER:
				if (option.cannonSupport == CannonSupport.RECOMMENDED)
				{
					/* A location whose reviewed method depends on cannon should lose. */
					score -= 130;
				}
				else if (option.cannonSupport == CannonSupport.OPTIONAL)
				{
					/* Optional means fully viable without a cannon; do not punish the location. */
					score += 6;
				}
				else
				{
					score += 18;
				}
				break;
			case ALLOW:
			default:
				if (cannonAvailable && cannonUnlocked)
				{
					score += 12;
				}
				break;
		}

		final boolean burstAvailable = supportsBurst(option);
		switch (burstPreference())
		{
			case PREFER:
				score += burstAvailable ? 72 : -16;
				break;
			case NEVER:
				score += burstAvailable ? -130 : 18;
				break;
			case ALLOW:
			default:
				if (burstAvailable)
				{
					score += 12;
				}
				break;
		}

		score += travelScore(option.travel);
		/*
		 * A shard preference is an explicit destination choice, not a small
		 * efficiency tie-breaker. Apply it after cannon, barrage, and travel so a
		 * valid shard-producing area can intentionally override those preferences.
		 * Konar/assigned-area and Krystilia branches return before this scoring and
		 * therefore remain authoritative.
		 */
		score += shardPreferenceBonusForRegression(
			shardPreference(), normalizedTask, option.location
		);
		return score;
	}

	private int speedRating(final LocationOption option)
	{
		final String text = normalize(
			option.location + " " + option.method
		);
		int rating = 42;

		if (text.contains("burst")
			|| text.contains("barrage"))
		{
			rating += 42;
		}
		if (text.contains("cannon"))
		{
			rating += 32;
		}
		if (text.contains("multi combat")
			|| text.contains("clustered")
			|| text.contains("stacked"))
		{
			rating += 14;
		}
		if (text.contains("single combat"))
		{
			rating -= 14;
		}

		return clampRating(rating);
	}

	private int profitRating(final LocationOption option)
	{
		final String text = normalize(
			option.location + " " + option.method
		);
		int rating = 34;

		if (text.contains("higher value")
			|| text.contains("basilisk knight"))
		{
			rating += 48;
		}
		if (text.contains("lithkren")
			|| text.contains("kraken")
			|| text.contains("gargoyle")
			|| text.contains("skeletal wyvern"))
		{
			rating += 35;
		}
		if (text.contains("darkmeyer")
			|| text.contains("cave horror")
			|| text.contains("abyssal"))
		{
			rating += 24;
		}
		if (text.contains("ancient shard")
			|| text.contains("totem"))
		{
			rating += 18;
		}

		return clampRating(rating);
	}

	private int afkRating(final LocationOption option)
	{
		final String text = normalize(option.method);
		int rating = 45;

		if (text.contains("single combat")
			|| text.contains("melee"))
		{
			rating += 24;
		}
		if (text.contains("ranged"))
		{
			rating += 12;
		}
		if (text.contains("cannon"))
		{
			rating += 12;
		}
		if (text.contains("burst")
			|| text.contains("barrage")
			|| text.contains("stacked"))
		{
			rating -= 26;
		}
		if (text.contains("finish each kill")
			|| text.contains("witchwood")
			|| text.contains("boots of stone"))
		{
			rating -= 18;
		}

		return clampRating(rating);
	}

	private int travelScore(final String route)
	{
		final String normalized = normalize(route);
		final boolean directTeleport =
			normalized.contains("teleport")
				|| normalized.contains("slayer ring")
				|| normalized.contains("xeric")
				|| normalized.contains("rada")
				|| normalized.contains("drakan")
				|| normalized.contains("digsite pendant")
				|| normalized.contains("fairy ring");
		final boolean freeOrReusable =
			normalized.contains("minigame teleport")
				|| normalized.contains("fairy ring")
				|| normalized.contains("spirit tree")
				|| normalized.contains("run from")
				|| normalized.contains("then run");
		final boolean likelyConsumable =
			normalized.contains("games necklace")
				|| normalized.contains("talisman")
				|| normalized.contains("slayer ring")
				|| normalized.contains("digsite pendant")
				|| normalized.contains("teleport crystal")
				|| normalized.contains("house teleport");

		switch (travelPreference())
		{
			case CHEAPEST:
				if (freeOrReusable)
				{
					return 52;
				}
				return likelyConsumable ? -20 : 18;
			case AVOID_CONSUMABLES:
				return likelyConsumable ? -70 : 42;
			case FASTEST:
			default:
				return directTeleport ? 45 : 8;
		}
	}

	private String buildReason(
		final LocationOption option,
		final String normalizedTask,
		final int score,
		final int masterId)
	{
		final StringBuilder reason = new StringBuilder();
		reason.append(playstyle())
			.append(" profile selected this unlocked option");

		switch (playstyle())
		{
			case PROFIT:
				reason.append(" for its stronger loot and supply-efficiency potential");
				break;
			case FAST_XP:
			default:
				reason.append(
					" for maximum kill-speed potential without a gear-cost penalty"
				);
				break;
		}

		if (cannonPreference() == SlayerPreference.Cannon.PREFER
			&& option.cannonSupport != CannonSupport.NOT_ALLOWED)
		{
			reason.append("; cannon preference increased its score");
		}
		else if (cannonPreference() == SlayerPreference.Cannon.NEVER
			&& option.cannonSupport == CannonSupport.NOT_ALLOWED)
		{
			reason.append("; it naturally avoids cannon use");
		}

		if (burstPreference() == SlayerPreference.Burst.PREFER
			&& supportsBurst(option))
		{
			reason.append("; burst/barrage preference increased its score");
		}
		else if (burstPreference() == SlayerPreference.Burst.NEVER
			&& !supportsBurst(option))
		{
			reason.append("; it avoids burst/barrage");
		}

		if (shardPreferenceBonusForRegression(
			shardPreference(), normalizedTask, option.location
		) > 0)
		{
			reason.append("; shard preference selected a valid shard-producing area");
		}

		reason.append(". Travel was ranked by ")
			.append(travelPreference().toString().toLowerCase(Locale.ENGLISH))
			.append(". Score: ")
			.append(score)
			.append('.');

		return reason.toString();
	}

	private String formatMethod(final LocationOption option)
	{
		final boolean hasBurst = supportsBurst(option);
		final boolean hasCannon =
			normalize(option.method).contains("cannon");

		if (burstPreference() == SlayerPreference.Burst.NEVER
			&& cannonPreference() == SlayerPreference.Cannon.NEVER
			&& hasBurst
			&& hasCannon)
		{
			return "Use melee or ranged; cannon and burst/barrage are disabled by preference.";
		}

		if (burstPreference() == SlayerPreference.Burst.NEVER
			&& hasBurst)
		{
			return option.cannonSupport == CannonSupport.RECOMMENDED
				&& cannonPreference() != SlayerPreference.Cannon.NEVER
					? "Use melee or ranged with a cannon where allowed; burst/barrage is disabled by preference."
					: "Use melee or ranged; burst/barrage is disabled by preference.";
		}

		if (cannonPreference() == SlayerPreference.Cannon.NEVER
			&& hasCannon)
		{
			return hasBurst
				? "Burst or barrage without a cannon."
				: "Use the strongest non-cannon setup available here.";
		}

		if (burstPreference() == SlayerPreference.Burst.PREFER
			&& hasBurst)
		{
			return option.method + " — preferred by profile";
		}

		return option.method;
	}

	private static boolean supportsBurst(
		final LocationOption option)
	{
		final String method = normalize(option.method);
		return method.contains("burst")
			|| method.contains("barrage");
	}

	private static int clampRating(final int value)
	{
		return Math.max(0, Math.min(100, value));
	}

	private SlayerPreference.Playstyle playstyle()
	{
		return config == null || config.playstyle() == null
			? SlayerPreference.Playstyle.FAST_XP
			: config.playstyle();
	}

	private SlayerPreference.CombatStyle combatStylePreference()
	{
		return config == null || config.combatStylePreference() == null
			? SlayerPreference.CombatStyle.AUTOMATIC
			: config.combatStylePreference();
	}

	private SlayerPreference.Cannon cannonPreference()
	{
		return config == null || config.cannonPreference() == null
			? SlayerPreference.Cannon.ALLOW
			: config.cannonPreference();
	}

	private SlayerPreference.Burst burstPreference()
	{
		return config == null || config.burstPreference() == null
			? SlayerPreference.Burst.ALLOW
			: config.burstPreference();
	}

	private SlayerPreference.Travel travelPreference()
	{
		return config == null || config.travelPreference() == null
			? SlayerPreference.Travel.FASTEST
			: config.travelPreference();
	}

	private SlayerPreference.Shard shardPreference()
	{
		return config == null || config.shardPreference() == null
			? SlayerPreference.Shard.NO_PREFERENCE
			: config.shardPreference();
	}

	static int shardPreferenceBonusForRegression(
		final SlayerPreference.Shard preference,
		final String taskName,
		final String location)
	{
		if (preference == null
			|| preference == SlayerPreference.Shard.NO_PREFERENCE)
		{
			return 0;
		}

		final String task = normalize(taskName);
		final String area = normalize(location);
		final boolean ancientShardArea = !task.equals("ghost")
			&& !task.equals("ghosts")
			&& (area.contains("catacombs of kourend")
				|| area.contains("giants den"));
		final boolean crystalShardArea = area.contains("iorwerth dungeon");

		switch (preference)
		{
			case ANCIENT_SHARD:
				return ancientShardArea ? 1000 : 0;
			case CRYSTAL_SHARD:
				return crystalShardArea ? 1000 : 0;
			case ANCIENT_AND_CRYSTAL_SHARD:
				return ancientShardArea || crystalShardArea ? 1000 : 0;
			case NO_PREFERENCE:
			default:
				return 0;
		}
	}

	private String formatCannon(
		final CannonSupport support)
	{
		if (support == CannonSupport.NOT_ALLOWED)
		{
			return "Not allowed";
		}

		if (support == CannonSupport.UNKNOWN)
		{
			return cannonPreference() == SlayerPreference.Cannon.NEVER
				? "Disabled by preference"
				: "Check assigned-area rules";
		}

		if (cannonPreference() == SlayerPreference.Cannon.NEVER)
		{
			return "Disabled by preference";
		}

		if (Quest.DWARF_CANNON.getState(client)
			!= QuestState.FINISHED)
		{
			return "Locked — complete Dwarf Cannon";
		}

		if (cannonPreference() == SlayerPreference.Cannon.PREFER)
		{
			return support == CannonSupport.RECOMMENDED
				? "Preferred and recommended"
				: "Preferred where practical";
		}

		return support == CannonSupport.RECOMMENDED
			? "Recommended"
			: "Optional";
	}

	private SlayerTaskStrategy strategyFor(
		final String taskName,
		final String location,
		final boolean wilderness)
	{
		return SlayerTaskStrategyCatalog.resolve(
			taskName,
			playstyle(),
			cannonPreference(),
			burstPreference(),
			combatStylePreference(),
			location,
			wilderness
		);
	}

	private String methodForTask(final String normalizedTask)
	{
		return strategyFor(
			normalizedTask,
			"Assigned Slayer area",
			false
		).getMethod();
	}


	private static String travelForArea(final String area)
	{
		final String normalized = normalize(area);

		if (normalized.contains("catacombs of kourend"))
		{
			return "Xeric's talisman to Xeric's Heart";
		}
		if (normalized.contains("slayer tower"))
		{
			return "Slayer ring to Slayer Tower";
		}
		if (normalized.contains("fremennik slayer dungeon"))
		{
			return "Slayer ring to Fremennik Slayer Dungeon";
		}
		if (normalized.contains("stronghold slayer cave"))
		{
			return "Slayer ring to Stronghold Slayer Cave";
		}
		if (normalized.contains("karuulm"))
		{
			return "Rada's blessing to Mount Karuulm";
		}
		if (normalized.contains("lighthouse"))
		{
			return "Games necklace to Barbarian Outpost, then run north";
		}
		if (normalized.contains("lunar isle"))
		{
			return "Lunar Isle teleport";
		}
		if (normalized.contains("meiyerditch"))
		{
			return "Drakan's medallion to Darkmeyer, then enter the laboratories";
		}
		if (normalized.contains("mos le harmless"))
		{
			return "Trouble Brewing minigame teleport, then run east";
		}
		if (normalized.contains("wilderness god wars"))
		{
			return "Cemetery teleport or Wilderness obelisk, then enter the Wilderness God Wars Dungeon";
		}
		if (normalized.contains("god wars"))
		{
			return "Trollheim teleport, then enter God Wars Dungeon";
		}
		if (normalized.contains("brimhaven dungeon"))
		{
			return "House teleport to Brimhaven, then run south";
		}
		if (normalized.contains("taverley dungeon"))
		{
			return "Falador teleport, then run northwest";
		}

		return "Use the nearest unlocked teleport to the assigned area";
	}

	private static CannonSupport cannonForArea(final String area)
	{
		final String normalized = normalize(area);

		if (normalized.contains("catacombs")
			|| normalized.contains("slayer tower")
			|| normalized.contains("fremennik slayer dungeon")
			|| normalized.contains("karuulm")
			|| normalized.contains("kraken")
			|| normalized.contains("god wars")
			|| normalized.contains("mos le harmless"))
		{
			return CannonSupport.NOT_ALLOWED;
		}

		if (normalized.contains("lighthouse")
			|| normalized.contains("meiyerditch")
			|| normalized.contains("stronghold slayer cave")
			|| normalized.contains("iorwerth dungeon")
			|| normalized.contains("lunar isle")
			|| normalized.contains("kalphite slayer cave")
			|| normalized.contains("death plateau")
			|| normalized.contains("smoke devil dungeon"))
		{
			return CannonSupport.RECOMMENDED;
		}

		return CannonSupport.UNKNOWN;
	}

	private static boolean isAssignedArea(final String area)
	{
		if (area == null)
		{
			return false;
		}

		final String normalized = normalize(area);
		return !normalized.isEmpty()
			&& !normalized.equals("not restricted")
			&& !normalized.equals("assigned area")
			&& !normalized.equals("unknown")
			&& !normalized.equals("-");
	}

	private static boolean isWilderness(final String value)
	{
		final String normalized = normalize(value);
		return normalized.contains("wilderness")
			|| normalized.contains("revenant caves")
			|| normalized.contains("forinthry");
	}

	private static Map<String, List<LocationOption>> createTaskLocations()
	{
		final Map<String, List<LocationOption>> map = new HashMap<>();

		register(map,
			aliases("abyssal demon", "abyssal demons"),
			option(
				"Catacombs of Kourend",
				"Multi-combat melee or burst/barrage",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			),
			option(
				"Slayer Tower",
				"Single-combat melee",
				"Slayer ring to Slayer Tower",
				CannonSupport.NOT_ALLOWED,
				Quest.PRIEST_IN_PERIL,
				"Priest in Peril"
			)
		);

		register(map,
			aliases("bloodveld", "bloodvelds"),
			option(
				"Meiyerditch Laboratories",
				"Cannon mutated bloodvelds in multi-combat",
				"Drakan's medallion to Darkmeyer, then enter the laboratories",
				CannonSupport.RECOMMENDED,
				Quest.SINS_OF_THE_FATHER,
				"Sins of the Father"
			),
			option(
				"Buccaneers' Laboratory",
				"Cannon mutated Bloodvelds in unobstructed multi-combat",
				"Pirates' Cove, then the built rowboat to Buccaneers' Haven",
				CannonSupport.RECOMMENDED,
				Skill.SAILING,
				76
			),
			option(
				"Iorwerth Dungeon",
				"Cannon mutated Bloodvelds in a single-combat crystal-shard area",
				"Teleport crystal to Prifddinas, then enter Iorwerth Dungeon",
				CannonSupport.OPTIONAL,
				Quest.SONG_OF_THE_ELVES,
				"Song of the Elves"
			),
			option(
				"Stronghold Slayer Cave",
				"Safespot regular Bloodvelds with a dwarf multicannon",
				"Slayer ring to Stronghold Slayer Cave",
				CannonSupport.RECOMMENDED
			),
			option(
				"Catacombs of Kourend",
				"Venator bow with goading potion or prayer melee in multi-combat",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			),
			option(
				"Slayer Tower (first floor)",
				"Safespot regular Bloodvelds in single-combat",
				"Slayer ring to Slayer Tower",
				CannonSupport.NOT_ALLOWED,
				Quest.PRIEST_IN_PERIL,
				"Priest in Peril"
			),
			option(
				"Slayer Tower basement",
				"Safespot task-only regular Bloodvelds in single-combat",
				"Slayer ring to Slayer Tower, then use the entrance ladder",
				CannonSupport.NOT_ALLOWED,
				Quest.PRIEST_IN_PERIL,
				"Priest in Peril"
			),
			option(
				"God Wars Dungeon",
				"Safespot Zamorak Bloodvelds with Saradomin and Zamorak protection",
				"Trollheim teleport, then enter God Wars Dungeon",
				CannonSupport.NOT_ALLOWED,
				Quest.DEATH_PLATEAU,
				"Death Plateau"
			)
		);

		register(map,
			aliases("dust devil", "dust devils"),
			option(
				"Catacombs of Kourend",
				"Burst or barrage stacked dust devils",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("nechryael", "nechryaels"),
			option(
				"Catacombs of Kourend",
				"Burst or barrage stacked nechryaels",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			),
			option(
				"Iorwerth Dungeon",
				"Single-combat Greater Nechryaels with optional cannon support for crystal shard drops",
				"Teleport crystal to Prifddinas, then enter Iorwerth Dungeon",
				CannonSupport.OPTIONAL,
				Quest.SONG_OF_THE_ELVES,
				"Song of the Elves"
			)
		);

		register(map,
			aliases("smoke devil", "smoke devils"),
			option(
				"Smoke Devil Dungeon",
				"Cannon while bursting or barraging",
				"Use the nearest unlocked teleport to the dungeon entrance",
				CannonSupport.RECOMMENDED
			)
		);

		register(map,
			aliases("gargoyle", "gargoyles"),
			option(
				"Slayer Tower",
				"Melee with a rock hammer or automatic finisher",
				"Slayer ring to Slayer Tower",
				CannonSupport.NOT_ALLOWED,
				Quest.PRIEST_IN_PERIL,
				"Priest in Peril"
			)
		);

		register(map,
			aliases("kurask", "kurasks"),
			option(
				"Iorwerth Dungeon",
				"Leaf-bladed weapon, broad ammunition, or Magic Dart",
				"Teleport crystal to Prifddinas, then enter Iorwerth Dungeon",
				CannonSupport.NOT_ALLOWED,
				Quest.SONG_OF_THE_ELVES,
				"Song of the Elves"
			),
			option(
				"Fremennik Slayer Dungeon",
				"Leaf-bladed weapon, broad ammunition, or Magic Dart",
				"Slayer ring to Fremennik Slayer Dungeon",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("turoth", "turoths"),
			option(
				"Fremennik Slayer Dungeon",
				"Leaf-bladed weapon, broad ammunition, or Magic Dart",
				"Slayer ring to Fremennik Slayer Dungeon",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("cave kraken", "kraken"),
			option(
				"Kraken Cove",
				"Magic against cave kraken",
				"Use the nearest unlocked teleport to Kraken Cove",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("cave horror", "cave horrors"),
			option(
				"Mos Le'Harmless Cave",
				"Fast ranged attacks with a witchwood icon and dwarf multicannon",
				"Trouble Brewing minigame teleport, then run east",
				CannonSupport.RECOMMENDED,
				Quest.CABIN_FEVER,
				"Cabin Fever"
			)
		);

		register(map,
			aliases("dagannoth", "dagannoths"),
			option(
				"Lighthouse Dungeon",
				"Cannon in multi-combat",
				"Games necklace to Barbarian Outpost, then run north",
				CannonSupport.RECOMMENDED,
				Quest.HORROR_FROM_THE_DEEP,
				"Horror from the Deep"
			),
			option(
				"Jormungand's Prison",
				"Prayer melee against melee-only Dagannoth",
				"Rellekka teleport, then travel to the prison",
				CannonSupport.NOT_ALLOWED,
				Quest.THE_FREMENNIK_EXILES,
				"The Fremennik Exiles"
			),
			option(
				"Waterbirth Island Dungeon",
				"Multi-combat ranged or melee",
				"Waterbirth teleport or travel from Rellekka",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("kalphite", "kalphites"),
			option(
				"Kalphite Slayer Cave",
				"Cannon in the task-only cave",
				"Use the nearest unlocked Desert teleport",
				CannonSupport.RECOMMENDED
			)
		);

		register(map,
			aliases("suqah", "suqahs"),
			option(
				"Lunar Isle",
				"Cannon the clustered suqahs",
				"Lunar Isle teleport",
				CannonSupport.RECOMMENDED,
				Quest.LUNAR_DIPLOMACY,
				"Lunar Diplomacy"
			)
		);

		register(map,
			aliases("troll", "trolls"),
			option(
				"Death Plateau",
				"Cannon the dense troll spawns",
				"Games necklace to Burthorpe, then run north",
				CannonSupport.RECOMMENDED,
				Quest.DEATH_PLATEAU,
				"Death Plateau"
			)
		);

		register(map,
			aliases("wyrm", "wyrms"),
			option(
				"Karuulm Slayer Dungeon",
				"Ranged or melee with boots of stone protection",
				"Rada's blessing to Mount Karuulm",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("drake", "drakes"),
			option(
				"Karuulm Slayer Dungeon",
				"Ranged or melee with boots of stone protection",
				"Rada's blessing to Mount Karuulm",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("hydra", "hydras"),
			option(
				"Karuulm Slayer Dungeon",
				"Ranged or melee in the hydra area",
				"Rada's blessing to Mount Karuulm",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("hellhound", "hellhounds"),
			option(
				"Stronghold Slayer Cave",
				"Cannon with Toxic blowpipe or Scorching bow from the safespot",
				"Slayer ring to Stronghold Slayer Cave",
				CannonSupport.RECOMMENDED
			),
			option(
				"Catacombs of Kourend",
				"Venator bow in multi-combat; demonbane melee or Water spells if preferred",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("fire giant", "fire giants"),
			option(
				"Catacombs of Kourend",
				"Melee or ranged in multi-combat",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("greater demon", "greater demons"),
			option(
				"Chasm of Fire",
				"Cannon or melee in the lower levels",
				"Use the nearest unlocked Kourend teleport",
				CannonSupport.RECOMMENDED
			),
			option(
				"Catacombs of Kourend",
				"Melee in multi-combat",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("black demon", "black demons"),
			option(
				"Catacombs of Kourend",
				"Melee or ranged in multi-combat",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("aberrant spectre", "aberrant spectres"),
			option(
				"Stronghold Slayer Cave",
				"Cannon with Protect from Magic and required face protection",
				"Slayer ring to Stronghold Slayer Cave",
				CannonSupport.RECOMMENDED
			),
			option(
				"Catacombs of Kourend",
				"Venator bow or prayer melee against Deviant spectres",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			),
			option(
				"Slayer Tower",
				"Protect from Magic with a nose peg or Slayer helmet",
				"Slayer ring to Slayer Tower",
				CannonSupport.NOT_ALLOWED,
				Quest.PRIEST_IN_PERIL,
				"Priest in Peril"
			)
		);

		register(map,
			aliases("ankou"),
			option(
				"Catacombs of Kourend",
				"Use a Venator bow against the tightly packed 1x1 Ankous in multi-combat",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			),
			option(
				"Catacombs of Kourend",
				"Burst or barrage stacked Ankous in multi-combat",
				"Xeric's talisman to Xeric's Heart",
				CannonSupport.NOT_ALLOWED
			),
			option(
				"Stronghold Slayer Cave",
				"Cannon with a Toxic blowpipe in the single-combat room",
				"Slayer ring to Stronghold Slayer Cave",
				CannonSupport.RECOMMENDED
			),
			option(
				"Stronghold of Security",
				"Safespot with a Toxic blowpipe or another fast ranged weapon",
				"Skull sceptre to the Stronghold of Security",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("blue dragon", "blue dragons"),
			option(
				"Taverley Dungeon",
				"Ranged with an anti-dragon shield or antifire protection",
				"Falador teleport, then run northwest",
				CannonSupport.OPTIONAL
			)
		);

		register(map,
			aliases(
				"bronze dragon", "bronze dragons",
				"iron dragon", "iron dragons",
				"steel dragon", "steel dragons"
			),
			option(
				"Brimhaven Dungeon",
				"Use a dragonbane weapon or Earth Magic with complete dragonfire protection",
				"Fairy ring CKR, then run north to Brimhaven Dungeon",
				CannonSupport.OPTIONAL
			)
		);

		register(map,
			aliases("mithril dragon", "mithril dragons"),
			option(
				"Ancient Cavern",
				"Use a dragonbane weapon or Earth Magic with complete dragonfire protection",
				"Barbarian teleport, then enter the whirlpool",
				CannonSupport.NOT_ALLOWED,
				Quest.BARBARIAN_TRAINING,
				"Barbarian Training"
			)
		);

		register(map,
			aliases("minions of scabaras", "scabarite", "scabarites"),
			option(
				"Sophanem Dungeon",
				"Use the reviewed cannon or non-cannon Scabarite method",
				"Pharaoh's sceptre to Sophanem, then enter the dungeon",
				CannonSupport.OPTIONAL
			)
		);

		register(map,
			aliases("skeletal wyvern", "skeletal wyverns"),
			option(
				"Asgarnian Ice Dungeon",
				"Ranged with an elemental or dragonfire shield",
				"Falador teleport, then run south",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("waterfiend", "waterfiends"),
			option(
				"Ancient Cavern",
				"Crush attacks with prayer or food",
				"Barbarian teleport, then enter the whirlpool",
				CannonSupport.NOT_ALLOWED,
				Quest.BARBARIAN_TRAINING,
				"Barbarian Training"
			),
			option(
				"Iorwerth Dungeon",
				"Use Earth spells with optional cannon support for crystal shard drops",
				"Teleport crystal to Prifddinas, then enter Iorwerth Dungeon",
				CannonSupport.OPTIONAL,
				Quest.SONG_OF_THE_ELVES,
				"Song of the Elves"
			)
		);

		register(map,
			aliases("spiritual creature", "spiritual creatures"),
			option(
				"God Wars Dungeon",
				"Kill the highest unlocked spiritual creature",
				"Trollheim teleport, then enter God Wars Dungeon",
				CannonSupport.NOT_ALLOWED,
				Quest.TROLL_STRONGHOLD,
				"Troll Stronghold"
			)
		);

		register(map,
			aliases("fossil island wyvern", "fossil island wyverns"),
			option(
				"Wyvern Cave on Fossil Island",
				"Use an elemental or dragonfire shield",
				"Digsite pendant to Fossil Island",
				CannonSupport.NOT_ALLOWED,
				Quest.BONE_VOYAGE,
				"Bone Voyage"
			)
		);

		register(map,
			aliases("vampyre", "vampyres"),
			option(
				"Darkmeyer",
				"Use an Ivandis or blisterwood weapon",
				"Drakan's medallion to Darkmeyer",
				CannonSupport.NOT_ALLOWED,
				Quest.SINS_OF_THE_FATHER,
				"Sins of the Father"
			)
		);

		register(map,
			aliases("basilisk", "basilisks"),
			option(
				"Jormungand's Prison",
				"Basilisk Knights for the higher-value option",
				"Fremennik sea boots or Rellekka teleport, then run north",
				CannonSupport.NOT_ALLOWED,
				Quest.THE_FREMENNIK_EXILES,
				"The Fremennik Exiles"
			),
			option(
				"Fremennik Slayer Dungeon",
				"Use a mirror shield against regular basilisks",
				"Slayer ring to Fremennik Slayer Dungeon",
				CannonSupport.NOT_ALLOWED
			)
		);

		register(map,
			aliases("elf", "elves"),
			option(
				"Iorwerth Dungeon",
				"Melee or ranged against Iorwerth elves",
				"Teleport crystal to Prifddinas",
				CannonSupport.NOT_ALLOWED,
				Quest.SONG_OF_THE_ELVES,
				"Song of the Elves"
			),
			option(
				"Lletya",
				"Kill the elves around Lletya",
				"Teleport crystal to Lletya",
				CannonSupport.NOT_ALLOWED,
				Quest.REGICIDE,
				"Regicide"
			)
		);

		register(map,
			aliases("dark beast", "dark beasts"),
			option(
				"Mourner Tunnels",
				"Dense melee task with optional cannon support and a direct Slayer-ring route",
				"Slayer ring to Dark Beasts",
				CannonSupport.OPTIONAL,
				Quest.MOURNINGS_END_PART_II,
				"Mourning's End Part II"
			),
			option(
				"Iorwerth Dungeon",
				"Aggressive melee with optional cannon support and higher-value crystal shard drops",
				"Teleport crystal to Prifddinas, then enter Iorwerth Dungeon",
				CannonSupport.OPTIONAL,
				Quest.SONG_OF_THE_ELVES,
				"Song of the Elves"
			)
		);

		register(map,
			aliases("mutated zygomite", "mutated zygomites"),
			option(
				"Zanaris",
				"Use fungicide spray to finish each kill",
				"Fairy ring to Zanaris",
				CannonSupport.NOT_ALLOWED,
				Quest.LOST_CITY,
				"Lost City"
			)
		);

		register(map,
			aliases("brine rat", "brine rats"),
			option(
				"Brine Rat Cavern",
				"Melee or ranged in the cavern",
				"Rellekka teleport, then run northeast",
				CannonSupport.NOT_ALLOWED,
				Quest.OLAFS_QUEST,
				"Olaf's Quest"
			)
		);

		register(map,
			aliases("adamant dragon", "adamant dragons"),
			option(
				"Lithkren Vault",
				"Ranged with strong dragonfire protection",
				"Digsite pendant to Lithkren",
				CannonSupport.NOT_ALLOWED,
				Quest.DRAGON_SLAYER_II,
				"Dragon Slayer II"
			)
		);

		register(map,
			aliases("rune dragon", "rune dragons"),
			option(
				"Lithkren Vault",
				"Ranged or melee with strong dragonfire protection",
				"Digsite pendant to Lithkren",
				CannonSupport.NOT_ALLOWED,
				Quest.DRAGON_SLAYER_II,
				"Dragon Slayer II"
			)
		);

		final Map<String, List<LocationOption>> immutable =
			new HashMap<>();
		for (final Map.Entry<String, List<LocationOption>> entry
			: map.entrySet())
		{
			immutable.put(
				entry.getKey(),
				Collections.unmodifiableList(
					new ArrayList<>(entry.getValue())
				)
			);
		}

		return Collections.unmodifiableMap(immutable);
	}

	private static void register(
		final Map<String, List<LocationOption>> map,
		final List<String> aliases,
		final LocationOption... options)
	{
		final List<LocationOption> values =
			Arrays.asList(options);

		for (final String alias : aliases)
		{
			map.put(normalize(alias), values);
		}
	}

	private static List<String> aliases(
		final String... values)
	{
		return Arrays.asList(values);
	}

	private static LocationOption option(
		final String location,
		final String method,
		final String travel,
		final CannonSupport cannonSupport)
	{
		return new LocationOption(
			location,
			method,
			travel,
			cannonSupport,
			null,
			"",
			null,
			0,
			false
		);
	}

	private static LocationOption option(
		final String location,
		final String method,
		final String travel,
		final CannonSupport cannonSupport,
		final Quest quest,
		final String questName)
	{
		return new LocationOption(
			location,
			method,
			travel,
			cannonSupport,
			quest,
			questName,
			null,
			0,
			false
		);
	}

	private static LocationOption option(
		final String location,
		final String method,
		final String travel,
		final CannonSupport cannonSupport,
		final Skill skill,
		final int skillLevel)
	{
		return new LocationOption(
			location,
			method,
			travel,
			cannonSupport,
			null,
			"",
			skill,
			skillLevel,
			false
		);
	}

	private static String normalize(final String value)
	{
		if (value == null)
		{
			return "";
		}

		return value
			.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
	}

	private enum CannonSupport
	{
		RECOMMENDED,
		OPTIONAL,
		NOT_ALLOWED,
		UNKNOWN
	}

	private static final class LocationOption
	{
		private final String location;
		private final String method;
		private final String travel;
		private final CannonSupport cannonSupport;
		private final Quest quest;
		private final String questName;
		private final Skill skill;
		private final int skillLevel;
		private final boolean wilderness;

		private LocationOption(
			final String location,
			final String method,
			final String travel,
			final CannonSupport cannonSupport,
			final Quest quest,
			final String questName,
			final Skill skill,
			final int skillLevel,
			final boolean wilderness)
		{
			this.location = location;
			this.method = method;
			this.travel = travel;
			this.cannonSupport = cannonSupport;
			this.quest = quest;
			this.questName = questName;
			this.skill = skill;
			this.skillLevel = Math.max(0, skillLevel);
			this.wilderness = wilderness;
		}
	}
}
