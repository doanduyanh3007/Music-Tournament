package com.example.musictournament.analysis.repo;

import com.example.musictournament.analysis.model.SongLyric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SongLyricRepository extends JpaRepository<SongLyric, Long> {

    List<SongLyric> findByVersionIdOrderBySortOrderAscStartTimeMsAsc(Long versionId);

    List<SongLyric> findBySectionIdOrderBySortOrderAscStartTimeMsAsc(Long sectionId);

    void deleteByVersionId(Long versionId);
}
