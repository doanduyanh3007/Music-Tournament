package com.example.musictournament.analysis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "song_sections", indexes = {
    @Index(name = "idx_song_section_version", columnList = "version_id"),
    @Index(name = "idx_song_section_start_time", columnList = "start_time_ms")
})
public class SongSection {

    public enum SectionType {
        INTRO,
        VERSE,
        PRE_CHORUS,
        CHORUS,
        BRIDGE,
        FINAL_CHORUS,
        OUTRO,
        SOLO,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SectionType sectionType = SectionType.VERSE;

    @Column(nullable = false, length = 100)
    private String sectionLabel;

    @Column(name = "start_time_ms", nullable = false)
    private Long startTimeMs;

    @Column(name = "end_time_ms", nullable = false)
    private Long endTimeMs;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false)
    private Integer emotionalIntensity = 5;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SongSection() {}

    public SongSection(Long versionId, SectionType sectionType, String sectionLabel, Long startTimeMs, Long endTimeMs) {
        this.versionId = versionId;
        this.sectionType = sectionType != null ? sectionType : SectionType.VERSE;
        this.sectionLabel = sectionLabel != null ? sectionLabel : this.sectionType.name();
        this.startTimeMs = startTimeMs != null ? startTimeMs : 0L;
        this.endTimeMs = endTimeMs != null ? endTimeMs : 0L;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }

    public SectionType getSectionType() { return sectionType; }
    public void setSectionType(SectionType sectionType) { this.sectionType = sectionType; }

    public String getSectionLabel() { return sectionLabel; }
    public void setSectionLabel(String sectionLabel) { this.sectionLabel = sectionLabel; }

    public Long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(Long startTimeMs) { this.startTimeMs = startTimeMs; }

    public Long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(Long endTimeMs) { this.endTimeMs = endTimeMs; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public Integer getEmotionalIntensity() { return emotionalIntensity; }
    public void setEmotionalIntensity(Integer emotionalIntensity) { this.emotionalIntensity = emotionalIntensity; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
