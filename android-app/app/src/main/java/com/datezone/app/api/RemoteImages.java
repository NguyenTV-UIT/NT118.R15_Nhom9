package com.datezone.app.api;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import com.datezone.app.R;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemoteImages {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };

    public static void load(ImageView view, String source) {
        Object request = new Object();
        view.setTag(R.id.api_image_request, request);
        view.setImageResource(R.drawable.discovery_profile_placeholder);
        if (source == null || source.isEmpty()) return;
        final String url;
        try {
            URI uri = URI.create(source);
            URI base = URI.create(ApiClient.baseUrl());
            if (uri.getHost() == null) uri = base.resolve(uri);
            if ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
                uri = new URI(base.getScheme(), null, base.getHost(), base.getPort(), uri.getPath(), uri.getQuery(), null);
            url = uri.toString();
        } catch (Exception e) { return; }
        Bitmap cached = CACHE.get(url);
        if (cached != null) { view.setImageBitmap(cached); return; }
        IO.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                if (conn.getResponseCode() != 200) return;
                byte[] bytes;
                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        if (out.size() + n > 6 * 1024 * 1024) return;
                        out.write(buf, 0, n);
                    }
                    bytes = out.toByteArray();
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                opts.inSampleSize = 1;
                while (Math.max(opts.outWidth, opts.outHeight) / opts.inSampleSize > 1200) opts.inSampleSize *= 2;
                opts.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                if (bitmap == null) return;
                CACHE.put(url, bitmap);
                MAIN.post(() -> {
                    if (view.getTag(R.id.api_image_request) == request) view.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) {
                // Keep the placeholder on image errors.
            } finally { if (conn != null) conn.disconnect(); }
        });
    }
}
