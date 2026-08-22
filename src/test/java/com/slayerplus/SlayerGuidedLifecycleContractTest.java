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
		final SlayerGuidedLifecycleContract.TaskRouteKey before =
			SlayerGuidedLifecycleContract.taskRouteKey(
				"Araxytes",
				SlayerTaskVariant.ARAXXOR,
				"Araxxor",
				"Morytania Spider Cave"
			);

		for (final SlayerGuidedLifecycleContract.BankEndpointKind endpoint
			: SlayerGuidedLifecycleContract.BankEndpointKind.values())
		{
			final SlayerGuidedLifecycleContract.TaskRouteKey after =
				SlayerGuidedLifecycleContract.resumeTaskAfterBank(
					before,
					117,
					endpoint
				);
			assertSame(endpoint.name(), before, after);
			assertEquals("Araxytes", after.getAssignmentName());
			assertEquals(SlayerTaskVariant.ARAXXOR, after.getVariant());
			assertEquals("Araxxor", after.getEncounterName());
			assertEquals("Morytania Spider Cave", after.getLocation());
		}
	}

	@Test
	public void completedOrChangedTaskCannotResumeStaleBankDetour()
	{
		final SlayerGuidedLifecycleContract.TaskRouteKey key =
			SlayerGuidedLifecycleContract.taskRouteKey(
				"Blue dragons",
				SlayerTaskVariant.VORKATH,
				"Vorkath",
				"Ungael"
			);

		assertNull(SlayerGuidedLifecycleContract.resumeTaskAfterBank(
			key,
			0,
			SlayerGuidedLifecycleContract.BankEndpointKind.CHEST
		));
		assertTrue(key.belongsToAssignment("blue DRAGONS"));
		assertTrue(!key.belongsToAssignment("Black dragons"));
	}

	@Test
	public void routeIdentitySeparatesAssignmentVariantAndLocation()
	{
		final SlayerGuidedLifecycleContract.TaskRouteKey regular =
			SlayerGuidedLifecycleContract.taskRouteKey(
				"Blue dragons",
				SlayerTaskVariant.STANDARD_TASK,
				"Blue dragons",
				"Taverley Dungeon"
			);
		final SlayerGuidedLifecycleContract.TaskRouteKey boss =
			SlayerGuidedLifecycleContract.taskRouteKey(
				"Blue dragons",
				SlayerTaskVariant.VORKATH,
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
			SlayerGuidedLifecycleContract.MasterReturnPlan.CARRIED_TELEPORT,
			SlayerGuidedLifecycleContract.masterReturnPlan(true, true, true)
		);
		assertEquals(
			SlayerGuidedLifecycleContract.MasterReturnPlan.CARRIED_TELEPORT,
			SlayerGuidedLifecycleContract.masterReturnPlan(true, false, false)
		);
		assertEquals(
			SlayerGuidedLifecycleContract.MasterReturnPlan.BANK_OWNED_TELEPORT,
			SlayerGuidedLifecycleContract.masterReturnPlan(false, true, true)
		);
		assertEquals(
			SlayerGuidedLifecycleContract.MasterReturnPlan.PHYSICAL_PATH,
			SlayerGuidedLifecycleContract.masterReturnPlan(false, true, false)
		);
		assertEquals(
			SlayerGuidedLifecycleContract.MasterReturnPlan.PHYSICAL_PATH,
			SlayerGuidedLifecycleContract.masterReturnPlan(false, false, true)
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
			final SlayerMasterRouteCatalog.MasterRoute route =
				SlayerMasterRouteCatalog.find(masterId);
			assertTrue("master " + masterId, route != null);
			assertTrue("master " + masterId + " destination",
				route.getDestination() != null);
		}
	}
}
