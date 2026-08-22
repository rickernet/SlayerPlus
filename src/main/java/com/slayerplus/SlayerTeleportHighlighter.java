package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Highlights the exact live teleport path selected by SlayerPlus/Shortest Path.
 *
 * <p>The highlighter is deliberately UI-only. It never chooses a route or a
 * teleport item. It consumes the immutable {@link SlayerTravelSelection} owned
 * by {@link SlayerTravelCoordinator}. This prevents menu highlighting, Bank Tag
 * slot 1, and Shortest Path from developing separate route opinions.</p>
 *
 * <p>Easy Teleports rewrites the same RuneLite MenuEntry/Widget objects after
 * their creation. We therefore capture object identity before the rewrite and
 * paint that same object afterwards. Config-label matching remains a fallback,
 * not the primary identity mechanism.</p>
 */
public final class SlayerTeleportHighlighter
{
	private static final Logger log = LoggerFactory.getLogger(
		SlayerTeleportHighlighter.class
	);
	private static final int TELEPORT_HIGHLIGHT_RGB = 0x00ff00;
	private static final java.awt.Color TELEPORT_HIGHLIGHT_COLOR =
		new java.awt.Color(TELEPORT_HIGHLIGHT_RGB);
	private static final String EASY_TELEPORTS_CONFIG_GROUP =
		"easypharaohsceptre";

	private static final int FIELD_TEXT = 1;
	private static final int FIELD_NAME = 2;

	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final Supplier<SlayerTravelSelection> selectionSupplier;

	private final Set<MenuEntry> selectedItemEntries =
		Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<MenuEntry> selectedDestinationEntries =
		Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Widget, Integer> capturedWidgetFields =
		new IdentityHashMap<>();
	private final Map<Widget, WidgetState> highlightedWidgetStates =
		new IdentityHashMap<>();
	private final Set<Integer> activeWidgetGroups = new LinkedHashSet<>();
	private final Set<Integer> pendingWidgetGroups = new LinkedHashSet<>();

	private boolean menuRefreshQueued;
	private boolean widgetRefreshQueued;
	/* Deferred client-thread refreshes cannot be cancelled. Mark this instance
	 * closed before teardown so callbacks queued by an old plugin instance cannot
	 * repaint a freshly reloaded interface or retain orphan widget references. */
	private volatile boolean closed;
	/*
	 * RuneLite can expose a hovered submenu as a standalone MenuEntry array after
	 * the parent inventory/equipment entry has already been sorted. Remember only
	 * that the currently-open menu originated from the authoritative travel item;
	 * onGameTick clears the handoff as soon as the menu closes. This lets generic
	 * leaves such as Max cape -> Home be painted without treating every unrelated
	 * "Home" entry during the Slayer session as a teleport recommendation.
	 */
	private boolean selectedItemMenuContext;
	private String lastSelectionIdentity = "";
	/*
	 * The graphical destination chooser creates its option labels at runtime.
	 * Retry a bounded whole-widget-tree discovery only immediately after an
	 * interface loads; once found, normal direct widget tracking takes over.
	 */
	private int ghommalWidgetDiscoveryTicksRemaining;
	private int lastLoggedGhommalDestinationWidgetId = -1;

	/*
	 * Portion 3 performance guard: destination aliases (especially Easy Teleports
	 * config aliases) must never be rebuilt for every MenuEntryAdded event or for
	 * every widget visited in a tree walk. They are immutable for one authoritative
	 * travel selection and are invalidated only when that selection or companion
	 * config changes.
	 */
	private Set<String> cachedNormalizedDestinationAliases = Collections.emptySet();
	private String cachedAliasSelectionIdentity = "";
	private String cachedSelectedItemFamily = "";
	private boolean destinationAliasCacheDirty = true;
	/*
	 * A reviewed route can have one manual UI handoff after its selected item
	 * teleport. Fossil Island wyverns use the Digsite pendant first, then the
	 * Magic Mushtree's Mushroom Meadow button. Keep that second destination in
	 * this UI-only consumer; it never changes route selection or sends an action.
	 */
	private String routeGuidanceItemName = "";
	private Set<String> routeGuidanceDestinations = Collections.emptySet();

