package com.slayerplus;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Rasterizer;

@RequiredArgsConstructor
public final class PlayerPortraitRenderer {
  private static final int CAPTURE_SIZE = 448;
  private static final int OUTPUT_SIZE = 140;
  private static final int FRONT_YAW = 0;
  private static final double HEAD_TURN_RADIANS = Math.toRadians(11.0);
  private static final int NECK_SCAN_BINS = 72;
  private static final int CLEAR_RGB = 0x010203;
  private static final int EXTENDED_CAPTURE_EXTRA = 448;
  private static final int TOP_SOURCE_EXTENSION_ROWS = 64;
  private static final int[] ZOOM_LEVELS = {120, 145, 170, 200, 230, 260, 300, 340, 380};
  private final Client client;
  private Framing lockedFraming;
  private ContentBounds lockedContentBounds;

  public BufferedImage render(Model inputModel, int turnDirection) {
    if (inputModel == null) {
      return null;
    }
    Model unskewed = inputModel.getUnskewedModel();
    Model model = unskewed != null ? unskewed : inputModel;
    Rasterizer rasterizer = client.getRasterizer();
    if (rasterizer == null) {
      return null;
    }
    int rasterWidth = rasterizer.getWidth();
    int rasterHeight = rasterizer.getHeight();
    int[] pixels = rasterizer.getPixels();
    if (pixels == null || rasterWidth < CAPTURE_SIZE || rasterHeight < CAPTURE_SIZE) {
      return null;
    }
    int captureX = (rasterWidth - CAPTURE_SIZE) / 2;
    int captureY = (rasterHeight - CAPTURE_SIZE) / 2;
    int extendedCaptureSize =
        Math.max(
            CAPTURE_SIZE,
            Math.min(CAPTURE_SIZE + EXTENDED_CAPTURE_EXTRA, Math.min(rasterWidth, rasterHeight)));
    int extendedCaptureX = (rasterWidth - extendedCaptureSize) / 2;
    int extendedCaptureY = (rasterHeight - extendedCaptureSize) / 2;
    int extendedOffsetX = captureX - extendedCaptureX;
    int extendedOffsetY = captureY - extendedCaptureY;
    int[] savedPixels =
        copyRegion(
            pixels,
            rasterWidth,
            extendedCaptureX,
            extendedCaptureY,
            extendedCaptureSize,
            extendedCaptureSize);
    try {
      rasterizer.setDrawRegion(
          captureX, captureY, captureX + CAPTURE_SIZE, captureY + CAPTURE_SIZE);
      model.calculateBoundsCylinder();
      if (lockedFraming == null) {
        lockedFraming =
            findInitialFraming(model, rasterizer, pixels, rasterWidth, captureX, captureY);
      }
      if (lockedFraming == null) {
        return null;
      }
      if (lockedContentBounds == null) {
        var calibrationCapture =
            renderCaptureSized(
                model,
                rasterizer,
                pixels,
                rasterWidth,
                captureX,
                captureY,
                CAPTURE_SIZE,
                lockedFraming);
        var calibrationPortrait = copyLockedCrop(calibrationCapture, lockedFraming, true, 0, 0);
        if (calibrationPortrait == null) {
          return null;
        }
      }
      NeckTurn neckTurn = applyTemporaryNeckTurn(model, turnDirection);
      try {
        var capture =
            renderCaptureSized(
                model,
                rasterizer,
                pixels,
                rasterWidth,
                extendedCaptureX,
                extendedCaptureY,
                extendedCaptureSize,
                lockedFraming);
        return copyLockedCrop(capture, lockedFraming, false, extendedOffsetX, extendedOffsetY);
      } finally {
        if (neckTurn != null) {
          neckTurn.restore();
        }
      }
    } finally {
      try {
        restoreRegion(
            pixels,
            rasterWidth,
            extendedCaptureX,
            extendedCaptureY,
            extendedCaptureSize,
            extendedCaptureSize,
            savedPixels);
      } finally {
        rasterizer.resetRasterClipping();
      }
    }
  }

