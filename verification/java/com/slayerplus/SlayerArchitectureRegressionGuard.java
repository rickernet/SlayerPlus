package com.slayerplus;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/** Fast structural invariants that protect the shared routing/travel plumbing. */
public final class SlayerArchitectureRegressionGuard
{
	private SlayerArchitectureRegressionGuard()
	{
	}

	public static void validateOrThrow()
	{
		validateTravelAuthority();
		validateTravelLifecycleIsolation();
		validateProfileConsumerIsolation();
		validatePhysicalVariantRepair();
		validateBankTagTravelExclusivity();
		validateLiveProfileSwitchBankReuse();
		validatePostTaskMasterReturnTravel();
		validateInfernoPreparationStaging();
		validateTravelItemUsability();
		validateShortestPathAlternativeSelection();
		validateCatacombsStaging();
		validateTeleportEquivalence();
	}

	private static void validateTravelAuthority()
	{
		final SlayerTravelCoordinator coordinator = new SlayerTravelCoordinator();
		coordinator.beginRoute("regression-route");
		final long firstGeneration = coordinator.getGeneration();
		coordinator.seedProvisionalItem(
			"regression-route", 1, "Rada's blessing 4", "Kourend Woodland"
		);
		final Set<String> edgeFamilies = new LinkedHashSet<>(Arrays.asList(
			"Max cape",
			"Construct. cape",
			"Teleport to house"
		));
		coordinator.resolveItem(
			"regression-route",
			2,
			"Max cape",
			"Teleport to house",
			edgeFamilies
		);
		coordinator.seedProvisionalItem(
			"regression-route", 1, "Rada's blessing 4", "Kourend Woodland"
		);
		if (coordinator.current().getItemId() != 2
			|| !coordinator.current().isResolved()
			|| !coordinator.current().isStructurallyValid()
			|| !coordinator.current().getEquivalents().equals(edgeFamilies))
		{
			throw new IllegalStateException(
				"Travel regression: provisional travel overrode a resolved Shortest Path winner"
			);
		}

		/*
		 * Simulate a profile object being replaced before the invalidation call.
		 * Even if the caller supplies the new identity, the coordinator must evict
		 * the old active route because invalidateRoute is a hard generation boundary.
		 */
		coordinator.invalidateRoute("replacement-profile");
		if (coordinator.getGeneration() <= firstGeneration
			|| coordinator.ownsCurrentRoute("regression-route")
			|| coordinator.selectionFor("regression-route").isResolved())
		{
			throw new IllegalStateException(
				"Travel regression: route/profile invalidation retained stale active Shortest Path state"
			);
		}
	}

	private static void validateTravelLifecycleIsolation()
	{
		final SlayerTravelCoordinator coordinator = new SlayerTravelCoordinator();
		coordinator.beginRoute("account-a-route");
		coordinator.resolveItem(
			"account-a-route", 42, "Slayer ring (8)", "Dark Beasts"
		);
		coordinator.clearAll();

		if (coordinator.current().physical()
			|| coordinator.current().isResolved()
			|| coordinator.selectionFor("account-a-route").isResolved())
		{
			throw new IllegalStateException(
				"Travel regression: hard lifecycle reset retained an account-specific cached route"
			);
		}
	}

	private static void validateProfileConsumerIsolation()
	{
		final SlayerTravelCoordinator coordinator = new SlayerTravelCoordinator();
		coordinator.beginRoute("regular-smoke-devils");
		coordinator.resolveItem(
			"regular-smoke-devils",
			10,
			"Max cape",
			"Home"
		);

		if (coordinator.currentFor("thermonuclear-smoke-devil").physical())
		{
			throw new IllegalStateException(
				"Travel regression: a consumer could observe another profile's live travel selection"
			);
		}

		coordinator.invalidateRoute("regular-smoke-devils");
		coordinator.beginRoute("thermonuclear-smoke-devil");
		final long bossGeneration = coordinator.getGeneration();

		/* Simulate a late worker callback from the regular route. */
		coordinator.resolveItem(
			"regular-smoke-devils",
			11,
			"Teleport to house",
			"Home"
		);
		if (!coordinator.ownsCurrentRoute("thermonuclear-smoke-devil")
			|| coordinator.getGeneration() != bossGeneration
			|| coordinator.current().physical()
			|| coordinator.selectionFor("regular-smoke-devils").isResolved())
		{
			throw new IllegalStateException(
				"Travel regression: stale regular/boss worker result crossed a profile generation boundary"
			);
		}
	}

