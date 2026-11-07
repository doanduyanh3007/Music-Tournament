package com.example.musictournament;

import com.example.musictournament.analysis.dto.SongAnalysisDetailDto;
import com.example.musictournament.analysis.dto.SongAudioFeatureDto;
import com.example.musictournament.analysis.model.SongVersion;
import com.example.musictournament.analysis.repo.SongVersionRepository;
import com.example.musictournament.analysis.service.AudioAnalysisService;
import com.example.musictournament.analysis.service.SongAnalysisService;
import com.example.musictournament.model.QuizSong;
import com.example.musictournament.repo.QuizSongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:audio-analysis-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.h2.console.enabled=false",
    "audio.upload-dir=target/test-uploads/audio"
})
class AudioAnalysisServiceTest {

    @Autowired
    private AudioAnalysisService audioAnalysisService;

    @Autowired
    private SongAnalysisService songAnalysisService;

    @Autowired
    private SongVersionRepository versionRepository;

    @Autowired
    private QuizSongRepository quizSongRepository;

    private Long versionId;

    @BeforeEach
    void setUp() {
        QuizSong song = new QuizSong(
                "迷星叫",
                "MyGO!!!!!",
                "https://example.com/cover.jpg",
                null,
                null,
                null,
                "J-Rock",
                75
        );
        song.setRomajiTitle("Mayoiuta");
        song = quizSongRepository.save(song);

        SongAnalysisDetailDto detail = songAnalysisService.getOrCreateAnalysis(song.getId());
        versionId = detail.getCurrentVersionId();
    }

    @Test
    @DisplayName("Upload valid audio file performs analysis, saves features and updates version")
    void testUploadAudioFile() {
        byte[] dummyAudioContent = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample_track.mp3",
                "audio/mpeg",
                dummyAudioContent
        );

        SongAudioFeatureDto feature = audioAnalysisService.analyzeAndStoreAudio(versionId, file);

        assertThat(feature).isNotNull();
        assertThat(feature.getVersionId()).isEqualTo(versionId);
        assertThat(feature.getBpm()).isNotNull();
        assertThat(feature.getDetectedKey()).isNotBlank();
        assertThat(feature.getBoundaryPointsJson()).isNotBlank();

        // Verify version updated
        SongVersion version = versionRepository.findById(versionId).orElseThrow();
        assertThat(version.getAudioFilePath()).contains("/uploads/audio/");
        assertThat(version.getAudioFilePath()).contains("sample_track.mp3");
    }

    @Test
    @DisplayName("Empty audio upload file throws 400 Bad Request")
    void testEmptyAudioUploadThrowsBadRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.mp3",
                "audio/mpeg",
                new byte[0]
        );

        assertThrows(ResponseStatusException.class, () -> {
            audioAnalysisService.analyzeAndStoreAudio(versionId, emptyFile);
        });
    }
}
