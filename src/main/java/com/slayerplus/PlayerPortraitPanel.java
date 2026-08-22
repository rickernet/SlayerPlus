package com.slayerplus;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.time.LocalTime;
import javax.swing.JPanel;
import javax.swing.Timer;

public class PlayerPortraitPanel extends JPanel
{
	private static final int WIDTH = 144;
	private static final int HEIGHT = 144;
	private static final int PORTRAIT_SIZE = 140;
	private static final int ARC = 11;
	private static final int CLOCK_REFRESH_MS = 60_000;
	private static final int FADE_FRAME_MS = 33;
	private static final int FADE_DURATION_MS = 230;
	private static final int SUNRISE_MINUTE = 5 * 60 + 30;
	private static final int SUNSET_MINUTE = 20 * 60 + 30;

	private static final Color BORDER = new Color(91, 125, 151);
	private static final Color INNER_HIGHLIGHT =
		new Color(255, 255, 255, 38);

	private final Timer clockTimer;
	private final Timer fadeTimer;
	private BufferedImage portrait;
	private BufferedImage portraitShadow;
	private BufferedImage previousPortrait;
	private BufferedImage previousPortraitShadow;
	private long fadeStartedAtNanos;
	private boolean portraitIsDark;

	public PlayerPortraitPanel()
	{
		setOpaque(false);
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
		setMinimumSize(new Dimension(WIDTH, HEIGHT));
		setMaximumSize(new Dimension(WIDTH, HEIGHT));

		/*
		 * Repaint once per minute so the background can move between dawn,
		 * daytime, sunset, and night without requiring an equipment change.
		 */
		clockTimer = new Timer(
			CLOCK_REFRESH_MS,
			event -> repaint()
		);
		clockTimer.setRepeats(true);

		fadeTimer = new Timer(
			FADE_FRAME_MS,
			event -> advanceFade()
		);
		fadeTimer.setRepeats(true);
	}

	public void setPortrait(final BufferedImage portrait)
	{
		fadeTimer.stop();
		this.previousPortrait = this.portrait;
		this.previousPortraitShadow = this.portraitShadow;
		this.portrait = portrait;
		this.portraitShadow = portrait == null
			? null
			: createShadow(portrait);
		this.portraitIsDark = portrait != null
			&& isMostlyDark(portrait);

		if (portrait != null && previousPortrait != null)
		{
			fadeStartedAtNanos = System.nanoTime();
			if (isDisplayable())
			{
				fadeTimer.start();
			}
			else
			{
				clearPreviousPortrait();
			}
		}
		else
		{
			clearPreviousPortrait();
		}
		repaint();
	}

	@Override
	public void removeNotify()
	{
		disposeTimers();
		super.removeNotify();
	}

	/** Explicit plugin-shutdown boundary for panels that were constructed but
	 * never attached, where Swing therefore never calls removeNotify(). */
	public void disposeTimers()
	{
		clockTimer.stop();
		fadeTimer.stop();
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		if (!clockTimer.isRunning())
		{
			clockTimer.start();
		}
		if (previousPortrait != null && portrait != null
			&& fadeProgress() < 1.0f)
		{
			fadeTimer.start();
		}
	}

