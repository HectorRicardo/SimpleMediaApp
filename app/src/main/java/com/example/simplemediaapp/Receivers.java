package com.example.simplemediaapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class Receivers {
  private Receivers() {}

  public static BroadcastReceiver create(Runnable onReceive) {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        onReceive.run();
      }
    };
  }
}
