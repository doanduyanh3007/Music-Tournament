package com.example.musictournament.repo;

import com.example.musictournament.model.QuizPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizPoolRepository extends JpaRepository<QuizPool, Long> {
    Optional<QuizPool> findByCode(String code);
    List<QuizPool> findByActiveTrueAndIsPublicTrueOrderByCreatedAtDesc();
    List<QuizPool> findAllByOrderByCreatedAtDesc();
}
