package com.example.musictournament.repo;
import com.example.musictournament.model.Song;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SongRepository extends JpaRepository<Song, Long> {}
