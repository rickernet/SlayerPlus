package com.slayerplus;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Rasterizer;

public final class PlayerPortraitRenderer {
  private static final int CAPTURE_SIZE = 448;
  private static final int OUTPUT_SIZE = 140;
  private static final int FINAL_RENDER_SCALE = 3;
  private static final int FINAL_CONTENT_PADDING = 4;
  private static final int DOWNSAMPLE_INTERMEDIATE_SCALE = 2;
  private static final int LOCKED_SCALE_TOP_SOURCE_ROWS = 32;
  private static final int CAPTURE_TOP_SOURCE_ROWS = 52;
  private static final int LOCKED_SIDE_SOURCE_COLUMNS = 12;
  private static final int SUPERSAMPLED_CAPTURE_TOP_MARGIN = 16;
  private static final int LOCKED_OUTPUT_UPWARD_SHIFT = 0;
  private static final int FRONT_YAW = 0;
  private static final int CLEAR_RGB = 0x010203;
  private static final double HEAD_TURN_RADIANS = Math.toRadians(11.0);
  private static final int NECK_SCAN_BINS = 72;
  private static final int EXTENDED_CAPTURE_EXTRA = 448;
  private static final int TOP_SOURCE_WINDOW_SHIFT = 0;
  private static final int TOP_SOURCE_EXTENSION_ROWS = 64;
  private static final int[] ZOOM_LEVELS = {120, 145, 170, 200, 230, 260, 300, 340, 380};
  private final Client client;
  private Framing lockedFraming;
  private ContentBounds lockedContentBounds;

  public PlayerPortraitRenderer(Client client) {
    this.client = client;
  }

