package donutauction.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import donutauction.client.render.Glass;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

public final class GlassControl extends AbstractWidget {
  private final Supplier<String> label;
  private final Runnable click;
  private final DoubleSupplier value;
  private final DoubleConsumer update;
  private final BooleanSupplier toggle;
  private final double min, max;
  private double hover, displayed = Double.NaN;
  private long last = System.nanoTime();
  private final boolean primary;
  private final int accent;

  public GlassControl(
      int x,
      int y,
      int w,
      int h,
      Supplier<String> label,
      Runnable click,
      boolean primary,
      int accent) {
    super(x, y, w, h, Component.literal(label.get()));
    this.label = label;
    this.click = click;
    this.primary = primary;
    this.accent = accent;
    value = null;
    update = null;
    toggle = null;
    min = 0;
    max = 1;
  }

  public GlassControl(
      int x,
      int y,
      int w,
      Supplier<String> label,
      BooleanSupplier toggle,
      Runnable click,
      int accent) {
    super(x, y, w, 32, Component.literal(label.get()));
    this.label = label;
    this.toggle = toggle;
    this.click = click;
    this.accent = accent;
    primary = false;
    value = null;
    update = null;
    min = 0;
    max = 1;
  }

  public GlassControl(
      int x,
      int y,
      int w,
      Supplier<String> label,
      DoubleSupplier value,
      DoubleConsumer update,
      double min,
      double max,
      int accent) {
    super(x, y, w, 43, Component.literal(label.get()));
    this.label = label;
    this.value = value;
    this.update = update;
    this.min = min;
    this.max = max;
    this.accent = accent;
    click = null;
    toggle = null;
    primary = false;
  }

  @Override
  protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
    long now = System.nanoTime();
    double dt = Math.min(.1, (now - last) / 1e9);
    last = now;
    hover += ((isHoveredOrFocused() ? 1 : 0) - hover) * (1 - Math.exp(-dt * 18));
    double a = alpha * (active ? 1 : .4);
    setMessage(Component.literal(label.get()));
    if (value != null) {
      double f = Math.clamp((value.getAsDouble() - min) / (max - min), 0, 1);
      if (Double.isNaN(displayed)) displayed = f;
      displayed += (f - displayed) * (1 - Math.exp(-dt * 26));
      Glass.text(g, label.get(), getX(), getY() + 1, .86f, 0xDDE6F3, a);
      Glass.round(g, getX(), getY() + 25, width, 4, 2, Glass.color(0xFFFFFF, .10 * a));
      Glass.round(
          g,
          getX(),
          getY() + 25,
          Math.max(1, (int) (width * displayed)),
          4,
          2,
          Glass.color(accent, .65 * a));
      int cx = getX() + (int) Math.round((width - 10) * displayed);
      Glass.round(g, cx - 2, getY() + 20, 14, 14, 7, Glass.color(accent, .12 * a * hover));
      Glass.round(g, cx, getY() + 22, 10, 10, 5, Glass.color(0xF4FFF9, a));
    } else if (toggle != null) {
      double target = toggle.getAsBoolean() ? 1 : 0;
      if (Double.isNaN(displayed)) displayed = target;
      displayed += (target - displayed) * (1 - Math.exp(-dt * 18));
      Glass.round(
          g, getX(), getY(), width, height, 8, Glass.color(0xFFFFFF, (.025 + .035 * hover) * a));
      Glass.text(g, label.get(), getX() + 10, getY() + 11, .85f, 0xDDE6F3, a);
      Glass.round(
          g,
          getRight() - 42,
          getY() + 8,
          32,
          16,
          8,
          Glass.color(Glass.mix(0x3B4556, accent, displayed), a));
      Glass.round(
          g,
          getRight() - 39 + (int) (16 * displayed),
          getY() + 11,
          10,
          10,
          5,
          Glass.color(0xFFFFFF, a));
    } else {
      int bg = primary ? accent : Glass.mix(0x202C3E, 0x344359, hover);
      Glass.round(g, getX(), getY(), width, height, 8, Glass.color(bg, a * (primary ? .94 : .78)));
      if (isFocused())
        Glass.round(g, getX() + 4, getBottom() - 3, width - 8, 1, 0, Glass.color(accent, a));
      Glass.centered(
          g,
          label.get(),
          getX(),
          getY() + (height - 9) / 2,
          width,
          primary ? 0x102219 : 0xE4ECF8,
          a);
    }
  }

  private void drag(double x) {
    if (update != null) update.accept(min + (max - min) * Math.clamp((x - getX()) / width, 0, 1));
  }

  @Override
  public void onClick(MouseButtonEvent event, boolean doubleClick) {
    if (value != null) drag(event.x());
    else click.run();
  }

  @Override
  protected void onDrag(MouseButtonEvent event, double dx, double dy) {
    drag(event.x());
  }

  @Override
  public boolean keyPressed(KeyEvent e) {
    if (!active || !visible) return false;
    if (value != null
        && (e.key() == InputConstants.KEY_LEFT || e.key() == InputConstants.KEY_RIGHT)) {
      update.accept(
          Math.clamp(
              value.getAsDouble()
                  + (e.key() == InputConstants.KEY_RIGHT ? 1 : -1) * (max - min) / 100,
              min,
              max));
      return true;
    }
    if (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_SPACE) {
      if (click != null) click.run();
      return true;
    }
    return false;
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }
}