  private static BufferedImage renderCaptureSized(
      Model model,
      Rasterizer rasterizer,
      int[] pixels,
      int rasterWidth,
      int captureX,
      int captureY,
      int captureSize,
      Framing framing) {
    rasterizer.setDrawRegion(captureX, captureY, captureX + captureSize, captureY + captureSize);
    rasterizer.fillRectangle(captureX, captureY, captureSize, captureSize, CLEAR_RGB);
    model.drawOrtho(0, 0, FRONT_YAW, 0, 0, framing.verticalOffset, 0, framing.zoom);
    return captureRegion(pixels, rasterWidth, captureX, captureY, captureSize, captureSize);
  }

  public boolean hasCalibration() {
    return lockedFraming != null && lockedContentBounds != null;
  }

  public String exportCalibration() {
    if (!hasCalibration()) {
      return null;
    }
    return "v1"
        + ","
        + lockedFraming.zoom
        + ","
        + lockedFraming.verticalOffset
        + ","
        + lockedFraming.cropX
        + ","
        + lockedFraming.cropY
        + ","
        + lockedFraming.cropWidth
        + ","
        + lockedFraming.cropHeight
        + ","
        + lockedContentBounds.minX
        + ","
        + lockedContentBounds.minY
        + ","
        + lockedContentBounds.maxX
        + ","
        + lockedContentBounds.maxY;
  }

