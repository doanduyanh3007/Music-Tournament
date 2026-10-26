package com.example.musictournament.repo;
import com.example.musictournament.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
public interface VoteRepository extends JpaRepository<Vote, Long> {
    boolean existsByMatchIdAndVoterKey(Long matchId, String voterKey);
    long countByMatchIdAndSongId(Long matchId, Long songId);
    Optional<Vote> findByMatchIdAndVoterKey(Long matchId, String voterKey);
    @EntityGraph(attributePaths = {"song"})
    Page<Vote> findByMatchIdOrderByCreatedAtDescIdDesc(Long matchId, Pageable pageable);
    @EntityGraph(attributePaths = {"match", "match.winner"})
    List<Vote> findByMatchTournamentIdOrderByCreatedAtAscIdAsc(Long tournamentId);
}
