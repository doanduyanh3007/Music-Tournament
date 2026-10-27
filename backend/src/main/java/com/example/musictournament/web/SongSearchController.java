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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/search")
public class SongSearchController {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern YT_URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?v=|embed/|v/|shorts/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    );

    private static final Pattern YT_VIDEO_RENDERER_PATTERN = Pattern.compile(
            "(?s)\"videoRenderer\":\\{\"videoId\":\"(?<id>[a-zA-Z0-9_-]{11})\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"(?<title>[^\"]+)\"\\}\\].*?\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"(?<channel>[^\"]+)\""
    );

    @GetMapping
    public Map<String, Object> search(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "source", defaultValue = "all") String source,
            @RequestParam(name = "limit", defaultValue = "15") int limit
    ) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return Map.of("query", "", "results", List.of(), "suggestions", List.of());
        }

        List<Map<String, Object>> songResults = new ArrayList<>();
        List<String> youtubeSuggestions = new ArrayList<>();

        // 1. Trường hợp người dùng dán trực tiếp đường link YouTube
        Matcher ytUrlMatcher = YT_URL_PATTERN.matcher(trimmed);
        if (ytUrlMatcher.find()) {
            String videoId = ytUrlMatcher.group(1);
            Map<String, Object> directSong = fetchYoutubeOEmbed(videoId);
            if (directSong != null) {
                return Map.of(
                        "query", trimmed,
                        "count", 1,
                        "results", List.of(directSong),
                        "suggestions", List.of()
                );
            }
        }

        // 2. Tìm kiếm trên YouTube lấy các video chuẩn xác (nếu source là all hoặc youtube)
        List<Map<String, Object>> ytVideos = new ArrayList<>();
        if (!"spotify".equalsIgnoreCase(source)) {
            ytVideos = searchYoutubeVideos(trimmed, Math.min(limit, 20));
        }

        // 3. Tìm kiếm trên iTunes/Apple Music (nếu source là all hoặc spotify)
        List<Map<String, Object>> itunesSongs = new ArrayList<>();
        if (!"youtube".equalsIgnoreCase(source)) {
            itunesSongs = searchItunes(trimmed, limit, ytVideos);
        }

        // 4. Kết hợp kết quả theo lựa chọn của người dùng
        if ("youtube".equalsIgnoreCase(source)) {
            songResults.addAll(ytVideos);
        } else if ("spotify".equalsIgnoreCase(source)) {
            songResults.addAll(itunesSongs);
        } else {
            // "all": Kết hợp cả 2 nguồn, ưu tiên các video YouTube chính xác và bài hát phổ biến
            Set<String> seen = new HashSet<>();
            for (Map<String, Object> s : ytVideos) {
                String key = ((String) s.get("title")).toLowerCase();
                seen.add(key);
                songResults.add(s);
                if (songResults.size() >= 8) break;
            }
            for (Map<String, Object> s : itunesSongs) {
                String key = ((String) s.get("title")).toLowerCase();
                if (!seen.contains(key)) {
                    songResults.add(s);
                }
            }
            // Nếu vẫn ít kết quả, thêm phần còn lại của ytVideos
            for (Map<String, Object> s : ytVideos) {
                if (!songResults.contains(s) && songResults.size() < Math.max(limit, 16)) {
                    songResults.add(s);
                }
            }
        }

        // 5. Gợi ý từ khóa tìm kiếm (YouTube autocomplete)
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

    /**
     * Lấy thông tin video YouTube trực tiếp qua oEmbed
     */
    private Map<String, Object> fetchYoutubeOEmbed(String videoId) {
        try {
            String oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoId + "&format=json";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(oembedUrl))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode node = objectMapper.readTree(resp.body());
                String title = node.hasNonNull("title") ? node.get("title").asText() : "YouTube Song";
                String author = node.hasNonNull("author_name") ? node.get("author_name").asText() : "";
                String directYtUrl = "https://youtu.be/" + videoId;
                String artwork = "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";

                Map<String, Object> song = new LinkedHashMap<>();
                song.put("title", cleanTitle(title));
                song.put("artist", author);
                song.put("genre", "YouTube Video");
                song.put("artwork", artwork);
                song.put("previewUrl", null);
                song.put("youtubeUrl", directYtUrl);
                song.put("spotifyUrl", "https://open.spotify.com/search/" + URLEncoder.encode(title, StandardCharsets.UTF_8));
                song.put("durationMs", 0);
                song.put("formattedText", cleanTitle(title) + (author.isBlank() ? "" : " — " + author) + " | " + directYtUrl);
                song.put("source", "youtube");
                return song;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Tìm kiếm danh sách video thực tế trên YouTube để lấy link chính xác (youtu.be/{videoId})
     */
    private List<Map<String, Object>> searchYoutubeVideos(String query, int maxResults) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.youtube.com/results?search_query=" + encoded;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "vi,en;q=0.9")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                String html = resp.body();
                Matcher m = YT_VIDEO_RENDERER_PATTERN.matcher(html);
                Set<String> seenIds = new HashSet<>();

                while (m.find() && list.size() < maxResults) {
                    String id = m.group("id");
                    if (seenIds.contains(id)) continue;
                    seenIds.add(id);

                    String rawTitle = unescapeJson(m.group("title"));
                    String channel = unescapeJson(m.group("channel"));
                    String clean = cleanTitle(rawTitle);

                    String directYtUrl = "https://youtu.be/" + id;
                    String artwork = "https://img.youtube.com/vi/" + id + "/hqdefault.jpg";

                    Map<String, Object> song = new LinkedHashMap<>();
                    song.put("title", clean);
                    song.put("artist", channel);
                    song.put("genre", "YouTube");
                    song.put("artwork", artwork);
                    song.put("previewUrl", null);
                    song.put("youtubeUrl", directYtUrl);
                    song.put("spotifyUrl", "https://open.spotify.com/search/" + URLEncoder.encode(clean + " " + channel, StandardCharsets.UTF_8));
                    song.put("durationMs", 0);
                    song.put("formattedText", clean + " — " + channel + " | " + directYtUrl);
                    song.put("source", "youtube");
                    list.add(song);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * Tìm kiếm qua iTunes / Apple Music (hỗ trợ preview 30s) và tự động gắn link YouTube chính xác nếu có
     */
    private List<Map<String, Object>> searchItunes(String query, int limit, List<Map<String, Object>> ytVideos) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://itunes.apple.com/search?term=" + encoded + "&media=music&entity=song&limit=" + Math.min(limit, 25);
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

                        String artworkRaw = item.hasNonNull("artworkUrl100") ? item.get("artworkUrl100").asText() : "";
                        String artwork = artworkRaw.replace("100x100bb.jpg", "600x600bb.jpg");
                        String previewUrl = item.hasNonNull("previewUrl") ? item.get("previewUrl").asText() : "";
                        String genre = item.hasNonNull("primaryGenreName") ? item.get("primaryGenreName").asText() : "Music";
                        long durationMs = item.hasNonNull("trackTimeMillis") ? item.get("trackTimeMillis").asLong() : 0;

                        // Tìm video YouTube chính xác tương ứng trong danh sách video đã tìm được
                        String matchedYtUrl = null;
                        for (Map<String, Object> yt : ytVideos) {
                            String ytTitle = ((String) yt.get("title")).toLowerCase();
                            if (ytTitle.contains(title.toLowerCase())) {
                                matchedYtUrl = (String) yt.get("youtubeUrl");
                                break;
                            }
                        }

                        // Nếu chưa match được và ytVideos có ít nhất 1 video, gán video đầu tiên
                        if (matchedYtUrl == null && !ytVideos.isEmpty()) {
                            matchedYtUrl = (String) ytVideos.get(0).get("youtubeUrl");
                        }

                        // Fallback nếu không có video nào
                        if (matchedYtUrl == null) {
                            String queryCombo = URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8);
                            matchedYtUrl = "https://www.youtube.com/results?search_query=" + queryCombo;
                        }

                        String spotifySearchLink = "https://open.spotify.com/search/" + URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8);

                        Map<String, Object> song = new LinkedHashMap<>();
                        song.put("title", title);
                        song.put("artist", artist);
                        song.put("genre", genre);
                        song.put("artwork", artwork.isBlank() ? (matchedYtUrl.contains("youtu.be/") ? "https://img.youtube.com/vi/" + matchedYtUrl.substring(matchedYtUrl.lastIndexOf('/') + 1) + "/hqdefault.jpg" : "") : artwork);
                        song.put("previewUrl", previewUrl);
                        song.put("youtubeUrl", matchedYtUrl);
                        song.put("spotifyUrl", spotifySearchLink);
                        song.put("durationMs", durationMs);
                        song.put("formattedText", title + " — " + artist + " | " + matchedYtUrl);
                        song.put("source", "spotify");
                        list.add(song);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private String cleanTitle(String title) {
        if (title == null) return "";
        return title.replaceAll("(?i)\\[(official\\s*music\\s*video|mv|audio|vietsub|lyrics|official|m/v)\\]", "")
                .replaceAll("(?i)\\((official\\s*music\\s*video|mv|audio|vietsub|lyrics|official|m/v)\\)", "")
                .trim();
    }

    private String unescapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", " ")
                .replace("\\r", "");
    }
}
