package com.hectorricardo.player;

import static com.example.simplemediaapp.Songs.defaultSong;

public class Player {

  private final PlayerListener playerListener;

  private State state = new State(defaultSong, null, 0);
  private Interruption interruption;

  public Player(PlayerListener playerListener) {
    this.playerListener = playerListener;
  }

  // This method is synchronized in order to enforce a happens-before relationship between the
  // `onFinished()` callback and a subsequent play command. Imagine the player started playing a
  // song that lasts 5 seconds and, at the same time, an additional thread is spawned. This
  // additional thread sleeps for 5.01 seconds and then issues a new play command. We need to ensure
  // that the onFinished callback finishes first than and doesn't interleave with the new play
  // command issued from the spawned thread.
  public synchronized void play() {
    if (state.isPlaying()) {
      throw new RuntimeException("Player already playing!");
    }
    if (state.progress < state.song.duration) {
      state = new State(state.song, new Thread(this::run), state.progress);
      state.thread.start();
    } else {
      state = new State(state.song, null, 0);
      playerListener.onFinished();
    }
  }

  public void pause() {
    // See comment further below for an explanation of why we have this variable
    State state;

    // Pause command issued. We grab the lock so we don't interleave with the onFinished logic.
    synchronized (this) {
      if (!this.state.isPlaying()) {
        // To avoid crashing. We need to do this a no-op because otherwise, the state of the player
        // could become PAUSED while we're waiting for the `this` lock to be granted. If we take
        // the approach of throwing an exception when pausing an already-PAUSED player, then we
        // would also be throwing an exception on this legitimate situation.
        return;
      }
      state = this.state;

      interruption = new PauseInterruption();
      state.thread.interrupt();
    }
    // It could be that `this.state` was updated after we exited the synchronized block above but
    // before executing the following statements. That's why we used the auxiliary local variable
    // `state` to make sure we wait against the original `state.thread`.
    //
    // How could `this.state` change?
    //
    // When executing `state.thread.interrupt()` above, it could be that there's a context switch
    // immediately and the interruption catch clause is immediately executed. This causes
    // `this.state.thread` to become null, and we would be encountering a NullPointerException
    // below.
    try {
      state.thread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized void seekTo(long progress) {
    if (!state.isPlaying()) {
      state = new State(state.song, null, progress);
      playerListener.onSoughtTo(progress, false);
      return;
    }

    State state = this.state;
    interruption = new SeekToInterruption(progress);
    state.thread.interrupt();

    // After executing the above `state.thread.interrupt()`, it could be that there's a context
    // switch immediately and the interruption catch clause is immediately executed. This generates
    // a new state and assigns it to the `this.state` property. But it could also be that this
    // situation doesn't happen. So that's why we stored the original state (before the interrupt)
    // in an homonymous local variable. Whenever this two variables aren't equal anymore, we know
    // the interruption was handled, so we unblock.
    //
    // We use a `while` instead of an `if` because of spurious wake-ups.
    while (state == this.state) {
      try {
        wait();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void run() {
    boolean keepAlive;
    playerListener.onPlaybackStarted(state.progress);
    do {
      System.out.println("Playing " + state.song.id + " from " + state.progress);
      long startedOn = System.currentTimeMillis();
      try {
        Thread.sleep(state.song.duration - state.progress);

        synchronized (this) {
          // Song successfully finished playing. There are two possible paths:
          //
          // 1) THE HAPPY PATH: no interruption happened between the time `Thread.sleep()` returned
          // and the time we entered this synchronized block. Just execute `onFinished()` callback.
          // 2) THE PARANOID PATH: An interruption did happen in that small period of time. This
          // interruption already missed the window of opportunity to be handled in the catch block.
          //
          // We need to account for this interruption of Path 2. Why not just ignore it? If we
          // ignored it, we would continue the happy path, executing the `onFinished()` callback
          // with two ramifications:
          // 1.1) The thread will die (onFinished returns false)
          // 1.2) The thread will start a new iteration (onFinished returns true).
          //
          // Path 1.2 is not problematic, because the interruption will be handled the next time the
          // thread sleeps. On the other hand, Path 1.1 is problematic because probably the
          // interruption would have entailed the thread to remain alive, but now it died. In this
          // case, the interruption would have been ignored and will remain until the next time the
          // player starts, which will exhibit wrong behavior (e.g. imagine a pause interruption
          // left pending, and the next time the player starts, it is paused immediately).
          //
          // So it follows that we need to account for the paranoid interruption here
          //
          // NOTE: the interruption consumption can be executed outside of the synchronized block,
          // but for elegance purposes, I'll leave it inside.
          keepAlive = interruption == null ? onFinished() : interruption.consumeAndClear(startedOn);
        }
      } catch (InterruptedException ignored) {
        // Why not put this inside a synchronized block? Because there's no need. Every interruption
        // (user command) is issued from the main thread, and it blocks the main thread until it is
        // handled here, in this catch block (via wait/notify mechanism). It's impossible that the
        // following statement interleaves with anything else.
        keepAlive = interruption.consumeAndClear(startedOn);
      }
    } while (keepAlive);
  }

  private boolean onFinished() {
    state = new State(state.song, null, 0);
    System.out.println("Song finished");
    playerListener.onFinished();
    return false;
  }

  public interface PlayerListener {

    void onPlaybackStarted(long progress);

    void onPaused(long progress);

    void onSoughtTo(long progress, boolean playing);

    void onFinished();
  }

  private static class State {
    final Song song;
    final Thread thread;
    final long progress;

    State(Song song, Thread thread, long progress) {
      this.song = song;
      this.thread = thread;
      this.progress = progress;
    }

    boolean isPlaying() {
      return thread != null;
    }
  }

  private abstract class Interruption {
    boolean consumeAndClear(long startedOn) {
      interruption = null;
      return consume(startedOn);
    }

    abstract boolean consume(long startedOn);
  }

  private class PauseInterruption extends Interruption {
    @Override
    boolean consume(long startedOn) {
      state =
          new State(
              state.song,
              null,
              Math.min(
                  System.currentTimeMillis() - startedOn + state.progress, state.song.duration));
      System.out.println("Paused on " + state.progress);
      playerListener.onPaused(state.progress);
      return false;
    }
  }

  private class SeekToInterruption extends Interruption {
    private final long progress;

    SeekToInterruption(long progress) {
      this.progress = progress;
    }

    @Override
    boolean consume(long ignored) {
      boolean keepAlive;
      if (progress < state.song.duration) {
        System.out.println("Seeking to " + progress);
        state = new State(state.song, state.thread, progress);
        playerListener.onSoughtTo(progress, true);
        keepAlive = true;
      } else {
        keepAlive = onFinished();
      }
      synchronized (Player.this) {
        Player.this.notifyAll();
      }
      return keepAlive;
    }
  }
}
