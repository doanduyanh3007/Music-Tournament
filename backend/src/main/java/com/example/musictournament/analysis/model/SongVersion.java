package com.example.musictournament.analysis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "song_versions", indexes = {
    @Index(name = "idx_song_version_analysis", columnList = "analysis_id")
})
public class SongVersion {

    public enum VersionType {
        ORIGINAL,
        ANIME_SIZE,
        LIVE,
        ACOUSTIC,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VersionType versionType = VersionType.ORIGINAL;

    @Column(nullable = false, length = 200)
    private String versionName;

    @Column(nullable = false)
    private Long durationMs = 0L;

    @Column(length = 500)
    private String spotifyUrl;

    @Column(length = 500)
    private String youtubeUrl;

    @Column(length = 500)
    private String audioFilePath;

    @Column(nullable = false)
    private Boolean isPrimary = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SongVersion() {}

    public SongVersion(Long analysisId, VersionType versionType, String versionName, Long durationMs) {
        this.analysisId = analysisId;
        this.versionType = versionType != null ? versionType : VersionType.ORIGINAL;
        this.versionName = versionName;
        this.durationMs = durationMs != null ? durationMs : 0L;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long analysisId) { this.analysisId = analysisId; }

    public VersionType getVersionType() { return versionType; }
    public void setVersionType(VersionType versionType) { this.versionType = versionType; }

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
