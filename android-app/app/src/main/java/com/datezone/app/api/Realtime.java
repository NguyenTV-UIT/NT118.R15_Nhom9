package com.datezone.app.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kênh realtime của app: 1 kết nối WebSocket tới máy chủ (ws://<host>/ws) dùng chung cho mọi màn hình.
 *
 * - DateZoneApp gọi start() khi app lên trước màn hình và stop() khi app xuống nền.
 * - Tự kết nối lại khi rớt mạng; bị máy chủ đóng với mã 4401 (token hết hạn) thì làm mới token rồi nối lại.
 * - Màn hình đăng ký addListener() để nhận sự kiện (luôn được gọi trên luồng giao diện):
 *     message.new, message.read, typing, presence, like.new, match.new, match.removed,
 *     call.incoming, call.accepted, call.rejected, call.cancelled, call.ended, call.missed, call.signal
 *   và 2 sự kiện nội bộ: ws.open (vừa nối xong - nên tải bù dữ liệu), ws.closed.
 */
public final class Realtime {
    public interface Listener {
        void onEvent(String event, JSONObject data);
    }

    public static final String EVENT_OPEN = "ws.open";
    public static final String EVENT_CLOSED = "ws.closed";
    private static final long PING_INTERVAL_MS = 25000;
    private static final long[] RETRY_DELAYS_MS = {1000, 2000, 5000, 10000};
    private static final Realtime INSTANCE = new Realtime();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private Context appContext;
    private Thread worker;
    private volatile boolean wanted;
    private volatile boolean open;
    private volatile WsConnection connection;

    private final Runnable pingTask = new Runnable() {
        @Override public void run() {
            if (open) send("ping", null);
            main.postDelayed(this, PING_INTERVAL_MS);
        }
    };

    private Realtime() { }

    public static Realtime get() {
        return INSTANCE;
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isConnected() {
        return open;
    }

    /** Bắt đầu (hoặc tiếp tục) giữ kết nối. Gọi nhiều lần không sao. */
    public void start(Context context) {
        synchronized (lock) {
            appContext = context.getApplicationContext();
            wanted = true;
            if (worker == null || !worker.isAlive()) {
                worker = new Thread(this::runLoop, "datezone-realtime");
                worker.setDaemon(true);
                worker.start();
            } else {
                lock.notifyAll(); // đang ngủ chờ thử lại -> thử ngay
            }
        }
        main.removeCallbacks(pingTask);
        main.postDelayed(pingTask, PING_INTERVAL_MS);
    }

    /** Ngắt kết nối (app xuống nền). */
    public void stop() {
        synchronized (lock) {
            wanted = false;
            lock.notifyAll();
        }
        main.removeCallbacks(pingTask);
        WsConnection current = connection;
        if (current != null) writer.execute(current::close);
    }

    /** Gửi 1 sự kiện lên máy chủ (typing, call.signal...). Không chặn luồng giao diện. Trả về false nếu đang mất kết nối. */
    public boolean send(String event, JSONObject data) {
        WsConnection current = connection;
        if (!open || current == null) return false;
        String text;
        try {
            JSONObject message = new JSONObject().put("event", event);
            if (data != null) message.put("data", data);
            text = message.toString();
        } catch (org.json.JSONException e) {
            return false;
        }
        writer.execute(() -> current.send(text));
        return true;
    }

    /** ws://host/ws suy ra từ địa chỉ API (http://host/api/). */
    static String socketUrl() {
        String base = ApiClient.baseUrl();
        int apiIndex = base.lastIndexOf("/api");
        String root = apiIndex > 0 ? base.substring(0, apiIndex) : base.replaceAll("/+$", "");
        if (root.startsWith("https://")) return "wss://" + root.substring("https://".length()) + "/ws";
        if (root.startsWith("http://")) return "ws://" + root.substring("http://".length()) + "/ws";
        return root + "/ws";
    }

    private void runLoop() {
        int failures = 0;
        while (true) {
            synchronized (lock) {
                if (!wanted) { worker = null; return; }
            }
            String token = new SessionStore(appContext).accessToken();
            if (token.isEmpty()) { // chưa đăng nhập: chờ rồi xem lại
                pause(3000);
                continue;
            }
            final int[] closeCode = {1006};
            final boolean[] opened = {false};
            WsConnection ws = new WsConnection(socketUrl(), token, new WsConnection.Handler() {
                @Override public void onText(String text) {
                    try {
                        JSONObject message = new JSONObject(text);
                        String event = message.optString("event");
                        JSONObject data = message.optJSONObject("data");
                        if ("ready".equals(event)) {
                            open = true;
                            opened[0] = true;
                            dispatch(EVENT_OPEN, new JSONObject());
                        } else if (!"pong".equals(event)) {
                            dispatch(event, data == null ? new JSONObject() : data);
                        }
                    } catch (org.json.JSONException ignored) { /* tin không phải JSON: bỏ qua */ }
                }
                @Override public void onClosed(int code, String reason) {
                    closeCode[0] = code;
                }
            });
            try {
                ws.connect(10000);
                connection = ws;
                ws.readLoop(); // chặn tới khi kết nối đóng
            } catch (java.io.IOException | RuntimeException e) {
                ws.close();
            }
            connection = null;
            if (open) {
                open = false;
                dispatch(EVENT_CLOSED, new JSONObject());
            }
            if (!wanted) continue;

            if (closeCode[0] == 4401) {
                // Token hết hạn / phiên bị thu hồi: 1 request thường sẽ tự làm mới token (hoặc xoá phiên nếu không còn hợp lệ)
                try { new ApiClient(appContext).execute("GET", "users/me/settings", null); }
                catch (Exception ignored) { }
                pause(1000);
                continue;
            }
            failures = opened[0] ? 0 : Math.min(failures + 1, RETRY_DELAYS_MS.length - 1);
            pause(RETRY_DELAYS_MS[opened[0] ? 0 : failures]);
        }
    }

    private void pause(long ms) {
        synchronized (lock) {
            if (!wanted) return;
            try { lock.wait(ms); }
            catch (InterruptedException ignored) { }
        }
    }

    private void dispatch(String event, JSONObject data) {
        main.post(() -> {
            for (Listener listener : listeners) {
                try { listener.onEvent(event, data); }
                catch (RuntimeException e) { android.util.Log.w("Realtime", "Lỗi xử lý sự kiện " + event, e); }
            }
        });
    }
}
