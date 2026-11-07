package com.example.musictournament.analysis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "song_audio_features", indexes = {
    @Index(name = "idx_song_audio_feature_version", columnList = "version_id")
})
public class SongAudioFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    private Double bpm;

    @Column(length = 50)
    private String detectedKey;

    @Column(length = 20)
    private String timeSignature;

    @Column(columnDefinition = "TEXT")
    private String boundaryPointsJson;

    @Column(columnDefinition = "TEXT")
    private String spectralFeaturesJson;

    @Column(length = 100)
    private String analysisEngine;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SongAudioFeature() {}

    public SongAudioFeature(Long versionId, Double bpm, String detectedKey) {
        this.versionId = versionId;
        this.bpm = bpm;
        this.detectedKey = detectedKey;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }

    public Double getBpm() { return bpm; }
    public void setBpm(Double bpm) { this.bpm = bpm; }

    public String getDetectedKey() { return detectedKey; }
    public void setDetectedKey(String detectedKey) { this.detectedKey = detectedKey; }

    public String getTimeSignature() { return timeSignature; }
    public void setTimeSignature(String timeSignature) { this.timeSignature = timeSignature; }

    public String getBoundaryPointsJson() { return boundaryPointsJson; }
    public void setBoundaryPointsJson(String boundaryPointsJson) { this.boundaryPointsJson = boundaryPointsJson; }

    public String getSpectralFeaturesJson() { return spectralFeaturesJson; }
    public void setSpectralFeaturesJson(String spectralFeaturesJson) { this.spectralFeaturesJson = spectralFeaturesJson; }

    public String getAnalysisEngine() { return analysisEngine; }
    public void setAnalysisEngine(String analysisEngine) { this.analysisEngine = analysisEngine; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
