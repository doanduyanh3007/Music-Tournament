package com.example.musictournament.service;

import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class SongIdentity {
    private SongIdentity() {}

    static String text(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]+", " ").trim().toLowerCase(Locale.ROOT);
    }

    static String metadata(String title, String artist) {
        return text(title) + "\u0000" + text(artist);
    }

    // Search links identify a query, not a recording, so they must not be deduplicated.
    static String recording(String link) {
        if (link == null) return null;
        try {
            URI uri = URI.create(link);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = uri.getPath();
            String video = null;
            if (host.equals("youtu.be")) video = path.substring(1).split("/")[0];
            if (host.equals("youtube.com") || host.equals("m.youtube.com")) {
                var query = Pattern.compile("(?:^|&)v=([A-Za-z0-9_-]{11})(?:&|$)").matcher(uri.getQuery() == null ? "" : uri.getQuery());
                if (path.equals("/watch") && query.find()) video = query.group(1);
                var embedded = Pattern.compile("^/(?:embed|shorts)/([A-Za-z0-9_-]{11})/?$").matcher(path);
                if (embedded.matches()) video = embedded.group(1);
            }
            if (video != null && video.matches("[A-Za-z0-9_-]{11}")) return "youtube:" + video;
            if (host.equals("open.spotify.com")) {
                var track = Pattern.compile("^/(?:intl-[^/]+/)?track/([A-Za-z0-9]+)/?$").matcher(path);
                if (track.matches()) return "spotify:" + track.group(1);
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }
}