  public boolean loadCalibration(String value) {
    if (value == null || value.trim().isEmpty()) {
      return false;
    }
    String[] parts = value.split(",");
    if (parts.length != 11 || !"v1".equals(parts[0])) {
      return false;
    }
    try {
      int zoom = Integer.parseInt(parts[1]);
      int verticalOffset = Integer.parseInt(parts[2]);
      int cropX = Integer.parseInt(parts[3]);
      int cropY = Integer.parseInt(parts[4]);
      int cropWidth = Integer.parseInt(parts[5]);
      int cropHeight = Integer.parseInt(parts[6]);
      int minX = Integer.parseInt(parts[7]);
      int minY = Integer.parseInt(parts[8]);
      int maxX = Integer.parseInt(parts[9]);
      int maxY = Integer.parseInt(parts[10]);
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
          || maxY >= cropHeight) {
        return false;
      }
      lockedFraming = new Framing(zoom, verticalOffset, cropX, cropY, cropWidth, cropHeight);
      lockedContentBounds = new ContentBounds(minX, minY, maxX, maxY);
      return true;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private static Framing findInitialFraming(
      Model model,
      Rasterizer rasterizer,
      int[] pixels,
      int rasterWidth,
      int captureX,
      int captureY) {
    int modelHeight = Math.max(1, model.getModelHeight());
    int[] verticalOffsets = {
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
    for (int zoom : ZOOM_LEVELS) {
      for (int verticalOffset : verticalOffsets) {
        rasterizer.fillRectangle(captureX, captureY, CAPTURE_SIZE, CAPTURE_SIZE, CLEAR_RGB);
        model.drawOrtho(0, 0, FRONT_YAW, 0, 0, verticalOffset, 0, zoom);
        var capture =
            captureRegion(pixels, rasterWidth, captureX, captureY, CAPTURE_SIZE, CAPTURE_SIZE);
        Candidate candidate = measure(capture, zoom, verticalOffset);
        if (candidate != null && (best == null || candidate.score > best.score)) {
          best = candidate;
        }
      }
    }
    if (best == null) {
      return null;
    }
    int fullWidth = best.maxX - best.minX + 1;
    int fullHeight = best.maxY - best.minY + 1;
    int centerX = best.minX + fullWidth / 2;
    int cropHeight = findHeadAndShoulderHeight(best.image, best.minY, best.maxY, fullWidth);
    int widestUpperBody =
        findMaximumRowWidth(best.image, best.minY, Math.min(best.maxY, best.minY + cropHeight - 1));
    int cropWidth =
        Math.min(
            CAPTURE_SIZE,
            Math.max(96, Math.max(widestUpperBody + 8, (int) Math.round(cropHeight * 1.08))));
    int headwearTopPadding = 24;
    int finalCropHeight = Math.min(CAPTURE_SIZE, cropHeight + headwearTopPadding);
    int cropX = clamp(centerX - cropWidth / 2, 0, CAPTURE_SIZE - cropWidth);
    int cropY = clamp(best.minY - headwearTopPadding, 0, CAPTURE_SIZE - finalCropHeight);
    return new Framing(best.zoom, best.verticalOffset, cropX, cropY, cropWidth, finalCropHeight);
  }

  private static int findHeadAndShoulderHeight(
      BufferedImage image, int minY, int maxY, int fullWidth) {
    int fullHeight = maxY - minY + 1;
    int[] widths = new int[fullHeight];
    for (int offset = 0; offset < fullHeight; offset++) {
      widths[offset] = rowWidth(image, minY + offset);
    }
    int[] smoothed = new int[fullHeight];
    for (int i = 0; i < fullHeight; i++) {
      int total = 0;
      int count = 0;
      for (int sample = Math.max(0, i - 2); sample <= Math.min(fullHeight - 1, i + 2); sample++) {
        if (widths[sample] > 0) {
          total += widths[sample];
          count++;
        }
      }
      smoothed[i] = count == 0 ? 0 : total / count;
    }
    int searchStart = Math.max(1, (int) Math.round(fullHeight * 0.18));
    int searchEnd = Math.min(fullHeight - 1, (int) Math.round(fullHeight * 0.58));
    int neckOffset = -1;
    int neckWidth = Integer.MAX_VALUE;
    int minimumMeaningfulWidth = Math.max(6, fullWidth / 12);
    for (int i = searchStart; i <= searchEnd; i++) {
      int width = smoothed[i];
      if (width >= minimumMeaningfulWidth && width < neckWidth) {
        neckWidth = width;
        neckOffset = i;
      }
    }
    if (neckOffset >= 0) {
      int shoulderSearchEnd = Math.min(fullHeight - 1, neckOffset + Math.max(12, fullHeight / 7));
      int shoulderThreshold = Math.max(neckWidth + 12, (int) Math.round(neckWidth * 1.45));
      for (int i = neckOffset + 1; i <= shoulderSearchEnd; i++) {
        if (smoothed[i] >= shoulderThreshold) {
          int bottomPadding = Math.max(3, fullHeight / 45);
          return clamp(i + bottomPadding, 64, fullHeight);
        }
      }
    }
    return clamp((int) Math.round(fullHeight * 0.34), 72, fullHeight);
  }

  private static int findMaximumRowWidth(BufferedImage image, int startY, int endY) {
    int maximum = 0;
    for (int y = startY; y <= endY; y++) {
      maximum = Math.max(maximum, rowWidth(image, y));
    }
    return maximum;
  }

  private static int rowWidth(BufferedImage image, int y) {
    if (y < 0 || y >= image.getHeight()) {
      return 0;
    }
    int minimumX = image.getWidth();
    int maximumX = -1;
    for (int x = 0; x < image.getWidth(); x++) {
      int rgb = image.getRGB(x, y) & 0x00FFFFFF;
      if (rgb == CLEAR_RGB) {
        continue;
      }
      minimumX = Math.min(minimumX, x);
      maximumX = Math.max(maximumX, x);
    }
    return maximumX < minimumX ? 0 : maximumX - minimumX + 1;
  }

  private static Candidate measure(BufferedImage image, int zoom, int verticalOffset) {
    int minX = image.getWidth();
    int minY = image.getHeight();
    int maxX = -1;
    int maxY = -1;
    int pixelCount = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int rgb = image.getRGB(x, y) & 0x00FFFFFF;
        if (rgb == CLEAR_RGB) {
          continue;
        }
        pixelCount++;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
      }
    }
    if (pixelCount == 0 || maxX < minX || maxY < minY) {
      return null;
    }
    int fullWidth = maxX - minX + 1;
    int fullHeight = maxY - minY + 1;
    int portraitHeight = Math.max(1, (int) Math.round(fullHeight * 0.20));
    int portraitWidth =
        Math.max(
            1,
            Math.min((int) Math.round(fullWidth * 0.62), (int) Math.round(portraitHeight * 1.04)));
    int score = 200000;
    score -= Math.abs(portraitHeight - 88) * 1800;
    score -= Math.abs(portraitWidth - 92) * 1400;
    if (minY <= 12
        || maxY >= image.getHeight() - 13
        || minX <= 12
        || maxX >= image.getWidth() - 13) {
      return null;
    }
    return new Candidate(image, minX, minY, maxX, maxY, zoom, verticalOffset, score);
  }

