package com.example.musictournament.analysis.dto;

import com.example.musictournament.analysis.model.SongSource;
import java.time.LocalDateTime;

public class SongSourceDto {
    private Long id;
    private Long analysisId;
    private SongSource.SourceType sourceType;
    private String title;
    private String url;
    private String citedClaim;
    private String author;
    private Boolean verified;
    private LocalDateTime createdAt;

    public SongSourceDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long analysisId) { this.analysisId = analysisId; }

    public SongSource.SourceType getSourceType() { return sourceType; }
    public void setSourceType(SongSource.SourceType sourceType) { this.sourceType = sourceType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getCitedClaim() { return citedClaim; }
    public void setCitedClaim(String citedClaim) { this.citedClaim = citedClaim; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
