package com.mateforge.api.service;

import com.mateforge.api.dto.AnalyticsDtos.FavoriteDto;
import com.mateforge.api.dto.AnalyticsDtos.FavoriteRequest;
import com.mateforge.api.dto.AnalyticsDtos.ProgressSummary;
import com.mateforge.api.dto.TrainingDtos.LeaderboardEntry;
import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.FavoritePosition;
import com.mateforge.api.model.SessionStatus;
import com.mateforge.api.model.TrainingSession;
import com.mateforge.api.repository.AppUserRepository;
import com.mateforge.api.repository.FavoritePositionRepository;
import com.mateforge.api.repository.TrainingMoveRepository;
import com.mateforge.api.repository.TrainingSessionRepository;
import com.mateforge.api.security.UserPrincipal;
import java.time.Duration;
import java.util.Comparator;
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
        List<String> recentMistakes = history.stream()
            .flatMap(session -> moves.findBySessionOrderByPlyAsc(session).stream())
            .filter(move -> !move.isEngineMove() && !move.isOptimal())
            .limit(5)
            .map(move -> move.getUci() + ": " + move.getReason())
            .toList();
        return new ProgressSummary(user.getTotalCompleted(),
            sessions.findByUserAndStatus(user, SessionStatus.ACTIVE).size(),
            user.getStreakDays(),
            Math.round(average * 100.0) / 100.0,
            recentMistakes);
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
        return sessions.findAll().stream()
            .filter(session -> session.getStatus() == SessionStatus.CHECKMATE && session.getEndedAt() != null)
            .sorted(Comparator.comparingLong(this::elapsedSeconds))
            .limit(20)
            .map(session -> new LeaderboardEntry(session.getUser().getUsername(), session.getMode(), session.getDifficulty(),
                (int) elapsedSeconds(session), session.getAccuracy()))
            .toList();
    }

    private long elapsedSeconds(TrainingSession session) {
        return Duration.between(session.getStartedAt(), session.getEndedAt()).toSeconds();
    }

    private AppUser user(UserPrincipal principal) {
        return users.findById(principal.id()).orElseThrow();
    }
}