  private BufferedImage copyLockedCrop(
      BufferedImage capture,
      Framing framing,
      boolean allowCalibration,
      int captureOffsetX,
      int captureOffsetY) {
    int requestedTopExtension = allowCalibration ? 0 : TOP_SOURCE_EXTENSION_ROWS;
    int availableTopExtension = Math.max(0, captureOffsetY + framing.cropY);
    int sourceTopExtension = Math.min(requestedTopExtension, availableTopExtension);
    var source =
        new BufferedImage(
            framing.cropWidth,
            framing.cropHeight + sourceTopExtension,
            BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < framing.cropWidth; x++) {
        int sourceX = captureOffsetX + framing.cropX + x;
        int sourceY = captureOffsetY + framing.cropY - sourceTopExtension + y;
        if (sourceX < 0
            || sourceX >= capture.getWidth()
            || sourceY < 0
            || sourceY >= capture.getHeight()) {
          continue;
        }
        int rgb = capture.getRGB(sourceX, sourceY) & 0x00FFFFFF;
        if (rgb != CLEAR_RGB) {
          source.setRGB(x, y, 0xFF000000 | rgb);
        }
      }
    }
    if (lockedContentBounds == null) {
      if (!allowCalibration) {
        return null;
      }
      int measuredMinX = source.getWidth();
      int measuredMinY = source.getHeight();
      int measuredMaxX = -1;
      int measuredMaxY = -1;
      for (int y = 0; y < source.getHeight(); y++) {
        for (int x = 0; x < source.getWidth(); x++) {
          if ((source.getRGB(x, y) >>> 24) == 0) {
            continue;
          }
          measuredMinX = Math.min(measuredMinX, x);
          measuredMinY = Math.min(measuredMinY, y);
          measuredMaxX = Math.max(measuredMaxX, x);
          measuredMaxY = Math.max(measuredMaxY, y);
        }
      }
      if (measuredMaxX < measuredMinX || measuredMaxY < measuredMinY) {
        return new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
      }
      int horizontalPadding = 3;
      int topPadding = 16;
      int bottomPadding = 1;
      lockedContentBounds =
          new ContentBounds(
              Math.max(0, measuredMinX - horizontalPadding),
              Math.max(0, measuredMinY - topPadding),
              Math.min(source.getWidth() - 1, measuredMaxX + horizontalPadding),
              Math.min(source.getHeight() - 1, measuredMaxY + bottomPadding));
    }
    int minX = lockedContentBounds.minX;
    int minY = lockedContentBounds.minY + sourceTopExtension;
    int maxX = lockedContentBounds.maxX;
    int maxY = lockedContentBounds.maxY + sourceTopExtension;
    int visibleWidth = maxX - minX + 1;
    int visibleHeight = maxY - minY + 1;
    int availableWidth = OUTPUT_SIZE - 4;
    int availableHeight = OUTPUT_SIZE - 3;
    double scale =
        Math.min((double) availableWidth / visibleWidth, (double) availableHeight / visibleHeight);
    int drawWidth = Math.max(1, (int) Math.round(visibleWidth * scale));
    int drawHeight = Math.max(1, (int) Math.round(visibleHeight * scale));
    int targetX = (OUTPUT_SIZE - drawWidth) / 2;
    int targetY = OUTPUT_SIZE - drawHeight;
    int availableTopSourceRows = Math.min(minY, Math.max(0, (int) Math.floor(targetY / scale)));
    int extendedMinY = minY - availableTopSourceRows;
    int extendedVisibleHeight = maxY - extendedMinY + 1;
    int extendedDrawHeight = Math.max(1, (int) Math.round(extendedVisibleHeight * scale));
    int extensionDrawHeight = extendedDrawHeight - drawHeight;
    int extendedTargetY = Math.max(0, targetY - extensionDrawHeight);
    var scaledPortrait =
        new BufferedImage(drawWidth, extendedDrawHeight, BufferedImage.TYPE_INT_ARGB);
    var scaledGraphics = scaledPortrait.createGraphics();
    scaledGraphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    scaledGraphics.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    scaledGraphics.setRenderingHint(
        RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    scaledGraphics.drawImage(
        source, 0, 0, drawWidth, extendedDrawHeight, minX, extendedMinY, maxX + 1, maxY + 1, null);
    scaledGraphics.dispose();
    var refinedPortrait = applySubtleSharpen(scaledPortrait);
    var output = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
    var graphics = output.createGraphics();
    graphics.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.drawImage(refinedPortrait, targetX, extendedTargetY, null);
    graphics.dispose();
    return output;
  }

