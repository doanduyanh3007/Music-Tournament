package com.example.musictournament.analysis.dto;

public class SongLyricDto {
    private Long id;
    private Long versionId;
    private Long sectionId;
    private Long startTimeMs;
    private Long endTimeMs;
    private String japaneseText;
    private String furiganaReading;
    private String romajiReading;
    private String literalTranslation;
    private String poeticTranslation;
    private String metaphorExplanation;
    private Integer sortOrder;

    public SongLyricDto() {}

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
}
