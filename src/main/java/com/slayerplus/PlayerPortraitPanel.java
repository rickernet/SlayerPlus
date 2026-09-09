package com.slayerplus;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.Timer;

public class PlayerPortraitPanel extends JPanel {
  private static final int SIZE = 144;
  private static final int PORTRAIT_SIZE = 140;
  private static final int[] SKY_STARTS = {
    0, 120, 240, 330, 420, 570, 720, 870, 1020, 1140, 1260, 1380
  };
  private static final BufferedImage[] SKIES = {
    loadImage("portrait-night.png"),
    loadImage("portrait-night-right.png"),
    loadImage("portrait-predawn.png"),
    loadImage("portrait-dawn.png"),
    loadImage("portrait-morning.png"),
    loadImage("portrait-late-morning.png"),
    loadImage("portrait-day.png"),
    loadImage("portrait-afternoon.png"),
    loadImage("portrait-golden-hour.png"),
    loadImage("portrait-dusk.png"),
    loadImage("portrait-night-left.png"),
    loadImage("portrait-late-evening.png")
  };
  private static final BufferedImage PLACEHOLDER = loadImage("portrait-placeholder.png");
  private static final Shape FRAME = new RoundRectangle2D.Float(1, 1, 141, 141, 11, 11);
  private static final Color BORDER = new Color(79, 102, 133);
  private static final Color INNER_HIGHLIGHT = new Color(255, 255, 255, 38);
  private static final RadialGradientPaint LIGHT_PORTRAIT_GLOW =
      portraitGlow(new Color(2, 7, 13, 40), new Color(4, 10, 18, 12));
  private static final RadialGradientPaint DARK_PORTRAIT_GLOW =
      portraitGlow(new Color(235, 246, 255, 48), new Color(190, 218, 238, 13));
  private static final RadialGradientPaint VIGNETTE =
      new RadialGradientPaint(
          new Point2D.Float(72, 72),
          88,
          new float[] {0.56f, 1.0f},
          new Color[] {new Color(0, 0, 0, 0), new Color(0, 3, 9, 106)});
  private final Timer clockTimer = new Timer(60_000, event -> repaint());
  private final Timer fadeTimer = new Timer(33, event -> advanceFade());
  private BufferedImage portrait;
  private BufferedImage portraitShadow;
  private BufferedImage previousPortrait;
  private BufferedImage previousPortraitShadow;
  private long fadeStartedAtNanos;
  private boolean portraitIsDark;

  public PlayerPortraitPanel() {
    setOpaque(false);
    Dimension size = new Dimension(SIZE, SIZE);
    setPreferredSize(size);
    setMinimumSize(size);
    setMaximumSize(size);
    clockTimer.setRepeats(true);
    fadeTimer.setRepeats(true);
  }

  public void setPortrait(BufferedImage image) {
    fadeTimer.stop();
    previousPortrait = portrait;
    previousPortraitShadow = portraitShadow;
    portrait = image;
    portraitShadow = image == null ? null : createShadow(image);
    portraitIsDark = image != null && isMostlyDark(image);
    if (image != null && previousPortrait != null && isDisplayable()) {
      fadeStartedAtNanos = System.nanoTime();
      fadeTimer.start();
    } else {
      clearPreviousPortrait();
    }
    repaint();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    clockTimer.start();
    if (previousPortrait != null && portrait != null && fadeProgress() < 1.0f) {
      fadeTimer.start();
    }
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
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    LocalTime now = LocalTime.now();
    Graphics2D g = (Graphics2D) graphics.create();
    g.setClip(FRAME);
    quality(g);
    drawBackdrop(g, now);
    if (portrait != null) {
      g.setPaint(portraitIsDark ? DARK_PORTRAIT_GLOW : LIGHT_PORTRAIT_GLOW);
      g.fillRect(0, 0, SIZE, SIZE);
      int position = (SIZE - PORTRAIT_SIZE) / 2;
      float progress = fadeProgress();
      drawPortrait(g, previousPortrait, previousPortraitShadow, position, 1.0f - progress);
      drawPortrait(g, portrait, portraitShadow, position, progress);
    } else {
      g.drawImage(PLACEHOLDER, 0, 0, null);
    }
    g.setPaint(VIGNETTE);
    g.fillRect(0, 0, SIZE, SIZE);
    g.setClip(null);
    g.setColor(BORDER);
    g.setStroke(new BasicStroke(1.4f));
    g.draw(FRAME);
    g.setColor(INNER_HIGHLIGHT);
    g.setStroke(new BasicStroke());
    g.draw(new RoundRectangle2D.Float(2.5f, 2.5f, 138, 138, 9, 9));
    g.dispose();
  }

