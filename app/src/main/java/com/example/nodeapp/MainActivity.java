package com.example.nodeapp;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Window;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import com.jakewharton.threetenabp.AndroidThreeTen;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.Map;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.net.URL;
import java.net.HttpURLConnection;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private TextView outputView;
    private NodeClient nodeClient;
    private RecyclerView recyclerView;
    private FrameLayout overlay;
    private MatchAdapter matchAdapter;
    private List<Fixture> matchList = new ArrayList<>();
    private OkHttpClient client = new OkHttpClient();
    private static final int REQ_CODE_POST_NOTIFICATIONS = 1001;

    private static final String API_URL = "https://api.gogoaltv.com/api/fixtures?api_key=eTE6NzFhNjYyYmJlNzM5Nzk0YjM5Yjc1NjBiNGZiOTNjNDY6MjAyNS0wNy0zMVQxOToyNjoyNC44OTgzMTA=";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidThreeTen.init(this);
        setContentView(R.layout.activity_main);

        Intent serviceIntent = new Intent(this, NodeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.POST_NOTIFICATIONS },
                        REQ_CODE_POST_NOTIFICATIONS);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(Color.parseColor("#C62828")); // top bar
            window.setNavigationBarColor(Color.parseColor("#C62828")); // bottom nav bar (optional)
        }
        
        outputView = findViewById(R.id.outputView);
        outputView.setVisibility(View.GONE);

        // overlay controls
        overlay = findViewById(R.id.overlay);
        recyclerView = findViewById(R.id.recyclerViewMatches);

        TextView overlayText = findViewById(R.id.overlayTitle);
        TextView overlaySubtitle = findViewById(R.id.overlaySubtitle);

        overlay.setVisibility(View.VISIBLE);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                    @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.bottom = 16; // space in pixels between each item
            }
        });

        matchAdapter = new MatchAdapter(matchList, match -> {
            String url = "https://gogoaltv.com/stream/" + match.matchUrl + "?_id="+String.valueOf(match.id);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        });
        recyclerView.setAdapter(matchAdapter);

        requestBatteryOptimizationExemption();
        fetchMatches();
        checkForUpdate();

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchMatches(); // Your existing method to reload data
            swipeRefreshLayout.setRefreshing(false);
        });
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

    private void fetchMatches() {
        overlay.setVisibility(View.VISIBLE);

        Request request = new Request.Builder()
                .url(API_URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> overlay.setVisibility(View.GONE));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> overlay.setVisibility(View.GONE));
                    return;
                }

                String responseBody = response.body().string();
                try {
                    JSONArray jsonArray = new JSONArray(responseBody);
                    matchList.clear();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        Fixture match = Fixture.fromJson(obj);
                        matchList.add(match);
                    }
                    runOnUiThread(() -> {
                        matchAdapter.notifyDataSetChanged();
                        overlay.setVisibility(View.GONE);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> overlay.setVisibility(View.GONE));
                }
            }
        });
    }

    private void checkForUpdate() {
        String updateUrl = "https://api.gogoaltv.com/release/update"; // example

        new Thread(() -> {
            try {
                URL url = new URL(updateUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        json.append(line);
                    }
                    reader.close();

                    JSONObject obj = new JSONObject(json.toString());
                    String latestVersion = obj.getString("version");
                    String downloadLink = obj.getString("blobUrl");

                    String currentVersion = BuildConfig.VERSION_NAME;

                    if (isUpdateAvailable(currentVersion, latestVersion)) {
                        runOnUiThread(() -> showUpdateDialog(downloadLink));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showUpdateDialog(String downloadLink) {
        new AlertDialog.Builder(this)
                .setTitle("Update Available")
                .setMessage(
                        "A newer version of GoGoalTv is available. Please update to continue enjoying the latest features.")
                .setPositiveButton("Update", (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadLink));
                    startActivity(browserIntent);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private boolean isUpdateAvailable(String currentVersion, String latestVersion) {
        String[] currentParts = currentVersion.split("\\.");
        String[] latestParts = latestVersion.split("\\.");

        int length = Math.max(currentParts.length, latestParts.length);

        for (int i = 0; i < length; i++) {
            int current = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int latest = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;

            if (latest > current) {
                return true; // latest is newer
            } else if (latest < current) {
                return false; // current is already newer
            }
        }
        return false; // versions are the same
    }
}
