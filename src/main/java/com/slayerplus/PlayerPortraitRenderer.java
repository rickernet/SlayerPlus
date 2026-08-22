package com.slayerplus;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Rasterizer;

public final class PlayerPortraitRenderer
{
	private static final int CAPTURE_SIZE = 448;
	private static final int OUTPUT_SIZE = 140;
	/*
	 * The calibrated model is only about 88 source pixels tall. Rendering the
	 * final model at three times the calibrated size turns the old upscale into
	 * a genuine downsample, preserving armour detail and smoothing diagonals.
	 * RuneLite's drawOrtho zoom argument is an inverse projection distance, so
	 * rendering larger requires dividing that value rather than multiplying it.
	 */
	private static final int FINAL_RENDER_SCALE = 3;
	private static final int FINAL_CONTENT_PADDING = 4;
	private static final int DOWNSAMPLE_INTERMEDIATE_SCALE = 2;
	private static final int LOCKED_SCALE_TOP_SOURCE_ROWS = 32;
	private static final int CAPTURE_TOP_SOURCE_ROWS = 52;
	/*
	 * Twelve calibrated columns leave a small, fixed guard around equipment
	 * without making horizontal width dictate a noticeably smaller portrait.
	 */
	private static final int LOCKED_SIDE_SOURCE_COLUMNS = 12;
	private static final int SUPERSAMPLED_CAPTURE_TOP_MARGIN = 16;
	private static final int LOCKED_OUTPUT_UPWARD_SHIFT = 0;
	private static final int FRONT_YAW = 0;
	private static final int CLEAR_RGB = 0x010203;

	/*
	 * Applied only to model vertices above the detected neck. The torso stays
	 * front-facing. The portrait model is supplied without its weapon, so a
	 * scythe or other tall weapon cannot enter this calculation.
	 */
	private static final double HEAD_TURN_RADIANS =
		Math.toRadians(11.0);
	private static final int NECK_SCAN_BINS = 72;

	/*
	 * The original 448x448 capture could clip tall helmets and weapons before
	 * the portrait crop was applied. Final renders use a larger centered capture
	 * while retaining the exact saved v48 zoom and output scale.
	 */
	private static final int EXTENDED_CAPTURE_EXTRA = 448;
	/*
	 * Keep the calibrated source window at its original vertical position.
	 * Moving the window upward makes the model appear lower in the finished
	 * card; the supersampled capture margin already protects tall geometry.
	 */
	private static final int TOP_SOURCE_WINDOW_SHIFT = 0;
	/*
	 * The previous top-margin setting could only use pixels already inside the
	 * fixed crop. Extend the intermediate source above that crop so real model
	 * pixels are available to fill the otherwise-empty top of the portrait.
	 */
	private static final int TOP_SOURCE_EXTENSION_ROWS = 64;

	private static final int[] ZOOM_LEVELS =
	{
		120,
		145,
		170,
		200,
		230,
		260,
		300,
		340,
		380
	};

	private final Client client;

	/*
	 * The first successful portrait establishes the camera and crop.
	 * Every later equipment change reuses these exact values so hats and
	 * helmets cannot move or resize the portrait.
	 */
	private Framing lockedFraming;
	private ContentBounds lockedContentBounds;

	public PlayerPortraitRenderer(final Client client)
	{
		this.client = client;
	}

	public BufferedImage render(final Model inputModel)
	{
		return render(inputModel, inputModel, true);
	}

	public BufferedImage render(
		final Model inputModel,
		final boolean allowCalibration)
	{
		return render(
			inputModel,
			inputModel,
			allowCalibration
		);
	}

	public BufferedImage render(
		final Model displayInputModel,
		final Model bodyInputModel,
		final boolean allowCalibration)
	{
		if (displayInputModel == null || bodyInputModel == null)
		{
			return null;
		}

		final Model displayUnskewed =
			displayInputModel.getUnskewedModel();
		final Model displayModel = displayUnskewed != null
			? displayUnskewed
			: displayInputModel;

		final Model bodyUnskewed =
			bodyInputModel.getUnskewedModel();
		final Model bodyModel = bodyUnskewed != null
			? bodyUnskewed
			: bodyInputModel;

		final Rasterizer rasterizer = client.getRasterizer();
		if (rasterizer == null)
		{
			return null;
		}

		final int rasterWidth = rasterizer.getWidth();
		final int rasterHeight = rasterizer.getHeight();
		final int[] pixels = rasterizer.getPixels();

		if (pixels == null
			|| rasterWidth < CAPTURE_SIZE
			|| rasterHeight < CAPTURE_SIZE)
		{
			return null;
		}

		final int captureX =
			(rasterWidth - CAPTURE_SIZE) / 2;
		final int captureY =
			(rasterHeight - CAPTURE_SIZE) / 2;

		final int extendedCaptureSize = Math.max(
			CAPTURE_SIZE,
			Math.min(
				CAPTURE_SIZE + EXTENDED_CAPTURE_EXTRA,
				Math.min(rasterWidth, rasterHeight)
			)
		);
		final int extendedCaptureX =
			(rasterWidth - extendedCaptureSize) / 2;
		final int extendedCaptureY =
			(rasterHeight - extendedCaptureSize) / 2;
		final int extendedOffsetX =
			captureX - extendedCaptureX;
		final int extendedOffsetY =
			captureY - extendedCaptureY;

		final int[] savedPixels = copyRegion(
			pixels,
			rasterWidth,
			extendedCaptureX,
			extendedCaptureY,
			extendedCaptureSize,
			extendedCaptureSize
		);

		NeckTurn neckTurn = null;

		try
		{
			rasterizer.setDrawRegion(
				captureX,
				captureY,
				captureX + CAPTURE_SIZE,
				captureY + CAPTURE_SIZE
			);

			bodyModel.calculateBoundsCylinder();
			displayModel.calculateBoundsCylinder();

			/*
			 * Calibration always uses the weaponless body model. The full
			 * equipment model can never influence v48's saved framing.
			 */
			if (lockedFraming == null)
			{
				if (!allowCalibration)
				{
					return null;
				}

				lockedFraming = findInitialFraming(
					bodyModel,
					rasterizer,
					pixels,
					rasterWidth,
					captureX,
					captureY
				);
			}

			if (lockedFraming == null)
			{
				return null;
			}

			/*
			 * Establish the persistent v48 content rectangle using the original
			 * 448x448 calibration render. This happens only once.
			 */
			if (lockedContentBounds == null)
			{
				final BufferedImage calibrationCapture =
					renderCaptureSized(
						bodyModel,
						rasterizer,
						pixels,
						rasterWidth,
						captureX,
						captureY,
						CAPTURE_SIZE,
						lockedFraming
					);

				final BufferedImage calibrationPortrait =
					copyLockedCrop(
						calibrationCapture,
						lockedFraming,
						allowCalibration,
						0,
						0
					);

				if (calibrationPortrait == null)
				{
					return null;
				}
			}

			/*
			 * Shift the source window upward by the requested amount, but do
			 * not clamp it at zero. The larger capture supplies the pixels that
			 * used to be outside the 448x448 boundary.
			 */
			final int availableTopShift = Math.max(
				0,
				extendedOffsetY + lockedFraming.cropY
			);
			final int appliedTopShift = Math.min(
				TOP_SOURCE_WINDOW_SHIFT,
				availableTopShift
			);
			final Framing renderFraming =
				createExtendedFraming(
					lockedFraming,
					appliedTopShift
				);

			/*
			 * Render a forward-facing weaponless reference in the larger
			 * capture. It is used to isolate the equipped weapon.
			 */
			final int projectionZoom = Math.max(1, client.get3dZoom());
			final Framing supersampledFraming =
				createSupersampledFraming(
					lockedFraming,
					lockedContentBounds,
					projectionZoom
				);

			final BufferedImage bodyFrontCapture =
				renderCaptureSized(
					bodyModel,
					rasterizer,
					pixels,
					rasterWidth,
					extendedCaptureX,
					extendedCaptureY,
					extendedCaptureSize,
					supersampledFraming
				);

			/*
			 * Apply the genuine 3D neck swivel only to the weaponless body.
			 */
			neckTurn = applyTemporaryNeckTurn(bodyModel);

			final BufferedImage turnedBodyCapture =
				renderCaptureSized(
					bodyModel,
					rasterizer,
					pixels,
					rasterWidth,
					extendedCaptureX,
					extendedCaptureY,
					extendedCaptureSize,
					supersampledFraming
				);

			if (neckTurn != null)
			{
				neckTurn.restore();
				neckTurn = null;
			}

			/*
			 * Render the complete equipment model in the same larger capture.
			 * Tall hats and weapons now exist in the source image instead of
			 * being chopped by the old 448x448 boundary.
			 */
			final BufferedImage fullCapture =
				renderCaptureSized(
					displayModel,
					rasterizer,
					pixels,
					rasterWidth,
					extendedCaptureX,
					extendedCaptureY,
					extendedCaptureSize,
					supersampledFraming
				);

			final BufferedImage supersampledPortrait =
				renderSupersampledComposite(
				turnedBodyCapture,
				fullCapture,
				bodyFrontCapture,
				renderFraming,
				lockedFraming,
				lockedContentBounds,
				extendedCaptureSize,
				FINAL_RENDER_SCALE
			);
			if (hasSufficientVisiblePixels(supersampledPortrait))
			{
				return supersampledPortrait;
			}

			/*
			 * A portrait must never disappear because an unusual model exceeds the
			 * supersampled projection. Fall back to the proven calibrated render;
			 * this path is only used when the high-resolution output is effectively
			 * empty.
			 */
			final BufferedImage fallbackBodyFront =
				renderCaptureSized(
					bodyModel,
					rasterizer,
					pixels,
					rasterWidth,
					extendedCaptureX,
					extendedCaptureY,
					extendedCaptureSize,
					lockedFraming
				);
			neckTurn = applyTemporaryNeckTurn(bodyModel);
			final BufferedImage fallbackTurnedCapture =
				renderCaptureSized(
					bodyModel,
					rasterizer,
					pixels,
					rasterWidth,
					extendedCaptureX,
					extendedCaptureY,
					extendedCaptureSize,
					lockedFraming
				);
			if (neckTurn != null)
			{
				neckTurn.restore();
				neckTurn = null;
			}
			final BufferedImage fallbackTurnedPortrait =
				copyLockedCrop(
					fallbackTurnedCapture,
					renderFraming,
					false,
					extendedOffsetX,
					extendedOffsetY
				);
			final BufferedImage fallbackFull =
				renderCaptureSized(
					displayModel,
					rasterizer,
					pixels,
					rasterWidth,
					extendedCaptureX,
					extendedCaptureY,
					extendedCaptureSize,
					lockedFraming
				);
			return overlayEquipmentDifferenceFromCapture(
				fallbackTurnedPortrait,
				fallbackFull,
				fallbackBodyFront,
				renderFraming,
				lockedContentBounds,
				extendedCaptureSize,
				extendedOffsetX,
				extendedOffsetY
			);

		}
		finally
		{
			try
			{
				if (neckTurn != null)
				{
					neckTurn.restore();
				}
			}
			finally
			{
				try
				{
					restoreRegion(
						pixels,
						rasterWidth,
						extendedCaptureX,
						extendedCaptureY,
						extendedCaptureSize,
						extendedCaptureSize,
						savedPixels
					);
				}
				finally
				{
					/* Always restore the shared client rasterizer's clipping. */
					rasterizer.resetRasterClipping();
				}
			}
		}
	}

