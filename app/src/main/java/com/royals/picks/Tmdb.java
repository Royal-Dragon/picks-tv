package com.royals.picks;

import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TMDB access: which titles are popular on the streaming services we know, and which service has each one. */
final class Tmdb {
    private static final String API = "https://api.themoviedb.org/3";
    static final String POSTER = "https://image.tmdb.org/t/p/w342";
    static final String BACKDROP = "https://image.tmdb.org/t/p/w780";

    static final class Title {
        int id;
        String type = "";      // "movie" or "tv"
        String name = "";
        String overview = "";
        String poster = "";    // path, appended to POSTER
        String backdrop = "";  // path, appended to BACKDROP
        String date = "";
        double rating;
        double popularity;
        String service = "";   // "Netflix": filled in by resolveApp
        String pkg = "";       // the TV app that plays it: filled in by resolveApp

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("type", type).put("name", name).put("overview", overview)
                    .put("poster", poster).put("backdrop", backdrop).put("date", date).put("rating", rating)
                    .put("popularity", popularity).put("service", service).put("pkg", pkg);
        }

        static Title fromJson(JSONObject o) {
            Title t = new Title();
            t.id = o.optInt("id");
            t.type = o.optString("type");
            t.name = o.optString("name");
            t.overview = o.optString("overview");
            t.poster = o.optString("poster");
            t.backdrop = o.optString("backdrop");
            t.date = o.optString("date");
            t.rating = o.optDouble("rating");
            t.popularity = o.optDouble("popularity");
            t.service = o.optString("service");
            t.pkg = o.optString("pkg");
            return t;
        }
    }

    /**
     * TMDB provider id -> {TV app package, service name}. These ids are from memory, not checked against TMDB:
     * confirm them once with GET /watch/providers/movie?watch_region=IN and fix any that are wrong.
     */
    static final Map<Integer, String[]> SERVICES = new LinkedHashMap<>();
    static {
        SERVICES.put(8, new String[]{"com.netflix.ninja", "Netflix"});
        SERVICES.put(119, new String[]{"com.amazon.amazonvideo.livingroom", "Prime Video"});
        SERVICES.put(122, new String[]{"in.startv.hotstar", "JioHotstar"});
    }

    private Tmdb() {}

    private static String get(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("Authorization", "Bearer " + BuildConfig.TMDB_TOKEN);
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("TMDB answered HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    /** Popular movies and shows on any of our services in the region: one page each, services not yet checked. */
    static List<Title> candidates(String region) throws IOException, JSONException {
        StringBuilder ids = new StringBuilder();
        for (int id : SERVICES.keySet()) ids.append(ids.length() == 0 ? "" : "%7C").append(id);
        List<Title> out = new ArrayList<>();
        for (String type : new String[]{"movie", "tv"}) {
            JSONArray results = new JSONObject(get("/discover/" + type + "?language=en-US&sort_by=popularity.desc"
                    + "&watch_region=" + region + "&with_watch_monetization_types=flatrate"
                    + "&with_watch_providers=" + ids + "&page=1")).optJSONArray("results");
            if (results == null) continue;
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                Title t = new Title();
                t.id = r.optInt("id");
                t.type = type;
                t.name = r.optString(type.equals("movie") ? "title" : "name");
                t.overview = r.optString("overview");
                t.poster = r.isNull("poster_path") ? "" : r.optString("poster_path");
                t.backdrop = r.isNull("backdrop_path") ? "" : r.optString("backdrop_path");
                t.date = r.optString(type.equals("movie") ? "release_date" : "first_air_date");
                t.rating = r.optDouble("vote_average");
                t.popularity = r.optDouble("popularity");
                if (!t.poster.isEmpty() && !t.name.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    /** Fills service and pkg with the first of our services that streams the title here AND is installed on this TV. */
    static boolean resolveApp(Title t, String region, PackageManager pm) throws IOException, JSONException {
        JSONObject res = new JSONObject(get("/" + t.type + "/" + t.id + "/watch/providers")).optJSONObject("results");
        JSONObject here = res == null ? null : res.optJSONObject(region);
        JSONArray flat = here == null ? null : here.optJSONArray("flatrate");
        if (flat == null) return false;
        for (int i = 0; i < flat.length(); i++) {
            String[] app = SERVICES.get(flat.getJSONObject(i).optInt("provider_id"));
            if (app != null && pm.getLeanbackLaunchIntentForPackage(app[0]) != null) {
                t.pkg = app[0];
                t.service = app[1];
                return true;
            }
        }
        return false;
    }
}
