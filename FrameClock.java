import java.util.concurrent.locks.LockSupport;

public final class FrameClock {
  private static final long NS_PER_SEC = 1_000_000_000L;
  private static final long ONE_MS_NS = 1_000_000L;

  private final int baseFps;
  private long frameNs;
  private long maxLateNs;
  private long deadline;

  public FrameClock(int fps) {
    baseFps = fps;
    frameNs = NS_PER_SEC / fps;
    maxLateNs = 4L * frameNs;
    reset();
  }

  public void setSpeed(int speed) {
    if (speed < 1) speed = 1;
    if (speed > 4) speed = 4;

    frameNs = NS_PER_SEC / (baseFps * speed);
    maxLateNs = 4L * frameNs;
    deadline = System.nanoTime();
  }

  public void reset() {
    deadline = System.nanoTime();
  }

  public void endFrameAndSleep() {
    deadline += frameNs;

    long now = System.nanoTime();

    // If a pause, debugger step, window resize, GC stall, or audio stall made
    // this frame much later than expected, re-anchor the clock instead of
    // trying to catch up with impossible deadlines.
    if (now - deadline > maxLateNs) {
      deadline = now + frameNs;
      return;
    }

    long remaining = deadline - System.nanoTime();

    while (remaining > 0) {
      try {
        if (remaining > ONE_MS_NS) {
          Thread.sleep(remaining / ONE_MS_NS, (int) (remaining % ONE_MS_NS));
        } else {
          LockSupport.parkNanos(remaining);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }

      remaining = deadline - System.nanoTime();
    }
  }
}
