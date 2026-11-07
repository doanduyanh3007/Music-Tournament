package com.example.musictournament.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_songs", indexes = {
    @Index(name = "idx_quiz_song_title", columnList = "title"),
    @Index(name = "idx_quiz_song_artist", columnList = "artist"),
    @Index(name = "idx_quiz_song_genre", columnList = "genre"),
    @Index(name = "idx_quiz_song_difficulty", columnList = "difficulty")
})
public class QuizSong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String artist;

    @Column(length = 255)
    private String romajiTitle;

    @Column(columnDefinition = "TEXT")
    private String aliases; // JSON array string or comma separated

    @Column(length = 500)
    private String artworkUrl;

    @Column(length = 500)
    private String previewUrl; // Direct 30s MP3 audio URL

    @Column(length = 500)
    private String youtubeUrl;

    @Column(length = 500)
    private String spotifyUrl;

    @Column(length = 100)
    private String genre = "General";

    @Column(nullable = false)
    private Integer difficulty = 50; // 0-100 (0-20 Easy, 21-40 Normal, 41-65 Hard, 66-85 Expert, 86-100 Master)

    @Column(nullable = false)
    private Boolean difficultyManuallySet = false;

    @Column(nullable = false)
    private Integer popularityScore = 50;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public QuizSong() {}

    public QuizSong(String title, String artist, String artworkUrl, String previewUrl, String youtubeUrl, String spotifyUrl, String genre, Integer difficulty) {
        this.title = title;
        this.artist = artist != null && !artist.isBlank() ? artist : "Unknown Artist";
        this.artworkUrl = artworkUrl;
        this.previewUrl = previewUrl;
        this.youtubeUrl = youtubeUrl;
        this.spotifyUrl = spotifyUrl;
        if (genre != null && !genre.isBlank()) this.genre = genre;
        if (difficulty != null) this.difficulty = Math.max(0, Math.min(100, difficulty));
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getRomajiTitle() { return romajiTitle; }
    public void setRomajiTitle(String romajiTitle) { this.romajiTitle = romajiTitle; }

    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }

    public String getArtworkUrl() { return artworkUrl; }
    public void setArtworkUrl(String artworkUrl) { this.artworkUrl = artworkUrl; }

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public String getYoutubeUrl() { return youtubeUrl; }
    public void setYoutubeUrl(String youtubeUrl) { this.youtubeUrl = youtubeUrl; }

    public String getSpotifyUrl() { return spotifyUrl; }
    public void setSpotifyUrl(String spotifyUrl) { this.spotifyUrl = spotifyUrl; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) {
        if (difficulty != null) this.difficulty = Math.max(0, Math.min(100, difficulty));
    }

    public Boolean getDifficultyManuallySet() { return difficultyManuallySet; }
    public void setDifficultyManuallySet(Boolean difficultyManuallySet) { this.difficultyManuallySet = difficultyManuallySet; }

    public Integer getPopularityScore() { return popularityScore; }
    public void setPopularityScore(Integer popularityScore) { this.popularityScore = popularityScore; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getDifficultyTier() {
        int d = difficulty != null ? difficulty : 50;
        if (d <= 20) return "Easy";
        if (d <= 40) return "Normal";
        if (d <= 65) return "Hard";
        if (d <= 85) return "Expert";
        return "Master";
    }
}
