package com.example.musictournament.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_pools", indexes = {
    @Index(name = "idx_quiz_pool_code", columnList = "code", unique = true)
})
public class QuizPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code; // e.g. "anime-mystery", "j-rock", "universe"

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String coverUrl;

    @Column(nullable = false)
    private Boolean isPublic = true;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Integer songCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public QuizPool() {}

    public QuizPool(String code, String name, String description, String coverUrl) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.coverUrl = coverUrl;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Integer getSongCount() { return songCount; }
    public void setSongCount(Integer songCount) { this.songCount = songCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
