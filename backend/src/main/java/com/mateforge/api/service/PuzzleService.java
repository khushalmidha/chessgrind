package com.mateforge.api.service;

import com.mateforge.api.dto.TrainingDtos.PuzzleDto;
import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.TrainingMode;
import com.mateforge.api.repository.TrainingPuzzleRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PuzzleService {
    private final TrainingPuzzleRepository puzzles;
    private final TrainingMapper mapper;

    public PuzzleService(TrainingPuzzleRepository puzzles, TrainingMapper mapper) {
        this.puzzles = puzzles;
        this.mapper = mapper;
    }

    public List<PuzzleDto> list(TrainingMode mode, Difficulty difficulty) {
        if (mode != null && difficulty != null) {
            return puzzles.findByModeAndDifficulty(mode, difficulty).stream().map(mapper::puzzle).toList();
        }
        return puzzles.findAll().stream().map(mapper::puzzle).toList();
    }

    public List<PuzzleDto> daily() {
        return puzzles.findByDailyChallengeTrue().stream().map(mapper::puzzle).toList();
    }
}
