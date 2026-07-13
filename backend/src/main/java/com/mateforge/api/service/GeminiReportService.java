package com.mateforge.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mateforge.api.dto.TrainingDtos.GameReportDto;
import com.mateforge.api.dto.TrainingDtos.MoveHighlightDto;
import com.mateforge.api.dto.TrainingDtos.PlayerProfileReportDto;
import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.SessionStatus;
import com.mateforge.api.model.TrainingMove;
import com.mateforge.api.model.TrainingSession;
import com.mateforge.api.repository.AppUserRepository;
import com.mateforge.api.repository.TrainingMoveRepository;
import com.mateforge.api.repository.TrainingSessionRepository;
import com.mateforge.api.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class GeminiReportService {
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    private final String apiKey;
    private final String model;
    private final TrainingSessionRepository sessions;
    private final TrainingMoveRepository moves;
    private final AppUserRepository users;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiReportService(
        @Value("${app.gemini.api-key:}") String apiKey,
        @Value("${app.gemini.model:gemini-2.5-flash}") String model,
        TrainingSessionRepository sessions,
        TrainingMoveRepository moves,
        AppUserRepository users,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model != null && model.startsWith("models/") ? model.substring("models/".length()) : model;
        this.sessions = sessions;
        this.moves = moves;
        this.users = users;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    @Transactional
    public GameReportDto sessionReport(UUID sessionId, UserPrincipal principal, boolean refresh) {
        TrainingSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getUser().getId().equals(principal.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not own this session");
        }
        if (session.getStatus() == SessionStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reports are available after the session ends");
        }

        List<TrainingMove> moveList = moves.findBySessionOrderByPlyAsc(session);
        String fingerprint = fingerprint(sessionFingerprintSource(session, moveList));
        if (!refresh && fingerprint.equals(session.getReportFingerprint()) && session.getReportJson() != null) {
            return parseCached(session.getReportJson(), GameReportDto.class);
        }

        String prompt = """
            You are Mateforge's chess training analyst. Create a concise post-session report.
            Use only the recorded data below. Do not invent engine evaluations, plans, or move facts that are absent.
            Return JSON matching the provided schema.

            Recorded session data:
            %s
            """.formatted(toJson(sessionPayload(session, moveList)));

        GameReportDto report = geminiConfigured()
            ? safeGemini(prompt, gameReportSchema(), GameReportDto.class, fallbackSessionReport(session, moveList))
            : fallbackSessionReport(session, moveList);
        // FIXED: missing or failing Gemini config made post-game reports unusable instead of returning a real recorded-data coach report.
        session.setReportJson(toJson(report));
        session.setReportFingerprint(fingerprint);
        sessions.save(session);
        return report;
    }

    @Transactional
    public PlayerProfileReportDto profileReport(UserPrincipal principal, boolean refresh) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again");
        }
        AppUser user = users.findById(principal.id())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        // FIXED: stale profile-report auth could produce a generic 500 before user lookup.
        List<TrainingSession> history = sessions.findTop20ByUserOrderByStartedAtDesc(user);
        if (history.isEmpty()) {
            return new PlayerProfileReportDto("Unrated", "Play a few Mateforge sessions to build a coaching profile.",
                List.of(), List.of(), "Not enough session data yet.", List.of("Complete one king and rook mate", "Play a timed queen mate drill"), 0);
            // FIXED: zero-session profile reports no longer call Gemini with an empty payload that can produce malformed responses.
        }
        String fingerprint = fingerprint(profileFingerprintSource(history));
        if (!refresh && fingerprint.equals(user.getProfileReportFingerprint()) && user.getProfileReportJson() != null) {
            return parseCached(user.getProfileReportJson(), PlayerProfileReportDto.class);
        }

        String prompt = """
            You are Mateforge's chess coach. Create a rolling player profile from recent checkmate-training sessions.
            Use only the aggregate facts and recorded sessions below. Do not invent unrecorded chess metrics.
            Return JSON matching the provided schema.

            Player profile data:
            %s
            """.formatted(toJson(profilePayload(history)));

        PlayerProfileReportDto report = geminiConfigured()
            ? safeGemini(prompt, profileReportSchema(), PlayerProfileReportDto.class, fallbackProfileReport(history))
            : fallbackProfileReport(history);
        // FIXED: profile coaching disappeared when GEMINI_API_KEY was absent; the endpoint now degrades to deterministic coaching.
        user.setProfileReportJson(toJson(report));
        user.setProfileReportFingerprint(fingerprint);
        users.save(user);
        return report;
    }

    private boolean geminiConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private <T> T safeGemini(String prompt, Map<String, Object> schema, Class<T> type, T fallback) {
        try {
            return callGemini(prompt, schema, type);
        } catch (ApiException ex) {
            if (ex.status() == HttpStatus.SERVICE_UNAVAILABLE) {
                return fallback;
            }
            throw ex;
        }
    }

    private <T> T callGemini(String prompt, Map<String, Object> schema, Class<T> type) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI reports are unavailable because Gemini is not configured");
        }
        Map<String, Object> body = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", schema
            )
        );
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .uri(ENDPOINT + "?key={key}", model, apiKey)
                .body(body)
                .retrieve()
                .body(Map.class);
            return parseCached(extractText(response), type);
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI report generation is temporarily unavailable");
        }
    }

    private GameReportDto fallbackSessionReport(TrainingSession session, List<TrainingMove> moveList) {
        List<TrainingMove> userMoves = moveList.stream().filter(move -> !move.isEngineMove()).toList();
        List<TrainingMove> mistakes = userMoves.stream().filter(move -> !move.isOptimal()).toList();
        double accuracy = session.getAccuracy();
        List<String> strengths = new ArrayList<>();
        if (session.getStatus() == SessionStatus.CHECKMATE) strengths.add("Converted the mating pattern to checkmate.");
        if (accuracy >= 80) strengths.add("Kept a high move accuracy against the defender.");
        if (session.getHintsUsed() == 0) strengths.add("Completed the drill without using hints.");
        if (strengths.isEmpty()) strengths.add("Finished a recorded training attempt that can now be reviewed.");

        List<String> weaknesses = new ArrayList<>();
        if (!mistakes.isEmpty()) weaknesses.add("Review the inexact moves where Stockfish found a more forcing continuation.");
        if (elapsedSeconds(session) > 180) weaknesses.add("Work on converting the same pattern with a faster clock.");
        if (session.getHintsUsed() > 0) weaknesses.add("Reduce hint dependency by replaying the first critical move.");
        if (weaknesses.isEmpty()) weaknesses.add("Keep sharpening speed and precision on nearby endgame patterns.");

        List<String> recurringMistakes = mistakes.stream()
            .map(TrainingMove::getReason)
            .filter(reason -> reason != null && !reason.isBlank())
            .distinct()
            .limit(5)
            .toList();
        if (recurringMistakes.isEmpty()) {
            recurringMistakes = List.of("No repeated mistake pattern was recorded in this session.");
        }

        List<MoveHighlightDto> highlights = userMoves.stream()
            .limit(6)
            .map(move -> new MoveHighlightDto(move.getPly(), move.isOptimal()
                ? "Best or acceptable attacking move from the recorded line."
                : cleanReason(move.getReason())))
            .toList();

        String summary = "You scored %.0f%% accuracy in %s with %d recorded attacking move%s. Outcome: %s."
            .formatted(accuracy, session.getMode(), userMoves.size(), userMoves.size() == 1 ? "" : "s", session.getStatus());
        String band = accuracy >= 85 ? "Strong" : accuracy >= 60 ? "Solid" : "Developing";
        return new GameReportDto(summary, strengths, weaknesses, recurringMistakes, highlights,
            List.of("Replay the highlighted ply sequence", "Compare your first move with the best-move arrow in review", "Repeat this mode once with a shorter timer"),
            band);
    }

    private PlayerProfileReportDto fallbackProfileReport(List<TrainingSession> history) {
        List<TrainingSession> completed = history.stream().filter(session -> session.getStatus() != SessionStatus.ACTIVE).toList();
        if (completed.isEmpty()) {
            return new PlayerProfileReportDto("Unrated", "Complete one Mateforge session to unlock a coaching profile.",
                List.of(), List.of(), "No completed sessions are recorded yet.", List.of("Finish one king and rook mate", "Then replay it in review mode"), 0);
        }
        double averageAccuracy = completed.stream().mapToDouble(TrainingSession::getAccuracy).average().orElse(0);
        List<String> strongModes = modeNames(completed, false);
        List<String> weakModes = modeNames(completed, true);
        List<String> mistakeReasons = commonMistakeReasons(completed);
        int consistency = (int) Math.max(0, Math.min(100, Math.round(averageAccuracy)));
        String level = averageAccuracy >= 85 ? "Strong" : averageAccuracy >= 60 ? "Solid" : "Developing";
        String style = "Across %d recent completed sessions you average %.0f%% accuracy with %.1f hints per game."
            .formatted(completed.size(), averageAccuracy, completed.stream().mapToInt(TrainingSession::getHintsUsed).average().orElse(0));
        List<String> drills = new ArrayList<>();
        if (!weakModes.isEmpty()) drills.add("Prioritize " + weakModes.getFirst() + " until it reaches your average accuracy.");
        drills.add("Replay one missed move with the review arrows before starting the next game.");
        drills.add("Use a tournament rating target near your current profile rating and choose events with increment time controls.");
        if (!mistakeReasons.isEmpty()) drills.add("Focus on: " + mistakeReasons.getFirst());
        return new PlayerProfileReportDto(level, style, strongModes, weakModes,
            "Recent accuracy is based only on saved Mateforge sessions, so it updates after each completed game is stored.",
            drills, consistency);
    }

    private String cleanReason(String reason) {
        return reason == null || reason.isBlank() ? "Stockfish marked this move as inexact." : reason;
    }

    private List<String> modeNames(List<TrainingSession> sessions, boolean ascending) {
        return modeAccuracy(sessions, ascending).stream()
            .map(row -> row.get("mode") + " (" + row.get("averageAccuracy") + "%)")
            .toList();
    }

    private String extractText(Map<String, Object> response) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) candidates.getFirst().get("content");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            Object text = parts.getFirst().get("text");
            if (text instanceof String value && !value.isBlank()) {
                return value;
            }
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI report response was malformed");
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI report response was empty");
    }

    private <T> T parseCached(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI report response was malformed");
        }
    }

    private Map<String, Object> sessionPayload(TrainingSession session, List<TrainingMove> moveList) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", session.getMode());
        payload.put("difficulty", session.getDifficulty());
        payload.put("outcome", session.getStatus());
        payload.put("hintsUsed", session.getHintsUsed());
        payload.put("accuracy", session.getAccuracy());
        payload.put("timeTakenSeconds", elapsedSeconds(session));
        payload.put("moves", moveList.stream().map(this::movePayload).toList());
        return payload;
    }

    private Map<String, Object> profilePayload(List<TrainingSession> history) {
        List<TrainingSession> completed = history.stream().filter(session -> session.getStatus() != SessionStatus.ACTIVE).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionCount", completed.size());
        payload.put("averageAccuracy", completed.stream().mapToDouble(TrainingSession::getAccuracy).average().orElse(0));
        payload.put("hintDependency", completed.stream().mapToInt(TrainingSession::getHintsUsed).average().orElse(0));
        payload.put("weakModes", modeAccuracy(completed, true));
        payload.put("strongModes", modeAccuracy(completed, false));
        payload.put("accuracyTrendOldestToNewest", completed.reversed().stream().map(TrainingSession::getAccuracy).toList());
        payload.put("timeTrendOldestToNewest", completed.reversed().stream().map(this::elapsedSeconds).toList());
        payload.put("commonMistakeReasons", commonMistakeReasons(completed));
        payload.put("recentSessions", completed.stream().map(this::sessionSummaryPayload).toList());
        return payload;
    }

    private Map<String, Object> movePayload(TrainingMove move) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ply", move.getPly());
        payload.put("uci", move.getUci());
        payload.put("san", move.getSan());
        payload.put("engineMove", move.isEngineMove());
        payload.put("optimal", move.isOptimal());
        payload.put("reason", move.getReason());
        return payload;
    }

    private Map<String, Object> sessionSummaryPayload(TrainingSession session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", session.getMode());
        payload.put("difficulty", session.getDifficulty());
        payload.put("outcome", session.getStatus());
        payload.put("accuracy", session.getAccuracy());
        payload.put("hintsUsed", session.getHintsUsed());
        payload.put("timeTakenSeconds", elapsedSeconds(session));
        payload.put("mistakes", session.getMistakes());
        return payload;
    }

    private List<Map<String, Object>> modeAccuracy(List<TrainingSession> sessions, boolean ascending) {
        return sessions.stream()
            .collect(java.util.stream.Collectors.groupingBy(TrainingSession::getMode,
                java.util.stream.Collectors.averagingDouble(TrainingSession::getAccuracy)))
            .entrySet()
            .stream()
            .sorted((left, right) -> ascending
                ? Double.compare(left.getValue(), right.getValue())
                : Double.compare(right.getValue(), left.getValue()))
            .limit(3)
            .map(entry -> Map.<String, Object>of("mode", entry.getKey().name(), "averageAccuracy", Math.round(entry.getValue() * 100.0) / 100.0))
            .toList();
    }

    private List<String> commonMistakeReasons(List<TrainingSession> sessions) {
        List<String> reasons = new ArrayList<>();
        for (TrainingSession session : sessions) {
            reasons.addAll(moves.findBySessionOrderByPlyAsc(session).stream()
                .filter(move -> !move.isEngineMove() && !move.isOptimal())
                .map(TrainingMove::getReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .toList());
        }
        return reasons.stream()
            .collect(java.util.stream.Collectors.groupingBy(reason -> reason, java.util.stream.Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .toList();
    }

    private long elapsedSeconds(TrainingSession session) {
        if (session.getEndedAt() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(session.getStartedAt(), session.getEndedAt()).toSeconds());
    }

    private String sessionFingerprintSource(TrainingSession session, List<TrainingMove> moveList) {
        return session.getId() + "|" + session.getStatus() + "|" + session.getAccuracy() + "|" + session.getHintsUsed() + "|"
            + session.getEndedAt() + "|" + moveList.stream().map(move -> move.getPly() + ":" + move.getUci() + ":" + move.isOptimal()).toList();
    }

    private String profileFingerprintSource(List<TrainingSession> history) {
        return history.stream()
            .map(session -> session.getId() + ":" + session.getStatus() + ":" + session.getAccuracy() + ":" + session.getHintsUsed() + ":" + session.getEndedAt())
            .toList()
            .toString();
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI report payload could not be prepared");
        }
    }

    private Map<String, Object> gameReportSchema() {
        return objectSchema(Map.of(
            "summary", stringSchema(),
            "strengths", stringArraySchema(),
            "weaknesses", stringArraySchema(),
            "recurringMistakes", stringArraySchema(),
            "moveHighlights", arraySchema(objectSchema(Map.of("ply", integerSchema(), "comment", stringSchema()), List.of("ply", "comment"))),
            "nextFocusAreas", stringArraySchema(),
            "overallRatingBand", stringSchema()
        ), List.of("summary", "strengths", "weaknesses", "recurringMistakes", "moveHighlights", "nextFocusAreas", "overallRatingBand"));
    }

    private Map<String, Object> profileReportSchema() {
        return objectSchema(Map.of(
            "playerLevel", stringSchema(),
            "styleSummary", stringSchema(),
            "strongModes", stringArraySchema(),
            "weakModes", stringArraySchema(),
            "trendNotes", stringSchema(),
            "recommendedDrills", stringArraySchema(),
            "consistencyScore", integerSchema()
        ), List.of("playerLevel", "styleSummary", "strongModes", "weakModes", "trendNotes", "recommendedDrills", "consistencyScore"));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties) {
        return objectSchema(properties, new ArrayList<>(properties.keySet()));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "OBJECT", "properties", properties, "required", required);
    }

    private Map<String, Object> arraySchema(Map<String, Object> items) {
        return Map.of("type", "ARRAY", "items", items);
    }

    private Map<String, Object> stringArraySchema() {
        return arraySchema(stringSchema());
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "STRING");
    }

    private Map<String, Object> integerSchema() {
        return Map.of("type", "INTEGER");
    }
}