  private static void drawBackdrop(Graphics2D graphics, LocalTime time) {
    int minute = time.getHour() * 60 + time.getMinute();
    int index = 0;
    for (index = SKY_STARTS.length - 1; index > 0; index--) {
      if (minute >= SKY_STARTS[index]) {
        break;
      }
    }
    int next = (index + 1) % SKIES.length;
    int nextStart = next == 0 ? 1440 : SKY_STARTS[next];
    graphics.drawImage(SKIES[index], 0, 0, SIZE, SIZE, null);
    int minutesUntilNext = nextStart - minute;
    if (minutesUntilNext > 30) {
      return;
    }
    float blend = 1.0f - minutesUntilNext / 30.0f;
    graphics.setComposite(AlphaComposite.SrcOver.derive(blend));
    graphics.drawImage(SKIES[next], 0, 0, SIZE, SIZE, null);
    graphics.setComposite(AlphaComposite.SrcOver);
  }

  private static BufferedImage loadImage(String name) {
    try (InputStream stream = PlayerPortraitPanel.class.getResourceAsStream(name)) {
      if (stream == null) {
        throw new IllegalStateException("Missing portrait image: " + name);
      }
      BufferedImage image = ImageIO.read(stream);
      if (image == null) {
        throw new IllegalStateException("Invalid portrait image: " + name);
      }
      return image;
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load portrait image: " + name, exception);
    }
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
    return Math.min(1.0f, (System.nanoTime() - fadeStartedAtNanos) / (230 * 1_000_000.0f));
  }

  private void clearPreviousPortrait() {
    previousPortrait = null;
    previousPortraitShadow = null;
  }

  private static void drawPortrait(
      Graphics2D g, BufferedImage image, BufferedImage shadow, int position, float alpha) {
    if (image == null || alpha <= 0) {
      return;
    }
    if (shadow != null) {
      g.setComposite(AlphaComposite.SrcOver.derive(alpha * 0.42f));
      g.drawImage(shadow, position, position + 3, null);
    }
    g.setComposite(AlphaComposite.SrcOver.derive(alpha));
    g.drawImage(image, position, position, null);
    g.setComposite(AlphaComposite.SrcOver);
  }

  private static BufferedImage createShadow(BufferedImage source) {
    BufferedImage silhouette =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        int alpha = source.getRGB(x, y) >>> 24;
        silhouette.setRGB(x, y, Math.min(150, alpha) << 24);
      }
    }
    float[] values = {
      1, 2, 3, 2, 1, 2, 4, 6, 4, 2, 3, 6, 9, 6, 3, 2, 4, 6, 4, 2, 1, 2, 3, 2, 1
    };
    for (int index = 0; index < values.length; index++) {
      values[index] /= 81;
    }
    return new ConvolveOp(new Kernel(5, 5, values), ConvolveOp.EDGE_NO_OP, null)
        .filter(silhouette, null);
  }

  private static boolean isMostlyDark(BufferedImage image) {
    long luminance = 0;
    long alphaTotal = 0;
    for (int y = 0; y < image.getHeight(); y += 2) {
      for (int x = 0; x < image.getWidth(); x += 2) {
        int argb = image.getRGB(x, y);
        int alpha = argb >>> 24;
        int light =
            (((argb >>> 16) & 0xFF) * 54
                    + ((argb >>> 8) & 0xFF) * 183
                    + (argb & 0xFF) * 19)
                >> 8;
        luminance += (long) light * alpha;
        alphaTotal += alpha;
      }
    }
    return alphaTotal > 0 && luminance / alphaTotal < 112;
  }

  private static void quality(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
  }

  private static RadialGradientPaint portraitGlow(Color center, Color middle) {
    return new RadialGradientPaint(
        new Point2D.Float(SIZE / 2.0f, SIZE * 0.48f),
        58,
        new float[] {0.0f, 0.74f, 1.0f},
        new Color[] {center, middle, new Color(0, 0, 0, 0)});
  }

}
