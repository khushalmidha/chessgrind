package com.mateforge.api.repository;

import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.TrainingMode;
import com.mateforge.api.model.TrainingPuzzle;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingPuzzleRepository extends JpaRepository<TrainingPuzzle, UUID> {
    List<TrainingPuzzle> findByModeAndDifficulty(TrainingMode mode, Difficulty difficulty);

    List<TrainingPuzzle> findByDailyChallengeTrue();
}
