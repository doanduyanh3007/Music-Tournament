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
    private final EntityManager em;

    public TournamentService(TournamentRepository tournaments, SongRepository songs,
                             MatchRepository matches, VoteRepository votes, EntityManager em) {
        this.tournaments=tournaments; this.songs=songs; this.matches=matches; this.votes=votes; this.em=em;
    }

    @Transactional
    public Map<String,Object> create(CreateTournamentRequest request) {
        Tournament t = tournaments.save(new Tournament(request.name().trim()));
        List<Song> songEntities = new ArrayList<>();
        for (String raw : request.songs()) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            // Extract link: "Title — Artist | https://..."
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
        
        // High-speed batch insert
        List<Song> entrants = songs.saveAll(songEntities);

        if (request.shouldShuffle()) {
            Collections.shuffle(entrants);
        }

        List<TournamentMatch> matchesToSave = new ArrayList<>();
        int totalSongs = entrants.size();

        if (totalSongs > 48) {
            // Rule: 1 vòng tối đa 48 bài (24 cặp). Nếu vượt quá 48 bài, tự động chia đều làm 2 vòng:
            // Vòng 0: Vòng loại 1 (nửa đầu)
            // Vòng 1: Vòng loại 2 (nửa sau)
            // Vòng 2 trở đi: Ghép các người thắng từ 2 vòng loại vào nhánh đấu chính (Vòng chính, Tứ kết, Bán kết, Chung kết...)
            int half1 = totalSongs / 2;
            List<Song> group1 = entrants.subList(0, half1);
            List<Song> group2 = entrants.subList(half1, totalSongs);

            // Xếp Vòng 0 (Vòng loại 1)
            int pos0 = 0;
            for (int i = 0; i < group1.size(); i += 2) {
                Song a = group1.get(i);
                Song b = (i + 1 < group1.size()) ? group1.get(i + 1) : null;
                TournamentMatch m = new TournamentMatch(t, 0, pos0++, a, b);
                if (b == null) m.setWinner(a);
                matchesToSave.add(m);
            }

            // Xếp Vòng 1 (Vòng loại 2)
            int pos1 = 0;
            for (int i = 0; i < group2.size(); i += 2) {
                Song a = group2.get(i);
                Song b = (i + 1 < group2.size()) ? group2.get(i + 1) : null;
                TournamentMatch m = new TournamentMatch(t, 1, pos1++, a, b);
                if (b == null) m.setWinner(a);
                matchesToSave.add(m);
            }

            // Tổng số người thắng sau 2 vòng loại: pos0 + pos1
            int qualifiers = pos0 + pos1;
            int mainSize = 1; while (mainSize < qualifiers) mainSize *= 2;
            int nextRoundMatches = mainSize / 2;
            int roundIdx = 2;
            while (nextRoundMatches >= 1) {
                for (int i = 0; i < nextRoundMatches; i++) {
                    matchesToSave.add(new TournamentMatch(t, roundIdx, i, null, null));
                }
                nextRoundMatches /= 2;
                roundIdx++;
            }
        } else {
            // Chuẩn thông thường khi <= 48 bài (tối đa 24 cặp)
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

        // Configure initial round open/close if specified
        if (request.initialOpenRounds() != null && !request.initialOpenRounds().isEmpty()) {
            for (TournamentMatch m : matchesToSave) {
                boolean isOpen = request.initialOpenRounds().contains(m.getRoundIndex());
                m.setClosed(!isOpen);
            }
        }

        // High-speed batch insert matches
        matches.saveAll(matchesToSave);

        advance(t.getId());
        return detail(t.getId(), null);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> list() {
        return tournaments.findAll().stream()
            .sorted(Comparator.comparing(Tournament::getCreatedAt).reversed())
            .map(t -> summary(t)).toList();
    }

    @Transactional(readOnly=true)
    public Map<String,Object> detail(Long id, String voterKey) {
        Tournament t = tournaments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giải đấu."));
        List<TournamentMatch> all = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(id);
        List<Map<String,Object>> roundDtos = new ArrayList<>();
        Map<Integer,List<TournamentMatch>> grouped = all.stream().collect(Collectors.groupingBy(TournamentMatch::getRoundIndex, TreeMap::new, Collectors.toList()));
        for (var entry : grouped.entrySet()) {
            List<Map<String,Object>> md = new ArrayList<>();
            for (TournamentMatch m : entry.getValue()) {
                long a = m.getSongA() == null ? 0 : votes.countByMatchIdAndSongId(m.getId(), m.getSongA().getId());
                long b = m.getSongB() == null ? 0 : votes.countByMatchIdAndSongId(m.getId(), m.getSongB().getId());
                boolean hasVoted = voterKey != null && !voterKey.isBlank() && votes.existsByMatchIdAndVoterKey(m.getId(), voterKey);
                Map<String,Object> dto = new LinkedHashMap<>();
                dto.put("id", m.getId()); dto.put("position", m.getPosition());
                dto.put("songA", songDto(m.getSongA())); dto.put("songB", songDto(m.getSongB()));
                dto.put("winner", songDto(m.getWinner())); dto.put("votesA", a); dto.put("votesB", b);
                dto.put("closed", m.isClosed() || !t.isActive() || (t.getEndTime() != null && Instant.now().isAfter(t.getEndTime())));
                dto.put("hasVoted", hasVoted); dto.put("bye", m.getSongA() != null && m.getSongB() == null && m.getWinner() != null);
                md.add(dto);
            }
            boolean allClosed = entry.getValue().stream().allMatch(TournamentMatch::isClosed);
            boolean allCompleted = entry.getValue().stream().allMatch(m -> m.getWinner() != null);
            Map<String,Object> rd = new LinkedHashMap<>();
            rd.put("index", entry.getKey());
            rd.put("name", roundName(entry.getKey(), grouped.size()));
            rd.put("closed", allClosed);
            rd.put("completed", allCompleted);
            rd.put("matches", md);
            roundDtos.add(rd);
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", t.getId()); out.put("name", t.getName()); out.put("createdAt", t.getCreatedAt());
        out.put("active", t.isActive()); out.put("endTime", t.getEndTime()); out.put("songs", t.getSongs().stream().map(this::songDto).toList());
        out.put("rounds", roundDtos);
        out.put("champion", all.isEmpty() ? null : songDto(all.stream().filter(m -> m.getRoundIndex() == grouped.size() - 1).map(TournamentMatch::getWinner).filter(Objects::nonNull).findFirst().orElse(null)));
        return out;
    }

    @Transactional
    public Map<String,Object> vote(Long tournamentId, Long matchId, VoteRequest request) {
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
    public Map<String,Object> closeRound(Long tournamentId, int roundIndex) {
        List<TournamentMatch> round = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(tournamentId).stream().filter(m -> m.getRoundIndex() == roundIndex).toList();
        if (round.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vòng.");
        round.forEach(m -> m.setClosed(true));
        for (TournamentMatch m : round) {
            if (m.getWinner() == null && m.getSongA() != null && m.getSongB() != null) {
                long a = votes.countByMatchIdAndSongId(m.getId(), m.getSongA().getId()), b = votes.countByMatchIdAndSongId(m.getId(), m.getSongB().getId());
                if (a > b) m.setWinner(m.getSongA());
                else if (b > a) m.setWinner(m.getSongB());
                else m.setWinner(Math.random() < 0.5 ? m.getSongA() : m.getSongB());
            }
        }
        advance(tournamentId);
        return detail(tournamentId, null);
    }

    @Transactional
    public Map<String,Object> openRound(Long tournamentId, int roundIndex) {
        List<TournamentMatch> round = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(tournamentId).stream().filter(m -> m.getRoundIndex() == roundIndex).toList();
        if (round.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vòng.");
        round.forEach(m -> m.setClosed(false));
        return detail(tournamentId, null);
    }

    @Transactional
    public Map<String,Object> setRoundVoting(Long tournamentId, int roundIndex, boolean open) {
        List<TournamentMatch> round = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(tournamentId).stream().filter(m -> m.getRoundIndex() == roundIndex).toList();
        if (round.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vòng.");
        round.forEach(m -> m.setClosed(!open));
        return detail(tournamentId, null);
    }

    @Transactional
    public Map<String,Object> setEndTime(Long tournamentId, Instant endTime) {
        Tournament t = tournaments.findById(tournamentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giải đấu."));
        t.setEndTime(endTime);
        return detail(tournamentId, null);
    }

    @Transactional
    public Map<String,Object> setActive(Long tournamentId, boolean active) {
        Tournament t = tournaments.findById(tournamentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giải đấu."));
        t.setActive(active);
        return detail(tournamentId, null);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> results(Long id) { return detail(id, null); }

    private void advance(Long tournamentId) {
        List<TournamentMatch> all = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(tournamentId);
        Map<Integer,List<TournamentMatch>> grouped = all.stream().collect(Collectors.groupingBy(TournamentMatch::getRoundIndex, TreeMap::new, Collectors.toList()));
        boolean hasTwoQualifiers = grouped.containsKey(0) && grouped.containsKey(1) && grouped.containsKey(2) 
            && grouped.get(0).size() > 0 && grouped.get(1).size() > 0 
            && (grouped.get(0).size() + grouped.get(1).size() >= grouped.get(2).size());

        for (int r = 1; r < grouped.size(); r++) {
            List<TournamentMatch> cur = grouped.get(r);
            if (cur == null) continue;

            if (r == 1 && hasTwoQualifiers) {
                // Round 0 và Round 1 là 2 vòng loại song song (chưa advance vào nhau)
                continue;
            }

            List<TournamentMatch> prevWinnersMatches;
            if (r == 2 && hasTwoQualifiers) {
                // Ghép người thắng từ cả Round 0 và Round 1 vào Round 2
                prevWinnersMatches = new ArrayList<>();
                prevWinnersMatches.addAll(grouped.get(0));
                prevWinnersMatches.addAll(grouped.get(1));
            } else {
                prevWinnersMatches = grouped.get(r - 1);
            }
            if (prevWinnersMatches == null) continue;

            for (TournamentMatch m : cur) {
                int idx = m.getPosition() * 2;
                Song a = idx < prevWinnersMatches.size() ? prevWinnersMatches.get(idx).getWinner() : null;
                Song b = idx + 1 < prevWinnersMatches.size() ? prevWinnersMatches.get(idx + 1).getWinner() : null;
                boolean changed = !Objects.equals(m.getSongA() == null ? null : m.getSongA().getId(), a == null ? null : a.getId())
                    || !Objects.equals(m.getSongB() == null ? null : m.getSongB().getId(), b == null ? null : b.getId());
                if (changed) {
                    m.getVotes().clear();
                    m.setWinner(null);
                }
                m.setEntrants(a, b);
                if (a != null && b == null) m.setWinner(a);
                else if (b != null && a == null) m.setWinner(b);
                else if (changed) m.setWinner(null);
            }
        }
    }

    private Map<String,Object> summary(Tournament t) {
        List<TournamentMatch> ms = matches.findByTournamentIdOrderByRoundIndexAscPositionAsc(t.getId());
        long done = ms.stream().filter(m -> m.getWinner() != null && m.getSongA() != null && m.getSongB() != null).count();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", t.getId()); out.put("name", t.getName()); out.put("createdAt", t.getCreatedAt());
        out.put("songCount", t.getSongs().size()); out.put("active", t.isActive()); out.put("completedMatches", done);
        out.put("champion", ms.stream().filter(m -> m.getRoundIndex() == ms.stream().mapToInt(TournamentMatch::getRoundIndex).max().orElse(0)).map(TournamentMatch::getWinner).filter(Objects::nonNull).findFirst().map(this::songDto).orElse(null));
        return out;
    }

    private Map<String,Object> songDto(Song s) {
        if (s == null) return null;
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", s.getId()); m.put("title", s.getTitle()); m.put("artist", s.getArtist()); m.put("link", s.getLink());
        return m;
    }

    private String roundName(int index, int count) {
        if (index == count - 1) return "Chung kết";
        if (index == count - 2) return "Bán kết";
        if (index == count - 3) return "Tứ kết";
        if (index == 0) return (count > 4) ? "Vòng loại 1" : "Vòng 1";
        if (index == 1) return (count > 4) ? "Vòng loại 2" : "Vòng 2";
        return "Vòng " + (index + 1);
    }
}
