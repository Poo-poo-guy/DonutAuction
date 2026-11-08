package donutauction.client.render;

import donutauction.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

public final class HudRenderer {
  public static final int WIDTH = 328, HEIGHT = 116;
  private final DonutAuctionClient mod;
  private final RollingDigit[] digits = new RollingDigit[6];
  private final Component[][] glyphs = new Component[3][11];
  private String formattedTime = "00:00:00";
  private long previousRevision = -1;
  private long previousSeconds = -1, lastFrame = System.nanoTime();
  private double visibility = 1;
  private long rotationEpoch = System.nanoTime();
  private double rotationBase, lastSpeed = Double.NaN;

  public HudRenderer(DonutAuctionClient mod) {
    this.mod = mod;
    this.visibility = mod.config.visible ? 1 : 0;
    for (int i = 0; i < 6; i++) digits[i] = new RollingDigit();
    for (int face = 0; face < 3; face++) {
      for (int digit = 0; digit < 10; digit++)
        glyphs[face][digit] = Glass.styled(Integer.toString(digit), face);
      glyphs[face][10] = Glass.styled(":", face);
    }
  }

  public double effectiveScale(int width, int height) {
    return Math.min(mod.config.scale, Math.min((width - 8d) / WIDTH, (height - 8d) / HEIGHT));
  }

  public int x(int width, double scale) {
    return (int) Math.round(Math.max(0, width - WIDTH * scale) * mod.config.x);
  }

  public int y(int height, double scale) {
    return (int) Math.round(Math.max(0, height - HEIGHT * scale) * mod.config.y);
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
    Glass.panel(g, 0, 0, WIDTH, HEIGHT, (int) c.radius, c.background, c.opacity * alpha, c.shadow);
    Glass.round(g, 18, 27, 60, 60, 30, Glass.color(c.accent, .10 * alpha));
    Glass.round(g, 20, 29, 56, 56, 28, Glass.color(c.background, .65 * alpha));
    if (lastSpeed != c.rotationSpeed) {
      if (Double.isFinite(lastSpeed)) rotationBase += (now - rotationEpoch) / 1e9 * lastSpeed;
      rotationBase %= 360;
      rotationEpoch = now;
      lastSpeed = c.rotationSpeed;
    }
    float angle = (float) ((rotationBase + (now - rotationEpoch) / 1e9 * c.rotationSpeed) % 360);
    SpinningItem.extract(g, mod.selected(), 20, 29, 56, angle, alpha, clip);
    Glass.text(g, "DONUTAUCTION+", 94, 15, .65f, c.accent, alpha * .9);
    Component name = mod.selected().getHoverName();
    // Clip the original component without discarding custom names or styling.
    g.enableScissor(94, 29, 312, 44);
    Glass.text(g, name, 94, 30, .95f, c.text, alpha);
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
    String value = formattedTime;
    int index = 0;
    int direction = previousSeconds < 0 || seconds <= previousSeconds ? -1 : 1;
    int rgb = seconds <= 10 ? c.urgent : c.timer;
    float cell = 10, timerScale = 2.35f, yTimer = 48;
    g.enableScissor(91, 47, WIDTH - 12, 74);
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      float px = 94 + i * cell * timerScale;
      if (ch == ':') {
        Glass.text(g, glyphs[c.font][10], px, yTimer, timerScale, rgb, alpha * .55);
        continue;
      }
      RollingDigit d = digits[index++];
      d.target(ch - '0', changeAt, c.animationSeconds, c.animationStyle, direction);
      double p = d.position(now, c.animationSeconds, c.animationStyle);
      int base = (int) Math.floor(p);
      for (int row = base; row <= base + 1; row++) {
        if (Math.abs(row - p) >= .999999) continue;
        float yy = yTimer + (float) ((row - p) * 13) * timerScale;
        Glass.text(g, glyphs[c.font][RollingDigit.digit(row)], px, yy, timerScale, rgb, alpha);
      }
    }
    g.disableScissor();
    previousSeconds = seconds;
    previousRevision = mod.timer.revision();
    double fraction = mod.timer.remainingNanos() / (1e9 * Math.max(1, c.duration));
    Glass.round(g, 94, 89, 214, 3, 1, Glass.color(c.text, .09 * alpha));
    int progress = (int) Math.round(214 * Math.clamp(fraction, 0, 1));
    if (progress > 0) Glass.round(g, 94, 89, progress, 3, 1, Glass.color(rgb, .8 * alpha));
    String status =
        seconds == 0 ? "AUCTION ENDED" : mod.timer.running() ? "AUCTION LIVE" : "READY / PAUSED";
    Glass.round(
        g, 94, 100, 3, 3, 1, Glass.color(mod.timer.running() ? c.accent : c.text, alpha * .8));
    Glass.text(g, status, 103, 98, .62f, c.text, alpha * .6);
    g.pose().popMatrix();
  }
}
