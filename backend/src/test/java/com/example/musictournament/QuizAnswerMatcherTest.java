package com.example.musictournament;

import com.example.musictournament.service.QuizAnswerMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QuizAnswerMatcherTest {

    private QuizAnswerMatcher matcher;

    @BeforeEach
    public void setup() {
        matcher = new QuizAnswerMatcher();
    }

    @Test
    public void testExactAndNormalizedMatching() {
        // Lowercase, spaces, punctuation
        QuizAnswerMatcher.MatchResult r1 = matcher.matchAnswer("gurenge", "Gurenge", null);
        assertTrue(r1.isCorrect());

        QuizAnswerMatcher.MatchResult r2 = matcher.matchAnswer("  SPARKLE!  ", "Sparkle", null);
        assertTrue(r2.isCorrect());

        // Vietnamese accents removal
        QuizAnswerMatcher.MatchResult r3 = matcher.matchAnswer("Nguoi Ay", "Người Ấy", null);
        assertTrue(r3.isCorrect());
    }

    @Test
    public void testShortTitleStrictness() {
        // Length <= 4 must NOT match different words
        QuizAnswerMatcher.MatchResult r1 = matcher.matchAnswer("An", "Ai", null);
        assertFalse(r1.isCorrect());

        QuizAnswerMatcher.MatchResult r2 = matcher.matchAnswer("Ai", "Ai", null);
        assertTrue(r2.isCorrect());

        QuizAnswerMatcher.MatchResult r3 = matcher.matchAnswer("Oren", "One", null);
        assertFalse(r3.isCorrect());
    }

    @Test
    public void testJapaneseKatakanaAndHiragana() {
        // Katakana input vs Hiragana target
        String hiragana = "ぐれんげ";
        String katakana = "グレンゲ";
        QuizAnswerMatcher.MatchResult r = matcher.matchAnswer(katakana, hiragana, null);
        assertTrue(r.isCorrect());
    }

    @Test
    public void testFuzzyMatchAndAlmostCorrect() {
        // 1 typo in medium word -> Correct
        QuizAnswerMatcher.MatchResult r1 = matcher.matchAnswer("Unraval", "Unravel", null);
        assertTrue(r1.isCorrect());

        // Transposition / near match -> Almost correct
        QuizAnswerMatcher.MatchResult rTrans = matcher.matchAnswer("Unravle", "Unravel", null);
        assertTrue(rTrans.isAlmost());

        // Partial or exact match
        QuizAnswerMatcher.MatchResult r2 = matcher.matchAnswer("Kaikai Kitan", "Kaikai Kitan", null);
        assertTrue(r2.isCorrect());

        // Missing 1 char in long title -> Correct or Almost
        QuizAnswerMatcher.MatchResult r3 = matcher.matchAnswer("Kaikai Kita", "Kaikai Kitan", null);
        assertTrue(r3.isCorrect());
    }

    @Test
    public void testAliasesMatching() {
        String aliases = "紅蓮華, Red Lotus, Kimetsu no Yaiba OP";
        QuizAnswerMatcher.MatchResult r1 = matcher.matchAnswer("Red Lotus", "Gurenge", aliases);
        assertTrue(r1.isCorrect());

        QuizAnswerMatcher.MatchResult r2 = matcher.matchAnswer("Kimetsu no Yaiba OP", "Gurenge", aliases);
        assertTrue(r2.isCorrect());
    }
}
