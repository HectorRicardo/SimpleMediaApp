package com.hectorricardo.player;

import static com.example.simplemediaapp.Songs.defaultSong;

public class Player {

  private final PlayerListener playerListener;

  private State state = new State(null, 0);

  public Player(PlayerListener playerListener) {
    this.playerListener = playerListener;
  }

  /**
   * This is synchronized because of the following hypothetical scenario: imagine the player started
   * playing a song that lasts 5 seconds, and at the same time an additional thread that sleeps for
   * 5.01 seconds and then issues the play command is spawned. We need to make sure that the
   * onFinished callback finishes first than and doesn't interleave with the new play command issued
   * from the additional thread.
   */
  public synchronized void play() {
    if (state.isPlaying()) {
      throw new RuntimeException("Player already playing!");
    }
    state =
        new State(
            new Thread(
                () -> {
                  System.out.println("Playing " + defaultSong.id + " from " + state.progress);
                  long startedOn = System.currentTimeMillis();
                  try {
                    playerListener.onPlaybackStarted(state.progress);
                    Thread.sleep(defaultSong.duration);
                    synchronized (this) {
                      state = new State(null, 0);
                      playerListener.onFinished();
                    }
                  } catch (InterruptedException ignored) {
                    state =
                        new State(
                            null,
                            Math.min(
                                System.currentTimeMillis() - startedOn + state.progress,
                                defaultSong.duration));
                    playerListener.onPaused(state.progress);
                  }
                }),
            state.progress);
    state.thread.start();
  }

  public void pause() {
    // See comment further below for an explanation of why we have this variable
    State state;

    synchronized (this) {
      if (!this.state.isPlaying()) {
        // To avoid crashing. We need to do this a no-op because otherwise, the state of the player
        // could become PAUSED while we're waiting for the `this` lock to be received. If we take
        // the approach of throwing an exception when pausing an already-PAUSED player, then we
        // would also be throwing an exception on this legitimate situation.
        return;
      }
      state = this.state;
      state.thread.interrupt();
    }
    // It could be that `this.state` was updated after we exited the synchronized block above but
    // before executing the following statement. For this scenario, we use the auxiliary local
    // variable `state` to make sure we wait against the original `state.thread`.
    //
    // How could `this.state` change?
    //
    // When executing `state.thread.interrupt()` above, it could be that there's a context switch
    // and the interruption catch clause is immediately executed. This causes `this.state.thread` to
    // become null, and we would be encountering a NullPointerException below.
    try {
      state.thread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public interface PlayerListener {

    void onPlaybackStarted(long progress);

    void onPaused(long progress);

    void onFinished();
  }

  private static class State {
    final Thread thread;
    final long progress;

    State(Thread thread, long progress) {
      this.thread = thread;
      this.progress = progress;
    }

    boolean isPlaying() {
      return thread != null;
    }
  }
}
