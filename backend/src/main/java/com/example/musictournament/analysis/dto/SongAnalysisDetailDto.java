package com.example.musictournament.analysis.dto;

import com.example.musictournament.analysis.model.SongAnalysis;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SongAnalysisDetailDto {
    private Long id;
    private Long quizSongId;
    private String title;
    private String artist;
    private String romajiTitle;
    private String artworkUrl;
    private String previewUrl;
    private String spotifyUrl;
    private String youtubeUrl;
    private String genre;

    private SongAnalysis.Status status;
    private Long currentVersionId;
    private String songBackground;
    private String musicalArrangement;
    private String overallInterpretation;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<SongVersionDto> versions = new ArrayList<>();
    private SongVersionDto currentVersion;
    private List<SongSectionDto> sections = new ArrayList<>();
    private List<SongLyricDto> lyrics = new ArrayList<>();
    private List<SongSourceDto> sources = new ArrayList<>();
    private List<SongNoteDto> notes = new ArrayList<>();
    private SongAudioFeatureDto audioFeature;

    public SongAnalysisDetailDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getQuizSongId() { return quizSongId; }
    public void setQuizSongId(Long quizSongId) { this.quizSongId = quizSongId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getRomajiTitle() { return romajiTitle; }
    public void setRomajiTitle(String romajiTitle) { this.romajiTitle = romajiTitle; }

    public String getArtworkUrl() { return artworkUrl; }
    public void setArtworkUrl(String artworkUrl) { this.artworkUrl = artworkUrl; }

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public String getSpotifyUrl() { return spotifyUrl; }
    public void setSpotifyUrl(String spotifyUrl) { this.spotifyUrl = spotifyUrl; }

    public String getYoutubeUrl() { return youtubeUrl; }
    public void setYoutubeUrl(String youtubeUrl) { this.youtubeUrl = youtubeUrl; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public SongAnalysis.Status getStatus() { return status; }
    public void setStatus(SongAnalysis.Status status) { this.status = status; }

    public Long getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }

    public String getSongBackground() { return songBackground; }
    public void setSongBackground(String songBackground) { this.songBackground = songBackground; }

    public String getMusicalArrangement() { return musicalArrangement; }
    public void setMusicalArrangement(String musicalArrangement) { this.musicalArrangement = musicalArrangement; }

    public String getOverallInterpretation() { return overallInterpretation; }
    public void setOverallInterpretation(String overallInterpretation) { this.overallInterpretation = overallInterpretation; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<SongVersionDto> getVersions() { return versions; }
    public void setVersions(List<SongVersionDto> versions) { this.versions = versions; }

    public SongVersionDto getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(SongVersionDto currentVersion) { this.currentVersion = currentVersion; }

    public List<SongSectionDto> getSections() { return sections; }
    public void setSections(List<SongSectionDto> sections) { this.sections = sections; }

    public List<SongLyricDto> getLyrics() { return lyrics; }
    public void setLyrics(List<SongLyricDto> lyrics) { this.lyrics = lyrics; }

    public List<SongSourceDto> getSources() { return sources; }
    public void setSources(List<SongSourceDto> sources) { this.sources = sources; }

    public List<SongNoteDto> getNotes() { return notes; }
    public void setNotes(List<SongNoteDto> notes) { this.notes = notes; }

    public SongAudioFeatureDto getAudioFeature() { return audioFeature; }
    public void setAudioFeature(SongAudioFeatureDto audioFeature) { this.audioFeature = audioFeature; }
}
