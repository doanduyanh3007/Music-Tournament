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

    @GetMapping("/{id}")
    public QuizPlaylist getPlaylist(@PathVariable Long id) {
        return musicManagerService.getPlaylistById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy playlist ID: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizPlaylist addPlaylist(@RequestBody(required = false) Map<String, String> body) {
        if (body == null || body.get("url") == null || body.get("url").trim().isEmpty()) {
            throw new IllegalArgumentException("Đường dẫn playlist (URL) không được để trống");
        }
        String url = body.get("url").trim();
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
