package com.mateforge.api.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EngineService {
    private static final Duration BEST_MOVE_TTL = Duration.ofHours(12);
    private static final Duration SOLUTION_TTL = Duration.ofHours(6);

    private final String stockfishPath;
    private final int moveTimeMs;
    private final int lineDepth;
    private final int threads;
    private final int hashMb;
    private final ChessRulesService rules;
    private final RedisCacheService cache;
    private final Object engineLock = new Object();
    private final Map<String, EngineMove> bestMoveCache = new ConcurrentHashMap<>();
    private final Map<String, List<EngineMove>> solutionCache = new ConcurrentHashMap<>();
    // Dedicated single-thread executor so engine calls never block HTTP threads
    private final ExecutorService engineExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stockfish-worker");
        t.setDaemon(true);
        return t;
    });
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    public EngineService(
        @Value("${app.engine.stockfish-path}") String stockfishPath,
        @Value("${app.engine.move-time-ms}") int moveTimeMs,
        @Value("${app.engine.line-depth}") int lineDepth,
        @Value("${app.engine.threads}") int threads,
        @Value("${app.engine.hash-mb}") int hashMb,
        ChessRulesService rules,
        RedisCacheService cache
    ) {
        this.stockfishPath = stockfishPath;
        this.moveTimeMs = moveTimeMs;
        this.lineDepth = lineDepth;
        this.threads = threads;
        this.hashMb = hashMb;
        this.rules = rules;
        this.cache = cache;
    }

    /**
     * Returns best move synchronously. Checks in-process cache first, then Redis,
     * then calls Stockfish. The Stockfish call runs on the dedicated engine thread
     * so it never blocks a Tomcat HTTP worker.
     */
    public EngineMove bestMove(String fen) {
        // 1. In-process cache (instant)
        EngineMove local = bestMoveCache.get(fen);
        if (local != null) {
            return local;
        }
        // 2. Redis cache (fast network round-trip)
        String key = cache.key("engine:best", fen);
        EngineMove cached = cache.get(key, EngineMove.class).orElse(null);
        if (cached != null) {
            bestMoveCache.put(fen, cached);
            return cached;
        }
        // 3. Calculate on dedicated engine thread to avoid blocking HTTP workers
        try {
            return engineExecutor.submit(() -> {
                EngineMove calculated = calculateBestMove(fen);
                bestMoveCache.put(fen, calculated);
                cache.put(key, calculated, BEST_MOVE_TTL);
                return calculated;
            }).get();
        } catch (Exception ex) {
            // Fallback: pick first legal move
            Board board = rules.board(fen);
            List<Move> legal = rules.legalMoves(board);
            String fallback = legal.isEmpty() ? "" : legal.getFirst().toString();
            return new EngineMove(fallback, "Fallback move (engine unavailable).");
        }
    }

    /**
     * Async variant – returns immediately with a CompletableFuture.
     * Use this when the caller does not need to block (e.g. fire-and-forget warm-up).
     */
    public CompletableFuture<EngineMove> bestMoveAsync(String fen) {
        EngineMove local = bestMoveCache.get(fen);
        if (local != null) {
            return CompletableFuture.completedFuture(local);
        }
        String key = cache.key("engine:best", fen);
        EngineMove cached = cache.get(key, EngineMove.class).orElse(null);
        if (cached != null) {
            bestMoveCache.put(fen, cached);
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            EngineMove calculated = calculateBestMove(fen);
            bestMoveCache.put(fen, calculated);
            cache.put(key, calculated, BEST_MOVE_TTL);
            return calculated;
        }, engineExecutor);
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
        List<EngineMove> local = solutionCache.get(cacheKey);
        if (local != null) {
            return local;
        }
        String redisKey = cache.key("engine:solution", cacheKey);
        List<EngineMove> cached = cache.get(redisKey, new TypeReference<List<EngineMove>>() {}).orElse(null);
        if (cached != null) {
            solutionCache.put(cacheKey, cached);
            return cached;
        }
        List<EngineMove> calculated = calculateSolutionLine(fen, maxPlies);
        solutionCache.put(cacheKey, calculated);
        cache.put(redisKey, calculated, SOLUTION_TTL);
        return calculated;
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
            send(writer, "position fen " + fen);
            send(writer, goCommand);
            String line;
            // Tight deadline: moveTimeMs + small fixed overhead (no extra 450ms padding)
            long deadline = System.currentTimeMillis() + moveTimeMs + 150;
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
        send(writer, "setoption name Threads value " + Math.max(1, threads));
        send(writer, "setoption name Hash value " + Math.max(16, hashMb));
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