  private static BufferedImage applySubtleSharpen(BufferedImage input) {
    float[] kernel = {0.0f, -0.10f, 0.0f, -0.10f, 1.40f, -0.10f, 0.0f, -0.10f, 0.0f};
    ConvolveOp sharpen = new ConvolveOp(new Kernel(3, 3, kernel), ConvolveOp.EDGE_NO_OP, null);
    return sharpen.filter(input, null);
  }

  private static NeckTurn applyTemporaryNeckTurn(Model model, int turnDirection) {
    int vertexCount = model.getVerticesCount();
    float[] verticesX = model.getVerticesX();
    float[] verticesY = model.getVerticesY();
    float[] verticesZ = model.getVerticesZ();
    if (vertexCount < 12
        || verticesX == null
        || verticesY == null
        || verticesZ == null
        || verticesX.length < vertexCount
        || verticesY.length < vertexCount
        || verticesZ.length < vertexCount) {
      return null;
    }
    float minimumY = Float.POSITIVE_INFINITY;
    float maximumY = Float.NEGATIVE_INFINITY;
    float minimumX = Float.POSITIVE_INFINITY;
    float maximumX = Float.NEGATIVE_INFINITY;
    float minimumZ = Float.POSITIVE_INFINITY;
    float maximumZ = Float.NEGATIVE_INFINITY;
    for (int vertex = 0; vertex < vertexCount; vertex++) {
      minimumY = Math.min(minimumY, verticesY[vertex]);
      maximumY = Math.max(maximumY, verticesY[vertex]);
      minimumX = Math.min(minimumX, verticesX[vertex]);
      maximumX = Math.max(maximumX, verticesX[vertex]);
      minimumZ = Math.min(minimumZ, verticesZ[vertex]);
      maximumZ = Math.max(maximumZ, verticesZ[vertex]);
    }
    float modelHeight = maximumY - minimumY;
    if (modelHeight < 20.0f) {
      return null;
    }
    float neckY = findNeckY(verticesX, verticesY, verticesZ, vertexCount, minimumY, maximumY);
    float[] pivot =
        findNeckPivot(
            verticesX,
            verticesY,
            verticesZ,
            vertexCount,
            neckY,
            modelHeight,
            minimumX,
            maximumX,
            minimumZ,
            maximumZ);
    float[] originalX = Arrays.copyOf(verticesX, vertexCount);
    float[] originalY = Arrays.copyOf(verticesY, vertexCount);
    float[] originalZ = Arrays.copyOf(verticesZ, vertexCount);
    double direction = turnDirection < 0 ? -1.0 : 1.0;
    float fullTurnY = neckY - Math.max(3.0f, modelHeight / 45.0f);
    float stationaryY = neckY + Math.max(7.0f, modelHeight / 18.0f);
    float transitionHeight = Math.max(1.0f, stationaryY - fullTurnY);
    for (int vertex = 0; vertex < vertexCount; vertex++) {
      double weight = turnWeight(originalY[vertex], fullTurnY, stationaryY, transitionHeight);
      if (weight <= 0.0) {
        continue;
      }
      double angle = direction * HEAD_TURN_RADIANS * weight;
      double sine = Math.sin(angle);
      double cosine = Math.cos(angle);
      double relativeX = originalX[vertex] - pivot[0];
      double relativeZ = originalZ[vertex] - pivot[1];
      verticesX[vertex] = (float) (pivot[0] + relativeX * cosine + relativeZ * sine);
      verticesZ[vertex] = (float) (pivot[1] - relativeX * sine + relativeZ * cosine);
    }
    return new NeckTurn(
        verticesX, verticesY, verticesZ, originalX, originalY, originalZ, vertexCount);
  }

