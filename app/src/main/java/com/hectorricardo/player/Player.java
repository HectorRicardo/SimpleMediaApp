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
 * about Player instances. Although in reality, if you just issue player commands from a single
 * thread or from within the callbacks (that could be running in the about-to-die background
 * thread), only the following methods and pieces of code should be synchronized: TODO complete
 * list.
 */
public class Player {

  private StateOps stateOps;
  private Song song;

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
  // I initially had the idea that this method could return a boolean indicated whether the call to
  // this method caused the onPause callback to be called (99% will be yes, see the comment about
  // the slim chance above in the class's documentation). However,
  public void pause() {
    issueCommand(stateOps::stop);
  }

  public void seekTo(long millis) {
    issueCommand(() -> stateOps.seekTo(millis));
  }

  private void issueCommand(Runnable runnable) {
    StateOps stateOps;
    synchronized (this) {
      stateOps = this.stateOps;
      runnable.run();
    }
    // From this point onwards, it could be that `stateOps != this.stateOps`. How?
    //
    // If we commanded "pause" while PLAYING: Just after interrupting the thread (i.e, after
    // the `runnable.run()` statement a few lines above), imagine that the processor switches
    // context and passes control to the background thread (remember that threads are
    // non-deterministic so this is quite possible). The background thread, now interrupted, runs
    // the `onPaused()` callback, which updates `this.stateOps`. Switch back to this thread. Now,
    // `this.stateOps` is different than it was at the beginning.
    //
    // If we commanded "seekTo" while PLAYING: Imagine we are seeking to the very beginning of a
    // song. The background thread is interrupted. Context switch. The background thread handles the
    // interruption, and runs the `onSought()` callback, which updates `stateOps` to a new
    // `PlayingStateOps`. Context switch, and return to this thread. In that case, if we hadn't
    // stored the original `this.stateOps` in an auxiliar variable, we would be waiting for the new
    // PlayingStateOps, which hasn't event been sent an interrupt signal! We should instead have
    // waited for the original stateOps.
    //
    // This is the reason we need an additional, auxiliary local `stateOps` variable.
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
    // All the player command calls that interrupt the background thread won't have any effect if
    // they interleave with these callbacks. It's impossible that any other callback happens before
    // this callback. This is because, when this method executes, even if the interrupt signal has
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
      stateOps = new StoppedStateOps(song, 0, Player.this, this);
      // This should be inside the synchronized block, even though it doesn't modify any shared
      // state. Otherwise, the following super rare case could happen: while executing the
      // `onFinished()` callback, the thread loses control and execution passes back to the main
      // thread. We then issue from the main thread a `pause()` command (this command is valid
      // because we already updated `stateOps` above). It's possible that the corresponding
      // `onPaused()` callback finish its execution first before the `onFinished()` callback, which
      // would be counter-intuitive. We ensure this doesn't happen by putting the call to the
      // user-facing `onFinished()` callback inside the synchronized block.
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
