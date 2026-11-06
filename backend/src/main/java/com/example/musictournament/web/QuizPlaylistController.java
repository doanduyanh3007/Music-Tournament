package com.example.musictournament.web;

import com.example.musictournament.model.QuizPlaylist;
import com.example.musictournament.service.QuizMusicManagerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quiz/playlists")
public class QuizPlaylistController {

    private final QuizMusicManagerService musicManagerService;

    public QuizPlaylistController(QuizMusicManagerService musicManagerService) {
        this.musicManagerService = musicManagerService;
    }

    @GetMapping
    public List<QuizPlaylist> listPlaylists() {
        return musicManagerService.getPlaylists();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizPlaylist addPlaylist(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Đường dẫn playlist (URL) không được để trống");
        }
        String title = body.get("title");
        return musicManagerService.addPlaylist(url, title);
    }

    @PostMapping("/{id}/sync")
    public Map<String, Object> triggerSync(@PathVariable Long id) {
        musicManagerService.triggerSyncAsync(id);
        return Map.of("success", true, "message", "Đã bắt đầu đồng bộ playlist trong background", "playlistId", id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deletePlaylist(@PathVariable Long id) {
        musicManagerService.deletePlaylist(id);
        return Map.of("success", true, "deletedId", id);
    }
}
