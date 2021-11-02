package com.hectorricardo.player;

/**
 * Instances of this class simulate playback of a song. A player can be in one of two states:
 * PLAYING or STOPPED.
 *
 * <p>When the player enters the PLAYING state, a background thread is started and put to sleep for
 * N milliseconds. Ths is to simulate the playback of a song with duration of N milliseconds.
 *
 * <p>If a pause, seekTo, or setSong command is issued while the thread is sleeping, the thread is
 * sent an interrupt signal and, after being interrupted, the corresponding callback is executed
 * (from that same thread). If playback finishes normally, then the onFinished callback is called
 * (still from that same thread). If needed, a new thread is created and started again.
 *
 * <p>On the other hand, if no such thread exists (i.e, the player is STOPPED), then the callbacks
 * will run in the same thread in which the corresponding instance method was called.
 *
 * <p>If you issue a command to a PLAYING player, you should wait for it's corresponding callback to
 * run before issuing a subsequent command. For example, if you pause a player, you should wait for
 * the onPaused callback to run before issuing a subsequent command to the player.
 *
 * <p>(For the pause and seekTo commands, you should know there's a slim chance that their
 * corresponding callbacks won't run. If you issue either of those commands extremely close before
 * the thread finishes sleeping, there's a chance the onFinished callback will run instead. However,
 * if this happens, the pause command will prevent any subsequent playback if the player has a
 * repeat mode enabled. On the other hand, the seekTo command will be ignored).
 *
 * <p>If the player is PLAYING, the time between the issuing of a command and it's corresponding
 * callback being run is extremely small. And the player command and callbacks will run on different
 * threads. Because of this, we took the following 2 decisions:
 *
 * <p>1. All of the methods of this class are thread-safe. No interleaving will occur between any of
 * the player methods and/or any of the callbacks. This simplifies reasoning about Player instances.
 *
 * <p>2. Methods run validation checks before executing its logic (for example, we check if the
 * player is not already stopped before issuing the stop command). If validation fails, then the
 * method is simply a no-op (instead of throwing an invalid operation exception).
 *
 * <p>Point number# 2 is important because of the following:
 *
 * <p>1. As we previously said, if the player is PLAYING, the time window from when a playback
 * command is issued to when it's corresponding callback is run is very short. During this small
 * period of time, any issued commands will be invalid. However, it might be costly or useless to
 * update the UI to disable the controls/button to avoid issuing a command from within this small
 * period or time.
 *
 * <p>1. If a callback is running and an issued command is blocked because of this (for example, the
 * pause command was blocked because of the onFinished callback), then when the command gets back
 * control, it will check if the player is in a valid state to receive that command (because maybe
 * that state changed when the callback was running).
 */
public class Player {

  public static final int REPEAT_NONE = 0;
  public static final int REPEAT_SONG = 1;
  public static final int REPEAT_PLAYLIST = 2;

  private StateOps stateOps;
  private Song song;
  private int repeatMode = REPEAT_NONE;

  // to make sure that pause commands do indeed prevent further playback
  private boolean pauseRequested;

  public Player(PlayerListener playerListener) {
    stateOps = new StoppedStateOps(null, 0, this, new PlayerListenerInternal(playerListener));
  }

  public synchronized void play() {
    if (stateOps.isPlaying()) {
      return;
    }
    stateOps = stateOps.play();
  }

  public synchronized void play(Song song) {
    if (stateOps.isPlaying()) {
      if (this.song != song) {
        throw new IllegalStateException("");
      }
      return;
    }
    this.song = song;
    stateOps = stateOps.play(song);
  }

  public synchronized void pause() {
    if (!stateOps.isPlaying()) {
      return;
    }
    pauseRequested = true;
    stateOps.stop();
  }

  public synchronized long getProgress() {
    return stateOps.getProgress();
  }

  public synchronized void seekTo(long millis) {
    stateOps.seekTo(millis);
  }

  public synchronized void setRepeatMode(int repeatMode) {
    this.repeatMode = repeatMode;
  }

  // If the player is in the PLAYING state, the methods of this class will never interleave with
  // the (synchronized) methods of the Player class.
  class PlayerListenerInternal {

    private final PlayerListener playerListener;

    private PlayerListenerInternal(PlayerListener playerListener) {
      this.playerListener = playerListener;
    }

    void onPlaybackStarted(long progress) {
      playerListener.onPlaybackStarted(progress);
    }

    void onPaused(StoppedStateOps stateOps) {
      Player.this.stateOps = stateOps;
      pauseRequested = false;
      playerListener.onPaused();
    }

    void onFinished() {
      if (pauseRequested) {
        pauseRequested = false;
        stateOps = newStoppedStateOps();
      } else if (repeatMode == REPEAT_NONE) {
        stateOps = newStoppedStateOps();
      } else if (repeatMode == REPEAT_SONG) {
        stateOps = new PlayingStateOps(song, 0, Player.this, this);
      } else {
        throw new RuntimeException("Something terribly wrong");
      }
      playerListener.onFinished();
    }

    void onSought(StateOps stateOps, long progress) {
      Player.this.stateOps = stateOps;
      playerListener.onSought(stateOps.isPlaying(), progress);
    }

    void onSongSet(StateOps stateOps) {
      Player.this.stateOps = stateOps;
      playerListener.onSongChanged();
    }

    private StoppedStateOps newStoppedStateOps() {
      return new StoppedStateOps(song, 0, Player.this, this);
    }
  }

  interface StateOps {
    PlayingStateOps play();

    PlayingStateOps play(Song song);

    void stop();

    long getProgress();

    void seekTo(long millis);

    void changeSong(Song song);

    boolean isPlaying();
  }

  public interface PlayerListener {
    void onPlaybackStarted(long progress);

    void onPaused();

    void onFinished();

    void onSought(boolean playing, long progress);

    void onSongChanged();
  }
}
