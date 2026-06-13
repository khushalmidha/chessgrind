package com.mateforge.api.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EngineService {
    private final String stockfishPath;
    private final int moveTimeMs;
    private final int lineDepth;
    private final ChessRulesService rules;
    private final Object engineLock = new Object();
    private final Map<String, EngineMove> bestMoveCache = new ConcurrentHashMap<>();
    private final Map<String, List<EngineMove>> solutionCache = new ConcurrentHashMap<>();
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    public EngineService(
        @Value("${app.engine.stockfish-path}") String stockfishPath,
        @Value("${app.engine.move-time-ms}") int moveTimeMs,
        @Value("${app.engine.line-depth}") int lineDepth,
        ChessRulesService rules
    ) {
        this.stockfishPath = stockfishPath;
        this.moveTimeMs = moveTimeMs;
        this.lineDepth = lineDepth;
        this.rules = rules;
    }

    public EngineMove bestMove(String fen) {
        return bestMoveCache.computeIfAbsent(fen, this::calculateBestMove);
    }

    private EngineMove calculateBestMove(String fen) {
        String best = askStockfish(fen, "go movetime " + moveTimeMs);
        if (best == null || best.isBlank() || "(none)".equals(best)) {
            Board board = rules.board(fen);
            List<Move> legal = rules.legalMoves(board);
            best = legal.isEmpty() ? "" : legal.getFirst().toString();
        }
        return new EngineMove(best, reasonFor(best, fen));
    }

    public List<EngineMove> solutionLine(String fen, int maxPlies) {
        String cacheKey = fen + "|" + maxPlies;
        return solutionCache.computeIfAbsent(cacheKey, ignored -> calculateSolutionLine(fen, maxPlies));
    }

    private List<EngineMove> calculateSolutionLine(String fen, int maxPlies) {
        List<EngineMove> line = new ArrayList<>();
        String currentFen = fen;
        for (int ply = 0; ply < maxPlies; ply++) {
            Board board = rules.board(currentFen);
            if (board.isMated() || board.isStaleMate() || board.isDraw()) {
                break;
            }
            EngineMove best = bestMove(currentFen);
            if (best.uci().isBlank()) {
                break;
            }
            line.add(best);
            currentFen = rules.apply(currentFen, best.uci());
        }
        return line;
    }

    public List<String> principalVariation(String fen) {
        String best = askStockfish(fen, "go depth " + lineDepth);
        return best == null || best.isBlank() ? List.of() : List.of(best);
    }

    private String askStockfish(String fen, String goCommand) {
        synchronized (engineLock) {
            return askPersistentStockfish(fen, goCommand);
        }
    }

    private String askPersistentStockfish(String fen, String goCommand) {
        try {
            ensureEngineReady();
            send(writer, "ucinewgame");
            send(writer, "position fen " + fen);
            send(writer, goCommand);
            String line;
            long deadline = System.currentTimeMillis() + Math.max(1000, moveTimeMs + 1500);
            while (System.currentTimeMillis() < deadline && (line = reader.readLine()) != null) {
                if (line.startsWith("bestmove ")) {
                    return line.split("\\s+")[1];
                }
            }
        } catch (IOException ignored) {
            restartEngine();
            return null;
        }
        return null;
    }

    private void ensureEngineReady() throws IOException {
        if (process != null && process.isAlive()) {
            send(writer, "isready");
            waitFor(reader, "readyok", Duration.ofSeconds(2));
            return;
        }
        process = new ProcessBuilder(stockfishPath).redirectErrorStream(true).start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        send(writer, "uci");
        waitFor(reader, "uciok", Duration.ofSeconds(3));
        send(writer, "isready");
        waitFor(reader, "readyok", Duration.ofSeconds(3));
    }

    private void restartEngine() {
        try {
            if (writer != null) {
                send(writer, "quit");
            }
        } catch (IOException ignored) {
        }
        if (process != null) {
            process.destroyForcibly();
        }
        process = null;
        writer = null;
        reader = null;
    }

    private void send(BufferedWriter writer, String command) throws IOException {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private void waitFor(BufferedReader reader, String token, Duration timeout) throws IOException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String line;
        while (System.currentTimeMillis() < deadline && (line = reader.readLine()) != null) {
            if (line.contains(token)) {
                return;
            }
        }
    }

    private String reasonFor(String move, String fen) {
        Board board = rules.board(fen);
        if (board.getSideToMove() == Side.WHITE) {
            return "Keeps the mating net tight and improves the attacking king or piece coordination.";
        }
        return "The defending king chooses the most resilient legal square and avoids walking into a faster mate.";
    }

    public record EngineMove(String uci, String reason) {
    }
}
