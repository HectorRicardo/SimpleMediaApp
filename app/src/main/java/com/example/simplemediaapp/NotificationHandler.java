package com.example.simplemediaapp;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_RATING;
import static android.support.v4.media.RatingCompat.RATING_THUMB_UP_DOWN;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC;
import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

public class NotificationHandler {

  public static final int NOTIFICATION_ID = 1; // has to be positive
  private static final String CHANNEL_ID = "default";

  private static final IntentFilter LIKE_ACTION_FILTER;
  private static final IntentFilter DISLIKE_ACTION_FILTER;
  private static final IntentFilter NEUTRAL_ACTION_FILTER;

  static {
    String className = NotificationHandler.class.getName();
    LIKE_ACTION_FILTER = new IntentFilter(className + ".LIKE_ACTION");
    DISLIKE_ACTION_FILTER = new IntentFilter(className + ".DISLIKE_ACTION");
    NEUTRAL_ACTION_FILTER = new IntentFilter(className + ".NEUTRAL_ACTION");
  }

  private final BroadcastReceiver likeActionReceiver =
      Receivers.create(() -> setRating(RatingCompat.newThumbRating(true)));
  private final BroadcastReceiver dislikeActionReceiver =
      Receivers.create(() -> setRating(RatingCompat.newThumbRating(false)));
  private final BroadcastReceiver neutralActionReceiver =
      Receivers.create(() -> setRating(RatingCompat.newUnratedRating(RATING_THUMB_UP_DOWN)));

  private final Context context;
  private final MediaSessionCompat mediaSession;
  private final int pendingIntentFlags;
  private final PendingIntent previousIntent;
  private final PendingIntent playIntent;
  private final PendingIntent pauseIntent;
  private final PendingIntent nextIntent;
  private final PendingIntent likeIntent;
  private final PendingIntent neutralIntent;
  private final PendingIntent dislikeIntent;
  private final NotificationCompat.Builder notificationBuilder;

  private Action previousAction;
  private Action pauseAction;
  private Action playAction;
  private Action nextAction;
  private Action thumbsDownAction;
  private Action thumbsUpAction;

  public NotificationHandler(Context context, MediaSessionCompat mediaSession) {
    this.context = context;
    this.mediaSession = mediaSession;
    pendingIntentFlags = VERSION.SDK_INT < VERSION_CODES.M ? 0 : PendingIntent.FLAG_IMMUTABLE;
    previousIntent = intent(ACTION_SKIP_TO_PREVIOUS);
    playIntent = intent(ACTION_PLAY);
    pauseIntent = intent(ACTION_PAUSE);
    nextIntent = intent(ACTION_SKIP_TO_NEXT);
    likeIntent = intent(LIKE_ACTION_FILTER);
    neutralIntent = intent(NEUTRAL_ACTION_FILTER);
    dislikeIntent = intent(DISLIKE_ACTION_FILTER);

    createNotificationChannel(context);
    defineActions();

    mediaSession.setSessionActivity(createLaunchIntent());

    notificationBuilder =
        new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(getMetadata().getDescription().getIconBitmap())
            .setContentIntent(getMediaController().getSessionActivity())
            .setDeleteIntent(intent(ACTION_STOP))
            .setVisibility(VISIBILITY_PUBLIC)
            .setColor(Color.BLACK) // you need this because of bug
            .setStyle(
                new MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(1, 2, 3));
  }

  // We make this static because we don't want to instantiate this class when this methods is called
  // from a manifest-registered receiver
  public static void createNotificationChannel(Context context) {
    NotificationManagerCompat.from(context)
        .createNotificationChannel(
            new NotificationChannelCompat.Builder(CHANNEL_ID, IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_name))
                .build());
  }

  public void updateSong() {
    MediaDescriptionCompat mediaDescription = getMetadata().getDescription();
    notificationBuilder
        .setContentTitle(mediaDescription.getTitle())
        .setContentText(mediaDescription.getSubtitle())
        .setSubText(mediaDescription.getDescription());
  }

  public void onLocaleChanged() {
    updateSong();
    defineActions();
    updateNotification();
  }

  public void setRatingAndUpdate() {
    defineRatingActions();
    updateNotification();
  }

  public Notification createNotification() {
    return notificationBuilder
        .clearActions()
        .addAction(thumbsDownAction)
        .addAction(previousAction)
        .addAction(
            getMediaController().getPlaybackState().getState() == STATE_PLAYING
                ? pauseAction
                : playAction)
        .addAction(nextAction)
        .addAction(thumbsUpAction)
        .build();
  }

  public void updateNotification() {
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, createNotification());
  }

  // Called when initializing or when locale changes
  private void defineActions() {
    previousAction =
        new Action(R.drawable.exo_icon_previous, str(R.string.previous), previousIntent);
    playAction = new Action(R.drawable.exo_icon_play, str(R.string.play), playIntent);
    pauseAction = new Action(R.drawable.exo_icon_pause, str(R.string.pause), pauseIntent);
    nextAction = new Action(R.drawable.exo_icon_next, str(R.string.next), nextIntent);
    defineRatingActions();
  }

  // Called when locale or rating changes
  private void defineRatingActions() {
    RatingCompat rating = getMetadata().getRating(METADATA_KEY_RATING);
    boolean isNeutral = !rating.isRated();
    thumbsDownAction =
        isNeutral || rating.isThumbUp()
            ? new Action(R.drawable.thumbs_down_off, str(R.string.like), dislikeIntent)
            : new Action(R.drawable.thumbs_down_on, str(R.string.neutral), neutralIntent);
    thumbsUpAction =
        isNeutral || !rating.isThumbUp()
            ? new Action(R.drawable.thumbs_up_off, str(R.string.dislike), likeIntent)
            : new Action(R.drawable.thumbs_up_on, str(R.string.neutral), neutralIntent);
  }

  public void registerReceivers() {
    context.registerReceiver(likeActionReceiver, LIKE_ACTION_FILTER);
    context.registerReceiver(dislikeActionReceiver, DISLIKE_ACTION_FILTER);
    context.registerReceiver(neutralActionReceiver, NEUTRAL_ACTION_FILTER);
  }

  public void unregisterReceivers() {
    context.unregisterReceiver(likeActionReceiver);
    context.unregisterReceiver(dislikeActionReceiver);
    context.unregisterReceiver(neutralActionReceiver);
  }

  // Helper methods

  private PendingIntent intent(IntentFilter intentFilter) {
    return PendingIntent.getBroadcast(
        context,
        0,
        new Intent(intentFilter.getAction(0)).setPackage(context.getPackageName()),
        pendingIntentFlags);
  }

  private PendingIntent intent(long playbackState) {
    return MediaButtonReceiver.buildMediaButtonPendingIntent(context, playbackState);
  }

  private PendingIntent createLaunchIntent() {
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());

    // not really sure why we need this. See the following links:
    // https://github.com/HectorRicardo/getLaunchIntent-bug
    // https://github.com/android/uamp/pull/464
    // (comments)
    // https://stackoverflow.com/questions/69519433/android-how-to-show-skip-the-splash-screen-as-needed-when-opening-an-app-throug
    intent.setPackage(null);

    return PendingIntent.getActivity(context, 0, intent, pendingIntentFlags);
  }

  private MediaControllerCompat getMediaController() {
    return mediaSession.getController();
  }

  private MediaMetadataCompat getMetadata() {
    return getMediaController().getMetadata();
  }

  private void setRating(RatingCompat rating) {
    getMediaController().getTransportControls().setRating(rating);
  }

  private String str(int resId) {
    return context.getString(resId);
  }
}
