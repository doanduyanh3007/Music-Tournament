package com.example.musictournament.web;

import com.example.musictournament.service.QuizMultiplayerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/quiz/multiplayer")
public class QuizMultiplayerController {

    private final QuizMultiplayerService multiplayerService;

    public QuizMultiplayerController(QuizMultiplayerService multiplayerService) {
        this.multiplayerService = multiplayerService;
    }

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRoom(@RequestBody Map<String, Object> body) {
        String hostPlayerKey = (String) body.get("playerKey");
        String hostDisplayName = (String) body.get("displayName");
        String roomName = (String) body.get("roomName");
        String mode = (String) body.get("mode");
        String targetType = (String) body.get("targetType");
        Integer timeLimit = body.get("timeLimit") != null ? ((Number) body.get("timeLimit")).intValue() : 20;
        Integer questionCount = body.get("questionCount") != null ? ((Number) body.get("questionCount")).intValue() : 10;
        Long poolId = body.get("poolId") != null ? Long.valueOf(body.get("poolId").toString()) : null;
        Integer difficultyTier = body.get("difficultyTier") != null ? ((Number) body.get("difficultyTier")).intValue() : null;

        if (hostPlayerKey == null || hostPlayerKey.isBlank()) {
            throw new IllegalArgumentException("Thiếu playerKey của người tạo phòng");
        }

        return multiplayerService.createRoom(
                hostPlayerKey, hostDisplayName, roomName, mode, targetType, timeLimit, questionCount, poolId, difficultyTier
        );
    }

    @PostMapping("/{roomCode}/join")
    public Map<String, Object> joinRoom(
            @PathVariable String roomCode,
            @RequestBody Map<String, String> body
    ) {
        String playerKey = body.get("playerKey");
        String displayName = body.get("displayName");
        if (playerKey == null || playerKey.isBlank()) {
            throw new IllegalArgumentException("Thiếu playerKey");
        }
        return multiplayerService.joinRoom(roomCode, playerKey, displayName);
    }

    @PostMapping("/{roomCode}/ready")
    public Map<String, Object> toggleReady(
            @PathVariable String roomCode,
            @RequestBody Map<String, String> body
    ) {
        String playerKey = body.get("playerKey");
        return multiplayerService.toggleReady(roomCode, playerKey);
    }

    @PostMapping("/{roomCode}/start")
    public Map<String, Object> startRoom(
            @PathVariable String roomCode,
            @RequestBody Map<String, String> body
    ) {
        String hostPlayerKey = body.get("playerKey");
        return multiplayerService.startRoomGame(roomCode, hostPlayerKey);
    }

    @GetMapping("/{roomCode}/state")
    public Map<String, Object> getRoomState(
            @PathVariable String roomCode,
            @RequestParam(name = "playerKey", defaultValue = "") String playerKey
    ) {
        return multiplayerService.getRoomState(roomCode, playerKey);
    }

    @PostMapping("/{roomCode}/answer")
    public Map<String, Object> submitAnswer(
            @PathVariable String roomCode,
            @RequestBody Map<String, String> body
    ) {
        String playerKey = body.get("playerKey");
        String questionId = body.get("questionId");
        String answer = body.get("answer");
        return multiplayerService.submitAnswer(roomCode, playerKey, questionId, answer);
    }

    @PostMapping("/{roomCode}/hint")
    public Map<String, Object> requestHint(
            @PathVariable String roomCode,
            @RequestBody Map<String, String> body
    ) {
        String playerKey = body.get("playerKey");
        String hintType = body.get("hintType");
        return multiplayerService.requestHint(roomCode, playerKey, hintType);
    }

    @PostMapping("/{roomCode}/next")
    public Map<String, Object> advanceRound(
            @PathVariable String roomCode,
            @RequestBody Map<String, String> body
    ) {
        String hostPlayerKey = body.get("playerKey");
        return multiplayerService.advanceRound(roomCode, hostPlayerKey);
    }
}
