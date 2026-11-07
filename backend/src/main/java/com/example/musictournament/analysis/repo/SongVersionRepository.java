package com.example.musictournament.analysis.repo;

import com.example.musictournament.analysis.model.SongVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SongVersionRepository extends JpaRepository<SongVersion, Long> {

    List<SongVersion> findByAnalysisIdOrderByCreatedAtAsc(Long analysisId);

    Optional<SongVersion> findFirstByAnalysisIdAndIsPrimaryTrue(Long analysisId);

    long countByAnalysisId(Long analysisId);
}
