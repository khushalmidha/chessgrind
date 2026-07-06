package com.mateforge.api.dto;

import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.TrainingMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class TournamentDtos {
    private TournamentDtos() {
    }

    public record CreateTournamentRequest(
        @NotBlank String name,
        @NotNull TrainingMode mode,
        @NotNull Difficulty difficulty,
        @Min(1) @Max(12) int rounds,
        @NotNull Instant scheduledStartAt
    ) {
    }

    public record SubmitResultRequest(
        @Min(1) int roundNumber,
        @Min(0) @Max(100) double accuracy,
        @Min(0) int timeSeconds,
        @Min(0) int hintsUsed
    ) {
    }

    public record TournamentDto(
        String code,
        String name,
        String hostUsername,
        TrainingMode mode,
        Difficulty difficulty,
        int rounds,
        Instant scheduledStartAt,
        String status,
        boolean joined,
        String joinUrl,
        int participantCount
    ) {
    }

    public record StandingDto(String username, int completedRounds, int totalPoints, double averageAccuracy, int totalTimeSeconds) {
    }

    public record TournamentDetailDto(TournamentDto tournament, List<StandingDto> standings) {
    }
}
