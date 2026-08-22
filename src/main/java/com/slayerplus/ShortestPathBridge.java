package com.slayerplus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

public final class ShortestPathBridge
{
	private static final String NAMESPACE = "shortestpath";
	private static final String PATH_MESSAGE = "path";
	private static final String CLEAR_MESSAGE = "clear";
	private static final String INVENTORY_TELEPORTS = "Inventory";
	private static final String BANK_AND_INVENTORY_TELEPORTS =
		"Inventory and Bank";
	private static final int TASK_AREA_UNREACHABLE_DISTANCE = 12;
	/*
	 * Shortest Path's bank catalog can identify a booth/NPC-object tile rather than
	 * the walkable interaction tile beside it. Match Shortest Path's own default:
	 * ending within two tiles is a valid bank arrival, not an unreachable route.
	 */
	private static final int PREPARATION_BANK_UNREACHABLE_DISTANCE = 2;
	private static final int PREPARATION_ACCESS_UNREACHABLE_DISTANCE = 2;
	private static final int LOCAL_TASK_UNREACHABLE_DISTANCE = 6;
	/* A used transport should land in the same broad routing area as at least one
	 * reviewed target. This deliberately allows long post-teleport walks while
	 * rejecting a late response from an unrelated cross-map request. */
	private static final int MAX_RELEVANT_EDGE_DISTANCE = 1_024;
	private static final int MAX_RETIRED_REQUESTS = 4;
	private static final int ENDPOINT_AFFINITY_MARGIN = 32;

	private final EventBus eventBus;
	private final Object requestLock = new Object();
	private long nextRequestSequence;
	private long acceptedRequestSequence = -1L;
	private long reservedRequestSequence = -1L;
	private volatile TransportRequestToken activeRequest;
	private final Deque<RequestSignature> retiredRequests = new ArrayDeque<>(
		MAX_RETIRED_REQUESTS
	);

	/**
	 * Immutable local correlation token for the most recently submitted path.
	 * Shortest Path does not echo a request id, start, target, or config in its
	 * response, so SlayerPlus couples this token with a clear-before-supersede
	 * barrier and validates the response edges against this exact target set.
	 */
	static final class TransportRequestToken
	{
		private final long sequence;
		private final RequestSignature signature;
		private final Set<WorldPoint> targets;
		private final boolean expectsTransportResponse;
		private final boolean ambiguityRetryAllowed;

		private TransportRequestToken(
			final long sequence,
			final RequestSignature signature,
			final Set<WorldPoint> targets,
			final boolean expectsTransportResponse,
			final boolean ambiguityRetryAllowed)
		{
			this.sequence = sequence;
			this.signature = signature;
			this.targets = targets;
			this.expectsTransportResponse = expectsTransportResponse;
			this.ambiguityRetryAllowed = ambiguityRetryAllowed;
		}

		Set<WorldPoint> getTargetsForRegression()
		{
			return targets;
		}
	}

	private static final class RequestSignature
	{
		private final WorldPoint start;
		private final Set<WorldPoint> targets;
		private final Map<String, Object> config;

		private RequestSignature(
			final WorldPoint start,
			final Set<WorldPoint> targets,
			final Map<String, Object> config)
		{
			this.start = start;
			this.targets = targets;
			this.config = config;
		}

		@Override
		public boolean equals(final Object value)
		{
			if (this == value)
			{
				return true;
			}
			if (!(value instanceof RequestSignature))
			{
				return false;
			}
			final RequestSignature other = (RequestSignature) value;
			return Objects.equals(start, other.start)
				&& targets.equals(other.targets)
				&& config.equals(other.config);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(start, targets, config);
		}

		private boolean hasSameEndpoints(final RequestSignature other)
		{
			return other != null
				&& Objects.equals(start, other.start)
				&& targets.equals(other.targets);
		}

		private Map<String, Object> toMessageData()
		{
			final Map<String, Object> data = new HashMap<>();
			if (start != null)
			{
				data.put("start", start);
			}
			data.put("target", targets);
			data.put("config", config);
			return data;
		}
	}

	private static final class TransportResponse
	{
		private final List<WorldPoint> origins;
		private final List<WorldPoint> destinations;

		private TransportResponse(
			final List<WorldPoint> origins,
			final List<WorldPoint> destinations)
		{
			this.origins = origins;
			this.destinations = destinations;
		}

		private boolean isEmpty()
		{
			return origins.isEmpty();
		}
	}

