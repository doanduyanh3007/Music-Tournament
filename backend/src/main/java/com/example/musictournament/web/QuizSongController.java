package com.example.musictournament.web;

import com.example.musictournament.model.QuizSong;
import com.example.musictournament.service.QuizMusicManagerService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quiz/songs")
public class QuizSongController {

    private final QuizMusicManagerService musicManagerService;

    public QuizSongController(QuizMusicManagerService musicManagerService) {
        this.musicManagerService = musicManagerService;
    }

    @GetMapping
    public Map<String, Object> listSongs(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer minDiff,
            @RequestParam(required = false) Integer maxDiff,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<QuizSong> result = musicManagerService.getSongs(query, genre, minDiff, maxDiff, activeOnly, sortBy, sortDir, page, size);
        return Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "isLast", result.isLast()
        );
    }

    @GetMapping("/genres")
    public List<String> listGenres() {
        return musicManagerService.getAllGenres();
    }

    @GetMapping("/{id}")
    public QuizSong getSong(@PathVariable Long id) {
        return musicManagerService.getSongById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài hát ID: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizSong createSong(@RequestBody QuizSong song) {
        return musicManagerService.saveSong(song);
    }

    @PutMapping("/{id}")
    public QuizSong updateSong(@PathVariable Long id, @RequestBody QuizSong updated) {
        QuizSong existing = musicManagerService.getSongById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài hát ID: " + id));

        existing.setTitle(updated.getTitle());
        existing.setArtist(updated.getArtist());
        existing.setArtworkUrl(updated.getArtworkUrl());
        existing.setPreviewUrl(updated.getPreviewUrl());
        existing.setYoutubeUrl(updated.getYoutubeUrl());
        existing.setSpotifyUrl(updated.getSpotifyUrl());
        existing.setGenre(updated.getGenre());
        existing.setAliases(updated.getAliases());

        if (updated.getDifficulty() != null) {
            existing.setDifficulty(updated.getDifficulty());
            existing.setDifficultyManuallySet(true); // Flag manual override
        }
        if (updated.getActive() != null) {
            existing.setActive(updated.getActive());
        }

        return musicManagerService.saveSong(existing);
    }

    @PatchMapping("/{id}/toggle-active")
    public Map<String, Object> toggleActive(@PathVariable Long id) {
        int res = musicManagerService.toggleSongActive(id);
        if (res == -1) {
            throw new IllegalArgumentException("Không tìm thấy bài hát ID: " + id);
        }
        return Map.of("id", id, "active", res == 1);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteSong(@PathVariable Long id) {
        musicManagerService.deleteSong(id);
        return Map.of("success", true, "deletedId", id);
    }

    @PostMapping("/import-from-tournaments")
    public Map<String, Object> importFromTournaments() {
        return musicManagerService.importSongsFromTournaments();
    }

    @PostMapping("/batch")
    public Map<String, Object> batchImport(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> songs = (List<Map<String, Object>>) body.get("songs");
        if (songs == null || songs.isEmpty()) {
            return Map.of("totalSubmitted", 0, "addedCount", 0, "duplicateCount", 0, "errorCount", 0, "poolLinkedCount", 0);
        }

        Long quizPoolId = null;
        if (body.get("quizPoolId") != null) {
            quizPoolId = Long.valueOf(body.get("quizPoolId").toString());
        }

        boolean addToQuizLibrary = body.get("addToQuizLibrary") == null || Boolean.parseBoolean(body.get("addToQuizLibrary").toString());
        boolean enrichMetadata = body.get("enrichMetadata") == null || Boolean.parseBoolean(body.get("enrichMetadata").toString());

        return musicManagerService.batchImportSongs(songs, quizPoolId, addToQuizLibrary, enrichMetadata);
    }

    @GetMapping("/enrich")
    public Map<String, Object> enrichMetadata(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String link
    ) {
        return musicManagerService.enrichMetadata(title, artist, link);
    }

    @PostMapping("/deduplicate")
    public Map<String, Object> deduplicateSongs() {
        return musicManagerService.deduplicateAllSongs();
    }

    @PostMapping("/enrich-all")
    public QuizMusicManagerService.EnrichmentProgress enrichAllSongs() {
        return musicManagerService.startBulkEnrichmentAsync();
    }

    @GetMapping("/enrich-status")
    public Map<String, Object> getEnrichStatus() {
        QuizMusicManagerService.EnrichmentProgress progress = musicManagerService.getEnrichmentProgress();
        long missing = musicManagerService.getMissingPreviewCount();
        return Map.of(
                "isRunning", progress.isRunning(),
                "total", progress.getTotal(),
                "processed", progress.getProcessed(),
                "enriched", progress.getEnriched(),
                "errors", progress.getErrors(),
                "currentSong", progress.getCurrentSong() != null ? progress.getCurrentSong() : "",
                "lastMessage", progress.getLastMessage() != null ? progress.getLastMessage() : "",
                "missingPreviewCount", missing
        );
    }

    @PostMapping("/{id}/enrich")
    public QuizSong enrichSingleSong(@PathVariable Long id) {
        return musicManagerService.enrichSingleSong(id);
    }
}
