package com.example.musictournament;

import com.example.musictournament.analysis.dto.SongAnalysisDetailDto;
import com.example.musictournament.analysis.model.SongAnalysis;
import com.example.musictournament.analysis.service.SongAiResearchService;
import com.example.musictournament.analysis.service.SongAnalysisService;
import com.example.musictournament.model.QuizSong;
import com.example.musictournament.repo.QuizSongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-research-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.h2.console.enabled=false"
})
class SongAiResearchServiceTest {

    @Autowired
    private SongAiResearchService aiResearchService;

    @Autowired
    private SongAnalysisService analysisService;

    @Autowired
    private QuizSongRepository quizSongRepository;

    private QuizSong sampleSong;

    @BeforeEach
    void setUp() {
        sampleSong = new QuizSong(
                "春日影",
                "MyGO!!!!!",
                "https://example.com/cover.jpg",
                "https://example.com/preview.mp3",
                "https://youtube.com/watch?v=123",
                "https://spotify.com/track/123",
                "J-Rock",
                70
        );
        sampleSong.setRomajiTitle("Haruhikage");
        sampleSong = quizSongRepository.save(sampleSong);
    }

    @Test
    @DisplayName("AI Research with module ALL populates background, arrangement, interpretation and sources")
    void testAiResearchModuleAll() throws Exception {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());

        CompletableFuture<Void> future = aiResearchService.runAiAnalysisAsync(detail.getId(), "ALL");
        future.get(); // wait for async completion

        SongAnalysisDetailDto updated = analysisService.getAnalysisDetail(detail.getId(), null);
        assertThat(updated.getStatus()).isEqualTo(SongAnalysis.Status.COMPLETED);
        assertThat(updated.getSongBackground()).isNotBlank();
        assertThat(updated.getSongBackground()).contains("春日影");
        assertThat(updated.getMusicalArrangement()).isNotBlank();
        assertThat(updated.getMusicalArrangement()).contains("Guitar");
        assertThat(updated.getOverallInterpretation()).isNotBlank();
        assertThat(updated.getSources()).isNotEmpty();
    }

    @Test
    @DisplayName("AI Research with module BACKGROUND preserves existing musical arrangement manual edits")
    void testAiResearchModuleBackgroundPreservesOtherModules() throws Exception {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());

        // User manually edited arrangement
        String customArrangement = "Bản phối riêng do Admin ghi chép: Guitar acoustic mở đầu và solo overdrive.";
        var req = new com.example.musictournament.analysis.dto.SongAnalysisUpsertRequest();
        req.setMusicalArrangement(customArrangement);
        analysisService.updateAnalysis(detail.getId(), req);

        // Run AI research ONLY on module BACKGROUND
        CompletableFuture<Void> future = aiResearchService.runAiAnalysisAsync(detail.getId(), "BACKGROUND");
        future.get();

        SongAnalysisDetailDto updated = analysisService.getAnalysisDetail(detail.getId(), null);
        assertThat(updated.getStatus()).isEqualTo(SongAnalysis.Status.COMPLETED);
        assertThat(updated.getSongBackground()).isNotBlank();
        // Crucial requirement: custom manual arrangement must NOT be overwritten!
        assertThat(updated.getMusicalArrangement()).isEqualTo(customArrangement);
    }
}
