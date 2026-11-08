package donutauction.client.mixin;

import donutauction.client.render.ClipAccess;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiClipMixin implements ClipAccess {
  @Unique private final Deque<ScreenRectangle> donutauction$clips = new ArrayDeque<>();

  @Inject(method = "enableScissor", at = @At("TAIL"))
  private void clip(int x, int y, int x1, int y1, CallbackInfo ci) {
    var g = (GuiGraphicsExtractor) (Object) this;
    ScreenRectangle r =
        new ScreenRectangle(x, y, Math.max(0, x1 - x), Math.max(0, y1 - y))
            .transformAxisAligned(g.pose());
    if (!donutauction$clips.isEmpty()) {
      r = r.intersection(donutauction$clips.peek());
      if (r == null) r = ScreenRectangle.empty();
    }
    donutauction$clips.push(r);
  }

  @Inject(method = "disableScissor", at = @At("TAIL"))
  private void unclip(CallbackInfo ci) {
    if (!donutauction$clips.isEmpty()) donutauction$clips.pop();
  }

  public ScreenRectangle donutauction$clip() {
    return donutauction$clips.peek();
  }
}
