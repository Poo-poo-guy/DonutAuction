package donutauction.client.render;

import donutauction.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

public final class HudRenderer {
  public static final int WIDTH = 328, HEIGHT = 116;
  private final DonutAuctionClient mod;
  private final RollingDigit[] digits = new RollingDigit[6];
  private final Component[][] glyphs = new Component[4][11];
  private String formattedTime = "00:00:00";
  private long previousRevision = -1;
  private long previousSeconds = -1, lastFrame = System.nanoTime();
  private double visibility = 1;
  private long rotationEpoch = System.nanoTime();
  private double rotationBase, lastSpeed = Double.NaN;
  private final EndingAnimation ending = new EndingAnimation();
  private double endedBlend;
  private long blendAt = System.nanoTime();

  public HudRenderer(DonutAuctionClient mod) {
    this.mod = mod;
    this.visibility = mod.config.visible ? 1 : 0;
    for (int i = 0; i < 6; i++) digits[i] = new RollingDigit();
    for (int face = 0; face < 4; face++) {
      for (int digit = 0; digit < 10; digit++)
        glyphs[face][digit] = Glass.styled(Integer.toString(digit), face);
      glyphs[face][10] = Glass.styled(":", face);
    }
  }

  public double effectiveScale(int width, int height) {
    return Math.min(
        mod.config.scale,
        Math.min((width - 8d) / mod.config.hudWidth, (height - 8d) / mod.config.hudHeight));
  }

  public int x(int width, double scale) {
    return (int) Math.round(Math.max(0, width - mod.config.hudWidth * scale) * mod.config.x);
  }

  public int y(int height, double scale) {
    return (int) Math.round(Math.max(0, height - mod.config.hudHeight * scale) * mod.config.y);
  }

  public void renderOverlay(GuiGraphicsExtractor g) {
    long now = System.nanoTime();
    double dt = Math.min(.1, (now - lastFrame) / 1e9);
    lastFrame = now;
    visibility += ((mod.config.visible ? 1 : 0) - visibility) * (1 - Math.exp(-dt * 14));
    if (visibility < .003) return;
    double scale = effectiveScale(g.guiWidth(), g.guiHeight());
    draw(
        g,
        x(g.guiWidth(), scale),
        y(g.guiHeight(), scale),
        (float) scale,
        visibility,
        new ScreenRectangle(0, 0, g.guiWidth(), g.guiHeight()));
  }