	@Override
	protected void paintComponent(final Graphics graphics)
	{
		super.paintComponent(graphics);

		final LocalTime now = LocalTime.now();
		final TimePalette palette = TimePalette.forTime(now);

		final Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(
			RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON
		);
		g.setRenderingHint(
			RenderingHints.KEY_RENDERING,
			RenderingHints.VALUE_RENDER_QUALITY
		);
		g.setRenderingHint(
			RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BILINEAR
		);

		final Shape frame = new RoundRectangle2D.Float(
			1,
			1,
			WIDTH - 3,
			HEIGHT - 3,
			ARC,
			ARC
		);

		g.setClip(frame);

		final LinearGradientPaint sky = new LinearGradientPaint(
			new Point2D.Float(0, 0),
			new Point2D.Float(0, HEIGHT),
			new float[]{0.0f, 0.58f, 1.0f},
			new Color[]{
				palette.skyTop,
				palette.skyMiddle,
				palette.skyBottom
			}
		);
		g.setPaint(sky);
		g.fillRect(0, 0, WIDTH, HEIGHT);

		/*
		 * The light source changes with the time palette while preserving the
		 * same professional portrait-card treatment.
		 */
		final RadialGradientPaint glow = new RadialGradientPaint(
			new Point2D.Float(
				palette.glowX,
				palette.glowY
			),
			palette.glowRadius,
			new float[]{0.0f, 0.72f, 1.0f},
			new Color[]{
				palette.glowCenter,
				palette.glowMiddle,
				new Color(0, 0, 0, 0)
			}
		);
		g.setPaint(glow);
		g.fillRect(0, 0, WIDTH, HEIGHT);

		drawCelestialBody(g, now);

		/*
		 * A subtle lower haze separates the shoulders from the sky.
		 */
		g.setPaint(new LinearGradientPaint(
			new Point2D.Float(0, HEIGHT * 0.68f),
			new Point2D.Float(0, HEIGHT),
			new float[]{0.0f, 1.0f},
			new Color[]{
				new Color(255, 255, 255, 0),
				palette.lowerHaze
			}
		));
		g.fillRect(0, 0, WIDTH, HEIGHT);

		/*
		 * A restrained studio base grounds the model without competing with
		 * armour colours or the time-of-day sky.
		 */
		g.setColor(new Color(3, 7, 12, 54));
		g.fillOval(14, HEIGHT - 27, WIDTH - 28, 28);
		g.setColor(new Color(226, 239, 248, 22));
		g.drawArc(18, HEIGHT - 24, WIDTH - 36, 18, 8, 164);

		/* Keep pale robes and dark armour equally legible. */
		if (portrait != null)
		{
			g.setPaint(new RadialGradientPaint(
				new Point2D.Float(WIDTH / 2.0f, HEIGHT * 0.48f),
				58.0f,
				new float[]{0.0f, 0.74f, 1.0f},
				new Color[]{
					portraitIsDark
						? new Color(235, 246, 255, 48)
						: new Color(2, 7, 13, 40),
					portraitIsDark
						? new Color(190, 218, 238, 13)
						: new Color(4, 10, 18, 12),
					new Color(0, 0, 0, 0)
				}
			));
			g.fillRect(0, 0, WIDTH, HEIGHT);
		}

		final int drawX = (WIDTH - PORTRAIT_SIZE) / 2;
		final int drawY = (HEIGHT - PORTRAIT_SIZE) / 2;

		if (portrait != null)
		{
			final float progress = fadeProgress();
			if (previousPortrait != null && progress < 1.0f)
			{
					drawPortraitLayer(
						g,
						previousPortrait,
						previousPortraitShadow,
						drawX,
					drawY,
					1.0f - progress
				);
			}
			drawPortraitLayer(
				g,
				portrait,
				portraitShadow,
				drawX,
				drawY,
				progress
			);
		}
		else
		{
			final int placeholderCenterX = WIDTH / 2;
			final int headWidth = 34;
			final int bodyWidth = 64;
			g.setColor(palette.placeholder);
			g.fillOval(
				placeholderCenterX - headWidth / 2,
				27,
				headWidth,
				38
			);
			g.fillRoundRect(
				placeholderCenterX - bodyWidth / 2,
				65,
				bodyWidth,
				35,
				18,
				18
			);
		}

		/*
		 * Gentle vignette keeps attention on the player model.
		 */
		final RadialGradientPaint vignette = new RadialGradientPaint(
			new Point2D.Float(WIDTH / 2.0f, HEIGHT / 2.0f),
			88.0f,
			new float[]{0.56f, 1.0f},
			new Color[]{
				new Color(0, 0, 0, 0),
				palette.vignette
			}
		);
		g.setPaint(vignette);
		g.fillRect(0, 0, WIDTH, HEIGHT);

		g.setClip(null);

		g.setColor(palette.border);
		g.setStroke(new BasicStroke(1.4f));
		g.draw(frame);

		g.setColor(INNER_HIGHLIGHT);
		g.setStroke(new BasicStroke(1.0f));
		g.draw(new RoundRectangle2D.Float(
			2.5f,
			2.5f,
			WIDTH - 6,
			HEIGHT - 6,
			ARC - 2,
			ARC - 2
		));

		g.dispose();
	}

