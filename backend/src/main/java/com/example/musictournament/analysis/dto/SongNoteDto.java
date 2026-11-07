package com.example.musictournament.analysis.dto;

import java.time.LocalDateTime;

public class SongNoteDto {
    private Long id;
    private Long analysisId;
    private String authorName;
    private String noteContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SongNoteDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long analysisId) { this.analysisId = analysisId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getNoteContent() { return noteContent; }
    public void setNoteContent(String noteContent) { this.noteContent = noteContent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
