package com.mateforge.api.repository;

import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.Tournament;
import com.mateforge.api.model.TournamentParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, UUID> {
    List<TournamentParticipant> findByTournamentOrderByScoreDescBestTimeSecondsAsc(Tournament tournament);

    Optional<TournamentParticipant> findByTournamentAndUser(Tournament tournament, AppUser user);

    long countByTournament(Tournament tournament);
}
