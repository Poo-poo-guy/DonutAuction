package donutauction.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import donutauction.client.mixin.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Analytic rounded contour tessellation with a one-framebuffer-pixel coverage fringe. */
public record SmoothShape(
    Matrix3x2fc pose,
    float x,
    float y,
    float w,
    float h,
    float radius,
    int color,
    float feather,
    int steps,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds)
    implements GuiElementRenderState {
  public RenderPipeline pipeline() {
    return RenderPipelines.GUI;
  }

  public TextureSetup textureSetup() {
    return TextureSetup.noTexture();
  }

  public static void draw(
      GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
    float scale =
        (float) Math.hypot(g.pose().m00(), g.pose().m01())
            * Minecraft.getInstance().getWindow().getGuiScale();
    float aa = 1f / Math.max(.01f, scale);
    int steps = Math.clamp((int) Math.ceil(Math.PI * Math.sqrt(Math.max(1, r * scale))), 8, 64);
    var clip = ((ClipAccess) g).donutauction$clip();
    var pose = new Matrix3x2f(g.pose());
    int pad = (int) Math.ceil(aa) + 1;
    var bounds =
        new ScreenRectangle(
                (int) Math.floor(x) - pad,
                (int) Math.floor(y) - pad,
                (int) Math.ceil(w) + pad * 2,
                (int) Math.ceil(h) + pad * 2)
            .transformMaxBounds(pose);
    if (clip != null) bounds = bounds.intersection(clip);
    if (bounds != null)
      ((GuiGraphicsAccessor) g)
          .donutauction$renderState()
          .addGuiElement(
              new SmoothShape(
                  pose,
                  x,
                  y,
                  w,
                  h,
                  Math.min(r, Math.min(w, h) / 2f),
                  color,
                  aa,
                  steps,
                  clip,
                  bounds));
  }

  private static final float[][] COS = new float[65][], SIN = new float[65][];

  static {
    for (int steps = 8; steps <= 64; steps++) {
      COS[steps] = new float[steps * 4];
      SIN[steps] = new float[steps * 4];
      for (int i = 0; i < steps * 4; i++) {
        double angle = i * Math.PI / (2 * steps);
        COS[steps][i] = (float) Math.cos(angle);
        SIN[steps][i] = (float) Math.sin(angle);
      }
    }
  }

  private float px(int i, float rr) {
    int corner = i / steps;
    return x + (corner == 0 || corner == 3 ? w - radius : radius) + COS[steps][i] * rr;
  }

  private float py(int i, float rr) {
    int corner = i / steps;
    return y + (corner < 2 ? h - radius : radius) + SIN[steps][i] * rr;
  }

  private void v(VertexConsumer out, float xx, float yy, int c) {
    int rgb = c & 0xFFFFFF;
    if (donutauction.client.DonutAuctionClient.INSTANCE.config.gradient && h > 50)
      rgb = Glass.mix(rgb, 0xBCD4EF, .035 * Math.clamp(1 - (yy - y) / h, 0, 1));
    out.addVertexWith2DPose(pose, xx, yy).setColor((c & 0xFF000000) | rgb);
  }

  public void buildVertices(VertexConsumer out) {
    int n = steps * 4;
    float inner = Math.max(0, radius - feather * .5f), outer = radius + feather * .5f;
    for (int i = 0; i < n; i++) {
      int j = (i + 1) % n;
      float ax = px(i, inner), ay = py(i, inner), bx = px(j, inner), by = py(j, inner);
      v(out, x + w / 2, y + h / 2, color);
      v(out, bx, by, color);
      v(out, ax, ay, color);
      v(out, x + w / 2, y + h / 2, color);
      v(out, ax, ay, color);
      v(out, bx, by, color);
      v(out, px(j, outer), py(j, outer), color & 0xFFFFFF);
      v(out, px(i, outer), py(i, outer), color & 0xFFFFFF);
    }
  }
}
