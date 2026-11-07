package com.example.musictournament;

import com.example.musictournament.analysis.dto.*;
import com.example.musictournament.analysis.model.SongAnalysis;
import com.example.musictournament.analysis.model.SongSection;
import com.example.musictournament.analysis.model.SongSource;
import com.example.musictournament.analysis.model.SongVersion;
import com.example.musictournament.analysis.service.SongAnalysisService;
import com.example.musictournament.model.QuizSong;
import com.example.musictournament.repo.QuizSongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:analysis-service-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.h2.console.enabled=false"
})
class SongAnalysisServiceTest {

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
                60
        );
        sampleSong.setRomajiTitle("Haruhikage");
        sampleSong = quizSongRepository.save(sampleSong);
    }

    @Test
    @DisplayName("getOrCreateAnalysis creates an analysis with a default version")
    void testGetOrCreateAnalysis() {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isNotNull();
        assertThat(detail.getTitle()).isEqualTo("春日影");
        assertThat(detail.getRomajiTitle()).isEqualTo("Haruhikage");
        assertThat(detail.getStatus()).isEqualTo(SongAnalysis.Status.DRAFT);
        assertThat(detail.getVersions()).hasSize(1);
        assertThat(detail.getVersions().get(0).getVersionName()).isEqualTo("Original Version");
        assertThat(detail.getVersions().get(0).getSpotifyUrl()).isEqualTo("https://spotify.com/track/123");
    }

    @Test
    @DisplayName("Update analysis background story, arrangement and overall interpretation")
    void testUpdateAnalysis() {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());

        SongAnalysisUpsertRequest req = new SongAnalysisUpsertRequest();
        req.setStatus(SongAnalysis.Status.COMPLETED);
        req.setSongBackground("Bối cảnh sáng tác trong anime BanG Dream! It's MyGO!!!!!.");
        req.setMusicalArrangement("Guitar đôi chơi các câu riff đối đáp, trống dồn dập ở điệp khúc.");
        req.setOverallInterpretation("Bài hát là sự giải tỏa cảm xúc dồn nén và tìm lại chính mình.");

        SongAnalysisDetailDto updated = analysisService.updateAnalysis(detail.getId(), req);

        assertThat(updated.getStatus()).isEqualTo(SongAnalysis.Status.COMPLETED);
        assertThat(updated.getSongBackground()).contains("BanG Dream");
        assertThat(updated.getMusicalArrangement()).contains("Guitar đôi");
        assertThat(updated.getOverallInterpretation()).contains("giải tỏa cảm xúc");
    }

    @Test
    @DisplayName("Save song sections with start/end millisecond timestamps")
    void testSaveSections() {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());
        Long versionId = detail.getCurrentVersionId();

        SongSectionDto intro = new SongSectionDto();
        intro.setSectionType(SongSection.SectionType.INTRO);
        intro.setSectionLabel("Intro");
        intro.setStartTimeMs(0L);
        intro.setEndTimeMs(18500L);
        intro.setExplanation("Tiếng piano nhẹ nhàng mở đầu");
        intro.setEmotionalIntensity(4);
        intro.setSortOrder(1);

        SongSectionDto verse1 = new SongSectionDto();
        verse1.setSectionType(SongSection.SectionType.VERSE);
        verse1.setSectionLabel("Verse 1");
        verse1.setStartTimeMs(18500L);
        verse1.setEndTimeMs(42000L);
        verse1.setExplanation("Tomori bắt đầu cất giọng kể lại quá khứ");
        verse1.setEmotionalIntensity(6);
        verse1.setSortOrder(2);

        List<SongSectionDto> saved = analysisService.saveSections(versionId, List.of(intro, verse1));
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getStartTimeMs()).isEqualTo(0L);
        assertThat(saved.get(0).getEndTimeMs()).isEqualTo(18500L);
        assertThat(saved.get(1).getSectionLabel()).isEqualTo("Verse 1");
    }

    @Test
    @DisplayName("Reject section with invalid timestamps (start > end or negative)")
    void testInvalidSectionTimestamps() {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());
        Long versionId = detail.getCurrentVersionId();

        SongSectionDto invalid = new SongSectionDto();
        invalid.setStartTimeMs(50000L);
        invalid.setEndTimeMs(10000L); // start > end

        assertThrows(ResponseStatusException.class, () -> {
            analysisService.saveSections(versionId, List.of(invalid));
        });

        SongSectionDto negative = new SongSectionDto();
        negative.setStartTimeMs(-1000L);
        negative.setEndTimeMs(10000L);

        assertThrows(ResponseStatusException.class, () -> {
            analysisService.saveSections(versionId, List.of(negative));
        });
    }

    @Test
    @DisplayName("Save lyrics with Japanese, Furigana, Romaji and Vietnamese translations")
    void testSaveLyrics() {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());
        Long versionId = detail.getCurrentVersionId();

        SongLyricDto line1 = new SongLyricDto();
        line1.setStartTimeMs(18500L);
        line1.setEndTimeMs(23000L);
        line1.setJapaneseText("届かない言葉だけが");
        line1.setFuriganaReading("とどかないことばだけが");
        line1.setRomajiReading("todokanai kotoba dake ga");
        line1.setLiteralTranslation("Chỉ có những lời không thể chạm tới");
        line1.setPoeticTranslation("Chỉ còn lại những tâm sự chẳng thể giãi bày");
        line1.setMetaphorExplanation("Biểu thị sự bế tắc trong giao tiếp giữa các thành viên");
        line1.setSortOrder(1);

        List<SongLyricDto> savedLyrics = analysisService.saveLyrics(versionId, List.of(line1));
        assertThat(savedLyrics).hasSize(1);
        assertThat(savedLyrics.get(0).getRomajiReading()).isEqualTo("todokanai kotoba dake ga");
        assertThat(savedLyrics.get(0).getLiteralTranslation()).isEqualTo("Chỉ có những lời không thể chạm tới");
        assertThat(savedLyrics.get(0).getPoeticTranslation()).isEqualTo("Chỉ còn lại những tâm sự chẳng thể giãi bày");
    }

    @Test
    @DisplayName("Add sources and personal notes")
    void testAddSourcesAndNotes() {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());

        // Add verified source
        SongSourceDto src = new SongSourceDto();
        src.setSourceType(SongSource.SourceType.VERIFIED_OFFICIAL);
        src.setTitle("BanG Dream! Official Interview");
        src.setUrl("https://bang-dream.com/interview");
        src.setCitedClaim("Haruhikage được sáng tác như lời chia tay và tái ngộ");
        src.setVerified(true);
        SongSourceDto savedSrc = analysisService.addSource(detail.getId(), src);
        assertThat(savedSrc.getId()).isNotNull();
        assertThat(savedSrc.getVerified()).isTrue();

        // Add personal note
        SongNoteDto note = new SongNoteDto();
        note.setAuthorName("Admin Duy Anh");
        note.setNoteContent("Giai điệu piano đoạn đầu rất xúc động, phân đoạn cao trào solo guitar tuyệt vời.");
        SongNoteDto savedNote = analysisService.addNote(detail.getId(), note);
        assertThat(savedNote.getId()).isNotNull();
        assertThat(savedNote.getAuthorName()).isEqualTo("Admin Duy Anh");
    }

    @Test
    @DisplayName("Save audio features with BPM and detected boundary points")
    void testSaveAudioFeatures() {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());
        Long versionId = detail.getCurrentVersionId();

        SongAudioFeatureDto feature = new SongAudioFeatureDto();
        feature.setBpm(168.0);
        feature.setDetectedKey("F# Minor");
        feature.setTimeSignature("4/4");
        feature.setBoundaryPointsJson("[0, 18500, 42000, 75000, 120000]");
        feature.setAnalysisEngine("Librosa-0.10.1");

        SongAudioFeatureDto savedFeature = analysisService.saveAudioFeature(versionId, feature);
        assertThat(savedFeature.getBpm()).isEqualTo(168.0);
        assertThat(savedFeature.getDetectedKey()).isEqualTo("F# Minor");
        assertThat(savedFeature.getBoundaryPointsJson()).contains("18500");
    }

    @Test
    @DisplayName("Delete analysis cascades and deletes child entities")
    void testDeleteAnalysis() {
        SongAnalysisDetailDto detail = analysisService.getOrCreateAnalysis(sampleSong.getId());
        Long analysisId = detail.getId();

        analysisService.deleteAnalysis(analysisId);

        assertThrows(ResponseStatusException.class, () -> {
            analysisService.getAnalysisDetail(analysisId, null);
        });
    }
}
