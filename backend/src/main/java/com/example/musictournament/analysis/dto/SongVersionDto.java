package com.example.musictournament.analysis.dto;

import com.example.musictournament.analysis.model.SongVersion;
import java.time.LocalDateTime;

public class SongVersionDto {
    private Long id;
    private Long analysisId;
    private SongVersion.VersionType versionType;
    private String versionName;
    private Long durationMs;
    private String spotifyUrl;
    private String youtubeUrl;
    private String audioFilePath;
    private Boolean isPrimary;
    private LocalDateTime createdAt;

    public SongVersionDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long analysisId) { this.analysisId = analysisId; }

    public SongVersion.VersionType getVersionType() { return versionType; }
    public void setVersionType(SongVersion.VersionType versionType) { this.versionType = versionType; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getSpotifyUrl() { return spotifyUrl; }
    public void setSpotifyUrl(String spotifyUrl) { this.spotifyUrl = spotifyUrl; }

    public String getYoutubeUrl() { return youtubeUrl; }
    public void setYoutubeUrl(String youtubeUrl) { this.youtubeUrl = youtubeUrl; }

    public String getAudioFilePath() { return audioFilePath; }
    public void setAudioFilePath(String audioFilePath) { this.audioFilePath = audioFilePath; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
