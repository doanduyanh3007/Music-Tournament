package com.example.musictournament.analysis.dto;

import com.example.musictournament.analysis.model.SongAnalysis;
import java.time.LocalDateTime;

public class SongAnalysisSummaryDto {
    private Long id;
    private Long quizSongId;
    private String title;
    private String artist;
    private String romajiTitle;
    private String artworkUrl;
    private String genre;
    private SongAnalysis.Status status;
    private Long currentVersionId;
    private int versionCount;
    private LocalDateTime updatedAt;

    public SongAnalysisSummaryDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getQuizSongId() { return quizSongId; }
    public void setQuizSongId(Long quizSongId) { this.quizSongId = quizSongId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getRomajiTitle() { return romajiTitle; }
    public void setRomajiTitle(String romajiTitle) { this.romajiTitle = romajiTitle; }

    public String getArtworkUrl() { return artworkUrl; }
    public void setArtworkUrl(String artworkUrl) { this.artworkUrl = artworkUrl; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public SongAnalysis.Status getStatus() { return status; }
    public void setStatus(SongAnalysis.Status status) { this.status = status; }

    public Long getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }

    public int getVersionCount() { return versionCount; }
    public void setVersionCount(int versionCount) { this.versionCount = versionCount; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
