package com.example.simplemediaapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LocaleChangedReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (!Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
      return;
    }
    NotificationHandler.createNotificationChannel(context);
  }
}
