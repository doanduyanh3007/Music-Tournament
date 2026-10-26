package com.example.musictournament.repo;

import com.example.musictournament.model.WildcardSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WildcardRepository extends JpaRepository<WildcardSelection, Long> {
    List<WildcardSelection> findByTournamentIdOrderBySelectedAtAsc(Long tournamentId);
    void deleteByTournamentId(Long tournamentId);
}
