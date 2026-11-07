package com.example.musictournament.analysis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "song_sources", indexes = {
    @Index(name = "idx_song_source_analysis", columnList = "analysis_id")
})
public class SongSource {

    public enum SourceType {
        VERIFIED_OFFICIAL,
        MUSIC_ANALYSIS,
        INTERPRETATION,
        UNVERIFIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SourceType sourceType = SourceType.UNVERIFIED;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 500)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String citedClaim;

    @Column(length = 150)
    private String author;

    @Column(nullable = false)
    private Boolean verified = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SongSource() {}

    public SongSource(Long analysisId, SourceType sourceType, String title, String url, String citedClaim) {
        this.analysisId = analysisId;
        this.sourceType = sourceType != null ? sourceType : SourceType.UNVERIFIED;
        this.title = title;
        this.url = url;
        this.citedClaim = citedClaim;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAnalysisId() { return analysisId; }
    public void setAnalysisId(Long analysisId) { this.analysisId = analysisId; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

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