  public BufferedImage render(
      Model displayInputModel, Model bodyInputModel, boolean allowCalibration) {
    if (displayInputModel == null || bodyInputModel == null) {
      return null;
    }
    Model displayUnskewed = displayInputModel.getUnskewedModel();
    Model displayModel = displayUnskewed != null ? displayUnskewed : displayInputModel;
    Model bodyUnskewed = bodyInputModel.getUnskewedModel();
    Model bodyModel = bodyUnskewed != null ? bodyUnskewed : bodyInputModel;
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
    NeckTurn neckTurn = null;
    try {
      rasterizer.setDrawRegion(
          captureX, captureY, captureX + CAPTURE_SIZE, captureY + CAPTURE_SIZE);
      bodyModel.calculateBoundsCylinder();
      displayModel.calculateBoundsCylinder();
      if (lockedFraming == null) {
        if (!allowCalibration) {
          return null;
        }
        lockedFraming =
            findInitialFraming(bodyModel, rasterizer, pixels, rasterWidth, captureX, captureY);
      }
      if (lockedFraming == null) {
        return null;
      }
      if (lockedContentBounds == null) {
        var calibrationCapture =
            renderCaptureSized(
                bodyModel,
                rasterizer,
                pixels,
                rasterWidth,
                captureX,
                captureY,
                CAPTURE_SIZE,
                lockedFraming);
        var calibrationPortrait =
            copyLockedCrop(calibrationCapture, lockedFraming, allowCalibration, 0, 0);
        if (calibrationPortrait == null) {
          return null;
        }
      }
      int availableTopShift = Math.max(0, extendedOffsetY + lockedFraming.cropY);
      int appliedTopShift = Math.min(TOP_SOURCE_WINDOW_SHIFT, availableTopShift);
      Framing renderFraming = createExtendedFraming(lockedFraming, appliedTopShift);
      int projectionZoom = Math.max(1, client.get3dZoom());
      Framing supersampledFraming =
          createSupersampledFraming(lockedFraming, lockedContentBounds, projectionZoom);
      var bodyFrontCapture =
          renderCaptureSized(
              bodyModel,
              rasterizer,
              pixels,
              rasterWidth,
              extendedCaptureX,
              extendedCaptureY,
              extendedCaptureSize,
              supersampledFraming);
      neckTurn = applyTemporaryNeckTurn(bodyModel);
      var turnedBodyCapture =
          renderCaptureSized(
              bodyModel,
              rasterizer,
              pixels,
              rasterWidth,
              extendedCaptureX,
              extendedCaptureY,
              extendedCaptureSize,
              supersampledFraming);
      if (neckTurn != null) {
        neckTurn.restore();
        neckTurn = null;
      }
      var fullCapture =
          renderCaptureSized(
              displayModel,
              rasterizer,
              pixels,
              rasterWidth,
              extendedCaptureX,
              extendedCaptureY,
              extendedCaptureSize,
              supersampledFraming);
      var supersampledPortrait =
          renderSupersampledComposite(
              turnedBodyCapture,
              fullCapture,
              bodyFrontCapture,
              renderFraming,
              lockedFraming,
              lockedContentBounds,
              extendedCaptureSize,
              FINAL_RENDER_SCALE);
      if (hasSufficientVisiblePixels(supersampledPortrait)) {
        return supersampledPortrait;
      }
      var fallbackBodyFront =
          renderCaptureSized(
              bodyModel,
              rasterizer,
              pixels,
              rasterWidth,
              extendedCaptureX,
              extendedCaptureY,
              extendedCaptureSize,
              lockedFraming);
      neckTurn = applyTemporaryNeckTurn(bodyModel);
      var fallbackTurnedCapture =
          renderCaptureSized(
              bodyModel,
              rasterizer,
              pixels,
              rasterWidth,
              extendedCaptureX,
              extendedCaptureY,
              extendedCaptureSize,
              lockedFraming);
      if (neckTurn != null) {
        neckTurn.restore();
        neckTurn = null;
      }
      var fallbackTurnedPortrait =
          copyLockedCrop(
              fallbackTurnedCapture, renderFraming, false, extendedOffsetX, extendedOffsetY);
      var fallbackFull =
          renderCaptureSized(
              displayModel,
              rasterizer,
              pixels,
              rasterWidth,
              extendedCaptureX,
              extendedCaptureY,
              extendedCaptureSize,
              lockedFraming);
      return overlayEquipmentDifferenceFromCapture(
          fallbackTurnedPortrait,
          fallbackFull,
          fallbackBodyFront,
          renderFraming,
          lockedContentBounds,
          extendedCaptureSize,
          extendedOffsetX,
          extendedOffsetY);
    } finally {
      try {
        if (neckTurn != null) {
          neckTurn.restore();
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

  private static Framing createExtendedFraming(Framing framing, int topShift) {
    return new Framing(
        framing.zoom,
        framing.verticalOffset,
        framing.cropX,
        framing.cropY - topShift,
        framing.cropWidth,
        framing.cropHeight);
  }

  private static Framing createSupersampledFraming(
      Framing framing, ContentBounds contentBounds, int projectionZoom) {
    double contentCenterY = framing.cropY + (contentBounds.minY + contentBounds.maxY + 1) / 2.0;
    return new Framing(
        supersampledZoom(framing.zoom),
        supersampledVerticalOffset(
                framing.verticalOffset, framing.zoom, contentCenterY, projectionZoom)
            + supersampledCaptureMarginOffset(framing.zoom, projectionZoom),
        framing.cropX,
        framing.cropY,
        framing.cropWidth,
        framing.cropHeight);
  }

  static int supersampledCaptureMarginOffset(int calibratedZoom, int projectionZoom) {
    if (projectionZoom <= 0) {
      return 0;
    }
    return (int)
        Math.round(
            (double) SUPERSAMPLED_CAPTURE_TOP_MARGIN
                * supersampledZoom(calibratedZoom)
                / projectionZoom);
  }

  static int supersampledVerticalOffset(
      int calibratedVerticalOffset,
      int calibratedZoom,
      double calibratedContentCenterY,
      int projectionZoom) {
    if (projectionZoom <= 0) {
      return calibratedVerticalOffset;
    }
    double centerCorrection = CAPTURE_SIZE / 2.0 - calibratedContentCenterY;
    return calibratedVerticalOffset
        + (int) Math.round(centerCorrection * calibratedZoom / projectionZoom);
  }

  static int supersampledZoom(int calibratedZoom) {
    return Math.max(1, (int) Math.round((double) calibratedZoom / FINAL_RENDER_SCALE));
  }

  static double projectCalibrationCoordinate(
      double calibrationCoordinate, int captureSize, int renderScale) {
    return captureSize / 2.0 + (calibrationCoordinate - CAPTURE_SIZE / 2.0) * renderScale;
  }

  static double projectCalibrationCoordinate(
      double calibrationCoordinate, double calibrationAnchor, int captureSize, int renderScale) {
    return captureSize / 2.0 + (calibrationCoordinate - calibrationAnchor) * renderScale;
  }

  static boolean hasSufficientVisiblePixels(BufferedImage image) {
    if (image == null) {
      return false;
    }
    int minimumVisiblePixels = Math.max(32, image.getWidth() * image.getHeight() / 100);
    int visiblePixels = 0;
    int minX = image.getWidth();
    int minY = image.getHeight();
    int maxX = -1;
    int maxY = -1;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) > 16) {
          visiblePixels++;
          minX = Math.min(minX, x);
          minY = Math.min(minY, y);
          maxX = Math.max(maxX, x);
          maxY = Math.max(maxY, y);
        }
      }
    }
    return visiblePixels >= minimumVisiblePixels
        && maxX - minX + 1 >= image.getWidth() / 8
        && maxY - minY + 1 >= image.getHeight() / 2;
  }

