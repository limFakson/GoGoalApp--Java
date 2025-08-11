package com.example.nodeapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.os.PowerManager;
import android.net.Uri;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.net.wifi.WifiManager;
import android.content.Context;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.SystemClock;

public class NodeService extends Service {

    private static final String TAG = "NodeService";
    private static final String CHANNEL_ID = "NodeServiceChannel";
    private NodeClient nodeClient;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        sendLogToActivity("Service created");

        String gateway = "ws://proxy.gogoaltv.com:8010/ws";
        // Start NodeClient here
        nodeClient = new NodeClient(gateway, 15, 8, 30, log->sendLogToActivity(log));
        
        // Create Notification Channel for Foreground Service
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GoGoal Service")
                .setContentText("GoGoalClient is running")
                .setSmallIcon(R.drawable.ic_notification)
                .build();

        startForeground(1, notification);

        if (nodeClient != null) {
            nodeClient.start();
        }

        return START_STICKY; // Restart if killed
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroyed");
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (nodeClient != null) {
            nodeClient.stop();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't bind, only start
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        scheduleRestart(this);
        super.onTaskRemoved(rootIntent);
    }

    private void scheduleRestart(Context context) {
        Intent restartServiceIntent = new Intent(context, NodeService.class);
        PendingIntent restartServicePendingIntent = PendingIntent.getService(context, 1, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 5000,
                restartServicePendingIntent);
    }

    private void sendLogToActivity(String log) {
        Intent intent = new Intent("NodeServiceLog");
        intent.putExtra("log_message", log);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Node Background Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void acquireWakeLock() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiManager.WifiLock wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "NodeApp::WifiLock");
            wifiLock.acquire();

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                if (wakeLock == null || !wakeLock.isHeld()) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NodeApp::WakelockTag");
                    wakeLock.acquire(10 * 60 * 1000L /* 10 minutes */); // Timeout to avoid leaks
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
