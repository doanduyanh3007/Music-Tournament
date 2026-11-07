package com.example.musictournament.service;

import java.net.URI;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class SongIdentity {
    private SongIdentity() {}

    public static String text(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public static String normalizeTitle(String title) {
        String clean = text(title);
        clean = clean.replaceAll("[\"\'`~\\(\\)\\[\\]\\{\\}]+", " ");
        clean = clean.replaceAll("[\\s\\p{Z}]+", " ").trim();
        return clean;
    }

    public static String normalizeArtist(String artist) {
        if (artist == null) return "";
        String s = Normalizer.normalize(artist, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        // Standardize fullwidth punctuation
        s = s.replace('；', ';').replace('，', ',').replace('、', ',');
        // Replace feat., ft., &, /, and ; with comma
        s = s.replaceAll("(?i)\\b(feat\\.?|ft\\.?)\\b", ",");
        s = s.replaceAll("[;&/|]+", ",");
        String[] parts = s.split(",");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.replaceAll("[\\s\\p{Z}]+", " ").trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        if (tokens.isEmpty()) return "";
        Collections.sort(tokens);
        return String.join(",", tokens);
    }

    public static String primaryArtist(String artist) {
        if (artist == null) return "";
        String s = Normalizer.normalize(artist, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        s = s.replace('；', ';').replace('，', ',').replace('、', ',');
        s = s.replaceAll("(?i)\\b(feat\\.?|ft\\.?)\\b", ",");
        s = s.replaceAll("[;&/|]+", ",");
        String[] parts = s.split(",");
        for (String p : parts) {
            String t = p.replaceAll("[\\s\\p{Z}]+", " ").trim();
            if (!t.isEmpty()) return t;
        }
        return "";
    }

    public static String metadata(String title, String artist) {
        return normalizeTitle(title) + "\u0000" + normalizeArtist(artist);
    }

    public static boolean areDuplicates(String title1, String artist1, String sp1, String yt1,
                                       String title2, String artist2, String sp2, String yt2) {
        // 1. Spotify track ID match
        String recSp1 = recording(sp1);
        String recSp2 = recording(sp2);
        if (recSp1 != null && recSp2 != null && recSp1.startsWith("spotify:") && recSp1.equals(recSp2)) {
            return true;
        }

        // 2. YouTube video ID match
        String recYt1 = recording(yt1);
        String recYt2 = recording(yt2);
        if (recYt1 != null && recYt2 != null && recYt1.startsWith("youtube:") && recYt1.equals(recYt2)) {
            return true;
        }

        // 3. Normalized Title + Artist match
        String normT1 = normalizeTitle(title1);
        String normT2 = normalizeTitle(title2);
        if (!normT1.isEmpty() && normT1.equals(normT2)) {
            String normA1 = normalizeArtist(artist1);
            String normA2 = normalizeArtist(artist2);
            if (normA1.equals(normA2)) {
                return true;
            }
            // If primary artist matches (e.g. B小町 == B小町)
            String prim1 = primaryArtist(artist1);
            String prim2 = primaryArtist(artist2);
            if (!prim1.isEmpty() && prim1.equals(prim2)) {
                return true;
            }
        }
        return false;
    }

    public static String recording(String link) {
        if (link == null) return null;
        try {
            URI uri = URI.create(link.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = uri.getPath();
            if (path == null) path = "";
            String video = null;
            if (host.equals("youtu.be")) {
                video = path.startsWith("/") ? path.substring(1).split("/")[0] : path.split("/")[0];
            }
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
