package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ShortestPathResponseCorrelationTest
{
	@Test
	public void emptyOrMalformedResponseCannotCompleteRequest()
	{
		final ShortestPathBridge bridge = routeTo(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3300, 3300, 0)
		);
		final ShortestPathBridge.TransportRequestToken request =
			bridge.claimPendingTransportResponse();
		assertNotNull(request);

		assertFalse(bridge.tryAcceptTransportResponse(
			request, Collections.emptyMap()
		));
		assertNotNull(bridge.currentPendingTransportRequest());

		final Map<String, Object> malformed = new HashMap<>();
		malformed.put("origin", Collections.singletonList(
			new WorldPoint(3200, 3200, 0)
		));
		malformed.put("destination", Collections.emptyList());
		malformed.put("displayInfo", Collections.singletonList(
			"Games necklace: Burthorpe"
		));
		final ShortestPathBridge.TransportRequestToken malformedClaim =
			bridge.claimPendingTransportResponse();
		assertNotNull(malformedClaim);
		assertFalse(bridge.tryAcceptTransportResponse(
			malformedClaim, malformed
		));

		/* A rejected payload does not consume the live request. */
		final ShortestPathBridge.TransportRequestToken validClaim =
			bridge.claimPendingTransportResponse();
		assertNotNull(validClaim);
		assertTrue(bridge.tryAcceptTransportResponse(
			validClaim,
			transportPayload(
				new WorldPoint(3200, 3200, 0),
				new WorldPoint(3290, 3290, 0)
			)
		));
		assertNull(bridge.currentPendingTransportRequest());
	}

	@Test
	public void oneRelevantResponseIsAcceptedPerRequest()
	{
		final ShortestPathBridge bridge = routeTo(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3300, 3300, 0)
		);
		final ShortestPathBridge.TransportRequestToken request =
			bridge.claimPendingTransportResponse();
		final Map<String, Object> response = transportPayload(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3290, 3290, 0)
		);

		assertNull(bridge.currentPendingTransportRequest());
		assertTrue(bridge.tryAcceptTransportResponse(request, response));
		assertFalse(bridge.tryAcceptTransportResponse(request, response));
		assertNull(bridge.currentPendingTransportRequest());
		assertNull(bridge.claimPendingTransportResponse());
	}

	@Test
	public void freshWalkingOnlyResponseConsumesRequest()
	{
		final ShortestPathBridge bridge = routeTo(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3300, 3300, 0)
		);
		final ShortestPathBridge.TransportRequestToken request =
			bridge.claimPendingTransportResponse();

		assertNotNull(request);
		assertTrue(bridge.tryAcceptTransportResponse(
			request,
			emptyTransportPayload()
		));
		assertNull(bridge.currentPendingTransportRequest());
		assertNull(bridge.claimPendingTransportResponse());
	}

	@Test
	public void acceptedPriorRouteDoesNotQuarantineFreshWalkingResponse()
	{
		final EventBus eventBus = new EventBus();
		final List<String> messageNames = new ArrayList<>();
		eventBus.register(
			PluginMessage.class,
			message -> messageNames.add(message.getName()),
			0.0f
		);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);

		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(new WorldPoint(3300, 3300, 0)),
			true,
			true,
			true,
			true
		));
		final ShortestPathBridge.TransportRequestToken first =
			bridge.claimPendingTransportResponse();
		assertNotNull(first);
		assertTrue(bridge.tryAcceptTransportResponse(
			first,
			transportPayload(start, new WorldPoint(3290, 3290, 0))
		));

		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(new WorldPoint(1200, 1200, 0)),
			true,
			true,
			true,
			true
		));
		final ShortestPathBridge.TransportRequestToken second =
			bridge.claimPendingTransportResponse();
		assertNotNull(second);
		assertTrue(bridge.tryAcceptTransportResponse(
			second,
			emptyTransportPayload()
		));
		assertEquals(
			java.util.Arrays.asList("path", "clear", "path"),
			messageNames
		);
		assertNull(bridge.currentPendingTransportRequest());
	}

	@Test
	public void duplicateCallbackCannotClaimWhileFirstResponseIsQueued()
	{
		final ShortestPathBridge bridge = routeTo(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3300, 3300, 0)
		);
		final ShortestPathBridge.TransportRequestToken queued =
			bridge.claimPendingTransportResponse();

		assertNotNull(queued);
		assertNull(bridge.currentPendingTransportRequest());
		assertNull(bridge.claimPendingTransportResponse());

		bridge.releaseTransportResponseClaim(queued);
		assertNotNull(bridge.currentPendingTransportRequest());
		assertNotNull(bridge.claimPendingTransportResponse());
	}

	@Test
	public void supersededTargetSetRejectsLateResponse()
	{
		final EventBus eventBus = new EventBus();
		final List<String> messageNames = new ArrayList<>();
		eventBus.register(
			PluginMessage.class,
			message -> messageNames.add(message.getName()),
			0.0f
		);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);
		final WorldPoint firstTarget = new WorldPoint(3300, 3300, 0);
		final WorldPoint secondTarget = new WorldPoint(1200, 1200, 0);

		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(firstTarget),
			true,
			true,
			true,
			true
		));
		final ShortestPathBridge.TransportRequestToken first =
			bridge.claimPendingTransportResponse();
		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(secondTarget),
			true,
			true,
			true,
			true
		));
		final ShortestPathBridge.TransportRequestToken second =
			bridge.claimPendingTransportResponse();

		assertEquals(
			java.util.Arrays.asList("path", "clear", "path"),
			messageNames
		);
		assertFalse(bridge.tryAcceptTransportResponse(
			first,
			transportPayload(start, new WorldPoint(3290, 3290, 0))
		));
		assertFalse(bridge.tryAcceptTransportResponse(
			second,
			transportPayload(start, new WorldPoint(3290, 3290, 0))
		));
		assertEquals(
			java.util.Arrays.asList(
				"path", "clear", "path", "clear", "path"
			),
			messageNames
		);
		final ShortestPathBridge.TransportRequestToken secondRetry =
			bridge.claimPendingTransportResponse();
		assertNotNull(secondRetry);
		assertTrue(bridge.tryAcceptTransportResponse(
			secondRetry,
			transportPayload(start, new WorldPoint(1210, 1210, 0))
		));
		assertEquals(Collections.singleton(secondTarget),
			secondRetry.getTargetsForRegression());
	}

	@Test
	public void clearlyCurrentSupersededResponseIsAcceptedWithoutRetry()
	{
		final EventBus eventBus = new EventBus();
		final List<String> messageNames = new ArrayList<>();
		eventBus.register(
			PluginMessage.class,
			message -> messageNames.add(message.getName()),
			0.0f
		);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);
		final WorldPoint firstTarget = new WorldPoint(3300, 3300, 0);
		final WorldPoint secondTarget = new WorldPoint(1200, 1200, 0);

		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(firstTarget),
			true,
			true,
			true,
			true
		));
		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(secondTarget),
			true,
			true,
			true,
			true
		));
		final ShortestPathBridge.TransportRequestToken current =
			bridge.claimPendingTransportResponse();

		assertNotNull(current);
		assertTrue(bridge.tryAcceptTransportResponse(
			current,
			transportPayload(start, new WorldPoint(1210, 1210, 0))
		));
		assertEquals(
			java.util.Arrays.asList("path", "clear", "path"),
			messageNames
		);
		assertNull(bridge.currentPendingTransportRequest());
	}

	@Test
	public void ambiguousWalkingResponseIsRepostedExactlyOnce()
	{
		final EventBus eventBus = new EventBus();
		final List<String> messageNames = new ArrayList<>();
		eventBus.register(
			PluginMessage.class,
			message -> messageNames.add(message.getName()),
			0.0f
		);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);

		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(new WorldPoint(3300, 3300, 0)),
			true,
			true,
			true,
			true
		));
		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(new WorldPoint(1200, 1200, 0)),
			true,
			true,
			true,
			true
		));
		final ShortestPathBridge.TransportRequestToken ambiguous =
			bridge.claimPendingTransportResponse();

		assertNotNull(ambiguous);
		assertFalse(bridge.tryAcceptTransportResponse(
			ambiguous,
			emptyTransportPayload()
		));
		assertEquals(
			java.util.Arrays.asList(
				"path", "clear", "path", "clear", "path"
			),
			messageNames
		);

		final ShortestPathBridge.TransportRequestToken retry =
			bridge.claimPendingTransportResponse();
		assertNotNull(retry);
		assertTrue(bridge.tryAcceptTransportResponse(
			retry,
			emptyTransportPayload()
		));
		assertEquals(5, messageNames.size());
		assertNull(bridge.currentPendingTransportRequest());
	}

	@Test
	public void configOnlySupersessionIsRepostedExactlyOnce()
	{
		final EventBus eventBus = new EventBus();
		final List<String> messageNames = new ArrayList<>();
		eventBus.register(
			PluginMessage.class,
			message -> messageNames.add(message.getName()),
			0.0f
		);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);
		final WorldPoint target = new WorldPoint(3300, 3300, 0);
		final Map<String, Object> response = transportPayload(
			start,
			new WorldPoint(3290, 3290, 0)
		);

		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(target),
			true,
			true,
			true,
			true,
			false
		));
		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(target),
			true,
			true,
			true,
			true,
			true
		));
		final ShortestPathBridge.TransportRequestToken ambiguous =
			bridge.claimPendingTransportResponse();

		assertNotNull(ambiguous);
		assertFalse(bridge.tryAcceptTransportResponse(ambiguous, response));
		assertEquals(
			java.util.Arrays.asList(
				"path", "clear", "path", "clear", "path"
			),
			messageNames
		);

		final ShortestPathBridge.TransportRequestToken retry =
			bridge.claimPendingTransportResponse();
		assertNotNull(retry);
		assertTrue(bridge.tryAcceptTransportResponse(retry, response));
		assertEquals(5, messageNames.size());
		assertNull(bridge.currentPendingTransportRequest());
	}

	@Test
	public void clearBarrierPublishesBeforeReplacementCanBeClaimed()
	{
		final EventBus eventBus = new EventBus();
		final ShortestPathBridge[] bridgeRef = new ShortestPathBridge[1];
		final AtomicInteger clearMessages = new AtomicInteger();
		eventBus.register(
			PluginMessage.class,
			message -> {
				if ("clear".equals(message.getName()))
				{
					clearMessages.incrementAndGet();
					assertNull(bridgeRef[0].claimPendingTransportResponse());
				}
			},
			0.0f
		);
		bridgeRef[0] = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);

		assertTrue(bridgeRef[0].routeToTaskArea(
			start,
			Collections.singleton(new WorldPoint(3300, 3300, 0)),
			true,
			true,
			true,
			true
		));
		assertTrue(bridgeRef[0].routeToTaskArea(
			start,
			Collections.singleton(new WorldPoint(1200, 1200, 0)),
			true,
			true,
			true,
			true
		));

		assertEquals(1, clearMessages.get());
		assertNotNull(bridgeRef[0].claimPendingTransportResponse());
	}

	@Test
	public void identicalRequestIsCoalesced()
	{
		final EventBus eventBus = new EventBus();
		final AtomicInteger pathMessages = new AtomicInteger();
		eventBus.register(
			PluginMessage.class,
			message -> {
				if ("path".equals(message.getName()))
				{
					pathMessages.incrementAndGet();
				}
			},
			0.0f
		);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		final WorldPoint start = new WorldPoint(3200, 3200, 0);
		final WorldPoint target = new WorldPoint(3300, 3300, 0);

		for (int attempt = 0; attempt < 2; attempt++)
		{
			assertTrue(bridge.routeToTaskArea(
				start,
				Collections.singleton(target),
				true,
				true,
				true,
				true
			));
		}
		assertEquals(1, pathMessages.get());
	}

	private static ShortestPathBridge routeTo(
		final WorldPoint start,
		final WorldPoint target)
	{
		final ShortestPathBridge bridge = new ShortestPathBridge(new EventBus());
		assertTrue(bridge.routeToTaskArea(
			start,
			Collections.singleton(target),
			true,
			true,
			true,
			true
		));
		return bridge;
	}

	private static Map<String, Object> transportPayload(
		final WorldPoint origin,
		final WorldPoint destination)
	{
		final Map<String, Object> data = new HashMap<>();
		data.put("origin", Collections.singletonList(origin));
		data.put("destination", Collections.singletonList(destination));
		data.put("displayInfo", Collections.singletonList(
			"Games necklace: Burthorpe"
		));
		data.put("objectInfo", Collections.singletonList("Rub"));
		return data;
	}

	private static Map<String, Object> emptyTransportPayload()
	{
		final Map<String, Object> data = new HashMap<>();
		data.put("origin", Collections.emptyList());
		data.put("destination", Collections.emptyList());
		data.put("displayInfo", Collections.emptyList());
		data.put("objectInfo", Collections.emptyList());
		return data;
	}
}
