package com.example.musictournament.analysis.repo;

import com.example.musictournament.analysis.model.SongSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SongSourceRepository extends JpaRepository<SongSource, Long> {

    List<SongSource> findByAnalysisIdOrderByCreatedAtDesc(Long analysisId);

    void deleteByAnalysisId(Long analysisId);
}
