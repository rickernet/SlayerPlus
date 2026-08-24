package com.slayerplus;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ShortestPathTaskTransportLifecycleTest
{
	@Test
	public void inventoryOnlyTaskRouteStillPublishesSelectedTransport()
	{
		final Map<?, ?> config = taskRouteConfig(false, false);
		assertEquals("Inventory", config.get("useTeleportationItems"));
		assertEquals(Boolean.FALSE, config.get("includeBankPath"));
		assertEquals(Boolean.TRUE, config.get("avoidWilderness"));
		assertEquals(Boolean.TRUE, config.get("postTransports"));
		assertEquals(null, config.get("bankPath"));
		assertEquals(null, config.get("avoidWildy"));
	}

	@Test
	public void inventoryOnlyTaskAccessRouteStillPublishesSelectedTransport()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);

		assertTrue(bridge.routeToTaskAccess(
			new WorldPoint(3200, 3200, 0),
			Collections.singleton(new WorldPoint(3300, 3300, 0)),
			true,
			false,
			false,
			true
		));

		final PluginMessage message = posted.get();
		assertNotNull(message);
		final Map<?, ?> config = (Map<?, ?>) message.getData().get("config");
		assertEquals("Inventory", config.get("useTeleportationItems"));
		assertEquals(Boolean.FALSE, config.get("includeBankPath"));
		assertEquals(Boolean.TRUE, config.get("avoidWilderness"));
		assertEquals(Boolean.TRUE, config.get("postTransports"));
	}

	@Test
	public void localRouteStillDrawsShortestPath()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		assertTrue(bridge.routeToLocalTaskArea(new WorldPoint(3200, 3200, 0),
			Collections.singleton(new WorldPoint(3210, 3210, 0)), true, true));
		final Map<?, ?> config = (Map<?, ?>) posted.get().getData().get("config");
		assertEquals(Boolean.TRUE, config.get("drawMap"));
	}

	@Test
	public void spellbookRouteRequestsTeleportSelection()
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);
		assertTrue(bridge.routeToSpellbookChange(new WorldPoint(3200, 3200, 0),
			Collections.singleton(new WorldPoint(3300, 3300, 0)), true, false));
		final Map<?, ?> config = (Map<?, ?>) posted.get().getData().get("config");
		assertEquals(Boolean.TRUE, config.get("drawMap"));
		assertEquals(Boolean.TRUE, config.get("postTransports"));
	}

	private static Map<?, ?> taskRouteConfig(
		final boolean useBank,
		final boolean bankPath)
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);

		assertTrue(bridge.routeToTaskArea(
			new WorldPoint(3200, 3200, 0),
			Collections.singleton(new WorldPoint(3300, 3300, 0)),
			true,
			useBank,
			bankPath,
			true,
			false
		));
		final PluginMessage message = posted.get();
		assertNotNull(message);
		return (Map<?, ?>) message.getData().get("config");
	}
}
