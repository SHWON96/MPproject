/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.alarmclock;
/**
 * Subject: Alarm application
 * Update: 2019-11-17 JK setLatestEventInfo() is renewed
 * Update: 2019-11-18 JK mClock is removed
 */
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import java.util.Calendar;

import org.tensorflow.lite.examples.detection.DetectorActivity;

/**
 * Alarm Clock alarm alert: pops visible indicator and plays alarm
 * tone. This activity is the full screen version which shows over the lock
 * screen with the wallpaper as the background.
 */
public class AlarmAlertFullScreen extends Activity {

    // These defaults must match the values in res/xml/settings.xml
    private static final String DEFAULT_SNOOZE = "10";
    private static final String DEFAULT_VOLUME_BEHAVIOR = "2";
    protected static final String SCREEN_OFF = "screen_off";

    protected Alarm mAlarm;
    private int mVolumeBehavior;

    // Receives the ALARM_KILLED action from the AlarmKlaxon.
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Alarm alarm = intent.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);
            if (alarm != null && mAlarm.id == alarm.id) {
                dismiss(true);
            }
        }
    };

    private InterstitialAd mInterstitialAd;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Set ads
        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {}
        });
        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-3940256099942544/1033173712");
        // For the build and testing, test ad should be set.
        // For the production
        // ca-app-pub-8933690005967310/9685842587
        mInterstitialAd.loadAd(new AdRequest.Builder().build());

        mAlarm = getIntent().getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);

        // Get the volume/camera button behavior setting
        final String vol =
                PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SettingsActivity.KEY_VOLUME_BEHAVIOR,
                        DEFAULT_VOLUME_BEHAVIOR);
        mVolumeBehavior = Integer.parseInt(vol);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // Turn on the screen unless we are being launched from the AlarmAlert subclass.
        if (!getIntent().getBooleanExtra(SCREEN_OFF, false)) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        updateLayout();

        // Register to get the alarm killed intent.
        registerReceiver(mReceiver, new IntentFilter(Alarms.ALARM_KILLED));
    }

    private void setTitle() {
        String label = mAlarm.getLabelOrDefault(this);
        TextView title = (TextView) findViewById(R.id.alertTitle);
        title.setText(label);
    }

    private void updateLayout() {
        LayoutInflater inflater = LayoutInflater.from(this);

        setContentView(inflater.inflate(R.layout.alarm_alert, null));

        // set clock
        int face = 0;
        ViewGroup clockView = (ViewGroup) findViewById(R.id.clockView);
        inflater.inflate(AlarmClock.CLOCKS[face], clockView);
        View clockLayout = findViewById(R.id.clock);
        if (clockLayout instanceof DigitalClock) {
            ((DigitalClock) clockLayout).setAnimate();
        }

        // snooze behavior: pop a snooze confirmation view, kick alarm manager.
        Button snooze = (Button) findViewById(R.id.snooze);
        snooze.requestFocus();
        snooze.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                if (mInterstitialAd.isLoaded()) {
                    mInterstitialAd.show();
                }
                snooze();
            }
        });

        // dismiss button: close notification
        findViewById(R.id.dismiss).setOnClickListener(
                new Button.OnClickListener() {
                    public void onClick(View v) {
                        Intent intent = new Intent(getApplicationContext(), DetectorActivity.class);
                        intent.putExtra(Alarms.ALARM_ID, mAlarm.id);
                        startActivity(intent);
                    }
                });

        // Set the title from the passed in alarm
        setTitle();
    }

    // Attempt to snooze this alert.
    private void snooze() {
        final String snooze =
                PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SettingsActivity.KEY_ALARM_SNOOZE, DEFAULT_SNOOZE);
        int snoozeMinutes = Integer.parseInt(snooze);

        final long snoozeTime = System.currentTimeMillis()
                + (1000 * 60 * snoozeMinutes);
        Alarms.saveSnoozeAlert(AlarmAlertFullScreen.this, mAlarm.id,
                snoozeTime);

        // Get the display time for the snooze and update the notification.
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(snoozeTime);

        // Append (snoozed) to the label.
        String label = mAlarm.getLabelOrDefault(this);
        label = getString(R.string.alarm_notify_snooze_label, label);

        // Notify the user that the alarm has been snoozed.
        Intent cancelSnooze = new Intent(this, AlarmReceiver.class);
        cancelSnooze.setAction(Alarms.CANCEL_SNOOZE);
        cancelSnooze.putExtra(Alarms.ALARM_ID, mAlarm.id);
        PendingIntent broadcast =
                PendingIntent.getBroadcast(this, mAlarm.id, cancelSnooze, 0);
        NotificationManager nm = getNotificationManager();

        Notification n = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setTicker(label)
                .setWhen(0)
                .setContentTitle(label)
                .setContentText(getString(R.string.alarm_notify_snooze_text, Alarms.formatTime(this, c)))
                .setContentIntent(broadcast)
                .build();
        n.flags |= Notification.FLAG_AUTO_CANCEL
                | Notification.FLAG_ONGOING_EVENT;
        nm.notify(mAlarm.id, n);

        String displayTime = getString(R.string.alarm_alert_snooze_set,
                snoozeMinutes);
        // Intentionally log the snooze time for debugging.
        Log.v(displayTime);

        // Display the snooze minutes in a toast.
        Toast.makeText(AlarmAlertFullScreen.this, displayTime,
                Toast.LENGTH_LONG).show();

        Intent snoozeAlarm = new Intent(Alarms.ALARM_ALERT_ACTION);
        snoozeAlarm.setPackage("com.android.alarmclock");
        stopService(snoozeAlarm);
        finish();
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    // Dismiss the alarm.
    private void dismiss(boolean killed) {
        // The service told us that the alarm has been killed, do not modify
        // the notification or stop the service.
        if (!killed) {
            // Cancel the notification and stop playing the alarm
            NotificationManager nm = getNotificationManager();
            nm.cancel(mAlarm.id);
            Intent stopAlarm = new Intent(Alarms.ALARM_ALERT_ACTION);
            stopAlarm.setPackage("com.android.alarmclock");
            stopService(stopAlarm);
        }
        finish();
    }

    // This is called when a second alarm is triggered while previous alert window is still active.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (Log.LOGV) Log.v("AlarmAlert.OnNewIntent()");

        mAlarm = intent.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);

        setTitle();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) {
            // Don't hang around.
            finish();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (Log.LOGV) Log.v("AlarmAlert.onDestroy()");
        // No longer care about the alarm being killed.
        unregisterReceiver(mReceiver);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Do this on key down to handle a few of the system keys.
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        switch (event.getKeyCode()) {
            // Volume keys and camera keys dismiss the alarm
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
                if (up) {
                    switch (mVolumeBehavior) {
                        case 1:
                            snooze();
                            break;

                        case 2:
                            dismiss(false);
                            break;

                        default:
                            break;
                    }
                }
                return true;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Don't allow back to dismiss. This method is overriden by AlarmAlert so that the dialog is dismissed.
        return;
    }
}
