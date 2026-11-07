package com.example.musictournament.service;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Hệ thống chuyển đổi và quản lý phiên âm Romaji cho tên bài hát tiếng Nhật
 * (Kanji / Hiragana / Katakana).
 *
 * <h3>Nguyên tắc thiết kế:</h3>
 * <ol>
 *   <li><b>Từ điển ưu tiên (Verified Song Registry):</b> Các ca khúc có cách đọc Ateji
 *       (chữ Hán mang ý nghĩa nhưng phát âm đặc biệt do tác giả/ban nhạc quy định, vd: MyGO!!!!!)
 *       hoặc tên đã kiểm chứng được ưu tiên số 1, lưu đầy đủ: Original Name, Reading (Furigana),
 *       Romaji và Vietnamese Translation.</li>
 *   <li><b>Phân tích hình thái học (Kuromoji IPADIC):</b> Dành cho các từ Hán thông thường
 *       khi chưa có trong từ điển xác minh.</li>
 *   <li><b>Bảo toàn tính chính xác:</b> Khi gặp Kanji không xác định được cách đọc,
 *       tuyệt đối không suy đoán bừa bãi (không tạo phiên âm sai như "Wa Fu U").</li>
 *   <li><b>Tách biệt rõ ràng:</b> Phiên âm Romaji và Dịch nghĩa tiếng Việt được lưu riêng biệt.</li>
 * </ol>
 */
public final class JapaneseRomanizer {

    private JapaneseRomanizer() {}

    private static final Tokenizer TOKENIZER = new Tokenizer();

    // =========================================================================
    // 1. DATA MODELS
    // =========================================================================

    /**
     * Bản ghi bài hát đã được xác minh chính thức.
     */
    public static final class VerifiedSongEntry {
        private final String originalTitle;
        private final String reading;               // Furigana / Kana reading (vd: ルフラン, のろし, まよいうた)
        private final String romaji;                // Romaji chuẩn (vd: refrain, noroshi, Mayoiuta)
        private final String vietnameseTranslation; // Dịch nghĩa tiếng Việt (vd: Cơn mưa điệp khúc, Khói tín hiệu chỉ lối)

        public VerifiedSongEntry(String originalTitle, String reading, String romaji, String vietnameseTranslation) {
            this.originalTitle = originalTitle;
            this.reading = reading != null ? reading.trim() : "";
            this.romaji = romaji != null ? romaji.trim() : "";
            this.vietnameseTranslation = vietnameseTranslation != null ? vietnameseTranslation.trim() : "";
        }

        public String getOriginalTitle() { return originalTitle; }
        public String getReading() { return reading; }
        public String getRomaji() { return romaji; }
        public String getVietnameseTranslation() { return vietnameseTranslation; }

        @Override
        public String toString() {
            return "VerifiedSongEntry{" +
                    "original='" + originalTitle + '\'' +
                    ", reading='" + reading + '\'' +
                    ", romaji='" + romaji + '\'' +
                    ", vi='" + vietnameseTranslation + '\'' +
                    '}';
        }
    }

    /**
     * Kết quả phiên âm bao gồm thông tin xác thực.
     */
    public static final class RomanizationResult {
        private final String originalTitle;
        private final String reading;
        private final String romaji;
        private final String vietnameseTranslation;
        private final boolean verified;

        public RomanizationResult(String originalTitle, String reading, String romaji, String vietnameseTranslation, boolean verified) {
            this.originalTitle = originalTitle;
            this.reading = reading != null ? reading : "";
            this.romaji = romaji != null ? romaji : "";
            this.vietnameseTranslation = vietnameseTranslation != null ? vietnameseTranslation : "";
            this.verified = verified;
        }

        public String getOriginalTitle() { return originalTitle; }
        public String getReading() { return reading; }
        public String getRomaji() { return romaji; }
        public String getVietnameseTranslation() { return vietnameseTranslation; }
        public boolean isVerified() { return verified; }

