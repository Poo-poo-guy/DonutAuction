package donutauction.client.render;

import donutauction.client.DonutAuctionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;

public final class Glass {
  private static final Identifier ROUND = DonutAuctionClient.id("textures/gui/round.png");
  private static final FontDescription SANS =
      new FontDescription.Resource(DonutAuctionClient.id("sans"));
  private static final FontDescription MONO =
      new FontDescription.Resource(DonutAuctionClient.id("mono"));

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

  /** Nine-sliced antialiased mask. Fixed draw cost, independent of panel size. */
  public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
    if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
    r = Math.clamp(r, 0, Math.min(w, h) / 2);
    if (r == 0) {
      g.fill(x, y, x + w, y + h, color);
      return;
    }
    int[] dx = {x, x + r, x + w - r, x + w};
    int[] dy = {y, y + r, y + h - r, y + h};
    int[] uv = {0, 32, 32, 64};
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 3; col++) {
        int sw = col == 1 ? 1 : 32, sh = row == 1 ? 1 : 32;
        g.blit(
            RenderPipelines.GUI_TEXTURED,
            ROUND,
            dx[col],
            dy[row],
            uv[col],
            uv[row],
            dx[col + 1] - dx[col],
            dy[row + 1] - dy[row],
            sw,
            sh,
            64,
            64,
            color);
      }
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
    round(g, x, y, w, h, r, color(0xC4D4ED, .18 * opacity));
    round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), color(bg, opacity));
    g.fill(x + r, y + 1, x + w - r, y + 2, color(0xFFFFFF, .06 * opacity));
  }

  public static MutableComponent styled(String text, int font) {
    return Component.literal(text)
        .withStyle(
            Style.EMPTY.withFont(font == 2 ? FontDescription.DEFAULT : font == 1 ? MONO : SANS));
  }

  public static void text(
      GuiGraphicsExtractor g, String text, float x, float y, float scale, int rgb, double alpha) {
    text(g, styled(text, 0), x, y, scale, rgb, alpha);
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
    g.pose().pushMatrix();
    g.pose().translate(x, y);
    g.pose().scale(scale, scale);
    g.text(Minecraft.getInstance().font, text, 0, 0, color(rgb, alpha), false);
    g.pose().popMatrix();
  }

  public static void centered(
      GuiGraphicsExtractor g, String s, int x, int y, int w, int rgb, double alpha) {
    var c = styled(s, 0);
    int tw = Minecraft.getInstance().font.width(c);
    text(g, c, x + (w - tw) / 2f, y, 1, rgb, alpha);
  }
}
