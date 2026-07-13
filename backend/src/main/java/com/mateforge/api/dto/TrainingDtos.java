package com.mateforge.api.dto;

import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.SessionStatus;
import com.mateforge.api.model.TimerMode;
import com.mateforge.api.model.TrainingMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TrainingDtos {
    private TrainingDtos() {
    }

    public record PuzzleDto(
        UUID id,
        TrainingMode mode,
        Difficulty difficulty,
        String title,
        String fen,
        int targetMateMoves,
        boolean dailyChallenge
    ) {
    }

    public record StartSessionRequest(
        @NotNull TrainingMode mode,
        @NotNull Difficulty difficulty,
        @NotNull TimerMode timerMode,
        @Min(0) @Max(7200) int timeLimitSeconds,
        @Min(0) @Max(60) int incrementSeconds,
        boolean hintsEnabled,
        boolean takebacksEnabled,
        UUID puzzleId,
        String customFen
    ) {
    }

    public record MoveRequest(@NotNull String uci) {
    }

    public record HintResponse(String bestMove, String san, String reason, List<String> arrows) {
    }

    public record MoveDto(
        int ply,
        String uci,
        String san,
        String fenAfter,
        boolean engineMove,
        boolean optimal,
        String reason
    ) {
    }

    public record ImportMoveDto(
        @NotNull String uci,
        boolean engineMove
    ) {
    }

    public record ImportSessionRequest(
        @NotNull TrainingMode mode,
        @NotNull Difficulty difficulty,
        @NotNull TimerMode timerMode,
        @NotNull SessionStatus status,
        @NotNull String startFen,
        @NotNull String currentFen,
        @Min(0) @Max(7200) int timeLimitSeconds,
        @Min(0) int remainingSeconds,
        @Min(0) int hintsUsed,
        @NotNull Instant startedAt,
        Instant endedAt,
        @NotNull List<ImportMoveDto> moves
    ) {
    }

    public record SessionDto(
        UUID id,
        TrainingMode mode,
        Difficulty difficulty,
        TimerMode timerMode,
        SessionStatus status,
        String startFen,
        String currentFen,
        int remainingSeconds,
        int hintsUsed,
        int mistakes,
        double accuracy,
        Instant startedAt,
        Instant endedAt,
        List<MoveDto> moves
    ) {
    }

    public record MoveResponse(
        SessionDto session,
        String userMove,
        String engineMove,
        String gameState,
        boolean check,
        boolean checkmate,
        boolean stalemate,
        String message
    ) {
    }

    public record ReviewMoveDto(
        int ply,
        String fenBefore,
        String playedMove,
        String playedSan,
        String bestMove,
        String bestSan,
        String bestFenAfter,
        boolean optimal,
        String comment
    ) {
    }

    public record SolutionResponse(List<MoveDto> line, String summary, List<ReviewMoveDto> reviews) {
    }

    public record LeaderboardEntry(String username, TrainingMode mode, Difficulty difficulty, int seconds, double accuracy) {
    }

    public record MoveHighlightDto(int ply, String comment) {
    }

    public record GameReportDto(
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> recurringMistakes,
        List<MoveHighlightDto> moveHighlights,
        List<String> nextFocusAreas,
        String overallRatingBand
    ) {
    }

    public record PlayerProfileReportDto(
        String playerLevel,
        String styleSummary,
        List<String> strongModes,
        List<String> weakModes,
        String trendNotes,
        List<String> recommendedDrills,
        int consistencyScore
    ) {
    }
}
