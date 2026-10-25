package com.example.musictournament.repo;
import com.example.musictournament.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface VoteRepository extends JpaRepository<Vote, Long> {
    boolean existsByMatchIdAndVoterKey(Long matchId, String voterKey);
    long countByMatchIdAndSongId(Long matchId, Long songId);
    Optional<Vote> findByMatchIdAndVoterKey(Long matchId, String voterKey);
}
