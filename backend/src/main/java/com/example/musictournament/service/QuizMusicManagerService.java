package com.example.musictournament.service;

import com.example.musictournament.model.QuizPlaylist;
import com.example.musictournament.model.QuizSong;
import com.example.musictournament.model.Song;
import com.example.musictournament.repo.QuizPlaylistRepository;
import com.example.musictournament.repo.QuizSongRepository;
import com.example.musictournament.repo.SongRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuizMusicManagerService {

    private final QuizSongRepository quizSongRepository;
    private final QuizPlaylistRepository quizPlaylistRepository;
    private final SongRepository tournamentSongRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern YT_PLAYLIST_PATTERN = Pattern.compile("[?&]list=([a-zA-Z0-9_-]+)");
    private static final Pattern YT_VIDEO_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?v=|embed/|v/|shorts/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    );

    public QuizMusicManagerService(
            QuizSongRepository quizSongRepository,
            QuizPlaylistRepository quizPlaylistRepository,
            SongRepository tournamentSongRepository
    ) {
        this.quizSongRepository = quizSongRepository;
        this.quizPlaylistRepository = quizPlaylistRepository;
        this.tournamentSongRepository = tournamentSongRepository;
    }

    // ==========================================
    // SONG CRUD & QUERIES
    // ==========================================

    public Page<QuizSong> getSongs(String query, String genre, Integer minDiff, Integer maxDiff, Boolean activeOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return quizSongRepository.searchSongs(query, genre, minDiff, maxDiff, activeOnly, pageable);
    }

    public Optional<QuizSong> getSongById(Long id) {
        return quizSongRepository.findById(id);
    }

    public List<String> getAllGenres() {
        return quizSongRepository.findDistinctGenres();
    }

    @Transactional
    public QuizSong saveSong(QuizSong song) {
        if (song.getTitle() == null || song.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên bài hát không được để trống");
        }
        song.setTitle(song.getTitle().trim());
        if (song.getArtist() == null || song.getArtist().trim().isEmpty()) {
            song.setArtist("Unknown Artist");
        } else {
            song.setArtist(song.getArtist().trim());
        }

        // Deduplication check if creating new
        if (song.getId() == null) {
            Optional<QuizSong> existing = quizSongRepository.findFirstByTitleIgnoreCaseAndArtistIgnoreCase(
                    song.getTitle(), song.getArtist()
            );
            if (existing.isPresent()) {
                QuizSong found = existing.get();
                // Update existing record rather than duplicate
                if (song.getArtworkUrl() != null && !song.getArtworkUrl().isBlank()) found.setArtworkUrl(song.getArtworkUrl());
                if (song.getPreviewUrl() != null && !song.getPreviewUrl().isBlank()) found.setPreviewUrl(song.getPreviewUrl());
                if (song.getYoutubeUrl() != null && !song.getYoutubeUrl().isBlank()) found.setYoutubeUrl(song.getYoutubeUrl());
                if (song.getGenre() != null && !song.getGenre().isBlank()) found.setGenre(song.getGenre());
                if (song.getAliases() != null && !song.getAliases().isBlank()) found.setAliases(song.getAliases());
                if (Boolean.TRUE.equals(song.getDifficultyManuallySet())) {
                    found.setDifficulty(song.getDifficulty());
                    found.setDifficultyManuallySet(true);
                }
                return quizSongRepository.save(found);
            }
        }

        // Auto-estimate difficulty if not manually set
        if (!Boolean.TRUE.equals(song.getDifficultyManuallySet())) {
            song.setDifficulty(estimateDifficulty(song.getTitle(), song.getArtist(), song.getGenre()));
        }

        // Enrich audio preview and artwork if missing
        if ((song.getPreviewUrl() == null || song.getPreviewUrl().isBlank()) ||
            (song.getArtworkUrl() == null || song.getArtworkUrl().isBlank())) {
            enrichWithItunesData(song);
        }

        return quizSongRepository.save(song);
    }

    @Transactional
    public void deleteSong(Long id) {
        quizSongRepository.deleteById(id);
    }

    @Transactional
    public int toggleSongActive(Long id) {
        return quizSongRepository.findById(id).map(s -> {
            s.setActive(!Boolean.TRUE.equals(s.getActive()));
            quizSongRepository.save(s);
            return s.getActive() ? 1 : 0;
        }).orElse(-1);
    }

    // ==========================================
    // IMPORT FROM TOURNAMENTS
    // ==========================================

    @Transactional
    public Map<String, Object> importSongsFromTournaments() {
        List<Song> tournamentSongs = tournamentSongRepository.findAll();
        int importedCount = 0;
        int skippedCount = 0;

        for (Song tSong : tournamentSongs) {
            if (tSong.getTitle() == null || tSong.getTitle().isBlank()) continue;
            String title = cleanSongTitle(tSong.getTitle());
            String artist = tSong.getArtist() != null && !tSong.getArtist().isBlank() ? tSong.getArtist().trim() : "Unknown Artist";

            Optional<QuizSong> exists = quizSongRepository.findFirstByTitleIgnoreCaseAndArtistIgnoreCase(title, artist);
            if (exists.isPresent()) {
                skippedCount++;
                continue;
            }

            QuizSong quizSong = new QuizSong();
            quizSong.setTitle(title);
            quizSong.setArtist(artist);
            quizSong.setYoutubeUrl(tSong.getLink());
            quizSong.setGenre("Anime / Tournament");
            quizSong.setDifficulty(estimateDifficulty(title, artist, "Anime"));
            quizSong.setDifficultyManuallySet(false);
            quizSong.setActive(true);

            enrichWithItunesData(quizSong);
            quizSongRepository.save(quizSong);
            importedCount++;
        }

        return Map.of(
                "totalTournamentSongs", tournamentSongs.size(),
                "importedCount", importedCount,
                "skippedDuplicateCount", skippedCount
        );
    }

    // ==========================================
    // PLAYLIST MANAGER & SYNC
    // ==========================================

    public List<QuizPlaylist> getPlaylists() {
        return quizPlaylistRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public QuizPlaylist addPlaylist(String url, String customTitle) {
        String trimmedUrl = url.trim();
        String source = "custom";
        if (trimmedUrl.contains("youtube.com") || trimmedUrl.contains("youtu.be")) {
            source = "youtube";
        } else if (trimmedUrl.contains("spotify.com")) {
            source = "spotify";
        }

        String title = customTitle != null && !customTitle.isBlank() ? customTitle.trim() : "Playlist " + source.toUpperCase();
        QuizPlaylist playlist = new QuizPlaylist(title, source, trimmedUrl);
        return quizPlaylistRepository.save(playlist);
    }

    public void triggerSyncAsync(Long playlistId) {
        CompletableFuture.runAsync(() -> {
            try {
                syncPlaylist(playlistId);
            } catch (Exception e) {
                // logged in syncPlaylist
            }
        });
    }

    @Transactional
    public void syncPlaylist(Long playlistId) {
        Optional<QuizPlaylist> opt = quizPlaylistRepository.findById(playlistId);
        if (opt.isEmpty()) return;
        QuizPlaylist playlist = opt.get();

        playlist.setSyncStatus("SYNCING");
        playlist.setLastErrorMessage(null);
        quizPlaylistRepository.save(playlist);

        int addedCount = 0;
        try {
            if ("youtube".equalsIgnoreCase(playlist.getSource())) {
                addedCount = syncYoutubePlaylist(playlist.getSourceUrl());
            } else if ("spotify".equalsIgnoreCase(playlist.getSource())) {
                addedCount = syncSpotifyPlaylist(playlist.getSourceUrl());
            } else {
                throw new UnsupportedOperationException("Chưa hỗ trợ nguồn playlist: " + playlist.getSource());
            }

            playlist.setItemCount(playlist.getItemCount() + addedCount);
            playlist.setSyncStatus("COMPLETED");
            playlist.setLastSyncAt(LocalDateTime.now());
            quizPlaylistRepository.save(playlist);
        } catch (Exception e) {
            playlist.setSyncStatus("ERROR");
            playlist.setLastErrorMessage(e.getMessage() != null ? e.getMessage() : e.toString());
            quizPlaylistRepository.save(playlist);
        }
    }

    @Transactional
    public void deletePlaylist(Long id) {
        quizPlaylistRepository.deleteById(id);
    }

    // ==========================================
    // SYNC LOGIC (YOUTUBE / SPOTIFY)
    // ==========================================

    private int syncYoutubePlaylist(String url) throws Exception {
        Matcher plMatcher = YT_PLAYLIST_PATTERN.matcher(url);
        String playlistId = null;
        if (plMatcher.find()) {
            playlistId = plMatcher.group(1);
        } else {
            // Single video fallback
            Matcher vidMatcher = YT_VIDEO_PATTERN.matcher(url);
            if (vidMatcher.find()) {
                String videoId = vidMatcher.group(1);
                return processSingleYoutubeTrack(videoId);
            }
            throw new IllegalArgumentException("Không tìm thấy Playlist ID (list=...) hoặc Video ID trong đường dẫn YouTube");
        }

        // Fetch YouTube Playlist RSS feed (public, no API key needed, high reliability)
        String rssUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=" + playlistId;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(rssUrl))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 || resp.body() == null) {
            throw new IllegalStateException("Không thể tải thông tin Playlist từ YouTube (HTTP " + resp.statusCode() + "). Vui lòng đảm bảo playlist ở chế độ Công khai (Public/Unlisted).");
        }

        String body = resp.body();
        // Parse XML entries: <yt:videoId>...</yt:videoId> and <title>...</title>
        Pattern entryPattern = Pattern.compile("<yt:videoId>(?<vid>[^<]+)</yt:videoId>.*?<title>(?<vtitle>[^<]+)</title>", Pattern.DOTALL);
        Matcher m = entryPattern.matcher(body);

        int count = 0;
        while (m.find()) {
            String videoId = m.group("vid").trim();
            String rawTitle = m.group("vtitle").trim();
            if (rawTitle.isEmpty() || videoId.isEmpty()) continue;

            String[] parsed = parseTitleAndArtist(rawTitle);
            String title = parsed[0];
            String artist = parsed[1];
            String ytUrl = "https://www.youtube.com/watch?v=" + videoId;

            Optional<QuizSong> exists = quizSongRepository.findFirstByTitleIgnoreCaseAndArtistIgnoreCase(title, artist);
            if (exists.isPresent()) continue;

            QuizSong song = new QuizSong();
            song.setTitle(title);
            song.setArtist(artist);
            song.setYoutubeUrl(ytUrl);
            song.setArtworkUrl("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg");
            song.setGenre("J-Pop / Anime");
            song.setDifficulty(estimateDifficulty(title, artist, song.getGenre()));
            song.setDifficultyManuallySet(false);
            song.setActive(true);

            enrichWithItunesData(song);
            quizSongRepository.save(song);
            count++;
        }

        return count;
    }

    private int processSingleYoutubeTrack(String videoId) {
        String ytUrl = "https://www.youtube.com/watch?v=" + videoId;
        Optional<QuizSong> byYt = quizSongRepository.findFirstByYoutubeUrl(ytUrl);
        if (byYt.isPresent()) return 0;

        try {
            String oembedUrl = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(ytUrl, StandardCharsets.UTF_8) + "&format=json";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(oembedUrl)).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode json = objectMapper.readTree(resp.body());
                String rawTitle = json.path("title").asText("");
                String author = json.path("author_name").asText("YouTube Artist");
                String[] parsed = parseTitleAndArtist(rawTitle);
                String title = parsed[0];
                String artist = !parsed[1].equals("Unknown Artist") ? parsed[1] : author;

                QuizSong song = new QuizSong();
                song.setTitle(title);
                song.setArtist(artist);
                song.setYoutubeUrl(ytUrl);
                song.setArtworkUrl("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg");
                song.setGenre("General");
                song.setDifficulty(estimateDifficulty(title, artist, song.getGenre()));
                song.setActive(true);
                enrichWithItunesData(song);
                quizSongRepository.save(song);
                return 1;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private int syncSpotifyPlaylist(String url) throws Exception {
        // Spotify public playlist oEmbed or HTML open graph metadata
        String oembedUrl = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(oembedUrl)).timeout(Duration.ofSeconds(6)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200 && resp.body() != null) {
            JsonNode json = objectMapper.readTree(resp.body());
            String title = json.path("title").asText("Spotify Playlist");
            String thumbnail = json.path("thumbnail_url").asText("");
            // Spotify oEmbed gives playlist title & cover. Full playlist tracks via Spotify require an OAuth token;
            // if single track:
            if (url.contains("/track/")) {
                String[] parts = title.split(" - song and lyrics by | by | - ");
                String songTitle = parts.length > 0 ? parts[0].trim() : title;
                String songArtist = parts.length > 1 ? parts[1].trim() : "Spotify Artist";

                Optional<QuizSong> exists = quizSongRepository.findFirstByTitleIgnoreCaseAndArtistIgnoreCase(songTitle, songArtist);
                if (exists.isEmpty()) {
                    QuizSong song = new QuizSong();
                    song.setTitle(songTitle);
                    song.setArtist(songArtist);
                    song.setSpotifyUrl(url);
                    song.setArtworkUrl(thumbnail);
                    song.setDifficulty(estimateDifficulty(songTitle, songArtist, "Pop"));
                    song.setActive(true);
                    enrichWithItunesData(song);
                    quizSongRepository.save(song);
                    return 1;
                }
            }
        }
        return 0;
    }

    // ==========================================
    // ITUNES AUDIO PREVIEW ENRICHMENT & PARSING
    // ==========================================

    private void enrichWithItunesData(QuizSong song) {
        try {
            String query = song.getTitle() + " " + song.getArtist();
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String itunesUrl = "https://itunes.apple.com/search?term=" + encoded + "&media=music&entity=song&limit=1";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(itunesUrl))
                    .timeout(Duration.ofSeconds(3))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode results = root.path("results");
                if (results.isArray() && results.size() > 0) {
                    JsonNode item = results.get(0);
                    if (song.getPreviewUrl() == null || song.getPreviewUrl().isBlank()) {
                        String preview = item.path("previewUrl").asText("");
                        if (!preview.isBlank()) song.setPreviewUrl(preview);
                    }
                    if (song.getArtworkUrl() == null || song.getArtworkUrl().isBlank()) {
                        String art = item.path("artworkUrl100").asText("");
                        if (!art.isBlank()) song.setArtworkUrl(art.replace("100x100bb.jpg", "600x600bb.jpg"));
                    }
                    if ("General".equalsIgnoreCase(song.getGenre())) {
                        String genre = item.path("primaryGenreName").asText("");
                        if (!genre.isBlank()) song.setGenre(genre);
                    }
                }
            }
        } catch (Exception ignored) {
            // Non-critical: failure simply leaves previewUrl empty or relies on YouTube
        }
    }

    public static String[] parseTitleAndArtist(String rawTitle) {
        String cleaned = rawTitle
                .replaceAll("(?i)\\[(official\\s*music\\s*video|mv|audio|vietsub|lyrics|official|m/v|hd|4k)\\]", "")
                .replaceAll("(?i)\\((official\\s*music\\s*video|mv|audio|vietsub|lyrics|official|m/v|hd|4k)\\)", "")
                .trim();

        // Pattern 1: Artist - Title
        if (cleaned.contains(" - ")) {
            String[] parts = cleaned.split(" - ", 2);
            return new String[]{parts[1].trim(), parts[0].trim()};
        }
        // Pattern 2: Title / Artist
        if (cleaned.contains(" / ")) {
            String[] parts = cleaned.split(" / ", 2);
            return new String[]{parts[0].trim(), parts[1].trim()};
        }
        // Pattern 3: 「Title」 / Artist
        if (cleaned.contains("「") && cleaned.contains("」")) {
            Matcher m = Pattern.compile("「([^」]+)」(.*)").matcher(cleaned);
            if (m.find()) {
                String title = m.group(1).trim();
                String artist = m.group(2).replaceAll("^[\\s/\\-–]+", "").trim();
                return new String[]{title, artist.isEmpty() ? "Unknown Artist" : artist};
            }
        }

        return new String[]{cleaned, "Unknown Artist"};
    }

    private String cleanSongTitle(String title) {
        return title
                .replaceAll("(?i)\\[(official\\s*music\\s*video|mv|audio|vietsub|lyrics|official|m/v)\\]", "")
                .replaceAll("(?i)\\((official\\s*music\\s*video|mv|audio|vietsub|lyrics|official|m/v)\\)", "")
                .trim();
    }

    // ==========================================
    // AUTO-DIFFICULTY ESTIMATION
    // ==========================================

    public static int estimateDifficulty(String title, String artist, String genre) {
        if (title == null || title.isBlank()) return 50;

        int score = 40; // baseline: Normal

        // Title length complexity
        int length = title.length();
        if (length > 25) {
            score += 25; // long titles are much harder to memorize exactly
        } else if (length > 15) {
            score += 15;
        } else if (length < 6) {
            score -= 10; // short titles like "Gurenge", "Unravel", "Sparkle" are easier
        }

        // Japanese characters (Kanji / Katakana / Hiragana)
        boolean hasJapanese = title.chars().anyMatch(c ->
                (c >= 0x3040 && c <= 0x309F) || // Hiragana
                (c >= 0x30A0 && c <= 0x30FF) || // Katakana
                (c >= 0x4E00 && c <= 0x9FAF)    // Kanji
        );
        if (hasJapanese) {
            score += 20; // non-latin characters increase guessing difficulty
        }

        // Complex punctuation / parentheses / subtitles
        if (title.contains("(") || title.contains("~") || title.contains(":") || title.contains("-")) {
            score += 10;
        }

        // Genre modifiers
        if (genre != null) {
            String g = genre.toLowerCase();
            if (g.contains("anime") || g.contains("pop")) {
                score -= 5; // Anime and Pop songs are more mainstream
            } else if (g.contains("classical") || g.contains("jazz") || g.contains("instrumental")) {
                score += 15;
            }
        }

        return Math.max(10, Math.min(95, score));
    }
}
