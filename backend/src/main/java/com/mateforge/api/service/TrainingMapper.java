package com.mateforge.api.service;

import com.mateforge.api.dto.TrainingDtos.MoveDto;
import com.mateforge.api.dto.TrainingDtos.PuzzleDto;
import com.mateforge.api.dto.TrainingDtos.SessionDto;
import com.mateforge.api.model.TrainingMove;
import com.mateforge.api.model.TrainingPuzzle;
import com.mateforge.api.model.TrainingSession;
import com.mateforge.api.repository.TrainingMoveRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TrainingMapper {
    private final TrainingMoveRepository moves;

    public TrainingMapper(TrainingMoveRepository moves) {
        this.moves = moves;
    }

    public PuzzleDto puzzle(TrainingPuzzle puzzle) {
        if (puzzle == null) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Puzzle not found");
        }
        return new PuzzleDto(puzzle.getId(), puzzle.getMode(), puzzle.getDifficulty(), puzzle.getTitle(),
            puzzle.getFen(), puzzle.getTargetMateMoves(), puzzle.isDailyChallenge());
        // FIXED: null puzzle mapper input previously failed with an unhelpful NullPointerException.
    }

    public MoveDto move(TrainingMove move) {
        return new MoveDto(move.getPly(), move.getUci(), move.getSan(), move.getFenAfter(), move.isEngineMove(),
            move.isOptimal(), move.getReason() == null ? "" : move.getReason());
        // FIXED: nullable move reasons could violate the frontend DTO contract and render as undefined.
    }

    public SessionDto session(TrainingSession session) {
        List<MoveDto> moveDtos = moves.findBySessionOrderByPlyAsc(session).stream().map(this::move).toList();
        return new SessionDto(session.getId(), session.getMode(), session.getDifficulty(), session.getTimerMode(),
            session.getStatus(), session.getStartFen(), session.getCurrentFen(), session.getRemainingSeconds(),
            session.getHintsUsed(), session.getMistakes(), session.getAccuracy(), session.getStartedAt(),
            session.getEndedAt(), moveDtos);
    }
}
