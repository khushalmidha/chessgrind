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
import java.util.List;
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
        List<TrainingSession> recent = sessions.findTop20ByUserOrderByStartedAtDesc(user);
        Rank rank = rankFor(user);
        List<ModeBestTimeDto> bestTimes = sessions.bestTimesByMode(user.getId()).stream()
            .map(row -> new ModeBestTimeDto(TrainingMode.valueOf(row.getMode()), row.getSeconds()))
            .toList();
        List<AccuracyPointDto> accuracyTrend = recent.reversed().stream()
            .map(session -> new AccuracyPointDto(session.getStartedAt(), session.getAccuracy()))
            .toList();
        List<ModeDifficultyBreakdownDto> breakdown = sessions.modeDifficultyBreakdown(user.getId()).stream()
            .map(row -> new ModeDifficultyBreakdownDto(TrainingMode.valueOf(row.getMode()), Difficulty.valueOf(row.getDifficulty()),
                row.getSessionsPlayed(), Math.round(row.getAverageAccuracy() * 100.0) / 100.0))
            .toList();
        return new ProfileDto(
            user.getUsername(),
            user.getCreatedAt(),
            sessions.countByUser(user),
            user.getTotalCompleted(),
            user.getStreakDays(),
            bestTimes,
            accuracyTrend,
            breakdown,
            favorites.countByUser(user),
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
        long totalUsers = sessions.rankedUserCount();
        Double bestSeconds = sessions.bestSecondsForUser(user.getId());
        if (bestSeconds == null) {
            return new Rank(0, Math.toIntExact(totalUsers));
        }
        long fasterUsers = sessions.countUsersFasterThan(bestSeconds);
        return new Rank(Math.toIntExact(fasterUsers + 1), Math.toIntExact(totalUsers));
    }

    private record Rank(int position, int totalUsers) {
    }

    private AppUser user(UserPrincipal principal) {
        return users.findById(principal.id()).orElseThrow();
    }
}
