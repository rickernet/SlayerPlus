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

public final class ShortestPathBridge {
  private static final String NAMESPACE = "shortestpath";
  private static final String PATH_MESSAGE = "path";
  private static final String CLEAR_MESSAGE = "clear";
  private static final String INVENTORY_TELEPORTS = "Inventory";
  private static final String BANK_AND_INVENTORY_TELEPORTS = "Inventory and Bank";
  private static final int TASK_AREA_UNREACHABLE_DISTANCE = 12;
  private static final int PREPARATION_BANK_UNREACHABLE_DISTANCE = 2;
  private static final int PREPARATION_ACCESS_UNREACHABLE_DISTANCE = 2;
  private static final int LOCAL_TASK_UNREACHABLE_DISTANCE = 6;
  private static final int MAX_RELEVANT_EDGE_DISTANCE = 1_024;
  private static final int MAX_RETIRED_REQUESTS = 4;
  private static final int ENDPOINT_AFFINITY_MARGIN = 32;
  private final EventBus eventBus;
  private final Object requestLock = new Object();
  private long nextRequestSequence;
  private long acceptedRequestSequence = -1L;
  private long reservedRequestSequence = -1L;
  private volatile TransportRequestToken activeRequest;
  private final Deque<RequestSignature> retiredRequests = new ArrayDeque<>(MAX_RETIRED_REQUESTS);

  static final class TransportRequestToken {
    private final long sequence;
    private final RequestSignature signature;
    private final Set<WorldPoint> targets;
    private final boolean expectsTransportResponse;
    private final boolean ambiguityRetryAllowed;

    private TransportRequestToken(
        long sequence,
        RequestSignature signature,
        Set<WorldPoint> targets,
        boolean expectsTransportResponse,
        boolean ambiguityRetryAllowed) {
      this.sequence = sequence;
      this.signature = signature;
      this.targets = targets;
      this.expectsTransportResponse = expectsTransportResponse;
      this.ambiguityRetryAllowed = ambiguityRetryAllowed;
    }

    Set<WorldPoint> getTargetsForRegression() {
      return targets;
    }
  }

  private static final class RequestSignature {
    private final WorldPoint start;
    private final Set<WorldPoint> targets;
    private final Map<String, Object> config;

    private RequestSignature(
        WorldPoint start, Set<WorldPoint> targets, Map<String, Object> config) {
      this.start = start;
      this.targets = targets;
      this.config = config;
    }

    @Override
    public boolean equals(Object value) {
      if (this == value) {
        return true;
      }
      if (!(value instanceof RequestSignature)) {
        return false;
      }
      RequestSignature other = (RequestSignature) value;
      return Objects.equals(start, other.start)
          && targets.equals(other.targets)
          && config.equals(other.config);
    }

    @Override
    public int hashCode() {
      return Objects.hash(start, targets, config);
    }

    private boolean hasSameEndpoints(RequestSignature other) {
      return other != null && Objects.equals(start, other.start) && targets.equals(other.targets);
    }

    private Map<String, Object> toMessageData() {
      Map<String, Object> data = new HashMap<>();
      if (start != null) {
        data.put("start", start);
      }
      data.put("target", targets);
      data.put("config", config);
      return data;
    }
  }

  private static final class TransportResponse {
    private final List<WorldPoint> origins;
    private final List<WorldPoint> destinations;

    private TransportResponse(List<WorldPoint> origins, List<WorldPoint> destinations) {
      this.origins = origins;
      this.destinations = destinations;
    }

    private boolean isEmpty() {
      return origins.isEmpty();
    }
  }

