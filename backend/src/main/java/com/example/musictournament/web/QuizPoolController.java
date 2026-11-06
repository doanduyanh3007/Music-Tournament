package com.example.musictournament.web;

import com.example.musictournament.model.QuizPool;
import com.example.musictournament.service.QuizEngineService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quiz/pools")
public class QuizPoolController {

    private final QuizEngineService quizEngineService;

    public QuizPoolController(QuizEngineService quizEngineService) {
        this.quizEngineService = quizEngineService;
    }

    @GetMapping
    public List<QuizPool> listPools(@RequestParam(required = false, defaultValue = "false") boolean all) {
        return all ? quizEngineService.getAllPools() : quizEngineService.getActivePools();
    }

    @GetMapping("/{id}")
    public QuizPool getPool(@PathVariable Long id) {
        return quizEngineService.getPoolById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Pool ID: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizPool createPool(@RequestBody QuizPool pool) {
        return quizEngineService.createPool(pool);
    }

    @PutMapping("/{id}")
    public QuizPool updatePool(@PathVariable Long id, @RequestBody QuizPool updated) {
        return quizEngineService.updatePool(id, updated);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deletePool(@PathVariable Long id) {
        quizEngineService.deletePool(id);
        return Map.of("success", true, "deletedPoolId", id);
    }

    @PostMapping("/{id}/songs")
    public Map<String, Object> addSongsToPool(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> songIds = body.get("songIds");
        if (songIds == null || songIds.isEmpty()) {
            throw new IllegalArgumentException("Danh sách songIds không được rỗng");
        }
        return quizEngineService.addSongsToPool(id, songIds);
    }

    @DeleteMapping("/{id}/songs/{songId}")
    public Map<String, Object> removeSongFromPool(@PathVariable Long id, @PathVariable Long songId) {
        quizEngineService.removeSongFromPool(id, songId);
        return Map.of("success", true, "removedSongId", songId, "poolId", id);
    }
}
