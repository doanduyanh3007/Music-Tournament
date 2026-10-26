package com.example.musictournament.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/search")
public class SongSearchController {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public Map<String, Object> search(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "limit", defaultValue = "15") int limit
    ) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return Map.of("query", "", "results", List.of(), "suggestions", List.of());
        }

        List<Map<String, Object>> songResults = new ArrayList<>();
        List<String> youtubeSuggestions = new ArrayList<>();

        // 1. Search Music (Spotify / iTunes / Apple Music Database)
        try {
            String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
            String url = "https://itunes.apple.com/search?term=" + encoded + "&media=music&entity=song&limit=" + Math.min(limit, 30);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode results = root.get("results");
                if (results != null && results.isArray()) {
                    for (JsonNode item : results) {
                        String title = item.hasNonNull("trackName") ? item.get("trackName").asText() : "";
                        String artist = item.hasNonNull("artistName") ? item.get("artistName").asText() : "";
                        if (title.isBlank()) continue;

                        String artwork = item.hasNonNull("artworkUrl100") ? item.get("artworkUrl100").asText() : "";
                        String previewUrl = item.hasNonNull("previewUrl") ? item.get("previewUrl").asText() : "";
                        String genre = item.hasNonNull("primaryGenreName") ? item.get("primaryGenreName").asText() : "Music";
                        long durationMs = item.hasNonNull("trackTimeMillis") ? item.get("trackTimeMillis").asLong() : 0;

                        String queryCombo = URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8);
                        String ytSearchLink = "https://www.youtube.com/results?search_query=" + queryCombo;
                        String spotifySearchLink = "https://open.spotify.com/search/" + queryCombo;

                        Map<String, Object> song = new LinkedHashMap<>();
                        song.put("title", title);
                        song.put("artist", artist);
                        song.put("genre", genre);
                        song.put("artwork", artwork);
                        song.put("previewUrl", previewUrl);
                        song.put("youtubeUrl", ytSearchLink);
                        song.put("spotifyUrl", spotifySearchLink);
                        song.put("durationMs", durationMs);
                        song.put("formattedText", title + " — " + artist + " | " + ytSearchLink);
                        songResults.add(song);
                    }
                }
            }
        } catch (Exception ignored) {
            // Log and continue gracefully
        }

        // 2. Fetch YouTube Autocomplete Suggestions
        try {
            String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
            String url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=" + encoded;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode arr = objectMapper.readTree(resp.body());
                if (arr.isArray() && arr.size() > 1 && arr.get(1).isArray()) {
                    for (JsonNode s : arr.get(1)) {
                        youtubeSuggestions.add(s.asText());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", trimmed);
        out.put("count", songResults.size());
        out.put("results", songResults);
        out.put("suggestions", youtubeSuggestions);
        return out;
    }
}
