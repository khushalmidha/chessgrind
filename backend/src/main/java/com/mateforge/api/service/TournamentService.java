package com.mateforge.api.service;

import com.mateforge.api.dto.TournamentDtos.CreateTournamentRequest;
import com.mateforge.api.dto.TournamentDtos.StandingDto;
import com.mateforge.api.dto.TournamentDtos.SubmitResultRequest;
import com.mateforge.api.dto.TournamentDtos.TournamentDetailDto;
import com.mateforge.api.dto.TournamentDtos.TournamentDto;
import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.Tournament;
import com.mateforge.api.model.TournamentParticipant;
import com.mateforge.api.model.TournamentResult;
import com.mateforge.api.repository.AppUserRepository;
import com.mateforge.api.repository.TournamentParticipantRepository;
import com.mateforge.api.repository.TournamentRepository;
import com.mateforge.api.repository.TournamentResultRepository;
import com.mateforge.api.security.UserPrincipal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final TournamentRepository tournaments;
    private final TournamentParticipantRepository participants;
    private final TournamentResultRepository results;
    private final AppUserRepository users;
    private final SimpMessagingTemplate messaging;

    public TournamentService(
        TournamentRepository tournaments,
        TournamentParticipantRepository participants,
        TournamentResultRepository results,
        AppUserRepository users,
        SimpMessagingTemplate messaging
    ) {
        this.tournaments = tournaments;
        this.participants = participants;
        this.results = results;
        this.users = users;
        this.messaging = messaging;
    }

    @Transactional
    public TournamentDetailDto create(CreateTournamentRequest request, UserPrincipal principal) {
        AppUser host = user(principal);
        if (request.scheduledStartAt().isBefore(Instant.now().minusSeconds(60))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Start time must be in the future");
        }
        Tournament tournament = new Tournament();
        tournament.setCode(uniqueCode());
        tournament.setName(request.name().trim());
        tournament.setHost(host);
        tournament.setMode(request.mode());
        tournament.setDifficulty(request.difficulty());
        tournament.setRounds(request.rounds());
        tournament.setScheduledStartAt(request.scheduledStartAt());
        tournaments.save(tournament);
        joinInternal(tournament, host);
        return detail(tournament.getCode(), principal);
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> mine(UserPrincipal principal) {
        AppUser user = user(principal);
        return tournaments.findTop20ByHostOrderByScheduledStartAtDesc(user).stream()
            .map(tournament -> dto(tournament, user))
            .toList();
    }

    @Transactional(readOnly = true)
    public TournamentDetailDto detail(String code, UserPrincipal principal) {
        AppUser user = user(principal);
        Tournament tournament = tournament(code);
        return new TournamentDetailDto(dto(tournament, user), standings(code, principal));
    }

    @Transactional
    public TournamentDetailDto join(String code, UserPrincipal principal) {
        AppUser user = user(principal);
        Tournament tournament = tournament(code);
        if (!Instant.now().isBefore(tournament.getScheduledStartAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tournament has already started");
        }
        if (!participants.existsByTournamentAndUser(tournament, user)) {
            joinInternal(tournament, user);
        }
        TournamentDetailDto detail = detail(code, principal);
        publish(code, detail.standings());
        return detail;
    }

    @Transactional(readOnly = true)
    public List<StandingDto> standings(String code, UserPrincipal principal) {
        user(principal);
        Tournament tournament = tournament(code);
        Map<TournamentParticipant, List<TournamentResult>> byParticipant = results.findByTournament(tournament).stream()
            .collect(Collectors.groupingBy(TournamentResult::getParticipant));
        return participants.findByTournamentOrderByJoinedAtAsc(tournament).stream()
            .map(participant -> standing(participant, byParticipant.getOrDefault(participant, List.of())))
            .sorted(Comparator.comparing(StandingDto::totalPoints).reversed()
                .thenComparing(StandingDto::averageAccuracy, Comparator.reverseOrder())
                .thenComparing(StandingDto::totalTimeSeconds))
            .toList();
    }

    @Transactional
    public TournamentDetailDto submit(String code, SubmitResultRequest request, UserPrincipal principal) {
        AppUser user = user(principal);
        Tournament tournament = tournament(code);
        if (Instant.now().isBefore(tournament.getScheduledStartAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tournament has not started yet");
        }
        if (request.roundNumber() > tournament.getRounds()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Round is outside this tournament");
        }
        TournamentParticipant participant = participants.findByTournamentAndUser(tournament, user)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Join the tournament before submitting results"));
        if (results.existsByParticipantAndRoundNumber(participant, request.roundNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "Result already submitted for this round");
        }
        TournamentResult result = new TournamentResult();
        result.setTournament(tournament);
        result.setParticipant(participant);
        result.setRoundNumber(request.roundNumber());
        result.setAccuracy(request.accuracy());
        result.setTimeSeconds(request.timeSeconds());
        result.setHintsUsed(request.hintsUsed());
        result.setPoints(points(request));
        results.save(result);
        TournamentDetailDto detail = detail(code, principal);
        publish(code, detail.standings());
        return detail;
    }

    private void joinInternal(Tournament tournament, AppUser user) {
        TournamentParticipant participant = new TournamentParticipant();
        participant.setTournament(tournament);
        participant.setUser(user);
        participants.save(participant);
    }

    private StandingDto standing(TournamentParticipant participant, List<TournamentResult> rows) {
        int points = rows.stream().mapToInt(TournamentResult::getPoints).sum();
        int time = rows.stream().mapToInt(TournamentResult::getTimeSeconds).sum();
        double accuracy = rows.stream().mapToDouble(TournamentResult::getAccuracy).average().orElse(0);
        return new StandingDto(participant.getUser().getUsername(), rows.size(), points, Math.round(accuracy * 100.0) / 100.0, time);
    }

    private int points(SubmitResultRequest request) {
        return Math.max(0, (int) Math.round(request.accuracy() * 10) - request.timeSeconds() - request.hintsUsed() * 25);
    }

    private Tournament tournament(String code) {
        return tournaments.findByCode(code.toUpperCase())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tournament not found"));
    }

    private TournamentDto dto(Tournament tournament, AppUser viewer) {
        String status = Instant.now().isBefore(tournament.getScheduledStartAt()) ? "LOBBY" : "LIVE";
        boolean joined = participants.existsByTournamentAndUser(tournament, viewer);
        int count = participants.findByTournamentOrderByJoinedAtAsc(tournament).size();
        return new TournamentDto(tournament.getCode(), tournament.getName(), tournament.getHost().getUsername(),
            tournament.getMode(), tournament.getDifficulty(), tournament.getRounds(), tournament.getScheduledStartAt(),
            status, joined, "/tournament/" + tournament.getCode(), count);
    }

    private String uniqueCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = builder.toString();
        } while (tournaments.existsByCode(code));
        return code;
    }

    private AppUser user(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again");
        }
        return users.findById(principal.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again"));
    }

    private void publish(String code, List<StandingDto> standings) {
        messaging.convertAndSend("/topic/tournaments/" + code + "/standings", standings);
    }
}
