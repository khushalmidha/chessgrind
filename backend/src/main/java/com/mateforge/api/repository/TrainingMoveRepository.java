package com.mateforge.api.repository;

import com.mateforge.api.model.TrainingMove;
import com.mateforge.api.model.TrainingSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingMoveRepository extends JpaRepository<TrainingMove, UUID> {
    List<TrainingMove> findBySessionOrderByPlyAsc(TrainingSession session);

    // FIXED: progress previously performed one move query per recent session and then trimmed in memory.
    List<TrainingMove> findTop5BySessionUserAndEngineMoveFalseAndOptimalFalseOrderByPlayedAtDesc(com.mateforge.api.model.AppUser user);
}
