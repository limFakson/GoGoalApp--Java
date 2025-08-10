package com.example.nodeapp;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import okhttp3.WebSocketListener;

public class NodeClient {
    private static final String TAG = "NodeClient";

    private final String GATEWAY_URL;
    private final String NODE_ID = UUID.randomUUID().toString();
    private final int MAX_RETRIES;
    private final int RETRY_DELAY_SECONDS;
    private final int PING_INTERVAL_SECONDS;

    private final OkHttpClient httpClient;
    private final OkHttpClient wsClient;
    private final Gson gson = new Gson();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService ioPool = Executors.newCachedThreadPool();

    // tunnelId -> Socket (we keep the socket so we can write to it)
    private final ConcurrentHashMap<String, Socket> activeTunnels = new ConcurrentHashMap<>();
    // tunnelId -> Future (reader task) for cancellation
    private final ConcurrentHashMap<String, Future<?>> tunnelReaders = new ConcurrentHashMap<>();

    private volatile WebSocket webSocket;
    private volatile boolean stopped = false;

    private static final long PING_INTERVAL_MS = 30000; // 30 seconds (adjust to your server timeout)
    private ScheduledExecutorService pingScheduler;

    private final NodeClientCallback callback;

    public interface NodeClientCallback {
        void onLog(String text);
    }

    public NodeClient(String gatewayUrl, int maxRetries, int retryDelaySeconds, int pingIntervalSeconds,
            NodeClientCallback callback) {
        this.GATEWAY_URL = gatewayUrl;
        this.MAX_RETRIES = maxRetries;
        this.RETRY_DELAY_SECONDS = retryDelaySeconds;
        this.PING_INTERVAL_SECONDS = pingIntervalSeconds;
        this.callback = callback;

        // One OkHttpClient for http calls and websockets (can share)
        this.httpClient = new OkHttpClient.Builder().build();
        this.wsClient = this.httpClient;
    }

    public void start() {
        stopped = false;
        scheduler.execute(this::connectWithRetries);
        startPingLoop();
    }

    public void stop() {
        stopped = true;

        // Close websocket
        if (webSocket != null) {
            webSocket.close(1000, "client-stopping");
            webSocket = null;
        }

        // cancel pingers
        scheduler.shutdownNow();

        // cancel ping loop
        stopPingLoop();

        // cancel reader tasks
        tunnelReaders.forEach((tid, future) -> future.cancel(true));
        tunnelReaders.clear();

        // close sockets
        activeTunnels.forEach((tid, socket) -> {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        });
        activeTunnels.clear();

        try {
            ioPool.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    private void log(String s) {
        Log.i(TAG, s);
        if (callback != null)
            callback.onLog(s);
    }

    private void connectWithRetries() {
        int retries = 0;
        while (!stopped && retries < MAX_RETRIES) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                log("Attempting websocket connect (attempt " + (retries + 1) + ")");
                Request request = new Request.Builder().url(GATEWAY_URL).build();
                webSocket = wsClient.newWebSocket(request, new WebSocketListenerImpl(latch));
                // Wait for onOpen or short timeout
                boolean opened = latch.await(15, TimeUnit.SECONDS); // wait up to 15s
                if (opened && webSocket != null) {
                    // Successfully connected; block here until connection closes (listener will
                    // handle reconnect)
                    log("Connected to " + GATEWAY_URL + " as " + NODE_ID);

                    // Wait until this webSocket reference becomes null (onClose sets it null) or
                    // stopped
                    while (!stopped && webSocket != null) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                    if (stopped)
                        break;
                } else {
                    log("WebSocket open timed out.");
                    if (webSocket != null) {
                        webSocket.cancel();
                        webSocket = null;
                    }
                }
            } catch (Exception e) {
                log("WebSocket connect error: " + e.toString());
            }

            retries++;
            if (!stopped && retries < MAX_RETRIES) {
                log("Reconnecting in " + RETRY_DELAY_SECONDS + "s...");
                try {
                    Thread.sleep(RETRY_DELAY_SECONDS * 1000L);
                } catch (InterruptedException ignored) {
                }
            } else if (retries >= MAX_RETRIES) {
                log("Maximum retry attempts reached. Giving up.");
            }
        }
    }

