package com.mateforge.api.controller;

import com.mateforge.api.dto.TournamentDtos.CreateTournamentRequest;
import com.mateforge.api.dto.TournamentDtos.StandingDto;
import com.mateforge.api.dto.TournamentDtos.SubmitResultRequest;
import com.mateforge.api.dto.TournamentDtos.TournamentDetailDto;
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
    private final TournamentService service;

    public TournamentController(TournamentService service) {
        this.service = service;
    }

    @PostMapping
    TournamentDetailDto create(@Valid @RequestBody CreateTournamentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return service.create(request, principal);
    }

    @GetMapping
    List<TournamentDto> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return service.mine(principal);
    }

    @GetMapping("/{code}")
    TournamentDetailDto detail(@PathVariable String code, @AuthenticationPrincipal UserPrincipal principal) {
        return service.detail(code, principal);
    }

    @PostMapping("/{code}/join")
    TournamentDetailDto join(@PathVariable String code, @AuthenticationPrincipal UserPrincipal principal) {
        return service.join(code, principal);
    }

    @GetMapping("/{code}/standings")
    List<StandingDto> standings(@PathVariable String code, @AuthenticationPrincipal UserPrincipal principal) {
        return service.standings(code, principal);
    }

    @PostMapping("/{code}/results")
    TournamentDetailDto submit(@PathVariable String code, @Valid @RequestBody SubmitResultRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return service.submit(code, request, principal);
    }
}
