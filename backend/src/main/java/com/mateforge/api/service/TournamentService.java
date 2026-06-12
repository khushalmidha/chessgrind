package com.mateforge.api.service;

import com.mateforge.api.dto.TournamentDtos.CreateTournamentRequest;
import com.mateforge.api.dto.TournamentDtos.TournamentDto;
import com.mateforge.api.dto.TournamentDtos.TournamentParticipantDto;
import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.Tournament;
import com.mateforge.api.model.TournamentParticipant;
import com.mateforge.api.repository.AppUserRepository;
import com.mateforge.api.repository.TournamentParticipantRepository;
import com.mateforge.api.repository.TournamentRepository;
import com.mateforge.api.security.UserPrincipal;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final TournamentRepository tournaments;
    private final TournamentParticipantRepository participants;
    private final AppUserRepository users;
    private final SecureRandom random = new SecureRandom();
    private final String frontendBaseUrl;

    public TournamentService(
        TournamentRepository tournaments,
        TournamentParticipantRepository participants,
        AppUserRepository users,
        @Value("${app.frontend-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.tournaments = tournaments;
        this.participants = participants;
        this.users = users;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public TournamentDto create(CreateTournamentRequest request, UserPrincipal principal) {
        AppUser user = user(principal);
        Tournament tournament = new Tournament();
        tournament.setCreatedBy(user);
        tournament.setName(request.name());
        tournament.setMode(request.mode());
        tournament.setDifficulty(request.difficulty());
        tournament.setTimeLimitSeconds(request.timeLimitSeconds());
        tournament.setMaxPlayers(request.maxPlayers());
        tournament.setJoinCode(joinCode());
        tournaments.save(tournament);
        join(tournament, user);
        return dto(tournament);
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> mine(UserPrincipal principal) {
        return tournaments.findTop20ByCreatedByOrderByCreatedAtDesc(user(principal)).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public TournamentDto get(String joinCode) {
        return dto(tournament(joinCode));
    }

    @Transactional
    public TournamentDto join(String joinCode, UserPrincipal principal) {
        Tournament tournament = tournament(joinCode);
        AppUser user = user(principal);
        if (participants.countByTournament(tournament) >= tournament.getMaxPlayers()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tournament is full");
        }
        join(tournament, user);
        return dto(tournament);
    }

    private void join(Tournament tournament, AppUser user) {
        participants.findByTournamentAndUser(tournament, user).orElseGet(() -> {
            TournamentParticipant participant = new TournamentParticipant();
            participant.setTournament(tournament);
            participant.setUser(user);
            return participants.save(participant);
        });
    }

    private Tournament tournament(String joinCode) {
        return tournaments.findByJoinCode(joinCode).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tournament not found"));
    }

    private TournamentDto dto(Tournament tournament) {
        List<TournamentParticipantDto> playerDtos = participants.findByTournamentOrderByScoreDescBestTimeSecondsAsc(tournament).stream()
            .map(participant -> new TournamentParticipantDto(participant.getUser().getId(), participant.getUser().getUsername(),
                participant.getScore(), participant.getBestTimeSeconds(), participant.getBestAccuracy()))
            .toList();
        return new TournamentDto(tournament.getId(), tournament.getName(), tournament.getJoinCode(),
            frontendBaseUrl.replaceAll("/$", "") + "/tournament/" + tournament.getJoinCode(),
            tournament.getStatus(), tournament.getMode(), tournament.getDifficulty(), tournament.getTimeLimitSeconds(),
            tournament.getMaxPlayers(), playerDtos.size(), tournament.getCreatedAt(), playerDtos);
    }

    private AppUser user(UserPrincipal principal) {
        return users.findById(principal.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private String joinCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = builder.toString();
        } while (tournaments.existsByJoinCode(code));
        return code;
    }
}
