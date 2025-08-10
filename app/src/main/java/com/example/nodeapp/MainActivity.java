package com.example.nodeapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.os.Build;
import android.os.PowerManager;
import android.net.Uri;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.Map;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private TextView outputView;
    private NodeClient nodeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        outputView = findViewById(R.id.outputView);
        outputView.setVisibility(View.GONE);

        // overlay controls
        View overlay = findViewById(R.id.overlay);
        TextView overlayText = findViewById(R.id.overlayTitle);
        TextView overlaySubtitle = findViewById(R.id.overlaySubtitle);

        overlay.setVisibility(View.VISIBLE);

        requestBatteryOptimizationExemption();

        Intent serviceIntent = new Intent(this, NodeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

    }

    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String logMessage = intent.getStringExtra("log_message");
            outputView.append(logMessage + "\n");
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver,
                new IntentFilter("NodeServiceLog"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver);
    }

    private void requestBatteryOptimizationExemption() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
}
