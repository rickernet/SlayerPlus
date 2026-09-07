package com.slayerplus;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

class SlayerBraceletChargeInfoBox extends InfoBox {
  private static final long DEPLETED_PULSE_PERIOD_MILLIS = 2400L;
  private static final Color DEPLETED_RED = new Color(220, 55, 55);
  private static final Color DEPLETED_GREY = new Color(115, 105, 105);
  private int charges;
  private final String braceletName;

  SlayerBraceletChargeInfoBox(
      BufferedImage image, Plugin plugin, String braceletName, int charges) {
    super(charges == 0 ? dullDepletedImage(image) : image, plugin);
    this.braceletName = braceletName;
    setCharges(charges);
  }

  boolean isDepleted() {
    return charges == 0;
  }

  void setCharges(int charges) {
    this.charges = charges;
    setTooltip(
        braceletName
            + ": "
            + (charges < 0
                ? "right-click Check to learn charges"
                : charges + " charges remaining"));
  }

  @Override
  public String getText() {
    return charges < 0 ? "?" : Integer.toString(charges);
  }

  @Override
  public Color getTextColor() {
    if (charges == 0) {
      return depletedTextColorForTest(System.currentTimeMillis());
    }
    if (charges > 0 && charges <= 2) {
      return Color.RED;
    }
    if (charges <= 5) {
      return Color.YELLOW;
    }
    return Color.WHITE;
  }

  static Color depletedTextColorForTest(long nowMillis) {
    double phase =
        (nowMillis % DEPLETED_PULSE_PERIOD_MILLIS) / (double) DEPLETED_PULSE_PERIOD_MILLIS;
    double blend = (Math.sin(phase * Math.PI * 2.0) + 1.0) / 2.0;
    return blend(DEPLETED_GREY, DEPLETED_RED, blend);
  }

  private static Color blend(Color from, Color to, double amount) {
    return new Color(
        (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
        (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
        (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
  }

  private static BufferedImage dullDepletedImage(BufferedImage source) {
    if (source == null) {
      return null;
    }
    BufferedImage result =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        int argb = source.getRGB(x, y);
        int alpha = argb >>> 24;
        int red = argb >>> 16 & 0xff;
        int green = argb >>> 8 & 0xff;
        int blue = argb & 0xff;
        int grey = (red * 30 + green * 59 + blue * 11) / 100;
        int mutedRed = Math.min(255, grey * 3 / 4 + 45);
        int mutedOther = grey * 3 / 5;
        result.setRGB(x, y, alpha << 24 | mutedRed << 16 | mutedOther << 8 | mutedOther);
      }
    }
    return result;
  }
}
