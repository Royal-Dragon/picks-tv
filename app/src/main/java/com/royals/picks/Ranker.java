package com.royals.picks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Decides which of the candidate titles Picks shows, and in what order. */
final class Ranker {
    private Ranker() {}

    /**
     * @param candidates movies and shows from TMDB, popular on our services in this region (movies first, then
     *                   shows; each group already sorted by popularity)
     * @param count      how many to return, at most
     * @param nowMillis  the current time, so the choice can change by hour or by day
     */
    static List<Tmdb.Title> choose(List<Tmdb.Title> candidates, int count, long nowMillis) {
        // get random list based on imdb ratings and that are available on netflix or jio hotstar
        List<Tmdb.Title> sorted = new ArrayList<>(candidates);
        Collections.sort(sorted, new Comparator<Tmdb.Title>() {
            @Override public int compare(Tmdb.Title a, Tmdb.Title b) {
                return Double.compare(b.popularity, a.popularity);
            }
        });
        return sorted.subList(0, Math.min(count, sorted.size()));
    }
}
