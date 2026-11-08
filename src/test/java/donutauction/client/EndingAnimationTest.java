package donutauction.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class EndingAnimationTest {
  @Test
  void spinIsIndependentOfFrameRateAndStops() {
    double expected = Double.NaN;
    for (int fps : new int[] {60, 144, 240, 360}) {
      EndingAnimation a = new EndingAnimation();
      a.sample(0, 1, false, 0, 32, 2.8, 4, true);
      double angle = 0;
      for (int i = 0; i <= fps * 6; i++) {
        long now = Math.round(i * 1e9 / fps);
        angle = a.sample(now, 1, now >= 1_000_000_000L, 1_000_000_000L, 32, 2.8, 4, true);
      }
      if (Double.isNaN(expected)) expected = angle;
      else assertEquals(expected, angle, 1e-7);
      assertEquals(
          angle, a.sample(100_000_000_000L, 1, true, 1_000_000_000L, 32, 2.8, 4, true), 1e-7);
    }
  }

  @Test
  void interruptedEndingKeepsItsAngle() {
    EndingAnimation a = new EndingAnimation();
    a.sample(0, 1, false, 0, 32, 2.8, 4, true);
    double before = a.sample(2_000_000_000L, 1, true, 1_000_000_000L, 32, 2.8, 4, true);
    double after = a.sample(2_000_000_000L, 2, false, 0, 32, 2.8, 4, true);
    assertEquals(before, after, 1e-8);
    assertFalse(a.ending());
    assertTrue(a.sample(3_000_000_000L, 2, false, 0, 32, 2.8, 4, true) > after);
  }

  @Test
  void timerCyclesResetOnlyAtAuctionBoundaries() {
    AtomicLong clock = new AtomicLong();
    AuctionTimer t = new AuctionTimer(2, clock::get);
    long round = t.cycle();
    t.start();
    t.pause();
    t.start();
    assertEquals(round, t.cycle());
    clock.set(3_000_000_000L);
    assertTrue(t.ended());
    t.add(10);
    assertFalse(t.ended());
    assertEquals(round + 1, t.cycle());
    assertEquals(10, t.seconds());
    t.reset(1);
    t.start();
    clock.addAndGet(2_000_000_000L);
    assertTrue(t.ended());
    t.start();
    assertFalse(t.ended());
    assertTrue(t.running());
    assertEquals(1, t.seconds());
  }
}
