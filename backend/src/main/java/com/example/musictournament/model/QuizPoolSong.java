package com.example.musictournament.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_pool_songs", indexes = {
    @Index(name = "idx_qps_pool_song", columnList = "pool_id, song_id", unique = true),
    @Index(name = "idx_qps_pool", columnList = "pool_id"),
    @Index(name = "idx_qps_song", columnList = "song_id")
})
public class QuizPoolSong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pool_id", nullable = false)
    private Long poolId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    @Column(nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    public QuizPoolSong() {}

    public QuizPoolSong(Long poolId, Long songId) {
        this.poolId = poolId;
        this.songId = songId;
        this.addedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPoolId() { return poolId; }
    public void setPoolId(Long poolId) { this.poolId = poolId; }

    public Long getSongId() { return songId; }
    public void setSongId(Long songId) { this.songId = songId; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}
