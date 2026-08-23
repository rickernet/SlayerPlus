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
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
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
    tags = {"slayer", "gear", "loadout", "route"})
@PluginDependency(BankTagsPlugin.class)
@PluginDependency(SlayerPlugin.class)
public class SlayerPlusPlugin extends Plugin {
  private static final Logger log = LoggerFactory.getLogger(SlayerPlusPlugin.class);
  private static final int KRYSTILIA_MASTER_ID = 7;
  private static final int KONAR_MASTER_ID = 8;
  private static final String RUNELITE_SLAYER_CONFIG_GROUP = "slayer";
  private static final String RUNELITE_SLAYER_TASK_NAME_KEY = "taskName";
  private static final String RUNELITE_SLAYER_AMOUNT_KEY = "amount";
  private static final String RUNELITE_SLAYER_INITIAL_AMOUNT_KEY = "initialAmount";
  private static final String RUNELITE_SLAYER_LOCATION_KEY = "taskLocation";
  private static final String RUNELITE_SLAYER_STREAK_KEY = "streak";
  private static final String RUNELITE_SLAYER_POINTS_KEY = "points";
  private static final String LAST_SLAYER_MASTER_SNAPSHOT_KEY = "lastSlayerMasterIdV1";
  private static final String PORTRAIT_CALIBRATION_KEY = "portraitCalibrationV1";
  private static final String PLAYSTYLE_KEY = "playstyle";
  private static final String EASY_TELEPORTS_CONFIG_GROUP = "easypharaohsceptre";
  private static final String ROUTE_CHECKPOINT_PREFIX =
      "routeCheckpointV3.r" + RouteCatalog.CATALOG_REVISION + ".";
  private static final String INFERNO_TRAVEL_SNAPSHOT_KEY = "infernoTravelItemSnapshotV1";
  private static final String OWNED_SLAYER_HELMETS_SNAPSHOT_KEY = "ownedSlayerHelmetItemIdsV1";
  private static final int LEARNED_ROUTE_USE_RADIUS = 256;
  private static final int TASK_ARRIVAL_RADIUS = 14;
  private static final int MASTER_ARRIVAL_RADIUS = 12;
  private static final int TASK_EXIT_RADIUS = 48;
  private static final int TASK_EXIT_CONFIRM_TICKS = 5;
  private static final int TASK_ARRIVAL_GRACE_TICKS = 60;
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
  private static final WorldPoint MEIYERDITCH_SHORTCUT_APPROACH = new WorldPoint(3500, 9803, 0);
  private static final int MEIYERDITCH_LAB_SHORTCUT_EXIT_MIN_X = 3534;
  private static final WorldPoint KALPHITE_QUEEN_LOWER_ROPE_APPROACH =
      new WorldPoint(3508, 9498, 2);
  private static final int KALPHITE_LAIR_STAGE_RADIUS = 192;
  private static final int KALPHITE_LAIR_SHORTCUT_AGILITY_LEVEL = 86;
  private static final WorldPoint WHISPERER_LASSAR_SINKHOLE = new WorldPoint(2922, 5827, 0);
  private static final WorldPoint WHISPERER_LASSAR_RING_LANDING = new WorldPoint(2588, 6435, 0);
  private static final WorldPoint WHISPERER_PALACE_TELEPORTER = new WorldPoint(2593, 6424, 0);
  private static final WorldPoint WHISPERER_CATHEDRAL_TELEPORTER = new WorldPoint(2652, 6405, 0);
  private static final int CAMDOZAAL_MIN_X = 2897;
  private static final int CAMDOZAAL_MAX_X = 3047;
  private static final int CAMDOZAAL_MIN_Y = 5757;
  private static final int CAMDOZAAL_MAX_Y = 5869;
  private static final int LASSAR_UNDERCITY_MIN_X = 2464;
  private static final int LASSAR_UNDERCITY_MAX_X = 2848;
  private static final int LASSAR_UNDERCITY_MIN_Y = 6178;
  private static final int LASSAR_UNDERCITY_MAX_Y = 6562;
  private static final WorldPoint SKOTIZO_CATACOMBS_ALTAR = new WorldPoint(1666, 10050, 0);
  private static final int SKOTIZO_LAIR_REGION_ID = 6810;
  private static final WorldPoint TORMENTED_TEARS_LANDING = new WorldPoint(3245, 9500, 2);
  private static final WorldPoint TORMENTED_LIGHT_CREATURE_APPROACH = new WorldPoint(3241, 9525, 2);
  private static final WorldPoint TORMENTED_DIRECT_SCROLL_STAGE = new WorldPoint(4096, 4419, 0);
  private static final WorldPoint MORTIMER_SLAYER_RING_LANDING = new WorldPoint(2581, 8633, 0);
  private static final WorldPoint MORTIMER_MASTER_DESTINATION = new WorldPoint(2589, 8614, 0);
  private static final int MORTIMER_CAVERN_RADIUS = 96;
  private static final WorldPoint TORMENTED_MANUAL_CHASM_STAGE = new WorldPoint(4096, 4418, 0);
  private static final WorldPoint AQUANITE_YNYSDAIL_ROWBOAT = new WorldPoint(2227, 3469, 0);
  private static final WorldPoint AQUANITE_CAVERN_ENTRANCE = new WorldPoint(2218, 3477, 0);
  private static final WorldPoint AQUANITE_PORT_ROBERTS_MOORING = new WorldPoint(1889, 3292, 0);
  private static final Set<String> TORMENTED_LIGHT_CREATURE_NAMES =
      java.util.Collections.singleton("light creature");
  private static final int ROUTE_DISCOVERY_INTERVAL_TICKS = 3;
  private static final int EXTRA_QUIVER_SAFETY_POLL_TICKS = 5;
  private static final int BANK_ROUTE_REBASE_DISTANCE = 32;
  private static final int PORTRAIT_APPEARANCE_POLL_TICKS = 5;
  private static final int ROUTE_CHECKPOINT_WRITE_INTERVAL_TICKS = 25;
  private static final int INFERNO_PREP_BANK_ARRIVAL_RADIUS = 10;
  private static final int INFERNO_PREP_BANK_APPROACH_RADIUS = 32;

  static int infernoPreparationBankApproachRadiusForRegression() {
    return INFERNO_PREP_BANK_APPROACH_RADIUS;
  }

  private static final int INFERNO_PREP_LOCAL_HANDOFF_RADIUS = 128;
  private static final int INFERNO_PREP_BANKER_DISCOVERY_RADIUS = 64;
  private static final int INFERNO_ENTRY_NPC_RADIUS = 64;
  private static final int INFERNO_REGION_ID = 9043;
  private static final int INFERNO_STAGING_TRAVEL_SLOT_INDEX = -1;
  private static final String INFERNO_PREP_TRAVEL_LOCATION = "Mor Ul Rek east bank";
  private static final Set<String> INFERNO_PREP_BANKER_NAMES =
      java.util.Collections.singleton("tzhaar ket yil");
  private static final Set<Integer> INFERNO_ENTRY_NPC_IDS =
      java.util.Collections.unmodifiableSet(
          new LinkedHashSet<>(
              Arrays.asList(
                  NpcID.INFERNO_MASTER, NpcID.INFERNO_MASTER_1OP, NpcID.INFERNO_MASTER_2OP)));
  private static final int[] INFERNO_PREPARATION_TRAVEL_ITEM_IDS = {
    ItemID.CA_OFFHAND_GRANDMASTER,
    ItemID.INFERNAL_DEFENDER_GHOMMAL_6,
    ItemID.INFERNAL_DEFENDER_GHOMMAL_6_TROUVER,
    ItemID.CA_OFFHAND_MASTER,
    ItemID.INFERNAL_DEFENDER_GHOMMAL_5,
    ItemID.INFERNAL_DEFENDER_GHOMMAL_5_TROUVER,
    ItemID.CA_OFFHAND_ELITE
  };

  enum GuidedSessionPhase {
    STOPPED,
    ROUTING_TO_SPELLBOOK,
    ROUTING_TO_BANK,
    ROUTING_TO_PREP_BANK,
    ROUTING_TO_TASK,
    TASKING,
    ROUTING_TO_MASTER,
    WAITING_FOR_TASK
  }

  private static final class GuidedTaskTarget {
    private final String taskName;
    private final String location;
    private final RouteCatalog.RouteProfile profile;
    private final RoutePlan routePlan;
    private final boolean bossEncounter;

    private GuidedTaskTarget(
        String taskName,
        String location,
        RouteCatalog.RouteProfile profile,
        RoutePlan routePlan,
        boolean bossEncounter) {
      this.taskName = taskName == null ? "" : taskName.trim();
      this.location = location == null ? "" : location.trim();
      this.profile = profile;
      this.routePlan = routePlan;
      this.bossEncounter = bossEncounter;
    }

    private boolean isBoss() {
      return bossEncounter || (profile != null && profile.isBoss());
    }

    private boolean isStaged() {
      return profile != null && profile.isStaged();
    }

    private boolean requiresLoadedNpc() {
      return profile != null && profile.requiresLoadedNpc();
    }

    private boolean isInsideEncounterArea(WorldPoint playerLocation) {
      return profile != null && profile.isInsideEncounterArea(playerLocation);
    }

    private WorldPoint destination(WorldPoint playerLocation) {
      return profile == null ? null : profile.getRouteDestination(playerLocation);
    }

    private int arrivalRadius(WorldPoint playerLocation) {
      return profile == null ? TASK_ARRIVAL_RADIUS : profile.getArrivalRadius(playerLocation);
    }
  }

  private static final class GuidedTaskDetourSnapshot {
    private final GuideLifecycle.TaskRouteKey key;
    private final GuidedTaskTarget target;

    private GuidedTaskDetourSnapshot(
        GuideLifecycle.TaskRouteKey key, GuidedTaskTarget target) {
      this.key = key;
      this.target = target;
    }
  }

  private static final class RouteStage {
    private final WorldPoint destination;
    private final Set<WorldPoint> targets;
    private final boolean entrance;
    private final boolean exactNpc;
    private final boolean learnedCheckpoint;

    private RouteStage(
        WorldPoint destination,
        Set<WorldPoint> targets,
        boolean entrance,
        boolean exactNpc,
        boolean learnedCheckpoint) {
      this.destination = destination;
      this.targets = targets == null ? java.util.Collections.emptySet() : targets;
      this.entrance = entrance;
      this.exactNpc = exactNpc;
      this.learnedCheckpoint = learnedCheckpoint;
    }

    private boolean isValid() {
      return destination != null && !targets.isEmpty();
    }
  }

  private static final class RoutePlanResolution {
    private final RoutePlan.ProgressStatus status;
    private final RoutePlan.Leg leg;
    private final RouteStage stage;
    private final boolean pauseForInteraction;

    private RoutePlanResolution(
        RoutePlan.ProgressStatus status,
        RoutePlan.Leg leg,
        RouteStage stage,
        boolean pauseForInteraction) {
      this.status = status;
      this.leg = leg;
      this.stage = stage;
      this.pauseForInteraction = pauseForInteraction;
    }

    private boolean hasArrived() {
      return status == RoutePlan.ProgressStatus.ARRIVED;
    }

    private boolean isWaiting() {
      return pauseForInteraction;
    }
  }

  @Inject private Client client;
  @Inject private ClientThread clientThread;
  @Inject private ClientToolbar clientToolbar;
  @Inject private SlayerPlusConfig config;
  @Inject private ConfigManager configManager;
  @Inject private ItemManager itemManager;
  @Inject private EventBus eventBus;
  @Inject private TagManager tagManager;
  @Inject private LayoutManager layoutManager;
  @Inject private BankTagsService bankTagsService;
  @Inject private OverlayManager overlayManager;
  @Inject private InfoBoxManager infoBoxManager;
  @Inject private SlayerPluginService slayerPluginService;
  @Inject private SlayerTaskReadinessOverlay readinessOverlay;
  @Inject private SlayerTravelItemOverlay travelItemOverlay;
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
  private final Map<Integer, Integer> cachedBankItems = new LinkedHashMap<>();
  private int[] cachedRawBankContainerState = new int[0];
  private boolean cachedRawBankContainerConfirmed;
  private boolean bankInterfaceOpen;
  private int[] cachedWornItemIdentityState = new int[0];
  private boolean cachedWornItemIdentityKnown;
  private int[] cachedInventoryContainerState = new int[0];
  private boolean cachedInventoryContainerStateKnown;
  private int profileInfernoTravelItemId = -1;
  private boolean profileInfernoTravelSnapshotLoaded;
  private final Set<Integer> profileOwnedSlayerHelmetItemIds = new LinkedHashSet<>();
  private boolean profileOwnedSlayerHelmetSnapshotLoaded;
  private final Set<Integer> cachedSeekingArrowPlaceholderItemIds = new LinkedHashSet<>();
  private final Map<Integer, Integer> seekingPlaceholderClassificationCache = new LinkedHashMap<>();
  private int cachedExtraQuiverAmmoItemId = -1;
  private int cachedExtraQuiverAmmoQuantity;
  private int cachedBankDizanaVariantItemId = -1;
  private final SlayerTravelCoordinator travelCoordinator = new SlayerTravelCoordinator();
  private boolean bankScanned;
  private Recommendation currentRecommendation;
  private KitPlan currentLoadout = KitPlan.empty();
  private PreparationCatalog.PreparationPlan currentPreparation =
      PreparationCatalog.PreparationPlan.none();
  private BankTagLayout bankTagLayoutService;
  private int currentMasterId;
  private String currentTaskName = "";
  private TaskVariant currentTaskVariant = TaskVariant.STANDARD_TASK;
  private Recommendation cachedGuidedTargetRecommendation;
  private String cachedGuidedTargetTaskName = "";
  private TaskVariant cachedGuidedTargetVariant;
  private GuidedTaskTarget cachedGuidedTaskTarget;
  private GuidedTaskDetourSnapshot guidedTaskDetourSnapshot;
  private boolean guidedSessionActive;
  private GuidedSessionPhase guidedSessionPhase = GuidedSessionPhase.STOPPED;
  private String guidedSessionStatus = "Start a guided session to route through your Slayer task";
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
  private WorldPoint guidedLastPlayerLocation;
  private int loadedPohSpellbookAltarId = -1;
  private WorldPoint loadedPohSpellbookAltarLocation;
  private boolean guidedPohSpellbookTeleportPending;
  private boolean guidedPohSpellbookAltarReached;
  private boolean guidedPohSpellbookFallbackActive;
  private int guidedPohSpellbookLoadTicks;
  private boolean guidedBankTransportTracking;
  private boolean guidedTaskBankPickupPending;
  private boolean guidedMasterBankPickupPending;
  private volatile long guidedTravelRequestGeneration = -1L;
  private volatile String guidedTravelRequestRouteIdentity = "";
  private String lastResolvedRoutingProfileIdentity = "";
  private boolean bankTagLayoutCreated;
  private String lastWrittenBankTagState = "";
  private WorldPoint guidedDynamicTaskDestination;
  private WorldPoint guidedSurfaceEntranceDestination;
  private boolean guidedEntranceReached;
  private boolean guidedRouteWaitingForCheckpoint;
  private RoutePlan guidedRoutePlan;
  private RoutePlan.Cursor guidedRoutePlanCursor;
  private RoutePlan.Evaluation guidedRoutePlanEvaluation;
  private String guidedRoutePlanIdentity = "";
  private long guidedRoutePlanEvidenceVersion = -1L;
  private long guidedRoutePlanArrivalEvidenceVersion = -1L;
  private RouteEvidence guidedRouteEvidenceCache;
  private RoutePlan guidedRouteEvidenceCachePlan;
  private long guidedRouteEvidenceCacheVersion = -1L;
  private long routeEvidenceVersion;
  private WorldPoint guidedPreviousPlayerLocation;
  private WorldPoint guidedPreviousTemplateLocation;
  private WorldPoint guidedLastTemplateLocation;
  private final Set<RouteEvidence.ObjectAction> observedRoutePlanInteractions =
      new LinkedHashSet<>();
  private long observedRoutePlanInteractionExpiresAfterTick = -1L;
  private final Map<TileObject, Set<RouteEvidence.ObjectAction>>
      loadedRoutePlanObjectActions = new LinkedHashMap<>();
  private final Set<RouteCheck.ObjectActionSpec> guidedRoutePlanObjectSpecs =
      new LinkedHashSet<>();
  private final Set<Integer> guidedRoutePlanObjectIds = new LinkedHashSet<>();
  private boolean guidedRoutePlanHasNameOnlyObjectSpecs;
  private boolean routePlanObjectSceneSeedPending = true;
  private final Set<NPC> loadedRoutePlanNpcs = new LinkedHashSet<>();
  private final Set<String> guidedRoutePlanNpcNames = new LinkedHashSet<>();
  private boolean routePlanNpcSceneSeedPending = true;
  private final Set<Integer> loadedRoutePlanWidgetGroups = new LinkedHashSet<>();
  private final Set<Integer> guidedRoutePlanWidgetGroups = new LinkedHashSet<>();
  private final Set<Integer> guidedRoutePlanWidgetComponents = new LinkedHashSet<>();
  private final Set<Integer> visibleRoutePlanWidgetComponents = new LinkedHashSet<>();
  private boolean infernoPreparationBankReady;
  private boolean infernoPreparationManualTeleportPending;
  private boolean infernoPreparationHotVentPending;
  private boolean whispererRingTeleportPending;
  private boolean whispererCathedralTeleportPending;
  private boolean araxxorSpiderTeleportPending;
  private boolean mortimerSlayerRingTeleportPending;
  private WorldPoint guidedInfernoEntryDestination;
  private int infernoEntryPromptState;
  private boolean guidedPostTransportContinuationPending;
  private boolean tormentedLightCreatureAttracted;
  private boolean tormentedTempleReached;
  private WorldPoint guidedTormentedChasmTarget;
  private final Set<WorldPoint> loadedTormentedChasmWallLocations = new LinkedHashSet<>();
  private final Set<WorldPoint> loadedTormentedChasmExitLocations = new LinkedHashSet<>();
  private boolean tormentedChasmSceneSeedPending = true;
  private TeleportHighlighter teleportHighlighter;
  private boolean slayerRefreshQueued;
  private boolean bankOpenedRefreshPending;
  private boolean bankTagSettingsRefreshPending;
  private boolean bankTagTravelRefreshQueued;
  private String lastPublishedTravelSelectionIdentity = "";
  private long pinnedTravelHighlightGeneration = -1L;
  private String pinnedTravelHighlightRouteIdentity = "";
  private long gameTickSequence;
  private long lastSurfaceEntranceDiscoveryTick = Long.MIN_VALUE;
  private String lastSurfaceEntranceDiscoveryIdentity = "";
  private long lastTaskNpcDiscoveryTick = Long.MIN_VALUE;
  private String lastTaskNpcDiscoveryIdentity = "";
  private final Map<String, String> persistedRouteCheckpointCache = new LinkedHashMap<>();
  private final Map<String, Long> routeCheckpointLastWriteTick = new LinkedHashMap<>();
  private final Set<WorldPoint> loadedBankObjectLocations = new LinkedHashSet<>();
  private final Set<TileObject> loadedRouteTransitionObjects = new LinkedHashSet<>();
  private boolean routeTransitionSceneSeedPending = true;
  private boolean bankObjectSceneSeedPending = true;

  @Override
  protected void startUp() {
    migrateRemovedPlaystyle();
    portraitRenderer = new PlayerPortraitRenderer(client);
    portraitCalibrationLoaded =
        portraitRenderer.loadCalibration(
            configManager.getConfiguration(SlayerPlusConfig.GROUP, PORTRAIT_CALIBRATION_KEY));
    recommendationEngine = new SlayerRecommendationEngine(client, config);
    loadoutAnalyzer = new SlayerLoadoutAnalyzer(itemManager);
    shortestPathBridge = new ShortestPathBridge(eventBus);
    bankTagLayoutService =
        new BankTagLayout(
            client, tagManager, layoutManager, bankTagsService, configManager);
    teleportHighlighter =
        new TeleportHighlighter(
            client, clientThread, configManager, this::currentTravelSelectionForHighlight);
    panel =
        new SlayerPlusPanel(
            () -> clientThread.invokeLater(this::startRecommendedRoute),
            () -> clientThread.invokeLater(this::createRecommendedBankTag),
            variant -> clientThread.invokeLater(() -> selectBankTagVariant(variant)),
            () -> clientThread.invokeLater(this::toggleGuidedSession),
            config,
            configManager);
    BufferedImage icon = loadPluginIcon();
    navigationButton =
        NavigationButton.builder()
            .tooltip("SlayerPlus")
            .icon(icon)
            .priority(6)
            .panel(panel)
            .build();
    clientToolbar.addNavigation(navigationButton);
    if (overlayManager != null && readinessOverlay != null) {
      overlayManager.add(readinessOverlay);
    }
    if (overlayManager != null && travelItemOverlay != null) {
      overlayManager.add(travelItemOverlay);
    }
    if (client.getGameState() == GameState.LOGGED_IN) {
      portraitPending = true;
      portraitRetryAfterTick = 0;
      clientThread.invokeLater(
          () -> {
            loadInfernoTravelItemSnapshot();
            loadOwnedSlayerHelmetSnapshot();
            cacheBankContainer(client.getItemContainer(InventoryID.BANK));
            refreshBraceletChargeInfoBox();
            refreshSlayerData();
          });
    }
  }

