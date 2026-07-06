package com.mateforge.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tournament_results", uniqueConstraints = @UniqueConstraint(columnNames = {"participant_id", "round_number"}))
public class TournamentResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private TournamentParticipant participant;

    @Column(nullable = false)
    private int roundNumber;

    @Column(nullable = false)
    private double accuracy;

    @Column(nullable = false)
    private int timeSeconds;

    @Column(nullable = false)
    private int hintsUsed;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private Instant completedAt = Instant.now();
}
