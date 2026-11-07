package com.example.musictournament;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MusicTournamentApplication {
    public static void main(String[] args) {
        SpringApplication.run(MusicTournamentApplication.class, args);
    }
}