  public void draw(
      GuiGraphicsExtractor g, float x, float y, float scale, double alpha, ScreenRectangle clip) {
    var c = mod.config;
    long now = System.nanoTime();
    g.pose().pushMatrix();
    g.pose().translate(x, y);
    g.pose().scale(scale, scale);
    int panelW = (int) c.hudWidth, panelH = (int) c.hudHeight;
    Glass.panel(g, 0, 0, panelW, panelH, (int) c.radius, c.background, c.opacity * alpha, c.shadow);
    if (c.itemCircle) Glass.round(g, 18, 27, 60, 60, 30, Glass.color(c.accent, .10 * alpha));
    if (c.itemCircle) Glass.round(g, 20, 29, 56, 56, 28, Glass.color(c.background, .65 * alpha));
    boolean finished = mod.timer.ended();
    float angle =
        (float)
            (ending.sample(
                    now,
                    mod.timer.cycle(),
                    finished,
                    mod.timer.endedAt(),
                    c.rotationSpeed,
                    c.endingDuration,
                    c.endingMultiplier,
                    c.endingEnabled)
                % 360);
    double phase = ending.progress(now, c.endingDuration);
    double target =
        finished ? (c.endingEnabled ? EndingAnimation.ease((phase - .28) / .22) : 1) : 0;
    double dt = Math.max(0, (now - blendAt) / 1e9);
    blendAt = now;
    endedBlend += (target - endedBlend) * (1 - Math.exp(-dt * 18));
    if (finished && c.endingGlow)
      Glass.round(
          g,
          1,
          1,
          panelW - 2,
          panelH - 2,
          (int) c.radius,
          Glass.color(c.accent, .04 * endedBlend * alpha));
    SpinningItem.extract(
        g,
        mod.selected(),
        (int) (48 - c.itemSize / 2),
        (int) (57 - c.itemSize / 2),
        (int) c.itemSize,
        angle,
        alpha,
        clip);
    Glass.text(g, "DONUTAUCTION+", 94, 15, .65f, c.accent, alpha * .9);
    Component name =
        mod.selected().getHoverName().copy().withStyle(Glass.styled("", c.uiFont).getStyle());
    // Clip the original component without discarding custom names or styling.
    g.enableScissor(94, 29, panelW - 16, 46);
    Glass.text(g, name, 94, 30, (float) (.95 * c.textSize), c.text, alpha);
    g.disableScissor();
    long remainingNanos = mod.timer.remainingNanos();
    long seconds = (remainingNanos + 999_999_999L) / 1_000_000_000L;
    long changeAt = now;
    // Anchor natural rolls to the exact second boundary instead of the first rendered frame.
    if (previousSeconds >= 0
        && seconds < previousSeconds
        && previousRevision == mod.timer.revision()
        && remainingNanos > 0) {
      long fraction = remainingNanos % 1_000_000_000L;
      changeAt = now - (fraction == 0 ? 0 : 1_000_000_000L - fraction);
    }
    if (seconds != previousSeconds) formattedTime = AuctionTimer.format(seconds);
    String value = c.showHours || seconds >= 3600 ? formattedTime : formattedTime.substring(3);
    int index = 0;
    int direction = previousSeconds < 0 || seconds <= previousSeconds ? -1 : 1;
    int rgb = seconds <= 10 ? c.urgent : c.timer;
    double digitOut =
        finished ? (c.endingEnabled ? EndingAnimation.ease((phase - .10) / .18) : 1) : endedBlend;
    float cell = 10,
        timerScale =
            Math.min((float) (2.35 * c.timerSize), (panelW - 112) / (value.length() * 10f)),
        yTimer = (float) (48 - 7 * digitOut);
    double digitAlpha = alpha * (1 - digitOut);
    g.enableScissor(91, 47, panelW - 12, 74);
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      float px = 94 + i * cell * timerScale;
      if (ch == ':') {
        Glass.text(g, glyphs[c.timerFont][10], px, yTimer, timerScale, rgb, digitAlpha * .55);
        continue;
      }
      RollingDigit d = digits[index++];
      d.target(ch - '0', changeAt, c.animationSeconds, c.animationStyle, direction);
      double p = d.position(now, c.animationSeconds, c.animationStyle);
      int base = (int) Math.floor(p);
      for (int row = base; row <= base + 1; row++) {
        if (Math.abs(row - p) >= .999999) continue;
        float yy = yTimer + (float) ((row - p) * 13) * timerScale;
        Glass.text(
            g, glyphs[c.timerFont][RollingDigit.digit(row)], px, yy, timerScale, rgb, digitAlpha);
      }
    }
    g.disableScissor();
    if (endedBlend > .001) {
      var message = Glass.styled(c.endingText, c.endingFont);
      float size =
          (float)
              Math.min(
                  c.endingSize,
                  (panelW - 112)
                      / Math.max(1, PremiumFonts.width(c.endingText, c.endingFont, c.fontWeight)));
      Glass.text(
          g,
          message,
          94,
          (float) (51 + 7 * (1 - endedBlend)),
          size,
          c.endingColor,
          alpha * endedBlend);
    }
    previousSeconds = seconds;
    previousRevision = mod.timer.revision();
    double fraction = mod.timer.remainingNanos() / (1e9 * Math.max(1, c.duration));
    Glass.round(g, 94, panelH - 27, panelW - 114, 3, 1, Glass.color(c.text, .09 * alpha));
    int progress = (int) Math.round((panelW - 114) * Math.clamp(fraction, 0, 1));
    if (progress > 0) Glass.round(g, 94, panelH - 27, progress, 3, 1, Glass.color(rgb, .8 * alpha));
    String status =
        seconds == 0 ? "AUCTION ENDED" : mod.timer.running() ? "AUCTION LIVE" : "READY / PAUSED";
    Glass.round(
        g,
        94,
        panelH - 16,
        3,
        3,
        1,
        Glass.color(mod.timer.running() ? c.accent : c.text, alpha * .8));
    Glass.text(g, status, 103, panelH - 18, .62f, c.text, alpha * .6);
    g.pose().popMatrix();
  }
}
