package com.hectorricardo.player;

/**
 * To simulate playing a song with duration of N milliseconds, a background thread is started and
 * put to sleep for N milliseconds.
 */
public class Player {

  public static final int REPEAT_NONE = 0;
  public static final int REPEAT_SONG = 1;

  private volatile StateOps stateOps;

  private Song song;
  private int repeatMode = REPEAT_NONE;
  private boolean pauseRequested;

  public Player(PlayerListener playerListener) {
    stateOps = new StoppedStateOps(null, 0, new PlayerListenerInternal(playerListener));
  }

  public void play() {
    if (song == null) {
      throw new IllegalStateException("No song set");
    }
    //noinspection NonAtomicOperationOnVolatileField
    stateOps = stateOps.play();
  }

  // Can't be interleaving.
  public void play(Song song) {
    this.song = song;
    //noinspection NonAtomicOperationOnVolatileField
    stateOps = stateOps.play(song);
  }

  public void pause() {
    synchronized (this) {
      pauseRequested = true;
    }
    stateOps.stop();
  }

  public long getProgress() {
    return stateOps.getProgress();
  }

  public void setProgress(long millis) {
    stateOps.setProgress(millis);
  }

  public void changeSong(Song song) {
    synchronized (this) {
      this.song = song;
    }
    stateOps.changeSong(song);
  }

  public void setRepeatMode(int repeatMode) {
    synchronized (this) {
      this.repeatMode = repeatMode;
    }
  }

  interface StateOps {
    PlayingStateOps play();

    PlayingStateOps play(Song song);

    void stop();

    long getProgress();

    void setProgress(long millis);

    void changeSong(Song song);
  }

  public interface PlayerListener {
    default void onPaused() {}

    default void onFinished() {}

    default void onSought(boolean playing, long progress) {}

    default void onSongChanged() {}
  }

  class PlayerListenerInternal {

    private final PlayerListener playerListener;

    PlayerListenerInternal(PlayerListener playerListener) {
      this.playerListener = playerListener;
    }

    public void onPaused(StoppedStateOps stateOps) {
      Player.this.stateOps = stateOps;
      pauseRequested = false;
      playerListener.onPaused();
    }

    // Interleaving method
    public void onFinished() {
      synchronized (Player.this) {
        if (!pauseRequested) {
          if (repeatMode == REPEAT_NONE) {
            stateOps = new StoppedStateOps(song, 0, this);
          } else if (repeatMode == REPEAT_SONG) {
            stateOps = new PlayingStateOps(song, 0, this);
          }
        }
        pauseRequested = false;
      }
      playerListener.onFinished();
    }

    public void onSought(StateOps stateOps, long progress) {
      Player.this.stateOps = stateOps;
      playerListener.onSought(stateOps.getClass().equals(PlayingStateOps.class), progress);
    }

    public void onSongSet(StateOps stateOps) {
      Player.this.stateOps = stateOps;
      playerListener.onSongChanged();
    }
  }
}
