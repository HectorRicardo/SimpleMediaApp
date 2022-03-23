package com.hectorricardo.player;

import static com.example.simplemediaapp.Songs.defaultSong;

import com.hectorricardo.player.Player.PlayerListenerInternal;
import com.hectorricardo.player.Player.StateOps;

class StoppedStateOps implements StateOps {

  private final long progress;
  private final Object lock;
  private final PlayerListenerInternal playerListener;

  StoppedStateOps(long progress, Object lock, PlayerListenerInternal playerListener) {
    this.progress = progress;
    this.lock = lock;
    this.playerListener = playerListener;
  }

  @Override
  public PlayingStateOps play() {
    return new PlayingStateOps(progress, false, lock, playerListener);
  }

  @Override
  public void pause() {
    throw new IllegalStateException("Player already stopped!");
  }

  @Override
  public long getProgress() {
    return progress;
  }

  @Override
  public void seekTo(long progress) {
    if (defaultSong.duration < progress) {
      throw new IllegalStateException("Progress can't be set for more than song's duration");
    }
    playerListener.onSought(new StoppedStateOps(progress, lock, playerListener), progress);
  }

  @Override
  public boolean isPlaying() {
    return false;
  }

  @Override
  public void waitToFinish() {

  }
}
