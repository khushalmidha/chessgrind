package com.mateforge.api.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import com.mateforge.api.dto.TrainingDtos.HintResponse;
import com.mateforge.api.dto.TrainingDtos.MoveResponse;
import com.mateforge.api.dto.TrainingDtos.SessionDto;
import com.mateforge.api.dto.TrainingDtos.SolutionResponse;
import com.mateforge.api.dto.TrainingDtos.StartSessionRequest;
import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.SessionStatus;
import com.mateforge.api.model.TimerMode;
import com.mateforge.api.model.TrainingMove;
import com.mateforge.api.model.TrainingPuzzle;
import com.mateforge.api.model.TrainingSession;
import com.mateforge.api.repository.AppUserRepository;
import com.mateforge.api.repository.TrainingMoveRepository;
import com.mateforge.api.repository.TrainingPuzzleRepository;
import com.mateforge.api.repository.TrainingSessionRepository;
import com.mateforge.api.security.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingService {
    private static final int MAX_HINTS = 3;

    private final AppUserRepository users;
    private final TrainingPuzzleRepository puzzles;
    private final TrainingSessionRepository sessions;
    private final TrainingMoveRepository moves;
    private final ChessRulesService rules;
    private final EngineService engine;
    private final TrainingMapper mapper;
    private final SimpMessagingTemplate messaging;

    public TrainingService(
        AppUserRepository users,
        TrainingPuzzleRepository puzzles,
        TrainingSessionRepository sessions,
        TrainingMoveRepository moves,
        ChessRulesService rules,
        EngineService engine,
        TrainingMapper mapper,
        SimpMessagingTemplate messaging
    ) {
        this.users = users;
        this.puzzles = puzzles;
        this.sessions = sessions;
        this.moves = moves;
        this.rules = rules;
        this.engine = engine;
        this.mapper = mapper;
        this.messaging = messaging;
    }

    @Transactional
    public SessionDto start(StartSessionRequest request, UserPrincipal principal) {
        AppUser user = user(principal);
        TrainingPuzzle puzzle = resolvePuzzle(request);
        String startFen = request.customFen() != null && !request.customFen().isBlank() ? request.customFen() : puzzle.getFen();
        rules.validatePlayableFen(startFen);
        // FIXED: custom FEN input previously trusted the client after only basic parsing, allowing impossible positions.

        TrainingSession session = new TrainingSession();
        session.setUser(user);
        session.setPuzzle(puzzle.getId() == null ? null : puzzle);
        session.setMode(request.mode());
        session.setDifficulty(request.difficulty());
        session.setTimerMode(request.timerMode());
        session.setStartFen(startFen);
        session.setCurrentFen(startFen);
        session.setTimeLimitSeconds(request.timeLimitSeconds());
        session.setIncrementSeconds(request.incrementSeconds());
        session.setRemainingSeconds(request.timerMode() == TimerMode.NONE ? 0 : request.timeLimitSeconds());
        session.setNextPly(1);
        session.setHintsEnabled(request.hintsEnabled());
        session.setTakebacksEnabled(request.takebacksEnabled());
        sessions.save(session);
        publish(session);
        return mapper.session(session);
    }

    @Transactional
    public MoveResponse play(UUID sessionId, String uci, UserPrincipal principal) {
        TrainingSession session = ownedActiveSession(sessionId, principal);
        // FIXED: server now rejects moves after the authoritative clock expires, even if the client has not reported timeout.
        Board before = rules.board(session.getCurrentFen());
        Move userMove = rules.requireLegal(before, uci);
        String san = rules.simpleSan(userMove, before);
        before.doMove(userMove);
        String afterUserFen = before.getFen();

        EngineService.EngineMove optimal = engine.bestMove(session.getCurrentFen());
        boolean optimalMove = optimal.uci().equals(uci);
        if (!optimalMove) {
            session.setMistakes(session.getMistakes() + 1);
        }
        saveMove(session, uci, san, afterUserFen, false, optimalMove, trainingReason(optimalMove, before));
        session.setCurrentFen(afterUserFen);
        refreshClock(session, Instant.now(), false);
        // FIXED: incremental time was ignored after successful user moves, so the backend timer drifted from session rules.

        String engineMove = null;
        Board afterUser = rules.board(afterUserFen);
        updateTerminalState(session, afterUser);
        if (session.getStatus() == SessionStatus.ACTIVE) {
            EngineService.EngineMove defense = engine.bestMove(afterUserFen);
            if (!defense.uci().isBlank()) {
                Board beforeDefense = rules.board(afterUserFen);
                Move defenseMove = rules.requireLegal(beforeDefense, defense.uci());
                String defenseSan = rules.simpleSan(defenseMove, beforeDefense);
                beforeDefense.doMove(defenseMove);
                engineMove = defense.uci();
                saveMove(session, defense.uci(), defenseSan, beforeDefense.getFen(), true, true, defense.reason());
                session.setCurrentFen(beforeDefense.getFen());
                updateTerminalState(session, beforeDefense);
            }
        }

        recalculateAccuracy(session);
        refreshClock(session, Instant.now(), false);
        sessions.save(session);
        publish(session);
        Board finalBoard = rules.board(session.getCurrentFen());
        return new MoveResponse(mapper.session(session), uci, engineMove, rules.describeState(finalBoard),
            finalBoard.isKingAttacked(), finalBoard.isMated(), finalBoard.isStaleMate(), message(session));
    }

    @Transactional
    public HintResponse hint(UUID sessionId, UserPrincipal principal) {
        TrainingSession session = ownedActiveSession(sessionId, principal);
        if (!session.isHintsEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hints are disabled for this session");
        }
        if (session.getHintsUsed() >= MAX_HINTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No hints remaining");
        }
        EngineService.EngineMove best = engine.bestMove(session.getCurrentFen());
        session.setHintsUsed(session.getHintsUsed() + 1);
        sessions.save(session);
        return new HintResponse(best.uci(), best.uci(), best.reason(), best.uci().length() >= 4 ? List.of(best.uci().substring(0, 4)) : List.of());
    }

    @Transactional
    public SessionDto undo(UUID sessionId, UserPrincipal principal) {
        TrainingSession session = ownedActiveSession(sessionId, principal);
        if (!session.isTakebacksEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Takebacks are disabled");
        }
        List<TrainingMove> existing = moves.findBySessionOrderByPlyAsc(session);
        int removeCount = Math.min(2, existing.size());
        for (int i = 0; i < removeCount; i++) {
            moves.delete(existing.get(existing.size() - 1 - i));
        }
        List<TrainingMove> remaining = moves.findBySessionOrderByPlyAsc(session);
        session.setCurrentFen(remaining.isEmpty() ? session.getStartFen() : remaining.getLast().getFenAfter());
        session.setNextPly(remaining.size() + 1);
        session.setMistakes((int) remaining.stream().filter(move -> !move.isEngineMove() && !move.isOptimal()).count());
        recalculateAccuracy(session);
        refreshClock(session, Instant.now(), false);
        sessions.save(session);
        publish(session);
        return mapper.session(session);
    }

    @Transactional(readOnly = true)
    public SolutionResponse solution(UUID sessionId, UserPrincipal principal) {
        TrainingSession session = ownedSession(sessionId, principal);
        List<EngineService.EngineMove> engineLine = engine.solutionLine(session.getStartFen(), 40);
        String fen = session.getStartFen();
        List<com.mateforge.api.dto.TrainingDtos.MoveDto> line = new java.util.ArrayList<>();
        int ply = 1;
        for (EngineService.EngineMove best : engineLine) {
            Board board = rules.board(fen);
            Move move = rules.requireLegal(board, best.uci());
            String san = rules.simpleSan(move, board);
            board.doMove(move);
            fen = board.getFen();
            line.add(new com.mateforge.api.dto.TrainingDtos.MoveDto(ply++, best.uci(), san, fen, ply % 2 == 1, true, best.reason()));
        }
        return new SolutionResponse(line, "Optimal line generated from the starting position using the configured UCI engine.");
    }

    @Transactional
    public SessionDto get(UUID id, UserPrincipal principal) {
        TrainingSession session = ownedSession(id, principal);
        if (session.getStatus() == SessionStatus.ACTIVE) {
            refreshClock(session, Instant.now(), true);
        }
        return mapper.session(session);
    }

    @Transactional(readOnly = true)
    public List<SessionDto> history(UserPrincipal principal) {
        AppUser user = user(principal);
        return sessions.findTop20ByUserOrderByStartedAtDesc(user).stream().map(mapper::session).toList();
    }

    private TrainingPuzzle resolvePuzzle(StartSessionRequest request) {
        if (request.customFen() != null && !request.customFen().isBlank()) {
            TrainingPuzzle puzzle = new TrainingPuzzle();
            puzzle.setMode(request.mode());
            puzzle.setDifficulty(request.difficulty());
            puzzle.setTitle("Custom position");
            puzzle.setFen(request.customFen());
            puzzle.setTargetMateMoves(0);
            return puzzle;
        }
        if (request.puzzleId() != null) {
            return puzzles.findById(request.puzzleId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Puzzle not found"));
        }
        return puzzles.findByModeAndDifficulty(request.mode(), request.difficulty()).stream()
            .findAny()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No puzzle found for this mode and difficulty"));
    }

    private void saveMove(TrainingSession session, String uci, String san, String fenAfter, boolean engineMove, boolean optimal, String reason) {
        TrainingMove move = new TrainingMove();
        move.setSession(session);
        move.setPly(session.getNextPly());
        move.setUci(uci);
        move.setSan(san);
        move.setFenAfter(fenAfter);
        move.setEngineMove(engineMove);
        move.setOptimal(optimal);
        move.setReason(reason);
        moves.save(move);
        session.setNextPly(session.getNextPly() + 1);
        // FIXED: move numbering now advances from the session state instead of fetching all prior moves.
    }

    private void updateTerminalState(TrainingSession session, Board board) {
        if (board.isMated()) {
            session.setStatus(SessionStatus.CHECKMATE);
            session.setEndedAt(Instant.now());
            user(session).setTotalCompleted(user(session).getTotalCompleted() + 1);
        } else if (board.isStaleMate()) {
            session.setStatus(SessionStatus.STALEMATE);
            session.setEndedAt(Instant.now());
        } else if (board.isDraw()) {
            session.setStatus(SessionStatus.DRAW);
            session.setEndedAt(Instant.now());
        }
    }

    private void recalculateAccuracy(TrainingSession session) {
        List<TrainingMove> userMoves = moves.findBySessionOrderByPlyAsc(session).stream().filter(move -> !move.isEngineMove()).toList();
        long optimal = userMoves.stream().filter(TrainingMove::isOptimal).count();
        session.setAccuracy(userMoves.isEmpty() ? 100.0 : Math.round((optimal * 10000.0 / userMoves.size())) / 100.0);
    }

    private String trainingReason(boolean optimal, Board boardAfter) {
        if (boardAfter.isMated()) {
            return "Checkmate: the defender has no legal square and cannot block or capture the attacker.";
        }
        if (optimal) {
            return "Best technique: this move preserves the mating net against the strongest defense.";
        }
        return "This still may be playable, but the engine found a more forcing move from the same position.";
    }

    private String message(TrainingSession session) {
        return switch (session.getStatus()) {
            case CHECKMATE -> "Mate delivered. Review the optimal line to reinforce the pattern.";
            case STALEMATE -> "Stalemate. The defender escaped because the king has no legal move but is not in check.";
            case DRAW -> "Draw reached. Use the solution replay to recover the winning technique.";
            case TIMEOUT -> "Time expired.";
            default -> "Keep the king boxed in and improve piece coordination.";
        };
    }

    private TrainingSession ownedActiveSession(UUID id, UserPrincipal principal) {
        TrainingSession session = ownedSession(id, principal);
        if (session.getStatus() == SessionStatus.TIMEOUT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Session has timed out");
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Session is already finished");
        }
        if (refreshClock(session, Instant.now(), true)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Session has timed out");
        }
        return session;
    }

    private boolean refreshClock(TrainingSession session, Instant now, boolean persistTimeout) {
        if (session.getTimerMode() == TimerMode.NONE || session.getStatus() != SessionStatus.ACTIVE) {
            return false;
        }
        int userMoveCount = Math.max(0, session.getNextPly() / 2);
        long elapsed = Math.max(0, Duration.between(session.getStartedAt(), now).toSeconds());
        int remaining = (int) Math.max(0, session.getTimeLimitSeconds() + (long) session.getIncrementSeconds() * userMoveCount - elapsed);
        session.setRemainingSeconds(remaining);
        if (remaining == 0) {
            session.setStatus(SessionStatus.TIMEOUT);
            session.setEndedAt(now);
            if (persistTimeout) {
                sessions.save(session);
                publish(session);
            }
            // FIXED: active sessions could outlive their countdown because elapsed time was never reconciled on the server.
            return true;
        }
        return false;
    }

    private TrainingSession ownedSession(UUID id, UserPrincipal principal) {
        TrainingSession session = sessions.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getUser().getId().equals(principal.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not own this session");
        }
        return session;
    }

    private AppUser user(UserPrincipal principal) {
        return users.findById(principal.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private AppUser user(TrainingSession session) {
        return users.findById(session.getUser().getId()).orElseThrow();
    }

    private void publish(TrainingSession session) {
        messaging.convertAndSend("/topic/sessions/" + session.getId(), mapper.session(session));
    }
}
