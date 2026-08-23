package com.slayerplus;

import java.awt.Component;
import java.awt.Container;
import javax.swing.AbstractButton;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SlayerDiscordButtonRegressionTest
{
	@Test
	public void sidebarIncludesPersistentDiscordBugReportButton()
	{
		final SlayerPlusPanel panel = new SlayerPlusPanel();
		assertEquals(
			"https://discord.gg/WZCCPsTU67",
			SlayerPlusPanel.DISCORD_INVITE_URL
		);
		assertTrue(containsButtonText(panel, "Report a bug on Discord"));
	}

	private static boolean containsButtonText(
		final Component component,
		final String expected)
	{
		if (component instanceof AbstractButton)
		{
			final String text = ((AbstractButton) component).getText();
			if (text != null && text.contains(expected))
			{
				return true;
			}
		}

		if (component instanceof Container)
		{
			for (final Component child : ((Container) component).getComponents())
			{
				if (containsButtonText(child, expected))
				{
					return true;
				}
			}
		}
		return false;
	}
}
