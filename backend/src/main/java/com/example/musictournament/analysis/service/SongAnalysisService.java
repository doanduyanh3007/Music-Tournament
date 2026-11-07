package com.example.musictournament.analysis.service;

import com.example.musictournament.analysis.dto.*;
import com.example.musictournament.analysis.model.*;
import com.example.musictournament.analysis.repo.*;
import com.example.musictournament.model.QuizSong;
import com.example.musictournament.repo.QuizSongRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SongAnalysisService {

    private final SongAnalysisRepository analysisRepository;
    private final SongVersionRepository versionRepository;
    private final SongSectionRepository sectionRepository;
    private final SongLyricRepository lyricRepository;
    private final SongSourceRepository sourceRepository;
    private final SongAudioFeatureRepository audioFeatureRepository;
    private final SongNoteRepository noteRepository;
    private final QuizSongRepository quizSongRepository;

    public SongAnalysisService(
            SongAnalysisRepository analysisRepository,
            SongVersionRepository versionRepository,
            SongSectionRepository sectionRepository,
            SongLyricRepository lyricRepository,
            SongSourceRepository sourceRepository,
            SongAudioFeatureRepository audioFeatureRepository,
            SongNoteRepository noteRepository,
            QuizSongRepository quizSongRepository
    ) {
        this.analysisRepository = analysisRepository;
        this.versionRepository = versionRepository;
        this.sectionRepository = sectionRepository;
        this.lyricRepository = lyricRepository;
        this.sourceRepository = sourceRepository;
        this.audioFeatureRepository = audioFeatureRepository;
        this.noteRepository = noteRepository;
        this.quizSongRepository = quizSongRepository;
    }

    // ========================================================
    // Analysis List & Get / Create
    // ========================================================

    @Transactional(readOnly = true)
    public Page<SongAnalysisSummaryDto> listAnalyses(String query, SongAnalysis.Status status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<SongAnalysis> entityPage = analysisRepository.search(query, status, pageable);
        return entityPage.map(this::toSummaryDto);
    }

    @Transactional
    public SongAnalysisDetailDto getOrCreateAnalysis(Long quizSongId) {
        QuizSong song = quizSongRepository.findById(quizSongId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài hát ID: " + quizSongId));

        SongAnalysis analysis = analysisRepository.findByQuizSongId(quizSongId)
                .orElseGet(() -> {
                    SongAnalysis newAnalysis = new SongAnalysis(song);
                    newAnalysis = analysisRepository.save(newAnalysis);

                    // Create default original version
                    SongVersion defaultVersion = new SongVersion(
                            newAnalysis.getId(),
                            SongVersion.VersionType.ORIGINAL,
                            "Original Version",
                            0L
                    );
                    defaultVersion.setSpotifyUrl(song.getSpotifyUrl());
                    defaultVersion.setYoutubeUrl(song.getYoutubeUrl());
                    defaultVersion.setAudioFilePath(song.getPreviewUrl());
                    defaultVersion.setIsPrimary(true);
                    defaultVersion = versionRepository.save(defaultVersion);

                    newAnalysis.setCurrentVersionId(defaultVersion.getId());
                    return analysisRepository.save(newAnalysis);
                });

        return getAnalysisDetail(analysis.getId(), null);
    }

    @Transactional(readOnly = true)
    public SongAnalysisDetailDto getAnalysisDetail(Long analysisId, Long specificVersionId) {
        SongAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản phân tích ID: " + analysisId));

        List<SongVersion> versions = versionRepository.findByAnalysisIdOrderByCreatedAtAsc(analysisId);
        Long targetVersionId = specificVersionId;
        if (targetVersionId == null) {
            targetVersionId = analysis.getCurrentVersionId();
        }
        if (targetVersionId == null && !versions.isEmpty()) {
            targetVersionId = versions.get(0).getId();
        }

        SongVersion currentVersion = null;
        if (targetVersionId != null) {
            Long finalTargetVersionId = targetVersionId;
            currentVersion = versions.stream()
                    .filter(v -> v.getId().equals(finalTargetVersionId))
                    .findFirst()
                    .orElse(versions.isEmpty() ? null : versions.get(0));
        }

        List<SongSection> sections = (currentVersion != null)
                ? sectionRepository.findByVersionIdOrderBySortOrderAscStartTimeMsAsc(currentVersion.getId())
                : List.of();

        List<SongLyric> lyrics = (currentVersion != null)
                ? lyricRepository.findByVersionIdOrderBySortOrderAscStartTimeMsAsc(currentVersion.getId())
                : List.of();

        List<SongSource> sources = sourceRepository.findByAnalysisIdOrderByCreatedAtDesc(analysisId);
        List<SongNote> notes = noteRepository.findByAnalysisIdOrderByCreatedAtDesc(analysisId);

        Optional<SongAudioFeature> audioFeatureOpt = (currentVersion != null)
                ? audioFeatureRepository.findFirstByVersionIdOrderByCreatedAtDesc(currentVersion.getId())
                : Optional.empty();

        SongAnalysisDetailDto dto = new SongAnalysisDetailDto();
        dto.setId(analysis.getId());
        dto.setQuizSongId(analysis.getQuizSong().getId());
        dto.setTitle(analysis.getQuizSong().getTitle());
        dto.setArtist(analysis.getQuizSong().getArtist());
        dto.setRomajiTitle(analysis.getQuizSong().getRomajiTitle());
        dto.setArtworkUrl(analysis.getQuizSong().getArtworkUrl());
        dto.setPreviewUrl(analysis.getQuizSong().getPreviewUrl());
        dto.setSpotifyUrl(analysis.getQuizSong().getSpotifyUrl());
        dto.setYoutubeUrl(analysis.getQuizSong().getYoutubeUrl());
        dto.setGenre(analysis.getQuizSong().getGenre());

        dto.setStatus(analysis.getStatus());
        dto.setCurrentVersionId(currentVersion != null ? currentVersion.getId() : analysis.getCurrentVersionId());
        dto.setSongBackground(analysis.getSongBackground());
        dto.setMusicalArrangement(analysis.getMusicalArrangement());
        dto.setOverallInterpretation(analysis.getOverallInterpretation());
        dto.setCreatedAt(analysis.getCreatedAt());
        dto.setUpdatedAt(analysis.getUpdatedAt());

        dto.setVersions(versions.stream().map(this::toVersionDto).collect(Collectors.toList()));
        if (currentVersion != null) {
            dto.setCurrentVersion(toVersionDto(currentVersion));
        }
        dto.setSections(sections.stream().map(this::toSectionDto).collect(Collectors.toList()));
        dto.setLyrics(lyrics.stream().map(this::toLyricDto).collect(Collectors.toList()));
        dto.setSources(sources.stream().map(this::toSourceDto).collect(Collectors.toList()));
        dto.setNotes(notes.stream().map(this::toNoteDto).collect(Collectors.toList()));
        audioFeatureOpt.ifPresent(feature -> dto.setAudioFeature(toAudioFeatureDto(feature)));

        return dto;
    }

    @Transactional
    public SongAnalysisDetailDto updateAnalysis(Long analysisId, SongAnalysisUpsertRequest req) {
        SongAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản phân tích ID: " + analysisId));

        if (req.getStatus() != null) {
            analysis.setStatus(req.getStatus());
        }
        if (req.getCurrentVersionId() != null) {
            // Verify version belongs to this analysis
            SongVersion version = versionRepository.findById(req.getCurrentVersionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên bản không tồn tại"));
            if (!version.getAnalysisId().equals(analysisId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên bản không thuộc bài hát này");
            }
            analysis.setCurrentVersionId(req.getCurrentVersionId());
        }
        if (req.getSongBackground() != null) {
            analysis.setSongBackground(req.getSongBackground());
        }
        if (req.getMusicalArrangement() != null) {
            analysis.setMusicalArrangement(req.getMusicalArrangement());
        }
        if (req.getOverallInterpretation() != null) {
            analysis.setOverallInterpretation(req.getOverallInterpretation());
        }
        analysis.setUpdatedAt(LocalDateTime.now());
        analysisRepository.save(analysis);

        return getAnalysisDetail(analysisId, analysis.getCurrentVersionId());
    }

    @Transactional
    public void deleteAnalysis(Long analysisId) {
        SongAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản phân tích ID: " + analysisId));

        List<SongVersion> versions = versionRepository.findByAnalysisIdOrderByCreatedAtAsc(analysisId);
        for (SongVersion version : versions) {
            sectionRepository.deleteByVersionId(version.getId());
            lyricRepository.deleteByVersionId(version.getId());
            audioFeatureRepository.deleteByVersionId(version.getId());
            versionRepository.delete(version);
        }
        sourceRepository.deleteByAnalysisId(analysisId);
        noteRepository.deleteByAnalysisId(analysisId);
        analysisRepository.delete(analysis);
    }

    // ========================================================
    // Song Versions
    // ========================================================

    @Transactional
    public SongVersionDto createVersion(Long analysisId, SongVersionDto dto) {
        if (!analysisRepository.existsById(analysisId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản phân tích ID: " + analysisId);
        }
        SongVersion version = new SongVersion(
                analysisId,
                dto.getVersionType() != null ? dto.getVersionType() : SongVersion.VersionType.ORIGINAL,
                dto.getVersionName() != null ? dto.getVersionName().trim() : "New Version",
                dto.getDurationMs() != null ? Math.max(0L, dto.getDurationMs()) : 0L
        );
        version.setSpotifyUrl(dto.getSpotifyUrl());
        version.setYoutubeUrl(dto.getYoutubeUrl());
        version.setAudioFilePath(dto.getAudioFilePath());
        version.setIsPrimary(Boolean.TRUE.equals(dto.getIsPrimary()));

        if (Boolean.TRUE.equals(version.getIsPrimary())) {
            // Unset other primary versions
            List<SongVersion> existing = versionRepository.findByAnalysisIdOrderByCreatedAtAsc(analysisId);
            for (SongVersion v : existing) {
                v.setIsPrimary(false);
                versionRepository.save(v);
            }
        }

        version = versionRepository.save(version);
        return toVersionDto(version);
    }

    @Transactional
    public SongVersionDto updateVersion(Long versionId, SongVersionDto dto) {
        SongVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản ID: " + versionId));

        if (dto.getVersionName() != null && !dto.getVersionName().isBlank()) {
            version.setVersionName(dto.getVersionName().trim());
        }
        if (dto.getVersionType() != null) {
            version.setVersionType(dto.getVersionType());
        }
        if (dto.getDurationMs() != null) {
            version.setDurationMs(Math.max(0L, dto.getDurationMs()));
        }
        if (dto.getSpotifyUrl() != null) {
            version.setSpotifyUrl(dto.getSpotifyUrl());
        }
        if (dto.getYoutubeUrl() != null) {
            version.setYoutubeUrl(dto.getYoutubeUrl());
        }
        if (dto.getAudioFilePath() != null) {
            version.setAudioFilePath(dto.getAudioFilePath());
        }
        if (dto.getIsPrimary() != null) {
            if (Boolean.TRUE.equals(dto.getIsPrimary())) {
                List<SongVersion> existing = versionRepository.findByAnalysisIdOrderByCreatedAtAsc(version.getAnalysisId());
                for (SongVersion v : existing) {
                    if (!v.getId().equals(versionId)) {
                        v.setIsPrimary(false);
                        versionRepository.save(v);
                    }
                }
            }
            version.setIsPrimary(dto.getIsPrimary());
        }

        return toVersionDto(versionRepository.save(version));
    }

    @Transactional
    public void deleteVersion(Long versionId) {
        SongVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản ID: " + versionId));

        sectionRepository.deleteByVersionId(versionId);
        lyricRepository.deleteByVersionId(versionId);
        audioFeatureRepository.deleteByVersionId(versionId);
        versionRepository.delete(version);
    }

    // ========================================================
    // Song Sections
    // ========================================================

    @Transactional
    public List<SongSectionDto> saveSections(Long versionId, List<SongSectionDto> dtos) {
        if (!versionRepository.existsById(versionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản ID: " + versionId);
        }

        // Validate timestamps
        for (SongSectionDto dto : dtos) {
            if (dto.getStartTimeMs() == null || dto.getEndTimeMs() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian bắt đầu và kết thúc (ms) là bắt buộc");
            }
            if (dto.getStartTimeMs() < 0 || dto.getEndTimeMs() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timestamp không được là số âm");
            }
            if (dto.getStartTimeMs() > dto.getEndTimeMs()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian bắt đầu không thể lớn hơn thời gian kết thúc");
            }
        }

        sectionRepository.deleteByVersionId(versionId);

        List<SongSection> saved = new ArrayList<>();
        int order = 0;
        for (SongSectionDto dto : dtos) {
            SongSection section = new SongSection(
                    versionId,
                    dto.getSectionType() != null ? dto.getSectionType() : SongSection.SectionType.VERSE,
                    dto.getSectionLabel() != null ? dto.getSectionLabel().trim() : "Phần " + (order + 1),
                    dto.getStartTimeMs(),
                    dto.getEndTimeMs()
            );
            section.setExplanation(dto.getExplanation());
            section.setEmotionalIntensity(dto.getEmotionalIntensity() != null ? dto.getEmotionalIntensity() : 5);
            section.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : order++);
            saved.add(sectionRepository.save(section));
        }

        return saved.stream().map(this::toSectionDto).collect(Collectors.toList());
    }

    // ========================================================
    // Song Lyrics
    // ========================================================

    @Transactional
    public List<SongLyricDto> saveLyrics(Long versionId, List<SongLyricDto> dtos) {
        if (!versionRepository.existsById(versionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản ID: " + versionId);
        }

        // Validate
        for (SongLyricDto dto : dtos) {
            if (dto.getJapaneseText() == null || dto.getJapaneseText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lời tiếng Nhật không được để trống");
            }
            if (dto.getStartTimeMs() != null && dto.getEndTimeMs() != null) {
                if (dto.getStartTimeMs() < 0 || dto.getEndTimeMs() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timestamp không được là số âm");
                }
                if (dto.getStartTimeMs() > dto.getEndTimeMs()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian bắt đầu không thể lớn hơn thời gian kết thúc");
                }
            }
        }

        lyricRepository.deleteByVersionId(versionId);

        List<SongLyric> saved = new ArrayList<>();
        int order = 0;
        for (SongLyricDto dto : dtos) {
            SongLyric lyric = new SongLyric(versionId, dto.getJapaneseText().trim(), dto.getSortOrder() != null ? dto.getSortOrder() : order++);
            lyric.setSectionId(dto.getSectionId());
            lyric.setStartTimeMs(dto.getStartTimeMs());
            lyric.setEndTimeMs(dto.getEndTimeMs());
            lyric.setFuriganaReading(dto.getFuriganaReading());
            lyric.setRomajiReading(dto.getRomajiReading());
            lyric.setLiteralTranslation(dto.getLiteralTranslation());
            lyric.setPoeticTranslation(dto.getPoeticTranslation());
            lyric.setMetaphorExplanation(dto.getMetaphorExplanation());
            saved.add(lyricRepository.save(lyric));
        }

        return saved.stream().map(this::toLyricDto).collect(Collectors.toList());
    }

    // ========================================================
    // Sources, Notes, Audio Features
    // ========================================================

    @Transactional
    public SongSourceDto addSource(Long analysisId, SongSourceDto dto) {
        if (!analysisRepository.existsById(analysisId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản phân tích ID: " + analysisId);
        }
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề nguồn tham khảo không được để trống");
        }
        SongSource source = new SongSource(
                analysisId,
                dto.getSourceType() != null ? dto.getSourceType() : SongSource.SourceType.UNVERIFIED,
                dto.getTitle().trim(),
                dto.getUrl(),
                dto.getCitedClaim()
        );
        source.setAuthor(dto.getAuthor());
        source.setVerified(Boolean.TRUE.equals(dto.getVerified()));
        return toSourceDto(sourceRepository.save(source));
    }

    @Transactional
    public void deleteSource(Long sourceId) {
        sourceRepository.deleteById(sourceId);
    }

    @Transactional
    public SongNoteDto addNote(Long analysisId, SongNoteDto dto) {
        if (!analysisRepository.existsById(analysisId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản phân tích ID: " + analysisId);
        }
        if (dto.getNoteContent() == null || dto.getNoteContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung ghi chú không được để trống");
        }
        SongNote note = new SongNote(
                analysisId,
                dto.getAuthorName() != null ? dto.getAuthorName().trim() : "Admin",
                dto.getNoteContent().trim()
        );
        return toNoteDto(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(Long noteId) {
        noteRepository.deleteById(noteId);
    }

    @Transactional
    public SongAudioFeatureDto saveAudioFeature(Long versionId, SongAudioFeatureDto dto) {
        if (!versionRepository.existsById(versionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản ID: " + versionId);
        }
        audioFeatureRepository.deleteByVersionId(versionId);

        SongAudioFeature feature = new SongAudioFeature(versionId, dto.getBpm(), dto.getDetectedKey());
        feature.setTimeSignature(dto.getTimeSignature());
        feature.setBoundaryPointsJson(dto.getBoundaryPointsJson());
        feature.setSpectralFeaturesJson(dto.getSpectralFeaturesJson());
        feature.setAnalysisEngine(dto.getAnalysisEngine() != null ? dto.getAnalysisEngine() : "Librosa");
        return toAudioFeatureDto(audioFeatureRepository.save(feature));
    }

    // ========================================================
    // Entity -> DTO Mappers
    // ========================================================

    private SongAnalysisSummaryDto toSummaryDto(SongAnalysis sa) {
        SongAnalysisSummaryDto dto = new SongAnalysisSummaryDto();
        dto.setId(sa.getId());
        dto.setQuizSongId(sa.getQuizSong().getId());
        dto.setTitle(sa.getQuizSong().getTitle());
        dto.setArtist(sa.getQuizSong().getArtist());
        dto.setRomajiTitle(sa.getQuizSong().getRomajiTitle());
        dto.setArtworkUrl(sa.getQuizSong().getArtworkUrl());
        dto.setGenre(sa.getQuizSong().getGenre());
        dto.setStatus(sa.getStatus());
        dto.setCurrentVersionId(sa.getCurrentVersionId());
        dto.setVersionCount((int) versionRepository.countByAnalysisId(sa.getId()));
        dto.setUpdatedAt(sa.getUpdatedAt());
        return dto;
    }

    private SongVersionDto toVersionDto(SongVersion v) {
        SongVersionDto dto = new SongVersionDto();
        dto.setId(v.getId());
        dto.setAnalysisId(v.getAnalysisId());
        dto.setVersionType(v.getVersionType());
        dto.setVersionName(v.getVersionName());
        dto.setDurationMs(v.getDurationMs());
        dto.setSpotifyUrl(v.getSpotifyUrl());
        dto.setYoutubeUrl(v.getYoutubeUrl());
        dto.setAudioFilePath(v.getAudioFilePath());
        dto.setIsPrimary(v.getIsPrimary());
        dto.setCreatedAt(v.getCreatedAt());
        return dto;
    }

    private SongSectionDto toSectionDto(SongSection s) {
        SongSectionDto dto = new SongSectionDto();
        dto.setId(s.getId());
        dto.setVersionId(s.getVersionId());
        dto.setSectionType(s.getSectionType());
        dto.setSectionLabel(s.getSectionLabel());
        dto.setStartTimeMs(s.getStartTimeMs());
        dto.setEndTimeMs(s.getEndTimeMs());
        dto.setExplanation(s.getExplanation());
        dto.setEmotionalIntensity(s.getEmotionalIntensity());
        dto.setSortOrder(s.getSortOrder());
        return dto;
    }

    private SongLyricDto toLyricDto(SongLyric l) {
        SongLyricDto dto = new SongLyricDto();
        dto.setId(l.getId());
        dto.setVersionId(l.getVersionId());
        dto.setSectionId(l.getSectionId());
        dto.setStartTimeMs(l.getStartTimeMs());
        dto.setEndTimeMs(l.getEndTimeMs());
        dto.setJapaneseText(l.getJapaneseText());
        dto.setFuriganaReading(l.getFuriganaReading());
        dto.setRomajiReading(l.getRomajiReading());
        dto.setLiteralTranslation(l.getLiteralTranslation());
        dto.setPoeticTranslation(l.getPoeticTranslation());
        dto.setMetaphorExplanation(l.getMetaphorExplanation());
        dto.setSortOrder(l.getSortOrder());
        return dto;
    }

    private SongSourceDto toSourceDto(SongSource s) {
        SongSourceDto dto = new SongSourceDto();
        dto.setId(s.getId());
        dto.setAnalysisId(s.getAnalysisId());
        dto.setSourceType(s.getSourceType());
        dto.setTitle(s.getTitle());
        dto.setUrl(s.getUrl());
        dto.setCitedClaim(s.getCitedClaim());
        dto.setAuthor(s.getAuthor());
        dto.setVerified(s.getVerified());
        dto.setCreatedAt(s.getCreatedAt());
        return dto;
    }

    private SongNoteDto toNoteDto(SongNote n) {
        SongNoteDto dto = new SongNoteDto();
        dto.setId(n.getId());
        dto.setAnalysisId(n.getAnalysisId());
        dto.setAuthorName(n.getAuthorName());
        dto.setNoteContent(n.getNoteContent());
        dto.setCreatedAt(n.getCreatedAt());
        dto.setUpdatedAt(n.getUpdatedAt());
        return dto;
    }

    private SongAudioFeatureDto toAudioFeatureDto(SongAudioFeature f) {
        SongAudioFeatureDto dto = new SongAudioFeatureDto();
        dto.setId(f.getId());
        dto.setVersionId(f.getVersionId());
        dto.setBpm(f.getBpm());
        dto.setDetectedKey(f.getDetectedKey());
        dto.setTimeSignature(f.getTimeSignature());
        dto.setBoundaryPointsJson(f.getBoundaryPointsJson());
        dto.setSpectralFeaturesJson(f.getSpectralFeaturesJson());
        dto.setAnalysisEngine(f.getAnalysisEngine());
        dto.setCreatedAt(f.getCreatedAt());
        return dto;
    }
}
