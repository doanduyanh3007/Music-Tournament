package com.example.musictournament.service;

import com.example.musictournament.dto.CreateTournamentRequest;
import com.example.musictournament.dto.VoteRequest;
import com.example.musictournament.model.*;
import com.example.musictournament.repo.*;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TournamentService {
    private final TournamentRepository tournaments;
    private final SongRepository songs;
    private final MatchRepository matches;
    private final VoteRepository votes;
    private final WildcardRepository wildcards;
    private final EntityManager em;

    public TournamentService(TournamentRepository tournaments, SongRepository songs,
                             MatchRepository matches, VoteRepository votes, WildcardRepository wildcards, EntityManager em) {
        this.tournaments = tournaments;
        this.songs = songs;
        this.matches = matches;
        this.votes = votes;
        this.wildcards = wildcards;
        this.em = em;
    }

    @Transactional
    public Map<String, Object> create(CreateTournamentRequest request) {
        Tournament t = tournaments.save(new Tournament(request.name().trim()));
        List<Song> songEntities = new ArrayList<>();
        for (String raw : request.songs()) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String link = null;
            String songPart = line;
            int pipeIdx = line.lastIndexOf('|');
            if (pipeIdx > 0) {
                String maybeLnk = line.substring(pipeIdx + 1).trim();
                if (maybeLnk.startsWith("http://") || maybeLnk.startsWith("https://")) {
                    link = maybeLnk;
                    songPart = line.substring(0, pipeIdx).trim();
                }
            }
            String[] parts = songPart.split("\\s+[—–-]\\s+", 2);
            String title = parts[0].trim();
            String artist = parts.length > 1 ? parts[1].trim() : "";
            if (title.isBlank()) continue;
            songEntities.add(new Song(title, artist, link, t));
        }
        if (songEntities.size() < 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần ít nhất 2 bài hát hợp lệ.");

        List<Song> entrants = songs.saveAll(songEntities);

        if (request.shouldShuffle()) {
            Collections.shuffle(entrants);
        }

        List<TournamentMatch> matchesToSave = new ArrayList<>();
        int totalSongs = entrants.size();

        if (totalSongs == 48) {
            // ==========================================
            // LOGIC CHUẨN 48 BÀI (THEO SƠ ĐỒ NGHIỆP VỤ)
            // ==========================================
            // Nhóm A: 24 bài (Trận 0..11) -> 12 bài thắng
            // Nhóm B: 24 bài (Trận 12..23) -> 12 bài thắng
            // Vòng 0: Vòng bảng (24 cặp)
            List<Song> groupA = entrants.subList(0, 24);
            List<Song> groupB = entrants.subList(24, 48);

            int pos = 0;
            for (int i = 0; i < groupA.size(); i += 2) {
                TournamentMatch m = new TournamentMatch(t, 0, pos++, groupA.get(i), groupA.get(i + 1));
                m.setGroupName("A");
                matchesToSave.add(m);
            }
            for (int i = 0; i < groupB.size(); i += 2) {
                TournamentMatch m = new TournamentMatch(t, 0, pos++, groupB.get(i), groupB.get(i + 1));
                m.setGroupName("B");
                matchesToSave.add(m);
            }

            // Vòng 1: Vòng loại 2 (12 cặp) - Bảng A 6 cặp, Bảng B 6 cặp
            for (int i = 0; i < 6; i++) {
                TournamentMatch m = new TournamentMatch(t, 1, i, null, null);
                m.setGroupName("A");
                matchesToSave.add(m);
            }
            for (int i = 6; i < 12; i++) {
                TournamentMatch m = new TournamentMatch(t, 1, i, null, null);
                m.setGroupName("B");
                matchesToSave.add(m);
            }

            // Vòng 2: Play-off (6 cặp đối đầu giữa 6 đại diện Bảng A và 6 đại diện Bảng B)
            for (int i = 0; i < 6; i++) {
                TournamentMatch m = new TournamentMatch(t, 2, i, null, null);
                m.setGroupName("PLAYOFF");
                matchesToSave.add(m);
            }

            // Vòng 3: Tứ kết (4 cặp) - 6 bài thắng Play-off + 2 bài đặc cách Wildcard = 8 bài
            for (int i = 0; i < 4; i++) {
                TournamentMatch m = new TournamentMatch(t, 3, i, null, null);
                m.setGroupName("MAIN");
                matchesToSave.add(m);
            }

            // Vòng 4: Bán kết (2 cặp)
            for (int i = 0; i < 2; i++) {
                TournamentMatch m = new TournamentMatch(t, 4, i, null, null);
                m.setGroupName("MAIN");
                matchesToSave.add(m);
            }

            // Vòng 5: Chung kết (1 cặp)
            TournamentMatch finalMatch = new TournamentMatch(t, 5, 0, null, null);
            finalMatch.setGroupName("MAIN");
            matchesToSave.add(finalMatch);

        } else if (totalSongs > 48) {
            // Linh hoạt nếu > 48 bài: Chia đều 2 nhóm A & B
            int halfA = totalSongs / 2;
            List<Song> groupA = entrants.subList(0, halfA);
            List<Song> groupB = entrants.subList(halfA, totalSongs);

            int pos = 0;
            for (int i = 0; i < groupA.size(); i += 2) {
                Song a = groupA.get(i);
                Song b = (i + 1 < groupA.size()) ? groupA.get(i + 1) : null;
                TournamentMatch m = new TournamentMatch(t, 0, pos++, a, b);
                m.setGroupName("A");
                if (b == null) m.setWinner(a);
                matchesToSave.add(m);
            }
            for (int i = 0; i < groupB.size(); i += 2) {
                Song a = groupB.get(i);
                Song b = (i + 1 < groupB.size()) ? groupB.get(i + 1) : null;
                TournamentMatch m = new TournamentMatch(t, 0, pos++, a, b);
                m.setGroupName("B");
                if (b == null) m.setWinner(a);
                matchesToSave.add(m);
            }

            // Tạo các vòng tiếp theo lũy thừa 2
            int nextEntrants = pos;
            int mainSize = 1; while (mainSize < nextEntrants) mainSize *= 2;
            int nextMatches = mainSize / 2;
            int rIdx = 1;
            while (nextMatches >= 1) {
                for (int i = 0; i < nextMatches; i++) {
                    TournamentMatch m = new TournamentMatch(t, rIdx, i, null, null);
                    m.setGroupName("MAIN");
                    matchesToSave.add(m);
                }
                nextMatches /= 2;
                rIdx++;
            }
        } else {
            // ==========================================
            // THỂ THỨC TIÊU CHUẨN (VD: 32 bài, 16 bài, 8 bài...)
            // Cây loại trực tiếp chuẩn từ vòng ngoài đến chung kết
            // ==========================================
            int size = 1; while (size < entrants.size()) size *= 2;
            int byes = size - entrants.size();

            int pos = 0;
            for (int i = 0; i < byes; i++) {
                TournamentMatch m = new TournamentMatch(t, 0, pos++, entrants.get(i), null);
                m.setWinner(entrants.get(i));
                matchesToSave.add(m);
            }
            for (int i = byes; i < entrants.size(); i += 2) {
                Song a = entrants.get(i), b = (i + 1 < entrants.size() ? entrants.get(i + 1) : null);
                TournamentMatch m = new TournamentMatch(t, 0, pos++, a, b);
                if (b == null) m.setWinner(a);
                matchesToSave.add(m);
            }
            int count = size / 4, round = 1;
            while (count >= 1) {
                for (int i = 0; i < count; i++) {
                    matchesToSave.add(new TournamentMatch(t, round, i, null, null));
                }
                count /= 2; round++;
            }
        }

        // Cấu hình đóng/mở ban đầu
        if (request.initialOpenRounds() != null && !request.initialOpenRounds().isEmpty()) {
            for (TournamentMatch m : matchesToSave) {
                boolean isOpen = request.initialOpenRounds().contains(m.getRoundIndex());
                m.setClosed(!isOpen);
            }
        } else {
            // Mặc định: Chỉ mở Vòng 0, các vòng sau đóng chờ vòng trước hoàn thành
            for (TournamentMatch m : matchesToSave) {
                m.setClosed(m.getRoundIndex() != 0);
            }
        }

        matches.saveAll(matchesToSave);
        advance(t.getId());
        return detail(t.getId(), null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return tournaments.findAll().stream()
                .sorted(Comparator.comparing(Tournament::getCreatedAt).reversed())
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id, String voterKey) {
        Tournament t = tournaments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giải đấu."));
        List<TournamentMatch> all = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(id);
        List<Map<String, Object>> roundDtos = new ArrayList<>();
        Map<Integer, List<TournamentMatch>> grouped = all.stream().collect(Collectors.groupingBy(TournamentMatch::getRoundIndex, TreeMap::new, Collectors.toList()));

        int totalSongs = t.getSongs().size();
        for (var entry : grouped.entrySet()) {
            List<Map<String, Object>> md = new ArrayList<>();
            for (TournamentMatch m : entry.getValue()) {
                long a = m.getSongA() == null ? 0 : votes.countByMatchIdAndSongId(m.getId(), m.getSongA().getId());
                long b = m.getSongB() == null ? 0 : votes.countByMatchIdAndSongId(m.getId(), m.getSongB().getId());
                boolean hasVoted = voterKey != null && !voterKey.isBlank() && votes.existsByMatchIdAndVoterKey(m.getId(), voterKey);
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", m.getId());
                dto.put("position", m.getPosition());
                dto.put("groupName", m.getGroupName());
                dto.put("isWildcard", m.isWildcard());
                dto.put("songA", songDto(m.getSongA()));
                dto.put("songB", songDto(m.getSongB()));
                dto.put("winner", songDto(m.getWinner()));
                dto.put("votesA", a);
                dto.put("votesB", b);
                dto.put("closed", m.isClosed() || !t.isActive() || (t.getEndTime() != null && Instant.now().isAfter(t.getEndTime())));
                dto.put("hasVoted", hasVoted);
                dto.put("bye", m.getSongA() != null && m.getSongB() == null && m.getWinner() != null);
                md.add(dto);
            }
            boolean allClosed = entry.getValue().stream().allMatch(TournamentMatch::isClosed);
            boolean allCompleted = entry.getValue().stream().allMatch(m -> m.getWinner() != null);
            Map<String, Object> rd = new LinkedHashMap<>();
            rd.put("index", entry.getKey());
            rd.put("name", roundName(entry.getKey(), grouped.size(), totalSongs));
            rd.put("closed", allClosed);
            rd.put("completed", allCompleted);
            rd.put("matches", md);
            roundDtos.add(rd);
        }

        // Lấy lịch sử đặc cách
        List<WildcardSelection> wcList = wildcards.findByTournamentIdOrderBySelectedAtAsc(id);
        List<Map<String, Object>> wcDtos = wcList.stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("song", songDto(w.getSong()));
            m.put("fromRoundIndex", w.getFromRoundIndex());
            m.put("votesReceived", w.getVotesReceived());
            m.put("totalMatchVotes", w.getTotalMatchVotes());
            m.put("voteRatioPercent", w.getVoteRatioPercent());
            m.put("reason", w.getReason());
            return m;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", t.getId());
        out.put("name", t.getName());
        out.put("createdAt", t.getCreatedAt());
        out.put("active", t.isActive());
        out.put("endTime", t.getEndTime());
        out.put("songs", t.getSongs().stream().map(this::songDto).toList());
        out.put("rounds", roundDtos);
        out.put("wildcards", wcDtos);
        out.put("champion", all.isEmpty() ? null : songDto(all.stream().filter(m -> m.getRoundIndex() == grouped.size() - 1).map(TournamentMatch::getWinner).filter(Objects::nonNull).findFirst().orElse(null)));
        return out;
    }

    @Transactional
    public Map<String, Object> vote(Long tournamentId, Long matchId, VoteRequest request) {
        Tournament t = tournaments.findById(tournamentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giải đấu."));
        if (!t.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Giải đấu đã đóng.");
        if (t.getEndTime() != null && Instant.now().isAfter(t.getEndTime())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã hết thời gian bình chọn.");
        TournamentMatch m = matches.findByIdAndTournamentId(matchId, tournamentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cặp đấu."));
        if (m.isClosed()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cặp đấu đã đóng hoặc chưa mở bình chọn.");
        if (m.getWinner() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cặp đấu đã có kết quả.");
        Song selected = null;
        if (m.getSongA() != null && m.getSongA().getId().equals(request.songId())) selected = m.getSongA();
        if (m.getSongB() != null && m.getSongB().getId().equals(request.songId())) selected = m.getSongB();
        if (selected == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bài hát không thuộc cặp đấu này.");
        if (votes.existsByMatchIdAndVoterKey(matchId, request.voterKey())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã vote cặp này rồi.");
        try {
            votes.saveAndFlush(new Vote(m, request.voterKey(), selected));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã vote cặp này rồi.");
        }
        return detail(tournamentId, request.voterKey());
    }

    @Transactional
    public Map<String, Object> closeRound(Long tournamentId, int roundIndex) {
        List<TournamentMatch> round = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(tournamentId).stream()
                .filter(m -> m.getRoundIndex() == roundIndex).toList();
        if (round.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vòng.");

        round.forEach(m -> m.setClosed(true));
        for (TournamentMatch m : round) {
            if (m.getWinner() == null && m.getSongA() != null && m.getSongB() != null) {
                long a = votes.countByMatchIdAndSongId(m.getId(), m.getSongA().getId());
                long b = votes.countByMatchIdAndSongId(m.getId(), m.getSongB().getId());
                if (a > b) m.setWinner(m.getSongA());
                else if (b > a) m.setWinner(m.getSongB());
                else {
                    // Xử lý hòa: Random công bằng nếu phiếu bằng nhau
                    m.setWinner(Math.random() < 0.5 ? m.getSongA() : m.getSongB());
                }
            }
        }

        // Đẩy kết quả sang vòng tiếp theo (Advance)
        advance(tournamentId);

        // Mở bình chọn cho vòng tiếp theo nếu có
        List<TournamentMatch> nextRound = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(tournamentId).stream()
                .filter(m -> m.getRoundIndex() == roundIndex + 1).toList();
        if (!nextRound.isEmpty()) {
            nextRound.forEach(m -> m.setClosed(false));
        }

        return detail(tournamentId, null);
    }

    @Transactional
    public Map<String, Object> openRound(Long tournamentId, int roundIndex) {
        List<TournamentMatch> round = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(tournamentId).stream().filter(m -> m.getRoundIndex() == roundIndex).toList();
        if (round.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vòng.");
        round.forEach(m -> m.setClosed(false));
        return detail(tournamentId, null);
    }

    @Transactional
    public Map<String, Object> setRoundVoting(Long tournamentId, int roundIndex, boolean open) {
        List<TournamentMatch> round = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(tournamentId).stream().filter(m -> m.getRoundIndex() == roundIndex).toList();
        if (round.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vòng.");
        round.forEach(m -> m.setClosed(!open));
        return detail(tournamentId, null);
    }

    @Transactional
    public Map<String, Object> setEndTime(Long tournamentId, Instant endTime) {
        Tournament t = tournaments.findById(tournamentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giải đấu."));
        t.setEndTime(endTime);
        return detail(tournamentId, null);
    }

    @Transactional
    public Map<String, Object> setActive(Long tournamentId, boolean active) {
        Tournament t = tournaments.findById(tournamentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giải đấu."));
        t.setActive(active);
        return detail(tournamentId, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> results(Long id) { return detail(id, null); }

    /**
     * THUẬT TOÁN ĐIỀU CHUYỂN BÀI THẮNG & XÉT ĐẶC CÁCH CHÍNH XÁC
     */
    private void advance(Long tournamentId) {
        Tournament t = tournaments.findById(tournamentId).orElse(null);
        if (t == null) return;
        int totalSongs = t.getSongs().size();

        List<TournamentMatch> all = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(tournamentId);
        Map<Integer, List<TournamentMatch>> grouped = all.stream().collect(Collectors.groupingBy(TournamentMatch::getRoundIndex, TreeMap::new, Collectors.toList()));

        if (totalSongs == 48) {
            // ==========================================
            // THUẬT TOÁN 48 BÀI: BẢNG A/B -> PLAY-OFF -> TỨ KẾT
            // ==========================================
            List<TournamentMatch> r0 = grouped.get(0); // 24 trận (12 A, 12 B)
            List<TournamentMatch> r1 = grouped.get(1); // 12 trận (6 A, 6 B)
            List<TournamentMatch> r2 = grouped.get(2); // 6 trận Play-off (A vs B)
            List<TournamentMatch> r3 = grouped.get(3); // 4 trận Tứ kết
            List<TournamentMatch> r4 = grouped.get(4); // 2 trận Bán kết
            List<TournamentMatch> r5 = grouped.get(5); // 1 trận Chung kết

            // Bước 1: Từ Vòng 0 -> Vòng 1 (Đấu loại nội bộ Bảng A và Bảng B)
            if (r0 != null && r1 != null) {
                // Nhánh A (6 trận): lấy từ 12 trận đầu của r0
                for (int i = 0; i < 6; i++) {
                    TournamentMatch m = r1.get(i);
                    Song a = (2 * i < 12) ? r0.get(2 * i).getWinner() : null;
                    Song b = (2 * i + 1 < 12) ? r0.get(2 * i + 1).getWinner() : null;
                    updateEntrantsIfChanged(m, a, b);
                }
                // Nhánh B (6 trận): lấy từ 12 trận sau của r0 (vị trí 12..23)
                for (int i = 0; i < 6; i++) {
                    TournamentMatch m = r1.get(6 + i);
                    Song a = (12 + 2 * i < r0.size()) ? r0.get(12 + 2 * i).getWinner() : null;
                    Song b = (12 + 2 * i + 1 < r0.size()) ? r0.get(12 + 2 * i + 1).getWinner() : null;
                    updateEntrantsIfChanged(m, a, b);
                }
            }

            // Bước 2: Từ Vòng 1 -> Vòng 2 (Play-off: 6 đại diện A gặp 6 đại diện B)
            if (r1 != null && r2 != null) {
                for (int i = 0; i < 6; i++) {
                    TournamentMatch m = r2.get(i);
                    Song a = r1.get(i).getWinner();
                    Song b = r1.get(6 + i).getWinner();
                    updateEntrantsIfChanged(m, a, b);
                }
            }

            // Bước 3: Từ Vòng 2 (Play-off) -> Vòng 3 (Tứ kết)
            // Lấy 6 bài thắng trực tiếp + 2 bài thua có thành tích tốt nhất (Wildcard)
            if (r2 != null && r3 != null && r2.stream().allMatch(m -> m.getWinner() != null)) {
                List<Song> directWinners = r2.stream().map(TournamentMatch::getWinner).filter(Objects::nonNull).toList();

                // Tìm 2 vé đặc cách (Wildcard) từ 6 bài thua ở Play-off
                class LoserStat {
                    final Song song;
                    final long votes;
                    final long totalVotes;
                    final double ratio;
                    LoserStat(Song song, long votes, long totalVotes, double ratio) {
                        this.song = song; this.votes = votes; this.totalVotes = totalVotes; this.ratio = ratio;
                    }
                }

                List<LoserStat> losers = new ArrayList<>();
                for (TournamentMatch m : r2) {
                    Song loser = (m.getWinner().getId().equals(m.getSongA().getId())) ? m.getSongB() : m.getSongA();
                    if (loser == null) continue;
                    long vA = votes.countByMatchIdAndSongId(m.getId(), m.getSongA().getId());
                    long vB = votes.countByMatchIdAndSongId(m.getId(), m.getSongB().getId());
                    long loserVotes = loser.getId().equals(m.getSongA().getId()) ? vA : vB;
                    long tot = vA + vB;
                    double ratio = tot > 0 ? ((double) loserVotes / tot) * 100.0 : 0.0;
                    losers.add(new LoserStat(loser, loserVotes, tot, ratio));
                }

                // Sắp xếp: Ưu tiên 1: Tỷ lệ % phiếu cao nhất -> Ưu tiên 2: Tổng số phiếu tuyệt đối
                losers.sort((x, y) -> {
                    int c = Double.compare(y.ratio, x.ratio);
                    if (c != 0) return c;
                    return Long.compare(y.votes, x.votes);
                });

                // Lưu lịch sử đặc cách vào DB
                wildcards.deleteByTournamentId(tournamentId);
                List<Song> wildcardWinners = new ArrayList<>();
                for (int i = 0; i < Math.min(2, losers.size()); i++) {
                    LoserStat ls = losers.get(i);
                    wildcardWinners.add(ls.song);
                    String reason = String.format("Hạng %d Play-off: %.1f%% phiếu (%d/%d vote)", i + 1, ls.ratio, ls.votes, ls.totalVotes);
                    wildcards.save(new WildcardSelection(t, ls.song, 2, ls.votes, ls.totalVotes, ls.ratio, reason));
                }

                // Tổng cộng 8 bài vào Tứ kết: 6 directWinners + 2 wildcardWinners
                List<Song> qfEntrants = new ArrayList<>(directWinners);
                qfEntrants.addAll(wildcardWinners);

                // Xếp vào 4 trận Tứ kết (Trận 1: 0 vs 1, Trận 2: 2 vs 3, Trận 3: 4 vs 5, Trận 4: 6 vs 7)
                for (int i = 0; i < r3.size(); i++) {
                    TournamentMatch m = r3.get(i);
                    Song a = (i * 2 < qfEntrants.size()) ? qfEntrants.get(i * 2) : null;
                    Song b = (i * 2 + 1 < qfEntrants.size()) ? qfEntrants.get(i * 2 + 1) : null;
                    boolean isWcMatch = (a != null && wildcardWinners.contains(a)) || (b != null && wildcardWinners.contains(b));
                    m.setWildcard(isWcMatch);
                    updateEntrantsIfChanged(m, a, b);
                }
            }

            // Bước 4: Từ Tứ kết (r3) -> Bán kết (r4)
            if (r3 != null && r4 != null) {
                advanceDirect(r3, r4);
            }

            // Bước 5: Từ Bán kết (r4) -> Chung kết (r5)
            if (r4 != null && r5 != null) {
                advanceDirect(r4, r5);
            }

        } else {
            // ==========================================
            // THUẬT TOÁN TIÊU CHUẨN CHO MỌI SỐ LƯỢNG BÀI KHÁC
            // ==========================================
            for (int r = 1; r < grouped.size(); r++) {
                List<TournamentMatch> prev = grouped.get(r - 1);
                List<TournamentMatch> cur = grouped.get(r);
                if (prev == null || cur == null) continue;
                advanceDirect(prev, cur);
            }
        }
    }

    private void advanceDirect(List<TournamentMatch> prev, List<TournamentMatch> cur) {
        for (TournamentMatch m : cur) {
            int idx = m.getPosition() * 2;
            Song a = idx < prev.size() ? prev.get(idx).getWinner() : null;
            Song b = idx + 1 < prev.size() ? prev.get(idx + 1).getWinner() : null;
            updateEntrantsIfChanged(m, a, b);
        }
    }

    private void updateEntrantsIfChanged(TournamentMatch m, Song a, Song b) {
        boolean changed = !Objects.equals(m.getSongA() == null ? null : m.getSongA().getId(), a == null ? null : a.getId())
                || !Objects.equals(m.getSongB() == null ? null : m.getSongB().getId(), b == null ? null : b.getId());
        if (changed) {
            m.getVotes().clear();
            m.setWinner(null);
            m.setEntrants(a, b);
            if (a != null && b == null) m.setWinner(a);
            else if (b != null && a == null) m.setWinner(b);
        }
    }

    private Map<String, Object> summary(Tournament t) {
        List<TournamentMatch> ms = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(t.getId());
        long done = ms.stream().filter(m -> m.getWinner() != null && m.getSongA() != null && m.getSongB() != null).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", t.getId());
        out.put("name", t.getName());
        out.put("createdAt", t.getCreatedAt());
        out.put("songCount", t.getSongs().size());
        out.put("active", t.isActive());
        out.put("completedMatches", done);
        out.put("champion", ms.stream().filter(m -> m.getRoundIndex() == ms.stream().mapToInt(TournamentMatch::getRoundIndex).max().orElse(0)).map(TournamentMatch::getWinner).filter(Objects::nonNull).findFirst().map(this::songDto).orElse(null));
        return out;
    }

    private Map<String, Object> songDto(Song s) {
        if (s == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("title", s.getTitle());
        m.put("artist", s.getArtist());
        m.put("link", s.getLink());
        return m;
    }

    private String roundName(int index, int totalRounds, int totalSongs) {
        if (totalSongs == 48) {
            if (index == 0) return "Vòng loại 1 (Bảng A & B)";
            if (index == 1) return "Vòng loại 2 (Bảng A & B)";
            if (index == 2) return "Vòng Play-off (6 cặp)";
            if (index == 3) return "Tứ kết (8 bài)";
            if (index == 4) return "Bán kết (4 bài)";
            if (index == 5) return "Chung kết";
        }
        if (index == totalRounds - 1) return "Chung kết";
        if (index == totalRounds - 2) return "Bán kết";
        if (index == totalRounds - 3) return "Tứ kết";
        if (index == 0) return "Vòng 1";
        return "Vòng " + (index + 1);
    }
}
