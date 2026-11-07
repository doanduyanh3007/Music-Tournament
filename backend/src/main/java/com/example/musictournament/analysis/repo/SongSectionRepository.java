package com.example.musictournament.analysis.repo;

import com.example.musictournament.analysis.model.SongSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SongSectionRepository extends JpaRepository<SongSection, Long> {

    List<SongSection> findByVersionIdOrderBySortOrderAscStartTimeMsAsc(Long versionId);

    void deleteByVersionId(Long versionId);
}