	public SlayerTeleportHighlighter(
		final Client client,
		final ClientThread clientThread,
		final ConfigManager configManager,
		final Supplier<SlayerTravelSelection> selectionSupplier)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.selectionSupplier = selectionSupplier;
	}

	public void onMenuEntryAdded(final MenuEntryAdded event)
	{
		if (!isActive() || event == null || event.getMenuEntry() == null)
		{
			return;
		}

		synchronizeSelectionIdentity();
		final MenuEntry entry = event.getMenuEntry();
		boolean relevant = false;
		if (isSelectedTravelItemEntry(entry))
		{
			selectedItemEntries.add(entry);
			selectedItemMenuContext = true;
			relevant = true;
		}
		if (isDestinationEntryByVisibleText(entry))
		{
			/* Capture before Easy Teleports' deferred rename. */
			selectedDestinationEntries.add(entry);
			relevant = true;
		}

		/*
		 * MenuEntryAdded fires for essentially every live game-menu entry. Portion 3
		 * queued a deferred highlighter pass for all of them while a Slayer session
		 * had a physical travel item selected. Only entries belonging to the selected
		 * teleport can affect the highlight, so unrelated game entries are a hard
		 * no-op.
		 */
		if (relevant)
		{
			queueMenuRefresh();
		}
	}

	/**
	 * Final synchronous menu paint after other default-priority menu rewrite
	 * subscribers (notably Easy Teleports) have finished PostMenuSort. Unlike the
	 * MenuEntryAdded capture path, this runs once per sorted menu, uses cached
	 * aliases, and performs no config enumeration.
	 */
	public void onPostMenuSort()
	{
		if (!isActive())
		{
			return;
		}
		synchronizeSelectionIdentity();
		if (hasRelevantLiveMenuPath())
		{
			highlightLiveMenuPath();
		}
	}

	/**
	 * RuneLite documents MenuOpened as the stable place to set open menu entries.
	 * Paint the actual entries carried by this event synchronously so a nested
	 * destination such as Max cape -> Home remains green while the menu is open.
	 */
	public void onMenuOpened(final MenuOpened event)
	{
		if (!isActive() || event == null || event.getMenuEntries() == null)
		{
			return;
		}
		synchronizeSelectionIdentity();
		highlightMenuEntries(event.getMenuEntries());
	}

	public void onMenuStructureChanged()
	{
		if (!isActive())
		{
			return;
		}
		synchronizeSelectionIdentity();

		/*
		 * Compatibility/deferred path for entry rewrites that happen after the
		 * normal menu events. Keep it narrowly gated to the selected travel item.
		 */
		if (hasRelevantLiveMenuPath())
		{
			queueMenuRefresh();
		}
	}

	public void onWidgetLoaded(final WidgetLoaded event)
	{
		if (!isActive() || event == null)
		{
			return;
		}

		synchronizeSelectionIdentity();
		final int groupId = event.getGroupId();
		/*
		 * Bank Tags rebuilds BANKMAIN when a tag is opened. It can contain hundreds
		 * of item widgets and is never a teleport destination chooser, so walking it
		 * (and every other visible root below it) only adds client-thread frame stalls.
		 */
		if (!shouldInspectWidgetGroupForRegression(groupId))
		{
			discardCapturedWidgetGroup(groupId);
			return;
		}
		discardCapturedWidgetGroup(groupId);
		if (groupId == InterfaceID.GRAPHICAL_MULTI
			&& isGhommalItemName(selection().getItemName()))
		{
			/*
			 * The live chooser is not guaranteed to attach its runtime labels to the
			 * component IDs published for GRAPHICAL_MULTI. Start a small discovery
			 * window for every newly loaded interface while this exact travel item is
			 * selected, then stop scanning automatically.
			 */
			ghommalWidgetDiscoveryTicksRemaining = 8;
		}
		final int capturedBefore = capturedWidgetFields.size();

		/*
		 * Easy Teleports rewrites CHATMENU.OPTIONS on a deferred ClientThread pass.
		 * Capture only that option subtree, never arbitrary chatbox text. This fixes
		 * the old regression where Slayer assignment/dialog text was painted green.
		 */
		if (groupId == InterfaceID.CHATMENU)
		{
			captureWidgetTree(client.getWidget(InterfaceID.Chatmenu.OPTIONS), groupId);
		}
		else
		{
			captureLoadedGroup(groupId);
		}
		final boolean deferredGhommalDestinationInterface =
			shouldDeferGhommalDestinationInterface(
				groupId,
				selection().getItemName()
			);

		/*
		 * Do not turn every interface opened during a Slayer session into a tracked
		 * teleport interface. Normally only a group where the selected destination
		 * was captured is deferred. Ghommal's GRAPHICAL_MULTI is the narrow exception:
		 * its two dynamic option strings are populated after WidgetLoaded, so the
		 * immediate capture is expected to be empty.
		 */
		if (capturedWidgetFields.size() == capturedBefore
			&& !deferredGhommalDestinationInterface)
		{
			return;
		}
		pendingWidgetGroups.add(groupId);
		queueWidgetRefresh();
	}

	static boolean shouldInspectWidgetGroupForRegression(final int groupId)
	{
		return groupId != InterfaceID.BANKMAIN;
	}

	static boolean shouldRefreshOpenMenuOnTick(
		final boolean menuOpen,
		final boolean selectedItemContext,
		final boolean relevantLivePath)
	{
		return menuOpen && (selectedItemContext || relevantLivePath);
	}

	static boolean shouldDeferGhommalDestinationInterface(
		final int groupId,
		final String itemName)
	{
		return groupId == InterfaceID.GRAPHICAL_MULTI
			&& isGhommalItemName(itemName);
	}

	public void onEasyTeleportsConfigChanged()
	{
		destinationAliasCacheDirty = true;
		if (!isActive())
		{
			return;
		}
		queueMenuRefresh();
		queueWidgetRefresh();
	}

	public void refreshNow()
	{
		if (!isActive())
		{
			restoreWidgetHighlights();
			return;
		}
		synchronizeSelectionIdentity();
		highlightLiveMenuPath();
		refreshVisibleWidgetGroups();
	}

	public void setRouteGuidance(
		final String itemName,
		final String... destinations)
	{
		final String nextItem = clean(itemName);
		final Set<String> nextDestinations = new LinkedHashSet<>();
		if (destinations != null)
		{
			for (final String destination : destinations)
			{
				final String next = clean(destination);
				if (!next.isEmpty())
				{
					nextDestinations.add(next);
				}
			}
		}
		if (nextItem.equals(routeGuidanceItemName)
			&& nextDestinations.equals(routeGuidanceDestinations))
		{
			return;
		}
		restoreWidgetHighlights();
		selectedItemEntries.clear();
		selectedDestinationEntries.clear();
		selectedItemMenuContext = false;
		routeGuidanceItemName = nextItem;
		routeGuidanceDestinations = Collections.unmodifiableSet(nextDestinations);
		cachedAliasSelectionIdentity = "";
		cachedNormalizedDestinationAliases = Collections.emptySet();
		cachedSelectedItemFamily = itemFamily(effectiveSelectedItemName());
		destinationAliasCacheDirty = true;
	}

	/**
	 * GRAPHICAL_MULTI populates its visible labels through runtime children after
	 * WidgetLoaded. Prefer its reviewed option components, then use a bounded live
	 * widget-tree discovery window for revisions where those labels are attached
	 * elsewhere. This never scans the game scene.
	 */
	public void onGameTick()
	{
		if (!isActive())
		{
			selectedItemMenuContext = false;
			return;
		}
		final boolean menuOpen = client.isMenuOpen();
		if (!menuOpen)
		{
			selectedItemMenuContext = false;
		}
		else
		{
			final boolean relevantLivePath = selectedItemMenuContext
				|| hasRelevantLiveMenuPath();
			/*
			 * Companion menu plugins can rewrite an already-open teleport menu after
			 * MenuEntryAdded/PostMenuSort. Repaint the small live menu tree once per
			 * game tick while it is open so the authoritative route cannot flicker or
			 * disappear. No scene, bank, or inventory scan occurs here.
			 */
			if (shouldRefreshOpenMenuOnTick(
				menuOpen,
				selectedItemMenuContext,
				relevantLivePath
			))
			{
				highlightLiveMenuPath();
			}
		}
		if (!isGhommalItemName(selection().getItemName()))
		{
			return;
		}
		highlightGhommalGraphicalDestination();
		if (ghommalWidgetDiscoveryTicksRemaining > 0)
		{
			ghommalWidgetDiscoveryTicksRemaining--;
			highlightVisibleGhommalDestination();
		}
	}

	public void restoreWidgetHighlights()
	{
		for (final Map.Entry<Widget, WidgetState> entry
			: highlightedWidgetStates.entrySet())
		{
			final Widget widget = entry.getKey();
			final WidgetState state = entry.getValue();
			if (widget == null || state == null)
			{
				continue;
			}
			try
			{
				widget.setText(state.text);
				widget.setName(state.name);
				widget.setTextColor(state.textColor);
				widget.revalidate();
			}
			catch (RuntimeException ignored)
			{
				// The interface may have closed and destroyed the widget.
			}
		}
		highlightedWidgetStates.clear();
	}

	public void clearCaptures()
	{
		restoreWidgetHighlights();
		discardCaptures();
	}

	/**
	 * A teleport can destroy its chooser widgets while the new scene is loading.
	 * Drop those stale references without writing back into the closing interface.
	 */
	public void discardForWorldTransition()
	{
		highlightedWidgetStates.clear();
		discardCaptures();
	}

	private void discardCaptures()
	{
		selectedItemEntries.clear();
		selectedDestinationEntries.clear();
		capturedWidgetFields.clear();
		activeWidgetGroups.clear();
		pendingWidgetGroups.clear();
		menuRefreshQueued = false;
		widgetRefreshQueued = false;
		selectedItemMenuContext = false;
		ghommalWidgetDiscoveryTicksRemaining = 0;
		lastLoggedGhommalDestinationWidgetId = -1;
		lastSelectionIdentity = selectionIdentity();
		cachedSelectedItemFamily = itemFamily(selection().getItemName());
	}

	public void shutDown()
	{
		closed = true;
		clearCaptures();
		routeGuidanceItemName = "";
		routeGuidanceDestinations = Collections.emptySet();
		lastSelectionIdentity = "";
		cachedAliasSelectionIdentity = "";
		cachedNormalizedDestinationAliases = Collections.emptySet();
		destinationAliasCacheDirty = true;
	}

	private boolean hasRelevantLiveMenuPath()
	{
		final Menu menu = client == null ? null : client.getMenu();
		final MenuEntry[] roots = menu == null ? null : menu.getMenuEntries();
		if (roots == null || roots.length == 0)
		{
			return false;
		}

		final Set<String> aliases = normalizedDestinationAliases();
		for (final MenuEntry root : roots)
		{
			if (root == null)
			{
				continue;
			}
			if (isSelectedTravelItemEntry(root)
				|| matchesDestination(root.getOption(), aliases)
				|| matchesDestination(root.getTarget(), aliases))
			{
				return true;
			}
		}
		return false;
	}

	private void queueMenuRefresh()
	{
		if (menuRefreshQueued || !isActive())
		{
			return;
		}
		menuRefreshQueued = true;
		/*
		 * Easy Teleports itself uses ClientThread.invokeLater for inventory/equipment
		 * MenuEntry rewrites. Two passes ensure we paint the rewritten live entry.
		 */
		clientThread.invokeLater(() ->
		{
			if (closed)
			{
				menuRefreshQueued = false;
				return;
			}
			clientThread.invokeLater(() ->
			{
				menuRefreshQueued = false;
				if (!closed)
				{
					highlightLiveMenuPath();
				}
			});
		});
	}

	private void queueWidgetRefresh()
	{
		if (widgetRefreshQueued || !isActive())
		{
			return;
		}
		widgetRefreshQueued = true;
		/* Same ordering rule as the menu path: run after Easy Teleports' rewrite. */
		clientThread.invokeLater(() ->
		{
			if (closed)
			{
				widgetRefreshQueued = false;
				return;
			}
			clientThread.invokeLater(() ->
			{
				widgetRefreshQueued = false;
				if (!closed)
				{
					refreshVisibleWidgetGroups();
				}
			});
		});
	}

	private void highlightLiveMenuPath()
	{
		if (!isActive())
		{
			return;
		}
		synchronizeSelectionIdentity();
		final Menu menu = client.getMenu();
		highlightMenuEntries(menu == null ? null : menu.getMenuEntries());
	}

	private void highlightMenuEntries(final MenuEntry[] roots)
	{
		if (roots == null || roots.length == 0)
		{
			selectedItemEntries.clear();
			selectedDestinationEntries.clear();
			return;
		}

		final Set<String> aliases = normalizedDestinationAliases();
		final Menu rootMenu = client == null ? null : client.getMenu();
		MenuPath best = null;
		for (final MenuEntry root : roots)
		{
			if (root == null || !isSelectedTravelItemEntry(root))
			{
				continue;
			}
			selectedItemEntries.add(root);
			selectedItemMenuContext = true;
			final MenuPath candidate = findDestinationPath(root, aliases);
			if (candidate == null)
			{
				continue;
			}
			if (best == null || candidate.score > best.score)
			{
				best = candidate;
			}
		}

		if (best == null)
		{
			/*
			 * RuneLite may deliver MenuOpened for the currently-open submenu rather
			 * than for the original inventory/equipment root entry. In that shape the
			 * Max-cape parent is absent, but each leaf still carries Max cape context
			 * (normally in its target). Paint the exact destination leaf directly.
			 */
			if ((menuContainsSelectedItemContext(roots)
				|| selectedItemMenuContext
				|| isContextlessSelectedDestinationMenu(roots, aliases))
				&& highlightVisibleDestinationEntries(roots, aliases))
			{
				if (rootMenu != null)
				{
					rootMenu.setMenuEntries(roots);
				}
			}
			else
			{
				/*
				 * The submenu may not have been constructed yet. Highlight only the
				 * best selected-item action; the destination joins on the next menu pass.
				 */
				final MenuEntry fallback = selectBestFirstStageOnly(roots);
				if (fallback != null)
				{
					highlightEntryOption(fallback);
					if (rootMenu != null)
					{
						rootMenu.setMenuEntries(roots);
					}
				}
			}
			/* MenuEntry identities are only needed across this deferred rewrite pass. */
			selectedItemEntries.clear();
			selectedDestinationEntries.clear();
			return;
		}

		for (final MenuEntry entry : best.entries)
		{
			highlightRouteEntry(entry, aliases);
			final Menu subMenu = entry == null ? null : entry.getSubMenu();
			if (subMenu != null && subMenu.getMenuEntries() != null)
			{
				subMenu.setMenuEntries(subMenu.getMenuEntries());
			}
		}
		if (rootMenu != null)
		{
			rootMenu.setMenuEntries(roots);
		}
		/*
		 * Do not retain MenuEntry objects for the lifetime of the Slayer session.
		 * RuneLite rebuilds these objects constantly; keeping them in identity sets
		 * creates an unbounded session-long retention set and progressively increases
		 * GC pressure. The next menu build will capture the current objects again.
		 */
		selectedItemEntries.clear();
		selectedDestinationEntries.clear();
	}

	/**
	 * RuneLite can publish the opened Ghommal destination submenu as a standalone
	 * array whose leaves have no item ID, widget, target, or parent entry. In that
	 * exact shape, the active immutable Ghommal selection plus an exact Mor Ul Rek
	 * option is sufficient context. Do not apply this exception to generic travel
	 * aliases such as "Home" or to target text containing the item name.
	 */
	private boolean isContextlessSelectedDestinationMenu(
		final MenuEntry[] entries,
		final Set<String> aliases)
	{
		if (entries == null)
		{
			return false;
		}
		for (final MenuEntry entry : entries)
		{
			if (entry != null && isContextlessSelectedDestinationChoice(
				effectiveSelectedItemName(),
				entry.getOption(),
				aliases
			))
			{
				return true;
			}
		}
		return false;
	}

	private boolean menuContainsSelectedItemContext(final MenuEntry[] entries)
	{
		if (entries == null)
		{
			return false;
		}
		for (final MenuEntry entry : entries)
		{
			if (entry == null)
			{
				continue;
			}
			if (isSelectedTravelItemEntry(entry)
				|| selectedDestinationEntries.contains(entry))
			{
				return true;
			}
			final Menu subMenu = entry.getSubMenu();
			if (subMenu != null
				&& menuContainsSelectedItemContext(subMenu.getMenuEntries()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean highlightVisibleDestinationEntries(
		final MenuEntry[] entries,
		final Set<String> aliases)
	{
		if (entries == null)
		{
			return false;
		}
		boolean highlighted = false;
		for (final MenuEntry entry : entries)
		{
			if (entry == null)
			{
				continue;
			}
			if (matchesDestination(entry.getOption(), aliases)
				|| selectedDestinationEntries.contains(entry))
			{
				highlightEntryOption(entry);
				highlighted = true;
			}
			else if (matchesDestination(entry.getTarget(), aliases))
			{
				entry.setTarget(highlight(entry.getTarget()));
				highlighted = true;
			}

			final Menu subMenu = entry.getSubMenu();
			if (subMenu != null)
			{
				final MenuEntry[] children = subMenu.getMenuEntries();
				if (highlightVisibleDestinationEntries(children, aliases))
				{
					highlighted = true;
					subMenu.setMenuEntries(children);
				}
			}
		}
		return highlighted;
	}

	private MenuPath findDestinationPath(
		final MenuEntry root,
		final Set<String> aliases)
	{
		final List<MenuEntry> entries = new ArrayList<>();
		if (!appendPathToDestination(root, aliases, entries))
		{
			return null;
		}
		return new MenuPath(entries, firstStageScore(root, true));
	}

	private boolean appendPathToDestination(
		final MenuEntry entry,
		final Set<String> aliases,
		final List<MenuEntry> output)
	{
		if (entry == null)
		{
			return false;
		}

		if (isExactDestinationEntry(entry, aliases))
		{
			output.add(entry);
			return true;
		}

		final Menu subMenu = entry.getSubMenu();
		final MenuEntry[] children = subMenu == null ? null : subMenu.getMenuEntries();
		if (children == null || children.length == 0)
		{
			return false;
		}

		for (final MenuEntry child : children)
		{
			final List<MenuEntry> childPath = new ArrayList<>();
			if (appendPathToDestination(child, aliases, childPath))
			{
				output.add(entry);
				output.addAll(childPath);
				return true;
			}
		}
		return false;
	}

	private MenuEntry selectBestFirstStageOnly(final MenuEntry[] roots)
	{
		MenuEntry best = null;
		int bestScore = Integer.MIN_VALUE;
		for (final MenuEntry entry : roots)
		{
			if (entry == null || !isSelectedTravelItemEntry(entry))
			{
				continue;
			}
			final int score = firstStageScore(entry, false);
			if (best == null || score > bestScore)
			{
				best = entry;
				bestScore = score;
			}
		}
		/* Never paint an explicitly rejected action merely because it was the only
		 * selected-item entry still present in a partially-built menu. */
		return bestScore > 0 ? best : null;
	}

	private int firstStageScore(final MenuEntry entry, final boolean ownsDestination)
	{
		return (ownsDestination ? 100 : 0) + firstStageActionPreference(
			entry == null ? "" : entry.getOption(),
			effectiveSelectedItemName()
		);
	}

	/**
	 * Rank only the visible action text; the selected item ID has already been
	 * verified by the caller. Ghommal equipment is deliberately strict because
	 * Wield, Use, Dismantle, Drop, and Destinations all share that same item ID.
	 */
	static int firstStageActionPreference(
		final String option,
		final String itemName)
	{
		final String action = normalize(clean(option));
		final boolean ghommal = isGhommalItemName(itemName);

		if (ghommal)
		{
			if (action.equals("destinations") || action.equals("destination"))
			{
				return 3000;
			}
			if (action.equals("teleport") || action.equals("teleports")
				|| action.startsWith("teleport "))
			{
				return 2000;
			}
			return -3000;
		}

		/* Slayer-ring reference behavior: Teleport outranks Rub. */
		if (action.equals("teleport") || action.equals("teleports")
			|| action.startsWith("teleport "))
		{
			return 1000;
		}
		if (action.equals("drop") || action.equals("destroy")
			|| action.equals("dismantle") || action.equals("discard")
			|| action.equals("release") || action.equals("examine")
			|| action.equals("cancel") || action.isEmpty())
		{
			return -1000;
		}
		if (action.equals("rub") || action.equals("operate")
			|| action.equals("destinations") || action.equals("destination")
			|| action.equals("invoke") || action.equals("commune"))
		{
			return 500;
		}
		return 100;
	}

	static boolean isGhommalItemName(final String itemName)
	{
		final String family = normalize(clean(itemName));
		return family.contains("ghommal s hilt")
			|| family.contains("ghommal s avernic defender");
	}

	static boolean isContextlessGhommalDestinationChoice(
		final String itemName,
		final String option,
		final Set<String> normalizedAliases)
	{
		return isGhommalItemName(itemName)
			&& matchesDestination(option, normalizedAliases);
	}

	static boolean isContextlessSelectedDestinationChoice(
		final String itemName,
		final String option,
		final Set<String> normalizedAliases)
	{
		if (!matchesDestination(option, normalizedAliases))
		{
			return false;
		}
		if (isGhommalItemName(itemName))
		{
			return true;
		}

		/*
		 * The live Digsite-pendant flyout shown by RuneLite can be a standalone
		 * submenu whose leaves have no item id, widget, target, or parent. Fossil
		 * Island is an exact, item-specific destination—not a generic label such as
		 * Home—so it is safe to recognize while the immutable selected item is a
		 * Digsite pendant.
		 */
		final String family = normalize(clean(itemName));
		if (family.contains("achievement diary cape")
			|| family.contains("achievement cape"))
		{
			return true;
		}
		return family.contains("digsite pendant")
			&& normalizeChoice(option).equals("fossil island");
	}

	private void highlightRouteEntry(
		final MenuEntry entry,
		final Set<String> aliases)
	{
		if (entry == null)
		{
			return;
		}
		if (isExactDestinationEntry(entry, aliases))
		{
			if (matchesDestination(entry.getOption(), aliases)
				|| selectedDestinationEntries.contains(entry))
			{
				highlightEntryOption(entry);
			}
			else if (matchesDestination(entry.getTarget(), aliases))
			{
				entry.setTarget(highlight(entry.getTarget()));
			}
			else
			{
				/* Identity-captured custom label; option is the visible destination. */
				highlightEntryOption(entry);
			}
			return;
		}
		highlightEntryOption(entry);
	}

	private static void highlightEntryOption(final MenuEntry entry)
	{
		if (entry == null)
		{
			return;
		}
		final String option = clean(entry.getOption());
		if (!option.isEmpty())
		{
			entry.setOption(highlight(option));
		}
	}

	private boolean isExactDestinationEntry(
		final MenuEntry entry,
		final Set<String> aliases)
	{
		return entry != null
			&& (selectedDestinationEntries.contains(entry)
				|| matchesDestination(entry.getOption(), aliases)
				|| matchesDestination(entry.getTarget(), aliases));
	}

	private boolean isDestinationEntryByVisibleText(final MenuEntry entry)
	{
		final Set<String> aliases = normalizedDestinationAliases();
		return entry != null
			&& (matchesDestination(entry.getOption(), aliases)
				|| matchesDestination(entry.getTarget(), aliases));
	}

	private boolean isSelectedTravelItemEntry(final MenuEntry entry)
	{
		final SlayerTravelSelection selection = selection();
		final boolean hasPhysicalSelection = selection.hasPhysicalItem();
		final boolean hasRouteGuidance = !routeGuidanceItemName.isEmpty();
		if (entry == null
			|| (!hasPhysicalSelection && !hasRouteGuidance))
		{
			return false;
		}
		if (selectedItemEntries.contains(entry))
		{
			return true;
		}

		try
		{
			if (!hasRouteGuidance && hasPhysicalSelection
				&& entry.getItemId() == selection.getItemId())
			{
				return true;
			}
			final Widget widget = entry.getWidget();
			if (!hasRouteGuidance && hasPhysicalSelection && widget != null
				&& widget.getItemId() == selection.getItemId())
			{
				return true;
			}
		}
		catch (RuntimeException ignored)
		{
			// Menu/widget can disappear while the menu tree is being rebuilt.
		}

		final String family = cachedSelectedItemFamily;
		final String target = normalize(clean(entry.getTarget()));
		return !family.isEmpty() && !target.isEmpty() && target.contains(family);
	}

	private void captureLoadedGroup(final int groupId)
	{
		final Widget[] roots = client.getWidgetRoots();
		if (roots == null)
		{
			return;
		}
		for (final Widget root : roots)
		{
			if (root == null)
			{
				continue;
			}
			/*
			 * Do not require the root itself to share the loaded group id. Some
			 * teleport interfaces (notably InterfaceID.MENU used by Xeric's
			 * talisman) are nested beneath a parent from another group. The tree
			 * walker still captures only widgets whose own id belongs to groupId.
			 */
			if (groupId == InterfaceID.MENU
				|| widgetGroup(root) == groupId)
			{
				captureWidgetTree(root, groupId);
			}
		}
	}

	private void discardCapturedWidgetGroup(final int groupId)
	{
		for (final Iterator<Map.Entry<Widget, WidgetState>> iterator =
			highlightedWidgetStates.entrySet().iterator(); iterator.hasNext();)
		{
			final Map.Entry<Widget, WidgetState> entry = iterator.next();
			final Widget widget = entry.getKey();
			if (widget == null || widgetGroup(widget) != groupId)
			{
				continue;
			}
			final WidgetState state = entry.getValue();
			try
			{
				widget.setText(state.text);
				widget.setName(state.name);
				widget.setTextColor(state.textColor);
				widget.revalidate();
			}
			catch (RuntimeException ignored)
			{
				// A reloaded interface can invalidate its former widget objects.
			}
			iterator.remove();
		}

		for (final Iterator<Widget> iterator =
			capturedWidgetFields.keySet().iterator(); iterator.hasNext();)
		{
			final Widget widget = iterator.next();
			if (widget != null && widgetGroup(widget) == groupId)
			{
				iterator.remove();
			}
		}
		activeWidgetGroups.remove(groupId);
		pendingWidgetGroups.remove(groupId);
	}

	private void captureWidgetTree(final Widget root, final int groupId)
	{
		if (root == null)
		{
			return;
		}
		final Map<Widget, Boolean> visited = new IdentityHashMap<>();
		captureWidgetTree(root, groupId, visited);
	}

	private void captureWidgetTree(
		final Widget widget,
		final int groupId,
		final Map<Widget, Boolean> visited)
	{
		if (widget == null || visited.put(widget, Boolean.TRUE) != null)
		{
			return;
		}
		try
		{
			if (widgetGroup(widget) == groupId && !widget.isHidden())
			{
				int fields = 0;
				final Set<String> aliases = normalizedDestinationAliases();
				if (matchesDestination(widget.getText(), aliases))
				{
					fields |= FIELD_TEXT;
				}
				if (matchesDestination(widget.getName(), aliases))
				{
					fields |= FIELD_NAME;
				}
				if (fields != 0)
				{
					capturedWidgetFields.put(widget, fields);
				}
			}
		}
		catch (RuntimeException ignored)
		{
			return;
		}

		captureWidgetChildren(widget.getChildren(), groupId, visited);
		captureWidgetChildren(widget.getDynamicChildren(), groupId, visited);
		captureWidgetChildren(widget.getStaticChildren(), groupId, visited);
		captureWidgetChildren(widget.getNestedChildren(), groupId, visited);
	}

	private void captureWidgetChildren(
		final Widget[] children,
		final int groupId,
		final Map<Widget, Boolean> visited)
	{
		if (children == null)
		{
			return;
		}
		for (final Widget child : children)
		{
			captureWidgetTree(child, groupId, visited);
		}
	}

	private void refreshVisibleWidgetGroups()
	{
		restoreWidgetHighlights();
		if (!isActive())
		{
			return;
		}

		final Set<Integer> groups = new LinkedHashSet<>(activeWidgetGroups);
		groups.addAll(pendingWidgetGroups);
		pendingWidgetGroups.clear();

		for (final int groupId : groups)
		{
			boolean matched = false;
			if (groupId == InterfaceID.GRAPHICAL_MULTI
				&& isGhommalItemName(selection().getItemName()))
			{
				matched = highlightGhommalGraphicalDestination();
			}
			else if (groupId == InterfaceID.CHATMENU)
			{
				final Widget root = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
				matched = root != null && highlightWidgetTree(root, groupId);
			}
			else
			{
				final Widget[] roots = client.getWidgetRoots();
				if (roots != null)
				{
					for (final Widget root : roots)
					{
						if (root != null
							&& (groupId == InterfaceID.MENU
								|| widgetGroup(root) == groupId))
						{
							/* See captureLoadedGroup: the target group can be nested
							 * under a parent belonging to a different interface group. */
							matched |= highlightWidgetTree(root, groupId);
						}
					}
				}
			}

			if (matched)
			{
				activeWidgetGroups.add(groupId);
			}
			else
			{
				activeWidgetGroups.remove(groupId);
			}
		}
	}

	private boolean highlightGhommalGraphicalDestination()
	{
		final int[] optionComponentIds =
		{
			InterfaceID.GraphicalMulti.GRAPHICAL_MULTI_2A,
			InterfaceID.GraphicalMulti.GRAPHICAL_MULTI_2B
		};
		final Set<String> aliases = normalizedDestinationAliases();
		final Map<Widget, Boolean> visited = new IdentityHashMap<>();
		boolean matched = false;
		for (final int componentId : optionComponentIds)
		{
			final Widget widget = client.getWidget(componentId);
			matched |= highlightGhommalOptionSubtree(widget, aliases, visited);
		}
		return matched;
	}

	/**
	 * Finds the actual visible Mor Ul Rek label without assuming which component
	 * owns it. This path is intentionally enabled for only a few ticks following
	 * WidgetLoaded while an immutable Ghommal selection is active.
	 */
	private boolean highlightVisibleGhommalDestination()
	{
		final Widget[] roots = client.getWidgetRoots();
		if (roots == null)
		{
			return false;
		}
		final Set<String> aliases = normalizedDestinationAliases();
		final Map<Widget, Boolean> visited = new IdentityHashMap<>();
		boolean matched = false;
		for (final Widget root : roots)
		{
			matched |= highlightVisibleGhommalDestination(
				root,
				aliases,
				visited
			);
		}
		return matched;
	}

	private boolean highlightVisibleGhommalDestination(
		final Widget widget,
		final Set<String> aliases,
		final Map<Widget, Boolean> visited)
	{
		if (widget == null || visited.put(widget, Boolean.TRUE) != null)
		{
			return false;
		}

		boolean matched = false;
		try
		{
			if (!widget.isHidden())
			{
				int fields = 0;
				if (matchesDestination(widget.getText(), aliases))
				{
					fields |= FIELD_TEXT;
				}
				if (matchesDestination(widget.getName(), aliases))
				{
					fields |= FIELD_NAME;
				}
				if (fields != 0)
				{
					highlightWidget(widget, fields);
					matched = true;
					if (widget.getId() != lastLoggedGhommalDestinationWidgetId)
					{
						lastLoggedGhommalDestinationWidgetId = widget.getId();
						log.debug(
							"Highlighted live Ghommal destination widget {} (group {})",
							widget.getId(),
							widgetGroup(widget)
						);
					}
				}
			}
		}
		catch (RuntimeException ignored)
		{
			return matched;
		}

		matched |= highlightVisibleGhommalDestinationChildren(
			widget.getChildren(), aliases, visited
		);
		matched |= highlightVisibleGhommalDestinationChildren(
			widget.getDynamicChildren(), aliases, visited
		);
		matched |= highlightVisibleGhommalDestinationChildren(
			widget.getStaticChildren(), aliases, visited
		);
		matched |= highlightVisibleGhommalDestinationChildren(
			widget.getNestedChildren(), aliases, visited
		);
		return matched;
	}

	private boolean highlightVisibleGhommalDestinationChildren(
		final Widget[] children,
		final Set<String> aliases,
		final Map<Widget, Boolean> visited)
	{
		if (children == null)
		{
			return false;
		}
		boolean matched = false;
		for (final Widget child : children)
		{
			matched |= highlightVisibleGhommalDestination(
				child,
				aliases,
				visited
			);
		}
		return matched;
	}

	private boolean highlightGhommalOptionSubtree(
		final Widget widget,
		final Set<String> aliases,
		final Map<Widget, Boolean> visited)
	{
		if (widget == null || visited.put(widget, Boolean.TRUE) != null)
		{
			return false;
		}

		boolean matched = false;
		try
		{
			if (!widget.isHidden())
			{
				int fields = 0;
				if (matchesDestination(widget.getText(), aliases))
				{
					fields |= FIELD_TEXT;
				}
				if (matchesDestination(widget.getName(), aliases))
				{
					fields |= FIELD_NAME;
				}
				if (fields != 0)
				{
					highlightWidget(widget, fields);
					matched = true;
				}
			}
		}
		catch (RuntimeException ignored)
		{
			return matched;
		}

		matched |= highlightGhommalOptionChildren(
			widget.getChildren(), aliases, visited
		);
		matched |= highlightGhommalOptionChildren(
			widget.getDynamicChildren(), aliases, visited
		);
		matched |= highlightGhommalOptionChildren(
			widget.getStaticChildren(), aliases, visited
		);
		matched |= highlightGhommalOptionChildren(
			widget.getNestedChildren(), aliases, visited
		);
		return matched;
	}

	private boolean highlightGhommalOptionChildren(
		final Widget[] children,
		final Set<String> aliases,
		final Map<Widget, Boolean> visited)
	{
		if (children == null)
		{
			return false;
		}
		boolean matched = false;
		for (final Widget child : children)
		{
			matched |= highlightGhommalOptionSubtree(child, aliases, visited);
		}
		return matched;
	}

	private boolean highlightWidgetTree(final Widget root, final int groupId)
	{
		final Map<Widget, Boolean> visited = new IdentityHashMap<>();
		return highlightWidgetTree(root, groupId, visited);
	}

	private boolean highlightWidgetTree(
		final Widget widget,
		final int groupId,
		final Map<Widget, Boolean> visited)
	{
		if (widget == null || visited.put(widget, Boolean.TRUE) != null)
		{
			return false;
		}
		boolean matched = false;
		try
		{
			if (widgetGroup(widget) == groupId && !widget.isHidden())
			{
				final Set<String> aliases = normalizedDestinationAliases();
				int fields = capturedWidgetFields.getOrDefault(widget, 0);
				if (matchesDestination(widget.getText(), aliases))
				{
					fields |= FIELD_TEXT;
				}
				if (matchesDestination(widget.getName(), aliases))
				{
					fields |= FIELD_NAME;
				}
				if (fields != 0)
				{
					highlightWidget(widget, fields);
					matched = true;
				}
			}
		}
		catch (RuntimeException ignored)
		{
			return matched;
		}

		matched |= highlightWidgetChildren(widget.getChildren(), groupId, visited);
		matched |= highlightWidgetChildren(widget.getDynamicChildren(), groupId, visited);
		matched |= highlightWidgetChildren(widget.getStaticChildren(), groupId, visited);
		matched |= highlightWidgetChildren(widget.getNestedChildren(), groupId, visited);
		return matched;
	}

	private boolean highlightWidgetChildren(
		final Widget[] children,
		final int groupId,
		final Map<Widget, Boolean> visited)
	{
		if (children == null)
		{
			return false;
		}
		boolean matched = false;
		for (final Widget child : children)
		{
			matched |= highlightWidgetTree(child, groupId, visited);
		}
		return matched;
	}

	private void highlightWidget(final Widget widget, final int fields)
	{
		if (widget == null)
		{
			return;
		}
		try
		{
			highlightedWidgetStates.putIfAbsent(
				widget,
				new WidgetState(widget.getText(), widget.getName(), widget.getTextColor())
			);
			if ((fields & FIELD_TEXT) != 0)
			{
				/* Keep the live option text untouched; colour alone is sufficient. */
				widget.setTextColor(TELEPORT_HIGHLIGHT_RGB);
			}
			if ((fields & FIELD_NAME) != 0)
			{
				widget.setName(highlight(widget.getName()));
			}
		}
		catch (RuntimeException ignored)
		{
			// The interface may close between discovery and painting.
		}
	}

	private Set<String> normalizedDestinationAliases()
	{
		/*
		 * This method is called from hot menu/widget paths. Easy Teleports config key
		 * enumeration is comparatively expensive, so perform it once per authoritative
		 * travel selection (or explicit Easy Teleports config change), never once per
		 * MenuEntry or Widget.
		 */
		if (!destinationAliasCacheDirty
			&& lastSelectionIdentity.equals(cachedAliasSelectionIdentity))
		{
			return cachedNormalizedDestinationAliases;
		}

		final SlayerTravelSelection selection = selection();
		final Set<String> raw = new LinkedHashSet<>();
		if (routeGuidanceItemName.isEmpty())
		{
			raw.addAll(SlayerTeleportRouteRegistry.destinationAliases(
				selection.getItemName(),
				selection.getDestination()
			));
			final String menuDestination =
				SlayerTeleportRouteRegistry.menuDestination(
					selection.getItemName(),
					selection.getDestination()
				);
			if (!clean(menuDestination).isEmpty())
			{
				raw.add(menuDestination);
			}
			addEasyTeleportsAliases(raw, selection);
		}
		for (final String routeDestination : routeGuidanceDestinations)
		{
			raw.add(routeDestination);
		}

		final Set<String> normalized = new LinkedHashSet<>();
		for (final String value : raw)
		{
			final String candidate = normalizeChoice(value);
			if (!candidate.isEmpty())
			{
				normalized.add(candidate);
			}
		}

		cachedNormalizedDestinationAliases = Collections.unmodifiableSet(normalized);
		cachedAliasSelectionIdentity = lastSelectionIdentity;
		destinationAliasCacheDirty = false;
		return cachedNormalizedDestinationAliases;
	}

	private void addEasyTeleportsAliases(
		final Set<String> aliases,
		final SlayerTravelSelection selection)
	{
		if (configManager == null || aliases == null || aliases.isEmpty())
		{
			return;
		}
		final List<String> keys;
		try
		{
			keys = configManager.getConfigurationKeys(
				EASY_TELEPORTS_CONFIG_GROUP + "."
			);
		}
		catch (RuntimeException ignored)
		{
			return;
		}
		if (keys == null || keys.isEmpty())
		{
			return;
		}

		final String prefix = EASY_TELEPORTS_CONFIG_GROUP + ".";
		for (final String wholeKey : keys)
		{
			if (wholeKey == null || !wholeKey.startsWith(prefix))
			{
				continue;
			}
			final String key = wholeKey.substring(prefix.length());
			if (!key.startsWith("replacement"))
			{
				continue;
			}
			if (!SlayerTeleportRouteRegistry.matchesEasyTeleportsConfigKey(
				selection.getItemName(),
				selection.getDestination(),
				key
			))
			{
				continue;
			}
			try
			{
				final String replacement = clean(
					configManager.getConfiguration(EASY_TELEPORTS_CONFIG_GROUP, key)
				);
				if (!replacement.isEmpty())
				{
					aliases.add(replacement);
				}
			}
			catch (RuntimeException ignored)
			{
				// Optional companion config can disappear while plugins reload.
			}
		}
	}

	private boolean isActive()
	{
		if (closed)
		{
			return false;
		}
		final SlayerTravelSelection selection = selection();
		final String itemName = selection.getItemName();
		return client != null
			&& client.getGameState() == GameState.LOGGED_IN
			&& ((selection.hasPhysicalItem()
				&& itemName != null
				&& !itemName.trim().isEmpty())
				|| (!routeGuidanceItemName.isEmpty()
					&& !routeGuidanceDestinations.isEmpty()));
	}

	private String effectiveSelectedItemName()
	{
		return routeGuidanceItemName.isEmpty()
			? selection().getItemName()
			: routeGuidanceItemName;
	}

	private SlayerTravelSelection selection()
	{
		final SlayerTravelSelection value = selectionSupplier == null
			? null
			: selectionSupplier.get();
		return value == null
			? SlayerTravelSelection.unresolved("", -1L)
			: value;
	}

	private void synchronizeSelectionIdentity()
	{
		final String current = selectionIdentity();
		if (current.equals(lastSelectionIdentity))
		{
			return;
		}
		restoreWidgetHighlights();
		selectedItemEntries.clear();
		selectedDestinationEntries.clear();
		selectedItemMenuContext = false;
		capturedWidgetFields.clear();
		activeWidgetGroups.clear();
		pendingWidgetGroups.clear();
		ghommalWidgetDiscoveryTicksRemaining = 0;
		lastLoggedGhommalDestinationWidgetId = -1;
		lastSelectionIdentity = current;
		cachedAliasSelectionIdentity = "";
		cachedNormalizedDestinationAliases = Collections.emptySet();
		cachedSelectedItemFamily = itemFamily(effectiveSelectedItemName());
		destinationAliasCacheDirty = true;
	}

	private String selectionIdentity()
	{
		final SlayerTravelSelection selection = selection();
		/* Raw immutable selection fields are sufficient for identity and avoid the
		 * regex-heavy normalization that Portion 3 previously performed per menu entry. */
		return selection.getGeneration()
			+ "|" + String.valueOf(selection.getRouteIdentity())
			+ "|" + selection.getItemId()
			+ "|" + String.valueOf(selection.getItemName())
			+ "|" + String.valueOf(selection.getDestination())
			+ "|" + String.valueOf(selection.getStatus());
	}

	private static boolean matchesDestination(
		final String value,
		final Set<String> normalizedAliases)
	{
		if (normalizedAliases == null || normalizedAliases.isEmpty())
		{
			return false;
		}
		final String actual = normalizeChoice(value);
		return !actual.isEmpty() && normalizedAliases.contains(actual);
	}

	private static int widgetGroup(final Widget widget)
	{
		if (widget == null || widget.getId() < 0)
		{
			return -1;
		}
		return widget.getId() >>> 16;
	}

	private static String itemFamily(final String value)
	{
		return normalize(value)
			.replaceFirst("\\s+[a-z]?\\d+$", "")
			.trim();
	}

	private static String normalizeChoice(final String value)
	{
		return normalize(clean(value))
			.replaceFirst("^\\d+\\s+", "")
			.trim();
	}

	private static String normalize(final String value)
	{
		return clean(value).toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", " ")
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static String clean(final String value)
	{
		if (value == null)
		{
			return "";
		}
		return Text.removeTags(value.replaceAll("(?i)<br\\s*/?>", " "))
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static String highlight(final String value)
	{
		return ColorUtil.wrapWithColorTag(clean(value), TELEPORT_HIGHLIGHT_COLOR);
	}

	private static final class MenuPath
	{
		private final List<MenuEntry> entries;
		private final int score;

		private MenuPath(final List<MenuEntry> entries, final int score)
		{
			this.entries = entries;
			this.score = score;
		}
	}

	private static final class WidgetState
	{
		private final String text;
		private final String name;
		private final int textColor;

		private WidgetState(
			final String text,
			final String name,
			final int textColor)
		{
			this.text = text == null ? "" : text;
			this.name = name == null ? "" : name;
			this.textColor = textColor;
		}
	}
}
