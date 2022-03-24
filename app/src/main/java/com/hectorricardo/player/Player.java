package com.hectorricardo.player;

import static com.example.simplemediaapp.Songs.defaultSong;

public class Player {

  private final PlayerListener playerListener;

  private State state = new State(null, 0);

  public Player(PlayerListener playerListener) {
    this.playerListener = playerListener;
  }

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
