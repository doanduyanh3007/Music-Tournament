package com.example.musictournament.analysis.web;

import com.example.musictournament.analysis.dto.*;
import com.example.musictournament.analysis.model.SongAnalysis;
import com.example.musictournament.analysis.service.AudioAnalysisService;
import com.example.musictournament.analysis.service.SongAiResearchService;
import com.example.musictournament.analysis.service.SongAnalysisService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/analysis")
public class SongAnalysisController {

    private final SongAnalysisService analysisService;
    private final SongAiResearchService aiResearchService;
    private final AudioAnalysisService audioAnalysisService;

    public SongAnalysisController(
            SongAnalysisService analysisService,
            SongAiResearchService aiResearchService,
            AudioAnalysisService audioAnalysisService
    ) {
        this.analysisService = analysisService;
        this.aiResearchService = aiResearchService;
        this.audioAnalysisService = audioAnalysisService;
    }

    @GetMapping
    public Map<String, Object> listAnalyses(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) SongAnalysis.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<SongAnalysisSummaryDto> result = analysisService.listAnalyses(query, status, page, size);
        return Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "isLast", result.isLast()
        );
    }

    @GetMapping("/song/{quizSongId}")
    public SongAnalysisDetailDto getOrCreateBySongId(@PathVariable Long quizSongId) {
        return analysisService.getOrCreateAnalysis(quizSongId);
    }

    @GetMapping("/{id}")
    public SongAnalysisDetailDto getAnalysisDetail(
            @PathVariable Long id,
            @RequestParam(required = false) Long versionId
    ) {
        return analysisService.getAnalysisDetail(id, versionId);
    }

    @PostMapping("/{id}/generate")
    public Map<String, Object> triggerAiGeneration(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "ALL") String module
    ) {
        aiResearchService.runAiAnalysisAsync(id, module);
        return Map.of(
                "status", "ANALYZING",
                "analysisId", id,
                "module", module.toUpperCase(),
                "message", "Phân tích AI đang được tiến hành ngầm. Trạng thái sẽ tự động cập nhật khi hoàn tất."
        );
    }

    @PutMapping("/{id}")
    public SongAnalysisDetailDto updateAnalysis(
            @PathVariable Long id,
            @RequestBody SongAnalysisUpsertRequest req
    ) {
        return analysisService.updateAnalysis(id, req);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteAnalysis(@PathVariable Long id) {
        analysisService.deleteAnalysis(id);
        return Map.of("success", true, "deletedAnalysisId", id);
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public SongVersionDto createVersion(
            @PathVariable Long id,
            @RequestBody SongVersionDto dto
    ) {
        return analysisService.createVersion(id, dto);
    }

    @PutMapping("/versions/{versionId}")
    public SongVersionDto updateVersion(
            @PathVariable Long versionId,
            @RequestBody SongVersionDto dto
    ) {
        return analysisService.updateVersion(versionId, dto);
    }

    @DeleteMapping("/versions/{versionId}")
    public Map<String, Object> deleteVersion(@PathVariable Long versionId) {
        analysisService.deleteVersion(versionId);
        return Map.of("success", true, "deletedVersionId", versionId);
    }

    @PutMapping("/versions/{versionId}/sections")
    public List<SongSectionDto> saveSections(
            @PathVariable Long versionId,
            @RequestBody List<SongSectionDto> sections
    ) {
        return analysisService.saveSections(versionId, sections);
    }

    @PutMapping("/versions/{versionId}/lyrics")
    public List<SongLyricDto> saveLyrics(
            @PathVariable Long versionId,
            @RequestBody List<SongLyricDto> lyrics
    ) {
        return analysisService.saveLyrics(versionId, lyrics);
    }

    @PostMapping("/{id}/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public SongSourceDto addSource(
            @PathVariable Long id,
            @RequestBody SongSourceDto dto
    ) {
        return analysisService.addSource(id, dto);
    }

    @DeleteMapping("/sources/{sourceId}")
    public Map<String, Object> deleteSource(@PathVariable Long sourceId) {
        analysisService.deleteSource(sourceId);
        return Map.of("success", true, "deletedSourceId", sourceId);
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public SongNoteDto addNote(
            @PathVariable Long id,
            @RequestBody SongNoteDto dto
    ) {
        return analysisService.addNote(id, dto);
    }

    @DeleteMapping("/notes/{noteId}")
    public Map<String, Object> deleteNote(@PathVariable Long noteId) {
        analysisService.deleteNote(noteId);
        return Map.of("success", true, "deletedNoteId", noteId);
    }

    @PutMapping("/versions/{versionId}/audio-features")
    public SongAudioFeatureDto saveAudioFeature(
            @PathVariable Long versionId,
            @RequestBody SongAudioFeatureDto dto
    ) {
        return analysisService.saveAudioFeature(versionId, dto);
    }

    @PostMapping(value = "/versions/{versionId}/upload-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SongAudioFeatureDto uploadAndAnalyzeAudio(
            @PathVariable Long versionId,
            @RequestParam("file") MultipartFile file
    ) {
        return audioAnalysisService.analyzeAndStoreAudio(versionId, file);
    }

    @PostMapping("/{id}/chat")
    public SongChatResponse chatWithSong(
            @PathVariable Long id,
            @RequestBody SongChatRequest request
    ) {
        return aiResearchService.chatWithSong(id, request);
    }
}
