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

    @Test
    public void testJapaneseRomajiMatching() {
        // "haruhikage" should match "春日影 (MyGO!!!!! ver.)"
        QuizAnswerMatcher.MatchResult r1 = matcher.matchAnswer("haruhikage", "春日影 (MyGO!!!!! ver.)", null);
        assertTrue(r1.isCorrect(), "Expected 'haruhikage' to match '春日影 (MyGO!!!!! ver.)'");

        // With space: "haruhi kage"
        QuizAnswerMatcher.MatchResult r2 = matcher.matchAnswer("haruhi kage", "春日影 (MyGO!!!!! ver.)", null);
        assertTrue(r2.isCorrect());

        // Capitalized: "Haruhikage"
        QuizAnswerMatcher.MatchResult r3 = matcher.matchAnswer("Haruhikage", "春日影 (MyGO!!!!! ver.)", null);
        assertTrue(r3.isCorrect());

        // "refrain" matching "輪符雨"
        QuizAnswerMatcher.MatchResult rRefrain = matcher.matchAnswer("refrain", "輪符雨", null);
        assertTrue(rRefrain.isCorrect(), "Expected 'refrain' to match '輪符雨'");

        // "noroshi" matching "無路矢"
        QuizAnswerMatcher.MatchResult rNoroshi = matcher.matchAnswer("noroshi", "無路矢", null);
        assertTrue(rNoroshi.isCorrect(), "Expected 'noroshi' to match '無路矢'");

        // "mayoiuta" matching "迷星叫"
        QuizAnswerMatcher.MatchResult rMayoi = matcher.matchAnswer("mayoiuta", "迷星叫", null);
        assertTrue(rMayoi.isCorrect(), "Expected 'mayoiuta' to match '迷星叫'");

        // "hitoshizuku" matching "壱雫空"
        QuizAnswerMatcher.MatchResult rHito = matcher.matchAnswer("hitoshizuku", "壱雫空", null);
        assertTrue(rHito.isCorrect(), "Expected 'hitoshizuku' to match '壱雫空'");

        // "sukima" matching "素寄曲"
        QuizAnswerMatcher.MatchResult rSukima = matcher.matchAnswer("sukima", "素寄曲", null);
        assertTrue(rSukima.isCorrect(), "Expected 'sukima' to match '素寄曲'");

        // "line" matching "羅永線"
        QuizAnswerMatcher.MatchResult rLine = matcher.matchAnswer("line", "羅永線", null);
        assertTrue(rLine.isCorrect(), "Expected 'line' to match '羅永線'");

        // "page" matching "残痕宇" & "残痕字"
        QuizAnswerMatcher.MatchResult rPage = matcher.matchAnswer("page", "残痕宇", null);
        assertTrue(rPage.isCorrect(), "Expected 'page' to match '残痕宇'");

        // "speed" matching "歩拾道"
        QuizAnswerMatcher.MatchResult rSpeed = matcher.matchAnswer("speed", "歩拾道", null);
        assertTrue(rSpeed.isCorrect(), "Expected 'speed' to match '歩拾道'");

        // "tanebi" matching "焚音打"
        QuizAnswerMatcher.MatchResult rTanebi = matcher.matchAnswer("tanebi", "焚音打", null);
        assertTrue(rTanebi.isCorrect(), "Expected 'tanebi' to match '焚音打'");

        // Kuromoji IPADIC dynamic romanization on Japanese Kanji
        QuizAnswerMatcher.MatchResult r4 = matcher.matchAnswer("zankyosanka", "残響散歌", null);
        assertTrue(r4.isCorrect());

        QuizAnswerMatcher.MatchResult r5 = matcher.matchAnswer("ao no sumika", "青のすみか", null);
        assertTrue(r5.isCorrect());

        // Katakana loanwords: "ショータイム" accepts BOTH English "showtime" AND Romaji "shōtaimu" / "shotaimu" / "shootaimu"
        QuizAnswerMatcher.MatchResult rEnglish = matcher.matchAnswer("showtime", "ショータイム", null);
        assertTrue(rEnglish.isCorrect(), "Expected 'showtime' to match 'ショータイム'");

        QuizAnswerMatcher.MatchResult rRomajiMacron = matcher.matchAnswer("shōtaimu", "ショータイム", null);
        assertTrue(rRomajiMacron.isCorrect(), "Expected 'shōtaimu' to match 'ショータイム'");

        QuizAnswerMatcher.MatchResult rRomajiSingle = matcher.matchAnswer("shotaimu", "ショータイム", null);
        assertTrue(rRomajiSingle.isCorrect(), "Expected 'shotaimu' to match 'ショータイム'");

        QuizAnswerMatcher.MatchResult rRomajiDouble = matcher.matchAnswer("shootaimu", "ショータイム", null);
        assertTrue(rRomajiDouble.isCorrect(), "Expected 'shootaimu' to match 'ショータイム'");
    }
}