	private static BufferedImage renderCaptureSized(
		final Model model,
		final Rasterizer rasterizer,
		final int[] pixels,
		final int rasterWidth,
		final int captureX,
		final int captureY,
		final int captureSize,
		final Framing framing)
	{
		rasterizer.setDrawRegion(
			captureX,
			captureY,
			captureX + captureSize,
			captureY + captureSize
		);

		rasterizer.fillRectangle(
			captureX,
			captureY,
			captureSize,
			captureSize,
			CLEAR_RGB
		);

		model.drawOrtho(
			0,
			0,
			FRONT_YAW,
			0,
			0,
			framing.verticalOffset,
			0,
			framing.zoom
		);

		return captureRegion(
			pixels,
			rasterWidth,
			captureX,
			captureY,
			captureSize,
			captureSize
		);
	}

	private static Framing createExtendedFraming(
		final Framing framing,
		final int topShift)
	{
		return new Framing(
			framing.zoom,
			framing.verticalOffset,
			framing.cropX,
			framing.cropY - topShift,
			framing.cropWidth,
			framing.cropHeight
		);
	}

	private static Framing createSupersampledFraming(
		final Framing framing,
		final ContentBounds contentBounds,
		final int projectionZoom)
	{
		final double contentCenterY = framing.cropY
			+ (contentBounds.minY + contentBounds.maxY + 1) / 2.0;
		return new Framing(
			supersampledZoom(framing.zoom),
			supersampledVerticalOffset(
				framing.verticalOffset,
				framing.zoom,
				contentCenterY,
				projectionZoom
			) + supersampledCaptureMarginOffset(
				framing.zoom,
				projectionZoom
			),
			framing.cropX,
			framing.cropY,
			framing.cropWidth,
			framing.cropHeight
		);
	}

	static int supersampledCaptureMarginOffset(
		final int calibratedZoom,
		final int projectionZoom)
	{
		if (projectionZoom <= 0)
		{
			return 0;
		}
		return (int) Math.round(
			(double) SUPERSAMPLED_CAPTURE_TOP_MARGIN
				* supersampledZoom(calibratedZoom)
				/ projectionZoom
		);
	}

	static int supersampledVerticalOffset(
		final int calibratedVerticalOffset,
		final int calibratedZoom,
		final double calibratedContentCenterY,
		final int projectionZoom)
	{
		if (projectionZoom <= 0)
		{
			return calibratedVerticalOffset;
		}
		final double centerCorrection =
			CAPTURE_SIZE / 2.0 - calibratedContentCenterY;
		return calibratedVerticalOffset + (int) Math.round(
			centerCorrection * calibratedZoom / projectionZoom
		);
	}

	static int supersampledZoom(final int calibratedZoom)
	{
		return Math.max(
			1,
			(int) Math.round(
				(double) calibratedZoom / FINAL_RENDER_SCALE
			)
		);
	}

	static double projectCalibrationCoordinate(
		final double calibrationCoordinate,
		final int captureSize,
		final int renderScale)
	{
		return captureSize / 2.0
			+ (calibrationCoordinate - CAPTURE_SIZE / 2.0)
				* renderScale;
	}

	static double projectCalibrationCoordinate(
		final double calibrationCoordinate,
		final double calibrationAnchor,
		final int captureSize,
		final int renderScale)
	{
		return captureSize / 2.0
			+ (calibrationCoordinate - calibrationAnchor)
				* renderScale;
	}

