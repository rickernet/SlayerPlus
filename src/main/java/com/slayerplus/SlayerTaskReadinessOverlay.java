package com.slayerplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Displays required task preparation. Travel guidance is handled by the
 * Shortest Path integration and teleport widget highlights.
 */
final class SlayerTaskReadinessOverlay extends OverlayPanel
{
	private static final Color READY = new Color(74, 214, 126);
	private static final Color WARNING = new Color(255, 171, 64);
	private static final Color MISSING = new Color(255, 92, 92);
	private static final Color MUTED = new Color(190, 190, 190);

	private final Client client;
	private final SlayerPlusPlugin plugin;
	private SlayerTaskPreparationCatalog.PreparationPlan renderedPlan;
	private String renderedFingerprint = "";
	private boolean rowsVisible;

	@Inject
	private SlayerTaskReadinessOverlay(
		final Client client,
		final SlayerPlusPlugin plugin)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		/*
		 * Use RuneLite's native top-right stack so the setup panel defaults just
		 * below the minimap and participates in normal overlay collision/layout.
		 * Users can still reposition overlays with RuneLite's overlay tools.
		 */
		setPosition(OverlayPosition.TOP_RIGHT);
		setPriority(PRIORITY_HIGH);
		setLayer(OverlayLayer.ABOVE_SCENE);
		panelComponent.setPreferredSize(new Dimension(330, 0));
	}

	@Override
	public Dimension render(final Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN
			|| !plugin.isGuidedSessionActiveForOverlay())
		{
			clearRows();
			return null;
		}


		final SlayerTaskPreparationCatalog.PreparationPlan plan =
			plugin.getCurrentPreparationForOverlay();
		final boolean setupRequired = plan != null
			&& plan.isActive()
			&& !plan.isReady();

		if (!setupRequired)
		{
			clearRows();
			return null;
		}

		/*
		 * PreparationPlan is immutable and is replaced whenever inventory or bank
		 * state changes. Object identity is therefore not a meaningful render key:
		 * rebuilding the panel for every ordinary withdrawal makes RuneLite briefly
		 * resize/repaint the box and looks like a flash. Rebuild only when something
		 * the overlay actually displays has changed.
		 */
		/* The exact immutable plan is normally reused for many rendered frames.
		 * Avoid allocating a StringBuilder/fingerprint on every frame when neither
		 * the preparation snapshot nor its visible rows changed. */
		if (rowsVisible && plan == renderedPlan)
		{
			return super.render(graphics);
		}
		final String fingerprint = renderFingerprint(plan);
		if (rowsVisible && fingerprint.equals(renderedFingerprint))
		{
			renderedPlan = plan;
			return super.render(graphics);
		}

		panelComponent.getChildren().clear();
		renderedPlan = plan;
		renderedFingerprint = fingerprint;
		rowsVisible = true;

		panelComponent.getChildren().add(
			TitleComponent.builder()
				.text(plan.getEncounterName().isEmpty()
					? "Slayer setup required"
					: plan.getEncounterName() + " setup required")
				.color(WARNING)
				.build()
		);
		panelComponent.getChildren().add(
			LineComponent.builder()
				.left(plan.getSpellbookName().isEmpty()
					? "Spellbook"
					: plan.getSpellbookName())
				.right(plan.isSpellbookReady()
					? "Ready"
					: "SWITCH SPELLBOOK")
				.rightColor(plan.isSpellbookReady() ? READY : MISSING)
				.build()
		);

		if (!plan.isLevelReady())
		{
			panelComponent.getChildren().add(
				LineComponent.builder()
					.left("Magic level")
					.right(plan.getMagicLevel() + " / " + plan.getRequiredMagicLevel())
					.rightColor(MISSING)
					.build()
			);
		}

		for (final SlayerTaskPreparationCatalog.RuneStatus rune
			: plan.getRuneStatuses())
		{
			final boolean runeReady = rune.isReadyForOneCast();
			panelComponent.getChildren().add(
				LineComponent.builder()
					.left(rune.getName() + " runes")
					.leftColor(MUTED)
					.right(rune.getAvailable() + " / " + rune.getPerCast())
					.rightColor(runeReady ? READY : MISSING)
					.build()
			);
		}

		if (!plan.getSpellName().isEmpty())
		{
			panelComponent.getChildren().add(
				LineComponent.builder()
					.left(plan.getSpellName() + " casts available")
					.right(Integer.toString(plan.getCastsAvailable()))
					.rightColor(plan.isRunesReady() ? READY : MISSING)
					.build()
			);
		}

		return super.render(graphics);
	}

	private void clearRows()
	{
		if (!rowsVisible && renderedPlan == null)
		{
			return;
		}
		panelComponent.getChildren().clear();
		renderedPlan = null;
		renderedFingerprint = "";
		rowsVisible = false;
	}

	static String renderFingerprint(
		final SlayerTaskPreparationCatalog.PreparationPlan plan)
	{
		if (plan == null || !plan.isActive() || plan.isReady())
		{
			return "hidden";
		}

		final StringBuilder value = new StringBuilder(128)
			.append(plan.getEncounterName()).append('|')
			.append(plan.getSpellbookName()).append('|')
			.append(plan.isSpellbookReady()).append('|')
			.append(plan.isLevelReady()).append('|')
			.append(plan.getMagicLevel()).append('|')
			.append(plan.getRequiredMagicLevel()).append('|')
			.append(plan.getSpellName()).append('|')
			.append(plan.getCastsAvailable()).append('|')
			.append(plan.isRunesReady());
		for (final SlayerTaskPreparationCatalog.RuneStatus rune
			: plan.getRuneStatuses())
		{
			value.append('|').append(rune.getName())
				.append(':').append(rune.getAvailable())
				.append('/').append(rune.getPerCast());
		}
		return value.toString();
	}

}
