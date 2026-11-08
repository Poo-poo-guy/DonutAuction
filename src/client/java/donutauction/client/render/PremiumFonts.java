package donutauction.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import donutauction.client.*;
import donutauction.client.mixin.GuiGraphicsAccessor;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.*;
import org.joml.Matrix3x2f;

/** High-resolution coverage atlases, linear sampled. Glyphs rasterize once, never per frame. */
public final class PremiumFonts {
  public static final String[] NAMES = {"Inter", "Manrope", "Outfit", "Plus Jakarta Sans"};
  public static final String[] IDS = {"inter", "manrope", "outfit", "plusjakartasans"};
  private static final Map<Integer, Atlas> atlases = new HashMap<>();
  private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

  private record Glyph(int cell, float advance) {}

  private static class Atlas {
    final Font font;
    final NativeImage pixels;
    final int density, size, cellSize;
    long lastUsed = System.nanoTime();
    final DynamicTexture texture;
    final Map<Integer, Glyph> glyphs = new HashMap<>();
    final Map<String, Float> widths = new HashMap<>();

    Atlas(int face, int weight, int density) {
      this.density = density;
      size = 1024 * density;
      cellSize = 84 * density;
      pixels = new NativeImage(size, size, true);
      try (var in =
          PremiumFonts.class.getResourceAsStream(
              "/assets/donutauction/font/v2/"
                  + IDS[face]
                  + "-"
                  + new int[] {400, 600, 700}[weight]
                  + ".ttf")) {
        font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(48f * density);
      } catch (Exception e) {
        throw new IllegalStateException("Cannot load licensed font", e);
      }
      texture = new DynamicTexture(() -> "DonutAuction font coverage atlas", pixels);
      for (int c = 32; c < 127; c++) add(c);
      texture.upload();
    }

    Glyph add(int cp) {
      if (!font.canDisplay(cp) || glyphs.size() >= 144) return null;
      int cell = glyphs.size(), x = cell % 12 * cellSize, y = cell / 12 * cellSize;
      BufferedImage image = new BufferedImage(cellSize, cellSize, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      g.setFont(font);
      g.setColor(Color.WHITE);
      String s = new String(Character.toChars(cp));
      g.drawString(s, 4 * density, 56 * density);
      g.dispose();
      for (int yy = 0; yy < cellSize; yy++)
        for (int xx = 0; xx < cellSize; xx++)
          pixels.setPixel(x + xx, y + yy, (image.getRGB(xx, yy) | 0xFFFFFF));
      Glyph glyph =
          new Glyph(cell, (float) font.getStringBounds(s, FRC).getWidth() / (5 * density));
      glyphs.put(cp, glyph);
      return glyph;
    }
  }

  private static long cleanup;

  private static Atlas atlas(int face, int weight, int density) {
    long now = System.nanoTime();
    if (now - cleanup > 1_000_000_000L) {
      cleanup = now;
      var it = atlases.values().iterator();
      while (it.hasNext()) {
        Atlas old = it.next();
        if (now - old.lastUsed > 5_000_000_000L) {
          old.texture.close();
          it.remove();
        }
      }
    }
    Atlas a =
        atlases.computeIfAbsent(
            density * 12 + face * 3 + weight, k -> new Atlas(face, weight, density));
    a.lastUsed = now;
    return a;
  }

  public static float width(String s, int face, int weight) {
    var a = atlas(face, weight, 1);
    if (a.widths.size() > 2048) a.widths.clear();
    return a.widths.computeIfAbsent(
        s, key -> (float) a.font.getStringBounds(key, FRC).getWidth() / 5);
  }

  public static void draw(
      GuiGraphicsExtractor g,
      Component text,
      float x,
      float y,
      float scale,
      int rgb,
      double alpha,
      int face,
      int weight) {
    float physical =
        scale
            * (float) Math.hypot(g.pose().m00(), g.pose().m01())
            * net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
    Atlas a = atlas(face, weight, Math.clamp((int) Math.ceil(physical / 5), 1, 4));
    float cursor = 0;
    var state = ((GuiGraphicsAccessor) g).donutauction$renderState();
    var clip = ((ClipAccess) g).donutauction$clip();
    var setup =
        TextureSetup.singleTexture(
            a.texture.getTextureView(),
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    String content = text.getString();
    for (int offset = 0; offset < content.length(); ) {
      int cp = content.codePointAt(offset);
      offset += Character.charCount(cp);
      Glyph glyph = a.glyphs.get(cp);
      if (glyph == null) {
        glyph = a.add(cp);
        if (glyph != null) a.texture.upload();
      }
      if (glyph == null) {
        g.pose().pushMatrix();
        g.pose().translate(x + cursor * scale, y);
        g.pose().scale(scale, scale);
        g.text(
            net.minecraft.client.Minecraft.getInstance().font,
            new String(Character.toChars(cp)),
            0,
            0,
            Glass.color(rgb, alpha),
            false);
        g.pose().popMatrix();
        cursor += 9;
        continue;
      }
      var pose =
          new Matrix3x2f(g.pose())
              .translate(x + (cursor - .8f) * scale, y - 2.2f * scale)
              .scale(scale / (5 * a.density));
      int cell = glyph.cell, x0 = cell % 12 * a.cellSize, y0 = cell / 12 * a.cellSize;
      state.addGuiElement(
          new BlitRenderState(
              RenderPipelines.GUI_TEXTURED,
              setup,
              pose,
              0,
              0,
              a.cellSize,
              a.cellSize,
              x0 / (float) a.size,
              (x0 + a.cellSize) / (float) a.size,
              y0 / (float) a.size,
              (y0 + a.cellSize) / (float) a.size,
              Glass.color(rgb, alpha),
              clip));
      cursor += glyph.advance;
    }
  }

  public static void close() {
    atlases.values().forEach(a -> a.texture.close());
    atlases.clear();
  }
}
