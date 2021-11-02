package com.hectorricardo.player;

public class Song {
  public final String id;
  public final String title;
  public final String artist;
  public final long duration;

  public Song(String id, String title, String artist, long duration) {
    this.id = id;
    this.title = title;
    this.artist = artist;
    this.duration = duration;
  }
}
