package com.mateforge.api.repository;

import com.mateforge.api.model.Tournament;
import com.mateforge.api.model.TournamentParticipant;
import com.mateforge.api.model.TournamentResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentResultRepository extends JpaRepository<TournamentResult, UUID> {
    boolean existsByParticipantAndRoundNumber(TournamentParticipant participant, int roundNumber);

    List<TournamentResult> findByTournament(Tournament tournament);
}
