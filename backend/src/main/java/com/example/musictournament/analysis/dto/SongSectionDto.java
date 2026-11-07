package com.example.musictournament.analysis.dto;

import com.example.musictournament.analysis.model.SongSection;

public class SongSectionDto {
    private Long id;
    private Long versionId;
    private SongSection.SectionType sectionType;
    private String sectionLabel;
    private Long startTimeMs;
    private Long endTimeMs;
    private String explanation;
    private Integer emotionalIntensity;
    private Integer sortOrder;

    public SongSectionDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }

    public SongSection.SectionType getSectionType() { return sectionType; }
    public void setSectionType(SongSection.SectionType sectionType) { this.sectionType = sectionType; }

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
}
