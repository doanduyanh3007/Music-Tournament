package com.example.musictournament.analysis.dto;

import com.example.musictournament.analysis.model.SongAnalysis;

public class SongAnalysisUpsertRequest {
    private Long quizSongId;
    private SongAnalysis.Status status;
    private Long currentVersionId;
    private String songBackground;
    private String musicalArrangement;
    private String overallInterpretation;

    public SongAnalysisUpsertRequest() {}

    public Long getQuizSongId() { return quizSongId; }
    public void setQuizSongId(Long quizSongId) { this.quizSongId = quizSongId; }

    public SongAnalysis.Status getStatus() { return status; }
    public void setStatus(SongAnalysis.Status status) { this.status = status; }

    public Long getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }

    public String getSongBackground() { return songBackground; }
    public void setSongBackground(String songBackground) { this.songBackground = songBackground; }

    public String getMusicalArrangement() { return musicalArrangement; }
    public void setMusicalArrangement(String musicalArrangement) { this.musicalArrangement = musicalArrangement; }

    public String getOverallInterpretation() { return overallInterpretation; }
    public void setOverallInterpretation(String overallInterpretation) { this.overallInterpretation = overallInterpretation; }
}
