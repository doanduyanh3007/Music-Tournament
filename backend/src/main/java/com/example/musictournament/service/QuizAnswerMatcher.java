package com.example.musictournament.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class QuizAnswerMatcher {

    public enum MatchStatus {
        EXACT,    // Đúng hoàn toàn
        CORRECT,  // Đúng qua fuzzy match / alias / base title
        ALMOST,   // Gần đúng rồi! (chưa tính điểm)
        WRONG     // Sai
    }

    public static class MatchResult {
        private final MatchStatus status;
        private final double scoreRatio;
        private final String matchedTarget;
        private final String message;

        public MatchResult(MatchStatus status, double scoreRatio, String matchedTarget, String message) {
            this.status = status;
            this.scoreRatio = scoreRatio;
            this.matchedTarget = matchedTarget;
            this.message = message;
        }

        public MatchStatus getStatus() { return status; }
        public boolean isCorrect() { return status == MatchStatus.EXACT || status == MatchStatus.CORRECT; }
        public boolean isAlmost() { return status == MatchStatus.ALMOST; }
        public double getScoreRatio() { return scoreRatio; }
        public String getMatchedTarget() { return matchedTarget; }
        public String getMessage() { return message; }
    }

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[.,/#!$%^&*;:{}=\\-_`~()\"'?!\\[\\]【】（）「」『』〜~+@*]+");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private static final Set<String> COMMON_SUFFIX_KEYWORDS = Set.of(
            "feat", "ft", "featuring", "with", "tv", "ver", "version", "size",
            "edit", "cover", "remix", "mix", "ost", "op", "ed", "theme",
            "live", "acoustic", "instrumental", "off", "vocal", "mv", "pv", "prod"
    );

    /**
     * Chuẩn hóa sâu văn bản đầu vào:
     * 1. NFKC normalization (chuyển fullwidth sang halfwidth).
     * 2. Chuyển Katakana thành Hiragana để thống nhất bảng chữ cái tiếng Nhật.
     * 3. Chuyển chữ thường (lowercase).
     * 4. Bỏ dấu tiếng Việt & dấu Latin qua NFD.
     * 5. Xóa bỏ dấu câu và ký tự rác.
     * 6. Gộp khoảng trắng.
     */
    public String normalize(String input) {
        if (input == null) return "";
        String text = input.trim();
        if (text.isEmpty()) return "";

        // 1. NFKC (Fullwidth -> Halfwidth)
        text = Normalizer.normalize(text, Normalizer.Form.NFKC);

        // 2. Chuyển Katakana sang Hiragana
        text = katakanaToHiragana(text);

        // 3. Lowercase
        text = text.toLowerCase(Locale.ROOT);

        // 4. Bỏ dấu phụ / diacritics Latin/Vietnamese
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        text = DIACRITICS_PATTERN.matcher(nfd).replaceAll("");
        // Xử lý đ, Đ trong tiếng Việt
        text = text.replace("đ", "d").replace("Đ", "d");

        // 5. Xóa dấu câu
        text = PUNCTUATION_PATTERN.matcher(text).replaceAll(" ");

        // 6. Gộp khoảng trắng
        text = MULTI_SPACE_PATTERN.matcher(text).replaceAll(" ").trim();

        return text;
    }

    /**
     * Chuyển ký tự Katakana (0x30A1-0x30F6) thành Hiragana (0x3041-0x3096)
     */
    public String katakanaToHiragana(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x30A1 && c <= 0x30F6) {
                sb.append((char) (c - 0x60));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Tự động trích xuất các biến thể tên cốt lõi từ tiêu đề bài hát hoặc nghệ sĩ:
     * - Bỏ feat / ft / featuring (ví dụ: "Bad Apple!! feat.SEKAI" -> "Bad Apple!!")
     * - Bỏ thông tin phụ trong ngoặc: "(TV Size)", "[Cover]", "(Remix)"
     * - Trích xuất tên phụ hoặc tên tiếng Anh trong ngoặc
     * - Tách từng nghệ sĩ nếu có dấu phân cách (, ; /)
     */
    public Set<String> generateTargetVariants(String target) {
        Set<String> variants = new LinkedHashSet<>();
        if (target == null || target.isBlank()) return variants;

        String raw = target.trim();
        variants.add(raw);

        // 1. Bỏ feat / ft / featuring ở cuối hoặc sau tên bài
        // Hỗ trợ "Bad Apple!! feat.SEKAI", "Song (feat. Artist)", "Song ft. Artist"
        String noFeat = raw.replaceAll("(?i)\\s*[\\[\\(]?\\s*(?:feat\\.?|ft\\.?|featuring|with)\\s*[^\\)\\]]+[\\)\\]]?", "").trim();
        if (!noFeat.isEmpty() && !noFeat.equalsIgnoreCase(raw)) {
            variants.add(noFeat);
        }
        String noFeatTrailing = raw.replaceAll("(?i)(?:[\\s\\(\\[\\{]|^)(?:feat\\.?|ft\\.?|featuring|with)\\s*.*$", "").trim();
        if (!noFeatTrailing.isEmpty() && !noFeatTrailing.equalsIgnoreCase(raw)) {
            variants.add(noFeatTrailing);
        }

        // 2. Bỏ toàn bộ nội dung trong các cặp ngoặc: (), [], 【】, （）, 「」, 『』
        String noBrackets = raw.replaceAll("[\\[\\(（【「『][^\\]\\)）】」』]*[\\]\\)）】」』]", "").trim();
        if (!noBrackets.isEmpty() && !noBrackets.equalsIgnoreCase(raw)) {
            variants.add(noBrackets);
            String nbNoFeat = noBrackets.replaceAll("(?i)(?:[\\s\\(\\[\\{]|^)(?:feat\\.?|ft\\.?|featuring|with)\\s*.*$", "").trim();
            if (!nbNoFeat.isEmpty()) {
                variants.add(nbNoFeat);
            }
        }

        // 3. Tách tiêu đề trước dấu gạch ngang " - " hoặc " ~ " hoặc " : "
        if (raw.contains(" - ") || raw.contains(" ~ ") || raw.contains(" : ")) {
            String prefix = raw.split("\\s+[-~:]\\s+")[0].trim();
            if (!prefix.isEmpty() && prefix.length() >= 2) {
                variants.add(prefix);
            }
        }

        // 4. Trích xuất tên thay thế nằm bên trong ngoặc (nếu là tên bài hát song ngữ)
        // Ví dụ: "夜に駆ける (Racing Into The Night)" -> thêm "Racing Into The Night"
        var bracketMatcher = Pattern.compile("[\\[\\(（【「『]([^\\]\\)）】」』]+)[\\]\\)）】」』]").matcher(raw);
        while (bracketMatcher.find()) {
            String inside = bracketMatcher.group(1).trim();
            if (inside.length() >= 2 && !inside.matches("(?i)^(tv size|tv ver|full|ost|mv|pv|cover|remix|feat.*|ft.*|instrumental|audio|video|lyrics?)$")) {
                variants.add(inside);
            }
        }

        // 5. Nếu target là nghệ sĩ có nhiều người (phân cách bởi dấu ; , 、 ； /)
        if (raw.contains(";") || raw.contains(",") || raw.contains("、") || raw.contains("；") || raw.contains("/")) {
            String[] parts = raw.split("[,;、；/]+");
            for (String p : parts) {
                String t = p.trim();
                // Bỏ (CV:...) nếu có
                String cleanVoice = t.replaceAll("(?i)\\s*[\\[\\(]cv:[^\\)\\]]+[\\)\\]]", "").trim();
                if (!cleanVoice.isEmpty()) variants.add(cleanVoice);
                if (!t.isEmpty()) variants.add(t);
            }
        }

        return variants;
    }

    /**
     * Kiểm tra xem chuỗi user có phải là base title của candidate khi candidate có đuôi feat/version/remix
     */
    private boolean isBaseTitleMatch(String normUser, String normCandidate) {
        if (normUser.isEmpty() || normCandidate.isEmpty()) return false;
        if (normCandidate.startsWith(normUser + " ")) {
            String remainder = normCandidate.substring(normUser.length() + 1).trim();
            String firstWord = remainder.split("\\s+")[0];
            if (COMMON_SUFFIX_KEYWORDS.contains(firstWord)) {
                return true;
            }
        }
        if (normUser.startsWith(normCandidate + " ")) {
            String remainder = normUser.substring(normCandidate.length() + 1).trim();
            String firstWord = remainder.split("\\s+")[0];
            if (COMMON_SUFFIX_KEYWORDS.contains(firstWord)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Chấm câu trả lời của người chơi với mục tiêu (Tên bài hoặc Nghệ sĩ) và các aliases.
     */
    public MatchResult matchAnswer(String userInput, String officialTarget, String rawAliases) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return new MatchResult(MatchStatus.WRONG, 0.0, null, "Chưa nhập đáp án");
        }

        String normUser = normalize(userInput);
        if (normUser.isEmpty()) {
            return new MatchResult(MatchStatus.WRONG, 0.0, null, "Đáp án không hợp lệ");
        }

        // Tạo danh sách các target hợp lệ (Official + Variants tự động trích xuất + Aliases)
        Set<String> candidates = new LinkedHashSet<>();
        if (officialTarget != null && !officialTarget.isBlank()) {
            candidates.addAll(generateTargetVariants(officialTarget));
        }

        if (rawAliases != null && !rawAliases.isBlank()) {
            String[] parts = rawAliases.split("[,;|\n]+");
            for (String p : parts) {
                String trimmed = p.trim().replaceAll("^[\"']+|[\"']+$", "").trim();
                if (!trimmed.isEmpty()) {
                    candidates.addAll(generateTargetVariants(trimmed));
                }
            }
        }

        boolean hasAlmost = false;
        double bestSimilarity = 0.0;
        String bestTarget = null;

        for (String candidate : candidates) {
            String normCandidate = normalize(candidate);
            if (normCandidate.isEmpty()) continue;

            // 1. So khớp chính xác tuyệt đối sau chuẩn hóa
            if (normUser.equals(normCandidate)) {
                return new MatchResult(MatchStatus.EXACT, 1.0, candidate, "Chính xác tuyệt đối!");
            }

            // 2. So khớp Base Title khi có đuôi feat/remix/ver
            if (isBaseTitleMatch(normUser, normCandidate)) {
                return new MatchResult(MatchStatus.CORRECT, 0.98, candidate, "Chính xác!");
            }

            // 3. Kiểm tra fuzzy match với quy tắc khoảng cách
            int userLen = normUser.length();
            int candLen = normCandidate.length();
            int maxLen = Math.max(userLen, candLen);

            int dist = levenshteinDistance(normUser, normCandidate);
            double sim = 1.0 - ((double) dist / maxLen);

            if (sim > bestSimilarity) {
                bestSimilarity = sim;
                bestTarget = candidate;
            }

            // QUY TẮC PHÂN LOẠI ĐỘ DÀI:
            // Tên <= 4 ký tự: Bắt buộc exact 100% (không cho phép distance > 0)
            if (candLen <= 4) {
                if (normUser.equals(normCandidate)) {
                    return new MatchResult(MatchStatus.EXACT, 1.0, candidate, "Chính xác!");
                }
                if (dist == 1 && candLen >= 3) {
                    hasAlmost = true;
                }
            }
            // Tên 5-8 ký tự: Cho phép sai 1 ký tự
            else if (candLen <= 8) {
                if (dist <= 1 || sim >= 0.88) {
                    return new MatchResult(MatchStatus.CORRECT, sim, candidate, "Chính xác!");
                }
                if (dist == 2 || (sim >= 0.70 && sim < 0.88)) {
                    hasAlmost = true;
                }
            }
            // Tên dài > 8 ký tự: Cho phép sai tối đa 2 ký tự hoặc độ tương đồng >= 82%
            else {
                if (dist <= 2 || sim >= 0.82) {
                    return new MatchResult(MatchStatus.CORRECT, sim, candidate, "Chính xác!");
                }
                if (dist <= 3 || (sim >= 0.68 && sim < 0.82)) {
                    hasAlmost = true;
                }
            }
        }

        // Nếu rơi vào khoảng "Gần đúng rồi!"
        if (hasAlmost || bestSimilarity >= 0.70) {
            return new MatchResult(MatchStatus.ALMOST, bestSimilarity, bestTarget, "Gần đúng rồi! Hãy kiểm tra lại lỗi chính tả.");
        }

        return new MatchResult(MatchStatus.WRONG, bestSimilarity, null, "Chưa đúng, hãy thử lại!");
    }

    /**
     * Tính khoảng cách Levenshtein giữa 2 chuỗi
     */
    public int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];
        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[len1][len2];
    }
}
