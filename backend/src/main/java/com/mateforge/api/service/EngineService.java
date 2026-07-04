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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EngineService {
    private final String stockfishPath;
    private final int moveTimeMs;
    private final int lineDepth;
    private final ChessRulesService rules;

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
        String best = askStockfish(fen, "go movetime " + moveTimeMs);
        boolean fallback = best == null || best.isBlank() || "(none)".equals(best);
        if (fallback) {
            Board board = rules.board(fen);
            List<Move> legal = rules.legalMoves(board);
            best = legal.isEmpty() ? "" : legal.getFirst().toString();
        }
        return new EngineMove(best, fallback ? "Engine unavailable or timed out; using the first legal move as a safe fallback." : reasonFor(best, fen));
        // FIXED: a missing or hung Stockfish process previously failed silently with no clear reason for fallback moves.
    }

    public List<EngineMove> solutionLine(String fen, int maxPlies) {
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
        Process process = null;
        try {
            process = new ProcessBuilder(stockfishPath).redirectErrorStream(true).start();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                send(writer, "uci");
                waitFor(reader, "uciok", Duration.ofSeconds(2));
                send(writer, "isready");
                waitFor(reader, "readyok", Duration.ofSeconds(2));
                send(writer, "position fen " + fen);
                send(writer, goCommand);
                long deadline = System.currentTimeMillis() + Math.max(1000, moveTimeMs + 1000);
                String line;
                while (process.isAlive() && System.currentTimeMillis() < deadline && (line = readLine(reader, process, deadline)) != null) {
                    if (line.startsWith("bestmove ")) {
                        send(writer, "quit");
                        return line.split("\\s+")[1];
                    }
                }
            }
        } catch (IOException ignored) {
            return null;
        } finally {
            if (process != null) {
                // FIXED: every UCI request owns its process and forcibly closes it on timeout/error to avoid Stockfish leaks.
                process.destroyForcibly();
            }
        }
        return null;
    }

    private void send(BufferedWriter writer, String command) throws IOException {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private void waitFor(BufferedReader reader, String token, Duration timeout) throws IOException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String line;
        while (System.currentTimeMillis() < deadline && (line = readLine(reader, null, deadline)) != null) {
            if (line.contains(token)) {
                return;
            }
        }
        // FIXED: UCI handshakes now stop at a deadline instead of blocking indefinitely on readLine().
    }

    private String readLine(BufferedReader reader, Process process, long deadline) throws IOException {
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                return reader.readLine();
            }
            if (process != null && !process.isAlive()) {
                return reader.ready() ? reader.readLine() : null;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
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
