package com.hectorricardo.player;

import com.hectorricardo.player.Player.PlayerListenerInternal;
import com.hectorricardo.player.Player.StateOps;

class PlayingStateOps implements StateOps {

  private static final int STOPPED = 0;
  private static final int PROGRESS_SET = 1;
  private static final int SONG_SET = 2;

  private final Thread thread;
  private long startedOn;

  private Song newSong;
  private long newProgress;
  private volatile int interruptReason;

  PlayingStateOps(Song song, long progress, PlayerListenerInternal playerListener) {
    thread =
        new Thread(
            () -> {
              System.out.println("Playing " + song.title + " from " + progress);
              startedOn = System.currentTimeMillis();
              try {
                Thread.sleep(song.duration - progress);
                playerListener.onFinished();

              } catch (InterruptedException ignored) {
                if (interruptReason == PROGRESS_SET) {
                  System.out.println("Progress changing");
                  playerListener.onSought(
                      new PlayingStateOps(song, newProgress, playerListener), newProgress);
                } else if (interruptReason == SONG_SET) {
                  System.out.println("Song changing");
                  playerListener.onSongSet(new PlayingStateOps(newSong, 0, playerListener));
                } else {
                  long newProgress = System.currentTimeMillis() - startedOn + progress;
                  System.out.println("Paused on " + newProgress);
                  StoppedStateOps controls = new StoppedStateOps(song, newProgress, playerListener);
                  playerListener.onPaused(controls);
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
    throw new UnsupportedOperationException("Simplistic example. Can't get progress while playing");
  }

  @Override
  public void setProgress(long millis) {
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
}
