package com.example.musictournament.analysis.dto;

import java.time.LocalDateTime;

public class SongAudioFeatureDto {
    private Long id;
    private Long versionId;
    private Double bpm;
    private String detectedKey;
    private String timeSignature;
    private String boundaryPointsJson;
    private String spectralFeaturesJson;
    private String analysisEngine;
    private LocalDateTime createdAt;

    public SongAudioFeatureDto() {}

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
