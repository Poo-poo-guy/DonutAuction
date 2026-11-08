package donutauction.client;

import java.util.function.LongSupplier;

/** Monotonic, deadline-based timer. Never accumulates frame or tick deltas. */
public final class AuctionTimer {
  public static final long MAX_SECONDS = 359999; // 99:59:59
  private final LongSupplier clock;
  private long remaining, deadline;
  private boolean running;
  private long revision;

  public AuctionTimer(long seconds) {
    this(seconds, System::nanoTime);
  }

  public AuctionTimer(long seconds, LongSupplier clock) {
    this.clock = clock;
    reset(seconds);
  }

  public long remainingNanos() {
    if (!running) return remaining;
    long value = Math.max(0, deadline - clock.getAsLong());
    if (value == 0) {
      remaining = 0;
      running = false;
    }
    return value;
  }

  public long revision() {
    return revision;
  }

  public long seconds() {
    return (remainingNanos() + 999_999_999L) / 1_000_000_000L;
  }

  public boolean running() {
    remainingNanos();
    return running;
  }

  public void start() {
    if (!running && remaining > 0) {
      deadline = clock.getAsLong() + remaining;
      running = true;
      revision++;
    }
  }

  public void pause() {
    remaining = remainingNanos();
    running = false;
    revision++;
  }

  public void toggle() {
    if (running()) pause();
    else start();
  }

  public void reset(long seconds) {
    revision++;
    running = false;
    remaining = Math.clamp(seconds, 0, MAX_SECONDS) * 1_000_000_000L;
  }

  public void add(long seconds) {
    revision++;
    long value =
        Math.clamp(remainingNanos() + seconds * 1_000_000_000L, 0, MAX_SECONDS * 1_000_000_000L);
    remaining = value;
    if (running) deadline = clock.getAsLong() + value;
    if (value == 0) running = false;
  }

  public static String format(long seconds) {
    seconds = Math.clamp(seconds, 0, MAX_SECONDS);
    return String.format(
        java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
  }

  public static long parse(String text) {
    String[] parts = text.trim().split(":", -1);
    if (parts.length > 3 || parts.length == 0)
      throw new IllegalArgumentException("Use seconds, mm:ss, or hh:mm:ss");
    long seconds = 0;
    for (int index = 0; index < parts.length; index++) {
      String part = parts[index];
      if (!part.matches("[0-9]{1,6}")) throw new IllegalArgumentException("Use positive numbers");
      long value = Long.parseLong(part);
      if (index > 0 && value > 59)
        throw new IllegalArgumentException("Minutes and seconds must be below 60");
      seconds = seconds * 60 + value;
    }
    if (seconds < 1 || seconds > MAX_SECONDS)
      throw new IllegalArgumentException("Choose 1 second to 99:59:59");
    return seconds;
  }
}
