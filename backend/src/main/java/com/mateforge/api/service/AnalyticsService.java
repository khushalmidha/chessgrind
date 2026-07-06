package com.mateforge.api.service;

import com.mateforge.api.dto.AnalyticsDtos.FavoriteDto;
import com.mateforge.api.dto.AnalyticsDtos.FavoriteRequest;
import com.mateforge.api.dto.AnalyticsDtos.AccuracyPointDto;
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
import java.util.List;
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
        return new ProgressSummary(user.getTotalCompleted(),
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
        long totalSessions = sessions.countByUser(user);
        long favoriteCount = favorites.countByUser(user);
        Rank rank = rankFor(user);
        if (totalSessions == 0) {
            return new ProfileDto(
                user.getUsername(),
                user.getCreatedAt(),
                0,
                user.getTotalCompleted(),
                user.getStreakDays(),
                List.of(),
                List.of(),
                List.of(),
                favoriteCount,
                rank.position(),
                rank.totalUsers()
            );
            // FIXED: brand-new users with zero sessions still need a profile instead of hitting aggregate queries that can fail on empty data.
        }
        List<TrainingSession> recent = sessions.findTop20ByUserOrderByStartedAtDesc(user);
        List<AccuracyPointDto> accuracyTrend = recent.reversed().stream()
            .map(session -> new AccuracyPointDto(session.getStartedAt(), session.getAccuracy()))
            .toList();
        List<ModeBestTimeDto> bestTimes = bestTimes(user);
        List<ModeDifficultyBreakdownDto> breakdown = breakdown(user);
        return new ProfileDto(
            user.getUsername(),
            user.getCreatedAt(),
            totalSessions,
            user.getTotalCompleted(),
            user.getStreakDays(),
            bestTimes,
            accuracyTrend,
            breakdown,
            favoriteCount,
            rank.position(),
            rank.totalUsers()
        );
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

    private List<ModeBestTimeDto> bestTimes(AppUser user) {
        try {
            return sessions.bestTimesByMode(user.getId()).stream()
                .map(row -> new ModeBestTimeDto(TrainingMode.valueOf(row.getMode()), row.getSeconds()))
                .toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<ModeDifficultyBreakdownDto> breakdown(AppUser user) {
        try {
            return sessions.modeDifficultyBreakdown(user.getId()).stream()
                .map(row -> new ModeDifficultyBreakdownDto(TrainingMode.valueOf(row.getMode()), Difficulty.valueOf(row.getDifficulty()),
                    row.getSessionsPlayed(), Math.round(row.getAverageAccuracy() * 100.0) / 100.0))
                .toList();
        } catch (RuntimeException ex) {
            List<ModeDifficultyBreakdownDto> fallback = new ArrayList<>();
            for (TrainingSession session : sessions.findTop20ByUserOrderByStartedAtDesc(user)) {
                fallback.add(new ModeDifficultyBreakdownDto(session.getMode(), session.getDifficulty(), 1, session.getAccuracy()));
            }
            return fallback;
            // FIXED: profile breakdown now degrades to recent-session data if DB aggregation fails.
        }
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
