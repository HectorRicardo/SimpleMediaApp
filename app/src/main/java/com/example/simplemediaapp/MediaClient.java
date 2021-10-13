package com.example.simplemediaapp;

import android.content.ComponentName;
import android.media.AudioManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import androidx.appcompat.app.AppCompatActivity;

public class MediaClient {

  private final AppCompatActivity activity;
  private final MediaBrowserCompat.ConnectionCallback mediaBrowserConnectionCallback;
  private final MediaControllerCompat.Callback mediaControllerCallback;

  private MediaBrowserCompat mediaBrowser;

  public MediaClient(
      AppCompatActivity activity,
      MediaBrowserCompat.ConnectionCallback mediaBrowserConnectionCallback,
      MediaControllerCompat.Callback mediaControllerCallback) {
    this.activity = activity;
    this.mediaBrowserConnectionCallback = mediaBrowserConnectionCallback;
    this.mediaControllerCallback = mediaControllerCallback;
  }

  public void onCreate() {
    mediaBrowser =
        new MediaBrowserCompat(
            activity,
            new ComponentName(activity, MediaService.class),
            new MediaBrowserCompat.ConnectionCallback() {
              @Override
              public void onConnected() {
                MediaControllerCompat mediaController =
                    new MediaControllerCompat(activity, mediaBrowser.getSessionToken());
                mediaController.registerCallback(mediaControllerCallback);
                MediaControllerCompat.setMediaController(activity, mediaController);
                mediaBrowserConnectionCallback.onConnected();
              }

              @Override
              public void onConnectionFailed() {
                mediaBrowserConnectionCallback.onConnectionFailed();
              }

              @Override
              public void onConnectionSuspended() {
                mediaBrowserConnectionCallback.onConnectionSuspended();
              }
            },
            null);
  }

  public void onStart() {
    mediaBrowser.connect();
  }

  public void onResume() {
    activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
  }

  public void onStop() {
    MediaControllerCompat mediaController = getMediaController();
    if (mediaController != null) {
      mediaController.unregisterCallback(mediaControllerCallback);
    }
    mediaBrowser.disconnect();
  }

  public MediaControllerCompat getMediaController() {
    return MediaControllerCompat.getMediaController(activity);
  }
}
