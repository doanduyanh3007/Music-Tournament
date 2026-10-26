package com.example.musictournament;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:voting-features;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
class VotingFeaturesTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    JsonNode createTournament() throws Exception {
        String body = json.writeValueAsString(Map.of("name", "Test Cup", "songs",
                List.of("Idol — YOASOBI", "Gurenge — LiSA", "Sparkle — RADWIMPS", "SPECIALZ — King Gnu")));
        JsonNode created = json.readTree(mvc.perform(post("/api/tournaments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return json.readTree(mvc.perform(get("/api/tournaments/" + created.get("id").asLong()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    String voteUrl(JsonNode tournament, JsonNode match) {
        return "/api/tournaments/" + tournament.get("id").asLong() + "/matches/" + match.get("id").asLong();
    }

    void vote(JsonNode t, JsonNode m, String voter, String name, String reason, int expected) throws Exception {
        mvc.perform(post(voteUrl(t,m) + "/vote").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("voterKey",voter,"songId",m.get("songA").get("id").asLong(),"displayName",name,"reason",reason))))
                .andExpect(status().is(expected));
    }

    @Test void storesNamesReasonsAndPaginatesWithoutExposingVoterKeys() throws Exception {
        JsonNode t = createTournament(), m = t.get("rounds").get(0).get("matches").get(0);
        vote(t,m,"browser-one","  Duy Anh  ","  Giai điệu hay!  ",200);
        vote(t,m,"browser-two","Khánh","",200);
        vote(t,m,"browser-one","Tên khác","",409);
        String detail = mvc.perform(get(voteUrl(t,m) + "/votes").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].displayName").value("Khánh"))
                .andExpect(jsonPath("$.items[0].reason").value(""))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain("voterKey", "browser-one", "browser-two");
        mvc.perform(get(voteUrl(t,m) + "/votes").param("size","1").param("page","1"))
                .andExpect(jsonPath("$.items[0].displayName").value("Duy Anh"))
                .andExpect(jsonPath("$.items[0].reason").value("Giai điệu hay!"));
        mvc.perform(get(voteUrl(t,m) + "/votes").param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get(voteUrl(t,m) + "/votes").param("size","101")).andExpect(status().isBadRequest());
        JsonNode other = createTournament();
        mvc.perform(get("/api/tournaments/"+other.get("id").asLong()+"/matches/"+m.get("id").asLong()+"/votes"))
                .andExpect(status().isNotFound());
    }

