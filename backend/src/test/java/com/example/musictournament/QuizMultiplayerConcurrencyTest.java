package com.example.musictournament;

import com.example.musictournament.model.QuizSong;
import com.example.musictournament.repo.QuizPoolSongRepository;
import com.example.musictournament.repo.QuizSongRepository;
import com.example.musictournament.service.QuizAnswerMatcher;
import com.example.musictournament.service.QuizMultiplayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;

public class QuizMultiplayerConcurrencyTest {

    private QuizMultiplayerService service;
    private QuizSong sampleSong;

    @BeforeEach
    public void setup() {
        QuizSongRepository songRepo = Mockito.mock(QuizSongRepository.class);
        QuizPoolSongRepository poolSongRepo = Mockito.mock(QuizPoolSongRepository.class);
        QuizAnswerMatcher matcher = new QuizAnswerMatcher();

        sampleSong = new QuizSong();
        sampleSong.setId(101L);
        sampleSong.setTitle("Sparkle");
        sampleSong.setArtist("RADWIMPS");
        sampleSong.setPreviewUrl("https://audio.example.com/preview.mp3");
        sampleSong.setArtworkUrl("https://art.example.com/cover.jpg");
        sampleSong.setDifficulty(35);
        sampleSong.setActive(true);

        Mockito.when(songRepo.findRandomSongs(anyInt())).thenReturn(List.of(sampleSong));

        service = new QuizMultiplayerService(songRepo, poolSongRepo, matcher);
    }

    @Test
    public void testFirstBloodAtomicConcurrency() throws Exception {
        // 1. Create and Join room
        Map<String, Object> room = service.createRoom(
                "player_1", "Alice", "Championship Room", "TEN_QUESTIONS", "TITLE", 20, 1, null, null
        );
        String code = (String) room.get("roomCode");

        service.joinRoom(code, "player_2", "Bob");

        // 2. Start game
        service.startRoomGame(code, "player_1");

        Map<String, Object> state = service.getRoomState(code, "player_1");
        assertEquals("PLAYING", state.get("status"));
        String qId = (String) state.get("questionId");

        // Verify title & artist are NOT leaked in PLAYING state
        assertNull(state.get("officialTitle"));
        assertNull(state.get("officialArtist"));

        // 3. Concurrent answer submission by Alice and Bob
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<Map<String, Object>> future1 = executor.submit(() -> {
            latch.await();
            return service.submitAnswer(code, "player_1", qId, "Sparkle");
        });

        Future<Map<String, Object>> future2 = executor.submit(() -> {
            latch.await();
            return service.submitAnswer(code, "player_2", qId, "Sparkle");
        });

        latch.countDown(); // Fire both threads at the exact same instant

        Map<String, Object> res1 = future1.get(2, TimeUnit.SECONDS);
        Map<String, Object> res2 = future2.get(2, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals("CORRECT", res1.get("status"));
        assertEquals("CORRECT", res2.get("status"));

        boolean fb1 = Boolean.TRUE.equals(res1.get("firstBlood"));
        boolean fb2 = Boolean.TRUE.equals(res2.get("firstBlood"));

        // EXACTLY ONE PLAYER MUST RECEIVE FIRST BLOOD!
        assertTrue(fb1 ^ fb2, "Duy nhất 1 người chơi được nhận First Blood!");

        // 4. Verify round ended since all players answered
        Map<String, Object> finalState = service.getRoomState(code, "player_1");
        assertEquals("ROUND_RESULT", finalState.get("status"));
        assertEquals("Sparkle", finalState.get("officialTitle"));
        assertEquals("RADWIMPS", finalState.get("officialArtist"));
        assertNotNull(finalState.get("firstBloodWinner"));
    }
}
