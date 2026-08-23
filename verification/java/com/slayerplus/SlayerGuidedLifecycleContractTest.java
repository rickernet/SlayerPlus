package com.slayerplus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SlayerGuidedLifecycleContractTest
{
	@Test
	public void everyBankEndpointRetainsExactUnfinishedTaskRoute()
	{
		final GuideLifecycle.TaskRouteKey before =
			GuideLifecycle.taskRouteKey(
				"Araxytes",
				TaskVariant.ARAXXOR,
				"Araxxor",
				"Morytania Spider Cave"
			);

		for (final GuideLifecycle.BankEndpointKind endpoint
			: GuideLifecycle.BankEndpointKind.values())
		{
			final GuideLifecycle.TaskRouteKey after =
				GuideLifecycle.resumeTaskAfterBank(
					before,
					117,
					endpoint
				);
			assertSame(endpoint.name(), before, after);
			assertEquals("Araxytes", after.getAssignmentName());
			assertEquals(TaskVariant.ARAXXOR, after.getVariant());
			assertEquals("Araxxor", after.getEncounterName());
			assertEquals("Morytania Spider Cave", after.getLocation());
		}
	}

	@Test
	public void completedOrChangedTaskCannotResumeStaleBankDetour()
	{
		final GuideLifecycle.TaskRouteKey key =
			GuideLifecycle.taskRouteKey(
				"Blue dragons",
				TaskVariant.VORKATH,
				"Vorkath",
				"Ungael"
			);

		assertNull(GuideLifecycle.resumeTaskAfterBank(
			key,
			0,
			GuideLifecycle.BankEndpointKind.CHEST
		));
		assertTrue(key.belongsToAssignment("blue DRAGONS"));
		assertTrue(!key.belongsToAssignment("Black dragons"));
	}

	@Test
	public void routeIdentitySeparatesAssignmentVariantAndLocation()
	{
		final GuideLifecycle.TaskRouteKey regular =
			GuideLifecycle.taskRouteKey(
				"Blue dragons",
				TaskVariant.STANDARD_TASK,
				"Blue dragons",
				"Taverley Dungeon"
			);
		final GuideLifecycle.TaskRouteKey boss =
			GuideLifecycle.taskRouteKey(
				"Blue dragons",
				TaskVariant.VORKATH,
				"Vorkath",
				"Ungael"
			);

		assertTrue(!regular.routeIdentity().equals(boss.routeIdentity()));
		assertTrue(boss.routeIdentity().contains("assignment=blue dragons"));
		assertTrue(boss.routeIdentity().contains("variant=VORKATH"));
		assertTrue(boss.routeIdentity().contains("location=ungael"));
	}

	@Test
	public void masterReturnAlwaysUsesCarriedThenBankThenPhysicalPrecedence()
	{
		assertEquals(
			GuideLifecycle.MasterReturnPlan.CARRIED_TELEPORT,
			GuideLifecycle.masterReturnPlan(true, true, true)
		);
		assertEquals(
			GuideLifecycle.MasterReturnPlan.CARRIED_TELEPORT,
			GuideLifecycle.masterReturnPlan(true, false, false)
		);
		assertEquals(
			GuideLifecycle.MasterReturnPlan.BANK_OWNED_TELEPORT,
			GuideLifecycle.masterReturnPlan(false, true, true)
		);
		assertEquals(
			GuideLifecycle.MasterReturnPlan.PHYSICAL_PATH,
			GuideLifecycle.masterReturnPlan(false, true, false)
		);
		assertEquals(
			GuideLifecycle.MasterReturnPlan.PHYSICAL_PATH,
			GuideLifecycle.masterReturnPlan(false, false, true)
		);
	}

	@Test
	public void completedTaskBankDetourResumesOnlyThePendingMasterRoute()
	{
		assertTrue(SlayerPlusPlugin.shouldResumeMasterAfterBankForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_BANK,
			true,
			0
		));
		assertTrue(!SlayerPlusPlugin.shouldResumeMasterAfterBankForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_BANK,
			true,
			12
		));
		assertTrue(!SlayerPlusPlugin.shouldResumeMasterAfterBankForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_BANK,
			false,
			0
		));
		assertTrue(!SlayerPlusPlugin.shouldResumeMasterAfterBankForRegression(
			SlayerPlusPlugin.GuidedSessionPhase.ROUTING_TO_TASK,
			true,
			0
		));
	}

	@Test
	public void everyRecordedMasterHasAStableFinalDestination()
	{
		for (int masterId = 1; masterId <= 10; masterId++)
		{
			final MasterRoutes.MasterRoute route =
				MasterRoutes.find(masterId);
			assertTrue("master " + masterId, route != null);
			assertTrue("master " + masterId + " destination",
				route.getDestination() != null);
		}
	}
}