  public ShortestPathBridge(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public boolean routeTo(WorldPoint destination, boolean avoidWilderness) {
    return routeTo(destination, avoidWilderness, false, false);
  }

  public boolean routeTo(
      WorldPoint destination,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath) {
    if (destination == null) {
      return false;
    }
    return postPath(
        null, destination, avoidWilderness, useBankItems, includeBankPath, null, true, false);
  }

  public boolean routeToTrackedInventory(WorldPoint destination, boolean avoidWilderness) {
    if (destination == null) {
      return false;
    }
    return postPath(null, destination, avoidWilderness, false, false, null, true, false, true);
  }

  public boolean routeToAny(
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath) {
    return routeToAny(null, destinations, avoidWilderness, useBankItems, includeBankPath);
  }

  public boolean routeToAny(
      WorldPoint start,
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath) {
    if (destinations == null || destinations.isEmpty()) {
      return false;
    }
    Set<WorldPoint> safeDestinations = new LinkedHashSet<>();
    for (WorldPoint destination : destinations) {
      if (destination != null) {
        safeDestinations.add(destination);
      }
    }
    if (safeDestinations.isEmpty()) {
      return false;
    }
    return postPath(
        start, safeDestinations, avoidWilderness, useBankItems, includeBankPath, null, true, false);
  }

  public boolean routeToPreparationBank(
      WorldPoint start,
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath) {
    if (destinations == null || destinations.isEmpty()) {
      return false;
    }
    Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
    return !safeDestinations.isEmpty()
        && postPath(
            start,
            safeDestinations,
            avoidWilderness,
            useBankItems,
            includeBankPath,
            PREPARATION_BANK_UNREACHABLE_DISTANCE,
            true,
            false);
  }

  static int preparationBankApproachThresholdForRegression() {
    return PREPARATION_BANK_UNREACHABLE_DISTANCE;
  }

  public boolean routeToPreparationAccess(
      WorldPoint start,
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath) {
    if (destinations == null || destinations.isEmpty()) {
      return false;
    }
    Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
    return !safeDestinations.isEmpty()
        && postPath(
            start,
            safeDestinations,
            avoidWilderness,
            useBankItems,
            includeBankPath,
            PREPARATION_ACCESS_UNREACHABLE_DISTANCE,
            true,
            false,
            useBankItems,
            false);
  }

  static int preparationAccessApproachThresholdForRegression() {
    return PREPARATION_ACCESS_UNREACHABLE_DISTANCE;
  }

  public boolean routeToTaskArea(
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath) {
    return routeToTaskArea(destinations, avoidWilderness, useBankItems, includeBankPath, true);
  }

  public boolean routeToTaskArea(
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      boolean allowWaterTransports) {
    return routeToTaskArea(
        null, destinations, avoidWilderness, useBankItems, includeBankPath, allowWaterTransports);
  }

  public boolean routeToTaskArea(
      WorldPoint start,
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      boolean allowWaterTransports) {
    return routeToTaskArea(
        start,
        destinations,
        avoidWilderness,
        useBankItems,
        includeBankPath,
        allowWaterTransports,
        false);
  }

  public boolean routeToTaskArea(
      WorldPoint start,
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      boolean allowWaterTransports,
      boolean suppressReusableTeleportItems) {
    if (destinations == null || destinations.isEmpty()) {
      return false;
    }
    Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
    if (safeDestinations.isEmpty()) {
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
        suppressReusableTeleportItems);
  }

  public boolean routeToSpellbookChange(
      WorldPoint start, Set<WorldPoint> destinations, boolean avoidWilderness) {
    return routeToSpellbookChange(start, destinations, avoidWilderness, false);
  }

  public boolean routeToSpellbookChange(
      WorldPoint start,
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean suppressReusableTeleportItems) {
    Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
    return !safeDestinations.isEmpty()
        && postPath(
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
            suppressReusableTeleportItems);
  }

  public boolean routeToLocalTaskArea(Set<WorldPoint> destinations, boolean avoidWilderness) {
    return routeToLocalTaskArea(null, destinations, avoidWilderness);
  }

  public boolean routeToExactLocalTarget(
      WorldPoint start, WorldPoint destination, boolean avoidWilderness) {
    if (destination == null) {
      return false;
    }
    return postPath(start, destination, avoidWilderness, false, false, 0, false, true);
  }

  public boolean routeToLocalBankTarget(
      WorldPoint start, WorldPoint destination, boolean avoidWilderness) {
    if (destination == null) {
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
        true);
  }

  public boolean routeToLocalTaskArea(
      WorldPoint start, Set<WorldPoint> destinations, boolean avoidWilderness) {
    return routeToLocalTaskArea(start, destinations, avoidWilderness, true);
  }

  public boolean routeToLocalTaskArea(
      WorldPoint start,
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useAgilityShortcuts) {
    if (destinations == null || destinations.isEmpty()) {
      return false;
    }
    Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
    return !safeDestinations.isEmpty()
        && postPath(
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
            useAgilityShortcuts);
  }

  private static Set<WorldPoint> sanitizeTargets(Set<WorldPoint> destinations) {
    Set<WorldPoint> targets = new LinkedHashSet<>();
    for (WorldPoint destination : destinations) {
      if (destination != null) {
        targets.add(destination);
      }
    }
    return targets;
  }

  public boolean routeToTaskAccess(
      WorldPoint start,
      Set<WorldPoint> destinations,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      boolean allowWaterTransports) {
    if (destinations == null || destinations.isEmpty()) {
      return false;
    }
    Set<WorldPoint> safeDestinations = sanitizeTargets(destinations);
    return !safeDestinations.isEmpty()
        && postPath(
            start,
            safeDestinations,
            avoidWilderness,
            useBankItems,
            includeBankPath,
            TASK_AREA_UNREACHABLE_DISTANCE,
            allowWaterTransports,
            false,
            true);
  }

  private boolean postPath(
      WorldPoint start,
      Object target,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      Integer unreachableTargetDistance,
      boolean allowWaterTransports,
      boolean localOnly) {
    return postPath(
        start,
        target,
        avoidWilderness,
        useBankItems,
        includeBankPath,
        unreachableTargetDistance,
        allowWaterTransports,
        localOnly,
        useBankItems);
  }

  private boolean postPath(
      WorldPoint start,
      Object target,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      Integer unreachableTargetDistance,
      boolean allowWaterTransports,
      boolean localOnly,
      boolean postTransportUpdates) {
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
        true);
  }