    private void sendJson(Map<String, Object> map) {
        if (webSocket == null)
            return;
        String json = gson.toJson(map);
        boolean ok = webSocket.send(json);
        if (!ok) {
            log("Failed to send JSON: " + json);
        }
    }

    // HTTP helper: performs a request and sends response back via websocket
    private void performHttpRequestAsync(Map<String, Object> data) {
        ioPool.submit(() -> {
            try {
                String method = (String) data.get("method");
                String url = (String) data.get("url");
                Map<String, String> headers = (Map<String, String>) data.getOrDefault("headers", Map.of());
                String body = data.get("body") == null ? null : String.valueOf(data.get("body"));
                String requestId = String.valueOf(data.get("request_id"));

                Request.Builder builder = new Request.Builder().url(url);
                headers.forEach(builder::addHeader);

                if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    MediaType mt = MediaType.parse("application/octet-stream; charset=utf-8");
                    if (body == null)
                        body = "";
                    builder.method(method, RequestBody.create(body.getBytes(StandardCharsets.UTF_8), mt));
                } else {
                    builder.get();
                }

                Call call = httpClient.newCall(builder.build());
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Map<String, Object> resp = Map.of(
                                "type", "http-response",
                                "request_id", requestId,
                                "error", e.toString());
                        sendJson(resp);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Map<String, Object> resp = Map.of(
                                "type", "http-response",
                                "request_id", requestId,
                                "status_code", response.code(),
                                "headers", response.headers().toMultimap(),
                                "body", response.body() != null ? response.body().string() : "");
                        sendJson(resp);
                        response.close();
                    }
                });

            } catch (Exception e) {
                log("performHttpRequestAsync error: " + e.toString());
            }
        });
    }

    // Open a TCP tunnel to host:port and spawn reader that sends data back to
    // websocket
    private void openHttpsTunnel(String tunnelId, String host, int port) {
        ioPool.submit(() -> {
            Socket socket = null;
            try {
                log("Opening tunnel " + tunnelId + " -> " + host + ":" + port);
                socket = new Socket(host, port);
                activeTunnels.put(tunnelId, socket);

                // Send ready
                sendJson(Map.of("type", "https-tunnel-ready", "tunnel_id", tunnelId));

                // Start reader loop
                Socket finalSocket = socket;
                Future<?> readerFuture = ioPool.submit(() -> {
                    try (InputStream in = new BufferedInputStream(finalSocket.getInputStream())) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = in.read(buffer)) != -1 && !finalSocket.isClosed()) {
                            // hex encode
                            StringBuilder sb = new StringBuilder(read * 2);
                            for (int i = 0; i < read; i++) {
                                sb.append(String.format("%02x", buffer[i] & 0xff));
                            }
                            sendJson(Map.of("type", "https-tunnel-data", "tunnel_id", tunnelId, "data", sb.toString()));
                        }
                    } catch (IOException e) {
                        log("Tunnel reader exception for " + tunnelId + ": " + e);
                        sendJson(Map.of("type", "https-tunnel-error", "tunnel_id", tunnelId, "error", e.toString()));
                    } finally {
                        // close and cleanup
                        try {
                            finalSocket.close();
                        } catch (IOException ignored) {
                        }
                        activeTunnels.remove(tunnelId);
                        tunnelReaders.remove(tunnelId);
                    }
                });

                tunnelReaders.put(tunnelId, readerFuture);

            } catch (Exception e) {
                log("Failed to open tunnel " + tunnelId + ": " + e);
                sendJson(Map.of("type", "https-tunnel-error", "tunnel_id", tunnelId, "error", e.toString()));
                if (socket != null)
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
            }
        });
    }

    // When message of type https-tunnel-data arrives from server, write to
    // corresponding socket
    private void handleHttpsTunnelData(String tunnelId, String dataHex) {
        Socket socket = activeTunnels.get(tunnelId);
        if (socket == null) {
            log("Received tunnel data for unknown tunnel: " + tunnelId);
            return;
        }
        ioPool.submit(() -> {
            try {
                OutputStream out = socket.getOutputStream();
                byte[] bytes = hexStringToByteArray(dataHex);
                out.write(bytes);
                out.flush();
            } catch (IOException e) {
                log("Error writing to tunnel " + tunnelId + ": " + e);
                sendJson(Map.of("type", "https-tunnel-error", "tunnel_id", tunnelId, "error", e.toString()));
                // cleanup
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                activeTunnels.remove(tunnelId);
                Future<?> f = tunnelReaders.remove(tunnelId);
                if (f != null)
                    f.cancel(true);
            }
        });
    }

    private static byte[] hexStringToByteArray(String s) {
        if (s == null)
            return new byte[0];
        int len = s.length();
        byte[] data = new byte[(len + 1) / 2];
        int i = 0, j = 0;
        if ((len % 2) == 1) {
            // odd-length: treat first char as single nibble
            data[j++] = (byte) Character.digit(s.charAt(i++), 16);
        }
        while (i < len) {
            int hi = Character.digit(s.charAt(i++), 16);
            int lo = Character.digit(s.charAt(i++), 16);
            data[j++] = (byte) ((hi << 4) + lo);
        }
        if (j == data.length)
            return data;
        // trim
        byte[] trimmed = new byte[j];
        System.arraycopy(data, 0, trimmed, 0, j);
        return trimmed;
    }

    // WebSocket listener
    private class WebSocketListenerImpl extends WebSocketListener {
        private final CountDownLatch openLatch;

        WebSocketListenerImpl(CountDownLatch openLatch) {
            this.openLatch = openLatch;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            log("WebSocket onOpen. Registering node: " + NODE_ID);
            sendJson(Map.of("type", "register", "node_id", NODE_ID));
            if (openLatch != null)
                openLatch.countDown();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                Type type = new TypeToken<Map<String, Object>>() {
                }.getType();
                Map<String, Object> data = gson.fromJson(text, type);
                if (data == null || !data.containsKey("type"))
                    return;
                String t = String.valueOf(data.get("type"));

                switch (t) {
                    case "http-request":
                        performHttpRequestAsync(data);
                        break;

                    case "https-connect":
                        // expected fields: host (string), port (number), tunnel_id (string)
                        String host = String.valueOf(data.get("host"));
                        int port = ((Number) data.get("port")).intValue();
                        String tunnelId = String.valueOf(data.get("tunnel_id"));
                        openHttpsTunnel(tunnelId, host, port);
                        break;

                    case "https-tunnel-data":
                        String tid = String.valueOf(data.get("tunnel_id"));
                        String hex = String.valueOf(data.get("data"));
                        handleHttpsTunnelData(tid, hex);
                        break;

                    // server ping/pong or other messages can be handled here
                    default:
                        log("Unhandled websocket message: " + text);
                }
            } catch (Exception e) {
                log("onMessage parse error: " + e);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            log("WebSocket closing: " + code + " reason: " + reason);
            webSocket.close(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            log("WebSocket closed: " + reason);
            NodeClient.this.webSocket = null;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            log("WebSocket failure: " + t);
            NodeClient.this.webSocket = null;

            // On failure, cancel all tunnel readers and clear
            tunnelReaders.forEach((k, future) -> future.cancel(true));
            tunnelReaders.clear();
            activeTunnels.forEach((k, s) -> {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            });
            activeTunnels.clear();
        }
    }

    private void startPingLoop() {
        if (pingScheduler == null || pingScheduler.isShutdown()) {
            pingScheduler = Executors.newSingleThreadScheduledExecutor();
            pingScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (webSocket != null) {
                        log("Sending ping to ws");
                        // Option 1: Send a standard WebSocket PING frame if your library supports it
                        // webSocket.sendPing(ByteString.EMPTY);

                        // Option 2: Send a text-based heartbeat if server expects that
                        sendJson(Map.of("type", "ping", "node_id", NODE_ID));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void stopPingLoop() {
        if (pingScheduler != null && !pingScheduler.isShutdown()) {
            pingScheduler.shutdownNow();
        }
    }
}
