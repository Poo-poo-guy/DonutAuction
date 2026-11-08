package donutauction.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.textures.FilterMode;
import donutauction.client.mixin.GuiGraphicsAccessor;
import donutauction.client.mixin.PictureInPictureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Uses the vanilla item model resolver and PiP renderer, including glint and custom models. */
public final class SpinningItem extends PictureInPictureRenderer<SpinningItem.State> {
  public record State(
      ItemStackRenderState item,
      float angle,
      float alpha,
      int x0,
      int y0,
      int x1,
      int y1,
      float scale,
      Matrix3x2fc pose,
      ScreenRectangle scissorArea,
      ScreenRectangle bounds)
      implements PictureInPictureRenderState {}

  public static void extract(
      GuiGraphicsExtractor g,
      ItemStack stack,
      int x,
      int y,
      int size,
      float angle,
      double alpha,
      ScreenRectangle clip) {
    Minecraft mc = Minecraft.getInstance();
    ItemStackRenderState item = new ItemStackRenderState();
    mc.getItemModelResolver()
        .updateForTopItem(item, stack, ItemDisplayContext.FIXED, mc.level, mc.player, 0);
    Matrix3x2f pose = new Matrix3x2f(g.pose());
    ScreenRectangle bounds =
        PictureInPictureRenderState.getBounds(x, y, x + size, y + size, pose, clip);
    ((GuiGraphicsAccessor) g)
        .donutauction$renderState()
        .addPicturesInPictureState(
            new State(
                item,
                angle,
                (float) alpha,
                x,
                y,
                x + size,
                y + size,
                size * .72f,
                pose,
                clip,
                bounds));
  }

  @Override
  public Class<State> getRenderStateClass() {
    return State.class;
  }

  @Override
  protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
    pose.pushPose();
    pose.scale(1, -1, -1);
    Minecraft.getInstance()
        .gameRenderer
        .lighting()
        .setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ITEMS_3D);
    pose.rotateDegrees(Axis.XP, 12);
    pose.rotateDegrees(Axis.YP, state.angle());
    state.item().submit(pose, collector, 15728880, 655360, 0);
    pose.popPose();
  }

  @Override
  protected void blitTexture(State state, GuiRenderState guiState) {
    // PiP targets use premultiplied alpha, so fade RGB and alpha together.
    int a = (int) Math.round(Math.clamp(state.alpha(), 0, 1) * 255);
    int tint = (a << 24) | (a << 16) | (a << 8) | a;
    guiState.addBlitToCurrentLayer(
        new BlitRenderState(
            RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.singleTexture(
                ((PictureInPictureAccessor) (Object) this).donutauction$textureView(),
                RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
            state.pose(),
            state.x0(),
            state.y0(),
            state.x1(),
            state.y1(),
            0,
            1,
            1,
            0,
            tint,
            state.scissorArea(),
            null));
  }

  @Override
  protected float getTranslateY(int height, int guiScale) {
    return height / 2f;
  }

  @Override
  protected String getTextureLabel() {
    return "donutauction_rotating_item";
  }
}