	private static void drawCelestialBody(
		final Graphics2D graphics,
		final LocalTime time)
	{
		final float minute = time.getHour() * 60.0f
			+ time.getMinute()
			+ time.getSecond() / 60.0f;

		if (minute >= SUNRISE_MINUTE && minute < SUNSET_MINUTE)
		{
			final float travel = (minute - SUNRISE_MINUTE)
				/ (SUNSET_MINUTE - SUNRISE_MINUTE);
			drawSun(graphics, travel, horizonVisibility(travel));
			return;
		}

		final float nightMinute = minute < SUNRISE_MINUTE
			? minute + 24.0f * 60.0f
			: minute;
		final float nightLength = 24.0f * 60.0f
			- SUNSET_MINUTE + SUNRISE_MINUTE;
		final float travel = (nightMinute - SUNSET_MINUTE) / nightLength;
		drawMoon(graphics, travel, horizonVisibility(travel));
	}

	private static void drawSun(
		final Graphics2D graphics,
		final float travel,
		final float visibility)
	{
		final float x = celestialX(travel);
		final float y = celestialY(travel, 3.0f, 8.0f);
		final float horizonWarmth = Math.abs(travel * 2.0f - 1.0f);
		final Color sunLight = mixColor(
			new Color(247, 235, 182),
			new Color(239, 176, 83),
			horizonWarmth
		);
		final Color sunMiddle = mixColor(
			new Color(214, 179, 92),
			new Color(193, 112, 44),
			horizonWarmth
		);
		final Color sunEdge = mixColor(
			new Color(151, 99, 39),
			new Color(123, 65, 30),
			horizonWarmth
		);
		graphics.setPaint(new RadialGradientPaint(
			new Point2D.Float(x, y),
			38.0f,
			new float[]{0.0f, 0.42f, 1.0f},
			new Color[]{
				withAlpha(sunLight, 104, visibility),
				withAlpha(sunMiddle, 32, visibility),
				new Color(255, 180, 70, 0)
			}
		));
		graphics.fill(new Ellipse2D.Float(x - 38, y - 38, 76, 76));

		final Shape sun = regularPolygon(x, y, 19.0f, 10, -Math.PI / 2.0);
		graphics.setPaint(new LinearGradientPaint(
			new Point2D.Float(x - 8.0f, y - 15.0f),
			new Point2D.Float(x + 8.0f, y + 17.0f),
			new float[]{0.0f, 0.52f, 1.0f},
			new Color[]{
				withAlpha(sunLight, 238, visibility),
				withAlpha(sunMiddle, 242, visibility),
				withAlpha(sunEdge, 238, visibility)
			}
		));
		graphics.fill(sun);
		graphics.setColor(withAlpha(
			new Color(255, 229, 157), 62, visibility
		));
		graphics.fill(facet(
			x, y, x - 13.0f, y + 4.0f, x - 7.0f, y - 13.0f
		));
	}

