package com.hectorricardo.player;

import com.hectorricardo.player.Player.PlayerListenerInternal;
import com.hectorricardo.player.Player.StateOps;

class PlayingStateOps implements StateOps {

  private static final int STOPPED = 0;
  private static final int PROGRESS_SET = 1;
  private static final int SONG_SET = 2;

  private final Thread thread;
  private final long startingProgress;

  // late initialization
  // volatile because we want it to be visible in case the getProgress() method is called
  private volatile Long startedOn;

  // Interruption reason parameters
  private Song newSong;
  private long newProgress;
  private volatile int interruptReason;

  PlayingStateOps(
      Song song,
      long startingProgress,
      boolean sought,
      Object lock,
      PlayerListenerInternal playerListener) {
    this.startingProgress = startingProgress;
    thread =
        new Thread(
            () -> {
              System.out.println("Playing " + song.id + " from " + startingProgress);
              playerListener.onThreadStarted(startingProgress, sought);
              startedOn = System.currentTimeMillis();
              try {
                Thread.sleep(song.duration - startingProgress);
                synchronized (lock) {
                  playerListener.onFinished();
                }

              } catch (InterruptedException ignored) {
                Runnable runnable = null;
                synchronized (lock) {
                  if (interruptReason == PROGRESS_SET) {
                    System.out.println("Progress changing");
                    playerListener.onSought(
                        new PlayingStateOps(song, newProgress, true, lock, playerListener), newProgress);
                  } else if (interruptReason == SONG_SET) {
                    System.out.println("Song changing");
                    playerListener.onSongSet(new PlayingStateOps(newSong, 0, false, lock, playerListener));
                  } else {
                    long newProgress = getProgress();
                    System.out.println("Paused on " + newProgress);
                    runnable = playerListener.onPaused(
                        new StoppedStateOps(song, newProgress, lock, playerListener));
                  }
                  if (runnable != null) {
                    runnable.run();
                  }
                }
              }
            });
    thread.start();
  }

  @Override
  public PlayingStateOps play() {
    throw new IllegalStateException("Player is already playing!");
  }

  @Override
  public PlayingStateOps play(Song song) {
    throw new IllegalStateException("Player is already playing!");
  }

  @Override
  public void stop() {
    interruptReason = STOPPED;
    thread.interrupt();
  }

  @Override
  public long getProgress() {
    return startedOn == null
        ? startingProgress
        : System.currentTimeMillis() - startedOn + startingProgress;
  }

  @Override
  public void seekTo(long millis) {
    newProgress = millis;
    interruptReason = PROGRESS_SET;
    thread.interrupt();
  }

  @Override
  public void changeSong(Song song) {
    newSong = song;
    interruptReason = SONG_SET;
    thread.interrupt();
  }

  @Override
  public boolean isPlaying() {
    return true;
  }

  @Override
  public void waitToFinish() {
    try {
      thread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
