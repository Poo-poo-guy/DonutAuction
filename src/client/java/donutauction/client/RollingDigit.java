package donutauction.client;

/** A continuous unwrapped reel; retargeting samples the in-flight position. */
public final class RollingDigit {
  private double from, to;
  private long began;
  private boolean initialized;
  private double activeDuration = .42;
  private int activeStyle;
  private int target = -1;

  public double position(long now, double duration, int style) {
    double t = Math.clamp((now - began) / (activeDuration * 1e9), 0, 1);
    double eased =
        switch (activeStyle) {
          case 1 -> t * t * (3 - 2 * t);
          case 2 -> 1 - Math.pow(1 - t, 5);
          default -> t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
        };
    return from + (to - from) * eased;
  }

  public void target(int value, long now, double duration, int style, int direction) {
    if (!initialized) {
      from = to = value;
      target = value;
      initialized = true;
      return;
    }
    if (target == value) return;
    double current = position(now, duration, style);
    double next = Math.round((current - value) / 10) * 10 + value;
    // Prefer the countdown direction for adjacent changes, while keeping edits short.
    if (Math.abs(next - current) == 5) next = current + 5 * direction;
    from = current;
    to = next;
    began = now;
    target = value;
    activeDuration = duration;
    activeStyle = style;
  }

  public static int digit(int position) {
    return Math.floorMod(position, 10);
  }
}
