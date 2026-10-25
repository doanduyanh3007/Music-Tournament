package com.example.musictournament.repo;
import com.example.musictournament.model.TournamentMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MatchRepository extends JpaRepository<TournamentMatch, Long> {
    List<TournamentMatch> findByTournamentIdOrderByRoundIndexAscPositionAsc(Long tournamentId);
    Optional<TournamentMatch> findByIdAndTournamentId(Long id, Long tournamentId);
}
