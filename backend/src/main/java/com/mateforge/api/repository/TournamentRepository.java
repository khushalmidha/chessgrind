package com.mateforge.api.repository;

import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.Tournament;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRepository extends JpaRepository<Tournament, UUID> {
    Optional<Tournament> findByCode(String code);

    boolean existsByCode(String code);

    List<Tournament> findTop20ByHostOrderByScheduledStartAtDesc(AppUser host);
}
