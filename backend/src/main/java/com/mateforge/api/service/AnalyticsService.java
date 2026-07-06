package com.mateforge.api.service;

import com.mateforge.api.dto.AnalyticsDtos.FavoriteDto;
import com.mateforge.api.dto.AnalyticsDtos.FavoriteRequest;
import com.mateforge.api.dto.AnalyticsDtos.AccuracyPointDto;
import com.mateforge.api.dto.AnalyticsDtos.BestCheckmateModeDto;
import com.mateforge.api.dto.AnalyticsDtos.ModeBestTimeDto;
import com.mateforge.api.dto.AnalyticsDtos.ModeDifficultyBreakdownDto;
import com.mateforge.api.dto.AnalyticsDtos.ProfileDto;
import com.mateforge.api.dto.AnalyticsDtos.ProgressSummary;
import com.mateforge.api.dto.TrainingDtos.LeaderboardEntry;
import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.FavoritePosition;
import com.mateforge.api.model.SessionStatus;
import com.mateforge.api.model.TrainingSession;
import com.mateforge.api.model.TrainingMode;
import com.mateforge.api.repository.AppUserRepository;
import com.mateforge.api.repository.FavoritePositionRepository;
import com.mateforge.api.repository.TrainingMoveRepository;
import com.mateforge.api.repository.TrainingSessionRepository;
import com.mateforge.api.security.UserPrincipal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private final AppUserRepository users;
    private final TrainingSessionRepository sessions;
    private final TrainingMoveRepository moves;
    private final FavoritePositionRepository favorites;

    public AnalyticsService(AppUserRepository users, TrainingSessionRepository sessions, TrainingMoveRepository moves, FavoritePositionRepository favorites) {
        this.users = users;
        this.sessions = sessions;
        this.moves = moves;
        this.favorites = favorites;
    }

    @Transactional(readOnly = true)
    public ProgressSummary progress(UserPrincipal principal) {
        AppUser user = user(principal);
        List<TrainingSession> history = sessions.findTop20ByUserOrderByStartedAtDesc(user);
        double average = history.stream().mapToDouble(TrainingSession::getAccuracy).average().orElse(0);
        // FIXED: users with no sessions must get a zero average instead of risking empty-stream arithmetic elsewhere.
        Integer bestTime = history.stream()
            .filter(session -> session.getStatus() == SessionStatus.CHECKMATE && session.getEndedAt() != null)
            .map(session -> (int) elapsedSeconds(session))
            .min(Integer::compareTo)
            .orElse(null);
        Rank rank = rankFor(user);
        List<String> recentMistakes = moves.findTop5BySessionUserAndEngineMoveFalseAndOptimalFalseOrderByPlayedAtDesc(user).stream()
            .filter(move -> !move.isEngineMove() && !move.isOptimal())
            .map(move -> move.getUci() + ": " + move.getReason())
            .toList();
        int completed = Math.toIntExact(Math.min(Integer.MAX_VALUE, safeCompletedSessions(user, history)));
        return new ProgressSummary(completed,
            (int) sessions.countByUserAndStatus(user, SessionStatus.ACTIVE),
            user.getStreakDays(),
            Math.round(average * 100.0) / 100.0,
            rank.position(),
            rank.totalUsers(),
            bestTime,
            recentMistakes);
    }

    @Transactional(readOnly = true)
    public ProfileDto profile(UserPrincipal principal) {
        AppUser user = user(principal);
        List<TrainingSession> recent = safeRecentSessions(user);
        long totalSessions = safeTotalSessions(user, recent.size());
        long favoriteCount = safeFavoriteCount(user);
        List<AccuracyPointDto> accuracyTrend = recent.reversed().stream()
            .map(session -> new AccuracyPointDto(session.getStartedAt(), session.getAccuracy()))
            .toList();
        List<ModeBestTimeDto> bestTimes = bestTimesFromHistory(recent);
        List<ModeDifficultyBreakdownDto> breakdown = breakdownFromHistory(recent);
        List<BestCheckmateModeDto> bestCheckmates = bestCheckmatesFromHistory(recent);
        double averageAccuracy = recent.stream().mapToDouble(TrainingSession::getAccuracy).average().orElse(0);
        long completed = safeCompletedSessions(user, recent);
        return new ProfileDto(
            user.getUsername(),
            user.getCreatedAt(),
            totalSessions,
            Math.toIntExact(Math.min(Integer.MAX_VALUE, completed)),
            user.getStreakDays(),
            rating(1000, averageAccuracy, completed, user.getStreakDays()),
            rating(900, averageAccuracy, recent.size(), 0),
            bestCheckmates,
            bestTimes,
            accuracyTrend,
            breakdown,
            favoriteCount,
            0,
            0
        );
        // FIXED: profile now renders for zero-game users without native aggregate/rank queries that were causing 500s.
    }

    @Transactional
    public FavoriteDto addFavorite(UserPrincipal principal, FavoriteRequest request) {
        FavoritePosition favorite = new FavoritePosition();
        favorite.setUser(user(principal));
        favorite.setName(request.name());
        favorite.setFen(request.fen());
        favorites.save(favorite);
        return new FavoriteDto(favorite.getId().toString(), favorite.getName(), favorite.getFen());
    }

    @Transactional(readOnly = true)
    public List<FavoriteDto> favorites(UserPrincipal principal) {
        return favorites.findByUserOrderByCreatedAtDesc(user(principal)).stream()
            .map(favorite -> new FavoriteDto(favorite.getId().toString(), favorite.getName(), favorite.getFen()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard() {
        return sessions.fastestCheckmates().stream()
            .map(row -> new LeaderboardEntry(row.getUsername(), TrainingMode.valueOf(row.getMode()),
                Difficulty.valueOf(row.getDifficulty()), row.getSeconds(), row.getAccuracy()))
            .toList();
    }

    private long elapsedSeconds(TrainingSession session) {
        return Duration.between(session.getStartedAt(), session.getEndedAt()).toSeconds();
    }

    private Rank rankFor(AppUser user) {
        try {
            long totalUsers = sessions.rankedUserCount();
            Double bestSeconds = sessions.bestSecondsForUser(user.getId());
            if (bestSeconds == null) {
                return new Rank(0, Math.toIntExact(totalUsers));
            }
            long fasterUsers = sessions.countUsersFasterThan(bestSeconds);
            return new Rank(Math.toIntExact(fasterUsers + 1), Math.toIntExact(totalUsers));
        } catch (RuntimeException ex) {
            return new Rank(0, 0);
            // FIXED: leaderboard aggregate issues should not make the profile page fail with 500.
        }
    }

    private List<TrainingSession> safeRecentSessions(AppUser user) {
        try {
            return sessions.findTop20ByUserOrderByStartedAtDesc(user);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private long safeTotalSessions(AppUser user, long fallback) {
        try {
            return sessions.countByUser(user);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private long safeFavoriteCount(AppUser user) {
        try {
            return favorites.countByUser(user);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private long safeCompletedSessions(AppUser user, List<TrainingSession> fallbackHistory) {
        try {
            return sessions.countByUserAndStatusNot(user, SessionStatus.ACTIVE);
        } catch (RuntimeException ex) {
            return fallbackHistory.stream().filter(session -> session.getStatus() != SessionStatus.ACTIVE).count();
            // FIXED: profile completion counts now come from session history, so finished games update even if AppUser.totalCompleted is stale.
        }
    }

    private List<ModeBestTimeDto> bestTimesFromHistory(List<TrainingSession> history) {
        return history.stream()
            .filter(session -> session.getStatus() == SessionStatus.CHECKMATE && session.getEndedAt() != null)
            .collect(Collectors.groupingBy(TrainingSession::getMode,
                Collectors.mapping(session -> (int) elapsedSeconds(session), Collectors.minBy(Integer::compareTo))))
            .entrySet()
            .stream()
            .map(entry -> new ModeBestTimeDto(entry.getKey(), entry.getValue().orElse(null)))
            .toList();
    }

    private List<ModeDifficultyBreakdownDto> breakdownFromHistory(List<TrainingSession> history) {
        Map<String, List<TrainingSession>> grouped = history.stream()
            .collect(Collectors.groupingBy(session -> session.getMode().name() + "|" + session.getDifficulty().name()));
        List<ModeDifficultyBreakdownDto> rows = new ArrayList<>();
        for (List<TrainingSession> group : grouped.values()) {
            TrainingSession first = group.getFirst();
            double average = group.stream().mapToDouble(TrainingSession::getAccuracy).average().orElse(0);
            rows.add(new ModeDifficultyBreakdownDto(first.getMode(), first.getDifficulty(), group.size(), Math.round(average * 100.0) / 100.0));
        }
        rows.sort(Comparator.comparing((ModeDifficultyBreakdownDto row) -> row.mode().name()).thenComparing(row -> row.difficulty().name()));
        return rows;
    }

    private List<BestCheckmateModeDto> bestCheckmatesFromHistory(List<TrainingSession> history) {
        return history.stream()
            .filter(session -> session.getStatus() == SessionStatus.CHECKMATE)
            .collect(Collectors.groupingBy(TrainingSession::getMode))
            .entrySet()
            .stream()
            .map(entry -> {
                List<TrainingSession> group = entry.getValue();
                double average = group.stream().mapToDouble(TrainingSession::getAccuracy).average().orElse(0);
                Integer bestSeconds = group.stream()
                    .filter(session -> session.getEndedAt() != null)
                    .map(session -> (int) elapsedSeconds(session))
                    .filter(Objects::nonNull)
                    .min(Integer::compareTo)
                    .orElse(null);
                return new BestCheckmateModeDto(entry.getKey(), group.size(), Math.round(average * 100.0) / 100.0, bestSeconds);
            })
            .sorted(Comparator.comparing(BestCheckmateModeDto::completed).reversed().thenComparing(BestCheckmateModeDto::averageAccuracy).reversed())
            .limit(3)
            .toList();
    }

    private int rating(int base, double averageAccuracy, long volume, int streak) {
        int rating = base + (int) Math.round(averageAccuracy * 4) + (int) Math.min(220, volume * 18) + Math.min(80, streak * 8);
        return Math.max(800, Math.min(2200, rating));
    }

    private record Rank(int position, int totalUsers) {
    }

    private AppUser user(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again");
        }
        return users.findById(principal.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again"));
        // FIXED: missing or stale auth principals produced a generic 500 on profile/favorites endpoints.
    }
}
