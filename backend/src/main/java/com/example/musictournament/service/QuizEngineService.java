package com.example.musictournament.service;

import com.example.musictournament.model.QuizPool;
import com.example.musictournament.model.QuizPoolSong;
import com.example.musictournament.model.QuizSong;
import com.example.musictournament.repo.QuizPoolRepository;
import com.example.musictournament.repo.QuizPoolSongRepository;
import com.example.musictournament.repo.QuizSongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuizEngineService {

    private final QuizPoolRepository poolRepository;
    private final QuizPoolSongRepository poolSongRepository;
    private final QuizSongRepository songRepository;
    private final QuizAnswerMatcher answerMatcher;

    // In-memory active game sessions
    private final Map<String, ActiveQuizSession> sessions = new ConcurrentHashMap<>();

    public QuizEngineService(
            QuizPoolRepository poolRepository,
            QuizPoolSongRepository poolSongRepository,
            QuizSongRepository songRepository,
            QuizAnswerMatcher answerMatcher
    ) {
        this.poolRepository = poolRepository;
        this.poolSongRepository = poolSongRepository;
        this.songRepository = songRepository;
        this.answerMatcher = answerMatcher;
    }

    // ==========================================
    // POOL MANAGEMENT
    // ==========================================

    public List<QuizPool> getActivePools() {
        return poolRepository.findByActiveTrueAndIsPublicTrueOrderByCreatedAtDesc();
    }

    public List<QuizPool> getAllPools() {
        return poolRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<QuizPool> getPoolById(Long id) {
        return poolRepository.findById(id);
    }

    @Transactional
    public QuizPool createPool(QuizPool pool) {
        if (pool.getName() == null || pool.getName().isBlank()) {
            throw new IllegalArgumentException("Tên Pool không được để trống");
        }
        if (pool.getCode() == null || pool.getCode().isBlank()) {
            String slug = pool.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            pool.setCode(slug.isEmpty() ? "pool-" + System.currentTimeMillis() : slug);
        }
        return poolRepository.save(pool);
    }

    @Transactional
    public QuizPool updatePool(Long id, QuizPool updated) {
        QuizPool existing = poolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Mystery Pool ID: " + id));

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setCoverUrl(updated.getCoverUrl());
        if (updated.getIsPublic() != null) existing.setIsPublic(updated.getIsPublic());
        if (updated.getActive() != null) existing.setActive(updated.getActive());

        return poolRepository.save(existing);
    }

    @Transactional
    public void deletePool(Long id) {
        poolSongRepository.deleteByPoolId(id);
        poolRepository.deleteById(id);
    }

    @Transactional
    public Map<String, Object> addSongsToPool(Long poolId, List<Long> songIds) {
        QuizPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Mystery Pool ID: " + poolId));

        int added = 0;
        for (Long songId : songIds) {
            if (poolSongRepository.findByPoolIdAndSongId(poolId, songId).isEmpty()) {
                poolSongRepository.save(new QuizPoolSong(poolId, songId));
                added++;
            }
        }

        long total = poolSongRepository.countByPoolId(poolId);
        pool.setSongCount((int) total);
        poolRepository.save(pool);

        return Map.of("poolId", poolId, "added", added, "totalSongs", total);
    }

    @Transactional
    public void removeSongFromPool(Long poolId, Long songId) {
        poolSongRepository.deleteByPoolIdAndSongId(poolId, songId);
        long total = poolSongRepository.countByPoolId(poolId);
        poolRepository.findById(poolId).ifPresent(p -> {
            p.setSongCount((int) total);
            poolRepository.save(p);
        });
    }

    // ==========================================
    // GAME ENGINE & SESSION MANAGEMENT
    // ==========================================

    public static class ActiveQuizSession {
        public String sessionId;
        public String mode; // "ENDLESS", "TEN_QUESTIONS", "DAILY_CHALLENGE", "BLIND_RANDOM", "CATEGORY"
        public String targetType; // "TITLE", "ARTIST", "BOTH"
        public Long poolId;
        public String genre;
        public Integer difficultyTier; // 1: Easy, 2: Normal, 3: Hard, 4: Expert, 5: Master, null: All
        public int totalQuestions;
        public int currentQuestionIndex = 0;
        public int timeLimitPerQuestion = 20; // seconds
        public int score = 0;
        public int streak = 0;
        public int maxStreak = 0;
        public int correctCount = 0;
        public int totalHintsUsed = 0;
        public boolean isFinished = false;

        // Current Active Question (Protected / Hidden from client)
        public CurrentQuestion currentQuestion;
        public Set<Long> servedSongIds = new HashSet<>();
        public LocalDateTime questionStartedAt;
    }

    public static class CurrentQuestion {
        public String questionId;
        public QuizSong song;
        public Set<String> usedHintTypes = new HashSet<>();
        public boolean answered = false;
        public long startedTimestampMs;
    }

    public Map<String, Object> startSession(
            String mode,
            String targetType,
            Long poolId,
            String genre,
            Integer difficultyTier,
            Integer questionCount,
            Integer timeLimit
    ) {
        ActiveQuizSession session = new ActiveQuizSession();
        session.sessionId = UUID.randomUUID().toString();
        session.mode = (mode != null && !mode.isBlank()) ? mode.toUpperCase() : "TEN_QUESTIONS";
        session.targetType = (targetType != null && !targetType.isBlank()) ? targetType.toUpperCase() : "TITLE";
        session.poolId = poolId;
        session.genre = genre;
        session.difficultyTier = difficultyTier;
        session.timeLimitPerQuestion = (timeLimit != null && timeLimit >= 5) ? timeLimit : 20;

        if ("TEN_QUESTIONS".equals(session.mode)) {
            session.totalQuestions = 10;
        } else if ("DAILY_CHALLENGE".equals(session.mode)) {
            session.totalQuestions = 5;
        } else if ("ENDLESS".equals(session.mode)) {
            session.totalQuestions = 999;
        } else {
            session.totalQuestions = (questionCount != null && questionCount > 0) ? questionCount : 10;
        }

        sessions.put(session.sessionId, session);

        // Fetch first question
        Map<String, Object> firstQuestion = generateNextQuestion(session);
        return Map.of(
                "sessionId", session.sessionId,
                "mode", session.mode,
                "targetType", session.targetType,
                "totalQuestions", session.totalQuestions,
                "timeLimit", session.timeLimitPerQuestion,
                "question", firstQuestion
        );
    }

    public Map<String, Object> getNextQuestion(String sessionId) {
        ActiveQuizSession session = sessions.get(sessionId);
        if (session == null || session.isFinished) {
            throw new IllegalArgumentException("Lượt chơi không tồn tại hoặc đã kết thúc");
        }
        return generateNextQuestion(session);
    }

    private synchronized Map<String, Object> generateNextQuestion(ActiveQuizSession session) {
        if (session.currentQuestionIndex >= session.totalQuestions) {
            session.isFinished = true;
            return Map.of("isFinished", true, "summary", getSessionSummary(session));
        }

        QuizSong picked = pickRandomSongForSession(session);
        if (picked == null) {
            // No more songs available matching criteria
            session.isFinished = true;
            return Map.of("isFinished", true, "summary", getSessionSummary(session), "reason", "Hết bài hát trong kho phù hợp");
        }

        session.servedSongIds.add(picked.getId());
        session.currentQuestionIndex++;

        CurrentQuestion q = new CurrentQuestion();
        q.questionId = UUID.randomUUID().toString();
        q.song = picked;
        q.startedTimestampMs = System.currentTimeMillis();
        session.currentQuestion = q;
        session.questionStartedAt = LocalDateTime.now();

        boolean isBlind = "BLIND_RANDOM".equalsIgnoreCase(session.mode);

        // SECURE PAYLOAD: ABSOLUTELY NO SONG TITLE, ARTIST, OR ALIASES LEAKED!
        Map<String, Object> clientPayload = new HashMap<>();
        clientPayload.put("questionId", q.questionId);
        clientPayload.put("questionNumber", session.currentQuestionIndex);
        clientPayload.put("totalQuestions", session.totalQuestions);
        clientPayload.put("timeLimit", session.timeLimitPerQuestion);
        clientPayload.put("targetType", session.targetType);
        clientPayload.put("isBlind", isBlind);
        clientPayload.put("audioUrl", picked.getPreviewUrl() != null && !picked.getPreviewUrl().isBlank() ? picked.getPreviewUrl() : picked.getYoutubeUrl());
        clientPayload.put("youtubeUrl", picked.getYoutubeUrl());
        clientPayload.put("spotifyUrl", picked.getSpotifyUrl());
        clientPayload.put("artworkUrl", isBlind ? null : picked.getArtworkUrl());
        clientPayload.put("genre", picked.getGenre());
        clientPayload.put("difficultyTier", picked.getDifficultyTier());

        return clientPayload;
    }

    private QuizSong pickRandomSongForSession(ActiveQuizSession session) {
        List<QuizSong> poolSongs;
        if (session.poolId != null) {
            poolSongs = poolSongRepository.findRandomSongsInPool(session.poolId, 15);
        } else if (session.difficultyTier != null) {
            int minD = (session.difficultyTier - 1) * 20;
            int maxD = session.difficultyTier == 5 ? 100 : session.difficultyTier * 20;
            poolSongs = songRepository.findRandomSongsByDifficulty(minD, maxD, 15);
        } else {
            poolSongs = songRepository.findRandomSongs(15);
        }

        for (QuizSong s : poolSongs) {
            if (!session.servedSongIds.contains(s.getId())) {
                return s;
            }
        }
        return poolSongs.isEmpty() ? null : poolSongs.get(0);
    }

    public synchronized Map<String, Object> submitAnswer(
            String sessionId,
            String questionId,
            String answer,
            Integer timeTakenMs
    ) {
        ActiveQuizSession session = sessions.get(sessionId);
        if (session == null || session.isFinished) {
            throw new IllegalArgumentException("Phiên chơi không hợp lệ hoặc đã kết thúc");
        }

        CurrentQuestion currentQ = session.currentQuestion;
        if (currentQ == null || !currentQ.questionId.equals(questionId)) {
            throw new IllegalArgumentException("Câu hỏi đã hết hạn hoặc không khớp");
        }

        QuizSong song = currentQ.song;
        String target = "ARTIST".equalsIgnoreCase(session.targetType) ? song.getArtist() : song.getTitle();
        String aliases = "ARTIST".equalsIgnoreCase(session.targetType) ? null : song.getAliases();

        QuizAnswerMatcher.MatchResult matchResult = answerMatcher.matchAnswer(answer, target, aliases);

        Map<String, Object> res = new HashMap<>();
        res.put("status", matchResult.getStatus().name());
        res.put("message", matchResult.getMessage());

        if (matchResult.isAlmost()) {
            // "Gần đúng rồi!" -> User can retry, no points awarded yet, round is NOT marked answered
            res.put("canRetry", true);
            return res;
        }

        currentQ.answered = true;

        if (matchResult.isCorrect()) {
            int basePoints = 100;

            // Speed bonus: max 50 points if answered within time limit
            int totalMs = session.timeLimitPerQuestion * 1000;
            int takenMs = timeTakenMs != null ? Math.max(0, Math.min(totalMs, timeTakenMs)) : totalMs / 2;
            int remainingMs = Math.max(0, totalMs - takenMs);
            int speedBonus = (int) Math.round(((double) remainingMs / totalMs) * 50);

            // Hint penalty: -25 points per hint used
            int hintPenalty = currentQ.usedHintTypes.size() * 25;

            // Streak bonus
            session.streak++;
            if (session.streak > session.maxStreak) session.maxStreak = session.streak;
            int streakBonus = Math.min(session.streak * 10, 50);

            int earned = Math.max(10, basePoints + speedBonus + streakBonus - hintPenalty);
            session.score += earned;
            session.correctCount++;

            res.put("earnedPoints", earned);
            res.put("basePoints", basePoints);
            res.put("speedBonus", speedBonus);
            res.put("streakBonus", streakBonus);
            res.put("hintPenalty", hintPenalty);
            res.put("currentScore", session.score);
            res.put("currentStreak", session.streak);
        } else {
            session.streak = 0;
            res.put("earnedPoints", 0);
            res.put("currentScore", session.score);
            res.put("currentStreak", 0);
        }

        // REVEAL OFFICIAL ANSWER ONCE ANSWERED:
        res.put("officialTitle", song.getTitle());
        res.put("officialArtist", song.getArtist());
        res.put("romajiTitle", resolveRomaji(song));
        res.put("officialArtwork", song.getArtworkUrl());
        res.put("officialYoutubeUrl", song.getYoutubeUrl());
        res.put("audioUrl", song.getPreviewUrl() != null && !song.getPreviewUrl().isBlank() ? song.getPreviewUrl() : song.getYoutubeUrl());
        res.put("aliases", song.getAliases());

        boolean isFirstBlood = matchResult.isCorrect() && (timeTakenMs != null && timeTakenMs <= 6000) && currentQ.usedHintTypes.isEmpty();
        res.put("firstBlood", isFirstBlood);

        // Check if finished
        if (session.currentQuestionIndex >= session.totalQuestions) {
            session.isFinished = true;
            res.put("isFinished", true);
            res.put("summary", getSessionSummary(session));
        } else {
            res.put("isFinished", false);
        }

        return res;
    }

    public synchronized Map<String, Object> requestHint(String sessionId, String questionId, String hintType) {
        ActiveQuizSession session = sessions.get(sessionId);
        if (session == null || session.currentQuestion == null || !session.currentQuestion.questionId.equals(questionId)) {
            throw new IllegalArgumentException("Không tìm thấy câu hỏi");
        }

        CurrentQuestion q = session.currentQuestion;
        QuizSong song = q.song;
        q.usedHintTypes.add(hintType);
        session.totalHintsUsed++;

        String hintText;
        String target = "ARTIST".equalsIgnoreCase(session.targetType) ? song.getArtist() : song.getTitle();

        switch (hintType.toUpperCase()) {
            case "FIRST_LETTER":
                hintText = "Ký tự đầu tiên: " + (target.isEmpty() ? "?" : String.valueOf(target.charAt(0)).toUpperCase());
                break;
            case "LENGTH_INFO":
                String[] words = target.trim().split("\\s+");
                hintText = "Độ dài: " + words.length + " từ (" + target.length() + " ký tự)";
                break;
            case "ARTIST_OR_GENRE":
                if ("TITLE".equalsIgnoreCase(session.targetType)) {
                    hintText = "Nghệ sĩ thể hiện: " + song.getArtist();
                } else {
                    hintText = "Thể loại: " + song.getGenre();
                }
                break;
            case "AUDIO_MORE":
                hintText = "Đã mở khóa toàn bộ đoạn trích âm thanh!";
                break;
            default:
                hintText = "Gợi ý thể loại: " + song.getGenre();
                break;
        }

        return Map.of(
                "hintType", hintType,
                "hintText", hintText,
                "totalHintsUsedInQuestion", q.usedHintTypes.size()
        );
    }

    public Map<String, Object> skipQuestion(String sessionId, String questionId) {
        ActiveQuizSession session = sessions.get(sessionId);
        if (session == null || session.currentQuestion == null) {
            throw new IllegalArgumentException("Phiên không tồn tại");
        }
        CurrentQuestion q = session.currentQuestion;
        q.answered = true;
        session.streak = 0;

        Map<String, Object> res = new HashMap<>();
        res.put("status", "SKIPPED");
        res.put("earnedPoints", 0);
        res.put("currentScore", session.score);
        res.put("currentStreak", 0);
        res.put("officialTitle", q.song.getTitle());
        res.put("officialArtist", q.song.getArtist());
        res.put("romajiTitle", resolveRomaji(q.song));
        res.put("officialArtwork", q.song.getArtworkUrl());
        res.put("officialYoutubeUrl", q.song.getYoutubeUrl());
        res.put("audioUrl", q.song.getPreviewUrl() != null && !q.song.getPreviewUrl().isBlank() ? q.song.getPreviewUrl() : q.song.getYoutubeUrl());
        res.put("aliases", q.song.getAliases());

        if (session.currentQuestionIndex >= session.totalQuestions) {
            session.isFinished = true;
            res.put("isFinished", true);
            res.put("summary", getSessionSummary(session));
        } else {
            res.put("isFinished", false);
        }
        return res;
    }

    private Map<String, Object> getSessionSummary(ActiveQuizSession s) {
        return Map.of(
                "totalQuestions", s.totalQuestions,
                "answeredQuestions", s.currentQuestionIndex,
                "correctCount", s.correctCount,
                "accuracyPercent", s.currentQuestionIndex > 0 ? (int) Math.round(((double) s.correctCount / s.currentQuestionIndex) * 100) : 0,
                "finalScore", s.score,
                "maxStreak", s.maxStreak,
                "hintsUsed", s.totalHintsUsed
        );
    }

    private String resolveRomaji(QuizSong song) {
        if (song == null) return "";
        return JapaneseRomanizer.resolveRomaji(song.getTitle(), song.getRomajiTitle());
    }
}
