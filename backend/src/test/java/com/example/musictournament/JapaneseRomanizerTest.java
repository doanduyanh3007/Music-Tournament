package com.example.musictournament;

import com.example.musictournament.service.JapaneseRomanizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JapaneseRomanizerTest {

    @Test
    @DisplayName("1. MyGO!!!!! ca khúc Ateji và cách đọc đặc biệt từ điển xác minh")
    public void testMyGOAtejiSpecialReadings() {
        // 輪符雨 -> refrain (không bị dịch thành Wa Fu U)
        assertEquals("refrain", JapaneseRomanizer.toRomaji("輪符雨"));
        assertEquals("refrain", JapaneseRomanizer.toBaseRomaji("輪符雨 (MyGO!!!!! ver.)"));

        // 無路矢 -> noroshi (không bị dịch thành Mu Michi Ya)
        assertEquals("noroshi", JapaneseRomanizer.toRomaji("無路矢"));
        assertEquals("noroshi", JapaneseRomanizer.toBaseRomaji("無路矢 (TV Size)"));

        // 迷星叫 -> Mayoiuta
        assertEquals("Mayoiuta", JapaneseRomanizer.toRomaji("迷星叫"));

        // 壱雫空 -> Hitoshizuku
        assertEquals("Hitoshizuku", JapaneseRomanizer.toRomaji("壱雫空"));

        // 春日影 -> Haruhikage
        assertEquals("Haruhikage", JapaneseRomanizer.toRomaji("春日影"));

        // 詩超絆 -> Utakotoba
        assertEquals("Utakotoba", JapaneseRomanizer.toRomaji("詩超絆"));

        // 碧天伴走 -> Hekiten Bansou
        assertEquals("Hekiten Bansou", JapaneseRomanizer.toRomaji("碧天伴走"));

        // 迷路日々 -> Melody
        assertEquals("Melody", JapaneseRomanizer.toRomaji("迷路日々"));

        // 処救生 -> Spit out
        assertEquals("Spit out", JapaneseRomanizer.toRomaji("処救生"));

        // 端程山 -> Panorama
        assertEquals("Panorama", JapaneseRomanizer.toRomaji("端程山"));

        // 孤壊牢 -> Corridor
        assertEquals("Corridor", JapaneseRomanizer.toRomaji("孤壊牢"));

        // 廻色巡 -> Circle
        assertEquals("Circle", JapaneseRomanizer.toRomaji("廻色巡"));
    }

    @Test
    @DisplayName("2. Kiểm tra cấu trúc từ điển xác minh có đầy đủ Original Name, Reading, Romaji, Vietnamese Translation")
    public void testVerifiedRegistryMetadata() {
        JapaneseRomanizer.VerifiedSongEntry entry = JapaneseRomanizer.getVerifiedEntry("輪符雨");
        assertNotNull(entry, "Expected '輪符雨' to exist in verified registry");
        assertEquals("輪符雨", entry.getOriginalTitle());
        assertEquals("ルフラン", entry.getReading());
        assertEquals("refrain", entry.getRomaji());
        assertEquals("Cơn mưa điệp khúc", entry.getVietnameseTranslation());

        // Kiểm tra tách biệt Romaji và Dịch nghĩa tiếng Việt
        assertFalse(entry.getRomaji().isEmpty());
        assertFalse(entry.getVietnameseTranslation().isEmpty());
        assertNotEquals(entry.getRomaji(), entry.getVietnameseTranslation());

        // Kiểm tra bài 無路矢
        JapaneseRomanizer.VerifiedSongEntry noroshi = JapaneseRomanizer.getVerifiedEntry("無路矢");
        assertNotNull(noroshi);
        assertEquals("のろし", noroshi.getReading());
        assertEquals("noroshi", noroshi.getRomaji());
        assertEquals("Khói tín hiệu chỉ lối", noroshi.getVietnameseTranslation());
    }

    @Test
    @DisplayName("3. Kiểm tra logic Kana: Âm ngắt, Âm dài, Âm ghép, Âm đục")
    public void testKanaPhonetics() {
        // Âm ngắt (Sokuon) っ / ッ: gấp đôi phụ âm kế tiếp
        assertEquals("kitte", JapaneseRomanizer.kanaToRomaji("きって"));
        assertEquals("rokkusuta", JapaneseRomanizer.kanaToRomaji("ロックスタ"));

        // Âm ngắt trước 'ch' trong Hepburn
        assertEquals("matchi", JapaneseRomanizer.kanaToRomaji("マッチ"));

        // Âm dài (Chōonpu) ー: kéo dài nguyên âm trước
        assertEquals("shootaimu", JapaneseRomanizer.kanaToRomaji("ショータイム"));
        assertEquals("suupaa", JapaneseRomanizer.kanaToRomaji("スーパー"));

        // Âm ghép (Yoon): きゃ, しゅ, ちょ, びょ, ぴゃ
        assertEquals("kyaku", JapaneseRomanizer.kanaToRomaji("きゃく"));
        assertEquals("shuto", JapaneseRomanizer.kanaToRomaji("しゅと"));
        assertEquals("chotto", JapaneseRomanizer.kanaToRomaji("ちょっと"));
        assertEquals("byouin", JapaneseRomanizer.kanaToRomaji("びょういん"));
        assertEquals("happyou", JapaneseRomanizer.kanaToRomaji("はっぴょう"));

        // Âm đục (Dakuten): で (de) không bị nhầm lẫn ký tự Cyrillic
        assertEquals("de", JapaneseRomanizer.kanaToRomaji("で"));
        assertEquals("mo", JapaneseRomanizer.kanaToRomaji("も"));
        assertEquals("dokidoki", JapaneseRomanizer.kanaToRomaji("どきどき"));
    }

    @Test
    @DisplayName("4. Thứ tự ưu tiên của resolveRomaji")
    public void testResolveRomajiPriority() {
        // Ưu tiên 1: Từ điển xác minh đè bẹp dữ liệu cũ bị sai
        assertEquals("refrain", JapaneseRomanizer.resolveRomaji("輪符雨", "Wa Fu U"));
        assertEquals("noroshi", JapaneseRomanizer.resolveRomaji("無路矢", "Mu Michi Ya"));

        // Ưu tiên 2: Giữ nguyên dữ liệu romaji đã có nếu đã chuẩn Latin và không có trong từ điển
        assertEquals("Custom Latin Title", JapaneseRomanizer.resolveRomaji("Bài hát thông thường", "Custom Latin Title"));

        // Ưu tiên 3: Tự động phân tích cho bài tiếng Nhật chuẩn (Kuromoji)
        String romajiGurenge = JapaneseRomanizer.resolveRomaji("紅蓮華", null);
        assertEquals("Gurenge", romajiGurenge);

        // Chuỗi không phải tiếng Nhật -> Trả về rỗng
        assertEquals("", JapaneseRomanizer.resolveRomaji("English Song Name", null));
    }

    @Test
    @DisplayName("5. An toàn: Không suy đoán sai lệch khi gặp ký tự chưa rõ cách đọc")
    public void testUnverifiedAndUnknownHandling() {
        // Tên hoàn toàn không phải tiếng Nhật
        JapaneseRomanizer.RomanizationResult engResult = JapaneseRomanizer.romanize("Blinding Lights");
        assertTrue(engResult.isVerified());
        assertEquals("Blinding Lights", engResult.getRomaji());

        // Kiểm tra danh mục từ điển xác minh có đầy đủ các ca khúc được yêu cầu
        Map<String, String> dict = JapaneseRomanizer.getVerifiedTitleDict();
        assertTrue(dict.containsKey("輪符雨"));
        assertTrue(dict.containsKey("無路矢"));
        assertTrue(dict.containsKey("迷星叫"));
        assertTrue(dict.containsKey("壱雫空"));
        assertTrue(dict.containsKey("春日影"));
        assertTrue(dict.containsKey("詩超絆"));
        assertTrue(dict.containsKey("碧天伴走"));
    }

    @Test
    @DisplayName("6. Biến thể ngữ âm (Phonetic variants) cho đối soát đáp án")
    public void testPhoneticVariants() {
        List<String> variants = JapaneseRomanizer.toPhoneticRomajiVariants("輪符雨");
        assertTrue(variants.contains("refrain"), "Expected variants to contain 'refrain'");

        List<String> showtimeVariants = JapaneseRomanizer.toPhoneticRomajiVariants("ショータイム");
        assertTrue(showtimeVariants.contains("shootaimu") || showtimeVariants.contains("shotaimu"));
    }
}
