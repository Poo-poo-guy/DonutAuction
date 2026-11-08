package donutauction.client;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

public final class AuctionConfig {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  public static final Path PATH =
      FabricLoader.getInstance().getConfigDir().resolve("donutauction.json");
  public boolean visible = true, shadow = true;
  public double x = .04,
      y = .08,
      scale = .65,
      opacity = .82,
      radius = 16,
      rotationSpeed = 32,
      animationSeconds = .42;
  public int background = 0x101725,
      accent = 0x9EF0CB,
      text = 0xEDF3FA,
      timer = 0xFFFFFF,
      urgent = 0xFF8EAB;
  public int schemaVersion = 2, uiFont = 0, timerFont = 2, endingFont = 2, fontWeight = 1;
  public double textSize = 1,
      timerSize = 1,
      endingSize = 1.5,
      endingDuration = 2.8,
      endingMultiplier = 4,
      hudWidth = 328,
      hudHeight = 116,
      itemSize = 56;
  public boolean endingEnabled = true,
      endingGlow = true,
      showHours = false,
      itemCircle = true,
      borders = true,
      gradient = true;
  public String endingText = "AUCTION ENDED!";
  public int endingColor = 0x9EF0CB;
  public Map<String, JsonObject> auctionPresets = new LinkedHashMap<>();
  public int font = 0, animationStyle = 0;
  public long duration = 120;
  public List<Long> presets = new ArrayList<>(List.of(30L, 60L, 120L, 300L));
  public JsonElement selectedItem;

  public static AuctionConfig load() {
    if (Files.exists(PATH)) {
      try {
        AuctionConfig c = GSON.fromJson(Files.readString(PATH), AuctionConfig.class);
        if (c == null) throw new IOException("Empty configuration");
        JsonObject original = JsonParser.parseString(Files.readString(PATH)).getAsJsonObject();
        if (!original.has("schemaVersion")) {
          c.uiFont = 0;
          c.timerFont = 2;
          c.endingFont = 2;
        }
        c.validate();
        return c;
      } catch (Exception e) {
        DonutAuctionClient.LOG.warn("Invalid configuration; preserving a backup", e);
        try {
          Files.copy(
              PATH,
              PATH.resolveSibling("donutauction.invalid-" + System.currentTimeMillis() + ".json"));
        } catch (IOException backup) {
          DonutAuctionClient.LOG.warn("Could not back up configuration", backup);
        }
      }
    }
    return new AuctionConfig();
  }

  public void validate() {
    x = finite(x, .04, 0, 1);
    y = finite(y, .08, 0, 1);
    scale = finite(scale, .65, .5, 2);
    opacity = finite(opacity, .82, .1, 1);
    radius = finite(radius, 16, 0, 24);
    rotationSpeed = finite(rotationSpeed, 32, -180, 180);
    animationSeconds = finite(animationSeconds, .42, .15, .85);
    font = Math.clamp(font, 0, 2);
    uiFont = Math.clamp(uiFont, 0, 3);
    timerFont = Math.clamp(timerFont, 0, 3);
    endingFont = Math.clamp(endingFont, 0, 3);
    fontWeight = Math.clamp(fontWeight, 0, 2);
    textSize = finite(textSize, 1, .6, 1.5);
    timerSize = finite(timerSize, 1, .6, 1.3);
    endingSize = finite(endingSize, 1.5, .6, 2.5);
    endingDuration = finite(endingDuration, 2.8, .5, 8);
    endingMultiplier = finite(endingMultiplier, 4, 1, 8);
    hudWidth = finite(hudWidth, 328, 260, 520);
    hudHeight = finite(hudHeight, 116, 100, 180);
    itemSize = finite(itemSize, 56, 28, 68);
    if (endingText == null) endingText = "AUCTION ENDED!";
    endingText = endingText.substring(0, Math.min(64, endingText.length()));
    if (auctionPresets == null) auctionPresets = new LinkedHashMap<>();
    animationStyle = Math.clamp(animationStyle, 0, 2);
    duration = Math.clamp(duration, 1, AuctionTimer.MAX_SECONDS);
    if (presets == null) presets = new ArrayList<>();
    presets =
        new ArrayList<>(
            presets.stream()
                .filter(Objects::nonNull)
                .filter(v -> v > 0 && v <= AuctionTimer.MAX_SECONDS)
                .distinct()
                .limit(8)
                .toList());
    background &= 0xFFFFFF;
    accent &= 0xFFFFFF;
    text &= 0xFFFFFF;
    timer &= 0xFFFFFF;
    urgent &= 0xFFFFFF;
  }

  private static double finite(double v, double fallback, double min, double max) {
    return Double.isFinite(v) ? Math.clamp(v, min, max) : fallback;
  }

  public void savePreset(String name) {
    JsonObject value = GSON.toJsonTree(this).getAsJsonObject();
    value.remove("auctionPresets");
    value.remove("presets");
    auctionPresets.put(name, value);
    save();
  }

  public AuctionConfig preset(String name) {
    JsonObject j = auctionPresets.get(name);
    if (j == null) return null;
    AuctionConfig c = GSON.fromJson(j, AuctionConfig.class);
    c.auctionPresets = auctionPresets;
    c.presets = new ArrayList<>(presets);
    c.validate();
    return c;
  }

  public void save() {
    validate();
    try {
      Files.createDirectories(PATH.getParent());
      Path temp = PATH.resolveSibling("donutauction.json.tmp");
      Files.writeString(temp, GSON.toJson(this));
      try {
        Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      DonutAuctionClient.LOG.error("Could not save DonutAuction+ settings", e);
    }
  }
}
