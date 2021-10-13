package com.example.simplemediaapp;

import static android.content.Intent.ACTION_LOCALE_CHANGED;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_RATING;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static android.support.v4.media.RatingCompat.RATING_THUMB_UP_DOWN;
import static android.support.v4.media.RatingCompat.newUnratedRating;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static com.example.simplemediaapp.NotificationHandler.NOTIFICATION_ID;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;
import com.hectorricardo.player.Player;
import com.hectorricardo.player.Player.PlayerListener;
import com.hectorricardo.player.Song;
import java.util.List;

public class MediaService extends MediaBrowserServiceCompat {

  private final Song song = new Song("My Hit", "Hector Ricardo", 30000);

  private static final IntentFilter localeChangedFilter = new IntentFilter(ACTION_LOCALE_CHANGED);

  private final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
  private final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

  private MediaSessionCompat mediaSession;
  private NotificationHandler notificationsHandler;

  private final BroadcastReceiver localeChangedReceiver =
      Receivers.create(
          () -> {
            mediaSession.setMetadata(
                metadataBuilder
                    .putString(METADATA_KEY_TITLE, "My hit song " + Math.random())
                    .build());
            notificationsHandler.onLocaleChanged();
          });

  private final Player player =
      new Player(
          new PlayerListener() {
            @Override
            public void onPaused() {
              mediaSession.setPlaybackState(
                  stateBuilder
                      .setState(STATE_PAUSED, player.getProgress(), 0)
                      .setActions(
                          ACTION_PLAY
                              | ACTION_PLAY_PAUSE
                              | ACTION_STOP
                              | ACTION_SKIP_TO_NEXT
                              | ACTION_SKIP_TO_PREVIOUS
                              | ACTION_SEEK_TO)
                      .build());
              stopForeground(false);
              notificationsHandler.updateNotification();
            }

            @Override
            public void onFinished() {
              mediaSession.setPlaybackState(
                  stateBuilder
                      .setState(STATE_STOPPED, 0, 1)
                      .setActions(
                          ACTION_PLAY
                              | ACTION_PLAY_PAUSE
                              | ACTION_SKIP_TO_NEXT
                              | ACTION_SKIP_TO_PREVIOUS
                              | ACTION_SEEK_TO)
                      .build());
              notificationsHandler.updateNotification();
            }

            @Override
            public void onSought(boolean playing, long progress) {
              mediaSession.setPlaybackState(
                  stateBuilder
                      .setState(playing ? STATE_PLAYING : STATE_PAUSED, progress, 1)
                      .build());
              notificationsHandler.updateNotification();
            }
          });

  private boolean started = false;
  private final MediaSessionCompat.Callback mediaSessionCallback =
      new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
          Log.d("MyMediumService", "ONPLAY");
          if (!started) {
            ContextCompat.startForegroundService(
                MediaService.this, new Intent(MediaService.this, MediaService.class));
          }

          mediaSession.setPlaybackState(
              stateBuilder
                  .setState(STATE_PLAYING, player.getProgress(), 1)
                  .setActions(
                      ACTION_PAUSE
                          | ACTION_PLAY_PAUSE
                          | ACTION_STOP
                          | ACTION_SKIP_TO_NEXT
                          | ACTION_SKIP_TO_PREVIOUS
                          | ACTION_SEEK_TO)
                  .build());
          mediaSession.setActive(true);

          player.play(song);

          startForeground(NOTIFICATION_ID, notificationsHandler.createNotification());
          if (!started) {
            notificationsHandler.registerReceivers();
            registerReceiver(localeChangedReceiver, localeChangedFilter);
          }

          started = true;
        }

        @Override
        public void onPause() {
          player.pause();
        }

        @Override
        public void onStop() {
          super.onStop();
          Log.d("HR-SMA", "ONSTOP");
        }

        @Override
        public void onSkipToNext() {
          android.util.Log.d("SMA_MSC", "onSkipToNext");
        }

        @Override
        public void onSkipToPrevious() {
          android.util.Log.d("SMA_MSC", "onSkipToPrevious");
        }

        @Override
        public void onSeekTo(long pos) {
          // Android bug
          if (mediaSession.getController().getPlaybackState().getState() == STATE_PLAYING) {
            mediaSession.setPlaybackState(stateBuilder.setState(STATE_BUFFERING, pos, 1).build());
          }

          player.setProgress(pos);
        }

        @Override
        public void onSetRating(RatingCompat rating) {
          mediaSession.setMetadata(metadataBuilder.putRating(METADATA_KEY_RATING, rating).build());
          notificationsHandler.setRatingAndUpdate();
        }
      };

  @Override
  public void onCreate() {
    super.onCreate();

    mediaSession = new MediaSessionCompat(this, "MyAppService");

    mediaSession.setPlaybackState(stateBuilder.setActions(ACTION_PLAY | ACTION_PLAY_PAUSE).build());
    mediaSession.setMetadata(
        metadataBuilder
            .putString(METADATA_KEY_TITLE, song.title)
            .putString(METADATA_KEY_ARTIST, song.artist)
            .putLong(METADATA_KEY_DURATION, song.duration)
            .putRating(METADATA_KEY_RATING, newUnratedRating(RATING_THUMB_UP_DOWN))
            .putBitmap(
                METADATA_KEY_ART,
                BitmapFactory.decodeResource(getResources(), R.drawable.ic_notification))
            .build());
    mediaSession.setCallback(mediaSessionCallback);

    setSessionToken(mediaSession.getSessionToken());

    notificationsHandler = new NotificationHandler(this, mediaSession);
    notificationsHandler.setSong();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    notificationsHandler.unregisterReceivers();
  }

  @Nullable
  @Override
  public BrowserRoot onGetRoot(
      @NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
    return new BrowserRoot("", null);
  }

  @Override
  public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaItem>> result) {
    result.sendResult(null);
  }
}
