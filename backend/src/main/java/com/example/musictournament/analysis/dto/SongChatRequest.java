package com.example.musictournament.analysis.dto;

import java.util.List;

public record SongChatRequest(
        String message,
        List<ChatMessage> history,
        String contextModule,
        String contextDetail
) {
    public record ChatMessage(
            String role,
            String content
    ) {}
}
