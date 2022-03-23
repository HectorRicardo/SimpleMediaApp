package com.example.simplemediaapp;

import com.hectorricardo.player.Song;
import java.util.HashMap;
import java.util.Map;

public class Songs {

  public static final Song[] songs =
      new Song[] {
        new Song("furElise", "Für Elise", "Ludwig van Beethoven", 30000),
        new Song("myWay", "My Way", "Frank Sinatra", 30000),
        new Song("mariageDamour", "Mariage d'Amour", "Paul de Senneville", 40000),
      };

  public static final Song defaultSong = songs[0];

  private static final Map<String, Integer> songsMap = new HashMap<>(songs.length);

  static {
    for (int i = 0; i < songs.length; i++) {
      songsMap.put(songs[i].id, i);
    }
  }

  public static int getSongIdx(String id) {
    Integer index = songsMap.get(id);
    assert index != null;
    return index;
  }
}
