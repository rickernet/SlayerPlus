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
import java.awt.geom.*;
import java.awt.image.*;
import java.time.LocalTime;
import javax.swing.*;

public class PlayerPortraitPanel extends JPanel {
  private static final int WIDTH = 144;
  private static final int HEIGHT = 144;
  private static final int PORTRAIT_SIZE = 140;
  private static final int ARC = 11;
  private static final int CLOCK_REFRESH_MS = 60_000;
  private static final int FADE_FRAME_MS = 33;
  private static final int FADE_DURATION_MS = 230;
  private static final int SUNRISE_MINUTE = 5 * 60 + 30;
  private static final int SUNSET_MINUTE = 20 * 60 + 30;
  private static final Color INNER_HIGHLIGHT = new Color(255, 255, 255, 38);
  private final Timer clockTimer;
  private final Timer fadeTimer;
  private BufferedImage portrait;
  private BufferedImage portraitShadow;
  private BufferedImage previousPortrait;
  private BufferedImage previousPortraitShadow;
  private long fadeStartedAtNanos;
  private boolean portraitIsDark;

  public PlayerPortraitPanel() {
    setOpaque(false);
    setPreferredSize(new Dimension(WIDTH, HEIGHT));
    setMinimumSize(new Dimension(WIDTH, HEIGHT));
    setMaximumSize(new Dimension(WIDTH, HEIGHT));
    clockTimer = new Timer(CLOCK_REFRESH_MS, event -> repaint());
    clockTimer.setRepeats(true);
    fadeTimer = new Timer(FADE_FRAME_MS, event -> advanceFade());
    fadeTimer.setRepeats(true);
  }

  public void setPortrait(BufferedImage portrait) {
    fadeTimer.stop();
    this.previousPortrait = this.portrait;
    this.previousPortraitShadow = this.portraitShadow;
    this.portrait = portrait;
    this.portraitShadow = portrait == null ? null : createShadow(portrait);
    this.portraitIsDark = portrait != null && isMostlyDark(portrait);
    if (portrait != null && previousPortrait != null) {
      fadeStartedAtNanos = System.nanoTime();
      if (isDisplayable()) {
        fadeTimer.start();
      } else {
        clearPreviousPortrait();
      }
    } else {
      clearPreviousPortrait();
    }
    repaint();
  }

  @Override
  public void removeNotify() {
    disposeTimers();
    super.removeNotify();
  }

