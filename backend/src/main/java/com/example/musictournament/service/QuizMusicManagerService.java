package com.example.musictournament.service;

import com.example.musictournament.model.QuizPlaylist;
import com.example.musictournament.model.QuizPool;
import com.example.musictournament.model.QuizPoolSong;
import com.example.musictournament.model.QuizSong;
import com.example.musictournament.model.Song;
import com.example.musictournament.repo.QuizPlaylistRepository;
import com.example.musictournament.repo.QuizPoolRepository;
import com.example.musictournament.repo.QuizPoolSongRepository;
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
    private final QuizPoolRepository quizPoolRepository;
    private final QuizPoolSongRepository quizPoolSongRepository;
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
    private static final Pattern SPOTIFY_PLAYLIST_PATTERN = Pattern.compile("(?:playlist|album)[:/]([a-zA-Z0-9]+)");

    @org.springframework.beans.factory.annotation.Value("${spotify.client-id:}")
    private String spotifyClientId;

    @org.springframework.beans.factory.annotation.Value("${spotify.client-secret:}")
    private String spotifyClientSecret;

    public QuizMusicManagerService(
            QuizSongRepository quizSongRepository,
            QuizPlaylistRepository quizPlaylistRepository,
            QuizPoolRepository quizPoolRepository,
            QuizPoolSongRepository quizPoolSongRepository,
            SongRepository tournamentSongRepository
    ) {
        this.quizSongRepository = quizSongRepository;
        this.quizPlaylistRepository = quizPlaylistRepository;
        this.quizPoolRepository = quizPoolRepository;
        this.quizPoolSongRepository = quizPoolSongRepository;
        this.tournamentSongRepository = tournamentSongRepository;
    }

    public static class EnrichmentProgress {
        private boolean isRunning = false;
        private int total = 0;
        private int processed = 0;
        private int enriched = 0;
        private int errors = 0;
        private String currentSong = "";
        private String lastMessage = "";

        public boolean isRunning() { return isRunning; }
        public void setRunning(boolean running) { isRunning = running; }
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getProcessed() { return processed; }
        public void setProcessed(int processed) { this.processed = processed; }
        public int getEnriched() { return enriched; }
        public void setEnriched(int enriched) { this.enriched = enriched; }
        public int getErrors() { return errors; }
        public void setErrors(int errors) { this.errors = errors; }
        public String getCurrentSong() { return currentSong; }
        public void setCurrentSong(String currentSong) { this.currentSong = currentSong; }
        public String getLastMessage() { return lastMessage; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    }

    private final EnrichmentProgress enrichmentProgress = new EnrichmentProgress();

    // ==========================================
    // SONG CRUD & QUERIES
    // ==========================================

    public Page<QuizSong> getSongs(String query, String genre, Integer minDiff, Integer maxDiff, Boolean activeOnly, int page, int size) {
        return getSongs(query, genre, minDiff, maxDiff, activeOnly, "id", "desc", page, size);
    }

    public Page<QuizSong> getSongs(String query, String genre, Integer minDiff, Integer maxDiff, Boolean activeOnly, String sortBy, String sortDir, int page, int size) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = "id";
        if ("artist".equalsIgnoreCase(sortBy)) {
            property = "artist";
        } else if ("title".equalsIgnoreCase(sortBy)) {
            property = "title";
        } else if ("difficulty".equalsIgnoreCase(sortBy)) {
            property = "difficulty";
        }
        Sort sort = Sort.by(direction, property);
        if (!"id".equalsIgnoreCase(property)) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);
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
                if (song.getSpotifyUrl() != null && !song.getSpotifyUrl().isBlank()) found.setSpotifyUrl(song.getSpotifyUrl());
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
            enrichSongMetadata(song);
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
    // BATCH IMPORT & ENRICHMENT
    // ==========================================

    @Transactional
    public Map<String, Object> batchImportSongs(
            List<Map<String, Object>> songList,
            Long quizPoolId,
            boolean addToQuizLibrary,
            boolean enrichMetadata
    ) {
        int added = 0;
        int duplicates = 0;
        int errors = 0;
        int poolLinked = 0;

        for (Map<String, Object> raw : songList) {
            try {
                String rawTitle = (String) raw.get("title");
                if (rawTitle == null || rawTitle.trim().isEmpty()) continue;
                String title = cleanSongTitle(rawTitle);

                String rawArtist = (String) raw.get("artist");
                String artist = (rawArtist == null || rawArtist.trim().isEmpty()) ? "Unknown Artist" : rawArtist.trim();

                String link = (String) raw.get("link");
                String ytUrl = (String) raw.get("youtubeUrl");
                String spUrl = (String) raw.get("spotifyUrl");
                if (ytUrl == null && link != null && (link.contains("youtube.com") || link.contains("youtu.be"))) {
                    ytUrl = link;
                }
                if (spUrl == null && link != null && link.contains("spotify.com")) {
                    spUrl = link;
                }

                String artwork = (String) raw.get("artworkUrl");
                if (artwork == null) artwork = (String) raw.get("artwork");
                String preview = (String) raw.get("previewUrl");
                String genre = (String) raw.get("genre");
                if (genre == null || genre.isBlank()) genre = "General";

                QuizSong finalSong;
                Optional<QuizSong> exists = quizSongRepository.findFirstByTitleIgnoreCaseAndArtistIgnoreCase(title, artist);
                if (exists.isPresent()) {
                    duplicates++;
                    finalSong = exists.get();
                    boolean updated = false;
                    if (artwork != null && !artwork.isBlank() && (finalSong.getArtworkUrl() == null || finalSong.getArtworkUrl().isBlank())) {
                        finalSong.setArtworkUrl(artwork);
                        updated = true;
                    }
                    if (preview != null && !preview.isBlank() && (finalSong.getPreviewUrl() == null || finalSong.getPreviewUrl().isBlank())) {
                        finalSong.setPreviewUrl(preview);
                        updated = true;
                    }
                    if (ytUrl != null && !ytUrl.isBlank() && (finalSong.getYoutubeUrl() == null || finalSong.getYoutubeUrl().isBlank())) {
                        finalSong.setYoutubeUrl(ytUrl);
                        updated = true;
                    }
                    if (spUrl != null && !spUrl.isBlank() && (finalSong.getSpotifyUrl() == null || finalSong.getSpotifyUrl().isBlank())) {
                        finalSong.setSpotifyUrl(spUrl);
                        updated = true;
                    }
                    if (enrichMetadata && ((finalSong.getPreviewUrl() == null || finalSong.getPreviewUrl().isBlank()) ||
                                           (finalSong.getArtworkUrl() == null || finalSong.getArtworkUrl().isBlank()))) {
                        boolean enriched = enrichSongMetadata(finalSong);
                        if (enriched) updated = true;
                    }
                    if (updated) {
                        finalSong = quizSongRepository.save(finalSong);
                    }
                } else if (addToQuizLibrary) {
                    QuizSong newSong = new QuizSong();
                    newSong.setTitle(title);
                    newSong.setArtist(artist);
                    newSong.setArtworkUrl(artwork);
                    newSong.setPreviewUrl(preview);
                    newSong.setYoutubeUrl(ytUrl);
                    newSong.setSpotifyUrl(spUrl);
                    newSong.setGenre(genre);
                    newSong.setDifficulty(estimateDifficulty(title, artist, genre));
                    newSong.setDifficultyManuallySet(false);
                    newSong.setActive(true);

                    if (enrichMetadata && ((newSong.getPreviewUrl() == null || newSong.getPreviewUrl().isBlank()) ||
                                           (newSong.getArtworkUrl() == null || newSong.getArtworkUrl().isBlank()))) {
                        enrichSongMetadata(newSong);
                    }
                    finalSong = quizSongRepository.save(newSong);
                    added++;
                } else {
                    finalSong = null;
                }

                // If pool is specified, associate song with that pool
                if (quizPoolId != null && finalSong != null && finalSong.getId() != null) {
                    Optional<QuizPoolSong> poolSongExists = quizPoolSongRepository.findByPoolIdAndSongId(quizPoolId, finalSong.getId());
                    if (poolSongExists.isEmpty()) {
                        quizPoolSongRepository.save(new QuizPoolSong(quizPoolId, finalSong.getId()));
                        poolLinked++;
                    }
                }
            } catch (Exception e) {
                errors++;
            }
        }

        return Map.of(
                "totalSubmitted", songList.size(),
                "addedCount", added,
                "duplicateCount", duplicates,
                "errorCount", errors,
                "poolLinkedCount", poolLinked
        );
    }

    public Map<String, Object> enrichMetadata(String title, String artist, String link) {
        String queryTitle = title != null ? title.trim() : "";
        String queryArtist = artist != null ? artist.trim() : "";

        if (queryTitle.isEmpty() && link != null && !link.isBlank()) {
            Matcher m = YT_VIDEO_PATTERN.matcher(link);
            if (m.find()) {
                String vid = m.group(1);
                try {
                    String oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + vid + "&format=json";
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(oembedUrl)).timeout(Duration.ofSeconds(4)).GET().build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 && resp.body() != null) {
                        JsonNode json = objectMapper.readTree(resp.body());
                        String[] parsed = parseTitleAndArtist(json.path("title").asText(""));
                        queryTitle = parsed[0];
                        queryArtist = !parsed[1].equals("Unknown Artist") ? parsed[1] : json.path("author_name").asText("");
                    }
                } catch (Exception ignored) {}
            }
        }

        QuizSong dummy = new QuizSong();
        dummy.setTitle(queryTitle);
        dummy.setArtist(queryArtist);
        dummy.setGenre("General");
        enrichSongMetadata(dummy);

        int diff = estimateDifficulty(queryTitle, queryArtist, dummy.getGenre());

        Map<String, Object> out = new HashMap<>();
        out.put("title", queryTitle);
        out.put("artist", queryArtist);
        out.put("artworkUrl", dummy.getArtworkUrl() != null ? dummy.getArtworkUrl() : "");
        out.put("previewUrl", dummy.getPreviewUrl() != null ? dummy.getPreviewUrl() : "");
        out.put("genre", dummy.getGenre() != null ? dummy.getGenre() : "General");
        out.put("difficulty", diff);
        return out;
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

            enrichSongMetadata(quizSong);
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

    public Optional<QuizPlaylist> getPlaylistById(Long id) {
        return quizPlaylistRepository.findById(id);
    }

    @Transactional
    public QuizPlaylist addPlaylist(String url, String customTitle) {
        String trimmedUrl = url.trim();
        String source = "custom";
        if (trimmedUrl.contains("youtube.com") || trimmedUrl.contains("youtu.be")) {
            source = "youtube";
        } else if (trimmedUrl.contains("spotify.com") || trimmedUrl.startsWith("spotify:")) {
            source = "spotify";
        }

        Optional<QuizPlaylist> existing = quizPlaylistRepository.findFirstBySourceUrl(trimmedUrl);
        if (existing.isPresent()) {
            QuizPlaylist pl = existing.get();
            if (customTitle != null && !customTitle.isBlank()) {
                pl.setTitle(customTitle.trim());
                return quizPlaylistRepository.save(pl);
            }
            return pl;
        }

        String title = customTitle != null && !customTitle.isBlank() ? customTitle.trim() : "Playlist " + source.toUpperCase();
        QuizPlaylist playlist = new QuizPlaylist(title, source, trimmedUrl);
        return quizPlaylistRepository.save(playlist);
    }

    public void triggerSyncAsync(Long playlistId) {
        CompletableFuture.runAsync(() -> {
            try {
                syncPlaylist(playlistId);
            } catch (Exception ignored) {
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
        playlist.setProcessedCount(0);
        playlist.setAddedCount(0);
        playlist.setDuplicateCount(0);
        playlist.setErrorCount(0);
        quizPlaylistRepository.saveAndFlush(playlist);

        List<RawTrackItem> tracks;
        try {
            if ("youtube".equalsIgnoreCase(playlist.getSource())) {
                tracks = fetchYoutubePlaylistTracks(playlist.getSourceUrl());
            } else if ("spotify".equalsIgnoreCase(playlist.getSource())) {
                tracks = fetchSpotifyPlaylistTracks(playlist.getSourceUrl());
            } else {
                throw new UnsupportedOperationException("Chưa hỗ trợ nguồn playlist: " + playlist.getSource());
            }

            if (tracks.isEmpty()) {
                throw new IllegalStateException("Không tìm thấy bài hát nào trong playlist hoặc playlist ở chế độ riêng tư (Private). Vui lòng đặt thành Công khai (Public/Unlisted).");
            }

            playlist.setTotalFound(tracks.size());
            quizPlaylistRepository.saveAndFlush(playlist);

            int addedCount = 0;
            int duplicateCount = 0;
            int errorCount = 0;

            for (int i = 0; i < tracks.size(); i++) {
                RawTrackItem item = tracks.get(i);
                try {
                    String title = cleanSongTitle(item.title);
                    String artist = item.artist != null && !item.artist.isBlank() ? item.artist.trim() : "Unknown Artist";

                    Optional<QuizSong> exists = quizSongRepository.findFirstByTitleIgnoreCaseAndArtistIgnoreCase(title, artist);
                    if (exists.isPresent()) {
                        QuizSong found = exists.get();
                        boolean updated = false;
                        if ((found.getYoutubeUrl() == null || found.getYoutubeUrl().isBlank()) && item.youtubeUrl != null) {
                            found.setYoutubeUrl(item.youtubeUrl);
                            updated = true;
                        }
                        if ((found.getSpotifyUrl() == null || found.getSpotifyUrl().isBlank()) && item.spotifyUrl != null) {
                            found.setSpotifyUrl(item.spotifyUrl);
                            updated = true;
                        }
                        if ((found.getPreviewUrl() == null || found.getPreviewUrl().isBlank()) && item.previewUrl != null && !item.previewUrl.isBlank()) {
                            found.setPreviewUrl(item.previewUrl);
                            updated = true;
                        }
                        if ((found.getArtworkUrl() == null || found.getArtworkUrl().isBlank()) && item.artworkUrl != null) {
                            found.setArtworkUrl(item.artworkUrl);
                            updated = true;
                        }
                        if (updated) quizSongRepository.save(found);
                        duplicateCount++;
                    } else {
                        QuizSong song = new QuizSong();
                        song.setTitle(title);
                        song.setArtist(artist);
                        song.setYoutubeUrl(item.youtubeUrl);
                        song.setSpotifyUrl(item.spotifyUrl);
                        song.setArtworkUrl(item.artworkUrl);
                        song.setPreviewUrl(item.previewUrl);
                        song.setGenre(item.genre != null ? item.genre : "Pop");
                        song.setDifficulty(estimateDifficulty(title, artist, song.getGenre()));
                        song.setDifficultyManuallySet(false);
                        song.setActive(true);

                        if (song.getPreviewUrl() == null || song.getPreviewUrl().isBlank()) {
                            enrichSongMetadata(song);
                        }

                        quizSongRepository.save(song);
                        addedCount++;
                    }
                } catch (Exception ex) {
                    errorCount++;
                }

                if ((i + 1) % 10 == 0 || i == tracks.size() - 1) {
                    playlist.setProcessedCount(i + 1);
                    playlist.setAddedCount(addedCount);
                    playlist.setDuplicateCount(duplicateCount);
                    playlist.setErrorCount(errorCount);
                    quizPlaylistRepository.saveAndFlush(playlist);
                }
            }

            playlist.setItemCount(playlist.getItemCount() + addedCount);
            playlist.setSyncStatus("COMPLETED");
            playlist.setLastSyncAt(LocalDateTime.now());
            playlist.setLastErrorMessage(null);
            quizPlaylistRepository.saveAndFlush(playlist);

        } catch (Exception e) {
            playlist.setSyncStatus("ERROR");
            playlist.setLastErrorMessage(e.getMessage() != null ? e.getMessage() : e.toString());
            quizPlaylistRepository.saveAndFlush(playlist);
        }
    }

    @Transactional
    public void deletePlaylist(Long id) {
        quizPlaylistRepository.deleteById(id);
    }

    // ==========================================
    // SCRAPING LOGIC (YOUTUBE / SPOTIFY)
    // ==========================================

    private List<RawTrackItem> fetchYoutubePlaylistTracks(String url) throws Exception {
        List<RawTrackItem> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        Matcher plMatcher = YT_PLAYLIST_PATTERN.matcher(url);
        String playlistId = null;
        if (plMatcher.find()) {
            playlistId = plMatcher.group(1);
        } else {
            // Check single video
            Matcher vidMatcher = YT_VIDEO_PATTERN.matcher(url);
            if (vidMatcher.find()) {
                String videoId = vidMatcher.group(1);
                RawTrackItem single = fetchSingleYoutubeTrack(videoId);
                if (single != null) results.add(single);
                return results;
            }
            throw new IllegalArgumentException("Không tìm thấy Playlist ID (list=...) hoặc Video ID trong đường dẫn YouTube");
        }

        // Method 1: Fetch full YouTube playlist HTML page (extracts up to 100 videos)
        try {
            String playlistUrl = "https://www.youtube.com/playlist?list=" + playlistId;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(playlistUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                String html = resp.body();

                // Pattern A: lockupMetadataViewModel format
                Pattern pA = Pattern.compile("\"animationActivationTargetId\":\"([a-zA-Z0-9_-]{11})\".*?\"lockupMetadataViewModel\":\\{\"title\":\\{\"content\":\"([^\"]+)\"\\}");
                Matcher mA = pA.matcher(html);
                while (mA.find()) {
                    String vid = mA.group(1);
                    String rawTitle = mA.group(2);
                    if (seenIds.add(vid)) {
                        String[] parsed = parseTitleAndArtist(rawTitle);
                        results.add(new RawTrackItem(
                                parsed[0],
                                parsed[1],
                                "https://www.youtube.com/watch?v=" + vid,
                                null,
                                "https://img.youtube.com/vi/" + vid + "/hqdefault.jpg",
                                null
                        ));
                    }
                }

                // Pattern B: playlistVideoRenderer format (classic / mobile)
                if (results.isEmpty()) {
                    Pattern pB = Pattern.compile("\"playlistVideoRenderer\":\\{\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"title\":\\{(?:\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}|\"simpleText\":\"([^\"]+)\")");
                    Matcher mB = pB.matcher(html);
                    while (mB.find()) {
                        String vid = mB.group(1);
                        String rawTitle = mB.group(2) != null ? mB.group(2) : mB.group(3);
                        if (rawTitle != null && seenIds.add(vid)) {
                            String[] parsed = parseTitleAndArtist(rawTitle);
                            results.add(new RawTrackItem(
                                    parsed[0],
                                    parsed[1],
                                    "https://www.youtube.com/watch?v=" + vid,
                                    null,
                                    "https://img.youtube.com/vi/" + vid + "/hqdefault.jpg",
                                    null
                            ));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Method 2: If HTML scrape yielded nothing, fallback to RSS XML
        if (results.isEmpty()) {
            try {
                String rssUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=" + playlistId;
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(rssUrl))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null) {
                    Pattern entryPattern = Pattern.compile("<yt:videoId>(?<vid>[^<]+)</yt:videoId>.*?<title>(?<vtitle>[^<]+)</title>", Pattern.DOTALL);
                    Matcher m = entryPattern.matcher(resp.body());
                    while (m.find()) {
                        String vid = m.group("vid").trim();
                        String rawTitle = m.group("vtitle").trim();
                        if (!vid.isEmpty() && !rawTitle.isEmpty() && seenIds.add(vid)) {
                            String[] parsed = parseTitleAndArtist(rawTitle);
                            results.add(new RawTrackItem(
                                    parsed[0],
                                    parsed[1],
                                    "https://www.youtube.com/watch?v=" + vid,
                                    null,
                                    "https://img.youtube.com/vi/" + vid + "/hqdefault.jpg",
                                    null
                            ));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Method 3: If playlist failed/private but URL contains a single video ID (watch?v=...), import that video
        if (results.isEmpty()) {
            Matcher vidMatcher = YT_VIDEO_PATTERN.matcher(url);
            if (vidMatcher.find()) {
                String videoId = vidMatcher.group(1);
                RawTrackItem single = fetchSingleYoutubeTrack(videoId);
                if (single != null) results.add(single);
            }
        }

        return results;
    }

    private RawTrackItem fetchSingleYoutubeTrack(String videoId) {
        String ytUrl = "https://www.youtube.com/watch?v=" + videoId;
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

                return new RawTrackItem(
                        title,
                        artist,
                        ytUrl,
                        null,
                        "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg",
                        null
                );
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<RawTrackItem> fetchSpotifyPlaylistTracksViaOfficialApi(String playlistId) {
        if (spotifyClientId == null || spotifyClientId.isBlank() || spotifyClientSecret == null || spotifyClientSecret.isBlank()) {
            return null;
        }
        try {
            String auth = Base64.getEncoder().encodeToString((spotifyClientId.trim() + ":" + spotifyClientSecret.trim()).getBytes(StandardCharsets.UTF_8));
            HttpRequest tokenReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                    .timeout(Duration.ofSeconds(6))
                    .build();

            HttpResponse<String> tokenResp = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            if (tokenResp.statusCode() != 200 || tokenResp.body() == null) return null;

            JsonNode tokenJson = objectMapper.readTree(tokenResp.body());
            String token = tokenJson.path("access_token").asText(null);
            if (token == null || token.isBlank()) return null;

            List<RawTrackItem> list = new ArrayList<>();
            int offset = 0;
            int limit = 100;
            int total = Integer.MAX_VALUE;

            while (offset < total) {
                String apiUrl = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks?offset=" + offset + "&limit=" + limit;
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 429) {
                    long retry = resp.headers().firstValueAsLong("Retry-After").orElse(2L);
                    Thread.sleep(retry * 1000L);
                    continue;
                }
                if (resp.statusCode() != 200 || resp.body() == null) break;

                JsonNode root = objectMapper.readTree(resp.body());
                total = root.path("total").asInt(total);
                JsonNode items = root.path("items");
                if (!items.isArray() || items.isEmpty()) break;

                for (JsonNode item : items) {
                    JsonNode track = item.path("track");
                    if (track.isMissingNode() || track.isNull()) continue;
                    String title = track.path("name").asText("").trim();
                    List<String> artists = new ArrayList<>();
                    track.path("artists").forEach(a -> {
                        String an = a.path("name").asText("").trim();
                        if (!an.isEmpty()) artists.add(an);
                    });
                    String artist = !artists.isEmpty() ? String.join(", ", artists) : "Spotify Artist";
                    String spotifyUrl = track.path("external_urls").path("spotify").asText("");
                    String previewUrl = track.path("preview_url").asText(null);
                    String artwork = null;
                    JsonNode imgs = track.path("album").path("images");
                    if (imgs.isArray() && !imgs.isEmpty()) {
                        artwork = imgs.get(0).path("url").asText(null);
                    }
                    if (!title.isEmpty()) {
                        list.add(new RawTrackItem(title, artist, null, spotifyUrl, artwork, previewUrl));
                    }
                }

                offset += limit;
                if (offset < total) {
                    Thread.sleep(100);
                }
            }

            return list.isEmpty() ? null : list;
        } catch (Exception e) {
            return null;
        }
    }

    private List<RawTrackItem> fetchSpotifyPlaylistTracks(String url) throws Exception {
        List<RawTrackItem> results = new ArrayList<>();
        Set<String> seenUris = new HashSet<>();

        Matcher m = SPOTIFY_PLAYLIST_PATTERN.matcher(url);
        if (m.find()) {
            String type = url.contains("album") ? "album" : "playlist";
            String entityId = m.group(1);

            // Attempt official API if configured (supports up to thousands of tracks with pagination)
            if ("playlist".equals(type)) {
                List<RawTrackItem> apiTracks = fetchSpotifyPlaylistTracksViaOfficialApi(entityId);
                if (apiTracks != null && !apiTracks.isEmpty()) {
                    return apiTracks;
                }
            }

            String embedUrl = "https://open.spotify.com/embed/" + type + "/" + entityId;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(embedUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null) {
                throw new IllegalStateException("Không thể tải Spotify (HTTP " + resp.statusCode() + "). Vui lòng đảm bảo playlist/album tồn tại và ở chế độ công khai.");
            }

            String body = resp.body();
            Pattern nextDataPattern = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>([\\s\\S]*?)</script>");
            Matcher ndm = nextDataPattern.matcher(body);
            if (ndm.find()) {
                try {
                    JsonNode root = objectMapper.readTree(ndm.group(1));
                    extractSpotifyTracksFromJson(root, results, seenUris);
                } catch (Exception e) {
                    System.err.println("Error parsing Spotify embed JSON: " + e.getMessage());
                }
            }

            // Fallback: Regex scan HTML for tracks if JSON extraction was empty
            if (results.isEmpty()) {
                Pattern fallbackPattern = Pattern.compile("\"uri\":\"spotify:track:([a-zA-Z0-9]+)\"[^}]*?\"title\":\"([^\"]+)\"[^}]*?\"subtitle\":\"([^\"]+)\"");
                Matcher fm = fallbackPattern.matcher(body);
                while (fm.find()) {
                    String trackId = fm.group(1);
                    String title = fm.group(2).trim();
                    String artist = fm.group(3).trim();
                    String trackUri = "spotify:track:" + trackId;
                    if (seenUris.add(trackUri)) {
                        results.add(new RawTrackItem(title, artist, null, "https://open.spotify.com/track/" + trackId, null, null));
                    }
                }
            }
        } else if (url.contains("/track/") || url.contains("spotify:track:")) {
            Matcher tm = Pattern.compile("track[:/]([a-zA-Z0-9]+)").matcher(url);
            String trackUrl = url;
            if (tm.find()) {
                trackUrl = "https://open.spotify.com/track/" + tm.group(1);
            }
            String oembedUrl = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(trackUrl, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(oembedUrl)).timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode json = objectMapper.readTree(resp.body());
                String title = json.path("title").asText("Spotify Track");
                String thumbnail = json.path("thumbnail_url").asText(null);
                String[] parts = title.split(" - song and lyrics by | by | - ");
                String songTitle = parts.length > 0 ? parts[0].trim() : title;
                String songArtist = parts.length > 1 ? parts[1].trim() : "Spotify Artist";
                results.add(new RawTrackItem(songTitle, songArtist, null, trackUrl, thumbnail, null));
            }
        } else {
            throw new IllegalArgumentException("Không tìm thấy Playlist, Album hoặc Track ID trong liên kết Spotify: " + url);
        }

        return results;
    }

    private void extractSpotifyTracksFromJson(JsonNode node, List<RawTrackItem> results, Set<String> seenUris) {
        if (node == null || node.isMissingNode()) return;
        if (node.isObject()) {
            String uri = node.path("uri").asText("");
            String title = node.path("title").asText(node.path("name").asText("")).trim();
            if (uri.startsWith("spotify:track:") && !title.isEmpty() && seenUris.add(uri)) {
                String artist = node.path("subtitle").asText("");
                if (artist.isBlank() && node.has("artists") && node.path("artists").isArray()) {
                    List<String> artNames = new ArrayList<>();
                    for (JsonNode a : node.path("artists")) {
                        String an = a.path("name").asText("");
                        if (!an.isBlank()) artNames.add(an);
                    }
                    artist = String.join(", ", artNames);
                }
                if (artist.isBlank()) artist = "Spotify Artist";
                String previewUrl = node.path("audioPreview").path("url").asText(null);
                if (previewUrl == null || previewUrl.isBlank()) {
                    previewUrl = node.path("preview_url").asText(null);
                }
                String spotifyUrl = "https://open.spotify.com/track/" + uri.substring("spotify:track:".length());
                results.add(new RawTrackItem(title, artist, null, spotifyUrl, null, previewUrl));
            }
            node.fields().forEachRemaining(entry -> extractSpotifyTracksFromJson(entry.getValue(), results, seenUris));
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                extractSpotifyTracksFromJson(item, results, seenUris);
            }
        }
    }

    // ==========================================
    // MULTI-TIER AUDIO & ARTWORK ENRICHMENT
    // ==========================================

    public boolean enrichWithItunesData(QuizSong song) {
        return enrichSongMetadata(song);
    }

    public boolean enrichSongMetadata(QuizSong song) {
        if (song == null) return false;
        boolean modified = false;

        String title = song.getTitle() != null ? song.getTitle().trim() : "";
        String artist = song.getArtist() != null ? song.getArtist().trim() : "";
        if (title.isEmpty()) return false;

        boolean isJapanese = hasJapaneseCharacters(title) || hasJapaneseCharacters(artist);

        // 1. Try iTunes (supports direct 30s MP3/AAC preview and 600x600 artwork)
        boolean foundItunes = false;
        if (isJapanese) {
            foundItunes = tryItunesEnrich(song, title, artist, "JP");
            if (!foundItunes) {
                foundItunes = tryItunesEnrich(song, title, artist, null);
            }
        } else {
            foundItunes = tryItunesEnrich(song, title, artist, null);
            if (!foundItunes && isVietnamese(title + " " + artist)) {
                foundItunes = tryItunesEnrich(song, title, artist, "VN");
            }
        }
        if (foundItunes) modified = true;

        // 2. If Spotify URL exists and artwork is still missing, fetch via Spotify oEmbed
        if ((song.getArtworkUrl() == null || song.getArtworkUrl().isBlank()) &&
                song.getSpotifyUrl() != null && song.getSpotifyUrl().contains("spotify.com")) {
            try {
                String oembedUrl = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(song.getSpotifyUrl(), StandardCharsets.UTF_8);
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(oembedUrl)).timeout(Duration.ofSeconds(4)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null) {
                    JsonNode json = objectMapper.readTree(resp.body());
                    String thumb = json.path("thumbnail_url").asText(null);
                    if (thumb != null && !thumb.isBlank()) {
                        song.setArtworkUrl(thumb);
                        modified = true;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Fallback: Search YouTube if youtubeUrl is missing or if previewUrl is still missing
        if ((song.getYoutubeUrl() == null || song.getYoutubeUrl().isBlank()) ||
                (song.getPreviewUrl() == null || song.getPreviewUrl().isBlank())) {
            String ytVid = searchFirstYoutubeVideoId(title + " " + (!artist.equalsIgnoreCase("Unknown Artist") ? artist : "") + " audio");
            if (ytVid != null) {
                if (song.getYoutubeUrl() == null || song.getYoutubeUrl().isBlank()) {
                    song.setYoutubeUrl("https://www.youtube.com/watch?v=" + ytVid);
                    modified = true;
                }
                if (song.getArtworkUrl() == null || song.getArtworkUrl().isBlank()) {
                    song.setArtworkUrl("https://img.youtube.com/vi/" + ytVid + "/hqdefault.jpg");
                    modified = true;
                }
            }
        }

        return modified;
    }

    private boolean tryItunesEnrich(QuizSong song, String title, String artist, String country) {
        try {
            String cleanTitle = cleanSongTitle(title);
            String query = (artist != null && !artist.isBlank() && !artist.equalsIgnoreCase("Unknown Artist"))
                    ? (artist + " " + cleanTitle)
                    : cleanTitle;
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String itunesUrl = "https://itunes.apple.com/search?term=" + encoded + "&media=music&entity=song&limit=1"
                    + (country != null ? "&country=" + country : "");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(itunesUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode results = root.path("results");
                if (results.isArray() && results.size() > 0) {
                    JsonNode item = results.get(0);
                    boolean updated = false;

                    String preview = item.path("previewUrl").asText("");
                    if (!preview.isBlank() && (song.getPreviewUrl() == null || song.getPreviewUrl().isBlank())) {
                        song.setPreviewUrl(preview);
                        updated = true;
                    }

                    String art = item.path("artworkUrl100").asText("");
                    if (!art.isBlank() && (song.getArtworkUrl() == null || song.getArtworkUrl().isBlank())) {
                        song.setArtworkUrl(art.replace("100x100bb.jpg", "600x600bb.jpg"));
                        updated = true;
                    }

                    if (song.getGenre() == null || song.getGenre().isBlank() || "General".equalsIgnoreCase(song.getGenre())) {
                        String genre = item.path("primaryGenreName").asText("");
                        if (!genre.isBlank()) {
                            song.setGenre(genre);
                            updated = true;
                        }
                    }
                    return updated;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean hasJapaneseCharacters(String text) {
        if (text == null) return false;
        return text.chars().anyMatch(c ->
                (c >= 0x3040 && c <= 0x309F) || // Hiragana
                (c >= 0x30A0 && c <= 0x30FF) || // Katakana
                (c >= 0x4E00 && c <= 0x9FAF)    // CJK Kanji
        );
    }

    public static boolean isVietnamese(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.matches(".*[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ].*");
    }

    private String searchFirstYoutubeVideoId(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.youtube.com/results?search_query=" + encoded;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "en;q=0.9")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                Matcher m = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").matcher(resp.body());
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public synchronized EnrichmentProgress startBulkEnrichmentAsync() {
        if (enrichmentProgress.isRunning()) {
            return enrichmentProgress;
        }

        List<QuizSong> targets = quizSongRepository.findSongsNeedingEnrichment();
        enrichmentProgress.setRunning(true);
        enrichmentProgress.setTotal(targets.size());
        enrichmentProgress.setProcessed(0);
        enrichmentProgress.setEnriched(0);
        enrichmentProgress.setErrors(0);
        enrichmentProgress.setLastMessage("Bắt đầu xử lý " + targets.size() + " bài hát...");

        if (targets.isEmpty()) {
            enrichmentProgress.setRunning(false);
            enrichmentProgress.setLastMessage("Kho nhạc đã có đầy đủ Audio và Bìa!");
            return enrichmentProgress;
        }

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < targets.size(); i++) {
                    QuizSong song = targets.get(i);
                    enrichmentProgress.setCurrentSong(song.getTitle() + " - " + song.getArtist());
                    try {
                        boolean modified = enrichSongMetadata(song);
                        if (modified) {
                            quizSongRepository.save(song);
                            enrichmentProgress.setEnriched(enrichmentProgress.getEnriched() + 1);
                        }
                    } catch (Exception e) {
                        enrichmentProgress.setErrors(enrichmentProgress.getErrors() + 1);
                    }
                    enrichmentProgress.setProcessed(i + 1);
                    enrichmentProgress.setLastMessage("Đang xử lý: " + (i + 1) + "/" + targets.size() + " bài (Đã bổ sung: " + enrichmentProgress.getEnriched() + ")");
                    try {
                        Thread.sleep(180); // Rate pacing to prevent API throttling
                    } catch (InterruptedException ignored) {}
                }
            } finally {
                enrichmentProgress.setRunning(false);
                enrichmentProgress.setLastMessage("Hoàn thành bổ sung Audio & Bìa cho " + enrichmentProgress.getEnriched() + " bài hát!");
            }
        });

        return enrichmentProgress;
    }

    public QuizSong enrichSingleSong(Long id) {
        QuizSong song = quizSongRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài hát ID: " + id));
        boolean modified = enrichSongMetadata(song);
        if (modified) {
            song = quizSongRepository.save(song);
        }
        return song;
    }

    public EnrichmentProgress getEnrichmentProgress() {
        return enrichmentProgress;
    }

    public long getMissingPreviewCount() {
        return quizSongRepository.countSongsMissingPreview();
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

        int length = title.length();
        if (length > 25) {
            score += 25;
        } else if (length > 15) {
            score += 15;
        } else if (length < 6) {
            score -= 10;
        }

        boolean hasJapanese = title.chars().anyMatch(c ->
                (c >= 0x3040 && c <= 0x309F) || // Hiragana
                (c >= 0x30A0 && c <= 0x30FF) || // Katakana
                (c >= 0x4E00 && c <= 0x9FAF)    // Kanji
        );
        if (hasJapanese) {
            score += 20;
        }

        if (title.contains("(") || title.contains("~") || title.contains(":") || title.contains("-")) {
            score += 10;
        }

        if (genre != null) {
            String g = genre.toLowerCase();
            if (g.contains("anime") || g.contains("pop")) {
                score -= 5;
            } else if (g.contains("classical") || g.contains("jazz") || g.contains("instrumental")) {
                score += 15;
            }
        }

        return Math.max(10, Math.min(95, score));
    }

    public static class RawTrackItem {
        public String title;
        public String artist;
        public String youtubeUrl;
        public String spotifyUrl;
        public String artworkUrl;
        public String previewUrl;
        public String genre;

        public RawTrackItem() {}

        public RawTrackItem(String title, String artist, String youtubeUrl, String spotifyUrl, String artworkUrl, String previewUrl) {
            this.title = title;
            this.artist = artist;
            this.youtubeUrl = youtubeUrl;
            this.spotifyUrl = spotifyUrl;
            this.artworkUrl = artworkUrl;
            this.previewUrl = previewUrl;
        }
    }
}
