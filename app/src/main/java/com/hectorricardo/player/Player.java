package com.hectorricardo.player;

import static com.example.simplemediaapp.Songs.defaultSong;

public class Player {

  private final PlayerListener playerListener;

  private State state = new State(null, 0);
  private Interruption interruption;

  public Player(PlayerListener playerListener) {
    this.playerListener = playerListener;
  }

  /**
   * This is synchronized because of the following hypothetical scenario: imagine the player started
   * playing a song that lasts 5 seconds. Imagine that, at the same time, an additional thread is
   * spawned. This additional thread sleeps for 5.01 seconds and then issues the play command. We
   * need to make sure that the onFinished callback finishes first than and doesn't interleave with
   * the new play command issued from the spawned thread.
   */
  public synchronized void play() {
    if (state.isPlaying()) {
      throw new RuntimeException("Player already playing!");
    }
    state =
        new State(
            new Thread(
                () -> {
                  playerListener.onPlaybackStarted(state.progress);

                  do {
                    System.out.println("Playing " + defaultSong.id + " from " + state.progress);
                    long startedOn = System.currentTimeMillis();
                    try {
                      Thread.sleep(defaultSong.duration - state.progress);

                      // Song successfully finished playing. We grab the lock while we run the
                      // onFinished logic so we don't interleave with a potential pause command.
                      synchronized (this) {
                        state = new State(null, 0);
                        playerListener.onFinished();
                        break;
                      }
                    } catch (InterruptedException ignored) {
                      if (!interruption.consumeAndClear(startedOn)) {
                        break;
                      }
                    }
                  } while (true);
                }),
            state.progress);
    state.thread.start();
  }

  public void pause() {
    // See comment further below for an explanation of why we have this variable
    State state;

    // Pause command issued. We grab the lock so we don't interleave with the onFinished logic.
    synchronized (this) {
      if (!this.state.isPlaying()) {
        // To avoid crashing. We need to do this a no-op because otherwise, the state of the player
        // could become PAUSED while we're waiting for the `this` lock to be received. If we take
        // the approach of throwing an exception when pausing an already-PAUSED player, then we
        // would also be throwing an exception on this legitimate situation.
        return;
      }
      state = this.state;

      interruption = new PauseInterruption();
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

  public synchronized void seekTo(long progress) {
    if (!this.state.isPlaying()) {
      state = new State(null, progress);
      playerListener.onSoughtTo(progress, false);
      return;
    }

    interruption = new SeekToInterruption(progress);
    state.thread.interrupt();

    try {
      wait();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public interface PlayerListener {

    void onPlaybackStarted(long progress);

    void onPaused(long progress);

    void onSoughtTo(long progress, boolean playing);

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
              null,
              Math.min(
                  System.currentTimeMillis() - startedOn + state.progress, defaultSong.duration));
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
    boolean consume(long startedOn) {
      state = new State(state.thread, progress);
      System.out.println("Seeking to " + progress);
      playerListener.onSoughtTo(state.progress, true);
      synchronized (Player.this) {
        Player.this.notifyAll();
      }
      return true;
    }
  }
}
