package com.example.musictournament.repo;

import com.example.musictournament.model.QuizPoolSong;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizPoolSongRepository extends JpaRepository<QuizPoolSong, Long> {

    List<QuizPoolSong> findByPoolId(Long poolId);

    Optional<QuizPoolSong> findByPoolIdAndSongId(Long poolId, Long songId);

    long countByPoolId(Long poolId);

    @Modifying
    @Query("DELETE FROM QuizPoolSong qps WHERE qps.poolId = :poolId AND qps.songId = :songId")
    void deleteByPoolIdAndSongId(@Param("poolId") Long poolId, @Param("songId") Long songId);

    List<QuizPoolSong> findBySongId(Long songId);

    @Modifying
    @Query("DELETE FROM QuizPoolSong qps WHERE qps.songId = :songId")
    void deleteBySongId(@Param("songId") Long songId);

    @Modifying
    @Query("DELETE FROM QuizPoolSong qps WHERE qps.poolId = :poolId")
    void deleteByPoolId(@Param("poolId") Long poolId);

    @Query(value = "SELECT s.* FROM quiz_songs s " +
                   "JOIN quiz_pool_songs ps ON s.id = ps.song_id " +
                   "WHERE ps.pool_id = :poolId AND s.active = true " +
                   "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<com.example.musictournament.model.QuizSong> findRandomSongsInPool(@Param("poolId") Long poolId, @Param("limit") int limit);
}
