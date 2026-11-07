package com.example.musictournament.analysis.repo;

import com.example.musictournament.analysis.model.SongAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SongAnalysisRepository extends JpaRepository<SongAnalysis, Long> {

    Optional<SongAnalysis> findByQuizSongId(Long quizSongId);

    boolean existsByQuizSongId(Long quizSongId);

    @Query("SELECT sa FROM SongAnalysis sa WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(sa.quizSong.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(sa.quizSong.artist) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(sa.quizSong.romajiTitle) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:status IS NULL OR sa.status = :status)")
    Page<SongAnalysis> search(
            @Param("query") String query,
            @Param("status") SongAnalysis.Status status,
            Pageable pageable
    );
}