	private static void validatePhysicalVariantRepair()
	{
		final SlayerTravelCoordinator coordinator = new SlayerTravelCoordinator();
		final Set<String> families = new LinkedHashSet<>(Arrays.asList(
			"Slayer ring",
			"Eternal slayer ring"
		));
		coordinator.beginRoute("dark-beasts");
		final long generation = coordinator.getGeneration();
		coordinator.resolveItem(
			"dark-beasts",
			20,
			"Slayer ring (8)",
			"ME2 Caves",
			families
		);
		final TravelChoice repaired = coordinator.replacePhysicalItemVariant(
			"dark-beasts",
			21,
			"Eternal slayer ring"
		);
		if (repaired.getItemId() != 21
			|| !"Eternal slayer ring".equals(repaired.getItemName())
			|| !"ME2 Caves".equals(repaired.getDestination())
			|| repaired.getGeneration() != generation
			|| !repaired.getEquivalents().equals(families)
			|| !repaired.isResolved())
		{
			throw new IllegalStateException(
				"Travel regression: physical item-state repair split item identity from route/destination authority"
			);
		}
	}

	private static void validateBankTagTravelExclusivity()
	{
		final Set<String> families = new LinkedHashSet<>(Arrays.asList(
			"Max cape",
			"Construct. cape",
			"Teleport to house"
		));
		final TravelChoice selection = TravelChoice.resolvedItem(
			"smoke-devils",
			1L,
			100,
			"Max cape",
			"Home",
			families
		);
		final KitItem selected = new KitItem(
			"Max cape", 100, 1, KitItem.Status.BANK
		);
		final KitItem constructionCape = new KitItem(
			"Construction cape", 101, 1, KitItem.Status.BANK
		);
		final KitItem houseTablet = new KitItem(
			"Teleport to house", 102, 1, KitItem.Status.BANK
		);
		final KitItem prayerPotion = new KitItem(
			"Prayer potion(4)", 103, 1, KitItem.Status.BANK
		);

		if (!BankTagLayout.shouldSuppressInventoryTravelItem(
			true, 0, 100, prayerPotion, selection
		) || !BankTagLayout.shouldSuppressInventoryTravelItem(
			true, 1, 100, selected, selection
		) || !BankTagLayout.shouldSuppressInventoryTravelItem(
			true, 2, 100, constructionCape, selection
		) || !BankTagLayout.shouldSuppressInventoryTravelItem(
			true, 3, 100, houseTablet, selection
		) || BankTagLayout.shouldSuppressInventoryTravelItem(
			true, 4, 100, prayerPotion, selection
		))
		{
			throw new IllegalStateException(
				"Travel regression: Bank Tag no longer guarantees exactly one authoritative route teleport in the 4x7 inventory"
			);
		}
	}

	private static void validateLiveProfileSwitchBankReuse()
	{
		/*
		 * A regular <-> boss or other live profile switch commonly happens after
		 * leaving the bank. The already-scanned bank snapshot must remain eligible
		 * for the replacement Shortest Path route even though the bank UI is closed.
		 */
		if (!SlayerPlusPlugin.shouldUseBankAwareProfileReroute(true, false)
			|| !SlayerPlusPlugin.shouldUseBankAwareProfileReroute(false, true)
			|| SlayerPlusPlugin.shouldUseBankAwareProfileReroute(false, false))
		{
			throw new IllegalStateException(
				"Travel regression: live encounter/profile switch lost reusable bank state"
			);
		}
	}

	private static void validatePostTaskMasterReturnTravel()
	{
		/*
		 * A completed task has no encounter profile, but the route back to the
		 * last-used Slayer master still needs Shortest Path transport publication so
		 * the exact carried item can drive the menu highlighter. The automatic
		 * completion leg remains inventory-only; an explicit bank-open event may
		 * separately rebuild it with bank access enabled.
		 */
		if (!ShortestPathBridge.shouldPostTransportUpdates(false, true, false)
			|| ShortestPathBridge.shouldPostTransportUpdates(false, false, false)
			|| ShortestPathBridge.shouldPostTransportUpdates(false, true, true))
		{
			throw new IllegalStateException(
				"Travel regression: post-task master routes no longer publish carried transport selections safely"
			);
		}

		final SlayerTravelCoordinator coordinator = new SlayerTravelCoordinator();
		coordinator.beginRoute("master|id=6");
		coordinator.resolveItem(
			"master|id=6",
			500,
			"Max cape",
			"POH Portals: Brimhaven"
		);
		if (!coordinator.currentFor("master|id=6").physical()
			|| coordinator.currentFor("task|dust-devils").physical())
		{
			throw new IllegalStateException(
				"Travel regression: master-return travel selection was not isolated from the completed task profile"
			);
		}
	}

