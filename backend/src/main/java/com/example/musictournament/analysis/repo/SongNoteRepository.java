package com.example.musictournament.analysis.repo;

import com.example.musictournament.analysis.model.SongNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SongNoteRepository extends JpaRepository<SongNote, Long> {

    List<SongNote> findByAnalysisIdOrderByCreatedAtDesc(Long analysisId);

    void deleteByAnalysisId(Long analysisId);
}