  private static double turnWeight(
      float vertexY, float fullTurnY, float stationaryY, float transitionHeight) {
    if (vertexY <= fullTurnY) {
      return 1.0;
    }
    if (vertexY >= stationaryY) {
      return 0.0;
    }
    double linear = (stationaryY - vertexY) / transitionHeight;
    return linear * linear * (3.0 - 2.0 * linear);
  }

  private static float findNeckY(
      float[] verticesX,
      float[] verticesY,
      float[] verticesZ,
      int vertexCount,
      float minimumY,
      float maximumY) {
    double[] widths = modelWidths(verticesX, verticesY, verticesZ, vertexCount, minimumY, maximumY);
    double[] smoothed = smoothWidths(widths);
    int searchStart = (int) Math.round(NECK_SCAN_BINS * 0.22);
    int searchEnd = (int) Math.round(NECK_SCAN_BINS * 0.68);
    int bestBin = -1;
    double bestScore = Double.POSITIVE_INFINITY;
    for (int bin = searchStart; bin <= searchEnd; bin++) {
      double width = smoothed[bin];
      if (Double.isNaN(width) || width < 8.0) {
        continue;
      }
      double widestBelow = width;
      for (int lower = bin + 2; lower <= Math.min(NECK_SCAN_BINS - 1, bin + 11); lower++) {
        if (!Double.isNaN(smoothed[lower])) {
          widestBelow = Math.max(widestBelow, smoothed[lower]);
        }
      }
      if (widestBelow < width * 1.28 + 5.0) {
        continue;
      }
      double position = (bin + 0.5) / NECK_SCAN_BINS;
      double score = width + Math.abs(position - 0.48) * 28.0 - (widestBelow - width) * 0.16;
      if (score < bestScore) {
        bestScore = score;
        bestBin = bin;
      }
    }
    float modelHeight = maximumY - minimumY;
    return bestBin < 0
        ? minimumY + modelHeight * 0.48f
        : minimumY + (float) ((bestBin + 0.5) * modelHeight / NECK_SCAN_BINS);
  }

  private static double[] modelWidths(
      float[] verticesX,
      float[] verticesY,
      float[] verticesZ,
      int vertexCount,
      float minimumY,
      float maximumY) {
    float[] minimumX = new float[NECK_SCAN_BINS];
    float[] maximumX = new float[NECK_SCAN_BINS];
    float[] minimumZ = new float[NECK_SCAN_BINS];
    float[] maximumZ = new float[NECK_SCAN_BINS];
    int[] counts = new int[NECK_SCAN_BINS];
    Arrays.fill(minimumX, Float.POSITIVE_INFINITY);
    Arrays.fill(maximumX, Float.NEGATIVE_INFINITY);
    Arrays.fill(minimumZ, Float.POSITIVE_INFINITY);
    Arrays.fill(maximumZ, Float.NEGATIVE_INFINITY);
    float modelHeight = Math.max(1.0f, maximumY - minimumY);
    for (int vertex = 0; vertex < vertexCount; vertex++) {
      int bin =
          clamp(
              (int) ((verticesY[vertex] - minimumY) * NECK_SCAN_BINS / (modelHeight + 1.0f)),
              0,
              NECK_SCAN_BINS - 1);
      minimumX[bin] = Math.min(minimumX[bin], verticesX[vertex]);
      maximumX[bin] = Math.max(maximumX[bin], verticesX[vertex]);
      minimumZ[bin] = Math.min(minimumZ[bin], verticesZ[vertex]);
      maximumZ[bin] = Math.max(maximumZ[bin], verticesZ[vertex]);
      counts[bin]++;
    }
    double[] widths = new double[NECK_SCAN_BINS];
    for (int bin = 0; bin < NECK_SCAN_BINS; bin++) {
      widths[bin] =
          counts[bin] < 2
              ? Double.NaN
              : Math.max(maximumX[bin] - minimumX[bin], maximumZ[bin] - minimumZ[bin]);
    }
    return widths;
  }

