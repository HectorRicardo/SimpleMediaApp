package com.hectorricardo.player;

public class Song {
  public final String title;
  public final String artist;
  public final long duration;

  public Song(String title, String artist, long duration) {
    this.title = title;
    this.artist = artist;
    this.duration = duration;
  }
}