	private static void drawMoon(
		final Graphics2D graphics,
		final float travel,
		final float visibility)
	{
		final float x = celestialX(travel);
		final float y = celestialY(travel, 4.0f, 8.0f);
		final float horizonTint = Math.abs(travel * 2.0f - 1.0f);
		final Color moonLight = mixColor(
			new Color(199, 214, 228),
			new Color(181, 181, 211),
			horizonTint
		);
		final Color moonShade = mixColor(
			new Color(63, 79, 105),
			new Color(72, 61, 94),
			horizonTint
		);
		graphics.setPaint(new RadialGradientPaint(
			new Point2D.Float(x, y),
			37.0f,
			new float[]{0.0f, 0.48f, 1.0f},
			new Color[]{
				withAlpha(new Color(210, 229, 249), 116, visibility),
				withAlpha(new Color(119, 158, 205), 38, visibility),
				new Color(110, 145, 190, 0)
			}
		));
		graphics.fill(new Ellipse2D.Float(x - 37, y - 37, 74, 74));

		final Shape moonBody = regularPolygon(
			x, y, 20.0f, 10, -Math.PI / 2.0
		);
		graphics.setColor(withAlpha(
			moonShade, 116, visibility
		));
		graphics.fill(moonBody);

		final Area crescent = new Area(moonBody);
		crescent.subtract(new Area(
			regularPolygon(x + 11.0f, y - 3.0f, 20.0f, 10, -Math.PI / 2.0)
		));
		graphics.setColor(withAlpha(
			moonLight, 228, visibility
		));
		graphics.fill(crescent);

		graphics.setColor(withAlpha(
			new Color(231, 238, 244), 48, visibility
		));
		graphics.fill(facet(
			x - 2.0f, y,
			x - 14.0f, y + 7.0f,
			x - 12.0f, y - 8.0f
		));
	}

	private static Shape regularPolygon(
		final float centerX,
		final float centerY,
		final float radius,
		final int vertices,
		final double rotation)
	{
		final Path2D.Float polygon = new Path2D.Float();
		for (int vertex = 0; vertex < vertices; vertex++)
		{
			final double angle = rotation
				+ vertex * Math.PI * 2.0 / vertices;
			final float x = centerX + (float) Math.cos(angle) * radius;
			final float y = centerY + (float) Math.sin(angle) * radius;
			if (vertex == 0)
			{
				polygon.moveTo(x, y);
			}
			else
			{
				polygon.lineTo(x, y);
			}
		}
		polygon.closePath();
		return polygon;
	}

	private static Shape facet(
		final float firstX,
		final float firstY,
		final float secondX,
		final float secondY,
		final float thirdX,
		final float thirdY)
	{
		final Path2D.Float facet = new Path2D.Float();
		facet.moveTo(firstX, firstY);
		facet.lineTo(secondX, secondY);
		facet.lineTo(thirdX, thirdY);
		facet.closePath();
		return facet;
	}

	private static float celestialX(final float travel)
	{
		return 16.0f + Math.max(0.0f, Math.min(1.0f, travel))
			* (WIDTH - 32.0f);
	}

	private static float celestialY(
		final float travel,
		final float horizon,
		final float arcHeight)
	{
		return horizon - (float) Math.sin(
			Math.PI * Math.max(0.0f, Math.min(1.0f, travel))
		) * arcHeight;
	}

	private static float horizonVisibility(final float travel)
	{
		return Math.min(
			1.0f,
			Math.max(0.0f, Math.min(travel, 1.0f - travel) * 20.0f)
		);
	}

	private static Color withAlpha(
		final Color color,
		final int alpha,
		final float visibility)
	{
		return new Color(
			color.getRed(),
			color.getGreen(),
			color.getBlue(),
			Math.max(0, Math.min(255, Math.round(alpha * visibility)))
		);
	}

	private static Color mixColor(
		final Color from,
		final Color to,
		final float amount)
	{
		final float safeAmount = Math.max(0.0f, Math.min(1.0f, amount));
		return new Color(
			Math.round(from.getRed()
				+ (to.getRed() - from.getRed()) * safeAmount),
			Math.round(from.getGreen()
				+ (to.getGreen() - from.getGreen()) * safeAmount),
			Math.round(from.getBlue()
				+ (to.getBlue() - from.getBlue()) * safeAmount)
		);
	}

	private void advanceFade()
	{
		if (fadeProgress() >= 1.0f)
		{
			fadeTimer.stop();
			clearPreviousPortrait();
		}
		repaint();
	}

