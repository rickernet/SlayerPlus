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
		assertEquals(Boolean.TRUE, config.get("postTransports"));
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
		assertEquals(Boolean.TRUE, config.get("postTransports"));
	}

	private static Map<?, ?> taskRouteConfig(
		final boolean useBankItems,
		final boolean includeBankPath)
	{
		final EventBus eventBus = new EventBus();
		final AtomicReference<PluginMessage> posted = new AtomicReference<>();
		eventBus.register(PluginMessage.class, posted::set, 0.0f);
		final ShortestPathBridge bridge = new ShortestPathBridge(eventBus);

		assertTrue(bridge.routeToTaskArea(
			new WorldPoint(3200, 3200, 0),
			Collections.singleton(new WorldPoint(3300, 3300, 0)),
			true,
			useBankItems,
			includeBankPath,
			true
		));
		final PluginMessage message = posted.get();
		assertNotNull(message);
		return (Map<?, ?>) message.getData().get("config");
	}
}