  public void disposeTimers() {
    clockTimer.stop();
    fadeTimer.stop();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (!clockTimer.isRunning()) {
      clockTimer.start();
    }
    if (previousPortrait != null && portrait != null && fadeProgress() < 1.0f) {
      fadeTimer.start();
    }
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    LocalTime now = LocalTime.now();
    TimePalette palette = TimePalette.forTime(now);
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    Shape frame = new RoundRectangle2D.Float(1, 1, WIDTH - 3, HEIGHT - 3, ARC, ARC);
    g.setClip(frame);
    LinearGradientPaint sky =
        new LinearGradientPaint(
            new Point2D.Float(0, 0),
            new Point2D.Float(0, HEIGHT),
            new float[] {0.0f, 0.58f, 1.0f},
            new Color[] {palette.skyTop, palette.skyMiddle, palette.skyBottom});
    g.setPaint(sky);
    g.fillRect(0, 0, WIDTH, HEIGHT);
    RadialGradientPaint glow =
        new RadialGradientPaint(
            new Point2D.Float(palette.glowX, palette.glowY),
            palette.glowRadius,
            new float[] {0.0f, 0.72f, 1.0f},
            new Color[] {palette.glowCenter, palette.glowMiddle, new Color(0, 0, 0, 0)});
    g.setPaint(glow);
    g.fillRect(0, 0, WIDTH, HEIGHT);
    drawCelestialBody(g, now);
    g.setPaint(
        new LinearGradientPaint(
            new Point2D.Float(0, HEIGHT * 0.68f),
            new Point2D.Float(0, HEIGHT),
            new float[] {0.0f, 1.0f},
            new Color[] {new Color(255, 255, 255, 0), palette.lowerHaze}));
    g.fillRect(0, 0, WIDTH, HEIGHT);
    g.setColor(new Color(3, 7, 12, 54));
    g.fillOval(14, HEIGHT - 27, WIDTH - 28, 28);
    g.setColor(new Color(226, 239, 248, 22));
    g.drawArc(18, HEIGHT - 24, WIDTH - 36, 18, 8, 164);
    if (portrait != null) {
      g.setPaint(
          new RadialGradientPaint(
              new Point2D.Float(WIDTH / 2.0f, HEIGHT * 0.48f),
              58.0f,
              new float[] {0.0f, 0.74f, 1.0f},
              new Color[] {
                portraitIsDark ? new Color(235, 246, 255, 48) : new Color(2, 7, 13, 40),
                portraitIsDark ? new Color(190, 218, 238, 13) : new Color(4, 10, 18, 12),
                new Color(0, 0, 0, 0)
              }));
      g.fillRect(0, 0, WIDTH, HEIGHT);
    }
    int drawX = (WIDTH - PORTRAIT_SIZE) / 2;
    int drawY = (HEIGHT - PORTRAIT_SIZE) / 2;
    if (portrait != null) {
      float progress = fadeProgress();
      if (previousPortrait != null && progress < 1.0f) {
        drawPortraitLayer(
            g, previousPortrait, previousPortraitShadow, drawX, drawY, 1.0f - progress);
      }
      drawPortraitLayer(g, portrait, portraitShadow, drawX, drawY, progress);
    } else {
      int placeholderCenterX = WIDTH / 2;
      int headWidth = 34;
      int bodyWidth = 64;
      g.setColor(palette.placeholder);
      g.fillOval(placeholderCenterX - headWidth / 2, 27, headWidth, 38);
      g.fillRoundRect(placeholderCenterX - bodyWidth / 2, 65, bodyWidth, 35, 18, 18);
    }
    RadialGradientPaint vignette =
        new RadialGradientPaint(
            new Point2D.Float(WIDTH / 2.0f, HEIGHT / 2.0f),
            88.0f,
            new float[] {0.56f, 1.0f},
            new Color[] {new Color(0, 0, 0, 0), palette.vignette});
    g.setPaint(vignette);
    g.fillRect(0, 0, WIDTH, HEIGHT);
    g.setClip(null);
    g.setColor(palette.border);
    g.setStroke(new BasicStroke(1.4f));
    g.draw(frame);
    g.setColor(INNER_HIGHLIGHT);
    g.setStroke(new BasicStroke(1.0f));
    g.draw(new RoundRectangle2D.Float(2.5f, 2.5f, WIDTH - 6, HEIGHT - 6, ARC - 2, ARC - 2));
    g.dispose();
  }

  private static void drawCelestialBody(Graphics2D graphics, LocalTime time) {
    float minute = time.getHour() * 60.0f + time.getMinute() + time.getSecond() / 60.0f;
    if (minute >= SUNRISE_MINUTE && minute < SUNSET_MINUTE) {
      float travel = (minute - SUNRISE_MINUTE) / (SUNSET_MINUTE - SUNRISE_MINUTE);
      drawSun(graphics, travel, horizonVisibility(travel));
      return;
    }
    float nightMinute = minute < SUNRISE_MINUTE ? minute + 24.0f * 60.0f : minute;
    float nightLength = 24.0f * 60.0f - SUNSET_MINUTE + SUNRISE_MINUTE;
    float travel = (nightMinute - SUNSET_MINUTE) / nightLength;
    drawMoon(graphics, travel, horizonVisibility(travel));
  }