	private float fadeProgress()
	{
		if (previousPortrait == null || portrait == null)
		{
			return 1.0f;
		}
		final long elapsedNanos = System.nanoTime() - fadeStartedAtNanos;
		return Math.min(
			1.0f,
			elapsedNanos / (FADE_DURATION_MS * 1_000_000.0f)
		);
	}

	private void clearPreviousPortrait()
	{
		previousPortrait = null;
		previousPortraitShadow = null;
	}

	private static void drawPortraitLayer(
		final Graphics2D graphics,
		final BufferedImage image,
		final BufferedImage shadow,
		final int x,
		final int y,
		final float alpha)
	{
		if (image == null || alpha <= 0.0f)
		{
			return;
		}

		if (shadow != null)
		{
			graphics.setComposite(
				AlphaComposite.SrcOver.derive(alpha * 0.42f)
			);
			graphics.drawImage(shadow, x, y + 3, null);
		}
		graphics.setComposite(AlphaComposite.SrcOver.derive(alpha));
		graphics.drawImage(image, x, y, null);
		graphics.setComposite(AlphaComposite.SrcOver);
	}

	private static boolean isMostlyDark(final BufferedImage image)
	{
		long weightedLuminance = 0;
		long totalAlpha = 0;
		for (int y = 0; y < image.getHeight(); y += 2)
		{
			for (int x = 0; x < image.getWidth(); x += 2)
			{
				final int argb = image.getRGB(x, y);
				final int alpha = argb >>> 24;
				if (alpha == 0)
				{
					continue;
				}
				final int red = (argb >>> 16) & 0xFF;
				final int green = (argb >>> 8) & 0xFF;
				final int blue = argb & 0xFF;
				final int luminance = (red * 54 + green * 183 + blue * 19) >> 8;
				weightedLuminance += (long) luminance * alpha;
				totalAlpha += alpha;
			}
		}
		return totalAlpha > 0 && weightedLuminance / totalAlpha < 112;
	}

	private static BufferedImage createShadow(final BufferedImage source)
	{
		final BufferedImage silhouette = new BufferedImage(
			source.getWidth(),
			source.getHeight(),
			BufferedImage.TYPE_INT_ARGB
		);

		for (int y = 0; y < source.getHeight(); y++)
		{
			for (int x = 0; x < source.getWidth(); x++)
			{
				final int alpha = source.getRGB(x, y) >>> 24;
				if (alpha > 0)
				{
					silhouette.setRGB(
						x,
						y,
						(Math.min(150, alpha) << 24)
					);
				}
			}
		}

		final float[] kernel =
		{
			1, 2, 3, 2, 1,
			2, 4, 6, 4, 2,
			3, 6, 9, 6, 3,
			2, 4, 6, 4, 2,
			1, 2, 3, 2, 1
		};

		float total = 0;
		for (final float value : kernel)
		{
			total += value;
		}
		for (int i = 0; i < kernel.length; i++)
		{
			kernel[i] /= total;
		}

		final ConvolveOp blur = new ConvolveOp(
			new Kernel(5, 5, kernel),
			ConvolveOp.EDGE_NO_OP,
			null
		);

		return blur.filter(silhouette, null);
	}

	private static final class TimePalette
	{
		private final Color skyTop;
		private final Color skyMiddle;
		private final Color skyBottom;
		private final Color glowCenter;
		private final Color glowMiddle;
		private final Color lowerHaze;
		private final Color placeholder;
		private final Color vignette;
		private final Color border;
		private final float glowX;
		private final float glowY;
		private final float glowRadius;

		private TimePalette(
			final Color skyTop,
			final Color skyMiddle,
			final Color skyBottom,
			final Color glowCenter,
			final Color glowMiddle,
			final Color lowerHaze,
			final Color placeholder,
			final Color vignette,
			final Color border,
			final float glowX,
			final float glowY,
			final float glowRadius)
		{
			this.skyTop = skyTop;
			this.skyMiddle = skyMiddle;
			this.skyBottom = skyBottom;
			this.glowCenter = glowCenter;
			this.glowMiddle = glowMiddle;
			this.lowerHaze = lowerHaze;
			this.placeholder = placeholder;
			this.vignette = vignette;
			this.border = border;
			this.glowX = glowX;
			this.glowY = glowY;
			this.glowRadius = glowRadius;
		}

