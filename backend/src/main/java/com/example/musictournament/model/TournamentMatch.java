package com.example.musictournament.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournament_matches")
public class TournamentMatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;
    @Column(nullable = false)
    private int roundIndex;
    @Column(nullable = false)
    private int position;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "song_a_id")
    private Song songA;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "song_b_id")
    private Song songB;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "winner_id")
    private Song winner;
    @Column(nullable = false)
    private boolean closed = false;
    @Column(length = 20)
    private String groupName; // 'A', 'B', 'PLAYOFF', 'MAIN'
    @Column(name = "is_wildcard", columnDefinition = "boolean default false")
    private boolean isWildcard = false;
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Vote> votes = new ArrayList<>();

    public TournamentMatch() {}
    public TournamentMatch(Tournament tournament, int roundIndex, int position, Song a, Song b) {
        this.tournament=tournament; this.roundIndex=roundIndex; this.position=position;
        this.songA=a; this.songB=b;
    }
    public Long getId(){return id;}
    public Tournament getTournament(){return tournament;}
    public int getRoundIndex(){return roundIndex;}
    public int getPosition(){return position;}
    public Song getSongA(){return songA;}
    public Song getSongB(){return songB;}
    public void setEntrants(Song a, Song b){this.songA=a;this.songB=b;}
    public Song getWinner(){return winner;}
    public void setWinner(Song winner){this.winner=winner;}
    public boolean isClosed(){return closed;}
    public void setClosed(boolean closed){this.closed=closed;}
    public String getGroupName(){return groupName;}
    public void setGroupName(String groupName){this.groupName=groupName;}
    public boolean isWildcard(){return isWildcard;}
    public void setWildcard(boolean wildcard){this.isWildcard=wildcard;}
    public List<Vote> getVotes(){return votes;}
}