  private static BufferedImage loadPluginIcon() {
    try {
      return ImageUtil.loadImageResource(SlayerPlusPlugin.class, "slayerplus_sidebar_v2.png");
    } catch (RuntimeException ignored) {
      BufferedImage fallback = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = fallback.createGraphics();
      try {
        graphics.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(42, 38, 30, 245));
        graphics.fillRoundRect(1, 1, 30, 30, 7, 7);
        graphics.setColor(new Color(218, 176, 70));
        graphics.drawRoundRect(1, 1, 29, 29, 7, 7);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 19));
        graphics.drawString("S", 9, 23);
      } finally {
        graphics.dispose();
      }
      return fallback;
    }
  }

  @Override
  protected void shutDown() {
    removeBraceletChargeInfoBox();
    if (shortestPathBridge != null) {
      shortestPathBridge.clear();
    }
    if (overlayManager != null && readinessOverlay != null) {
      overlayManager.remove(readinessOverlay);
    }
    if (overlayManager != null && travelItemOverlay != null) {
      overlayManager.remove(travelItemOverlay);
    }
    if (panel != null) {
      panel.disposeResources();
    }
    if (navigationButton != null) {
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
    currentLoadout = KitPlan.empty();
    currentRecommendation = null;
    clearCachedGuidedTaskTarget();
    currentPreparation = PreparationCatalog.PreparationPlan.none();
    currentMasterId = 0;
    currentTaskName = "";
    currentTaskVariant = TaskVariant.STANDARD_TASK;
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
    if (teleportHighlighter != null) {
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
  public void onGameTick(GameTick event) {
    gameTickSequence++;
    refreshPortrait();
    if (currentTaskName.isEmpty()
        && slayerPluginService != null
        && slayerPluginService.getRemainingAmount() > 0) {
      scheduleSlayerDataRefresh(false);
    }
    if (teleportHighlighter != null) {
      teleportHighlighter.onGameTick();
    }
    if (shouldPollExtraQuiverSnapshotForRegression(gameTickSequence)
        && refreshExtraQuiverAmmoSnapshot()) {
      scheduleSlayerDataRefresh(false);
    }
    reconcileBankInterfaceState();
    updateGuidedSessionPosition();
  }

  @Subscribe
  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
      return;
    }
    String message = Text.removeTags(event.getMessage());
    SlayerTaskChatUpdate taskUpdate = SlayerTaskChatUpdate.parse(message);
    if (taskUpdate != null) {
      pendingChatTaskExpiresAfterTick = gameTickSequence + 5L;
      if (taskUpdate.getRemaining() > 0 && !taskUpdate.getTaskName().isEmpty()) {
        pendingChatTaskName = taskUpdate.getTaskName();
        pendingChatTaskRemaining = taskUpdate.getRemaining();
      }
      if (taskUpdate.getStreak() >= 0) {
        pendingChatStreak = taskUpdate.getStreak();
      }
      if (taskUpdate.getPoints() >= 0) {
        pendingChatPoints = taskUpdate.getPoints();
      }
      scheduleSlayerDataRefresh(false);
    }
    SlayerBraceletChargeTracker.Update braceletUpdate = SlayerBraceletChargeTracker.parse(message);
    if (braceletUpdate != null) {
      configManager.setRSProfileConfiguration(
          SlayerBraceletChargeTracker.CONFIG_GROUP,
          braceletUpdate.getConfigKey(),
          braceletUpdate.getCharges());
      if (braceletUpdate.getCharges() == 0) {
        showDepletedBraceletChargeInfoBox(braceletUpdate.getItemId());
      } else {
        refreshBraceletChargeInfoBox();
      }
    }
    if (!isTormentedLightCreatureAttractionMessage(message)) {
      return;
    }
    if (!guidedSessionActive || guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_TASK) {
      return;
    }
    GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
    if (target == null
        || !"ancient guthixian temple".equals(normalizeTravelName(target.location))) {
      return;
    }
    Player player = client.getLocalPlayer();
    WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
    if (!isOnTearsOfGuthixUpperLayer(playerLocation)) {
      return;
    }
    tormentedLightCreatureAttracted = true;
    taskAreaAnchor = TORMENTED_MANUAL_CHASM_STAGE;
    if (shortestPathBridge != null) {
      shortestPathBridge.clear();
    }
    showGuidedTravelRecommendation(
        "Light creature → Into the chasm",
        "Click the attracted light creature and choose Into the chasm.");
    setGuidedSessionStatus("The light creature is ready — click it and choose Into the chasm.");
  }

  static boolean isTormentedLightCreatureAttractionMessage(String message) {
    return message != null
        && Text.removeTags(message)
            .toLowerCase(Locale.ENGLISH)
            .contains("the light creature is attracted to your beam and comes towards you");
  }

  static boolean shouldPollExtraQuiverSnapshotForRegression(long gameTick) {
    return gameTick > 0 && gameTick % EXTRA_QUIVER_SAFETY_POLL_TICKS == 0;
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged event) {
    if (event.getGameState() == GameState.LOGGED_IN) {
      bankObjectSceneSeedPending = true;
      routePlanObjectSceneSeedPending = true;
      routePlanNpcSceneSeedPending = true;
      portraitPending = true;
      portraitRetryAfterTick = 0;
      clientThread.invokeLater(
          () -> {
            loadInfernoTravelItemSnapshot();
            loadOwnedSlayerHelmetSnapshot();
            cacheBankContainer(client.getItemContainer(InventoryID.BANK));
            refreshSlayerData();
          });
    } else {
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
      if (shouldInvalidateTaskObservationForGameState(event.getGameState())) {
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
      if (teleportHighlighter != null) {
        teleportHighlighter.discardForWorldTransition();
      }
      clearPinnedTravelHighlight();
      if (event.getGameState() != GameState.LOGIN_SCREEN || panel == null) {
        return;
      }
      if (shortestPathBridge != null) {
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
      currentLoadout = KitPlan.empty();
      currentPreparation = PreparationCatalog.PreparationPlan.none();
      currentMasterId = 0;
      currentTaskName = "";
      currentTaskVariant = TaskVariant.STANDARD_TASK;
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

  static boolean shouldInvalidateTaskObservationForGameState(GameState gameState) {
    return gameState == GameState.HOPPING
        || gameState == GameState.CONNECTION_LOST
        || gameState == GameState.LOGGING_IN;
  }

  @Subscribe
  public void onGameObjectSpawned(GameObjectSpawned event) {
    observeLoadedRouteTransitionObject(event.getGameObject());
    observeLoadedRoutePlanObject(event.getGameObject());
    observeLoadedBankObject(event.getGameObject());
    observeLoadedTormentedChasmObject(event.getGameObject());
    observeLoadedPohSpellbookAltar(event.getGameObject());
  }

  @Subscribe
  public void onGameObjectDespawned(GameObjectDespawned event) {
    forgetLoadedRouteTransitionObject(event.getGameObject());
    forgetLoadedRoutePlanObject(event.getGameObject());
    forgetLoadedBankObject(event.getGameObject());
    forgetLoadedTormentedChasmObject(event.getGameObject());
    forgetLoadedPohSpellbookAltar(event.getGameObject());
  }

  @Subscribe
  public void onNpcSpawned(NpcSpawned event) {
    NPC npc = event == null ? null : event.getNpc();
    if (guidedRoutePlan != null
        && routePlanNpcMatchesActivePlan(npc)
        && loadedRoutePlanNpcs.add(npc)) {
      routeEvidenceVersion++;
    }
  }

  @Subscribe
  public void onNpcDespawned(NpcDespawned event) {
    if (event != null && event.getNpc() != null && loadedRoutePlanNpcs.remove(event.getNpc())) {
      routeEvidenceVersion++;
    }
  }

  private void observeLoadedPohSpellbookAltar(GameObject object) {
    if (object == null || !isPohSpellbookAltarIdForRegression(object.getId())) {
      return;
    }
    loadedPohSpellbookAltarId = object.getId();
    loadedPohSpellbookAltarLocation = object.getWorldLocation();
  }

  private void forgetLoadedPohSpellbookAltar(GameObject object) {
    if (object != null
        && object.getId() == loadedPohSpellbookAltarId
        && object.getWorldLocation() != null
        && object.getWorldLocation().equals(loadedPohSpellbookAltarLocation)) {
      loadedPohSpellbookAltarId = -1;
      loadedPohSpellbookAltarLocation = null;
    }
  }

  private void seedLoadedPohSpellbookAltarFromScene() {
    WorldView worldView = client == null ? null : client.getTopLevelWorldView();
    Scene scene = worldView == null ? null : worldView.getScene();
    if (scene == null || scene.getTiles() == null) {
      return;
    }
    for (Tile[][] planeTiles : scene.getTiles()) {
      if (planeTiles == null) {
        continue;
      }
      for (Tile[] column : planeTiles) {
        if (column == null) {
          continue;
        }
        for (Tile tile : column) {
          if (tile == null || tile.getGameObjects() == null) {
            continue;
          }
          for (GameObject object : tile.getGameObjects()) {
            observeLoadedPohSpellbookAltar(object);
            if (loadedPohSpellbookAltarId > 0) {
              return;
            }
          }
        }
      }
    }
  }

  static boolean isPohSpellbookAltarIdForRegression(int objectId) {
    return objectId == ObjectID.POH_ALTAR_ANCIENT
        || objectId == ObjectID.POH_ALTAR_LUNAR
        || objectId == ObjectID.POH_ALTAR_DARK
        || objectId == ObjectID.POH_ALTAR_OCCULT
        || objectId == ObjectID.POH_ALTAR_OCCULT_STANDARD
        || objectId == ObjectID.POH_ALTAR_OCCULT_ANCIENT
        || objectId == ObjectID.POH_ALTAR_OCCULT_LUNAR
        || objectId == ObjectID.POH_ALTAR_OCCULT_ARCEUUS;
  }

  static boolean pohAltarSupportsSpellbookForRegression(int objectId, String requiredSpellbook) {
    String required = normalizeTravelName(requiredSpellbook);
    if (!isPohSpellbookAltarIdForRegression(objectId) || required.isEmpty()) {
      return false;
    }
    if (objectId == ObjectID.POH_ALTAR_OCCULT
        || objectId == ObjectID.POH_ALTAR_OCCULT_STANDARD
        || objectId == ObjectID.POH_ALTAR_OCCULT_ANCIENT
        || objectId == ObjectID.POH_ALTAR_OCCULT_LUNAR
        || objectId == ObjectID.POH_ALTAR_OCCULT_ARCEUUS) {
      return true;
    }
    if (required.contains("standard")) {
      return true;
    }
    return objectId == ObjectID.POH_ALTAR_ANCIENT && required.contains("ancient")
        || objectId == ObjectID.POH_ALTAR_LUNAR && required.contains("lunar")
        || objectId == ObjectID.POH_ALTAR_DARK && required.contains("arceuus");
  }

  @Subscribe
  public void onWallObjectSpawned(WallObjectSpawned event) {
    observeLoadedRouteTransitionObject(event.getWallObject());
    observeLoadedRoutePlanObject(event.getWallObject());
    observeLoadedBankObject(event.getWallObject());
    observeLoadedTormentedChasmObject(event.getWallObject());
  }

  @Subscribe
  public void onWallObjectDespawned(WallObjectDespawned event) {
    forgetLoadedRouteTransitionObject(event.getWallObject());
    forgetLoadedRoutePlanObject(event.getWallObject());
    forgetLoadedBankObject(event.getWallObject());
    forgetLoadedTormentedChasmObject(event.getWallObject());
  }

  @Subscribe
  public void onGroundObjectSpawned(GroundObjectSpawned event) {
    observeLoadedRouteTransitionObject(event.getGroundObject());
    observeLoadedRoutePlanObject(event.getGroundObject());
    observeLoadedBankObject(event.getGroundObject());
    observeLoadedTormentedChasmObject(event.getGroundObject());
  }

  @Subscribe
  public void onGroundObjectDespawned(GroundObjectDespawned event) {
    forgetLoadedRouteTransitionObject(event.getGroundObject());
    forgetLoadedRoutePlanObject(event.getGroundObject());
    forgetLoadedBankObject(event.getGroundObject());
    forgetLoadedTormentedChasmObject(event.getGroundObject());
  }

  @Subscribe
  public void onDecorativeObjectSpawned(DecorativeObjectSpawned event) {
    observeLoadedRouteTransitionObject(event.getDecorativeObject());
    observeLoadedRoutePlanObject(event.getDecorativeObject());
    observeLoadedBankObject(event.getDecorativeObject());
    observeLoadedTormentedChasmObject(event.getDecorativeObject());
  }

  @Subscribe
  public void onDecorativeObjectDespawned(DecorativeObjectDespawned event) {
    forgetLoadedRouteTransitionObject(event.getDecorativeObject());
    forgetLoadedRoutePlanObject(event.getDecorativeObject());
    forgetLoadedBankObject(event.getDecorativeObject());
    forgetLoadedTormentedChasmObject(event.getDecorativeObject());
  }

  private void migrateRemovedPlaystyle() {
    String stored = configManager.getConfiguration(SlayerPlusConfig.GROUP, PLAYSTYLE_KEY);
    if (stored == null || stored.trim().isEmpty()) {
      return;
    }
    String normalized =
        stored.trim().toUpperCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
    if (normalized.equals("BALANCED")
        || normalized.equals("KONAR_FOCUSED")
        || normalized.equals("AFK")) {
      configManager.setConfiguration(
          SlayerPlusConfig.GROUP, PLAYSTYLE_KEY, Preference.Playstyle.FAST_XP.name());
    }
  }

  @Subscribe
  public void onConfigChanged(ConfigChanged event) {
    if (event == null) {
      return;
    }
    if (EASY_TELEPORTS_CONFIG_GROUP.equals(event.getGroup())) {
      if (teleportHighlighter != null) {
        teleportHighlighter.onEasyTeleportsConfigChanged();
      }
      return;
    }
    if (!SlayerPlusConfig.GROUP.equals(event.getGroup())) {
      return;
    }
    String changedKey = event.getKey();
    if (PORTRAIT_CALIBRATION_KEY.equals(changedKey)
        || (changedKey != null && changedKey.startsWith(ROUTE_CHECKPOINT_PREFIX))) {
      return;
    }
    clientThread.invokeLater(
        () -> {
          if (guidedSessionActive && !config.enableShortestPathRouting()) {
            stopGuidedSession("Session stopped because Shortest Path routing is disabled");
          }
          bankTagSettingsRefreshPending = true;
          scheduleSlayerDataRefresh(false);
          if (guidedSessionActive
              && effectiveTaskRemaining() <= 0
              && ("slayerWorkflow".equals(changedKey)
                  || "pointBoostBonusMaster".equals(changedKey))) {
            pointBoostStreakSyncPending = false;
            pointBoostStreakBeforeCompletion = -1;
            routeToMasterForGuidedSession();
          }
        });
  }

  @Subscribe
  public void onVarbitChanged(VarbitChanged event) {
    int varpId = event.getVarpId();
    int varbitId = event.getVarbitId();
    if (varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO
        || varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT) {
      if (refreshExtraQuiverAmmoSnapshot()) {
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
        || varbitId == VarbitID.RUNE_POUCH_QUANTITY_6) {
      scheduleSlayerDataRefresh(false);
    }
  }

  @Subscribe
  public void onItemContainerChanged(ItemContainerChanged event) {
    int containerId = event.getContainerId();
    if (containerId == InventoryID.WORN) {
      refreshBraceletChargeInfoBox();
    }
    if (containerId == InventoryID.DIZANAS_QUIVER_AMMO) {
      if (refreshExtraQuiverAmmoSnapshot()) {
        scheduleSlayerDataRefresh(false);
      }
      return;
    }
    boolean bankChanged = containerId == InventoryID.BANK;
    if (bankChanged) {
      if (!cacheBankContainer(event.getItemContainer())) {
        return;
      }
    } else if (containerId == InventoryID.WORN) {
      if (!captureWornIdentityChange(event.getItemContainer())) {
        return;
      }
      portraitPending = true;
      portraitRetryAfterTick = 0;
    } else if (containerId == InventoryID.INV) {
      if (!captureInventoryChange(event.getItemContainer())) {
        return;
      }
    } else {
      return;
    }
    scheduleSlayerDataRefresh(bankChanged);
    if (!bankChanged) {
      continueWithSelectedCarriedTeleport();
    }
  }

  private void refreshBraceletChargeInfoBox() {
    if (infoBoxManager == null || itemManager == null || client == null) {
      return;
    }
    ItemContainer worn = client.getItemContainer(InventoryID.WORN);
    Item gloves = worn == null ? null : worn.getItem(EquipmentInventorySlot.GLOVES.getSlotIdx());
    int itemId = gloves == null ? -1 : gloves.getId();
    String configKey = SlayerBraceletChargeTracker.configKey(itemId);
    if (configKey.isEmpty()) {
      if (braceletChargeInfoBox != null && braceletChargeInfoBox.isDepleted()) {
        braceletAbsentAfterDepletion = true;
        return;
      }
      removeBraceletChargeInfoBox();
      return;
    }
    Integer charges =
        configManager.getRSProfileConfiguration(
            SlayerBraceletChargeTracker.CONFIG_GROUP, configKey, Integer.class);
    if (charges == null) {
      charges =
          configManager.getConfiguration(
              SlayerBraceletChargeTracker.CONFIG_GROUP, configKey, Integer.class);
      if (charges != null) {
        configManager.unsetConfiguration(SlayerBraceletChargeTracker.CONFIG_GROUP, configKey);
        configManager.setRSProfileConfiguration(
            SlayerBraceletChargeTracker.CONFIG_GROUP, configKey, charges);
      }
    }
    if (braceletAbsentAfterDepletion) {
      charges = SlayerBraceletChargeTracker.MAX_CHARGES;
      configManager.setRSProfileConfiguration(
          SlayerBraceletChargeTracker.CONFIG_GROUP, configKey, charges);
      braceletAbsentAfterDepletion = false;
    }
    int displayedCharges = charges == null ? -1 : charges;
    if (braceletChargeInfoBox == null
        || braceletChargeInfoBoxItemId != itemId
        || braceletChargeInfoBox.isDepleted() != (displayedCharges == 0)) {
      removeBraceletChargeInfoBox();
      braceletChargeInfoBoxItemId = itemId;
      braceletChargeInfoBox =
          new SlayerBraceletChargeInfoBox(
              itemManager.getImage(itemId),
              this,
              itemId == ItemID.BRACELET_OF_SLAUGHTER
                  ? "Bracelet of slaughter"
                  : "Expeditious bracelet",
              displayedCharges);
      infoBoxManager.addInfoBox(braceletChargeInfoBox);
      return;
    }
    braceletChargeInfoBox.setCharges(displayedCharges);
  }

  private void showDepletedBraceletChargeInfoBox(int itemId) {
    if (infoBoxManager == null || itemManager == null || itemId <= 0) {
      return;
    }
    removeBraceletChargeInfoBox();
    braceletChargeInfoBoxItemId = itemId;
    braceletChargeInfoBox =
        new SlayerBraceletChargeInfoBox(
            itemManager.getImage(itemId),
            this,
            itemId == ItemID.BRACELET_OF_SLAUGHTER
                ? "Bracelet of slaughter"
                : "Expeditious bracelet",
            0);
    infoBoxManager.addInfoBox(braceletChargeInfoBox);
  }

  private void removeBraceletChargeInfoBox() {
    if (braceletChargeInfoBox != null && infoBoxManager != null) {
      infoBoxManager.removeInfoBox(braceletChargeInfoBox);
    }
    braceletChargeInfoBox = null;
    braceletChargeInfoBoxItemId = -1;
    braceletAbsentAfterDepletion = false;
  }

  private boolean captureWornIdentityChange(ItemContainer container) {
    Item[] items = container == null ? null : container.getItems();
    if (items == null) {
      return false;
    }
    boolean changed =
        !cachedWornItemIdentityKnown
            || !matchesItemIdentityState(cachedWornItemIdentityState, items);
    if (changed) {
      cachedWornItemIdentityState = snapshotItemIdentityState(items);
      cachedWornItemIdentityKnown = true;
    }
    return changed;
  }

  private boolean captureInventoryChange(ItemContainer container) {
    Item[] items = container == null ? null : container.getItems();
    if (items == null) {
      return false;
    }
    boolean changed =
        !cachedInventoryContainerStateKnown
            || !matchesExactItemState(cachedInventoryContainerState, items);
    if (changed) {
      cachedInventoryContainerState = snapshotExactItemState(items);
      cachedInventoryContainerStateKnown = true;
    }
    return changed;
  }

  private static boolean matchesItemIdentityState(int[] cachedState, Item[] items) {
    if (cachedState == null || cachedState.length != items.length) {
      return false;
    }
    for (int slot = 0; slot < items.length; slot++) {
      Item item = items[slot];
      if (cachedState[slot] != (item == null ? -1 : item.getId())) {
        return false;
      }
    }
    return true;
  }

  private static int[] snapshotItemIdentityState(Item[] items) {
    int[] state = new int[items.length];
    for (int slot = 0; slot < items.length; slot++) {
      state[slot] = items[slot] == null ? -1 : items[slot].getId();
    }
    return state;
  }

  private static boolean matchesExactItemState(int[] cachedState, Item[] items) {
    if (cachedState == null || cachedState.length != items.length * 2) {
      return false;
    }
    for (int slot = 0; slot < items.length; slot++) {
      Item item = items[slot];
      int offset = slot * 2;
      if (cachedState[offset] != (item == null ? -1 : item.getId())
          || cachedState[offset + 1] != (item == null ? 0 : item.getQuantity())) {
        return false;
      }
    }
    return true;
  }

  private static int[] snapshotExactItemState(Item[] items) {
    int[] state = new int[items.length * 2];
    for (int slot = 0; slot < items.length; slot++) {
      Item item = items[slot];
      int offset = slot * 2;
      state[offset] = item == null ? -1 : item.getId();
      state[offset + 1] = item == null ? 0 : item.getQuantity();
    }
    return state;
  }

  static boolean shouldRefreshCarriedContainerForRegression(
      int containerId, boolean identityChanged, boolean exactStateChanged) {
    return containerId == InventoryID.WORN
        ? identityChanged
        : containerId == InventoryID.INV && exactStateChanged;
  }

  private void scheduleSlayerDataRefresh(boolean bankOpened) {
    bankOpenedRefreshPending |= bankOpened;
    if (slayerRefreshQueued) {
      return;
    }
    slayerRefreshQueued = true;
    clientThread.invokeLater(
        () -> {
          boolean handleBank = bankOpenedRefreshPending;
          boolean refreshBankTag = bankTagSettingsRefreshPending;
          bankOpenedRefreshPending = false;
          bankTagSettingsRefreshPending = false;
          slayerRefreshQueued = false;
          if (handleBank) {
            captureGuidedTaskDetourSnapshot();
            ItemContainer liveBank = client.getItemContainer(InventoryID.BANK);
            if (liveBank != null) {
              cacheBankContainer(liveBank);
            }
          }
          refreshSlayerData();
          if (handleBank) {
            handleBankOpenedForGuidedSession();
          }
          if (refreshBankTag
              && shouldRefreshExistingBankTag(bankTagLayoutCreated, isSlayerPlusBankTagOpen())) {
            createRecommendedBankTagNow();
          }
        });
  }

  static boolean shouldRefreshExistingBankTag(boolean layoutCreated, boolean tagOpen) {
    return layoutCreated || tagOpen;
  }

  @Subscribe
  public void onPluginMessage(PluginMessage event) {
    if (event == null
        || !"shortestpath".equals(event.getNamespace())
        || !"transports".equals(event.getName())
        || clientThread == null) {
      return;
    }
    ShortestPathBridge requestBridge = shortestPathBridge;
    ShortestPathBridge.TransportRequestToken requestToken =
        requestBridge == null ? null : requestBridge.claimPendingTransportResponse();
    if (requestToken == null) {
      return;
    }
    Map<String, Object> data;
    try {
      data = snapshotTransportData(event.getData());
    } catch (RuntimeException ex) {
      requestBridge.releaseTransportResponseClaim(requestToken);
      return;
    }
    try {
      clientThread.invokeLater(
          () -> {
            try {
              processShortestPathTransportMessage(
                  data,
                  guidedTravelRequestGeneration,
                  guidedTravelRequestRouteIdentity,
                  guidedSessionPhase,
                  requestToken,
                  requestBridge);
            } catch (RuntimeException ex) {
              releaseShortestPathTransportClaim(requestBridge, requestToken);
              log.debug("Unable to process Shortest Path transport response", ex);
            }
          });
    } catch (RuntimeException ex) {
      requestBridge.releaseTransportResponseClaim(requestToken);
    }
  }

  private void processShortestPathTransportMessage(
      Map<String, Object> data,
      long requestGeneration,
      String requestIdentity,
      GuidedSessionPhase requestPhase,
      ShortestPathBridge.TransportRequestToken requestToken,
      ShortestPathBridge requestBridge) {
    boolean routingToTask = guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_TASK;
    boolean routingToPrepBank = guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK;
    boolean routingToMaster = guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER;
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
        || !travelCoordinator.ownsCurrentRoute(requestIdentity)) {
      releaseShortestPathTransportClaim(requestBridge, requestToken);
      return;
    }
    String identity;
    if (routingToMaster) {
      identity = masterRouteIdentity(MasterRoutes.find(getRoutingMasterId()));
    } else if (routingToPrepBank) {
      identity = guidedTravelRequestRouteIdentity;
    } else {
      identity = routeIdentity(resolveGuidedTaskTargetForRouting());
    }
    if (!requestIdentity.equals(identity)) {
      releaseShortestPathTransportClaim(requestBridge, requestToken);
      return;
    }
    if (requestBridge == null || !requestBridge.tryAcceptTransportResponse(requestToken, data)) {
      return;
    }
    Object displayInfos = data == null ? null : data.get("displayInfo");
    TravelChoice previousSelection = travelCoordinator.current();
    GuidedTaskTarget activeTarget = routingToMaster ? null : resolveGuidedTaskTargetForRouting();
    if (shouldRetainVerifiedTravelItem(
        activeTarget == null ? "" : activeTarget.location,
        previousSelection,
        previousSelection.hasPhysicalItem()
            && isTravelItemStillOwned(previousSelection.getItemId()))) {
      return;
    }
    String previousTravelIdentity = previousSelection.travelIdentity();
    TravelRouteMatch match = findTravelItemFromTransportInfo(data, routingToMaster);
    if (match == null
        && shouldRetainPinnedTravelHighlight(
            requestGeneration,
            requestIdentity,
            previousSelection,
            pinnedTravelHighlightGeneration,
            pinnedTravelHighlightRouteIdentity,
            findOwnedTravelItem(previousSelection.getItemName()) != null)) {
      return;
    }
    if (match != null) {
      applyTravelRouteMatch(match, identity);
    } else {
      TravelInstruction instruction = findTravelInstructionFromTransportInfo(displayInfos);
      String family = instruction == null ? "" : instruction.family;
      String destination = instruction == null ? "" : instruction.destination;
      TravelChoice selection =
          travelCoordinator.resolveNonItem(identity, family, destination);
      publishTravelSelection(
          selection,
          "Follow the transport selected by Shortest Path. No unrelated inventory teleport will be"
              + " substituted.");
    }
    String currentTravelIdentity = travelCoordinator.current().travelIdentity();
    if (shouldRefreshTravelConsumers(previousTravelIdentity, currentTravelIdentity)) {
      refreshOpenBankTagForTravelSelection();
    }
    continueWithSelectedCarriedTeleport();
  }

  private static void releaseShortestPathTransportClaim(
      ShortestPathBridge requestBridge, ShortestPathBridge.TransportRequestToken requestToken) {
    if (requestBridge != null) {
      requestBridge.releaseTransportResponseClaim(requestToken);
    }
  }

  private void continueWithSelectedCarriedTeleport() {
    if (!guidedSessionActive || !guidedBankTransportTracking) {
      return;
    }
    TravelChoice selection = currentTravelSelection();
    if (!selection.hasPhysicalItem()
        || findCarriedTravelItemForTransport(selection.getItemName()) == null) {
      return;
    }
    if (shouldContinueMasterAfterBankPickupForRegression(
        guidedSessionPhase, guidedMasterBankPickupPending)) {
      guidedMasterBankPickupPending = false;
      routeToMasterForGuidedSession(false);
      return;
    }
    if (guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_TASK) {
      return;
    }
    if (!guidedTaskBankPickupPending) {
      return;
    }
    routeToTaskForGuidedSession(false, "Using the selected carried teleport");
    guidedTaskBankPickupPending = false;
  }

  static boolean shouldContinueMasterAfterBankPickupForRegression(
      GuidedSessionPhase phase, boolean bankPickupPending) {
    return bankPickupPending && phase == GuidedSessionPhase.ROUTING_TO_MASTER;
  }

  static boolean shouldRefreshTravelConsumers(
      String previousTravelIdentity, String currentTravelIdentity) {
    return currentTravelIdentity != null && !currentTravelIdentity.equals(previousTravelIdentity);
  }

  static boolean shouldRetainPinnedTravelHighlight(
      long requestGeneration,
      String requestIdentity,
      TravelChoice selection,
      long pinnedGeneration,
      String pinnedIdentity,
      boolean selectedItemStillOwned) {
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
      String location, TravelChoice selection, boolean selectedItemStillOwned) {
    return TravelRoutes.locksFirstResolvedTravelItem(location)
        && selection != null
        && selection.getStatus() == TravelChoice.Status.RESOLVED_ITEM
        && selection.hasPhysicalItem()
        && selectedItemStillOwned;
  }

  private static Map<String, Object> snapshotTransportData(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return java.util.Collections.emptyMap();
    }
    Map<String, Object> snapshot = new LinkedHashMap<>();
    for (String key : new String[] {"displayInfo", "origin", "destination", "objectInfo"}) {
      Object value = source.get(key);
      if (value instanceof List<?>) {
        snapshot.put(key, new ArrayList<>((List<?>) value));
      } else if (value instanceof Object[]) {
        snapshot.put(key, new ArrayList<>(Arrays.asList((Object[]) value)));
      } else if (value != null) {
        snapshot.put(key, value);
      }
    }
    return snapshot;
  }

  @Subscribe
  public void onMenuEntryAdded(MenuEntryAdded event) {
    if (teleportHighlighter != null) {
      teleportHighlighter.onMenuEntryAdded(event);
    }
    if (event != null) {
      highlightGuidedRoutePlanMenuEntry(event.getMenuEntry());
    }
  }

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked event) {
    if (guidedRoutePlan == null
        || event == null
        || !isRoutePlanObjectMenuAction(event.getMenuAction())) {
      return;
    }
    String action = Text.removeTags(event.getMenuOption());
    String objectName = Text.removeTags(event.getMenuTarget());
    RouteEvidence.ObjectAction observation;
    try {
      observation = RouteEvidence.ObjectAction.of(event.getId(), objectName, action);
    } catch (IllegalArgumentException ignored) {
      return;
    }
    if (!matchesAnyRoutePlanObjectSpec(observation)
        || !observedRoutePlanInteractions.add(observation)) {
      return;
    }
    observedRoutePlanInteractionExpiresAfterTick = gameTickSequence + 3L;
    routeEvidenceVersion++;
  }

  private static boolean isRoutePlanObjectMenuAction(MenuAction action) {
    return action == MenuAction.ITEM_USE_ON_GAME_OBJECT
        || action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT
        || action == MenuAction.GAME_OBJECT_FIRST_OPTION
        || action == MenuAction.GAME_OBJECT_SECOND_OPTION
        || action == MenuAction.GAME_OBJECT_THIRD_OPTION
        || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
        || action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
  }

  private void highlightGuidedRoutePlanLiveMenu() {
    if (client == null) {
      return;
    }
    Menu menu = client.getMenu();
    if (menu == null) {
      return;
    }
    MenuEntry[] entries = menu.getMenuEntries();
    highlightGuidedRoutePlanMenuEntries(entries);
    if (entries != null) {
      menu.setMenuEntries(entries);
    }
  }

  private void highlightGuidedRoutePlanMenuEntries(MenuEntry[] entries) {
    if (entries == null) {
      return;
    }
    for (MenuEntry entry : entries) {
      highlightGuidedRoutePlanMenuEntry(entry);
      Menu subMenu = entry == null ? null : entry.getSubMenu();
      if (subMenu != null) {
        MenuEntry[] children = subMenu.getMenuEntries();
        highlightGuidedRoutePlanMenuEntries(children);
        if (children != null) {
          subMenu.setMenuEntries(children);
        }
      }
    }
  }

  private void highlightGuidedRoutePlanMenuEntry(MenuEntry entry) {
    if (entry == null
        || guidedRoutePlan == null
        || guidedRoutePlanCursor == null
        || !isRoutePlanObjectMenuAction(entry.getType())) {
      return;
    }
    RoutePlan.Leg activeLeg = guidedRoutePlan.getLeg(guidedRoutePlanCursor.getActiveLegId());
    if (activeLeg == null || activeLeg.getKind() != RoutePlan.LegKind.MANUAL_INTERACTION) {
      return;
    }
    RouteEvidence.ObjectAction observation;
    try {
      observation =
          RouteEvidence.ObjectAction.of(
              entry.getIdentifier(),
              Text.removeTags(entry.getTarget()),
              Text.removeTags(entry.getOption()));
    } catch (IllegalArgumentException ignored) {
      return;
    }
    if (routePlanMenuPredicateMatches(activeLeg.getReadinessPredicate(), observation)) {
      entry.setOption("<col=00ff00>" + Text.removeTags(entry.getOption()) + "</col>");
    }
  }

  private static boolean routePlanMenuPredicateMatches(
      RouteCheck predicate, RouteEvidence.ObjectAction observation) {
    if (predicate == null || observation == null) {
      return false;
    }
    if (predicate.getKind() == RouteCheck.Kind.TRACKED_OBJECT_ACTION) {
      return routePlanObjectSpecMatches(predicate.getObjectActionSpec(), observation);
    }
    for (RouteCheck child : predicate.getChildren()) {
      if (routePlanMenuPredicateMatches(child, observation)) {
        return true;
      }
    }
    return false;
  }

  private static boolean routePlanObjectSpecMatches(
      RouteCheck.ObjectActionSpec spec, RouteEvidence.ObjectAction observation) {
    if (spec == null || observation == null) {
      return false;
    }
    boolean identityMatches =
        (!spec.getObjectIds().isEmpty() && spec.getObjectIds().contains(observation.getObjectId()))
            || (!spec.getObjectNames().isEmpty()
                && spec.getObjectNames().contains(observation.getObjectName()));
    return identityMatches && spec.getActions().contains(observation.getAction());
  }

  @Subscribe(priority = -1000f)
  public void onPostMenuSort(PostMenuSort event) {
    if (teleportHighlighter != null) {
      teleportHighlighter.onPostMenuSort();
    }
    highlightGuidedRoutePlanLiveMenu();
  }

  @Subscribe(priority = -1000f)
  public void onMenuOpened(MenuOpened event) {
    if (teleportHighlighter != null) {
      teleportHighlighter.onMenuOpened(event);
    }
    if (event != null) {
      highlightGuidedRoutePlanMenuEntries(event.getMenuEntries());
    }
  }

  @Subscribe
  public void onWidgetLoaded(WidgetLoaded event) {
    if (guidedRoutePlan != null
        && event != null
        && guidedRoutePlanWidgetGroups.contains(event.getGroupId())
        && loadedRoutePlanWidgetGroups.add(event.getGroupId())) {
      routeEvidenceVersion++;
    }
    if (teleportHighlighter != null) {
      teleportHighlighter.onWidgetLoaded(event);
    }
    if (event.getGroupId() == InterfaceID.BANKMAIN) {
      if (!shouldHandleBankWidgetLoadForRegression(bankInterfaceOpen, event.getGroupId())) {
        return;
      }
      bankInterfaceOpen = true;
      scheduleSlayerDataRefresh(true);
      return;
    }
    if (isQuiverRelevantInterfaceGroupForRegression(event.getGroupId())
        && refreshExtraQuiverAmmoSnapshot()) {
      scheduleSlayerDataRefresh(false);
    }
  }

  @Subscribe
  public void onWidgetClosed(WidgetClosed event) {
    if (event != null && loadedRoutePlanWidgetGroups.remove(event.getGroupId())) {
      routeEvidenceVersion++;
    }
    if (event != null && event.getGroupId() == InterfaceID.BANKMAIN) {
      bankInterfaceOpen = false;
      handleBankClosedForGuidedSession();
    }
  }

  static boolean shouldHandleBankWidgetLoadForRegression(boolean bankAlreadyOpen, int groupId) {
    return groupId == InterfaceID.BANKMAIN && !bankAlreadyOpen;
  }

  private void reconcileBankInterfaceState() {
    Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
    boolean visiblyOpen = bankItems != null && !bankItems.isHidden();
    if (!shouldReconcileBankVisibilityForRegression(bankInterfaceOpen, visiblyOpen)) {
      return;
    }
    bankInterfaceOpen = visiblyOpen;
    if (visiblyOpen) {
      scheduleSlayerDataRefresh(true);
    } else {
      handleBankClosedForGuidedSession();
    }
  }

  static boolean shouldReconcileBankVisibilityForRegression(
      boolean recordedOpen, boolean visiblyOpen) {
    return recordedOpen != visiblyOpen;
  }

  static boolean isQuiverRelevantInterfaceGroupForRegression(int groupId) {
    return groupId == InterfaceID.EQUIPMENT
        || groupId == InterfaceID.EQUIPMENT_SIDE
        || groupId == InterfaceID.WORNITEMS
        || groupId == InterfaceID.DIZANAS_QUIVER;
  }

  private void queueTeleportMenuHighlight() {
    if (teleportHighlighter != null) {
      teleportHighlighter.onMenuStructureChanged();
    }
  }

  private void refreshGuidedTeleportWidgetHighlights() {
    if (teleportHighlighter != null) {
      teleportHighlighter.refreshNow();
    }
  }

  private void restoreGuidedTeleportWidgetHighlights() {
    if (teleportHighlighter != null) {
      teleportHighlighter.restoreWidgetHighlights();
    }
  }

  private void clearTeleportHighlightCaptures() {
    if (teleportHighlighter != null) {
      teleportHighlighter.clearCaptures();
    }
  }

  private void refreshPortrait() {
    SlayerPlusPanel currentPanel = panel;
    if (currentPanel == null
        || portraitRenderer == null
        || client.getGameState() != GameState.LOGGED_IN) {
      return;
    }
    if (!shouldCheckPortraitForRegression(
        gameTickSequence, portraitPending, portraitRetryAfterTick)) {
      return;
    }
    Player player = client.getLocalPlayer();
    if (player == null) {
      return;
    }
    String playerName = player.getName();
    if (playerName != null
        && !playerName.trim().isEmpty()
        && !playerName.trim().equals(lastAccountName)) {
      lastAccountName = playerName.trim();
      String accountName = lastAccountName;
      SwingUtilities.invokeLater(() -> currentPanel.showAccountName(accountName));
    }
    PlayerComposition composition = player.getPlayerComposition();
    if (composition == null) {
      return;
    }
    int appearanceHash = Arrays.hashCode(composition.getEquipmentIds());
    appearanceHash = 31 * appearanceHash + Arrays.hashCode(composition.getColors());
    appearanceHash = 31 * appearanceHash + composition.getGender();
    if (appearanceHash != lastAppearanceHash) {
      lastAppearanceHash = appearanceHash;
      portraitPending = true;
      portraitRetryAfterTick = 0;
    }
    if (!portraitPending) {
      return;
    }
    if (player.getAnimation() != -1 || player.getPoseAnimation() != player.getIdlePoseAnimation()) {
      return;
    }
    int originalPoseFrame = player.getPoseAnimationFrame();
    try {
      player.setPoseAnimationFrame(0);
      int[] equipmentIds = composition.getEquipmentIds();
      int weaponIndex = KitType.WEAPON.getIndex();
      int torsoIndex = KitType.TORSO.getIndex();
      boolean weaponEquipped =
          weaponIndex >= 0
              && weaponIndex < equipmentIds.length
              && equipmentIds[weaponIndex] >= PlayerComposition.ITEM_OFFSET;
      int originalWeapon = weaponEquipped ? equipmentIds[weaponIndex] : 0;
      Model bodyModel;
      if (weaponEquipped) {
        equipmentIds[weaponIndex] = 0;
        composition.setHash();
        try {
          bodyModel = player.getModel();
        } finally {
          equipmentIds[weaponIndex] = originalWeapon;
          composition.setHash();
        }
      } else {
        bodyModel = player.getModel();
      }
      if (bodyModel == null) {
        deferPortraitRetry();
        return;
      }
      Model displayModel = player.getModel();
      if (displayModel == null) {
        deferPortraitRetry();
        return;
      }
      boolean torsoArmourEquipped =
          torsoIndex >= 0
              && torsoIndex < equipmentIds.length
              && equipmentIds[torsoIndex] >= PlayerComposition.ITEM_OFFSET;
      boolean allowCalibration =
          !portraitCalibrationLoaded && !weaponEquipped && torsoArmourEquipped;
      BufferedImage portrait =
          portraitRenderer.render(
              displayModel, bodyModel, portraitCalibrationLoaded || allowCalibration);
      if (portrait != null) {
        if (!portraitCalibrationLoaded && portraitRenderer.hasCalibration()) {
          String calibration = portraitRenderer.exportCalibration();
          if (calibration != null) {
            configManager.setConfiguration(
                SlayerPlusConfig.GROUP, PORTRAIT_CALIBRATION_KEY, calibration);
            portraitCalibrationLoaded = true;
          }
        }
        portraitPending = false;
        portraitRetryAfterTick = 0;
        SwingUtilities.invokeLater(() -> currentPanel.setPortrait(portrait));
      } else {
        deferPortraitRetry();
      }
    } catch (RuntimeException ex) {
      log.debug("Unable to capture SlayerPlus portrait; retrying later", ex);
      deferPortraitRetry();
    } finally {
      try {
        player.setPoseAnimationFrame(originalPoseFrame);
      } catch (RuntimeException ex) {
        log.debug("Unable to restore portrait pose after scene transition", ex);
      }
    }
  }

  static boolean shouldCheckPortraitForRegression(long tick, boolean pending, long retryAfterTick) {
    if (pending) {
      return tick >= retryAfterTick;
    }
    return tick % PORTRAIT_APPEARANCE_POLL_TICKS == 0;
  }

  private void deferPortraitRetry() {
    portraitRetryAfterTick = gameTickSequence + PORTRAIT_APPEARANCE_POLL_TICKS;
  }

  private void refreshSlayerData() {
    SlayerPlusPanel currentPanel = panel;
    if (currentPanel == null || client.getGameState() != GameState.LOGGED_IN) {
      return;
    }
    refreshExtraQuiverAmmoSnapshot();
    loadoutAnalyzer.setHelmetPreference(
        configManager.getConfiguration(SlayerPlusConfig.GROUP, HelmetPreference.CONFIG_KEY));
    loadoutAnalyzer.setDesertEliteDiaryComplete(
        client.getVarbitValue(VarbitID.DESERT_DIARY_ELITE_COMPLETE) > 0);
    achievementDiaries = SlayerAchievementDiarySnapshot.capture(client);
    loadoutAnalyzer.setAchievementDiaries(achievementDiaries);
    if (!profileOwnedSlayerHelmetSnapshotLoaded) {
      loadOwnedSlayerHelmetSnapshot();
    }
    Set<String> ownedSlayerHelmetNames = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    ownedSlayerHelmetNames.addAll(
        loadoutAnalyzer.ownedSlayerHelmetNames(
            client.getItemContainer(InventoryID.INV),
            client.getItemContainer(InventoryID.WORN),
            cachedBankItems));
    ownedSlayerHelmetNames.addAll(
        loadoutAnalyzer.slayerHelmetNamesForItemIds(profileOwnedSlayerHelmetItemIds));
    List<String> ownedSlayerHelmets = new ArrayList<>(ownedSlayerHelmetNames);
    int previousRemaining = observedTaskRemaining;
    String previousTaskName = observedTaskName;
    String accountName = getAccountName();
    boolean whileGuthixSleepsFinished =
        Quest.WHILE_GUTHIX_SLEEPS.getState(client) == QuestState.FINISHED;
    int serviceRemaining =
        slayerPluginService == null ? 0 : slayerPluginService.getRemainingAmount();
    int serviceInitialAmount =
        slayerPluginService == null ? 0 : slayerPluginService.getInitialAmount();
    String serviceTaskName =
        slayerPluginService == null || slayerPluginService.getTask() == null
            ? ""
            : slayerPluginService.getTask().trim();
    String serviceTaskLocation =
        slayerPluginService == null || slayerPluginService.getTaskLocation() == null
            ? ""
            : slayerPluginService.getTaskLocation().trim();
    int liveRemaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
    int profileRemaining = readRuneLiteSlayerProfileInt(RUNELITE_SLAYER_AMOUNT_KEY);
    boolean chatUpdatePending = gameTickSequence <= pendingChatTaskExpiresAfterTick;
    boolean chatAssignmentPending = pendingChatTaskRemaining > 0 && chatUpdatePending;
    int remaining =
        resolveTaskRemainingForRegression(
            observedTaskRemaining,
            serviceRemaining,
            liveRemaining,
            profileRemaining,
            chatAssignmentPending ? pendingChatTaskRemaining : -1);
    int liveInitialAmount = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
    int profileInitialAmount = readRuneLiteSlayerProfileInt(RUNELITE_SLAYER_INITIAL_AMOUNT_KEY);
    int initialAmount =
        Math.max(
            remaining,
            firstPositive(
                serviceInitialAmount,
                liveInitialAmount,
                profileInitialAmount,
                chatAssignmentPending ? pendingChatTaskRemaining : -1));
    int liveMasterId = client.getVarbitValue(VarbitID.SLAYER_MASTER);
    int inferredMasterId = remaining > 0 ? inferNearbySlayerMasterId() : 0;
    int storedMasterId = readSlayerPlusProfileInt(LAST_SLAYER_MASTER_SNAPSHOT_KEY);
    int masterId = firstPositive(liveMasterId, inferredMasterId, storedMasterId);
    int livePoints = client.getVarbitValue(VarbitID.SLAYER_POINTS);
    int points =
        preferLiveOrProfileValue(
            livePoints,
            readRuneLiteSlayerProfileInt(RUNELITE_SLAYER_POINTS_KEY),
            chatUpdatePending ? pendingChatPoints : -1);
    int normalStreak = client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
    int resolvedNormalStreak =
        preferLiveOrProfileValue(
            normalStreak,
            readRuneLiteSlayerProfileInt(RUNELITE_SLAYER_STREAK_KEY),
            chatUpdatePending ? pendingChatStreak : -1);
    int streak =
        masterId == KRYSTILIA_MASTER_ID
            ? client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED)
            : resolvedNormalStreak;
    int previousNormalStreak = observedNormalTaskStreak;
    currentMasterId = masterId;
    if (masterId > 0) {
      lastUsedMasterId = masterId;
      storeLastSlayerMasterId(masterId);
    }
    if (remaining <= 0) {
      currentRecommendation = null;
      clearCachedGuidedTaskTarget();
      currentLoadout = KitPlan.empty();
      currentPreparation = PreparationCatalog.PreparationPlan.none();
      currentTaskName = "";
      currentTaskVariant = TaskVariant.STANDARD_TASK;
      lastResolvedRoutingProfileIdentity = "";
      observedTaskRemaining = 0;
      observedTaskName = "";
      observedNormalTaskStreak = resolvedNormalStreak;
      handleGuidedTaskTransition(
          previousRemaining, previousTaskName, 0, "", previousNormalStreak, resolvedNormalStreak);
      boolean sessionActive = guidedSessionActive;
      boolean sessionAvailable = isGuidedSessionAvailable();
      String sessionStatus = getGuidedSessionDisplayStatus();
      String pointBoostStatus = pointBoostPanelStatus(resolvedNormalStreak, false, masterId);
      String nextMasterName =
          masterDisplayNameForRegression(
              masterForNextAssignment(resolvedNormalStreak), whileGuthixSleepsFinished);
      SwingUtilities.invokeLater(
          () -> {
            currentPanel.configureOwnedSlayerHelmets(ownedSlayerHelmets);
            currentPanel.showAccountName(accountName);
            currentPanel.showNoTask(points, streak, nextMasterName);
            currentPanel.showPointBoostStatus(pointBoostStatus);
            currentPanel.showPreparation(currentPreparation);
            currentPanel.showSessionState(sessionActive, sessionAvailable, sessionStatus);
          });
      return;
    }
    String profileTaskName = readRuneLiteSlayerProfileString(RUNELITE_SLAYER_TASK_NAME_KEY);
    String taskName =
        serviceRemaining > 0 && !serviceTaskName.isEmpty()
            ? formatName(serviceTaskName)
            : liveRemaining > 0
                ? readTaskName()
                : !profileTaskName.isEmpty() && profileRemaining > 0
                    ? formatName(profileTaskName)
                    : chatAssignmentPending ? pendingChatTaskName : "Unknown task";
    if (isPointBoosting()
        && masterId == BoostCoordinator.TURAEL_AYA_MASTER_ID
        && TuraelBoost.find(taskName) != null) {
      currentTaskVariant = TaskVariant.STANDARD_TASK;
    } else if (!sameTask(currentTaskName, taskName)) {
      currentTaskVariant = VariantCatalog.getDefaultVariant(taskName);
    }
    currentTaskName = taskName == null ? "" : taskName.trim();
    if (!VariantCatalog.getAvailableVariants(taskName).contains(currentTaskVariant)) {
      currentTaskVariant = VariantCatalog.getDefaultVariant(taskName);
    }
    boolean konarAssignment = masterId == KONAR_MASTER_ID;
    String assignedLocation =
        konarAssignment
            ? !serviceTaskLocation.isEmpty()
                ? serviceTaskLocation
                : liveRemaining > 0 ? readAssignedLocation() : profileSlayerLocation()
            : "Not restricted";
    Recommendation recommendation =
        recommendationEngine == null
            ? null
            : recommendationEngine.recommend(taskName, masterId, assignedLocation);
    if (recommendation != null && loadoutAnalyzer != null) {
      recommendation =
          loadoutAnalyzer.resolveOwnedAutomaticRecommendation(
              taskName,
              recommendation,
              client.getItemContainer(InventoryID.INV),
              client.getItemContainer(InventoryID.WORN),
              cachedBankItems,
              bankScanned,
              currentTaskVariant,
              config);
    }
    Recommendation resolvedRecommendation = recommendation;
    currentRecommendation = resolvedRecommendation;
    KitPlan loadout;
    if (!config.showLoadoutRecommendations()) {
      loadout = KitPlan.hidden();
    } else if (loadoutAnalyzer == null) {
      loadout =
          new KitPlan(
              "Loadout scanner unavailable",
              "Loadout scanner unavailable",
              "No item scan available");
    } else {
      loadout =
          loadoutAnalyzer.analyze(
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
              cachedExtraQuiverAmmoQuantity);
    }
    currentLoadout = loadout;
    VariantCatalog.ResolvedTarget preparationTarget =
        VariantCatalog.resolve(
            currentTaskName, currentTaskVariant, resolvedRecommendation, config);
    String preparationEncounter =
        preparationTarget == null ? currentTaskName : preparationTarget.getTaskName();
    String preparationLocation =
        preparationTarget == null
            ? resolvedRecommendation.getLocation()
            : preparationTarget.getLocation();
    TaskStrategy preparationStrategy =
        preparationTarget == null
            ? resolvedRecommendation.getStrategy()
            : preparationTarget.getStrategy();
    currentPreparation =
        PreparationCatalog.resolve(
            preparationEncounter,
            preparationLocation,
            preparationStrategy,
            client,
            client.getItemContainer(InventoryID.INV),
            client.getItemContainer(InventoryID.WORN),
            cachedBankItems);
    observedTaskRemaining = remaining;
    observedTaskName = currentTaskName;
    observedNormalTaskStreak = resolvedNormalStreak;
    handleGuidedTaskTransition(
        previousRemaining,
        previousTaskName,
        remaining,
        currentTaskName,
        previousNormalStreak,
        resolvedNormalStreak);
    handleGuidedSpellbookReadinessTransition();
    GuidedTaskTarget displayTarget = resolveGuidedTaskTarget();
    String resolvedRoutingProfileIdentity = routeIdentity(displayTarget);
    String previousRoutingProfileIdentity = lastResolvedRoutingProfileIdentity;
    boolean routingProfileChanged =
        !previousRoutingProfileIdentity.isEmpty()
            && !previousRoutingProfileIdentity.equals(resolvedRoutingProfileIdentity);
    lastResolvedRoutingProfileIdentity = resolvedRoutingProfileIdentity;
    if (routingProfileChanged) {
      invalidateTravelStateForProfileChange(previousRoutingProfileIdentity);
      if (!resolvedRoutingProfileIdentity.isEmpty()
          && guidedSessionActive
          && config.enableShortestPathRouting()
          && (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_SPELLBOOK
              || guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_TASK
              || guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK
              || guidedSessionPhase == GuidedSessionPhase.TASKING)) {
        boolean bankAwareProfileReroute =
            shouldUseBankAwareProfileReroute(
                bankScanned, client.getItemContainer(InventoryID.BANK) != null);
        routeToTaskForGuidedSession(bankAwareProfileReroute, "Encounter/profile changed");
        if (bankTagLayoutCreated || isSlayerPlusBankTagOpen()) {
          createRecommendedBankTag();
        }
      }
    }
    String positioningNote =
        displayTarget == null || displayTarget.profile == null
            ? ""
            : displayTarget.profile.getPositioningNote();
    String displayedTaskName = taskName;
    boolean sessionActive = guidedSessionActive;
    boolean sessionAvailable = isGuidedSessionAvailable();
    String sessionStatus = getGuidedSessionDisplayStatus();
    String pointBoostStatus = pointBoostPanelStatus(resolvedNormalStreak, true, masterId);
    String masterName = masterDisplayNameForRegression(masterId, whileGuthixSleepsFinished);
    SwingUtilities.invokeLater(
        () -> {
          currentPanel.configureOwnedSlayerHelmets(ownedSlayerHelmets);
          currentPanel.showAccountName(accountName);
          currentPanel.configureTaskVariants(taskName, currentTaskVariant);
          currentPanel.showTask(
              displayedTaskName,
              remaining,
              initialAmount,
              masterName,
              assignedLocation,
              points,
              streak,
              konarAssignment);
          currentPanel.showPointBoostStatus(pointBoostStatus);
          currentPanel.showRecommendation(
              resolvedRecommendation, loadout, config.enableShortestPathRouting());
          currentPanel.showPositioningNote(positioningNote);
          currentPanel.showPreparation(currentPreparation);
          currentPanel.showSessionState(sessionActive, sessionAvailable, sessionStatus);
        });
  }

  private void selectBankTagVariant(TaskVariant variant) {
    boolean refreshOpenBankTag = isSlayerPlusBankTagOpen();
    clearGuidedTaskDetourSnapshot();
    TaskVariant requested = variant == null ? TaskVariant.STANDARD_TASK : variant;
    if (!VariantCatalog.getAvailableVariants(currentTaskName).contains(requested)) {
      currentTaskVariant = VariantCatalog.getDefaultVariant(currentTaskName);
    } else {
      currentTaskVariant = requested;
    }
    refreshSlayerData();
    if (refreshOpenBankTag) {
      createRecommendedBankTag();
    }
  }

  private boolean isSlayerPlusBankTagOpen() {
    if (bankTagsService == null || client.getItemContainer(InventoryID.BANK) == null) {
      return false;
    }
    String activeTag = bankTagsService.getActiveTag();
    return activeTag != null
        && Text.standardize(activeTag)
            .equals(Text.standardize(BankTagLayout.TAG_NAME));
  }

  private GuidedTaskTarget resolveGuidedTaskTarget() {
    Recommendation recommendation = currentRecommendation;
    if (recommendation == null) {
      clearCachedGuidedTaskTarget();
      return null;
    }
    if (recommendation == cachedGuidedTargetRecommendation
        && sameTask(currentTaskName, cachedGuidedTargetTaskName)
        && currentTaskVariant == cachedGuidedTargetVariant) {
      return cachedGuidedTaskTarget;
    }
    VariantCatalog.ResolvedTarget resolvedTarget =
        VariantCatalog.resolve(
            currentTaskName, currentTaskVariant, recommendation, config);
    if (resolvedTarget == null || !resolvedTarget.isValid()) {
      return null;
    }
    String targetTaskName = resolvedTarget.getTaskName();
    String targetLocation = resolvedTarget.getLocation();
    boolean bossEncounter = resolvedTarget.isBoss();
    RouteCatalog.RouteProfile profile =
        RouteCatalog.resolve(targetTaskName, targetLocation, bossEncounter);
    RoutePlan routePlan =
        SlayerRoutePlanCatalog.resolve(targetTaskName, targetLocation, bossEncounter);
    GuidedTaskTarget target =
        profile == null && routePlan == null
            ? null
            : new GuidedTaskTarget(
                targetTaskName, targetLocation, profile, routePlan, bossEncounter);
    cachedGuidedTargetRecommendation = recommendation;
    cachedGuidedTargetTaskName = currentTaskName;
    cachedGuidedTargetVariant = currentTaskVariant;
    cachedGuidedTaskTarget = target;
    return target;
  }

  private GuidedTaskTarget resolveGuidedTaskTargetForRouting() {
    GuidedTaskDetourSnapshot snapshot = guidedTaskDetourSnapshot;
    if (snapshot == null) {
      return resolveGuidedTaskTarget();
    }
    if (!GuideLifecycle.canResumeTaskAfterBank(
            snapshot.key, effectiveTaskRemaining())
        || !snapshot.key.belongsToAssignment(currentTaskName)) {
      clearGuidedTaskDetourSnapshot();
      return resolveGuidedTaskTarget();
    }
    return snapshot.target;
  }

  private void captureGuidedTaskDetourSnapshot() {
    if (guidedTaskDetourSnapshot != null || effectiveTaskRemaining() <= 0) {
      return;
    }
    GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
    if (target == null) {
      return;
    }
    GuideLifecycle.TaskRouteKey key =
        GuideLifecycle.taskRouteKey(
            currentTaskName, currentTaskVariant, target.taskName, target.location);
    if (key.isComplete()) {
      guidedTaskDetourSnapshot = new GuidedTaskDetourSnapshot(key, target);
    }
  }

  private void clearGuidedTaskDetourSnapshot() {
    guidedTaskDetourSnapshot = null;
  }

  private void clearCachedGuidedTaskTarget() {
    cachedGuidedTargetRecommendation = null;
    cachedGuidedTargetTaskName = "";
    cachedGuidedTargetVariant = null;
    cachedGuidedTaskTarget = null;
  }

  private static boolean sameTask(String first, String second) {
    String left = first == null ? "" : first.trim();
    String right = second == null ? "" : second.trim();
    return left.equalsIgnoreCase(right);
  }

  private boolean refreshExtraQuiverAmmoSnapshot() {
    if (client == null || client.getGameState() != GameState.LOGGED_IN) {
      return false;
    }
    boolean wearingQuiver = isWearingUsableDizana();
    int carriedQuiverItemId =
        findUsableDizanaVariantInContainer(client.getItemContainer(InventoryID.INV));
    if (!wearingQuiver && carriedQuiverItemId <= 0 && cachedBankDizanaVariantItemId <= 0) {
      boolean changed = cachedExtraQuiverAmmoItemId > 0 || cachedExtraQuiverAmmoQuantity > 0;
      clearExtraQuiverAmmoSnapshot();
      return changed;
    }
    int[] storedAmmo = readStoredExtraQuiverAmmoFromContainer();
    int varpItemId =
        QuiverAmmo.restoreSeekingIdentityFromPlaceholders(
            normalizeQuiverAmmoDisplayItemId(
                client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)),
            cachedSeekingArrowPlaceholderItemIds);
    int varpQuantity = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
    int[] widgetAmmo = readLiveExtraQuiverAmmoFromWidgets();
    QuiverAmmo.Snapshot snapshot =
        QuiverAmmo.restoreHiddenBankedSeekingAmmo(
            cachedBankDizanaVariantItemId > 0,
            QuiverAmmo.resolveSnapshot(
                wearingQuiver,
                storedAmmo == null
                    ? -1
                    : QuiverAmmo.restoreSeekingIdentityFromPlaceholders(
                        storedAmmo[0], cachedSeekingArrowPlaceholderItemIds),
                storedAmmo == null ? 0 : storedAmmo[1],
                widgetAmmo == null
                    ? -1
                    : QuiverAmmo.restoreSeekingIdentityFromPlaceholders(
                        widgetAmmo[0], cachedSeekingArrowPlaceholderItemIds),
                widgetAmmo == null ? 0 : widgetAmmo[1],
                canonicalCompatibleQuiverAmmoItemId(varpItemId),
                varpQuantity,
                cachedExtraQuiverAmmoItemId),
            cachedSeekingArrowPlaceholderItemIds);
    int itemId = snapshot.getItemId();
    int quantity = snapshot.getQuantity();
    boolean identityChanged = cachedExtraQuiverAmmoItemId != itemId;
    boolean presenceChanged = (cachedExtraQuiverAmmoQuantity > 0) != (quantity > 0);
    cachedExtraQuiverAmmoItemId = itemId;
    cachedExtraQuiverAmmoQuantity = quantity;
    return identityChanged || presenceChanged;
  }

  private int canonicalCompatibleQuiverAmmoItemId(int rawItemId) {
    int itemId = canonicalizeQuiverAmmoItemId(rawItemId);
    return isQuiverCompatibleAmmoItemId(itemId) ? itemId : -1;
  }

  private int canonicalizeQuiverAmmoItemId(int rawItemId) {
    if (rawItemId <= 0) {
      return -1;
    }
    int normalizedRaw = normalizeQuiverAmmoDisplayItemId(rawItemId);
    if (isSeekingArrowDisplayItemId(normalizedRaw)) {
      return normalizedRaw;
    }
    int itemId = rawItemId;
    try {
      if (itemManager != null) {
        itemId = itemManager.canonicalize(itemId);
      } else if (client != null) {
        ItemComposition composition = client.getItemDefinition(itemId);
        if (composition != null) {
          if (composition.getNote() != -1) {
            itemId = composition.getLinkedNoteId();
          }
          if (composition.getPlaceholderTemplateId() != -1) {
            itemId = composition.getPlaceholderId();
          }
        }
      }
    } catch (RuntimeException ignored) {
    }
    return normalizeQuiverAmmoDisplayItemId(itemId);
  }

  private boolean isQuiverCompatibleAmmoItemId(int rawItemId) {
    int itemId = canonicalizeQuiverAmmoItemId(rawItemId);
    if (itemId <= 0) {
      return false;
    }
    if (isSeekingArrowDisplayItemId(itemId)) {
      return true;
    }
    if (client == null) {
      return false;
    }
    try {
      ItemComposition composition = client.getItemDefinition(itemId);
      if (composition == null || composition.getName() == null) {
        return false;
      }
      String name =
          Text.removeTags(composition.getName())
              .toLowerCase(Locale.ENGLISH)
              .replace('’', '\'')
              .trim();
      if (name.contains("arrowtip") || name.contains("bolt tip")) {
        return false;
      }
      return name.contains("arrow") || name.contains("bolt") || name.contains("grapple");
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private int[] readLiveExtraQuiverAmmoFromWidgets() {
    int[] itemComponents = {
      InterfaceID.DizanasQuiver.AMMO_OBJ,
      InterfaceID.Wornitems.EXTRA_QUIVER_AMMO,
      InterfaceID.Equipment.EXTRA_QUIVER_AMMO,
      InterfaceID.Bankmain.EXTRA_QUIVER_AMMO
    };
    int[] best = null;
    for (int componentId : itemComponents) {
      best =
          betterQuiverWidgetCandidate(best, quiverWidgetCandidate(client.getWidget(componentId)));
    }
    return best;
  }

  private int[] readStoredExtraQuiverAmmoFromContainer() {
    ItemContainer container = client.getItemContainer(InventoryID.DIZANAS_QUIVER_AMMO);
    if (container == null) {
      return null;
    }
    int[] best = new int[] {-1, 0};
    for (Item item : container.getItems()) {
      if (item == null || item.getQuantity() <= 0) {
        continue;
      }
      int itemId = canonicalCompatibleQuiverAmmoItemId(item.getId());
      if (itemId <= 0) {
        continue;
      }
      if (best[0] <= 0
          || QuiverAmmo.infernoPreference(itemId)
              < QuiverAmmo.infernoPreference(best[0])) {
        best = new int[] {itemId, item.getQuantity()};
      }
    }
    return best;
  }

  private int[] quiverWidgetCandidate(Widget widget) {
    if (widget == null) {
      return null;
    }
    int rawItemId = widget.getItemId();
    if (rawItemId <= 0) {
      return null;
    }
    int itemId = canonicalizeQuiverAmmoItemId(rawItemId);
    if (!isQuiverCompatibleAmmoItemId(itemId)) {
      return null;
    }
    int quantity = widget.getItemQuantity();
    if (quantity <= 0) {
      quantity = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
    }
    if (quantity <= 0) {
      return null;
    }
    return new int[] {itemId, quantity};
  }

  private static int[] betterQuiverWidgetCandidate(int[] current, int[] candidate) {
    if (candidate == null) {
      return current;
    }
    if (current == null) {
      return candidate;
    }
    boolean candidateSeeking = isSeekingArrowDisplayItemId(candidate[0]);
    boolean currentSeeking = isSeekingArrowDisplayItemId(current[0]);
    return candidateSeeking && !currentSeeking ? candidate : current;
  }

  private static int normalizeQuiverAmmoDisplayItemId(int rawItemId) {
    return QuiverAmmo.normalizeSeekingArrowItemId(rawItemId);
  }

  private static boolean isSeekingArrowDisplayItemId(int itemId) {
    return QuiverAmmo.isSeekingArrow(itemId);
  }

  private boolean isWearingUsableDizana() {
    ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
    if (equipment == null) {
      return false;
    }
    Item cape = equipment.getItem(EquipmentInventorySlot.CAPE.getSlotIdx());
    return cape != null && cape.getQuantity() > 0 && isUsableDizanaItemId(cape.getId());
  }

  private static boolean isUsableDizanaItemId(int rawItemId) {
    return QuiverAmmo.isUsableDizanaVariant(rawItemId);
  }

  private static int findUsableDizanaVariantInContainer(ItemContainer container) {
    if (container == null) {
      return -1;
    }
    int bestItemId = -1;
    for (Item item : container.getItems()) {
      if (item == null || item.getQuantity() <= 0) {
        continue;
      }
      bestItemId = QuiverAmmo.preferUsableDizanaVariant(bestItemId, item.getId());
    }
    return bestItemId;
  }

  private void clearExtraQuiverAmmoSnapshot() {
    cachedExtraQuiverAmmoItemId = -1;
    cachedExtraQuiverAmmoQuantity = 0;
  }

  private boolean cacheBankContainer(ItemContainer bankContainer) {
    Widget bankItems =
        client == null ? null : client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
    return cacheBankContainer(bankContainer, bankItems != null);
  }

  private boolean cacheBankContainer(ItemContainer bankContainer, boolean confirmedBankScan) {
    if (bankContainer == null || loadoutAnalyzer == null) {
      return false;
    }
    boolean rawStateMatches = matchesCachedBankContainerState(bankContainer);
    if (shouldSkipUnchangedBankScanForRegression(bankScanned, rawStateMatches)) {
      if (confirmedBankScan && !cachedRawBankContainerConfirmed) {
        cachedRawBankContainerConfirmed = true;
        persistInfernoTravelItemSnapshot();
        persistOwnedSlayerHelmetSnapshot();
      }
      return false;
    }
    cachedRawBankContainerState = snapshotRawBankContainerState(bankContainer);
    cachedRawBankContainerConfirmed = confirmedBankScan;
    boolean previouslyScanned = bankScanned;
    int previousQuiverId = cachedBankDizanaVariantItemId;
    Map<Integer, Integer> previousBankItems = new LinkedHashMap<>(cachedBankItems);
    Set<Integer> previousSeekingIds = new LinkedHashSet<>(cachedSeekingArrowPlaceholderItemIds);
    int previousExtraAmmoId = cachedExtraQuiverAmmoItemId;
    cachedBankDizanaVariantItemId = findUsableDizanaVariantInContainer(bankContainer);
    cacheSeekingArrowPlaceholders(bankContainer);
    cachedBankItems.clear();
    cachedBankItems.putAll(loadoutAnalyzer.snapshotItems(bankContainer));
    bankScanned = true;
    if (confirmedBankScan) {
      persistInfernoTravelItemSnapshot();
      persistOwnedSlayerHelmetSnapshot();
    }
    boolean extraAmmoChanged = refreshExtraQuiverAmmoSnapshot();
    return !previouslyScanned
        || previousQuiverId != cachedBankDizanaVariantItemId
        || !previousBankItems.equals(cachedBankItems)
        || !previousSeekingIds.equals(cachedSeekingArrowPlaceholderItemIds)
        || previousExtraAmmoId != cachedExtraQuiverAmmoItemId
        || extraAmmoChanged;
  }

  private boolean matchesCachedBankContainerState(ItemContainer bankContainer) {
    Item[] items = bankContainer.getItems();
    if (items == null || cachedRawBankContainerState.length != items.length * 2) {
      return false;
    }
    for (int slot = 0; slot < items.length; slot++) {
      Item item = items[slot];
      int offset = slot * 2;
      int itemId = item == null ? -1 : item.getId();
      int quantity = item == null ? 0 : item.getQuantity();
      if (cachedRawBankContainerState[offset] != itemId
          || cachedRawBankContainerState[offset + 1] != quantity) {
        return false;
      }
    }
    return true;
  }

  private static int[] snapshotRawBankContainerState(ItemContainer bankContainer) {
    Item[] items = bankContainer.getItems();
    if (items == null || items.length == 0) {
      return new int[0];
    }
    int[] state = new int[items.length * 2];
    for (int slot = 0; slot < items.length; slot++) {
      Item item = items[slot];
      int offset = slot * 2;
      state[offset] = item == null ? -1 : item.getId();
      state[offset + 1] = item == null ? 0 : item.getQuantity();
    }
    return state;
  }

  static boolean shouldSkipUnchangedBankScanForRegression(
      boolean bankScanned, boolean rawStateMatches) {
    return bankScanned && rawStateMatches;
  }

  private void persistOwnedSlayerHelmetSnapshot() {
    Set<Integer> discovered =
        loadoutAnalyzer.ownedSlayerHelmetItemIds(
            client.getItemContainer(InventoryID.INV),
            client.getItemContainer(InventoryID.WORN),
            cachedBankItems);
    boolean snapshotChanged =
        shouldPersistSnapshotForRegression(
            profileOwnedSlayerHelmetSnapshotLoaded, profileOwnedSlayerHelmetItemIds, discovered);
    profileOwnedSlayerHelmetItemIds.clear();
    profileOwnedSlayerHelmetItemIds.addAll(discovered);
    profileOwnedSlayerHelmetSnapshotLoaded = true;
    try {
      if (configManager.getRSProfileKey() == null) {
        return;
      }
      if (!snapshotChanged) {
        return;
      }
      configManager.setRSProfileConfiguration(
          SlayerPlusConfig.GROUP, OWNED_SLAYER_HELMETS_SNAPSHOT_KEY, serializeItemIds(discovered));
      log.debug("Saved {} owned Slayer helmet variants", discovered.size());
    } catch (RuntimeException ex) {
      log.debug("Unable to save owned Slayer helmet snapshot", ex);
    }
  }

  private void loadOwnedSlayerHelmetSnapshot() {
    profileOwnedSlayerHelmetItemIds.clear();
    profileOwnedSlayerHelmetSnapshotLoaded = false;
    try {
      if (configManager.getRSProfileKey() == null) {
        return;
      }
      profileOwnedSlayerHelmetItemIds.addAll(
          parseItemIds(
              configManager.getRSProfileConfiguration(
                  SlayerPlusConfig.GROUP, OWNED_SLAYER_HELMETS_SNAPSHOT_KEY)));
      profileOwnedSlayerHelmetSnapshotLoaded = true;
      log.debug("Loaded {} owned Slayer helmet variants", profileOwnedSlayerHelmetItemIds.size());
    } catch (RuntimeException ex) {
      log.debug("Unable to load owned Slayer helmet snapshot", ex);
    }
  }

  static String serializeItemIds(Iterable<Integer> itemIds) {
    StringBuilder serialized = new StringBuilder();
    if (itemIds != null) {
      for (Integer itemId : itemIds) {
        if (itemId == null || itemId <= 0) {
          continue;
        }
        if (serialized.length() > 0) {
          serialized.append(',');
        }
        serialized.append(itemId);
      }
    }
    return serialized.toString();
  }

  static Set<Integer> parseItemIds(String serialized) {
    Set<Integer> itemIds = new LinkedHashSet<>();
    if (serialized == null || serialized.trim().isEmpty()) {
      return itemIds;
    }
    for (String token : serialized.split(",")) {
      try {
        int itemId = Integer.parseInt(token.trim());
        if (itemId > 0) {
          itemIds.add(itemId);
        }
      } catch (NumberFormatException ignored) {
        log.debug("Ignoring invalid Slayer helmet snapshot item id: {}", token);
      }
    }
    return itemIds;
  }

  private void persistInfernoTravelItemSnapshot() {
    int bestItemId = preferredInfernoTravelItemId(cachedBankItems.keySet());
    int nextItemId = Math.max(0, bestItemId);
    boolean snapshotChanged =
        shouldPersistSnapshotForRegression(
            profileInfernoTravelSnapshotLoaded, profileInfernoTravelItemId, nextItemId);
    profileInfernoTravelItemId = nextItemId;
    profileInfernoTravelSnapshotLoaded = true;
    try {
      if (configManager.getRSProfileKey() == null) {
        profileInfernoTravelSnapshotLoaded = false;
        return;
      }
      if (!snapshotChanged) {
        return;
      }
      configManager.setRSProfileConfiguration(
          SlayerPlusConfig.GROUP, INFERNO_TRAVEL_SNAPSHOT_KEY, profileInfernoTravelItemId);
      log.debug("Saved Inferno travel-item bank snapshot: {}", profileInfernoTravelItemId);
    } catch (RuntimeException ex) {
      profileInfernoTravelSnapshotLoaded = false;
      log.debug("Unable to save Inferno travel-item bank snapshot", ex);
    }
  }

  static boolean shouldPersistSnapshotForRegression(
      boolean snapshotLoaded, Object previousValue, Object nextValue) {
    return !snapshotLoaded || !java.util.Objects.equals(previousValue, nextValue);
  }

  private void loadInfernoTravelItemSnapshot() {
    profileInfernoTravelItemId = -1;
    profileInfernoTravelSnapshotLoaded = false;
    try {
      if (configManager.getRSProfileKey() == null) {
        return;
      }
      String stored =
          configManager.getRSProfileConfiguration(
              SlayerPlusConfig.GROUP, INFERNO_TRAVEL_SNAPSHOT_KEY);
      if (stored != null) {
        int storedItemId = Integer.parseInt(stored.trim());
        profileInfernoTravelItemId =
            storedItemId == 0 || isInfernoPreparationTravelItemId(storedItemId) ? storedItemId : 0;
        profileInfernoTravelSnapshotLoaded = true;
        log.debug("Loaded Inferno travel-item bank snapshot: {}", profileInfernoTravelItemId);
        return;
      }
      int migratedItemId = infernoTravelItemFromSavedLayout();
      if (migratedItemId > 0) {
        profileInfernoTravelItemId = migratedItemId;
        configManager.setRSProfileConfiguration(
            SlayerPlusConfig.GROUP, INFERNO_TRAVEL_SNAPSHOT_KEY, migratedItemId);
        log.debug(
            "Migrated Inferno travel-item snapshot from SlayerPlus layout: {}", migratedItemId);
      } else {
        profileInfernoTravelItemId = 0;
        configManager.setRSProfileConfiguration(
            SlayerPlusConfig.GROUP, INFERNO_TRAVEL_SNAPSHOT_KEY, 0);
      }
      profileInfernoTravelSnapshotLoaded = true;
    } catch (NumberFormatException ex) {
      profileInfernoTravelItemId = 0;
      profileInfernoTravelSnapshotLoaded = true;
      log.debug("Ignoring invalid Inferno travel-item bank snapshot", ex);
    } catch (RuntimeException ex) {
      log.debug("Unable to load Inferno travel-item bank snapshot", ex);
    }
  }

  private int infernoTravelItemFromSavedLayout() {
    if (layoutManager == null) {
      return -1;
    }
    Layout layout = layoutManager.loadLayout(BankTagLayout.TAG_NAME);
    if (layout == null || layout.getLayout() == null) {
      return -1;
    }
    Set<Integer> layoutItemIds = new LinkedHashSet<>();
    for (int itemId : layout.getLayout()) {
      if (itemId > 0) {
        layoutItemIds.add(itemId);
      }
    }
    return preferredInfernoTravelItemId(layoutItemIds);
  }

  static int preferredInfernoTravelItemId(Iterable<Integer> candidateItemIds) {
    if (candidateItemIds == null) {
      return -1;
    }
    Set<Integer> candidates = new HashSet<>();
    for (Integer itemId : candidateItemIds) {
      if (itemId != null) {
        candidates.add(itemId);
      }
    }
    for (int supportedItemId : INFERNO_PREPARATION_TRAVEL_ITEM_IDS) {
      if (candidates.contains(supportedItemId)) {
        return supportedItemId;
      }
    }
    return -1;
  }

  private static boolean isInfernoPreparationTravelItemId(int itemId) {
    for (int supportedItemId : INFERNO_PREPARATION_TRAVEL_ITEM_IDS) {
      if (itemId == supportedItemId) {
        return true;
      }
    }
    return false;
  }

  private void cacheSeekingArrowPlaceholders(ItemContainer bankContainer) {
    cachedSeekingArrowPlaceholderItemIds.clear();
    if (bankContainer == null || itemManager == null) {
      cacheSeekingArrowBankTagIdentityHints();
      return;
    }
    for (Item item : bankContainer.getItems()) {
      if (item == null || item.getId() <= 0) {
        continue;
      }
      Integer cachedClassification = seekingPlaceholderClassificationCache.get(item.getId());
      if (cachedClassification != null) {
        if (cachedClassification > 0) {
          cachedSeekingArrowPlaceholderItemIds.add(cachedClassification);
        }
        continue;
      }
      int classification = -1;
      try {
        ItemComposition composition = itemManager.getItemComposition(item.getId());
        if (composition != null
            && composition.getPlaceholderTemplateId() >= 0
            && composition.getPlaceholderId() >= 0) {
          int canonicalItemId =
              normalizeQuiverAmmoDisplayItemId(itemManager.canonicalize(item.getId()));
          if (isSeekingArrowDisplayItemId(canonicalItemId)) {
            classification = canonicalItemId;
          }
        }
      } catch (RuntimeException ignored) {
      }
      seekingPlaceholderClassificationCache.put(item.getId(), classification);
      if (classification > 0) {
        cachedSeekingArrowPlaceholderItemIds.add(classification);
      }
    }
    cacheSeekingArrowBankTagIdentityHints();
  }

  private void cacheSeekingArrowBankTagIdentityHints() {
    if (configManager == null) {
      return;
    }
    String prefix = BankTagsPlugin.CONFIG_GROUP + ".item_";
    for (String key : configManager.getConfigurationKeys(prefix)) {
      if (key == null || !key.startsWith(prefix)) {
        continue;
      }
      try {
        int taggedItemId = Integer.parseInt(key.substring(prefix.length()));
        if (taggedItemId <= 0) {
          continue;
        }
        int normalizedItemId = normalizeQuiverAmmoDisplayItemId(taggedItemId);
        if (isSeekingArrowDisplayItemId(normalizedItemId)) {
          cachedSeekingArrowPlaceholderItemIds.add(normalizedItemId);
        }
      } catch (NumberFormatException ignored) {
      }
    }
  }

  private TravelInstruction findTravelInstructionFromTransportInfo(Object rawDisplayInfos) {
    Iterable<?> displayInfos;
    if (rawDisplayInfos instanceof Iterable<?>) {
      displayInfos = (Iterable<?>) rawDisplayInfos;
    } else if (rawDisplayInfos instanceof Object[]) {
      displayInfos = Arrays.asList((Object[]) rawDisplayInfos);
    } else if (rawDisplayInfos instanceof String) {
      displayInfos = java.util.Collections.singletonList(rawDisplayInfos);
    } else {
      return null;
    }
    for (Object value : displayInfos) {
      if (!(value instanceof String)) {
        continue;
      }
      String displayInfo = cleanTransportText((String) value);
      if (displayInfo.isEmpty()) {
        continue;
      }
      int separator = displayInfo.indexOf(':');
      String family = separator >= 0 ? displayInfo.substring(0, separator).trim() : displayInfo;
      String destination = separator >= 0 ? displayInfo.substring(separator + 1).trim() : "";
      if (!family.isEmpty()) {
        return new TravelInstruction(family, destination);
      }
    }
    return null;
  }

  private static String cleanTransportText(String value) {
    if (value == null) {
      return "";
    }
    return Text.removeTags(value.replaceAll("(?i)<br\\s*/?>", " ")).trim();
  }

  private static String travelInstructionDisplay(String family, String destination) {
    String safeFamily = family == null ? "" : family.trim();
    String safeDestination = destination == null ? "" : destination.trim();
    if (safeFamily.isEmpty()) {
      return safeDestination;
    }
    return safeDestination.isEmpty() ? safeFamily : safeFamily + " \u2192 " + safeDestination;
  }

  private TravelRouteMatch findTravelItemFromTransportInfo(
      Map<String, Object> data, boolean carriedOnly) {
    if (data == null) {
      return null;
    }
    List<?> displayInfos = postedValues(data.get("displayInfo"));
    if (displayInfos.isEmpty()) {
      return null;
    }
    List<?> origins = postedValues(data.get("origin"));
    List<?> destinations = postedValues(data.get("destination"));
    List<TravelRouteCandidate> edgeCandidates = new ArrayList<>();
    Object activeOrigin = null;
    Object activeDestination = null;
    boolean activeEdge = false;
    for (int index = 0; index < displayInfos.size(); index++) {
      Object value = displayInfos.get(index);
      if (!(value instanceof String)) {
        continue;
      }
      String displayInfo = cleanTransportText((String) value);
      if (displayInfo.isEmpty()) {
        continue;
      }
      Object origin = index < origins.size() ? origins.get(index) : null;
      Object edgeDestination = index < destinations.size() ? destinations.get(index) : null;
      if (activeEdge
          && !sameTransportEdge(activeOrigin, activeDestination, origin, edgeDestination)) {
        TravelRouteMatch selected = selectTravelCandidate(edgeCandidates, carriedOnly);
        if (selected != null) {
          return selected;
        }
        edgeCandidates.clear();
        activeEdge = false;
      }
      int separator = displayInfo.indexOf(':');
      String family = separator >= 0 ? displayInfo.substring(0, separator).trim() : displayInfo;
      String destination = separator >= 0 ? displayInfo.substring(separator + 1).trim() : "";
      TravelItemMatch item =
          carriedOnly
              ? findCarriedTravelItemForTransport(family)
              : findOwnedTravelItemForTransport(family);
      if (item == null) {
        continue;
      }
      if (!activeEdge) {
        activeOrigin = origin;
        activeDestination = edgeDestination;
        activeEdge = true;
      }
      edgeCandidates.add(new TravelRouteCandidate(family, item, destination));
    }
    return selectTravelCandidate(edgeCandidates, carriedOnly);
  }

  private static List<?> postedValues(Object value) {
    if (value instanceof List<?>) {
      return (List<?>) value;
    }
    if (value instanceof Object[]) {
      return Arrays.asList((Object[]) value);
    }
    if (value == null) {
      return java.util.Collections.emptyList();
    }
    return java.util.Collections.singletonList(value);
  }

  private static boolean sameTransportEdge(
      Object firstOrigin, Object firstDestination, Object secondOrigin, Object secondDestination) {
    if (firstOrigin == null
        && firstDestination == null
        && secondOrigin == null
        && secondDestination == null) {
      return true;
    }
    return samePostedValue(firstOrigin, secondOrigin)
        && samePostedValue(firstDestination, secondDestination);
  }

  private static boolean samePostedValue(Object first, Object second) {
    return first == second || (first != null && first.equals(second));
  }

  private boolean isTravelCandidateAllowedForTarget(
      GuidedTaskTarget target, TravelRouteCandidate candidate) {
    return target != null
        && candidate != null
        && achievementDiaries.allowsTravelItem(candidate.family)
        && TravelRoutes.allowsLiveCandidate(
            target.location, candidate.family, candidate.destination);
  }

  private TravelRouteMatch selectTravelCandidate(
      List<TravelRouteCandidate> candidates, boolean masterReturnRoute) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
    TravelRouteCandidate best = null;
    int bestPreference = Integer.MIN_VALUE;
    int bestItemScore = Integer.MIN_VALUE;
    String bestStableKey = "";
    Set<String> equivalentItemFamilies = new LinkedHashSet<>();
    for (TravelRouteCandidate candidate : candidates) {
      if (candidate == null
          || candidate.item == null
          || shouldRejectTravelItemForSpellbookRouteForRegression(
              guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_SPELLBOOK, candidate.family)
          || (!masterReturnRoute && !isTravelCandidateAllowedForTarget(target, candidate))) {
        continue;
      }
      equivalentItemFamilies.add(candidate.family);
      int preference =
          TravelRoutes.liveCandidatePreference(
              target == null ? "" : target.location, candidate.family);
      int itemScore = candidate.item.score;
      String stableKey =
          normalizeTravelName(candidate.family)
              + "|"
              + normalizeTravelName(candidate.item.displayName)
              + "|"
              + candidate.item.itemId;
      if (best == null
          || preference > bestPreference
          || (preference == bestPreference && itemScore > bestItemScore)
          || (preference == bestPreference
              && itemScore == bestItemScore
              && stableKey.compareTo(bestStableKey) < 0)) {
        best = candidate;
        bestPreference = preference;
        bestItemScore = itemScore;
        bestStableKey = stableKey;
      }
    }
    return best == null
        ? null
        : new TravelRouteMatch(best.item, best.destination, equivalentItemFamilies);
  }

  static boolean shouldRejectTravelItemForSpellbookRouteForRegression(
      boolean spellbookRoute, String itemFamily) {
    return normalizeTravelName(itemFamily).contains("hallowed crystal shard");
  }

  private static String travelItemFamilyName(String value) {
    return normalizeTravelName(value).replaceFirst("\\s+[a-z]?\\d+$", "").trim();
  }

  private TravelItemMatch findOwnedTravelItemForTransport(String family) {
    String normalizedFamily = normalizeTravelName(family);
    if (normalizedFamily.isEmpty()) {
      return null;
    }
    if (normalizedFamily.equals("max cape") || normalizedFamily.contains("max cape teleport")) {
      TravelItemMatch cape = findOwnedTravelItem("max cape");
      return cape != null && travelItemFamilyName(cape.displayName).equals("max cape")
          ? cape
          : null;
    }
    if (normalizedFamily.contains("construction cape")
        || normalizedFamily.contains("construct cape")) {
      return findOwnedTravelItem("construct cape");
    }
    return findOwnedTravelItem(family);
  }

  private TravelItemMatch findCarriedTravelItemForTransport(String family) {
    String normalizedFamily = normalizeTravelName(family);
    if (normalizedFamily.isEmpty()) {
      return null;
    }
    if (normalizedFamily.equals("max cape") || normalizedFamily.contains("max cape teleport")) {
      TravelItemMatch cape = findCarriedTravelItem("max cape");
      return cape != null && travelItemFamilyName(cape.displayName).equals("max cape")
          ? cape
          : null;
    }
    if (normalizedFamily.contains("construction cape")
        || normalizedFamily.contains("construct cape")) {
      return findCarriedTravelItem("construct cape");
    }
    return findCarriedTravelItem(family);
  }

  private TravelChoice currentTravelSelection() {
    String expectedIdentity = activeTravelConsumerIdentity();
    return expectedIdentity == null || expectedIdentity.isEmpty()
        ? TravelChoice.unresolved("", travelCoordinator.getGeneration())
        : travelCoordinator.currentFor(expectedIdentity);
  }

  private TravelChoice currentTravelSelectionForHighlight() {
    if (!shouldExposeTravelHighlightForRegression(
        guidedSessionActive, guidedSessionPhase == GuidedSessionPhase.TASKING)) {
      return TravelChoice.unresolved("", travelCoordinator.getGeneration());
    }
    return currentTravelSelection();
  }

  static boolean shouldExposeTravelHighlightForRegression(
      boolean sessionActive, boolean taskAreaConfirmed) {
    return !sessionActive || !taskAreaConfirmed;
  }

  private String activeTravelConsumerIdentity() {
    if (guidedSessionActive
        && (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER
            || guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK
            || guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_TASK)
        && guidedTravelRequestRouteIdentity != null
        && !guidedTravelRequestRouteIdentity.isEmpty()
        && travelCoordinator.ownsCurrentRoute(guidedTravelRequestRouteIdentity)) {
      return guidedTravelRequestRouteIdentity;
    }
    return lastResolvedRoutingProfileIdentity;
  }

  private void applyTravelRouteMatch(TravelRouteMatch match, String identity) {
    if (match == null || match.item == null) {
      return;
    }
    if (identity.isEmpty() || !travelCoordinator.ownsCurrentRoute(identity)) {
      return;
    }
    String menuDestination =
        SlayerTeleportRouteRegistry.menuDestination(match.item.displayName, match.destination);
    TravelChoice selection =
        travelCoordinator.resolveItem(
            identity,
            match.item.itemId,
            match.item.displayName,
            menuDestination,
            match.equivalentItemFamilies);
    boolean infernoPrepRoute =
        guidedSessionActive
            && guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK
            && identity.equals(guidedTravelRequestRouteIdentity);
    String slotText =
        infernoPrepRoute
            ? "the staging strip above the 4 x 7 until the Inferno bank is opened"
            : "Bank Tag slot 1";
    String note =
        selection.getDestination().isEmpty()
            ? "Using this owned item for Shortest Path's selected route and including it when the"
                  + " Bank Tag Layout is generated. The item is outlined in green."
            : "Using this owned item for Shortest Path's selected route and including it in "
                + slotText
                + ". The item and exact live teleport menu path are highlighted in green.";
    publishTravelSelection(selection, note);
  }

  private void applyProvisionalTravelRouteMatch(TravelRouteMatch match, GuidedTaskTarget target) {
    if (target == null) {
      return;
    }
    applyProvisionalTravelRouteMatch(match, routeIdentity(target));
  }

  private void applyProvisionalTravelRouteMatch(TravelRouteMatch match, String identity) {
    if (match == null || match.item == null) {
      return;
    }
    if (identity == null || identity.isEmpty() || !travelCoordinator.ownsCurrentRoute(identity)) {
      return;
    }
    String menuDestination =
        SlayerTeleportRouteRegistry.menuDestination(match.item.displayName, match.destination);
    TravelChoice selection =
        travelCoordinator.seedProvisionalItem(
            identity,
            match.item.itemId,
            match.item.displayName,
            menuDestination,
            match.equivalentItemFamilies);
    if (!selection.isProvisional()) {
      return;
    }
    publishTravelSelection(
        selection,
        "Temporary route-specific travel item while Shortest Path finishes calculating. A live"
            + " Shortest Path selection always replaces it.");
  }

  private TravelRouteMatch findAuthoredRouteTravelItem(GuidedTaskTarget target) {
    return target == null ? null : findAuthoredRouteTravelItem(target.location);
  }

  private TravelRouteMatch findAuthoredRouteTravelItem(String location) {
    if (location == null || location.trim().isEmpty()) {
      return null;
    }
    for (TravelRoutes.Option option : TravelRoutes.fallbacksFor(location)) {
      if (shouldRejectTravelItemForSpellbookRouteForRegression(false, option.getItemFamily())) {
        continue;
      }
      if ("ynysdail cavern".equals(normalizeTravelName(location))
          && !hasBuiltYnysdailRowboat()
          && !normalizeTravelName(option.getItemFamily()).contains("sailor s amulet")) {
        continue;
      }
      if (!achievementDiaries.allowsTravelItem(option.getItemFamily())) {
        continue;
      }
      TravelItemMatch item = findOwnedTravelItem(option.getItemFamily());
      if (item != null) {
        Set<String> families = new LinkedHashSet<>();
        families.add(option.getItemFamily());
        return new TravelRouteMatch(item, option.getDestination(), families);
      }
    }
    return null;
  }

  private TravelRouteMatch findInfernoPreparationTravelItem() {
    for (int index = 0; index < INFERNO_PREPARATION_TRAVEL_ITEM_IDS.length; index++) {
      int itemId = INFERNO_PREPARATION_TRAVEL_ITEM_IDS[index];
      if (!isTravelItemStillOwned(itemId)) {
        continue;
      }
      try {
        ItemComposition composition = itemManager.getItemComposition(itemId);
        if (composition == null
            || composition.getName() == null
            || composition.getName().trim().isEmpty()) {
          continue;
        }
        log.debug("Inferno preparation selected currently observed Ghommal item {}", itemId);
        return infernoPreparationTravelMatch(itemId, index, composition);
      } catch (RuntimeException ignored) {
      }
    }
    if (!bankScanned && !profileInfernoTravelSnapshotLoaded) {
      loadInfernoTravelItemSnapshot();
    }
    if (!bankScanned && profileInfernoTravelItemId > 0) {
      for (int index = 0; index < INFERNO_PREPARATION_TRAVEL_ITEM_IDS.length; index++) {
        int itemId = INFERNO_PREPARATION_TRAVEL_ITEM_IDS[index];
        if (itemId != profileInfernoTravelItemId) {
          continue;
        }
        try {
          ItemComposition composition = itemManager.getItemComposition(itemId);
          if (composition == null
              || composition.getName() == null
              || composition.getName().trim().isEmpty()) {
            break;
          }
          log.debug("Inferno preparation selected profile bank-snapshot Ghommal item {}", itemId);
          return infernoPreparationTravelMatch(itemId, index, composition);
        } catch (RuntimeException ex) {
          log.debug("Unable to resolve remembered Inferno travel item {}", itemId, ex);
        }
        break;
      }
    }
    TravelRouteMatch compatible = findAuthoredRouteTravelItem(INFERNO_PREP_TRAVEL_LOCATION);
    if (compatible == null) {
      log.debug("Inferno preparation found no Ghommal item; selecting the Hot vent branch");
    }
    return compatible;
  }

  private static TravelRouteMatch infernoPreparationTravelMatch(
      int itemId, int preferenceIndex, ItemComposition composition) {
    String displayName = composition.getName().trim();
    return new TravelRouteMatch(
        new TravelItemMatch(itemId, displayName, 10_000 - preferenceIndex),
        "Mor Ul Rek",
        java.util.Collections.singleton(displayName));
  }

  private TravelItemMatch findOwnedTravelItem(String family) {
    String normalizedFamily = normalizeTravelName(family);
    if (normalizedFamily.isEmpty()) {
      return null;
    }
    TravelItemMatch best = null;
    for (Map.Entry<Integer, Integer> entry : cachedBankItems.entrySet()) {
      if (entry.getKey() == null
          || entry.getKey() <= 0
          || entry.getValue() == null
          || entry.getValue() <= 0) {
        continue;
      }
      best =
          betterTravelItem(
              best, matchTravelItem(entry.getKey(), normalizedFamily, TRAVEL_BANK_SOURCE_SCORE));
    }
    best =
        findOwnedTravelItemInContainer(
            client.getItemContainer(InventoryID.INV),
            normalizedFamily,
            TRAVEL_INVENTORY_SOURCE_SCORE,
            best);
    best =
        findOwnedTravelItemInContainer(
            client.getItemContainer(InventoryID.WORN),
            normalizedFamily,
            TRAVEL_WORN_SOURCE_SCORE,
            best);
    return best;
  }

  private TravelItemMatch findBankTravelItem(String family) {
    String normalizedFamily = normalizeTravelName(family);
    if (normalizedFamily.isEmpty()) {
      return null;
    }
    TravelItemMatch best = null;
    for (Map.Entry<Integer, Integer> entry : cachedBankItems.entrySet()) {
      if (entry.getKey() == null
          || entry.getKey() <= 0
          || entry.getValue() == null
          || entry.getValue() <= 0) {
        continue;
      }
      best =
          betterTravelItem(
              best, matchTravelItem(entry.getKey(), normalizedFamily, TRAVEL_BANK_SOURCE_SCORE));
    }
    return best;
  }

  static boolean carriedTravelOutranksBankForRegression() {
    return TRAVEL_INVENTORY_SOURCE_SCORE > TRAVEL_BANK_SOURCE_SCORE
        && TRAVEL_WORN_SOURCE_SCORE > TRAVEL_BANK_SOURCE_SCORE;
  }

  private TravelItemMatch findCarriedTravelItem(String family) {
    String normalizedFamily = normalizeTravelName(family);
    if (normalizedFamily.isEmpty()) {
      return null;
    }
    TravelItemMatch best =
        findOwnedTravelItemInContainer(
            client.getItemContainer(InventoryID.INV), normalizedFamily, 2000, null);
    best =
        findOwnedTravelItemInContainer(
            client.getItemContainer(InventoryID.WORN), normalizedFamily, 1900, best);
    return best;
  }

  private TravelItemMatch findOwnedTravelItemInContainer(
      ItemContainer container,
      String normalizedFamily,
      int sourceBonus,
      TravelItemMatch currentBest) {
    TravelItemMatch best = currentBest;
    if (container == null) {
      return best;
    }
    for (Item item : container.getItems()) {
      if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
        continue;
      }
      best = betterTravelItem(best, matchTravelItem(item.getId(), normalizedFamily, sourceBonus));
    }
    return best;
  }

  private TravelItemMatch matchTravelItem(int itemId, String normalizedFamily, int sourceBonus) {
    if (itemManager == null || itemId <= 0) {
      return null;
    }
    ItemComposition composition;
    try {
      composition = itemManager.getItemComposition(itemId);
    } catch (RuntimeException ignored) {
      return null;
    }
    if (composition == null
        || composition.getName() == null
        || composition.getName().trim().isEmpty()
        || "null".equalsIgnoreCase(composition.getName().trim())) {
      return null;
    }
    String displayName = composition.getName().trim();
    String normalizedItem = normalizeTravelName(displayName);
    if (!SlayerTravelItemPolicy.isUsableDisplayName(displayName)) {
      return null;
    }
    String withoutTablet =
        normalizedFamily.endsWith(" tablet")
            ? normalizedFamily.substring(0, normalizedFamily.length() - " tablet".length()).trim()
            : normalizedFamily;
    int quality = travelNameMatchQuality(normalizedItem, normalizedFamily, withoutTablet);
    if (quality <= 0) {
      return null;
    }
    return new TravelItemMatch(
        itemId, displayName, sourceBonus + quality + largestNumber(normalizedItem));
  }

  private static int travelNameMatchQuality(
      String itemName, String family, String alternateFamily) {
    int quality = travelNameMatchQuality(itemName, family);
    if (!alternateFamily.equals(family)) {
      quality = Math.max(quality, travelNameMatchQuality(itemName, alternateFamily));
    }
    return quality;
  }

  private static int travelNameMatchQuality(String itemName, String family) {
    if (itemName.isEmpty() || family.isEmpty()) {
      return 0;
    }
    if (itemName.equals(family)) {
      return 1000;
    }
    if (itemName.startsWith(family + " ")) {
      return 900;
    }
    String chargeFreeItem = itemName.replaceFirst("\\s+[a-z]?\\d+$", "").trim();
    if (chargeFreeItem.equals(family)) {
      return 850;
    }
    String equivalentItem = stripTravelVariantModifiers(chargeFreeItem);
    String equivalentFamily = stripTravelVariantModifiers(family);
    if (!equivalentItem.isEmpty() && equivalentItem.equals(equivalentFamily)) {
      if (containsTravelWord(chargeFreeItem, "eternal") && containsTravelWord(family, "eternal")) {
        return 875;
      }
      return 825;
    }
    if (family.startsWith(chargeFreeItem + " ") && chargeFreeItem.length() >= 8) {
      return 700;
    }
    return 0;
  }

  static int travelNameMatchQualityForRegression(String itemName, String family) {
    return travelNameMatchQuality(normalizeTravelName(itemName), normalizeTravelName(family));
  }

  private static boolean containsTravelWord(String value, String word) {
    return (" " + value + " ").contains(" " + word + " ");
  }

  private static String stripTravelVariantModifiers(String value) {
    if (value == null || value.trim().isEmpty()) {
      return "";
    }
    return value.replaceAll("\\beternal\\b", " ").trim().replaceAll("\\s+", " ");
  }

  private static TravelItemMatch betterTravelItem(TravelItemMatch first, TravelItemMatch second) {
    if (second == null) {
      return first;
    }
    return first == null || second.score > first.score ? second : first;
  }

  private static int largestNumber(String value) {
    int largest = 0;
    int current = 0;
    boolean reading = false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isDigit(character)) {
        reading = true;
        current = current * 10 + character - '0';
      } else if (reading) {
        largest = Math.max(largest, current);
        current = 0;
        reading = false;
      }
    }
    return reading ? Math.max(largest, current) : largest;
  }

  private static String normalizeTravelName(String value) {
    if (value == null) {
      return "";
    }
    return value
        .toLowerCase(Locale.ENGLISH)
        .replace('’', '\'')
        .replaceAll("[^a-z0-9]+", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  private boolean isTravelItemStillOwned(int itemId) {
    if (itemId <= 0) {
      return false;
    }
    Integer bankQuantity = cachedBankItems.get(itemId);
    if (bankQuantity != null && bankQuantity > 0) {
      return true;
    }
    return containerHasItem(client.getItemContainer(InventoryID.INV), itemId)
        || containerHasItem(client.getItemContainer(InventoryID.WORN), itemId);
  }

  private boolean isInfernoStagingTravelItemKnownOwned(int itemId, boolean infernoStaging) {
    return isTravelItemStillOwned(itemId)
        || shouldUseRememberedInfernoTravelItem(
            infernoStaging,
            bankScanned,
            profileInfernoTravelSnapshotLoaded,
            profileInfernoTravelItemId,
            itemId);
  }

  static boolean shouldUseRememberedInfernoTravelItem(
      boolean infernoStaging,
      boolean bankScanned,
      boolean snapshotLoaded,
      int snapshotItemId,
      int selectedItemId) {
    return infernoStaging
        && !bankScanned
        && snapshotLoaded
        && snapshotItemId > 0
        && snapshotItemId == selectedItemId;
  }

  private static boolean containerHasItem(ItemContainer container, int itemId) {
    if (container == null || itemId <= 0) {
      return false;
    }
    for (Item item : container.getItems()) {
      if (item != null && item.getId() == itemId && item.getQuantity() > 0) {
        return true;
      }
    }
    return false;
  }

  PreparationCatalog.PreparationPlan getCurrentPreparationForOverlay() {
    return currentPreparation;
  }

  boolean isGuidedSessionActiveForOverlay() {
    return guidedSessionActive;
  }

  String getSelectedTravelItemNameForOverlay() {
    return currentTravelSelection().getItemName();
  }

  String getSelectedTravelDestinationForOverlay() {
    return currentTravelSelection().getDestination();
  }

  int getSelectedTravelItemIdForOverlay() {
    if (!guidedSessionActive || guidedSessionPhase == GuidedSessionPhase.TASKING) {
      return -1;
    }
    TravelChoice selection = currentTravelSelection();
    return selection.hasPhysicalItem() ? selection.getItemId() : -1;
  }

  private void publishTravelSelection(TravelChoice selection, String note) {
    if (selection == null || !selection.isStructurallyValid()) {
      return;
    }
    if (selection.hasPhysicalItem()) {
      pinnedTravelHighlightGeneration = selection.getGeneration();
      pinnedTravelHighlightRouteIdentity = selection.getRouteIdentity();
    } else {
      clearPinnedTravelHighlight();
    }
    String selectionIdentity = selection.travelIdentity();
    if (selectionIdentity.equals(lastPublishedTravelSelectionIdentity)) {
      return;
    }
    lastPublishedTravelSelectionIdentity = selectionIdentity;
    restoreGuidedTeleportWidgetHighlights();
    clearTeleportHighlightCaptures();
    String display = travelInstructionDisplay(selection.getItemName(), selection.getDestination());
    showGuidedTravelSelection(selection, display, note);
    if (selection.hasPhysicalItem()) {
      queueTeleportMenuHighlight();
    }
  }

  private void showGuidedTravelSelection(
      TravelChoice selection, String display, String note) {
    SlayerPlusPanel currentPanel = panel;
    if (currentPanel == null || selection == null) {
      return;
    }
    String selectionIdentity = selection.travelIdentity();
    SwingUtilities.invokeLater(
        () -> {
          if (!selectionIdentity.equals(currentTravelSelection().travelIdentity())) {
            return;
          }
          currentPanel.showTravelRecommendation(display, note);
        });
  }

  private void showGuidedTravelRecommendation(String itemName, String note) {
    SlayerPlusPanel currentPanel = panel;
    if (currentPanel == null) {
      return;
    }
    SwingUtilities.invokeLater(() -> currentPanel.showTravelRecommendation(itemName, note));
  }

  private void clearGuidedTravelRecommendation() {
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

  private void clearPinnedTravelHighlight() {
    pinnedTravelHighlightGeneration = -1L;
    pinnedTravelHighlightRouteIdentity = "";
  }

  private void createRecommendedBankTag() {
    if (panel == null || bankTagLayoutService == null) {
      return;
    }
    if (loadoutAnalyzer.rerollRandomSlayerHelmet()) {
      refreshSlayerData();
    }
    createRecommendedBankTagNow(true);
    startOrRefreshGuidedRouteAfterBankTag();
  }

  private void startOrRefreshGuidedRouteAfterBankTag() {
    if (!config.enableShortestPathRouting() || effectiveTaskRemaining() <= 0) {
      return;
    }
    if (!guidedSessionActive) {
      guidedSessionActive = true;
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      taskAreaAnchor = null;
      taskArrivalGraceTicks = 0;
      taskAreaExitTicks = 0;
      if (currentMasterId > 0) {
        lastUsedMasterId = currentMasterId;
      }
    }
    boolean bankAware =
        shouldUseBankAwareProfileReroute(
            bankScanned, client.getItemContainer(InventoryID.BANK) != null);
    routeToTaskForGuidedSession(bankAware, "Bank tag ready");
  }

  private void createRecommendedBankTagNow() {
    createRecommendedBankTagNow(false);
  }

  private void createRecommendedBankTagNow(boolean forceWrite) {
    SlayerPlusPanel currentPanel = panel;
    if (currentPanel == null || bankTagLayoutService == null) {
      return;
    }
    cacheSeekingArrowPlaceholders(client.getItemContainer(InventoryID.BANK));
    if (refreshExtraQuiverAmmoSnapshot()) {
      refreshSlayerData();
    }
    logQuiverBankTagDiagnostic();
    GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
    boolean infernoTarget = isInfernoPreparationTarget(target);
    boolean infernoStaging = infernoTarget && !infernoPreparationBankReady;
    boolean infernoFinal = infernoTarget && infernoPreparationBankReady;
    String expectedRouteIdentity =
        infernoStaging ? infernoPreparationBankRouteIdentity(target) : routeIdentity(target);
    TravelChoice selection = travelCoordinator.selectionFor(expectedRouteIdentity);
    boolean shortestPathOwnsTravel = target != null && !infernoFinal;
    if (shortestPathOwnsTravel) {
      selection = repairAuthoritativeTravelItemVariant(selection, expectedRouteIdentity);
    }
    int travelItemId =
        shortestPathOwnsTravel
                && selection != null
                && selection.belongsTo(expectedRouteIdentity)
                && selection.hasPhysicalItem()
                && isInfernoStagingTravelItemKnownOwned(selection.getItemId(), infernoStaging)
            ? selection.getItemId()
            : -1;
    KitPlan bankTagPlan;
    if (!shouldAddPersistentReturnTeleports(infernoTarget)) {
      bankTagPlan = currentLoadout;
    } else {
      bankTagPlan =
          withOwnedPointBoostTravelKit(
              withOwnedMasterReturnTeleport(
                  withOwnedBankTeleport(currentLoadout, travelItemId), travelItemId),
              travelItemId);
    }
    if (shortestPathOwnsTravel && travelItemId <= 0 && !selection.isResolved()) {
      showGuidedTravelRecommendation(
          "",
          infernoStaging
              ? "Waiting for the exact Shortest Path transport to the Inferno preparation bank; the"
                    + " staging item will appear above the 4 x 7 without consuming a combat slot."
              : "Waiting for the exact Shortest Path transport for this route; SlayerPlus will not"
                    + " insert a competing teleport.");
    }
    String bankTagState =
        bankTagStateFingerprint(
            bankTagPlan,
            selection,
            travelItemId,
            currentPreparation.getBankTagItemIds(),
            shortestPathOwnsTravel,
            infernoStaging ? INFERNO_STAGING_TRAVEL_SLOT_INDEX : 0,
            !infernoTarget,
            cachedExtraQuiverAmmoItemId);
    boolean sameWrittenState = bankTagState.equals(lastWrittenBankTagState);
    if (!forceWrite && sameWrittenState) {
      return;
    }
    boolean reopenedWithoutRewrite =
        forceWrite && sameWrittenState && bankTagLayoutService.reopenLastSavedLayoutIfUnchanged();
    BankTagLayout.Result result =
        reopenedWithoutRewrite
            ? BankTagLayout.Result.success(
                "Opened the existing SlayerPlus Current layout without rebuilding it.")
            : bankTagLayoutService.createOrUpdate(
                bankTagPlan,
                selection,
                travelItemId,
                currentPreparation.getBankTagItemIds(),
                shortestPathOwnsTravel,
                infernoStaging ? INFERNO_STAGING_TRAVEL_SLOT_INDEX : 0,
                !infernoTarget,
                cachedExtraQuiverAmmoItemId);
    if (result.isSuccess()) {
      bankTagLayoutCreated = true;
      lastWrittenBankTagState = bankTagState;
    }
    SwingUtilities.invokeLater(() -> currentPanel.showBankTagStatus(result.getMessage()));
  }

  private KitPlan withOwnedMasterReturnTeleport(
      KitPlan plan, int taskTravelItemId) {
    if (plan == null) {
      return null;
    }
    MasterRoutes.MasterRoute master =
        MasterRoutes.find(getPostTaskReturnMasterId());
    if (master == null) {
      return plan;
    }
    for (String family : master.getReturnItemFamilies()) {
      if (!achievementDiaries.allowsTravelItem(family)) {
        continue;
      }
      TravelItemMatch match = findOwnedTravelItem(family);
      if (match == null) {
        continue;
      }
      if (match.itemId == taskTravelItemId) {
        return plan;
      }
      return appendMasterReturnTeleportForRegression(plan, match.itemId, match.displayName);
    }
    return plan;
  }

  static boolean shouldAddPersistentReturnTeleports(boolean infernoTarget) {
    return !infernoTarget;
  }

  private KitPlan withOwnedBankTeleport(KitPlan plan, int taskTravelItemId) {
    if (plan == null) {
      return null;
    }
    String[] bankFamilies = {
      "max cape", "crafting cape",
      "amulet of eternal glory", "ring of dueling",
      "amulet of glory"
    };
    for (String family : bankFamilies) {
      if (!achievementDiaries.allowsTravelItem(family)) {
        continue;
      }
      TravelItemMatch match = findOwnedTravelItem(family);
      if (match == null) {
        continue;
      }
      if (match.itemId == taskTravelItemId) {
        return plan;
      }
      return appendMasterReturnTeleportForRegression(plan, match.itemId, match.displayName);
    }
    return plan;
  }

  private KitPlan withOwnedPointBoostTravelKit(
      KitPlan plan, int taskTravelItemId) {
    if (plan == null
        || !isPointBoosting()
        || currentMasterId != BoostCoordinator.TURAEL_AYA_MASTER_ID
        || TuraelBoost.find(currentTaskName) == null) {
      return plan;
    }
    String[][] travelFamilies = {
      {"max cape", "construct cape"},
      {"eternal slayer ring", "slayer ring"},
      {"digsite pendant"},
      {"ring of dueling"},
      {"amulet of glory"},
      {"games necklace"}
    };
    KitPlan result = plan;
    int additions = 0;
    for (String[] equivalentGroup : travelFamilies) {
      TravelItemMatch owned = null;
      for (String family : equivalentGroup) {
        if (!achievementDiaries.allowsTravelItem(family)) {
          continue;
        }
        owned = findOwnedTravelItem(family);
        if (owned != null) {
          break;
        }
      }
      if (owned == null || owned.itemId == taskTravelItemId) {
        continue;
      }
      KitPlan updated =
          appendMasterReturnTeleportForRegression(result, owned.itemId, owned.displayName);
      if (updated != result) {
        result = updated;
        additions++;
        if (additions >= 4) {
          break;
        }
      }
    }
    return result;
  }

  static KitPlan appendMasterReturnTeleportForRegression(
      KitPlan plan, int itemId, String displayName) {
    if (plan == null || itemId <= 0) {
      return plan;
    }
    List<KitItem> inventory = new ArrayList<>(plan.getInventoryItems());
    for (KitItem item : inventory) {
      if (item != null && item.getItemId() == itemId) {
        return plan;
      }
    }
    for (KitItem item : plan.getEquipmentItems()) {
      if (item != null && item.getItemId() == itemId) {
        return plan;
      }
    }
    KitItem masterReturn =
        new KitItem(displayName, itemId, 1, KitItem.Status.BANK)
            .withInventoryGroup(MethodRules.InventoryGroup.UTILITY);
    if (inventory.size() < 28) {
      inventory.add(masterReturn);
    } else {
      int replaceIndex = lastInventoryGroupIndex(inventory, MethodRules.InventoryGroup.FOOD);
      if (replaceIndex < 0) {
        replaceIndex =
            lastDuplicateInventoryGroupIndex(inventory, MethodRules.InventoryGroup.RESTORE);
      }
      if (replaceIndex < 0) {
        return plan;
      }
      inventory.set(replaceIndex, masterReturn);
    }
    return new KitPlan(
        plan.getEquipment(),
        plan.getInventory(),
        plan.getOwnedStatus(),
        plan.getLayoutTitle(),
        plan.getEquipmentItems(),
        inventory,
        plan.getOptionalItems());
  }

  private static int lastInventoryGroupIndex(
      List<KitItem> inventory, MethodRules.InventoryGroup group) {
    for (int index = Math.min(28, inventory.size()) - 1; index >= 0; index--) {
      KitItem item = inventory.get(index);
      if (item != null && item.getInventoryGroup() == group) {
        return index;
      }
    }
    return -1;
  }

  private static int lastDuplicateInventoryGroupIndex(
      List<KitItem> inventory, MethodRules.InventoryGroup group) {
    int count = 0;
    for (int index = 0; index < Math.min(28, inventory.size()); index++) {
      KitItem item = inventory.get(index);
      if (item != null && item.getInventoryGroup() == group) {
        count++;
      }
    }
    if (count > 1) {
      return lastInventoryGroupIndex(inventory, group);
    }
    return -1;
  }

  static String bankTagStateFingerprint(
      KitPlan plan,
      TravelChoice selection,
      int travelItemId,
      List<Integer> preparationItemIds,
      boolean shortestPathOwnsTravel,
      int authoritativeTravelSlotIndex,
      boolean analyzerSourceZeroIsTravel,
      int extraQuiverAmmoItemId) {
    StringBuilder value = new StringBuilder(512);
    value.append(plan == null ? "" : plan.getLayoutTitle()).append('|');
    appendBankTagItems(
        value, 'E', plan == null ? java.util.Collections.emptyList() : plan.getEquipmentItems());
    appendBankTagItems(
        value, 'I', plan == null ? java.util.Collections.emptyList() : plan.getInventoryItems());
    appendBankTagItems(
        value, 'O', plan == null ? java.util.Collections.emptyList() : plan.getOptionalItems());
    value
        .append("P=")
        .append(preparationItemIds)
        .append('|')
        .append("T=")
        .append(travelItemId)
        .append('|')
        .append(shortestPathOwnsTravel)
        .append('|')
        .append(authoritativeTravelSlotIndex)
        .append('|')
        .append(analyzerSourceZeroIsTravel)
        .append('|')
        .append("Q=")
        .append(extraQuiverAmmoItemId)
        .append('|');
    if (selection != null) {
      value
          .append(selection.getStatus())
          .append('|')
          .append(selection.getItemId())
          .append('|')
          .append(selection.getEquivalentItemFamilies());
    }
    return value.toString();
  }

  private static void appendBankTagItems(
      StringBuilder value, char section, List<KitItem> items) {
    value.append(section).append('=');
    for (KitItem item : items) {
      if (item == null) {
        value.append("null;");
        continue;
      }
      value
          .append(item.getItemId())
          .append(',')
          .append(item.getDisplayName())
          .append(',')
          .append(item.isEquipmentSwitch())
          .append(',')
          .append(item.getSwitchStyle())
          .append(',')
          .append(item.getInventoryGroup())
          .append(';');
    }
    value.append('|');
  }

  private void logQuiverBankTagDiagnostic() {
    if (client == null || !log.isDebugEnabled()) {
      return;
    }
    StringBuilder containerItems = new StringBuilder("[");
    ItemContainer container = client.getItemContainer(InventoryID.DIZANAS_QUIVER_AMMO);
    if (container != null) {
      for (Item item : container.getItems()) {
        if (item == null || item.getId() <= 0) {
          continue;
        }
        if (containerItems.length() > 1) {
          containerItems.append(',');
        }
        containerItems.append(item.getId()).append('x').append(item.getQuantity());
      }
    }
    containerItems.append(']');
    StringBuilder widgets = new StringBuilder("[");
    int[] componentIds = {
      InterfaceID.DizanasQuiver.AMMO_OBJ,
      InterfaceID.Wornitems.EXTRA_QUIVER_AMMO,
      InterfaceID.Equipment.EXTRA_QUIVER_AMMO,
      InterfaceID.Bankmain.EXTRA_QUIVER_AMMO
    };
    for (int componentId : componentIds) {
      Widget widget = client.getWidget(componentId);
      if (widget == null) {
        continue;
      }
      if (widgets.length() > 1) {
        widgets.append(',');
      }
      widgets
          .append(componentId)
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
        cachedExtraQuiverAmmoQuantity);
  }

  private TravelChoice repairAuthoritativeTravelItemVariant(
      TravelChoice selection, String expectedRouteIdentity) {
    if (selection == null
        || !selection.belongsTo(expectedRouteIdentity)
        || !selection.hasPhysicalItem()
        || isTravelItemStillOwned(selection.getItemId())) {
      return selection;
    }
    String selectedFamily = travelItemFamilyName(selection.getItemName());
    TravelItemMatch equivalent = findOwnedTravelItem(selectedFamily);
    if (equivalent == null) {
      return selection;
    }
    TravelChoice repaired =
        travelCoordinator.replacePhysicalItemVariant(
            expectedRouteIdentity, equivalent.itemId, equivalent.displayName);
    if (repaired != null
        && repaired.belongsTo(expectedRouteIdentity)
        && travelCoordinator.ownsCurrentRoute(expectedRouteIdentity)) {
      publishTravelSelection(
          repaired,
          "Using the currently owned equivalent state of Shortest Path's selected travel item."
              + " Panel, highlight, and Bank Tag travel placement remain synchronized.");
    }
    return repaired;
  }

  private void refreshOpenBankTagForTravelSelection() {
    if (clientThread == null || bankTagTravelRefreshQueued) {
      return;
    }
    bankTagTravelRefreshQueued = true;
    clientThread.invokeLater(
        () -> {
          bankTagTravelRefreshQueued = false;
          if (!bankTagLayoutCreated && !isSlayerPlusBankTagOpen()) {
            return;
          }
          createRecommendedBankTagNow();
        });
  }

  static boolean shouldUseBankAwareProfileReroute(
      boolean hasScannedBankSnapshot, boolean liveBankOpen) {
    return hasScannedBankSnapshot || liveBankOpen;
  }

  private void toggleGuidedSession() {
    if (guidedSessionActive) {
      stopGuidedSession("Guided Slayer session stopped");
      return;
    }
    if (!isGuidedSessionAvailable()) {
      guidedSessionStatus =
          client.getGameState() == GameState.LOGGED_IN
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
    int remaining = effectiveTaskRemaining();
    if (currentMasterId > 0) {
      lastUsedMasterId = currentMasterId;
    }
    if (remaining > 0) {
      ItemContainer liveBank = client.getItemContainer(InventoryID.BANK);
      if (liveBank != null) {
        cacheBankContainer(liveBank);
        refreshSlayerData();
      } else if (currentRecommendation == null) {
        refreshSlayerData();
      }
      GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
      if (confirmGuidedTaskAreaBeforePreparation(target)) {
        return;
      }
      if (routeToSpellbookIfRequired("Required spellbook is not active")) {
        return;
      }
      if (liveBank != null) {
        routeToTaskForGuidedSession(true, "Bank scanned");
      } else if (isInfernoPreparationTarget(target)) {
        routeToInfernoPreparationBankForGuidedSession(
            target, "TzKal-Zuk task found - preparation starts at the east Mor Ul Rek bank");
      } else if (isCarriedLoadoutReadyForRegression(currentLoadout)) {
        routeToTaskForGuidedSession(false, "The complete reviewed loadout is already carried");
      } else {
        routeToBankForGuidedSession("Active task found — go to a bank to gear up");
      }
      return;
    }
    if (getRoutingMasterId() > 0) {
      routeToMasterForGuidedSession();
    } else {
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus(
          "Session active — get a Slayer task and SlayerPlus will guide the next step");
    }
  }

  private void stopGuidedSession(String status) {
    if (shortestPathBridge != null) {
      shortestPathBridge.clear();
    }
    resetGuidedSessionState(false);
    guidedSessionStatus =
        status == null || status.trim().isEmpty() ? "Guided Slayer session stopped" : status.trim();
    updateGuidedSessionPanel();
    refreshGuidedTeleportWidgetHighlights();
  }

  private void resetGuidedSessionState() {
    resetGuidedSessionState(true);
  }

  private void resetGuidedSessionState(boolean clearTravelSelection) {
    if (teleportHighlighter != null) {
      teleportHighlighter.setRouteGuidance("");
    }
    guidedSessionActive = false;
    guidedSessionPhase = GuidedSessionPhase.STOPPED;
    guidedSessionStatus = "Start a guided session to route through your Slayer task";
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
    if (clearTravelSelection) {
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
    if (clearTravelSelection) {
      restoreGuidedTeleportWidgetHighlights();
      clearTeleportHighlightCaptures();
      showGuidedTravelRecommendation("", "");
    }
  }

  private void handleBankOpenedForGuidedSession() {
    if (!guidedSessionActive) {
      return;
    }
    if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK) {
      Player player = client.getLocalPlayer();
      WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
      WorldPoint liveBankerTarget = findInfernoPreparationBankerTarget(playerLocation);
      GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
      if (!isInfernoPreparationTarget(target)) {
        return;
      }
      if (!isAtInfernoPreparationBank(playerLocation) && liveBankerTarget == null) {
        TravelRouteMatch bankTravel = findInfernoPreparationTravelItem();
        if (isManualInfernoPreparationTeleport(bankTravel)) {
          log.debug(
              "Inferno preparation bank scan replaced the Hot vent branch with Ghommal item {}",
              bankTravel.item.itemId);
          routeToInfernoPreparationBankForGuidedSession(
              target, "Supported Ghommal item found in the open bank");
        }
        return;
      }
      ItemContainer liveBank = client.getItemContainer(InventoryID.BANK);
      if (liveBank != null) {
        cacheBankContainer(liveBank);
      }
      infernoPreparationBankReady = true;
      infernoPreparationManualTeleportPending = false;
      infernoPreparationHotVentPending = false;
      if (shortestPathBridge != null) {
        shortestPathBridge.clear();
      }
      travelCoordinator.invalidateRoute(infernoPreparationBankRouteIdentity(target));
      guidedBankTransportTracking = false;
      guidedTaskBankPickupPending = false;
      guidedTravelRequestGeneration = -1L;
      guidedTravelRequestRouteIdentity = "";
      restoreGuidedTeleportWidgetHighlights();
      clearTeleportHighlightCaptures();
      showGuidedTravelRecommendation("", "");
      refreshSlayerData();
      createRecommendedBankTagNow();
      GuidedTaskTarget refreshedTarget = resolveGuidedTaskTarget();
      routeToInfernoEntryForGuidedSession(
          refreshedTarget != null ? refreshedTarget : target, "Inferno preparation bank opened");
      return;
    }
    if (shouldResumeTaskAfterBankForRegression(guidedSessionPhase, effectiveTaskRemaining())) {
      routeToTaskForGuidedSession(true, "Bank scanned — resuming the active task");
      return;
    }
    if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER) {
      routeToMasterForGuidedSession(true);
      return;
    }
    if (shouldResumeMasterAfterBankForRegression(
        guidedSessionPhase, guidedMasterBankPickupPending, effectiveTaskRemaining())) {
      routeToMasterForGuidedSession(true);
      return;
    }
    if (guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_BANK) {
      return;
    }
    if (effectiveTaskRemaining() <= 0) {
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus("Bank opened, but no active task is available");
      return;
    }
    routeToTaskForGuidedSession(true, "Bank scanned");
  }

  static boolean shouldResumeTaskAfterBankForRegression(GuidedSessionPhase phase, int remaining) {
    return remaining > 0
        && (phase == GuidedSessionPhase.ROUTING_TO_TASK || phase == GuidedSessionPhase.TASKING);
  }

  static boolean shouldResumeMasterAfterBankForRegression(
      GuidedSessionPhase phase, boolean masterBankPickupPending, int remaining) {
    return phase == GuidedSessionPhase.ROUTING_TO_BANK && masterBankPickupPending && remaining <= 0;
  }

  private void handleBankClosedForGuidedSession() {
    if (!shouldResumeTaskAfterBankCloseForRegression(
        guidedSessionActive, guidedSessionPhase, effectiveTaskRemaining())) {
      return;
    }
    if (whispererRingTeleportPending) {
      TravelChoice selection = currentTravelSelection();
      boolean ringCarried =
          selection.hasPhysicalItem()
              && findCarriedTravelItemForTransport(selection.getItemName()) != null;
      routeToTaskForGuidedSession(
          !ringCarried,
          ringCarried
              ? "Bank closed — use Ring of shadows -> Lassar Undercity"
              : "Bank closed before the Ring of shadows was withdrawn");
      return;
    }
    routeToTaskForGuidedSession(false, "Bank closed — returning to the unfinished task");
  }

  static boolean shouldResumeTaskAfterBankCloseForRegression(
      boolean sessionActive, GuidedSessionPhase phase, int remaining) {
    return sessionActive
        && remaining > 0
        && (phase == GuidedSessionPhase.ROUTING_TO_BANK
            || phase == GuidedSessionPhase.ROUTING_TO_TASK
            || phase == GuidedSessionPhase.TASKING);
  }

  private void handleGuidedTaskTransition(
      int previousRemaining,
      String previousTaskName,
      int remaining,
      String taskName,
      int previousNormalStreak,
      int normalStreak) {
    if (!guidedSessionActive || previousRemaining < 0) {
      return;
    }
    boolean hadTask = previousRemaining > 0;
    boolean hasTask = remaining > 0;
    if (pointBoostStreakSyncPending && !hasTask) {
      clearGuidedTaskDetourSnapshot();
      if (normalStreak == pointBoostStreakBeforeCompletion) {
        guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
        setGuidedSessionStatus(
            "Task complete — waiting for the Slayer streak to update before choosing the next"
                + " point-boost master.");
        return;
      }
      pointBoostStreakSyncPending = false;
      pointBoostStreakBeforeCompletion = -1;
      routeToMasterForGuidedSession();
      return;
    }
    if (hadTask && !hasTask) {
      clearGuidedTaskDetourSnapshot();
      if (isPointBoosting() && previousNormalStreak >= 0 && normalStreak == previousNormalStreak) {
        pointBoostStreakSyncPending = true;
        pointBoostStreakBeforeCompletion = previousNormalStreak;
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
        guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
        setGuidedSessionStatus(
            "Task complete — waiting for the Slayer streak to update before choosing the next"
                + " point-boost master.");
        return;
      }
      routeToMasterForGuidedSession();
      return;
    }
    boolean receivedNewTask = hasTask && (!hadTask || !sameTask(previousTaskName, taskName));
    if (receivedNewTask) {
      clearGuidedTaskDetourSnapshot();
      pointBoostStreakSyncPending = false;
      pointBoostStreakBeforeCompletion = -1;
      GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
      if (routeToSpellbookIfRequired("New task requires a different spellbook")) {
        return;
      }
      if (isInfernoPreparationTarget(target)) {
        routeToInfernoPreparationBankForGuidedSession(
            target, "New TzKal-Zuk task received - preparation starts at the east Mor Ul Rek bank");
        return;
      }
      if (guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_BANK) {
        routeToBankForGuidedSession("New task received — go to a bank to gear up");
      }
      return;
    }
    if (hasTask
        && previousRemaining > remaining
        && shouldConfirmTaskAreaAfterCountDecreaseForRegression(guidedSessionPhase)) {
      confirmTaskAreaForGuidedSession();
    }
  }

  static boolean shouldConfirmTaskAreaAfterCountDecreaseForRegression(GuidedSessionPhase phase) {
    return phase == GuidedSessionPhase.TASKING;
  }

  private void routeToBankForGuidedSession(String reason) {
    if (!guidedSessionActive) {
      return;
    }
    captureGuidedTaskDetourSnapshot();
    GuidedTaskDetourSnapshot detour = guidedTaskDetourSnapshot;
    if (detour != null && detour.target != null && detour.target.routePlan != null) {
      ensureGuidedRoutePlanState(detour.target);
      restartGuidedRoutePlanProgress();
    }
    clearGuidedTravelRecommendation();
    resetTormentedRouteProgress();
    boolean krystiliaSession = getRoutingMasterId() == KRYSTILIA_MASTER_ID;
    Player player = client.getLocalPlayer();
    WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
    seedLoadedBankObjectsIfNeeded();
    WorldPoint loadedBankTarget =
        nearestTarget(
            playerLocation,
            findLoadedBankNpcTarget(playerLocation),
            findLoadedBankObjectTarget(playerLocation));
    WorldPoint reviewedLocalBankTarget =
        loadedBankTarget == null
            ? BankRoutes.getPreferredLocalApproachTarget(playerLocation)
            : null;
    boolean sent;
    if (shortestPathBridge == null) {
      sent = false;
    } else if (loadedBankTarget != null) {
      sent =
          shortestPathBridge.routeToLocalBankTarget(
              shortestPathStart(playerLocation), loadedBankTarget, !krystiliaSession);
    } else if (reviewedLocalBankTarget != null) {
      sent =
          shortestPathBridge.routeToLocalBankTarget(
              shortestPathStart(playerLocation), reviewedLocalBankTarget, !krystiliaSession);
    } else {
      sent =
          shortestPathBridge.routeToAny(
              shortestPathStart(playerLocation),
              BankRoutes.getBankTargets(krystiliaSession),
              !krystiliaSession,
              false,
              false);
    }
    if (!sent) {
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus("Unable to send a bank route to Shortest Path");
      return;
    }
    guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_BANK;
    guidedLastPlayerLocation = playerLocation;
    taskAreaAnchor = null;
    taskArrivalGraceTicks = 0;
    taskAreaExitTicks = 0;
    setGuidedSessionStatus(
        loadedBankTarget == null
            ? reason + ". Follow the highlighted route using teleports you carry."
            : reason + ". A usable bank is already nearby — follow the local route and open it.");
  }

  private boolean routeToSpellbookIfRequired(String reason) {
    if (!guidedSessionActive
        || currentPreparation == null
        || !currentPreparation.isActive()
        || currentPreparation.isSpellbookReady()) {
      return false;
    }
    int currentBook = client.getVarbitValue(SPELLBOOK_VARBIT);
    SlayerSpellbookRouteCatalog.Route route =
        SlayerSpellbookRouteCatalog.resolve(currentPreparation.getSpellbookName(), currentBook);
    if (route == null) {
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus(
          "A different spellbook is required, but no reviewed change route is available");
      return true;
    }
    Player player = client.getLocalPlayer();
    WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
    if (needsBankSnapshotBeforeSpellbookRouteForRegression(bankScanned)) {
      routeToBankForGuidedSession(reason + " — scan a bank so owned teleports can be compared");
      return true;
    }
    if (!guidedPohSpellbookFallbackActive) {
      if (pohAltarSupportsSpellbookForRegression(
          loadedPohSpellbookAltarId, currentPreparation.getSpellbookName())) {
        clearGuidedTravelRecommendation();
        guidedPohSpellbookTeleportPending = false;
        guidedPohSpellbookAltarReached = true;
        guidedPohSpellbookLoadTicks = 0;
        guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_SPELLBOOK;
        setGuidedSessionStatus(
            reason
                + " — use the loaded POH altar to switch to "
                + route.getSpellbookName()
                + ". SlayerPlus will route back to a bank after the switch.");
        return true;
      }
      TravelRouteMatch pohTravel = findOwnedPohSpellbookTravel();
      if (pohTravel != null) {
        String identity = "spellbook-poh|" + normalizeTravelName(route.getSpellbookName());
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
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
        applyTravelRouteMatch(pohTravel, identity);
        refreshGuidedTeleportWidgetHighlights();
        setGuidedSessionStatus(
            reason
                + " — use the highlighted "
                + pohTravel.item.displayName
                + " -> "
                + pohTravel.destination
                + ". SlayerPlus will verify the loaded altar and continue automatically.");
        return true;
      }
    }
    clearGuidedTravelRecommendation();
    boolean suppressInactiveQuestCape =
        shouldSuppressInactiveQuestCapeForRegression(
            isTravelItemStillOwned(ItemID.SKILLCAPE_QP)
                || isTravelItemStillOwned(ItemID.SKILLCAPE_QP_TRIMMED),
            client.getVarpValue(VarPlayerID.QP),
            client.getVarbitValue(VarbitID.QP_MAX));
    boolean sent =
        shortestPathBridge != null
            && shortestPathBridge.routeToSpellbookChange(
                shortestPathStart(playerLocation),
                route.getTargets(),
                true,
                suppressInactiveQuestCape);
    if (!sent) {
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus(
          "Unable to send the " + route.getSpellbookName() + " change route to Shortest Path");
      return true;
    }
    guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_SPELLBOOK;
    guidedLastPlayerLocation = playerLocation;
    taskAreaAnchor = route.getDestination();
    taskArrivalGraceTicks = 0;
    taskAreaExitTicks = 0;
    setGuidedSessionStatus(
        reason
            + " — Shortest Path is routing to the reviewed "
            + route.getSpellbookName()
            + " change point using the fastest teleport you own in your inventory or bank. "
            + route.getInteraction());
    return true;
  }

  private TravelRouteMatch findOwnedPohSpellbookTravel() {
    for (String family : pohSpellbookTravelFamiliesForRegression()) {
      TravelItemMatch item = findOwnedTravelItem(family);
      if (item != null) {
        return new TravelRouteMatch(
            item,
            family.equals("teleport to house") ? "Break" : "Teleport to house",
            java.util.Collections.singleton(family));
      }
    }
    return null;
  }

  static List<String> pohSpellbookTravelFamiliesForRegression() {
    return Arrays.asList("max cape", "construct cape", "teleport to house");
  }

  static boolean needsBankSnapshotBeforeSpellbookRouteForRegression(boolean hasBankSnapshot) {
    return !hasBankSnapshot;
  }

  private WorldPoint shortestPathStart(WorldPoint point) {
    WorldView worldView = client.getTopLevelWorldView();
    return shortestPathStartForRegression(point, worldView != null && worldView.isInstance());
  }

  private WorldPoint routeComparisonLocation(Player player, WorldPoint fallback) {
    if (player == null) {
      return fallback;
    }
    WorldView worldView = client.getTopLevelWorldView();
    if (worldView == null || !worldView.isInstance() || player.getLocalLocation() == null) {
      return fallback;
    }
    WorldPoint templateLocation = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
    return templateLocation == null ? fallback : templateLocation;
  }

  static WorldPoint shortestPathStartForRegression(WorldPoint point, boolean instancedWorldView) {
    return instancedWorldView ? null : point;
  }

  static boolean shouldSuppressInactiveQuestCapeForRegression(
      boolean questCapeOwned, int currentQuestPoints, int maximumQuestPoints) {
    return questCapeOwned && maximumQuestPoints > 0 && currentQuestPoints < maximumQuestPoints;
  }

  private void handleGuidedSpellbookReadinessTransition() {
    if (!guidedSessionActive
        || guidedSessionPhase != GuidedSessionPhase.ROUTING_TO_SPELLBOOK
        || currentPreparation == null
        || !currentPreparation.isSpellbookReady()) {
      return;
    }
    if (shortestPathBridge != null) {
      shortestPathBridge.clear();
    }
    guidedPohSpellbookTeleportPending = false;
    guidedPohSpellbookAltarReached = false;
    guidedPohSpellbookFallbackActive = false;
    guidedPohSpellbookLoadTicks = 0;
    taskAreaAnchor = null;
    if (shouldReturnToBankAfterSpellbookForRegression(bankScanned, currentLoadout)) {
      routeToBankForGuidedSession("Spellbook changed — return to a bank to finish the loadout");
    } else {
      routeToTaskForGuidedSession(false, "Spellbook changed and the carried loadout is complete");
    }
  }

  static boolean shouldReturnToBankAfterSpellbookForRegression(
      boolean hasBankSnapshot, KitPlan loadout) {
    return true;
  }

  private static boolean hasBankedRequiredItem(List<KitItem> items) {
    if (items == null) {
      return false;
    }
    for (KitItem item : items) {
      if (item != null && item.isBanked()) {
        return true;
      }
    }
    return false;
  }

  static boolean isCarriedLoadoutReadyForRegression(KitPlan loadout) {
    if (loadout == null || !loadout.hasConcreteItems()) {
      return false;
    }
    return itemsAreCarried(loadout.getEquipmentItems())
        && itemsAreCarried(loadout.getInventoryItems());
  }

  private static boolean itemsAreCarried(List<KitItem> items) {
    for (KitItem item : items) {
      if (item == null) {
        continue;
      }
      KitItem.Status status = item.getStatus();
      if (status == KitItem.Status.BANK
          || status == KitItem.Status.MISSING
          || status == KitItem.Status.UNKNOWN) {
        return false;
      }
    }
    return true;
  }

  private boolean selectedStrategyUsesCannon() {
    return currentRecommendation != null
        && currentRecommendation.getStrategy() != null
        && currentRecommendation.getStrategy().hasTag(TaskStrategy.MethodTag.CANNON);
  }

  private WorldPoint selectedCannonPosition(GuidedTaskTarget target) {
    if (target == null || target.isBoss() || !selectedStrategyUsesCannon()) {
      return null;
    }
    return RouteCatalog.findCannonPosition(target.taskName, target.location);
  }

  private WorldPoint selectedBarragePosition(GuidedTaskTarget target) {
    if (target == null
        || target.isBoss()
        || currentRecommendation == null
        || currentRecommendation.getStrategy() == null
        || !currentRecommendation
            .getStrategy()
            .hasTag(TaskStrategy.MethodTag.BURST_BARRAGE)) {
      return null;
    }
    return RouteCatalog.findBarragePosition(target.taskName, target.location);
  }

  private RouteStage resolveRouteStage(GuidedTaskTarget target, WorldPoint playerLocation) {
    return resolveRouteStage(target, playerLocation, playerLocation);
  }

  private RouteStage resolveRouteStage(
      GuidedTaskTarget target, WorldPoint playerLocation, WorldPoint livePlayerLocation) {
    if (target == null || target.profile == null) {
      return null;
    }
    if (shouldUseMeiyerditchShortcut(target, playerLocation)) {
      return new RouteStage(
          MEIYERDITCH_SHORTCUT_APPROACH,
          singletonRouteTarget(MEIYERDITCH_SHORTCUT_APPROACH),
          true,
          false,
          false);
    }
    WorldPoint kalphiteQueenLowerRope =
        kalphiteQueenInteriorTransitionForRegression(
            target.taskName, target.location, playerLocation);
    if (kalphiteQueenLowerRope != null) {
      return new RouteStage(
          kalphiteQueenLowerRope,
          RouteCatalog.targetArea(kalphiteQueenLowerRope, 1),
          true,
          false,
          false);
    }
    WorldPoint whispererIntermediate =
        whispererIntermediateDestinationForRegression(
            target.taskName, target.location, playerLocation);
    if (whispererIntermediate != null) {
      return new RouteStage(
          whispererIntermediate, singletonRouteTarget(whispererIntermediate), true, false, false);
    }
    WorldPoint aquaniteIslandIntermediate =
        aquaniteIslandDestinationForRegression(target.taskName, target.location, playerLocation);
    if (aquaniteIslandIntermediate != null) {
      return new RouteStage(
          aquaniteIslandIntermediate,
          RouteCatalog.targetArea(aquaniteIslandIntermediate, 1),
          true,
          false,
          false);
    }
    WorldPoint aquaniteSailingDeparture =
        aquaniteSailingDepartureForRegression(
            target.taskName, target.location, playerLocation, hasBuiltYnysdailRowboat());
    if (aquaniteSailingDeparture != null) {
      return new RouteStage(
          aquaniteSailingDeparture,
          RouteCatalog.targetArea(aquaniteSailingDeparture, 2),
          true,
          false,
          false);
    }
    WorldPoint whispererTeleporter =
        whispererCathedralTeleporterApproachForRegression(
            target.taskName, target.location, playerLocation);
    if (whispererTeleporter != null) {
      return new RouteStage(
          whispererTeleporter,
          RouteCatalog.targetArea(whispererTeleporter, 2),
          true,
          false,
          false);
    }
    boolean stagedOutside = target.isStaged() && !target.isInsideEncounterArea(playerLocation);
    boolean accessOutside =
        target.profile.isAccessThenNpc()
            && target.destination(playerLocation) != null
            && sameCoordinateLayer(playerLocation, target.destination(playerLocation));
    String discoveryIdentity = routeDiscoveryIdentity(target);
    WorldPoint exactEntrance = null;
    if (stagedOutside || accessOutside) {
      WorldPoint authoredEntrance =
          target.profile.getSurfaceAccess() != null
              ? target.profile.getSurfaceAccess()
              : target.destination(playerLocation);
      exactEntrance = guidedSurfaceEntranceDestination;
      if (exactEntrance == null
          && shouldDiscoverNearbyRouteEntity(
              playerLocation, authoredEntrance, DYNAMIC_TASK_NPC_RADIUS)
          && routeDiscoveryDue(
              gameTickSequence,
              lastSurfaceEntranceDiscoveryTick,
              discoveryIdentity,
              lastSurfaceEntranceDiscoveryIdentity)) {
        lastSurfaceEntranceDiscoveryTick = gameTickSequence;
        lastSurfaceEntranceDiscoveryIdentity = discoveryIdentity;
        exactEntrance = findLoadedSurfaceEntrance(target.profile, authoredEntrance);
        if (exactEntrance != null) {
          guidedSurfaceEntranceDestination = exactEntrance;
        }
      }
    }
    WorldPoint methodPosition = null;
    WorldPoint reviewedCannonPosition = selectedCannonPosition(target);
    WorldPoint reviewedMethodPosition =
        reviewedCannonPosition != null ? reviewedCannonPosition : selectedBarragePosition(target);
    if (!stagedOutside
        && reviewedMethodPosition != null
        && sameCoordinateLayer(playerLocation, reviewedMethodPosition)) {
      methodPosition = reviewedMethodPosition;
    }
    WorldPoint exactNpc = null;
    if (!stagedOutside) {
      exactNpc = guidedDynamicTaskDestination;
      if (exactNpc == null
          && shouldAttemptExactNpcDiscoveryForRegression(
              target.profile, playerLocation, DYNAMIC_TASK_NPC_RADIUS)
          && routeDiscoveryDue(
              gameTickSequence,
              lastTaskNpcDiscoveryTick,
              discoveryIdentity,
              lastTaskNpcDiscoveryIdentity)) {
        lastTaskNpcDiscoveryTick = gameTickSequence;
        lastTaskNpcDiscoveryIdentity = discoveryIdentity;
        WorldPoint discovered = findLoadedTaskNpcTarget(livePlayerLocation, target.profile);
        if (discovered != null) {
          exactNpc = discovered;
          guidedDynamicTaskDestination = discovered;
          saveLearnedRouteCheckpoint(target, discovered);
        }
      }
    }
    WorldPoint learned = readLearnedRouteCheckpoint(target);
    WorldPoint usableLearned =
        canUseLearnedRouteCheckpointNow(target, playerLocation, learned) ? learned : null;
    SlayerRouteCoordinator.Stage resolved =
        SlayerRouteCoordinator.resolve(
            target.profile,
            playerLocation,
            exactEntrance,
            exactEntrance == null
                ? java.util.Collections.emptySet()
                : singletonRouteTarget(exactEntrance),
            methodPosition,
            methodPosition == null
                ? java.util.Collections.emptySet()
                : singletonRouteTarget(methodPosition),
            exactNpc,
            exactNpc == null ? java.util.Collections.emptySet() : singletonRouteTarget(exactNpc),
            usableLearned,
            usableLearned == null
                ? java.util.Collections.emptySet()
                : RouteCatalog.targetArea(usableLearned, 2));
    if (resolved == null || !resolved.isValid()) {
      return null;
    }
    SlayerRouteCoordinator.StageKind kind = resolved.getKind();
    return new RouteStage(
        resolved.getDestination(),
        resolved.getTargets(),
        kind == SlayerRouteCoordinator.StageKind.SURFACE_APPROACH
            || kind == SlayerRouteCoordinator.StageKind.EXACT_ENTRANCE
            || kind == SlayerRouteCoordinator.StageKind.ACCESS,
        kind == SlayerRouteCoordinator.StageKind.EXACT_NPC,
        kind == SlayerRouteCoordinator.StageKind.LEARNED_CHECKPOINT);
  }

  static boolean shouldAttemptExactNpcDiscoveryForRegression(
      RouteCatalog.RouteProfile profile, WorldPoint playerLocation, int maximumRadius) {
    if (profile == null || playerLocation == null) {
      return false;
    }
    if (profile.requiresLoadedNpc() || profile.isAccessThenNpc()) {
      return true;
    }
    return profile.isInsideEncounterArea(playerLocation)
        || shouldDiscoverNearbyRouteEntity(
            playerLocation, profile.getRouteDestination(playerLocation), maximumRadius);
  }

  private boolean shouldUseMeiyerditchShortcut(GuidedTaskTarget target, WorldPoint playerLocation) {
    return target != null
        && client != null
        && shouldUseMeiyerditchShortcutForRegression(
            target.taskName,
            target.location,
            playerLocation,
            client.getBoostedSkillLevel(Skill.AGILITY));
  }

  static WorldPoint whispererIntermediateDestinationForRegression(
      String taskName, String location, WorldPoint playerLocation) {
    if (playerLocation == null
        || !("whisperer".equals(normalizeTravelName(taskName))
            || "the whisperer".equals(normalizeTravelName(taskName)))
        || !"lassar undercity".equals(normalizeTravelName(location))
        || playerLocation.getPlane() != 0
        || playerLocation.getX() < CAMDOZAAL_MIN_X
        || playerLocation.getX() > CAMDOZAAL_MAX_X
        || playerLocation.getY() < CAMDOZAAL_MIN_Y
        || playerLocation.getY() > CAMDOZAAL_MAX_Y) {
      return null;
    }
    return WHISPERER_LASSAR_SINKHOLE;
  }

  static WorldPoint aquaniteIslandDestinationForRegression(
      String taskName, String location, WorldPoint playerLocation) {
    String task = normalizeTravelName(taskName);
    return playerLocation != null
            && ("aquanite".equals(task) || "aquanites".equals(task))
            && "ynysdail cavern".equals(normalizeTravelName(location))
            && playerLocation.getPlane() == AQUANITE_YNYSDAIL_ROWBOAT.getPlane()
            && distance(playerLocation, AQUANITE_YNYSDAIL_ROWBOAT) <= 24
        ? AQUANITE_CAVERN_ENTRANCE
        : null;
  }

  private boolean hasBuiltYnysdailRowboat() {
    return client != null
        && hasBuiltYnysdailRowboatForRegression(
            client.getVarbitValue(VarbitID.AMENITY_ROWBOAT_YNYSDAIL));
  }

  static boolean hasBuiltYnysdailRowboatForRegression(int value) {
    return value > 0;
  }

  static WorldPoint aquaniteSailingDepartureForRegression(
      String taskName, String location, WorldPoint playerLocation, boolean rowboatBuilt) {
    String task = normalizeTravelName(taskName);
    if (rowboatBuilt
        || playerLocation == null
        || !("aquanite".equals(task) || "aquanites".equals(task))
        || !"ynysdail cavern".equals(normalizeTravelName(location))
        || (playerLocation.getPlane() == AQUANITE_YNYSDAIL_ROWBOAT.getPlane()
            && distance(playerLocation, AQUANITE_YNYSDAIL_ROWBOAT) <= 24)) {
      return null;
    }
    return AQUANITE_PORT_ROBERTS_MOORING;
  }

  static boolean isWhispererLassarInteriorForRegression(
      String taskName, String location, WorldPoint playerLocation) {
    String task = normalizeTravelName(taskName).replaceFirst("^the\\s+", "");
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
      String taskName, String location, String itemName, String destination) {
    String task = normalizeTravelName(taskName).replaceFirst("^the\\s+", "");
    return task.equals("whisperer")
        && "lassar undercity".equals(normalizeTravelName(location))
        && normalizeTravelName(itemName).contains("ring of shadows")
        && "lassar undercity".equals(normalizeTravelName(destination));
  }

  static WorldPoint whispererRingLandingForRegression() {
    return WHISPERER_LASSAR_RING_LANDING;
  }

  static WorldPoint whispererCathedralTeleporterApproachForRegression(
      String taskName, String location, WorldPoint playerLocation) {
    String task = normalizeTravelName(taskName).replaceFirst("^the\\s+", "");
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
      String taskName, String location, WorldPoint playerLocation) {
    String task = normalizeTravelName(taskName).replaceFirst("^the\\s+", "");
    return playerLocation != null
        && task.equals("whisperer")
        && "lassar undercity".equals(normalizeTravelName(location))
        && playerLocation.getPlane() == WHISPERER_CATHEDRAL_TELEPORTER.getPlane()
        && distance(playerLocation, WHISPERER_CATHEDRAL_TELEPORTER) <= 12;
  }

  static boolean shouldUseMeiyerditchShortcutForRegression(
      String taskName, String location, WorldPoint playerLocation, int boostedAgilityLevel) {
    String task = normalizeTravelName(taskName);
    if (playerLocation == null
        || (!("bloodveld".equals(task)) && !("bloodvelds".equals(task)))
        || !"meiyerditch laboratories".equals(normalizeTravelName(location))
        || boostedAgilityLevel < MEIYERDITCH_SHORTCUT_AGILITY_LEVEL) {
      return false;
    }
    boolean onLaboratorySide =
        playerLocation.getX() >= MEIYERDITCH_LAB_SHORTCUT_EXIT_MIN_X
            && playerLocation.getX() <= 3660
            && playerLocation.getY() >= 9650
            && playerLocation.getY() <= 9850;
    return !onLaboratorySide;
  }

  private static boolean isMeiyerditchShortcutStage(
      GuidedTaskTarget target, WorldPoint destination) {
    return target != null
        && MEIYERDITCH_SHORTCUT_APPROACH.equals(destination)
        && "meiyerditch laboratories".equals(normalizeTravelName(target.location));
  }

  private static boolean isWhispererSinkholeStage(GuidedTaskTarget target, WorldPoint destination) {
    return target != null
        && WHISPERER_LASSAR_SINKHOLE.equals(destination)
        && ("whisperer".equals(normalizeTravelName(target.taskName))
            || "the whisperer".equals(normalizeTravelName(target.taskName)))
        && "lassar undercity".equals(normalizeTravelName(target.location));
  }

  private static String routeDiscoveryIdentity(GuidedTaskTarget target) {
    if (target == null) {
      return "";
    }
    return normalizeTravelName(target.taskName)
        + "|"
        + normalizeTravelName(target.location)
        + "|"
        + (target.isBoss() ? "boss" : "regular");
  }

  static boolean routeDiscoveryDue(
      long currentTick, long previousTick, String currentIdentity, String previousIdentity) {
    if (currentIdentity == null || currentIdentity.isEmpty()) {
      return false;
    }
    if (!currentIdentity.equals(previousIdentity) || previousTick == Long.MIN_VALUE) {
      return true;
    }
    return currentTick - previousTick >= ROUTE_DISCOVERY_INTERVAL_TICKS;
  }

  static boolean shouldDiscoverNearbyRouteEntity(
      WorldPoint playerLocation, WorldPoint authoredLocation, int maximumRadius) {
    return playerLocation != null
        && authoredLocation != null
        && playerLocation.getPlane() == authoredLocation.getPlane()
        && distance(playerLocation, authoredLocation) <= Math.max(1, maximumRadius);
  }

  private void clearRouteDiscoveryThrottle() {
    lastSurfaceEntranceDiscoveryTick = Long.MIN_VALUE;
    lastSurfaceEntranceDiscoveryIdentity = "";
    lastTaskNpcDiscoveryTick = Long.MIN_VALUE;
    lastTaskNpcDiscoveryIdentity = "";
  }

  private static boolean sameCoordinateLayer(WorldPoint first, WorldPoint second) {
    if (first == null || second == null) {
      return true;
    }
    return first.getPlane() == second.getPlane() && (first.getY() >>> 12) == (second.getY() >>> 12);
  }

  static boolean shouldUseLocalTaskRouteForRegression(
      WorldPoint playerLocation, WorldPoint destination) {
    return destination != null
        && destination.getY() >= 4096
        && sameCoordinateLayer(playerLocation, destination);
  }

  static WorldPoint kalphiteQueenInteriorTransitionForRegression(
      String taskName, String location, WorldPoint playerLocation) {
    if (taskName == null
        || location == null
        || playerLocation == null
        || !(taskName.equalsIgnoreCase("Kalphite Queen")
            || taskName.equalsIgnoreCase("The Kalphite Queen"))
        || !location.equalsIgnoreCase("Kalphite Lair")
        || playerLocation.getPlane() != KALPHITE_QUEEN_LOWER_ROPE_APPROACH.getPlane()
        || !sameCoordinateLayer(playerLocation, KALPHITE_QUEEN_LOWER_ROPE_APPROACH)) {
      return null;
    }
    return Math.abs(playerLocation.getX() - KALPHITE_QUEEN_LOWER_ROPE_APPROACH.getX())
                <= KALPHITE_LAIR_STAGE_RADIUS
            && Math.abs(playerLocation.getY() - KALPHITE_QUEEN_LOWER_ROPE_APPROACH.getY())
                <= KALPHITE_LAIR_STAGE_RADIUS
        ? KALPHITE_QUEEN_LOWER_ROPE_APPROACH
        : null;
  }

  private boolean shouldEnableAgilityShortcutsForStage(
      GuidedTaskTarget target, WorldPoint destination) {
    if (target == null || client == null) {
      return true;
    }
    return shouldEnableAgilityShortcutsForStageForRegression(
        target.taskName,
        target.location,
        destination,
        client.getBoostedSkillLevel(Skill.AGILITY),
        client.getVarbitValue(VarbitID.DESERT_DIARY_ELITE_COMPLETE) > 0);
  }

  static boolean shouldEnableAgilityShortcutsForStageForRegression(
      String taskName,
      String location,
      WorldPoint destination,
      int agilityLevel,
      boolean desertEliteDiaryComplete) {
    boolean kalphiteQueenLowerRopeStage =
        destination != null
            && destination.equals(KALPHITE_QUEEN_LOWER_ROPE_APPROACH)
            && taskName != null
            && (taskName.equalsIgnoreCase("Kalphite Queen")
                || taskName.equalsIgnoreCase("The Kalphite Queen"))
            && location != null
            && location.equalsIgnoreCase("Kalphite Lair");
    return !kalphiteQueenLowerRopeStage
        || (desertEliteDiaryComplete && agilityLevel >= KALPHITE_LAIR_SHORTCUT_AGILITY_LEVEL);
  }

  static boolean shouldRetireTravelRecommendationForLocalRoute(
      boolean localTaskRoute, boolean travelTrackingActive, boolean hasPhysicalTravelItem) {
    return localTaskRoute && (travelTrackingActive || hasPhysicalTravelItem);
  }

  private boolean canUseLearnedRouteCheckpointNow(
      GuidedTaskTarget target, WorldPoint playerLocation, WorldPoint checkpoint) {
    if (target == null
        || target.profile == null
        || playerLocation == null
        || checkpoint == null
        || !target.profile.isPlausibleLearnedCheckpoint(checkpoint)
        || playerLocation.getPlane() != checkpoint.getPlane()) {
      return false;
    }
    if (target.isStaged()) {
      return target.isInsideEncounterArea(playerLocation);
    }
    return distance(playerLocation, checkpoint) <= LEARNED_ROUTE_USE_RADIUS;
  }

  private String routeIdentity(GuidedTaskTarget target) {
    if (target == null) {
      return "";
    }
    String recommendationMethod =
        currentRecommendation == null ? "" : normalizeTravelName(currentRecommendation.getMethod());
    String recommendationTravel =
        currentRecommendation == null ? "" : normalizeTravelName(currentRecommendation.getTravel());
    String recommendationCannon =
        currentRecommendation == null ? "" : normalizeTravelName(currentRecommendation.getCannon());
    GuidedTaskDetourSnapshot detour = guidedTaskDetourSnapshot;
    GuideLifecycle.TaskRouteKey routeKey =
        detour != null && detour.target == target
            ? detour.key
            : GuideLifecycle.taskRouteKey(
                currentTaskName, currentTaskVariant, target.taskName, target.location);
    return routeKey.routeIdentity()
        + "|"
        + (target.isBoss() ? "boss" : "regular")
        + "|playstyle="
        + String.valueOf(config.playstyle())
        + "|combat="
        + String.valueOf(config.combatStylePreference())
        + "|cannon="
        + String.valueOf(config.cannonPreference())
        + "|burst="
        + String.valueOf(config.burstPreference())
        + "|travelpref="
        + String.valueOf(config.travelPreference())
        + "|method="
        + recommendationMethod
        + "|travel="
        + recommendationTravel
        + "|routecannon="
        + recommendationCannon;
  }

  private boolean ensureGuidedRoutePlanState(GuidedTaskTarget target) {
    RoutePlan plan = target == null ? null : target.routePlan;
    if (plan == null) {
      if (guidedRoutePlan != null) {
        resetGuidedRoutePlanState();
      }
      return false;
    }
    String identity = routeIdentity(target) + "|plan=" + plan.getId();
    if (plan == guidedRoutePlan && identity.equals(guidedRoutePlanIdentity)) {
      return true;
    }
    resetGuidedRoutePlanState();
    guidedRoutePlan = plan;
    guidedRoutePlanCursor = plan.initialCursor();
    guidedRoutePlanIdentity = identity;
    for (RoutePlan.Leg leg : plan.getLegs().values()) {
      collectRoutePlanPredicateRequirements(leg.getCompletionPredicate());
      collectRoutePlanPredicateRequirements(leg.getReadinessPredicate());
      for (RoutePlan.Branch branch : leg.getBranches()) {
        collectRoutePlanPredicateRequirements(branch.getCondition());
      }
    }
    routePlanObjectSceneSeedPending = true;
    routePlanNpcSceneSeedPending = true;
    routeEvidenceVersion++;
    return true;
  }

  private void collectRoutePlanPredicateRequirements(RouteCheck predicate) {
    if (predicate == null) {
      return;
    }
    switch (predicate.getKind()) {
      case TRACKED_OBJECT_ACTION:
      case OBSERVED_OBJECT_INTERACTION:
        if (predicate.getObjectActionSpec() != null) {
          RouteCheck.ObjectActionSpec spec = predicate.getObjectActionSpec();
          guidedRoutePlanObjectSpecs.add(spec);
          guidedRoutePlanObjectIds.addAll(spec.getObjectIds());
          if (spec.getObjectIds().isEmpty()) {
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
    for (RouteCheck child : predicate.getChildren()) {
      collectRoutePlanPredicateRequirements(child);
    }
  }

  private void resetGuidedRoutePlanState() {
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

  private void restartGuidedRoutePlanProgress() {
    if (guidedRoutePlan == null) {
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

  private RouteEvidence buildGuidedRouteEvidence(
      Player player, WorldPoint rawPlayerLocation) {
    if (guidedRoutePlan == null) {
      return RouteEvidence.empty();
    }
    if (routePlanObjectSceneSeedPending) {
      seedLoadedRoutePlanObjects();
    }
    if (routePlanNpcSceneSeedPending) {
      seedLoadedRoutePlanNpcs();
    }
    WorldPoint templateLocation = routeComparisonLocation(player, rawPlayerLocation);
    if (guidedLastPlayerLocation == null && rawPlayerLocation != null) {
      guidedLastPlayerLocation = rawPlayerLocation;
      guidedLastTemplateLocation = templateLocation;
      routeEvidenceVersion++;
    } else if (!java.util.Objects.equals(guidedLastPlayerLocation, rawPlayerLocation)
        || !java.util.Objects.equals(guidedLastTemplateLocation, templateLocation)) {
      guidedPreviousPlayerLocation = guidedLastPlayerLocation;
      guidedPreviousTemplateLocation = guidedLastTemplateLocation;
      guidedLastPlayerLocation = rawPlayerLocation;
      guidedLastTemplateLocation = templateLocation;
      routeEvidenceVersion++;
    }
    Set<Integer> currentlyVisibleWidgetComponents;
    if (guidedRoutePlanWidgetComponents.isEmpty()) {
      currentlyVisibleWidgetComponents = java.util.Collections.emptySet();
    } else {
      currentlyVisibleWidgetComponents = new LinkedHashSet<>();
      for (Integer componentId : guidedRoutePlanWidgetComponents) {
        Widget widget = client.getWidget(componentId);
        if (widget != null && !widget.isHidden()) {
          currentlyVisibleWidgetComponents.add(componentId);
        }
      }
    }
    if (!visibleRoutePlanWidgetComponents.equals(currentlyVisibleWidgetComponents)) {
      visibleRoutePlanWidgetComponents.clear();
      visibleRoutePlanWidgetComponents.addAll(currentlyVisibleWidgetComponents);
      routeEvidenceVersion++;
    }
    if (guidedRouteEvidenceCache != null
        && guidedRouteEvidenceCachePlan == guidedRoutePlan
        && guidedRouteEvidenceCacheVersion == routeEvidenceVersion) {
      return guidedRouteEvidenceCache;
    }
    WorldView worldView = client == null ? null : client.getTopLevelWorldView();
    RouteEvidence.Builder builder =
        RouteEvidence.builder()
            .previousWorldPoint(guidedPreviousPlayerLocation)
            .worldPoint(rawPlayerLocation)
            .previousTemplatePoint(guidedPreviousTemplateLocation)
            .templatePoint(templateLocation)
            .instanced(worldView != null && worldView.isInstance());
    for (Set<RouteEvidence.ObjectAction> actions : loadedRoutePlanObjectActions.values()) {
      for (RouteEvidence.ObjectAction action : actions) {
        builder.trackedObjectAction(action);
      }
    }
    for (RouteEvidence.ObjectAction action : observedRoutePlanInteractions) {
      builder.observedObjectInteraction(action);
    }
    for (Integer groupId : loadedRoutePlanWidgetGroups) {
      builder.visibleWidgetGroup(groupId);
    }
    for (Integer componentId : visibleRoutePlanWidgetComponents) {
      builder.visibleWidgetComponent(componentId);
    }
    for (NPC npc : loadedRoutePlanNpcs) {
      if (npc != null && npc.getName() != null && !npc.getName().trim().isEmpty()) {
        builder.exactNpc(npc.getName());
      }
    }
    guidedRouteEvidenceCache = builder.build();
    guidedRouteEvidenceCachePlan = guidedRoutePlan;
    guidedRouteEvidenceCacheVersion = routeEvidenceVersion;
    return guidedRouteEvidenceCache;
  }

  private RoutePlanResolution resolveGuidedRoutePlan(
      GuidedTaskTarget target, Player player, WorldPoint rawPlayerLocation) {
    if (!ensureGuidedRoutePlanState(target)) {
      return null;
    }
    if (observedRoutePlanInteractionExpiresAfterTick >= 0
        && gameTickSequence > observedRoutePlanInteractionExpiresAfterTick) {
      observedRoutePlanInteractions.clear();
      observedRoutePlanInteractionExpiresAfterTick = -1L;
      routeEvidenceVersion++;
    }
    RouteEvidence evidence = buildGuidedRouteEvidence(player, rawPlayerLocation);
    long evidenceVersion = routeEvidenceVersion;
    if (guidedRoutePlanArrivalEvidenceVersion == evidenceVersion) {
      return new RoutePlanResolution(RoutePlan.ProgressStatus.ARRIVED, null, null, false);
    }
    if (guidedRoutePlanEvaluation != null
        && guidedRoutePlanEvidenceVersion == evidenceVersion
        && !guidedRoutePlanEvaluation.hasAdvanced()) {
      return routePlanResolution(
          guidedRoutePlanEvaluation, routeComparisonLocation(player, rawPlayerLocation));
    }
    if (guidedRoutePlan.hasArrived(evidence)) {
      guidedRoutePlanArrivalEvidenceVersion = evidenceVersion;
      return new RoutePlanResolution(RoutePlan.ProgressStatus.ARRIVED, null, null, false);
    }
    RoutePlan.Leg previousLeg =
        guidedRoutePlan.getLeg(guidedRoutePlanCursor.getActiveLegId());
    guidedRoutePlanEvaluation = guidedRoutePlan.evaluate(guidedRoutePlanCursor, evidence);
    guidedRoutePlanCursor = guidedRoutePlanEvaluation.getCursor();
    guidedRoutePlanEvidenceVersion =
        guidedRoutePlanEvaluation.hasAdvanced() ? -1L : evidenceVersion;
    if (guidedRoutePlanEvaluation.hasAdvanced()
        && previousLeg != null
        && previousLeg.getKind() == RoutePlan.LegKind.MANUAL_INTERACTION) {
      observedRoutePlanInteractions.clear();
      observedRoutePlanInteractionExpiresAfterTick = -1L;
      routeEvidenceVersion++;
    }
    return routePlanResolution(
        guidedRoutePlanEvaluation, routeComparisonLocation(player, rawPlayerLocation));
  }

  private static RoutePlanResolution routePlanResolution(
      RoutePlan.Evaluation evaluation, WorldPoint playerLocation) {
    if (evaluation == null) {
      return null;
    }
    RoutePlan.Leg leg = evaluation.getActiveLeg();
    RoutePlan.RouteTarget target = leg == null ? null : leg.getTarget();
    if (target == null || !target.hasRoutingPoint()) {
      return new RoutePlanResolution(
          evaluation.getStatus(),
          leg,
          null,
          shouldPauseForRoutePlanForRegression(evaluation.getStatus(), playerLocation, target));
    }
    Set<WorldPoint> targets = new LinkedHashSet<>(target.getRoutingPoints());
    WorldPoint destination = null;
    int nearestDistance = Integer.MAX_VALUE;
    for (WorldPoint point : targets) {
      int candidateDistance = playerLocation == null ? 0 : distance(playerLocation, point);
      if (destination == null || candidateDistance < nearestDistance) {
        destination = point;
        nearestDistance = candidateDistance;
      }
    }
    return new RoutePlanResolution(
        evaluation.getStatus(),
        leg,
        new RouteStage(
            destination, targets, leg.getKind() != RoutePlan.LegKind.TERMINAL, false, false),
        shouldPauseForRoutePlanForRegression(evaluation.getStatus(), playerLocation, target));
  }

  static boolean shouldPauseForRoutePlanForRegression(
      RoutePlan.ProgressStatus status,
      WorldPoint playerLocation,
      RoutePlan.RouteTarget target) {
    if (status == RoutePlan.ProgressStatus.WAITING_FOR_BRANCH) {
      return true;
    }
    if (status != RoutePlan.ProgressStatus.WAITING_FOR_INTERACTION) {
      return false;
    }
    if (target == null || !target.hasRoutingPoint() || playerLocation == null) {
      return true;
    }
    for (WorldPoint routingPoint : target.getRoutingPoints()) {
      if (routingPoint != null
          && playerLocation.getPlane() == routingPoint.getPlane()
          && distance(playerLocation, routingPoint) <= target.getApproachRadius()) {
        return true;
      }
    }
    return false;
  }

  private void pauseForGuidedRoutePlan(RoutePlanResolution resolution, String prefix) {
    if (!guidedRouteWaitingForCheckpoint) {
      if (shortestPathBridge != null) {
        shortestPathBridge.clear();
      }
      taskAreaAnchor = null;
      guidedRouteWaitingForCheckpoint = true;
      clearGuidedTravelRecommendation();
    }
    guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
    RoutePlan.Leg leg = resolution == null ? null : resolution.leg;
    String guidance =
        leg == null
            ? "Waiting for the reviewed encounter arrival signal."
            : leg.getKind() == RoutePlan.LegKind.MANUAL_INTERACTION
                    && !leg.getInteractionInstruction().isEmpty()
                ? leg.getInteractionInstruction()
                : leg.getGuidance();
    setGuidedSessionStatus(
        (prefix == null || prefix.trim().isEmpty() ? "" : prefix.trim() + " — ") + guidance);
  }

  private String masterRouteIdentity(MasterRoutes.MasterRoute masterRoute) {
    if (masterRoute == null || masterRoute.getDestination() == null) {
      return "";
    }
    WorldPoint destination = masterRoute.getDestination();
    return "master|id="
        + masterRoute.getId()
        + "|destination="
        + destination.getX()
        + ","
        + destination.getY()
        + ","
        + destination.getPlane();
  }

  private boolean isInfernoPreparationTarget(GuidedTaskTarget target) {
    return target != null
        && RouteCatalog.requiresPreparationBank(
            target.taskName, target.location, target.isBoss());
  }

  private String infernoPreparationBankRouteIdentity(GuidedTaskTarget target) {
    if (!isInfernoPreparationTarget(target)) {
      return "";
    }
    WorldPoint bank = BankRoutes.getInfernoPreparationBankTarget();
    if (bank == null) {
      return "";
    }
    return "prepbank|"
        + routeIdentity(target)
        + "|destination="
        + bank.getX()
        + ","
        + bank.getY()
        + ","
        + bank.getPlane();
  }

  private static boolean isInfernoRegion(WorldPoint point) {
    if (point == null) {
      return false;
    }
    int regionId = ((point.getX() >> 6) << 8) | (point.getY() >> 6);
    return regionId == INFERNO_REGION_ID;
  }

  private boolean isAtInfernoPreparationBank(WorldPoint playerLocation) {
    WorldPoint bank = BankRoutes.getInfernoPreparationBankTarget();
    return playerLocation != null
        && bank != null
        && playerLocation.getPlane() == bank.getPlane()
        && distance(playerLocation, bank) <= INFERNO_PREP_BANK_ARRIVAL_RADIUS;
  }

  private boolean isInInfernoPreparationLocalArea(WorldPoint playerLocation) {
    WorldPoint bank = BankRoutes.getInfernoPreparationBankTarget();
    return playerLocation != null
        && bank != null
        && sameCoordinateLayer(playerLocation, bank)
        && playerLocation.getPlane() == bank.getPlane()
        && distance(playerLocation, bank) <= INFERNO_PREP_LOCAL_HANDOFF_RADIUS;
  }

  private WorldPoint findInfernoPreparationBankerTarget(WorldPoint playerLocation) {
    return findLoadedNamedNpcTarget(
        playerLocation, INFERNO_PREP_BANKER_NAMES, INFERNO_PREP_BANKER_DISCOVERY_RADIUS);
  }

  private static boolean isManualInfernoPreparationTeleport(TravelRouteMatch match) {
    if (match == null || match.item == null) {
      return false;
    }
    String item = normalizeTravelName(match.item.displayName);
    String destination = normalizeTravelName(match.destination);
    return destination.equals("mor ul rek")
        && (item.contains("ghommal s hilt") || item.contains("ghommal s avernic defender"));
  }

  private void invalidateTravelStateForProfileChange(String previousRouteIdentity) {
    travelCoordinator.invalidateRoute(previousRouteIdentity);
    guidedTravelRequestGeneration = -1L;
    guidedTravelRequestRouteIdentity = "";
    if (shortestPathBridge != null) {
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
      GuidedTaskTarget target, String reason) {
    if (!guidedSessionActive || !isInfernoPreparationTarget(target)) {
      return;
    }
    WorldPoint bank = BankRoutes.getInfernoPreparationBankTarget();
    String identity = infernoPreparationBankRouteIdentity(target);
    if (bank == null || identity.isEmpty()) {
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus("The reviewed Inferno preparation bank route is unavailable");
      return;
    }
    Player player = client.getLocalPlayer();
    WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
    ItemContainer liveBank = client.getItemContainer(InventoryID.BANK);
    WorldPoint loadedPrepBanker = findInfernoPreparationBankerTarget(playerLocation);
    if (liveBank != null
        && (isAtInfernoPreparationBank(playerLocation) || loadedPrepBanker != null)) {
      cacheBankContainer(liveBank);
      infernoPreparationBankReady = true;
      infernoPreparationManualTeleportPending = false;
      infernoPreparationHotVentPending = false;
      refreshSlayerData();
      createRecommendedBankTagNow();
      GuidedTaskTarget refreshedTarget = resolveGuidedTaskTarget();
      routeToInfernoEntryForGuidedSession(
          refreshedTarget != null ? refreshedTarget : target,
          "Already at the Inferno preparation bank");
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
    TravelRouteMatch authoredTravel = findInfernoPreparationTravelItem();
    boolean manualTeleport = isManualInfernoPreparationTeleport(authoredTravel);
    if (manualTeleport) {
      applyTravelRouteMatch(authoredTravel, identity);
      infernoPreparationManualTeleportPending = true;
      infernoPreparationHotVentPending = false;
      guidedBankTransportTracking = false;
      guidedTaskBankPickupPending = false;
      taskAreaAnchor = null;
      if (shortestPathBridge != null) {
        shortestPathBridge.clear();
      }
      refreshOpenBankTagForTravelSelection();
      refreshGuidedTeleportWidgetHighlights();
      setGuidedSessionStatus(
          reason
              + " - withdraw the Ghommal item shown above the 4 x 7, use Mor Ul Rek, and SlayerPlus"
              + " will start Shortest Path's local Zuk-bank route immediately after you land.");
      return;
    }
    if (authoredTravel != null) {
      applyProvisionalTravelRouteMatch(authoredTravel, identity);
    }
    infernoPreparationHotVentPending = true;
    guidedBankTransportTracking = true;
    if (authoredTravel == null) {
      showGuidedTravelRecommendation(
          "",
          "Shortest Path is selecting the best owned route it supports to the east Mor Ul Rek Hot"
              + " vent door.");
    }
    refreshOpenBankTagForTravelSelection();
    refreshGuidedTeleportWidgetHighlights();
    Set<WorldPoint> approachTargets =
        BankRoutes.getInfernoPreparationHotVentDoorTargets();
    boolean sent =
        shortestPathBridge != null
            && shortestPathBridge.routeToPreparationAccess(
                playerLocation, approachTargets, true, true, true);
    if (!sent) {
      travelCoordinator.invalidateRoute(identity);
      guidedBankTransportTracking = false;
      guidedTaskBankPickupPending = false;
      infernoPreparationHotVentPending = false;
      guidedTravelRequestGeneration = -1L;
      guidedTravelRequestRouteIdentity = "";
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus(
          "Unable to send the reviewed east Mor Ul Rek Hot vent door to Shortest Path");
      return;
    }
    setGuidedSessionStatus(
        reason
            + " — routing to the east Mor Ul Rek Hot vent door. Pass through the glowing barrier;"
            + " SlayerPlus will then start a separate collision-checked route to the Zuk bank.");
  }

  private void routeToInfernoEntryForGuidedSession(GuidedTaskTarget target, String reason) {
    if (!guidedSessionActive || !isInfernoPreparationTarget(target)) {
      return;
    }
    Player player = client.getLocalPlayer();
    WorldPoint rawPlayerLocation = player == null ? null : player.getWorldLocation();
    WorldPoint playerLocation = routeComparisonLocation(player, rawPlayerLocation);
    if (isInfernoRegion(playerLocation)) {
      confirmTaskAreaForGuidedSession();
      return;
    }
    String prepIdentity = infernoPreparationBankRouteIdentity(target);
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
    Set<String> entryNames =
        RouteCatalog.getPreparationEntryNpcNames(
            target.taskName, target.location, target.isBoss());
    WorldPoint entry = findInfernoEntryTarget(playerLocation, entryNames);
    guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
    if (entry == null) {
      guidedInfernoEntryDestination = null;
      WorldPoint entryApproach =
          RouteCatalog.getPreparationEntryApproachTarget(
              target.taskName, target.location, target.isBoss());
      boolean sent =
          shortestPathBridge != null
              && entryApproach != null
              && shortestPathBridge.routeToLocalTaskArea(
                  playerLocation, java.util.Collections.singleton(entryApproach), true);
      if (!sent) {
        taskAreaAnchor = null;
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
        setGuidedSessionStatus(
            "Unable to send the separate Inferno-entrance approach segment to Shortest Path");
        return;
      }
      taskAreaAnchor = entryApproach;
      showInfernoEntryNextStep(false);
      setGuidedSessionStatus(
          reason
              + " — 28-slot Inferno setup is ready. Routing separately to the reviewed Inferno"
              + " entrance approach; SlayerPlus will switch to an exact tile beside TzHaar-Ket-Keh"
              + " when he loads.");
      return;
    }
    guidedInfernoEntryDestination = entry;
    taskAreaAnchor = entry;
    boolean sent =
        shortestPathBridge != null
            && shortestPathBridge.routeToExactLocalTarget(playerLocation, entry, true);
    if (!sent) {
      setGuidedSessionStatus("Unable to send the exact Inferno entrance route to Shortest Path");
      return;
    }
    showInfernoEntryNextStep(false);
    setGuidedSessionStatus(
        reason
            + " — 28-slot Inferno setup is ready. Routing to a collision-checked tile beside"
            + " TzHaar-Ket-Keh; enter the Inferno from there.");
  }

  private void showInfernoEntryNextStep(boolean readyToEnter) {
    int state = readyToEnter ? 2 : 1;
    if (infernoEntryPromptState == state) {
      return;
    }
    infernoEntryPromptState = state;
    showGuidedTravelRecommendation(
        readyToEnter ? "Enter the Inferno" : "Follow Shortest Path → Inferno entrance",
        readyToEnter
            ? "Use TzHaar-Ket-Keh to begin the Inferno."
            : "The 28-slot setup is ready. Follow the route to TzHaar-Ket-Keh.");
  }

  private void routeToTaskForGuidedSession(boolean useBankItems, String reason) {
    if (!guidedSessionActive) {
      return;
    }
    GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
    Player player = client.getLocalPlayer();
    WorldPoint rawPlayerLocation = player == null ? null : player.getWorldLocation();
    WorldPoint playerLocation = routeComparisonLocation(player, rawPlayerLocation);
    if (target == null) {
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus(
          "No verified Shortest Path target is available for this task location");
      return;
    }
    if (confirmGuidedTaskAreaBeforePreparation(target)) {
      return;
    }
    if (teleportHighlighter != null) {
      if ("wyvern cave on fossil island".equals(normalizeTravelName(target.location))) {
        teleportHighlighter.setRouteGuidance("Digsite pendant", "Fossil Island", "Mushroom Meadow");
      } else {
        teleportHighlighter.setRouteGuidance("");
      }
    }
    if (currentMasterId != KRYSTILIA_MASTER_ID
        && RouteCatalog.isWilderness(target.location)) {
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus("Wilderness route blocked unless Krystilia assigned the task");
      return;
    }
    if (routeToSpellbookIfRequired("Selected Slayer method requires a different spellbook")) {
      return;
    }
    if (isInfernoPreparationTarget(target)) {
      if (!infernoPreparationBankReady) {
        routeToInfernoPreparationBankForGuidedSession(target, reason);
      } else {
        routeToInfernoEntryForGuidedSession(target, reason);
      }
      return;
    }
    guidedDynamicTaskDestination = null;
    guidedSurfaceEntranceDestination = null;
    clearRouteDiscoveryThrottle();
    guidedEntranceReached = false;
    guidedRouteWaitingForCheckpoint = false;
    RoutePlanResolution planResolution =
        target.routePlan == null ? null : resolveGuidedRoutePlan(target, player, rawPlayerLocation);
    if (planResolution != null && planResolution.hasArrived()) {
      confirmTaskAreaForGuidedSession();
      return;
    }
    if (planResolution != null && (planResolution.isWaiting() || planResolution.stage == null)) {
      pauseForGuidedRoutePlan(planResolution, reason);
      return;
    }
    if (planResolution == null
        && stageUnsupportedEncounterTeleport(target, playerLocation, useBankItems, reason)) {
      return;
    }
    RouteStage stage =
        planResolution == null
            ? resolveRouteStage(target, playerLocation, rawPlayerLocation)
            : planResolution.stage;
    if (stage == null || !stage.isValid()) {
      if (shortestPathBridge != null) {
        shortestPathBridge.clear();
      }
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus(
          "No verified entrance or current-scene monster checkpoint exists for "
              + target.location
              + ". SlayerPlus will not guess a destination.");
      return;
    }
    WorldPoint destination = stage.destination;
    Set<WorldPoint> routeTargets = stage.targets;
    boolean avoidWilderness = currentMasterId != KRYSTILIA_MASTER_ID;
    if (stage.exactNpc) {
      guidedDynamicTaskDestination = destination;
    }
    boolean preferDirectConsumableTeleport = false;
    String identity = routeIdentity(target);
    travelCoordinator.beginRoute(identity);
    TravelChoice cachedSelection = travelCoordinator.current();
    if (!useBankItems
        && cachedSelection.hasPhysicalItem()
        && findCarriedTravelItemForTransport(cachedSelection.getItemName()) == null) {
      travelCoordinator.invalidateRoute(identity);
      travelCoordinator.beginRoute(identity);
    }
    guidedTravelRequestGeneration = travelCoordinator.getGeneration();
    guidedTravelRequestRouteIdentity = identity;
    restoreGuidedTeleportWidgetHighlights();
    clearTeleportHighlightCaptures();
    TravelRouteMatch authoredTravel = findAuthoredRouteTravelItem(target);
    if (!useBankItems
        && authoredTravel != null
        && findCarriedTravelItemForTransport(authoredTravel.item.displayName) == null) {
      authoredTravel = null;
    }
    if (authoredTravel != null) {
      if (isWhispererRingTravelForRegression(
          target.taskName,
          target.location,
          authoredTravel.item.displayName,
          authoredTravel.destination)) {
        applyTravelRouteMatch(authoredTravel, identity);
        whispererRingTeleportPending = true;
        guidedTaskBankPickupPending = useBankItems;
        guidedBankTransportTracking = true;
        guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
        taskAreaAnchor = WHISPERER_LASSAR_RING_LANDING;
        boolean ringStageSent =
            shortestPathBridge != null
                && shortestPathBridge.routeToTaskArea(
                    playerLocation,
                    java.util.Collections.singleton(WHISPERER_LASSAR_RING_LANDING),
                    currentMasterId != KRYSTILIA_MASTER_ID,
                    useBankItems,
                    useBankItems,
                    true);
        if (!ringStageSent) {
          guidedBankTransportTracking = false;
          guidedTaskBankPickupPending = false;
        }
        refreshOpenBankTagForTravelSelection();
        refreshGuidedTeleportWidgetHighlights();
        setGuidedSessionStatus(
            ringStageSent
                ? reason + " — use Ring of shadows -> Lassar Undercity."
                : reason
                    + " — use Ring of shadows -> Lassar Undercity; "
                    + "Shortest Path could not draw the teleport leg.");
        return;
      }
      if (TravelRoutes.locksAuthoredTravelItem(target.location)) {
        applyTravelRouteMatch(authoredTravel, identity);
        if (shouldPreferDirectConsumableTeleportForRegression(
            target.location, authoredTravel.item.displayName)) {
          preferDirectConsumableTeleport = true;
        }
      } else {
        applyProvisionalTravelRouteMatch(authoredTravel, identity);
      }
    } else if (useBankItems) {
      showGuidedTravelRecommendation(
          "", "Shortest Path is comparing the teleport items in your bank.");
    }
    if (!useBankItems
        && currentTravelSelection().hasPhysicalItem()
        && findCarriedTravelItemForTransport(currentTravelSelection().getItemName()) != null
        && shouldPreferDirectConsumableTeleportForRegression(
            target.location, currentTravelSelection().getItemName())) {
      preferDirectConsumableTeleport = true;
    }
    whispererRingTeleportPending = false;
    whispererCathedralTeleportPending = false;
    guidedBankTransportTracking = true;
    guidedTaskBankPickupPending = useBankItems;
    refreshOpenBankTagForTravelSelection();
    if (preferDirectConsumableTeleport
        && !isAraxxorSpiderTeleportLandingForRegression(
            target.taskName,
            target.location,
            playerLocation,
            target.profile.getSurfaceAccess(),
            target.isInsideEncounterArea(playerLocation))) {
      araxxorSpiderTeleportPending = true;
      guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
      guidedBankTransportTracking = false;
      guidedTaskBankPickupPending = false;
      guidedPostTransportContinuationPending = true;
      WorldPoint caveEntrance = target.profile.getSurfaceAccess();
      taskAreaAnchor = caveEntrance;
      boolean fallbackSent =
          shortestPathBridge != null
              && caveEntrance != null
              && shortestPathBridge.routeToTaskAccess(
                  shortestPathStart(rawPlayerLocation),
                  RouteCatalog.targetArea(caveEntrance, 2),
                  avoidWilderness,
                  false,
                  false,
                  true);
      refreshOpenBankTagForTravelSelection();
      refreshGuidedTeleportWidgetHighlights();
      setGuidedSessionStatus(
          reason
              + " - use the highlighted Spider cave teleport. "
              + (fallbackSent
                  ? "Shortest Path is keeping the cave entrance active and will rebase after you"
                        + " land."
                  : "SlayerPlus will start the cave-entrance walk after you land."));
      return;
    }
    araxxorSpiderTeleportPending = false;
    guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
    refreshGuidedTeleportWidgetHighlights();
    boolean localTaskRoute = shouldUseLocalTaskRouteForRegression(playerLocation, destination);
    if (shouldRetireTravelRecommendationForLocalRoute(
        localTaskRoute, guidedBankTransportTracking, currentTravelSelection().hasPhysicalItem())) {
      clearGuidedTravelRecommendation();
    }
    if (localTaskRoute) {
      guidedBankTransportTracking = false;
      guidedTaskBankPickupPending = false;
      guidedPostTransportContinuationPending = false;
    } else {
      guidedBankTransportTracking = true;
      guidedTaskBankPickupPending = useBankItems;
      guidedPostTransportContinuationPending = requiresPostTransportContinuation(target, stage);
    }
    WorldPoint shortestPathPlayerStart = shortestPathStart(rawPlayerLocation);
    boolean sent =
        shortestPathBridge != null
            && (localTaskRoute
                ? shortestPathBridge.routeToLocalTaskArea(
                    shortestPathPlayerStart,
                    routeTargets,
                    avoidWilderness,
                    shouldEnableAgilityShortcutsForStage(target, destination))
                : shortestPathBridge.routeToTaskArea(
                    shortestPathPlayerStart,
                    routeTargets,
                    avoidWilderness,
                    useBankItems,
                    useBankItems,
                    true,
                    preferDirectConsumableTeleport));
    if (!sent) {
      guidedBankTransportTracking = false;
      guidedTaskBankPickupPending = false;
      guidedPostTransportContinuationPending = false;
      guidedTravelRequestGeneration = -1L;
      guidedTravelRequestRouteIdentity = "";
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus("Unable to send the task route to Shortest Path");
      return;
    }
    taskAreaAnchor = destination;
    taskArrivalGraceTicks = 0;
    taskAreaExitTicks = 0;
    if (planResolution != null) {
      RoutePlan.Leg planLeg = planResolution.leg;
      RoutePlan.RouteTarget planTarget = planLeg == null ? null : planLeg.getTarget();
      String targetLabel = planTarget == null ? target.location : planTarget.getLabel();
      String routeInstruction =
          useBankItems
              ? " — withdraw the highlighted teleport, then follow Shortest Path to "
              : " — follow Shortest Path to ";
      String preparationWarning =
          currentPreparation.isActive() && !currentPreparation.isReady()
              ? " " + currentPreparation.getRouteWarning()
              : "";
      setGuidedSessionStatus(
          reason
              + routeInstruction
              + targetLabel
              + ". "
              + (planLeg == null ? "" : planLeg.getGuidance())
              + preparationWarning);
      return;
    }
    boolean routingToEntrance = stage.entrance;
    boolean routingToExactNpc = stage.exactNpc;
    boolean routingToMeiyerditchShortcut = isMeiyerditchShortcutStage(target, destination);
    boolean routingToWhispererSinkhole = isWhispererSinkholeStage(target, destination);
    boolean routingToWhispererTeleporter =
        WHISPERER_PALACE_TELEPORTER.equals(destination)
            && whispererCathedralTeleporterApproachForRegression(
                    target.taskName, target.location, playerLocation)
                != null;
    whispererCathedralTeleportPending = routingToWhispererTeleporter;
    boolean routingToAquaniteSailing =
        AQUANITE_PORT_ROBERTS_MOORING.equals(destination)
            && "ynysdail cavern".equals(normalizeTravelName(target.location));
    String destinationText =
        routingToEntrance
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
                ? "a walkable tile beside the nearest exact " + formatName(target.taskName)
                : stage.learnedCheckpoint
                    ? "the locally verified " + formatName(target.taskName) + " checkpoint"
                    : target.location + " interior checkpoint";
    String routeInstruction =
        useBankItems
            ? " — Shortest Path can use teleports in your bank. Withdraw the highlighted teleport"
                  + " and follow the route to "
            : " — follow the route back to ";
    String transition =
        routingToEntrance
            ? routingToWhispererSinkhole
                ? " Descend into Lassar Undercity; SlayerPlus will continue to the Cathedral after"
                      + " it loads."
                : routingToWhispererTeleporter
                    ? " Use the teleporter and choose The Cathedral; SlayerPlus will start the"
                          + " walking route after you arrive."
                    : routingToMeiyerditchShortcut
                        ? " Enter the cave manually; the shortcut must have been cleared once from"
                              + " the laboratory side with 78 Mining."
                        : routingToAquaniteSailing
                            ? " Summon or board your player-owned boat and sail to Ynysdail."
                                  + " SlayerPlus will resume at the island landing and route to the"
                                  + " cavern."
                            : " " + target.profile.getTransitionInstruction()
            : "";
    String preparationWarning =
        currentPreparation.isActive() && !currentPreparation.isReady()
            ? " " + currentPreparation.getRouteWarning()
            : "";
    setGuidedSessionStatus(
        reason + routeInstruction + destinationText + "." + transition + preparationWarning);
  }

  private boolean confirmGuidedTaskAreaBeforePreparation(GuidedTaskTarget target) {
    if (!guidedSessionActive || target == null) {
      return false;
    }
    Player player = client.getLocalPlayer();
    WorldPoint rawPlayerLocation = player == null ? null : player.getWorldLocation();
    if (rawPlayerLocation == null) {
      return false;
    }
    WorldPoint comparisonLocation = routeComparisonLocation(player, rawPlayerLocation);
    if (isInfernoPreparationTarget(target) && isInfernoRegion(comparisonLocation)) {
      confirmTaskAreaForGuidedSession();
      return true;
    }
    if (target.routePlan == null) {
      return false;
    }
    ensureGuidedRoutePlanState(target);
    RouteEvidence evidence = buildGuidedRouteEvidence(player, rawPlayerLocation);
    if (guidedRoutePlan == null || !guidedRoutePlan.hasArrived(evidence)) {
      return false;
    }
    confirmTaskAreaForGuidedSession();
    return true;
  }

  private boolean requiresPostTransportContinuation(GuidedTaskTarget target, RouteStage stage) {
    return target != null
        && stage != null
        && (target.routePlan != null
            || stage.entrance
            || target.isStaged()
            || target.requiresLoadedNpc()
            || (target.profile != null && target.profile.isAccessThenNpc())
            || selectedCannonPosition(target) != null
            || selectedBarragePosition(target) != null);
  }

  static boolean shouldPreferDirectConsumableTeleportForRegression(
      String location, String itemName) {
    return "morytania spider cave".equals(normalizeTravelName(location))
        && "spider cave teleport".equals(normalizeTravelName(itemName));
  }

  static boolean isAraxxorSpiderTeleportLandingForRegression(
      String taskName,
      String location,
      WorldPoint playerLocation,
      WorldPoint surfaceAccess,
      boolean insideEncounterArea) {
    return "araxxor".equals(normalizeTravelName(taskName))
        && "morytania spider cave".equals(normalizeTravelName(location))
        && playerLocation != null
        && (insideEncounterArea
            || distance(playerLocation, surfaceAccess) <= ARAXXOR_SPIDER_TELEPORT_LANDING_RADIUS);
  }

  private boolean stageUnsupportedEncounterTeleport(
      GuidedTaskTarget target, WorldPoint playerLocation, boolean useBankItems, String reason) {
    if (target == null || !isUnsupportedEncounterTeleportLocation(target.location)) {
      return false;
    }
    boolean skotizo = normalizeTravelName(target.location).equals("skotizo s lair");
    boolean tormented = normalizeTravelName(target.location).equals("ancient guthixian temple");
    if (tormented) {
      return stageTormentedDemonRoute(target, playerLocation, useBankItems, reason);
    }
    if (skotizo
        && (!isOnCatacombsCoordinateLayer(playerLocation)
            || isOnSkotizoLairCoordinateLayer(playerLocation))) {
      return false;
    }
    String identity = routeIdentity(target);
    if (useBankItems || !travelCoordinator.ownsCurrentRoute(identity)) {
      travelCoordinator.beginRoute(identity);
      guidedTravelRequestGeneration = travelCoordinator.getGeneration();
      guidedTravelRequestRouteIdentity = identity;
    }
    TravelRouteMatch authoredTravel =
        skotizo
            ? encounterKeyTravelMatch("dark totem", "Skotizo's Lair")
            : findAuthoredRouteTravelItem(target);
    if (authoredTravel != null
        && isUnsupportedEncounterTeleportItem(target.location, authoredTravel.item.displayName)) {
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
    if (skotizo && distance(playerLocation, SKOTIZO_CATACOMBS_ALTAR) > 3) {
      if (!SKOTIZO_CATACOMBS_ALTAR.equals(taskAreaAnchor)) {
        taskAreaAnchor = SKOTIZO_CATACOMBS_ALTAR;
        if (shortestPathBridge != null) {
          shortestPathBridge.routeToExactLocalTarget(playerLocation, SKOTIZO_CATACOMBS_ALTAR, true);
        }
      }
      refreshOpenBankTagForTravelSelection();
      refreshGuidedTeleportWidgetHighlights();
      setGuidedSessionStatus(
          reason
              + " - follow Shortest Path to the central Catacombs altar, then use the highlighted"
              + " Dark totem.");
      return true;
    }
    taskAreaAnchor = null;
    if (shortestPathBridge != null) {
      shortestPathBridge.clear();
    }
    refreshOpenBankTagForTravelSelection();
    refreshGuidedTeleportWidgetHighlights();
    TravelChoice selection = currentTravelSelection();
    boolean hasRequiredItem =
        selection.hasPhysicalItem()
            && isUnsupportedEncounterTeleportItem(target.location, selection.getItemName());
    String instruction =
        normalizeTravelName(target.location).equals("skotizo s lair")
            ? "Use the highlighted Dark totem on the Catacombs altar. Shortest Path will resume"
                  + " inside Skotizo's lair."
            : "Use the highlighted Guthixian temple teleport. Shortest Path will resume inside"
                  + " beside the Tormented demons.";
    setGuidedSessionStatus(
        hasRequiredItem
            ? reason + " - " + instruction
            : "The required manual entrance item was not found. " + instruction);
    return true;
  }

  private void resetTormentedRouteProgress() {
    tormentedLightCreatureAttracted = false;
    tormentedTempleReached = false;
    guidedTormentedChasmTarget = null;
  }

  private boolean stageTormentedDemonRoute(
      GuidedTaskTarget target, WorldPoint playerLocation, boolean useBankItems, String reason) {
    if (TORMENTED_MANUAL_CHASM_STAGE.equals(taskAreaAnchor)
        && !isOnTearsOfGuthixUpperLayer(playerLocation)
        && !isOnAncientGuthixianTempleLayer(playerLocation)) {
      taskAreaAnchor = null;
      resetTormentedRouteProgress();
    }
    if (TORMENTED_DIRECT_SCROLL_STAGE.equals(taskAreaAnchor)
        && !isOnAncientGuthixianTempleLayer(playerLocation)) {
      taskAreaAnchor = null;
    }
    if (tormentedTempleReached && !isOnAncientGuthixianTempleLayer(playerLocation)) {
      resetTormentedRouteProgress();
    }
    WorldPoint loadedDemonTarget =
        gameTickSequence == 0 || gameTickSequence % ROUTE_DISCOVERY_INTERVAL_TICKS == 0
            ? findLoadedNamedNpcTarget(
                playerLocation,
                java.util.Collections.singleton("tormented demon"),
                DYNAMIC_TASK_NPC_RADIUS)
            : null;
    if (loadedDemonTarget != null) {
      tormentedTempleReached = true;
      return false;
    }
    if (tormentedTempleReached) {
      return false;
    }
    if (TORMENTED_DIRECT_SCROLL_STAGE.equals(taskAreaAnchor)
        && isOnAncientGuthixianTempleLayer(playerLocation)) {
      return false;
    }
    if (TORMENTED_MANUAL_CHASM_STAGE.equals(taskAreaAnchor)) {
      guidedBankTransportTracking = false;
      guidedTaskBankPickupPending = false;
      guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
      if (isOnTearsOfGuthixUpperLayer(playerLocation)) {
        setGuidedSessionStatus(
            tormentedLightCreatureAttracted
                ? "The light creature is ready — click it and choose Into the chasm."
                : "At the light creatures — use the lit Sapphire lantern on one.");
        return true;
      }
      routeThroughTormentedLowerChasm(playerLocation);
      return true;
    }
    String identity = routeIdentity(target);
    TravelRouteMatch directScroll =
        encounterKeyTravelMatch("guthixian temple teleport", "Ancient Guthixian Temple");
    if (directScroll != null
        && !mayUseEncounterTravelMatch(
            useBankItems,
            findCarriedTravelItemForTransport(directScroll.item.displayName) != null)) {
      directScroll = null;
    }
    if (directScroll != null) {
      TravelChoice selection = currentTravelSelection();
      boolean selectionChanged =
          !selection.hasPhysicalItem()
              || !normalizeTravelName(selection.getItemName())
                  .contains("guthixian temple teleport");
      if (useBankItems || !travelCoordinator.ownsCurrentRoute(identity)) {
        travelCoordinator.beginRoute(identity);
        guidedTravelRequestGeneration = travelCoordinator.getGeneration();
        guidedTravelRequestRouteIdentity = identity;
      }
      if (selectionChanged) {
        applyTravelRouteMatch(directScroll, identity);
        refreshOpenBankTagForTravelSelection();
        refreshGuidedTeleportWidgetHighlights();
      }
      guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
      guidedBankTransportTracking = false;
      guidedTaskBankPickupPending = false;
      guidedPostTransportContinuationPending = false;
      if (!TORMENTED_DIRECT_SCROLL_STAGE.equals(taskAreaAnchor)) {
        taskAreaAnchor = TORMENTED_DIRECT_SCROLL_STAGE;
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
      }
      setGuidedSessionStatus(
          reason
              + " - use the highlighted Guthixian temple teleport. Shortest Path will resume once"
              + " the temple loads.");
      return true;
    }
    if (isOnTearsOfGuthixUpperLayer(playerLocation)) {
      guidedBankTransportTracking = false;
      guidedTaskBankPickupPending = false;
      guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
      WorldPoint loadedLightCreatureTarget =
          findLoadedNamedNpcTarget(playerLocation, TORMENTED_LIGHT_CREATURE_NAMES, 32);
      WorldPoint lightCreatureTarget =
          loadedLightCreatureTarget == null
              ? TORMENTED_LIGHT_CREATURE_APPROACH
              : loadedLightCreatureTarget;
      if (distance(playerLocation, lightCreatureTarget) <= 6) {
        taskAreaAnchor = TORMENTED_MANUAL_CHASM_STAGE;
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
        if (tormentedLightCreatureAttracted) {
          showGuidedTravelRecommendation(
              "Light creature → Into the chasm",
              "Click the attracted light creature and choose Into the chasm.");
          setGuidedSessionStatus(
              "The light creature is ready — click it and choose Into the chasm.");
        } else {
          showGuidedTravelRecommendation(
              "Lit Sapphire lantern", "Use it on a light creature, then descend into the chasm.");
          setGuidedSessionStatus("At the light creatures — use the lit Sapphire lantern on one.");
        }
        return true;
      }
      if (shouldSubmitTormentedPath(false, taskAreaAnchor, lightCreatureTarget)) {
        taskAreaAnchor = lightCreatureTarget;
        if (shortestPathBridge != null) {
          shortestPathBridge.routeToExactLocalTarget(playerLocation, lightCreatureTarget, true);
        }
      }
      setGuidedSessionStatus(
          "Follow Shortest Path to a walkable tile beside the nearest light creature; a lit"
              + " Sapphire lantern is required.");
      return true;
    }
    boolean routeChanged =
        useBankItems
            || guidedTaskBankPickupPending
            || !travelCoordinator.ownsCurrentRoute(identity);
    if (routeChanged) {
      travelCoordinator.beginRoute(identity);
      guidedTravelRequestGeneration = travelCoordinator.getGeneration();
      guidedTravelRequestRouteIdentity = identity;
      TravelRouteMatch gamesNecklace = encounterKeyTravelMatch("games necklace", "Tears of Guthix");
      if (gamesNecklace != null) {
        applyProvisionalTravelRouteMatch(gamesNecklace, target);
      } else {
        showGuidedTravelRecommendation(
            "",
            "Shortest Path is comparing a carried Games necklace, a POH jewellery box route, and"
                + " the overland fallback.");
      }
      refreshOpenBankTagForTravelSelection();
      refreshGuidedTeleportWidgetHighlights();
    }
    guidedSessionPhase = GuidedSessionPhase.ROUTING_TO_TASK;
    guidedBankTransportTracking = true;
    guidedTaskBankPickupPending = useBankItems;
    guidedPostTransportContinuationPending = false;
    if (shouldSubmitTormentedPath(routeChanged, taskAreaAnchor, TORMENTED_TEARS_LANDING)) {
      taskAreaAnchor = TORMENTED_TEARS_LANDING;
      if (shortestPathBridge != null) {
        shortestPathBridge.routeToTaskArea(
            playerLocation,
            java.util.Collections.singleton(TORMENTED_TEARS_LANDING),
            true,
            useBankItems,
            useBankItems,
            true);
      }
    }
    setGuidedSessionStatus(
        reason
            + " - routing to Tears of Guthix. Fastest available order is direct Games necklace, POH"
            + " jewellery box, then the overland cave route. Bring the lit Sapphire lantern shown"
            + " in the Bank Tag.");
    return true;
  }

  static boolean mayUseEncounterTravelMatch(boolean bankItemsAvailable, boolean carried) {
    return bankItemsAvailable || carried;
  }

  private void routeThroughTormentedLowerChasm(WorldPoint playerLocation) {
    seedLoadedTormentedChasmObjectsIfNeeded();
    WorldPoint objectLocation =
        nearestSamePlaneLocation(playerLocation, loadedTormentedChasmWallLocations, 64);
    boolean wallStep = objectLocation != null;
    if (objectLocation == null) {
      objectLocation =
          nearestSamePlaneLocation(playerLocation, loadedTormentedChasmExitLocations, 64);
    }
    if (objectLocation == null) {
      if (shortestPathBridge != null) {
        shortestPathBridge.clear();
      }
      guidedTormentedChasmTarget = null;
      showGuidedTravelRecommendation(
          "Continue south", "The next wall or skull entrance has not loaded yet.");
      setGuidedSessionStatus(
          "Inside the lower chasm — continue south until the next stone wall loads.");
      return;
    }
    WorldPoint interactionTarget =
        nearestWalkableEntranceInteractionTile(objectLocation, playerLocation);
    if (interactionTarget == null) {
      return;
    }
    if (!interactionTarget.equals(guidedTormentedChasmTarget)) {
      guidedTormentedChasmTarget = interactionTarget;
      if (shortestPathBridge != null) {
        shortestPathBridge.routeToExactLocalTarget(playerLocation, interactionTarget, true);
      }
    }
    String action = wallStep ? "Climb the stone wall" : "Enter the skull";
    showGuidedTravelRecommendation(
        action,
        distance(playerLocation, interactionTarget) <= 3
            ? "Use the highlighted interaction, then the next route segment will appear."
            : "Follow Shortest Path to the next required interaction.");
    setGuidedSessionStatus(
        distance(playerLocation, interactionTarget) <= 3
            ? action + "."
            : "Follow Shortest Path to " + action.toLowerCase(Locale.ENGLISH) + ".");
  }

  private void seedLoadedTormentedChasmObjectsIfNeeded() {
    if (!tormentedChasmSceneSeedPending || client == null) {
      return;
    }
    tormentedChasmSceneSeedPending = false;
    WorldView worldView = client.getTopLevelWorldView();
    Scene scene = worldView == null ? null : worldView.getScene();
    if (scene == null || scene.getTiles() == null) {
      tormentedChasmSceneSeedPending = true;
      return;
    }
    for (Tile[][] planeTiles : scene.getTiles()) {
      if (planeTiles == null) {
        continue;
      }
      for (Tile[] column : planeTiles) {
        if (column == null) {
          continue;
        }
        for (Tile tile : column) {
          if (tile == null) {
            continue;
          }
          GameObject[] gameObjects = tile.getGameObjects();
          if (gameObjects != null) {
            for (GameObject object : gameObjects) {
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

  private void observeLoadedTormentedChasmObject(TileObject object) {
    if (!guidedSessionActive
        || currentTaskVariant != TaskVariant.TORMENTED_DEMONS
        || object == null
        || client == null
        || object.getWorldLocation() == null) {
      return;
    }
    try {
      ObjectComposition composition = client.getObjectDefinition(object.getId());
      if (composition != null && composition.getImpostorIds() != null) {
        ObjectComposition impostor = composition.getImpostor();
        if (impostor != null) {
          composition = impostor;
        }
      }
      if (composition == null) {
        return;
      }
      if (isTormentedChasmWallForRegression(composition.getName(), composition.getActions())) {
        loadedTormentedChasmWallLocations.add(object.getWorldLocation());
      } else if (isTormentedChasmExitForRegression(
          composition.getName(), composition.getActions())) {
        loadedTormentedChasmExitLocations.add(object.getWorldLocation());
      }
    } catch (RuntimeException ignored) {
    }
  }

  private void forgetLoadedTormentedChasmObject(TileObject object) {
    if (object == null || object.getWorldLocation() == null) {
      return;
    }
    loadedTormentedChasmWallLocations.remove(object.getWorldLocation());
    loadedTormentedChasmExitLocations.remove(object.getWorldLocation());
  }

  static boolean isTormentedChasmWallForRegression(String name, String[] actions) {
    String normalizedName = normalizeTravelName(name);
    return normalizedName.contains("wall") && hasActionContaining(actions, "climb");
  }

  static boolean isTormentedChasmExitForRegression(String name, String[] actions) {
    String normalizedName = normalizeTravelName(name);
    return (hasActionContaining(actions, "enter") || hasActionContaining(actions, "climb through"))
        && (normalizedName.contains("skull")
            || normalizedName.contains("cave")
            || normalizedName.contains("entrance")
            || normalizedName.contains("opening"));
  }

  private static boolean hasActionContaining(String[] actions, String expected) {
    if (actions == null || expected == null) {
      return false;
    }
    String normalizedExpected = normalizeTravelName(expected);
    for (String action : actions) {
      if (normalizeTravelName(action).contains(normalizedExpected)) {
        return true;
      }
    }
    return false;
  }

  private static WorldPoint nearestSamePlaneLocation(
      WorldPoint origin, Set<WorldPoint> locations, int maximumRadius) {
    if (origin == null || locations == null) {
      return null;
    }
    WorldPoint nearest = null;
    int nearestDistance = Integer.MAX_VALUE;
    for (WorldPoint location : locations) {
      if (location == null || location.getPlane() != origin.getPlane()) {
        continue;
      }
      int candidateDistance = distance(origin, location);
      if (candidateDistance <= maximumRadius && candidateDistance < nearestDistance) {
        nearest = location;
        nearestDistance = candidateDistance;
      }
    }
    return nearest;
  }

  static boolean isOnTearsOfGuthixLayer(WorldPoint point) {
    return point != null && point.getRegionID() == 12948;
  }

  static boolean isOnTearsOfGuthixUpperLayer(WorldPoint point) {
    return isOnTearsOfGuthixLayer(point) && point.getPlane() == 2;
  }

  static boolean isOnAncientGuthixianTempleLayer(WorldPoint point) {
    return point != null
        && point.getPlane() == 0
        && point.getX() >= 4032
        && point.getX() <= 4223
        && point.getY() >= 4352
        && point.getY() <= 4543;
  }

  static WorldPoint tormentedTearsLandingForRegression() {
    return TORMENTED_TEARS_LANDING;
  }

  static WorldPoint tormentedLightCreatureApproachForRegression() {
    return TORMENTED_LIGHT_CREATURE_APPROACH;
  }

  static boolean shouldSubmitTormentedPath(
      boolean routeChanged, WorldPoint currentAnchor, WorldPoint destination) {
    return routeChanged || destination == null || !destination.equals(currentAnchor);
  }

  private TravelRouteMatch encounterKeyTravelMatch(String itemFamily, String destination) {
    TravelItemMatch item = findOwnedTravelItem(itemFamily);
    if (item == null) {
      return null;
    }
    return new TravelRouteMatch(item, destination, java.util.Collections.singleton(itemFamily));
  }

  static boolean isOnCatacombsCoordinateLayer(WorldPoint point) {
    return point != null
        && point.getPlane() == 0
        && point.getX() >= 1536
        && point.getX() <= 1791
        && point.getY() >= 9856
        && point.getY() <= 10239;
  }

  static boolean isOnSkotizoLairCoordinateLayer(WorldPoint point) {
    return point != null && point.getRegionID() == SKOTIZO_LAIR_REGION_ID;
  }

  static WorldPoint skotizoCatacombsAltarForRegression() {
    return SKOTIZO_CATACOMBS_ALTAR;
  }

  private static boolean isUnsupportedEncounterTeleportLocation(String location) {
    String normalized = normalizeTravelName(location);
    return normalized.equals("ancient guthixian temple") || normalized.equals("skotizo s lair");
  }

  static boolean isUnsupportedEncounterTeleportItem(String location, String itemName) {
    String normalizedLocation = normalizeTravelName(location);
    String normalizedItem = normalizeTravelName(itemName);
    return (normalizedLocation.equals("ancient guthixian temple")
            && normalizedItem.contains("guthixian temple teleport"))
        || (normalizedLocation.equals("skotizo s lair") && normalizedItem.equals("dark totem"));
  }

  private void routeToMasterForGuidedSession() {
    routeToMasterForGuidedSession(false);
  }

  private void routeToMasterForGuidedSession(boolean useBankItems) {
    if (!guidedSessionActive) {
      return;
    }
    clearGuidedTravelRecommendation();
    int masterId = getRoutingMasterId();
    MasterRoutes.MasterRoute masterRoute = MasterRoutes.find(masterId);
    if (masterRoute == null) {
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus("Task complete, but the last-used Slayer master route is unavailable");
      return;
    }
    TravelRouteMatch carriedMasterTeleport = findCarriedMasterReturnTeleport(masterRoute);
    TravelRouteMatch bankMasterTeleport =
        bankScanned ? findBankMasterReturnTeleport(masterRoute) : null;
    GuideLifecycle.MasterReturnPlan returnPlan =
        GuideLifecycle.masterReturnPlan(
            carriedMasterTeleport != null, bankScanned, bankMasterTeleport != null);
    if (!useBankItems
        && returnPlan == GuideLifecycle.MasterReturnPlan.BANK_OWNED_TELEPORT) {
      routeToBankForGuidedSession(
          "Task complete — the fastest Slayer-master teleport is in your bank");
      guidedMasterBankPickupPending = guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_BANK;
      return;
    }
    TravelRouteMatch selectedMasterTeleport =
        carriedMasterTeleport != null
            ? carriedMasterTeleport
            : useBankItems ? bankMasterTeleport : null;
    String identity = masterRouteIdentity(masterRoute);
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
            ? "Shortest Path is selecting the fastest carried or bank-owned teleport for your"
                  + " Slayer master."
            : "Shortest Path is selecting the carried teleport for your Slayer master.");
    Player player = client.getLocalPlayer();
    WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
    if (masterId == 10 && isMortimerCavernForRegression(playerLocation)) {
      mortimerSlayerRingTeleportPending = false;
      boolean localSent =
          shortestPathBridge != null
              && shortestPathBridge.routeToExactLocalTarget(
                  playerLocation, masterRoute.getDestination(), true);
      setGuidedSessionStatus(
          localSent
              ? "Inside Wyrmscraig Cavern — routing locally to Mortimer."
              : "Inside Wyrmscraig Cavern, but the local route to Mortimer could not be drawn.");
      return;
    }
    if (masterId == 10 && selectedMasterTeleport != null) {
      applyTravelRouteMatch(selectedMasterTeleport, identity);
      mortimerSlayerRingTeleportPending = true;
      taskAreaAnchor = MORTIMER_SLAYER_RING_LANDING;
      refreshGuidedTeleportWidgetHighlights();
      setGuidedSessionStatus(
          useBankItems
              ? "Withdraw the highlighted Slayer ring, then use Wyrmscraig Cavern. Routing will"
                    + " resume inside to Mortimer."
              : "Use the highlighted Slayer ring -> Wyrmscraig Cavern. Routing will resume inside"
                    + " to Mortimer.");
      return;
    }
    if (masterId == 10) {
      travelCoordinator.invalidateRoute(identity);
      guidedBankTransportTracking = false;
      guidedTravelRequestGeneration = -1L;
      guidedTravelRequestRouteIdentity = "";
      setGuidedSessionStatus(
          "Carry a Slayer ring with the Wyrmscraig Cavern teleport to return to Mortimer.");
      return;
    }
    if (selectedMasterTeleport != null) {
      applyProvisionalTravelRouteMatch(selectedMasterTeleport, identity);
    }
    boolean sent =
        shortestPathBridge != null
            && (useBankItems
                ? shortestPathBridge.routeToAny(
                    shortestPathStart(playerLocation),
                    singletonRouteTarget(masterRoute.getDestination()),
                    masterId != KRYSTILIA_MASTER_ID,
                    true,
                    true)
                : shortestPathBridge.routeToTrackedInventory(
                    masterRoute.getDestination(), masterId != KRYSTILIA_MASTER_ID));
    if (!sent) {
      travelCoordinator.invalidateRoute(identity);
      guidedBankTransportTracking = false;
      guidedTravelRequestGeneration = -1L;
      guidedTravelRequestRouteIdentity = "";
      guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
      setGuidedSessionStatus("Unable to send the Slayer master route to Shortest Path");
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
                    : " using teleports you carry."));
  }

  private String pointBoostRouteStatus(MasterRoutes.MasterRoute masterRoute) {
    BoostCoordinator.Decision decision =
        pointBoostDecision(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED));
    return "Point boosting — routing to "
        + masterRoute.getName()
        + " for "
        + decision.getProgressText()
        + " using teleports you carry.";
  }

  private TravelRouteMatch findCarriedMasterReturnTeleport(
      MasterRoutes.MasterRoute masterRoute) {
    if (masterRoute == null) {
      return null;
    }
    for (String family : masterRoute.getReturnItemFamilies()) {
      TravelItemMatch item = findCarriedTravelItem(family);
      if (item == null || !achievementDiaries.allowsTravelItem(family)) {
        continue;
      }
      Set<String> equivalentFamilies = new LinkedHashSet<>();
      equivalentFamilies.add(family);
      return new TravelRouteMatch(
          item, masterRoute.getReturnDestination(family), equivalentFamilies);
    }
    return null;
  }

  private TravelRouteMatch findBankMasterReturnTeleport(
      MasterRoutes.MasterRoute masterRoute) {
    if (masterRoute == null) {
      return null;
    }
    for (String family : masterRoute.getReturnItemFamilies()) {
      TravelItemMatch item = findBankTravelItem(family);
      if (item == null || !achievementDiaries.allowsTravelItem(family)) {
        continue;
      }
      Set<String> equivalentFamilies = new LinkedHashSet<>();
      equivalentFamilies.add(family);
      return new TravelRouteMatch(
          item, masterRoute.getReturnDestination(family), equivalentFamilies);
    }
    return null;
  }

  private void confirmTaskAreaForGuidedSession() {
    if (!guidedSessionActive) {
      return;
    }
    Player player = client.getLocalPlayer();
    if (player == null) {
      return;
    }
    boolean newlyConfirmed = guidedSessionPhase != GuidedSessionPhase.TASKING;
    guidedSessionPhase = GuidedSessionPhase.TASKING;
    taskAreaAnchor = player.getWorldLocation();
    guidedDynamicTaskDestination = null;
    guidedSurfaceEntranceDestination = null;
    guidedEntranceReached = false;
    taskArrivalGraceTicks = TASK_ARRIVAL_GRACE_TICKS;
    taskAreaExitTicks = 0;
    if (newlyConfirmed) {
      if (shortestPathBridge != null) {
        shortestPathBridge.clear();
      }
      clearGuidedTravelRecommendation();
      GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
      String positioning =
          target == null || target.profile == null || target.profile.getPositioningNote().isEmpty()
              ? ""
              : " " + target.profile.getPositioningNote();
      clearGuidedTaskDetourSnapshot();
      setGuidedSessionStatus(
          "At the task area."
              + positioning
              + " SlayerPlus will route you back if you leave before finishing.");
    }
  }

  private void updateGuidedSessionPosition() {
    if (!guidedSessionActive || client.getGameState() != GameState.LOGGED_IN) {
      return;
    }
    Player player = client.getLocalPlayer();
    if (player == null) {
      return;
    }
    WorldPoint playerLocation = player.getWorldLocation();
    if (playerLocation == null) {
      return;
    }
    WorldPoint previousPlayerLocation = guidedLastPlayerLocation;
    WorldPoint currentTemplateLocation = routeComparisonLocation(player, playerLocation);
    boolean routeEvidenceMoved =
        !java.util.Objects.equals(previousPlayerLocation, playerLocation)
            || !java.util.Objects.equals(guidedLastTemplateLocation, currentTemplateLocation);
    boolean routeEvidenceHistoryChanged =
        routeEvidenceMoved
            || !java.util.Objects.equals(guidedPreviousPlayerLocation, previousPlayerLocation)
            || !java.util.Objects.equals(
                guidedPreviousTemplateLocation, guidedLastTemplateLocation);
    guidedPreviousPlayerLocation = previousPlayerLocation;
    guidedPreviousTemplateLocation = guidedLastTemplateLocation;
    guidedLastPlayerLocation = playerLocation;
    guidedLastTemplateLocation = currentTemplateLocation;
    if (routeEvidenceHistoryChanged) {
      routeEvidenceVersion++;
    }
    if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER
        && effectiveTaskRemaining() > 0) {
      GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
      if (confirmGuidedTaskAreaBeforePreparation(target)) {
        return;
      }
      if (routeToSpellbookIfRequired("Assigned task requires a different spellbook")) {
        return;
      }
      if (isInfernoPreparationTarget(target)) {
        routeToInfernoPreparationBankForGuidedSession(
            target, "Assigned task detected after leaving the Slayer master");
      } else {
        routeToBankForGuidedSession("Assigned task detected — routing to the nearest bank");
      }
      return;
    }
    if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_BANK
        && effectiveTaskRemaining() > 0
        && shouldRebaseBankRoute(previousPlayerLocation, playerLocation)) {
      routeToBankForGuidedSession(
          "Teleport detected — recalculating the nearest bank from your landing point");
      return;
    }
    if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_SPELLBOOK) {
      if (currentPreparation != null && currentPreparation.isSpellbookReady()) {
        handleGuidedSpellbookReadinessTransition();
        return;
      }
      if (guidedPohSpellbookAltarReached) {
        setGuidedSessionStatus(
            "Use the loaded POH altar to switch to "
                + currentPreparation.getSpellbookName()
                + ". SlayerPlus will route back to a bank after the switch.");
        return;
      }
      if (guidedPohSpellbookTeleportPending) {
        WorldView worldView = client.getTopLevelWorldView();
        boolean instanced = worldView != null && worldView.isInstance();
        if (!instanced) {
          setGuidedSessionStatus(
              "Use the highlighted cape -> Teleport to house. SlayerPlus will verify the altar"
                  + " after the POH loads.");
          return;
        }
        if (guidedPohSpellbookLoadTicks == 0 && loadedPohSpellbookAltarId <= 0) {
          seedLoadedPohSpellbookAltarFromScene();
        }
        guidedPohSpellbookLoadTicks++;
        if (pohAltarSupportsSpellbookForRegression(
            loadedPohSpellbookAltarId, currentPreparation.getSpellbookName())) {
          clearGuidedTravelRecommendation();
          guidedPohSpellbookTeleportPending = false;
          guidedPohSpellbookAltarReached = true;
          setGuidedSessionStatus(
              "POH altar found — use it to switch to "
                  + currentPreparation.getSpellbookName()
                  + ". SlayerPlus will continue after the switch.");
          return;
        }
        if (guidedPohSpellbookLoadTicks < 5) {
          setGuidedSessionStatus("POH loaded — checking its spellbook altar.");
          return;
        }
        guidedPohSpellbookTeleportPending = false;
        guidedPohSpellbookFallbackActive = true;
        clearGuidedTravelRecommendation();
        routeToSpellbookIfRequired(
            "The loaded POH has no compatible altar — using the reviewed world change point");
        return;
      }
      SlayerSpellbookRouteCatalog.Route route =
          SlayerSpellbookRouteCatalog.resolve(
              currentPreparation == null ? "" : currentPreparation.getSpellbookName(),
              client.getVarbitValue(SPELLBOOK_VARBIT));
      if (route == null) {
        return;
      }
      if (shouldRebaseBankRoute(previousPlayerLocation, playerLocation)
          && distance(playerLocation, route.getDestination()) > 6) {
        routeToSpellbookIfRequired("Travel detected — recalculating the spellbook-change route");
        return;
      }
      boolean atChangePoint = distance(playerLocation, route.getDestination()) <= 6;
      setGuidedSessionStatus(
          atChangePoint
              ? "At the "
                  + route.getSpellbookName()
                  + " change point — "
                  + route.getInteraction()
                  + " SlayerPlus will continue automatically after the spellbook changes."
              : "Routing to the reviewed " + route.getSpellbookName() + " change point.");
      return;
    }
    if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_MASTER) {
      MasterRoutes.MasterRoute masterRoute =
          MasterRoutes.find(getRoutingMasterId());
      if (masterRoute != null && masterRoute.getId() == 10 && mortimerSlayerRingTeleportPending) {
        if (!isMortimerCavernForRegression(playerLocation)) {
          setGuidedSessionStatus("Use the highlighted Slayer ring -> Wyrmscraig Cavern.");
          return;
        }
        mortimerSlayerRingTeleportPending = false;
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
        clearGuidedTravelRecommendation();
        boolean localSent =
            shortestPathBridge != null
                && shortestPathBridge.routeToExactLocalTarget(
                    playerLocation, masterRoute.getDestination(), true);
        setGuidedSessionStatus(
            localSent
                ? "Wyrmscraig Cavern reached — routing locally to Mortimer."
                : "Wyrmscraig Cavern reached, but the local route to Mortimer could not be drawn.");
        return;
      }
      if (masterRoute != null
          && distance(playerLocation, masterRoute.getDestination()) <= MASTER_ARRIVAL_RADIUS) {
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
        clearGuidedTravelRecommendation();
        guidedSessionPhase = GuidedSessionPhase.WAITING_FOR_TASK;
        setGuidedSessionStatus(
            "At "
                + masterRoute.getName()
                + " — get a new task and SlayerPlus will route you to a bank");
      }
      return;
    }
    if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_PREP_BANK) {
      WorldPoint bank = BankRoutes.getInfernoPreparationBankTarget();
      if (gameTickSequence == 0 || gameTickSequence % ROUTE_DISCOVERY_INTERVAL_TICKS == 0) {
        WorldPoint liveBankTarget = findInfernoPreparationBankerTarget(playerLocation);
        if (liveBankTarget != null) {
          boolean startingManualBankSegment =
              infernoPreparationManualTeleportPending || infernoPreparationHotVentPending;
          if (startingManualBankSegment) {
            infernoPreparationManualTeleportPending = false;
            infernoPreparationHotVentPending = false;
            guidedBankTransportTracking = false;
            String prepIdentity = guidedTravelRequestRouteIdentity;
            if (prepIdentity != null && !prepIdentity.isEmpty()) {
              travelCoordinator.beginRoute(prepIdentity);
              guidedTravelRequestGeneration = travelCoordinator.getGeneration();
            }
            restoreGuidedTeleportWidgetHighlights();
            clearTeleportHighlightCaptures();
            showGuidedTravelRecommendation("", "");
          }
          if (startingManualBankSegment
              || taskAreaAnchor == null
              || !taskAreaAnchor.equals(liveBankTarget)) {
            boolean sent =
                shortestPathBridge != null
                    && shortestPathBridge.routeToExactLocalTarget(
                        playerLocation, liveBankTarget, true);
            if (sent) {
              taskAreaAnchor = liveBankTarget;
            } else {
              taskAreaAnchor = null;
              setGuidedSessionStatus(
                  "Mor Ul Rek reached, but SlayerPlus could not start the separate local route to"
                      + " TzHaar-Ket-Yil.");
              return;
            }
          }
          showGuidedTravelRecommendation(
              "Open the east Mor Ul Rek bank",
              "Open the bank beside TzHaar-Ket-Yil to finalize the Inferno setup.");
          setGuidedSessionStatus(
              "Routing to a collision-checked tile beside TzHaar-Ket-Yil at the east Mor Ul Rek"
                  + " (Zuk) bank — open the bank there to finalize the Inferno setup.");
          return;
        }
      }
      if (infernoPreparationHotVentPending) {
        WorldPoint hotVent = BankRoutes.getInfernoPreparationHotVentDoorTarget();
        boolean atHotVent =
            hotVent != null
                && playerLocation.getPlane() == hotVent.getPlane()
                && distance(playerLocation, hotVent) <= 4;
        taskAreaAnchor = hotVent;
        setGuidedSessionStatus(
            atHotVent
                ? "At the east Mor Ul Rek Hot vent door — Pass through the glowing barrier."
                      + " SlayerPlus will detect the reachable banker and start the local Zuk-bank"
                      + " route after you cross."
                : "Routing to the east Mor Ul Rek Hot vent door; pass the glowing barrier there to"
                      + " enter the Zuk-bank side.");
        return;
      }
      if (infernoPreparationManualTeleportPending) {
        if (isInInfernoPreparationLocalArea(playerLocation)) {
          infernoPreparationManualTeleportPending = false;
          guidedBankTransportTracking = false;
          String prepIdentity = guidedTravelRequestRouteIdentity;
          if (prepIdentity != null && !prepIdentity.isEmpty()) {
            travelCoordinator.beginRoute(prepIdentity);
            guidedTravelRequestGeneration = travelCoordinator.getGeneration();
          }
          restoreGuidedTeleportWidgetHighlights();
          clearTeleportHighlightCaptures();
          showGuidedTravelRecommendation("", "");
          boolean handedOff =
              shortestPathBridge != null
                  && bank != null
                  && shortestPathBridge.routeToLocalBankTarget(playerLocation, bank, true);
          taskAreaAnchor = handedOff ? bank : null;
          setGuidedSessionStatus(
              handedOff
                  ? "Mor Ul Rek reached — Shortest Path is now routing locally to its exact east"
                        + " Mor Ul Rek bank destination; SlayerPlus will lock onto TzHaar-Ket-Yil"
                        + " as soon as he is loaded."
                  : "Mor Ul Rek reached, but SlayerPlus could not hand the local Zuk-bank walk to"
                        + " Shortest Path.");
          return;
        }
        taskAreaAnchor = null;
        setGuidedSessionStatus(
            "Use the highlighted Ghommal -> Mor Ul Rek teleport. Shortest Path cannot model that"
                + " teleport itself; SlayerPlus will hand it the bank walk after you land.");
        return;
      }
      boolean bankApproachReached =
          playerLocation != null
              && bank != null
              && playerLocation.getPlane() == bank.getPlane()
              && distance(playerLocation, bank) <= INFERNO_PREP_BANK_APPROACH_RADIUS;
      taskAreaAnchor = bank;
      setGuidedSessionStatus(
          bankApproachReached
              ? "East Mor Ul Rek Zuk-bank destination reached — waiting for TzHaar-Ket-Yil to load,"
                    + " then SlayerPlus will finish on an exact collision-safe banker tile."
              : "Routing to Shortest Path's exact east Mor Ul Rek (Zuk) bank destination for"
                    + " Inferno preparation.");
      return;
    }
    if (effectiveTaskRemaining() <= 0) {
      return;
    }
    if (guidedSessionPhase == GuidedSessionPhase.ROUTING_TO_TASK) {
      GuidedTaskTarget target = resolveGuidedTaskTargetForRouting();
      if (target == null) {
        return;
      }
      WorldPoint routePlayerLocation = routeComparisonLocation(player, playerLocation);
      if (araxxorSpiderTeleportPending) {
        boolean landed =
            isAraxxorSpiderTeleportLandingForRegression(
                target.taskName,
                target.location,
                playerLocation,
                target.profile.getSurfaceAccess(),
                target.isInsideEncounterArea(playerLocation));
        if (!landed) {
          setGuidedSessionStatus(
              "Use the highlighted Spider cave teleport. Shortest Path will start at the cave"
                  + " entrance after you land.");
          return;
        }
        araxxorSpiderTeleportPending = false;
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
        clearGuidedTravelRecommendation();
        routeToTaskForGuidedSession(false, "Spider cave teleport landing reached");
        return;
      }
      WorldPoint whispererPlayerLocation = routePlayerLocation;
      Widget sanityPercentage = client.getWidget(InterfaceID.Sanity.SANITY_PERCENTAGE);
      boolean whispererArenaVisible =
          shouldConfirmWhispererArenaForRegression(
              target.taskName, sanityPercentage != null && !sanityPercentage.isHidden());
      if (whispererRingTeleportPending) {
        boolean landedInLassar =
            whispererArenaVisible
                || target.isInsideEncounterArea(whispererPlayerLocation)
                || isWhispererLassarInteriorForRegression(
                    target.taskName, target.location, whispererPlayerLocation);
        if (!landedInLassar) {
          setGuidedSessionStatus("Use Ring of shadows -> Lassar Undercity.");
          return;
        }
        whispererRingTeleportPending = false;
        if (whispererArenaVisible) {
          confirmTaskAreaForGuidedSession();
          return;
        }
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
        clearGuidedTravelRecommendation();
        routeToTaskForGuidedSession(false, "Lassar Undercity reached");
        return;
      }
      if (whispererCathedralTeleportPending) {
        if (!isWhispererCathedralTeleporterLandingForRegression(
            target.taskName, target.location, whispererPlayerLocation)) {
          setGuidedSessionStatus(
              "Follow Shortest Path to the Palace teleporter, then choose The Cathedral.");
          return;
        }
        whispererCathedralTeleportPending = false;
        if (shortestPathBridge != null) {
          shortestPathBridge.clear();
        }
        routeToTaskForGuidedSession(false, "Cathedral teleporter reached");
        return;
      }
      if (whispererArenaVisible) {
        confirmTaskAreaForGuidedSession();
        return;
      }
      RoutePlanResolution planResolution =
          target.routePlan == null ? null : resolveGuidedRoutePlan(target, player, playerLocation);
      if (planResolution != null && planResolution.hasArrived()) {
        confirmTaskAreaForGuidedSession();
        return;
      }
      if (planResolution != null && (planResolution.isWaiting() || planResolution.stage == null)) {
        pauseForGuidedRoutePlan(planResolution, "Continuing the reviewed encounter route");
        return;
      }
      String bossDiscoveryIdentity = routeDiscoveryIdentity(target);
      if (planResolution == null
          && target.isBoss()
          && routeDiscoveryDue(
              gameTickSequence,
              lastTaskNpcDiscoveryTick,
              bossDiscoveryIdentity,
              lastTaskNpcDiscoveryIdentity)) {
        lastTaskNpcDiscoveryTick = gameTickSequence;
        lastTaskNpcDiscoveryIdentity = bossDiscoveryIdentity;
        if (hasLoadedAssignedBossNearby(playerLocation, target.taskName, target.profile)) {
          confirmTaskAreaForGuidedSession();
          return;
        }
      }
      if (isInfernoPreparationTarget(target)) {
        if (isInfernoRegion(currentTemplateLocation)) {
          confirmTaskAreaForGuidedSession();
          return;
        }
        if (!infernoPreparationBankReady) {
          routeToInfernoPreparationBankForGuidedSession(
              target, "Inferno preparation is not finalized");
          return;
        }
        WorldPoint entry = guidedInfernoEntryDestination;
        if (entry == null
            && (gameTickSequence == 0 || gameTickSequence % ROUTE_DISCOVERY_INTERVAL_TICKS == 0)) {
          entry =
              findInfernoEntryTarget(
                  playerLocation,
                  RouteCatalog.getPreparationEntryNpcNames(
                      target.taskName, target.location, target.isBoss()));
          if (entry != null) {
            guidedInfernoEntryDestination = entry;
          }
        }
        if (entry == null) {
          WorldPoint entryApproach =
              RouteCatalog.getPreparationEntryApproachTarget(
                  target.taskName, target.location, target.isBoss());
          if (entryApproach != null
              && (taskAreaAnchor == null || !taskAreaAnchor.equals(entryApproach))) {
            boolean sent =
                shortestPathBridge != null
                    && shortestPathBridge.routeToLocalTaskArea(
                        playerLocation, java.util.Collections.singleton(entryApproach), true);
            taskAreaAnchor = sent ? entryApproach : null;
            if (!sent) {
              setGuidedSessionStatus(
                  "Unable to send the separate Inferno-entrance approach segment to Shortest Path");
              return;
            }
          }
          setGuidedSessionStatus(
              "Inferno setup is ready — routing to the reviewed Inferno entrance approach so"
                  + " TzHaar-Ket-Keh can load.");
          return;
        }
        if (taskAreaAnchor == null || !taskAreaAnchor.equals(entry)) {
          taskAreaAnchor = entry;
          if (shortestPathBridge != null) {
            shortestPathBridge.routeToExactLocalTarget(playerLocation, entry, true);
          }
        }
        if (distance(playerLocation, entry) <= 3) {
          showInfernoEntryNextStep(true);
          setGuidedSessionStatus(
              "At TzHaar-Ket-Keh — enter the Inferno. The 28-slot loadout is ready.");
        } else {
          showInfernoEntryNextStep(false);
          setGuidedSessionStatus(
              "Routing to a collision-checked tile beside TzHaar-Ket-Keh for Inferno entry.");
        }
        return;
      }
      if (planResolution == null
          && isUnsupportedEncounterTeleportLocation(target.location)
          && stageUnsupportedEncounterTeleport(
              target, playerLocation, false, "Continuing the reviewed encounter route")) {
        return;
      }
      RouteStage stage =
          planResolution == null
              ? resolveRouteStage(target, routePlayerLocation, playerLocation)
              : planResolution.stage;
      if (stage == null || !stage.isValid()) {
        if (!guidedRouteWaitingForCheckpoint) {
          if (shortestPathBridge != null) {
            shortestPathBridge.clear();
          }
          taskAreaAnchor = null;
          guidedRouteWaitingForCheckpoint = true;
        }
        setGuidedSessionStatus(
            "Waiting for the exact reviewed transition or "
                + formatName(target.taskName)
                + " checkpoint for "
                + target.location
                + ".");
        return;
      }
      guidedRouteWaitingForCheckpoint = false;
      WorldPoint destination = stage.destination;
      WorldPoint methodPosition = selectedCannonPosition(target);
      boolean methodStage = methodPosition != null && destination.equals(methodPosition);
      boolean destinationChanged = taskAreaAnchor == null || !taskAreaAnchor.equals(destination);
      boolean postTransportTransition =
          guidedPostTransportContinuationPending
              && shouldRebaseBankRoute(previousPlayerLocation, playerLocation);
      if (destinationChanged || postTransportTransition) {
        taskAreaAnchor = destination;
        if (!stage.exactNpc) {
          guidedDynamicTaskDestination = null;
        }
        boolean localTaskRoute =
            shouldUseLocalTaskRouteForRegression(routePlayerLocation, destination);
        guidedTaskBankPickupPending = false;
        if (shouldRetireTravelRecommendationForLocalRoute(
            localTaskRoute,
            guidedBankTransportTracking,
            currentTravelSelection().hasPhysicalItem())) {
          clearGuidedTravelRecommendation();
        }
        if (shortestPathBridge != null) {
          if (localTaskRoute) {
            shortestPathBridge.routeToLocalTaskArea(
                shortestPathStart(playerLocation),
                stage.targets,
                currentMasterId != KRYSTILIA_MASTER_ID,
                shouldEnableAgilityShortcutsForStage(target, destination));
          } else {
            shortestPathBridge.routeToTaskArea(
                shortestPathStart(playerLocation),
                stage.targets,
                currentMasterId != KRYSTILIA_MASTER_ID,
                false,
                false,
                true);
          }
        }
        guidedPostTransportContinuationPending = false;
      }
      if (planResolution != null) {
        RoutePlan.Leg planLeg = planResolution.leg;
        RoutePlan.RouteTarget planTarget = planLeg == null ? null : planLeg.getTarget();
        setGuidedSessionStatus(
            (planLeg == null ? "Following the reviewed encounter route." : planLeg.getGuidance())
                + (planTarget == null ? "" : " Next checkpoint: " + planTarget.getLabel() + "."));
        return;
      }
      if (stage.entrance) {
        boolean meiyerditchShortcut = isMeiyerditchShortcutStage(target, destination);
        boolean whispererSinkhole = isWhispererSinkholeStage(target, destination);
        boolean exactLoadedEntrance =
            guidedSurfaceEntranceDestination != null
                && guidedSurfaceEntranceDestination.equals(destination);
        int arrival = exactLoadedEntrance ? 3 : target.arrivalRadius(routePlayerLocation);
        if (distance(routePlayerLocation, destination) <= arrival) {
          guidedEntranceReached = true;
          setGuidedSessionStatus(
              whispererSinkhole
                  ? "At the Lassar sinkhole — descend into the Undercity. SlayerPlus will continue"
                        + " to the Cathedral after it loads."
                  : meiyerditchShortcut
                      ? "At the level-93 cave under The Hollows — enter it manually. It must have"
                            + " been cleared once from the laboratory side with 78 Mining."
                      : "At the exact "
                          + target.location
                          + " transition — "
                          + target.profile.getTransitionInstruction());
        } else {
          setGuidedSessionStatus(
              whispererSinkhole
                  ? "Routing through Camdozaal to the Lassar sinkhole."
                  : meiyerditchShortcut
                      ? "Routing through the Myreque tunnels to the level-93 Meiyerditch cave"
                            + " shortcut."
                      : "Routing to the reviewed " + target.location + " transition checkpoint.");
        }
        return;
      }
      if (methodStage) {
        setGuidedSessionStatus(
            "Inside "
                + target.location
                + " — routing to the exact reviewed dwarf multicannon setup tile.");
        if (distance(routePlayerLocation, destination) <= 1) {
          confirmTaskAreaForGuidedSession();
        }
        return;
      }
      if (stage.exactNpc) {
        setGuidedSessionStatus(
            "Inside "
                + target.location
                + " — routing to a collision-checked tile beside the nearest exact "
                + formatName(target.taskName)
                + ".");
      } else if (stage.learnedCheckpoint) {
        setGuidedSessionStatus(
            "Using the versioned locally verified "
                + formatName(target.taskName)
                + " checkpoint for "
                + target.location
                + ".");
      } else {
        setGuidedSessionStatus(
            "Inside "
                + target.location
                + " — following the reviewed interior route toward "
                + formatName(target.taskName)
                + ".");
      }
      if (distance(routePlayerLocation, destination) <= target.arrivalRadius(routePlayerLocation)) {
        confirmTaskAreaForGuidedSession();
      }
      return;
    }
    if (guidedSessionPhase != GuidedSessionPhase.TASKING) {
      return;
    }
    if (taskArrivalGraceTicks > 0) {
      taskAreaAnchor = playerLocation;
      taskArrivalGraceTicks--;
      taskAreaExitTicks = 0;
      return;
    }
    if (taskAreaAnchor == null) {
      taskAreaAnchor = playerLocation;
      return;
    }
    GuidedTaskTarget activeTarget = resolveGuidedTaskTarget();
    RouteCatalog.RouteProfile activeProfile =
        activeTarget == null ? null : activeTarget.profile;
    WorldPoint activeRouteLocation = routeComparisonLocation(player, playerLocation);
    boolean activeRoutePlan =
        activeTarget != null
            && activeTarget.routePlan != null
            && ensureGuidedRoutePlanState(activeTarget);
    if (activeRoutePlan
        && guidedRoutePlan.isWithinActivityRetention(
            buildGuidedRouteEvidence(player, playerLocation))) {
      taskAreaAnchor = playerLocation;
      taskAreaExitTicks = 0;
      return;
    }
    if (activeProfile != null
        && !activeRoutePlan
        && activeProfile.isInsideEncounterArea(activeRouteLocation)) {
      taskAreaAnchor = playerLocation;
      taskAreaExitTicks = 0;
      return;
    }
    boolean outsideTaskArea =
        activeRoutePlan
            ? distance(playerLocation, taskAreaAnchor) > TASK_EXIT_RADIUS
            : shouldCountTaskAreaExitForRegression(playerLocation, taskAreaAnchor, activeProfile);
    if (outsideTaskArea) {
      taskAreaExitTicks++;
      if (taskAreaExitTicks >= TASK_EXIT_CONFIRM_TICKS) {
        routeToBankForGuidedSession(
            "You left the unfinished task area — routing to the nearest bank before returning");
      }
    } else {
      taskAreaExitTicks = 0;
    }
  }

  static boolean shouldCountTaskAreaExitForRegression(
      WorldPoint playerLocation, WorldPoint anchor, RouteCatalog.RouteProfile profile) {
    return (profile == null || !profile.isInsideEncounterArea(playerLocation))
        && distance(playerLocation, anchor) > TASK_EXIT_RADIUS;
  }

  static boolean isMortimerCavernForRegression(WorldPoint location) {
    return location != null
        && location.getPlane() == MORTIMER_MASTER_DESTINATION.getPlane()
        && (distance(location, MORTIMER_SLAYER_RING_LANDING) <= MORTIMER_CAVERN_RADIUS
            || distance(location, MORTIMER_MASTER_DESTINATION) <= MORTIMER_CAVERN_RADIUS);
  }

  private WorldPoint findLoadedSurfaceEntrance(
      RouteCatalog.RouteProfile profile, WorldPoint authoredAccess) {
    RouteCatalog.TransitionSpec transition =
        profile == null ? null : profile.getTransitionSpec();
    if (authoredAccess == null || client == null || transition == null) {
      return null;
    }
    if (routeTransitionSceneSeedPending) {
      seedLoadedRouteTransitionObjectsFromScene();
    }
    WorldPoint best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (TileObject object : loadedRouteTransitionObjects) {
      WorldPoint candidate = entranceLocation(object, authoredAccess, transition);
      if (candidate == null) {
        continue;
      }
      int candidateDistance = distance(candidate, authoredAccess);
      if (candidateDistance < bestDistance) {
        best = candidate;
        bestDistance = candidateDistance;
      }
    }
    return best;
  }

  private void observeLoadedRouteTransitionObject(TileObject object) {
    if (object != null) {
      loadedRouteTransitionObjects.add(object);
    }
  }

  private void forgetLoadedRouteTransitionObject(TileObject object) {
    if (object != null) {
      loadedRouteTransitionObjects.remove(object);
    }
  }

  private void seedLoadedRouteTransitionObjectsFromScene() {
    WorldView worldView = client == null ? null : client.getTopLevelWorldView();
    Scene scene = worldView == null ? null : worldView.getScene();
    if (scene == null || scene.getTiles() == null) {
      return;
    }
    loadedRouteTransitionObjects.clear();
    for (Tile[][] planeTiles : scene.getTiles()) {
      if (planeTiles == null) {
        continue;
      }
      for (Tile[] column : planeTiles) {
        if (column == null) {
          continue;
        }
        for (Tile tile : column) {
          if (tile == null) {
            continue;
          }
          GameObject[] gameObjects = tile.getGameObjects();
          if (gameObjects != null) {
            for (GameObject object : gameObjects) {
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

  private void observeLoadedRoutePlanObject(TileObject object) {
    if (guidedRoutePlan == null || object == null) {
      return;
    }
    Set<RouteEvidence.ObjectAction> actions = routePlanObjectActions(object);
    if (actions.isEmpty()) {
      return;
    }
    Set<RouteEvidence.ObjectAction> previous =
        loadedRoutePlanObjectActions.put(object, actions);
    if (!actions.equals(previous)) {
      routeEvidenceVersion++;
    }
  }

  private void forgetLoadedRoutePlanObject(TileObject object) {
    if (object != null && loadedRoutePlanObjectActions.remove(object) != null) {
      routeEvidenceVersion++;
    }
  }

  private Set<RouteEvidence.ObjectAction> routePlanObjectActions(TileObject object) {
    if (object == null
        || guidedRoutePlanObjectSpecs.isEmpty()
        || (!guidedRoutePlanObjectIds.contains(object.getId())
            && !guidedRoutePlanHasNameOnlyObjectSpecs)) {
      return java.util.Collections.emptySet();
    }
    ObjectComposition composition;
    try {
      ObjectComposition resolved = client.getObjectDefinition(object.getId());
      if (resolved != null && resolved.getImpostorIds() != null) {
        ObjectComposition impostor = resolved.getImpostor();
        if (impostor != null) {
          resolved = impostor;
        }
      }
      composition = resolved;
    } catch (RuntimeException ignored) {
      return java.util.Collections.emptySet();
    }
    if (composition == null || composition.getActions() == null) {
      return java.util.Collections.emptySet();
    }
    Set<RouteEvidence.ObjectAction> result = new LinkedHashSet<>();
    for (String action : composition.getActions()) {
      if (action == null || action.trim().isEmpty()) {
        continue;
      }
      RouteEvidence.ObjectAction observation;
      try {
        observation =
            RouteEvidence.ObjectAction.of(object.getId(), composition.getName(), action);
      } catch (IllegalArgumentException ignored) {
        continue;
      }
      if (matchesAnyRoutePlanObjectSpec(observation)) {
        result.add(observation);
      }
    }
    return result.isEmpty()
        ? java.util.Collections.emptySet()
        : java.util.Collections.unmodifiableSet(result);
  }

  private boolean matchesAnyRoutePlanObjectSpec(RouteEvidence.ObjectAction observation) {
    if (observation == null) {
      return false;
    }
    for (RouteCheck.ObjectActionSpec spec : guidedRoutePlanObjectSpecs) {
      boolean identityMatches =
          (!spec.getObjectIds().isEmpty()
                  && spec.getObjectIds().contains(observation.getObjectId()))
              || (!spec.getObjectNames().isEmpty()
                  && spec.getObjectNames().contains(observation.getObjectName()));
      if (identityMatches && spec.getActions().contains(observation.getAction())) {
        return true;
      }
    }
    return false;
  }

  private void seedLoadedRoutePlanObjects() {
    if (!routePlanObjectSceneSeedPending || guidedRoutePlan == null) {
      return;
    }
    if (routeTransitionSceneSeedPending) {
      seedLoadedRouteTransitionObjectsFromScene();
    }
    loadedRoutePlanObjectActions.clear();
    for (TileObject object : loadedRouteTransitionObjects) {
      observeLoadedRoutePlanObject(object);
    }
    routePlanObjectSceneSeedPending = false;
  }

  private void seedLoadedRoutePlanNpcs() {
    if (!routePlanNpcSceneSeedPending || guidedRoutePlan == null) {
      return;
    }
    loadedRoutePlanNpcs.clear();
    WorldView worldView = client == null ? null : client.getTopLevelWorldView();
    if (worldView != null) {
      for (NPC npc : worldView.npcs()) {
        if (routePlanNpcMatchesActivePlan(npc)) {
          loadedRoutePlanNpcs.add(npc);
        }
      }
    }
    routePlanNpcSceneSeedPending = false;
    routeEvidenceVersion++;
  }

  private boolean routePlanNpcMatchesActivePlan(NPC npc) {
    if (npc == null || npc.getName() == null || guidedRoutePlanNpcNames.isEmpty()) {
      return false;
    }
    return guidedRoutePlanNpcNames.contains(RouteEvidence.normalize(npc.getName()));
  }

  private WorldPoint entranceLocation(
      TileObject object, WorldPoint authoredAccess, RouteCatalog.TransitionSpec transition) {
    if (object == null || authoredAccess == null || transition == null) {
      return null;
    }
    WorldPoint location = object.getWorldLocation();
    if (location == null
        || location.getPlane() != authoredAccess.getPlane()
        || distance(location, authoredAccess) > transition.getSearchRadius()) {
      return null;
    }
    ObjectComposition composition;
    try {
      composition = client.getObjectDefinition(object.getId());
      if (composition != null && composition.getImpostorIds() != null) {
        ObjectComposition impostor = composition.getImpostor();
        if (impostor != null) {
          composition = impostor;
        }
      }
    } catch (RuntimeException ignored) {
      return null;
    }
    if (composition == null) {
      return null;
    }
    String name = normalizeTravelName(composition.getName());
    if (!transition.matchesObjectName(name)) {
      return null;
    }
    String[] actions = composition.getActions();
    if (actions == null) {
      return null;
    }
    for (String action : actions) {
      String normalizedAction = normalizeTravelName(action);
      if (!transition.matchesAction(normalizedAction)) {
        continue;
      }
      if (normalizedAction.equals("use")
          && !transition.hasObjectNameHints()
          && !name.contains("cave")
          && !name.contains("entrance")
          && !name.contains("hole")
          && !name.contains("crevice")
          && !name.contains("tunnel")
          && !name.contains("stairs")
          && !name.contains("ladder")) {
        continue;
      }
      return nearestWalkableEntranceInteractionTile(location, authoredAccess);
    }
    return null;
  }

  private WorldPoint nearestWalkableEntranceInteractionTile(
      WorldPoint objectLocation, WorldPoint authoredAccess) {
    if (objectLocation == null) {
      return null;
    }
    if (isWalkableSceneTile(objectLocation)) {
      return objectLocation;
    }
    WorldPoint best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        if (dx == 0 && dy == 0) {
          continue;
        }
        WorldPoint candidate =
            new WorldPoint(
                objectLocation.getX() + dx, objectLocation.getY() + dy, objectLocation.getPlane());
        if (!isWalkableSceneTile(candidate)) {
          continue;
        }
        int candidateDistance =
            authoredAccess == null
                ? Math.abs(dx) + Math.abs(dy)
                : distance(candidate, authoredAccess);
        if (best == null || candidateDistance < bestDistance) {
          best = candidate;
          bestDistance = candidateDistance;
        }
      }
    }
    return best == null ? objectLocation : best;
  }

  private void seedLoadedBankObjectsIfNeeded() {
    if (!bankObjectSceneSeedPending || client == null) {
      return;
    }
    bankObjectSceneSeedPending = false;
    WorldView worldView = client.getTopLevelWorldView();
    Scene scene = worldView == null ? null : worldView.getScene();
    if (scene == null || scene.getTiles() == null) {
      bankObjectSceneSeedPending = true;
      return;
    }
    for (Tile[][] planeTiles : scene.getTiles()) {
      if (planeTiles == null) {
        continue;
      }
      for (Tile[] column : planeTiles) {
        if (column == null) {
          continue;
        }
        for (Tile tile : column) {
          if (tile == null) {
            continue;
          }
          GameObject[] gameObjects = tile.getGameObjects();
          if (gameObjects != null) {
            for (GameObject object : gameObjects) {
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

  private void observeLoadedBankObject(TileObject object) {
    WorldPoint location = bankObjectLocation(object);
    if (location != null) {
      loadedBankObjectLocations.add(location);
    }
  }

  private void forgetLoadedBankObject(TileObject object) {
    if (object != null && object.getWorldLocation() != null) {
      loadedBankObjectLocations.remove(object.getWorldLocation());
    }
  }

  private WorldPoint bankObjectLocation(TileObject object) {
    if (object == null || client == null) {
      return null;
    }
    try {
      ObjectComposition composition = client.getObjectDefinition(object.getId());
      if (composition != null && composition.getImpostorIds() != null) {
        ObjectComposition impostor = composition.getImpostor();
        if (impostor != null) {
          composition = impostor;
        }
      }
      return composition != null
              && isBankObjectForRegression(composition.getName(), composition.getActions())
          ? object.getWorldLocation()
          : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private WorldPoint findLoadedBankObjectTarget(WorldPoint playerLocation) {
    if (playerLocation == null) {
      return null;
    }
    WorldPoint nearestObject = null;
    int nearestDistance = Integer.MAX_VALUE;
    for (WorldPoint objectLocation : loadedBankObjectLocations) {
      if (objectLocation == null || objectLocation.getPlane() != playerLocation.getPlane()) {
        continue;
      }
      int objectDistance = distance(playerLocation, objectLocation);
      if (objectDistance <= DYNAMIC_BANK_OBJECT_RADIUS && objectDistance < nearestDistance) {
        nearestObject = objectLocation;
        nearestDistance = objectDistance;
      }
    }
    return nearestObject == null
        ? null
        : nearestWalkableBankInteractionTile(nearestObject, playerLocation);
  }

  private WorldPoint nearestWalkableBankInteractionTile(
      WorldPoint objectLocation, WorldPoint playerLocation) {
    WorldPoint best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (int dx = -3; dx <= 3; dx++) {
      for (int dy = -3; dy <= 3; dy++) {
        if (dx == 0 && dy == 0) {
          continue;
        }
        WorldPoint candidate =
            new WorldPoint(
                objectLocation.getX() + dx, objectLocation.getY() + dy, objectLocation.getPlane());
        if (!isWalkableSceneTile(candidate)) {
          continue;
        }
        int candidateDistance = distance(playerLocation, candidate);
        if (best == null || candidateDistance < bestDistance) {
          best = candidate;
          bestDistance = candidateDistance;
        }
      }
    }
    return best;
  }

  static boolean hasBankActionForRegression(String[] actions) {
    return hasObjectAction(actions, "Bank");
  }

  static boolean isBankObjectForRegression(String name, String[] actions) {
    if (hasBankActionForRegression(actions)) {
      return true;
    }
    String normalizedName = name == null ? "" : name.trim().toLowerCase(Locale.ENGLISH);
    return normalizedName.contains("bank chest")
        && (hasObjectAction(actions, "Use") || hasObjectAction(actions, "Open"));
  }

  private static boolean hasObjectAction(String[] actions, String expected) {
    if (actions == null || expected == null) {
      return false;
    }
    for (String action : actions) {
      if (action != null && action.trim().equalsIgnoreCase(expected)) {
        return true;
      }
    }
    return false;
  }

  private static WorldPoint nearestTarget(WorldPoint origin, WorldPoint first, WorldPoint second) {
    if (first == null) {
      return second;
    }
    if (second == null || origin == null) {
      return first;
    }
    return distance(origin, first) <= distance(origin, second) ? first : second;
  }

  private WorldPoint findLoadedBankNpcTarget(WorldPoint playerLocation) {
    if (playerLocation == null) {
      return null;
    }
    NPC nearestNpc = null;
    int nearestDistance = Integer.MAX_VALUE;
    WorldView worldView = client.getTopLevelWorldView();
    if (worldView == null) {
      return null;
    }
    for (NPC npc : worldView.npcs()) {
      if (npc == null || !npcOffersBanking(npc)) {
        continue;
      }
      WorldPoint npcLocation = npc.getWorldLocation();
      if (npcLocation == null || npcLocation.getPlane() != playerLocation.getPlane()) {
        continue;
      }
      int npcDistance = distance(playerLocation, npcLocation);
      if (npcDistance <= DYNAMIC_BANK_NPC_RADIUS && npcDistance < nearestDistance) {
        nearestDistance = npcDistance;
        nearestNpc = npc;
      }
    }
    if (nearestNpc == null) {
      return null;
    }
    return liveBankNpcTargetForRegression(
        findNearestWalkablePerimeterTile(playerLocation, nearestNpc),
        nearestNpc.getWorldLocation());
  }

  static WorldPoint liveBankNpcTargetForRegression(
      WorldPoint reachablePerimeter, WorldPoint npcLocation) {
    return reachablePerimeter == null ? npcLocation : reachablePerimeter;
  }

  private static boolean npcOffersBanking(NPC npc) {
    if (npc == null) {
      return false;
    }
    try {
      if (npc.getComposition() != null
          && hasBankActionForRegression(npc.getComposition().getActions())) {
        return true;
      }
    } catch (RuntimeException ignored) {
    }
    String name = npc.getName();
    return name != null && name.equalsIgnoreCase("Banker");
  }

  private WorldPoint findLoadedNamedNpcTarget(
      WorldPoint playerLocation, Set<String> normalizedNpcNames, int maximumRadius) {
    return findLoadedNpcTarget(
        playerLocation, normalizedNpcNames, java.util.Collections.emptySet(), maximumRadius);
  }

  private WorldPoint findInfernoEntryTarget(
      WorldPoint playerLocation, Set<String> normalizedNpcNames) {
    return findLoadedNpcTarget(
        playerLocation, normalizedNpcNames, INFERNO_ENTRY_NPC_IDS, INFERNO_ENTRY_NPC_RADIUS);
  }

  private WorldPoint findLoadedNpcTarget(
      WorldPoint playerLocation,
      Set<String> normalizedNpcNames,
      Set<Integer> acceptedNpcIds,
      int maximumRadius) {
    if (playerLocation == null
        || ((normalizedNpcNames == null || normalizedNpcNames.isEmpty())
            && (acceptedNpcIds == null || acceptedNpcIds.isEmpty()))) {
      return null;
    }
    NPC nearestNpc = null;
    int nearestDistance = Integer.MAX_VALUE;
    WorldView worldView = client.getTopLevelWorldView();
    if (worldView == null) {
      return null;
    }
    for (NPC npc : worldView.npcs()) {
      if (npc == null) {
        continue;
      }
      String npcName = npc.getName();
      boolean nameMatches =
          npcName != null
              && normalizedNpcNames != null
              && normalizedNpcNames.contains(normalizeTravelName(npcName));
      boolean idMatches = acceptedNpcIds != null && acceptedNpcIds.contains(npc.getId());
      if (!nameMatches && !idMatches) {
        continue;
      }
      WorldPoint npcLocation = npc.getWorldLocation();
      if (npcLocation == null || npcLocation.getPlane() != playerLocation.getPlane()) {
        continue;
      }
      int npcDistance = distance(playerLocation, npcLocation);
      if (npcDistance <= Math.max(1, maximumRadius) && npcDistance < nearestDistance) {
        nearestDistance = npcDistance;
        nearestNpc = npc;
      }
    }
    return nearestNpc == null ? null : findNearestWalkablePerimeterTile(playerLocation, nearestNpc);
  }

  private WorldPoint findLoadedTaskNpcTarget(
      WorldPoint playerLocation, RouteCatalog.RouteProfile profile) {
    if (playerLocation == null || profile == null) {
      return null;
    }
    NPC nearestNpc = null;
    int nearestDistance = Integer.MAX_VALUE;
    WorldView worldView = client.getTopLevelWorldView();
    if (worldView == null) {
      return null;
    }
    for (NPC npc : worldView.npcs()) {
      if (npc == null || npc.getName() == null || !profile.matchesNpc(npc.getName())) {
        continue;
      }
      WorldPoint npcLocation = npc.getWorldLocation();
      if (npcLocation == null || npcLocation.getPlane() != playerLocation.getPlane()) {
        continue;
      }
      int npcDistance = distance(playerLocation, npcLocation);
      if (npcDistance < nearestDistance) {
        nearestDistance = npcDistance;
        nearestNpc = npc;
      }
    }
    if (nearestNpc == null || nearestDistance > DYNAMIC_TASK_NPC_RADIUS) {
      return null;
    }
    return findNearestWalkablePerimeterTile(playerLocation, nearestNpc);
  }

  private boolean hasLoadedAssignedBossNearby(
      WorldPoint playerLocation, String taskName, RouteCatalog.RouteProfile profile) {
    if (playerLocation == null || taskName == null || taskName.trim().isEmpty()) {
      return false;
    }
    WorldView worldView = client.getTopLevelWorldView();
    if (worldView == null) {
      return false;
    }
    for (NPC npc : worldView.npcs()) {
      if (npc == null
          || !((profile != null && profile.matchesNpc(npc.getName()))
              || bossNpcNameMatchesTaskForRegression(taskName, npc.getName()))) {
        continue;
      }
      WorldPoint npcLocation = npc.getWorldLocation();
      if (npcLocation != null
          && npcLocation.getPlane() == playerLocation.getPlane()
          && distance(playerLocation, npcLocation) <= DYNAMIC_TASK_NPC_RADIUS) {
        return true;
      }
    }
    return false;
  }

  static boolean bossNpcNameMatchesTaskForRegression(String taskName, String npcName) {
    String task = normalizeTravelName(taskName).replaceFirst("^the\\s+", "");
    String npc = normalizeTravelName(npcName).replaceFirst("^the\\s+", "");
    return !task.isEmpty() && task.equals(npc);
  }

  static boolean shouldConfirmWhispererArenaForRegression(
      String taskName, boolean sanityInterfaceVisible) {
    String task = normalizeTravelName(taskName).replaceFirst("^the\\s+", "");
    return sanityInterfaceVisible && task.equals("whisperer");
  }

  private WorldPoint findNearestWalkablePerimeterTile(WorldPoint playerLocation, NPC npc) {
    if (playerLocation == null || npc == null) {
      return null;
    }
    WorldArea npcArea = npc.getWorldArea();
    if (npcArea == null) {
      WorldPoint npcLocation = npc.getWorldLocation();
      if (npcLocation == null) {
        return null;
      }
      npcArea = new WorldArea(npcLocation, 1, 1);
    }
    Set<WorldPoint> candidates = new LinkedHashSet<>();
    for (int padding = 1; padding <= 3; padding++) {
      int minX = npcArea.getX() - padding;
      int maxX = npcArea.getX() + npcArea.getWidth() - 1 + padding;
      int minY = npcArea.getY() - padding;
      int maxY = npcArea.getY() + npcArea.getHeight() - 1 + padding;
      for (int x = minX; x <= maxX; x++) {
        addWalkableNpcTarget(candidates, new WorldPoint(x, minY, npcArea.getPlane()), npcArea);
        addWalkableNpcTarget(candidates, new WorldPoint(x, maxY, npcArea.getPlane()), npcArea);
      }
      for (int y = minY + 1; y < maxY; y++) {
        addWalkableNpcTarget(candidates, new WorldPoint(minX, y, npcArea.getPlane()), npcArea);
        addWalkableNpcTarget(candidates, new WorldPoint(maxX, y, npcArea.getPlane()), npcArea);
      }
    }
    return findReachableCandidate(playerLocation, npcArea, candidates);
  }

  private WorldPoint findReachableCandidate(
      WorldPoint playerLocation, WorldArea npcArea, Set<WorldPoint> candidates) {
    if (playerLocation == null || npcArea == null || candidates == null || candidates.isEmpty()) {
      return null;
    }
    WorldView worldView = client.getTopLevelWorldView();
    if (worldView == null || playerLocation.getPlane() != worldView.getPlane()) {
      return null;
    }
    ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
    Set<WorldPoint> visited = new HashSet<>();
    queue.add(playerLocation);
    visited.add(playerLocation);
    while (!queue.isEmpty() && visited.size() <= DYNAMIC_TASK_NPC_SEARCH_TILE_LIMIT) {
      WorldPoint current = queue.removeFirst();
      if (candidates.contains(current)) {
        return current;
      }
      WorldArea currentArea = new WorldArea(current, 1, 1);
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          if ((dx == 0 && dy == 0) || !currentArea.canTravelInDirection(worldView, dx, dy)) {
            continue;
          }
          WorldPoint next =
              new WorldPoint(current.getX() + dx, current.getY() + dy, current.getPlane());
          if (npcArea.contains(next)
              || distance(playerLocation, next) > DYNAMIC_TASK_NPC_RADIUS
              || !isWalkableSceneTile(next)
              || !visited.add(next)) {
            continue;
          }
          queue.addLast(next);
        }
      }
    }
    return null;
  }

  private void saveLearnedRouteCheckpoint(GuidedTaskTarget target, WorldPoint destination) {
    if (target == null
        || target.profile == null
        || destination == null
        || configManager == null
        || !target.profile.isPlausibleLearnedCheckpoint(destination)) {
      return;
    }
    WorldView worldView = client.getTopLevelWorldView();
    if (worldView == null || worldView.isInstance()) {
      return;
    }
    String key = routeCheckpointKey(target);
    String value = destination.getX() + "," + destination.getY() + "," + destination.getPlane();
    String persisted = persistedRouteCheckpointCache.get(key);
    if (persisted == null) {
      persisted = configManager.getConfiguration(SlayerPlusConfig.GROUP, key);
      if (persisted != null && !persisted.trim().isEmpty()) {
        persistedRouteCheckpointCache.put(key, persisted.trim());
      }
    }
    if (value.equals(persisted == null ? "" : persisted.trim())) {
      return;
    }
    Long lastWrite = routeCheckpointLastWriteTick.get(key);
    if (lastWrite != null && gameTickSequence - lastWrite < ROUTE_CHECKPOINT_WRITE_INTERVAL_TICKS) {
      return;
    }
    configManager.setConfiguration(SlayerPlusConfig.GROUP, key, value);
    persistedRouteCheckpointCache.put(key, value);
    routeCheckpointLastWriteTick.put(key, gameTickSequence);
  }

  private WorldPoint readLearnedRouteCheckpoint(GuidedTaskTarget target) {
    if (target == null || configManager == null) {
      return null;
    }
    String key = routeCheckpointKey(target);
    String stored;
    if (persistedRouteCheckpointCache.containsKey(key)) {
      stored = persistedRouteCheckpointCache.get(key);
    } else {
      String configured = configManager.getConfiguration(SlayerPlusConfig.GROUP, key);
      stored = configured == null ? "" : configured.trim();
      persistedRouteCheckpointCache.put(key, stored);
    }
    if (stored.isEmpty()) {
      return null;
    }
    String[] parts = stored.split(",");
    if (parts.length != 3) {
      return null;
    }
    try {
      WorldPoint checkpoint =
          new WorldPoint(
              Integer.parseInt(parts[0].trim()),
              Integer.parseInt(parts[1].trim()),
              Integer.parseInt(parts[2].trim()));
      return target.profile != null && target.profile.isPlausibleLearnedCheckpoint(checkpoint)
          ? checkpoint
          : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String routeCheckpointKey(GuidedTaskTarget target) {
    String identity =
        normalizeTravelName(target.taskName)
            + "|"
            + normalizeTravelName(target.location)
            + "|"
            + (target.isBoss() ? "boss" : "regular");
    return ROUTE_CHECKPOINT_PREFIX + Integer.toUnsignedString(identity.hashCode(), 36);
  }

  private Set<WorldPoint> singletonRouteTarget(WorldPoint destination) {
    Set<WorldPoint> targets = new LinkedHashSet<>();
    if (destination != null) {
      targets.add(destination);
    }
    return targets;
  }

  private void addWalkableNpcTarget(
      Set<WorldPoint> candidates, WorldPoint candidate, WorldArea npcArea) {
    if (candidate != null
        && npcArea != null
        && !npcArea.contains(candidate)
        && isWalkableSceneTile(candidate)) {
      candidates.add(candidate);
    }
  }

  private boolean isWalkableSceneTile(WorldPoint point) {
    if (point == null) {
      return false;
    }
    WorldView worldView = client.getTopLevelWorldView();
    if (worldView == null || point.getPlane() != worldView.getPlane()) {
      return false;
    }
    int sceneX = point.getX() - worldView.getBaseX();
    int sceneY = point.getY() - worldView.getBaseY();
    if (sceneX < 0
        || sceneY < 0
        || sceneX >= worldView.getSizeX()
        || sceneY >= worldView.getSizeY()) {
      return false;
    }
    CollisionData[] collisionMaps = worldView.getCollisionMaps();
    if (collisionMaps == null
        || point.getPlane() < 0
        || point.getPlane() >= collisionMaps.length
        || collisionMaps[point.getPlane()] == null) {
      return false;
    }
    int[][] flags = collisionMaps[point.getPlane()].getFlags();
    return sceneX < flags.length
        && flags[sceneX] != null
        && sceneY < flags[sceneX].length
        && (flags[sceneX][sceneY] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
  }

  private boolean isPointBoosting() {
    return config != null
        && config.slayerWorkflow() == Preference.Workflow.TURAEL_POINT_BOOST;
  }

  private BoostCoordinator.Decision pointBoostDecision(int completedStreak) {
    return BoostCoordinator.nextAssignment(
        completedStreak,
        config == null ? Preference.BonusMaster.KONAR : config.pointBoostBonusMaster());
  }

  private String pointBoostPanelStatus(
      int completedStreak, boolean hasActiveTask, int assigningMasterId) {
    if (!isPointBoosting()) {
      return "";
    }
    BoostCoordinator.Decision decision = pointBoostDecision(completedStreak);
    if (hasActiveTask && assigningMasterId != decision.getMasterId()) {
      return "Point boosting resumes after this existing assignment.";
    }
    return hasActiveTask
        ? "Point boost: " + decision.getProgressText()
        : "Next: "
            + MasterRoutes.getName(decision.getMasterId())
            + " — "
            + decision.getProgressText();
  }

  private int masterForNextAssignment(int normalStreak) {
    if (isPointBoosting()) {
      return pointBoostDecision(normalStreak).getMasterId();
    }
    return lastUsedMasterId > 0 ? lastUsedMasterId : currentMasterId;
  }

  private int getPostTaskReturnMasterId() {
    if (!isPointBoosting()) {
      return getRoutingMasterId();
    }
    int completedStreak = client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
    int predictedPostTaskStreak = completedStreak + (effectiveTaskRemaining() > 0 ? 1 : 0);
    return pointBoostDecision(predictedPostTaskStreak).getMasterId();
  }

  private int getRoutingMasterId() {
    if (effectiveTaskRemaining() > 0 && currentMasterId > 0) {
      return currentMasterId;
    }
    if (isPointBoosting()) {
      return pointBoostDecision(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED))
          .getMasterId();
    }
    return lastUsedMasterId > 0 ? lastUsedMasterId : currentMasterId;
  }

  private int effectiveTaskRemaining() {
    return effectiveTaskRemainingForRegression(
        observedTaskRemaining, client.getVarpValue(VarPlayerID.SLAYER_COUNT));
  }

  static int effectiveTaskRemainingForRegression(int resolvedRemaining, int liveRemaining) {
    return resolvedRemaining >= 0 ? resolvedRemaining : Math.max(0, liveRemaining);
  }

  private boolean isGuidedSessionAvailable() {
    return panel != null
        && client.getGameState() == GameState.LOGGED_IN
        && config.enableShortestPathRouting()
        && shortestPathBridge != null;
  }

  private String getGuidedSessionDisplayStatus() {
    if (guidedSessionActive) {
      return guidedSessionStatus;
    }
    if (client.getGameState() != GameState.LOGGED_IN) {
      return "Log in to start a guided Slayer session";
    }
    if (!config.enableShortestPathRouting()) {
      return "Enable Shortest Path routing in SlayerPlus settings to start a session";
    }
    if (effectiveTaskRemaining() > 0) {
      return "Start a session to route to a bank, gear up, and travel to your task";
    }
    if (getRoutingMasterId() > 0) {
      if (isPointBoosting()) {
        BoostCoordinator.Decision decision =
            pointBoostDecision(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED));
        return "Start a session to route to "
            + MasterRoutes.getName(decision.getMasterId())
            + " for "
            + decision.getProgressText();
      }
      return "Start a session to route to "
          + MasterRoutes.getName(getRoutingMasterId())
          + " for a task";
    }
    return guidedSessionStatus;
  }

  private void setGuidedSessionStatus(String status) {
    String nextStatus = status == null ? "" : status.trim();
    if (nextStatus.equals(guidedSessionStatus)) {
      return;
    }
    guidedSessionStatus = nextStatus;
    updateGuidedSessionPanel();
  }

  private void updateGuidedSessionPanel() {
    SlayerPlusPanel currentPanel = panel;
    if (currentPanel == null) {
      return;
    }
    boolean active = guidedSessionActive;
    boolean available = isGuidedSessionAvailable();
    String status = getGuidedSessionDisplayStatus();
    SwingUtilities.invokeLater(() -> currentPanel.showSessionState(active, available, status));
  }

  private static int distance(WorldPoint first, WorldPoint second) {
    if (first == null || second == null) {
      return Integer.MAX_VALUE;
    }
    if (first.getPlane() != second.getPlane()) {
      return Integer.MAX_VALUE;
    }
    return Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getY() - second.getY()));
  }

  static boolean shouldRebaseBankRoute(WorldPoint previous, WorldPoint current) {
    if (previous == null || current == null || previous.equals(current)) {
      return false;
    }
    if (previous.getPlane() != current.getPlane()) {
      return true;
    }
    return distance(previous, current) > BANK_ROUTE_REBASE_DISTANCE;
  }

  private void startRecommendedRoute() {
    SlayerPlusPanel currentPanel = panel;
    if (currentPanel == null) {
      return;
    }
    if (!config.enableShortestPathRouting()) {
      SwingUtilities.invokeLater(
          () -> currentPanel.showRouteStatus("Routing is disabled in SlayerPlus settings"));
      return;
    }
    if (!guidedSessionActive) {
      toggleGuidedSession();
      return;
    }
    routeToTaskForGuidedSession(
        bankScanned || client.getItemContainer(InventoryID.BANK) != null, "Route requested");
  }

  private int readRuneLiteSlayerProfileInt(String key) {
    try {
      Integer value =
          configManager.getRSProfileConfiguration(RUNELITE_SLAYER_CONFIG_GROUP, key, Integer.class);
      return value == null ? -1 : value;
    } catch (RuntimeException ex) {
      log.debug("Unable to read RuneLite Slayer profile key {}", key, ex);
      return -1;
    }
  }

  private String readRuneLiteSlayerProfileString(String key) {
    try {
      String value = configManager.getRSProfileConfiguration(RUNELITE_SLAYER_CONFIG_GROUP, key);
      return value == null ? "" : value.trim();
    } catch (RuntimeException ex) {
      log.debug("Unable to read RuneLite Slayer profile key {}", key, ex);
      return "";
    }
  }

  private int readSlayerPlusProfileInt(String key) {
    try {
      Integer value =
          configManager.getRSProfileConfiguration(SlayerPlusConfig.GROUP, key, Integer.class);
      return value == null ? -1 : value;
    } catch (RuntimeException ex) {
      log.debug("Unable to read SlayerPlus profile key {}", key, ex);
      return -1;
    }
  }

  private void storeLastSlayerMasterId(int masterId) {
    if (masterId <= 0 || masterId == readSlayerPlusProfileInt(LAST_SLAYER_MASTER_SNAPSHOT_KEY)) {
      return;
    }
    try {
      configManager.setRSProfileConfiguration(
          SlayerPlusConfig.GROUP, LAST_SLAYER_MASTER_SNAPSHOT_KEY, masterId);
    } catch (RuntimeException ex) {
      log.debug("Unable to store the last Slayer master", ex);
    }
  }

  private String profileSlayerLocation() {
    String location = readRuneLiteSlayerProfileString(RUNELITE_SLAYER_LOCATION_KEY);
    return location.isEmpty() ? "Assigned area" : location;
  }

  private int inferNearbySlayerMasterId() {
    Player player = client.getLocalPlayer();
    WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
    if (playerLocation == null) {
      return 0;
    }
    int nearestId = 0;
    int nearestDistance = Integer.MAX_VALUE;
    for (int masterId = 1; masterId <= 10; masterId++) {
      MasterRoutes.MasterRoute route = MasterRoutes.find(masterId);
      if (route == null || route.getDestination().getPlane() != playerLocation.getPlane()) {
        continue;
      }
      int distance = playerLocation.distanceTo2D(route.getDestination());
      if (distance <= 20 && distance < nearestDistance) {
        nearestId = masterId;
        nearestDistance = distance;
      }
    }
    return nearestId;
  }

  static int firstPositive(int... values) {
    if (values != null) {
      for (int value : values) {
        if (value > 0) {
          return value;
        }
      }
    }
    return 0;
  }

  static int resolveTaskRemainingForRegression(
      int previouslyObservedRemaining,
      int serviceRemaining,
      int liveRemaining,
      int profileRemaining,
      int chatRemaining) {
    if (previouslyObservedRemaining > 0 && liveRemaining <= 0) {
      return 0;
    }
    return firstPositive(serviceRemaining, liveRemaining, profileRemaining, chatRemaining);
  }

  static int preferLiveOrProfileValue(int liveValue, int profileValue, int chatValue) {
    if (liveValue > 0) {
      return liveValue;
    }
    if (profileValue > 0) {
      return profileValue;
    }
    if (chatValue >= 0) {
      return chatValue;
    }
    return Math.max(0, Math.max(liveValue, profileValue));
  }

  private String readTaskName() {
    try {
      int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
      int taskRow;
      if (taskId == 98) {
        List<Integer> bossRows =
            client.getDBRowsByValue(
                DBTableID.SlayerTaskSublist.ID,
                DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
                0,
                client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
        if (bossRows == null || bossRows.isEmpty()) {
          return "Unknown task";
        }
        Object[] taskFields =
            client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0);
        if (taskFields == null || taskFields.length == 0 || !(taskFields[0] instanceof Integer)) {
          return "Unknown task";
        }
        taskRow = (Integer) taskFields[0];
      } else {
        List<Integer> taskRows =
            client.getDBRowsByValue(
                DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
        if (taskRows == null || taskRows.isEmpty()) {
          return "Unknown task";
        }
        taskRow = taskRows.get(0);
      }
      Object[] fields = client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
      return fields != null && fields.length > 0 && fields[0] instanceof String
          ? formatName((String) fields[0])
          : "Unknown task";
    } catch (RuntimeException ex) {
      log.debug("Unable to read Slayer task from the game database", ex);
      return "Unknown task";
    }
  }

  private String readAssignedLocation() {
    try {
      int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
      if (areaId <= 0) {
        return "Not restricted";
      }
      List<Integer> areaRows =
          client.getDBRowsByValue(
              DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
      if (areaRows == null || areaRows.isEmpty()) {
        return "Assigned area";
      }
      Object[] fields =
          client.getDBTableField(areaRows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0);
      return fields != null && fields.length > 0 && fields[0] instanceof String
          ? (String) fields[0]
          : "Assigned area";
    } catch (RuntimeException ex) {
      log.debug("Unable to read Slayer area from the game database", ex);
      return "Assigned area";
    }
  }

  private String getAccountName() {
    Player player = client.getLocalPlayer();
    if (player == null || player.getName() == null || player.getName().trim().isEmpty()) {
      return "ACCOUNT LOADING";
    }
    return player.getName().trim();
  }

  static String masterDisplayNameForRegression(int masterId, boolean whileGuthixSleepsFinished) {
    if (masterId == 5 && whileGuthixSleepsFinished) {
      return "Kuradal";
    }
    return MasterRoutes.getName(masterId);
  }

  private static String formatName(String value) {
    if (value == null || value.isEmpty()) {
      return "Unknown task";
    }
    String lower = value.toLowerCase();
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }

  private static final class TravelRouteCandidate {
    private final String family;
    private final TravelItemMatch item;
    private final String destination;

    private TravelRouteCandidate(String family, TravelItemMatch item, String destination) {
      this.family = family == null ? "" : family.trim();
      this.item = item;
      this.destination = destination == null ? "" : destination.trim();
    }
  }

  private static final class TravelRouteMatch {
    private final TravelItemMatch item;
    private final String destination;
    private final Set<String> equivalentItemFamilies;

    private TravelRouteMatch(
        TravelItemMatch item, String destination, Set<String> equivalentItemFamilies) {
      this.item = item;
      this.destination = destination == null ? "" : destination;
      this.equivalentItemFamilies =
          equivalentItemFamilies == null
              ? java.util.Collections.emptySet()
              : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(equivalentItemFamilies));
    }
  }

  private static final class TravelInstruction {
    private final String family;
    private final String destination;

    private TravelInstruction(String family, String destination) {
      this.family = family == null ? "" : family.trim();
      this.destination = destination == null ? "" : destination.trim();
    }
  }

  private static final class TravelItemMatch {
    private final int itemId;
    private final String displayName;
    private final int score;

    private TravelItemMatch(int itemId, String displayName, int score) {
      this.itemId = itemId;
      this.displayName = displayName;
      this.score = score;
    }
  }

  @Provides
  SlayerPlusConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(SlayerPlusConfig.class);
  }
}
