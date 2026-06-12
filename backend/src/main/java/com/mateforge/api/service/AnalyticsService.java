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
import java.util.UUID;
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
        Integer bestTime = history.stream()
            .filter(session -> session.getStatus() == SessionStatus.CHECKMATE && session.getEndedAt() != null)
            .map(session -> (int) elapsedSeconds(session))
            .min(Integer::compareTo)
            .orElse(null);
        Rank rank = rankFor(user);
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
            rank.position(),
            rank.totalUsers(),
            bestTime,
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

    private Rank rankFor(AppUser user) {
        java.util.Map<UUID, Long> bestByUser = sessions.findAll().stream()
            .filter(session -> session.getStatus() == SessionStatus.CHECKMATE && session.getEndedAt() != null && session.getUser() != null)
            .collect(java.util.stream.Collectors.toMap(
                session -> session.getUser().getId(),
                this::elapsedSeconds,
                Math::min
            ));
        List<java.util.Map.Entry<UUID, Long>> ranked = bestByUser.entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByValue())
            .toList();
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).getKey().equals(user.getId())) {
                return new Rank(i + 1, ranked.size());
            }
        }
        return new Rank(0, ranked.size());
    }

    private record Rank(int position, int totalUsers) {
    }

    private AppUser user(UserPrincipal principal) {
        return users.findById(principal.id()).orElseThrow();
    }
}