  private static void drawSun(Graphics2D graphics, float travel, float visibility) {
    float x = celestialX(travel);
    float y = celestialY(travel, 3.0f, 8.0f);
    float horizonWarmth = Math.abs(travel * 2.0f - 1.0f);
    Color sunLight = mixColor(new Color(247, 235, 182), new Color(239, 176, 83), horizonWarmth);
    Color sunMiddle = mixColor(new Color(214, 179, 92), new Color(193, 112, 44), horizonWarmth);
    Color sunEdge = mixColor(new Color(151, 99, 39), new Color(123, 65, 30), horizonWarmth);
    graphics.setPaint(
        new RadialGradientPaint(
            new Point2D.Float(x, y),
            38.0f,
            new float[] {0.0f, 0.42f, 1.0f},
            new Color[] {
              withAlpha(sunLight, 104, visibility),
              withAlpha(sunMiddle, 32, visibility),
              new Color(255, 180, 70, 0)
            }));
    graphics.fill(new Ellipse2D.Float(x - 38, y - 38, 76, 76));
    Shape sun = regularPolygon(x, y, 19.0f, 10, -Math.PI / 2.0);
    graphics.setPaint(
        new LinearGradientPaint(
            new Point2D.Float(x - 8.0f, y - 15.0f),
            new Point2D.Float(x + 8.0f, y + 17.0f),
            new float[] {0.0f, 0.52f, 1.0f},
            new Color[] {
              withAlpha(sunLight, 238, visibility),
              withAlpha(sunMiddle, 242, visibility),
              withAlpha(sunEdge, 238, visibility)
            }));
    graphics.fill(sun);
    graphics.setColor(withAlpha(new Color(255, 229, 157), 62, visibility));
    graphics.fill(facet(x, y, x - 13.0f, y + 4.0f, x - 7.0f, y - 13.0f));
  }

  private static void drawMoon(Graphics2D graphics, float travel, float visibility) {
    float x = celestialX(travel);
    float y = celestialY(travel, 4.0f, 8.0f);
    float horizonTint = Math.abs(travel * 2.0f - 1.0f);
    Color moonLight = mixColor(new Color(199, 214, 228), new Color(181, 181, 211), horizonTint);
    Color moonShade = mixColor(new Color(63, 79, 105), new Color(72, 61, 94), horizonTint);
    graphics.setPaint(
        new RadialGradientPaint(
            new Point2D.Float(x, y),
            37.0f,
            new float[] {0.0f, 0.48f, 1.0f},
            new Color[] {
              withAlpha(new Color(210, 229, 249), 116, visibility),
              withAlpha(new Color(119, 158, 205), 38, visibility),
              new Color(110, 145, 190, 0)
            }));
    graphics.fill(new Ellipse2D.Float(x - 37, y - 37, 74, 74));
    Shape moonBody = regularPolygon(x, y, 20.0f, 10, -Math.PI / 2.0);
    graphics.setColor(withAlpha(moonShade, 116, visibility));
    graphics.fill(moonBody);
    Area crescent = new Area(moonBody);
    crescent.subtract(new Area(regularPolygon(x + 11.0f, y - 3.0f, 20.0f, 10, -Math.PI / 2.0)));
    graphics.setColor(withAlpha(moonLight, 228, visibility));
    graphics.fill(crescent);
    graphics.setColor(withAlpha(new Color(231, 238, 244), 48, visibility));
    graphics.fill(facet(x - 2.0f, y, x - 14.0f, y + 7.0f, x - 12.0f, y - 8.0f));
  }

  private static Shape regularPolygon(
      float centerX, float centerY, float radius, int vertices, double rotation) {
    Path2D.Float polygon = new Path2D.Float();
    for (int vertex = 0; vertex < vertices; vertex++) {
      double angle = rotation + vertex * Math.PI * 2.0 / vertices;
      float x = centerX + (float) Math.cos(angle) * radius;
      float y = centerY + (float) Math.sin(angle) * radius;
      if (vertex == 0) {
        polygon.moveTo(x, y);
      } else {
        polygon.lineTo(x, y);
      }
    }
    polygon.closePath();
    return polygon;
  }

