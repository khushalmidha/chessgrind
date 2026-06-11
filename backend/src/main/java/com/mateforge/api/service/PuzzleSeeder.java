package com.mateforge.api.service;

import com.mateforge.api.model.Difficulty;
import com.mateforge.api.model.TrainingMode;
import com.mateforge.api.model.TrainingPuzzle;
import com.mateforge.api.repository.TrainingPuzzleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PuzzleSeeder implements CommandLineRunner {
    private final TrainingPuzzleRepository puzzles;

    public PuzzleSeeder(TrainingPuzzleRepository puzzles) {
        this.puzzles = puzzles;
    }

    @Override
    public void run(String... args) {
        if (puzzles.count() > 0) {
            return;
        }
        seed(TrainingMode.KING_ROOK_VS_KING, Difficulty.BEGINNER, "Box the king", "8/8/8/8/8/4k3/8/4K2R w K - 0 1", 8, true);
        seed(TrainingMode.TWO_ROOKS_VS_KING, Difficulty.BEGINNER, "Ladder mate", "8/8/8/8/8/4k3/8/R3K2R w KQ - 0 1", 5, false);
        seed(TrainingMode.QUEEN_VS_KING, Difficulty.BEGINNER, "Queen shoulder", "8/8/8/8/8/3k4/8/4K2Q w - - 0 1", 7, false);
        seed(TrainingMode.TWO_BISHOPS_VS_KING, Difficulty.INTERMEDIATE, "Drive to the corner", "8/8/8/8/3k4/8/8/2B1KB2 w - - 0 1", 16, false);
        seed(TrainingMode.BISHOP_KNIGHT_VS_KING, Difficulty.ADVANCED, "Wrong corner no more", "8/8/8/8/3k4/8/8/2B1KN2 w - - 0 1", 24, false);
        seed(TrainingMode.TWO_PAWNS_VS_KING, Difficulty.INTERMEDIATE, "Connected runners", "8/8/8/8/3k4/8/3PP3/4K3 w - - 0 1", 18, false);
    }

    private void seed(TrainingMode mode, Difficulty difficulty, String title, String fen, int target, boolean daily) {
        TrainingPuzzle puzzle = new TrainingPuzzle();
        puzzle.setMode(mode);
        puzzle.setDifficulty(difficulty);
        puzzle.setTitle(title);
        puzzle.setFen(fen);
        puzzle.setTargetMateMoves(target);
        puzzle.setDailyChallenge(daily);
        puzzles.save(puzzle);
    }
}
