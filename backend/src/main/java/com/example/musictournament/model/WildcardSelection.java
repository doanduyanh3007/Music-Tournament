package com.example.musictournament.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "wildcard_selections")
public class WildcardSelection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id")
    private Song song;

    @Column(nullable = false)
    private int fromRoundIndex;

    @Column(nullable = false)
    private long votesReceived;

    @Column(nullable = false)
    private long totalMatchVotes;

    @Column(nullable = false)
    private double voteRatioPercent;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false)
    private Instant selectedAt = Instant.now();

    public WildcardSelection() {}

    public WildcardSelection(Tournament tournament, Song song, int fromRoundIndex, long votesReceived, long totalMatchVotes, double voteRatioPercent, String reason) {
        this.tournament = tournament;
        this.song = song;
        this.fromRoundIndex = fromRoundIndex;
        this.votesReceived = votesReceived;
        this.totalMatchVotes = totalMatchVotes;
        this.voteRatioPercent = voteRatioPercent;
        this.reason = reason;
        this.selectedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Tournament getTournament() { return tournament; }
    public Song getSong() { return song; }
    public int getFromRoundIndex() { return fromRoundIndex; }
    public long getVotesReceived() { return votesReceived; }
    public long getTotalMatchVotes() { return totalMatchVotes; }
    public double getVoteRatioPercent() { return voteRatioPercent; }
    public String getReason() { return reason; }
    public Instant getSelectedAt() { return selectedAt; }
}
