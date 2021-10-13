package com.hectorricardo.player;

import com.hectorricardo.player.Player.PlayerListenerInternal;
import com.hectorricardo.player.Player.StateOps;

class StoppedStateOps implements StateOps {

  private final PlayerListenerInternal playerListener;
  private final Song song;
  private final long progress;

  StoppedStateOps(Song song, long progress, PlayerListenerInternal playerListener) {
    this.song = song;
    this.progress = progress;
    this.playerListener = playerListener;
  }

  @Override
  public PlayingStateOps play() {
    return new PlayingStateOps(song, progress, playerListener);
  }

  @Override
  public PlayingStateOps play(Song song) {
    return new PlayingStateOps(song, progress, playerListener);
  }

  @Override
  public void stop() {
    throw new IllegalStateException("Player already stopped!");
  }

  @Override
  public long getProgress() {
    return progress;
  }

  @Override
  public void changeSong(Song song) {
    playerListener.onSongSet(new StoppedStateOps(song, progress, playerListener));
  }

  @Override
  public void setProgress(long progress) {
    if (song == null) {
      throw new IllegalStateException("No song set");
    }
    if (song.duration < progress) {
      throw new IllegalStateException("Progress can't be set for more than song's duration");
    }
    playerListener.onSought(new StoppedStateOps(song, progress, playerListener), progress);
  }
}