  private static Shape facet(
      float firstX, float firstY, float secondX, float secondY, float thirdX, float thirdY) {
    Path2D.Float facet = new Path2D.Float();
    facet.moveTo(firstX, firstY);
    facet.lineTo(secondX, secondY);
    facet.lineTo(thirdX, thirdY);
    facet.closePath();
    return facet;
  }

  private static float celestialX(float travel) {
    return 16.0f + Math.max(0.0f, Math.min(1.0f, travel)) * (WIDTH - 32.0f);
  }

  private static float celestialY(float travel, float horizon, float arcHeight) {
    return horizon - (float) Math.sin(Math.PI * Math.max(0.0f, Math.min(1.0f, travel))) * arcHeight;
  }

  private static float horizonVisibility(float travel) {
    return Math.min(1.0f, Math.max(0.0f, Math.min(travel, 1.0f - travel) * 20.0f));
  }

  private static Color withAlpha(Color color, int alpha, float visibility) {
    return new Color(
        color.getRed(),
        color.getGreen(),
        color.getBlue(),
        Math.max(0, Math.min(255, Math.round(alpha * visibility))));
  }

  private static Color mixColor(Color from, Color to, float amount) {
    float safeAmount = Math.max(0.0f, Math.min(1.0f, amount));
    return new Color(
        Math.round(from.getRed() + (to.getRed() - from.getRed()) * safeAmount),
        Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * safeAmount),
        Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * safeAmount));
  }

  private void advanceFade() {
    if (fadeProgress() >= 1.0f) {
      fadeTimer.stop();
      clearPreviousPortrait();
    }
    repaint();
  }

  private float fadeProgress() {
    if (previousPortrait == null || portrait == null) {
      return 1.0f;
    }
    long elapsedNanos = System.nanoTime() - fadeStartedAtNanos;
    return Math.min(1.0f, elapsedNanos / (FADE_DURATION_MS * 1_000_000.0f));
  }

  private void clearPreviousPortrait() {
    previousPortrait = null;
    previousPortraitShadow = null;
  }

  private static void drawPortraitLayer(
      Graphics2D graphics, BufferedImage image, BufferedImage shadow, int x, int y, float alpha) {
    if (image == null || alpha <= 0.0f) {
      return;
    }
    if (shadow != null) {
      graphics.setComposite(AlphaComposite.SrcOver.derive(alpha * 0.42f));
      graphics.drawImage(shadow, x, y + 3, null);
    }
    graphics.setComposite(AlphaComposite.SrcOver.derive(alpha));
    graphics.drawImage(image, x, y, null);
    graphics.setComposite(AlphaComposite.SrcOver);
  }

  private static boolean isMostlyDark(BufferedImage image) {
    long weightedLuminance = 0;
    long totalAlpha = 0;
    for (int y = 0; y < image.getHeight(); y += 2) {
      for (int x = 0; x < image.getWidth(); x += 2) {
        int argb = image.getRGB(x, y);
        int alpha = argb >>> 24;
        if (alpha == 0) {
          continue;
        }
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        int luminance = (red * 54 + green * 183 + blue * 19) >> 8;
        weightedLuminance += (long) luminance * alpha;
        totalAlpha += alpha;
      }
    }
    return totalAlpha > 0 && weightedLuminance / totalAlpha < 112;
  }

  private static BufferedImage createShadow(BufferedImage source) {
    BufferedImage silhouette =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        int alpha = source.getRGB(x, y) >>> 24;
        if (alpha > 0) {
          silhouette.setRGB(x, y, (Math.min(150, alpha) << 24));
        }
      }
    }
    float[] kernel = {1, 2, 3, 2, 1, 2, 4, 6, 4, 2, 3, 6, 9, 6, 3, 2, 4, 6, 4, 2, 1, 2, 3, 2, 1};
    float total = 0;
    for (float value : kernel) {
      total += value;
    }
    for (int i = 0; i < kernel.length; i++) {
      kernel[i] /= total;
    }
    ConvolveOp blur = new ConvolveOp(new Kernel(5, 5, kernel), ConvolveOp.EDGE_NO_OP, null);
    return blur.filter(silhouette, null);
  }

  private static final class TimePalette {
    private static final int[] TIMES = {0, 225, 285, 375, 540, 750, 960, 1110, 1230, 1320, 1440};
    private static final TimePalette[] FRAMES = {
      palette(0x1D2F4F, 0x14223B, 0x0A1425, 0.65f, 0.31f, 70),
      palette(0x1D2F4F, 0x14223B, 0x0A1425, 0.65f, 0.31f, 70),
      palette(0x313D67, 0x393D62, 0x1F2440, 0.24f, 0.36f, 73),
      palette(0x717EA8, 0xB77777, 0x564865, 0.22f, 0.40f, 80),
      palette(0x6FA9CB, 0x5286A8, 0x30526B, 0.36f, 0.36f, 74),
      palette(0x70B0D6, 0x4985AE, 0x254867, 0.52f, 0.32f, 72),
      palette(0x6999C4, 0x557A9E, 0x304763, 0.68f, 0.36f, 75),
      palette(0x5B6297, 0xB45E67, 0x4E2E45, 0.78f, 0.42f, 80),
      palette(0x373E69, 0x513A5B, 0x221F37, 0.80f, 0.36f, 74),
      palette(0x1D2F4F, 0x14223B, 0x0A1425, 0.65f, 0.31f, 70),
      palette(0x1D2F4F, 0x14223B, 0x0A1425, 0.65f, 0.31f, 70)
    };
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

    private TimePalette(Color top, Color middle, Color bottom, float x, float y, float radius) {
      skyTop = top;
      skyMiddle = middle;
      skyBottom = bottom;
      glowCenter = alpha(mixColor(top, Color.WHITE, 0.52f), 82);
      glowMiddle = alpha(mixColor(middle, Color.WHITE, 0.24f), 22);
      lowerHaze = alpha(mixColor(middle, Color.WHITE, 0.22f), 25);
      placeholder = alpha(mixColor(bottom, Color.WHITE, 0.12f), 180);
      vignette = new Color(0, 3, 9, 106);
      border = mixColor(top, new Color(130, 154, 176), 0.45f);
      glowX = WIDTH * x;
      glowY = HEIGHT * y;
      glowRadius = radius;
    }

    private static TimePalette forTime(LocalTime time) {
      int minute = time.getHour() * 60 + time.getMinute();
      for (int i = 1; i < TIMES.length; i++) {
        if (minute < TIMES[i]) {
          return blend(
              FRAMES[i - 1],
              FRAMES[i],
              (minute - TIMES[i - 1]) / (float) (TIMES[i] - TIMES[i - 1]));
        }
      }
      return FRAMES[FRAMES.length - 1];
    }

    private static TimePalette palette(
        int top, int middle, int bottom, float x, float y, float radius) {
      return new TimePalette(new Color(top), new Color(middle), new Color(bottom), x, y, radius);
    }

    private static TimePalette blend(TimePalette from, TimePalette to, float amount) {
      return new TimePalette(
          mixColor(from.skyTop, to.skyTop, amount),
          mixColor(from.skyMiddle, to.skyMiddle, amount),
          mixColor(from.skyBottom, to.skyBottom, amount),
          lerp(from.glowX / WIDTH, to.glowX / WIDTH, amount),
          lerp(from.glowY / HEIGHT, to.glowY / HEIGHT, amount),
          lerp(from.glowRadius, to.glowRadius, amount));
    }

    private static Color alpha(Color color, int alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static float lerp(float from, float to, float amount) {
      return from + (to - from) * amount;
    }
  }
}