    @Test void ranksByParticipationThenWinningVotesAndKeepsSameNamesSeparate() throws Exception {
        JsonNode t = createTournament(), ms = t.get("rounds").get(0).get("matches");
        vote(t,ms.get(0),"one","Same name","",200);
        vote(t,ms.get(1),"one","Renamed player","",200);
        vote(t,ms.get(0),"two","Same name","",200);
        vote(t,ms.get(1),"three","Same name","",200);
        String path = "/api/tournaments/"+t.get("id").asLong();
        mvc.perform(post(path+"/admin/rounds/0/close")).andExpect(status().isOk());
        String body = mvc.perform(get(path+"/players").param("voterKey","one"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].displayName").value("Renamed player"))
                .andExpect(jsonPath("$[0].totalVotes").value(2)).andExpect(jsonPath("$[0].winningVotes").value(2))
                .andExpect(jsonPath("$[0].decidedVotes").value(2)).andExpect(jsonPath("$[0].isCurrentPlayer").value(true))
                .andExpect(jsonPath("$[1].rank").value(2)).andExpect(jsonPath("$[2].rank").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("voterKey");
        mvc.perform(get("/api/tournaments/"+createTournament().get("id").asLong()+"/players"))
                .andExpect(content().json("[]"));
    }

    @Test void validatesNameReasonAndSelectedSongBeforeSaving() throws Exception {
        JsonNode t = createTournament(), m = t.get("rounds").get(0).get("matches").get(0);
        vote(t,m,"invalid","   ","",400);
        vote(t,m,"invalid","x".repeat(51),"",400);
        vote(t,m,"invalid","Valid","x".repeat(1001),400);
        mvc.perform(post(voteUrl(t,m)+"/vote").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("voterKey","invalid","displayName","Valid","songId",-1))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(voteUrl(t,m)+"/vote").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("voterKey","optional-reason","displayName","Valid","songId",m.get("songA").get("id").asLong()))))
                .andExpect(status().isOk());
    }

    @Test void rejectsDuplicateMetadataAndRecordingUrlsButAllowsDifferentArtists() throws Exception {
        for (List<String> songs : List.of(
                List.of("Idol — YOASOBI", "  ＩＤＯＬ — yoasobi  "),
                List.of("1. Idol — YOASOBI", "Idol\tYOASOBI"),
                List.of("Song A — A | https://youtu.be/ZRtdQ81jPUQ", "Other title — B | https://www.youtube.com/watch?v=ZRtdQ81jPUQ&t=30"),
                List.of("Song A — A | https://open.spotify.com/track/abc123?si=x", "Other — B | https://open.spotify.com/intl-vi/track/abc123"))) {
            mvc.perform(post("/api/tournaments").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("name","Duplicates","songs",songs))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("trùng")));
        }
        mvc.perform(post("/api/tournaments").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name","Different artists","songs", List.of("Idol — YOASOBI","Idol — Other artist")))))
                .andExpect(status().isCreated());
    }

    @Test void preservesLegacyVotesWithNoNameOrReason() throws Exception {
        JsonNode t = createTournament(), m = t.get("rounds").get(0).get("matches").get(0);
        jdbc.update("insert into votes (match_id, song_id, voter_key, created_at) values (?, ?, ?, CURRENT_TIMESTAMP)",
                m.get("id").asLong(), m.get("songA").get("id").asLong(), "legacy-browser");
        mvc.perform(get(voteUrl(t,m)+"/votes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].displayName").value("Người chơi cũ"))
                .andExpect(jsonPath("$.items[0].reason").value(""));
        mvc.perform(get("/api/tournaments/"+t.get("id").asLong()+"/players"))
                .andExpect(jsonPath("$[0].totalVotes").value(1));
        vote(t,m,"legacy-browser","New name","",409);
    }

    JsonNode createBracket(int count) throws Exception {
        var songs = IntStream.rangeClosed(1, count).mapToObj(i -> "Track " + i + " — Artist " + i).toList();
        return json.readTree(mvc.perform(post("/api/tournaments").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Bracket QA", "songs", songs))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.songs.length()").value(count))
                .andReturn().getResponse().getContentAsString());
    }

    JsonNode close(JsonNode t, int round) throws Exception {
        return json.readTree(mvc.perform(post("/api/tournaments/" + t.get("id").asLong() + "/admin/rounds/" + round + "/close"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void onlyCompletedFinalProducesChampionAcrossDetailResultsListAndLeaderboard() throws Exception {
        JsonNode t = createBracket(64);
        assertThat(t.get("champion").isNull()).isTrue();
        for (int round = 0; round < 6; round++) {
            if (round == 5) {
                mvc.perform(get("/api/tournaments/"+t.get("id").asLong()+"/leaderboard"))
                        .andExpect(jsonPath("$[?(@.tier == 1)]").isEmpty());
            }
            t = close(t, round);
            assertThat(t.get("champion").isNull()).isEqualTo(round < 5);
        }
        long id = t.get("id").asLong(), champ = t.get("champion").get("id").asLong();
        mvc.perform(get("/api/tournaments/"+id+"/results")).andExpect(jsonPath("$.champion.id").value(champ));
        JsonNode list = json.readTree(mvc.perform(get("/api/tournaments")).andReturn().getResponse().getContentAsString());
        for (JsonNode item : list) if (item.get("id").asLong() == id) assertThat(item.get("champion").get("id").asLong()).isEqualTo(champ);
        mvc.perform(get("/api/tournaments/"+id+"/leaderboard")).andExpect(jsonPath("$[0].tier").value(1))
                .andExpect(jsonPath("$[0].song.id").value(champ));
    }

    @Test void byeDoesNotWinPendingFinalAndLegacyPrematureWinnerIsNotPublished() throws Exception {
        JsonNode t = createBracket(3), fin = t.get("rounds").get(1).get("matches").get(0);
        assertThat(fin.get("songA").isNull()).isFalse();
        assertThat(fin.get("songB").isNull()).isTrue();
        assertThat(fin.get("winner").isNull()).isTrue();
        assertThat(t.get("champion").isNull()).isTrue();
        jdbc.update("update tournament_matches set winner_id=? where id=?", fin.get("songA").get("id").asLong(), fin.get("id").asLong());
        mvc.perform(get("/api/tournaments/"+t.get("id").asLong())).andExpect(jsonPath("$.champion").isEmpty());
        t = close(t,0);
        assertThat(t.get("champion").isNull()).isTrue();
        assertThat(t.get("rounds").get(1).get("matches").get(0).get("winner").isNull()).isTrue();
        t = close(t,1);
        assertThat(t.get("champion").isNull()).isFalse();
    }

    @Test void resetRemovesChampionAndFinalistsWithoutLeakingPreviousResult() throws Exception {
        JsonNode t = close(close(createBracket(4),0),1);
        long id = t.get("id").asLong();
        assertThat(t.get("champion").isNull()).isFalse();
        // Fixture simulates an administrator resetting bracket data; no production reset is performed.
        jdbc.update("update tournament_matches set winner_id=null where tournament_id=?", id);
        jdbc.update("update tournament_matches set song_a_id=null, song_b_id=null where tournament_id=? and round_index>0", id);
        mvc.perform(get("/api/tournaments/"+id)).andExpect(jsonPath("$.champion").isEmpty());
        mvc.perform(get("/api/tournaments/"+id+"/results")).andExpect(jsonPath("$.champion").isEmpty());
        mvc.perform(get("/api/tournaments/"+id+"/leaderboard"))
                .andExpect(jsonPath("$[?(@.tier == 1)]").isEmpty());
    }

    @Test void fortyEightSongWildcardBracketStillReachesARealFinal() throws Exception {
        JsonNode t = createBracket(48);
        for (int round = 0; round < 6; round++) {
            t = close(t,round);
            assertThat(t.get("champion").isNull()).isEqualTo(round < 5);
        }
        assertThat(t.get("wildcards").size()).isEqualTo(2);
        JsonNode fin = t.get("rounds").get(5).get("matches").get(0);
        assertThat(fin.get("songA").isNull()).isFalse();
        assertThat(fin.get("songB").isNull()).isFalse();
    }

    @Test void artistSeparationPreservesEveryEntrantExactlyOnce() throws Exception {
        var songs = IntStream.range(0,64).mapToObj(i -> "Track " + i + " — Artist " + (i % 9)).toList();
        JsonNode t = json.readTree(mvc.perform(post("/api/tournaments").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name","Artist seeding QA","songs",songs))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        java.util.Set<Long> expected = new java.util.HashSet<>(), seeded = new java.util.HashSet<>();
        t.get("songs").forEach(s -> expected.add(s.get("id").asLong()));
        for (JsonNode match : t.get("rounds").get(0).get("matches")) {
            assertThat(seeded.add(match.get("songA").get("id").asLong())).isTrue();
            assertThat(seeded.add(match.get("songB").get("id").asLong())).isTrue();
        }
        assertThat(seeded).isEqualTo(expected);
    }

    @Test void nonPowerOfTwoAndLargeBracketsStillAdvanceToAValidFinal() throws Exception {
        for (int count : new int[]{5,65}) {
            JsonNode t = createBracket(count);
            assertThat(t.get("champion").isNull()).isTrue();
            int rounds = t.get("rounds").size();
            for (int round = 0; round < rounds; round++) t = close(t,round);
            assertThat(t.get("champion").isNull()).isFalse();
        }
    }
}
