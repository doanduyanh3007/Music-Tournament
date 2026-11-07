package com.example.musictournament.analysis.repo;

import com.example.musictournament.analysis.model.SongAudioFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SongAudioFeatureRepository extends JpaRepository<SongAudioFeature, Long> {

    Optional<SongAudioFeature> findFirstByVersionIdOrderByCreatedAtDesc(Long versionId);

    void deleteByVersionId(Long versionId);
}
