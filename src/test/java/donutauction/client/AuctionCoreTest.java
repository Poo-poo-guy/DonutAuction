package donutauction.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AuctionCoreTest {
  @Test
  void timerDoesNotDriftAtAnyFrameRate() {
    for (int fps : new int[] {60, 144, 240, 360}) {
      AtomicLong clock = new AtomicLong();
      AuctionTimer timer = new AuctionTimer(300, clock::get);
      timer.start();
      for (int frame = 0; frame <= fps * 300; frame++) {
        clock.set(Math.round(frame * 1e9 / fps));
        long expected = (Math.max(0, 300_000_000_000L - clock.get()) + 999_999_999) / 1_000_000_000;
        assertEquals(expected, timer.seconds(), "FPS " + fps + " frame " + frame);
      }
      assertFalse(timer.running());
      assertEquals(0, timer.seconds());
    }
  }

  @Test
  void pauseResumeEditsAndStallsRemainExact() {
    AtomicLong clock = new AtomicLong();
    AuctionTimer t = new AuctionTimer(30, clock::get);
    t.start();
    clock.set(1_250_000_000L);
    t.pause();
    assertEquals(28_750_000_000L, t.remainingNanos());
    clock.set(400_000_000_000L);
    assertEquals(28_750_000_000L, t.remainingNanos());
    t.add(10);
    t.start();
    clock.addAndGet(750_000_000L);
    assertEquals(38, t.seconds());
    t.add(-10);
    assertEquals(28, t.seconds());
    clock.addAndGet(60_000_000_000L);
    assertEquals(0, t.seconds());
    t.add(10);
    assertFalse(t.running());
    assertEquals(10, t.seconds());
    t.reset(120);
    assertEquals(120, t.seconds());
  }

  @Test
  void durationsValidateAllSegments() {
    assertEquals(30, AuctionTimer.parse("30"));
    assertEquals(90, AuctionTimer.parse("1:30"));
    assertEquals(359999, AuctionTimer.parse("99:59:59"));
    for (String input :
        new String[] {"0", "-5", "00:99", "1:60", "1:00:61", "100:00:00", "abc", "1:"})
      assertThrows(IllegalArgumentException.class, () -> AuctionTimer.parse(input), input);
    assertEquals("00:01:30", AuctionTimer.format(90));
  }

  @Test
  void reelRetargetingIsContinuousAndWrapsCorrectly() {
    RollingDigit digit = new RollingDigit();
    long start = 1_000_000_000;
    digit.target(0, start, .42, 0, -1);
    digit.target(9, start, .42, 0, -1);
    assertEquals(0, digit.position(start, .42, 0));
    assertEquals(-1, digit.position(start + 420_000_000, .42, 0));
    digit.target(8, start + 1_000_000_000, .42, 0, -1);
    double before = digit.position(start + 1_180_000_000, .42, 0);
    digit.target(2, start + 1_180_000_000, .42, 0, 1);
    assertEquals(before, digit.position(start + 1_180_000_000, .42, 0), 1e-10);
    assertEquals(
        2, RollingDigit.digit((int) Math.round(digit.position(start + 2_000_000_000, .42, 0))));
  }

  @Test
  void allEasingStylesAreMonotonicAndFrameIndependent() {
    for (int style = 0; style < 3; style++)
      for (int fps : new int[] {60, 240}) {
        RollingDigit d = new RollingDigit();
        d.target(5, 0, .42, style, -1);
        d.target(4, 0, .42, style, -1);
        double previous = 5;
        for (int frame = 1; frame <= fps; frame++) {
          double current = d.position(Math.round(frame * 1e9 / fps), .42, style);
          assertTrue(current <= previous + 1e-9);
          assertTrue(current >= 4);
          previous = current;
        }
        assertEquals(4, previous);
      }
  }
}