  private boolean postPath(
      WorldPoint start,
      Object target,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      Integer unreachableTargetDistance,
      boolean allowWaterTransports,
      boolean localOnly,
      boolean postTransportUpdates,
      boolean allowPoh) {
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
        false);
  }

  private boolean postPath(
      WorldPoint start,
      Object target,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      Integer unreachableTargetDistance,
      boolean allowWaterTransports,
      boolean localOnly,
      boolean postTransportUpdates,
      boolean allowPoh,
      boolean showBankPickupInfo) {
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
        false);
  }

  private boolean postPath(
      WorldPoint start,
      Object target,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      Integer unreachableTargetDistance,
      boolean allowWaterTransports,
      boolean localOnly,
      boolean postTransportUpdates,
      boolean allowPoh,
      boolean showBankPickupInfo,
      boolean suppressReusableTeleportItems) {
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
        true);
  }

  private boolean postPath(
      WorldPoint start,
      Object target,
      boolean avoidWilderness,
      boolean useBankItems,
      boolean includeBankPath,
      Integer unreachableTargetDistance,
      boolean allowWaterTransports,
      boolean localOnly,
      boolean postTransportUpdates,
      boolean allowPoh,
      boolean showBankPickupInfo,
      boolean suppressReusableTeleportItems,
      boolean useAgilityShortcuts) {
    if (eventBus == null || target == null) {
      return false;
    }
    boolean bankAwarePath = useBankItems || includeBankPath;
    Map<String, Object> overrides = new HashMap<>();
    overrides.put("avoidWilderness", avoidWilderness);
    overrides.put("useAgilityShortcuts", useAgilityShortcuts);
    overrides.put("costAgilityShortcuts", 0);
    overrides.put("drawMap", !localOnly);
    if (localOnly) {
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
    } else if (!allowPoh) {
      overrides.put("usePoh", false);
      overrides.put("usePohFairyRing", false);
      overrides.put("usePohSpiritTree", false);
      overrides.put("useTeleportationPortalsPoh", false);
      overrides.put("usePohMountedItems", false);
      overrides.put("usePohObelisk", false);
      overrides.put("pohJewelleryBoxTier", "None");
    }
    overrides.put("includeBankPath", bankAwarePath);
    if (localOnly) {
      overrides.put("showTransportInfo", false);
      overrides.put("showBankPickupInfo", false);
      overrides.put("postTransports", false);
    } else {
      overrides.put("showTransportInfo", true);
      overrides.put("showBankPickupInfo", false);
      overrides.put(
          "postTransports",
          shouldPostTransportUpdates(useBankItems, postTransportUpdates, localOnly));
    }
    if (!allowWaterTransports) {
      overrides.put("useBoats", false);
      overrides.put("useShips", false);
      overrides.put("useCharterShips", false);
      overrides.put("useCanoes", false);
    }
    if (suppressReusableTeleportItems) {
      overrides.put("costNonConsumableTeleportationItems", 1_000_000);
    }
    if (!localOnly) {
      overrides.put(
          "useTeleportationItems",
          useBankItems ? BANK_AND_INVENTORY_TELEPORTS : INVENTORY_TELEPORTS);
    }
    if (unreachableTargetDistance != null) {
      overrides.put("unreachableTargetDistanceThreshold", unreachableTargetDistance);
    }
    Map<String, Object> data = new HashMap<>();
    if (start != null) {
      data.put("start", start);
    }
    data.put("target", target);
    data.put("config", overrides);
    Set<WorldPoint> requestTargets = canonicalTargets(target);
    if (requestTargets.isEmpty()) {
      return false;
    }
    Map<String, Object> requestConfig = Collections.unmodifiableMap(new HashMap<>(overrides));
    RequestSignature signature = new RequestSignature(start, requestTargets, requestConfig);
    boolean expectsTransportResponse = Boolean.TRUE.equals(overrides.get("postTransports"));
    TransportRequestToken nextRequest;
    synchronized (requestLock) {
      if (activeRequest != null && activeRequest.signature.equals(signature)) {
        return true;
      }
      TransportRequestToken previousRequest = activeRequest;
      boolean supersedesExistingRequest = previousRequest != null;
      boolean canReceiveRetiredResponse =
          supersedesExistingRequest
              && previousRequest.expectsTransportResponse
              && acceptedRequestSequence != previousRequest.sequence;
      if (canReceiveRetiredResponse) {
        rememberRetiredRequest(previousRequest.signature);
      }
      activeRequest = null;
      reservedRequestSequence = -1L;
      if (supersedesExistingRequest) {
        try {
          eventBus.post(new PluginMessage(NAMESPACE, CLEAR_MESSAGE));
        } catch (RuntimeException ex) {
          return false;
        }
      }
      nextRequest =
          new TransportRequestToken(
              ++nextRequestSequence,
              signature,
              requestTargets,
              expectsTransportResponse,
              canReceiveRetiredResponse && expectsTransportResponse);
      activeRequest = nextRequest;
      reservedRequestSequence = -1L;
      try {
        eventBus.post(new PluginMessage(NAMESPACE, PATH_MESSAGE, data));
      } catch (RuntimeException ex) {
        if (activeRequest == nextRequest) {
          activeRequest = null;
          reservedRequestSequence = -1L;
        }
        return false;
      }
    }
    return true;
  }

  private void rememberRetiredRequest(RequestSignature signature) {
    if (signature == null) {
      return;
    }
    retiredRequests.remove(signature);
    retiredRequests.addFirst(signature);
    while (retiredRequests.size() > MAX_RETIRED_REQUESTS) {
      retiredRequests.removeLast();
    }
  }

  private static Set<WorldPoint> canonicalTargets(Object target) {
    Set<WorldPoint> targets = new LinkedHashSet<>();
    if (target instanceof WorldPoint) {
      targets.add((WorldPoint) target);
    } else if (target instanceof Iterable<?>) {
      for (Object value : (Iterable<?>) target) {
        if (value instanceof WorldPoint) {
          targets.add((WorldPoint) value);
        }
      }
    } else if (target instanceof Object[]) {
      for (Object value : (Object[]) target) {
        if (value instanceof WorldPoint) {
          targets.add((WorldPoint) value);
        }
      }
    }
    return Collections.unmodifiableSet(targets);
  }

  TransportRequestToken currentPendingTransportRequest() {
    synchronized (requestLock) {
      TransportRequestToken request = activeRequest;
      return isPendingTransportRequest(request) ? request : null;
    }
  }

  TransportRequestToken claimPendingTransportResponse() {
    synchronized (requestLock) {
      TransportRequestToken request = activeRequest;
      if (!isPendingTransportRequest(request)) {
        return null;
      }
      reservedRequestSequence = request.sequence;
      return request;
    }
  }

  void releaseTransportResponseClaim(TransportRequestToken request) {
    synchronized (requestLock) {
      if (isActiveTransportRequest(request)
          && reservedRequestSequence == request.sequence
          && acceptedRequestSequence != request.sequence) {
        reservedRequestSequence = -1L;
      }
    }
  }

  private boolean isPendingTransportRequest(TransportRequestToken request) {
    return isActiveTransportRequest(request)
        && request.expectsTransportResponse
        && acceptedRequestSequence != request.sequence
        && reservedRequestSequence != request.sequence;
  }

  private boolean isActiveTransportRequest(TransportRequestToken request) {
    return request != null
        && activeRequest != null
        && activeRequest.sequence == request.sequence
        && activeRequest.signature.equals(request.signature)
        && activeRequest.targets.equals(request.targets);
  }

  boolean tryAcceptTransportResponse(TransportRequestToken request, Map<String, Object> data) {
    if (request == null || !request.expectsTransportResponse) {
      return false;
    }
    synchronized (requestLock) {
      if (!isActiveTransportRequest(request)
          || acceptedRequestSequence == request.sequence
          || reservedRequestSequence != request.sequence) {
        return false;
      }
    }
    TransportResponse response = parseTransportResponse(data);
    if (response == null) {
      releaseTransportResponseClaim(request);
      return false;
    }
    List<RequestSignature> retiredSnapshot;
    synchronized (requestLock) {
      if (!isActiveTransportRequest(request)
          || acceptedRequestSequence == request.sequence
          || reservedRequestSequence != request.sequence) {
        return false;
      }
      retiredSnapshot = new ArrayList<>(retiredRequests);
    }
    if (request.ambiguityRetryAllowed
        && responseNeedsBarrierRetry(request.signature, response, retiredSnapshot)) {
      retryAmbiguousTransportRequest(request);
      return false;
    }
    if (!response.isEmpty()
        && distanceToNearestTarget(
                response.destinations.get(response.destinations.size() - 1), request.targets)
            > MAX_RELEVANT_EDGE_DISTANCE) {
      releaseTransportResponseClaim(request);
      return false;
    }
    synchronized (requestLock) {
      if (!isActiveTransportRequest(request)
          || acceptedRequestSequence == request.sequence
          || reservedRequestSequence != request.sequence) {
        return false;
      }
      acceptedRequestSequence = request.sequence;
      reservedRequestSequence = -1L;
      retiredRequests.clear();
      return true;
    }
  }

  private boolean retryAmbiguousTransportRequest(TransportRequestToken request) {
    synchronized (requestLock) {
      if (!isActiveTransportRequest(request)
          || !request.ambiguityRetryAllowed
          || acceptedRequestSequence == request.sequence
          || reservedRequestSequence != request.sequence) {
        return false;
      }
      activeRequest = null;
      reservedRequestSequence = -1L;
      try {
        eventBus.post(new PluginMessage(NAMESPACE, CLEAR_MESSAGE));
      } catch (RuntimeException ex) {
        return false;
      }
      TransportRequestToken replacement =
          new TransportRequestToken(
              ++nextRequestSequence,
              request.signature,
              request.targets,
              request.expectsTransportResponse,
              false);
      activeRequest = replacement;
      try {
        eventBus.post(
            new PluginMessage(NAMESPACE, PATH_MESSAGE, request.signature.toMessageData()));
        return true;
      } catch (RuntimeException ex) {
        if (activeRequest == replacement) {
          activeRequest = null;
          reservedRequestSequence = -1L;
        }
        return false;
      }
    }
  }

  private static boolean responseNeedsBarrierRetry(
      RequestSignature current, TransportResponse response, List<RequestSignature> retired) {
    if (current == null || retired == null || retired.isEmpty()) {
      return false;
    }
    if (response.isEmpty()) {
      return true;
    }
    long bestRetiredAffinity = Long.MAX_VALUE;
    for (RequestSignature previous : retired) {
      if (current.hasSameEndpoints(previous)) {
        return true;
      }
      bestRetiredAffinity = Math.min(bestRetiredAffinity, endpointAffinity(previous, response));
    }
    if (bestRetiredAffinity == Long.MAX_VALUE) {
      return false;
    }
    long currentAffinity = endpointAffinity(current, response);
    return currentAffinity == Long.MAX_VALUE
        || currentAffinity + ENDPOINT_AFFINITY_MARGIN >= bestRetiredAffinity;
  }

  private static long endpointAffinity(RequestSignature signature, TransportResponse response) {
    if (signature == null || response == null || response.isEmpty()) {
      return Long.MAX_VALUE;
    }
    long targetDistance =
        distanceToNearestTarget(
            response.destinations.get(response.destinations.size() - 1), signature.targets);
    if (targetDistance == Long.MAX_VALUE) {
      return Long.MAX_VALUE;
    }
    long affinity = targetDistance;
    if (signature.start != null) {
      WorldPoint firstOrigin = response.origins.get(0);
      if (signature.start.getPlane() == firstOrigin.getPlane()) {
        affinity += chebyshevDistance(signature.start, firstOrigin);
      }
    }
    return affinity;
  }

  private static long distanceToNearestTarget(WorldPoint point, Set<WorldPoint> targets) {
    if (point == null || targets == null || targets.isEmpty()) {
      return Long.MAX_VALUE;
    }
    long closest = Long.MAX_VALUE;
    for (WorldPoint target : targets) {
      if (target != null && point.getPlane() == target.getPlane()) {
        closest = Math.min(closest, chebyshevDistance(point, target));
      }
    }
    return closest;
  }

  private static long chebyshevDistance(WorldPoint first, WorldPoint second) {
    return Math.max(
        Math.abs((long) first.getX() - second.getX()),
        Math.abs((long) first.getY() - second.getY()));
  }

  private static TransportResponse parseTransportResponse(Map<String, Object> data) {
    if (data == null
        || !data.containsKey("origin")
        || !data.containsKey("destination")
        || !data.containsKey("objectInfo")
        || !data.containsKey("displayInfo")) {
      return null;
    }
    List<?> originValues = responseValues(data.get("origin"));
    List<?> destinationValues = responseValues(data.get("destination"));
    List<?> objectInfos = responseValues(data.get("objectInfo"));
    List<?> displayInfos = responseValues(data.get("displayInfo"));
    if (originValues == null
        || destinationValues == null
        || objectInfos == null
        || displayInfos == null
        || originValues.size() != destinationValues.size()
        || originValues.size() != objectInfos.size()
        || originValues.size() != displayInfos.size()) {
      return null;
    }
    List<WorldPoint> origins = new ArrayList<>(originValues.size());
    List<WorldPoint> destinations = new ArrayList<>(destinationValues.size());
    for (int index = 0; index < originValues.size(); index++) {
      Object origin = originValues.get(index);
      Object destination = destinationValues.get(index);
      Object objectInfo = objectInfos.get(index);
      Object displayInfo = displayInfos.get(index);
      if (!(origin instanceof WorldPoint)
          || !(destination instanceof WorldPoint)
          || (objectInfo != null && !(objectInfo instanceof String))
          || (displayInfo != null && !(displayInfo instanceof String))) {
        return null;
      }
      origins.add((WorldPoint) origin);
      destinations.add((WorldPoint) destination);
    }
    return new TransportResponse(origins, destinations);
  }

  private static List<?> responseValues(Object value) {
    if (value instanceof List<?>) {
      return (List<?>) value;
    }
    if (value instanceof Object[]) {
      return new ArrayList<>(java.util.Arrays.asList((Object[]) value));
    }
    return null;
  }

  static boolean showsNativeTransportInfoForRegression(
      boolean useBankItems, boolean includeBankPath) {
    return true;
  }

  static boolean shouldPostTransportUpdates(
      boolean useBankItems, boolean explicitlyTrackInventoryRoute, boolean localOnly) {
    return !localOnly && explicitlyTrackInventoryRoute;
  }

  public void clear() {
    synchronized (requestLock) {
      activeRequest = null;
      acceptedRequestSequence = -1L;
      reservedRequestSequence = -1L;
      retiredRequests.clear();
      ++nextRequestSequence;
    }
    if (eventBus != null) {
      eventBus.post(new PluginMessage(NAMESPACE, CLEAR_MESSAGE));
    }
  }
}
