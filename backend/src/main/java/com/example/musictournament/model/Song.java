package com.example.musictournament.model;

import jakarta.persistence.*;

@Entity
@Table(name = "songs")
public class Song {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 200)
    private String artist;
    @Column(length = 500)
    private String link;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    public Song() {}
    public Song(String title, String artist, String link, Tournament tournament) {
        this.title = title; this.artist = artist; this.link = link; this.tournament = tournament;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getLink() { return link; }
    public Tournament getTournament() { return tournament; }
}
