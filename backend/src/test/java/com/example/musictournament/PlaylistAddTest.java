package com.example.musictournament;

import com.example.musictournament.model.QuizPlaylist;
import com.example.musictournament.service.QuizMusicManagerService;
import com.example.musictournament.web.QuizPlaylistController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PlaylistAddTest {

    @Autowired
    private QuizPlaylistController quizPlaylistController;

    @Autowired
    private QuizMusicManagerService quizMusicManagerService;

    @Test
    void testAddYoutubeAndSpotifyPlaylists() {
        // 1. YouTube watch URL with playlist (fallback to video if playlist empty/private)
        String ytUrl = "https://www.youtube.com/watch?v=FoTd918zhZc&list=PLSSeWUVZSrHr9DmAhKcFgsrHIJAvtqHTaV";
        QuizPlaylist p1 = quizPlaylistController.addPlaylist(Map.of("url", ytUrl, "title", "Test YT"));
        assertNotNull(p1.getId());
        assertEquals("youtube", p1.getSource());
        quizMusicManagerService.syncPlaylist(p1.getId());
        QuizPlaylist synced1 = quizMusicManagerService.getPlaylistById(p1.getId()).orElseThrow();
        System.out.println("YT Synced status: " + synced1.getSyncStatus() + ", totalFound: " + synced1.getTotalFound());
        assertTrue(synced1.getTotalFound() > 0, "Should have found at least 1 track via fallback");

        // 2. Spotify playlist
        String spUrl = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M";
        QuizPlaylist p2 = quizPlaylistController.addPlaylist(Map.of("url", spUrl, "title", "Test Spotify"));
        assertNotNull(p2.getId());
        assertEquals("spotify", p2.getSource());
        quizMusicManagerService.syncPlaylist(p2.getId());
        QuizPlaylist synced2 = quizMusicManagerService.getPlaylistById(p2.getId()).orElseThrow();
        System.out.println("Spotify Synced status: " + synced2.getSyncStatus() + ", totalFound: " + synced2.getTotalFound());
        assertEquals("COMPLETED", synced2.getSyncStatus());
        assertTrue(synced2.getTotalFound() > 0);

        // 3. Spotify Album
        String spAlbum = "https://open.spotify.com/album/4yP0hdKOZPNshxUOjY0cZj";
        QuizPlaylist p3 = quizPlaylistController.addPlaylist(Map.of("url", spAlbum, "title", "After Hours Album"));
        assertNotNull(p3.getId());
        assertEquals("spotify", p3.getSource());
        quizMusicManagerService.syncPlaylist(p3.getId());
        QuizPlaylist synced3 = quizMusicManagerService.getPlaylistById(p3.getId()).orElseThrow();
        System.out.println("Album Synced status: " + synced3.getSyncStatus() + ", totalFound: " + synced3.getTotalFound());
        assertEquals("COMPLETED", synced3.getSyncStatus());
        assertTrue(synced3.getTotalFound() > 0);

        // 4. Spotify intl URL with query params
        String spIntl = "https://open.spotify.com/intl-vi/playlist/37i9dQZF1DXcBWIGoYBM5M?si=123456";
        QuizPlaylist p4 = quizPlaylistController.addPlaylist(Map.of("url", spIntl));
        assertNotNull(p4.getId());
        assertEquals("spotify", p4.getSource());

        // 5. Validation error
        assertThrows(IllegalArgumentException.class, () -> quizPlaylistController.addPlaylist(Map.of("url", "   ")));
    }
}
