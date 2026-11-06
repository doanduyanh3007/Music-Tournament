package com.example.musictournament.web;

import com.example.musictournament.service.QuizEngineService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/quiz/game")
public class QuizGameController {

    private final QuizEngineService quizEngineService;

    public QuizGameController(QuizEngineService quizEngineService) {
        this.quizEngineService = quizEngineService;
    }

    @PostMapping("/start")
    public Map<String, Object> startGame(@RequestBody(required = false) Map<String, Object> options) {
        String mode = options != null && options.get("mode") != null ? options.get("mode").toString() : "TEN_QUESTIONS";
        String targetType = options != null && options.get("targetType") != null ? options.get("targetType").toString() : "TITLE";
        Long poolId = options != null && options.get("poolId") != null ? Long.valueOf(options.get("poolId").toString()) : null;
        String genre = options != null && options.get("genre") != null ? options.get("genre").toString() : null;
        Integer difficultyTier = options != null && options.get("difficultyTier") != null ? Integer.valueOf(options.get("difficultyTier").toString()) : null;
        Integer questionCount = options != null && options.get("questionCount") != null ? Integer.valueOf(options.get("questionCount").toString()) : 10;
        Integer timeLimit = options != null && options.get("timeLimit") != null ? Integer.valueOf(options.get("timeLimit").toString()) : 20;

        return quizEngineService.startSession(mode, targetType, poolId, genre, difficultyTier, questionCount, timeLimit);
    }

    @GetMapping("/{sessionId}/next")
    public Map<String, Object> nextQuestion(@PathVariable String sessionId) {
        return quizEngineService.getNextQuestion(sessionId);
    }

    @PostMapping("/{sessionId}/answer")
    public Map<String, Object> submitAnswer(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body
    ) {
        String questionId = (String) body.get("questionId");
        String answer = (String) body.get("answer");
        Integer timeTakenMs = body.get("timeTakenMs") != null ? ((Number) body.get("timeTakenMs")).intValue() : null;

        return quizEngineService.submitAnswer(sessionId, questionId, answer, timeTakenMs);
    }

    @PostMapping("/{sessionId}/hint")
    public Map<String, Object> requestHint(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body
    ) {
        String questionId = body.get("questionId");
        String hintType = body.get("hintType");
        return quizEngineService.requestHint(sessionId, questionId, hintType);
    }

    @PostMapping("/{sessionId}/skip")
    public Map<String, Object> skipQuestion(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body
    ) {
        String questionId = body.get("questionId");
        return quizEngineService.skipQuestion(sessionId, questionId);
    }
}