	public ShortestPathBridge(final EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

	public boolean routeTo(
		final WorldPoint destination,
		final boolean avoidWilderness)
	{
		return routeTo(
			destination,
			avoidWilderness,
			false,
			false
		);
	}

	public boolean routeTo(
		final WorldPoint destination,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath)
	{
		if (destination == null)
		{
			return false;
		}

		return postPath(
			null,
			destination,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			null,
			true,
			false
		);
	}

	/**
	 * Routes using only teleports the player currently carries/wears while still
	 * asking Shortest Path to publish the exact transport edge it selected. This
	 * is used for post-task return-to-master guidance: SlayerPlus must highlight
	 * the carried teleport without allowing Shortest Path to detour through a bank.
	 */
	public boolean routeToTrackedInventory(
		final WorldPoint destination,
		final boolean avoidWilderness)
	{
		if (destination == null)
		{
			return false;
		}

		return postPath(
			null,
			destination,
			avoidWilderness,
			false,
			false,
			null,
			true,
			false,
			true
		);
	}

	public boolean routeToAny(
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath)
	{
		return routeToAny(
			null,
			destinations,
			avoidWilderness,
			useBankItems,
			includeBankPath
		);
	}

	/**
	 * Re-bases a multi-destination route on an explicit live player position.
	 * This is important immediately after a teleport because Shortest Path may
	 * otherwise still be resolving the route from the pre-teleport scene.
	 */
	public boolean routeToAny(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath)
	{
		if (destinations == null || destinations.isEmpty())
		{
			return false;
		}

		final Set<WorldPoint> safeDestinations = new LinkedHashSet<>();
		for (final WorldPoint destination : destinations)
		{
			if (destination != null)
			{
				safeDestinations.add(destination);
			}
		}

		if (safeDestinations.isEmpty())
		{
			return false;
		}

		return postPath(
			start,
			safeDestinations,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			null,
			true,
			false
		);
	}

	/**
	 * Routes to a reviewed final-preparation bank target. SlayerPlus supplies the
	 * destination from Shortest Path's own bank catalog. Catalog entries can be
	 * occupied bank-object tiles, so arriving on an adjacent interaction tile is
	 * valid. Once the named banker is loaded, SlayerPlus replaces the catalog point
	 * with a collision-checked live-NPC perimeter tile.
	 */
	public boolean routeToPreparationBank(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath)
	{
		if (destinations == null || destinations.isEmpty())
		{
			return false;
		}

		final Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
		return !safeDestinations.isEmpty() && postPath(
			start,
			safeDestinations,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			PREPARATION_BANK_UNREACHABLE_DISTANCE,
			true,
			false
		);
	}

	static int preparationBankApproachThresholdForRegression()
	{
		return PREPARATION_BANK_UNREACHABLE_DISTANCE;
	}

	/**
	 * Routes to a reviewed manual access boundary, such as Mor Ul Rek's Hot vent
	 * door. The object tile itself may be blocked, so arriving on an adjacent tile
	 * is valid; the interaction remains entirely player-controlled.
	 */
	public boolean routeToPreparationAccess(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath)
	{
		if (destinations == null || destinations.isEmpty())
		{
			return false;
		}

		final Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
		return !safeDestinations.isEmpty() && postPath(
			start,
			safeDestinations,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			PREPARATION_ACCESS_UNREACHABLE_DISTANCE,
			true,
			false,
			useBankItems,
			false
		);
	}

	static int preparationAccessApproachThresholdForRegression()
	{
		return PREPARATION_ACCESS_UNREACHABLE_DISTANCE;
	}

	/**
	 * Routes to a small task-area target set. Dungeon room centers and NPC
	 * world locations can be blocked tiles, so the target set lets Shortest
	 * Path choose a reachable adjacent tile. A 12-tile unreachable threshold
	 * suppresses false warnings while a valid perimeter route is still being
	 * drawn. It applies only to task-area guidance and does not weaken bank or
	 * Slayer-master routes.
	 */
	public boolean routeToTaskArea(
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath)
	{
		return routeToTaskArea(
			destinations,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			true
		);
	}

	/**
	 * Routes to a task area with explicit control over water transports. Some
	 * dungeon entrances are near coastline transport nodes; disabling boats and
	 * ships prevents Shortest Path from selecting an unrelated offshore leg.
	 */
	public boolean routeToTaskArea(
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final boolean allowWaterTransports)
	{
		return routeToTaskArea(
			null,
			destinations,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			allowWaterTransports
		);
	}

	/**
	 * Sends the player's exact template-aware start together with the target.
	 * Shortest Path otherwise derives the start from Player#getWorldLocation(),
	 * which is not instance-aware in its plugin-message handler.
	 */
	public boolean routeToTaskArea(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final boolean allowWaterTransports)
	{
		return routeToTaskArea(
			start,
			destinations,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			allowWaterTransports,
			false
		);
	}

	/**
	 * Routes to a task while optionally making reusable teleport items expensive.
	 * This lets a reviewed direct consumable (such as Spider cave teleport) win
	 * over a longer free Max-cape/POH detour without disabling Shortest Path.
	 */
	public boolean routeToTaskArea(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final boolean allowWaterTransports,
		final boolean suppressReusableTeleportItems)
	{
		if (destinations == null || destinations.isEmpty())
		{
			return false;
		}

		final Set<WorldPoint> safeDestinations =
			sanitizeTargets(destinations);

		if (safeDestinations.isEmpty())
		{
			return false;
		}

		return postPath(
			start,
			safeDestinations,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			TASK_AREA_UNREACHABLE_DISTANCE,
			allowWaterTransports,
			false,
			true,
			true,
			false,
			suppressReusableTeleportItems
		);
	}

	/**
	 * Routes to a manually operated spellbook-change point after SlayerPlus has
	 * scanned the bank. Shortest Path may compare carried and bank-owned
	 * teleports and show its withdrawal guidance, but it must not publish that
	 * temporary transport back as the Slayer task's authoritative travel item.
	 * When the player is already inside a POH, callers omit {@code start} so
	 * Shortest Path can perform its own template-aware instance conversion.
	 */
	public boolean routeToSpellbookChange(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness)
	{
		return routeToSpellbookChange(
			start, destinations, avoidWilderness, false
		);
	}

	public boolean routeToSpellbookChange(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean suppressReusableTeleportItems)
	{
		final Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
		return !safeDestinations.isEmpty() && postPath(
			start,
			safeDestinations,
			avoidWilderness,
			true,
			true,
			TASK_AREA_UNREACHABLE_DISTANCE,
			true,
			false,
			false,
			true,
			true,
			suppressReusableTeleportItems
		);
	}

	/**
	 * Replaces the current route with a scene-local task route. Underground and
	 * instanced coordinates are valid for the minimap/tile overlays but map to
	 * meaningless ocean positions on the overworld map. A local route therefore
	 * disables world-map drawing. The plugin-message API accepts an exact single
	 * target, so SlayerPlus must never invent adjacent tiles around a reviewed
	 * destination; those tiles may be blocked, water, or across an entrance.
	 */
	public boolean routeToLocalTaskArea(
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness)
	{
		return routeToLocalTaskArea(null, destinations, avoidWilderness);
	}

	/**
	 * Routes to one already collision-checked live destination. Unlike the broad
	 * task-area helper, this exact endpoint must itself be reached; allowing a
	 * non-zero unreachable threshold here would recreate the misleading
	 * "destination could not be reached" state around bank/NPC interaction tiles.
	 */
	public boolean routeToExactLocalTarget(
		final WorldPoint start,
		final WorldPoint destination,
		final boolean avoidWilderness)
	{
		if (destination == null)
		{
			return false;
		}
		return postPath(
			start,
			destination,
			avoidWilderness,
			false,
			false,
			0,
			false,
			true
		);
	}

	/**
	 * Routes locally to a catalog bank point. Unlike a live collision-checked NPC
	 * perimeter tile, the catalog point may be occupied by the bank object itself,
	 * so the normal two-tile bank arrival tolerance must remain enabled.
	 */
	public boolean routeToLocalBankTarget(
		final WorldPoint start,
		final WorldPoint destination,
		final boolean avoidWilderness)
	{
		if (destination == null)
		{
			return false;
		}
		return postPath(
			start,
			destination,
			avoidWilderness,
			false,
			false,
			PREPARATION_BANK_UNREACHABLE_DISTANCE,
			false,
			true
		);
	}

	public boolean routeToLocalTaskArea(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness)
	{
		return routeToLocalTaskArea(
			start, destinations, avoidWilderness, true
		);
	}

	public boolean routeToLocalTaskArea(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useAgilityShortcuts)
	{
		if (destinations == null || destinations.isEmpty())
		{
			return false;
		}

		final Set<WorldPoint> safeDestinations =
			sanitizeTargets(destinations);
		return !safeDestinations.isEmpty() && postPath(
			start,
			safeDestinations,
			avoidWilderness,
			false,
			false,
			LOCAL_TASK_UNREACHABLE_DISTANCE,
			false,
			true,
			false,
			true,
			false,
			false,
			useAgilityShortcuts
		);
	}

	/**
	 * Preserve only the reviewed targets supplied by SlayerPlus. Shortest Path's
	 * plugin-message API accepts WorldPoint sets directly and performs its own
	 * destination filtering, so expanding a singleton into guessed neighbours is
	 * both unnecessary and unsafe.
	 */
	private static Set<WorldPoint> sanitizeTargets(
		final Set<WorldPoint> destinations)
	{
		final Set<WorldPoint> targets = new LinkedHashSet<>();
		for (final WorldPoint destination : destinations)
		{
			if (destination != null)
			{
				targets.add(destination);
			}
		}
		return targets;
	}

	/**
	 * Routes to the reviewed surface approach area for a cave or dungeon. The
	 * catalog, rather than the bridge, supplies this area so SlayerPlus never
	 * invents neighbours around an underground or method-specific destination.
	 * A set is important here because the entrance object tile itself may be
	 * collision-blocked while one of its surrounding approach tiles is valid.
	 */
	public boolean routeToTaskAccess(
		final WorldPoint start,
		final Set<WorldPoint> destinations,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final boolean allowWaterTransports)
	{
		if (destinations == null || destinations.isEmpty())
		{
			return false;
		}

		final Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
		return !safeDestinations.isEmpty() && postPath(
			start,
			safeDestinations,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			TASK_AREA_UNREACHABLE_DISTANCE,
			allowWaterTransports,
			false,
			true
		);
	}

	private boolean postPath(
		final WorldPoint start,
		final Object target,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final Integer unreachableTargetDistance,
		final boolean allowWaterTransports,
		final boolean localOnly)
	{
		return postPath(
			start,
			target,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			unreachableTargetDistance,
			allowWaterTransports,
			localOnly,
			useBankItems
		);
	}

	private boolean postPath(
		final WorldPoint start,
		final Object target,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final Integer unreachableTargetDistance,
		final boolean allowWaterTransports,
		final boolean localOnly,
		final boolean postTransportUpdates)
	{
		return postPath(
			start,
			target,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			unreachableTargetDistance,
			allowWaterTransports,
			localOnly,
			postTransportUpdates,
			true
		);
	}

	private boolean postPath(
		final WorldPoint start,
		final Object target,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final Integer unreachableTargetDistance,
		final boolean allowWaterTransports,
		final boolean localOnly,
		final boolean postTransportUpdates,
		final boolean allowPoh)
	{
		return postPath(
			start,
			target,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			unreachableTargetDistance,
			allowWaterTransports,
			localOnly,
			postTransportUpdates,
			allowPoh,
			false
		);
	}

	private boolean postPath(
		final WorldPoint start,
		final Object target,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final Integer unreachableTargetDistance,
		final boolean allowWaterTransports,
		final boolean localOnly,
		final boolean postTransportUpdates,
		final boolean allowPoh,
		final boolean showBankPickupInfo)
	{
		return postPath(
			start,
			target,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			unreachableTargetDistance,
			allowWaterTransports,
			localOnly,
			postTransportUpdates,
			allowPoh,
			showBankPickupInfo,
			false
		);
	}

	private boolean postPath(
		final WorldPoint start,
		final Object target,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final Integer unreachableTargetDistance,
		final boolean allowWaterTransports,
		final boolean localOnly,
		final boolean postTransportUpdates,
		final boolean allowPoh,
		final boolean showBankPickupInfo,
		final boolean suppressReusableTeleportItems)
	{
		return postPath(
			start,
			target,
			avoidWilderness,
			useBankItems,
			includeBankPath,
			unreachableTargetDistance,
			allowWaterTransports,
			localOnly,
			postTransportUpdates,
			allowPoh,
			showBankPickupInfo,
			suppressReusableTeleportItems,
			true
		);
	}

	private boolean postPath(
		final WorldPoint start,
		final Object target,
		final boolean avoidWilderness,
		final boolean useBankItems,
		final boolean includeBankPath,
		final Integer unreachableTargetDistance,
		final boolean allowWaterTransports,
		final boolean localOnly,
		final boolean postTransportUpdates,
		final boolean allowPoh,
		final boolean showBankPickupInfo,
		final boolean suppressReusableTeleportItems,
		final boolean useAgilityShortcuts)
	{
		if (eventBus == null || target == null)
		{
			return false;
		}

		/*
		 * "Inventory and Bank" only exposes bank-owned teleport items to the
		 * pathfinder. Shortest Path also requires includeBankPath so the route
		 * state may visit a bank, withdraw the selected item, and continue to
		 * the final destination. Keep both settings coupled so a caller cannot
		 * accidentally request bank items without a bank-aware route.
		 */
		final boolean bankAwarePath = useBankItems || includeBankPath;
		final Map<String, Object> overrides = new HashMap<>();
		overrides.put("avoidWilderness", avoidWilderness);
		/*
		 * Most Slayer routes should use every agility shortcut the current character
		 * qualifies for. A caller may disable them for a reviewed stage whose
		 * Shortest Path transport record omits an external unlock requirement.
		 */
		overrides.put("useAgilityShortcuts", useAgilityShortcuts);
		overrides.put("costAgilityShortcuts", 0);
		overrides.put("drawMap", !localOnly);
		if (localOnly)
		{
			overrides.put("drawMap", false);
			overrides.put("showBankPickupInfo", false);
			overrides.put("postTransports", false);
			overrides.put("useTeleportationItems", "None");
			overrides.put("useTeleportationSpells", false);
			overrides.put("useTeleportationSpellsHome", false);
			overrides.put("useTeleportationMinigames", false);
			overrides.put("useTeleportationLevers", false);
			overrides.put("useTeleportationPortals", false);
			overrides.put("usePoh", false);
		}
		else if (!allowPoh)
		{
			/*
			 * Shortest Path models POH utilities as one abstract hub. Recalculating
			 * inside a player's real instanced room layout can therefore report the
			 * destination unreachable even when the selected fairy-ring code is valid.
			 * Reviewed access stages may opt out and use an overworld ring instead.
			 */
			overrides.put("usePoh", false);
			/*
			 * Older Shortest Path releases did not consistently apply the master POH
			 * gate to every house transport family. Override each family as well so
			 * bank-owned house tablets/capes cannot strand an instanced route.
			 */
			overrides.put("usePohFairyRing", false);
			overrides.put("usePohSpiritTree", false);
			overrides.put("useTeleportationPortalsPoh", false);
			overrides.put("usePohMountedItems", false);
			overrides.put("usePohObelisk", false);
			overrides.put("pohJewelleryBoxTier", "None");
		}
		overrides.put("includeBankPath", bankAwarePath);
		if (localOnly)
		{
			overrides.put("showTransportInfo", false);
			overrides.put("showBankPickupInfo", false);
			overrides.put("postTransports", false);
		}
		else
		{
			/*
			 * Keep Shortest Path's native transport instruction visible throughout
			 * bank discovery. Hiding it also removes the only route text before the
			 * selected item reaches the inventory. Its separate generic "Pick up"
			 * summary remains disabled; SlayerPlus owns that bank recommendation and
			 * replaces the discovery route with an inventory-only route as soon as the
			 * selected physical teleport is carried.
			 */
			overrides.put("showTransportInfo", true);
			overrides.put("showBankPickupInfo", false);
			overrides.put(
				"postTransports",
				shouldPostTransportUpdates(
					useBankItems,
					postTransportUpdates,
					localOnly
				)
			);
		}
		if (!allowWaterTransports)
		{
			/*
			 * These are the current Shortest Path transport configuration keys.
			 * Disable every water family together so a land dungeon entrance near
			 * the coast cannot be replaced by a boat or ship destination.
			 */
			overrides.put("useBoats", false);
			overrides.put("useShips", false);
			overrides.put("useCharterShips", false);
			overrides.put("useCanoes", false);
		}
		if (suppressReusableTeleportItems)
		{
			/*
			 * Shortest Path's static quest-point requirement can lag a newly released
			 * quest. An inactive Quest cape is a reusable teleport, so make that class
			 * noncompetitive while leaving consumable bank teleports available.
			 */
			overrides.put("costNonConsumableTeleportationItems", 1_000_000);
		}
		if (!localOnly)
		{
			overrides.put(
				"useTeleportationItems",
				useBankItems
					? BANK_AND_INVENTORY_TELEPORTS
					: INVENTORY_TELEPORTS
			);
		}
		if (unreachableTargetDistance != null)
		{
			overrides.put(
				"unreachableTargetDistanceThreshold",
				unreachableTargetDistance
			);
		}

		final Map<String, Object> data = new HashMap<>();
		if (start != null)
		{
			data.put("start", start);
		}
		data.put("target", target);
		data.put("config", overrides);

		final Set<WorldPoint> requestTargets = canonicalTargets(target);
		if (requestTargets.isEmpty())
		{
			return false;
		}
		final Map<String, Object> requestConfig =
			Collections.unmodifiableMap(new HashMap<>(overrides));
		final RequestSignature signature = new RequestSignature(
			start,
			requestTargets,
			requestConfig
		);
		final boolean expectsTransportResponse = Boolean.TRUE.equals(
			overrides.get("postTransports")
		);
		final TransportRequestToken nextRequest;
		synchronized (requestLock)
		{
			if (activeRequest != null
				&& activeRequest.signature.equals(signature))
			{
				/* Shortest Path continuously recalculates an active route as the
				 * player moves. Reposting the same request only creates duplicate
				 * workers and late transport callbacks, so coalesce it. */
				return true;
			}

			final TransportRequestToken previousRequest = activeRequest;
			final boolean supersedesExistingRequest = previousRequest != null;
			final boolean canReceiveRetiredResponse = supersedesExistingRequest
				&& previousRequest.expectsTransportResponse
				&& acceptedRequestSequence != previousRequest.sequence;
			if (canReceiveRetiredResponse)
			{
				rememberRetiredRequest(previousRequest.signature);
			}

			/* Publish a real clear barrier while no local request is claimable. The
			 * installed Shortest Path protocol has no response request id, so making
			 * the replacement token active before this synchronous clear would allow
			 * the retiring worker to claim the replacement token. */
			activeRequest = null;
			reservedRequestSequence = -1L;
			if (supersedesExistingRequest)
			{
				try
				{
					eventBus.post(new PluginMessage(NAMESPACE, CLEAR_MESSAGE));
				}
				catch (final RuntimeException ex)
				{
					return false;
				}
			}

			nextRequest = new TransportRequestToken(
				++nextRequestSequence,
				signature,
				requestTargets,
				expectsTransportResponse,
				canReceiveRetiredResponse && expectsTransportResponse
			);
			activeRequest = nextRequest;
			reservedRequestSequence = -1L;
			try
			{
				eventBus.post(new PluginMessage(NAMESPACE, PATH_MESSAGE, data));
			}
			catch (final RuntimeException ex)
			{
				if (activeRequest == nextRequest)
				{
					activeRequest = null;
					reservedRequestSequence = -1L;
				}
				return false;
			}
		}
		return true;
	}

	private void rememberRetiredRequest(final RequestSignature signature)
	{
		if (signature == null)
		{
			return;
		}
		retiredRequests.remove(signature);
		retiredRequests.addFirst(signature);
		while (retiredRequests.size() > MAX_RETIRED_REQUESTS)
		{
			retiredRequests.removeLast();
		}
	}

	private static Set<WorldPoint> canonicalTargets(final Object target)
	{
		final Set<WorldPoint> targets = new LinkedHashSet<>();
		if (target instanceof WorldPoint)
		{
			targets.add((WorldPoint) target);
		}
		else if (target instanceof Iterable<?>)
		{
			for (final Object value : (Iterable<?>) target)
			{
				if (value instanceof WorldPoint)
				{
					targets.add((WorldPoint) value);
				}
			}
		}
		else if (target instanceof Object[])
		{
			for (final Object value : (Object[]) target)
			{
				if (value instanceof WorldPoint)
				{
					targets.add((WorldPoint) value);
				}
			}
		}
		return Collections.unmodifiableSet(targets);
	}

	/**
	 * Returns the active request only while it can still accept a transport
	 * response. This method is observational; callers that are about to copy or
	 * queue a response payload must use {@link #claimPendingTransportResponse()}
	 * so the eligibility check and reservation happen atomically.
	 */
	TransportRequestToken currentPendingTransportRequest()
	{
		synchronized (requestLock)
		{
			final TransportRequestToken request = activeRequest;
			return isPendingTransportRequest(request) ? request : null;
		}
	}

	/**
	 * Atomically reserves the sole response slot for the active transport
	 * request. A second callback receives {@code null} until the first callback
	 * either accepts its payload or releases/rejects its reservation.
	 */
	TransportRequestToken claimPendingTransportResponse()
	{
		synchronized (requestLock)
		{
			final TransportRequestToken request = activeRequest;
			if (!isPendingTransportRequest(request))
			{
				return null;
			}
			reservedRequestSequence = request.sequence;
			return request;
		}
	}

	/**
	 * Releases a queued response without reopening a superseded or already
	 * accepted request. Call this when lifecycle checks reject a claimed
	 * callback before its payload reaches {@link #tryAcceptTransportResponse}.
	 */
	void releaseTransportResponseClaim(
		final TransportRequestToken request)
	{
		synchronized (requestLock)
		{
			if (isActiveTransportRequest(request)
				&& reservedRequestSequence == request.sequence
				&& acceptedRequestSequence != request.sequence)
			{
				reservedRequestSequence = -1L;
			}
		}
	}

	private boolean isPendingTransportRequest(
		final TransportRequestToken request)
	{
		return isActiveTransportRequest(request)
			&& request.expectsTransportResponse
			&& acceptedRequestSequence != request.sequence
			&& reservedRequestSequence != request.sequence;
	}

	private boolean isActiveTransportRequest(
		final TransportRequestToken request)
	{
		return request != null
			&& activeRequest != null
			&& activeRequest.sequence == request.sequence
			&& activeRequest.signature.equals(request.signature)
			&& activeRequest.targets.equals(request.targets);
	}

	boolean tryAcceptTransportResponse(
		final TransportRequestToken request,
		final Map<String, Object> data)
	{
		if (request == null
			|| !request.expectsTransportResponse)
		{
			return false;
		}

		/* Validate outside the lock, but only after the callback has atomically
		 * claimed this request. A malformed or irrelevant payload must release its
		 * reservation so a later valid response can still be considered. */
		synchronized (requestLock)
		{
			if (!isActiveTransportRequest(request)
				|| acceptedRequestSequence == request.sequence
				|| reservedRequestSequence != request.sequence)
			{
				return false;
			}
		}
		final TransportResponse response = parseTransportResponse(data);
		if (response == null)
		{
			releaseTransportResponseClaim(request);
			return false;
		}

		final List<RequestSignature> retiredSnapshot;
		synchronized (requestLock)
		{
			if (!isActiveTransportRequest(request)
				|| acceptedRequestSequence == request.sequence
				|| reservedRequestSequence != request.sequence)
			{
				return false;
			}
			retiredSnapshot = new ArrayList<>(retiredRequests);
		}

		if (request.ambiguityRetryAllowed
			&& responseNeedsBarrierRetry(
				request.signature,
				response,
				retiredSnapshot
			))
		{
			retryAmbiguousTransportRequest(request);
			return false;
		}

		/* Shortest Path legitimately posts four empty parallel arrays when the
		 * selected path is walking-only. Once the request has no retired-response
		 * ambiguity (or has crossed the one-time retry barrier), that empty result is
		 * authoritative and must consume the response slot. */
		if (!response.isEmpty()
			&& distanceToNearestTarget(
				response.destinations.get(response.destinations.size() - 1),
				request.targets
			) > MAX_RELEVANT_EDGE_DISTANCE)
		{
			releaseTransportResponseClaim(request);
			return false;
		}

		synchronized (requestLock)
		{
			if (!isActiveTransportRequest(request)
				|| acceptedRequestSequence == request.sequence
				|| reservedRequestSequence != request.sequence)
			{
				return false;
			}
			acceptedRequestSequence = request.sequence;
			reservedRequestSequence = -1L;
			retiredRequests.clear();
			return true;
		}
	}

	private boolean retryAmbiguousTransportRequest(
		final TransportRequestToken request)
	{
		synchronized (requestLock)
		{
			if (!isActiveTransportRequest(request)
				|| !request.ambiguityRetryAllowed
				|| acceptedRequestSequence == request.sequence
				|| reservedRequestSequence != request.sequence)
			{
				return false;
			}

			/* The first indistinguishable callback after supersession is quarantined.
			 * Clear and reproduce the immutable request exactly once; the replacement
			 * token is barrier-confirmed and therefore cannot recurse into another
			 * retry. If this first callback was already the valid current result, the
			 * repost regenerates it instead of permanently dropping it. */
			activeRequest = null;
			reservedRequestSequence = -1L;
			try
			{
				eventBus.post(new PluginMessage(NAMESPACE, CLEAR_MESSAGE));
			}
			catch (final RuntimeException ex)
			{
				return false;
			}

			final TransportRequestToken replacement = new TransportRequestToken(
				++nextRequestSequence,
				request.signature,
				request.targets,
				request.expectsTransportResponse,
				false
			);
			activeRequest = replacement;
			try
			{
				eventBus.post(new PluginMessage(
					NAMESPACE,
					PATH_MESSAGE,
					request.signature.toMessageData()
				));
				return true;
			}
			catch (final RuntimeException ex)
			{
				if (activeRequest == replacement)
				{
					activeRequest = null;
					reservedRequestSequence = -1L;
				}
				return false;
			}
		}
	}

	private static boolean responseNeedsBarrierRetry(
		final RequestSignature current,
		final TransportResponse response,
		final List<RequestSignature> retired)
	{
		if (current == null || retired == null || retired.isEmpty())
		{
			return false;
		}
		if (response.isEmpty())
		{
			return true;
		}

		long bestRetiredAffinity = Long.MAX_VALUE;
		for (final RequestSignature previous : retired)
		{
			/* A config-only supersession has identical geometry. No response field can
			 * distinguish which config produced the transport list, so force the one
			 * clear/repost barrier rather than guessing. */
			if (current.hasSameEndpoints(previous))
			{
				return true;
			}
			bestRetiredAffinity = Math.min(
				bestRetiredAffinity,
				endpointAffinity(previous, response)
			);
		}

		if (bestRetiredAffinity == Long.MAX_VALUE)
		{
			return false;
		}
		final long currentAffinity = endpointAffinity(current, response);
		return currentAffinity == Long.MAX_VALUE
			|| currentAffinity + ENDPOINT_AFFINITY_MARGIN
				>= bestRetiredAffinity;
	}

	private static long endpointAffinity(
		final RequestSignature signature,
		final TransportResponse response)
	{
		if (signature == null || response == null || response.isEmpty())
		{
			return Long.MAX_VALUE;
		}
		final long targetDistance = distanceToNearestTarget(
			response.destinations.get(response.destinations.size() - 1),
			signature.targets
		);
		if (targetDistance == Long.MAX_VALUE)
		{
			return Long.MAX_VALUE;
		}

		long affinity = targetDistance;
		if (signature.start != null)
		{
			final WorldPoint firstOrigin = response.origins.get(0);
			if (signature.start.getPlane() == firstOrigin.getPlane())
			{
				affinity += chebyshevDistance(signature.start, firstOrigin);
			}
		}
		return affinity;
	}

	private static long distanceToNearestTarget(
		final WorldPoint point,
		final Set<WorldPoint> targets)
	{
		if (point == null || targets == null || targets.isEmpty())
		{
			return Long.MAX_VALUE;
		}
		long closest = Long.MAX_VALUE;
		for (final WorldPoint target : targets)
		{
			if (target != null && point.getPlane() == target.getPlane())
			{
				closest = Math.min(closest, chebyshevDistance(point, target));
			}
		}
		return closest;
	}

	private static long chebyshevDistance(
		final WorldPoint first,
		final WorldPoint second)
	{
		return Math.max(
			Math.abs((long) first.getX() - second.getX()),
			Math.abs((long) first.getY() - second.getY())
		);
	}

	private static TransportResponse parseTransportResponse(
		final Map<String, Object> data)
	{
		if (data == null
			|| !data.containsKey("origin")
			|| !data.containsKey("destination")
			|| !data.containsKey("objectInfo")
			|| !data.containsKey("displayInfo"))
		{
			return null;
		}
		final List<?> originValues = responseValues(data.get("origin"));
		final List<?> destinationValues = responseValues(data.get("destination"));
		final List<?> objectInfos = responseValues(data.get("objectInfo"));
		final List<?> displayInfos = responseValues(data.get("displayInfo"));
		if (originValues == null
			|| destinationValues == null
			|| objectInfos == null
			|| displayInfos == null
			|| originValues.size() != destinationValues.size()
			|| originValues.size() != objectInfos.size()
			|| originValues.size() != displayInfos.size())
		{
			return null;
		}

		final List<WorldPoint> origins = new ArrayList<>(originValues.size());
		final List<WorldPoint> destinations = new ArrayList<>(
			destinationValues.size()
		);
		for (int index = 0; index < originValues.size(); index++)
		{
			final Object origin = originValues.get(index);
			final Object destination = destinationValues.get(index);
			final Object objectInfo = objectInfos.get(index);
			final Object displayInfo = displayInfos.get(index);
			if (!(origin instanceof WorldPoint)
				|| !(destination instanceof WorldPoint)
				|| (objectInfo != null && !(objectInfo instanceof String))
				|| (displayInfo != null && !(displayInfo instanceof String)))
			{
				return null;
			}
			origins.add((WorldPoint) origin);
			destinations.add((WorldPoint) destination);
		}
		return new TransportResponse(origins, destinations);
	}

	private static List<?> responseValues(final Object value)
	{
		if (value instanceof List<?>)
		{
			return (List<?>) value;
		}
		if (value instanceof Object[])
		{
			return new ArrayList<>(java.util.Arrays.asList((Object[]) value));
		}
		return null;
	}

	static boolean showsNativeTransportInfoForRegression(
		final boolean useBankItems,
		final boolean includeBankPath)
	{
		return true;
	}

	static boolean shouldPostTransportUpdates(
		final boolean useBankItems,
		final boolean explicitlyTrackInventoryRoute,
		final boolean localOnly)
	{
		/*
		 * Normal bank-aware callers pass useBankItems through as the explicit
		 * tracking flag. A preparation-only route may deliberately opt out so its
		 * temporary teleport never replaces the task's travel selection.
		 */
		return !localOnly && explicitlyTrackInventoryRoute;
	}

	public void clear()
	{
		synchronized (requestLock)
		{
			activeRequest = null;
			acceptedRequestSequence = -1L;
			reservedRequestSequence = -1L;
			retiredRequests.clear();
			++nextRequestSequence;
		}
		if (eventBus != null)
		{
			eventBus.post(
				new PluginMessage(NAMESPACE, CLEAR_MESSAGE)
			);
		}
	}
}
