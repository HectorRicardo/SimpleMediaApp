package com.hectorricardo.player;

import static com.example.simplemediaapp.Songs.defaultSong;

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

  private final PlayerListener playerListener;
  private Thread thread;
  private long lastProgress = 0;

  private long startedOn;
  private Interruption interruption;

  public Player(PlayerListener playerListener) {
    this.playerListener = playerListener;
  }

  public void play() {
    if (thread != null) {
      throw new RuntimeException("Player already playing!");
    }
    thread =
        new Thread(
            () -> {
              System.out.println("Playing " + defaultSong.id + " from " + lastProgress);
              playerListener.onPlaybackStarted(lastProgress);
              startedOn = System.currentTimeMillis();
              try {
                Thread.sleep(defaultSong.duration - lastProgress);
                synchronized (this) {
                  if (interruption == null) {
                    thread = null;
                    lastProgress = 0;
                    playerListener.onFinished();
                  } else {
                    interruption.consumeAndClear();
                  }
                }
              } catch (InterruptedException ignored) {
                interruption.consumeAndClear();
              }
            });
    thread.start();
  }

  public void pause() {
    Thread thread;
    synchronized (this) {
      if (this.thread == null) {
        // To avoid crashing. We need to do this a no-op because otherwise, the state of the player
        // could become PAUSED while we're waiting for the `this` lock to be received. If we take
        // the approach of throwing an exception when pausing an already-PAUSED player, then we
        // would also be throwing an exception on this legitimate situation.
        return;
      }
      interruption = new PauseInterruption();
      thread = this.thread;
    }
    try {
      thread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    this.thread = null;
  }

  public interface PlayerListener {

    void onPlaybackStarted(long progress);

    void onPaused(long progress);

    void onFinished();
  }

  private abstract class Interruption {
    Interruption() {
      thread.interrupt();
    }

    void consumeAndClear() {
      consume();
      interruption = null;
    }

    abstract void consume();
  }

  private class PauseInterruption extends Interruption {
    @Override
    void consume() {
      lastProgress =
          Math.min(System.currentTimeMillis() - startedOn + lastProgress, defaultSong.duration);
      System.out.println("Paused on " + lastProgress);
      playerListener.onPaused(lastProgress);
    }
  }
}
