package com.mateforge.api.dto;

import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.TournamentStatus;
import com.mateforge.api.model.TrainingMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TournamentDtos {
    private TournamentDtos() {
    }

    public record CreateTournamentRequest(
        @NotBlank String name,
        @NotNull TrainingMode mode,
        @NotNull Difficulty difficulty,
        @Min(30) @Max(7200) int timeLimitSeconds,
        @Min(2) @Max(200) int maxPlayers
    ) {
    }

    public record TournamentParticipantDto(
        UUID userId,
        String username,
        int score,
        int bestTimeSeconds,
        double bestAccuracy
    ) {
    }

    public record TournamentDto(
        UUID id,
        String name,
        String joinCode,
        String shareUrl,
        TournamentStatus status,
        TrainingMode mode,
        Difficulty difficulty,
        int timeLimitSeconds,
        int maxPlayers,
        int playerCount,
        Instant createdAt,
        List<TournamentParticipantDto> participants
    ) {
    }
}
