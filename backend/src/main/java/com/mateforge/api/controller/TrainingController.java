package com.mateforge.api.controller;

import com.mateforge.api.dto.TrainingDtos.HintResponse;
import com.mateforge.api.dto.TrainingDtos.MoveRequest;
import com.mateforge.api.dto.TrainingDtos.MoveResponse;
import com.mateforge.api.dto.TrainingDtos.SessionDto;
import com.mateforge.api.dto.TrainingDtos.SolutionResponse;
import com.mateforge.api.dto.TrainingDtos.StartSessionRequest;
import com.mateforge.api.security.UserPrincipal;
import com.mateforge.api.service.TrainingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class TrainingController {
    private final TrainingService training;

    public TrainingController(TrainingService training) {
        this.training = training;
    }

    @PostMapping
    SessionDto start(@Valid @RequestBody StartSessionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return training.start(request, principal);
    }

    @GetMapping("/{id}")
    SessionDto get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return training.get(id, principal);
    }

    @GetMapping
    List<SessionDto> history(@AuthenticationPrincipal UserPrincipal principal) {
        return training.history(principal);
    }

    @PostMapping("/{id}/moves")
    MoveResponse move(@PathVariable UUID id, @Valid @RequestBody MoveRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return training.play(id, request.uci(), principal);
    }

    @PostMapping("/{id}/hint")
    HintResponse hint(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return training.hint(id, principal);
    }

    @PostMapping("/{id}/undo")
    SessionDto undo(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return training.undo(id, principal);
    }

    @GetMapping("/{id}/solution")
    SolutionResponse solution(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return training.solution(id, principal);
    }
}
