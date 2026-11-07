package com.example.musictournament.analysis.model;

import com.example.musictournament.model.QuizSong;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "song_analysis", indexes = {
    @Index(name = "idx_song_analysis_quiz_song", columnList = "quiz_song_id")
})
public class SongAnalysis {

    public enum Status {
        DRAFT,
        ANALYZING,
        COMPLETED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "quiz_song_id", nullable = false)
    private QuizSong quizSong;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.DRAFT;

    private Long currentVersionId;

    @Column(columnDefinition = "TEXT")
    private String songBackground;

    @Column(columnDefinition = "TEXT")
    private String musicalArrangement;

    @Column(columnDefinition = "TEXT")
    private String overallInterpretation;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public SongAnalysis() {}

    public SongAnalysis(QuizSong quizSong) {
        this.quizSong = quizSong;
        this.status = Status.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public QuizSong getQuizSong() { return quizSong; }
    public void setQuizSong(QuizSong quizSong) { this.quizSong = quizSong; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

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
}
