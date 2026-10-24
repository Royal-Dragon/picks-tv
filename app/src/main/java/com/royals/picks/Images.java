package com.royals.picks;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A tiny image loader: three background downloads, a 12 MB memory cache, nothing on disk. */
final class Images {
    interface Done { void on(Bitmap bitmap); }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    private Images() {}

    /** Calls done on the UI thread once the image is ready; a failed download simply never calls it. */
    static void load(final Activity activity, final String url, final Done done) {
        Bitmap hit = CACHE.get(url);
        if (hit != null) {
            done.on(hit);
            return;
        }
        POOL.execute(new Runnable() {
            @Override public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(12000);
                    try (InputStream in = c.getInputStream()) {
                        final Bitmap bitmap = BitmapFactory.decodeStream(in);
                        if (bitmap == null) return;
                        CACHE.put(url, bitmap);
                        activity.runOnUiThread(new Runnable() {
                            @Override public void run() { done.on(bitmap); }
                        });
                    } finally {
                        c.disconnect();
                    }
                } catch (IOException ignored) {
                    // No picture is better than a crash on a TV with a bad connection.
                }
            }
        });
    }
}
