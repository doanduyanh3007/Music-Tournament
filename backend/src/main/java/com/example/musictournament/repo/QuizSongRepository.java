package com.example.musictournament.repo;

import com.example.musictournament.model.QuizSong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizSongRepository extends JpaRepository<QuizSong, Long> {

    Optional<QuizSong> findFirstByTitleIgnoreCaseAndArtistIgnoreCase(String title, String artist);

    List<QuizSong> findByTitleIgnoreCase(String title);

    Optional<QuizSong> findFirstByYoutubeUrl(String youtubeUrl);

    Optional<QuizSong> findFirstBySpotifyUrl(String spotifyUrl);

    @Query("SELECT s FROM QuizSong s WHERE " +
           "(:query IS NULL OR :query = '' OR LOWER(s.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(s.artist) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:genre IS NULL OR :genre = '' OR LOWER(s.genre) = LOWER(:genre)) AND " +
           "(:minDiff IS NULL OR s.difficulty >= :minDiff) AND " +
           "(:maxDiff IS NULL OR s.difficulty <= :maxDiff) AND " +
           "(:activeOnly IS NULL OR :activeOnly = false OR s.active = true)")
    Page<QuizSong> searchSongs(
            @Param("query") String query,
            @Param("genre") String genre,
            @Param("minDiff") Integer minDiff,
            @Param("maxDiff") Integer maxDiff,
            @Param("activeOnly") Boolean activeOnly,
            Pageable pageable
    );

    @Query(value = "SELECT DISTINCT genre FROM quiz_songs WHERE genre IS NOT NULL AND genre <> '' ORDER BY genre ASC", nativeQuery = true)
    List<String> findDistinctGenres();

    @Query(value = "SELECT * FROM quiz_songs WHERE active = true ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<QuizSong> findRandomSongs(@Param("limit") int limit);

    @Query(value = "SELECT * FROM quiz_songs WHERE active = true AND difficulty BETWEEN :minDiff AND :maxDiff ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<QuizSong> findRandomSongsByDifficulty(@Param("minDiff") int minDiff, @Param("maxDiff") int maxDiff, @Param("limit") int limit);

    @Query("SELECT s FROM QuizSong s WHERE s.previewUrl IS NULL OR s.previewUrl = '' OR s.artworkUrl IS NULL OR s.artworkUrl = '' OR s.youtubeUrl IS NULL OR s.youtubeUrl = ''")
    List<QuizSong> findSongsNeedingEnrichment();

    @Query("SELECT COUNT(s) FROM QuizSong s WHERE s.previewUrl IS NULL OR s.previewUrl = ''")
    long countSongsMissingPreview();
}
