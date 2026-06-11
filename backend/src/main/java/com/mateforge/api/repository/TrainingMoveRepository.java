package com.mateforge.api.repository;

import com.mateforge.api.model.TrainingMove;
import com.mateforge.api.model.TrainingSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingMoveRepository extends JpaRepository<TrainingMove, UUID> {
    List<TrainingMove> findBySessionOrderByPlyAsc(TrainingSession session);
}
