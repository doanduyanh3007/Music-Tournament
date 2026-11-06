package com.example.musictournament.repo;

import com.example.musictournament.model.QuizPlaylist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizPlaylistRepository extends JpaRepository<QuizPlaylist, Long> {
    Optional<QuizPlaylist> findFirstBySourceUrl(String sourceUrl);
    List<QuizPlaylist> findAllByOrderByCreatedAtDesc();
}
