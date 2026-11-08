package donutauction.client.render;

import donutauction.client.DonutAuctionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.*;

public final class Glass {
  private Glass() {}

  public static int color(int rgb, double alpha) {
    return ((int) Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
  }

  public static int mix(int a, int b, double t) {
    t = Math.clamp(t, 0, 1);
    return ((int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t) << 16)
        | ((int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t) << 8)
        | (int) ((a & 255) * (1 - t) + (b & 255) * t);
  }

  /** Framebuffer-aware analytic coverage geometry; never enlarges a raster mask. */
  public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
    if (w > 0 && h > 0 && (color >>> 24) > 0)
      SmoothShape.draw(g, x, y, w, h, Math.max(0, r), color);
  }

  public static void panel(
      GuiGraphicsExtractor g,
      int x,
      int y,
      int w,
      int h,
      int r,
      int bg,
      double opacity,
      boolean shadow) {
    if (shadow) {
      for (int i = 6; i >= 1; i--)
        round(g, x - i, y - i + 3, w + i * 2, h + i * 2, r + i, color(0x000000, .025 * opacity));
    }
    if (DonutAuctionClient.INSTANCE.config.borders)
      round(g, x, y, w, h, r, color(0xC4D4ED, .18 * opacity));
    float border =
        1f
            / Math.max(
                .01f,
                (float) Math.hypot(g.pose().m00(), g.pose().m01())
                    * Minecraft.getInstance().getWindow().getGuiScale());
    SmoothShape.draw(
        g,
        x + border,
        y + border,
        w - 2 * border,
        h - 2 * border,
        Math.max(0, r - border),
        color(bg, opacity));
  }

  public static MutableComponent styled(String text, int font) {
    return Component.literal(text)
        .withStyle(
            Style.EMPTY.withFont(
                new FontDescription.Resource(
                    DonutAuctionClient.id("v2/" + PremiumFonts.IDS[Math.clamp(font, 0, 3)]))));
  }

  public static void text(
      GuiGraphicsExtractor g, String text, float x, float y, float scale, int rgb, double alpha) {
    text(g, styled(text, DonutAuctionClient.INSTANCE.config.uiFont), x, y, scale, rgb, alpha);
  }

  public static void text(
      GuiGraphicsExtractor g,
      Component text,
      float x,
      float y,
      float scale,
      int rgb,
      double alpha) {
    if (alpha <= .003) return;
    int face = DonutAuctionClient.INSTANCE == null ? 0 : DonutAuctionClient.INSTANCE.config.uiFont;
    if (text.getStyle().getFont() instanceof FontDescription.Resource r)
      for (int i = 0; i < 4; i++)
        if (r.id().getPath().equals("v2/" + PremiumFonts.IDS[i])) face = i;
    PremiumFonts.draw(
        g, text, x, y, scale, rgb, alpha, face, DonutAuctionClient.INSTANCE.config.fontWeight);
  }

  public static void centered(
      GuiGraphicsExtractor g, String s, int x, int y, int w, int rgb, double alpha) {
    var c = styled(s, DonutAuctionClient.INSTANCE.config.uiFont);
    float tw =
        PremiumFonts.width(
            s,
            DonutAuctionClient.INSTANCE.config.uiFont,
            DonutAuctionClient.INSTANCE.config.fontWeight);
    text(g, c, x + (w - tw) / 2f, y, 1, rgb, alpha);
  }
}
