package donutauction.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import donutauction.client.*;
import donutauction.client.render.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class AuctionScreen extends Screen {
  private static final int W = 780, H = 500;
  private final DonutAuctionClient mod;
  private final List<AbstractWidget> fixed = new ArrayList<>(),
      body = new ArrayList<>(),
      picker = new ArrayList<>();
  private final List<ItemStack> allItems = new ArrayList<>(), filtered = new ArrayList<>();
  private final Map<Integer, Double> itemHover = new HashMap<>();
  private int tab = 0, binding = -1;
  private int fontMenu = -1, fontX, fontY;
  private EditBox search, duration;
  private double uiScale = 1, originX, originY;
  private double transition = 0,
      transitionFrom = 0,
      transitionTarget = 0,
      scroll = 0,
      scrollTarget = 0;
  private long transitionStart = 0, last = System.nanoTime(), opened = System.nanoTime();
  private String notice = "Changes save automatically", hoveredItem = "";
  private boolean positioning, dragging;
  private double dragX, dragY;
  private double alpha = 1;

  private record Caption(String text, int x, int y, float scale, int color) {}

  private final List<Caption> captions = new ArrayList<>();

  public AuctionScreen(DonutAuctionClient mod) {
    super(Component.literal("DonutAuction+ Studio"));
    this.mod = mod;
  }

  private void layout() {
    uiScale = Math.min(1.5, Math.min((width - 16d) / W, (height - 16d) / H));
    originX = (width - W * uiScale) / 2;
    originY = (height - H * uiScale) / 2;
  }

  @Override
  protected void init() {
    layout();
    fixed.clear();
    body.clear();
    picker.clear();
    captions.clear();
    clearWidgets();
    button(
        fixed,
        24,
        398,
        194,
        30,
        () -> positioning ? "Positioning..." : "Position HUD",
        () -> {
          positioning = true;
          clearFocus();
        },
        false);
    button(
        fixed,
        24,
        439,
        94,
        28,
        () -> mod.config.visible ? "HUD visible" : "HUD hidden",
        () -> mod.action(0),
        false);
    button(fixed, 124, 439, 94, 28, () -> "Close", this::onClose, false);
    String[] tabs = {"Surface", "Timer", "Items", "Keys", "Type", "Finale", "Layout"};
    for (int i = 0; i < 7; i++) {
      final int t = i;
      button(
          fixed,
          264 + i * 68,
          78,
          63,
          27,
          () -> tabs[t],
          () -> {
            tab = t;
            binding = -1;
            switchPicker(false);
            buildBody();
          },
          false);
    }
    search =
        new PremiumEditBox(
            font, 280, 166, 444, 22, Component.literal("Search items by name or ID"));
    search.setMaxLength(128);
    search.setBordered(false);
    search.setTextColor(0xFFEAF2FC);
    search.setTextShadow(false);
    search.setHint(
        Component.literal("Search by item name or minecraft:id...")
            .withStyle(style -> style.withColor(0x8A9BB4)));
    search.setResponder(this::filterItems);
    register(picker, search);
    button(picker, 270, 120, 86, 26, () -> "< Back", () -> switchPicker(false), false);
    if (allItems.isEmpty())
      for (Item item : BuiltInRegistries.ITEM)
        if (item != Items.AIR) allItems.add(new ItemStack(item));
    allItems.sort(
        Comparator.comparing(s -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
    filterItems(search.getValue());
    buildBody();
  }

  private <T extends AbstractWidget> T register(List<AbstractWidget> list, T control) {
    list.add(control);
    addWidget(control);
    return control;
  }

  private void button(
      List<AbstractWidget> list,
      int x,
      int y,
      int w,
      int h,
      Supplier<String> name,
      Runnable action,
      boolean primary) {
    register(list, new GlassControl(x, y, w, h, name, action, primary, mod.config.accent));
  }

  private void label(String text, int x, int y) {
    captions.add(new Caption(text, x, y, .85f, 0x8292AB));
  }

  private void slider(
      int x,
      int y,
      int w,
      Supplier<String> name,
      DoubleSupplier get,
      DoubleConsumer set,
      double min,
      double max) {
    register(body, new GlassControl(x, y, w, name, get, set, min, max, mod.config.accent));
  }

  private void toggle(int x, int y, int w, String name, BooleanSupplier get, Runnable action) {
    register(body, new GlassControl(x, y, w, () -> name, get, action, mod.config.accent));
  }

  private EditBox field(int x, int y, int w, String text, String hint, Consumer<String> responder) {
    EditBox box = new PremiumEditBox(font, x + 9, y + 9, w - 18, 18, Component.literal(hint));
    box.setBordered(false);
    box.setTextColor(0xFFEAF2FC);
    box.setTextShadow(false);
    box.setMaxLength(64);
    box.setValue(text);
    box.setHint(Component.literal(hint));
    if (responder != null) box.setResponder(responder);
    register(body, box);
    return box;
  }

  private void colorField(String name, int x, int y, int value, IntConsumer update) {
    label(name, x, y);
    field(
        x,
        y + 16,
        142,
        String.format(Locale.ROOT, "#%06X", value),
        "#RRGGBB",
        s -> {
          if (s.matches("#?[0-9a-fA-F]{6}"))
            update.accept(Integer.parseInt(s.replace("#", ""), 16));
        });
  }

  private void buildBody() {
    clearFocus();
    for (AbstractWidget c : body) removeWidget(c);
    body.clear();
    captions.clear();
    var c = mod.config;
    switch (tab) {
      case 0 -> {
        label("SURFACE & MOTION", 270, 124);
        slider(
            270,
            149,
            220,
            () -> String.format(Locale.ROOT, "HUD size  %.0f%%", c.scale * 100),
            () -> c.scale,
            v -> c.scale = v,
            .5,
            2);
        slider(
            518,
            149,
            220,
            () -> String.format(Locale.ROOT, "Opacity  %.0f%%", c.opacity * 100),
            () -> c.opacity,
            v -> c.opacity = v,
            .1,
            1);
        slider(
            270,
            199,
            220,
            () -> String.format(Locale.ROOT, "Corner radius  %.0f", c.radius),
            () -> c.radius,
            v -> c.radius = v,
            0,
            24);
        slider(
            518,
            199,
            220,
            () -> String.format(Locale.ROOT, "Item rotation  %.0f°/s", c.rotationSpeed),
            () -> c.rotationSpeed,
            v -> c.rotationSpeed = v,
            -180,
            180);
        button(
            body,
            270,
            253,
            220,
            30,
            () -> "Typography settings →",
            () -> {
              tab = 4;
              buildBody();
            },
            false);
        toggle(518, 253, 220, "Soft shadow", () -> c.shadow, () -> c.shadow = !c.shadow);
        colorField("Background", 270, 306, c.background, v -> c.background = v);
        colorField("Accent", 434, 306, c.accent, v -> c.accent = v);
        colorField("Item name", 598, 306, c.text, v -> c.text = v);
        colorField("Timer", 270, 373, c.timer, v -> c.timer = v);
        colorField("Final 10 seconds", 434, 373, c.urgent, v -> c.urgent = v);
        label("Hex colors update live", 598, 395);
      }
      case 1 -> {
        label("AUCTION DURATION", 270, 124);
        duration =
            field(270, 142, 220, AuctionTimer.format(c.duration), "Seconds or hh:mm:ss", null);
        button(body, 504, 142, 112, 34, () -> "Set duration", this::applyDuration, true);
        button(
            body,
            626,
            142,
            112,
            34,
            () -> "Save preset",
            () -> {
              if (applyDuration()) {
                if (c.presets.size() >= 8 && !c.presets.contains(c.duration)) {
                  notice = "Eight presets maximum; remove one first";
                  return;
                }
                if (!c.presets.contains(c.duration)) c.presets.add(c.duration);
                mod.select(mod.selected());
                c.savePreset(AuctionTimer.format(c.duration));
                buildBody();
              }
            },
            false);
        for (int i = 0; i < c.presets.size(); i++) {
          final long seconds = c.presets.get(i);
          int px = 270 + (i % 4) * 119, py = 191 + (i / 4) * 34;
          button(
              body,
              px,
              py,
              84,
              27,
              () -> compact(seconds),
              () -> {
                if (c.auctionPresets.containsKey(AuctionTimer.format(seconds)))
                  mod.loadPreset(AuctionTimer.format(seconds));
                else mod.duration(seconds);
                buildBody();
              },
              false);
          button(
              body,
              px + 87,
              py,
              24,
              27,
              () -> "×",
              () -> {
                c.presets.remove(seconds);
                c.auctionPresets.remove(AuctionTimer.format(seconds));
                c.save();
                buildBody();
              },
              false);
        }
        button(
            body,
            270,
            269,
            150,
            32,
            () -> mod.timer.running() ? "Pause auction" : "Start / resume",
            () -> mod.toggleTimer(),
            true);
        button(body, 430, 269, 90, 32, () -> "Reset", () -> mod.action(3), false);
        button(body, 530, 269, 99, 32, () -> "−10 sec", () -> mod.action(6), false);
        button(body, 639, 269, 99, 32, () -> "+10 sec", () -> mod.action(5), false);
        label("ROLLING DIGITS", 270, 324);
        button(
            body,
            270,
            347,
            220,
            32,
            () -> "Easing: " + new String[] {"Cinematic", "Silk", "Swift"}[c.animationStyle],
            () -> c.animationStyle = (c.animationStyle + 1) % 3,
            false);
        slider(
            518,
            340,
            220,
            () -> String.format(Locale.ROOT, "Roll duration  %.0f ms", c.animationSeconds * 1000),
            () -> c.animationSeconds,
            v -> c.animationSeconds = v,
            .15,
            .85);
        label("Every digit rolls independently. The clock stays accurate at any FPS.", 270, 410);
      }
      case 2 -> {
        label("AUCTION ITEM", 270, 124);
        button(body, 270, 150, 226, 36, () -> "Pick Item", () -> switchPicker(true), true);
        button(
            body,
            510,
            150,
            228,
            36,
            () -> "Use Held Item",
            () ->
                notice =
                    mod.useHeld()
                        ? "Held item copied, including its components"
                        : "Hold a non-empty item in your main hand",
            false);
        label("CURRENT SELECTION", 270, 219);
        label("Held items are copied, including custom names, enchantments,", 270, 320);
        label("and all components supported by Minecraft's item codec.", 270, 338);
        label("Switching inventory slots never changes the selection.", 270, 375);
        label("Your selected stack is saved between sessions.", 270, 393);
      }
      case 3 -> {
        label("Click a shortcut, then press a key or mouse button. Escape cancels.", 270, 124);
        for (int i = 0; i < mod.keys.length; i++) {
          final int index = i;
          int py = 150 + i * 38;
          label(DonutAuctionClient.ACTIONS[i], 270, py + 10);
          button(
              body,
              526,
              py,
              212,
              29,
              () ->
                  binding == index
                      ? "Press key... (Del clears)"
                      : mod.keys[index].getTranslatedKeyMessage().getString(),
              () -> {
                clearFocus();
                binding = index;
              },
              binding == index);
        }
        button(
            body,
            270,
            428,
            220,
            25,
            () -> "Restore default shortcuts",
            () -> {
              for (KeyMapping key : mod.keys) key.setKey(key.getDefaultKey());
              KeyMapping.resetMapping();
              minecraft.options.save();
              binding = -1;
            },
            false);
      }
      case 4 -> {
        label("FOUR FAMILIES · LICENSED COVERAGE ATLAS TYPOGRAPHY", 270, 124);
        fontButton(270, 151, 0, "Interface", c.uiFont);
        fontButton(518, 151, 1, "Countdown", c.timerFont);
        fontButton(270, 195, 2, "Ending", c.endingFont);
        button(
            body,
            518,
            195,
            220,
            32,
            () ->
                "Weight: " + new String[] {"Regular 400", "SemiBold 600", "Bold 700"}[c.fontWeight],
            () -> c.fontWeight = (c.fontWeight + 1) % 3,
            false);
        slider(
            270, 254, 220, () -> "Item name size", () -> c.textSize, v -> c.textSize = v, .6, 1.5);
        slider(
            518,
            254,
            220,
            () -> "Countdown size",
            () -> c.timerSize,
            v -> c.timerSize = v,
            .6,
            1.3);
        toggle(
            270,
            320,
            220,
            "Always show hours",
            () -> c.showHours,
            () -> c.showHours = !c.showHours);
        label("Inter · Manrope · Outfit · Plus Jakarta Sans", 270, 388);
        label("Cached antialiased glyphs · Linear sampling · Unicode fallback", 270, 410);
      }
      case 5 -> {
        label("CINEMATIC AUCTION ENDING", 270, 124);
        toggle(
            270,
            148,
            220,
            "Ending animation",
            () -> c.endingEnabled,
            () -> c.endingEnabled = !c.endingEnabled);
        toggle(
            518, 148, 220, "Accent glow", () -> c.endingGlow, () -> c.endingGlow = !c.endingGlow);
        label("Completion text", 270, 202);
        field(270, 220, 468, c.endingText, "AUCTION ENDED!", v -> c.endingText = v);
        slider(
            270,
            274,
            220,
            () -> "Sequence duration",
            () -> c.endingDuration,
            v -> c.endingDuration = v,
            .5,
            8);
        slider(
            518,
            274,
            220,
            () -> "Spin acceleration",
            () -> c.endingMultiplier,
            v -> c.endingMultiplier = v,
            1,
            8);
        slider(
            270,
            336,
            220,
            () -> "Ending text size",
            () -> c.endingSize,
            v -> c.endingSize = v,
            .6,
            2.5);
        colorField("Ending text color", 518, 330, c.endingColor, v -> c.endingColor = v);
        fontButton(270, 402, 2, "Ending font", c.endingFont);
      }
      case 6 -> {
        label("HUD GEOMETRY & MATERIAL", 270, 124);
        slider(270, 150, 220, () -> "Panel width", () -> c.hudWidth, v -> c.hudWidth = v, 260, 520);
        slider(
            518, 150, 220, () -> "Panel height", () -> c.hudHeight, v -> c.hudHeight = v, 100, 180);
        slider(
            270,
            212,
            220,
            () -> "Item preview size",
            () -> c.itemSize,
            v -> c.itemSize = v,
            28,
            68);
        toggle(
            518, 212, 220, "Item circle", () -> c.itemCircle, () -> c.itemCircle = !c.itemCircle);
        toggle(270, 278, 220, "Crisp border", () -> c.borders, () -> c.borders = !c.borders);
        toggle(518, 278, 220, "Surface gradient", () -> c.gradient, () -> c.gradient = !c.gradient);
        label("Placement remains relative to the viewport across GUI scales.", 270, 360);
        button(
            body,
            270,
            398,
            220,
            32,
            () -> "Drag HUD in world",
            () -> {
              positioning = true;
              clearFocus();
            },
            true);
      }
    }
    updateVisibility();
  }

  private void fontButton(int x, int y, int target, String title, int face) {
    button(
        body,
        x,
        y,
        220,
        32,
        () -> title + ": " + PremiumFonts.NAMES[face],
        () -> {
          fontMenu = target;
          fontX = x;
          fontY = Math.min(330, y + 34);
          clearFocus();
        },
        false);
  }

  private static String compact(long seconds) {
    return seconds % 60 == 0
        ? seconds / 60 + " min"
        : seconds < 60 ? seconds + " sec" : AuctionTimer.format(seconds);
  }

  private boolean applyDuration() {
    try {
      long seconds = AuctionTimer.parse(duration.getValue());
      mod.duration(seconds);
      notice = "Duration set to " + AuctionTimer.format(seconds);
      return true;
    } catch (IllegalArgumentException e) {
      notice = e.getMessage();
      return false;
    }
  }

  private void filterItems(String query) {
    String q = query.toLowerCase(Locale.ROOT).trim();
    filtered.clear();
    for (ItemStack stack : allItems)
      if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
          || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(q))
        filtered.add(stack);
    scroll = scrollTarget = 0;
    itemHover.clear();
  }

  private double transition() {
    double t = Math.clamp((System.nanoTime() - transitionStart) / 300_000_000d, 0, 1);
    double ease = t * t * (3 - 2 * t);
    transition = transitionFrom + (transitionTarget - transitionFrom) * ease;
    return transition;
  }

  private void switchPicker(boolean open) {
    transitionFrom = transition();
    transitionTarget = open ? 1 : 0;
    transitionStart = System.nanoTime();
    clearFocus();
    binding = -1;
    if (open) {
      search.setValue("");
      filterItems("");
    }
    updateVisibility();
  }

  private void updateVisibility() {
    boolean p = transitionTarget == 1;
    for (AbstractWidget c : body) {
      c.visible = !p;
      c.active = !p;
    }
    for (AbstractWidget c : picker) {
      c.visible = p;
      c.active = p;
    }
    search.setFocused(false);
  }

  private void renderWidgets(
      GuiGraphicsExtractor g, List<AbstractWidget> controls, int mx, int my, float dt, double a) {
    for (AbstractWidget c : controls) {
      boolean visible = c.visible;
      c.visible = true;
      c.setAlpha((float) a);
      c.extractRenderState(g, mx, my, dt);
      c.visible = visible;
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
    layout();
    long now = System.nanoTime();
    double dt = Math.min(.1, (now - last) / 1e9);
    last = now;
    if (positioning) {
      renderPositioning(g);
      return;
    }
    double entrance = Math.clamp((now - opened) / 300_000_000d, 0, 1);
    entrance = 1 - Math.pow(1 - entrance, 3);
    alpha = entrance;
    // Native blur applies only behind this screen's glass surface.
    if (minecraft.level != null) g.blurBeforeThisStratum();
    g.fill(0, 0, width, height, Glass.color(0x060B13, .50 * alpha));
    int lx = (int) ((mx - originX) / uiScale), ly = (int) ((my - originY) / uiScale);
    g.pose().pushMatrix();
    g.pose().translate((float) originX, (float) (originY + 12 * (1 - entrance)));
    g.pose().scale((float) uiScale, (float) uiScale);
    Glass.panel(g, 0, 0, W, H, 20, 0x101826, .94 * alpha, true);
    Glass.round(g, 1, 1, 241, H - 2, 19, Glass.color(0x0B121E, .48 * alpha));
    g.fill(242, 20, 243, 480, Glass.color(0xFFFFFF, .07 * alpha));
    Glass.round(g, 24, 25, 29, 29, 10, Glass.color(mod.config.accent, .15 * alpha));
    Glass.centered(g, "D+", 24, 35, 29, mod.config.accent, alpha);
    Glass.text(g, "DonutAuction+", 64, 27, 1.1f, 0xF2F7FE, alpha);
    Glass.text(g, "STREAMER STUDIO", 64, 45, .6f, 0x8A9BB4, alpha);
    Glass.text(g, "LIVE PREVIEW", 24, 91, .65f, 0x93A7C0, alpha);
    ScreenRectangle modalClip =
        new ScreenRectangle((int) originX, (int) originY, (int) (W * uiScale), (int) (H * uiScale));
    mod.hud.draw(
        g,
        24,
        116,
        (float) Math.min(.59, Math.min(194 / mod.config.hudWidth, 80 / mod.config.hudHeight)),
        alpha,
        modalClip);
    Glass.text(g, "Your auction, beautifully in focus.", 24, 207, .72f, 0x8292AB, alpha);
    Glass.round(g, 24, 249, 194, 119, 12, Glass.color(0xFFFFFF, .035 * alpha));
    Glass.text(g, "SELECTED ITEM", 36, 264, .6f, 0x8292AB, alpha);
    g.pose().pushMatrix();
    g.pose().translate(38, 286);
    g.pose().scale(1.5f, 1.5f);
    g.item(mod.selected(), 0, 0);
    g.pose().popMatrix();
    g.enableScissor(74, 284, 207, 320);
    Glass.text(g, mod.selected().getHoverName(), 76, 289, .83f, 0xEAF1FB, alpha);
    g.disableScissor();
    Glass.text(g, "Snapshot saved locally", 36, 337, .68f, mod.config.accent, alpha);
    Glass.text(g, "Auction studio", 264, 28, 1.6f, 0xF2F7FE, alpha);
    Glass.text(
        g, "Small details. Smooth moments. Made for your stream.", 264, 54, .8f, 0x8A9BB4, alpha);
    renderWidgets(g, fixed, lx, ly, delta, alpha);
    Glass.round(
        g,
        270 + tab * 68,
        103,
        48,
        2,
        1,
        Glass.color(mod.config.accent, alpha * (1 - transitionTarget)));
    double p = transition();
    g.enableScissor(252, 113, 756, 456);
    if (p < .999) {
      g.pose().pushMatrix();
      g.pose().translate((float) (-38 * p), 0);
      renderBody(g, lx, ly, delta, alpha * (1 - p));
      g.pose().popMatrix();
    }
    if (p > .001) {
      g.pose().pushMatrix();
      g.pose().translate((float) (38 * (1 - p)), 0);
      renderPicker(g, lx, ly, delta, alpha * p, dt);
      g.pose().popMatrix();
    }
    g.disableScissor();
    if (fontMenu >= 0) {
      g.nextStratum();
      Glass.panel(g, fontX, fontY, 220, 108, 10, 0x172234, .99, true);
      for (int i = 0; i < 4; i++)
        Glass.text(
            g,
            Glass.styled(PremiumFonts.NAMES[i], i),
            fontX + 12,
            fontY + 9 + i * 25,
            1,
            mod.config.text,
            1);
    }
    g.fill(264, 462, 738, 463, Glass.color(0xFFFFFF, .06 * alpha));
    Glass.text(g, notice, 264, 477, .72f, 0x8A9BB4, alpha);
    g.pose().popMatrix();
  }

  private void renderBody(GuiGraphicsExtractor g, int mx, int my, float delta, double a) {
    for (Caption caption : captions)
      Glass.text(g, caption.text, caption.x, caption.y, caption.scale, caption.color, a);
    for (AbstractWidget c : body)
      if (c instanceof EditBox)
        Glass.round(
            g,
            c.getX() - 9,
            c.getY() - 9,
            c.getWidth() + 18,
            34,
            8,
            Glass.color(0xFFFFFF, .055 * a));
    renderWidgets(g, body, mx, my, delta, a);
    if (tab == 2) {
      Glass.round(g, 270, 241, 468, 60, 12, Glass.color(0xFFFFFF, .035 * a));
      g.pose().pushMatrix();
      g.pose().translate(284, 254);
      g.pose().scale(2, 2);
      g.item(mod.selected(), 0, 0);
      g.pose().popMatrix();
      g.enableScissor(332, 246, 729, 299);
      Glass.text(g, mod.selected().getHoverName(), 334, 254, 1.15f, 0xEAF2FB, a);
      Glass.text(
          g,
          BuiltInRegistries.ITEM.getKey(mod.selected().getItem()).toString(),
          334,
          278,
          .75f,
          0x8292AB,
          a);
      g.disableScissor();
    }
  }

  private double maxScroll() {
    return Math.max(0, Math.ceil(filtered.size() / 10d) * 40 - 216);
  }

  private void renderPicker(
      GuiGraphicsExtractor g, int mx, int my, float delta, double a, double dt) {
    Glass.text(g, "Item library", 374, 125, 1.1f, 0xF0F6FF, a);
    Glass.text(g, filtered.size() + " items", 666, 129, .7f, 0x8292AB, a);
    Glass.round(g, 270, 158, 468, 34, 8, Glass.color(0xFFFFFF, .06 * a));
    renderWidgets(g, picker, mx, my, delta, a);
    scroll += (scrollTarget - scroll) * (1 - Math.exp(-dt * 18));
    hoveredItem = "";
    g.enableScissor(270, 204, 738, 420);
    int start = Math.max(0, (int) (scroll / 40) * 10), end = Math.min(filtered.size(), start + 70);
    for (int i = start; i < end; i++) {
      int x = 270 + (i % 10) * 46, y = 204 + (i / 10) * 40 - (int) Math.floor(scroll);
      boolean hover = mx >= x && mx < x + 42 && my >= y && my < y + 36 && my >= 204 && my < 420;
      double h = itemHover.getOrDefault(i, 0d);
      h += ((hover ? 1 : 0) - h) * (1 - Math.exp(-dt * 20));
      itemHover.put(i, h);
      ItemStack stack = filtered.get(i);
      boolean chosen = stack.getItem() == mod.selected().getItem();
      Glass.round(
          g,
          x,
          y,
          42,
          36,
          8,
          Glass.color(chosen ? mod.config.accent : 0x6D8DB2, (chosen ? .19 : .045 + .12 * h) * a));
      g.pose().pushMatrix();
      g.pose().translate(x + 10, (float) (y + 7 - h * 1.5));
      g.pose().scale(1.35f, 1.35f);
      g.item(stack, 0, 0);
      g.pose().popMatrix();

      if (hover)
        hoveredItem =
            stack.getHoverName().getString()
                + "  ·  "
                + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
    g.disableScissor();
    if (filtered.isEmpty())
      Glass.centered(g, "No items match your search", 270, 290, 468, 0x8292AB, a);
    if (maxScroll() > 0) {
      int sh = Math.max(16, (int) (216 * 216 / (maxScroll() + 216)));
      int sy = 204 + (int) ((216 - sh) * scroll / maxScroll());
      Glass.round(g, 735, sy, 3, sh, 1, Glass.color(0x8CABC7, .4 * a));
    }
    Glass.text(
        g,
        hoveredItem.isEmpty() ? "Click an item to select it. Escape goes back." : hoveredItem,
        270,
        435,
        .73f,
        0x93A7C0,
        a);
    if (!hoveredItem.isEmpty() && a > .99) {
      int tx = Math.min(mx + 12, 500), ty = Math.min(my + 12, 397);
      g.nextStratum();
      Glass.panel(g, tx, ty, 230, 42, 8, 0x172234, .98, true);
      g.enableScissor(tx + 6, ty + 3, tx + 224, ty + 39);
      String[] info = hoveredItem.split("  ·  ", 2);
      Glass.text(g, info[0], tx + 9, ty + 7, .9f, mod.config.text, 1);
      if (info.length > 1) Glass.text(g, info[1], tx + 9, ty + 23, .72f, 0x8A9BB4, 1);
      g.disableScissor();
    }
    // Fade actual vanilla item icons together with the rest of the shared panel.
    if (a < .98) Glass.round(g, 269, 203, 470, 218, 8, Glass.color(0x101826, 1 - a));
  }

  private void renderPositioning(GuiGraphicsExtractor g) {
    double s = mod.hud.effectiveScale(width, height);
    mod.hud.draw(
        g,
        mod.hud.x(width, s),
        mod.hud.y(height, s),
        (float) s,
        1,
        new ScreenRectangle(0, 0, width, height));
    Glass.panel(g, width / 2 - 154, height - 49, 308, 32, 10, 0x101826, .94, true);
    Glass.centered(
        g,
        "Drag the HUD · Scroll to resize · Esc to finish",
        width / 2 - 154,
        height - 38,
        308,
        0xEAF2FB,
        1);
  }

  private MouseButtonEvent local(MouseButtonEvent e) {
    return new MouseButtonEvent(
        (e.x() - originX) / uiScale, (e.y() - originY) / uiScale, e.buttonInfo());
  }

  private boolean moving() {
    return Math.abs(transition() - transitionTarget) > .001;
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
    if (binding >= 0) {
      mod.keys[binding].setKey(InputConstants.Type.MOUSE.getOrCreate(e.button()));
      finishBinding();
      return true;
    }
    if (positioning) {
      double s = mod.hud.effectiveScale(width, height),
          x = mod.hud.x(width, s),
          y = mod.hud.y(height, s);
      if (e.button() == InputConstants.MOUSE_BUTTON_LEFT
          && e.x() >= x
          && e.x() <= x + mod.config.hudWidth * s
          && e.y() >= y
          && e.y() <= y + mod.config.hudHeight * s) {
        dragging = true;
        dragX = e.x() - x;
        dragY = e.y() - y;
        return true;
      }
      return false;
    }
    if (moving()) return true;
    MouseButtonEvent l = local(e);
    if (fontMenu >= 0) {
      int index = (int) ((l.y() - fontY - 4) / 25);
      if (l.x() >= fontX && l.x() < fontX + 220 && l.y() >= fontY + 4 && index >= 0 && index < 4) {
        switch (fontMenu) {
          case 0 -> mod.config.uiFont = index;
          case 1 -> mod.config.timerFont = index;
          case 2 -> mod.config.endingFont = index;
        }
        mod.config.save();
      }
      fontMenu = -1;
      buildBody();
      return true;
    }
    if (transitionTarget == 1
        && l.x() >= 270
        && l.x() < 730
        && l.y() >= 204
        && l.y() < 420
        && e.button() == InputConstants.MOUSE_BUTTON_LEFT) {
      int col = (int) ((l.x() - 270) / 46),
          row = (int) ((l.y() - 204 + scroll) / 40),
          index = row * 10 + col;
      if (index >= 0
          && index < filtered.size()
          && (l.x() - 270) % 46 < 42
          && (l.y() - 204 + scroll) % 40 < 36) {
        mod.select(filtered.get(index));
        notice = "Selected " + mod.selected().getHoverName().getString();
        switchPicker(false);
        return true;
      }
    }
    return super.mouseClicked(l, doubleClick);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
    if (positioning && dragging) {
      double s = mod.hud.effectiveScale(width, height);
      mod.config.x =
          Math.clamp((e.x() - dragX) / Math.max(1, width - mod.config.hudWidth * s), 0, 1);
      mod.config.y =
          Math.clamp((e.y() - dragY) / Math.max(1, height - mod.config.hudHeight * s), 0, 1);
      return true;
    }
    return super.mouseDragged(local(e), dx / uiScale, dy / uiScale);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent e) {
    dragging = false;
    mod.config.save();
    return !positioning && super.mouseReleased(local(e));
  }

  @Override
  public boolean mouseScrolled(double x, double y, double dx, double dy) {
    if (positioning) {
      mod.config.scale = Math.clamp(mod.config.scale + dy * .05, .5, 2);
      mod.config.save();
      return true;
    }
    if (transitionTarget == 1 && !moving()) {
      scrollTarget = Math.clamp(scrollTarget - dy * 38, 0, maxScroll());
      return true;
    }
    return super.mouseScrolled((x - originX) / uiScale, (y - originY) / uiScale, dx, dy);
  }

  private void finishBinding() {
    KeyMapping.resetMapping();
    minecraft.options.save();
    binding = -1;
    notice = "Shortcut saved; also available in Minecraft Controls";
  }

  @Override
  public boolean keyPressed(KeyEvent e) {
    if (binding >= 0) {
      if (e.key() != InputConstants.KEY_ESCAPE) {
        mod.keys[binding].setKey(
            e.key() == InputConstants.KEY_DELETE
                ? InputConstants.UNKNOWN
                : InputConstants.getKey(e));
        finishBinding();
      } else binding = -1;
      return true;
    }
    if (e.key() == InputConstants.KEY_ESCAPE) {
      if (fontMenu >= 0) {
        fontMenu = -1;
        return true;
      }
      if (positioning) {
        positioning = false;
        dragging = false;
        mod.config.save();
        return true;
      }
      if (transitionTarget == 1) {
        switchPicker(false);
        return true;
      }
      onClose();
      return true;
    }
    if (positioning) return true;
    return super.keyPressed(e);
  }

  @Override
  public void tick() {
    if (transitionTarget == 1
        && !moving()
        && search.visible
        && !search.isFocused()
        && (getFocused() == null
            || (getFocused() instanceof AbstractWidget widget && !widget.visible)))
      setInitialFocus(search);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    mod.config.save();
    minecraft.gui.setScreen(null);
  }

  @Override
  public void removed() {
    mod.config.save();
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
}
