package donutauction.client;

/** Analytic velocity integral: rotation is independent of frame count and never snaps. */
public final class EndingAnimation {
  private long cycle = -1, epoch;
  private double angle, normalSpeed, lastTime;
  private boolean ending;
  private long endedAt;
  private double frozenDuration, frozenMultiplier;
  private double returningFrom = 1, returnAt = -100;

  public static double ease(double t) {
    t = Math.clamp(t, 0, 1);
    return t * t * (3 - 2 * t);
  }

  private static double primitive(double t) {
    return t * t * t - .5 * t * t * t * t;
  }

  public static double integral(double t, double multiplier) {
    t = Math.clamp(t, 0, 1);
    double a = Math.min(t, .2), value = a + (multiplier - 1) * .2 * primitive(a / .2);
    if (t > .2) value += multiplier * (Math.min(t, .55) - .2);
    if (t > .55) {
      double u = (t - .55) / .45;
      value += multiplier * .45 * (u - primitive(u));
    }
    return value;
  }

  public double sample(
      long now,
      long round,
      boolean ended,
      long deadline,
      double speed,
      double duration,
      double multiplier,
      boolean enabled) {
    double time = now / 1e9;
    if (cycle < 0) {
      cycle = round;
      epoch = now;
      normalSpeed = speed;
      lastTime = time;
    }
    if (cycle != round || (!ended && ending)) {
      angle = current(time, duration, multiplier);
      returningFrom =
          ending ? velocity((time - endedAt / 1e9) / frozenDuration, frozenMultiplier) : 1;
      returnAt = time;
      epoch = now;
      ending = false;
      cycle = round;
    }
    if (!ending && ended) {
      angle = current(deadline / 1e9, duration, multiplier);
      epoch = deadline;
      endedAt = deadline;
      frozenDuration = duration;
      frozenMultiplier = multiplier;
      ending = true;
      if (!enabled) normalSpeed = 0;
    }
    if (!ending && normalSpeed != speed) {
      angle = current(time, duration, multiplier);
      epoch = now;
      normalSpeed = speed;
      returnAt = -100;
    }
    lastTime = time;
    return current(time, duration, multiplier);
  }

  private double current(double time, double duration, double mult) {
    double dt = Math.max(0, time - epoch / 1e9);
    if (ending)
      return angle + normalSpeed * frozenDuration * integral(dt / frozenDuration, frozenMultiplier);
    double u = Math.clamp((time - returnAt) / .3, 0, 1);
    double ramp =
        returnAt < 0
            ? dt
            : Math.min(dt, .3) * returningFrom
                + (1 - returningFrom) * .3 * primitive(u)
                + Math.max(0, dt - .3);
    return angle + normalSpeed * ramp;
  }

  public static double velocity(double t, double m) {
    return t < .2 ? 1 + (m - 1) * ease(t / .2) : t < .55 ? m : m * (1 - ease((t - .55) / .45));
  }

  public double progress(long now, double duration) {
    return ending ? Math.clamp((now - endedAt) / (frozenDuration * 1e9), 0, 1) : 0;
  }

  public boolean ending() {
    return ending;
  }
}