		private static TimePalette forTime(final LocalTime time)
		{
			final int minutes = time.getHour() * 60
				+ time.getMinute();

			/*
			 * Eight authored lighting anchors provide more life than the former
			 * four-state sky while interpolation keeps every change gradual.
			 */
			if (minutes < 3 * 60 + 45 || minutes >= 22 * 60)
			{
				return night();
			}
			if (minutes < 4 * 60 + 45)
			{
				return blend(
					night(),
					preDawn(),
					progress(minutes, 3 * 60 + 45, 4 * 60 + 45)
				);
			}
			if (minutes < 6 * 60 + 15)
			{
				return blend(
					preDawn(),
					sunrise(),
					progress(minutes, 4 * 60 + 45, 6 * 60 + 15)
				);
			}
			if (minutes < 9 * 60)
			{
				return blend(
					sunrise(),
					morning(),
					progress(minutes, 6 * 60 + 15, 9 * 60)
				);
			}
			if (minutes < 12 * 60 + 30)
			{
				return blend(
					morning(),
					midday(),
					progress(minutes, 9 * 60, 12 * 60 + 30)
				);
			}
			if (minutes < 16 * 60)
			{
				return blend(
					midday(),
					afternoon(),
					progress(minutes, 12 * 60 + 30, 16 * 60)
				);
			}
			if (minutes < 18 * 60 + 30)
			{
				return blend(
					afternoon(),
					sunset(),
					progress(minutes, 16 * 60, 18 * 60 + 30)
				);
			}
			if (minutes < 20 * 60 + 30)
			{
				return blend(
					sunset(),
					dusk(),
					progress(minutes, 18 * 60 + 30, 20 * 60 + 30)
				);
			}
			return blend(
				dusk(),
				night(),
				progress(minutes, 20 * 60 + 30, 22 * 60)
			);
		}

		private static float progress(
			final int value,
			final int start,
			final int end)
		{
			return Math.max(
				0.0f,
				Math.min(1.0f, (value - start) / (float) (end - start))
			);
		}

		private static TimePalette blend(
			final TimePalette from,
			final TimePalette to,
			final float amount)
		{
			return new TimePalette(
				blend(from.skyTop, to.skyTop, amount),
				blend(from.skyMiddle, to.skyMiddle, amount),
				blend(from.skyBottom, to.skyBottom, amount),
				blend(from.glowCenter, to.glowCenter, amount),
				blend(from.glowMiddle, to.glowMiddle, amount),
				blend(from.lowerHaze, to.lowerHaze, amount),
				blend(from.placeholder, to.placeholder, amount),
				blend(from.vignette, to.vignette, amount),
				blend(from.border, to.border, amount),
				lerp(from.glowX, to.glowX, amount),
				lerp(from.glowY, to.glowY, amount),
				lerp(from.glowRadius, to.glowRadius, amount)
			);
		}

		private static Color blend(
			final Color from,
			final Color to,
			final float amount)
		{
			return new Color(
				Math.round(lerp(from.getRed(), to.getRed(), amount)),
				Math.round(lerp(from.getGreen(), to.getGreen(), amount)),
				Math.round(lerp(from.getBlue(), to.getBlue(), amount)),
				Math.round(lerp(from.getAlpha(), to.getAlpha(), amount))
			);
		}

		private static float lerp(
			final float from,
			final float to,
			final float amount)
		{
			return from + (to - from) * amount;
		}

