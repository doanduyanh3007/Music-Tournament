package com.example.musictournament.service;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class JapaneseRomanizer {

    private JapaneseRomanizer() {}

    private static final Tokenizer TOKENIZER = new Tokenizer();

    private static final Map<String, String> KANA_TO_ROMAJI = new LinkedHashMap<>();
    private static final Map<String, String> COMMON_KANJI_DICT = new LinkedHashMap<>();

    static {
        // Yoon (compound kana) & Extended Katakana - Must come first for greedy matching
        String[][] yoon = {
                {"きゃ", "kya"}, {"きゅ", "kyu"}, {"きょ", "kyo"},
                {"しゃ", "sha"}, {"しゅ", "shu"}, {"しょ", "sho"},
                {"ちゃ", "cha"}, {"ちゅ", "chu"}, {"ちょ", "cho"},
                {"にゃ", "nya"}, {"にゅ", "nyu"}, {"にょ", "nyo"},
                {"ひゃ", "hya"}, {"ヒュ", "hyu"}, {"ひょ", "hyo"},
                {"みゃ", "mya"}, {"みゅ", "myu"}, {"みょ", "myo"},
                {"りゃ", "rya"}, {"りゅ", "ryu"}, {"りょ", "ryo"},
                {"ぎゃ", "gya"}, {"ぎゅ", "gyu"}, {"ぎょ", "gyo"},
                {"じゃ", "ja"},  {"じゅ", "ju"},  {"じょ", "jo"},
                {"びゃ", "bya"}, {"びゅ", "byu"}, {"びょ", "byo"},
                {"ぴゃ", "pya"}, {"ぴゅ", "pyu"}, {"ぴょ", "pyo"},
                {"キャ", "kya"}, {"キュ", "kyu"}, {"キョ", "kyo"},
                {"シャ", "sha"}, {"シュ", "shu"}, {"ショ", "sho"},
                {"チャ", "cha"}, {"チュ", "chu"}, {"チョ", "cho"},
                {"ニャ", "nya"}, {"ニュ", "nyu"}, {"ニョ", "nyo"},
                {"ヒャ", "hya"}, {"ヒュ", "hyu"}, {"ヒョ", "hyo"},
                {"ミャ", "mya"}, {"ミュ", "myu"}, {"ミョ", "myo"},
                {"リャ", "rya"}, {"リュ", "ryu"}, {"リョ", "ryo"},
                {"ギャ", "gya"}, {"ギュ", "gyu"}, {"ギョ", "gyo"},
                {"ジャ", "ja"},  {"ジュ", "ju"},  {"ジョ", "jo"},
                {"ビャ", "bya"}, {"ビュ", "byu"}, {"ビョ", "byo"},
                {"ピャ", "pya"}, {"ピュ", "pyu"}, {"ピョ", "pyo"},
                {"ティ", "ti"},  {"ディ", "di"},  {"ファ", "fa"},
                {"フィ", "fi"},  {"フェ", "fe"},  {"フォ", "fo"},
                {"チェ", "che"}, {"ジェ", "je"},  {"シェ", "she"},
                {"ヴァ", "va"},  {"ヴィ", "vi"},  {"ヴ", "vu"},
                {"ヴェ", "ve"},  {"ヴォ", "vo"},
                {"ウィ", "wi"},  {"ウェ", "we"},  {"ウォ", "wo"},
                {"トゥ", "tu"},  {"ドゥ", "du"},
                {"ツァ", "tsa"}, {"ツィ", "tsi"}, {"ツェ", "tse"}, {"ツォ", "tso"},
                {"クァ", "kwa"}, {"グァ", "gwa"}
        };
        for (String[] pair : yoon) {
            KANA_TO_ROMAJI.put(pair[0], pair[1]);
        }

        // Basic & Dakuten Kana
        String[][] basic = {
                {"あ", "a"}, {"い", "i"}, {"う", "u"}, {"え", "e"}, {"お", "o"},
                {"か", "ka"}, {"き", "ki"}, {"く", "ku"}, {"け", "ke"}, {"こ", "ko"},
                {"さ", "sa"}, {"し", "shi"}, {"す", "su"}, {"せ", "se"}, {"そ", "so"},
                {"た", "ta"}, {"ち", "chi"}, {"つ", "tsu"}, {"て", "te"}, {"と", "to"},
                {"な", "na"}, {"に", "ni"}, {"ぬ", "nu"}, {"ね", "ne"}, {"の", "no"},
                {"は", "ha"}, {"ひ", "hi"}, {"ふ", "fu"}, {"へ", "he"}, {"ほ", "ho"},
                {"ま", "ma"}, {"み", "mi"}, {"む", "mu"}, {"め", "me"}, {"モ", "mo"},
                {"や", "ya"}, {"ゆ", "yu"}, {"よ", "yo"},
                {"ら", "ra"}, {"り", "ri"}, {"る", "ru"}, {"れ", "re"}, {"ろ", "ro"},
                {"わ", "wa"}, {"を", "wo"}, {"ん", "n"},
                {"が", "ga"}, {"ぎ", "gi"}, {"ぐ", "gu"}, {"げ", "ge"}, {"ご", "go"},
                {"ざ", "za"}, {"じ", "ji"}, {"ず", "zu"}, {"ぜ", "ze"}, {"ぞ", "zo"},
                {"だ", "da"}, {"ぢ", "ji"}, {"づ", "zu"}, {"де", "de"}, {"ど", "do"},
                {"ば", "ba"}, {"び", "bi"}, {"ぶ", "bu"}, {"べ", "be"}, {"ぼ", "bo"},
                {"ぱ", "pa"}, {"ぴ", "pi"}, {"ぷ", "pu"}, {"ぺ", "pe"}, {"ぽ", "po"},

                {"ア", "a"}, {"イ", "i"}, {"ウ", "u"}, {"エ", "e"}, {"オ", "o"},
                {"カ", "ka"}, {"キ", "ki"}, {"ク", "ku"}, {"ケ", "ke"}, {"コ", "ko"},
                {"サ", "sa"}, {"シ", "shi"}, {"ス", "su"}, {"セ", "se"}, {"ソ", "so"},
                {"タ", "ta"}, {"チ", "chi"}, {"ツ", "tsu"}, {"テ", "te"}, {"ト", "to"},
                {"ナ", "na"}, {"ニ", "ni"}, {"ヌ", "nu"}, {"ネ", "ne"}, {"ノ", "no"},
                {"ハ", "ha"}, {"ヒ", "hi"}, {"フ", "fu"}, {"ヘ", "he"}, {"ホ", "ho"},
                {"マ", "ma"}, {"ミ", "mi"}, {"ム", "mu"}, {"メ", "me"}, {"モ", "mo"},
                {"ヤ", "ya"}, {"ユ", "yu"}, {"ヨ", "yo"},
                {"ラ", "ra"}, {"リ", "ri"}, {"ル", "ru"}, {"レ", "re"}, {"ロ", "ro"},
                {"ワ", "wa"}, {"ヲ", "wo"}, {"ン", "n"},
                {"ガ", "ga"}, {"ギ", "gi"}, {"グ", "gu"}, {"ゲ", "ge"}, {"ゴ", "go"},
                {"ザ", "za"}, {"ジ", "ji"}, {"ズ", "zu"}, {"ゼ", "ze"}, {"ゾ", "zo"},
                {"ダ", "da"}, {"ヂ", "ji"}, {"ヅ", "zu"}, {"デ", "de"}, {"ド", "do"},
                {"バ", "ba"}, {"ビ", "bi"}, {"ブ", "bu"}, {"ベ", "be"}, {"ボ", "bo"},
                {"パ", "pa"}, {"ピ", "pi"}, {"プ", "pu"}, {"ペ", "pe"}, {"ポ", "po"},

                {"ぁ", "a"}, {"ぃ", "i"}, {"ぅ", "u"}, {"ぇ", "e"}, {"ぉ", "o"},
                {"ァ", "a"}, {"ィ", "i"}, {"ゥ", "u"}, {"ェ", "e"}, {"ォ", "o"},
                {"ゃ", "ya"}, {"ゅ", "yu"}, {"ょ", "yo"},
                {"ャ", "ya"}, {"ュ", "yu"}, {"ョ", "yo"},
                {"ゎ", "wa"}, {"ヮ", "wa"}
        };
        for (String[] pair : basic) {
            KANA_TO_ROMAJI.put(pair[0], pair[1]);
        }

        // Popular Anime / J-Pop Kanji Titles Dictionary (Overrides for unique anime title readings)
        String[][] kanji = {
                {"春日影", "Haruhikage"},
                {"詩超絆", "Utakotoba"},
                {"紅蓮華", "Gurenge"},
                {"残響散歌", "Zankyosanka"},
                {"夜に駆ける", "Yoru ni Kakeru"},
                {"青のすみか", "Ao no Sumika"},
                {"廻廻奇譚", "Kaikai Kitan"},
                {"怪物", "Kaibutsu"},
                {"群青", "Gunjou"},
                {"祝福", "Shukufuku"},
                {"勇者", "Yuusha"},
                {"アイドル", "Idol"},
                {"心臓を捧げよ", "Shinzou wo Sasageyo"},
                {"炎", "Homura"},
                {"白日", "Hakujitsu"},
                {"打上花火", "Uchiage Hanabi"},
                {"前前前世", "Zenzenzense"},
                {"スパークル", "Sparkle"},
                {"なんでもないや", "Nandemonaiya"},
                {"春を告げる", "Haru wo Tsugeru"},
                {"ドライフラワー", "Dry Flower"},
                {"Lemon", "Lemon"},
                {"感電", "Kanden"},
                {"ピースサイン", "Peace Sign"},
                {"新時代", "New Genesis"},
                {"逆光", "Gyakko"},
                {"私は最強", "Watashi wa Saikyou"},
                {"ウタカタララバイ", "Utakata Lullaby"},
                {"Tot Musica", "Tot Musica"},
                {"世界のつづき", "Sekai no Tsuzuki"},
                {"風のゆくえ", "Kaze no Yukue"},
                {"ミックスナッツ", "Mixed Nuts"},
                {"喜劇", "Kigeki"},
                {"Subtitle", "Subtitle"},
                {"第ゼロ感", "Dai Zero Kan"},
                {"絆ノ奇跡", "Kizuna no Kiseki"},
                {"トウキョウ・シャンディ・ランデヴ", "Tokyo Shandy Rendezvous"},
                {"ちゅ、多様性。", "Chu, Tayousei."},
                {"メフィスト", "Mephisto"},
                {"ファタール", "Fatal"},
                {"サインはB", "Sign wa B"},
                {"STAR☆T☆RAIN", "STAR☆T☆RAIN"},
                {"ぴえヨンブートダンス", "Pieyon Boot Dance"},
                {"狂乱 Hey Kids!!", "Kyouran Hey Kids!!"},
                {"青と夏", "Ao to Natsu"},
                {"点描の唄", "Tenbyou no Uta"},
                {"僕のこと", "Boku no Koto"},
                {"ダンスホール", "Dance Hall"},
                {"ケセラセラ", "Que Sera Sera"},
                {"B小町", "B-Komachi"},
                {"25時、ナイトコードで。", "25-ji, Nightcord de."}
        };
        for (String[] pair : kanji) {
            COMMON_KANJI_DICT.put(pair[0], pair[1]);
        }
    }

    public static boolean hasJapanese(String text) {
        if (text == null) return false;
        return text.chars().anyMatch(c ->
                (c >= 0x3040 && c <= 0x309F) || // Hiragana
                (c >= 0x30A0 && c <= 0x30FF) || // Katakana
                (c >= 0x4E00 && c <= 0x9FAF)    // CJK Kanji
        );
    }

    /**
     * Converts Katakana / Hiragana characters into Romaji.
     */
    public static String kanaToRomaji(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Sokuon (small tsu: っ / ッ) -> doubles next consonant
            if ((c == 'っ' || c == 'ッ') && i + 1 < len) {
                String nextTwo = (i + 2 <= len) ? text.substring(i + 1, i + 3) : "";
                String nextOne = String.valueOf(text.charAt(i + 1));
                String nextRomaji = KANA_TO_ROMAJI.getOrDefault(nextTwo, KANA_TO_ROMAJI.get(nextOne));
                if (nextRomaji != null && !nextRomaji.isEmpty()) {
                    char firstConsonant = nextRomaji.charAt(0);
                    if ("aeiouAEIOU".indexOf(firstConsonant) == -1) {
                        sb.append(firstConsonant);
                    }
                }
                i++;
                continue;
            }

            // Chōonpu (ー: long vowel mark)
            if (c == 'ー') {
                if (sb.length() > 0) {
                    char prev = sb.charAt(sb.length() - 1);
                    if ("aeiouAEIOU".indexOf(prev) != -1) {
                        sb.append(prev);
                    }
                }
                i++;
                continue;
            }

            // Try 2-char Yoon / compound match first
            if (i + 1 < len) {
                String two = text.substring(i, i + 2);
                if (KANA_TO_ROMAJI.containsKey(two)) {
                    sb.append(KANA_TO_ROMAJI.get(two));
                    i += 2;
                    continue;
                }
            }

            // Try 1-char Kana match
            String one = String.valueOf(c);
            if (KANA_TO_ROMAJI.containsKey(one)) {
                sb.append(KANA_TO_ROMAJI.get(one));
            } else {
                sb.append(c);
            }
            i++;
        }

        return sb.toString();
    }

    /**
     * Converts a Japanese song title or artist to Romaji.
     * Uses manual dictionary lookup first, then Kuromoji morphological analysis to extract Furigana readings,
     * and finally transliterates to Romaji.
     */
    public static String toRomaji(String input) {
        if (input == null || input.isBlank()) return "";
        String text = input.trim();

        // 1. Direct dictionary match
        if (COMMON_KANJI_DICT.containsKey(text)) {
            return COMMON_KANJI_DICT.get(text);
        }

        // Substring dictionary replacement (for compound titles like "25時、ナイトコードで。")
        for (Map.Entry<String, String> entry : COMMON_KANJI_DICT.entrySet()) {
            if (text.contains(entry.getKey())) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
        }

        if (!hasJapanese(text)) {
            return text;
        }

        // 2. Tokenize using Kuromoji IPADIC to extract readings for Kanji
        try {
            List<Token> tokens = TOKENIZER.tokenize(text);
            StringBuilder result = new StringBuilder();
            boolean lastWasJapaneseWord = false;

            for (Token token : tokens) {
                String surface = token.getSurface();
                String reading = token.getReading();

                // If token has Katakana reading from Kuromoji
                if (reading != null && !reading.equals("*") && !reading.isBlank()) {
                    String romaji = kanaToRomaji(reading);
                    if (!romaji.isBlank()) {
                        String cap = capitalize(romaji);
                        if (lastWasJapaneseWord) {
                            result.append(" ");
                        }
                        result.append(cap);
                        lastWasJapaneseWord = true;
                        continue;
                    }
                }

                // If surface itself contains Japanese Kana
                if (hasJapanese(surface)) {
                    String romaji = kanaToRomaji(surface);
                    if (!romaji.isBlank()) {
                        String cap = capitalize(romaji);
                        if (lastWasJapaneseWord) {
                            result.append(" ");
                        }
                        result.append(cap);
                        lastWasJapaneseWord = true;
                        continue;
                    }
                }

                // Non-Japanese character / token
                if (surface.equals(" ") || surface.equals("　")) {
                    result.append(" ");
                    lastWasJapaneseWord = false;
                } else {
                    // Retain ASCII symbols, letters, digits
                    result.append(surface);
                    lastWasJapaneseWord = false;
                }
            }

            String romanized = result.toString().replaceAll("\\s+", " ").trim();
            if (!romanized.isEmpty()) {
                return romanized;
            }
        } catch (Exception ignored) {
        }

        // Fallback: direct kana transliteration
        return kanaToRomaji(text).replaceAll("\\s+", " ").trim();
    }

    /**
     * Extracts core base title (removing brackets and suffixes like "(TV Size)", "(MyGO!!!!! ver.)")
     * and converts it to Romaji.
     */
    public static String toBaseRomaji(String title) {
        if (title == null || title.isBlank()) return "";
        String base = title.replaceAll("[\\[\\(（【「『][^\\]\\)）】」』]*[\\]\\)）】」』]", "").trim();
        base = base.replaceAll("(?i)(?:[\\s\\(\\[\\{]|^)(?:feat\\.?|ft\\.?|featuring|with)\\s*.*$", "").trim();
        if (base.isEmpty()) base = title.trim();
        return toRomaji(base);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