        public boolean hasRomaji() {
            return romaji != null && !romaji.isBlank();
        }
    }

    // =========================================================================
    // 2. KANA TO ROMAJI MAPPING TABLE
    // =========================================================================

    private static final Map<String, String> KANA_TO_ROMAJI = new LinkedHashMap<>();

    // =========================================================================
    // 3. VERIFIED SONG REGISTRY (Từ điển ca khúc đã xác minh)
    // =========================================================================

    private static final Map<String, VerifiedSongEntry> VERIFIED_REGISTRY = new LinkedHashMap<>();
    private static final Map<String, String> VERIFIED_ROMAJI_MAP = new LinkedHashMap<>();

    static {
        // --- Âm ghép (Yoon) & Katakana mở rộng: Ưu tiên khớp tham lam trước ---
        String[][] yoon = {
                {"きゃ", "kya"}, {"きゅ", "kyu"}, {"きょ", "kyo"},
                {"しゃ", "sha"}, {"しゅ", "shu"}, {"しょ", "sho"},
                {"ちゃ", "cha"}, {"ちゅ", "chu"}, {"ちょ", "cho"},
                {"にゃ", "nya"}, {"にゅ", "nyu"}, {"にょ", "nyo"},
                {"ひゃ", "hya"}, {"ひゅ", "hyu"}, {"ひょ", "hyo"},
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

        // --- Bảng Kana cơ bản & Âm đục (Dakuten) ---
        String[][] basic = {
                {"あ", "a"}, {"い", "i"}, {"う", "u"}, {"え", "e"}, {"お", "o"},
                {"か", "ka"}, {"き", "ki"}, {"く", "ku"}, {"け", "ke"}, {"こ", "ko"},
                {"さ", "sa"}, {"し", "shi"}, {"す", "su"}, {"せ", "se"}, {"そ", "so"},
                {"た", "ta"}, {"ち", "chi"}, {"つ", "tsu"}, {"て", "te"}, {"と", "to"},
                {"な", "na"}, {"に", "ni"}, {"ぬ", "nu"}, {"ね", "ne"}, {"の", "no"},
                {"は", "ha"}, {"ひ", "hi"}, {"ふ", "fu"}, {"へ", "he"}, {"ほ", "ho"},
                {"ま", "ma"}, {"み", "mi"}, {"む", "mu"}, {"め", "me"}, {"も", "mo"},
                {"や", "ya"}, {"ゆ", "yu"}, {"よ", "yo"},
                {"ら", "ra"}, {"り", "ri"}, {"る", "ru"}, {"れ", "re"}, {"ろ", "ro"},
                {"わ", "wa"}, {"を", "wo"}, {"ん", "n"},
                {"が", "ga"}, {"ぎ", "gi"}, {"ぐ", "gu"}, {"げ", "ge"}, {"ご", "go"},
                {"ざ", "za"}, {"じ", "ji"}, {"ず", "zu"}, {"ぜ", "ze"}, {"ぞ", "zo"},
                {"だ", "da"}, {"ぢ", "ji"}, {"づ", "zu"}, {"で", "de"}, {"ど", "do"},
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

        // =====================================================================
        // ĐĂNG KÝ TỪ ĐIỂN BÀI HÁT ĐÃ XÁC MINH (VERIFIED ENTRIES)
        // Lưu trữ chuẩn: { Original Title, Kana Reading, Romaji, Vietnamese Meaning }
        // =====================================================================

        // 1. MyGO!!!!! (BanG Dream!) — Ca khúc Ateji / độc quyền có cách đọc đặc biệt
        registerVerified("輪符雨",   "ルフラン",           "refrain",         "Cơn mưa điệp khúc");
        registerVerified("無路矢",   "のろし",             "noroshi",         "Khói tín hiệu chỉ lối");
        registerVerified("迷星叫",   "まよいうた",         "Mayoiuta",        "Tiếng gọi vì sao lạc lối");
        registerVerified("壱雫空",   "ひとしずく",         "Hitoshizuku",     "Một giọt mưa từ bầu trời");
        registerVerified("碧天伴走", "へきてんばんそう",   "Hekiten Bansou",  "Chạy cùng nhau dưới bầu trời xanh");
        registerVerified("春日影",   "はるひかげ",         "Haruhikage",      "Bóng nắng xuân");
        registerVerified("詩超絆",   "うたことば",         "Utakotoba",       "Lời ca vượt qua gắn kết");
        registerVerified("音一会",   "おといちえ",         "Otoichie",        "Âm thanh gặp gỡ duy nhất");
        registerVerified("潜在表明", "せんざいひょうめい", "Senzai Hyoumei",  "Bộc lộ tiềm ẩn");
        registerVerified("影色舞",   "シルエットダンス",   "Silhouette Dance","Điệu nhảy của bóng hình");
        registerVerified("啼涙の星", "ているいのほし",     "Tairui no Hoshi", "Vì sao rơi lệ");
        registerVerified("猛獣録",   "もうじゅうろく",     "Moujuuroku",      "Hồ sơ dã thú");
        registerVerified("迷路日々", "メロディ",           "Melody",          "Những ngày trong mê lộ");
        registerVerified("処救生",   "スピットアウト",     "Spit out",        "Nơi cứu rỗi cuộc đời");
        registerVerified("端程山",   "パノラマ",           "Panorama",        "Bức tranh toàn cảnh núi non");
        registerVerified("孤壊牢",   "コリドー",           "Corridor",        "Lồng giam cô độc đổ vỡ");
        registerVerified("廻色巡",   "サークル",           "Circle",          "Vòng xoay luân hồi");
        registerVerified("栞",       "しおり",             "Shiori",          "Chiếc kẹp sách");

        // 2. Anime & J-Pop phổ biến có chữ Hán
        registerVerified("紅蓮華",         "ぐれんげ",           "Gurenge",             "Hoa sen đỏ thắm");
        registerVerified("残響散歌",       "ざんきょうさんか",   "Zankyosanka",         "Bài ca dư âm tan biến");
        registerVerified("夜に駆ける",     "よるにかける",       "Yoru ni Kakeru",      "Chạy vào màn đêm");
        registerVerified("青のすみか",     "あおのすみか",       "Ao no Sumika",        "Nơi cư ngụ của màu xanh");
        registerVerified("廻廻奇譚",       "かいかいきたん",     "Kaikai Kitan",        "Câu chuyện kỳ lạ luân hồi");
        registerVerified("怪物",           "かいぶつ",           "Kaibutsu",            "Quái vật");
        registerVerified("群青",           "ぐんじょう",         "Gunjou",              "Màu xanh thẫm");
        registerVerified("祝福",           "しゅくふく",         "Shukufuku",           "Lời chúc phúc");
        registerVerified("勇者",           "ゆうしゃ",           "Yuusha",              "Dũng giả");
        registerVerified("心臓を捧げよ",   "しんぞうをささげよ", "Shinzou wo Sasageyo", "Hãy dâng hiến trái tim");
        registerVerified("炎",             "ほむら",             "Homura",              "Ngọn lửa");
        registerVerified("白日",           "はくじつ",           "Hakujitsu",           "Thanh thiên bạch nhật");
        registerVerified("打上花火",       "うちあげはなび",     "Uchiage Hanabi",      "Pháo hoa bay lên");
        registerVerified("前前前世",       "ぜんぜんぜんせ",     "Zenzenzense",         "Tiền tiền tiền kiếp");
        registerVerified("春を告げる",     "はるをつげる",       "Haru wo Tsugeru",     "Báo hiệu mùa xuân");
        registerVerified("感電",           "かんでん",           "Kanden",              "Điện giật");
        registerVerified("新時代",         "しんじだい",         "Shinjidai",           "Kỷ nguyên mới");
        registerVerified("逆光",           "ぎゃっこう",         "Gyakkou",             "Ngược sáng");
        registerVerified("私は最強",       "わたしはさいきょう", "Watashi wa Saikyou", "Tôi là kẻ mạnh nhất");
        registerVerified("世界のつづき",   "せかいのつづき",     "Sekai no Tsuzuki",    "Phần tiếp theo của thế giới");
        registerVerified("風のゆくえ",     "かぜのゆくえ",       "Kaze no Yukue",       "Nơi ngọn gió hướng về");
        registerVerified("喜劇",           "きげき",             "Kigeki",              "Hài kịch");
        registerVerified("第ゼロ感",       "だいぜろかん",       "Dai Zero Kan",        "Giác quan thứ không");
        registerVerified("絆ノ奇跡",       "きずなのきせき",     "Kizuna no Kiseki",    "Kỳ tích của mối liên kết");
        registerVerified("狂乱 Hey Kids!!","きょうらん Hey Kids!!","Kyouran Hey Kids!!","Cuồng loạn Hey Kids!!");
        registerVerified("青と夏",         "あおとなつ",         "Ao to Natsu",         "Màu xanh và mùa hạ");
        registerVerified("点描の唄",       "てんびょうのうた",   "Tenbyou no Uta",      "Bài ca vẽ bằng những chấm nhỏ");
        registerVerified("僕のこと",       "ぼくのこと",         "Boku no Koto",        "Chuyện của tôi");
        registerVerified("B小町",          "ビーこまち",         "B-Komachi",           "Nhóm B-Komachi");

        // 3. Từ mượn Katakana tiếng Anh — đáp án chính thức thường là từ gốc tiếng Anh
        registerVerified("アイドル",                       "アイドル",                       "Idol",                    "Thần tượng");
        registerVerified("スパークル",                     "スパークル",                     "Sparkle",                 "Tia sáng lấp lánh");
        registerVerified("なんでもないや",                 "なんでもないや",                 "Nandemonaiya",            "Chẳng là gì cả");
        registerVerified("ドライフラワー",                 "ドライフラワー",                 "Dry Flower",              "Hoa khô");
        registerVerified("ピースサイン",                   "ピースサイン",                   "Peace Sign",              "Biểu tượng hòa bình");
        registerVerified("ウタカタララバイ",               "ウタカタララバイ",               "Utakata Lullaby",         "Khúc hát ru bọt nước");
        registerVerified("ミックスナッツ",                 "ミックスナッツ",                 "Mixed Nuts",              "Hạt thập cẩm");
        registerVerified("トウキョウ・シャンディ・ランデヴ", "トウキョウ・シャンディ・ランデヴ", "Tokyo Shandy Rendezvous", "Cuộc hẹn Shandy Tokyo");
        registerVerified("ちゅ、多様性。",                 "ちゅ、たようせい。",             "Chu, Tayousei.",          "Chu, sự đa dạng.");
        registerVerified("メフィスト",                     "メフィスト",                     "Mephisto",                "Mephisto");
        registerVerified("ファタール",                     "ファタール",                     "Fatal",                   "Định mệnh chết chóc");
        registerVerified("サインはB",                      "サインはビー",                   "Sign wa B",               "Dấu hiệu là chữ B");
        registerVerified("ショータイム",                   "ショータイム",                   "Showtime",                "Giờ diễn");
        registerVerified("アンサー",                       "アンサー",                       "Answer",                  "Câu trả lời");
        registerVerified("モンスター",                     "モンスター",                     "Monster",                 "Quái vật");
        registerVerified("ルーザー",                       "ルーザー",                       "Loser",                   "Kẻ thua cuộc");
        registerVerified("シンドローム",                   "シンドローム",                   "Syndrome",                "Hội chứng");
        registerVerified("ロックスター",                   "ロックスター",                   "Rockstar",                "Ngôi sao nhạc Rock");
        registerVerified("パレード",                       "パレード",                       "Parade",                  "Diễu hành");
        registerVerified("ダンス",                         "ダンス",                         "Dance",                   "Khiêu vũ");
        registerVerified("ぴえヨンブートダンス",           "ぴえヨンブートダンス",           "Pieyon Boot Dance",       "Điệu nhảy Pieyon");
        registerVerified("ダンスホール",                   "ダンスホール",                   "Dance Hall",              "Vũ trường");
        registerVerified("ケセラセラ",                     "ケセラセラ",                     "Que Sera Sera",           "Chuyện gì đến sẽ đến");
        registerVerified("25時、ナイトコードで。",          "にじゅうごじ、ナイトコードで。", "25-ji, Nightcord de.",   "25 giờ, tại Nightcord.");

        // 4. Các tựa đề Latin / Ký hiệu giữ nguyên
        registerVerified("Lemon",      "レモン",      "Lemon",      "Quả chanh vàng");
        registerVerified("Subtitle",   "サブタイトル","Subtitle",   "Phụ đề");
        registerVerified("Tot Musica", "トットムジカ","Tot Musica", "Bản nhạc Tot");
        registerVerified("STAR☆T☆RAIN","スタートレイン","STAR☆T☆RAIN","Chuyến tàu khởi hành");
    }

    private static void registerVerified(String title, String reading, String romaji, String viMeaning) {
        VerifiedSongEntry entry = new VerifiedSongEntry(title, reading, romaji, viMeaning);
        VERIFIED_REGISTRY.put(title, entry);
        VERIFIED_ROMAJI_MAP.put(title, romaji);
    }

    // =========================================================================
    // 4. PUBLIC API & RESOLUTION ENGINE
    // =========================================================================

    /**
     * Kiểm tra xem chuỗi có chứa ký tự tiếng Nhật (Hiragana, Katakana, Kanji) hay không.
     */
    public static boolean hasJapanese(String text) {
        if (text == null) return false;
        return text.chars().anyMatch(c ->
                (c >= 0x3040 && c <= 0x309F) || // Hiragana
                (c >= 0x30A0 && c <= 0x30FF) || // Katakana
                (c >= 0x4E00 && c <= 0x9FAF)    // CJK Kanji
        );
    }

    /**
     * Kiểm tra xem ký tự có phải là Kanji hay không.
     */
    public static boolean isKanji(char c) {
        return c >= 0x4E00 && c <= 0x9FAF;
    }

    /**
     * Lấy thông tin bản ghi xác minh đầy đủ (Original, Reading, Romaji, Tiếng Việt).
     */
    public static VerifiedSongEntry getVerifiedEntry(String title) {
        if (title == null || title.isBlank()) return null;
        String text = title.trim();
        if (VERIFIED_REGISTRY.containsKey(text)) {
            return VERIFIED_REGISTRY.get(text);
        }
        String base = stripBracketsAndFeat(text);
        if (VERIFIED_REGISTRY.containsKey(base)) {
            return VERIFIED_REGISTRY.get(base);
        }
        for (Map.Entry<String, VerifiedSongEntry> entry : VERIFIED_REGISTRY.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Kiểm tra xem tựa đề đã có trong từ điển xác minh hay chưa.
     */
    public static boolean hasVerifiedReading(String text) {
        return getVerifiedEntry(text) != null;
    }

    /**
     * Trả về bảng Map unmodifiable của từ điển xác minh (gồm title -> romaji).
     * Dùng cho DB migration hoặc sync tự động.
     */
    public static Map<String, String> getVerifiedTitleDict() {
        return Collections.unmodifiableMap(VERIFIED_ROMAJI_MAP);
    }

    /**
     * Trả về toàn bộ danh mục bài hát đã xác minh với đầy đủ siêu dữ liệu.
     */
    public static Map<String, VerifiedSongEntry> getVerifiedRegistry() {
        return Collections.unmodifiableMap(VERIFIED_REGISTRY);
    }

    /**
     * Bộ phân giải Romaji trung tâm cho hệ thống (QuizSong, Arena, Multiplayer).
     *
     * <p>Thứ tự ưu tiên xử lý:
     * <ol>
     *   <li><b>Từ điển xác minh (Verified Registry):</b> Ưu tiên số 1 tuyệt đối.</li>
     *   <li><b>Dữ liệu đã lưu hợp lệ:</b> Nếu storedRomaji sạch chữ Hán và không phải giá trị lỗi cũ.</li>
     *   <li><b>Tự động phiên âm chuẩn xác:</b> Dùng Kuromoji, nếu không thể đọc Kanji thì không suy đoán sai.</li>
     * </ol>
     */
    public static String resolveRomaji(String title, String storedRomaji) {
        if (title == null || title.isBlank()) return "";

        // 1. Ưu tiên tuyệt đối từ điển xác minh
        VerifiedSongEntry entry = getVerifiedEntry(title);
        if (entry != null) {
            return entry.getRomaji();
        }

        // 2. Kiểm tra romaji đã lưu trong DB (nếu sạch và không dính phiên âm lỗi cũ)
        if (storedRomaji != null && !storedRomaji.isBlank() && !hasJapanese(storedRomaji)) {
            String trimmed = storedRomaji.trim();
            // Lọc bỏ các phiên âm lỗi cũ nếu vô tình còn sót
            if (!isStaleFlawedTransliteration(trimmed)) {
                return trimmed;
            }
        }

        // 3. Nếu là tiếng Nhật, tự động phân tích an toàn
        if (hasJapanese(title)) {
            RomanizationResult result = romanize(title);
            if (result.hasRomaji() && !hasJapanese(result.getRomaji())) {
                return result.getRomaji();
            }
        }

        return "";
    }

    /**
     * Phiên âm toàn diện tựa đề tiếng Nhật sang Romaji có kiểm chứng tính xác thực.
     */
    public static RomanizationResult romanize(String input) {
        if (input == null || input.isBlank()) {
            return new RomanizationResult("", "", "", "", false);
        }
        String text = input.trim();

        // 1. Kiểm tra từ điển xác minh trực tiếp
        VerifiedSongEntry entry = getVerifiedEntry(text);
        if (entry != null) {
            return new RomanizationResult(text, entry.getReading(), entry.getRomaji(), entry.getVietnameseTranslation(), true);
        }

        // 2. Nếu chuỗi hoàn toàn không chứa tiếng Nhật -> Trả về nguyên bản
        if (!hasJapanese(text)) {
            return new RomanizationResult(text, text, text, "", true);
        }

        // 3. Xử lý trường hợp chuỗi chứa một phần từ điển (vd: "25時、ナイトコードで。")
        String workingText = text;
        String viTranslation = "";
        for (Map.Entry<String, VerifiedSongEntry> e : VERIFIED_REGISTRY.entrySet()) {
            if (workingText.contains(e.getKey())) {
                workingText = workingText.replace(e.getKey(), e.getValue().getRomaji());
                if (viTranslation.isEmpty()) {
                    viTranslation = e.getValue().getVietnameseTranslation();
                }
            }
        }
        if (!hasJapanese(workingText)) {
            return new RomanizationResult(text, "", workingText, viTranslation, true);
        }

        // 4. Phân tích hình thái học Kuromoji IPADIC
        try {
            List<Token> tokens = TOKENIZER.tokenize(text);
            StringBuilder romajiSb = new StringBuilder();
            StringBuilder kanaSb = new StringBuilder();
            boolean lastWasJapaneseWord = false;
            boolean hasUnknownKanji = false;

            for (Token token : tokens) {
                String surface = token.getSurface();
                String reading = token.getReading();

                // Kiểm tra nếu token chứa Kanji nhưng Kuromoji không có cách đọc hợp lệ
                boolean tokenContainsKanji = surface.chars().anyMatch(c -> isKanji((char) c));
                boolean readingMissing = reading == null || reading.equals("*") || reading.isBlank();

                if (tokenContainsKanji && readingMissing) {
                    // CẢNH BÁO: Gặp chữ Hán không xác định được cách đọc!
                    // Theo Yêu cầu 5: Tuyệt đối không suy đoán bừa bãi, đánh dấu chưa xác minh.
                    hasUnknownKanji = true;
                    romajiSb.append(surface);
                    kanaSb.append(surface);
                    lastWasJapaneseWord = false;
                    continue;
                }

                // Nếu có reading Katakana từ Kuromoji
                if (reading != null && !reading.equals("*") && !reading.isBlank()) {
                    String romaji = kanaToRomaji(reading);
                    if (!romaji.isBlank()) {
                        String cap = capitalize(romaji);
                        if (lastWasJapaneseWord && romajiSb.length() > 0) {
                            romajiSb.append(" ");
                        }
                        romajiSb.append(cap);
                        kanaSb.append(reading);
                        lastWasJapaneseWord = true;
                        continue;
                    }
                }

                // Nếu bề mặt token là Kana (Hiragana/Katakana)
                if (hasJapanese(surface)) {
                    String romaji = kanaToRomaji(surface);
                    if (!romaji.isBlank()) {
                        String cap = capitalize(romaji);
                        if (lastWasJapaneseWord && romajiSb.length() > 0) {
                            romajiSb.append(" ");
                        }
                        romajiSb.append(cap);
                        kanaSb.append(surface);
                        lastWasJapaneseWord = true;
                        continue;
                    }
                }

                // Ký tự / ký hiệu Latin, số, khoảng trắng
                if (surface.equals(" ") || surface.equals("　")) {
                    if (romajiSb.length() > 0 && romajiSb.charAt(romajiSb.length() - 1) != ' ') {
                        romajiSb.append(" ");
                    }
                    lastWasJapaneseWord = false;
                } else {
                    romajiSb.append(surface);
                    kanaSb.append(surface);
                    lastWasJapaneseWord = false;
                }
            }

            // Nếu gặp chữ Hán không xác định được cách đọc:
            if (hasUnknownKanji) {
                return new RomanizationResult(text, kanaSb.toString(), text, "", false);
            }

            String finalRomaji = romajiSb.toString().replaceAll("\\s+", " ").trim();
            if (!finalRomaji.isEmpty()) {
                return new RomanizationResult(text, kanaSb.toString(), finalRomaji, "", false);
            }
        } catch (Exception ignored) {}

        // 5. Fallback cuối cùng: Chuyển đổi Kana trực tiếp
        String direct = kanaToRomaji(text).replaceAll("\\s+", " ").trim();
        return new RomanizationResult(text, text, direct, "", false);
    }

    /**
     * Chuyển đổi tên bài hát sang Romaji.
     */
    public static String toRomaji(String input) {
        return romanize(input).getRomaji();
    }

    /**
     * Tách bỏ phần mở rộng trong ngoặc "(TV Size)", "(MyGO!!!!! ver.)" và chuyển sang Romaji.
     */
    public static String toBaseRomaji(String title) {
        if (title == null || title.isBlank()) return "";
        String base = stripBracketsAndFeat(title);
        return toRomaji(base);
    }

    /**
     * Tạo danh sách biến thể phiên âm Hepburn ngữ âm phục vụ đối soát đáp án người chơi.
     * vd: "ショータイム" -> ["shootaimu", "shotaimu"]
     */
    public static List<String> toPhoneticRomajiVariants(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        List<String> list = new ArrayList<>();

        // Nếu có trong từ điển xác minh, thêm cách đọc chính thức
        VerifiedSongEntry entry = getVerifiedEntry(text);
        if (entry != null) {
            String rom = entry.getRomaji().toLowerCase();
            list.add(rom);
            if (!entry.getReading().isBlank()) {
                String kanaRom = kanaToRomaji(entry.getReading());
                if (!kanaRom.isBlank() && !list.contains(kanaRom)) {
                    list.add(kanaRom);
                }
            }
        }

        try {
            List<Token> tokens = TOKENIZER.tokenize(text);
            StringBuilder kanaSb = new StringBuilder();
            for (Token t : tokens) {
                String r = t.getReading();
                if (r != null && !r.equals("*") && !r.isBlank()) {
                    kanaSb.append(r);
                } else {
                    kanaSb.append(t.getSurface());
                }
            }
            String rawKana = kanaSb.toString();
            String doubleVowel = kanaToRomaji(rawKana);
            if (!doubleVowel.isBlank() && !list.contains(doubleVowel)) {
                list.add(doubleVowel);
                String singleVowel = doubleVowel.replaceAll("([aeiou])\\1+", "$1");
                if (!singleVowel.equalsIgnoreCase(doubleVowel) && !list.contains(singleVowel)) {
                    list.add(singleVowel);
                }
            }
        } catch (Exception ignored) {}

        String direct = kanaToRomaji(text);
        if (!direct.isBlank() && !list.contains(direct)) {
            list.add(direct);
        }
        return list;
    }

    /**
     * Chuyển đổi ký tự Katakana / Hiragana thuần sang Romaji Hepburn.
     * Xử lý đúng:
     * - Âm ngắt (Sokuon: っ / ッ) -> gấp đôi phụ âm kế tiếp; nếu trước "ch" dùng "tch" (Hepburn).
     * - Âm dài (Chōonpu: ー) -> kéo dài nguyên âm trước đó.
     * - Âm ghép (Yoon: きゃ, しゃ, ぴょ, v.v.).
     */
    public static String kanaToRomaji(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // 1. Âm ngắt (Sokuon: っ / ッ)
            if ((c == 'っ' || c == 'ッ') && i + 1 < len) {
                String nextTwo = (i + 3 <= len) ? text.substring(i + 1, i + 3) : "";
                String nextOne = String.valueOf(text.charAt(i + 1));
                String nextRomaji = KANA_TO_ROMAJI.getOrDefault(nextTwo, KANA_TO_ROMAJI.get(nextOne));

                if (nextRomaji != null && !nextRomaji.isEmpty()) {
                    char firstConsonant = nextRomaji.charAt(0);
                    // Hepburn: っち -> "tchi" hoặc "cchi"
                    if (nextRomaji.startsWith("ch")) {
                        sb.append('t');
                    } else if ("aeiouAEIOU".indexOf(firstConsonant) == -1) {
                        sb.append(firstConsonant);
                    }
                }
                i++;
                continue;
            }

            // 2. Âm dài Katakana (ー: Chōonpu)
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

            // 3. Khớp tham lam 2 ký tự (Âm ghép Yoon)
            if (i + 1 < len) {
                String two = text.substring(i, i + 2);
                if (KANA_TO_ROMAJI.containsKey(two)) {
                    sb.append(KANA_TO_ROMAJI.get(two));
                    i += 2;
                    continue;
                }
            }

            // 4. Khớp Kana đơn
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

    // =========================================================================
    // 5. INTERNAL HELPERS
    // =========================================================================

    public static String stripBracketsAndFeat(String title) {
        if (title == null || title.isBlank()) return "";
        String base = title.replaceAll("[\\[\\(（【「『][^\\]\\)）】」』]*[\\]\\)）】」』]", "").trim();
        base = base.replaceAll("(?i)(?:[\\s\\(\\[\\{]|^)(?:feat\\.?|ft\\.?|featuring|with)\\s*.*$", "").trim();
        if (base.isEmpty()) base = title.trim();
        return base;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static boolean isStaleFlawedTransliteration(String romaji) {
        if (romaji == null) return false;
        String lower = romaji.toLowerCase(Locale.ROOT);
        return lower.equals("wa fu u") || lower.equals("wafu u") || lower.equals("wafuu")
                || lower.equals("mu michi ya") || lower.equals("mumichiya");
    }

    // =========================================================================
    // 6. BACKWARD COMPATIBILITY
    // =========================================================================

    /**
     * @deprecated Dùng {@link #hasVerifiedReading(String)}
     */
    @Deprecated
    public static boolean hasKanjiOverride(String text) {
        return hasVerifiedReading(text);
    }
}
