package donutauction.client;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import donutauction.client.render.*;
import donutauction.client.ui.AuctionScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DonutAuctionClient implements ClientModInitializer {
  public static final Logger LOG = LoggerFactory.getLogger("donutauction");
  public static DonutAuctionClient INSTANCE;
  public AuctionConfig config;
  public AuctionTimer timer;
  public HudRenderer hud;
  private ItemStack selected = ItemStack.EMPTY;
  private Object decodedConnection;
  private boolean studioOpened;
  public KeyMapping[] keys;
  public static final String[] ACTIONS = {
    "Toggle HUD",
    "Open settings",
    "Start / pause",
    "Reset timer",
    "Use held item",
    "Add 10 seconds",
    "Subtract 10 seconds"
  };

  @Override
  public void onInitializeClient() {
    INSTANCE = this;
    config = AuctionConfig.load();
    timer = new AuctionTimer(config.duration);
    hud = new HudRenderer(this);
    var category = KeyMapping.Category.register(id("controls"));
    int[] defaults = {
      InputConstants.KEY_F6,
      InputConstants.KEY_F7,
      InputConstants.KEY_F8,
      InputConstants.KEY_F9,
      InputConstants.KEY_F10,
      InputConstants.KEY_EQUALS,
      InputConstants.KEY_MINUS
    };
    String[] ids = {"toggle", "settings", "start_pause", "reset", "held", "add", "subtract"};
    keys = new KeyMapping[ids.length];
    for (int i = 0; i < keys.length; i++)
      keys[i] =
          KeyMappingHelper.registerKeyMapping(
              new KeyMapping(
                  "key.donutauction." + ids[i],
                  InputConstants.Type.KEYBOARD,
                  defaults[i],
                  category));
    PictureInPictureRendererRegistry.register(context -> new SpinningItem());
    HudElementRegistry.addLast(
        id("auction"),
        (g, delta) -> {
          Minecraft mc = Minecraft.getInstance();
          if (mc.player != null && !(mc.gui.screen() instanceof AuctionScreen))
            hud.renderOverlay(g);
        });
    ClientTickEvents.END_CLIENT_TICK.register(
        mc -> {
          if (mc.getConnection() != decodedConnection) {
            decodedConnection = mc.getConnection();
            if (decodedConnection != null) {
              if (selected.isEmpty()) selected = new ItemStack(Items.DIAMOND);
              restoreItem(mc);
            }
          }
          if (Boolean.getBoolean("donutauction.openStudio")
              && !studioOpened
              && mc.player != null
              && mc.level != null) {
            studioOpened = true;
            mc.gui.setScreen(new AuctionScreen(this));
          }
          if (mc.gui.screen() != null) return;
          for (int i = 0; i < keys.length; i++) while (keys[i].consumeClick()) action(i);
        });
    ClientLifecycleEvents.CLIENT_STOPPING.register(
        mc -> {
          config.save();
          PremiumFonts.close();
        });
    LOG.info("DonutAuction+ loaded for Minecraft 26.3");
  }

  public static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath("donutauction", path);
  }

  public ItemStack selected() {
    return selected;
  }

  private RegistryOps<JsonElement> ops(Minecraft mc) {
    return RegistryOps.create(
        JsonOps.INSTANCE,
        mc.level != null
            ? mc.level.registryAccess()
            : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
  }

  private void restoreItem(Minecraft mc) {
    if (config.selectedItem == null) return;
    ItemStack.CODEC
        .parse(ops(mc), config.selectedItem)
        .resultOrPartial(message -> LOG.warn("Could not decode saved auction item: {}", message))
        .filter(stack -> !stack.isEmpty())
        .ifPresent(stack -> selected = stack.copy());
  }

  public boolean select(ItemStack stack) {
    if (stack.isEmpty()) return false;
    selected = stack.copy();
    timer.reset(config.duration);
    var encoded = ItemStack.CODEC.encodeStart(ops(Minecraft.getInstance()), selected);
    var value =
        encoded.resultOrPartial(message -> LOG.warn("Could not persist auction item: {}", message));
    if (value.isPresent()) {
      config.selectedItem = value.get();
      config.save();
      return true;
    }
    return false;
  }

  public boolean useHeld() {
    var mc = Minecraft.getInstance();
    return mc.player != null && select(mc.player.getMainHandItem());
  }

  public void duration(long seconds) {
    config.duration = seconds;
    timer.reset(seconds);
    config.save();
  }

  public void loadPreset(String name) {
    AuctionConfig next = config.preset(name);
    if (next != null) {
      config = next;
      restoreItem(Minecraft.getInstance());
      timer.reset(config.duration);
      config.save();
    }
  }

  public void toggleTimer() {
    if (timer.ended()) timer.reset(config.duration);
    timer.toggle();
  }

  public void action(int action) {
    switch (action) {
      case 0 -> {
        config.visible = !config.visible;
        config.save();
      }
      case 1 -> Minecraft.getInstance().gui.setScreen(new AuctionScreen(this));
      case 2 -> toggleTimer();
      case 3 -> timer.reset(config.duration);
      case 4 -> useHeld();
      case 5 -> timer.add(10);
      case 6 -> timer.add(-10);
    }
  }
}