	static boolean hasSufficientVisiblePixels(final BufferedImage image)
	{
		if (image == null)
		{
			return false;
		}
		final int minimumVisiblePixels = Math.max(
			32,
			image.getWidth() * image.getHeight() / 100
		);
		int visiblePixels = 0;
		int minX = image.getWidth();
		int minY = image.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) > 16)
				{
					visiblePixels++;
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		return visiblePixels >= minimumVisiblePixels
			&& maxX - minX + 1 >= image.getWidth() / 3
			&& maxY - minY + 1 >= image.getHeight() / 2;
	}

	private static BufferedImage renderSupersampledComposite(
		final BufferedImage turnedBodyCapture,
		final BufferedImage fullCapture,
		final BufferedImage bodyFrontCapture,
		final Framing framing,
		final Framing calibrationFraming,
		final ContentBounds contentBounds,
		final int captureSize,
		final int renderScale)
	{
		if (turnedBodyCapture == null
			|| fullCapture == null
			|| bodyFrontCapture == null
			|| framing == null
			|| calibrationFraming == null
			|| contentBounds == null
			|| captureSize <= 0
			|| renderScale <= 1
			|| turnedBodyCapture.getWidth() != captureSize
			|| turnedBodyCapture.getHeight() != captureSize
			|| fullCapture.getWidth() != captureSize
			|| fullCapture.getHeight() != captureSize
			|| bodyFrontCapture.getWidth() != captureSize
			|| bodyFrontCapture.getHeight() != captureSize)
		{
			return null;
		}

		final boolean[] equipmentMask = new boolean[
			captureSize * captureSize
		];
		for (int y = 0; y < captureSize; y++)
		{
			for (int x = 0; x < captureSize; x++)
			{
				final int index = y * captureSize + x;
				equipmentMask[index] =
					isCaptureEquipmentDifference(
						fullCapture.getRGB(x, y),
						bodyFrontCapture.getRGB(x, y)
					);
			}
		}
		final boolean[] expandedMask = expandMask(
			equipmentMask,
			captureSize
		);
		final int visibleWidth =
			contentBounds.maxX - contentBounds.minX + 1;
		final int visibleHeight =
			contentBounds.maxY - contentBounds.minY + 1;
		final int availableTopSourceRows = CAPTURE_TOP_SOURCE_ROWS;
		final int extendedVisibleHeight =
			visibleHeight + availableTopSourceRows;

		/*
		 * Project the locked 448px calibration rectangle into the centered,
		 * high-zoom capture. Keeping this transform explicit prevents equipment
		 * changes from altering the player's scale or placement.
		 */
		final double baseLeft =
			framing.cropX
				+ contentBounds.minX
				- LOCKED_SIDE_SOURCE_COLUMNS;
		final double baseTop = framing.cropY
			+ contentBounds.minY
			- availableTopSourceRows;
		final double baseRight = framing.cropX
			+ contentBounds.minX
			+ visibleWidth
			+ LOCKED_SIDE_SOURCE_COLUMNS;
		final double baseBottom = baseTop + extendedVisibleHeight;
		final double calibrationVerticalAnchor =
			calibrationFraming.cropY
				+ (contentBounds.minY + contentBounds.maxY + 1) / 2.0;

		final int sourceLeft = (int) Math.floor(
			projectCalibrationCoordinate(
				baseLeft,
				captureSize,
				renderScale
			)
		);
		final int sourceTop = (int) Math.floor(
			projectCalibrationCoordinate(
				baseTop,
				calibrationVerticalAnchor,
				captureSize,
				renderScale
			) + SUPERSAMPLED_CAPTURE_TOP_MARGIN
		);
		final int sourceRight = (int) Math.ceil(
			projectCalibrationCoordinate(
				baseRight,
				captureSize,
				renderScale
			)
		);
		final int sourceBottom = (int) Math.ceil(
			projectCalibrationCoordinate(
				baseBottom,
				calibrationVerticalAnchor,
				captureSize,
				renderScale
			) + SUPERSAMPLED_CAPTURE_TOP_MARGIN
		);

		final int sourceWidth = Math.max(1, sourceRight - sourceLeft);
		final int sourceHeight = Math.max(1, sourceBottom - sourceTop);
		final BufferedImage compositeSource = new BufferedImage(
			sourceWidth,
			sourceHeight,
			BufferedImage.TYPE_INT_ARGB_PRE
		);
		int firstOpaqueRow = sourceHeight;
		int lastOpaqueRow = -1;

		for (int y = 0; y < sourceHeight; y++)
		{
			final int captureY = sourceTop + y;
			if (captureY < 0 || captureY >= captureSize)
			{
				continue;
			}
			for (int x = 0; x < sourceWidth; x++)
			{
				final int captureX = sourceLeft + x;
				if (captureX < 0 || captureX >= captureSize)
				{
					continue;
				}

				final int index = captureY * captureSize + captureX;
				final int full = fullCapture.getRGB(captureX, captureY);
				final int body = turnedBodyCapture.getRGB(captureX, captureY);
				final int selected = expandedMask[index]
					&& (full & 0x00FFFFFF) != CLEAR_RGB
					? full
					: body;
				final int rgb = selected & 0x00FFFFFF;
				if (rgb != CLEAR_RGB)
				{
					compositeSource.setRGB(
						x,
						y,
						0xFF000000 | rgb
					);
					firstOpaqueRow = Math.min(firstOpaqueRow, y);
					lastOpaqueRow = Math.max(lastOpaqueRow, y);
				}
			}
		}
		if (lastOpaqueRow < firstOpaqueRow)
		{
			return null;
		}

		/*
		 * Keep the final frame tied to the locked calibration rectangle rather
		 * than the current equipment silhouette.  Trimming to the outermost
		 * opaque row makes the body change scale and position whenever a taller
		 * helmet, weapon, or cape is equipped.  The fixed source rectangle keeps
		 * the body baseline stable while the reserved upper rows protect tall
		 * equipment.
		 */
		final int availableWidth =
			OUTPUT_SIZE - FINAL_CONTENT_PADDING;
		/*
		 * Capture additional upper geometry without letting it change the
		 * calibrated body zoom. The extra rows extend above the destination;
		 * bottom anchoring leaves the body exactly where it was and uses the
		 * previously empty top pixels for tall equipment.
		 */
		final int lockedScaleSourceHeight = lockedScaleSourceHeight(
			sourceHeight,
			renderScale
		);
		final double outputScale = Math.min(
			(double) availableWidth / sourceWidth,
			(double) OUTPUT_SIZE / lockedScaleSourceHeight
		);
		final int drawWidth = Math.max(
			1,
			(int) Math.round(sourceWidth * outputScale)
		);
		final int drawHeight = Math.max(
			1,
			(int) Math.round(sourceHeight * outputScale)
		);
		final int targetX = (OUTPUT_SIZE - drawWidth) / 2;
		final int targetY = lockedOutputTargetY(drawHeight);

		/*
		 * Reduce in two stages. A single 3x-to-1x bicubic operation undersamples
		 * thin armour and weapon edges; the 2x intermediate preserves those
		 * features and gives the final pass a well-filtered source.
		 */
		final BufferedImage intermediate = resamplePremultiplied(
			compositeSource,
			drawWidth * DOWNSAMPLE_INTERMEDIATE_SCALE,
			drawHeight * DOWNSAMPLE_INTERMEDIATE_SCALE
		);
		final BufferedImage refinedPortrait = resamplePremultiplied(
			intermediate,
			drawWidth,
			drawHeight
		);
		final BufferedImage clarifiedPortrait =
			applyPortraitFinish(refinedPortrait);

		final BufferedImage output = new BufferedImage(
			OUTPUT_SIZE,
			OUTPUT_SIZE,
			BufferedImage.TYPE_INT_ARGB_PRE
		);
		final Graphics2D graphics = output.createGraphics();
		graphics.setRenderingHint(
			RenderingHints.KEY_ALPHA_INTERPOLATION,
			RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
		);
		graphics.setRenderingHint(
			RenderingHints.KEY_RENDERING,
			RenderingHints.VALUE_RENDER_QUALITY
		);
		graphics.drawImage(
			clarifiedPortrait,
			targetX,
			targetY,
			null
		);
		graphics.dispose();

		return output;
	}

	static int lockedScaleSourceHeight(
		final int capturedSourceHeight,
		final int renderScale)
	{
		return Math.max(
			1,
			capturedSourceHeight
				- (CAPTURE_TOP_SOURCE_ROWS
					- LOCKED_SCALE_TOP_SOURCE_ROWS)
					* Math.max(1, renderScale)
		);
	}

	static int lockedOutputTargetY(final int drawHeight)
	{
		return OUTPUT_SIZE
			- Math.max(1, drawHeight)
			- LOCKED_OUTPUT_UPWARD_SHIFT;
	}

	private static BufferedImage applyPortraitFinish(
		final BufferedImage source)
	{
		/*
		 * Three-times sampling already preserves fine geometry. Apply clarity
		 * only where the pixel and all direct neighbours are solid model pixels;
		 * filtering transparent boundary pixels produces pale or fuzzy halos.
		 */
		final BufferedImage clarified = applyInteriorClarity(source);
		final int[] bounds = portraitOpaqueBounds(clarified);
		final BufferedImage finished = new BufferedImage(
			clarified.getWidth(),
			clarified.getHeight(),
			BufferedImage.TYPE_INT_ARGB_PRE
		);

		/*
		 * Restore a small amount of contrast and colour separation lost while
		 * downsampling. A restrained upper-left light and interior-only local
		 * relief give armour facets depth without changing model geometry or
		 * drawing an outline around the silhouette.
		 */
		for (int y = 0; y < clarified.getHeight(); y++)
		{
			for (int x = 0; x < clarified.getWidth(); x++)
			{
				final int argb = clarified.getRGB(x, y);
				final int alpha = argb >>> 24;
				if (alpha == 0)
				{
					continue;
				}
				final double sourceRed = (argb >>> 16) & 0xFF;
				final double sourceGreen = (argb >>> 8) & 0xFF;
				final double sourceBlue = argb & 0xFF;
				final double luminance =
					portraitLuminance(sourceRed, sourceGreen, sourceBlue);
				final double light = alpha >= 160
					? portraitDirectionalLight(x, y, bounds)
						+ portraitLocalRelief(clarified, x, y, luminance)
					: 0.0;
				final double red = shadedPortraitChannel(sourceRed, light);
				final double green = shadedPortraitChannel(sourceGreen, light);
				final double blue = shadedPortraitChannel(sourceBlue, light);
				final double maximum = Math.max(
					red,
					Math.max(green, blue)
				);
				final double minimum = Math.min(
					red,
					Math.min(green, blue)
				);
				/*
				 * Boost subdued materials more than colours that are already
				 * vivid.  This preserves recognizable item accents and avoids
				 * turning saturated reds, greens, or gems into neon blocks.
				 */
				final double chroma = (maximum - minimum) / 255.0;
				final double vibrance = 1.10 - 0.04 * chroma;
				final int finishedRed = portraitChannel(
					luminance + (red - luminance) * vibrance
				);
				final int finishedGreen = portraitChannel(
					luminance + (green - luminance) * vibrance
				);
				final int finishedBlue = portraitChannel(
					luminance + (blue - luminance) * vibrance
				);
				finished.setRGB(
					x,
					y,
					(alpha << 24)
						| (finishedRed << 16)
						| (finishedGreen << 8)
						| finishedBlue
				);
			}
		}
		return finished;
	}

	private static BufferedImage applyInteriorClarity(
		final BufferedImage source)
	{
		final BufferedImage output = new BufferedImage(
			source.getWidth(),
			source.getHeight(),
			BufferedImage.TYPE_INT_ARGB_PRE
		);
		final Graphics2D graphics = output.createGraphics();
		graphics.drawImage(source, 0, 0, null);
		graphics.dispose();

		for (int y = 1; y < source.getHeight() - 1; y++)
		{
			for (int x = 1; x < source.getWidth() - 1; x++)
			{
				final int center = source.getRGB(x, y);
				if (!isSolidPortraitPixel(center)
					|| !isSolidPortraitPixel(source.getRGB(x - 1, y))
					|| !isSolidPortraitPixel(source.getRGB(x + 1, y))
					|| !isSolidPortraitPixel(source.getRGB(x, y - 1))
					|| !isSolidPortraitPixel(source.getRGB(x, y + 1)))
				{
					continue;
				}

				final int alpha = center >>> 24;
				final int red = interiorClarityChannel(source, x, y, 16);
				final int green = interiorClarityChannel(source, x, y, 8);
				final int blue = interiorClarityChannel(source, x, y, 0);
				output.setRGB(
					x,
					y,
					(alpha << 24) | (red << 16) | (green << 8) | blue
				);
			}
		}
		return output;
	}

	private static boolean isSolidPortraitPixel(final int argb)
	{
		return (argb >>> 24) >= 224;
	}

	private static int interiorClarityChannel(
		final BufferedImage source,
		final int x,
		final int y,
		final int shift)
	{
		final double center = (source.getRGB(x, y) >>> shift) & 0xFF;
		final double neighbours =
			((source.getRGB(x - 1, y) >>> shift) & 0xFF)
				+ ((source.getRGB(x + 1, y) >>> shift) & 0xFF)
				+ ((source.getRGB(x, y - 1) >>> shift) & 0xFF)
				+ ((source.getRGB(x, y + 1) >>> shift) & 0xFF);
		return clampColor(center * 1.04 - neighbours * 0.01);
	}

	private static int[] portraitOpaqueBounds(final BufferedImage image)
	{
		int minX = image.getWidth();
		int minY = image.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) < 32)
				{
					continue;
				}
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}
		}
		if (maxX < minX || maxY < minY)
		{
			return new int[] {0, 0, image.getWidth() - 1, image.getHeight() - 1};
		}
		return new int[] {minX, minY, maxX, maxY};
	}

	static double portraitDirectionalLight(
		final int x,
		final int y,
		final int[] bounds)
	{
		final double width = Math.max(1.0, bounds[2] - bounds[0]);
		final double height = Math.max(1.0, bounds[3] - bounds[1]);
		final double normalizedX = (x - bounds[0]) / width;
		final double normalizedY = (y - bounds[1]) / height;
		return (0.5 - normalizedX) * 3.0
			+ (0.5 - normalizedY) * 7.0;
	}

	private static double portraitLocalRelief(
		final BufferedImage image,
		final int x,
		final int y,
		final double centerLuminance)
	{
		if (x <= 0 || y <= 0
			|| x >= image.getWidth() - 1
			|| y >= image.getHeight() - 1
			|| !isSolidPortraitPixel(image.getRGB(x - 1, y))
			|| !isSolidPortraitPixel(image.getRGB(x + 1, y))
			|| !isSolidPortraitPixel(image.getRGB(x, y - 1))
			|| !isSolidPortraitPixel(image.getRGB(x, y + 1)))
		{
			return 0.0;
		}
		final double neighbourLuminance = (
			portraitLuminance(image.getRGB(x - 1, y))
				+ portraitLuminance(image.getRGB(x + 1, y))
				+ portraitLuminance(image.getRGB(x, y - 1))
				+ portraitLuminance(image.getRGB(x, y + 1))
		) / 4.0;
		return Math.max(
			-3.5,
			Math.min(3.5, (centerLuminance - neighbourLuminance) * 0.18)
		);
	}

	private static double portraitLuminance(final int argb)
	{
		return portraitLuminance(
			(argb >>> 16) & 0xFF,
			(argb >>> 8) & 0xFF,
			argb & 0xFF
		);
	}

	private static double portraitLuminance(
		final double red,
		final double green,
		final double blue)
	{
		return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
	}

	private static double shadedPortraitChannel(
		final double channel,
		final double light)
	{
		final double highlightProtection = light > 0.0
			? 1.0 - channel / 510.0
			: 1.0;
		return channel + light * highlightProtection;
	}

	private static int clampColor(final double value)
	{
		return Math.max(0, Math.min(255, (int) Math.round(value)));
	}

	private static int portraitChannel(final double value)
	{
		final double contrasted = 127.5 + (value - 127.5) * 1.04;
		final double normalized = Math.max(
			0.0,
			Math.min(1.0, contrasted / 255.0)
		);
		/* A slight gamma lift reveals armour midtones without washing blacks. */
		final double lifted = Math.pow(normalized, 0.97) * 255.0;
		return Math.max(0, Math.min(255, (int) Math.round(lifted)));
	}

	private static BufferedImage resamplePremultiplied(
		final BufferedImage source,
		final int width,
		final int height)
	{
		final BufferedImage output = new BufferedImage(
			Math.max(1, width),
			Math.max(1, height),
			BufferedImage.TYPE_INT_ARGB_PRE
		);
		final Graphics2D graphics = output.createGraphics();
		graphics.setRenderingHint(
			RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BICUBIC
		);
		graphics.setRenderingHint(
			RenderingHints.KEY_ALPHA_INTERPOLATION,
			RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
		);
		graphics.setRenderingHint(
			RenderingHints.KEY_RENDERING,
			RenderingHints.VALUE_RENDER_QUALITY
		);
		graphics.drawImage(
			source,
			0,
			0,
			output.getWidth(),
			output.getHeight(),
			null
		);
		graphics.dispose();
		return output;
	}


	private static BufferedImage overlayEquipmentDifferenceFromCapture(
		final BufferedImage turnedBody,
		final BufferedImage fullCapture,
		final BufferedImage bodyCapture,
		final Framing framing,
		final ContentBounds contentBounds,
		final int captureSize,
		final int captureOffsetX,
		final int captureOffsetY)
	{
		if (turnedBody == null
			|| fullCapture == null
			|| bodyCapture == null
			|| framing == null
			|| contentBounds == null
			|| captureSize < CAPTURE_SIZE
			|| fullCapture.getWidth() != captureSize
			|| fullCapture.getHeight() != captureSize
			|| bodyCapture.getWidth() != captureSize
			|| bodyCapture.getHeight() != captureSize)
		{
			return turnedBody;
		}

		final int visibleWidth =
			contentBounds.maxX - contentBounds.minX + 1;
		final int visibleHeight =
			contentBounds.maxY - contentBounds.minY + 1;

		final int availableWidth = OUTPUT_SIZE - 4;
		final int availableHeight = OUTPUT_SIZE - 3;

		final double scale = Math.min(
			(double) availableWidth / visibleWidth,
			(double) availableHeight / visibleHeight
		);

		final int drawWidth = Math.max(
			1,
			(int) Math.round(visibleWidth * scale)
		);
		final int drawHeight = Math.max(
			1,
			(int) Math.round(visibleHeight * scale)
		);

		final int targetX =
			(OUTPUT_SIZE - drawWidth) / 2;
		final int targetY =
			OUTPUT_SIZE - drawHeight;

		final int absoluteSourceX =
			framing.cropX + contentBounds.minX;
		final int absoluteSourceY =
			framing.cropY + contentBounds.minY;

		final boolean[] equipmentMask =
			new boolean[captureSize * captureSize];

		for (int y = 0; y < captureSize; y++)
		{
			for (int x = 0; x < captureSize; x++)
			{
				final int full =
					fullCapture.getRGB(x, y);
				final int body =
					bodyCapture.getRGB(x, y);

				equipmentMask[y * captureSize + x] =
					isCaptureEquipmentDifference(full, body);
			}
		}

		/*
		 * Preserve the anti-aliased edge of the weapon.
		 */
		final boolean[] expandedMask = expandMask(
			equipmentMask,
			captureSize
		);

		final BufferedImage output = new BufferedImage(
			OUTPUT_SIZE,
			OUTPUT_SIZE,
			BufferedImage.TYPE_INT_ARGB
		);

		final Graphics2D graphics = output.createGraphics();
		graphics.drawImage(turnedBody, 0, 0, null);
		graphics.dispose();

		/*
		 * Project each equipment pixel through the exact same source-to-output
		 * transform used by v48. Pixels outside the portrait are clipped, but
		 * they never change the character scale.
		 */
		for (int sourceY = 0;
			sourceY < captureSize;
			sourceY++)
		{
			for (int sourceX = 0;
				sourceX < captureSize;
				sourceX++)
			{
				if (!expandedMask[
					sourceY * captureSize + sourceX
				])
				{
					continue;
				}

				final int equipmentPixel =
					fullCapture.getRGB(sourceX, sourceY);
				final int equipmentRgb =
					equipmentPixel & 0x00FFFFFF;

				if (equipmentRgb == CLEAR_RGB)
				{
					continue;
				}

				final double bodySourceX =
					sourceX - captureOffsetX;
				final double bodySourceY =
					sourceY - captureOffsetY;

				final double mappedLeft =
					targetX
						+ (bodySourceX - absoluteSourceX)
							* scale;
				final double mappedTop =
					targetY
						+ (bodySourceY - absoluteSourceY)
							* scale;
				final double mappedRight =
					targetX
						+ (bodySourceX + 1 - absoluteSourceX)
							* scale;
				final double mappedBottom =
					targetY
						+ (bodySourceY + 1 - absoluteSourceY)
							* scale;

				final int destinationMinX =
					(int) Math.floor(mappedLeft);
				final int destinationMinY =
					(int) Math.floor(mappedTop);
				final int destinationMaxX =
					(int) Math.ceil(mappedRight) - 1;
				final int destinationMaxY =
					(int) Math.ceil(mappedBottom) - 1;

				for (int destinationY =
						destinationMinY;
					destinationY <= destinationMaxY;
					destinationY++)
				{
					if (destinationY < 0
						|| destinationY >= OUTPUT_SIZE)
					{
						continue;
					}

					for (int destinationX =
							destinationMinX;
						destinationX <= destinationMaxX;
						destinationX++)
					{
						if (destinationX < 0
							|| destinationX >= OUTPUT_SIZE)
						{
							continue;
						}

						output.setRGB(
							destinationX,
							destinationY,
							alphaComposite(
								output.getRGB(
									destinationX,
									destinationY
								),
								0xFF000000 | equipmentRgb
							)
						);
					}
				}
			}
		}

		return output;
	}

	static boolean[] expandMask(
		final boolean[] source,
		final int width)
	{
		if (source == null || width <= 0 || source.length != width * width)
		{
			return new boolean[0];
		}

		final boolean[] expanded = source.clone();
		for (int y = 0; y < width; y++)
		{
			for (int x = 0; x < width; x++)
			{
				if (!source[y * width + x])
				{
					continue;
				}
				for (int offsetY = -1; offsetY <= 1; offsetY++)
				{
					final int expandedY = y + offsetY;
					if (expandedY < 0 || expandedY >= width)
					{
						continue;
					}
					for (int offsetX = -1; offsetX <= 1; offsetX++)
					{
						final int expandedX = x + offsetX;
						if (expandedX >= 0 && expandedX < width)
						{
							expanded[expandedY * width + expandedX] = true;
						}
					}
				}
			}
		}
		return expanded;
	}


	private static boolean isCaptureEquipmentDifference(
		final int full,
		final int body)
	{
		final int fullRgb = full & 0x00FFFFFF;
		final int bodyRgb = body & 0x00FFFFFF;

		final boolean fullVisible =
			fullRgb != CLEAR_RGB;
		final boolean bodyVisible =
			bodyRgb != CLEAR_RGB;

		if (!fullVisible)
		{
			return false;
		}

		if (!bodyVisible)
		{
			return true;
		}

		final int redDifference = Math.abs(
			((fullRgb >>> 16) & 0xFF)
				- ((bodyRgb >>> 16) & 0xFF)
		);
		final int greenDifference = Math.abs(
			((fullRgb >>> 8) & 0xFF)
				- ((bodyRgb >>> 8) & 0xFF)
		);
		final int blueDifference = Math.abs(
			(fullRgb & 0xFF) - (bodyRgb & 0xFF)
		);

		return redDifference
				+ greenDifference
				+ blueDifference
			> 48;
	}


	private static int alphaComposite(
		final int background,
		final int foreground)
	{
		final int foregroundAlpha =
			(foreground >>> 24) & 0xFF;

		if (foregroundAlpha >= 255)
		{
			return foreground;
		}

		if (foregroundAlpha <= 0)
		{
			return background;
		}

		final int backgroundAlpha =
			(background >>> 24) & 0xFF;
		final double foregroundWeight =
			foregroundAlpha / 255.0;
		final double backgroundWeight =
			(backgroundAlpha / 255.0)
				* (1.0 - foregroundWeight);
		final double outputAlpha =
			foregroundWeight + backgroundWeight;

		if (outputAlpha <= 0.0)
		{
			return 0;
		}

		final int red = (int) Math.round(
			(
				((foreground >>> 16) & 0xFF)
					* foregroundWeight
				+ ((background >>> 16) & 0xFF)
					* backgroundWeight
			) / outputAlpha
		);
		final int green = (int) Math.round(
			(
				((foreground >>> 8) & 0xFF)
					* foregroundWeight
				+ ((background >>> 8) & 0xFF)
					* backgroundWeight
			) / outputAlpha
		);
		final int blue = (int) Math.round(
			(
				(foreground & 0xFF)
					* foregroundWeight
				+ (background & 0xFF)
					* backgroundWeight
			) / outputAlpha
		);
		final int alpha = (int) Math.round(
			outputAlpha * 255.0
		);

		return (alpha << 24)
			| (red << 16)
			| (green << 8)
			| blue;
	}

	public void resetFraming()
	{
		lockedFraming = null;
		lockedContentBounds = null;
	}


	public boolean hasCalibration()
	{
		return lockedFraming != null
			&& lockedContentBounds != null;
	}

	public String exportCalibration()
	{
		if (!hasCalibration())
		{
			return null;
		}

		return "v1"
			+ "," + lockedFraming.zoom
			+ "," + lockedFraming.verticalOffset
			+ "," + lockedFraming.cropX
			+ "," + lockedFraming.cropY
			+ "," + lockedFraming.cropWidth
			+ "," + lockedFraming.cropHeight
			+ "," + lockedContentBounds.minX
			+ "," + lockedContentBounds.minY
			+ "," + lockedContentBounds.maxX
			+ "," + lockedContentBounds.maxY;
	}

	public boolean loadCalibration(final String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return false;
		}

		final String[] parts = value.split(",");
		if (parts.length != 11 || !"v1".equals(parts[0]))
		{
			return false;
		}

		try
		{
			final int zoom = Integer.parseInt(parts[1]);
			final int verticalOffset = Integer.parseInt(parts[2]);
			final int cropX = Integer.parseInt(parts[3]);
			final int cropY = Integer.parseInt(parts[4]);
			final int cropWidth = Integer.parseInt(parts[5]);
			final int cropHeight = Integer.parseInt(parts[6]);
			final int minX = Integer.parseInt(parts[7]);
			final int minY = Integer.parseInt(parts[8]);
			final int maxX = Integer.parseInt(parts[9]);
			final int maxY = Integer.parseInt(parts[10]);

			if (zoom <= 0
				|| cropWidth <= 0
				|| cropHeight <= 0
				|| cropX < 0
				|| cropY < 0
				|| cropX + cropWidth > CAPTURE_SIZE
				|| cropY + cropHeight > CAPTURE_SIZE
				|| minX < 0
				|| minY < 0
				|| maxX < minX
				|| maxY < minY
				|| maxX >= cropWidth
				|| maxY >= cropHeight)
			{
				return false;
			}

			lockedFraming = new Framing(
				zoom,
				verticalOffset,
				cropX,
				cropY,
				cropWidth,
				cropHeight
			);
			lockedContentBounds = new ContentBounds(
				minX,
				minY,
				maxX,
				maxY
			);
			return true;
		}
		catch (final NumberFormatException exception)
		{
			return false;
		}
	}

	private static NeckTurn applyTemporaryNeckTurn(
		final Model model)
	{
		final int vertexCount = model.getVerticesCount();
		final float[] verticesX = model.getVerticesX();
		final float[] verticesY = model.getVerticesY();
		final float[] verticesZ = model.getVerticesZ();

		if (vertexCount < 12
			|| verticesX == null
			|| verticesY == null
			|| verticesZ == null
			|| verticesX.length < vertexCount
			|| verticesY.length < vertexCount
			|| verticesZ.length < vertexCount)
		{
			return null;
		}

		float minimumY = Float.POSITIVE_INFINITY;
		float maximumY = Float.NEGATIVE_INFINITY;
		float minimumX = Float.POSITIVE_INFINITY;
		float maximumX = Float.NEGATIVE_INFINITY;
		float minimumZ = Float.POSITIVE_INFINITY;
		float maximumZ = Float.NEGATIVE_INFINITY;

		for (int vertex = 0; vertex < vertexCount; vertex++)
		{
			minimumY = Math.min(minimumY, verticesY[vertex]);
			maximumY = Math.max(maximumY, verticesY[vertex]);
			minimumX = Math.min(minimumX, verticesX[vertex]);
			maximumX = Math.max(maximumX, verticesX[vertex]);
			minimumZ = Math.min(minimumZ, verticesZ[vertex]);
			maximumZ = Math.max(maximumZ, verticesZ[vertex]);
		}

		final float modelHeight = maximumY - minimumY;
		if (modelHeight < 20.0f)
		{
			return null;
		}

		final float neckY = findNeckY(
			verticesX,
			verticesY,
			verticesZ,
			vertexCount,
			minimumY,
			maximumY
		);

		final float[] pivot = findNeckPivot(
			verticesX,
			verticesY,
			verticesZ,
			vertexCount,
			neckY,
			modelHeight,
			minimumX,
			maximumX,
			minimumZ,
			maximumZ
		);

		final float[] originalX = Arrays.copyOf(
			verticesX,
			vertexCount
		);
		final float[] originalY = Arrays.copyOf(
			verticesY,
			vertexCount
		);
		final float[] originalZ = Arrays.copyOf(
			verticesZ,
			vertexCount
		);

		final double direction =
			ThreadLocalRandom.current().nextBoolean() ? 1.0 : -1.0;

		/*
		 * Vertices above the upper transition line receive the complete turn.
		 * A short smooth transition through the neck prevents the head from
		 * separating from the torso, while shoulder vertices remain untouched.
		 */
		final float fullTurnY = neckY - Math.max(
			3.0f,
			modelHeight / 45.0f
		);
		final float stationaryY = neckY + Math.max(
			7.0f,
			modelHeight / 18.0f
		);
		final float transitionHeight = Math.max(
			1.0f,
			stationaryY - fullTurnY
		);

		for (int vertex = 0; vertex < vertexCount; vertex++)
		{
			final float y = originalY[vertex];

			final double weight;
			if (y <= fullTurnY)
			{
				weight = 1.0;
			}
			else if (y >= stationaryY)
			{
				weight = 0.0;
			}
			else
			{
				final double linear =
					(stationaryY - y) / transitionHeight;

				/*
				 * Smoothstep keeps the neck transition gradual rather than
				 * producing a hard horizontal cut through the model.
				 */
				weight = linear * linear
					* (3.0 - 2.0 * linear);
			}

			if (weight <= 0.0)
			{
				continue;
			}

			final double angle =
				direction * HEAD_TURN_RADIANS * weight;
			final double sine = Math.sin(angle);
			final double cosine = Math.cos(angle);

			final double relativeX =
				originalX[vertex] - pivot[0];
			final double relativeZ =
				originalZ[vertex] - pivot[1];

			verticesX[vertex] = (float) (
				pivot[0]
					+ relativeX * cosine
					+ relativeZ * sine
			);
			verticesZ[vertex] = (float) (
				pivot[1]
					- relativeX * sine
					+ relativeZ * cosine
			);
		}

		return new NeckTurn(
			verticesX,
			verticesY,
			verticesZ,
			originalX,
			originalY,
			originalZ,
			vertexCount
		);
	}

	private static float findNeckY(
		final float[] verticesX,
		final float[] verticesY,
		final float[] verticesZ,
		final int vertexCount,
		final float minimumY,
		final float maximumY)
	{
		final float[] binMinimumX =
			new float[NECK_SCAN_BINS];
		final float[] binMaximumX =
			new float[NECK_SCAN_BINS];
		final float[] binMinimumZ =
			new float[NECK_SCAN_BINS];
		final float[] binMaximumZ =
			new float[NECK_SCAN_BINS];
		final int[] binCounts =
			new int[NECK_SCAN_BINS];

		Arrays.fill(
			binMinimumX,
			Float.POSITIVE_INFINITY
		);
		Arrays.fill(
			binMaximumX,
			Float.NEGATIVE_INFINITY
		);
		Arrays.fill(
			binMinimumZ,
			Float.POSITIVE_INFINITY
		);
		Arrays.fill(
			binMaximumZ,
			Float.NEGATIVE_INFINITY
		);

		final float modelHeight = Math.max(
			1.0f,
			maximumY - minimumY
		);

		for (int vertex = 0; vertex < vertexCount; vertex++)
		{
			final float relativeY =
				verticesY[vertex] - minimumY;
			final int bin = clamp(
				(int) (
					relativeY * NECK_SCAN_BINS
						/ (modelHeight + 1.0f)
				),
				0,
				NECK_SCAN_BINS - 1
			);

			binMinimumX[bin] = Math.min(
				binMinimumX[bin],
				verticesX[vertex]
			);
			binMaximumX[bin] = Math.max(
				binMaximumX[bin],
				verticesX[vertex]
			);
			binMinimumZ[bin] = Math.min(
				binMinimumZ[bin],
				verticesZ[vertex]
			);
			binMaximumZ[bin] = Math.max(
				binMaximumZ[bin],
				verticesZ[vertex]
			);
			binCounts[bin]++;
		}

		final double[] widths =
			new double[NECK_SCAN_BINS];

		for (int bin = 0; bin < NECK_SCAN_BINS; bin++)
		{
			if (binCounts[bin] < 2)
			{
				widths[bin] = Double.NaN;
				continue;
			}

			final float spanX =
				binMaximumX[bin] - binMinimumX[bin];
			final float spanZ =
				binMaximumZ[bin] - binMinimumZ[bin];

			widths[bin] = Math.max(spanX, spanZ);
		}

		final double[] smoothed =
			new double[NECK_SCAN_BINS];

		for (int bin = 0; bin < NECK_SCAN_BINS; bin++)
		{
			double total = 0.0;
			int samples = 0;

			for (int sample = Math.max(0, bin - 2);
				sample <= Math.min(
					NECK_SCAN_BINS - 1,
					bin + 2
				);
				sample++)
			{
				if (!Double.isNaN(widths[sample]))
				{
					total += widths[sample];
					samples++;
				}
			}

			smoothed[bin] = samples == 0
				? Double.NaN
				: total / samples;
		}

		final int searchStart =
			(int) Math.round(NECK_SCAN_BINS * 0.22);
		final int searchEnd =
			(int) Math.round(NECK_SCAN_BINS * 0.68);

		int bestBin = -1;
		double bestScore = Double.POSITIVE_INFINITY;

		for (int bin = searchStart;
			bin <= searchEnd;
			bin++)
		{
			final double width = smoothed[bin];
			if (Double.isNaN(width) || width < 8.0)
			{
				continue;
			}

			double widestBelow = width;
			for (int lower = bin + 2;
				lower <= Math.min(
					NECK_SCAN_BINS - 1,
					bin + 11
				);
				lower++)
			{
				if (!Double.isNaN(smoothed[lower]))
				{
					widestBelow = Math.max(
						widestBelow,
						smoothed[lower]
					);
				}
			}

			/*
			 * A neck candidate must widen into the shoulders beneath it.
			 * This avoids selecting a narrow point on a tall or pointed hat.
			 */
			if (widestBelow < width * 1.28 + 5.0)
			{
				continue;
			}

			final double position =
				(bin + 0.5) / NECK_SCAN_BINS;
			final double positionPenalty =
				Math.abs(position - 0.48) * 28.0;
			final double wideningReward =
				(widestBelow - width) * 0.16;
			final double score =
				width + positionPenalty - wideningReward;

			if (score < bestScore)
			{
				bestScore = score;
				bestBin = bin;
			}
		}

		if (bestBin < 0)
		{
			/*
			 * Fail conservatively near the normal player neck position. The
			 * transition still leaves the shoulder area stationary.
			 */
			return minimumY + modelHeight * 0.48f;
		}

		return minimumY + (float) (
			(bestBin + 0.5) * modelHeight
				/ NECK_SCAN_BINS
		);
	}

	private static float[] findNeckPivot(
		final float[] verticesX,
		final float[] verticesY,
		final float[] verticesZ,
		final int vertexCount,
		final float neckY,
		final float modelHeight,
		final float minimumX,
		final float maximumX,
		final float minimumZ,
		final float maximumZ)
	{
		final float defaultX =
			(minimumX + maximumX) / 2.0f;
		final float defaultZ =
			(minimumZ + maximumZ) / 2.0f;
		final float xLimit = Math.max(
			8.0f,
			(maximumX - minimumX) * 0.35f
		);
		final float zLimit = Math.max(
			8.0f,
			(maximumZ - minimumZ) * 0.35f
		);
		final float neckBand = Math.max(
			4.0f,
			modelHeight / 25.0f
		);

		double totalX = 0.0;
		double totalZ = 0.0;
		int samples = 0;

		for (int vertex = 0; vertex < vertexCount; vertex++)
		{
			if (Math.abs(verticesY[vertex] - neckY)
				> neckBand)
			{
				continue;
			}

			if (Math.abs(verticesX[vertex] - defaultX)
					> xLimit
				|| Math.abs(verticesZ[vertex] - defaultZ)
					> zLimit)
			{
				continue;
			}

			totalX += verticesX[vertex];
			totalZ += verticesZ[vertex];
			samples++;
		}

		if (samples < 3)
		{
			return new float[]
			{
				defaultX,
				defaultZ
			};
		}

		return new float[]
		{
			(float) (totalX / samples),
			(float) (totalZ / samples)
		};
	}

	private static Framing findInitialFraming(
		final Model model,
		final Rasterizer rasterizer,
		final int[] pixels,
		final int rasterWidth,
		final int captureX,
		final int captureY)
	{
		final int modelHeight = Math.max(1, model.getModelHeight());
		final int[] verticalOffsets =
		{
			-modelHeight,
			-(modelHeight * 3 / 4),
			-(modelHeight / 2),
			-(modelHeight / 4),
			0,
			modelHeight / 4,
			modelHeight / 2,
			modelHeight * 3 / 4,
			modelHeight
		};

		Candidate best = null;

		for (final int zoom : ZOOM_LEVELS)
		{
			for (final int verticalOffset : verticalOffsets)
			{
				rasterizer.fillRectangle(
					captureX,
					captureY,
					CAPTURE_SIZE,
					CAPTURE_SIZE,
					CLEAR_RGB
				);

				model.drawOrtho(
					0,
					0,
					FRONT_YAW,
					0,
					0,
					verticalOffset,
					0,
					zoom
				);

				final BufferedImage capture = captureRegion(
					pixels,
					rasterWidth,
					captureX,
					captureY,
					CAPTURE_SIZE,
					CAPTURE_SIZE
				);

				final Candidate candidate = measure(
					capture,
					zoom,
					verticalOffset
				);

				if (candidate != null
					&& (best == null || candidate.score > best.score))
				{
					best = candidate;
				}
			}
		}

		if (best == null)
		{
			return null;
		}

		final int fullWidth = best.maxX - best.minX + 1;
		final int fullHeight = best.maxY - best.minY + 1;
		final int centerX = best.minX + fullWidth / 2;

		/*
		 * Create one permanent head-and-shoulders crop. The crop is slightly
		 * wider than the head so most helmets fit, but it does not change when
		 * the equipment model changes.
		 */
		/*
		 * Find the neck and the first widening of the shoulders from the rendered
		 * silhouette. A fixed percentage fails for tall hats because the hat can
		 * consume nearly the entire crop before the shoulders are reached.
		 */
		final int cropHeight = findHeadAndShoulderHeight(
			best.image,
			best.minY,
			best.maxY,
			fullWidth
		);

		final int widestUpperBody = findMaximumRowWidth(
			best.image,
			best.minY,
			Math.min(best.maxY, best.minY + cropHeight - 1)
		);

		final int cropWidth = Math.min(
			CAPTURE_SIZE,
			Math.max(
				96,
				Math.max(
					widestUpperBody + 8,
					(int) Math.round(cropHeight * 1.08)
				)
			)
		);

		/*
		 * Reserve extra space above the detected model so tall hats do not lose
		 * their tip before the final visible-bounds scaling step.
		 */
		final int headwearTopPadding = 24;
		final int finalCropHeight = Math.min(
			CAPTURE_SIZE,
			cropHeight + headwearTopPadding
		);

		final int cropX = clamp(
			centerX - cropWidth / 2,
			0,
			CAPTURE_SIZE - cropWidth
		);
		final int cropY = clamp(
			best.minY - headwearTopPadding,
			0,
			CAPTURE_SIZE - finalCropHeight
		);

		return new Framing(
			best.zoom,
			best.verticalOffset,
			cropX,
			cropY,
			cropWidth,
			finalCropHeight
		);
	}

	private static int findHeadAndShoulderHeight(
		final BufferedImage image,
		final int minY,
		final int maxY,
		final int fullWidth)
	{
		final int fullHeight = maxY - minY + 1;
		final int[] widths = new int[fullHeight];

		for (int offset = 0; offset < fullHeight; offset++)
		{
			widths[offset] = rowWidth(image, minY + offset);
		}

		final int[] smoothed = new int[fullHeight];
		for (int i = 0; i < fullHeight; i++)
		{
			int total = 0;
			int count = 0;

			for (int sample = Math.max(0, i - 2);
				sample <= Math.min(fullHeight - 1, i + 2);
				sample++)
			{
				if (widths[sample] > 0)
				{
					total += widths[sample];
					count++;
				}
			}

			smoothed[i] = count == 0 ? 0 : total / count;
		}

		/*
		 * The neck is normally the narrowest meaningful part between the lower
		 * head and upper torso. Ignore the very top of the model, where pointed
		 * hats naturally become only a few pixels wide.
		 */
		final int searchStart = Math.max(
			1,
			(int) Math.round(fullHeight * 0.18)
		);
		final int searchEnd = Math.min(
			fullHeight - 1,
			(int) Math.round(fullHeight * 0.58)
		);

		int neckOffset = -1;
		int neckWidth = Integer.MAX_VALUE;
		final int minimumMeaningfulWidth = Math.max(6, fullWidth / 12);

		for (int i = searchStart; i <= searchEnd; i++)
		{
			final int width = smoothed[i];
			if (width >= minimumMeaningfulWidth && width < neckWidth)
			{
				neckWidth = width;
				neckOffset = i;
			}
		}

		if (neckOffset >= 0)
		{
			final int shoulderSearchEnd = Math.min(
				fullHeight - 1,
				neckOffset + Math.max(12, fullHeight / 7)
			);

			final int shoulderThreshold = Math.max(
				neckWidth + 12,
				(int) Math.round(neckWidth * 1.45)
			);

			for (int i = neckOffset + 1; i <= shoulderSearchEnd; i++)
			{
				if (smoothed[i] >= shoulderThreshold)
				{
					/*
					 * Include only a thin line below the first shoulder widening.
					 */
					final int bottomPadding = Math.max(3, fullHeight / 45);
					return clamp(
						i + bottomPadding,
						64,
						fullHeight
					);
				}
			}
		}

		/*
		 * Conservative fallback for unusual models where a neck transition
		 * cannot be detected.
		 */
		return clamp(
			(int) Math.round(fullHeight * 0.34),
			72,
			fullHeight
		);
	}

	private static int findMaximumRowWidth(
		final BufferedImage image,
		final int startY,
		final int endY)
	{
		int maximum = 0;

		for (int y = startY; y <= endY; y++)
		{
			maximum = Math.max(maximum, rowWidth(image, y));
		}

		return maximum;
	}

	private static int rowWidth(
		final BufferedImage image,
		final int y)
	{
		if (y < 0 || y >= image.getHeight())
		{
			return 0;
		}

		int minimumX = image.getWidth();
		int maximumX = -1;

		for (int x = 0; x < image.getWidth(); x++)
		{
			final int rgb = image.getRGB(x, y) & 0x00FFFFFF;
			if (rgb == CLEAR_RGB)
			{
				continue;
			}

			minimumX = Math.min(minimumX, x);
			maximumX = Math.max(maximumX, x);
		}

		return maximumX < minimumX ? 0 : maximumX - minimumX + 1;
	}

	private static Candidate measure(
		final BufferedImage image,
		final int zoom,
		final int verticalOffset)
	{
		int minX = image.getWidth();
		int minY = image.getHeight();
		int maxX = -1;
		int maxY = -1;
		int pixelCount = 0;

		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				final int rgb = image.getRGB(x, y) & 0x00FFFFFF;
				if (rgb == CLEAR_RGB)
				{
					continue;
				}

				pixelCount++;
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}
		}

		if (pixelCount == 0 || maxX < minX || maxY < minY)
		{
			return null;
		}

		final int fullWidth = maxX - minX + 1;
		final int fullHeight = maxY - minY + 1;
		final int portraitHeight = Math.max(
			1,
			(int) Math.round(fullHeight * 0.20)
		);
		final int portraitWidth = Math.max(
			1,
			Math.min(
				(int) Math.round(fullWidth * 0.62),
				(int) Math.round(portraitHeight * 1.04)
			)
		);

		int score = 200000;
		score -= Math.abs(portraitHeight - 88) * 1800;
		score -= Math.abs(portraitWidth - 92) * 1400;

		/*
		 * Reject camera settings where the model is already touching the
		 * temporary capture boundary. Scaling a clipped render later cannot
		 * restore the missing helmet or hat geometry.
		 */
		if (minY <= 12
			|| maxY >= image.getHeight() - 13
			|| minX <= 12
			|| maxX >= image.getWidth() - 13)
		{
			return null;
		}

		return new Candidate(
			image,
			minX,
			minY,
			maxX,
			maxY,
			zoom,
			verticalOffset,
			score
		);
	}

	private BufferedImage copyLockedCrop(
		final BufferedImage capture,
		final Framing framing,
		final boolean allowCalibration,
		final int captureOffsetX,
		final int captureOffsetY)
	{
		/*
		 * Calibration remains byte-for-byte equivalent to v48. Normal renders
		 * receive extra rows above the saved crop, supplied by the larger capture.
		 */
		final int requestedTopExtension =
			allowCalibration ? 0 : TOP_SOURCE_EXTENSION_ROWS;
		final int availableTopExtension = Math.max(
			0,
			captureOffsetY + framing.cropY
		);
		final int sourceTopExtension = Math.min(
			requestedTopExtension,
			availableTopExtension
		);

		final BufferedImage source = new BufferedImage(
			framing.cropWidth,
			framing.cropHeight + sourceTopExtension,
			BufferedImage.TYPE_INT_ARGB
		);

		/*
		 * Convert the keyed capture background to transparency.
		 */
		for (int y = 0; y < source.getHeight(); y++)
		{
			for (int x = 0; x < framing.cropWidth; x++)
			{
				final int sourceX =
					captureOffsetX + framing.cropX + x;
				final int sourceY =
					captureOffsetY
						+ framing.cropY
						- sourceTopExtension
						+ y;

				if (sourceX < 0
					|| sourceX >= capture.getWidth()
					|| sourceY < 0
					|| sourceY >= capture.getHeight())
				{
					continue;
				}

				final int rgb = capture.getRGB(
					sourceX,
					sourceY
				) & 0x00FFFFFF;

				if (rgb != CLEAR_RGB)
				{
					source.setRGB(x, y, 0xFF000000 | rgb);
				}
			}
		}

		/*
		 * Measure the exact v48 visible bounds only during calibration.
		 * After that, every equipment setup reuses the same rectangle.
		 */
		if (lockedContentBounds == null)
		{
			if (!allowCalibration)
			{
				return null;
			}

			int measuredMinX = source.getWidth();
			int measuredMinY = source.getHeight();
			int measuredMaxX = -1;
			int measuredMaxY = -1;

			for (int y = 0; y < source.getHeight(); y++)
			{
				for (int x = 0; x < source.getWidth(); x++)
				{
					if ((source.getRGB(x, y) >>> 24) == 0)
					{
						continue;
					}

					measuredMinX = Math.min(measuredMinX, x);
					measuredMinY = Math.min(measuredMinY, y);
					measuredMaxX = Math.max(measuredMaxX, x);
					measuredMaxY = Math.max(measuredMaxY, y);
				}
			}

			if (measuredMaxX < measuredMinX
				|| measuredMaxY < measuredMinY)
			{
				return new BufferedImage(
					OUTPUT_SIZE,
					OUTPUT_SIZE,
					BufferedImage.TYPE_INT_ARGB
				);
			}

			final int horizontalPadding = 3;
			final int topPadding = 16;
			final int bottomPadding = 1;

			lockedContentBounds = new ContentBounds(
				Math.max(0, measuredMinX - horizontalPadding),
				Math.max(0, measuredMinY - topPadding),
				Math.min(
					source.getWidth() - 1,
					measuredMaxX + horizontalPadding
				),
				Math.min(
					source.getHeight() - 1,
					measuredMaxY + bottomPadding
				)
			);
		}

		final int minX = lockedContentBounds.minX;
		final int minY =
			lockedContentBounds.minY + sourceTopExtension;
		final int maxX = lockedContentBounds.maxX;
		final int maxY =
			lockedContentBounds.maxY + sourceTopExtension;

		final int visibleWidth = maxX - minX + 1;
		final int visibleHeight = maxY - minY + 1;

		final int availableWidth = OUTPUT_SIZE - 4;
		final int availableHeight = OUTPUT_SIZE - 3;

		final double scale = Math.min(
			(double) availableWidth / visibleWidth,
			(double) availableHeight / visibleHeight
		);

		final int drawWidth = Math.max(
			1,
			(int) Math.round(visibleWidth * scale)
		);
		final int drawHeight = Math.max(
			1,
			(int) Math.round(visibleHeight * scale)
		);

		final int targetX = (OUTPUT_SIZE - drawWidth) / 2;

		/*
		 * Keep the body at the exact same bottom-anchored position and scale.
		 */
		final int targetY = OUTPUT_SIZE - drawHeight;

		/*
		 * The old renderer left targetY pixels unused above the body, but threw
		 * away source rows above minY. Reuse only that already-empty output space.
		 * This reveals taller helmets and upper model geometry without changing
		 * the body's zoom, size, targetY, or lower-body visibility.
		 */
		final int availableTopSourceRows = Math.min(
			minY,
			Math.max(
				0,
				(int) Math.floor(targetY / scale)
			)
		);
		final int extendedMinY =
			minY - availableTopSourceRows;
		final int extendedVisibleHeight =
			maxY - extendedMinY + 1;
		final int extendedDrawHeight = Math.max(
			1,
			(int) Math.round(
				extendedVisibleHeight * scale
			)
		);
		final int extensionDrawHeight =
			extendedDrawHeight - drawHeight;
		final int extendedTargetY = Math.max(
			0,
			targetY - extensionDrawHeight
		);

		final BufferedImage scaledPortrait = new BufferedImage(
			drawWidth,
			extendedDrawHeight,
			BufferedImage.TYPE_INT_ARGB
		);

		final Graphics2D scaledGraphics = scaledPortrait.createGraphics();
		scaledGraphics.setRenderingHint(
			RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BICUBIC
		);
		scaledGraphics.setRenderingHint(
			RenderingHints.KEY_ALPHA_INTERPOLATION,
			RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
		);
		scaledGraphics.setRenderingHint(
			RenderingHints.KEY_RENDERING,
			RenderingHints.VALUE_RENDER_QUALITY
		);

		scaledGraphics.drawImage(
			source,
			0,
			0,
			drawWidth,
			extendedDrawHeight,
			minX,
			extendedMinY,
			maxX + 1,
			maxY + 1,
			null
		);
		scaledGraphics.dispose();

		final BufferedImage refinedPortrait =
			applySubtleSharpen(scaledPortrait);

		final BufferedImage output = new BufferedImage(
			OUTPUT_SIZE,
			OUTPUT_SIZE,
			BufferedImage.TYPE_INT_ARGB
		);

		final Graphics2D graphics = output.createGraphics();
		graphics.setRenderingHint(
			RenderingHints.KEY_ALPHA_INTERPOLATION,
			RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
		);
		graphics.setRenderingHint(
			RenderingHints.KEY_RENDERING,
			RenderingHints.VALUE_RENDER_QUALITY
		);
		graphics.drawImage(
			refinedPortrait,
			targetX,
			extendedTargetY,
			null
		);
		graphics.dispose();

		return output;
	}


	private static BufferedImage applySubtleSharpen(
		final BufferedImage input)
	{
		/*
		 * Light unsharp pass. This improves perceived crispness without
		 * changing the portrait framing, zoom, or placement.
		 */
		final float[] kernel =
		{
			0.0f, -0.10f, 0.0f,
			-0.10f, 1.40f, -0.10f,
			0.0f, -0.10f, 0.0f
		};

		final ConvolveOp sharpen = new ConvolveOp(
			new Kernel(3, 3, kernel),
			ConvolveOp.EDGE_NO_OP,
			null
		);

		return sharpen.filter(input, null);
	}

	private static int clamp(
		final int value,
		final int minimum,
		final int maximum)
	{
		if (maximum < minimum)
		{
			return minimum;
		}

		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int[] copyRegion(
		final int[] pixels,
		final int rasterWidth,
		final int x,
		final int y,
		final int width,
		final int height)
	{
		final int[] copy = new int[width * height];

		for (int row = 0; row < height; row++)
		{
			System.arraycopy(
				pixels,
				(y + row) * rasterWidth + x,
				copy,
				row * width,
				width
			);
		}

		return copy;
	}

	private static void restoreRegion(
		final int[] pixels,
		final int rasterWidth,
		final int x,
		final int y,
		final int width,
		final int height,
		final int[] savedPixels)
	{
		for (int row = 0; row < height; row++)
		{
			System.arraycopy(
				savedPixels,
				row * width,
				pixels,
				(y + row) * rasterWidth + x,
				width
			);
		}
	}

	private static BufferedImage captureRegion(
		final int[] pixels,
		final int rasterWidth,
		final int x,
		final int y,
		final int width,
		final int height)
	{
		final BufferedImage image = new BufferedImage(
			width,
			height,
			BufferedImage.TYPE_INT_ARGB
		);

		for (int row = 0; row < height; row++)
		{
			for (int column = 0; column < width; column++)
			{
				final int rgb = pixels[
					(y + row) * rasterWidth + x + column
				] & 0x00FFFFFF;

				image.setRGB(
					column,
					row,
					0xFF000000 | rgb
				);
			}
		}

		return image;
	}

	private static final class ContentBounds
	{
		private final int minX;
		private final int minY;
		private final int maxX;
		private final int maxY;

		private ContentBounds(
			final int minX,
			final int minY,
			final int maxX,
			final int maxY)
		{
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
		}
	}

	private static final class NeckTurn
	{
		private final float[] verticesX;
		private final float[] verticesY;
		private final float[] verticesZ;
		private final float[] originalX;
		private final float[] originalY;
		private final float[] originalZ;
		private final int vertexCount;

		private NeckTurn(
			final float[] verticesX,
			final float[] verticesY,
			final float[] verticesZ,
			final float[] originalX,
			final float[] originalY,
			final float[] originalZ,
			final int vertexCount)
		{
			this.verticesX = verticesX;
			this.verticesY = verticesY;
			this.verticesZ = verticesZ;
			this.originalX = originalX;
			this.originalY = originalY;
			this.originalZ = originalZ;
			this.vertexCount = vertexCount;
		}

		private void restore()
		{
			System.arraycopy(
				originalX,
				0,
				verticesX,
				0,
				vertexCount
			);
			System.arraycopy(
				originalY,
				0,
				verticesY,
				0,
				vertexCount
			);
			System.arraycopy(
				originalZ,
				0,
				verticesZ,
				0,
				vertexCount
			);
		}
	}

	private static final class Candidate
	{
		private final BufferedImage image;
		private final int minX;
		private final int minY;
		private final int maxX;
		private final int maxY;
		private final int zoom;
		private final int verticalOffset;
		private final int score;

		private Candidate(
			final BufferedImage image,
			final int minX,
			final int minY,
			final int maxX,
			final int maxY,
			final int zoom,
			final int verticalOffset,
			final int score)
		{
			this.image = image;
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
			this.zoom = zoom;
			this.verticalOffset = verticalOffset;
			this.score = score;
		}
	}

	private static final class Framing
	{
		private final int zoom;
		private final int verticalOffset;
		private final int cropX;
		private final int cropY;
		private final int cropWidth;
		private final int cropHeight;

		private Framing(
			final int zoom,
			final int verticalOffset,
			final int cropX,
			final int cropY,
			final int cropWidth,
			final int cropHeight)
		{
			this.zoom = zoom;
			this.verticalOffset = verticalOffset;
			this.cropX = cropX;
			this.cropY = cropY;
			this.cropWidth = cropWidth;
			this.cropHeight = cropHeight;
		}
	}
}
