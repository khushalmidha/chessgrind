package com.mateforge.api.controller;

import com.mateforge.api.dto.TrainingDtos.PuzzleDto;
import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.TrainingMode;
import com.mateforge.api.service.PuzzleService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/puzzles")
public class PuzzleController {
    private final PuzzleService puzzles;

    public PuzzleController(PuzzleService puzzles) {
        this.puzzles = puzzles;
    }

    @GetMapping
    List<PuzzleDto> list(@RequestParam(required = false) TrainingMode mode, @RequestParam(required = false) Difficulty difficulty) {
        return puzzles.list(mode, difficulty);
    }

    @GetMapping("/daily")
    List<PuzzleDto> daily() {
        return puzzles.daily();
    }
}
