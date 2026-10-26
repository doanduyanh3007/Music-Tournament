package com.example.musictournament.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "votes", uniqueConstraints = @UniqueConstraint(name = "uk_match_voter", columnNames = {"match_id", "voter_key"}))
public class Vote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id")
    private TournamentMatch match;
    @Column(name = "voter_key", nullable = false, length = 100)
    private String voterKey;
    @Column(length = 50)
    private String displayName;
    @Column(length = 1000)
    private String reason;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id")
    private Song song;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Vote() {}
    public Vote(TournamentMatch match, String voterKey, Song song, String displayName, String reason) {
        this.match=match; this.voterKey=voterKey; this.song=song;
        this.displayName=displayName; this.reason=reason;
    }
    public Long getId(){return id;}
    public TournamentMatch getMatch(){return match;}
    public String getVoterKey(){return voterKey;}
    public String getDisplayName(){return displayName;}
    public String getReason(){return reason;}
    public void changeSelection(Song song, String displayName, String reason) {
        this.song = song;
        this.displayName = displayName;
        this.reason = reason;
    }
    public Song getSong(){return song;}
    public Instant getCreatedAt(){return createdAt;}
}
