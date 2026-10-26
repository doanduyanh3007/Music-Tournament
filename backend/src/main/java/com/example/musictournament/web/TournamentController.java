package com.example.musictournament.web;

import com.example.musictournament.dto.*;
import com.example.musictournament.service.TournamentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TournamentController {
    private final TournamentService service;
    @Value("${app.admin-token}") private String adminToken;
    public TournamentController(TournamentService service){this.service=service;}

    @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok","service","music-tournament-api");}
    @GetMapping("/tournaments") public List<Map<String,Object>> list(){return service.list();}
    @PostMapping("/tournaments") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@Valid @RequestBody CreateTournamentRequest request){return service.create(request);}
    @GetMapping("/tournaments/{id}") public Map<String,Object> detail(@PathVariable Long id,@RequestParam(required=false) String voterKey){return service.detail(id,voterKey);}
    @GetMapping("/tournaments/{id}/results") public Map<String,Object> results(@PathVariable Long id){return service.results(id);}
    @PostMapping("/tournaments/{id}/matches/{matchId}/vote")
    public Map<String,Object> vote(@PathVariable Long id,@PathVariable Long matchId,@Valid @RequestBody VoteRequest request){return service.vote(id,matchId,request);}
    @PostMapping("/tournaments/{id}/admin/rounds/{roundIndex}/close")
    public Map<String,Object> close(@PathVariable Long id,@PathVariable int roundIndex){return service.closeRound(id,roundIndex);}
    @PostMapping("/tournaments/{id}/admin/rounds/{roundIndex}/open")
    public Map<String,Object> open(@PathVariable Long id,@PathVariable int roundIndex){return service.openRound(id,roundIndex);}
    @PutMapping("/tournaments/{id}/admin/rounds/{roundIndex}/voting")
    public Map<String,Object> voting(@PathVariable Long id,@PathVariable int roundIndex,@RequestParam boolean open){return service.setRoundVoting(id,roundIndex,open);}
    @PutMapping("/tournaments/{id}/admin/end-time")
    public Map<String,Object> endTime(@PathVariable Long id,@RequestBody EndTimeRequest req){return service.setEndTime(id,req.endTime());}
    @PutMapping("/tournaments/{id}/admin/status")
    public Map<String,Object> status(@PathVariable Long id,@RequestParam boolean active){return service.setActive(id,active);}
    @PostMapping("/admin/verify")
    public Map<String, Object> verifyAdmin(@RequestBody(required = false) Map<String, String> body) {
        String token = body != null ? body.get("token") : null;
        if (token != null && token.trim().equals(adminToken.trim())) {
            return Map.of("valid", true, "role", "ADMIN", "message", "Xác thực quản trị viên thành công!");
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Mã quản trị viên không chính xác.");
    }

    @GetMapping("/tournaments/{id}/leaderboard")
    public List<Map<String, Object>> leaderboard(@PathVariable Long id) {
        return service.leaderboard(id);
    }
}
