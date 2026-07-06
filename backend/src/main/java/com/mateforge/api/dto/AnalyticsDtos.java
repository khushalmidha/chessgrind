package com.mateforge.api.dto;

import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.TrainingMode;
import java.time.Instant;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public record ProgressSummary(
        int completed,
        int activeSessions,
        int streakDays,
        double averageAccuracy,
        int rank,
        int totalRankedUsers,
        Integer bestCheckmateSeconds,
        List<String> recentMistakes
    ) {
    }

    public record FavoriteRequest(String name, String fen) {
    }

    public record FavoriteDto(String id, String name, String fen) {
    }

    public record AccuracyPointDto(Instant date, double accuracy) {
    }

    public record ModeBestTimeDto(TrainingMode mode, Integer seconds) {
    }

    public record ModeDifficultyBreakdownDto(TrainingMode mode, Difficulty difficulty, long sessionsPlayed, double averageAccuracy) {
    }

    public record BestCheckmateModeDto(TrainingMode mode, long completed, double averageAccuracy, Integer bestSeconds) {
    }

    public record ProfileDto(
        String username,
        Instant joinDate,
        long totalSessions,
        int totalCompleted,
        int currentStreak,
        int tournamentRating,
        int regularChessRating,
        List<BestCheckmateModeDto> bestCheckmateModes,
        List<ModeBestTimeDto> bestTimes,
        List<AccuracyPointDto> accuracyTrend,
        List<ModeDifficultyBreakdownDto> breakdown,
        long favoritePositionsCount,
        int leaderboardRank,
        int totalRankedUsers
    ) {
    }
}
