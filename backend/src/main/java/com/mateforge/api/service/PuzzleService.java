package com.mateforge.api.service;

import com.mateforge.api.dto.TrainingDtos.PuzzleDto;
import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.TrainingMode;
import com.mateforge.api.repository.TrainingPuzzleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PuzzleService {
    private static final Duration PUZZLE_TTL = Duration.ofHours(6);

    private final TrainingPuzzleRepository puzzles;
    private final TrainingMapper mapper;
    private final RedisCacheService cache;

    public PuzzleService(TrainingPuzzleRepository puzzles, TrainingMapper mapper, RedisCacheService cache) {
        this.puzzles = puzzles;
        this.mapper = mapper;
        this.cache = cache;
    }

    public List<PuzzleDto> list(TrainingMode mode, Difficulty difficulty) {
        String key = "puzzles:" + (mode == null ? "all" : mode.name()) + ":" + (difficulty == null ? "all" : difficulty.name());
        List<PuzzleDto> cached = cache.get(key, new TypeReference<List<PuzzleDto>>() {}).orElse(null);
        if (cached != null) {
            return cached;
        }
        List<PuzzleDto> result;
        if (mode != null && difficulty != null) {
            result = puzzles.findByModeAndDifficulty(mode, difficulty).stream().map(mapper::puzzle).toList();
        } else {
            result = puzzles.findAll().stream().map(mapper::puzzle).toList();
        }
        cache.put(key, result, PUZZLE_TTL);
        return result;
    }

    public List<PuzzleDto> daily() {
        String key = "puzzles:daily";
        List<PuzzleDto> cached = cache.get(key, new TypeReference<List<PuzzleDto>>() {}).orElse(null);
        if (cached != null) {
            return cached;
        }
        List<PuzzleDto> result = puzzles.findByDailyChallengeTrue().stream().map(mapper::puzzle).toList();
        cache.put(key, result, PUZZLE_TTL);
        return result;
    }
}
