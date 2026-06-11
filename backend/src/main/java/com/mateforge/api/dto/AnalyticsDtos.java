package com.mateforge.api.dto;

import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public record ProgressSummary(
        int completed,
        int activeSessions,
        int streakDays,
        double averageAccuracy,
        List<String> recentMistakes
    ) {
    }

    public record FavoriteRequest(String name, String fen) {
    }

    public record FavoriteDto(String id, String name, String fen) {
    }
}
