package com.example.musictournament.analysis.dto;

import java.util.List;

public record SongChatResponse(
        String reply,
        String category, // "FACT", "ANALYSIS", "INFERENCE"
        List<String> suggestedQuestions
) {}
