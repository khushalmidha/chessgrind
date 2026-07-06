package com.mateforge.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mateforge.api.dto.TrainingDtos.GameReportDto;
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

        GameReportDto report = callGemini(prompt, gameReportSchema(), GameReportDto.class);
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

        PlayerProfileReportDto report = callGemini(prompt, profileReportSchema(), PlayerProfileReportDto.class);
        user.setProfileReportJson(toJson(report));
        user.setProfileReportFingerprint(fingerprint);
        users.save(user);
        return report;
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
