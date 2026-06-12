package com.mateforge.api.controller;

import com.mateforge.api.dto.TournamentDtos.CreateTournamentRequest;
import com.mateforge.api.dto.TournamentDtos.TournamentDto;
import com.mateforge.api.security.UserPrincipal;
import com.mateforge.api.service.TournamentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {
    private final TournamentService tournaments;

    public TournamentController(TournamentService tournaments) {
        this.tournaments = tournaments;
    }

    @PostMapping
    TournamentDto create(@Valid @RequestBody CreateTournamentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return tournaments.create(request, principal);
    }

    @GetMapping("/mine")
    List<TournamentDto> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return tournaments.mine(principal);
    }

    @GetMapping("/{joinCode}")
    TournamentDto get(@PathVariable String joinCode) {
        return tournaments.get(joinCode);
    }

    @PostMapping("/{joinCode}/join")
    TournamentDto join(@PathVariable String joinCode, @AuthenticationPrincipal UserPrincipal principal) {
        return tournaments.join(joinCode, principal);
    }
}
