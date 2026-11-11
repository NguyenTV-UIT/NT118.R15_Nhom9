package com.datezone.app.api;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.datezone.app.BuildConfig;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** JSON transport. Requests and token refresh run off the main thread. */
public final class ApiClient {
    public interface Callback {
        void onSuccess(JSONObject response) throws Exception;
        void onError(String message);
    }
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Object SESSION_LOCK = new Object();
    private final SessionStore session;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());

    public ApiClient(Context context) {
        this.context = context.getApplicationContext();
        session = new SessionStore(context);
    }

    public static String baseUrl() {
        String url = BuildConfig.API_BASE_URL;
        return url.endsWith("/") ? url : url + "/";
    }

    public void request(Activity owner, String method, String path, JSONObject body, Callback callback) {
        dispatch(owner, () -> execute(method, path, body), callback);
    }

    public void publicRequest(Activity owner, String method, String path, JSONObject body, Callback callback) {
        dispatch(owner, () -> executePublic(method, path, body), callback);
    }

    private interface Work { JSONObject run() throws Exception; }

    private void dispatch(Activity owner, Work work, Callback callback) {
        IO.execute(() -> {
            try {
                JSONObject result = work.run();
                main.post(() -> {
                    if (owner.isFinishing() || owner.isDestroyed()) return;
                    try { callback.onSuccess(result); }
                    catch (Exception e) { callback.onError("Dữ liệu máy chủ không đúng định dạng."); }
                });
            } catch (Exception e) {
                String message = e instanceof java.net.SocketTimeoutException
                        ? "Máy chủ phản hồi quá chậm. Vui lòng thử lại."
                        : e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException
                        ? "Không kết nối được máy chủ. Vui lòng kiểm tra địa chỉ API và mạng."
                        : e instanceof org.json.JSONException ? "Dữ liệu máy chủ không đúng định dạng." : e.getMessage();
                main.post(() -> {
                    if (!owner.isFinishing() && !owner.isDestroyed())
                        callback.onError(message == null ? "Không thể tải dữ liệu. Vui lòng thử lại." : message);
                });
            }
        });
    }

    JSONObject execute(String method, String path, JSONObject body) throws Exception {
        return executePayload(method, path, Payload.json(body));
    }

    JSONObject executePublic(String method, String path, JSONObject body) throws Exception {
        Reply reply = exchange(method, path, Payload.json(body), "");
        requireSuccess(reply);
        return reply.json;
    }

    private JSONObject executePayload(String method, String path, Payload body) throws Exception {
        String[] snapshot = session.snapshot();
        String token = snapshot[0];
        String generation = snapshot[1];
        if (token.isEmpty()) throw new IOException("Vui lòng đăng nhập để tải dữ liệu từ máy chủ.");
        Reply reply = exchange(method, path, body, token);
        if (reply.status == 401) {
            synchronized (SESSION_LOCK) {
                requireCurrentSession(generation);
                if (token.equals(session.accessToken())) {
                    String refresh = session.refreshToken();
                    if (refresh.isEmpty()) {
                        session.clearIfCurrent(generation);
                        throw new IOException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
                    }
                    Reply renewed = exchange("POST", "auth/refresh",
                            Payload.json(new JSONObject().put("refreshToken", refresh)), "");
                    if (renewed.status == 401 || renewed.status == 403) session.clearIfCurrent(generation);
                    requireSuccess(renewed);
                    if (!session.saveRefreshedTokens(generation, renewed.json.getJSONObject("data")))
                        throw new IOException("Phiên đăng nhập đã thay đổi. Vui lòng thử lại.");
                }
                token = session.accessToken();
            }
            if (token.isEmpty()) throw new IOException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
            reply = exchange(method, path, body, token);
        }
        requireCurrentSession(generation);
        requireSuccess(reply);
        return reply.json;
    }

    private void requireCurrentSession(String generation) throws IOException {
        if (!generation.equals(session.generation()))
            throw new IOException("Phiên đăng nhập đã thay đổi. Vui lòng thử lại.");
    }

    private static void requireSuccess(Reply reply) throws IOException {
        if (reply.status < 200 || reply.status >= 300 || !reply.json.optBoolean("success")) {
            String message = reply.json.optString("message", "Máy chủ trả về lỗi " + reply.status);
            org.json.JSONArray errors = reply.json.optJSONArray("errors");
            if (errors != null) for (int i = 0; i < errors.length(); i++) {
                JSONObject error = errors.optJSONObject(i);
                if (error != null) message += "\n" + error.optString("message");
            }
            throw new IOException(message);
        }
    }

    public void uploadPhoto(Activity owner, android.net.Uri uri, Callback callback) {
        dispatch(owner, () -> executePhoto(uri), callback);
    }

    JSONObject executePhoto(android.net.Uri uri) throws Exception {
        String type = context.getContentResolver().getType(uri);
        if (type == null) type = java.net.URLConnection.guessContentTypeFromName(uri.getLastPathSegment());
        if (!"image/jpeg".equals(type) && !"image/png".equals(type) && !"image/webp".equals(type))
            throw new IOException("Chỉ hỗ trợ ảnh JPG, PNG hoặc WebP.");
        String boundary = "DateZone" + java.util.UUID.randomUUID().toString().replace("-", "");
        String extension = "image/png".equals(type) ? "png" : "image/webp".equals(type) ? "webp" : "jpg";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"photo." + extension
                + "\"\r\nContent-Type: " + type + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Không mở được ảnh đã chọn.");
            byte[] buffer = new byte[8192];
            int n, size = 0;
            while ((n = in.read(buffer)) != -1) {
                size += n;
                if (size > 5 * 1024 * 1024) throw new IOException("Ảnh phải có dung lượng tối đa 5 MB.");
                out.write(buffer, 0, n);
            }
        }
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return executePayload("POST", "users/me/photos", new Payload(out.toByteArray(), "multipart/form-data; boundary=" + boundary));
    }

    private static Reply exchange(String method, String path, Payload body, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl() + path).openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept", "application/json");
            if (!token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", body.type);
                try (java.io.OutputStream out = conn.getOutputStream()) {
                    out.write(body.bytes);
                }
            }
            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) throw new IOException("Máy chủ không trả về dữ liệu (" + status + ").");
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                return new Reply(status, new JSONObject(out.toString("UTF-8")));
            }
        } finally { conn.disconnect(); }
    }

    private static final class Payload {
        final byte[] bytes;
        final String type;
        Payload(byte[] bytes, String type) { this.bytes = bytes; this.type = type; }
        static Payload json(JSONObject body) {
            return body == null ? null : new Payload(body.toString().getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
        }
    }

    private static final class Reply {
        final int status;
        final JSONObject json;
        Reply(int status, JSONObject json) { this.status = status; this.json = json; }
    }
}