  private static BufferedImage renderSupersampledComposite(
      BufferedImage turnedBodyCapture,
      BufferedImage fullCapture,
      BufferedImage bodyFrontCapture,
      Framing framing,
      Framing calibrationFraming,
      ContentBounds contentBounds,
      int captureSize,
      int renderScale) {
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
        || bodyFrontCapture.getHeight() != captureSize) {
      return null;
    }
    boolean[] equipmentMask = new boolean[captureSize * captureSize];
    for (int y = 0; y < captureSize; y++) {
      for (int x = 0; x < captureSize; x++) {
        int index = y * captureSize + x;
        equipmentMask[index] =
            isCaptureEquipmentDifference(fullCapture.getRGB(x, y), bodyFrontCapture.getRGB(x, y));
      }
    }
    boolean[] expandedMask = expandMask(equipmentMask, captureSize);
    int visibleWidth = contentBounds.maxX - contentBounds.minX + 1;
    int visibleHeight = contentBounds.maxY - contentBounds.minY + 1;
    int availableTopSourceRows = CAPTURE_TOP_SOURCE_ROWS;
    int extendedVisibleHeight = visibleHeight + availableTopSourceRows;
    double baseLeft = framing.cropX + contentBounds.minX - LOCKED_SIDE_SOURCE_COLUMNS;
    double baseTop = framing.cropY + contentBounds.minY - availableTopSourceRows;
    double baseRight =
        framing.cropX + contentBounds.minX + visibleWidth + LOCKED_SIDE_SOURCE_COLUMNS;
    double baseBottom = baseTop + extendedVisibleHeight;
    double calibrationVerticalAnchor =
        calibrationFraming.cropY + (contentBounds.minY + contentBounds.maxY + 1) / 2.0;
    int sourceLeft =
        (int) Math.floor(projectCalibrationCoordinate(baseLeft, captureSize, renderScale));
    int sourceTop =
        (int)
            Math.floor(
                projectCalibrationCoordinate(
                        baseTop, calibrationVerticalAnchor, captureSize, renderScale)
                    + SUPERSAMPLED_CAPTURE_TOP_MARGIN);
    int sourceRight =
        (int) Math.ceil(projectCalibrationCoordinate(baseRight, captureSize, renderScale));
    int sourceBottom =
        (int)
            Math.ceil(
                projectCalibrationCoordinate(
                        baseBottom, calibrationVerticalAnchor, captureSize, renderScale)
                    + SUPERSAMPLED_CAPTURE_TOP_MARGIN);
    int sourceWidth = Math.max(1, sourceRight - sourceLeft);
    int sourceHeight = Math.max(1, sourceBottom - sourceTop);
    var compositeSource =
        new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB_PRE);
    int firstOpaqueRow = sourceHeight;
    int lastOpaqueRow = -1;
    for (int y = 0; y < sourceHeight; y++) {
      int captureY = sourceTop + y;
      if (captureY < 0 || captureY >= captureSize) {
        continue;
      }
      for (int x = 0; x < sourceWidth; x++) {
        int captureX = sourceLeft + x;
        if (captureX < 0 || captureX >= captureSize) {
          continue;
        }
        int index = captureY * captureSize + captureX;
        int full = fullCapture.getRGB(captureX, captureY);
        int body = turnedBodyCapture.getRGB(captureX, captureY);
        int selected = expandedMask[index] && (full & 0x00FFFFFF) != CLEAR_RGB ? full : body;
        int rgb = selected & 0x00FFFFFF;
        if (rgb != CLEAR_RGB) {
          compositeSource.setRGB(x, y, 0xFF000000 | rgb);
          firstOpaqueRow = Math.min(firstOpaqueRow, y);
          lastOpaqueRow = Math.max(lastOpaqueRow, y);
        }
      }
    }
    if (lastOpaqueRow < firstOpaqueRow) {
      return null;
    }
    int availableWidth = OUTPUT_SIZE - FINAL_CONTENT_PADDING;
    int lockedScaleSourceHeight = lockedScaleSourceHeight(sourceHeight, renderScale);
    double outputScale =
        Math.min(
            (double) availableWidth / sourceWidth, (double) OUTPUT_SIZE / lockedScaleSourceHeight);
    int drawWidth = Math.max(1, (int) Math.round(sourceWidth * outputScale));
    int drawHeight = Math.max(1, (int) Math.round(sourceHeight * outputScale));
    int targetX = (OUTPUT_SIZE - drawWidth) / 2;
    int targetY = lockedOutputTargetY(drawHeight);
    var intermediate =
        resamplePremultiplied(
            compositeSource,
            drawWidth * DOWNSAMPLE_INTERMEDIATE_SCALE,
            drawHeight * DOWNSAMPLE_INTERMEDIATE_SCALE);
    var refinedPortrait = resamplePremultiplied(intermediate, drawWidth, drawHeight);
    var clarifiedPortrait = applyPortraitFinish(refinedPortrait);
    var output = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB_PRE);
    var graphics = output.createGraphics();
    graphics.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.drawImage(clarifiedPortrait, targetX, targetY, null);
    graphics.dispose();
    return output;
  }

  static int lockedScaleSourceHeight(int capturedSourceHeight, int renderScale) {
    return Math.max(
        1,
        capturedSourceHeight
            - (CAPTURE_TOP_SOURCE_ROWS - LOCKED_SCALE_TOP_SOURCE_ROWS) * Math.max(1, renderScale));
  }

  static int lockedOutputTargetY(int drawHeight) {
    return OUTPUT_SIZE - Math.max(1, drawHeight) - LOCKED_OUTPUT_UPWARD_SHIFT;
  }

  private static BufferedImage applyPortraitFinish(BufferedImage source) {
    var clarified = applyInteriorClarity(source);
    int[] bounds = portraitOpaqueBounds(clarified);
    var finished =
        new BufferedImage(
            clarified.getWidth(), clarified.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
    for (int y = 0; y < clarified.getHeight(); y++) {
      for (int x = 0; x < clarified.getWidth(); x++) {
        int argb = clarified.getRGB(x, y);
        int alpha = argb >>> 24;
        if (alpha == 0) {
          continue;
        }
        double sourceRed = (argb >>> 16) & 0xFF;
        double sourceGreen = (argb >>> 8) & 0xFF;
        double sourceBlue = argb & 0xFF;
        double luminance = portraitLuminance(sourceRed, sourceGreen, sourceBlue);
        double light =
            alpha >= 160
                ? portraitDirectionalLight(x, y, bounds)
                    + portraitLocalRelief(clarified, x, y, luminance)
                : 0.0;
        double red = shadedPortraitChannel(sourceRed, light);
        double green = shadedPortraitChannel(sourceGreen, light);
        double blue = shadedPortraitChannel(sourceBlue, light);
        double maximum = Math.max(red, Math.max(green, blue));
        double minimum = Math.min(red, Math.min(green, blue));
        double chroma = (maximum - minimum) / 255.0;
        double vibrance = 1.10 - 0.04 * chroma;
        int finishedRed = portraitChannel(luminance + (red - luminance) * vibrance);
        int finishedGreen = portraitChannel(luminance + (green - luminance) * vibrance);
        int finishedBlue = portraitChannel(luminance + (blue - luminance) * vibrance);
        finished.setRGB(
            x, y, (alpha << 24) | (finishedRed << 16) | (finishedGreen << 8) | finishedBlue);
      }
    }
    return finished;
  }

  private static BufferedImage applyInteriorClarity(BufferedImage source) {
    var output =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
    var graphics = output.createGraphics();
    graphics.drawImage(source, 0, 0, null);
    graphics.dispose();
    for (int y = 1; y < source.getHeight() - 1; y++) {
      for (int x = 1; x < source.getWidth() - 1; x++) {
        int center = source.getRGB(x, y);
        if (!isSolidPortraitPixel(center)
            || !isSolidPortraitPixel(source.getRGB(x - 1, y))
            || !isSolidPortraitPixel(source.getRGB(x + 1, y))
            || !isSolidPortraitPixel(source.getRGB(x, y - 1))
            || !isSolidPortraitPixel(source.getRGB(x, y + 1))) {
          continue;
        }
        int alpha = center >>> 24;
        int red = interiorClarityChannel(source, x, y, 16);
        int green = interiorClarityChannel(source, x, y, 8);
        int blue = interiorClarityChannel(source, x, y, 0);
        output.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
      }
    }
    return output;
  }

  private static boolean isSolidPortraitPixel(int argb) {
    return (argb >>> 24) >= 224;
  }

  private static int interiorClarityChannel(BufferedImage source, int x, int y, int shift) {
    double center = (source.getRGB(x, y) >>> shift) & 0xFF;
    double neighbours =
        ((source.getRGB(x - 1, y) >>> shift) & 0xFF)
            + ((source.getRGB(x + 1, y) >>> shift) & 0xFF)
            + ((source.getRGB(x, y - 1) >>> shift) & 0xFF)
            + ((source.getRGB(x, y + 1) >>> shift) & 0xFF);
    return clampColor(center * 1.04 - neighbours * 0.01);
  }

  private static int[] portraitOpaqueBounds(BufferedImage image) {
    int minX = image.getWidth();
    int minY = image.getHeight();
    int maxX = -1;
    int maxY = -1;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) < 32) {
          continue;
        }
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
      }
    }
    if (maxX < minX || maxY < minY) {
      return new int[] {0, 0, image.getWidth() - 1, image.getHeight() - 1};
    }
    return new int[] {minX, minY, maxX, maxY};
  }

  static double portraitDirectionalLight(int x, int y, int[] bounds) {
    double width = Math.max(1.0, bounds[2] - bounds[0]);
    double height = Math.max(1.0, bounds[3] - bounds[1]);
    double normalizedX = (x - bounds[0]) / width;
    double normalizedY = (y - bounds[1]) / height;
    return (0.5 - normalizedX) * 3.0 + (0.5 - normalizedY) * 7.0;
  }

  private static double portraitLocalRelief(
      BufferedImage image, int x, int y, double centerLuminance) {
    if (x <= 0
        || y <= 0
        || x >= image.getWidth() - 1
        || y >= image.getHeight() - 1
        || !isSolidPortraitPixel(image.getRGB(x - 1, y))
        || !isSolidPortraitPixel(image.getRGB(x + 1, y))
        || !isSolidPortraitPixel(image.getRGB(x, y - 1))
        || !isSolidPortraitPixel(image.getRGB(x, y + 1))) {
      return 0.0;
    }
    double neighbourLuminance =
        (portraitLuminance(image.getRGB(x - 1, y))
                + portraitLuminance(image.getRGB(x + 1, y))
                + portraitLuminance(image.getRGB(x, y - 1))
                + portraitLuminance(image.getRGB(x, y + 1)))
            / 4.0;
    return Math.max(-3.5, Math.min(3.5, (centerLuminance - neighbourLuminance) * 0.18));
  }

  private static double portraitLuminance(int argb) {
    return portraitLuminance((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF);
  }

  private static double portraitLuminance(double red, double green, double blue) {
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
  }

  private static double shadedPortraitChannel(double channel, double light) {
    double highlightProtection = light > 0.0 ? 1.0 - channel / 510.0 : 1.0;
    return channel + light * highlightProtection;
  }

  private static int clampColor(double value) {
    return Math.max(0, Math.min(255, (int) Math.round(value)));
  }

  private static int portraitChannel(double value) {
    double contrasted = 127.5 + (value - 127.5) * 1.04;
    double normalized = Math.max(0.0, Math.min(1.0, contrasted / 255.0));
    double lifted = Math.pow(normalized, 0.97) * 255.0;
    return Math.max(0, Math.min(255, (int) Math.round(lifted)));
  }

  private static BufferedImage resamplePremultiplied(BufferedImage source, int width, int height) {
    var output =
        new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB_PRE);
    var graphics = output.createGraphics();
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.drawImage(source, 0, 0, output.getWidth(), output.getHeight(), null);
    graphics.dispose();
    return output;
  }

  private static BufferedImage overlayEquipmentDifferenceFromCapture(
      BufferedImage turnedBody,
      BufferedImage fullCapture,
      BufferedImage bodyCapture,
      Framing framing,
      ContentBounds contentBounds,
      int captureSize,
      int captureOffsetX,
      int captureOffsetY) {
    if (turnedBody == null
        || fullCapture == null
        || bodyCapture == null
        || framing == null
        || contentBounds == null
        || captureSize < CAPTURE_SIZE
        || fullCapture.getWidth() != captureSize
        || fullCapture.getHeight() != captureSize
        || bodyCapture.getWidth() != captureSize
        || bodyCapture.getHeight() != captureSize) {
      return turnedBody;
    }
    int visibleWidth = contentBounds.maxX - contentBounds.minX + 1;
    int visibleHeight = contentBounds.maxY - contentBounds.minY + 1;
    int availableWidth = OUTPUT_SIZE - 4;
    int availableHeight = OUTPUT_SIZE - 3;
    double scale =
        Math.min((double) availableWidth / visibleWidth, (double) availableHeight / visibleHeight);
    int drawWidth = Math.max(1, (int) Math.round(visibleWidth * scale));
    int drawHeight = Math.max(1, (int) Math.round(visibleHeight * scale));
    int targetX = (OUTPUT_SIZE - drawWidth) / 2;
    int targetY = OUTPUT_SIZE - drawHeight;
    int absoluteSourceX = framing.cropX + contentBounds.minX;
    int absoluteSourceY = framing.cropY + contentBounds.minY;
    boolean[] equipmentMask = new boolean[captureSize * captureSize];
    for (int y = 0; y < captureSize; y++) {
      for (int x = 0; x < captureSize; x++) {
        int full = fullCapture.getRGB(x, y);
        int body = bodyCapture.getRGB(x, y);
        equipmentMask[y * captureSize + x] = isCaptureEquipmentDifference(full, body);
      }
    }
    boolean[] expandedMask = expandMask(equipmentMask, captureSize);
    var output = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
    var graphics = output.createGraphics();
    graphics.drawImage(turnedBody, 0, 0, null);
    graphics.dispose();
    for (int sourceY = 0; sourceY < captureSize; sourceY++) {
      for (int sourceX = 0; sourceX < captureSize; sourceX++) {
        if (!expandedMask[sourceY * captureSize + sourceX]) {
          continue;
        }
        int equipmentPixel = fullCapture.getRGB(sourceX, sourceY);
        int equipmentRgb = equipmentPixel & 0x00FFFFFF;
        if (equipmentRgb == CLEAR_RGB) {
          continue;
        }
        double bodySourceX = sourceX - captureOffsetX;
        double bodySourceY = sourceY - captureOffsetY;
        double mappedLeft = targetX + (bodySourceX - absoluteSourceX) * scale;
        double mappedTop = targetY + (bodySourceY - absoluteSourceY) * scale;
        double mappedRight = targetX + (bodySourceX + 1 - absoluteSourceX) * scale;
        double mappedBottom = targetY + (bodySourceY + 1 - absoluteSourceY) * scale;
        int destinationMinX = (int) Math.floor(mappedLeft);
        int destinationMinY = (int) Math.floor(mappedTop);
        int destinationMaxX = (int) Math.ceil(mappedRight) - 1;
        int destinationMaxY = (int) Math.ceil(mappedBottom) - 1;
        for (int destinationY = destinationMinY; destinationY <= destinationMaxY; destinationY++) {
          if (destinationY < 0 || destinationY >= OUTPUT_SIZE) {
            continue;
          }
          for (int destinationX = destinationMinX;
              destinationX <= destinationMaxX;
              destinationX++) {
            if (destinationX < 0 || destinationX >= OUTPUT_SIZE) {
              continue;
            }
            output.setRGB(
                destinationX,
                destinationY,
                alphaComposite(
                    output.getRGB(destinationX, destinationY), 0xFF000000 | equipmentRgb));
          }
        }
      }
    }
    return output;
  }

  static boolean[] expandMask(boolean[] source, int width) {
    if (source == null || width <= 0 || source.length != width * width) {
      return new boolean[0];
    }
    boolean[] expanded = source.clone();
    for (int y = 0; y < width; y++) {
      for (int x = 0; x < width; x++) {
        if (!source[y * width + x]) {
          continue;
        }
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
          int expandedY = y + offsetY;
          if (expandedY < 0 || expandedY >= width) {
            continue;
          }
          for (int offsetX = -1; offsetX <= 1; offsetX++) {
            int expandedX = x + offsetX;
            if (expandedX >= 0 && expandedX < width) {
              expanded[expandedY * width + expandedX] = true;
            }
          }
        }
      }
    }
    return expanded;
  }

  private static boolean isCaptureEquipmentDifference(int full, int body) {
    int fullRgb = full & 0x00FFFFFF;
    int bodyRgb = body & 0x00FFFFFF;
    boolean fullVisible = fullRgb != CLEAR_RGB;
    boolean bodyVisible = bodyRgb != CLEAR_RGB;
    if (!fullVisible) {
      return false;
    }
    if (!bodyVisible) {
      return true;
    }
    int redDifference = Math.abs(((fullRgb >>> 16) & 0xFF) - ((bodyRgb >>> 16) & 0xFF));
    int greenDifference = Math.abs(((fullRgb >>> 8) & 0xFF) - ((bodyRgb >>> 8) & 0xFF));
    int blueDifference = Math.abs((fullRgb & 0xFF) - (bodyRgb & 0xFF));
    return redDifference + greenDifference + blueDifference > 48;
  }

  private static int alphaComposite(int background, int foreground) {
    int foregroundAlpha = (foreground >>> 24) & 0xFF;
    if (foregroundAlpha >= 255) {
      return foreground;
    }
    if (foregroundAlpha <= 0) {
      return background;
    }
    int backgroundAlpha = (background >>> 24) & 0xFF;
    double foregroundWeight = foregroundAlpha / 255.0;
    double backgroundWeight = (backgroundAlpha / 255.0) * (1.0 - foregroundWeight);
    double outputAlpha = foregroundWeight + backgroundWeight;
    if (outputAlpha <= 0.0) {
      return 0;
    }
    int red =
        (int)
            Math.round(
                (((foreground >>> 16) & 0xFF) * foregroundWeight
                        + ((background >>> 16) & 0xFF) * backgroundWeight)
                    / outputAlpha);
    int green =
        (int)
            Math.round(
                (((foreground >>> 8) & 0xFF) * foregroundWeight
                        + ((background >>> 8) & 0xFF) * backgroundWeight)
                    / outputAlpha);
    int blue =
        (int)
            Math.round(
                ((foreground & 0xFF) * foregroundWeight + (background & 0xFF) * backgroundWeight)
                    / outputAlpha);
    int alpha = (int) Math.round(outputAlpha * 255.0);
    return (alpha << 24) | (red << 16) | (green << 8) | blue;
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

  private static NeckTurn applyTemporaryNeckTurn(Model model) {
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
    double direction = Math.random() < .5 ? 1.0 : -1.0;
    float fullTurnY = neckY - Math.max(3.0f, modelHeight / 45.0f);
    float stationaryY = neckY + Math.max(7.0f, modelHeight / 18.0f);
    float transitionHeight = Math.max(1.0f, stationaryY - fullTurnY);
    for (int vertex = 0; vertex < vertexCount; vertex++) {
      float y = originalY[vertex];
      double weight;
      if (y <= fullTurnY) {
        weight = 1.0;
      } else if (y >= stationaryY) {
        weight = 0.0;
      } else {
        double linear = (stationaryY - y) / transitionHeight;
        weight = linear * linear * (3.0 - 2.0 * linear);
      }
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

  private static float findNeckY(
      float[] verticesX,
      float[] verticesY,
      float[] verticesZ,
      int vertexCount,
      float minimumY,
      float maximumY) {
    float[] binMinimumX = new float[NECK_SCAN_BINS];
    float[] binMaximumX = new float[NECK_SCAN_BINS];
    float[] binMinimumZ = new float[NECK_SCAN_BINS];
    float[] binMaximumZ = new float[NECK_SCAN_BINS];
    int[] binCounts = new int[NECK_SCAN_BINS];
    Arrays.fill(binMinimumX, Float.POSITIVE_INFINITY);
    Arrays.fill(binMaximumX, Float.NEGATIVE_INFINITY);
    Arrays.fill(binMinimumZ, Float.POSITIVE_INFINITY);
    Arrays.fill(binMaximumZ, Float.NEGATIVE_INFINITY);
    float modelHeight = Math.max(1.0f, maximumY - minimumY);
    for (int vertex = 0; vertex < vertexCount; vertex++) {
      float relativeY = verticesY[vertex] - minimumY;
      int bin =
          clamp((int) (relativeY * NECK_SCAN_BINS / (modelHeight + 1.0f)), 0, NECK_SCAN_BINS - 1);
      binMinimumX[bin] = Math.min(binMinimumX[bin], verticesX[vertex]);
      binMaximumX[bin] = Math.max(binMaximumX[bin], verticesX[vertex]);
      binMinimumZ[bin] = Math.min(binMinimumZ[bin], verticesZ[vertex]);
      binMaximumZ[bin] = Math.max(binMaximumZ[bin], verticesZ[vertex]);
      binCounts[bin]++;
    }
    double[] widths = new double[NECK_SCAN_BINS];
    for (int bin = 0; bin < NECK_SCAN_BINS; bin++) {
      if (binCounts[bin] < 2) {
        widths[bin] = Double.NaN;
        continue;
      }
      float spanX = binMaximumX[bin] - binMinimumX[bin];
      float spanZ = binMaximumZ[bin] - binMinimumZ[bin];
      widths[bin] = Math.max(spanX, spanZ);
    }
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
      double positionPenalty = Math.abs(position - 0.48) * 28.0;
      double wideningReward = (widestBelow - width) * 0.16;
      double score = width + positionPenalty - wideningReward;
      if (score < bestScore) {
        bestScore = score;
        bestBin = bin;
      }
    }
    if (bestBin < 0) {
      return minimumY + modelHeight * 0.48f;
    }
    return minimumY + (float) ((bestBin + 0.5) * modelHeight / NECK_SCAN_BINS);
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
      if (Math.abs(verticesY[vertex] - neckY) > neckBand) {
        continue;
      }
      if (Math.abs(verticesX[vertex] - defaultX) > xLimit
          || Math.abs(verticesZ[vertex] - defaultZ) > zLimit) {
        continue;
      }
      totalX += verticesX[vertex];
      totalZ += verticesZ[vertex];
      samples++;
    }
    if (samples < 3) {
      return new float[] {defaultX, defaultZ};
    }
    return new float[] {(float) (totalX / samples), (float) (totalZ / samples)};
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

  private static final class ContentBounds {
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;

    private ContentBounds(int minX, int minY, int maxX, int maxY) {
      this.minX = minX;
      this.minY = minY;
      this.maxX = maxX;
      this.maxY = maxY;
    }
  }

  private static final class NeckTurn {
    private final float[] verticesX;
    private final float[] verticesY;
    private final float[] verticesZ;
    private final float[] originalX;
    private final float[] originalY;
    private final float[] originalZ;
    private final int vertexCount;

    private NeckTurn(
        float[] verticesX,
        float[] verticesY,
        float[] verticesZ,
        float[] originalX,
        float[] originalY,
        float[] originalZ,
        int vertexCount) {
      this.verticesX = verticesX;
      this.verticesY = verticesY;
      this.verticesZ = verticesZ;
      this.originalX = originalX;
      this.originalY = originalY;
      this.originalZ = originalZ;
      this.vertexCount = vertexCount;
    }

    private void restore() {
      System.arraycopy(originalX, 0, verticesX, 0, vertexCount);
      System.arraycopy(originalY, 0, verticesY, 0, vertexCount);
      System.arraycopy(originalZ, 0, verticesZ, 0, vertexCount);
    }
  }

  private static final class Candidate {
    private final BufferedImage image;
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final int zoom;
    private final int verticalOffset;
    private final int score;

    private Candidate(
        BufferedImage image,
        int minX,
        int minY,
        int maxX,
        int maxY,
        int zoom,
        int verticalOffset,
        int score) {
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

  private static final class Framing {
    private final int zoom;
    private final int verticalOffset;
    private final int cropX;
    private final int cropY;
    private final int cropWidth;
    private final int cropHeight;

    private Framing(
        int zoom, int verticalOffset, int cropX, int cropY, int cropWidth, int cropHeight) {
      this.zoom = zoom;
      this.verticalOffset = verticalOffset;
      this.cropX = cropX;
      this.cropY = cropY;
      this.cropWidth = cropWidth;
      this.cropHeight = cropHeight;
    }
  }
}
