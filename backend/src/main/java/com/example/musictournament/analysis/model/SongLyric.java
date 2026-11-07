package com.example.musictournament.analysis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "song_lyrics", indexes = {
    @Index(name = "idx_song_lyric_version", columnList = "version_id"),
    @Index(name = "idx_song_lyric_section", columnList = "section_id"),
    @Index(name = "idx_song_lyric_start_time", columnList = "start_time_ms")
})
public class SongLyric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(name = "start_time_ms")
    private Long startTimeMs;

    @Column(name = "end_time_ms")
    private Long endTimeMs;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String japaneseText;

    @Column(columnDefinition = "TEXT")
    private String furiganaReading;

    @Column(columnDefinition = "TEXT")
    private String romajiReading;

    @Column(columnDefinition = "TEXT")
    private String literalTranslation;

    @Column(columnDefinition = "TEXT")
    private String poeticTranslation;

    @Column(columnDefinition = "TEXT")
    private String metaphorExplanation;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SongLyric() {}

    public SongLyric(Long versionId, String japaneseText, Integer sortOrder) {
        this.versionId = versionId;
        this.japaneseText = japaneseText;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }

    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }

    public Long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(Long startTimeMs) { this.startTimeMs = startTimeMs; }

    public Long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(Long endTimeMs) { this.endTimeMs = endTimeMs; }

    public String getJapaneseText() { return japaneseText; }
    public void setJapaneseText(String japaneseText) { this.japaneseText = japaneseText; }

    public String getFuriganaReading() { return furiganaReading; }
    public void setFuriganaReading(String furiganaReading) { this.furiganaReading = furiganaReading; }

    public String getRomajiReading() { return romajiReading; }
    public void setRomajiReading(String romajiReading) { this.romajiReading = romajiReading; }

    public String getLiteralTranslation() { return literalTranslation; }
    public void setLiteralTranslation(String literalTranslation) { this.literalTranslation = literalTranslation; }

    public String getPoeticTranslation() { return poeticTranslation; }
    public void setPoeticTranslation(String poeticTranslation) { this.poeticTranslation = poeticTranslation; }

    public String getMetaphorExplanation() { return metaphorExplanation; }
    public void setMetaphorExplanation(String metaphorExplanation) { this.metaphorExplanation = metaphorExplanation; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
