package com.example.simplemediaapp;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaControllerCompat.TransportControls;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

  private Button button;
  private final MediaControllerCompat.Callback mediaControllerCallback =
      new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat unused) {
          updateButton();
        }
      };
  private final MediaBrowserCompat.ConnectionCallback mediaBrowserConnectionCallback =
      new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
          updateButton();
          button.setClickable(true);
        }

        @Override
        public void onConnectionFailed() {
          button.setText(R.string.connection_failed);
        }

        @Override
        public void onConnectionSuspended() {
          button.setClickable(false);
          button.setOnClickListener(null);
          button.setText(R.string.connection_suspended);
        }
      };
  private final MediaClient mediaClient =
      new MediaClient(this, mediaBrowserConnectionCallback, mediaControllerCallback);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    button = findViewById(R.id.playPauseButton);

    mediaClient.onCreate();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mediaClient.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mediaClient.onResume();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mediaClient.onStop();
  }

  private void updateButton() {
    MediaControllerCompat mediaController = mediaClient.getMediaController();
    TransportControls transportControls = mediaController.getTransportControls();

    int playbackState = mediaController.getPlaybackState().getState();

    if (playbackState == STATE_PLAYING) {
      button.setText(R.string.pause);
      button.setOnClickListener(v -> transportControls.pause());
    } else {
      button.setText(R.string.play);
      button.setOnClickListener(v -> transportControls.play());
    }
  }
}
