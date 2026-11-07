package com.example.musictournament.service;

import com.example.musictournament.model.QuizSong;
import com.example.musictournament.repo.QuizPoolSongRepository;
import com.example.musictournament.repo.QuizSongRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class QuizMultiplayerService {

    private final QuizSongRepository songRepository;
    private final QuizPoolSongRepository poolSongRepository;
    private final QuizAnswerMatcher answerMatcher;

    // In-memory active multiplayer rooms: roomCode -> QuizMultiplayerRoom
    private final Map<String, QuizMultiplayerRoom> rooms = new ConcurrentHashMap<>();

    // Rate limiting tracker: playerKey -> lastAnswerTimestampMs
    private final Map<String, Long> rateLimitTracker = new ConcurrentHashMap<>();

    public QuizMultiplayerService(
            QuizSongRepository songRepository,
            QuizPoolSongRepository poolSongRepository,
            QuizAnswerMatcher answerMatcher
    ) {
        this.songRepository = songRepository;
        this.poolSongRepository = poolSongRepository;
        this.answerMatcher = answerMatcher;
    }

    public static class RoomPlayer {
        public String playerKey;
        public String displayName;
        public boolean isHost = false;
        public boolean isReady = false;
        public int score = 0;
        public int streak = 0;
        public int maxStreak = 0;
        public int firstBloodCount = 0;
        public int correctCount = 0;
        public int hintsUsed = 0;

        // Current round temporary state
        public boolean hasAnswered = false;
        public boolean isCorrect = false;
        public boolean gotFirstBlood = false;
        public int roundPointsEarned = 0;
        public int roundSpeedBonus = 0;
        public long answerTimeMs = 0;
    }

    public static class CurrentMultiplayerQuestion {
        public String questionId;
        public QuizSong song;
        public long startedTimestampMs;
        public AtomicBoolean firstBloodAwarded = new AtomicBoolean(false);
        public String firstBloodPlayerKey = null;
        public AtomicInteger correctCount = new AtomicInteger(0);
        public Map<String, Integer> hintsUsedByPlayer = new ConcurrentHashMap<>();
    }

    public static class QuizMultiplayerRoom {
        public String roomCode;
        public String roomName;
        public String hostPlayerKey;
        public String status = "LOBBY"; // "LOBBY", "PLAYING", "ROUND_RESULT", "GAME_OVER"
        public String mode = "TEN_QUESTIONS";
        public String targetType = "TITLE"; // "TITLE", "ARTIST"
        public int timeLimit = 20; // seconds
        public int questionCount = 10;
        public Long poolId;
        public Integer difficultyTier;

        public Map<String, RoomPlayer> players = new ConcurrentHashMap<>();
        public List<QuizSong> preparedSongs = new ArrayList<>();
        public int currentQuestionIndex = 0;
        public CurrentMultiplayerQuestion currentQuestion;
        public long lastActivityTimestamp = System.currentTimeMillis();

        // Synchronized Intermission & 3-Second Countdown fields
        public long intermissionStartedAtMs = 0;
        public int intermissionDurationSeconds = 15;
        public long countdownStartedAtMs = 0;
        public int countdownDurationSeconds = 3;
    }

    // ==========================================
    // ROOM LIFECYCLE
    // ==========================================

    public Map<String, Object> createRoom(
            String hostPlayerKey,
            String hostDisplayName,
            String roomName,
            String mode,
            String targetType,
            Integer timeLimit,
            Integer questionCount,
            Long poolId,
            Integer difficultyTier
    ) {
        String code = generateRoomCode();
        QuizMultiplayerRoom room = new QuizMultiplayerRoom();
        room.roomCode = code;
        room.roomName = (roomName != null && !roomName.isBlank()) ? roomName.trim() : "Phòng Quiz #" + code;
        room.hostPlayerKey = hostPlayerKey;
        room.mode = (mode != null && !mode.isBlank()) ? mode : "TEN_QUESTIONS";
        room.targetType = (targetType != null && !targetType.isBlank()) ? targetType : "TITLE";
        room.timeLimit = (timeLimit != null && timeLimit >= 10) ? timeLimit : 20;
        room.questionCount = (questionCount != null && questionCount > 0) ? Math.min(questionCount, 30) : 10;
        room.poolId = poolId;
        room.difficultyTier = difficultyTier;

        RoomPlayer host = new RoomPlayer();
        host.playerKey = hostPlayerKey;
        host.displayName = (hostDisplayName != null && !hostDisplayName.isBlank()) ? hostDisplayName.trim() : "Host";
        host.isHost = true;
        host.isReady = true;
        room.players.put(hostPlayerKey, host);

        rooms.put(code, room);
        return getRoomState(code, hostPlayerKey);
    }

    public Map<String, Object> joinRoom(String roomCode, String playerKey, String displayName) {
        QuizMultiplayerRoom room = rooms.get(roomCode.toUpperCase().trim());
        if (room == null) {
            throw new IllegalArgumentException("Không tìm thấy phòng chơi với mã: " + roomCode);
        }
        if (!"LOBBY".equals(room.status)) {
            // Reconnecting existing player
            if (room.players.containsKey(playerKey)) {
                return getRoomState(room.roomCode, playerKey);
            }
            throw new IllegalStateException("Phòng đấu đã bắt đầu, không thể tham gia lúc này");
        }

        RoomPlayer player = room.players.computeIfAbsent(playerKey, k -> {
            RoomPlayer p = new RoomPlayer();
            p.playerKey = playerKey;
            return p;
        });
        player.displayName = (displayName != null && !displayName.isBlank()) ? displayName.trim() : "Người chơi " + (room.players.size());
        room.lastActivityTimestamp = System.currentTimeMillis();

        return getRoomState(room.roomCode, playerKey);
    }

    public Map<String, Object> toggleReady(String roomCode, String playerKey) {
        QuizMultiplayerRoom room = getValidRoom(roomCode);
        RoomPlayer player = room.players.get(playerKey);
        if (player != null && !player.isHost) {
            player.isReady = !player.isReady;
        }
        return getRoomState(roomCode, playerKey);
    }

    public Map<String, Object> startRoomGame(String roomCode, String hostPlayerKey) {
        QuizMultiplayerRoom room = getValidRoom(roomCode);
        if (!room.hostPlayerKey.equals(hostPlayerKey)) {
            throw new IllegalArgumentException("Chỉ chủ phòng mới có quyền bắt đầu trận đấu");
        }

        // Prepare songs for game
        List<QuizSong> songs;
        if (room.poolId != null) {
            songs = poolSongRepository.findRandomSongsInPool(room.poolId, room.questionCount);
        } else if (room.difficultyTier != null) {
            int minD = (room.difficultyTier - 1) * 20;
            int maxD = room.difficultyTier == 5 ? 100 : room.difficultyTier * 20;
            songs = songRepository.findRandomSongsByDifficulty(minD, maxD, room.questionCount);
        } else {
            songs = songRepository.findRandomSongs(room.questionCount);
        }

        if (songs.isEmpty()) {
            throw new IllegalStateException("Không có bài hát nào trong kho phù hợp với cài đặt phòng");
        }

        room.preparedSongs = songs;
        room.currentQuestionIndex = 0;
        advanceToNextQuestion(room);

        return getRoomState(roomCode, hostPlayerKey);
    }

    private synchronized void advanceToNextQuestion(QuizMultiplayerRoom room) {
        if (room.currentQuestionIndex >= room.preparedSongs.size()) {
            room.status = "GAME_OVER";
            return;
        }

        QuizSong song = room.preparedSongs.get(room.currentQuestionIndex);
        CurrentMultiplayerQuestion q = new CurrentMultiplayerQuestion();
        q.questionId = UUID.randomUUID().toString();
        q.song = song;
        q.startedTimestampMs = System.currentTimeMillis();
        room.currentQuestion = q;
        room.currentQuestionIndex++;
        room.status = "PLAYING";

        // Reset per-round player state
        for (RoomPlayer p : room.players.values()) {
            p.hasAnswered = false;
            p.isCorrect = false;
            p.gotFirstBlood = false;
            p.roundPointsEarned = 0;
            p.roundSpeedBonus = 0;
            p.answerTimeMs = 0;
        }
    }

    // ==========================================
    // ANSWER SUBMISSION & CONCURRENCY CONTROL
    // ==========================================

    public Map<String, Object> submitAnswer(
            String roomCode,
            String playerKey,
            String questionId,
            String answer
    ) {
        QuizMultiplayerRoom room = getValidRoom(roomCode);
        if (!"PLAYING".equals(room.status) || room.currentQuestion == null) {
            throw new IllegalStateException("Câu hỏi hiện không mở nhận câu trả lời");
        }

        CurrentMultiplayerQuestion currentQ = room.currentQuestion;
        if (!currentQ.questionId.equals(questionId)) {
            throw new IllegalArgumentException("Mã câu hỏi không khớp hoặc đã hết hạn");
        }

        // Anti-spam rate limiting: at least 400ms between attempts per player
        long now = System.currentTimeMillis();
        Long lastTime = rateLimitTracker.get(playerKey);
        if (lastTime != null && (now - lastTime < 400)) {
            return Map.of("status", "RATE_LIMITED", "message", "Vui lòng thao tác chậm lại một chút");
        }
        rateLimitTracker.put(playerKey, now);

        RoomPlayer player = room.players.get(playerKey);
        if (player == null) {
            throw new IllegalArgumentException("Người chơi không tồn tại trong phòng");
        }

        if (player.hasAnswered) {
            return Map.of("status", "ALREADY_ANSWERED", "message", "Bạn đã hoàn thành câu hỏi này");
        }

        QuizSong song = currentQ.song;
        String target = "ARTIST".equalsIgnoreCase(room.targetType) ? song.getArtist() : song.getTitle();
        String aliases = "ARTIST".equalsIgnoreCase(room.targetType) ? null : song.getAliases();

        QuizAnswerMatcher.MatchResult matchResult = answerMatcher.matchAnswer(answer, target, aliases);

        // Near match ("Gần đúng rồi!"): player can keep typing!
        if (matchResult.isAlmost()) {
            return Map.of(
                    "status", "ALMOST",
                    "message", "Gần đúng rồi! Hãy kiểm tra lại lỗi chính tả.",
                    "canRetry", true
            );
        }

        if (matchResult.isCorrect()) {
            player.hasAnswered = true;
            player.isCorrect = true;
            long timeTakenMs = now - currentQ.startedTimestampMs;
            player.answerTimeMs = timeTakenMs;
            currentQ.correctCount.incrementAndGet();

            int baseScore = 100;

            // Speed Bonus
            int totalMs = room.timeLimit * 1000;
            int remainingMs = (int) Math.max(0, totalMs - timeTakenMs);
            int speedBonus = (int) Math.round(((double) remainingMs / totalMs) * 50);

            // First Blood: ATOMIC GUARANTEE - only ONE player gets First Blood!
            int firstBloodBonus = 0;
            if (currentQ.firstBloodAwarded.compareAndSet(false, true)) {
                currentQ.firstBloodPlayerKey = playerKey;
                player.gotFirstBlood = true;
                player.firstBloodCount++;
                firstBloodBonus = 50; // Extra 50 pts for First Blood!
            }

            // Hint penalty
            int hintsUsed = currentQ.hintsUsedByPlayer.getOrDefault(playerKey, 0);
            int hintPenalty = hintsUsed * 25;

            // Streak Bonus
            player.streak++;
            if (player.streak > player.maxStreak) player.maxStreak = player.streak;
            int streakBonus = Math.min(player.streak * 10, 50);

            int earned = Math.max(10, baseScore + speedBonus + firstBloodBonus + streakBonus - hintPenalty);
            player.roundPointsEarned = earned;
            player.roundSpeedBonus = speedBonus;
            player.score += earned;
            player.correctCount++;

            // Check if all players answered
            checkIfRoundFinished(room);

            return Map.of(
                    "status", "CORRECT",
                    "message", player.gotFirstBlood ? "FIRST BLOOD! Bạn là người đầu tiên giải được!" : "Chính xác!",
                    "earnedPoints", earned,
                    "firstBlood", player.gotFirstBlood,
                    "speedBonus", speedBonus,
                    "currentScore", player.score
            );
        } else {
            // Wrong answer
            return Map.of(
                    "status", "WRONG",
                    "message", "Chưa đúng, hãy thử lại trước khi hết giờ!",
                    "canRetry", true
            );
        }
    }

    private void checkIfRoundFinished(QuizMultiplayerRoom room) {
        boolean allAnswered = room.players.values().stream().allMatch(p -> p.hasAnswered);
        if (allAnswered && "PLAYING".equals(room.status)) {
            room.status = "ROUND_RESULT";
            room.intermissionStartedAtMs = System.currentTimeMillis();
        }
    }

    public Map<String, Object> advanceRound(String roomCode, String hostPlayerKey) {
        QuizMultiplayerRoom room = getValidRoom(roomCode);
        if (!room.hostPlayerKey.equals(hostPlayerKey)) {
            throw new IllegalArgumentException("Chỉ chủ phòng mới có quyền chuyển câu hỏi");
        }
        if (room.currentQuestionIndex >= room.preparedSongs.size()) {
            room.status = "GAME_OVER";
        } else {
            room.status = "COUNTDOWN";
            room.countdownStartedAtMs = System.currentTimeMillis();
        }
        return getRoomState(roomCode, hostPlayerKey);
    }

    public Map<String, Object> requestHint(String roomCode, String playerKey, String hintType) {
        QuizMultiplayerRoom room = getValidRoom(roomCode);
        if (room.currentQuestion == null) throw new IllegalArgumentException("Không có câu hỏi");

        CurrentMultiplayerQuestion q = room.currentQuestion;
        q.hintsUsedByPlayer.merge(playerKey, 1, Integer::sum);
        RoomPlayer p = room.players.get(playerKey);
        if (p != null) p.hintsUsed++;

        QuizSong song = q.song;
        String target = "ARTIST".equalsIgnoreCase(room.targetType) ? song.getArtist() : song.getTitle();

        String hintText;
        switch (hintType.toUpperCase()) {
            case "FIRST_LETTER":
                hintText = "Ký tự đầu tiên: " + (target.isEmpty() ? "?" : String.valueOf(target.charAt(0)).toUpperCase());
                break;
            case "LENGTH_INFO":
                hintText = "Độ dài: " + target.trim().split("\\s+").length + " từ (" + target.length() + " ký tự)";
                break;
            default:
                hintText = "Nghệ sĩ: " + song.getArtist();
                break;
        }

        return Map.of("hintType", hintType, "hintText", hintText);
    }

    // ==========================================
    // SECURE ROOM STATE SERIALIZATION
    // ==========================================

    public Map<String, Object> getRoomState(String roomCode, String viewerPlayerKey) {
        QuizMultiplayerRoom room = getValidRoom(roomCode);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("roomCode", room.roomCode);
        state.put("roomName", room.roomName);
        state.put("hostPlayerKey", room.hostPlayerKey);
        state.put("isHost", room.hostPlayerKey.equals(viewerPlayerKey));
        state.put("status", room.status);
        state.put("mode", room.mode);
        state.put("targetType", room.targetType);
        state.put("timeLimit", room.timeLimit);
        state.put("currentQuestionIndex", room.currentQuestionIndex);
        state.put("totalQuestions", room.questionCount);

        // Player Leaderboard / Public List
        List<Map<String, Object>> playersList = new ArrayList<>();
        for (RoomPlayer p : room.players.values()) {
            Map<String, Object> pMap = new LinkedHashMap<>();
            pMap.put("playerKey", p.playerKey);
            pMap.put("displayName", p.displayName);
            pMap.put("isHost", p.isHost);
            pMap.put("isReady", p.isReady);
            pMap.put("score", p.score);
            pMap.put("streak", p.streak);
            pMap.put("firstBloodCount", p.firstBloodCount);
            pMap.put("hasAnswered", p.hasAnswered);

            // In ROUND_RESULT or GAME_OVER, reveal round details
            if ("ROUND_RESULT".equals(room.status) || "GAME_OVER".equals(room.status)) {
                pMap.put("isCorrect", p.isCorrect);
                pMap.put("gotFirstBlood", p.gotFirstBlood);
                pMap.put("roundPointsEarned", p.roundPointsEarned);
                pMap.put("answerTimeMs", p.answerTimeMs);
            }
            playersList.add(pMap);
        }
        // Sort by score descending
        playersList.sort((a, b) -> Integer.compare((int) b.get("score"), (int) a.get("score")));
        state.put("players", playersList);

        if ("COUNTDOWN".equals(room.status)) {
            long now = System.currentTimeMillis();
            long elapsedCountdown = (now - room.countdownStartedAtMs) / 1000;
            int countdownRemaining = (int) Math.max(0, room.countdownDurationSeconds - elapsedCountdown);
            state.put("countdownRemaining", countdownRemaining);

            if (countdownRemaining == 0) {
                advanceToNextQuestion(room);
                state.put("status", "PLAYING");
            }
        }

        // SECURE QUESTION METADATA
        if (room.currentQuestion != null) {
            CurrentMultiplayerQuestion q = room.currentQuestion;
            long now = System.currentTimeMillis();
            long elapsedSeconds = (now - q.startedTimestampMs) / 1000;
            int remainingSeconds = (int) Math.max(0, room.timeLimit - elapsedSeconds);

            // Automatically flip to ROUND_RESULT if timer expired
            if (remainingSeconds == 0 && "PLAYING".equals(room.status)) {
                room.status = "ROUND_RESULT";
                room.intermissionStartedAtMs = System.currentTimeMillis();
                state.put("status", "ROUND_RESULT");
            }

            state.put("questionId", q.questionId);
            state.put("timeRemaining", remainingSeconds);

            // CRITICAL ANSWER SECURITY IN MULTIPLAYER:
            if ("PLAYING".equals(room.status)) {
                // NEVER LEAK TITLE, ARTIST, OR WHO SOLVED IT!
                state.put("audioUrl", q.song.getPreviewUrl() != null && !q.song.getPreviewUrl().isBlank() ? q.song.getPreviewUrl() : q.song.getYoutubeUrl());
                state.put("youtubeUrl", q.song.getYoutubeUrl());
                state.put("genre", q.song.getGenre());
                state.put("difficultyTier", q.song.getDifficultyTier());

                // Anonymized notification: "Có người đã giải được câu hỏi!"
                int solved = q.correctCount.get();
                state.put("someoneSolved", solved > 0);
                state.put("solvedCount", solved);
                if (solved > 0) {
                    state.put("notificationMessage", "Có người đã giải được câu hỏi! (" + solved + "/" + room.players.size() + " người)");
                }
            } else if ("ROUND_RESULT".equals(room.status) || "GAME_OVER".equals(room.status)) {
                // ROUND OVER: Reveal official answer and First Blood recipient!
                state.put("officialTitle", q.song.getTitle());
                state.put("officialArtist", q.song.getArtist());
                state.put("romajiTitle", q.song.getRomajiTitle() != null ? q.song.getRomajiTitle() : JapaneseRomanizer.toRomaji(q.song.getTitle()));
                state.put("officialArtwork", q.song.getArtworkUrl());
                state.put("officialYoutubeUrl", q.song.getYoutubeUrl());
                state.put("audioUrl", q.song.getPreviewUrl() != null && !q.song.getPreviewUrl().isBlank() ? q.song.getPreviewUrl() : q.song.getYoutubeUrl());
                state.put("aliases", q.song.getAliases());

                if (q.firstBloodPlayerKey != null) {
                    RoomPlayer fbPlayer = room.players.get(q.firstBloodPlayerKey);
                    state.put("firstBloodWinner", fbPlayer != null ? fbPlayer.displayName : null);
                }

                if ("ROUND_RESULT".equals(room.status)) {
                    long elapsedIntermission = (now - room.intermissionStartedAtMs) / 1000;
                    int intermissionRemaining = (int) Math.max(0, room.intermissionDurationSeconds - elapsedIntermission);
                    state.put("intermissionRemaining", intermissionRemaining);

                    // Auto-advance to countdown if intermission timeout expires
                    if (room.intermissionStartedAtMs > 0 && intermissionRemaining == 0) {
                        if (room.currentQuestionIndex >= room.preparedSongs.size()) {
                            room.status = "GAME_OVER";
                            state.put("status", "GAME_OVER");
                        } else {
                            room.status = "COUNTDOWN";
                            room.countdownStartedAtMs = System.currentTimeMillis();
                            state.put("status", "COUNTDOWN");
                            state.put("countdownRemaining", 3);
                        }
                    }
                }
            }
        }

        return state;
    }

    private QuizMultiplayerRoom getValidRoom(String roomCode) {
        QuizMultiplayerRoom room = rooms.get(roomCode.toUpperCase().trim());
        if (room == null) {
            throw new IllegalArgumentException("Không tìm thấy phòng chơi với mã: " + roomCode);
        }
        return room;
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
