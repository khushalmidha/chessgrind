package com.mateforge.api.repository;

import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.SessionStatus;
import com.mateforge.api.model.TrainingSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {
    List<TrainingSession> findTop20ByUserOrderByStartedAtDesc(AppUser user);

    List<TrainingSession> findByUserAndStatus(AppUser user, SessionStatus status);

    long countByUser(AppUser user);

    long countByUserAndStatus(AppUser user, SessionStatus status);

    @Query(value = """
        select s.mode as mode,
               cast(min(extract(epoch from (s.ended_at - s.started_at))) as integer) as seconds
        from training_sessions s
        where s.user_id = :userId
          and s.status = 'CHECKMATE'
          and s.ended_at is not null
        group by s.mode
        order by s.mode
        """, nativeQuery = true)
    List<ModeBestTimeRow> bestTimesByMode(@Param("userId") UUID userId);

    @Query(value = """
        select s.mode as mode,
               s.difficulty as difficulty,
               count(*) as sessionsPlayed,
               coalesce(avg(s.accuracy), 0) as averageAccuracy
        from training_sessions s
        where s.user_id = :userId
        group by s.mode, s.difficulty
        order by s.mode, s.difficulty
        """, nativeQuery = true)
    List<ModeDifficultyBreakdownRow> modeDifficultyBreakdown(@Param("userId") UUID userId);

    // FIXED: leaderboard previously loaded every session into memory instead of asking the database for the fastest rows.
    @Query(value = """
        select u.username as username,
               s.mode as mode,
               s.difficulty as difficulty,
               cast(extract(epoch from (s.ended_at - s.started_at)) as integer) as seconds,
               s.accuracy as accuracy
        from training_sessions s
        join app_users u on u.id = s.user_id
        where s.status = 'CHECKMATE'
          and s.ended_at is not null
        order by extract(epoch from (s.ended_at - s.started_at)) asc
        limit 20
        """, nativeQuery = true)
    List<LeaderboardRow> fastestCheckmates();

    // FIXED: rank calculation previously scanned every historical session in the JVM.
    @Query(value = """
        select count(*)
        from (
            select s.user_id, min(extract(epoch from (s.ended_at - s.started_at))) as best_seconds
            from training_sessions s
            where s.status = 'CHECKMATE'
              and s.ended_at is not null
            group by s.user_id
        ) best
        """, nativeQuery = true)
    long rankedUserCount();

    // FIXED: rank calculation now compares the user's best time with database aggregation instead of sessions.findAll().
    @Query(value = """
        select min(extract(epoch from (s.ended_at - s.started_at)))
        from training_sessions s
        where s.status = 'CHECKMATE'
          and s.ended_at is not null
          and s.user_id = :userId
        """, nativeQuery = true)
    Double bestSecondsForUser(@Param("userId") UUID userId);

    // FIXED: rank calculation now counts faster users in the database instead of loading all users' sessions.
    @Query(value = """
        select count(*)
        from (
            select s.user_id, min(extract(epoch from (s.ended_at - s.started_at))) as best_seconds
            from training_sessions s
            where s.status = 'CHECKMATE'
              and s.ended_at is not null
            group by s.user_id
            having min(extract(epoch from (s.ended_at - s.started_at))) < :seconds
        ) best
        """, nativeQuery = true)
    long countUsersFasterThan(@Param("seconds") double seconds);

    interface LeaderboardRow {
        String getUsername();

        String getMode();

        String getDifficulty();

        int getSeconds();

        double getAccuracy();
    }

    interface ModeBestTimeRow {
        String getMode();

        Integer getSeconds();
    }

    interface ModeDifficultyBreakdownRow {
        String getMode();

        String getDifficulty();

        long getSessionsPlayed();

        double getAverageAccuracy();
    }
}