		private static TimePalette preDawn()
		{
			return new TimePalette(
				new Color(49, 61, 103),
				new Color(57, 61, 98),
				new Color(31, 36, 64),
				new Color(176, 175, 216, 66),
				new Color(119, 112, 164, 18),
				new Color(135, 123, 163, 20),
				new Color(38, 43, 70, 184),
				new Color(4, 5, 15, 108),
				new Color(86, 91, 128),
				WIDTH * 0.24f,
				HEIGHT * 0.36f,
				73.0f
			);
		}

		private static TimePalette sunrise()
		{
			return new TimePalette(
				new Color(113, 126, 168),
				new Color(183, 119, 119),
				new Color(86, 72, 101),
				new Color(255, 203, 144, 102),
				new Color(235, 143, 111, 28),
				new Color(242, 178, 151, 27),
				new Color(65, 58, 82, 178),
				new Color(11, 7, 18, 91),
				new Color(142, 112, 132),
				WIDTH * 0.22f,
				HEIGHT * 0.40f,
				80.0f
			);
		}

		private static TimePalette morning()
		{
			return new TimePalette(
				new Color(111, 169, 203),
				new Color(82, 134, 168),
				new Color(48, 82, 107),
				new Color(241, 230, 190, 80),
				new Color(203, 214, 211, 20),
				new Color(204, 215, 211, 24),
				new Color(48, 68, 82, 172),
				new Color(5, 10, 16, 91),
				new Color(98, 128, 146),
				WIDTH * 0.36f,
				HEIGHT * 0.36f,
				74.0f
			);
		}

		private static TimePalette midday()
		{
			return new TimePalette(
				new Color(112, 176, 214),
				new Color(73, 133, 174),
				new Color(37, 72, 103),
				new Color(238, 246, 250, 78),
				new Color(193, 221, 238, 19),
				new Color(196, 218, 232, 25),
				new Color(43, 66, 84, 170),
				new Color(4, 10, 16, 90),
				new Color(91, 125, 151),
				WIDTH * 0.52f,
				HEIGHT * 0.32f,
				72.0f
			);
		}

		private static TimePalette afternoon()
		{
			return new TimePalette(
				new Color(105, 153, 196),
				new Color(85, 122, 158),
				new Color(48, 71, 99),
				new Color(247, 222, 176, 82),
				new Color(218, 181, 145, 22),
				new Color(220, 190, 163, 25),
				new Color(50, 61, 79, 172),
				new Color(7, 9, 16, 93),
				new Color(112, 117, 139),
				WIDTH * 0.68f,
				HEIGHT * 0.36f,
				75.0f
			);
		}

		private static TimePalette sunset()
		{
			return new TimePalette(
				new Color(91, 98, 151),
				new Color(180, 94, 103),
				new Color(78, 46, 69),
				new Color(255, 178, 105, 104),
				new Color(231, 108, 94, 30),
				new Color(244, 157, 124, 27),
				new Color(74, 48, 68, 176),
				new Color(16, 8, 18, 98),
				new Color(152, 100, 112),
				WIDTH * 0.78f,
				HEIGHT * 0.42f,
				80.0f
			);
		}

		private static TimePalette dusk()
		{
			return new TimePalette(
				new Color(55, 62, 105),
				new Color(81, 58, 91),
				new Color(34, 31, 55),
				new Color(190, 156, 190, 70),
				new Color(137, 94, 140, 20),
				new Color(145, 108, 139, 21),
				new Color(43, 38, 62, 185),
				new Color(4, 3, 12, 109),
				new Color(91, 76, 111),
				WIDTH * 0.80f,
				HEIGHT * 0.36f,
				74.0f
			);
		}

		private static TimePalette night()
		{
			return new TimePalette(
				new Color(29, 47, 79),
				new Color(20, 34, 59),
				new Color(10, 20, 37),
				new Color(167, 196, 231, 58),
				new Color(89, 123, 165, 16),
				new Color(103, 132, 168, 18),
				new Color(27, 39, 60, 190),
				new Color(0, 3, 9, 116),
				new Color(65, 88, 119),
				WIDTH * 0.65f,
				HEIGHT * 0.31f,
				70.0f
			);
		}
	}
}
