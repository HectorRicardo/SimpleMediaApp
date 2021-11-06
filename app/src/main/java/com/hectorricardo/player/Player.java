package com.hectorricardo.player;

/**
 * Instances of this class simulate (in silence) the playback of a song.
 *
 * <p>A player can be in one of two states: PLAYING or STOPPED. Initially, when created, the player
 * is in the STOPPED state.
 *
 * <p>When the player enters the PLAYING state (by calling `play()`), a background thread is started
 * and put to sleep for N milliseconds. Ths is to simulate the playback of a song with duration of N
 * milliseconds.
 *
 * <p>It's important to note that the `play()` method returns immediately. It creates a thread and
 * calls the `start()` method on it, but it doesn't actually wait for the thread to start. It just
 * returns immediately. If you want to know when playback actually starts, use the
 * `onPlaybackStarted()` callback.
 *
 * <p>If a `pause()` command is issued while the thread is sleeping, the thread is sent an interrupt
 * signal and, after being interrupted, the `onPause` callback is executed (from that same thread).
 * The thread that called the `pause` command blocks until its corresponding callback finishes
 * executing on the background thread. Then it resumes and handles control back to you.
 *
 * <p>If playback finishes normally, then the `onFinished()` callback is called (from the background
 * thread, which is about to die). If the player is supposed to keep playing after the
 * `onFinished()` callback executes (because it has a repeat mode enabled), a new thread is created
 * and started again.
 *
 * <p>When you call the `seekTo()` command in a PLAYING player, the current thread is interrupted
 * and a new thread is started, just like to the `pause()` command. However, in contrast to the
 * `onPause()` callback, the `onSought()` callback will actually be called from this newly-created
 * thread. Additionally, the thread that called `seekTo()` will only be blocked until the old
 * background thread finishes. This is to be consistent with the `play()` behavior, in the sense
 * that these methods return immediately and do not wait for the new thread to actually start.
 *
 * <p>For the `pause()` and `seekTo()` commands, you should know there's a slim chance that their
 * corresponding callbacks won't run. If you issue either of those commands extremely close before
 * the thread finishes sleeping, there's a chance the `onFinished()` callback will run instead.
 * However, if this happens, the `pause()` command will be "respected" in the sense that it will
 * prevent any subsequent playback if the player has a repeat mode enabled. On the other hand, the
 * `seekTo()` command will be discarded.
 *
 * <p>If the player is STOPPED, then the callbacks will run in the same thread in which the
 * corresponding command instance method was called.
 *
 * <p>All of the methods of this class are thread-safe. This is to ensure that no interleaving will
 * occur between any of the player methods and/or any of the callbacks. This simplifies reasoning
 * about Player instances.
 */
public class Player {

  public static final int REPEAT_NONE = 0;
  public static final int REPEAT_SONG = 1;
  public static final int REPEAT_PLAYLIST = 2;
  private int repeatMode = REPEAT_NONE;

  private StateOps stateOps;
  private Song song;

  // to make sure that pause commands are respected and do indeed prevent further playback
  private boolean pauseRequested;

  public Player(PlayerListener playerListener) {
    stateOps = new StoppedStateOps(null, 0, this, new PlayerListenerInternal(playerListener));
  }

  // Synchronized because a user might call play (wrongly) when the onFinished callback is running.
  // I say wrongly because in reality, you should wait for the onFinished callback to finish running
  // before trying to play again.
  public synchronized void play() {
    stateOps = stateOps.play();
  }

  // Synchronized for the same reason documented above. Also, the user could call this method to
  // change song, but the onFinished callback is still running.
  public synchronized void play(Song song) {
    this.song = song;
    stateOps = stateOps.play(song);
  }

  // This methods blocks until either the background thread finishes (either by calling `onPause()`
  // or `onFinished()`.
  public void pause() {
    issueCommand(
        () -> {
          pauseRequested = true;
          try {
            stateOps.stop();
          } catch (IllegalStateException e) {
            pauseRequested = false;
            throw e;
          }
        });
  }

  public void seekTo(long millis) {
    issueCommand(() -> stateOps.seekTo(millis));
  }

  public synchronized void setRepeatMode(int repeatMode) {
    this.repeatMode = repeatMode;
  }

  private void issueCommand(Runnable runnable) {
    StateOps stateOps;
    synchronized (this) {
      stateOps = this.stateOps;
      runnable.run();
    }
    stateOps.waitToFinish();
  }

  public synchronized long getProgress() {
    return stateOps.getProgress();
  }

  // Most methods of this class will never interleave with the methods of the Player class (because
  // the Player's methods are either synchronized or called from within a synchronized block). If
  // the player is PLAYING, most methods of PlayerListenerInternal are called from within a
  // synchronized block.
  class PlayerListenerInternal {

    private final PlayerListener userFacingPlayerListener;

    private PlayerListenerInternal(PlayerListener userFacingPlayerListener) {
      this.userFacingPlayerListener = userFacingPlayerListener;
    }

    // No need for this method to be synchronized. It doesn't modify any shared state variables.
    // All the player calls that interrupt the background thread won't have any effect if they
    // interleave with these callbacks. It's impossible that any other callback happen before this
    // callback. This is because, when this method executes, even if the interrupt signal has
    // already been sent, it hasn't even been observed by the background thread yet.
    void onThreadStarted(long progress, boolean sought) {
      if (sought) {
        userFacingPlayerListener.onSought(true, progress);
      } else {
        userFacingPlayerListener.onPlaybackStarted(progress);
      }
    }

    Runnable onPaused(StoppedStateOps stateOps) {
      Player.this.stateOps = stateOps;
      return userFacingPlayerListener::onPaused;
    }

    void onFinished() {
      if (pauseRequested) {
        stateOps = new StoppedStateOps(song, 0, Player.this, this);
        pauseRequested = false;
      } else if (repeatMode == REPEAT_NONE) {
        stateOps = new StoppedStateOps(song, 0, Player.this, this);
      } else if (repeatMode == REPEAT_SONG) {
        stateOps = new PlayingStateOps(song, 0, false, Player.this, this);
      } else {
        throw new RuntimeException("Something terribly wrong");
      }
      // This should be inside the synchronized block. Even though it doesn't modify any shared
      // state, the following super rare case could happen: Imagine this statement were outside the
      // synchronized block, and we issued from the main thread a `seekTo()` command. It could be
      // the case that the corresponding `onSought()` callback gets executed first before the
      // onfinished Callback. We ensure this doesn't happen by putting the user-facing onFinished
      // inside the synchronized block.
      userFacingPlayerListener.onFinished();
    }

    void onSought(StateOps stateOps, long progress) {
      Player.this.stateOps = stateOps;
      // If the player is PLAYING, then postpone the call to the callback until the new thread
      // restarts. Else call callback immediately.
      if (!stateOps.isPlaying()) {
        userFacingPlayerListener.onSought(false, progress);
      }
    }

    void onSongSet(StateOps stateOps) {
      Player.this.stateOps = stateOps;
      userFacingPlayerListener.onSongChanged();
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

    void waitToFinish();
  }

  public interface PlayerListener {
    void onPlaybackStarted(long progress);

    void onPaused();

    void onFinished();

    void onSought(boolean playing, long progress);

    void onSongChanged();
  }
}