	private static void validateInfernoPreparationStaging()
	{
		if (!RouteCatalog.requiresPreparationBank(
			"TzKal-Zuk", "Inferno", true
		) || RouteCatalog.requiresPreparationBank(
			"TzKal-Zuk", "Inferno", false
		) || !RouteCatalog.getPreparationEntryNpcNames(
			"TzKal-Zuk", "Inferno", true
		).contains("tzhaar ket keh")
			|| !new WorldPoint(2496, 5115, 0).equals(
				RouteCatalog.getPreparationEntryApproachTarget(
					"TzKal-Zuk", "Inferno", true
				)
			))
		{
			throw new IllegalStateException(
				"Routing regression: TzKal-Zuk no longer requires Zuk-bank -> exact TzHaar-Ket-Keh staging"
			);
		}

		if (BankRoutes.infernoBankTarget() == null
			|| !new WorldPoint(2495, 5157, 0).equals(
				BankRoutes.getInfernoPreparationHotVentDoorTarget()
			)
			|| TravelRoutes.fallbacksFor(
				"Mor Ul Rek east bank"
			).isEmpty())
		{
			throw new IllegalStateException(
				"Routing regression: Inferno preparation has no exact Hot vent, bank, or travel branch"
			);
		}

		/*
		 * Ghommal -> Mor Ul Rek is deliberately a SlayerPlus-owned shortcut because
		 * current Shortest Path transport data does not model that edge. SlayerPlus
		 * clears that unsupported request, then starts a separate local bank route as
		 * soon as the external teleport landing is observed.
		 */
		if (!TravelRoutes.requiresExternalTeleportHandoff(
			"Mor Ul Rek east bank", "Ghommal's hilt 6", "Mor Ul Rek"
		) || TravelRoutes.requiresExternalTeleportHandoff(
			"Catacombs of Kourend", "Xeric's talisman", "Xeric's Heart"
		))
		{
			throw new IllegalStateException(
				"Routing regression: external Mor Ul Rek teleport handoff classification changed"
			);
		}

		final TravelChoice selection = TravelChoice.resolvedItem(
			"zuk-prep",
			1L,
			200,
			"Ghommal's hilt 6",
			"Mor Ul Rek",
			new LinkedHashSet<>(Arrays.asList("Ghommal's hilt 6"))
		);
		final KitItem firstCombatItem = new KitItem(
			"Super restore(4)", 201, 1, KitItem.Status.BANK
		);
		if (BankTagLayout.shouldSuppressInventoryTravelItem(
			true, 0, 200, firstCombatItem, selection, false
		))
		{
			throw new IllegalStateException(
				"Bank Tag regression: Zuk staging incorrectly suppresses combat inventory slot 1"
			);
		}

		if (!BankTagLayout.usesExternalStagingTravelStrip(-1)
			|| BankTagLayout.usesExternalStagingTravelStrip(0))
		{
			throw new IllegalStateException(
				"Bank Tag regression: Zuk staging travel no longer lives above the 4x7 inventory"
			);
		}

		if (!BankTagLayout.hasDedicatedExtraQuiverAmmoPositionForTest())
		{
			throw new IllegalStateException(
				"Bank Tag regression: Dizana's second ammunition cell no longer sits above the ordinary ammo cell"
			);
		}

		if (SlayerPlusPlugin.infernoPreparationBankApproachRadiusForTest() < 20)
		{
			throw new IllegalStateException(
				"Routing regression: Inferno preparation-bank approach threshold is too small for the live-banker handoff"
			);
		}

		if (ShortestPathBridge.preparationBankApproachThresholdForTest() < 2)
		{
			throw new IllegalStateException(
				"Routing regression: catalog bank targets must accept Shortest Path's normal adjacent-tile arrival"
			);
		}

		if (ShortestPathBridge.preparationAccessApproachThresholdForTest() != 2)
		{
			throw new IllegalStateException(
				"Routing regression: manual preparation access must finish beside its exact interaction boundary"
			);
		}
	}

	private static void validateTravelItemUsability()
	{
		final String[] unusable =
		{
			"Xeric's talisman (inert)",
			"Teleport crystal (uncharged)",
			"Example teleport (depleted)",
			"Ring of dueling(0)",
			"Example teleport - 0 charges"
		};
		for (final String name : unusable)
		{
			if (SlayerTravelItemPolicy.isUsableDisplayName(name))
			{
				throw new IllegalStateException(
					"Travel regression: unusable charged item accepted: " + name
				);
			}
		}

		final String[] usable =
		{
			"Xeric's talisman",
			"Rada's blessing 4",
			"Games necklace (8)",
			"Max cape"
		};
		for (final String name : usable)
		{
			if (!SlayerTravelItemPolicy.isUsableDisplayName(name))
			{
				throw new IllegalStateException(
					"Travel regression: usable item rejected: " + name
				);
			}
		}
	}

