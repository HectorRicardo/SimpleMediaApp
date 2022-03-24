package com.hectorricardo.player;

public class Player {

  private final PlayerListener playerListener;

  public Player(PlayerListener playerListener) {
    this.playerListener = playerListener;
  }

  public interface PlayerListener {

    void onPlaybackStarted(long progress);

    void onPaused(long progress);

    void onFinished();
  }
}
