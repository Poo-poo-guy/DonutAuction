package donutauction.client.ui;

import donutauction.client.*;
import donutauction.client.render.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Native editing/clipboard/IME input with custom antialiased visible typography. */
public final class PremiumEditBox extends EditBox {
  private int viewStart;
  private Component placeholder = Component.empty();

  public PremiumEditBox(Font font, int x, int y, int w, int h, Component title) {
    super(font, x, y, w, h, title);
  }

  @Override
  public void setHint(Component hint) {
    super.setHint(hint);
    placeholder = hint;
  }

  private int index(double mouseX) {
    var c = DonutAuctionClient.INSTANCE.config;
    String text = getValue();
    float width = 0;
    int i = Math.min(viewStart, text.length());
    while (i < text.length()) {
      int next = i + Character.charCount(text.codePointAt(i));
      float step = PremiumFonts.width(text.substring(i, next), c.uiFont, c.fontWeight);
      if (mouseX - getX() < width + step / 2) break;
      width += step;
      i = next;
    }
    return i;
  }

  @Override
  public void onClick(net.minecraft.client.input.MouseButtonEvent e, boolean twice) {
    super.onClick(e, twice);
    int i = index(e.x());
    moveCursorTo(i, net.minecraft.client.Minecraft.getInstance().hasShiftDown());
    if (twice) {
      String t = getValue();
      int a = i, b = i;
      while (a > 0 && !Character.isWhitespace(t.charAt(a - 1))) a--;
      while (b < t.length() && !Character.isWhitespace(t.charAt(b))) b++;
      setCursorPosition(a);
      setHighlightPos(b);
    }
  }

  @Override
  protected void onDrag(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) {
    moveCursorTo(index(e.x()), true);
  }

  @Override
  public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
    var c = DonutAuctionClient.INSTANCE.config;
    String value = getValue();
    int cursor = Math.min(getCursorPosition(), value.length()), start = 0;
    while (start < cursor
        && PremiumFonts.width(value.substring(start, cursor), c.uiFont, c.fontWeight)
            > getWidth() - 12) start++;
    g.enableScissor(getX(), getY() - 2, getRight(), getBottom() + 2);
    viewStart = start;
    String selected = getHighlighted();
    if (!selected.isEmpty()) {
      int at =
          Math.max(
              start,
              Math.min(
                  cursor,
                  ((donutauction.client.mixin.EditBoxAccessor) (Object) this)
                      .donutauction$highlight()));
      if (at >= 0) {
        int x =
            getX() + (int) PremiumFonts.width(value.substring(start, at), c.uiFont, c.fontWeight);
        Glass.round(
            g,
            x,
            getY() - 1,
            (int) PremiumFonts.width(selected, c.uiFont, c.fontWeight),
            12,
            2,
            Glass.color(c.accent, .25 * getAlpha()));
      }
    }
    Glass.text(
        g,
        value.isEmpty() && !isFocused() ? placeholder.getString() : value.substring(start),
        getX(),
        getY(),
        1,
        value.isEmpty() ? 0x8A9BB4 : c.text,
        getAlpha());
    if (isFocused() && System.nanoTime() / 500_000_000L % 2 == 0) {
      int x =
          getX() + (int) PremiumFonts.width(value.substring(start, cursor), c.uiFont, c.fontWeight);
      g.fill(x, getY(), x + 1, getY() + 10, Glass.color(c.accent, getAlpha()));
    }
    g.disableScissor();
  }
}
