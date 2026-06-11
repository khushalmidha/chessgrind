package com.mateforge.api.repository;

import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.SessionStatus;
import com.mateforge.api.model.TrainingSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {
    List<TrainingSession> findTop20ByUserOrderByStartedAtDesc(AppUser user);

    List<TrainingSession> findByUserAndStatus(AppUser user, SessionStatus status);
}