  private static double[] smoothWidths(double[] widths) {
    double[] smoothed = new double[NECK_SCAN_BINS];
    for (int bin = 0; bin < NECK_SCAN_BINS; bin++) {
      double total = 0.0;
      int samples = 0;
      for (int sample = Math.max(0, bin - 2);
          sample <= Math.min(NECK_SCAN_BINS - 1, bin + 2);
          sample++) {
        if (!Double.isNaN(widths[sample])) {
          total += widths[sample];
          samples++;
        }
      }
      smoothed[bin] = samples == 0 ? Double.NaN : total / samples;
    }
    return smoothed;
  }

  private static float[] findNeckPivot(
      float[] verticesX,
      float[] verticesY,
      float[] verticesZ,
      int vertexCount,
      float neckY,
      float modelHeight,
      float minimumX,
      float maximumX,
      float minimumZ,
      float maximumZ) {
    float defaultX = (minimumX + maximumX) / 2.0f;
    float defaultZ = (minimumZ + maximumZ) / 2.0f;
    float xLimit = Math.max(8.0f, (maximumX - minimumX) * 0.35f);
    float zLimit = Math.max(8.0f, (maximumZ - minimumZ) * 0.35f);
    float neckBand = Math.max(4.0f, modelHeight / 25.0f);
    double totalX = 0.0;
    double totalZ = 0.0;
    int samples = 0;
    for (int vertex = 0; vertex < vertexCount; vertex++) {
      if (Math.abs(verticesY[vertex] - neckY) > neckBand
          || Math.abs(verticesX[vertex] - defaultX) > xLimit
          || Math.abs(verticesZ[vertex] - defaultZ) > zLimit) {
        continue;
      }
      totalX += verticesX[vertex];
      totalZ += verticesZ[vertex];
      samples++;
    }
    return samples < 3
        ? new float[] {defaultX, defaultZ}
        : new float[] {(float) (totalX / samples), (float) (totalZ / samples)};
  }

  private static int clamp(int value, int minimum, int maximum) {
    if (maximum < minimum) {
      return minimum;
    }
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static int[] copyRegion(
      int[] pixels, int rasterWidth, int x, int y, int width, int height) {
    int[] copy = new int[width * height];
    for (int row = 0; row < height; row++) {
      System.arraycopy(pixels, (y + row) * rasterWidth + x, copy, row * width, width);
    }
    return copy;
  }

  private static void restoreRegion(
      int[] pixels, int rasterWidth, int x, int y, int width, int height, int[] savedPixels) {
    for (int row = 0; row < height; row++) {
      System.arraycopy(savedPixels, row * width, pixels, (y + row) * rasterWidth + x, width);
    }
  }

  private static BufferedImage captureRegion(
      int[] pixels, int rasterWidth, int x, int y, int width, int height) {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    for (int row = 0; row < height; row++) {
      for (int column = 0; column < width; column++) {
        int rgb = pixels[(y + row) * rasterWidth + x + column] & 0x00FFFFFF;
        image.setRGB(column, row, 0xFF000000 | rgb);
      }
    }
    return image;
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class ContentBounds {
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class NeckTurn {
    private final float[] verticesX;
    private final float[] verticesY;
    private final float[] verticesZ;
    private final float[] originalX;
    private final float[] originalY;
    private final float[] originalZ;
    private final int vertexCount;

    private void restore() {
      System.arraycopy(originalX, 0, verticesX, 0, vertexCount);
      System.arraycopy(originalY, 0, verticesY, 0, vertexCount);
      System.arraycopy(originalZ, 0, verticesZ, 0, vertexCount);
    }
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class Candidate {
    private final BufferedImage image;
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final int zoom;
    private final int verticalOffset;
    private final int score;
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class Framing {
    private final int zoom;
    private final int verticalOffset;
    private final int cropX;
    private final int cropY;
    private final int cropWidth;
    private final int cropHeight;
  }
}
