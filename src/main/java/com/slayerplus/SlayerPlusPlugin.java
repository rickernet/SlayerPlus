package com.slayerplus;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.WorldView;
import net.runelite.api.kit.KitType;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "SlayerPlus",
	description = "Plan Slayer tasks, loadouts, locations, and routes.",
	tags = {"slayer", "gear", "loadout", "route"}
)
@PluginDependency(BankTagsPlugin.class)
@PluginDependency(SlayerPlugin.class)
public class SlayerPlusPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(
		SlayerPlusPlugin.class
	);
	private static final int KRYSTILIA_MASTER_ID = 7;
	private static final int KONAR_MASTER_ID = 8;
	private static final String RUNELITE_SLAYER_CONFIG_GROUP = "slayer";
	private static final String RUNELITE_SLAYER_TASK_NAME_KEY = "taskName";
	private static final String RUNELITE_SLAYER_AMOUNT_KEY = "amount";
	private static final String RUNELITE_SLAYER_INITIAL_AMOUNT_KEY =
		"initialAmount";
	private static final String RUNELITE_SLAYER_LOCATION_KEY = "taskLocation";
	private static final String RUNELITE_SLAYER_STREAK_KEY = "streak";
	private static final String RUNELITE_SLAYER_POINTS_KEY = "points";
	private static final String LAST_SLAYER_MASTER_SNAPSHOT_KEY =
		"lastSlayerMasterIdV1";
	private static final String PORTRAIT_CALIBRATION_KEY =
		"portraitCalibrationV1";
	private static final String PLAYSTYLE_KEY = "playstyle";
	private static final String EASY_TELEPORTS_CONFIG_GROUP =
		"easypharaohsceptre";
	private static final String ROUTE_CHECKPOINT_PREFIX =
		"routeCheckpointV3.r" + SlayerRouteCatalog.CATALOG_REVISION + ".";
	/*
	 * Account-scoped last confirmed bank ownership. A value of zero means a real
	 * bank scan confirmed that no supported Ghommal item was present; absence of
	 * the key allows a one-time migration from the existing SlayerPlus layout.
	 */
	private static final String INFERNO_TRAVEL_SNAPSHOT_KEY =
		"infernoTravelItemSnapshotV1";
	private static final String OWNED_SLAYER_HELMETS_SNAPSHOT_KEY =
		"ownedSlayerHelmetItemIdsV1";
	private static final int LEARNED_ROUTE_USE_RADIUS = 256;
	private static final int TASK_ARRIVAL_RADIUS = 14;
	private static final int MASTER_ARRIVAL_RADIUS = 12;
	private static final int TASK_EXIT_RADIUS = 48;
	private static final int TASK_EXIT_CONFIRM_TICKS = 5;
	private static final int TASK_ARRIVAL_GRACE_TICKS = 60;
	/*
	 * The scroll lands outside the cave, but the exact arrival square can vary
	 * slightly as the scene loads. Keep this surface-only window broad enough to
	 * recognize the reviewed cave area without ever matching a remote bank.
	 */
	private static final int ARAXXOR_SPIDER_TELEPORT_LANDING_RADIUS = 64;
	private static final int DYNAMIC_TASK_NPC_RADIUS = 48;
	private static final int DYNAMIC_TASK_NPC_SEARCH_TILE_LIMIT = 4_096;
	private static final int DYNAMIC_BANK_NPC_RADIUS = 48;
	private static final int DYNAMIC_BANK_OBJECT_RADIUS = 48;
	private static final int TRAVEL_BANK_SOURCE_SCORE = 2000;
	private static final int TRAVEL_INVENTORY_SOURCE_SCORE = 3000;
	private static final int TRAVEL_WORN_SOURCE_SCORE = 2900;
	private static final int SPELLBOOK_VARBIT = VarbitID.SPELLBOOK;
	private static final int MEIYERDITCH_SHORTCUT_AGILITY_LEVEL = 93;
	private static final WorldPoint MEIYERDITCH_SHORTCUT_APPROACH =
		new WorldPoint(3500, 9803, 0);
	private static final int MEIYERDITCH_LAB_SHORTCUT_EXIT_MIN_X = 3534;
	/* Installed Shortest Path transport: upper Kalphite Lair -> Queen level. */
	private static final WorldPoint KALPHITE_QUEEN_LOWER_ROPE_APPROACH =
		new WorldPoint(3508, 9498, 2);
	private static final int KALPHITE_LAIR_STAGE_RADIUS = 192;
	private static final int KALPHITE_LAIR_SHORTCUT_AGILITY_LEVEL = 86;
	/* Quest Helper-reviewed Desert Treasure II path through Camdozaal. */
	private static final WorldPoint WHISPERER_LASSAR_SINKHOLE =
		new WorldPoint(2922, 5827, 0);
	/* Installed Shortest Path data: Ring of shadows -> Lassar Undercity. */
	private static final WorldPoint WHISPERER_LASSAR_RING_LANDING =
		new WorldPoint(2588, 6435, 0);
	/* Quest Helper-reviewed post-quest Palace -> Cathedral teleporter stage. */
	private static final WorldPoint WHISPERER_PALACE_TELEPORTER =
		new WorldPoint(2593, 6424, 0);
	private static final WorldPoint WHISPERER_CATHEDRAL_TELEPORTER =
		new WorldPoint(2652, 6405, 0);
	private static final int CAMDOZAAL_MIN_X = 2897;
	private static final int CAMDOZAAL_MAX_X = 3047;
	private static final int CAMDOZAAL_MIN_Y = 5757;
	private static final int CAMDOZAAL_MAX_Y = 5869;
	/* Includes the Ring landing, cathedral approach, and non-instanced Undercity. */
	private static final int LASSAR_UNDERCITY_MIN_X = 2464;
	private static final int LASSAR_UNDERCITY_MAX_X = 2848;
	private static final int LASSAR_UNDERCITY_MIN_Y = 6178;
	private static final int LASSAR_UNDERCITY_MAX_Y = 6562;
	/* Shortest Path's transport graph uses this walkable altar interaction tile. */
	private static final WorldPoint SKOTIZO_CATACOMBS_ALTAR =
		new WorldPoint(1666, 10050, 0);
	private static final int SKOTIZO_LAIR_REGION_ID = 6810;
	/* Installed Shortest Path transport data: Games necklace -> Tears landing. */
	private static final WorldPoint TORMENTED_TEARS_LANDING =
		new WorldPoint(3245, 9500, 2);
	/* Installed Shortest Path transport data: northern climb-rocks approach. */
	private static final WorldPoint TORMENTED_LIGHT_CREATURE_APPROACH =
		new WorldPoint(3241, 9525, 2);
	/* Route-state marker only; it is never submitted as a path destination. */
	private static final WorldPoint TORMENTED_DIRECT_SCROLL_STAGE =
		new WorldPoint(4096, 4419, 0);
	/* Mortimer's Slayer-ring landing and reviewed NPC tile share this cavern. */
	private static final WorldPoint MORTIMER_SLAYER_RING_LANDING =
		new WorldPoint(2581, 8633, 0);
	private static final WorldPoint MORTIMER_MASTER_DESTINATION =
		new WorldPoint(2589, 8614, 0);
	private static final int MORTIMER_CAVERN_RADIUS = 96;
	/* Route-state marker only; the lower chasm requires manual wall/skull actions. */
	private static final WorldPoint TORMENTED_MANUAL_CHASM_STAGE =
		new WorldPoint(4096, 4418, 0);
	/* Built-rowboat endpoints and the verified Ynysdail cavern entrance. */
	private static final WorldPoint AQUANITE_YNYSDAIL_ROWBOAT =
		new WorldPoint(2227, 3469, 0);
	private static final WorldPoint AQUANITE_CAVERN_ENTRANCE =
		new WorldPoint(2218, 3477, 0);
	private static final WorldPoint AQUANITE_PORT_ROBERTS_MOORING =
		new WorldPoint(1889, 3292, 0);
	private static final Set<String> TORMENTED_LIGHT_CREATURE_NAMES =
		java.util.Collections.singleton("light creature");

	private static final int ROUTE_DISCOVERY_INTERVAL_TICKS = 3;
	/*
	 * Container/varbit events are the authoritative quiver update path. Keep a
	 * low-frequency fallback for login/interface ordering without crawling the
	 * nested container and four possible widgets on every game tick.
	 */
	private static final int EXTRA_QUIVER_SAFETY_POLL_TICKS = 5;
	/* A one-tick move beyond this distance is a teleport/region transition. */
	private static final int BANK_ROUTE_REBASE_DISTANCE = 32;
	private static final int PORTRAIT_APPEARANCE_POLL_TICKS = 5;
	private static final int ROUTE_CHECKPOINT_WRITE_INTERVAL_TICKS = 25;
	private static final int INFERNO_PREP_BANK_ARRIVAL_RADIUS = 10;
	private static final int INFERNO_PREP_BANK_APPROACH_RADIUS = 32;

	static int infernoPreparationBankApproachRadiusForRegression()
	{
		return INFERNO_PREP_BANK_APPROACH_RADIUS;
	}
	/*
	 * Ghommal lands inside Mor Ul Rek but the banker may not be in the first NPC
	 * snapshot. Once the player is on the same inner-city coordinate layer and
	 * close to the reviewed east bank, Shortest Path may safely take over the
	 * local walk even before TzHaar-Ket-Yil itself has loaded.
	 */
	private static final int INFERNO_PREP_LOCAL_HANDOFF_RADIUS = 128;
	private static final int INFERNO_PREP_BANKER_DISCOVERY_RADIUS = 64;
	private static final int INFERNO_ENTRY_NPC_RADIUS = 64;
	private static final int INFERNO_REGION_ID = 9043;
	/* Negative means staging travel is shown above, not inside, the 4 x 7. */
	private static final int INFERNO_STAGING_TRAVEL_SLOT_INDEX = -1;
	private static final String INFERNO_PREP_TRAVEL_LOCATION =
		"Mor Ul Rek east bank";
	private static final Set<String> INFERNO_PREP_BANKER_NAMES =
		java.util.Collections.singleton("tzhaar ket yil");
	/* Every current TzHaar-Ket-Keh definition that can own Inferno entry. */
	private static final Set<Integer> INFERNO_ENTRY_NPC_IDS =
		java.util.Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
			NpcID.INFERNO_MASTER,
			NpcID.INFERNO_MASTER_1OP,
			NpcID.INFERNO_MASTER_2OP
		)));
	/*
	 * Best-to-worst reusable Mor Ul Rek teleports. Explicit gameval IDs keep all
	 * usable Hilt/Defender variants detectable even if a display-name alias changes.
	 */
	private static final int[] INFERNO_PREPARATION_TRAVEL_ITEM_IDS =
	{
		ItemID.CA_OFFHAND_GRANDMASTER,
		ItemID.INFERNAL_DEFENDER_GHOMMAL_6,
		ItemID.INFERNAL_DEFENDER_GHOMMAL_6_TROUVER,
		ItemID.CA_OFFHAND_MASTER,
		ItemID.INFERNAL_DEFENDER_GHOMMAL_5,
		ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER,
		ItemID.CA_OFFHAND_ELITE
	};

	enum GuidedSessionPhase
	{
		STOPPED,
		ROUTING_TO_SPELLBOOK,
		ROUTING_TO_BANK,
		ROUTING_TO_PREP_BANK,
		ROUTING_TO_TASK,
		TASKING,
		ROUTING_TO_MASTER,
		WAITING_FOR_TASK
	}

	/**
	 * The route target currently selected by the player. This is deliberately
	 * separate from the detected Slayer assignment: a Hellhound assignment can
	 * target either regular Hellhounds or the reviewed Cerberus boss variant.
	 */
	private static final class GuidedTaskTarget
	{
		private final String taskName;
		private final String location;
		private final SlayerRouteCatalog.RouteProfile profile;
		private final SlayerRoutePlan routePlan;
		private final boolean bossEncounter;

		private GuidedTaskTarget(
			final String taskName,
			final String location,
			final SlayerRouteCatalog.RouteProfile profile,
			final SlayerRoutePlan routePlan,
			final boolean bossEncounter)
		{
			this.taskName = taskName == null ? "" : taskName.trim();
			this.location = location == null ? "" : location.trim();
			this.profile = profile;
			this.routePlan = routePlan;
			this.bossEncounter = bossEncounter;
		}

		private boolean isBoss()
		{
			return bossEncounter || (profile != null && profile.isBoss());
		}

		private boolean isStaged()
		{
			return profile != null && profile.isStaged();
		}

		private boolean requiresLoadedNpc()
		{
			return profile != null && profile.requiresLoadedNpc();
		}

		private boolean isInsideEncounterArea(final WorldPoint playerLocation)
		{
			return profile != null
				&& profile.isInsideEncounterArea(playerLocation);
		}

		private WorldPoint destination(final WorldPoint playerLocation)
		{
			return profile == null
				? null
				: profile.getRouteDestination(playerLocation);
		}

		private int arrivalRadius(final WorldPoint playerLocation)
		{
			return profile == null
				? TASK_ARRIVAL_RADIUS
				: profile.getArrivalRadius(playerLocation);
		}
	}

	/** Exact assignment target frozen across an unfinished-task bank detour. */
	private static final class GuidedTaskDetourSnapshot
	{
		private final SlayerGuidedLifecycleContract.TaskRouteKey key;
		private final GuidedTaskTarget target;

		private GuidedTaskDetourSnapshot(
			final SlayerGuidedLifecycleContract.TaskRouteKey key,
			final GuidedTaskTarget target)
		{
			this.key = key;
			this.target = target;
		}
	}

	private static final class RouteStage
	{
		private final WorldPoint destination;
		private final Set<WorldPoint> targets;
		private final boolean entrance;
		private final boolean exactNpc;
		private final boolean learnedCheckpoint;

		private RouteStage(
			final WorldPoint destination,
			final Set<WorldPoint> targets,
			final boolean entrance,
			final boolean exactNpc,
			final boolean learnedCheckpoint)
		{
			this.destination = destination;
			this.targets = targets == null
				? java.util.Collections.emptySet()
				: targets;
			this.entrance = entrance;
			this.exactNpc = exactNpc;
			this.learnedCheckpoint = learnedCheckpoint;
		}

		private boolean isValid()
		{
			return destination != null && !targets.isEmpty();
		}
	}

	/** Result of evaluating one immutable route-plan evidence snapshot. */
	private static final class RoutePlanResolution
	{
		private final SlayerRoutePlan.ProgressStatus status;
		private final SlayerRoutePlan.Leg leg;
		private final RouteStage stage;
		private final boolean pauseForInteraction;

		private RoutePlanResolution(
			final SlayerRoutePlan.ProgressStatus status,
			final SlayerRoutePlan.Leg leg,
			final RouteStage stage,
			final boolean pauseForInteraction)
		{
			this.status = status;
			this.leg = leg;
			this.stage = stage;
			this.pauseForInteraction = pauseForInteraction;
		}

		private boolean hasArrived()
		{
			return status == SlayerRoutePlan.ProgressStatus.ARRIVED;
		}

		private boolean isWaiting()
		{
			return pauseForInteraction;
		}
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private SlayerPlusConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private TagManager tagManager;

	@Inject
	private LayoutManager layoutManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private SlayerPluginService slayerPluginService;

	@Inject
	private SlayerTaskReadinessOverlay readinessOverlay;

	@Inject
	private SlayerTravelItemOverlay travelItemOverlay;

	private PlayerPortraitRenderer portraitRenderer;
	private SlayerRecommendationEngine recommendationEngine;
	private SlayerLoadoutAnalyzer loadoutAnalyzer;
	private SlayerAchievementDiarySnapshot achievementDiaries =
		SlayerAchievementDiarySnapshot.empty();
	private ShortestPathBridge shortestPathBridge;
	private SlayerPlusPanel panel;
	private NavigationButton navigationButton;
	private SlayerBraceletChargeInfoBox braceletChargeInfoBox;
	private int braceletChargeInfoBoxItemId = -1;
	private boolean braceletAbsentAfterDepletion;
	private int lastAppearanceHash = Integer.MIN_VALUE;
	private boolean portraitPending;
	private long portraitRetryAfterTick;
	private boolean portraitCalibrationLoaded;
	private String lastAccountName = "";
	private final Map<Integer, Integer> cachedBankItems =
		new LinkedHashMap<>();
	/*
	 * Exact, allocation-free comparison source for repeated BANK container events.
	 * Bank Tags can emit these while only changing the visible tag. Avoid resolving
	 * every item composition again unless an item id, quantity, or slot changed.
	 */
	private int[] cachedRawBankContainerState = new int[0];
	private boolean cachedRawBankContainerConfirmed;
	/* BANKMAIN can be republished by Bank Tags while the same bank stays open. */
	private boolean bankInterfaceOpen;
	/*
	 * Equipment recommendations depend on the item occupying each worn slot, not
	 * the live stack count. Keeping this identity snapshot prevents every arrow,
	 * bolt, or thrown-ammo use from rebuilding the complete loadout and route.
	 */
	private int[] cachedWornItemIdentityState = new int[0];
	private boolean cachedWornItemIdentityKnown;
	/* Duplicate inventory publications still use exact id + quantity semantics. */
	private int[] cachedInventoryContainerState = new int[0];
	private boolean cachedInventoryContainerStateKnown;
	private int profileInfernoTravelItemId = -1;
	private boolean profileInfernoTravelSnapshotLoaded;
	private final Set<Integer> profileOwnedSlayerHelmetItemIds =
		new LinkedHashSet<>();
	private boolean profileOwnedSlayerHelmetSnapshotLoaded;
	/*
	 * Bank ownership intentionally excludes placeholders. Seeking-arrow
	 * placeholders and existing gear-tag entries are retained separately only as
	 * identity hints for a real, matching stack reported by Dizana's dedicated
	 * ammo container. They can refine identity but never create ownership.
	 */
	private final Set<Integer> cachedSeekingArrowPlaceholderItemIds =
		new LinkedHashSet<>();
	/* Item definitions are immutable for the client session; avoid re-resolving
	 * every bank item whenever the same Bank Tag is reopened. */
	private final Map<Integer, Integer> seekingPlaceholderClassificationCache =
		new LinkedHashMap<>();
	/*
	 * Dizana's quiver has a second ammunition slot that is not represented in
	 * InventoryID.WORN. RuneLite exposes that nested stack through the two
	 * DIZANAS_QUIVER_TEMP_AMMO varps, so keep it as a separate ownership source.
	 */
	private int cachedExtraQuiverAmmoItemId = -1;
	private int cachedExtraQuiverAmmoQuantity;
	/* Exact usable Dizana variant discovered by the most recent live bank scan. */
	private int cachedBankDizanaVariantItemId = -1;
	/*
	 * GLOBAL TRAVEL AUTHORITY: every consumer reads one immutable selection.
	 * Resolved Shortest Path winners are cached inside this coordinator; authored
	 * fallbacks remain provisional and can never overwrite a resolved winner.
	 */
	private final SlayerTravelCoordinator travelCoordinator =
		new SlayerTravelCoordinator();
	private boolean bankScanned;
	private SlayerRecommendation currentRecommendation;
	private SlayerLoadoutPlan currentLoadout =
		SlayerLoadoutPlan.empty();
	private SlayerTaskPreparationCatalog.PreparationPlan currentPreparation =
		SlayerTaskPreparationCatalog.PreparationPlan.none();
	private SlayerBankTagLayoutService bankTagLayoutService;
	private int currentMasterId;
	private String currentTaskName = "";
	private SlayerTaskVariant currentTaskVariant =
		SlayerTaskVariant.STANDARD_TASK;
	/* Avoid rebuilding immutable encounter/catalog state on every game tick. */
	private SlayerRecommendation cachedGuidedTargetRecommendation;
	private String cachedGuidedTargetTaskName = "";
	private SlayerTaskVariant cachedGuidedTargetVariant;
	private GuidedTaskTarget cachedGuidedTaskTarget;
	private GuidedTaskDetourSnapshot guidedTaskDetourSnapshot;
	private boolean guidedSessionActive;
	private GuidedSessionPhase guidedSessionPhase =
		GuidedSessionPhase.STOPPED;
	private String guidedSessionStatus =
		"Start a guided session to route through your Slayer task";
	private int observedTaskRemaining = -1;
	private String observedTaskName = "";
	private int observedNormalTaskStreak = -1;
	private String pendingChatTaskName = "";
	private int pendingChatTaskRemaining = -1;
	private int pendingChatStreak = -1;
	private int pendingChatPoints = -1;
	private long pendingChatTaskExpiresAfterTick = -1L;
	private boolean pointBoostStreakSyncPending;
	private int pointBoostStreakBeforeCompletion = -1;
	private int lastUsedMasterId;
	private WorldPoint taskAreaAnchor;
	private int taskArrivalGraceTicks;
	private int taskAreaExitTicks;
	/* Last live position used to detect a teleport while routing to a bank. */
	private WorldPoint guidedLastPlayerLocation;
	/* Manual, instance-safe POH spellbook stage. Shortest Path must not receive
	 * an instanced house altar as an overworld destination. */
	private int loadedPohSpellbookAltarId = -1;
	private WorldPoint loadedPohSpellbookAltarLocation;
	private boolean guidedPohSpellbookTeleportPending;
	private boolean guidedPohSpellbookAltarReached;
	private boolean guidedPohSpellbookFallbackActive;
	private int guidedPohSpellbookLoadTicks;
	private boolean guidedBankTransportTracking;
	/* True only while a task route is waiting for a bank-selected teleport to be
	 * withdrawn. A transport callback for an already-carried route must update the
	 * authoritative selection without recursively submitting the same route. */
	private boolean guidedTaskBankPickupPending;
	/* True only while a post-task master route is waiting for a bank-selected
	 * teleport to move into the inventory. It prevents ordinary inventory changes
	 * from repeatedly restarting an already inventory-only master route. */
	private boolean guidedMasterBankPickupPending;
	/* Generation captured when the active Shortest Path bank-aware request starts. */
	private volatile long guidedTravelRequestGeneration = -1L;
	private volatile String guidedTravelRequestRouteIdentity = "";
	/*
	 * GLOBAL PROFILE/ROUTE SYNCHRONIZATION:
	 * Tracks the exact recommendation state that owns the current Shortest Path
	 * route. A playstyle/combat/cannon/burst/travel/location/variant change must
	 * never inherit travel state from the previous profile.
	 */
	private String lastResolvedRoutingProfileIdentity = "";
	/*
	 * Once the user has authored SlayerPlus Current, keep that persistent layout
	 * synchronized with later Shortest Path travel resolution even if Bank Tags
	 * does not report the tab as active on the callback thread.
	 */
	private boolean bankTagLayoutCreated;
	/*
	 * Last complete input successfully written by the proven Bank Tags writer.
	 * Automatic callbacks may repeat the same state; skip those before touching
	 * TagManager/LayoutManager. Explicit button clicks always bypass this cache.
	 */
	private String lastWrittenBankTagState = "";
	private WorldPoint guidedDynamicTaskDestination;
	/*
	 * Exact interactable surface entrance discovered in the loaded scene. The
	 * catalog coordinate remains the long-distance approach anchor; this point
	 * becomes authoritative for the final walking leg once the entrance loads.
	 */
	private WorldPoint guidedSurfaceEntranceDestination;
	private boolean guidedEntranceReached;
	/* Clear a stale surface route once while the exact interior continuation is
	 * loading. The latch prevents a clear/repost loop on every game tick. */
	private boolean guidedRouteWaitingForCheckpoint;
	/*
	 * Declarative cave/dungeon/instance progress. The cursor belongs to one exact
	 * task + encounter + location plan and survives ordinary transport callbacks;
	 * an unfinished-task bank detour restarts it from the reviewed outer leg.
	 */
	private SlayerRoutePlan guidedRoutePlan;
	private SlayerRoutePlan.Cursor guidedRoutePlanCursor;
	private SlayerRoutePlan.Evaluation guidedRoutePlanEvaluation;
	private String guidedRoutePlanIdentity = "";
	private long guidedRoutePlanEvidenceVersion = -1L;
	private long guidedRoutePlanArrivalEvidenceVersion = -1L;
	private SlayerRouteEvidence guidedRouteEvidenceCache;
	private SlayerRoutePlan guidedRouteEvidenceCachePlan;
	private long guidedRouteEvidenceCacheVersion = -1L;
	private long routeEvidenceVersion;
	private WorldPoint guidedPreviousPlayerLocation;
	private WorldPoint guidedPreviousTemplateLocation;
	private WorldPoint guidedLastTemplateLocation;
	/* Manual interactions are observations, never injected actions. Keep them for
	 * a short transition window, then consume them when their manual leg advances. */
	private final Set<SlayerRouteEvidence.ObjectAction>
		observedRoutePlanInteractions = new LinkedHashSet<>();
	private long observedRoutePlanInteractionExpiresAfterTick = -1L;
	/* Event-maintained evidence for the currently active plan. A one-time scene
	 * seed covers enabling or changing a route after the scene was already loaded. */
	private final Map<TileObject, Set<SlayerRouteEvidence.ObjectAction>>
		loadedRoutePlanObjectActions = new LinkedHashMap<>();
	private final Set<SlayerRoutePredicate.ObjectActionSpec>
		guidedRoutePlanObjectSpecs = new LinkedHashSet<>();
	/* Fast rejection for the one-time scene seed. All currently authored route
	 * interactions use reviewed object IDs, so unrelated scene objects never
	 * require an ObjectComposition lookup. */
	private final Set<Integer> guidedRoutePlanObjectIds = new LinkedHashSet<>();
	/* Preserve support for future reviewed name-only predicates. Those are rare,
	 * so only they fall back to composition inspection during the one-time seed. */
	private boolean guidedRoutePlanHasNameOnlyObjectSpecs;
	private boolean routePlanObjectSceneSeedPending = true;
	private final Set<NPC> loadedRoutePlanNpcs = new LinkedHashSet<>();
	/* Keep only NPC aliases referenced by the active plan. Busy areas can contain
	 * hundreds of unrelated NPCs; retaining and rebuilding all of them on every
	 * route tick adds work without contributing any arrival evidence. */
	private final Set<String> guidedRoutePlanNpcNames = new LinkedHashSet<>();
	private boolean routePlanNpcSceneSeedPending = true;
	private final Set<Integer> loadedRoutePlanWidgetGroups =
		new LinkedHashSet<>();
	private final Set<Integer> guidedRoutePlanWidgetGroups =
		new LinkedHashSet<>();
	private final Set<Integer> guidedRoutePlanWidgetComponents =
		new LinkedHashSet<>();
	private final Set<Integer> visibleRoutePlanWidgetComponents =
		new LinkedHashSet<>();
	/* TzKal-Zuk uses the east Mor Ul Rek bank as a mandatory final staging bank. */
	private boolean infernoPreparationBankReady;
	/*
	 * Shortest Path's current public transport catalog does not expose Ghommal's
	 * Mor Ul Rek teleport. When SlayerPlus selects that exact owned staging item,
	 * keep the highlighted physical teleport authoritative but wait for the player
	 * to use it before handing the route to Shortest Path locally.
	 */
	private boolean infernoPreparationManualTeleportPending;
	/*
	 * No-Hilt Inferno access is a separate manual-transition branch: Shortest Path
	 * can reach the east Hot vent door but cannot model its Pass interaction.
	 */
	private boolean infernoPreparationHotVentPending;
	/*
	 * Ring of shadows -> Lassar is a manual first leg. It must never share a
	 * Shortest Path request whose pre-teleport target is Camdozaal's surface
	 * entrance, otherwise that stale route leads the player back out of Lassar.
	 */
	private boolean whispererRingTeleportPending;
	private boolean whispererCathedralTeleportPending;
	/*
	 * The installed Shortest Path transport data does not contain the Spider cave
	 * teleport. Keep SlayerPlus's reviewed scroll instruction authoritative until
	 * its landing is observed, then start a separate local Shortest Path leg.
	 */
	private boolean araxxorSpiderTeleportPending;
	/* Installed Shortest Path data predates Wyrmscraig's Slayer-ring edge. */
	private boolean mortimerSlayerRingTeleportPending;
	private WorldPoint guidedInfernoEntryDestination;
	/* 0 = hidden, 1 = following the route, 2 = ready to enter. */
	private int infernoEntryPromptState;
	/*
	 * GLOBAL POST-TRANSPORT CONTINUATION:
	 * A bank-aware Shortest Path request may finish its teleport leg before the
	 * final in-area walking/positioning leg is drawn. Keep one explicit pending
	 * continuation so arrival through a teleport always triggers a fresh route to
	 * the exact authored cannon/safespot/positioning checkpoint.
	 */
	private boolean guidedPostTransportContinuationPending;
	/* Lantern use is confirmed before the player clicks the attracted creature. */
	private boolean tormentedLightCreatureAttracted;
	private boolean tormentedTempleReached;
	private WorldPoint guidedTormentedChasmTarget;
	private final Set<WorldPoint> loadedTormentedChasmWallLocations =
		new LinkedHashSet<>();
	private final Set<WorldPoint> loadedTormentedChasmExitLocations =
		new LinkedHashSet<>();
	private boolean tormentedChasmSceneSeedPending = true;

	/* UI-only consumer of the authoritative travel selection. */
	private SlayerTeleportHighlighter teleportHighlighter;

	/* Performance guards: coalesce duplicate UI/recommendation work. */
	private boolean slayerRefreshQueued;
	private boolean bankOpenedRefreshPending;
	/* Coalesced rewrite requested by a user-facing SlayerPlus setting change. */
	private boolean bankTagSettingsRefreshPending;
	/* Coalesce Shortest Path transport bursts into one persistent Bank Tag rewrite. */
	private boolean bankTagTravelRefreshQueued;
	/* Stable identity of the last travel decision already painted for consumers. */
	private String lastPublishedTravelSelectionIdentity = "";
	/*
	 * A physical teleport selected for an active Shortest Path request remains the
	 * menu-highlight authority across intermediate recalculation messages. A real
	 * world transition clears the pin, allowing the post-teleport walking leg to
	 * replace it with a non-item route.
	 */
	private long pinnedTravelHighlightGeneration = -1L;
	private String pinnedTravelHighlightRouteIdentity = "";
	private long gameTickSequence;
	/*
	 * Failed live-object/NPC discovery is intentionally retried as the player
	 * approaches an encounter, but never more than once per discovery interval.
	 * This prevents duplicate resolveRouteStage calls in one tick from repeatedly
	 * walking the NPC list or the entire loaded scene.
	 */
	private long lastSurfaceEntranceDiscoveryTick = Long.MIN_VALUE;
	private String lastSurfaceEntranceDiscoveryIdentity = "";
	private long lastTaskNpcDiscoveryTick = Long.MIN_VALUE;
	private String lastTaskNpcDiscoveryIdentity = "";
	private final Map<String, String> persistedRouteCheckpointCache =
		new LinkedHashMap<>();
	private final Map<String, Long> routeCheckpointLastWriteTick =
		new LinkedHashMap<>();
	/*
	 * Loaded bank booths/chests are maintained from scene events. A one-time scene
	 * seed covers enabling SlayerPlus after the surrounding scene already loaded;
	 * routing never crawls the scene every tick.
	 */
	private final Set<WorldPoint> loadedBankObjectLocations =
		new LinkedHashSet<>();
	/* Scene events maintain the entrance candidates used by reviewed cave and
	 * dungeon transitions. A single seed covers enabling the plugin mid-scene;
	 * normal routing never scans the complete scene every few ticks. */
	private final Set<TileObject> loadedRouteTransitionObjects =
		new LinkedHashSet<>();
	private boolean routeTransitionSceneSeedPending = true;
	private boolean bankObjectSceneSeedPending = true;

	@Override
	protected void startUp()
	{
		migrateRemovedPlaystyle();

		portraitRenderer = new PlayerPortraitRenderer(client);
		portraitCalibrationLoaded = portraitRenderer.loadCalibration(
			configManager.getConfiguration(
				SlayerPlusConfig.GROUP,
				PORTRAIT_CALIBRATION_KEY
			)
		);
		recommendationEngine = new SlayerRecommendationEngine(client, config);
		loadoutAnalyzer = new SlayerLoadoutAnalyzer(itemManager);
		shortestPathBridge = new ShortestPathBridge(eventBus);
		bankTagLayoutService = new SlayerBankTagLayoutService(
			client,
			tagManager,
			layoutManager,
			bankTagsService,
			configManager
		);

		teleportHighlighter = new SlayerTeleportHighlighter(
			client,
			clientThread,
			configManager,
			this::currentTravelSelectionForHighlight
		);

		panel = new SlayerPlusPanel(
			() -> clientThread.invokeLater(this::startRecommendedRoute),
			() -> clientThread.invokeLater(this::createRecommendedBankTag),
			variant -> clientThread.invokeLater(
				() -> selectBankTagVariant(variant)
			),
			() -> clientThread.invokeLater(this::toggleGuidedSession),
			config,
			configManager
		);

		final BufferedImage icon = loadPluginIcon();

		navigationButton = NavigationButton.builder()
			.tooltip("SlayerPlus")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navigationButton);

		/* Global selected-method preparation popup. Keep this overlay registered for
		 * the plugin lifetime; its render() method decides when the selected method
		 * actually requires spellbook/rune preparation. */
		if (overlayManager != null && readinessOverlay != null)
		{
			overlayManager.add(readinessOverlay);
		}
		if (overlayManager != null && travelItemOverlay != null)
		{
			overlayManager.add(travelItemOverlay);
		}
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			portraitPending = true;
			portraitRetryAfterTick = 0;
			clientThread.invokeLater(() ->
			{
				loadInfernoTravelItemSnapshot();
				loadOwnedSlayerHelmetSnapshot();
				cacheBankContainer(client.getItemContainer(InventoryID.BANK));
				refreshBraceletChargeInfoBox();
				refreshSlayerData();
			});
		}
	}


	/**
	 * Loads the packaged sidebar icon when it is available. A missing resource
	 * must never prevent the entire plugin from starting, so a small built-in
	 * fallback is rendered when Gradle did not copy src/main/resources.
	 */
	private static BufferedImage loadPluginIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(
				SlayerPlusPlugin.class,
				/* Version the resource name so an older external-plugin JAR on the
				 * development classpath cannot win a same-name resource lookup. */
				"slayerplus_sidebar_v2.png"
			);
		}
		catch (RuntimeException ignored)
		{
			final BufferedImage fallback = new BufferedImage(
				32,
				32,
				BufferedImage.TYPE_INT_ARGB
			);
			final Graphics2D graphics = fallback.createGraphics();
			try
			{
				graphics.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON
				);
				graphics.setColor(new Color(42, 38, 30, 245));
				graphics.fillRoundRect(1, 1, 30, 30, 7, 7);
				graphics.setColor(new Color(218, 176, 70));
				graphics.drawRoundRect(1, 1, 29, 29, 7, 7);
				graphics.setFont(new Font("SansSerif", Font.BOLD, 19));
				graphics.drawString("S", 9, 23);
			}
			finally
			{
				graphics.dispose();
			}
			return fallback;
		}
	}

	@Override
	protected void shutDown()
	{
		removeBraceletChargeInfoBox();
		if (shortestPathBridge != null)
		{
			shortestPathBridge.clear();
		}

		if (overlayManager != null && readinessOverlay != null)
		{
			overlayManager.remove(readinessOverlay);
		}
		if (overlayManager != null && travelItemOverlay != null)
		{
			overlayManager.remove(travelItemOverlay);
		}
		if (panel != null)
		{
			panel.disposeResources();
		}
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
		}

		navigationButton = null;
		panel = null;
		portraitRenderer = null;
		recommendationEngine = null;
		loadoutAnalyzer = null;
		shortestPathBridge = null;
		bankTagLayoutService = null;
		bankTagLayoutCreated = false;
		lastWrittenBankTagState = "";
		currentLoadout = SlayerLoadoutPlan.empty();
		currentRecommendation = null;
		clearCachedGuidedTaskTarget();
		currentPreparation = SlayerTaskPreparationCatalog.PreparationPlan.none();
		currentMasterId = 0;
		currentTaskName = "";
		currentTaskVariant = SlayerTaskVariant.STANDARD_TASK;
		lastResolvedRoutingProfileIdentity = "";
		cachedBankItems.clear();
		cachedRawBankContainerState = new int[0];
		cachedRawBankContainerConfirmed = false;
		bankInterfaceOpen = false;
		cachedWornItemIdentityState = new int[0];
		cachedWornItemIdentityKnown = false;
		cachedInventoryContainerState = new int[0];
		cachedInventoryContainerStateKnown = false;
		profileInfernoTravelItemId = -1;
		profileInfernoTravelSnapshotLoaded = false;
		profileOwnedSlayerHelmetItemIds.clear();
		profileOwnedSlayerHelmetSnapshotLoaded = false;
		cachedSeekingArrowPlaceholderItemIds.clear();
		seekingPlaceholderClassificationCache.clear();
		cachedBankDizanaVariantItemId = -1;
		bankScanned = false;
		clearExtraQuiverAmmoSnapshot();
		lastAppearanceHash = Integer.MIN_VALUE;
		portraitPending = false;
		portraitRetryAfterTick = 0;
		portraitCalibrationLoaded = false;
		lastAccountName = "";
		resetGuidedSessionState();
		travelCoordinator.clearAll();
		if (teleportHighlighter != null)
		{
			teleportHighlighter.shutDown();
			teleportHighlighter = null;
		}
		observedTaskRemaining = -1;
		observedTaskName = "";
		observedNormalTaskStreak = -1;
		pointBoostStreakSyncPending = false;
		pointBoostStreakBeforeCompletion = -1;
		lastUsedMasterId = 0;
		slayerRefreshQueued = false;
		bankOpenedRefreshPending = false;
		bankTagSettingsRefreshPending = false;
		bankTagTravelRefreshQueued = false;
		lastPublishedTravelSelectionIdentity = "";
		gameTickSequence = 0;
		clearRouteDiscoveryThrottle();
		loadedBankObjectLocations.clear();
		bankObjectSceneSeedPending = true;
		loadedRouteTransitionObjects.clear();
		routeTransitionSceneSeedPending = true;
		persistedRouteCheckpointCache.clear();
		routeCheckpointLastWriteTick.clear();
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		gameTickSequence++;
		refreshPortrait();
		/*
		 * RuneLite's Slayer service owns the same state displayed by its task
		 * counter. It can settle one callback after the underlying varp event, so
		 * recover once on the next tick when SlayerPlus is still showing no task.
		 */
		if (currentTaskName.isEmpty()
			&& slayerPluginService != null
			&& slayerPluginService.getRemainingAmount() > 0)
		{
			scheduleSlayerDataRefresh(false);
		}
		if (teleportHighlighter != null)
		{
			teleportHighlighter.onGameTick();
		}
		if (shouldPollExtraQuiverSnapshotForRegression(gameTickSequence)
			&& refreshExtraQuiverAmmoSnapshot())
		{
			scheduleSlayerDataRefresh(false);
		}
		reconcileBankInterfaceState();
		updateGuidedSessionPosition();
	}

	@Subscribe
	public void onChatMessage(final ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		final String message = Text.removeTags(event.getMessage());
		final SlayerTaskChatUpdate taskUpdate =
			SlayerTaskChatUpdate.parse(message);
		if (taskUpdate != null)
		{
			pendingChatTaskExpiresAfterTick = gameTickSequence + 5L;
			if (taskUpdate.getRemaining() > 0
				&& !taskUpdate.getTaskName().isEmpty())
			{
				pendingChatTaskName = taskUpdate.getTaskName();
				pendingChatTaskRemaining = taskUpdate.getRemaining();
			}
			if (taskUpdate.getStreak() >= 0)
			{
				pendingChatStreak = taskUpdate.getStreak();
			}
			if (taskUpdate.getPoints() >= 0)
			{
				pendingChatPoints = taskUpdate.getPoints();
			}
			scheduleSlayerDataRefresh(false);
		}
		final SlayerBraceletChargeTracker.Update braceletUpdate =
			SlayerBraceletChargeTracker.parse(message);
		if (braceletUpdate != null)
		{
			configManager.setRSProfileConfiguration(
				SlayerBraceletChargeTracker.CONFIG_GROUP,
				braceletUpdate.getConfigKey(),
				braceletUpdate.getCharges()
			);
			if (braceletUpdate.getCharges() == 0)
			{
				showDepletedBraceletChargeInfoBox(braceletUpdate.getItemId());
			}
			else
			{
				refreshBraceletChargeInfoBox();
			}
		}

		if (!isTormentedLightCreatureAttractionMessage(message))
		{
			return;
		}

		if (!guidedSessionActive
			|| guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_TASK)
		{
			return;
		}
		final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
		if (target == null
			|| !"ancient guthixian temple".equals(
				normalizeTravelName(target.location)
			))
		{
			return;
		}

		final Player player = client.getLocalPlayer();
		final WorldPoint playerLocation = player == null
			? null : player.getWorldLocation();
		if (!isOnTearsOfGuthixUpperLayer(playerLocation))
		{
			return;
		}

		tormentedLightCreatureAttracted = true;
		taskAreaAnchor = TORMENTED_MANUAL_CHASM_STAGE;
		if (shortestPathBridge != null)
		{
			shortestPathBridge.clear();
		}
		showGuidedTravelRecommendation(
			"Light creature → Into the chasm",
			"Click the attracted light creature and choose Into the chasm."
		);
		setGuidedSessionStatus(
			"The light creature is ready — click it and choose Into the chasm."
		);
	}

	static boolean isTormentedLightCreatureAttractionMessage(
		final String message)
	{
		return message != null
			&& Text.removeTags(message)
				.toLowerCase(Locale.ENGLISH)
				.contains(
					"the light creature is attracted to your beam and comes towards you"
				);
	}

	static boolean shouldPollExtraQuiverSnapshotForRegression(
		final long gameTick)
	{
		return gameTick > 0
			&& gameTick % EXTRA_QUIVER_SAFETY_POLL_TICKS == 0;
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			bankObjectSceneSeedPending = true;
			routePlanObjectSceneSeedPending = true;
			routePlanNpcSceneSeedPending = true;
			portraitPending = true;
			portraitRetryAfterTick = 0;
			clientThread.invokeLater(() ->
			{
				loadInfernoTravelItemSnapshot();
				loadOwnedSlayerHelmetSnapshot();
				cacheBankContainer(client.getItemContainer(InventoryID.BANK));
				refreshSlayerData();
			});
		}
		else
		{
			resetTormentedRouteProgress();
			loadedRoutePlanObjectActions.clear();
			loadedRoutePlanNpcs.clear();
			loadedRoutePlanWidgetGroups.clear();
			visibleRoutePlanWidgetComponents.clear();
			observedRoutePlanInteractions.clear();
			observedRoutePlanInteractionExpiresAfterTick = -1L;
			routePlanObjectSceneSeedPending = true;
			routePlanNpcSceneSeedPending = true;
			guidedPreviousPlayerLocation = null;
			guidedPreviousTemplateLocation = null;
			routeEvidenceVersion++;
			/* A world hop can expose a transient zero Slayer varp before RuneLite's
			 * Slayer service/profile restore the assignment. Forget only the observation
			 * boundary so the next LOGGED_IN refresh cannot misread that zero as a real
			 * task completion. Keep the guided route itself alive across the hop. */
			if (shouldInvalidateTaskObservationForGameState(event.getGameState()))
			{
				/* A hop/reconnect is not a reviewed route transition. Forget both
				 * ends of the movement sample so the first point in the new scene
				 * cannot be compared with a point from the previous world. */
				guidedLastPlayerLocation = null;
				guidedLastTemplateLocation = null;
				observedTaskRemaining = -1;
				observedTaskName = "";
				observedNormalTaskStreak = -1;
			}
			loadedPohSpellbookAltarId = -1;
			loadedPohSpellbookAltarLocation = null;
			loadedBankObjectLocations.clear();
			bankObjectSceneSeedPending = true;
			loadedRouteTransitionObjects.clear();
			routeTransitionSceneSeedPending = true;
			loadedTormentedChasmWallLocations.clear();
			loadedTormentedChasmExitLocations.clear();
			tormentedChasmSceneSeedPending = true;
			if (teleportHighlighter != null)
			{
				teleportHighlighter.discardForWorldTransition();
			}
			clearPinnedTravelHighlight();
			if (event.getGameState() != GameState.LOGIN_SCREEN || panel == null)
			{
				return;
			}
			if (shortestPathBridge != null)
			{
				shortestPathBridge.clear();
			}
			resetGuidedSessionState();
			travelCoordinator.clearAll();
			observedTaskRemaining = -1;
			observedTaskName = "";
			observedNormalTaskStreak = -1;
			pointBoostStreakSyncPending = false;
			pointBoostStreakBeforeCompletion = -1;
			lastUsedMasterId = 0;
			lastAppearanceHash = Integer.MIN_VALUE;
			lastAccountName = "";
			portraitPending = false;
			portraitRetryAfterTick = 0;
			currentRecommendation = null;
			clearCachedGuidedTaskTarget();
			currentLoadout = SlayerLoadoutPlan.empty();
			currentPreparation = SlayerTaskPreparationCatalog.PreparationPlan.none();
			currentMasterId = 0;
			currentTaskName = "";
			currentTaskVariant = SlayerTaskVariant.STANDARD_TASK;
			lastResolvedRoutingProfileIdentity = "";
			bankTagLayoutCreated = false;
			lastWrittenBankTagState = "";
			cachedBankItems.clear();
			cachedRawBankContainerState = new int[0];
			cachedRawBankContainerConfirmed = false;
			bankInterfaceOpen = false;
			cachedWornItemIdentityState = new int[0];
			cachedWornItemIdentityKnown = false;
			cachedInventoryContainerState = new int[0];
			cachedInventoryContainerStateKnown = false;
			profileInfernoTravelItemId = -1;
			profileInfernoTravelSnapshotLoaded = false;
			profileOwnedSlayerHelmetItemIds.clear();
			profileOwnedSlayerHelmetSnapshotLoaded = false;
			cachedSeekingArrowPlaceholderItemIds.clear();
			seekingPlaceholderClassificationCache.clear();
			cachedBankDizanaVariantItemId = -1;
			bankScanned = false;
			clearExtraQuiverAmmoSnapshot();
			bankTagSettingsRefreshPending = false;
			bankTagTravelRefreshQueued = false;
			lastPublishedTravelSelectionIdentity = "";
			clearRouteDiscoveryThrottle();
			persistedRouteCheckpointCache.clear();
			routeCheckpointLastWriteTick.clear();
			SwingUtilities.invokeLater(panel::showLoggedOut);
		}
	}

	static boolean shouldInvalidateTaskObservationForGameState(
		final GameState gameState)
	{
		return gameState == GameState.HOPPING
			|| gameState == GameState.CONNECTION_LOST
			|| gameState == GameState.LOGGING_IN;
	}

	@Subscribe
	public void onGameObjectSpawned(final GameObjectSpawned event)
	{
		observeLoadedRouteTransitionObject(event.getGameObject());
		observeLoadedRoutePlanObject(event.getGameObject());
		observeLoadedBankObject(event.getGameObject());
		observeLoadedTormentedChasmObject(event.getGameObject());
		observeLoadedPohSpellbookAltar(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(final GameObjectDespawned event)
	{
		forgetLoadedRouteTransitionObject(event.getGameObject());
		forgetLoadedRoutePlanObject(event.getGameObject());
		forgetLoadedBankObject(event.getGameObject());
		forgetLoadedTormentedChasmObject(event.getGameObject());
		forgetLoadedPohSpellbookAltar(event.getGameObject());
	}

	@Subscribe
	public void onNpcSpawned(final NpcSpawned event)
	{
		final NPC npc = event == null ? null : event.getNpc();
		if (guidedRoutePlan != null && routePlanNpcMatchesActivePlan(npc)
			&& loadedRoutePlanNpcs.add(npc))
		{
			routeEvidenceVersion++;
		}
	}

	@Subscribe
	public void onNpcDespawned(final NpcDespawned event)
	{
		if (event != null && event.getNpc() != null
			&& loadedRoutePlanNpcs.remove(event.getNpc()))
		{
			routeEvidenceVersion++;
		}
	}

	private void observeLoadedPohSpellbookAltar(final GameObject object)
	{
		if (object == null || !isPohSpellbookAltarIdForRegression(object.getId()))
		{
			return;
		}
		loadedPohSpellbookAltarId = object.getId();
		loadedPohSpellbookAltarLocation = object.getWorldLocation();
	}

	private void forgetLoadedPohSpellbookAltar(final GameObject object)
	{
		if (object != null
			&& object.getId() == loadedPohSpellbookAltarId
			&& object.getWorldLocation() != null
			&& object.getWorldLocation().equals(loadedPohSpellbookAltarLocation))
		{
			loadedPohSpellbookAltarId = -1;
			loadedPohSpellbookAltarLocation = null;
		}
	}

	private void seedLoadedPohSpellbookAltarFromScene()
	{
		final WorldView worldView = client == null
			? null : client.getTopLevelWorldView();
		final Scene scene = worldView == null ? null : worldView.getScene();
		if (scene == null || scene.getTiles() == null)
		{
			return;
		}
		for (final Tile[][] planeTiles : scene.getTiles())
		{
			if (planeTiles == null)
			{
				continue;
			}
			for (final Tile[] column : planeTiles)
			{
				if (column == null)
				{
					continue;
				}
				for (final Tile tile : column)
				{
					if (tile == null || tile.getGameObjects() == null)
					{
						continue;
					}
					for (final GameObject object : tile.getGameObjects())
					{
						observeLoadedPohSpellbookAltar(object);
						if (loadedPohSpellbookAltarId > 0)
						{
							return;
						}
					}
				}
			}
		}
	}

	static boolean isPohSpellbookAltarIdForRegression(final int objectId)
	{
		return objectId == ObjectID.POH_ALTAR_ANCIENT
			|| objectId == ObjectID.POH_ALTAR_LUNAR
			|| objectId == ObjectID.POH_ALTAR_DARK
			|| objectId == ObjectID.POH_ALTAR_OCCULT
			|| objectId == ObjectID.POH_ALTAR_OCCULT_STANDARD
			|| objectId == ObjectID.POH_ALTAR_OCCULT_ANCIENT
			|| objectId == ObjectID.POH_ALTAR_OCCULT_LUNAR
			|| objectId == ObjectID.POH_ALTAR_OCCULT_ARCEUUS;
	}

	static boolean pohAltarSupportsSpellbookForRegression(
		final int objectId,
		final String requiredSpellbook)
	{
		final String required = normalizeTravelName(requiredSpellbook);
		if (!isPohSpellbookAltarIdForRegression(objectId)
			|| required.isEmpty())
		{
			return false;
		}
		if (objectId == ObjectID.POH_ALTAR_OCCULT
			|| objectId == ObjectID.POH_ALTAR_OCCULT_STANDARD
			|| objectId == ObjectID.POH_ALTAR_OCCULT_ANCIENT
			|| objectId == ObjectID.POH_ALTAR_OCCULT_LUNAR
			|| objectId == ObjectID.POH_ALTAR_OCCULT_ARCEUUS)
		{
			return true;
		}
		if (required.contains("standard"))
		{
			return true;
		}
		return objectId == ObjectID.POH_ALTAR_ANCIENT
				&& required.contains("ancient")
			|| objectId == ObjectID.POH_ALTAR_LUNAR
				&& required.contains("lunar")
			|| objectId == ObjectID.POH_ALTAR_DARK
				&& required.contains("arceuus");
	}

	@Subscribe
	public void onWallObjectSpawned(final WallObjectSpawned event)
	{
		observeLoadedRouteTransitionObject(event.getWallObject());
		observeLoadedRoutePlanObject(event.getWallObject());
		observeLoadedBankObject(event.getWallObject());
		observeLoadedTormentedChasmObject(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(final WallObjectDespawned event)
	{
		forgetLoadedRouteTransitionObject(event.getWallObject());
		forgetLoadedRoutePlanObject(event.getWallObject());
		forgetLoadedBankObject(event.getWallObject());
		forgetLoadedTormentedChasmObject(event.getWallObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(final GroundObjectSpawned event)
	{
		observeLoadedRouteTransitionObject(event.getGroundObject());
		observeLoadedRoutePlanObject(event.getGroundObject());
		observeLoadedBankObject(event.getGroundObject());
		observeLoadedTormentedChasmObject(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(final GroundObjectDespawned event)
	{
		forgetLoadedRouteTransitionObject(event.getGroundObject());
		forgetLoadedRoutePlanObject(event.getGroundObject());
		forgetLoadedBankObject(event.getGroundObject());
		forgetLoadedTormentedChasmObject(event.getGroundObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(
		final DecorativeObjectSpawned event)
	{
		observeLoadedRouteTransitionObject(event.getDecorativeObject());
		observeLoadedRoutePlanObject(event.getDecorativeObject());
		observeLoadedBankObject(event.getDecorativeObject());
		observeLoadedTormentedChasmObject(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(
		final DecorativeObjectDespawned event)
	{
		forgetLoadedRouteTransitionObject(event.getDecorativeObject());
		forgetLoadedRoutePlanObject(event.getDecorativeObject());
		forgetLoadedBankObject(event.getDecorativeObject());
		forgetLoadedTormentedChasmObject(event.getDecorativeObject());
	}

	private void migrateRemovedPlaystyle()
	{
		final String stored = configManager.getConfiguration(
			SlayerPlusConfig.GROUP,
			PLAYSTYLE_KEY
		);
		if (stored == null || stored.trim().isEmpty())
		{
			return;
		}

		final String normalized = stored
			.trim()
			.toUpperCase(Locale.ENGLISH)
			.replace('-', '_')
			.replace(' ', '_');

		if (normalized.equals("BALANCED")
			|| normalized.equals("KONAR_FOCUSED")
			|| normalized.equals("AFK"))
		{
			configManager.setConfiguration(
				SlayerPlusConfig.GROUP,
				PLAYSTYLE_KEY,
				SlayerPreference.Playstyle.FAST_XP.name()
			);
		}
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event)
	{
		if (event == null)
		{
			return;
		}

		/*
		 * Easy Teleports is an optional companion plugin, not a hard dependency.
		 * It exposes its replacement labels through ConfigManager and rewrites the
		 * same RuneLite MenuEntry objects we receive. If one of its labels changes,
		 * repaint only the open teleport menu; never rebuild Slayer research/loadouts.
		 */
		if (EASY_TELEPORTS_CONFIG_GROUP.equals(event.getGroup()))
		{
			if (teleportHighlighter != null)
			{
				teleportHighlighter.onEasyTeleportsConfigChanged();
			}
			return;
		}

		if (!SlayerPlusConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		/*
		 * Internal persistence is not a recommendation preference. In particular,
		 * learned route checkpoints used to trigger a full loadout rebuild every
		 * time an NPC-derived checkpoint was saved.
		 */
		final String changedKey = event.getKey();
		if (PORTRAIT_CALIBRATION_KEY.equals(changedKey)
			|| (changedKey != null
				&& changedKey.startsWith(ROUTE_CHECKPOINT_PREFIX)))
		{
			return;
		}

		/*
		 * Re-score immediately when a player changes any recommendation
		 * preference in RuneLite's configuration panel.
		 */
		clientThread.invokeLater(() ->
		{
			if (guidedSessionActive
				&& !config.enableShortestPathRouting())
			{
				stopGuidedSession(
					"Session stopped because Shortest Path routing is disabled"
				);
			}
			bankTagSettingsRefreshPending = true;
			scheduleSlayerDataRefresh(false);
			if (guidedSessionActive
				&& effectiveTaskRemaining() <= 0
				&& ("slayerWorkflow".equals(changedKey)
					|| "pointBoostBonusMaster".equals(changedKey)))
			{
				pointBoostStreakSyncPending = false;
				pointBoostStreakBeforeCompletion = -1;
				routeToMasterForGuidedSession();
			}
		});
	}

	@Subscribe
	public void onVarbitChanged(final VarbitChanged event)
	{
		final int varpId = event.getVarpId();
		final int varbitId = event.getVarbitId();

		/*
		 * RuneLite exposes Dizana's quiver contents through the TEMP_AMMO pair.
		 * refreshExtraQuiverAmmoSnapshot() preserves Seeking identity from that
		 * authoritative value. Amount changes while
		 * firing update the cache but do not rebuild the loadout unless identity or
		 * presence changes.
		 */
		if (varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO
			|| varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)
		{
			if (refreshExtraQuiverAmmoSnapshot())
			{
				scheduleSlayerDataRefresh(false);
			}
			return;
		}

		if (varpId == VarPlayerID.SLAYER_COUNT
			|| varpId == VarPlayerID.SLAYER_COUNT_ORIGINAL
			|| varpId == VarPlayerID.SLAYER_TARGET
			|| varpId == VarPlayerID.SLAYER_AREA
			|| varbitId == VarbitID.SLAYER_TARGET_BOSSID
			|| varbitId == VarbitID.SLAYER_POINTS
			|| varbitId == VarbitID.SLAYER_MASTER
			|| varbitId == VarbitID.SLAYER_TASKS_COMPLETED
			|| varbitId == VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED
			|| varbitId == SPELLBOOK_VARBIT
			|| varbitId == VarbitID.RUNE_POUCH_TYPE_1
			|| varbitId == VarbitID.RUNE_POUCH_TYPE_2
			|| varbitId == VarbitID.RUNE_POUCH_TYPE_3
			|| varbitId == VarbitID.RUNE_POUCH_TYPE_4
			|| varbitId == VarbitID.RUNE_POUCH_TYPE_5
			|| varbitId == VarbitID.RUNE_POUCH_TYPE_6
			|| varbitId == VarbitID.RUNE_POUCH_QUANTITY_1
			|| varbitId == VarbitID.RUNE_POUCH_QUANTITY_2
			|| varbitId == VarbitID.RUNE_POUCH_QUANTITY_3
			|| varbitId == VarbitID.RUNE_POUCH_QUANTITY_4
			|| varbitId == VarbitID.RUNE_POUCH_QUANTITY_5
			|| varbitId == VarbitID.RUNE_POUCH_QUANTITY_6)
		{
			scheduleSlayerDataRefresh(false);
		}
	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event)
	{
		final int containerId = event.getContainerId();
		if (containerId == InventoryID.WORN)
		{
			refreshBraceletChargeInfoBox();
		}
		if (containerId == InventoryID.DIZANAS_QUIVER_AMMO)
		{
			if (refreshExtraQuiverAmmoSnapshot())
			{
				scheduleSlayerDataRefresh(false);
			}
			return;
		}

		final boolean bankChanged = containerId == InventoryID.BANK;
		if (bankChanged)
		{
			if (!cacheBankContainer(event.getItemContainer()))
			{
				/* Bank Tags filtering can republish an identical bank container. */
				return;
			}
		}
		else if (containerId == InventoryID.WORN)
		{
			if (!captureWornIdentityChange(event.getItemContainer()))
			{
				return;
			}
			/* Equipment identity is the authoritative portrait-change event. */
			portraitPending = true;
			portraitRetryAfterTick = 0;
		}
		else if (containerId == InventoryID.INV)
		{
			if (!captureInventoryChange(event.getItemContainer()))
			{
				return;
			}
		}
		else
		{
			return;
		}

		scheduleSlayerDataRefresh(bankChanged);
		if (!bankChanged)
		{
			continueWithSelectedCarriedTeleport();
		}
	}

	private void refreshBraceletChargeInfoBox()
	{
		if (infoBoxManager == null || itemManager == null || client == null)
		{
			return;
		}

		final ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		final Item gloves = worn == null ? null : worn.getItem(
			EquipmentInventorySlot.GLOVES.getSlotIdx()
		);
		final int itemId = gloves == null ? -1 : gloves.getId();
		final String configKey = SlayerBraceletChargeTracker.configKey(itemId);
		if (configKey.isEmpty())
		{
			if (braceletChargeInfoBox != null
				&& braceletChargeInfoBox.isDepleted())
			{
				braceletAbsentAfterDepletion = true;
				return;
			}
			removeBraceletChargeInfoBox();
			return;
		}

		Integer charges = configManager.getRSProfileConfiguration(
			SlayerBraceletChargeTracker.CONFIG_GROUP,
			configKey,
			Integer.class
		);
		if (charges == null)
		{
			/* Migrate the pre-profile Item Charges value if one exists. */
			charges = configManager.getConfiguration(
				SlayerBraceletChargeTracker.CONFIG_GROUP,
				configKey,
				Integer.class
			);
			if (charges != null)
			{
				configManager.unsetConfiguration(
					SlayerBraceletChargeTracker.CONFIG_GROUP,
					configKey
				);
				configManager.setRSProfileConfiguration(
					SlayerBraceletChargeTracker.CONFIG_GROUP,
					configKey,
					charges
				);
			}
		}
		if (braceletAbsentAfterDepletion)
		{
			/* A crumbled bracelet is gone. Equipping a replacement starts a fresh
			 * 30-charge bracelet even though both copies share the same item id. */
			charges = SlayerBraceletChargeTracker.MAX_CHARGES;
			configManager.setRSProfileConfiguration(
				SlayerBraceletChargeTracker.CONFIG_GROUP,
				configKey,
				charges
			);
			braceletAbsentAfterDepletion = false;
		}
		final int displayedCharges = charges == null ? -1 : charges;
		if (braceletChargeInfoBox == null
			|| braceletChargeInfoBoxItemId != itemId
			|| braceletChargeInfoBox.isDepleted() != (displayedCharges == 0))
		{
			removeBraceletChargeInfoBox();
			braceletChargeInfoBoxItemId = itemId;
			braceletChargeInfoBox = new SlayerBraceletChargeInfoBox(
				itemManager.getImage(itemId),
				this,
				itemId == ItemID.BRACELET_OF_SLAUGHTER
					? "Bracelet of slaughter" : "Expeditious bracelet",
				displayedCharges
			);
			infoBoxManager.addInfoBox(braceletChargeInfoBox);
			return;
		}

		braceletChargeInfoBox.setCharges(displayedCharges);
	}

	private void showDepletedBraceletChargeInfoBox(final int itemId)
	{
		if (infoBoxManager == null || itemManager == null || itemId <= 0)
		{
			return;
		}
		removeBraceletChargeInfoBox();
		braceletChargeInfoBoxItemId = itemId;
		braceletChargeInfoBox = new SlayerBraceletChargeInfoBox(
			itemManager.getImage(itemId),
			this,
			itemId == ItemID.BRACELET_OF_SLAUGHTER
				? "Bracelet of slaughter" : "Expeditious bracelet",
			0
		);
		infoBoxManager.addInfoBox(braceletChargeInfoBox);
	}

	private void removeBraceletChargeInfoBox()
	{
		if (braceletChargeInfoBox != null && infoBoxManager != null)
		{
			infoBoxManager.removeInfoBox(braceletChargeInfoBox);
		}
		braceletChargeInfoBox = null;
		braceletChargeInfoBoxItemId = -1;
		braceletAbsentAfterDepletion = false;
	}

	private boolean captureWornIdentityChange(final ItemContainer container)
	{
		final Item[] items = container == null ? null : container.getItems();
		if (items == null)
		{
			return false;
		}
		final boolean changed = !cachedWornItemIdentityKnown
			|| !matchesItemIdentityState(cachedWornItemIdentityState, items);
		if (changed)
		{
			cachedWornItemIdentityState = snapshotItemIdentityState(items);
			cachedWornItemIdentityKnown = true;
		}
		return changed;
	}

	private boolean captureInventoryChange(final ItemContainer container)
	{
		final Item[] items = container == null ? null : container.getItems();
		if (items == null)
		{
			return false;
		}
		final boolean changed = !cachedInventoryContainerStateKnown
			|| !matchesExactItemState(cachedInventoryContainerState, items);
		if (changed)
		{
			cachedInventoryContainerState = snapshotExactItemState(items);
			cachedInventoryContainerStateKnown = true;
		}
		return changed;
	}

	private static boolean matchesItemIdentityState(
		final int[] cachedState,
		final Item[] items)
	{
		if (cachedState == null || cachedState.length != items.length)
		{
			return false;
		}
		for (int slot = 0; slot < items.length; slot++)
		{
			final Item item = items[slot];
			if (cachedState[slot] != (item == null ? -1 : item.getId()))
			{
				return false;
			}
		}
		return true;
	}

	private static int[] snapshotItemIdentityState(final Item[] items)
	{
		final int[] state = new int[items.length];
		for (int slot = 0; slot < items.length; slot++)
		{
			state[slot] = items[slot] == null ? -1 : items[slot].getId();
		}
		return state;
	}

	private static boolean matchesExactItemState(
		final int[] cachedState,
		final Item[] items)
	{
		if (cachedState == null || cachedState.length != items.length * 2)
		{
			return false;
		}
		for (int slot = 0; slot < items.length; slot++)
		{
			final Item item = items[slot];
			final int offset = slot * 2;
			if (cachedState[offset] != (item == null ? -1 : item.getId())
				|| cachedState[offset + 1]
					!= (item == null ? 0 : item.getQuantity()))
			{
				return false;
			}
		}
		return true;
	}

	private static int[] snapshotExactItemState(final Item[] items)
	{
		final int[] state = new int[items.length * 2];
		for (int slot = 0; slot < items.length; slot++)
		{
			final Item item = items[slot];
			final int offset = slot * 2;
			state[offset] = item == null ? -1 : item.getId();
			state[offset + 1] = item == null ? 0 : item.getQuantity();
		}
		return state;
	}

	static boolean shouldRefreshCarriedContainerForRegression(
		final int containerId,
		final boolean identityChanged,
		final boolean exactStateChanged)
	{
		return containerId == InventoryID.WORN
			? identityChanged
			: containerId == InventoryID.INV && exactStateChanged;
	}

	/**
	 * Coalesce bursts of inventory/worn/varbit changes into one recommendation
	 * rebuild. RuneLite can post several related state events in the same client
	 * cycle; recomputing the full researched loadout for each one is redundant.
	 */
	private void scheduleSlayerDataRefresh(final boolean bankOpened)
	{
		bankOpenedRefreshPending |= bankOpened;
		if (slayerRefreshQueued)
		{
			return;
		}
		slayerRefreshQueued = true;
		clientThread.invokeLater(() ->
		{
			final boolean handleBank = bankOpenedRefreshPending;
			final boolean refreshBankTag = bankTagSettingsRefreshPending;
			bankOpenedRefreshPending = false;
			bankTagSettingsRefreshPending = false;
			slayerRefreshQueued = false;
			if (handleBank)
			{
				/* Freeze the exact assignment/variant/location before the bank-driven
				 * recommendation rebuild can select a different owned strategy. This
				 * also covers banks opened directly while TASKING, not only bank legs
				 * initiated by SlayerPlus. */
				captureGuidedTaskDetourSnapshot();
				/* Re-read after BANKMAIN/container events settle in this cycle. */
				final ItemContainer liveBank = client.getItemContainer(
					InventoryID.BANK
				);
				if (liveBank != null)
				{
					cacheBankContainer(liveBank);
				}
			}
			refreshSlayerData();
			if (handleBank)
			{
				handleBankOpenedForGuidedSession();
			}
			if (refreshBankTag && shouldRefreshExistingBankTag(
				bankTagLayoutCreated,
				isSlayerPlusBankTagOpen()
			))
			{
				createRecommendedBankTagNow();
			}
		});
	}

	static boolean shouldRefreshExistingBankTag(
		final boolean layoutCreated,
		final boolean tagOpen)
	{
		return layoutCreated || tagOpen;
	}

	@Subscribe
	public void onPluginMessage(final PluginMessage event)
	{
		if (event == null
			|| !"shortestpath".equals(event.getNamespace())
			|| !"transports".equals(event.getName())
			|| clientThread == null)
		{
			return;
		}

		/*
		 * Shortest Path publishes its completed transport list from its pathfinder
		 * worker. Never inspect RuneLite item containers, item definitions, current
		 * task state, or Bank Tag state on that worker thread. Snapshot only the
		 * message payload plus SlayerPlus' immutable request token, then resolve the
		 * exact physical transport on the client thread. This is the shared source of
		 * truth for both the green menu highlight and the authoritative Bank Tag travel placement
		 * (normally slot 1; Inferno staging is shown above the 4 x 7).
		 */
		final ShortestPathBridge requestBridge = shortestPathBridge;
		final ShortestPathBridge.TransportRequestToken requestToken =
			requestBridge == null
				? null
				: requestBridge.claimPendingTransportResponse();
		if (requestToken == null)
		{
			return;
		}
		final Map<String, Object> data;
		try
		{
			data = snapshotTransportData(event.getData());
		}
		catch (final RuntimeException ex)
		{
			requestBridge.releaseTransportResponseClaim(requestToken);
			return;
		}
		try
		{
			clientThread.invokeLater(() ->
			{
				try
				{
					/* Guided-session state belongs to the client thread. Reading it here
					 * also means the immutable bridge token is compared with the live route
					 * rather than a racy worker-thread snapshot. */
					processShortestPathTransportMessage(
						data,
						guidedTravelRequestGeneration,
						guidedTravelRequestRouteIdentity,
						guidedSessionPhase,
						requestToken,
						requestBridge
					);
				}
				catch (final RuntimeException ex)
				{
					/* A client-thread failure before tryAccept must not strand the sole
					 * response reservation. Release only through the exact bridge that
					 * issued the token; shutdown may already have installed a new one. */
					releaseShortestPathTransportClaim(requestBridge, requestToken);
					log.debug("Unable to process Shortest Path transport response", ex);
				}
			});
		}
		catch (final RuntimeException ex)
		{
			requestBridge.releaseTransportResponseClaim(requestToken);
		}
	}

	private void processShortestPathTransportMessage(
		final Map<String, Object> data,
		final long requestGeneration,
		final String requestIdentity,
		final GuidedSessionPhase requestPhase,
		final ShortestPathBridge.TransportRequestToken requestToken,
		final ShortestPathBridge requestBridge)
	{
		/*
		 * GLOBAL SHORTEST-PATH AUTHORITY:
		 * A worker result may arrive after the task variant/profile changed. The
		 * generation + exact route identity pair captured when the message arrived
		 * must still own the live bank-aware request before it can update anything.
		 */
		final boolean routingToTask =
			guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_TASK;
		final boolean routingToPrepBank =
			guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK;
		final boolean routingToMaster =
			guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER;
		if (!guidedSessionActive
			|| requestBridge != shortestPathBridge
			|| !guidedBankTransportTracking
			|| requestPhase != guidedSessionPhase
			|| (!routingToTask && !routingToPrepBank && !routingToMaster)
			|| requestGeneration < 0L
			|| requestGeneration != guidedTravelRequestGeneration
			|| requestGeneration != travelCoordinator.getGeneration()
			|| requestIdentity == null
			|| requestIdentity.isEmpty()
			|| !requestIdentity.equals(guidedTravelRequestRouteIdentity)
			|| !travelCoordinator.ownsCurrentRoute(requestIdentity))
		{
			releaseShortestPathTransportClaim(requestBridge, requestToken);
			return;
		}

		final String identity;
		if (routingToMaster)
		{
			identity = masterRouteIdentity(
				SlayerMasterRouteCatalog.find(getRoutingMasterId())
			);
		}
		else if (routingToPrepBank)
		{
			identity = guidedTravelRequestRouteIdentity;
		}
		else
		{
			identity = routeIdentity(resolveGuidedTaskTargetForRouting());
		}
		if (!requestIdentity.equals(identity))
		{
			releaseShortestPathTransportClaim(requestBridge, requestToken);
			return;
		}
		if (requestBridge == null
			|| !requestBridge.tryAcceptTransportResponse(
				requestToken,
				data
			))
		{
			/* Malformed, superseded, or geometrically irrelevant responses may not
			 * replace the route's selected item. A strict walking-only response is
			 * accepted by the bridge once any supersession ambiguity is quarantined. */
			return;
		}

		final Object displayInfos = data == null ? null : data.get("displayInfo");
		final SlayerTravelSelection previousSelection =
			travelCoordinator.current();
		final GuidedTaskTarget activeTarget = routingToMaster
			? null : resolveGuidedTaskTargetForRouting();
		if (shouldRetainVerifiedTravelItem(
			activeTarget == null ? "" : activeTarget.location,
			previousSelection,
			previousSelection.hasPhysicalItem()
				&& isTravelItemStillOwned(previousSelection.getItemId())
		))
		{
			/*
			 * Shortest Path has already drawn this recalculated path. Ignore only
			 * its replacement travel-item message so every SlayerPlus consumer
			 * continues to show the first verified owned Taverley transport.
			 */
			return;
		}
		final String previousTravelIdentity =
			previousSelection.travelIdentity();
		final TravelRouteMatch match = findTravelItemFromTransportInfo(
			data,
			routingToMaster
		);
		if (match == null
			&& shouldRetainPinnedTravelHighlight(
				requestGeneration,
				requestIdentity,
				previousSelection,
				pinnedTravelHighlightGeneration,
				pinnedTravelHighlightRouteIdentity,
				findOwnedTravelItem(previousSelection.getItemName()) != null
			))
		{
			/*
			 * Shortest Path can publish a transient walking/no-item result while it
			 * recalculates the same pre-teleport route. Keep the already-verified item
			 * and its green menu path until a world transition proves that transport
			 * has actually been completed.
			 */
			return;
		}

		if (match != null)
		{
			applyTravelRouteMatch(match, identity);
		}
		else
		{
			/*
			 * A Shortest Path transport response with no owned physical item is a real
			 * non-item route (spell, fairy ring, POH-only continuation, walking, etc.).
			 * It must replace, not preserve, an authored provisional inventory item.
			 */
			final TravelInstruction instruction =
				findTravelInstructionFromTransportInfo(displayInfos);
			final String family = instruction == null ? "" : instruction.family;
			final String destination = instruction == null ? "" : instruction.destination;
			final SlayerTravelSelection selection =
				travelCoordinator.resolveNonItem(identity, family, destination);
			publishTravelSelection(
				selection,
				"Follow the transport selected by Shortest Path. No unrelated inventory teleport will be substituted."
			);
		}

		/* One correlated response owns this request generation. Shortest Path may
		 * continue recalculating its visual path as the player moves, but later
		 * worker callbacks cannot oscillate the panel/highlighter/Bank Tag choice. */
		final String currentTravelIdentity =
			travelCoordinator.current().travelIdentity();
		if (shouldRefreshTravelConsumers(
			previousTravelIdentity,
			currentTravelIdentity
		))
		{
			refreshOpenBankTagForTravelSelection();
		}
		continueWithSelectedCarriedTeleport();
	}

	private static void releaseShortestPathTransportClaim(
		final ShortestPathBridge requestBridge,
		final ShortestPathBridge.TransportRequestToken requestToken)
	{
		if (requestBridge != null)
		{
			requestBridge.releaseTransportResponseClaim(requestToken);
		}
	}

	private void continueWithSelectedCarriedTeleport()
	{
		if (!guidedSessionActive
			|| !guidedBankTransportTracking)
		{
			return;
		}
		final SlayerTravelSelection selection = currentTravelSelection();
		if (!selection.hasPhysicalItem()
			|| findCarriedTravelItemForTransport(selection.getItemName()) == null)
		{
			return;
		}

		if (shouldContinueMasterAfterBankPickupForRegression(
			guidedSessionPhase,
			guidedMasterBankPickupPending
		))
		{
			guidedMasterBankPickupPending = false;
			routeToMasterForGuidedSession(false);
			return;
		}

		if (guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_TASK)
		{
			return;
		}
		if (!guidedTaskBankPickupPending)
		{
			/* Inventory-only task routes also publish their selected transport so the
			 * panel, Bank Tag and menu highlighter stay synchronized. Accept that
			 * result, but do not turn it into an endless route -> callback -> route loop. */
			return;
		}

		/* The discovery request has selected exactly one physical teleport and
		 * the player now carries it. Recalculate using inventory only so Shortest
		 * Path shows that native instruction instead of every bank alternative. */
		routeToTaskForGuidedSession(
			false,
			"Using the selected carried teleport"
		);
		guidedTaskBankPickupPending = false;
	}

	static boolean shouldContinueMasterAfterBankPickupForRegression(
		final GuidedSessionPhase phase,
		final boolean bankPickupPending)
	{
		return bankPickupPending
			&& phase == GuidedSessionPhase.ROUTING_TO_MASTER;
	}

	static boolean shouldRefreshTravelConsumers(
		final String previousTravelIdentity,
		final String currentTravelIdentity)
	{
		return currentTravelIdentity != null
			&& !currentTravelIdentity.equals(previousTravelIdentity);
	}

	static boolean shouldRetainPinnedTravelHighlight(
		final long requestGeneration,
		final String requestIdentity,
		final SlayerTravelSelection selection,
		final long pinnedGeneration,
		final String pinnedIdentity,
		final boolean selectedItemStillOwned)
	{
		return selection != null
			&& selection.hasPhysicalItem()
			&& selectedItemStillOwned
			&& requestGeneration >= 0L
			&& requestGeneration == pinnedGeneration
			&& requestIdentity != null
			&& !requestIdentity.isEmpty()
			&& requestIdentity.equals(pinnedIdentity)
			&& selection.belongsTo(requestIdentity);
	}

	static boolean shouldRetainVerifiedTravelItem(
		final String location,
		final SlayerTravelSelection selection,
		final boolean selectedItemStillOwned)
	{
		return SlayerTravelRouteCatalog.locksFirstResolvedTravelItem(location)
			&& selection != null
			&& selection.getStatus()
				== SlayerTravelSelection.Status.RESOLVED_ITEM
			&& selection.hasPhysicalItem()
			&& selectedItemStillOwned;
	}

	private static Map<String, Object> snapshotTransportData(
		final Map<String, Object> source)
	{
		if (source == null || source.isEmpty())
		{
			return java.util.Collections.emptyMap();
		}

		final Map<String, Object> snapshot = new LinkedHashMap<>();
		for (final String key : new String[] {
			"displayInfo", "origin", "destination", "objectInfo"
		})
		{
			final Object value = source.get(key);
			if (value instanceof List<?>)
			{
				snapshot.put(key, new ArrayList<>((List<?>) value));
			}
			else if (value instanceof Object[])
			{
				snapshot.put(key, new ArrayList<>(Arrays.asList((Object[]) value)));
			}
			else if (value != null)
			{
				snapshot.put(key, value);
			}
		}
		return snapshot;
	}

	@Subscribe
	public void onMenuEntryAdded(final MenuEntryAdded event)
	{
		if (teleportHighlighter != null)
		{
			teleportHighlighter.onMenuEntryAdded(event);
		}
		if (event != null)
		{
			highlightGuidedRoutePlanMenuEntry(event.getMenuEntry());
		}
	}

	@Subscribe
	public void onMenuOptionClicked(final MenuOptionClicked event)
	{
		if (guidedRoutePlan == null || event == null
			|| !isRoutePlanObjectMenuAction(event.getMenuAction()))
		{
			return;
		}

		final String action = Text.removeTags(event.getMenuOption());
		final String objectName = Text.removeTags(event.getMenuTarget());
		final SlayerRouteEvidence.ObjectAction observation;
		try
		{
			observation = SlayerRouteEvidence.ObjectAction.of(
				event.getId(), objectName, action
			);
		}
		catch (IllegalArgumentException ignored)
		{
			return;
		}
		if (!matchesAnyRoutePlanObjectSpec(observation)
			|| !observedRoutePlanInteractions.add(observation))
		{
			return;
		}
		observedRoutePlanInteractionExpiresAfterTick = gameTickSequence + 3L;
		routeEvidenceVersion++;
	}

	private static boolean isRoutePlanObjectMenuAction(
		final MenuAction action)
	{
		return action == MenuAction.ITEM_USE_ON_GAME_OBJECT
			|| action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT
			|| action == MenuAction.GAME_OBJECT_FIRST_OPTION
			|| action == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| action == MenuAction.GAME_OBJECT_THIRD_OPTION
			|| action == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	/**
	 * Highlights only the exact action for the active reviewed manual route leg.
	 * This is a visual menu-label change only; it never reorders, invokes, or
	 * consumes an action. Future route legs are deliberately ignored.
	 */
	private void highlightGuidedRoutePlanLiveMenu()
	{
		if (client == null)
		{
			return;
		}
		final Menu menu = client.getMenu();
		if (menu == null)
		{
			return;
		}
		final MenuEntry[] entries = menu.getMenuEntries();
		highlightGuidedRoutePlanMenuEntries(entries);
		if (entries != null)
		{
			menu.setMenuEntries(entries);
		}
	}

	private void highlightGuidedRoutePlanMenuEntries(
		final MenuEntry[] entries)
	{
		if (entries == null)
		{
			return;
		}
		for (final MenuEntry entry : entries)
		{
			highlightGuidedRoutePlanMenuEntry(entry);
			final Menu subMenu = entry == null ? null : entry.getSubMenu();
			if (subMenu != null)
			{
				final MenuEntry[] children = subMenu.getMenuEntries();
				highlightGuidedRoutePlanMenuEntries(children);
				if (children != null)
				{
					subMenu.setMenuEntries(children);
				}
			}
		}
	}

	private void highlightGuidedRoutePlanMenuEntry(final MenuEntry entry)
	{
		if (entry == null || guidedRoutePlan == null
			|| guidedRoutePlanCursor == null
			|| !isRoutePlanObjectMenuAction(entry.getType()))
		{
			return;
		}
		final SlayerRoutePlan.Leg activeLeg = guidedRoutePlan.getLeg(
			guidedRoutePlanCursor.getActiveLegId());
		if (activeLeg == null
			|| activeLeg.getKind()
				!= SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
		{
			return;
		}

		final SlayerRouteEvidence.ObjectAction observation;
		try
		{
			observation = SlayerRouteEvidence.ObjectAction.of(
				entry.getIdentifier(),
				Text.removeTags(entry.getTarget()),
				Text.removeTags(entry.getOption())
			);
		}
		catch (IllegalArgumentException ignored)
		{
			return;
		}
		if (routePlanMenuPredicateMatches(
			activeLeg.getReadinessPredicate(), observation))
		{
			entry.setOption("<col=00ff00>"
				+ Text.removeTags(entry.getOption()) + "</col>");
		}
	}

	private static boolean routePlanMenuPredicateMatches(
		final SlayerRoutePredicate predicate,
		final SlayerRouteEvidence.ObjectAction observation)
	{
		if (predicate == null || observation == null)
		{
			return false;
		}
		if (predicate.getKind()
			== SlayerRoutePredicate.Kind.TRACKED_OBJECT_ACTION)
		{
			return routePlanObjectSpecMatches(
				predicate.getObjectActionSpec(), observation);
		}
		for (final SlayerRoutePredicate child : predicate.getChildren())
		{
			if (routePlanMenuPredicateMatches(child, observation))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean routePlanObjectSpecMatches(
		final SlayerRoutePredicate.ObjectActionSpec spec,
		final SlayerRouteEvidence.ObjectAction observation)
	{
		if (spec == null || observation == null)
		{
			return false;
		}
		final boolean identityMatches =
			(!spec.getObjectIds().isEmpty()
				&& spec.getObjectIds().contains(observation.getObjectId()))
			|| (!spec.getObjectNames().isEmpty()
				&& spec.getObjectNames().contains(observation.getObjectName()));
		return identityMatches
			&& spec.getActions().contains(observation.getAction());
	}

	@Subscribe(priority = -1000f)
	public void onPostMenuSort(final PostMenuSort event)
	{
		if (teleportHighlighter != null)
		{
			/* Paint after default-priority menu-rewrite plugins such as Easy Teleports. */
			teleportHighlighter.onPostMenuSort();
		}
		highlightGuidedRoutePlanLiveMenu();
	}

	@Subscribe(priority = -1000f)
	public void onMenuOpened(final MenuOpened event)
	{
		if (teleportHighlighter != null)
		{
			/* MenuOpened is the stable final write point for an already-open submenu. */
			teleportHighlighter.onMenuOpened(event);
		}
		if (event != null)
		{
			highlightGuidedRoutePlanMenuEntries(event.getMenuEntries());
		}
	}

	@Subscribe
	public void onWidgetLoaded(final WidgetLoaded event)
	{
		if (guidedRoutePlan != null && event != null
			&& guidedRoutePlanWidgetGroups.contains(event.getGroupId())
			&& loadedRoutePlanWidgetGroups.add(event.getGroupId()))
		{
			routeEvidenceVersion++;
		}
		if (teleportHighlighter != null)
		{
			teleportHighlighter.onWidgetLoaded(event);
		}

		/*
		 * BANKMAIN can load before or after its item-container event. Queue one
		 * settled live-bank read so either ordering discovers the quiver immediately.
		 */
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			if (!shouldHandleBankWidgetLoadForRegression(
				bankInterfaceOpen, event.getGroupId()))
			{
				return;
			}
			bankInterfaceOpen = true;
			scheduleSlayerDataRefresh(true);
			return;
		}

		/* Re-read only when a widget group capable of exposing quiver state loads. */
		if (isQuiverRelevantInterfaceGroupForRegression(event.getGroupId())
			&& refreshExtraQuiverAmmoSnapshot())
		{
			scheduleSlayerDataRefresh(false);
		}
	}

	@Subscribe
	public void onWidgetClosed(final WidgetClosed event)
	{
		if (event != null
			&& loadedRoutePlanWidgetGroups.remove(event.getGroupId()))
		{
			routeEvidenceVersion++;
		}
		if (event != null && event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankInterfaceOpen = false;
			handleBankClosedForGuidedSession();
		}
	}

	static boolean shouldHandleBankWidgetLoadForRegression(
		final boolean bankAlreadyOpen,
		final int groupId)
	{
		return groupId == InterfaceID.BANKMAIN && !bankAlreadyOpen;
	}

	private void reconcileBankInterfaceState()
	{
		final Widget bankItems = client.getWidget(
			InterfaceID.Bankmain.ITEMS_CONTAINER
		);
		final boolean visiblyOpen = bankItems != null && !bankItems.isHidden();
		if (!shouldReconcileBankVisibilityForRegression(
			bankInterfaceOpen, visiblyOpen
		))
		{
			return;
		}

		bankInterfaceOpen = visiblyOpen;
		if (visiblyOpen)
		{
			/* WidgetLoaded can be skipped when Bank Tags rebuilds BANKMAIN or a
			 * client transition reuses the group. A settled tick-level edge check
			 * recovers that one missed opening without polling/rebuilding each tick. */
			scheduleSlayerDataRefresh(true);
		}
		else
		{
			/* WidgetClosed is not guaranteed for every bank exit. Treat the actual
			 * disappearance of the visible item container as the authoritative edge. */
			handleBankClosedForGuidedSession();
		}
	}

	static boolean shouldReconcileBankVisibilityForRegression(
		final boolean recordedOpen,
		final boolean visiblyOpen)
	{
		return recordedOpen != visiblyOpen;
	}

	static boolean isQuiverRelevantInterfaceGroupForRegression(
		final int groupId)
	{
		return groupId == InterfaceID.EQUIPMENT
			|| groupId == InterfaceID.EQUIPMENT_SIDE
			|| groupId == InterfaceID.WORNITEMS
			|| groupId == InterfaceID.DIZANAS_QUIVER;
	}

	private void queueTeleportMenuHighlight()
	{
		if (teleportHighlighter != null)
		{
			teleportHighlighter.onMenuStructureChanged();
		}
	}

	private void refreshGuidedTeleportWidgetHighlights()
	{
		if (teleportHighlighter != null)
		{
			teleportHighlighter.refreshNow();
		}
	}

	private void restoreGuidedTeleportWidgetHighlights()
	{
		if (teleportHighlighter != null)
		{
			teleportHighlighter.restoreWidgetHighlights();
		}
	}

	private void clearTeleportHighlightCaptures()
	{
		if (teleportHighlighter != null)
		{
			teleportHighlighter.clearCaptures();
		}
	}

	private void refreshPortrait()
	{
		final SlayerPlusPanel currentPanel = panel;
		if (currentPanel == null
			|| portraitRenderer == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (!shouldCheckPortraitForRegression(
			gameTickSequence,
			portraitPending,
			portraitRetryAfterTick
		))
		{
			return;
		}

		final Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		final String playerName = player.getName();
		if (playerName != null
			&& !playerName.trim().isEmpty()
			&& !playerName.trim().equals(lastAccountName))
		{
			lastAccountName = playerName.trim();
			final String accountName = lastAccountName;
			SwingUtilities.invokeLater(
				() -> currentPanel.showAccountName(accountName)
			);
		}

		final PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			return;
		}

		int appearanceHash = Arrays.hashCode(composition.getEquipmentIds());
		appearanceHash = 31 * appearanceHash
			+ Arrays.hashCode(composition.getColors());
		appearanceHash = 31 * appearanceHash + composition.getGender();

		if (appearanceHash != lastAppearanceHash)
		{
			lastAppearanceHash = appearanceHash;
			portraitPending = true;
			portraitRetryAfterTick = 0;
		}

		if (!portraitPending)
		{
			return;
		}

		/*
		 * Wait until the player is not performing an action animation. This
		 * avoids capturing attacks, skilling poses, emotes, or movement.
		 */
		if (player.getAnimation() != -1
			|| player.getPoseAnimation() != player.getIdlePoseAnimation())
		{
			return;
		}

		final int originalPoseFrame = player.getPoseAnimationFrame();

		try
		{
			/*
			 * Force a consistent idle frame only while requesting the model,
			 * then immediately restore the real frame. The panel receives a
			 * BufferedImage snapshot, so it remains completely static.
			 */
			player.setPoseAnimationFrame(0);

			final int[] equipmentIds =
				composition.getEquipmentIds();
			final int weaponIndex =
				KitType.WEAPON.getIndex();
			final int torsoIndex =
				KitType.TORSO.getIndex();

			final boolean weaponEquipped =
				weaponIndex >= 0
					&& weaponIndex < equipmentIds.length
					&& equipmentIds[weaponIndex]
						>= PlayerComposition.ITEM_OFFSET;

			/*
			 * Capture the weaponless body first, restore the equipment, and
			 * only then obtain a fresh complete model. This avoids retaining a
			 * model reference that may be replaced when the composition hash
			 * changes.
			 */
			final int originalWeapon = weaponEquipped
				? equipmentIds[weaponIndex]
				: 0;
			Model bodyModel;

			if (weaponEquipped)
			{
				equipmentIds[weaponIndex] = 0;
				composition.setHash();

				try
				{
					bodyModel = player.getModel();
				}
				finally
				{
					equipmentIds[weaponIndex] = originalWeapon;
					composition.setHash();
				}
			}
			else
			{
				bodyModel = player.getModel();
			}

			if (bodyModel == null)
			{
				deferPortraitRetry();
				return;
			}

			/*
			 * Fetch the full model after the original weapon has been restored.
			 */
			final Model displayModel = player.getModel();
			if (displayModel == null)
			{
				deferPortraitRetry();
				return;
			}
			final boolean torsoArmourEquipped =
				torsoIndex >= 0
					&& torsoIndex < equipmentIds.length
					&& equipmentIds[torsoIndex]
						>= PlayerComposition.ITEM_OFFSET;

			final boolean allowCalibration =
				!portraitCalibrationLoaded
					&& !weaponEquipped
					&& torsoArmourEquipped;

			final BufferedImage portrait =
				portraitRenderer.render(
					displayModel,
					bodyModel,
					portraitCalibrationLoaded
						|| allowCalibration
				);

			if (portrait != null)
			{
				if (!portraitCalibrationLoaded
					&& portraitRenderer.hasCalibration())
				{
					final String calibration =
						portraitRenderer.exportCalibration();

					if (calibration != null)
					{
						configManager.setConfiguration(
							SlayerPlusConfig.GROUP,
							PORTRAIT_CALIBRATION_KEY,
							calibration
						);
						portraitCalibrationLoaded = true;
					}
				}

				portraitPending = false;
				portraitRetryAfterTick = 0;
				SwingUtilities.invokeLater(
					() -> currentPanel.setPortrait(portrait)
				);
			}
			else
			{
				deferPortraitRetry();
			}
		}
		catch (RuntimeException ex)
		{
			/*
			 * Models and the shared rasterizer can be invalidated by a scene or
			 * equipment transition while a portrait is being captured. Portraits
			 * are cosmetic, so never let that transient state escape GameTick and
			 * destabilize the client; retry after the normal cooldown instead.
			 */
			log.debug("Unable to capture SlayerPlus portrait; retrying later", ex);
			deferPortraitRetry();
		}
		finally
		{
			try
			{
				player.setPoseAnimationFrame(originalPoseFrame);
			}
			catch (RuntimeException ex)
			{
				/* The player can disappear during a scene transition. */
				log.debug("Unable to restore portrait pose after scene transition", ex);
			}
		}
	}

	static boolean shouldCheckPortraitForRegression(
		final long tick,
		final boolean pending,
		final long retryAfterTick)
	{
		if (pending)
		{
			return tick >= retryAfterTick;
		}
		return tick % PORTRAIT_APPEARANCE_POLL_TICKS == 0;
	}

	private void deferPortraitRetry()
	{
		portraitRetryAfterTick = gameTickSequence
			+ PORTRAIT_APPEARANCE_POLL_TICKS;
	}

	private void refreshSlayerData()
	{
		final SlayerPlusPanel currentPanel = panel;
		if (currentPanel == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		/* Keep Dizana's extra ammo synchronized with the loadout being rebuilt. */
		refreshExtraQuiverAmmoSnapshot();
		loadoutAnalyzer.setSlayerHelmetPreference(
			configManager.getConfiguration(
				SlayerPlusConfig.GROUP,
				SlayerHelmetPreference.CONFIG_KEY
			)
		);
		loadoutAnalyzer.setDesertEliteDiaryComplete(
			client.getVarbitValue(VarbitID.DESERT_DIARY_ELITE_COMPLETE) > 0
		);
		achievementDiaries = SlayerAchievementDiarySnapshot.capture(client);
		loadoutAnalyzer.setAchievementDiaries(achievementDiaries);
		if (!profileOwnedSlayerHelmetSnapshotLoaded)
		{
			loadOwnedSlayerHelmetSnapshot();
		}
		final Set<String> ownedSlayerHelmetNames =
			new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		ownedSlayerHelmetNames.addAll(
			loadoutAnalyzer.ownedSlayerHelmetNames(
				client.getItemContainer(InventoryID.INV),
				client.getItemContainer(InventoryID.WORN),
				cachedBankItems
			)
		);
		ownedSlayerHelmetNames.addAll(
			loadoutAnalyzer.slayerHelmetNamesForItemIds(
				profileOwnedSlayerHelmetItemIds
			)
		);
		final List<String> ownedSlayerHelmets =
			new ArrayList<>(ownedSlayerHelmetNames);

		final int previousRemaining = observedTaskRemaining;
		final String previousTaskName = observedTaskName;
		final String accountName = getAccountName();
		/* Quest.getState executes a client script and must stay on this thread. */
		final boolean whileGuthixSleepsFinished =
			Quest.WHILE_GUTHIX_SLEEPS.getState(client) == QuestState.FINISHED;
		final int serviceRemaining = slayerPluginService == null
			? 0 : slayerPluginService.getRemainingAmount();
		final int serviceInitialAmount = slayerPluginService == null
			? 0 : slayerPluginService.getInitialAmount();
		final String serviceTaskName = slayerPluginService == null
			|| slayerPluginService.getTask() == null
			? "" : slayerPluginService.getTask().trim();
		final String serviceTaskLocation = slayerPluginService == null
			|| slayerPluginService.getTaskLocation() == null
			? "" : slayerPluginService.getTaskLocation().trim();
		final int liveRemaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		final int profileRemaining = readRuneLiteSlayerProfileInt(
			RUNELITE_SLAYER_AMOUNT_KEY
		);
		final boolean chatUpdatePending =
			gameTickSequence <= pendingChatTaskExpiresAfterTick;
		final boolean chatAssignmentPending =
			pendingChatTaskRemaining > 0 && chatUpdatePending;
		final int remaining = resolveTaskRemainingForRegression(
				observedTaskRemaining,
				serviceRemaining,
				liveRemaining,
				profileRemaining,
				chatAssignmentPending ? pendingChatTaskRemaining : -1
			);
		final int liveInitialAmount = client.getVarpValue(
			VarPlayerID.SLAYER_COUNT_ORIGINAL
		);
		final int profileInitialAmount = readRuneLiteSlayerProfileInt(
			RUNELITE_SLAYER_INITIAL_AMOUNT_KEY
		);
		final int initialAmount = Math.max(
				remaining,
				firstPositive(
					serviceInitialAmount,
					liveInitialAmount,
					profileInitialAmount,
					chatAssignmentPending ? pendingChatTaskRemaining : -1
				)
			);
		final int liveMasterId = client.getVarbitValue(VarbitID.SLAYER_MASTER);
		final int inferredMasterId = remaining > 0
			? inferNearbySlayerMasterId()
			: 0;
		final int storedMasterId = readSlayerPlusProfileInt(
			LAST_SLAYER_MASTER_SNAPSHOT_KEY
		);
		final int masterId = firstPositive(
			liveMasterId, inferredMasterId, storedMasterId
		);
		final int livePoints = client.getVarbitValue(VarbitID.SLAYER_POINTS);
		final int points = preferLiveOrProfileValue(
			livePoints,
			readRuneLiteSlayerProfileInt(RUNELITE_SLAYER_POINTS_KEY),
			chatUpdatePending ? pendingChatPoints : -1
		);
		final int normalStreak = client.getVarbitValue(
			VarbitID.SLAYER_TASKS_COMPLETED
		);
		final int resolvedNormalStreak = preferLiveOrProfileValue(
			normalStreak,
			readRuneLiteSlayerProfileInt(RUNELITE_SLAYER_STREAK_KEY),
			chatUpdatePending ? pendingChatStreak : -1
		);
		final int streak = masterId == KRYSTILIA_MASTER_ID
			? client.getVarbitValue(
				VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED
			)
			: resolvedNormalStreak;
		final int previousNormalStreak = observedNormalTaskStreak;

		currentMasterId = masterId;
		if (masterId > 0)
		{
			lastUsedMasterId = masterId;
			storeLastSlayerMasterId(masterId);
		}

		if (remaining <= 0)
		{
			currentRecommendation = null;
			clearCachedGuidedTaskTarget();
			currentLoadout = SlayerLoadoutPlan.empty();
			currentPreparation = SlayerTaskPreparationCatalog.PreparationPlan.none();
			currentTaskName = "";
			currentTaskVariant = SlayerTaskVariant.STANDARD_TASK;
			lastResolvedRoutingProfileIdentity = "";

			/* Publish the newly resolved state before routing callbacks consult it.
			 * This keeps completion and login/profile recovery on one authority. */
			observedTaskRemaining = 0;
			observedTaskName = "";
			observedNormalTaskStreak = resolvedNormalStreak;
			handleGuidedTaskTransition(
				previousRemaining,
				previousTaskName,
				0,
				"",
				previousNormalStreak,
				resolvedNormalStreak
			);
			final boolean sessionActive = guidedSessionActive;
			final boolean sessionAvailable = isGuidedSessionAvailable();
			final String sessionStatus = getGuidedSessionDisplayStatus();
			final String pointBoostStatus = pointBoostPanelStatus(
				resolvedNormalStreak,
				false,
				masterId
			);
			final String nextMasterName = masterDisplayNameForRegression(
				masterForNextAssignment(resolvedNormalStreak),
				whileGuthixSleepsFinished
			);
			SwingUtilities.invokeLater(() ->
			{
				currentPanel.configureOwnedSlayerHelmets(ownedSlayerHelmets);
				currentPanel.showAccountName(accountName);
				currentPanel.showNoTask(
					points,
					streak,
					nextMasterName
				);
				currentPanel.showPointBoostStatus(pointBoostStatus);
				currentPanel.showPreparation(currentPreparation);
				currentPanel.showSessionState(
					sessionActive,
					sessionAvailable,
					sessionStatus
				);
			});
			return;
		}

		final String profileTaskName = readRuneLiteSlayerProfileString(
			RUNELITE_SLAYER_TASK_NAME_KEY
		);
		final String taskName = serviceRemaining > 0 && !serviceTaskName.isEmpty()
				? formatName(serviceTaskName)
				: liveRemaining > 0
				? readTaskName()
				: !profileTaskName.isEmpty() && profileRemaining > 0
					? formatName(profileTaskName)
					: chatAssignmentPending
						? pendingChatTaskName
						: "Unknown task";
		if (isPointBoosting()
			&& masterId == SlayerPointBoostCoordinator.TURAEL_AYA_MASTER_ID
			&& SlayerTuraelBoostCatalog.find(taskName) != null)
		{
			/*
			 * Point boosting is about the small ordinary assignment.  Never retain
			 * a boss alternative (Artio, Scurrius, Brutus, and similar) from a
			 * previous normal-session selection for a Turael/Aya boost task.
			 */
			currentTaskVariant = SlayerTaskVariant.STANDARD_TASK;
		}
		else if (!sameTask(currentTaskName, taskName))
		{
			currentTaskVariant =
				SlayerTaskVariantCatalog.getDefaultVariant(taskName);
		}
		currentTaskName = taskName == null ? "" : taskName.trim();
		if (!SlayerTaskVariantCatalog.getAvailableVariants(taskName)
			.contains(currentTaskVariant))
		{
			currentTaskVariant =
				SlayerTaskVariantCatalog.getDefaultVariant(taskName);
		}

		final boolean konarAssignment = masterId == KONAR_MASTER_ID;
		final String assignedLocation = konarAssignment
			? !serviceTaskLocation.isEmpty()
				? serviceTaskLocation
				: liveRemaining > 0
				? readAssignedLocation()
				: profileSlayerLocation()
			: "Not restricted";
		SlayerRecommendation recommendation =
			recommendationEngine == null
				? null
				: recommendationEngine.recommend(
					taskName,
					masterId,
					assignedLocation
				);

		/*
		 * Automatic is account-aware only after the real bank has been scanned.
		 * This compares the reviewed melee, ranged, and magic candidates using
		 * the bank, inventory, and worn equipment instead of forcing a weak
		 * fallback inside the catalog's initially preferred combat style.
		 */
		if (recommendation != null && loadoutAnalyzer != null)
		{
			recommendation =
				loadoutAnalyzer.resolveOwnedAutomaticRecommendation(
					taskName,
					recommendation,
					client.getItemContainer(InventoryID.INV),
					client.getItemContainer(InventoryID.WORN),
					cachedBankItems,
					bankScanned,
					currentTaskVariant,
					config
				);
		}
		final SlayerRecommendation resolvedRecommendation = recommendation;
		currentRecommendation = resolvedRecommendation;

		final SlayerLoadoutPlan loadout;
		if (!config.showLoadoutRecommendations())
		{
			loadout = SlayerLoadoutPlan.hidden();
		}
		else if (loadoutAnalyzer == null)
		{
			loadout = new SlayerLoadoutPlan(
				"Loadout scanner unavailable",
				"Loadout scanner unavailable",
				"No item scan available"
			);
		}
		else
		{
			loadout = loadoutAnalyzer.analyze(
				taskName,
				resolvedRecommendation,
				client.getItemContainer(InventoryID.INV),
				client.getItemContainer(InventoryID.WORN),
				cachedBankItems,
				bankScanned,
				currentTaskVariant,
				config,
				remaining,
				cachedExtraQuiverAmmoItemId,
				cachedExtraQuiverAmmoQuantity
			);
		}

		currentLoadout = loadout;

		/*
		 * Global preparation rule: resolve spellbook/rune-pouch/readiness from
		 * the exact selected encounter strategy, not from the assignment name.
		 * This is critical for boss variants (Thermy, Cerberus, etc.) because the
		 * legacy compatibility overload intentionally ignored boss variants.
		 * Every regular task, boss variant, and future encounter now flows through
		 * the same method-driven preparation resolver.
		 */
		final SlayerTaskVariantCatalog.ResolvedTarget preparationTarget =
			SlayerTaskVariantCatalog.resolve(
				currentTaskName,
				currentTaskVariant,
				resolvedRecommendation,
				config
			);
		final String preparationEncounter = preparationTarget == null
			? currentTaskName : preparationTarget.getTaskName();
		final String preparationLocation = preparationTarget == null
			? resolvedRecommendation.getLocation() : preparationTarget.getLocation();
		final SlayerTaskStrategy preparationStrategy = preparationTarget == null
			? resolvedRecommendation.getStrategy() : preparationTarget.getStrategy();

		currentPreparation = SlayerTaskPreparationCatalog.resolve(
			preparationEncounter,
			preparationLocation,
			preparationStrategy,
			client,
			client.getItemContainer(InventoryID.INV),
			client.getItemContainer(InventoryID.WORN),
			cachedBankItems
		);
		/* Routing must use the same resolved task snapshot as the panel. Raw varps
		 * can still be zero briefly after login even when RuneLite's Slayer profile
		 * and service have already restored the active assignment. */
		observedTaskRemaining = remaining;
		observedTaskName = currentTaskName;
		observedNormalTaskStreak = resolvedNormalStreak;
		handleGuidedTaskTransition(
			previousRemaining,
			previousTaskName,
			remaining,
			currentTaskName,
			previousNormalStreak,
			resolvedNormalStreak
		);
		handleGuidedSpellbookReadinessTransition();
		final GuidedTaskTarget displayTarget = resolveGuidedTaskTarget();
		final String resolvedRoutingProfileIdentity = routeIdentity(displayTarget);
		final String previousRoutingProfileIdentity =
			lastResolvedRoutingProfileIdentity;
		final boolean routingProfileChanged =
			!previousRoutingProfileIdentity.isEmpty()
				&& !previousRoutingProfileIdentity.equals(
					resolvedRoutingProfileIdentity
				);
		lastResolvedRoutingProfileIdentity = resolvedRoutingProfileIdentity;

		/*
		 * GLOBAL BANK-TAG / SHORTEST-PATH SYNCHRONIZATION RULE:
		 * A recommendation-profile change invalidates the old route immediately.
		 * Recalculate Shortest Path from the newly resolved profile and, when the
		 * persistent bank tag already exists, rebuild it from that same state. This
		 * prevents Profit/Fast-XP/AFK or preference travel from surviving after the
		 * player changes profiles. The Bank Tag button itself remains instant; this
		 * reroute is driven by the profile change that caused the reload.
		 */
		if (routingProfileChanged)
		{
			/*
			 * Invalidate the route we are leaving, not the route we just selected.
			 * The previous implementation recomputed the identity after replacing the
			 * recommendation and accidentally evicted the new profile while leaving the
			 * stale old Shortest Path winner cached.
			 */
			invalidateTravelStateForProfileChange(
				previousRoutingProfileIdentity
			);

			if (!resolvedRoutingProfileIdentity.isEmpty()
				&& guidedSessionActive
				&& config.enableShortestPathRouting()
				&& (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_SPELLBOOK
					|| guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_TASK
					|| guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK
					|| guidedSessionPhase == GuidedSessionPhase.TASKING))
			{
				/*
				 * LIVE PROFILE SWITCH INVARIANT:
				 * Once this account has scanned its bank, changing encounter/location/
				 * variant while the session is active must reuse that known bank state.
				 * Requiring the bank interface to still be physically open discarded the
				 * new route's transport selection after regular <-> boss switches.
				 */
				final boolean bankAwareProfileReroute =
					shouldUseBankAwareProfileReroute(
						bankScanned,
						client.getItemContainer(InventoryID.BANK) != null
					);
				routeToTaskForGuidedSession(
					bankAwareProfileReroute,
					"Encounter/profile changed"
				);
				if (bankTagLayoutCreated || isSlayerPlusBankTagOpen())
				{
					createRecommendedBankTag();
				}
			}
		}

		final String positioningNote = displayTarget == null
			|| displayTarget.profile == null
				? ""
				: displayTarget.profile.getPositioningNote();
		final String displayedTaskName = taskName;
		final boolean sessionActive = guidedSessionActive;
		final boolean sessionAvailable = isGuidedSessionAvailable();
		final String sessionStatus = getGuidedSessionDisplayStatus();
		final String pointBoostStatus = pointBoostPanelStatus(
			resolvedNormalStreak,
			true,
			masterId
		);
		final String masterName = masterDisplayNameForRegression(
			masterId,
			whileGuthixSleepsFinished
		);
		SwingUtilities.invokeLater(() ->
		{
			currentPanel.configureOwnedSlayerHelmets(ownedSlayerHelmets);
			currentPanel.showAccountName(accountName);
			currentPanel.configureTaskVariants(
				taskName,
				currentTaskVariant
			);
			currentPanel.showTask(
				displayedTaskName,
				remaining,
				initialAmount,
				masterName,
				assignedLocation,
				points,
				streak,
				konarAssignment
			);
			currentPanel.showPointBoostStatus(pointBoostStatus);
			currentPanel.showRecommendation(
				resolvedRecommendation,
				loadout,
				config.enableShortestPathRouting()
			);
			currentPanel.showPositioningNote(positioningNote);
			currentPanel.showPreparation(currentPreparation);
			currentPanel.showSessionState(
				sessionActive,
				sessionAvailable,
				sessionStatus
			);
		});
	}

	private void selectBankTagVariant(
		final SlayerTaskVariant variant)
	{
		final boolean refreshOpenBankTag = isSlayerPlusBankTagOpen();
		clearGuidedTaskDetourSnapshot();

		final SlayerTaskVariant requested = variant == null
			? SlayerTaskVariant.STANDARD_TASK
			: variant;

		if (!SlayerTaskVariantCatalog.getAvailableVariants(currentTaskName)
			.contains(requested))
		{
			currentTaskVariant =
				SlayerTaskVariantCatalog.getDefaultVariant(currentTaskName);
		}
		else
		{
			currentTaskVariant = requested;
		}
		refreshSlayerData();
		if (refreshOpenBankTag)
		{
			/*
			 * The player is already looking at SlayerPlus Current. Rebuild the
			 * same persistent layout immediately so changing between the regular
			 * monster and boss variant never requires a second button press.
			 */
			createRecommendedBankTag();
		}
	}

	private boolean isSlayerPlusBankTagOpen()
	{
		if (bankTagsService == null
			|| client.getItemContainer(InventoryID.BANK) == null)
		{
			return false;
		}

		final String activeTag = bankTagsService.getActiveTag();
		return activeTag != null
			&& Text.standardize(activeTag).equals(
				Text.standardize(SlayerBankTagLayoutService.TAG_NAME)
			);
	}

	private GuidedTaskTarget resolveGuidedTaskTarget()
	{
		final SlayerRecommendation recommendation = currentRecommendation;
		if (recommendation == null)
		{
			clearCachedGuidedTaskTarget();
			return null;
		}
		if (recommendation == cachedGuidedTargetRecommendation
			&& sameTask(currentTaskName, cachedGuidedTargetTaskName)
			&& currentTaskVariant == cachedGuidedTargetVariant)
		{
			return cachedGuidedTaskTarget;
		}

		/*
		 * Resolve encounter identity before Shortest Path sees anything. This is
		 * the single global gate for regular tasks, selectable boss alternatives,
		 * and direct boss assignments. No enum-name guessing is permitted here.
		 */
		final SlayerTaskVariantCatalog.ResolvedTarget resolvedTarget =
			SlayerTaskVariantCatalog.resolve(
				currentTaskName,
				currentTaskVariant,
				recommendation,
				config
			);
		if (resolvedTarget == null || !resolvedTarget.isValid())
		{
			return null;
		}

		final String targetTaskName = resolvedTarget.getTaskName();
		final String targetLocation = resolvedTarget.getLocation();
		final boolean bossEncounter = resolvedTarget.isBoss();

		final SlayerRouteCatalog.RouteProfile profile =
			SlayerRouteCatalog.resolve(
				targetTaskName,
				targetLocation,
				bossEncounter
			);
		final SlayerRoutePlan routePlan = SlayerRoutePlanCatalog.resolve(
			targetTaskName,
			targetLocation,
			bossEncounter
		);

		/*
		 * Boss routing is deliberately strict. SlayerRouteCatalog may return a
		 * boss-only NPC profile when an entrance has not yet been verified, but
		 * it can never reuse an ordinary-monster profile.
		 */
		final GuidedTaskTarget target = profile == null && routePlan == null
			? null
			: new GuidedTaskTarget(
				targetTaskName,
				targetLocation,
				profile,
				routePlan,
				bossEncounter
			);
		cachedGuidedTargetRecommendation = recommendation;
		cachedGuidedTargetTaskName = currentTaskName;
		cachedGuidedTargetVariant = currentTaskVariant;
		cachedGuidedTaskTarget = target;
		return target;
	}

	private GuidedTaskTarget resolveGuidedTaskTargetForRouting()
	{
		final GuidedTaskDetourSnapshot snapshot = guidedTaskDetourSnapshot;
		if (snapshot == null)
		{
			return resolveGuidedTaskTarget();
		}

		if (!SlayerGuidedLifecycleContract.canResumeTaskAfterBank(
			snapshot.key,
			effectiveTaskRemaining()
			)
			|| !snapshot.key.belongsToAssignment(currentTaskName))
		{
			clearGuidedTaskDetourSnapshot();
			return resolveGuidedTaskTarget();
		}

		return snapshot.target;
	}

	private void captureGuidedTaskDetourSnapshot()
	{
		if (guidedTaskDetourSnapshot != null
			|| effectiveTaskRemaining() <= 0)
		{
			return;
		}

		final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
		if (target == null)
		{
			return;
		}

		final SlayerGuidedLifecycleContract.TaskRouteKey key =
			SlayerGuidedLifecycleContract.taskRouteKey(
				currentTaskName,
				currentTaskVariant,
				target.taskName,
				target.location
			);
		if (key.isComplete())
		{
			guidedTaskDetourSnapshot = new GuidedTaskDetourSnapshot(
				key,
				target
			);
		}
	}

	private void clearGuidedTaskDetourSnapshot()
	{
		guidedTaskDetourSnapshot = null;
	}

	private void clearCachedGuidedTaskTarget()
	{
		cachedGuidedTargetRecommendation = null;
		cachedGuidedTargetTaskName = "";
		cachedGuidedTargetVariant = null;
		cachedGuidedTaskTarget = null;
	}

	private static boolean sameTask(
		final String first,
		final String second)
	{
		final String left = first == null ? "" : first.trim();
		final String right = second == null ? "" : second.trim();
		return left.equalsIgnoreCase(right);
	}

	private boolean refreshExtraQuiverAmmoSnapshot()
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}

		final boolean wearingQuiver = isWearingUsableDizana();
		final int carriedQuiverItemId = findUsableDizanaVariantInContainer(
			client.getItemContainer(InventoryID.INV)
		);
		if (!wearingQuiver
			&& carriedQuiverItemId <= 0
			&& cachedBankDizanaVariantItemId <= 0)
		{
			/* Do not turn unrelated/stale ammo state into owned quiver equipment. */
			final boolean changed = cachedExtraQuiverAmmoItemId > 0
				|| cachedExtraQuiverAmmoQuantity > 0;
			clearExtraQuiverAmmoSnapshot();
			return changed;
		}

		/*
		 * Dizana's dedicated item container persists the nested ammunition while the
		 * quiver is banked or unequipped. The TEMP_AMMO pair is the live source while
		 * it is worn. Reading both on every refresh makes the first loadout built after
		 * startup bank-aware without requiring an equip/unequip cycle.
		 */
		final int[] storedAmmo = readStoredExtraQuiverAmmoFromContainer();
		final int varpItemId =
			SlayerQuiverAmmo.restoreSeekingIdentityFromPlaceholders(
				normalizeQuiverAmmoDisplayItemId(
					client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)
				),
				cachedSeekingArrowPlaceholderItemIds
			);
		final int varpQuantity = client.getVarpValue(
			VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT
		);
		final int[] widgetAmmo = readLiveExtraQuiverAmmoFromWidgets();
		final SlayerQuiverAmmo.Snapshot snapshot =
			SlayerQuiverAmmo.restoreHiddenBankedSeekingAmmo(
				cachedBankDizanaVariantItemId > 0,
				SlayerQuiverAmmo.resolveSnapshot(
					wearingQuiver,
					storedAmmo == null
						? -1
						: SlayerQuiverAmmo.restoreSeekingIdentityFromPlaceholders(
							storedAmmo[0],
							cachedSeekingArrowPlaceholderItemIds
						),
					storedAmmo == null ? 0 : storedAmmo[1],
					widgetAmmo == null
						? -1
						: SlayerQuiverAmmo.restoreSeekingIdentityFromPlaceholders(
							widgetAmmo[0],
							cachedSeekingArrowPlaceholderItemIds
						),
					widgetAmmo == null ? 0 : widgetAmmo[1],
					canonicalCompatibleQuiverAmmoItemId(varpItemId),
					varpQuantity,
					cachedExtraQuiverAmmoItemId
				),
				cachedSeekingArrowPlaceholderItemIds
		);
		final int itemId = snapshot.getItemId();
		final int quantity = snapshot.getQuantity();

		final boolean identityChanged = cachedExtraQuiverAmmoItemId != itemId;
		final boolean presenceChanged =
			(cachedExtraQuiverAmmoQuantity > 0) != (quantity > 0);

		/* Always keep quantity current, but don't rebuild loadouts per shot. */
		cachedExtraQuiverAmmoItemId = itemId;
		cachedExtraQuiverAmmoQuantity = quantity;

		return identityChanged || presenceChanged;
	}

	private int canonicalCompatibleQuiverAmmoItemId(final int rawItemId)
	{
		final int itemId = canonicalizeQuiverAmmoItemId(rawItemId);
		return isQuiverCompatibleAmmoItemId(itemId) ? itemId : -1;
	}

	private int canonicalizeQuiverAmmoItemId(final int rawItemId)
	{
		/*
		 * Item IDs in the quiver path are live game IDs. Negative values are
		 * sentinels, not "signed item IDs". Never Math.abs(-1): item id 1 is
		 * Toolkit, which is exactly how the bogus Toolkit layout entry appeared.
		 */
		if (rawItemId <= 0)
		{
			return -1;
		}

		/*
		 * Seeking arrows are real, distinct ammunition items. RuneLite's general
		 * variation canonicalizer may map them to the underlying ordinary arrow,
		 * so preserve their exact family before applying generic canonicalization.
		 */
		final int normalizedRaw = normalizeQuiverAmmoDisplayItemId(rawItemId);
		if (isSeekingArrowDisplayItemId(normalizedRaw))
		{
			return normalizedRaw;
		}

		int itemId = rawItemId;
		try
		{
			if (itemManager != null)
			{
				itemId = itemManager.canonicalize(itemId);
			}
			else if (client != null)
			{
				final ItemComposition composition =
					client.getItemDefinition(itemId);
				if (composition != null)
				{
					if (composition.getNote() != -1)
					{
						itemId = composition.getLinkedNoteId();
					}
					if (composition.getPlaceholderTemplateId() != -1)
					{
						itemId = composition.getPlaceholderId();
					}
				}
			}
		}
		catch (RuntimeException ignored)
		{
			/* Keep the raw positive ID; manual Seeking normalization follows. */
		}

		return normalizeQuiverAmmoDisplayItemId(itemId);
	}

	private boolean isQuiverCompatibleAmmoItemId(final int rawItemId)
	{
		final int itemId = canonicalizeQuiverAmmoItemId(rawItemId);
		if (itemId <= 0)
		{
			return false;
		}

		/* Seeking arrows are known-good even if the item definition is transient. */
		if (isSeekingArrowDisplayItemId(itemId))
		{
			return true;
		}

		if (client == null)
		{
			return false;
		}

		try
		{
			final ItemComposition composition = client.getItemDefinition(itemId);
			if (composition == null || composition.getName() == null)
			{
				return false;
			}

			final String name = Text.removeTags(composition.getName())
				.toLowerCase(Locale.ENGLISH)
				.replace('’', '\'')
				.trim();

			if (name.contains("arrowtip") || name.contains("bolt tip"))
			{
				return false;
			}

			/*
			 * Dizana's extra compartment is ammunition-only: arrows and bolts.
			 * Mithril grapple is retained because the live game also handles that
			 * crossbow-ammo edge case through quiver state.
			 */
			return name.contains("arrow")
				|| name.contains("bolt")
				|| name.contains("grapple");
		}
		catch (RuntimeException ignored)
		{
			return false;
		}
	}

	private int[] readLiveExtraQuiverAmmoFromWidgets()
	{
		/*
		 * These are the actual item-object components for Dizana's extra ammo.
		 * Do NOT crawl EXTRA_QUIVER_SLOT or arbitrary child widgets here. Slot/
		 * decoration widgets may carry positive item IDs that are not ammunition
		 * (item id 1 is Toolkit), which previously leaked into the Bank Tag.
		 */
		final int[] itemComponents =
		{
			InterfaceID.DizanasQuiver.AMMO_OBJ,
			InterfaceID.Wornitems.EXTRA_QUIVER_AMMO,
			InterfaceID.Equipment.EXTRA_QUIVER_AMMO,
			InterfaceID.Bankmain.EXTRA_QUIVER_AMMO
		};

		int[] best = null;
		for (final int componentId : itemComponents)
		{
			best = betterQuiverWidgetCandidate(
				best,
				quiverWidgetCandidate(client.getWidget(componentId))
			);
		}
		return best;
	}

	/**
	 * Return null only when RuneLite has not published the persistent quiver
	 * container yet. An available-but-empty container returns {-1, 0}; snapshot
	 * resolution can then use the live TEMP_AMMO pair during login rather than
	 * incorrectly treating allocation as proof that the quiver itself is empty.
	 */
	private int[] readStoredExtraQuiverAmmoFromContainer()
	{
		final ItemContainer container = client.getItemContainer(
			InventoryID.DIZANAS_QUIVER_AMMO
		);
		if (container == null)
		{
			return null;
		}

		int[] best = new int[]{-1, 0};
		for (final Item item : container.getItems())
		{
			if (item == null || item.getQuantity() <= 0)
			{
				continue;
			}
			final int itemId = canonicalCompatibleQuiverAmmoItemId(item.getId());
			if (itemId <= 0)
			{
				continue;
			}

			if (best[0] <= 0
				|| SlayerQuiverAmmo.infernoPreference(itemId)
					< SlayerQuiverAmmo.infernoPreference(best[0]))
			{
				best = new int[]{itemId, item.getQuantity()};
			}
		}
		return best;
	}

	private int[] quiverWidgetCandidate(final Widget widget)
	{
		if (widget == null)
		{
			return null;
		}

		final int rawItemId = widget.getItemId();
		if (rawItemId <= 0)
		{
			return null;
		}

		final int itemId = canonicalizeQuiverAmmoItemId(rawItemId);
		if (!isQuiverCompatibleAmmoItemId(itemId))
		{
			return null;
		}

		int quantity = widget.getItemQuantity();
		if (quantity <= 0)
		{
			quantity = client.getVarpValue(
				VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT
			);
		}
		if (quantity <= 0)
		{
			return null;
		}
		return new int[]{itemId, quantity};
	}

	private static int[] betterQuiverWidgetCandidate(
		final int[] current,
		final int[] candidate)
	{
		if (candidate == null)
		{
			return current;
		}
		if (current == null)
		{
			return candidate;
		}
		final boolean candidateSeeking = isSeekingArrowDisplayItemId(candidate[0]);
		final boolean currentSeeking = isSeekingArrowDisplayItemId(current[0]);
		return candidateSeeking && !currentSeeking ? candidate : current;
	}

	private static int normalizeQuiverAmmoDisplayItemId(final int rawItemId)
	{
		return SlayerQuiverAmmo.normalizeSeekingArrowItemId(rawItemId);
	}

	private static boolean isSeekingArrowDisplayItemId(final int itemId)
	{
		return SlayerQuiverAmmo.isSeekingArrow(itemId);
	}

	private boolean isWearingUsableDizana()
	{
		final ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return false;
		}

		/* Match RuneLite's Ammo plugin: only the worn cape slot is authoritative. */
		final Item cape = equipment.getItem(
			EquipmentInventorySlot.CAPE.getSlotIdx()
		);
		return cape != null
			&& cape.getQuantity() > 0
			&& isUsableDizanaItemId(cape.getId());
	}

	private static boolean isUsableDizanaItemId(final int rawItemId)
	{
		return SlayerQuiverAmmo.isUsableDizanaVariant(rawItemId);
	}

	private static int findUsableDizanaVariantInContainer(
		final ItemContainer container)
	{
		if (container == null)
		{
			return -1;
		}

		int bestItemId = -1;
		for (final Item item : container.getItems())
		{
			if (item == null || item.getQuantity() <= 0)
			{
				continue;
			}
			bestItemId = SlayerQuiverAmmo.preferUsableDizanaVariant(
				bestItemId,
				item.getId()
			);
		}
		return bestItemId;
	}

	private void clearExtraQuiverAmmoSnapshot()
	{
		cachedExtraQuiverAmmoItemId = -1;
		cachedExtraQuiverAmmoQuantity = 0;
	}

	private boolean cacheBankContainer(final ItemContainer bankContainer)
	{
		final Widget bankItems = client == null
			? null : client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
		return cacheBankContainer(bankContainer, bankItems != null);
	}

	private boolean cacheBankContainer(
		final ItemContainer bankContainer,
		final boolean confirmedBankScan)
	{
		if (bankContainer == null || loadoutAnalyzer == null)
		{
			return false;
		}

		final boolean rawStateMatches = matchesCachedBankContainerState(
			bankContainer
		);
		if (shouldSkipUnchangedBankScanForRegression(
			bankScanned, rawStateMatches))
		{
			/*
			 * A container event may precede the bank widget. Preserve the first
			 * confirmed-open persistence pass without repeating item resolution.
			 */
			if (confirmedBankScan && !cachedRawBankContainerConfirmed)
			{
				cachedRawBankContainerConfirmed = true;
				persistInfernoTravelItemSnapshot();
				persistOwnedSlayerHelmetSnapshot();
			}
			return false;
		}
		cachedRawBankContainerState = snapshotRawBankContainerState(
			bankContainer
		);
		cachedRawBankContainerConfirmed = confirmedBankScan;

		final boolean previouslyScanned = bankScanned;
		final int previousQuiverId = cachedBankDizanaVariantItemId;
		final Map<Integer, Integer> previousBankItems =
			new LinkedHashMap<>(cachedBankItems);
		final Set<Integer> previousSeekingIds =
			new LinkedHashSet<>(cachedSeekingArrowPlaceholderItemIds);
		final int previousExtraAmmoId = cachedExtraQuiverAmmoItemId;

		/*
		 * Bank-open authority: first discover every exact Dizana variant, then
		 * collect Seeking identity hints, then read the nested ammo container.
		 * This ordering makes the first loadout built for an open bank quiver-aware.
		 */
		cachedBankDizanaVariantItemId =
			findUsableDizanaVariantInContainer(bankContainer);
		cacheSeekingArrowPlaceholders(bankContainer);
		cachedBankItems.clear();
		cachedBankItems.putAll(
			loadoutAnalyzer.snapshotItems(bankContainer)
		);
		bankScanned = true;
		if (confirmedBankScan)
		{
			persistInfernoTravelItemSnapshot();
			persistOwnedSlayerHelmetSnapshot();
		}
		final boolean extraAmmoChanged = refreshExtraQuiverAmmoSnapshot();
		return !previouslyScanned
			|| previousQuiverId != cachedBankDizanaVariantItemId
			|| !previousBankItems.equals(cachedBankItems)
			|| !previousSeekingIds.equals(cachedSeekingArrowPlaceholderItemIds)
			|| previousExtraAmmoId != cachedExtraQuiverAmmoItemId
			|| extraAmmoChanged;
	}

	private boolean matchesCachedBankContainerState(
		final ItemContainer bankContainer)
	{
		final Item[] items = bankContainer.getItems();
		if (items == null
			|| cachedRawBankContainerState.length != items.length * 2)
		{
			return false;
		}
		for (int slot = 0; slot < items.length; slot++)
		{
			final Item item = items[slot];
			final int offset = slot * 2;
			final int itemId = item == null ? -1 : item.getId();
			final int quantity = item == null ? 0 : item.getQuantity();
			if (cachedRawBankContainerState[offset] != itemId
				|| cachedRawBankContainerState[offset + 1] != quantity)
			{
				return false;
			}
		}
		return true;
	}

	private static int[] snapshotRawBankContainerState(
		final ItemContainer bankContainer)
	{
		final Item[] items = bankContainer.getItems();
		if (items == null || items.length == 0)
		{
			return new int[0];
		}
		final int[] state = new int[items.length * 2];
		for (int slot = 0; slot < items.length; slot++)
		{
			final Item item = items[slot];
			final int offset = slot * 2;
			state[offset] = item == null ? -1 : item.getId();
			state[offset + 1] = item == null ? 0 : item.getQuantity();
		}
		return state;
	}

	static boolean shouldSkipUnchangedBankScanForRegression(
		final boolean bankScanned,
		final boolean rawStateMatches)
	{
		return bankScanned && rawStateMatches;
	}

	/**
	 * Stores only Slayer-helmet identities in the account's RuneLite profile.
	 * This snapshot populates Settings after login but is never merged into bank
	 * ownership, so an old value cannot make a loadout claim an unavailable item.
	 */
	private void persistOwnedSlayerHelmetSnapshot()
	{
		final Set<Integer> discovered =
			loadoutAnalyzer.ownedSlayerHelmetItemIds(
				client.getItemContainer(InventoryID.INV),
				client.getItemContainer(InventoryID.WORN),
				cachedBankItems
			);
		final boolean snapshotChanged = shouldPersistSnapshotForRegression(
			profileOwnedSlayerHelmetSnapshotLoaded,
			profileOwnedSlayerHelmetItemIds,
			discovered
		);
		profileOwnedSlayerHelmetItemIds.clear();
		profileOwnedSlayerHelmetItemIds.addAll(discovered);
		profileOwnedSlayerHelmetSnapshotLoaded = true;

		try
		{
			if (configManager.getRSProfileKey() == null)
			{
				return;
			}
			if (!snapshotChanged)
			{
				return;
			}
			configManager.setRSProfileConfiguration(
				SlayerPlusConfig.GROUP,
				OWNED_SLAYER_HELMETS_SNAPSHOT_KEY,
				serializeItemIds(discovered)
			);
			log.debug("Saved {} owned Slayer helmet variants", discovered.size());
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to save owned Slayer helmet snapshot", ex);
		}
	}

	private void loadOwnedSlayerHelmetSnapshot()
	{
		profileOwnedSlayerHelmetItemIds.clear();
		profileOwnedSlayerHelmetSnapshotLoaded = false;
		try
		{
			if (configManager.getRSProfileKey() == null)
			{
				return;
			}
			profileOwnedSlayerHelmetItemIds.addAll(parseItemIds(
				configManager.getRSProfileConfiguration(
					SlayerPlusConfig.GROUP,
					OWNED_SLAYER_HELMETS_SNAPSHOT_KEY
				)
			));
			profileOwnedSlayerHelmetSnapshotLoaded = true;
			log.debug(
				"Loaded {} owned Slayer helmet variants",
				profileOwnedSlayerHelmetItemIds.size()
			);
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to load owned Slayer helmet snapshot", ex);
		}
	}

	static String serializeItemIds(final Iterable<Integer> itemIds)
	{
		final StringBuilder serialized = new StringBuilder();
		if (itemIds != null)
		{
			for (final Integer itemId : itemIds)
			{
				if (itemId == null || itemId <= 0)
				{
					continue;
				}
				if (serialized.length() > 0)
				{
					serialized.append(',');
				}
				serialized.append(itemId);
			}
		}
		return serialized.toString();
	}

	static Set<Integer> parseItemIds(final String serialized)
	{
		final Set<Integer> itemIds = new LinkedHashSet<>();
		if (serialized == null || serialized.trim().isEmpty())
		{
			return itemIds;
		}
		for (final String token : serialized.split(","))
		{
			try
			{
				final int itemId = Integer.parseInt(token.trim());
				if (itemId > 0)
				{
					itemIds.add(itemId);
				}
			}
			catch (NumberFormatException ignored)
			{
				log.debug(
					"Ignoring invalid Slayer helmet snapshot item id: {}",
					token
				);
			}
		}
		return itemIds;
	}

	/**
	 * Persist only the small, reviewed Inferno staging-item fact instead of the
	 * whole bank. This lets a clean client start choose the correct branch before
	 * the bank is opened, while every later real bank scan can also authoritatively
	 * replace the value with another supported variant or with "none".
	 */
	private void persistInfernoTravelItemSnapshot()
	{
		final int bestItemId = preferredInfernoTravelItemId(
			cachedBankItems.keySet()
		);
		final int nextItemId = Math.max(0, bestItemId);
		final boolean snapshotChanged = shouldPersistSnapshotForRegression(
			profileInfernoTravelSnapshotLoaded,
			profileInfernoTravelItemId,
			nextItemId
		);
		profileInfernoTravelItemId = nextItemId;
		profileInfernoTravelSnapshotLoaded = true;

		try
		{
			if (configManager.getRSProfileKey() == null)
			{
				profileInfernoTravelSnapshotLoaded = false;
				return;
			}
			if (!snapshotChanged)
			{
				return;
			}
			configManager.setRSProfileConfiguration(
				SlayerPlusConfig.GROUP,
				INFERNO_TRAVEL_SNAPSHOT_KEY,
				profileInfernoTravelItemId
			);
			log.debug(
				"Saved Inferno travel-item bank snapshot: {}",
				profileInfernoTravelItemId
			);
		}
		catch (RuntimeException ex)
		{
			profileInfernoTravelSnapshotLoaded = false;
			log.debug("Unable to save Inferno travel-item bank snapshot", ex);
		}
	}

	static boolean shouldPersistSnapshotForRegression(
		final boolean snapshotLoaded,
		final Object previousValue,
		final Object nextValue)
	{
		return !snapshotLoaded
			|| !java.util.Objects.equals(previousValue, nextValue);
	}

	private void loadInfernoTravelItemSnapshot()
	{
		profileInfernoTravelItemId = -1;
		profileInfernoTravelSnapshotLoaded = false;
		try
		{
			if (configManager.getRSProfileKey() == null)
			{
				return;
			}

			final String stored = configManager.getRSProfileConfiguration(
				SlayerPlusConfig.GROUP,
				INFERNO_TRAVEL_SNAPSHOT_KEY
			);
			if (stored != null)
			{
				final int storedItemId = Integer.parseInt(stored.trim());
				profileInfernoTravelItemId = storedItemId == 0
					|| isInfernoPreparationTravelItemId(storedItemId)
					? storedItemId : 0;
				profileInfernoTravelSnapshotLoaded = true;
				log.debug(
					"Loaded Inferno travel-item bank snapshot: {}",
					profileInfernoTravelItemId
				);
				return;
			}

			/*
			 * Upgrade path for existing installs: the generated layout records the
			 * exact physical Ghommal item previously selected from a live bank. This
			 * migration runs only while the new profile key is genuinely absent.
			 */
			final int migratedItemId = infernoTravelItemFromSavedLayout();
			if (migratedItemId > 0)
			{
				profileInfernoTravelItemId = migratedItemId;
				configManager.setRSProfileConfiguration(
					SlayerPlusConfig.GROUP,
					INFERNO_TRAVEL_SNAPSHOT_KEY,
					migratedItemId
				);
				log.debug(
					"Migrated Inferno travel-item snapshot from SlayerPlus layout: {}",
					migratedItemId
				);
			}
			else
			{
				profileInfernoTravelItemId = 0;
				configManager.setRSProfileConfiguration(
					SlayerPlusConfig.GROUP,
					INFERNO_TRAVEL_SNAPSHOT_KEY,
					0
				);
			}
			profileInfernoTravelSnapshotLoaded = true;
		}
		catch (NumberFormatException ex)
		{
			profileInfernoTravelItemId = 0;
			profileInfernoTravelSnapshotLoaded = true;
			log.debug("Ignoring invalid Inferno travel-item bank snapshot", ex);
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to load Inferno travel-item bank snapshot", ex);
		}
	}

	private int infernoTravelItemFromSavedLayout()
	{
		if (layoutManager == null)
		{
			return -1;
		}
		final Layout layout = layoutManager.loadLayout(
			SlayerBankTagLayoutService.TAG_NAME
		);
		if (layout == null || layout.getLayout() == null)
		{
			return -1;
		}

		final Set<Integer> layoutItemIds = new LinkedHashSet<>();
		for (final int itemId : layout.getLayout())
		{
			if (itemId > 0)
			{
				layoutItemIds.add(itemId);
			}
		}
		return preferredInfernoTravelItemId(layoutItemIds);
	}

	static int preferredInfernoTravelItemId(
		final Iterable<Integer> candidateItemIds)
	{
		if (candidateItemIds == null)
		{
			return -1;
		}
		final Set<Integer> candidates = new HashSet<>();
		for (final Integer itemId : candidateItemIds)
		{
			if (itemId != null)
			{
				candidates.add(itemId);
			}
		}
		for (final int supportedItemId : INFERNO_PREPARATION_TRAVEL_ITEM_IDS)
		{
			if (candidates.contains(supportedItemId))
			{
				return supportedItemId;
			}
		}
		return -1;
	}

	private static boolean isInfernoPreparationTravelItemId(final int itemId)
	{
		for (final int supportedItemId : INFERNO_PREPARATION_TRAVEL_ITEM_IDS)
		{
			if (itemId == supportedItemId)
			{
				return true;
			}
		}
		return false;
	}

	private void cacheSeekingArrowPlaceholders(
		final ItemContainer bankContainer)
	{
		cachedSeekingArrowPlaceholderItemIds.clear();
		if (bankContainer == null || itemManager == null)
		{
			cacheSeekingArrowBankTagIdentityHints();
			return;
		}

		for (final Item item : bankContainer.getItems())
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}

			final Integer cachedClassification =
				seekingPlaceholderClassificationCache.get(item.getId());
			if (cachedClassification != null)
			{
				if (cachedClassification > 0)
				{
					cachedSeekingArrowPlaceholderItemIds.add(
						cachedClassification
					);
				}
				continue;
			}

			int classification = -1;
			try
			{
				final ItemComposition composition =
					itemManager.getItemComposition(item.getId());
				if (composition != null
					&& composition.getPlaceholderTemplateId() >= 0
					&& composition.getPlaceholderId() >= 0)
				{
					final int canonicalItemId =
						normalizeQuiverAmmoDisplayItemId(
							itemManager.canonicalize(item.getId())
						);
					if (isSeekingArrowDisplayItemId(canonicalItemId))
					{
						classification = canonicalItemId;
					}
				}
			}
			catch (RuntimeException ignored)
			{
				/* A transient definition must not make the bank scan fail. */
			}
			seekingPlaceholderClassificationCache.put(
				item.getId(), classification
			);
			if (classification > 0)
			{
				cachedSeekingArrowPlaceholderItemIds.add(classification);
			}
		}

		cacheSeekingArrowBankTagIdentityHints();
	}

	/**
	 * Accept an exact Seeking-arrow entry already used by a Bank Tags gear tab as
	 * an identity hint for a matching, populated quiver stack. This covers the
	 * login state where Jagex exposes the nested stack as its ordinary arrow ID
	 * and the user does not retain zero-quantity bank placeholders.
	 */
	private void cacheSeekingArrowBankTagIdentityHints()
	{
		if (configManager == null)
		{
			return;
		}

		final String prefix = BankTagsPlugin.CONFIG_GROUP + ".item_";
		for (final String key : configManager.getConfigurationKeys(prefix))
		{
			if (key == null || !key.startsWith(prefix))
			{
				continue;
			}

			try
			{
				/* Negative keys represent generic variation tags, not exact items. */
				final int taggedItemId = Integer.parseInt(
					key.substring(prefix.length())
				);
				if (taggedItemId <= 0)
				{
					continue;
				}

				final int normalizedItemId =
					normalizeQuiverAmmoDisplayItemId(taggedItemId);
				if (isSeekingArrowDisplayItemId(normalizedItemId))
				{
					cachedSeekingArrowPlaceholderItemIds.add(
						normalizedItemId
					);
				}
			}
			catch (NumberFormatException ignored)
			{
				/* Ignore non-item Bank Tags configuration keys. */
			}
		}
	}

	private TravelInstruction findTravelInstructionFromTransportInfo(
		final Object rawDisplayInfos)
	{
		final Iterable<?> displayInfos;
		if (rawDisplayInfos instanceof Iterable<?>)
		{
			displayInfos = (Iterable<?>) rawDisplayInfos;
		}
		else if (rawDisplayInfos instanceof Object[])
		{
			displayInfos = Arrays.asList((Object[]) rawDisplayInfos);
		}
		else if (rawDisplayInfos instanceof String)
		{
			displayInfos = java.util.Collections.singletonList(rawDisplayInfos);
		}
		else
		{
			return null;
		}

		for (final Object value : displayInfos)
		{
			if (!(value instanceof String))
			{
				continue;
			}
			final String displayInfo = cleanTransportText((String) value);
			if (displayInfo.isEmpty())
			{
				continue;
			}
			final int separator = displayInfo.indexOf(':');
			final String family = separator >= 0
				? displayInfo.substring(0, separator).trim()
				: displayInfo;
			final String destination = separator >= 0
				? displayInfo.substring(separator + 1).trim()
				: "";
			if (!family.isEmpty())
			{
				return new TravelInstruction(family, destination);
			}
		}
		return null;
	}

	private static String cleanTransportText(final String value)
	{
		if (value == null)
		{
			return "";
		}
		return Text.removeTags(
			value.replaceAll("(?i)<br\\s*/?>", " ")
		).trim();
	}

	private static String travelInstructionDisplay(
		final String family,
		final String destination)
	{
		final String safeFamily = family == null ? "" : family.trim();
		final String safeDestination = destination == null
			? ""
			: destination.trim();
		if (safeFamily.isEmpty())
		{
			return safeDestination;
		}
		return safeDestination.isEmpty()
			? safeFamily
			: safeFamily + " \u2192 " + safeDestination;
	}

	private TravelRouteMatch findTravelItemFromTransportInfo(
		final Map<String, Object> data,
		final boolean carriedOnly)
	{
		if (data == null)
		{
			return null;
		}

		final List<?> displayInfos = postedValues(data.get("displayInfo"));
		if (displayInfos.isEmpty())
		{
			return null;
		}
		final List<?> origins = postedValues(data.get("origin"));
		final List<?> destinations = postedValues(data.get("destination"));

		/*
		 * Current Shortest Path posts every transport candidate for each rendered
		 * path edge. Several interchangeable items can therefore share one edge;
		 * iteration order is not an authoritative item choice. Work one edge at a
		 * time and deterministically select exactly one owned candidate from the
		 * first edge that actually needs a withdrawable travel item.
		 */
		final List<TravelRouteCandidate> edgeCandidates = new ArrayList<>();
		Object activeOrigin = null;
		Object activeDestination = null;
		boolean activeEdge = false;

		for (int index = 0; index < displayInfos.size(); index++)
		{
			final Object value = displayInfos.get(index);
			if (!(value instanceof String))
			{
				continue;
			}

			final String displayInfo = cleanTransportText((String) value);
			if (displayInfo.isEmpty())
			{
				continue;
			}

			final Object origin = index < origins.size()
				? origins.get(index)
				: null;
			final Object edgeDestination = index < destinations.size()
				? destinations.get(index)
				: null;

			if (activeEdge
				&& !sameTransportEdge(
					activeOrigin,
					activeDestination,
					origin,
					edgeDestination
				))
			{
				final TravelRouteMatch selected =
					selectTravelCandidate(edgeCandidates, carriedOnly);
				if (selected != null)
				{
					return selected;
				}
				edgeCandidates.clear();
				activeEdge = false;
			}

			final int separator = displayInfo.indexOf(':');
			final String family = separator >= 0
				? displayInfo.substring(0, separator).trim()
				: displayInfo;
			final String destination = separator >= 0
				? displayInfo.substring(separator + 1).trim()
				: "";
			final TravelItemMatch item = carriedOnly
				? findCarriedTravelItemForTransport(family)
				: findOwnedTravelItemForTransport(family);
			if (item == null)
			{
				continue;
			}

			if (!activeEdge)
			{
				activeOrigin = origin;
				activeDestination = edgeDestination;
				activeEdge = true;
			}
			edgeCandidates.add(
				new TravelRouteCandidate(family, item, destination)
			);
		}

		return selectTravelCandidate(edgeCandidates, carriedOnly);
	}

	private static List<?> postedValues(final Object value)
	{
		if (value instanceof List<?>)
		{
			return (List<?>) value;
		}
		if (value instanceof Object[])
		{
			return Arrays.asList((Object[]) value);
		}
		if (value == null)
		{
			return java.util.Collections.emptyList();
		}
		return java.util.Collections.singletonList(value);
	}

	private static boolean sameTransportEdge(
		final Object firstOrigin,
		final Object firstDestination,
		final Object secondOrigin,
		final Object secondDestination)
	{
		/*
		 * Older Shortest Path builds may omit origin/destination arrays. In that
		 * compatibility case treat the posted candidates as one group and rely on
		 * the same deterministic tie-breaker rather than arbitrary iteration order.
		 */
		if (firstOrigin == null
			&& firstDestination == null
			&& secondOrigin == null
			&& secondDestination == null)
		{
			return true;
		}
		return samePostedValue(firstOrigin, secondOrigin)
			&& samePostedValue(firstDestination, secondDestination);
	}

	private static boolean samePostedValue(
		final Object first,
		final Object second)
	{
		return first == second
			|| (first != null && first.equals(second));
	}

	private boolean isTravelCandidateAllowedForTarget(
		final GuidedTaskTarget target,
		final TravelRouteCandidate candidate)
	{
		return target != null
			&& candidate != null
			&& achievementDiaries.allowsTravelItem(candidate.family)
			&& SlayerTravelRouteCatalog.allowsLiveCandidate(
				target.location,
				candidate.family,
				candidate.destination
			);
	}

	private TravelRouteMatch selectTravelCandidate(
		final List<TravelRouteCandidate> candidates,
		final boolean masterReturnRoute)
	{
		if (candidates == null || candidates.isEmpty())
		{
			return null;
		}

		final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
		TravelRouteCandidate best = null;
		int bestPreference = Integer.MIN_VALUE;
		int bestItemScore = Integer.MIN_VALUE;
		String bestStableKey = "";
		final Set<String> equivalentItemFamilies = new LinkedHashSet<>();

		/*
		 * GLOBAL SHORTEST-PATH EDGE AUTHORITY:
		 * Shortest Path publishes every valid transport for one used path edge.
		 * A HashSet/order from that callback is not an exact-item choice. Resolve
		 * exactly one owned representative deterministically, then share that same
		 * selection with the panel, highlighter, and authoritative Bank Tag travel cell.
		 */
		for (final TravelRouteCandidate candidate : candidates)
		{
			if (candidate == null || candidate.item == null
				|| shouldRejectTravelItemForSpellbookRouteForRegression(
					guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_SPELLBOOK,
					candidate.family
				)
				|| (!masterReturnRoute
					&& !isTravelCandidateAllowedForTarget(target, candidate)))
			{
				continue;
			}

			/*
			 * Preserve every owned physical alternative Shortest Path exposed for this
			 * exact edge. The winner is authoritative; the remaining families are used
			 * only to suppress duplicate/equivalent teleports from the 4 x 7 layout.
			 */
			equivalentItemFamilies.add(candidate.family);

			final int preference = SlayerTravelRouteCatalog.liveCandidatePreference(
				target == null ? "" : target.location,
				candidate.family
			);
			final int itemScore = candidate.item.score;
			final String stableKey = normalizeTravelName(candidate.family)
				+ "|" + normalizeTravelName(candidate.item.displayName)
				+ "|" + candidate.item.itemId;

			if (best == null
				|| preference > bestPreference
				|| (preference == bestPreference && itemScore > bestItemScore)
				|| (preference == bestPreference
					&& itemScore == bestItemScore
					&& stableKey.compareTo(bestStableKey) < 0))
			{
				best = candidate;
				bestPreference = preference;
				bestItemScore = itemScore;
				bestStableKey = stableKey;
			}
		}

		return best == null
			? null
			: new TravelRouteMatch(
				best.item,
				best.destination,
				equivalentItemFamilies
			);
	}

	static boolean shouldRejectTravelItemForSpellbookRouteForRegression(
		final boolean spellbookRoute,
		final String itemFamily)
	{
		/* Hallowed crystal shards are never an authored SlayerPlus transport.
		 * Keep the legacy parameter so existing callers remain source compatible
		 * while enforcing the prohibition during every route phase. */
		return normalizeTravelName(itemFamily).contains("hallowed crystal shard");
	}

	private static String travelItemFamilyName(final String value)
	{
		return normalizeTravelName(value)
			.replaceFirst("\\s+[a-z]?\\d+$", "")
			.trim();
	}

	private TravelItemMatch findOwnedTravelItemForTransport(
		final String family)
	{
		final String normalizedFamily = normalizeTravelName(family);
		if (normalizedFamily.isEmpty())
		{
			return null;
		}

		/*
		 * Never infer an inventory cape from a generic POH/house transition.
		 * Only an item family actually posted by Shortest Path may become the
		 * physical travel item.
		 */
		if (normalizedFamily.equals("max cape")
			|| normalizedFamily.contains("max cape teleport"))
		{
			final TravelItemMatch cape = findOwnedTravelItem("max cape");
			return cape != null
				&& travelItemFamilyName(cape.displayName).equals("max cape")
					? cape
					: null;
		}

		if (normalizedFamily.contains("construction cape")
			|| normalizedFamily.contains("construct cape"))
		{
			return findOwnedTravelItem("construct cape");
		}

		return findOwnedTravelItem(family);
	}

	private TravelItemMatch findCarriedTravelItemForTransport(
		final String family)
	{
		final String normalizedFamily = normalizeTravelName(family);
		if (normalizedFamily.isEmpty())
		{
			return null;
		}

		if (normalizedFamily.equals("max cape")
			|| normalizedFamily.contains("max cape teleport"))
		{
			final TravelItemMatch cape = findCarriedTravelItem("max cape");
			return cape != null
				&& travelItemFamilyName(cape.displayName).equals("max cape")
					? cape
					: null;
		}

		if (normalizedFamily.contains("construction cape")
			|| normalizedFamily.contains("construct cape"))
		{
			return findCarriedTravelItem("construct cape");
		}

		return findCarriedTravelItem(family);
	}


	private SlayerTravelSelection currentTravelSelection()
	{
		/*
		 * GLOBAL CONSUMER ISOLATION:
		 * UI consumers may only observe travel owned by the currently resolved
		 * recommendation profile. Even if a future lifecycle path forgets to clear
		 * the coordinator first, the highlighter/panel cannot inherit the previous
		 * encounter's physical teleport.
		 */
		final String expectedIdentity = activeTravelConsumerIdentity();
		return expectedIdentity == null || expectedIdentity.isEmpty()
			? SlayerTravelSelection.unresolved(
				"",
				travelCoordinator.getGeneration()
			)
			: travelCoordinator.currentFor(expectedIdentity);
	}

	private SlayerTravelSelection currentTravelSelectionForHighlight()
	{
		/* Reaching the encounter is a hard UI boundary. Even if a delayed
		 * Shortest Path callback publishes the previous Achievement-cape edge,
		 * combat must never repaint Jarr (or any other teleport) green. */
		if (!shouldExposeTravelHighlightForRegression(
			guidedSessionActive,
			guidedSessionPhase == GuidedSessionPhase.TASKING
		))
		{
			return SlayerTravelSelection.unresolved(
				"",
				travelCoordinator.getGeneration()
			);
		}
		return currentTravelSelection();
	}

	static boolean shouldExposeTravelHighlightForRegression(
		final boolean sessionActive,
		final boolean taskAreaConfirmed)
	{
		return !sessionActive || !taskAreaConfirmed;
	}

	private String activeTravelConsumerIdentity()
	{
		/*
		 * During a post-task master return there is deliberately no active Slayer
		 * encounter profile. The exact in-flight request identity is authoritative
		 * while routing to a master, preparation bank, or task. In particular, a task
		 * target can be re-resolved after Shortest Path has already selected its first
		 * transport; using that later display identity made the menu highlighter see an
		 * unresolved selection even though the panel still correctly showed the Max
		 * cape (or another physical teleport) chosen for the live request.
		 */
		if (guidedSessionActive
			&& (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER
				|| guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK
				|| guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_TASK)
			&& guidedTravelRequestRouteIdentity != null
			&& !guidedTravelRequestRouteIdentity.isEmpty()
			&& travelCoordinator.ownsCurrentRoute(
				guidedTravelRequestRouteIdentity
			))
		{
			return guidedTravelRequestRouteIdentity;
		}
		return lastResolvedRoutingProfileIdentity;
	}

	private void applyTravelRouteMatch(
		final TravelRouteMatch match,
		final String identity)
	{
		if (match == null || match.item == null)
		{
			return;
		}

		if (identity.isEmpty() || !travelCoordinator.ownsCurrentRoute(identity))
		{
			return;
		}

		final String menuDestination = SlayerTeleportRouteRegistry.menuDestination(
			match.item.displayName,
			match.destination
		);
		final SlayerTravelSelection selection = travelCoordinator.resolveItem(
			identity,
			match.item.itemId,
			match.item.displayName,
			menuDestination,
			match.equivalentItemFamilies
		);

		final boolean infernoPrepRoute =
			guidedSessionActive
				&& guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK
				&& identity.equals(guidedTravelRequestRouteIdentity);
		final String slotText = infernoPrepRoute
			? "the staging strip above the 4 x 7 until the Inferno bank is opened"
			: "Bank Tag slot 1";
		final String note = selection.getDestination().isEmpty()
			? "Using this owned item for Shortest Path's selected route and including it when the Bank Tag Layout is generated. The item is outlined in green."
			: "Using this owned item for Shortest Path's selected route and including it in "
				+ slotText + ". The item and exact live teleport menu path are highlighted in green.";
		publishTravelSelection(selection, note);
	}

	private void applyProvisionalTravelRouteMatch(
		final TravelRouteMatch match,
		final GuidedTaskTarget target)
	{
		if (target == null)
		{
			return;
		}
		applyProvisionalTravelRouteMatch(match, routeIdentity(target));
	}

	private void applyProvisionalTravelRouteMatch(
		final TravelRouteMatch match,
		final String identity)
	{
		if (match == null || match.item == null)
		{
			return;
		}

		if (identity == null
			|| identity.isEmpty()
			|| !travelCoordinator.ownsCurrentRoute(identity))
		{
			return;
		}
		final String menuDestination = SlayerTeleportRouteRegistry.menuDestination(
			match.item.displayName,
			match.destination
		);
		final SlayerTravelSelection selection = travelCoordinator.seedProvisionalItem(
			identity,
			match.item.itemId,
			match.item.displayName,
			menuDestination,
			match.equivalentItemFamilies
		);
		if (!selection.isProvisional())
		{
			return;
		}

		publishTravelSelection(
			selection,
			"Temporary route-specific travel item while Shortest Path finishes calculating. A live Shortest Path selection always replaces it."
		);
	}

	/**
	 * Exact authored physical travel for routes whose first leg is known and
	 * location-specific. This is deliberately not a generic fallback hierarchy:
	 * every item returned here is valid only for the selected exact location.
	 * Shortest Path may still replace it with another verified candidate for the
	 * same route when its transport callback arrives.
	 */
	private TravelRouteMatch findAuthoredRouteTravelItem(
		final GuidedTaskTarget target)
	{
		return target == null
			? null
			: findAuthoredRouteTravelItem(target.location);
	}

	private TravelRouteMatch findAuthoredRouteTravelItem(
		final String location)
	{
		if (location == null || location.trim().isEmpty())
		{
			return null;
		}

		for (final SlayerTravelRouteCatalog.Option option
			: SlayerTravelRouteCatalog.fallbacksFor(location))
		{
			if (shouldRejectTravelItemForSpellbookRouteForRegression(
				false, option.getItemFamily()
			))
			{
				continue;
			}
			/* The Prifddinas/Gwenith branch is invalid until its rowboat is built. */
			if ("ynysdail cavern".equals(normalizeTravelName(location))
				&& !hasBuiltYnysdailRowboat()
				&& !normalizeTravelName(option.getItemFamily()).contains("sailor s amulet"))
			{
				continue;
			}
			if (!achievementDiaries.allowsTravelItem(option.getItemFamily()))
			{
				continue;
			}
			final TravelItemMatch item = findOwnedTravelItem(option.getItemFamily());
			if (item != null)
			{
				final Set<String> families = new LinkedHashSet<>();
				families.add(option.getItemFamily());
				return new TravelRouteMatch(
					item,
					option.getDestination(),
					families
				);
			}
		}
		return null;
	}

	private TravelRouteMatch findInfernoPreparationTravelItem()
	{
		/* Prefer something visible now in bank/inventory/equipment. */
		for (int index = 0; index < INFERNO_PREPARATION_TRAVEL_ITEM_IDS.length;
			index++)
		{
			final int itemId = INFERNO_PREPARATION_TRAVEL_ITEM_IDS[index];
			if (!isTravelItemStillOwned(itemId))
			{
				continue;
			}

			try
			{
				final ItemComposition composition =
					itemManager.getItemComposition(itemId);
				if (composition == null
					|| composition.getName() == null
					|| composition.getName().trim().isEmpty())
				{
					continue;
				}

				log.debug(
					"Inferno preparation selected currently observed Ghommal item {}",
					itemId
				);
				return infernoPreparationTravelMatch(itemId, index, composition);
			}
			catch (RuntimeException ignored)
			{
				/* Continue to the next reviewed variant. */
			}
		}

		/*
		 * A clean restart has no BANK container until the player opens the bank.
		 * Use the account-scoped last confirmed item only in that unknown state.
		 * Once any bank snapshot is available, its presence/absence wins.
		 */
		if (!bankScanned && !profileInfernoTravelSnapshotLoaded)
		{
			loadInfernoTravelItemSnapshot();
		}
		if (!bankScanned && profileInfernoTravelItemId > 0)
		{
			for (int index = 0;
				index < INFERNO_PREPARATION_TRAVEL_ITEM_IDS.length;
				index++)
			{
				final int itemId = INFERNO_PREPARATION_TRAVEL_ITEM_IDS[index];
				if (itemId != profileInfernoTravelItemId)
				{
					continue;
				}
				try
				{
					final ItemComposition composition =
						itemManager.getItemComposition(itemId);
					if (composition == null
						|| composition.getName() == null
						|| composition.getName().trim().isEmpty())
					{
						break;
					}
					log.debug(
						"Inferno preparation selected profile bank-snapshot Ghommal item {}",
						itemId
					);
					return infernoPreparationTravelMatch(
						itemId,
						index,
						composition
					);
				}
				catch (RuntimeException ex)
				{
					log.debug(
						"Unable to resolve remembered Inferno travel item {}",
						itemId,
						ex
					);
				}
				break;
			}
		}

		/* Preserve name-family matching for compatible future RuneLite variants. */
		final TravelRouteMatch compatible =
			findAuthoredRouteTravelItem(INFERNO_PREP_TRAVEL_LOCATION);
		if (compatible == null)
		{
			log.debug(
				"Inferno preparation found no Ghommal item; selecting the Hot vent branch"
			);
		}
		return compatible;
	}

	private static TravelRouteMatch infernoPreparationTravelMatch(
		final int itemId,
		final int preferenceIndex,
		final ItemComposition composition)
	{
		final String displayName = composition.getName().trim();
		return new TravelRouteMatch(
			new TravelItemMatch(
				itemId,
				displayName,
				10_000 - preferenceIndex
			),
			"Mor Ul Rek",
			java.util.Collections.singleton(displayName)
		);
	}

	private TravelItemMatch findOwnedTravelItem(final String family)
	{
		final String normalizedFamily = normalizeTravelName(family);
		if (normalizedFamily.isEmpty())
		{
			return null;
		}

		TravelItemMatch best = null;
		for (final Map.Entry<Integer, Integer> entry
			: cachedBankItems.entrySet())
		{
			if (entry.getKey() == null
				|| entry.getKey() <= 0
				|| entry.getValue() == null
				|| entry.getValue() <= 0)
			{
				continue;
			}
			best = betterTravelItem(
				best,
				matchTravelItem(
					entry.getKey(), normalizedFamily, TRAVEL_BANK_SOURCE_SCORE
				)
			);
		}

		best = findOwnedTravelItemInContainer(
			client.getItemContainer(InventoryID.INV),
			normalizedFamily,
			TRAVEL_INVENTORY_SOURCE_SCORE,
			best
		);
		best = findOwnedTravelItemInContainer(
			client.getItemContainer(InventoryID.WORN),
			normalizedFamily,
			TRAVEL_WORN_SOURCE_SCORE,
			best
		);
		return best;
	}

	private TravelItemMatch findBankTravelItem(final String family)
	{
		final String normalizedFamily = normalizeTravelName(family);
		if (normalizedFamily.isEmpty())
		{
			return null;
		}

		TravelItemMatch best = null;
		for (final Map.Entry<Integer, Integer> entry
			: cachedBankItems.entrySet())
		{
			if (entry.getKey() == null
				|| entry.getKey() <= 0
				|| entry.getValue() == null
				|| entry.getValue() <= 0)
			{
				continue;
			}
			best = betterTravelItem(
				best,
				matchTravelItem(
					entry.getKey(),
					normalizedFamily,
					TRAVEL_BANK_SOURCE_SCORE
				)
			);
		}
		return best;
	}

	static boolean carriedTravelOutranksBankForRegression()
	{
		return TRAVEL_INVENTORY_SOURCE_SCORE > TRAVEL_BANK_SOURCE_SCORE
			&& TRAVEL_WORN_SOURCE_SCORE > TRAVEL_BANK_SOURCE_SCORE;
	}

	private TravelItemMatch findCarriedTravelItem(final String family)
	{
		final String normalizedFamily = normalizeTravelName(family);
		if (normalizedFamily.isEmpty())
		{
			return null;
		}

		TravelItemMatch best = findOwnedTravelItemInContainer(
			client.getItemContainer(InventoryID.INV),
			normalizedFamily,
			2000,
			null
		);
		best = findOwnedTravelItemInContainer(
			client.getItemContainer(InventoryID.WORN),
			normalizedFamily,
			1900,
			best
		);
		return best;
	}

	private TravelItemMatch findOwnedTravelItemInContainer(
		final ItemContainer container,
		final String normalizedFamily,
		final int sourceBonus,
		final TravelItemMatch currentBest)
	{
		TravelItemMatch best = currentBest;
		if (container == null)
		{
			return best;
		}

		for (final Item item : container.getItems())
		{
			if (item == null
				|| item.getId() <= 0
				|| item.getQuantity() <= 0)
			{
				continue;
			}

			best = betterTravelItem(
				best,
				matchTravelItem(
					item.getId(),
					normalizedFamily,
					sourceBonus
				)
			);
		}
		return best;
	}

	private TravelItemMatch matchTravelItem(
		final int itemId,
		final String normalizedFamily,
		final int sourceBonus)
	{
		if (itemManager == null || itemId <= 0)
		{
			return null;
		}

		final ItemComposition composition;
		try
		{
			composition = itemManager.getItemComposition(itemId);
		}
		catch (RuntimeException ignored)
		{
			return null;
		}

		if (composition == null
			|| composition.getName() == null
			|| composition.getName().trim().isEmpty()
			|| "null".equalsIgnoreCase(composition.getName().trim()))
		{
			return null;
		}

		final String displayName = composition.getName().trim();
		final String normalizedItem = normalizeTravelName(displayName);

		/*
		 * GLOBAL USABLE-TRAVEL-ITEM RULE:
		 * Ownership alone is insufficient for an item whose current state cannot
		 * teleport. Never let inert/uncharged/depleted forms become Shortest Path
		 * travel authority or occupy the authoritative Bank Tag travel cell. The route resolver must
		 * continue to another exact valid transport instead.
		 */
		if (!SlayerTravelItemPolicy.isUsableDisplayName(displayName))
		{
			return null;
		}
		final String withoutTablet = normalizedFamily.endsWith(" tablet")
			? normalizedFamily.substring(
				0,
				normalizedFamily.length() - " tablet".length()
			).trim()
			: normalizedFamily;
		final int quality = travelNameMatchQuality(
			normalizedItem,
			normalizedFamily,
			withoutTablet
		);
		if (quality <= 0)
		{
			return null;
		}

		return new TravelItemMatch(
			itemId,
			displayName,
			sourceBonus + quality + largestNumber(normalizedItem)
		);
	}

	private static int travelNameMatchQuality(
		final String itemName,
		final String family,
		final String alternateFamily)
	{
		int quality = travelNameMatchQuality(itemName, family);
		if (!alternateFamily.equals(family))
		{
			quality = Math.max(
				quality,
				travelNameMatchQuality(itemName, alternateFamily)
			);
		}
		return quality;
	}

	private static int travelNameMatchQuality(
		final String itemName,
		final String family)
	{
		if (itemName.isEmpty() || family.isEmpty())
		{
			return 0;
		}
		if (itemName.equals(family))
		{
			return 1000;
		}
		if (itemName.startsWith(family + " "))
		{
			return 900;
		}

		final String chargeFreeItem = itemName
			.replaceFirst("\\s+[a-z]?\\d+$", "")
			.trim();
		if (chargeFreeItem.equals(family))
		{
			return 850;
		}

		/*
		 * Shortest Path intentionally groups a few permanent versions under the
		 * same transport family as their charged counterpart. Examples in its
		 * transport data include Slayer ring -> Eternal slayer ring and Amulet of
		 * glory -> Amulet of eternal glory. Strip only the mechanically-equivalent
		 * "eternal" modifier before comparing; do not use loose substring matching
		 * that could accidentally treat unrelated items as teleports.
		 */
		final String equivalentItem = stripTravelVariantModifiers(chargeFreeItem);
		final String equivalentFamily = stripTravelVariantModifiers(family);
		if (!equivalentItem.isEmpty()
			&& equivalentItem.equals(equivalentFamily))
		{
			/*
			 * RuneLite uses "Slayer ring (eternal)" while some transport data calls
			 * the family "Eternal slayer ring". Prefer that permanent variant over
			 * a charged Slayer ring when the family explicitly requests Eternal.
			 */
			if (containsTravelWord(chargeFreeItem, "eternal")
				&& containsTravelWord(family, "eternal"))
			{
				return 875;
			}
			return 825;
		}

		if (family.startsWith(chargeFreeItem + " ")
			&& chargeFreeItem.length() >= 8)
		{
			return 700;
		}
		return 0;
	}

	static int travelNameMatchQualityForRegression(
		final String itemName,
		final String family)
	{
		return travelNameMatchQuality(
			normalizeTravelName(itemName),
			normalizeTravelName(family)
		);
	}

	private static boolean containsTravelWord(
		final String value,
		final String word)
	{
		return (" " + value + " ").contains(" " + word + " ");
	}

	private static String stripTravelVariantModifiers(final String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return "";
		}
		return value
			.replaceAll("\\beternal\\b", " ")
			.trim()
			.replaceAll("\\s+", " ");
	}

	private static TravelItemMatch betterTravelItem(
		final TravelItemMatch first,
		final TravelItemMatch second)
	{
		if (second == null)
		{
			return first;
		}
		return first == null || second.score > first.score
			? second
			: first;
	}

	private static int largestNumber(final String value)
	{
		int largest = 0;
		int current = 0;
		boolean reading = false;
		for (int index = 0; index < value.length(); index++)
		{
			final char character = value.charAt(index);
			if (Character.isDigit(character))
			{
				reading = true;
				current = current * 10 + character - '0';
			}
			else if (reading)
			{
				largest = Math.max(largest, current);
				current = 0;
				reading = false;
			}
		}
		return reading ? Math.max(largest, current) : largest;
	}

	private static String normalizeTravelName(final String value)
	{
		if (value == null)
		{
			return "";
		}

		return value
			.toLowerCase(Locale.ENGLISH)
			.replace('’', '\'')
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceAll("\\s+", " ");
	}

	private boolean isTravelItemStillOwned(final int itemId)
	{
		if (itemId <= 0)
		{
			return false;
		}
		final Integer bankQuantity = cachedBankItems.get(itemId);
		if (bankQuantity != null && bankQuantity > 0)
		{
			return true;
		}
		return containerHasItem(
			client.getItemContainer(InventoryID.INV),
			itemId
		) || containerHasItem(
			client.getItemContainer(InventoryID.WORN),
			itemId
		);
	}

	private boolean isInfernoStagingTravelItemKnownOwned(
		final int itemId,
		final boolean infernoStaging)
	{
		return isTravelItemStillOwned(itemId)
			|| shouldUseRememberedInfernoTravelItem(
				infernoStaging,
				bankScanned,
				profileInfernoTravelSnapshotLoaded,
				profileInfernoTravelItemId,
				itemId
			);
	}

	static boolean shouldUseRememberedInfernoTravelItem(
		final boolean infernoStaging,
		final boolean bankScanned,
		final boolean snapshotLoaded,
		final int snapshotItemId,
		final int selectedItemId)
	{
		return infernoStaging
			&& !bankScanned
			&& snapshotLoaded
			&& snapshotItemId > 0
			&& snapshotItemId == selectedItemId;
	}

	private static boolean containerHasItem(
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

	SlayerTaskPreparationCatalog.PreparationPlan getCurrentPreparationForOverlay()
	{
		return currentPreparation;
	}

	boolean isGuidedSessionActiveForOverlay()
	{
		return guidedSessionActive;
	}

	String getSelectedTravelItemNameForOverlay()
	{
		return currentTravelSelection().getItemName();
	}

	String getSelectedTravelDestinationForOverlay()
	{
		return currentTravelSelection().getDestination();
	}

	/**
	 * The widget overlay consumes the same immutable selection as the menu and
	 * panel highlighters. confirmTaskAreaForGuidedSession() clears that selection,
	 * so the marker disappears as soon as the encounter is reached.
	 */
	int getSelectedTravelItemIdForOverlay()
	{
		if (!guidedSessionActive || guidedSessionPhase == GuidedSessionPhase.TASKING)
		{
			return -1;
		}
		final SlayerTravelSelection selection = currentTravelSelection();
		return selection.hasPhysicalItem() ? selection.getItemId() : -1;
	}

	private void publishTravelSelection(
		final SlayerTravelSelection selection,
		final String note)
	{
		if (selection == null || !selection.isStructurallyValid())
		{
			return;
		}
		if (selection.hasPhysicalItem())
		{
			pinnedTravelHighlightGeneration = selection.getGeneration();
			pinnedTravelHighlightRouteIdentity = selection.getRouteIdentity();
		}
		else
		{
			clearPinnedTravelHighlight();
		}

		final String selectionIdentity = selection.travelIdentity();
		if (selectionIdentity.equals(lastPublishedTravelSelectionIdentity))
		{
			return;
		}
		lastPublishedTravelSelectionIdentity = selectionIdentity;

		restoreGuidedTeleportWidgetHighlights();
		clearTeleportHighlightCaptures();
		final String display = travelInstructionDisplay(
			selection.getItemName(),
			selection.getDestination()
		);
		showGuidedTravelSelection(selection, display, note);
		if (selection.hasPhysicalItem())
		{
			queueTeleportMenuHighlight();
		}
	}

	private void showGuidedTravelSelection(
		final SlayerTravelSelection selection,
		final String display,
		final String note)
	{
		final SlayerPlusPanel currentPanel = panel;
		if (currentPanel == null || selection == null)
		{
			return;
		}

		final String selectionIdentity = selection.travelIdentity();
		SwingUtilities.invokeLater(() ->
		{
			/*
			 * The Swing EDT can lag behind the RuneLite client thread. Drop a queued
			 * panel update if the route/profile changed before it reached the EDT.
			 */
			if (!selectionIdentity.equals(currentTravelSelection().travelIdentity()))
			{
				return;
			}
			currentPanel.showTravelRecommendation(display, note);
		});
	}

	private void showGuidedTravelRecommendation(
		final String itemName,
		final String note)
	{
		final SlayerPlusPanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
			currentPanel.showTravelRecommendation(itemName, note)
		);
	}

	private void clearGuidedTravelRecommendation()
	{
		guidedBankTransportTracking = false;
		guidedTaskBankPickupPending = false;
		guidedMasterBankPickupPending = false;
		guidedTravelRequestGeneration = -1L;
		guidedTravelRequestRouteIdentity = "";
		travelCoordinator.invalidate();
		lastPublishedTravelSelectionIdentity = "";
		clearPinnedTravelHighlight();
		restoreGuidedTeleportWidgetHighlights();
		clearTeleportHighlightCaptures();
		showGuidedTravelRecommendation("", "");
	}

	private void clearPinnedTravelHighlight()
	{
		pinnedTravelHighlightGeneration = -1L;
		pinnedTravelHighlightRouteIdentity = "";
	}

	private void createRecommendedBankTag()
	{
		if (panel == null || bankTagLayoutService == null)
		{
			return;
		}

		if (loadoutAnalyzer.rerollRandomSlayerHelmet())
		{
			refreshSlayerData();
		}
		createRecommendedBankTagNow(true);

		/*
		 * A completed 4x7 is the hand-off from preparation to travel. Keep that
		 * action and Shortest Path in one workflow: the same resolved task variant
		 * that authored the layout now owns a bank-aware, multi-stage route. This is
		 * especially important for Taverley Dungeon (surface entrance -> loaded Blue
		 * dragon) and Ungael (Torfinn -> boat landing -> Vorkath).
		 *
		 * Automatic layout refreshes call createRecommendedBankTagNow() directly, so
		 * they cannot recursively restart pathfinding when Shortest Path publishes a
		 * transport update.
		 */
		startOrRefreshGuidedRouteAfterBankTag();
	}

	private void startOrRefreshGuidedRouteAfterBankTag()
	{
		if (!config.enableShortestPathRouting()
			|| effectiveTaskRemaining() <= 0)
		{
			return;
		}

		if (!guidedSessionActive)
		{
			guidedSessionActive = true;
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			taskAreaAnchor = null;
			taskArrivalGraceTicks = 0;
			taskAreaExitTicks = 0;
			if (currentMasterId > 0)
			{
				lastUsedMasterId = currentMasterId;
			}
		}

		final boolean bankAware = shouldUseBankAwareProfileReroute(
			bankScanned,
			client.getItemContainer(InventoryID.BANK) != null
		);
		routeToTaskForGuidedSession(
			bankAware,
			"Bank tag ready"
		);
	}

	private void createRecommendedBankTagNow()
	{
		createRecommendedBankTagNow(false);
	}

	private void createRecommendedBankTagNow(final boolean forceWrite)
	{
		final SlayerPlusPanel currentPanel = panel;
		if (currentPanel == null || bankTagLayoutService == null)
		{
			return;
		}

		/*
		 * Scan Dizana's live extra-ammo item before layout output. If its identity
		 * changed, rebuild the loadout synchronously on this client-thread callback;
		 * otherwise the button would render the recommendation created before the
		 * quiver snapshot arrived.
		 */
		/*
		 * Gear tabs can retain the only exact Seeking ID visible after the server
		 * publishes a banked quiver stack as its ordinary compatibility ID.
		 */
		cacheSeekingArrowPlaceholders(
			client.getItemContainer(InventoryID.BANK)
		);
		if (refreshExtraQuiverAmmoSnapshot())
		{
			refreshSlayerData();
		}
		logQuiverBankTagDiagnostic();

		final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
		final boolean infernoTarget = isInfernoPreparationTarget(target);
		final boolean infernoStaging = infernoTarget && !infernoPreparationBankReady;
		final boolean infernoFinal = infernoTarget && infernoPreparationBankReady;
		final String expectedRouteIdentity = infernoStaging
			? infernoPreparationBankRouteIdentity(target)
			: routeIdentity(target);
		SlayerTravelSelection selection =
			travelCoordinator.selectionFor(expectedRouteIdentity);
		final boolean shortestPathOwnsTravel = target != null && !infernoFinal;
		if (shortestPathOwnsTravel)
		{
			selection = repairAuthoritativeTravelItemVariant(
				selection,
				expectedRouteIdentity
			);
		}
		final int travelItemId = shortestPathOwnsTravel
			&& selection != null
			&& selection.belongsTo(expectedRouteIdentity)
			&& selection.hasPhysicalItem()
			&& isInfernoStagingTravelItemKnownOwned(
				selection.getItemId(),
				infernoStaging
			)
				? selection.getItemId()
				: -1;
		final SlayerLoadoutPlan bankTagPlan;
		if (!shouldAddPersistentReturnTeleports(infernoTarget))
		{
			/*
			 * Inferno is a complete endurance run. Its Mor Ul Rek item is staged
			 * above the 4 x 7, and neither a bank teleport nor Karamja gloves may
			 * replace brews/restores in the final combat inventory.
			 */
			bankTagPlan = currentLoadout;
		}
		else
		{
			bankTagPlan = withOwnedPointBoostTravelKit(
				withOwnedMasterReturnTeleport(
					withOwnedBankTeleport(currentLoadout, travelItemId),
					travelItemId
				),
				travelItemId
			);
		}

		if (shortestPathOwnsTravel
			&& travelItemId <= 0
			&& !selection.isResolved())
		{
			showGuidedTravelRecommendation(
				"",
				infernoStaging
					? "Waiting for the exact Shortest Path transport to the Inferno preparation bank; the staging item will appear above the 4 x 7 without consuming a combat slot."
					: "Waiting for the exact Shortest Path transport for this route; SlayerPlus will not insert a competing teleport."
			);
		}

		final String bankTagState = bankTagStateFingerprint(
			bankTagPlan,
			selection,
			travelItemId,
			currentPreparation.getBankTagItemIds(),
			shortestPathOwnsTravel,
			infernoStaging ? INFERNO_STAGING_TRAVEL_SLOT_INDEX : 0,
			!infernoTarget,
			cachedExtraQuiverAmmoItemId
		);
		final boolean sameWrittenState = bankTagState.equals(
			lastWrittenBankTagState
		);
		if (!forceWrite && sameWrittenState)
		{
			return;
		}

		final boolean reopenedWithoutRewrite = forceWrite
			&& sameWrittenState
			&& bankTagLayoutService.reopenLastSavedLayoutIfUnchanged();
		final SlayerBankTagLayoutService.Result result = reopenedWithoutRewrite
			? SlayerBankTagLayoutService.Result.success(
				"Opened the existing SlayerPlus Current layout without rebuilding it."
			)
			: bankTagLayoutService.createOrUpdate(
				bankTagPlan,
				selection,
				travelItemId,
				currentPreparation.getBankTagItemIds(),
				shortestPathOwnsTravel,
				infernoStaging ? INFERNO_STAGING_TRAVEL_SLOT_INDEX : 0,
				!infernoTarget,
				cachedExtraQuiverAmmoItemId
			);

		if (result.isSuccess())
		{
			bankTagLayoutCreated = true;
			lastWrittenBankTagState = bankTagState;
		}

		SwingUtilities.invokeLater(() ->
			currentPanel.showBankTagStatus(result.getMessage())
		);
	}

	/**
	 * Adds one owned return item for the assigned Slayer master to the 4 x 7.
	 * The task-route teleport remains authoritative and the rune pouch remains
	 * last; this utility replaces only one ordinary food/restoration filler when
	 * an already-full boss inventory needs room.
	 */
	private SlayerLoadoutPlan withOwnedMasterReturnTeleport(
		final SlayerLoadoutPlan plan,
		final int taskTravelItemId)
	{
		if (plan == null)
		{
			return null;
		}

		final SlayerMasterRouteCatalog.MasterRoute master =
			SlayerMasterRouteCatalog.find(getPostTaskReturnMasterId());
		if (master == null)
		{
			return plan;
		}

		for (final String family : master.getReturnItemFamilies())
		{
			if (!achievementDiaries.allowsTravelItem(family))
			{
				continue;
			}
			final TravelItemMatch match = findOwnedTravelItem(family);
			if (match == null)
			{
				continue;
			}
			if (match.itemId == taskTravelItemId)
			{
				return plan;
			}
			return appendMasterReturnTeleportForRegression(
				plan,
				match.itemId,
				match.displayName
			);
		}
		return plan;
	}

	static boolean shouldAddPersistentReturnTeleports(
		final boolean infernoTarget)
	{
		return !infernoTarget;
	}

	/**
	 * Adds one reliable, owned bank/escape item independently of the outbound
	 * task teleport. Duplicate items collapse through the common append helper,
	 * so a single Max cape may satisfy both roles when it really is the selected
	 * task route, while Araxxor keeps Spider cave teleport + Max cape separately.
	 */
	private SlayerLoadoutPlan withOwnedBankTeleport(
		final SlayerLoadoutPlan plan,
		final int taskTravelItemId)
	{
		if (plan == null)
		{
			return null;
		}

		final String[] bankFamilies = {
			"max cape", "crafting cape",
			"amulet of eternal glory", "ring of dueling",
			"amulet of glory"
		};
		for (final String family : bankFamilies)
		{
			if (!achievementDiaries.allowsTravelItem(family))
			{
				continue;
			}
			final TravelItemMatch match = findOwnedTravelItem(family);
			if (match == null)
			{
				continue;
			}
			if (match.itemId == taskTravelItemId)
			{
				return plan;
			}
			return appendMasterReturnTeleportForRegression(
				plan, match.itemId, match.displayName
			);
		}
		return plan;
	}

	/**
	 * A point-boost trip should be able to finish, return for another assignment,
	 * and reach several common follow-up areas before banking again. Add a small
	 * owned-only travel kit to Turael/Aya layouts; normal and milestone tasks keep
	 * their existing one-route inventory policy.
	 */
	private SlayerLoadoutPlan withOwnedPointBoostTravelKit(
		final SlayerLoadoutPlan plan,
		final int taskTravelItemId)
	{
		if (plan == null
			|| !isPointBoosting()
			|| currentMasterId
				!= SlayerPointBoostCoordinator.TURAEL_AYA_MASTER_ID
			|| SlayerTuraelBoostCatalog.find(currentTaskName) == null)
		{
			return plan;
		}

		final String[][] travelFamilies = {
			{"max cape", "construct cape"},
			{"eternal slayer ring", "slayer ring"},
			{"digsite pendant"},
			{"ring of dueling"},
			{"amulet of glory"},
			{"games necklace"}
		};
		SlayerLoadoutPlan result = plan;
		int additions = 0;
		for (final String[] equivalentGroup : travelFamilies)
		{
			TravelItemMatch owned = null;
			for (final String family : equivalentGroup)
			{
				if (!achievementDiaries.allowsTravelItem(family))
				{
					continue;
				}
				owned = findOwnedTravelItem(family);
				if (owned != null)
				{
					break;
				}
			}
			if (owned == null || owned.itemId == taskTravelItemId)
			{
				continue;
			}
			final SlayerLoadoutPlan updated =
				appendMasterReturnTeleportForRegression(
					result,
					owned.itemId,
					owned.displayName
				);
			if (updated != result)
			{
				result = updated;
				additions++;
				if (additions >= 4)
				{
					break;
				}
			}
		}
		return result;
	}

	static SlayerLoadoutPlan appendMasterReturnTeleportForRegression(
		final SlayerLoadoutPlan plan,
		final int itemId,
		final String displayName)
	{
		if (plan == null || itemId <= 0)
		{
			return plan;
		}

		final List<SlayerLoadoutItem> inventory = new ArrayList<>(
			plan.getInventoryItems()
		);
		for (final SlayerLoadoutItem item : inventory)
		{
			if (item != null && item.getItemId() == itemId)
			{
				return plan;
			}
		}
		for (final SlayerLoadoutItem item : plan.getEquipmentItems())
		{
			if (item != null && item.getItemId() == itemId)
			{
				return plan;
			}
		}

		final SlayerLoadoutItem masterReturn = new SlayerLoadoutItem(
			displayName,
			itemId,
			1,
			SlayerLoadoutItem.Status.BANK
		).withInventoryGroup(SlayerMethodRules.InventoryGroup.UTILITY);
		if (inventory.size() < 28)
		{
			inventory.add(masterReturn);
		}
		else
		{
			int replaceIndex = lastInventoryGroupIndex(
				inventory,
				SlayerMethodRules.InventoryGroup.FOOD
			);
			if (replaceIndex < 0)
			{
				replaceIndex = lastDuplicateInventoryGroupIndex(
					inventory,
					SlayerMethodRules.InventoryGroup.RESTORE
				);
			}
			if (replaceIndex < 0)
			{
				return plan;
			}
			inventory.set(replaceIndex, masterReturn);
		}

		return new SlayerLoadoutPlan(
			plan.getEquipment(),
			plan.getInventory(),
			plan.getOwnedStatus(),
			plan.getLayoutTitle(),
			plan.getEquipmentItems(),
			inventory,
			plan.getOptionalItems()
		);
	}

	private static int lastInventoryGroupIndex(
		final List<SlayerLoadoutItem> inventory,
		final SlayerMethodRules.InventoryGroup group)
	{
		for (int index = Math.min(28, inventory.size()) - 1;
			index >= 0; index--)
		{
			final SlayerLoadoutItem item = inventory.get(index);
			if (item != null && item.getInventoryGroup() == group)
			{
				return index;
			}
		}
		return -1;
	}

	private static int lastDuplicateInventoryGroupIndex(
		final List<SlayerLoadoutItem> inventory,
		final SlayerMethodRules.InventoryGroup group)
	{
		int count = 0;
		for (int index = 0; index < Math.min(28, inventory.size()); index++)
		{
			final SlayerLoadoutItem item = inventory.get(index);
			if (item != null && item.getInventoryGroup() == group)
			{
				count++;
			}
		}
		if (count > 1)
		{
			return lastInventoryGroupIndex(inventory, group);
		}
		return -1;
	}

	static String bankTagStateFingerprint(
		final SlayerLoadoutPlan plan,
		final SlayerTravelSelection selection,
		final int travelItemId,
		final List<Integer> preparationItemIds,
		final boolean shortestPathOwnsTravel,
		final int authoritativeTravelSlotIndex,
		final boolean analyzerSourceZeroIsTravel,
		final int extraQuiverAmmoItemId)
	{
		final StringBuilder value = new StringBuilder(512);
		value.append(plan == null ? "" : plan.getLayoutTitle()).append('|');
		appendBankTagItems(value, 'E', plan == null
			? java.util.Collections.emptyList() : plan.getEquipmentItems());
		appendBankTagItems(value, 'I', plan == null
			? java.util.Collections.emptyList() : plan.getInventoryItems());
		appendBankTagItems(value, 'O', plan == null
			? java.util.Collections.emptyList() : plan.getOptionalItems());
		value.append("P=").append(preparationItemIds).append('|')
			.append("T=").append(travelItemId).append('|')
			.append(shortestPathOwnsTravel).append('|')
			.append(authoritativeTravelSlotIndex).append('|')
			.append(analyzerSourceZeroIsTravel).append('|')
			.append("Q=").append(extraQuiverAmmoItemId).append('|');
		if (selection != null)
		{
			value.append(selection.getStatus()).append('|')
				.append(selection.getItemId()).append('|')
				.append(selection.getEquivalentItemFamilies());
		}
		return value.toString();
	}

	private static void appendBankTagItems(
		final StringBuilder value,
		final char section,
		final List<SlayerLoadoutItem> items)
	{
		value.append(section).append('=');
		for (final SlayerLoadoutItem item : items)
		{
			if (item == null)
			{
				value.append("null;");
				continue;
			}
			value.append(item.getItemId()).append(',')
				.append(item.getDisplayName()).append(',')
				.append(item.isEquipmentSwitch()).append(',')
				.append(item.getSwitchStyle()).append(',')
				.append(item.getInventoryGroup()).append(';');
		}
		value.append('|');
	}

	/** Log once per explicit Bank Tag button click; never runs per tick/frame. */
	private void logQuiverBankTagDiagnostic()
	{
		if (client == null || !log.isDebugEnabled())
		{
			return;
		}

		final StringBuilder containerItems = new StringBuilder("[");
		final ItemContainer container = client.getItemContainer(
			InventoryID.DIZANAS_QUIVER_AMMO
		);
		if (container != null)
		{
			for (final Item item : container.getItems())
			{
				if (item == null || item.getId() <= 0)
				{
					continue;
				}
				if (containerItems.length() > 1)
				{
					containerItems.append(',');
				}
				containerItems.append(item.getId())
					.append('x')
					.append(item.getQuantity());
			}
		}
		containerItems.append(']');

		final StringBuilder widgets = new StringBuilder("[");
		final int[] componentIds =
		{
			InterfaceID.DizanasQuiver.AMMO_OBJ,
			InterfaceID.Wornitems.EXTRA_QUIVER_AMMO,
			InterfaceID.Equipment.EXTRA_QUIVER_AMMO,
			InterfaceID.Bankmain.EXTRA_QUIVER_AMMO
		};
		for (final int componentId : componentIds)
		{
			final Widget widget = client.getWidget(componentId);
			if (widget == null)
			{
				continue;
			}
			if (widgets.length() > 1)
			{
				widgets.append(',');
			}
			widgets.append(componentId)
				.append('=')
				.append(widget.getItemId())
				.append('x')
				.append(widget.getItemQuantity())
				.append(widget.isHidden() ? "h" : "v");
		}
		widgets.append(']');

		log.debug(
			"SlayerPlus quiver bank-tag: worn={}, bankQuiver={}, container={}, varp={}x{}, "
				+ "ammoSave={}, widgets={}, identityHints={}, resolved={}x{}",
			isWearingUsableDizana(),
			cachedBankDizanaVariantItemId,
			containerItems,
			client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO),
			client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT),
			client.getVarbitValue(VarbitID.DIZANAS_QUIVER_AMMO_SAVE),
			widgets,
			cachedSeekingArrowPlaceholderItemIds,
			cachedExtraQuiverAmmoItemId,
			cachedExtraQuiverAmmoQuantity
		);
	}

	/**
	 * Repair the concrete owned variant of the authoritative physical travel item.
	 *
	 * <p>This method never pathfinds and never rescans the bank. If an exact item
	 * ID changes state (for example a charged/cosmetic equivalent), the coordinator
	 * itself is updated so panel text, highlighter identity, and the Bank Tag travel cell
	 * continue to consume one shared selection.</p>
	 */
	private SlayerTravelSelection repairAuthoritativeTravelItemVariant(
		final SlayerTravelSelection selection,
		final String expectedRouteIdentity)
	{
		if (selection == null
			|| !selection.belongsTo(expectedRouteIdentity)
			|| !selection.hasPhysicalItem()
			|| isTravelItemStillOwned(selection.getItemId()))
		{
			return selection;
		}

		/*
		 * The selected ID can become stale when an equivalent charged/cosmetic
		 * variant moves between states. Repair the coordinator itself rather than
		 * returning a Bank-Tag-only ID. That keeps panel, menu highlight, and the travel cell
		 * on the same immutable travel selection.
		 */
		final String selectedFamily = travelItemFamilyName(
			selection.getItemName()
		);
		final TravelItemMatch equivalent = findOwnedTravelItem(selectedFamily);
		if (equivalent == null)
		{
			return selection;
		}

		final SlayerTravelSelection repaired =
			travelCoordinator.replacePhysicalItemVariant(
				expectedRouteIdentity,
				equivalent.itemId,
				equivalent.displayName
			);
		if (repaired != null
			&& repaired.belongsTo(expectedRouteIdentity)
			&& travelCoordinator.ownsCurrentRoute(expectedRouteIdentity))
		{
			publishTravelSelection(
				repaired,
				"Using the currently owned equivalent state of Shortest Path's selected travel item. Panel, highlight, and Bank Tag travel placement remain synchronized."
			);
		}
		return repaired;
	}

	private void refreshOpenBankTagForTravelSelection()
	{
		/*
		 * The persistent SlayerPlus Current layout is valid even while the bank UI
		 * is closed. A live encounter/profile switch therefore must be allowed to
		 * replace the authoritative travel cell when Shortest Path resolves the new transport. Coalesce
		 * transport bursts so this never becomes a per-message layout rebuild loop.
		 */
		if (clientThread == null || bankTagTravelRefreshQueued)
		{
			return;
		}

		bankTagTravelRefreshQueued = true;
		clientThread.invokeLater(() ->
		{
			bankTagTravelRefreshQueued = false;
			if (!bankTagLayoutCreated && !isSlayerPlusBankTagOpen())
			{
				return;
			}
			createRecommendedBankTagNow();
		});
	}

	/*
	 * Pure policy helper kept package-visible so the startup regression guard can
	 * lock the live profile-switch behavior without needing a RuneLite client.
	 */
	static boolean shouldUseBankAwareProfileReroute(
		final boolean hasScannedBankSnapshot,
		final boolean liveBankOpen)
	{
		return hasScannedBankSnapshot || liveBankOpen;
	}

	private void toggleGuidedSession()
	{
		if (guidedSessionActive)
		{
			stopGuidedSession("Guided Slayer session stopped");
			return;
		}

		if (!isGuidedSessionAvailable())
		{
			guidedSessionStatus = client.getGameState() == GameState.LOGGED_IN
				? "Enable Shortest Path routing in SlayerPlus settings first"
				: "Log in to start a guided Slayer session";
			updateGuidedSessionPanel();
			return;
		}

		guidedSessionActive = true;
		guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
		taskAreaAnchor = null;
		taskArrivalGraceTicks = 0;
		taskAreaExitTicks = 0;
		guidedLastPlayerLocation = null;
		guidedLastTemplateLocation = null;
		resetGuidedRoutePlanState();
		loadedPohSpellbookAltarId = -1;
		loadedPohSpellbookAltarLocation = null;
		guidedPohSpellbookTeleportPending = false;
		guidedPohSpellbookAltarReached = false;
		guidedPohSpellbookFallbackActive = false;
		guidedPohSpellbookLoadTicks = 0;

		final int remaining = effectiveTaskRemaining();
		if (currentMasterId > 0)
		{
			lastUsedMasterId = currentMasterId;
		}

		if (remaining > 0)
		{
			final ItemContainer liveBank =
				client.getItemContainer(InventoryID.BANK);
			if (liveBank != null)
			{
				/* One bank-aware rebuild replaces the old pre-bank + post-bank pair. */
				cacheBankContainer(liveBank);
				refreshSlayerData();
			}
			else if (currentRecommendation == null)
			{
				refreshSlayerData();
			}

			final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
			if (confirmGuidedTaskAreaBeforePreparation(target))
			{
				return;
			}
			if (routeToSpellbookIfRequired(
				"Required spellbook is not active"
			))
			{
				return;
			}
			if (liveBank != null)
			{
				routeToTaskForGuidedSession(
					true,
					"Bank scanned"
				);
			}
			else if (isInfernoPreparationTarget(target))
			{
				/*
				 * The Zuk route owns its final preparation bank. Sending this task to
				 * the nearest generic bank first breaks the intended two-leg workflow
				 * and can leave Shortest Path with the wrong destination.
				 */
				routeToInfernoPreparationBankForGuidedSession(
					target,
					"TzKal-Zuk task found - final preparation starts at the east Mor Ul Rek bank"
				);
			}
			else if (isCarriedLoadoutReadyForRegression(currentLoadout))
			{
				/* The setup—including its task teleport—is already on the player.
				 * Do not route through a bank merely because an older bank snapshot
				 * also contains another copy or variant of that teleport family. */
				routeToTaskForGuidedSession(
					false,
					"The complete reviewed loadout is already carried"
				);
			}
			else
			{
				routeToBankForGuidedSession(
					"Active task found — go to a bank to gear up"
				);
			}
			return;
		}

		if (getRoutingMasterId() > 0)
		{
			routeToMasterForGuidedSession();
		}
		else
		{
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"Session active — get a Slayer task and SlayerPlus will guide the next step"
			);
		}
	}

	private void stopGuidedSession(final String status)
	{
		if (shortestPathBridge != null)
		{
			shortestPathBridge.clear();
		}

		/*
		 * Stopping path guidance must not erase the Bank Tag's authoritative travel
		 * choice. The selected item/destination still belongs to the current profile
		 * and must continue to drive inventory placement and dropdown highlighting.
		 */
		resetGuidedSessionState(false);
		guidedSessionStatus = status == null || status.trim().isEmpty()
			? "Guided Slayer session stopped"
			: status.trim();
		updateGuidedSessionPanel();
		refreshGuidedTeleportWidgetHighlights();
	}

	private void resetGuidedSessionState()
	{
		resetGuidedSessionState(true);
	}

	private void resetGuidedSessionState(final boolean clearTravelSelection)
	{
		if (teleportHighlighter != null)
		{
			teleportHighlighter.setRouteGuidance("");
		}
		guidedSessionActive = false;
		guidedSessionPhase = GuidedSessionPhase.STOPPED;
		guidedSessionStatus =
			"Start a guided session to route through your Slayer task";
		clearGuidedTaskDetourSnapshot();
		pointBoostStreakSyncPending = false;
		pointBoostStreakBeforeCompletion = -1;
		taskAreaAnchor = null;
		taskArrivalGraceTicks = 0;
		taskAreaExitTicks = 0;
		guidedLastPlayerLocation = null;
		guidedLastTemplateLocation = null;
		resetGuidedRoutePlanState();
		guidedPohSpellbookTeleportPending = false;
		guidedPohSpellbookAltarReached = false;
		guidedPohSpellbookFallbackActive = false;
		guidedPohSpellbookLoadTicks = 0;
		guidedBankTransportTracking = false;
		guidedTaskBankPickupPending = false;
		guidedMasterBankPickupPending = false;
		guidedTravelRequestGeneration = -1L;
		guidedTravelRequestRouteIdentity = "";
		if (clearTravelSelection)
		{
			travelCoordinator.invalidate();
			clearPinnedTravelHighlight();
		}
		guidedDynamicTaskDestination = null;
		guidedSurfaceEntranceDestination = null;
		clearRouteDiscoveryThrottle();
		guidedEntranceReached = false;
		guidedRouteWaitingForCheckpoint = false;
		infernoPreparationBankReady = false;
		infernoPreparationManualTeleportPending = false;
		infernoPreparationHotVentPending = false;
		whispererRingTeleportPending = false;
		whispererCathedralTeleportPending = false;
		araxxorSpiderTeleportPending = false;
		mortimerSlayerRingTeleportPending = false;
		guidedInfernoEntryDestination = null;
		infernoEntryPromptState = 0;
		guidedPostTransportContinuationPending = false;
		resetTormentedRouteProgress();
		loadedTormentedChasmWallLocations.clear();
		loadedTormentedChasmExitLocations.clear();
		tormentedChasmSceneSeedPending = true;
		if (clearTravelSelection)
		{
			restoreGuidedTeleportWidgetHighlights();
			clearTeleportHighlightCaptures();
			showGuidedTravelRecommendation("", "");
		}
	}

	private void handleBankOpenedForGuidedSession()
	{
		if (!guidedSessionActive)
		{
			return;
		}

		if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK)
		{
			final Player player = client.getLocalPlayer();
			final WorldPoint playerLocation = player == null
				? null : player.getWorldLocation();
			final WorldPoint liveBankerTarget =
				findInfernoPreparationBankerTarget(playerLocation);
			final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
			if (!isInfernoPreparationTarget(target))
			{
				return;
			}
			/*
			 * The live named banker is the strongest possible proof that this is the
			 * Zuk bank. Keep the reviewed anchor as the bank-chest/interface fallback,
			 * but never reject the real bank merely because an anchor is a few tiles
			 * away from the player's interaction tile.
			 */
			if (!isAtInfernoPreparationBank(playerLocation)
				&& liveBankerTarget == null)
			{
				/*
				 * The player may have opened an ordinary bank while the no-hilt path was
				 * already active. The settled bank scan immediately above this handler is
				 * authoritative. If it discovers a supported Ghommal item, restart the
				 * preparation branch now so the old Hot-vent route, panel instruction,
				 * and travel-less Bank Tag cannot survive that discovery.
				 */
				final TravelRouteMatch bankTravel =
					findInfernoPreparationTravelItem();
				if (isManualInfernoPreparationTeleport(bankTravel))
				{
					log.debug(
						"Inferno preparation bank scan replaced the Hot vent branch with Ghommal item {}",
						bankTravel.item.itemId
					);
					routeToInfernoPreparationBankForGuidedSession(
						target,
						"Supported Ghommal item found in the open bank"
					);
				}
				return;
			}

			final ItemContainer liveBank = client.getItemContainer(InventoryID.BANK);
			if (liveBank != null)
			{
				cacheBankContainer(liveBank);
			}
			infernoPreparationBankReady = true;
			infernoPreparationManualTeleportPending = false;
			infernoPreparationHotVentPending = false;
			if (shortestPathBridge != null)
			{
				shortestPathBridge.clear();
			}
			travelCoordinator.invalidateRoute(
				infernoPreparationBankRouteIdentity(target)
			);
			guidedBankTransportTracking = false;
			guidedTaskBankPickupPending = false;
			guidedTravelRequestGeneration = -1L;
			guidedTravelRequestRouteIdentity = "";
			restoreGuidedTeleportWidgetHighlights();
			clearTeleportHighlightCaptures();
			showGuidedTravelRecommendation("", "");

			/* Re-resolve owned Inferno gear from this final bank snapshot. */
			refreshSlayerData();
			createRecommendedBankTagNow();
			final GuidedTaskTarget refreshedTarget = resolveGuidedTaskTarget();
			routeToInfernoEntryForGuidedSession(
				refreshedTarget != null ? refreshedTarget : target,
				"Inferno preparation bank opened"
			);
			return;
		}

		/*
		 * CONTINUOUS SESSION RULE:
		 * Opening a bank during a live assignment is an explicit preparation
		 * checkpoint, even when the player arrived there while TASKING or while an
		 * inventory-only return-to-task route was already active. Rebuild from the
		 * settled bank snapshot so task teleports stored in the bank can be selected.
		 */
		if (shouldResumeTaskAfterBankForRegression(
			guidedSessionPhase,
			effectiveTaskRemaining()
		))
		{
			routeToTaskForGuidedSession(true, "Bank scanned — resuming the active task");
			return;
		}

		/* A player may deliberately bank after completing a task. Preserve the
		 * master-return workflow instead of silently abandoning it at the bank. */
		if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER)
		{
			routeToMasterForGuidedSession(true);
			return;
		}
		if (shouldResumeMasterAfterBankForRegression(
			guidedSessionPhase,
			guidedMasterBankPickupPending,
			effectiveTaskRemaining()
		))
		{
			routeToMasterForGuidedSession(true);
			return;
		}

		if (guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_BANK)
		{
			return;
		}

		if (effectiveTaskRemaining() <= 0)
		{
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"Bank opened, but no active task is available"
			);
			return;
		}

		routeToTaskForGuidedSession(true, "Bank scanned");
	}

	static boolean shouldResumeTaskAfterBankForRegression(
		final GuidedSessionPhase phase,
		final int remaining)
	{
		return remaining > 0
			&& (phase == GuidedSessionPhase.ROUTING_TO_TASK
				|| phase == GuidedSessionPhase.TASKING);
	}

	static boolean shouldResumeMasterAfterBankForRegression(
		final GuidedSessionPhase phase,
		final boolean masterBankPickupPending,
		final int remaining)
	{
		return phase == GuidedSessionPhase.ROUTING_TO_BANK
			&& masterBankPickupPending
			&& remaining <= 0;
	}

	private void handleBankClosedForGuidedSession()
	{
		if (!shouldResumeTaskAfterBankCloseForRegression(
			guidedSessionActive,
			guidedSessionPhase,
			effectiveTaskRemaining()
		))
		{
			return;
		}

		if (whispererRingTeleportPending)
		{
			/* Closing the bank is not completion of the manual Ring-of-shadows
			 * stage. Preserve that stage and merely change whether Shortest Path may
			 * use the bank, based on whether the selected ring is now carried. */
			final SlayerTravelSelection selection = currentTravelSelection();
			final boolean ringCarried = selection.hasPhysicalItem()
				&& findCarriedTravelItemForTransport(
					selection.getItemName()
				) != null;
			routeToTaskForGuidedSession(
				!ringCarried,
				ringCarried
					? "Bank closed — use Ring of shadows -> Lassar Undercity"
					: "Bank closed before the Ring of shadows was withdrawn"
			);
			return;
		}

		/* The bank-open calculation may be superseded by container refreshes while
		 * the player withdraws gear. Reissue one inventory-only request from the
		 * settled post-bank position so the return path cannot disappear. */
		routeToTaskForGuidedSession(
			false,
			"Bank closed — returning to the unfinished task"
		);
	}

	static boolean shouldResumeTaskAfterBankCloseForRegression(
		final boolean sessionActive,
		final GuidedSessionPhase phase,
		final int remaining)
	{
		return sessionActive
			&& remaining > 0
			&& (phase == GuidedSessionPhase.ROUTING_TO_BANK
				|| phase == GuidedSessionPhase.ROUTING_TO_TASK
				|| phase == GuidedSessionPhase.TASKING);
	}

	private void handleGuidedTaskTransition(
		final int previousRemaining,
		final String previousTaskName,
		final int remaining,
		final String taskName,
		final int previousNormalStreak,
		final int normalStreak)
	{
		if (!guidedSessionActive || previousRemaining < 0)
		{
			return;
		}

		final boolean hadTask = previousRemaining > 0;
		final boolean hasTask = remaining > 0;

		if (pointBoostStreakSyncPending && !hasTask)
		{
			clearGuidedTaskDetourSnapshot();
			if (normalStreak == pointBoostStreakBeforeCompletion)
			{
				guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
				setGuidedSessionStatus(
					"Task complete — waiting for the Slayer streak to update before choosing the next point-boost master."
				);
				return;
			}
			pointBoostStreakSyncPending = false;
			pointBoostStreakBeforeCompletion = -1;
			routeToMasterForGuidedSession();
			return;
		}

		if (hadTask && !hasTask)
		{
			clearGuidedTaskDetourSnapshot();
			if (isPointBoosting()
				&& previousNormalStreak >= 0
				&& normalStreak == previousNormalStreak)
			{
				pointBoostStreakSyncPending = true;
				pointBoostStreakBeforeCompletion = previousNormalStreak;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
				guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
				setGuidedSessionStatus(
					"Task complete — waiting for the Slayer streak to update before choosing the next point-boost master."
				);
				return;
			}
			routeToMasterForGuidedSession();
			return;
		}

		final boolean receivedNewTask = hasTask
			&& (!hadTask || !sameTask(previousTaskName, taskName));
		if (receivedNewTask)
		{
			clearGuidedTaskDetourSnapshot();
			pointBoostStreakSyncPending = false;
			pointBoostStreakBeforeCompletion = -1;
			final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
			if (routeToSpellbookIfRequired(
				"New task requires a different spellbook"
			))
			{
				return;
			}
			if (isInfernoPreparationTarget(target))
			{
				routeToInfernoPreparationBankForGuidedSession(
					target,
					"New TzKal-Zuk task received - final preparation starts at the east Mor Ul Rek bank"
				);
				return;
			}

			if (guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_BANK)
			{
				routeToBankForGuidedSession(
					"New task received — go to a bank to gear up"
				);
			}
			return;
		}

		if (hasTask && previousRemaining > remaining
			&& shouldConfirmTaskAreaAfterCountDecreaseForRegression(
				guidedSessionPhase))
		{
			confirmTaskAreaForGuidedSession();
		}
	}

	static boolean shouldConfirmTaskAreaAfterCountDecreaseForRegression(
		final GuidedSessionPhase phase)
	{
		/* A delayed count refresh or another player's kill can arrive after the
		 * player has already left for a bank. Only an already-confirmed encounter
		 * may refresh its anchor; travel phases must retain their active route. */
		return phase == GuidedSessionPhase.TASKING;
	}

	private void routeToBankForGuidedSession(final String reason)
	{
		if (!guidedSessionActive)
		{
			return;
		}

		captureGuidedTaskDetourSnapshot();
		final GuidedTaskDetourSnapshot detour = guidedTaskDetourSnapshot;
		if (detour != null && detour.target != null
			&& detour.target.routePlan != null)
		{
			ensureGuidedRoutePlanState(detour.target);
			restartGuidedRoutePlanProgress();
		}
		clearGuidedTravelRecommendation();
		/* A bank detour begins a new attempt at the encounter. Never carry lower-
		 * chasm/temple observations from an earlier Tormented-demon visit into it. */
		resetTormentedRouteProgress();

		final boolean krystiliaSession = getRoutingMasterId()
			== KRYSTILIA_MASTER_ID;
		/*
		 * Prefer a bank-capable NPC that is actually loaded near the player. This
		 * catches valid banks whose usable target is a Banker rather than one of
		 * the static booth/chest coordinates in SlayerBankRouteCatalog. The NPC's
		 * occupied tile is never used; route to a collision-checked perimeter tile.
		 */
		final Player player = client.getLocalPlayer();
		final WorldPoint playerLocation = player == null
			? null
			: player.getWorldLocation();
		/*
		 * A bank leg can begin after several scene/instance transitions (most
		 * noticeably after a completed task and a new assignment). Rebuild the
		 * live-object snapshot once here so a chest that loaded before its spawn
		 * subscription was observed cannot be missed. This is deliberately not a
		 * tick-time scan.
		 */
		seedLoadedBankObjectsIfNeeded();
		final WorldPoint loadedBankTarget = nearestTarget(
			playerLocation,
			findLoadedBankNpcTarget(playerLocation),
			findLoadedBankObjectTarget(playerLocation)
		);
		final WorldPoint reviewedLocalBankTarget = loadedBankTarget == null
			? SlayerBankRouteCatalog.getPreferredLocalApproachTarget(playerLocation)
			: null;

		final boolean sent;
		if (shortestPathBridge == null)
		{
			sent = false;
		}
		else if (loadedBankTarget != null)
		{
			/*
			 * A live banker/chest beside the player is already authoritative. Keep
			 * this leg scene-local so a teleport can never beat the short walk. Use
			 * normal bank interaction tolerance because a Banker may stand behind a
			 * counter and the NPC's occupied tile is not itself walkable.
			 */
			sent = shortestPathBridge.routeToLocalBankTarget(
				shortestPathStart(playerLocation),
				loadedBankTarget,
				!krystiliaSession
			);
		}
		else if (reviewedLocalBankTarget != null)
		{
			sent = shortestPathBridge.routeToLocalBankTarget(
				shortestPathStart(playerLocation),
				reviewedLocalBankTarget,
				!krystiliaSession
			);
		}
		else
		{
			sent = shortestPathBridge.routeToAny(
				shortestPathStart(playerLocation),
				SlayerBankRouteCatalog.getBankTargets(krystiliaSession),
				!krystiliaSession,
				false,
				false
			);
		}

		if (!sent)
		{
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"Unable to send a bank route to Shortest Path"
			);
			return;
		}

		guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_BANK;
		guidedLastPlayerLocation = playerLocation;
		taskAreaAnchor = null;
		taskArrivalGraceTicks = 0;
		taskAreaExitTicks = 0;
		setGuidedSessionStatus(loadedBankTarget == null
			? reason + ". Follow the highlighted route using teleports you carry."
			: reason + ". A usable bank is already nearby — follow the local route and open it."
		);
	}

	private boolean routeToSpellbookIfRequired(final String reason)
	{
		if (!guidedSessionActive
			|| currentPreparation == null
			|| !currentPreparation.isActive()
			|| currentPreparation.isSpellbookReady())
		{
			return false;
		}

		final int currentBook = client.getVarbitValue(SPELLBOOK_VARBIT);
		final SlayerSpellbookRouteCatalog.Route route =
			SlayerSpellbookRouteCatalog.resolve(
				currentPreparation.getSpellbookName(), currentBook
			);
		if (route == null)
		{
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"A different spellbook is required, but no reviewed change route is available"
			);
			return true;
		}

		final Player player = client.getLocalPlayer();
		final WorldPoint playerLocation = player == null
			? null : player.getWorldLocation();
		/*
		 * Do not enable Shortest Path's bank-item pool until SlayerPlus has an
		 * authoritative ownership snapshot. The first bank opening caches that
		 * snapshot, then handleBankOpenedForGuidedSession resumes here and sends
		 * the bank-aware spellbook route.
		 */
		if (needsBankSnapshotBeforeSpellbookRouteForRegression(bankScanned))
		{
			routeToBankForGuidedSession(
				reason + " — scan a bank so owned teleports can be compared"
			);
			return true;
		}

		if (!guidedPohSpellbookFallbackActive)
		{
			if (pohAltarSupportsSpellbookForRegression(
				loadedPohSpellbookAltarId,
				currentPreparation.getSpellbookName()
			))
			{
				clearGuidedTravelRecommendation();
				guidedPohSpellbookTeleportPending = false;
				guidedPohSpellbookAltarReached = true;
				guidedPohSpellbookLoadTicks = 0;
				guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_SPELLBOOK;
				setGuidedSessionStatus(
					reason + " — use the loaded POH altar to switch to "
						+ route.getSpellbookName()
						+ ". SlayerPlus will route back to a bank after the switch."
				);
				return true;
			}

			final TravelRouteMatch pohTravel = findOwnedPohSpellbookTravel();
			if (pohTravel != null)
			{
				final String identity = "spellbook-poh|"
					+ normalizeTravelName(route.getSpellbookName());
				travelCoordinator.beginRoute(identity);
				guidedTravelRequestGeneration = travelCoordinator.getGeneration();
				guidedTravelRequestRouteIdentity = identity;
				guidedBankTransportTracking = false;
				guidedTaskBankPickupPending = false;
				guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_SPELLBOOK;
				guidedPohSpellbookTeleportPending = true;
				guidedPohSpellbookAltarReached = false;
				guidedPohSpellbookLoadTicks = 0;
				taskAreaAnchor = null;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
				applyTravelRouteMatch(pohTravel, identity);
				refreshGuidedTeleportWidgetHighlights();
				setGuidedSessionStatus(
					reason + " — use the highlighted "
						+ pohTravel.item.displayName
						+ " -> " + pohTravel.destination
						+ ". SlayerPlus will verify the loaded altar and continue automatically."
				);
				return true;
			}
		}

		clearGuidedTravelRecommendation();
		final boolean suppressInactiveQuestCape =
			shouldSuppressInactiveQuestCapeForRegression(
				isTravelItemStillOwned(ItemID.SKILLCAPE_QP)
					|| isTravelItemStillOwned(ItemID.SKILLCAPE_QP_TRIMMED),
				client.getVarpValue(VarPlayerID.QP),
				client.getVarbitValue(VarbitID.QP_MAX)
			);
		final boolean sent = shortestPathBridge != null
			&& shortestPathBridge.routeToSpellbookChange(
				shortestPathStart(playerLocation),
				route.getTargets(),
				true,
				suppressInactiveQuestCape
			);
		if (!sent)
		{
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"Unable to send the " + route.getSpellbookName()
					+ " change route to Shortest Path"
			);
			return true;
		}

		guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_SPELLBOOK;
		guidedLastPlayerLocation = playerLocation;
		taskAreaAnchor = route.getDestination();
		taskArrivalGraceTicks = 0;
		taskAreaExitTicks = 0;
		setGuidedSessionStatus(
			reason + " — Shortest Path is routing to the reviewed "
				+ route.getSpellbookName()
				+ " change point using the fastest teleport you own in your inventory or bank. "
				+ route.getInteraction()
		);
		return true;
	}

	private TravelRouteMatch findOwnedPohSpellbookTravel()
	{
		for (final String family : pohSpellbookTravelFamiliesForRegression())
		{
			final TravelItemMatch item = findOwnedTravelItem(family);
			if (item != null)
			{
				return new TravelRouteMatch(
					item,
					family.equals("teleport to house")
						? "Break" : "Teleport to house",
					java.util.Collections.singleton(family)
				);
			}
		}
		return null;
	}

	static List<String> pohSpellbookTravelFamiliesForRegression()
	{
		return Arrays.asList(
			"max cape", "construct cape", "teleport to house"
		);
	}

	static boolean needsBankSnapshotBeforeSpellbookRouteForRegression(
		final boolean hasBankSnapshot)
	{
		return !hasBankSnapshot;
	}

	private WorldPoint shortestPathStart(final WorldPoint point)
	{
		final WorldView worldView = client.getTopLevelWorldView();
		return shortestPathStartForRegression(
			point,
			worldView != null && worldView.isInstance()
		);
	}

	/**
	 * Static route terminals are authored in template coordinates. RuneLite gives
	 * the player a copied/dynamic WorldPoint inside an instance, so compare route
	 * areas against the template point while retaining the live point for loaded
	 * NPC collision and teleport-transition checks.
	 */
	private WorldPoint routeComparisonLocation(
		final Player player,
		final WorldPoint fallback)
	{
		if (player == null)
		{
			return fallback;
		}

		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || !worldView.isInstance()
			|| player.getLocalLocation() == null)
		{
			return fallback;
		}

		final WorldPoint templateLocation = WorldPoint.fromLocalInstance(
			client, player.getLocalLocation()
		);
		return templateLocation == null ? fallback : templateLocation;
	}

	static WorldPoint shortestPathStartForRegression(
		final WorldPoint point,
		final boolean instancedWorldView)
	{
		/*
		 * A null start tells Shortest Path to call its instance-aware
		 * WorldPointUtil.fromLocalInstance conversion. Passing Player#getWorldLocation
		 * from an instanced POH bypasses that conversion and produces an unreachable
		 * route. WorldView.isInstance is authoritative for every custom house layout.
		 */
		return instancedWorldView ? null : point;
	}

	static boolean shouldSuppressInactiveQuestCapeForRegression(
		final boolean questCapeOwned,
		final int currentQuestPoints,
		final int maximumQuestPoints)
	{
		return questCapeOwned
			&& maximumQuestPoints > 0
			&& currentQuestPoints < maximumQuestPoints;
	}

	private void handleGuidedSpellbookReadinessTransition()
	{
		if (!guidedSessionActive
			|| guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_SPELLBOOK
			|| currentPreparation == null
			|| !currentPreparation.isSpellbookReady())
		{
			return;
		}

		if (shortestPathBridge != null)
		{
			shortestPathBridge.clear();
		}
		guidedPohSpellbookTeleportPending = false;
		guidedPohSpellbookAltarReached = false;
		guidedPohSpellbookFallbackActive = false;
		guidedPohSpellbookLoadTicks = 0;
		taskAreaAnchor = null;
		if (shouldReturnToBankAfterSpellbookForRegression(
			bankScanned, currentLoadout
		))
		{
			routeToBankForGuidedSession(
				"Spellbook changed — return to a bank to finish the loadout"
			);
		}
		else
		{
			routeToTaskForGuidedSession(
				false,
				"Spellbook changed and the carried loadout is complete"
			);
		}
	}

	static boolean shouldReturnToBankAfterSpellbookForRegression(
		final boolean hasBankSnapshot,
		final SlayerLoadoutPlan loadout)
	{
		/*
		 * Spellbook switching is always a preparation leg. Return to a bank even
		 * when the last snapshot appeared carried-ready: the switch route may have
		 * consumed a teleport, and the player still needs one final authoritative
		 * gear/inventory scan before the monster route begins.
		 */
		return true;
	}

	private static boolean hasBankedRequiredItem(
		final List<SlayerLoadoutItem> items)
	{
		if (items == null)
		{
			return false;
		}
		for (final SlayerLoadoutItem item : items)
		{
			if (item != null && item.isBanked())
			{
				return true;
			}
		}
		return false;
	}

	static boolean isCarriedLoadoutReadyForRegression(
		final SlayerLoadoutPlan loadout)
	{
		if (loadout == null || !loadout.hasConcreteItems())
		{
			return false;
		}
		return itemsAreCarried(loadout.getEquipmentItems())
			&& itemsAreCarried(loadout.getInventoryItems());
	}

	private static boolean itemsAreCarried(final List<SlayerLoadoutItem> items)
	{
		for (final SlayerLoadoutItem item : items)
		{
			if (item == null)
			{
				continue;
			}
			final SlayerLoadoutItem.Status status = item.getStatus();
			if (status == SlayerLoadoutItem.Status.BANK
				|| status == SlayerLoadoutItem.Status.MISSING
				|| status == SlayerLoadoutItem.Status.UNKNOWN)
			{
				return false;
			}
		}
		return true;
	}


	private boolean selectedStrategyUsesCannon()
	{
		return currentRecommendation != null
			&& currentRecommendation.getStrategy() != null
			&& currentRecommendation.getStrategy().hasTag(
				SlayerTaskStrategy.MethodTag.CANNON
			);
	}

	private WorldPoint selectedCannonPosition(
		final GuidedTaskTarget target)
	{
		if (target == null || target.isBoss() || !selectedStrategyUsesCannon())
		{
			return null;
		}
		return SlayerRouteCatalog.findCannonPosition(
			target.taskName,
			target.location
		);
	}

	private WorldPoint selectedBarragePosition(
		final GuidedTaskTarget target)
	{
		if (target == null || target.isBoss()
			|| currentRecommendation == null
			|| currentRecommendation.getStrategy() == null
			|| !currentRecommendation.getStrategy().hasTag(
				SlayerTaskStrategy.MethodTag.BURST_BARRAGE
			))
		{
			return null;
		}
		return SlayerRouteCatalog.findBarragePosition(
			target.taskName,
			target.location
		);
	}

	private RouteStage resolveRouteStage(
		final GuidedTaskTarget target,
		final WorldPoint playerLocation)
	{
		return resolveRouteStage(target, playerLocation, playerLocation);
	}

	private RouteStage resolveRouteStage(
		final GuidedTaskTarget target,
		final WorldPoint playerLocation,
		final WorldPoint livePlayerLocation)
	{
		if (target == null || target.profile == null)
		{
			return null;
		}

		/*
		 * Shortest Path 1.20.6 does not currently include the level-93 cave
		 * transport between The Hollows and the Meiyerditch Laboratories. Route
		 * qualified players to the real western cave mouth as an explicit stage;
		 * after they manually enter it, the changed coordinate loads the ordinary
		 * laboratory/cannon stage. This remains guidance only and never interacts
		 * with the shortcut for the player.
		 */
		if (shouldUseMeiyerditchShortcut(target, playerLocation))
		{
			return new RouteStage(
				MEIYERDITCH_SHORTCUT_APPROACH,
				singletonRouteTarget(MEIYERDITCH_SHORTCUT_APPROACH),
				true,
				false,
				false
			);
		}

		final WorldPoint kalphiteQueenLowerRope =
			kalphiteQueenInteriorTransitionForRegression(
				target.taskName, target.location, playerLocation
			);
		if (kalphiteQueenLowerRope != null)
		{
			return new RouteStage(
				kalphiteQueenLowerRope,
				SlayerRouteCatalog.targetArea(kalphiteQueenLowerRope, 1),
				true,
				false,
				false
			);
		}

		final WorldPoint whispererIntermediate =
			whispererIntermediateDestinationForRegression(
				target.taskName, target.location, playerLocation
			);
		if (whispererIntermediate != null)
		{
			return new RouteStage(
				whispererIntermediate,
				singletonRouteTarget(whispererIntermediate),
				true,
				false,
				false
			);
		}

		final WorldPoint aquaniteIslandIntermediate =
			aquaniteIslandDestinationForRegression(
				target.taskName, target.location, playerLocation
			);
		if (aquaniteIslandIntermediate != null)
		{
			return new RouteStage(
				aquaniteIslandIntermediate,
				SlayerRouteCatalog.targetArea(aquaniteIslandIntermediate, 1),
				true,
				false,
				false
			);
		}

		final WorldPoint aquaniteSailingDeparture =
			aquaniteSailingDepartureForRegression(
				target.taskName,
				target.location,
				playerLocation,
				hasBuiltYnysdailRowboat()
			);
		if (aquaniteSailingDeparture != null)
		{
			return new RouteStage(
				aquaniteSailingDeparture,
				SlayerRouteCatalog.targetArea(aquaniteSailingDeparture, 2),
				true,
				false,
				false
			);
		}

		final WorldPoint whispererTeleporter =
			whispererCathedralTeleporterApproachForRegression(
				target.taskName, target.location, playerLocation
			);
		if (whispererTeleporter != null)
		{
			return new RouteStage(
				whispererTeleporter,
				SlayerRouteCatalog.targetArea(whispererTeleporter, 2),
				true,
				false,
				false
			);
		}

		final boolean stagedOutside = target.isStaged()
			&& !target.isInsideEncounterArea(playerLocation);
		final boolean accessOutside = target.profile.isAccessThenNpc()
			&& target.destination(playerLocation) != null
			&& sameCoordinateLayer(playerLocation, target.destination(playerLocation));

		final String discoveryIdentity = routeDiscoveryIdentity(target);
		WorldPoint exactEntrance = null;
		if (stagedOutside || accessOutside)
		{
			final WorldPoint authoredEntrance = target.profile.getSurfaceAccess() != null
				? target.profile.getSurfaceAccess()
				: target.destination(playerLocation);
			exactEntrance = guidedSurfaceEntranceDestination;
			if (exactEntrance == null
				&& shouldDiscoverNearbyRouteEntity(
					playerLocation,
					authoredEntrance,
					DYNAMIC_TASK_NPC_RADIUS
				)
				&& routeDiscoveryDue(
					gameTickSequence,
					lastSurfaceEntranceDiscoveryTick,
					discoveryIdentity,
					lastSurfaceEntranceDiscoveryIdentity
				))
			{
				lastSurfaceEntranceDiscoveryTick = gameTickSequence;
				lastSurfaceEntranceDiscoveryIdentity = discoveryIdentity;
				exactEntrance = findLoadedSurfaceEntrance(
					target.profile,
					authoredEntrance
				);
				if (exactEntrance != null)
				{
					guidedSurfaceEntranceDestination = exactEntrance;
				}
			}
		}

		WorldPoint methodPosition = null;
		final WorldPoint reviewedCannonPosition = selectedCannonPosition(target);
		final WorldPoint reviewedMethodPosition = reviewedCannonPosition != null
			? reviewedCannonPosition
			: selectedBarragePosition(target);
		if (!stagedOutside
			&& reviewedMethodPosition != null
			&& sameCoordinateLayer(playerLocation, reviewedMethodPosition))
		{
			methodPosition = reviewedMethodPosition;
		}

		WorldPoint exactNpc = null;
		if (!stagedOutside)
		{
			exactNpc = guidedDynamicTaskDestination;
			/*
			 * A route only needs one reachable encounter anchor. Re-scanning every
			 * three ticks to chase a walking NPC repeatedly traversed the loaded NPC
			 * list and local collision map while Shortest Path was also recalculating.
			 * Route/profile transitions clear this cache, so discover once per route.
			 */
			if (exactNpc == null
				&& shouldAttemptExactNpcDiscoveryForRegression(
					target.profile,
					playerLocation,
					DYNAMIC_TASK_NPC_RADIUS
				)
				&& routeDiscoveryDue(
					gameTickSequence,
					lastTaskNpcDiscoveryTick,
					discoveryIdentity,
					lastTaskNpcDiscoveryIdentity
				))
			{
				lastTaskNpcDiscoveryTick = gameTickSequence;
				lastTaskNpcDiscoveryIdentity = discoveryIdentity;
				final WorldPoint discovered = findLoadedTaskNpcTarget(
					livePlayerLocation,
					target.profile
				);
				if (discovered != null)
				{
					exactNpc = discovered;
					guidedDynamicTaskDestination = discovered;
					saveLearnedRouteCheckpoint(target, discovered);
				}
			}
		}

		final WorldPoint learned = readLearnedRouteCheckpoint(target);
		final WorldPoint usableLearned = canUseLearnedRouteCheckpointNow(
			target,
			playerLocation,
			learned
		) ? learned : null;

		final SlayerRouteCoordinator.Stage resolved = SlayerRouteCoordinator.resolve(
			target.profile,
			playerLocation,
			exactEntrance,
			exactEntrance == null ? java.util.Collections.emptySet()
				: singletonRouteTarget(exactEntrance),
			methodPosition,
			methodPosition == null ? java.util.Collections.emptySet()
				: singletonRouteTarget(methodPosition),
			exactNpc,
			exactNpc == null ? java.util.Collections.emptySet()
				: singletonRouteTarget(exactNpc),
			usableLearned,
			usableLearned == null ? java.util.Collections.emptySet()
				: SlayerRouteCatalog.targetArea(usableLearned, 2)
		);

		if (resolved == null || !resolved.isValid())
		{
			return null;
		}

		final SlayerRouteCoordinator.StageKind kind = resolved.getKind();
		return new RouteStage(
			resolved.getDestination(),
			resolved.getTargets(),
			kind == SlayerRouteCoordinator.StageKind.SURFACE_APPROACH
				|| kind == SlayerRouteCoordinator.StageKind.EXACT_ENTRANCE
				|| kind == SlayerRouteCoordinator.StageKind.ACCESS,
			kind == SlayerRouteCoordinator.StageKind.EXACT_NPC,
			kind == SlayerRouteCoordinator.StageKind.LEARNED_CHECKPOINT
		);
		}

	static boolean shouldAttemptExactNpcDiscoveryForRegression(
		final SlayerRouteCatalog.RouteProfile profile,
		final WorldPoint playerLocation,
		final int maximumRadius)
	{
		if (profile == null || playerLocation == null)
		{
			return false;
		}
		/* NPC-only and entrance-then-NPC profiles intentionally have no final
		 * interior coordinate. Their exact aliases are therefore the continuation
		 * contract and must be discoverable after the scene changes. */
		if (profile.requiresLoadedNpc() || profile.isAccessThenNpc())
		{
			return true;
		}
		return profile.isInsideEncounterArea(playerLocation)
			|| shouldDiscoverNearbyRouteEntity(
				playerLocation,
				profile.getRouteDestination(playerLocation),
				maximumRadius
			);
	}

	private boolean shouldUseMeiyerditchShortcut(
		final GuidedTaskTarget target,
		final WorldPoint playerLocation)
	{
		return target != null
			&& client != null
			&& shouldUseMeiyerditchShortcutForRegression(
				target.taskName,
				target.location,
				playerLocation,
				client.getBoostedSkillLevel(Skill.AGILITY)
			);
	}

	static WorldPoint whispererIntermediateDestinationForRegression(
		final String taskName,
		final String location,
		final WorldPoint playerLocation)
	{
		if (playerLocation == null
			|| !("whisperer".equals(normalizeTravelName(taskName))
				|| "the whisperer".equals(normalizeTravelName(taskName)))
			|| !"lassar undercity".equals(normalizeTravelName(location))
			|| playerLocation.getPlane() != 0
			|| playerLocation.getX() < CAMDOZAAL_MIN_X
			|| playerLocation.getX() > CAMDOZAAL_MAX_X
			|| playerLocation.getY() < CAMDOZAAL_MIN_Y
			|| playerLocation.getY() > CAMDOZAAL_MAX_Y)
		{
			return null;
		}
		return WHISPERER_LASSAR_SINKHOLE;
	}

	/**
	 * The Gwenith and Ynysdail rowboat endpoints share the ordinary overworld
	 * coordinate layer, so a generic staged-dungeon check cannot distinguish
	 * them. Only expose the island cavern entrance after the player is at the
	 * verified Ynysdail rowboat landing. This prevents Shortest Path from drawing
	 * a walk across the ocean from Gwenith.
	 */
	static WorldPoint aquaniteIslandDestinationForRegression(
		final String taskName,
		final String location,
		final WorldPoint playerLocation)
	{
		final String task = normalizeTravelName(taskName);
		return playerLocation != null
			&& ("aquanite".equals(task) || "aquanites".equals(task))
			&& "ynysdail cavern".equals(normalizeTravelName(location))
			&& playerLocation.getPlane() == AQUANITE_YNYSDAIL_ROWBOAT.getPlane()
			&& distance(playerLocation, AQUANITE_YNYSDAIL_ROWBOAT) <= 24
				? AQUANITE_CAVERN_ENTRANCE
				: null;
	}

	private boolean hasBuiltYnysdailRowboat()
	{
		return client != null && hasBuiltYnysdailRowboatForRegression(
			client.getVarbitValue(VarbitID.AMENITY_ROWBOAT_YNYSDAIL)
		);
	}

	static boolean hasBuiltYnysdailRowboatForRegression(final int value)
	{
		return value > 0;
	}

	static WorldPoint aquaniteSailingDepartureForRegression(
		final String taskName,
		final String location,
		final WorldPoint playerLocation,
		final boolean rowboatBuilt)
	{
		final String task = normalizeTravelName(taskName);
		if (rowboatBuilt
			|| playerLocation == null
			|| !("aquanite".equals(task) || "aquanites".equals(task))
			|| !"ynysdail cavern".equals(normalizeTravelName(location))
			|| (playerLocation.getPlane() == AQUANITE_YNYSDAIL_ROWBOAT.getPlane()
				&& distance(playerLocation, AQUANITE_YNYSDAIL_ROWBOAT) <= 24))
		{
			return null;
		}
		return AQUANITE_PORT_ROBERTS_MOORING;
	}

	static boolean isWhispererLassarInteriorForRegression(
		final String taskName,
		final String location,
		final WorldPoint playerLocation)
	{
		final String task = normalizeTravelName(taskName)
			.replaceFirst("^the\\s+", "");
		return playerLocation != null
			&& task.equals("whisperer")
			&& "lassar undercity".equals(normalizeTravelName(location))
			&& playerLocation.getPlane() == 0
			&& playerLocation.getX() >= LASSAR_UNDERCITY_MIN_X
			&& playerLocation.getX() <= LASSAR_UNDERCITY_MAX_X
			&& playerLocation.getY() >= LASSAR_UNDERCITY_MIN_Y
			&& playerLocation.getY() <= LASSAR_UNDERCITY_MAX_Y;
	}

	static boolean isWhispererRingTravelForRegression(
		final String taskName,
		final String location,
		final String itemName,
		final String destination)
	{
		final String task = normalizeTravelName(taskName)
			.replaceFirst("^the\\s+", "");
		return task.equals("whisperer")
			&& "lassar undercity".equals(normalizeTravelName(location))
			&& normalizeTravelName(itemName).contains("ring of shadows")
			&& "lassar undercity".equals(normalizeTravelName(destination));
	}

	static WorldPoint whispererRingLandingForRegression()
	{
		return WHISPERER_LASSAR_RING_LANDING;
	}

	static WorldPoint whispererCathedralTeleporterApproachForRegression(
		final String taskName,
		final String location,
		final WorldPoint playerLocation)
	{
		final String task = normalizeTravelName(taskName)
			.replaceFirst("^the\\s+", "");
		return playerLocation != null
			&& task.equals("whisperer")
			&& "lassar undercity".equals(normalizeTravelName(location))
			&& playerLocation.getPlane() == 0
			&& playerLocation.getX() >= 2558
			&& playerLocation.getX() <= 2609
			&& playerLocation.getY() >= 6413
			&& playerLocation.getY() <= 6468
				? WHISPERER_PALACE_TELEPORTER
				: null;
	}

	static boolean isWhispererCathedralTeleporterLandingForRegression(
		final String taskName,
		final String location,
		final WorldPoint playerLocation)
	{
		final String task = normalizeTravelName(taskName)
			.replaceFirst("^the\\s+", "");
		return playerLocation != null
			&& task.equals("whisperer")
			&& "lassar undercity".equals(normalizeTravelName(location))
			&& playerLocation.getPlane() == WHISPERER_CATHEDRAL_TELEPORTER.getPlane()
			&& distance(playerLocation, WHISPERER_CATHEDRAL_TELEPORTER) <= 12;
	}

	static boolean shouldUseMeiyerditchShortcutForRegression(
		final String taskName,
		final String location,
		final WorldPoint playerLocation,
		final int boostedAgilityLevel)
	{
		final String task = normalizeTravelName(taskName);
		if (playerLocation == null
			|| (!("bloodveld".equals(task)) && !("bloodvelds".equals(task)))
			|| !"meiyerditch laboratories".equals(normalizeTravelName(location))
			|| boostedAgilityLevel < MEIYERDITCH_SHORTCUT_AGILITY_LEVEL)
		{
			return false;
		}

		/*
		 * The shortcut exits on the laboratory coordinate layer at x=3535/3541.
		 * Once the player is on that side, never send them back to the western
		 * entrance; let the reviewed interior stage continue to the cannon tile.
		 */
		final boolean onLaboratorySide =
			playerLocation.getX() >= MEIYERDITCH_LAB_SHORTCUT_EXIT_MIN_X
				&& playerLocation.getX() <= 3660
				&& playerLocation.getY() >= 9650
				&& playerLocation.getY() <= 9850;
		return !onLaboratorySide;
	}

	private static boolean isMeiyerditchShortcutStage(
		final GuidedTaskTarget target,
		final WorldPoint destination)
	{
		return target != null
			&& MEIYERDITCH_SHORTCUT_APPROACH.equals(destination)
			&& "meiyerditch laboratories".equals(
				normalizeTravelName(target.location)
			);
	}

	private static boolean isWhispererSinkholeStage(
		final GuidedTaskTarget target,
		final WorldPoint destination)
	{
		return target != null
			&& WHISPERER_LASSAR_SINKHOLE.equals(destination)
			&& ("whisperer".equals(normalizeTravelName(target.taskName))
				|| "the whisperer".equals(normalizeTravelName(target.taskName)))
			&& "lassar undercity".equals(
				normalizeTravelName(target.location)
			);
	}

	private static String routeDiscoveryIdentity(
		final GuidedTaskTarget target)
	{
		if (target == null)
		{
			return "";
		}
		return normalizeTravelName(target.taskName)
			+ "|" + normalizeTravelName(target.location)
			+ "|" + (target.isBoss() ? "boss" : "regular");
	}

	static boolean routeDiscoveryDue(
		final long currentTick,
		final long previousTick,
		final String currentIdentity,
		final String previousIdentity)
	{
		if (currentIdentity == null || currentIdentity.isEmpty())
		{
			return false;
		}
		if (!currentIdentity.equals(previousIdentity)
			|| previousTick == Long.MIN_VALUE)
		{
			return true;
		}
		return currentTick - previousTick >= ROUTE_DISCOVERY_INTERVAL_TICKS;
	}

	static boolean shouldDiscoverNearbyRouteEntity(
		final WorldPoint playerLocation,
		final WorldPoint authoredLocation,
		final int maximumRadius)
	{
		return playerLocation != null
			&& authoredLocation != null
			&& playerLocation.getPlane() == authoredLocation.getPlane()
			&& distance(playerLocation, authoredLocation)
				<= Math.max(1, maximumRadius);
	}

	private void clearRouteDiscoveryThrottle()
	{
		lastSurfaceEntranceDiscoveryTick = Long.MIN_VALUE;
		lastSurfaceEntranceDiscoveryIdentity = "";
		lastTaskNpcDiscoveryTick = Long.MIN_VALUE;
		lastTaskNpcDiscoveryIdentity = "";
	}

	private static boolean sameCoordinateLayer(
		final WorldPoint first,
		final WorldPoint second)
	{
		if (first == null || second == null)
		{
			return true;
		}
		return first.getPlane() == second.getPlane()
			&& (first.getY() >>> 12) == (second.getY() >>> 12);
	}

	static boolean shouldUseLocalTaskRouteForRegression(
		final WorldPoint playerLocation,
		final WorldPoint destination)
	{
		/*
		 * World-map dungeon panels can reposition an underground region without
		 * changing its game coordinates. Shortest Path's world-map polyline then
		 * has the correct corridor shape but is drawn beside the panel, exactly as
		 * seen in the Meiyerditch tunnels. Once both endpoints are on the same
		 * underground coordinate layer, use scene/minimap guidance instead.
		 */
		return destination != null
			&& destination.getY() >= 4096
			&& sameCoordinateLayer(playerLocation, destination);
	}

	static WorldPoint kalphiteQueenInteriorTransitionForRegression(
		final String taskName,
		final String location,
		final WorldPoint playerLocation)
	{
		if (taskName == null
			|| location == null
			|| playerLocation == null
			|| !(taskName.equalsIgnoreCase("Kalphite Queen")
				|| taskName.equalsIgnoreCase("The Kalphite Queen"))
			|| !location.equalsIgnoreCase("Kalphite Lair")
			|| playerLocation.getPlane()
				!= KALPHITE_QUEEN_LOWER_ROPE_APPROACH.getPlane()
			|| !sameCoordinateLayer(
				playerLocation, KALPHITE_QUEEN_LOWER_ROPE_APPROACH
			))
		{
			return null;
		}

		return Math.abs(
			playerLocation.getX() - KALPHITE_QUEEN_LOWER_ROPE_APPROACH.getX()
		) <= KALPHITE_LAIR_STAGE_RADIUS
			&& Math.abs(
				playerLocation.getY()
					- KALPHITE_QUEEN_LOWER_ROPE_APPROACH.getY()
			) <= KALPHITE_LAIR_STAGE_RADIUS
				? KALPHITE_QUEEN_LOWER_ROPE_APPROACH
				: null;
	}

	private boolean shouldEnableAgilityShortcutsForStage(
		final GuidedTaskTarget target,
		final WorldPoint destination)
	{
		if (target == null || client == null)
		{
			return true;
		}
		return shouldEnableAgilityShortcutsForStageForRegression(
			target.taskName,
			target.location,
			destination,
			client.getBoostedSkillLevel(Skill.AGILITY),
			client.getVarbitValue(VarbitID.DESERT_DIARY_ELITE_COMPLETE) > 0
		);
	}

	static boolean shouldEnableAgilityShortcutsForStageForRegression(
		final String taskName,
		final String location,
		final WorldPoint destination,
		final int agilityLevel,
		final boolean desertEliteDiaryComplete)
	{
		final boolean kalphiteQueenLowerRopeStage = destination != null
			&& destination.equals(KALPHITE_QUEEN_LOWER_ROPE_APPROACH)
			&& taskName != null
			&& (taskName.equalsIgnoreCase("Kalphite Queen")
				|| taskName.equalsIgnoreCase("The Kalphite Queen"))
			&& location != null
			&& location.equalsIgnoreCase("Kalphite Lair");
		return !kalphiteQueenLowerRopeStage
			|| (desertEliteDiaryComplete
				&& agilityLevel >= KALPHITE_LAIR_SHORTCUT_AGILITY_LEVEL);
	}

	static boolean shouldRetireTravelRecommendationForLocalRoute(
		final boolean localTaskRoute,
		final boolean travelTrackingActive,
		final boolean hasPhysicalTravelItem)
	{
		/*
		 * A teleport recommendation belongs only to the cross-region leg. Once a
		 * reviewed underground route can be drawn locally, retaining that selection
		 * leaves stale instructions (for example "Max cape -> Teleport to house")
		 * visible throughout the dungeon and keeps its menu highlights active.
		 */
		return localTaskRoute
			&& (travelTrackingActive || hasPhysicalTravelItem);
	}

	private boolean canUseLearnedRouteCheckpointNow(
		final GuidedTaskTarget target,
		final WorldPoint playerLocation,
		final WorldPoint checkpoint)
	{
		if (target == null
			|| target.profile == null
			|| playerLocation == null
			|| checkpoint == null
			|| !target.profile.isPlausibleLearnedCheckpoint(checkpoint)
			|| playerLocation.getPlane() != checkpoint.getPlane())
		{
			return false;
		}

		if (target.isStaged())
		{
			return target.isInsideEncounterArea(playerLocation);
		}

		return distance(playerLocation, checkpoint)
			<= LEARNED_ROUTE_USE_RADIUS;
	}

	private String routeIdentity(final GuidedTaskTarget target)
	{
		if (target == null)
		{
			return "";
		}

		final String recommendationMethod = currentRecommendation == null
			? ""
			: normalizeTravelName(currentRecommendation.getMethod());
		final String recommendationTravel = currentRecommendation == null
			? ""
			: normalizeTravelName(currentRecommendation.getTravel());
		final String recommendationCannon = currentRecommendation == null
			? ""
			: normalizeTravelName(currentRecommendation.getCannon());
		final GuidedTaskDetourSnapshot detour = guidedTaskDetourSnapshot;
		final SlayerGuidedLifecycleContract.TaskRouteKey routeKey = detour != null
			&& detour.target == target
				? detour.key
				: SlayerGuidedLifecycleContract.taskRouteKey(
					currentTaskName,
					currentTaskVariant,
					target.taskName,
					target.location
				);

		/*
		 * Route cache identity includes every recommendation control that can change
		 * the selected location, travel method, combat method, or positioning target.
		 * This prevents an otherwise identical task/location from reusing a stale
		 * Profit/Fast-XP/AFK transport selection.
		 */
		return routeKey.routeIdentity()
			+ "|" + (target.isBoss() ? "boss" : "regular")
			+ "|playstyle=" + String.valueOf(config.playstyle())
			+ "|combat=" + String.valueOf(config.combatStylePreference())
			+ "|cannon=" + String.valueOf(config.cannonPreference())
			+ "|burst=" + String.valueOf(config.burstPreference())
			+ "|travelpref=" + String.valueOf(config.travelPreference())
			+ "|method=" + recommendationMethod
			+ "|travel=" + recommendationTravel
			+ "|routecannon=" + recommendationCannon;
	}

	private boolean ensureGuidedRoutePlanState(
		final GuidedTaskTarget target)
	{
		final SlayerRoutePlan plan = target == null ? null : target.routePlan;
		if (plan == null)
		{
			if (guidedRoutePlan != null)
			{
				resetGuidedRoutePlanState();
			}
			return false;
		}

		final String identity = routeIdentity(target) + "|plan=" + plan.getId();
		if (plan == guidedRoutePlan && identity.equals(guidedRoutePlanIdentity))
		{
			return true;
		}

		resetGuidedRoutePlanState();
		guidedRoutePlan = plan;
		guidedRoutePlanCursor = plan.initialCursor();
		guidedRoutePlanIdentity = identity;
		for (final SlayerRoutePlan.Leg leg : plan.getLegs().values())
		{
			collectRoutePlanPredicateRequirements(leg.getCompletionPredicate());
			collectRoutePlanPredicateRequirements(leg.getReadinessPredicate());
			for (final SlayerRoutePlan.Branch branch : leg.getBranches())
			{
				collectRoutePlanPredicateRequirements(branch.getCondition());
			}
		}
		routePlanObjectSceneSeedPending = true;
		routePlanNpcSceneSeedPending = true;
		routeEvidenceVersion++;
		return true;
	}

	private void collectRoutePlanPredicateRequirements(
		final SlayerRoutePredicate predicate)
	{
		if (predicate == null)
		{
			return;
		}
		switch (predicate.getKind())
		{
			case TRACKED_OBJECT_ACTION:
			case OBSERVED_OBJECT_INTERACTION:
				if (predicate.getObjectActionSpec() != null)
				{
					final SlayerRoutePredicate.ObjectActionSpec spec =
						predicate.getObjectActionSpec();
					guidedRoutePlanObjectSpecs.add(spec);
					guidedRoutePlanObjectIds.addAll(spec.getObjectIds());
					if (spec.getObjectIds().isEmpty())
					{
						guidedRoutePlanHasNameOnlyObjectSpecs = true;
					}
				}
				break;
			case VISIBLE_WIDGET_COMPONENT:
				guidedRoutePlanWidgetComponents.add(predicate.getWidgetId());
				break;
			case VISIBLE_WIDGET_GROUP:
				guidedRoutePlanWidgetGroups.add(predicate.getWidgetId());
				break;
			case EXACT_NPC:
				guidedRoutePlanNpcNames.addAll(predicate.getExactNpcNames());
				break;
			default:
				break;
		}
		for (final SlayerRoutePredicate child : predicate.getChildren())
		{
			collectRoutePlanPredicateRequirements(child);
		}
	}

	private void resetGuidedRoutePlanState()
	{
		guidedRoutePlan = null;
		guidedRoutePlanCursor = null;
		guidedRoutePlanEvaluation = null;
		guidedRoutePlanIdentity = "";
		guidedRoutePlanEvidenceVersion = -1L;
		guidedRoutePlanArrivalEvidenceVersion = -1L;
		guidedRouteEvidenceCache = null;
		guidedRouteEvidenceCachePlan = null;
		guidedRouteEvidenceCacheVersion = -1L;
		guidedPreviousPlayerLocation = null;
		guidedPreviousTemplateLocation = null;
		observedRoutePlanInteractions.clear();
		observedRoutePlanInteractionExpiresAfterTick = -1L;
		loadedRoutePlanObjectActions.clear();
		guidedRoutePlanObjectSpecs.clear();
		guidedRoutePlanObjectIds.clear();
		guidedRoutePlanHasNameOnlyObjectSpecs = false;
		routePlanObjectSceneSeedPending = true;
		loadedRoutePlanNpcs.clear();
		guidedRoutePlanNpcNames.clear();
		routePlanNpcSceneSeedPending = true;
		loadedRoutePlanWidgetGroups.clear();
		guidedRoutePlanWidgetGroups.clear();
		guidedRoutePlanWidgetComponents.clear();
		visibleRoutePlanWidgetComponents.clear();
		routeEvidenceVersion++;
	}

	private void restartGuidedRoutePlanProgress()
	{
		if (guidedRoutePlan == null)
		{
			return;
		}
		guidedRoutePlanCursor = guidedRoutePlan.initialCursor();
		guidedRoutePlanEvaluation = null;
		guidedRoutePlanEvidenceVersion = -1L;
		guidedRoutePlanArrivalEvidenceVersion = -1L;
		guidedRouteEvidenceCache = null;
		guidedRouteEvidenceCachePlan = null;
		guidedRouteEvidenceCacheVersion = -1L;
		guidedPreviousPlayerLocation = null;
		guidedPreviousTemplateLocation = null;
		observedRoutePlanInteractions.clear();
		observedRoutePlanInteractionExpiresAfterTick = -1L;
		routeEvidenceVersion++;
	}

	private SlayerRouteEvidence buildGuidedRouteEvidence(
		final Player player,
		final WorldPoint rawPlayerLocation)
	{
		if (guidedRoutePlan == null)
		{
			return SlayerRouteEvidence.empty();
		}
		if (routePlanObjectSceneSeedPending)
		{
			seedLoadedRoutePlanObjects();
		}
		if (routePlanNpcSceneSeedPending)
		{
			seedLoadedRoutePlanNpcs();
		}

		final WorldPoint templateLocation = routeComparisonLocation(
			player, rawPlayerLocation);
		if (guidedLastPlayerLocation == null && rawPlayerLocation != null)
		{
			guidedLastPlayerLocation = rawPlayerLocation;
			guidedLastTemplateLocation = templateLocation;
			routeEvidenceVersion++;
		}
		else if (!java.util.Objects.equals(
			guidedLastPlayerLocation, rawPlayerLocation)
			|| !java.util.Objects.equals(
				guidedLastTemplateLocation, templateLocation))
		{
			/* Route resolution also runs from bank/widget/menu events. Reconcile
			 * movement here before consulting the evidence cache so a teleport
			 * between game ticks cannot reuse the prior scene's evidence. */
			guidedPreviousPlayerLocation = guidedLastPlayerLocation;
			guidedPreviousTemplateLocation = guidedLastTemplateLocation;
			guidedLastPlayerLocation = rawPlayerLocation;
			guidedLastTemplateLocation = templateLocation;
			routeEvidenceVersion++;
		}
		final Set<Integer> currentlyVisibleWidgetComponents;
		if (guidedRoutePlanWidgetComponents.isEmpty())
		{
			currentlyVisibleWidgetComponents = java.util.Collections.emptySet();
		}
		else
		{
			currentlyVisibleWidgetComponents = new LinkedHashSet<>();
			for (final Integer componentId : guidedRoutePlanWidgetComponents)
			{
				final Widget widget = client.getWidget(componentId);
				if (widget != null && !widget.isHidden())
				{
					currentlyVisibleWidgetComponents.add(componentId);
				}
			}
		}
		if (!visibleRoutePlanWidgetComponents.equals(
			currentlyVisibleWidgetComponents))
		{
			visibleRoutePlanWidgetComponents.clear();
			visibleRoutePlanWidgetComponents.addAll(
				currentlyVisibleWidgetComponents);
			routeEvidenceVersion++;
		}
		if (guidedRouteEvidenceCache != null
			&& guidedRouteEvidenceCachePlan == guidedRoutePlan
			&& guidedRouteEvidenceCacheVersion == routeEvidenceVersion)
		{
			return guidedRouteEvidenceCache;
		}

		final WorldView worldView = client == null
			? null : client.getTopLevelWorldView();
		final SlayerRouteEvidence.Builder builder = SlayerRouteEvidence.builder()
			.previousWorldPoint(guidedPreviousPlayerLocation)
			.worldPoint(rawPlayerLocation)
			.previousTemplatePoint(guidedPreviousTemplateLocation)
			.templatePoint(templateLocation)
			.instanced(worldView != null && worldView.isInstance());

		for (final Set<SlayerRouteEvidence.ObjectAction> actions
			: loadedRoutePlanObjectActions.values())
		{
			for (final SlayerRouteEvidence.ObjectAction action : actions)
			{
				builder.trackedObjectAction(action);
			}
		}
		for (final SlayerRouteEvidence.ObjectAction action
			: observedRoutePlanInteractions)
		{
			builder.observedObjectInteraction(action);
		}
		for (final Integer groupId : loadedRoutePlanWidgetGroups)
		{
			builder.visibleWidgetGroup(groupId);
		}
		for (final Integer componentId : visibleRoutePlanWidgetComponents)
		{
			builder.visibleWidgetComponent(componentId);
		}
		for (final NPC npc : loadedRoutePlanNpcs)
		{
			if (npc != null && npc.getName() != null
				&& !npc.getName().trim().isEmpty())
			{
				builder.exactNpc(npc.getName());
			}
		}
		guidedRouteEvidenceCache = builder.build();
		guidedRouteEvidenceCachePlan = guidedRoutePlan;
		guidedRouteEvidenceCacheVersion = routeEvidenceVersion;
		return guidedRouteEvidenceCache;
	}

	private RoutePlanResolution resolveGuidedRoutePlan(
		final GuidedTaskTarget target,
		final Player player,
		final WorldPoint rawPlayerLocation)
	{
		if (!ensureGuidedRoutePlanState(target))
		{
			return null;
		}
		if (observedRoutePlanInteractionExpiresAfterTick >= 0
			&& gameTickSequence > observedRoutePlanInteractionExpiresAfterTick)
		{
			observedRoutePlanInteractions.clear();
			observedRoutePlanInteractionExpiresAfterTick = -1L;
			routeEvidenceVersion++;
		}

		final SlayerRouteEvidence evidence = buildGuidedRouteEvidence(
			player, rawPlayerLocation);
		final long evidenceVersion = routeEvidenceVersion;
		if (guidedRoutePlanArrivalEvidenceVersion == evidenceVersion)
		{
			return new RoutePlanResolution(
				SlayerRoutePlan.ProgressStatus.ARRIVED, null, null, false);
		}
		if (guidedRoutePlanEvaluation != null
			&& guidedRoutePlanEvidenceVersion == evidenceVersion
			&& !guidedRoutePlanEvaluation.hasAdvanced())
		{
			return routePlanResolution(guidedRoutePlanEvaluation,
				routeComparisonLocation(player, rawPlayerLocation));
		}
		if (guidedRoutePlan.hasArrived(evidence))
		{
			guidedRoutePlanArrivalEvidenceVersion = evidenceVersion;
			return new RoutePlanResolution(
				SlayerRoutePlan.ProgressStatus.ARRIVED, null, null, false);
		}

		final SlayerRoutePlan.Leg previousLeg = guidedRoutePlan.getLeg(
			guidedRoutePlanCursor.getActiveLegId());
		guidedRoutePlanEvaluation = guidedRoutePlan.evaluate(
			guidedRoutePlanCursor, evidence);
		guidedRoutePlanCursor = guidedRoutePlanEvaluation.getCursor();
		guidedRoutePlanEvidenceVersion = guidedRoutePlanEvaluation.hasAdvanced()
			? -1L : evidenceVersion;
		if (guidedRoutePlanEvaluation.hasAdvanced()
			&& previousLeg != null
			&& previousLeg.getKind()
				== SlayerRoutePlan.LegKind.MANUAL_INTERACTION)
		{
			observedRoutePlanInteractions.clear();
			observedRoutePlanInteractionExpiresAfterTick = -1L;
			routeEvidenceVersion++;
		}
		return routePlanResolution(guidedRoutePlanEvaluation,
			routeComparisonLocation(player, rawPlayerLocation));
	}

	private static RoutePlanResolution routePlanResolution(
		final SlayerRoutePlan.Evaluation evaluation,
		final WorldPoint playerLocation)
	{
		if (evaluation == null)
		{
			return null;
		}
		final SlayerRoutePlan.Leg leg = evaluation.getActiveLeg();
		final SlayerRoutePlan.RouteTarget target = leg == null
			? null : leg.getTarget();
		if (target == null || !target.hasRoutingPoint())
		{
			return new RoutePlanResolution(
				evaluation.getStatus(), leg, null,
				shouldPauseForRoutePlanForRegression(
					evaluation.getStatus(), playerLocation, target));
		}
		final Set<WorldPoint> targets = new LinkedHashSet<>(
			target.getRoutingPoints());
		WorldPoint destination = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (final WorldPoint point : targets)
		{
			final int candidateDistance = playerLocation == null
				? 0 : distance(playerLocation, point);
			if (destination == null || candidateDistance < nearestDistance)
			{
				destination = point;
				nearestDistance = candidateDistance;
			}
		}
		return new RoutePlanResolution(
			evaluation.getStatus(), leg,
			new RouteStage(
				destination, targets,
				leg.getKind() != SlayerRoutePlan.LegKind.TERMINAL,
				false, false),
			shouldPauseForRoutePlanForRegression(
				evaluation.getStatus(), playerLocation, target)
		);
	}

	static boolean shouldPauseForRoutePlanForRegression(
		final SlayerRoutePlan.ProgressStatus status,
		final WorldPoint playerLocation,
		final SlayerRoutePlan.RouteTarget target)
	{
		if (status == SlayerRoutePlan.ProgressStatus.WAITING_FOR_BRANCH)
		{
			return true;
		}
		if (status
			!= SlayerRoutePlan.ProgressStatus.WAITING_FOR_INTERACTION)
		{
			return false;
		}
		if (target == null || !target.hasRoutingPoint()
			|| playerLocation == null)
		{
			return true;
		}
		for (final WorldPoint routingPoint : target.getRoutingPoints())
		{
			if (routingPoint != null
				&& playerLocation.getPlane() == routingPoint.getPlane()
				&& distance(playerLocation, routingPoint)
					<= target.getApproachRadius())
			{
				return true;
			}
		}
		return false;
	}

	private void pauseForGuidedRoutePlan(
		final RoutePlanResolution resolution,
		final String prefix)
	{
		if (!guidedRouteWaitingForCheckpoint)
		{
			if (shortestPathBridge != null)
			{
				shortestPathBridge.clear();
			}
			taskAreaAnchor = null;
			guidedRouteWaitingForCheckpoint = true;
			clearGuidedTravelRecommendation();
		}
		guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
		final SlayerRoutePlan.Leg leg = resolution == null
			? null : resolution.leg;
		final String guidance = leg == null
			? "Waiting for the reviewed encounter arrival signal."
			: leg.getKind() == SlayerRoutePlan.LegKind.MANUAL_INTERACTION
				&& !leg.getInteractionInstruction().isEmpty()
					? leg.getInteractionInstruction()
					: leg.getGuidance();
		setGuidedSessionStatus(
			(prefix == null || prefix.trim().isEmpty()
				? "" : prefix.trim() + " — ") + guidance);
	}

	private String masterRouteIdentity(
		final SlayerMasterRouteCatalog.MasterRoute masterRoute)
	{
		if (masterRoute == null || masterRoute.getDestination() == null)
		{
			return "";
		}
		final WorldPoint destination = masterRoute.getDestination();
		return "master|id=" + masterRoute.getId()
			+ "|destination=" + destination.getX() + ","
			+ destination.getY() + "," + destination.getPlane();
	}

	private boolean isInfernoPreparationTarget(final GuidedTaskTarget target)
	{
		return target != null
			&& SlayerRouteCatalog.requiresPreparationBank(
				target.taskName, target.location, target.isBoss()
			);
	}

	private String infernoPreparationBankRouteIdentity(
		final GuidedTaskTarget target)
	{
		if (!isInfernoPreparationTarget(target))
		{
			return "";
		}
		final WorldPoint bank =
			SlayerBankRouteCatalog.getInfernoPreparationBankTarget();
		if (bank == null)
		{
			return "";
		}
		return "prepbank|" + routeIdentity(target)
			+ "|destination=" + bank.getX() + ","
			+ bank.getY() + "," + bank.getPlane();
	}

	private static boolean isInfernoRegion(final WorldPoint point)
	{
		if (point == null)
		{
			return false;
		}
		final int regionId = ((point.getX() >> 6) << 8)
			| (point.getY() >> 6);
		return regionId == INFERNO_REGION_ID;
	}

	private boolean isAtInfernoPreparationBank(final WorldPoint playerLocation)
	{
		final WorldPoint bank =
			SlayerBankRouteCatalog.getInfernoPreparationBankTarget();
		return playerLocation != null
			&& bank != null
			&& playerLocation.getPlane() == bank.getPlane()
			&& distance(playerLocation, bank) <= INFERNO_PREP_BANK_ARRIVAL_RADIUS;
	}

	private boolean isInInfernoPreparationLocalArea(
		final WorldPoint playerLocation)
	{
		final WorldPoint bank =
			SlayerBankRouteCatalog.getInfernoPreparationBankTarget();
		return playerLocation != null
			&& bank != null
			&& sameCoordinateLayer(playerLocation, bank)
			&& playerLocation.getPlane() == bank.getPlane()
			&& distance(playerLocation, bank) <= INFERNO_PREP_LOCAL_HANDOFF_RADIUS;
	}

	private WorldPoint findInfernoPreparationBankerTarget(
		final WorldPoint playerLocation)
	{
		return findLoadedNamedNpcTarget(
			playerLocation,
			INFERNO_PREP_BANKER_NAMES,
			INFERNO_PREP_BANKER_DISCOVERY_RADIUS
		);
	}

	private static boolean isManualInfernoPreparationTeleport(
		final TravelRouteMatch match)
	{
		if (match == null || match.item == null)
		{
			return false;
		}

		/*
		 * Inferno preparation is the one reviewed external first leg: Ghommal's
		 * Hilt 4+ / Ghommal's Avernic Defender 5+ teleports to Mor Ul Rek, then
		 * Shortest Path resumes locally to the inner banker. Keep this boundary
		 * here rather than depending on a newer SlayerTravelRouteCatalog helper
		 * so the runtime stays compatible with existing route-catalog versions.
		 */
		final String item = normalizeTravelName(match.item.displayName);
		final String destination = normalizeTravelName(match.destination);
		return destination.equals("mor ul rek")
			&& (item.contains("ghommal s hilt")
				|| item.contains("ghommal s avernic defender"));
	}

	private void invalidateTravelStateForProfileChange(
		final String previousRouteIdentity)
	{
		/*
		 * A profile change is a hard route-generation boundary. Discard the exact
		 * route we are leaving so switching away and back cannot resurrect stale
		 * Shortest Path state. The newly-selected route must remain eligible for its
		 * own exact cached selection until routeToTaskForGuidedSession decides whether
		 * to reuse or recalculate it.
		 */
		travelCoordinator.invalidateRoute(previousRouteIdentity);
		guidedTravelRequestGeneration = -1L;
		guidedTravelRequestRouteIdentity = "";
		if (shortestPathBridge != null)
		{
			shortestPathBridge.clear();
		}

		guidedBankTransportTracking = false;
		guidedTaskBankPickupPending = false;
		guidedDynamicTaskDestination = null;
		guidedSurfaceEntranceDestination = null;
		clearRouteDiscoveryThrottle();
		guidedEntranceReached = false;
		guidedRouteWaitingForCheckpoint = false;
		infernoPreparationBankReady = false;
		infernoPreparationManualTeleportPending = false;
		infernoPreparationHotVentPending = false;
		whispererRingTeleportPending = false;
		whispererCathedralTeleportPending = false;
		araxxorSpiderTeleportPending = false;
		guidedInfernoEntryDestination = null;
		infernoEntryPromptState = 0;
		guidedPostTransportContinuationPending = false;
		resetTormentedRouteProgress();
		restoreGuidedTeleportWidgetHighlights();
		clearTeleportHighlightCaptures();
		showGuidedTravelRecommendation("", "");
	}

	private void routeToInfernoPreparationBankForGuidedSession(
		final GuidedTaskTarget target,
		final String reason)
	{
		if (!guidedSessionActive || !isInfernoPreparationTarget(target))
		{
			return;
		}

		final WorldPoint bank =
			SlayerBankRouteCatalog.getInfernoPreparationBankTarget();
		final String identity = infernoPreparationBankRouteIdentity(target);
		if (bank == null || identity.isEmpty())
		{
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"The reviewed Inferno preparation bank route is unavailable"
			);
			return;
		}

		final Player player = client.getLocalPlayer();
		final WorldPoint playerLocation = player == null
			? null : player.getWorldLocation();
		final ItemContainer liveBank = client.getItemContainer(InventoryID.BANK);
		final WorldPoint loadedPrepBanker =
			findInfernoPreparationBankerTarget(playerLocation);
		if (liveBank != null
			&& (isAtInfernoPreparationBank(playerLocation)
				|| loadedPrepBanker != null))
		{
			cacheBankContainer(liveBank);
			infernoPreparationBankReady = true;
			infernoPreparationManualTeleportPending = false;
			infernoPreparationHotVentPending = false;
			refreshSlayerData();
			createRecommendedBankTagNow();
			final GuidedTaskTarget refreshedTarget = resolveGuidedTaskTarget();
			routeToInfernoEntryForGuidedSession(
				refreshedTarget != null ? refreshedTarget : target,
				"Already at the Inferno preparation bank"
			);
			return;
		}

		infernoPreparationBankReady = false;
		infernoPreparationManualTeleportPending = false;
		infernoPreparationHotVentPending = false;
		guidedInfernoEntryDestination = null;
		infernoEntryPromptState = 0;
		travelCoordinator.beginRoute(identity);
		guidedTravelRequestGeneration = travelCoordinator.getGeneration();
		guidedTravelRequestRouteIdentity = identity;
		guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_PREP_BANK;
		taskAreaAnchor = bank;
		restoreGuidedTeleportWidgetHighlights();
		clearTeleportHighlightCaptures();

		/*
		 * CURRENT SHORTEST PATH COMPATIBILITY
		 * -----------------------------------
		 * Ghommal's hilt 4/5/6 (and the Avernic variants) really do teleport to
		 * inner Mor Ul Rek, but Shortest Path's current public transport data does
		 * not expose that edge. Never mix that unsupported edge with a Shortest Path
		 * request: keep the owned Ghommal item authoritative in the staging Bank Tag,
		 * wait for its landing, then send only the reachable local bank walk.
		 */
		final TravelRouteMatch authoredTravel =
			findInfernoPreparationTravelItem();

		final boolean manualTeleport =
			isManualInfernoPreparationTeleport(authoredTravel);
		if (manualTeleport)
		{
			/*
			 * This is a reviewed physical route, not a calculating fallback. Resolve it
			 * so a route restart cannot discard the Hilt/Defender from the staging tag.
			 */
			applyTravelRouteMatch(authoredTravel, identity);
			infernoPreparationManualTeleportPending = true;
			infernoPreparationHotVentPending = false;
			guidedBankTransportTracking = false;
			guidedTaskBankPickupPending = false;
			/*
			 * The bank was only the eventual destination of this staged route; no
			 * Shortest Path request is active while the player performs the external
			 * Ghommal teleport. Clear the anchor with the path so landing can never
			 * mistake the future bank target for an already-issued route segment.
			 */
			taskAreaAnchor = null;
			if (shortestPathBridge != null)
			{
				shortestPathBridge.clear();
			}
			refreshOpenBankTagForTravelSelection();
			refreshGuidedTeleportWidgetHighlights();
			setGuidedSessionStatus(
				reason
					+ " - withdraw the Ghommal item shown above the 4 x 7, use Mor Ul Rek, and SlayerPlus will start Shortest Path's local Zuk-bank route immediately after you land."
			);
			return;
		}
		if (authoredTravel != null)
		{
			applyProvisionalTravelRouteMatch(authoredTravel, identity);
		}

		/*
		 * NO-HILT BRANCH
		 * ----------------
		 * Shortest Path can reach outer Mor Ul Rek, but its transport catalog has no
		 * edge for the inner-city Hot vent door. Route only to the reviewed east door
		 * first. The player performs Pass; a collision-reachable TzHaar-Ket-Yil then
		 * proves the transition and starts the separate local bank segment below.
		 */
		infernoPreparationHotVentPending = true;
		guidedBankTransportTracking = true;
		if (authoredTravel == null)
		{
			showGuidedTravelRecommendation(
				"",
				"Shortest Path is selecting the best owned route it supports to the east Mor Ul Rek Hot vent door."
			);
		}

		refreshOpenBankTagForTravelSelection();
		refreshGuidedTeleportWidgetHighlights();

		final Set<WorldPoint> approachTargets =
			SlayerBankRouteCatalog.getInfernoPreparationHotVentDoorTargets();
		final boolean sent = shortestPathBridge != null
			&& shortestPathBridge.routeToPreparationAccess(
				playerLocation,
				approachTargets,
				true,
				true,
				true
			);
		if (!sent)
		{
			travelCoordinator.invalidateRoute(identity);
			guidedBankTransportTracking = false;
			guidedTaskBankPickupPending = false;
			infernoPreparationHotVentPending = false;
			guidedTravelRequestGeneration = -1L;
			guidedTravelRequestRouteIdentity = "";
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"Unable to send the reviewed east Mor Ul Rek Hot vent door to Shortest Path"
			);
			return;
		}

		setGuidedSessionStatus(
			reason
				+ " — routing to the east Mor Ul Rek Hot vent door. Pass through the glowing barrier; SlayerPlus will then start a separate collision-checked route to the Zuk bank."
		);
	}

	private void routeToInfernoEntryForGuidedSession(
		final GuidedTaskTarget target,
		final String reason)
	{
		if (!guidedSessionActive || !isInfernoPreparationTarget(target))
		{
			return;
		}

		final Player player = client.getLocalPlayer();
		final WorldPoint rawPlayerLocation = player == null
			? null : player.getWorldLocation();
		final WorldPoint playerLocation = routeComparisonLocation(
			player, rawPlayerLocation);
		if (isInfernoRegion(playerLocation))
		{
			confirmTaskAreaForGuidedSession();
			return;
		}

		/* The staging item is banked here; it must not leak into the final setup. */
		final String prepIdentity = infernoPreparationBankRouteIdentity(target);
		travelCoordinator.invalidateRoute(prepIdentity);
		travelCoordinator.invalidateRoute(routeIdentity(target));
		guidedBankTransportTracking = false;
		guidedTaskBankPickupPending = false;
		guidedTravelRequestGeneration = -1L;
		guidedTravelRequestRouteIdentity = "";
		restoreGuidedTeleportWidgetHighlights();
		clearTeleportHighlightCaptures();
		showGuidedTravelRecommendation("", "");
		infernoEntryPromptState = 0;

		final Set<String> entryNames =
			SlayerRouteCatalog.getPreparationEntryNpcNames(
				target.taskName, target.location, target.isBoss()
			);
		final WorldPoint entry = findInfernoEntryTarget(
			playerLocation, entryNames
		);
		guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
		if (entry == null)
		{
			guidedInfernoEntryDestination = null;
			final WorldPoint entryApproach =
				SlayerRouteCatalog.getPreparationEntryApproachTarget(
					target.taskName, target.location, target.isBoss()
				);
			final boolean sent = shortestPathBridge != null
				&& entryApproach != null
				&& shortestPathBridge.routeToLocalTaskArea(
					playerLocation,
					java.util.Collections.singleton(entryApproach),
					true
				);
			if (!sent)
			{
				taskAreaAnchor = null;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
				setGuidedSessionStatus(
					"Unable to send the separate Inferno-entrance approach segment to Shortest Path"
				);
				return;
			}
			taskAreaAnchor = entryApproach;
			showInfernoEntryNextStep(false);
			setGuidedSessionStatus(
				reason
					+ " — final 28-slot Inferno setup is ready. Routing separately to the reviewed Inferno entrance approach; SlayerPlus will switch to an exact tile beside TzHaar-Ket-Keh when he loads."
			);
			return;
		}

		guidedInfernoEntryDestination = entry;
		taskAreaAnchor = entry;
		final boolean sent = shortestPathBridge != null
			&& shortestPathBridge.routeToExactLocalTarget(
				playerLocation, entry, true
			);
		if (!sent)
		{
			setGuidedSessionStatus(
				"Unable to send the exact Inferno entrance route to Shortest Path"
			);
			return;
		}

		showInfernoEntryNextStep(false);
		setGuidedSessionStatus(
			reason
				+ " — final 28-slot Inferno setup is ready. Routing to a collision-checked tile beside TzHaar-Ket-Keh; enter the Inferno from there."
		);
	}

	private void showInfernoEntryNextStep(final boolean readyToEnter)
	{
		final int state = readyToEnter ? 2 : 1;
		if (infernoEntryPromptState == state)
		{
			return;
		}
		infernoEntryPromptState = state;
		showGuidedTravelRecommendation(
			readyToEnter
				? "Enter the Inferno"
				: "Follow Shortest Path → Inferno entrance",
			readyToEnter
				? "Use TzHaar-Ket-Keh to begin the Inferno."
				: "The final 28-slot setup is ready. Follow the route to TzHaar-Ket-Keh."
		);
	}

	private void routeToTaskForGuidedSession(
		final boolean useBankItems,
		final String reason)
	{
		if (!guidedSessionActive)
		{
			return;
		}

		final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
		final Player player = client.getLocalPlayer();
		final WorldPoint rawPlayerLocation = player == null
			? null
			: player.getWorldLocation();
		final WorldPoint playerLocation = routeComparisonLocation(
			player, rawPlayerLocation
		);

		if (target == null)
		{
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"No verified Shortest Path target is available for this task location"
			);
			return;
		}

		if (confirmGuidedTaskAreaBeforePreparation(target))
		{
			return;
		}

		if (teleportHighlighter != null)
		{
			if ("wyvern cave on fossil island".equals(
				normalizeTravelName(target.location)
			))
			{
				teleportHighlighter.setRouteGuidance(
					"Digsite pendant",
					"Fossil Island",
					"Mushroom Meadow"
				);
			}
			else
			{
				teleportHighlighter.setRouteGuidance("");
			}
		}

		if (currentMasterId != KRYSTILIA_MASTER_ID
			&& SlayerRouteCatalog.isWilderness(target.location))
		{
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"Wilderness route blocked unless Krystilia assigned the task"
			);
			return;
		}

		if (routeToSpellbookIfRequired(
			"Selected Slayer method requires a different spellbook"
		))
		{
			return;
		}

		/*
		 * TzKal-Zuk is a full-run preparation workflow, not a direct boss route.
		 * Always stage at the reviewed east Mor Ul Rek bank before the exact
		 * entrance interaction. This intercept happens before generic route-stage
		 * resolution so the boss NPC (which does not exist until wave 69) can never
		 * cause a guessed or direct-to-Inferno route.
		 */
		if (isInfernoPreparationTarget(target))
		{
			if (!infernoPreparationBankReady)
			{
				routeToInfernoPreparationBankForGuidedSession(target, reason);
			}
			else
			{
				routeToInfernoEntryForGuidedSession(target, reason);
			}
			return;
		}

		guidedDynamicTaskDestination = null;
		guidedSurfaceEntranceDestination = null;
		clearRouteDiscoveryThrottle();
		guidedEntranceReached = false;
		guidedRouteWaitingForCheckpoint = false;
		final RoutePlanResolution planResolution =
			target.routePlan == null
				? null
				: resolveGuidedRoutePlan(
					target, player, rawPlayerLocation);
		if (planResolution != null && planResolution.hasArrived())
		{
			confirmTaskAreaForGuidedSession();
			return;
		}
		if (planResolution != null && (planResolution.isWaiting()
			|| planResolution.stage == null))
		{
			pauseForGuidedRoutePlan(planResolution, reason);
			return;
		}
		if (planResolution == null && stageUnsupportedEncounterTeleport(
			target,
			playerLocation,
			useBankItems,
			reason
		))
		{
			return;
		}

		final RouteStage stage = planResolution == null
			? resolveRouteStage(
				target,
				playerLocation,
				rawPlayerLocation
			)
			: planResolution.stage;
		if (stage == null || !stage.isValid())
		{
			if (shortestPathBridge != null)
			{
				shortestPathBridge.clear();
			}
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"No verified entrance or current-scene monster checkpoint exists for "
					+ target.location + ". SlayerPlus will not guess a destination."
			);
			return;
		}

		final WorldPoint destination = stage.destination;
		final Set<WorldPoint> routeTargets = stage.targets;
		final boolean avoidWilderness = currentMasterId != KRYSTILIA_MASTER_ID;
		if (stage.exactNpc)
		{
			guidedDynamicTaskDestination = destination;
		}
		boolean preferDirectConsumableTeleport = false;
		final String identity = routeIdentity(target);
		travelCoordinator.beginRoute(identity);
		/* A cached bank winner cannot own an inventory-only request after the item
		 * has left the player's carried containers. Start that request unresolved so
		 * Shortest Path can select the best route that is actually usable now. */
		final SlayerTravelSelection cachedSelection = travelCoordinator.current();
		if (!useBankItems
			&& cachedSelection.hasPhysicalItem()
			&& findCarriedTravelItemForTransport(
				cachedSelection.getItemName()
			) == null)
		{
			travelCoordinator.invalidateRoute(identity);
			travelCoordinator.beginRoute(identity);
		}
		guidedTravelRequestGeneration = travelCoordinator.getGeneration();
		guidedTravelRequestRouteIdentity = identity;
		restoreGuidedTeleportWidgetHighlights();
		clearTeleportHighlightCaptures();

		/*
		 * Most reviewed physical fallbacks remain provisional while Shortest Path
		 * calculates. Inventory-only requests may seed only a carried item; otherwise
		 * a bank-owned fallback would be displayed as though the player could use it.
		 */
		TravelRouteMatch authoredTravel = findAuthoredRouteTravelItem(target);
		if (!useBankItems
			&& authoredTravel != null
			&& findCarriedTravelItemForTransport(
				authoredTravel.item.displayName
			) == null)
		{
			authoredTravel = null;
		}
		if (authoredTravel != null)
		{
			if (isWhispererRingTravelForRegression(
				target.taskName,
				target.location,
				authoredTravel.item.displayName,
				authoredTravel.destination
			))
			{
				/*
				 * The ring lands beyond the surface/sinkhole legs. Preserve this manual
				 * first stage for both the bank-aware request and the inventory-only
				 * request issued when the bank closes.
				 */
				applyTravelRouteMatch(authoredTravel, identity);
				whispererRingTeleportPending = true;
				guidedTaskBankPickupPending = useBankItems;
				guidedBankTransportTracking = true;
				guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
				taskAreaAnchor = WHISPERER_LASSAR_RING_LANDING;
				final boolean ringStageSent = shortestPathBridge != null
					&& shortestPathBridge.routeToTaskArea(
						playerLocation,
						java.util.Collections.singleton(
							WHISPERER_LASSAR_RING_LANDING
						),
						currentMasterId != KRYSTILIA_MASTER_ID,
						useBankItems,
						useBankItems,
						true
					);
				if (!ringStageSent)
				{
					guidedBankTransportTracking = false;
					guidedTaskBankPickupPending = false;
				}
				refreshOpenBankTagForTravelSelection();
				refreshGuidedTeleportWidgetHighlights();
				setGuidedSessionStatus(
					ringStageSent
						? reason + " — use Ring of shadows -> Lassar Undercity."
						: reason + " — use Ring of shadows -> Lassar Undercity; "
							+ "Shortest Path could not draw the teleport leg."
				);
				return;
			}
			if (SlayerTravelRouteCatalog.locksAuthoredTravelItem(
				target.location
			))
			{
				applyTravelRouteMatch(authoredTravel, identity);
				if (shouldPreferDirectConsumableTeleportForRegression(
					target.location,
					authoredTravel.item.displayName
				))
				{
					/* Keep Shortest Path active, but make the reviewed direct
					 * consumable competitive with generic free Max-cape/POH
					 * detours so its native instruction matches the green item. */
					preferDirectConsumableTeleport = true;
				}
			}
			else
			{
				applyProvisionalTravelRouteMatch(
					authoredTravel,
					identity
				);
			}
		}
		else if (useBankItems)
		{
			showGuidedTravelRecommendation(
				"",
				"Shortest Path is comparing the teleport items in your bank."
			);
		}
		if (!useBankItems
			&& currentTravelSelection().hasPhysicalItem()
			&& findCarriedTravelItemForTransport(
				currentTravelSelection().getItemName()
			) != null
			&& shouldPreferDirectConsumableTeleportForRegression(
				target.location,
				currentTravelSelection().getItemName()
			))
		{
			preferDirectConsumableTeleport = true;
		}

		whispererRingTeleportPending = false;
		whispererCathedralTeleportPending = false;
		guidedBankTransportTracking = true;
		guidedTaskBankPickupPending = useBankItems;
		refreshOpenBankTagForTravelSelection();

		/*
		 * Shortest Path 1.20.6 has Max-cape Feldip Hills in its transport table,
		 * but no Spider cave teleport entry. Keep a transport-free walking fallback
		 * active instead of clearing the plugin while SlayerPlus highlights the
		 * reviewed scroll. The route is then freshly rebased from the observed
		 * landing, so it cannot retain the pre-teleport bank start.
		 */
		if (preferDirectConsumableTeleport
			&& !isAraxxorSpiderTeleportLandingForRegression(
				target.taskName,
				target.location,
				playerLocation,
				target.profile.getSurfaceAccess(),
				target.isInsideEncounterArea(playerLocation)
			))
		{
			araxxorSpiderTeleportPending = true;
			guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
			guidedBankTransportTracking = false;
			guidedTaskBankPickupPending = false;
			guidedPostTransportContinuationPending = true;
			final WorldPoint caveEntrance = target.profile.getSurfaceAccess();
			taskAreaAnchor = caveEntrance;
			final boolean fallbackSent = shortestPathBridge != null
				&& caveEntrance != null
				&& shortestPathBridge.routeToTaskAccess(
					shortestPathStart(rawPlayerLocation),
					SlayerRouteCatalog.targetArea(caveEntrance, 2),
					avoidWilderness,
					false,
					false,
					true
				);
			refreshOpenBankTagForTravelSelection();
			refreshGuidedTeleportWidgetHighlights();
			setGuidedSessionStatus(
				reason + " - use the highlighted Spider cave teleport. "
					+ (fallbackSent
						? "Shortest Path is keeping the cave entrance active and will rebase after you land."
						: "SlayerPlus will start the cave-entrance walk after you land.")
			);
			return;
		}
		araxxorSpiderTeleportPending = false;

		guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
		/*
		 * The authored travel choice may have been seeded immediately above while
		 * the phase was still transitioning. Refresh once ROUTING_TO_TASK is live so
		 * any already-open multi-destination interface is highlighted as well; future
		 * menu entries are covered by onMenuEntryAdded.
		 */
		refreshGuidedTeleportWidgetHighlights();
		final boolean localTaskRoute =
			shouldUseLocalTaskRouteForRegression(playerLocation, destination);
		if (shouldRetireTravelRecommendationForLocalRoute(
			localTaskRoute,
			guidedBankTransportTracking,
			currentTravelSelection().hasPhysicalItem()
		))
		{
			clearGuidedTravelRecommendation();
		}
		if (localTaskRoute)
		{
			guidedBankTransportTracking = false;
			guidedTaskBankPickupPending = false;
			guidedPostTransportContinuationPending = false;
		}
		else
		{
			guidedBankTransportTracking = true;
			guidedTaskBankPickupPending = useBankItems;
			/* A bank-aware transport may finish before a reviewed cave/dungeon,
			 * NPC, cannon or barrage checkpoint. Rebase only after a real world
			 * transition; the tick update consumes this latch at that boundary. */
			guidedPostTransportContinuationPending =
				requiresPostTransportContinuation(target, stage);
		}
		final WorldPoint shortestPathPlayerStart =
			shortestPathStart(rawPlayerLocation);
		final boolean sent = shortestPathBridge != null
			&& (localTaskRoute
				? shortestPathBridge.routeToLocalTaskArea(
					shortestPathPlayerStart,
					routeTargets,
					avoidWilderness,
					shouldEnableAgilityShortcutsForStage(target, destination)
				)
				: shortestPathBridge.routeToTaskArea(
					shortestPathPlayerStart,
					routeTargets,
					avoidWilderness,
					useBankItems,
					useBankItems,
					true,
					preferDirectConsumableTeleport
				));

		if (!sent)
		{
			guidedBankTransportTracking = false;
			guidedTaskBankPickupPending = false;
			guidedPostTransportContinuationPending = false;
			guidedTravelRequestGeneration = -1L;
			guidedTravelRequestRouteIdentity = "";
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"Unable to send the task route to Shortest Path"
			);
			return;
		}

		taskAreaAnchor = destination;
		taskArrivalGraceTicks = 0;
		taskAreaExitTicks = 0;
		if (planResolution != null)
		{
			final SlayerRoutePlan.Leg planLeg = planResolution.leg;
			final SlayerRoutePlan.RouteTarget planTarget = planLeg == null
				? null : planLeg.getTarget();
			final String targetLabel = planTarget == null
				? target.location
				: planTarget.getLabel();
			final String routeInstruction = useBankItems
				? " — withdraw the highlighted teleport, then follow Shortest Path to "
				: " — follow Shortest Path to ";
			final String preparationWarning = currentPreparation.isActive()
				&& !currentPreparation.isReady()
					? " " + currentPreparation.getRouteWarning()
					: "";
			setGuidedSessionStatus(
				reason + routeInstruction + targetLabel + ". "
					+ (planLeg == null ? "" : planLeg.getGuidance())
					+ preparationWarning
			);
			return;
		}
		final boolean routingToEntrance = stage.entrance;
		final boolean routingToExactNpc = stage.exactNpc;
		final boolean routingToMeiyerditchShortcut =
			isMeiyerditchShortcutStage(target, destination);
		final boolean routingToWhispererSinkhole =
			isWhispererSinkholeStage(target, destination);
		final boolean routingToWhispererTeleporter =
			WHISPERER_PALACE_TELEPORTER.equals(destination)
				&& whispererCathedralTeleporterApproachForRegression(
					target.taskName, target.location, playerLocation
				) != null;
		whispererCathedralTeleportPending = routingToWhispererTeleporter;
		final boolean routingToAquaniteSailing =
			AQUANITE_PORT_ROBERTS_MOORING.equals(destination)
				&& "ynysdail cavern".equals(normalizeTravelName(target.location));
		final String destinationText = routingToEntrance
			? routingToWhispererSinkhole
				? "the Lassar sinkhole inside Camdozaal"
				: routingToWhispererTeleporter
				? "the Palace teleporter inside Lassar Undercity"
				: routingToMeiyerditchShortcut
				? "the level-93 cave under The Hollows"
				: routingToAquaniteSailing
				? "the Port Roberts mooring point"
				: target.location + " access checkpoint"
			: routingToExactNpc
				? "a walkable tile beside the nearest exact "
					+ formatName(target.taskName)
				: stage.learnedCheckpoint
					? "the locally verified " + formatName(target.taskName)
						+ " checkpoint"
					: target.location + " interior checkpoint";
		final String routeInstruction = useBankItems
			? " — Shortest Path can use teleports in your bank. Withdraw the highlighted teleport and follow the route to "
			: " — follow the route back to ";
		final String transition = routingToEntrance
			? routingToWhispererSinkhole
				? " Descend into Lassar Undercity; SlayerPlus will continue to the Cathedral after it loads."
				: routingToWhispererTeleporter
				? " Use the teleporter and choose The Cathedral; SlayerPlus will start the final walking route after you arrive."
				: routingToMeiyerditchShortcut
				? " Enter the cave manually; the shortcut must have been cleared once from the laboratory side with 78 Mining."
				: routingToAquaniteSailing
				? " Summon or board your player-owned boat and sail to Ynysdail. SlayerPlus will resume at the island landing and route to the cavern."
				: " " + target.profile.getTransitionInstruction()
			: "";
		final String preparationWarning = currentPreparation.isActive()
			&& !currentPreparation.isReady()
				? " " + currentPreparation.getRouteWarning()
				: "";
		setGuidedSessionStatus(
			reason
				+ routeInstruction
				+ destinationText
				+ "."
				+ transition
				+ preparationWarning
		);
	}

	/**
	 * Terminal evidence always wins over preparation. This prevents a client
	 * restart, world hop, or panel restart inside an encounter from routing the
	 * player back out merely because gear or a spellbook differs from the setup.
	 */
	private boolean confirmGuidedTaskAreaBeforePreparation(
		final GuidedTaskTarget target)
	{
		if (!guidedSessionActive || target == null)
		{
			return false;
		}
		final Player player = client.getLocalPlayer();
		final WorldPoint rawPlayerLocation = player == null
			? null : player.getWorldLocation();
		if (rawPlayerLocation == null)
		{
			return false;
		}
		final WorldPoint comparisonLocation = routeComparisonLocation(
			player, rawPlayerLocation);
		if (isInfernoPreparationTarget(target)
			&& isInfernoRegion(comparisonLocation))
		{
			confirmTaskAreaForGuidedSession();
			return true;
		}
		if (target.routePlan == null)
		{
			return false;
		}

		ensureGuidedRoutePlanState(target);
		final SlayerRouteEvidence evidence = buildGuidedRouteEvidence(
			player, rawPlayerLocation);
		if (guidedRoutePlan == null || !guidedRoutePlan.hasArrived(evidence))
		{
			return false;
		}
		confirmTaskAreaForGuidedSession();
		return true;
	}

	private boolean requiresPostTransportContinuation(
		final GuidedTaskTarget target,
		final RouteStage stage)
	{
		return target != null
			&& stage != null
			&& (target.routePlan != null
				|| stage.entrance
				|| target.isStaged()
				|| target.requiresLoadedNpc()
				|| (target.profile != null
					&& target.profile.isAccessThenNpc())
				|| selectedCannonPosition(target) != null
				|| selectedBarragePosition(target) != null);
	}

	static boolean shouldPreferDirectConsumableTeleportForRegression(
		final String location,
		final String itemName)
	{
		return "morytania spider cave".equals(normalizeTravelName(location))
			&& "spider cave teleport".equals(normalizeTravelName(itemName));
	}

	static boolean isAraxxorSpiderTeleportLandingForRegression(
		final String taskName,
		final String location,
		final WorldPoint playerLocation,
		final WorldPoint surfaceAccess,
		final boolean insideEncounterArea)
	{
		return "araxxor".equals(normalizeTravelName(taskName))
			&& "morytania spider cave".equals(normalizeTravelName(location))
			&& playerLocation != null
			&& (insideEncounterArea
				|| distance(playerLocation, surfaceAccess)
					<= ARAXXOR_SPIDER_TELEPORT_LANDING_RADIUS);
	}

	private boolean stageUnsupportedEncounterTeleport(
		final GuidedTaskTarget target,
		final WorldPoint playerLocation,
		final boolean useBankItems,
		final String reason)
	{
		if (target == null
			|| !isUnsupportedEncounterTeleportLocation(target.location))
		{
			return false;
		}

		final boolean skotizo = normalizeTravelName(target.location)
			.equals("skotizo s lair");
		final boolean tormented = normalizeTravelName(target.location)
			.equals("ancient guthixian temple");
		if (tormented)
		{
			return stageTormentedDemonRoute(
				target, playerLocation, useBankItems, reason
			);
		}
		/*
		 * A Dark totem is the altar key, not transport from the bank. Let the
		 * normal staged route lead to King Rada's statue first. Once the player
		 * loads the Catacombs, own the short local altar leg; after the lair
		 * loads, release control back to the normal exact-NPC route.
		 */
		if (skotizo && (!isOnCatacombsCoordinateLayer(playerLocation)
			|| isOnSkotizoLairCoordinateLayer(playerLocation)))
		{
			return false;
		}

		final String identity = routeIdentity(target);
		if (useBankItems || !travelCoordinator.ownsCurrentRoute(identity))
		{
			travelCoordinator.beginRoute(identity);
			guidedTravelRequestGeneration = travelCoordinator.getGeneration();
			guidedTravelRequestRouteIdentity = identity;
		}

		final TravelRouteMatch authoredTravel = skotizo
			? encounterKeyTravelMatch("dark totem", "Skotizo's Lair")
			: findAuthoredRouteTravelItem(target);
		if (authoredTravel != null
			&& isUnsupportedEncounterTeleportItem(
				target.location,
				authoredTravel.item.displayName
			))
		{
			applyTravelRouteMatch(authoredTravel, identity);
		}

		guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
		guidedBankTransportTracking = false;
		guidedTaskBankPickupPending = false;
		guidedDynamicTaskDestination = null;
		guidedSurfaceEntranceDestination = null;
		guidedEntranceReached = false;
		guidedRouteWaitingForCheckpoint = false;
		guidedPostTransportContinuationPending = false;

		if (skotizo && distance(playerLocation, SKOTIZO_CATACOMBS_ALTAR) > 3)
		{
			if (!SKOTIZO_CATACOMBS_ALTAR.equals(taskAreaAnchor))
			{
				taskAreaAnchor = SKOTIZO_CATACOMBS_ALTAR;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.routeToExactLocalTarget(
						playerLocation,
						SKOTIZO_CATACOMBS_ALTAR,
						true
					);
				}
			}
			refreshOpenBankTagForTravelSelection();
			refreshGuidedTeleportWidgetHighlights();
			setGuidedSessionStatus(
				reason + " - follow Shortest Path to the central Catacombs altar, then use the highlighted Dark totem."
			);
			return true;
		}

		taskAreaAnchor = null;
		if (shortestPathBridge != null)
		{
			shortestPathBridge.clear();
		}
		refreshOpenBankTagForTravelSelection();
		refreshGuidedTeleportWidgetHighlights();

		final SlayerTravelSelection selection = currentTravelSelection();
		final boolean hasRequiredItem = selection.hasPhysicalItem()
			&& isUnsupportedEncounterTeleportItem(
				target.location,
				selection.getItemName()
			);
		final String instruction = normalizeTravelName(target.location)
			.equals("skotizo s lair")
				? "Use the highlighted Dark totem on the Catacombs altar. Shortest Path will resume inside Skotizo's lair."
				: "Use the highlighted Guthixian temple teleport. Shortest Path will resume inside beside the Tormented demons.";
		setGuidedSessionStatus(
			hasRequiredItem
				? reason + " - " + instruction
				: "The required manual entrance item was not found. " + instruction
		);
		return true;
	}

	private void resetTormentedRouteProgress()
	{
		tormentedLightCreatureAttracted = false;
		tormentedTempleReached = false;
		guidedTormentedChasmTarget = null;
	}

	private boolean stageTormentedDemonRoute(
		final GuidedTaskTarget target,
		final WorldPoint playerLocation,
		final boolean useBankItems,
		final String reason)
	{
		if (TORMENTED_MANUAL_CHASM_STAGE.equals(taskAreaAnchor)
			&& !isOnTearsOfGuthixUpperLayer(playerLocation)
			&& !isOnAncientGuthixianTempleLayer(playerLocation))
		{
			/* The manual sentinel is meaningful only on the Tears upper layer or
			 * inside the temple transition. Death/banking/scene exit must restart it. */
			taskAreaAnchor = null;
			resetTormentedRouteProgress();
		}
		if (TORMENTED_DIRECT_SCROLL_STAGE.equals(taskAreaAnchor)
			&& !isOnAncientGuthixianTempleLayer(playerLocation))
		{
			taskAreaAnchor = null;
		}
		if (tormentedTempleReached
			&& !isOnAncientGuthixianTempleLayer(playerLocation))
		{
			/* Temple arrival is evidence for the current scene only. A bank detour,
			 * death or other scene exit must allow the full entrance route again. */
			resetTormentedRouteProgress();
		}
		/*
		 * A loaded demon is the authoritative proof that the skull transition is
		 * complete. The lower chasm shares the broad temple coordinate layer, so
		 * coordinates alone previously released an impossible cross-wall path.
		 */
		final WorldPoint loadedDemonTarget = gameTickSequence == 0
			|| gameTickSequence % ROUTE_DISCOVERY_INTERVAL_TICKS == 0
				? findLoadedNamedNpcTarget(
					playerLocation,
					java.util.Collections.singleton("tormented demon"),
					DYNAMIC_TASK_NPC_RADIUS
				)
				: null;
		if (loadedDemonTarget != null)
		{
			tormentedTempleReached = true;
			return false;
		}
		if (tormentedTempleReached)
		{
			return false;
		}

		/* A direct scroll lands beyond every manual chasm transition. */
		if (TORMENTED_DIRECT_SCROLL_STAGE.equals(taskAreaAnchor)
			&& isOnAncientGuthixianTempleLayer(playerLocation))
		{
			return false;
		}

		if (TORMENTED_MANUAL_CHASM_STAGE.equals(taskAreaAnchor))
		{
			guidedBankTransportTracking = false;
			guidedTaskBankPickupPending = false;
			guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
			if (isOnTearsOfGuthixUpperLayer(playerLocation))
			{
				setGuidedSessionStatus(tormentedLightCreatureAttracted
					? "The light creature is ready — click it and choose Into the chasm."
					: "At the light creatures — use the lit Sapphire lantern on one."
				);
				return true;
			}

			routeThroughTormentedLowerChasm(playerLocation);
			return true;
		}

		final String identity = routeIdentity(target);
		TravelRouteMatch directScroll = encounterKeyTravelMatch(
			"guthixian temple teleport", "Ancient Guthixian Temple"
		);
		if (directScroll != null
			&& !mayUseEncounterTravelMatch(
				useBankItems,
				findCarriedTravelItemForTransport(
					directScroll.item.displayName
				) != null
			))
		{
			/* A cached bank snapshot proves ownership, not that the item survived
			 * bank close. Inventory-only continuation must fall through to the
			 * carried Games-necklace/POH route instead of highlighting an item the
			 * player cannot click. */
			directScroll = null;
		}
		if (directScroll != null)
		{
			final SlayerTravelSelection selection = currentTravelSelection();
			final boolean selectionChanged = !selection.hasPhysicalItem()
				|| !normalizeTravelName(selection.getItemName())
					.contains("guthixian temple teleport");
			if (useBankItems || !travelCoordinator.ownsCurrentRoute(identity))
			{
				travelCoordinator.beginRoute(identity);
				guidedTravelRequestGeneration = travelCoordinator.getGeneration();
				guidedTravelRequestRouteIdentity = identity;
			}
			if (selectionChanged)
			{
				applyTravelRouteMatch(directScroll, identity);
				refreshOpenBankTagForTravelSelection();
				refreshGuidedTeleportWidgetHighlights();
			}

			guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
			guidedBankTransportTracking = false;
			guidedTaskBankPickupPending = false;
			guidedPostTransportContinuationPending = false;
			if (!TORMENTED_DIRECT_SCROLL_STAGE.equals(taskAreaAnchor))
			{
				taskAreaAnchor = TORMENTED_DIRECT_SCROLL_STAGE;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
			}
			setGuidedSessionStatus(
				reason + " - use the highlighted Guthixian temple teleport. Shortest Path will resume once the temple loads."
			);
			return true;
		}

		/*
		 * Wiki fallback: Games necklace (carried or POH jewellery box) to Tears,
		 * then a lit sapphire lantern on a light creature. Shortest Path owns the
		 * bank/POH/overland portion only; manual interface interactions stay manual.
		 */
		if (isOnTearsOfGuthixUpperLayer(playerLocation))
		{
			guidedBankTransportTracking = false;
			guidedTaskBankPickupPending = false;
			guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
			final WorldPoint loadedLightCreatureTarget =
				findLoadedNamedNpcTarget(
					playerLocation,
					TORMENTED_LIGHT_CREATURE_NAMES,
					32
				);
			final WorldPoint lightCreatureTarget =
				loadedLightCreatureTarget == null
					? TORMENTED_LIGHT_CREATURE_APPROACH
					: loadedLightCreatureTarget;
			if (distance(playerLocation, lightCreatureTarget) <= 6)
			{
				taskAreaAnchor = TORMENTED_MANUAL_CHASM_STAGE;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
				if (tormentedLightCreatureAttracted)
				{
					showGuidedTravelRecommendation(
						"Light creature → Into the chasm",
						"Click the attracted light creature and choose Into the chasm."
					);
					setGuidedSessionStatus(
						"The light creature is ready — click it and choose Into the chasm."
					);
				}
				else
				{
					showGuidedTravelRecommendation(
						"Lit Sapphire lantern",
						"Use it on a light creature, then descend into the chasm."
					);
					setGuidedSessionStatus(
						"At the light creatures — use the lit Sapphire lantern on one."
					);
				}
				return true;
			}
			if (shouldSubmitTormentedPath(
				false,
				taskAreaAnchor,
				lightCreatureTarget
			))
			{
				taskAreaAnchor = lightCreatureTarget;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.routeToExactLocalTarget(
						playerLocation,
						lightCreatureTarget,
						true
					);
				}
			}
			setGuidedSessionStatus(
				"Follow Shortest Path to a walkable tile beside the nearest light creature; a lit Sapphire lantern is required."
			);
			return true;
		}

		final boolean routeChanged = useBankItems
			|| guidedTaskBankPickupPending
			|| !travelCoordinator.ownsCurrentRoute(identity);
		if (routeChanged)
		{
			travelCoordinator.beginRoute(identity);
			guidedTravelRequestGeneration = travelCoordinator.getGeneration();
			guidedTravelRequestRouteIdentity = identity;
			final TravelRouteMatch gamesNecklace = encounterKeyTravelMatch(
				"games necklace", "Tears of Guthix"
			);
			if (gamesNecklace != null)
			{
				applyProvisionalTravelRouteMatch(gamesNecklace, target);
			}
			else
			{
				showGuidedTravelRecommendation(
					"",
					"Shortest Path is comparing a carried Games necklace, a POH jewellery box route, and the overland fallback."
				);
			}
			refreshOpenBankTagForTravelSelection();
			refreshGuidedTeleportWidgetHighlights();
		}

		guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
		guidedBankTransportTracking = true;
		guidedTaskBankPickupPending = useBankItems;
		guidedPostTransportContinuationPending = false;
		if (shouldSubmitTormentedPath(
			routeChanged,
			taskAreaAnchor,
			TORMENTED_TEARS_LANDING
		))
		{
			taskAreaAnchor = TORMENTED_TEARS_LANDING;
			if (shortestPathBridge != null)
			{
				shortestPathBridge.routeToTaskArea(
					playerLocation,
					java.util.Collections.singleton(TORMENTED_TEARS_LANDING),
					true,
					useBankItems,
					useBankItems,
					true
				);
			}
		}
		setGuidedSessionStatus(
			reason + " - routing to Tears of Guthix. Fastest available order is direct Games necklace, POH jewellery box, then the overland cave route. Bring the lit Sapphire lantern shown in the Bank Tag."
		);
		return true;
	}

	static boolean mayUseEncounterTravelMatch(
		final boolean bankItemsAvailable,
		final boolean carried)
	{
		return bankItemsAvailable || carried;
	}

	private void routeThroughTormentedLowerChasm(
		final WorldPoint playerLocation)
	{
		seedLoadedTormentedChasmObjectsIfNeeded();
		WorldPoint objectLocation = nearestSamePlaneLocation(
			playerLocation,
			loadedTormentedChasmWallLocations,
			64
		);
		final boolean wallStep = objectLocation != null;
		if (objectLocation == null)
		{
			objectLocation = nearestSamePlaneLocation(
				playerLocation,
				loadedTormentedChasmExitLocations,
				64
			);
		}

		if (objectLocation == null)
		{
			if (shortestPathBridge != null)
			{
				shortestPathBridge.clear();
			}
			guidedTormentedChasmTarget = null;
			showGuidedTravelRecommendation(
				"Continue south",
				"The next wall or skull entrance has not loaded yet."
			);
			setGuidedSessionStatus(
				"Inside the lower chasm — continue south until the next stone wall loads."
			);
			return;
		}

		final WorldPoint interactionTarget =
			nearestWalkableEntranceInteractionTile(
				objectLocation,
				playerLocation
			);
		if (interactionTarget == null)
		{
			return;
		}

		if (!interactionTarget.equals(guidedTormentedChasmTarget))
		{
			guidedTormentedChasmTarget = interactionTarget;
			if (shortestPathBridge != null)
			{
				shortestPathBridge.routeToExactLocalTarget(
					playerLocation,
					interactionTarget,
					true
				);
			}
		}

		final String action = wallStep
			? "Climb the stone wall"
			: "Enter the skull";
		showGuidedTravelRecommendation(
			action,
			distance(playerLocation, interactionTarget) <= 3
				? "Use the highlighted interaction, then the next route segment will appear."
				: "Follow Shortest Path to the next required interaction."
		);
		setGuidedSessionStatus(
			distance(playerLocation, interactionTarget) <= 3
				? action + "."
				: "Follow Shortest Path to " + action.toLowerCase(Locale.ENGLISH) + "."
		);
	}

	private void seedLoadedTormentedChasmObjectsIfNeeded()
	{
		if (!tormentedChasmSceneSeedPending || client == null)
		{
			return;
		}
		tormentedChasmSceneSeedPending = false;
		final WorldView worldView = client.getTopLevelWorldView();
		final Scene scene = worldView == null ? null : worldView.getScene();
		if (scene == null || scene.getTiles() == null)
		{
			tormentedChasmSceneSeedPending = true;
			return;
		}

		for (final Tile[][] planeTiles : scene.getTiles())
		{
			if (planeTiles == null)
			{
				continue;
			}
			for (final Tile[] column : planeTiles)
			{
				if (column == null)
				{
					continue;
				}
				for (final Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					final GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects != null)
					{
						for (final GameObject object : gameObjects)
						{
							observeLoadedTormentedChasmObject(object);
						}
					}
					observeLoadedTormentedChasmObject(tile.getWallObject());
					observeLoadedTormentedChasmObject(tile.getGroundObject());
					observeLoadedTormentedChasmObject(tile.getDecorativeObject());
				}
			}
		}
	}

	private void observeLoadedTormentedChasmObject(final TileObject object)
	{
		if (!guidedSessionActive
			|| currentTaskVariant != SlayerTaskVariant.TORMENTED_DEMONS
			|| object == null
			|| client == null
			|| object.getWorldLocation() == null)
		{
			return;
		}
		try
		{
			ObjectComposition composition =
				client.getObjectDefinition(object.getId());
			if (composition != null && composition.getImpostorIds() != null)
			{
				final ObjectComposition impostor = composition.getImpostor();
				if (impostor != null)
				{
					composition = impostor;
				}
			}
			if (composition == null)
			{
				return;
			}
			if (isTormentedChasmWallForRegression(
				composition.getName(), composition.getActions()))
			{
				loadedTormentedChasmWallLocations.add(object.getWorldLocation());
			}
			else if (isTormentedChasmExitForRegression(
				composition.getName(), composition.getActions()))
			{
				loadedTormentedChasmExitLocations.add(object.getWorldLocation());
			}
		}
		catch (RuntimeException ignored)
		{
			/* A transformed object can disappear while its definition is read. */
		}
	}

	private void forgetLoadedTormentedChasmObject(final TileObject object)
	{
		if (object == null || object.getWorldLocation() == null)
		{
			return;
		}
		loadedTormentedChasmWallLocations.remove(object.getWorldLocation());
		loadedTormentedChasmExitLocations.remove(object.getWorldLocation());
	}

	static boolean isTormentedChasmWallForRegression(
		final String name,
		final String[] actions)
	{
		final String normalizedName = normalizeTravelName(name);
		return normalizedName.contains("wall")
			&& hasActionContaining(actions, "climb");
	}

	static boolean isTormentedChasmExitForRegression(
		final String name,
		final String[] actions)
	{
		final String normalizedName = normalizeTravelName(name);
		return (hasActionContaining(actions, "enter")
				|| hasActionContaining(actions, "climb through"))
			&& (normalizedName.contains("skull")
				|| normalizedName.contains("cave")
				|| normalizedName.contains("entrance")
				|| normalizedName.contains("opening"));
	}

	private static boolean hasActionContaining(
		final String[] actions,
		final String expected)
	{
		if (actions == null || expected == null)
		{
			return false;
		}
		final String normalizedExpected = normalizeTravelName(expected);
		for (final String action : actions)
		{
			if (normalizeTravelName(action).contains(normalizedExpected))
			{
				return true;
			}
		}
		return false;
	}

	private static WorldPoint nearestSamePlaneLocation(
		final WorldPoint origin,
		final Set<WorldPoint> locations,
		final int maximumRadius)
	{
		if (origin == null || locations == null)
		{
			return null;
		}
		WorldPoint nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (final WorldPoint location : locations)
		{
			if (location == null || location.getPlane() != origin.getPlane())
			{
				continue;
			}
			final int candidateDistance = distance(origin, location);
			if (candidateDistance <= maximumRadius
				&& candidateDistance < nearestDistance)
			{
				nearest = location;
				nearestDistance = candidateDistance;
			}
		}
		return nearest;
	}

	static boolean isOnTearsOfGuthixLayer(final WorldPoint point)
	{
		return point != null && point.getRegionID() == 12948;
	}

	static boolean isOnTearsOfGuthixUpperLayer(final WorldPoint point)
	{
		return isOnTearsOfGuthixLayer(point) && point.getPlane() == 2;
	}

	static boolean isOnAncientGuthixianTempleLayer(final WorldPoint point)
	{
		return point != null
			&& point.getPlane() == 0
			&& point.getX() >= 4032
			&& point.getX() <= 4223
			&& point.getY() >= 4352
			&& point.getY() <= 4543;
	}

	static WorldPoint tormentedTearsLandingForRegression()
	{
		return TORMENTED_TEARS_LANDING;
	}

	static WorldPoint tormentedLightCreatureApproachForRegression()
	{
		return TORMENTED_LIGHT_CREATURE_APPROACH;
	}

	static boolean shouldSubmitTormentedPath(
		final boolean routeChanged,
		final WorldPoint currentAnchor,
		final WorldPoint destination)
	{
		return routeChanged || destination == null
			|| !destination.equals(currentAnchor);
	}

	private TravelRouteMatch encounterKeyTravelMatch(
		final String itemFamily,
		final String destination)
	{
		final TravelItemMatch item = findOwnedTravelItem(itemFamily);
		if (item == null)
		{
			return null;
		}
		return new TravelRouteMatch(
			item,
			destination,
			java.util.Collections.singleton(itemFamily)
		);
	}

	static boolean isOnCatacombsCoordinateLayer(final WorldPoint point)
	{
		return point != null
			&& point.getPlane() == 0
			&& point.getX() >= 1536
			&& point.getX() <= 1791
			&& point.getY() >= 9856
			&& point.getY() <= 10239;
	}

	static boolean isOnSkotizoLairCoordinateLayer(final WorldPoint point)
	{
		return point != null && point.getRegionID() == SKOTIZO_LAIR_REGION_ID;
	}

	static WorldPoint skotizoCatacombsAltarForRegression()
	{
		return SKOTIZO_CATACOMBS_ALTAR;
	}

	private static boolean isUnsupportedEncounterTeleportLocation(
		final String location)
	{
		final String normalized = normalizeTravelName(location);
		return normalized.equals("ancient guthixian temple")
			|| normalized.equals("skotizo s lair");
	}

	static boolean isUnsupportedEncounterTeleportItem(
		final String location,
		final String itemName)
	{
		final String normalizedLocation = normalizeTravelName(location);
		final String normalizedItem = normalizeTravelName(itemName);
		return (normalizedLocation.equals("ancient guthixian temple")
				&& normalizedItem.contains("guthixian temple teleport"))
			|| (normalizedLocation.equals("skotizo s lair")
				&& normalizedItem.equals("dark totem"));
	}

	private void routeToMasterForGuidedSession()
	{
		routeToMasterForGuidedSession(false);
	}

	private void routeToMasterForGuidedSession(final boolean useBankItems)
	{
		if (!guidedSessionActive)
		{
			return;
		}

		clearGuidedTravelRecommendation();

		final int masterId = getRoutingMasterId();
		final SlayerMasterRouteCatalog.MasterRoute masterRoute =
			SlayerMasterRouteCatalog.find(masterId);
		if (masterRoute == null)
		{
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"Task complete, but the last-used Slayer master route is unavailable"
			);
			return;
		}

		final TravelRouteMatch carriedMasterTeleport =
			findCarriedMasterReturnTeleport(masterRoute);
		final TravelRouteMatch bankMasterTeleport = bankScanned
			? findBankMasterReturnTeleport(masterRoute)
			: null;
		final SlayerGuidedLifecycleContract.MasterReturnPlan returnPlan =
			SlayerGuidedLifecycleContract.masterReturnPlan(
				carriedMasterTeleport != null,
				bankScanned,
				bankMasterTeleport != null
			);
		if (!useBankItems
			&& returnPlan
				== SlayerGuidedLifecycleContract.MasterReturnPlan.BANK_OWNED_TELEPORT)
		{
			routeToBankForGuidedSession(
				"Task complete — the fastest Slayer-master teleport is in your bank"
			);
			guidedMasterBankPickupPending =
				guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_BANK;
			return;
		}
		final TravelRouteMatch selectedMasterTeleport =
			carriedMasterTeleport != null
				? carriedMasterTeleport
				: useBankItems ? bankMasterTeleport : null;

		/*
		 * POST-TASK TRAVEL AUTHORITY:
		 * Returning to a Slayer master is a real guided route and must publish the
		 * same immutable travel selection used by task routes. Source precedence is
		 * carried teleport, known bank-owned teleport, then physical path. A known
		 * bank-only winner first creates a bank leg; opening that bank resumes this
		 * exact master route with bank access enabled.
		 */
		final String identity = masterRouteIdentity(masterRoute);
		travelCoordinator.beginRoute(identity);
		guidedTravelRequestGeneration = travelCoordinator.getGeneration();
		guidedTravelRequestRouteIdentity = identity;
		guidedBankTransportTracking = true;
		guidedMasterBankPickupPending = useBankItems;
		guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_MASTER;
		restoreGuidedTeleportWidgetHighlights();
		clearTeleportHighlightCaptures();
		showGuidedTravelRecommendation(
			"",
			useBankItems
				? "Shortest Path is selecting the fastest carried or bank-owned teleport for your Slayer master."
				: "Shortest Path is selecting the carried teleport for your Slayer master."
		);

		/*
		 * Do not leave the player without guidance while Shortest Path calculates
		 * its transport edge. Master-return teleports are a small reviewed list, so
		 * publish the first carried, diary-valid option immediately. A later live
		 * Shortest Path transport callback remains authoritative and may replace it.
		 */
		final Player player = client.getLocalPlayer();
		final WorldPoint playerLocation = player == null
			? null : player.getWorldLocation();
		if (masterId == 10 && isMortimerCavernForRegression(playerLocation))
		{
			mortimerSlayerRingTeleportPending = false;
			final boolean localSent = shortestPathBridge != null
				&& shortestPathBridge.routeToExactLocalTarget(
					playerLocation,
					masterRoute.getDestination(),
					true
				);
			setGuidedSessionStatus(
				localSent
					? "Inside Wyrmscraig Cavern — routing locally to Mortimer."
					: "Inside Wyrmscraig Cavern, but the local route to Mortimer could not be drawn."
			);
			return;
		}
		if (masterId == 10 && selectedMasterTeleport != null)
		{
			/*
			 * The current installed Shortest Path transport tables do not yet contain
			 * Wyrmscraig. Own the new Slayer-ring edge explicitly, then hand only the
			 * short walk inside the loaded cavern back to Shortest Path after landing.
			 */
			applyTravelRouteMatch(selectedMasterTeleport, identity);
			mortimerSlayerRingTeleportPending = true;
			taskAreaAnchor = MORTIMER_SLAYER_RING_LANDING;
			refreshGuidedTeleportWidgetHighlights();
			setGuidedSessionStatus(
				useBankItems
					? "Withdraw the highlighted Slayer ring, then use Wyrmscraig Cavern. Routing will resume inside to Mortimer."
					: "Use the highlighted Slayer ring -> Wyrmscraig Cavern. Routing will resume inside to Mortimer."
			);
			return;
		}
		if (masterId == 10)
		{
			/* Do not submit an unreachable interior coordinate to an older
			 * Shortest Path release when the required manual teleport is absent. */
			travelCoordinator.invalidateRoute(identity);
			guidedBankTransportTracking = false;
			guidedTravelRequestGeneration = -1L;
			guidedTravelRequestRouteIdentity = "";
			setGuidedSessionStatus(
				"Carry a Slayer ring with the Wyrmscraig Cavern teleport to return to Mortimer."
			);
			return;
		}
		if (selectedMasterTeleport != null)
		{
			applyProvisionalTravelRouteMatch(
				selectedMasterTeleport,
				identity
			);
		}

		final boolean sent = shortestPathBridge != null
			&& (useBankItems
				? shortestPathBridge.routeToAny(
					shortestPathStart(playerLocation),
					singletonRouteTarget(masterRoute.getDestination()),
					masterId != KRYSTILIA_MASTER_ID,
					true,
					true
				)
				: shortestPathBridge.routeToTrackedInventory(
					masterRoute.getDestination(),
					masterId != KRYSTILIA_MASTER_ID
				));
		if (!sent)
		{
			travelCoordinator.invalidateRoute(identity);
			guidedBankTransportTracking = false;
			guidedTravelRequestGeneration = -1L;
			guidedTravelRequestRouteIdentity = "";
			guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
			setGuidedSessionStatus(
				"Unable to send the Slayer master route to Shortest Path"
			);
			return;
		}

		taskAreaAnchor = null;
		taskArrivalGraceTicks = 0;
		taskAreaExitTicks = 0;
		refreshGuidedTeleportWidgetHighlights();
		setGuidedSessionStatus(
			isPointBoosting()
				? pointBoostRouteStatus(masterRoute)
				: "Task complete — routing to "
					+ masterRoute.getName()
					+ (useBankItems
						? " using the open bank and teleports you carry."
						: " using teleports you carry.")
		);
	}

	private String pointBoostRouteStatus(
		final SlayerMasterRouteCatalog.MasterRoute masterRoute)
	{
		final SlayerPointBoostCoordinator.Decision decision =
			pointBoostDecision(client.getVarbitValue(
				VarbitID.SLAYER_TASKS_COMPLETED
			));
		return "Point boosting — routing to " + masterRoute.getName()
			+ " for " + decision.getProgressText()
			+ " using teleports you carry.";
	}

	private TravelRouteMatch findCarriedMasterReturnTeleport(
		final SlayerMasterRouteCatalog.MasterRoute masterRoute)
	{
		if (masterRoute == null)
		{
			return null;
		}

		for (final String family : masterRoute.getReturnItemFamilies())
		{
			final TravelItemMatch item = findCarriedTravelItem(family);
			if (item == null || !achievementDiaries.allowsTravelItem(family))
			{
				continue;
			}

			final Set<String> equivalentFamilies = new LinkedHashSet<>();
			equivalentFamilies.add(family);
			return new TravelRouteMatch(
				item,
				masterRoute.getReturnDestination(family),
				equivalentFamilies
			);
		}
		return null;
	}

	private TravelRouteMatch findBankMasterReturnTeleport(
		final SlayerMasterRouteCatalog.MasterRoute masterRoute)
	{
		if (masterRoute == null)
		{
			return null;
		}

		for (final String family : masterRoute.getReturnItemFamilies())
		{
			final TravelItemMatch item = findBankTravelItem(family);
			if (item == null || !achievementDiaries.allowsTravelItem(family))
			{
				continue;
			}

			final Set<String> equivalentFamilies = new LinkedHashSet<>();
			equivalentFamilies.add(family);
			return new TravelRouteMatch(
				item,
				masterRoute.getReturnDestination(family),
				equivalentFamilies
			);
		}
		return null;
	}

	private void confirmTaskAreaForGuidedSession()
	{
		if (!guidedSessionActive)
		{
			return;
		}

		final Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		final boolean newlyConfirmed =
			guidedSessionPhase != GuidedSessionPhase.TASKING;
		guidedSessionPhase = GuidedSessionPhase.TASKING;
		taskAreaAnchor = player.getWorldLocation();
		guidedDynamicTaskDestination = null;
		guidedSurfaceEntranceDestination = null;
		guidedEntranceReached = false;
		taskArrivalGraceTicks = TASK_ARRIVAL_GRACE_TICKS;
		taskAreaExitTicks = 0;
		if (newlyConfirmed)
		{
			if (shortestPathBridge != null)
			{
				shortestPathBridge.clear();
			}
			/*
			 * Arrival ends the travel leg. Leaving its physical teleport resolved made
			 * the panel keep showing "Ring of shadows -> Lassar Undercity" and left its
			 * menu highlight active during the encounter even after the path was cleared.
			 */
			clearGuidedTravelRecommendation();
			final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
			final String positioning = target == null
				|| target.profile == null
				|| target.profile.getPositioningNote().isEmpty()
					? ""
					: " " + target.profile.getPositioningNote();
			clearGuidedTaskDetourSnapshot();
			setGuidedSessionStatus(
				"At the task area."
					+ positioning
					+ " SlayerPlus will route you back if you leave before finishing."
			);
		}
	}

	private void updateGuidedSessionPosition()
	{
		if (!guidedSessionActive
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		final WorldPoint playerLocation = player.getWorldLocation();
		if (playerLocation == null)
		{
			return;
		}

		final WorldPoint previousPlayerLocation = guidedLastPlayerLocation;
		final WorldPoint currentTemplateLocation = routeComparisonLocation(
			player, playerLocation);
		final boolean routeEvidenceMoved =
			!java.util.Objects.equals(previousPlayerLocation, playerLocation)
			|| !java.util.Objects.equals(
				guidedLastTemplateLocation, currentTemplateLocation);
		final boolean routeEvidenceHistoryChanged = routeEvidenceMoved
			|| !java.util.Objects.equals(
				guidedPreviousPlayerLocation, previousPlayerLocation)
			|| !java.util.Objects.equals(
				guidedPreviousTemplateLocation, guidedLastTemplateLocation);
		guidedPreviousPlayerLocation = previousPlayerLocation;
		guidedPreviousTemplateLocation = guidedLastTemplateLocation;
		guidedLastPlayerLocation = playerLocation;
		guidedLastTemplateLocation = currentTemplateLocation;
		if (routeEvidenceHistoryChanged)
		{
			routeEvidenceVersion++;
		}

		/*
		 * Assignment transitions are event-driven, but this state invariant closes
		 * the startup/world-hop race where the first observed task can arrive while
		 * the old route-to-master phase is still active.
		 */
		if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER
			&& effectiveTaskRemaining() > 0)
		{
			final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
			if (confirmGuidedTaskAreaBeforePreparation(target))
			{
				return;
			}
			if (routeToSpellbookIfRequired(
				"Assigned task requires a different spellbook"
			))
			{
				return;
			}
			if (isInfernoPreparationTarget(target))
			{
				routeToInfernoPreparationBankForGuidedSession(
					target,
					"Assigned task detected after leaving the Slayer master"
				);
			}
			else
			{
				routeToBankForGuidedSession(
					"Assigned task detected — routing to the nearest bank"
				);
			}
			return;
		}

		/*
		 * A teleport invalidates the origin used by the in-flight bank route. Re-send
		 * the complete reviewed bank set from the landing tile; Shortest Path then
		 * selects the nearest reachable bank. Opening it still performs the existing
		 * bank-scan -> task-route handoff, so the assignment is never discarded.
		 */
		if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_BANK
			&& effectiveTaskRemaining() > 0
			&& shouldRebaseBankRoute(
				previousPlayerLocation,
				playerLocation
			))
		{
			routeToBankForGuidedSession(
				"Teleport detected — recalculating the nearest bank from your landing point"
			);
			return;
		}

		if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_SPELLBOOK)
		{
			if (currentPreparation != null
				&& currentPreparation.isSpellbookReady())
			{
				handleGuidedSpellbookReadinessTransition();
				return;
			}

			if (guidedPohSpellbookAltarReached)
			{
				setGuidedSessionStatus(
					"Use the loaded POH altar to switch to "
						+ currentPreparation.getSpellbookName()
						+ ". SlayerPlus will route back to a bank after the switch."
				);
				return;
			}

			if (guidedPohSpellbookTeleportPending)
			{
				final WorldView worldView = client.getTopLevelWorldView();
				final boolean instanced = worldView != null && worldView.isInstance();
				if (!instanced)
				{
					setGuidedSessionStatus(
						"Use the highlighted cape -> Teleport to house. SlayerPlus will verify the altar after the POH loads."
					);
					return;
				}

				if (guidedPohSpellbookLoadTicks == 0
					&& loadedPohSpellbookAltarId <= 0)
				{
					/* One scene seed handles enabling/restarting SlayerPlus after the
					 * house was already loaded. Spawn events handle normal entry. */
					seedLoadedPohSpellbookAltarFromScene();
				}
				guidedPohSpellbookLoadTicks++;
				if (pohAltarSupportsSpellbookForRegression(
					loadedPohSpellbookAltarId,
					currentPreparation.getSpellbookName()
				))
				{
					clearGuidedTravelRecommendation();
					guidedPohSpellbookTeleportPending = false;
					guidedPohSpellbookAltarReached = true;
					setGuidedSessionStatus(
						"POH altar found — use it to switch to "
							+ currentPreparation.getSpellbookName()
							+ ". SlayerPlus will continue after the switch."
					);
					return;
				}

				if (guidedPohSpellbookLoadTicks < 5)
				{
					setGuidedSessionStatus(
						"POH loaded — checking its spellbook altar."
					);
					return;
				}

				guidedPohSpellbookTeleportPending = false;
				guidedPohSpellbookFallbackActive = true;
				clearGuidedTravelRecommendation();
				routeToSpellbookIfRequired(
					"The loaded POH has no compatible altar — using the reviewed world change point"
				);
				return;
			}
			final SlayerSpellbookRouteCatalog.Route route =
				SlayerSpellbookRouteCatalog.resolve(
					currentPreparation == null ? ""
						: currentPreparation.getSpellbookName(),
					client.getVarbitValue(SPELLBOOK_VARBIT)
				);
			if (route == null)
			{
				return;
			}

			if (shouldRebaseBankRoute(previousPlayerLocation, playerLocation)
				&& distance(playerLocation, route.getDestination()) > 6)
			{
				routeToSpellbookIfRequired(
					"Travel detected — recalculating the spellbook-change route"
				);
				return;
			}

			final boolean atChangePoint = distance(
				playerLocation, route.getDestination()
			) <= 6;
			setGuidedSessionStatus(
				atChangePoint
					? "At the " + route.getSpellbookName()
						+ " change point — " + route.getInteraction()
						+ " SlayerPlus will continue automatically after the spellbook changes."
					: "Routing to the reviewed " + route.getSpellbookName()
						+ " change point."
			);
			return;
		}

		if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER)
		{
			final SlayerMasterRouteCatalog.MasterRoute masterRoute =
				SlayerMasterRouteCatalog.find(getRoutingMasterId());
			if (masterRoute != null
				&& masterRoute.getId() == 10
				&& mortimerSlayerRingTeleportPending)
			{
				if (!isMortimerCavernForRegression(playerLocation))
				{
					setGuidedSessionStatus(
						"Use the highlighted Slayer ring -> Wyrmscraig Cavern."
					);
					return;
				}

				mortimerSlayerRingTeleportPending = false;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
				clearGuidedTravelRecommendation();
				final boolean localSent = shortestPathBridge != null
					&& shortestPathBridge.routeToExactLocalTarget(
						playerLocation,
						masterRoute.getDestination(),
						true
					);
				setGuidedSessionStatus(
					localSent
						? "Wyrmscraig Cavern reached — routing locally to Mortimer."
						: "Wyrmscraig Cavern reached, but the local route to Mortimer could not be drawn."
				);
				return;
			}
			if (masterRoute != null
				&& distance(playerLocation, masterRoute.getDestination())
					<= MASTER_ARRIVAL_RADIUS)
			{
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
				clearGuidedTravelRecommendation();
				guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
				setGuidedSessionStatus(
					"At "
						+ masterRoute.getName()
						+ " — get a new task and SlayerPlus will route you to a bank"
				);
			}
			return;
		}

		if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK)
		{
			final WorldPoint bank =
				SlayerBankRouteCatalog.getInfernoPreparationBankTarget();

			/*
			 * The named inner-city banker is the authoritative arrival signal. Before
			 * the Mor Ul Rek teleport the player can be beside an unrelated bank; after
			 * landing, only TzHaar-Ket-Yil satisfies this exact lookup.
			 */
			if (gameTickSequence == 0
				|| gameTickSequence % ROUTE_DISCOVERY_INTERVAL_TICKS == 0)
			{
				final WorldPoint liveBankTarget =
					findInfernoPreparationBankerTarget(playerLocation);
				if (liveBankTarget != null)
				{
					final boolean startingManualBankSegment =
						infernoPreparationManualTeleportPending
							|| infernoPreparationHotVentPending;
					/*
					 * Arrival at the exact named banker ends the unsupported first leg. Keep
					 * the prep-bank route identity alive but unresolved so UI consumers cannot
					 * fall back to a stale task teleport while Shortest Path walks locally.
					 */
					if (startingManualBankSegment)
					{
						infernoPreparationManualTeleportPending = false;
						infernoPreparationHotVentPending = false;
						guidedBankTransportTracking = false;
						final String prepIdentity = guidedTravelRequestRouteIdentity;
						if (prepIdentity != null && !prepIdentity.isEmpty())
						{
							travelCoordinator.beginRoute(prepIdentity);
							guidedTravelRequestGeneration =
								travelCoordinator.getGeneration();
						}
						restoreGuidedTeleportWidgetHighlights();
						clearTeleportHighlightCaptures();
						showGuidedTravelRecommendation("", "");
					}

					if (startingManualBankSegment
						|| taskAreaAnchor == null
						|| !taskAreaAnchor.equals(liveBankTarget))
					{
						final boolean sent = shortestPathBridge != null
							&& shortestPathBridge.routeToExactLocalTarget(
								playerLocation,
								liveBankTarget,
								true
							);
						if (sent)
						{
							taskAreaAnchor = liveBankTarget;
						}
						else
						{
							taskAreaAnchor = null;
							setGuidedSessionStatus(
								"Mor Ul Rek reached, but SlayerPlus could not start the separate local route to TzHaar-Ket-Yil."
							);
							return;
						}
					}
					showGuidedTravelRecommendation(
						"Open the east Mor Ul Rek bank",
						"Open the bank beside TzHaar-Ket-Yil to finalize the Inferno setup."
					);
					setGuidedSessionStatus(
						"Routing to a collision-checked tile beside TzHaar-Ket-Yil at the east Mor Ul Rek (Zuk) bank — open the bank there to finalize the Inferno setup."
					);
					return;
				}
			}

			if (infernoPreparationHotVentPending)
			{
				final WorldPoint hotVent =
					SlayerBankRouteCatalog.getInfernoPreparationHotVentDoorTarget();
				final boolean atHotVent = hotVent != null
					&& playerLocation.getPlane() == hotVent.getPlane()
					&& distance(playerLocation, hotVent) <= 4;
				taskAreaAnchor = hotVent;
				setGuidedSessionStatus(
					atHotVent
						? "At the east Mor Ul Rek Hot vent door — Pass through the glowing barrier. SlayerPlus will detect the reachable banker and start the local Zuk-bank route after you cross."
						: "Routing to the east Mor Ul Rek Hot vent door; pass the glowing barrier there to enter the Zuk-bank side."
				);
				return;
			}

			if (infernoPreparationManualTeleportPending)
			{
				/*
				 * Do not wait forever on NPC load timing. Ghommal lands on Mor Ul Rek's
				 * inner coordinate layer near this bank; once that landing is observed,
				 * hand walking back to Shortest Path using its own reviewed bank targets.
				 */
				if (isInInfernoPreparationLocalArea(playerLocation))
				{
					infernoPreparationManualTeleportPending = false;
					guidedBankTransportTracking = false;
					final String prepIdentity = guidedTravelRequestRouteIdentity;
					if (prepIdentity != null && !prepIdentity.isEmpty())
					{
						travelCoordinator.beginRoute(prepIdentity);
						guidedTravelRequestGeneration =
							travelCoordinator.getGeneration();
					}
					restoreGuidedTeleportWidgetHighlights();
					clearTeleportHighlightCaptures();
					showGuidedTravelRecommendation("", "");
					final boolean handedOff = shortestPathBridge != null
						&& bank != null
						&& shortestPathBridge.routeToLocalBankTarget(
							playerLocation,
							bank,
							true
						);
					taskAreaAnchor = handedOff ? bank : null;
					setGuidedSessionStatus(
						handedOff
							? "Mor Ul Rek reached — Shortest Path is now routing locally to its exact east Mor Ul Rek bank destination; SlayerPlus will lock onto TzHaar-Ket-Yil as soon as he is loaded."
							: "Mor Ul Rek reached, but SlayerPlus could not hand the local Zuk-bank walk to Shortest Path."
					);
					return;
				}

				taskAreaAnchor = null;
				setGuidedSessionStatus(
					"Use the highlighted Ghommal -> Mor Ul Rek teleport. Shortest Path cannot model that teleport itself; SlayerPlus will hand it the bank walk after you land."
				);
				return;
			}

			final boolean bankApproachReached = playerLocation != null
				&& bank != null
				&& playerLocation.getPlane() == bank.getPlane()
				&& distance(playerLocation, bank) <= INFERNO_PREP_BANK_APPROACH_RADIUS;
			taskAreaAnchor = bank;
			setGuidedSessionStatus(
				bankApproachReached
					? "East Mor Ul Rek Zuk-bank destination reached — waiting for TzHaar-Ket-Yil to load, then SlayerPlus will finish on an exact collision-safe banker tile."
					: "Routing to Shortest Path's exact east Mor Ul Rek (Zuk) bank destination for final Inferno preparation."
			);
			return;
		}

		if (effectiveTaskRemaining() <= 0)
		{
			return;
		}

		if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_TASK)
		{
			final GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
			if (target == null)
			{
				return;
			}
			final WorldPoint routePlayerLocation = routeComparisonLocation(
				player,
				playerLocation
			);

			if (araxxorSpiderTeleportPending)
			{
				final boolean landed = isAraxxorSpiderTeleportLandingForRegression(
					target.taskName,
					target.location,
					playerLocation,
					target.profile.getSurfaceAccess(),
					target.isInsideEncounterArea(playerLocation)
				);
				if (!landed)
				{
					setGuidedSessionStatus(
						"Use the highlighted Spider cave teleport. Shortest Path will start at the cave entrance after you land."
					);
					return;
				}

				araxxorSpiderTeleportPending = false;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
				clearGuidedTravelRecommendation();
				routeToTaskForGuidedSession(
					false,
					"Spider cave teleport landing reached"
				);
				return;
			}
			final WorldPoint whispererPlayerLocation = routePlayerLocation;
			final Widget sanityPercentage = client.getWidget(
				InterfaceID.Sanity.SANITY_PERCENTAGE
			);
			final boolean whispererArenaVisible =
				shouldConfirmWhispererArenaForRegression(
					target.taskName,
					sanityPercentage != null && !sanityPercentage.isHidden()
				);

			if (whispererRingTeleportPending)
			{
				final boolean landedInLassar =
					whispererArenaVisible
						|| target.isInsideEncounterArea(whispererPlayerLocation)
						|| isWhispererLassarInteriorForRegression(
							target.taskName,
							target.location,
							whispererPlayerLocation
						);
				if (!landedInLassar)
				{
					setGuidedSessionStatus(
						"Use Ring of shadows -> Lassar Undercity."
					);
					return;
				}

				whispererRingTeleportPending = false;
				if (whispererArenaVisible)
				{
					confirmTaskAreaForGuidedSession();
					return;
				}
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
				clearGuidedTravelRecommendation();
				routeToTaskForGuidedSession(
					false,
					"Lassar Undercity reached"
				);
				return;
			}

			if (whispererCathedralTeleportPending)
			{
				if (!isWhispererCathedralTeleporterLandingForRegression(
					target.taskName, target.location, whispererPlayerLocation
				))
				{
					setGuidedSessionStatus(
						"Follow Shortest Path to the Palace teleporter, then choose The Cathedral."
					);
					return;
				}

				whispererCathedralTeleportPending = false;
				if (shortestPathBridge != null)
				{
					shortestPathBridge.clear();
				}
				routeToTaskForGuidedSession(
					false,
					"Cathedral teleporter reached"
				);
				return;
			}

			/*
			 * Between Whisperer kills the boss NPC is absent, but Jagex's Sanity
			 * interface remains visible throughout the arena. That interface is the
			 * authoritative pre-spawn/between-kills arrival signal and prevents the
			 * task route from continuing to calculate while the player is already ready
			 * to start the encounter.
			 */
			if (whispererArenaVisible)
			{
				confirmTaskAreaForGuidedSession();
				return;
			}

			final RoutePlanResolution planResolution =
				target.routePlan == null
					? null
					: resolveGuidedRoutePlan(
						target, player, playerLocation);
			if (planResolution != null && planResolution.hasArrived())
			{
				confirmTaskAreaForGuidedSession();
				return;
			}
			if (planResolution != null && (planResolution.isWaiting()
				|| planResolution.stage == null))
			{
				pauseForGuidedRoutePlan(
					planResolution, "Continuing the reviewed encounter route");
				return;
			}

			/*
			 * Instanced boss arenas can expose dynamic WorldPoints which do not fall in
			 * the static template-area bounds. The exact assigned boss being loaded near
			 * the player is stronger arrival evidence than those coordinates. Check only
			 * while routing and at the existing discovery cadence to avoid combat-time
			 * scene scans.
			 */
			final String bossDiscoveryIdentity = routeDiscoveryIdentity(target);
			if (planResolution == null && target.isBoss()
				&& routeDiscoveryDue(
					gameTickSequence,
					lastTaskNpcDiscoveryTick,
					bossDiscoveryIdentity,
					lastTaskNpcDiscoveryIdentity
				))
			{
				lastTaskNpcDiscoveryTick = gameTickSequence;
				lastTaskNpcDiscoveryIdentity = bossDiscoveryIdentity;
				if (hasLoadedAssignedBossNearby(
					playerLocation,
					target.taskName,
					target.profile
				))
				{
					confirmTaskAreaForGuidedSession();
					return;
				}
			}

			if (isInfernoPreparationTarget(target))
			{
				if (isInfernoRegion(currentTemplateLocation))
				{
					confirmTaskAreaForGuidedSession();
					return;
				}
				if (!infernoPreparationBankReady)
				{
					routeToInfernoPreparationBankForGuidedSession(
						target, "Inferno preparation is not finalized"
					);
					return;
				}

				WorldPoint entry = guidedInfernoEntryDestination;
				if (entry == null
					&& (gameTickSequence == 0
						|| gameTickSequence % ROUTE_DISCOVERY_INTERVAL_TICKS == 0))
				{
					entry = findInfernoEntryTarget(
						playerLocation,
						SlayerRouteCatalog.getPreparationEntryNpcNames(
							target.taskName, target.location, target.isBoss()
						)
					);
					if (entry != null)
					{
						guidedInfernoEntryDestination = entry;
					}
				}

				if (entry == null)
				{
					final WorldPoint entryApproach =
						SlayerRouteCatalog.getPreparationEntryApproachTarget(
							target.taskName,
							target.location,
							target.isBoss()
						);
					if (entryApproach != null
						&& (taskAreaAnchor == null
							|| !taskAreaAnchor.equals(entryApproach)))
					{
						final boolean sent = shortestPathBridge != null
							&& shortestPathBridge.routeToLocalTaskArea(
								playerLocation,
								java.util.Collections.singleton(entryApproach),
								true
							);
						taskAreaAnchor = sent ? entryApproach : null;
						if (!sent)
						{
							setGuidedSessionStatus(
								"Unable to send the separate Inferno-entrance approach segment to Shortest Path"
							);
							return;
						}
					}
					setGuidedSessionStatus(
						"Final Inferno setup is ready — routing to the reviewed Inferno entrance approach so TzHaar-Ket-Keh can load."
					);
					return;
				}

				if (taskAreaAnchor == null || !taskAreaAnchor.equals(entry))
				{
					taskAreaAnchor = entry;
					if (shortestPathBridge != null)
					{
						shortestPathBridge.routeToExactLocalTarget(
							playerLocation, entry, true
						);
					}
				}

				if (distance(playerLocation, entry) <= 3)
				{
					showInfernoEntryNextStep(true);
					setGuidedSessionStatus(
						"At TzHaar-Ket-Keh — enter the Inferno. The final 28-slot loadout is ready."
					);
				}
				else
				{
					showInfernoEntryNextStep(false);
					setGuidedSessionStatus(
						"Routing to a collision-checked tile beside TzHaar-Ket-Keh for Inferno entry."
					);
				}
				return;
			}

			if (planResolution == null
				&& isUnsupportedEncounterTeleportLocation(target.location)
				&& stageUnsupportedEncounterTeleport(
					target,
					playerLocation,
					false,
					"Continuing the reviewed encounter route"
				))
			{
				return;
			}

			/*
			 * GLOBAL DATA-DRIVEN MULTI-STAGE ROUTING RULE:
			 * One resolver owns every valid branch and continuation, but a reviewed
			 * task may require as many stages as its bank, teleport, dungeon entrance,
			 * interior transition, and exact endpoint demand. There is no second copy
			 * of Catacombs/cave/NPC/cannon precedence here.
			 */
			final RouteStage stage = planResolution == null
				? resolveRouteStage(
					target,
					routePlayerLocation,
					playerLocation
				)
				: planResolution.stage;
			if (stage == null || !stage.isValid())
			{
				if (!guidedRouteWaitingForCheckpoint)
				{
					/* The previous entrance route is no longer authoritative after
					 * crossing a cave/instance boundary. Clear it once, then wait for
					 * the exact local continuation without thrashing Shortest Path. */
					if (shortestPathBridge != null)
					{
						shortestPathBridge.clear();
					}
					taskAreaAnchor = null;
					guidedRouteWaitingForCheckpoint = true;
				}
				setGuidedSessionStatus(
					"Waiting for the exact reviewed transition or "
						+ formatName(target.taskName)
						+ " checkpoint for " + target.location + "."
				);
				return;
			}
			guidedRouteWaitingForCheckpoint = false;

			final WorldPoint destination = stage.destination;
			final WorldPoint methodPosition = selectedCannonPosition(target);
			final boolean methodStage = methodPosition != null
				&& destination.equals(methodPosition);
			final boolean destinationChanged = taskAreaAnchor == null
				|| !taskAreaAnchor.equals(destination);
			final boolean postTransportTransition =
				guidedPostTransportContinuationPending
					&& shouldRebaseBankRoute(
						previousPlayerLocation,
						playerLocation
					);

			if (destinationChanged || postTransportTransition)
			{
				taskAreaAnchor = destination;
				if (!stage.exactNpc)
				{
					guidedDynamicTaskDestination = null;
				}
				final boolean localTaskRoute =
					shouldUseLocalTaskRouteForRegression(
						routePlayerLocation, destination
					);
				/* This is the post-bank continuation, not another bank-pickup request.
				 * Keep accepting its transport result without recursively reissuing it. */
				guidedTaskBankPickupPending = false;
				if (shouldRetireTravelRecommendationForLocalRoute(
					localTaskRoute,
					guidedBankTransportTracking,
					currentTravelSelection().hasPhysicalItem()
				))
				{
					clearGuidedTravelRecommendation();
				}
				if (shortestPathBridge != null)
				{
					if (localTaskRoute)
					{
						shortestPathBridge.routeToLocalTaskArea(
							shortestPathStart(playerLocation),
							stage.targets,
							currentMasterId != KRYSTILIA_MASTER_ID,
							shouldEnableAgilityShortcutsForStage(
								target, destination
							)
						);
					}
					else
					{
						shortestPathBridge.routeToTaskArea(
							shortestPathStart(playerLocation),
							stage.targets,
							currentMasterId != KRYSTILIA_MASTER_ID,
							false,
							false,
							true
						);
					}
				}
				guidedPostTransportContinuationPending = false;
			}

			if (planResolution != null)
			{
				final SlayerRoutePlan.Leg planLeg = planResolution.leg;
				final SlayerRoutePlan.RouteTarget planTarget = planLeg == null
					? null : planLeg.getTarget();
				setGuidedSessionStatus(
					(planLeg == null
						? "Following the reviewed encounter route."
						: planLeg.getGuidance())
						+ (planTarget == null
							? "" : " Next checkpoint: "
								+ planTarget.getLabel() + ".")
				);
				return;
			}

			if (stage.entrance)
			{
				final boolean meiyerditchShortcut =
					isMeiyerditchShortcutStage(target, destination);
				final boolean whispererSinkhole =
					isWhispererSinkholeStage(target, destination);
				final boolean exactLoadedEntrance =
					guidedSurfaceEntranceDestination != null
						&& guidedSurfaceEntranceDestination.equals(destination);
				final int arrival = exactLoadedEntrance
					? 3
					: target.arrivalRadius(routePlayerLocation);
				if (distance(routePlayerLocation, destination) <= arrival)
				{
					guidedEntranceReached = true;
					setGuidedSessionStatus(whispererSinkhole
						? "At the Lassar sinkhole — descend into the Undercity. SlayerPlus will continue to the Cathedral after it loads."
						: meiyerditchShortcut
						? "At the level-93 cave under The Hollows — enter it manually. It must have been cleared once from the laboratory side with 78 Mining."
						: "At the exact " + target.location
							+ " transition — "
							+ target.profile.getTransitionInstruction());
				}
				else
				{
					setGuidedSessionStatus(whispererSinkhole
						? "Routing through Camdozaal to the Lassar sinkhole."
						: meiyerditchShortcut
						? "Routing through the Myreque tunnels to the level-93 Meiyerditch cave shortcut."
						: "Routing to the reviewed " + target.location
							+ " transition checkpoint.");
				}
				return;
			}

			if (methodStage)
			{
				setGuidedSessionStatus(
					"Inside " + target.location
						+ " — routing to the exact reviewed dwarf multicannon setup tile."
				);
				if (distance(routePlayerLocation, destination) <= 1)
				{
					confirmTaskAreaForGuidedSession();
				}
				return;
			}

			if (stage.exactNpc)
			{
				setGuidedSessionStatus(
					"Inside " + target.location
						+ " — routing to a collision-checked tile beside the nearest exact "
						+ formatName(target.taskName) + "."
				);
			}
			else if (stage.learnedCheckpoint)
			{
				setGuidedSessionStatus(
					"Using the versioned locally verified "
						+ formatName(target.taskName)
						+ " checkpoint for " + target.location + "."
				);
			}
			else
			{
				setGuidedSessionStatus(
					"Inside " + target.location
						+ " — following the reviewed interior route toward "
						+ formatName(target.taskName) + "."
				);
			}

			if (distance(routePlayerLocation, destination)
				<= target.arrivalRadius(routePlayerLocation))
			{
				confirmTaskAreaForGuidedSession();
			}
			return;
		}

		if (guidedSessionPhase != GuidedSessionPhase.TASKING)
		{
			return;
		}

		if (taskArrivalGraceTicks > 0)
		{
			taskAreaAnchor = playerLocation;
			taskArrivalGraceTicks--;
			taskAreaExitTicks = 0;
			return;
		}

		if (taskAreaAnchor == null)
		{
			taskAreaAnchor = playerLocation;
			return;
		}

		final GuidedTaskTarget activeTarget = resolveGuidedTaskTarget();
		final SlayerRouteCatalog.RouteProfile activeProfile = activeTarget == null
			? null : activeTarget.profile;
		/*
		 * Large caves and multi-room encounters can legitimately move the player
		 * farther than the small anchor radius. While the reviewed encounter bounds
		 * still contain the player, follow their position and do not mistake normal
		 * movement for a bank detour. Once outside those bounds, the short confirmation
		 * window remains as protection against transition/instance jitter.
		 */
		final WorldPoint activeRouteLocation = routeComparisonLocation(
			player,
			playerLocation
		);
		final boolean activeRoutePlan = activeTarget != null
			&& activeTarget.routePlan != null
			&& ensureGuidedRoutePlanState(activeTarget);
		if (activeRoutePlan
			&& guidedRoutePlan.isWithinActivityRetention(
				buildGuidedRouteEvidence(player, playerLocation)))
		{
			taskAreaAnchor = playerLocation;
			taskAreaExitTicks = 0;
			return;
		}
		if (activeProfile != null
			&& !activeRoutePlan
			&& activeProfile.isInsideEncounterArea(activeRouteLocation))
		{
			taskAreaAnchor = playerLocation;
			taskAreaExitTicks = 0;
			return;
		}

		final boolean outsideTaskArea = activeRoutePlan
			? distance(playerLocation, taskAreaAnchor) > TASK_EXIT_RADIUS
			: shouldCountTaskAreaExitForRegression(
				playerLocation,
				taskAreaAnchor,
				activeProfile
			);
		if (outsideTaskArea)
		{
			taskAreaExitTicks++;
			if (taskAreaExitTicks >= TASK_EXIT_CONFIRM_TICKS)
			{
				routeToBankForGuidedSession(
					"You left the unfinished task area — routing to the nearest bank before returning"
				);
			}
		}
		else
		{
			taskAreaExitTicks = 0;
		}
	}

	static boolean shouldCountTaskAreaExitForRegression(
		final WorldPoint playerLocation,
		final WorldPoint anchor,
		final SlayerRouteCatalog.RouteProfile profile)
	{
		return (profile == null || !profile.isInsideEncounterArea(playerLocation))
			&& distance(playerLocation, anchor) > TASK_EXIT_RADIUS;
	}

	static boolean isMortimerCavernForRegression(final WorldPoint location)
	{
		return location != null
			&& location.getPlane() == MORTIMER_MASTER_DESTINATION.getPlane()
			&& (distance(location, MORTIMER_SLAYER_RING_LANDING)
				<= MORTIMER_CAVERN_RADIUS
				|| distance(location, MORTIMER_MASTER_DESTINATION)
					<= MORTIMER_CAVERN_RADIUS);
	}

	/**
	 * Finds the real interactable surface entrance once it is inside the loaded
	 * scene. The authored catalog point is intentionally only an approach anchor.
	 * Restricting discovery to a small radius around that anchor prevents an
	 * unrelated cave/ladder elsewhere in the scene from stealing the route.
	 */
	private WorldPoint findLoadedSurfaceEntrance(
		final SlayerRouteCatalog.RouteProfile profile,
		final WorldPoint authoredAccess)
	{
		final SlayerRouteCatalog.TransitionSpec transition = profile == null
			? null
			: profile.getTransitionSpec();
		if (authoredAccess == null || client == null || transition == null)
		{
			return null;
		}

		if (routeTransitionSceneSeedPending)
		{
			seedLoadedRouteTransitionObjectsFromScene();
		}

		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (final TileObject object : loadedRouteTransitionObjects)
		{
			final WorldPoint candidate = entranceLocation(
				object,
				authoredAccess,
				transition
			);
			if (candidate == null)
			{
				continue;
			}
			final int candidateDistance = distance(candidate, authoredAccess);
			if (candidateDistance < bestDistance)
			{
				best = candidate;
				bestDistance = candidateDistance;
			}
		}
		return best;
	}

	private void observeLoadedRouteTransitionObject(final TileObject object)
	{
		if (object != null)
		{
			loadedRouteTransitionObjects.add(object);
		}
	}

	private void forgetLoadedRouteTransitionObject(final TileObject object)
	{
		if (object != null)
		{
			loadedRouteTransitionObjects.remove(object);
		}
	}

	private void seedLoadedRouteTransitionObjectsFromScene()
	{
		final WorldView worldView = client == null
			? null : client.getTopLevelWorldView();
		final Scene scene = worldView == null ? null : worldView.getScene();
		if (scene == null || scene.getTiles() == null)
		{
			return;
		}

		loadedRouteTransitionObjects.clear();
		for (final Tile[][] planeTiles : scene.getTiles())
		{
			if (planeTiles == null)
			{
				continue;
			}
			for (final Tile[] column : planeTiles)
			{
				if (column == null)
				{
					continue;
				}
				for (final Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					final GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects != null)
					{
						for (final GameObject object : gameObjects)
						{
							observeLoadedRouteTransitionObject(object);
						}
					}
					observeLoadedRouteTransitionObject(tile.getWallObject());
					observeLoadedRouteTransitionObject(tile.getGroundObject());
					observeLoadedRouteTransitionObject(tile.getDecorativeObject());
				}
			}
		}
		routeTransitionSceneSeedPending = false;
	}

	private void observeLoadedRoutePlanObject(final TileObject object)
	{
		if (guidedRoutePlan == null || object == null)
		{
			return;
		}
		final Set<SlayerRouteEvidence.ObjectAction> actions =
			routePlanObjectActions(object);
		if (actions.isEmpty())
		{
			return;
		}
		final Set<SlayerRouteEvidence.ObjectAction> previous =
			loadedRoutePlanObjectActions.put(object, actions);
		if (!actions.equals(previous))
		{
			routeEvidenceVersion++;
		}
	}

	private void forgetLoadedRoutePlanObject(final TileObject object)
	{
		if (object != null
			&& loadedRoutePlanObjectActions.remove(object) != null)
		{
			routeEvidenceVersion++;
		}
	}

	private Set<SlayerRouteEvidence.ObjectAction> routePlanObjectActions(
		final TileObject object)
	{
		if (object == null || guidedRoutePlanObjectSpecs.isEmpty()
			|| (!guidedRoutePlanObjectIds.contains(object.getId())
				&& !guidedRoutePlanHasNameOnlyObjectSpecs))
		{
			return java.util.Collections.emptySet();
		}

		final ObjectComposition composition;
		try
		{
			ObjectComposition resolved = client.getObjectDefinition(object.getId());
			if (resolved != null && resolved.getImpostorIds() != null)
			{
				final ObjectComposition impostor = resolved.getImpostor();
				if (impostor != null)
				{
					resolved = impostor;
				}
			}
			composition = resolved;
		}
		catch (RuntimeException ignored)
		{
			return java.util.Collections.emptySet();
		}
		if (composition == null || composition.getActions() == null)
		{
			return java.util.Collections.emptySet();
		}

		final Set<SlayerRouteEvidence.ObjectAction> result =
			new LinkedHashSet<>();
		for (final String action : composition.getActions())
		{
			if (action == null || action.trim().isEmpty())
			{
				continue;
			}
			final SlayerRouteEvidence.ObjectAction observation;
			try
			{
				observation = SlayerRouteEvidence.ObjectAction.of(
					object.getId(), composition.getName(), action
				);
			}
			catch (IllegalArgumentException ignored)
			{
				continue;
			}
			if (matchesAnyRoutePlanObjectSpec(observation))
			{
				result.add(observation);
			}
		}
		return result.isEmpty()
			? java.util.Collections.emptySet()
			: java.util.Collections.unmodifiableSet(result);
	}

	private boolean matchesAnyRoutePlanObjectSpec(
		final SlayerRouteEvidence.ObjectAction observation)
	{
		if (observation == null)
		{
			return false;
		}
		for (final SlayerRoutePredicate.ObjectActionSpec spec
			: guidedRoutePlanObjectSpecs)
		{
			final boolean identityMatches =
				(!spec.getObjectIds().isEmpty()
					&& spec.getObjectIds().contains(observation.getObjectId()))
				|| (!spec.getObjectNames().isEmpty()
					&& spec.getObjectNames().contains(
						observation.getObjectName()));
			if (identityMatches
				&& spec.getActions().contains(observation.getAction()))
			{
				return true;
			}
		}
		return false;
	}

	private void seedLoadedRoutePlanObjects()
	{
		if (!routePlanObjectSceneSeedPending || guidedRoutePlan == null)
		{
			return;
		}
		if (routeTransitionSceneSeedPending)
		{
			seedLoadedRouteTransitionObjectsFromScene();
		}
		loadedRoutePlanObjectActions.clear();
		for (final TileObject object : loadedRouteTransitionObjects)
		{
			observeLoadedRoutePlanObject(object);
		}
		routePlanObjectSceneSeedPending = false;
	}

	private void seedLoadedRoutePlanNpcs()
	{
		if (!routePlanNpcSceneSeedPending || guidedRoutePlan == null)
		{
			return;
		}
		loadedRoutePlanNpcs.clear();
		final WorldView worldView = client == null
			? null : client.getTopLevelWorldView();
		if (worldView != null)
		{
			for (final NPC npc : worldView.npcs())
			{
				if (routePlanNpcMatchesActivePlan(npc))
				{
					loadedRoutePlanNpcs.add(npc);
				}
			}
		}
		routePlanNpcSceneSeedPending = false;
		routeEvidenceVersion++;
	}

	private boolean routePlanNpcMatchesActivePlan(final NPC npc)
	{
		if (npc == null || npc.getName() == null
			|| guidedRoutePlanNpcNames.isEmpty())
		{
			return false;
		}
		return guidedRoutePlanNpcNames.contains(
			SlayerRouteEvidence.normalize(npc.getName()));
	}

	private WorldPoint entranceLocation(
		final TileObject object,
		final WorldPoint authoredAccess,
		final SlayerRouteCatalog.TransitionSpec transition)
	{
		if (object == null || authoredAccess == null || transition == null)
		{
			return null;
		}

		final WorldPoint location = object.getWorldLocation();
		if (location == null
			|| location.getPlane() != authoredAccess.getPlane()
			|| distance(location, authoredAccess) > transition.getSearchRadius())
		{
			return null;
		}

		ObjectComposition composition;
		try
		{
			composition = client.getObjectDefinition(object.getId());
			if (composition != null && composition.getImpostorIds() != null)
			{
				final ObjectComposition impostor = composition.getImpostor();
				if (impostor != null)
				{
					composition = impostor;
				}
			}
		}
		catch (RuntimeException ignored)
		{
			return null;
		}
		if (composition == null)
		{
			return null;
		}

		final String name = normalizeTravelName(composition.getName());
		if (!transition.matchesObjectName(name))
		{
			return null;
		}
		final String[] actions = composition.getActions();
		if (actions == null)
		{
			return null;
		}

		for (final String action : actions)
		{
			final String normalizedAction = normalizeTravelName(action);
			if (!transition.matchesAction(normalizedAction))
			{
				continue;
			}

			/* Generic Use remains conservative unless the transition names an object. */
			if (normalizedAction.equals("use")
				&& !transition.hasObjectNameHints()
				&& !name.contains("cave")
				&& !name.contains("entrance")
				&& !name.contains("hole")
				&& !name.contains("crevice")
				&& !name.contains("tunnel")
				&& !name.contains("stairs")
				&& !name.contains("ladder"))
			{
				continue;
			}

			return nearestWalkableEntranceInteractionTile(
				location,
				authoredAccess
			);
		}

		return null;
	}

	private WorldPoint nearestWalkableEntranceInteractionTile(
		final WorldPoint objectLocation,
		final WorldPoint authoredAccess)
	{
		if (objectLocation == null)
		{
			return null;
		}
		if (isWalkableSceneTile(objectLocation))
		{
			return objectLocation;
		}

		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				final WorldPoint candidate = new WorldPoint(
					objectLocation.getX() + dx,
					objectLocation.getY() + dy,
					objectLocation.getPlane()
				);
				if (!isWalkableSceneTile(candidate))
				{
					continue;
				}

				final int candidateDistance = authoredAccess == null
					? Math.abs(dx) + Math.abs(dy)
					: distance(candidate, authoredAccess);
				if (best == null || candidateDistance < bestDistance)
				{
					best = candidate;
					bestDistance = candidateDistance;
				}
			}
		}
		return best == null ? objectLocation : best;
	}

	private void seedLoadedBankObjectsIfNeeded()
	{
		if (!bankObjectSceneSeedPending || client == null)
		{
			return;
		}
		bankObjectSceneSeedPending = false;

		final WorldView worldView = client.getTopLevelWorldView();
		final Scene scene = worldView == null ? null : worldView.getScene();
		if (scene == null || scene.getTiles() == null)
		{
			bankObjectSceneSeedPending = true;
			return;
		}

		for (final Tile[][] planeTiles : scene.getTiles())
		{
			if (planeTiles == null)
			{
				continue;
			}
			for (final Tile[] column : planeTiles)
			{
				if (column == null)
				{
					continue;
				}
				for (final Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					final GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects != null)
					{
						for (final GameObject object : gameObjects)
						{
							observeLoadedBankObject(object);
						}
					}
					observeLoadedBankObject(tile.getWallObject());
					observeLoadedBankObject(tile.getGroundObject());
					observeLoadedBankObject(tile.getDecorativeObject());
				}
			}
		}
	}

	private void observeLoadedBankObject(final TileObject object)
	{
		final WorldPoint location = bankObjectLocation(object);
		if (location != null)
		{
			loadedBankObjectLocations.add(location);
		}
	}

	private void forgetLoadedBankObject(final TileObject object)
	{
		if (object != null && object.getWorldLocation() != null)
		{
			loadedBankObjectLocations.remove(object.getWorldLocation());
		}
	}

	private WorldPoint bankObjectLocation(final TileObject object)
	{
		if (object == null || client == null)
		{
			return null;
		}

		try
		{
			ObjectComposition composition =
				client.getObjectDefinition(object.getId());
			if (composition != null && composition.getImpostorIds() != null)
			{
				final ObjectComposition impostor = composition.getImpostor();
				if (impostor != null)
				{
					composition = impostor;
				}
			}
			return composition != null
				&& isBankObjectForRegression(
					composition.getName(),
					composition.getActions()
				)
				? object.getWorldLocation()
				: null;
		}
		catch (RuntimeException ignored)
		{
			return null;
		}
	}

	private WorldPoint findLoadedBankObjectTarget(
		final WorldPoint playerLocation)
	{
		if (playerLocation == null)
		{
			return null;
		}

		WorldPoint nearestObject = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (final WorldPoint objectLocation : loadedBankObjectLocations)
		{
			if (objectLocation == null
				|| objectLocation.getPlane() != playerLocation.getPlane())
			{
				continue;
			}
			final int objectDistance = distance(playerLocation, objectLocation);
			if (objectDistance <= DYNAMIC_BANK_OBJECT_RADIUS
				&& objectDistance < nearestDistance)
			{
				nearestObject = objectLocation;
				nearestDistance = objectDistance;
			}
		}

		return nearestObject == null
			? null
			: nearestWalkableBankInteractionTile(nearestObject, playerLocation);
	}

	private WorldPoint nearestWalkableBankInteractionTile(
		final WorldPoint objectLocation,
		final WorldPoint playerLocation)
	{
		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		/*
		 * TileObject#getWorldLocation can be the south-west origin of a
		 * multi-tile chest/booth. A one-tile perimeter therefore misses valid
		 * interaction tiles for wider objects. Search a small local perimeter;
		 * collision checking still prevents an unreachable target from winning.
		 */
		for (int dx = -3; dx <= 3; dx++)
		{
			for (int dy = -3; dy <= 3; dy++)
			{
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				final WorldPoint candidate = new WorldPoint(
					objectLocation.getX() + dx,
					objectLocation.getY() + dy,
					objectLocation.getPlane()
				);
				if (!isWalkableSceneTile(candidate))
				{
					continue;
				}
				final int candidateDistance = distance(
					playerLocation,
					candidate
				);
				if (best == null || candidateDistance < bestDistance)
				{
					best = candidate;
					bestDistance = candidateDistance;
				}
			}
		}
		return best;
	}

	static boolean hasBankActionForRegression(final String[] actions)
	{
		return hasObjectAction(actions, "Bank");
	}

	static boolean isBankObjectForRegression(
		final String name,
		final String[] actions)
	{
		if (hasBankActionForRegression(actions))
		{
			return true;
		}

		/*
		 * Most traditional bank chests use "Use" instead of "Bank". Restrict
		 * that alias to an actual Bank chest name so deposit boxes, storage
		 * chests, and arbitrary Use objects can never satisfy bank arrival.
		 */
		final String normalizedName = name == null
			? ""
			: name.trim().toLowerCase(Locale.ENGLISH);
		return normalizedName.contains("bank chest")
			&& (hasObjectAction(actions, "Use")
				|| hasObjectAction(actions, "Open"));
	}

	private static boolean hasObjectAction(
		final String[] actions,
		final String expected)
	{
		if (actions == null || expected == null)
		{
			return false;
		}
		for (final String action : actions)
		{
			if (action != null && action.trim().equalsIgnoreCase(expected))
			{
				return true;
			}
		}
		return false;
	}

	private static WorldPoint nearestTarget(
		final WorldPoint origin,
		final WorldPoint first,
		final WorldPoint second)
	{
		if (first == null)
		{
			return second;
		}
		if (second == null || origin == null)
		{
			return first;
		}
		return distance(origin, first) <= distance(origin, second)
			? first : second;
	}

	/**
	 * Returns a collision-checked tile beside the nearest loaded NPC that
	 * exposes a Bank action. The name check is only a fallback for transformed
	 * Banker compositions whose action array is temporarily unavailable.
	 */
	private WorldPoint findLoadedBankNpcTarget(
		final WorldPoint playerLocation)
	{
		if (playerLocation == null)
		{
			return null;
		}

		NPC nearestNpc = null;
		int nearestDistance = Integer.MAX_VALUE;
		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}
		for (final NPC npc : worldView.npcs())
		{
			if (npc == null || !npcOffersBanking(npc))
			{
				continue;
			}

			final WorldPoint npcLocation = npc.getWorldLocation();
			if (npcLocation == null
				|| npcLocation.getPlane() != playerLocation.getPlane())
			{
				continue;
			}

			final int npcDistance = distance(playerLocation, npcLocation);
			if (npcDistance <= DYNAMIC_BANK_NPC_RADIUS
				&& npcDistance < nearestDistance)
			{
				nearestDistance = npcDistance;
				nearestNpc = npc;
			}
		}

		if (nearestNpc == null)
		{
			return null;
		}

		return liveBankNpcTargetForRegression(
			findNearestWalkablePerimeterTile(playerLocation, nearestNpc),
			nearestNpc.getWorldLocation()
		);
	}

	static WorldPoint liveBankNpcTargetForRegression(
		final WorldPoint reachablePerimeter,
		final WorldPoint npcLocation)
	{
		return reachablePerimeter == null ? npcLocation : reachablePerimeter;
	}

	private static boolean npcOffersBanking(final NPC npc)
	{
		if (npc == null)
		{
			return false;
		}

		try
		{
			if (npc.getComposition() != null
				&& hasBankActionForRegression(
					npc.getComposition().getActions()
				))
			{
				return true;
			}
		}
		catch (RuntimeException ignored)
		{
			/* Fall through to the conservative name fallback below. */
		}

		final String name = npc.getName();
		return name != null && name.equalsIgnoreCase("Banker");
	}

	private WorldPoint findLoadedNamedNpcTarget(
		final WorldPoint playerLocation,
		final Set<String> normalizedNpcNames,
		final int maximumRadius)
	{
		return findLoadedNpcTarget(
			playerLocation,
			normalizedNpcNames,
			java.util.Collections.emptySet(),
			maximumRadius
		);
	}

	private WorldPoint findInfernoEntryTarget(
		final WorldPoint playerLocation,
		final Set<String> normalizedNpcNames)
	{
		return findLoadedNpcTarget(
			playerLocation,
			normalizedNpcNames,
			INFERNO_ENTRY_NPC_IDS,
			INFERNO_ENTRY_NPC_RADIUS
		);
	}

	private WorldPoint findLoadedNpcTarget(
		final WorldPoint playerLocation,
		final Set<String> normalizedNpcNames,
		final Set<Integer> acceptedNpcIds,
		final int maximumRadius)
	{
		if (playerLocation == null
			|| ((normalizedNpcNames == null || normalizedNpcNames.isEmpty())
				&& (acceptedNpcIds == null || acceptedNpcIds.isEmpty())))
		{
			return null;
		}

		NPC nearestNpc = null;
		int nearestDistance = Integer.MAX_VALUE;
		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}
		for (final NPC npc : worldView.npcs())
		{
			if (npc == null)
			{
				continue;
			}
			final String npcName = npc.getName();
			final boolean nameMatches = npcName != null
				&& normalizedNpcNames != null
				&& normalizedNpcNames.contains(normalizeTravelName(npcName));
			final boolean idMatches = acceptedNpcIds != null
				&& acceptedNpcIds.contains(npc.getId());
			if (!nameMatches && !idMatches)
			{
				continue;
			}

			final WorldPoint npcLocation = npc.getWorldLocation();
			if (npcLocation == null
				|| npcLocation.getPlane() != playerLocation.getPlane())
			{
				continue;
			}
			final int npcDistance = distance(playerLocation, npcLocation);
			if (npcDistance <= Math.max(1, maximumRadius)
				&& npcDistance < nearestDistance)
			{
				nearestDistance = npcDistance;
				nearestNpc = npc;
			}
		}

		return nearestNpc == null
			? null
			: findNearestWalkablePerimeterTile(playerLocation, nearestNpc);
	}

	private WorldPoint findLoadedTaskNpcTarget(
		final WorldPoint playerLocation,
		final SlayerRouteCatalog.RouteProfile profile)
	{
		if (playerLocation == null || profile == null)
		{
			return null;
		}

		NPC nearestNpc = null;
		int nearestDistance = Integer.MAX_VALUE;
		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}
		for (final NPC npc : worldView.npcs())
		{
			if (npc == null
				|| npc.getName() == null
				|| !profile.matchesNpc(npc.getName()))
			{
				continue;
			}

			final WorldPoint npcLocation = npc.getWorldLocation();
			if (npcLocation == null
				|| npcLocation.getPlane() != playerLocation.getPlane())
			{
				continue;
			}

			final int npcDistance = distance(playerLocation, npcLocation);
			if (npcDistance < nearestDistance)
			{
				nearestDistance = npcDistance;
				nearestNpc = npc;
			}
		}

		if (nearestNpc == null
			|| nearestDistance > DYNAMIC_TASK_NPC_RADIUS)
		{
			return null;
		}

		return findNearestWalkablePerimeterTile(
			playerLocation,
			nearestNpc
		);
	}

	private boolean hasLoadedAssignedBossNearby(
		final WorldPoint playerLocation,
		final String taskName,
		final SlayerRouteCatalog.RouteProfile profile)
	{
		if (playerLocation == null || taskName == null || taskName.trim().isEmpty())
		{
			return false;
		}
		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return false;
		}
		for (final NPC npc : worldView.npcs())
		{
			if (npc == null
				|| !((profile != null && profile.matchesNpc(npc.getName()))
					|| bossNpcNameMatchesTaskForRegression(taskName, npc.getName())))
			{
				continue;
			}
			final WorldPoint npcLocation = npc.getWorldLocation();
			if (npcLocation != null
				&& npcLocation.getPlane() == playerLocation.getPlane()
				&& distance(playerLocation, npcLocation) <= DYNAMIC_TASK_NPC_RADIUS)
			{
				return true;
			}
		}
		return false;
	}

	static boolean bossNpcNameMatchesTaskForRegression(
		final String taskName,
		final String npcName)
	{
		final String task = normalizeTravelName(taskName)
			.replaceFirst("^the\\s+", "");
		final String npc = normalizeTravelName(npcName)
			.replaceFirst("^the\\s+", "");
		return !task.isEmpty() && task.equals(npc);
	}

	static boolean shouldConfirmWhispererArenaForRegression(
		final String taskName,
		final boolean sanityInterfaceVisible)
	{
		final String task = normalizeTravelName(taskName)
			.replaceFirst("^the\\s+", "");
		return sanityInterfaceVisible && task.equals("whisperer");
	}

	private WorldPoint findNearestWalkablePerimeterTile(
		final WorldPoint playerLocation,
		final NPC npc)
	{
		if (playerLocation == null || npc == null)
		{
			return null;
		}

		WorldArea npcArea = npc.getWorldArea();
		if (npcArea == null)
		{
			final WorldPoint npcLocation = npc.getWorldLocation();
			if (npcLocation == null)
			{
				return null;
			}
			npcArea = new WorldArea(npcLocation, 1, 1);
		}

		final Set<WorldPoint> candidates = new LinkedHashSet<>();
		for (int padding = 1; padding <= 3; padding++)
		{
			final int minX = npcArea.getX() - padding;
			final int maxX = npcArea.getX() + npcArea.getWidth() - 1 + padding;
			final int minY = npcArea.getY() - padding;
			final int maxY = npcArea.getY() + npcArea.getHeight() - 1 + padding;

			for (int x = minX; x <= maxX; x++)
			{
				addWalkableNpcTarget(
					candidates,
					new WorldPoint(x, minY, npcArea.getPlane()),
					npcArea
				);
				addWalkableNpcTarget(
					candidates,
					new WorldPoint(x, maxY, npcArea.getPlane()),
					npcArea
				);
			}
			for (int y = minY + 1; y < maxY; y++)
			{
				addWalkableNpcTarget(
					candidates,
					new WorldPoint(minX, y, npcArea.getPlane()),
					npcArea
				);
				addWalkableNpcTarget(
					candidates,
					new WorldPoint(maxX, y, npcArea.getPlane()),
					npcArea
				);
			}

		}

		return findReachableCandidate(
			playerLocation,
			npcArea,
			candidates
		);
	}

	/**
	 * Finds a target in the player's current collision component. A tile may
	 * lack BLOCK_MOVEMENT_FULL and still be sealed behind a wall, so merely
	 * checking the destination flag is not enough. This local breadth-first
	 * search uses RuneLite's standard WorldArea collision traversal and only
	 * returns a perimeter tile the player can actually walk to.
	 */
	private WorldPoint findReachableCandidate(
		final WorldPoint playerLocation,
		final WorldArea npcArea,
		final Set<WorldPoint> candidates)
	{
		if (playerLocation == null
			|| npcArea == null
			|| candidates == null
			|| candidates.isEmpty())
		{
			return null;
		}

		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null
			|| playerLocation.getPlane() != worldView.getPlane())
		{
			return null;
		}

		final ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
		final Set<WorldPoint> visited = new HashSet<>();
		queue.add(playerLocation);
		visited.add(playerLocation);

		while (!queue.isEmpty()
			&& visited.size() <= DYNAMIC_TASK_NPC_SEARCH_TILE_LIMIT)
		{
			final WorldPoint current = queue.removeFirst();
			if (candidates.contains(current))
			{
				return current;
			}

			final WorldArea currentArea = new WorldArea(current, 1, 1);
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if ((dx == 0 && dy == 0)
						|| !currentArea.canTravelInDirection(worldView, dx, dy))
					{
						continue;
					}

					final WorldPoint next = new WorldPoint(
						current.getX() + dx,
						current.getY() + dy,
						current.getPlane()
					);
					if (npcArea.contains(next)
						|| distance(playerLocation, next) > DYNAMIC_TASK_NPC_RADIUS
						|| !isWalkableSceneTile(next)
						|| !visited.add(next))
					{
						continue;
					}
					queue.addLast(next);
				}
			}
		}
		return null;
	}

	private void saveLearnedRouteCheckpoint(
		final GuidedTaskTarget target,
		final WorldPoint destination)
	{
		if (target == null
			|| target.profile == null
			|| destination == null
			|| configManager == null
			|| !target.profile.isPlausibleLearnedCheckpoint(destination))
		{
			return;
		}

		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || worldView.isInstance())
		{
			return;
		}

		final String key = routeCheckpointKey(target);
		final String value = destination.getX() + ","
			+ destination.getY() + ","
			+ destination.getPlane();

		String persisted = persistedRouteCheckpointCache.get(key);
		if (persisted == null)
		{
			persisted = configManager.getConfiguration(
				SlayerPlusConfig.GROUP,
				key
			);
			if (persisted != null && !persisted.trim().isEmpty())
			{
				persistedRouteCheckpointCache.put(key, persisted.trim());
			}
		}

		if (value.equals(persisted == null ? "" : persisted.trim()))
		{
			return;
		}

		/*
		 * Keep learned checkpoints current, but never treat a walking NPC as a
		 * reason to write plugin configuration every game tick.
		 */
		final Long lastWrite = routeCheckpointLastWriteTick.get(key);
		if (lastWrite != null
			&& gameTickSequence - lastWrite < ROUTE_CHECKPOINT_WRITE_INTERVAL_TICKS)
		{
			return;
		}

		configManager.setConfiguration(
			SlayerPlusConfig.GROUP,
			key,
			value
		);
		persistedRouteCheckpointCache.put(key, value);
		routeCheckpointLastWriteTick.put(key, gameTickSequence);
	}

	private WorldPoint readLearnedRouteCheckpoint(
		final GuidedTaskTarget target)
	{
		if (target == null || configManager == null)
		{
			return null;
		}

		final String key = routeCheckpointKey(target);
		final String stored;
		if (persistedRouteCheckpointCache.containsKey(key))
		{
			stored = persistedRouteCheckpointCache.get(key);
		}
		else
		{
			final String configured = configManager.getConfiguration(
				SlayerPlusConfig.GROUP,
				key
			);
			stored = configured == null ? "" : configured.trim();
			/* Cache misses too; route resolution runs every game tick while active. */
			persistedRouteCheckpointCache.put(key, stored);
		}
		if (stored.isEmpty())
		{
			return null;
		}

		final String[] parts = stored.split(",");
		if (parts.length != 3)
		{
			return null;
		}

		try
		{
			final WorldPoint checkpoint = new WorldPoint(
				Integer.parseInt(parts[0].trim()),
				Integer.parseInt(parts[1].trim()),
				Integer.parseInt(parts[2].trim())
			);
			return target.profile != null
				&& target.profile.isPlausibleLearnedCheckpoint(checkpoint)
					? checkpoint
					: null;
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	private static String routeCheckpointKey(
		final GuidedTaskTarget target)
	{
		final String identity = normalizeTravelName(target.taskName)
			+ "|" + normalizeTravelName(target.location)
			+ "|" + (target.isBoss() ? "boss" : "regular");
		return ROUTE_CHECKPOINT_PREFIX
			+ Integer.toUnsignedString(identity.hashCode(), 36);
	}

	private Set<WorldPoint> singletonRouteTarget(final WorldPoint destination)
	{
		final Set<WorldPoint> targets = new LinkedHashSet<>();
		if (destination != null)
		{
			targets.add(destination);
		}
		return targets;
	}

	private void addWalkableNpcTarget(
		final Set<WorldPoint> candidates,
		final WorldPoint candidate,
		final WorldArea npcArea)
	{
		if (candidate != null
			&& npcArea != null
			&& !npcArea.contains(candidate)
			&& isWalkableSceneTile(candidate))
		{
			candidates.add(candidate);
		}
	}

	private boolean isWalkableSceneTile(final WorldPoint point)
	{
		if (point == null)
		{
			return false;
		}

		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || point.getPlane() != worldView.getPlane())
		{
			return false;
		}

		final int sceneX = point.getX() - worldView.getBaseX();
		final int sceneY = point.getY() - worldView.getBaseY();
		if (sceneX < 0
			|| sceneY < 0
			|| sceneX >= worldView.getSizeX()
			|| sceneY >= worldView.getSizeY())
		{
			return false;
		}

		final CollisionData[] collisionMaps = worldView.getCollisionMaps();
		if (collisionMaps == null
			|| point.getPlane() < 0
			|| point.getPlane() >= collisionMaps.length
			|| collisionMaps[point.getPlane()] == null)
		{
			return false;
		}

		final int[][] flags = collisionMaps[point.getPlane()].getFlags();
		return sceneX < flags.length
			&& flags[sceneX] != null
			&& sceneY < flags[sceneX].length
			&& (flags[sceneX][sceneY]
				& CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
	}

	private boolean isPointBoosting()
	{
		return config != null
			&& config.slayerWorkflow()
				== SlayerPreference.Workflow.TURAEL_POINT_BOOST;
	}

	private SlayerPointBoostCoordinator.Decision pointBoostDecision(
		final int completedStreak)
	{
		return SlayerPointBoostCoordinator.nextAssignment(
			completedStreak,
			config == null ? SlayerPreference.BonusMaster.KONAR
				: config.pointBoostBonusMaster()
		);
	}

	private String pointBoostPanelStatus(
		final int completedStreak,
		final boolean hasActiveTask,
		final int assigningMasterId)
	{
		if (!isPointBoosting())
		{
			return "";
		}
		final SlayerPointBoostCoordinator.Decision decision =
			pointBoostDecision(completedStreak);
		if (hasActiveTask && assigningMasterId != decision.getMasterId())
		{
			return "Point boosting resumes after this existing assignment.";
		}
		return hasActiveTask
			? "Point boost: " + decision.getProgressText()
			: "Next: " + SlayerMasterRouteCatalog.getName(decision.getMasterId())
				+ " — " + decision.getProgressText();
	}

	private int masterForNextAssignment(final int normalStreak)
	{
		if (isPointBoosting())
		{
			return pointBoostDecision(normalStreak).getMasterId();
		}
		return lastUsedMasterId > 0
			? lastUsedMasterId
			: currentMasterId;
	}

	private int getPostTaskReturnMasterId()
	{
		if (!isPointBoosting())
		{
			return getRoutingMasterId();
		}
		final int completedStreak = client.getVarbitValue(
			VarbitID.SLAYER_TASKS_COMPLETED
		);
		final int predictedPostTaskStreak = completedStreak
			+ (effectiveTaskRemaining() > 0 ? 1 : 0);
		return pointBoostDecision(predictedPostTaskStreak).getMasterId();
	}

	private int getRoutingMasterId()
	{
		if (effectiveTaskRemaining() > 0 && currentMasterId > 0)
		{
			return currentMasterId;
		}
		if (isPointBoosting())
		{
			return pointBoostDecision(client.getVarbitValue(
				VarbitID.SLAYER_TASKS_COMPLETED
			)).getMasterId();
		}
		return lastUsedMasterId > 0 ? lastUsedMasterId : currentMasterId;
	}

	private int effectiveTaskRemaining()
	{
		return effectiveTaskRemainingForRegression(
			observedTaskRemaining,
			client.getVarpValue(VarPlayerID.SLAYER_COUNT)
		);
	}

	static int effectiveTaskRemainingForRegression(
		final int resolvedRemaining,
		final int liveRemaining)
	{
		return resolvedRemaining >= 0
			? resolvedRemaining
			: Math.max(0, liveRemaining);
	}

	private boolean isGuidedSessionAvailable()
	{
		return panel != null
			&& client.getGameState() == GameState.LOGGED_IN
			&& config.enableShortestPathRouting()
			&& shortestPathBridge != null;
	}

	private String getGuidedSessionDisplayStatus()
	{
		if (guidedSessionActive)
		{
			return guidedSessionStatus;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return "Log in to start a guided Slayer session";
		}

		if (!config.enableShortestPathRouting())
		{
			return "Enable Shortest Path routing in SlayerPlus settings to start a session";
		}

		if (effectiveTaskRemaining() > 0)
		{
			return "Start a session to route to a bank, gear up, and travel to your task";
		}

		if (getRoutingMasterId() > 0)
		{
			if (isPointBoosting())
			{
				final SlayerPointBoostCoordinator.Decision decision =
					pointBoostDecision(client.getVarbitValue(
						VarbitID.SLAYER_TASKS_COMPLETED
					));
				return "Start a session to route to "
					+ SlayerMasterRouteCatalog.getName(decision.getMasterId())
					+ " for " + decision.getProgressText();
			}
			return "Start a session to route to "
				+ SlayerMasterRouteCatalog.getName(getRoutingMasterId())
				+ " for a task";
		}

		return guidedSessionStatus;
	}

	private void setGuidedSessionStatus(final String status)
	{
		final String nextStatus = status == null ? "" : status.trim();
		if (nextStatus.equals(guidedSessionStatus))
		{
			return;
		}
		guidedSessionStatus = nextStatus;
		updateGuidedSessionPanel();
	}

	private void updateGuidedSessionPanel()
	{
		final SlayerPlusPanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}

		final boolean active = guidedSessionActive;
		final boolean available = isGuidedSessionAvailable();
		final String status = getGuidedSessionDisplayStatus();
		SwingUtilities.invokeLater(() ->
			currentPanel.showSessionState(active, available, status)
		);
	}

	private static int distance(
		final WorldPoint first,
		final WorldPoint second)
	{
		if (first == null || second == null)
		{
			return Integer.MAX_VALUE;
		}

		if (first.getPlane() != second.getPlane())
		{
			return Integer.MAX_VALUE;
		}

		return Math.max(
			Math.abs(first.getX() - second.getX()),
			Math.abs(first.getY() - second.getY())
		);
	}

	static boolean shouldRebaseBankRoute(
		final WorldPoint previous,
		final WorldPoint current)
	{
		if (previous == null || current == null || previous.equals(current))
		{
			return false;
		}
		if (previous.getPlane() != current.getPlane())
		{
			return true;
		}
		return distance(previous, current) > BANK_ROUTE_REBASE_DISTANCE;
	}

	private void startRecommendedRoute()
	{
		final SlayerPlusPanel currentPanel = panel;
		if (currentPanel == null)
		{
			return;
		}

		if (!config.enableShortestPathRouting())
		{
			SwingUtilities.invokeLater(() ->
				currentPanel.showRouteStatus("Routing is disabled in SlayerPlus settings")
			);
			return;
		}

		/* The panel action must use the same exact route-plan state machine as the
		 * automatic session. Bypassing it here previously skipped manual cave,
		 * dungeon, instance, Tormented-demon, and Skotizo stages. */
		if (!guidedSessionActive)
		{
			toggleGuidedSession();
			return;
		}
		routeToTaskForGuidedSession(
			bankScanned || client.getItemContainer(InventoryID.BANK) != null,
			"Route requested"
		);
	}

	private int readRuneLiteSlayerProfileInt(final String key)
	{
		try
		{
			final Integer value = configManager.getRSProfileConfiguration(
				RUNELITE_SLAYER_CONFIG_GROUP,
				key,
				Integer.class
			);
			return value == null ? -1 : value;
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to read RuneLite Slayer profile key {}", key, ex);
			return -1;
		}
	}

	private String readRuneLiteSlayerProfileString(final String key)
	{
		try
		{
			final String value = configManager.getRSProfileConfiguration(
				RUNELITE_SLAYER_CONFIG_GROUP,
				key
			);
			return value == null ? "" : value.trim();
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to read RuneLite Slayer profile key {}", key, ex);
			return "";
		}
	}

	private int readSlayerPlusProfileInt(final String key)
	{
		try
		{
			final Integer value = configManager.getRSProfileConfiguration(
				SlayerPlusConfig.GROUP,
				key,
				Integer.class
			);
			return value == null ? -1 : value;
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to read SlayerPlus profile key {}", key, ex);
			return -1;
		}
	}

	private void storeLastSlayerMasterId(final int masterId)
	{
		if (masterId <= 0 || masterId == readSlayerPlusProfileInt(
			LAST_SLAYER_MASTER_SNAPSHOT_KEY))
		{
			return;
		}
		try
		{
			configManager.setRSProfileConfiguration(
				SlayerPlusConfig.GROUP,
				LAST_SLAYER_MASTER_SNAPSHOT_KEY,
				masterId
			);
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to store the last Slayer master", ex);
		}
	}

	private String profileSlayerLocation()
	{
		final String location = readRuneLiteSlayerProfileString(
			RUNELITE_SLAYER_LOCATION_KEY
		);
		return location.isEmpty() ? "Assigned area" : location;
	}

	private int inferNearbySlayerMasterId()
	{
		final Player player = client.getLocalPlayer();
		final WorldPoint playerLocation = player == null
			? null : player.getWorldLocation();
		if (playerLocation == null)
		{
			return 0;
		}

		int nearestId = 0;
		int nearestDistance = Integer.MAX_VALUE;
		for (int masterId = 1; masterId <= 10; masterId++)
		{
			final SlayerMasterRouteCatalog.MasterRoute route =
				SlayerMasterRouteCatalog.find(masterId);
			if (route == null
				|| route.getDestination().getPlane() != playerLocation.getPlane())
			{
				continue;
			}
			final int distance = playerLocation.distanceTo2D(
				route.getDestination()
			);
			if (distance <= 20 && distance < nearestDistance)
			{
				nearestId = masterId;
				nearestDistance = distance;
			}
		}
		return nearestId;
	}

	static int firstPositive(final int... values)
	{
		if (values != null)
		{
			for (final int value : values)
			{
				if (value > 0)
				{
					return value;
				}
			}
		}
		return 0;
	}

	static int resolveTaskRemainingForRegression(
		final int previouslyObservedRemaining,
		final int serviceRemaining,
		final int liveRemaining,
		final int profileRemaining,
		final int chatRemaining)
	{
		/* A live zero after a known active task is the completion boundary. The
		 * Slayer service and RS-profile values can each retain the old positive
		 * amount for one callback, so allowing firstPositive() to win here prevents
		 * the had-task -> no-task transition and therefore the master return route. */
		if (previouslyObservedRemaining > 0 && liveRemaining <= 0)
		{
			return 0;
		}
		return firstPositive(
			serviceRemaining,
			liveRemaining,
			profileRemaining,
			chatRemaining
		);
	}

	static int preferLiveOrProfileValue(
		final int liveValue,
		final int profileValue,
		final int chatValue)
	{
		if (liveValue > 0)
		{
			return liveValue;
		}
		if (profileValue > 0)
		{
			return profileValue;
		}
		if (chatValue >= 0)
		{
			return chatValue;
		}
		return Math.max(0, Math.max(liveValue, profileValue));
	}

	private String readTaskName()
	{
		try
		{
			final int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
			final int taskRow;

			if (taskId == 98)
			{
				final List<Integer> bossRows = client.getDBRowsByValue(
					DBTableID.SlayerTaskSublist.ID,
					DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
					0,
					client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID)
				);

				if (bossRows == null || bossRows.isEmpty())
				{
					return "Unknown task";
				}

				final Object[] taskFields = client.getDBTableField(
					bossRows.get(0),
					DBTableID.SlayerTaskSublist.COL_TASK,
					0
				);
				if (taskFields == null || taskFields.length == 0
					|| !(taskFields[0] instanceof Integer))
				{
					return "Unknown task";
				}
				taskRow = (Integer) taskFields[0];
			}
			else
			{
				final List<Integer> taskRows = client.getDBRowsByValue(
					DBTableID.SlayerTask.ID,
					DBTableID.SlayerTask.COL_ID,
					0,
					taskId
				);

				if (taskRows == null || taskRows.isEmpty())
				{
					return "Unknown task";
				}

				taskRow = taskRows.get(0);
			}

			final Object[] fields = client.getDBTableField(
				taskRow,
				DBTableID.SlayerTask.COL_NAME_UPPERCASE,
				0
			);

			return fields != null && fields.length > 0
				&& fields[0] instanceof String
					? formatName((String) fields[0])
					: "Unknown task";
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to read Slayer task from the game database", ex);
			return "Unknown task";
		}
	}

	private String readAssignedLocation()
	{
		try
		{
			final int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
			if (areaId <= 0)
			{
				return "Not restricted";
			}

			final List<Integer> areaRows = client.getDBRowsByValue(
				DBTableID.SlayerArea.ID,
				DBTableID.SlayerArea.COL_AREA_ID,
				0,
				areaId
			);

			if (areaRows == null || areaRows.isEmpty())
			{
				return "Assigned area";
			}

			final Object[] fields = client.getDBTableField(
				areaRows.get(0),
				DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER,
				0
			);

			return fields != null && fields.length > 0
				&& fields[0] instanceof String
					? (String) fields[0]
					: "Assigned area";
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to read Slayer area from the game database", ex);
			return "Assigned area";
		}
	}

	private String getAccountName()
	{
		final Player player = client.getLocalPlayer();
		if (player == null
			|| player.getName() == null
			|| player.getName().trim().isEmpty())
		{
			return "ACCOUNT LOADING";
		}

		return player.getName().trim();
	}

	static String masterDisplayNameForRegression(
		final int masterId,
		final boolean whileGuthixSleepsFinished)
	{
		/* Kuradal inherits Duradel's master ID, assignments, and routes. */
		if (masterId == 5 && whileGuthixSleepsFinished)
		{
			return "Kuradal";
		}
		return SlayerMasterRouteCatalog.getName(masterId);
	}

	private static String formatName(final String value)
	{
		if (value == null || value.isEmpty())
		{
			return "Unknown task";
		}

		final String lower = value.toLowerCase();
		return Character.toUpperCase(lower.charAt(0))
			+ lower.substring(1);
	}

	private static final class TravelRouteCandidate
	{
		private final String family;
		private final TravelItemMatch item;
		private final String destination;

		private TravelRouteCandidate(
			final String family,
			final TravelItemMatch item,
			final String destination)
		{
			this.family = family == null ? "" : family.trim();
			this.item = item;
			this.destination = destination == null ? "" : destination.trim();
		}
	}

	private static final class TravelRouteMatch
	{
		private final TravelItemMatch item;
		private final String destination;
		private final Set<String> equivalentItemFamilies;

		private TravelRouteMatch(
			final TravelItemMatch item,
			final String destination,
			final Set<String> equivalentItemFamilies)
		{
			this.item = item;
			this.destination = destination == null ? "" : destination;
			this.equivalentItemFamilies = equivalentItemFamilies == null
				? java.util.Collections.emptySet()
				: java.util.Collections.unmodifiableSet(
					new LinkedHashSet<>(equivalentItemFamilies)
				);
		}
	}

	private static final class TravelInstruction
	{
		private final String family;
		private final String destination;

		private TravelInstruction(
			final String family,
			final String destination)
		{
			this.family = family == null ? "" : family.trim();
			this.destination = destination == null ? "" : destination.trim();
		}
	}


	private static final class TravelItemMatch
	{
		private final int itemId;
		private final String displayName;
		private final int score;

		private TravelItemMatch(
			final int itemId,
			final String displayName,
			final int score)
		{
			this.itemId = itemId;
			this.displayName = displayName;
			this.score = score;
		}
	}

	@Provides
	SlayerPlusConfig provideConfig(final ConfigManager configManager)
	{
		return configManager.getConfig(SlayerPlusConfig.class);
	}
}