	private static void validateShortestPathAlternativeSelection()
	{
		final int maxCape = TravelRoutes.liveCandidatePreference("Max cape");
		final int constructionCape = TravelRoutes.liveCandidatePreference(
			"Construct. cape"
		);
		final int houseTablet = TravelRoutes.liveCandidatePreference(
			"Teleport to house"
		);
		if (!(maxCape > constructionCape && constructionCape > houseTablet))
		{
			throw new IllegalStateException(
				"Travel regression: Shortest Path POH alternatives lost deterministic Max cape > Construction cape > house tablet ordering"
			);
		}
	}

	private static void validateCatacombsStaging()
	{
		final Set<String> tasks = new LinkedHashSet<>(Arrays.asList(
			"Abyssal demons",
			"Bloodvelds",
			"Dust devils",
			"Nechryaels",
			"Hellhounds",
			"Fire giants",
			"Greater demons",
			"Black demons",
			"Aberrant spectres"
		));
		for (final String task : tasks)
		{
			final RouteCatalog.RouteProfile profile = RouteCatalog.resolve(
				task,
				"Catacombs of Kourend",
				false
			);
			if (profile == null || !profile.isStaged()
				|| profile.getTransitionSpec() == null
				|| !profile.getTransitionSpec().matchesAction("Investigate"))
			{
				throw new IllegalStateException(
					"Route regression: Catacombs task bypasses King Rada transition: " + task
				);
			}
		}
	}

	private static void validateTeleportEquivalence()
	{
		if (!SlayerTeleportRouteRegistry.destinationAliases(
			"Slayer ring (8)", "Dark Beasts"
		).contains("ME2 Caves"))
		{
			throw new IllegalStateException(
				"Teleport regression: Slayer ring Dark Beasts/ME2 Caves equivalence missing"
			);
		}

		if (!SlayerTeleportRouteRegistry.destinationAliases(
			"Max cape", "POH"
		).contains("Teleport to house")
			|| !SlayerTeleportRouteRegistry.destinationAliases(
				"Max cape", "Home"
			).contains("Teleport to house"))
		{
			throw new IllegalStateException(
				"Teleport regression: Max cape POH menu equivalence missing"
			);
		}

		final Set<String> annotatedPohAliases =
			SlayerTeleportRouteRegistry.destinationAliases(
				"Max cape",
				"Tele to POH (exit: Fairy Ring B K P)"
			);
		if (!annotatedPohAliases.contains("Home")
			|| !annotatedPohAliases.contains("Tele to POH")
			|| !SlayerTeleportRouteRegistry.matchesEasyTeleportsConfigKey(
				"Max cape",
				"Tele to POH (exit: Fairy Ring B K P)",
				"replacementMaxCapeHome"
			))
		{
			throw new IllegalStateException(
				"Teleport regression: annotated Max cape POH route no longer maps to the live Home submenu"
			);
		}

		if (!SlayerTeleportRouteRegistry.destinationAliases(
			"Max cape",
			"POH Portals: Brimhaven"
		).contains("Brimhaven"))
		{
			throw new IllegalStateException(
				"Teleport regression: nested Max cape POH portal route no longer maps to its live submenu leaf"
			);
		}

		if (!SlayerTeleportRouteRegistry.matchesEasyTeleportsConfigKey(
			"Slayer ring (8)", "ME2 Caves", "replacementDarkBeasts"
		) || !SlayerTeleportRouteRegistry.matchesEasyTeleportsConfigKey(
			"Games necklace (8)", "Wintertodt Camp", "replacementGamesWintertodtCamp"
		) || !SlayerTeleportRouteRegistry.matchesEasyTeleportsConfigKey(
			"Skills necklace (6)", "Farming Guild", "replacementSkillsFarmingGuild"
		) || !SlayerTeleportRouteRegistry.matchesEasyTeleportsConfigKey(
			"Max cape", "POH", "replacementMaxCapeHome"
		))
		{
			throw new IllegalStateException(
				"Teleport regression: Easy Teleports destination-key matching failed"
			);
		}

		if (SlayerTeleportRouteRegistry.matchesEasyTeleportsConfigKey(
			"Skills necklace (6)", "Farming Guild", "replacementGamesWintertodtCamp"
		))
		{
			throw new IllegalStateException(
				"Teleport regression: unrelated Easy Teleports replacement matched the selected route"
			);
		}
	}
}
