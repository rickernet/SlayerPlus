package com.slayerplus;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import org.junit.Test;
import static org.junit.Assert.*;

public class PluginServiceWiringTest
{
    @Test
    public void readsExistingSlayerServiceWithoutReinjectingPlugin()
    {
        ExistingSlayerPlugin slayer = new ExistingSlayerPlugin();
        slayer.injectorForTest(Guice.createInjector(binder ->
            binder.bind(SlayerPluginService.class).toInstance(slayer.service)));
        assertSame(slayer.service, SlayerPlusPlugin.findExistingSlayerService(
            Arrays.asList(new Plugin() {}, slayer)));
        assertEquals(0, slayer.injections);
    }

    @Test
    public void absentSlayerServiceAllowsExistingTaskFallbacks()
    {
        assertNull(SlayerPlusPlugin.findExistingSlayerService(Collections.emptyList()));
        assertNull(SlayerPlusPlugin.findExistingSlayerService(
            Collections.singletonList(new ExistingSlayerPlugin())));
    }

    @Test
    public void singleDependencyChildrenShareTheSelectedBankTab()
    {
        Injector bankScope = Guice.createInjector(binder ->
            binder.bind(TabState.class).in(Singleton.class));
        TabState bank = bankScope.getInstance(TabState.class);
        TabState layouts = bankScope.createChildInjector().getInstance(TabState.class);
        TabState slayerPlus = bankScope.createChildInjector().getInstance(TabState.class);
        slayerPlus.selected = "slayerplus current";
        bank.selected = "custom";
        assertSame(bank, slayerPlus);
        assertSame(bank, layouts);
        assertEquals("custom", layouts.selected);
    }

    public static class TabState { String selected; }

    private static class ExistingSlayerPlugin extends SlayerPlugin
    {
        int injections;
        @Inject void injected() { injections++; }
        void injectorForTest(Injector value) { injector = value; }
        final SlayerPluginService service = new SlayerPluginService()
        {
            public List<NPC> getTargets() { return Collections.emptyList(); }
            public String getTask() { return "Cave crawlers"; }
            public String getTaskLocation() { return "Fremennik Slayer Dungeon"; }
            public int getInitialAmount() { return 100; }
            public int getRemainingAmount() { return 50; }
        };
    }
}
