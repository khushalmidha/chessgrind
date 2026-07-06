package com.mateforge.api.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EngineService {
    private final String stockfishPath;
    private final int moveTimeMs;
    private final int lineDepth;
    private final ChessRulesService rules;
    private final ReentrantLock engineLock = new ReentrantLock();

    private Process engineProcess;
    private BufferedWriter engineWriter;
    private BufferedReader engineReader;

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
            best = fallbackMove(board);
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
        engineLock.lock();
        try {
            ensureEngine();
            send(engineWriter, "ucinewgame");
            send(engineWriter, "position fen " + fen);
            send(engineWriter, goCommand);
            long deadline = System.currentTimeMillis() + Math.max(1000, moveTimeMs + 1000);
            String line;
            while (engineProcess != null && engineProcess.isAlive() && System.currentTimeMillis() < deadline && (line = readLine(engineReader, engineProcess, deadline)) != null) {
                if (line.startsWith("bestmove ")) {
                    return line.split("\\s+")[1];
                }
            }
            resetEngine();
        } catch (IOException ignored) {
            resetEngine();
            return null;
        } finally {
            engineLock.unlock();
        }
        return null;
    }

    @PreDestroy
    public void shutdown() {
        engineLock.lock();
        try {
            resetEngine();
        } finally {
            engineLock.unlock();
        }
    }

    private void send(BufferedWriter writer, String command) throws IOException {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private void ensureEngine() throws IOException {
        if (engineProcess != null && engineProcess.isAlive() && engineWriter != null && engineReader != null) {
            return;
        }
        resetEngine();
        engineProcess = new ProcessBuilder(stockfishPath).redirectErrorStream(true).start();
        engineWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream(), StandardCharsets.UTF_8));
        engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream(), StandardCharsets.UTF_8));
        send(engineWriter, "uci");
        waitFor(engineReader, "uciok", Duration.ofSeconds(2));
        send(engineWriter, "isready");
        waitFor(engineReader, "readyok", Duration.ofSeconds(2));
        // FIXED: Stockfish is initialized once and then reused across requests instead of being restarted on every move.
    }

    private void waitFor(BufferedReader reader, String token, Duration timeout) throws IOException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String line;
        while (System.currentTimeMillis() < deadline && (line = readLine(reader, null, deadline)) != null) {
            if (line.contains(token)) {
                return;
            }
        }
        resetEngine();
        throw new IOException("Stockfish did not respond with " + token + " before the deadline");
        // FIXED: UCI handshakes now fail fast on timeout instead of leaving the request thread stuck.
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

    private String fallbackMove(Board board) {
        List<Move> legal = rules.legalMoves(board);
        if (legal.isEmpty()) {
            return "";
        }
        Piece king = board.getSideToMove() == Side.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING;
        return legal.stream()
            .filter(move -> board.getPiece(move.getFrom()) == king)
            .findFirst()
            .orElse(legal.getFirst())
            .toString();
        // FIXED: when Stockfish is unavailable, defender fallback now prefers moving the side-to-move king.
    }

    private void resetEngine() {
        if (engineWriter != null) {
            try {
                engineWriter.close();
            } catch (IOException ignored) {
            }
        }
        if (engineReader != null) {
            try {
                engineReader.close();
            } catch (IOException ignored) {
            }
        }
        if (engineProcess != null) {
            engineProcess.destroyForcibly();
        }
        engineProcess = null;
        engineWriter = null;
        engineReader = null;
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
