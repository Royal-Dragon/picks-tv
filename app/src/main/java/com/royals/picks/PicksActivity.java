package com.royals.picks;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Full-screen page of picks: a row of posters, the focused title's backdrop and blurb behind it. */
public class PicksActivity extends Activity {
    private static final String PREFS = "picks";
    private static final long MAX_AGE_MS = 60L * 60 * 1000;   // refetch at most once an hour
    private static final int SHOWN = 12;                      // how many posters to keep

    private ImageView backdrop;
    private TextView name, meta, overview, status;
    private LinearLayout row;
    private float dp;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        buildUi();
        new Thread(this::loadPicks).start();
    }

    private int px(int v) { return Math.round(v * dp); }

    private TextView text(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0C12);

        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.40f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(px(48), px(40), px(48), px(32));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        name = text(34, Color.WHITE, true);
        meta = text(15, 0xFFB8BCC8, false);
        overview = text(16, 0xFFE0E2EA, false);
        overview.setMaxLines(3);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        status = text(16, 0xFFE8A33D, false);
        column.addView(name);
        column.addView(meta);
        LinearLayout.LayoutParams blurb = new LinearLayout.LayoutParams(px(640), -2);
        blurb.topMargin = px(10);
        column.addView(overview, blurb);
        column.addView(status);

        View spacer = new View(this);
        column.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.addView(row);
        column.addView(scroller, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        status.setText("Loading picks...");
    }

    /** Background thread: use the cached list if it is under an hour old, else ask TMDB and cache the answer. */
    private void loadPicks() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        List<Tmdb.Title> cached = parse(prefs.getString("list", ""));
        long age = System.currentTimeMillis() - prefs.getLong("ts", 0);
        if (!cached.isEmpty() && age < MAX_AGE_MS) {
            show(cached, null);
            return;
        }
        if (BuildConfig.TMDB_TOKEN.isEmpty()) {
            show(cached, "No TMDB token. Put it in tmdb.properties and rebuild.");
            return;
        }
        try {
            List<Tmdb.Title> chosen = Ranker.choose(Tmdb.candidates(BuildConfig.REGION), SHOWN * 2,
                    System.currentTimeMillis());
            List<Tmdb.Title> ready = new ArrayList<>();
            for (Tmdb.Title t : chosen) {
                if (ready.size() >= SHOWN) break;
                try {
                    if (Tmdb.resolveApp(t, BuildConfig.REGION, getPackageManager())) ready.add(t);
                } catch (Exception skip) {
                    // One title failing must not lose the rest.
                }
            }
            JSONArray out = new JSONArray();
            for (Tmdb.Title t : ready) out.put(t.toJson());
            prefs.edit().putString("list", out.toString()).putLong("ts", System.currentTimeMillis()).apply();
            show(ready, ready.isEmpty() ? "TMDB lists nothing for your apps in " + BuildConfig.REGION : null);
        } catch (Exception e) {
            show(cached, "Could not reach TMDB: " + e.getMessage());
        }
    }

    private static List<Tmdb.Title> parse(String json) {
        List<Tmdb.Title> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) out.add(Tmdb.Title.fromJson((JSONObject) a.get(i)));
        } catch (Exception ignored) {
            // An empty or damaged cache just means we fetch again.
        }
        return out;
    }

    private void show(final List<Tmdb.Title> titles, final String message) {
        runOnUiThread(() -> {
            status.setText(message == null ? "" : message);
            row.removeAllViews();
            for (Tmdb.Title t : titles) row.addView(tile(t));
            if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
        });
    }

    private View tile(final Tmdb.Title t) {
        FrameLayout cell = new FrameLayout(this);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setPadding(px(10), px(14), px(10), px(14));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(0xFF1A1D28);
        cell.addView(poster, new FrameLayout.LayoutParams(px(150), px(225)));
        Images.load(this, Tmdb.POSTER + t.poster, poster::setImageBitmap);

        cell.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f).setDuration(120).start();
            if (focused) showTitle(t);
        });
        cell.setOnClickListener(v -> open(t));
        return cell;
    }

    private void showTitle(Tmdb.Title t) {
        name.setText(t.name);
        String year = t.date.length() >= 4 ? t.date.substring(0, 4) : "";
        meta.setText(t.service + (year.isEmpty() ? "" : "  ·  " + year) + "  ·  "
                + (t.type.equals("tv") ? "Show" : "Movie") + String.format("  ·  %.1f", t.rating));
        overview.setText(t.overview);
        if (t.backdrop.isEmpty()) {
            backdrop.setImageDrawable(null);
        } else {
            final String wanted = t.backdrop;
            backdrop.setTag(wanted);
            Images.load(this, Tmdb.BACKDROP + wanted, b -> {
                if (wanted.equals(backdrop.getTag())) backdrop.setImageBitmap(b);   // ignore a late, stale image
            });
        }
    }

    private void open(Tmdb.Title t) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(t.pkg);
        if (i != null) startActivity(i);
        else status.setText(t.service + " is not installed.");
    }
}
