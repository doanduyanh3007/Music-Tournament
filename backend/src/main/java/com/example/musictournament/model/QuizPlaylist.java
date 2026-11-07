package com.example.musictournament.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_playlists")
public class QuizPlaylist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 50)
    private String source; // "youtube", "spotify", "custom"

    @Column(nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "item_count")
    private Integer itemCount = 0;

    @Column(name = "total_found")
    private Integer totalFound = 0;

    @Column(name = "added_count")
    private Integer addedCount = 0;

    @Column(name = "duplicate_count")
    private Integer duplicateCount = 0;

    @Column(name = "error_count")
    private Integer errorCount = 0;

    @Column(name = "processed_count")
    private Integer processedCount = 0;

    @Column(name = "sync_status", length = 50)
    private String syncStatus = "IDLE"; // "IDLE", "SYNCING", "COMPLETED", "ERROR"

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public QuizPlaylist() {}

    public QuizPlaylist(String title, String source, String sourceUrl) {
        this.title = title;
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.syncStatus = "IDLE";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public Integer getItemCount() { return itemCount != null ? itemCount : 0; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

    public Integer getTotalFound() { return totalFound != null ? totalFound : 0; }
    public void setTotalFound(Integer totalFound) { this.totalFound = totalFound; }

    public Integer getAddedCount() { return addedCount != null ? addedCount : 0; }
    public void setAddedCount(Integer addedCount) { this.addedCount = addedCount; }

    public Integer getDuplicateCount() { return duplicateCount != null ? duplicateCount : 0; }
    public void setDuplicateCount(Integer duplicateCount) { this.duplicateCount = duplicateCount; }

    public Integer getErrorCount() { return errorCount != null ? errorCount : 0; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

    public Integer getProcessedCount() { return processedCount != null ? processedCount : 0; }
    public void setProcessedCount(Integer processedCount) { this.processedCount = processedCount; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
