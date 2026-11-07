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
    public void testFeatAndSubtitleVariations() {
        // "Bad Apple!! feat.SEKAI" should match "bad apple"
        QuizAnswerMatcher.MatchResult r1 = matcher.matchAnswer("bad apple", "Bad Apple!! feat.SEKAI", null);
        assertTrue(r1.isCorrect(), "Expected 'bad apple' to match 'Bad Apple!! feat.SEKAI'");

        QuizAnswerMatcher.MatchResult r2 = matcher.matchAnswer("Bad Apple!!", "Bad Apple!! feat.SEKAI", null);
        assertTrue(r2.isCorrect());

        // 1 typo in bad apple -> should still match base title
        QuizAnswerMatcher.MatchResult r3 = matcher.matchAnswer("bad aple", "Bad Apple!! feat.SEKAI", null);
        assertTrue(r3.isCorrect());

        // TV Size and bracket versions
        QuizAnswerMatcher.MatchResult r4 = matcher.matchAnswer("Shinzou wo Sasageyo", "Shinzou wo Sasageyo! (TV Size)", null);
        assertTrue(r4.isCorrect());

        // Subtitle after dash
        QuizAnswerMatcher.MatchResult r5 = matcher.matchAnswer("Aoi Shiori", "Aoi Shiori - Anime Ver.", null);
        assertTrue(r5.isCorrect());

        // English title in bracket
        QuizAnswerMatcher.MatchResult r6 = matcher.matchAnswer("Racing Into The Night", "夜に駆ける (Racing Into The Night)", null);
        assertTrue(r6.isCorrect());
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
